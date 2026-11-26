package io.github.zahakj.tandem.ui

import android.os.SystemClock
import android.view.HapticFeedbackConstants
import io.github.zahakj.tandem.ActiveHost
import io.github.zahakj.tandem.MainActivity
import io.github.zahakj.tandem.R
import io.github.zahakj.tandem.Selection
import io.github.zahakj.tandem.Tx
import io.github.zahakj.tandem.data.ExtrasStore

/*
 * Feel each move: a very light tick from the handheld's motor every time the highlight moves (grids, menus, the edit
 * screen: wherever the move sound plays), a slightly fuller one for a page turned with L1 / R1 (and none for the
 * highlight settling on the new page: SettleGate), and a fuller one when a tile is picked up or dropped. Through the window's
 * own haptic feedback, so Android's touch-feedback setting still decides whether anything is felt at all.
 */

/**
 * At most one tick per [gapMs]; while a direction is held (key repeats), one per [heldGapMs], so a fast scroll is a
 * gentle patter instead of a constant buzz. Pure, so it is unit tested.
 */
class TickGate(private val gapMs: Long = 35L, private val heldGapMs: Long = 140L) {
    private var last = Long.MIN_VALUE / 2

    fun allow(now: Long, held: Boolean): Boolean {
        if (now - last < if (held) heldGapMs else gapMs) return false
        last = now
        return true
    }
}

/**
 * After a page turn (L1 / R1 to the next tab, section or page) the new page puts its highlight where it belongs, and
 * that focus settling must not tick: one turn is one page sound and one page feel. For [windowMs] after [page], a
 * highlight move counts only once a D-pad press came after the turn ([direction]); the press that turned the page (a
 * grid's edge) doesn't count either. Pure, so it is unit tested.
 */
class SettleGate(private val windowMs: Long = 450L) {
    private var pageAt = Long.MIN_VALUE / 2
    private var directionAt = Long.MIN_VALUE / 2

    fun page(now: Long) { pageAt = now }

    fun direction(now: Long) { directionAt = now }

    fun allowMove(now: Long): Boolean = now - pageAt >= windowMs || directionAt > pageAt
}

object Feel {
    const val OFF = "off"
    const val LIGHT = "light"
    const val FIRM = "firm"

    val levels: List<Pair<String, String>>
        get() = listOf(OFF to Tx.s(R.string.controls_feel_off), LIGHT to Tx.s(R.string.controls_feel_light), FIRM to Tx.s(R.string.controls_feel_firm))

    private val gate = TickGate()

    private val level: String get() = ExtrasStore.settings.feelMoves

    /** The highlight moved. */
    fun move() {
        val l = level
        if (l == OFF || !gate.allow(SystemClock.uptimeMillis(), Selection.held)) return
        perform(if (l == FIRM) HapticFeedbackConstants.KEYBOARD_TAP else HapticFeedbackConstants.CLOCK_TICK)
    }

    /**
     * A page turned (L1 / R1): one tick a little fuller than a move's, so a new tab feels different from a new row.
     * Held shoulders patter at most about eight a second, as the page sound does.
     */
    fun page() {
        val l = level
        if (l == OFF || !pageGate.allow(SystemClock.uptimeMillis(), held = false)) return
        perform(if (l == FIRM) HapticFeedbackConstants.VIRTUAL_KEY else HapticFeedbackConstants.KEYBOARD_TAP)
    }

    private val pageGate = TickGate(gapMs = 115L, heldGapMs = 115L)

    /** Something was picked up, dropped or confirmed: one fuller tap. */
    fun thump() {
        if (level == OFF) return
        perform(HapticFeedbackConstants.CONFIRM)
    }

    /** A setting's new value, felt right away. */
    fun sample(l: String) {
        if (l == OFF) return
        perform(if (l == FIRM) HapticFeedbackConstants.KEYBOARD_TAP else HapticFeedbackConstants.CLOCK_TICK)
    }

    private fun perform(constant: Int) {
        val view = (ActiveHost.token as? MainActivity)?.window?.decorView ?: return
        runCatching { view.performHapticFeedback(constant) }
    }
}
