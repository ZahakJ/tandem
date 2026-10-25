package io.github.zahakj.tandem.ui

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import io.github.zahakj.tandem.Button
import io.github.zahakj.tandem.Buttons
import io.github.zahakj.tandem.Collections
import io.github.zahakj.tandem.Game
import io.github.zahakj.tandem.Libraries
import io.github.zahakj.tandem.Privacy
import io.github.zahakj.tandem.Selected
import io.github.zahakj.tandem.Selection
import io.github.zahakj.tandem.StorageInfo
import io.github.zahakj.tandem.data.UpNextEntry
import io.github.zahakj.tandem.data.Widget
import io.github.zahakj.tandem.data.WidgetPlace
import io.github.zahakj.tandem.data.WidgetSize
import io.github.zahakj.tandem.data.WidgetStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/*
 * Widgets: small live cards on the home (top) screen's widget area and on the bottom screen while nothing is
 * highlighted. This file is the framework (model helpers, layout, the focusable card, the host); the widgets
 * themselves are in WidgetKinds.kt and the editor in WidgetEditor.kt.
 *
 * For other layouts (a grid home, for example): `Widgets.list(WidgetArea.Top)` is the user's widgets in order,
 * `Widgets.footprint(size, area)` their size in cells, and `WidgetView(widget, size)` draws one, filling whatever
 * size its modifier gives it.
 */

/** The two places widgets live. */
enum class WidgetArea { Top, Bottom }

/** A widget setting: the user cycles through [choices] (value to label). */
class WidgetOption(
    val key: String,
    val label: String,
    val choices: List<Pair<String, String>>,
    val default: String,
    /** Only shown for sizes where it makes a difference. */
    val showFor: (WidgetSize) -> Boolean = { true },
)

fun toggleOption(key: String, label: String, default: Boolean, showFor: (WidgetSize) -> Boolean = { true }) =
    WidgetOption(key, label, listOf("on" to "On", "off" to "Off"), if (default) "on" else "off", showFor)

/** An extra row in a widget's menu ("Measure again", "Reroll"). */
/** [closes]: close the menu after; otherwise [refocus] when the action removes rows (focus starts again at the top). */
class WidgetAction(val label: String, val value: String? = null, val color: Color = Palette.Sky, val closes: Boolean = true, val refocus: Boolean = false, val run: () -> Unit)

/** A kind of widget: what it is called, the sizes it supports, its settings and how it draws. */
class WidgetKind(
    val id: String,
    val name: String,
    val blurb: String,
    val icon: String,
    val accent: Color,
    val sizes: List<WidgetSize>,
    val defaultSize: WidgetSize,
    val options: List<WidgetOption> = emptyList(),
    /** False hides it (RetroAchievements without an account); [needs] says why in the gallery. */
    val available: () -> Boolean = { true },
    val needs: String? = null,
    val actions: (Widget) -> List<WidgetAction> = { emptyList() },
    val content: @Composable (WidgetScope, Modifier) -> Unit,
)

/** What a widget needs to draw itself. */
class WidgetScope(
    val widget: Widget,
    val kind: WidgetKind,
    val size: WidgetSize,
    val area: WidgetArea,
    val interactive: Boolean,
    val onFocused: () -> Unit,
    /** On the icon grid, where Wide is a 2 x 2 card. */
    val grid: Boolean = false,
) {
    fun opt(key: String): String = widget.options[key] ?: kind.options.firstOrNull { it.key == key }?.default.orEmpty()
    fun on(key: String): Boolean = opt(key) == "on"
    /** Two cells high (tall, or wide on the bottom screen or the icon grid). */
    val high: Boolean get() = size == WidgetSize.Tall || ((area == WidgetArea.Bottom || grid) && size == WidgetSize.Wide)
    val small: Boolean get() = size == WidgetSize.Small
}

/** A widget at its place in a packed grid. [size] is what it is drawn as (a tall widget in a one-row area is small). */
data class Placed(val widget: Widget, val size: WidgetSize, val col: Int, val row: Int, val w: Int, val h: Int)

val TOP_CELL_W = 136.dp
val TOP_CELL_H = 108.dp
val TOP_GAP = 14.dp

fun span(n: Int, cell: Dp, gap: Dp): Dp = if (n <= 0) 0.dp else cell * n + gap * (n - 1)

fun plural(n: Int, one: String, many: String) = "$n ${if (n == 1) one else many}"

/** Popups, focus and move mode for widgets. */
object WidgetUi {
    /** The widget that has focus, for Y. */
    var focused by mutableStateOf<String?>(null)
    var focusedArea = WidgetArea.Top
    var focusedInEditor = false

    var menuFor by mutableStateOf<String?>(null)
        private set
    var menuArea = WidgetArea.Top
        private set
    var menuInEditor = false
        private set
    /** Opens the "add a widget" gallery for this area. */
    var gallery by mutableStateOf<WidgetArea?>(null)
        private set
    /** A fresh focus key per popup, so each opens on its first row. */
    var popupGen by mutableIntStateOf(0)
        private set

    /** The widget being moved with the D-pad, and where. */
    var moving by mutableStateOf<String?>(null)
    var movingArea = WidgetArea.Top

    /** Editor tab: 0 = top screen, 1 = bottom screen. */
    var editorTab by mutableIntStateOf(0)

    /** Bumped to measure storage again right away. */
    var rescan by mutableIntStateOf(0)
    var rescanHandled = 0

    val popupOpen: Boolean get() = menuFor != null || gallery != null

    private fun blocked() = TextEntry.request != null

    /** Where the menu's widget was, so focus can land on a neighbour if it goes away. */
    private var menuIndex = 0

    /** The open menu is for a widget on the icon grid. */
    var menuOnGrid = false
        private set

    fun openMenu(id: String, area: WidgetArea, inEditor: Boolean) {
        if (blocked()) return
        Panels.close()
        moving = null
        menuOnGrid = Widgets.byId(id)?.place == WidgetPlace.Grid
        menuFor = id; menuArea = area; menuInEditor = inEditor; gallery = null
        menuIndex = Widgets.list(area).indexOfFirst { it.id == id }.coerceAtLeast(0)
        popupGen++
    }

    /** A fresh focus key for the open popup, when the row that had focus is gone. */
    fun refocus() { popupGen++ }

    fun openGallery(area: WidgetArea) {
        if (blocked()) return
        moving = null
        menuFor = null; gallery = area
        popupGen++
    }

    fun close() {
        val id = menuFor
        // A widget on the icon grid keeps the grid's own focus (and goes back to its spot's neighbour by itself).
        if (id != null && !menuOnGrid) {
            // The widget was removed or sent to the other screen: land on its neighbour instead of nowhere.
            val list = Widgets.list(menuArea)
            if (list.none { it.id == id }) {
                val near = (list.getOrNull(menuIndex) ?: list.lastOrNull())?.id
                if (menuInEditor) {
                    FocusMemory.remember("widgets:${if (menuArea == WidgetArea.Top) 0 else 1}", near?.let { "w:$it" } ?: "addtile")
                } else {
                    val fallback = near?.let { "w:$it" } ?: io.github.zahakj.tandem.Libraries.all?.firstOrNull()?.let { "s:${it.platform.id}" }
                    if (fallback != null) FocusMemory.remember(Route.Home.key, fallback)
                }
            }
        }
        menuFor = null; gallery = null
        Nav.focusTick++
    }

    fun startMoving(id: String, area: WidgetArea) {
        close()
        moving = id; movingArea = area
        Toasts.show("Move it with the D-pad, then press A")
    }
}

/** The user's widgets and the changes the editor makes. */
object Widgets {
    fun ensure(ctx: Context) = WidgetStore.init(ctx)

    fun kind(w: Widget): WidgetKind? = WidgetKinds.byId(w.type)

    fun isOn(area: WidgetArea, w: Widget): Boolean = when (area) {
        WidgetArea.Top -> w.place == WidgetPlace.Home || w.place == WidgetPlace.Both
        WidgetArea.Bottom -> w.place == WidgetPlace.Bottom || w.place == WidgetPlace.Both
    }

    /** The widgets shown in [area], in order. Unknown or unavailable kinds are left out. */
    fun list(area: WidgetArea): List<Widget> = WidgetStore.widgets.filter { isOn(area, it) && kind(it)?.available?.invoke() == true }

    fun byId(id: String): Widget? = WidgetStore.widgets.firstOrNull { it.id == id }

    /** Whether the home screen shows its widget area. */
    fun homeVisible(): Boolean = WidgetStore.config.homeEnabled && list(WidgetArea.Top).isNotEmpty()

    /** Rows in the home widget area. */
    fun homeRows(list: List<Widget> = list(WidgetArea.Top)): Int = when (val r = WidgetStore.config.homeRows) {
        1, 2 -> r
        else -> if (list.any { it.size == WidgetSize.Tall }) 2 else 1
    }

    /** Width and height in cells. The bottom screen has two columns, so a wide widget there is a big 2 x 2 card. */
    fun footprint(size: WidgetSize, area: WidgetArea): Pair<Int, Int> = when (area) {
        WidgetArea.Top -> when (size) {
            WidgetSize.Small -> 1 to 1
            WidgetSize.Medium -> 2 to 1
            WidgetSize.Wide -> 3 to 1
            WidgetSize.Tall -> 1 to 2
        }
        WidgetArea.Bottom -> when (size) {
            WidgetSize.Small -> 1 to 1
            WidgetSize.Medium -> 2 to 1
            WidgetSize.Wide -> 2 to 2
            WidgetSize.Tall -> 1 to 2
        }
    }

    /**
     * Lays widgets out in order, each in the first free spot. The top area has [rows] rows and grows sideways
     * (widget columns); the bottom screen has two columns and grows downwards.
     */
    fun pack(items: List<Widget>, area: WidgetArea, rows: Int = 2): List<Placed> {
        val taken = HashSet<Long>()
        fun free(c: Int, r: Int, w: Int, h: Int): Boolean {
            for (x in c until c + w) for (y in r until r + h) if (x.toLong() * 10_000 + y in taken) return false
            return true
        }
        val out = ArrayList<Placed>()
        for (wd in items) {
            var size = wd.size
            if (area == WidgetArea.Top && rows < 2 && size == WidgetSize.Tall) size = WidgetSize.Small
            val (w, h) = footprint(size, area)
            var at: Pair<Int, Int>? = null
            var i = 0
            while (at == null) {
                if (area == WidgetArea.Top) {
                    for (r in 0..(rows - h)) if (free(i, r, w, h)) { at = i to r; break }
                } else {
                    for (c in 0..(2 - w)) if (free(c, i, w, h)) { at = c to i; break }
                }
                i++
            }
            val (c, r) = at
            for (x in c until c + w) for (y in r until r + h) taken += x.toLong() * 10_000 + y
            out += Placed(wd, size, c, r, w, h)
        }
        return out
    }

    fun add(kind: WidgetKind, area: WidgetArea): Widget {
        val place = if (area == WidgetArea.Top) WidgetPlace.Home else WidgetPlace.Bottom
        val w = Widget(WidgetStore.newId(kind.id), kind.id, kind.defaultSize, place)
        WidgetStore.updateWidgets { it + w }
        return w
    }

    fun remove(id: String) {
        if (WidgetUi.moving == id) WidgetUi.moving = null
        WidgetStore.updateWidgets { list -> list.filter { it.id != id } }
    }

    /** Swaps the widget with its neighbour in [area] (other widgets keep their places). */
    fun move(area: WidgetArea, id: String, by: Int) {
        WidgetStore.updateWidgets { list ->
            val shown = list.filter { isOn(area, it) && kind(it)?.available?.invoke() == true }
            val i = shown.indexOfFirst { it.id == id }
            val j = i + by
            if (i < 0 || j !in shown.indices) return@updateWidgets list
            val a = list.indexOf(shown[i])
            val b = list.indexOf(shown[j])
            list.toMutableList().apply { this[a] = shown[j]; this[b] = shown[i] }
        }
    }

    fun setOption(id: String, key: String, value: String) = WidgetStore.updateWidget(id) { it.copy(options = it.options + (key to value)) }
    fun setSize(id: String, size: WidgetSize) = WidgetStore.updateWidget(id) { it.copy(size = size) }
    fun setPlace(id: String, place: WidgetPlace) = WidgetStore.updateWidget(id) { it.copy(place = place) }
}

/** The Up next list: games (or notes) to play next. */
object UpNext {
    private const val MAX = 40

    fun entries(): List<UpNextEntry> = WidgetStore.config.upNext

    /** Entries that may be shown: hidden games only while they're revealed. */
    fun shown(): List<Pair<UpNextEntry, Game?>> {
        val byKey = Libraries.games.associateBy { it.key }
        return entries().mapNotNull { e ->
            val g = e.key?.let(byKey::get)
            if (g != null && !Privacy.canShow(g)) null else e to g
        }
    }

    fun contains(g: Game): Boolean = entries().any { it.key == g.key }

    fun toggle(g: Game) {
        val e = entries().firstOrNull { it.key == g.key }
        if (e != null) {
            removeWithUndo(e, "Removed from Up next")
        } else {
            add(UpNextEntry(key = g.key, added = System.currentTimeMillis()))
            Toasts.show("Added to Up next ✓")
        }
    }

    private fun add(e: UpNextEntry) = WidgetStore.update { c -> c.copy(upNext = (c.upNext + e).takeLast(MAX)) }

    fun remove(e: UpNextEntry) = WidgetStore.update { c -> c.copy(upNext = c.upNext - e) }

    /** Takes [e] off the list and says [label] with Undo, which puts it back in its place. */
    fun removeWithUndo(e: UpNextEntry, label: String) {
        val at = entries().indexOf(e)
        if (at < 0) return
        remove(e)
        Undo.offer(label) {
            WidgetStore.update { c ->
                if (e in c.upNext) c else c.copy(upNext = c.upNext.toMutableList().apply { add(at.coerceAtMost(size), e) }.takeLast(MAX))
            }
        }
    }

    /** Takes out the games played since they were added. */
    fun clearPlayed(): Int {
        val byKey = Libraries.games.associateBy { it.key }
        val played = entries().filter { e -> e.key?.let(byKey::get)?.let { it.meta.lastPlayed > e.added } == true }
        if (played.isNotEmpty()) WidgetStore.update { c -> c.copy(upNext = c.upNext - played.toSet()) }
        return played.size
    }

    fun played(e: UpNextEntry, g: Game?): Boolean = g != null && g.meta.lastPlayed > e.added

    /** Asks for a game name (or any note) with the on-screen keyboard. A name that matches one game adds that game. */
    fun ask() {
        TextEntry.ask("Add to Up next: a game name or a note") { text ->
            val t = text.trim()
            if (t.isEmpty()) return@ask
            val pool = Collections.visibleGames()
            val q = t.lowercase()
            val match = pool.firstOrNull { it.title.lowercase() == q }
                ?: pool.filter { it.title.lowercase().contains(q) }.takeIf { it.size == 1 }?.first()
            if (match != null) {
                if (contains(match)) Toasts.show("${match.title} is already on the list")
                else { add(UpNextEntry(key = match.key, added = System.currentTimeMillis())); Toasts.show("Added ${match.title} ✓") }
            } else {
                add(UpNextEntry(note = t.take(60), added = System.currentTimeMillis()))
                Toasts.show("Added a note to Up next ✓")
            }
        }
    }

    fun editNote(e: UpNextEntry) {
        TextEntry.ask("Edit note (empty removes it)", e.note.orEmpty()) { text ->
            val t = text.trim()
            WidgetStore.update { c -> c.copy(upNext = if (t.isEmpty()) c.upNext - e else c.upNext.map { if (it == e) it.copy(note = t.take(60)) else it }) }
        }
    }
}

/** The current time, updated every [periodMs] (on the boundary) only while Tandem is in front. */
@Composable
fun rememberNow(periodMs: Long): Long {
    val owner = LocalLifecycleOwner.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(owner, periodMs) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                now = System.currentTimeMillis()
                delay(periodMs - now % periodMs + 5)
            }
        }
    }
    return now
}

/** Re-reads [read] (off the main thread) every [periodMs] while Tandem is in front; null until the first read. */
@Composable
fun <T> rememberPolled(periodMs: Long, vararg keys: Any?, read: () -> T): T? {
    val owner = LocalLifecycleOwner.current
    var value by remember(*keys) { mutableStateOf<T?>(null) }
    val reader by rememberUpdatedState(read)
    LaunchedEffect(owner, periodMs, *keys) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                value = withContext(Dispatchers.IO) { runCatching { reader() }.getOrNull() }
                delay(periodMs)
            }
        }
    }
    return value
}

/** The soft tinted card every widget sits on. */
fun Modifier.widgetSurface(accent: Color, shape: androidx.compose.ui.graphics.Shape = Palette.cardShape): Modifier =
    clip(shape).background(Palette.Card).background(Brush.linearGradient(listOf(accent.copy(alpha = 0.20f), Color.Transparent, accent.copy(alpha = 0.06f))))

/**
 * The focusable card a widget is built on (the theme's [FocusCard]). With [cursorCount] > 1 the D-pad moves a
 * highlight inside it first (left/right, or up/down when [vertical]) and leaves at the ends; A runs [onClick] with
 * the highlighted index. Not interactive (editor and gallery previews): just the card.
 */
@Composable
fun WidgetCard(
    scope: WidgetScope,
    modifier: Modifier = Modifier,
    accent: Color = scope.kind.accent,
    cursorCount: Int = 0,
    vertical: Boolean = false,
    onClick: (Int) -> Unit = {},
    content: @Composable BoxScope.(focused: Boolean, cursor: Int) -> Unit,
) {
    if (!scope.interactive) {
        Box(modifier.widgetSurface(accent)) { content(false, -1) }
        return
    }
    val id = scope.widget.id
    FocusFrame(
        modifier,
        accent = accent,
        cursorCount = cursorCount,
        vertical = vertical,
        onFocus = {
            // The bottom screen never has controller focus; only the top screen tracks it (for Y).
            if (scope.area == WidgetArea.Top) {
                WidgetUi.focused = id; WidgetUi.focusedArea = scope.area; WidgetUi.focusedInEditor = false
                Selection.current = Selected.Home
            }
            scope.onFocused()
        },
        onBlur = { if (scope.area == WidgetArea.Top && WidgetUi.focused == id) WidgetUi.focused = null },
        // Touch: a long press opens the widget's options, on either screen (the bottom one has no Y).
        onLongPress = { WidgetUi.openMenu(id, scope.area, inEditor = false) },
        onClick = onClick,
        content = content,
    )
}

@Composable
fun FocusFrame(
    modifier: Modifier,
    accent: Color,
    drawSurface: Boolean = true,
    cursorCount: Int = 0,
    vertical: Boolean = false,
    onFocus: () -> Unit,
    onBlur: () -> Unit,
    onLongPress: (() -> Unit)? = null,
    onClick: (Int) -> Unit,
    content: @Composable BoxScope.(focused: Boolean, cursor: Int) -> Unit,
) {
    var cursorState by remember { mutableIntStateOf(0) }
    val cursor = if (cursorCount <= 0) 0 else cursorState.coerceIn(0, cursorCount - 1)
    val blur by rememberUpdatedState(onBlur)
    DisposableEffect(Unit) { onDispose { blur() } }
    // The theme's own card, so widgets get the same highlight style, growth limits and sounds as everything else.
    FocusCard(
        modifier
            .onFocusChanged { if (!it.isFocused) onBlur() }
            .onPreviewKeyEvent { e ->
                if (cursorCount < 2) return@onPreviewKeyEvent false
                val d = when (e.key) {
                    Key.DirectionLeft -> if (vertical) return@onPreviewKeyEvent false else -1
                    Key.DirectionRight -> if (vertical) return@onPreviewKeyEvent false else 1
                    Key.DirectionUp -> if (vertical) -1 else return@onPreviewKeyEvent false
                    Key.DirectionDown -> if (vertical) 1 else return@onPreviewKeyEvent false
                    else -> return@onPreviewKeyEvent false
                }
                // Let focus leave at the ends.
                if (cursor + d !in 0 until cursorCount) return@onPreviewKeyEvent false
                if (e.type == KeyEventType.KeyDown) { cursorState = cursor + d; Sounds.move() }
                true
            },
        glow = accent,
        onFocus = onFocus,
        onLongPress = onLongPress,
        onClick = { onClick(cursor) },
    ) { focused ->
        Box(if (drawSurface) Modifier.fillMaxSize().widgetSurface(accent) else Modifier.fillMaxSize()) { content(focused, cursor) }
    }
}

/** A tap target inside a widget (one game of a strip, say): highlights [index] and runs [onTap]. */
fun Modifier.widgetTap(scope: WidgetScope, onTap: () -> Unit): Modifier =
    if (!scope.interactive) this else pointerInput(onTap) { detectTapGestures { onTap() } }

/** Move mode: while [id] is being moved, the D-pad swaps it with its neighbours, and it wiggles. A or B ends it. */
@Composable
fun Modifier.movable(id: String, area: WidgetArea): Modifier {
    val moving = WidgetUi.moving == id
    val wiggle = if (moving && !io.github.zahakj.tandem.data.Store.settings.reduceMotion) {
        val t = rememberInfiniteTransition(label = "wiggle")
        t.animateFloat(-1.2f, 1.2f, infiniteRepeatable(tween(260, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "w").value
    } else 0f
    return this
        .graphicsLayer { rotationZ = wiggle }
        .onPreviewKeyEvent { e ->
            if (WidgetUi.moving != id) return@onPreviewKeyEvent false
            val d = when (e.key) {
                Key.DirectionLeft, Key.DirectionUp -> -1
                Key.DirectionRight, Key.DirectionDown -> 1
                Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.ButtonA -> 0
                else -> return@onPreviewKeyEvent false
            }
            if (d == 0) {
                if (e.type == KeyEventType.KeyUp) { WidgetUi.moving = null; Toasts.show("Moved ✓") }
            } else if (e.type == KeyEventType.KeyDown) {
                Widgets.move(area, id, d)
            }
            true
        }
}

/** Places [placed] widgets on a grid of [cellW] x [cellH] cells; positions spring when the order changes. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PackedGrid(placed: List<Placed>, cellW: Dp, cellH: Dp, gap: Dp, modifier: Modifier = Modifier, item: @Composable (Placed, Modifier) -> Unit) {
    val cols = placed.maxOfOrNull { it.col + it.w } ?: 0
    val rows = placed.maxOfOrNull { it.row + it.h } ?: 0
    Box(modifier.size(span(cols, cellW, gap), span(rows, cellH, gap))) {
        for (p in placed) key(p.widget.id) {
            val x by animateDpAsState((cellW + gap) * p.col, spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow), label = "x")
            val y by animateDpAsState((cellH + gap) * p.row, spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow), label = "y")
            val reveal = remember { BringIntoViewRequester() }
            if (WidgetUi.moving == p.widget.id) {
                LaunchedEffect(p.col, p.row) { delay(120); runCatching { reveal.bringIntoView() } }
            }
            item(
                p,
                Modifier.offset { IntOffset(x.roundToPx(), y.roundToPx()) }
                    .bringIntoViewRequester(reveal)
                    .size(span(p.w, cellW, gap), span(p.h, cellH, gap)),
            )
        }
    }
}

/** The widget area on the home screen. Draws nothing when it's off or empty. [inset]: side padding (0 inside a padded grid). */
@Composable
fun HomeWidgetsRow(inset: Dp = 22.dp) {
    Widgets.ensure(LocalContext.current)
    val list = Widgets.list(WidgetArea.Top)
    if (!WidgetStore.config.homeEnabled || list.isEmpty()) return
    val placed = Widgets.pack(list, WidgetArea.Top, Widgets.homeRows(list))
    val route = Route.Home.key
    Column {
        SectionTitle("Widgets", Palette.Butter, Modifier.padding(start = inset))
        val scroll = rememberScrollState()
        // Widgets scrolled sideways fade out at the edge instead of being cut; the padding is room for the highlight.
        Box(Modifier.fillMaxWidth().bleed(FocusRoomSide).fadingEdges(scroll, horizontal = true).focusRow().horizontalScroll(scroll).padding(horizontal = inset + FocusRoomSide, vertical = FocusRoom)) {
            PackedGrid(placed, TOP_CELL_W, TOP_CELL_H, TOP_GAP) { p, m ->
                val key = "w:${p.widget.id}"
                WidgetView(
                    p.widget, p.size, m.movable(p.widget.id, WidgetArea.Top).remembersFocus(route, key, false), WidgetArea.Top,
                    onFocused = { FocusMemory.remember(route, key) },
                )
            }
        }
    }
}

/** Draws one widget, filling the size [modifier] gives it. */
@Composable
fun WidgetView(
    widget: Widget,
    size: WidgetSize = widget.size,
    modifier: Modifier = Modifier,
    area: WidgetArea = WidgetArea.Top,
    interactive: Boolean = true,
    /** On the icon grid (a 2 x 2 Wide card). */
    grid: Boolean = false,
    onFocused: () -> Unit = {},
) {
    val kind = WidgetKinds.byId(widget.type) ?: return
    kind.content(WidgetScope(widget, kind, size, area, interactive, onFocused, grid), modifier)
}

/** Cell size on a bottom-screen-shaped area [width] wide. */
fun bottomCell(width: Dp, pad: Dp, gap: Dp): Pair<Dp, Dp> {
    val w = (width - pad * 2 - gap) / 2
    return w to w * 0.8f
}

/**
 * The bottom screen when nothing is highlighted: the user's bottom widgets, or [fallback] (the greeting) when there
 * are none. Touch only; the bottom screen never takes controller focus.
 */
@Composable
fun BottomWidgets(fallback: @Composable () -> Unit) {
    Widgets.ensure(LocalContext.current)
    val list = Widgets.list(WidgetArea.Bottom)
    if (list.isEmpty()) {
        fallback()
        return
    }
    // Below the now-playing banner (it takes its own room off the top), never pushed down past the bottom edge.
    val topPad = 16.dp
    BoxWithConstraints(Modifier.fillMaxSize().clearOfNowPlaying()) {
        val pad = 16.dp
        val gap = 12.dp
        val placed = Widgets.pack(list, WidgetArea.Bottom)
        val rows = placed.maxOf { it.row + it.h }
        // Cells come from the width, which on the landscape screen (swapped) would make a 2 x 2 card taller than
        // the screen: they shrink to fit the height too, and only scroll when that would make them tiny.
        val (wideW, wideH) = bottomCell(maxWidth, pad, gap)
        val fitH = (maxHeight - topPad - pad - gap * (rows - 1)) / rows
        val ch = if (fitH < wideH && fitH >= 100.dp) fitH else wideH
        val cw = wideW * (ch / wideH)
        val height = span(rows, ch, gap)
        val grid = @Composable {
            PackedGrid(placed, cw, ch, gap) { p, m -> WidgetView(p.widget, p.size, m, WidgetArea.Bottom) }
        }
        if (height + topPad + pad <= maxHeight + 0.5.dp) {
            Box(Modifier.fillMaxSize().padding(top = topPad, bottom = pad), contentAlignment = Alignment.Center) { grid() }
        } else {
            val scroll = rememberScrollState()
            Column(Modifier.fillMaxSize().fadingEdges(scroll).verticalScroll(scroll).padding(horizontal = pad), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(topPad))
                grid()
                Spacer(Modifier.height(pad))
            }
        }
    }
}

/** Button handling, move mode, data refresh and the widget popups. Composed once, with the other popups. */
@Composable
fun WidgetHost() {
    val ctx = LocalContext.current
    val activity = ctx as ComponentActivity
    Widgets.ensure(ctx)

    // Storage: volumes on every return, folder sizes when stale (or asked), only while Tandem is in front.
    val hasStorage = WidgetStore.widgets.any { it.type == "storage" }
    val force = WidgetUi.rescan
    LaunchedEffect(activity, hasStorage, force) {
        if (!hasStorage) return@LaunchedEffect
        activity.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            withContext(Dispatchers.IO) { StorageInfo.load(ctx); StorageInfo.readVolumes(ctx) }
            if (StorageInfo.stale() || WidgetUi.rescanHandled != force) {
                delay(1500) // let the screen settle first
                val done = withContext(Dispatchers.IO) { StorageInfo.scan(ctx) { isActive } }
                if (done) WidgetUi.rescanHandled = force
                withContext(Dispatchers.IO) { StorageInfo.readVolumes(ctx) }
            }
        }
    }

    // While a widget popup is open it owns the buttons, so Y closing it can't also open a menu underneath.
    InterceptButtons(WidgetUi.popupOpen && TextEntry.request == null && !GameEditor.isOpen) { b ->
        if (b == Button.Y) WidgetUi.close()
        true
    }
    LaunchedEffect(Unit) {
        Buttons.events.collect { b ->
            if (TextEntry.request != null || GameEditor.isOpen || WidgetUi.popupOpen) return@collect
            if (Panels.switcher || Panels.quick || Panels.apps || Panels.appMenu != null || Panels.homeExplainer) return@collect
            if (Nav.menuFor != null || Nav.search || Nav.groupMenu || Nav.openPanels > 0 || LaunchFx.busy) return@collect
            val onWidgets = Nav.route == Route.Widgets
            when {
                b == Button.Y && WidgetUi.moving == null && (Nav.route == Route.Home || onWidgets) ->
                    WidgetUi.focused?.let { WidgetUi.openMenu(it, WidgetUi.focusedArea, WidgetUi.focusedInEditor) }
                onWidgets && (b == Button.L1 || b == Button.R1) -> { WidgetUi.moving = null; WidgetUi.editorTab = 1 - WidgetUi.editorTab }
            }
        }
    }
    LaunchedEffect(Nav.route) { WidgetUi.moving = null }
    BackHandler(enabled = WidgetUi.moving != null && !WidgetUi.popupOpen) { WidgetUi.moving = null; Toasts.show("Moved ✓") }

    WidgetMenu()
    WidgetGallery()
}

/** Settings: the widget editor and the quick switches. */
@Composable
fun widgetsSettings(): SettingSection {
    Widgets.ensure(LocalContext.current)
    val c = WidgetStore.config
    val top = Widgets.list(WidgetArea.Top).size
    val bottom = Widgets.list(WidgetArea.Bottom).size
    val upNext = UpNext.entries().size
    return SettingSection(
        "Widgets",
        listOf(
            SettingRow(
                "Customize widgets", plural(WidgetStore.widgets.size, "widget", "widgets"), Palette.Butter, Glyph.Grid,
                "Add, move, resize and remove widgets: clock, battery, play time, storage, Up next and more.",
                opens = true, keywords = "widget editor add clock battery storage",
            ) {
                WidgetUi.editorTab = 0; Nav.go(Route.Widgets)
            },
            SettingRow(
                "Widgets on the home screen", if (!c.homeEnabled) "Off" else if (top == 0) "None yet" else plural(top, "widget", "widgets"), Palette.Butter, Glyph.Layers,
                "Show your widgets at the top of the home screen.", toggle = c.homeEnabled,
            ) {
                WidgetStore.update { it.copy(homeEnabled = !it.homeEnabled) }
            },
            run {
                val step = { WidgetStore.update { it.copy(gridPlace = if (it.gridPlace == "row") "cells" else "row") } }
                SettingRow(
                    "Widgets on the grid", if (c.gridPlace == "row") "As a row" else "In cells", Palette.Butter, Glyph.Grid,
                    "Where Add here › Widget… on the icon grid puts a new widget: in a spot of its own, 1 to 2 cells each way, " +
                        "or in the widget row. Widgets already placed stay where they are.",
                    onLeft = step, onRight = step, keywords = "widget grid cells row icon grid", onClick = step,
                )
            },
            SettingRow(
                "Bottom screen when idle", if (bottom == 0) "Greeting" else plural(bottom, "widget", "widgets"), Palette.Butter, Glyph.Screens,
                "What the bottom screen shows when nothing is highlighted: Pip's greeting, or widgets you choose.",
                opens = true, keywords = "second screen idle greeting",
            ) {
                WidgetUi.editorTab = 1; Nav.go(Route.Widgets)
            },
            SettingRow(
                "Add to Up next", if (upNext == 0) "Empty" else plural(upNext, "entry", "entries"), Palette.Butter, Glyph.Sort,
                "A little to-play list for the Up next widget. Type a game you want to get to.", keywords = "backlog todo list",
            ) { UpNext.ask() },
        ),
    )
}
