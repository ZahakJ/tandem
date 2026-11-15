package io.github.zahakj.tandem.ui

import android.content.Context
import java.io.File
import android.graphics.BitmapFactory
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import io.github.zahakj.tandem.Game
import io.github.zahakj.tandem.Libraries
import io.github.zahakj.tandem.data.BandMark
import io.github.zahakj.tandem.data.BandSide
import io.github.zahakj.tandem.data.PackSpec
import io.github.zahakj.tandem.data.Packaging
import io.github.zahakj.tandem.data.Store
import io.github.zahakj.tandem.data.playStatus
import io.github.zahakj.tandem.data.systemIds
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.luminance
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * The Shelf view of a folder: its games as cases standing spine out on shelves, a shelf for the folder's own things
 * and one for each folder inside it (a system's genres). Each game stands in its system's real packaging
 * ([Packaging]): the case's plastic and size, a spine printed as the real one is (the cover's art carried round, the
 * system's band at the head, the logo down it, the publisher or the status at the foot) or a scan of the real spine,
 * and the highlighted one swings round to show its cover: a scan of the real box, or the art laid into the system's
 * front template. A Switch case stands slim and tall, a PS2 case taller and thicker, a DS case nearly square, a SNES
 * box lies wide, as the real ones do. Settings › Home & layout › Shelf look picks the bookcase: warm wood, matte in the theme's colours, or open
 * (no bookcase, the cases on the wallpaper with a thin shelf line).
 *
 * Kept light on purpose: a closed case is one spine with no render layers of its own (its logo is drawn, not
 * composed as a picture), and its cover is only composed, with its 3D layers, while it is open or swinging.
 */

/** One shelf: the folder's own things, or a folder inside it with everything in that. */
class ShelfSection(val key: String, val title: String, val color: Color, val items: List<GridItem>, val folderId: String)

/** The main colour of each game's cover, worked out once in the background, for its spine and its cartridge. */
object CoverColors {
    // One state per game, so a colour arriving redraws only the spine (or cartridge) that wears it.
    private val colors = HashMap<String, androidx.compose.runtime.MutableState<Color?>>()

    fun of(ctx: Context, g: Game): Color? {
        val known = colors[g.key]
        if (known != null) return known.value
        val state = androidx.compose.runtime.mutableStateOf<Color?>(null)
        colors[g.key] = state
        val app = ctx.applicationContext
        inBackground({
            val path = Libraries.of(g)?.box(g)?.path ?: return@inBackground
            FolderArtCache.dominant(app, path)?.let { c -> android.os.Handler(android.os.Looper.getMainLooper()).post { state.value = Color(c) } }
        })
        return state.value
    }
}

/**
 * The shape of each game's cover, width over height, read from the picture's header once in the background (never
 * the whole picture). A case takes it, so the cover shows whole and as it really is: tall for a Switch or PS2 box,
 * nearly square for a DS or PS1 one, wide for a Game Boy or SNES one. The shapes are kept in `cover-shapes.tsv`
 * (key, ratio, the picture's size) across launches, so a cover is never drawn in the wrong box on the first pass
 * of a session; a cover whose picture changed size is measured again. [warm] measures a whole list ahead of time.
 */
object CoverShapes {
    private val shapes = HashMap<String, androidx.compose.runtime.MutableState<Float?>>()
    private val sizes = HashMap<String, Long>()
    private var loaded = false
    private var saveDue = false
    private val main = android.os.Handler(android.os.Looper.getMainLooper())

    fun of(ctx: Context, g: Game): Float? {
        load(ctx)
        val known = shapes[g.key]
        if (known != null) return known.value
        val state = androidx.compose.runtime.mutableStateOf<Float?>(null)
        shapes[g.key] = state
        inBackground({
            val file = Libraries.of(g)?.box(g) ?: return@inBackground
            measure(ctx, g.key, file)?.let { r -> main.post { state.value = r } }
        })
        return null
    }

    /** Measures every unknown cover in [games] in one background pass, so the rail or shelf never meets a stranger. */
    fun warm(ctx: Context, games: List<Game>) {
        load(ctx)
        val todo = games.filter { shapes[it.key] == null }
        if (todo.isEmpty()) return
        val states = todo.associate { it.key to androidx.compose.runtime.mutableStateOf<Float?>(null) }
        shapes.putAll(states)
        inBackground({
            for (g in todo) {
                val file = Libraries.of(g)?.box(g) ?: continue
                measure(ctx, g.key, file)?.let { r -> main.post { states.getValue(g.key).value = r } }
            }
        })
    }

    /** The picture's width over height from its header, remembered with its size; null when it can't be read. */
    private fun measure(ctx: Context, key: String, file: File): Float? {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, o)
        if (o.outWidth <= 0 || o.outHeight <= 0) return null
        val ratio = o.outWidth.toFloat() / o.outHeight
        synchronized(sizes) { sizes[key] = file.length() }
        main.post { saveLater(ctx) }
        return ratio
    }

    private fun cache(ctx: Context) = File(ctx.filesDir, "cover-shapes.tsv")

    /** Reads the remembered shapes once; a shape whose picture is now another size is dropped so it is measured again. */
    private fun load(ctx: Context) {
        if (loaded) return
        loaded = true
        val f = cache(ctx)
        if (!f.isFile) return
        runCatching {
            for (line in f.readLines()) {
                val parts = line.split('\t')
                if (parts.size != 3) continue
                val key = parts[0]
                val ratio = parts[1].toFloatOrNull() ?: continue
                val size = parts[2].toLongOrNull() ?: continue
                val game = Libraries.byKey(key) ?: continue
                val file = Libraries.of(game)?.box(game) ?: continue
                if (file.length() != size) continue
                shapes[key] = androidx.compose.runtime.mutableStateOf(ratio)
                sizes[key] = size
            }
        }
    }

    /** Writes the known shapes a moment after the last one arrived, off the main thread. */
    private fun saveLater(ctx: Context) {
        if (saveDue) return
        saveDue = true
        main.postDelayed({
            saveDue = false
            val lines = shapes.mapNotNull { (k, s) -> s.value?.let { r -> sizes[k]?.let { n -> "$k\t$r\t$n" } } }
            inBackground({ runCatching { cache(ctx).writeText(lines.joinToString("\n")) } })
        }, 1500)
    }

    /** Covers as wide as a cover gets to be on a shelf: the widest real boxes are about 1.5 : 1, the tallest 0.56 : 1. */
    fun clamp(ratio: Float?): Float = (ratio ?: DEFAULT).coerceIn(0.56f, 1.5f)
    /** A portrait box, the most common, until the cover's own shape is known. */
    const val DEFAULT = 0.66f
}

/**
 * What the Shelf shows of each game's real packaging, looked up off the main thread (it touches the storage and
 * reads each picture's header): the picture for its front (a scan of the real box when there is one, else the box
 * art), whether that picture is a real box already or needs the system's band round it ([Packaging.isScan]), and
 * a scan of its spine. [warm] looks up a whole folder as it opens; [recheck] looks again for scans after a scrape.
 */
object ShelfPacks {
    /**
     * [thumb] and [logo] are small ready-made copies for the spine (the cover's art stretched soft over it, the logo
     * turned down it), so a spine draws them straight away instead of each starting its own image load.
     */
    class Pack(
        val art: File?, val spine: File?, val aspect: Float?, val scan: Boolean,
        val thumb: androidx.compose.ui.graphics.ImageBitmap? = null, val logo: androidx.compose.ui.graphics.ImageBitmap? = null,
    )

    private val known = HashMap<String, androidx.compose.runtime.MutableState<Pack?>>()
    private val main = android.os.Handler(android.os.Looper.getMainLooper())

    /** The game's packaging, or null while it is still being looked up. Changed box art is looked up again. */
    fun of(ctx: Context, g: Game): Pack? {
        known[id(g)]?.let { return it.value }
        warm(ctx, listOf(g))
        return null
    }

    fun warm(ctx: Context, games: List<Game>) {
        val app = ctx.applicationContext
        val todo = games.filter { known[id(it)] == null }.distinctBy { id(it) }
        if (todo.isEmpty()) return
        val states = todo.associate { id(it) to androidx.compose.runtime.mutableStateOf<Pack?>(null) }
        known.putAll(states)
        inBackground({
            for (g in todo) {
                val pack = runCatching { look(app, g) }.getOrNull() ?: Pack(null, null, null, true)
                main.post { states.getValue(id(g)).value = pack }
            }
        })
    }

    /** Looks again, after a scrape, at the games of [games] that had no front or spine scan. */
    fun recheck(ctx: Context, games: List<Game>) {
        val stale = games.filter { g -> known[id(g)]?.value?.let { it.spine == null || !it.scan } == true }
        if (stale.isEmpty()) return
        stale.forEach { known.remove(id(it)) }
        warm(ctx, stale)
    }

    private fun look(ctx: Context, g: Game): Pack {
        val lib = Libraries.of(g)
        // A scrape saves scans into their folders without telling the art index, so the folder is checked too.
        val front = lib?.boxFront(g) ?: saved(g, io.github.zahakj.tandem.data.MediaKind.Front)
        val art = front ?: lib?.box(g)
        val aspect = art?.let { f ->
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(f.path, o)
            if (o.outWidth > 0 && o.outHeight > 0) o.outWidth.toFloat() / o.outHeight else null
        }
        val spine = lib?.boxSpine(g) ?: saved(g, io.github.zahakj.tandem.data.MediaKind.Spine)
        val spec = Cases.spec(g)
        val scan = Packaging.isScan(aspect, spec, fromScans = front != null) || (art != null && spec != null && banded(art, spec))
        // The spine's own pictures, unless a scan of the real spine stands in for them.
        val thumb = if (spine == null) art?.let { ShelfThumbs.load(ctx, it, 96, alpha = false) } else null
        val logo = if (spine == null) lib?.logo(g)?.let { ShelfThumbs.load(ctx, it, 192, alpha = true) } else null
        return Pack(art, spine, aspect, scan, thumb, logo)
    }

    /** Whether the picture already has the system's band along its edge (a small decode, see [Packaging.hasBand]). */
    private fun banded(f: File, spec: io.github.zahakj.tandem.data.PackSpec): Boolean = runCatching {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, o)
        val opts = BitmapFactory.Options().apply { inSampleSize = maxOf(1, maxOf(o.outWidth, o.outHeight) / 96) }
        val bmp = BitmapFactory.decodeFile(f.path, opts) ?: return@runCatching false
        val px = IntArray(bmp.width * bmp.height)
        bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
        Packaging.hasBand(px, bmp.width, bmp.height, spec).also { bmp.recycle() }
    }.getOrDefault(false)

    private fun saved(g: Game, kind: io.github.zahakj.tandem.data.MediaKind): File? {
        val dir = io.github.zahakj.tandem.data.Media.dirFor(g, kind)
        return listOf("jpg", "png").map { File(dir, "${g.stem}.$it") }.firstOrNull { it.isFile }
    }

    private fun id(g: Game) = g.key + "\u0000" + g.meta.box + "\u0000" + g.meta.logo
}

/**
 * Small copies of the Shelf's spine pictures, made once and kept in the cache folder. A cover is often a big PNG,
 * which has to be inflated whole even for a small copy: 130 of them took seconds of decoding while the shelf
 * scrolled, every time the folder opened. A kept copy is a few kilobytes and decodes at once.
 */
private object ShelfThumbs {
    fun load(ctx: Context, f: File, maxPx: Int, alpha: Boolean): androidx.compose.ui.graphics.ImageBitmap? = runCatching {
        val dir = File(ctx.cacheDir, "shelf-thumbs").apply { mkdirs() }
        val key = "${f.path}:${f.length()}:${f.lastModified()}:$maxPx".hashCode().toUInt().toString(16)
        val kept = File(dir, "$key.webp")
        val bmp = (if (kept.isFile) BitmapFactory.decodeFile(kept.path) else null) ?: run {
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(f.path, o)
            var sample = 1
            while (maxOf(o.outWidth, o.outHeight) / (sample * 2) >= maxPx) sample *= 2
            val src = BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return@runCatching null
            val scale = maxPx.toFloat() / maxOf(src.width, src.height)
            val small = if (scale < 1f) android.graphics.Bitmap.createScaledBitmap(src, (src.width * scale).roundToInt().coerceAtLeast(1), (src.height * scale).roundToInt().coerceAtLeast(1), true)
                .also { if (it !== src) src.recycle() } else src
            kept.outputStream().use {
                small.compress(if (alpha) android.graphics.Bitmap.CompressFormat.WEBP_LOSSLESS else android.graphics.Bitmap.CompressFormat.WEBP_LOSSY, if (alpha) 100 else 80, it)
            }
            small
        }
        // Sent to the GPU now, in the background, not on the frame that first shows it.
        bmp.prepareToDraw()
        bmp.asImageBitmap()
    }.getOrNull()
}

/**
 * Each system's packaging ([Packaging], with the research behind it) and how big its case stands on a shelf. Spines
 * are drawn half again as thick as the real ones (a Switch case's 10 mm on a 170 mm case would be a hairline on a
 * phone) and never thinner than a tenth of the case's height, so the title still reads; the order of thickness is kept.
 */
internal object Cases {
    private val specs = HashMap<String, PackSpec?>()

    /** The packaging of [g]'s system in the user's region (their scrape region), or null for a system without one. */
    fun spec(g: Game): PackSpec? {
        val region = io.github.zahakj.tandem.data.ScrapeConfig.settings.regionPref
        val key = g.platform.id + "/" + region.code
        synchronized(specs) {
            if (key in specs) return specs[key]
            return Packaging.of(g.systemIds, region).also { specs[key] = it }
        }
    }

    /** The spine's thickness over the case's height. */
    fun spine(spec: PackSpec?): Float = if (spec == null) 0.14f else (spec.spineRatio * 1.5f).coerceIn(0.1f, 0.3f)

    /** The case's height in millimetres, for standing cases of several systems side by side to scale. */
    fun height(spec: PackSpec?): Float = spec?.heightMm ?: 190f
}

/** How a case stands on the shelf: its spine, how wide its cover may open, and its height. */
private class CaseSize(val spine: Dp, val coverMax: Dp, val height: Dp)

/** The looks of the bookcase (Settings › Home & layout › Shelf look). */
object ShelfLooks {
    const val WOOD = "wood"
    const val MATTE = "matte"
    const val OPEN = "open"
    val choices = listOf(WOOD to "Wood", MATTE to "Matte", OPEN to "Open")
    fun current(): String = Store.settings.look.shelfLook.takeIf { it in listOf(WOOD, MATTE, OPEN) } ?: WOOD
}

@Composable
fun BookshelfView(
    route: String,
    sections: List<ShelfSection>,
    target: String?,
    onFocus: (GridItem) -> Unit,
    onOpen: (GridItem) -> Unit,
    onLong: (GridItem) -> Unit,
) {
    val list = rememberLazyListState()
    val look = ShelfLooks.current()
    val ctx = LocalContext.current
    val games = remember(sections) { sections.flatMap { s -> s.items.mapNotNull { it.game } } }
    // Every spine's logo, packaging and cover shape looked up in the background as the folder opens, ahead of the scroll.
    LaunchedEffect(games) {
        ShelfPacks.warm(ctx, games)
        CoverShapes.warm(ctx, games)
    }
    // A scrape that just finished may have brought box and spine scans.
    val scraped = io.github.zahakj.tandem.data.Scraper.run?.finished == true
    LaunchedEffect(scraped) { if (scraped) ShelfPacks.recheck(ctx, games) }
    // The thickest spine and the widest cover among the folder's systems, for how many cases fit on a shelf.
    val widest = remember(games) {
        val specs = games.map { Cases.spec(it) }.distinct()
        Pair(specs.maxOfOrNull { Cases.spine(it) } ?: 0.14f, specs.maxOfOrNull { it?.coverRatio ?: 1.15f } ?: 1.15f)
    }
    BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        // Two shelves to a screen, so it reads as a bookcase.
        val shelfH = (maxHeight / 2).coerceIn(170.dp, 280.dp)
        val plank = if (look == ShelfLooks.OPEN) 14.dp else 20.dp
        val side = if (look == ShelfLooks.OPEN) 0.dp else 12.dp
        val caseH = shelfH - plank - 24.dp
        val size = CaseSize(spine = caseH * widest.first, coverMax = caseH * 1.5f, height = caseH)
        // As many spines as fit on a shelf with room left for a cover to swing open (the widest of the folder's
        // boxes, a Game Boy or SNES one); the rest go on the next shelf.
        val perShelf = (((maxWidth - side * 2 - 36.dp) - (caseH * widest.second.coerceAtMost(1.5f) - size.spine)) / (size.spine + 3.dp)).toInt().coerceAtLeast(1)
        val rows = remember(sections, perShelf) { shelfRows(sections, perShelf) }
        // Empty shelves under the last one, so the bookcase always reaches the floor.
        val fill = ((maxHeight / shelfH).toInt() + 1 - rows.size).coerceAtLeast(0)
        val rowOf = remember(rows) { rows.withIndex().flatMap { (i, r) -> r.items.map { it.key to i } }.toMap() }
        KeepInView(list, target?.let { rowOf[it] } ?: -1)
        LazyColumn(Modifier.fillMaxSize().fadingEdges(list), state = list) {
            items(rows.size, key = { rows[it].key }) { i ->
                ShelfRow(route, rows[i], size, shelfH, plank, side, look, i == 0, target, onFocus, onOpen, onLong)
            }
            items(fill, key = { "empty:$it" }) { i ->
                ShelfRow(route, null, size, shelfH, plank, side, look, rows.isEmpty() && i == 0, target, onFocus, onOpen, onLong, pip = i == 0)
            }
        }
    }
}

/** One shelf of the bookcase: some of a section's things, the name plate on its first shelf, and where Right and Left wrap to. */
private class ShelfRow(val key: String, val section: ShelfSection, val items: List<GridItem>, val first: Boolean, val before: String?, val after: String?)

/** [sections] cut into shelves of at most [per] things, each knowing the things just before and after it. */
private fun shelfRows(sections: List<ShelfSection>, per: Int): List<ShelfRow> {
    val chunks = sections.flatMap { s -> s.items.chunked(per).mapIndexed { i, c -> Triple(s, c, i) } }
    return chunks.mapIndexed { i, (s, c, n) ->
        ShelfRow("${s.key}:$n", s, c, n == 0, chunks.getOrNull(i - 1)?.second?.last()?.key, chunks.getOrNull(i + 1)?.second?.first()?.key)
    }
}

@Composable
private fun ShelfRow(
    route: String, row: ShelfRow?, size: CaseSize, shelfH: Dp, plank: Dp, side: Dp, look: String, top: Boolean, target: String?,
    onFocus: (GridItem) -> Unit, onOpen: (GridItem) -> Unit, onLong: (GridItem) -> Unit,
    /** Pip sits on this (empty) shelf. */
    pip: Boolean = false,
) {
    val open = look == ShelfLooks.OPEN
    val shape = if (top && !open) RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp) else RoundedCornerShape(0.dp)
    Box(Modifier.fillMaxWidth().height(shelfH).clip(shape).cubby(look)) {
        if (row != null) {
            // Cases of several systems stand to scale beside each other: the tallest fills the shelf.
            val tallest = remember(row) { row.items.maxOfOrNull { it.game?.let { g -> Cases.height(Cases.spec(g)) } ?: 0f }?.takeIf { it > 0f } ?: 190f }
            Row(
                Modifier.align(Alignment.BottomStart).padding(start = side + 18.dp, bottom = plank),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                row.items.forEachIndexed { i, item ->
                    // Right off the end of a shelf carries on at the start of the next; Left off the start goes back.
                    val wrap = when (i) {
                        row.items.lastIndex -> row.after?.let { Key.DirectionRight to it }
                        0 -> row.before?.let { Key.DirectionLeft to it }
                        else -> null
                    }
                    Standing(route, item, size, tallest, item.key == target, onFocus, onOpen, onLong, wrap)
                }
            }
        }
        if (pip) Pip(size.height * 0.42f, Modifier.align(Alignment.BottomEnd).padding(end = side + 40.dp, bottom = plank - 2.dp), mood = 1, bounce = false)
        Plank(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(plank), look)
        // The section's name on the front of its first shelf: a brass plaque on wood, a small chip otherwise.
        if (row?.first == true) {
            if (look == ShelfLooks.WOOD) Row(
                Modifier.align(Alignment.BottomStart).padding(start = side + 14.dp, bottom = 2.dp).height(plank - 4.dp)
                    .clip(RoundedCornerShape(3.dp)).background(Brush.verticalGradient(listOf(Color(0xFFE2C27A), Color(0xFFA9843C))))
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(6.dp).clip(RoundedCornerShape(50)).background(row.section.color))
                Text(
                    "  ${row.section.title}  ·  ${row.section.items.size}", style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                    color = Color(0xFF3B2A10), maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            } else Row(
                Modifier.align(Alignment.BottomStart).padding(start = side + 14.dp, bottom = if (open) 0.dp else 2.dp).height(if (open) plank else plank - 4.dp)
                    .clip(RoundedCornerShape(50)).background(if (open) Palette.Card.copy(alpha = 0.85f) else row.section.color.copy(alpha = 0.22f))
                    .padding(horizontal = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(6.dp).clip(RoundedCornerShape(50)).background(row.section.color))
                Text(
                    "  ${row.section.title}  ·  ${row.section.items.size}", style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                    color = if (open) Palette.readable(row.section.color) else Palette.Text, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        // The bookcase's sides.
        if (!open) {
            SideBoard(Modifier.align(Alignment.CenterStart).width(side).fillMaxHeight(), look)
            SideBoard(Modifier.align(Alignment.CenterEnd).width(side).fillMaxHeight(), look)
        }
    }
}

@Composable
private fun SideBoard(modifier: Modifier, look: String) {
    val brush = if (look == ShelfLooks.WOOD) Brush.horizontalGradient(listOf(Color(0xFF6E4A30), Color(0xFF8C603F), Color(0xFF5A3B25)))
    else Brush.horizontalGradient(listOf(lerp(Palette.CardHigh, Color.Black, 0.2f), Palette.CardHigh, lerp(Palette.CardHigh, Color.Black, 0.3f)))
    Box(modifier.background(brush))
}

/** The inside of a shelf: a darker back board, shaded under the shelf above and at the sides. Nothing when open. */
private fun Modifier.cubby(look: String): Modifier {
    if (look == ShelfLooks.OPEN) return this
    val wood = look == ShelfLooks.WOOD
    return drawWithCache {
        val back = if (wood) Brush.verticalGradient(listOf(Color(0xFF1E1611), Color(0xFF2E2219), Color(0xFF35271C)))
        else Brush.verticalGradient(listOf(lerp(Palette.Card, Color.Black, 0.35f), Palette.Card, lerp(Palette.Card, Palette.CardHigh, 0.4f)))
        val top = Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent), endY = size.height * 0.28f)
        val side = 14.dp.toPx()
        val left = Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent), endX = side)
        val right = Brush.horizontalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.45f)), startX = size.width - side, endX = size.width)
        onDrawBehind {
            drawRect(back)
            drawRect(top)
            drawRect(left)
            drawRect(right)
        }
    }
}

/**
 * The shelf board. Wood: warm, a lit front edge and a few long grain lines. Matte: a board in the theme's card colour.
 * Open: a thin line on the wallpaper with the cases' shadow above it.
 */
@Composable
private fun Plank(modifier: Modifier, look: String) {
    val high = Palette.CardHigh
    val card = Palette.Card
    Canvas(modifier) {
        val h = size.height
        when (look) {
            ShelfLooks.WOOD -> {
                drawRect(Brush.verticalGradient(listOf(Color(0xFF9A6B45), Color(0xFF7A5134), Color(0xFF5C3B25))))
                drawRect(Color.White.copy(alpha = 0.16f), Offset.Zero, Size(size.width, h * 0.12f))
                val grain = Color(0xFF4A2F1D).copy(alpha = 0.35f)
                var seed = 7
                for (i in 0 until 7) {
                    seed = (seed * 1103515245 + 12345) and 0x7fffffff
                    val y = h * (0.25f + 0.65f * (seed % 1000) / 1000f)
                    val x0 = size.width * ((seed / 1000) % 1000) / 1000f * 0.6f
                    drawLine(grain, Offset(x0, y), Offset(x0 + size.width * 0.45f, y + h * 0.04f), strokeWidth = 1.2f)
                }
                drawRect(Color.Black.copy(alpha = 0.35f), Offset(0f, h - 2.dp.toPx()), Size(size.width, 2.dp.toPx()))
            }
            ShelfLooks.MATTE -> {
                drawRect(Brush.verticalGradient(listOf(lerp(high, Color.White, 0.08f), high, lerp(high, Color.Black, 0.25f))))
                drawRect(Color.White.copy(alpha = 0.12f), Offset.Zero, Size(size.width, h * 0.12f))
                drawRect(Color.Black.copy(alpha = 0.3f), Offset(0f, h - 2.dp.toPx()), Size(size.width, 2.dp.toPx()))
            }
            else -> {
                // The cases' shadow, then the shelf line itself.
                val line = 3.dp.toPx()
                drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.35f)), endY = h - line), size = Size(size.width, h - line))
                drawRect(card.copy(alpha = 0.9f), Offset(0f, h - line), Size(size.width, line))
                drawRect(Color.White.copy(alpha = 0.12f), Offset(0f, h - line), Size(size.width, 1f))
            }
        }
    }
}

/**
 * One thing on the shelf: a game as a case (spine out, swinging round to its cover when highlighted), anything else
 * as a small square box of its own art.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun Standing(
    route: String, item: GridItem, shelf: CaseSize, tallest: Float, wanted: Boolean,
    onFocus: (GridItem) -> Unit, onOpen: (GridItem) -> Unit, onLong: (GridItem) -> Unit,
    /** A direction off the end of its shelf and the thing it goes to there. */
    wrap: Pair<Key, String>? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val press = remember { TilePress() }
    // Read only while laying out and drawing, so the swing re-lays and redraws without recomposing anything.
    val openState = animateFloatAsState(if (focused) 1f else 0f, Motion.soft(), label = "case-open")
    val bring = remember { BringIntoViewRequester() }
    // Once it has swung open, its cover is brought fully into view (it was only a spine when the focus came).
    LaunchedEffect(focused) { if (focused) { kotlinx.coroutines.delay(260); runCatching { bring.bringIntoView() } } }
    val density = LocalDensity.current
    val ctx = LocalContext.current
    val game = item.game
    val base = Modifier
        .bringIntoViewRequester(bring)
        .takesFocus(route, item.key, wanted)
        .liftOrigin(0.05f)
        .touchEntry()
        .onFocusChanged { if (it.isFocused) { onFocus(item); Sounds.move() } else press.pressed = false }
        .focusable(interactionSource = interaction)
        .onKeyEvent { e -> tileKey(e, press, { onOpen(item) }, { onLong(item) }) }
        .onPreviewKeyEvent { e ->
            val (dir, to) = wrap ?: return@onPreviewKeyEvent false
            if (e.key != dir) return@onPreviewKeyEvent false
            if (e.type == KeyEventType.KeyDown) { FocusMemory.remember(route, to); Nav.focusTick++ }
            true
        }
        .pointerInput(item.key) { detectTapGestures(onTap = { onOpen(item); Sounds.select() }) }
    if (game == null) {
        // Not a game: a keepsake box with its tile art, lifting a little when highlighted.
        val box = shelf.height * 0.5f
        Box(
            base.size(box).graphicsLayer {
                val open = openState.value
                translationY = -10.dp.toPx() * open; scaleX = 1f + 0.08f * open; scaleY = scaleX
                shape = RoundedCornerShape(10.dp); clip = true; shadowElevation = (2f + 14f * open) * density.density
            },
        ) { TileArtView(item.art, box) }
        return
    }
    val spec = Cases.spec(game)
    val pack = ShelfPacks.of(ctx, game)
    // The case as its system's real one: its height to scale with the shelf's tallest, its spine's thickness.
    val height = shelf.height * (Cases.height(spec) / tallest).coerceIn(0.6f, 1f)
    val size = CaseSize(spine = height * Cases.spine(spec), coverMax = shelf.coverMax, height = height)
    val spinePx = with(density) { size.spine.toPx() }
    // The cover's shape: a real box scan's own, else the system's real cover (the art is fitted into it). A system
    // without packaging of its own keeps its picture's shape.
    val ratio = when {
        spec == null -> CoverShapes.clamp(CoverShapes.of(ctx, game))
        pack?.scan == true && pack.art != null && pack.aspect != null -> pack.aspect.coerceIn(0.56f, 1.5f)
        else -> spec.coverRatio
    }
    val cover = size.height * ratio
    val coverPx = with(density) { cover.toPx() }
    // Its cover, and the 3D layers of the swing, are only there while it is open or swinging: a closed case is just its spine.
    val swinging by remember { androidx.compose.runtime.derivedStateOf { openState.value > 0f } }
    Box(
        base
            // Only as wide as it shows: neighbours make room as it swings open.
            .layout { m, _ ->
                val rad = Math.toRadians(openState.value * 82.0)
                val shown = (spinePx * cos(rad) + coverPx * sin(rad)).toFloat()
                val shift = spinePx * (1f - cos(rad).toFloat())
                val p = m.measure(Constraints.fixed((spinePx + coverPx).roundToInt(), with(density) { size.height.roundToPx() }))
                layout(shown.roundToInt().coerceAtLeast(1), p.height) { p.place(-shift.roundToInt(), 0) }
            }
            .then(if (swinging) Modifier.graphicsLayer { translationY = -8.dp.toPx() * openState.value } else Modifier),
    ) {
        Row(Modifier.fillMaxSize()) {
            Box(
                Modifier.width(size.spine).fillMaxHeight().then(
                    if (swinging) Modifier.graphicsLayer {
                        transformOrigin = TransformOrigin(1f, 0.5f); rotationY = -82f * openState.value; cameraDistance = 14f * this.density
                    } else Modifier,
                ),
            ) { Spine(game, spec, pack, size.spine, size.height) }
            if (swinging) Box(
                Modifier.width(cover).fillMaxHeight().graphicsLayer {
                    val open = openState.value
                    transformOrigin = TransformOrigin(0f, 0.5f); rotationY = 90f - 82f * open; cameraDistance = 14f * this.density
                    shape = RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp); clip = true
                    shadowElevation = 18f * open * this.density
                    alpha = if (open < 0.02f) 0f else 1f
                },
            ) {
                // In a plastic case the cover sits under its clear sleeve, the plastic showing round it.
                val plastic = spec?.plastic
                Box(Modifier.fillMaxSize().then(if (plastic != null) Modifier.background(Color(plastic)).padding(3.dp).clip(RoundedCornerShape(2.dp)) else Modifier)) {
                    CaseFront(game, spec, pack, size.height)
                }
                // The case's plastic: a soft sheen across the cover, strongest at the hinge.
                Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.35f), Color.Transparent, Color.White.copy(alpha = 0.08f)), endX = coverPx)))
            }
        }
    }
}

/**
 * A case's spine, as the real one is printed: the game's own art carried round from its cover (a strip of the
 * cover's edge, soft and darkened a little so the title reads), the system's band at the head with its mark where
 * the real spine has it, the game's logo (or its title) running down it, the publisher at the foot and the case's
 * plastic showing as a thin rim round the print. A real spine scan, when there is one, is shown instead. All of it
 * is drawn into one canvas (the pictures are small decodes), no layers, so a shelf of forty stays light.
 */
@Composable
private fun Spine(game: Game, spec: PackSpec?, pack: ShelfPacks.Pack?, w: Dp, h: Dp) {
    val ctx = LocalContext.current
    val system = SystemColors.of(game.platform.id)
    val tint = CoverColors.of(ctx, game) ?: lerp(system, Palette.NightDeep, 0.35f)
    val scan = pack?.spine
    val thumb = pack?.thumb?.takeIf { scan == null }
    val artPainter = remember(thumb) { thumb?.let { androidx.compose.ui.graphics.painter.BitmapPainter(it) } }
    val scanPainter = scan?.let { rememberAsyncImagePainter(rememberSizedRequest(it, tilePx(h))) }
    // The logo, made ready with the pack; a scanned spine already has its own. Until the pack is there, neither shows.
    val found = pack?.takeIf { scan == null }
    val logo = found?.logo
    val logoPainter = remember(logo) { logo?.let { androidx.compose.ui.graphics.painter.BitmapPainter(it) } }
    val measurer = rememberTextMeasurer()
    val texts = remember(measurer) { SpineTexts(measurer) }
    val status = game.meta.playStatus
    val publisher = game.meta.publisher
    val plastic = spec?.plastic?.let { Color(it) }
    Box(Modifier.fillMaxSize().clip(RoundedCornerShape(2.dp))) {
        Canvas(Modifier.fillMaxSize()) {
            // The case's plastic round the printed insert: a little more at the head and foot, as a keep case has.
            val rimX = if (plastic != null) 1.5.dp.toPx() else 0f
            val rimY = if (plastic != null) 3.dp.toPx() else 0f
            if (plastic != null) drawRect(Brush.horizontalGradient(listOf(lerp(plastic, Color.Black, 0.25f), plastic, lerp(plastic, Color.Black, 0.18f))))
            val inner = androidx.compose.ui.geometry.Rect(rimX, rimY, size.width - rimX, size.height - rimY)
            clipRect(inner.left, inner.top, inner.right, inner.bottom) {
                translate(inner.left, inner.top) {
                    val area = inner.size
                    if (scanPainter != null) drawSpineScan(scanPainter, area, tint)
                    else drawSpinePrint(artPainter, pack, spec, tint, area)
                }
            }
            if (scanPainter == null) {
                val band = if (spec != null) inner.height * spec.spineBand else inner.height * 0.1f
                // The head: the system's band and mark across the top of the print.
                translate(inner.left, inner.top) { drawSpineHead(spec, game, Size(inner.width, band), texts) }
                // The foot: the publisher, as its logo sits at the foot of a real spine (not where a status is shown).
                val foot = if (status == null && publisher != null) inner.width * 2.4f else if (status != null) inner.width * 0.9f else 0f
                val ink = if (tint.luminance() > 0.6f && artPainter == null) Color(0xFF1B1C1F) else Color.White
                if (status == null && publisher != null) {
                    val style = TextStyle(color = ink.copy(alpha = 0.8f), fontSize = (inner.width * 0.3f).coerceAtMost(9.dp.toPx()).toSp(), fontWeight = FontWeight.Bold)
                    val t = texts.line(publisher.substringBefore(' ').substringBefore(',').uppercase(), style, foot.roundToInt().coerceAtLeast(1))
                    translate(inner.left, inner.bottom - foot) { rotate90 { drawText(t, topLeft = Offset((foot - t.size.width) / 2f, -(inner.width + t.size.height) / 2f)) } }
                }
                // The title down the middle: the game's logo turned on its side, or its name.
                val top = inner.top + band + inner.width * 0.25f
                val bottom = inner.bottom - foot - inner.width * 0.2f
                val along = bottom - top
                if (along > 0f) translate(inner.left, top) {
                    val intrinsic = logoPainter?.intrinsicSize
                    if (logoPainter != null && intrinsic != null && intrinsic.isSpecified && intrinsic.width > 0f && intrinsic.height > 0f) {
                        // The logo's room once turned: as long as the space, as wide as the spine lets it be.
                        val roomW = along; val roomH = inner.width * 0.84f
                        val scale = minOf(roomW / intrinsic.width, roomH / intrinsic.height)
                        val dw = intrinsic.width * scale; val dh = intrinsic.height * scale
                        rotate90 { translate((along - dw) / 2f, -(inner.width + dh) / 2f) { with(logoPainter) { draw(Size(dw, dh)) } } }
                    } else if (found != null && logo == null) {
                        val style = TextStyle(
                            color = ink, fontSize = (inner.width * 0.4f).coerceIn(7.dp.toPx(), 15.dp.toPx()).toSp(), fontWeight = FontWeight.ExtraBold,
                            shadow = androidx.compose.ui.graphics.Shadow(Color.Black.copy(alpha = if (ink == Color.White) 0.7f else 0f), blurRadius = 3f),
                        )
                        val t = texts.line(game.title, style, along.roundToInt().coerceAtLeast(1), TextOverflow.Ellipsis)
                        rotate90 { drawText(t, topLeft = Offset((along - t.size.width) / 2f, -(inner.width + t.size.height) / 2f)) }
                    }
                }
            }
            // Hairline edges, so neighbouring spines of the same colour still read as two.
            drawRect(Color.Black.copy(alpha = 0.5f), size = Size(1.dp.toPx(), size.height))
            drawRect(Color.White.copy(alpha = 0.2f), size = Size(size.width, 1.dp.toPx()))
            // The case's curve: a soft sheen down the middle, shade at the edges.
            drawRect(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.32f), Color.Transparent, Color.White.copy(alpha = 0.1f), Color.Transparent, Color.Black.copy(alpha = 0.36f))))
        }
        // Its status at the foot of the spine (finished, playing…), so a shelf shows where you are at a glance.
        if (status != null) {
            Box(
                Modifier.align(Alignment.BottomCenter).padding(bottom = h * 0.035f).size(w * 0.62f).clip(RoundedCornerShape(50)).background(status.color),
                contentAlignment = Alignment.Center,
            ) { Text(status.mark, style = TextStyle(fontSize = (w.value * 0.3f).coerceIn(7f, 12f).sp, fontWeight = FontWeight.Black), color = Palette.NightDeep, maxLines = 1) }
        }
    }
}

/**
 * A spine's words, laid out once and kept: the canvas is drawn again whenever the shelf around it moves, and laying
 * the same line out on every draw was a good part of each frame.
 */
private class SpineTexts(private val measurer: androidx.compose.ui.text.TextMeasurer) {
    private val laid = HashMap<List<Any>, androidx.compose.ui.text.TextLayoutResult>()

    fun line(text: String, style: TextStyle, maxWidth: Int = Constraints.Infinity, overflow: TextOverflow = TextOverflow.Clip) =
        laid.getOrPut(listOf(text, style, maxWidth, overflow)) {
            measurer.measure(text, style, overflow = overflow, maxLines = 1, constraints = Constraints(maxWidth = maxWidth))
        }
}

/**
 * A scanned spine filling the print: stretched across the spine's width (the drawn spine is thicker than the real
 * one; the title runs along it, so it only grows bolder), and turned upright when it was stored on its side.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSpineScan(painter: androidx.compose.ui.graphics.painter.Painter, area: Size, tint: Color) {
    val intrinsic = painter.intrinsicSize
    if (!intrinsic.isSpecified || intrinsic.width <= 0f || intrinsic.height <= 0f) { drawRect(tint, size = area); return }
    if (intrinsic.width > intrinsic.height) {
        // Stored on its side: turned a quarter clockwise, so its title reads from the top down, as North American spines do.
        rotate90 { translate(0f, -area.width) { with(painter) { draw(Size(area.height, area.width)) } } }
    } else with(painter) { draw(area) }
}

/**
 * The printed spine without a scan: the cover's art carried round onto it, the strip of the cover nearest the spine
 * (for a cover that needs the system's band, the strip the composed front starts with), darkened a little with the
 * cover's own colour so the title reads. Before the art has loaded, or with none, the cover's colour.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSpinePrint(
    painter: androidx.compose.ui.graphics.painter.Painter?, pack: ShelfPacks.Pack?, spec: PackSpec?, tint: Color, area: Size,
) {
    drawRect(Brush.verticalGradient(listOf(lerp(tint, Color.Black, 0.2f), lerp(tint, Color.Black, 0.4f))), size = area)
    val intrinsic = painter?.intrinsicSize
    if (painter == null || intrinsic == null || !intrinsic.isSpecified || intrinsic.width <= 0f || intrinsic.height <= 0f) return
    val scale = area.height / intrinsic.height
    val dw = intrinsic.width * scale
    val shown = if (pack?.scan == false && spec != null) area.height * spec.coverRatio else dw
    // A scan's own edge (less a sliver of its border), or the left edge of the part the composed front shows.
    val x = if (pack?.scan == false) -((dw - shown) / 2f).coerceAtLeast(0f) else -dw * 0.015f
    translate(x, 0f) { with(painter) { draw(Size(dw, area.height)) } }
    drawRect(tint.copy(alpha = 0.28f), size = area)
    drawRect(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.18f), Color.Black.copy(alpha = 0.38f), Color.Black.copy(alpha = 0.18f))), size = area)
}

/** The head of a spine: the system's band in its colour, with its mark (or short name) where the real spine has it. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSpineHead(
    spec: PackSpec?, game: Game, area: Size, texts: SpineTexts,
) {
    val band = spec?.let { Color(it.band) } ?: SystemColors.of(game.platform.id)
    val ink = spec?.let { Color(it.bandInk) } ?: Color.White
    drawRect(band, size = area)
    drawRect(Color.Black.copy(alpha = 0.14f), Offset(0f, area.height - 1.dp.toPx()), Size(area.width, 1.dp.toPx()))
    val c = Offset(area.width / 2f, area.height / 2f)
    val s = minOf(area.width * 0.62f, area.height * 0.62f)
    when (spec?.mark) {
        BandMark.Switch -> drawSwitchMark(c, s, ink, band)
        BandMark.Cube -> drawCubeMark(c, s, Color(0xFF8C7CF0))
        BandMark.Swirl -> drawSwirlMark(c, s, ink)
        else -> {
            if (spec?.mark == BandMark.Grid) drawGrid(area)
            val label = spec?.spineMark?.ifEmpty { null } ?: game.platform.shortName.uppercase().take(6)
            val style = TextStyle(color = ink, fontSize = minOf(area.width * 0.34f, area.height * 0.42f).toSp(), fontWeight = FontWeight.Black)
            var t = texts.line(label, style)
            // A long mark shrinks to the spine's width rather than spilling over it.
            if (t.size.width > area.width * 0.9f) t = texts.line(label, style.copy(fontSize = (style.fontSize.value * area.width * 0.9f / t.size.width).sp))
            drawText(t, topLeft = Offset((area.width - t.size.width) / 2f, (area.height - t.size.height) / 2f))
        }
    }
}

/**
 * The front of an open case, as its real cover is laid out. A real box scan (or art shaped like one, see
 * [Packaging.isScan]) is shown as it is, its band already printed on it. Other art (a square store icon, a grid made
 * for a launcher) gets the system's front template: its band along the top (the left for a PlayStation jewel case)
 * with the system's mark and name, and the art filling the rest of the real cover's shape, cropped from its middle.
 * The ratings box isn't drawn: Tandem doesn't know a game's rating, and a wrong one would mislead.
 */
@Composable
private fun CaseFront(game: Game, spec: PackSpec?, pack: ShelfPacks.Pack?, h: Dp) {
    val art = pack?.art
    if (art == null) { GameArt(game); return }
    val picture = @Composable { modifier: Modifier ->
        androidx.compose.foundation.Image(rememberAsyncImagePainter(rememberSizedRequest(art, tilePx(h))), game.title, modifier, contentScale = ContentScale.Crop)
    }
    if (spec == null || pack?.scan != false) { picture(Modifier.fillMaxSize()); return }
    val measurer = rememberTextMeasurer()
    val band = Modifier.drawBehind { drawFrontBand(spec, measurer) }
    if (spec.side == BandSide.Left) Row(Modifier.fillMaxSize()) {
        Box(band.width(h * spec.coverRatio * spec.frontBand).fillMaxHeight())
        picture(Modifier.weight(1f).fillMaxHeight())
    } else if (spec.side == BandSide.Right) Row(Modifier.fillMaxSize()) {
        picture(Modifier.weight(1f).fillMaxHeight())
        Box(band.width(h * spec.coverRatio * spec.frontBand).fillMaxHeight())
    } else Column(Modifier.fillMaxSize()) {
        Box(band.fillMaxWidth().height(h * spec.frontBand))
        picture(Modifier.fillMaxWidth().weight(1f))
    }
}

/** A cover's band: its colour, and the system's mark and name, at the left of a top band or down a left one. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawFrontBand(spec: PackSpec, measurer: androidx.compose.ui.text.TextMeasurer) {
    val band = Color(spec.band)
    val ink = Color(spec.bandInk)
    drawRect(band)
    if (spec.mark == BandMark.Grid) drawGrid(size)
    val left = spec.side == BandSide.Left
    val right = spec.side == BandSide.Right
    // Measured along the band: across a top band, down a side one.
    val long = if (left || right) size.height else size.width
    val short = if (left || right) size.width else size.height
    val markS = short * 0.62f
    val hasMark = spec.mark == BandMark.Switch || spec.mark == BandMark.Cube || spec.mark == BandMark.Swirl
    val style = TextStyle(color = ink, fontSize = (short * 0.42f).toSp(), fontWeight = FontWeight.Black, letterSpacing = (short * 0.02f).toSp())
    val t = measurer.measure(spec.wordmark, style, maxLines = 1, overflow = TextOverflow.Clip, constraints = Constraints(maxWidth = (long * 0.8f).roundToInt().coerceAtLeast(1)))
    val pad = short * 0.3f
    if (left) {
        // Near the band's top, turned to read from the bottom up, as North America's PlayStation covers have it.
        drawContext.transform.rotate(-90f, Offset.Zero)
        drawText(t, topLeft = Offset(-(pad + t.size.width), (short - t.size.height) / 2f))
        drawContext.transform.rotate(90f, Offset.Zero)
        return
    }
    if (right) {
        // Down the right edge from the top, reading top to bottom, as the 3DS cover has it.
        rotate90 { drawText(t, topLeft = Offset(pad, -(short + t.size.height) / 2f)) }
        return
    }
    var x = pad
    if (hasMark) {
        val c = Offset(x + markS / 2f, short / 2f)
        when (spec.mark) {
            BandMark.Switch -> drawSwitchMark(c, markS, ink, band)
            BandMark.Cube -> drawCubeMark(c, markS, Color(0xFF8C7CF0))
            else -> drawSwirlMark(c, markS, ink)
        }
        x += markS + pad * 0.6f
    }
    drawText(t, topLeft = Offset(x, (short - t.size.height) / 2f))
}

/** Nintendo Switch's mark, drawn: the two Joy-Con, the left an outline with its stick up top, the right solid with its stick low. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSwitchMark(c: Offset, s: Float, ink: Color, under: Color) {
    val w = s * 0.4f; val gap = s * 0.1f
    val r = androidx.compose.ui.geometry.CornerRadius(w * 0.5f)
    val left = Offset(c.x - gap / 2f - w, c.y - s / 2f)
    drawRoundRect(ink, left, Size(w, s), r, style = androidx.compose.ui.graphics.drawscope.Stroke(s * 0.09f))
    drawCircle(ink, w * 0.2f, Offset(left.x + w / 2f, left.y + s * 0.3f))
    val right = Offset(c.x + gap / 2f, c.y - s / 2f)
    drawRoundRect(ink, right, Size(w, s), r)
    drawCircle(under, w * 0.2f, Offset(right.x + w / 2f, right.y + s * 0.6f))
}

/** GameCube's mark, drawn: a cube seen from a corner, in outline. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCubeMark(c: Offset, s: Float, ink: Color) {
    val r = s / 2f
    val pts = (0 until 6).map { i -> val a = Math.toRadians(90.0 + 60.0 * i); Offset(c.x + r * cos(a).toFloat(), c.y - r * sin(a).toFloat()) }
    val stroke = s * 0.11f
    for (i in 0 until 6) drawLine(ink, pts[i], pts[(i + 1) % 6], stroke)
    for (i in listOf(1, 3, 5)) drawLine(ink, c, pts[i], stroke)
}

/** Dreamcast's mark, drawn: a swirl of three arcs closing in. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSwirlMark(c: Offset, s: Float, ink: Color) {
    val stroke = androidx.compose.ui.graphics.drawscope.Stroke(s * 0.1f)
    for (i in 0 until 3) {
        val r = s / 2f * (1f - i * 0.28f)
        drawArc(ink, -60f + i * 40f, 260f, false, Offset(c.x - r, c.y - r), Size(r * 2f, r * 2f), style = stroke)
    }
}

/** Sega's grid behind a Genesis band: fine lines across it. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawGrid(area: Size) {
    val line = Color(0xFFD0122A).copy(alpha = 0.55f)
    val step = maxOf(area.height / 5f, 2f)
    var y = step
    while (y < area.height) { drawLine(line, Offset(0f, y), Offset(area.width, y), 1f); y += step }
    var x = step
    while (x < area.width) { drawLine(line.copy(alpha = 0.3f), Offset(x, 0f), Offset(x, area.height), 1f); x += step }
}

/** Draws [block] turned a quarter clockwise about the top left, in a space as wide as this is tall. */
private inline fun androidx.compose.ui.graphics.drawscope.DrawScope.rotate90(block: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit) {
    drawContext.transform.rotate(90f, Offset.Zero)
    block()
    drawContext.transform.rotate(-90f, Offset.Zero)
}

/**
 * What the Shelf and Case views of [folder] show: its own things on the first shelf, then a shelf for each folder
 * inside it with everything in that (folders deeper down included). Widgets stay on the grid; anything shown twice
 * (folders hold shortcuts) shows once, where it comes first.
 */
internal fun shelfSections(folder: io.github.zahakj.tandem.data.HomeFolder, catalog: GridCatalog): List<ShelfSection> {
    val seen = HashSet<String>()
    fun items(keys: List<String>) = keys.mapNotNull { k -> catalog.item(k)?.takeIf { it.kind != TileKind.Widget && it.kind != TileKind.Folder && seen.add(k) } }
    fun everything(f: io.github.zahakj.tandem.data.HomeFolder, depth: Int): List<String> {
        val own = SystemFolders.contents(f, catalog)
        if (depth > 4) return own.filter { !it.startsWith("folder:") }
        return own.flatMap { k ->
            if (k.startsWith("folder:")) io.github.zahakj.tandem.data.IconGridStore.folder(k.removePrefix("folder:"))?.let { everything(it, depth + 1) }.orEmpty() else listOf(k)
        }
    }
    val contents = SystemFolders.contents(folder, catalog)
    val out = ArrayList<ShelfSection>()
    items(contents.filter { !it.startsWith("folder:") }).takeIf { it.isNotEmpty() }?.let {
        out += ShelfSection("own", folder.name, folderColor(folder.color), it, folder.id)
    }
    for (k in contents.filter { it.startsWith("folder:") }) {
        val sub = io.github.zahakj.tandem.data.IconGridStore.folder(k.removePrefix("folder:")) ?: continue
        val list = items(everything(sub, 1)).takeIf { it.isNotEmpty() } ?: continue
        out += ShelfSection("f:${sub.id}", sub.name, catalog.item(k)?.color ?: folderColor(sub.color), list, sub.id)
    }
    return out
}

/** The Carousel view of a folder: everything in it (folders inside included) as one coverflow, its name under it. */
@Composable
fun FolderCarouselView(route: String, sections: List<ShelfSection>, onOpen: (GridItem) -> Unit) {
    val items = remember(sections) { sections.flatMap { it.items } }
    val flow = remember(items) {
        items.map { i -> FlowItem(i.key, i.title, listOf(i.type, i.detail).filter { it.isNotEmpty() }.joinToString(" · "), i.color, i.selected, i.game, i.games) { onOpen(i) } }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val h = (maxHeight * 0.66f).coerceAtMost(330.dp)
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Coverflow(route, flow, h * 0.74f, h, Modifier.fillMaxWidth().weight(1f))
            val sel = io.github.zahakj.tandem.Selection.current
            val shown = items.firstOrNull { it.selected == sel } ?: items.firstOrNull { it.key == FocusMemory.get(route) } ?: items.firstOrNull()
            if (shown != null) {
                val status = shown.game?.meta?.playStatus
                FlowCaption(shown.title, listOfNotNull(shown.game?.platform?.name ?: shown.type, status?.chip, shown.game?.meta?.let { playProgress(it) }).joinToString("  ·  "), status?.color ?: shown.color)
            }
            androidx.compose.foundation.layout.Spacer(Modifier.height(18.dp))
        }
    }
}
