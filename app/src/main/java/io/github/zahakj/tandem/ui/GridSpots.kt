package io.github.zahakj.tandem.ui

import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.zahakj.tandem.data.FolderSort
import io.github.zahakj.tandem.data.GridShown
import io.github.zahakj.tandem.data.GridSlots
import io.github.zahakj.tandem.data.GridTree
import io.github.zahakj.tandem.data.IconGridStore
import kotlinx.coroutines.launch

/*
 * Empty spots on the icon grid and inside home folders, like the empty slots of the 3DS HOME menu, except that they
 * look like nothing at all until the highlight lands on one: then the theme's ring and a soft "+" fade in. A (or Y)
 * opens "Add here": a new folder, a game, system, collection, app, Tandem app or widget, or Paste while carrying a
 * tile. The slots themselves live in IconGridStore (GridSlots).
 */

/** The key of empty slot [i] of the grid on screen (not saved anywhere). */
private const val GAP = "gap:"

/** The key of slot [i] when a widget covers it: never focused, never offered for adding. */
private const val COVERED = "cov:"

fun gapKey(i: Int): String = GAP + i

fun coveredKey(i: Int): String = COVERED + i

fun isGapKey(key: String?): Boolean = key != null && key.startsWith(GAP)

fun isCoveredKey(key: String?): Boolean = key != null && key.startsWith(COVERED)

fun gapIndex(key: String): Int? = if (key.startsWith(GAP)) key.substring(GAP.length).toIntOrNull() else null

/** Where Add to home puts what is picked: slot [index] of the home grid ([folder] null) or of a folder. */
data class SlotTarget(val folder: String?, val index: Int)

private val EmptySpotColor = Color(0xFF8C95B5)

/** The highlighted "tile" while an empty spot has focus: one for every spot, so moving between them is calm. */
val EmptySpotItem: GridItem by lazy {
    GridItem(
        "gap", TileKind.Empty, "Empty spot", "Free spot", "", EmptySpotColor, TileArt.Symbol(Glyph.Plus, EmptySpotColor), emptyList(),
        io.github.zahakj.tandem.Selected.Group("Empty spot", "", emptyList(), EmptySpotColor), default = false, open = {},
        blurb = "A adds a folder, game, app or widget here",
    )
}

/** The home grid as it shows now, or as saved when it isn't on screen. */
fun homeShown(): GridShown = IconGrid.home ?: IconGridStore.config.let { c ->
    GridShown(c.order, c.order.filter { it.isNotEmpty() }.toHashSet(), c.lanes)
}

/**
 * An empty slot: nothing at all until it is highlighted, then the theme's highlight ring around the empty spot, a
 * faint fill and a "+", fading in together, so a highlighted empty spot reads as clearly as a tile. One cached draw; focus only animates a value read while drawing, so moving
 * through empty spots recomposes nothing. A tap does nothing; a long press opens Add here ([onClick]).
 */
@Composable
fun EmptySlot(tile: Dp, corner: Dp, labelRoom: Dp, modifier: Modifier = Modifier, onFocus: () -> Unit, onClick: () -> Unit, hovered: Boolean = false) {
    val lit = remember { Animatable(0f) }
    // Focused, or under a tile being dragged here (GridDrag.kt): the ring and the + show.
    val focused = remember { booleanArrayOf(false) }
    androidx.compose.runtime.LaunchedEffect(hovered) { lit.animateTo(if (hovered || focused[0]) 1f else 0f, Motion.focus()) }
    val pressed = remember { booleanArrayOf(false) }
    val requester = remember { androidx.compose.ui.focus.FocusRequester() }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val line = Palette.Text
    // The same ring a highlighted tile wears (IconTile), in the theme's highlight colour.
    val ring = lerp(Palette.highlightFor(EmptySpotColor), Color.White, 0.62f)
    val ringW = with(density) { (Themes.live.highlightThickness * 0.75f).coerceIn(1.2f, 6f).dp.toPx() }
    val grow = Themes.live.focusScale.coerceIn(1f, 1.12f)
    Column(modifier.width(tile), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(tile)
                .liftOrigin(corner / tile)
                .onFocusChanged {
                    if (it.isFocused) { onFocus(); Sounds.move() } else pressed[0] = false
                    focused[0] = it.isFocused
                    scope.launch { lit.animateTo(if (it.isFocused || hovered) 1f else 0f, Motion.focus()) }
                }
                .focusRequester(requester)
                .focusable()
                .onKeyEvent { e -> selectKey(e, { pressed[0] }, { pressed[0] = it }, onClick) }
                // Touch: the first tap highlights the spot, a tap on the highlighted spot opens Add here (no long
                // press: on the grid a hold picks tiles up, and the menu is Y).
                .pointerInput(onClick) {
                    detectTapGestures(onTap = { if (focused[0]) { onClick(); Sounds.select() } else runCatching { requester.requestFocus() } })
                }
                .graphicsLayer { val s = 1f + (grow - 1f) * lit.value; scaleX = s; scaleY = s }
                .drawWithCache {
                    val ringStroke = Stroke(ringW)
                    val gap = 3.5.dp.toPx() + ringW / 2f
                    val ringSize = Size(size.width + gap * 2, size.height + gap * 2)
                    val ringRadius = CornerRadius(corner.toPx() + gap)
                    val arm = size.minDimension * 0.13f
                    val plusW = 2.4.dp.toPx()
                    val c = Offset(size.width / 2f, size.height / 2f)
                    onDrawBehind {
                        val f = lit.value
                        if (f > 0.01f) {
                            drawRoundRect(ring.copy(alpha = 0.16f * f), cornerRadius = CornerRadius(corner.toPx()))
                            drawLine(line.copy(alpha = 0.8f * f), c - Offset(arm, 0f), c + Offset(arm, 0f), plusW, StrokeCap.Round)
                            drawLine(line.copy(alpha = 0.8f * f), c - Offset(0f, arm), c + Offset(0f, arm), plusW, StrokeCap.Round)
                            drawRoundRect(ring, Offset(-gap, -gap), ringSize, ringRadius, alpha = f, style = ringStroke)
                        }
                    }
                },
        )
        if (labelRoom > 0.dp) Spacer(Modifier.height(labelRoom))
    }
}

/** A cell a widget covers: room only, never focused. */
@Composable
internal fun CoveredSlot(tile: Dp, cell: Dp) = Spacer(Modifier.size(tile, cell))

/** The empty spot lifted in the Add here menu: a faint rounded square with a soft "+". */
@Composable
private fun EmptySpotArt() {
    val line = Palette.Text
    // Solid: the lift draws a shadow under it, which showed through a see-through fill as a grey wash and a stray
    // dark square beside the +.
    val fill = Palette.CardHigh
    Box(
        Modifier.fillMaxSize().drawWithCache {
            val stroke = 2.dp.toPx()
            val radius = CornerRadius(size.minDimension * 0.16f)
            val arm = size.minDimension * 0.11f
            val c = Offset(size.width / 2f, size.height / 2f)
            onDrawBehind {
                drawRect(fill)
                drawRoundRect(line, cornerRadius = radius, alpha = 0.08f)
                drawLine(line.copy(alpha = 0.6f), c - Offset(arm, 0f), c + Offset(arm, 0f), stroke * 1.2f, StrokeCap.Round)
                drawLine(line.copy(alpha = 0.6f), c - Offset(0f, arm), c + Offset(0f, arm), stroke * 1.2f, StrokeCap.Round)
            }
        },
    )
}

/** A widget kind's symbol in a menu row. */
@Composable
private fun KindSymbol(kind: WidgetKind) {
    Text(kind.icon, style = MaterialTheme.typography.titleMedium, color = kind.accent, maxLines = 1)
}

/** Add to home showing only [filter] things, for slot [target]. */
private fun openAdd(target: SlotTarget, filter: String) {
    IconGrid.menu = null
    IconGrid.choosingFolder = false
    FocusMemory.forget(IconGrid.ADD_ROUTE)
    IconGrid.addFilter = filter
    IconGrid.addAt = target
    IconGrid.adding = true
}

/** A or Y on an empty spot: "Add here". */
internal fun gapMenuSpec(t: TileMenuTarget, catalog: GridCatalog, ctx: Context): MenuSpec? {
    val at = gapIndex(t.key) ?: return null
    val folder = t.folder
    val shown = shownFor(folder) ?: return null
    val route = routeFor(folder)
    val target = SlotTarget(folder, at)
    // A carried tile can be pasted here; into a folder, never a widget, and a folder only where it fits (no cycles).
    val carried = IconGrid.moving?.takeIf {
        it.carried && !(folder != null && (GridWidgets.idOf(it.key) != null || !GridTree.canNest(IconGridStore.config, it.key, folder)))
    }
    val actions = buildList {
        if (carried != null) {
            val item = catalog.item(carried.key)
            val name = item?.title ?: "it"
            add(MenuAction("paste", "Paste $name", Glyph.Move, Palette.Sky) {
                if (folder != null) suggestForFirst(ctx, folder, item)
                GridUndoUi.run("Moved $name") { IconGridStore.place(carried.key, carried.folder, folder, shown, at, item?.default == true) }
                IconGrid.moving = null
                FocusMemory.remember(route, carried.key)
                IconGrid.closeMenu()
                Sounds.confirm()
            })
        }
        if (GridTree.canHoldFolder(IconGridStore.config, folder)) add(MenuAction("folder", "New folder…", Glyph.Folder, Palette.Butter) {
            IconGrid.closeMenu()
            TextEntry.ask("Name the folder", "New folder") { name ->
                val n = name.trim().ifEmpty { "Folder" }
                val grid = shownFor(folder) ?: return@ask
                val id = IconGridStore.createFolderAt(n, grid, at, IconGridStore.config.folders.size, into = folder)
                FocusMemory.remember(route, "folder:$id")
                Nav.focusTick++
                Sounds.confirm()
                Toasts.show("Made the folder $n here")
            }
        })
        // A folder that fills itself, made here: inside an arranged system, that system's games of a genre, say.
        if (GridTree.canHoldFolder(IconGridStore.config, folder)) add(MenuAction("smart", "Smart folder…", Glyph.Sparkle, Palette.Lavender, sub = "Fills itself: a genre, unplayed games…") {
            IconGrid.closeMenu()
            val grid = shownFor(folder) ?: return@MenuAction
            val system = folder?.let { IconGridStore.folder(it)?.system }
            val rule = io.github.zahakj.tandem.data.SmartRule(systems = listOfNotNull(system))
            val id = IconGridStore.createFolderAt("Smart folder", grid, at, IconGridStore.config.folders.size, into = folder, rule = rule)
            FocusMemory.remember(route, "folder:$id")
            SmartFolderForm.edit(id)
        })
        add(MenuAction("game", "Game…", Glyph.Cartridge, Palette.Mint, sub = "One or several, by system") {
            IconGrid.menu = null
            IconGrid.choosingFolder = false
            FocusMemory.forget("gridpick")
            IconGrid.pickingGames = target
        })
        add(MenuAction("system", "System…", Glyph.Gamepad, Palette.Sky) { openAdd(target, "system") })
        add(MenuAction("collection", "Collection or genre…", Glyph.Star, Palette.Peach) { openAdd(target, "collection") })
        add(MenuAction("app", "App…", Glyph.Grid, Palette.Sky) { openAdd(target, "app") })
        add(MenuAction("tandem", "Tandem app…", Glyph.Sparkle, Palette.Lavender, sub = "Themes, Journal, Gallery…") { openAdd(target, "tandem") })
        add(MenuAction("widget", "Widget…", Glyph.Layers, Palette.Butter, sub = "Clock, battery, play stats…") { IconGrid.choosingFolder = true })
        add(MenuAction("tidy", "Tidy up", Glyph.Sparkle, Palette.Mint, more = true) {
            IconGrid.closeMenu()
            if (folder == null) tidyHome() else tidyFolder(folder)
        })
        folder?.let { IconGridStore.folder(it)?.system }?.let { system ->
            add(MenuAction("unarrange", "Show as a plain list", Glyph.Sort, Palette.Sky, more = true) { IconGrid.closeMenu(); SystemFolders.unarrange(system) })
        }
        GridUndoUi.action { IconGrid.closeMenu() }?.let { add(it) }
        add(MenuAction("search", "Search", Glyph.Search, Palette.Sky, more = true) { IconGrid.menu = null; Nav.search = true })
        add(MenuAction("swap", "Swap screens", Glyph.Swap, Palette.Lavender, value = if (ScreenRoles.xSwaps()) "X" else null, more = true) {
            IconGrid.closeMenu(); ScreenRoles.toggle(ctx)
        })
    }
    // Widget…: every kind, added in this spot at its usual size (or the biggest that fits here). In a folder, every spot
    // is one cell: the kinds that come that small.
    val page = if (IconGrid.choosingFolder) MenuPage(
        "Add a widget",
        WidgetKinds.all.map { kind ->
            val ok = kind.available() && (folder == null || GridWidgets.fitsFolder(kind))
            MenuAction(
                "w:${kind.id}", kind.name, color = kind.accent,
                value = if (!ok) null else if (folder == null) GridWidgets.spanFor(kind.defaultSize).label else "1×1",
                sub = if (ok) kind.blurb else if (!kind.available()) kind.needs ?: "Not available yet" else "Too big for a folder: home grid only",
                art = { KindSymbol(kind) },
            ) {
                val key = if (folder == null) GridWidgets.add(kind, homeShown(), at) else GridWidgets.addInFolder(kind, folder, shown, at)
                IconGrid.closeMenu()
                if (key != null) FocusMemory.remember(route, key)
            }
        },
        onBack = { IconGrid.choosingFolder = false },
    ) else null
    val where = gridName(folder)
    return MenuSpec(IconGrid.MENU_ROUTE + ":" + (folder ?: "") + t.key, "Empty spot", where, EmptySpotColor, actions, page) { EmptySpotArt() }
}

/** Tidy up: every tile on the home grid packed to the front, in the same order (widgets whole). */
fun tidyHome() {
    val h = homeShown()
    val packed = GridSlots.tidy(h.slots, h.present, h.lanes, h.ext, h.page)
    if (packed == GridSlots.trim(h.slots)) { Toasts.show("Already tidy"); return }
    GridUndoUi.run("Tidied up") { IconGridStore.setSlots(packed, h.lanes) }
    Nav.focusTick++
    Sounds.confirm()
}

/** Tidy up inside a folder. */
fun tidyFolder(id: String) {
    val f = IconGridStore.folder(id) ?: return
    if (f.sort != FolderSort.MANUAL) { Toasts.show("Sorted ${FolderSort.label(f.sort)}: always tidy"); return }
    val packed = GridSlots.tidy(f.items)
    if (packed == GridSlots.trim(f.items)) { Toasts.show("Already tidy"); return }
    GridUndoUi.run("Tidied up ${f.name}") { IconGridStore.setFolderItems(id, packed) }
    Nav.focusTick++
    Sounds.confirm()
}

/**
 * A folder made empty (an empty spot's New folder…) gets the designer's suggested look from the first thing put in
 * it, as a folder made from a tile does. Call before the thing goes in.
 */
internal fun suggestForFirst(ctx: Context, folderId: String, first: GridItem?) {
    if (first == null || IconGridStore.folder(folderId)?.items?.any { it.isNotEmpty() } != false) return
    FolderDesign.suggest(ctx, folderId, first)
}

/**
 * Before a tile leaves the home grid ([folder] null) or a folder: an arrangement saved before slots gets them now, so
 * the tile leaves a gap where it was instead of everything after it closing up.
 */
internal fun keepPlaces(folder: String?) {
    if (folder == null) {
        val h = IconGrid.home ?: return
        if (IconGridStore.config.lanes <= 0) IconGridStore.setSlots(h.slots, h.lanes)
    } else {
        val f = IconGridStore.folder(folder) ?: return
        val s = IconGrid.folderShown?.takeIf { IconGrid.openFolder == folder } ?: return
        if (f.lanes <= 0) IconGridStore.setFolderItems(folder, s.slots, s.lanes)
    }
}

/**
 * Y while moving: a widget changes size (the next size that fits); anything else is carried instead, so the D-pad is
 * free to go anywhere (into a folder too) and Paste it.
 */
internal fun carryMove() {
    val mv = IconGrid.moving ?: return
    if (GridWidgets.idOf(mv.key) != null) { GridWidgets.resizeMoving(mv); return }
    mv.order.clear()
    mv.order.addAll(mv.original)
    mv.carried = true
    Toasts.show("Carrying it · A on an empty spot › Paste")
}
