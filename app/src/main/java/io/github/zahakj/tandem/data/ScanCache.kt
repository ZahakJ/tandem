package io.github.zahakj.tandem.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Remembers what was in every folder Tandem scanned (games and artwork), so:
 * - at startup the library is built from memory without touching the storage at all, and
 * - a rescan only lists folders whose modification time changed (adding, removing or renaming a file updates it).
 * Kept as scan-cache.json in Tandem's files folder; deleting it just means one slower scan.
 */
object ScanCache {
    @Serializable
    data class Listing(val modified: Long, val files: List<String>, val dirs: List<String>)

    private val EMPTY = Listing(0, emptyList(), emptyList())
    private val json = Json { ignoreUnknownKeys = true }
    private var file: File? = null
    private val listings = ConcurrentHashMap<String, Listing>()
    @Volatile private var dirty = false

    /** Bumped whenever a folder's contents changed since it was last listed, so callers can skip no-op rescans. */
    @Volatile var generation = 0
        private set

    fun init(ctx: Context) {
        if (file != null) return
        synchronized(this) {
            if (file != null) return
            val f = File(ctx.applicationContext.filesDir, "scan-cache.json")
            runCatching { json.decodeFromString<Map<String, Listing>>(f.readText()) }.getOrNull()?.let(listings::putAll)
            file = f
        }
    }

    /**
     * What is in [dir]. With [trust], a remembered listing is returned as is (fast startup); otherwise the folder's
     * modification time is checked and it is only listed again when it changed.
     */
    fun list(dir: File, trust: Boolean): Listing {
        val key = dir.path
        val known = listings[key]
        if (trust && known != null) return known
        val modified = dir.lastModified()
        if (modified == 0L) {
            // Gone (or never there).
            if (known != null) { listings.remove(key); dirty = true; generation++ }
            return EMPTY
        }
        if (known != null && known.modified == modified) return known
        val entries = dir.listFiles() ?: return EMPTY
        val (dirs, files) = entries.partition { it.isDirectory }
        val fresh = Listing(modified, files.map { it.name }, dirs.map { it.name })
        listings[key] = fresh
        dirty = true
        if (known == null || known.files != fresh.files || known.dirs != fresh.dirs) generation++
        return fresh
    }

    /** Writes the cache if anything changed. Call off the main thread after a scan. */
    fun save() {
        val target = file ?: return
        if (!dirty) return
        dirty = false
        runCatching {
            val tmp = File(target.path + ".tmp")
            tmp.writeText(json.encodeToString(HashMap(listings)))
            tmp.renameTo(target)
        }
    }
}
