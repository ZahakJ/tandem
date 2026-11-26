package io.github.zahakj.tandem

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A RetroAchievements game's list (RaList.kt): reading the answers, filters, sorts, and hints fetched only on demand. */
class RaListTest {
    private val progress = """
        {"ID":1,"Title":"Sonic the Hedgehog","NumAchievements":4,"NumDistinctPlayers":1000,"NumAwardedToUser":2,"NumAwardedToUserHardcore":1,
         "Achievements":{
          "10":{"ID":10,"Title":"Ring Collector","Description":"Get 100 rings","Points":5,"BadgeName":"00010","DisplayOrder":2,"NumAwarded":800,"NumAwardedHardcore":500,"type":"progression","DateEarned":"2024-05-01 12:00:00","DateEarnedHardcore":"2024-05-01 12:00:00"},
          "11":{"ID":11,"Title":"Secret Path","Description":"Find it","Points":25,"BadgeName":"00011","DisplayOrder":1,"NumAwarded":30,"NumAwardedHardcore":10,"type":"missable"},
          "12":{"ID":12,"Title":"Speedy","Description":"Beat act 1 fast","Points":10,"BadgeName":"00012","DisplayOrder":3,"NumAwarded":200,"NumAwardedHardcore":50,"type":null,"DateEarned":"2024-06-02 08:00:00"},
          "13":{"ID":13,"Title":"The End","Description":"Finish","Points":50,"BadgeName":"00013","DisplayOrder":4,"NumAwarded":100,"NumAwardedHardcore":90,"type":"win_condition"}
         }}
    """.trimIndent()

    private fun parsed() = RaList.parseProgress(1, progress, "https://media.example")!!

    @Test fun progressReadsTheSetAndTheUser() {
        val p = parsed()
        assertEquals("Sonic the Hedgehog", p.title)
        assertEquals(4, p.total)
        assertEquals(2, p.earned)
        assertEquals(1000, p.players)
        assertEquals(90, p.points)
        assertEquals(15, p.earnedPoints)
        assertEquals(1, p.hardcoreCount)
        assertEquals(5, p.hardcorePoints)
        assertEquals(listOf(11, 10, 12, 13), p.achievements.map { it.id })
        val ring = p.achievements.first { it.id == 10 }
        assertTrue(ring.earned && ring.earnedHardcore)
        assertEquals("https://media.example/Badge/00010.png", ring.badgeUrl)
        val speedy = p.achievements.first { it.id == 12 }
        assertTrue(speedy.earned && !speedy.earnedHardcore)
        assertEquals("", speedy.type)
        assertEquals("missable", p.achievements.first { it.id == 11 }.type)
    }

    @Test fun aSetWithoutAchievementsReadsEmpty() {
        val p = RaList.parseProgress(2, """{"Title":"Nothing","NumAchievements":0,"Achievements":[]}""", "")!!
        assertEquals(0, p.total)
        assertTrue(p.achievements.isEmpty())
        assertNull(RaList.parseProgress(2, "not json", ""))
    }

    @Test fun filtersPickTheirAchievements() {
        val all = parsed().achievements
        assertEquals(4, RaList.filter(all, RaList.Filter.All).size)
        assertEquals(listOf(11, 13), RaList.filter(all, RaList.Filter.Locked).map { it.id })
        assertEquals(listOf(10, 12), RaList.filter(all, RaList.Filter.Unlocked).map { it.id })
        assertEquals(listOf(11), RaList.filter(all, RaList.Filter.Missable).map { it.id })
        assertEquals(mapOf(RaList.Filter.All to 4, RaList.Filter.Locked to 2, RaList.Filter.Unlocked to 2, RaList.Filter.Missable to 1), RaList.counts(all))
    }

    @Test fun sortsOrderByRarityAndPoints() {
        val p = parsed()
        val all = p.achievements.shuffled(java.util.Random(4))
        assertEquals(listOf(11, 10, 12, 13), RaList.sort(all, RaList.Sort.Default, p.players).map { it.id })
        assertEquals(listOf(11, 13, 12, 10), RaList.sort(all, RaList.Sort.Rarity, p.players).map { it.id })
        assertEquals(listOf(13, 11, 12, 10), RaList.sort(all, RaList.Sort.Points, p.players).map { it.id })
        // Unknown players: rarity can't be told, so the set's order stands.
        assertEquals(listOf(11, 10, 12, 13), RaList.sort(all, RaList.Sort.Rarity, 0).map { it.id })
        assertEquals(listOf(13, 11), RaList.shown(all, RaList.Filter.Locked, RaList.Sort.Points, p.players).map { it.id })
        assertEquals(0.03f, RaList.share(p.achievements.first { it.id == 11 }, p.players)!!, 0.0001f)
        assertNull(RaList.share(p.achievements.first(), 0))
    }

    @Test fun filtersStepAroundBothWays() {
        assertEquals(RaList.Filter.Locked, RaList.step(RaList.Filter.All, 1))
        assertEquals(RaList.Filter.Missable, RaList.step(RaList.Filter.All, -1))
        assertEquals(RaList.Filter.All, RaList.step(RaList.Filter.Missable, 1))
        assertEquals(RaList.Sort.Rarity, RaList.next(RaList.Sort.Default))
        assertEquals(RaList.Sort.Default, RaList.next(RaList.Sort.Points))
    }

    private fun comments(from: Int, n: Int, total: Int, server: Boolean = false) = buildString {
        append("""{"Count":$n,"Total":$total,"Results":[""")
        append((from until from + n).joinToString(",") { """{"User":"player$it","Submitted":"2024-01-0${1 + it % 9}T10:00:00.000000Z","CommentText":"Hint &amp; tip $it"}""" })
        if (server) append(""",{"User":"Server","Submitted":"2024-01-01","CommentText":"Achievement edited"}""")
        append("]}")
    }

    @Test fun commentsReadWithoutServerNotes() {
        val page = RaList.parseComments(comments(0, 2, 3, server = true))!!
        assertEquals(listOf("player0", "player1"), page.comments.map { it.user })
        assertEquals("Hint & tip 0", page.comments[0].text)
        assertEquals(2, page.total)
        assertFalse(page.more)
    }

    @Test fun hintsAreFetchedOnlyWhenAskedFor() = runBlocking {
        val calls = mutableListOf<Triple<Int, Int, Int>>()
        val hints = RaList.Hints(pageSize = 2) { id, offset, count ->
            calls += Triple(id, offset, count)
            comments(offset, minOf(count, 5 - offset), 5)
        }
        // Built, looked at: nothing asked.
        assertNull(hints.cached(7))
        assertTrue(calls.isEmpty())
        // A on achievement 7: one call, the first page.
        val first = hints.load(7)!!
        assertEquals(listOf(Triple(7, 0, 2)), calls)
        assertEquals(2, first.comments.size)
        assertTrue(first.more)
        // Opening it again uses what was loaded.
        hints.load(7)
        assertEquals(1, calls.size)
        // More: the next page, then the last.
        assertEquals(4, hints.more(7)!!.comments.size)
        val last = hints.more(7)!!
        assertEquals(5, last.comments.size)
        assertFalse(last.more)
        assertEquals(listOf(Triple(7, 0, 2), Triple(7, 2, 2), Triple(7, 4, 2)), calls)
        // Nothing more to fetch: no call.
        hints.more(7)
        assertEquals(3, calls.size)
        // Another achievement is its own fetch; clearing forgets them all.
        hints.load(8)
        assertEquals(Triple(8, 0, 2), calls.last())
        hints.clear()
        assertNull(hints.cached(7))
    }

    @Test fun aFailedFetchIsNotKept() = runBlocking {
        var answer: String? = null
        var calls = 0
        val hints = RaList.Hints { _, _, _ -> calls++; answer }
        assertNull(hints.load(3))
        assertNull(hints.cached(3))
        answer = comments(0, 1, 1)
        assertEquals(1, hints.load(3)!!.comments.size)
        assertEquals(2, calls)
    }
}
