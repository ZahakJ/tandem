package io.github.zahakj.tandem.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ContinueDeckTest {
    private val hidden = setOf("secret")
    private fun pick(running: List<String>, recent: List<String>, max: Int = ContinueDeck.MAX) =
        ContinueDeck.pick(running, recent, { it }, { it !in hidden }, max)

    @Test fun runningGamesLeadThenTheLastPlayed() {
        assertEquals(listOf("zelda", "mario", "tetris"), pick(listOf("zelda"), listOf("mario", "tetris")))
    }

    @Test fun aRunningGameIsNotShownTwice() {
        assertEquals(listOf("zelda", "mario"), pick(listOf("zelda"), listOf("zelda", "mario")))
    }

    @Test fun privateGamesNeverShowRunningOrNot() {
        assertEquals(listOf("mario"), pick(listOf("secret"), listOf("secret", "mario")))
        assertEquals(emptyList<String>(), pick(listOf("secret"), emptyList()))
    }

    @Test fun aHandfulAtMost() {
        val recent = (1..12).map { "g$it" }
        assertEquals(5, pick(emptyList(), recent).size)
        assertEquals(listOf("g1", "g2"), pick(emptyList(), recent, max = 2))
    }

    @Test fun theCardKeepsItsGameWhileItIsThere() {
        val keys = listOf("zelda", "mario", "tetris")
        assertEquals(1, ContinueDeck.shown(keys, "mario"))
        assertEquals(0, ContinueDeck.shown(keys, "gone"))
        assertEquals(0, ContinueDeck.shown(keys, null))
        assertEquals(-1, ContinueDeck.shown(emptyList(), "mario"))
    }

    @Test fun l2AndR2StepRoundTheDeck() {
        assertEquals(1, ContinueDeck.step(0, 1, 3))
        assertEquals(0, ContinueDeck.step(2, 1, 3))
        assertEquals(2, ContinueDeck.step(0, -1, 3))
        assertEquals(0, ContinueDeck.step(0, 1, 1))
        assertEquals(-1, ContinueDeck.step(0, 1, 0))
    }
}
