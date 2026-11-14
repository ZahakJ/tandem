package io.github.zahakj.tandem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Deck's trackpad maths (DeckPointer.kt). */
class DeckPointerTest {
    @Test fun startsInTheMiddle() {
        val p = DeckPointer(1920, 1080)
        assertEquals(960, p.x)
        assertEquals(540, p.y)
    }

    @Test fun slowMovementFollowsTheSensitivity() {
        val p = DeckPointer(1920, 1080)
        // A slow finger (far under the speed where acceleration shows) moves the pointer by the sensitivity alone.
        p.move(10f, 0f, 1000, sensitivity = 1f)
        assertEquals(970, p.x)
        val q = DeckPointer(1920, 1080)
        q.move(10f, 0f, 1000, sensitivity = 2f)
        assertEquals(980, q.x)
    }

    @Test fun aFlickGoesFurtherThanACrawl() {
        val slow = DeckPointer(1920, 1080)
        val fast = DeckPointer(1920, 1080)
        slow.move(50f, 0f, 500, 1f)
        fast.move(50f, 0f, 10, 1f)
        assertTrue("${fast.x} > ${slow.x}", fast.x > slow.x)
        // Never more than the sensitivity times one plus the acceleration.
        assertTrue(fast.x - 960 <= 50 * (1 + DeckPointer.ACCELERATION) + 1)
        assertTrue(DeckPointer.gain(0f, 1.5f) == 1.5f)
        assertTrue(DeckPointer.gain(100f, 1f) < 1f + DeckPointer.ACCELERATION)
        assertTrue(DeckPointer.gain(1f, 1f) < DeckPointer.gain(2f, 1f))
    }

    @Test fun unknownTimingCountsAsSlow() {
        val p = DeckPointer(1920, 1080)
        p.move(10f, 10f, 0, 1f)
        assertEquals(970, p.x)
        assertEquals(550, p.y)
    }

    @Test fun staysOnTheScreen() {
        val p = DeckPointer(1920, 1080)
        p.move(-5000f, -5000f, 100, 1f)
        assertEquals(0, p.x)
        assertEquals(0, p.y)
        p.move(50000f, 50000f, 100, 1f)
        assertEquals(1919, p.x)
        assertEquals(1079, p.y)
        p.place(-20f, 2000f)
        assertEquals(0, p.x)
        assertEquals(1079, p.y)
    }

    @Test fun fractionsAddUp() {
        val p = DeckPointer(1920, 1080)
        var changes = 0
        // Ten tiny moves of 0.3 px each: three whole pixels in the end, not none.
        repeat(10) { if (p.move(0.3f, 0f, 100, 1f)) changes++ }
        assertEquals(963, p.x)
        assertTrue(changes in 2..4)
        assertFalse(p.move(0f, 0f, 16, 1f))
    }

    @Test fun scrollNotchesCarryTheRest() {
        val s = DeckScroll(notchPx = 40f)
        assertEquals(0, s.add(30f))
        assertEquals(1, s.add(30f))
        assertEquals(0, s.add(10f))
        assertEquals(3, s.add(90f))
        // Back up the pad: negative notches, through zero.
        assertEquals(-1, s.add(-50f))
        s.reset()
        assertEquals(0, s.add(39f))
    }

    @Test fun tapsAreShortAndStill() {
        val slop = 24f
        assertEquals(DeckTap.Click.Left, DeckTap.classify(fingers = 1, heldMs = 120, movedPx = 5f, slopPx = slop))
        assertEquals(DeckTap.Click.Right, DeckTap.classify(fingers = 2, heldMs = 120, movedPx = 5f, slopPx = slop))
        assertEquals(DeckTap.Click.Right, DeckTap.classify(fingers = 3, heldMs = 120, movedPx = 5f, slopPx = slop))
        // A drag, or a long press, isn't a click.
        assertEquals(DeckTap.Click.None, DeckTap.classify(1, 120, 60f, slop))
        assertEquals(DeckTap.Click.None, DeckTap.classify(1, 600, 0f, slop))
        assertEquals(DeckTap.Click.None, DeckTap.classify(0, 10, 0f, slop))
    }

    @Test fun repeatPausesThenRuns() {
        assertEquals(DeckRepeat.FIRST_MS, DeckRepeat.delayMs(1))
        assertEquals(DeckRepeat.NEXT_MS, DeckRepeat.delayMs(2))
        assertEquals(DeckRepeat.NEXT_MS, DeckRepeat.delayMs(50))
        assertTrue(DeckRepeat.FIRST_MS > DeckRepeat.NEXT_MS)
    }
}
