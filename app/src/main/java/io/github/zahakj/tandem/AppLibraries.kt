package io.github.zahakj.tandem

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.zahakj.tandem.AppLibrary.Tile
import io.github.zahakj.tandem.DroidDeckLibrary.ArtKind
import io.github.zahakj.tandem.data.Media
import io.github.zahakj.tandem.data.MediaKind
import io.github.zahakj.tandem.data.ScanCache
import io.github.zahakj.tandem.data.Store
import io.github.zahakj.tandem.data.SystemPrefs
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The games installed in DroidDeck and GameNative join the Windows system on their own, with the apps' art, and start
 * straight in the app that has them.
 *
 * Reading them:
 * - DroidDeck: its ContentProvider ([DroidDeckContract]) once it has one (proposed upstream; not in 0.7.1), else its
 *   `files/library-cache.json` as root;
 * - GameNative: a copy of its database, made as root ([GameNativeLibrary]).
 * Both go through one root pass (app-games.sh, through Shizuku like Save sync): one `stat` of both apps' files tells
 * whether anything changed, and only what changed is read again. Coming back to Tandem does that at most once a
 * minute; a rescan or a cold start always does.
 *
 * Each game becomes a small file in Tandem's own folder (files/app-games/games/<stem>.<store>, see
 * [AppLibrary.stubText]), never in the user's game folders, so players, play time, resume and Steam achievements work
 * on it as on any shortcut. The same game in both apps is one tile ([AppLibrary.tiles]) whose file names each app's
 * id; it starts in the app it was last played in, and Y › More › Emulator switches. A tile's key
 * ("windows/<stem>.<store>") stays the same while either app has the game, and comes back with it. The art is copied
 * into ROMs/downloaded_media/windows/{icon,hero,logo}/ under the same name, like scraped art, only again when the
 * picture changed: DroidDeck's own pictures, and the ones GameNative keeps in its picture cache. Tandem never goes
 * online for these (Steam games' art from Steam is SteamArt's, as for any .steam shortcut). A shortcut file of the
 * user's for the same game wins: one tile, the user's, which gets the apps' art where it has none.
 */
object AppLibraries {
    /** The system the apps' games join. */
    const val SYSTEM = "windows"
    private const val TAG = "Tandem"
    private const val SCRIPT = "app-games.sh"
    /**
     * Coming back to Tandem asks root at most this often (a rescan or a cold start always does): each `su` can make the
     * root manager show a toast.
     */
    private const val RESUME_ROOT_MS = 60_000L

    sealed interface Status {
        data object Off : Status
        data object Checking : Status
        data object NotInstalled : Status
        data object NeedsRoot : Status
        /** [count] games Tandem can start (every store); [shared] when the app's provider gave them (no root needed). */
        data class Found(val count: Int, val shared: Boolean) : Status
    }

    /** What Settings shows for each app. */
    var droidDeck by mutableStateOf<Status>(Status.Checking)
        private set
    var gameNative by mutableStateOf<Status>(Status.Checking)
        private set

    /** One app's games as last read; [sig] tells whether they changed (its files' size and time, or the provider's rows). */
    @Serializable
    private data class Source(val sig: String = "", val games: List<AppGame> = emptyList())

    /**
     * What Tandem last read, kept in files/app-games/library.json. [art] is "<stem>/<kind>" to "<picture's
     * signature>|<saved file>"; [stems] is each tile's file name ([AppLibrary.tiles]); [takesSource] whether the files
     * name DroidDeck's store ([AppLibrary.droidDeckTakesSource]).
     */
    @Serializable
    private data class Snapshot(
        val droidDeck: Source = Source(),
        val gameNative: Source = Source(),
        val art: Map<String, String> = emptyMap(),
        val stems: Map<String, String> = emptyMap(),
        val takesSource: Boolean = false,
    )

    /** DroidDeck's own file before GameNative joined (files/droiddeck/library.json), read once to carry it over. */
    @Serializable
    private data class OldDroidDeck(val sig: String = "", val games: List<AppGame> = emptyList(), val art: Map<String, String> = emptyMap())

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    @Volatile private var dir: File? = null
    @Volatile private var snapshot = Snapshot()
    @Volatile private var tiles: List<Tile> = emptyList()
    @Volatile private var byFile: Map<String, Tile> = emptyMap()
    /** The user's shortcut files that stand for a tile (see [withLibrary]): game key to tile. */
    @Volatile private var absorbed: Map<String, Tile> = emptyMap()
    @Volatile private var rootAt = 0L
    private val lock = Any()

    fun init(ctx: Context) {
        if (dir != null) return
        synchronized(lock) {
            if (dir != null) return
            val files = ctx.applicationContext.filesDir
            val d = File(files, "app-games")
            var s = runCatching { json.decodeFromString<Snapshot>(File(d, "library.json").readText()) }.getOrNull()
            if (s == null) {
                // Carried over from when only DroidDeck's games came in: the same games, keys and copied pictures.
                val old = File(files, "droiddeck")
                s = runCatching { json.decodeFromString<OldDroidDeck>(File(old, "library.json").readText()) }.getOrNull()
                    ?.let { o -> Snapshot(droidDeck = Source(o.sig, o.games), art = o.art) } ?: Snapshot()
                // The games' files move to the new folder: the next scan replaces the library, whatever else changed.
                if (old.exists()) ScanCache.bump()
                old.deleteRecursively()
            }
            snapshot = s
            dir = d
            setTiles(build(s))
            val p = SystemPrefs.data
            droidDeck = initial(p.droidDeck, DroidDeck.installed(ctx), s.droidDeck)
            gameNative = initial(p.gameNative, GameNative.installed(ctx), s.gameNative)
        }
    }

    private fun initial(on: Boolean, installed: Boolean, src: Source): Status = when {
        !on -> Status.Off
        !installed -> Status.NotInstalled
        src.sig.isNotEmpty() -> Status.Found(src.games.count { it.launchable }, src.sig.startsWith("provider:"))
        else -> Status.Checking
    }

    private fun gamesDir(): File? = dir?.let { File(it, "games") }

    private fun stubFor(t: Tile): File? = gamesDir()?.let { File(it, t.fileName) }

    /** The tiles of the apps that are switched on, from [s]. */
    private fun build(s: Snapshot): Pair<List<Tile>, Map<String, String>> {
        val p = SystemPrefs.data
        val games = (if (p.droidDeck) s.droidDeck.games else emptyList()) + (if (p.gameNative) s.gameNative.games else emptyList())
        return AppLibrary.tiles(games, s.stems)
    }

    private fun setTiles(built: Pair<List<Tile>, Map<String, String>>) {
        tiles = built.first
        byFile = built.first.associateBy { it.fileName }
    }

    /** True for a game that is one of the apps', not a file of the user's. */
    fun owns(game: Game): Boolean {
        val d = gamesDir() ?: return false
        return game.platform.id == SYSTEM && game.file.parent == d.path
    }

    private fun tileOf(game: Game): Tile? = if (owns(game)) byFile[game.file.name] else null

    /**
     * The players offered for [game], out of [ps] (the ones that open its file): for an app's game of another store
     * than Steam, only the apps that have it, since each knows it by its own id. A Steam game can go to any of them.
     */
    fun playersFor(game: Game, ps: List<LaunchSpec>): List<LaunchSpec> {
        val t = tileOf(game) ?: return ps
        if (t.source == AppGame.STEAM) return ps
        val pkgs = t.apps.map(AppLibrary::packageOf).toSet()
        return ps.filter { it.packageName in pkgs }
    }

    /**
     * The app one of the apps' games starts in, out of [ps]: the one it was last played in, else the first that has it
     * ([AppLibrary.defaultApp]); null when the user picked a player for it (Y › More › Emulator) that is still there.
     */
    fun playerFor(game: Game, ps: List<LaunchSpec>): LaunchSpec? {
        val t = tileOf(game) ?: return null
        if (game.meta.emulator != null && ps.any { it.name == game.meta.emulator }) return null
        val pkg = AppLibrary.packageOf(AppLibrary.defaultApp(t))
        return ps.firstOrNull { it.packageName == pkg } ?: t.apps.firstNotNullOfOrNull { a -> ps.firstOrNull { it.packageName == AppLibrary.packageOf(a) } }
    }

    /**
     * The file to start [game] from in [spec]: a shortcut of the user's that stands for an app's game, started in one of
     * the apps that have it, goes by Tandem's file, which holds each app's own id (a GameNative export for Epic holds
     * GameNative's number, which DroidDeck doesn't know). Null to use the game's own file.
     */
    fun launchFile(game: Game, spec: LaunchSpec): File? {
        val t = absorbed[game.key] ?: return null
        if (t.apps.none { AppLibrary.packageOf(it) == spec.packageName }) return null
        return stubFor(t)?.takeIf { it.isFile }
    }

    private fun gameOf(platform: Platform, t: Tile, stub: File) = Game(platform, stub, t.name, group = null)

    /** What a file in the game folders stands for, or null for one of the apps' own. */
    private fun refOf(g: Game): AppLibrary.Ref? =
        if (owns(g)) null else AppLibrary.refOf(g.file.extension, GameFiles.firstLine(g.file))

    /** [platform]'s games from the folders ([scanned]) with the apps' added (see [AppLibrary.merge]). */
    fun withLibrary(platform: Platform, scanned: List<Game>): List<Game> {
        if (platform.id != SYSTEM) return scanned
        val ready = tiles.filter { t -> stubFor(t)?.isFile == true }
        if (ready.isEmpty()) {
            absorbed = emptyMap()
            return scanned
        }
        val taken = HashMap<String, Tile>()
        val merged = AppLibrary.merge(scanned, ready, { g -> refOf(g)?.also { r -> AppLibrary.tileFor(r, ready)?.let { taken[g.key] = it } } }) { t ->
            gameOf(platform, t, stubFor(t)!!)
        }
        absorbed = taken
        return merged
    }

    /**
     * Brings Tandem's copy of both apps' libraries up to date, before a scan. Cheap when nothing changed: a provider
     * query, or one root call for the files' sizes and times. [force] (a rescan, a cold start) also looks at the
     * pictures again and asks root however recently it last did. True when the games changed (the scan cache is told,
     * so the scan that follows replaces the library). Blocking: call off the main thread.
     */
    fun sync(ctx: Context, platforms: List<Platform>, force: Boolean): Boolean {
        init(ctx)
        val before = tiles
        runCatching { pass(ctx.applicationContext, platforms.firstOrNull { it.id == SYSTEM }, force) }
            .onFailure { Log.w(TAG, "Reading DroidDeck's and GameNative's libraries failed", it) }
        val changed = tiles != before
        if (changed) ScanCache.bump()
        return changed
    }

    private fun pass(ctx: Context, windows: Platform?, force: Boolean) {
        val s = snapshot
        val prefs = SystemPrefs.data
        val ddVersion = if (prefs.droidDeck) DroidDeck.versionCode(ctx) else null
        val ddOn = ddVersion != null
        val gnOn = prefs.gameNative && GameNative.installed(ctx)
        // Uninstalled: its games go with it (Tandem's records of them stay, keyed by their files' names).
        var dd = if (prefs.droidDeck && !ddOn) Source() else s.droidDeck
        var gn = if (prefs.gameNative && !gnOn) Source() else s.gameNative

        val provided = if (ddOn) DroidDeck.queryProvider(ctx) else null
        if (provided != null) {
            // The rows carry each picture's link with its time in it, so a changed picture changes them too.
            dd = Source("provider:" + provided.hashCode(), provided)
        }
        val ddRoot = ddOn && provided == null
        var rootRan = false
        var rootFailed = false
        var script: String? = null
        if (ddRoot || gnOn) {
            val now = SystemClock.elapsedRealtime()
            val waiting = !force && rootAt != 0L && now - rootAt < RESUME_ROOT_MS &&
                (!ddRoot || droidDeck is Status.Found) && (!gnOn || gameNative is Status.Found)
            if (!waiting) {
                if (!Shell.ready()) {
                    rootFailed = true
                } else {
                    rootAt = now
                    val sc = Shell.script(ctx, SCRIPT)
                    val paths = (if (ddRoot) listOf(DroidDeckLibrary.LIBRARY) else emptyList()) +
                        (if (gnOn) GameNativeLibrary.DB_FILES.take(2) else emptyList())
                    val out = Shell.su(sc, "stat", *paths.toTypedArray())?.takeIf(::ran)
                    if (out == null) {
                        rootFailed = true
                    } else {
                        rootRan = true
                        script = sc
                        val stats = DroidDeckLibrary.statLines(out)
                        if (ddRoot) dd = readDroidDeck(sc, stats, dd)
                        if (gnOn) gn = readGameNative(ctx, sc, stats, gn)
                    }
                }
            }
        }

        val takesSource = ddVersion != null && AppLibrary.droidDeckTakesSource(ddVersion)
        var next = s.copy(droidDeck = dd, gameNative = gn, takesSource = takesSource)
        val built = build(next)
        next = next.copy(stems = built.second)
        val gamesChanged = dd != s.droidDeck || gn != s.gameNative
        if (gamesChanged || takesSource != s.takesSource || built.first != tiles || force) writeStubs(built.first, takesSource)
        setTiles(built)

        if (windows != null && (gamesChanged || force || artMissing(built.first, next.art))) {
            next = next.copy(art = art(ctx, windows, built.first, next.art, provided != null, script.takeIf { rootRan }))
        }
        if (next != s) save(next)

        fun status(on: Boolean, installed: Boolean, viaRoot: Boolean, src: Source, shared: Boolean): Status = when {
            !on -> Status.Off
            !installed -> Status.NotInstalled
            shared -> Status.Found(src.games.count { it.launchable }, shared = true)
            viaRoot && rootFailed -> Status.NeedsRoot
            src.sig.isEmpty() -> Status.Checking
            else -> Status.Found(src.games.count { it.launchable }, shared = false)
        }
        droidDeck = status(prefs.droidDeck, ddOn, ddRoot, dd, provided != null)
        gameNative = status(prefs.gameNative, gnOn, true, gn, false)
    }

    private fun ran(out: String): Boolean = out.lineSequence().any { it.trim() == "ok" }

    /** DroidDeck's library file, read again only when its size or time changed. */
    private fun readDroidDeck(script: String, stats: Map<String, String>, last: Source): Source {
        // No library file yet (DroidDeck never opened, or nothing installed): no games.
        val sig = "root:" + (stats[DroidDeckLibrary.LIBRARY] ?: "none")
        if (sig == last.sig) return last
        if (sig == "root:none") return Source(sig)
        val text = Shell.su(script, "read", DroidDeckLibrary.LIBRARY)?.takeIf(::ran) ?: return last
        // Caught mid-write, say: keep what was there, and read it again next time.
        val games = DroidDeckLibrary.parseOrNull(text.trimEnd().removeSuffix("ok")) ?: return last
        return Source(sig, games)
    }

    /** GameNative's database, copied out and read again only when it or its write-ahead log changed. */
    private fun readGameNative(ctx: Context, script: String, stats: Map<String, String>, last: Source): Source {
        val (db, wal) = GameNativeLibrary.DB_FILES
        val sig = "root:" + (stats[db] ?: "none") + "/" + (stats[wal] ?: "none")
        if (sig == last.sig) return last
        // GameNative never opened: nothing installed.
        if (stats[db] == null) return Source(sig)
        val stage = File(ctx.cacheDir, "gamenative-db")
        try {
            stage.deleteRecursively()
            stage.mkdirs()
            val args = listOf("copy", stage.path, ctx.cacheDir.path) +
                GameNativeLibrary.DB_FILES.flatMap { listOf(it, File(it).name) }
            Shell.su(script, *args.toTypedArray())?.takeIf(::ran) ?: return last
            val copy = File(stage, File(db).name)
            if (!copy.isFile) return last
            val games = GameNative.read(copy) ?: return last
            return Source(sig, games)
        } finally {
            stage.deleteRecursively()
        }
    }

    // ---- Art ----

    private fun artKey(t: Tile, kind: ArtKind) = "${t.stem}/${kind.name}"

    /** Whether the picture saved for [record] ("signature|file") is still [sig], and still there. */
    private fun fresh(record: String?, sig: String): Boolean =
        record != null && record.substringBefore('|') == sig && File(record.substringAfter('|')).isFile

    /** A picture saved for one of [tiles] that has gone missing (cleared downloaded media, say). */
    private fun artMissing(tiles: List<Tile>, art: Map<String, String>): Boolean {
        val stems = tiles.map { it.stem }.toSet()
        return art.any { (key, record) -> key.substringBefore('/') in stems && !File(record.substringAfter('|')).isFile }
    }

    private fun mediaKind(kind: ArtKind): MediaKind = when (kind) {
        ArtKind.Box -> MediaKind.Box
        ArtKind.Hero -> MediaKind.Hero
        ArtKind.Logo -> MediaKind.Logo
    }

    private fun sha1(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-1").digest(bytes).joinToString("") { "%02x".format(it) }

    /**
     * Each tile's best picture of each kind, saved only when it changed: DroidDeck's first (its provider's, else its
     * files as root), then GameNative's picture cache (as root). Root pictures only when this pass ran root ([script]).
     */
    private fun art(ctx: Context, windows: Platform, tiles: List<Tile>, known: Map<String, String>, provider: Boolean, script: String?): Map<String, String> {
        val art = known.toMutableMap()
        val viaRoot = ArrayList<Triple<Tile, ArtKind, List<String>>>()
        for (t in tiles) {
            val stub = stubFor(t) ?: continue
            val dd = t.of(AppLibrary.DROIDDECK)
            val gn = t.of(AppLibrary.GAMENATIVE)
            for (kind in ArtKind.entries) {
                if (provider && dd != null && providerPicture(ctx, windows, t, stub, dd, kind, art)) continue
                val paths = (if (!provider && dd != null) DroidDeckLibrary.artCandidates(dd)[kind].orEmpty() else emptyList()) +
                    (gn?.let { GameNativeLibrary.artCandidates(it)[kind] }.orEmpty())
                if (paths.isNotEmpty()) viaRoot += Triple(t, kind, paths)
            }
        }
        if (script != null && viaRoot.isNotEmpty()) rootArt(ctx, script, windows, viaRoot, art)
        return art
    }

    /** The provider's picture of [kind] for [g]: true when Tandem has it (saved now, or already and unchanged). */
    private fun providerPicture(ctx: Context, windows: Platform, t: Tile, stub: File, g: AppGame, kind: ArtKind, art: MutableMap<String, String>): Boolean {
        val uri = kind.providerUri(g)
        val key = artKey(t, kind)
        val versioned = "?v=" in uri
        if (versioned && fresh(art[key], uri)) return true
        val bytes = try {
            ctx.contentResolver.openInputStream(Uri.parse(uri))?.use { it.readBytes() }
        } catch (e: Exception) {
            null
        } ?: return false
        val sig = if (versioned) uri else sha1(bytes)
        if (fresh(art[key], sig)) return true
        val saved = Media.save(gameOf(windows, t, stub), mediaKind(kind), bytes) ?: return false
        art[key] = "$sig|${saved.path}"
        return true
    }

    /**
     * The first of each set of [wanted] paths that exists, copied out as root and saved, only when its size, time or
     * path changed since the last copy, or the saved one is gone.
     */
    private fun rootArt(ctx: Context, script: String, windows: Platform, wanted: List<Triple<Tile, ArtKind, List<String>>>, art: MutableMap<String, String>) {
        val paths = wanted.flatMap { it.third }.distinct()
        val stats = HashMap<String, String>()
        for (chunk in paths.chunked(80)) {
            val out = Shell.su(script, "stat", *chunk.toTypedArray())?.takeIf(::ran) ?: return
            stats += DroidDeckLibrary.statLines(out)
        }
        class Copy(val tile: Tile, val kind: ArtKind, val src: String, val sig: String) {
            val name = "${tile.stem}-${kind.name}"
        }
        val copies = wanted.mapNotNull { (t, kind, candidates) ->
            val src = candidates.firstOrNull { it in stats } ?: return@mapNotNull null
            val sig = "${stats[src]}:$src"
            if (fresh(art[artKey(t, kind)], sig)) null else Copy(t, kind, src, sig)
        }
        if (copies.isEmpty()) return
        val stage = File(ctx.cacheDir, "app-games-art")
        try {
            stage.deleteRecursively()
            stage.mkdirs()
            for (chunk in copies.chunked(40)) {
                val args = listOf("copy", stage.path, ctx.cacheDir.path) + chunk.flatMap { listOf(it.src, it.name) }
                Shell.su(script, *args.toTypedArray())?.takeIf(::ran) ?: break
                for (c in chunk) {
                    val f = File(stage, c.name)
                    if (!f.isFile) continue
                    val stub = stubFor(c.tile) ?: continue
                    val saved = Media.save(gameOf(windows, c.tile, stub), mediaKind(c.kind), f.readBytes())
                    if (saved != null) art[artKey(c.tile, c.kind)] = "${c.sig}|${saved.path}"
                    f.delete()
                }
            }
        } finally {
            stage.deleteRecursively()
        }
    }

    // ---- Files ----

    /**
     * One small file per tile ([AppLibrary.stubText]); files of games no longer there are removed. "Recently added"
     * goes by a file's time: when the app installed the game, if it says.
     */
    private fun writeStubs(tiles: List<Tile>, takesSource: Boolean) {
        val d = gamesDir() ?: return
        val wanted = tiles.associateBy { it.fileName }
        d.listFiles()?.forEach { if (it.name !in wanted) it.delete() }
        if (wanted.isEmpty()) return
        d.mkdirs()
        for ((name, t) in wanted) {
            val f = File(d, name)
            val text = AppLibrary.stubText(t, takesSource)
            if (!f.isFile || runCatching { f.readText() }.getOrNull() != text) f.writeText(text)
            val at = t.games.map { it.installedAt }.filter { it > 0 }.minOrNull() ?: 0
            if (at > 0 && f.lastModified() != at) f.setLastModified(at)
        }
    }

    private fun save(s: Snapshot) {
        snapshot = s
        val d = dir ?: return
        runCatching {
            d.mkdirs()
            val f = File(d, "library.json")
            val tmp = File(d, "library.json.tmp")
            tmp.writeText(json.encodeToString(s))
            if (!tmp.renameTo(f)) tmp.delete()
        }.onFailure { Log.w(TAG, "Couldn't save the apps' libraries", it) }
    }

    // ---- Art for the user's own shortcuts ----

    private val worker = Executors.newSingleThreadExecutor { r ->
        Thread({ android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND); r.run() }, "tandem-app-games").apply { isDaemon = true }
    }
    private val queued = AtomicBoolean(false)

    /**
     * After a scan: a shortcut of the user's that stands for an app's game gets the apps' pictures of the kinds it has
     * none of (its own art, from any frontend, always stays).
     */
    fun fillShortcutArt() {
        if (absorbed.isEmpty()) return
        if (!queued.compareAndSet(false, true)) return
        worker.execute {
            queued.set(false)
            runCatching { fillPass() }.onFailure { Log.w(TAG, "App art for shortcuts failed", it) }
        }
    }

    private fun fillPass() {
        val art = snapshot.art
        val mine = absorbed
        for (g in Libraries.games) {
            if (g.platform.id != SYSTEM) continue
            val t = mine[g.key] ?: continue
            for (kind in ArtKind.entries) {
                val mk = mediaKind(kind)
                if (Media.has(g, mk)) continue
                val src = art[artKey(t, kind)]?.substringAfter('|')?.let(::File)?.takeIf { it.isFile } ?: continue
                val saved = Media.save(g, mk, src.readBytes()) ?: continue
                Store.update(g.key) { m -> if (mk.get(m) == null) mk.set(m, saved.path) else m }
            }
        }
    }
}
