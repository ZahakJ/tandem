package io.github.zahakj.tandem.ui

import android.view.KeyEvent
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import io.github.zahakj.tandem.ActiveHost
import io.github.zahakj.tandem.Button
import io.github.zahakj.tandem.Buttons
import io.github.zahakj.tandem.MainActivity
import kotlinx.coroutines.launch

/*
 * Touch works like the controller (docs/UI-RULES.md, "Touch"):
 *
 * - Entries (games, systems, collections, apps, tiles, the built-in apps' rows: FocusCard(entry = true)) take two
 *   taps: the first highlights the entry (the other screen shows it), a tap on the highlighted entry opens it.
 *   Controls (buttons, menu rows, settings rows, keys, chips) act on the first tap.
 * - A long press is Y on that thing: it is highlighted, then Y is pressed through [Buttons], so it opens whatever the
 *   button opens there. A long press never also counts as a tap.
 * - A button hint runs its button when tapped ([HintPress]), through the same path as the real button.
 */

/** What tapping a [ButtonHint] presses. Pure, so the mapping is unit-tested. */
sealed interface HintPress {
    /** X, Y, Start, Select and the shoulders: handed to [Buttons.press], as the activity does for the real button. */
    data class Pad(val button: Button) : HintPress

    /** A or B: the key code the button sends once every swap is applied (A selects, B goes back). */
    data class Key(val code: Int) : HintPress

    /** A D-pad hint (◀▶ or ▲▼): the arm nearest the tap. */
    data class Dpad(val horizontal: Boolean) : HintPress {
        /** The key for a tap at [fraction] (0..1) across the hint (along it for ▲▼, top to bottom). */
        fun key(fraction: Float): Int = when {
            horizontal && fraction < 0.5f -> KeyEvent.KEYCODE_DPAD_LEFT
            horizontal -> KeyEvent.KEYCODE_DPAD_RIGHT
            fraction < 0.5f -> KeyEvent.KEYCODE_DPAD_UP
            else -> KeyEvent.KEYCODE_DPAD_DOWN
        }
    }

    companion object {
        /** The press for a hint's [button] name (the names [ButtonHint] takes); null for one it can't press. */
        fun of(button: String): HintPress? = when (button) {
            "A" -> Key(KeyEvent.KEYCODE_BUTTON_A)
            "B" -> Key(KeyEvent.KEYCODE_BUTTON_B)
            "◀▶" -> Dpad(horizontal = true)
            "▲▼" -> Dpad(horizontal = false)
            else -> Button.entries.firstOrNull { it.name == button }?.let(::Pad)
        }
    }
}

object Touch {
    /** Runs a tapped hint's [press]; [fraction] is where across the hint it was tapped. */
    fun press(press: HintPress, fraction: Float) {
        val activity = ActiveHost.token as? MainActivity
        when (press) {
            is HintPress.Pad -> Buttons.press(press.button)
            is HintPress.Key -> activity?.tapKey(press.code)
            is HintPress.Dpad -> activity?.tapKey(press.key(fraction))
        }
    }

    /**
     * A long press on something that holds the focus: [focus] it unless [focused], wait until it has the focus (its
     * focus callbacks, which tell the screen what is highlighted, run a frame later), then press Y.
     */
    suspend fun holdForY(focused: () -> Boolean, focus: () -> Unit) {
        if (!focused()) {
            focus()
            var frames = 0
            while (!focused() && frames++ < 8) withFrameNanos { }
            if (!focused()) return
        }
        pressY()
    }

    /** Y, as the controller presses it. Whatever opens makes its sound in SoundCues, as it does for the button. */
    fun pressY() {
        Buttons.press(Button.Y)
    }

    fun buzz(haptic: HapticFeedback) = haptic.performHapticFeedback(HapticFeedbackType.LongPress)
}

/** True inside a [FocusCard]'s content: a [ButtonHint] there leaves its taps to the card. */
val LocalInFocusCard = staticCompositionLocalOf { false }

/**
 * Touch for an entry that draws its own focusable box instead of a [FocusCard] (an icon grid tile). Put it before
 * the box's `focusable()`, and before its own tap handling, which it runs ahead of: a tap on an entry that isn't
 * highlighted only highlights it, and a long press highlights it and presses Y ([Touch.holdForY]) instead of
 * tapping. [onLongPress] replaces the Y press.
 */
@Composable
fun Modifier.touchEntry(onLongPress: (() -> Unit)? = null): Modifier {
    val requester = remember { FocusRequester() }
    val focused = remember { booleanArrayOf(false) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val custom by rememberUpdatedState(onLongPress)
    return focusRequester(requester).onFocusChanged { focused[0] = it.isFocused }.pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val wasFocused = focused[0]
            // false: the finger moved off (a scroll) or went away; a change: it lifted; null: it was held.
            val outcome = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                while (true) {
                    val c = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull false
                    if (!c.pressed) return@withTimeoutOrNull c
                    if (c.isConsumed || (c.position - down.position).getDistance() > viewConfiguration.touchSlop) return@withTimeoutOrNull false
                }
                @Suppress("UNREACHABLE_CODE") false
            }
            when (outcome) {
                null -> {
                    Touch.buzz(haptic)
                    val action = custom
                    if (action != null) action() else scope.launch { Touch.holdForY({ focused[0] }) { runCatching { requester.requestFocus() } } }
                    // The rest of the press is used up here, so the tap handling below never sees a tap.
                    do {
                        val e = awaitPointerEvent(PointerEventPass.Initial)
                        e.changes.forEach { it.consume() }
                    } while (e.changes.any { it.pressed })
                }
                is androidx.compose.ui.input.pointer.PointerInputChange -> if (!wasFocused) {
                    // The first tap only highlights.
                    outcome.consume()
                    runCatching { requester.requestFocus() }
                }
                else -> Unit
            }
        }
    }
}
