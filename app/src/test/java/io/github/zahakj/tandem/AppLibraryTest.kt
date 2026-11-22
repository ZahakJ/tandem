package io.github.zahakj.tandem

import io.github.zahakj.tandem.AppLibrary.DROIDDECK
import io.github.zahakj.tandem.AppLibrary.GAMENATIVE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** One tile per game across DroidDeck, GameNative and the user's own shortcuts, and what each app is told. */
class AppLibraryTest {
    // Made-up games. DroidDeck's store games go by a 64-bit id, GameNative's by its own small numbers.
    private val ddSteam = AppGame(DROIDDECK, "steam", "1000010", "Moss Garden", storeId = "1000010")
    private val gnSteam = AppGame(GAMENATIVE, "steam", "1000010", "Moss Garden", storeId = "1000010")
    private val gnSteamOnly = AppGame(GAMENATIVE, "steam", "1000020", "Lantern Post", storeId = "1000020")
    private val ddEpic = AppGame(DROIDDECK, "epic", "10065604626084986880", "Paper Kites", storeId = "a1b2c3d4e5f6")
    private val gnEpic = AppGame(GAMENATIVE, "epic", "202", "Paper Kites", storeId = "A1B2C3D4E5F6")
    private val gnGog = AppGame(GAMENATIVE, "gog", "1207658930", "Ember Road", storeId = "1207658930")
    private val ddGog = AppGame(DROIDDECK, "gog", "10065604626084987000", "Ember Road", storeId = "1207658930")
    private val gnAmazon = AppGame(GAMENATIVE, "amazon", "7", "Hollow Bay", storeId = "amzn1.adg.product.1")
    private val ddXbox = AppGame(DROIDDECK, "xbox", "10065604626084987616", "Glass Tide", storeId = "9NBLGGH4R315")
    private val ddAdded = AppGame(DROIDDECK, "added", "10065604626084988000", "My Own Exe")

    @Test fun sameGameInBothAppsIsOneTile() {
        val (tiles, _) = AppLibrary.tiles(listOf(gnSteam, ddSteam, gnSteamOnly, gnEpic, ddEpic, gnGog, ddGog, gnAmazon, ddXbox, ddAdded))
        assertEquals(7, tiles.size)
        val moss = tiles.first { it.name == "Moss Garden" }
        // DroidDeck first, whatever order they came in; named after the first app's game.
        assertEquals(listOf(DROIDDECK, GAMENATIVE), moss.apps)
        assertEquals("droiddeck-steam-1000010", moss.stem)
        assertEquals("droiddeck-steam-1000010.steam", moss.fileName)
        // Epic by its app name (case doesn't matter), GOG by its product id.
        assertEquals(listOf(DROIDDECK, GAMENATIVE), tiles.first { it.name == "Paper Kites" }.apps)
        assertEquals(listOf(DROIDDECK, GAMENATIVE), tiles.first { it.name == "Ember Road" }.apps)
        // A game only GameNative has is named after GameNative's.
        assertEquals("gamenative-steam-1000020.steam", tiles.first { it.name == "Lantern Post" }.fileName)
        assertEquals("gamenative-amazon-7.amazon", tiles.first { it.name == "Hollow Bay" }.fileName)
        assertEquals("droiddeck-xbox-10065604626084987616.xbox", tiles.first { it.name == "Glass Tide" }.fileName)
        // A game added to DroidDeck by hand: never a .steam file, so nothing reads its id as a Steam app id.
        assertEquals("droiddeck-added-10065604626084988000.droiddeck", tiles.first { it.name == "My Own Exe" }.fileName)
    }

    @Test fun byNameOnlyWithoutAStoreIdAndInTheSameStore() {
        // DroidDeck's provider without a store id: matched by name, within Epic only.
        val shared = ddEpic.copy(storeId = null, name = "Paper Kites™")
        val (tiles, _) = AppLibrary.tiles(listOf(gnEpic, shared, gnGog.copy(name = "Paper Kites")))
        assertEquals(2, tiles.size)
        assertEquals(listOf(DROIDDECK, GAMENATIVE), tiles.first { it.source == "epic" }.apps)
        // Two different store ids never match, whatever the names.
        val (apart, _) = AppLibrary.tiles(listOf(gnEpic, ddEpic.copy(storeId = "other")))
        assertEquals(2, apart.size)
    }

    @Test fun aTileKeepsItsNameWhenTheOtherAppComesAndGoes() {
        // GameNative had it first.
        val (first, stems1) = AppLibrary.tiles(listOf(gnEpic))
        assertEquals("gamenative-epic-202", first.single().stem)
        // DroidDeck installs it too: still GameNative's name, so the play time Tandem kept stays on it.
        val (both, stems2) = AppLibrary.tiles(listOf(gnEpic, ddEpic), stems1)
        assertEquals("gamenative-epic-202", both.single().stem)
        // GameNative uninstalls it: DroidDeck's alone, same name still.
        val (dd, stems3) = AppLibrary.tiles(listOf(ddEpic), stems2)
        assertEquals("gamenative-epic-202", dd.single().stem)
        // Gone from both, then back in DroidDeck: its name comes back with it.
        val (none, stems4) = AppLibrary.tiles(emptyList(), stems3)
        assertTrue(none.isEmpty())
        assertEquals("gamenative-epic-202", AppLibrary.tiles(listOf(ddEpic), stems4).first.single().stem)
        // DroidDeck read through its provider (no store id) finds the same tile by DroidDeck's id.
        assertEquals("gamenative-epic-202", AppLibrary.tiles(listOf(ddEpic.copy(storeId = null)), stems4).first.single().stem)
        // DroidDeck's Steam games keep the names they had before GameNative came in.
        assertEquals("droiddeck-steam-1000010", AppLibrary.tiles(listOf(gnSteam, ddSteam)).first.single().stem)
    }

    @Test fun eachAppIsToldItsOwnId() {
        val (tiles, _) = AppLibrary.tiles(listOf(gnEpic, ddEpic, gnSteam, ddSteam, ddAdded))
        val kites = tiles.first { it.source == "epic" }
        // DroidDeck 0.7.1 (versionCode 19) turns down any game_source but STEAM: none is sent, and it finds the game by id.
        assertFalse(AppLibrary.droidDeckTakesSource(19))
        assertEquals("[gamenative] 202\n[droiddeck] 10065604626084986880\n", AppLibrary.stubText(kites, takesSource = false))
        assertTrue(AppLibrary.droidDeckTakesSource(20))
        assertEquals(
            "[gamenative] 202\n[droiddeck] 10065604626084986880\n[droiddeck_source] EPIC\n",
            AppLibrary.stubText(kites, takesSource = true),
        )
        // Steam: the app id alone, which both apps take (and Steam art and achievements read).
        assertEquals("1000010\n", AppLibrary.stubText(tiles.first { it.source == "steam" }, takesSource = true))
        // A game added by hand has no store to name.
        assertEquals("[droiddeck] 10065604626084988000\n", AppLibrary.stubText(tiles.first { it.source == "added" }, takesSource = true))
        assertEquals("XBOX", AppLibrary.droidDeckSource(ddXbox))
        assertNull(AppLibrary.droidDeckSource(ddSteam))
        // The players' templates read the lines: {tags.gamenative} and {tags.droiddeck}.
        val tags = AppLibrary.stubText(kites, takesSource = true).lines().mapNotNull { Regex("""^\[(\w+)] (.*)$""").find(it) }
            .associate { it.groupValues[1] to it.groupValues[2] }
        assertEquals(mapOf("gamenative" to "202", "droiddeck" to "10065604626084986880", "droiddeck_source" to "EPIC"), tags)
    }

    @Test fun startsWhereItWasLastPlayed() {
        val both = AppLibrary.tiles(listOf(gnEpic, ddEpic)).first.single()
        // Never played in either: the first app that has it.
        assertEquals(DROIDDECK, AppLibrary.defaultApp(both))
        val gnLater = AppLibrary.tiles(listOf(gnEpic.copy(lastPlayed = 2000), ddEpic.copy(lastPlayed = 1000))).first.single()
        assertEquals(GAMENATIVE, AppLibrary.defaultApp(gnLater))
        val ddOnly = AppLibrary.tiles(listOf(gnEpic, ddEpic.copy(lastPlayed = 5))).first.single()
        assertEquals(DROIDDECK, AppLibrary.defaultApp(ddOnly))
    }

    @Test fun theUsersShortcutWins() {
        val (tiles, _) = AppLibrary.tiles(listOf(gnSteam, ddSteam, gnEpic, ddEpic, gnAmazon, ddXbox))
        // The folders: GameNative's "Export for frontend" files (its own number inside), and one for a game no app has.
        val scanned = listOf(
            "Moss Garden.steam" to "1000010",
            "Paper Kites.epic" to "202",
            "Ember Road.gog" to "555",
            "notes.pcgame" to "202",
        )
        val merged = AppLibrary.merge(scanned, tiles, { (name, line) -> AppLibrary.refOf(name.substringAfterLast('.'), line) }) { t -> t.fileName to "" }
        assertEquals(
            listOf("Moss Garden.steam", "Paper Kites.epic", "Ember Road.gog", "notes.pcgame",
                "droiddeck-xbox-10065604626084987616.xbox", "gamenative-amazon-7.amazon"),
            merged.map { it.first },
        )
        // A file DroidDeck exported (its 64-bit id inside) stands for the same tile.
        val ref = AppLibrary.refOf("epic", "10065604626084986880")!!
        assertEquals("epic", AppLibrary.tileFor(ref, tiles)!!.source)
        assertEquals(AppLibrary.Ref("steam", "413150"), AppLibrary.refOf("steamappid", "app_id=0413150"))
        assertEquals(AppLibrary.Ref("epic", "202"), AppLibrary.refOf("epicgame", "202"))
        assertNull(AppLibrary.refOf("pcgame", "202"))
        assertNull(AppLibrary.refOf("epic", "a1b2c3"))
        assertNull(AppLibrary.refOf("epic", null))
    }

    @Test fun nothingInstalledKeepsTheFolders() {
        val scanned = listOf("a.steam" to "1")
        assertTrue(AppLibrary.merge(scanned, emptyList(), { null }) { t -> t.fileName to "" } === scanned)
    }

    @Test fun titlesCompareLoosely() {
        assertEquals("paperkites2", AppLibrary.normalTitle("Paper Kites™ 2: "))
        assertEquals(AppLibrary.normalTitle("ÉCLAT"), AppLibrary.normalTitle("éclat"))
    }

    @Test fun extensions() {
        assertEquals("epic", AppLibrary.extension("epic"))
        assertEquals("droiddeck", AppLibrary.extension("added"))
        assertEquals("steam", AppLibrary.sourceOfExtension("STEAMAPPID"))
        assertEquals("added", AppLibrary.sourceOfExtension("droiddeck"))
        assertNull(AppLibrary.sourceOfExtension("pcgame"))
        assertNull(AppLibrary.sourceOfExtension("moonlight"))
    }
}
