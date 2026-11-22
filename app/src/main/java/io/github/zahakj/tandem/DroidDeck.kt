package io.github.zahakj.tandem

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
 * The games installed in DroidDeck (Steam games in the real Steam client, through Proton) join the Windows system on
 * their own, with DroidDeck's art, and start straight in DroidDeck.
 *
 * Two ways to read DroidDeck's library, the first that works wins:
 * - its ContentProvider ([DroidDeckContract]), once DroidDeck has one (proposed upstream; not in 0.7.1);
 * - its `files/library-cache.json`, read as root through Shizuku the way Save sync is ([Shell.su], droiddeck.sh).
 *
 * Each game becomes a small `.steam` file in Tandem's own folder (files/droiddeck/games/droiddeck-steam-<id>.steam,
 * holding the app id), never in the user's game folders: the existing DroidDeck player, play time, resume and Steam
 * achievements all work on it as on any Steam shortcut, and its key ("windows/droiddeck-steam-<id>.steam") stays the
 * same for as long as the game exists, so Tandem's records survive an uninstall and reinstall. The art is copied into
 * ROMs/downloaded_media/windows/{icon,hero,logo}/ under the same name, like scraped art, and only again when the
 * picture changed. A .steam or .steamappid shortcut of the user's for the same game wins: one tile, the user's.
 */
object DroidDeck {
    const val PACKAGE = DroidDeckContract.PACKAGE
    /** The system DroidDeck's games join. */
    const val SYSTEM = "windows"
    private const val TAG = "Tandem"
    private const val SCRIPT = "droiddeck.sh"
    /**
     * Coming back to Tandem asks root at most this often (a rescan or a cold start always does): each `su` can
     * make the root manager show a toast.
     */
    private const val RESUME_ROOT_MS = 60_000L

    sealed interface Status {
        data object Off : Status
        data object Checking : Status
        data object NotInstalled : Status
        data object NeedsRoot : Status
        /** [count] games Tandem can start; [shared] when DroidDeck's provider gave them (no root needed). */
        data class Found(val count: Int, val shared: Boolean) : Status
    }

    /** What Settings shows. */
    var status by mutableStateOf<Status>(Status.Checking)
        private set

    /**
     * What Tandem last read, kept in files/droiddeck/library.json: [sig] tells whether the library changed (the
     * file's size and time, or the provider's rows), [art] is "<stem>/<kind>" to "<picture's signature>|<saved file>".
     */
    @Serializable
    private data class Snapshot(
        val sig: String = "",
        val games: List<DroidDeckGame> = emptyList(),
        val art: Map<String, String> = emptyMap(),
    )

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    @Volatile private var dir: File? = null
    @Volatile private var snapshot = Snapshot()
    @Volatile private var rootAt = 0L
    private val lock = Any()

    fun init(ctx: Context) {
        if (dir != null) return
        synchronized(lock) {
            if (dir != null) return
            val d = File(ctx.applicationContext.filesDir, "droiddeck")
            snapshot = runCatching { json.decodeFromString<Snapshot>(File(d, "library.json").readText()) }.getOrDefault(Snapshot())
            dir = d
            status = when {
                !SystemPrefs.data.droidDeck -> Status.Off
                !installed(ctx) -> Status.NotInstalled
                snapshot.sig.isNotEmpty() -> Status.Found(snapshot.games.count { it.launchable }, snapshot.sig.startsWith("provider:"))
                else -> Status.Checking
            }
        }
    }

    private fun gamesDir(): File? = dir?.let { File(it, "games") }

    private fun stubFor(g: DroidDeckGame): File? = gamesDir()?.let { File(it, "${g.stem}.steam") }

    /** True for a game that is one of DroidDeck's, not a file of the user's. */
    fun owns(game: Game): Boolean {
        val d = gamesDir() ?: return false
        return game.platform.id == SYSTEM && game.file.parent == d.path
    }

    /** DroidDeck for one of its own games, unless the user picked another app for it (Y › More › Emulator). */
    fun playerFor(game: Game, players: List<LaunchSpec>): LaunchSpec? {
        if (!owns(game) || game.meta.emulator != null) return null
        return players.firstOrNull { it.packageName == PACKAGE }
    }

    private fun installed(ctx: Context): Boolean =
        runCatching { ctx.packageManager.getPackageInfo(PACKAGE, 0) }.isSuccess

    /** The Steam app id a shortcut of the user's stands for (.steam or .steamappid), or null. */
    private fun shortcutId(g: Game): String? {
        if (owns(g)) return null
        val ext = g.file.extension.lowercase()
        if (ext != "steam" && ext != "steamappid") return null
        return SteamApi.appId(ext, GameFiles.firstLine(g.file), emptyMap())?.toString()
    }

    private fun gameOf(platform: Platform, g: DroidDeckGame, stub: File) =
        Game(platform, stub, g.name.ifBlank { g.appId }, group = null)

    /** [scanned], the Windows system's games from the folders, with DroidDeck's added (see [DroidDeckLibrary.merge]). */
    fun withLibrary(platform: Platform, scanned: List<Game>): List<Game> {
        if (platform.id != SYSTEM || !SystemPrefs.data.droidDeck) return scanned
        val ready = snapshot.games.filter { g -> g.launchable && stubFor(g)?.isFile == true }
        if (ready.isEmpty()) return scanned
        return DroidDeckLibrary.merge(scanned, ready, ::shortcutId) { g -> gameOf(platform, g, stubFor(g)!!) }
    }

    /**
     * Brings Tandem's copy of DroidDeck's library up to date, before a scan. Cheap when nothing changed: a provider
     * query, or one root call for the file's size and time. [force] (a rescan, a cold start) also looks at the
     * pictures again and asks root however recently it last did. True when the games changed (the scan cache is
     * told, so the scan that follows replaces the library). Blocking: call off the main thread.
     */
    fun sync(ctx: Context, platforms: List<Platform>, force: Boolean): Boolean {
        init(ctx)
        val before = snapshot.games
        val now = runCatching { pass(ctx.applicationContext, platforms.firstOrNull { it.id == SYSTEM }, force) }
            .onFailure { Log.w(TAG, "Reading DroidDeck's library failed", it) }
            .getOrNull() ?: return false
        status = now
        val changed = snapshot.games != before
        if (changed) ScanCache.bump()
        return changed
    }

    private fun pass(ctx: Context, windows: Platform?, force: Boolean): Status {
        if (!SystemPrefs.data.droidDeck) return Status.Off
        if (!installed(ctx)) {
            // Uninstalled: its games go with it (Tandem's records of them stay, keyed by their files' names).
            if (snapshot.games.isNotEmpty() || snapshot.sig.isNotEmpty()) save(Snapshot(art = snapshot.art))
            writeStubs(emptyList())
            return Status.NotInstalled
        }
        return fromProvider(ctx, windows, force) ?: fromRoot(ctx, windows, force)
    }

    // ---- The provider ----

    private fun fromProvider(ctx: Context, windows: Platform?, force: Boolean): Status? {
        if (ctx.packageManager.resolveContentProvider(DroidDeckContract.AUTHORITY, 0) == null) return null
        if (ctx.checkSelfPermission(DroidDeckContract.PERMISSION) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "DroidDeck's library provider is there, but ${DroidDeckContract.PERMISSION} isn't granted")
            return null
        }
        val games = try {
            queryProvider(ctx)
        } catch (e: Exception) {
            Log.w(TAG, "DroidDeck's library provider failed", e)
            null
        } ?: return null
        watch(ctx)
        // The rows carry each picture's link with its time in it, so a changed picture changes them too.
        val sig = "provider:" + games.hashCode()
        val changed = sig != snapshot.sig
        if (changed) writeStubs(games)
        var art = snapshot.art
        if (windows != null && (changed || force || artMissing(games, art))) art = providerArt(ctx, windows, games, art)
        if (changed || art != snapshot.art) save(Snapshot(sig, games, art))
        return Status.Found(games.count { it.launchable }, shared = true)
    }

    private fun queryProvider(ctx: Context): List<DroidDeckGame>? {
        val c = DroidDeckContract
        return ctx.contentResolver.query(Uri.parse(c.gamesUri()), null, null, null, null)?.use { cur ->
            val id = cur.getColumnIndex(c.COL_APP_ID)
            if (id < 0) return@use null
            fun text(col: String): String? = cur.getColumnIndex(col).takeIf { it >= 0 && !cur.isNull(it) }?.let(cur::getString)
            fun millis(col: String): Long = cur.getColumnIndex(col).takeIf { it >= 0 && !cur.isNull(it) }?.let(cur::getLong)?.coerceAtLeast(0) ?: 0
            val out = ArrayList<DroidDeckGame>()
            while (cur.moveToNext()) {
                val appId = DroidDeckLibrary.normalId(text(c.COL_APP_ID)) ?: continue
                out += DroidDeckGame(
                    source = DroidDeckLibrary.normalSource(text(c.COL_SOURCE)),
                    appId = appId,
                    name = text(c.COL_NAME)?.trim().orEmpty(),
                    installedAt = millis(c.COL_INSTALLED_AT),
                    lastPlayed = millis(c.COL_LAST_PLAYED),
                    art = text(c.COL_CAPSULE_URI),
                    hero = text(c.COL_HERO_URI),
                    logo = text(c.COL_LOGO_URI),
                    icon = text(c.COL_ICON_URI),
                )
            }
            out.distinctBy { it.id }
        }
    }

    /**
     * Each picture the provider has, saved only when it changed: its link (which carries the picture's time) when the
     * row gives one, else a hash of its bytes.
     */
    private fun providerArt(ctx: Context, windows: Platform, games: List<DroidDeckGame>, known: Map<String, String>): Map<String, String> {
        val art = known.toMutableMap()
        for (g in games) {
            if (!g.launchable) continue
            val stub = stubFor(g) ?: continue
            for (kind in ArtKind.entries) {
                val uri = kind.providerUri(g)
                val key = artKey(g, kind)
                val versioned = "?v=" in uri
                if (versioned && fresh(known[key], uri)) continue
                val bytes = try {
                    ctx.contentResolver.openInputStream(Uri.parse(uri))?.use { it.readBytes() }
                } catch (e: Exception) {
                    null
                } ?: continue
                val sig = if (versioned) uri else sha1(bytes)
                if (fresh(known[key], sig)) continue
                Media.save(gameOf(windows, g, stub), mediaKind(kind), bytes)?.let { art[key] = "$sig|${it.path}" }
            }
        }
        return art
    }

    @Volatile private var watching = false

    /** While Tandem runs, a change to DroidDeck's library (it notifies on every save) brings a quiet rescan. */
    private fun watch(ctx: Context) {
        if (watching) return
        val app = ctx.applicationContext
        val main = android.os.Handler(android.os.Looper.getMainLooper())
        val observer = object : android.database.ContentObserver(main) {
            override fun onChange(selfChange: Boolean) {
                io.github.zahakj.tandem.ui.LibraryScan.request(app, announce = false, onlyIfChanged = true)
            }
        }
        runCatching {
            app.contentResolver.registerContentObserver(Uri.parse(DroidDeckContract.gamesUri()), true, observer)
            watching = true
        }.onFailure { Log.w(TAG, "Couldn't watch DroidDeck's library", it) }
    }


    // ---- Root ----

    private fun fromRoot(ctx: Context, windows: Platform?, force: Boolean): Status {
        val last = Status.Found(snapshot.games.count { it.launchable }, shared = false)
        if (!Shell.ready()) return Status.NeedsRoot
        val now = SystemClock.elapsedRealtime()
        if (!force && rootAt != 0L && now - rootAt < RESUME_ROOT_MS && status is Status.Found) return last
        rootAt = now
        val script = Shell.script(ctx, SCRIPT)
        val out = Shell.su(script, "stat", DroidDeckLibrary.LIBRARY) ?: return Status.NeedsRoot
        if (!ran(out)) return Status.NeedsRoot
        // No library file yet (DroidDeck never opened, or nothing installed): no games.
        val sig = "root:" + (DroidDeckLibrary.statLines(out)[DroidDeckLibrary.LIBRARY] ?: "none")
        val changed = sig != snapshot.sig
        var games = snapshot.games
        if (changed) {
            games = if (sig == "root:none") emptyList() else {
                val text = Shell.su(script, "read", DroidDeckLibrary.LIBRARY)?.takeIf(::ran) ?: return Status.NeedsRoot
                // Caught mid-write, say: keep what was there, and read it again next time.
                DroidDeckLibrary.parseOrNull(text.trimEnd().removeSuffix("ok")) ?: return last
            }
            writeStubs(games)
        }
        var art = snapshot.art
        if (windows != null && (changed || force || artMissing(games, art))) art = rootArt(ctx, script, windows, games, art)
        if (changed || art != snapshot.art) save(Snapshot(sig, games, art))
        return Status.Found(games.count { it.launchable }, shared = false)
    }

    private fun ran(out: String): Boolean = out.lineSequence().any { it.trim() == "ok" }

    /**
     * The best picture of each kind (DroidDeck's SteamGridDB art, else Steam's), copied out as root and saved, only
     * when its size, time or path changed since the last copy, or the saved one is gone.
     */
    private fun rootArt(ctx: Context, script: String, windows: Platform, games: List<DroidDeckGame>, known: Map<String, String>): Map<String, String> {
        val wanted = games.filter { it.launchable }.associateWith { DroidDeckLibrary.artCandidates(it) }
        val paths = wanted.values.flatMap { k -> k.values.flatten() }.distinct()
        if (paths.isEmpty()) return known
        val stats = HashMap<String, String>()
        for (chunk in paths.chunked(80)) {
            val out = Shell.su(script, "stat", *chunk.toTypedArray())?.takeIf(::ran) ?: return known
            stats += DroidDeckLibrary.statLines(out)
        }
        class Copy(val game: DroidDeckGame, val kind: ArtKind, val src: String, val sig: String) {
            val name = "${game.stem}-${kind.name}"
        }
        val copies = ArrayList<Copy>()
        for ((g, kinds) in wanted) for ((kind, candidates) in kinds) {
            val src = candidates.firstOrNull { it in stats } ?: continue
            val sig = "${stats[src]}:$src"
            if (!fresh(known[artKey(g, kind)], sig)) copies += Copy(g, kind, src, sig)
        }
        if (copies.isEmpty()) return known
        val art = known.toMutableMap()
        val stage = File(ctx.cacheDir, "droiddeck-art")
        try {
            stage.deleteRecursively()
            stage.mkdirs()
            for (chunk in copies.chunked(40)) {
                val args = listOf("copy", stage.path, ctx.cacheDir.path) + chunk.flatMap { listOf(it.src, it.name) }
                Shell.su(script, *args.toTypedArray())?.takeIf(::ran) ?: break
                for (c in chunk) {
                    val f = File(stage, c.name)
                    if (!f.isFile) continue
                    val stub = stubFor(c.game) ?: continue
                    val saved = Media.save(gameOf(windows, c.game, stub), mediaKind(c.kind), f.readBytes())
                    if (saved != null) art[artKey(c.game, c.kind)] = "${c.sig}|${saved.path}"
                    f.delete()
                }
            }
        } finally {
            stage.deleteRecursively()
        }
        return art
    }

    // ---- Shared ----

    private fun artKey(g: DroidDeckGame, kind: ArtKind) = "${g.stem}/${kind.name}"

    /** Whether the picture saved for [record] ("signature|file") is still [sig], and still there. */
    private fun fresh(record: String?, sig: String): Boolean =
        record != null && record.substringBefore('|') == sig && File(record.substringAfter('|')).isFile

    /** A picture saved for one of [games] that has gone missing (cleared downloaded media, say). */
    private fun artMissing(games: List<DroidDeckGame>, art: Map<String, String>): Boolean {
        val stems = games.filter { it.launchable }.map { it.stem }.toSet()
        return art.any { (key, record) -> key.substringBefore('/') in stems && !File(record.substringAfter('|')).isFile }
    }

    private fun mediaKind(kind: ArtKind): MediaKind = when (kind) {
        ArtKind.Box -> MediaKind.Box
        ArtKind.Hero -> MediaKind.Hero
        ArtKind.Logo -> MediaKind.Logo
    }

    private fun sha1(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-1").digest(bytes).joinToString("") { "%02x".format(it) }

    /** One small file per game Tandem can start, holding its app id; files of games no longer there are removed. */
    private fun writeStubs(games: List<DroidDeckGame>) {
        val d = gamesDir() ?: return
        val wanted = games.filter { it.launchable }.associateBy { "${it.stem}.steam" }
        d.listFiles()?.forEach { if (it.name !in wanted) it.delete() }
        if (wanted.isEmpty()) return
        d.mkdirs()
        for ((name, g) in wanted) {
            val f = File(d, name)
            val text = "${g.appId}\n"
            if (!f.isFile || runCatching { f.readText() }.getOrNull() != text) f.writeText(text)
            // "Recently added" goes by the file's time: when DroidDeck installed it, if it says.
            val at = g.installedAt
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
        }.onFailure { Log.w(TAG, "Couldn't save DroidDeck's library", it) }
    }

    // ---- Art for the user's own shortcuts ----

    private val worker = Executors.newSingleThreadExecutor { r ->
        Thread({ android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND); r.run() }, "tandem-droiddeck").apply { isDaemon = true }
    }
    private val queued = AtomicBoolean(false)

    /**
     * After a scan: a .steam or .steamappid shortcut of the user's for a game DroidDeck has gets DroidDeck's pictures
     * of the kinds it has none of (its own art, from any frontend, always stays).
     */
    fun fillShortcutArt() {
        if (!SystemPrefs.data.droidDeck || snapshot.games.isEmpty()) return
        if (!queued.compareAndSet(false, true)) return
        worker.execute {
            queued.set(false)
            runCatching { fillPass() }.onFailure { Log.w(TAG, "DroidDeck art for shortcuts failed", it) }
        }
    }

    private fun fillPass() {
        val s = snapshot
        val byId = s.games.filter { it.launchable }.associateBy { it.appId }
        for (g in Libraries.games) {
            if (g.platform.id != SYSTEM) continue
            val dd = byId[shortcutId(g) ?: continue] ?: continue
            for (kind in ArtKind.entries) {
                val mk = mediaKind(kind)
                if (Media.has(g, mk)) continue
                val src = s.art[artKey(dd, kind)]?.substringAfter('|')?.let(::File)?.takeIf { it.isFile } ?: continue
                val saved = Media.save(g, mk, src.readBytes()) ?: continue
                Store.update(g.key) { m -> if (mk.get(m) == null) mk.set(m, saved.path) else m }
            }
        }
    }
}
