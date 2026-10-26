package io.github.zahakj.tandem.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import io.github.zahakj.tandem.Game
import io.github.zahakj.tandem.Library
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import java.text.Normalizer
import java.util.zip.CRC32

// ---------------------------------------------------------------------------------------------------------------
// Network
// ---------------------------------------------------------------------------------------------------------------

class HttpError(val code: Int, val body: String) : IOException("HTTP $code")

/** Plain HTTPS GETs and POSTs. Every caller runs on Dispatchers.IO; nothing here touches the main thread. */
object Net {
    const val AGENT = "Tandem/0.2 (Android game frontend; open source)"

    /** POSTs [body] (JSON unless [contentType] says otherwise) and returns the answer as text. */
    fun post(url: String, body: String, headers: Map<String, String> = emptyMap(), contentType: String = "application/json"): String =
        String(bytes(url, headers + ("Content-Type" to contentType), 8 shl 20, body.toByteArray()))

    fun bytes(url: String, headers: Map<String, String> = emptyMap(), maxBytes: Int = 16 shl 20, post: ByteArray? = null): ByteArray {
        // Private mode: nothing leaves the device (Offline.kt).
        io.github.zahakj.tandem.NetGate.check()
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 12_000
            c.readTimeout = 25_000
            c.instanceFollowRedirects = true
            c.setRequestProperty("User-Agent", AGENT)
            headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
            if (post != null) {
                c.requestMethod = "POST"
                c.doOutput = true
                c.setFixedLengthStreamingMode(post.size)
                c.outputStream.use { it.write(post) }
            }
            val code = c.responseCode
            if (code !in 200..299) {
                val body = runCatching { c.errorStream?.bufferedReader()?.use { it.readText().take(300) } }.getOrNull().orEmpty()
                throw HttpError(code, body)
            }
            val out = ByteArrayOutputStream()
            c.inputStream.use { input ->
                val buf = ByteArray(32 * 1024)
                while (true) {
                    if (Thread.currentThread().isInterrupted) throw IOException("cancelled")
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    if (out.size() > maxBytes) throw IOException("too large")
                }
            }
            return out.toByteArray()
        } finally {
            c.disconnect()
        }
    }

    fun text(url: String, headers: Map<String, String> = emptyMap()): String = String(bytes(url, headers, 8 shl 20))

    fun enc(s: String): String = URLEncoder.encode(s, "UTF-8").replace("+", "%20")
}

// ---------------------------------------------------------------------------------------------------------------
// Saving media
// ---------------------------------------------------------------------------------------------------------------

/**
 * Writes pictures and clips to ROMs/downloaded_media/<system>/<kind dir>/<file stem>.<ext>, scaled down first so
 * the library stays light, and remembers every file it wrote so "Clear downloaded media" only ever removes
 * Tandem's own downloads (never art from Cocoon, ES-DE or the user).
 */
object Media {
    private var manifest: File? = null
    private val lock = Any()
    private val decodeLock = Any()

    fun init(ctx: Context) { manifest = File(ctx.filesDir, "downloaded-media.txt") }

    fun dirFor(game: Game, kind: MediaKind): File =
        File(Library.romsDir, "downloaded_media/${game.platform.artIds.firstOrNull() ?: game.platform.id}/${kind.dir}")

    /** True when [game] already has this kind of media, from any frontend. */
    fun has(game: Game, kind: MediaKind): Boolean {
        kind.get(game.meta)?.let { if (File(it).isFile) return true }
        val names = when (kind) {
            MediaKind.Box -> listOf("icon", "covers", "box2dfront", "boxart")
            MediaKind.Hero -> listOf("hero", "fanart")
            MediaKind.Logo -> listOf("logo", "marquees", "wheel")
            MediaKind.Title -> listOf("screenshot_title", "titlescreens")
            MediaKind.Gameplay -> listOf("screenshot_gameplay", "screenshots")
            MediaKind.Video -> listOf("videos")
        }
        val stem = game.stem
        for (id in game.platform.artIds) for (d in names) {
            val dir = File(Library.romsDir, "downloaded_media/$id/$d")
            if (dirListing(dir).any { it == stem.lowercase() }) return true
        }
        return false
    }

    // Listing a folder for every game would be slow; cache each folder's file keys for a short while.
    private val listings = HashMap<String, Pair<Long, Set<String>>>()

    private fun dirListing(dir: File): Set<String> = synchronized(listings) {
        val now = System.currentTimeMillis()
        listings[dir.path]?.takeIf { now - it.first < 60_000 }?.second ?: run {
            val keys = dir.list().orEmpty().map { n ->
                val cut = n.indexOf("__")
                (if (cut > 0) n.substring(0, cut) else n.substringBeforeLast('.')).lowercase()
            }.toSet()
            listings[dir.path] = now to keys
            keys
        }
    }

    /**
     * Saves downloaded [data] for [game]. Pictures are decoded, scaled to the kind's size and re-encoded
     * (JPEG, or PNG when they have transparency); videos are saved as they are. Returns the file, or null when
     * the data isn't a usable picture or clip.
     */
    fun save(game: Game, kind: MediaKind, data: ByteArray): File? {
        val (bytes, ext) = if (kind == MediaKind.Video) {
            when {
                isMp4(data) -> data to "mp4"
                isWebm(data) -> data to "webm"
                else -> return null
            }
        } else {
            // One picture at a time: a big download decodes to tens of megabytes, and several at once is a burst of
            // memory (and garbage collection) the screens feel.
            synchronized(decodeLock) { encodeScaled(data, kind.maxPx) } ?: return null
        }
        val dir = dirFor(game, kind).apply { mkdirs() }
        val target = File(dir, "${game.stem}.$ext")
        val tmp = File(dir, ".${game.stem}.$ext.part")
        tmp.writeBytes(bytes)
        // Drop our older copy in another format, so the art index never picks a stale one.
        dir.listFiles { f -> f.nameWithoutExtension == game.stem && f.name != target.name }?.forEach { it.delete() }
        if (!tmp.renameTo(target)) { tmp.delete(); return null }
        record(target)
        synchronized(listings) { listings.remove(dir.path) }
        return target
    }

    /** Copies a picture the user picked on the device. */
    fun saveLocal(game: Game, kind: MediaKind, data: ByteArray): File? = save(game, kind, data)

    private fun isMp4(d: ByteArray) = d.size > 12 && String(d, 4, 4, Charsets.US_ASCII) == "ftyp"

    /** Matroska / WebM files start with the EBML magic number (Steam trailers from LaunchBox are WebM). */
    private fun isWebm(d: ByteArray) = d.size > 4 && d[0] == 0x1A.toByte() && d[1] == 0x45.toByte() && d[2] == 0xDF.toByte() && d[3] == 0xA3.toByte()

    private fun encodeScaled(data: ByteArray, maxPx: Int): Pair<ByteArray, String>? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxPx) sample *= 2
        val decoded = BitmapFactory.decodeByteArray(data, 0, data.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val longest = maxOf(decoded.width, decoded.height)
        val bmp = if (longest > maxPx) {
            val f = maxPx.toFloat() / longest
            Bitmap.createScaledBitmap(decoded, (decoded.width * f).toInt().coerceAtLeast(1), (decoded.height * f).toInt().coerceAtLeast(1), true)
                .also { if (it !== decoded) decoded.recycle() }
        } else decoded
        val alpha = bmp.hasAlpha() && hasTransparentPixels(bmp)
        val out = ByteArrayOutputStream()
        bmp.compress(if (alpha) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, 88, out)
        bmp.recycle()
        return out.toByteArray() to (if (alpha) "png" else "jpg")
    }

    /** Many PNGs carry an alpha channel they never use; only keep PNG when something is actually see-through. */
    private fun hasTransparentPixels(b: Bitmap): Boolean {
        val stepX = maxOf(1, b.width / 24)
        val stepY = maxOf(1, b.height / 24)
        var y = 0
        while (y < b.height) {
            var x = 0
            while (x < b.width) {
                if ((b.getPixel(x, y) ushr 24) < 250) return true
                x += stepX
            }
            y += stepY
        }
        return (b.getPixel(0, 0) ushr 24) < 250 || (b.getPixel(b.width - 1, b.height - 1) ushr 24) < 250
    }

    private fun record(f: File) = synchronized(lock) { manifest?.appendText(f.path + "\n") }

    /** Files Tandem downloaded that still exist. */
    fun downloaded(): List<File> = synchronized(lock) {
        manifest?.takeIf { it.isFile }?.readLines().orEmpty().distinct().map(::File).filter { it.isFile }
    }

    /** Deletes Tandem's downloads, except files in [keep]. Returns (files deleted, bytes freed). */
    fun clear(keep: Set<String>): Pair<Int, Long> = synchronized(lock) {
        var n = 0
        var bytes = 0L
        val kept = mutableListOf<String>()
        for (f in downloaded()) {
            if (f.path in keep) { kept += f.path; continue }
            val size = f.length()
            if (f.delete()) { n++; bytes += size }
        }
        manifest?.writeText(kept.joinToString("") { "$it\n" })
        synchronized(listings) { listings.clear() }
        n to bytes
    }
}

// ---------------------------------------------------------------------------------------------------------------
// Matching and hashing
// ---------------------------------------------------------------------------------------------------------------

object Match {
    /** "The Legend of Zelda: A Link to the Past (USA)" and "Legend of Zelda, The - A Link to the Past" -> same key. */
    fun key(s: String): String = tokens(s).joinToString(" ")

    fun tokens(s: String): List<String> {
        val plain = Normalizer.normalize(Library.cleanTitle(s), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
        return plain.lowercase().replace("&", " and ").replace(Regex("[^a-z0-9]+"), " ").trim()
            .split(' ').filter { it.isNotEmpty() && it != "the" }
    }

    /** Dice similarity of the two titles' words, 0..1. */
    fun similarity(a: String, b: String): Float {
        val ta = tokens(a).toSet()
        val tb = tokens(b).toSet()
        if (ta.isEmpty() || tb.isEmpty()) return 0f
        return 2f * ta.intersect(tb).size / (ta.size + tb.size)
    }

    /** Region tags in a No-Intro name, e.g. "(USA, Europe)" -> [USA, Europe]. */
    fun tags(name: String): List<String> =
        Regex("""\(([^)]*)\)""").findAll(name).flatMap { it.groupValues[1].split(',').map(String::trim) }.toList()

    private val junk = setOf("beta", "proto", "demo", "sample", "kiosk", "pirate", "unl", "program", "debug")

    /** How well a release name fits the user's region and the ROM's own region; higher is better. */
    fun regionScore(name: String, pref: Region, romName: String): Int {
        val t = tags(name).map { it.lowercase() }
        val rom = tags(romName).map { it.lowercase() }
        var s = 0
        if (pref.tag.lowercase() in t) s += 4
        if (t.any { it in rom }) s += 3
        if ("world" in t) s += 2
        if (t.any { tag -> junk.any { tag.startsWith(it) } }) s -= 6
        return s - t.size / 3
    }
}

object Hashes {
    /** Games bigger than this aren't hashed: reading gigabytes would take ages and drain the battery. */
    const val MAX_BYTES = 64L shl 20

    data class Result(val crc: String, val md5: String)

    fun of(file: File): Result? {
        if (!file.isFile || file.length() > MAX_BYTES) return null
        return runCatching {
            val crc = CRC32()
            val md5 = MessageDigest.getInstance("MD5")
            file.inputStream().use { input ->
                val buf = ByteArray(256 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    crc.update(buf, 0, n)
                    md5.update(buf, 0, n)
                }
            }
            Result("%08X".format(crc.value), md5.digest().joinToString("") { "%02x".format(it) })
        }.getOrNull()
    }
}

// ---------------------------------------------------------------------------------------------------------------
// Systems (the table is in ScrapePlatforms.kt)
// ---------------------------------------------------------------------------------------------------------------

/** The names a game's system goes by: its id, its art folder names and its ROM folder names. */
val Game.systemIds: List<String> get() = listOf(platform.id) + platform.artIds + platform.folders

fun ScrapeSystems.libretro(g: Game): String? = libretro(g.systemIds)
fun ScrapeSystems.screenScraper(g: Game): Int? = screenScraper(g.systemIds)
fun ScrapeSystems.launchBox(g: Game): String? = launchBox(g.systemIds)
fun ScrapeSystems.igdb(g: Game): Int? = igdb(g.systemIds)

// ---------------------------------------------------------------------------------------------------------------
// Sources
// ---------------------------------------------------------------------------------------------------------------

/** A picture or clip some source offers for a game. */
data class Candidate(val kind: MediaKind, val source: String, val url: String, val thumb: String, val note: String = "")

/** libretro-thumbnails: free, no account. Pictures only (box, title screen, gameplay). */
class Libretro(private val cacheDir: File) {
    private val folder = mapOf(MediaKind.Box to "Named_Boxarts", MediaKind.Title to "Named_Titles", MediaKind.Gameplay to "Named_Snaps")
    /** One picture name, with its match key and words worked out once. */
    private class Entry(val name: String) {
        val tokens = Match.tokens(name).toSet()
        val key = Match.key(name)
    }

    private class Index(entries: List<Entry>) {
        val names: Set<String> = entries.mapTo(HashSet()) { it.name }
        val byKey: Map<String, List<Entry>> = entries.groupBy { it.key }
        val byWord: Map<String, List<Entry>> = HashMap<String, MutableList<Entry>>().also { m ->
            for (e in entries) for (t in e.tokens) m.getOrPut(t) { mutableListOf() } += e
        }
    }

    private val indexes = HashMap<String, Index?>()

    fun supports(kind: MediaKind) = kind in folder

    /** libretro's file-name rule: these characters become underscores. */
    private fun sanitize(name: String) = name.replace(Regex("""[&*/:`<>?\\|"]"""), "_")

    private fun url(system: String, kind: MediaKind, name: String) =
        "https://thumbnails.libretro.com/${Net.enc(system)}/${folder[kind]}/${Net.enc(name)}.png"

    /** Every picture of this kind for a system, from the server's file listing (cached on disk for a week). */
    private fun index(system: String, kind: MediaKind): Index? = synchronized(indexes) {
        // Held for the whole fetch, so workers starting together download each listing only once.
        val key = "$system/${folder[kind]}"
        if (key in indexes) return indexes[key]
        val cache = File(cacheDir, "libretro/${key.replace('/', '_')}.txt")
        val names = if (cache.isFile && System.currentTimeMillis() - cache.lastModified() < 7 * 86_400_000L) {
            cache.readLines().filter { it.isNotBlank() }
        } else runCatching {
            val html = Net.text("https://thumbnails.libretro.com/${Net.enc(system)}/${folder[kind]}/")
            Regex("""href="([^"?/]+)\.png"""").findAll(html)
                .map { URLDecoder.decode(it.groupValues[1].replace("+", "%2B"), "UTF-8") }.toList()
                .also { if (it.isNotEmpty()) { cache.parentFile?.mkdirs(); cache.writeText(it.joinToString("\n")) } }
        }.getOrNull()
        val index = names?.let { n -> Index(n.map(::Entry)) }
        indexes[key] = index
        index
    }

    /** Best-matching picture names for [game], best first: exact file name, same title, then close titles. */
    fun matches(game: Game, kind: MediaKind, pref: Region, limit: Int = 1): List<String> {
        val system = ScrapeSystems.libretro(game) ?: return emptyList()
        val stem = sanitize(game.stem)
        // Without the listing, guess the exact file name for a scrape, but offer nothing unverified in the chooser.
        val index = index(system, kind) ?: return if (limit == 1) listOf(stem) else emptyList()
        if (limit == 1 && stem in index.names) return listOf(stem)
        val titles = listOf(game.title, game.fileTitle).distinct()
        val exact = titles.flatMap { index.byKey[Match.key(it)].orEmpty() }.distinct()
        val pool = exact.ifEmpty {
            val scored = HashMap<Entry, Float>()
            for (t in titles) {
                val words = Match.tokens(t).toSet()
                if (words.isEmpty()) continue
                for (e in words.flatMap { index.byWord[it].orEmpty() }.distinct()) {
                    val s = 2f * e.tokens.intersect(words).size / (e.tokens.size + words.size)
                    if (s >= 0.8f && s > (scored[e] ?: 0f)) scored[e] = s
                }
            }
            scored.entries.sortedByDescending { it.value }.take(12).map { it.key }
        }
        val ranked = pool.map { it.name }.sortedByDescending { Match.regionScore(it, pref, game.file.name) }
        return (if (stem in index.names) listOf(stem) + ranked.filter { it != stem } else ranked).take(limit)
    }

    /** True when the server lists a picture called [name] (the setup screen's Test button). */
    fun listed(system: String, kind: MediaKind, name: String): Boolean = index(system, kind)?.names?.contains(name) == true

    /**
     * The address of the best listed picture for [game], never a guess (the Discord status's cover, see Covers.kt).
     * Null when nothing matches; throws when the server's listing can't be read, so a failed lookup isn't a miss.
     */
    fun listedUrl(game: Game, kind: MediaKind, pref: Region): String? {
        val system = ScrapeSystems.libretro(game) ?: return null
        if (index(system, kind) == null) throw IOException("libretro listing unavailable")
        return matches(game, kind, pref).firstOrNull()?.let { url(system, kind, it) }
    }

    fun find(game: Game, kind: MediaKind, pref: Region): String? {
        val system = ScrapeSystems.libretro(game) ?: return null
        return matches(game, kind, pref).firstOrNull()?.let { url(system, kind, it) }
    }

    fun candidates(game: Game, kind: MediaKind, pref: Region): List<Candidate> {
        val system = ScrapeSystems.libretro(game) ?: return emptyList()
        return matches(game, kind, pref, limit = 4).map { n ->
            val u = url(system, kind, n)
            Candidate(kind, "libretro", u, u, Match.tags(n).joinToString(", "))
        }
    }
}

/** What ScreenScraper knows about a game. */
data class SsGame(
    val name: String?,
    val description: String?,
    val genres: List<String>,
    val developer: String?,
    val publisher: String?,
    val year: Int?,
    val players: String?,
    val rating: Float?,
    /** (type, region, url, format, size) */
    val media: List<SsMedia>,
)

data class SsMedia(val type: String, val region: String, val url: String, val format: String, val size: Long)

/** Thrown when ScreenScraper can't be used for the rest of this run (quota, bad login, closed API). */
class SourceDown(message: String) : Exception(message)

/** ScreenScraper API v2, with the user's own account and developer credentials. */
class ScreenScraper(private val s: ScrapeSettings) {
    private val base = "https://api.screenscraper.fr/api2"

    private fun auth(): String = buildString {
        append("devid=").append(Net.enc(s.ssDevId)).append("&devpassword=").append(Net.enc(s.ssDevPassword))
        append("&softname=Tandem&output=json")
        if (s.ssUser.isNotBlank()) append("&ssid=").append(Net.enc(s.ssUser)).append("&sspassword=").append(Net.enc(s.ssPassword))
    }

    /** Checks the sign-in and returns how many requests at once the account may make. */
    fun signIn(): Int {
        if (s.ssUser.isBlank()) return 1
        val o = call("$base/ssuserInfos.php?${auth()}")
        val user = o.optJSONObject("response")?.optJSONObject("ssuser") ?: throw SourceDown("ScreenScraper didn't accept your login")
        return user.optString("maxthreads").toIntOrNull()?.coerceIn(1, 4) ?: 1
    }

    /** Looks a game up by hash and file name, then by title. Null when ScreenScraper doesn't know it. */
    fun lookup(game: Game, hash: Hashes.Result?, langPref: String, region: Region): SsGame? {
        val system = ScrapeSystems.screenScraper(game) ?: return null
        val q = buildString {
            append("$base/jeuInfos.php?").append(auth()).append("&romtype=rom")
            append("&systemeid=$system")
            append("&romnom=").append(Net.enc(game.file.name))
            append("&romtaille=").append(game.file.length())
            if (hash != null) append("&crc=${hash.crc}&md5=${hash.md5}")
        }
        val jeu = try {
            call(q).optJSONObject("response")?.optJSONObject("jeu")
        } catch (e: HttpError) {
            if (e.code == 404) null else throw e
        }
        val found = jeu ?: search(game.title, system) ?: return null
        return parse(found, langPref, region)
    }

    /** A title search on one system (used by the setup screen's Test button). */
    fun findByTitle(title: String, system: Int, langPref: String, region: Region): SsGame? =
        search(title, system)?.let { parse(it, langPref, region) }

    private fun search(title: String, system: Int): JSONObject? {
        val q = "$base/jeuRecherche.php?${auth()}&recherche=${Net.enc(title)}" + "&systemeid=$system"
        val list = try {
            call(q).optJSONObject("response")?.optJSONArray("jeux")
        } catch (e: HttpError) {
            if (e.code == 404) null else throw e
        } ?: return null
        for (i in 0 until list.length()) {
            val j = list.optJSONObject(i) ?: continue
            val names = j.optJSONArray("noms").objects().mapNotNull { it.optString("text").takeIf(String::isNotBlank) }
            if (names.any { Match.similarity(it, title) >= 0.75f }) return j
        }
        return null
    }

    private fun call(url: String): JSONObject {
        var wait = 1500L
        repeat(4) { attempt ->
            try {
                return JSONObject(Net.text(url))
            } catch (e: HttpError) {
                when (e.code) {
                    429 -> if (attempt < 3) { Thread.sleep(wait); wait *= 2 } else throw e
                    430 -> throw SourceDown("ScreenScraper's daily limit for your account is used up")
                    431 -> throw SourceDown("ScreenScraper paused lookups: too many unknown games today")
                    401, 423 -> throw SourceDown("ScreenScraper is closed or overloaded right now")
                    403 -> throw SourceDown("ScreenScraper didn't accept the developer ID or login")
                    426 -> throw SourceDown("ScreenScraper blocked this app version")
                    else -> throw e
                }
            } catch (e: org.json.JSONException) {
                throw IOException("ScreenScraper sent something unexpected")
            }
        }
        throw IOException("ScreenScraper is busy")
    }

    private fun parse(j: JSONObject, lang: String, region: Region): SsGame {
        val regions = listOf(region.code, "wor", "us", "eu", "ss", "jp")
        fun byRegion(arr: JSONArray?): String? {
            val items = arr.objects()
            return regions.firstNotNullOfOrNull { r -> items.firstOrNull { it.optString("region") == r }?.optString("text") }
                ?: items.firstOrNull()?.optString("text")
        }
        fun byLang(arr: JSONArray?): String? {
            val items = arr.objects()
            return listOf(lang, "en").firstNotNullOfOrNull { l -> items.firstOrNull { it.optString("langue") == l }?.optString("text") }
                ?: items.firstOrNull()?.optString("text")
        }
        val genres = j.optJSONArray("genres").objects().let { all ->
            all.filter { it.optString("principale") == "1" }.ifEmpty { all }
        }.mapNotNull { byLang(it.optJSONArray("noms"))?.trim()?.takeIf(String::isNotEmpty) }.distinct().take(3)
        val media = j.optJSONArray("medias").objects().mapNotNull { m ->
            val url = m.optString("url").takeIf { it.startsWith("http") } ?: return@mapNotNull null
            SsMedia(m.optString("type"), m.optString("region"), url, m.optString("format"), m.optString("size").toLongOrNull() ?: 0L)
        }
        return SsGame(
            name = byRegion(j.optJSONArray("noms"))?.trim()?.takeIf(String::isNotEmpty),
            description = byLang(j.optJSONArray("synopsis"))?.trim()?.takeIf(String::isNotEmpty),
            genres = genres,
            developer = j.optJSONObject("developpeur")?.optString("text")?.trim()?.takeIf(String::isNotEmpty),
            publisher = j.optJSONObject("editeur")?.optString("text")?.trim()?.takeIf(String::isNotEmpty),
            year = byRegion(j.optJSONArray("dates"))?.take(4)?.toIntOrNull(),
            players = j.optJSONObject("joueurs")?.optString("text")?.trim()?.takeIf { it.isNotEmpty() && it != "0" },
            rating = j.optJSONObject("note")?.optString("text")?.toFloatOrNull()?.let { (it / 20f).coerceIn(0f, 1f) },
            media = media,
        )
    }

    companion object {
        /** ScreenScraper media types for each kind, best first. */
        fun types(kind: MediaKind) = when (kind) {
            MediaKind.Box -> listOf("box-2D")
            MediaKind.Hero -> listOf("fanart")
            MediaKind.Logo -> listOf("wheel-hd", "wheel")
            MediaKind.Title -> listOf("sstitle")
            MediaKind.Gameplay -> listOf("ss")
            MediaKind.Video -> listOf("video-normalized", "video")
        }

        fun pick(g: SsGame, kind: MediaKind, region: Region): SsMedia? {
            val order = listOf(region.code, "wor", "us", "eu", "ss", "jp", "")
            for (t in types(kind)) {
                val of = g.media.filter { it.type == t }
                if (of.isEmpty()) continue
                return order.firstNotNullOfOrNull { r -> of.firstOrNull { it.region == r } } ?: of.first()
            }
            return null
        }

        fun candidates(g: SsGame, kind: MediaKind): List<Candidate> {
            val types = when (kind) {
                MediaKind.Hero -> listOf("fanart", "sstitle", "ss")
                else -> types(kind)
            }
            return g.media.filter { it.type in types }.distinctBy { it.url }.take(12).map {
                Candidate(kind, "ScreenScraper", it.url, it.url + "&maxwidth=320", listOf(it.type, it.region.uppercase()).filter(String::isNotBlank).joinToString(" · "))
            }
        }
    }
}

/** SteamGridDB with the user's API key: box grids, heroes and logos. */
class SteamGridDb(key: String) {
    private val base = "https://www.steamgriddb.com/api/v2"
    private val headers = mapOf("Authorization" to "Bearer $key")

    private fun get(path: String): JSONArray? = try {
        JSONObject(Net.text(base + path, headers)).optJSONArray("data")
    } catch (e: HttpError) {
        when (e.code) {
            401, 403 -> throw SourceDown("SteamGridDB didn't accept the API key")
            404 -> null
            429 -> { Thread.sleep(2000); null }
            else -> throw e
        }
    }

    /** SteamGridDB's id for [game], when its name search finds a close match. */
    fun gameId(game: Game): Int? = find(game.title)?.first

    /** SteamGridDB's (id, name) for the closest title, when its name search finds a close match. */
    fun find(title: String): Pair<Int, String>? {
        val hits = get("/search/autocomplete/${Net.enc(Library.cleanTitle(title))}").objects()
        return hits.maxByOrNull { Match.similarity(it.optString("name"), title) }
            ?.takeIf { Match.similarity(it.optString("name"), title) >= 0.6f }?.let { it.optInt("id") to it.optString("name") }
    }

    fun candidates(id: Int, kind: MediaKind): List<Candidate> {
        val paths = when (kind) {
            MediaKind.Box -> listOf("/grids/game/$id?dimensions=512x512,1024x1024&types=static&nsfw=false&humor=false", "/grids/game/$id?dimensions=600x900,342x482,660x930&types=static&nsfw=false&humor=false")
            MediaKind.Hero -> listOf("/heroes/game/$id?types=static&nsfw=false&humor=false")
            MediaKind.Logo -> listOf("/logos/game/$id?types=static&nsfw=false&humor=false")
            else -> return emptyList()
        }
        return paths.flatMap { p ->
            get(p).objects().mapNotNull { o ->
                val url = o.optString("url").takeIf { it.startsWith("http") } ?: return@mapNotNull null
                Candidate(kind, "SteamGridDB", url, o.optString("thumb").ifBlank { url }, "${o.optInt("width")}×${o.optInt("height")}")
            }
        }.take(16)
    }

    companion object {
        fun supports(kind: MediaKind) = kind == MediaKind.Box || kind == MediaKind.Hero || kind == MediaKind.Logo
    }
}

internal fun JSONArray?.objects(): List<JSONObject> =
    if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }
