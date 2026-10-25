package io.github.zahakj.tandem.ui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Environment
import android.os.SystemClock
import android.provider.DocumentsContract
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.zahakj.tandem.ActiveHost
import io.github.zahakj.tandem.Button
import io.github.zahakj.tandem.Buttons
import io.github.zahakj.tandem.Collections
import io.github.zahakj.tandem.Game
import io.github.zahakj.tandem.GroupTarget
import io.github.zahakj.tandem.Libraries
import io.github.zahakj.tandem.Library
import io.github.zahakj.tandem.MainActivity
import io.github.zahakj.tandem.Platforms
import io.github.zahakj.tandem.Prefs
import io.github.zahakj.tandem.Selected
import io.github.zahakj.tandem.Selection
import io.github.zahakj.tandem.Shell
import io.github.zahakj.tandem.data.Importer
import io.github.zahakj.tandem.data.ScanCache
import io.github.zahakj.tandem.data.Store
import io.github.zahakj.tandem.plural
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.text.DateFormat
import java.util.Date

/** Where the user is. Overlays (game menu, search) sit on top of a route. */
sealed interface Route {
    val key: String
    val depth: Int

    data object Home : Route { override val key = "home"; override val depth = 0 }
    data class System(val platformId: String, val folder: String? = null) : Route {
        override val key = "sys:$platformId/${folder.orEmpty()}"
        override val depth = if (folder == null) 1 else 2
    }
    data class Collection(val id: String) : Route { override val key = "col:$id"; override val depth = 1 }
    data object Genres : Route { override val key = "genres"; override val depth = 1 }
    data object Settings : Route { override val key = "settings"; override val depth = 1 }
    data object Systems : Route { override val key = "systems"; override val depth = 2 }
    data object Apps : Route { override val key = "apps"; override val depth = 1 }
    data object Appearance : Route { override val key = "appearance"; override val depth = 2 }
    data object ThemePacks : Route { override val key = "themepacks"; override val depth = 3 }
    data object Scrape : Route { override val key = "scrape"; override val depth = 2 }
    data object Stats : Route { override val key = "stats"; override val depth = 1 }
    data object Widgets : Route { override val key = "widgets"; override val depth = 1 }
    /** A page of Settings (see SettingsCatalog.kt); Look & feel is [Appearance]. */
    data class SettingsPage(val id: String) : Route { override val key = "settings:$id"; override val depth = 2 }
    /** One of the built-in apps (BuiltInApps.kt). */
    data class Tool(val id: String) : Route { override val key = "tool:$id"; override val depth = 1 }
}

object Nav {
    var route by mutableStateOf<Route>(Route.Home)
    private val stack = ArrayDeque<Route>()
    var menuFor by mutableStateOf<Game?>(null)
    var groupMenu by mutableStateOf(false)
    /** What the view menu is about (a system, folder, collection); null for just the view options. */
    var groupTarget by mutableStateOf<GroupTarget?>(null)
    var search by mutableStateOf(false)
    /** Bumped when an overlay closes, so the screen underneath takes focus back. */
    var focusTick by mutableIntStateOf(0)
    /** Popups owned by features (see PanelGuard); while one is open the app's own buttons stay quiet. */
    var openPanels by mutableIntStateOf(0)
    /** Which way the next same-level change slides: 1 from the right (R1), -1 from the left (L1). */
    var slide = 0
        private set
    /**
     * Which way the last route change moves (1: the new screen comes from the right, -1: from the left). Set before
     * [route] changes, so both the screen arriving and the one leaving read the same direction (see [routeMotion]).
     */
    var dir = 1
        private set
    private var lastGo = 0L

    private fun setRoute(r: Route, slideBy: Int) {
        slide = slideBy
        dir = when {
            r.depth != route.depth -> if (r.depth > route.depth) 1 else -1
            slideBy != 0 -> slideBy
            else -> 1
        }
        route = r
    }

    /**
     * Opens [r] on top of the current screen. A second press while the first screen is still sliding in (or a
     * press for the screen already showing) is ignored, so a double A never opens the same place twice.
     */
    fun go(r: Route) {
        val now = SystemClock.uptimeMillis()
        if (r == route || now - lastGo < 350) return
        lastGo = now
        stack.addLast(route)
        setRoute(r, 0)
        if (r is Route.System && r.folder == null) Sounds.systemOpened(r.platformId)
    }

    fun back(): Boolean {
        val r = stack.removeLastOrNull() ?: return false
        // Back to the Settings tiles from a page (maybe one reached with L1 / R1): that page's tile is highlighted.
        if (r == Route.Settings) SettingsCategories.idOf(route)?.let { FocusMemory.remember(r.key, "cat:$it") }
        setRoute(r, 0)
        return true
    }

    fun replace(r: Route, direction: Int = 0) = setRoute(r, direction)

    /** Start: open Settings, or go back to it when already somewhere inside it (Appearance, Systems...), or close it. */
    fun toggleSettings() {
        when {
            route == Route.Settings -> back()
            Route.Settings in stack -> { while (route != Route.Settings && back()) Unit }
            else -> go(Route.Settings)
        }
    }

    /** True inside Settings or a page reached from it (Start goes back there instead of opening the Start panel). */
    val inSettings: Boolean get() = route == Route.Settings || Route.Settings in stack || isSettingsRoute(route)

    fun openMenu(game: Game) {
        // Always open on Play, never on whatever was used last (Reset, Hide...).
        FocusMemory.forget("menu:${game.key}")
        menuFor = game
    }

    fun openGroupMenu(target: GroupTarget?) {
        FocusMemory.forget("groupmenu")
        groupTarget = target
        groupMenu = true
    }

    /** Closes the game and view menus only (search, if open underneath, stays). */
    fun closeMenus() { menuFor = null; groupMenu = false; focusTick++ }

    fun closeOverlays() { menuFor = null; groupMenu = false; search = false; focusTick++ }

    /** True while any popup is showing (game menu, view menu, search, keyboard, feature panels, a launch). */
    val overlayOpen: Boolean
        get() = menuFor != null || groupMenu || search || TextEntry.request != null || GameEditor.isOpen ||
            Confirm.request != null || DeleteGame.request != null || Panels.anyOpen || openPanels > 0 || LaunchFx.busy || SettingsSearch.open || Welcome.open
}

object Toasts {
    const val DEFAULT_MS = 2800L
    var message by mutableStateOf<String?>(null)
    private var serial = 0
    /** How long the current message stays up. */
    var shownMs = DEFAULT_MS
        private set
    /** What Select (or a tap on the toast) does while it shows, like Undo; null for most toasts. */
    var action by mutableStateOf<Action?>(null)
        private set

    class Action(val label: String, val run: () -> Unit)

    /** [ms]: longer for a prompt that must stay up exactly as long as what it asks for (see PressTwice). */
    fun show(text: String, ms: Long = DEFAULT_MS, action: Action? = null) { message = text; shownMs = ms; this.action = action; serial++ }

    /** Runs the toast's action once, and puts the toast away. */
    fun act() {
        val a = action ?: return
        action = null
        message = null
        a.run()
    }

    val id get() = serial
}

/** Bumped when Shizuku starts, stops or answers a permission request, so rows showing its state refresh. */
object ShizukuWatch {
    var version by mutableIntStateOf(0)
}

@Composable
fun TandemApp(activity: MainActivity) {
    DisposableEffect(Unit) {
        val bump = { ShizukuWatch.version++ }
        val onResult = Shizuku.OnRequestPermissionResultListener { _, _ -> bump() }
        val onBinder = Shizuku.OnBinderReceivedListener { bump() }
        val onDead = Shizuku.OnBinderDeadListener { bump() }
        runCatching {
            Shizuku.addRequestPermissionResultListener(onResult)
            Shizuku.addBinderReceivedListenerSticky(onBinder)
            Shizuku.addBinderDeadListener(onDead)
        }
        onDispose {
            runCatching {
                Shizuku.removeRequestPermissionResultListener(onResult)
                Shizuku.removeBinderReceivedListener(onBinder)
                Shizuku.removeBinderDeadListener(onDead)
            }
        }
    }
    Themes.Driver()
    // Swapped screens: the library is drawn on the bottom screen (BottomScreen.kt) and this one shows the details.
    // The stage stays put through a swap (its wallpaper too); only what's on it changes, with SwapFx's choreography.
    SwapStage(ScreenSlot.Top) {
        if (ScreenRoles.libraryOnBottom) TandemTheme { BottomContent() } else LibraryRoot(activity)
    }
}

/**
 * True inside the library the bottom-screen home borrows while another app has the top screen (LibraryGuest.kt):
 * the stopped top library still runs its watchers (rescans, Collections), so this copy starts none of its own.
 */
val LocalLibraryGuest = androidx.compose.runtime.staticCompositionLocalOf { false }

/** The library UI: screens, popups, toasts. On the top screen normally, on the bottom one when the screens are swapped. */
@Composable
fun LibraryRoot(activity: MainActivity) {
    val resumes = activity.resumeCount.intValue
    val ctx = LocalContext.current
    val hasFiles = remember(resumes) { Environment.isExternalStorageManager() }
    var hasTree by remember(resumes) { mutableStateOf(Prefs.romsTree(ctx) != null) }
    // The wallpaper belongs to the screen (SwapStage), so it doesn't restart when the library moves to the other one.
    TandemTheme {
        Box(Modifier.fillMaxSize()) {
            if (hasFiles && hasTree) Main(resumes) else {
                SetupScreen(hasFiles, hasTree) { hasTree = true }
                // Nothing to load before setup: the startup sequence ends here (StartFlow).
                androidx.compose.runtime.SideEffect { StartFlow.ready() }
            }
            ToastHost()
            SoundCues()
            ThemeOverlay()
        }
    }
}

/** Whether a library scan is running; only one runs at a time, outside any screen (see [ScanRunner]). */
object LibraryScan {
    var scanning by mutableStateOf(false)
        private set
    private var app: Context? = null
    private val runner = ScanRunner(
        MainScope(),
        onScanning = { scanning = it },
        onError = { android.util.Log.w("Tandem", "Library scan failed", it) },
    ) { announce, onlyIfChanged -> app?.let { scanOnce(it, announce, onlyIfChanged) } }

    /** Starts a rescan from anywhere (Settings, menus), with a toast when it's done. */
    fun rescan(ctx: Context) {
        request(ctx, announce = true, onlyIfChanged = false)
    }

    /**
     * Loads the library when nothing has and nothing is loading it (a cold start whose first scan never finished).
     * Safe to call any time (Home, a dead key, the swap check): it does nothing once the library is there.
     */
    fun ensureLoaded(ctx: Context) {
        if (Libraries.all == null && !runner.running) request(ctx, announce = false, onlyIfChanged = false)
    }

    /**
     * Cold start, from MainActivity.onCreate: starts loading the library before the first frame is composed (the
     * work runs on Dispatchers.IO). True when a scan cache from an earlier run exists, so the library from it is
     * expected within a moment and the first frame can wait for it (StartFlow).
     */
    fun startEarly(ctx: Context): Boolean {
        if (Libraries.all != null || !Environment.isExternalStorageManager() || Prefs.romsTree(ctx) == null) return false
        ensureLoaded(ctx)
        return Store.settings.autoImportDone && java.io.File(ctx.filesDir, "scan-cache.json").isFile
    }

    /** Starts a scan, or queues one more pass after the running one (then null). Main thread. */
    internal fun request(ctx: Context, announce: Boolean, onlyIfChanged: Boolean): kotlinx.coroutines.Job? {
        app = ctx.applicationContext
        val job = runner.request(announce, onlyIfChanged)
        if (job == null && announce) Toasts.show("Already looking for games…")
        return job
    }
}

/**
 * Loads (or reloads) the library, importing existing metadata the first time. With [onlyIfChanged] (coming back to
 * Tandem), the library is only replaced when a folder's contents changed. The scan runs outside the caller
 * ([LibraryScan]): cancelling the caller (its screen went away) only stops the wait, never the scan.
 */
suspend fun reloadLibrary(ctx: Context, announce: Boolean = false, onlyIfChanged: Boolean = false) {
    LibraryScan.request(ctx, announce, onlyIfChanged)?.join()
}

private suspend fun scanOnce(ctx: Context, announce: Boolean, onlyIfChanged: Boolean) {
    if (announce) Toasts.show("Looking for games…")
    // The systems list, loaded once for both passes at startup.
    var platforms: List<io.github.zahakj.tandem.Platform>? = null
    var shown: List<io.github.zahakj.tandem.PlatformLibrary>? = null
    if (Libraries.all == null && Store.settings.autoImportDone) {
        // Show the library from the last scan straight away; the real scan below then only re-reads changed folders.
        val cached = withContext(Dispatchers.IO) { Library.scan(Platforms.load(ctx).also { platforms = it }, quick = true) }
        if (cached.isNotEmpty() && Libraries.all == null) {
            io.github.zahakj.tandem.HiddenFolders.follow(cached)
            Libraries.all = cached
            shown = cached
            // Let the home's arrival play before the scan below competes with it.
            delay(1200)
        }
    }
    val before = ScanCache.generation
    val began = SystemClock.uptimeMillis()
    val libs = withContext(Dispatchers.IO) { Library.scan(platforms ?: Platforms.load(ctx)) }
    // How long a scan takes (tools/perf.sh reads it).
    android.util.Log.i("TandemPerf", "scan ${SystemClock.uptimeMillis() - began} ms, changed=${ScanCache.generation != before}")
    if (onlyIfChanged && Libraries.all != null && ScanCache.generation == before) return
    if (shown != null && Libraries.all === shown && ScanCache.generation == before) {
        // No folder changed since the cached library went up: it is the same library, so the home isn't rebuilt.
        io.github.zahakj.tandem.FileChecks.clear()
        io.github.zahakj.tandem.data.DetailsFill.request()
        if (announce) Toasts.show("Found ${plural(Collections.visible(shown.flatMap { it.games }).size, "game")}")
        return
    }
    if (!Store.settings.autoImportDone) {
        val result = withContext(Dispatchers.IO) { Importer.importAll(libs, firstTime = true) }
        Store.updateSettings { it.copy(autoImportDone = true) }
        if (result.games > 0) Toasts.show("Brought over details for ${plural(result.games, "game")} ✨")
    }
    io.github.zahakj.tandem.FileChecks.clear()
    // A hidden folder that was renamed stays hidden: found again before the new scan is shown.
    io.github.zahakj.tandem.HiddenFolders.follow(libs)
    Libraries.all = libs
    // New games (and ones a scrape never reached) get their description from the LaunchBox database on the device.
    io.github.zahakj.tandem.data.DetailsFill.request()
    if (announce) Toasts.show("Found ${plural(Collections.visible(libs.flatMap { it.games }).size, "game")}")
}

@Composable
private fun Main(resumes: Int) {
    val ctx = LocalContext.current
    // Usually already loading since MainActivity.onCreate (LibraryScan.startEarly); never a second pass on top.
    LaunchedEffect(Unit) { LibraryScan.ensureLoaded(ctx) }
    // Games added or removed while Tandem was in the background show up when it comes back. The scan cache makes
    // this cheap: only folders whose modification time changed are listed again.
    // It waits a moment, so coming back from a game draws and settles before any scan work or its results arrive.
    val guest = LocalLibraryGuest.current
    LaunchedEffect(resumes) {
        // Back with no library at all (a cold start whose first scan never finished): load it now.
        if (Libraries.all == null) LibraryScan.ensureLoaded(ctx)
        if (guest) return@LaunchedEffect
        if (resumes > 1) DeleteGame.afterResume(ctx)
        if (resumes > 1 && Libraries.all != null) {
            delay(700)
            reloadLibrary(ctx, onlyIfChanged = true)
        }
    }
    // Collections, genres and Recently played are worked out in the background whenever the library or its
    // details change, so the screens that show them find them ready instead of sorting the library mid-transition.
    if (!guest) LaunchedEffect(Unit) {
        androidx.compose.runtime.snapshotFlow { Triple(Libraries.all, Store.meta, io.github.zahakj.tandem.Privacy.view) }
            .collectLatest { withContext(Dispatchers.Default) { Collections.warm() } }
    }
    // The first start that finds a Cocoon backup brings that backup's setup over by itself (undo in Settings).
    val libraryReady = Libraries.all != null
    LaunchedEffect(libraryReady) { if (libraryReady) CocoonSetup.autoImport(ctx) }
    // Nothing on a new screen took focus (it is empty, say): don't keep showing the old screen's item below.
    LaunchedEffect(Nav.route) {
        val opened = SystemClock.uptimeMillis()
        delay(600)
        if (Selection.setAt < opened && !Nav.overlayOpen) Selection.current = Selected.Home
    }

    HomeBackGuard()
    // Back on Home never leaves Tandem, whatever the layout. Layouts with their own Home Back action (scroll to top) win.
    BackHandler(enabled = Nav.route == Route.Home && !Nav.overlayOpen) { }
    BackHandler(enabled = Nav.route != Route.Home && !Nav.overlayOpen) { Nav.back() }
    LaunchedEffect(Unit) {
        Buttons.events.collect { b ->
            // Only the Tandem on screen acts (another copy may linger in a second task).
            if (ActiveHost.token !== activityOf(ctx)) return@collect
            if (Nav.overlayOpen) return@collect
            when (b) {
                Button.Y -> openMenuFor(Selection.current)
                // On the icon grid (or everywhere, if chosen) X swaps the two screens.
                // In Settings, X searches the settings.
                Button.X -> if (isSettingsRoute(Nav.route)) SettingsSearch.show() else if (!ScreenRoles.onX(ctx) && Nav.route != Route.Apps) Nav.search = true
                // Start: the Start panel (StartPanel.kt), or inside Settings back to its first screen and out.
                Button.Start -> if (Nav.inSettings) Nav.toggleSettings() else StartMenu.show()
                Button.Select -> surpriseMe()
                Button.L1, Button.R1 -> cycle(if (b == Button.R1) 1 else -1)
                // L2 / R2: in a list of games, the previous or next letter (or year, rating); elsewhere a page up or down.
                Button.L2, Button.R2 -> if (!QuickJump.jump(if (b == Button.R2) 1 else -1)) {
                    (ctx as? MainActivity)?.pageJump(if (b == Button.R2) android.view.KeyEvent.KEYCODE_DPAD_DOWN else android.view.KeyEvent.KEYCODE_DPAD_UP, 5)
                }
            }
        }
    }

    val libs = Libraries.all
    if (libs == null) {
        // At startup the splash is drawn over the stage instead (StartSplash), so it can fade while the home arrives.
        if (!StartFlow.waiting) Splash()
        return
    }
    // The home is composed: a held first frame may draw, the splash fades and the screens arrive (StartFlow).
    androidx.compose.runtime.SideEffect { StartFlow.ready() }
    val iconGrid = IconGrid.enabled
    val fullscreen = isFullscreen(Nav.route, iconGrid)
    PaddedScrolling {
        // While a popup is open the screen keeps its focus but ignores the D-pad and A, so nothing behind the
        // popup can be moved to or started.
        Box(Modifier.fillMaxSize().menuBackdrop().onPreviewKeyEvent { (Nav.overlayOpen || Layers.top != null) && !Layers.focusInPopup && it.isNavKey() }) {
            // The top bar lies over the screens instead of pushing them down: each screen keeps a fixed room for it
            // (none on fullscreen ones), and the bar only fades and slides in its layer. Resizing the screen area
            // during a transition would re-lay out Home's grid (sized from its height) and Settings on every frame.
            // Left and Right never leave the screen for the top bar.
            Box(Modifier.fillMaxSize().screenContent()) {
                // Home and the first Settings screen stay composed (hidden, paused and out of focus's reach while
                // another screen shows), so opening or closing Settings starts moving on the very next frame instead
                // of first building a whole screen. Settings is composed ahead once Tandem has been idle a moment,
                // or as soon as the Start panel opens (its Settings page leads there), then measured ahead too.
                KeptScreen(Route.Home, topRoom = if (iconGrid) 0.dp else TopBarHeight) { HomeScreen(libs) }
                var settingsWarm by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    while (true) {
                        delay(1500)
                        if (SystemClock.elapsedRealtime() - io.github.zahakj.tandem.Idle.last > 1500 && !Nav.overlayOpen) break
                    }
                    settingsWarm = true
                }
                if (StartMenu.open && !settingsWarm) LaunchedEffect(Unit) {
                    // After the panel has slid out (its first frames stay light), before a row is likely chosen.
                    delay(260)
                    settingsWarm = true
                }
                KeptScreen(Route.Settings, warm = settingsWarm, topRoom = TopBarHeight) { SettingsScreen() }
                AnimatedContent(
                    targetState = Nav.route.takeIf { it !in KeptRoutes },
                    modifier = Modifier.fillMaxSize(),
                    // Fades carry the timing (they keep the old screen until it has gone); the slide is a
                    // graphics-layer translation (routeMotion), so a transition never re-places layouts per frame.
                    transitionSpec = {
                        if (Motion.style == AnimationStyle.Off) {
                            EnterTransition.None togetherWith ExitTransition.None
                        } else {
                            fadeIn(Motion.screenIn()) togetherWith fadeOut(Motion.screenOut())
                        }
                    },
                    label = "route",
                ) { route ->
                    // Composed once for its whole stay; only the layer below follows the animation.
                    if (route != null) Box(Modifier.fillMaxSize().routeMotion(this).padding(top = if (isFullscreen(route, iconGrid)) 0.dp else TopBarHeight)) {
                        RouteContent(route, libs)
                    }
                }
            }
            TopBarOverlay(!fullscreen)
        }
        CornerHints()
        ScrapeBadge(fullscreen)
        QuickJumpBubble()
        FolderPopupHost()
        IconGridMenuHost()
        FolderDesignHost()
        SmartFolderFormHost()
        StartPanelHost()
        // Search sits below the menus, so Y on a result opens the game menu on top of it.
        SearchOverlay()
        SettingsSearchOverlay()
        GroupMenu()
        GameMenu()
        GameEditorOverlays()
        DeleteGameOverlay()
        ScrapeHost()
        HomeHost()
        // The welcome and the artwork setup can ask questions and take typed keys, so the confirm popup and the
        // keyboard are drawn above them.
        WelcomeOverlay()
        ArtSetupOverlay()
        BackupUi.Host()
        ConfirmOverlay()
        TextEntryOverlay()
        LaunchOverlay()
        FrameTimingOverlay()
    }
}

/**
 * The route slide, as a graphics-layer translation read in the draw phase: the screen arriving comes in from a fifth
 * of the width on [Nav.dir]'s side, the one leaving drifts a sixth the other way. Same curve and length as the fade.
 */
@Composable
private fun Modifier.routeMotion(scope: androidx.compose.animation.AnimatedVisibilityScope): Modifier {
    if (Motion.style == AnimationStyle.Off) return this
    val shift = scope.transition.animateFloat(
        transitionSpec = {
            if (targetState == androidx.compose.animation.EnterExitState.PostExit) Motion.screenOut() else Motion.screenIn()
        },
        label = "route-shift",
    ) { state ->
        when (state) {
            androidx.compose.animation.EnterExitState.PreEnter -> Nav.dir / 5f
            androidx.compose.animation.EnterExitState.Visible -> 0f
            androidx.compose.animation.EnterExitState.PostExit -> -Nav.dir / 6f
        }
    }
    return graphicsLayer { translationX = shift.value * size.width }
}

/** Screens kept composed between visits (see [KeptScreen]). */
private val KeptRoutes = setOf<Route>(Route.Home, Route.Settings)

/** The room every screen with the top bar leaves above itself (the bar lies over the screens, see [TopBarOverlay]). */
val TopBarHeight = 52.dp

/** Screens without the top bar: the icon grid home, and folders when they open fullscreen. */
private fun isFullscreen(route: Route, iconGrid: Boolean): Boolean =
    // Fullscreen folders: inside a system or collection the top bar slides away and the grid gets the whole screen.
    (Store.settings.look.fullscreenFolders || (iconGrid && Store.settings.look.gridFolders == "fullscreen")) &&
        (route is Route.System || route is Route.Collection || route == Route.Genres) ||
        // The icon grid has no top bar: the icons get the whole screen.
        (iconGrid && route == Route.Home)

/**
 * The top bar over the screens: fades and slides in or out in its layer only (the screens below never change size).
 * While hidden it stays composed but isn't placed (focus can't reach it) and its lifecycle rests, like [KeptScreen].
 */
@Composable
private fun TopBarOverlay(shown: Boolean) {
    val state = remember { androidx.compose.animation.core.MutableTransitionState(shown) }
    state.targetState = shown
    @Suppress("DEPRECATION")
    val transition = androidx.compose.animation.core.updateTransition(state, label = "top-bar")
    val p = transition.animateFloat(
        transitionSpec = { if (targetState) Motion.screenIn() else Motion.screenOut() }, label = "top-bar-p",
    ) { if (it) 1f else 0f }
    val drawn = shown || transition.isRunning || transition.currentState
    val parent = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val owner = remember(parent) { CappedLifecycleOwner(parent) }
    DisposableEffect(owner) { owner.attach(); onDispose { owner.detach() } }
    androidx.compose.runtime.SideEffect {
        owner.cap = if (drawn) androidx.lifecycle.Lifecycle.State.RESUMED else androidx.lifecycle.Lifecycle.State.CREATED
    }
    @Suppress("DEPRECATION")
    androidx.compose.runtime.CompositionLocalProvider(
        androidx.lifecycle.compose.LocalLifecycleOwner provides owner,
        androidx.compose.ui.platform.LocalLifecycleOwner provides owner,
    ) {
        androidx.compose.ui.layout.Layout(
            content = {
                Box(
                    Modifier.fillMaxWidth().height(TopBarHeight).graphicsLayer {
                        val v = p.value
                        alpha = v.coerceIn(0f, 1f)
                        translationY = -(1f - v) * size.height * 0.6f
                    },
                ) { TopBar() }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { measurables, c ->
            val placeables = measurables.map { it.measure(c.copy(minHeight = 0)) }
            val h = placeables.maxOfOrNull { it.height } ?: 0
            layout(c.maxWidth, h) { if (drawn) placeables.forEach { it.place(0, 0) } }
        }
    }
}

/**
 * A screen that stays composed once it has been shown (or [warm]ed). While another screen is in front it is not
 * placed or drawn, its items can't take focus (its [KeptLayer] is dormant, and focus search skips unplaced items),
 * and its lifecycle is held at CREATED, so videos, floating motion and polling inside it stop. It is still measured
 * (once ahead after warming, then only when something in it changes), so showing it again doesn't lay out a whole
 * screen in the first frame. Showing it is only a fade and slide in the graphics layer, the same as [routeMotion]:
 * no composition or layout in the frame that starts the transition. [topRoom] is the room it leaves for the top bar.
 */
@Composable
private fun KeptScreen(route: Route, warm: Boolean = false, topRoom: androidx.compose.ui.unit.Dp = 0.dp, content: @Composable () -> Unit) {
    val shown = Nav.route == route
    val alive = remember { booleanArrayOf(false) }
    if (shown || warm) alive[0] = true
    if (!alive[0]) return
    // Measured ahead from the frame after it is first composed, so composing and measuring land in different frames.
    var measureAhead by remember { mutableStateOf(shown) }
    if (!measureAhead) LaunchedEffect(Unit) { androidx.compose.runtime.withFrameNanos { }; measureAhead = true }
    val layer = remember { KeptLayer().apply { dormant = !shown } }
    androidx.compose.runtime.SideEffect {
        // Shown again: focus is handed back, so the target is worked out afresh from the item remembered (it kept
        // the one from its last hand back, often the first tile, which then took the focus back from a page).
        if (layer.dormant && shown) Nav.focusTick++
        layer.dormant = !shown
    }
    // Starts hidden, so a screen composed by being opened still fades and slides in.
    val state = remember { androidx.compose.animation.core.MutableTransitionState(false) }
    state.targetState = shown
    @Suppress("DEPRECATION")
    val transition = androidx.compose.animation.core.updateTransition(state, label = "kept-${route.key}")
    // Where it slides from or to, fixed when it starts showing or hiding (Nav.dir is set before the route changes).
    val from = remember(shown) { if (shown) Nav.dir / 5f else -Nav.dir / 6f }
    val p = transition.animateFloat(
        transitionSpec = { if (targetState) Motion.screenIn() else Motion.screenOut() }, label = "kept-p",
    ) { if (it) 1f else 0f }
    val drawn = shown || transition.isRunning || transition.currentState
    val parent = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val owner = remember(parent) { CappedLifecycleOwner(parent) }
    DisposableEffect(owner) { owner.attach(); onDispose { owner.detach() } }
    androidx.compose.runtime.SideEffect {
        owner.cap = if (drawn) androidx.lifecycle.Lifecycle.State.RESUMED else androidx.lifecycle.Lifecycle.State.CREATED
    }
    @Suppress("DEPRECATION")
    androidx.compose.runtime.CompositionLocalProvider(
        androidx.lifecycle.compose.LocalLifecycleOwner provides owner,
        androidx.compose.ui.platform.LocalLifecycleOwner provides owner,
        LocalLayer provides layer,
    ) {
        androidx.compose.ui.layout.Layout(
            content = {
                Box(
                    Modifier.fillMaxSize().graphicsLayer {
                        val v = p.value
                        alpha = v.coerceIn(0f, 1f)
                        translationX = from * (1f - v) * size.width
                    }.padding(top = topRoom),
                ) { content() }
            },
            modifier = Modifier.fillMaxSize(),
        ) { measurables, c ->
            // Hidden: measured (ahead) but not placed, so it isn't drawn and focus search can't reach it.
            if (!drawn) {
                if (measureAhead) measurables.forEach { it.measure(c) }
                layout(c.maxWidth, c.maxHeight) {}
            } else {
                val placeables = measurables.map { it.measure(c) }
                layout(c.maxWidth, c.maxHeight) { placeables.forEach { it.place(0, 0) } }
            }
        }
    }
}

@Composable
private fun RouteContent(route: Route, libs: List<io.github.zahakj.tandem.PlatformLibrary>) {
    when (route) {
        Route.Home -> HomeScreen(libs)
        is Route.System -> SystemScreen(route)
        is Route.Collection -> CollectionScreen(route)
        Route.Genres -> GenresScreen()
        Route.Settings -> SettingsScreen()
        is Route.SettingsPage -> SettingsPage(route.id)
        Route.Systems -> SystemsScreen()
        Route.Apps -> AppsScreen()
        Route.Appearance -> AppearanceScreen()
        Route.ThemePacks -> ThemePacksScreen()
        Route.Scrape -> ScrapeScreen()
        Route.Stats -> StatsScreen()
        Route.Widgets -> WidgetsScreen()
        is Route.Tool -> ToolScreen(route)
    }
}

/** Y: the game menu for a game, or a menu for the system, folder or collection that is highlighted. */
private fun openMenuFor(s: Selected) {
    when (s) {
        is Selected.GameItem -> Nav.openMenu(s.game)
        is Selected.System -> Nav.openGroupMenu(GroupTarget.System(s.library.platform.id))
        is Selected.Group -> (s.target ?: routeTarget())?.let { Nav.openGroupMenu(it) }
        // On Home, Y on a dock app is handled by HomeHost.
        else -> if (Nav.route != Route.Home) routeTarget()?.let { Nav.openGroupMenu(it) }
    }
}

/** The group the current screen shows, for the view menu. */
fun routeTarget(): GroupTarget? = when (val r = Nav.route) {
    is Route.System -> GroupTarget.System(r.platformId, r.folder)
    is Route.Collection -> GroupTarget.Collection(r.id)
    Route.Genres -> GroupTarget.Genres
    else -> null
}

/** L1 / R1: the previous or next system, collection or genre. Systems with every game hidden are skipped. */
private fun cycle(by: Int) {
    fun <T> step(list: List<T>, current: T): T? {
        if (list.size < 2) return null
        val i = list.indexOf(current)
        return if (i < 0) list.first() else list[(i + by + list.size) % list.size]
    }
    when (val r = Nav.route) {
        is Route.System -> {
            val ids = Libraries.all.orEmpty().filter { Collections.visible(it.games).isNotEmpty() || it.platform.id == r.platformId }.map { it.platform.id }
            step(ids, r.platformId)?.let { Nav.replace(Route.System(it), by); Sounds.page() }
        }
        is Route.Collection -> {
            val ids = if (r.id.startsWith("genre:")) {
                Collections.genres().keys.map { "genre:$it" }
            } else {
                // The collections on the home screen, in their order (plus this one, if it has just become empty).
                Collections.home().map { it.id }.let { if (r.id in it) it else it + r.id }
            }
            step(ids, r.id)?.let { Nav.replace(Route.Collection(it), by); Sounds.page() }
        }
        // Settings pages: L1 and R1 flip to the page before or after, like turning pages.
        Route.Appearance, is Route.SettingsPage -> SettingsCategories.idOf(r)?.let { Nav.replace(SettingsCategories.route(SettingsCategories.step(it, by)), by); Sounds.page() }
        else -> Unit
    }
}

/** Settings and its pages (where X searches settings and the corner hints stay away). */
fun isSettingsRoute(r: Route): Boolean = r == Route.Settings || SettingsCategories.idOf(r) != null

/** Select: jump to a random game you haven't touched in a while. */
private fun surpriseMe() {
    val pool = Collections.visibleGames()
    val pick = pool.filter { it.meta.playCount == 0 && it.meta.playSeconds == 0L }.ifEmpty { pool }.randomOrNull() ?: return
    val route = Route.System(pick.platform.id, pick.group)
    FocusMemory.remember(route.key, pick.key)
    when {
        // Already there: the grid scrolls to the pick and highlights it.
        Nav.route == route -> Nav.focusTick++
        // Another grid (often the last surprise): swap it, so Back still goes where you came from.
        Nav.route is Route.System -> Nav.replace(route)
        else -> Nav.go(route)
    }
    Toasts.show("How about ${pick.title}?")
}

@Composable
private fun TopBar() {
    val ctx = LocalContext.current
    val now by rememberMinute()
    val battery by produceState(-1) {
        while (true) {
            value = ctx.getSystemService(BatteryManager::class.java).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            delay(60_000)
        }
    }
    val crumb = when (val r = Nav.route) {
        Route.Home -> null
        is Route.System -> listOfNotNull(Libraries.byId(r.platformId)?.platform?.name ?: "Games", r.folder).joinToString("  ›  ")
        is Route.Collection -> if (r.id.startsWith("genre:")) "Genres  ›  ${r.id.removePrefix("genre:")}" else Collections.byId(r.id)?.title
        Route.Genres -> "Genres"
        Route.Settings -> "Settings"
        Route.Systems -> "Systems"
        Route.Apps -> "Apps"
        Route.Appearance -> "Settings  ›  Look & feel"
        is Route.SettingsPage -> "Settings  ›  ${SettingsCategories.title(r.id)}"
        Route.ThemePacks -> "Look & feel  ›  Theme packs"
        Route.Scrape -> "Artwork & details"
        Route.Stats -> "Play stats"
        Route.Widgets -> "Widgets"
        is Route.Tool -> BuiltInApps.byId(r.id)?.title
    }
    Row(Modifier.fillMaxWidth().height(TopBarHeight).topBarFocus().padding(horizontal = 22.dp), verticalAlignment = Alignment.CenterVertically) {
        Pip(26.dp, bounce = false)
        Spacer(Modifier.width(8.dp))
        Text("Tandem", style = MaterialTheme.typography.headlineSmall, maxLines = 1)
        // The crumb gives way (with an ellipsis) to the clock and buttons on the right.
        Crumb(crumb, Modifier.weight(1f))
        Spacer(Modifier.width(10.dp))
        ScrapePill()
        Spacer(Modifier.width(10.dp))
        TopBarButtons()
        if (battery >= 0 && Store.settings.look.battery) {
            Spacer(Modifier.width(10.dp))
            Pill("$battery%", if (battery <= 20) Palette.Peach else Palette.Mint)
        }
        Clock.format(now)?.let {
            Spacer(Modifier.width(10.dp))
            Text(it, style = MaterialTheme.typography.titleLarge, maxLines = 1)
        }
    }
}

@Composable
private fun Crumb(crumb: String?, modifier: Modifier) {
    val shown = rememberLatest(crumb)
    Box(modifier) {
        AnimatedVisibility(crumb != null, enter = fadeIn() + slideInHorizontally { -it / 3 }, exit = fadeOut()) {
            Text(
                "  ›  ${shown.orEmpty()}", style = MaterialTheme.typography.titleMedium, color = Palette.Muted,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun Splash() {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Pip(96.dp, mood = 1)
        Spacer(Modifier.height(14.dp))
        Text("Tandem", style = MaterialTheme.typography.displaySmall)
        Text("Gathering your games…", style = MaterialTheme.typography.bodyMedium, color = Palette.Muted)
    }
}

/**
 * Pip pops up with a speech bubble for a couple of seconds, in the top-right corner over the clock, so it never
 * covers the card you're on.
 */
@Composable
private fun ToastHost() {
    val message = Toasts.message
    val id = Toasts.id
    LaunchedEffect(id) { if (message != null) { delay(Toasts.shownMs); if (Toasts.id == id) Toasts.message = null } }
    val shown = rememberLatest(message)
    // A toast with an action (Undo): Select runs it while it shows.
    val action = Toasts.action?.takeIf { message != null }
    InterceptButtons(action != null) { b -> if (b == Button.Select) { Toasts.act(); true } else false }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopEnd) {
        AnimatedVisibility(
            message != null,
            enter = slideInVertically(spring(Spring.DampingRatioMediumBouncy)) { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
        ) {
            Row(
                // A rounded rectangle, not a pill: a two-line message keeps its text clear of the curved ends.
                Modifier.padding(horizontal = 14.dp, vertical = 6.dp).widthIn(max = 520.dp)
                    .clip(RoundedCornerShape(22.dp)).background(Palette.CardHigh)
                    .then(if (action != null) Modifier.swallowTaps { Toasts.act() } else Modifier)
                    .padding(start = 10.dp, end = 18.dp, top = 7.dp, bottom = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Pip(26.dp, mood = 1)
                Spacer(Modifier.width(10.dp))
                Text(shown.orEmpty(), Modifier.weight(1f, fill = false), style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (action != null) {
                    Spacer(Modifier.width(12.dp))
                    ButtonHint("Select", action.label, Palette.Butter)
                }
            }
        }
    }
}

/** True when the picked folder is the ROMs folder (or a folder above it), so games inside can be shared. */
private fun isRomsTree(uri: Uri): Boolean {
    val id = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull() ?: return false
    if (!id.startsWith("primary:", ignoreCase = true)) return false
    val path = id.substringAfter(':').trim('/')
    return path.isEmpty() || path.equals("ROMs", ignoreCase = true)
}

/** First run: Tandem needs to read the ROMs folder, and to be able to share game files with emulators. */
@Composable
private fun SetupScreen(hasFiles: Boolean, hasTree: Boolean, onTree: () -> Unit) {
    val ctx = LocalContext.current
    val pickTree = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        if (!isRomsTree(uri)) {
            Toasts.show("That isn't the ROMs folder. Pick \"ROMs\" on the internal storage, then \"Use this folder\"")
            return@rememberLauncherForActivityResult
        }
        ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        Prefs.setRomsTree(ctx, uri)
        onTree()
    }
    val shizukuAlive = remember { runCatching { Shizuku.pingBinder() }.getOrDefault(false) }
    val keys = listOfNotNull("files", "tree", "shizuku".takeIf { shizukuAlive })
    // Start on the first step that still needs doing.
    LaunchedEffect(hasFiles, hasTree) {
        FocusMemory.remember("setup", if (!hasFiles) "files" else if (!hasTree) "tree" else "files")
        Nav.focusTick++
    }
    val target = rememberFocusTarget("setup", keys)
    Row(Modifier.fillMaxSize().padding(28.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(0.8f), horizontalAlignment = Alignment.CenterHorizontally) {
            Pip(130.dp, mood = 1)
            Spacer(Modifier.height(10.dp))
            Text("Hi, I'm Pip!", style = MaterialTheme.typography.displaySmall)
            Text("Let's find your games.", style = MaterialTheme.typography.bodyLarge, color = Palette.Muted)
        }
        Column(Modifier.weight(1.2f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SetupStep(hasFiles, "1  Read your games", "Allow access to all files, so I can look through the ROMs folder and its artwork.", "files", target) {
                ctx.startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${ctx.packageName}")))
            }
            SetupStep(hasTree, "2  Share games with emulators", "Pick the ROMs folder and tap \"Use this folder\". Some emulators only open games shared this way.", "tree", target) {
                if (!hasFiles) Toasts.show("Step 1 first, then this one")
                else pickTree.launch(DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:ROMs"))
            }
            if (shizukuAlive) {
                val ready = remember(ShizukuWatch.version) { Shell.ready() }
                SetupStep(ready, "✦  Resume games (optional)", "Allow me in Shizuku, so I can bring a game back right where you left it.", "shizuku", target) {
                    if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) Shizuku.requestPermission(1)
                }
            }
        }
    }
}

@Composable
private fun SetupStep(done: Boolean, title: String, detail: String, key: String, target: String?, onClick: () -> Unit) {
    FocusCard(Modifier.fillMaxWidth().takesFocus("setup", key, key == target), glow = if (done) Palette.Mint else Palette.Peach, onClick = onClick) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text(detail, style = MaterialTheme.typography.bodyMedium, color = Palette.Muted)
            }
            Spacer(Modifier.width(10.dp))
            Pill(if (done) "Done ✓" else "Set up", if (done) Palette.Mint else Palette.Peach)
        }
    }
}

/** Shared enter/exit for overlay panels: a bouncy pop. */
val popIn = scaleIn(spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium), initialScale = 0.85f) + fadeIn(tween(150))
val popOut = scaleOut(tween(120), targetScale = 0.92f) + fadeOut(tween(120))

/** The MainActivity behind a context (Compose may wrap it). */
fun activityOf(ctx: android.content.Context): MainActivity? {
    var c: android.content.Context? = ctx
    while (c is android.content.ContextWrapper) { if (c is MainActivity) return c; c = c.baseContext }
    return null
}
