package io.github.zahakj.tandem

import io.github.zahakj.tandem.WhatsNewRules.Decision
import io.github.zahakj.tandem.ui.whatsnew.Decks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * What's new (ui/whatsnew/, WhatsNewRules.kt). The build check: versionName in app/build.gradle.kts belongs to a minor
 * version with a deck, so bumping to a new x.Y.0 without writing its deck fails here (docs/RELEASING.md). Then when the
 * deck opens by itself: fresh installs, updates, patches, skipped versions and the switch.
 */
class WhatsNewTest {

    private val versionName: String by lazy {
        val gradle = listOf("build.gradle.kts", "app/build.gradle.kts").map(::File).first { it.isFile && "versionName" in it.readText() }
        Regex("""versionName\s*=\s*"([^"]+)"""").find(gradle.readText())!!.groupValues[1]
    }

    // --- The build check ---

    @Test fun thisVersionHasADeck() {
        val minor = WhatsNewRules.minorOf(versionName)
        assertTrue(
            "versionName is $versionName, but there is no What's new deck for $minor. Every minor version needs one: " +
                "write its slides in ui/whatsnew/ and add it to Decks.all (docs/RELEASING.md).",
            minor in Decks.versions,
        )
    }

    @Test fun decksAreForMinorVersionsWithAFewSlides() {
        assertEquals("one deck per version", Decks.versions.size, Decks.versions.toSet().size)
        assertEquals("decks oldest first", Decks.versions.sortedWith { a, b -> Releases.compare(a, b) }, Decks.versions)
        for (d in Decks.all) {
            assertTrue("${d.version}: decks belong to minor versions (x.Y.0)", WhatsNewRules.isMinor(d.version))
            assertTrue("${d.version}: between 1 and 8 slides, punchy", d.slides.size in 1..8)
            assertEquals("${d.version}: slide ids are unique", d.slides.size, d.slides.map { it.id }.toSet().size)
        }
    }

    /** Every string of strings_whatsnew.xml is in all five languages (the deck is read right after an update). */
    @Test fun everyDeckStringIsInEveryLanguage() {
        val res = listOf("src/main/res", "app/src/main/res").map(::File).first { it.isDirectory }
        fun keys(dir: String): Set<String> {
            val f = File(res, "$dir/strings_whatsnew.xml")
            if (!f.isFile) return emptySet()
            return Regex("""<(?:string|plurals) name="([a-z0-9_]+)"""").findAll(f.readText()).map { it.groupValues[1] }.toSet()
        }
        val english = keys("values")
        assertTrue(english.isNotEmpty())
        for (lang in listOf("values-ar", "values-ja", "values-zh-rCN", "values-fr")) {
            val missing = english - keys(lang)
            assertTrue("$lang/strings_whatsnew.xml misses $missing", missing.isEmpty())
        }
    }

    // --- Versions ---

    @Test fun aVersionBelongsToItsMinor() {
        assertEquals("0.9.0", WhatsNewRules.minorOf("0.9.0"))
        assertEquals("0.9.0", WhatsNewRules.minorOf("0.9.3"))
        assertEquals("0.10.0", WhatsNewRules.minorOf("v0.10.2-rc1"))
        assertEquals("1.0.0", WhatsNewRules.minorOf("1"))
        assertEquals("2.3.0", WhatsNewRules.minorOf("2.3.1+abc"))
        assertTrue(WhatsNewRules.isMinor("0.9.0"))
        assertTrue(WhatsNewRules.isMinor("0.9"))
        assertFalse(WhatsNewRules.isMinor("0.9.1"))
    }

    // --- When it opens ---

    private val decks = listOf("0.7.0", "0.8.0", "0.9.0")

    private fun decide(current: String, seen: String?, welcomed: Boolean = true, on: Boolean = true) =
        WhatsNewRules.decide(current, seen, welcomed, on, decks)

    @Test fun aFreshInstallSkipsItQuietly() {
        assertEquals(Decision.MarkSeen("0.9.0"), decide("0.9.0", null, welcomed = false))
        assertEquals(Decision.MarkSeen("0.9.0"), decide("0.9.2", null, welcomed = false))
    }

    @Test fun anUpdateShowsTheNewDeckOnce() {
        // From a Tandem before What's new existed: nothing seen yet, but past the welcome.
        assertEquals(Decision.Show("0.9.0"), decide("0.9.0", null))
        assertEquals(Decision.Show("0.9.0"), decide("0.9.0", "0.8.0"))
        // Seen: never again.
        assertEquals(Decision.Stay, decide("0.9.0", "0.9.0"))
    }

    @Test fun aPatchShowsItsMinorsDeckOnlyIfNotSeen() {
        assertEquals(Decision.Stay, decide("0.9.1", "0.9.0"))
        assertEquals(Decision.Stay, decide("0.9.4", "0.9.0"))
        // Straight from 0.8 to 0.9.2: the 0.9 deck.
        assertEquals(Decision.Show("0.9.0"), decide("0.9.2", "0.8.0"))
    }

    @Test fun skippingVersionsShowsOnlyTheCurrentDeck() {
        assertEquals(Decision.Show("0.9.0"), decide("0.9.1", "0.6.0"))
    }

    @Test fun switchedOffMarksItSeen() {
        assertEquals(Decision.MarkSeen("0.9.0"), decide("0.9.0", "0.8.0", on = false))
        assertEquals(Decision.Stay, decide("0.9.0", "0.9.0", on = false))
    }

    @Test fun aVersionWithoutADeckOrAnOlderBuildDoesNothing() {
        assertEquals(Decision.Stay, decide("0.6.0", "0.5.0"))
        // Back on an older build after a newer one was seen.
        assertEquals(Decision.Stay, decide("0.9.0", "0.10.0"))
    }
}
