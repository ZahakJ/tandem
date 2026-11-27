package io.github.zahakj.tandem.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Tandem's small icon set, drawn in code with one rounded-stroke style so every icon matches (and there are no
 * icon assets to license). Coordinates are on a 24x24 grid.
 *
 * Every Settings row has a glyph of its own on its page (SettingsPlan.glyphs; SettingsGlyphsTest checks it), so
 * most glyphs below picture one setting; reuse one only where it means the same thing.
 */
enum class Glyph {
    Theme, Sparkle, Grid, Image, Music, Volume, Gamepad, Screens, Clock, Battery, Eye, Folder, Sort, Refresh,
    Download, Save, Restore, Info, Heart, Motion, Corners, Layers, Text, Home, Swap, Highlight, Bell,
    Play, Trash, Pencil, Star, Close, Move, More, Back, Search, Plus, Forward,
    Chart, Trophy, Key, Shield, Cartridge, Wand,

    // Look & feel
    Drop, Glass, Handheld, Share, Globe, Calendar, Undo, TopScreen, BottomScreen, Dim, Shapes, Box, FetchArt,
    Bounce, Snail, HighlightMove, FloatText, Gauge, Flip,

    // Home & layout
    IconSize, Scroll, Tag, FolderOpen, History, NowPlaying, Dots9, Orbit, Ribbon, Rail, Backdrop, Stage, Resize,
    Rows, Tiles, Covers, Dock, Screensaver, GridRows, Spacing, Split, AnimIcon, WideCard, Views, CardShape, Shelf,
    Checklist, Roller, DockTop, EyeOff, Widgets,

    // Two screens
    OneScreen, OpenOn, HoldButton, Rotate, BottomDetails, BottomWidgets, BottomOff, Timer, Film, Mic, Video, Pan,
    TextSize, Fade,

    // Sound & music
    Cassette, Waveform, Bubble, Headphones, Shuffle, Skip,

    // Controls
    Buttons, SwapAB, SwapXY, XButton, Rebind, Vibrate, CornerHint, Plug, Link, Keyboard, HomeButton, Trackpad,

    // Library & art
    Robot, Monitor, Sources, Person, Medal, SignOut, Unlink, Presence, Unplug, Strike, Disc,

    // Play & system
    PlayCircle, Resume, CloseWindow, AskBubble, Fullscreen, Rocket, MoonBolt, Bump, Update, Wrench, Bolt, Fan, Hz,
    Sliders, Moon, WifiOff, Lock, FileIn, Export, Gift, Megaphone, Bug, GamepadSpark, FacePencil, CardEye, Fountain,
    PersonOff, Fingerprint,
}

/** A glyph on a soft tinted chip, as used in settings rows; [round] for a circle (the rounded popup rows). */
@Composable
fun GlyphChip(glyph: Glyph, color: Color, size: Dp = 30.dp, modifier: Modifier = Modifier, round: Boolean = false) {
    Box(
        modifier.size(size).clip(RoundedCornerShape(if (round) size / 2 else size * 0.32f)).background(color.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
    ) { GlyphIcon(glyph, color, Modifier.padding(size * 0.2f)) }
}

@Composable
fun GlyphIcon(glyph: Glyph, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxSize()) { drawGlyph(glyph, color) }
}

/** [glyph] filling this drawing's square. */
fun DrawScope.drawGlyph(glyph: Glyph, color: Color) {
    val u = size.minDimension / 24f
    // A layer of its own, so a glyph can cut a gap out of itself (a slash across an eye, a badge on a picture).
    drawIntoCanvas { it.saveLayer(Rect(Offset.Zero, size), Paint()) }
    // Arrows and lines ranged from the start mirror in right to left; pictures of things don't.
    if (glyph in Pointing) mirroredInRtl { GlyphPen(this, color, u).draw(glyph) }
    else GlyphPen(this, color, u).draw(glyph)
    drawIntoCanvas { it.restore() }
}

/** Glyphs that point or read from the start side, drawn mirrored in right to left. */
private val Pointing = setOf(
    Glyph.Back, Glyph.Forward, Glyph.Sort, Glyph.Motion, Glyph.Undo, Glyph.SignOut, Glyph.HighlightMove, Glyph.History,
)


/**
 * Draws on the 24 grid: [ox], [oy] and [k] place a smaller glyph inside a bigger one (the stroke stays as wide), [alpha]
 * draws a part faint. A pen with a [gap] erases instead, a little wider than it would draw (see [cut]); [solid] erases
 * the inside of closed shapes too.
 */
private class GlyphPen(
    val s: DrawScope, val color: Color, val u: Float, val ox: Float = 0f, val oy: Float = 0f, val k: Float = 1f,
    val alpha: Float = 1f, val gap: Float = 0f, val solid: Boolean = false,
) {
    private val erasing = gap > 0f
    private val ink = if (erasing) Color.Black else color.copy(alpha = color.alpha * alpha)
    private val blend = if (erasing) BlendMode.Clear else BlendMode.SrcOver
    private val stroke = Stroke(width = (2.1f + 2 * gap) * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
    /** What a filled shape erases around itself. */
    private val rim = Stroke(width = 2 * gap * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
    fun x(v: Float) = (ox + v * k) * u
    fun y(v: Float) = (oy + v * k) * u
    fun p(a: Float, b: Float) = Offset(x(a), y(b))
    fun d(v: Float) = v * k * u

    /** Fill, outline, or (erasing) both, as the shape asks. */
    private inline fun paint(fill: Boolean, closed: Boolean, draw: (androidx.compose.ui.graphics.drawscope.DrawStyle) -> Unit) {
        if (fill) { draw(Fill); if (erasing) draw(rim) }
        else { if (erasing && solid && closed) draw(Fill); draw(stroke) }
    }

    fun line(x1: Float, y1: Float, x2: Float, y2: Float) = s.drawLine(ink, p(x1, y1), p(x2, y2), stroke.width, StrokeCap.Round, blendMode = blend)
    fun c(cx: Float, cy: Float, r: Float, fill: Boolean = false) =
        paint(fill, true) { s.drawCircle(ink, d(r), p(cx, cy), style = it, blendMode = blend) }
    fun r(rx: Float, ry: Float, w: Float, h: Float, rad: Float, fill: Boolean = false) =
        paint(fill, true) { s.drawRoundRect(ink, p(rx, ry), Size(d(w), d(h)), CornerRadius(d(rad)), style = it, blendMode = blend) }
    fun oval(rx: Float, ry: Float, w: Float, h: Float, fill: Boolean = false) =
        paint(fill, true) { s.drawOval(ink, p(rx, ry), Size(d(w), d(h)), style = it, blendMode = blend) }
    /** An arc of the circle at [cx], [cy]: angles in degrees, 0 pointing right, growing clockwise. */
    fun arc(cx: Float, cy: Float, r: Float, start: Float, sweep: Float) =
        s.drawArc(ink, start, sweep, false, p(cx - r, cy - r), Size(d(2 * r), d(2 * r)), style = stroke, blendMode = blend)
    fun ovalArc(rx: Float, ry: Float, w: Float, h: Float, start: Float, sweep: Float) =
        s.drawArc(ink, start, sweep, false, p(rx, ry), Size(d(w), d(h)), style = stroke, blendMode = blend)
    fun pl(vararg pts: Float, close: Boolean = false, fill: Boolean = false) = shape(close, fill) {
        m(pts[0], pts[1]); var i = 2
        while (i < pts.size) { l(pts[i], pts[i + 1]); i += 2 }
    }

    inner class Shape(val path: Path) {
        fun m(a: Float, b: Float) = path.moveTo(x(a), y(b))
        fun l(a: Float, b: Float) = path.lineTo(x(a), y(b))
        fun q(a: Float, b: Float, c: Float, e: Float) = path.quadraticTo(x(a), y(b), x(c), y(e))
        fun cu(a: Float, b: Float, c: Float, e: Float, f: Float, g: Float) = path.cubicTo(x(a), y(b), x(c), y(e), x(f), y(g))
        /** An arc of the circle at [cx], [cy] joined on to the path. */
        fun a(cx: Float, cy: Float, r: Float, start: Float, sweep: Float) =
            path.arcTo(Rect(p(cx - r, cy - r), Size(d(2 * r), d(2 * r))), start, sweep, false)
    }

    fun shape(close: Boolean = false, fill: Boolean = false, build: Shape.() -> Unit) {
        val path = Path()
        Shape(path).build()
        if (close) path.close()
        paint(fill, close) { s.drawPath(path, ink, style = it, blendMode = blend) }
    }

    /** A four-pointed twinkle, filled. */
    fun spark(cx: Float, cy: Float, r: Float) = shape(close = true, fill = true) {
        val t = r * 0.18f
        m(cx, cy - r); q(cx + t, cy - t, cx + r, cy); q(cx + t, cy + t, cx, cy + r); q(cx - t, cy + t, cx - r, cy); q(cx - t, cy - t, cx, cy - r)
    }

    /** A crescent: the circle at [cx], [cy] with the circle [dx], [dy] off its centre bitten out. */
    fun crescent(cx: Float, cy: Float, r: Float, dx: Float, dy: Float, r2: Float, fill: Boolean = false) {
        val dist = hypot(dx, dy)
        val toward = atan2(dy, dx)
        val phi = acos(((dist * dist + r * r - r2 * r2) / (2 * dist * r)).coerceIn(-1f, 1f))
        val p1x = cx + r * cos(toward + phi); val p1y = cy + r * sin(toward + phi)
        val p2x = cx + r * cos(toward - phi); val p2y = cy + r * sin(toward - phi)
        val ix = cx + dx; val iy = cy + dy
        val a2 = atan2(p2y - iy, p2x - ix); val a1 = atan2(p1y - iy, p1x - ix)
        // The bite's edge runs from p2 back to p1 through the side facing the big circle's centre.
        val facing = toward + PI.toFloat()
        fun off(a: Float) = abs(atan2(sin(a - facing), cos(a - facing)))
        val one = a1 - a2
        val other = if (one > 0) one - 2 * PI.toFloat() else one + 2 * PI.toFloat()
        val sweep = if (off(a2 + other / 2) < off(a2 + one / 2)) other else one
        val deg = 180f / PI.toFloat()
        shape(close = true, fill = fill) {
            m(p1x, p1y)
            a(cx, cy, r, (toward + phi) * deg, 360f - 2 * phi * deg)
            a(ix, iy, r2, a2 * deg, sweep * deg)
        }
    }

    /** [block] drawn over what's there with a clear gap around it, so it stands apart (a badge, a slash). */
    fun cut(solid: Boolean = false, gap: Float = 1.3f, block: GlyphPen.() -> Unit) {
        GlyphPen(s, color, u, ox, oy, k, alpha, gap, solid).block()
        block()
    }

    /** [block] cut out of what's there, as a gap a stroke wide (a symbol inside a filled shape). */
    fun erase(block: GlyphPen.() -> Unit) = GlyphPen(s, color, u, ox, oy, k, alpha, 0.001f).block()

    /** A slash across what's drawn (off, blocked, hidden). */
    fun slash() = cut { line(4f, 4f, 20f, 20f) }

    fun at(dx: Float, dy: Float, scale: Float, block: GlyphPen.() -> Unit) = GlyphPen(s, color, u, ox + dx * k, oy + dy * k, k * scale, alpha, gap, solid).block()
    fun faint(block: GlyphPen.() -> Unit) = GlyphPen(s, color, u, ox, oy, k, alpha * 0.4f, gap, solid).block()
    fun turned(deg: Float, block: GlyphPen.() -> Unit) = s.rotate(deg, p(12f, 12f)) { GlyphPen(this, color, u, ox, oy, k, alpha, gap, solid).block() }
}

private fun GlyphPen.draw(g: Glyph) {
    when (g) {
        Glyph.Theme -> { // A painter's palette.
            pl(12f, 3f, 6f, 5f, 3f, 11f, 5f, 18f, 11f, 21f, 14f, 19f, 13f, 16f, 16f, 15f, 20f, 14f, 21f, 9f, 17f, 4f, close = true)
            c(8f, 10f, 1.3f, true); c(12f, 7f, 1.3f, true); c(16.5f, 9.5f, 1.3f, true)
        }
        Glyph.Sparkle -> { pl(12f, 3f, 14f, 10f, 21f, 12f, 14f, 14f, 12f, 21f, 10f, 14f, 3f, 12f, 10f, 10f, close = true) }
        Glyph.Grid -> { r(4f, 4f, 7f, 7f, 2f); r(13f, 4f, 7f, 7f, 2f); r(4f, 13f, 7f, 7f, 2f); r(13f, 13f, 7f, 7f, 2f) }
        Glyph.Image -> { r(3f, 5f, 18f, 14f, 3f); c(9f, 10f, 1.6f); pl(5f, 18f, 11f, 13f, 14f, 15.5f, 16f, 14f, 20f, 17.5f) }
        Glyph.Music -> { line(9f, 17f, 9f, 5f); line(9f, 5f, 19f, 3f); line(19f, 3f, 19f, 15f); c(6.5f, 17f, 2.5f); c(16.5f, 15f, 2.5f) }
        Glyph.Volume -> {
            pl(4f, 9f, 8f, 9f, 13f, 5f, 13f, 19f, 8f, 15f, 4f, 15f, close = true)
            line(16.5f, 9.5f, 17.5f, 12f); line(17.5f, 12f, 16.5f, 14.5f); line(19.5f, 7f, 21f, 12f); line(21f, 12f, 19.5f, 17f)
        }
        Glyph.Gamepad -> gamepad()
        Glyph.Screens -> { r(4f, 2.5f, 16f, 9f, 2f); r(6f, 13.5f, 12f, 8f, 2f) }
        Glyph.Clock -> { c(12f, 12f, 8.5f); line(12f, 12f, 12f, 7f); line(12f, 12f, 15.5f, 14f) }
        Glyph.Battery -> { r(3f, 7f, 16f, 10f, 2.5f); r(5.5f, 9.5f, 7f, 5f, 1f, true); line(21f, 10.5f, 21f, 13.5f) }
        Glyph.Eye -> eye()
        Glyph.Folder -> { pl(3f, 6f, 3f, 19f, 21f, 19f, 21f, 8f, 12f, 8f, 10f, 5f, 3f, 5f, close = true) }
        Glyph.Sort -> { line(5f, 7f, 19f, 7f); line(5f, 12f, 15f, 12f); line(5f, 17f, 11f, 17f) }
        Glyph.Refresh -> {
            pl(19f, 12f, 17.7f, 16.6f, 14f, 19.3f, 9.5f, 19.6f, 5.6f, 17.3f, 4.1f, 12.8f, 5.3f, 8.3f, 9f, 5.1f, 13.6f, 4.6f, 17.6f, 6.6f)
            pl(17.8f, 2.8f, 17.6f, 6.6f, 13.8f, 6.6f)
        }
        Glyph.Download -> { line(12f, 4f, 12f, 15f); pl(7.5f, 10.5f, 12f, 15f, 16.5f, 10.5f); line(5f, 19.5f, 19f, 19.5f) }
        Glyph.Save -> { pl(5f, 4f, 16f, 4f, 20f, 8f, 20f, 20f, 4f, 20f, 4f, 4f, close = true); r(8f, 13f, 8f, 7f, 1f); line(8f, 4f, 8f, 8f) }
        Glyph.Restore -> { line(12f, 20f, 12f, 9f); pl(7.5f, 13.5f, 12f, 9f, 16.5f, 13.5f); line(5f, 4.5f, 19f, 4.5f) }
        Glyph.Info -> { c(12f, 12f, 8.5f); line(12f, 11f, 12f, 16.5f); c(12f, 7.8f, 1.1f, true) }
        Glyph.Heart -> { pl(12f, 19.5f, 4.5f, 12f, 4f, 8f, 7f, 5f, 10f, 5f, 12f, 7.5f, 14f, 5f, 17f, 5f, 20f, 8f, 19.5f, 12f, close = true) }
        Glyph.Motion -> { line(3f, 8f, 11f, 8f); line(5f, 12f, 13f, 12f); line(3f, 16f, 11f, 16f); c(17f, 12f, 3.5f) }
        Glyph.Corners -> { pl(4f, 20f, 4f, 11f, 6f, 6f, 11f, 4f, 20f, 4f) ; c(4f, 20f, 0.6f, true) }
        Glyph.Layers -> { pl(12f, 4f, 21f, 9f, 12f, 14f, 3f, 9f, close = true); pl(3f, 13.5f, 12f, 18.5f, 21f, 13.5f) }
        Glyph.Text -> { line(5f, 5f, 19f, 5f); line(12f, 5f, 12f, 19f); line(9f, 19f, 15f, 19f) }
        Glyph.Home -> { pl(4f, 11f, 12f, 4f, 20f, 11f); pl(6f, 9.5f, 6f, 20f, 18f, 20f, 18f, 9.5f); r(10f, 14f, 4f, 6f, 1f) }
        Glyph.Swap -> { line(5f, 8f, 19f, 8f); pl(15.5f, 4.5f, 19f, 8f, 15.5f, 11.5f); line(19f, 16f, 5f, 16f); pl(8.5f, 12.5f, 5f, 16f, 8.5f, 19.5f) }
        Glyph.Highlight -> { r(4f, 4f, 16f, 16f, 4f); r(8f, 8f, 8f, 8f, 2f, true) }
        Glyph.Bell -> { pl(6f, 17f, 6f, 11f, 8f, 6.5f, 12f, 5f, 16f, 6.5f, 18f, 11f, 18f, 17f, close = true); line(4.5f, 17f, 19.5f, 17f); line(10.5f, 20f, 13.5f, 20f) }
        Glyph.Chart -> { line(4f, 20f, 20f, 20f); r(5.5f, 12f, 3.5f, 6f, 1f); r(10.25f, 7f, 3.5f, 11f, 1f); r(15f, 10f, 3.5f, 8f, 1f) }
        Glyph.Trophy -> {
            pl(7f, 4f, 17f, 4f, 17f, 10f, 15f, 13.5f, 12f, 14.5f, 9f, 13.5f, 7f, 10f, close = true)
            pl(7f, 6f, 4f, 6f, 4.5f, 9f, 7.5f, 11f); pl(17f, 6f, 20f, 6f, 19.5f, 9f, 16.5f, 11f)
            line(12f, 14.5f, 12f, 18f); line(8f, 20f, 16f, 20f)
        }
        Glyph.Key -> { c(8f, 12f, 4f); line(12f, 12f, 21f, 12f); line(18f, 12f, 18f, 15.5f); line(15f, 12f, 15f, 14.5f) }
        Glyph.Shield -> { pl(12f, 3f, 19.5f, 6f, 19f, 12f, 16f, 17f, 12f, 21f, 8f, 17f, 5f, 12f, 4.5f, 6f, close = true); pl(9f, 12f, 11.2f, 14.2f, 15.5f, 9.5f) }
        Glyph.Cartridge -> {
            pl(5f, 3f, 19f, 3f, 19f, 19f, 17f, 21f, 7f, 21f, 5f, 19f, close = true)
            r(8f, 6f, 8f, 6f, 1f); line(8.5f, 17f, 8.5f, 19f); line(11f, 17f, 11f, 19f); line(13.5f, 17f, 13.5f, 19f); line(16f, 17f, 16f, 19f)
        }
        Glyph.Wand -> {
            line(4f, 20f, 15f, 9f); pl(17f, 3f, 17.8f, 5.2f, 20f, 6f, 17.8f, 6.8f, 17f, 9f, 16.2f, 6.8f, 14f, 6f, 16.2f, 5.2f, close = true)
            c(8f, 5f, 0.9f, true); c(20f, 14f, 0.9f, true)
        }
        Glyph.Play -> { pl(7f, 4.5f, 19f, 12f, 7f, 19.5f, close = true) }
        Glyph.Trash -> { line(4f, 6.5f, 20f, 6.5f); pl(9.5f, 6.5f, 9.5f, 4f, 14.5f, 4f, 14.5f, 6.5f); pl(6f, 6.5f, 7f, 20f, 17f, 20f, 18f, 6.5f); line(10.3f, 10f, 10.3f, 16.5f); line(13.7f, 10f, 13.7f, 16.5f) }
        Glyph.Pencil -> pencil()
        Glyph.Star -> { pl(12f, 3f, 14.6f, 9f, 21f, 9.4f, 16f, 13.5f, 17.6f, 20f, 12f, 16.4f, 6.4f, 20f, 8f, 13.5f, 3f, 9.4f, 9.4f, 9f, close = true) }
        Glyph.Close -> { line(6f, 6f, 18f, 18f); line(18f, 6f, 6f, 18f) }
        Glyph.Move -> { line(12f, 3f, 12f, 21f); line(3f, 12f, 21f, 12f); pl(9f, 6f, 12f, 3f, 15f, 6f); pl(9f, 18f, 12f, 21f, 15f, 18f); pl(6f, 9f, 3f, 12f, 6f, 15f); pl(18f, 9f, 21f, 12f, 18f, 15f) }
        Glyph.More -> { c(5.5f, 12f, 1.6f, true); c(12f, 12f, 1.6f, true); c(18.5f, 12f, 1.6f, true) }
        // Back points towards the start, Forward towards the end (both mirrored in right to left).
        Glyph.Back -> { line(5f, 12f, 19f, 12f); pl(11f, 6f, 5f, 12f, 11f, 18f) }
        Glyph.Forward -> { line(5f, 12f, 19f, 12f); pl(13f, 6f, 19f, 12f, 13f, 18f) }
        Glyph.Search -> { c(10.5f, 10.5f, 6f); line(15f, 15f, 20f, 20f) }
        Glyph.Plus -> { line(12f, 5f, 12f, 19f); line(5f, 12f, 19f, 12f) }
        else -> drawSettings(g)
    }
}

private fun GlyphPen.gamepad() {
    pl(7f, 7f, 17f, 7f, 21f, 15f, 19f, 18f, 15f, 15f, 9f, 15f, 5f, 18f, 3f, 15f, close = true)
    line(8f, 10f, 8f, 12.5f); line(6.75f, 11.25f, 9.25f, 11.25f); c(16f, 10.5f, 0.9f, true); c(17.8f, 12.3f, 0.9f, true)
}

private fun GlyphPen.eye() {
    pl(2.5f, 12f, 7f, 6.5f, 12f, 5f, 17f, 6.5f, 21.5f, 12f, 17f, 17.5f, 12f, 19f, 7f, 17.5f, close = true)
    c(12f, 12f, 3f)
}

private fun GlyphPen.pencil() { pl(4f, 20f, 5f, 15.5f, 15.5f, 5f, 19f, 8.5f, 8.5f, 19f, close = true); line(13f, 7.5f, 16.5f, 11f) }

private fun GlyphPen.bolt() = pl(13.5f, 2.5f, 6f, 13.5f, 11.5f, 13.5f, 10.5f, 21.5f, 18f, 10.5f, 12.5f, 10.5f, close = true)

/** The two screens of [Glyph.Screens], one of them [filled] ("top" or "bottom"). */
private fun GlyphPen.screens(top: Boolean = false, bottom: Boolean = false) { r(4f, 2.5f, 16f, 9f, 2f, top); r(6f, 13.5f, 12f, 8f, 2f, bottom) }

private fun GlyphPen.swapPair() {
    c(6f, 15.5f, 3f, true); c(18f, 15.5f, 3f)
    line(6f, 6f, 18f, 6f); pl(8.5f, 3.5f, 6f, 6f, 8.5f, 8.5f); pl(15.5f, 3.5f, 18f, 6f, 15.5f, 8.5f)
}

/** A speech bubble with its tail at the bottom start. */
private fun GlyphPen.bubble() = shape(close = true) {
    m(7f, 3.5f); l(17f, 3.5f); q(21f, 3.5f, 21f, 7.5f); l(21f, 12.5f); q(21f, 16.5f, 17f, 16.5f); l(11.5f, 16.5f); l(7f, 20.5f); l(7f, 16.5f)
    q(3f, 16.5f, 3f, 12.5f); l(3f, 7.5f); q(3f, 3.5f, 7f, 3.5f)
}

private fun GlyphPen.person(cx: Float = 12f) { c(cx, 8f, 4f); arc(cx, 21f, 7.5f, 180f, 180f) }

/** A plug: prongs up, cord down. */
private fun GlyphPen.plug() {
    line(9.5f, 3.5f, 9.5f, 7.5f); line(14.5f, 3.5f, 14.5f, 7.5f)
    shape(close = true) { m(6.5f, 7.5f); l(17.5f, 7.5f); l(17.5f, 10.5f); q(17.5f, 16f, 12f, 16f); q(6.5f, 16f, 6.5f, 10.5f) }
    line(12f, 16f, 12f, 20.5f)
}

private fun GlyphPen.drawSettings(g: Glyph) {
    when (g) {
        // Look & feel
        Glyph.Drop -> { // A drop of paint.
            shape(close = true) { m(12f, 3f); cu(12f, 3f, 5f, 10f, 5f, 14.5f); cu(5f, 18.4f, 8.1f, 21f, 12f, 21f); cu(15.9f, 21f, 19f, 18.4f, 19f, 14.5f); cu(19f, 10f, 12f, 3f, 12f, 3f) }
            arc(12f, 14.5f, 3.6f, 100f, 70f)
        }
        Glyph.Glass -> { r(3.5f, 3.5f, 17f, 17f, 4f); line(7.5f, 12.5f, 12.5f, 7.5f); line(9f, 16.5f, 16.5f, 9f) }
        Glyph.Handheld -> { r(2f, 6f, 20f, 12f, 4.5f); r(8f, 9f, 8f, 6f, 1f); c(5.2f, 12f, 1f, true); c(18.8f, 12f, 1f, true) }
        Glyph.Share -> {
            c(17f, 5.5f, 2.5f); c(6.5f, 12f, 2.5f); c(17f, 18.5f, 2.5f)
            line(8.7f, 10.6f, 14.8f, 6.9f); line(8.7f, 13.4f, 14.8f, 17.1f)
        }
        Glyph.Globe -> { c(12f, 12f, 8.5f); oval(8f, 3.5f, 8f, 17f); line(3.5f, 12f, 20.5f, 12f) }
        Glyph.Calendar -> {
            r(3.5f, 5.5f, 17f, 15f, 2.5f); line(3.5f, 10f, 20.5f, 10f); line(8f, 3.5f, 8f, 7f); line(16f, 3.5f, 16f, 7f)
            c(8f, 13.8f, 1f, true); c(12f, 13.8f, 1f, true); c(16f, 13.8f, 1f, true); c(8f, 17.2f, 1f, true); c(12f, 17.2f, 1f, true)
        }
        Glyph.Undo -> { pl(8.5f, 4.5f, 4.5f, 8.5f, 8.5f, 12.5f); shape { m(4.5f, 8.5f); l(14f, 8.5f); a(14f, 14f, 5.5f, -90f, 180f); l(8f, 19.5f) } }
        Glyph.TopScreen -> screens(top = true)
        Glyph.BottomScreen -> screens(bottom = true)
        Glyph.Dim -> { c(12f, 12f, 8.5f); shape(close = true, fill = true) { m(12f, 3.5f); a(12f, 12f, 8.5f, -90f, -180f) } }
        Glyph.Shapes -> { c(7.5f, 7.5f, 4f); r(13f, 3.5f, 8f, 8f, 1.5f); pl(12f, 13.5f, 16.5f, 20.5f, 7.5f, 20.5f, close = true) }
        Glyph.Box -> { pl(3.5f, 7.5f, 12f, 3.5f, 20.5f, 7.5f, 12f, 11.5f, close = true); pl(3.5f, 7.5f, 3.5f, 16.5f, 12f, 20.5f, 20.5f, 16.5f, 20.5f, 7.5f); line(12f, 11.5f, 12f, 20.5f) }
        Glyph.FetchArt -> { // A picture with art coming down into it.
            r(2.5f, 4f, 15f, 12.5f, 2.5f); pl(3f, 14.5f, 7.5f, 10f, 11f, 13f)
            cut(gap = 1.6f) { line(18f, 10f, 18f, 20.5f); pl(15f, 17.5f, 18f, 20.5f, 21f, 17.5f) }
        }
        Glyph.Bounce -> {
            line(3f, 21f, 21f, 21f)
            shape { m(3f, 4.5f); q(5f, 17.5f, 8f, 17.5f); q(11f, 17.5f, 13.5f, 10f) }
            c(16.8f, 6.8f, 2.8f, true)
        }
        Glyph.Snail -> {
            c(10f, 11.5f, 6f); arc(10f, 11.5f, 2.4f, 0f, 270f)
            shape { m(3.5f, 18.5f); l(17f, 18.5f); q(20f, 18.5f, 20f, 15.5f); l(20f, 13.5f) }
            line(20f, 13.5f, 18.5f, 10.5f); line(20f, 13.5f, 21.5f, 10.5f)
        }
        Glyph.HighlightMove -> { r(9.5f, 5.5f, 12f, 13f, 3.5f); line(3f, 9f, 6f, 9f); line(2f, 12f, 6f, 12f); line(3f, 15f, 6f, 15f) }
        Glyph.FloatText -> {
            line(6f, 4f, 18f, 4f); line(12f, 4f, 12f, 13f)
            shape { m(3.5f, 18.5f); q(5.6f, 16f, 7.8f, 18.5f); q(10f, 21f, 12f, 18.5f); q(14.2f, 16f, 16.3f, 18.5f); q(18.4f, 21f, 20.5f, 18.5f) }
        }
        Glyph.Gauge -> { arc(12f, 14f, 8.5f, 150f, 240f); line(12f, 14f, 16f, 9.5f); c(12f, 14f, 1.5f, true) }
        Glyph.Flip -> {
            r(3f, 9f, 7.5f, 11.5f, 2f); faint { r(13.5f, 9f, 7.5f, 11.5f, 2f) }
            arc(12f, 9.5f, 5.5f, 205f, 125f); pl(14.5f, 4.6f, 17f, 6.3f, 16.4f, 3.4f)
        }

        // Home & layout
        Glyph.IconSize -> { r(3f, 13f, 6f, 6f, 1.8f); r(11.5f, 8.5f, 10f, 10.5f, 2.5f) }
        Glyph.Scroll -> { pl(8f, 7.5f, 12f, 3.5f, 16f, 7.5f); pl(8f, 16.5f, 12f, 20.5f, 16f, 16.5f); line(12f, 9f, 12f, 15f) }
        Glyph.Tag -> { pl(4.5f, 4.5f, 11.5f, 4.5f, 19.8f, 12.8f, 12.8f, 19.8f, 4.5f, 11.5f, close = true); c(8.5f, 8.5f, 1.4f, true) }
        Glyph.FolderOpen -> { pl(3f, 18.5f, 3f, 5f, 9.5f, 5f, 11.5f, 7.5f, 18f, 7.5f, 18f, 10f); pl(3f, 18.5f, 6.5f, 10.5f, 21.5f, 10.5f, 18f, 18.5f, close = true) }
        Glyph.History -> { arc(13f, 12f, 7.5f, 180f, 270f); pl(3.3f, 9.8f, 5.5f, 12f, 7.7f, 9.8f); line(13f, 12f, 13f, 8f); line(13f, 12f, 15.8f, 13.8f) }
        Glyph.NowPlaying -> { r(3f, 4.5f, 18f, 15f, 3f); pl(10f, 8.5f, 15.5f, 12f, 10f, 15.5f, close = true) }
        Glyph.Dots9 -> { for (a in listOf(5.5f, 12f, 18.5f)) for (b in listOf(5.5f, 12f, 18.5f)) c(a, b, 1.7f, true) }
        Glyph.Orbit -> {
            c(12f, 12f, 8f); c(12f, 12f, 2.6f, true)
            cut { c(17.66f, 6.34f, 2f, true) }
        }
        Glyph.Ribbon -> { r(2f, 7.5f, 5.5f, 9f, 1.5f); r(9.25f, 7.5f, 5.5f, 9f, 1.5f); r(16.5f, 7.5f, 5.5f, 9f, 1.5f) }
        Glyph.Rail -> {
            r(3.5f, 3.5f, 17f, 10.5f, 2.5f)
            for (a in listOf(3.5f, 8.25f, 13f, 17.75f)) r(a, 16.5f, 2.75f, 3.5f, 0.8f, true)
        }
        Glyph.Backdrop -> {
            r(2.5f, 3f, 17f, 12.5f, 2.5f); pl(3f, 13.5f, 7.5f, 9f, 10.5f, 12f)
            cut(solid = true) { r(12f, 10.5f, 9f, 11f, 2f) }
        }
        Glyph.Stage -> { // A big cover on a stage.
            r(7.5f, 2.5f, 9f, 11.5f, 2f); pl(2.5f, 21f, 5f, 17f, 19f, 17f, 21.5f, 21f, close = true)
        }
        Glyph.Resize -> { r(3f, 9f, 9.5f, 12f, 2f); line(15f, 9f, 20.5f, 3.5f); pl(15.5f, 3.5f, 20.5f, 3.5f, 20.5f, 8.5f) }
        Glyph.Rows -> { r(3.5f, 3.5f, 17f, 4.5f, 1.5f); r(3.5f, 9.75f, 17f, 4.5f, 1.5f); r(3.5f, 16f, 17f, 4.5f, 1.5f) }
        Glyph.Tiles -> { r(3f, 4f, 8f, 7f, 2f); r(13f, 4f, 8f, 7f, 2f, true); r(3f, 13f, 8f, 7f, 2f); r(13f, 13f, 8f, 7f, 2f) }
        Glyph.Covers -> { r(3f, 3f, 10f, 14f, 2f); cut(solid = true) { r(10.5f, 6.5f, 10f, 14f, 2f) } }
        Glyph.Dock -> { r(3f, 3.5f, 18f, 17f, 3f); r(6.5f, 14.5f, 11f, 3f, 1.5f, true) }
        Glyph.Screensaver -> { r(2.5f, 3.5f, 19f, 13.5f, 2.5f); line(9f, 20.5f, 15f, 20.5f); spark(12f, 10.25f, 4f) }
        Glyph.GridRows -> {
            for (a in listOf(2.5f, 7.5f, 12.5f)) for (b in listOf(4.5f, 10.25f, 16f)) r(a, b, 3.5f, 3.5f, 0.8f, true)
            line(19.5f, 4.5f, 19.5f, 19.5f); pl(17.5f, 6.5f, 19.5f, 4.5f, 21.5f, 6.5f); pl(17.5f, 17.5f, 19.5f, 19.5f, 21.5f, 17.5f)
        }
        Glyph.Spacing -> {
            r(2.5f, 5f, 4.5f, 14f, 1.5f); r(17f, 5f, 4.5f, 14f, 1.5f)
            line(9.5f, 12f, 14.5f, 12f); pl(11.5f, 10f, 9.5f, 12f, 11.5f, 14f); pl(12.5f, 10f, 14.5f, 12f, 12.5f, 14f)
        }
        Glyph.Split -> { r(3f, 4f, 18f, 16f, 2.5f); line(14f, 4f, 14f, 20f); line(14f, 12f, 21f, 12f) }
        Glyph.AnimIcon -> {
            r(3f, 7.5f, 13.5f, 13.5f, 3.5f)
            cut { spark(17.5f, 6.5f, 4f) }
        }
        Glyph.WideCard -> { r(2.5f, 6f, 19f, 12f, 3f); pl(6.5f, 9.5f, 10.5f, 12f, 6.5f, 14.5f, close = true, fill = true); line(13.5f, 10f, 18f, 10f); line(13.5f, 14f, 16.5f, 14f) }
        Glyph.Views -> { r(3f, 4f, 6.5f, 6.5f, 1.5f); line(13f, 7.25f, 21f, 7.25f); r(3f, 13.5f, 6.5f, 6.5f, 1.5f); line(13f, 16.75f, 21f, 16.75f) }
        Glyph.CardShape -> { r(3f, 4f, 9f, 16f, 2.5f); c(17f, 12f, 4f) }
        Glyph.Shelf -> {
            line(2.5f, 20.5f, 21.5f, 20.5f); r(4f, 7f, 3.5f, 13.5f, 1f); r(9.5f, 4f, 3.5f, 16.5f, 1f)
            pl(14.6f, 8.6f, 17.9f, 7.7f, 21.2f, 19.6f, 17.9f, 20.5f, close = true)
        }
        Glyph.Checklist -> {
            pl(3f, 6.5f, 4.7f, 8.2f, 7.5f, 5f); line(11f, 6.5f, 21f, 6.5f)
            pl(3f, 12.5f, 4.7f, 14.2f, 7.5f, 11f); line(11f, 12.5f, 21f, 12.5f)
            c(5f, 18.5f, 1.6f); line(11f, 18.5f, 21f, 18.5f)
        }
        Glyph.Roller -> { r(3.5f, 3.5f, 14f, 5.5f, 2f); pl(17.5f, 6.25f, 20.5f, 6.25f, 20.5f, 11.5f, 12f, 11.5f, 12f, 14.5f); r(10.5f, 14.5f, 3f, 6.5f, 1f, true) }
        Glyph.DockTop -> { r(3f, 3.5f, 18f, 17f, 3f); r(6.5f, 6.5f, 11f, 3f, 1.5f, true); pl(9.5f, 16f, 12f, 13.5f, 14.5f, 16f) }
        Glyph.EyeOff -> { eye(); slash() }
        Glyph.Widgets -> { r(3.5f, 3.5f, 7f, 7f, 2f); c(17f, 7f, 3.5f); r(3.5f, 13.5f, 7f, 7f, 2f); r(13.5f, 13.5f, 7f, 7f, 2f) }

        // Two screens
        Glyph.OneScreen -> { r(4f, 2.5f, 16f, 9f, 2f); pl(10.6f, 6.1f, 12f, 5f, 12f, 9f); faint { r(6f, 13.5f, 12f, 8f, 2f) } }
        Glyph.OpenOn -> { screens(); r(10.5f, 15.75f, 3f, 3.5f, 0.8f, true); line(12f, 5f, 12f, 8.5f); pl(10f, 7f, 12f, 9f, 14f, 7f) }
        Glyph.HoldButton -> { c(12f, 12f, 3.5f, true); arc(12f, 12f, 8f, -90f, 250f) }
        Glyph.Rotate -> {
            r(3.5f, 9.5f, 8f, 11.5f, 2f)
            arc(11.5f, 12.5f, 8f, -95f, 95f); pl(17.5f, 10.5f, 19.5f, 12.5f, 21.5f, 10.5f)
        }
        Glyph.BottomDetails -> { screens(); line(9f, 16.5f, 15f, 16.5f); line(9f, 18.5f, 12.5f, 18.5f) }
        Glyph.BottomWidgets -> { screens(); r(8.5f, 16f, 3f, 3f, 0.8f, true); r(12.5f, 16f, 3f, 3f, 0.8f, true) }
        Glyph.BottomOff -> { // The bottom screen, switched off.
            r(4f, 2.5f, 16f, 9f, 2f); r(6f, 13.5f, 12f, 8f, 2f, true)
            erase { arc(12f, 17.8f, 2.4f, -55f, 290f); line(12f, 14.9f, 12f, 17.4f) }
        }
        Glyph.Timer -> { c(12f, 13.5f, 7.5f); line(12f, 13.5f, 12f, 9.5f); line(10f, 3f, 14f, 3f); line(12f, 3f, 12f, 6f); line(18f, 7f, 19.5f, 5.5f) }
        Glyph.Film -> {
            r(4f, 3f, 16f, 18f, 2.5f); line(8f, 3f, 8f, 21f); line(16f, 3f, 16f, 21f)
            line(4f, 8.5f, 8f, 8.5f); line(4f, 15.5f, 8f, 15.5f); line(16f, 8.5f, 20f, 8.5f); line(16f, 15.5f, 20f, 15.5f)
        }
        Glyph.Mic -> { r(9f, 3f, 6f, 11f, 3f); arc(12f, 10.5f, 6.5f, 0f, 180f); line(12f, 17f, 12f, 20.5f); line(9f, 20.5f, 15f, 20.5f) }
        Glyph.Video -> { r(2.5f, 6.5f, 13f, 11f, 2.5f); pl(15.5f, 10.5f, 21f, 7.5f, 21f, 16.5f, 15.5f, 13.5f, close = true) }
        Glyph.Pan -> {
            r(2.5f, 4.5f, 19f, 15f, 2.5f); line(7f, 12f, 17f, 12f)
            pl(9.5f, 9.5f, 7f, 12f, 9.5f, 14.5f); pl(14.5f, 9.5f, 17f, 12f, 14.5f, 14.5f)
        }
        Glyph.TextSize -> { line(3f, 11f, 9f, 11f); line(6f, 11f, 6f, 19f); line(10.5f, 5f, 20.5f, 5f); line(15.5f, 5f, 15.5f, 19f) }
        Glyph.Fade -> {
            r(3f, 4f, 18f, 16f, 2.5f); r(6f, 7f, 3.5f, 10f, 1f, true); line(13f, 7.5f, 13f, 16.5f)
            c(17f, 8f, 0.9f, true); c(17f, 12f, 0.9f, true); c(17f, 16f, 0.9f, true)
        }

        // Sound & music
        Glyph.Cassette -> {
            r(2.5f, 5f, 19f, 14f, 2.5f); c(8.5f, 10.5f, 1.8f); c(15.5f, 10.5f, 1.8f); line(10.3f, 10.5f, 13.7f, 10.5f)
            pl(6.5f, 19f, 8f, 15.5f, 16f, 15.5f, 17.5f, 19f)
        }
        Glyph.Waveform -> { line(4f, 10.5f, 4f, 13.5f); line(8f, 7f, 8f, 17f); line(12f, 3.5f, 12f, 20.5f); line(16f, 8f, 16f, 16f); line(20f, 10f, 20f, 14f) }
        Glyph.Bubble -> { bubble(); line(7.5f, 8.5f, 16.5f, 8.5f); line(7.5f, 12f, 13f, 12f) }
        Glyph.Headphones -> { arc(12f, 13f, 8f, 180f, 180f); r(3f, 12.5f, 4.5f, 8f, 2f); r(16.5f, 12.5f, 4.5f, 8f, 2f) }
        Glyph.Shuffle -> {
            pl(3f, 7f, 7f, 7f, 15f, 17f, 20.5f, 17f); pl(3f, 17f, 7f, 17f, 15f, 7f, 20.5f, 7f)
            pl(18f, 4.5f, 20.5f, 7f, 18f, 9.5f); pl(18f, 14.5f, 20.5f, 17f, 18f, 19.5f)
        }
        Glyph.Skip -> { pl(5f, 5f, 15f, 12f, 5f, 19f, close = true); line(18.5f, 5f, 18.5f, 19f) }

        // Controls
        Glyph.Buttons -> { c(12f, 5.5f, 2.6f); c(5.5f, 12f, 2.6f); c(18.5f, 12f, 2.6f); c(12f, 18.5f, 2.6f, true) }
        // Two buttons side by side (A and B) or one over the other (X and Y), trading places.
        Glyph.SwapAB -> swapPair()
        Glyph.SwapXY -> turned(-90f) { swapPair() }
        Glyph.XButton -> { c(12f, 12f, 8.5f); line(9f, 9f, 15f, 15f); line(15f, 9f, 9f, 15f) }
        Glyph.Rebind -> {
            c(6.5f, 6.5f, 3f); c(17.5f, 17.5f, 3f, true)
            shape { m(9.5f, 6.5f); q(17.5f, 6.5f, 17.5f, 12.5f) }; pl(15.3f, 10.6f, 17.5f, 12.8f, 19.7f, 10.6f)
        }
        Glyph.Vibrate -> {
            r(8f, 4f, 8f, 16f, 2f)
            pl(4.5f, 8f, 2.8f, 10f, 4.5f, 12f, 2.8f, 14f, 4.5f, 16f); pl(19.5f, 8f, 21.2f, 10f, 19.5f, 12f, 21.2f, 14f, 19.5f, 16f)
        }
        Glyph.CornerHint -> { r(3f, 4f, 18f, 16f, 2.5f); c(16.5f, 8.5f, 1.8f, true); pl(6.5f, 16.5f, 6.5f, 13f); pl(6.5f, 16.5f, 10f, 16.5f) }
        Glyph.Plug -> plug()
        Glyph.Link -> turned(-45f) { r(1.5f, 8.5f, 12f, 7f, 3.5f); r(10.5f, 8.5f, 12f, 7f, 3.5f) }
        Glyph.Keyboard -> {
            r(2f, 6f, 20f, 12f, 2.5f)
            for (a in listOf(6f, 10f, 14f, 18f)) { c(a, 9.5f, 1f, true); c(a, 12.5f, 1f, true) }
            line(8.5f, 15.2f, 15.5f, 15.2f)
        }
        Glyph.HomeButton -> { c(12f, 12f, 8.5f); pl(8.3f, 12.2f, 12f, 8.7f, 15.7f, 12.2f); pl(9.5f, 11.3f, 9.5f, 15.5f, 14.5f, 15.5f, 14.5f, 11.3f) }
        Glyph.Trackpad -> { r(3f, 3.5f, 18f, 17f, 3f); line(3f, 15f, 21f, 15f); line(12f, 15f, 12f, 20.5f); c(10f, 9f, 1.8f, true) }

        // Library & art
        Glyph.Robot -> {
            arc(12f, 17.5f, 8f, 180f, 180f); line(4f, 17.5f, 20f, 17.5f)
            c(9f, 14f, 1f, true); c(15f, 14f, 1f, true); line(8f, 10.8f, 6.2f, 7.5f); line(16f, 10.8f, 17.8f, 7.5f)
        }
        Glyph.Monitor -> { r(2.5f, 3.5f, 19f, 13f, 2.5f); line(12f, 16.5f, 12f, 20f); line(8f, 20.5f, 16f, 20.5f) }
        Glyph.Sources -> {
            oval(5f, 3f, 14f, 5f); line(5f, 5.5f, 5f, 18.5f); line(19f, 5.5f, 19f, 18.5f)
            ovalArc(5f, 9.5f, 14f, 5f, 0f, 180f); ovalArc(5f, 16f, 14f, 5f, 0f, 180f)
        }
        Glyph.Person -> person()
        Glyph.Medal -> { pl(7.5f, 3f, 10.5f, 9.5f); pl(16.5f, 3f, 13.5f, 9.5f); c(12f, 15f, 5.5f); c(12f, 15f, 1.8f, true) }
        Glyph.SignOut -> { pl(12.5f, 4f, 5f, 4f, 5f, 20f, 12.5f, 20f); line(10f, 12f, 20.5f, 12f); pl(17f, 8.5f, 20.5f, 12f, 17f, 15.5f) }
        Glyph.Unlink -> turned(-45f) {
            r(0.5f, 8.5f, 10f, 7f, 3.5f); r(13.5f, 8.5f, 10f, 7f, 3.5f)
            line(12f, 4f, 12f, 6f); line(12f, 18f, 12f, 20f)
        }
        Glyph.Presence -> { c(11f, 11f, 7.5f); cut { c(17.5f, 17.5f, 3f, true) } }
        Glyph.Unplug -> { plug(); slash() }
        Glyph.Strike -> { pl(5.5f, 20f, 12f, 4f, 18.5f, 20f); line(8f, 14.5f, 16f, 14.5f); slash() }
        Glyph.Disc -> { c(12f, 12f, 8.5f); c(12f, 12f, 2.2f); arc(12f, 12f, 5.5f, 200f, 60f) }

        // Play & system
        Glyph.PlayCircle -> { c(12f, 12f, 8.5f); pl(10f, 8.2f, 15.8f, 12f, 10f, 15.8f, close = true) }
        Glyph.Resume -> { // Play, picking up where it was.
            arc(12f, 12f, 8.5f, -50f, 290f); pl(20.6f, 4.4f, 17.5f, 5.5f, 18.2f, 8.7f)
            pl(10f, 8.5f, 15f, 12f, 10f, 15.5f, close = true)
        }
        Glyph.CloseWindow -> { r(3f, 4f, 18f, 16f, 2.5f); line(3f, 8.5f, 21f, 8.5f); line(9.5f, 11.5f, 14.5f, 16.5f); line(14.5f, 11.5f, 9.5f, 16.5f) }
        Glyph.AskBubble -> {
            bubble()
            shape { m(9.8f, 8f); q(9.8f, 5.8f, 12f, 5.8f); q(14.2f, 5.8f, 14.2f, 7.9f); q(14.2f, 9.4f, 12f, 10.3f); l(12f, 11f) }
            c(12f, 13.6f, 1f, true)
        }
        Glyph.Fullscreen -> { pl(3.5f, 9f, 3.5f, 3.5f, 9f, 3.5f); pl(15f, 3.5f, 20.5f, 3.5f, 20.5f, 9f); pl(20.5f, 15f, 20.5f, 20.5f, 15f, 20.5f); pl(9f, 20.5f, 3.5f, 20.5f, 3.5f, 15f) }
        Glyph.Rocket -> {
            shape(close = true) { m(12f, 2.5f); cu(15.6f, 5f, 16.6f, 9.5f, 15.5f, 15.5f); l(8.5f, 15.5f); cu(7.4f, 9.5f, 8.4f, 5f, 12f, 2.5f) }
            c(12f, 9.5f, 1.6f); pl(8.7f, 12f, 5.5f, 15f, 5.5f, 18.5f, 8.9f, 16.8f); pl(15.3f, 12f, 18.5f, 15f, 18.5f, 18.5f, 15.1f, 16.8f)
            line(12f, 18.5f, 12f, 21.5f)
        }
        Glyph.MoonBolt -> {
            crescent(10f, 10.5f, 7.5f, 4.5f, -3.5f, 6f)
            cut(solid = true) { at(10.5f, 9f, 0.55f) { bolt() } }
        }
        Glyph.Bump -> {
            c(6.5f, 9.5f, 2.6f); arc(6.5f, 20f, 4.2f, 180f, 180f)
            c(17.5f, 9.5f, 2.6f); arc(17.5f, 20f, 4.2f, 180f, 180f)
            spark(12f, 4.5f, 3f)
        }
        Glyph.Update -> {
            arc(12f, 12f, 8.5f, -60f, 300f); pl(20.6f, 2.9f, 16.3f, 4.6f, 18.2f, 8.4f)
            line(12f, 7.5f, 12f, 15.5f); pl(9f, 12.5f, 12f, 15.5f, 15f, 12.5f)
        }
        Glyph.Wrench -> { arc(15.5f, 8.5f, 4.5f, 0f, 270f); line(12.3f, 11.7f, 4.5f, 19.5f) }
        Glyph.Bolt -> bolt()
        Glyph.Fan -> {
            c(12f, 12f, 9f)
            at(3.6f, 3.6f, 0.7f) {
                c(12f, 12f, 2f, true)
                for (t in listOf(0f, 120f, 240f)) turned(t) {
                    shape(close = true) { m(12f, 9.5f); cu(9.5f, 8.5f, 9f, 4f, 12f, 3f); cu(15f, 2.5f, 16f, 6.5f, 13.8f, 9.8f) }
                }
            }
        }
        Glyph.Hz -> {
            r(2.5f, 3.5f, 19f, 13f, 2.5f); line(9f, 20.5f, 15f, 20.5f)
            shape { m(6f, 10f); q(7.75f, 5.5f, 9.5f, 10f); q(11.25f, 14.5f, 13f, 10f); q(14.75f, 5.5f, 16.5f, 10f); q(17.4f, 12.2f, 18f, 12f) }
        }
        Glyph.Sliders -> {
            line(3.5f, 6f, 20.5f, 6f); line(3.5f, 12f, 20.5f, 12f); line(3.5f, 18f, 20.5f, 18f)
            for ((a, b) in listOf(15f to 6f, 8f to 12f, 13f to 18f)) cut(solid = true) { c(a, b, 2.1f) }
        }
        Glyph.Moon -> crescent(11.5f, 12.5f, 8.5f, 5f, -4.5f, 7f)
        Glyph.WifiOff -> {
            arc(12f, 19f, 3.8f, 225f, 90f); arc(12f, 19f, 8f, 225f, 90f); arc(12f, 19f, 12.2f, 228f, 84f); c(12f, 19f, 1.3f, true)
            slash()
        }
        Glyph.Lock -> { r(5f, 10.5f, 14f, 10f, 2.5f); arc(12f, 8f, 4f, 180f, 180f); line(8f, 8f, 8f, 10.5f); line(16f, 8f, 16f, 10.5f); c(12f, 15.5f, 1.4f, true) }
        Glyph.FileIn -> {
            pl(5.5f, 3f, 13.5f, 3f, 18.5f, 8f, 18.5f, 21f, 5.5f, 21f, close = true); pl(13.5f, 3f, 13.5f, 8f, 18.5f, 8f)
            line(12f, 10.5f, 12f, 17f); pl(9.5f, 14.5f, 12f, 17f, 14.5f, 14.5f)
        }
        Glyph.Export -> {
            line(3.5f, 20.5f, 20.5f, 20.5f); line(5.5f, 20.5f, 5.5f, 15.5f); line(10f, 20.5f, 10f, 12f)
            line(13f, 14f, 20f, 7f); pl(15f, 6.5f, 20.5f, 6.5f, 20.5f, 12f)
        }
        Glyph.Gift -> {
            r(3f, 7.5f, 18f, 4.5f, 1.5f); pl(4.5f, 12f, 4.5f, 20.5f, 19.5f, 20.5f, 19.5f, 12f); line(12f, 7.5f, 12f, 20.5f)
            shape { m(12f, 7.5f); cu(10f, 3f, 5.5f, 4f, 7.5f, 7.5f) }; shape { m(12f, 7.5f); cu(14f, 3f, 18.5f, 4f, 16.5f, 7.5f) }
        }
        Glyph.Megaphone -> {
            pl(3.5f, 9.5f, 3.5f, 14.5f, 8f, 14.5f, 17f, 19.5f, 17f, 4.5f, 8f, 9.5f, close = true); line(7f, 14.5f, 8.5f, 20f)
            line(20f, 9.5f, 21.5f, 8.5f); line(20f, 14.5f, 21.5f, 15.5f)
        }
        Glyph.Bug -> {
            oval(7f, 8f, 10f, 13f); arc(12f, 8.5f, 3f, 180f, 180f); line(12f, 11f, 12f, 21f)
            line(7f, 11.5f, 3.5f, 9.5f); line(7f, 15f, 3f, 15f); line(7.5f, 18.5f, 4f, 20.5f)
            line(17f, 11.5f, 20.5f, 9.5f); line(17f, 15f, 21f, 15f); line(16.5f, 18.5f, 20f, 20.5f)
        }
        Glyph.GamepadSpark -> {
            at(0f, 5.5f, 0.82f) { gamepad() }
            cut { spark(18f, 5f, 3.8f) }
        }
        Glyph.FacePencil -> {
            c(10f, 13.5f, 7f); c(7.8f, 12.5f, 1f, true); c(12.2f, 12.5f, 1f, true); arc(10f, 13.5f, 3.6f, 35f, 110f)
            cut(solid = true) { at(11f, 0f, 0.55f) { pencil() } }
        }
        Glyph.CardEye -> {
            r(2.5f, 3.5f, 15f, 12f, 2.5f); c(7.5f, 8f, 1.8f); line(11.5f, 7f, 14.5f, 7f); line(11.5f, 10.5f, 13.5f, 10.5f)
            cut(solid = true) { at(8.5f, 9.5f, 0.62f) { eye() } }
        }
        Glyph.Fountain -> {
            line(3f, 14.5f, 21f, 14.5f); shape { m(4.5f, 14.5f); q(5.5f, 20f, 12f, 20f); q(18.5f, 20f, 19.5f, 14.5f) }
            line(12f, 14.5f, 12f, 8f); shape { m(12f, 8f); q(7f, 4f, 5.5f, 11.5f) }; shape { m(12f, 8f); q(17f, 4f, 18.5f, 11.5f) }
            c(12f, 4f, 1.2f, true)
        }
        Glyph.PersonOff -> { // A person with a no-entry badge.
            at(-0.5f, 0f, 0.85f) { person() }
            cut(solid = true) { c(17.5f, 17f, 4.3f) }
            line(14.6f, 14.1f, 20.4f, 19.9f)
        }
        Glyph.Fingerprint -> {
            arc(12f, 12.5f, 8.5f, 200f, 140f)
            arc(12f, 13f, 5.5f, 190f, 160f); line(17.5f, 13f, 17.5f, 15f)
            shape { m(6.5f, 13f); q(6.5f, 18f, 9.5f, 20.5f) }
            shape { m(12f, 11f); q(12f, 18f, 15f, 20f) }
            arc(12f, 13f, 2.5f, 180f, 180f); line(9.5f, 13f, 9.5f, 16f)
        }
        else -> Unit
    }
}

/** [Glyph.Battery] filled to [level] (0–100), so the icon agrees with the percentage beside it. */
@Composable
fun BatteryIcon(level: Int, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxSize()) {
        val u = size.minDimension / 24f
        val stroke = Stroke(width = 2.1f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
        drawRoundRect(color, Offset(3f * u, 7f * u), Size(16f * u, 10f * u), CornerRadius(2.5f * u), style = stroke)
        drawLine(color, Offset(21f * u, 10.5f * u), Offset(21f * u, 13.5f * u), stroke.width, StrokeCap.Round)
        // The inside is 11 wide; a sliver stays visible above 0 so a nearly empty battery still reads as a battery.
        val w = if (level <= 0) 0f else maxOf(1.5f, 11f * level.coerceAtMost(100) / 100f)
        if (w > 0f) drawRoundRect(color, Offset(5.5f * u, 9.5f * u), Size(w * u, 5f * u), CornerRadius(1f * u))
    }
}
