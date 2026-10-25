package io.github.zahakj.tandem

import io.github.zahakj.tandem.WakeGate.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** What a key or stick move does while Tandem's screensaver may be running (DreamWake.kt), without Android. */
class WakeGateTest {
    private val a = 96 // KEYCODE_BUTTON_A
    private val x = 99 // KEYCODE_BUTTON_X
    private val up = 19 // KEYCODE_DPAD_UP

    @Test fun nothingHappensWithoutADream() {
        val gate = WakeGate()
        assertEquals(Verdict.Pass, gate.onKey(dreaming = false, down = true, code = a, now = 0))
        assertEquals(Verdict.Pass, gate.onKey(dreaming = false, down = false, code = a, now = 50))
        assertEquals(Verdict.Pass, gate.onStick(dreaming = false, moved = true, now = 60))
        assertFalse(gate.swallowing)
    }

    @Test fun anyKeyWakesTheDream() {
        for (code in listOf(a, x, up, 4 /* BACK */, 108 /* START */)) {
            assertEquals(Verdict.Wake, WakeGate().onKey(dreaming = true, down = true, code = code, now = 0))
        }
        // Even a release whose press went elsewhere.
        assertEquals(Verdict.Wake, WakeGate().onKey(dreaming = true, down = false, code = x, now = 0))
    }

    @Test fun theRestOfTheWakingPressIsSwallowedOnceTheLibraryIsBack() {
        val gate = WakeGate()
        assertEquals(Verdict.Wake, gate.onKey(dreaming = true, down = true, code = a, now = 0))
        // The dream is gone; held A repeats, then lets go: none of it may select anything.
        assertEquals(Verdict.Swallow, gate.onKey(dreaming = false, down = true, code = a, now = 400))
        assertEquals(Verdict.Swallow, gate.onKey(dreaming = false, down = false, code = a, now = 600))
        assertFalse(gate.swallowing)
        // The next press is an ordinary one.
        assertEquals(Verdict.Pass, gate.onKey(dreaming = false, down = true, code = a, now = 900))
        // Other buttons are never held back.
        assertEquals(Verdict.Pass, gate.onKey(dreaming = false, down = true, code = up, now = 950))
    }

    @Test fun aReleaseThatNeverArrivesIsForgotten() {
        val gate = WakeGate(holdMs = 2_000)
        gate.onKey(dreaming = true, down = true, code = a, now = 0)
        assertTrue(gate.swallowing)
        assertEquals(Verdict.Pass, gate.onKey(dreaming = false, down = true, code = a, now = 2_500))
    }

    @Test fun aReleaseDuringTheDreamLeavesNothingToSwallow() {
        val gate = WakeGate()
        gate.onKey(dreaming = true, down = true, code = x, now = 0)
        gate.onKey(dreaming = true, down = false, code = x, now = 80)
        assertFalse(gate.swallowing)
        assertEquals(Verdict.Pass, gate.onKey(dreaming = false, down = true, code = x, now = 500))
    }

    @Test fun aStickMoveWakesTheDreamButDriftDoesNot() {
        val gate = WakeGate()
        assertEquals(Verdict.Swallow, gate.onStick(dreaming = true, moved = false, now = 0))
        assertFalse(gate.swallowing)
        assertEquals(Verdict.Wake, gate.onStick(dreaming = true, moved = true, now = 10))
        // Still pushed once the dream has gone: ignored until it is let go.
        assertEquals(Verdict.Swallow, gate.onStick(dreaming = false, moved = true, now = 300))
        assertEquals(Verdict.Swallow, gate.onStick(dreaming = false, moved = false, now = 400))
        assertEquals(Verdict.Pass, gate.onStick(dreaming = false, moved = true, now = 500))
    }

    @Test fun aStickHeldLongAfterWakingMovesAgain() {
        val gate = WakeGate(holdMs = 2_000)
        gate.onStick(dreaming = true, moved = true, now = 0)
        assertEquals(Verdict.Swallow, gate.onStick(dreaming = false, moved = true, now = 1_000))
        assertEquals(Verdict.Pass, gate.onStick(dreaming = false, moved = true, now = 2_100))
        assertEquals(Verdict.Pass, gate.onStick(dreaming = false, moved = true, now = 2_200))
    }

    @Test fun theBottomScreenStaysOnlyForTheDream() {
        assertTrue(WakeGate.keepBottomScreen(dreaming = true, bottomShowing = true, singleScreen = false))
        assertFalse(WakeGate.keepBottomScreen(dreaming = false, bottomShowing = true, singleScreen = false))
        assertFalse(WakeGate.keepBottomScreen(dreaming = true, bottomShowing = false, singleScreen = false))
        assertFalse(WakeGate.keepBottomScreen(dreaming = true, bottomShowing = true, singleScreen = true))
    }
}
