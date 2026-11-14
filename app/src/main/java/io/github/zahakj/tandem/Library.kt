package io.github.zahakj.tandem

import android.os.Environment
import io.github.zahakj.tandem.data.GameMeta
import io.github.zahakj.tandem.data.ScanCache
import io.github.zahakj.tandem.data.SortOrder
import io.github.zahakj.tandem.data.Store
import io.github.zahakj.tandem.data.SystemPrefs
import java.io.File

/**
 * One game file. [group] is the subfolder it sits in (for example a genre), or null at the top level. [root] is the
 * game folder tree it was found in when that isn't the ROMs folder (LibraryRoots): its art is looked for there first.
 */
@androidx.compose.runtime.Immutable
data class Game(
    val platform: Platform,
    val file: File,
    val fileTitle: String,
    val group: String?,
    val root: File? = null,
) {
    val stem: String get() = file.nameWithoutExtension

    /** Stable id for Tandem's data: survives moving the file between subfolders. */
    val key: String = "${platform.id}/${file.name}"

    val meta: GameMeta get() = Store.get(key)
    val title: String get() = meta.title ?: fileTitle
}

/** Everything found for one system: its games and where their artwork is. */
class PlatformLibrary(val platform: Platform, val games: List<Game>, val art: ArtIndex) {
    /** Subfolder names, in alphabetical order. */
    val groups: List<String> = games.mapNotNull { it.group }.distinct().sortedBy { it.lowercase() }

    fun gamesIn(group: String?): List<Game> = games.filter { it.group == group }

    fun box(game: Game): File? = FileChecks.file(game.meta.box) ?: art.find(ArtIndex.Kind.Box, game)
        ?: platform.fallbackArt?.invoke(game)
    fun hero(game: Game): File? = FileChecks.file(game.meta.hero) ?: art.find(ArtIndex.Kind.Hero, game)
    /**
     * The game's logo, only if it can float over art: scrapers sometimes save a title card (an opaque picture) as the
     * "logo", which would cover the hero and look doubled. Those are skipped and the title is shown as text instead.
     */
    fun logo(game: Game): File? = (FileChecks.file(game.meta.logo) ?: art.find(ArtIndex.Kind.Logo, game))?.takeIf { FileChecks.isCutout(it) }
    fun screenshot(game: Game): File? = FileChecks.file(game.meta.screenshot)

    /**
     * Every picture that could fill a screen behind the game, with what kind it is: the details' own choices first,
     * then what is in the art folders, then the box art. [io.github.zahakj.tandem.ui.ArtPick] chooses among them.
     */
    fun backdrops(game: Game): List<Backdrop> {
        val m = game.meta
        val out = ArrayList<Backdrop>(6)
        fun add(f: File?, kind: Backdrop.Kind) { if (f != null && out.none { it.file == f }) out += Backdrop(f, kind) }
        add(FileChecks.file(m.hero), Backdrop.Kind.Key)
        add(FileChecks.file(m.titleScreen), Backdrop.Kind.Title)
        add(FileChecks.file(m.screenshot), Backdrop.Kind.Play)
        for (f in art.wides(game)) add(f, Backdrop.Kind.of(f.parentFile?.name.orEmpty()))
        add(box(game), Backdrop.Kind.Box)
        return out
    }
}

/** A picture that can fill the screen behind a game, and what kind of picture it is (key art beats a screenshot). */
data class Backdrop(val file: File, val kind: Kind) {
    enum class Kind(val weight: Float) {
        Key(1f), Title(0.62f), Play(0.56f), Box(0.4f);

        companion object {
            fun of(dir: String): Kind = when (dir.lowercase()) {
                "hero", "fanart" -> Key
                "screenshot_title", "titlescreens" -> Title
                else -> Play
            }
        }
    }
}

/**
 * Whether an art file a game's details point at exists. Screens ask for every card on every recomposition, so the
 * answer is kept for a few seconds instead of touching storage each time.
 */
object FileChecks {
    private const val FRESH_MS = 15_000L
    private val known = java.util.concurrent.ConcurrentHashMap<String, Pair<Boolean, Long>>()

    fun file(path: String?): File? {
        if (path == null) return null
        val now = android.os.SystemClock.uptimeMillis()
        val hit = known[path]
        val exists = if (hit != null && now - hit.second < FRESH_MS) hit.first else File(path).isFile.also { known[path] = it to now }
        return if (exists) File(path) else null
    }

    private val cutout = java.util.concurrent.ConcurrentHashMap<String, Boolean>()

    /**
     * True when the picture has transparent areas, the way a real logo does. Decided from a tiny decode (a few
     * kilobytes, once per file), checking the corners, which are see-through on cut-out logos.
     */
    fun isCutout(f: File): Boolean = cutout.getOrPut(f.path + ":" + f.lastModified()) {
        val ext = f.extension.lowercase()
        if (ext == "jpg" || ext == "jpeg") return@getOrPut false
        runCatching {
            val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeFile(f.path, bounds)
            val opts = android.graphics.BitmapFactory.Options().apply {
                inSampleSize = maxOf(1, maxOf(bounds.outWidth, bounds.outHeight) / 64)
            }
            val b = android.graphics.BitmapFactory.decodeFile(f.path, opts) ?: return@runCatching true
            val w = b.width - 1; val h = b.height - 1
            val corners = listOf(b.getPixel(0, 0), b.getPixel(w, 0), b.getPixel(0, h), b.getPixel(w, h))
            val transparent = b.hasAlpha() && corners.count { (it ushr 24) < 40 } >= 2
            b.recycle()
            transparent
        }.getOrDefault(true)
    }

    /** After files were deleted, or a rescan. */
    fun clear() { known.clear(); cutout.clear() }
}

object Library {
    val romsDir: File = File(Environment.getExternalStorageDirectory(), "ROMs")
    private const val MAX_DEPTH = 3

    /** Games found so far by the scan running now (the startup splash counts them up). */
    @Volatile var found = 0
        private set

    /**
     * Finds every game. With [quick], folders are read from the scan cache without touching storage, so the library
     * shows instantly at startup; a normal scan then only re-lists folders that changed.
     */
    fun scan(platforms: List<Platform>, quick: Boolean = false): List<PlatformLibrary> {
        if (!quick) found = 0
        val roots = roots()
        val libs = platforms.mapNotNull { platform ->
            val games = gamesOf(platform, quick, roots)
            if (!quick) found += games.size
            if (games.isEmpty()) null else PlatformLibrary(platform, games, ArtIndex(platform.artIds, quick, roots))
        }
        // The games' manuals (ROMs/manuals) are matched by name like their art, so they are looked for with the games.
        Manuals.index(libs, quick)
        ScanCache.save()
        // Remember when each game first showed up, for "Recently added".
        val now = System.currentTimeMillis()
        val known = Store.meta
        Store.updateAll(
            libs.flatMap { it.games }.filter { (known[it.key]?.firstSeen ?: 0L) == 0L }
                .associate { g -> g.key to { m: GameMeta -> m.copy(firstSeen = g.file.lastModified().takeIf { it > 0 } ?: now) } },
        )
        return libs
    }

    /**
     * Every tree games are read from: the ROMs folder, then the folders added in Settings (LibraryRoots), in the
     * order they were added. A root that isn't there right now (an SD card taken out) lists as empty, so its games
     * are simply missing until it is back.
     */
    fun roots(): List<File> = listOf(romsDir) + SystemPrefs.data.roots.map(::File)

    /**
     * The games for one system: from its own source (Android games) or its folders in every root. Game keys are
     * "system/file name", so the same name twice (two subfolders, both n3ds/ and 3ds/, or the same file in two
     * roots) would clash; the first one found wins, with roots in order (the ROMs folder first), folders in
     * platforms.json order and a folder's own files before its subfolders'.
     */
    fun gamesOf(platform: Platform, quick: Boolean = false, roots: List<File> = roots()): List<Game> =
        platform.source?.invoke(platform) ?: LibraryRoots.merge(
            roots.map { root ->
                // A folder added in Systems is a full path of its own: walked once, with the ROMs folder.
                val main = root == romsDir
                val folders = if (main) platform.folders else platform.folders.filter { !it.startsWith("/") }
                folders.flatMap { walk(folder(it, root), platform, group = null, depth = 0, quick, root = if (main) null else root) }
            },
        ) { it.key }

    /** A folder from platforms.json is under [root] (the ROMs folder, or another root); one added in Systems is a full path. */
    fun folder(name: String, root: File = romsDir): File = if (name.startsWith("/")) File(name) else File(root, name)

    private fun walk(dir: File, platform: Platform, group: String?, depth: Int, quick: Boolean, root: File?): List<Game> {
        val found = mutableListOf<Game>()
        val listing = ScanCache.list(dir, quick)
        for (name in listing.files) {
            if (name.startsWith(".")) continue
            val f = File(dir, name)
            if (platform.matches(name)) found += Game(platform, f, cleanTitle(f.nameWithoutExtension), group, root)
        }
        for (name in listing.dirs) {
            if (name.startsWith(".")) continue
            val f = File(dir, name)
            if (f.extension.isNotEmpty() && f.extension.lowercase() in platform.extensions) {
                // A folder named like a game file is one game (PS3 "Game.ps3" folders, for example).
                found += Game(platform, f, cleanTitle(f.nameWithoutExtension), group, root)
            } else if (depth < MAX_DEPTH && name.lowercase() !in platform.skipDirs) {
                // Emulators keep their own data next to the games (Azahar's nand/sdmc, for example); skip it.
                found += walk(f, platform, group ?: name, depth + 1, quick, root)
            }
        }
        return found
    }

    /** "Mario Kart 7 (USA) (En,Fr,Es)" -> "Mario Kart 7". */
    fun cleanTitle(stem: String): String =
        stem.replace(Regex("""\s*[(\[][^)\]]*[)\]]"""), "").trim().ifEmpty { stem }

    fun sorted(games: List<Game>, order: SortOrder): List<Game> = when (order) {
        SortOrder.Name -> games.sortedBy { it.title.lowercase() }
        SortOrder.Recent -> games.sortedWith(compareByDescending<Game> { it.meta.lastPlayed }.thenBy { it.title.lowercase() })
        SortOrder.MostPlayed -> games.sortedWith(compareByDescending<Game> { it.meta.playSeconds }.thenBy { it.title.lowercase() })
        SortOrder.Year -> games.sortedWith(compareBy<Game> { it.meta.year ?: Int.MAX_VALUE }.thenBy { it.title.lowercase() })
        SortOrder.Rating -> games.sortedWith(compareByDescending<Game> { it.meta.rating ?: -1f }.thenBy { it.title.lowercase() })
        SortOrder.Added -> games.sortedWith(compareByDescending<Game> { it.meta.firstSeen }.thenBy { it.title.lowercase() })
    }
}

/**
 * Finds artwork in `<root>/downloaded_media/<system>/<kind>/`, the layout ES-DE and similar frontends use, in every
 * root games come from. A game's art is looked for in its own root first, then in the ROMs folder's (where scrapes
 * save), see [LibraryRoots.artOrder]. A file belongs to a game when its name is the game's file name (without
 * extension), optionally followed by "__" and anything else, which is how some frontends keep several versions of
 * the same picture.
 */
class ArtIndex(artIds: List<String>, quick: Boolean = false, roots: List<File> = listOf(Library.romsDir)) {
    enum class Kind(val dirs: List<String>) {
        Box(listOf("icon", "covers", "box2dfront", "boxart")),
        Hero(listOf("hero", "fanart", "screenshot_title", "titlescreens", "screenshot_gameplay", "screenshots")),
        Logo(listOf("logo", "marquees", "wheel")),
    }

    /** What one root's media folders hold: a picture per game and kind, and every wide picture per game. */
    private class Found(val byKind: Map<Kind, Map<String, File>>, val wides: Map<String, List<File>>)

    private val main = Library.romsDir.path
    private val perRoot: Map<String, Found> = roots.associate { it.path to read(it, artIds, quick) }

    private fun read(root: File, artIds: List<String>, quick: Boolean): Found {
        // Every wide picture of a game, one per folder (the newest edit), in [Kind.Hero]'s order of preference.
        val wides = HashMap<String, MutableList<File>>()
        val byKind = Kind.entries.associateWith { kind ->
            val byGame = HashMap<String, File>()
            for (id in artIds) for (dir in kind.dirs) {
                val folder = File(root, "downloaded_media/$id/$dir")
                for (name in ScanCache.list(folder, quick).files) {
                    val f = File(folder, name)
                    val key = key(f.name)
                    val known = byGame[key]
                    // Earlier dirs in the list are preferred; within one dir the newest edit wins.
                    if (known == null || (known.parentFile == f.parentFile && f.lastModified() > known.lastModified())) {
                        byGame[key] = f
                    }
                    if (kind == Kind.Hero) {
                        val list = wides.getOrPut(key) { mutableListOf() }
                        val same = list.indexOfFirst { it.parentFile == f.parentFile }
                        if (same < 0) list += f else if (f.lastModified() > list[same].lastModified()) list[same] = f
                    }
                }
            }
            byGame
        }
        return Found(byKind, wides)
    }

    fun find(kind: Kind, game: Game): File? {
        val key = game.stem.lowercase()
        for (root in LibraryRoots.artOrder(game.root?.path, main)) perRoot[root]?.byKind?.get(kind)?.get(key)?.let { return it }
        return null
    }

    /**
     * The game's wide pictures (key art, title screens, screenshots), best kind first: its own root's, then those of
     * kinds its root has none of from the ROMs folder.
     */
    fun wides(game: Game): List<File> {
        val key = game.stem.lowercase()
        val order = LibraryRoots.artOrder(game.root?.path, main)
        if (order.size == 1) return perRoot[order[0]]?.wides?.get(key).orEmpty()
        val out = ArrayList<File>()
        for (root in order) for (f in perRoot[root]?.wides?.get(key).orEmpty()) {
            if (out.none { it.parentFile?.name.equals(f.parentFile?.name, ignoreCase = true) }) out += f
        }
        return out
    }

    private fun key(name: String): String {
        val cut = name.indexOf("__")
        return (if (cut > 0) name.substring(0, cut) else name.substringBeforeLast('.')).lowercase()
    }
}
