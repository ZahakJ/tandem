package io.github.zahakj.tandem

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.zahakj.tandem.data.HiddenFolder
import io.github.zahakj.tandem.data.HomeFolder
import io.github.zahakj.tandem.data.IconGridStore
import io.github.zahakj.tandem.data.Store
import java.util.concurrent.ConcurrentHashMap

/*
 * Everything hidden, as one list: hidden ROM folders and systems, hidden home-grid folders and games hidden by
 * themselves. Settings › Play & stats › Hidden and Start › Hidden both list it and unhide through it.
 *
 * Peeks: Start › Hidden › Y shows one hidden thing on its own until hidden games are next tucked away. They live in
 * memory only, count only while private mode is off, and never change what is sent anywhere (that is still
 * Privacy.isHidden).
 */

/** One hidden thing. [id] is stable while it stays hidden, for focus. */
sealed class HiddenItem(val id: String) {
    /** A ROM folder, or a whole system when [entry]'s folder is null. [lib] is null when the system wasn't found. */
    class Folder(val entry: HiddenFolder, val lib: PlatformLibrary?) :
        HiddenItem("f:" + Peeks.folderKey(entry.system, entry.folder)) {
        val isSystem: Boolean get() = entry.folder == null
        val name: String get() = (lib?.platform?.name ?: entry.system).let { if (entry.folder == null) it else "$it › ${entry.folder}" }
        val games: List<Game> get() = lib?.games.orEmpty().let { all -> if (entry.folder == null) all else all.filter { it.group.equals(entry.folder, ignoreCase = true) } }
    }

    /** A folder on the icon-grid home, hidden with everything in it. */
    class HomeFolderItem(val folder: HomeFolder) : HiddenItem("h:${folder.id}")

    /** A game hidden by itself. [twin]: another hidden game has the same title, so its system is named too. */
    class GameItem(val game: Game, val twin: Boolean) : HiddenItem("g:${game.key}") {
        val name: String get() = if (twin) "${game.title} · ${game.platform.shortName}" else game.title
    }
}

/** When Start › Hidden is offered. Free of Android so it can be tested on its own. */
object HiddenAccess {
    /**
     * The Start row shows only when something is hidden, and in private mode only while L1 + R1 + Select has hidden
     * things showing: otherwise nothing may hint that any exist.
     */
    fun startRow(privateMode: Boolean, revealed: Boolean, count: Int): Boolean = count > 0 && (!privateMode || revealed)
}

/** Hidden things shown one at a time (see the notes at the top of this file). */
data class Peeks(val games: Set<String> = emptySet(), val folders: Set<String> = emptySet(), val home: Set<String> = emptySet()) {
    val isEmpty: Boolean get() = games.isEmpty() && folders.isEmpty() && home.isEmpty()

    companion object {
        val None = Peeks()
        /** A ROM folder's key, or a whole system's ([folder] null). */
        fun folderKey(system: String, folder: String?) = if (folder == null) system else system + "\u0000" + folder.lowercase()
    }
}

object HiddenItems {
    private class Memo(val libs: Any?, val meta: Any?, val folders: Any, val grid: Any, val items: List<HiddenItem>)
    @Volatile private var memo: Memo? = null

    /** Everything hidden: folders and systems, then home folders, then games A to Z. Worked out once per change. */
    fun all(): List<HiddenItem> {
        val libs = Libraries.all
        val meta = Store.meta
        val folders = Store.settings.hiddenFolders
        val grid = IconGridStore.config.folders
        memo?.let { if (it.libs === libs && it.meta === meta && it.folders === folders && it.grid === grid) return it.items }
        val out = ArrayList<HiddenItem>()
        for (e in folders) out += HiddenItem.Folder(e, libs?.firstOrNull { it.platform.id == e.system })
        for (f in grid) if (f.hidden) out += HiddenItem.HomeFolderItem(f)
        val games = Libraries.games.filter { it.meta.hidden }.map { it.title.lowercase() to it }.sortedBy { it.first }.map { it.second }
        val titles = games.groupingBy { it.title }.eachCount()
        for (g in games) out += HiddenItem.GameItem(g, (titles[g.title] ?: 0) > 1)
        memo = Memo(libs, meta, folders, grid, out)
        return out
    }

    fun count(): Int = all().size

    /** Whether Start › Hidden is offered right now (see [HiddenAccess.startRow]). */
    fun offered(): Boolean = HiddenAccess.startRow(Privacy.enabled, Privacy.revealed, count())

    /** "System", "Folder · 12 games", for a row's second line. */
    fun kind(item: HiddenItem): String = when (item) {
        is HiddenItem.Folder -> (if (item.isSystem) "System" else "Folder") + " · " + if (item.lib == null) "not found" else plural(item.games.size, "game")
        is HiddenItem.HomeFolderItem -> "Home folder · ${plural(item.folder.items.count { it.isNotEmpty() }, "item")}"
        is HiddenItem.GameItem -> "Game · ${item.game.platform.name}"
    }

    /** Frees [item]: it is back where it was, for good. Returns a toast that names nothing. */
    fun unhide(item: HiddenItem): String {
        when (item) {
            is HiddenItem.Folder -> HiddenFolders.unhide(item.entry.system, item.entry.folder)
            is HiddenItem.HomeFolderItem -> HiddenFolders.setGridFolderHidden(item.folder.id, false)
            is HiddenItem.GameItem -> Store.update(item.game.key) { it.copy(hidden = false) }
        }
        return if (item is HiddenItem.HomeFolderItem) "Back on your home grid" else "Back in your library"
    }

    /** Takes back an [unhide] (the Undo in its toast). */
    fun rehide(item: HiddenItem) {
        when (item) {
            is HiddenItem.Folder -> HiddenFolders.hide(item.entry.system, item.entry.folder)
            is HiddenItem.HomeFolderItem -> HiddenFolders.setGridFolderHidden(item.folder.id, true)
            is HiddenItem.GameItem -> Store.update(item.game.key) { it.copy(hidden = true) }
        }
    }

    // Peeks.

    /** In memory only. Read through [peeks], which ignores them in private mode. */
    private var current by mutableStateOf(Peeks.None)

    val peeks: Peeks get() = if (Privacy.enabled) Peeks.None else current

    fun peeking(item: HiddenItem): Boolean = peeks.let { p ->
        when (item) {
            is HiddenItem.Folder -> Peeks.folderKey(item.entry.system, item.entry.folder) in p.folders
            is HiddenItem.HomeFolderItem -> item.folder.id in p.home
            is HiddenItem.GameItem -> item.game.key in p.games
        }
    }

    /** Shows [item] on its own, or tucks it away again when it already is. */
    fun togglePeek(item: HiddenItem) {
        val p = current
        val on = !peeking(item)
        fun Set<String>.with(k: String) = if (on) this + k else this - k
        current = when (item) {
            is HiddenItem.Folder -> p.copy(folders = p.folders.with(Peeks.folderKey(item.entry.system, item.entry.folder)))
            is HiddenItem.HomeFolderItem -> p.copy(home = p.home.with(item.folder.id))
            is HiddenItem.GameItem -> p.copy(games = p.games.with(item.game.key))
        }
    }

    /** Every peek ends: hidden games were tucked away, private mode changed, or the screensaver started. */
    fun endPeeks() { current = Peeks.None }

    private class Rules(val folders: Any, val grid: Any, val peeks: Peeks, val rules: HideRules) {
        val byPath = ConcurrentHashMap<String, Boolean>()
    }
    @Volatile private var rules: Rules? = null

    /** The folder rules with peeked folders, systems and home folders left out. */
    private fun rules(p: Peeks): Rules {
        val folders = Store.settings.hiddenFolders
        val grid = IconGridStore.config.folders
        rules?.let { if (it.folders === folders && it.grid === grid && it.peeks == p) return it }
        val tiles = io.github.zahakj.tandem.data.GridTree.hiddenTiles(grid, p.home)
        val kept = folders.filter { Peeks.folderKey(it.system, it.folder) !in p.folders }
        return Rules(folders, grid, p, HideRules(kept, tiles)).also { rules = it }
    }

    /** Whether [game] stays out of sight while hidden games are tucked away, peeks counted. */
    fun tucked(game: Game): Boolean {
        val p = peeks
        if (game.meta.hidden && game.key !in p.games) return true
        if (!HiddenFolders.hides(game)) return false
        if (p.folders.isEmpty() && p.home.isEmpty()) return true
        val r = rules(p)
        return r.byPath.getOrPut(game.platform.id + ":" + game.file.path) {
            r.rules.hides(game.platform.id, game.group, HiddenPaths.key(game.file.path, Library.romsDir.path), game.key)
        }
    }

    /** [tucked] for a game known by its key, like a play session's. */
    fun tuckedKey(key: String): Boolean {
        Libraries.byKey(key)?.let { return tucked(it) }
        val p = peeks
        if (Store.get(key).hidden && key !in p.games) return true
        if (!HiddenFolders.hidesKey(key)) return false
        return p.isEmpty || rules(p).rules.hidesKey(key)
    }

    /** Whether a ROM folder or system ([folder] null) stays out of sight while hidden games are tucked away. */
    fun tuckedFolder(platformId: String, folder: String?): Boolean {
        if (!HiddenFolders.isHidden(platformId, folder)) return false
        val p = peeks
        if (p.isEmpty) return true
        val r = rules(p).rules
        return if (folder == null) r.systemHidden(platformId) else r.folderHidden(platformId, folder)
    }

    fun tuckedHomeFolder(f: HomeFolder): Boolean = f.hidden && f.id !in peeks.home
}
