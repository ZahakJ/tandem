package io.github.zahakj.tandem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** DroidDeck's library-cache.json as DroidDeckLibrary reads it, and how its games join the ones in the game folders. */
class DroidDeckLibraryTest {
    private val lib = "/data/user/0/com.droiddeck.launcher/files/linuxfs/root/.local/share/Steam"

    /** Made-up games in the shape DroidDeck 0.7.1 writes. */
    private val fixture = """
        {"client":"arm64","games":[
         {"appId":1000010,"gameId":1000010,"source":"steam","library":"internal","installedAt":1700000000,"lastPlayed":0,
          "art":"$lib/appcache/librarycache/1000010/library_600x900.jpg",
          "hero":"$lib/appcache/librarycache/1000010/library_hero.jpg",
          "icon":"$lib/appcache/librarycache/1000010/0123456789abcdef.jpg",
          "logo":"$lib/appcache/librarycache/1000010/logo.png",
          "gameFiles":"$lib/steamapps/common/Moss Garden","protonPrefix":"$lib/steamapps/compatdata/1000010/pfx",
          "name":"Moss Garden"},
         {"appId":"1000020","source":"STEAM","installedAt":0,"lastPlayed":1700000500,
          "art":"$lib/appcache/librarycache/1000020/fedcba9876543210/library_capsule.jpg","name":" Lantern Post "},
         {"appId":77,"source":"epic","name":"Paper Kites"},
         {"source":"steam","name":"No id"},
         {"appId":1000030,"name":"Tide Clock","art":"/sdcard/elsewhere.jpg","hero":"/data/data/com.droiddeck.launcher/../x/hero.jpg"},
         {"appId":1000010,"source":"steam","name":"Moss Garden again"}
        ]}
    """.trimIndent()

    @Test fun readsTheLibrary() {
        val games = DroidDeckLibrary.parse(fixture)
        assertEquals(listOf("1000010", "1000020", "77", "1000030"), games.map { it.appId })
        val moss = games[0]
        assertEquals("steam", moss.source)
        assertEquals("Moss Garden", moss.name)
        assertEquals("droiddeck:steam:1000010", moss.id)
        assertEquals("droiddeck-steam-1000010", moss.stem)
        // Seconds in the file, milliseconds in Tandem (the provider's unit).
        assertEquals(1_700_000_000_000L, moss.installedAt)
        assertEquals(0L, moss.lastPlayed)
        assertEquals("$lib/appcache/librarycache/1000010/library_600x900.jpg", moss.art)
        assertEquals("$lib/appcache/librarycache/1000010/logo.png", moss.logo)

        val lantern = games[1]
        assertEquals("steam", lantern.source)
        assertEquals("Lantern Post", lantern.name)
        assertEquals(1_700_000_500_000L, lantern.lastPlayed)
        assertTrue(lantern.art!!.endsWith("/fedcba9876543210/library_capsule.jpg"))
        assertNull(lantern.hero)
    }

    @Test fun onlySteamGamesStart() {
        val games = DroidDeckLibrary.parse(fixture).associateBy { it.appId }
        assertTrue(games.getValue("1000010").launchable)
        assertFalse(games.getValue("77").launchable)
        // No source means Steam.
        assertEquals("steam", games.getValue("1000030").source)
    }

    @Test fun picturesOnlyFromDroidDecksFolder() {
        val tide = DroidDeckLibrary.parse(fixture).first { it.appId == "1000030" }
        assertNull(tide.art)
        assertNull(tide.hero)
        assertNull(DroidDeckLibrary.safePath("/data/data/com.droiddeck.launcher/files/it's.jpg"))
        assertEquals("/data/data/com.droiddeck.launcher/files/a.jpg", DroidDeckLibrary.safePath(" /data/data/com.droiddeck.launcher/files/a.jpg "))
    }

    @Test fun notALibrary() {
        assertNull(DroidDeckLibrary.parseOrNull("{\"games\":[{\"appId\":1"))
        assertNull(DroidDeckLibrary.parseOrNull("[]"))
        assertEquals(emptyList<DroidDeckGame>(), DroidDeckLibrary.parse("nonsense"))
        assertEquals(emptyList<DroidDeckGame>(), DroidDeckLibrary.parseOrNull("{\"games\":[]}"))
    }

    @Test fun idsAreTheSameWhicheverWayTheyWereRead() {
        assertEquals("413150", DroidDeckLibrary.normalId("413150"))
        assertEquals("413150", DroidDeckLibrary.normalId(" 0413150 "))
        assertEquals("413150", DroidDeckLibrary.normalId("413150.0"))
        // Unsigned 64-bit ids stay as they are.
        assertEquals("18446744073709551615", DroidDeckLibrary.normalId("18446744073709551615"))
        assertNull(DroidDeckLibrary.normalId("0"))
        assertNull(DroidDeckLibrary.normalId("-5"))
        assertNull(DroidDeckLibrary.normalId("abc"))
        assertEquals("steam", DroidDeckLibrary.normalSource(" Steam "))
        assertEquals("steam", DroidDeckLibrary.normalSource(null))
    }

    @Test fun artPrefersSteamGridArt() {
        val moss = DroidDeckLibrary.parse(fixture).first()
        val box = DroidDeckLibrary.artCandidates(moss).getValue(DroidDeckLibrary.ArtKind.Box)
        assertEquals(listOf("/data/data/com.droiddeck.launcher/files/steam-art/1000010/grid-p", moss.art), box)
        // The provider: the row's own link, else the art path by id.
        val shared = moss.copy(art = "content://com.droiddeck.launcher.library/art/1000010/capsule?v=17")
        assertEquals("content://com.droiddeck.launcher.library/art/1000010/capsule?v=17", DroidDeckLibrary.ArtKind.Box.providerUri(shared))
        assertEquals("content://com.droiddeck.launcher.library/art/1000010/hero", DroidDeckLibrary.ArtKind.Hero.providerUri(shared.copy(hero = null)))
    }

    @Test fun mergeAddsOnlyWhatNoShortcutHas() {
        val installed = DroidDeckLibrary.parse(fixture)
        // The folders hold two shortcuts: one for Lantern Post (a .steam file with its id), one for a game DroidDeck lacks.
        val scanned = listOf("lantern.steam" to "1000020", "other.steam" to "555", "readme.pcgame" to null)
        val merged = DroidDeckLibrary.merge(scanned, installed, { it.second }) { g -> g.stem to g.appId }
        assertEquals(
            listOf("lantern.steam", "other.steam", "readme.pcgame", "droiddeck-steam-1000010", "droiddeck-steam-1000030"),
            merged.map { it.first },
        )
    }

    @Test fun mergeWithNothingInstalledKeepsTheFolders() {
        val scanned = listOf("a.steam" to "1")
        assertTrue(DroidDeckLibrary.merge(scanned, emptyList(), { it.second }) { g -> g.stem to g.appId } === scanned)
    }

    @Test fun statOutput() {
        val out = "1234 1700000000 /data/data/com.droiddeck.launcher/files/library-cache.json\n" +
            "99 1700000001 /data/data/com.droiddeck.launcher/files/steam-art/1/grid-p\nok\n"
        val stats = DroidDeckLibrary.statLines(out)
        assertEquals("1234:1700000000", stats["/data/data/com.droiddeck.launcher/files/library-cache.json"])
        assertEquals(2, stats.size)
    }
}
