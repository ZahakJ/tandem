package io.github.zahakj.tandem

import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Display
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import io.github.zahakj.tandem.data.SystemPrefs
import io.github.zahakj.tandem.ui.HomeMode
import io.github.zahakj.tandem.ui.SecondaryHomeScreen
import io.github.zahakj.tandem.ui.TandemTheme
import io.github.zahakj.tandem.ui.Themes

/**
 * What the bottom screen shows when nothing else is there: Tandem's own secondary-display Home.
 *
 * When Tandem isn't drawing on the bottom screen (an app such as DroidDeck or a browser is in front on the top one,
 * so [MainActivity] stopped and took its bottom-screen presentation with it), Android shows its "secondary home"
 * there. Android 10+ looks for it in the default Home app first: an activity with MAIN + SECONDARY_HOME + DEFAULT
 * that isn't singleTask / singleInstance (RootWindowContainer.resolveSecondaryHomeActivity and
 * canStartHomeOnDisplayArea). Only without one does it use config_secondaryHomePackage, which on the Thor is
 * launcher3's app list (com.android.launcher3/.secondarydisplay.SecondaryDisplayLauncher). So while Tandem is the
 * default Home, [SecondaryHomeActivity] is that screen; when another Home app is chosen, Android stops using it by
 * itself and the stock behaviour returns.
 *
 * Everything else still goes on top of it: Tandem's own presentation (a presentation window sits above activity
 * windows; underneath it this draws only a plain background), the game companion ([CompanionActivity], started
 * later, so above), and emulators that use the bottom screen themselves (their windows are started later too).
 *
 * As a backup (an update installed without a reboot leaves the stock list running as the home of that display until
 * the next boot, and some builds force the stock launcher there), Tandem opens this activity itself on the bottom
 * screen just before it starts an app or a game when no instance of it is alive ([beforeLeave]), the same way the
 * companion is opened before a game. Android's own list is still there when asked for: the "Android apps" chip here
 * and "Android home" in All apps open it on purpose ([openStockBottom], [openStockHome]).
 */
object SecondaryHome {
    private const val TAG = "Tandem"
    private const val PREFS = "secondary_home"
    /** A backup open that hasn't arrived yet (onCreate comes a moment later) still counts for this long. */
    private const val COVER_PENDING_MS = 5_000L

    /** Packages that are never "the app in front" here: the system bars and so on. */
    private val NOT_APPS = setOf("com.android.systemui", "android")

    /** Instances alive in this process (the system's home on the bottom display, and Tandem's backup one). */
    @Volatile private var alive = 0
    @Volatile private var coverAt = 0L
    private val main by lazy { Handler(Looper.getMainLooper()) }

    /**
     * The app last seen in front on the top screen: an Android app ([Front.App]), a game ([Front.Game], whose name
     * is never shown here: it could be a hidden one), or Tandem / nothing (null).
     */
    sealed class Front {
        data class App(val pkg: String) : Front()
        data class Game(val pkg: String?) : Front()
    }

    var front by mutableStateOf<Front?>(null)
        private set

    /**
     * Called just before Tandem starts something on the top screen: [pkg] (an app, or the emulator of a game when
     * [game]). Remembers it for the panel, and when no bottom-screen home of Tandem's is alive (or Android's own list
     * was opened down there on purpose), opens one there first, so whatever starts next is above it and the stock
     * list never shows. Only while Tandem is the default Home with a second screen; otherwise nothing is forced.
     * Safe from any thread; call it before [GameCompanion.show] so the companion lands above it.
     */
    fun beforeLeave(ctx: Context, pkg: String?, game: Boolean = false) {
        front = when {
            game -> Front.Game(pkg)
            pkg.isNullOrEmpty() -> null
            else -> Front.App(pkg)
        }
        val app = ctx.applicationContext
        val stockOpen = stockBottomOpen(app)
        if (!stockOpen && (alive > 0 || SystemClock.elapsedRealtime() - coverAt < COVER_PENDING_MS)) return
        if (SystemPrefs.data.singleScreen || !runCatching { HomeMode.isDefault(app) }.getOrDefault(false)) return
        val display = LaunchDisplay.secondary(app) ?: return
        cover(app, display.displayId)
    }

    /** Opens [SecondaryHomeActivity] on display [displayId], without focus and without an animation. */
    private fun cover(ctx: Context, displayId: Int, why: String = "none was alive") {
        coverAt = SystemClock.elapsedRealtime()
        val intent = Intent(ctx, SecondaryHomeActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        val options = ActivityOptions.makeCustomAnimation(ctx, 0, 0).setLaunchDisplayId(displayId).toBundle()
        runCatching { ctx.startActivity(intent, options) }
            .onSuccess { Log.i(TAG, "Bottom-screen home opened by Tandem ($why)") }
            .onFailure { Log.w(TAG, "Couldn't open the bottom-screen home", it) }
    }

    /**
     * Another app's window holds the focus on the bottom display, under Tandem's bottom screen (see [FocusGuard]):
     * brings this screen forward there, so it takes the focus (and hands the keys to the library) and the bars hide.
     */
    fun reclaim(ctx: Context, displayId: Int) {
        cover(ctx, displayId, "to take the focus back from another app")
    }

    /**
     * After Tandem's process was killed or crashed, Android puts its own bottom-screen launcher (launcher3's app list)
     * there until something else covers it. Called when Tandem starts again (the Controller Home button service is
     * the first part Android brings back): if that list holds the bottom screen, this screen takes it back.
     */
    fun replaceStock(ctx: Context) {
        val app = ctx.applicationContext
        Thread({
            if (!Shell.waitReady(5_000)) return@Thread
            val display = LaunchDisplay.secondary(app)?.displayId ?: return@Thread
            val owner = FocusGuard.parse(Shell.run("sh", "-c", FocusGuard.COMMAND))?.byDisplay?.get(display)
            val stock = stockBottom(app)?.packageName ?: return@Thread
            if (owner == stock) Handler(Looper.getMainLooper()).post { cover(app, display, "Android's app list had the bottom screen") }
        }, "tandem-bottom-check").start()
    }

    /** Tandem is in front again (its own bottom screen covers this one, and the library goes back up). */
    fun tandemInFront() {
        front = null
    }

    internal fun attach() { alive++ }
    internal fun detach() { alive = (alive - 1).coerceAtLeast(0) }

    /** This screen is showing again, so nothing (Android's list included) is above it any more. */
    internal fun shown(a: SecondaryHomeActivity) {
        setStockBottomOpen(a, false)
        lookAtFront(a)
    }

    /**
     * Asks Android which app is in front on the top screen, once (through Shizuku, off the main thread), so an app
     * opened some other way (Recents, a notification, DroidDeck) shows too. Without Shizuku it keeps what Tandem
     * itself started.
     */
    fun lookAtFront(ctx: Context) {
        val app = ctx.applicationContext
        Thread({
            val dump = if (Shell.ready()) Shell.run("dumpsys", "activity", "activities") else null
            val pkg = dump?.let { CompanionRestore.frontPackage(it, Display.DEFAULT_DISPLAY) } ?: return@Thread
            val seen = classify(pkg, app.packageName, isGame(app, pkg))
            main.post { front = seen }
        }, "bottom-home-front").start()
    }

    /** What [pkg], in front on the top screen, comes to here. Plain, for tests. */
    fun classify(pkg: String?, own: String, game: Boolean): Front? = when {
        pkg.isNullOrEmpty() || pkg == own || pkg in NOT_APPS -> null
        game -> Front.Game(pkg)
        else -> Front.App(pkg)
    }

    /** An emulator Tandem knows, one that uses both screens, or the one the open play session is in. */
    private fun isGame(ctx: Context, pkg: String): Boolean {
        (front as? Front.Game)?.let { if (it.pkg == pkg) return true }
        if (AndroidApps.isEmulator(pkg) || GameCompanion.usesBothScreens("", pkg)) return true
        if (runCatching { pkg in RunningGames.emulatorPackages() }.getOrDefault(false)) return true
        return Sessions.openKey(ctx) != null && CompanionRestore.load(ctx)?.pkg == pkg
    }

    // ---- Android's own launcher, on purpose ----

    /**
     * The system launcher's Home activity on the top screen (launcher3, the Thor's own), never Tandem nor Android's
     * "FallbackHome" from Settings. Null when there is none.
     */
    fun stockHome(ctx: Context): ComponentName? = stock(ctx, Intent.CATEGORY_HOME)

    /** The system's own bottom-screen launcher (launcher3's app list). Null when there is none. */
    fun stockBottom(ctx: Context): ComponentName? = stock(ctx, Intent.CATEGORY_SECONDARY_HOME)

    private fun stock(ctx: Context, category: String): ComponentName? {
        val list = runCatching {
            ctx.packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(category), PackageManager.MATCH_SYSTEM_ONLY)
        }.getOrDefault(emptyList())
        val (pkg, cls) = pickStock(list.map { it.activityInfo.packageName to it.activityInfo.name }, ctx.packageName) ?: return null
        return ComponentName(pkg, cls)
    }

    /**
     * From (package, activity) candidates in the system's order: launcher3 first, else the first other one; never
     * Tandem, Android's resolver or Settings' FallbackHome. Plain, for tests.
     */
    fun pickStock(candidates: List<Pair<String, String>>, own: String): Pair<String, String>? {
        val usable = candidates.filter { (pkg, _) -> pkg != own && pkg != "android" && pkg != "com.android.settings" }
        return usable.firstOrNull { it.first == LAUNCHER3 } ?: usable.firstOrNull()
    }

    /**
     * Opens Android's own Home screen on the top screen, on purpose (All apps › Android home). It opens as a plain
     * app, not as the Home: Home still brings Tandem back.
     */
    fun openStockHome(ctx: Context): Boolean {
        val c = stockHome(ctx) ?: return false
        // MAIN + LAUNCHER, not HOME: Android treats it as an ordinary app then, and Tandem stays the Home.
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(c)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        beforeLeave(ctx, c.packageName)
        val options = ActivityOptions.makeBasic().setLaunchDisplayId(Display.DEFAULT_DISPLAY).toBundle()
        return runCatching { ctx.startActivity(intent, options) }
            .onFailure { Log.w(TAG, "Couldn't open Android's own Home", it) }.isSuccess
    }

    /**
     * Opens Android's own app list on the bottom screen, on purpose (the "Android apps" chip on this screen). It
     * goes above this one; the next time Tandem starts something, Tandem's bottom screen goes back on top of it.
     */
    fun openStockBottom(a: SecondaryHomeActivity): Boolean {
        val c = stockBottom(a) ?: return false
        val display = a.display?.displayId ?: return false
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(c)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val options = ActivityOptions.makeBasic().setLaunchDisplayId(display).toBundle()
        return runCatching { a.startActivity(intent, options) }
            .onSuccess { setStockBottomOpen(a, true) }
            .onFailure { Log.w(TAG, "Couldn't open Android's own app list", it) }.isSuccess
    }

    private fun stockBottomOpen(ctx: Context): Boolean =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("stockBottom", false)

    private fun setStockBottomOpen(ctx: Context, open: Boolean) {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean("stockBottom", false) != open) prefs.edit().putBoolean("stockBottom", open).apply()
    }

    const val LAUNCHER3 = "com.android.launcher3"
}

/**
 * Tandem's bottom-screen Home (see [SecondaryHome]). Android can make this display the focused one (a Home started
 * here, a touch), so its window takes keys, but while Tandem is in front it hands every key and stick move to the
 * library ([MainActivity]): the controller can never end up on a window that ignores it. Out of Recents, in its own task, and not singleTask / singleInstance (Android
 * refuses those as a secondary Home).
 */
class SecondaryHomeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setWindowAnimations(0)
        io.github.zahakj.tandem.ui.Images.install(this)
        // At boot this often starts before the library: from its very first frame the same backdrop colour (StartLook).
        StartLook.paintSaved(window, this)
        // Often the first thing of Tandem's to start after a reboot: load what the screen reads.
        io.github.zahakj.tandem.data.Store.init(this)
        SystemPrefs.init(this)
        io.github.zahakj.tandem.data.HomeStore.init(this)
        PinVault.init(this)
        io.github.zahakj.tandem.data.AppPrefs.init(this)
        io.github.zahakj.tandem.data.ExtrasStore.init(this)
        if (!Themes.initialized) Themes.init(this)
        StartLook.paint(window, StartLook.colorOf(Themes.live))
        WindowCompat.setDecorFitsSystemWindows(window, false)
        SystemBars.hide(window)
        // A Home never closes itself: Back here does nothing.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) { override fun handleOnBackPressed() = Unit })
        SecondaryHome.attach()
        setContent { TandemTheme { SecondaryHomeScreen(this) } }
        io.github.zahakj.tandem.ui.QuietSemantics.watch(window.decorView)
        // Android often answers a Home press on this screen with a new instance rather than onNewIntent: with a game
        // running, that press is handled the same way.
        if (savedInstanceState == null && intent?.hasCategory(Intent.CATEGORY_SECONDARY_HOME) == true && GameCompanion.up()) homeOverGame()
    }

    /**
     * Home pressed down here with a game running, so an app had this screen (the companion never takes the focus):
     * the game and its companion come back, or (pressed again, or when the game can't be found) Tandem. Never this
     * screen's own panel, which would leave the game half-covered.
     */
    private fun homeOverGame() {
        if (HomeBack.justBack() || !HomeBack.tryBack(this, fromBottom = true) { GameCompanion.backToTandem(this) }) GameCompanion.backToTandem(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Home pressed while this screen has the focus (an app was open down here): Home means Tandem, not this
        // panel. With a game running, the first Home brings the game and its companion back (HomeBack.kt) and a
        // second one goes to Tandem.
        if (intent.hasCategory(Intent.CATEGORY_SECONDARY_HOME)) {
            if (!GameCompanion.up()) {
                if (!GameCompanion.recentlyShown()) { GameCompanion.backToTandem(this); return }
            } else {
                homeOverGame()
                return
            }
        }
        SecondaryHome.lookAtFront(this)
    }

    /** The library, when it is the app in front: keys that land here belong to it. */
    private fun library(): MainActivity? =
        MainActivity.live?.get()?.takeIf { it.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED) }

    /** The library this screen shows while an app has the top screen ([LibraryGuest]), stopped behind that app. */
    private fun guest(): MainActivity? = if (LibraryGuest.host === this) LibraryGuest.idleMain?.get() else null

    /** Whether Tandem is the default Home, checked on every resume: only then does this screen show the library. */
    var defaultHome by mutableStateOf(true)
        private set

    // A key or stick move wakes Tandem's screensaver (see Dreaming); otherwise, with Tandem in front, it goes to the
    // library as if pressed there. With an app in front, the library shown here takes it through the library
    // activity's own handling (A, B, buttons, swaps), which hands it back to [libraryKey]. Without the library, this
    // screen's own chips (Back to Tandem, Android apps) take it.
    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean =
        Dreaming.onKey(event) || (library() ?: guest())?.dispatchKeyEvent(event) ?: super.dispatchKeyEvent(event)

    /** A key for the library shown here, already handled as the library activity would: delivered to this window. */
    fun libraryKey(event: android.view.KeyEvent): Boolean = super.dispatchKeyEvent(event)

    // Sticks over the library shown here aren't handed on: unused, Android turns them into D-pad keys for this
    // window, which then go the way above.
    override fun dispatchGenericMotionEvent(ev: android.view.MotionEvent): Boolean =
        Dreaming.onMotion(ev) || library()?.dispatchGenericMotionEvent(ev) ?: super.dispatchGenericMotionEvent(ev)

    override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean {
        // An app set to Auto, started from the library down here by touch, opens down here.
        if (ev.actionMasked == android.view.MotionEvent.ACTION_DOWN) LaunchFrom.touch(bottom = true)
        return super.dispatchTouchEvent(ev)
    }

    override fun onResume() {
        super.onResume()
        SystemBars.hide(window)
        defaultHome = runCatching { io.github.zahakj.tandem.ui.HomeMode.isDefault(this) }.getOrDefault(true)
        SecondaryHome.shown(this)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        FocusGuard.bottomHomeFocused = hasFocus
        SystemBars.hide(window)
        // The focus left this screen while the library is in front: another app's window down there may have it now.
        if (!hasFocus) library()?.focusMayHaveLeft()
        // This screen got the controller (a touch, or Android moved the focus here) while it shows the library: the
        // library's remembered item takes the focus.
        if (hasFocus && guest() != null) takeLibraryFocus()
    }

    /** The library shown here takes the focus inside this window (its remembered item, through Nav.focusTick). */
    internal fun takeLibraryFocus() {
        val content = findViewById<android.view.ViewGroup>(android.R.id.content)?.getChildAt(0)
        if (content != null && !content.hasFocus()) content.requestFocus()
        io.github.zahakj.tandem.ui.Nav.focusTick++
    }

    override fun onDestroy() {
        FocusGuard.bottomHomeFocused = false
        LibraryGuest.detach(this)
        SecondaryHome.detach()
        super.onDestroy()
    }
}
