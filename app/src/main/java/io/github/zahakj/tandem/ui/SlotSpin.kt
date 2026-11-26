package io.github.zahakj.tandem.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.zahakj.tandem.Game
import io.github.zahakj.tandem.R
import io.github.zahakj.tandem.Tx
import io.github.zahakj.tandem.data.Store
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.floor

/*
 * Surprise me as a slot machine: once Select has picked a game (the pick itself is the caller's), a reel of covers
 * spins past for about a second, slowing down, and lands on the pick with a little bounce. Each cover passing ticks
 * (the move sound, felt as Feel each move says); landing plays the confirm sound. The list underneath has already
 * gone to the game, so the reel fades away onto it. Reduce motion (or animations off) skips the reel: the toast
 * alone says what was picked. Any button stops it early. Pure timing lives in [SlotReel], unit tested.
 */
object SlotSpin {
    /** One spin: the covers in reel order, the pick last. */
    class Spin(val reel: List<Game>, val id: Long)

    var spin by mutableStateOf<Spin?>(null)
        private set

    /** Rolls onto [pick], with covers from [pool] flashing past first. */
    fun roll(pick: Game, pool: List<Game>) {
        val still = Store.settings.reduceMotion || Motion.style == AnimationStyle.Off
        val others = pool.filter { it.key != pick.key }.shuffled().take(SlotReel.COVERS - 1)
        if (still || others.isEmpty()) { landed(pick); return }
        spin = Spin(others + pick, System.nanoTime())
    }

    internal fun landed(pick: Game) {
        Toasts.show(Tx.s(R.string.home_surprise, pick.title))
    }

    fun stop() { spin = null }
}

/** The reel's timing: where it stands at each moment, decelerating onto the last cover. Pure. */
object SlotReel {
    /** Covers on the reel, the pick included. */
    const val COVERS = 14
    const val SPIN_MS = 950
    /** How far past the pick the reel runs before springing back, in covers. */
    const val OVERSHOOT = 0.22f
    /** Fast at first, a long slow end: a reel losing speed. */
    val EASE = CubicBezierEasing(0.12f, 0.75f, 0.25f, 1f)

    /** The cover under the marker at reel position [p] (rounded to the nearest), within [size]. */
    fun under(p: Float, size: Int): Int = Math.round(p).coerceIn(0, size - 1)

    /** How many covers have passed the marker between [from] and [to]: one tick each. */
    fun passed(from: Float, to: Float): Int = abs(floor(to + 0.5f).toInt() - floor(from + 0.5f).toInt())
}

@Composable
fun SlotSpinOverlay() {
    val spin = SlotSpin.spin ?: return
    InterceptButtons(true) { SlotSpin.stop(); SlotSpin.landed(spin.reel.last()); true }
    val pos = remember(spin.id) { Animatable(0f) }
    val fade = remember(spin.id) { Animatable(1f) }
    val last = (spin.reel.size - 1).toFloat()
    LaunchedEffect(spin.id) {
        var at = 0f
        // A tick per cover passing (the move sound and Feel each move's tick).
        val ticks = launch {
            snapshotFlow { pos.value }.collect { p -> if (SlotReel.passed(at, p) > 0) { at = p; Sounds.move() } }
        }
        pos.animateTo(last + SlotReel.OVERSHOOT, tween(SlotReel.SPIN_MS, easing = SlotReel.EASE))
        pos.animateTo(last, spring(dampingRatio = 0.42f, stiffness = Spring.StiffnessMediumLow))
        ticks.cancel()
        Sounds.confirm()
        Feel.thump()
        SlotSpin.landed(spin.reel.last())
        delay(380)
        fade.animateTo(0f, tween(220))
        SlotSpin.stop()
    }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val step = with(LocalDensity.current) { 196.dp.toPx() }
    Box(Modifier.fillMaxSize().graphicsLayer { alpha = fade.value }.background(Palette.NightDeep.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
        Box(Modifier.width(900.dp).height(300.dp), contentAlignment = Alignment.Center) {
            val p = pos.value
            for ((i, g) in spin.reel.withIndex()) {
                val d = i - p
                if (abs(d) > 2.6f) continue
                val near = 1f - (abs(d) / 2.6f).coerceIn(0f, 1f)
                Box(
                    Modifier.size(170.dp, 230.dp).graphicsLayer {
                        translationX = d * step * (if (rtl) -1 else 1)
                        val s = 0.78f + 0.22f * near
                        scaleX = s; scaleY = s
                        alpha = 0.25f + 0.75f * near
                    }.clip(RoundedCornerShape(18.dp)),
                ) { GameArt(g) }
            }
            // The marker the pick lands in.
            Box(Modifier.size(184.dp, 244.dp).border(4.dp, Palette.Butter, RoundedCornerShape(22.dp)))
        }
    }
}
