package io.github.zahakj.tandem.ui

import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.platform.AndroidUiDispatcher
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import io.github.zahakj.tandem.Selected
import io.github.zahakj.tandem.Selection
import io.github.zahakj.tandem.data.Store
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * The X swap's choreography. On each screen things flow towards the hinge:
 * down on the top screen, up on the bottom one.
 *
 * - The screen losing the grid: a picture of the grid taken just before the swap is cut into its tiles, which drift
 *   away from the highlighted one (and with the flow), shrink, blur and fade, nearest first; the highlighted tile
 *   swells into the hand-off. Under them the Spotlight sweeps in: its colour band diagonally, the big icon with a
 *   little overshoot, the name card sliding up, the status pill and hints last.
 * - The screen gaining the grid: the old Spotlight slides with the flow and fades; the tiles fly in from scattered
 *   places against the flow and settle on a spring, staggered by distance from the highlighted tile; then the dock
 *   rises and the corner bits fade in.
 * Swapping back plays the same choreography the other way round. Everything moves in graphics layers that read one
 * clock per screen in the draw phase: nothing recomposes per frame, and tile offsets are worked out once. Reduce
 * motion (or animations Off) swaps at once.
 */

/** Which physical screen this composition is on. Provided by [SwapStage]. */
val LocalScreenSlot = staticCompositionLocalOf { ScreenSlot.Top }

/** A piece of the old grid picture: a tile, the highlighted tile or the dock. */
internal class SwapPiece(val src: Rect, val path: Path, val kind: Int, val delay: Float, val dir: Offset, val dist: Float)

/** The picture a screen showed just before the swap, and (for the grid) its pieces. */
internal class SwapShot(val image: ImageBitmap, val pieces: List<SwapPiece>)

object SwapFx {
    /** The clock reads this when no swap runs. */
    const val IDLE = 1e9f
    /** How long the whole choreography runs, in ms. */
    const val LENGTH = 950f

    private val top = mutableFloatStateOf(IDLE)
    private val bottom = mutableFloatStateOf(IDLE)

    /** Milliseconds into the running swap on [slot] (read it in graphics layers only), or [IDLE]. */
    fun clock(slot: ScreenSlot) = if (slot == ScreenSlot.Top) top else bottom

    /** Which way things move on [slot]: down (1) on the top screen, up (-1) on the bottom one. */
    fun flow(slot: ScreenSlot): Float = if (slot == ScreenSlot.Top) 1f else -1f

    /** True while a swap or the startup arrival runs (a second X waits; things composed now skip their own entrance). */
    val busy: Boolean get() = swapping || intro[0] || intro[1]

    @Volatile private var swapping = false
    /** A screen held for, or playing, the startup arrival ([introHold], [introPlay]). */
    private val intro = BooleanArray(2)
    private val introJobs = arrayOfNulls<kotlinx.coroutines.Job>(2)

    private val custom = BooleanArray(2)

    /** What arrives on [slot] animates its own pieces (grid tiles, Spotlight); otherwise the screen arrives whole. */
    fun custom(slot: ScreenSlot): Boolean = custom[slot.ordinal]

    internal val shots = arrayOfNulls<SwapShot>(2)
    internal var shotVersion by mutableIntStateOf(0)
    internal val capturers = HashMap<ScreenSlot, suspend () -> ImageBitmap>()

    /** Where each home-grid tile is (set as tiles are placed; read only when a swap starts). */
    internal val tiles = HashMap<String, LayoutCoordinates>()
    internal var dock: LayoutCoordinates? = null
    /** The grid's icon corner radius in px. */
    internal var tileCorner = 0f

    private val scope = CoroutineScope(AndroidUiDispatcher.Main)

    /** Runs [flip] (which swaps the screens) inside the choreography, or straight away with reduce motion. */
    fun swap(flip: () -> Unit) {
        if (busy) return
        if (!Flair.entering) { flip(); return }
        swapping = true
        scope.launch {
            try {
                val libSlot = if (ScreenRoles.libraryOnBottom) ScreenSlot.Bottom else ScreenSlot.Top
                val detSlot = if (libSlot == ScreenSlot.Top) ScreenSlot.Bottom else ScreenSlot.Top
                val gridShown = IconGrid.enabled && Nav.route == Route.Home && Layers.top == null && !Nav.overlayOpen
                val libImage = capture(libSlot)
                val detImage = capture(detSlot)
                shots[libSlot.ordinal] = libImage?.let { SwapShot(it, if (gridShown) pieces(it.width, it.height) else emptyList()) }
                shots[detSlot.ordinal] = detImage?.let { SwapShot(it, emptyList()) }
                // After the flip the library's old screen shows the details and the other one the library.
                custom[libSlot.ordinal] = Store.settings.bottomMode != "clock" && Spotlight.wanted(Selection.current)
                custom[detSlot.ordinal] = gridShown
                top.floatValue = 0f
                bottom.floatValue = 0f
                shotVersion++
                flip()
                // Hold the first pose until both screens have drawn their new content, so nothing starts half-way.
                withFrameNanos { }
                withFrameNanos { }
                val start = withFrameNanos { it }
                while (true) {
                    val t = (withFrameNanos { it } - start) / 1_000_000f
                    top.floatValue = t
                    bottom.floatValue = t
                    if (t >= LENGTH) break
                }
            } finally {
                top.floatValue = IDLE
                bottom.floatValue = IDLE
                shots.fill(null)
                shotVersion++
                swapping = false
            }
        }
    }

    /**
     * Startup ([StartFlow]): [slot]'s content waits out of sight (its clock holds at 0, so the stage layer and every
     * [swapArrive] piece are invisible) until [introPlay]. Nothing with reduce motion or animations off.
     */
    fun introHold(slot: ScreenSlot) {
        if (swapping || !Flair.entering) return
        intro[slot.ordinal] = true
        custom[slot.ordinal] = false
        clock(slot).floatValue = 0f
    }

    /**
     * Plays the arrival half of the swap on a held [slot], with no old picture leaving: the grid's tiles fly in
     * ([pieces]) or the whole content rises in. Starts two frames on, once the screen has drawn its content.
     */
    fun introPlay(slot: ScreenSlot, pieces: Boolean) {
        val i = slot.ordinal
        if (!intro[i] || introJobs[i] != null) return
        custom[i] = pieces
        val clock = clock(slot)
        introJobs[i] = scope.launch {
            try {
                withFrameNanos { }
                withFrameNanos { }
                val start = withFrameNanos { it }
                while (true) {
                    val t = (withFrameNanos { it } - start) / 1_000_000f
                    clock.floatValue = t
                    if (t >= LENGTH) break
                }
            } finally {
                clock.floatValue = IDLE
                custom[i] = false
                intro[i] = false
                introJobs[i] = null
            }
        }
    }

    /** A held [slot] that never showed: from now on it shows normally, with no arrival. */
    fun introDrop(slot: ScreenSlot) {
        val i = slot.ordinal
        if (!intro[i] || introJobs[i] != null) return
        intro[i] = false
        custom[i] = false
        clock(slot).floatValue = IDLE
    }

    private suspend fun capture(slot: ScreenSlot): ImageBitmap? {
        val c = capturers[slot] ?: return null
        return withTimeoutOrNull(160) { runCatching { c() }.getOrNull() }
    }

    /** The grid's tiles and dock as pieces of the picture, with their delays and directions worked out once. */
    private fun pieces(w: Int, h: Int): List<SwapPiece> {
        val screen = Rect(0f, 0f, w.toFloat(), h.toFloat())
        val focusKey = FocusMemory.get(Route.Home.key)
        val placed = tiles.mapNotNull { (k, c) ->
            if (!c.isAttached) return@mapNotNull null
            val r = c.boundsInRoot()
            if (r.width < 4f || r.height < 4f || !r.overlaps(screen)) null else k to r
        }
        if (placed.isEmpty()) return emptyList()
        val size = placed.maxOf { it.second.width }.coerceAtLeast(1f)
        val focus = placed.firstOrNull { it.first == focusKey }?.second?.center ?: screen.center
        val out = ArrayList<SwapPiece>()
        for ((k, r0) in placed) {
            val isFocus = k == focusKey
            // The highlighted tile has grown and wears its ring a few px outside its bounds: take those too.
            val grow = if (isFocus) size * 0.14f else size * 0.03f
            val r = r0.inflate(grow).intersect(screen)
            val v = r.center - focus
            val len = hypot(v.x, v.y)
            val dist = len / size
            val dir = if (len < 1f) Offset.Zero else v / len
            val path = Path().apply { addRoundRect(RoundRect(r, CornerRadius(tileCorner + grow))) }
            out += SwapPiece(r, path, if (isFocus) 1 else 0, if (isFocus) 0f else min(dist, 6f) * 22f, dir, dist)
        }
        dock?.takeIf { it.isAttached }?.boundsInRoot()?.takeIf { it.width > 4f && it.overlaps(screen) }?.let { r0 ->
            val r = r0.inflate(size * 0.12f).intersect(screen)
            out += SwapPiece(r, Path().apply { addRoundRect(RoundRect(r, CornerRadius(r.height / 2f))) }, 2, 30f, Offset.Zero, 0f)
        }
        return out
    }
}

// ---- Curves ----

/** A spring from 0 to 1 (with a little overshoot for damping under 1), [ms] after it starts. */
fun springAt(ms: Float, damping: Float = 0.62f, hz: Float = 2.2f): Float {
    if (ms <= 0f) return 0f
    val t = ms / 1000f
    val w = 2f * PI.toFloat() * hz
    val z = damping.coerceIn(0.05f, 0.99f)
    val wd = w * sqrt(1f - z * z)
    return 1f - exp(-z * w * t) * (cos(wd * t) + z * w / wd * sin(wd * t))
}

private fun easeOutCubic(x: Float): Float { val c = 1f - x.coerceIn(0f, 1f); return 1f - c * c * c }

private fun smooth(x: Float): Float { val c = x.coerceIn(0f, 1f); return c * c * (3f - 2f * c) }

// ---- Arrivals (things that come in with a swap) ----

/**
 * Comes in with a swap on this screen: from ([fromX], [fromY]) px and [fromScale], [delayMs] after it starts, on a
 * spring, fading in over [fadeMs]. Only while what arrives here animates its own pieces ([SwapFx.custom]).
 */
fun Modifier.swapArrive(
    slot: ScreenSlot, delayMs: Float, fromX: Float, fromY: Float, fromScale: Float = 1f, fadeMs: Float = 160f,
    damping: Float = 0.62f,
): Modifier = graphicsLayer {
    val t = SwapFx.clock(slot).floatValue
    if (t >= SwapFx.IDLE / 2f || !SwapFx.custom(slot)) return@graphicsLayer
    val ms = t - delayMs
    val p = springAt(ms, damping)
    translationX = fromX * (1f - p)
    translationY = fromY * (1f - p)
    val s = fromScale + (1f - fromScale) * p
    scaleX = s; scaleY = s
    alpha = (ms / fadeMs).coerceIn(0f, 1f)
}

/** The Spotlight's colour band or hero sweeping in diagonally, from the top right (bottom right on the bottom screen). */
fun Modifier.swapSweep(slot: ScreenSlot): Modifier = graphicsLayer {
    val t = SwapFx.clock(slot).floatValue
    if (t >= SwapFx.IDLE / 2f || !SwapFx.custom(slot)) return@graphicsLayer
    val e = easeOutCubic(t / 440f)
    translationX = size.width * 0.32f * (1f - e)
    translationY = -SwapFx.flow(slot) * size.height * 0.24f * (1f - e)
    alpha = (t / 180f).coerceIn(0f, 1f)
}

/** The tint the wallpaper takes from what is highlighted. */
@Composable
fun selectionTint(): Color = when (val s = Selection.current) {
    is Selected.GameItem -> Palette.forName(s.game.platform.id)
    is Selected.System -> Palette.forName(s.library.platform.id)
    is Selected.Group -> s.color
    is Selected.Setting -> s.color
    is Selected.App -> appTint(s.pkg)
    Selected.Home -> Palette.Lavender
}

/**
 * One screen's stage: its wallpaper (which stays put through a swap, so a video never restarts behind the motion),
 * the content (recorded into a layer, so a swap can take its picture), and the old picture on top while a swap runs.
 */
@Composable
fun SwapStage(slot: ScreenSlot, content: @Composable () -> Unit) {
    val layer = rememberGraphicsLayer()
    DisposableEffect(slot, layer) {
        val c: suspend () -> ImageBitmap = { layer.toImageBitmap() }
        SwapFx.capturers[slot] = c
        onDispose { if (SwapFx.capturers[slot] === c) SwapFx.capturers.remove(slot) }
    }
    // A cold start holds both screens out of sight until the library is ready, then plays their arrival (StartFlow).
    DisposableEffect(slot) {
        StartFlow.stageShown(slot)
        onDispose { StartFlow.stageGone(slot) }
    }
    val density = LocalDensity.current.density
    CompositionLocalProvider(LocalScreenSlot provides slot) {
        Box(Modifier.fillMaxSize()) {
            TandemTheme { Wallpaper(slot, selectionTint()) }
            androidx.compose.runtime.CompositionLocalProvider(LocalOnStage provides true) { Box(
                Modifier.fillMaxSize()
                    // Content without its own choreography arrives whole: against the flow, on a soft spring.
                    .graphicsLayer {
                        val t = SwapFx.clock(slot).floatValue
                        if (t >= SwapFx.IDLE / 2f || SwapFx.custom(slot)) return@graphicsLayer
                        val p = springAt(t - 60f, 0.8f, 1.9f)
                        translationY = -SwapFx.flow(slot) * 46f * density * (1f - p)
                        alpha = ((t - 60f) / 200f).coerceIn(0f, 1f)
                    }
                    .drawWithContent {
                        layer.record { this@drawWithContent.drawContent() }
                        drawLayer(layer)
                    },
            ) { content() } }
            // Above the arriving layer, so the splash fades on its own while the content comes in under it.
            StartSplash(slot)
            SwapOverlay(slot)
        }
    }
}

/** The old picture leaving this screen: whole (sliding with the flow) or cut into tiles that scatter. */
@Composable
private fun SwapOverlay(slot: ScreenSlot) {
    @Suppress("UNUSED_VARIABLE") val version = SwapFx.shotVersion
    val shot = SwapFx.shots[slot.ordinal] ?: return
    val clock = SwapFx.clock(slot)
    val flow = SwapFx.flow(slot)
    val d = LocalDensity.current.density
    val scattered = shot.pieces.isNotEmpty()
    Canvas(
        Modifier.fillMaxSize().graphicsLayer {
            compositingStrategy = CompositingStrategy.Offscreen
            val t = clock.floatValue
            renderEffect = if (scattered && Build.VERSION.SDK_INT >= 31 && t < SwapFx.LENGTH) {
                val r = smooth(t / 280f) * 7f * d
                if (r > 0.5f) BlurEffect(r, r, TileMode.Decal) else null
            } else null
        },
    ) {
        val t = clock.floatValue
        if (t >= SwapFx.LENGTH) return@Canvas
        val img = shot.image
        if (!scattered) {
            // The hero leaving: it slides with the flow, shrinks a touch and fades.
            val p = easeOutCubic(t / 340f)
            if (p >= 1f) return@Canvas
            withTransform({ translate(0f, flow * 44f * d * p); scale(1f - 0.05f * p, 1f - 0.05f * p, center) }) {
                drawImage(img, alpha = 1f - p)
            }
            return@Canvas
        }
        // What isn't a tile (Pip, the hints) drifts with the flow and fades quickly; the tiles are cut out of it.
        val pb = easeOutCubic(t / 220f)
        if (pb < 1f) {
            translate(0f, flow * 22f * d * pb) {
                drawImage(img, alpha = 1f - pb)
                for (pc in shot.pieces) drawPath(pc.path, Color.Black, blendMode = BlendMode.Clear)
            }
        }
        for (pc in shot.pieces) {
            val length = when (pc.kind) { 1 -> 260f; 2 -> 240f; else -> 320f }
            val local = ((t - pc.delay) / length).coerceIn(0f, 1f)
            if (local >= 1f) continue
            val e = local * local * (3f - 2f * local)
            val reach = (26f + 9f * min(pc.dist, 6f)) * d
            val dx: Float
            val dy: Float
            val s: Float
            when (pc.kind) {
                1 -> { dx = 0f; dy = flow * 18f * d * e; s = 1f + 0.32f * e }
                2 -> { dx = 0f; dy = flow * 56f * d * e; s = 1f - 0.06f * e }
                else -> { dx = pc.dir.x * reach * e; dy = pc.dir.y * reach * e + flow * 78f * d * e; s = 1f - 0.22f * e }
            }
            val a = 1f - e
            val c = pc.src.center
            withTransform({ translate(dx, dy); scale(s, s, c) }) {
                clipPath(pc.path) {
                    drawImage(
                        img,
                        srcOffset = IntOffset(pc.src.left.roundToInt(), pc.src.top.roundToInt()),
                        srcSize = IntSize(pc.src.width.roundToInt().coerceAtMost(img.width - pc.src.left.roundToInt()), pc.src.height.roundToInt().coerceAtMost(img.height - pc.src.top.roundToInt())),
                        dstOffset = IntOffset(pc.src.left.roundToInt(), pc.src.top.roundToInt()),
                        dstSize = IntSize(pc.src.width.roundToInt().coerceAtMost(img.width - pc.src.left.roundToInt()), pc.src.height.roundToInt().coerceAtMost(img.height - pc.src.top.roundToInt())),
                        alpha = a,
                    )
                }
            }
        }
    }
}
