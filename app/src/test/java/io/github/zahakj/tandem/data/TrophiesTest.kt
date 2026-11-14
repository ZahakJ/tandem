package io.github.zahakj.tandem.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

/** Tandem's trophies, worked out from play history and statuses. */
class TrophiesTest {
    private val utc = ZoneOffset.UTC
    private fun at(day: Int, hour: Int = 20) = LocalDateTime.of(2026, 3, day, hour, 0).toInstant(utc).toEpochMilli()
    private fun facts(metas: List<GameMeta> = emptyList(), sessions: List<PlaySession> = emptyList(), library: Int = 0) =
        TrophyFacts(metas, sessions, library, utc)
    private fun earned(f: TrophyFacts) = TrophyList.withMaster.filter { it.earned(f) }.map { it.id }.toSet()

    @Test
    fun nothingPlayedEarnsNothing() {
        assertTrue(earned(facts()).isEmpty())
    }

    @Test
    fun streaksCountDaysInARow() {
        val s = listOf(1, 2, 3, 5, 6, 7, 8, 9, 10, 11).map { PlaySession("snes/a.sfc", at(it), 600) }
        val f = facts(sessions = s)
        assertEquals(7, f.bestStreak)
        assertTrue("week" in earned(f))
        assertFalse("month" in earned(f))
    }

    @Test
    fun timeOfDayAndSystems() {
        val s = listOf(PlaySession("snes/a.sfc", at(1, 3), 60), PlaySession("gba/b.gba", at(2, 6), 60), PlaySession("n64/c.z64", at(3), 4 * 3600))
        val e = earned(facts(sessions = s))
        assertTrue("night-owl" in e)
        assertTrue("early-bird" in e)
        assertTrue("travelled" in e)
        assertTrue("marathon" in e)
    }

    @Test
    fun finishingAndCompleting() {
        val metas = listOf(
            GameMeta(status = "finished", finishedAt = at(1)),
            GameMeta(status = "completed", finishedAt = at(2)),
            GameMeta(status = "playing", finishedAt = at(3)),
            GameMeta(status = "backlog"),
        )
        val f = facts(metas)
        assertEquals(2, f.finished)
        assertEquals(1, f.completed)
        // A game played again after its first finish still counts for the year it was finished in.
        assertEquals(3, f.bestYear)
        val e = earned(f)
        assertTrue("credits" in e)
        assertTrue("completionist" in e)
        assertFalse("closer" in e)
    }

    @Test
    fun progressIsCapped() {
        val t = TrophyList.all.first { it.id == "hour" }
        assertEquals(1f, t.progress(facts(listOf(GameMeta(playSeconds = 99_999)))), 0f)
        assertEquals(0.5f, t.progress(facts(listOf(GameMeta(playSeconds = 1800)))), 0.001f)
    }

    @Test
    fun theMasterTrophyNeedsEveryOther() {
        assertEquals(TrophyList.all.size.toLong(), TrophyList.master.goal)
        assertFalse(TrophyList.master.earned(facts(library = 1000)))
    }

    @Test
    fun idsAreUnique() {
        assertEquals(TrophyList.withMaster.size, TrophyList.withMaster.map { it.id }.toSet().size)
    }
}
