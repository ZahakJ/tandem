package io.github.zahakj.tandem.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/*
 * Tandem's one confirm for things that can't be undone (deleting a game, a folder with its subfolders, a backup or a
 * screenshot, clearing sessions, resetting play history, restoring over the current data): hold A on the control.
 * A ring fills over HOLD_MS with a rising tone; letting go before it is full cancels, and the ring drains back.
 * Touch does the same with a press and hold. Reversible actions don't ask at all: they offer Undo in their toast.
 */

/** How long A (or a finger) has to stay down. */
const val HOLD_MS = 800

/** One hold control's progress. Made by [rememberHold]; read [progress] only in draw or layer lambdas. */
@Stable
class HoldState internal constructor(
    private val scope: CoroutineScope,
    private val haptic: HapticFeedback,
    /** What a press that lets go too soon says ("Hold A to delete"). */
    internal var hint: () -> String,
    internal var onConfirm: () -> Unit,
) {
    val progress = Animatable(0f)
    var holding by mutableStateOf(false)
        private set
    private var job: Job? = null
    private var stream = 0
    private var done = false

    internal fun start() {
        if (holding) return
        holding = true
        done = false
        stream = Sounds.holdRise()
        job?.cancel()
        job = scope.launch {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(HOLD_MS, easing = LinearEasing))
            done = true
            holding = false
            Sounds.stopHold(stream)
            Touch.buzz(haptic)
            Sounds.confirm()
            onConfirm()
            progress.animateTo(0f, tween(260))
        }
    }

    /** The button or finger came up (or the focus left): cancels unless the hold already confirmed. */
    internal fun release(early: Boolean = true) {
        if (!holding) return
        holding = false
        if (done) return
        job?.cancel()
        Sounds.stopHold(stream)
        val far = progress.value
        job = scope.launch { progress.animateTo(0f, tween(180)) }
        // A quick press means the user didn't know: say how. A long one let go on purpose: say nothing.
        if (early && far < 0.5f) Toasts.show(hint())
    }

    internal fun dispose() { if (holding) { holding = false; job?.cancel(); Sounds.stopHold(stream) } }
}

@Composable
fun rememberHold(hint: String, onConfirm: () -> Unit): HoldState {
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val action by rememberUpdatedState(onConfirm)
    val text by rememberUpdatedState(hint)
    val state = remember { HoldState(scope, haptic, { text }, { action() }) }
    DisposableEffect(state) { onDispose { state.dispose() } }
    return state
}

private fun isSelect(k: Key) = k == Key.DirectionCenter || k == Key.Enter || k == Key.NumPadEnter || k == Key.ButtonA

/**
 * Makes the focusable control after it in the chain a hold control: A (DPAD_CENTER after MainActivity's mapping)
 * and touch go to [state] and never reach the control's own click. [requester] (attached by the caller) is focused
 * when a finger comes down. A finger that drags past the touch slop lets go, so a list can still scroll.
 */
fun Modifier.holdToConfirm(state: HoldState, requester: FocusRequester? = null): Modifier =
    onFocusChanged { if (!it.hasFocus) state.release(early = false) }
        .onPreviewKeyEvent { e ->
            if (!isSelect(e.key)) return@onPreviewKeyEvent false
            when (e.type) {
                KeyEventType.KeyDown -> if (e.nativeKeyEvent.repeatCount == 0) state.start()
                KeyEventType.KeyUp -> state.release()
            }
            true
        }
        .pointerInput(state) {
            val slop = viewConfiguration.touchSlop
            awaitEachGesture {
                // Taken in the first pass, so the control's own tap handling below never sees this gesture.
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                down.consume()
                requester?.let { runCatching { it.requestFocus() } }
                state.start()
                var dragged = false
                while (true) {
                    val ev = awaitPointerEvent(PointerEventPass.Initial)
                    val c = ev.changes.firstOrNull { it.id == down.id } ?: break
                    if ((c.position - down.position).getDistance() > slop) { dragged = true; break }
                    ev.changes.forEach { it.consume() }
                    if (ev.changes.none { it.pressed }) break
                }
                state.release(early = !dragged)
            }
        }

/** A ring around the content: a faint track, and an arc that fills clockwise as the hold goes on. */
fun Modifier.holdArc(state: HoldState, color: Color, width: Dp = 2.4.dp): Modifier = drawWithContent {
    drawContent()
    val w = width.toPx()
    val inset = w / 2
    val arc = Size(size.width - w, size.height - w)
    drawArc(color.copy(alpha = 0.3f), 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(w))
    val p = state.progress.value
    if (p > 0.001f) drawArc(color, -90f, 360f * p, false, Offset(inset, inset), arc, style = Stroke(w, cap = StrokeCap.Round))
}

/** The hold ring with the A button in the middle. */
@Composable
fun HoldRing(state: HoldState, color: Color, size: Dp = 20.dp) {
    Box(Modifier.size(size).holdArc(state, color), contentAlignment = Alignment.Center) {
        Text("A", style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1)
    }
}

/** Fills the control from the left as the hold goes on, behind its content. */
fun Modifier.holdFill(state: HoldState, color: Color, round: Dp): Modifier = drawWithContent {
    val p = state.progress.value
    if (p > 0.001f) drawRoundRect(color.copy(alpha = 0.32f), size = Size(size.width * p, size.height), cornerRadius = CornerRadius(round.toPx()))
    drawContent()
}

/** A pill button that acts only when held: the hold ring, then [text]. [hint] is what a quick press says. */
@Composable
fun HoldButton(text: String, color: Color, modifier: Modifier = Modifier, hint: String = holdHint(text.lowercase()), onConfirm: () -> Unit) {
    val state = rememberHold(hint, onConfirm)
    val requester = remember { FocusRequester() }
    FocusCard(
        modifier.height(42.dp).focusRequester(requester).holdToConfirm(state, requester),
        glow = color, shape = RoundedCornerShape(21.dp), onClick = {},
    ) {
        Row(
            Modifier.fillMaxHeight().background(color.copy(alpha = 0.22f)).holdFill(state, color, 21.dp).padding(start = 11.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HoldRing(state, color)
            Spacer(Modifier.width(8.dp))
            Text(text, style = MaterialTheme.typography.titleSmall, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** What a hold control's quick press says, for touch and controller alike ("Hold A, or press and hold, to delete"). */
fun holdHint(verb: String) = "Hold A, or press and hold, to $verb"

/**
 * The other half of the confirm pattern: a reversible action asks nothing and says what it did in a toast with
 * Undo (Select, or a tap on the toast), up as long as the grid's own undo toasts.
 */
object Undo {
    const val TOAST_MS = 6000L

    fun offer(label: String, revert: () -> Unit) = Toasts.show(label, TOAST_MS, Toasts.Action("Undo") {
        revert()
        Sounds.undo()
        Toasts.show("Undone")
        Nav.focusTick++
    })
}
