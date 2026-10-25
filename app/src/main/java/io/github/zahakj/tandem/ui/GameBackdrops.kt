package io.github.zahakj.tandem.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.random.Random

private const val TAU = (2 * PI).toFloat()

private fun frac(v: Float): Float = v - floor(v)

/**
 * The game-flavoured backdrops of the built-in themes. Like the others in Backdrops.kt, [t] runs from 0 to 1 over
 * one long loop and every motion repeats a whole number of times per loop, so there is never a jump. Everything
 * that doesn't change per frame is built once in the constructor.
 */
internal class GameScene(private val style: BackdropStyle, size: Size, private val theme: ThemeSpec, private val dp: Float) {
    private val rnd = Random(7)
    private val w = size.width
    private val h = size.height
    private val path = Path()
    private val accents = theme.accents

    private fun glow(color: Color, radius: Float, alpha: Float) =
        Brush.radialGradient(listOf(color.copy(alpha = alpha), Color.Transparent), Offset.Zero, radius)

    // Shared star field: x, y, size, phase.
    private val stars = Array(110) { floatArrayOf(rnd.nextFloat(), rnd.nextFloat(), 0.5f + rnd.nextFloat() * 1.5f, rnd.nextFloat() * TAU) }

    fun draw(d: DrawScope, t: Float, tint: Color) = when (style) {
        BackdropStyle.Pixels -> d.pixels(t)
        BackdropStyle.Lcd -> d.lcd(t)
        BackdropStyle.Cubes -> d.cubes(t)
        BackdropStyle.Towers -> d.towers(t)
        BackdropStyle.Marquee -> d.marquee(t)
        BackdropStyle.Shell -> d.shell(t)
        BackdropStyle.Disc -> d.disc(t)
        BackdropStyle.Visor -> d.visor(t)
        BackdropStyle.Road -> d.road(t)
        BackdropStyle.Shooter -> d.shooter(t)
        BackdropStyle.Farm -> d.farm(t)
        BackdropStyle.Meadow -> d.meadow(t)
        BackdropStyle.Torch -> d.torch(t)
        BackdropStyle.Rainbow -> d.rainbow(t)
        else -> Unit
    }

    // ---- Cartridge: an 8-bit evening with blocky hills, clouds and a big pixel moon ----

    private val px = (6f * dp).coerceAtLeast(4f)
    private val cols = (w / px).toInt() + 2
    private fun ridge(seed: Int, base: Float, amp: Float): FloatArray {
        val n = cols
        val a = 1 + seed % 3; val b = 3 + seed % 4
        return FloatArray(n) { i ->
            val u = i.toFloat() / n
            val v = base + amp * (0.6f * sin(TAU * u * a + seed) + 0.4f * sin(TAU * u * b + seed * 2f))
            floor(v / px) * px
        }
    }
    private val farRidge by lazy { ridge(1, h * 0.62f, h * 0.08f) }
    private val nearRidge by lazy { ridge(4, h * 0.76f, h * 0.06f) }
    private val moonCells by lazy {
        val r = 7
        buildList { for (i in -r..r) for (j in -r..r) if (i * i + j * j <= r * r) add(i to j) }
    }
    private val clouds = Array(6) { floatArrayOf(rnd.nextFloat(), 0.08f + rnd.nextFloat() * 0.35f, 3f + rnd.nextInt(4)) }

    private fun DrawScope.pixels(t: Float) {
        // Banded sky, like a limited palette.
        val bands = 6
        for (i in 0 until bands) {
            val c = lerp(theme.backgroundDeep, lerp(theme.background, theme.lavender, 0.35f), i / (bands - 1f))
            drawRect(c, Offset(0f, h * 0.7f * i / bands), Size(w, h * 0.7f / bands + 1))
        }
        for (s in stars) {
            if (s[1] > 0.55f) continue
            val on = sin(t * TAU * 30 + s[3]) > -0.2f
            if (on) drawRect(theme.text, Offset(floor(s[0] * w / px) * px, floor(s[1] * h / px) * px), Size(px * 0.5f, px * 0.5f), alpha = 0.55f)
        }
        val mc = Offset(floor(w * 0.78f / px) * px, floor(h * 0.24f / px) * px)
        for ((i, j) in moonCells) {
            val shade = if (i + j > 3) 0.82f else 1f
            drawRect(lerp(theme.butter, theme.backgroundDeep, 1f - shade), Offset(mc.x + i * px, mc.y + j * px), Size(px + 0.5f, px + 0.5f))
        }
        for (c in clouds) {
            val x = frac(c[0] - t * 3) * (w + 12 * px) - 6 * px
            val y = floor(c[1] * h / px) * px
            val n = c[2].toInt()
            drawRect(theme.text, Offset(x, y), Size(n * 2 * px, px), alpha = 0.18f)
            drawRect(theme.text, Offset(x + px, y - px), Size((n * 2 - 2) * px, px), alpha = 0.18f)
            drawRect(theme.text, Offset(x + 2 * px, y - 2 * px), Size(n * px, px), alpha = 0.18f)
        }
        layer(farRidge, t * 2, lerp(theme.background, theme.sky, 0.3f))
        layer(nearRidge, t * 4, lerp(theme.backgroundDeep, theme.mint, 0.25f))
        // A ground strip with a checker edge.
        val gy = floor(h * 0.9f / px) * px
        drawRect(theme.backgroundDeep, Offset(0f, gy), Size(w, h - gy))
        var x = -frac(t * 8) * 2 * px
        while (x < w) { drawRect(theme.peach, Offset(x, gy), Size(px, px), alpha = 0.5f); x += 2 * px }
    }

    private fun DrawScope.layer(heights: FloatArray, scroll: Float, color: Color) {
        val shift = frac(scroll) * cols
        val whole = floor(shift).toInt()
        val part = (shift - whole) * px
        for (i in 0 until cols) {
            val y = heights[(i + whole) % cols]
            drawRect(color, Offset(i * px - part, y), Size(px + 0.5f, h - y))
        }
    }

    // ---- Handheld green: a dot-matrix screen with little sprites bouncing about ----

    private val sprites = listOf(
        listOf("..XXXX..", ".XXXXXX.", "XX.XX.XX", "XXXXXXXX", "XXXXXXXX", ".X.XX.X.", "X......X", ".X....X."),
        listOf(".XX..XX.", "XXXXXXXX", "XXXXXXXX", "XXXXXXXX", ".XXXXXX.", "..XXXX..", "...XX...", "........"),
        listOf("...XX...", "..XXXX..", "..X..X..", ".XXXXXX.", "XXXXXXXX", "XX.XX.XX", "X......X", "........"),
        listOf("..XXXX..", ".XX..XX.", "XX.XX.XX", "XX.XX.XX", "XX.XX.XX", "XX.XX.XX", ".XX..XX.", "..XXXX.."),
    )
    private val bouncers = Array(7) { floatArrayOf(rnd.nextFloat(), rnd.nextFloat(), (1 + rnd.nextInt(3)).toFloat(), (1 + rnd.nextInt(3)).toFloat(), rnd.nextInt(4).toFloat()) }

    private fun DrawScope.lcd(t: Float) {
        val ink = theme.text
        val cell = 4f * dp
        // The faint grid between LCD pixels.
        var x = 0f
        while (x < w) { drawRect(ink, Offset(x, 0f), Size(0.7f * dp, h), alpha = 0.05f); x += cell }
        var y = 0f
        while (y < h) { drawRect(ink, Offset(0f, y), Size(w, 0.7f * dp), alpha = 0.05f); y += cell }
        val p = cell * 1.5f
        for (b in bouncers) {
            val sprite = sprites[b[4].toInt()]
            // Bounce across the screen: a triangle wave, whole bounces per loop.
            fun tri(v: Float) = 1f - abs(frac(v) * 2f - 1f)
            for (ghost in 3 downTo 0) {
                val tt = t - ghost * 0.0009f
                val bx = tri(b[0] + tt * b[2] * 6) * (w - 8 * p)
                val by = tri(b[1] + tt * b[3] * 5) * (h - 8 * p)
                val alpha = if (ghost == 0) 0.3f else 0.08f
                for ((r, row) in sprite.withIndex()) for ((c, ch) in row.withIndex()) {
                    if (ch == 'X') drawRect(ink, Offset(floor((bx + c * p) / cell) * cell, floor((by + r * p) / cell) * cell), Size(p - 0.8f * dp, p - 0.8f * dp), alpha = alpha)
                }
            }
        }
    }

    // ---- Indigo cube / blue boot: floating translucent cubes ----

    private val cubeList = Array(14) { floatArrayOf(rnd.nextFloat(), rnd.nextFloat(), 0.4f + rnd.nextFloat(), rnd.nextFloat() * TAU, (1 + rnd.nextInt(3)).toFloat()) }

    private fun DrawScope.isoCube(cx: Float, cy: Float, s: Float, color: Color, alpha: Float) {
        val dx = s * 0.866f; val dy = s * 0.5f
        fun face(c: Color, a: Float, p0: Offset, p1: Offset, p2: Offset, p3: Offset) {
            path.rewind(); path.moveTo(p0.x, p0.y); path.lineTo(p1.x, p1.y); path.lineTo(p2.x, p2.y); path.lineTo(p3.x, p3.y); path.close()
            drawPath(path, c, alpha = a)
        }
        val top = Offset(cx, cy - s); val l = Offset(cx - dx, cy - dy); val r = Offset(cx + dx, cy - dy)
        val c0 = Offset(cx, cy); val bl = Offset(cx - dx, cy + dy); val br = Offset(cx + dx, cy + dy); val bot = Offset(cx, cy + s)
        face(lerp(color, Color.White, 0.45f), alpha, top, r, c0, l)
        face(color, alpha * 0.85f, l, c0, bot, bl)
        face(lerp(color, Color.Black, 0.35f), alpha * 0.85f, c0, r, br, bot)
        path.rewind(); path.moveTo(top.x, top.y); path.lineTo(r.x, r.y); path.lineTo(br.x, br.y); path.lineTo(bot.x, bot.y)
        path.lineTo(bl.x, bl.y); path.lineTo(l.x, l.y); path.close()
        drawPath(path, Color.White, alpha = alpha * 0.6f, style = Stroke(1.2f * dp))
        drawLine(Color.White, c0, top, 1f * dp, alpha = alpha * 0.25f)
    }

    private fun DrawScope.cubes(t: Float) {
        translate(w * 0.5f, h * 0.45f) { drawCircle(glow(theme.lavender, w * 0.6f, 0.22f), w * 0.6f, Offset.Zero) }
        for ((i, c) in cubeList.withIndex()) {
            val s = (16f + 38f * c[2]) * dp
            val x = frac(c[0] + t * c[4] * (if (i % 2 == 0) 1 else -1)) * (w + 4 * s) - 2 * s
            val y = c[1] * h + 14f * dp * sin(t * TAU * (10 + i % 4) + c[3])
            isoCube(x, y, s, accents[i % accents.size].let { lerp(it, theme.lavender, 0.5f) }, 0.12f + 0.16f * c[2])
        }
    }

    private fun DrawScope.towers(t: Float) {
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, theme.sky.copy(alpha = 0.18f)), startY = h * 0.3f, endY = h))
        val n = 9
        for (k in 0 until n) {
            // Back rows first.
            val depth = k / (n - 1f)
            val s = (10f + 14f * depth) * dp
            val baseY = h * (0.62f + 0.3f * depth)
            val count = 9
            for (j in 0 until count) {
                val xi = (j + (k % 2) * 0.5f) / count
                val x = xi * w * 1.1f - w * 0.05f
                val height = 2 + ((sin(t * TAU * (6 + (j + k) % 5) + j * 1.7f + k) + 1f) * 2.2f).toInt()
                for (lvl in 0 until height) {
                    isoCube(x, baseY - lvl * s * 1.0f, s, lerp(theme.sky, theme.lavender, depth * 0.6f), 0.06f + 0.1f * depth)
                }
            }
        }
        // Motes drifting up.
        for (st in stars) {
            if (st[0] > 0.7f) continue
            val y = h - frac(st[1] + t * (3 + (st[2] * 2).toInt())) * h
            drawCircle(theme.sky, st[2] * dp, Offset(st[0] / 0.7f * w, y), alpha = 0.4f)
        }
    }

    // ---- Neon arcade: tube signs, chase lights and a wet floor ----

    private fun DrawScope.neon(p: Path, color: Color, width: Float, on: Float) {
        drawPath(p, color, alpha = 0.10f * on, style = Stroke(width * 6, cap = StrokeCap.Round))
        drawPath(p, color, alpha = 0.25f * on, style = Stroke(width * 2.6f, cap = StrokeCap.Round))
        drawPath(p, lerp(color, Color.White, 0.6f), alpha = on, style = Stroke(width, cap = StrokeCap.Round))
    }

    private val signs: List<Pair<Path, Int>> by lazy {
        val zig = Path().apply {
            moveTo(w * 0.08f, h * 0.2f)
            for (k in 1..6) lineTo(w * (0.08f + 0.035f * k), h * (if (k % 2 == 0) 0.2f else 0.28f))
        }
        val star = Path().apply {
            val cx = w * 0.86f; val cy = h * 0.22f; val r = h * 0.09f
            for (i in 0 until 10) {
                val a = -PI / 2 + i * PI / 5; val rr = if (i % 2 == 0) r else r * 0.45f
                val x = cx + (rr * cos(a)).toFloat(); val y = cy + (rr * sin(a)).toFloat()
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }
        val ring = Path().apply { addOval(androidx.compose.ui.geometry.Rect(Offset(w * 0.12f, h * 0.52f), h * 0.07f)) }
        val arrow = Path().apply {
            moveTo(w * 0.74f, h * 0.6f); lineTo(w * 0.9f, h * 0.6f); moveTo(w * 0.86f, h * 0.55f); lineTo(w * 0.9f, h * 0.6f); lineTo(w * 0.86f, h * 0.65f)
        }
        listOf(zig to 0, star to 1, ring to 2, arrow to 3)
    }

    private fun DrawScope.marquee(t: Float) {
        // Brick hint.
        val bh = 18f * dp
        var y = 0f; var r = 0
        while (y < h * 0.8f) {
            drawLine(Color.Black, Offset(0f, y), Offset(w, y), 1f * dp, alpha = 0.18f)
            var x = if (r % 2 == 0) 0f else bh * 1.5f
            while (x < w) { drawLine(Color.Black, Offset(x, y), Offset(x, y + bh), 1f * dp, alpha = 0.14f); x += bh * 3 }
            y += bh; r++
        }
        val colors = listOf(theme.accent, theme.sky, theme.butter, theme.mint)
        for ((p, i) in signs) {
            // An occasional flicker, different for each sign.
            val f = sin(t * TAU * (47 + i * 13))
            val on = if (f > 0.97f) 0.35f else 1f
            neon(p, colors[i % colors.size], 2.2f * dp, on)
        }
        // Marquee frame with chasing bulbs.
        val left = w * 0.3f; val right = w * 0.7f; val top = h * 0.1f; val bottom = h * 0.36f
        drawRoundRect(theme.accent, Offset(left, top), Size(right - left, bottom - top), CornerRadius(14f * dp), alpha = 0.12f, style = Stroke(10f * dp))
        drawRoundRect(lerp(theme.accent, Color.White, 0.5f), Offset(left, top), Size(right - left, bottom - top), CornerRadius(14f * dp), style = Stroke(2f * dp))
        val step = 16f * dp
        val chase = floor(t * 900).toInt()
        var i = 0
        fun bulb(x: Float, y: Float) {
            val lit = (i + chase) % 3 == 0
            drawCircle(theme.butter, if (lit) 3.2f * dp else 2.2f * dp, Offset(x, y), alpha = if (lit) 1f else 0.3f)
            if (lit) drawCircle(theme.butter, 7f * dp, Offset(x, y), alpha = 0.18f)
            i++
        }
        var x = left; while (x <= right) { bulb(x, top - 8 * dp); x += step }
        var yy = top; while (yy <= bottom) { bulb(right + 8 * dp, yy); yy += step }
        x = right; while (x >= left) { bulb(x, bottom + 8 * dp); x -= step }
        yy = bottom; while (yy >= top) { bulb(left - 8 * dp, yy); yy -= step }
        // Floor reflection.
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, theme.accent.copy(alpha = 0.16f), Color.Transparent), startY = h * 0.78f, endY = h), Offset(0f, h * 0.78f), Size(w, h * 0.22f))
        drawLine(theme.accent, Offset(0f, h * 0.8f), Offset(w, h * 0.8f), 1.5f * dp, alpha = 0.4f)
    }

    // ---- Pocket atomic: a see-through shell with the circuit board inside ----

    private val traces: List<List<Offset>> by lazy {
        List(16) {
            var x = rnd.nextFloat() * w; var y = rnd.nextFloat() * h
            buildList {
                add(Offset(x, y))
                repeat(4) { k ->
                    if (k % 2 == 0) x += (rnd.nextFloat() - 0.5f) * w * 0.4f else y += (rnd.nextFloat() - 0.5f) * h * 0.4f
                    add(Offset(x, y))
                }
            }
        }
    }
    private val chips = Array(6) { floatArrayOf(rnd.nextFloat() * 0.85f, rnd.nextFloat() * 0.8f, 0.05f + rnd.nextFloat() * 0.07f) }

    private fun DrawScope.shell(t: Float) {
        val board = lerp(theme.background, theme.mint, 0.12f)
        for (tr in traces) {
            for (k in 1 until tr.size) drawLine(board, tr[k - 1], tr[k], 2.2f * dp, alpha = 0.55f, cap = StrokeCap.Round)
            drawCircle(board, 3.5f * dp, tr.first(), alpha = 0.7f)
            drawCircle(board, 3.5f * dp, tr.last(), alpha = 0.7f)
        }
        for (c in chips) {
            val s = c[2] * w
            val o = Offset(c[0] * w, c[1] * h)
            drawRoundRect(lerp(theme.backgroundDeep, Color.Black, 0.3f), o, Size(s, s * 0.6f), CornerRadius(2f * dp), alpha = 0.6f)
            var px = o.x + 4 * dp
            while (px < o.x + s - 3 * dp) {
                drawLine(board, Offset(px, o.y - 3 * dp), Offset(px, o.y), 1.5f * dp, alpha = 0.8f)
                drawLine(board, Offset(px, o.y + s * 0.6f), Offset(px, o.y + s * 0.6f + 3 * dp), 1.5f * dp, alpha = 0.8f)
                px += 5 * dp
            }
        }
        // Signals running along the traces.
        for ((i, tr) in traces.withIndex()) {
            val u = frac(t * (6 + i % 5) + i * 0.13f) * (tr.size - 1)
            val k = floor(u).toInt().coerceAtMost(tr.size - 2)
            val p = lerp(tr[k], tr[k + 1], u - k)
            translate(p.x, p.y) { drawCircle(glow(theme.accent, 10f * dp, 0.8f), 10f * dp, Offset.Zero) }
        }
        // The shell: tinted plastic with a broad sheen.
        drawRect(theme.lavender, alpha = 0.10f)
        val sx = frac(t * 2) * w * 2 - w * 0.5f
        path.rewind(); path.moveTo(sx, 0f); path.lineTo(sx + w * 0.25f, 0f); path.lineTo(sx - w * 0.1f, h); path.lineTo(sx - w * 0.35f, h); path.close()
        drawPath(path, Color.White, alpha = 0.05f)
    }

    private fun lerp(a: Offset, b: Offset, f: Float) = Offset(a.x + (b.x - a.x) * f, a.y + (b.y - a.y) * f)

    // ---- Midnight disc: a big spinning disc with a rainbow sheen ----

    private val rainbow = listOf(
        Color(0xFF7DF9FF), Color(0xFFB98CFF), Color(0xFFFF8AD8), Color(0xFFFFE08A), Color(0xFF8CFFB4), Color(0xFF7DF9FF),
    )

    private fun DrawScope.disc(t: Float) {
        val c = Offset(w * 0.78f, h * 0.62f)
        val r = h * 0.78f
        drawCircle(lerp(theme.card, Color.Black, 0.2f), r, c)
        rotate(t * 360f * 6, c) {
            drawCircle(Brush.sweepGradient(rainbow.map { it.copy(alpha = 0.22f) }, c), r, c)
            drawCircle(Brush.sweepGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.18f), Color.Transparent, Color.Transparent, Color.White.copy(alpha = 0.12f), Color.Transparent), c), r, c)
        }
        var rr = r * 0.4f
        while (rr < r) { drawCircle(Color.White, rr, c, alpha = 0.035f, style = Stroke(0.8f * dp)); rr += 6f * dp }
        drawCircle(theme.background, r * 0.17f, c)
        drawCircle(Color.White, r * 0.17f, c, alpha = 0.15f, style = Stroke(2f * dp))
        drawCircle(theme.backgroundDeep, r * 0.06f, c)
        // A few glints around the disc.
        for ((i, s) in stars.withIndex()) {
            if (i > 20) break
            val a = (sin(t * TAU * (25 + i) + s[3]) + 1f) / 2f
            drawCircle(Color.White, s[2] * dp * 1.4f, Offset(s[0] * w * 0.6f, s[1] * h), alpha = a * 0.5f)
        }
    }

    // ---- Crimson visor: a red wireframe tunnel on black ----

    private fun DrawScope.visor(t: Float) {
        val red = theme.accent
        val cx = w / 2; val cy = h * 0.5f
        val n = 12
        val shift = frac(t * 60)
        for (i in 0 until n) {
            val z = (i + shift) / n
            val k = z * z
            val yb = cy + (h - cy) * k; val yt = cy - cy * k
            drawLine(red, Offset(0f, yb), Offset(w, yb), 1.2f * dp, alpha = 0.1f + 0.55f * z)
            drawLine(red, Offset(0f, yt), Offset(w, yt), 1.2f * dp, alpha = 0.06f + 0.3f * z)
        }
        for (j in -12..12) {
            drawLine(red, Offset(cx + j * w * 0.012f, cy), Offset(cx + j * w * 0.12f, h), 1f * dp, alpha = 0.35f)
            drawLine(red, Offset(cx + j * w * 0.012f, cy), Offset(cx + j * w * 0.12f, 0f), 1f * dp, alpha = 0.15f)
        }
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, red.copy(alpha = 0.35f), Color.Transparent), startY = cy - 30 * dp, endY = cy + 30 * dp), Offset(0f, cy - 30 * dp), Size(w, 60 * dp))
        // A scan bar sweeps down now and then.
        val scan = frac(t * 14) * h * 1.6f
        if (scan < h) drawRect(Brush.verticalGradient(listOf(Color.Transparent, red.copy(alpha = 0.18f), Color.Transparent), startY = scan - 20 * dp, endY = scan + 20 * dp), Offset(0f, scan - 20 * dp), Size(w, 40 * dp))
    }

    // ---- Sunset racer: a sun, a road and palms rushing by ----

    private val horizon = h * 0.55f
    private val sunR = h * 0.2f
    private val sunC = Offset(w / 2, horizon - sunR * 0.3f)
    private val sunBrush = Brush.verticalGradient(listOf(theme.butter, theme.peach, theme.accent), startY = sunC.y - sunR, endY = sunC.y + sunR)
    private val mountains by lazy {
        Path().apply {
            moveTo(0f, horizon)
            var x = 0f
            var i = 0
            while (x <= w) { lineTo(x, horizon - (if (i % 2 == 0) h * 0.04f else h * (0.07f + 0.04f * sin(i * 1.3f)))); x += w / 14; i++ }
            lineTo(w, horizon); close()
        }
    }

    private fun DrawScope.road(t: Float) {
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, lerp(theme.background, theme.accent, 0.35f)), startY = 0f, endY = horizon), size = Size(w, horizon))
        for ((i, s) in stars.withIndex()) if (s[1] < 0.45f && i % 2 == 0) drawCircle(theme.text, s[2] * dp * 0.8f, Offset(s[0] * w, s[1] * h), alpha = 0.4f)
        translate(sunC.x, sunC.y) { drawCircle(glow(theme.peach, sunR * 2.2f, 0.28f), sunR * 2.2f, Offset.Zero) }
        drawCircle(sunBrush, sunR, sunC)
        var y = sunC.y + sunR * 0.1f; var band = 2f * dp
        while (y < sunC.y + sunR) { drawRect(lerp(theme.background, theme.accent, 0.25f), Offset(sunC.x - sunR, y), Size(sunR * 2, band)); y += band + 6f * dp; band += 1.4f * dp }
        drawPath(mountains, lerp(theme.backgroundDeep, theme.lavender, 0.25f))
        drawRect(theme.backgroundDeep, Offset(0f, horizon), Size(w, h - horizon))
        // Road.
        path.rewind(); path.moveTo(w * 0.48f, horizon); path.lineTo(w * 0.52f, horizon); path.lineTo(w * 0.95f, h); path.lineTo(w * 0.05f, h); path.close()
        drawPath(path, lerp(theme.backgroundDeep, theme.card, 0.6f))
        drawLine(theme.accent, Offset(w * 0.48f, horizon), Offset(w * 0.05f, h), 2f * dp, alpha = 0.8f)
        drawLine(theme.accent, Offset(w * 0.52f, horizon), Offset(w * 0.95f, h), 2f * dp, alpha = 0.8f)
        val n = 10
        val shift = frac(t * 90)
        for (i in 0 until n) {
            val z0 = (i + shift) / n; val z1 = (i + shift + 0.45f) / n
            val y0 = horizon + (h - horizon) * z0 * z0; val y1 = horizon + (h - horizon) * (z1 * z1).coerceAtMost(1f)
            val w0 = 1f * dp + 5f * dp * z0
            drawLine(theme.butter, Offset(w / 2, y0), Offset(w / 2, y1), w0, alpha = 0.85f)
        }
        // Palms on both sides, passing by.
        for (i in 0 until 4) {
            val z = frac(i / 4f + t * 45)
            val k = z * z
            val y = horizon + (h - horizon) * k
            val sc = 0.15f + k * 1.6f
            for (side in listOf(-1f, 1f)) palm(w / 2 + side * (w * 0.05f + w * 0.55f * k), y, sc * h * 0.22f, side)
        }
    }

    private fun DrawScope.palm(x: Float, y: Float, s: Float, side: Float) {
        val ink = lerp(theme.backgroundDeep, Color.Black, 0.4f)
        drawLine(ink, Offset(x, y), Offset(x + side * s * 0.15f, y - s), s * 0.06f, cap = StrokeCap.Round)
        val top = Offset(x + side * s * 0.15f, y - s)
        for (k in 0 until 6) {
            val a = (-160f + k * 28f) * PI.toFloat() / 180f
            val end = Offset(top.x + cos(a) * s * 0.45f, top.y + sin(a) * s * 0.25f + s * 0.12f)
            drawLine(ink, top, end, s * 0.05f, cap = StrokeCap.Round)
        }
    }

    // ---- Starfield shooter: warp stars, a squadron and laser fire ----

    private fun DrawScope.shooter(t: Float) {
        for ((i, s) in stars.withIndex()) {
            val layer = i % 3
            val y = frac(s[1] + t * (10 + layer * 10)) * h
            val len = (2f + layer * 5f) * dp
            drawLine(theme.text, Offset(s[0] * w, y), Offset(s[0] * w, y + len), s[2] * dp * 0.8f, alpha = 0.25f + layer * 0.2f)
        }
        // A formation sways at the top.
        val sway = sin(t * TAU * 12) * w * 0.06f
        for (k in 0 until 5) {
            val x = w * (0.3f + k * 0.1f) + sway
            val y = h * (0.16f + (if (k % 2 == 0) 0f else 0.05f)) + sin(t * TAU * 20 + k) * 4 * dp
            ship(x, y, 9f * dp, accents[k % accents.size], down = true)
        }
        // The player ship, firing.
        val px = w * 0.5f + sin(t * TAU * 8) * w * 0.25f
        val py = h * 0.84f
        ship(px, py, 13f * dp, theme.accent, down = false)
        for (b in 0 until 4) {
            val by = py - frac(t * 120 + b / 4f) * h * 0.7f
            drawLine(theme.butter, Offset(px, by), Offset(px, by - 10 * dp), 2.5f * dp, alpha = 0.9f, cap = StrokeCap.Round)
        }
    }

    private fun DrawScope.ship(x: Float, y: Float, s: Float, color: Color, down: Boolean) {
        val d = if (down) 1f else -1f
        path.rewind()
        path.moveTo(x, y + d * s); path.lineTo(x + s * 0.8f, y - d * s * 0.6f); path.lineTo(x, y - d * s * 0.2f); path.lineTo(x - s * 0.8f, y - d * s * 0.6f); path.close()
        drawPath(path, color, alpha = 0.75f)
        drawCircle(Color.White, s * 0.18f, Offset(x, y), alpha = 0.7f)
        if (!down) translate(x, y + s * 0.8f) { drawCircle(glow(theme.peach, s, 0.6f), s, Offset.Zero) }
    }

    // ---- Cozy farm: rolling fields, puffy clouds and a lazy sun ----

    private val puffs = Array(5) { floatArrayOf(rnd.nextFloat(), 0.08f + rnd.nextFloat() * 0.25f, 0.6f + rnd.nextFloat() * 0.6f) }

    private fun DrawScope.hill(base: Float, amp: Float, freq: Int, phase: Float, color: Color) {
        path.rewind(); path.moveTo(0f, h)
        var x = 0f; val step = 12f * dp
        while (x <= w + step) { path.lineTo(x, base + amp * sin(TAU * x / w * freq + phase)); x += step }
        path.lineTo(w, h); path.close()
        drawPath(path, color)
    }

    private fun DrawScope.farm(t: Float) {
        val sun = Offset(w * 0.8f, h * 0.22f)
        translate(sun.x, sun.y) { drawCircle(glow(theme.butter, h * 0.4f, 0.45f), h * 0.4f, Offset.Zero) }
        drawCircle(theme.butter, h * 0.07f, sun, alpha = 0.85f)
        for (p in puffs) {
            val x = frac(p[0] + t * 2) * (w + 200 * dp) - 100 * dp
            val y = p[1] * h
            val s = p[2] * 26 * dp
            for ((dx, r) in listOf(-1.1f to 0.8f, 0f to 1.1f, 1.1f to 0.85f, 0.5f to 0.7f)) drawCircle(Color.White, s * r, Offset(x + dx * s, y), alpha = 0.55f)
        }
        hill(h * 0.62f, h * 0.04f, 2, 0.5f, lerp(theme.background, theme.mint, 0.35f))
        hill(h * 0.72f, h * 0.05f, 3, 2f, lerp(theme.background, theme.mint, 0.55f))
        hill(h * 0.84f, h * 0.035f, 2, 4f, lerp(theme.background, theme.butter, 0.45f))
        // Crop rows on the front field, swaying a touch.
        val sway = sin(t * TAU * 30) * 1.5f * dp
        var x = 8 * dp
        while (x < w) {
            val y = h * 0.84f + h * 0.035f * sin(TAU * x / w * 2 + 4f) + 10 * dp
            for (k in 0..3) drawLine(lerp(theme.mint, theme.backgroundDeep, 0.25f), Offset(x, y + k * 14 * dp), Offset(x + sway, y + k * 14 * dp - 7 * dp), 2f * dp, alpha = 0.7f, cap = StrokeCap.Round)
            x += 18 * dp
        }
        // A fence along the middle field.
        var fx = 0f
        val fy = h * 0.71f
        drawLine(lerp(theme.peach, theme.backgroundDeep, 0.4f), Offset(0f, fy), Offset(w, fy), 2f * dp, alpha = 0.6f)
        while (fx < w) { drawLine(lerp(theme.peach, theme.backgroundDeep, 0.4f), Offset(fx, fy - 8 * dp), Offset(fx, fy + 10 * dp), 3f * dp, alpha = 0.6f); fx += 40 * dp }
    }

    // ---- Monster catcher: tall grass that rustles where something hides ----

    private val blades = Array(150) { floatArrayOf(rnd.nextFloat(), 0.5f + rnd.nextFloat() * 0.5f, rnd.nextFloat() * TAU, rnd.nextInt(3).toFloat()) }

    private fun DrawScope.meadow(t: Float) {
        translate(w * 0.2f, h * 0.15f) { drawCircle(glow(theme.butter, h * 0.6f, 0.25f), h * 0.6f, Offset.Zero) }
        // Where the grass rustles: a hidden something wandering about.
        val hx = w * (0.5f + 0.38f * sin(t * TAU * 5))
        val colors = listOf(lerp(theme.mint, theme.backgroundDeep, 0.45f), lerp(theme.mint, theme.backgroundDeep, 0.2f), theme.mint)
        for (layer in 0..2) {
            path.rewind()
            for (b in blades) {
                if (b[3].toInt() != layer) continue
                val x = b[0] * w
                val base = h * (0.82f + 0.07f * layer) + 30 * dp
                val len = h * 0.22f * b[1]
                val near = (1f - abs(x - hx) / (w * 0.12f)).coerceIn(0f, 1f)
                val bend = (sin(t * TAU * 24 + b[2]) * 0.06f + near * sin(t * TAU * 160 + b[2]) * 0.25f) * len
                path.moveTo(x - 3 * dp, base)
                path.quadraticTo(x + bend * 0.5f, base - len * 0.6f, x + bend, base - len)
                path.quadraticTo(x + bend * 0.5f + 1 * dp, base - len * 0.6f, x + 3 * dp, base)
                path.close()
            }
            drawPath(path, colors[layer], alpha = 0.55f + 0.15f * layer)
        }
        // Leaves drifting by.
        for ((i, s) in stars.withIndex()) {
            if (i > 14) break
            val x = frac(s[0] + t * 4) * w
            val y = h * (0.15f + 0.5f * s[1]) + sin(t * TAU * 20 + s[3]) * 10 * dp
            rotate(t * 360f * 20 + i * 40f, Offset(x, y)) { drawOval(theme.mint, Offset(x - 5 * dp, y - 2.5f * dp), Size(10 * dp, 5 * dp), alpha = 0.5f) }
        }
    }

    // ---- Dungeon: a stone wall lit by flickering torches ----

    private val bricks: List<FloatArray> by lazy {
        val out = mutableListOf<FloatArray>()
        val bh = 26f * dp; val bw = 62f * dp
        var y = 0f; var r = 0
        while (y < h) {
            var x = if (r % 2 == 0) 0f else -bw / 2
            while (x < w) { out += floatArrayOf(x, y, bw, bh, rnd.nextFloat()); x += bw }
            y += bh; r++
        }
        out
    }

    private fun DrawScope.torch(t: Float) {
        for (b in bricks) {
            drawRoundRect(lerp(theme.card, theme.backgroundDeep, 0.3f + 0.4f * b[4]), Offset(b[0] + 1.5f * dp, b[1] + 1.5f * dp), Size(b[2] - 3 * dp, b[3] - 3 * dp), CornerRadius(3f * dp))
        }
        for ((i, tx) in listOf(0.18f, 0.82f).withIndex()) {
            val x = w * tx; val y = h * 0.38f
            val flicker = 0.85f + 0.1f * sin(t * TAU * 211 + i) + 0.05f * sin(t * TAU * 397 + i * 2)
            translate(x, y) { drawCircle(glow(theme.peach, h * 0.55f * flicker, 0.38f), h * 0.55f * flicker, Offset.Zero) }
            drawRoundRect(Color(0xFF2A1A10), Offset(x - 4 * dp, y), Size(8 * dp, 34 * dp), CornerRadius(2 * dp))
            // Flame.
            val fh = 22 * dp * flicker
            path.rewind(); path.moveTo(x - 7 * dp, y); path.quadraticTo(x - 6 * dp, y - fh * 0.6f, x + 1.5f * dp * sin(t * TAU * 150), y - fh)
            path.quadraticTo(x + 6 * dp, y - fh * 0.6f, x + 7 * dp, y); path.close()
            drawPath(path, theme.peach, alpha = 0.95f)
            drawCircle(theme.butter, 4 * dp, Offset(x, y - 5 * dp), alpha = 0.9f)
            // Embers.
            for (k in 0 until 6) {
                val p = frac(t * (40 + k * 7) + k / 6f)
                drawCircle(theme.butter, 1.5f * dp, Offset(x + sin(p * 9 + k) * 10 * dp, y - fh - p * h * 0.25f), alpha = (1f - p) * 0.8f)
            }
        }
        drawRect(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)), Offset(w / 2, h * 0.45f), w * 0.7f))
    }

    // ---- Kart rainbow: a striped road winding through space ----

    private val rainbowBands = listOf(Color(0xFFFF5D73), Color(0xFFFFA24C), Color(0xFFFFE15C), Color(0xFF6BEA8C), Color(0xFF5CC8FF), Color(0xFFB07CFF))

    private fun DrawScope.rainbow(t: Float) {
        for (s in stars) {
            val tw = 0.5f + 0.5f * sin(t * TAU * 25 + s[3])
            drawCircle(theme.text, s[2] * dp, Offset(s[0] * w, s[1] * h * 0.75f), alpha = 0.2f + 0.4f * tw)
        }
        val top = h * 0.42f
        val slices = 70
        val travel = t * 162
        for (i in slices - 1 downTo 0) {
            val z0 = i / slices.toFloat(); val z1 = (i + 1) / slices.toFloat()
            val y0 = top + (h - top) * z0 * z0; val y1 = top + (h - top) * z1 * z1
            val curve = sin(TAU * (z0 * 0.8f) + t * TAU * 6) * w * 0.18f * (1f - z0)
            val half = w * (0.02f + 0.45f * z0 * z0)
            val worldZ = 1f / (0.05f + z0)
            val band = floor(worldZ * 0.6f + travel).toInt()
            val c = rainbowBands[((band % 6) + 6) % 6]
            drawRect(c, Offset(w / 2 + curve - half, y0), Size(half * 2, (y1 - y0) + 1f), alpha = 0.55f)
            drawRect(Color.White, Offset(w / 2 + curve - half, y0), Size(2f * dp, (y1 - y0) + 1f), alpha = 0.6f)
            drawRect(Color.White, Offset(w / 2 + curve + half - 2f * dp, y0), Size(2f * dp, (y1 - y0) + 1f), alpha = 0.6f)
        }
    }
}
