package io.github.zahakj.tandem.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Feel each move (Feel.kt): a tick per move, thinned out while a direction is held. */
class TickGateTest {
    @Test fun everySeparateMoveTicks() {
        val g = TickGate(gapMs = 35, heldGapMs = 140)
        assertTrue(g.allow(0, held = false))
        assertTrue(g.allow(200, held = false))
        assertTrue(g.allow(400, held = false))
    }

    @Test fun twoTicksAtOnceAreOne() {
        val g = TickGate(gapMs = 35, heldGapMs = 140)
        assertTrue(g.allow(1000, held = false))
        assertFalse(g.allow(1010, held = false))
        assertTrue(g.allow(1040, held = false))
    }

    @Test fun aHeldDirectionPattersInsteadOfBuzzing() {
        val g = TickGate(gapMs = 35, heldGapMs = 140)
        // Key repeats every 50 ms for a second: about seven ticks, not twenty.
        val ticks = (0..1000 step 50).count { g.allow(it.toLong(), held = true) }
        assertTrue("$ticks ticks", ticks in 6..8)
    }

    @Test fun theFirstMoveAlwaysTicks() {
        assertEquals(true, TickGate().allow(0, held = true))
    }
}
