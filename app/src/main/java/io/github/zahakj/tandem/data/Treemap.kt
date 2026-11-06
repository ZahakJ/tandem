package io.github.zahakj.tandem.data

/**
 * A squarified treemap: [weights] (largest first works best) laid out as boxes filling a [w] × [h] area, each with an
 * area in proportion to its weight and as close to square as the row it sits in allows (Bruls, Huizing, van Wijk).
 * Returns one box per weight, in the same order, as left, top, width, height.
 */
object Treemap {
    data class Box(val x: Float, val y: Float, val w: Float, val h: Float)

    fun layout(weights: List<Double>, w: Float, h: Float): List<Box> {
        val out = arrayOfNulls<Box>(weights.size)
        val total = weights.sum()
        if (weights.isEmpty() || total <= 0.0 || w <= 0f || h <= 0f) return weights.map { Box(0f, 0f, 0f, 0f) }
        // Areas in the units of the box being filled.
        val areas = weights.map { it / total * w * h }
        var x = 0f; var y = 0f; var rw = w; var rh = h
        var i = 0
        while (i < areas.size) {
            val side = minOf(rw, rh).toDouble()
            // Grow the row while it makes its worst box no worse.
            var end = i + 1
            while (end < areas.size && worst(areas.subList(i, end + 1), side) <= worst(areas.subList(i, end), side)) end++
            val row = areas.subList(i, end)
            val sum = row.sum()
            if (rw >= rh) {
                // A column down the left side.
                val colW = (sum / rh).toFloat()
                var yy = y
                for ((k, a) in row.withIndex()) {
                    val bh = (a / colW).toFloat()
                    out[i + k] = Box(x, yy, colW, bh); yy += bh
                }
                x += colW; rw -= colW
            } else {
                // A row across the top.
                val rowH = (sum / rw).toFloat()
                var xx = x
                for ((k, a) in row.withIndex()) {
                    val bw = (a / rowH).toFloat()
                    out[i + k] = Box(xx, y, bw, rowH); xx += bw
                }
                y += rowH; rh -= rowH
            }
            i = end
        }
        return out.map { it!! }
    }

    /** The worst aspect ratio among boxes of [areas] laid along a side of length [side]. */
    private fun worst(areas: List<Double>, side: Double): Double {
        val s = areas.sum()
        val mx = areas.max()
        val mn = areas.min()
        val s2 = s * s
        val w2 = side * side
        return maxOf(w2 * mx / s2, s2 / (w2 * mn))
    }
}
