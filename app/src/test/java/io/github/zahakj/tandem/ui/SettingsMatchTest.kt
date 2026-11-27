package io.github.zahakj.tandem.ui

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

/** How settings hits are matched and ranked ([matchHits]), as the settings search and the home search's finds use it. */
class SettingsMatchTest {
    private fun hit(key: String, label: String, words: String = "", help: String = "") =
        Hit(key, label, "Library & art › Games & systems", "", Color.White, null, help, label, words, help) {}

    private val rescan = hit(
        "rescan", "Rescan games", words = "Games & systems Library & art",
        help = "Manuals and guides go in ROMs/manuals/<system>/ named like the game (Pokemon Red.pdf for Pokemon Red.gb).",
    )
    private val sort = hit("sort", "Sort by", words = "order")

    @Test
    fun helpTextFindsInSettingsSearch() {
        assertEquals(listOf("rescan"), matchHits(listOf(rescan, sort), normalize("poke"), 5).map { it.key })
    }

    @Test
    fun homeFindsSkipAWordOnlyInTheHelp() {
        // The home search lists these above the games: "poke" must not put Rescan games over every Pokémon game.
        assertEquals(emptyList<String>(), matchHits(listOf(rescan, sort), normalize("poke"), 3, help = false).map { it.key })
    }

    @Test
    fun homeFindsStillMatchLabelsAndSynonyms() {
        assertEquals(listOf("rescan"), matchHits(listOf(rescan, sort), normalize("resc"), 3, help = false).map { it.key })
        assertEquals(listOf("sort"), matchHits(listOf(rescan, sort), normalize("order"), 3, help = false).map { it.key })
    }

    @Test
    fun labelBeatsSynonymBeatsHelp() {
        val inLabel = hit("a", "Game folders")
        val inWords = hit("b", "Rescan", words = "games")
        val inHelp = hit("c", "Sort by", help = "games")
        assertEquals(listOf("a", "b", "c"), matchHits(listOf(inHelp, inWords, inLabel), "game", 5).map { it.key })
    }
}
