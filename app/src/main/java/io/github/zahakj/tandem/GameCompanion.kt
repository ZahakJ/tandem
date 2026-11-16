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
import io.github.zahakj.tandem.data.HomeStore
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
 * What it shows is chosen for every game at once (Settings › Play & system › Playing), per system and per game, the
 * most particular choice winning: the game's art, Tandem's companion (Pip, clock, quick bar, toggles), a dark screen
 * (Screen off, ScreenOff.kt: black and still until a tap, the biggest battery saving there is on the OLED panel), or
 * nothing (the emulator uses both screens itself: 3DS, DS).
 */
object GameCompanion {
    const val ART = "art"
    const val TANDEM = "tandem"
    /** Screen off: the companion is up, black and still, so the panel's pixels are off (ScreenOff.kt). */
    const val DARK = "dark"
    /** Leave to emulator: no companion at all. */
    const val OFF = "off"
    /** A keyboard and trackpad for the game (ui/DeckPanel.kt): offered for PC games and streams ([DeckGames]). */
    const val DECK = "deck"

    val choices = listOf(ART to "Game art", TANDEM to "Tandem companion", DARK to "Screen off", OFF to "Leave to emulator")
    private val deckChoice = DECK to "Deck: keyboard & trackpad"

    /** The choices for a game or system; the Deck only where a keyboard and mouse make sense ([deck]). */
    fun choices(deck: Boolean): List<Pair<String, String>> = if (deck) choices.take(2) + deckChoice + choices.drop(2) else choices

    fun label(mode: String): String = (choices + deckChoice).firstOrNull { it.first == mode }?.second ?: "Game art"

    /** The next choice after [mode], for rows that cycle; [deck] says whether the Deck is among them. */
    fun next(mode: String, deck: Boolean = false): String {
        val list = choices(deck)
        return list[(list.indexOfFirst { it.first == mode } + 1) % list.size].first
    }

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

    /** The choice for every system and game without one of their own (Settings › Play & system › Playing): game art unless changed. */
    fun defaultMode(): String = HomeStore.settings.companion ?: ART

    /** What a system's games get by default: the user's choice for it, else [defaultMode] (or nothing for dual-screen emulators). */
    fun systemMode(platform: Platform): String =
        CompanionMode.resolve(null, SystemPrefs.config(platform.id).companion, usesBothScreens(platform.id, platform.players.firstOrNull()?.packageName), defaultMode())

    /** What [game] gets: its own choice, else its system's, else the setting for everyone. */
    fun modeFor(game: Game, pkg: String? = null): String {
        val emu = pkg ?: if (game.platform.isApps) null else game.platform.playerFor(game)?.packageName
        return CompanionMode.resolve(game.meta.companion, SystemPrefs.config(game.platform.id).companion, usesBothScreens(game.platform.id, emu), defaultMode())
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
            manual = Manuals.forGame(game)?.path,
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
            // Home is bringing the game and this companion back (keepForGame): Tandem resuming meanwhile doesn't close it.
            if (android.os.SystemClock.elapsedRealtime() < keptUntil) return@postDelayed
            current?.get()?.let { if (!it.isFinishing) it.finishQuietly() }
            current = null
            app?.let {
                removeLeftover(it)
                // Nothing to bring back after a restart any more.
                CompanionRestore.save(it, null)
            }
        }, afterMs)
    }

    /**
     * Closes a companion this process doesn't know: Tandem's process was restarted (an update, a low-memory kill)
     * under it, and Android kept its task, showing it again (even relaunching it) with no game behind it. Found
     * among Tandem's own tasks, so it needs no Shizuku.
     */
    private fun removeLeftover(ctx: Context) {
        val name = CompanionActivity::class.java.name
        runCatching {
            for (task in ctx.getSystemService(android.app.ActivityManager::class.java).appTasks) {
                val info = runCatching { task.taskInfo }.getOrNull() ?: continue
                if (info.baseIntent.component?.className == name || info.baseActivity?.className == name) {
                    runCatching { task.finishAndRemoveTask() }
                }
            }
        }.onFailure { Log.w("Tandem", "Couldn't look for a companion left behind", it) }
    }

    /** [a]'s game is gone (GameExit): it closes by itself, Tandem stays where it is. */
    internal fun closeFor(a: CompanionActivity) {
        if (current?.get() === a) current = null
        a.finishQuietly()
        app?.let { CompanionRestore.save(it, null) }
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
                var front = if (Shell.waitReady(CompanionRestore.SHIZUKU_WAIT_MS)) {
                    Shell.run("dumpsys", "activity", "activities")?.let { CompanionRestore.frontPackage(it) }
                } else null
                // Updating Tandem, the default Home, puts its home in front of the game. The game's session is still
                // open (nobody went home), so the game goes back in front first, as it was.
                val pkg = info.pkg
                if (front == appCtx.packageName && pkg != null && Sessions.openKey(appCtx) == info.key) {
                    GameResume.target(Processes.snapshot(maxAgeMs = 0)?.tasks, pkg)?.let { t ->
                        GameResume.bringBack(appCtx, t)
                        Thread.sleep(500)
                        front = Shell.run("dumpsys", "activity", "activities")?.let { CompanionRestore.frontPackage(it) }
                    }
                }
                val ok = CompanionRestore.shouldRestore(
                    info, front,
                    sessionKey = Sessions.openKey(appCtx),
                    hidden = io.github.zahakj.tandem.data.Store.get(info.key).hidden,
                    singleScreen = SystemPrefs.data.singleScreen,
                    hasSecondDisplay = display != null,
                    now = System.currentTimeMillis(),
                    processStart = info.pkg?.let { p -> Processes.snapshot(maxAgeMs = 0)?.started?.get(p) },
                )
                Log.i("Tandem", "Companion after a restart: ${if (ok) "bringing it back" else "not needed"} (in front: $front, game: ${info.pkg})")
                if (ok && display != null) {
                    opened = true
                    main.post {
                        try {
                            // A game started (or Tandem came back) in the meantime: that already took care of it.
                            if (gen == generation && current?.get() == null) {
                                open(appCtx, info, display.displayId)
                                settleBars(appCtx, info.pkg)
                            }
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

    /**
     * A companion opened over an app's leftovers on the bottom screen (after a restart): it sets the bars there
     * ([claimBars]) once it is up, then the game is brought to the front again so it keeps the controller.
     */
    private fun settleBars(ctx: Context, pkg: String?) {
        pkg ?: return
        Thread({
            Thread.sleep(800)
            claimBars()
            Thread.sleep(250)
            GameResume.target(Processes.snapshot(maxAgeMs = 0)?.tasks, pkg)?.let { GameResume.bringBack(ctx, it) }
        }, "companion-bars").start()
    }

    internal fun attach(a: CompanionActivity) { current = WeakReference(a) }

    /** The companion on screen, if one is up and not on its way out. */
    internal fun current(): CompanionActivity? = current?.get()?.takeIf { !it.isFinishing }

    /** True while the companion is up, which means a game Tandem started is running. */
    fun up(): Boolean = current?.get() != null
    internal fun detach(a: CompanionActivity) { if (current?.get() === a) current = null }

    /** The package of the game the companion is up for, if it is up. */
    fun gamePackage(): String? = current?.get()?.takeIf { !it.isFinishing }?.gamePackage

    /**
     * Home is bringing the game back with its companion (HomeBack): dismissals already on their way are dropped, as
     * when a game starts, and the focus checks leave the bottom screen to it for a moment.
     */
    /** Until when (elapsedRealtime) a dismissal leaves the companion be: Home is bringing it back. */
    @Volatile private var keptUntil = 0L
    private const val KEEP_MS = 1_500L

    fun keepForGame() {
        generation++
        keptUntil = android.os.SystemClock.elapsedRealtime() + KEEP_MS
        shownAt = android.os.SystemClock.elapsedRealtime()
    }

    /** See [CompanionActivity.claimBars]: lets the companion set the bottom screen's bars after an app had them. */
    fun claimBars() { main.post { current?.get()?.takeIf { !it.isFinishing }?.claimBars() } }

    /** Lights a dimmed or dark panel (ScreenOff.kt): Home brought the game and its companion back. Safe from any thread. */
    fun wake() { main.post { current?.get()?.takeIf { !it.isFinishing }?.wake() } }

    /** Brings the companion's task to the front of the bottom screen (it never takes the focus). Blocking. */
    fun toFront(ctx: Context): Boolean {
        val name = CompanionActivity::class.java.name
        return runCatching {
            val task = ctx.getSystemService(android.app.ActivityManager::class.java).appTasks.firstOrNull {
                val info = runCatching { it.taskInfo }.getOrNull()
                info?.baseIntent?.component?.className == name || info?.baseActivity?.className == name
            } ?: return false
            // By its id, like the game (GameResume): AppTask.moveToFront can bring Tandem's library up too, over the
            // game, and an emulator paused by that (Eden pauses whenever it is covered) stays paused.
            val id = task.taskInfo.taskId
            ctx.getSystemService(android.app.ActivityManager::class.java).moveTaskToFront(id, 0)
            true
        }.getOrElse { Log.w("Tandem", "Couldn't bring the companion back", it); false }
    }

    /** Back to Tandem on the top screen; it closes the companion itself once its own bottom screen is showing. */
    fun backToTandem(ctx: Context) {
        keptUntil = 0L
        val intent = MainActivity.frontIntent(ctx)
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
    internal const val X_MANUAL = "manual"
}

/** Which of [GameCompanion]'s modes a game gets. Apart from [GameCompanion], which has a main-thread handler, so tests can ask it. */
object CompanionMode {
    /**
     * The mode that wins: the game's own choice, else its system's, else nothing for an emulator that draws on both
     * screens ([dual]; the setting for everyone can't take its screen away), else the setting for everyone ([default]).
     */
    fun resolve(game: String?, system: String?, dual: Boolean, default: String): String = game ?: system ?: if (dual) GameCompanion.OFF else default
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
    /** The game's manual (Manuals.kt), a file or folder path, so the companion can offer it without the library. */
    val manual: String? = null,
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
        .putExtra(GameCompanion.X_MANUAL, manual)

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
                i.getStringExtra(GameCompanion.X_MANUAL),
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

    /** The face shown while the panel is lit: Tandem's companion, the Deck, or the game's art (a Screen off panel lights to the art). */
    val face = mutableStateOf(GameCompanion.ART)

    /**
     * When the panel was last touched, or lit by Home bringing the game back (uptimeMillis); null since it came up for
     * this game. With [shownAt], what ScreenOff needs to say whether the panel is bright, dimmed or dark.
     */
    val touchedAt = mutableStateOf<Long?>(null)
    var shownAt: Long = android.os.SystemClock.uptimeMillis()
        private set
    private var starts = 0

    private fun faceOf(mode: String): String = when (mode) {
        GameCompanion.TANDEM, GameCompanion.DECK -> mode
        else -> GameCompanion.ART
    }

    /** A touch, or Home bringing the game back: the panel lights up (or stays lit) from now. */
    fun wake() { touchedAt.value = android.os.SystemClock.uptimeMillis() }

    /** Another game, or the same one brought back: the panel starts over, dark again when its mode is Screen off. */
    private fun freshPanel(i: CompanionInfo) {
        face.value = faceOf(i.mode)
        touchedAt.value = null
        shownAt = android.os.SystemClock.uptimeMillis()
        // Which display-off paths this Android has, for the log; once, and only when a panel is meant to be dark.
        if (i.mode == GameCompanion.DARK) ScreenOff.logFacts(this)
    }

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
        if (!GameCompanion.launchedHere) {
            Themes.init(this)
            // The profile line on this screen comes from what Tandem wrote down when the game started.
            Performance.startup(this)
        }
        window.setBackgroundDrawable(ColorDrawable(Themes.live.background.toArgb()))
        WindowCompat.setDecorFitsSystemWindows(window, false)
        SystemBars.hide(window)
        val first = CompanionInfo.from(intent)
        info.value = first
        if (first == null) { finishQuietly(); return }
        freshPanel(first)
        GameCompanion.attach(this)
        // A companion app covers this at once and it may never be started: watch for the game's end from here.
        if (CompanionApps.covering) watchGame()
        setContent { TandemTheme { info.value?.let { CompanionScreen(it, this) } } }
        io.github.zahakj.tandem.ui.QuietSemantics.watch(window.decorView)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        CompanionInfo.from(intent)?.let { info.value = it; freshPanel(it) }
        GameCompanion.attach(this)
        // Another game (or the same one brought back): watch that one.
        if (lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) watchGame()
    }

    /** Brings Tandem back when the game ends while this is up (see [GameExit]). */
    private var watch: GameExit.Watch? = null

    private fun watchGame() {
        watch?.stop()
        watch = info.value?.pkg?.takeIf { it.isNotEmpty() }?.let { GameExit.Watch(this, it).also { w -> w.start() } }
        if (GameScreens.isFor(info.value?.pkg)) GameScreens.watch(this) else GameScreens.stop(this)
    }

    override fun onStart() {
        super.onStart()
        HomeBack.coveredAt = 0L
        // Back on show after an app covered it or the handheld slept: that is as good as a touch, so a dimmed or dark
        // panel lights up for a while. The first show leaves a Screen off panel dark.
        if (starts++ > 0) wake()
        watchGame()
    }

    /** The game this is up for. */
    val gamePackage: String? get() = info.value?.pkg?.takeIf { it.isNotEmpty() }

    override fun onResume() {
        super.onResume()
        SystemBars.hide(window)
    }

    /** A note is being typed ([typing]): the window holds the focus on purpose. */
    private var typingNow = false

    /**
     * This became the activity buttons go to (a touch on this screen, the AYN menu closing over the game, an app
     * closing). A window that never takes the focus would leave every button press waiting until Android called
     * Tandem not responding and offered to close it, so for as long as it is the top one it takes the presses, and
     * the game is brought back to the front (with Shizuku, which knows its task), which takes the controller again.
     */
    override fun onTopResumedActivityChanged(isTopResumedActivity: Boolean) {
        super.onTopResumedActivityChanged(isTopResumedActivity)
        if (typingNow) return
        if (!isTopResumedActivity) {
            window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
            return
        }
        window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        SystemBars.hide(window)
        val pkg = gamePackage ?: return
        val app = applicationContext
        Thread({ GameResume.target(Processes.snapshot(maxAgeMs = 0)?.tasks, pkg)?.let { GameResume.bringBack(app, it) } }, "companion-hand-back").start()
    }

    /** A screenshot of the game's screen, saved and copied to the clipboard (GameShot.kt). */
    fun screenshot() {
        val title = info.value?.title ?: return
        val gameOnTop = gameOnTop
        val app = applicationContext
        Thread({
            val said = when (val r = GameShot.take(app, title, gameOnTop)) {
                is GameShot.Result.Saved -> "Screenshot saved and copied: paste it anywhere"
                is GameShot.Result.Failed -> r.why
            }
            runOnUiThread { android.widget.Toast.makeText(app, said, android.widget.Toast.LENGTH_SHORT).show() }
        }, "tandem-shot").start()
    }

    /** Starts a clip of the game's screen (GameClip.kt), or stops the one being recorded. */
    fun clip() {
        if (GameClip.endsAt != 0L) {
            Thread({ GameClip.stop() }, "tandem-clip-stop").start()
            return
        }
        val title = info.value?.title ?: return
        val voice = io.github.zahakj.tandem.data.SystemPrefs.data.clipVoice
        val app = applicationContext
        // The game's sound needs the screen-capture grant: given through Shizuku, Android's ask returns at once.
        Thread({
            val sound = GameClip.grant(app, voice)
            runOnUiThread {
                if (sound && runCatching { askCapture.launch(captureAsk(title, gameOnTop, voice)) }.isSuccess) return@runOnUiThread
                clipFor = null
                recordSilent(title, gameOnTop, voice)
            }
        }, "tandem-clip-grant").start()
    }

    /** The clip waiting for the screen-capture grant. */
    private var clipFor: Triple<String, Boolean, Boolean>? = null

    private fun captureAsk(title: String, gameOnTop: Boolean, voice: Boolean): Intent {
        clipFor = Triple(title, gameOnTop, voice)
        return getSystemService(android.media.projection.MediaProjectionManager::class.java).createScreenCaptureIntent()
    }

    private val askCapture = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { r ->
        val (title, gameOnTop, voice) = clipFor ?: return@registerForActivityResult
        clipFor = null
        val grant = r.data
        val code = r.resultCode
        val screen = display?.displayId ?: android.view.Display.DEFAULT_DISPLAY
        val app = applicationContext
        val pkg = gamePackage
        // Android's ask took the focus for a moment, and the game's screen shows its bars without it: the game gets
        // the focus back, and a moment to hide them again, before the clip starts.
        Thread({
            pkg?.let { p -> GameResume.target(Processes.snapshot(maxAgeMs = 0)?.tasks, p)?.let { GameResume.bringBack(app, it) } }
            Thread.sleep(700)
            runOnUiThread {
                if (code == RESULT_OK && grant != null) ClipService.start(this, code, grant, title, gameOnTop, voice, screen)
                else recordSilent(title, gameOnTop, voice)
            }
        }, "tandem-clip-focus").start()
    }

    /** A clip without the game's sound (no Shizuku grant, or capture refused): the picture, and your voice if on. */
    private fun recordSilent(title: String, gameOnTop: Boolean, voice: Boolean) {
        val app = applicationContext
        val screen = display?.displayId ?: android.view.Display.DEFAULT_DISPLAY
        Thread({ ClipService.say(app, ClipService.said(GameClip.record(app, title, gameOnTop, null, voice)), screen) }, "tandem-clip").start()
    }

    /** True when the game has the top screen (this is on the other one). */
    val gameOnTop: Boolean get() = (display?.displayId ?: android.view.Display.DEFAULT_DISPLAY) != android.view.Display.DEFAULT_DISPLAY

    /**
     * Typing a note (CompanionNotes): Android's keyboard needs a window with the focus, so for that long this one
     * takes it, and with it the controller. Afterwards it lets go again and the game is brought back to the front by
     * its task (with Shizuku), so the controller is the game's again; without Shizuku a touch on the game does that.
     */
    fun typing(on: Boolean) {
        val app = applicationContext
        typingNow = on
        if (on) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
            SystemBars.hide(window)
            Thread({ GameCompanion.toFront(app) }, "companion-typing").start()
        } else {
            window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
            SystemBars.hide(window)
            val pkg = gamePackage ?: return
            Thread({ GameResume.target(Processes.snapshot(maxAgeMs = 0)?.tasks, pkg)?.let { GameResume.bringBack(app, it) } }, "companion-typed").start()
        }
    }

    /**
     * Android shows the bottom screen's bars as the last window there with the focus asked (an app opened over this,
     * say), since this never takes the focus. For a moment it may, so its own "no bars" counts; the game, brought to
     * the front right after, keeps the controller.
     */
    fun claimBars() {
        window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        SystemBars.hide(window)
        window.decorView.postDelayed({ window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) }, 600)
    }

    override fun onStop() {
        // Covered by another app (not the game's own companion app): Home brings this back first (HomeBack).
        if (!isFinishing && !CompanionApps.covering) HomeBack.coveredAt = android.os.SystemClock.elapsedRealtime()
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

    /** Switches between game art and the companion for this play session; a Screen off panel still goes dark again. */
    fun switchMode() {
        face.value = if (face.value == GameCompanion.TANDEM) GameCompanion.ART else GameCompanion.TANDEM
    }

    /** Shows [mode] (art, companion or the Deck) for this play session; a Screen off panel still goes dark again. */
    fun setMode(mode: String) {
        face.value = faceOf(mode)
    }

    fun finishQuietly() {
        finish()
        @Suppress("DEPRECATION") overridePendingTransition(0, 0)
    }

    override fun onDestroy() {
        watch?.stop()
        watch = null
        GameScreens.stop(this)
        GameCompanion.detach(this)
        super.onDestroy()
    }
}

/**
 * The Screenshot and Clip commands (Bindings.kt) pressed inside Tandem: the same as the companion's own Screenshot and
 * Clip chips, for the game it is up for. With no companion up there is no game on the other screen to capture.
 */
object Capture {
    private fun companion(): CompanionActivity? = GameCompanion.current()

    fun screenshot() {
        companion()?.screenshot() ?: nothing()
    }

    fun clip() {
        companion()?.clip() ?: nothing()
    }

    private fun nothing() {
        io.github.zahakj.tandem.ui.Sounds.error()
        io.github.zahakj.tandem.ui.Toasts.show("Start a game first: this captures the game on the other screen")
    }
}
