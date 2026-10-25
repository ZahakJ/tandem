package io.github.zahakj.tandem

import io.github.zahakj.tandem.data.HiddenFolder
import io.github.zahakj.tandem.data.IconGridStore
import io.github.zahakj.tandem.data.Store
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/*
 * Hidden folders and systems.
 *
 * A game is hidden when it was hidden by itself (GameMeta.hidden), when it sits anywhere under a hidden ROM folder,
 * when its whole system is hidden, or when it (or its system) is inside a hidden icon-grid folder. Hidden folders
 * are stored as paths relative to the ROMs folder, so games copied in later, and games in folders inside it, are
 * hidden too; the folder's name and a few of its games' keys back the paths up, so a renamed folder stays hidden.
 * Everything else asks Privacy.isHidden / Privacy.canShow, never GameMeta.hidden directly.
 */

/** Path helpers, free of Android so they can be tested on their own. */
object HiddenPaths {
    /** How a path is compared: [stored], lowercased, since the SD card and shared storage ignore case. */
    fun key(path: String, root: String): String = stored(path, root).lowercase()

    /**
     * How a path is stored: relative to [root] when inside it ("snes/RPG"), else the absolute path, with '/'
     * separators and no trailing '/'.
     */
    fun stored(path: String, root: String): String {
        val p = tidy(path)
        val r = tidy(root)
        return when {
            r.isNotEmpty() && p.equals(r, ignoreCase = true) -> ""
            r.isNotEmpty() && p.startsWith("$r/", ignoreCase = true) -> p.substring(r.length + 1)
            else -> p
        }
    }

    /** A stored path as a file again. */
    fun file(stored: String, root: File): File = if (stored.startsWith("/")) File(stored) else File(root, stored)

    private fun tidy(path: String): String = path.replace('\\', '/').trimEnd('/')

    /** True when [key] is one of [folders] or anywhere inside one, walking up a folder at a time. */
    fun covered(key: String, folders: Set<String>): Boolean {
        if (folders.isEmpty()) return false
        var k = key
        while (true) {
            if (k in folders) return true
            val cut = k.lastIndexOf('/')
            if (cut <= 0) return false
            k = k.substring(0, cut)
        }
    }
}

/**
 * The rules for one set of hidden folders and icon-grid folders: built once per change and asked per game.
 * [tiles] are the icon-grid tile keys sitting in hidden icon-grid folders ("s:snes", "game:snes/Mario.sfc").
 */
class HideRules(val folders: List<HiddenFolder>, tiles: Set<String>) {
    private val paths: Set<String> = folders.flatMap { it.paths }.mapTo(HashSet()) { HiddenPaths.key(it, "") }
    private val named: Set<String> = folders.filter { it.folder != null }.mapTo(HashSet()) { named(it.system, it.folder!!) }
    private val systems: Set<String> = folders.filter { it.folder == null }.mapTo(HashSet()) { it.system } +
        tiles.filter { it.startsWith("s:") }.map { it.removePrefix("s:") }
    private val games: Set<String> = tiles.filter { it.startsWith("game:") }.mapTo(HashSet()) { it.removePrefix("game:") }

    val isEmpty: Boolean get() = paths.isEmpty() && named.isEmpty() && systems.isEmpty() && games.isEmpty()

    /**
     * Whether a game is hidden by a folder or system: [path] is its file's key (see [HiddenPaths.key]), [group] the
     * top-level subfolder it sits in, [key] its "system/file name".
     */
    fun hides(platformId: String, group: String?, path: String, key: String): Boolean =
        platformId in systems || key in games ||
            (group != null && named(platformId, group) in named) ||
            HiddenPaths.covered(path, paths)

    /** Whether a game known only by its key (a play session of a game no longer scanned) is hidden. */
    fun hidesKey(key: String): Boolean = key in games || key.substringBefore('/') in systems

    fun folderHidden(platformId: String, group: String): Boolean = named(platformId, group) in named
    fun systemHidden(platformId: String): Boolean = platformId in systems

    private fun named(system: String, folder: String) = system + "\u0000" + folder.lowercase()

    companion object {
        val None = HideRules(emptyList(), emptySet())
    }
}

object HiddenFolders {
    private class Cached(val folders: Any, val grid: Any, val rules: HideRules) {
        /** Answers per game file, so a big library is checked once per change rather than on every frame. */
        val byPath = ConcurrentHashMap<String, Boolean>()
    }

    @Volatile private var cached: Cached? = null

    private val root: String get() = Library.romsDir.path

    private fun current(): Cached {
        val folders = Store.settings.hiddenFolders
        val grid = IconGridStore.config.folders
        val c = cached
        if (c != null && c.folders === folders && c.grid === grid) return c
        val tiles = io.github.zahakj.tandem.data.GridTree.hiddenTiles(grid)
        return Cached(folders, grid, if (folders.isEmpty() && tiles.isEmpty()) HideRules.None else HideRules(folders, tiles)).also { cached = it }
    }

    /** The rules in force. A new object whenever they change (screens use it to know when to work lists out again). */
    fun rules(): HideRules = current().rules

    /** True when a hidden folder, system or icon-grid folder hides [game] (not counting the game's own Hide). */
    fun hides(game: Game): Boolean {
        val c = current()
        if (c.rules.isEmpty) return false
        return c.byPath.getOrPut(game.platform.id + ":" + game.file.path) {
            c.rules.hides(game.platform.id, game.group, HiddenPaths.key(game.file.path, root), game.key)
        }
    }

    fun hidesKey(key: String): Boolean {
        val rules = rules()
        if (rules.isEmpty) return false
        return Libraries.byKey(key)?.let { hides(it) } ?: rules.hidesKey(key)
    }

    fun isHidden(platformId: String, folder: String?): Boolean =
        if (folder == null) rules().systemHidden(platformId) else rules().folderHidden(platformId, folder)

    /** Whether this folder or system was hidden by its own Hide (not just by sitting in a hidden icon-grid folder). */
    fun listed(platformId: String, folder: String?): Boolean =
        Store.settings.hiddenFolders.any { it.system == platformId && it.folder.equals(folder, ignoreCase = true) }

    /** Systems can be hidden whole; a folder can be hidden when it is a real folder (Android games have none). */
    fun canHide(platformId: String, folder: String?): Boolean {
        val lib = Libraries.byId(platformId) ?: return false
        return folder == null || lib.platform.source == null
    }

    /**
     * Where a folder's games live, as stored paths: the folder under each of the system's folders that holds its
     * games (a system may have several, like n3ds/ and 3ds/). For a whole system, its folders.
     */
    fun pathsOf(lib: PlatformLibrary, folder: String?): List<String> {
        val bases = lib.platform.folders.map { Library.folder(it) }
        val dirs = if (folder == null) bases else bases.map { File(it, folder) }
        val used = dirs.filter { d -> lib.games.any { it.file.path.startsWith(d.path + "/") } }.ifEmpty { dirs }
        return used.map { HiddenPaths.stored(it.path, root) }.distinctBy { it.lowercase() }
    }

    private fun knownOf(games: List<Game>): List<String> = games.take(KNOWN).map { it.key }

    fun hide(platformId: String, folder: String?) {
        val lib = Libraries.byId(platformId) ?: return
        val inside = if (folder == null) lib.games else lib.gamesIn(folder)
        val entry = HiddenFolder(platformId, folder, if (lib.platform.source == null) pathsOf(lib, folder) else emptyList(), knownOf(inside))
        Store.updateSettings { s ->
            s.copy(hiddenFolders = s.hiddenFolders.filterNot { it.system == platformId && it.folder.equals(folder, ignoreCase = true) } + entry)
        }
    }

    fun unhide(platformId: String, folder: String?) {
        Store.updateSettings { s ->
            s.copy(hiddenFolders = s.hiddenFolders.filterNot { it.system == platformId && it.folder.equals(folder, ignoreCase = true) })
        }
    }

    /** Hides or unhides an icon-grid folder (and so everything inside it). */
    fun setGridFolderHidden(id: String, hidden: Boolean) {
        IconGridStore.update { c -> c.copy(folders = c.folders.map { if (it.id == id) it.copy(hidden = hidden) else it }) }
    }

    /**
     * After a scan: keeps each hidden folder's [HiddenFolder.known] games current, and follows a folder that was
     * renamed or moved (its old paths are gone, and the games it held turn up together in another folder), so
     * renaming a hidden folder never brings its games back into view. Call before the scan is shown.
     */
    fun follow(libs: List<PlatformLibrary>) {
        val list = Store.settings.hiddenFolders
        if (list.isEmpty()) return
        var changed = false
        val out = list.map { e ->
            val folder = e.folder ?: return@map e
            val lib = libs.firstOrNull { it.platform.id == e.system } ?: return@map e
            val alone = HideRules(listOf(e), emptySet())
            val inside = lib.games.filter { alone.hides(it.platform.id, it.group, HiddenPaths.key(it.file.path, root), it.key) }
            if (inside.isNotEmpty()) {
                val known = knownOf(inside)
                if (known.toSet() == e.known.toSet()) e else e.copy(known = known).also { changed = true }
            } else {
                val gone = e.paths.none { HiddenPaths.file(it, Library.romsDir).isDirectory }
                val moved = lib.games.filter { it.key in e.known }
                val to = moved.map { it.group }.distinct().singleOrNull()
                if (!gone || to == null || to.equals(folder, ignoreCase = true)) e
                else e.copy(folder = to, paths = pathsOf(lib, to)).also { changed = true }
            }
        }
        if (changed) Store.updateSettings { it.copy(hiddenFolders = out) }
    }

    /** How many games' keys each hidden folder remembers, to find it again after a rename. */
    private const val KNOWN = 8
}
