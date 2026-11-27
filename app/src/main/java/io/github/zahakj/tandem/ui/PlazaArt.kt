package io.github.zahakj.tandem.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/*
 * The Plaza, drawn: a bright little square under an open sky. Back to front: the sky (its colours following the
 * clock), drifting clouds and the odd bird; hazy hills and a pastel town behind a row of round trees; the paved
 * square, its stones laid in rings round the fountain; the fountain itself, its water arcing and rippling; then
 * everyone on the square, farthest first, each on a soft shadow; planters at the front corners; petals or butterflies
 * by day, fireflies by night. Then the time of day is laid over it all, the lamps and windows light up after dark, and
 * last come the bubbles and the marker over whoever is highlighted.
 *
 * What never moves (hills, town, trees, the paving, benches and lamp posts) is drawn once into a picture for the
 * size; everything else is drawn each frame from kept paths and brushes, so a frame allocates nothing.
 */

/** Colours from Tandem's theme that the square wears (its flags, awnings and flowers). */
class PlazaLook(val accents: List<Color>)

class PlazaPainter {
    private var w = 0f
    private var h = 0f
    private var back: ImageBitmap? = null
    private var spare: ImageBitmap? = null
    private var backLook: PlazaLook? = null
    private var bakedGrade = Color.Unspecified
    private var bakedLights = -1f
    private val bakeScope = CanvasDrawScope()

    /** The light of the hour over everything that moves (a layer tinted as a whole). */
    private val gradePaint = Paint()
    private var filterGrade = Color.Unspecified

    /** Windows in the town, lit after dark: x, y, width, height each. */
    private val windows = FloatArray(4 * 80)
    private var windowCount = 0

    /** Lamp heads: x, y, size each, and where each lamp stands. */
    private val lamps = FloatArray(2 * 3)
    private val lampGround = FloatArray(2)

    private val stars = FloatArray(56 * 3)

    // Kept shapes and brushes.
    private val scratch = Path()
    private val heart = Path()
    private val star5 = Path()
    private val marker = Path()
    private val crescent = Path()
    private val bowl = Path()
    private val shadowBrush = Brush.radialGradient(0f to Color.Black.copy(alpha = 0.30f), 0.55f to Color.Black.copy(alpha = 0.16f), 1f to Color.Transparent, center = Offset.Zero, radius = 1f)
    private val glowBrush = Brush.radialGradient(0f to Color(0xFFFFE7A8).copy(alpha = 0.85f), 0.35f to Color(0xFFFFD27A).copy(alpha = 0.35f), 1f to Color.Transparent, center = Offset.Zero, radius = 1f)
    private val whiteGlow = Brush.radialGradient(0f to Color.White.copy(alpha = 0.9f), 1f to Color.Transparent, center = Offset.Zero, radius = 1f)
    private var skyBrush: Brush = Brush.verticalGradient(listOf(Color.White, Color.White))
    private var waterBrush: Brush = skyBrush
    private var skyKey = -1f
    private val tod = TimeOfDay()

    init {
        heartPath(heart)
        starPath(star5, 5, 0.48f)
        // A rounded drop pointing down, 2 wide and 2.4 tall, its tip at (0, 0).
        marker.moveTo(0f, 0f)
        marker.cubicTo(-0.45f, -0.55f, -1f, -1.0f, -1f, -1.55f)
        marker.cubicTo(-1f, -2.15f, -0.55f, -2.45f, 0f, -2.45f)
        marker.cubicTo(0.55f, -2.45f, 1f, -2.15f, 1f, -1.55f)
        marker.cubicTo(1f, -1.0f, 0.45f, -0.55f, 0f, 0f)
        marker.close()
        val a = Path().apply { addOval(androidx.compose.ui.geometry.Rect(-1f, -1f, 1f, 1f)) }
        val b = Path().apply { addOval(androidx.compose.ui.geometry.Rect(-0.55f, -1.15f, 1.35f, 0.85f)) }
        crescent.op(a, b, PathOperation.Difference)
    }

    // --- Where things are ------------------------------------------------------------------------------------------

    /** How much nearer things look at the front: the scale at the back of the walk (z 0) is [S_FAR] of the front's. */
    private fun scale(z: Float): Float = 1f / (1f / S_FAR + (1f - 1f / S_FAR) * z)
    private fun sx(x: Float, z: Float): Float = w / 2f + x * w * 0.5f * scale(z)
    private fun sy(z: Float): Float = h * NEAR_Y * scale(z)

    /** A character's height at depth [z]. */
    private fun tall(z: Float): Float = h * TALL * scale(z)

    // --- The frame -------------------------------------------------------------------------------------------------

    /**
     * Draws the square with [world] in it at [time] seconds of ambient time, at [hour] of the day (0..24). [still]
     * draws the calm version (no drifting or arcing, the lights as they are).
     */
    fun draw(scope: DrawScope, world: PlazaWorld, time: Float, hour: Float, look: PlazaLook, still: Boolean) = with(scope) {
        tod.at(hour)
        if (size.width != w || size.height != h || back == null || backLook !== look || tod.differs(bakedGrade, bakedLights)) {
            w = size.width
            h = size.height
            backLook = look
            bake(look)
        }
        if (abs(skyKey - hour) > 0.02f) {
            skyKey = hour
            skyBrush = Brush.verticalGradient(listOf(tod.skyTop, tod.skyMid, tod.skyBottom), 0f, h * 0.42f)
        }
        if (tod.grade != filterGrade) {
            filterGrade = tod.grade
            gradePaint.colorFilter = ColorFilter.tint(tod.grade, BlendMode.Modulate)
        }
        val t = if (still) 0f else time

        // The sky, its own colours for the hour.
        drawRect(skyBrush, Offset.Zero, Size(w, h * 0.42f))
        sunAndMoon(t)
        stars(t, still)
        clouds(t)
        if (!still) birds(t)

        // Everything that stays put, already in the light of the hour (lit windows and all).
        back?.let { drawImage(it) }

        // Everything that moves, in that same light: one layer tinted as a whole (none by day).
        val graded = tod.grade != Color.White
        if (graded) drawIntoCanvas { it.saveLayer(Rect(0f, 0f, w, h), gradePaint) }
        fountain(t, still)
        people(world, t, still)
        planters(t)
        if (!still) drift(t)
        if (graded) drawIntoCanvas { it.restore() }

        // What shines after dark.
        lights(t, still)

        // Bubbles and the marker stay bright and clear.
        overlays(world, t, still)
    }

    // --- Sky -------------------------------------------------------------------------------------------------------

    private fun DrawScope.sunAndMoon(t: Float) {
        val day = tod.sun
        if (day > 0.01f) {
            // Arcs over the sky with the hours, low in the morning and evening.
            val p = ((tod.hour - 6f) / 13f).coerceIn(0f, 1f)
            val x = w * (0.12f + 0.76f * p)
            val y = h * (0.30f - 0.2f * sin(p * PI.toFloat()))
            val r = h * 0.05f
            withTransform({ translate(x, y); scale(r * 4f, r * 4f, Offset.Zero) }) {
                drawCircle(whiteGlow, 1f, Offset.Zero, alpha = 0.55f * day)
            }
            drawCircle(Color(0xFFFFF6D8).copy(alpha = day), r, Offset(x, y))
            drawCircle(Color(0xFFFFE9A6).copy(alpha = 0.6f * day), r * 0.78f, Offset(x + r * 0.08f, y + r * 0.1f))
        }
    }

    private fun DrawScope.clouds(t: Float) {
        val tint = tod.cloud
        val shade = lerp(tint, tod.skyTop, 0.28f)
        // A few small clouds, nearer ones lower, larger and a little faster, drifting the same way.
        for (i in 0 until 5) {
            val near = ((i * 3) % 5) / 4f
            val cw = w * (0.055f + 0.04f * near)
            val span = w + cw * 2f
            val x = ((i * 0.29f * span + t * w * (0.004f + 0.004f * near)) % span) - cw
            val y = h * (0.12f + 0.15f * near)
            cloud(x, y, cw, tint, shade, 0.75f + 0.25f * near)
        }
    }

    /** A puffy cloud [cw] wide, its flat base at [y]. */
    private fun DrawScope.cloud(x: Float, y: Float, cw: Float, top: Color, under: Color, alpha: Float) {
        val r = cw * 0.2f
        drawRoundRect(under, Offset(x, y - r * 0.9f), Size(cw, r * 1.15f), CornerRadius(r * 0.6f), alpha = alpha)
        drawCircle(under, r * 1.05f, Offset(x + cw * 0.34f, y - r * 0.75f), alpha = alpha)
        drawCircle(under, r * 1.3f, Offset(x + cw * 0.6f, y - r * 1.0f), alpha = alpha)
        drawRoundRect(top, Offset(x + r * 0.15f, y - r * 1.05f), Size(cw - r * 0.3f, r * 0.95f), CornerRadius(r * 0.5f), alpha = alpha)
        drawCircle(top, r * 0.95f, Offset(x + cw * 0.3f, y - r * 0.95f), alpha = alpha)
        drawCircle(top, r * 1.2f, Offset(x + cw * 0.58f, y - r * 1.25f), alpha = alpha)
        drawCircle(top, r * 0.8f, Offset(x + cw * 0.8f, y - r * 0.85f), alpha = alpha)
    }

    private fun DrawScope.birds(t: Float) {
        if (tod.sun < 0.3f) return
        // Every half minute a few birds cross, high up, flapping.
        val cycle = 31f
        val c = t % cycle
        if (c > 12f) return
        val round = (t / cycle).toInt()
        val dir = if (round % 2 == 0) 1f else -1f
        val p = c / 12f
        val baseY = h * (0.1f + 0.05f * (round % 3))
        val ink = Color(0xFF55607A)
        for (i in 0 until 3) {
            val bx = if (dir > 0) -w * 0.05f + p * w * 1.1f - i * w * 0.03f else w * 1.05f - p * w * 1.1f + i * w * 0.03f
            val by = baseY + i * h * 0.018f + sin(t * 1.7f + i) * h * 0.006f
            val s = h * (0.011f - i * 0.0015f)
            val flap = sin(t * 11f + i * 1.9f)
            scratch.reset()
            scratch.moveTo(bx - s * 1.4f, by - s * 0.5f * flap)
            scratch.quadraticTo(bx - s * 0.6f, by - s * (0.2f + 0.6f * flap), bx, by)
            scratch.quadraticTo(bx + s * 0.6f, by - s * (0.2f + 0.6f * flap), bx + s * 1.4f, by - s * 0.5f * flap)
            drawPath(scratch, ink, style = Stroke(s * 0.32f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }

    // --- The picture that stays ------------------------------------------------------------------------------------

    /**
     * Draws what stays put into [back], in the light of the hour: the hills and town first, tinted, their windows lit
     * on top; then the trees, square, benches and lamp posts, tinted on their own and laid over them (so trees hide
     * the windows behind them). Done again only when the light changes enough to see (dawn and dusk, every few
     * minutes); the pictures are kept and reused.
     */
    private fun bake(look: PlazaLook) {
        val iw = w.toInt().coerceAtLeast(1)
        val ih = h.toInt().coerceAtLeast(1)
        val img = back?.takeIf { it.width == iw && it.height == ih } ?: ImageBitmap(iw, ih)
        val front = spare?.takeIf { it.width == iw && it.height == ih } ?: ImageBitmap(iw, ih)
        val grade = tod.grade
        val lit = tod.lights
        val density = androidx.compose.ui.unit.Density(1f)
        windowCount = 0
        bakeScope.draw(density, LayoutDirection.Ltr, Canvas(img), Size(w, h)) {
            drawRect(Color.Transparent, blendMode = BlendMode.Clear)
            hills()
            town(look)
            if (grade != Color.White) drawRect(grade, blendMode = BlendMode.Modulate)
            if (lit > 0.01f) for (i in 0 until windowCount) {
                val x = windows[i * 4]
                val y = windows[i * 4 + 1]
                val ww = windows[i * 4 + 2]
                val wh = windows[i * 4 + 3]
                drawRoundRect(Color(0xFFFFD98A).copy(alpha = 0.35f * lit), Offset(x - ww * 0.4f, y - wh * 0.4f), Size(ww * 1.8f, wh * 1.8f), CornerRadius(ww * 0.6f))
                drawRoundRect(Color(0xFFFFE6A6).copy(alpha = lit), Offset(x, y), Size(ww, wh), CornerRadius(ww * 0.3f))
            }
        }
        bakeScope.draw(density, LayoutDirection.Ltr, Canvas(front), Size(w, h)) {
            drawRect(Color.Transparent, blendMode = BlendMode.Clear)
            backTrees()
            floor()
            benches()
            lampPosts(look)
            vignette()
            if (grade != Color.White) drawRect(grade, blendMode = BlendMode.Modulate)
        }
        bakeScope.draw(density, LayoutDirection.Ltr, Canvas(img), Size(w, h)) { drawImage(front) }
        back = img
        spare = front
        bakedGrade = grade
        bakedLights = lit
        waterBrush = Brush.verticalGradient(listOf(Color(0xFFA6E6F4), Color(0xFF63C3E2), Color(0xFF4FAED6)), sy(-FOUNTAIN_BACK), sy(FOUNTAIN_BACK))
        val r = java.util.Random(11)
        for (i in 0 until stars.size / 3) {
            stars[i * 3] = r.nextFloat() * w
            stars[i * 3 + 1] = r.nextFloat().let { it * it } * h * 0.33f
            stars[i * 3 + 2] = 0.6f + r.nextFloat() * 1.4f
        }
    }

    private fun DrawScope.hills() {
        val haze = Color(0xFFDDF1FA)
        // Far hills, then nearer ones, softer and bluer the farther they are.
        hillBand(h * 0.285f, h * 0.035f, 0.9f, 1.3f, lerp(Color(0xFF9FD0D8), haze, 0.55f))
        hillBand(h * 0.32f, h * 0.03f, 1.7f, 4.1f, lerp(Color(0xFF8CCB9C), haze, 0.4f))
    }

    private fun DrawScope.hillBand(y: Float, amp: Float, f: Float, ph: Float, color: Color) {
        scratch.reset()
        scratch.moveTo(0f, h * 0.5f)
        var x = 0f
        while (x <= w + 8f) {
            val u = x / w * 2f * PI.toFloat()
            scratch.lineTo(x, y - amp * (sin(u * f + ph) * 0.7f + sin(u * f * 2.3f + ph * 1.7f) * 0.3f))
            x += 8f
        }
        scratch.lineTo(w, h * 0.5f)
        scratch.close()
        drawPath(scratch, color)
        // A soft light along the top.
        drawPath(scratch, Color.White.copy(alpha = 0.18f), style = Stroke(2f))
    }

    private fun DrawScope.town(look: PlazaLook) {
        val haze = Color(0xFFE6F4FB)
        val walls = longArrayOf(0xFFFFD9C7, 0xFFFFEDB0, 0xFFD3E9FF, 0xFFE2D6FF, 0xFFCFF1DD, 0xFFFFE0EC)
        val roofs = longArrayOf(0xFFF0907E, 0xFF86A9EA, 0xFFA48BE3, 0xFFF4B45F, 0xFF6FC2A0, 0xFFE884A8)
        val base = h * 0.385f
        var x = -w * 0.02f
        var k = 0
        while (x < w * 1.02f) {
            val bw = w * (0.045f + 0.02f * ((k * 37) % 5) / 4f)
            val bh = h * (0.055f + 0.04f * ((k * 53) % 7) / 6f) + (if (k % 7 == 3) h * 0.04f else 0f)
            val wall = lerp(Color(walls[k % walls.size]), haze, 0.28f)
            val roof = lerp(Color(roofs[(k * 5) % roofs.size]), haze, 0.3f)
            val edge = lerp(roof, Color(0xFF3B3150), 0.25f).copy(alpha = 0.55f)
            val top = base - bh
            drawRoundRect(wall, Offset(x, top), Size(bw, bh + 4f), CornerRadius(bw * 0.08f))
            drawRect(Color.Black.copy(alpha = 0.06f), Offset(x + bw * 0.78f, top), Size(bw * 0.22f, bh + 4f))
            // Roofs of a few kinds: pointed, round, flat with a little awning.
            scratch.reset()
            when (k % 3) {
                0 -> { scratch.moveTo(x - bw * 0.08f, top + 1f); scratch.lineTo(x + bw / 2f, top - bw * 0.5f); scratch.lineTo(x + bw * 1.08f, top + 1f); scratch.close() }
                1 -> { scratch.moveTo(x - bw * 0.04f, top + 1f); scratch.cubicTo(x, top - bw * 0.55f, x + bw, top - bw * 0.55f, x + bw * 1.04f, top + 1f); scratch.close() }
                else -> scratch.addRoundRect(androidx.compose.ui.geometry.RoundRect(x - bw * 0.06f, top - bw * 0.14f, x + bw * 1.06f, top + bw * 0.06f, CornerRadius(bw * 0.06f)))
            }
            drawPath(scratch, roof)
            drawPath(scratch, edge, style = Stroke(1.6f, join = StrokeJoin.Round))
            // Windows in neat pairs (lit after dark), and a door now and then.
            val rows = if (bh > h * 0.075f) 2 else 1
            for (r in 0 until rows) for (c in 0 until 2) {
                val wx = x + bw * (0.2f + c * 0.38f)
                val wy = top + bh * (0.18f + r * 0.38f)
                val ww = bw * 0.22f
                val wh = bh * 0.2f
                drawRoundRect(lerp(Color(0xFF7F8FB8), haze, 0.35f), Offset(wx, wy), Size(ww, wh), CornerRadius(ww * 0.3f))
                drawRect(Color.White.copy(alpha = 0.35f), Offset(wx + ww * 0.15f, wy + wh * 0.15f), Size(ww * 0.2f, wh * 0.5f))
                if (windowCount < windows.size / 4 && (k + r + c) % 3 != 0) {
                    windows[windowCount * 4] = wx; windows[windowCount * 4 + 1] = wy
                    windows[windowCount * 4 + 2] = ww; windows[windowCount * 4 + 3] = wh
                    windowCount++
                }
            }
            if (k % 3 == 2) {
                // An awning in one of the theme's colours, striped.
                val aw = lerp(look.accents[k % look.accents.size], haze, 0.2f)
                val ay = top + bh * 0.64f
                for (s in 0 until 4) drawRect(if (s % 2 == 0) aw else Color.White.copy(alpha = 0.9f), Offset(x + bw * (0.08f + s * 0.21f), ay), Size(bw * 0.21f, bh * 0.1f))
            }
            if (k % 7 == 3) {
                // A clock tower: a round face high up.
                drawCircle(Color(0xFFFFFBF0), bw * 0.2f, Offset(x + bw / 2f, top + bh * 0.16f))
                drawCircle(edge, bw * 0.2f, Offset(x + bw / 2f, top + bh * 0.16f), style = Stroke(1.4f))
                drawLine(edge, Offset(x + bw / 2f, top + bh * 0.16f), Offset(x + bw / 2f, top + bh * 0.16f - bw * 0.13f), 1.4f)
                drawLine(edge, Offset(x + bw / 2f, top + bh * 0.16f), Offset(x + bw / 2f + bw * 0.1f, top + bh * 0.16f), 1.4f)
            }
            x += bw + w * 0.006f
            k++
        }
    }

    private fun DrawScope.backTrees() {
        // A hedge along the back of the square, round trees standing in it, and taller ones at the corners.
        val hedgeTop = h * 0.375f
        val hedgeBottom = sy(HEDGE_Z)
        val hedge = Color(0xFF7CC77E)
        scratch.reset()
        scratch.moveTo(0f, hedgeBottom)
        var x = 0f
        val bump = w * 0.022f
        scratch.lineTo(0f, hedgeTop + bump * 0.4f)
        while (x < w) {
            scratch.quadraticTo(x + bump, hedgeTop - bump * 0.55f, x + bump * 2f, hedgeTop + bump * 0.25f)
            x += bump * 2f
        }
        scratch.lineTo(w, hedgeBottom)
        scratch.close()
        // Trees behind the hedge, in a rhythm, a little hazy.
        for (i in 0 until 9) {
            val tx = w * (0.06f + i * 0.112f) + (if (i % 2 == 0) w * 0.01f else -w * 0.012f)
            if (abs(tx - w / 2f) < w * 0.1f) continue
            val s = h * (0.075f + 0.018f * ((i * 3) % 4))
            puffTree(tx, hedgeTop + h * 0.01f, s, lerp(Color(0xFF79C47C), Color(0xFFDFF2EC), 0.22f), haze = true)
        }
        drawPath(scratch, hedge)
        drawPath(scratch, lerp(hedge, Color(0xFF2E5E3A), 0.35f), style = Stroke(2f))
        drawRect(Color(0xFF5FAE69), Offset(0f, hedgeBottom - h * 0.012f), Size(w, h * 0.012f))
        // Little flowers along the hedge.
        for (i in 0 until 26) {
            val fx = w * (i + 0.5f) / 26f
            val fy = hedgeTop + h * (0.016f + 0.008f * ((i * 7) % 3))
            val c = if (i % 3 == 0) Color(0xFFFFF4F7) else if (i % 3 == 1) Color(0xFFFFB3C8) else Color(0xFFFFE08A)
            drawCircle(c, h * 0.0045f, Offset(fx, fy))
        }
        // The big corner trees frame the square.
        puffTree(w * 0.035f, sy(-0.2f), h * 0.2f, Color(0xFF6CC07A), haze = false)
        puffTree(w * 0.965f, sy(-0.24f), h * 0.21f, Color(0xFF6CC07A), haze = false)
    }

    /** A round, puffy tree [s] tall-ish, its trunk at ([x], [base]). */
    private fun DrawScope.puffTree(x: Float, base: Float, s: Float, leaf: Color, haze: Boolean) {
        val trunk = if (haze) Color(0xFFB99A84) else Color(0xFFA9714B)
        val edge = lerp(leaf, Color(0xFF1E4A2C), 0.45f)
        if (!haze) {
            withTransform({ translate(x, base); scale(s * 0.55f, s * 0.11f, Offset.Zero) }) { drawCircle(shadowBrush, 1f, Offset.Zero) }
        }
        drawRoundRect(trunk.copy(), Offset(x - s * 0.06f, base - s * 0.42f), Size(s * 0.12f, s * 0.42f), CornerRadius(s * 0.05f))
        drawRoundRect(lerp(trunk, Color.Black, 0.3f), Offset(x - s * 0.06f, base - s * 0.42f), Size(s * 0.12f, s * 0.42f), CornerRadius(s * 0.05f), style = Stroke(max(1.2f, s * 0.012f)))
        val cy = base - s * 0.72f
        val dark = lerp(leaf, Color(0xFF235236), 0.22f)
        val light = lerp(leaf, Color(0xFFF4FFD8), 0.35f)
        val lw = max(1.4f, s * 0.014f)
        // Outline first (a little larger), then the crown in three tones.
        drawCircle(edge, s * 0.33f + lw, Offset(x - s * 0.2f, cy + s * 0.08f))
        drawCircle(edge, s * 0.36f + lw, Offset(x + s * 0.18f, cy + s * 0.05f))
        drawCircle(edge, s * 0.37f + lw, Offset(x, cy - s * 0.18f))
        drawCircle(dark, s * 0.33f, Offset(x - s * 0.2f, cy + s * 0.08f))
        drawCircle(dark, s * 0.36f, Offset(x + s * 0.18f, cy + s * 0.05f))
        drawCircle(leaf, s * 0.37f, Offset(x, cy - s * 0.18f))
        drawCircle(leaf, s * 0.26f, Offset(x - s * 0.16f, cy))
        drawCircle(light, s * 0.17f, Offset(x - s * 0.1f, cy - s * 0.3f))
        drawCircle(light.copy(alpha = 0.7f), s * 0.09f, Offset(x - s * 0.27f, cy - s * 0.02f))
    }

    private fun DrawScope.floor() {
        val top = sy(HEDGE_Z)
        // Warm stones, a touch lighter towards the middle of the square.
        drawRect(Brush.verticalGradient(listOf(Color(0xFFEFDCC4), Color(0xFFF6E9D6), Color(0xFFF2E0C8)), top, h), Offset(0f, top), Size(w, h - top))
        // Rings of paving round the fountain, alternately tinted, with joints staggered ring to ring.
        val rings = 9
        val ringW = 0.14f
        for (k in rings downTo 1) {
            val r = FOUNTAIN_R_DRAW + 0.07f + k * ringW
            ellipseOnGround(scratch, r)
            val tone = when {
                k == rings -> Color(0xFFE5CDAE)
                k % 2 == 0 -> Color(0xFFF7EBDA)
                else -> Color(0xFFF1E1CB)
            }
            drawPath(scratch, tone)
            drawPath(scratch, Color(0xFFD9C0A0).copy(alpha = 0.75f), style = Stroke(1.6f))
        }
        // A ring of rosy tiles right round the fountain.
        ellipseOnGround(scratch, FOUNTAIN_R_DRAW + 0.07f)
        drawPath(scratch, Color(0xFFF0C3A8))
        drawPath(scratch, Color(0xFFD69C82).copy(alpha = 0.8f), style = Stroke(1.6f))
        // Joints.
        scratch.reset()
        for (k in 1..rings) {
            val r0 = FOUNTAIN_R_DRAW + 0.07f + (k - 1) * ringW
            val r1 = r0 + ringW
            val n = 10 + k * 5
            for (j in 0 until n) {
                val a = (j + (if (k % 2 == 0) 0.5f else 0f)) / n * 2f * PI.toFloat()
                val ca = cos(a)
                val sa = sin(a)
                val z0 = min(FOUNTAIN_Z + sa * r0 / DEPTH, Z_GROUND_MAX)
                val z1 = min(FOUNTAIN_Z + sa * r1 / DEPTH, Z_GROUND_MAX)
                if (z1 < HEDGE_Z && z0 < HEDGE_Z) continue
                scratch.moveTo(sx(ca * r0, max(z0, HEDGE_Z)), sy(max(z0, HEDGE_Z)))
                scratch.lineTo(sx(ca * r1, max(z1, HEDGE_Z)), sy(max(z1, HEDGE_Z)))
            }
        }
        drawPath(scratch, Color(0xFFD9C0A0).copy(alpha = 0.6f), style = Stroke(1.3f))
        // A sunlit sheen in the middle of the square.
        withTransform({ translate(w * 0.5f, sy(0.55f)); scale(w * 0.42f, h * 0.16f, Offset.Zero) }) {
            drawCircle(whiteGlow, 1f, Offset.Zero, alpha = 0.32f)
        }
    }

    /** A circle of ground radius [r] round the fountain, as seen (clipped to the hedge at the back). */
    private fun ellipseOnGround(p: Path, r: Float) {
        p.reset()
        val n = 72
        for (i in 0..n) {
            val a = i.toFloat() / n * 2f * PI.toFloat()
            val z = (FOUNTAIN_Z + sin(a) * r / DEPTH).coerceIn(HEDGE_Z, Z_GROUND_MAX)
            val x = FOUNTAIN_X + cos(a) * r
            if (i == 0) p.moveTo(sx(x, z), sy(z)) else p.lineTo(sx(x, z), sy(z))
        }
        p.close()
    }

    private fun DrawScope.benches() {
        val b = PlazaWorld.BENCHES
        for (i in 0 until b.size / 2) {
            val z = b[i * 2 + 1]
            val cx = sx(b[i * 2], z)
            val y = sy(z)
            val s = tall(z)
            val bw = PlazaWorld.BENCH_W * w * 0.5f * scale(z)
            val wood = Color(0xFFD69A64)
            val woodDark = Color(0xFFAE6F42)
            val iron = Color(0xFF5E566F)
            val line = Color(0xFF6A4128)
            // Shadow, legs, the backrest's two slats, the seat.
            withTransform({ translate(cx, y + s * 0.01f); scale(bw * 0.62f, s * 0.05f, Offset.Zero) }) { drawCircle(shadowBrush, 1f, Offset.Zero) }
            val seatY = y - s * 0.1f
            for (side in floatArrayOf(-1f, 1f)) {
                drawRoundRect(iron, Offset(cx + side * bw * 0.4f - s * 0.012f, seatY - s * 0.16f), Size(s * 0.024f, s * 0.26f), CornerRadius(s * 0.01f))
            }
            for (r in 0 until 2) {
                val ry = seatY - s * (0.15f - r * 0.06f)
                drawRoundRect(wood, Offset(cx - bw / 2f, ry), Size(bw, s * 0.042f), CornerRadius(s * 0.015f))
                drawRoundRect(line, Offset(cx - bw / 2f, ry), Size(bw, s * 0.042f), CornerRadius(s * 0.015f), style = Stroke(1.5f))
            }
            drawRoundRect(woodDark, Offset(cx - bw * 0.52f, seatY), Size(bw * 1.04f, s * 0.05f), CornerRadius(s * 0.015f))
            drawRoundRect(wood, Offset(cx - bw * 0.52f, seatY - s * 0.012f), Size(bw * 1.04f, s * 0.04f), CornerRadius(s * 0.015f))
            drawRoundRect(line, Offset(cx - bw * 0.52f, seatY - s * 0.012f), Size(bw * 1.04f, s * 0.062f), CornerRadius(s * 0.015f), style = Stroke(1.5f))
            drawRect(Color.White.copy(alpha = 0.25f), Offset(cx - bw * 0.48f, seatY - s * 0.006f), Size(bw * 0.96f, s * 0.008f))
        }
    }

    /** Two old-fashioned lamps either side of the fountain, each with a little banner in a theme colour. */
    private fun DrawScope.lampPosts(look: PlazaLook) {
        var n = 0
        for (side in SIDES) {
            val z = -0.2f
            val x = sx(side * 0.47f, z)
            val y = sy(z)
            val s = tall(z)
            val post = Color(0xFF5C5677)
            val edge = Color(0xFF3B3650)
            val lw = max(1.5f, s * 0.011f)
            val ph = s * 0.98f
            withTransform({ translate(x, y); scale(s * 0.16f, s * 0.035f, Offset.Zero) }) { drawCircle(shadowBrush, 1f, Offset.Zero) }
            // A stepped base, the post with a light down one side, a collar under the lantern.
            drawRoundRect(post, Offset(x - s * 0.06f, y - s * 0.07f), Size(s * 0.12f, s * 0.08f), CornerRadius(s * 0.025f))
            drawRoundRect(edge, Offset(x - s * 0.06f, y - s * 0.07f), Size(s * 0.12f, s * 0.08f), CornerRadius(s * 0.025f), style = Stroke(lw))
            drawRoundRect(post, Offset(x - s * 0.042f, y - s * 0.12f), Size(s * 0.084f, s * 0.06f), CornerRadius(s * 0.02f))
            drawRoundRect(post, Offset(x - s * 0.024f, y - ph), Size(s * 0.048f, ph - s * 0.1f), CornerRadius(s * 0.02f))
            drawRoundRect(edge, Offset(x - s * 0.024f, y - ph), Size(s * 0.048f, ph - s * 0.1f), CornerRadius(s * 0.02f), style = Stroke(lw))
            drawRoundRect(Color.White.copy(alpha = 0.22f), Offset(x - s * 0.014f, y - ph + s * 0.02f), Size(s * 0.01f, ph - s * 0.16f), CornerRadius(s * 0.005f))
            drawRoundRect(post, Offset(x - s * 0.045f, y - ph - s * 0.01f), Size(s * 0.09f, s * 0.03f), CornerRadius(s * 0.012f))
            // The lantern: a glass with bars, a roof, a knob.
            val gx = x - s * 0.065f
            val gy = y - ph - s * 0.15f
            val gw = s * 0.13f
            val gh = s * 0.14f
            drawRoundRect(Color(0xFFFFF4D2), Offset(gx, gy), Size(gw, gh), CornerRadius(s * 0.03f))
            drawRoundRect(Color(0xFFFFE08A).copy(alpha = 0.6f), Offset(gx + gw * 0.2f, gy + gh * 0.15f), Size(gw * 0.6f, gh * 0.7f), CornerRadius(s * 0.02f))
            drawLine(edge, Offset(x, gy), Offset(x, gy + gh), lw * 0.9f)
            drawRoundRect(edge, Offset(gx, gy), Size(gw, gh), CornerRadius(s * 0.03f), style = Stroke(lw * 1.1f))
            scratch.reset()
            scratch.moveTo(gx - s * 0.03f, gy + s * 0.005f)
            scratch.quadraticTo(x, gy - s * 0.1f, gx + gw + s * 0.03f, gy + s * 0.005f)
            scratch.close()
            drawPath(scratch, post)
            drawPath(scratch, edge, style = Stroke(lw, join = StrokeJoin.Round))
            drawCircle(post, s * 0.022f, Offset(x, gy - s * 0.065f))
            drawCircle(edge, s * 0.022f, Offset(x, gy - s * 0.065f), style = Stroke(lw))
            // The banner hangs from an arm, away from the fountain.
            val flag = look.accents[(n * 2 + 1) % look.accents.size]
            val ax = x + side * s * 0.15f
            val top = y - ph * 0.8f
            drawLine(edge, Offset(x, top), Offset(ax + side * s * 0.01f, top), lw * 1.3f, StrokeCap.Round)
            scratch.reset()
            scratch.moveTo(x + side * s * 0.03f, top)
            scratch.lineTo(ax, top)
            scratch.lineTo(ax, top + s * 0.26f)
            scratch.lineTo((x + side * s * 0.03f + ax) / 2f, top + s * 0.21f)
            scratch.lineTo(x + side * s * 0.03f, top + s * 0.26f)
            scratch.close()
            drawPath(scratch, flag)
            drawPath(scratch, lerp(flag, edge, 0.55f), style = Stroke(lw, join = StrokeJoin.Round))
            drawCircle(Color.White.copy(alpha = 0.85f), s * 0.022f, Offset((x + side * s * 0.03f + ax) / 2f, top + s * 0.1f))
            lamps[n * 3] = x; lamps[n * 3 + 1] = gy + gh * 0.5f; lamps[n * 3 + 2] = s
            lampGround[n] = y
            n++
        }
    }


    private fun DrawScope.vignette() {
        // Soft corners, and the front darkened a little under the strip of cards.
        drawRect(
            Brush.radialGradient(0.55f to Color.Transparent, 1f to Color(0xFF5A3F35).copy(alpha = 0.22f), center = Offset(w / 2f, h * 0.48f), radius = w * 0.62f),
            Offset.Zero, size,
        )
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color(0xFF4A3A40).copy(alpha = 0.16f)), h * 0.72f, h), Offset(0f, h * 0.72f), Size(w, h * 0.28f))
    }

    // --- The fountain ----------------------------------------------------------------------------------------------

    private fun DrawScope.fountain(t: Float, still: Boolean) {
        val cx = sx(FOUNTAIN_X, FOUNTAIN_Z)
        val yBack = sy(FOUNTAIN_Z - FOUNTAIN_BACK)
        val yFront = sy(FOUNTAIN_Z + FOUNTAIN_BACK)
        val cy = (yBack + yFront) / 2f
        val rx = PlazaWorld.FOUNTAIN_R * w * 0.5f * scale(FOUNTAIN_Z)
        val ry = (yFront - yBack) / 2f
        val wall = h * 0.04f
        val stone = Color(0xFFF6EEE4)
        val stoneSide = Color(0xFFE2D3C2)
        val stoneEdge = Color(0xFFA89079)
        val lw = max(1.6f, h * 0.0022f)

        // Shadow, then the basin's wall (its front), the rim, and the water inside.
        withTransform({ translate(cx, cy + wall * 0.6f); scale(rx * 1.12f, ry * 1.25f, Offset.Zero) }) { drawCircle(shadowBrush, 1f, Offset.Zero) }
        drawOval(stoneSide, Offset(cx - rx, cy - ry + wall), Size(rx * 2f, ry * 2f))
        drawRect(stoneSide, Offset(cx - rx, cy), Size(rx * 2f, wall))
        drawOval(stoneEdge, Offset(cx - rx, cy - ry + wall), Size(rx * 2f, ry * 2f), style = Stroke(lw))
        drawLine(stoneEdge, Offset(cx - rx, cy), Offset(cx - rx, cy + wall), lw)
        drawLine(stoneEdge, Offset(cx + rx, cy), Offset(cx + rx, cy + wall), lw)
        // Stone blocks along the wall.
        for (i in 1 until 8) {
            val a = i / 8f * PI.toFloat()
            val bx = cx - cos(a) * rx
            val by = cy + sin(a) * ry
            drawLine(stoneEdge.copy(alpha = 0.35f), Offset(bx, by + 2f), Offset(bx, by + wall - 1f), lw * 0.8f)
        }
        drawOval(stone, Offset(cx - rx, cy - ry), Size(rx * 2f, ry * 2f))
        drawOval(stoneEdge, Offset(cx - rx, cy - ry), Size(rx * 2f, ry * 2f), style = Stroke(lw))
        val ix = rx * 0.88f
        val iy = ry * 0.8f
        drawOval(waterBrush, Offset(cx - ix, cy - iy + ry * 0.04f), Size(ix * 2f, iy * 2f))
        drawOval(stoneEdge.copy(alpha = 0.5f), Offset(cx - ix, cy - iy + ry * 0.04f), Size(ix * 2f, iy * 2f), style = Stroke(lw * 0.8f))
        // Light on the water.
        drawOval(Color.White.copy(alpha = 0.22f), Offset(cx - ix * 0.75f, cy - iy * 0.55f), Size(ix * 0.7f, iy * 0.35f))

        // Ripples spreading from where the water falls.
        val ripples = if (still) 2 else 3
        for (i in 0 until ripples) {
            val p = if (still) 0.35f + i * 0.3f else ((t / 2.2f) + i / 3f) % 1f
            val rr = 0.25f + 0.7f * p
            drawOval(Color.White.copy(alpha = 0.5f * (1f - p)), Offset(cx - ix * rr, cy + ry * 0.04f - iy * rr * 0.9f), Size(ix * rr * 2f, iy * rr * 1.8f), style = Stroke(lw * 1.1f))
        }

        // Arcs of water from four spouts on the rim, leaping in towards the middle.
        val water = Color(0xFFDDF6FF)
        val jetTop = cy - h * 0.06f
        scratch.reset()
        for (i in 0 until 4) {
            val a = (0.18f + i * 0.21f + (if (i >= 2) 0.06f else 0f)) * PI.toFloat() * (if (i % 2 == 0) 1f else 1f) + (if (i >= 2) PI.toFloat() * 0.5f else 0f)
            val sxp = cx + cos(a) * ix * 0.98f
            val syp = cy + sin(a) * iy * 0.98f
            val ex = cx + cos(a) * ix * 0.32f
            val ey = cy + sin(a) * iy * 0.32f
            scratch.moveTo(sxp, syp)
            scratch.quadraticTo((sxp + ex) / 2f, min(syp, ey) - h * 0.055f, ex, ey)
        }
        drawPath(scratch, water.copy(alpha = 0.75f), style = Stroke(lw * 1.6f, cap = StrokeCap.Round))
        drawPath(scratch, Color.White.copy(alpha = 0.9f), style = Stroke(lw * 0.6f, cap = StrokeCap.Round))

        // The column, the upper bowl, and its little top.
        val colW = rx * 0.1f
        val bowlY = cy - h * 0.12f
        drawOval(stoneSide, Offset(cx - colW * 1.9f, cy - ry * 0.18f), Size(colW * 3.8f, ry * 0.36f))
        drawRoundRect(stoneSide, Offset(cx - colW, bowlY), Size(colW * 2f, cy - bowlY), CornerRadius(colW * 0.4f))
        drawRoundRect(Color.White.copy(alpha = 0.35f), Offset(cx - colW * 0.6f, bowlY), Size(colW * 0.35f, cy - bowlY), CornerRadius(colW * 0.2f))
        drawRoundRect(stoneEdge, Offset(cx - colW, bowlY), Size(colW * 2f, cy - bowlY), CornerRadius(colW * 0.4f), style = Stroke(lw))
        drawRoundRect(stone, Offset(cx - colW * 1.35f, bowlY + (cy - bowlY) * 0.55f), Size(colW * 2.7f, h * 0.012f), CornerRadius(h * 0.006f))
        drawRoundRect(stoneEdge, Offset(cx - colW * 1.35f, bowlY + (cy - bowlY) * 0.55f), Size(colW * 2.7f, h * 0.012f), CornerRadius(h * 0.006f), style = Stroke(lw * 0.8f))
        val bx = rx * 0.42f
        val by = bx * (ry / rx)
        bowl.reset()
        bowl.moveTo(cx - bx, bowlY)
        bowl.cubicTo(cx - bx, bowlY + by * 2.4f, cx + bx, bowlY + by * 2.4f, cx + bx, bowlY)
        bowl.close()
        // Water spilling over the bowl's edge in thin sheets.
        val sheet = Color(0xFFBFEAF7)
        for (side in SIDES) {
            val ex = cx + side * bx * 0.96f
            drawLine(sheet.copy(alpha = 0.55f), Offset(ex, bowlY + by * 0.2f), Offset(ex + side * bx * 0.08f, cy - iy * 0.2f), lw * 2.6f, StrokeCap.Round)
            drawLine(Color.White.copy(alpha = 0.7f), Offset(ex, bowlY + by * 0.2f), Offset(ex + side * bx * 0.08f, cy - iy * 0.2f), lw * 0.9f, StrokeCap.Round)
        }
        drawPath(bowl, stoneSide)
        drawPath(bowl, stoneEdge, style = Stroke(lw))
        drawOval(stone, Offset(cx - bx, bowlY - by), Size(bx * 2f, by * 2f))
        drawOval(stoneEdge, Offset(cx - bx, bowlY - by), Size(bx * 2f, by * 2f), style = Stroke(lw))
        drawOval(waterBrush, Offset(cx - bx * 0.84f, bowlY - by * 0.72f), Size(bx * 1.68f, by * 1.44f))
        // The spout's little cap.
        val capY = bowlY - h * 0.03f
        drawRoundRect(stoneSide, Offset(cx - colW * 0.45f, capY), Size(colW * 0.9f, bowlY - capY), CornerRadius(colW * 0.3f))
        drawRoundRect(stoneEdge, Offset(cx - colW * 0.45f, capY), Size(colW * 0.9f, bowlY - capY), CornerRadius(colW * 0.3f), style = Stroke(lw * 0.8f))
        drawCircle(stone, colW * 0.6f, Offset(cx, capY))
        drawCircle(stoneEdge, colW * 0.6f, Offset(cx, capY), style = Stroke(lw * 0.8f))

        // The jet: up from the cap and falling back in a soft plume.
        val plume = h * 0.07f
        scratch.reset()
        for (side in SIDES) for (k in 0 until 2) {
            val spread = bx * (0.45f + k * 0.3f)
            scratch.moveTo(cx, capY - colW * 0.5f)
            scratch.cubicTo(cx + side * spread * 0.1f, capY - plume * (1.3f - k * 0.25f), cx + side * spread * 0.8f, capY - plume * (1.1f - k * 0.2f), cx + side * spread, bowlY - by * 0.2f)
        }
        drawPath(scratch, water.copy(alpha = 0.6f), style = Stroke(lw * 2.2f, cap = StrokeCap.Round))
        drawPath(scratch, Color.White.copy(alpha = 0.85f), style = Stroke(lw * 0.8f, cap = StrokeCap.Round))
        drawLine(Color.White.copy(alpha = 0.85f), Offset(cx, capY - colW * 0.5f), Offset(cx, capY - plume * 0.95f), lw * 2.2f, StrokeCap.Round)

        if (still) return
        // Drops: along the arcs, down the sheets and off the plume, each on its own beat.
        for (i in 0 until 4) {
            val a = (0.18f + i * 0.21f + (if (i >= 2) 0.06f else 0f)) * PI.toFloat() + (if (i >= 2) PI.toFloat() * 0.5f else 0f)
            val sxp = cx + cos(a) * ix * 0.98f
            val syp = cy + sin(a) * iy * 0.98f
            val ex = cx + cos(a) * ix * 0.32f
            val ey = cy + sin(a) * iy * 0.32f
            val qx = (sxp + ex) / 2f
            val qy = min(syp, ey) - h * 0.055f
            for (d in 0 until 2) {
                val p = ((t * 0.9f) + i * 0.27f + d * 0.5f) % 1f
                val q = 1f - p
                val dx = q * q * sxp + 2f * q * p * qx + p * p * ex
                val dy = q * q * syp + 2f * q * p * qy + p * p * ey
                drawCircle(Color.White, lw * 1.3f, Offset(dx, dy))
            }
        }
        for (i in 0 until 6) {
            val p = ((t * 1.3f) + i / 6f) % 1f
            val side = if (i % 2 == 0) -1f else 1f
            val spread = bx * (0.5f + (i % 3) * 0.18f)
            val dx = cx + side * spread * p
            val dy = capY - plume * 1.1f * (4f * p * (1f - p)) + (bowlY - capY) * p * p
            drawCircle(Color.White.copy(alpha = 0.9f), lw * (1.4f - p * 0.5f), Offset(dx, dy))
        }
        // Glints on the water.
        for (i in 0 until 5) {
            val tw = sin(t * 3.1f + i * 2.3f)
            if (tw < 0.3f) continue
            val gx = cx + ix * (-0.7f + (i * 0.37f % 1.4f))
            val gy = cy + iy * (-0.4f + ((i * 0.53f) % 0.9f))
            sparkle(gx, gy, h * 0.009f * (tw - 0.3f) / 0.7f, Color.White)
        }
    }

    private fun DrawScope.sparkle(x: Float, y: Float, r: Float, color: Color) {
        if (r <= 0.2f) return
        withTransform({ translate(x, y); scale(r, r, Offset.Zero) }) { drawPath(star4, color) }
    }

    private val star4 = Path().also { starPathTo(it, 4, 0.32f) }

    // --- People ----------------------------------------------------------------------------------------------------

    private fun DrawScope.people(world: PlazaWorld, t: Float, still: Boolean) {
        val list = world.walkers
        for (n in 0 until world.count) {
            val wk = list[world.order[n]]
            val z = wk.z
            val fx = sx(wk.x, z)
            val fy = sy(z)
            val ch = tall(z)
            val cw = ch * 100f / 120f
            val lift = wk.hop * ch
            // The shadow on the ground: smaller and fainter while hopping, a bench's when sitting.
            val sh = 1f - (wk.hop * 4f).coerceIn(0f, 0.45f)
            withTransform({ translate(fx, fy); scale(ch * 0.2f * sh, ch * 0.045f * sh, Offset.Zero) }) { drawCircle(shadowBrush, 1f, Offset.Zero) }
            if (wk.focused) focusRing(fx, fy, ch, avatarTheme(wk.spec), t, still)
            val p = wk.pose
            p.blink = wk.blink
            p.lookX = wk.lookX
            p.lookY = 0f
            p.wave = wk.wave
            p.waveSwing = if (still) 0.3f else sin(t * 7.2f + (wk.seed and 7))
            // Stretched rising from a hop, squashed landing.
            p.squash = wk.squash + (if (wk.hopV > 0f) -0.35f else 0f)
            p.cheer = wk.cheer.coerceIn(0f, 1f)
            p.turn = wk.turn
            p.walk = wk.walk
            p.phase = wk.phase
            p.sit = wk.sit
            withTransform({ translate(fx - cw / 2f, fy - ch - lift) }) {
                drawAvatar(wk.spec, p, shadow = false, box = Size(cw, ch))
            }
        }
    }

    private fun DrawScope.focusRing(x: Float, y: Float, ch: Float, theme: Color, t: Float, still: Boolean) {
        val pulse = if (still) 0.5f else 0.5f + 0.5f * sin(t * 3.2f)
        val rx = ch * (0.27f + 0.02f * pulse)
        val ry = rx * 0.3f
        drawOval(theme.copy(alpha = 0.22f), Offset(x - rx * 1.25f, y - ry * 1.25f), Size(rx * 2.5f, ry * 2.5f))
        drawOval(theme.copy(alpha = 0.4f), Offset(x - rx, y - ry), Size(rx * 2f, ry * 2f))
        drawOval(Color.White.copy(alpha = 0.85f), Offset(x - rx, y - ry), Size(rx * 2f, ry * 2f), style = Stroke(max(2f, ch * 0.012f)))
        drawOval(lerp(theme, Color.White, 0.3f), Offset(x - rx * 0.86f, y - ry * 0.86f), Size(rx * 1.72f, ry * 1.72f), style = Stroke(max(1.4f, ch * 0.007f)))
    }

    // --- The front -------------------------------------------------------------------------------------------------

    private fun DrawScope.planters(t: Float) {
        for (side in SIDES) {
            val z = 1.0f
            val x = sx(side * 1.0f, z)
            val y = sy(z) + h * 0.03f
            val s = tall(z)
            val pw = s * 0.75f
            val ph = s * 0.3f
            withTransform({ translate(x, y); scale(pw * 0.62f, s * 0.06f, Offset.Zero) }) { drawCircle(shadowBrush, 1f, Offset.Zero) }
            // A round stone planter brimming with leaves and flowers.
            val pot = Color(0xFFF1E4D3)
            val potEdge = Color(0xFFA88E74)
            val lw = max(1.6f, s * 0.008f)
            val leaf = Color(0xFF79C77B)
            val leafDark = Color(0xFF5BAF66)
            for (i in 0 until 7) {
                val a = i / 6f
                val lx = x - pw * 0.48f + pw * 0.96f * a
                val ly = y - ph - s * (0.05f + 0.06f * sin(a * PI.toFloat()))
                drawCircle(lerp(leafDark, Color(0xFF1E4A2C), 0.3f), s * 0.11f + lw, Offset(lx, ly))
            }
            for (i in 0 until 7) {
                val a = i / 6f
                val lx = x - pw * 0.48f + pw * 0.96f * a
                val ly = y - ph - s * (0.05f + 0.06f * sin(a * PI.toFloat()))
                drawCircle(if (i % 2 == 0) leaf else leafDark, s * 0.11f, Offset(lx, ly))
            }
            val colors = PLANTER_FLOWERS
            for (i in 0 until 9) {
                val a = (i + 0.5f) / 9f
                val fx = x - pw * 0.45f + pw * 0.9f * a + sin(i * 2.1f) * s * 0.02f
                val fy = y - ph - s * (0.08f + 0.08f * sin(a * PI.toFloat())) + cos(i * 1.7f) * s * 0.02f
                val sway = sin(t * 1.3f + i) * s * 0.006f
                flower(fx + sway, fy, s * 0.038f, Color(colors[(i + (if (side > 0) 2 else 0)) % colors.size]))
            }
            drawRoundRect(pot, Offset(x - pw / 2f, y - ph), Size(pw, ph), CornerRadius(ph * 0.25f))
            drawRect(Color.Black.copy(alpha = 0.06f), Offset(x - pw / 2f, y - ph * 0.35f), Size(pw, ph * 0.35f))
            drawRoundRect(potEdge, Offset(x - pw / 2f, y - ph), Size(pw, ph), CornerRadius(ph * 0.25f), style = Stroke(lw))
            drawRoundRect(Color(0xFFFAF4EB), Offset(x - pw * 0.54f, y - ph - s * 0.02f), Size(pw * 1.08f, s * 0.05f), CornerRadius(s * 0.02f))
            drawRoundRect(potEdge, Offset(x - pw * 0.54f, y - ph - s * 0.02f), Size(pw * 1.08f, s * 0.05f), CornerRadius(s * 0.02f), style = Stroke(lw))
        }
    }

    private fun DrawScope.flower(x: Float, y: Float, r: Float, color: Color) {
        val edge = lerp(color, Color(0xFF5A2A3A), 0.35f)
        for (i in 0 until 5) {
            val a = i / 5f * 2f * PI.toFloat()
            drawCircle(edge, r * 0.62f, Offset(x + cos(a) * r * 0.7f, y + sin(a) * r * 0.7f))
        }
        for (i in 0 until 5) {
            val a = i / 5f * 2f * PI.toFloat()
            drawCircle(color, r * 0.5f, Offset(x + cos(a) * r * 0.7f, y + sin(a) * r * 0.7f))
        }
        drawCircle(Color(0xFFFFD45C), r * 0.38f, Offset(x, y))
    }

    /** Petals drifting by day, butterflies by the planters; fireflies come out after dark (in [lights]). */
    private fun DrawScope.drift(t: Float) {
        val day = 1f - tod.lights
        if (day < 0.05f) return
        for (i in 0 until 9) {
            val speed = 0.022f + 0.008f * (i % 3)
            val p = (t * speed + i * 0.113f) % 1f
            val x = ((i * 0.31f + p * 0.55f) % 1.1f) * w + sin(t * 0.9f + i * 1.3f) * w * 0.012f
            val y = h * (0.1f + p * 0.75f)
            val r = h * 0.0058f
            val spin = t * (40f + i * 13f) + i * 50f
            val c = if (i % 3 == 0) Color(0xFFFFFFFF) else Color(0xFFFFC2D4)
            withTransform({ translate(x, y); rotate(spin, Offset.Zero) }) {
                drawOval(c.copy(alpha = 0.85f * day), Offset(-r * 1.4f, -r * 0.7f), Size(r * 2.8f, r * 1.4f))
            }
        }
        for (i in 0 until 2) {
            val side = if (i == 0) -1f else 1f
            val ax = sx(side * 0.88f, 0.92f) + sin(t * 0.7f + i * 3f) * w * 0.04f
            val ay = sy(0.92f) - h * 0.15f + sin(t * 1.3f + i) * h * 0.035f
            val flap = abs(sin(t * 14f + i * 2f))
            val s = h * 0.011f
            val c = if (i == 0) Color(0xFFFFB347) else Color(0xFF9EC9FF)
            val edge = lerp(c, Color(0xFF3A2A40), 0.45f)
            for (wing in SIDES) {
                drawOval(edge, Offset(ax + wing * s * (0.15f + 0.9f * flap) - s * 0.55f * flap - s * 0.06f, ay - s * 1.1f), Size(s * (0.2f + 1.1f * flap) + s * 0.12f, s * 1.32f), alpha = day)
                drawOval(c, Offset(ax + wing * s * (0.15f + 0.9f * flap) - s * 0.55f * flap, ay - s), Size(s * (0.2f + 1.1f * flap), s * 1.2f), alpha = day)
            }
            drawLine(edge, Offset(ax, ay - s * 0.9f), Offset(ax, ay + s * 0.4f), s * 0.3f, StrokeCap.Round, alpha = day)
        }
    }

    // --- Light after dark ------------------------------------------------------------------------------------------

    private fun DrawScope.stars(t: Float, still: Boolean) {
        if (tod.stars <= 0.01f) return
        for (i in 0 until stars.size / 3) {
            val tw = if (still) 0.8f else 0.6f + 0.4f * sin(t * (1.2f + (i % 5) * 0.3f) + i)
            drawCircle(Color.White.copy(alpha = tod.stars * tw * 0.9f), stars[i * 3 + 2], Offset(stars[i * 3], stars[i * 3 + 1]))
        }
        // The moon, low over the town, clear of the header.
        val mx = w * 0.64f
        val my = h * 0.2f
        val mr = h * 0.03f
        withTransform({ translate(mx, my); scale(mr * 3.5f, mr * 3.5f, Offset.Zero) }) {
            drawCircle(whiteGlow, 1f, Offset.Zero, alpha = 0.28f * tod.stars)
        }
        withTransform({ translate(mx, my); scale(mr, mr, Offset.Zero) }) {
            drawPath(crescent, Color(0xFFFFF4D6).copy(alpha = tod.stars))
        }
    }

    private fun DrawScope.lights(t: Float, still: Boolean) {
        val l = tod.lights
        if (l < 0.01f) return
        for (i in 0 until lamps.size / 3) {
            val x = lamps[i * 3]
            val y = lamps[i * 3 + 1]
            val s = lamps[i * 3 + 2]
            val flick = if (still) 1f else 0.95f + 0.05f * sin(t * 2.3f + i * 1.7f)
            withTransform({ translate(x, y); scale(s * 0.6f * flick, s * 0.6f * flick, Offset.Zero) }) {
                drawCircle(glowBrush, 1f, Offset.Zero, alpha = l, blendMode = BlendMode.Plus)
            }
            drawRoundRect(Color(0xFFFFF0B8).copy(alpha = l), Offset(x - s * 0.05f, y - s * 0.06f), Size(s * 0.1f, s * 0.11f), CornerRadius(s * 0.03f))
            // A pool of light on the ground below.
            withTransform({ translate(x, lampGround[i]); scale(s * 0.7f, s * 0.14f, Offset.Zero) }) {
                drawCircle(glowBrush, 1f, Offset.Zero, alpha = 0.45f * l, blendMode = BlendMode.Plus)
            }
        }
        if (!still) {
            // Fireflies.
            for (i in 0 until 12) {
                val fx = w * ((i * 0.137f + sin(t * 0.21f + i) * 0.04f + 1f) % 1f)
                val fy = h * (0.42f + 0.1f * ((i * 7) % 5) / 4f) + sin(t * 0.8f + i * 2f) * h * 0.02f
                val a = (0.5f + 0.5f * sin(t * 2.4f + i * 1.9f)).let { it * it } * l
                withTransform({ translate(fx, fy); scale(h * 0.012f, h * 0.012f, Offset.Zero) }) {
                    drawCircle(glowBrush, 1f, Offset.Zero, alpha = a, blendMode = BlendMode.Plus)
                }
                drawCircle(Color(0xFFFFFBD0).copy(alpha = a), h * 0.0022f, Offset(fx, fy))
            }
        }
    }

    // --- Bubbles and the marker ------------------------------------------------------------------------------------

    private fun DrawScope.overlays(world: PlazaWorld, t: Float, still: Boolean) {
        val list = world.walkers
        for (n in 0 until world.count) {
            val wk = list[world.order[n]]
            val ch = tall(wk.z)
            val fx = sx(wk.x, wk.z)
            val top = sy(wk.z) - ch * (1.0f - 0.06f * wk.sit) - wk.hop * ch
            if (wk.emote >= 0 && wk.emoteAge >= 0f) bubble(wk, fx, top, ch)
            if (wk.focused) {
                val bob = if (still) 0f else sin(t * 4.2f) * ch * 0.025f
                val s = ch * 0.085f
                val tipY = top - ch * 0.04f + bob
                val theme = avatarTheme(wk.spec)
                withTransform({ translate(fx, tipY); scale(s, s, Offset.Zero) }) {
                    translate(0f, 0.18f) { drawPath(marker, Color.Black.copy(alpha = 0.18f)) }
                    drawPath(marker, Color.White)
                }
                withTransform({ translate(fx, tipY - s * 0.32f); scale(s * 0.72f, s * 0.72f, Offset.Zero) }) {
                    drawPath(marker, theme)
                    drawCircle(Color.White.copy(alpha = 0.55f), 0.32f, Offset(-0.35f, -1.75f))
                }
            } else if (wk.fresh && wk.act != PlazaWorld.Act.Chat && !still) {
                // Someone new: a little twinkle over their head.
                val tw = 0.6f + 0.4f * sin(t * 4f + wk.seed)
                sparkle(fx + ch * 0.2f, top + ch * 0.02f, ch * 0.04f * tw, Color(0xFFFFD45C))
                sparkle(fx + ch * 0.29f, top + ch * 0.09f, ch * 0.022f * tw, Color.White)
            }
        }
    }

    private fun DrawScope.bubble(wk: PlazaWorld.Walker, fx: Float, top: Float, ch: Float) {
        val age = wk.emoteAge
        val pop = easeOutBack((age / 0.28f).coerceIn(0f, 1f))
        val fade = (wk.emoteLeft / 0.25f).coerceIn(0f, 1f)
        if (pop <= 0.01f || fade <= 0.01f) return
        // Off to the side the speaker faces, so a pair's bubbles don't cover each other.
        val side = if (wk.turn >= 0f) 1f else -1f
        val r = ch * 0.13f * pop
        val bx = fx + side * ch * 0.24f
        val by = top - ch * 0.02f - r
        val a = fade
        drawCircle(Color.Black.copy(alpha = 0.12f * a), r * 1.05f, Offset(bx, by + r * 0.12f))
        scratch.reset()
        scratch.moveTo(bx - side * r * 0.45f, by + r * 0.7f)
        scratch.lineTo(fx + side * ch * 0.08f, top + ch * 0.04f)
        scratch.lineTo(bx - side * r * 0.05f, by + r * 0.92f)
        scratch.close()
        drawPath(scratch, Color.White.copy(alpha = a))
        drawRoundRect(Color.White.copy(alpha = a), Offset(bx - r * 1.1f, by - r * 0.9f), Size(r * 2.2f, r * 1.8f), CornerRadius(r * 0.85f))
        drawRoundRect(Color(0xFFCCD3E0).copy(alpha = a), Offset(bx - r * 1.1f, by - r * 0.9f), Size(r * 2.2f, r * 1.8f), CornerRadius(r * 0.85f), style = Stroke(max(1.2f, r * 0.06f)))
        emoteIcon(wk.emote, bx, by, r * 0.62f, a, wk)
    }

    private fun DrawScope.emoteIcon(kind: Int, x: Float, y: Float, s: Float, a: Float, wk: PlazaWorld.Walker) {
        when (kind) {
            PlazaWorld.Emote.Heart -> withTransform({ translate(x, y + s * 0.1f); scale(s, s, Offset.Zero) }) { drawPath(heart, Color(0xFFFF6F91).copy(alpha = a)) }
            PlazaWorld.Emote.Star -> withTransform({ translate(x, y + s * 0.08f); scale(s * 1.05f, s * 1.05f, Offset.Zero) }) { drawPath(star5, Color(0xFFFFC23C).copy(alpha = a)) }
            PlazaWorld.Emote.Note -> {
                val c = Color(0xFF7C8CFF).copy(alpha = a)
                drawOval(c, Offset(x - s * 0.75f, y + s * 0.25f), Size(s * 0.7f, s * 0.52f))
                drawOval(c, Offset(x + s * 0.1f, y + s * 0.05f), Size(s * 0.7f, s * 0.52f))
                drawLine(c, Offset(x - s * 0.1f, y + s * 0.5f), Offset(x - s * 0.1f, y - s * 0.7f), s * 0.16f)
                drawLine(c, Offset(x + s * 0.75f, y + s * 0.3f), Offset(x + s * 0.75f, y - s * 0.9f), s * 0.16f)
                drawLine(c, Offset(x - s * 0.1f, y - s * 0.66f), Offset(x + s * 0.75f, y - s * 0.86f), s * 0.3f)
            }
            PlazaWorld.Emote.Dots -> for (i in -1..1) {
                val hop = sin(wk.emoteAge * 8f - i * 0.9f).coerceAtLeast(0f) * s * 0.2f
                drawCircle(Color(0xFF8A90A6).copy(alpha = a), s * 0.2f, Offset(x + i * s * 0.6f, y - hop))
            }
            PlazaWorld.Emote.Bang -> {
                val c = Color(0xFFFF8A3D).copy(alpha = a)
                drawRoundRect(c, Offset(x - s * 0.16f, y - s * 0.85f), Size(s * 0.32f, s * 1.1f), CornerRadius(s * 0.16f))
                drawCircle(c, s * 0.18f, Offset(x, y + s * 0.62f))
            }
            PlazaWorld.Emote.Pad -> {
                val c = Color(0xFF5DBB8A).copy(alpha = a)
                drawRoundRect(c, Offset(x - s * 0.95f, y - s * 0.5f), Size(s * 1.9f, s * 1.0f), CornerRadius(s * 0.45f))
                drawRect(Color.White.copy(alpha = a), Offset(x - s * 0.66f, y - s * 0.07f), Size(s * 0.44f, s * 0.14f))
                drawRect(Color.White.copy(alpha = a), Offset(x - s * 0.51f, y - s * 0.22f), Size(s * 0.14f, s * 0.44f))
                drawCircle(Color.White.copy(alpha = a), s * 0.1f, Offset(x + s * 0.42f, y - s * 0.12f))
                drawCircle(Color.White.copy(alpha = a), s * 0.1f, Offset(x + s * 0.62f, y + s * 0.1f))
            }
            else -> {
                val c = Color(0xFFFFB648).copy(alpha = a)
                drawCircle(c, s * 0.82f, Offset(x, y))
                val ink = Color(0xFF6A3E10).copy(alpha = a)
                drawCircle(ink, s * 0.11f, Offset(x - s * 0.28f, y - s * 0.18f))
                drawCircle(ink, s * 0.11f, Offset(x + s * 0.28f, y - s * 0.18f))
                drawArc(ink, 20f, 140f, false, Offset(x - s * 0.4f, y - s * 0.22f), Size(s * 0.8f, s * 0.6f), style = Stroke(s * 0.12f, cap = StrokeCap.Round))
            }
        }
    }

    companion object {
        /** The scale at the back of the walk relative to the front. */
        const val S_FAR = 0.68f

        /** Where the front of the walk is, down the canvas, and how tall someone stands there. */
        const val NEAR_Y = 0.665f
        const val TALL = 0.27f

        /** The hedge along the back of the square, in z. */
        const val HEDGE_Z = -0.42f

        /** The nearest ground drawn (well below the bottom edge; nearer still, the view's maths would turn over). */
        const val Z_GROUND_MAX = 2.4f

        private const val DEPTH = PlazaWorld.DEPTH
        private const val FOUNTAIN_X = PlazaWorld.FOUNTAIN_X
        private const val FOUNTAIN_Z = PlazaWorld.FOUNTAIN_Z
        private const val FOUNTAIN_R_DRAW = PlazaWorld.FOUNTAIN_R
        private const val FOUNTAIN_BACK = PlazaWorld.FOUNTAIN_R / PlazaWorld.DEPTH

        private val SIDES = floatArrayOf(-1f, 1f)
        private val PLANTER_FLOWERS = longArrayOf(0xFFFF8FB1, 0xFFFFFFFF, 0xFFFFD45C, 0xFFB79CFF, 0xFFFF9F6B)

        private fun easeOutBack(x: Float): Float {
            val c1 = 1.70158f
            val c3 = c1 + 1f
            val p = x - 1f
            return 1f + c3 * p * p * p + c1 * p * p
        }

        private fun heartPath(p: Path) {
            p.moveTo(0f, 0.85f)
            p.cubicTo(-0.4f, 0.5f, -1f, 0.15f, -1f, -0.3f)
            p.cubicTo(-1f, -0.75f, -0.6f, -0.95f, -0.32f, -0.95f)
            p.cubicTo(-0.12f, -0.95f, 0f, -0.8f, 0f, -0.62f)
            p.cubicTo(0f, -0.8f, 0.12f, -0.95f, 0.32f, -0.95f)
            p.cubicTo(0.6f, -0.95f, 1f, -0.75f, 1f, -0.3f)
            p.cubicTo(1f, 0.15f, 0.4f, 0.5f, 0f, 0.85f)
            p.close()
        }

        private fun starPath(p: Path, points: Int, inner: Float) = starPathTo(p, points, inner)

        fun starPathTo(p: Path, points: Int, inner: Float) {
            for (i in 0 until points * 2) {
                val a = -PI.toFloat() / 2f + i * PI.toFloat() / points
                val r = if (i % 2 == 0) 1f else inner
                if (i == 0) p.moveTo(cos(a) * r, sin(a) * r) else p.lineTo(cos(a) * r, sin(a) * r)
            }
            p.close()
        }
    }
}

/**
 * The look of the hour: sky colours, the tint laid over the square, how much the lamps and windows glow and how
 * many stars are out, eased between morning, day, evening and night.
 */
internal class TimeOfDay {
    var hour = 12f; private set
    var skyTop = Color.White; private set
    var skyMid = Color.White; private set
    var skyBottom = Color.White; private set
    var cloud = Color.White; private set
    var grade = Color.White; private set
    var lights = 0f; private set
    var stars = 0f; private set
    var sun = 1f; private set

    /** Whether the light has changed enough from [grade] and [lit] to be seen (and the picture drawn again). */
    fun differs(grade: Color, lit: Float): Boolean =
        grade == Color.Unspecified || abs(grade.red - this.grade.red) > 0.012f || abs(grade.green - this.grade.green) > 0.012f ||
            abs(grade.blue - this.grade.blue) > 0.012f || abs(lit - lights) > 0.03f

    fun at(h: Float) {
        hour = ((h % 24f) + 24f) % 24f
        var i = 0
        while (i < KEYS.size - 2 && KEYS[i + 1].hour <= hour) i++
        val a = KEYS[i]
        val b = KEYS[i + 1]
        val p = ((hour - a.hour) / (b.hour - a.hour)).coerceIn(0f, 1f)
        val e = p * p * (3f - 2f * p)
        skyTop = lerp(a.top, b.top, e)
        skyMid = lerp(a.mid, b.mid, e)
        skyBottom = lerp(a.bottom, b.bottom, e)
        cloud = lerp(a.cloud, b.cloud, e)
        grade = lerp(a.grade, b.grade, e)
        lights = a.lights + (b.lights - a.lights) * e
        stars = a.stars + (b.stars - a.stars) * e
        sun = a.sun + (b.sun - a.sun) * e
    }

    private class Key(val hour: Float, top: Long, mid: Long, bottom: Long, cloud: Long, grade: Long, val lights: Float, val stars: Float, val sun: Float) {
        val top = Color(top)
        val mid = Color(mid)
        val bottom = Color(bottom)
        val cloud = Color(cloud)
        val grade = Color(grade)
    }

    private companion object {
        private val NIGHT = longArrayOf(0xFF33438F, 0xFF5A5FA8, 0xFF8E86C6, 0xFF8F96C8, 0xFF7C86BC)
        private val KEYS = arrayOf(
            Key(0f, NIGHT[0], NIGHT[1], NIGHT[2], NIGHT[3], NIGHT[4], 1f, 1f, 0f),
            Key(5f, NIGHT[0], NIGHT[1], NIGHT[2], NIGHT[3], NIGHT[4], 1f, 1f, 0f),
            Key(7f, 0xFF8EC2EE, 0xFFC9DFF2, 0xFFFFDDBE, 0xFFFFF4EC, 0xFFFFF3E6, 0.15f, 0f, 0.8f),
            Key(10f, 0xFF6FBFF2, 0xFFA9DBF8, 0xFFE4F5FF, 0xFFFFFFFF, 0xFFFFFFFF, 0f, 0f, 1f),
            Key(16.5f, 0xFF6FBFF2, 0xFFA9DBF8, 0xFFE4F5FF, 0xFFFFFFFF, 0xFFFFFFFF, 0f, 0f, 1f),
            Key(18.5f, 0xFF8296DC, 0xFFD5A6C8, 0xFFFFC49C, 0xFFFFE6D6, 0xFFFFE1CC, 0.55f, 0.05f, 0.6f),
            Key(20f, 0xFF44529F, 0xFF7468AE, 0xFFB28DBE, 0xFFA3A0CC, 0xFF8C90C4, 1f, 0.7f, 0f),
            Key(24f, NIGHT[0], NIGHT[1], NIGHT[2], NIGHT[3], NIGHT[4], 1f, 1f, 0f),
        )
    }
}
