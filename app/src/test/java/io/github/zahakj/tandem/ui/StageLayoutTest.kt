package io.github.zahakj.tandem.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class StageLayoutTest {
    private val keys = listOf("zelda", "mario", "tetris", "kirby")

    @Test fun aPageLandsOnItsRememberedGameThenTheLastPlayedThenTheFirst() {
        assertEquals(2, StageLayout.landing(keys, "tetris", "mario"))
        assertEquals(1, StageLayout.landing(keys, "gone", "mario"))
        assertEquals(0, StageLayout.landing(keys, null, null))
        assertEquals(-1, StageLayout.landing(emptyList(), "tetris", null))
    }

    @Test fun theRowScrollsOnceTheHighlightPassesAThird() {
        val slot = StageLayout.slot(rowWidth = 900f, pitch = 100f)
        assertEquals(3, slot)
        assertEquals(0f, StageLayout.scroll(2f, slot))
        assertEquals(0f, StageLayout.scroll(3f, slot))
        assertEquals(4f, StageLayout.scroll(7f, slot))
        assertEquals(1.5f, StageLayout.scroll(4.5f, slot))
    }

    @Test fun leftAndRightStopAtTheEnds() {
        assertEquals(0, StageLayout.step(0, -1, 4))
        assertEquals(3, StageLayout.step(3, 1, 4))
        assertEquals(2, StageLayout.step(1, 1, 4))
        assertEquals(-1, StageLayout.step(0, 1, 0))
    }

    @Test fun onlyCoversNearTheViewAreComposed() {
        assertEquals(0..9, StageLayout.window(0f, 7, 100))
        assertEquals(8..19, StageLayout.window(10.4f, 7, 100))
        assertEquals(0..3, StageLayout.window(0f, 7, 4))
    }

    @Test fun l2AndR2JumpByLetter() {
        val titles = listOf("Advance Wars", "Aero", "Banjo", "Bomberman", "Castlevania", "1942", "Zelda")
        assertEquals(2, StageLayout.letterJump(titles, 0, 1, page = 3))
        assertEquals(4, StageLayout.letterJump(titles, 2, 1, page = 3))
        assertEquals(2, StageLayout.letterJump(titles, 3, -1, page = 3))
        assertEquals(0, StageLayout.letterJump(titles, 2, -1, page = 3))
        assertEquals(6, StageLayout.letterJump(titles, 6, 1, page = 3))
        assertEquals('#', StageLayout.letter("1942"))
    }

    @Test fun aShortRowJumpsToItsEnds() {
        assertEquals(3, StageLayout.letterJump(keys, 1, 1, page = 8))
        assertEquals(0, StageLayout.letterJump(keys, 2, -1, page = 8))
    }

    @Test fun backGoesToTheFirstCoverThenHome() {
        assertEquals(StageLayout.Back.Top, StageLayout.back(4, 3, 0))
        assertEquals(StageLayout.Back.Home, StageLayout.back(0, 3, 0))
        assertEquals(StageLayout.Back.Nothing, StageLayout.back(0, 0, 0))
    }
}
