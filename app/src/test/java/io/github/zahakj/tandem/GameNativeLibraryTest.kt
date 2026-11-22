package io.github.zahakj.tandem

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.sql.Connection
import java.sql.DriverManager

/**
 * GameNative's database as GameNativeLibrary reads it: a real SQLite file with GameNative's own table and column names
 * (its Room entities: SteamApp, AppInfo, EpicGame, GOGGame, AmazonGame, LibraryPlayHistory), made-up games in it.
 */
class GameNativeLibraryTest {
    private val file: File = File.createTempFile("pluvia", ".db")
    private val conn: Connection = DriverManager.getConnection("jdbc:sqlite:${file.path}")

    @After fun close() {
        conn.close()
        file.delete()
    }

    private fun exec(vararg sql: String) = conn.createStatement().use { st -> sql.forEach { st.executeUpdate(it) } }

    /** The same queries on the device go through Android's SQLite; here through the JDBC driver. */
    private val db = GameNativeLibrary.Db { sql ->
        runCatching {
            conn.createStatement().use { st ->
                st.executeQuery(sql).use { rs ->
                    val meta = rs.metaData
                    val out = ArrayList<Map<String, Any?>>()
                    while (rs.next()) {
                        out += (1..meta.columnCount).associate { i ->
                            meta.getColumnLabel(i) to when (val v = rs.getObject(i)) {
                                is Int -> v.toLong()
                                else -> v
                            }
                        }
                    }
                    out
                }
            }
        }.getOrNull()
    }

    private fun current() {
        exec(
            "CREATE TABLE steam_app (id INTEGER PRIMARY KEY NOT NULL, name TEXT NOT NULL, install_dir TEXT NOT NULL, " +
                "logo_hash TEXT NOT NULL DEFAULT '', icon_hash TEXT NOT NULL DEFAULT '', header_image TEXT NOT NULL DEFAULT '')",
            "CREATE TABLE app_info (id INTEGER PRIMARY KEY NOT NULL, is_downloaded INTEGER NOT NULL, downloaded_depots TEXT NOT NULL DEFAULT '[]', " +
                "branch TEXT NOT NULL DEFAULT 'public', custom_install_path TEXT NOT NULL DEFAULT '')",
            "CREATE TABLE epic_games (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, catalog_id TEXT NOT NULL, app_name TEXT NOT NULL, " +
                "title TEXT NOT NULL, namespace TEXT NOT NULL, is_installed INTEGER NOT NULL, install_path TEXT NOT NULL, " +
                "art_cover TEXT NOT NULL, art_square TEXT NOT NULL, art_logo TEXT NOT NULL, art_portrait TEXT NOT NULL, is_dlc INTEGER NOT NULL)",
            "CREATE TABLE gog_games (id TEXT PRIMARY KEY NOT NULL, title TEXT NOT NULL, is_installed INTEGER NOT NULL, install_path TEXT NOT NULL, " +
                "image_url TEXT NOT NULL, icon_url TEXT NOT NULL, background_url TEXT NOT NULL DEFAULT '', vertical_cover_url TEXT NOT NULL DEFAULT '', " +
                "last_played INTEGER NOT NULL, exclude INTEGER NOT NULL DEFAULT 0)",
            "CREATE TABLE amazon_games (app_id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, product_id TEXT NOT NULL, title TEXT NOT NULL, " +
                "is_installed INTEGER NOT NULL, install_path TEXT NOT NULL, art_url TEXT NOT NULL, hero_url TEXT NOT NULL DEFAULT '', " +
                "last_played INTEGER NOT NULL DEFAULT 0)",
            "CREATE TABLE library_play_history (app_id TEXT PRIMARY KEY NOT NULL, last_played INTEGER NOT NULL DEFAULT 0)",
            "CREATE TABLE downloading_app_info (appId INTEGER PRIMARY KEY NOT NULL)",
        )
        exec(
            // Steam: installed is app_info.is_downloaded, not install_dir (every owned game has one).
            "INSERT INTO steam_app (id, name, install_dir) VALUES (1000010, 'Moss Garden', 'Moss Garden'), " +
                "(1000020, 'Lantern Post', 'Lantern Post'), (1000030, 'Tide Clock', 'Tide Clock')",
            "INSERT INTO app_info (id, is_downloaded) VALUES (1000010, 1), (1000020, 0)",
            // Epic: 436 rows on a real device, one installed; DLC and Unreal Engine content never count.
            "INSERT INTO epic_games (id, catalog_id, app_name, title, namespace, is_installed, install_path, art_cover, art_square, art_logo, art_portrait, is_dlc) VALUES " +
                "(202, 'cat202', 'a1b2c3d4e5f6', 'Paper Kites', 'ns1', 1, '/x', 'https://cdn.example.com/kites/cover.jpg', 'https://cdn.example.com/kites/square.jpg', '', 'https://cdn.example.com/kites/wide.jpg', 0), " +
                "(203, 'cat203', 'b2', 'Paper Kites Soundtrack', 'ns1', 1, '/y', '', '', '', '', 1), " +
                "(204, 'cat204', 'c3', 'Some Engine Pack', 'ue', 1, '/z', '', '', '', '', 0), " +
                "(205, 'cat205', 'd4', 'Not Downloaded', 'ns2', 0, '', '', '', '', '', 0)",
            "INSERT INTO gog_games (id, title, is_installed, install_path, image_url, icon_url, vertical_cover_url, last_played, exclude) VALUES " +
                "('1207658930', 'Ember Road', 1, '/g', 'https://images.example.com/ember.jpg', '', '', 1700000000000, 0), " +
                "('1207658931', 'Hidden Away', 1, '/h', '', '', '', 0, 1)",
            "INSERT INTO amazon_games (app_id, product_id, title, is_installed, install_path, art_url, last_played) VALUES " +
                "(7, 'amzn1.adg.product.1', 'Hollow Bay', 1, '/a', 'https://m.example.com/hollow.png', 0), " +
                "(8, 'amzn1.adg.product.2', 'Not Here', 0, '', '', 0)",
            "INSERT INTO library_play_history (app_id, last_played) VALUES ('EPIC_202', 1763000000000), ('STEAM_1000010', 1762000000000)",
        )
    }

    @Test fun readsInstalledGamesOfEveryStore() {
        current()
        val games = GameNativeLibrary.read(db)!!
        assertEquals(listOf("steam:1000010", "epic:202", "gog:1207658930", "amazon:7"), games.map { "${it.source}:${it.appId}" })
        assertTrue(games.all { it.app == AppLibrary.GAMENATIVE && it.launchable })

        val moss = games[0]
        assertEquals("Moss Garden", moss.name)
        assertEquals("1000010", moss.storeId)
        assertEquals(1_762_000_000_000L, moss.lastPlayed)

        val kites = games[1]
        assertEquals("Paper Kites", kites.name)
        // GameNative's own row id is what its launch takes; Epic's app name is what matches DroidDeck's.
        assertEquals("202", kites.appId)
        assertEquals("a1b2c3d4e5f6", kites.storeId)
        assertEquals(1_763_000_000_000L, kites.lastPlayed)
        assertEquals("https://cdn.example.com/kites/cover.jpg", kites.art)
        assertEquals("https://cdn.example.com/kites/wide.jpg", kites.hero)
        assertNull(kites.logo)
        assertEquals("gamenative-epic-202", kites.stem)

        val ember = games[2]
        assertEquals("1207658930", ember.storeId)
        assertEquals(1_700_000_000_000L, ember.lastPlayed)
        // No tall cover or icon: the image GameNative's library shows.
        assertEquals("https://images.example.com/ember.jpg", ember.art)
        assertEquals("https://images.example.com/ember.jpg", ember.hero)

        val hollow = games[3]
        assertEquals("amzn1.adg.product.1", hollow.storeId)
        assertEquals("https://m.example.com/hollow.png", hollow.art)
    }

    @Test fun anOlderGameNativeWithFewerColumns() {
        exec(
            "CREATE TABLE gog_games (id TEXT PRIMARY KEY NOT NULL, title TEXT NOT NULL, is_installed INTEGER NOT NULL, image_url TEXT NOT NULL)",
            "INSERT INTO gog_games VALUES ('42', 'Small World', 1, '')",
            "CREATE TABLE epic_games (id INTEGER PRIMARY KEY, title TEXT, app_name TEXT)",
        )
        // No steam_app, app_info, amazon_games or history; epic_games without is_installed tells nothing installed.
        val games = GameNativeLibrary.read(db)!!
        assertEquals(listOf("gog:42"), games.map { "${it.source}:${it.appId}" })
        assertNull(games.single().art)
    }

    @Test fun notADatabase() {
        val broken = GameNativeLibrary.Db { null }
        assertNull(GameNativeLibrary.read(broken))
        // An empty one: no games.
        assertEquals(emptyList<AppGame>(), GameNativeLibrary.read(db))
    }

    @Test fun picturesFromGameNativesCache() {
        // Coil names a cached picture by the SHA-256 of its address, and keeps the bytes in "<hash>.1".
        assertEquals(
            "/data/data/app.gamenative/cache/image_cache/fc390954bde7aa878d9eaa19025e070a654b5d7c346e0099cbfb4370ce1af47b.1",
            GameNativeLibrary.cachedPicture("https://cdn.example.com/kites/cover.jpg"),
        )
        current()
        val kites = GameNativeLibrary.read(db)!!.first { it.source == "epic" }
        val art = GameNativeLibrary.artCandidates(kites)
        assertEquals(listOf(GameNativeLibrary.cachedPicture("https://cdn.example.com/kites/cover.jpg")), art.getValue(DroidDeckLibrary.ArtKind.Box))
        assertEquals(emptyList<String>(), art.getValue(DroidDeckLibrary.ArtKind.Logo))
        // A Steam game has no addresses here: its art is Steam's (SteamArt) or DroidDeck's.
        val moss = GameNativeLibrary.read(db)!!.first { it.source == "steam" }
        assertTrue(GameNativeLibrary.artCandidates(moss).values.all { it.isEmpty() })
    }

    @Test fun bothAppsOneTile() {
        current()
        val gn = GameNativeLibrary.read(db)!!
        val dd = listOf(AppGame(AppLibrary.DROIDDECK, "epic", "10065604626084986880", "Paper Kites", storeId = "a1b2c3d4e5f6"))
        val (tiles, _) = AppLibrary.tiles(gn + dd)
        assertEquals(4, tiles.size)
        val kites = tiles.first { it.source == "epic" }
        assertEquals(listOf(AppLibrary.DROIDDECK, AppLibrary.GAMENATIVE), kites.apps)
        // Played in GameNative last (its history), so it starts there.
        assertEquals(AppLibrary.GAMENATIVE, AppLibrary.defaultApp(kites))
    }
}
