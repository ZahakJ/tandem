package io.github.zahakj.tandem.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.zahakj.tandem.Button
import io.github.zahakj.tandem.Buttons
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlin.math.abs

/*
 * How focus works everywhere in Tandem:
 *
 * 1. Every screen and popup has a route key and lists the keys of its focusable items in order.
 *    [rememberFocusTarget] picks the one that should hold focus: the remembered item if it still exists, otherwise
 *    its nearest surviving neighbour (an item was hidden, unfavorited, rescanned away), otherwise the first.
 * 2. Lazy lists and grids keep that item on screen with [KeepInView] (it scrolls only when the item isn't already
 *    fully visible, so returning to a grid doesn't jump), and remember their scroll position per route
 *    ([rememberRouteListState] / [rememberRouteGridState]).
 * 3. Items attach [takesFocus]: it records focus as it moves and asks for focus when its item is the target.
 *    Only the topmost layer may take focus: the screen when no popup is open, otherwise the newest popup
 *    ([Layers]). Popups trap the D-pad ([trapFocus]); the screen behind ignores navigation keys while one is open.
 */

/** The last highlighted item on each screen or popup, so coming back lands where you were. */
object FocusMemory {
    private val keys = HashMap<String, String>()
    private val orders = HashMap<String, List<String>>()
    /** The last item on each route that isn't [passing], to come back to once a passing item goes away. */
    private val steady = HashMap<String, String>()

    /**
     * Items that only show while a game runs (the icon grid's Resume page, the home list's Now playing card). Tandem
     * lands on them when it comes back, so their neighbour says nothing about where you were: once the game is
     * closed (or exits), focus goes back to the item highlighted before, like the system or folder it was started
     * from, instead of the neighbour (which was always the first tile).
     */
    private val passing = setOf(HomeExtras.RESUME, "now:resume", "now:close", "now:more")

    fun remember(route: String, item: String) {
        keys[route] = item
        if (item !in passing) steady[route] = item
    }
    fun get(route: String): String? = keys[route]

    /** Forget a popup's last item, so it opens on its first row again. */
    fun forget(route: String) { keys.remove(route) }

    /** See the notes at the top of this file. */
    fun resolve(route: String, items: List<String>): String? {
        val previous = orders[route]
        orders[route] = items
        if (items.isEmpty()) return null
        val wanted = keys[route] ?: return items.first()
        if (wanted in items) return wanted
        val present = items.toHashSet()
        if (wanted in passing) steady[route]?.takeIf { it in present }?.let { keys[route] = it; return it }
        val at = previous?.indexOf(wanted) ?: -1
        val neighbour = if (at >= 0 && previous != null) {
            previous.subList(at + 1, previous.size).firstOrNull { it in present }
                ?: previous.subList(0, at).lastOrNull { it in present }
        } else null
        return (neighbour ?: items.first()).also { keys[route] = it }
    }
}

/** The item that should hold focus on [route]; recomputed when the items change or focus is handed back. */
@Composable
fun rememberFocusTarget(route: String, items: List<String>): String? {
    val layer = LocalLayer.current
    val tick = if (layer is KeptLayer) keptTick(layer) else Nav.focusTick
    return remember(route, items, tick) { FocusMemory.resolve(route, items) }
}

/**
 * [Nav.focusTick] for a screen kept composed while hidden (KeptScreen): it stands still while the screen is hidden,
 * so a resume or Home (each bumps the tick) doesn't recompose a hidden screen (Settings would rebuild every row).
 * Showing the screen bumps the tick, and this catches up then.
 */
@Composable
private fun keptTick(layer: KeptLayer): Int {
    val tick = remember(layer) { androidx.compose.runtime.mutableIntStateOf(Nav.focusTick) }
    LaunchedEffect(layer) {
        snapshotFlow { if (layer.dormant) -1 else Nav.focusTick }.collect { if (it >= 0) tick.intValue = it }
    }
    return tick.intValue
}

/** Open popups, oldest first. Only the newest may take focus; with none open, the screen may. */
object Layers {
    private val open = mutableStateListOf<Any>()
    /** Popups that hold the focus right now (read when a key arrives, so plain fields are enough). */
    private val holding = HashSet<Any>()
    val top: Any? get() = open.lastOrNull()
    /** How many popups are open (state: SoundCues whooshes when it goes up or down). */
    val count: Int get() = open.size
    fun push(id: Any) { open.remove(id); open.add(id) }
    fun pop(id: Any) { open.remove(id); holding.remove(id) }
    fun focusIn(id: Any, has: Boolean) { if (has) holding.add(id) else holding.remove(id) }

    /** True when the focus sits inside some popup (which may be drawn inside a screen, like the Systems panel). */
    val focusInPopup: Boolean get() = holding.isNotEmpty()
}

/** The popup an item sits in; null for the screen. */
val LocalLayer = compositionLocalOf<Any?> { null }

/** Registers a popup layer while [visible]; content inside should be wrapped in [ProvideLayer]. */
@Composable
fun rememberLayer(visible: Boolean): Any {
    val id = remember { Any() }
    DisposableEffect(visible) {
        if (visible) Layers.push(id)
        onDispose { Layers.pop(id) }
    }
    return id
}

@Composable
fun ProvideLayer(layer: Any, content: @Composable () -> Unit) = CompositionLocalProvider(LocalLayer provides layer, content = content)

/**
 * The layer of a screen kept composed while hidden (KeptScreen in App.kt). Its items may take focus only while
 * [dormant] is false. A state, not a different layer value, so showing or hiding the screen recomposes nothing.
 */
class KeptLayer {
    var dormant by mutableStateOf(true)
}

/** True when items in [layer] may take focus. Reads state: call it in composition or in a snapshotFlow. */
fun layerActive(layer: Any?): Boolean = when (layer) {
    null -> Layers.top == null && !Nav.overlayOpen
    is KeptLayer -> !layer.dormant && Layers.top == null && !Nav.overlayOpen
    else -> Layers.top == layer
}

/** True when items in the current layer may take focus. */
@Composable
fun layerActive(): Boolean = layerActive(LocalLayer.current)

/**
 * Hooks an item into focus handling: remembers it on [routeKey] when it gets focus, and takes focus when [wanted]
 * (it is the target from [rememberFocusTarget]) and its layer is on top.
 *
 * Whether the layer is on top is watched inside the effect, not read while composing: a popup opening or closing
 * (or a kept screen showing) would otherwise recompose every focusable item on the screen underneath.
 */
@Composable
fun Modifier.takesFocus(routeKey: String, itemKey: String, wanted: Boolean): Modifier {
    val requester = remember { FocusRequester() }
    val focused = remember { booleanArrayOf(false) }
    val layer = LocalLayer.current
    LaunchedEffect(routeKey, wanted, layer) {
        if (!wanted) return@LaunchedEffect
        // Focus handed back (Nav.focusTick: a resume, Home) asks again. Watched here rather than read while composing,
        // so a resume doesn't recompose every focusable item on the screen in its first frame.
        snapshotFlow { Nav.focusTick to layerActive(layer) }.collectLatest { (_, active) ->
            if (!active) return@collectLatest
            // The item may still be animating in or not laid out yet; keep asking briefly until it takes focus.
            // The first try comes right after the next frame (the item is laid out by then), so a screen's highlight
            // shows at once instead of 70 ms late.
            repeat(12) { i ->
                if (i == 0) androidx.compose.runtime.withFrameNanos { } else delay(70)
                if (focused[0]) return@collectLatest
                runCatching { requester.requestFocus() }
            }
        }
    }
    return this.focusRequester(requester).onFocusChanged {
        focused[0] = it.isFocused
        if (it.isFocused) FocusMemory.remember(routeKey, itemKey)
    }
}

/**
 * Older form: takes focus when it was the last highlighted item on [routeKey], or when nothing was remembered and
 * [fallback] is true. Prefer [rememberFocusTarget] + [takesFocus], which also copes with the item disappearing.
 */
@Composable
fun Modifier.remembersFocus(routeKey: String, itemKey: String, fallback: Boolean): Modifier {
    val wanted = FocusMemory.get(routeKey)?.let { it == itemKey } ?: fallback
    return takesFocus(routeKey, itemKey, wanted)
}

/** Scroll positions per route, so a list comes back exactly where it was. */
object ScrollMemory {
    private val positions = HashMap<String, Pair<Int, Int>>()
    fun get(key: String): Pair<Int, Int>? = positions[key]
    fun put(key: String, index: Int, offset: Int) { positions[key] = index to offset }
}

@Composable
fun rememberRouteListState(key: String): LazyListState {
    val state = remember(key) { ScrollMemory.get(key).let { LazyListState(it?.first ?: 0, it?.second ?: 0) } }
    DisposableEffect(state) { onDispose { ScrollMemory.put(key, state.firstVisibleItemIndex, state.firstVisibleItemScrollOffset) } }
    return state
}

@Composable
fun rememberRouteGridState(key: String): LazyGridState {
    val state = remember(key) { ScrollMemory.get(key).let { LazyGridState(it?.first ?: 0, it?.second ?: 0) } }
    DisposableEffect(state) { onDispose { ScrollMemory.put(key, state.firstVisibleItemIndex, state.firstVisibleItemScrollOffset) } }
    return state
}

private fun LazyListState.fullyVisible(index: Int): Boolean {
    val info = layoutInfo
    val item = info.visibleItemsInfo.firstOrNull { it.index == index } ?: return false
    return item.offset >= info.viewportStartOffset && item.offset + item.size <= info.viewportEndOffset
}

private fun LazyGridState.fullyVisible(index: Int): Boolean {
    val info = layoutInfo
    val item = info.visibleItemsInfo.firstOrNull { it.index == index } ?: return false
    return item.offset.y >= info.viewportStartOffset && item.offset.y + item.size.height <= info.viewportEndOffset
}

/**
 * Keeps item [index] (the focus target) on screen: when a screen opens, when focus is handed back, and when the
 * target moves (after sorting, or a played game jumping to the front). Lazy lists only compose what's on screen,
 * so an item scrolled away can't take focus until it is scrolled to.
 */
@Composable
fun KeepInView(state: LazyListState, index: Int, room: Dp = KeepRoom) {
    val roomPx = with(LocalDensity.current) { room.toPx() }
    LaunchedEffect(state, index, Nav.focusTick) {
        if (index < 0) return@LaunchedEffect
        snapshotFlow { state.layoutInfo.totalItemsCount }.first { it > index }
        if (!state.fullyVisible(index)) runCatching {
            state.scrollToItem(index)
            // Back off a little, so the item sits clear of the list's faded top edge.
            if (index > 0 && roomPx > 0f) state.scrollBy(-roomPx)
        }
    }
}

@Composable
fun KeepInView(state: LazyGridState, index: Int, room: Dp = KeepRoom) {
    val roomPx = with(LocalDensity.current) { room.toPx() }
    LaunchedEffect(state, index, Nav.focusTick) {
        if (index < 0) return@LaunchedEffect
        snapshotFlow { state.layoutInfo.totalItemsCount }.first { it > index }
        if (!state.fullyVisible(index)) runCatching {
            state.scrollToItem(index)
            if (index > 0 && roomPx > 0f) state.scrollBy(-roomPx)
        }
    }
}

/** How far [KeepInView] keeps a scrolled-to item from the top edge (past the edge fade, see Edges.kt). */
val KeepRoom = 28.dp

/**
 * Scrolling that keeps [pad] of room around the highlighted item, so a card that grows when highlighted (and its
 * glow) is never cut off at the edge of a list, and a row's heading stays in sight.
 */
@OptIn(ExperimentalFoundationApi::class)
private class PaddedBringIntoView(private val pad: Float) : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val room = minOf(pad, ((containerSize - size) / 2f).coerceAtLeast(0f))
        val lead = offset - room
        val trail = offset + size + room - containerSize
        return when {
            lead < 0f -> lead
            trail > 0f -> minOf(trail, lead)
            else -> 0f
        }
    }
}

/** For a list that scrolls itself in another way (the home rows snap to each row). */
@OptIn(ExperimentalFoundationApi::class)
private object NoBringIntoView : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = 0f
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PaddedScrolling(pad: Dp = 40.dp, content: @Composable () -> Unit) {
    val px = with(LocalDensity.current) { pad.toPx() }
    val spec = remember(px) { PaddedBringIntoView(px) }
    CompositionLocalProvider(LocalBringIntoViewSpec provides spec, content = content)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NoAutoScrolling(content: @Composable () -> Unit) = CompositionLocalProvider(LocalBringIntoViewSpec provides NoBringIntoView, content = content)

/**
 * Keeps D-pad focus inside a popup instead of wandering to the screen underneath. Focus asked for in code (another
 * popup opening on top, or the screen once this one closes) still moves.
 */
@OptIn(ExperimentalComposeUiApi::class)
fun Modifier.trapFocus(): Modifier =
    focusProperties { exit = { d -> if (d == FocusDirection.Enter) FocusRequester.Default else FocusRequester.Cancel } }.focusGroup()

/** D-pad and select keys: the ones the screen behind an open popup must ignore. */
fun KeyEvent.isNavKey(): Boolean = when (key) {
    Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight, Key.DirectionCenter,
    Key.Enter, Key.NumPadEnter, Key.ButtonA, Key.Tab, Key.PageUp, Key.PageDown, Key.MoveHome, Key.MoveEnd -> true
    else -> false
}

/** While [enabled], [handler] sees controller buttons before anything else (see [Buttons.intercept]). */
@Composable
fun InterceptButtons(enabled: Boolean, handler: (Button) -> Boolean) {
    val current by rememberUpdatedState(handler)
    val owner = androidx.compose.ui.platform.LocalContext.current.let { it as? io.github.zahakj.tandem.MainActivity ?: (it as? android.content.ContextWrapper)?.baseContext as? io.github.zahakj.tandem.MainActivity }
    DisposableEffect(enabled) {
        val remove = if (enabled) Buttons.intercept({ current(it) }, owner) else null
        onDispose { remove?.invoke() }
    }
}

/** The last non-null [value], for popups that keep showing their content while they animate away. */
@Composable
fun <T : Any> rememberLatest(value: T?): T? {
    val holder = remember { arrayOfNulls<Any>(1) }
    if (value != null) holder[0] = value
    @Suppress("UNCHECKED_CAST")
    return holder[0] as T?
}

/*
 * Horizontal rows. Left and Right move along a row and stop at its ends: they never jump out to another row, the
 * side panel or the top bar. Up and Down leave the row as usual. With a [RowFocus], coming back into the row (Up
 * or Down from the rows around it) lands on the item last highlighted there instead of whichever is nearest.
 */

/** How many FocusCards hold the focus right now (0 or 1; main thread only). */
object FocusCards {
    var focused = 0

    /**
     * Called on every D-pad press. When no card holds the focus (touch navigation took it away, or the focused card
     * went away), the screen's or popup's remembered item takes it again, so the press always shows a highlight.
     */
    fun rescue() {
        if (focused > 0) return
        focused = 0
        Nav.focusTick++
    }
}

/** Remembers the last highlighted item of one row. Items join with [rowItem]; the row uses [focusRow]. */
class RowFocus {
    private val requesters = HashMap<String, FocusRequester>()
    internal var last: String? = null
    internal fun requester(key: String) = requesters.getOrPut(key) { FocusRequester() }
    internal fun gone(key: String) { requesters.remove(key); if (last == key) last = null }
    internal val lastRequester: FocusRequester? get() = last?.let { requesters[it] }
}

@Composable
fun rememberRowFocus(): RowFocus = remember { RowFocus() }

/** Makes this container a row: Left/Right stop at its ends; entering it returns to [memory]'s last item. */
@OptIn(ExperimentalComposeUiApi::class)
fun Modifier.focusRow(memory: RowFocus? = null): Modifier = focusProperties {
    exit = { d -> if (d == FocusDirection.Left || d == FocusDirection.Right) FocusRequester.Cancel else FocusRequester.Default }
    if (memory != null) enter = { memory.lastRequester ?: FocusRequester.Default }
}.focusGroup()

/** An item of a row with a [RowFocus]: recorded when highlighted, so the row can come back to it. */
@Composable
fun Modifier.rowItem(memory: RowFocus, key: String): Modifier {
    val requester = remember(memory, key) { memory.requester(key) }
    DisposableEffect(memory, key) { onDispose { memory.gone(key) } }
    return focusRequester(requester).onFocusChanged { if (it.isFocused) memory.last = key }
}

/**
 * The area screens are drawn in (below the top bar). Left and Right can't leave it, so the top bar is never
 * reached sideways from a screen.
 */
@OptIn(ExperimentalComposeUiApi::class)
fun Modifier.screenContent(): Modifier = focusProperties {
    exit = { d -> if (d == FocusDirection.Left || d == FocusDirection.Right) FocusRequester.Cancel else FocusRequester.Default }
}.focusGroup()

/**
 * The top bar: Down goes back to the screen's highlighted item (not to whatever sits under the button), and
 * Left/Right/Up stop at its ends.
 *
 * Down is handled as a key, not left to focus search: the bar lies over the screens, so the screens' focus group
 * starts at the very top of the window, under the bar, and is never "below" a button in it. Focus search found
 * nothing at all, the exit below never ran, and Down from Stats or Quick did nothing (only B got you back).
 */
@OptIn(ExperimentalComposeUiApi::class)
fun Modifier.topBarFocus(): Modifier = onKeyEvent { e ->
    if (e.key != Key.DirectionDown) return@onKeyEvent false
    if (e.type == KeyEventType.KeyDown) Nav.focusTick++
    true
}.focusProperties {
    exit = { d ->
        when (d) {
            FocusDirection.Enter -> FocusRequester.Default
            FocusDirection.Down -> { Nav.focusTick++; FocusRequester.Cancel }
            else -> FocusRequester.Cancel
        }
    }
}.focusGroup()

/**
 * The buttons in a popup's header (sort and view, over a folder's games). Items join with [headerControl]; the row
 * that holds them uses [headerFocus].
 *
 * Up from the content lands on the button lined up with the item it leaves, not on whichever button's centre is
 * nearest: focus search goes by centres, so the wide sort button beat the small view button sitting right above
 * the last column. Lined up means by the nearer edge: an item in the right half of the screen goes to the button
 * whose right edge is closest to its own, one in the left half by left edges. Down goes back to the content's
 * highlighted item (the one Up came from), like the top bar.
 */
class HeaderFocus {
    internal class Control {
        val requester = FocusRequester()
        var coords: LayoutCoordinates? = null
    }

    private val controls = HashMap<String, Control>()
    internal fun control(key: String) = controls.getOrPut(key) { Control() }
    internal fun gone(key: String) { controls.remove(key) }

    /** The button Up from the highlighted item should land on (the item's place comes from [liftOrigin]). */
    internal fun above(): FocusRequester {
        val from = ActionMenu.origin?.takeIf { it.isAttached } ?: return FocusRequester.Default
        val src = from.boundsInWindow()
        val right = src.center.x > from.findRootCoordinates().size.width / 2f
        val best = controls.values.mapNotNull { c -> c.coords?.takeIf { it.isAttached }?.let { c to it.boundsInWindow() } }
            .minByOrNull { (_, r) -> if (right) abs(r.right - src.right) else abs(r.left - src.left) }
        return best?.first?.requester ?: FocusRequester.Default
    }
}

@Composable
fun rememberHeaderFocus(): HeaderFocus = remember { HeaderFocus() }

/** The row of header buttons: Up from the content enters at the button above, Down goes back to the content. */
@OptIn(ExperimentalComposeUiApi::class)
fun Modifier.headerFocus(header: HeaderFocus): Modifier = onKeyEvent { e ->
    // A key, as in the top bar: focus search would pick the item nearest the button, not the one Up came from.
    if (e.key != Key.DirectionDown) return@onKeyEvent false
    if (e.type == KeyEventType.KeyDown) Nav.focusTick++
    true
}.focusProperties {
    enter = { d -> if (d == FocusDirection.Up) header.above() else FocusRequester.Default }
    exit = { d -> if (d == FocusDirection.Enter) FocusRequester.Default else FocusRequester.Cancel }
}.focusGroup()

/** A button in a [headerFocus] row. Its place is a field write per layout; nothing recomposes. */
@Composable
fun Modifier.headerControl(header: HeaderFocus, key: String): Modifier {
    val control = remember(header, key) { header.control(key) }
    DisposableEffect(header, key) { onDispose { header.gone(key) } }
    return focusRequester(control.requester).onGloballyPositioned { control.coords = it }
}
