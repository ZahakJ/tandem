package io.github.zahakj.tandem.ui

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.zahakj.tandem.AndroidApps
import io.github.zahakj.tandem.AppMove
import io.github.zahakj.tandem.HiddenItem
import io.github.zahakj.tandem.HiddenItems
import io.github.zahakj.tandem.LaunchDisplay
import io.github.zahakj.tandem.Libraries
import io.github.zahakj.tandem.data.AppPrefs
import io.github.zahakj.tandem.data.AppScreen
import io.github.zahakj.tandem.MainActivity
import io.github.zahakj.tandem.Privacy
import io.github.zahakj.tandem.RunningGames
import io.github.zahakj.tandem.Selected
import io.github.zahakj.tandem.Selection
import io.github.zahakj.tandem.Shell
import io.github.zahakj.tandem.data.HomeStore
import io.github.zahakj.tandem.data.IconGridStore
import io.github.zahakj.tandem.data.Store
import androidx.compose.ui.draw.clip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Tandem as the Android Home app. */
object HomeMode {
    fun isDefault(ctx: Context): Boolean {
        val roles = ctx.getSystemService(RoleManager::class.java)
        if (roles != null && roles.isRoleAvailable(RoleManager.ROLE_HOME)) return roles.isRoleHeld(RoleManager.ROLE_HOME)
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return ctx.packageManager.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName == ctx.packageName
    }

    /** Android's own list of Home apps, where the user can pick another one. */
    fun openHomeSettings(ctx: Context) {
        val tries = listOf(Intent(Settings.ACTION_HOME_SETTINGS), Intent(Settings.ACTION_SETTINGS))
        for (i in tries) if (runCatching { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess) return
    }
}

/** A launchable Android app. */
data class AppEntry(val pkg: String, val label: String)

/** The dock: favourite Android apps on the home screen. */
object Dock {
    /** The All apps button at the dock's start, as a [io.github.zahakj.tandem.Selected.App] package. */
    const val ALL_APPS = "*"

    /** A dock entry that is one of Tandem's own apps ("tandem:settings"), not an Android package. */
    fun builtIn(key: String): BuiltInApp? = if (key.startsWith("tandem:")) BuiltInApps.byId(key.removePrefix("tandem:")) else null

    /**
     * How to open [pkg]: its launcher activity, or for a launcher that only has a Home activity (DroidDeck) that one,
     * so it can sit in the dock even though it is kept out of Android Games.
     */
    fun launchIntent(ctx: Context, pkg: String): Intent? {
        val pm = ctx.packageManager
        pm.getLaunchIntentForPackage(pkg)?.let { return it }
        val home = runCatching {
            pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setPackage(pkg), 0).firstOrNull()
        }.getOrNull() ?: return null
        return Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setClassName(home.activityInfo.packageName, home.activityInfo.name)
    }

    fun launchable(ctx: Context, pkg: String): Boolean = if (pkg.startsWith("tandem:")) builtIn(pkg) != null else launchIntent(ctx, pkg) != null

    /** The pinned apps, or a starting set: Android settings, the browser and Moonlight. */
    fun pinned(ctx: Context): List<String> {
        HomeStore.settings.dock?.let { list -> return list.filter { launchable(ctx, it) } }
        val pm = ctx.packageManager
        val browser = pm.resolveActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://example.org")), PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName?.takeIf { it != "android" }
            ?: pm.queryIntentActivities(Intent(Intent.ACTION_VIEW, Uri.parse("https://example.org")), 0).firstOrNull()?.activityInfo?.packageName
        return listOfNotNull("com.android.settings", browser, "com.limelight").distinct().filter { launchable(ctx, it) }
    }

    fun togglePin(ctx: Context, pkg: String) {
        val now = pinned(ctx)
        val pinned = pkg in now
        HomeStore.updateSettings { it.copy(dock = if (pinned) now - pkg else now + pkg) }
        Toasts.show(if (pinned) "Removed from the dock" else "Added to the dock ★")
    }

    fun move(ctx: Context, pkg: String, by: Int) {
        val list = pinned(ctx).toMutableList()
        val i = list.indexOf(pkg)
        val j = (i + by).coerceIn(0, list.lastIndex)
        if (i < 0 || i == j) return
        list.add(j, list.removeAt(i))
        HomeStore.updateSettings { it.copy(dock = list) }
    }

    /**
     * Opens [pkg] on its screen: its saved one, else the default for apps, else where it was started from
     * ([LaunchDisplay.appWantsBottom]); [bottom] picks the bottom (true) or top screen this once. Every app launch
     * comes through here (dock, drawer, search, widgets, Android Games, grid tiles). An app in Android Games starts
     * the way its game does (launch animation, play time).
     */
    fun open(ctx: Context, pkg: String, bottom: Boolean? = null) {
        builtIn(pkg)?.let { it.open(ctx); return }
        val game = Libraries.byId(AndroidApps.PLATFORM_ID)?.games?.firstOrNull { AndroidApps.packageOf(it) == pkg }
        if (game != null) { io.github.zahakj.tandem.Launcher.launch(ctx, game, bottom); return }
        val intent = launchIntent(ctx, pkg)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        if (intent == null) { Toasts.show("That app won't open"); return }
        val chosen = bottom != null || AppPrefs.screen(pkg) != AppScreen.AUTO
        val down = (bottom ?: LaunchDisplay.appWantsBottom(pkg)) && LaunchDisplay.dualScreen(ctx)
        // On the top screen, the bottom one shows this app (never Android's own app list) while it is in front
        // (SecondaryHome.kt); on the bottom one, Tandem's own bottom screen makes way for it.
        if (down) MainActivity.live?.get()?.hideBottomScreen() else io.github.zahakj.tandem.SecondaryHome.beforeLeave(ctx, pkg)
        if (runCatching { ctx.startActivity(intent, LaunchDisplay.appOptions(ctx, down)) }.isFailure) { Toasts.show("That app won't open"); return }
        // A chosen screen wins over where the app already runs (Android would bring it up there).
        if (chosen) inBackground({ AppMove.ensureOn(ctx, pkg, down) })
    }

    /** Every app with a launcher icon, except Tandem. Blocking. */
    fun allApps(ctx: Context): List<AppEntry> {
        val pm = ctx.packageManager
        // Launchers with only a Home activity (DroidDeck) count as apps too, so they can be pinned to the dock.
        val home = runCatching { pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0) }.getOrDefault(emptyList())
            .map { it.activityInfo.packageName }.filter { it != "android" && !it.startsWith("com.android.") }
        return (pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .map { it.activityInfo.packageName } + home)
            .distinct()
            .filter { it != ctx.packageName }
            .map { AppEntry(it, RunningGames.appLabel(ctx, it)) }
            .sortedBy { it.label.lowercase() }
    }
}

/** The dock row on the home screen. */
@Composable
fun DockRow(dock: List<String>, target: String?, state: androidx.compose.foundation.lazy.LazyListState) {
    val ctx = LocalContext.current
    val route = Route.Home.key
    Column {
        LazyRow(Modifier.fadingEdges(state, horizontal = true).focusRow(), state = state, contentPadding = PaddingValues(horizontal = 22.dp, vertical = FocusRoom), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            items(dock, key = { it }) { pkg ->
                AppTile(pkg, RunningGames.appLabel(ctx, pkg), Modifier.takesFocus(route, "app:$pkg", target == "app:$pkg")) {
                    Dock.open(ctx, pkg)
                }
            }
            item("allapps") {
                FocusCard(
                    Modifier.size(width = 96.dp, height = 104.dp).takesFocus(route, "app:*", target == "app:*"),
                    glow = Palette.Sky,
                    onFocus = { Panels.focusedApp = null; Selection.current = Selected.App(Dock.ALL_APPS) },
                    onClick = { Panels.openApps() },
                ) {
                    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("⊞", style = MaterialTheme.typography.displaySmall, color = Palette.Sky)
                        Text("All apps", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

@Composable
private fun AppTile(pkg: String, label: String, modifier: Modifier, pinned: Boolean = false, faded: Boolean = false, onFocus: () -> Unit = {}, onClick: () -> Unit) {
    FocusCard(
        modifier.size(width = 96.dp, height = 104.dp).liftOrigin().onFocusChanged { if (!it.isFocused && Panels.focusedApp == pkg) Panels.focusedApp = null },
        glow = Palette.Sky,
        onFocus = { Panels.focusedApp = pkg; Selection.current = Selected.App(pkg); onFocus() },
        entry = true,
        onClick = onClick,
    ) {
        Column(
            Modifier.fillMaxSize().graphicsLayer { alpha = if (faded) 0.4f else 1f }.padding(8.dp),
            verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AppIcon(pkg, Modifier.size(52.dp).clip(dockIconShape))
            Spacer(Modifier.height(6.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        }
        if (pinned) Text("★", Modifier.align(Alignment.TopEnd).padding(6.dp), color = Palette.Butter, style = MaterialTheme.typography.labelLarge)
    }
}

/** The All apps drawer's search and Show hidden apps switch; both start over each time it opens. */
object AppsDrawerState {
    var query by mutableStateOf("")
    var showHidden by mutableStateOf(false)

    /** The on-screen keyboard; the list narrows as you type. */
    fun search() {
        TextEntry.ask("Search apps", query, onChange = { query = it.trim() }) { query = it.trim() }
    }
}

/**
 * Every Android app, with the dock's apps first. A opens, Y opens the app menu (AppActionMenu.kt), X searches.
 * Apps hidden from the drawer stay out unless Show hidden apps is on.
 */
@Composable
fun AppsDrawer() {
    val ctx = LocalContext.current
    LaunchedEffect(Panels.apps) { if (Panels.apps) { AppsDrawerState.query = ""; AppsDrawerState.showHidden = false } }
    OverlayPanel(Panels.apps, onClose = { Panels.close() }) {
        val prefs = AppPrefs.data
        val apps by produceState<List<AppEntry>?>(null, prefs) { value = withContext(Dispatchers.IO) { Dock.allApps(ctx) } }
        val dockList = remember(HomeStore.settings.dock) { Dock.pinned(ctx) }
        val dock = remember(dockList) { dockList.toSet() }
        val stockHome = remember { io.github.zahakj.tandem.SecondaryHome.stockHome(ctx) != null }
        val query = AppsDrawerState.query
        val showHidden = AppsDrawerState.showHidden
        val hiddenCount = remember(apps, prefs) { apps.orEmpty().count { prefs.of(it.pkg).hidden } }
        val shown = remember(apps, prefs, query, showHidden) {
            val q = query.lowercase()
            apps?.filter { a ->
                (showHidden || !prefs.of(a.pkg).hidden) && (q.isEmpty() || a.label.lowercase().contains(q) || a.pkg.lowercase().contains(q))
            }
        }
        PanelSurface(Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("All apps", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f), maxLines = 1)
                ButtonHint("A", "Open", Palette.Mint)
                Spacer(Modifier.width(12.dp))
                ButtonHint("Y", "Options", Palette.Butter)
                Spacer(Modifier.width(12.dp))
                ButtonHint("X", "Search", Palette.Sky)
            }
            Spacer(Modifier.height(6.dp))
            Row(
                Modifier.padding(horizontal = FocusRoomSide, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                FocusCard(
                    Modifier.weight(1f).height(44.dp).remembersFocus("apps", "search", false), glow = Palette.Sky, shape = RoundedCornerShape(14.dp),
                    onFocus = { Panels.focusedApp = null }, onClick = { AppsDrawerState.search() },
                ) {
                    Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        GlyphIcon(Glyph.Search, Palette.Sky, Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            query.ifEmpty { "Search apps" }, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
                            color = if (query.isEmpty()) Palette.Muted else Palette.Text, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (hiddenCount > 0 || showHidden) FocusCard(
                    Modifier.height(44.dp).remembersFocus("apps", "hidden", false), glow = Palette.Lavender, shape = RoundedCornerShape(14.dp),
                    onFocus = { Panels.focusedApp = null }, onClick = { AppsDrawerState.showHidden = !showHidden },
                ) {
                    Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Show hidden apps", style = MaterialTheme.typography.titleSmall, maxLines = 1)
                        Spacer(Modifier.width(8.dp))
                        Pill(if (showHidden) "On" else "$hiddenCount", Palette.Lavender)
                    }
                }
            }
            val list = shown
            if (list == null) {
                PipNote("Gathering apps…", "Just a moment.")
            } else {
                val grid = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
                val pinned = if (query.isEmpty()) dockList.filter { p -> Dock.builtIn(p) == null && (showHidden || !prefs.of(p).hidden) } else emptyList()
                val first = pinned.firstOrNull()?.let { "dock:$it" } ?: list.firstOrNull()?.pkg ?: "clear"
                LazyVerticalGrid(
                    GridCells.Adaptive(96.dp),
                    Modifier.fadingEdges(grid),
                    state = grid,
                    contentPadding = PaddingValues(horizontal = FocusRoomSide, vertical = FocusRoom),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // The dock's apps first, while not searching.
                    if (pinned.isNotEmpty()) {
                        item("dock-title", span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                            Text("On the dock", style = MaterialTheme.typography.labelLarge, color = Palette.Muted, maxLines = 1)
                        }
                        items(pinned, key = { "dock:$it" }) { p ->
                            AppTile(p, RunningGames.appLabel(ctx, p), Modifier.remembersFocus("apps", "dock:$p", "dock:$p" == first), faded = prefs.of(p).hidden) {
                                Panels.close(); Dock.open(ctx, p)
                            }
                        }
                        item("all-title", span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                            Text("All apps", style = MaterialTheme.typography.labelLarge, color = Palette.Muted, maxLines = 1)
                        }
                    }
                    items(list, key = { it.pkg }) { a ->
                        AppTile(a.pkg, a.label, Modifier.remembersFocus("apps", a.pkg, a.pkg == first), pinned = a.pkg in dock, faded = prefs.of(a.pkg).hidden) {
                            Panels.close(); Dock.open(ctx, a.pkg)
                        }
                    }
                    if (list.isEmpty()) {
                        item("empty", span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                val hiddenHits = query.isNotEmpty() && !showHidden &&
                                    apps.orEmpty().any { prefs.of(it.pkg).hidden && it.label.lowercase().contains(query.lowercase()) }
                                PipNote(
                                    if (query.isEmpty()) "No apps to show" else "No apps match \"$query\"",
                                    if (hiddenHits) "One you hid from the drawer matches." else "Try fewer letters, or another name.", mood = 2,
                                )
                                PanelRow(
                                    if (hiddenHits) "Show hidden apps" else if (query.isEmpty()) "Show hidden apps" else "Clear the search",
                                    null, "apps", "clear", true, Palette.Sky,
                                ) {
                                    if (hiddenHits || query.isEmpty()) AppsDrawerState.showHidden = true else AppsDrawerState.query = ""
                                    Nav.focusTick++
                                }
                            }
                        }
                    }
                    // Android's own Home and app list, only when asked for: Tandem never opens them by itself.
                    if (stockHome && query.isEmpty()) {
                        item("stockhome", span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                            PanelRow("Android home (the system launcher)", "Home returns here", "apps", "stockhome", false, Palette.Lavender) {
                                Panels.close()
                                if (!io.github.zahakj.tandem.SecondaryHome.openStockHome(ctx)) Toasts.show("Android's own Home screen won't open")
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A row in one of this area's popups: a label, an optional value and an action. */
@Composable
fun PanelRow(label: String, value: String?, routeKey: String, key: String, first: Boolean, color: androidx.compose.ui.graphics.Color = Palette.Mint, onClick: () -> Unit) {
    FocusCard(Modifier.fillMaxWidth().remembersFocus(routeKey, key, first), glow = color, shape = RoundedCornerShape(14.dp), onClick = onClick) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            if (value != null) Pill(value, color)
        }
    }
}

/** Explains Home-screen mode and opens Android's Home app chooser. */
@Composable
fun HomeExplainer() {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        tick++
        if (HomeMode.isDefault(ctx)) { Toasts.show("Tandem is your Home screen now ✨"); Panels.close() }
    }
    OverlayPanel(Panels.homeExplainer, onClose = { Panels.close() }) {
        val isHome = remember(tick) { HomeMode.isDefault(ctx) }
        val key = "homeexplainer"
        PanelSurface(Modifier.width(560.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Pip(64.dp, mood = 1)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Tandem as your Home screen", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "Press Home anywhere and you land right back here, with your running game one button away. " +
                            "Your other apps stay in the dock and the All apps drawer. You can switch back any time.",
                        style = MaterialTheme.typography.bodyMedium, color = Palette.Muted,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                if (isHome) {
                    PanelRow("Tandem is your Home screen", "On ✓", key, "status", false, Palette.Mint) { }
                    PanelRow("Use a different Home app", null, key, "other", true, Palette.Lavender) { Panels.close(); HomeMode.openHomeSettings(ctx) }
                } else {
                    PanelRow("Make Tandem my Home screen", null, key, "choose", true, Palette.Mint) {
                        val roles = ctx.getSystemService(RoleManager::class.java)
                        val intent = roles?.takeIf { it.isRoleAvailable(RoleManager.ROLE_HOME) }?.createRequestRoleIntent(RoleManager.ROLE_HOME)
                        if (intent == null || runCatching { ask.launch(intent) }.isFailure) HomeMode.openHomeSettings(ctx)
                    }
                    PanelRow("Open Android's Home app settings", null, key, "settings", false, Palette.Sky) { HomeMode.openHomeSettings(ctx) }
                }
                PanelRow("Not now", null, key, "close", false, Palette.Lavender) { Panels.close() }
            }
        }
    }
}

/** The "show hidden games" toggles. In private mode they stay put: only the secret combo reveals hidden games. */
fun toggleShowHidden() {
    if (Privacy.enabled) {
        Toasts.show("Hidden games are private")
        return
    }
    // Tucking them away also ends anything shown on its own from Start › Hidden.
    if (Privacy.showHidden) Privacy.tuckAway()
    else io.github.zahakj.tandem.data.Store.updateSettings { it.copy(showHidden = true) }
}

private val SCREENSAVER_STEPS = listOf(0, 2, 5, 10, 15, 30)

/** Settings for running games, the Home screen, the screensaver and privacy. */
@Composable
fun homeSettings(): SettingSection {
    val ctx = LocalContext.current
    val s = HomeStore.settings
    val resumes = settingsResumes()
    val isHome = Facts.run { ensure(ctx); Facts.isHome }
    val shizuku = remember(resumes, ShizukuWatch.version) { Shell.ready() }
    return SettingSection(
        "Home & play",
        listOf(
            SettingRow(
                "Use Tandem as the Home screen", if (isHome) "On" else "Off", Palette.Mint, Glyph.Home,
                "Make the Home button bring you to Tandem, like a console. You can switch back to the usual launcher any time.",
                opens = true, keywords = "launcher default home button frontend",
            ) { Panels.openHomeExplainer() },
            SettingRow(
                "Close previous game",
                if (!shizuku) "Needs Shizuku" else onOff(RunningGames.closePreviousOn()), Palette.Mint, Glyph.Gamepad,
                "When you start a different game, the one still running in the background is closed first, so it doesn't use memory or battery. Needs Shizuku.",
                toggle = if (!shizuku) null else RunningGames.closePreviousOn(), keywords = "close the previous game when starting another kill emulator memory",
            ) {
                if (!shizuku) Toasts.show("This needs Shizuku running, with Tandem allowed")
                else HomeStore.updateSettings { it.copy(closePrevious = !RunningGames.closePreviousOn()) }
            },
            SettingRow(
                "Ask before closing a game",
                if (!shizuku) "Needs Shizuku" else onOff(s.askBeforeClosing), Palette.Mint, Glyph.Shield,
                "When starting a game would close one that's still running, Tandem asks first: resume it, or close it and start the new one. Needs Shizuku.",
                toggle = if (!shizuku) null else s.askBeforeClosing,
                keywords = "ask before closing a running game confirm warn kill emulator unsaved progress lose save resume prompt don't ask again",
            ) {
                if (!shizuku) Toasts.show("This needs Shizuku running, with Tandem allowed")
                else HomeStore.updateSettings { it.copy(askBeforeClosing = !it.askBeforeClosing) }
            },
            SettingRow(
                "Game switcher", "Hold Select", Palette.Mint, Glyph.Swap,
                "Hold Select anywhere to see the games still running, jump back into one or close them. A opens it now.",
                keywords = "running games multitask recent close", action = "Open now",
            ) { Panels.openSwitcher() },
            SettingRow(
                "Apps dock on the home screen", onOff(s.showDock), Palette.Sky, Glyph.Grid, toggle = s.showDock,
                help = "Your favourite Android apps on the home screen.",
            ) { HomeStore.updateSettings { it.copy(showDock = !it.showDock) } },
            SettingRow(
                "Choose dock apps", "${Dock.pinned(ctx).size} apps", Palette.Sky, Glyph.Grid,
                "Pick which apps sit on the dock and in what order.", opens = true, keywords = "pin apps favourite",
            ) { Panels.openApps() },
            SettingRow(
                "Quick settings panel", "Hold Start", Palette.Sky, Glyph.Layers,
                "Hold Start anywhere for brightness, volume, Wi-Fi, Bluetooth and battery at a glance. A opens it now.",
                keywords = "brightness volume wifi bluetooth airplane", action = "Open now",
            ) { Panels.toggleQuick() },
            run {
                fun step(d: Int) = HomeStore.updateSettings {
                    it.copy(screensaverMinutes = SCREENSAVER_STEPS[(SCREENSAVER_STEPS.indexOf(s.screensaverMinutes).coerceAtLeast(0) + d + SCREENSAVER_STEPS.size) % SCREENSAVER_STEPS.size])
                }
                SettingRow(
                    "Screensaver", if (s.screensaverMinutes == 0) "Off" else "After ${s.screensaverMinutes} min", Palette.Lavender, Glyph.Clock,
                    "After a while without a button press, your covers drift slowly across a night sky while Pip naps, so nothing burns into the screen. Any button wakes it.",
                    onLeft = { step(-1) }, onRight = { step(1) }, keywords = "idle sleep burn in",
                ) { step(1) }
            },
            SettingRow(
                "Private mode", onOff(s.privateMode), Palette.Lavender, Glyph.Shield, toggle = s.privateMode,
                help = "Hidden games, folders and systems stay hidden and the Show hidden games switch is ignored: only L1 + R1 + Select brings them out, and the combo says nothing while private mode is off.",
                keywords = "privacy hidden secret lock",
            ) {
                val on = !s.privateMode
                HomeStore.updateSettings { it.copy(privateMode = on) }
                Privacy.revealed = false
                io.github.zahakj.tandem.HiddenItems.endPeeks()
                Toasts.show(if (on) "Hidden games now show only with L1 + R1 + Select" else "Private mode off")
            },
            SettingRow(
                "Play stats", "${HomeStore.sessions.size} sessions", Palette.Butter, Glyph.Chart,
                "Your play time by day, system and game, every session you played, and your achievements.",
                opens = true, keywords = "statistics time history sessions",
            ) { Nav.go(Route.Stats) },
            SettingRow(
                "Export play stats", "ROMs/tandem", Palette.Butter, Glyph.Save,
                "Writes your play sessions as a spreadsheet file (CSV) to ROMs/tandem.", keywords = "csv spreadsheet",
            ) { exportStats(ctx) },
        ),
    )
}

/**
 * Settings › Play & stats › Hidden: every hidden folder, system, icon-grid folder and game, each with Unhide. Listed
 * only while hidden games are showing; otherwise one row that says how to show them (and, in private mode, not even
 * whether there are any).
 */
@Composable
fun hiddenSettings(): SettingSection {
    val s = Store.settings
    if (!Privacy.showHidden) {
        val any = s.hiddenFolders.isNotEmpty() || Store.meta.values.any { it.hidden } || IconGridStore.config.folders.any { it.hidden }
        return SettingSection(
            "Hidden",
            listOf(
                SettingRow(
                    "Hidden folders and games", if (Privacy.enabled) "Private" else if (any) "Tucked away" else "None",
                    Palette.Lavender, Glyph.Eye,
                    help = if (Privacy.enabled) "Listed here, each with Unhide, while L1 + R1 + Select has them showing."
                    else "Hide a game, a folder or a whole system with Y. Start › Hidden lists them all, and so does this page while hidden games show.",
                    keywords = "hidden folders systems unhide private start",
                    // With nothing hidden, Show would only switch on Show hidden games for nothing.
                    action = if (Privacy.enabled || !any) null else "Show",
                ) { if (any) toggleShowHidden() else Toasts.show("Nothing is hidden. Hide a game, folder or system with Y") },
            ),
        )
    }
    // The same list Start › Hidden shows, with the same Unhide (HiddenItems.kt).
    val items = HiddenItems.all()
    val rows = ArrayList<SettingRow>()
    // The same row as when they are tucked away (so the highlight stays on it), now putting them away again.
    val anyHidden = items.isNotEmpty() || Store.meta.values.any { it.hidden }
    rows += SettingRow(
        "Hidden folders and games", if (anyHidden) "Showing" else "None", Palette.Lavender, Glyph.Eye,
        help = if (anyHidden) "Hidden games, folders and systems are showing, dimmed, each listed below with Unhide."
        else "Nothing is hidden right now. Hide a game, a folder or a whole system with Y.",
        keywords = "hidden folders systems unhide private start", action = "Tuck away",
    ) { toggleShowHidden() }
    val gameRows = ArrayList<SettingRow>()
    for (item in items) {
        val unhide = { Sounds.toggle(true); Undo.offer(HiddenItems.unhide(item)) { HiddenItems.rehide(item) } }
        when (item) {
            is HiddenItem.Folder -> rows += SettingRow(
                item.name, HiddenItems.kind(item), Palette.Lavender, if (item.isSystem) Glyph.Cartridge else Glyph.Folder,
                help = "Hidden with everything in it, games added later included." +
                    if (item.entry.paths.isNotEmpty()) " " + item.entry.paths.joinToString(", ") { if (it.startsWith("/")) it else "ROMs/$it" } else "",
                keywords = "hidden unhide", action = "Unhide",
            ) { unhide() }
            is HiddenItem.HomeFolderItem -> rows += SettingRow(
                item.folder.name, HiddenItems.kind(item), Palette.Lavender, Glyph.Folder,
                help = "A folder on the icon grid, hidden with the systems and games inside it.", keywords = "hidden unhide", action = "Unhide",
            ) { unhide() }
            is HiddenItem.GameItem -> gameRows += SettingRow(
                item.name, item.game.platform.shortName, Palette.Lavender, Glyph.Gamepad,
                help = "A game hidden by itself.", keywords = "hidden unhide", action = "Unhide",
            ) { unhide() }
        }
    }
    // Folders and systems first; single games fold under More options once there are a few.
    val main = rows + gameRows.take((MAX_HIDDEN_ROWS - rows.size).coerceAtLeast(0))
    return SettingSection("Hidden", main, more = gameRows.drop(main.size - rows.size))
}

private const val MAX_HIDDEN_ROWS = 6
