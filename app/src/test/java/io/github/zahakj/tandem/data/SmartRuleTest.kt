package io.github.zahakj.tandem.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Smart folder rules: each condition alone, together, a bad pattern, the random pick and the sorts. */
class SmartRuleTest {
    private val pokemon = SmartGame("n3ds/x.3ds", "Pokémon X", "n3ds", listOf("RPG"), 2013, favorite = true, played = true, emulator = "org.citra", lastPlayed = 50, playSeconds = 900, added = 1)
    private val zelda = SmartGame("n3ds/z.3ds", "The Legend of Zelda", "n3ds", listOf("Adventure"), 2011, emulator = "org.citra", added = 3)
    private val bravely = SmartGame("n3ds/b.3ds", "Bravely Default", "n3ds", listOf("rpg", "Strategy"), 2012, emulator = "org.citra", lastPlayed = 90, playSeconds = 100, added = 2)
    private val mario = SmartGame("snes/m.sfc", "Super Mario World", "snes", listOf("Platform"), 1990, favorite = true, played = true, emulator = "com.retroarch", lastPlayed = 10, playSeconds = 5000, added = 4)
    private val homebrew = SmartGame("gba/h.gba", "Homebrew Demo", "gba")
    private val all = listOf(pokemon, zelda, bravely, mario, homebrew)

    private fun SmartRule.run(games: List<SmartGame> = all, seed: Long = 1) = apply(games, seed) { it }.map { it.title }

    @Test
    fun theDefaultRuleMatchesEverything() {
        assertEquals(all.size, SmartRule().run().size)
    }

    @Test
    fun systems() {
        assertEquals(setOf("Pokémon X", "The Legend of Zelda", "Bravely Default"), SmartRule(systems = listOf("n3ds")).run().toSet())
        assertEquals(4, SmartRule(systems = listOf("n3ds", "snes")).run().size)
        assertTrue(SmartRule(systems = listOf("psx")).run().isEmpty())
    }

    @Test
    fun genresIgnoreCaseAndMatchAny() {
        assertEquals(listOf("Bravely Default", "Pokémon X"), SmartRule(genres = listOf("RPG")).run())
        assertEquals(listOf("Bravely Default", "Super Mario World"), SmartRule(genres = listOf("platform", "STRATEGY")).run())
        // A game with no genres never matches a genre rule.
        assertFalse(SmartRule(genres = listOf("RPG")).matches(homebrew))
    }

    @Test
    fun plainPatternIsContainsWithoutCase() {
        assertEquals(listOf("Pokémon X"), SmartRule(pattern = "pokémon").run())
        assertEquals(listOf("Super Mario World"), SmartRule(pattern = "WORLD").run())
        // Regex characters are plain text unless asked for.
        assertTrue(SmartRule(pattern = "^Super").run().isEmpty())
    }

    @Test
    fun regexPattern() {
        assertEquals(listOf("Super Mario World"), SmartRule(pattern = "^super", regex = true).run())
        assertEquals(listOf("Pokémon X", "The Legend of Zelda"), SmartRule(pattern = "zelda|pok", regex = true).run())
    }

    @Test
    fun badRegexMatchesNothingAndSaysSo() {
        val bad = SmartRule(pattern = "([unclosed", regex = true)
        assertTrue(bad.patternError)
        assertTrue(bad.run().isEmpty())
        assertFalse(bad.matches(pokemon))
        // The same text as plain text is fine.
        val plain = bad.copy(regex = false)
        assertFalse(plain.patternError)
        assertTrue(plain.run().isEmpty())
        assertFalse(SmartRule(regex = true).patternError)
    }

    @Test
    fun yearRange() {
        assertEquals(listOf("Bravely Default", "Pokémon X"), SmartRule(yearFrom = 2012).run())
        assertEquals(listOf("Super Mario World"), SmartRule(yearTo = 2000).run())
        assertEquals(listOf("Bravely Default", "The Legend of Zelda"), SmartRule(yearFrom = 2011, yearTo = 2012).run())
        // Games without a year drop out once either end is set.
        assertFalse(SmartRule(yearTo = 3000).matches(homebrew))
    }

    @Test
    fun favoritesOnly() {
        assertEquals(listOf("Pokémon X", "Super Mario World"), SmartRule(favorites = true).run())
    }

    @Test
    fun playedAndUnplayed() {
        assertEquals(listOf("Pokémon X", "Super Mario World"), SmartRule(played = SmartRule.PLAYED_YES).run())
        assertEquals(listOf("Bravely Default", "Homebrew Demo", "The Legend of Zelda"), SmartRule(played = SmartRule.PLAYED_NO).run())
        // An unknown value (from a newer version) filters nothing.
        assertEquals(all.size, SmartRule(played = "sometimes").run().size)
    }

    @Test
    fun emulatorPackage() {
        assertEquals(listOf("Super Mario World"), SmartRule(emulator = "com.retroarch").run())
        assertFalse(SmartRule(emulator = "com.retroarch").matches(homebrew))
    }

    @Test
    fun conditionsCombine() {
        // "Unplayed 3DS RPGs".
        val r = SmartRule(systems = listOf("n3ds"), genres = listOf("RPG"), played = SmartRule.PLAYED_NO)
        assertEquals(listOf("Bravely Default"), r.run())
        assertTrue(r.copy(favorites = true).run().isEmpty())
        assertEquals(listOf("Pokémon X"), SmartRule(systems = listOf("n3ds"), pattern = "x$", regex = true, yearFrom = 2013, favorites = true).run())
    }

    @Test
    fun sorts() {
        assertEquals(listOf("Bravely Default", "Homebrew Demo", "Pokémon X", "Super Mario World", "The Legend of Zelda"), SmartRule().run())
        assertEquals(listOf("Bravely Default", "Pokémon X", "Super Mario World"), SmartRule(sort = SmartRule.SORT_RECENT).run().take(3))
        assertEquals(listOf("Super Mario World", "Pokémon X", "Bravely Default"), SmartRule(sort = SmartRule.SORT_MOST).run().take(3))
        assertEquals(listOf("Super Mario World", "The Legend of Zelda", "Bravely Default", "Pokémon X"), SmartRule(sort = SmartRule.SORT_ADDED).run().take(4))
        assertEquals(listOf("Homebrew Demo", "Bravely Default", "Pokémon X", "The Legend of Zelda", "Super Mario World"), SmartRule(sort = SmartRule.SORT_SYSTEM).run())
        // An unknown sort falls back to A–Z.
        assertEquals(SmartRule().run(), SmartRule(sort = "rating").run())
    }

    private val many = (1..60).map { SmartGame("snes/g$it.sfc", "Game %02d".format(it), "snes", played = it % 2 == 0) }

    @Test
    fun randomPicksThatManyAndStaysPut() {
        val r = SmartRule(random = 10)
        val first = r.run(many, seed = 42)
        assertEquals(10, first.size)
        assertEquals(first, r.run(many, seed = 42))
        // The order of the library doesn't change the pick.
        assertEquals(first, r.run(many.shuffled(java.util.Random(7)), seed = 42))
        // Another session (seed) picks others.
        assertNotEquals(first, r.run(many, seed = 43))
        // The pick still comes out in the rule's order.
        assertEquals(first.sorted(), first)
    }

    @Test
    fun randomOnlyReplacesAGameThatLeaves() {
        val r = SmartRule(random = 10)
        val before = r.apply(many, 42) { it }
        val gone = before.first()
        val after = r.apply(many - gone, 42) { it }
        assertEquals(10, after.size)
        assertEquals(9, after.count { it in before })
        // A new game joining the library swaps out at most one.
        val joined = r.apply(many + SmartGame("snes/new.sfc", "New", "snes"), 42) { it }
        assertTrue(joined.count { it in before } >= 9)
    }

    @Test
    fun randomWithFewerMatchesKeepsThemAll() {
        assertEquals(all.size, SmartRule(random = 50).run().size)
        assertEquals(5, SmartRule(random = 5, played = SmartRule.PLAYED_YES).run(many).size)
    }

    @Test
    fun statuses() {
        val games = listOf(pokemon.copy(status = "playing"), zelda.copy(status = "finished"), bravely.copy(status = "completed"), mario)
        assertEquals(listOf("Pokémon X"), SmartRule(statuses = listOf("playing")).run(games))
        assertEquals(setOf("The Legend of Zelda", "Bravely Default"), SmartRule(statuses = listOf("finished", "completed")).run(games).toSet())
        assertEquals(listOf("Super Mario World"), SmartRule(statuses = listOf(SmartRule.STATUS_NONE)).run(games))
        assertEquals(4, SmartRule().run(games).size)
    }

    // --- Rating, developer and publisher, play time, last played, any rule ---------------------------------------

    private val day = 24 * 60 * 60 * 1000L
    private val now = 1_000 * day
    private val rated = listOf(
        pokemon.copy(rating = 0.8f, developer = "Game Freak", publisher = "Nintendo", playSeconds = 30 * 3600, lastPlayed = now - 2 * day),
        zelda.copy(rating = 0.95f, developer = "Grezzo", publisher = "Nintendo", playSeconds = 3 * 3600, lastPlayed = now - 60 * day, played = true),
        bravely.copy(rating = 0.6f, developer = "Silicon Studio", publisher = "Square Enix", playSeconds = 0, lastPlayed = 0),
        homebrew,
    )
    private fun SmartRule.at(games: List<SmartGame> = rated) = games.filter { matches(it, now) }.map { it.title }

    @Test
    fun ratingBoundsLeaveUnratedGamesOut() {
        assertEquals(listOf("Pokémon X", "The Legend of Zelda"), SmartRule(ratingFrom = 0.8f).at())
        assertEquals(listOf("Bravely Default"), SmartRule(ratingTo = 0.6f).at())
        assertEquals(listOf("Pokémon X"), SmartRule(ratingFrom = 0.7f, ratingTo = 0.9f).at())
    }

    @Test
    fun developerAndPublisherAreContainsWithoutCase() {
        assertEquals(listOf("Pokémon X"), SmartRule(developer = "game freak").at())
        assertEquals(listOf("Pokémon X", "The Legend of Zelda"), SmartRule(publisher = "NINTENDO").at())
        assertEquals(listOf("Bravely Default"), SmartRule(publisher = " square ").at())
        assertEquals(4, SmartRule(developer = "  ").at().size)
    }

    @Test
    fun playTimeMoreAndLessThan() {
        assertEquals(listOf("Pokémon X"), SmartRule(playedMoreHours = 10).at())
        assertEquals(listOf("The Legend of Zelda", "Bravely Default", "Homebrew Demo"), SmartRule(playedLessHours = 10).at())
        assertEquals(listOf("The Legend of Zelda"), SmartRule(playedMoreHours = 2, playedLessHours = 5).at())
    }

    @Test
    fun lastPlayedWithinNotWithinAndNever() {
        assertEquals(listOf("Pokémon X"), SmartRule(lastPlayed = "within:7").at())
        assertEquals(listOf("Pokémon X", "The Legend of Zelda"), SmartRule(lastPlayed = "within:90").at())
        // Not in 30 days: Zelda, and the games never played.
        assertEquals(listOf("The Legend of Zelda", "Bravely Default", "Homebrew Demo"), SmartRule(lastPlayed = "notwithin:30").at())
        assertEquals(listOf("Bravely Default", "Homebrew Demo"), SmartRule(lastPlayed = SmartRule.LAST_NEVER).at())
        // A value this version doesn't understand doesn't filter.
        assertEquals(4, SmartRule(lastPlayed = "sometime:3").at().size)
        assertEquals(4, SmartRule(lastPlayed = "within:0").at().size)
    }

    @Test
    fun anyRuleIsEnough() {
        val r = SmartRule(match = SmartRule.MATCH_ANY, developer = "grezzo", ratingTo = 0.6f)
        assertEquals(listOf("The Legend of Zelda", "Bravely Default"), r.at())
        assertEquals(listOf("The Legend of Zelda"), r.copy(match = SmartRule.MATCH_ALL, ratingTo = null).at())
        // Nothing set matches everything, either way.
        assertEquals(4, SmartRule(match = SmartRule.MATCH_ANY).at().size)
        // Favourites or Super Mario: every favourite and every Mario.
        assertEquals(listOf("Pokémon X", "Super Mario World"), SmartRule(match = SmartRule.MATCH_ANY, favorites = true, pattern = "mario").run())
        // An unknown match value reads as all.
        assertEquals(listOf("Pokémon X"), SmartRule(match = "most", favorites = true, systems = listOf("n3ds")).run())
    }
}
