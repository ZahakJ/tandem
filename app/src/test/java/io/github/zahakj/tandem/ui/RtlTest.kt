package io.github.zahakj.tandem.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class RtlTest {
    @Test fun leftToRightStepsTheWayTheArrowPoints() {
        assertEquals(-1, Sides.step(left = true, rtl = false))
        assertEquals(1, Sides.step(left = false, rtl = false))
    }

    @Test fun rightToLeftLeftStepsTowardsTheEnd() {
        // The first option sits on the right: Left moves on to the next, Right back to the one before.
        assertEquals(1, Sides.step(left = true, rtl = true))
        assertEquals(-1, Sides.step(left = false, rtl = true))
    }

    @Test fun signFlipsSlidesInRightToLeft() {
        assertEquals(1f, Sides.sign(rtl = false))
        assertEquals(-1f, Sides.sign(rtl = true))
    }

    @Test fun xFromTheStartSide() {
        assertEquals(30f, Sides.x(30f, 100f, rtl = false))
        assertEquals(70f, Sides.x(30f, 100f, rtl = true))
    }

    @Test fun ltrIsolatesItsText() {
        assertEquals("⁦/sdcard/ROMs⁩", Sides.ltr("/sdcard/ROMs"))
    }
}
