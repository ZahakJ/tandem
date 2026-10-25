package io.github.zahakj.tandem.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/*
 * Tandem's own icon family, drawn (never bitmaps): a colour tile with a soft light, a faint checker
 * pattern, and a chunky white symbol with a soft drop in the tile's own deep colour. Used for collections, genres,
 * user folders without contents and the built-in apps, so no two tiles on the grid ever look alike.
 */

enum class TIcon {
    // Built-in apps
    Gallery, Journal, Themes, Stats, Trophy, UpNext, Files, Controller, Gear,
    // Collections
    Heart, Flame, NewBox, Rewind, Sprout, Genres, AllApps,
    // Genres
    Sword, Flag, Puzzle, Target, Ball, Fist, Ghost, Note, Chess, Jump, Star, Bolt, Planet, Leaf, Diamond, Book,
    // Built-in folders (FolderLooks.kt)
    Apps, Emulator, Folder,
}

/** The icon for a genre name: a symbol that fits it, or one picked steadily from the name. */
fun genreIcon(name: String): TIcon {
    val n = name.lowercase()
    return when {
        "rpg" in n || "role" in n -> TIcon.Sword
        "adventure" in n -> TIcon.Book
        "racing" in n || "driving" in n || "race" in n -> TIcon.Flag
        "puzzle" in n -> TIcon.Puzzle
        "shoot" in n || "shmup" in n || "fps" in n || "gun" in n -> TIcon.Target
        "sport" in n || "soccer" in n || "football" in n || "golf" in n || "tennis" in n -> TIcon.Ball
        "fight" in n || "beat" in n || "brawl" in n -> TIcon.Fist
        "horror" in n || "survival" in n -> TIcon.Ghost
        "music" in n || "rhythm" in n || "danc" in n -> TIcon.Note
        "strateg" in n || "tactic" in n || "board" in n || "card" in n -> TIcon.Chess
        "platform" in n || "jump" in n -> TIcon.Jump
        "action" in n -> TIcon.Bolt
        "sim" in n || "life" in n || "farm" in n -> TIcon.Leaf
        "space" in n || "sci" in n -> TIcon.Planet
        else -> listOf(TIcon.Star, TIcon.Diamond, TIcon.Planet, TIcon.Leaf, TIcon.Bolt)[(n.hashCode() and 0x7fffffff) % 5]
    }
}

/** A full tile: [color] background, the [icon] in white. Fills its box; the caller clips it to the tile shape. */
@Composable
fun TandemIconTile(icon: TIcon, color: Color, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().drawWithCache {
        val w = size.width
        val h = size.height
        val base = Brush.linearGradient(
            listOf(lerp(color, Color.White, 0.22f), color, lerp(color, Color.Black, 0.18f)),
            start = Offset(0f, 0f), end = Offset(w * 0.6f, h),
        )
        val glow = Brush.radialGradient(listOf(Color.White.copy(alpha = 0.28f), Color.Transparent), center = Offset(w * 0.2f, h * 0.08f), radius = w * 0.8f)
        val cell = w / 3f
        val check = Color.White.copy(alpha = 0.08f)
        val deep = lerp(color, Color.Black, 0.42f).copy(alpha = 0.55f)
        onDrawBehind {
            drawRect(base)
            for (i in 0 until 3) for (j in 0 until 3) if ((i + j) % 2 == 1) drawRect(check, Offset(i * cell, j * cell), Size(cell, cell))
            drawRect(glow)
            // The symbol, centred at 62% of the tile, with a soft drop under it.
            val s = w * 0.62f
            val at = Offset((w - s) / 2f, (h - s) / 2f)
            translate(at.x, at.y + w * 0.025f) { drawTIcon(icon, s, deep, color) }
            translate(at.x, at.y) { drawTIcon(icon, s, Color.White, color) }
        }
    })
}

/** Just the symbol, in [ink], on a transparent background (menus and headers). */
@Composable
fun TIconGlyph(icon: TIcon, ink: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxSize()) {
        val s = size.minDimension
        translate((size.width - s) / 2f, (size.height - s) / 2f) { drawTIcon(icon, s, ink, Color.Transparent) }
    }
}

/**
 * Draws [icon] in a square of [s] px at the origin, in [ink]. [cut] is the colour of details punched into filled
 * shapes (the tile colour), so the symbol reads as one chunky sticker.
 */
fun DrawScope.drawTIcon(icon: TIcon, s: Float, ink: Color, cut: Color) {
    val u = s / 24f
    val sw = 2.4f * u
    val stroke = Stroke(sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
    fun p(x: Float, y: Float) = Offset(x * u, y * u)
    fun line(x1: Float, y1: Float, x2: Float, y2: Float, c: Color = ink, w: Float = sw) = drawLine(c, p(x1, y1), p(x2, y2), w, StrokeCap.Round)
    fun circle(x: Float, y: Float, r: Float, fill: Boolean = true, c: Color = ink) =
        if (fill) drawCircle(c, r * u, p(x, y)) else drawCircle(c, r * u, p(x, y), style = stroke)
    fun rect(x: Float, y: Float, w: Float, h: Float, r: Float, fill: Boolean = true, c: Color = ink) =
        if (fill) drawRoundRect(c, p(x, y), Size(w * u, h * u), CornerRadius(r * u))
        else drawRoundRect(c, p(x, y), Size(w * u, h * u), CornerRadius(r * u), style = stroke)
    fun poly(vararg pts: Float, fill: Boolean = true, c: Color = ink, close: Boolean = true) {
        val path = Path()
        path.moveTo(pts[0] * u, pts[1] * u)
        var i = 2
        while (i < pts.size) { path.lineTo(pts[i] * u, pts[i + 1] * u); i += 2 }
        if (close) path.close()
        if (fill) drawPath(path, c) else drawPath(path, c, style = stroke)
    }
    val hole = if (cut.alpha < 0.01f) Color.Transparent else cut
    // Details punched into a filled shape: the tile colour, or (on a transparent glyph) just left out.
    fun punch(block: () -> Unit) { if (hole != Color.Transparent) block() }
    when (icon) {
        TIcon.Gallery -> {
            rect(2.5f, 4f, 19f, 16f, 3.5f)
            punch { rect(4.5f, 6f, 15f, 12f, 2f, c = hole) }
            if (hole == Color.Transparent) rect(2.5f, 4f, 19f, 16f, 3.5f, fill = false)
            circle(15.5f, 9.5f, 1.9f)
            poly(4.5f, 18f, 10f, 11f, 13.5f, 15f, 15.5f, 13f, 19.5f, 18f)
        }
        TIcon.Journal -> {
            rect(4f, 2.5f, 15f, 19f, 3f)
            punch {
                line(8.5f, 8f, 15.5f, 8f, hole, 1.8f * u); line(8.5f, 11.5f, 15.5f, 11.5f, hole, 1.8f * u); line(8.5f, 15f, 13f, 15f, hole, 1.8f * u)
                line(6.6f, 3.5f, 6.6f, 20.5f, hole, 1.2f * u)
            }
            poly(15f, 1.5f, 19.5f, 1.5f, 19.5f, 8.5f, 17.25f, 6.8f, 15f, 8.5f)
        }
        TIcon.Themes -> {
            poly(12f, 2.5f, 5.5f, 4.8f, 2.5f, 11f, 4.5f, 17.8f, 10.5f, 21f, 13.5f, 19f, 12.5f, 16f, 15.5f, 14.8f, 20f, 14.5f, 21.5f, 9.5f, 17.5f, 4f)
            punch { circle(7.5f, 10.5f, 1.6f, c = hole); circle(11.5f, 7f, 1.6f, c = hole); circle(16.2f, 8.5f, 1.6f, c = hole); circle(8.5f, 15.5f, 1.6f, c = hole) }
        }
        TIcon.Stats -> {
            rect(3.5f, 13f, 4.2f, 7.5f, 1.4f); rect(9.9f, 8.5f, 4.2f, 12f, 1.4f); rect(16.3f, 3.5f, 4.2f, 17f, 1.4f)
            line(2.5f, 21.8f, 21.5f, 21.8f, w = 1.6f * u)
        }
        TIcon.Trophy -> {
            poly(6.5f, 3f, 17.5f, 3f, 17f, 10.5f, 14.5f, 14f, 9.5f, 14f, 7f, 10.5f)
            drawArc(ink, 90f, 180f, false, p(2.5f, 4f), Size(6f * u, 7f * u), style = stroke)
            drawArc(ink, -90f, 180f, false, p(15.5f, 4f), Size(6f * u, 7f * u), style = stroke)
            rect(10.6f, 13.5f, 2.8f, 4f, 0.6f); rect(7f, 17.5f, 10f, 3.5f, 1.4f)
            punch { poly(12f, 5.2f, 12.9f, 7.2f, 15f, 7.4f, 13.4f, 8.8f, 13.9f, 10.9f, 12f, 9.8f, 10.1f, 10.9f, 10.6f, 8.8f, 9f, 7.4f, 11.1f, 7.2f, c = hole) }
        }
        TIcon.UpNext -> {
            rect(2.5f, 4f, 11f, 3f, 1.5f); rect(2.5f, 10.5f, 8f, 3f, 1.5f); rect(2.5f, 17f, 11f, 3f, 1.5f)
            poly(14.5f, 8.5f, 21.5f, 12f, 14.5f, 15.5f)
        }
        TIcon.Files -> {
            poly(2.5f, 6f, 2.5f, 20f, 21.5f, 20f, 21.5f, 8f, 11.5f, 8f, 9.5f, 5f, 2.5f, 5f)
            punch { rect(6f, 11f, 12f, 1.8f, 0.9f, c = hole); rect(6f, 14.6f, 8f, 1.8f, 0.9f, c = hole) }
        }
        TIcon.Gear -> {
            for (k in 0 until 8) rotate(k * 45f, p(12f, 12f)) { rect(10.1f, 1.6f, 3.8f, 5f, 1.2f) }
            circle(12f, 12f, 7.4f)
            if (hole == Color.Transparent) circle(12f, 12f, 7.4f, fill = false)
            punch { circle(12f, 12f, 3.1f, c = hole) }
        }
        TIcon.Controller -> {
            poly(6.5f, 7f, 17.5f, 7f, 21.5f, 15.5f, 19f, 19f, 15f, 15.5f, 9f, 15.5f, 5f, 19f, 2.5f, 15.5f)
            punch {
                line(7.5f, 9.6f, 7.5f, 13f, hole, 1.7f * u); line(5.8f, 11.3f, 9.2f, 11.3f, hole, 1.7f * u)
                circle(15.7f, 10.2f, 1.1f, c = hole); circle(17.7f, 12.4f, 1.1f, c = hole)
            }
            drawArc(ink, 220f, 100f, false, p(7f, 0.5f), Size(10f * u, 8f * u), style = Stroke(1.6f * u, cap = StrokeCap.Round))
        }
        TIcon.Heart -> {
            poly(12f, 21f, 3.6f, 13f, 2.5f, 8.5f, 4.5f, 4.8f, 8.3f, 3.8f, 12f, 6.4f, 15.7f, 3.8f, 19.5f, 4.8f, 21.5f, 8.5f, 20.4f, 13f)
            punch { circle(7.6f, 8.2f, 1.3f, c = Color.White.copy(alpha = 0.6f)) }
        }
        TIcon.Flame -> {
            poly(12f, 2f, 15.5f, 7f, 18.5f, 9.5f, 19.5f, 14f, 17.5f, 19f, 12f, 22f, 6.5f, 19f, 4.5f, 14f, 6f, 9.5f, 8.5f, 11.5f, 9.5f, 6.5f)
            punch { poly(12f, 12f, 14.5f, 15f, 14.8f, 18f, 12f, 20f, 9.2f, 18f, 9.8f, 15f, c = hole) }
        }
        TIcon.NewBox -> {
            poly(3f, 9f, 12f, 5f, 21f, 9f, 21f, 18f, 12f, 22f, 3f, 18f)
            punch { line(3f, 9f, 12f, 13f, hole, 1.4f * u); line(12f, 13f, 21f, 9f, hole, 1.4f * u); line(12f, 13f, 12f, 22f, hole, 1.4f * u) }
            poly(19f, 0.5f, 20f, 3f, 22.5f, 4f, 20f, 5f, 19f, 7.5f, 18f, 5f, 15.5f, 4f, 18f, 3f)
        }
        TIcon.Rewind -> {
            circle(12f, 12.5f, 9f)
            punch { line(12f, 12.5f, 12f, 7.5f, hole, 2.2f * u); line(12f, 12.5f, 15.5f, 14.5f, hole, 2.2f * u) }
            poly(1.5f, 4.5f, 7.5f, 3f, 5.5f, 9f)
        }
        TIcon.Sprout -> {
            line(12f, 21.5f, 12f, 11f)
            poly(12f, 12f, 6.5f, 11.5f, 3f, 7.5f, 3f, 4f, 7.5f, 4.5f, 11f, 8f)
            poly(12f, 10f, 14f, 5f, 18f, 2.5f, 21.5f, 3f, 21f, 7f, 17f, 10f)
            rect(6f, 19.5f, 12f, 3f, 1.5f)
        }
        TIcon.Genres -> {
            rect(7f, 2.5f, 13f, 14f, 2.5f, c = ink.copy(alpha = ink.alpha * 0.55f))
            rect(4f, 6f, 13f, 15.5f, 2.5f)
            punch { poly(8.5f, 10f, 13.5f, 13.75f, 8.5f, 17.5f, c = hole) }
        }
        TIcon.AllApps -> { for (i in 0 until 3) for (j in 0 until 3) rect(3f + i * 6.5f, 3f + j * 6.5f, 5f, 5f, 1.6f) }
        TIcon.Apps -> {
            // Four app tiles, one of them round and lifted a little: the Apps folder.
            rect(3f, 3f, 8f, 8f, 2.6f); rect(3f, 13f, 8f, 8f, 2.6f); rect(13f, 13f, 8f, 8f, 2.6f)
            circle(17f, 7f, 4.4f)
            punch { circle(17f, 7f, 1.6f, c = hole) }
        }
        TIcon.Emulator -> {
            // A cartridge with a play arrow on its label.
            poly(5f, 2.5f, 19f, 2.5f, 19f, 5f, 20.5f, 5f, 20.5f, 21.5f, 3.5f, 21.5f, 3.5f, 5f, 5f, 5f)
            punch {
                rect(6.5f, 8f, 11f, 9f, 1.8f, c = hole)
                for (k in 0 until 4) rect(7f + k * 2.8f, 19f, 1.4f, 2.5f, 0.4f, c = hole)
            }
            poly(10.5f, 10f, 14.8f, 12.5f, 10.5f, 15f)
        }
        TIcon.Folder -> {
            poly(2.5f, 5.5f, 2.5f, 19.5f, 21.5f, 19.5f, 21.5f, 8f, 11.5f, 8f, 9.5f, 4.5f, 3.5f, 4.5f)
            punch { rect(2.5f, 10f, 19f, 1.6f, 0.8f, c = hole) }
        }
        TIcon.Sword -> {
            poly(19.5f, 2.5f, 21.5f, 4.5f, 10f, 16f, 8f, 14f)
            line(5f, 12f, 12f, 19f); line(7f, 17f, 3.5f, 20.5f, w = 3f * u)
        }
        TIcon.Book -> {
            poly(12f, 6f, 6f, 3.5f, 2.5f, 4.5f, 2.5f, 19f, 6f, 18f, 12f, 20.5f)
            poly(12f, 6f, 18f, 3.5f, 21.5f, 4.5f, 21.5f, 19f, 18f, 18f, 12f, 20.5f, c = ink.copy(alpha = ink.alpha * 0.75f))
        }
        TIcon.Flag -> {
            line(5f, 2.5f, 5f, 21.5f)
            poly(5f, 3f, 20f, 3f, 17f, 7.5f, 20f, 12f, 5f, 12f)
            punch { rect(8f, 3.5f, 3f, 3.6f, 0f, c = hole); rect(14f, 3.5f, 3f, 3.6f, 0f, c = hole); rect(11f, 7.6f, 3f, 3.8f, 0f, c = hole) }
        }
        TIcon.Puzzle -> {
            poly(3f, 7f, 9f, 7f, 9f, 5f, 10.5f, 3f, 13.5f, 3f, 15f, 5f, 15f, 7f, 21f, 7f, 21f, 13f, 19f, 13f, 17f, 14.5f, 17f, 17.5f, 19f, 19f, 21f, 19f, 21f, 21f, 3f, 21f)
        }
        TIcon.Target -> {
            circle(12f, 12f, 9f, fill = false); circle(12f, 12f, 4.5f, fill = false); circle(12f, 12f, 1.6f)
            line(12f, 1f, 12f, 5f, w = 1.8f * u); line(12f, 19f, 12f, 23f, w = 1.8f * u); line(1f, 12f, 5f, 12f, w = 1.8f * u); line(19f, 12f, 23f, 12f, w = 1.8f * u)
        }
        TIcon.Ball -> {
            circle(12f, 12f, 9.5f)
            punch {
                poly(12f, 8f, 15.5f, 10.5f, 14.2f, 14.5f, 9.8f, 14.5f, 8.5f, 10.5f, c = hole)
                line(12f, 8f, 12f, 3f, hole, 1.4f * u); line(15.5f, 10.5f, 20.5f, 9f, hole, 1.4f * u); line(8.5f, 10.5f, 3.5f, 9f, hole, 1.4f * u)
                line(14.2f, 14.5f, 17f, 19f, hole, 1.4f * u); line(9.8f, 14.5f, 7f, 19f, hole, 1.4f * u)
            }
        }
        TIcon.Fist -> {
            rect(5f, 6f, 14f, 10f, 3.5f); rect(6f, 13f, 12f, 8.5f, 3f)
            punch { line(9.5f, 7f, 9.5f, 11f, hole, 1.3f * u); line(14f, 7f, 14f, 11f, hole, 1.3f * u) }
            rect(2.5f, 10f, 5f, 5.5f, 2.5f)
        }
        TIcon.Ghost -> {
            poly(4.5f, 21f, 4.5f, 10f, 6.5f, 4.8f, 12f, 2.5f, 17.5f, 4.8f, 19.5f, 10f, 19.5f, 21f, 16.5f, 18.5f, 14f, 21f, 12f, 18.5f, 10f, 21f, 7.5f, 18.5f)
            punch { circle(9.3f, 10.5f, 1.7f, c = hole); circle(14.7f, 10.5f, 1.7f, c = hole) }
        }
        TIcon.Note -> {
            line(9f, 17.5f, 9f, 4.5f); line(19f, 15.5f, 19f, 2.5f)
            poly(9f, 4.5f, 19f, 2.5f, 19f, 6.5f, 9f, 8.5f)
            circle(6.5f, 17.5f, 3f); circle(16.5f, 15.5f, 3f)
        }
        TIcon.Chess -> {
            circle(12f, 5.5f, 3f)
            poly(9f, 8.5f, 15f, 8.5f, 14f, 14f, 16.5f, 18f, 7.5f, 18f, 10f, 14f)
            rect(5f, 18f, 14f, 3.5f, 1.5f)
        }
        TIcon.Jump -> {
            circle(15f, 4.5f, 2.6f)
            line(14f, 8f, 11f, 13.5f, w = 3f * u); line(11f, 13.5f, 6f, 15f); line(11f, 13.5f, 13f, 18f)
            line(13f, 9f, 18.5f, 10.5f); line(12.5f, 9.5f, 7.5f, 8.5f)
            rect(2f, 19.5f, 9f, 2.6f, 1.3f); rect(14f, 19.5f, 8f, 2.6f, 1.3f)
        }
        TIcon.Star -> {
            val path = Path()
            for (i in 0 until 10) {
                val r = if (i % 2 == 0) 10f else 4.4f
                val a = -PI / 2 + i * PI / 5
                val x = (12f + r * cos(a).toFloat()) * u
                val y = (12.6f + r * sin(a).toFloat()) * u
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
            drawPath(path, ink)
        }
        TIcon.Bolt -> poly(13.5f, 1.5f, 4.5f, 13.5f, 11f, 13.5f, 9.5f, 22.5f, 19.5f, 9.5f, 12.5f, 9.5f)
        TIcon.Planet -> {
            circle(12f, 12f, 6.5f)
            drawOval(ink, p(1.5f, 9f), Size(21f * u, 6f * u), style = Stroke(1.8f * u))
            punch { drawArc(hole, 200f, 140f, false, p(1.5f, 9f), Size(21f * u, 6f * u), style = Stroke(1.2f * u)) }
        }
        TIcon.Leaf -> {
            poly(4f, 20f, 4.5f, 12f, 8f, 6f, 14f, 3.5f, 20.5f, 3.5f, 20f, 10f, 17f, 16f, 11f, 19.5f)
            punch { line(5f, 19f, 15f, 9f, hole, 1.4f * u) }
        }
        TIcon.Diamond -> {
            poly(6f, 3.5f, 18f, 3.5f, 22f, 9f, 12f, 21.5f, 2f, 9f)
            punch { line(2f, 9f, 22f, 9f, hole, 1.2f * u); line(9f, 3.5f, 12f, 9f, hole, 1.2f * u); line(15f, 3.5f, 12f, 9f, hole, 1.2f * u) }
        }
    }
}
