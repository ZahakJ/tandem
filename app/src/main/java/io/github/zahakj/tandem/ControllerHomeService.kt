package io.github.zahakj.tandem

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import io.github.zahakj.tandem.data.ControllerStore

/**
 * "Controller Home button": an optional accessibility service that makes the Home (guide) button of a Bluetooth or
 * USB controller work everywhere. Many X-input pads (often paired as "Xbox Wireless Controller") send it as
 * KEYCODE_BUTTON_MODE, which Android itself ignores. This service sees only key events (no screen content, no
 * events about other apps), and only ever keeps that one button, and only from external controllers:
 * - tap: Android's Home (Tandem, when it is the Home app);
 * - hold: Android's recent apps, or Tandem's game switcher (Settings › Controls › Hold the Home button).
 * While Tandem itself is in front the button is left to Tandem, which treats it as Home ([HomeButton]).
 * The built-in Odin Controller's buttons always pass straight through.
 *
 * It also watches Back, without keeping it: a press reaches the app as usual. Held for [BACK_HOLD_MS] while another
 * app is in front, the app moves to the other screen ([AppMove.moveFront]) and the rest of that press (its repeats
 * and release) is kept, so the app doesn't also go back. A game in front is left alone unless the user allowed it.
 */
class ControllerHomeService : AccessibilityService() {
    /**
     * The thread this service's callbacks run on. A service that filters key events holds every key on the device
     * (volume keys, a game's buttons) until it answers, and Android asks on the service's main looper: Tandem's UI
     * thread. While that was busy (coming back, a restart, a long frame) every key waited, up to half a second, and
     * the volume changed late. So the service answers from its own thread ([getMainLooper], which
     * AccessibilityService binds its callbacks to); the rest of Tandem's main thread no longer stands in the way.
     */
    private val keys = android.os.HandlerThread("tandem-keys", android.os.Process.THREAD_PRIORITY_DISPLAY).also { it.start() }
    private val handler = Handler(keys.looper)
    private val main = Handler(Looper.getMainLooper())

    override fun getMainLooper(): Looper = keys.looper

    /** The current press is ours (its down was kept), so its release must be kept too. */
    private var mine = false
    private var held = false
    /** A Back press is being watched; [backHeld] once it was held long enough to move the app. */
    private var backWatch = false
    private var backHeld = false

    private val hold = Runnable {
        if (mine && !held) { held = true; main.post { HomeButton.holdAction(this) } }
    }

    private val backHold = Runnable {
        if (!backWatch || backHeld || Gamepads.tandemHasController) return@Runnable
        backHeld = true
        Thread({ AppMove.moveFront(this) }, "tandem-move-app").start()
    }

    /** Whether holding Back may move the app in front now: switched on, two screens, and not a game unless allowed. */
    private fun backMayMove(): Boolean {
        val prefs = io.github.zahakj.tandem.data.AppPrefs.data
        if (!prefs.holdBackMoves || Gamepads.tandemHasController || !LaunchDisplay.dualScreen(this)) return false
        return prefs.moveGames || SecondaryHome.front !is SecondaryHome.Front.Game
    }

    private fun onBack(event: KeyEvent): Boolean {
        when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                if (event.repeatCount > 0) return backHeld
                handler.removeCallbacks(backHold)
                backHeld = false
                backWatch = backMayMove()
                if (backWatch) handler.postDelayed(backHold, BACK_HOLD_MS)
                return false
            }
            KeyEvent.ACTION_UP -> {
                handler.removeCallbacks(backHold)
                val keep = backHeld
                backWatch = false
                backHeld = false
                return keep
            }
        }
        return false
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        // On the key thread (see [keys]): the stores and the companion belong to Tandem's main thread.
        main.post {
            ControllerStore.init(this)
            A11yHeal.connected(this)
            io.github.zahakj.tandem.data.AppPrefs.init(this)
            instance = this
            // Android starts this service again in a new process after Tandem was killed (low memory) or updated: if a
            // game is still playing, its bottom-screen companion went with the old process; bring it back.
            GameCompanion.restore(this)
            // Android's own app list may have taken the bottom screen while Tandem was gone.
            SecondaryHome.replaceStock(this)
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(hold)
        handler.removeCallbacks(backHold)
        main.post { if (instance === this) instance = null }
        keys.quitSafely()
        super.onDestroy()
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK) return onBack(event)
        if (event.keyCode != KeyEvent.KEYCODE_BUTTON_MODE) return false
        when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                if (event.repeatCount > 0) return mine
                // Tandem in front handles it itself (the same as Home there); built-in buttons are never kept.
                mine = !Gamepads.tandemHasController && Gamepads.isExternalId(event.deviceId)
                if (!mine) return false
                held = false
                handler.postDelayed(hold, HomeButton.HOLD_MS)
                return true
            }
            KeyEvent.ACTION_UP -> {
                if (!mine) return false
                mine = false
                handler.removeCallbacks(hold)
                if (!held) performGlobalAction(GLOBAL_ACTION_HOME)
                return true
            }
        }
        return mine
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    companion object {
        /** How long Back is held to move the app in front to the other screen; a normal press is far shorter. */
        const val BACK_HOLD_MS = 600L

        @Volatile
        var instance: ControllerHomeService? = null
            private set

        /** Whether the user switched the service on in Android's Accessibility settings. */
        fun enabled(ctx: Context): Boolean {
            val list = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
            val me = ComponentName(ctx, ControllerHomeService::class.java)
            return list.split(':').any { ComponentName.unflattenFromString(it) == me }
        }
    }
}
