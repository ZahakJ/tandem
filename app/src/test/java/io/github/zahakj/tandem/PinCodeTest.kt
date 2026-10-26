package io.github.zahakj.tandem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The private mode PIN (PrivacyPin.kt): hashing, checking, the waits after wrong tries and Forgot PIN, without Android. */
class PinCodeTest {
    // Fewer rounds than the real ones, so the tests stay quick; the code is the same.
    private val rec = PinCode.make("2468", rounds = 1000)

    @Test fun onlyFourToSixDigitsAreAPin() {
        assertTrue(PinCode.valid("0000"))
        assertTrue(PinCode.valid("123456"))
        assertFalse(PinCode.valid("123"))
        assertFalse(PinCode.valid("1234567"))
        assertFalse(PinCode.valid("12a4"))
        assertFalse(PinCode.valid(""))
    }

    @Test fun onlyASaltedHashIsKept() {
        assertTrue(rec.isSet)
        assertFalse("2468" in rec.hash || "2468" in rec.salt)
        // The same PIN twice gets a new salt, so a different hash.
        val again = PinCode.make("2468", rounds = 1000)
        assertNotEquals(rec.salt, again.salt)
        assertNotEquals(rec.hash, again.hash)
        assertEquals(1000, rec.rounds)
        assertEquals(PinCode.ROUNDS, PinCode.make("2468").rounds)
    }

    @Test fun theRightPinMatchesAndNothingElse() {
        assertTrue(PinCode.matches(rec, "2468"))
        assertFalse(PinCode.matches(rec, "2469"))
        assertFalse(PinCode.matches(rec, "24680"))
        assertFalse(PinCode.matches(PinRecord(), "2468"))
        // A damaged record never matches.
        assertFalse(PinCode.matches(rec.copy(hash = "not base64!"), "2468"))
    }

    @Test fun theFirstWrongTriesAreFreeThenTheWaitsDouble() {
        assertEquals(0L, PinCode.waitAfter(1))
        assertEquals(0L, PinCode.waitAfter(2))
        assertEquals(30_000L, PinCode.waitAfter(3))
        assertEquals(60_000L, PinCode.waitAfter(4))
        assertEquals(120_000L, PinCode.waitAfter(5))
        assertEquals(PinCode.MAX_WAIT_MS, PinCode.waitAfter(9))
        assertEquals(PinCode.MAX_WAIT_MS, PinCode.waitAfter(500))
    }

    @Test fun wrongTriesAreCountedAndMakeTheNextOneWait() {
        var r = rec
        var now = 1_000_000L
        repeat(2) {
            val a = PinCode.attempt(r, "1111", now) as PinCode.Attempt.Wrong
            assertEquals(0L, a.waitMs)
            r = a.rec
        }
        val third = PinCode.attempt(r, "1111", now) as PinCode.Attempt.Wrong
        assertEquals(30_000L, third.waitMs)
        r = third.rec
        assertEquals(3, r.failures)
        // Even the right PIN isn't checked during the wait.
        val early = PinCode.attempt(r, "2468", now + 10_000)
        assertEquals(PinCode.Attempt.Wait(20_000L), early)
        now += 30_000
        val right = PinCode.attempt(r, "2468", now) as PinCode.Attempt.Right
        assertEquals(0, right.rec.failures)
        assertEquals(0L, right.rec.lockedUntil)
        assertEquals(rec.hash, right.rec.hash)
    }

    @Test fun aClockSetBackNeverMakesTheWaitLonger() {
        val locked = rec.copy(failures = 3, lockedUntil = 10_000_000L)
        assertEquals(30_000L, PinCode.waitLeft(locked, 0L))
        assertEquals(0L, PinCode.waitLeft(locked, 20_000_000L))
    }

    @Test fun noPinMeansOneHasToBeSet() {
        assertEquals(PinCode.Attempt.NotSet, PinCode.attempt(PinRecord(), "2468", 0L))
    }

    @Test fun forgotPinWaitsADayAndTheRightPinCallsItOff() {
        val now = 5_000_000L
        assertNull(PinCode.resetLeft(rec, now))
        val asked = PinCode.askReset(rec, now)
        assertEquals(PinCode.RESET_WAIT_MS, PinCode.resetLeft(asked, now))
        // Asking again doesn't start it over.
        assertEquals(asked, PinCode.askReset(asked, now + 3_600_000L))
        assertFalse(PinCode.resetDue(asked, now + PinCode.RESET_WAIT_MS - 1))
        assertTrue(PinCode.resetDue(asked, now + PinCode.RESET_WAIT_MS))
        // A clock set back counts from now: never sooner.
        assertEquals(PinCode.RESET_WAIT_MS, PinCode.resetLeft(asked, now - 86_400_000L))
        val right = PinCode.attempt(asked, "2468", now + 1000) as PinCode.Attempt.Right
        assertNull(PinCode.resetLeft(right.rec, now + 1000))
        // Nothing to reset without a PIN.
        assertFalse(PinCode.resetDue(PinRecord(resetAsked = 1), now + PinCode.RESET_WAIT_MS * 2))
    }

    @Test fun waitsReadRoundedUp() {
        assertEquals("1 s", PinCode.say(1))
        assertEquals("30 s", PinCode.say(30_000))
        assertEquals("2 min", PinCode.say(61_000))
        assertEquals("24 h", PinCode.say(PinCode.RESET_WAIT_MS))
        assertEquals("1 h", PinCode.say(3_599_000 + 60_000 - 59_000))
    }
}
