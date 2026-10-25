package io.github.zahakj.tandem.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Per-app choices (AppPrefs.kt): the file format, defaults and bringing older data up to date, without Android. */
class AppPrefsTest {

    @Test fun nothingSavedMeansEverythingAsAndroidSays() {
        val d = AppPrefs.decode(null)
        assertEquals(0, d.version)
        assertEquals(AppScreen.AUTO, d.defaultScreen)
        assertTrue(d.holdBackMoves)
        assertFalse(d.moveGames)
        val p = d.of("com.discord")
        assertNull(p.screen); assertNull(p.name); assertNull(p.art); assertNull(p.game); assertNull(p.platform)
        assertFalse(p.hidden)
        assertTrue(p.isDefault)
        assertEquals(AppScreen.AUTO, d.screenOf("com.discord"))
    }

    @Test fun unreadableFileFallsBackToDefaults() {
        assertEquals(AppsData(), AppPrefs.decode("{ not json"))
        assertEquals(AppsData(), AppPrefs.decode(""))
    }

    @Test fun roundTripsEveryField() {
        val d = AppsData(
            version = AppPrefs.VERSION,
            apps = mapOf(
                "com.discord" to AppPref(screen = AppScreen.BOTTOM, name = "Chat", art = "/storage/emulated/0/ROMs/icons/chat.png", hidden = true),
                "com.example.game" to AppPref(game = true, platform = "android"),
                "com.example.notgame" to AppPref(game = false),
            ),
            defaultScreen = AppScreen.TOP,
            holdBackMoves = false,
            moveGames = true,
        )
        assertEquals(d, AppPrefs.decode(AppPrefs.encode(d)))
    }

    @Test fun defaultsAreLeftOutOfTheFile() {
        val text = AppPrefs.encode(AppsData(version = 1, apps = mapOf("a.b" to AppPref(name = "Mine"))))
        assertFalse(text.contains("hidden"))
        assertFalse(text.contains("defaultScreen"))
        assertTrue(text.contains("\"name\":\"Mine\""))
    }

    @Test fun unknownFieldsFromANewerTandemAreIgnored() {
        val d = AppPrefs.decode("""{"version":1,"apps":{"a.b":{"screen":"top","sparkle":true}},"future":1}""")
        assertEquals(AppScreen.TOP, d.screenOf("a.b"))
    }

    @Test fun anAppsOwnScreenWinsOverTheDefault() {
        val d = AppsData(apps = mapOf("a.b" to AppPref(screen = AppScreen.TOP)), defaultScreen = AppScreen.BOTTOM)
        assertEquals(AppScreen.TOP, d.screenOf("a.b"))
        assertEquals(AppScreen.BOTTOM, d.screenOf("c.d"))
    }

    @Test fun resettingAnAppToDefaultsDropsItsEntry() {
        val d = AppsData().with("a.b") { it.copy(screen = AppScreen.BOTTOM) }
        assertEquals(setOf("a.b"), d.apps.keys)
        assertTrue(d.with("a.b") { it.copy(screen = null) }.apps.isEmpty())
    }

    @Test fun screenChoicesStepAutoTopBottom() {
        assertEquals(AppScreen.TOP, AppScreen.next(AppScreen.AUTO))
        assertEquals(AppScreen.BOTTOM, AppScreen.next(AppScreen.TOP))
        assertEquals(AppScreen.AUTO, AppScreen.next(AppScreen.BOTTOM))
        assertEquals(AppScreen.TOP, AppScreen.next("sideways"))
    }

    @Test fun migratesAndroidGamesChoicesFromSystemsJson() {
        val old = AppsData(apps = mapOf("kept.choice" to AppPref(game = false)))
        val d = AppPrefs.migrate(old, gameApps = setOf("added.game", "kept.choice"), notGameApps = setOf("removed.game"))
        assertEquals(AppPrefs.VERSION, d.version)
        assertEquals(true, d.of("added.game").game)
        assertEquals(false, d.of("removed.game").game)
        // An app's own choice is never overwritten by the old lists.
        assertEquals(false, d.of("kept.choice").game)
    }

    @Test fun migrationRunsOnce() {
        val current = AppsData(version = AppPrefs.VERSION)
        assertEquals(current, AppPrefs.migrate(current, gameApps = setOf("added.game"), notGameApps = emptySet()))
    }

    @Test fun inBothOldListsAddedWins() {
        val d = AppPrefs.migrate(AppsData(), gameApps = setOf("x.y"), notGameApps = setOf("x.y"))
        assertEquals(true, d.of("x.y").game)
    }

    @Test fun unknownScreensFallBackToAuto() {
        val d = AppPrefs.migrate(
            AppsData(version = 1, apps = mapOf("a.b" to AppPref(screen = "left"), "c.d" to AppPref(screen = "left", name = "C")), defaultScreen = "middle"),
            emptySet(), emptySet(),
        )
        assertEquals(AppScreen.AUTO, d.defaultScreen)
        assertFalse("a.b" in d.apps)
        assertNull(d.of("c.d").screen)
        assertEquals("C", d.of("c.d").name)
    }
}
