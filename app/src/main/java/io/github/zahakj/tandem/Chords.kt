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
import io.github.zahakj.tandem.data.ControllerStore
import io.github.zahakj.tandem.data.HomeFolder
import io.github.zahakj.tandem.data.HomeStore
import io.github.zahakj.tandem.data.Store
import io.github.zahakj.tandem.ui.Nav
import io.github.zahakj.tandem.ui.Panels
import io.github.zahakj.tandem.ui.Toasts
import kotlin.math.abs

/**
 * Controller buttons, from key events to commands (Bindings.kt). MainActivity feeds every key here: a [ChordEngine]
 * works out which command a press, a hold or a chord means, and this carries it out: a direct command (the game
 * switcher on a held Select, the quick panel on a held Start, private mode on L1 + R1 + Select) runs at once, a button
 * command goes through [Buttons] as its logical button, and Open, Back and the controller's Home button go back to
 * the activity as the key they stand for. While the rebind page is listening ([capture]), presses are collected
 * into a new binding instead.
 */
object Chords {
    private val handler = Handler(Looper.getMainLooper())
    private val engine = ChordEngine({ ControllerStore.bindings })
    /** Keys whose remaining events are swallowed (the press that woke the screensaver). */
    private val swallow = HashSet<Int>()
    private val tick = Runnable {
        carry(engine.tick(SystemClock.uptimeMillis()), raw = 0)
        schedule()
    }

    private val capturing = ChordCapture()

    /** While set, the rebind page is listening: each press, chord or hold made goes here once every button is up. */
    var capture: ((Binding) -> Unit)? = null
        set(value) {
            field = value
            capturing.reset()
            captured = emptyList()
            // Whatever was half pressed when listening started must not fire when it is let go.
            if (value != null) engine.reset()
        }

    /** The buttons pressed so far while listening, in order, for the popup. */
    var captured by mutableStateOf<List<Pad>>(emptyList())
        private set

    /**
     * Feeds a key whose code (after the pad's own Nintendo swap) is [code]. Null when the key was used up here;
     * otherwise the key code the activity goes on with: A or B for Open and Back, or the key as it came for a
     * button nothing is bound to and for keys that aren't buttons (the D-pad).
     */
    fun onKey(e: KeyEvent, code: Int): Int? {
        val pad = Pad.of(code) ?: return code
        val now = e.eventTime
        if (e.action == KeyEvent.ACTION_DOWN && e.repeatCount > 0) {
            // Repeats: only a pass-through key keeps going (A held on a tile), as the key it stands for.
            return if (capture == null) engine.passOf(pad)?.let(::passCode) else null
        }
        capture?.let { listener ->
            when (e.action) {
                KeyEvent.ACTION_DOWN -> { capturing.down(pad, now); captured = capturing.sofar }
                KeyEvent.ACTION_UP -> capturing.up(pad, now)?.let { b -> captured = emptyList(); listener(b) }
            }
            return null
        }
        val outs = when (e.action) {
            KeyEvent.ACTION_DOWN -> engine.down(pad, now)
            KeyEvent.ACTION_UP -> engine.up(pad, now)
            else -> return null
        }
        schedule()
        return carry(outs, raw = e.keyCode)
    }

    /** Carries out [outs]; the key code to go on with, or null. [raw] is the key as it came, for an unbound button. */
    private fun carry(outs: List<ChordEngine.Out>, raw: Int): Int? {
        var forward: Int? = null
        for (o in outs) when (o) {
            is ChordEngine.Out.Fire -> fire(o.commands)
            is ChordEngine.Out.Pass -> if (o.command == Command.ControllerHome) HomeButton.press(o.down) else forward = passCode(o.command)
            // A and B that no longer mean Open or Back must not reach the screens as keys either: Compose takes
            // BUTTON_A as select and BUTTON_B as back wherever a key handler looks at it.
            is ChordEngine.Out.Unbound -> if (raw != KeyEvent.KEYCODE_BUTTON_A && raw != KeyEvent.KEYCODE_BUTTON_B) forward = raw
        }
        return forward
    }

    /** The key a pass-through command goes on as; null for the Home button, which [HomeButton] takes. */
    private fun passCode(c: Command): Int? = when (c) {
        Command.Open -> KeyEvent.KEYCODE_BUTTON_A
        Command.Back -> KeyEvent.KEYCODE_BUTTON_B
        else -> null
    }

    /** The commands of one binding, as one press: the direct ones run here, the rest arrive in [Buttons] together. */
    private fun fire(commands: List<Command>) {
        for (c in commands) when (c) {
            Command.QuickPanel -> Panels.toggleQuick()
            Command.Switcher, Command.SwitcherChord -> Panels.openSwitcher()
            Command.Hidden -> Privacy.combo()
            else -> Unit
        }
        val buttons = commands.filter { it.kind == Command.Kind.Button }
        buttons.firstOrNull()?.carrier?.let { Buttons.press(it, buttons.toSet()) }
    }

    private fun schedule() {
        handler.removeCallbacks(tick)
        engine.deadline?.let { handler.postAtTime(tick, it) }
    }

    /**
     * Any button (held or repeating ones too) wakes the in-app screensaver, and the rest of that press is swallowed.
     * True when the event was used up that way. Checked before anything else gets the key, the controller test too.
     */
    fun wakeScreensaver(e: KeyEvent): Boolean {
        val code = if (e.keyCode == KeyEvent.KEYCODE_MENU) KeyEvent.KEYCODE_BUTTON_START else e.keyCode
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

    /** Forget held buttons; their release may never arrive once Tandem is in the background. */
    fun reset() {
        handler.removeCallbacks(tick)
        engine.reset()
        capturing.reset()
        captured = emptyList()
        swallow.clear()
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
 * Private mode: hidden games, folders and systems stay hidden until L1 + R1 + Select and the PIN bring them out,
 * whatever the Settings toggle says, and Tandem stays offline (Offline.kt). Hidden games (and every game in a hidden
 * folder) are never exported, looked up online or sent anywhere, whether private mode is on or not.
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

    /**
     * L1 + R1 + Select. Private mode off: turns it on at once (the panic switch). On: shows hidden games once the PIN
     * is right (PrivacyPin.kt), or tucks them away again, which never asks.
     */
    fun combo() {
        if (!enabled) { PrivateMode.turnOn(); return }
        if (!revealed) { PrivateMode.reveal(); return }
        revealed = false
        HiddenItems.endPeeks()
        Offline.tuckedAway()
        Toasts.show("Hidden games are tucked away")
        Nav.focusTick++
    }
}
