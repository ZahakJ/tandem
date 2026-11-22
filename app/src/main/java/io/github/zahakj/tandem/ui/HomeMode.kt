package io.github.zahakj.tandem.ui

import io.github.zahakj.tandem.R
import io.github.zahakj.tandem.Tx
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
import io.github.zahakj.tandem.LaunchDisplay
import io.github.zahakj.tandem.Libraries
import io.github.zahakj.tandem.data.AppPrefs
import io.github.zahakj.tandem.data.AppScreen
import io.github.zahakj.tandem.MainActivity
import io.github.zahakj.tandem.Privacy
import io.github.zahakj.tandem.RunningGames
import io.github.zahakj.tandem.Selected
import io.github.zahakj.tandem.Selection
import io.github.zahakj.tandem.Immersion
import io.github.zahakj.tandem.Shell
import io.github.zahakj.tandem.data.HomeStore
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
        Toasts.show(Tx.s(if (pinned) R.string.home_dock_removed else R.string.home_dock_added))
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
        if (intent == null) { Toasts.show(Tx.s(R.string.home_app_wont_open)); return }
        val chosen = bottom != null || AppPrefs.screen(pkg) != AppScreen.AUTO
        val down = (bottom ?: LaunchDisplay.appWantsBottom(pkg)) && LaunchDisplay.dualScreen(ctx)
        // On the top screen, the bottom one shows this app (never Android's own app list) while it is in front
        // (SecondaryHome.kt); on the bottom one, Tandem's own bottom screen makes way for it.
        if (down) MainActivity.live?.get()?.hideBottomScreen() else io.github.zahakj.tandem.SecondaryHome.beforeLeave(ctx, pkg)
        if (runCatching { ctx.startActivity(intent, LaunchDisplay.appOptions(ctx, down)) }.isFailure) { Toasts.show(Tx.s(R.string.home_app_wont_open)); return }
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
                        Text(Tx.s(R.string.home_dock_all_apps), style = MaterialTheme.typography.labelLarge)
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
        TextEntry.ask(Tx.s(R.string.home_apps_search), query, onChange = { query = it.trim() }) { query = it.trim() }
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
                Text(Tx.s(R.string.home_apps_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f), maxLines = 1)
                ButtonHint("A", Tx.s(R.string.home_apps_hint_open), Palette.Mint)
                Spacer(Modifier.width(12.dp))
                ButtonHint("Y", Tx.s(R.string.home_apps_hint_options), Palette.Butter)
                Spacer(Modifier.width(12.dp))
                ButtonHint("X", Tx.s(R.string.home_apps_hint_search), Palette.Sky)
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
                            query.ifEmpty { Tx.s(R.string.home_apps_search) }, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
                            color = if (query.isEmpty()) Palette.Muted else Palette.Text, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (hiddenCount > 0 || showHidden) FocusCard(
                    Modifier.height(44.dp).remembersFocus("apps", "hidden", false), glow = Palette.Lavender, shape = RoundedCornerShape(14.dp),
                    onFocus = { Panels.focusedApp = null }, onClick = { AppsDrawerState.showHidden = !showHidden },
                ) {
                    Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(Tx.s(R.string.home_apps_show_hidden), style = MaterialTheme.typography.titleSmall, maxLines = 1)
                        Spacer(Modifier.width(8.dp))
                        Pill(if (showHidden) Tx.s(R.string.common_on) else "$hiddenCount", Palette.Lavender)
                    }
                }
            }
            val list = shown
            if (list == null) {
                PipNote(Tx.s(R.string.home_apps_gathering), Tx.s(R.string.home_apps_gathering_detail))
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
                            Text(Tx.s(R.string.home_apps_on_dock), style = MaterialTheme.typography.labelLarge, color = Palette.Muted, maxLines = 1)
                        }
                        items(pinned, key = { "dock:$it" }) { p ->
                            AppTile(p, RunningGames.appLabel(ctx, p), Modifier.remembersFocus("apps", "dock:$p", "dock:$p" == first), faded = prefs.of(p).hidden) {
                                Panels.close(); Dock.open(ctx, p)
                            }
                        }
                        item("all-title", span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                            Text(Tx.s(R.string.home_apps_all), style = MaterialTheme.typography.labelLarge, color = Palette.Muted, maxLines = 1)
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
                                    if (query.isEmpty()) Tx.s(R.string.home_apps_none) else Tx.s(R.string.home_apps_no_match, query),
                                    Tx.s(if (hiddenHits) R.string.home_apps_hidden_match else R.string.home_apps_try_other), mood = 2,
                                )
                                PanelRow(
                                    Tx.s(if (hiddenHits || query.isEmpty()) R.string.home_apps_show_hidden else R.string.home_apps_clear_search),
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
                            PanelRow(Tx.s(R.string.home_apps_stock_home), Tx.s(R.string.home_apps_stock_home_value), "apps", "stockhome", false, Palette.Lavender) {
                                Panels.close()
                                if (!io.github.zahakj.tandem.SecondaryHome.openStockHome(ctx)) Toasts.show(Tx.s(R.string.home_apps_stock_home_failed))
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
        if (HomeMode.isDefault(ctx)) { Toasts.show(Tx.s(R.string.home_explainer_done)); Panels.close() }
    }
    OverlayPanel(Panels.homeExplainer, onClose = { Panels.close() }) {
        val isHome = remember(tick) { HomeMode.isDefault(ctx) }
        val key = "homeexplainer"
        PanelSurface(Modifier.width(560.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Pip(64.dp, mood = 1)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(Tx.s(R.string.home_explainer_title), style = MaterialTheme.typography.headlineSmall)
                    Text(
                        Tx.s(R.string.home_explainer_body),
                        style = MaterialTheme.typography.bodyMedium, color = Palette.Muted,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                if (isHome) {
                    PanelRow(Tx.s(R.string.home_explainer_is_home), Tx.s(R.string.home_explainer_on), key, "status", false, Palette.Mint) { }
                    PanelRow(Tx.s(R.string.home_explainer_other), null, key, "other", true, Palette.Lavender) { Panels.close(); HomeMode.openHomeSettings(ctx) }
                } else {
                    PanelRow(Tx.s(R.string.home_explainer_make), null, key, "choose", true, Palette.Mint) {
                        val roles = ctx.getSystemService(RoleManager::class.java)
                        val intent = roles?.takeIf { it.isRoleAvailable(RoleManager.ROLE_HOME) }?.createRequestRoleIntent(RoleManager.ROLE_HOME)
                        if (intent == null || runCatching { ask.launch(intent) }.isFailure) HomeMode.openHomeSettings(ctx)
                    }
                    PanelRow(Tx.s(R.string.home_explainer_settings), null, key, "settings", false, Palette.Sky) { HomeMode.openHomeSettings(ctx) }
                }
                PanelRow(Tx.s(R.string.home_explainer_not_now), null, key, "close", false, Palette.Lavender) { Panels.close() }
            }
        }
    }
}

/** The "show hidden games" toggles. In private mode they ask for the PIN first, as L1 + R1 + Select does. */
fun toggleShowHidden() {
    if (Privacy.enabled) {
        if (Privacy.revealed) Privacy.tuckAway() else io.github.zahakj.tandem.PrivateMode.reveal()
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
    val rootShizuku = remember(resumes, ShizukuWatch.version) { Shell.root() }
    val quietAllowed = remember(resumes, ShizukuWatch.version, Immersion.changed) { Immersion.granted(ctx) }
    return SettingSection(Tx.s(R.string.home_play_title), en = "Home & play",
        rows = listOf(
            SettingRow(
                Tx.s(R.string.home_play_use_home), onOff(isHome), Palette.Mint, Glyph.Home,
                Tx.s(R.string.home_play_use_home_help),
                opens = true, keywords = "launcher default home button frontend", en = "Use Tandem as the Home screen",
            ) { Panels.openHomeExplainer() },
            SettingRow(
                Tx.s(R.string.home_play_close_previous),
                if (!shizuku) Tx.s(R.string.home_play_needs_shizuku) else onOff(RunningGames.closePreviousOn()), Palette.Mint, Glyph.Gamepad,
                Tx.s(R.string.home_play_close_previous_help),
                toggle = if (!shizuku) null else RunningGames.closePreviousOn(), keywords = "close the previous game when starting another kill emulator memory",
                en = "Close previous game",
            ) {
                if (!shizuku) Toasts.show(Tx.s(R.string.home_play_needs_shizuku_toast))
                else HomeStore.updateSettings { it.copy(closePrevious = !RunningGames.closePreviousOn()) }
            },
            SettingRow(
                Tx.s(R.string.home_play_ask_closing),
                if (!shizuku) Tx.s(R.string.home_play_needs_shizuku) else onOff(s.askBeforeClosing), Palette.Mint, Glyph.Shield,
                Tx.s(R.string.home_play_ask_closing_help),
                toggle = if (!shizuku) null else s.askBeforeClosing,
                keywords = "ask before closing a running game confirm warn kill emulator unsaved progress lose save resume prompt don't ask again",
                en = "Ask before closing a game",
            ) {
                if (!shizuku) Toasts.show(Tx.s(R.string.home_play_needs_shizuku_toast))
                else HomeStore.updateSettings { it.copy(askBeforeClosing = !it.askBeforeClosing) }
            },
            SettingRow(
                Tx.s(R.string.home_play_immersion), if (!quietAllowed) Tx.s(R.string.home_play_immersion_needs) else onOff(s.immersion), Palette.Mint, Glyph.Bell,
                Tx.s(R.string.home_play_immersion_help),
                toggle = if (!quietAllowed) null else s.immersion, action = if (!quietAllowed) Tx.s(R.string.home_play_immersion_allow) else null, en = "Immersion mode",
                keywords = "immersion do not disturb dnd quiet silence notifications heads up pop up sound vibration focus game mode",
            ) {
                if (quietAllowed) HomeStore.updateSettings { it.copy(immersion = !it.immersion) }
                else Thread({
                    val ok = Immersion.allow(ctx)
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        if (ok) Toasts.show(Tx.s(R.string.home_play_immersion_on))
                        else { Toasts.show(Tx.s(R.string.home_play_immersion_come_back)); runCatching { ctx.startActivity(Immersion.settingsIntent()) } }
                    }
                }, "tandem-immersion-allow").start()
            },
            SettingRow(
                Tx.s(R.string.home_play_dock), Dock.pinned(ctx).size.let { Tx.p(R.plurals.home_play_dock_count, it) }, Palette.Sky, Glyph.Grid,
                Tx.s(R.string.home_play_dock_help), opens = true, keywords = "pin apps favourite", en = "Choose dock apps",
            ) { Panels.openApps() },
            choice(
                Tx.s(R.string.home_play_screensaver), Glyph.Clock, Palette.Lavender,
                Tx.s(R.string.home_play_screensaver_help),
                SCREENSAVER_STEPS.map { it.toString() to if (it == 0) Tx.s(R.string.common_off) else Tx.p(R.plurals.home_play_screensaver_after, it) },
                s.screensaverMinutes.takeIf { it in SCREENSAVER_STEPS }?.toString() ?: "0",
                en = "Screensaver",
            ) { v -> HomeStore.updateSettings { it.copy(screensaverMinutes = v.toInt()) } }.copy(keywords = "idle sleep burn in"),
            SettingRow(
                Tx.s(R.string.home_play_freeze), if (!rootShizuku) Tx.s(R.string.home_play_needs_root) else onOff(s.freezeAsleep), Palette.Lavender, Glyph.Clock,
                toggle = if (!rootShizuku) null else s.freezeAsleep,
                help = Tx.s(R.string.home_play_freeze_help),
                keywords = "battery drain standby suspend freeze emulator", en = "Pause games in sleep",
            ) {
                if (!rootShizuku) Toasts.show(Tx.s(R.string.home_play_needs_root_toast))
                else HomeStore.updateSettings { it.copy(freezeAsleep = !it.freezeAsleep) }
            },
            SettingRow(
                Tx.s(R.string.home_play_private), onOff(s.privateMode), Palette.Lavender, Glyph.Shield, toggle = s.privateMode,
                help = Tx.s(R.string.home_play_private_help, Prompts.words(io.github.zahakj.tandem.Command.Hidden)),
                keywords = "privacy hidden secret lock pin panic offline", en = "Private mode",
            ) { if (s.privateMode) io.github.zahakj.tandem.PrivateMode.turnOff() else io.github.zahakj.tandem.PrivateMode.turnOn() },
            run {
                // Going offline, and staying offline while the game is still open behind Tandem, as one choice.
                // Until it closes comes first: it was what going offline did unless you changed it.
                val options = listOf("off" to Tx.s(R.string.common_off), "closes" to Tx.s(R.string.home_play_offline_closes), "plays" to Tx.s(R.string.home_play_offline_plays))
                val now = if (!s.offlineHidden) "off" else if (s.offlineUntilClosed) "closes" else "plays"
                fun set(v: String) = HomeStore.updateSettings { it.copy(offlineHidden = v != "off", offlineUntilClosed = if (v == "off") it.offlineUntilClosed else v == "closes") }
                val help = Tx.s(if (!s.privateMode) R.string.home_play_offline_help_off else R.string.home_play_offline_help)
                val keywords = "airplane mode offline network wifi internet hidden private game emulator background sync close go offline stay offline"
                if (!shizuku) SettingRow(Tx.s(R.string.home_play_offline), Tx.s(R.string.home_play_needs_shizuku), Palette.Lavender, Glyph.Shield, help, keywords = keywords, en = "Offline for hidden games") {
                    Toasts.show(Tx.s(R.string.home_play_needs_shizuku_toast))
                } else choice(Tx.s(R.string.home_play_offline), Glyph.Shield, Palette.Lavender, help, options, now, en = "Offline for hidden games") { v -> set(v) }.copy(keywords = keywords)
            },
            SettingRow(
                Tx.s(R.string.home_play_pin), Tx.s(if (io.github.zahakj.tandem.PinVault.isSet) R.string.home_play_pin_set else R.string.home_play_pin_not_set), Palette.Lavender, Glyph.Shield,
                help = Tx.s(R.string.home_play_pin_help),
                keywords = "pin code password change forgot private", action = Tx.s(R.string.home_play_pin_change), en = "Private mode PIN",
            ) { io.github.zahakj.tandem.PrivateMode.changePin() },
            SettingRow(
                Tx.s(R.string.home_play_export), "ROMs/tandem", Palette.Butter, Glyph.Save, // tx-ok: a folder's name
                Tx.s(R.string.home_play_export_help), keywords = "csv spreadsheet", en = "Export play stats",
            ) { exportStats(ctx) },
        ),
    )
}

