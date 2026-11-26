package io.github.zahakj.tandem.ui

import io.github.zahakj.tandem.Collections
import io.github.zahakj.tandem.R
import io.github.zahakj.tandem.Tx

import android.os.BatteryManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import io.github.zahakj.tandem.Button
import io.github.zahakj.tandem.HiddenItem
import io.github.zahakj.tandem.HiddenItems
import io.github.zahakj.tandem.Libraries
import io.github.zahakj.tandem.Privacy
import io.github.zahakj.tandem.RunningGames
import io.github.zahakj.tandem.Selected
import io.github.zahakj.tandem.Selection
import io.github.zahakj.tandem.Screensaver
import io.github.zahakj.tandem.data.IconGridStore
import io.github.zahakj.tandem.data.SortOrder
import io.github.zahakj.tandem.data.Store
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Date

/*
 * Start: a panel that folds open from the left, instead of the whole of Settings. Pip sits big at
 * the top (blinking, glancing about); the rows unfold one after another like a folded strip of paper; New and Sort
 * open their own page inside the panel. Start is for going places and acting on the home screen, short enough to
 * fit without scrolling; device switches (brightness, radios, Battery saver, the screensaver) are the quick
 * settings' (QuickPanel.kt), and Settings opens Settings. B goes back a page or folds it all away again (rows fold up
 * in reverse, then the panel slides out). The screen behind blurs and dims. D-pad only, focus stays inside. Works in
 * every home layout and on every screen outside Settings (where Start still goes back out).
 */

object StartMenu {
    var open by mutableStateOf(false)
        private set
    /** The page inside the panel: null for the main one, "new", "sort" or "hidden". */
    var page by mutableStateOf<String?>(null)

    /** The highlighted row on the Hidden page, for Y and the hints. */
    var hiddenFocus by mutableStateOf<String?>(null)
    /** What the details screen showed before the Hidden page took it over, to give back when the page goes. */
    private var selectionBefore: Selected? = null

    fun openHidden() {
        FocusMemory.forget("$ROUTE:hidden")
        selectionBefore = Selection.current
        hiddenFocus = null
        page = "hidden"
    }

    fun leftHidden() {
        selectionBefore?.let { Selection.current = it }
        selectionBefore = null
    }

    fun show() {
        if (open) { close(); return }
        FocusMemory.forget(ROUTE)
        page = null
        open = true
        Sounds.select()
    }

    fun close() {
        if (!open) return
        open = false
        Nav.focusTick++
    }

    /** Closes the panel and then does [then] (opening another popup or screen). */
    fun go(then: () -> Unit) { open = false; then() }

    const val ROUTE = "start"
}

private val PanelWidth = 276.dp

/** Room around the panel's rows for a focused row's halo (4 dp) and growth (2%). */
private val RowRoom = 12.dp

/** A row's height: roomy when there is space, never under the compact one (StartPage). */
private val RowMax = 40.dp
private val RowMin = 30.dp
private val RowGap = 1.dp
/** The hairline between groups, with its space above and below. */
private val HairlineHeight = 7.dp

@Composable
fun StartPanelHost() {
    val ctx = LocalContext.current
    val open = StartMenu.open
    var visible by remember { mutableStateOf(false) }
    val slide = remember { Animatable(0f) }
    val unfold = remember { Animatable(0f) }
    val still = !Flair.entering
    LaunchedEffect(open) {
        if (open) {
            visible = true
            launch { slide.animateTo(1f, if (still) tween(0) else spring(0.8f, 420f)) }
            launch { unfold.animateTo(1f, if (still) tween(0) else tween(620, easing = LinearEasing)) }
        } else if (visible) {
            // Fold the rows away (last first), then slide the panel out.
            launch { unfold.animateTo(0f, if (still) tween(0) else tween(230, easing = LinearEasing)) }
            // No pause before it moves: the panel starts leaving on the next frame, folding as it goes.
            slide.animateTo(0f, if (still) tween(0) else tween(200, easing = FastOutLinearInEasing))
            visible = false
        }
    }
    // The screen behind blurs with the panel (menuBackdrop reads this).
    LaunchedEffect(Unit) { snapshotFlow { slide.value }.collect { ActionMenu.panelDepth.floatValue = it } }
    // Never leave blur behind if the panel goes away mid-animation (a launch, Home, the activity stopping).
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { ActionMenu.panelDepth.floatValue = 0f } }
    PanelGuard(open)
    InterceptButtons(open) { b ->
        when {
            b == Button.Start -> StartMenu.close()
            b == Button.Y && StartMenu.page == "hidden" -> peekFocused()
        }
        true
    }
    // The Hidden page goes the moment it may no longer be offered (tucked away, the private-mode combo, nothing left).
    val hiddenOffered = HiddenItems.offered()
    LaunchedEffect(open, StartMenu.page, hiddenOffered) {
        if (StartMenu.page == "hidden" && !hiddenOffered) StartMenu.page = null
        if (!open || StartMenu.page != "hidden") StartMenu.leftHidden()
    }
    BackHandler(open) { if (StartMenu.page != null) StartMenu.page = null else StartMenu.close() }
    // Composed in the same frame as the press (not a frame later, after the effect above runs).
    if (!visible && !open) return
    val layer = rememberLayer(open)
    val sign = startSign
    ProvideLayer(layer) {
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier.fillMaxSize().graphicsLayer { alpha = slide.value.coerceIn(0f, 1f) }
                    .background(Palette.NightDeep.copy(alpha = 0.42f)).swallowTaps { StartMenu.close() },
            )
            Column(
                Modifier.fillMaxHeight().width(PanelWidth)
                    .graphicsLayer {
                        // From the start side: the left, or the right in right to left.
                        translationX = -(1f - slide.value) * (size.width + 24f * density) * sign
                        shadowElevation = 24f * density * slide.value.coerceIn(0f, 1f)
                        shape = RoundedCornerShape(topEnd = 30.dp, bottomEnd = 30.dp)
                        clip = true
                    }
                    .background(Palette.Card.copy(alpha = 0.97f))
                    .onFocusChanged { Layers.focusIn(layer, it.hasFocus) }
                    .onPreviewKeyEvent { !StartMenu.open && it.isNavKey() }
                    .trapFocus()
                    .swallowTaps()
                    .padding(start = 4.dp, end = 4.dp, top = 14.dp, bottom = 10.dp),
            ) {
                StartHeader(unfold::value)
                Spacer(Modifier.height(4.dp))
                // Every page of the panel stays composed while it is open: the main page at once, the others (New,
                // Sort, Hidden) a few frames later, so opening one is only a slide in their layers, never a page
                // composed in the frame A is pressed. Hidden pages are measured but not placed (no focus, no drawing).
                // One page per frame.
                var warmPages by remember { androidx.compose.runtime.mutableIntStateOf(0) }
                LaunchedEffect(Unit) {
                    delay(180)
                    repeat(StartPages.size - 1) { androidx.compose.runtime.withFrameNanos { }; warmPages++ }
                }
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    StartPages.forEachIndexed { i, page ->
                        val shown = StartMenu.page == page
                        if (shown || i <= warmPages) androidx.compose.runtime.key(page) { StartPage(ctx, page, shown, still, unfold) }
                    }
                }
                StartHints(unfold::value)
            }
        }
    }
}

private val StartPages = listOf(null, "new", "sort", "hidden")

/**
 * One page of the panel. Shown, it slides in from the side it was opened towards and its rows unfold; hidden, it
 * slides away and stops being placed once the slide ends.
 */
@Composable
private fun StartPage(
    ctx: android.content.Context, page: String?, shown: Boolean, still: Boolean,
    unfold: Animatable<Float, androidx.compose.animation.core.AnimationVector1D>,
) {
    val rows = startRows(ctx, page)
    val routeKey = StartMenu.ROUTE + ":" + (page ?: "main")
    val ids = rows.map { it.id }
    val tick = Nav.focusTick
    // Worked out again each time the page shows (opening a page forgets its last row, so it starts on the first).
    val target = remember(routeKey, ids, tick, shown) { FocusMemory.resolve(routeKey, ids) }
    // 1 shown, 0 hidden; sub-pages come from the end side, the main page from the start side.
    val enter = remember { Animatable(if (shown) 1f else 0f) }
    var drawn by remember { mutableStateOf(shown) }
    LaunchedEffect(shown) {
        if (shown) { drawn = true; enter.animateTo(1f, if (still) tween(0) else spring(0.85f, 500f)) }
        else { enter.animateTo(0f, if (still) tween(0) else tween(170)); drawn = false }
    }
    val side = (if (page == null) -1f else 1f) * startSign
    // A page opened inside the panel unfolds its own rows.
    val pageUnfold = remember { Animatable(if (page == null || still) 1f else 0f) }
    LaunchedEffect(shown) {
        if (page == null) return@LaunchedEffect
        if (shown) { pageUnfold.snapTo(if (still) 1f else 0f); pageUnfold.animateTo(1f, tween(if (still) 0 else 420, easing = LinearEasing)) }
    }
    val scroll = rememberScrollState()
    androidx.compose.ui.layout.Layout(
        content = { androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
            // The rows share the height there is, from roomy down to compact, so the main page fits whole even with
            // Switch games, Undo and an update showing; only a page that can't fit at the compact height (a long
            // Hidden list) keeps roomy rows and scrolls.
            val lines = rows.drop(1).count { it.id in StartGroupStarts }
            val room = maxHeight - RowRoom * 2 - HairlineHeight * lines - RowGap * (rows.size - 1).coerceAtLeast(0)
            val fit = room / rows.size.coerceAtLeast(1)
            val rowHeight = if (fit < RowMin) RowMax else fit.coerceAtMost(RowMax)
            // Room inside the faded, clipped list for the focused row's ring, halo and growth on every side
            // (about 7 dp), so the first, last and every row's ends show whole (docs/UI-RULES.md).
            Column(
                Modifier.fillMaxSize()
                    .graphicsLayer {
                        val v = enter.value
                        alpha = (if (shown) v / 0.4f else v).coerceIn(0f, 1f)
                        translationX = (1f - v) * size.width * (if (shown) 0.5f else 0.33f) * side
                    }
                    .fadingEdges(scroll).verticalScroll(scroll).padding(horizontal = RowRoom, vertical = RowRoom),
                verticalArrangement = Arrangement.spacedBy(RowGap),
            ) {
                val progress = { if (page == null) unfold.value else pageUnfold.value * unfold.value.coerceAtLeast(0f) }
                rows.forEachIndexed { i, a ->
                    // A hairline between the main page's groups (go places · the home screen · Settings).
                    if (i > 0 && a.id in StartGroupStarts) Box(
                        Modifier.fillMaxWidth().height(HairlineHeight).padding(horizontal = 16.dp, vertical = 3.dp)
                            .graphicsLayer { alpha = ((progress() - i * 0.055f) * 2f).coerceIn(0f, 1f) }
                            .background(Palette.Text.copy(alpha = 0.09f)),
                    )
                    StartRow(a, routeKey, shown && a.id == target, rowHeight, Modifier.unfoldRow(i, rows.size, progress))
                }
            }
        } },
        modifier = Modifier.fillMaxSize(),
    ) { measurables, c ->
        val placeables = measurables.map { it.measure(c) }
        layout(c.maxWidth, c.maxHeight) { if (drawn || shown) placeables.forEach { it.place(0, 0) } }
    }
}

/** Big Pip and the time, popping in first. */
@Composable
private fun StartHeader(progress: () -> Float) {
    val ctx = LocalContext.current
    val now by rememberMinute()
    val battery by produceState(-1) {
        value = ctx.getSystemService(BatteryManager::class.java).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }
    Row(
        Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp).graphicsLayer {
            val p = (progress() / 0.3f).coerceIn(0f, 1f)
            val s = 0.6f + 0.4f * backOut(p)
            scaleX = s; scaleY = s
            alpha = p
            transformOrigin = TransformOrigin(0.15f, 0.5f)
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Tandem's logo, big: Pip blinks and glances the way you move.
        Pip(56.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(Tx.s(R.string.app_name), style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val line = listOfNotNull(Clock.format(now), if (battery >= 0) Tx.s(R.string.common_percent, battery) else null).joinParts()
            if (line.isNotEmpty()) Text(line, style = MaterialTheme.typography.labelLarge, color = Palette.Muted, maxLines = 1)
        }
    }
}

@Composable
private fun StartHints(progress: () -> Float) {
    Row(
        Modifier.fillMaxWidth().padding(start = 10.dp, top = 6.dp).graphicsLayer { alpha = ((progress() - 0.6f) / 0.4f).coerceIn(0f, 1f) },
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        val onItem = StartMenu.page == "hidden" && StartMenu.hiddenFocus.let { it != null && it != "back" && it != "toggle" }
        ButtonHint("A", Tx.s(if (onItem) R.string.panels_start_hint_unhide else R.string.common_choose), Palette.Mint)
        // Y shows one thing on its own, which means nothing while everything hidden is showing.
        if (onItem && !io.github.zahakj.tandem.Privacy.showHidden) {
            val peeking = HiddenItems.all().firstOrNull { it.id == StartMenu.hiddenFocus }?.let { HiddenItems.peeking(it) } == true
            ButtonHint("Y", Tx.s(if (peeking) R.string.panels_start_hint_hide_again else R.string.panels_start_hint_show_here), Palette.Sky)
        }
        ButtonHint("B", Tx.s(if (StartMenu.page != null) R.string.common_back else R.string.common_close), Palette.Lavender)
    }
}

private fun backOut(x: Float): Float {
    val c1 = 1.70158f
    val c3 = c1 + 1f
    val t = x - 1f
    return 1f + c3 * t * t * t + c1 * t * t
}

/**
 * Unfolds row [i] of [n] as [progress] runs 0 → 1: each in its own slice of the time, so they open one after
 * another, hinged at the top like a strip of paper, with a touch of overshoot. Running backwards folds them up last
 * first. Drawn in a layer only.
 */
private fun Modifier.unfoldRow(i: Int, n: Int, progress: () -> Float): Modifier = graphicsLayer {
    val stagger = 0.055f
    val window = (1f - stagger * (n - 1).coerceAtLeast(0)).coerceAtLeast(0.3f)
    val local = ((progress() - i * stagger) / window).coerceIn(0f, 1f)
    val e = backOut(local)
    rotationX = -78f * (1f - e)
    cameraDistance = 14f * density
    transformOrigin = TransformOrigin(0.5f, 0f)
    alpha = (local * 2.2f).coerceIn(0f, 1f)
    translationY = -6f * density * (1f - e)
}

/** The rows of a page of the panel. */
private fun startRows(ctx: android.content.Context, page: String?): List<MenuAction> {
    // "Edit grid" (and the grid's New and Sort rows) whenever the icon grid is the home layout or on screen.
    val grid = IconGrid.isHome
    val back = MenuAction("back", Tx.s(R.string.common_back), Glyph.Back, Palette.Lavender) { StartMenu.page = null }
    val widget = MenuAction("widget", Tx.s(R.string.panels_start_new_widget), Glyph.Layers, Palette.Sky) { StartMenu.go { Nav.go(Route.Widgets) } }
    return when (page) {
        "hidden" -> hiddenRows()
        "new" -> listOf(back) + if (grid) listOf(
            MenuAction("folder", Tx.s(R.string.panels_start_new_folder), Glyph.Folder, Palette.Butter) {
                StartMenu.go {
                    TextEntry.ask(Tx.s(R.string.panels_start_name_folder), Tx.s(R.string.panels_start_folder_default)) { name ->
                        val n = name.trim().ifEmpty { Tx.s(R.string.panels_start_folder_unnamed) }
                        IconGridStore.createFolder(n, null, homeShown(), IconGridStore.config.folders.size)
                        Toasts.show(Tx.s(R.string.panels_start_folder_made, n))
                    }
                }
            },
            MenuAction("smart", Tx.s(R.string.panels_start_new_smart_folder), Glyph.Sparkle, Palette.Lavender) { StartMenu.go { SmartFolderForm.openNew() } },
            MenuAction("add", Tx.s(R.string.panels_start_new_add_home), Glyph.Plus, Palette.Mint) {
                StartMenu.go { FocusMemory.forget(IconGrid.ADD_ROUTE); if (Nav.route != Route.Home) Nav.replace(Route.Home); IconGrid.addAt = null; IconGrid.addFilter = null; IconGrid.adding = true }
            },
            widget,
        ) else listOf(widget)
        "sort" -> listOf(back) + (if (grid) listOf(
            // Packs every tile to the front, closing the gaps (the grid otherwise keeps them).
            MenuAction("tidy", Tx.s(R.string.panels_start_sort_tidy), Glyph.Sparkle, Palette.Mint) { StartMenu.go { tidyHome() } },
            MenuAction("home-name", Tx.s(R.string.panels_start_sort_home_name), Glyph.Sort, Palette.Sky) { sortHome("name") },
            MenuAction("home-recent", Tx.s(R.string.panels_start_sort_home_recent), Glyph.Clock, Palette.Sky) { sortHome("recent") },
            MenuAction("home-most", Tx.s(R.string.panels_start_sort_home_most), Glyph.Chart, Palette.Sky) { sortHome("most") },
            MenuAction("home-kind", Tx.s(R.string.panels_start_sort_home_kind), Glyph.Cartridge, Palette.Sky) { sortHome("kind") },
        ) else emptyList()) + SortOrder.entries.map { o ->
            MenuAction("games-${o.name}", Tx.s(R.string.panels_start_sort_games, o.label), Glyph.Sort, Palette.Mint, value = if (Store.settings.sort == o) "✓" else null) {
                Store.updateSettings { it.copy(sort = o) }
                Toasts.show(Tx.s(R.string.panels_start_sorted_games, o.label))
            }
        }
        // Three groups, each thing in one place: going places, the home screen, then Settings. A row that only
        // sometimes means something (Switch games, Undo, Hidden, an update) shows only then.
        else -> buildList {
            if (RunningGames.list.isNotEmpty()) add(
                MenuAction("switch", Tx.s(R.string.panels_start_switch_games), Glyph.Swap, Palette.Mint, value = "${RunningGames.list.size}") { StartMenu.go { Panels.openSwitcher() } },
            )
            add(MenuAction("apps", Tx.s(R.string.panels_start_all_apps), null, Palette.Sky, icon = TIcon.AllApps) { StartMenu.go { Panels.openApps() } })
            add(MenuAction("search", Tx.s(R.string.panels_start_search), Glyph.Search, Palette.Sky) { StartMenu.go { Nav.search = true } })
            // Play time, your games by status and your trophies, always one press from Start.
            add(MenuAction("stats", Tx.s(R.string.panels_start_stats), null, Palette.Butter, icon = TIcon.Trophy, sub = startStatsLine()) {
                StartMenu.go { StatsTabs.tab = StatsTabs.OVERVIEW; if (Nav.route != Route.Stats) Nav.go(Route.Stats) }
            })
            add(MenuAction("new", Tx.s(R.string.panels_start_new), Glyph.Plus, Palette.Mint, value = "▸") { FocusMemory.forget(StartMenu.ROUTE + ":new"); StartMenu.page = "new" })
            add(MenuAction("sort", Tx.s(R.string.panels_start_sort), Glyph.Sort, Palette.Sky, value = "▸") { FocusMemory.forget(StartMenu.ROUTE + ":sort"); StartMenu.page = "sort" })
            // Try-on mode for every home layout (or, among a system's or collection's games, every games view): the panel
            // closes and L1 / R1 put each on live (LayoutSwitch.kt). The one in use is named beside it.
            val look = Store.settings.look
            val r = Nav.route
            if (r is Route.System || r is Route.Collection) add(
                MenuAction("gameview", Tx.s(R.string.look_games_view), Glyph.Grid, Palette.Butter, value = GameViews.firstOrNull { it.first == look.gameView }?.second) {
                    StartMenu.go { LayoutSwitch.show(LayoutSwitch.Kind.Games) }
                },
            ) else add(
                MenuAction("layout", Tx.s(R.string.panels_start_layout), Glyph.Home, Palette.Butter, value = HomeLayouts.firstOrNull { it.first == look.homeLayout }?.second?.substringBefore(" (")) {
                    StartMenu.go { LayoutSwitch.show(LayoutSwitch.Kind.Home) }
                },
            )
            if (grid) {
                add(MenuAction("edit", Tx.s(R.string.panels_start_edit_grid), Glyph.Move, Palette.Butter) { StartMenu.go { editGrid() } })
                // Only for a while after a change on the grid: an undo kept from days ago (they are saved) would
                // only be a puzzle here. Older ones are still in Y › More › Undo last change on the grid.
                IconGridStore.undoLabel?.takeIf { IconGridStore.undoAt > 0 && System.currentTimeMillis() - IconGridStore.undoAt < UNDO_FRESH_MS }?.let { label ->
                    add(MenuAction("undo", Tx.s(R.string.panels_start_undo), Glyph.Refresh, Palette.Butter, value = label) { StartMenu.go { GridUndoUi.undo() } })
                }
            } else add(MenuAction("edit", Tx.s(R.string.panels_start_edit_widgets), Glyph.Move, Palette.Butter) { StartMenu.go { Nav.go(Route.Widgets) } })
            // Only when something is hidden, and in private mode only while revealed (HiddenAccess.startRow).
            if (HiddenItems.offered()) add(
                MenuAction("hidden", Tx.s(R.string.panels_start_hidden), Glyph.Eye, Palette.Lavender, value = "${HiddenItems.count()}") { StartMenu.openHidden() },
            )
            // Straight to Settings' home: its tiles and search already lead to every page.
            add(MenuAction("settings", Tx.s(R.string.panels_start_settings), null, Palette.Accent, icon = TIcon.Gear) { StartMenu.go { Nav.go(Route.Settings) } })
            // A new Tandem is out (Updates.kt): one quiet row, A shows its notes and Update.
            io.github.zahakj.tandem.Updates.found?.takeIf { io.github.zahakj.tandem.Updates.enabled }?.let { rel ->
                add(MenuAction("update", Tx.s(R.string.panels_start_update, rel.version), Glyph.Download, Palette.Mint) { StartMenu.go { UpdatePopup.show() } })
            }
        }
    }
}

/** How long after a change on the grid Start offers to undo it. */
private const val UNDO_FRESH_MS = 15 * 60_000L

/** The rows that open a group on the main page, with a hairline above them. */
private val StartGroupStarts = setOf("new", "settings")

/** Edit grid: the highlighted tile (or the first) starts moving, like Y › Move. */
private fun editGrid() {
    if (Nav.route != Route.Home) { Nav.replace(Route.Home); Toasts.show(Tx.s(R.string.panels_start_edit_grid_pick)); return }
    val (_, arranged) = IconGrid.menuData ?: return
    val key = FocusMemory.get(Route.Home.key)?.takeIf { it in arranged } ?: arranged.firstOrNull() ?: return
    startMove(key, null, homeShown().slots)
}

/** Sort › Home: puts the home grid in a new order (Y › Move still works after). */
internal fun sortHome(by: String) {
    val (catalog, arranged) = IconGrid.menuData ?: return
    val items = arranged.mapNotNull { catalog.item(it) }
    val sorted = when (by) {
        "name" -> items.sortedBy { it.title.lowercase() }
        "recent" -> items.sortedByDescending { it.games.maxOfOrNull { g -> g.meta.lastPlayed } ?: 0L }
        "most" -> items.sortedByDescending { it.games.sumOf { g -> g.meta.playSeconds } }
        else -> items.sortedBy { it.kind.ordinal }
    }
    val said = when (by) {
        "name" -> R.string.panels_start_sorted_home_name
        "recent" -> R.string.panels_start_sorted_home_recent
        "most" -> R.string.panels_start_sorted_home_most
        else -> R.string.panels_start_sorted_home_kind
    }
    // The label is kept with the undo (saved), so it stays in the language it was made in.
    GridUndoUi.run(Tx.s(said)) { IconGridStore.setOrder(sorted.map { it.key }) }
}

/**
 * Start › Hidden: everything hidden, each with A to unhide it and Y to show just it until hidden games are next
 * tucked away, under a row that shows or tucks away all of them. Empty (so never drawn) whenever it may not be
 * offered: names of hidden things never reach the panel while private mode keeps them tucked away.
 */
private fun hiddenRows(): List<MenuAction> {
    if (!HiddenItems.offered()) return emptyList()
    val items = HiddenItems.all()
    val showing = Privacy.showHidden
    val peeks = HiddenItems.peeks
    val back = MenuAction("back", Tx.s(R.string.common_back), Glyph.Back, Palette.Lavender, onFocus = { StartMenu.hiddenFocus = "back" }) { StartMenu.page = null }
    val tuck = showing || !peeks.isEmpty
    val showingWord = Tx.s(R.string.panels_start_hidden_showing)
    val toggle = MenuAction(
        "toggle", Tx.s(if (tuck) R.string.panels_start_hidden_tuck else R.string.panels_start_hidden_show_all), Glyph.Eye, Palette.Sky,
        value = if (showing) showingWord else if (!peeks.isEmpty) Tx.s(R.string.panels_start_hidden_some_showing) else null,
        onFocus = { StartMenu.hiddenFocus = "toggle" },
    ) {
        if (tuck) {
            // In private mode this is the combo's own tuck-away (with its toast), and the page closes behind it.
            Privacy.tuckAway()
            if (!Privacy.enabled) Toasts.show(Tx.s(R.string.panels_start_hidden_tucked))
        } else {
            toggleShowHidden()
            Toasts.show(Tx.s(R.string.panels_start_hidden_shown))
        }
    }
    return listOf(back, toggle) + items.map { item ->
        MenuAction(
            item.id, hiddenName(item), hiddenGlyph(item), Palette.Lavender,
            value = if (!showing && HiddenItems.peeking(item)) showingWord else null,
            sub = HiddenItems.kind(item),
            art = hiddenArt(item),
            onFocus = { StartMenu.hiddenFocus = item.id; showDetails(item) },
        ) { unhideFromStart(item) }
    }
}

private fun hiddenName(item: HiddenItem): String = when (item) {
    is HiddenItem.Folder -> item.name
    is HiddenItem.HomeFolderItem -> item.folder.name
    is HiddenItem.GameItem -> item.name
}

private fun hiddenGlyph(item: HiddenItem): Glyph = when (item) {
    is HiddenItem.Folder -> if (item.isSystem) Glyph.Cartridge else Glyph.Folder
    is HiddenItem.HomeFolderItem -> Glyph.Folder
    is HiddenItem.GameItem -> Glyph.Gamepad
}

/** A game's cover or a system's icon; folders keep their glyph. */
private fun hiddenArt(item: HiddenItem): (@Composable () -> Unit)? = when {
    item is HiddenItem.GameItem -> ({
        val box = remember(item.game.key) { Libraries.of(item.game)?.box(item.game) }
        if (box != null) AsyncImage(box, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Box(Modifier.size(20.dp)) { GlyphIcon(Glyph.Gamepad, Palette.Lavender) }
    })
    item is HiddenItem.Folder && item.isSystem && item.lib != null -> ({ SystemIcon(item.lib.platform, 32.dp) })
    else -> null
}

/** The details screen follows the highlight, through the usual rules (BottomContent asks canShow for a game). */
private fun showDetails(item: HiddenItem) {
    Selection.current = when {
        item is HiddenItem.GameItem -> Selected.GameItem(item.game)
        item is HiddenItem.Folder && item.isSystem && item.lib != null && Privacy.canShowFolder(item.entry.system, null) -> Selected.System(item.lib)
        else -> Selected.Home
    }
}

/** A: unhides the highlighted thing; the highlight moves to the next one (or the one before). Toasts name nothing. */
private fun unhideFromStart(item: HiddenItem) {
    val items = HiddenItems.all()
    val at = items.indexOfFirst { it.id == item.id }
    val next = items.getOrNull(at + 1) ?: items.getOrNull(at - 1)
    val said = HiddenItems.unhide(item)
    Sounds.toggle(true)
    // Undo hides it again and the highlight goes back to it.
    val undo = { HiddenItems.rehide(item); FocusMemory.remember("${StartMenu.ROUTE}:hidden", item.id) }
    if (next == null) {
        StartMenu.page = null
        Undo.offer(Tx.s(R.string.panels_start_hidden_none_left, said), undo)
    } else {
        FocusMemory.remember("${StartMenu.ROUTE}:hidden", next.id)
        Undo.offer(said, undo)
    }
}

/** Y on the Hidden page: shows the highlighted thing on its own, or hides it again. */
private fun peekFocused() {
    val item = HiddenItems.all().firstOrNull { it.id == StartMenu.hiddenFocus } ?: return
    if (Privacy.showHidden) { Toasts.show(Tx.s(R.string.panels_start_hidden_all_showing)); return }
    HiddenItems.togglePeek(item)
    Sounds.select()
    Toasts.show(Tx.s(if (HiddenItems.peeking(item)) R.string.panels_start_hidden_peek else R.string.panels_start_hidden_again))
}

/** "3 playing · 12 trophies" for Start › Stats & trophies; null when there is nothing yet. */
private fun startStatsLine(): String? {
    val playing = Collections.byStatus().firstOrNull { it.id == Collections.PLAYING }?.games?.size ?: 0
    val trophies = io.github.zahakj.tandem.data.ExtrasStore.trophies.unlocked.size
    // One short detail, beside the label on the same line: the trophies, or what you're playing before the first.
    return when {
        trophies > 0 -> Tx.p(R.plurals.panels_start_trophies, trophies)
        playing > 0 -> Tx.p(R.plurals.panels_start_playing, playing)
        else -> null
    }
}

/**
 * One row of the panel, on one line at any height from compact to roomy: an icon chip, the label, and on the right a
 * quiet word for its value or detail ("3", "Moved Emulators"), a ✓ for the chosen one, or a › for a row that opens a
 * page. Highlighted, it glows like a Y menu's row (ActionRow), whose look it shares.
 */
@Composable
private fun StartRow(a: MenuAction, routeKey: String, wanted: Boolean, height: Dp, modifier: Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    var pressed by remember { mutableStateOf(false) }
    val glow = remember { Animatable(0f) }
    LaunchedEffect(focused) { glow.animateTo(if (focused) 1f else 0f, if (Motion.style == AnimationStyle.Off) tween(0) else tween(160)) }
    val color = a.color
    val stroke = with(LocalDensity.current) { 2.dp.toPx() }
    val click = { a.onClick(); Sounds.select() }
    val requester = remember { FocusRequester() }
    Row(
        modifier.fillMaxWidth().height(height)
            .takesFocus(routeKey, a.id, wanted)
            .focusRequester(requester)
            .onFocusChanged { if (it.isFocused) { Sounds.move(); a.onFocus?.invoke() } else pressed = false }
            .graphicsLayer {
                val sc = if (pressed) 0.97f else 1f + 0.02f * glow.value
                scaleX = sc; scaleY = sc
            }
            .drawWithCache {
                val r = CornerRadius(size.height / 2f)
                // Brightest at the icon end, the start side.
                val rtl = layoutDirection == androidx.compose.ui.unit.LayoutDirection.Rtl
                val fill = startGradient(listOf(color.copy(alpha = 0.26f), color.copy(alpha = 0.08f)), rtl)
                val ring = Brush.linearGradient(listOf(lerp(color, Color.White, 0.55f), color, lerp(color, Color.White, 0.3f)), Offset.Zero, Offset(size.width, size.height))
                val halo = startGradient(listOf(color.copy(alpha = 0.22f), Color.Transparent), rtl)
                onDrawBehind {
                    val g = glow.value
                    if (g > 0.01f) {
                        drawRoundRect(halo, Offset(-stroke * 2, -stroke * 2), Size(size.width + stroke * 4, size.height + stroke * 4), CornerRadius(r.x + stroke * 2), alpha = g * 0.8f)
                        drawRoundRect(fill, cornerRadius = r, alpha = g)
                        drawRoundRect(ring, Offset(stroke / 2, stroke / 2), Size(size.width - stroke, size.height - stroke), r, style = Stroke(stroke), alpha = g)
                    }
                }
            }
            .focusable(interactionSource = interaction)
            .onKeyEvent { e ->
                val select = e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.NumPadEnter || e.key == Key.ButtonA
                if (!select) return@onKeyEvent false
                val was = pressed
                pressed = e.type == KeyEventType.KeyDown
                if (e.type == KeyEventType.KeyUp && was) click()
                true
            }
            .pointerInput(a) {
                detectTapGestures(
                    onPress = { pressed = true; tryAwaitRelease(); pressed = false },
                    onTap = { runCatching { requester.requestFocus() }; click() },
                )
            }
            .padding(start = 6.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val chip = (height - 8.dp).coerceIn(22.dp, 28.dp)
        Box(
            Modifier.size(chip).graphicsLayer { shape = RoundedCornerShape(if (a.art != null) chip * 0.3f else chip / 2); clip = true }
                .background(color.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center,
        ) {
            when {
                a.art != null -> a.art.invoke()
                a.icon != null -> Box(Modifier.size(chip * 0.74f)) { TIconGlyph(a.icon, color) }
                a.glyph != null -> Box(Modifier.size(chip * 0.66f)) { GlyphIcon(a.glyph, color) }
            }
        }
        Spacer(Modifier.width(10.dp))
        ShrinkText(a.label, MaterialTheme.typography.titleSmall, Modifier.weight(1f), maxLines = 1)
        val detail = a.value ?: a.sub
        when (detail) {
            null -> Unit
            "▸" -> Text("›", style = MaterialTheme.typography.titleLarge, color = if (focused) color else Palette.Muted)
            "✓" -> Text("✓", style = MaterialTheme.typography.titleSmall, color = Palette.Mint)
            else -> Text(
                detail, Modifier.padding(start = 8.dp).widthIn(max = 130.dp), style = MaterialTheme.typography.labelLarge,
                color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
