package io.github.zahakj.tandem.ui

import android.os.SystemClock
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.zahakj.tandem.data.FolderSort
import io.github.zahakj.tandem.data.GridShown
import io.github.zahakj.tandem.data.GridSlots
import io.github.zahakj.tandem.data.GridSpan
import io.github.zahakj.tandem.data.GridTree
import io.github.zahakj.tandem.data.IconGridStore
import io.github.zahakj.tandem.data.Store
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/*
 * Touch drag and drop on the icon grid and in folder popups, like the 3DS HOME menu. Hold a tile (or a widget) and
 * move: it lifts under the finger and follows it. Let go on an empty spot to put it there, on a tile to swap them,
 * on a folder (or hover over one until it opens, then keep going inside) to put it in. Holding over a tile offers a
 * new folder of both; dragging out of a folder popup brings it back to the grid around it. Every drop is undoable.
 *
 * The finger is followed by the screen's drag host ([gridDragHost], an ancestor of the grid and the folder popup),
 * so the drag goes on when the tile it started from scrolls away or its folder closes. Each grid on screen is a
 * [DropZone]: the finger is mapped to its slots through the lazy grid's own layout. The lifted copy is one layer
 * moved in the layout phase; the grid only recomposes when the slot under the finger changes.
 */

/** A grid things can be dropped on: the home grid ([folder] null) or an open folder. Updated as it composes. */
internal class DropZone(val folder: String?, val state: LazyGridState) {
    var coords: LayoutCoordinates? = null
    /** Lazy index of slot 0 (the Resume page comes first while a game runs). */
    var off = 0
    var vertical = false
    var lanes = 1
    var keys: List<String> = emptyList()
    var owners: IntArray = IntArray(0)
    var tilePx = 0f
    var gapPx = 0f
    /** Paged grids turn a page when the finger rests at an edge. */
    var turnPage: ((Int) -> Unit)? = null
}

/** One drag: [key] from grid [from], under pointer [pointer], held at [grab] within it. */
internal class GridDragging(
    val key: String, val from: String?, val item: GridItem?, val span: GridSpan?,
    val pointer: PointerId, val grab: Offset, val tilePx: Float, val gapPx: Float,
) {
    /** Where the finger is, in the drag host. Read only while laying out the lifted copy. */
    val at = mutableStateOf(Offset.Zero)
    var zone by mutableStateOf(from)
    var slot by mutableIntStateOf(-1)
    /** What is under the lifted copy: a tile's key, an empty spot's ("gap:3"), or null. */
    var target by mutableStateOf<String?>(null)
    /** Held over a tile long enough: dropping makes a folder of both. */
    var armed by mutableStateOf(false)
    /** It can go where it is now (a widget only where it fits, a folder only where it may nest). */
    var fits by mutableStateOf(true)
    var hoverSince = 0L
    var edgeSince = 0L
    var outsideSince = 0L

    val widget: Boolean get() = GridWidgets.idOf(key) != null
    val isFolder: Boolean get() = GridTree.folderId(key) != null
}

object GridDrag {
    private const val OPEN_MS = 550L
    private const val ARM_MS = 650L
    private const val LEAVE_MS = 450L
    private const val PAGE_MS = 650L

    internal var drag by mutableStateOf<GridDragging?>(null)
        private set
    internal val zones = HashMap<String, DropZone>()
    internal var host: LayoutCoordinates? = null

    private fun zoneKey(folder: String?) = folder ?: ""

    internal fun register(z: DropZone) { zones[zoneKey(z.folder)] = z }

    internal fun unregister(z: DropZone) { if (zones[zoneKey(z.folder)] === z) zones.remove(zoneKey(z.folder)) }

    /** Nothing else is going on that a drag would fight with. */
    fun canStart(): Boolean = drag == null && IconGrid.moving == null && IconGrid.selecting == null && IconGrid.menu == null &&
        !IconGrid.adding && IconGrid.pickingGames == null && FolderDesign.key == null && TextEntry.request == null

    /** 0: not under the dragged copy; 1: under it (a drop goes here); 2: a folder about to open, or a new folder. */
    fun hover(folder: String?, key: String): Int {
        val d = drag ?: return 0
        if (d.zone != folder || d.target != key || key == d.key) return 0
        if (!d.fits) return 0
        return if (d.armed || GridTree.folderId(key) != null && GridTree.canNest(IconGridStore.config, d.key, GridTree.folderId(key)!!)) 2 else 1
    }

    /** True for the tile being dragged: its own cell shows only a faint ghost. */
    fun lifted(key: String): Boolean = drag?.key == key

    internal fun start(key: String, from: String?, item: GridItem?, span: GridSpan?, pointer: PointerId, coords: LayoutCoordinates, local: Offset): Boolean {
        if (!canStart()) return false
        val h = host ?: return false
        if (!coords.isAttached || !h.isAttached) return false
        val z = zones[zoneKey(from)]
        val tile = z?.tilePx?.takeIf { it > 0f } ?: coords.size.width.toFloat()
        val d = GridDragging(key, from, item, span, pointer, local, tile, z?.gapPx ?: 0f)
        d.at.value = h.localPositionOf(coords, local)
        d.hoverSince = SystemClock.uptimeMillis()
        // A widget's own long press may have opened its options meanwhile: the drag wins.
        if (d.widget) WidgetUi.close()
        drag = d
        Sounds.select()
        return true
    }

    /** Where the lifted copy's first cell is: the point mapped to a slot. */
    private fun probe(d: GridDragging): Offset = d.at.value - d.grab + Offset(d.tilePx / 2f, d.tilePx / 2f)

    private fun local(z: DropZone, p: Offset): Offset? {
        val c = z.coords?.takeIf { it.isAttached } ?: return null
        val h = host?.takeIf { it.isAttached } ?: return null
        return c.localPositionOf(h, p)
    }

    /** The slot of [z] under [p] (host coordinates), the nearest one within a cell, or -1. */
    private fun hit(z: DropZone, p: Offset): Int {
        val q = local(z, p) ?: return -1
        val info = z.state.layoutInfo
        var best = -1
        var bestD = Float.MAX_VALUE
        for (it in info.visibleItemsInfo) {
            val i = it.index - z.off
            if (i < 0 || i >= z.keys.size) continue
            val r = Rect(it.offset.x.toFloat(), it.offset.y.toFloat(), (it.offset.x + it.size.width).toFloat(), (it.offset.y + it.size.height).toFloat())
            if (r.contains(q)) return i
            val dd = (r.center - q).getDistanceSquared()
            if (dd < bestD) { bestD = dd; best = i }
        }
        val reach = (z.tilePx + z.gapPx) * 1.2f
        return if (bestD <= reach * reach) best else -1
    }

    private fun inside(z: DropZone, p: Offset): Boolean {
        val q = local(z, p) ?: return false
        val s = z.coords?.size ?: return false
        return q.x >= 0f && q.y >= 0f && q.x <= s.width && q.y <= s.height
    }

    /** Each frame of a drag: which grid and slot it is over, folders opening, the grid scrolling at its edges. */
    internal fun tick() {
        val d = drag ?: return
        val now = SystemClock.uptimeMillis()
        val p = d.at.value
        // A widget's own long press opens its options; on the grid a hold drags it instead.
        if (d.widget && WidgetUi.menuFor != null) WidgetUi.close()
        val open = IconGrid.openFolder
        val fz = open?.let { zones[it] }
        if (open != null && fz != null && fz.coords?.isAttached == true) {
            // Out of the folder's grid for a moment: back up a level (the grid around it).
            if (inside(fz, p)) d.outsideSince = 0L
            else if (d.outsideSince == 0L) d.outsideSince = now
            else if (now - d.outsideSince > LEAVE_MS) {
                d.outsideSince = 0L
                val up = IconGridStore.parentOf(open)?.id
                IconGrid.openFolder = up
                Sounds.back()
                return
            }
        }
        val z = (if (open != null) fz else null) ?: if (open == null) zones[""] else null
        if (z == null) { d.target = null; return }
        scrollAtEdge(z, d, p, now)
        val raw = hit(z, probe(d))
        val slot = if (raw < 0) -1 else z.owners.getOrElse(raw) { -1 }.let { if (it >= 0) it else raw }
        val key = z.keys.getOrNull(slot)?.takeIf { !isCoveredKey(it) }
        if (key != d.target || z.folder != d.zone) {
            d.zone = z.folder
            d.slot = slot
            d.target = key
            d.armed = false
            d.hoverSince = now
            d.fits = key != null && fits(d, z, slot, key)
            return
        }
        if (key == null || key == d.key || !d.fits) return
        val held = now - d.hoverSince
        val fid = GridTree.folderId(key)
        when {
            fid != null && GridTree.canNest(IconGridStore.config, d.key, fid) -> if (held > OPEN_MS) {
                // The folder opens; the drag goes on inside it.
                IconGrid.openedFrom = null
                FocusMemory.forget(IconGrid.folderRoute(fid))
                IconGrid.openFolder = fid
                d.hoverSince = now
                Sounds.page()
            }
            !isGapKey(key) && !d.armed && held > ARM_MS && !d.widget && !d.isFolder && GridWidgets.idOf(key) == null &&
                GridTree.canHoldFolder(IconGridStore.config, z.folder) -> {
                d.armed = true
                Sounds.toggle(true)
            }
        }
    }

    private fun fits(d: GridDragging, z: DropZone, slot: Int, key: String): Boolean {
        val c = IconGridStore.config
        if (z.folder != null) {
            if (d.widget) return false
            if (!GridTree.canNest(c, d.key, z.folder)) return false
            if (IconGridStore.folder(z.folder)?.sort != FolderSort.MANUAL && z.folder == d.from && !isGapKey(key)) return true
        }
        if (d.widget) {
            if (key != d.key && !isGapKey(key)) return false
            val shown = shownFor(null) ?: return false
            val ext = shown.ext + (d.key to GridSlots.extent(d.span ?: GridSpan.ONE, z.vertical))
            return GridSlots.fits(shown.slots, shown.lanes, ext, d.key, slot, shown.present, shown.page)
        }
        return true
    }

    /** Near an edge along the scroll the grid scrolls (faster nearer the edge); a paged grid turns its page. */
    private fun scrollAtEdge(z: DropZone, d: GridDragging, p: Offset, now: Long) {
        val q = local(z, p) ?: return
        val size = z.coords?.size ?: return
        val len = (if (z.vertical) size.height else size.width).toFloat()
        val pos = if (z.vertical) q.y else q.x
        val edge = (z.tilePx * 0.6f).coerceAtLeast(40f)
        val dir = when { pos < edge -> -1; pos > len - edge -> 1; else -> 0 }
        if (dir == 0) { d.edgeSince = 0L; return }
        val turn = z.turnPage
        if (turn != null) {
            if (d.edgeSince == 0L) d.edgeSince = now
            else if (now - d.edgeSince > PAGE_MS) { d.edgeSince = now; turn(dir); Sounds.page() }
            return
        }
        val depth = if (dir < 0) (edge - pos) / edge else (pos - (len - edge)) / edge
        z.state.dispatchRawDelta(dir * (6f + 22f * depth.coerceIn(0f, 1f)))
    }

    /** Let go: the drop, as one undoable step. */
    internal fun drop() {
        val d = drag ?: return
        drag = null
        val key = d.target
        val zone = d.zone
        val shown = shownFor(zone)
        val name = d.item?.title ?: "it"
        val default = d.item?.default == true
        if (key == null || shown == null || !d.fits || key == d.key && zone == d.from) { Sounds.back(); Nav.focusTick++; return }
        val slot = d.slot
        val fid = GridTree.folderId(key)
        val route = routeFor(zone)
        val into = fid != null && GridTree.canNest(IconGridStore.config, d.key, fid)
        when {
            d.armed && !isGapKey(key) -> {
                val other = key
                TextEntry.ask("Name the folder", "New folder") { typed ->
                    val n = typed.trim().ifEmpty { "Folder" }
                    var made = ""
                    GridUndoUi.run("Made the folder $n") {
                        if (zone != d.from) shownFor(zone)?.let { s -> IconGridStore.place(d.key, d.from, zone, s, s.firstFree(slot), default) }
                        made = IconGridStore.createFolder(n, listOf(other, d.key), zone, homeShown(), IconGridStore.config.folders.size, move = true, fromShown = shownFor(zone))
                    }
                    d.item?.let { FolderDesign.suggest(Ctx.app ?: return@let, made, it) }
                    FocusMemory.remember(routeFor(IconGridStore.parentOf(made)?.id), GridTree.folderKey(made))
                    Nav.focusTick++
                    Sounds.confirm()
                }
                return
            }
            into && fid != null ->
                GridUndoUi.run("Moved $name into ${IconGridStore.folder(fid)?.name ?: "the folder"}") { IconGridStore.moveIntoFolder(d.key, d.from, fid) }
            zone == d.from -> {
                if (zone != null && IconGridStore.folder(zone)?.sort != FolderSort.MANUAL) { Sounds.back(); return }
                GridUndoUi.run("Moved $name") {
                    val at = shown.slots.indexOf(d.key)
                    when {
                        d.widget -> {
                            val ext = shown.ext + (d.key to GridSlots.extent(d.span ?: GridSpan.ONE, IconGrid.vertical))
                            IconGridStore.setSlots(GridSlots.settle(GridSlots.putAt(shown.slots, d.key, slot), shown.lanes, ext, shown.page), shown.lanes)
                        }
                        at < 0 -> IconGridStore.place(d.key, d.from, zone, shown, shown.firstFree(slot), default)
                        zone == null -> IconGridStore.setSlots(GridSlots.move(shown.slots, at, slot), shown.lanes)
                        else -> IconGridStore.setFolderItems(zone, GridSlots.move(shown.slots, at, slot), shown.lanes)
                    }
                }
            }
            else -> {
                // Into another grid: the spot, or the first free one after the tile there.
                val at = if (isGapKey(key) || key !in shown.present) slot else shown.firstFree(slot)
                GridUndoUi.run("Moved $name") { IconGridStore.place(d.key, d.from, zone, shown, at, default) }
            }
        }
        // Put in a folder: the folder keeps the highlight; anything else: the tile where it landed.
        FocusMemory.remember(route, if (into) key else d.key)
        Nav.focusTick++
        Sounds.confirm()
    }

    /**
     * A tap while a tile is moved with the D-pad: on the tile itself it drops it there; on an empty spot of the same
     * grid it puts it there; anywhere else it cancels the move (the tile goes back). Never leaves Move stuck.
     */
    internal fun tapWhileMoving(p: Offset) {
        val mv = IconGrid.moving ?: return
        val z = zones[zoneKey(mv.folder)]
        val raw = if (z != null) hit(z, p).takeIf { z.folder == null || IconGrid.openFolder == z.folder } ?: -1 else -1
        val slot = if (raw < 0 || z == null) -1 else z.owners.getOrElse(raw) { -1 }.let { if (it >= 0) it else raw }
        val key = z?.keys?.getOrNull(slot)
        when {
            key == mv.key -> dropMoveNow()
            key != null && isGapKey(key) && GridWidgets.idOf(mv.key) == null -> {
                val order = GridSlots.putAt(mv.order.toList(), mv.key, slot)
                mv.order.clear()
                mv.order.addAll(order)
                dropMoveNow()
            }
            else -> cancelMoveNow()
        }
    }

    internal fun cancel() {
        if (drag == null) return
        drag = null
        Sounds.back()
        Nav.focusTick++
    }
}

/** The app's context, for the designer's suggestion after a drop (set by the drag host). */
private object Ctx { var app: android.content.Context? = null }

/** Registers a grid as a [DropZone] while it is composed. */
@Composable
internal fun rememberDropZone(folder: String?, state: LazyGridState): DropZone {
    val z = remember(folder, state) { DropZone(folder, state) }
    androidx.compose.runtime.DisposableEffect(z) {
        GridDrag.register(z)
        onDispose { GridDrag.unregister(z) }
    }
    return z
}

/**
 * The drag host: put on a box around the grid and its folder popup (and nothing that scrolls it away). It follows the
 * dragging finger wherever it goes, ticks the drag each frame and draws the lifted copy ([DragGhost]).
 */
@Composable
internal fun Modifier.gridDragHost(): Modifier {
    val d = GridDrag.drag
    val ctx = androidx.compose.ui.platform.LocalContext.current
    Ctx.app = ctx.applicationContext
    LaunchedEffect(d) {
        if (d == null) return@LaunchedEffect
        while (GridDrag.drag === d) withFrameNanos { GridDrag.tick() }
    }
    return this.onGloballyPositioned { GridDrag.host = it }.pointerInput(Unit) {
        // A tap while a tile is being moved with the D-pad (see [GridDrag.tapWhileMoving]).
        var tapId: PointerId? = null
        var tapAt = Offset.Zero
        var tapTime = 0L
        awaitPointerEventScope {
            while (true) {
                val e = awaitPointerEvent(PointerEventPass.Initial)
                if (GridDrag.drag == null && IconGrid.moving?.carried == false && IconGrid.menu == null) {
                    for (c in e.changes) {
                        when {
                            c.pressed && !c.previousPressed -> { tapId = c.id; tapAt = c.position; tapTime = c.uptimeMillis }
                            c.id != tapId -> Unit
                            (c.position - tapAt).getDistance() > viewConfiguration.touchSlop -> tapId = null
                            !c.pressed -> {
                                tapId = null
                                if (c.uptimeMillis - tapTime < 500) { c.consume(); GridDrag.tapWhileMoving(c.position) }
                            }
                        }
                    }
                    continue
                }
                val drag = GridDrag.drag ?: continue
                val c = e.changes.firstOrNull { it.id == drag.pointer }
                if (c == null) { if (e.changes.none { it.pressed }) GridDrag.cancel(); continue }
                drag.at.value = c.position
                c.consume()
                if (!c.pressed) GridDrag.drop()
            }
        }
    }
}

/** The lifted copy under the finger: a little bigger, with a shadow (just faded in with reduce motion). */
@Composable
internal fun DragGhost() {
    val d = GridDrag.drag ?: return
    val density = LocalDensity.current
    val still = Store.settings.reduceMotion || Motion.style == AnimationStyle.Off
    val span = d.span?.takeIf { d.widget } ?: GridSpan.ONE
    val wPx = d.tilePx * span.w + d.gapPx * (span.w - 1)
    val hPx = d.tilePx * span.h + d.gapPx * (span.h - 1)
    val shown = remember(d) { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(d) { shown.animateTo(1f, if (still) androidx.compose.animation.core.tween(120) else Motion.focus()) }
    val ring = if (d.armed) 1f else 0f
    val armedGlow by animateFloatAsState(ring, Motion.soft(), label = "drag-armed")
    val corner = with(density) { (d.tilePx * Store.settings.look.gridRoundness.coerceIn(0f, 0.5f)).toDp() }
    Box(
        Modifier.offset { (d.at.value - d.grab).let { IntOffset(it.x.roundToInt(), it.y.roundToInt()) } }
            .size(with(density) { wPx.toDp() }, with(density) { hPx.toDp() })
            .graphicsLayer {
                val v = shown.value
                alpha = if (still) v else 0.95f
                val s = if (still) 1f else 1f + 0.1f * v + 0.06f * armedGlow
                scaleX = s; scaleY = s
                shadowElevation = if (still) 0f else 20f * this.density * v
                shape = RoundedCornerShape(corner)
                clip = true
            },
    ) {
        val item = d.item
        when {
            d.widget || item == null -> Box(
                Modifier.fillMaxSize().background((item?.color ?: Palette.Sky).copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center,
            ) { Text(item?.title ?: "", style = MaterialTheme.typography.titleSmall, color = Palette.Text, maxLines = 1) }
            else -> TileArtView(item.art, with(density) { d.tilePx.toDp() })
        }
        if (armedGlow > 0.01f) Box(
            Modifier.align(Alignment.Center).graphicsLayer { alpha = armedGlow }.clip(RoundedCornerShape(50))
                .background(Palette.NightDeep.copy(alpha = 0.8f)).padding(horizontal = 8.dp, vertical = 3.dp),
        ) { Text("+ Folder", style = MaterialTheme.typography.labelMedium, color = Palette.Text, maxLines = 1) }
    }
}

/**
 * Touch on a grid tile ([watchOnly] false): as [touchEntry] (a first tap highlights, a tap on the highlighted tile
 * opens it), except that a long press picks the tile up for [GridDrag] instead of opening its menu (Y does that).
 * With [watchOnly] (a widget, which has its own taps) only the hold is watched, a little sooner than the widget's own
 * long press, which the drag then sets aside.
 */
@Composable
internal fun Modifier.gridTouch(key: String, from: String?, item: GridItem?, span: GridSpan? = null, watchOnly: Boolean = false): Modifier {
    val requester = remember { FocusRequester() }
    val focused = remember { booleanArrayOf(false) }
    val coords = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val haptic = LocalHapticFeedback.current
    val latest by rememberUpdatedState(item)
    val latestSpan by rememberUpdatedState(span)
    val base = if (watchOnly) this else this.focusRequester(requester).onFocusChanged { focused[0] = it.isFocused }
    return base.onPlaced { coords[0] = it }.pointerInput(key, from, watchOnly) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val wasFocused = focused[0]
            val holdMs = if (watchOnly) viewConfiguration.longPressTimeoutMillis * 3 / 4 else viewConfiguration.longPressTimeoutMillis
            // false: moved off (a scroll) or gone; a change: lifted; null: held.
            val outcome = withTimeoutOrNull(holdMs) {
                while (true) {
                    val c = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull false
                    if (!c.pressed) return@withTimeoutOrNull c
                    if (c.isConsumed || (c.position - down.position).getDistance() > viewConfiguration.touchSlop) return@withTimeoutOrNull false
                }
                @Suppress("UNREACHABLE_CODE") false
            }
            when (outcome) {
                null -> {
                    // Held: it comes up under the finger at once, like the 3DS HOME menu (no menu: that is Y). The
                    // drag host follows the finger from here; let go without moving and it goes back.
                    val cc = coords[0]
                    if (cc != null && GridDrag.start(key, from, latest, latestSpan, down.id, cc, down.position)) {
                        Touch.buzz(haptic)
                    } else if (!watchOnly) {
                        // Nothing to drag now (a tile is moving, tiles are marked): the hold does nothing.
                        do {
                            val e = awaitPointerEvent(PointerEventPass.Initial)
                            e.changes.forEach { it.consume() }
                        } while (e.changes.any { it.pressed })
                    }
                }
                is PointerInputChange -> if (!watchOnly && !wasFocused && IconGrid.moving == null) {
                    // The first tap only highlights.
                    outcome.consume()
                    runCatching { requester.requestFocus() }
                }
                else -> Unit
            }
        }
    }
}
