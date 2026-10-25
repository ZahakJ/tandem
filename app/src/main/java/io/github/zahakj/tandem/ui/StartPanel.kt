package io.github.zahakj.tandem.ui

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
 * the top (blinking, glancing about); the rows unfold one after another like a folded strip of paper; New, Sort and
 * Settings open their own page inside the panel. B goes back a page or folds it all away again (rows fold up in
 * reverse, then the panel slides out). The screen behind blurs and dims. D-pad only, focus stays inside. Works in
 * every home layout and on every screen outside Settings (where Start still goes back out).
 */

object StartMenu {
    var open by mutableStateOf(false)
        private set
    /** The page inside the panel: null for the main one, "new", "sort", "settings" or "hidden". */
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
    ProvideLayer(layer) {
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier.fillMaxSize().graphicsLayer { alpha = slide.value.coerceIn(0f, 1f) }
                    .background(Palette.NightDeep.copy(alpha = 0.42f)).swallowTaps { StartMenu.close() },
            )
            Column(
                Modifier.fillMaxHeight().width(PanelWidth)
                    .graphicsLayer {
                        translationX = -(1f - slide.value) * (size.width + 24f * density)
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
                Spacer(Modifier.height(6.dp))
                // Every page of the panel stays composed while it is open: the main page at once, the others (New,
                // Sort, Settings) a few frames later, so opening one is only a slide in their layers, never a page
                // composed in the frame A is pressed. Hidden pages are measured but not placed (no focus, no drawing).
                // One page per frame, Settings first (the likeliest).
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

private val StartPages = listOf(null, "settings", "new", "sort", "hidden")

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
    // 1 shown, 0 hidden; sub-pages come from the right, the main page from the left.
    val enter = remember { Animatable(if (shown) 1f else 0f) }
    var drawn by remember { mutableStateOf(shown) }
    LaunchedEffect(shown) {
        if (shown) { drawn = true; enter.animateTo(1f, if (still) tween(0) else spring(0.85f, 500f)) }
        else { enter.animateTo(0f, if (still) tween(0) else tween(170)); drawn = false }
    }
    val side = if (page == null) -1f else 1f
    // A page opened inside the panel unfolds its own rows.
    val pageUnfold = remember { Animatable(if (page == null || still) 1f else 0f) }
    LaunchedEffect(shown) {
        if (page == null) return@LaunchedEffect
        if (shown) { pageUnfold.snapTo(if (still) 1f else 0f); pageUnfold.animateTo(1f, tween(if (still) 0 else 420, easing = LinearEasing)) }
    }
    val scroll = rememberScrollState()
    androidx.compose.ui.layout.Layout(
        content = {
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
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                rows.forEachIndexed { i, a ->
                    ActionRow(
                        a, routeKey, shown && a.id == target, i, rise = false,
                        modifier = Modifier.unfoldRow(i, rows.size) { if (page == null) unfold.value else pageUnfold.value * unfold.value.coerceAtLeast(0f) },
                    )
                }
            }
        },
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
        Pip(62.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Tandem", style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val line = listOfNotNull(Clock.format(now), if (battery >= 0) "$battery%" else null).joinToString(" · ")
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
        ButtonHint("A", if (onItem) "Unhide" else "Choose", Palette.Mint)
        // Y shows one thing on its own, which means nothing while everything hidden is showing.
        if (onItem && !io.github.zahakj.tandem.Privacy.showHidden) {
            val peeking = HiddenItems.all().firstOrNull { it.id == StartMenu.hiddenFocus }?.let { HiddenItems.peeking(it) } == true
            ButtonHint("Y", if (peeking) "Hide again" else "Show here", Palette.Sky)
        }
        ButtonHint("B", if (StartMenu.page != null) "Back" else "Close", Palette.Lavender)
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
    val back = MenuAction("back", "Back", Glyph.Back, Palette.Lavender) { StartMenu.page = null }
    return when (page) {
        "hidden" -> hiddenRows()
        "new" -> listOf(back) + if (grid) listOf(
            MenuAction("folder", "New folder…", Glyph.Folder, Palette.Butter) {
                StartMenu.go {
                    TextEntry.ask("Name the folder", "New folder") { name ->
                        val n = name.trim().ifEmpty { "Folder" }
                        IconGridStore.createFolder(n, null, homeShown(), IconGridStore.config.folders.size)
                        Toasts.show("Made the folder $n. Y › Move to folder on any tile")
                    }
                }
            },
            MenuAction("smart", "Smart folder…", Glyph.Sparkle, Palette.Lavender) { StartMenu.go { SmartFolderForm.openNew() } },
            MenuAction("add", "Add to home…", Glyph.Plus, Palette.Mint) {
                StartMenu.go { FocusMemory.forget(IconGrid.ADD_ROUTE); if (Nav.route != Route.Home) Nav.replace(Route.Home); IconGrid.addAt = null; IconGrid.addFilter = null; IconGrid.adding = true }
            },
            MenuAction("widget", "Widget…", Glyph.Layers, Palette.Sky) { StartMenu.go { Nav.go(Route.Widgets) } },
        ) else listOf(
            MenuAction("widget", "Widget…", Glyph.Layers, Palette.Sky) { StartMenu.go { Nav.go(Route.Widgets) } },
        )
        "sort" -> listOf(back) + (if (grid) listOf(
            MenuAction("home-name", "Home: A to Z", Glyph.Sort, Palette.Sky) { sortHome("name") },
            MenuAction("home-recent", "Home: recently played", Glyph.Clock, Palette.Sky) { sortHome("recent") },
            MenuAction("home-most", "Home: most played", Glyph.Chart, Palette.Sky) { sortHome("most") },
            MenuAction("home-kind", "Home: systems first", Glyph.Cartridge, Palette.Sky) { sortHome("kind") },
        ) else emptyList()) + SortOrder.entries.map { o ->
            MenuAction("games-${o.name}", "Games: ${o.label}", Glyph.Sort, Palette.Mint, value = if (Store.settings.sort == o) "✓" else null) {
                Store.updateSettings { it.copy(sort = o) }
                Toasts.show("Games sorted: ${o.label}")
            }
        }
        "settings" -> listOf(
            back,
            MenuAction("all", "All settings", null, Palette.Accent, icon = TIcon.Gear) { StartMenu.go { Nav.go(Route.Settings) } },
        ) + SettingsCategories.ids.map { id ->
            MenuAction("cat:$id", SettingsCategories.title(id), CategoryGlyphs[id] ?: Glyph.Info, Palette.Lavender) {
                StartMenu.go { Nav.go(SettingsCategories.route(id)) }
            }
        } + listOfNotNull(
            MenuAction("cocoon", "Import my Cocoon setup", Glyph.Download, Palette.Sky) { StartMenu.go { CocoonSetup.run(ctx) } }
                // Read in the background (Facts), never while composing: listing ROMs/backups took tens of ms.
                .takeIf { Facts.cocoonBackup != null },
        )
        else -> buildList {
            add(MenuAction("apps", "All apps", null, Palette.Sky, icon = TIcon.AllApps) { StartMenu.go { Panels.openApps() } })
            add(MenuAction("new", "New", Glyph.Plus, Palette.Mint, value = "▸") { FocusMemory.forget(StartMenu.ROUTE + ":new"); StartMenu.page = "new" })
            add(MenuAction("sort", "Sort", Glyph.Sort, Palette.Sky, value = "▸") { FocusMemory.forget(StartMenu.ROUTE + ":sort"); StartMenu.page = "sort" })
            add(MenuAction("search", "Search", Glyph.Search, Palette.Sky) { StartMenu.go { Nav.search = true } })
            if (grid) {
                add(MenuAction("edit", "Edit grid", Glyph.Move, Palette.Butter) { StartMenu.go { editGrid() } })
                // Packs every tile to the front, closing the gaps (the grid otherwise keeps them).
                add(MenuAction("tidy", "Tidy up", Glyph.Sparkle, Palette.Mint) { StartMenu.go { tidyHome() } })
                // Only while there is a grid change to take back.
                IconGridStore.undoLabel?.let { label ->
                    add(MenuAction("undo", "Undo last change", Glyph.Refresh, Palette.Butter, sub = label) { StartMenu.go { GridUndoUi.undo() } })
                }
            }
            else add(MenuAction("edit", "Edit widgets", Glyph.Move, Palette.Butter) { StartMenu.go { Nav.go(Route.Widgets) } })
            if (RunningGames.list.isNotEmpty()) add(
                MenuAction("switch", "Switch games", Glyph.Swap, Palette.Mint, value = "${RunningGames.list.size}") { StartMenu.go { Panels.openSwitcher() } },
            )
            // Only when something is hidden, and in private mode only while revealed (HiddenAccess.startRow).
            if (HiddenItems.offered()) add(
                MenuAction("hidden", "Hidden", Glyph.Eye, Palette.Lavender, value = "${HiddenItems.count()}") { StartMenu.openHidden() },
            )
            add(MenuAction("quick", "Quick settings", Glyph.Volume, Palette.Peach) { StartMenu.go { Panels.toggleQuick() } })
            add(MenuAction("settings", "Settings", null, Palette.Accent, value = "▸", icon = TIcon.Gear) {
                FocusMemory.forget(StartMenu.ROUTE + ":settings"); StartMenu.page = "settings"
            })
            add(MenuAction("rest", "Screensaver", Glyph.Clock, Palette.Lavender) { StartMenu.go { Screensaver.start() } })
        }
    }
}

private val CategoryGlyphs = mapOf(
    "look" to Glyph.Theme, "home" to Glyph.Home, "screens" to Glyph.Screens, "sound" to Glyph.Music, "controls" to Glyph.Gamepad,
    "library" to Glyph.Cartridge, "art" to Glyph.Download, "play" to Glyph.Chart, "data" to Glyph.Info,
)

/** Edit grid: the highlighted tile (or the first) starts moving, like Y › Move. */
private fun editGrid() {
    if (Nav.route != Route.Home) { Nav.replace(Route.Home); Toasts.show("Pick a tile, then Start › Edit grid"); return }
    val (_, arranged) = IconGrid.menuData ?: return
    val key = FocusMemory.get(Route.Home.key)?.takeIf { it in arranged } ?: arranged.firstOrNull() ?: return
    startMove(key, null, homeShown().slots)
}

/** Sort › Home: puts the home grid in a new order (Y › Move still works after). */
private fun sortHome(by: String) {
    val (catalog, arranged) = IconGrid.menuData ?: return
    val items = arranged.mapNotNull { catalog.item(it) }
    val sorted = when (by) {
        "name" -> items.sortedBy { it.title.lowercase() }
        "recent" -> items.sortedByDescending { it.games.maxOfOrNull { g -> g.meta.lastPlayed } ?: 0L }
        "most" -> items.sortedByDescending { it.games.sumOf { g -> g.meta.playSeconds } }
        else -> items.sortedBy { it.kind.ordinal }
    }
    GridUndoUi.run(
        when (by) { "name" -> "Home sorted A to Z"; "recent" -> "Home sorted by recently played"; "most" -> "Home sorted by most played"; else -> "Systems first" },
    ) { IconGridStore.setOrder(sorted.map { it.key }) }
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
    val back = MenuAction("back", "Back", Glyph.Back, Palette.Lavender, onFocus = { StartMenu.hiddenFocus = "back" }) { StartMenu.page = null }
    val tuck = showing || !peeks.isEmpty
    val toggle = MenuAction(
        "toggle", if (tuck) "Tuck away" else "Show all hidden", Glyph.Eye, Palette.Sky,
        value = if (showing) "Showing" else if (!peeks.isEmpty) "Some showing" else null,
        onFocus = { StartMenu.hiddenFocus = "toggle" },
    ) {
        if (tuck) {
            // In private mode this is the combo's own tuck-away (with its toast), and the page closes behind it.
            Privacy.tuckAway()
            if (!Privacy.enabled) Toasts.show("Hidden games are tucked away")
        } else {
            toggleShowHidden()
            Toasts.show("Hidden games are showing")
        }
    }
    return listOf(back, toggle) + items.map { item ->
        MenuAction(
            item.id, hiddenName(item), hiddenGlyph(item), Palette.Lavender,
            value = if (!showing && HiddenItems.peeking(item)) "Showing" else null,
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
        Undo.offer("$said. Nothing else is hidden", undo)
    } else {
        FocusMemory.remember("${StartMenu.ROUTE}:hidden", next.id)
        Undo.offer(said, undo)
    }
}

/** Y on the Hidden page: shows the highlighted thing on its own, or hides it again. */
private fun peekFocused() {
    val item = HiddenItems.all().firstOrNull { it.id == StartMenu.hiddenFocus } ?: return
    if (Privacy.showHidden) { Toasts.show("Everything hidden is showing already"); return }
    HiddenItems.togglePeek(item)
    Sounds.select()
    Toasts.show(if (HiddenItems.peeking(item)) "Showing until hidden games are tucked away" else "Hidden again")
}
