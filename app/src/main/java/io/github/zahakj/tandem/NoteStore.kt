package io.github.zahakj.tandem

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import io.github.zahakj.tandem.data.NoteDoc
import io.github.zahakj.tandem.data.NoteDoc.Block
import io.github.zahakj.tandem.data.NoteEntry
import io.github.zahakj.tandem.data.NoteFiles
import io.github.zahakj.tandem.data.Notebook
import io.github.zahakj.tandem.data.SketchData
import io.github.zahakj.tandem.data.Sketches
import java.io.File
import java.util.concurrent.Executors
import kotlin.math.max

/**
 * Notes on the device (data/Notes.kt has the layout): the notes folder, which notebooks show (a hidden game's stay out
 * of sight like the game), saving, drawings and screenshots. Used by the Notes app and by the companion during play.
 */
object NoteStore {
    val root: File get() = File(Library.romsDir, "notes")

    /** Bumped after every change on disk, so lists read the folder again. */
    var version by mutableIntStateOf(0)
        private set

    fun changed() { version++ }

    /** Saves run one after another, off the main thread, in the order they were made. */
    private val writer = Executors.newSingleThreadExecutor { r -> Thread(r, "tandem-notes") }

    fun later(work: () -> Unit) = writer.execute { runCatching(work).onFailure { Log.w("Tandem", "Notes: couldn't save", it) } }

    /** [work] after the saves before it, then [then] on the main thread with its result. */
    fun <T> later(work: () -> T, then: (T?) -> Unit) = writer.execute {
        val r = runCatching(work).onFailure { Log.w("Tandem", "Notes: couldn't save", it) }.getOrNull()
        main.post { then(r) }
    }

    private val main = android.os.Handler(android.os.Looper.getMainLooper())

    /** Every notebook Tandem may show now, with its notes, newest change first within each. Blocking. */
    fun scan(): List<Pair<Notebook, List<NoteEntry>>> =
        NoteFiles.notebooks(root).filter { visible(it) }.map { it to NoteFiles.notes(it) }

    /** A game's notebook shows only while the game may (Privacy): hidden games' notes stay out of sight too. */
    fun visible(book: Notebook): Boolean = book.gameKey?.let { Privacy.canShowKey(it) } ?: true

    fun gameOf(book: Notebook): Game? = book.gameKey?.let { Libraries.byKey(it) }

    /** [game]'s notebook: the one it has, or the folder it will get with its first note. Blocking (lists the folder). */
    fun bookFor(game: Game): Notebook = bookFor(game.key, game.title, game.platform.shortName)

    fun bookFor(key: String, title: String, system: String): Notebook =
        NoteFiles.bookFor(root, key) ?: Notebook(NoteFiles.folderFor(root, key, title, system), key)

    /** A new note in [book] (its folder made first, with the game's `.game` file). Blocking. */
    fun create(book: Notebook, base: String, text: String = ""): File {
        val real = book.gameKey?.let { key ->
            val g = Libraries.byKey(key)
            NoteFiles.ensureBook(root, key, g?.title ?: book.name, g?.platform?.shortName ?: "")
        } ?: book
        return NoteFiles.create(real, base, text).also { changed() }
    }

    fun read(file: File): List<Block> = NoteDoc.parse(runCatching { file.readText() }.getOrDefault(""))

    fun save(file: File, blocks: List<Block>) {
        val text = NoteDoc.write(blocks)
        later { file.writeText(text); changed() }
    }

    // --- Drawings ----------------------------------------------------------------------------------------------

    /** The strokes kept beside a drawing, if it was drawn here. */
    fun loadSketch(png: File): SketchData? =
        NoteFiles.sidecar(png).takeIf { it.isFile }?.let { Sketches.decode(runCatching { it.readText() }.getOrDefault("")) }

    /** Reads a picture at most [maxSide] px on its longer side (screenshots are drawn on at about screen size). */
    fun decode(f: File, maxSide: Int = 2400): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, bounds)
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > maxSide) sample *= 2
        BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }.getOrNull()

    /** Flattens [data] (over its background, beside [png]) into [png] and keeps the strokes beside it. Blocking. */
    fun saveSketch(png: File, data: SketchData): Boolean = runCatching {
        png.parentFile?.mkdirs()
        val bg = data.background?.let { decode(File(png.parentFile, it)) }
        val full = Bitmap.createBitmap(data.w, data.h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(full)
        if (bg != null) canvas.drawBitmap(bg, null, Rect(0, 0, data.w, data.h), Paint(Paint.FILTER_BITMAP_FLAG)) else canvas.drawColor(Sketches.argb(data.paper))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
        for (s in data.strokes) {
            paint.color = Sketches.argb(s.color)
            paint.strokeWidth = s.width
            canvas.drawPath(strokePath(s.points).asAndroidPath(), paint)
        }
        val out = data.crop?.let { c -> Bitmap.createBitmap(full, c.left, c.top, c.width.coerceAtMost(data.w - c.left), c.height.coerceAtMost(data.h - c.top)) } ?: full
        png.outputStream().use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
        NoteFiles.sidecar(png).writeText(Sketches.encode(data))
        changed()
        true
    }.getOrElse { Log.w("Tandem", "Notes: couldn't save a drawing", it); false }

    /**
     * Where an edited drawing goes, and what it starts from. A drawing made here is drawn on again in place; any
     * other picture (a screenshot, a picture from the Gallery) gets a marked copy beside it, so the original stays as
     * it was taken.
     */
    class DrawTarget(val png: File, val start: SketchData, val background: File?)

    /** See [DrawTarget]. [existing] null: a blank sheet, square for a symbol. Blocking. */
    fun drawTarget(note: File, existing: File?, square: Boolean): DrawTarget? {
        if (existing == null) {
            return DrawTarget(NoteFiles.newAsset(note, if (square) "symbol" else "sketch", "png"), Sketches.sheet(square), null)
        }
        loadSketch(existing)?.let { d -> return DrawTarget(existing, d, d.background?.let { File(existing.parentFile, it) }) }
        if (!existing.isFile) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(existing.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        // Marks on a big picture are drawn on a copy at most 2400 px across, which is plenty for notes.
        val scale = minOf(1f, 2400f / max(bounds.outWidth, bounds.outHeight))
        val png = NoteFiles.newAsset(note, existing.nameWithoutExtension.substringBefore('.') + "-marked", "png")
        val bg = if (existing.parentFile == png.parentFile) existing else File(png.parentFile, existing.name).also {
            png.parentFile?.mkdirs()
            if (!it.exists()) existing.copyTo(it)
        }
        return DrawTarget(png, SketchData((bounds.outWidth * scale).toInt(), (bounds.outHeight * scale).toInt(), background = bg.name), bg)
    }

    // --- Screenshots and pictures ------------------------------------------------------------------------------------

    /** Takes a screenshot of the game's screen into [note]'s attachments ([GameShot]). Blocking. */
    fun screenshot(note: File, gameOnTop: Boolean): GameShot.Result {
        val file = NoteFiles.newAsset(note, "shot", "png")
        file.parentFile?.mkdirs()
        return GameShot.capture(file, gameOnTop).also { if (it is GameShot.Result.Saved) changed() }
    }

    /** Copies [picture] into [note]'s attachments. Blocking. */
    fun importPicture(note: File, picture: File): File? = runCatching {
        val to = NoteFiles.newAsset(note, "picture", picture.extension.lowercase().ifEmpty { "png" })
        to.parentFile?.mkdirs()
        picture.copyTo(to)
        changed()
        to
    }.getOrNull()

    /** The picture a link in [note] points at, when it is there. */
    fun picture(note: File, path: String): File? = NoteFiles.resolve(note, path)?.takeIf { it.isFile }
}

/**
 * The path of a stroke through [points] (x, y pairs): smoothed with curves through the midpoints, and a dot for a
 * single point (drawn with round caps).
 */
fun strokePath(points: List<Float>): Path = Path().apply {
    if (points.size < 2) return@apply
    var px = points[0]
    var py = points[1]
    moveTo(px, py)
    if (points.size < 4) { lineTo(px + 0.01f, py); return@apply }
    var i = 2
    while (i + 1 < points.size) {
        val x = points[i]; val y = points[i + 1]
        quadraticTo(px, py, (px + x) / 2f, (py + y) / 2f)
        px = x; py = y
        i += 2
    }
    lineTo(px, py)
}

/** A note open for reading and editing (the Notes app, the companion): its blocks, saved after every change. */
class OpenNote(file: File, known: List<Block>? = null) {
    var file by mutableStateOf(file)
        private set
    /** Null until read ([known]: a note just made, whose blocks need no reading). */
    var blocks by mutableStateOf(known)
        private set

    init {
        if (known == null) NoteStore.later({ NoteStore.read(file) }) { blocks = it ?: emptyList() }
    }

    val title: String get() = file.nameWithoutExtension

    /** Changes the blocks and saves them. */
    fun edit(change: (List<Block>) -> List<Block>) {
        val now = blocks ?: return
        val next = change(now)
        if (next == now) return
        blocks = next
        NoteStore.save(file, next)
    }

    /** Renames the note (after the saves before it); [done] gets the new name, or null when nothing changed. */
    fun rename(name: String, done: (String?) -> Unit) {
        val from = file
        NoteStore.later({ NoteFiles.rename(from, name).let { it to NoteStore.read(it) } }) { r ->
            val (to, read) = r ?: run { done(null); return@later }
            if (to == from) { done(null); return@later }
            file = to
            // The links inside point at the renamed attachments folder now.
            blocks = read
            NoteStore.changed()
            done(to.nameWithoutExtension)
        }
    }
}
