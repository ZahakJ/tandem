package io.github.zahakj.tandem

import android.os.Bundle
import android.os.StrictMode
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableIntStateOf
import androidx.core.view.WindowCompat
import io.github.zahakj.tandem.ui.Music
import io.github.zahakj.tandem.ui.Nav
import io.github.zahakj.tandem.ui.Route
import io.github.zahakj.tandem.ui.ScreenRoles
import io.github.zahakj.tandem.ui.Sounds
import io.github.zahakj.tandem.ui.TextEntry
import io.github.zahakj.tandem.ui.Themes

class MainActivity : ComponentActivity() {
    companion object {
        /** How long the bottom screen stays hidden in the background before it is taken down (see parkBottom). */
        private const val KEEP_BOTTOM_MS = 5 * 60_000L

        /** The running library activity, for windows on the other screen that hand it the controller (SecondaryHomeActivity). */
        @Volatile var live: java.lang.ref.WeakReference<MainActivity>? = null
    }

    private var bottomScreen: io.github.zahakj.tandem.ui.BottomScreen? = null

    /** Bumped on every resume so the UI re-checks permissions the user may have just granted. */
    val resumeCount = mutableIntStateOf(0)

    /** Bumped a few frames after each return (not the first start): only for what must follow at once (ResumeLanding). */
    val returnCount = mutableIntStateOf(0)
    private var pendingLanding: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Before anything is loaded: the window behind the first frame is the last theme's backdrop (StartLook).
        StartLook.paintSaved(window, this)
        live = java.lang.ref.WeakReference(this)
        io.github.zahakj.tandem.ui.Images.install(this)
        io.github.zahakj.tandem.data.Store.init(this)
        GameCompanion.attachApp(this)
        Themes.init(this)
        StartLook.paint(window, StartLook.colorOf(Themes.live))
        Sounds.init(this)
        Music.init(this)
        io.github.zahakj.tandem.data.ScrapeConfig.init(this)
        io.github.zahakj.tandem.data.Scraper.init(this)
        io.github.zahakj.tandem.data.DetailsFill.init(this)
        io.github.zahakj.tandem.data.HomeStore.init(this)
        io.github.zahakj.tandem.data.WidgetStore.init(this)
        io.github.zahakj.tandem.data.AppPrefs.init(this)
        io.github.zahakj.tandem.data.IconGridStore.init(this)
        io.github.zahakj.tandem.ui.CocoonSetup.init(this)
        io.github.zahakj.tandem.data.ExtrasStore.init(this)
        io.github.zahakj.tandem.data.ControllerStore.init(this)
        Gamepads.watch(this)
        RetroAchievements.init(this)
        io.github.zahakj.tandem.ui.DevTools.init(this)
        // Settings' disk and system facts, read in the background so they are ready before Settings opens.
        io.github.zahakj.tandem.ui.Facts.refresh(this)
        // Emulators that take a plain file path get a file:// link; Android refuses those unless this is relaxed.
        StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder().build())
        SystemBars.hide(window)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent { io.github.zahakj.tandem.ui.TandemApp(this) }
        io.github.zahakj.tandem.ui.QuietSemantics.watch(window.decorView)
        // Cold start: the library from the scan cache is read off the main thread while the first frame is built,
        // and that frame waits a moment for it, so the home comes up at once (StartFlow).
        io.github.zahakj.tandem.ui.StartFlow.begin(this, cached = io.github.zahakj.tandem.ui.LibraryScan.startEarly(this))
        Dreaming.addEndListener(dreamEnded)
        openRequested(intent)
    }

    /** A controller's Home button held outside Tandem brings it here to open the game switcher (see [HomeButton]). */
    private fun openRequested(intent: android.content.Intent?) {
        if (intent?.getStringExtra(Gamepads.EXTRA_OPEN) != "switcher") return
        intent.removeExtra(Gamepads.EXTRA_OPEN)
        if (!io.github.zahakj.tandem.ui.Panels.switcher) io.github.zahakj.tandem.ui.Panels.openSwitcher()
    }

    override fun onTopResumedActivityChanged(isTopResumedActivity: Boolean) {
        super.onTopResumedActivityChanged(isTopResumedActivity)
        Gamepads.tandemTop = isTopResumedActivity
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        Gamepads.tandemFocused = hasFocus
        if (!hasFocus) HomeButton.reset()
        if (hasFocus) SystemBars.hide(window)
        // Focus lost while the library stays in front: another app's window may have taken the controller (FocusGuard).
        else focusMayHaveLeft()
    }

    /** This window or Tandem's bottom-screen home lost the focus while the library is in front: check who has it. */
    fun focusMayHaveLeft() {
        if (isResumedNow()) scheduleFocusCheck(home = false, 700)
    }

    fun isResumedNow(): Boolean = lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)

    private val focusChecks = ArrayList<Runnable>()

    /** Checks [delayMs] from now that the library holds the controller, and takes it back if not (FocusGuard). */
    private fun scheduleFocusCheck(home: Boolean, delayMs: Long) {
        val r = object : Runnable {
            override fun run() {
                focusChecks.remove(this)
                FocusGuard.check(this@MainActivity, home)
            }
        }
        focusChecks += r
        handler.postDelayed(r, delayMs)
    }

    private fun cancelFocusChecks() {
        focusChecks.forEach { handler.removeCallbacks(it) }
        focusChecks.clear()
    }

    /** Tandem's bottom-screen home just took the focus back on the bottom display (FocusGuard): the library takes the controls. */
    fun controlsReclaimed() {
        handler.postDelayed({
            if (!isResumedNow()) return@postDelayed
            val screen = bottomScreen
            if (!io.github.zahakj.tandem.data.SystemPrefs.data.singleScreen && (screen == null || !screen.isShowing || !ScreenRoles.bottomShowing)) {
                showDetails()
            } else if (screen != null && ScreenRoles.libraryOnBottom) {
                screen.takeControls()
                screen.watchControls()
            }
            io.github.zahakj.tandem.ui.Nav.focusTick++
        }, 400)
    }

    /** Work waiting for the first frames after coming back (see onStart); cancelled if Tandem leaves again first. */
    private var pendingReturn: (() -> Unit)? = null
    private var pendingMusic: (() -> Unit)? = null
    private var pendingResume: (() -> Unit)? = null

    override fun onStart() {
        super.onStart()
        io.github.zahakj.tandem.ui.PipMind.startIdle()
        // Tandem is in front: its own bottom screen covers the bottom-screen home (SecondaryHome.kt), which gives
        // back the library it showed while an app had the top screen (LibraryGuest). The controls come back at once;
        // what the bottom-screen home draws changes (a whole composition goes) only once Tandem's bottom screen
        // covers it and the return has settled (guestBack), not in the first frames back.
        LibraryGuest.mainStarted(keepShown = true)
        // The Controller Home button service, switched back on if Android turned it off (Shizuku, off the main thread).
        val app = applicationContext
        Thread({ runCatching { A11yHeal.check(app) } }, "tandem-a11y").apply { priority = Thread.MIN_PRIORITY }.start()
        // Back from a game: its companion app closes, or stays running under Tandem's bottom screen (CompanionApps).
        CompanionApps.release()
        io.github.zahakj.tandem.data.SystemPrefs.init(this)
        // SystemPrefs holds the single-screen switch; migrate the legacy Look.singleScreen flag into it.
        if (io.github.zahakj.tandem.data.Store.settings.look.singleScreen) {
            io.github.zahakj.tandem.data.SystemPrefs.update { it.copy(singleScreen = true) }
            io.github.zahakj.tandem.data.Store.updateSettings { it.copy(look = it.look.copy(singleScreen = false)) }
        }
        if (ScreenRoles.ensureLibraryDisplay(this)) return
        // Coming back from a game: the top screen first shows the picture it had, at once. Everything else waits
        // until that is on screen (two frames), so none of it lands in the first frame: play time (which changes
        // the library's details and so what the home screen shows), a new bottom screen (a second window with its
        // own Compose tree; one kept hidden in the background shows at once, below), then the music.
        cancelPending()
        // The session ends now (a game started right away begins a new one); it is recorded a moment later.
        pendingRecord = Sessions.take(this)
        pendingReturn = io.github.zahakj.tandem.ui.AfterFrames.run(frames = 2) {
            pendingReturn = null
            recordSession()
            // An app just moved down to the bottom screen keeps it (AppMove); the parked bottom screen goes.
            if (reuseKeptBottom()) Unit
            else if (AppMove.justMovedDown()) releaseBottom()
            // Already up again (unparked in onStart) stays as it is.
            else if (bottomScreen?.isShowing != true || !ScreenRoles.bottomShowing) showDetails()
        }
        pendingMusic = io.github.zahakj.tandem.ui.AfterFrames.run(frames = 2, delayMs = 220) {
            pendingMusic = null
            Music.resume()
        }
        pendingGuest = io.github.zahakj.tandem.ui.AfterFrames.run(frames = 2, delayMs = 600) {
            pendingGuest = null
            guestBack()
        }
        // The bottom screen kept hidden in the background shows again right after the first frame: the same window
        // and composition, so only a layout pass (not in the top screen's first frame, which composes the most).
        if (!keptForDream && bottomScreen?.parked == true) {
            pendingReturn?.let { later ->
                val unpark = io.github.zahakj.tandem.ui.AfterFrames.run(frames = 1) {
                    if (bottomScreen?.parked == true && !AppMove.justMovedDown()) showDetails()
                }
                pendingReturn = { unpark(); later() }
            }
        }
    }

    private var pendingGuest: (() -> Unit)? = null

    /** The bottom-screen home under Tandem's bottom screen lets the library go (see onStart). */
    private fun guestBack() {
        LibraryGuest.mainStarted()
        SecondaryHome.tandemInFront()
        if (bottomScreen?.isShowing == true) GameCompanion.tandemBelow = true
    }

    private var pendingRecord: (() -> Unit)? = null

    private fun recordSession() {
        val record = pendingRecord ?: return
        pendingRecord = null
        record()
    }

    private fun cancelPending() {
        recordSession()
        pendingReturn?.invoke(); pendingReturn = null
        pendingMusic?.invoke(); pendingMusic = null
        pendingResume?.invoke(); pendingResume = null
        pendingLanding?.invoke(); pendingLanding = null
        // Not waited for any more: the bottom-screen home gives the library back now (onStop may lend it again).
        pendingGuest?.let { cancel -> cancel(); pendingGuest = null; guestBack() }
    }

    /** A game is starting on the bottom screen; the details panel would sit on top of it. */
    fun hideBottomScreen() {
        // A bottom screen still waiting to be shown (Tandem only just came back) must not appear over the game.
        pendingReturn?.let { cancel -> cancel(); pendingReturn = null; recordSession() }
        bottomScreen?.dismiss()
        bottomScreen = null
        GameCompanion.tandemBelow = false
    }

    /** (Re)shows the details screen according to the screen settings; call after changing them. */
    fun showDetails() {
        keptForDream = false
        handler.removeCallbacks(dropParked)
        // Back in front: the bottom screen hidden in the background shows again as it was (no new window).
        val parked = bottomScreen?.takeIf { it.parked }
        if (parked != null && !io.github.zahakj.tandem.data.SystemPrefs.data.singleScreen && parked.unpark()) {
            // Just back: the bottom-screen home underneath is told a moment later (guestBack).
            if (pendingGuest == null) GameCompanion.tandemBelow = true
            GameCompanion.dismiss(afterMs = 450)
            return
        }
        bottomScreen?.dismiss()
        bottomScreen = if (io.github.zahakj.tandem.data.SystemPrefs.data.singleScreen) null else ScreenRoles.showDetails(this)
        if (pendingGuest == null || bottomScreen == null) GameCompanion.tandemBelow = bottomScreen != null
        // Back from a game: the companion on the bottom screen (GameCompanion.kt) goes once the details cover it, so
        // Android's own bottom-screen launcher never shows in between.
        GameCompanion.dismiss(afterMs = if (bottomScreen != null) 450 else 0)
    }

    /** Applies a change to the swap-screens setting right away. */
    fun applyScreenRoles() {
        if (!ScreenRoles.ensureLibraryDisplay(this) && bottomScreen == null) showDetails()
    }

    override fun onResume() {
        super.onResume()
        ActiveHost.token = this
        // Screens re-check permissions, the Home role and so on when this changes; that is recomposition, so it
        // waits until the first frames are drawn. The first resume (startup) counts at once.
        if (resumeCount.intValue == 0) resumeCount.intValue = 1
        else {
            pendingResume?.invoke()
            pendingLanding?.invoke()
            pendingLanding = io.github.zahakj.tandem.ui.AfterFrames.run(frames = 3) {
                pendingLanding = null
                returnCount.intValue++
            }
            // Well after the return has settled: re-checking recomposes the home (and Settings, kept composed).
            pendingResume = io.github.zahakj.tandem.ui.AfterFrames.run(frames = 3, delayMs = 650) {
                pendingResume = null
                resumeCount.intValue++
                io.github.zahakj.tandem.ui.Facts.refresh(this)
            }
        }
        // Any Home press from here on happens while Tandem is in front (see onNewIntent).
        stoppedSinceIntent = false
        Sounds.hush(900)
        io.github.zahakj.tandem.ui.Nav.focusTick++
        SystemBars.hide(window)
        // Back in front (from a game that ended, say): once the bottom screen is up and the companion gone, make sure no
        // other app's window kept the controller on the bottom display (FocusGuard).
        scheduleFocusCheck(home = false, 1_000)
        scheduleFocusCheck(home = false, 2_500)
    }

    override fun onPause() {
        cancelFocusChecks()
        super.onPause()
    }

    override fun onStop() {
        io.github.zahakj.tandem.ui.PipMind.stopIdle()
        // The next cold start opens in this theme's colour (a no-op unless the theme changed).
        StartLook.remember(this, Themes.target)
        stoppedSinceIntent = true
        // Whatever Tandem opened on the bottom screen, the next time it is in front it owns that screen again.
        FocusGuard.bottomAppStarted = false
        // Left again before the return work ran: play time still counts (cancelPending records it); the rest
        // isn't needed any more.
        cancelPending()
        Sounds.fadeTail()
        io.github.zahakj.tandem.ui.GameJingles.stopNow()
        Music.pause()
        val screen = bottomScreen
        if (WakeGate.keepBottomScreen(Dreaming.active, screen?.isShowing == true, io.github.zahakj.tandem.data.SystemPrefs.data.singleScreen)) {
            // Only Tandem's screensaver covers the top screen: the bottom screen stays, as the window this device hands
            // the controller to, and wakes it (see Dreaming). Taken down if Tandem doesn't come back after it.
            keptForDream = true
        } else {
            keptForDream = false
            parkBottom()
        }
        // With an app on the top screen, the bottom-screen home can show this library now (LibraryGuest). An app
        // that came up some other way (Recents, a notification, moved by another tool) isn't known yet: ask once.
        LibraryGuest.mainStopped(this)
        if (SecondaryHome.front == null && !Dreaming.active) SecondaryHome.lookAtFront(this)
        super.onStop()
    }

    override fun onDestroy() {
        if (live?.get() === this) live = null
        LibraryGuest.mainGone(this)
        Dreaming.removeEndListener(dreamEnded)
        handler.removeCallbacks(dropKept)
        handler.removeCallbacks(dropParked)
        bottomScreen?.dismiss()
        bottomScreen = null
        keptForDream = false
        super.onDestroy()
    }

    /** The bottom screen stayed up while Tandem's screensaver covered the top screen (see onStop). */
    private var keptForDream = false
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    /** Tandem didn't come back after the screensaver (the screen went off, another app came up): as after any stop. */
    private val dropKept = Runnable {
        if (!keptForDream || lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) return@Runnable
        keptForDream = false
        parkBottom()
    }

    /**
     * Tandem left the front: its bottom screen is hidden, not taken down ([BottomScreen.park]), so coming back shows
     * it again at once. It goes for good after [KEEP_BOTTOM_MS] away, or when Android is short of memory.
     */
    private fun parkBottom() {
        bottomScreen?.park()
        // A companion opened for a game just started is uncovered now, and plays its entrance.
        GameCompanion.tandemBelow = false
        handler.removeCallbacks(dropParked)
        if (bottomScreen != null) handler.postDelayed(dropParked, KEEP_BOTTOM_MS)
    }

    /** Takes a parked bottom screen down (it is made afresh next time). */
    private fun releaseBottom() {
        handler.removeCallbacks(dropParked)
        val screen = bottomScreen?.takeIf { it.parked } ?: return
        screen.dismiss()
        bottomScreen = null
    }

    private val dropParked = Runnable {
        if (!lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) releaseBottom()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // Short of memory while away (a game running): the hidden bottom screen goes first.
        @Suppress("DEPRECATION")
        val pressed = level >= android.content.ComponentCallbacks2.TRIM_MEMORY_BACKGROUND ||
            level in android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW..android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL
        if (pressed && !lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) releaseBottom()
    }

    private val dreamEnded: () -> Unit = {
        handler.removeCallbacks(dropKept)
        if (keptForDream) handler.postDelayed(dropKept, 2_500)
    }

    /**
     * Back after the screensaver with the bottom screen kept under it: it takes the controls again at once, with no new
     * window. False when there is nothing to reuse (the bottom screen is then shown afresh, as after a game).
     */
    private fun reuseKeptBottom(): Boolean {
        if (!keptForDream) return false
        keptForDream = false
        handler.removeCallbacks(dropKept)
        val screen = bottomScreen
        if (screen == null || !screen.isShowing || !ScreenRoles.bottomShowing || io.github.zahakj.tandem.data.SystemPrefs.data.singleScreen) return false
        GameCompanion.tandemBelow = true
        screen.afterDream()
        return true
    }

    /** Home pressed while Tandem is the Home app. */
    /** Set when Tandem goes to the background, so the next Home press counts as coming back, not a second press. */
    private var stoppedSinceIntent = false

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        io.github.zahakj.tandem.ui.Host.onNewIntent(intent, returning = stoppedSinceIntent)
        stoppedSinceIntent = false
        openRequested(intent)
        if (intent.hasCategory(android.content.Intent.CATEGORY_HOME) || intent.hasCategory(android.content.Intent.CATEGORY_LAUNCHER)) {
            ensureLibraryWorks()
        }
    }

    /**
     * Home: whatever happened before (a cold start whose library never loaded, a bottom screen that went away), the
     * library is loaded, on show and holding the controls afterwards. Changes nothing when all is well.
     */
    private fun ensureLibraryWorks() {
        io.github.zahakj.tandem.ui.LibraryScan.ensureLoaded(this)
        // Home means Tandem gets the controller, wherever Android put the focus (FocusGuard): even with an app Tandem
        // opened on the bottom screen, which the bottom screen covers again now.
        FocusGuard.bottomAppStarted = false
        scheduleFocusCheck(home = true, 600)
        // Coming back (onStart) already shows the bottom screen, a couple of frames later.
        if (!lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED) || pendingReturn != null) return
        val screen = bottomScreen
        if (!io.github.zahakj.tandem.data.SystemPrefs.data.singleScreen && (screen == null || !screen.isShowing || !ScreenRoles.bottomShowing)) {
            showDetails()
        } else if (ScreenRoles.libraryOnBottom) {
            screen?.takeControls()
        }
    }

    /** The library should be on the bottom screen, but this activity has no bottom screen showing: show it again. */
    private fun bottomScreenMissing() {
        if (pendingReturn != null || !lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) return
        if (io.github.zahakj.tandem.data.SystemPrefs.data.singleScreen) return
        android.util.Log.w("Tandem", "The library's bottom screen was missing; showing it again")
        showDetails()
    }

    override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean {
        if (ev.actionMasked == android.view.MotionEvent.ACTION_DOWN) LaunchFrom.touch(bottom = false)
        return Idle.onTouch(ev) || super.dispatchTouchEvent(ev)
    }

    override fun dispatchGenericMotionEvent(ev: android.view.MotionEvent): Boolean {
        // Tandem's screensaver first (Android's or the in-app one): a stick move wakes it, whatever screen is open.
        if (Dreaming.onMotion(ev)) return true
        if (Idle.onMotion(ev)) return true
        if (io.github.zahakj.tandem.ui.PadTest.active && io.github.zahakj.tandem.ui.PadTest.onMotion(ev)) return true
        triggerAxes(ev)
        return super.dispatchGenericMotionEvent(ev)
    }

    /** Devices whose L2 / R2 send key events; their trigger axes are then ignored, so a pull never fires twice. */
    private val triggerKeys = HashSet<Int>()
    private val triggerHeld = BooleanArray(2)

    /** Analog triggers without key events: a pull past half way is one L2 / R2 press. */
    private fun triggerAxes(ev: android.view.MotionEvent) {
        if (!ev.isFromSource(android.view.InputDevice.SOURCE_JOYSTICK) || ev.deviceId in triggerKeys) return
        val values = floatArrayOf(
            maxOf(ev.getAxisValue(android.view.MotionEvent.AXIS_LTRIGGER), ev.getAxisValue(android.view.MotionEvent.AXIS_BRAKE)),
            maxOf(ev.getAxisValue(android.view.MotionEvent.AXIS_RTRIGGER), ev.getAxisValue(android.view.MotionEvent.AXIS_GAS)),
        )
        for (i in 0..1) {
            val pulled = values[i] > if (triggerHeld[i]) 0.3f else 0.6f
            if (pulled && !triggerHeld[i]) Buttons.press(if (i == 0) Button.L2 else Button.R2)
            triggerHeld[i] = pulled
        }
    }

    /** L2 / R2 in long lists: [steps] moves at once in [direction] (a D-pad key code), as quick taps. */
    fun pageJump(direction: Int, steps: Int) {
        val now = android.os.SystemClock.uptimeMillis()
        repeat(steps.coerceIn(1, 30)) { i ->
            deliver(KeyEvent(now, now, KeyEvent.ACTION_DOWN, direction, if (i == 0) 0 else 1))
            deliver(KeyEvent(now, now, KeyEvent.ACTION_UP, direction, 0))
        }
        Sounds.move()
    }

    private fun deliver(event: KeyEvent): Boolean = guestKey(event) ?: (forwardPlain(event) || super.dispatchKeyEvent(event))

    /**
     * An app has the top screen and the bottom-screen home shows the library (LibraryGuest): every key, Back too,
     * goes to the library there once it has been through this activity's buttons and swaps. Null otherwise.
     */
    private fun guestKey(event: KeyEvent): Boolean? {
        if (lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) return null
        return LibraryGuest.host?.libraryKey(event)
    }

    /** Controller A selects and B goes back, the same as on a console. A/B and X/Y can be swapped in Appearance. */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) LaunchFrom.key()
        // Tandem's screensaver in Android's slot (TandemDream) is running: the first key ends it, and that press does
        // nothing else (its release arrives here once the library is back).
        if (Dreaming.onKey(event)) return true
        // The in-app screensaver: any button wakes it, even on the controller test, which otherwise sees every key.
        if (Chords.wakeScreensaver(event)) return true
        // The controller test sees every button, B and Start included.
        if (io.github.zahakj.tandem.ui.PadTest.active) return io.github.zahakj.tandem.ui.PadTest.onKey(event)
        // Holds and combos (switcher, quick panel, private mode), and waking the screensaver.
        if (Chords.onKey(event)) return true
        // A controller's Home (guide) button, sent as BUTTON_MODE: the same as Home.
        if (HomeButton.onKey(event)) return true
        // The controller's own swap (Nintendo labels), then Swap A and B / Swap X and Y.
        return afterSwaps(event, Gamepads.remap(event))
    }

    /**
     * A tapped button hint (ui/Touch.kt): [code] is the button it shows, with every swap already applied (A, B or a
     * D-pad arm). It is pressed and released through the same path as the real button.
     */
    fun tapKey(code: Int) {
        val now = android.os.SystemClock.uptimeMillis()
        afterSwaps(KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0), code)
        afterSwaps(KeyEvent(now, now, KeyEvent.ACTION_UP, code, 0), code)
    }

    /** The rest of [dispatchKeyEvent], once [code] is the button [event] stands for. */
    private fun afterSwaps(event: KeyEvent, code: Int): Boolean {
        val button = when (code) {
            KeyEvent.KEYCODE_BUTTON_X -> Button.X
            KeyEvent.KEYCODE_BUTTON_Y -> Button.Y
            KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_MENU -> Button.Start
            KeyEvent.KEYCODE_BUTTON_SELECT -> Button.Select
            KeyEvent.KEYCODE_BUTTON_L1 -> Button.L1
            KeyEvent.KEYCODE_BUTTON_R1 -> Button.R1
            KeyEvent.KEYCODE_BUTTON_L2 -> Button.L2
            KeyEvent.KEYCODE_BUTTON_R2 -> Button.R2
            else -> null
        }
        if (button != null) {
            if (button == Button.L2 || button == Button.R2) triggerKeys += event.deviceId
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) Buttons.press(button)
            return true
        }
        // Nothing highlighted (after touch, say): the first D-pad press brings the highlight back.
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0 && code in DPAD_KEYS) io.github.zahakj.tandem.ui.FocusCards.rescue()
        // A held D-pad: the highlight moves on every repeat, the details follow it more calmly (Selection.follow).
        if (isDpad(code)) Selection.holding(event.action == KeyEvent.ACTION_DOWN && event.repeatCount > 0)
        if (event.action == KeyEvent.ACTION_DOWN) when (code) {
            KeyEvent.KEYCODE_DPAD_UP -> Sounds.onDirection(0, -1, event.repeatCount)
            KeyEvent.KEYCODE_DPAD_DOWN -> Sounds.onDirection(0, 1, event.repeatCount)
            KeyEvent.KEYCODE_DPAD_LEFT -> Sounds.onDirection(-1, 0, event.repeatCount)
            KeyEvent.KEYCODE_DPAD_RIGHT -> Sounds.onDirection(1, 0, event.repeatCount)
        }
        // Pip glances the way you move.
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) when (code) {
            KeyEvent.KEYCODE_DPAD_UP -> io.github.zahakj.tandem.ui.PipMind.look(0, -1)
            KeyEvent.KEYCODE_DPAD_DOWN -> io.github.zahakj.tandem.ui.PipMind.look(0, 1)
            KeyEvent.KEYCODE_DPAD_LEFT -> io.github.zahakj.tandem.ui.PipMind.look(-1, 0)
            KeyEvent.KEYCODE_DPAD_RIGHT -> io.github.zahakj.tandem.ui.PipMind.look(1, 0)
        }
        val mapped = when (code) {
            KeyEvent.KEYCODE_BUTTON_A -> KeyEvent.KEYCODE_DPAD_CENTER
            KeyEvent.KEYCODE_BUTTON_B -> KeyEvent.KEYCODE_BACK
            else -> if (code == event.keyCode) {
                // Held D-pad or stick: after a moment, moves come twice as fast, then three times.
                if (event.action == KeyEvent.ACTION_DOWN && isDpad(code) && event.repeatCount >= 10) {
                    repeat(if (event.repeatCount >= 28) 2 else 1) { deliver(event) }
                }
                return deliver(event)
            } else code
        }
        if (mapped == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0 && canGoBack()) Sounds.back()
        val out = KeyEvent(
            event.downTime, event.eventTime, event.action, mapped, event.repeatCount,
            event.metaState, event.deviceId, event.scanCode, event.flags, event.source,
        )
        // Swapped screens: the library is on the bottom screen, so its D-pad and A go there. Back still goes
        // through the activity, whose Back dispatcher the library's handlers are registered with.
        guestKey(out)?.let { return it }
        if (mapped != KeyEvent.KEYCODE_BACK && ScreenRoles.libraryOnBottom && toBottom(out)) return true
        return super.dispatchKeyEvent(out)
    }

    /** Swapped screens: a D-pad key that isn't remapped still belongs to the library on the bottom screen. */
    private fun forwardPlain(event: KeyEvent): Boolean =
        ScreenRoles.libraryOnBottom && event.keyCode != KeyEvent.KEYCODE_BACK && toBottom(event)

    /** Hands [event] to the library on the bottom screen; true when it used it. */
    private fun toBottom(event: KeyEvent): Boolean {
        val screen = bottomScreen
        if (screen == null) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) bottomScreenMissing()
            return false
        }
        return screen.forwardKey(event)
    }

    private fun isDpad(code: Int) = code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN || code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT

    private fun canGoBack() =
        Nav.route != Route.Home || Nav.menuFor != null || Nav.search || Nav.groupMenu || TextEntry.request != null
}

/** Keys that move or select: the ones that should always show a highlight. */
private val DPAD_KEYS = setOf(
    KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_BUTTON_A,
)
