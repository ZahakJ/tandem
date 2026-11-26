package io.github.zahakj.tandem.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Genres (Genres.kt): scrapers' spellings in one bucket, compounds in each of theirs, the names they show by. */
class GenresTest {
    @Test fun spellingsHyphensAndPluralsAreOne() {
        assertEquals(listOf("platformer"), Genres.buckets("Platform"))
        assertEquals(listOf("platformer"), Genres.buckets("Platformers"))
        assertEquals(listOf("rpg"), Genres.buckets("Role Playing Game"))
        assertEquals(listOf("rpg"), Genres.buckets("Role-Playing"))
        assertEquals(listOf("rpg"), Genres.buckets("Action RPG"))
        assertEquals(Genres.buckets("Educational games"), Genres.buckets("educational-game"))
    }

    @Test fun compoundsFallInEachPart() {
        assertEquals(listOf("rpg", "strategy"), Genres.buckets("RPG & Strategy"))
        assertEquals(listOf("action", "adventure"), Genres.buckets("Action & Adventure"))
        assertEquals(listOf("visualnovel"), Genres.buckets("Mystery & Visual Novels"))
        // A part without a keyword goes with the rest; a hyphen joins one genre, not two.
        assertEquals(listOf("adventure"), Genres.buckets("Point & Click"))
        assertEquals(listOf("adventure"), Genres.buckets("Action-Adventure"))
        assertEquals(listOf("my folder"), Genres.buckets(" My Folder "))
    }

    @Test fun choicesAreOnePerBucketByTheCommonestSpelling() {
        val library = listOf(
            "Platform", "Platform", "Platformers", "Role Playing Game", "Role-Playing", "Role-Playing", "RPG & Strategy",
            "Action & Adventure", "Action", "Adventure", "Mystery & Visual Novels", "Visual Novel",
        )
        assertEquals(listOf("Action", "Adventure", "Platform", "Role-Playing", "Strategy", "Visual Novel"), Genres.choices(library))
    }

    @Test fun matchingIsByBucket() {
        assertTrue(Genres.matches("Platformers", listOf("Platform")))
        assertTrue(Genres.matches("Strategy", listOf("RPG & Strategy")))
        assertTrue(Genres.matches("Action & Adventure", listOf("Adventure")))
        assertFalse(Genres.matches("Racing", listOf("RPG & Strategy")))
        assertFalse(Genres.matches("", listOf("Platform")))
    }

    @Test fun searchAndSmartRulesAgree() {
        val game = SmartGame("psx/vs", "Vagrant Story", "psx", listOf("Role Playing Game"))
        assertTrue(SmartRule(genres = listOf("Role-Playing")).matches(game))
        assertTrue(SearchFilter(genre = "RPG").rule().matches(game))
        assertFalse(SearchFilter(genre = "Platform").rule().matches(game))
    }
}
