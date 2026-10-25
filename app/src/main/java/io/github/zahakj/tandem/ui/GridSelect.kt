package io.github.zahakj.tandem.ui

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.zahakj.tandem.HiddenFolders
import io.github.zahakj.tandem.data.FolderSort
import io.github.zahakj.tandem.data.GridShown
import io.github.zahakj.tandem.data.GridTree
import io.github.zahakj.tandem.data.HomeFolder
import io.github.zahakj.tandem.data.IconGridStore
import io.github.zahakj.tandem.data.Store
import io.github.zahakj.tandem.plural

/*
 * Power editing on the icon grid: marking several tiles (Select, a long A, or Y › Select) and acting on them at once
 * (move them to a spot, into a folder, off the grid, hide them), and Undo for every change that takes something away
 * or moves many things (IconGridStore keeps the steps). Selection is per grid: the home grid or one folder.
 */

/** Tiles marked on one grid: the home grid ([folder] null) or a folder. [keys] in the order they were marked. */
class GridSelection(val folder: String?) {
    val keys = mutableStateListOf<String>()
}

object GridSelect {
    /** True while [key] is marked on grid [folder]. Tiles read it in a derivedStateOf, so only those that change redraw. */
    fun marked(folder: String?, key: String): Boolean = IconGrid.selecting?.let { it.folder == folder && key in it.keys } == true

    /** Selecting on grid [folder] right now. */
    fun on(folder: String?): Boolean = IconGrid.selecting?.folder == folder && IconGrid.selecting != null

    /** Things that can be marked: tiles, not empty spots, the cells under a widget or Now playing. */
    fun markable(key: String?): Boolean = key != null && !isGapKey(key) && !isCoveredKey(key) && key != "now" && key != HomeExtras.RESUME

    /** Starts selecting on grid [folder], with [key] marked (when given). A tile being moved goes back first. */
    fun start(folder: String?, key: String?) {
        IconGrid.moving?.let { mv -> if (mv.folder == folder) IconGrid.moving = null }
        val s = GridSelection(folder)
        if (markable(key)) s.keys.add(key!!)
        IconGrid.selecting = s
        Sounds.toggle(true)
        Toasts.show("A marks tiles · Y: what to do with them · B: done")
    }

    /** Marks [key] on grid [folder], or unmarks it; starts selecting there when nothing is. */
    fun toggle(folder: String?, key: String) {
        if (!markable(key)) return
        val s = IconGrid.selecting?.takeIf { it.folder == folder }
        if (s == null) { start(folder, key); return }
        if (key in s.keys) { s.keys.remove(key); Sounds.toggle(false) } else { s.keys.add(key); Sounds.toggle(true) }
    }

    /** Select while moving a tile: it goes back to where it was, marked, and selecting starts. */
    fun fromMove() {
        val mv = IconGrid.moving ?: return
        IconGrid.moving = null
        start(mv.folder, mv.key)
    }

    fun clear() {
        if (IconGrid.selecting == null) return
        IconGrid.selecting = null
        Sounds.back()
    }
}

/** The grid an empty spot or tile belongs to: the home grid ([folder] null) or an open folder, as it shows now. */
internal fun shownFor(folder: String?): GridShown? = if (folder == null) homeShown() else IconGrid.folderShown?.takeIf { IconGrid.openFolder == folder }

/** The focus route of grid [folder]. */
internal fun routeFor(folder: String?): String = if (folder == null) Route.Home.key else IconGrid.folderRoute(folder)

/** A folder's name with the folders above it ("Games › RPGs"). */
internal fun folderPath(f: HomeFolder): String = GridTree.trail(IconGridStore.config, f.id).joinToString(" › ") { it.name }.ifEmpty { f.name }

/** Where something sits, for menu subtitles: "Home grid" or the folder's path. */
internal fun gridName(folder: String?): String = folder?.let { IconGridStore.folder(it) }?.let { folderPath(it) } ?: "Home grid"

/**
 * The folders [keys] could go into from grid [from], as menu rows, deepest last: never the grid they are on, never a
 * folder among them, and for [move] == false not one that already holds every one of them. Folders only where they fit
 * (GridTree.canNest).
 */
internal fun folderChoices(keys: List<String>, from: String?, move: Boolean, onPick: (HomeFolder) -> Unit): List<MenuAction> {
    val c = IconGridStore.config
    return c.folders.filter { f ->
        f.id != from && GridTree.folderKey(f.id) !in keys &&
            keys.any { k -> GridTree.canNest(c, k, f.id) && (move || k !in f.items) }
    }.sortedWith(compareBy({ GridTree.depthOf(c, it.id) }, { folderPath(it).lowercase() })).map { f ->
        MenuAction("f:${f.id}", folderPath(f), Glyph.Folder, folderColor(f.color), value = plural(f.items.count { it.isNotEmpty() }, "item")) { onPick(f) }
    }
}

/** Undo: the steps of grid changes, a toast offering to take each back, and taking it back. */
object GridUndoUi {
    private const val TOAST_MS = Undo.TOAST_MS

    /**
     * Runs [change] as one undoable step named [label] ("Removed Mario"); when it changed anything, a toast says so
     * for 6 s with "Select: Undo" (a tap on it undoes too). [games] / [systems]: what it hides, to bring back.
     */
    fun run(label: String, games: List<String> = emptyList(), systems: List<String> = emptyList(), change: () -> Unit): Boolean {
        val did = IconGridStore.undoable(label, games, systems, change)
        if (did) offer(label)
        return did
    }

    /** The toast after a step: what happened, and Select to undo it. */
    fun offer(label: String) = Toasts.show(label, TOAST_MS, Toasts.Action("Undo") { undo() })

    /** Takes back the last step, if there is one. */
    fun undo() {
        val u = IconGridStore.undo()
        if (u == null) { Sounds.error(); Toasts.show("Nothing to undo"); return }
        for (s in u.systems) HiddenFolders.unhide(s, null)
        IconGrid.selecting = null
        IconGrid.moving = null
        // A folder that is gone now closes; focus finds its way back to the tile it was on (or its neighbour).
        IconGrid.openFolder?.let { if (IconGridStore.folder(it) == null) IconGrid.openFolder = null }
        Nav.focusTick++
        Sounds.undo()
        Toasts.show("Undone: ${u.label.replaceFirstChar { it.lowercase() }}")
    }

    /** "Undo last change" for a menu's More… (or the Start panel), when there is something to undo. */
    fun action(more: Boolean = true, before: () -> Unit = {}): MenuAction? {
        val label = IconGridStore.undoLabel ?: return null
        return MenuAction("undo", "Undo last change", Glyph.Refresh, Palette.Butter, sub = label, more = more) { before(); undo() }
    }
}

/** The selected keys on [s]'s grid that still show, in reading order (the order they keep when moved). */
private fun orderedSelection(s: GridSelection, catalog: GridCatalog, shown: GridShown?): List<String> {
    val keys = s.keys.filter { catalog.item(it) != null }
    val slots = shown?.slots ?: return keys
    val at = slots.withIndex().associate { (i, k) -> k to i }
    return keys.sortedBy { at[it] ?: Int.MAX_VALUE }
}

/** Y (or A on an empty spot) while tiles are marked: what to do with them, here. */
internal fun selectionMenuSpec(t: TileMenuTarget, catalog: GridCatalog, ctx: Context): MenuSpec? {
    val s = IconGrid.selecting?.takeIf { it.folder == t.folder } ?: return null
    val folder = s.folder
    val shown = shownFor(folder)
    val keys = orderedSelection(s, catalog, shown)
    val items = keys.mapNotNull { catalog.item(it) }
    val n = keys.size
    val what = if (n == 1) items.firstOrNull()?.title ?: "1 tile" else plural(n, "tile")
    val route = routeFor(folder)
    val sorted = folder?.let { IconGridStore.folder(it) }?.let { it.sort != FolderSort.MANUAL } == true
    val at = gapIndex(t.key) ?: shown?.slots?.indexOf(t.key) ?: -1
    val done: () -> Unit = { IconGrid.selecting = null; IconGrid.closeMenu() }
    val actions = buildList {
        if (n > 0 && shown != null && at >= 0 && !sorted) add(MenuAction("here", "Move here", Glyph.Move, Palette.Sky, value = plural(n, "tile"), sub = "From this spot on, in the order they're in") {
            GridUndoUi.run("Moved $what") { IconGridStore.moveMany(keys, folder, shown, at) }
            FocusMemory.remember(route, keys.first())
            done()
            Sounds.confirm()
        })
        if (n > 0) {
            add(MenuAction("move", "Move to folder…", Glyph.Folder, Palette.Butter) { IconGrid.folderPage = "move"; IconGrid.choosingFolder = true })
            if (keys.any { GridTree.folderId(it) == null }) add(MenuAction("also", "Also add to folder…", Glyph.Plus, Palette.Butter, sub = "They stay here too") {
                IconGrid.folderPage = "add"; IconGrid.choosingFolder = true
            })
            add(MenuAction("new", "New folder from selection", Glyph.Folder, Palette.Mint) {
                IconGrid.closeMenu()
                TextEntry.ask("Name the folder", "New folder") { name ->
                    val nm = name.trim().ifEmpty { "Folder" }
                    var id = ""
                    GridUndoUi.run("Made the folder $nm") {
                        id = IconGridStore.createFolder(nm, keys, folder, homeShown(), IconGridStore.config.folders.size, move = true, fromShown = shown)
                    }
                    IconGrid.selecting = null
                    val holder = IconGridStore.parentOf(id)?.id
                    FocusMemory.remember(routeFor(holder), GridTree.folderKey(id))
                    Nav.focusTick++
                    Sounds.confirm()
                    items.firstOrNull()?.let { FolderDesign.suggest(ctx, id, it) }
                }
            })
            val removable = keys.filter { GridTree.folderId(it) == null }
            if (removable.isNotEmpty()) add(MenuAction("remove", if (folder == null) "Remove from home" else "Remove from folder", Glyph.Close, Palette.Peach, value = plural(removable.size, "tile")) {
                val widgets = removable.mapNotNull { GridWidgets.idOf(it) }
                val label = if (removable.size == 1) "Removed ${catalog.item(removable[0])?.title ?: "it"}" else "Removed ${plural(removable.size, "tile")}"
                GridUndoUi.run(label) {
                    IconGridStore.removeMany(removable, folder)
                    if (folder == null) widgets.forEach { Widgets.remove(it) }
                }
                done()
                Nav.focusTick++
            })
            val hideable = items.filter { it.game != null || it.kind == TileKind.Folder || it.kind == TileKind.System && it.platformId != null }
            if (hideable.isNotEmpty()) add(MenuAction("hide", "Hide", Glyph.Eye, Palette.Lavender, value = plural(hideable.size, "tile"), sub = "Games, systems and folders") {
                hideMany(hideable)
                done()
            })
        }
        val all = shown?.slots.orEmpty().filter { k -> k.isNotEmpty() && k in shown!!.present && GridSelect.markable(k) && catalog.item(k) != null }
        if (all.size > n) add(MenuAction("all", "Select all", Glyph.Grid, Palette.Sky, value = plural(all.size, "tile")) {
            s.keys.clear(); s.keys.addAll(all); IconGrid.closeMenu(); Sounds.toggle(true)
        })
        add(MenuAction("clear", "Clear selection", Glyph.Back, Palette.Lavender, value = "B") { IconGrid.closeMenu(); GridSelect.clear() })
        GridUndoUi.action { IconGrid.closeMenu() }?.let { add(it) }
    }
    val page = if (IconGrid.choosingFolder && n > 0) {
        val move = IconGrid.folderPage != "add"
        MenuPage(
            if (move) "Move to folder" else "Also add to folder",
            folderChoices(keys, folder, move) { f ->
                val fit = keys.filter { GridTree.canNest(IconGridStore.config, it, f.id) }
                GridUndoUi.run((if (move) "Moved $what into " else "Added $what to ") + f.name) {
                    IconGridStore.putManyInFolder(fit, folder, f.id, move)
                }
                done()
                Nav.focusTick++
                Sounds.confirm()
            },
            onBack = { IconGrid.choosingFolder = false },
        )
    } else null
    val first = items.firstOrNull()
    return MenuSpec(IconGrid.MENU_ROUTE + ":sel:" + (folder ?: ""), if (n == 0) "Nothing marked" else "$n selected", gridName(folder), first?.color ?: Palette.Sky, actions, page) {
        Box(Modifier.fillMaxSize()) {
            if (first != null) LiftedTileArt(first)
            if (n > 0) CountPill(n, Modifier.align(Alignment.BottomEnd).padding(8.dp))
        }
    }
}

/** Hide on marked tiles: games (their own Hide), systems and folders (hidden with everything inside). */
private fun hideMany(items: List<GridItem>) {
    val games = items.mapNotNull { it.game?.key }.filter { !Store.get(it).hidden }
    val systems = items.filter { it.kind == TileKind.System }.mapNotNull { it.platformId }.filter { !HiddenFolders.listed(it, null) }
    val folders = items.filter { it.kind == TileKind.Folder && !it.hidden }.mapNotNull { it.folderId }
    val n = games.size + systems.size + folders.size
    if (n == 0) { Toasts.show("Already hidden"); return }
    GridUndoUi.run(if (n == 1) "Hid ${items.first().title}" else "Hid ${plural(n, "tile")}", games, systems) {
        for (g in games) Store.update(g) { it.copy(hidden = true) }
        for (s in systems) HiddenFolders.hide(s, null)
        for (f in folders) HiddenFolders.setGridFolderHidden(f, true)
    }
    Nav.focusTick++
}

/** A marked tile's badge: a check in a disc with a dark rim and a white halo, so it reads on any art and theme. */
@Composable
internal fun CheckBadge(size: Dp, modifier: Modifier = Modifier) {
    val fill = Palette.highlightFor(Palette.Mint)
    Canvas(modifier.size(size)) {
        val r = this.size.minDimension / 2f
        val c = Offset(r, r)
        drawCircle(Color.White, r, c)
        drawCircle(Color(0xFF14161F), r * 0.86f, c)
        drawCircle(fill, r * 0.74f, c)
        val tick = Path().apply {
            moveTo(r * 0.58f, r * 1.02f)
            lineTo(r * 0.88f, r * 1.32f)
            lineTo(r * 1.44f, r * 0.72f)
        }
        drawPath(tick, Color(0xFF14161F), style = Stroke(r * 0.26f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(tick, Color.White, style = Stroke(r * 0.15f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** How many are marked, on a dark pill with a light rim. */
@Composable
private fun CountPill(n: Int, modifier: Modifier = Modifier) {
    Box(
        modifier.clip(RoundedCornerShape(50)).background(Color.White).padding(1.5.dp).clip(RoundedCornerShape(50))
            .background(Color(0xFF14161F)).padding(horizontal = 9.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) { Text("$n", style = MaterialTheme.typography.labelLarge, color = Color.White, maxLines = 1) }
}

/**
 * While tiles are marked on grid [folder]: the count and what the buttons do, in the grid's caption strip (or the
 * folder's header). Reads the selection itself, so marking a tile redraws only this.
 */
@Composable
internal fun SelectPill(folder: String?, modifier: Modifier = Modifier, compact: Boolean = false) {
    val s = IconGrid.selecting?.takeIf { it.folder == folder }
    AnimatedVisibility(
        s != null, modifier,
        enter = fadeIn(Motion.fade(160)) + scaleIn(spring(Spring.DampingRatioMediumBouncy), initialScale = 0.8f),
        exit = fadeOut(Motion.fade(120)) + scaleOut(targetScale = 0.9f),
    ) {
        val n = rememberLatest(s)?.keys?.size ?: 0
        Row(
            Modifier.clip(RoundedCornerShape(50)).background((if (compact) Palette.CardHigh else Palette.Card).copy(alpha = 0.94f))
                .padding(start = 6.dp, end = 14.dp, top = 4.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            CountPill(n)
            if (!compact) {
                Text("selected", style = MaterialTheme.typography.labelLarge, color = Palette.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                ButtonHint("A", "Mark", Palette.Mint)
            }
            ButtonHint("Y", "Actions", Palette.Butter)
            // In a folder's header, B is already the back button beside the name.
            if (!compact) ButtonHint("B", "Done", Palette.Lavender)
        }
    }
}
