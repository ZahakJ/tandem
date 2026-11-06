package io.github.zahakj.tandem.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The squarified treemap: areas in proportion, everything inside the area, nothing overlapping. */
class TreemapTest {
    private fun check(weights: List<Double>, w: Float, h: Float) {
        val boxes = Treemap.layout(weights, w, h)
        assertEquals(weights.size, boxes.size)
        val total = weights.sum()
        for ((i, b) in boxes.withIndex()) {
            assertEquals(weights[i] / total * w * h, (b.w * b.h).toDouble(), w * h * 1e-4)
            assertTrue(b.x >= -0.01f && b.y >= -0.01f && b.x + b.w <= w + 0.01f && b.y + b.h <= h + 0.01f)
        }
        for (a in boxes.indices) for (b in boxes.indices) if (a < b) {
            val p = boxes[a]; val q = boxes[b]
            val ox = minOf(p.x + p.w, q.x + q.w) - maxOf(p.x, q.x)
            val oy = minOf(p.y + p.h, q.y + q.h) - maxOf(p.y, q.y)
            assertTrue("boxes $a and $b overlap", ox <= 0.01f || oy <= 0.01f)
        }
    }

    @Test fun wide() = check(listOf(60.0, 30.0, 20.0, 10.0, 5.0, 2.0), 900f, 240f)
    @Test fun tall() = check(listOf(6.0, 6.0, 4.0, 3.0, 2.0, 2.0, 1.0), 300f, 600f)
    @Test fun one() = check(listOf(42.0), 400f, 200f)

    @Test
    fun emptyOrZero() {
        assertEquals(emptyList<Treemap.Box>(), Treemap.layout(emptyList(), 100f, 100f))
        assertEquals(2, Treemap.layout(listOf(0.0, 0.0), 100f, 100f).size)
    }
}
