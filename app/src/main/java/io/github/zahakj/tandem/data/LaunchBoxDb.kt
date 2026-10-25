package io.github.zahakj.tandem.data

import android.content.Context
import android.net.ConnectivityManager
import android.util.Xml
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.zahakj.tandem.Game
import io.github.zahakj.tandem.Libraries
import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.InterruptedIOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Properties
import java.util.zip.ZipInputStream

/**
 * The LaunchBox Games Database on this device (see LaunchBox.kt for the index format). Downloaded only when the
 * user agrees (it's about 100 MB), read while it downloads (the zip itself is never stored), and refreshed at
 * most once a month, only on Wi-Fi, and only when the user starts a scrape.
 */
object LaunchBoxDb {
    const val URL = "https://gamesdb.launchbox-app.com/Metadata.zip"
    /** The download's size, for the warning (the file was 104 MB in October 2026). */
    const val APPROX_MB = 105
    private const val MONTH = 30L * 86_400_000L

    data class Info(val built: Long, val platforms: Set<String>, val games: Int) {
        val stale: Boolean get() = System.currentTimeMillis() - built > MONTH
    }

    private var dir: File? = null

    /** What's on the device; null until the first download. Compose state for the settings and setup screens. */
    var info by mutableStateOf<Info?>(null)
        private set

    /** 0..1 while downloading, else null. */
    var progress by mutableStateOf<Float?>(null)
        private set

    fun init(ctx: Context) {
        if (dir != null) return
        dir = File(ctx.filesDir, "launchbox")
        info = readInfo()
    }

    private fun readInfo(): Info? = runCatching {
        val f = File(dir ?: return null, "info.properties")
        if (!f.isFile) return null
        val p = Properties().apply { f.inputStream().use { load(it) } }
        Info(
            p.getProperty("built")?.toLongOrNull() ?: 0L,
            p.getProperty("platforms").orEmpty().split('|').filter { it.isNotEmpty() }.toSet(),
            p.getProperty("games")?.toIntOrNull() ?: 0,
        )
    }.getOrNull()

    fun index(): LbIndex? = dir?.takeIf { info != null }?.let(::LbIndex)

    fun platformOf(game: Game): String? = ScrapeSystems.launchBox(game.systemIds)

    /** LaunchBox platforms of the systems the user has games for. */
    fun wantedPlatforms(): Set<String> =
        Libraries.all.orEmpty().filter { it.games.isNotEmpty() }.mapNotNull { lib -> ScrapeSystems.launchBox(lib.games.first().systemIds) }.toSet()

    /** True when the index is missing, or misses one of [platforms]. */
    fun missing(platforms: Set<String>): Boolean {
        val i = info ?: return true
        return platforms.any { it !in i.platforms }
    }

    /** False when the device has no working internet connection (Wi-Fi out of range, airplane mode). */
    fun online(ctx: Context): Boolean = runCatching {
        val cm = ctx.getSystemService(ConnectivityManager::class.java) ?: return@runCatching true
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return@runCatching false
        caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }.getOrDefault(true)

    /** True on mobile data or a hotspot, where a 100 MB download costs money. */
    fun metered(ctx: Context): Boolean = runCatching {
        ctx.getSystemService(ConnectivityManager::class.java)?.isActiveNetworkMetered ?: false
    }.getOrDefault(false)

    /**
     * Downloads Metadata.zip and builds the index for [platforms] (plus whatever the old index had). Blocking: run
     * it on an IO thread with runInterruptible, so cancelling interrupts it. The old index stays in use until the
     * new one is complete.
     */
    fun download(platforms: Set<String>, onProgress: (Float) -> Unit = {}) { synchronized(this) {
        // A second caller (Settings and a scrape at once) waits for the first, then finds the work done.
        info?.let { i -> if (System.currentTimeMillis() - i.built < 10 * 60_000L && i.platforms.containsAll(platforms)) { onProgress(1f); return } }
        val root = dir ?: throw IOException("not ready")
        val keep = platforms + info?.platforms.orEmpty()
        val tmp = File(root.parentFile, "launchbox.part").apply { deleteRecursively(); mkdirs() }
        val c = URL(URL).openConnection() as HttpURLConnection
        progress = 0f
        // Reading half a gigabyte of XML takes a core for a minute or more: do it below the screens' threads,
        // whoever called (Settings uses Dispatchers.IO).
        val tid = android.os.Process.myTid()
        val priority = android.os.Process.getThreadPriority(tid)
        android.os.Process.setThreadPriority(maxOf(priority, android.os.Process.THREAD_PRIORITY_BACKGROUND))
        try {
            c.connectTimeout = 15_000
            c.readTimeout = 30_000
            c.setRequestProperty("User-Agent", Net.AGENT)
            if (c.responseCode !in 200..299) throw HttpError(c.responseCode, "")
            val total = c.contentLengthLong.takeIf { it > 0 } ?: (APPROX_MB * 1_048_576L)
            val counted = Counting(c.inputStream)
            val zip = ZipInputStream(counted.buffered(256 * 1024))
            var stats: LbBuildStats? = null
            while (true) {
                val e = zip.nextEntry ?: break
                if (e.name.substringAfterLast('/') != "Metadata.xml") continue
                val parser = Xml.newPullParser()
                parser.setInput(zip, "UTF-8")
                var shown = 0
                stats = LbIndexBuilder(tmp, keep).parse(parser) {
                    if (Thread.currentThread().isInterrupted) throw InterruptedIOException("cancelled")
                    val f = (counted.count.toFloat() / total).coerceIn(0f, 0.99f)
                    // Screens show whole percents: only tell them (Compose state, from this thread) when one passes.
                    val pct = (f * 100).toInt()
                    if (pct != shown) { shown = pct; progress = f; onProgress(f) }
                }
                break
            }
            val s = stats ?: throw IOException("LaunchBox's file didn't have the games list")
            if (s.games == 0) throw IOException("LaunchBox's games list was empty")
            Properties().apply {
                setProperty("built", System.currentTimeMillis().toString())
                setProperty("platforms", keep.joinToString("|"))
                setProperty("games", s.games.toString())
            }.let { p -> File(tmp, "info.properties").outputStream().use { p.store(it, "Tandem's LaunchBox index") } }
            // Swap the new index in.
            val old = File(root.parentFile, "launchbox.old").apply { deleteRecursively() }
            if (root.exists() && !root.renameTo(old)) root.deleteRecursively()
            if (!tmp.renameTo(root)) throw IOException("Couldn't save the LaunchBox index")
            old.deleteRecursively()
            info = readInfo()
            // Games still without a description may be in it now.
            DetailsFill.request()
            onProgress(1f)
        } finally {
            runCatching { android.os.Process.setThreadPriority(tid, priority) }
            progress = null
            c.disconnect()
            tmp.deleteRecursively()
        }
    } }

    /** Frees the space; scraping then skips LaunchBox until it's downloaded again. */
    fun delete() {
        dir?.deleteRecursively()
        info = null
    }

    /** Size of the index on disk. Reads the disk: call off the main thread. */
    fun sizeOnDisk(): Long = dir?.walkTopDown()?.filter { it.isFile }?.sumOf { it.length() } ?: 0L

    private class Counting(input: InputStream) : FilterInputStream(input) {
        @Volatile var count = 0L
        override fun read(): Int = super.read().also { if (it >= 0) count++ }
        override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also { if (it > 0) count += it }
    }
}
