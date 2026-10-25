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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Tandem's small icon set, drawn in code with one rounded-stroke style so every icon matches (and there are no
 * icon assets to license). Coordinates are on a 24x24 grid.
 */
enum class Glyph {
    Theme, Sparkle, Grid, Image, Music, Volume, Gamepad, Screens, Clock, Battery, Eye, Folder, Sort, Refresh,
    Download, Save, Restore, Info, Heart, Motion, Corners, Layers, Text, Home, Swap, Highlight, Bell,
    Play, Trash, Pencil, Star, Close, Move, More, Back, Search, Plus,
    Chart, Trophy, Key, Shield, Cartridge, Wand
}

/** A glyph on a soft tinted chip, as used in settings rows. */
@Composable
fun GlyphChip(glyph: Glyph, color: Color, size: Dp = 30.dp, modifier: Modifier = Modifier) {
    Box(
        modifier.size(size).clip(RoundedCornerShape(size * 0.32f)).background(color.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
    ) { GlyphIcon(glyph, color, Modifier.padding(size * 0.2f)) }
}

@Composable
fun GlyphIcon(glyph: Glyph, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxSize()) {
        val u = size.minDimension / 24f
        val stroke = Stroke(width = 2.1f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun p(x: Float, y: Float) = Offset(x * u, y * u)
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(color, p(x1, y1), p(x2, y2), stroke.width, StrokeCap.Round)
        fun circle(x: Float, y: Float, r: Float, fill: Boolean = false) =
            if (fill) drawCircle(color, r * u, p(x, y)) else drawCircle(color, r * u, p(x, y), style = stroke)
        fun rect(x: Float, y: Float, w: Float, h: Float, r: Float, fill: Boolean = false) =
            if (fill) drawRoundRect(color, p(x, y), Size(w * u, h * u), CornerRadius(r * u))
            else drawRoundRect(color, p(x, y), Size(w * u, h * u), CornerRadius(r * u), style = stroke)
        fun path(vararg pts: Float, close: Boolean = false) {
            val path = Path()
            path.moveTo(pts[0] * u, pts[1] * u)
            var i = 2
            while (i < pts.size) { path.lineTo(pts[i] * u, pts[i + 1] * u); i += 2 }
            if (close) path.close()
            drawPath(path, color, style = stroke)
        }
        draw(glyph, ::line, ::circle, ::rect, ::path, u)
    }
}

private fun DrawScope.draw(
    g: Glyph,
    line: (Float, Float, Float, Float) -> Unit,
    circle: (Float, Float, Float, Boolean) -> Unit,
    rect: (Float, Float, Float, Float, Float, Boolean) -> Unit,
    path: (FloatArray, Boolean) -> Unit,
    u: Float,
) {
    fun c(x: Float, y: Float, r: Float, fill: Boolean = false) = circle(x, y, r, fill)
    fun r(x: Float, y: Float, w: Float, h: Float, rad: Float, fill: Boolean = false) = rect(x, y, w, h, rad, fill)
    fun pl(vararg pts: Float, close: Boolean = false) = path(pts, close)
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
        Glyph.Gamepad -> {
            pl(7f, 7f, 17f, 7f, 21f, 15f, 19f, 18f, 15f, 15f, 9f, 15f, 5f, 18f, 3f, 15f, close = true)
            line(8f, 10f, 8f, 12.5f); line(6.75f, 11.25f, 9.25f, 11.25f); c(16f, 10.5f, 0.9f, true); c(17.8f, 12.3f, 0.9f, true)
        }
        Glyph.Screens -> { r(4f, 2.5f, 16f, 9f, 2f); r(6f, 13.5f, 12f, 8f, 2f) }
        Glyph.Clock -> { c(12f, 12f, 8.5f); line(12f, 12f, 12f, 7f); line(12f, 12f, 15.5f, 14f) }
        Glyph.Battery -> { r(3f, 7f, 16f, 10f, 2.5f); r(5.5f, 9.5f, 7f, 5f, 1f, true); line(21f, 10.5f, 21f, 13.5f) }
        Glyph.Eye -> {
            pl(2.5f, 12f, 7f, 6.5f, 12f, 5f, 17f, 6.5f, 21.5f, 12f, 17f, 17.5f, 12f, 19f, 7f, 17.5f, close = true)
            c(12f, 12f, 3f)
        }
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
        Glyph.Pencil -> { pl(4f, 20f, 5f, 15.5f, 15.5f, 5f, 19f, 8.5f, 8.5f, 19f, close = true); line(13f, 7.5f, 16.5f, 11f) }
        Glyph.Star -> { pl(12f, 3f, 14.6f, 9f, 21f, 9.4f, 16f, 13.5f, 17.6f, 20f, 12f, 16.4f, 6.4f, 20f, 8f, 13.5f, 3f, 9.4f, 9.4f, 9f, close = true) }
        Glyph.Close -> { line(6f, 6f, 18f, 18f); line(18f, 6f, 6f, 18f) }
        Glyph.Move -> { line(12f, 3f, 12f, 21f); line(3f, 12f, 21f, 12f); pl(9f, 6f, 12f, 3f, 15f, 6f); pl(9f, 18f, 12f, 21f, 15f, 18f); pl(6f, 9f, 3f, 12f, 6f, 15f); pl(18f, 9f, 21f, 12f, 18f, 15f) }
        Glyph.More -> { c(5.5f, 12f, 1.6f, true); c(12f, 12f, 1.6f, true); c(18.5f, 12f, 1.6f, true) }
        Glyph.Back -> { line(5f, 12f, 19f, 12f); pl(11f, 6f, 5f, 12f, 11f, 18f) }
        Glyph.Search -> { c(10.5f, 10.5f, 6f); line(15f, 15f, 20f, 20f) }
        Glyph.Plus -> { line(12f, 5f, 12f, 19f); line(5f, 12f, 19f, 12f) }
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
