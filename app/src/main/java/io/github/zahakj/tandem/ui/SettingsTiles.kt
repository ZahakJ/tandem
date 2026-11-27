package io.github.zahakj.tandem.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.zahakj.tandem.Command
import io.github.zahakj.tandem.Pad
import io.github.zahakj.tandem.Updates
import io.github.zahakj.tandem.data.ControllerStore
import io.github.zahakj.tandem.data.Store
import io.github.zahakj.tandem.data.SystemPrefs
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/*
 * The Settings home's tiles: eight of one make (the seven pages and Search), in one colour story. Each is a glass
 * tile in the theme's own card colour with the accent only in its glyph and glow, so every theme and theme pack
 * gives a set that belongs to it. A tile is a gently landscape box laid out the same way every time: a header with
 * the page's glyph on a small medallion (filled with the accent while highlighted) and its name beside it, then a
 * sketch box of one size with a tiny live drawing of what is set inside, centred. The sketches are
 * drawings, never sentences: the theme's colours, the home layout, the two screens, an equaliser, the A and B
 * buttons, the game count, the battery.
 *
 * Highlighted, a tile gets FocusCard's ring, lift and glow in the accent (the user's highlight style), and sways
 * gently, its medallion drifting a little against the sketch (parallax). Reduce motion keeps everything still.
 */

/** One tile of the Settings home. [hint] draws the live sketch, given how highlighted the tile is (0 to 1). */
@Composable
internal fun SettingsHomeTile(
    name: String,
    nameStyle: TextStyle,
    glyph: Glyph,
    modifier: Modifier,
    shape: Shape,
    onFocus: () -> Unit,
    onClick: () -> Unit,
    hint: @Composable () -> Unit,
) {
    FocusCard(modifier, glow = Palette.Accent, shape = shape, onFocus = onFocus, onClick = onClick) { focused ->
        val t = Themes.live
        val accent = Palette.Accent
        val ink = Palette.readable(accent)
        val still = showcaseStill()
        val f = animateFloatAsState(if (focused) 1f else 0f, tween(240), label = "tile-focus")
        // The sway runs on the shared ambient clock, only on the highlighted tile, and fades with the highlight.
        val swaying = rememberAmbient(focused && !still)
        val px = with(LocalDensity.current) { 1.dp.toPx() }
        fun sway(): Float = if (!swaying) 0f else sin(Ambient.loop(5200, lively = true) * 2f * PI.toFloat()) * f.value
        val dark = t.dark
        // Glass: lit from the top, a little deeper at the foot, a pool of the accent behind the sketch and a fine
        // highlight along the top edge. Drawn over the theme's card surface (its material: solid, frosted, outlined).
        Box(
            Modifier.fillMaxSize().drawBehind {
                val w = size.width
                val h = size.height
                drawRect(
                    Brush.verticalGradient(
                        0f to Color.White.copy(alpha = if (dark) 0.075f else 0.32f),
                        0.45f to Color.Transparent,
                        1f to Color.Black.copy(alpha = if (dark) 0.16f else 0.05f),
                    ),
                )
                val glow = 0.10f + 0.20f * f.value
                val c = Offset(w / 2f, h * 0.66f)
                val reach = minOf(w * 0.6f, h)
                drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = glow), Color.Transparent), c, reach), reach, c)
                val r = minOf(t.corner.dp.toPx(), w / 3f)
                drawLine(
                    Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = if (dark) 0.22f else 0.7f), Color.Transparent), r * 0.5f, w - r * 0.5f),
                    Offset(r * 0.5f, 0.8f * px), Offset(w - r * 0.5f, 0.8f * px), 1.2f * px,
                )
            },
        )
        Column(Modifier.fillMaxSize().padding(12.dp)) {
            // The header: the medallion and the page's name on one line, the same height on every tile, so names
            // line up across a row whether they take one line or two.
            Row(Modifier.fillMaxWidth().height(TileHeader), verticalAlignment = Alignment.CenterVertically) {
                // The medallion: a soft accent disc at rest, filled with the accent while highlighted. It drifts a touch
                // against the sketch's sway, and rises a little.
                Box(
                    Modifier.size(30.dp)
                        .graphicsLayer {
                            val s = sway()
                            translationX = -s * 3f * px
                            translationY = -f.value * 2f * px
                        }
                        .drawBehind {
                            val v = f.value
                            val rad = size.minDimension / 2f
                            drawCircle(accent.copy(alpha = 0.16f * (1f - v)), rad)
                            if (v > 0.01f) drawCircle(
                                Brush.linearGradient(listOf(lerp(accent, Color.White, 0.22f), accent, lerp(accent, t.background, 0.25f)), Offset.Zero, Offset(size.width, size.height)),
                                rad, alpha = v,
                            )
                            drawCircle(lerp(accent, Color.White, 0.4f).copy(alpha = 0.35f + 0.25f * v), rad - 0.5f * px, style = Stroke(1f * px))
                        },
                    contentAlignment = Alignment.Center,
                ) { GlyphIcon(glyph, lerp(ink, t.onAccent, f.value), Modifier.padding(7.dp)) }
                Spacer(Modifier.width(8.dp))
                TileName(name, nameStyle, Modifier.weight(1f).fillMaxHeight())
            }
            // The sketch: one box of one size under every header, the drawing centred in it.
            Box(
                Modifier.weight(1f).fillMaxWidth().padding(top = 6.dp)
                    .graphicsLayer {
                        val s = sway()
                        rotationY = s * 7f
                        translationX = s * 2f * px
                        cameraDistance = 14f * density
                        alpha = 0.78f + 0.22f * f.value
                    },
                contentAlignment = Alignment.Center,
            ) { Box(Modifier.heightIn(max = SketchHeight).fillMaxHeight(), contentAlignment = Alignment.Center) { hint() } }
        }
    }
}

private val TileHeader = 36.dp
private val SketchHeight = 44.dp

/** The room for a name in a one-column tile: the tile's width less its padding, the medallion and the gap after it. */
internal val TileNameInset = 12.dp * 2 + 30.dp + 8.dp

/**
 * The one size of every tile's name: titleMedium, shrunk (to 84 % at most) until every name that can be one line
 * fits [width] on one line. A name too long even then (French, Arabic) takes two lines in that size.
 */
@Composable
internal fun tileNameStyle(names: List<String>, width: Dp): TextStyle {
    val base = MaterialTheme.typography.titleMedium
    val measurer = rememberTextMeasurer()
    val room = with(LocalDensity.current) { width.toPx() }
    return remember(names, base, room) {
        val scale = names.map { room / measurer.measure(it, base, maxLines = 1, softWrap = false).size.width }
            .filter { it >= 0.84f }.minOrNull()?.coerceAtMost(1f) ?: 1f
        base.copy(fontSize = base.fontSize * scale, lineHeight = base.fontSize * scale * 1.1f)
    }
}

/** A tile's name in the set's one [style]: one line when it fits, else two (shrinking only if two can't hold it). */
@Composable
private fun TileName(name: String, style: TextStyle, modifier: Modifier) {
    val measurer = rememberTextMeasurer()
    IntrinsicText(name, style, 2, modifier) { BoxWithConstraints(contentAlignment = Alignment.CenterStart) {
        val maxW = constraints.maxWidth
        val oneLine = remember(name, style, maxW) {
            maxW == Constraints.Infinity || measurer.measure(name, style, maxLines = 1, softWrap = false).size.width <= maxW
        }
        if (oneLine) FitText(name, style, minScale = 0.95f)
        else ShrinkText(name, style, maxLines = 2, minScale = 0.8f)
    } }
}

/** The live sketch for page [id] (or "search"). */
@Composable
internal fun SettingsTileHint(id: String) {
    when (id) {
        "look" -> ThemeSwatchHint()
        "home" -> LayoutHint()
        "screens" -> ScreensHint()
        "sound" -> EqualizerHint()
        "controls" -> FaceButtonsHint()
        "library" -> GameCountHint()
        "play" -> BatteryHint()
        else -> SearchHint()
    }
}

/** A thin frame for the sketches drawn as little screens. */
@Composable
private fun Modifier.miniScreen(): Modifier {
    val shape = RoundedCornerShape(6.dp)
    return clip(shape).border(1.dp, Palette.Text.copy(alpha = 0.16f), shape)
}

/** Look & feel: a swatch of the live theme, its background, a card and the accent. */
@Composable
private fun ThemeSwatchHint() {
    val t = Themes.live
    Canvas(Modifier.fillMaxHeight().aspectRatio(1.6f).miniScreen().mirroredInRtl()) {
        val w = size.width
        val h = size.height
        val u = h / 40f
        drawRect(Brush.verticalGradient(listOf(t.background, t.backgroundDeep)))
        // A card with two lines of text.
        val cr = CornerRadius(minOf(t.corner * 0.3f, 6f) * u)
        drawRoundRect(t.cardHigh, Offset(5 * u, 6 * u), Size(w * 0.48f, h - 12 * u), cr)
        drawRoundRect(t.text, Offset(9 * u, 11 * u), Size(w * 0.3f, 3.5f * u), CornerRadius(2 * u))
        drawRoundRect(t.muted, Offset(9 * u, 18 * u), Size(w * 0.2f, 3f * u), CornerRadius(2 * u))
        drawRoundRect(t.accent, Offset(9 * u, h - 14 * u), Size(w * 0.22f, 5f * u), CornerRadius(2.5f * u))
        // The accent and the theme's other colours, as paint dots.
        val x = w * 0.58f + 6 * u
        drawCircle(t.accent, 7.5f * u, Offset(x + 6 * u, h * 0.36f))
        val dots = t.accents.filter { it != t.accent }.take(3)
        dots.forEachIndexed { i, c -> drawCircle(c, 3.2f * u, Offset(x + i * 7.5f * u, h * 0.74f)) }
    }
}

/** Home & layout: the home layout in use, as the layout gallery sketches it. */
@Composable
private fun LayoutHint() {
    val layout = Store.settings.look.homeLayout
    LayoutPreview(layout, Modifier.fillMaxHeight().aspectRatio(16f / 9f).miniScreen(), rounded = false, sketch = true)
}

/**
 * Two screens: the Thor's two screens, the one with the games lit in the accent (the top one, or the bottom one when
 * they're swapped); with one screen the bottom one is only an outline.
 */
@Composable
private fun ScreensHint() {
    val t = Themes.live
    val accent = Palette.Accent
    val look = Store.settings.look
    val single = SystemPrefs.data.singleScreen
    val swapped = look.swapScreens && !single
    Canvas(Modifier.fillMaxHeight().aspectRatio(1.15f)) {
        val h = size.height
        val w = size.width
        val u = h / 44f
        val gap = 3 * u
        val topH = (h - gap) * 0.5f
        val topW = minOf(w, topH * 16f / 9f)
        val botH = h - gap - topH
        val botW = botH * 1.15f
        val r = CornerRadius(3.5f * u)
        val tl = Offset((w - topW) / 2f, 0f)
        val bl = Offset((w - botW) / 2f, topH + gap)
        fun lit(o: Offset, s: Size) {
            drawRoundRect(Brush.linearGradient(listOf(lerp(accent, Color.White, 0.2f), lerp(accent, t.background, 0.3f)), o, Offset(o.x + s.width, o.y + s.height)), o, s, r)
            // Three game tiles on the lit screen.
            val tw = s.width * 0.2f
            for (i in 0 until 3) drawRoundRect(t.onAccent.copy(alpha = 0.55f), Offset(o.x + s.width * (0.14f + i * 0.26f), o.y + s.height * 0.32f), Size(tw, s.height * 0.36f), CornerRadius(1.5f * u))
        }
        fun plain(o: Offset, s: Size) {
            drawRoundRect(t.cardHigh, o, s, r)
            drawRoundRect(t.text.copy(alpha = 0.55f), Offset(o.x + s.width * 0.18f, o.y + s.height * 0.3f), Size(s.width * 0.5f, 2.6f * u), CornerRadius(1.3f * u))
            drawRoundRect(t.muted.copy(alpha = 0.6f), Offset(o.x + s.width * 0.18f, o.y + s.height * 0.55f), Size(s.width * 0.34f, 2.2f * u), CornerRadius(1.1f * u))
        }
        val topS = Size(topW, topH)
        val botS = Size(botW, botH)
        if (swapped) plain(tl, topS) else lit(tl, topS)
        when {
            single -> drawRoundRect(
                t.muted.copy(alpha = 0.6f), bl, botS, r,
                style = Stroke(1.2f * u, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f * u, 2.5f * u))),
            )
            swapped -> lit(bl, botS)
            else -> plain(bl, botS)
        }
    }
}

/** Sound & music: an equaliser, dancing while sounds or music are on, flat and dim when both are off. */
@Composable
private fun EqualizerHint() {
    val look = Store.settings.look
    val on = look.sounds || look.music
    val accent = Palette.Accent
    val muted = Palette.Muted
    val moving = rememberAmbient(on && !showcaseStill() && layerActive())
    Canvas(Modifier.fillMaxHeight().aspectRatio(1.5f)) {
        val n = 5
        val h = size.height
        val bw = size.width / (n * 1.7f)
        val gap = (size.width - bw * n) / (n - 1)
        val rest = floatArrayOf(0.45f, 0.8f, 0.6f, 0.95f, 0.5f)
        for (i in 0 until n) {
            val level = when {
                !on -> 0.12f
                moving -> 0.22f + 0.78f * abs(sin((Ambient.loop(1400 + i * 170, i * 0.23f, lively = true)) * PI.toFloat()))
                else -> rest[i]
            }
            val bh = (h * level).coerceAtLeast(bw)
            val x = i * (bw + gap)
            drawRoundRect(
                if (on) Brush.verticalGradient(listOf(lerp(accent, Color.White, 0.25f), accent), h - bh, h) else Brush.verticalGradient(listOf(muted, muted)),
                Offset(x, h - bh), Size(bw, bh), CornerRadius(bw / 2f), alpha = if (on) 1f else 0.5f,
            )
        }
    }
}

/**
 * Controls: the four face buttons where they sit on the pad (always as on the device, never mirrored), the one that
 * selects filled with the accent and the one that goes back ringed, each with its letter in the chosen prompt style.
 */
@Composable
private fun FaceButtonsHint() {
    val look = Store.settings.look
    val open = ControllerStore.binding(Command.Open)?.takeIf { !it.hold }?.single ?: Pad.A
    val back = ControllerStore.binding(Command.Back)?.takeIf { !it.hold }?.single ?: Pad.B
    val accent = Palette.Accent
    val text = Palette.Text
    Box(Modifier.fillMaxHeight().aspectRatio(1f)) {
        // Android's layout: A at the bottom, B on the right, X on the left, Y on top.
        listOf(Pad.Y, Pad.A, Pad.X, Pad.B).forEach { pad ->
            val align = when (pad) {
                Pad.Y -> Alignment.TopCenter
                Pad.A -> Alignment.BottomCenter
                Pad.X -> AbsoluteAlignment.CenterLeft
                else -> AbsoluteAlignment.CenterRight
            }
            val role = when (pad) { open -> 1; back -> 2; else -> 0 }
            Box(
                Modifier.align(align).fillMaxHeight(0.36f).aspectRatio(1f).clip(CircleShape)
                    .drawBehind {
                        when (role) {
                            1 -> drawCircle(accent)
                            2 -> drawCircle(text.copy(alpha = 0.7f), size.minDimension / 2f - 0.75.dp.toPx(), style = Stroke(1.5.dp.toPx()))
                            else -> drawCircle(text.copy(alpha = 0.16f))
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (role != 0) Text(
                    Prompts.physical(pad.name, look.prompts).glyph,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, lineHeight = 9.sp, fontWeight = FontWeight.ExtraBold),
                    color = if (role == 1) Palette.OnAccent else text, maxLines = 1,
                )
            }
        }
    }
}

/** Library & art: a fanned stack of game cards and how many games there are. */
@Composable
private fun GameCountHint() {
    val games by remember { derivedStateOf { io.github.zahakj.tandem.Collections.visibleGames().size } }
    val t = Themes.live
    val accent = Palette.Accent
    Row(Modifier.fillMaxHeight(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        Canvas(Modifier.fillMaxHeight(0.8f).aspectRatio(0.9f).mirroredInRtl()) {
            val w = size.width
            val h = size.height
            val cw = w * 0.56f
            val r = CornerRadius(w * 0.08f)
            for (i in 0 until 3) {
                val x = i * (w - cw) / 2f
                val y = (2 - i) * h * 0.06f
                val top = i == 2
                drawRoundRect(if (top) accent else lerp(t.cardHigh, accent, 0.25f + i * 0.15f), Offset(x, y), Size(cw, h - y), r)
                drawRoundRect(t.background.copy(alpha = 0.35f), Offset(x, y), Size(cw, h - y), r, style = Stroke(1.dp.toPx()))
            }
        }
        Spacer(Modifier.width(6.dp))
        // Western digits in every language (Arabic too), as everywhere in Tandem.
        Text(
            games.toString(),
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
            color = Palette.readable(accent), maxLines = 1,
        )
    }
}

/** Play & system: the battery as it is now, and a dot when an update is waiting. */
@Composable
private fun BatteryHint() {
    val ctx = LocalContext.current
    val b = rememberPolled(60_000) { SystemControls.battery(ctx) }
    val level = (b?.first ?: -1).coerceAtMost(100)
    val charging = b?.second == true
    val update = Updates.found != null
    val t = Themes.live
    val accent = Palette.Accent
    val low = Palette.readable(Palette.Peach)
    Canvas(Modifier.fillMaxHeight(0.8f).aspectRatio(1.9f).mirroredInRtl()) {
        val h = size.height
        val w = size.width
        val u = h / 24f
        val nub = 3 * u
        val bodyW = w * 0.8f - nub
        val stroke = 2f * u
        val r = CornerRadius(5 * u)
        val o = Offset(stroke / 2f, stroke / 2f)
        drawRoundRect(t.text.copy(alpha = 0.7f), o, Size(bodyW, h - stroke), r, style = Stroke(stroke))
        drawRoundRect(t.text.copy(alpha = 0.7f), Offset(bodyW + stroke, h * 0.32f), Size(nub, h * 0.36f), CornerRadius(1.5f * u))
        if (level >= 0) {
            val inset = stroke + 2 * u
            val full = bodyW - inset * 2 + stroke
            val fill = full * (level / 100f).coerceIn(0.06f, 1f)
            val c = if (level <= 15 && !charging) low else accent
            drawRoundRect(c, Offset(inset, inset), Size(fill, h - inset * 2), CornerRadius(2.5f * u))
        }
        if (charging) {
            // A small bolt in the middle.
            val cx = bodyW / 2f + stroke / 2f
            val p = androidx.compose.ui.graphics.Path().apply {
                moveTo(cx + 1.5f * u, 4 * u); lineTo(cx - 3f * u, h / 2f + 1 * u); lineTo(cx, h / 2f + 1 * u)
                lineTo(cx - 1.5f * u, h - 4 * u); lineTo(cx + 3f * u, h / 2f - 1 * u); lineTo(cx, h / 2f - 1 * u); close()
            }
            drawPath(p, t.onAccent)
        }
        if (update) {
            val c = Offset(w - 4.5f * u, 4.5f * u)
            drawCircle(t.background, 5.5f * u, c)
            drawCircle(accent, 4f * u, c)
        }
    }
}

/** Search: a little search field with the X button that opens it from anywhere in Settings. */
@Composable
private fun SearchHint() {
    val t = Themes.live
    Row(
        Modifier.fillMaxHeight(0.7f).aspectRatio(2.6f).clip(RoundedCornerShape(50)).border(1.dp, t.text.copy(alpha = 0.2f), RoundedCornerShape(50))
            .drawBehind { drawRect(t.backgroundDeep.copy(alpha = 0.45f)) }.padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.weight(1f).fillMaxHeight()) {
            val h = size.height
            drawRoundRect(t.muted.copy(alpha = 0.7f), Offset(2.dp.toPx(), h / 2f - 1.5.dp.toPx()), Size(size.width * 0.55f, 3.dp.toPx()), CornerRadius(1.5.dp.toPx()))
            // The caret, in the accent.
            drawRect(Palette.Accent, Offset(size.width * 0.55f + 4.dp.toPx(), h * 0.25f), Size(1.5.dp.toPx(), h * 0.5f))
        }
        ButtonHint("X", "", tappable = false)
    }
}
