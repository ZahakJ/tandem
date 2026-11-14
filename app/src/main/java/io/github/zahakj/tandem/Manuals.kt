package io.github.zahakj.tandem

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.util.Log
import android.util.LruCache
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.zahakj.tandem.data.ManualKind
import io.github.zahakj.tandem.data.ManualMatch
import io.github.zahakj.tandem.data.ManualText
import io.github.zahakj.tandem.data.PageOrder
import io.github.zahakj.tandem.data.ScanCache
import io.github.zahakj.tandem.data.Store
import java.io.Closeable
import java.io.File
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.zip.ZipFile
import kotlin.math.roundToInt

/*
 * Game manuals and guides: files in ROMs/manuals/<system>/ named like the game (the way artwork is matched, see
 * ArtIndex), or one chosen by hand in Edit details (GameMeta.manual). Read page by page on Tandem's screen from the
 * game's Y menu, and on the bottom screen while the game plays (ui/ManualReader.kt). The plain parts (matching,
 * page order, the text subset) are in data/ManualFiles.kt.
 */
object Manuals {
    /** Where manuals live: a folder per system, named like the system's art folders (`n3ds`, `gb`, `windows`...). */
    val dir: File get() = File(Library.romsDir, "manuals")

    /** Per system id, the manual found for each game stem. Replaced whole by [index]; read from any thread. */
    @Volatile private var found: Map<String, Map<String, File>> = emptyMap()

    /** How many manuals the last scan found, for the Settings row. */
    var count by mutableIntStateOf(0)
        private set

    /** Bumped when the index changes, so a game menu built earlier asks again. */
    var version by mutableIntStateOf(0)
        private set

    /**
     * Looks through ROMs/manuals for every system in [libs], as part of a library scan (Library.scan, off the main
     * thread). Folder listings come from the scan cache like the games' own, so a scan that changed nothing costs
     * nothing here either; with [quick] the remembered listings are trusted outright.
     */
    fun index(libs: List<PlatformLibrary>, quick: Boolean) {
        val out = HashMap<String, Map<String, File>>()
        var n = 0
        for (lib in libs) {
            val byStem = HashMap<String, File>()
            for (id in lib.platform.artIds) {
                val folder = File(dir, id)
                val listing = ScanCache.list(folder, quick)
                for (name in listing.files) {
                    if (name.startsWith(".")) continue
                    val kind = ManualKind.of(name) ?: continue
                    val stem = ManualMatch.stem(name)
                    val known = byStem[stem]
                    if (known == null || ManualMatch.preferred(kindOf(known), kind)) byStem[stem] = File(folder, name)
                }
                // A folder of pictures named like the game is a manual too, when nothing better is there.
                for (name in listing.dirs) {
                    if (name.startsWith(".")) continue
                    byStem.putIfAbsent(ManualMatch.stem(name), File(folder, name))
                }
            }
            if (byStem.isNotEmpty()) { out[lib.platform.id] = byStem; n += byStem.size }
        }
        found = out
        count = n
        version++
    }

    /** The kind of [file]: null for a folder of pictures. */
    fun kindOf(file: File): ManualKind? = if (file.isDirectory) null else ManualKind.of(file.name)

    /** The manual found for [game] by name, if any. */
    fun foundFor(game: Game): File? = found[game.platform.id]?.get(ManualMatch.gameStem(game.file.name))

    /** The manual [game] reads: the one chosen in Edit details when it is still there, else the one found by name. */
    fun forGame(game: Game): File? = FileChecks.file(game.meta.manual) ?: foundFor(game)

    /** [forGame] by the game's key, for the companion (null when the library isn't loaded in this process). */
    fun forKey(key: String): File? = Libraries.byKey(key)?.let { forGame(it) }

    /** The folder a system's manuals go in: its first art folder name under ROMs/manuals. */
    fun folderFor(platform: Platform): File = File(dir, platform.artIds.firstOrNull() ?: platform.id)

    /** The folder as people see it: "ROMs/manuals/gb". */
    fun shortPath(file: File): String = file.path.removePrefix(Library.romsDir.parent.orEmpty()).trimStart('/')

    /** What a manual is, in a few words: "PDF · Pokemon Red.pdf". */
    fun describe(file: File): String = (kindOf(file)?.label ?: "Folder of pictures") + " · " + file.name

    /** The biggest file taken from the picker: a scanned manual is tens of megabytes, a whole strategy guide more. */
    private const val MAX_BYTES = 400L shl 20

    /**
     * Copies a document the user picked (Edit details › Manual › Choose file) into the game's manuals folder, named
     * after the game, and makes it the game's manual. The picker hands out a content address that doesn't outlive the
     * pick, so the file has to come across; it lands next to the user's other manuals where a rescan finds it too.
     * Blocking; returns the file, or null (with the reason logged) when it couldn't be read or isn't a manual.
     */
    fun adopt(ctx: Context, game: Game, uri: Uri): File? {
        val resolver = ctx.contentResolver
        val display = runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        }.getOrNull() ?: uri.lastPathSegment.orEmpty()
        val kind = ManualKind.of(display) ?: kindFromType(resolver.getType(uri)) ?: run {
            Log.w("Tandem", "Not a manual: $display")
            return null
        }
        val ext = display.substringAfterLast('.', "").lowercase().takeIf { it in kind.extensions } ?: kind.extensions.first()
        val folder = folderFor(game.platform)
        if (!folder.isDirectory && !folder.mkdirs()) { Log.w("Tandem", "Couldn't make $folder"); return null }
        // Named like the game, so it is found by name from now on; a second pick with a new kind sits beside the first.
        var target = File(folder, "${game.stem}.$ext")
        var n = 2
        while (target.exists()) target = File(folder, "${game.stem}__$n.$ext").also { n++ }
        val tmp = File(folder, target.name + ".part")
        return try {
            var total = 0L
            resolver.openInputStream(uri)?.use { input ->
                tmp.outputStream().use { out ->
                    val buf = ByteArray(64 shl 10)
                    while (true) {
                        val r = input.read(buf)
                        if (r < 0) break
                        total += r
                        if (total > MAX_BYTES) throw java.io.IOException("over ${MAX_BYTES shr 20} MB")
                        out.write(buf, 0, r)
                    }
                }
            } ?: throw java.io.IOException("can't open $uri")
            if (!tmp.renameTo(target)) throw java.io.IOException("can't move into place")
            Store.update(game.key) { it.copy(manual = target.path) }
            FileChecks.clear()
            version++
            target
        } catch (t: Throwable) {
            Log.w("Tandem", "Couldn't bring the manual over: ${t.message}")
            tmp.delete()
            null
        }
    }

    private fun kindFromType(mime: String?): ManualKind? = when {
        mime == null -> null
        mime == "application/pdf" -> ManualKind.Pdf
        mime.endsWith("zip") -> ManualKind.Zip
        mime.startsWith("text/") -> ManualKind.Text
        mime.startsWith("image/") -> ManualKind.Image
        else -> null
    }

    /** The document picker's filter: everything a manual can be. */
    val pickerTypes = arrayOf("application/pdf", "application/zip", "application/vnd.comicbook+zip", "application/x-cbz", "text/*", "image/*")
}

/**
 * An open manual, read on a background thread. [Paged] manuals draw bitmaps on demand at the width asked for and keep
 * a few of them ([PagedManual.page]); a [Text] manual is pages of blocks the reader lays out itself. Close it when
 * the reader goes away.
 */
sealed class ManualDoc(val file: File) : Closeable {
    abstract val pageCount: Int

    class Text(file: File, val pages: List<ManualText.Page>) : ManualDoc(file) {
        override val pageCount: Int get() = pages.size
        override fun close() {}
    }

    companion object {
        /** The most text a guide is read from (a few megabytes is already a very long FAQ). */
        private const val MAX_TEXT = 4L shl 20

        /** Opens [file] (a file or a folder of pictures), or null when it can't be read. Blocking. */
        fun open(file: File): ManualDoc? = runCatching {
            when {
                file.isDirectory -> FolderManual(file, PageOrder.natural(file.list()?.toList().orEmpty()).map { File(file, it) })
                else -> when (ManualKind.of(file.name)) {
                    ManualKind.Pdf -> PdfManual(file)
                    ManualKind.Zip -> ZipManual(file)
                    ManualKind.Image -> FolderManual(file, listOf(file))
                    ManualKind.Text -> {
                        if (file.length() > MAX_TEXT) throw java.io.IOException("too long")
                        Text(file, ManualText.pages(ManualText.parse(file.readText())))
                    }
                    null -> null
                }
            }?.takeIf { it.pageCount > 0 }
        }.onFailure { Log.w("Tandem", "Couldn't open the manual ${file.name}: ${it.message}") }.getOrNull()
    }
}

/** A manual whose pages are pictures, drawn one at a time at the width the screen needs. */
abstract class PagedManual(file: File) : ManualDoc(file) {
    /** Width over height of each page drawn so far, so the reader can lay a page out before its picture arrives. */
    private val aspects = ConcurrentHashMap<Int, Float>()

    /** Only a few pages are kept: enough for the one shown, the one before and the one after at a screen's width. */
    private val cache = object : LruCache<String, Bitmap>(cacheBytes()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
    }

    fun aspect(index: Int): Float? = aspects[index] ?: aspects.values.firstOrNull()

    /**
     * Page [index] drawn [widthPx] wide (rounded up to a step, so small layout changes reuse the picture; capped at
     * [MAX_WIDTH]). From the cache when it is there. One page draws at a time: PdfRenderer isn't thread-safe.
     */
    fun page(index: Int, widthPx: Int): Bitmap? {
        if (index !in 0 until pageCount) return null
        val w = ((widthPx.coerceAtLeast(64) + STEP - 1) / STEP * STEP).coerceAtMost(MAX_WIDTH)
        val key = "$index@$w"
        cache.get(key)?.let { return it }
        return synchronized(this) {
            cache.get(key) ?: runCatching { draw(index, w) }.onFailure { Log.w("Tandem", "Couldn't draw page ${index + 1} of ${file.name}: ${it.message}") }
                .getOrNull()?.also {
                    aspects[index] = it.width / it.height.toFloat()
                    cache.put(key, it)
                }
        }
    }

    protected abstract fun draw(index: Int, widthPx: Int): Bitmap?

    override fun close() { cache.evictAll() }

    companion object {
        const val STEP = 64
        const val MAX_WIDTH = 1600
        /** The tallest a page is drawn, so a long scan can't ask for a bitmap bigger than the GPU takes. */
        const val MAX_HEIGHT = 4096

        private fun cacheBytes(): Int = (Runtime.getRuntime().maxMemory() / 6).coerceAtMost(48L shl 20).toInt()

        /** The sample size that decodes a picture [srcW] wide to about [wantW] without going under it. */
        fun sampleFor(srcW: Int, wantW: Int): Int {
            var s = 1
            while (srcW / (s * 2) >= wantW) s *= 2
            return s
        }
    }
}

/** A PDF through Android's own renderer: a white page with the content drawn over it. */
private class PdfManual(file: File) : PagedManual(file) {
    private val fd: ParcelFileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer = try { PdfRenderer(fd) } catch (t: Throwable) { fd.close(); throw t }
    override val pageCount: Int = renderer.pageCount

    override fun draw(index: Int, widthPx: Int): Bitmap? {
        renderer.openPage(index).use { p ->
            if (p.width <= 0 || p.height <= 0) return null
            var w = widthPx
            var h = (w * p.height / p.width.toFloat()).roundToInt().coerceAtLeast(1)
            if (h > MAX_HEIGHT) { w = (MAX_HEIGHT * p.width / p.height.toFloat()).roundToInt().coerceAtLeast(1); h = MAX_HEIGHT }
            val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            // The renderer leaves the background clear; a manual page is paper.
            b.eraseColor(android.graphics.Color.WHITE)
            p.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            return b
        }
    }

    override fun close() {
        super.close()
        runCatching { renderer.close() }
        runCatching { fd.close() }
    }
}

/** Pictures, each decoded at about the width asked for (never the whole archive at once). */
private fun decodeSized(open: () -> InputStream?, widthPx: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    open()?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return null
    if (bounds.outWidth <= 0) return null
    val opts = BitmapFactory.Options().apply {
        inSampleSize = PagedManual.sampleFor(bounds.outWidth, widthPx)
        // Scans and screenshots read fine without an alpha channel, at half the memory.
        inPreferredConfig = Bitmap.Config.RGB_565
    }
    return open()?.use { BitmapFactory.decodeStream(it, null, opts) }
}

/** A zip (or .cbz) of pictures, one page each, in natural name order. */
private class ZipManual(file: File) : PagedManual(file) {
    private val zip = ZipFile(file)
    private val names: List<String> = PageOrder.natural(zip.entries().asSequence().map { it.name }.toList())
    override val pageCount: Int get() = names.size

    override fun draw(index: Int, widthPx: Int): Bitmap? {
        val entry = zip.getEntry(names[index]) ?: return null
        return decodeSized({ zip.getInputStream(entry) }, widthPx)
    }

    override fun close() {
        super.close()
        runCatching { zip.close() }
    }
}

/** A folder of pictures (or a single picture), one page each. */
private class FolderManual(file: File, private val pages: List<File>) : PagedManual(file) {
    override val pageCount: Int get() = pages.size
    override fun draw(index: Int, widthPx: Int): Bitmap? = decodeSized({ pages[index].inputStream() }, widthPx)
}

/**
 * The page each game's manual was left open at (`manual-pages.json` in the app's files folder), so it opens where
 * reading stopped. Written in the background like the other stores; a small file, kept apart from library.json so a
 * page turn never rewrites the whole library.
 */
object ManualPages {
    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
    private val writer = Executors.newSingleThreadExecutor()
    private var file: File? = null

    private var pages by mutableStateOf<Map<String, Int>>(emptyMap())

    fun init(ctx: Context) {
        if (file != null) return
        val f = File(ctx.applicationContext.filesDir, "manual-pages.json")
        file = f
        pages = runCatching { json.decodeFromString<Map<String, Int>>(f.readText()) }.getOrDefault(emptyMap())
    }

    /** The page (from 0) [key]'s manual was left at; 0 when it was never opened. */
    fun get(key: String): Int = pages[key] ?: 0

    fun set(key: String, page: Int) {
        if (pages[key] == page) return
        synchronized(this) { pages = pages + (key to page) }
        val snapshot = pages
        val f = file ?: return
        writer.execute {
            val tmp = File(f.path + ".tmp")
            runCatching { tmp.writeText(json.encodeToString(kotlinx.serialization.serializer<Map<String, Int>>(), snapshot)); tmp.renameTo(f) }
        }
    }
}
