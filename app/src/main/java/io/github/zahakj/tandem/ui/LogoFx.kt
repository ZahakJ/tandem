package io.github.zahakj.tandem.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onPlaced
import io.github.zahakj.tandem.data.LogoFit
import io.github.zahakj.tandem.data.LogoPlace
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import io.github.zahakj.tandem.Game
import io.github.zahakj.tandem.Libraries
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * Game logos with a little life, after the 3DS HOME menu's banners. Landing on a game, its logo pops in: it springs up
 * from half size with a squash and stretch (wide first, then tall, then settling), rises a few dp, and a glossy shine
 * sweeps across it. After that it sways in a gentle 3D tilt and bobs, and every few seconds the shine passes again.
 *
 * The shine is a soft white band drawn over the logo with BlendMode.SrcAtop in the logo's own offscreen layer, so it
 * lights the letters only, never the art around them. The sway reads the shared Ambient clock (or the companion's
 * steady clock) in the layer lambda only. Each shine pass is its own short real-time animation read in the draw lambda
 * (about 30 fps once idle), so a pass always runs to its end; the next one waits while the logo is covered, dimmed or
 * in the background. Reduce motion, animations Off and battery saver keep it still.
 */

/** How often the shine passes again while you rest on a game, and how long one pass takes. */
private const val SHINE_EVERY_MS = 6_500
private const val SHINE_MS = 1_100

/** The companion's repeat shine is rarer: it is on screen for the whole game, not while you rest on one. */
private const val STEADY_SHINE_EVERY_MS = 11_000

/**
 * The game's logo, animated, sized by area so a tall logo and a wide one weigh the same, within [maxWidth] x
 * [maxHeight]. Without a cut-out logo the title stands in, in big letters, animated the same way. [entranceDelayMs]
 * lets the art arrive first. [sway] turns on the idle tilt and the repeat shine, on the shared [Ambient] clock, or on
 * [steady] when given (the in-game companion, whose motion must not follow Tandem's input idleness).
 */
@Composable
fun AnimatedLogo(
    game: Game,
    maxWidth: Dp,
    maxHeight: Dp,
    modifier: Modifier = Modifier,
    entranceDelayMs: Long = 140,
    sway: Boolean = true,
    textAlign: TextAlign = TextAlign.Start,
    steady: SteadyClock? = null,
) {
    val logo = remember(game.key, io.github.zahakj.tandem.data.Store.meta) { Libraries.of(game)?.logo(game) }
    val aspect = rememberLogoAspect(logo)
    if (logo != null && aspect == null) return
    val size = if (logo != null && aspect != null) logoSize(aspect, maxWidth, maxHeight) else null
    val fit = game.meta.logoFit
    Box(modifier.logoFit(fit).logoLife(game.key, entranceDelayMs, sway, steady)) {
        if (logo != null && size != null) {
            // A logo made bigger in Edit details is decoded bigger too, so it stays sharp.
            val px = with(LocalDensity.current) { size.width.roundToPx() } * maxOf(1f, (fit?.scale ?: 100) / 100f)
            AsyncImage(rememberSizedRequest(logo, (px * 1.25f).toInt().coerceIn(256, 1400)), game.title, Modifier.size(size), contentScale = ContentScale.Fit)
        } else {
            Text(
                game.title, Modifier.widthIn(max = maxWidth), style = MaterialTheme.typography.displaySmall.onArt(),
                color = Color.White, textAlign = textAlign, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** The logo's width over height, known at once when it was read before; null until it is. */
@Composable
private fun rememberLogoAspect(logo: File?): Float? {
    if (logo == null) return null
    var aspect by remember(logo) { mutableStateOf(ArtPick.cachedAspect(logo)) }
    LaunchedEffect(logo) {
        if (aspect == null) aspect = withContext(Dispatchers.IO) { ArtPick.aspect(logo) ?: 3f }
    }
    return aspect
}

/** A logo's size by area rather than by width (tall and wide logos look equally weighty), within the box. */
private fun logoSize(aspect: Float, maxWidth: Dp, maxHeight: Dp): DpSize {
    val scale = (Hero.logoScale / 0.7f).coerceIn(0.65f, 1.35f)
    val area = maxWidth.value * maxHeight.value * 0.5f * scale * scale
    var w = sqrt(area * aspect).coerceAtMost(maxWidth.value)
    var h = w / aspect
    if (h > maxHeight.value) { h = maxHeight.value; w = h * aspect }
    return DpSize(w.dp, h.dp)
}

/**
 * The pop-in, the sway and the shine, for whatever this is drawn on (a logo or a title). Restarts when [key] changes.
 * With [steady] the sway runs on that clock instead of [Ambient], scaled by its energy, so it eases into the resting
 * pose when the clock stops, and repeat shines wait until it runs at full energy again.
 */
@Composable
fun Modifier.logoLife(key: Any, entranceDelayMs: Long = 140, sway: Boolean = true, steady: SteadyClock? = null): Modifier {
    val entering = Flair.entering
    val pop = remember(key) { Animatable(if (entering && !SwapFx.busy) 0f else 1f) }
    val settle = remember(key) { Animatable(if (entering && !SwapFx.busy) 0f else 1f) }
    // Where the shine band is: 0..1 across the logo during a pass, -1 between passes.
    val shine = remember(key) { mutableFloatStateOf(-1f) }
    val swaying = sway && Flair.floating
    val moving = rememberAmbient(swaying && steady == null) || (swaying && steady != null)
    // A repeat pass starts only while the logo shows and moves: on the steady clock while it runs at full energy
    // (not dimmed), on the ambient clock while nothing covers the screen. Checked between passes only, so a pass
    // that started always runs to the end and the shine rests cleanly off the logo.
    val repeatOn = rememberUpdatedState(moving)
    LaunchedEffect(key) {
        if (pop.value < 1f) {
            delay(entranceDelayMs)
            launch { pop.animateTo(1f, spring(dampingRatio = 0.42f, stiffness = 330f)) }
            launch { settle.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = 150f)) }
            delay(240)
            shinePass(shine, FastOutSlowInEasing, steady != null)
        }
        val gap = (if (steady != null) STEADY_SHINE_EVERY_MS else SHINE_EVERY_MS) - SHINE_MS.toLong()
        while (true) {
            delay(gap)
            val ready = {
                repeatOn.value && if (steady != null) steady.energy >= 0.99f else !Ambient.covered && !Ambient.resting
            }
            if (!ready()) {
                // Resting (dimmed, covered, in the background): the next pass comes a full gap after it wakes.
                snapshotFlow { ready() }.first { it }
                continue
            }
            shinePass(shine, RepeatShineEasing, steady != null)
        }
    }
    val amount = Flair.amount
    val rise = with(LocalDensity.current) { 18.dp.toPx() }
    val bob = with(LocalDensity.current) { 3.dp.toPx() } * amount
    val seed = ((key.hashCode() and 0x7fffffff) % 997) * 37L
    return graphicsLayer {
        val p = pop.value
        val q = settle.value
        alpha = (p * 2.4f).coerceIn(0f, 1f)
        // Squash and stretch: the fast spring runs ahead of the slow one, so the logo goes wide, then tall, then rests.
        val base = 0.5f + 0.5f * p
        val stretch = ((p - q) * 0.45f).coerceIn(-0.2f, 0.2f)
        scaleX = base * (1f + stretch)
        scaleY = base * (1f - stretch)
        translationY = (1f - q) * rise
        cameraDistance = 18f * density
        if (moving && steady != null) {
            val e = steady.energy * amount
            val turn = (seed % 1000) / 1000f
            rotationY = steady.wave(5_200, turn) * 7f * e
            rotationX = steady.wave(7_300, turn + 0.175f) * 2.5f * e
            translationY += steady.wave(3_600, turn) * bob * steady.energy
        } else if (moving) {
            val t = (Ambient.ms + seed).toFloat()
            val tau = 2f * PI.toFloat()
            rotationY = sin(t / 5_200f * tau) * 7f * amount
            rotationX = sin(t / 7_300f * tau + 1.1f) * 2.5f * amount
            translationY += sin(t / 3_600f * tau) * bob
        }
        compositingStrategy = CompositingStrategy.Offscreen
    }.drawWithContent {
        drawContent()
        val s = shine.floatValue
        if (s in 0f..1f) {
            val band = size.width * 0.16f + size.height * 0.3f
            val x = -band + s * (size.width + 2f * band)
            drawRect(
                Brush.linearGradient(
                    0f to Color.Transparent, 0.42f to Color.White.copy(alpha = 0f), 0.5f to Color.White.copy(alpha = 0.62f),
                    0.58f to Color.White.copy(alpha = 0f), 1f to Color.Transparent,
                    start = Offset(x - band, 0f), end = Offset(x + band * 0.4f, size.height),
                ),
                blendMode = BlendMode.SrcAtop,
            )
        }
    }
}

/** Repeat passes glide in and out evenly; the entrance pass starts fast (FastOutSlowIn). */
private val RepeatShineEasing = CubicBezierEasing(0.45f, 0f, 0.55f, 1f)

/**
 * One pass of the shine across the logo, [SHINE_MS] long in real time, written to [at] (0..1, then -1). On the
 * companion's [steady] clock it ticks at about 30 fps and the main thread sleeps in between; otherwise it runs on
 * [AmbientFrames]: every vsync while Tandem is in use, the ambient clock's ticks once idle.
 */
private suspend fun shinePass(at: MutableFloatState, easing: Easing, steady: Boolean) {
    try {
        if (steady) {
            val start = withFrameNanos { it }
            at.floatValue = 0f
            while (true) {
                delay(26)
                val t = (withFrameNanos { it } - start) / 1_000_000f / SHINE_MS
                if (t >= 1f) break
                at.floatValue = easing.transform(t)
            }
        } else kotlinx.coroutines.withContext(AmbientFrames) {
            // Each value is written inside the frame callback, so it is drawn in that tick's frame, not one after.
            val start = withFrameNanos { at.floatValue = 0f; it }
            while (true) {
                val t = withFrameNanos { now ->
                    val t = (now - start) / 1_000_000f / SHINE_MS
                    at.floatValue = if (t < 1f) easing.transform(t) else -1f
                    t
                }
                if (t >= 1f) break
            }
        }
    } finally {
        at.floatValue = -1f
    }
}

/**
 * A game's own logo size and place ([LogoFit], set in Edit details), as a scale and a shift in a graphics layer: the
 * layout around the logo never changes. Where its layout put it is noted on each placement; the shift is worked out
 * from that inside its parent ([LogoPlace]) and read only in the layer, so moving it recomposes nothing.
 */
@Composable
fun Modifier.logoFit(fit: LogoFit?): Modifier {
    if (fit == null || fit.isDefault) return this
    val spot = remember { mutableStateOf<FloatArray?>(null) }
    val margin = with(LocalDensity.current) { 12.dp.toPx() }
    return onPlaced { c ->
        val parent = c.parentLayoutCoordinates ?: return@onPlaced
        val at = parent.localPositionOf(c, Offset.Zero)
        val now = floatArrayOf(parent.size.width.toFloat(), parent.size.height.toFloat(), at.x, at.y, c.size.width.toFloat(), c.size.height.toFloat())
        if (spot.value?.contentEquals(now) != true) spot.value = now
    }.graphicsLayer {
        val g = spot.value ?: return@graphicsLayer
        val r = LogoPlace.place(fit, g[0], g[1], g[2], g[3], g[4], g[5], margin)
        scaleX = r.scale; scaleY = r.scale
        translationX = r.dx; translationY = r.dy
    }
}
