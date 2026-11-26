package io.github.zahakj.tandem.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.zahakj.tandem.Libraries
import io.github.zahakj.tandem.Library
import io.github.zahakj.tandem.NetGate
import io.github.zahakj.tandem.R
import io.github.zahakj.tandem.Tx
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.coroutineContext

/** One fetch of system art, as the screens show it. */
class SystemArtRun(val total: Int, val one: String?) {
    var done by mutableIntStateOf(0)
    /** Systems that got at least one picture. */
    var withArt by mutableIntStateOf(0)
    var pictures by mutableIntStateOf(0)
    var current by mutableStateOf("")
    var state by mutableStateOf(RunState.Running)
    var notes by mutableStateOf(emptyList<String>())
    val finished: Boolean get() = state == RunState.Done || state == RunState.Cancelled
    val fraction: Float get() = if (total == 0) 0f else done.toFloat() / total
}

/**
 * Fetches real art for the user's systems (SystemArt.kt), only ever when they start it: one Wikidata query (the systems'
 * Wikidata ids), one Commons query (the file names it gave), RetroArch's icon by its system name, and, when set up,
 * ScreenScraper's system list and IGDB's platform logos. Then the pictures themselves, scaled down, into
 * ROMs/tandem/art/systems/<id>/. Never in private mode.
 */
object SystemArtFetch {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    var run by mutableStateOf<SystemArtRun?>(null)
        private set

    /** Bumped whenever a system's pictures change (fetched, cleared): pickers and icons look again. */
    var revision by mutableIntStateOf(0)
        private set

    val running: Boolean get() = run?.finished == false

    fun dir(platformId: String): File = File(Library.romsDir, "${SystemArt.ROOT}/${SystemArt.folderName(platformId)}")

    private class Listing(val stamp: Long, val files: Map<WebOption, File>)
    private val listings = ConcurrentHashMap<String, Listing>()

    /** The pictures a system has, by option. Cheap: re-read only when its folder changed. */
    fun files(platformId: String): Map<WebOption, File> {
        val d = dir(platformId)
        val stamp = d.lastModified()
        listings[platformId]?.takeIf { it.stamp == stamp }?.let { return it.files }
        val files = d.listFiles().orEmpty().filter { it.isFile }.mapNotNull { f -> WebOption.ofFile(f.name)?.let { it to f } }.toMap()
        listings[platformId] = Listing(stamp, files)
        return files
    }

    fun options(platformId: String): Set<WebOption> = files(platformId).keys

    /** Who made each picture of a system, as the sources said. */
    fun credits(platformId: String): Map<String, WebCredit> =
        runCatching { File(dir(platformId), CREDITS).takeIf { it.isFile }?.readText()?.let(SystemArt::decodeCredits) }.getOrNull().orEmpty()

    /** After Clear downloaded media: the folders are read again. */
    fun forget() {
        listings.clear()
        revision++
    }

    private const val CREDITS = "credits.json"

    /** Ids a system goes by in the sources' tables: its own, its art folders' and its ROM folders'. */
    private fun idsOf(platformId: String): List<String> =
        Libraries.byId(platformId)?.platform?.let { listOf(it.id) + it.artIds + it.folders } ?: listOf(platformId)

    /**
     * Starts fetching art for [platformIds] (every system in the library when null). False when a fetch is running,
     * private mode is on, or there is nothing to fetch for.
     */
    fun start(platformIds: List<String>? = null, onDone: (SystemArtRun) -> Unit = {}): Boolean {
        if (running || NetGate.closed) return false
        val ids = (platformIds ?: Libraries.all.orEmpty().map { it.platform.id }).distinct()
            .filter { it != io.github.zahakj.tandem.AndroidApps.PLATFORM_ID || platformIds != null }
        if (ids.isEmpty()) return false
        val r = SystemArtRun(ids.size, ids.singleOrNull())
        run = r
        job = scope.launch {
            try {
                execute(r, ids)
                withContext(Dispatchers.Main) { r.current = ""; r.state = RunState.Done }
            } catch (e: CancellationException) {
                withContext(NonCancellable + Dispatchers.Main) { r.current = ""; r.state = RunState.Cancelled }
            } catch (e: Exception) {
                android.util.Log.w("Tandem", "System art stopped", e)
                withContext(Dispatchers.Main) { r.notes = r.notes + Tx.s(R.string.scrape_note_stopped_early); r.current = ""; r.state = RunState.Done }
            }
            withContext(NonCancellable + Dispatchers.Main) { forget(); onDone(r) }
        }
        return true
    }

    fun cancel() {
        if (!running) return
        job?.cancel()
    }

    /** Forgets a finished run. */
    fun dismiss() { if (run?.finished == true) run = null }

    private class Want(val option: WebOption, val url: String, val credit: WebCredit)

    private suspend fun note(r: SystemArtRun, text: String) = withContext(Dispatchers.Main) { if (text !in r.notes) r.notes = r.notes + text }

    private suspend fun execute(r: SystemArtRun, ids: List<String>) {
        val cfg = ScrapeConfig.settings
        val keys = ids.associateWith(::idsOf)

        // Wikimedia: the console photo and logo of each system's Wikidata item, then their addresses on Commons.
        withContext(Dispatchers.Main) { r.current = WebSource.Wikimedia.label }
        val qOf = keys.mapNotNull { (id, k) -> ScrapeSystems.wikidata(k)?.let { id to it } }.toMap()
        val commons = HashMap<String, CommonsImage>()
        val files = runCatching {
            SystemArt.wikidataQuery(qOf.values)?.let { q ->
                val text = runInterruptible {
                    Net.text("https://query.wikidata.org/sparql?format=json&query=${Net.enc(q)}", mapOf("Accept" to "application/sparql-results+json"))
                }
                SystemArt.wikidataImages(text)
            }.orEmpty()
        }.onFailure { if (it is CancellationException) throw it; note(r, Tx.s(R.string.scrape_system_art_unreachable, WebSource.Wikimedia.label)) }.getOrDefault(emptyMap())
        val titles = files.values.flatMap { listOfNotNull(it.first, it.second) }.map(SystemArt::commonsTitle).distinct()
        for (batch in titles.chunked(40)) {
            coroutineContext.ensureActive()
            runCatching {
                val url = "https://commons.wikimedia.org/w/api.php?action=query&format=json&formatversion=2&prop=imageinfo" +
                    "&iiprop=url%7Cextmetadata&iiurlwidth=1280&iiextmetadatafilter=LicenseShortName%7CArtist&titles=" +
                    Net.enc(batch.joinToString("|"))
                commons.putAll(SystemArt.commonsImages(runInterruptible { Net.text(url) }))
            }.onFailure { if (it is CancellationException) throw it }
        }

        // ScreenScraper, with the user's own account: one list of every system and its media.
        var ss: Map<Int, List<SsSystemMedia>> = emptyMap()
        if (ScrapeSource.ScreenScraper in cfg.active && keys.values.any { ScrapeSystems.screenScraper(it) != null }) {
            withContext(Dispatchers.Main) { r.current = WebSource.ScreenScraper.label }
            try {
                ss = SystemArt.ssSystems(runInterruptible { ScreenScraper(cfg).systemsJson() })
            } catch (e: SourceDown) {
                note(r, e.message.orEmpty())
            } catch (e: Exception) {
                rethrowCancel(e)
                note(r, Tx.s(R.string.scrape_note_ss_short))
            }
        }

        // IGDB, with the user's own keys: the platforms' logos.
        var igdb: Map<Int, String> = emptyMap()
        val igdbIds = keys.values.mapNotNull { ScrapeSystems.igdb(it) }.toSet()
        if (ScrapeSource.Igdb in cfg.active && igdbIds.isNotEmpty()) {
            withContext(Dispatchers.Main) { r.current = WebSource.Igdb.label }
            try {
                igdb = runInterruptible { Igdb(cfg.igdbClientId, cfg.igdbSecret).platformLogos(igdbIds) }
            } catch (e: SourceDown) {
                note(r, e.message.orEmpty())
            } catch (e: Exception) {
                rethrowCancel(e)
                note(r, Tx.s(R.string.scrape_note_igdb_short))
            }
        }

        val region = cfg.regionPref.code
        for (id in ids) {
            coroutineContext.ensureActive()
            val k = keys.getValue(id)
            val name = Libraries.byId(id)?.platform?.name ?: id
            withContext(Dispatchers.Main) { r.current = name }
            val wants = ArrayList<Want>()
            ScrapeSystems.screenScraper(k)?.let { ssId ->
                for ((kind, url) in SystemArt.ssPick(ss[ssId].orEmpty(), region)) {
                    wants += Want(WebOption(WebSource.ScreenScraper, kind), url, WebCredit(WebSource.ScreenScraper.label, page = "https://www.screenscraper.fr"))
                }
            }
            qOf[id]?.let { q -> files[q] }?.let { (photo, logo) ->
                fun add(file: String?, kind: WebKind) {
                    val img = file?.let { commons[SystemArt.commonsTitle(it)] } ?: return
                    wants += Want(WebOption(WebSource.Wikimedia, kind), img.url, WebCredit(WebSource.Wikimedia.label, img.licence, img.author, img.page))
                }
                add(photo, WebKind.Photo)
                add(logo, WebKind.Logo)
            }
            ScrapeSystems.igdb(k)?.let { igdb[it] }?.let { img ->
                wants += Want(WebOption(WebSource.Igdb, WebKind.Logo), SystemArt.igdbLogoUrl(img), WebCredit(WebSource.Igdb.label, page = "https://www.igdb.com"))
            }
            ScrapeSystems.libretro(k)?.let { lr ->
                wants += Want(WebOption(WebSource.RetroArch, WebKind.Icon), SystemArt.retroArchIcon(lr), WebCredit(WebSource.RetroArch.label, "CC BY 4.0", "libretro", "https://github.com/libretro/retroarch-assets"))
            }
            val dir = dir(id)
            val credits = LinkedHashMap(credits(id))
            var got = 0
            for (w in wants) {
                coroutineContext.ensureActive()
                val saved = try {
                    runInterruptible {
                        val data = Net.bytes(w.url, maxBytes = 16 shl 20)
                        val max = if (w.option.kind in WIDE) 1920 else 1280
                        Media.saveSystemArt(dir, w.option.id, data, max)
                    }
                } catch (e: Exception) {
                    rethrowCancel(e)
                    null
                }
                if (saved != null) { got++; credits[w.option.id] = w.credit }
            }
            if (got > 0) runCatching { File(dir, CREDITS).writeText(SystemArt.encodeCredits(credits)) }
            withContext(Dispatchers.Main) {
                r.done++
                r.pictures += got
                if (got > 0) { r.withArt++; listings.remove(id); revision++ }
            }
        }
    }

    private val WIDE = setOf(WebKind.Fanart, WebKind.Background, WebKind.Banner)
}
