package io.github.zahakj.tandem.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.zahakj.tandem.data.Store
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.PI
import kotlin.math.sin

/*
 * Showcases: what a visual setting does, played in the side panel while its row is highlighted. A switch alternates
 * between its Off and On looks, a choice cycles through its options, each for about two seconds with a soft
 * crossfade, a chip naming what is on screen and marking the one in use. Every demo is a miniature drawn in Compose
 * in the live theme's colours (no GIFs or videos), so it always matches the theme. With Reduce motion (or animations
 * Off) nothing plays: the looks stand side by side, still. The miniatures keep their motion in draw and layer
 * lambdas, so a playing demo doesn't recompose.
 */

/** How long each look of a showcase stays on screen. */
private const val SHOWCASE_HOLD_MS = 2200L

/** True when showcases hold still: Reduce motion, or animations Off. */
@Composable
internal fun showcaseStill(): Boolean = Store.settings.reduceMotion || Motion.style == AnimationStyle.Off

/** 0 to 1 over [periodMs], round and round; 0 while showcases hold still. Read it in draw or layer lambdas. */
@Composable
internal fun loopClock(periodMs: Int): State<Float> {
    if (showcaseStill()) return remember { mutableFloatStateOf(0f) }
    return rememberInfiniteTransition(label = "loop").animateFloat(0f, 1f, infiniteRepeatable(tween(periodMs, easing = LinearEasing)), label = "t")
}

/**
 * Plays [labels].size looks of a setting in turn ([frame] draws look i), starting on [current] (the one in use, or
 * -1 for none) and again from it whenever it changes, so switching shows the new look at once.
 */
@Composable
fun Showcase(labels: List<String>, current: Int, modifier: Modifier = Modifier, color: Color = Palette.Accent, aspect: Float = 1.6f, frame: @Composable (Int) -> Unit) {
    val n = labels.size
    if (n == 0) return
    val start = current.coerceIn(0, n - 1)
    if (showcaseStill()) { StillShowcase(labels, current, modifier, color, aspect, frame); return }
    var shown by remember { mutableIntStateOf(start) }
    LaunchedEffect(current, n) {
        shown = start
        if (n < 2) return@LaunchedEffect
        while (true) { delay(SHOWCASE_HOLD_MS); shown = (shown + 1) % n }
    }
    val i = shown.coerceIn(0, n - 1)
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().aspectRatio(aspect).clip(RoundedCornerShape(16.dp)).background(Palette.NightDeep)) {
            Crossfade(i, animationSpec = tween(450), label = "showcase") { k -> Box(Modifier.fillMaxSize()) { frame(k) } }
            Caption(labels[i], i == current, color, Modifier.align(Alignment.TopStart).padding(8.dp))
        }
        if (n in 2..12) {
            Spacer(Modifier.height(8.dp))
            Dots(n, i, current, color)
        }
    }
}

/** A switch's two looks, Off then On. */
@Composable
fun ToggleShowcase(on: Boolean, color: Color, modifier: Modifier = Modifier, frame: @Composable (Boolean) -> Unit) =
    Showcase(listOf("Off", "On"), if (on) 1 else 0, modifier, color) { frame(it == 1) }

/** A choice's options, in order, by id. */
@Composable
fun ChoiceShowcase(options: List<Pair<String, String>>, current: String, color: Color, modifier: Modifier = Modifier, frame: @Composable (String) -> Unit) =
    Showcase(options.map { it.second }, options.indexOfFirst { it.first == current }, modifier, color) { frame(options[it].first) }

/** Reduce motion: up to four looks side by side, still (more than that: the one in use alone). */
@Composable
private fun StillShowcase(labels: List<String>, current: Int, modifier: Modifier, color: Color, aspect: Float, frame: @Composable (Int) -> Unit) {
    val shown = if (labels.size <= 4) labels.indices.toList() else listOf(current.coerceIn(0, labels.size - 1))
    val across = if (shown.size == 4) 2 else shown.size
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        shown.chunked(across).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { i ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier.fillMaxWidth().aspectRatio(aspect).clip(RoundedCornerShape(14.dp)).background(Palette.NightDeep)
                                .then(if (i == current) Modifier.border(2.dp, color, RoundedCornerShape(14.dp)) else Modifier),
                        ) { frame(i) }
                        Spacer(Modifier.height(6.dp))
                        Caption(labels[i], i == current, color, Modifier)
                    }
                }
            }
        }
    }
}

/** The chip on a showcase: what is on screen, and "In use" when that is the setting now. */
@Composable
private fun Caption(label: String, inUse: Boolean, color: Color, modifier: Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(50)).background(Palette.NightDeep.copy(alpha = 0.8f)).padding(start = 10.dp, end = if (inUse) 4.dp else 10.dp, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.widthIn(max = 180.dp), style = MaterialTheme.typography.labelLarge, color = if (inUse) color else Palette.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (inUse) {
            Spacer(Modifier.width(8.dp))
            Box(Modifier.clip(RoundedCornerShape(50)).background(color).padding(horizontal = 7.dp, vertical = 1.dp)) {
                Text("In use", style = MaterialTheme.typography.labelMedium, color = Palette.OnAccent, maxLines = 1)
            }
        }
    }
}

/** One dot per look: the one on screen filled, the one in use ringed. */
@Composable
private fun Dots(n: Int, shown: Int, current: Int, color: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(n) { i ->
            Box(
                Modifier.size(if (i == shown) 9.dp else 7.dp).clip(CircleShape)
                    .background(if (i == shown) color else Palette.Muted.copy(alpha = 0.4f))
                    .then(if (i == current && i != shown) Modifier.border(1.5.dp, color, CircleShape) else Modifier),
            )
        }
    }
}

// --- Drawing helpers --------------------------------------------------------------------------------------------

/** A miniature screen in the live theme: its background, then [draw] with the theme and a unit of 1 % of the height. */
@Composable
internal fun MiniScreen(modifier: Modifier = Modifier, draw: DrawScope.(t: ThemeSpec, u: Float) -> Unit) {
    val t = Themes.live
    Canvas(modifier.fillMaxSize()) {
        drawRect(Brush.verticalGradient(listOf(t.background, t.backgroundDeep)))
        draw(t, size.height / 100f)
    }
}

internal fun DrawScope.miniTile(t: ThemeSpec, x: Float, y: Float, w: Float, h: Float, i: Int, r: Float, alpha: Float = 1f) {
    val a = t.accents[((i % t.accents.size) + t.accents.size) % t.accents.size]
    drawRoundRect(Brush.linearGradient(listOf(a, lerp(a, t.background, 0.4f)), Offset(x, y), Offset(x + w, y + h)), Offset(x, y), Size(w, h), CornerRadius(r), alpha = alpha)
}

internal fun DrawScope.miniBar(x: Float, y: Float, w: Float, h: Float, c: Color, alpha: Float = 1f) =
    drawRoundRect(c, Offset(x, y), Size(w, h), CornerRadius(h / 2), alpha = alpha)

internal fun DrawScope.miniRing(t: ThemeSpec, x: Float, y: Float, w: Float, h: Float, r: Float, u: Float) {
    val o = 1.4f * u
    drawRoundRect(t.text, Offset(x - o, y - o), Size(w + 2 * o, h + 2 * o), CornerRadius(r + o), style = Stroke(1.1f * u))
}

/** Game art: a soft blend of the theme's colours, shifted by [phase] (0 to 1) for art that moves. */
internal fun DrawScope.miniArt(t: ThemeSpec, x: Float, y: Float, w: Float, h: Float, phase: Float = 0f, zoom: Float = 1f) {
    clipRect(x, y, x + w, y + h) {
        val cx = x + w / 2 + sin(phase * 2 * PI).toFloat() * w * 0.06f
        val cy = y + h / 2 + sin(phase * 2 * PI + 1.3).toFloat() * h * 0.05f
        scale(zoom, Offset(cx, cy)) {
            drawRect(Brush.linearGradient(listOf(t.sky, t.lavender, t.peach), Offset(x, y), Offset(x + w, y + h)), Offset(x, y), Size(w, h))
            drawCircle(t.butter.copy(alpha = 0.85f), h * 0.16f, Offset(x + w * 0.72f, y + h * 0.3f))
            drawCircle(t.mint.copy(alpha = 0.55f), h * 0.5f, Offset(x + w * 0.2f, y + h * 1.05f))
            drawCircle(t.lavender.copy(alpha = 0.6f), h * 0.42f, Offset(x + w * 0.85f, y + h * 1.1f))
        }
    }
}

/** A row of home tiles along [y], [n] across, the first ringed. */
internal fun DrawScope.miniTiles(t: ThemeSpec, u: Float, y: Float, h: Float, n: Int = 5, left: Float = 6 * u, right: Float = size.width - 6 * u, focus: Int = 0) {
    val gap = 3.5f * u
    val w = (right - left - gap * (n - 1)) / n
    for (i in 0 until n) {
        val x = left + i * (w + gap)
        miniTile(t, x, y, w, h, i, 3 * u)
        if (i == focus) miniRing(t, x, y, w, h, 3 * u, u)
    }
}

/** The top bar: a dot and a title on the left, the clock on the right. */
internal fun DrawScope.miniTopBar(t: ThemeSpec, u: Float, clock: Boolean = true) {
    drawCircle(t.mint, 2.6f * u, Offset(7 * u, 8 * u))
    miniBar(12 * u, 6.5f * u, size.width * 0.18f, 3 * u, t.text)
    if (clock) miniBar(size.width - 20 * u, 6.5f * u, 13 * u, 3 * u, t.text, 0.75f)
}

/** The shelves home in brief: three rows of tiles under the top bar. */
internal fun DrawScope.miniShelves(t: ThemeSpec, u: Float, top: Float = 16 * u, bottom: Float = size.height - 4 * u) {
    val rowH = (bottom - top) / 3f
    for (r in 0 until 3) {
        val y = top + r * rowH
        miniBar(6 * u, y, size.width * 0.16f, 2.6f * u, listOf(t.mint, t.lavender, t.peach)[r])
        miniTiles(t, u, y + 5 * u, rowH - 9 * u, n = if (r == 1) 3 else 5, focus = if (r == 0) 0 else -1)
    }
}

// --- Scenes -----------------------------------------------------------------------------------------------------

/** How a move eases in [style], as [Motion.move] does for the live theme. */
private fun <T> moveIn(style: AnimationStyle): FiniteAnimationSpec<T> = when (style) {
    AnimationStyle.Bouncy -> spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow)
    AnimationStyle.Smooth -> spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMediumLow)
    AnimationStyle.Minimal -> tween(160)
    AnimationStyle.Off -> snap()
}

/** The highlight hopping along a row of cards, eased the way [style] eases. */
@Composable
internal fun MotionStyleScene(style: AnimationStyle) {
    val x = remember { Animatable(0f) }
    val still = showcaseStill()
    LaunchedEffect(style, still) {
        x.snapTo(0f)
        if (still) return@LaunchedEffect
        while (true) {
            delay(450); x.animateTo(2f, moveIn(style))
            delay(450); x.animateTo(0f, moveIn(style))
        }
    }
    MiniScreen { t, u ->
        val n = 4
        val gap = 6 * u
        val w = (size.width - 16 * u - gap * (n - 1)) / n
        val h = minOf(w, 48 * u)
        val y = (size.height - h) / 2
        for (i in 0 until n) miniTile(t, 8 * u + i * (w + gap), y, w, h, i, 4 * u, alpha = 0.75f)
        val fx = 8 * u + x.value * (w + gap)
        val grow = 1.08f
        scale(grow, Offset(fx + w / 2, y + h / 2)) {
            drawRoundRect(t.glow.copy(alpha = 0.3f), Offset(fx - 2 * u, y - 2 * u), Size(w + 4 * u, h + 4 * u), CornerRadius(6 * u))
            miniTile(t, fx, y, w, h, x.value.toInt(), 4 * u)
            miniRing(t, fx, y, w, h, 4 * u, u)
        }
    }
}

/** Reduce motion: everything moving (the wallpaper, a floating title, the highlight), or all of it at rest. */
@Composable
internal fun StillnessScene(reduced: Boolean) {
    val clock = loopClock(4000)
    MiniScreen { t, u ->
        val p = if (reduced) 0f else clock.value
        val a = p * 2 * PI.toFloat()
        drawCircle(Brush.radialGradient(listOf(t.lavender.copy(alpha = 0.45f), Color.Transparent)), 40 * u, Offset(size.width * (0.25f + 0.08f * sin(a)), 30 * u + 8 * u * sin(a * 2)))
        drawCircle(Brush.radialGradient(listOf(t.peach.copy(alpha = 0.4f), Color.Transparent)), 36 * u, Offset(size.width * (0.75f - 0.07f * sin(a)), 70 * u))
        miniTopBar(t, u)
        val bob = if (reduced) 0f else sin(a * 2) * 2.4f * u
        miniBar(8 * u, 22 * u + bob, size.width * 0.38f, 7 * u, t.text)
        miniTiles(t, u, 46 * u, 36 * u, n = 4)
    }
}

/** Floating titles: a logo and title that drift gently ([amount] 0 to 2, how far), or hold still. */
@Composable
internal fun FloatScene(on: Boolean, amount: Int = 1) {
    val clock = loopClock(3200)
    MiniScreen { t, u ->
        val a = clock.value * 2 * PI.toFloat()
        val reach = if (!on) 0f else (1.5f + amount * 1.8f) * u
        miniArt(t, 0f, 0f, size.width, size.height * 0.62f)
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, t.background), 0f, size.height * 0.62f))
        translate(sin(a) * reach * 0.6f, sin(a * 2) * reach) {
            miniBar(size.width * 0.3f, 30 * u, size.width * 0.4f, 12 * u, t.text)
        }
        translate(0f, sin(a * 2 + 1f) * reach * 0.5f) {
            miniBar(size.width * 0.36f, 50 * u, size.width * 0.28f, 4 * u, t.muted)
        }
        miniTiles(t, u, 66 * u, 26 * u, n = 5)
    }
}

/** What a miniature screen of the handheld shows. */
internal enum class Shows { Library, Details, Off, App, AppFaint, Art, Companion, Emulator, Spotlight, Game }

/**
 * The handheld in brief: the wide top screen over the smaller bottom one, each showing [top] and [bottom]. Fits
 * any box (the picker's cards are wider than the side panel's frame).
 */
@Composable
internal fun DeviceScene(top: Shows, bottom: Shows) {
    MiniScreen { t, u -> device(t, top, bottom, 1f) }
}

/** Lays the two screens out in the box; returns the top and bottom screen rectangles' origin and size. */
private fun DrawScope.screens(): Pair<Pair<Offset, Size>, Pair<Offset, Size>> {
    // Top 16:9, bottom about 0.87 wide for its height, stacked with a hinge between, scaled to fit.
    val unitW = 1f
    val topH = 9f / 16f
    val botW = 0.52f
    val botH = botW / 0.87f
    val totalH = topH + 0.05f + botH
    val s = minOf(size.width * 0.92f / unitW, size.height * 0.94f / totalH)
    val topSize = Size(unitW * s, topH * s)
    val botSize = Size(botW * s, botH * s)
    val y0 = (size.height - totalH * s) / 2
    val topAt = Offset((size.width - topSize.width) / 2, y0)
    val botAt = Offset((size.width - botSize.width) / 2, y0 + (topH + 0.05f) * s)
    return (topAt to topSize) to (botAt to botSize)
}

private fun DrawScope.device(t: ThemeSpec, top: Shows, bottom: Shows, alpha: Float) {
    val (a, b) = screens()
    screen(t, a.first, a.second, top, alpha)
    // The hinge.
    val hy = a.first.y + a.second.height + (b.first.y - a.first.y - a.second.height) / 2
    drawRoundRect(t.muted.copy(alpha = 0.35f), Offset(b.first.x, hy - 1.5f), Size(b.second.width, 3f), CornerRadius(1.5f))
    screen(t, b.first, b.second, bottom, alpha)
}

/** One screen of [DeviceScene] at [at] with [sz], showing [what]. */
private fun DrawScope.screen(t: ThemeSpec, at: Offset, sz: Size, what: Shows, alpha: Float = 1f) {
    val u = sz.height / 100f
    val r = CornerRadius(minOf(sz.width, sz.height) * 0.06f)
    clipRect(at.x, at.y, at.x + sz.width, at.y + sz.height) {
        translate(at.x, at.y) {
            val w = sz.width
            val h = sz.height
            when (what) {
                Shows.Off -> drawRect(Color.Black, size = sz, alpha = alpha)
                else -> drawRect(Brush.verticalGradient(listOf(t.background, t.backgroundDeep), 0f, h), size = sz, alpha = alpha)
            }
            when (what) {
                Shows.Library -> {
                    val cols = if (w > h) 5 else 3
                    val gap = w * 0.03f
                    val tw = (w - gap * (cols + 1)) / cols
                    val rows = maxOf(1, ((h - 14 * u) / (tw + gap)).toInt())
                    for (rr in 0 until rows) for (c in 0 until cols) {
                        miniTile(t, gap + c * (tw + gap), 14 * u + rr * (tw + gap), tw, tw, rr * cols + c, tw * 0.18f, alpha)
                    }
                    miniRing(t, gap, 14 * u, tw, tw, tw * 0.18f, u * 0.6f)
                    miniBar(gap, 4 * u, w * 0.25f, 5 * u, t.text, alpha)
                }
                Shows.Details -> {
                    miniArt(t, 0f, 0f, w, h * 0.55f)
                    drawRect(Brush.verticalGradient(listOf(Color.Transparent, t.background), h * 0.25f, h * 0.55f), size = Size(w, h * 0.55f))
                    miniBar(w * 0.08f, h * 0.58f, w * 0.55f, 8 * u, t.text, alpha)
                    for (i in 0 until 3) miniBar(w * 0.08f, h * (0.72f + i * 0.08f), w * (0.8f - i * 0.15f), 4 * u, t.muted, alpha)
                }
                Shows.Off -> Unit
                Shows.App, Shows.AppFaint -> {
                    val f = if (what == Shows.AppFaint) 0.35f else 1f
                    drawRect(lerp(t.cardHigh, Color.White, 0.75f), size = sz, alpha = alpha * f)
                    drawRect(t.sky, size = Size(w, 12 * u), alpha = alpha * f)
                    for (i in 0 until 4) miniBar(w * 0.06f, h * (0.24f + i * 0.14f), w * (0.85f - i * 0.12f), 6 * u, Color.Black.copy(alpha = 0.25f), alpha * f)
                }
                Shows.Art -> {
                    miniArt(t, 0f, 0f, w, h)
                    drawRect(Color.Black.copy(alpha = 0.25f), size = sz)
                }
                Shows.Companion -> {
                    val gap = w * 0.06f
                    val bw = (w - gap * 3) / 2
                    val bh = (h - gap * 4) / 3
                    for (i in 0 until 6) {
                        val x = gap + (i % 2) * (bw + gap)
                        val y = gap + (i / 2) * (bh + gap)
                        drawRoundRect(t.card, Offset(x, y), Size(bw, bh), CornerRadius(bh * 0.25f), alpha = alpha)
                        drawCircle(t.accents[i % t.accents.size], bh * 0.18f, Offset(x + bw / 2, y + bh / 2), alpha = alpha)
                    }
                }
                Shows.Emulator -> {
                    drawRect(Color(0xFF203020), size = sz, alpha = alpha)
                    val cell = w / 8
                    for (x in 0 until 8) for (y in 0 until (h / cell).toInt() + 1) {
                        if ((x + y) % 3 == 0) drawRect(Color(0xFF3E6B3A), Offset(x * cell, y * cell), Size(cell, cell), alpha = alpha)
                    }
                    drawCircle(Color(0xFFE6D36B), cell * 0.35f, Offset(w * 0.55f, h * 0.45f), alpha = alpha)
                }
                Shows.Spotlight -> {
                    drawRect(Brush.radialGradient(listOf(t.lavender, t.background), Offset(w / 2, h / 2), maxOf(w, h) * 0.7f), size = sz, alpha = alpha)
                    val s = minOf(w, h) * 0.5f
                    miniTile(t, (w - s) / 2, (h - s) / 2 - 6 * u, s, s, 2, s * 0.22f, alpha)
                    miniBar(w * 0.3f, (h + s) / 2, w * 0.4f, 5 * u, t.text, alpha)
                }
                Shows.Game -> {
                    drawRect(Brush.verticalGradient(listOf(Color(0xFF5BB7F0), Color(0xFF9FE3B0)), 0f, h), size = sz, alpha = alpha)
                    drawRect(Color(0xFF5E9E4A), Offset(0f, h * 0.72f), Size(w, h * 0.28f), alpha = alpha)
                    drawCircle(Color(0xFFFFE07A), h * 0.1f, Offset(w * 0.8f, h * 0.22f), alpha = alpha)
                    drawRoundRect(Color(0xFFE5484D), Offset(w * 0.3f, h * 0.56f), Size(w * 0.06f, h * 0.16f), CornerRadius(w * 0.01f), alpha = alpha)
                }
            }
        }
    }
    drawRoundRect(t.muted.copy(alpha = 0.5f), at, sz, r, style = Stroke(1.2f))
}

/**
 * The screens trading places, the way [style] does it ("scatter", "push", "flip", "depth", "fade", "instant"): the
 * library and the details swap between the top and the bottom screen, and back.
 */
@Composable
internal fun SwapScene(style: String) {
    val p = remember { Animatable(0f) }
    val still = showcaseStill()
    LaunchedEffect(style, still) {
        p.snapTo(0f)
        if (still) return@LaunchedEffect
        delay(500)
        if (style == "instant") p.snapTo(1f) else p.animateTo(1f, tween(if (style == "scatter") 900 else 650))
    }
    MiniScreen { t, _ ->
        val k = p.value
        val (a, b) = screens()
        fun pair(at: Offset, sz: Size, old: Shows, new: Shows, fromBelow: Boolean) {
            when (style) {
                "push" -> clipRect(at.x, at.y, at.x + sz.width, at.y + sz.height) {
                    val d = sz.height * k * if (fromBelow) -1 else 1
                    translate(0f, d) { screen(t, at, sz, old) }
                    translate(0f, d - sz.height * if (fromBelow) -1 else 1) { screen(t, at, sz, new) }
                }
                "flip" -> {
                    val sy = kotlin.math.abs(1f - 2f * k).coerceAtLeast(0.02f)
                    scale(1f, sy, Offset(at.x + sz.width / 2, at.y + sz.height / 2)) { screen(t, at, sz, if (k < 0.5f) old else new) }
                }
                "depth" -> {
                    scale(1f - 0.15f * k, Offset(at.x + sz.width / 2, at.y + sz.height / 2)) { screen(t, at, sz, old, 1f - k) }
                    scale(1.12f - 0.12f * k, Offset(at.x + sz.width / 2, at.y + sz.height / 2)) { screen(t, at, sz, new, k) }
                }
                "fade", "scatter" -> { screen(t, at, sz, old, 1f - k); screen(t, at, sz, new, k) }
                else -> screen(t, at, sz, if (k < 0.5f) old else new)
            }
        }
        pair(a.first, a.second, Shows.Library, Shows.Details, fromBelow = true)
        pair(b.first, b.second, Shows.Details, Shows.Library, fromBelow = false)
        // Scatter: the icons fly across the hinge from one screen to the other.
        if (style == "scatter" && k in 0.01f..0.99f) {
            val n = 6
            for (i in 0 until n) {
                val from = Offset(a.first.x + a.second.width * (0.15f + 0.14f * i), a.first.y + a.second.height * 0.4f)
                val to = Offset(b.first.x + b.second.width * (0.2f + 0.12f * i), b.first.y + b.second.height * 0.45f)
                val e = (k * 1.4f - i * 0.06f).coerceIn(0f, 1f)
                val arc = sin(e * PI).toFloat() * a.second.height * (0.15f + 0.04f * i)
                val pos = Offset(from.x + (to.x - from.x) * e + arc * 0.3f, from.y + (to.y - from.y) * e - arc)
                val s = a.second.height * 0.16f
                miniTile(t, pos.x - s / 2, pos.y - s / 2, s, s, i, s * 0.2f)
            }
        }
    }
}

/** Video snaps: the card's art, then after [delayMs] of resting on it its video plays; none at all for null. */
@Composable
internal fun VideoSnapScene(delayMs: Int?) {
    val clock = loopClock(SHOWCASE_HOLD_MS.toInt())
    MiniScreen { t, u ->
        val ms = clock.value * SHOWCASE_HOLD_MS
        val w = size.width * 0.62f
        val h = w * 9f / 16f
        val x = (size.width - w) / 2
        val y = (size.height - h) / 2 - 4 * u
        val d = delayMs?.toFloat()
        val playing = d != null && ms >= d
        miniArt(t, x, y, w, h, phase = if (playing) (ms - d!!) / 900f else 0f, zoom = if (playing) 1.12f else 1f)
        if (playing) {
            // A play mark and a running progress line: the video is playing, muted.
            drawCircle(Color.Black.copy(alpha = 0.35f), h * 0.12f, Offset(x + w * 0.1f, y + h * 0.84f))
            drawRoundRect(Color.White.copy(alpha = 0.4f), Offset(x + w * 0.2f, y + h * 0.83f), Size(w * 0.72f, 2 * u), CornerRadius(u))
            drawRoundRect(Color.White, Offset(x + w * 0.2f, y + h * 0.83f), Size(w * 0.72f * ((ms - d!!) / 2000f).coerceIn(0f, 1f), 2 * u), CornerRadius(u))
        } else if (delayMs != null) {
            // Resting on the card: a ring fills until the video starts.
            val f = (ms / d!!).coerceIn(0f, 1f)
            drawArc(t.text, -90f, 360f * f, false, Offset(x + w / 2 - 7 * u, y + h / 2 - 7 * u), Size(14 * u, 14 * u), style = Stroke(2 * u))
        }
        miniRing(t, x, y, w, h, 3 * u, u)
        miniBar(x, y + h + 6 * u, w * 0.5f, 4 * u, t.text)
    }
}

/** Moving art: the game's art slowly panning and zooming, or still. */
@Composable
internal fun MovingArtScene(on: Boolean) {
    val clock = loopClock(6000)
    MiniScreen { t, u ->
        val p = if (on) clock.value else 0f
        miniArt(t, 0f, 0f, size.width, size.height, phase = p, zoom = if (on) 1.06f + 0.08f * sin(p * PI).toFloat() else 1f)
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, t.background), size.height * 0.45f, size.height))
        miniBar(8 * u, 72 * u, size.width * 0.4f, 8 * u, t.text)
        miniBar(8 * u, 84 * u, size.width * 0.55f, 3.5f * u, t.muted)
    }
}

/** How the art blends into the background below it: a soft or strong fade, a vignette, or none. */
@Composable
internal fun ArtFadeScene(id: String) {
    MiniScreen { t, u ->
        miniArt(t, 0f, 0f, size.width, size.height * 0.7f)
        when (id) {
            "strong" -> drawRect(Brush.verticalGradient(listOf(Color.Transparent, t.background), size.height * 0.1f, size.height * 0.5f))
            "vignette" -> drawRect(Brush.radialGradient(listOf(Color.Transparent, t.background), Offset(size.width / 2, size.height * 0.3f), size.width * 0.55f))
            "none" -> Unit
            else -> drawRect(Brush.verticalGradient(listOf(Color.Transparent, t.background), size.height * 0.35f, size.height * 0.7f))
        }
        miniBar(8 * u, 76 * u, size.width * 0.42f, 7 * u, t.text)
        miniBar(8 * u, 87 * u, size.width * 0.6f, 3.5f * u, t.muted)
    }
}

/** The game's logo over its art at [scale] of its full size. */
@Composable
internal fun LogoScene(scale: Float) {
    MiniScreen { t, u ->
        miniArt(t, 0f, 0f, size.width, size.height)
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, t.background.copy(alpha = 0.8f)), size.height * 0.4f, size.height))
        val w = size.width * 0.7f * scale
        val h = w * 0.32f
        drawRoundRect(Color.White, Offset((size.width - w) / 2, size.height * 0.6f - h / 2), Size(w, h), CornerRadius(h * 0.3f))
        drawRoundRect(t.lavender, Offset((size.width - w) / 2 + w * 0.08f, size.height * 0.6f - h * 0.18f), Size(w * 0.84f, h * 0.36f), CornerRadius(h * 0.18f))
        miniBar(8 * u, 88 * u, size.width * 0.5f, 3.5f * u, t.muted)
    }
}

/** A picture wallpaper darkened by [dim] (0 to 0.8), with text on top so the difference reads. */
@Composable
internal fun DimScene(dim: Float) {
    MiniScreen { t, u ->
        drawRect(Brush.linearGradient(listOf(Color(0xFFFFD29A), Color(0xFFFF8FB1), Color(0xFF8FD3FF)), Offset.Zero, Offset(size.width, size.height)))
        drawCircle(Color.White.copy(alpha = 0.8f), 14 * u, Offset(size.width * 0.78f, 28 * u))
        drawRect(Color.Black.copy(alpha = dim.coerceIn(0f, 1f)))
        miniTopBar(t, u)
        miniTiles(t, u, 40 * u, 30 * u, n = 4)
        miniBar(8 * u, 80 * u, size.width * 0.5f, 6 * u, t.text)
    }
}

/** The four face buttons in a prompt [style], with what they do. */
@Composable
internal fun PromptsScene(style: String) {
    Box(Modifier.fillMaxSize().background(Palette.Night), contentAlignment = Alignment.Center) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("A" to "Play", "B" to "Back", "X" to "Search", "Y" to "Options").forEach { (b, what) -> Badge(Prompts.physical(b, style), what) }
        }
    }
}

/** Two buttons and what each does: [first] does [firstDoes] and [second] does [secondDoes], or swapped. */
@Composable
internal fun SwapButtonsScene(swapped: Boolean, first: String, second: String, firstDoes: String, secondDoes: String) {
    val style = Store.settings.look.prompts
    Box(Modifier.fillMaxSize().background(Palette.Night), contentAlignment = Alignment.Center) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Badge(Prompts.physical(if (swapped) second else first, style), firstDoes)
            Badge(Prompts.physical(if (swapped) first else second, style), secondDoes)
        }
    }
}

@Composable
private fun Badge(p: PromptGlyph, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(30.dp).clip(if (p.square) RoundedCornerShape(8.dp) else CircleShape).background(p.color ?: Palette.Mint),
            contentAlignment = Alignment.Center,
        ) { Text(p.glyph, style = MaterialTheme.typography.titleSmall, color = p.onColor ?: Palette.OnAccent, maxLines = 1) }
        Spacer(Modifier.width(10.dp))
        Text(label, style = MaterialTheme.typography.titleMedium, color = Palette.Text, maxLines = 1)
    }
}

/** Launch animation: the game's card zooming up to fill the screen with a little flourish, or a cut straight to it. */
@Composable
internal fun LaunchScene(on: Boolean) {
    val clock = loopClock(SHOWCASE_HOLD_MS.toInt())
    MiniScreen { t, u ->
        val p = clock.value
        val w0 = size.width * 0.18f
        val x0 = 8 * u
        val y0 = 40 * u
        miniTopBar(t, u)
        miniTiles(t, u, y0, w0, n = 5)
        // 0..0.3 rest, then the zoom (or a cut), then the game.
        val e = if (on) ((p - 0.3f) / 0.4f).coerceIn(0f, 1f) else if (p >= 0.5f) 1f else 0f
        if (e > 0f) {
            val ease = 1f - (1f - e) * (1f - e)
            val x = x0 * (1 - ease)
            val y = y0 * (1 - ease)
            val w = w0 + (size.width - w0) * ease
            val h = w0 + (size.height - w0) * ease
            miniTile(t, x, y, w, h, 0, 3 * u * (1 - ease))
            if (on && e < 1f) for (i in 0 until 8) {
                val a = i * PI.toFloat() / 4
                val r = (12 + 30 * ease) * u
                drawCircle(t.butter, 1.6f * u * (1 - e), Offset(x + w / 2 + kotlin.math.cos(a) * r, y + h / 2 + sin(a) * r))
            }
            if (e >= 1f) {
                drawRect(Brush.verticalGradient(listOf(Color(0xFF5BB7F0), Color(0xFF9FE3B0))))
                drawRect(Color(0xFF5E9E4A), Offset(0f, size.height * 0.72f), Size(size.width, size.height * 0.28f))
            }
        }
    }
}

/** The Deck's keyboard in [layout] ("full" or "game"). */
@Composable
internal fun DeckScene(layout: String) {
    MiniScreen { t, u ->
        val key = t.card
        val ink = t.muted
        if (layout == "game") {
            // WASD with Shift, Ctrl and Space around it, and two mouse buttons on the right.
            val k = 16 * u
            val left = size.width * 0.12f
            for ((cx, cy) in listOf(1f to 0f, 0f to 1f, 1f to 1f, 2f to 1f)) drawRoundRect(t.lavender, Offset(left + cx * (k + 2 * u), 22 * u + cy * (k + 2 * u)), Size(k, k), CornerRadius(3 * u))
            drawRoundRect(key, Offset(left - k - 2 * u, 22 * u + 2 * (k + 2 * u)), Size(k * 1.6f, k), CornerRadius(3 * u))
            drawRoundRect(key, Offset(left + k, 22 * u + 2 * (k + 2 * u)), Size(k * 3, k), CornerRadius(3 * u))
            val mx = size.width * 0.62f
            drawRoundRect(key, Offset(mx, 24 * u), Size(size.width * 0.13f, 44 * u), CornerRadius(8 * u))
            drawRoundRect(key, Offset(mx + size.width * 0.14f, 24 * u), Size(size.width * 0.13f, 44 * u), CornerRadius(8 * u))
            drawRoundRect(ink, Offset(mx + size.width * 0.03f, 76 * u), Size(size.width * 0.21f, 6 * u), CornerRadius(3 * u))
        } else {
            val rows = 5
            for (r in 0 until rows) {
                val n = if (r == rows - 1) 7 else 12
                val gap = 1.6f * u
                val kw = (size.width - 12 * u - gap * (n - 1)) / n
                for (i in 0 until n) {
                    val wide = r == rows - 1 && i == 3
                    val x = 6 * u + i * (kw + gap)
                    drawRoundRect(if (wide) t.lavender else key, Offset(x, 12 * u + r * 16 * u), Size(if (wide) kw * 1f else kw, 13 * u), CornerRadius(2.5f * u))
                }
            }
        }
    }
}

/** Three system icons drawn in [style]. */
@Composable
internal fun IconStyleScene(style: IconStyle) {
    val systems = previewSystems(3).take(3)
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Palette.Night, Palette.NightDeep))), contentAlignment = Alignment.Center) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            systems.forEach { (id, short) -> DrawnIcon(short, Devices.of(id), Palette.forName(id), 64.dp, style = style) }
        }
    }
}

/** System tiles on the home screen in [style] ("pastel", "solid", "glass", "mono"). */
@Composable
internal fun SystemTilesScene(style: String) {
    MiniScreen { t, u ->
        miniTopBar(t, u)
        val n = 3
        val gap = 5 * u
        val w = (size.width - 12 * u - gap * (n - 1)) / n
        val h = 46 * u
        val y = 26 * u
        for (i in 0 until n) {
            val x = 6 * u + i * (w + gap)
            val a = t.accents[(i + 1) % t.accents.size]
            val r = CornerRadius(4 * u)
            when (style) {
                "solid" -> drawRoundRect(lerp(a, Color.Black, 0.15f), Offset(x, y), Size(w, h), r)
                "glass" -> {
                    drawRoundRect(t.cardHigh.copy(alpha = 0.45f), Offset(x, y), Size(w, h), r)
                    drawRoundRect(Color.White.copy(alpha = 0.35f), Offset(x, y), Size(w, h), r, style = Stroke(0.8f * u))
                    drawCircle(a, h * 0.16f, Offset(x + w * 0.75f, y + h * 0.3f))
                }
                "mono" -> {
                    drawRoundRect(t.card, Offset(x, y), Size(w, h), r)
                    drawRoundRect(a, Offset(x + 4 * u, y + h - 8 * u), Size(w * 0.3f, 2.5f * u), CornerRadius(u))
                }
                else -> drawRoundRect(Brush.linearGradient(listOf(a, a.copy(alpha = 0.35f), t.card), Offset(x, y), Offset(x + w, y + h)), Offset(x, y), Size(w, h), r)
            }
            miniBar(x + 4 * u, y + 5 * u, w * 0.45f, 4.5f * u, if (style == "mono") t.text else t.onAccent)
            if (i == 0) miniRing(t, x, y, w, h, 4 * u, u)
        }
        miniTiles(t, u, 80 * u, 14 * u, n = 6, focus = -1)
    }
}

/** The shelves home with its rows in [order] ("continue", "systems", "collections"), each named. */
@Composable
internal fun HomeRowsScene(order: List<String>) {
    val t = Themes.live
    Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(t.background, t.backgroundDeep))).padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        order.forEach { id ->
            val (name, c) = when (id) { "continue" -> "Continue" to t.mint; "systems" -> "Systems" to t.lavender; else -> "Collections" to t.peach }
            Text(name, style = MaterialTheme.typography.labelMedium, color = c, maxLines = 1)
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                val n = if (id == "continue") 3 else 5
                repeat(n) { i ->
                    Box(
                        Modifier.weight(1f).fillMaxSize().clip(RoundedCornerShape(5.dp))
                            .background(Brush.linearGradient(listOf(t.accents[(i + order.indexOf(id)) % t.accents.size], t.cardHigh))),
                    )
                }
            }
        }
    }
}

/** A folder opening as a page (under the top bar), a full-screen page, or a popup over the screen. */
@Composable
internal fun FolderOpenScene(mode: String) {
    MiniScreen { t, u ->
        if (mode == "popup" || mode == "Over the grid") {
            miniTopBar(t, u)
            miniShelves(t, u)
            drawRect(t.backgroundDeep.copy(alpha = 0.6f))
            val x = size.width * 0.14f
            val w = size.width * 0.72f
            drawRoundRect(t.card, Offset(x, 14 * u), Size(w, 74 * u), CornerRadius(8 * u))
            miniBar(x + 6 * u, 20 * u, w * 0.3f, 4 * u, t.text)
            for (r in 0 until 2) miniTiles(t, u, 30 * u + r * 28 * u, 24 * u, n = 4, left = x + 6 * u, right = x + w - 6 * u, focus = if (r == 0) 0 else -1)
        } else {
            val top = if (mode == "fullscreen") 4 * u else 16 * u
            if (mode != "fullscreen") miniTopBar(t, u)
            miniBar(6 * u, top, size.width * 0.3f, 5 * u, t.text)
            for (r in 0 until 3) miniTiles(t, u, top + 9 * u + r * ((92 * u - top) / 3), (92 * u - top) / 3 - 4 * u, n = 6, focus = if (r == 0) 0 else -1)
        }
    }
}

/** The home with a running game's banner at the top (Now playing), or without. */
@Composable
internal fun NowPlayingScene(on: Boolean) {
    MiniScreen { t, u ->
        miniTopBar(t, u)
        if (on) {
            drawRoundRect(t.card, Offset(6 * u, 15 * u), Size(size.width - 12 * u, 20 * u), CornerRadius(5 * u))
            miniArt(t, 8 * u, 17 * u, 26 * u, 16 * u)
            miniBar(38 * u, 20 * u, size.width * 0.25f, 4 * u, t.text)
            miniBar(38 * u, 27 * u, size.width * 0.15f, 3 * u, t.mint)
            miniShelves(t, u, top = 40 * u)
        } else miniShelves(t, u)
    }
}

/** The apps dock: [show]n or not, in [style] ("bar", "shelf", "minimal"), on [top] or below, faded when [autoHide]. */
@Composable
internal fun DockScene(show: Boolean, style: String, top: Boolean, autoHide: Boolean) {
    MiniScreen { t, u ->
        miniTopBar(t, u)
        val dockH = 14 * u
        val tilesTop = if (show && top) 18 * u + dockH else 18 * u
        miniTiles(t, u, tilesTop, 34 * u, n = 5)
        miniTiles(t, u, tilesTop + 39 * u, 22 * u, n = 6, focus = -1)
        if (!show) return@MiniScreen
        val y = if (top) 16 * u else size.height - dockH - 3 * u
        val alpha = if (autoHide) 0.35f else 1f
        val n = if (autoHide) 4 else 6
        val w = when (style) { "shelf" -> size.width; else -> size.width * (if (autoHide) 0.36f else 0.56f) }
        val x = (size.width - w) / 2
        when (style) {
            "shelf" -> drawRect(t.card, Offset(0f, y), Size(size.width, dockH), alpha = alpha)
            "minimal" -> Unit
            else -> drawRoundRect(t.card, Offset(x, y), Size(w, dockH), CornerRadius(dockH / 2), alpha = alpha)
        }
        val span = if (style == "shelf") size.width * 0.6f else w
        val sx = (size.width - span) / 2
        for (i in 0 until n) drawCircle(t.accents[(i + 2) % t.accents.size], dockH * (if (autoHide) 0.22f else 0.3f), Offset(sx + span * (i + 0.5f) / n, y + dockH / 2), alpha = alpha)
    }
}

/** Widgets along the top of the home screen (a clock, the battery, play time), or none. */
@Composable
internal fun HomeWidgetsScene(on: Boolean) {
    MiniScreen { t, u ->
        miniTopBar(t, u)
        if (on) {
            val w = (size.width - 12 * u - 8 * u) / 3
            for (i in 0 until 3) {
                val x = 6 * u + i * (w + 4 * u)
                drawRoundRect(t.card, Offset(x, 14 * u), Size(w, 20 * u), CornerRadius(5 * u))
                when (i) {
                    0 -> miniBar(x + 4 * u, 20 * u, w * 0.6f, 7 * u, t.text)
                    1 -> { drawCircle(t.mint, 6 * u, Offset(x + 10 * u, 24 * u), style = Stroke(1.8f * u)); miniBar(x + 20 * u, 22 * u, w * 0.4f, 4 * u, t.text) }
                    else -> { miniBar(x + 4 * u, 20 * u, w * 0.7f, 3.5f * u, t.muted); miniBar(x + 4 * u, 26 * u, w * 0.5f, 3.5f * u, t.peach) }
                }
            }
            miniShelves(t, u, top = 40 * u)
        } else miniShelves(t, u)
    }
}

/** The top bar with the clock in format [clock] ("system", "12", "24", "off") and the battery when [battery]. */
@Composable
internal fun TopBarScene(clock: String, battery: Boolean) {
    val now by rememberMinute()
    val text = when (clock) {
        "off" -> null
        "12" -> SimpleDateFormat("h:mm a", Locale.getDefault()).format(now)
        "24" -> SimpleDateFormat("HH:mm", Locale.getDefault()).format(now)
        else -> DateFormat.getTimeInstance(DateFormat.SHORT).format(now)
    }
    Box(Modifier.fillMaxSize()) {
        MiniScreen { t, u -> miniShelves(t, u, top = 34 * u) }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(Palette.Mint))
            Spacer(Modifier.width(8.dp))
            Text("Home", style = MaterialTheme.typography.titleSmall, color = Palette.Text, maxLines = 1)
            Spacer(Modifier.weight(1f))
            if (battery) {
                Text("87%", style = MaterialTheme.typography.titleSmall, color = Palette.Muted, maxLines = 1)
                Spacer(Modifier.width(10.dp))
            }
            if (text != null) Text(text, style = MaterialTheme.typography.titleMedium, color = Palette.Text, maxLines = 1)
        }
    }
}

/** The small button reminder in the top screen's corner, or a clean corner. */
@Composable
internal fun CornerHintsScene(on: Boolean) {
    Box(Modifier.fillMaxSize()) {
        MiniScreen { t, u -> miniTopBar(t, u); miniShelves(t, u) }
        if (on) Row(
            Modifier.align(Alignment.BottomEnd).padding(8.dp).clip(RoundedCornerShape(50)).background(Palette.Card.copy(alpha = 0.9f))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            listOf("A" to "Open", "X" to "Search", "Start" to "Menu").forEach { (b, what) ->
                val p = Prompts.physical(b, Store.settings.look.prompts)
                Box(Modifier.height(14.dp).widthIn(min = 14.dp).clip(RoundedCornerShape(50)).background(p.color ?: Palette.Mint).padding(horizontal = 3.dp), contentAlignment = Alignment.Center) {
                    Text(p.glyph, style = MaterialTheme.typography.labelSmall, color = p.onColor ?: Palette.OnAccent, maxLines = 1)
                }
                Text(what, style = MaterialTheme.typography.labelSmall, color = Palette.Text, maxLines = 1)
            }
        }
    }
}
