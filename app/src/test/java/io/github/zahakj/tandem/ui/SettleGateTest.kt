package io.github.zahakj.tandem.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** One L1 / R1 is one page sound and feel (Feel.kt's SettleGate): the highlight settling on the new page is quiet. */
class SettleGateTest {
    @Test fun movesTickWhenNoPageTurned() {
        val g = SettleGate(windowMs = 450)
        assertTrue(g.allowMove(1000))
    }

    @Test fun focusSettlingAfterATurnIsQuiet() {
        val g = SettleGate(windowMs = 450)
        g.page(1000)
        // The new page's first frame, a retry 70 ms on, the row rising in: none of them tick.
        assertFalse(g.allowMove(1016))
        assertFalse(g.allowMove(1090))
        assertFalse(g.allowMove(1400))
        assertTrue(g.allowMove(1450))
    }

    @Test fun aPressAfterTheTurnTicksAtOnce() {
        val g = SettleGate(windowMs = 450)
        g.page(1000)
        g.direction(1120)
        assertTrue(g.allowMove(1121))
    }

    @Test fun thePressThatTurnedThePageDoesntCount() {
        // Down at a grid's edge turns the page: the key comes first, then the page sound, in the same millisecond.
        val g = SettleGate(windowMs = 450)
        g.direction(2000)
        g.page(2000)
        assertFalse(g.allowMove(2030))
    }

    @Test fun eachTurnStartsItsOwnQuiet() {
        val g = SettleGate(windowMs = 450)
        g.page(1000)
        g.direction(1100)
        assertTrue(g.allowMove(1100))
        g.page(1200)
        assertFalse(g.allowMove(1250))
    }
}
