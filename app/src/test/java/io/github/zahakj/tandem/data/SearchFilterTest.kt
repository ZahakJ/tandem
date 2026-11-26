package io.github.zahakj.tandem.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Search's filters (SearchFilter.kt): each one, together, with the typed text, and with none. */
class SearchFilterTest {
    private class G(val text: String, val view: SmartGame)

    private val now = System.currentTimeMillis()
    private val games = listOf(
        G("super mario world", SmartGame("snes/smw", "Super Mario World", "snes", listOf("Platform"), 1990, favorite = true, played = true, status = "finished", rating = 0.9f)),
        G("super metroid", SmartGame("snes/sm", "Super Metroid", "snes", listOf("Action", "Platform"), 1994, played = true, status = "playing", rating = 0.95f)),
        G("sonic the hedgehog", SmartGame("megadrive/sonic", "Sonic the Hedgehog", "megadrive", listOf("Platform"), 1991, rating = 0.8f)),
        G("final fantasy vii", SmartGame("psx/ff7", "Final Fantasy VII", "psx", listOf("RPG"), 1997, favorite = true, rating = 0.92f)),
        G("tetris", SmartGame("gb/tetris", "Tetris", "gb", emptyList(), null, played = true)),
    )

    private fun keys(text: String, f: SearchFilter, limit: Int = 100) =
        SearchFilter.results(games, text, f, limit, { it.text }, { it.view }).let { (l, n) -> l.map { it.view.key } to n }

    @Test fun nothingTypedAndNothingSetShowsNothing() {
        assertEquals(emptyList<String>() to 0, keys("", SearchFilter()))
    }

    @Test fun textAloneSearchesTitles() {
        assertEquals(listOf("snes/smw", "snes/sm") to 2, keys("super", SearchFilter()))
    }

    @Test fun filtersAloneBrowseTheLibrary() {
        assertEquals(listOf("snes/smw", "snes/sm") to 2, keys("", SearchFilter(system = "snes")))
        assertEquals(listOf("snes/smw", "snes/sm", "megadrive/sonic") to 3, keys("", SearchFilter(genre = "platform")))
        assertEquals(listOf("snes/smw", "snes/sm", "megadrive/sonic") to 3, keys("", SearchFilter(yearFrom = 1990, yearTo = 1999).copy(genre = "Platform")))
        assertEquals(listOf("snes/smw", "psx/ff7") to 2, keys("", SearchFilter(favorites = true)))
        assertEquals(listOf("megadrive/sonic", "psx/ff7") to 2, keys("", SearchFilter(played = SmartRule.PLAYED_NO)))
        assertEquals(listOf("snes/sm") to 1, keys("", SearchFilter(status = "playing")))
        assertEquals(listOf("megadrive/sonic", "psx/ff7", "gb/tetris") to 3, keys("", SearchFilter(status = SmartRule.STATUS_NONE)))
        // A game without a year or rating never passes a year or rating filter.
        assertEquals(listOf("snes/smw", "snes/sm", "psx/ff7") to 3, keys("", SearchFilter(rating = 0.9f)))
        assertFalse(keys("", SearchFilter(yearFrom = 1980, yearTo = 2030)).first.contains("gb/tetris"))
    }

    @Test fun everythingCombines() {
        val f = SearchFilter(system = "snes", genre = "Platform", favorites = true)
        assertEquals(listOf("snes/smw") to 1, keys("", f))
        assertEquals(listOf("snes/smw") to 1, keys("mario", f))
        assertEquals(emptyList<String>() to 0, keys("metroid", f))
        assertEquals(listOf("snes/sm") to 1, keys("metroid", SearchFilter(played = SmartRule.PLAYED_YES, rating = 0.9f)))
    }

    @Test fun theCountGoesPastTheLimit() {
        assertEquals(listOf("snes/smw") to 3, keys("", SearchFilter(genre = "Platform"), limit = 1))
    }

    @Test fun chipsSetCountAndClear() {
        val f = SearchFilter(system = "snes", yearFrom = 1990, yearTo = 1999, favorites = true)
        assertEquals(3, f.count)
        assertTrue(f.active)
        assertTrue(f.isSet(SearchFilter.Chip.Year))
        assertFalse(f.isSet(SearchFilter.Chip.Played))
        val less = f.cleared(SearchFilter.Chip.Year).cleared(SearchFilter.Chip.Favorites)
        assertEquals(SearchFilter(system = "snes"), less)
        assertEquals(SearchFilter(), less.cleared(SearchFilter.Chip.System))
        assertFalse(SearchFilter().active)
    }

    @Test fun theRuleMatchesWhatASmartFolderWould() {
        val f = SearchFilter(system = "snes", genre = "Action", yearFrom = 1990, yearTo = 1999, rating = 0.8f, status = "playing", played = SmartRule.PLAYED_YES, favorites = false)
        val r = f.rule()
        assertEquals(listOf("snes"), r.systems)
        assertEquals(listOf("Action"), r.genres)
        assertEquals(SmartRule.MATCH_ALL, r.match)
        assertTrue(r.matches(games[1].view, now))
        assertFalse(r.matches(games[0].view, now))
    }

    @Test fun decadesComeFromTheLibraryNewestFirst() {
        assertEquals(listOf(2000, 1990, 1980), SearchFilter.decades(listOf(1985, 1991, 1999, 2004, 1, 9999)))
    }
}
