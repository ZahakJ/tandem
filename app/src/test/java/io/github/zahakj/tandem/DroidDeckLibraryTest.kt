package io.github.zahakj.tandem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** DroidDeck's library-cache.json as DroidDeckLibrary reads it: Steam games and its other stores'. */
class DroidDeckLibraryTest {
    private val lib = "/data/user/0/com.droiddeck.launcher/files/linuxfs/root/.local/share/Steam"

    /** Made-up games in the shape DroidDeck 0.7.1 writes (LibraryCache.save). */
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
         {"appId":-1951396107,"gameId":-8381139447624564736,"library":"added","source":"epic","storeId":"Quail",
          "installedAt":1763700000000,"lastPlayed":1700000900,"name":"Paper Kites",
          "art":"/data/user/0/com.droiddeck.launcher/files/added-art/2343571189/capsule",
          "hero":"/storage/emulated/0/DroidDeck/Games/Epic/Paper Kites/.droiddeck/hero.jpg"},
         {"appId":-1951396200,"gameId":"10065604626084987000","library":"added","source":"gog","storeId":"1207658930","name":"Ember Road"},
         {"appId":-1951396300,"gameId":-8381139447624564000,"library":"added","name":"My Own Exe"},
         {"appId":1,"gameId":2,"library":"epic-launcher","source":"epic","name":"Epic Games Launcher"},
         {"appId":3,"gameId":4,"library":"xbox-cloud","source":"xbox","name":"Cloud Thing"},
         {"source":"steam","name":"No id"},
         {"appId":1000030,"name":"Tide Clock","art":"/data/local/tmp/elsewhere.jpg","hero":"/data/data/com.droiddeck.launcher/../x/hero.jpg"},
         {"appId":1000010,"source":"steam","name":"Moss Garden again"}
        ]}
    """.trimIndent()

    @Test fun readsTheLibrary() {
        val games = DroidDeckLibrary.parse(fixture)
        assertEquals(
            listOf("1000010", "1000020", "10065604626084986880", "10065604626084987000", "10065604626084987616", "1000030"),
            games.map { it.appId },
        )
        val moss = games[0]
        assertEquals("droiddeck", moss.app)
        assertEquals("steam", moss.source)
        assertEquals("Moss Garden", moss.name)
        assertEquals("droiddeck:steam:1000010", moss.id)
        assertEquals("droiddeck-steam-1000010", moss.stem)
        assertEquals("1000010", moss.storeId)
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

    @Test fun storeGamesGoByTheirUnsignedGameId() {
        val games = DroidDeckLibrary.parse(fixture)
        val kites = games.first { it.name == "Paper Kites" }
        // gameId -8381139447624564736 as DroidDeck prints it (Long.toUnsignedString).
        assertEquals("10065604626084986880", kites.appId)
        assertEquals("epic", kites.source)
        assertEquals("Quail", kites.storeId)
        assertEquals("droiddeck-epic-10065604626084986880", kites.stem)
        // Already in milliseconds: left as it is.
        assertEquals(1_763_700_000_000L, kites.installedAt)
        assertEquals(1_700_000_900_000L, kites.lastPlayed)
        // Its art in DroidDeck's cache, and its hero in the game's folder on shared storage.
        assertEquals("/data/user/0/com.droiddeck.launcher/files/added-art/2343571189/capsule", kites.art)
        assertEquals("/storage/emulated/0/DroidDeck/Games/Epic/Paper Kites/.droiddeck/hero.jpg", kites.hero)
        // The same id written unsigned as a string.
        val ember = games.first { it.name == "Ember Road" }
        assertEquals("10065604626084987000", ember.appId)
        assertEquals("gog", ember.source)
        assertEquals("1207658930", ember.storeId)
        // A game added by hand, from no store: "added".
        val own = games.first { it.name == "My Own Exe" }
        assertEquals("added", own.source)
        assertNull(own.storeId)
    }

    @Test fun everyStoreStarts() {
        val games = DroidDeckLibrary.parse(fixture)
        assertTrue(games.all { it.launchable })
        // DroidDeck's Epic launcher and Xbox cloud games aren't games it starts by id.
        assertFalse(games.any { it.name == "Epic Games Launcher" || it.name == "Cloud Thing" })
        // No source means Steam.
        assertEquals("steam", games.first { it.appId == "1000030" }.source)
        // A store DroidDeck doesn't have, or no id: not started.
        assertFalse(AppGame(source = "humble", appId = "5", name = "x").launchable)
        assertFalse(AppGame(source = "epic", appId = "0", name = "x").launchable)
        assertFalse(AppGame(source = "epic", appId = "18446744073709551616", name = "x").launchable)
    }

    @Test fun picturesOnlyFromDroidDecksFolderOrSharedStorage() {
        val tide = DroidDeckLibrary.parse(fixture).first { it.appId == "1000030" }
        assertNull(tide.art)
        assertNull(tide.hero)
        assertNull(DroidDeckLibrary.safePath("/data/data/com.droiddeck.launcher/files/it's.jpg"))
        assertNull(DroidDeckLibrary.safePath("/data/data/app.gamenative/databases/pluvia.db"))
        assertNull(DroidDeckLibrary.safePath("/storage/emulated/0/../../data/x.jpg"))
        assertEquals("/data/data/com.droiddeck.launcher/files/a.jpg", DroidDeckLibrary.safePath(" /data/data/com.droiddeck.launcher/files/a.jpg "))
        assertEquals("/storage/1234-ABCD/Games/x.png", DroidDeckLibrary.safePath("/storage/1234-ABCD/Games/x.png"))
    }

    @Test fun notALibrary() {
        assertNull(DroidDeckLibrary.parseOrNull("{\"games\":[{\"appId\":1"))
        assertNull(DroidDeckLibrary.parseOrNull("[]"))
        assertEquals(emptyList<AppGame>(), DroidDeckLibrary.parse("nonsense"))
        assertEquals(emptyList<AppGame>(), DroidDeckLibrary.parseOrNull("{\"games\":[]}"))
    }

    @Test fun idsAreTheSameWhicheverWayTheyWereRead() {
        assertEquals("413150", DroidDeckLibrary.normalId("413150"))
        assertEquals("413150", DroidDeckLibrary.normalId(" 0413150 "))
        assertEquals("413150", DroidDeckLibrary.normalId("413150.0"))
        // Unsigned 64-bit ids stay as they are.
        assertEquals("18446744073709551615", DroidDeckLibrary.normalId("18446744073709551615"))
        assertNull(DroidDeckLibrary.normalId("18446744073709551616"))
        assertNull(DroidDeckLibrary.normalId("0"))
        assertNull(DroidDeckLibrary.normalId("-5"))
        assertNull(DroidDeckLibrary.normalId("abc"))
        assertEquals("steam", DroidDeckLibrary.normalSource(" Steam "))
        assertEquals("steam", DroidDeckLibrary.normalSource(null))
    }

    @Test fun unsignedIds() {
        assertEquals("18446744073709551615", AppLibrary.unsigned(-1L))
        assertEquals("9223372036854775808", AppLibrary.unsigned(Long.MIN_VALUE))
        assertEquals("413150", AppLibrary.unsigned(413150L))
        assertEquals("10065604626084986880", AppLibrary.unsignedId("-8381139447624564736"))
        assertEquals("10065604626084986880", AppLibrary.unsignedId("10065604626084986880"))
        assertEquals("10065604626084986880", AppLibrary.unsignedId("-8381139447624564736.0"))
        assertNull(AppLibrary.unsignedId("0"))
        assertNull(AppLibrary.unsignedId("18446744073709551616"))
        assertNull(AppLibrary.unsignedId(""))
        assertNull(AppLibrary.unsignedId("x"))
    }

    @Test fun artPrefersSteamGridArt() {
        val games = DroidDeckLibrary.parse(fixture)
        val moss = games.first()
        val box = DroidDeckLibrary.artCandidates(moss).getValue(DroidDeckLibrary.ArtKind.Box)
        assertEquals(listOf("/data/data/com.droiddeck.launcher/files/steam-art/1000010/grid-p", moss.art), box)
        // A store game has no SteamGridDB art by Steam id: its own pictures only.
        val kites = games.first { it.name == "Paper Kites" }
        assertEquals(listOf(kites.art), DroidDeckLibrary.artCandidates(kites).getValue(DroidDeckLibrary.ArtKind.Box))
        assertEquals(emptyList<String>(), DroidDeckLibrary.artCandidates(kites).getValue(DroidDeckLibrary.ArtKind.Logo))
        // The provider: the row's own link, else the art path by id.
        val shared = moss.copy(art = "content://com.droiddeck.launcher.library/art/1000010/capsule?v=17")
        assertEquals("content://com.droiddeck.launcher.library/art/1000010/capsule?v=17", DroidDeckLibrary.ArtKind.Box.providerUri(shared))
        assertEquals("content://com.droiddeck.launcher.library/art/1000010/hero", DroidDeckLibrary.ArtKind.Hero.providerUri(shared.copy(hero = null)))
    }

    @Test fun statOutput() {
        val out = "1234 1700000000 /data/data/com.droiddeck.launcher/files/library-cache.json\n" +
            "99 1700000001 /data/data/com.droiddeck.launcher/files/steam-art/1/grid-p\nok\n"
        val stats = DroidDeckLibrary.statLines(out)
        assertEquals("1234:1700000000", stats["/data/data/com.droiddeck.launcher/files/library-cache.json"])
        assertEquals(2, stats.size)
    }
}
