package io.github.zahakj.tandem.ui

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.view.ViewTreeObserver
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.zahakj.tandem.Library
import io.github.zahakj.tandem.MainActivity
import io.github.zahakj.tandem.data.Store
import io.github.zahakj.tandem.plural
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/*
 * A cold start, as one sequence on both screens:
 *
 * 1. Android's starting window: the last theme's backdrop colour, full screen, no icon, no bars (StartLook).
 * 2. When a scan cache exists, the first frame waits (up to HOLD_MS) for the library read from it, so the window
 *    goes straight from the starting colour to the home. Both screens' content waits out of sight meanwhile
 *    (SwapFx.introHold), over their wallpapers.
 * 3. Otherwise (first run, no cache, or a slow read) the splash shows over the wallpaper: Pip wakes up, the
 *    wordmark fades up and the number of games found counts up.
 * 4. Once the home is composed ([ready]) the splash fades and both screens play the swap's arrival: the grid's
 *    tiles fly in, staggered from the highlighted one, or the whole screen rises in; the details screen the same.
 *
 * Runs once per process. With reduce motion nothing is held or animated; the splash simply shows and goes.
 */
object StartFlow {
    /** The longest the first frame waits for the cached library before the splash shows instead. */
    private const val HOLD_MS = 800L
    /** A screen that never got its content (a scan that takes very long) shows it after this anyway. */
    private const val RELEASE_MS = 12_000L
    /** A screen that shows this long after the home came up arrives without the intro. */
    private const val LATE_MS = 1_500L

    private val main = Handler(Looper.getMainLooper())
    private var begun = false
    private val stageUp = BooleanArray(2)

    /** From a cold start until the home (or the setup screen) first shows. */
    var waiting by mutableStateOf(false)
        private set

    /** The splash may show: the first frame wasn't held for the cache, or the hold ran out. */
    var splashAllowed by mutableStateOf(false)
        private set

    /** Called once the home was composed (the held first frame may draw now). */
    @Volatile private var homeUp = false

    /**
     * First thing after [MainActivity] set its content, once per process. [cached]: a scan cache exists and its
     * library is being read (LibraryScan.startEarly), so the first frame waits for it.
     */
    fun begin(activity: MainActivity, cached: Boolean) {
        if (begun) return
        begun = true
        waiting = true
        SwapFx.introHold(ScreenSlot.Top)
        SwapFx.introHold(ScreenSlot.Bottom)
        if (cached) holdFirstDraw(activity) else splashAllowed = true
        main.postDelayed(release, RELEASE_MS)
    }

    /**
     * Keeps the window from drawing until the home is composed or [HOLD_MS] passed, so Android's starting window
     * stays up instead of an empty frame (the same thing the splash-screen library's keep-on-screen condition does).
     */
    private fun holdFirstDraw(activity: MainActivity) {
        val view = activity.findViewById<View>(android.R.id.content) ?: run { splashAllowed = true; return }
        val start = SystemClock.uptimeMillis()
        view.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                if (!homeUp && waiting && SystemClock.uptimeMillis() - start < HOLD_MS) return false
                view.viewTreeObserver.removeOnPreDrawListener(this)
                if (waiting) splashAllowed = true
                return true
            }
        })
    }

    /** A screen's stage was composed ([SwapStage]): after the home came up, it arrives at once. */
    fun stageShown(slot: ScreenSlot) {
        stageUp[slot.ordinal] = true
        if (begun && !waiting) play(slot)
    }

    fun stageGone(slot: ScreenSlot) { stageUp[slot.ordinal] = false }

    /** The home (or the setup screen) is composed: the splash goes and both screens arrive. Cheap to call again. */
    fun ready() {
        if (!waiting) return
        homeUp = true
        waiting = false
        main.removeCallbacks(release)
        // The cold start's time to a usable home, for tools/perf.sh: "Fully drawn" from Android when it tracks the
        // launch, and Tandem's own count from the process start to the first frame drawn with the home.
        runCatching { MainActivity.live?.get()?.reportFullyDrawn() }
        android.view.Choreographer.getInstance().postFrameCallback {
            val asked = if (android.os.Build.VERSION.SDK_INT >= 33) android.os.Process.getStartRequestedUptimeMillis()
            else android.os.Process.getStartUptimeMillis()
            android.util.Log.i("TandemPerf", "home drawn ${SystemClock.uptimeMillis() - asked} ms after the process start was asked")
        }
        playShown()
        main.postDelayed(late, LATE_MS)
    }

    private fun playShown() {
        for (slot in ScreenSlot.entries) if (stageUp[slot.ordinal]) play(slot)
    }

    /** Still scanning after a long while: the screens show what they have (the splash stays until [ready]). */
    private val release = Runnable { playShown() }

    /** A screen that hasn't shown by now (single-screen mode, say) shows normally whenever it does. */
    private val late = Runnable {
        SwapFx.introDrop(ScreenSlot.Top)
        SwapFx.introDrop(ScreenSlot.Bottom)
    }

    private fun play(slot: ScreenSlot) {
        val library = slot == librarySlot()
        val pieces = if (library) IconGrid.enabled && Nav.route == Route.Home && Layers.top == null
        else Store.settings.bottomMode != "clock" && Spotlight.wanted(io.github.zahakj.tandem.Selection.current)
        SwapFx.introPlay(slot, pieces)
    }

    fun librarySlot(): ScreenSlot = if (ScreenRoles.libraryOnBottom) ScreenSlot.Bottom else ScreenSlot.Top

    /** How long ago this process started, for screens that start alongside it (the bottom-screen home at boot). */
    fun processAgeMs(): Long = SystemClock.uptimeMillis() - android.os.Process.getStartUptimeMillis()
}

/**
 * The startup splash on the screen that holds the library: over that screen's own wallpaper (drawn by its stage),
 * never a solid block. Composed only while it shows or fades.
 */
@Composable
fun StartSplash(slot: ScreenSlot) {
    val shown = StartFlow.waiting && StartFlow.splashAllowed && slot == StartFlow.librarySlot()
    val state = remember { MutableTransitionState(false) }
    state.targetState = shown
    if (!state.currentState && !state.targetState) return
    val moving = Flair.entering
    TandemTheme {
        AnimatedVisibility(
            state,
            enter = fadeIn(if (moving) tween(380) else snap()),
            exit = if (moving) fadeOut(tween(360)) + scaleOut(tween(420, easing = FastOutSlowInEasing), targetScale = 1.08f) else fadeOut(snap()),
        ) { SplashFace(moving) }
    }
}

@Composable
private fun SplashFace(moving: Boolean) {
    val greet = Store.settings.look.splash
    // Pip wakes up: it fades and grows in asleep, opens its eyes, blinks, and hops. Simple animations that run only
    // while the splash is composed; Pip's own bob is on the shared ambient clock.
    val appear = remember { Animatable(if (moving) 0f else 1f) }
    val word = remember { Animatable(if (moving) 0f else 1f) }
    val hint = remember { Animatable(if (moving) 0f else 1f) }
    val hop = remember { Animatable(0f) }
    var eyes by remember { mutableStateOf(if (moving) 2 else 1) }
    LaunchedEffect(moving) {
        if (!moving) return@LaunchedEffect
        launch { appear.animateTo(1f, spring(0.75f, 180f)) }
        launch { delay(280); word.animateTo(1f, tween(560, easing = FastOutSlowInEasing)) }
        launch { delay(650); hint.animateTo(1f, tween(420)) }
        delay(560)
        eyes = 1
        delay(380)
        eyes = 2
        delay(110)
        eyes = 1
        delay(120)
        hop.animateTo(1f, tween(170))
        hop.animateTo(0f, spring(0.35f, 300f))
    }
    val found by produceState(Library.found) {
        while (true) {
            delay(150)
            value = Library.found
        }
    }
    val counted by animateIntAsState(found, if (moving) tween(450) else snap(), label = "splash-count")
    val glow = Palette.Lavender
    Box(
        Modifier.fillMaxSize().drawBehind {
            // A soft light behind Pip, in the theme's colour (never a dark band).
            drawRect(
                Brush.radialGradient(
                    listOf(glow.copy(alpha = 0.16f * appear.value.coerceIn(0f, 1f)), Color.Transparent),
                    center = Offset(size.width / 2, size.height * 0.44f), radius = size.minDimension * 0.62f,
                ),
            )
        },
        contentAlignment = Alignment.Center,
    ) {
        Column(Modifier.padding(horizontal = 22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (greet) {
                Box(
                    Modifier.graphicsLayer {
                        val a = appear.value
                        alpha = a.coerceIn(0f, 1f)
                        val s = 0.8f + 0.2f * a
                        scaleX = s; scaleY = s
                        translationY = (1f - a) * 18.dp.toPx() - hop.value * 20.dp.toPx()
                        rotationZ = hop.value * 5f
                    },
                ) { Pip(112.dp, mood = eyes, bounce = eyes == 1) }
                Spacer(Modifier.height(16.dp))
                Text(
                    "Tandem",
                    Modifier.graphicsLayer {
                        alpha = word.value
                        translationY = (1f - word.value) * 14.dp.toPx()
                    },
                    style = MaterialTheme.typography.displaySmall.onArt(), maxLines = 1,
                )
                Spacer(Modifier.height(6.dp))
            }
            Text(
                if (counted > 0) "Found ${plural(counted, "game")}…" else "Gathering your games…",
                Modifier.graphicsLayer { alpha = hint.value },
                style = MaterialTheme.typography.titleSmall.onArt(), color = Palette.Muted,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
