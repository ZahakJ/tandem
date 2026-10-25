package io.github.zahakj.tandem

import android.app.Presentation
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.service.dreams.DreamService
import android.util.Log
import android.view.Display
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import io.github.zahakj.tandem.data.SystemPrefs
import io.github.zahakj.tandem.ui.ScreensaverBottomScene
import io.github.zahakj.tandem.ui.ScreensaverScene
import io.github.zahakj.tandem.ui.TandemTheme
import io.github.zahakj.tandem.ui.Themes
import java.lang.ref.WeakReference
import kotlin.math.abs

/**
 * Tandem's screensaver in Android's own screensaver slot (Settings › Display › Screen saver), so the handheld shows
 * Pip and drifting covers instead of the stock clock while it charges or idles. Any button or touch wakes it.
 *
 * The controller must always be able to wake it. On the AYN Thor the system hands controller input to the
 * presentation window of the app behind the dream (Tandem's bottom screen), not to the dream, so the dream also
 * puts its own window on the bottom screen ([DreamBottom]) and every window of Tandem's that can receive a key
 * checks [Dreaming] first: while the dream runs, the first key or stick move ends it.
 */
class TandemDream : DreamService(), LifecycleOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedState = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry

    private var bottom: DreamBottom? = null
    private var waking = false

    override fun onCreate() {
        super.onCreate()
        // From the very start (before Tandem's activity stops under the dream), so the bottom screen is kept for it.
        Dreaming.started(this)
        io.github.zahakj.tandem.ui.Images.install(this)
        savedState.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        // The process may have been started just for the screensaver: load settings and the theme first.
        io.github.zahakj.tandem.data.Store.init(this)
        SystemPrefs.init(this)
        Themes.init(this)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        isScreenBright = false
        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@TandemDream)
            setViewTreeSavedStateRegistryOwner(this@TandemDream)
            setContent { TandemTheme { ScreensaverScene() } }
        }
        setContentView(view)
        io.github.zahakj.tandem.ui.QuietSemantics.watch(view)
        showBottom()
    }

    /** The dream's own window on the bottom screen, which wakes it on any key, stick move or touch. */
    private fun showBottom() {
        if (bottom != null || SystemPrefs.data.singleScreen) return
        val display = getSystemService(DisplayManager::class.java)
            ?.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)?.firstOrNull() ?: return
        val screen = runCatching { DreamBottom(this, display) }.getOrNull() ?: return
        try {
            screen.show()
            bottom = screen
        } catch (t: Throwable) {
            Log.w("Tandem", "Couldn't show the screensaver on the bottom screen", t)
        }
    }

    private fun hideBottom() {
        bottom?.let { runCatching { it.dismiss() } }
        bottom = null
    }

    /** Ends the screensaver (a key, stick move or touch on any of Tandem's windows). */
    fun wake() {
        if (waking) return
        waking = true
        Log.i("Tandem", "Waking the screensaver from the controller or a touch")
        // wakeUp() lets the dream fade out; finish() is the same without the fade on older Android versions.
        if (Build.VERSION.SDK_INT >= 33) wakeUp() else finish()
    }

    /** Its own window: it ends the dream itself (it isn't interactive); this only notes the press so its release is swallowed. */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        Dreaming.onKey(event)
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (Dreaming.onMotion(event)) return true
        return super.dispatchGenericMotionEvent(event)
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    override fun onDreamingStopped() {
        Dreaming.ended(this)
        hideBottom()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        super.onDreamingStopped()
    }

    override fun onDetachedFromWindow() {
        Dreaming.ended(this)
        hideBottom()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        super.onDetachedFromWindow()
    }

    override fun onDestroy() {
        Dreaming.ended(this)
        hideBottom()
        super.onDestroy()
    }
}

/**
 * The screensaver's window on the bottom screen: a real [Presentation] like the library's bottom screen
 * (ui/BottomScreen.kt), so the device can hand it the controller, and anything that reaches it wakes the dream.
 */
private class DreamBottom(private val dream: TandemDream, display: Display) : Presentation(dream, display) {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setCancelable(false)
        setContentView(
            ComposeView(context).apply {
                setViewTreeLifecycleOwner(dream)
                setViewTreeSavedStateRegistryOwner(dream)
                setContent { TandemTheme { ScreensaverBottomScene() } }
                io.github.zahakj.tandem.ui.QuietSemantics.watch(this)
            },
        )
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        Dreaming.onKey(event)
        if (event.action == KeyEvent.ACTION_DOWN) dream.wake()
        return true
    }

    override fun dispatchGenericMotionEvent(ev: MotionEvent): Boolean {
        Dreaming.onMotion(ev)
        return true
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) dream.wake()
        return true
    }
}

/**
 * Whether Tandem's screensaver ([TandemDream]) is running in this process, and the check every window of Tandem's
 * makes before handling a key or stick move ([onKey], [onMotion]): while it runs, the first one ends it and is
 * used up, and the rest of that press is swallowed once the library has the controls again ([WakeGate]).
 */
object Dreaming {
    private var ref: WeakReference<TandemDream>? = null
    private val gate = WakeGate()
    private val endListeners = LinkedHashSet<() -> Unit>()

    /** The screensaver running right now, if any. */
    val dream: TandemDream? get() = ref?.get()

    val active: Boolean get() = dream != null

    internal fun started(d: TandemDream) {
        ref = WeakReference(d)
    }

    internal fun ended(d: TandemDream) {
        if (ref?.get() !== d) return
        ref = null
        endListeners.toList().forEach { it() }
    }

    /** Runs [listener] each time the screensaver stops. */
    fun addEndListener(listener: () -> Unit) { endListeners += listener }

    fun removeEndListener(listener: () -> Unit) { endListeners -= listener }

    /** True when [e] woke the screensaver or is the rest of the press that did; it must then go no further. */
    fun onKey(e: KeyEvent): Boolean {
        val d = dream
        return when (gate.onKey(d != null, e.action == KeyEvent.ACTION_DOWN, e.keyCode, SystemClock.uptimeMillis())) {
            WakeGate.Verdict.Wake -> { d?.wake(); true }
            WakeGate.Verdict.Swallow -> true
            WakeGate.Verdict.Pass -> false
        }
    }

    /** [onKey] for sticks: only real movement wakes it, so a drifting stick doesn't. Touch and mice are left alone. */
    fun onMotion(ev: MotionEvent): Boolean {
        if (!ev.isFromSource(InputDevice.SOURCE_JOYSTICK) && !ev.isFromSource(InputDevice.SOURCE_GAMEPAD)) return false
        val d = dream
        if (d == null && !gate.swallowing) return false
        val moved = STICK_AXES.any { abs(ev.getAxisValue(it)) > 0.5f }
        return when (gate.onStick(d != null, moved, SystemClock.uptimeMillis())) {
            WakeGate.Verdict.Wake -> { d?.wake(); true }
            WakeGate.Verdict.Swallow -> true
            WakeGate.Verdict.Pass -> false
        }
    }

    private val STICK_AXES = intArrayOf(
        MotionEvent.AXIS_X, MotionEvent.AXIS_Y, MotionEvent.AXIS_Z, MotionEvent.AXIS_RZ, MotionEvent.AXIS_HAT_X, MotionEvent.AXIS_HAT_Y,
    )
}
