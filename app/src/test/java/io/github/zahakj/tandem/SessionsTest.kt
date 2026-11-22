package io.github.zahakj.tandem

import io.github.zahakj.tandem.data.GameMeta
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/** A launch makes the game the last one played at once (Sessions.opened); play time and counts wait for the session. */
class SessionsTest {
    private val yesterday = 1_700_000_000_000L
    private val today = yesterday + 86_400_000L

    @Test fun openingAGameMakesItTheLastPlayed() {
        val m = Sessions.opened(GameMeta(lastPlayed = yesterday), today)
        assertEquals(today, m.lastPlayed)
    }

    @Test fun aGameNeverPlayedIsPlayedOnceOpened() {
        assertEquals(today, Sessions.opened(GameMeta(), today).lastPlayed)
    }

    @Test fun openingCountsNoPlayTimeOrPlays() {
        val m = Sessions.opened(GameMeta(playCount = 3, playSeconds = 600, lastPlayed = yesterday), today)
        assertEquals(3, m.playCount)
        assertEquals(600L, m.playSeconds)
    }

    @Test fun aClockSetBackNeverMovesLastPlayedBack() {
        val m = GameMeta(lastPlayed = today)
        assertSame(m, Sessions.opened(m, yesterday))
    }
}
