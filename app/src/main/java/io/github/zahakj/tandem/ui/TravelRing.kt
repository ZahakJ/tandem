package io.github.zahakj.tandem.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.toSize
import io.github.zahakj.tandem.data.Store
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

/*
 * The travelling highlight (Settings › Look & feel › Motion › Travelling highlight). Instead of each tile drawing its
 * own ring, which pops off one tile and onto the next, one ring drawn over the whole grid springs from tile to tile,
 * overshooting a little and settling, and keeps its speed through a quick run of presses. The tile it lands on lifts
 * a little (IconTile).
 *
 * Cheap on purpose: the focused tile alone tells the ring where it is (its place on screen, kept while it is
 * focused), the ring is an offset from that place springing back to nothing, and the overlay reads both only while
 * drawing. Moving the highlight or scrolling the grid recomposes nothing; only the overlay redraws. While a tile it
 * is on scrolls, the ring rides along with it exactly. Off with Reduce motion and the Off animation style, where each
 * tile draws its own ring as before. The ring keeps the highlight's colour (the item's or the accent) and thickness.
 */

@Stable
class TravelRing internal constructor(private val scope: CoroutineScope) {
    /**
     * The focused tile's place: its coordinates (read again while drawing, so a grid zooming or arriving under it
     * carries the ring along) and its box when it was last seen, in the overlay's coordinates (px).
     */
    private var place: LayoutCoordinates? = null
    private var target: Rect? = null
    /** Bumped when the focused tile moves (a scroll), so the overlay draws again: the only state the draw reads for it. */
    private var moved by mutableIntStateOf(0)
    private var hop = 0f
    private var key: String? = null
    var color by mutableStateOf(Color.White)
        private set
    private var corner by mutableFloatStateOf(0f)
    private var stroke by mutableFloatStateOf(1f)
    /** How far the ring still is from [target] (left, top, right, bottom), springing back to nothing. */
    private val lag = Animatable(Rect.Zero, Rect.VectorConverter)
    /** 0 while no tile here has the highlight, 1 while one has. */
    private val shown = Animatable(0f)
    /** The overlay's place in the window, so the tiles' places can be turned into its coordinates. */
    internal var origin: Offset = Offset.Zero

    /** Where [c] is in the overlay, raised by [hop] (the lift of the tile the ring is on). */
    private fun boxOf(c: LayoutCoordinates): Rect = c.boxInRoot().translate(-origin.x, -origin.y - hop)

    /**
     * Tile [key] has the highlight and is laid out at [at] (its coordinates before its own grow and lift), with this
     * [corner] radius, ring colour, [stroke] width and [hop] (how far it rises). Called when it takes the highlight
     * and whenever it moves while it has it.
     */
    fun report(key: String, at: LayoutCoordinates, corner: Float, color: Color, stroke: Float, hop: Float) {
        val was = target
        this.corner = corner
        this.color = color
        this.stroke = stroke
        this.hop = hop
        place = at
        val to = boxOf(at)
        target = to
        moved++
        if (key == this.key && was != null) return
        this.key = key
        // From where the ring is drawn now, unless it was gone (the highlight came from the dock, say): then it
        // appears on the tile, never flying in from somewhere far.
        val from = was?.takeIf { shown.value > 0.05f }?.let { w -> val l = offset(); Rect(w.left + l.left, w.top + l.top, w.right + l.right, w.bottom + l.bottom) }
        val velocity = lag.velocity
        val start = if (from == null) Rect.Zero else Rect(from.left - to.left, from.top - to.top, from.right - to.right, from.bottom - to.bottom)
        // Drawn from where it is at once; the spring takes over when its coroutine runs (a frame drawn in between
        // would show the ring on the new tile for an instant).
        pending = start
        scope.launch {
            lag.snapTo(start)
            if (pending === start) pending = null
            if (from != null) lag.animateTo(Rect.Zero, spring(0.7f, 600f), initialVelocity = velocity)
        }
        scope.launch { shown.animateTo(1f, tween(90)) }
    }

    /** The ring's offset set by the last [report] whose spring hasn't started yet. */
    private var pending: Rect? = null

    private fun offset(): Rect = pending ?: lag.value

    /** Tile [key] lost the highlight: the ring fades unless another tile here takes it at once (it travels there). */
    fun leave(key: String) {
        if (this.key != key) return
        this.key = null
        scope.launch { shown.animateTo(0f, tween(140)) }
    }

    /** Draws the ring, grown by [grow] about its middle as the tile is; nothing while [hidden] (a swap arriving). */
    internal fun DrawScope.drawRing(grow: Float, gap: Float, hidden: Boolean) {
        moved
        val a = shown.value
        if (a < 0.01f || hidden) return
        val t = place?.takeIf { it.isAttached }?.let { boxOf(it).also { b -> target = b } } ?: target ?: return
        val l = offset()
        val r = Rect(t.left + l.left, t.top + l.top, t.right + l.right, t.bottom + l.bottom)
        val c = r.center
        val inflate = gap + stroke / 2f
        val halfW = (r.width / 2f + inflate) * grow
        val halfH = (r.height / 2f + inflate) * grow
        drawRoundRect(
            color, Offset(c.x - halfW, c.y - halfH), Size(halfW * 2, halfH * 2), CornerRadius((corner + inflate) * grow),
            alpha = a, style = Stroke(stroke * grow),
        )
    }

    companion object {
        /** The travelling highlight is wanted: its setting on, motion not reduced, the theme animating. */
        val wanted: Boolean
            get() = Store.settings.look.highlightTravel && !Store.settings.reduceMotion && Motion.style != AnimationStyle.Off
    }
}

/** A travelling ring for one grid, or null when it isn't wanted (each tile then draws its own). */
@Composable
fun rememberTravelRing(enabled: Boolean = true): TravelRing? {
    val scope = rememberCoroutineScope()
    val ring = remember(scope) { TravelRing(scope) }
    return ring.takeIf { enabled && TravelRing.wanted }
}

/**
 * The layer the ring is drawn in: lay it over the grid, the same size. [hidden] is read while drawing (a swap's
 * clock: the tiles fly in on their own then, and the ring waits for them).
 */
fun Modifier.travelOverlay(ring: TravelRing, grow: () -> Float, gap: Float, hidden: () -> Boolean): Modifier =
    onGloballyPositioned { ring.origin = it.positionInRoot() }.drawBehind { with(ring) { drawRing(grow(), gap, hidden()) } }

/** A tile's box in the window, unclipped (a tile half scrolled out keeps its whole ring). */
fun LayoutCoordinates.boxInRoot(): Rect = Rect(positionInRoot(), size.toSize())

/** The setting's showcase: a ring hopping round four tiles, travelling with a little overshoot, or jumping. */
@Composable
internal fun TravelScene(on: Boolean) {
    val clock = loopClock(3600)
    MiniScreen { t, u ->
        miniTopBar(t, u)
        val cols = 4
        val gap = 6 * u
        val left = 14 * u
        val tw = (size.width - left * 2 - gap * (cols - 1)) / cols
        val th = 26 * u
        val top = 30 * u
        for (row in 0 until 2) for (c in 0 until cols) miniTile(t, left + c * (tw + gap), top + row * (th + gap), tw, th, row * cols + c, 4 * u)
        // Four stops round the tiles, a quarter of the loop each: a quick move, then a rest.
        val p = clock.value * 4f
        val stop = p.toInt() % 4
        val into = (p - p.toInt()).coerceIn(0f, 1f)
        val path = listOf(0 to 0, 1 to 0, 2 to 1, 0 to 1)
        fun at(i: Int) = path[i % path.size].let { (c, r) -> Offset(left + c * (tw + gap), top + r * (th + gap)) }
        val from = at(stop)
        val to = at(stop + 1)
        // A spring's look: past the tile a little, then back.
        val k = if (!on) (if (into < 0.3f) 0f else 1f) else (into / 0.35f).coerceAtMost(1f).let { x -> 1f - (1f - x) * (1f - x) + 0.12f * sin(x * PI.toFloat()) }
        val o = Offset(from.x + (to.x - from.x) * k, from.y + (to.y - from.y) * k)
        val lift = if (on) 1.5f * u else 0f
        val ring = 1.6f * u
        drawRoundRect(t.text, Offset(o.x - ring, o.y - ring - lift), Size(tw + ring * 2, th + ring * 2), CornerRadius(5.5f * u), style = Stroke(1.2f * u))
    }
}
