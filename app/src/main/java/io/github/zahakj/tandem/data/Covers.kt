package io.github.zahakj.tandem.data

import android.content.Context
import io.github.zahakj.tandem.Game
import io.github.zahakj.tandem.Library
import io.github.zahakj.tandem.Privacy
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.URI
import java.net.URLDecoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * Which picture addresses may leave the device as a game's cover (the Discord status, which has Discord's servers
 * fetch it). Only plain public https addresses: never one with a login in it, from a host whose media addresses
 * carry the user's credentials (ScreenScraper's `ssid`, `sspassword`, `devid`…), or with a key-like query
 * parameter. Any other query and the fragment are dropped; the CDNs used here (thumbnails.libretro.com,
 * cdn*.steamgriddb.com, images.launchbox-app.com, images.igdb.com) don't need them.
 */
object CoverUrl {
    /** Discord takes a large image of up to a few hundred characters; anything longer is left out. */
    const val MAX = 256
    private val keyedHosts = listOf("screenscraper.fr")
    private val secret = Regex("key|token|secret|pass|pwd|auth|sig|ssid|devid|user|login|session|credential|client", RegexOption.IGNORE_CASE)

    /** [url] fit to send, without its query, or null when it isn't. */
    fun safe(url: String?): String? {
        val s = url?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val u = runCatching { URI(s) }.getOrNull() ?: return null
        if (!u.scheme.equals("https", ignoreCase = true) || u.rawUserInfo != null) return null
        val host = u.host?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null
        if (keyedHosts.any { host == it || host.endsWith(".$it") }) return null
        val names = u.rawQuery.orEmpty().split('&', ';').map { it.substringBefore('=') }.filter { it.isNotEmpty() }
        if (names.any { n -> secret.containsMatchIn(runCatching { URLDecoder.decode(n, "UTF-8") }.getOrDefault(n)) }) return null
        val plain = s.substringBefore('#').substringBefore('?')
        if (plain.length > MAX || u.rawPath.isNullOrEmpty() || u.rawPath == "/") return null
        return plain
    }
}

/**
 * Cover addresses for the Discord status. A scrape remembers where the box art came from ([GameMeta.coverUrl]); for
 * games scraped before that, or whose box came from ScreenScraper or the device, one is looked up the first time the
 * status shows the game: LaunchBox's box art (from the index on the device), a listed libretro thumbnail (the system's folder listing, cached for a week, matched on the
 * device), then SteamGridDB when the user entered a key. Only sources the user left on are asked, never for a hidden
 * game or in private mode. Answers, misses included, are kept in `discord-covers-2.json` in the cache folder; a miss
 * is tried again after [RETRY_MS], a failed lookup (no network) at the next play.
 */
object Covers {
    private const val FILE = "discord-covers-2.json"
    private const val RETRY_MS = 7 * 86_400_000L

    private class Entry(val url: String?, val at: Long)

    private val worker = Executors.newSingleThreadExecutor { r ->
        Thread({ android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND); r.run() }, "tandem-covers")
    }
    private val found = ConcurrentHashMap<String, Entry>()
    private val asking: MutableSet<String> = ConcurrentHashMap.newKeySet()
    // Owned by the worker thread.
    private var file: File? = null

    /** The cover address known for [game] now, without any lookup: from its scrape, else from an earlier lookup. */
    fun known(game: Game): String? = CoverUrl.safe(game.meta.coverUrl) ?: found[game.key]?.url

    /**
     * Looks a cover up for [game] off the main thread, unless one is being looked up or a recent miss is on file.
     * [done] runs on the worker thread with the address found.
     */
    fun resolve(ctx: Context, game: Game, done: (String) -> Unit) {
        if (!asking.add(game.key)) return
        val app = ctx.applicationContext
        worker.execute {
            try {
                load(app)
                val had = found[game.key]
                if (had?.url != null) { done(had.url); return@execute }
                if (had != null && System.currentTimeMillis() - had.at < RETRY_MS) return@execute
                // Checked here too: the game may have been hidden, or private mode turned on, since.
                if (Privacy.enabled || Privacy.isHidden(game)) return@execute
                val url = try { lookup(app, game) } catch (e: IOException) { return@execute }
                found[game.key] = Entry(url, System.currentTimeMillis())
                save()
                url?.let(done)
            } catch (e: Exception) {
                android.util.Log.w("Tandem", "Cover lookup failed", e)
            } finally {
                asking.remove(game.key)
            }
        }
    }

    /** A credential-free cover address, null when no source has one. Throws when a source couldn't be reached. */
    private fun lookup(ctx: Context, game: Game): String? {
        val cfg = ScrapeConfig.settings
        var trouble: IOException? = null
        // LaunchBox first: its index is on the device, so this needs no network, and it has covers for most systems.
        if (ScrapeSource.LaunchBox in cfg.active) {
            val index = LaunchBoxDb.index()
            val platform = LaunchBoxDb.platformOf(game)
            if (index != null && platform != null) {
                val titles = listOf(game.title, game.fileTitle, Library.cleanTitle(game.stem)).distinct()
                index.find(listOf(LbQuery(game.key, platform, titles)))[game.key]
                    ?.let { LbIndex.pick(it, listOf(LbArt.BoxFront, LbArt.BoxRebuilt), cfg.regionPref.code) }
                    ?.firstNotNullOfOrNull { CoverUrl.safe(it.url) }?.let { return it }
            }
        }
        if (ScrapeSource.Libretro in cfg.active) {
            try {
                Libretro(ctx.cacheDir).listedUrl(game, MediaKind.Box, cfg.regionPref)?.let(CoverUrl::safe)?.let { return it }
            } catch (e: IOException) { trouble = e }
        }
        if (ScrapeSource.SteamGridDb in cfg.active) {
            try {
                val sg = SteamGridDb(cfg.sgdbKey)
                sg.gameId(game)?.let { id -> sg.candidates(id, MediaKind.Box).firstNotNullOfOrNull { CoverUrl.safe(it.url) } }?.let { return it }
            } catch (e: SourceDown) {
                // A key SteamGridDB refuses: a miss, asked again with the other misses.
            } catch (e: IOException) { trouble = e }
        }
        trouble?.let { throw it }
        return null
    }

    private fun load(ctx: Context) {
        if (file != null) return
        val f = File(ctx.cacheDir, FILE)
        // The first version cached misses from before LaunchBox was asked.
        File(ctx.cacheDir, "discord-covers.json").delete()
        file = f
        val o = runCatching { JSONObject(f.readText()) }.getOrNull() ?: return
        for (k in o.keys()) {
            val e = o.optJSONObject(k) ?: continue
            found[k] = Entry(CoverUrl.safe(e.optString("u")), e.optLong("t"))
        }
    }

    private fun save() {
        val f = file ?: return
        val o = JSONObject()
        for ((k, e) in found) o.put(k, JSONObject().put("t", e.at).apply { e.url?.let { put("u", it) } })
        runCatching { BackupFiles.atomicWrite(f, o.toString()) }
    }
}
