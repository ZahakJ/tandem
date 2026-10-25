package io.github.zahakj.tandem.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.runtime.State
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import io.github.zahakj.tandem.MainActivity
import io.github.zahakj.tandem.data.ExtrasStore
import io.github.zahakj.tandem.data.Store
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin

/*
 * Motion flair: logos, icons and titles float (a slow bob with a little breathing), rise in
 * with a spring when they change, and hero art drifts. Everything runs in graphics layers that read animation state
 * in the draw phase, so nothing recomposes per frame; it stops when the screen isn't resumed, and holds still with
 * Reduce motion, animations Off, battery saver, or Floating titles off.
 */

object Flair {
    /** Floating and drifting may run. */
    val floating: Boolean get() = ExtrasStore.settings.floating && !Store.settings.reduceMotion && !Themes.still

    /** Things may animate in when they change. */
    val entering: Boolean get() = !Store.settings.reduceMotion && Motion.style != AnimationStyle.Off

    /** How far and how much: subtle, normal, lively. */
    val amount: Float get() = when (ExtrasStore.settings.intensity) { 0 -> 0.5f; 2 -> 1.65f; else -> 1f }

    val intensityNames = listOf("Subtle", "Normal", "Lively")
}

/** True while the activity hosting this screen is resumed (ambient motion stops otherwise). */
@Composable
fun rememberResumed(): Boolean {
    // The composition's owner: the activity, or a screen kept composed while hidden (KeptScreen), which holds it
    // below resumed so nothing floats behind the screen in front.
    val owner: LifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var resumed by remember(owner) { mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, _ -> resumed = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    return resumed
}

/**
 * A slow 0..1 phase that loops on the shared [Ambient] clock (read it in layer lambdas only), or null when floating
 * is off or the screen isn't showing.
 */
@Composable
private fun floatPhase(periodMs: Int, lively: Boolean = false): (() -> Float)? {
    if (!Flair.floating || !rememberAmbient(livelyOnly = lively)) return null
    return remember(periodMs, lively) { { Ambient.loop(periodMs, lively = lively) } }
}

/**
 * A gentle float: up and down by [bob] (scaled by the intensity) over [periodMs], a breath of scale and a hint of
 * tilt. [seed] offsets the phase so neighbours don't move in lockstep. [restsWhenIdle]: it holds still once Tandem is
 * idle (Ambient.livelyMs), for something inside a big layer that every tick would redraw whole (the home grid, under
 * its faded edges).
 */
@Composable
fun Modifier.floating(seed: Int = 0, bob: Dp = 6.dp, periodMs: Int = 3600, breathe: Float = 0.014f, tilt: Float = 0.7f, restsWhenIdle: Boolean = false): Modifier {
    val phase = floatPhase(periodMs, restsWhenIdle) ?: return this
    val amount = Flair.amount
    val px = with(LocalDensity.current) { bob.toPx() } * amount
    val offset = ((seed and 0x7fffffff) % 97) / 97f
    return graphicsLayer {
        val a = ((phase() + offset) % 1f) * 2f * PI.toFloat()
        // A soft, eased bob: sin is already eased at the turning points.
        translationY = sin(a) * px
        val s = 1f + sin(a * 2f + 0.8f) * breathe * amount
        scaleX = s; scaleY = s
        rotationZ = sin(a + 1.3f) * tilt * amount
    }
}

/**
 * Rises in with a spring and a fade whenever [key] changes; [index] staggers several pieces (logo first, then the
 * card under it). [distance] is how far below it starts. [delayMs] replaces the stagger's wait (for long lists).
 */
@Composable
fun Modifier.riseIn(key: Any?, index: Int = 0, distance: Dp = 22.dp, fromScale: Float = 0.9f, delayMs: Long? = null): Modifier {
    if (!Flair.entering) return this
    // Composed during a screen swap: the swap's own choreography brings it in (SwapFx.kt), so no second entrance.
    val p = remember(key) { Animatable(if (SwapFx.busy) 1f else 0f) }
    LaunchedEffect(key) {
        delay(delayMs ?: (index * 75L))
        p.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 240f))
    }
    val px = with(LocalDensity.current) { distance.toPx() } * Flair.amount.coerceAtMost(1.3f)
    return graphicsLayer {
        val v = p.value
        alpha = v.coerceIn(0f, 1f)
        translationY = (1f - v) * px
        val s = fromScale + (1f - fromScale) * v
        scaleX = s; scaleY = s
    }
}

/** Slides in sideways (for captions under a carousel): from the side the user moved towards. */
@Composable
fun Modifier.slideInFrom(key: Any?, fromRight: Boolean = true, distance: Dp = 26.dp): Modifier {
    if (!Flair.entering) return this
    val p = remember(key) { Animatable(0f) }
    LaunchedEffect(key) { p.animateTo(1f, spring(dampingRatio = 0.75f, stiffness = 300f)) }
    val px = with(LocalDensity.current) { distance.toPx() } * if (fromRight) 1f else -1f
    return graphicsLayer {
        val v = p.value
        alpha = v.coerceIn(0f, 1f)
        translationX = (1f - v) * px
    }
}

/**
 * A slow sideways drift for a background layer (parallax behind a floating logo). The layer is zoomed just enough
 * that its edges never come into view while it moves, so a full-screen background stays full-bleed.
 */
@Composable
fun Modifier.drift(seed: Int = 0, distance: Dp = 10.dp, periodMs: Int = 9000): Modifier {
    val phase = floatPhase(periodMs) ?: return this
    val px = with(LocalDensity.current) { distance.toPx() } * Flair.amount
    val offset = ((seed and 0x7fffffff) % 89) / 89f
    return graphicsLayer {
        val a = ((phase() + offset) % 1f) * 2f * PI.toFloat()
        translationX = sin(a) * px
        translationY = sin(a * 0.5f + 0.7f) * px * 0.4f
        val short = minOf(size.width, size.height)
        if (short > 0f) {
            val s = 1f + (2f * px + 2f) / short
            scaleX = s; scaleY = s
        }
    }
}

private val RECENT_MODES = listOf("button" to "L2 shows it", "always" to "Always", "off" to "Off")

/** The "Motion & home" settings: floating titles, Recently played, labels, Home screen. */
@Composable
fun flairSettings(): SettingSection {
    val ctx = LocalContext.current
    val x = ExtrasStore.settings
    val look = Store.settings.look
    val isHome = Facts.run { ensure(ctx); Facts.isHome }
    fun nextIntensity(by: Int) = ExtrasStore.update { it.copy(intensity = (it.intensity + by).coerceIn(0, 2)) }
    fun nextRecent(by: Int) = ExtrasStore.update { s ->
        val i = RECENT_MODES.indexOfFirst { it.first == s.recentStrip }.coerceAtLeast(0)
        s.copy(recentStrip = RECENT_MODES[(i + by + RECENT_MODES.size) % RECENT_MODES.size].first)
    }
    return SettingSection(
        "Motion & home",
        listOf(
            SettingRow(
                "Floating titles", onOff(x.floating), Palette.Lavender, icon = Glyph.Motion, toggle = x.floating,
                help = "Logos, icons and titles float gently and rise in when they change, and hero art drifts. " +
                    "Holds still with Reduce motion, animations Off or battery saver.",
            ) { ExtrasStore.update { it.copy(floating = !it.floating) } },
            SettingRow(
                "Motion intensity", Flair.intensityNames[x.intensity.coerceIn(0, 2)], Palette.Lavender, icon = Glyph.Sparkle,
                help = "How far things float and bounce. Left and right change it.",
                onLeft = { nextIntensity(-1) }, onRight = { nextIntensity(1) },
            ) { ExtrasStore.update { it.copy(intensity = (it.intensity + 1) % 3) } },
            SettingRow(
                "Recently played strip", RECENT_MODES.first { it.first == x.recentStrip }.second, Palette.Mint, icon = Glyph.Clock,
                help = "On the icon grid, a bar of your last games along the top. L2 shows and hides it; A resumes a game.",
                onLeft = { nextRecent(-1) }, onRight = { nextRecent(1) },
            ) { nextRecent(1) },
            SettingRow(
                "Icon names", if (look.gridLabels) "Always" else if (x.focusLabels) "Highlighted only" else "Off", Palette.Sky, icon = Glyph.Text,
                help = "Names under the icon grid's tiles: always, only for the highlighted icon, or never.",
            ) {
                when {
                    look.gridLabels -> { Store.updateSettings { it.copy(look = it.look.copy(gridLabels = false)) }; ExtrasStore.update { it.copy(focusLabels = true) } }
                    x.focusLabels -> ExtrasStore.update { it.copy(focusLabels = false) }
                    else -> Store.updateSettings { it.copy(look = it.look.copy(gridLabels = true)) }
                }
            },
            SettingRow(
                "Use Tandem as the Home screen", if (isHome) "On" else "Off", Palette.Mint, icon = Glyph.Home, opens = true,
                help = "Then Home always brings you back here: once to Tandem, again to the first tile. Android asks you to confirm.",
            ) { Panels.openHomeExplainer() },
        ),
        icon = Glyph.Motion,
    )
}

/** What Pip is reacting to: a glance the way the D-pad moved (it settles back after a moment) and launch cheers. */
object PipMind {
    var lookX by mutableStateOf(0f)
        private set
    var lookY by mutableStateOf(0f)
        private set
    var cheers by mutableStateOf(0)
        private set
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val settle = Runnable { lookX = 0f; lookY = 0f }

    fun look(dx: Int, dy: Int) {
        if (Store.settings.reduceMotion) return
        lookX = dx.toFloat(); lookY = dy.toFloat()
        lastNudge = android.os.SystemClock.uptimeMillis()
        handler.removeCallbacks(settle)
        handler.postDelayed(settle, 650)
    }

    fun cheer() { cheers++ }

    /*
     * When nothing is happening, Pip looks around on its own: a glance to a random spot every few seconds, a
     * curious double-take now and then, and back to the middle. Runs only while Tandem is on screen (start/stop
     * from the activity), so it costs nothing while a game is playing.
     */
    private var idle = false
    private var lastNudge = 0L
    private val random = java.util.Random()
    private val wander: Runnable = object : Runnable {
        override fun run() {
            if (!idle) return
            val quiet = android.os.SystemClock.uptimeMillis() - lastNudge > 1500
            if (quiet && !Store.settings.reduceMotion && !Ambient.resting) {
                when (random.nextInt(6)) {
                    0, 1, 2 -> { lookX = random.nextInt(3) - 1f; lookY = random.nextInt(3) - 1f }
                    3 -> { lookX = -1f; lookY = 0f; handler.postDelayed({ if (idle) { lookX = 1f } }, 420) }
                    else -> { lookX = 0f; lookY = 0f }
                }
                handler.removeCallbacks(settle)
                handler.postDelayed(settle, 1300L + random.nextInt(1200))
            }
            // Once idle, rarer: each glance redraws the screens it is on for half a second.
            handler.postDelayed(this, if (Ambient.idleNow()) 5000L + random.nextInt(4000) else 1600L + random.nextInt(2000))
        }
    }

    fun startIdle() {
        if (idle) return
        idle = true
        handler.postDelayed(wander, 2000)
    }

    fun stopIdle() {
        idle = false
        handler.removeCallbacks(wander)
        lookX = 0f; lookY = 0f
    }
}
