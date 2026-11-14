package io.github.zahakj.tandem.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.hypot

/*
 * A drawing in a note (ui/SketchPad.kt): finger or D-pad strokes on paper, or marks over a screenshot. The picture
 * the note links to is a flattened PNG; beside it, `<name>.strokes.json` keeps the strokes so the drawing can be
 * edited again. Coordinates are pixels of the full picture (before any crop), so cropping never moves a stroke.
 */

/** One stroke: [points] are x, y pairs; [color] is "#RRGGBB". */
@Serializable
data class SketchStroke(val color: String, val width: Float, val points: List<Float>)

/** The part of the picture kept, in pixels. */
@Serializable
data class SketchCrop(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

@Serializable
data class SketchData(
    val w: Int,
    val h: Int,
    /** The paper colour ("#RRGGBB"), under the strokes when there is no [background]. */
    val paper: String = Sketches.PAPER,
    /** The picture drawn over (a file name beside the drawing), for marks on a screenshot. */
    val background: String? = null,
    val crop: SketchCrop? = null,
    val strokes: List<SketchStroke> = emptyList(),
)

object Sketches {
    const val PAPER = "#FBF6EA"
    /** Ink colours: ink, red, blue, green, gold. */
    val INKS = listOf("#24212B", "#E0473C", "#2F6FD6", "#2E9E5B", "#E0A62E")
    /** Pen widths as a share of the picture's longer side: fine, medium, bold. */
    val WIDTHS = listOf(0.004f, 0.008f, 0.018f)
    /** A blank sheet: 4:3 for a sketch, square for a symbol in a glossary. */
    fun sheet(square: Boolean): SketchData = if (square) SketchData(800, 800) else SketchData(1200, 900)

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    fun decode(text: String): SketchData? = runCatching { json.decodeFromString(SketchData.serializer(), text) }.getOrNull()
    fun encode(d: SketchData): String = json.encodeToString(SketchData.serializer(), d)

    /** "#RRGGBB" as an opaque ARGB int (black when it can't be read). */
    fun argb(color: String): Int = (color.removePrefix("#").toLongOrNull(16) ?: 0L).toInt() or (0xFF shl 24)

    /** [strokes] without the ones that pass within [r] of ([x], [y]): the eraser takes whole strokes. */
    fun erase(strokes: List<SketchStroke>, x: Float, y: Float, r: Float): List<SketchStroke> = strokes.filterNot { touches(it, x, y, r + it.width / 2f) }

    private fun touches(s: SketchStroke, x: Float, y: Float, r: Float): Boolean {
        val p = s.points
        if (p.size < 2) return false
        if (p.size < 4) return hypot(p[0] - x, p[1] - y) <= r
        var i = 0
        while (i + 3 < p.size) {
            if (segmentDistance(x, y, p[i], p[i + 1], p[i + 2], p[i + 3]) <= r) return true
            i += 2
        }
        return false
    }

    /** The distance from ([px], [py]) to the segment from ([ax], [ay]) to ([bx], [by]). */
    fun segmentDistance(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = bx - ax; val dy = by - ay
        val len = dx * dx + dy * dy
        val t = if (len == 0f) 0f else (((px - ax) * dx + (py - ay) * dy) / len).coerceIn(0f, 1f)
        return hypot(px - (ax + t * dx), py - (ay + t * dy))
    }

    /** The crop between two corners, kept inside [w] × [h]; null when it is too small to keep (a stray tap). */
    fun crop(x1: Float, y1: Float, x2: Float, y2: Float, w: Int, h: Int): SketchCrop? {
        val l = minOf(x1, x2).toInt().coerceIn(0, w); val r = maxOf(x1, x2).toInt().coerceIn(0, w)
        val t = minOf(y1, y2).toInt().coerceIn(0, h); val b = maxOf(y1, y2).toInt().coerceIn(0, h)
        if (r - l < 16 || b - t < 16) return null
        return SketchCrop(l, t, r, b)
    }

    /** Drops points closer than [min] to the last one kept, so a slow stroke doesn't store thousands. */
    fun thin(points: List<Float>, min: Float): List<Float> {
        if (points.size <= 4) return points
        val out = ArrayList<Float>(points.size)
        out += points[0]; out += points[1]
        var i = 2
        while (i + 1 < points.size) {
            val last = i + 2 >= points.size
            if (last || hypot(points[i] - out[out.size - 2], points[i + 1] - out[out.size - 1]) >= min) { out += points[i]; out += points[i + 1] }
            i += 2
        }
        return out
    }
}
