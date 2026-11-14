package io.github.zahakj.tandem.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/** Game statuses: the automatic move to Playing after a real session, and setting one by hand. */
class GameStatusTest {
    private val long = GameStatus.PLAYING_AFTER_SECONDS

    @Test
    fun aRealSessionStartsAGameWithNoStatusOrInTheBacklog() {
        assertEquals("playing", GameStatus.afterSession(GameMeta(), long, 100).status)
        val backlog = GameMeta(status = "backlog", statusAt = 5)
        val after = GameStatus.afterSession(backlog, long + 1, 100)
        assertEquals("playing", after.status)
        assertEquals(100, after.statusAt)
    }

    @Test
    fun aShortSessionChangesNothing() {
        val m = GameMeta(status = "backlog")
        assertSame(m, GameStatus.afterSession(m, long - 1, 100))
        assertNull(GameStatus.afterSession(GameMeta(), 30, 100).status)
    }

    @Test
    fun everyOtherStatusIsKept() {
        for (s in listOf("playing", "finished", "completed", "shelved", "endless")) {
            val m = GameMeta(status = s, statusAt = 7)
            assertSame(m, GameStatus.afterSession(m, 10 * long, 100))
        }
    }

    @Test
    fun theFirstFinishIsKeptThroughASecondRun() {
        val finished = GameStatus.set(GameMeta(), GameStatus.Finished, 1_000)
        assertEquals(1_000, finished.finishedAt)
        val again = GameStatus.set(finished, GameStatus.Playing, 2_000)
        assertEquals("playing", again.status)
        assertEquals(1_000, again.finishedAt)
        val completed = GameStatus.set(again, GameStatus.Completed, 3_000)
        assertEquals(1_000, completed.finishedAt)
        assertEquals(3_000, completed.statusAt)
    }

    @Test
    fun clearingTheStatus() {
        val m = GameStatus.set(GameMeta(status = "shelved", statusAt = 9), null, 50)
        assertNull(m.status)
        assertEquals(0, m.statusAt)
    }

    @Test
    fun unknownIdsReadAsNoStatus() {
        assertNull(GameMeta(status = "someday").playStatus)
        assertEquals(GameStatus.Endless, GameMeta(status = "endless").playStatus)
    }
}
