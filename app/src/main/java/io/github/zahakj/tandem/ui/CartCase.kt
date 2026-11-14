package io.github.zahakj.tandem.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import io.github.zahakj.tandem.Game
import io.github.zahakj.tandem.Libraries
import io.github.zahakj.tandem.data.playStatus

/*
 * The Case view of a folder: a carrying case lined with foam, each game sitting in its own cut-out as the real thing
 * for its system (a Switch card, a Game Boy cartridge, an N64 cartridge, a disc in a clear sleeve…) with its cover
 * as the label. The highlighted one lifts out of the foam and catches the light. A folder inside gets a strip of
 * label tape and its own rows.
 */

/** The shapes games come in. [aspect] is width over height; [label] is the label's place, as fractions in from each side. */
enum class CartKind(val aspect: Float, val body: Long, val label: FloatArray) {
    SwitchCard(0.72f, 0xFF3A3B40, floatArrayOf(0.1f, 0.1f, 0.1f, 0.1f)),
    DsCard(0.95f, 0xFF4B4C52, floatArrayOf(0.1f, 0.09f, 0.1f, 0.3f)),
    ThreeDsCard(0.95f, 0xFF3D3E44, floatArrayOf(0.1f, 0.09f, 0.15f, 0.3f)),
    GameBoy(0.86f, 0xFFA7A8AE, floatArrayOf(0.12f, 0.3f, 0.12f, 0.14f)),
    GameBoyColor(0.86f, 0xFF4F5057, floatArrayOf(0.12f, 0.3f, 0.12f, 0.14f)),
    Advance(1.6f, 0xFF5A5B63, floatArrayOf(0.15f, 0.12f, 0.15f, 0.3f)),
    N64(1.5f, 0xFF8E9197, floatArrayOf(0.13f, 0.42f, 0.13f, 0.1f)),
    Snes(1.5f, 0xFFB9BAC0, floatArrayOf(0.2f, 0.1f, 0.2f, 0.36f)),
    Nes(0.9f, 0xFF9DA0A6, floatArrayOf(0.12f, 0.1f, 0.12f, 0.36f)),
    Mega(1.25f, 0xFF1E1F23, floatArrayOf(0.12f, 0.1f, 0.12f, 0.26f)),
    Disc(1f, 0xFFDDE3EA, floatArrayOf(0f, 0f, 0f, 0f)),
    Card(0.78f, 0xFF35363C, floatArrayOf(0.1f, 0.1f, 0.1f, 0.22f)),
    ;

    companion object {
        fun of(platformId: String): CartKind = when (platformId) {
            "switch" -> SwitchCard
            "nds" -> DsCard
            "n3ds" -> ThreeDsCard
            "gb", "pokemini", "megaduck", "supervision" -> GameBoy
            "gbc", "ngp", "ngpc", "wonderswan", "wonderswancolor", "gamegear", "atarilynx" -> GameBoyColor
            "gba" -> Advance
            "n64" -> N64
            "snes" -> Snes
            "nes", "fds", "atari2600", "atari7800", "atari5200", "colecovision", "intellivision", "mastersystem", "sg1000" -> Nes
            "genesis", "sega32x", "pcengine", "supergrafx", "atarijaguar", "neogeo" -> Mega
            "psx", "ps2", "ps3", "psp", "gc", "wii", "wiiu", "dreamcast", "saturn", "segacd", "pcenginecd", "neogeocd", "pcfx", "3do",
            "cdimono1", "xbox", "xbox360", "naomi" -> Disc
            else -> Card
        }
    }
}

@Composable
fun CartCaseView(
    route: String,
    sections: List<ShelfSection>,
    target: String?,
    onFocus: (GridItem) -> Unit,
    onOpen: (GridItem) -> Unit,
    onLong: (GridItem) -> Unit,
) {
    val grid = rememberLazyGridState()
    // Every cell's index in the grid (section tapes take a row each), for keeping the highlighted one in view.
    val index = remember(sections) {
        val m = HashMap<String, Int>()
        var i = 0
        for (s in sections) { if (sections.size > 1) i++; for (it in s.items) m[it.key] = i++ }
        m
    }
    KeepInView(grid, target?.let { index[it] } ?: -1)
    BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 4.dp).caseShell()) {
        // Seven tall cartridges across, or five wide ones, sized for the shape most of the folder's games come in.
        val wide = remember(sections) {
            val kinds = sections.flatMap { s -> s.items.mapNotNull { it.game?.let { g -> CartKind.of(g.platform.id) } } }
            kinds.count { it.aspect > 1.1f } * 2 > kinds.size
        }
        val cell = ((maxWidth - 44.dp - 14.dp * (if (wide) 4 else 6)) / (if (wide) 5 else 7)).coerceAtLeast(80.dp)
        LazyVerticalGrid(
            GridCells.Adaptive(cell),
            Modifier.fillMaxSize().fadingEdges(grid),
            state = grid,
            contentPadding = PaddingValues(horizontal = 22.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            for (s in sections) {
                if (sections.size > 1) item(key = "tape:" + s.key, span = { GridItemSpan(maxLineSpan) }) { Tape(s.title, s.items.size) }
                items(s.items, key = { it.key }) { item -> Slot(route, item, item.key == target, if (wide) 1.35f else 0.82f, onFocus, onOpen, onLong) }
            }
        }
    }
}

/** The case: a hard shell rim around grey egg-crate foam, drawn once into a picture per size and then just shown. */
private fun Modifier.caseShell(): Modifier = clip(RoundedCornerShape(26.dp)).drawWithCache {
    val w = size.width.toInt().coerceAtLeast(1)
    val h = size.height.toInt().coerceAtLeast(1)
    val picture = androidx.compose.ui.graphics.ImageBitmap(w, h)
    androidx.compose.ui.graphics.drawscope.CanvasDrawScope().draw(this, layoutDirection, androidx.compose.ui.graphics.Canvas(picture), size) {
        val rim = 7.dp.toPx()
        val foam = Color(0xFF2B2C31)
        val dimple = Brush.radialGradient(listOf(Color.White.copy(alpha = 0.05f), Color.Black.copy(alpha = 0.22f)), radius = 7.dp.toPx())
        val step = 16.dp.toPx()
        drawRect(Brush.verticalGradient(listOf(Color(0xFF3C3E46), Color(0xFF24252B))))
        drawRoundRect(foam, Offset(rim, rim), Size(size.width - rim * 2, size.height - rim * 2), CornerRadius(20.dp.toPx()))
        var y = rim + step / 2
        var odd = false
        while (y < size.height - rim) {
            var x = rim + (if (odd) step else step / 2)
            while (x < size.width - rim) {
                translateTo(x, y) { drawCircle(dimple, 6.dp.toPx(), Offset.Zero) }
                x += step
            }
            y += step * 0.87f
            odd = !odd
        }
        // The rim's light edge and the foam's shade under it.
        drawRoundRect(Color.White.copy(alpha = 0.12f), Offset(1f, 1f), Size(size.width - 2f, size.height - 2f), CornerRadius(26.dp.toPx()), style = Stroke(1.5f))
        drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.4f), Color.Transparent), startY = rim, endY = rim + 22.dp.toPx()))
    }
    onDrawBehind { drawImage(picture) }
}

private inline fun DrawScope.translateTo(x: Float, y: Float, block: DrawScope.() -> Unit) {
    drawContext.transform.translate(x, y); block(); drawContext.transform.translate(-x, -y)
}

/** A strip of label-maker tape naming a folder inside, stuck on a little crooked. */
@Composable
private fun Tape(title: String, count: Int) {
    Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.rotate(-1.5f).clip(RoundedCornerShape(2.dp)).background(Color(0xFFF1EBDD)).padding(horizontal = 10.dp, vertical = 3.dp),
        ) {
            Text(
                title.uppercase() + "  ·  $count", style = MaterialTheme.typography.labelLarge.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
                color = Color(0xFF1C1C1C), maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** A cut-out in the foam with a game sitting in it; anything else sits in its cut-out as a small tile. */
@Composable
private fun Slot(route: String, item: GridItem, wanted: Boolean, aspect: Float, onFocus: (GridItem) -> Unit, onOpen: (GridItem) -> Unit, onLong: (GridItem) -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val press = remember { TilePress() }
    val liftState = animateFloatAsState(if (focused) 1f else 0f, Motion.soft(), label = "cart-lift")
    // A glint that sweeps across the label once each time it is picked up.
    val glint = remember { Animatable(0f) }
    LaunchedEffect(focused) { if (focused) { glint.snapTo(0f); glint.animateTo(1f, tween(650)) } }
    val game = item.game
    val kind = game?.let { CartKind.of(it.platform.id) }
    Box(
        Modifier.fillMaxWidth().aspectRatio(aspect)
            .takesFocus(route, item.key, wanted)
            .liftOrigin(0.1f)
            .touchEntry()
            .onFocusChanged { if (it.isFocused) { onFocus(item); Sounds.move() } else press.pressed = false }
            .focusable(interactionSource = interaction)
            .onKeyEvent { e -> tileKey(e, press, { onOpen(item) }, { onLong(item) }) }
            .pointerInput(item.key) { detectTapGestures(onTap = { onOpen(item); Sounds.select() }) },
        contentAlignment = Alignment.Center,
    ) {
        // Sits in a cut-out of its own shape, a little bigger than it and shaded deep inside.
        Box(Modifier.fillMaxSize(0.84f), contentAlignment = Alignment.Center) {
            Box(Modifier.aspectRatio(kind?.aspect ?: 1f)) {
                Box(
                    Modifier.matchParentSize().graphicsLayer { scaleX = 1.07f; scaleY = 1.05f }.clip(RoundedCornerShape(9.dp))
                        .background(Brush.verticalGradient(listOf(Color(0xFF0E0E11), Color(0xFF1A1B1F), Color(0xFF23242A)))),
                )
                val sit = Modifier.matchParentSize().graphicsLayer {
                    val lift = liftState.value
                    translationY = -16.dp.toPx() * lift
                    scaleX = 1f + 0.14f * lift; scaleY = scaleX
                    rotationX = 8f * lift
                    shadowElevation = (2f + 22f * lift) * density
                    shape = RoundedCornerShape(8.dp)
                }
                if (game == null || kind == null) Box(sit.clip(RoundedCornerShape(12.dp))) { TileArtView(item.art, 96.dp) }
                else Box(sit) { Cart(game, kind, { glint.value }, { liftState.value }) }
            }
        }
    }
}

/** A cartridge (or a disc in its sleeve) for [game], its cover on the label. */
@Composable
private fun Cart(game: Game, kind: CartKind, glint: () -> Float, lift: () -> Float) {
    val ctx = LocalContext.current
    val box = remember(game.key, game.meta.box) { Libraries.of(game)?.box(game) }
    val tint = CoverColors.of(ctx, game)
    val status = game.meta.playStatus
    Box(Modifier.aspectRatio(kind.aspect)) {
        if (kind == CartKind.Disc) {
            DiscInSleeve(box, tint)
        } else {
            val body = Color(kind.body)
            Canvas(Modifier.fillMaxSize()) { cartBody(kind, body) }
            // The label, inset where the real one sits, its corners rounded like a sticker.
            val l = kind.label
            Box(Modifier.fillMaxSize().insetBy(l[0], l[1], l[2], l[3]).clip(RoundedCornerShape(4.dp)).background(tint ?: Palette.CardHigh)) {
                if (box != null) AsyncImage(box, game.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                Canvas(Modifier.fillMaxSize()) {
                    val g = glint()
                    if (g <= 0f || g >= 1f) return@Canvas
                    val x = size.width * (g * 1.8f - 0.4f)
                    drawRect(Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.45f), Color.Transparent), Offset(x - size.width * 0.3f, 0f), Offset(x, size.height)))
                }
            }
        }
        if (status != null) {
            Box(
                Modifier.align(Alignment.TopEnd).padding(2.dp).size(18.dp).graphicsLayer { alpha = 0.75f + 0.25f * lift() }.clip(CircleShape).background(status.color),
                contentAlignment = Alignment.Center,
            ) { Text(status.mark, style = MaterialTheme.typography.labelSmall, color = Palette.NightDeep, maxLines = 1) }
        }
    }
}

/** The plastic body of [kind], with the details that make each one recognisable. */
private fun DrawScope.cartBody(kind: CartKind, body: Color) {
    val w = size.width
    val h = size.height
    val r = minOf(w, h) * 0.07f
    val shade = Brush.verticalGradient(listOf(lerp(body, Color.White, 0.18f), body, lerp(body, Color.Black, 0.25f)))
    val dark = lerp(body, Color.Black, 0.35f)
    val outline = Path().apply {
        when (kind) {
            // Cards and Game Boy cartridges have one corner cut off.
            CartKind.SwitchCard, CartKind.GameBoy, CartKind.GameBoyColor -> {
                val cut = w * 0.16f
                moveTo(r, 0f); lineTo(w - cut, 0f); lineTo(w, cut); lineTo(w, h - r)
                quadraticTo(w, h, w - r, h); lineTo(r, h); quadraticTo(0f, h, 0f, h - r); lineTo(0f, r); quadraticTo(0f, 0f, r, 0f)
            }
            CartKind.DsCard, CartKind.ThreeDsCard -> {
                val cut = w * 0.12f
                moveTo(cut, 0f); lineTo(w - r, 0f); quadraticTo(w, 0f, w, r); lineTo(w, h - r)
                quadraticTo(w, h, w - r, h); lineTo(r, h); quadraticTo(0f, h, 0f, h - r); lineTo(0f, cut); close()
            }
            // A Super NES cartridge narrows into grips at both sides.
            CartKind.Snes -> {
                moveTo(r, 0f); lineTo(w - r, 0f); quadraticTo(w, 0f, w, r); lineTo(w, h * 0.3f)
                quadraticTo(w * 0.93f, h * 0.5f, w, h * 0.7f); lineTo(w, h - r); quadraticTo(w, h, w - r, h)
                lineTo(r, h); quadraticTo(0f, h, 0f, h - r); lineTo(0f, h * 0.7f); quadraticTo(w * 0.07f, h * 0.5f, 0f, h * 0.3f); lineTo(0f, r); quadraticTo(0f, 0f, r, 0f)
            }
            // An Advance cartridge has a rounded top.
            CartKind.Advance -> {
                moveTo(0f, h * 0.22f); quadraticTo(0f, 0f, w * 0.2f, 0f); lineTo(w * 0.8f, 0f); quadraticTo(w, 0f, w, h * 0.22f)
                lineTo(w, h - r); quadraticTo(w, h, w - r, h); lineTo(r, h); quadraticTo(0f, h, 0f, h - r); close()
            }
            else -> addRoundRect(RoundRect(Rect(0f, 0f, w, h), CornerRadius(r)))
        }
        close()
    }
    drawPath(outline, shade)
    drawPath(outline, Color.Black.copy(alpha = 0.35f), style = Stroke(1.5f))
    when (kind) {
        CartKind.GameBoy, CartKind.GameBoyColor -> {
            // Grip ridges along the top, and the arrow pointing in.
            for (i in 1..4) drawLine(dark, Offset(w * 0.1f, h * 0.04f * i + h * 0.02f), Offset(w * 0.72f, h * 0.04f * i + h * 0.02f), 1.5f)
            val a = Path().apply { moveTo(w * 0.42f, h * 0.22f); lineTo(w * 0.58f, h * 0.22f); lineTo(w * 0.5f, h * 0.27f); close() }
            drawPath(a, dark)
        }
        CartKind.N64 -> for (i in 0..9) {
            val x = w * (0.12f + 0.084f * i)
            drawLine(dark, Offset(x, h * 0.06f), Offset(x, h * 0.36f), 2f)
        }
        CartKind.Nes, CartKind.Mega -> for (i in 0..5) {
            val y = h * (0.7f + 0.045f * i)
            drawLine(dark, Offset(w * 0.15f, y), Offset(w * 0.85f, y), 1.5f)
        }
        CartKind.ThreeDsCard -> drawRoundRect(dark, Offset(w * 0.93f, h * 0.38f), Size(w * 0.07f, h * 0.18f), CornerRadius(2f))
        CartKind.Card -> {
            // The contacts along the bottom edge.
            val n = 8
            for (i in 0 until n) drawRect(Color(0xFFC9A44C).copy(alpha = 0.7f), Offset(w * (0.14f + 0.09f * i), h * 0.93f), Size(w * 0.05f, h * 0.04f))
        }
        else -> Unit
    }
}

/** A disc in a clear sleeve: the cover printed on the disc, with its hole, the sleeve's edge and a rainbow sheen. */
@Composable
private fun DiscInSleeve(box: java.io.File?, tint: Color?) {
    Box(
        Modifier.fillMaxSize().clip(RoundedCornerShape(6.dp)).background(Color.White.copy(alpha = 0.1f)),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.fillMaxSize(0.9f).clip(CircleShape).background(tint ?: Color(0xFFB8C2CF))) {
            if (box != null) AsyncImage(box, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Canvas(Modifier.fillMaxSize()) {
                val c = Offset(size.width / 2, size.height / 2)
                drawCircle(Brush.sweepGradient(listOf(Color(0x33FF6B6B), Color(0x3366E0FF), Color(0x33B26BFF), Color(0x33FFE066), Color(0x33FF6B6B)), c))
                drawCircle(Color(0xFFDDE3EA), size.minDimension * 0.17f, c)
                drawCircle(Color(0xFF2B2C31), size.minDimension * 0.07f, c)
                drawCircle(Color.Black.copy(alpha = 0.25f), size.minDimension * 0.17f, c, style = Stroke(1.5f))
            }
        }
        // The sleeve: a clear pocket with a lit edge.
        Canvas(Modifier.fillMaxSize()) {
            drawRoundRect(Color.White.copy(alpha = 0.35f), size = size, cornerRadius = CornerRadius(6.dp.toPx()), style = Stroke(1.5f))
            drawRect(Brush.linearGradient(listOf(Color.White.copy(alpha = 0.16f), Color.Transparent, Color.White.copy(alpha = 0.06f)), Offset.Zero, Offset(size.width, size.height)))
        }
    }
}


/** Insets the content by fractions of its own size from each side (a label sitting where the real one does). */
private fun Modifier.insetBy(l: Float, t: Float, r: Float, b: Float): Modifier = layout { m, c ->
    val w = c.maxWidth
    val h = c.maxHeight
    val left = (w * l).toInt(); val top = (h * t).toInt()
    val iw = (w - left - (w * r).toInt()).coerceAtLeast(0)
    val ih = (h - top - (h * b).toInt()).coerceAtLeast(0)
    val p = m.measure(androidx.compose.ui.unit.Constraints.fixed(iw, ih))
    layout(w, h) { p.place(left, top) }
}
