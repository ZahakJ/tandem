package io.github.zahakj.tandem.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Surprise me's reel (SlotSpin.kt): it slows down, lands on the pick, and ticks once per cover. */
class SlotReelTest {
    private val last = (SlotReel.COVERS - 1).toFloat()
    private fun at(t: Float) = SlotReel.EASE.transform(t) * (last + SlotReel.OVERSHOOT)

    @Test fun itSlowsDown() {
        val steps = (0..10).map { at(it / 10f) }.zipWithNext { a, b -> b - a }
        assertTrue("faster at first: $steps", steps.first() > steps.last() * 4)
        assertTrue(steps.all { it >= 0f })
    }

    @Test fun itEndsPastThePickAndSettlesOnIt() {
        assertEquals(last + SlotReel.OVERSHOOT, at(1f), 0.001f)
        assertEquals(SlotReel.COVERS - 1, SlotReel.under(at(1f), SlotReel.COVERS))
        assertEquals(SlotReel.COVERS - 1, SlotReel.under(last, SlotReel.COVERS))
    }

    @Test fun oneTickPerCoverPassing() {
        assertEquals(0, SlotReel.passed(0f, 0.4f))
        assertEquals(1, SlotReel.passed(0.4f, 0.6f))
        assertEquals(3, SlotReel.passed(0f, 3.2f))
        // Springing back over the line ticks too.
        assertEquals(1, SlotReel.passed(13.6f, 13f))
        // The whole spin: a tick per cover after the first.
        var ticks = 0
        var shown = 0f
        for (i in 1..200) { val p = at(i / 200f); if (SlotReel.passed(shown, p) > 0) { ticks += SlotReel.passed(shown, p); shown = p } }
        assertEquals(SlotReel.COVERS - 1, ticks)
    }

    @Test fun aboutASecond() {
        assertTrue(SlotReel.SPIN_MS in 800..1300)
    }
}
