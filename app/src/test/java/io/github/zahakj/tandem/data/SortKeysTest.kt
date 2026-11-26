package io.github.zahakj.tandem.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Sorting by name without leading articles (SortKeys.kt), per language. */
class SortKeysTest {
    private val en = SortKeys.articlesFor(listOf("en"))
    private fun sorted(titles: List<String>, articles: List<String>) = titles.sortedBy { SortKeys.name(it, articles) }

    @Test fun englishArticlesAreSkipped() {
        assertEquals("legend of zelda", SortKeys.name("The Legend of Zelda", en))
        assertEquals("boy and his blob", SortKeys.name("A Boy and His Blob", en))
        assertEquals("american tail", SortKeys.name("An American Tail", en))
        assertEquals(listOf("Banjo-Kazooie", "The Legend of Zelda", "Metroid"), sorted(listOf("Metroid", "The Legend of Zelda", "Banjo-Kazooie"), en))
    }

    @Test fun onlyAWholeLeadingWordCounts() {
        assertEquals("theme hospital", SortKeys.name("Theme Hospital", en))
        assertEquals("a-train", SortKeys.name("A-Train", en))
        assertEquals("anarchy reigns", SortKeys.name("Anarchy Reigns", en))
        // A title that is only the article keeps it.
        assertEquals("the", SortKeys.name("The", en))
    }

    @Test fun englishAlwaysThenTheLanguagesGiven() {
        val fr = SortKeys.articlesFor(listOf("fr-FR"))
        assertTrue("the " in fr)
        assertEquals("amerzone", SortKeys.name("L'Amerzone", fr))
        assertEquals("amerzone", SortKeys.name("L’Amerzone", fr))
        assertEquals("mans 24 heures", SortKeys.name("Le Mans 24 Heures", fr))
        assertEquals("aventures de tintin", SortKeys.name("Les Aventures de Tintin", fr))
        // Not French: Le stays.
        assertEquals("le mans 24 heures", SortKeys.name("Le Mans 24 Heures", en))
        assertEquals("schwarze auge", SortKeys.name("Das Schwarze Auge", SortKeys.articlesFor(listOf("de"))))
        assertEquals("ángel del mar", SortKeys.name("El Ángel del Mar", SortKeys.articlesFor(listOf("es"))))
        assertEquals("abadía", SortKeys.name("La Abadía", SortKeys.articlesFor(listOf("es"))))
    }

    @Test fun japaneseAndChineseHaveNone() {
        assertEquals(SortKeys.articlesFor(listOf("en")), SortKeys.articlesFor(listOf("ja", "zh")))
        assertEquals("ゼルダの伝説", SortKeys.name("ゼルダの伝説", SortKeys.articlesFor(listOf("ja"))))
        assertEquals("塞尔达传说", SortKeys.name("塞尔达传说", SortKeys.articlesFor(listOf("zh"))))
    }

    @Test fun arabicTitlesLoseTheirAl() {
        assertEquals("أسطورة زيلدا", SortKeys.name("الأسطورة زيلدا", en))
        // Too short to be the article on a word.
        assertEquals("ال", SortKeys.name("ال", en))
    }

    @Test fun switchedOffItIsPlainLowercase() {
        assertEquals("the legend of zelda", SortKeys.name("The Legend of Zelda", en, skip = false))
    }
}
