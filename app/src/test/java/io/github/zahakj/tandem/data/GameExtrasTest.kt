package io.github.zahakj.tandem.data

import io.github.zahakj.tandem.CompanionApps
import io.github.zahakj.tandem.GameCompanion
import io.github.zahakj.tandem.RetroAchievements
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Per-game extras: GameMeta's new fields on disk and the logo placement maths. */
class GameExtrasTest {
    @get:Rule val tmp = TemporaryFolder()

    private val json = Store.json

    // --- GameMeta ------------------------------------------------------------------------------------------------

    @Test fun oldLibraryReadsWithDefaults() {
        val old = """{"snes/Mario.sfc":{"title":"Mario","favorite":true,"playSeconds":60}}"""
        val m = json.decodeFromString<Map<String, GameMeta>>(old).getValue("snes/Mario.sfc")
        assertEquals("Mario", m.title)
        assertEquals(null, m.companionApp)
        assertTrue(m.companionAppClose)
        assertEquals(null, m.raId)
        assertEquals(null, m.logoFit)
    }

    @Test fun defaultsAreNotWritten() {
        val text = json.encodeToString(GameMeta(title = "Mario"))
        assertEquals("""{"title":"Mario"}""", text)
    }

    @Test fun extrasRoundTrip() {
        val m = GameMeta(
            companionApp = "com.example.guide", companionAppClose = false, raId = 0,
            logoFit = LogoFit(scale = 130, anchor = "left", dx = -2, dy = 3),
        )
        val back = json.decodeFromString<GameMeta>(json.encodeToString(m))
        assertEquals(m, back)
        // "No achievements" (0) is kept apart from "automatic" (missing).
        assertTrue(json.encodeToString(m).contains("\"raId\":0"))
    }

    @Test fun logoFitDefaultsAreNotWritten() {
        assertEquals("""{"logoFit":{"scale":120}}""", json.encodeToString(GameMeta(logoFit = LogoFit(scale = 120))))
    }

    @Test fun unknownAnchorAndRangesAreTidied() {
        val f = json.decodeFromString<LogoFit>("""{"scale":400,"anchor":"diagonal","dx":99,"dy":-99}""").tidy()
        assertEquals(LogoFit(LogoFit.MAX, LogoFit.AUTO, LogoFit.NUDGE_MAX, -LogoFit.NUDGE_MAX), f)
        assertEquals(LogoFit.MIN, LogoFit(scale = 10).tidy().scale)
    }

    // --- Logo placement --------------------------------------------------------------------------------------------

    private val eps = 0.001f

    @Test fun defaultFitChangesNothing() {
        assertEquals(LogoPlace.Result.NONE, LogoPlace.place(null, 400f, 300f, 100f, 200f, 200f, 80f, 12f))
        assertEquals(LogoPlace.Result.NONE, LogoPlace.place(LogoFit(), 400f, 300f, 100f, 200f, 200f, 80f, 12f))
    }

    @Test fun scaleOnlyKeepsTheCentre() {
        val r = LogoPlace.place(LogoFit(scale = 80), 400f, 300f, 100f, 200f, 200f, 80f, 12f)
        assertEquals(0.8f, r.scale, eps)
        assertEquals(0f, r.dx, eps)
        assertEquals(0f, r.dy, eps)
    }

    @Test fun anchorsMoveToTheEdgesLessTheMargin() {
        // A 200 x 80 logo centred at (200, 240) in a 400 x 300 parent.
        val left = LogoPlace.place(LogoFit(anchor = "left"), 400f, 300f, 100f, 200f, 200f, 80f, 12f)
        assertEquals(12f + 100f - 200f, left.dx, eps)
        assertEquals(0f, left.dy, eps)
        val right = LogoPlace.place(LogoFit(anchor = "right"), 400f, 300f, 100f, 200f, 200f, 80f, 12f)
        assertEquals(400f - 12f - 100f - 200f, right.dx, eps)
        val top = LogoPlace.place(LogoFit(anchor = "top"), 400f, 300f, 100f, 200f, 200f, 80f, 12f)
        assertEquals(0f, top.dx, eps)
        assertEquals(12f + 40f - 240f, top.dy, eps)
        val bottom = LogoPlace.place(LogoFit(anchor = "bottom"), 400f, 300f, 100f, 200f, 200f, 80f, 12f)
        assertEquals(300f - 12f - 40f - 240f, bottom.dy, eps)
        // A left-aligned logo moved to the centre.
        val centre = LogoPlace.place(LogoFit(anchor = "center"), 400f, 300f, 0f, 0f, 100f, 50f, 12f)
        assertEquals(200f - 50f, centre.dx, eps)
    }

    @Test fun anchorsUseTheScaledSize() {
        val r = LogoPlace.place(LogoFit(scale = 50, anchor = "left"), 400f, 300f, 100f, 200f, 200f, 80f, 12f)
        // Half as wide (100): its centre sits 12 + 50 from the left edge.
        assertEquals(12f + 50f - 200f, r.dx, eps)
    }

    @Test fun nudgesAreAShareOfTheParent() {
        val r = LogoPlace.place(LogoFit(dx = 2, dy = -1), 400f, 300f, 100f, 100f, 200f, 80f, 12f)
        assertEquals(2 * LogoFit.NUDGE_SHARE * 400f, r.dx, eps)
        assertEquals(-1 * LogoFit.NUDGE_SHARE * 300f, r.dy, eps)
    }

    @Test fun nudgesStopAtTheEdge() {
        val r = LogoPlace.place(LogoFit(anchor = "right", dx = 10), 400f, 300f, 100f, 100f, 200f, 80f, 12f)
        assertEquals(400f - 12f - 100f - 200f, r.dx, eps)
    }

    @Test fun aLogoAlreadyNearTheEdgeDoesNotJump() {
        // Its layout put it 4 px from the left; only making it smaller keeps that centre.
        val r = LogoPlace.place(LogoFit(scale = 90), 400f, 300f, 4f, 100f, 200f, 80f, 12f)
        assertEquals(0f, r.dx, eps)
    }

    @Test fun anchorsCycle() {
        var a = LogoFit.AUTO
        val seen = mutableListOf<String>()
        repeat(LogoFit.ANCHORS.size) { seen += a; a = LogoFit.nextAnchor(a) }
        assertEquals(LogoFit.ANCHORS.map { it.first }, seen)
        assertEquals(LogoFit.AUTO, a)
    }

    // --- Companion app and RetroAchievements rules -----------------------------------------------------------------

    @Test fun companionAppRules() {
        assertTrue(CompanionApps.wanted("com.guide", "org.emu", bottom = false, dualScreen = true, mode = GameCompanion.ART))
        assertFalse(CompanionApps.wanted(null, "org.emu", bottom = false, dualScreen = true, mode = GameCompanion.ART))
        assertFalse(CompanionApps.wanted("com.guide", "org.emu", bottom = true, dualScreen = true, mode = GameCompanion.ART))
        assertFalse(CompanionApps.wanted("com.guide", "org.emu", bottom = false, dualScreen = false, mode = GameCompanion.ART))
        assertFalse(CompanionApps.wanted("com.guide", "org.emu", bottom = false, dualScreen = true, mode = GameCompanion.OFF))
        assertFalse(CompanionApps.wanted("org.emu", "org.emu", bottom = false, dualScreen = true, mode = GameCompanion.TANDEM))
    }

    @Test fun raCandidatesRankClosestFirst() {
        val games = listOf(
            1 to "Legend of Zelda, The: The Minish Cap", 2 to "Zelda II: The Adventure of Link",
            3 to "~Hack~ Minish Cap Redux", 4 to "Metroid Fusion", 5 to "Minish Woods",
        )
        val r = RetroAchievements.rank(listOf("The Legend of Zelda - The Minish Cap (USA)"), games, 3)
        assertEquals(1, r.first().first)
        assertFalse(r.any { it.first == 3 || it.first == 4 })
    }
}
