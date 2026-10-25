package io.github.zahakj.tandem.data

import android.content.Context
import io.github.zahakj.tandem.Game
import io.github.zahakj.tandem.Libraries
import io.github.zahakj.tandem.Library
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Fills in the details of games that have no description, from the LaunchBox database already on the device: after
 * every library scan (which includes the first one at startup) and after the database is downloaded. A game added
 * with its art put in place by hand, which no art scrape ever looks at, still gets its description, genres, year,
 * credits and players this way.
 *
 * Only the local index is read: no network, ever, so hidden games are filled too, and nothing about any game is
 * logged. Fields the user edited or locked are never touched (the same [Scraper.apply] rules as a scrape, without
 * replacing anything), the name is left as it is, and only the detail kinds picked in the scrape settings are filled.
 * Each game is tried once per database download (and again if its title changes), so this costs nothing once done.
 */
object DetailsFill {
    /** What this fills: the scrape's details minus the name (a background job shouldn't rename games). */
    private val TYPES = setOf(MetaType.Description, MetaType.Genres, MetaType.Credits, MetaType.Year, MetaType.Players, MetaType.Rating)

    private val worker = Executors.newSingleThreadExecutor { r ->
        Thread({ android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND); r.run() }, "tandem-details").apply { isDaemon = true }
    }
    private val queued = AtomicBoolean(false)
    private var file: File? = null

    /** game key -> what it was last tried with ([stamp]); only ever touched on [worker]. */
    private var tried: HashMap<String, String>? = null

    fun init(ctx: Context) { if (file == null) file = File(ctx.filesDir, "details-tried.tsv") }

    /** Asks for a pass over the library in the background. Requests while one is waiting share it. */
    fun request() {
        if (file == null || !queued.compareAndSet(false, true)) return
        worker.execute {
            queued.set(false)
            runCatching { pass() }
        }
    }

    private fun pass() {
        val cfg = ScrapeConfig.settings
        if (!cfg.isOn(ScrapeSource.LaunchBox)) return
        val built = LaunchBoxDb.info?.built ?: return
        val index = LaunchBoxDb.index() ?: return
        val types = TYPES.filter { it in cfg.details }.toSet()
        if (MetaType.Description !in types) return
        val seen = tried ?: load().also { tried = it }

        val wanted = Libraries.games.filter { g -> needs(g.meta) && seen[g.key] != stamp(built, g) }
        if (wanted.isEmpty()) return
        val queries = wanted.mapNotNull { g ->
            val platform = ScrapeSystems.launchBox(g) ?: return@mapNotNull null
            if (!index.has(platform)) return@mapNotNull null
            LbQuery(g.key, platform, listOf(g.title, g.fileTitle, Library.cleanTitle(g.stem)).distinct())
        }
        if (queries.isEmpty()) return
        val found = index.find(queries)

        val changes = HashMap<String, (GameMeta) -> GameMeta>()
        for ((key, lb) in found) {
            val d = lb.toDetails()
            changes[key] = { m -> if (needs(m)) Scraper.apply(m, emptyMap(), d, types, force = false) else m }
        }
        Store.updateAll(changes)
        val byKey = wanted.associateBy { it.key }
        for (q in queries) byKey[q.key]?.let { seen[q.key] = stamp(built, it) }
        save(seen)
    }

    /** No description yet, and the user hasn't kept it empty on purpose. */
    private fun needs(m: GameMeta) = MetaType.Description.missing(m) && MetaField.DESCRIPTION !in m.lockedFields

    private fun stamp(built: Long, g: Game) = "$built:${g.title.hashCode()}"

    private fun load(): HashMap<String, String> {
        val out = HashMap<String, String>()
        runCatching {
            file?.takeIf { it.isFile }?.forEachLine { line ->
                val tab = line.lastIndexOf('\t')
                if (tab > 0) out[line.substring(0, tab)] = line.substring(tab + 1)
            }
        }
        return out
    }

    private fun save(seen: Map<String, String>) {
        val f = file ?: return
        // Games no longer in the library are dropped, so the file doesn't grow forever.
        val present = Libraries.games.mapTo(HashSet()) { it.key }
        runCatching {
            val tmp = File(f.path + ".tmp")
            tmp.bufferedWriter().use { w -> for ((k, v) in seen) if (k in present && '\n' !in k) { w.write(k); w.write("\t"); w.write(v); w.newLine() } }
            tmp.renameTo(f)
        }
    }
}
