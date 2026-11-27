package io.github.zahakj.tandem.pass

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Rotation, duty cycle, the hourly cap and who connects (PassPlanner.kt). */
class PassPlannerTest {
    private val t0 = 1_795_000_000_000L
    private val low = byteArrayOf(0, 0, 0, 0, 0, 0, 0, 1)
    private val high = byteArrayOf(0x7f, 0, 0, 0, 0, 0, 0, 0)
    private val top = byteArrayOf(0xff.toByte(), 0, 0, 0, 0, 0, 0, 0)

    @Test fun rotationStaysInItsWindow() {
        for (r in listOf(0.0, 0.25, 0.5, 1.0, -3.0, 7.0)) {
            val next = PassPlanner.nextRotation(t0, r) - t0
            assertTrue(next in (PassPlanner.ROTATE_MS - PassPlanner.ROTATE_JITTER_MS)..(PassPlanner.ROTATE_MS + PassPlanner.ROTATE_JITTER_MS))
        }
        assertTrue(PassPlanner.nextRotation(t0, 0.0) < PassPlanner.nextRotation(t0, 1.0))
        // About fifteen minutes, as promised in the guide.
        assertEquals(15L * 60 * 1000, PassPlanner.nextRotation(t0, 0.5) - t0)
    }

    @Test fun smallerTokenConnectsAndBytesAreUnsigned() {
        assertTrue(PassPlanner.initiates(low, high))
        assertFalse(PassPlanner.initiates(high, low))
        // 0xff is the largest byte, not -1.
        assertTrue(PassPlanner.initiates(high, top))
        assertFalse(PassPlanner.initiates(top, high))
    }

    @Test fun theOtherSideWaitsThenConnects() {
        val p = PassPlanner()
        assertFalse(p.shouldConnect("a", high, low, t0))
        assertFalse(p.shouldConnect("a", high, low, t0 + PassPlanner.FALLBACK_MS - 1))
        assertTrue(p.shouldConnect("a", high, low, t0 + PassPlanner.FALLBACK_MS))
        // Without a token (no scan response heard) it connects at once.
        assertTrue(PassPlanner().shouldConnect("addr", high, null, t0))
    }

    @Test fun aTokenMetIsNotConnectedAgain() {
        val p = PassPlanner()
        assertTrue(p.shouldConnect("b", low, high, t0))
        p.started("b", t0)
        p.finished("b", t0 + 2000)
        assertFalse(p.shouldConnect("b", low, high, t0 + 60_000))
        // Forgotten once it hasn't been heard for longer than a token lives.
        assertTrue(p.shouldConnect("b", low, high, t0 + 60_000 + PassPlanner.TOKEN_MEMORY_MS + 1))
    }

    @Test fun failuresAreRetriedAFewTimes() {
        val p = PassPlanner()
        var t = t0
        repeat(PassPlanner.MAX_TRIES) {
            assertTrue(p.shouldConnect("c", low, high, t))
            p.started("c", t)
            assertFalse(p.shouldConnect("c", low, high, t + 1000))
            t += PassPlanner.RETRY_MS
        }
        assertFalse(p.shouldConnect("c", low, high, t))
    }

    @Test fun hourlyCapCoversBothSides() {
        val p = PassPlanner(maxPerHour = 3)
        p.started("x", t0)
        assertTrue(p.answered(t0 + 1))
        p.started("y", t0 + 2)
        assertFalse(p.shouldConnect("z", low, high, t0 + 3))
        assertFalse(p.answered(t0 + 4))
        assertEquals(3, p.recent(t0 + 5))
        // An hour later there is room again.
        assertTrue(p.shouldConnect("z", low, high, t0 + PassPlanner.HOUR_MS + 3))
    }

    @Test fun dutyCyclesAreLight() {
        for (d in PassPlanner.Duty.entries) assertTrue(d.onMs * 3 <= d.onMs + d.offMs)
        assertTrue(PassPlanner.Duty.Background.onMs.toDouble() / (PassPlanner.Duty.Background.onMs + PassPlanner.Duty.Background.offMs) < 0.1)
    }
}
