package io.github.zahakj.tandem.ui

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Choreographer
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.staticCompositionLocalOf
import io.github.zahakj.tandem.Idle
import io.github.zahakj.tandem.Screensaver
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine

/*
 * One clock for all ambient, decorative motion (floating titles, drifting backdrops, Ken Burns art, folder sheens,
 * Pip's bob, the pulse highlight), shared by both screens (docs/PERFORMANCE.md, "Idle").
 *
 * Separate infinite animations would each run a frame loop at the display's full rate, so instead:
 *  - The motion reads [Ambient.ms] (or [Ambient.livelyMs]) inside graphics-layer and draw lambdas only, so a tick
 *    redraws those layers and never recomposes or re-lays out anything.
 *  - The clock ticks only while something on a showing, resumed screen watches it ([rememberAmbient]).
 *  - Ten seconds after the last button, stick or touch it drops to about 30 fps ("idle"), and to about 12 fps after
 *    half a minute; between those ticks the main thread sleeps instead of waking on every vsync. [livelyMs] stops when
 *    idle: Pip stops bobbing and sheens rest, while blinks and glances carry on, rarer and on the clock's own ticks
 *    ([AmbientFrames]). After three minutes everything rests ([resting]), wallpaper videos too.
 *  - While the D-pad is held it ticks at the idle pace, so the other screen doesn't redraw on every frame of a scroll.
 *  - While a popup, panel or the launch animation covers the screen it stops altogether (the screensaver's own
 *    scene keeps it running for itself; everything under the screensaver stops watching).
 */
object Ambient {
    /** How long without input before ambient motion calms down to ~30 fps. */
    const val IDLE_AFTER_MS = 10_000L

    /** How long without input before it slows further, to ~12 fps (slow drifts and bobs still look smooth). */
    const val DEEP_IDLE_AFTER_MS = 30_000L

    private val clock = mutableLongStateOf(0L)
    private val livelyClock = mutableLongStateOf(0L)

    /** Ambient time in ms. Read it only in graphics-layer and draw lambdas. */
    val ms: Long get() = clock.longValue

    /** Like [ms], but stands still while idle: for motion that should rest when nobody is using Tandem. */
    val livelyMs: Long get() = livelyClock.longValue

    /** False once Tandem has been idle for [IDLE_AFTER_MS]. */
    var lively by mutableStateOf(true)
        private set

    /**
     * How long without input before idle motion stops altogether: the clock stands still, wallpaper videos hold
     * their frame and Pip rests. Clocks on screen still tick (they have their own minute timer).
     */
    const val REST_AFTER_MS = 3 * 60_000L

    /** True once nobody has used Tandem for [REST_AFTER_MS] (never while a screensaver scene runs). */
    var resting by mutableStateOf(false)
        private set

    private var watchers = 0
    private var running = false
    /** Nothing is scheduled: the clock waits for a new watcher, an animation frame ([AmbientFrames]) or input. */
    private var parked = false
    private var lastNanos = 0L
    private var pending = 0L
    private val main = Handler(Looper.getMainLooper())

    private val frame = Choreographer.FrameCallback { nanos -> onFrame(nanos) }
    private val wake = Runnable { schedule() }

    /** Watchers that read [ms] (the rest only read [livelyMs], which needs no ticks while idle). */
    private var fullWatchers = 0

    /** Short animations waiting for the next tick while idle ([AmbientFrames]). */
    private val tickers = ArrayList<(Long) -> Unit>()

    /** Starts the clock for one more watcher; the returned function stops watching. */
    fun watch(livelyOnly: Boolean = false): () -> Unit {
        watchers++
        if (!livelyOnly) fullWatchers++
        if (!running) lastNanos = 0L
        running = true
        parked = false
        armRest()
        main.removeCallbacks(wake)
        schedule()
        var done = false
        return { if (!done) { done = true; watchers--; if (!livelyOnly) fullWatchers-- } }
    }

    /** True while nobody has used Tandem for [IDLE_AFTER_MS] (read now, not at the last tick). */
    internal fun idleNow(): Boolean = SystemClock.elapsedRealtime() - Idle.last > IDLE_AFTER_MS

    /** Runs [tick] on the next tick (see [AmbientFrames]); the returned function cancels it. */
    internal fun onNextTick(tick: (Long) -> Unit): () -> Unit {
        tickers += tick
        armRest()
        if (!running || parked) {
            if (!running) lastNanos = 0L
            running = true
            parked = false
            main.removeCallbacks(wake)
            // At the idle pace, as the clock itself would tick.
            main.postDelayed(wake, 28)
        }
        return { tickers.remove(tick) }
    }

    /** A button, stick or touch (Idle.poke): motion that stopped ([resting], or parked while idle) comes back at once. */
    fun onInput() {
        if (!resting && !parked) return
        if (resting) { resting = false; armRest() }
        if (parked && watchers > 0) {
            parked = false
            main.removeCallbacks(wake)
            schedule()
        }
    }

    private var restArmed = false
    private val restCheck = Runnable {
        restArmed = false
        val left = Idle.last + REST_AFTER_MS - SystemClock.elapsedRealtime()
        when {
            left > 0 -> armRest(left)
            // A screensaver scene runs for hours on this clock: it never rests.
            Screensaver.active || io.github.zahakj.tandem.Dreaming.active -> armRest(REST_AFTER_MS)
            else -> resting = true
        }
    }

    /** Keeps the timer for [resting] running: it fires when the idle time runs out (and waits on after new input). */
    fun armRest(delayMs: Long = Idle.last + REST_AFTER_MS - SystemClock.elapsedRealtime()) {
        if (restArmed || resting) return
        restArmed = true
        main.postDelayed(restCheck, delayMs.coerceAtLeast(1_000L))
    }

    /**
     * A popup whose live preview is the other screen (the folder designer, FolderDesign.kt): the clock keeps going so
     * the preview's art pans and its logo moves; the screen under the popup stays calm by itself (its wallpaper stops).
     */
    var livePreview by mutableStateOf(false)

    /** Popups, panels and the launch animation cover the screens: nothing behind them needs to move. */
    val covered: Boolean
        get() = !Screensaver.active && (Nav.overlayOpen || Layers.top != null) && !livePreview

    private fun schedule() {
        // Never queued twice (a wake-up and a new watcher at the same time would otherwise double the loop).
        val c = Choreographer.getInstance()
        c.removeFrameCallback(frame)
        c.postFrameCallback(frame)
    }

    private fun onFrame(nanos: Long) {
        if (watchers <= 0 && tickers.isEmpty()) { running = false; return }
        val dt = if (lastNanos == 0L) 0L else ((nanos - lastNanos) / 1_000_000L).coerceIn(0L, 100L)
        lastNanos = nanos
        if (covered) {
            // Check again a little later instead of every frame; the clock resumes where it stopped.
            lastNanos = 0L
            main.removeCallbacks(wake)
            main.postDelayed(wake, 120)
            return
        }
        val idle = idleNow()
        if (lively == idle) lively = !idle
        // Something reads ms, and motion hasn't stopped for good.
        val ticking = fullWatchers > 0 && !resting
        if (idle && !ticking && tickers.isEmpty()) {
            // Only resting motion is watching (Pip's bob), or everything rests: no frames at all until the next input.
            lastNanos = 0L
            pending = 0L
            Snapshot.sendApplyNotifications()
            main.removeCallbacks(wake)
            if (resting) parked = true else main.postDelayed(wake, 250)
            return
        }
        if (ticking || !idle) {
            pending += dt
            clock.longValue += pending
            if (!idle) livelyClock.longValue += pending
        } else {
            lastNanos = 0L
        }
        pending = 0L
        // Blinks, glances and shines waiting for a tick draw in this same frame.
        if (tickers.isNotEmpty()) {
            val due = tickers.toTypedArray()
            tickers.clear()
            for (t in due) t(nanos)
        }
        // Deliver the change now, so the layers reading it redraw in this very frame.
        Snapshot.sendApplyNotifications()
        // A held D-pad scrolls the grid on every frame: the decorative motion steps at the idle pace meanwhile, so
        // the other screen draws half as often and the main thread stays free for the scrolling screen.
        if (!idle && !io.github.zahakj.tandem.Selection.held) { schedule(); return }
        if (!idle) { main.removeCallbacks(wake); main.postDelayed(wake, 28L); return }
        // Idle: about 30 ticks a second, and about 12 after half a minute without input. In between the main thread sleeps
        // rather than waking on every vsync (120 times a second on a 120 Hz screen) only to skip it.
        val deep = SystemClock.elapsedRealtime() - Idle.last > DEEP_IDLE_AFTER_MS
        main.removeCallbacks(wake)
        main.postDelayed(wake, if (deep) 80L else 28L)
    }

    /** 0..1, looping every [periodMs], offset by [offset] (0..1). */
    fun loop(periodMs: Int, offset: Float = 0f, lively: Boolean = false): Float {
        val t = if (lively) livelyMs else ms
        return ((t % periodMs) / periodMs.toFloat() + offset) % 1f
    }

    /** 0..1..0 every 2 x [halfMs], eased at both ends (like a reversing FastOutSlowIn tween). */
    fun pingPong(halfMs: Int, lively: Boolean = false): Float {
        val t = if (lively) livelyMs else ms
        val p = (t % (2L * halfMs)) / halfMs.toFloat()
        return FastOutSlowInEasing.transform(if (p <= 1f) p else 2f - p)
    }
}

/**
 * A frame clock for short decorative animations (Pip's blinks and glances, a logo's shine), used with
 * `withContext(AmbientFrames)`. While Tandem is in use its frames are the display's; once idle they are [Ambient]'s
 * ticks, so these animations draw in the same frames as the rest of the idle motion (about 30 a second) instead of
 * adding frames of their own in between.
 */
object AmbientFrames : MonotonicFrameClock {
    private val main = Handler(Looper.getMainLooper())

    override suspend fun <R> withFrameNanos(onFrame: (frameTimeNanos: Long) -> R): R =
        suspendCancellableCoroutine { cont ->
            // Runs inside the frame callback, so the new value is drawn in this very frame.
            val tick: (Long) -> Unit = { nanos ->
                val r = runCatching { onFrame(nanos) }
                Snapshot.sendApplyNotifications()
                cont.resumeWith(r)
            }
            if (!Ambient.idleNow()) {
                val cb = Choreographer.FrameCallback { nanos -> tick(nanos) }
                Choreographer.getInstance().postFrameCallback(cb)
                cont.invokeOnCancellation { main.post { Choreographer.getInstance().removeFrameCallback(cb) } }
            } else {
                val cancel = Ambient.onNextTick(tick)
                cont.invokeOnCancellation { main.post { cancel() } }
            }
        }
}

/** True inside the screensaver's own scene, which keeps moving while everything under it stops. */
val LocalInScreensaver = staticCompositionLocalOf { false }

/**
 * Watches the [Ambient] clock while [enabled] and this screen shows (resumed, not under the screensaver). Returns
 * whether it is watching: read [Ambient.ms] in layer and draw lambdas only when it is.
 */
@Composable
fun rememberAmbient(enabled: Boolean = true, livelyOnly: Boolean = false): Boolean {
    val inSaver = LocalInScreensaver.current
    // The screensaver scene runs in Tandem's own overlay or in Android's dream service (no resumed activity there).
    val on = enabled && if (inSaver) true else rememberResumed() && !Screensaver.active
    DisposableEffect(on, livelyOnly) {
        val stop = if (on) Ambient.watch(livelyOnly) else null
        onDispose { stop?.invoke() }
    }
    return on
}

/**
 * A steady clock for motion that must not care whether anyone is touching Tandem: the bottom-screen companion while
 * a game plays (CompanionScreen.kt). The player is busy with the game, not idle, so [Ambient]'s idle rules (which
 * look at Tandem's own input) would freeze or slow its motion; this clock ignores input and ticks at a fixed rate.
 *
 * Its time advances only while [energy] is above 0, at [energy] times real time, and [energy] eases from 0 to 1 when the
 * clock is switched on and back to 0 when it is switched off. Motion multiplies its swing by [energy] and reads [loop]
 * or [wave] for its phase, so it glides into its rest pose and out of it again instead of stopping mid-move. At 0 the clock
 * requests no frames at all. Read both only in graphics-layer and draw lambdas (no recomposition per tick).
 */
@Stable
class SteadyClock internal constructor() {
    internal val clock = mutableDoubleStateOf(0.0)
    internal val level = mutableFloatStateOf(0f)

    /** 0..1, looping every [periodMs] of clock time (slowed while easing in or out), offset by [offset] (0..1). */
    fun loop(periodMs: Int, offset: Float = 0f): Float = (((clock.doubleValue % periodMs) / periodMs).toFloat() + offset) % 1f

    /** sin of a full turn every [periodMs], offset by [turn] turns: a smooth -1..1 swing. */
    fun wave(periodMs: Int, turn: Float = 0f): Float = kotlin.math.sin((loop(periodMs) + turn) * 2f * kotlin.math.PI.toFloat())

    /** 0 (at rest) .. 1 (full motion). */
    val energy: Float get() = level.floatValue
}

/**
 * A [SteadyClock] that runs while [on], at about [fps] ticks a second, timed to land on vsync so every tick is the
 * same length (30 fps is every 4th vsync at 120 Hz, every 2nd at 60 Hz) and the main thread sleeps in between.
 * Switching it off eases the motion to rest over [easeOutMs] and then stops ticking; [ease] false (the screen went
 * to the background, or motion was turned off) stops it at once.
 */
@Composable
fun rememberSteadyClock(on: Boolean, ease: Boolean = true, fps: Int = 30, easeInMs: Int = 800, easeOutMs: Int = 1800): SteadyClock {
    val c = remember { SteadyClock() }
    LaunchedEffect(on, ease) {
        val target = if (on) 1f else 0f
        if (!ease) {
            c.level.floatValue = target
            if (!on) return@LaunchedEffect
        } else if (!on && c.level.floatValue <= 0f) {
            return@LaunchedEffect
        }
        val from = c.level.floatValue
        val dur = (if (on) easeInMs else easeOutMs).toFloat()
        val tickNanos = 1_000_000_000L / fps
        val start = withFrameNanos { it }
        var last = start
        while (true) {
            // Sleep until a little before the next tick is due, then take the first vsync after that.
            val wait = (last + tickNanos - 5_000_000L - System.nanoTime()) / 1_000_000L
            if (wait > 0) delay(wait)
            val now = withFrameNanos { it }
            val dt = ((now - last) / 1_000_000f).coerceIn(0f, 100f)
            last = now
            val p = if (c.level.floatValue == target) 1f else ((now - start) / 1_000_000f / dur).coerceIn(0f, 1f)
            val e = from + (target - from) * FastOutSlowInEasing.transform(p)
            c.level.floatValue = e
            c.clock.doubleValue += dt * e
            if (!on && p >= 1f) { c.level.floatValue = 0f; break }
        }
    }
    return c
}
