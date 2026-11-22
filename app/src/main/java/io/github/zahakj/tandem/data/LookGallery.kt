package io.github.zahakj.tandem.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder

/*
 * The community gallery: a public GitHub repository of shared looks (its template is tools/gallery-template/). Tandem
 * reads its index.json and files from GitHub's raw content, with no account; sharing goes through a prefilled GitHub
 * issue form in the browser. Every address comes from [GalleryUrls.REPO].
 *
 * Plain Kotlin: GalleryIndexTest checks the parsing and the links.
 */

/** One look in the gallery's index.json. */
@Serializable
data class GalleryEntry(
    val id: String,
    val name: String,
    val author: String = "",
    val description: String = "",
    /** The look.json version of its file. */
    val version: Int = 1,
    /** Path in the repository of its preview picture. */
    val preview: String = "",
    /** Path in the repository of its .tandem file. */
    val file: String,
    val sha256: String,
    val size: Long,
    val tags: List<String> = emptyList(),
    /** When it was added, "2026-11-22". */
    val added: String = "",
) {
    /** False when it needs a newer Tandem than this one. */
    val supported: Boolean get() = version <= LookFile.VERSION
}

@Serializable
data class GalleryIndexFile(val format: String = GalleryIndex.FORMAT, val version: Int = 1, val looks: List<GalleryEntry> = emptyList())

object GalleryIndex {
    const val FORMAT = "tandem-gallery"
    const val VERSION = 1
    /** The index itself; a gallery of thousands of looks is well under this. */
    const val MAX_BYTES = 2L shl 20
    const val MAX_PREVIEW = 1L shl 20

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
    private val ID = Regex("[a-z0-9][a-z0-9-]{0,47}")
    private val FILE = Regex("looks/[a-z0-9][a-z0-9-]{0,47}/[a-z0-9][a-z0-9._-]{0,63}\\.tandem")
    private val PREVIEW = Regex("looks/[a-z0-9][a-z0-9-]{0,47}/[a-z0-9][a-z0-9._-]{0,63}\\.(png|jpg|jpeg|webp)")
    private val SHA = Regex("[0-9a-f]{64}")
    private val TAG = Regex("[a-z0-9][a-z0-9-]{0,23}")

    /**
     * The looks in an index.json, newest first. An entry that isn't well formed (an id or path outside the
     * gallery's layout, a size over [LookFile.MAX_FILE], a bad checksum) is left out; an index of another format or a
     * newer version throws [LookFileException].
     */
    fun parse(text: String): List<GalleryEntry> {
        val root = runCatching { json.parseToJsonElement(text) as JsonObject }.getOrElse { throw LookFileException(LookProblem.BadJson) }
        if ((root["format"] as? JsonPrimitive)?.content != FORMAT) throw LookFileException(LookProblem.NotALook)
        val version = (root["version"] as? JsonPrimitive)?.content?.toIntOrNull() ?: throw LookFileException(LookProblem.NotALook)
        if (version > VERSION) throw LookFileException(LookProblem.NewerVersion)
        val file = runCatching { json.decodeFromJsonElement(GalleryIndexFile.serializer(), root) }.getOrNull()
            // One broken entry shouldn't hide the rest: read them one by one.
            ?: GalleryIndexFile(looks = (root["looks"] as? kotlinx.serialization.json.JsonArray).orEmpty().mapNotNull { e ->
                runCatching { json.decodeFromJsonElement(GalleryEntry.serializer(), e) }.getOrNull()
            })
        return file.looks.mapNotNull(::clean).distinctBy { it.id }.sortedWith(compareByDescending<GalleryEntry> { it.added }.thenBy { it.name.lowercase() })
    }

    private fun clean(e: GalleryEntry): GalleryEntry? {
        if (!ID.matches(e.id) || !FILE.matches(e.file) || !e.file.startsWith("looks/${e.id}/")) return null
        if (!SHA.matches(e.sha256.lowercase()) || e.size !in 1..LookFile.MAX_FILE) return null
        val name = LookManifest.clean(e.name, 60).ifEmpty { return null }
        return e.copy(
            name = name, author = LookManifest.clean(e.author, 40), description = LookManifest.clean(e.description, 300, lines = true),
            preview = e.preview.takeIf { PREVIEW.matches(it) && it.startsWith("looks/${e.id}/") }.orEmpty(),
            sha256 = e.sha256.lowercase(), tags = e.tags.map { it.lowercase() }.filter { TAG.matches(it) }.distinct().take(8),
            added = e.added.takeIf { Regex("\\d{4}-\\d{2}-\\d{2}").matches(it) }.orEmpty(),
        )
    }

    /** Every tag, the most used first. */
    fun tags(entries: List<GalleryEntry>): List<String> =
        entries.flatMap { it.tags }.groupingBy { it }.eachCount().entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key }).map { it.key }

    /** The looks matching [query] (in the name, author, description or tags; every word must match) and [tag]. */
    fun search(entries: List<GalleryEntry>, query: String, tag: String?): List<GalleryEntry> {
        val words = query.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
        return entries.filter { e ->
            (tag == null || tag in e.tags) && words.all { w ->
                e.name.lowercase().contains(w) || e.author.lowercase().contains(w) || e.description.lowercase().contains(w) || e.tags.any { it.contains(w) }
            }
        }
    }
}

/** Where things are. Change [REPO] and everything follows. */
object GalleryUrls {
    /** The gallery's repository, "owner/name". */
    const val REPO = "ZahakJ/tandem-gallery"
    const val BRANCH = "main"

    private val owner get() = REPO.substringBefore('/')
    private val name get() = REPO.substringAfter('/')

    /** A file in the repository, from GitHub's raw content. */
    fun raw(path: String): String = "https://raw.githubusercontent.com/$REPO/$BRANCH/$path"

    val index: String get() = raw("index.json")

    /** The gallery's web page (GitHub Pages), for people without Tandem. */
    val site: String get() = "https://${owner.lowercase()}.github.io/$name/"

    /** A look's web page: what its QR code holds. The page opens it in Tandem, or offers the file. */
    fun page(id: String): String = "${site}look/?id=${enc(id)}"

    /** The repository on GitHub. */
    val repo: String get() = "https://github.com/$REPO"

    /** The share form, prefilled with what Tandem knows (the file itself is attached by hand). */
    fun shareForm(lookName: String, author: String, description: String): String =
        "$repo/issues/new?template=share-look.yml&labels=look&title=${enc("Look: $lookName")}" +
            "&name=${enc(lookName)}&author=${enc(author)}&description=${enc(description)}"

    /** Opens the look in Tandem directly. */
    fun deepLink(id: String): String = "tandem://look?id=${enc(id)}"

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    /** What a link asks for: a gallery look by [Link.Gallery.id], or a file at an https [Link.Web.url]. */
    sealed interface Link {
        data class Gallery(val id: String) : Link
        data class Web(val url: String) : Link
    }

    /**
     * Reads `tandem://look?id=…`, `tandem://look?url=https://…` and the gallery page's own address
     * (`https://<owner>.github.io/<name>/look/?id=…`). Null for anything else, and for a `url` that isn't https.
     */
    fun parse(link: String): Link? {
        val u = runCatching { URI(link.trim()) }.getOrNull() ?: return null
        val query = (u.rawQuery ?: "").split('&').mapNotNull { p ->
            val i = p.indexOf('=')
            if (i <= 0) null else p.substring(0, i) to runCatching { URLDecoder.decode(p.substring(i + 1), "UTF-8") }.getOrNull()
        }.toMap()
        val ours = when (u.scheme?.lowercase()) {
            "tandem" -> u.host?.lowercase() == "look" || u.schemeSpecificPart.startsWith("look")
            "https" -> u.host?.lowercase() == "${owner.lowercase()}.github.io" && (u.path ?: "").trimEnd('/') == "/$name/look"
            else -> false
        }
        if (!ours) return null
        query["id"]?.trim()?.lowercase()?.takeIf { Regex("[a-z0-9][a-z0-9-]{0,47}").matches(it) }?.let { return Link.Gallery(it) }
        if (u.scheme.equals("tandem", true)) query["url"]?.trim()?.let { url -> safeUrl(url)?.let { return Link.Web(it) } }
        return null
    }

    /** [url] when it is a plain https address (no user name or password in it), else null. */
    fun safeUrl(url: String): String? {
        val u = runCatching { URI(url.trim()) }.getOrNull() ?: return null
        if (!u.scheme.equals("https", true) || u.host.isNullOrBlank() || u.rawUserInfo != null) return null
        return u.toString()
    }
}

/**
 * Downloads for the gallery and links: https only, size-capped, with the index cached by its ETag and Last-Modified so
 * looking again costs a 304. Private mode stops it before anything is sent ([gate]).
 */
class LookDownloads(private val cacheDir: File, private val agent: String, private val gate: () -> Unit = {}) {
    sealed interface Result {
        data class Fresh(val file: File) : Result
        data class Cached(val file: File) : Result
    }

    /** The gallery's index: fetched again only when it changed. Falls back to the cached copy when offline. */
    fun index(url: String = GalleryUrls.index): Result {
        val file = File(cacheDir, "index.json")
        val meta = File(cacheDir, "index.etag")
        val (etag, modified) = meta.takeIf { it.isFile && file.isFile }?.readLines()?.let { it.getOrNull(0).orEmpty() to it.getOrNull(1).orEmpty() } ?: ("" to "")
        val headers = buildMap {
            if (etag.isNotEmpty()) put("If-None-Match", etag)
            if (modified.isNotEmpty()) put("If-Modified-Since", modified)
        }
        val tmp = File(cacheDir, "index.json.part")
        val got = get(url, tmp, GalleryIndex.MAX_BYTES, headers)
        if (got == null) { tmp.delete(); return Result.Cached(file) }
        tmp.renameTo(file)
        meta.writeText("${got.first}\n${got.second}\n")
        return Result.Fresh(file)
    }

    /** A gallery entry's preview picture, kept by its file name and checksum so a changed look gets a new one. */
    fun preview(e: GalleryEntry): File? {
        if (e.preview.isEmpty()) return null
        val f = File(cacheDir, "previews/${e.id}-${e.sha256.take(12)}.${e.preview.substringAfterLast('.')}")
        if (f.isFile) return f
        f.parentFile?.mkdirs()
        val tmp = File(f.path + ".part")
        get(GalleryUrls.raw(e.preview), tmp, GalleryIndex.MAX_PREVIEW)
        val ok = ImageSniff.size(tmp)?.let { s -> s.width <= 2048 && s.height <= 2048 } == true
        if (!ok) { tmp.delete(); return null }
        tmp.renameTo(f)
        return f
    }

    /** Downloads [url] (https only, at most [LookFile.MAX_FILE]) to [to]. */
    fun file(url: String, to: File) {
        get(url, to, LookFile.MAX_FILE) ?: throw IOException("not modified")
    }

    /**
     * GETs [url] into [to], at most [max] bytes. Returns (ETag, Last-Modified), or null for 304 Not Modified. Only
     * https, also after redirects.
     */
    private fun get(url: String, to: File, max: Long, headers: Map<String, String> = emptyMap()): Pair<String, String>? {
        if (GalleryUrls.safeUrl(url) == null) throw LookFileException(LookProblem.BadPath, "https only")
        gate()
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 12_000
            c.readTimeout = 25_000
            // HttpURLConnection never follows a redirect from https to http; the end must still be https.
            c.instanceFollowRedirects = true
            c.setRequestProperty("User-Agent", agent)
            headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
            val code = c.responseCode
            if (code == HttpURLConnection.HTTP_NOT_MODIFIED) return null
            if (!c.url.protocol.equals("https", true)) throw LookFileException(LookProblem.BadPath, "https only")
            if (code !in 200..299) throw HttpError(code, "")
            if (c.contentLengthLong > max) throw LookFileException(LookProblem.TooBig)
            to.parentFile?.mkdirs()
            var n = 0L
            c.inputStream.use { input ->
                to.outputStream().use { out ->
                    val buf = ByteArray(32 * 1024)
                    while (true) {
                        if (Thread.currentThread().isInterrupted) throw IOException("cancelled")
                        val r = input.read(buf)
                        if (r < 0) break
                        n += r
                        if (n > max) throw LookFileException(LookProblem.TooBig)
                        out.write(buf, 0, r)
                    }
                }
            }
            return c.getHeaderField("ETag").orEmpty() to c.getHeaderField("Last-Modified").orEmpty()
        } catch (e: Exception) {
            to.delete()
            throw e
        } finally {
            c.disconnect()
        }
    }
}
