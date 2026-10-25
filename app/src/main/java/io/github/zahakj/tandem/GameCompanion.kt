package io.github.zahakj.tandem

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.WindowCompat
import io.github.zahakj.tandem.data.SystemPrefs
import io.github.zahakj.tandem.ui.CompanionScreen
import io.github.zahakj.tandem.ui.TandemTheme
import io.github.zahakj.tandem.ui.Themes
import java.lang.ref.WeakReference

/**
 * The bottom screen while a game plays on the top one.
 *
 * Tandem's details panel on the bottom screen is a Presentation owned by [MainActivity]; when a game starts on the top
 * screen the activity stops, the presentation goes away and Android would show its own secondary-display launcher
 * down there. So just before the game starts, Tandem opens [CompanionActivity] on the bottom display, underneath the
 * presentation (presentation windows sit above activity windows): when the presentation goes, the companion is
 * already there. Coming back to Tandem shows the presentation again first and only then finishes the companion, so
 * the stock launcher never flashes.
 *
 * What it shows is chosen per system with a per-game override: the game's art, Tandem's companion (Pip, clock, quick
 * bar, toggles), or nothing (the emulator uses both screens itself: 3DS, DS).
 */
object GameCompanion {
    const val ART = "art"
    const val TANDEM = "tandem"
    const val OFF = "off"

    val choices = listOf(ART to "Game art", TANDEM to "Tandem companion", OFF to "Leave to emulator")

    fun label(mode: String): String = choices.firstOrNull { it.first == mode }?.second ?: "Game art"

    /** The next choice after [mode], for rows that cycle. */
    fun next(mode: String): String = choices[(choices.indexOfFirst { it.first == mode } + 1) % choices.size].first

    /**
     * Systems whose default emulators draw on both screens (Azahar/Citra, melonDS/DraStic). Not Wii U: Cemu for
     * Android (info.cemu.cemu, 0.5.2) draws nothing on the second screen, so leaving it to the emulator showed
     * Android's stock secondary-display launcher there; it gets the game art like any other system. Someone whose
     * emulator does use the second screen picks "Leave to emulator" in the system's options (or a game's).
     */
    private val dualSystems = setOf("n3ds", "nds")
    private val dualApps = listOf("azahar", "citra", "lime3ds", "borked3ds", "mandarine", "melonds", "drastic")

    fun usesBothScreens(platformId: String, pkg: String?): Boolean =
        platformId in dualSystems || pkg?.lowercase()?.let { p -> dualApps.any { it in p } } == true

    /** What a system's games get by default: the user's choice, else game art (or nothing for dual-screen emulators). */
    fun systemMode(platform: Platform): String =
        SystemPrefs.config(platform.id).companion
            ?: if (usesBothScreens(platform.id, platform.players.firstOrNull()?.packageName)) OFF else ART

    /** What [game] gets: its own choice, else its system's. */
    fun modeFor(game: Game, pkg: String? = null): String {
        game.meta.companion?.let { return it }
        SystemPrefs.config(game.platform.id).companion?.let { return it }
        val emu = pkg ?: if (game.platform.isApps) null else game.platform.playerFor(game)?.packageName
        return if (usesBothScreens(game.platform.id, emu)) OFF else ART
    }

    /** True once this process has opened a companion (so the theme and stores are already loaded). */
    @Volatile var launchedHere = false
        private set

    /**
     * True while Tandem's own bottom-screen window (the details, or the library when the screens are swapped) is
     * showing. It is a presentation, so it sits above the companion: a companion opened underneath it waits for this
     * to go false before playing its entrance, instead of finishing it unseen and then popping in.
     */
    var tandemBelow by mutableStateOf(false)
        internal set

    @Volatile private var generation = 0
    @Volatile private var shownAt = 0L

    /** A game was started a moment ago: Tandem is on its way out, and the companion is coming. */
    fun recentlyShown(): Boolean = android.os.SystemClock.elapsedRealtime() - shownAt < 4_000L
    private var current: WeakReference<CompanionActivity>? = null
    private val main = Handler(Looper.getMainLooper())

    /**
     * Opens the companion on the bottom screen for [game], before the game itself starts (so the game, started last,
     * keeps the controller). Does nothing in single-screen mode, without a second screen, or when the game starts on
     * the bottom screen ([bottom]) or its emulator uses both screens. [earlierMs] is what was already played this
     * session, when the game is brought back rather than started. Safe from any thread.
     */
    fun show(ctx: Context, game: Game, pkg: String?, bottom: Boolean, earlierMs: Long = 0L) {
        app = ctx.applicationContext
        val mode = modeFor(game, pkg)
        if (bottom || mode == OFF || SystemPrefs.data.singleScreen) { dismiss(); return }
        val display = LaunchDisplay.secondary(ctx) ?: return
        val lib = Libraries.of(game)
        val info = CompanionInfo(
            key = game.key,
            title = game.title,
            system = game.platform.shortName,
            systemId = game.platform.id,
            hero = (lib?.hero(game) ?: lib?.screenshot(game))?.path,
            box = lib?.box(game)?.path,
            logo = lib?.logo(game)?.path,
            mode = mode,
            pkg = pkg,
            start = System.currentTimeMillis(),
            earlier = earlierMs,
        )
        generation++
        shownAt = android.os.SystemClock.elapsedRealtime()
        // Kept so the companion can come back by itself if Tandem's process is restarted mid-game; never for a
        // hidden game, which the companion would show by name and art without the private-mode reveal.
        CompanionRestore.save(ctx, info.takeUnless { Privacy.isHidden(game) })
        launchedHere = true
        open(ctx, info, display.displayId)
    }

    /** Starts [CompanionActivity] for [info] on display [displayId]. Without focus: the game keeps the controller. */
    private fun open(ctx: Context, info: CompanionInfo, displayId: Int) {
        val intent = info.putInto(
            Intent(ctx, CompanionActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION),
        )
        val options = ActivityOptions.makeCustomAnimation(ctx, 0, 0).setLaunchDisplayId(displayId).toBundle()
        try {
            ctx.startActivity(intent, options)
        } catch (t: Throwable) {
            Log.w("Tandem", "Couldn't open the bottom-screen companion", t)
        }
    }

    /** Closes the companion (Tandem is back, or a game took the bottom screen). Safe from any thread. */
    fun dismiss(afterMs: Long = 0) {
        // A game started again before this runs keeps its companion.
        val gen = generation
        main.postDelayed({
            if (gen != generation) return@postDelayed
            current?.get()?.let { if (!it.isFinishing) it.finishQuietly() }
            current = null
            // Nothing to bring back after a restart any more.
            app?.let { CompanionRestore.save(it, null) }
        }, afterMs)
    }

    private val restoring = java.util.concurrent.atomic.AtomicBoolean(false)

    /** The application, for forgetting the companion when it closes (see [dismiss]). */
    @Volatile private var app: Context? = null

    /** Called when the main screen starts, so [dismiss] can forget the companion even in a fresh process. */
    fun attachApp(ctx: Context) { app = ctx.applicationContext }

    /**
     * Tandem's process was (re)started while something else may be in front: an update (`adb install -r`, a store
     * update) or a low-memory kill took the companion with it, and Android's stock launcher shows on the bottom
     * screen under the game. Brings the companion back as it was, only when the game it was opened for is still the
     * app in front on the main display (asked through Shizuku; without Shizuku nothing is restored), its play session
     * is still open (Tandem hasn't come back since) and the game isn't hidden. The companion never takes focus, so
     * the game keeps the controller. Blocking checks run on a background thread; [done] runs once, on any thread.
     */
    fun restore(ctx: Context, done: () -> Unit = {}) {
        val appCtx = ctx.applicationContext
        app = appCtx
        val info = CompanionRestore.load(appCtx)
        // The update receiver and the accessibility service can both ask in the same new process; one check is enough.
        if (info == null || current?.get() != null || !restoring.compareAndSet(false, true)) { done(); return }
        val gen = generation
        Thread({
            var opened = false
            fun finish() { restoring.set(false); done() }
            try {
                SystemPrefs.init(appCtx)
                io.github.zahakj.tandem.data.Store.init(appCtx)
                val display = LaunchDisplay.secondary(appCtx)
                val front = if (Shell.waitReady(CompanionRestore.SHIZUKU_WAIT_MS)) {
                    Shell.run("dumpsys", "activity", "activities")?.let { CompanionRestore.frontPackage(it) }
                } else null
                val ok = CompanionRestore.shouldRestore(
                    info, front,
                    sessionKey = Sessions.openKey(appCtx),
                    hidden = io.github.zahakj.tandem.data.Store.get(info.key).hidden,
                    singleScreen = SystemPrefs.data.singleScreen,
                    hasSecondDisplay = display != null,
                    now = System.currentTimeMillis(),
                )
                Log.i("Tandem", "Companion after a restart: ${if (ok) "bringing it back" else "not needed"} (in front: $front, game: ${info.pkg})")
                if (ok && display != null) {
                    opened = true
                    main.post {
                        try {
                            // A game started (or Tandem came back) in the meantime: that already took care of it.
                            if (gen == generation && current?.get() == null) open(appCtx, info, display.displayId)
                        } finally {
                            finish()
                        }
                    }
                }
            } catch (t: Throwable) {
                Log.w("Tandem", "Couldn't check whether to bring the companion back", t)
            } finally {
                if (!opened) finish()
            }
        }, "companion-restore").start()
    }

    internal fun attach(a: CompanionActivity) { current = WeakReference(a) }

    /** True while the companion is up, which means a game Tandem started is running. */
    fun up(): Boolean = current?.get() != null
    internal fun detach(a: CompanionActivity) { if (current?.get() === a) current = null }

    /** Back to Tandem on the top screen; it closes the companion itself once its own bottom screen is showing. */
    fun backToTandem(ctx: Context) {
        val intent = Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val options = ActivityOptions.makeBasic().setLaunchDisplayId(android.view.Display.DEFAULT_DISPLAY).toBundle()
        runCatching { ctx.startActivity(intent, options) }.onFailure { Log.w("Tandem", "Couldn't bring Tandem back", it) }
    }

    internal const val X_KEY = "key"
    internal const val X_TITLE = "title"
    internal const val X_SYSTEM = "system"
    internal const val X_SYSTEM_ID = "systemId"
    internal const val X_HERO = "hero"
    internal const val X_BOX = "box"
    internal const val X_LOGO = "logo"
    internal const val X_MODE = "mode"
    internal const val X_PKG = "pkg"
    internal const val X_START = "start"
    internal const val X_EARLIER = "earlier"
}

/** The game on the top screen, as the companion shows it. Built from the launch intent, so it needs no library. */
data class CompanionInfo(
    val key: String,
    val title: String,
    val system: String,
    val systemId: String,
    val hero: String?,
    val box: String?,
    val logo: String?,
    val mode: String,
    val pkg: String?,
    val start: Long,
    /** Time already played this session before [start] (a game brought back from Tandem), in ms. */
    val earlier: Long = 0L,
) {
    /** Puts this into the companion's launch intent (read back with [from]). */
    fun putInto(i: Intent): Intent = i
        .putExtra(GameCompanion.X_KEY, key)
        .putExtra(GameCompanion.X_TITLE, title)
        .putExtra(GameCompanion.X_SYSTEM, system)
        .putExtra(GameCompanion.X_SYSTEM_ID, systemId)
        .putExtra(GameCompanion.X_HERO, hero)
        .putExtra(GameCompanion.X_BOX, box)
        .putExtra(GameCompanion.X_LOGO, logo)
        .putExtra(GameCompanion.X_MODE, mode)
        .putExtra(GameCompanion.X_PKG, pkg)
        .putExtra(GameCompanion.X_START, start)
        .putExtra(GameCompanion.X_EARLIER, earlier)

    companion object {
        fun from(i: Intent?): CompanionInfo? {
            i ?: return null
            val key = i.getStringExtra(GameCompanion.X_KEY) ?: return null
            return CompanionInfo(
                key, i.getStringExtra(GameCompanion.X_TITLE).orEmpty(), i.getStringExtra(GameCompanion.X_SYSTEM).orEmpty(),
                i.getStringExtra(GameCompanion.X_SYSTEM_ID).orEmpty(), i.getStringExtra(GameCompanion.X_HERO),
                i.getStringExtra(GameCompanion.X_BOX), i.getStringExtra(GameCompanion.X_LOGO),
                i.getStringExtra(GameCompanion.X_MODE) ?: GameCompanion.ART, i.getStringExtra(GameCompanion.X_PKG),
                i.getLongExtra(GameCompanion.X_START, System.currentTimeMillis()),
                i.getLongExtra(GameCompanion.X_EARLIER, 0L),
            )
        }
    }
}

/**
 * The bottom screen while a game plays (see [GameCompanion]). Its window never takes input focus, so the controller
 * stays with the game on the top screen; touch still works. Kept out of Recents, in its own task.
 */
class CompanionActivity : ComponentActivity() {
    private val info = mutableStateOf<CompanionInfo?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Never take the controller from the game; touch still arrives.
        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        window.setWindowAnimations(0)
        io.github.zahakj.tandem.ui.Images.install(this)
        // Started in a fresh process (rare: Android restored it): load settings and the theme first.
        io.github.zahakj.tandem.data.Store.init(this)
        SystemPrefs.init(this)
        io.github.zahakj.tandem.data.HomeStore.init(this)
        io.github.zahakj.tandem.data.AppPrefs.init(this)
        io.github.zahakj.tandem.data.ExtrasStore.init(this)
        if (!GameCompanion.launchedHere) Themes.init(this)
        window.setBackgroundDrawable(ColorDrawable(Themes.live.background.toArgb()))
        WindowCompat.setDecorFitsSystemWindows(window, false)
        SystemBars.hide(window)
        info.value = CompanionInfo.from(intent)
        if (info.value == null) { finishQuietly(); return }
        GameCompanion.attach(this)
        // A companion app covers this at once and it may never be started: watch for the game's end from here.
        if (CompanionApps.covering) watchGame()
        setContent { TandemTheme { info.value?.let { CompanionScreen(it, this) } } }
        io.github.zahakj.tandem.ui.QuietSemantics.watch(window.decorView)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        CompanionInfo.from(intent)?.let { info.value = it }
        GameCompanion.attach(this)
        // Another game (or the same one brought back): watch that one.
        if (lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) watchGame()
    }

    /** Brings Tandem back when the game ends while this is up (see [GameExit]). */
    private var watch: GameExit.Watch? = null

    private fun watchGame() {
        watch?.stop()
        watch = info.value?.pkg?.takeIf { it.isNotEmpty() }?.let { GameExit.Watch(this, it).also { w -> w.start() } }
    }

    override fun onStart() {
        super.onStart()
        watchGame()
    }

    override fun onResume() {
        super.onResume()
        SystemBars.hide(window)
    }

    override fun onStop() {
        // Covered by the game's companion app (CompanionApps): it keeps watching for the game's end underneath.
        if (!CompanionApps.covering || isFinishing) {
            watch?.stop()
            watch = null
        }
        super.onStop()
    }

    // A key or stick move that reaches this window while Tandem's screensaver runs wakes it (see Dreaming).
    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean = Dreaming.onKey(event) || super.dispatchKeyEvent(event)

    override fun dispatchGenericMotionEvent(ev: android.view.MotionEvent): Boolean = Dreaming.onMotion(ev) || super.dispatchGenericMotionEvent(ev)

    /** Switches between game art and the companion for this play session. */
    fun switchMode() {
        val i = info.value ?: return
        info.value = i.copy(mode = if (i.mode == GameCompanion.TANDEM) GameCompanion.ART else GameCompanion.TANDEM)
    }

    fun finishQuietly() {
        finish()
        @Suppress("DEPRECATION") overridePendingTransition(0, 0)
    }

    override fun onDestroy() {
        watch?.stop()
        watch = null
        GameCompanion.detach(this)
        super.onDestroy()
    }
}
