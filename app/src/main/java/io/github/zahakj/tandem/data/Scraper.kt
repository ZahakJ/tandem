package io.github.zahakj.tandem.data

import io.github.zahakj.tandem.Privacy

import android.content.Context
import android.os.Process
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.zahakj.tandem.Game
import io.github.zahakj.tandem.Libraries
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/** Which games a scrape covers. Hidden games are always left out. */
sealed interface ScrapeScope {
    data object All : ScrapeScope
    data class System(val platformId: String) : ScrapeScope
    data class Folder(val platformId: String, val folder: String) : ScrapeScope
    data class One(val key: String) : ScrapeScope
}

data class ScrapeOptions(
    val kinds: Set<MediaKind>,
    val metadata: Boolean,
    val mode: ScrapeMode,
    val region: Region,
    val language: String,
    val details: Set<MetaType> = MetaType.entries.toSet(),
    /** Lets this run replace (and unlock) fields the user set by hand. */
    val replaceLocked: Boolean = false,
    /** Sources to ask, in order. */
    val sources: List<ScrapeSource> = ScrapeSource.defaultOrder,
    /** The user agreed to download (or refresh) the LaunchBox database first. */
    val downloadLaunchBox: Boolean = false,
) {
    /** The details this run looks for. */
    val types: Set<MetaType> get() = when {
        mode == ScrapeMode.Basics -> BASIC_TYPES
        metadata -> details
        else -> emptySet()
    }

    /**
     * What this run fetches for a game with details [m], [has] telling which art it already has (from any frontend,
     * or put there by hand). Art and details are judged separately: a game with every picture but no description
     * still gets its details in "Missing basics" and "Missing selected". Locked fields are left out unless
     * [replaceLocked].
     */
    fun needs(m: GameMeta, has: (MediaKind) -> Boolean): Pair<List<MediaKind>, List<MetaType>> {
        val locked = if (replaceLocked) emptySet() else m.lockedFields
        val force = mode == ScrapeMode.Refresh
        return if (mode == ScrapeMode.Basics) {
            // Missing basics: only games without box art or a description, and only those.
            val needBox = MediaKind.Box.field !in locked && !has(MediaKind.Box)
            val needText = MetaType.Description.missing(m) && MetaField.DESCRIPTION !in locked
            listOfNotNull(MediaKind.Box.takeIf { needBox }) to
                (if (needText) BASIC_TYPES.filter { t -> t.missing(m) && t.fields.any { it !in locked } } else emptyList())
        } else {
            kinds.filter { k -> k.field !in locked && (force || !has(k)) } to
                types.filter { t -> t.fields.any { it !in locked } && (force || t.missing(m)) }
        }
    }

    companion object {
        /** What "Missing basics" fills: box art and the main details. */
        val BASIC_TYPES = setOf(MetaType.Name, MetaType.Description, MetaType.Genres, MetaType.Year, MetaType.Credits)
    }
}

enum class RunState { Running, Paused, Done, Cancelled }

/** A cover (or other picture) that just arrived, for the live progress view. */
data class Found(val key: String, val title: String, val file: File)

/**
 * One scrape: what it covers and how far it got. The fields are Compose state, refreshed from the workers'
 * counters at most four times a second so a big run never floods the UI with recompositions.
 */
class ScrapeRun(val scope: ScrapeScope, val options: ScrapeOptions, val total: Int) {
    var state by mutableStateOf(RunState.Running)
    var done by mutableIntStateOf(0)
    var withArt by mutableIntStateOf(0)
    /** Games a source knew but whose downloads (or lookups) kept failing; another run may get them. */
    var failed by mutableIntStateOf(0)
    var withDetails by mutableIntStateOf(0)
    var upToDate by mutableIntStateOf(0)
    var bytes by mutableLongStateOf(0L)
    var current by mutableStateOf("")
    /** The source being asked right now ("LaunchBox", "libretro"…), for the progress card. */
    var source by mutableStateOf("")
    /** Seconds left at the recent pace, rounded so the label doesn't change every tick; null until there's a pace. */
    var eta by mutableStateOf<Int?>(null)
    /** True when the user paused it (rather than a game being in front). */
    var held by mutableStateOf(false)
    /** 0..1 while getting ready (downloading the LaunchBox database), else null. */
    var prep by mutableStateOf<Float?>(null)
    var recent by mutableStateOf<List<Found>>(emptyList())
    /** Keys of games nothing was found for. */
    var missed by mutableStateOf<List<String>>(emptyList())
    var notes by mutableStateOf<List<String>>(emptyList())
    val finished get() = state == RunState.Done || state == RunState.Cancelled
    val missedCount get() = missed.size
    /** Missed games no source knew at all (the rest of [missed] are [failed]). */
    val notFound get() = (missed.size - failed).coerceAtLeast(0)
}

/** Everything the chooser found for one game. */
data class Gallery(val byKind: Map<MediaKind, List<Candidate>>, val notes: List<String>)

/**
 * Scraping. Runs in its own scope so it keeps going when the user leaves the scrape screen, pauses while a game
 * is in front, and only ever touches the network after the user starts it.
 *
 * It runs on its own few low-priority threads rather than Dispatchers.IO: that pool is also where Coil loads the
 * library's art, and a scrape decoding big downloads there made covers (and everything waiting on them) stall.
 */
object Scraper {
    /** Most games worked on at once, whatever ScreenScraper's account allows. */
    private const val MAX_WORKERS = 4

    private val pool = ThreadPoolExecutor(MAX_WORKERS + 1, MAX_WORKERS + 1, 20, TimeUnit.SECONDS, LinkedBlockingQueue()) { r ->
        // Background priority (and the background CPU group): the screens' frames always come first.
        Thread({ Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND); r.run() }, "tandem-scrape").apply { isDaemon = true }
    }.apply { allowCoreThreadTimeOut(true) }.asCoroutineDispatcher()

    private val scope = CoroutineScope(SupervisorJob() + pool)
    private var job: Job? = null
    private var appContext: Context? = null

    var run by mutableStateOf<ScrapeRun?>(null)
        private set

    /** True while Tandem is in the background (a game is running): workers wait before their next step. */
    val backgrounded = MutableStateFlow(false)

    /** True while the user has paused the run. */
    private val held = MutableStateFlow(false)

    /** Workers wait while this is true. */
    private val blocked = combine(backgrounded, held) { a, b -> a || b }.stateIn(scope, SharingStarted.Eagerly, false)

    val running get() = run?.finished == false

    fun init(ctx: Context) { appContext = ctx.applicationContext }

    /** True on a metered connection (mobile data, a hotspot). */
    fun metered(): Boolean = appContext?.let { LaunchBoxDb.metered(it) } == true

    /** Games a scope covers, never including hidden ones. */
    fun gamesIn(scope: ScrapeScope): List<Game> {
        val all = Libraries.games
        val picked = when (scope) {
            ScrapeScope.All -> all
            is ScrapeScope.System -> all.filter { it.platform.id == scope.platformId }
            is ScrapeScope.Folder -> all.filter { it.platform.id == scope.platformId && it.group == scope.folder }
            is ScrapeScope.One -> all.filter { it.key == scope.key }
        }
        return picked.filter { !Privacy.isHidden(it) }
    }

    fun optionsFromSettings(): ScrapeOptions = ScrapeConfig.settings.let {
        ScrapeOptions(it.kinds, it.metadata, it.mode, it.regionPref, it.languagePref, it.details, it.replaceLocked, it.active)
    }

    /**
     * Why LaunchBox can't be used for [scope] yet: null when it's ready (or off), else what's missing. The scrape
     * screen asks before downloading (it's about 100 MB).
     */
    fun launchBoxNeeds(scope: ScrapeScope, options: ScrapeOptions): Set<String>? {
        if (ScrapeSource.LaunchBox !in options.sources) return null
        val wanted = gamesIn(scope).mapNotNull { ScrapeSystems.launchBox(it) }.toSet()
        if (wanted.isEmpty()) return null
        return wanted.takeIf { LaunchBoxDb.missing(it) }
    }

    /** Starts a scrape. Returns false when one is already running or there is nothing to do. */
    fun start(scrapeScope: ScrapeScope, options: ScrapeOptions): Boolean {
        if (running) return false
        val ctx = appContext ?: return false
        val games = gamesIn(scrapeScope)
        if (games.isEmpty()) return false
        val r = ScrapeRun(scrapeScope, options, games.size)
        held.value = false
        run = r
        job = scope.launch {
            try {
                execute(ctx, r, games)
            } catch (e: Exception) {
                rethrowCancel(e)
                android.util.Log.w("Tandem", "Scrape stopped", e)
                withContext(Dispatchers.Main) { r.notes = r.notes + "Something went wrong, so I stopped early"; r.state = RunState.Done }
            }
        }
        return true
    }

    fun cancel() {
        val r = run ?: return
        if (r.finished) return
        job?.cancel()
        held.value = false
        r.state = RunState.Cancelled
    }

    /** Pauses or resumes the run (the games being worked on finish first). */
    fun togglePause() {
        val r = run ?: return
        if (r.finished) return
        val hold = !held.value
        held.value = hold
        r.held = hold
        r.state = if (hold || backgrounded.value) RunState.Paused else RunState.Running
    }

    /** Forgets a finished run (back to the setup view). */
    fun dismiss() { if (run?.finished == true) run = null }

    /** What was found is saved at least this often, or as soon as this many games have something. */
    private const val FLUSH_MS = 5_000L
    private const val FLUSH_GAMES = 20

    // -----------------------------------------------------------------------------------------------------------

    /** Counters the workers update; copied into the [ScrapeRun] by a ticker. */
    private class Progress {
        val done = AtomicInteger()
        val withArt = AtomicInteger()
        val failed = AtomicInteger()
        val withDetails = AtomicInteger()
        val upToDate = AtomicInteger()
        val bytes = AtomicLong()
        @Volatile var current = ""
        @Volatile var source = ""
        @Volatile var prep: Float? = null
        val recent = ConcurrentLinkedQueue<Found>()
        val missed = ConcurrentLinkedQueue<String>()
        val notes = ConcurrentLinkedQueue<String>()
        val pending = ConcurrentHashMap<String, (GameMeta) -> GameMeta>()
        /** Bumped whenever one of the lists changes, so the UI copies them only then. */
        val listVersion = AtomicInteger()
        var publishedVersion = -1
        /** (time, games done) over the last minute or so, for the time left. Main thread only. */
        val pace = ArrayDeque<Pair<Long, Int>>()

        fun note(text: String) { if (text !in notes) { notes += text; listVersion.incrementAndGet() } }
        fun found(f: Found) { recent += f; while (recent.size > 40) recent.poll(); listVersion.incrementAndGet() }
        fun miss(key: String) { missed += key; listVersion.incrementAndGet() }
    }

    private suspend fun execute(ctx: Context, r: ScrapeRun, games: List<Game>) {
        val p = Progress()
        val cfg = ScrapeConfig.settings
        val o = r.options
        val libretro = Libretro(ctx.cacheDir)

        coroutineScope {
            // Copy progress to the UI four times a second, and save what was found in batches: each change to the
            // library's details makes every screen showing games look again, so never once per game.
            val ticker = launch(Dispatchers.Main) {
                var lastFlush = SystemClock.uptimeMillis()
                while (isActive) {
                    publish(p, r)
                    val now = SystemClock.uptimeMillis()
                    val due = p.pending.size >= FLUSH_GAMES || now - lastFlush >= FLUSH_MS || blocked.value
                    if (due && p.pending.isNotEmpty()) { flush(p); lastFlush = now }
                    delay(250)
                }
            }
            try {
                val src = o.sources
                var ss: ScreenScraper? = null
                var ssThreads = 1
                if (ScrapeSource.ScreenScraper in src && cfg.hasScreenScraper) {
                    p.current = "Signing in to ScreenScraper…"
                    try {
                        val s = ScreenScraper(cfg)
                        ssThreads = runInterruptible { s.signIn() }
                        ss = s
                        ScrapeConfig.update { it.copy(ssVerifiedUser = it.ssUser, ssThreads = ssThreads) }
                    } catch (e: SourceDown) {
                        p.note(e.message ?: "ScreenScraper is unavailable")
                    } catch (e: Exception) { rethrowCancel(e)
                        p.note("Couldn't reach ScreenScraper, using the other sources")
                    }
                }
                var igdb: Igdb? = null
                if (ScrapeSource.Igdb in src && cfg.hasIgdb) {
                    p.current = "Signing in to IGDB…"
                    try {
                        igdb = Igdb(cfg.igdbClientId, cfg.igdbSecret).also { runInterruptible { it.signIn() } }
                    } catch (e: SourceDown) {
                        p.note(e.message ?: "IGDB is unavailable")
                    } catch (e: Exception) { rethrowCancel(e)
                        p.note("Couldn't reach IGDB, using the other sources")
                    }
                }
                val launchBox = prepareLaunchBox(ctx, r, p, games)
                if (o.types.isNotEmpty() && src.none { s -> o.types.any { it in s.details } }) {
                    p.note("Details need LaunchBox, ScreenScraper or IGDB turned on")
                }
                val sources = RunSources(
                    order = src, options = o, libretro = libretro, ss = ss, ssThreads = ssThreads, launchBox = launchBox,
                    igdb = igdb, sgdb = if (ScrapeSource.SteamGridDb in src && cfg.hasSteamGridDb) SteamGridDb(cfg.sgdbKey) else null,
                    hltb = if (ScrapeSource.Hltb in src) Hltb() else null, note = p::note,
                )
                val w = Worker(p, o, sources)

                val queue = Channel<Game>(Channel.UNLIMITED)
                games.forEach { queue.trySend(it) }
                queue.close()
                // A few games at once: enough to hide network latency, few enough to stay gentle on the device.
                coroutineScope {
                    repeat(maxOf(3, ssThreads).coerceAtMost(MAX_WORKERS)) {
                        launch {
                            for (g in queue) {
                                waitIfBackgrounded(r)
                                ensureActive()
                                try { w.process(g) } catch (e: Exception) { rethrowCancel(e); p.failed.incrementAndGet(); p.miss(g.key) }
                                p.done.incrementAndGet()
                            }
                        }
                    }
                }
                p.current = ""
                p.source = ""
                withContext(Dispatchers.Main) { flush(p); publish(p, r); r.state = RunState.Done }
            } catch (e: CancellationException) {
                withContext(kotlinx.coroutines.NonCancellable + Dispatchers.Main) { flush(p); publish(p, r); r.state = RunState.Cancelled }
                throw e
            } finally {
                ticker.cancel()
            }
        }
    }

    /**
     * Gets the LaunchBox database ready and looks every game of the run up in it (on the device, no network).
     * Downloads it first when the user agreed to, or refreshes a month-old copy when on Wi-Fi.
     */
    private suspend fun prepareLaunchBox(ctx: Context, r: ScrapeRun, p: Progress, games: List<Game>): Map<String, LbGame> {
        if (ScrapeSource.LaunchBox !in r.options.sources) return emptyMap()
        val wanted = games.mapNotNull { ScrapeSystems.launchBox(it) }.toSet()
        if (wanted.isEmpty()) return emptyMap()
        val info = LaunchBoxDb.info
        val refresh = info != null && info.stale && !LaunchBoxDb.metered(ctx)
        if (r.options.downloadLaunchBox || refresh) {
            p.current = if (info == null) "Downloading the LaunchBox database…" else "Refreshing the LaunchBox database…"
            try {
                runInterruptible {
                    LaunchBoxDb.download(LaunchBoxDb.wantedPlatforms() + wanted) { f ->
                        p.current = "LaunchBox database: ${(f * 100).toInt()}%"
                        p.prep = f
                    }
                }
            } catch (e: Exception) {
                rethrowCancel(e)
                if (e is java.io.InterruptedIOException) throw CancellationException("cancelled")
                android.util.Log.w("Tandem", "LaunchBox download failed", e)
                p.note(when {
                    !LaunchBoxDb.online(ctx) -> "No internet connection: connect to Wi-Fi to download the LaunchBox database"
                    LaunchBoxDb.info == null -> "Couldn't download the LaunchBox database"
                    else -> "Couldn't refresh the LaunchBox database, using the one I have"
                })
            } finally {
                p.prep = null
            }
        }
        val index = LaunchBoxDb.index()
        if (index == null) {
            p.note("LaunchBox needs its database: Settings › Art & accounts › LaunchBox")
            return emptyMap()
        }
        if (wanted.any { !index.has(it) } && LaunchBoxDb.info?.platforms?.containsAll(wanted) != true) {
            p.note("Some systems aren't in the LaunchBox download yet; download it again to add them")
        }
        p.current = "Looking games up in LaunchBox…"
        return try {
            runInterruptible { index.find(RunSources.launchBoxQueries(games)) }
        } catch (e: Exception) {
            rethrowCancel(e)
            android.util.Log.w("Tandem", "LaunchBox lookup failed", e)
            p.note("Couldn't read the LaunchBox database")
            emptyMap()
        }
    }

    private suspend fun waitIfBackgrounded(r: ScrapeRun) {
        if (!blocked.value) return
        withContext(Dispatchers.Main) { if (r.state == RunState.Running) r.state = RunState.Paused }
        blocked.first { !it }
        withContext(Dispatchers.Main) { if (r.state == RunState.Paused) r.state = RunState.Running }
    }

    private fun publish(p: Progress, r: ScrapeRun) {
        r.done = p.done.get(); r.withArt = p.withArt.get(); r.withDetails = p.withDetails.get(); r.failed = p.failed.get()
        r.upToDate = p.upToDate.get(); r.bytes = p.bytes.get(); r.current = p.current; r.source = p.source; r.prep = p.prep
        r.eta = eta(p, r)
        val v = p.listVersion.get()
        if (v != p.publishedVersion) {
            p.publishedVersion = v
            r.recent = p.recent.toList().reversed()
            r.missed = p.missed.toList()
            r.notes = p.notes.toList()
        }
    }

    /** Seconds left at the pace of the last minute, rounded; null while paused or before there's a pace. */
    private fun eta(p: Progress, r: ScrapeRun): Int? {
        val now = SystemClock.uptimeMillis()
        val done = p.done.get()
        if (r.state != RunState.Running || p.prep != null) { p.pace.clear(); return null }
        if (p.pace.lastOrNull()?.second != done) p.pace.addLast(now to done)
        while (p.pace.size > 2 && now - p.pace.first().first > 60_000) p.pace.removeFirst()
        val (t0, d0) = p.pace.firstOrNull() ?: return null
        val left = r.total - done
        if (left <= 0) return null
        if (done - d0 < 2) return r.eta
        val secs = ((now - t0) / 1000.0 / (done - d0) * left).toInt()
        // Coarser as it grows: 5 s steps under two minutes, then whole minutes.
        return if (secs < 120) (secs + 4) / 5 * 5 else (secs + 59) / 60 * 60
    }

    private fun flush(p: Progress) {
        if (p.pending.isEmpty()) return
        val batch = HashMap(p.pending)
        batch.keys.forEach { p.pending.remove(it) }
        Store.updateAll(batch)
    }

    /**
     * Scrapes single games. Shared by every worker coroutine of a run. Sources are asked in the user's order, each
     * only for what the ones before couldn't fill, and each at most once per game.
     */
    private class Worker(val p: Progress, val o: ScrapeOptions, val sources: RunSources) {
        suspend fun process(g: Game) {
            val m = g.meta
            // Checked again here: the user may have hidden it after the run started.
            if (m.hidden || Privacy.isHidden(g)) return
            p.current = g.title
            val force = o.mode == ScrapeMode.Refresh
            val (kinds, types) = o.needs(m) { k -> Media.has(g, k) }
            if (kinds.isEmpty() && types.isEmpty()) { p.upToDate.incrementAndGet(); return }

            val hits = HashMap<ScrapeSource, Hit?>()
            suspend fun hit(s: ScrapeSource): Hit? {
                if (s in hits) return hits[s]
                waitWhileBackgrounded()
                p.source = s.label
                return sources.lookup(s, g).also { hits[s] = it }
            }

            // Details: each field from the first source that has it.
            var details = Details()
            val filled = HashSet<MetaType>()
            for (s in sources.order) {
                val want = types.filter { it !in filled && it in s.details }
                if (want.isEmpty()) continue
                val h = hit(s) ?: continue
                for (t in want) if (h.details.has(t)) { details = details.with(t, h.details); filled += t }
                if (filled.size == types.size) break
            }

            // Pictures and clips: the first source with one that downloads.
            val saved = HashMap<MediaKind, File>()
            var offered = false
            for (k in kinds) {
                for (s in sources.orderFor(k, g)) {
                    val h = hit(s) ?: continue
                    val urls = h.media(k)
                    if (urls.isNotEmpty()) offered = true
                    p.source = s.label
                    val file = urls.take(2).firstNotNullOfOrNull { url -> download(g, k, url) }
                    if (file != null) { saved[k] = file; break }
                }
            }

            if (saved.isNotEmpty() || filled.isNotEmpty()) {
                val paths = saved.mapValues { it.value.path }
                val d = details
                val types2 = filled.toSet()
                val prev = p.pending[g.key]
                p.pending[g.key] = { meta -> apply(prev?.invoke(meta) ?: meta, paths, d, types2, force, o.replaceLocked) }
            }
            if (saved.isNotEmpty()) {
                p.withArt.incrementAndGet()
                val shown = saved[MediaKind.Box] ?: saved.entries.firstOrNull { it.key != MediaKind.Video }?.value
                if (shown != null) p.found(Found(g.key, g.title, shown))
                io.github.zahakj.tandem.ui.Sounds.found()
            }
            if (filled.isNotEmpty()) p.withDetails.incrementAndGet()
            if (saved.isEmpty() && filled.isEmpty()) {
                // A source had pictures but none would download: a failure (worth another try), not just a miss.
                if (offered) p.failed.incrementAndGet()
                p.miss(g.key)
            }
        }

        private suspend fun download(g: Game, k: MediaKind, url: String): File? = try {
            runInterruptible {
                val data = Net.bytes(url, maxBytes = if (k == MediaKind.Video) 24 shl 20 else 16 shl 20)
                p.bytes.addAndGet(data.size.toLong())
                Media.save(g, k, data)
            }
        } catch (e: Exception) { rethrowCancel(e)
            null
        }

        private suspend fun waitWhileBackgrounded() { if (blocked.value) blocked.first { !it } }
    }

    /**
     * Merges scraped results into a game's data. Locked fields are kept, unless [replaceLocked], in which case
     * what gets replaced is unlocked (it's no longer the user's own). Without [force] only empty fields are filled.
     */
    fun apply(
        m: GameMeta, media: Map<MediaKind, String>, d: Details?, types: Set<MetaType>,
        force: Boolean, replaceLocked: Boolean = false,
    ): GameMeta {
        val locked = if (replaceLocked) emptySet() else m.lockedFields
        val replaced = HashSet<String>()
        var out = m
        for ((k, path) in media) if (k.field !in locked) { out = k.set(out, path); replaced += k.field }
        if (d != null) {
            fun <T> pick(field: String, current: T?, new: T?): T? =
                if (field in locked || new == null || (!force && current != null)) current else new.also { replaced += field }
            for (t in types) out = when (t) {
                MetaType.Name -> out.copy(title = pick(MetaField.TITLE, out.title, d.name))
                MetaType.Description -> out.copy(description = pick(MetaField.DESCRIPTION, out.description, d.description))
                MetaType.Genres -> out.copy(genres = pick(MetaField.GENRES, out.genres.ifEmpty { null }, d.genres.ifEmpty { null }).orEmpty())
                MetaType.Credits -> out.copy(
                    developer = pick(MetaField.DEVELOPER, out.developer, d.developer),
                    publisher = pick(MetaField.PUBLISHER, out.publisher, d.publisher),
                )
                MetaType.Year -> out.copy(year = pick(MetaField.YEAR, out.year, d.year))
                MetaType.Players -> out.copy(players = pick(MetaField.PLAYERS, out.players, d.players))
                MetaType.Rating -> out.copy(rating = pick(MetaField.RATING, out.rating, d.rating))
                MetaType.TimeToBeat -> out.copy(timeToBeat = pick(MetaField.TIME, out.timeToBeat, d.time?.takeIf { it.any }))
            }
        }
        if (replaceLocked && replaced.isNotEmpty()) out = out.copy(lockedFields = out.lockedFields - replaced)
        return out
    }

    // -----------------------------------------------------------------------------------------------------------
    // Per-game artwork chooser

    /** Every candidate picture from every configured source, for the art chooser. Never for hidden games. */
    suspend fun gallery(game: Game): Gallery = withContext(Dispatchers.IO) {
        if (Privacy.isHidden(game)) return@withContext Gallery(emptyMap(), listOf("Hidden games are never sent anywhere"))
        val ctx = appContext ?: return@withContext Gallery(emptyMap(), emptyList())
        val cfg = ScrapeConfig.settings
        val notes = mutableListOf<String>()
        val out = HashMap<MediaKind, MutableList<Candidate>>()
        val kinds = listOf(MediaKind.Box, MediaKind.Hero, MediaKind.Logo)
        val on = cfg.active
        if (ScrapeSource.LaunchBox in on) {
            // On the device: no network.
            val lb = runCatching { runInterruptible { LaunchBoxDb.index()?.find(RunSources.launchBoxQueries(listOf(game)))?.get(game.key) } }.getOrNull()
            if (lb != null) kinds.forEach { k ->
                out.getOrPut(k) { mutableListOf() } += lb.urls(k, cfg.regionPref).take(8).map { Candidate(k, "LaunchBox", it, it) }
            }
        }
        if (ScrapeSource.Igdb in on) {
            try {
                val g = runInterruptible { Igdb(cfg.igdbClientId, cfg.igdbSecret).search(game.title, ScrapeSystems.igdb(game)) }
                if (g != null) {
                    g.cover?.let { out.getOrPut(MediaKind.Box) { mutableListOf() } += Candidate(MediaKind.Box, "IGDB", IgdbGame.image(it, "720p"), IgdbGame.image(it, "cover_big")) }
                    (g.artworks + g.screenshots).take(8).forEach {
                        out.getOrPut(MediaKind.Hero) { mutableListOf() } += Candidate(MediaKind.Hero, "IGDB", IgdbGame.image(it, "1080p"), IgdbGame.image(it, "screenshot_med"))
                    }
                }
            } catch (e: SourceDown) {
                notes += e.message.orEmpty()
            } catch (e: Exception) { rethrowCancel(e)
                notes += "Couldn't reach IGDB"
            }
        }
        if (ScrapeSource.ScreenScraper in on) {
            try {
                val info = runInterruptible { ScreenScraper(cfg).lookup(game, if (ScrapeSystems.screenScraper(game) != null) Hashes.of(game.file) else null, cfg.languagePref, cfg.regionPref) }
                if (info != null) kinds.forEach { k -> out.getOrPut(k) { mutableListOf() } += ScreenScraper.candidates(info, k) }
            } catch (e: SourceDown) {
                notes += e.message.orEmpty()
            } catch (e: Exception) { rethrowCancel(e)
                notes += "Couldn't reach ScreenScraper"
            }
        }
        val lr = Libretro(ctx.cacheDir)
        if (ScrapeSource.Libretro in on) runCatching {
            out.getOrPut(MediaKind.Box) { mutableListOf() } += runInterruptible { lr.candidates(game, MediaKind.Box, cfg.regionPref) }
            out.getOrPut(MediaKind.Hero) { mutableListOf() } += runInterruptible {
                lr.candidates(game, MediaKind.Title, cfg.regionPref).take(2) + lr.candidates(game, MediaKind.Gameplay, cfg.regionPref).take(2)
            }.map { it.copy(kind = MediaKind.Hero) }
        }
        if (ScrapeSource.SteamGridDb in on) {
            try {
                val sg = SteamGridDb(cfg.sgdbKey)
                val id = runInterruptible { sg.gameId(game) }
                if (id != null) kinds.forEach { k -> out.getOrPut(k) { mutableListOf() } += runInterruptible { sg.candidates(id, k) } }
            } catch (e: SourceDown) {
                notes += e.message.orEmpty()
            } catch (e: Exception) { rethrowCancel(e)
                notes += "Couldn't reach SteamGridDB"
            }
        }
        Gallery(out.mapValues { (_, v) -> v.distinctBy { it.url } }, notes)
    }

    /** Downloads a chosen candidate, saves it and locks that picture so later scrapes keep it. */
    suspend fun use(game: Game, c: Candidate): Boolean = withContext(Dispatchers.IO) {
        if (Privacy.isHidden(game)) return@withContext false
        val file = try {
            runInterruptible { Media.save(game, c.kind, Net.bytes(c.url)) }
        } catch (e: Exception) { rethrowCancel(e)
            null
        } ?: return@withContext false
        setArt(game, c.kind, file.path)
        true
    }

    /** Saves a picture the user picked on the device as [kind] for [game], locked. */
    suspend fun useLocal(game: Game, kind: MediaKind, data: ByteArray): Boolean = withContext(Dispatchers.IO) {
        val file = Media.saveLocal(game, kind, data) ?: return@withContext false
        setArt(game, kind, file.path)
        true
    }

    private fun setArt(game: Game, kind: MediaKind, path: String) =
        Store.update(game.key) { kind.set(it, path).copy(lockedFields = it.lockedFields + kind.field) }

    /** Goes back to whatever art the game had before (the folder art), unlocking it. */
    fun resetArt(game: Game, kind: MediaKind) =
        Store.update(game.key) { kind.set(it, null).copy(lockedFields = it.lockedFields - kind.field) }

    // -----------------------------------------------------------------------------------------------------------

    /** Size of everything Tandem downloaded. Reads the disk: call off the main thread. */
    fun downloadedSize(): Pair<Int, Long> = Media.downloaded().let { files -> files.size to files.sumOf { it.length() } }

    /**
     * Deletes Tandem's downloads. Pictures the user chose by hand (locked) stay. Game data pointing at deleted
     * files is cleared so the folder art shows again. Call off the main thread.
     */
    fun clearDownloaded(): Pair<Int, Long> {
        val keep = Store.meta.values.flatMap { m -> MediaKind.entries.filter { it.field in m.lockedFields }.mapNotNull { it.get(m) } }.toSet()
        val result = Media.clear(keep)
        val changes = Store.meta.filter { (_, m) -> MediaKind.entries.any { k -> k.get(m)?.let { !File(it).isFile } == true } }
            .mapValues { { m: GameMeta -> MediaKind.entries.fold(m) { acc, k -> if (k.get(acc)?.let { !File(it).isFile } == true) k.set(acc, null) else acc } } }
        Store.updateAll(changes)
        return result
    }
}

/** Lets cancellation through when catching everything else. */
internal fun rethrowCancel(e: Exception) { if (e is CancellationException) throw e }
