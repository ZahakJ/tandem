package io.github.zahakj.tandem

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.zahakj.tandem.data.HomeFolder
import io.github.zahakj.tandem.data.HomeStore
import io.github.zahakj.tandem.data.Store
import io.github.zahakj.tandem.ui.Nav
import io.github.zahakj.tandem.ui.Panels
import io.github.zahakj.tandem.ui.Toasts
import kotlin.math.abs

/**
 * Held buttons and button combinations:
 * - Select tap: its usual action. Hold Select, or press Select + Start: the game switcher.
 * - Start tap: its usual action. Hold Start: the quick panel.
 * - L1 + R1 + Select: reveal or re-hide hidden games in private mode.
 *
 * Select and Start fire on release so a hold can mean something else. Fed every key event by MainActivity.
 */
object Chords {
    private const val HOLD_MS = 550L
    private const val SELECT = KeyEvent.KEYCODE_BUTTON_SELECT
    private const val START = KeyEvent.KEYCODE_BUTTON_START
    private const val L1 = KeyEvent.KEYCODE_BUTTON_L1
    private const val R1 = KeyEvent.KEYCODE_BUTTON_R1

    private val handler = Handler(Looper.getMainLooper())
    private val held = HashSet<Int>()
    /** Keys whose remaining events are swallowed (the press that woke the screensaver). */
    private val swallow = HashSet<Int>()
    /** The current Select / Start press already did something, so its release is not a tap. */
    private var selectUsed = false
    private var startUsed = false

    private val selectHold = Runnable {
        if (SELECT in held && !selectUsed) { selectUsed = true; Panels.openSwitcher() }
    }
    private val startHold = Runnable {
        if (START in held && !startUsed) { startUsed = true; Panels.toggleQuick() }
    }

    /** True when the event was fully handled here and must not go further. */
    fun onKey(e: KeyEvent): Boolean {
        val code = if (e.keyCode == KeyEvent.KEYCODE_MENU) START else e.keyCode
        if (wakeScreensaver(e)) return true
        when (e.action) {
            KeyEvent.ACTION_DOWN -> if (e.repeatCount == 0) onDown(code)
            KeyEvent.ACTION_UP -> onUp(code)
        }
        return code == SELECT || code == START
    }

    /**
     * Any button (held or repeating ones too) wakes the in-app screensaver, and the rest of that press is swallowed.
     * True when the event was used up that way. Checked before anything else gets the key, the controller test too.
     */
    fun wakeScreensaver(e: KeyEvent): Boolean {
        val code = if (e.keyCode == KeyEvent.KEYCODE_MENU) START else e.keyCode
        Idle.poke()
        if (e.action == KeyEvent.ACTION_DOWN && Screensaver.active) {
            Screensaver.wake()
            swallow += code
            return true
        }
        if (code in swallow) {
            if (e.action == KeyEvent.ACTION_UP) swallow -= code
            return true
        }
        return false
    }

    private fun onDown(code: Int) {
        // Any other button pressed while Select or Start is held makes them a modifier, not a tap.
        if (code != SELECT && SELECT in held) { selectUsed = true; handler.removeCallbacks(selectHold) }
        if (code != START && START in held) { startUsed = true; handler.removeCallbacks(startHold) }
        held += code
        when (code) {
            SELECT -> {
                selectUsed = false
                when {
                    L1 in held && R1 in held -> { selectUsed = true; Privacy.combo() }
                    START in held -> { selectUsed = true; startUsed = true; handler.removeCallbacks(startHold); Panels.openSwitcher() }
                    else -> handler.postDelayed(selectHold, HOLD_MS)
                }
            }
            START -> {
                startUsed = false
                if (SELECT in held) { selectUsed = true; startUsed = true; handler.removeCallbacks(selectHold); Panels.openSwitcher() }
                else handler.postDelayed(startHold, HOLD_MS)
            }
            L1, R1 -> if (SELECT in held && L1 in held && R1 in held) Privacy.combo()
        }
    }

    private fun onUp(code: Int) {
        held -= code
        when (code) {
            SELECT -> {
                handler.removeCallbacks(selectHold)
                if (!selectUsed) Buttons.press(Button.Select)
                selectUsed = true
            }
            START -> {
                handler.removeCallbacks(startHold)
                if (!startUsed) Buttons.press(Button.Start)
                startUsed = true
            }
        }
    }

    /** Forget held buttons; their release may never arrive once Tandem is in the background. */
    fun reset() {
        handler.removeCallbacks(selectHold)
        handler.removeCallbacks(startHold)
        held.clear()
        swallow.clear()
        selectUsed = true
        startUsed = true
    }
}

/** When the user last did anything, for the screensaver. */
object Idle {
    @Volatile
    var last = SystemClock.elapsedRealtime()
        private set
    private var swallowTouch = false

    fun poke() {
        last = SystemClock.elapsedRealtime()
        // Motion that stopped for good after a long idle (Ambient.resting) comes back at once.
        io.github.zahakj.tandem.ui.Ambient.onInput()
    }

    /** True when the touch is used to wake the screensaver and must not reach the screen. */
    fun onTouch(ev: MotionEvent): Boolean {
        poke()
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) swallowTouch = Screensaver.active.also { if (it) Screensaver.wake() }
        if (!swallowTouch) return false
        if (ev.actionMasked == MotionEvent.ACTION_UP || ev.actionMasked == MotionEvent.ACTION_CANCEL) swallowTouch = false
        return true
    }

    /** Sticks: only real movement counts, so a drifting stick doesn't keep the screensaver away. */
    fun onMotion(ev: MotionEvent): Boolean {
        if (!ev.isFromSource(InputDevice.SOURCE_JOYSTICK) && !ev.isFromSource(InputDevice.SOURCE_GAMEPAD)) return false
        val axes = intArrayOf(MotionEvent.AXIS_X, MotionEvent.AXIS_Y, MotionEvent.AXIS_Z, MotionEvent.AXIS_RZ, MotionEvent.AXIS_HAT_X, MotionEvent.AXIS_HAT_Y)
        if (axes.none { abs(ev.getAxisValue(it)) > 0.5f }) return false
        poke()
        if (Screensaver.active) { Screensaver.wake(); return true }
        return false
    }
}

object Screensaver {
    var active by mutableStateOf(false)
        private set

    fun start() {
        if (active) return
        active = true
        // A revealed private library (and anything shown on its own) goes back into hiding when nobody is looking.
        Privacy.revealed = false
        HiddenItems.endPeeks()
    }

    fun wake() {
        active = false
        Idle.poke()
    }
}

/**
 * Private mode: hidden games, folders and systems stay hidden until L1 + R1 + Select is pressed, whatever the
 * Settings toggle says. Hidden games (and every game in a hidden folder) are never exported, looked up online or
 * sent anywhere, whether private mode is on or not.
 */
object Privacy {
    var revealed by mutableStateOf(false)

    val enabled: Boolean get() = HomeStore.settings.privateMode

    /** Whether hidden games are shown in the library right now. */
    val showHidden: Boolean get() = if (enabled) revealed else Store.settings.showHidden

    /** Hidden by itself or by a hidden folder or system (see HiddenFolders.kt), whether or not it is showing. */
    fun isHidden(game: Game): Boolean = game.meta.hidden || HiddenFolders.hides(game)

    /** [isHidden] for a game known by its key, like a play session's. */
    fun isHiddenKey(key: String): Boolean = Store.get(key).hidden || HiddenFolders.hidesKey(key)

    /** Hidden games show only while revealed, or one at a time from Start › Hidden (see HiddenItems.kt). */
    fun canShow(game: Game): Boolean = showHidden || !HiddenItems.tucked(game)

    /** [canShow] for a game known by its key, like a play session's. */
    fun canShowKey(key: String): Boolean = showHidden || !HiddenItems.tuckedKey(key)

    /** [canShow] for a ROM folder, or a whole system when [folder] is null. */
    fun canShowFolder(platformId: String, folder: String?): Boolean = showHidden || !HiddenItems.tuckedFolder(platformId, folder)

    /** [canShow] for a folder on the icon-grid home. */
    fun canShowHomeFolder(f: HomeFolder): Boolean = showHidden || !HiddenItems.tuckedHomeFolder(f)

    /** Tucks hidden things away again, whatever brought them out, ending every one shown on its own. */
    fun tuckAway() {
        HiddenItems.endPeeks()
        if (enabled) { if (revealed) combo() }
        else if (Store.settings.showHidden) Store.updateSettings { it.copy(showHidden = false) }
    }

    /**
     * Changes whenever what may be shown changes: hidden games revealed or tucked away, a folder hidden or unhidden,
     * one shown on its own. For remember() keys of anything filtered with [canShow].
     */
    val view: Any get() = View(showHidden, HiddenFolders.rules(), HiddenItems.peeks)

    private data class View(val shown: Boolean, val rules: HideRules, val peeks: Peeks)

    fun combo() {
        // Says nothing when private mode is off, so the combo gives nothing away.
        if (!enabled) return
        revealed = !revealed
        if (!revealed) HiddenItems.endPeeks()
        Toasts.show(if (revealed) "Psst… hidden games are showing" else "Hidden games are tucked away")
        Nav.focusTick++
    }
}
