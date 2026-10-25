package io.github.zahakj.tandem.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.kxml2.io.KXmlParser
import java.io.File
import java.nio.file.Files

/** Parses a small sample shaped like LaunchBox's Metadata.xml into the index, then looks games up in it. */
class LaunchBoxTest {
    private fun buildIndex(wanted: Set<String>): Pair<File, LbBuildStats> {
        val dir = Files.createTempDirectory("lb-test").toFile()
        val stream = javaClass.classLoader!!.getResourceAsStream("launchbox-sample.xml")!!
        val parser = KXmlParser().apply { setInput(stream, "UTF-8") }
        val stats = LbIndexBuilder(dir, wanted).parse(parser)
        return dir to stats
    }

    @Test fun parsesGamesNamesAndImagesForWantedPlatformsOnly() {
        val (dir, stats) = buildIndex(setOf("Nintendo Switch", "Nintendo 3DS", "Windows"))
        try {
            // Super Mario Odyssey, Animal Crossing, Hades, Hades II, Celeste, Smash Ultimate, Late Game (Switch),
            // Pokémon X (3DS), Halo (Windows); not the C64 or N64 games.
            assertEquals(9, stats.games)
            assertEquals(setOf("Nintendo Switch", "Nintendo 3DS", "Windows"), stats.platforms)
            assertFalse(File(dir, "commodore-64").exists())

            val index = LbIndex(dir)
            assertTrue(index.has("Nintendo Switch"))
            assertFalse(index.has("Commodore 64"))

            val found = index.find(
                listOf(
                    // A typical Switch dump name: tags and title id in brackets, a dash instead of the colon.
                    LbQuery("acnh", "Nintendo Switch", listOf("Animal Crossing - New Horizons [01006F8002326000][v0] (USA)")),
                    LbQuery("smo", "Nintendo Switch", listOf("Super Mario Odyssey")),
                    LbQuery("jp", "Nintendo Switch", listOf("Doubutsu no Mori")),
                    LbQuery("poke", "Nintendo 3DS", listOf("Pokemon X (Europe)")),
                    LbQuery("halo", "Windows", listOf("Halo CE")),
                    LbQuery("late", "Nintendo Switch", listOf("Late Game")),
                    LbQuery("none", "Nintendo Switch", listOf("Not A Real Game At All")),
                    LbQuery("c64", "Commodore 64", listOf("Some Commodore Game")),
                ),
            )
            val smo = found["smo"]!!
            assertEquals(136132, smo.id)
            assertEquals(2017, smo.year)
            assertEquals("2", smo.players)
            assertEquals("Nintendo EPD", smo.developer)
            assertEquals(listOf("Action", "Platform"), smo.genres)
            assertEquals(0.926f, smo.rating!!, 0.01f)
            // Multi-line text and entities survive the round trip.
            assertEquals("Join Mario on a globe-trotting 3D adventure.\n\nCollect Moons & rescue Peach.", smo.overview)
            // YouTube links are dropped (they can't be saved), "Box - Back" isn't kept.
            assertNull(smo.video)
            assertEquals(4, smo.images.size)
            // Region preference: North America first for "us", Europe first for "eu".
            assertEquals("https://images.launchbox-app.com/smo-box-na.jpg", LbIndex.pick(smo, listOf(LbArt.BoxFront), "us").first().url)
            assertEquals("smo-box-eu.jpg", LbIndex.pick(smo, listOf(LbArt.BoxFront), "eu").first().file)
            assertEquals("smo-logo.png", LbIndex.pick(smo, listOf(LbArt.Logo), "us").single().file)

            val acnh = found["acnh"]!!
            assertEquals(80000, acnh.id)
            assertEquals(2020, acnh.year)
            assertTrue(acnh.video!!.endsWith("movie_max.webm?t=1"))
            assertEquals(LbArt.Gameplay, acnh.images.single().art)

            assertEquals(80000, found["jp"]?.id) // alternate name
            assertEquals(500, found["poke"]?.id) // accents and region tags ignored
            assertEquals(1, found["halo"]?.id) // alternate name on Windows
            assertEquals("late-logo.png", found["late"]?.images?.single()?.file) // image listed before its game
            assertNull(found["none"])
            assertNull(found["c64"])
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test fun matchingKeys() {
        assertEquals(LbNames.key("The Legend of Zelda: Breath of the Wild"), LbNames.key("Legend of Zelda - Breath of the Wild (USA) [v1.6.0]"))
        assertEquals("pokemon x", LbNames.key("Pokémon X"))
        assertEquals("mario and luigi", LbNames.key("Mario & Luigi"))
    }

    /** The Switch games that had no description: every way their files tend to be named finds the right entry. */
    @Test fun switchTitlesMatch() {
        val (dir, _) = buildIndex(setOf("Nintendo Switch"))
        try {
            val names = mapOf(
                "acnh-plain" to "Animal Crossing New Horizons",
                "acnh-colon" to "Animal Crossing: New Horizons",
                "acnh-dump" to "Animal Crossing New Horizons v2.0.8 + 1 DLC",
                "hades2" to "Hades II",
                "hades2-digit" to "Hades 2",
                "hades2-dump" to "Hades II [0100F1E019E68000][v0].nsp",
                "hades" to "Hades",
                "celeste" to "Celeste",
                "celeste-edition" to "Celeste - Definitive Edition",
                "smash" to "Super Smash Bros. Ultimate",
                "smash-plain" to "Super Smash Bros Ultimate",
                "smash-update" to "Super Smash Bros. Ultimate (v13.0.2) + Update",
            )
            val found = LbIndex(dir).find(names.map { (k, t) -> LbQuery(k, "Nintendo Switch", listOf(t)) })
            for (k in listOf("acnh-plain", "acnh-colon", "acnh-dump")) assertEquals(k, 80000, found[k]?.id)
            for (k in listOf("hades2", "hades2-digit", "hades2-dump")) assertEquals(k, 90002, found[k]?.id)
            assertEquals(90001, found["hades"]?.id) // Hades isn't Hades II
            for (k in listOf("celeste", "celeste-edition")) assertEquals(k, 90003, found[k]?.id)
            for (k in listOf("smash", "smash-plain", "smash-update")) assertEquals(k, 90004, found[k]?.id)

            val hades2 = found["hades2"]!!.toDetails()
            assertTrue(hades2.description!!.startsWith("Battle beyond the Underworld"))
            assertEquals(listOf("Action", "Roguelike"), hades2.genres)
            assertEquals(2025, hades2.year)
            assertEquals("Supergiant Games", hades2.developer)
            assertEquals("1", hades2.players)
        } finally {
            dir.deleteRecursively()
        }
    }

    /** An index written before numerals counted as numbers still finds "Hades 2", without downloading it again. */
    @Test fun olderIndexKeysStillMatch() {
        val dir = Files.createTempDirectory("lb-old").toFile()
        try {
            val p = File(dir, LbIndexBuilder.dirName("Nintendo Switch")).apply { mkdirs() }
            File(p, "names.tsv").writeText("90002\thades ii\t1\n")
            File(p, "games.tsv").writeText("90002\tHades II\t2025\t1\tSupergiant Games\t\tAction\t\t\n")
            val found = LbIndex(dir).find(listOf(LbQuery("h", "Nintendo Switch", listOf("Hades 2"))))
            assertEquals(90002, found["h"]?.id)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test fun normalisedKeys() {
        assertEquals("hades 2", LbNames.key("Hades II"))
        assertEquals("final fantasy 7", LbNames.key("Final Fantasy VII"))
        assertEquals("mega man x", LbNames.key("Mega Man X")) // single letters stay letters
        assertEquals(LbNames.key("Animal Crossing: New Horizons"), LbNames.key("Animal_Crossing_New_Horizons"))
        assertEquals("mario rabbids kingdom battle", LbNames.key("Mario + Rabbids Kingdom Battle")) // "+" alone is not a DLC tag
        assertEquals("celeste", LbNames.loose(LbNames.key("Celeste: Definitive Edition")))
        assertEquals("witcher 3 wild hunt", LbNames.loose(LbNames.key("The Witcher 3: Wild Hunt - Complete Edition")))
        assertNull(LbNames.loose(LbNames.key("Deluxe Edition")))
    }

    /** "Missing selected" and "Missing basics" fetch details for a game that has every picture already. */
    @Test fun missingDetailsCountWhenArtIsThere() {
        val allArt: (MediaKind) -> Boolean = { true }
        val bare = GameMeta()
        val selected = ScrapeOptions(kinds = MediaKind.entries.toSet(), metadata = true, mode = ScrapeMode.Missing, region = Region.US, language = "en")
        val (kinds, types) = selected.needs(bare, allArt)
        assertTrue(kinds.isEmpty())
        assertTrue(MetaType.Description in types && MetaType.Genres in types && MetaType.Year in types)

        val basics = selected.copy(mode = ScrapeMode.Basics)
        assertTrue(MetaType.Description in basics.needs(bare, allArt).second)

        // Nothing for a game that has its details, and a description the user locked is left alone.
        val done = GameMeta(title = "Celeste", description = "x", genres = listOf("Platform"), developer = "d", year = 2018, players = "1", rating = 0.9f, timeToBeat = TimeToBeat(1))
        assertTrue(selected.copy(details = MetaType.entries.toSet() - MetaType.Name).needs(done, allArt).second.isEmpty())
        val locked = GameMeta(lockedFields = setOf(MetaField.DESCRIPTION))
        assertFalse(MetaType.Description in selected.needs(locked, allArt).second)
        assertTrue(basics.needs(locked, allArt).second.isEmpty())
    }

    @Test fun tsvEscapesRoundTrip() {
        val s = "a\tb\nc\\d"
        assertEquals(s, Tsv.unesc(Tsv.esc(s)))
    }

    @Test fun platformMapping() {
        assertEquals("Nintendo Switch", ScrapeSystems.launchBox(listOf("switch")))
        assertEquals("Nintendo 3DS", ScrapeSystems.launchBox(listOf("n3ds")))
        assertEquals("Nintendo 3DS", ScrapeSystems.launchBox(listOf("3ds")))
        assertEquals("Nintendo Wii U", ScrapeSystems.launchBox(listOf("wiiu")))
        assertEquals("Sony Playstation Vita", ScrapeSystems.launchBox(listOf("psvita")))
        assertEquals("Windows", ScrapeSystems.launchBox(listOf("windows")))
        assertEquals("Windows", ScrapeSystems.launchBox(listOf("Winlator")))
        // Unknown ids fall through to the next name the system goes by (art folder, ROM folder).
        assertEquals("Nintendo Switch", ScrapeSystems.launchBox(listOf("my-switch", "switch")))
        assertNull(ScrapeSystems.launchBox(listOf("books")))

        assertEquals(130, ScrapeSystems.igdb(listOf("switch")))
        assertEquals(37, ScrapeSystems.igdb(listOf("n3ds")))
        assertEquals(41, ScrapeSystems.igdb(listOf("wiiu")))
        assertEquals(46, ScrapeSystems.igdb(listOf("psvita")))
        assertEquals(6, ScrapeSystems.igdb(listOf("windows")))

        // libretro has no Switch or Wii U set; ScreenScraper does.
        assertNull(ScrapeSystems.libretro(listOf("switch")))
        assertNull(ScrapeSystems.libretro(listOf("wiiu")))
        assertEquals(225, ScrapeSystems.screenScraper(listOf("switch")))
        assertNotNull(ScrapeSystems.libretro(listOf("snes")))

        assertTrue(ScrapeSystems.prefersSteamGridDb(listOf("windows")))
        assertTrue(ScrapeSystems.prefersSteamGridDb(listOf("switch")))
        assertFalse(ScrapeSystems.prefersSteamGridDb(listOf("snes")))
    }
}
