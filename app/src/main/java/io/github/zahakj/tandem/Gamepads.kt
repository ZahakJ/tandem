package io.github.zahakj.tandem

import android.content.Context
import android.content.Intent
import android.hardware.input.InputManager
import android.os.Handler
import android.os.Looper
import android.view.InputDevice
import android.view.KeyEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import io.github.zahakj.tandem.data.ControllerStore
import io.github.zahakj.tandem.ui.Host
import io.github.zahakj.tandem.ui.Panels
import io.github.zahakj.tandem.ui.Prompts

/**
 * Game controllers other than the Thor's own: which ones are connected, their per-controller button swap, and their
 * Home (guide) button. The built-in "Odin Controller" is never touched here (controller passthrough, which plays an
 * external pad as the built-in one, is PadLink.kt).
 */
object Gamepads {
    /** Whether a Tandem window has input focus, so its own key handling gets the controller's buttons. */
    @Volatile
    var tandemFocused = false

    /**
     * Whether Tandem's library is the top resumed activity of the whole device. With two screens, its window keeps
     * focus on its own display while an app on the other display holds the controller, so [tandemFocused] alone
     * can't tell.
     */
    @Volatile
    var tandemTop = false

    /** Tandem really has the controller: its window is focused and it is the top activity. */
    val tandemHasController: Boolean get() = tandemFocused && tandemTop

    private val descriptors = HashMap<Int, String?>()

    fun isPad(d: InputDevice?): Boolean {
        if (d == null || d.isVirtual) return false
        val s = d.sources
        return s and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD || s and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK
    }

    /** The handheld's own controls: the Odin Controller (AYN names it so), or anything not plugged in or paired. */
    fun isBuiltIn(d: InputDevice): Boolean = d.name.orEmpty().contains("odin", ignoreCase = true) || !d.isExternal

    /** A controller connected over Bluetooth or USB. */
    fun isExternal(d: InputDevice?): Boolean = isPad(d) && !isBuiltIn(d!!)

    fun isExternalId(deviceId: Int): Boolean = isExternal(InputDevice.getDevice(deviceId))

    /** The external controllers connected now, one entry each (a pad can show up as several input devices). */
    fun connected(): List<InputDevice> =
        InputDevice.getDeviceIds().toList().mapNotNull { InputDevice.getDevice(it) }.filter { isExternal(it) }.distinctBy { it.descriptor }

    /** Descriptor of an external controller, or null (cached per device id; ids are never reused while running). */
    private fun descriptorOf(deviceId: Int): String? = synchronized(descriptors) {
        descriptors.getOrPut(deviceId) { InputDevice.getDevice(deviceId)?.takeIf { isExternal(it) }?.descriptor }
    }

    /** Bumped when a controller connects, changes or goes away (Settings lists them live). */
    var version by mutableIntStateOf(0)
        private set
    private var watching = false

    /** Follows controllers coming and going while Tandem runs (the listener costs nothing at rest). */
    fun watch(ctx: Context) {
        if (watching) return
        watching = true
        val im = ctx.applicationContext.getSystemService(InputManager::class.java) ?: return
        PadLink.init(ctx)
        im.registerInputDeviceListener(object : InputManager.InputDeviceListener {
            override fun onInputDeviceAdded(deviceId: Int) = changed()
            override fun onInputDeviceRemoved(deviceId: Int) = changed()
            override fun onInputDeviceChanged(deviceId: Int) = changed()
        }, Handler(Looper.getMainLooper()))
    }

    private fun changed() {
        synchronized(descriptors) { descriptors.clear() }
        version++
        PadLink.devicesChanged()
    }

    /**
     * The key code Tandem acts on: the controller's own swap first (Nintendo labels: the button printed A sends
     * BUTTON_B in X-input mode, so it is turned back into A), then the Swap A and B / Swap X and Y settings.
     */
    fun remap(event: KeyEvent): Int {
        var code = event.keyCode
        val swap = ControllerStore.nintendo
        if (swap.isNotEmpty() && code in SwapCodes) {
            val d = descriptorOf(event.deviceId)
            if (d != null && d in swap) code = when (code) {
                KeyEvent.KEYCODE_BUTTON_A -> KeyEvent.KEYCODE_BUTTON_B
                KeyEvent.KEYCODE_BUTTON_B -> KeyEvent.KEYCODE_BUTTON_A
                KeyEvent.KEYCODE_BUTTON_X -> KeyEvent.KEYCODE_BUTTON_Y
                else -> KeyEvent.KEYCODE_BUTTON_X
            }
        }
        return Prompts.remap(code)
    }

    private val SwapCodes = setOf(KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BUTTON_X, KeyEvent.KEYCODE_BUTTON_Y)

    /** The intent extra that opens the game switcher when Tandem comes to the front (a held Home button). */
    const val EXTRA_OPEN = "io.github.zahakj.tandem.OPEN"

    /** Opens Tandem's game switcher, bringing Tandem to the front first. */
    fun openSwitcherFrom(ctx: Context) {
        val i = MainActivity.frontIntent(ctx)
            .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            .putExtra(EXTRA_OPEN, "switcher")
        runCatching { ctx.startActivity(i) }
    }
}

/**
 * A controller's Home (guide) button inside Tandem. Many X-input pads send it as
 * KEYCODE_BUTTON_MODE, which Android ignores; here a tap does exactly what Home does and a hold does what
 * "Hold the Home button" says. Outside Tandem the optional [ControllerHomeService] does the same.
 */
object HomeButton {
    const val HOLD_MS = 550L
    private val handler = Handler(Looper.getMainLooper())
    private var down = false
    private var held = false

    private val hold = Runnable {
        if (down && !held) { held = true; holdAction(null) }
    }

    /** True when the event was the Home button of an external controller (and is used up here). */
    fun onKey(e: KeyEvent): Boolean {
        if (e.keyCode != KeyEvent.KEYCODE_BUTTON_MODE || !Gamepads.isExternalId(e.deviceId)) return false
        when (e.action) {
            KeyEvent.ACTION_DOWN -> if (e.repeatCount == 0) {
                down = true; held = false
                handler.postDelayed(hold, HOLD_MS)
            }
            KeyEvent.ACTION_UP -> {
                handler.removeCallbacks(hold)
                if (down && !held) home()
                down = false
            }
        }
        return true
    }

    /** Tandem lost focus mid-press: the release may never come. */
    fun reset() { handler.removeCallbacks(hold); down = false; held = true }

    /** The same as Android's Home while Tandem is in front (see Host.onNewIntent). */
    fun home() {
        Host.onNewIntent(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), returning = false)
    }

    /** A held Home button: Android's recent apps (through the service, when it is on) or Tandem's game switcher. */
    fun holdAction(ctx: Context?) {
        val service = ControllerHomeService.instance
        if (ControllerStore.settings.holdHome == "recents" && service != null) {
            service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_RECENTS)
        } else if (ctx != null) {
            Gamepads.openSwitcherFrom(ctx)
        } else if (!Panels.switcher) {
            Panels.openSwitcher()
        }
    }
}
