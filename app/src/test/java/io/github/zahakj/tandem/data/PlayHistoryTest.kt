package io.github.zahakj.tandem.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Editing play sessions and working the totals out again (PlayHistory.kt). */
class PlayHistoryTest {
    private val min = 60_000L
    private val t0 = 1_700_000_000_000L

    /** A session of [key] starting [at] minutes after t0, [minutes] long. */
    private fun s(key: String, at: Long, minutes: Long) = PlaySession(key, t0 + at * min, minutes * 60)

    // --- merge ---

    @Test fun mergesWithNextOfSameGameWithinTenMinutes() {
        val a = s("snes/a", 0, 30)
        val b = s("snes/a", 40, 20) // starts 10 min after a ends
        val list = listOf(a, b)
        assertTrue(PlayHistory.canMerge(a, PlayHistory.next(list, a)))
        val merged = PlayHistory.mergeNext(list, a)
        assertEquals(listOf(PlaySession("snes/a", a.s, 50 * 60)), merged)
    }

    @Test fun noMergeAfterTenMinutes() {
        val a = s("snes/a", 0, 30)
        val b = s("snes/a", 41, 20)
        assertFalse(PlayHistory.canMerge(a, b))
        assertEquals(listOf(a, b), PlayHistory.mergeNext(listOf(a, b), a))
    }

    @Test fun noMergeWithAnotherGame() {
        val a = s("snes/a", 0, 30)
        val b = s("snes/b", 32, 20)
        assertFalse(PlayHistory.canMerge(a, b))
    }

    @Test fun nextIsTheFollowingSessionOfAnyGame() {
        val a = s("snes/a", 0, 30)
        val other = s("snes/b", 31, 2)
        val b = s("snes/a", 35, 20)
        val list = listOf(b, other, a) // out of order on purpose
        assertEquals(other, PlayHistory.next(list, a))
        assertFalse(PlayHistory.canMerge(a, PlayHistory.next(list, a)))
        assertNull(PlayHistory.next(list, b))
    }

    @Test fun lastSessionHasNothingToMergeWith() {
        val a = s("snes/a", 0, 30)
        assertFalse(PlayHistory.canMerge(a, PlayHistory.next(listOf(a), a)))
    }

    // --- edits ---

    @Test fun lengthIsClampedToAMinuteAndADay() {
        val a = s("snes/a", 0, 30)
        assertEquals(60L, PlayHistory.setLength(listOf(a), a, 5).single().d)
        assertEquals(24 * 3600L, PlayHistory.setLength(listOf(a), a, 99 * 3600).single().d)
        assertEquals(45 * 60L, PlayHistory.setLength(listOf(a), a, 45 * 60).single().d)
    }

    @Test fun movingKeepsOrderAndAvoidsSameStart() {
        val a = s("snes/a", 0, 30)
        val b = s("snes/a", 100, 10)
        val (list, moved) = PlayHistory.moveTo(listOf(a, b), b, a.s)
        assertEquals(a.s + 1000, moved.s)
        assertEquals(listOf(a, moved), list)
        val (list2, moved2) = PlayHistory.moveTo(listOf(a, b), a, b.s + 5 * min)
        assertEquals(listOf(b, moved2), list2)
    }

    @Test fun deleteRemovesOnlyThatSession() {
        val a = s("snes/a", 0, 30)
        val b = s("snes/a", 100, 10)
        assertEquals(listOf(b), PlayHistory.delete(listOf(a, b), a))
    }

    @Test fun changedKeysAreTheGamesTouched() {
        val a = s("snes/a", 0, 30)
        val b = s("snes/b", 100, 10)
        val after = PlayHistory.setLength(listOf(a, b), a, 10 * 60)
        assertEquals(setOf("snes/a"), PlayHistory.changedKeys(listOf(a, b), after))
    }

    // --- recompute ---

    @Test fun recomputeFromSessionsOnly() {
        val a = s("snes/a", 0, 30)
        val b = s("snes/a", 100, 10)
        val meta = GameMeta(playSeconds = 40 * 60, playCount = 2, lastPlayed = b.end + 2000)
        val after = PlayHistory.delete(listOf(a, b), b)
        val m = PlayHistory.recompute(meta, listOf(a, b), after)
        assertEquals(30 * 60L, m.playSeconds)
        assertEquals(1, m.playCount)
        assertEquals(a.end, m.lastPlayed)
    }

    @Test fun recomputeAfterMergeCountsOnePlay() {
        val a = s("snes/a", 0, 30)
        val b = s("snes/a", 35, 20)
        val meta = GameMeta(playSeconds = 50 * 60, playCount = 2, lastPlayed = b.end)
        val after = PlayHistory.mergeNext(listOf(a, b), a)
        val m = PlayHistory.recompute(meta, listOf(a, b), after)
        assertEquals(50 * 60L, m.playSeconds)
        assertEquals(1, m.playCount)
        assertEquals(a.s + 50 * min, m.lastPlayed)
    }

    @Test fun deletingEverythingClearsTheTotals() {
        val a = s("snes/a", 0, 30)
        val meta = GameMeta(playSeconds = 30 * 60, playCount = 1, lastPlayed = a.end)
        val m = PlayHistory.recompute(meta, listOf(a), emptyList())
        assertEquals(0L, m.playSeconds)
        assertEquals(0, m.playCount)
        assertEquals(0L, m.lastPlayed)
    }

    @Test fun importedPlayStaysWhenSessionsChange() {
        // Five hours and 12 plays imported, last on a day long before Tandem's one session.
        val imported = t0 - 30L * 24 * 60 * min
        val a = s("snes/a", 0, 30)
        val meta = GameMeta(playSeconds = 5 * 3600 + 30 * 60, playCount = 13, lastPlayed = a.end)
        val longer = PlayHistory.recompute(meta, listOf(a), PlayHistory.setLength(listOf(a), a, 60 * 60))
        assertEquals(6 * 3600L, longer.playSeconds)
        assertEquals(13, longer.playCount)
        val gone = PlayHistory.recompute(meta, listOf(a), emptyList())
        assertEquals(5 * 3600L, gone.playSeconds)
        assertEquals(12, gone.playCount)
        // Last played came from the session, so it goes with it; the imported date isn't known any more.
        assertEquals(0L, gone.lastPlayed)
        val older = meta.copy(lastPlayed = imported)
        assertEquals(imported, PlayHistory.recompute(older, listOf(a), emptyList()).lastPlayed)
    }

    @Test fun recomputeIsStableAcrossEdits() {
        val a = s("snes/a", 0, 30)
        val b = s("snes/a", 100, 10)
        val meta = GameMeta(playSeconds = 2 * 3600, playCount = 9, lastPlayed = b.end)
        val step1 = PlayHistory.setLength(listOf(a, b), a, 20 * 60)
        val m1 = PlayHistory.recompute(meta, listOf(a, b), step1)
        val step2 = PlayHistory.setLength(step1, step1.first(), 30 * 60)
        val m2 = PlayHistory.recompute(m1, step1, step2)
        assertEquals(meta.playSeconds, m2.playSeconds)
        assertEquals(meta.playCount, m2.playCount)
    }

    @Test fun otherFieldsAreKept() {
        val a = s("snes/a", 0, 30)
        val meta = GameMeta(title = "A", favorite = true, playSeconds = 30 * 60, playCount = 1, lastPlayed = a.end)
        val m = PlayHistory.recompute(meta, listOf(a), PlayHistory.setLength(listOf(a), a, 10 * 60))
        assertEquals("A", m.title)
        assertTrue(m.favorite)
    }
}
