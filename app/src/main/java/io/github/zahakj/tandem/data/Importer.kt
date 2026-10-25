package io.github.zahakj.tandem.data

import android.net.Uri
import android.os.Environment
import android.util.Xml
import io.github.zahakj.tandem.Library
import io.github.zahakj.tandem.PlatformLibrary
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Imports game details from gamelist.xml files, the format ES-DE uses and Cocoon exports. Looks in:
 * - ROMs/<system>/gamelist.xml
 * - ES-DE/gamelists/<system>/gamelist.xml
 * - the newest Cocoon backup in ROMs/backups
 *
 * Existing Tandem data wins for text fields the user may have edited; play counts and times take the larger value.
 */
object Importer {
    private val storage = Environment.getExternalStorageDirectory()

    data class Result(val games: Int, val sources: List<String>)

    /**
     * With [firstTime] everything is brought over, favorites, hidden games and play history included. Later
     * (Settings › Import details) only blanks are filled, so nothing the user changed in Tandem since is undone: an
     * unfavorited game stays unfavorited, a reset play history stays reset, a field typed by hand stays.
     */
    fun importAll(libraries: List<PlatformLibrary>, firstTime: Boolean = false): Result {
        val changes = HashMap<String, (GameMeta) -> GameMeta>()
        val used = mutableListOf<String>()
        val cocoonBackup = File(Library.romsDir, "backups").listFiles { f -> f.isDirectory && f.name.startsWith("cocoon_backup_") }
            ?.maxByOrNull { it.name }

        for (lib in libraries) {
            val files = lib.games.associateBy { it.file.name }
            val ids = (lib.platform.artIds + lib.platform.id).distinct()
            val lists = buildList {
                lib.platform.folders.forEach { add(File(Library.romsDir, "$it/gamelist.xml")) }
                ids.forEach { add(File(storage, "ES-DE/gamelists/$it/gamelist.xml")) }
                if (cocoonBackup != null) ids.forEach { add(File(cocoonBackup, "categories/metadata/$it/gamelist.xml")) }
            }.filter { it.isFile }
            for (list in lists) {
                var matched = 0
                for (entry in parse(list)) {
                    val game = files[entry.fileName] ?: continue
                    val key = game.key
                    val previous = changes[key]
                    changes[key] = { m -> merge(previous?.invoke(m) ?: m, entry, firstTime) }
                    matched++
                }
                if (matched > 0) used += "${list.parentFile?.name}/${list.name} ($matched)"
            }
        }
        Store.updateAll(changes)
        return Result(changes.size, used)
    }

    private data class Entry(
        val fileName: String,
        val title: String?, val description: String?, val genres: List<String>, val developer: String?,
        val publisher: String?, val year: Int?, val players: String?, val rating: Float?, val favorite: Boolean,
        val hidden: Boolean, val playCount: Int, val playSeconds: Long, val lastPlayed: Long,
        val box: String?, val hero: String?, val logo: String?, val screenshot: String?,
    )

    private fun merge(m: GameMeta, e: Entry, firstTime: Boolean) = m.copy(
        title = m.title ?: e.title,
        description = m.description ?: e.description,
        genres = m.genres.ifEmpty { e.genres },
        developer = m.developer ?: e.developer,
        publisher = m.publisher ?: e.publisher,
        year = m.year ?: e.year,
        players = m.players ?: e.players,
        rating = m.rating ?: e.rating,
        favorite = if (firstTime) m.favorite || e.favorite else m.favorite,
        hidden = if (firstTime) m.hidden || e.hidden else m.hidden,
        playCount = if (firstTime) maxOf(m.playCount, e.playCount) else m.playCount,
        playSeconds = if (firstTime) maxOf(m.playSeconds, e.playSeconds) else m.playSeconds,
        lastPlayed = if (firstTime) maxOf(m.lastPlayed, e.lastPlayed) else m.lastPlayed,
        box = m.box ?: e.box,
        hero = m.hero ?: e.hero,
        logo = m.logo ?: e.logo,
        screenshot = m.screenshot ?: e.screenshot,
    )

    private fun parse(file: File): List<Entry> = runCatching {
        val out = mutableListOf<Entry>()
        file.inputStream().use { input ->
            val p = Xml.newPullParser()
            p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            p.setInput(input, null)
            var fields: MutableMap<String, String>? = null
            var tag: String? = null
            while (p.next() != XmlPullParser.END_DOCUMENT) {
                when (p.eventType) {
                    XmlPullParser.START_TAG -> if (p.name == "game") fields = HashMap() else tag = p.name
                    XmlPullParser.TEXT -> {
                        val f = fields; val t = tag
                        if (f != null && t != null && p.text.isNotBlank()) f[t] = (f[t] ?: "") + p.text
                    }
                    XmlPullParser.END_TAG -> {
                        if (p.name == "game") fields?.let { f -> toEntry(f, file.parentFile!!)?.let(out::add) }.also { fields = null }
                        tag = null
                    }
                }
            }
        }
        out
    }.getOrDefault(emptyList())

    private fun toEntry(f: Map<String, String>, dir: File): Entry? {
        val path = f["path"]?.trim() ?: return null
        fun art(vararg keys: String) = keys.firstNotNullOfOrNull { k -> f[k]?.trim()?.let { resolve(it, dir) } }
        return Entry(
            fileName = path.substringAfterLast('/'),
            title = (f["cocoon:title"] ?: f["name"])?.trim(),
            description = f["desc"]?.trim(),
            genres = f["genre"]?.split(';', ',', '/')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty(),
            developer = f["developer"]?.trim(),
            publisher = f["publisher"]?.trim(),
            year = f["releasedate"]?.take(4)?.toIntOrNull(),
            players = f["players"]?.trim(),
            rating = f["rating"]?.toFloatOrNull(),
            favorite = f["favorite"]?.trim() == "true",
            hidden = f["hidden"]?.trim() == "true",
            playCount = f["playcount"]?.trim()?.toIntOrNull() ?: 0,
            // Cocoon stores play time in minutes.
            playSeconds = (f["cocoon:playtime"]?.trim()?.toLongOrNull() ?: 0L) * 60,
            lastPlayed = f["lastplayed"]?.trim()?.let(::esDate) ?: 0L,
            box = art("cocoon:boxartlocal", "image", "thumbnail"),
            hero = art("cocoon:herolocal", "fanart"),
            logo = art("cocoon:logolocal", "marquee"),
            screenshot = art("cocoon:screenshotgameplaylocal", "cocoon:screenshottitlelocal", "screenshot"),
        )
    }

    /** Turns "./media/x.png", "~/x.png", absolute paths and externalstorage document links into existing file paths. */
    private fun resolve(value: String, dir: File): String? {
        val file = when {
            value.startsWith("content://") -> {
                val docId = Uri.parse(value).lastPathSegment ?: return null
                if (!docId.startsWith("primary:")) return null
                File(storage, docId.removePrefix("primary:"))
            }
            value.startsWith("~/") -> File(storage, value.removePrefix("~/"))
            value.startsWith("/") -> File(value)
            else -> File(dir, value.removePrefix("./"))
        }
        return file.takeIf { it.isFile }?.path
    }

    private fun esDate(s: String): Long? =
        runCatching { SimpleDateFormat("yyyyMMdd'T'HHmmss", Locale.US).parse(s)?.time }.getOrNull()
}
