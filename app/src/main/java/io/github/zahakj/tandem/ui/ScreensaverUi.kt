package io.github.zahakj.tandem.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import io.github.zahakj.tandem.Collections
import io.github.zahakj.tandem.Libraries
import io.github.zahakj.tandem.Screensaver
import io.github.zahakj.tandem.data.Store
import kotlinx.coroutines.delay
import java.io.File
import java.text.DateFormat
import java.util.Date
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private const val TWO_PI = (2 * PI).toFloat()

/**
 * After a while with no input: covers drift slowly across the night sky while Pip naps. Everything keeps moving
 * so nothing burns into the screen. Any button or touch wakes it, and that press does nothing else.
 */
@Composable
fun ScreensaverOverlay() {
    AnimatedVisibility(Screensaver.active, enter = fadeIn(tween(1800)), exit = fadeOut(tween(250))) {
        ScreensaverScene()
    }
}

/**
 * The screensaver itself, shared by the in-app overlay and [io.github.zahakj.tandem.TandemDream] (Android's own
 * screensaver slot). Colours, type and backdrop all come from the current theme.
 */
@Composable
fun ScreensaverScene() {
    // Only games that may be shown: hidden games stay out of the screensaver unless revealed.
    val covers = remember {
        Collections.visibleGames().shuffled().asSequence().mapNotNull { g -> Libraries.of(g)?.box(g) }.take(9).toList()
    }
    val slow = Store.settings.reduceMotion
    // The scene runs for hours: its slow drift ticks at 30 fps on a steady clock (the main thread sleeps between
    // ticks) rather than on every vsync of a 120 Hz screen.
    val clock = rememberSteadyClock(on = true)
    val period = if (slow) 360_000 else 150_000
    val phase = { clock.loop(period) * TWO_PI }
    Box(Modifier.fillMaxSize().background(Palette.NightDeep)) {
        // Everything under the screensaver stops; its own backdrop keeps the shared ambient clock going.
        androidx.compose.runtime.CompositionLocalProvider(LocalInScreensaver provides true) {
            AnimatedBackdrop(MaterialTheme.colorScheme.primary, Modifier.graphicsLayer { alpha = 0.6f })
        }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val w = constraints.maxWidth.toFloat()
            val h = constraints.maxHeight.toFloat()
            covers.forEachIndexed { i, cover -> DriftingCover(cover, i, covers.size, w, h, phase) }
            Clock(Modifier.align(Alignment.Center), phase)
            SleepyPip(Modifier.align(Alignment.BottomEnd).padding(28.dp), clock, phase)
        }
    }
}

@Composable
private fun DriftingCover(cover: File, i: Int, n: Int, w: Float, h: Float, phase: () -> Float) {
    val size = (88 + (i * 37) % 60).dp
    // Each cover gets its own lane, speed and wobble, so they never move in step.
    val laneX = (i + 0.5f) / n
    val speed = 1 + i % 3
    val offset = i * 1.7f
    AsyncImage(
        cover, null,
        Modifier
            .size(size)
            .graphicsLayer {
                val p = phase() * speed + offset
                translationX = w * (laneX + 0.08f * sin(p * 0.7f)) - size.toPx() / 2
                translationY = h * (0.5f + 0.42f * sin(p)) - size.toPx() / 2
                rotationZ = 10f * sin(p * 1.3f)
                alpha = 0.35f + 0.25f * (0.5f + 0.5f * cos(p * 0.9f))
            }
            .shadow(10.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp)),
        contentScale = ContentScale.Crop,
    )
}

@Composable
private fun Clock(modifier: Modifier, phase: () -> Float) {
    val now by rememberMinute()
    val accent = MaterialTheme.colorScheme.primary
    Column(
        modifier.graphicsLayer {
            // A drift of a few pixels keeps it off the same pixels without ever looking off-centre.
            translationX = 6.dp.toPx() * sin(phase() * 3)
            translationY = 6.dp.toPx() * cos(phase() * 2)
        },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            DateFormat.getTimeInstance(DateFormat.SHORT).format(now),
            style = MaterialTheme.typography.displayLarge.copy(fontSize = 96.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center),
            color = Palette.Text,
        )
        Text(
            java.text.SimpleDateFormat("EEEE, MMMM d", java.util.Locale.getDefault()).format(now),
            style = MaterialTheme.typography.titleLarge.copy(textAlign = androidx.compose.ui.text.style.TextAlign.Center),
            color = accent,
        )
        Box(Modifier.padding(top = 14.dp).size(width = 48.dp, height = 4.dp).clip(RoundedCornerShape(2.dp)).background(accent.copy(alpha = 0.5f)))
    }
}

@Composable
private fun SleepyPip(modifier: Modifier, clock: SteadyClock, phase: () -> Float) {
    Box(modifier) {
        Pip(84.dp, mood = 2)
        for (k in 0 until 3) {
            Text(
                "z", Modifier.align(Alignment.TopEnd).graphicsLayer {
                    val p = clock.loop(3200, k / 3f)
                    translationX = 10f + p * 40f + 6f * sin(phase() * 20 + k)
                    translationY = -p * 90f
                    alpha = 1f - p
                    scaleX = 0.7f + p; scaleY = scaleX
                },
                style = MaterialTheme.typography.titleLarge, color = Palette.Lavender,
            )
        }
    }
}

/**
 * On the bottom screen: the now-playing banner, and a dim, sleepy screen while the screensaver runs. [bannerTop] is
 * what the banner keeps clear of at the top (Spotlight's status pill).
 */
@Composable
fun BottomExtras(bannerTop: Dp = 0.dp) {
    Box(Modifier.fillMaxSize()) {
        // Under the pill, 6 dp clear of it (the banner brings its own margin, so that much less padding).
        val below = if (bannerTop > 0.dp) (bannerTop + 6.dp - NowPlayingBannerMargin).coerceAtLeast(0.dp) else 0.dp
        // Its height (with the gap above it) is the room the details leave at the top (clearOfNowPlaying).
        val belowPx = with(LocalDensity.current) { below.roundToPx() }
        NowPlayingBanner(Modifier.align(Alignment.TopCenter).padding(top = below).onSizeChanged { NowPlayingRoom.height = if (it.height > 0) it.height + belowPx else 0 })
        DisposableEffect(Unit) { onDispose { NowPlayingRoom.height = 0 } }
        AnimatedVisibility(Screensaver.active, enter = fadeIn(tween(1800)), exit = fadeOut(tween(250))) {
            ScreensaverBottomScene(Modifier.swallowTaps { Screensaver.wake() })
        }
    }
}

/**
 * The bottom screen's side of the screensaver: a dim night with Pip napping as it drifts about. Shared by the in-app
 * screensaver and [io.github.zahakj.tandem.TandemDream]'s window on the bottom screen.
 */
@Composable
fun ScreensaverBottomScene(modifier: Modifier = Modifier) {
    Box(Modifier.fillMaxSize().background(Palette.NightDeep).then(modifier)) {
        val clock = rememberSteadyClock(on = true)
        val p = { clock.loop(120_000) * TWO_PI }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val w = constraints.maxWidth.toFloat()
            val h = constraints.maxHeight.toFloat()
            SleepyPip(
                Modifier.graphicsLayer {
                    translationX = w * (0.38f + 0.25f * sin(p()))
                    translationY = h * (0.4f + 0.25f * cos(p() * 1.5f))
                },
                clock, p,
            )
        }
    }
}
