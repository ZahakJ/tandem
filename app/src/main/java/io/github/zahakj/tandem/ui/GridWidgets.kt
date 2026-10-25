package io.github.zahakj.tandem.ui

import android.content.Context
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.zahakj.tandem.data.GridShown
import io.github.zahakj.tandem.data.GridSlots
import io.github.zahakj.tandem.data.GridSpan
import io.github.zahakj.tandem.data.IconGridStore
import io.github.zahakj.tandem.data.Widget
import io.github.zahakj.tandem.data.WidgetPlace
import io.github.zahakj.tandem.data.WidgetSize
import io.github.zahakj.tandem.data.WidgetStore

/*
 * Widgets on the icon grid. A widget is a grid item (`widget:<id>`) that covers 1×1, 2×1, 1×2 or 2×2 cells: its key
 * sits in its first (top left) cell and the cells it covers stay free in the slots, taken by it (GridSlots extents;
 * the size is IconGridConfig.spans). The widget itself is kept in WidgetStore with the place Grid, so its options and
 * actions work as they do anywhere else. This file: sizes, adding, resizing and removing, and the cell that draws one.
 */
object GridWidgets {
    private const val PREFIX = "widget:"

    fun key(id: String): String = PREFIX + id

    fun idOf(key: String): String? = if (key.startsWith(PREFIX)) key.substring(PREFIX.length) else null

    /** How a widget [span] cells big draws itself (the kinds lay themselves out by size). */
    fun sizeFor(span: GridSpan): WidgetSize = when {
        span.w >= 2 && span.h >= 2 -> WidgetSize.Wide
        span.w >= 2 -> WidgetSize.Medium
        span.h >= 2 -> WidgetSize.Tall
        else -> WidgetSize.Small
    }

    fun spanFor(size: WidgetSize): GridSpan = when (size) {
        WidgetSize.Small -> GridSpan(1, 1)
        WidgetSize.Medium -> GridSpan(2, 1)
        WidgetSize.Tall -> GridSpan(1, 2)
        WidgetSize.Wide -> GridSpan(2, 2)
    }

    /** The grid sizes [kind] supports, smallest first. */
    fun spans(kind: WidgetKind): List<GridSpan> = GridSpan.ALL.filter { sizeFor(it) in kind.sizes }.ifEmpty { listOf(GridSpan.ONE) }

    /** [key]'s size on the grid now. */
    fun span(key: String): GridSpan = IconGridStore.config.spans[key] ?: GridSpan.ONE

    /** New widgets go in grid cells (the default), or in the widget row (Settings › Widgets on the grid). */
    val inCells: Boolean get() = WidgetStore.config.gridPlace != "row"

    private fun ext(span: GridSpan) = GridSlots.extent(span, IconGrid.vertical)

    fun widget(key: String): Widget? = idOf(key)?.let { Widgets.byId(it) }

    /**
     * Adds a [kind] widget in slot [at] of the home grid as [shown], at its usual size when there is room there, else
     * the biggest size that fits. With widgets set to show as a row, it joins the row instead. Returns its key on the
     * grid.
     */
    fun add(kind: WidgetKind, shown: GridShown, at: Int): String? {
        if (!kind.available()) {
            Toasts.show(kind.needs ?: "Not available yet")
            Sounds.error()
            return null
        }
        if (!inCells) {
            Widgets.add(kind, WidgetArea.Top)
            Sounds.confirm()
            Toasts.show("Added ${kind.name} to the widget row")
            return null
        }
        val id = WidgetStore.newId(kind.id)
        val key = key(id)
        val usual = spanFor(kind.defaultSize)
        val options = listOf(usual) + spans(kind).filter { it != usual }.sortedByDescending { it.w * it.h }
        val span = options.firstOrNull { s ->
            GridSlots.fits(shown.slots, shown.lanes, shown.ext + (key to ext(s)), key, at, shown.present, shown.page)
        } ?: GridSpan.ONE
        WidgetStore.updateWidgets { it + Widget(id, kind.id, sizeFor(span), WidgetPlace.Grid) }
        IconGridStore.addWidgetAt(key, span, ext(span), shown, at)
        Sounds.confirm()
        Toasts.show(if (span == usual) "Added ${kind.name}" else "Added ${kind.name} at ${span.label}: Y › Resize when there's room")
        return key
    }

    /**
     * The next size of [kinds] after [now] that [fits], going round; null when no other size fits. [fits] gives the
     * slots with that size, or null.
     */
    private fun next(kinds: List<GridSpan>, now: GridSpan, fits: (GridSpan) -> List<String>?): Pair<GridSpan, List<String>>? {
        val start = kinds.indexOf(now)
        for (n in 1..kinds.size) {
            val s = kinds[(start + n).mod(kinds.size)]
            if (s == now) continue
            fits(s)?.let { return s to it }
        }
        return null
    }

    /** Resize on the grid (Y › Resize): the next size that fits where it sits, saved at once. */
    fun resize(key: String) {
        val w = widget(key) ?: return
        val kind = Widgets.kind(w) ?: return
        val shown = homeShown()
        val now = span(key)
        val found = next(spans(kind), now) { s -> GridSlots.resize(shown.slots, shown.lanes, shown.ext, key, ext(s), shown.page) }
        if (found == null) {
            Sounds.error()
            Toasts.show(if (spans(kind).size <= 1) "${kind.name} comes in one size" else "No room for another size here")
            return
        }
        GridUndoUi.run("Resized ${kind.name} to ${found.first.label}") {
            IconGridStore.setSpan(key, found.first, found.second, shown.lanes)
            Widgets.setSize(w.id, sizeFor(found.first))
        }
        Sounds.toggle()
    }

    /** Y while moving a widget: the next size that fits where it is now; saved when it is dropped. */
    fun resizeMoving(mv: MovingTile) {
        val w = widget(mv.key) ?: return
        val kind = Widgets.kind(w) ?: return
        val shown = IconGrid.home ?: return
        val now = mv.span ?: span(mv.key)
        val slots = mv.order.toList()
        val others = shown.ext - mv.key
        val found = next(spans(kind), now) { s -> GridSlots.resize(slots, shown.lanes, others + (mv.key to ext(now)), mv.key, ext(s), shown.page) }
        if (found == null) {
            Sounds.error()
            Toasts.show(if (spans(kind).size <= 1) "${kind.name} comes in one size" else "No room for another size here")
            return
        }
        mv.span = found.first
        mv.order.clear()
        mv.order.addAll(found.second)
        Sounds.toggle()
        Toasts.show("${kind.name}: ${found.first.label}")
    }

    /** Takes the widget off the grid and forgets it. */
    fun remove(key: String) {
        val name = widget(key)?.let { Widgets.kind(it)?.name } ?: "widget"
        // Undo brings it back with its size and options (IconGridStore keeps the grid's widgets with each step).
        GridUndoUi.run("Removed $name") {
            IconGridStore.remove(key, default = false)
            idOf(key)?.let { Widgets.remove(it) }
        }
    }
}

/** Y on a widget on the grid: its options, Move, Resize, its own actions, and Remove under More. */
internal fun widgetMenuSpec(item: GridItem, slots: List<String>, ctx: Context): MenuSpec? {
    val w = item.widgetId?.let { Widgets.byId(it) } ?: return null
    val kind = Widgets.kind(w) ?: return null
    val key = item.key
    val span = GridWidgets.span(key)
    val actions = buildList {
        add(MenuAction("options", "Widget options…", Glyph.Pencil, kind.accent, sub = kind.blurb) {
            IconGrid.closeMenu()
            WidgetUi.openMenu(w.id, WidgetArea.Top, inEditor = false)
        })
        add(MenuAction("move", "Move", Glyph.Move, Palette.Sky, value = "◀▶") { IconGrid.closeMenu(); startMove(key, null, slots) })
        if (GridWidgets.spans(kind).size > 1) add(MenuAction("resize", "Resize", Glyph.Corners, Palette.Butter, value = span.label) { GridWidgets.resize(key) })
        kind.actions(w).forEachIndexed { i, a ->
            add(MenuAction("act:$i", a.label, Glyph.Sparkle, a.color, value = a.value) {
                a.run()
                if (a.closes) IconGrid.closeMenu()
            })
        }
        add(MenuAction("remove", "Remove widget", Glyph.Trash, Palette.Peach, more = true) {
            IconGrid.closeMenu()
            GridWidgets.remove(key)
        })
        add(MenuAction("select", "Select", Glyph.Highlight, Palette.Sky, more = true, sub = "Mark several, then act on them all") {
            IconGrid.closeMenu(); GridSelect.start(null, key)
        })
        add(MenuAction("tidy", "Tidy up", Glyph.Sparkle, Palette.Mint, more = true) { IconGrid.closeMenu(); tidyHome() })
        GridUndoUi.action { IconGrid.closeMenu() }?.let { add(it) }
        add(MenuAction("search", "Search", Glyph.Search, Palette.Sky, more = true) { IconGrid.menu = null; Nav.search = true })
        add(MenuAction("swap", "Swap screens", Glyph.Swap, Palette.Lavender, value = if (ScreenRoles.xSwaps()) "X" else null, more = true) {
            IconGrid.closeMenu(); ScreenRoles.toggle(ctx)
        })
    }
    return MenuSpec(IconGrid.MENU_ROUTE + ":" + key, kind.name, "Widget · ${span.label}", kind.accent, actions, aspect = span.w.toFloat() / span.h) {
        WidgetView(w, GridWidgets.sizeFor(span), Modifier.fillMaxSize(), WidgetArea.Top, interactive = false, grid = true)
    }
}

/**
 * Lays its content out [bigW] by [bigH] (moved by [dx], [dy]) but takes only [w] by [h] itself: a widget draws over
 * the cells it covers while the lazy grid sees one cell, so rows and columns never change size.
 */
internal fun Modifier.spill(w: Dp, h: Dp, bigW: Dp, bigH: Dp, dx: Dp = 0.dp, dy: Dp = 0.dp): Modifier = layout { m, _ ->
    val p = m.measure(Constraints.fixed(bigW.roundToPx().coerceAtLeast(0), bigH.roundToPx().coerceAtLeast(0)))
    layout(w.roundToPx(), h.roundToPx()) { p.place(dx.roundToPx(), dy.roundToPx()) }
}

/**
 * A widget on the grid, drawn [span] cells big from its first cell. [cell] is one cell (a tile and its label room),
 * [gap] the space between cells, [label] the label room left free under the bottom row. [focus] carries the focus
 * hooks; a moving widget wobbles like a tile.
 */
@Composable
internal fun GridWidgetCell(widget: Widget, span: GridSpan, tile: Dp, cell: Dp, gap: Dp, label: Dp, focus: Modifier, moving: Boolean, onFocus: () -> Unit, drag: Modifier = Modifier) {
    val w = tile * span.w + gap * (span.w - 1)
    val h = cell * span.h + gap * (span.h - 1) - label
    // Being moved, it rises a little off the grid and glides from cell to cell.
    val raise = androidx.compose.animation.core.animateFloatAsState(if (moving) 1f else 0f, Motion.move(), label = "widget-raise")
    Box(
        Modifier.spill(tile, cell, w, h).then(drag).graphicsLayer {
            val r = raise.value
            scaleX = 1f + 0.04f * r; scaleY = 1f + 0.04f * r
            translationY = -6f * density * r
        },
    ) {
        WidgetView(widget, GridWidgets.sizeFor(span), focus.size(w, h), WidgetArea.Top, grid = true, onFocused = onFocus)
    }
}

/**
 * The second column (or row) of a two-cell-long widget whose first cell has scrolled out of the lazy grid: it draws
 * the widget from here, shifted back by one cell ([dx], [dy]), so its visible half never disappears at the edge.
 */
@Composable
internal fun GridWidgetStandIn(widget: Widget, span: GridSpan, tile: Dp, cell: Dp, gap: Dp, label: Dp, dx: Dp, dy: Dp) {
    val w = tile * span.w + gap * (span.w - 1)
    val h = cell * span.h + gap * (span.h - 1) - label
    Box(Modifier.spill(tile, cell, w, h, -dx, -dy)) {
        WidgetView(widget, GridWidgets.sizeFor(span), Modifier.size(w, h), WidgetArea.Top, interactive = false, grid = true)
    }
}
