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
import io.github.zahakj.tandem.data.Store
import io.github.zahakj.tandem.data.playStatus
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * The Shelf view of a folder: its games as cases standing spine out on shelves, a shelf for the folder's own things
 * and one for each folder inside it (a system's genres). Each spine takes its cover's colour and wears the game's
 * logo turned on its side, its status mark at the foot. The highlighted one swings round to show its cover, and the
 * case has the cover's own proportions, so a Switch box stands tall and a Game Boy box lies wide, as the real ones
 * do. Settings › Home & layout › Shelf look picks the bookcase: warm wood, matte in the theme's colours, or open
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
 * Each game's logo for its spine, looked up off the main thread: finding it touches the storage, and a logo seen for
 * the first time is test-decoded to tell a cut-out from a picture. Done while a shelf composes, that held each new
 * shelf scrolling in for a tenth of a second or more. [warm] looks up a whole folder as it opens.
 */
object SpineLogos {
    /** A game's logo once looked up: the file, or none. */
    class Found(val file: File?)

    private val known = HashMap<String, androidx.compose.runtime.MutableState<Found?>>()
    private val main = android.os.Handler(android.os.Looper.getMainLooper())

    /** The game's logo, or null while it is still being looked up. A changed logo is looked up again. */
    fun of(g: Game): Found? {
        known[id(g)]?.let { return it.value }
        warm(listOf(g))
        return null
    }

    fun warm(games: List<Game>) {
        val todo = games.filter { known[id(it)] == null }.distinctBy { id(it) }
        if (todo.isEmpty()) return
        val states = todo.associate { id(it) to androidx.compose.runtime.mutableStateOf<Found?>(null) }
        known.putAll(states)
        inBackground({
            for (g in todo) {
                val found = Found(runCatching { Libraries.of(g)?.logo(g) }.getOrNull())
                main.post { states.getValue(id(g)).value = found }
            }
        })
    }

    private fun id(g: Game) = g.key + "\u0000" + g.meta.logo
}

/**
 * The case a system's games came in, for its shelf: the plastic's colour, and the band at the top of the spine.
 * White cases (3DS, Wii) have white spines with the title in black, as the real ones do; the rest keep a spine in
 * the cover's colour under their system's band. Cardboard boxes and systems without a case of their own have none.
 */
internal class CaseKind(val plastic: Color, val band: Color, val bandText: Color, val label: String, val white: Boolean = false) {
    companion object {
        private val white = Color(0xFFF3F3F0)
        private val black = Color(0xFF17181B)
        private val kinds = mapOf(
            "n3ds" to CaseKind(white, white, Color(0xFFD7182A), "3DS", white = true),
            "wii" to CaseKind(white, white, Color(0xFF8A9096), "Wii", white = true),
            "wiiu" to CaseKind(Color(0xFF1C8AC9), Color(0xFF1C8AC9), Color.White, "Wii U"),
            "switch" to CaseKind(Color(0xFFE60012), Color(0xFFE60012), Color.White, "SWITCH"),
            "gc" to CaseKind(black, black, Color.White, "GCN"),
            "ps2" to CaseKind(black, black, Color.White, "PS2"),
            "ps3" to CaseKind(Color(0xFF22252B), black, Color.White, "PS3"),
            "ps4" to CaseKind(Color(0xFF0B3B8C), Color(0xFF0B3B8C), Color.White, "PS4"),
            "ps5" to CaseKind(Color(0xFF0B3B8C), white, black, "PS5"),
            "xbox" to CaseKind(Color(0xFF1C7A2C), Color(0xFF1C7A2C), Color.White, "XBOX"),
            "xbox360" to CaseKind(Color(0xFF3FA535), Color(0xFF3FA535), Color.White, "360"),
        )

        fun of(system: String): CaseKind? = kinds[system]
    }
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
    // Every spine's logo and cover shape looked up in the background as the folder opens, ahead of the scroll.
    LaunchedEffect(sections) {
        val games = sections.flatMap { s -> s.items.mapNotNull { it.game } }
        SpineLogos.warm(games)
        CoverShapes.warm(ctx, games)
    }
    BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        // Two shelves to a screen, so it reads as a bookcase.
        val shelfH = (maxHeight / 2).coerceIn(170.dp, 280.dp)
        val plank = if (look == ShelfLooks.OPEN) 14.dp else 20.dp
        val side = if (look == ShelfLooks.OPEN) 0.dp else 12.dp
        val caseH = shelfH - plank - 24.dp
        val size = CaseSize(spine = caseH * 0.2f, coverMax = caseH * 1.5f, height = caseH)
        // As many spines as fit on a shelf with room left for a cover to swing open, a DS or 3DS box being wider
        // than tall (the widest, a Game Boy box, pushes the last spine along a little); the rest go on the next shelf.
        val perShelf = (((maxWidth - side * 2 - 36.dp) - (caseH * 1.15f - size.spine)) / (size.spine + 3.dp)).toInt().coerceAtLeast(1)
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
                    Standing(route, item, size, item.key == target, onFocus, onOpen, onLong, wrap)
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
    route: String, item: GridItem, size: CaseSize, wanted: Boolean,
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
        val box = size.height * 0.5f
        Box(
            base.size(box).graphicsLayer {
                val open = openState.value
                translationY = -10.dp.toPx() * open; scaleX = 1f + 0.08f * open; scaleY = scaleX
                shape = RoundedCornerShape(10.dp); clip = true; shadowElevation = (2f + 14f * open) * density.density
            },
        ) { TileArtView(item.art, box) }
        return
    }
    val spinePx = with(density) { size.spine.toPx() }
    // The cover's own proportions, as tall as the case: known once its picture's header has been read.
    val cover = size.height * CoverShapes.clamp(CoverShapes.of(ctx, game))
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
            ) { Spine(game, size.spine, size.height) }
            if (swinging) Box(
                Modifier.width(cover).fillMaxHeight().graphicsLayer {
                    val open = openState.value
                    transformOrigin = TransformOrigin(0f, 0.5f); rotationY = 90f - 82f * open; cameraDistance = 14f * this.density
                    shape = RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp); clip = true
                    shadowElevation = 18f * open * this.density
                    alpha = if (open < 0.02f) 0f else 1f
                },
            ) {
                val kind = CaseKind.of(game.platform.id)
                // In a plastic case the cover sits under its clear sleeve, the plastic showing round it.
                Box(Modifier.fillMaxSize().then(if (kind != null) Modifier.background(kind.plastic).padding(3.dp).clip(RoundedCornerShape(2.dp)) else Modifier)) {
                    GameArt(game)
                }
                // The case's plastic: a soft sheen across the cover, strongest at the hinge.
                Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.35f), Color.Transparent, Color.White.copy(alpha = 0.08f)), endX = coverPx)))
            }
        }
    }
}

/**
 * A case's spine: the cover's colour with a curved sheen, the system's band at the top, the logo (or the name) on its
 * side, its status at the foot. One box with its edges and its logo drawn, not composed, so a shelf of forty is light.
 */
@Composable
private fun Spine(game: Game, w: Dp, h: Dp) {
    val ctx = LocalContext.current
    val system = SystemColors.of(game.platform.id)
    val kind = CaseKind.of(game.platform.id)
    val white = kind?.white == true
    val body = if (white) kind!!.plastic else lerp(CoverColors.of(ctx, game) ?: lerp(system, Palette.NightDeep, 0.35f), Color.Black, 0.28f)
    // A white case's spine carries its title in print, not the logo (a white logo would vanish on it).
    val found = if (white) SpineLogos.Found(null) else SpineLogos.of(game)
    val logo = found?.file
    val status = game.meta.playStatus
    val shade = if (white) 0.18f else 0.45f
    Box(
        Modifier.fillMaxSize().clip(RoundedCornerShape(3.dp))
            .background(Brush.horizontalGradient(listOf(lerp(body, Color.Black, shade), body, lerp(body, Color.White, 0.18f), body, lerp(body, Color.Black, shade + 0.05f))))
            // Hairline edges, so neighbouring spines of the same colour still read as two.
            .drawBehind {
                drawRect(Color.Black.copy(alpha = 0.5f), size = Size(1.dp.toPx(), size.height))
                drawRect(Color.White.copy(alpha = 0.2f), size = Size(size.width, 1.dp.toPx()))
            },
    ) {
        // The system's band across the top, like a store shelf of the real thing: the case's own where it has one.
        Box(
            Modifier.fillMaxWidth().height(h * 0.1f).background(kind?.band ?: system)
                .then(if (white) Modifier.drawBehind { drawRect(Color.Black.copy(alpha = 0.12f), Offset(0f, size.height - 1.dp.toPx()), Size(size.width, 1.dp.toPx())) } else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                kind?.label ?: game.platform.shortName.uppercase().take(6), style = TextStyle(fontSize = (w.value * 0.2f).coerceIn(6f, 10f).sp, fontWeight = FontWeight.Black),
                color = kind?.bandText ?: Color.White, maxLines = 1,
            )
        }
        val along = h * 0.66f
        Box(Modifier.align(Alignment.Center).padding(top = h * 0.04f), contentAlignment = Alignment.Center) {
            if (logo != null) {
                // Turned a quarter, reading from the top down, as spines do: drawn into the spine, no layer of its own.
                val painter = rememberAsyncImagePainter(rememberSizedRequest(logo, tilePx(along)))
                Canvas(Modifier.requiredSize(w * 0.8f, along)) {
                    val intrinsic = painter.intrinsicSize
                    if (intrinsic.isSpecified && intrinsic.width > 0f && intrinsic.height > 0f) {
                        // The logo's room once turned: as long as the spine, as wide as the spine lets it be.
                        val roomW = size.height; val roomH = size.width
                        val scale = minOf(roomW / intrinsic.width, roomH / intrinsic.height)
                        val dw = intrinsic.width * scale; val dh = intrinsic.height * scale
                        rotate(90f) {
                            translate((size.width - dw) / 2f, (size.height - dh) / 2f) { with(painter) { draw(Size(dw, dh)) } }
                        }
                    }
                }
            } else if (found != null) {
                val measurer = rememberTextMeasurer()
                Canvas(Modifier.requiredSize(w, along)) {
                    val style = TextStyle(color = if (white) Color(0xFF1B1C1F) else Color.White, fontSize = (w.value * 0.34f).coerceIn(8f, 15f).sp, fontWeight = FontWeight.ExtraBold)
                    val t = measurer.measure(game.title, style, overflow = TextOverflow.Ellipsis, maxLines = 1, constraints = Constraints(maxWidth = size.height.roundToInt()))
                    rotate90 { drawText(t, topLeft = Offset((size.height - t.size.width) / 2f, -(size.width + t.size.height) / 2f)) }
                }
            }
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
