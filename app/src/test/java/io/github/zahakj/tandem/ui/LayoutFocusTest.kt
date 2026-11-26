package io.github.zahakj.tandem.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** The highlight stays on the same thing when Start › Layout changes the home layout (LayoutFocus.kt). */
class LayoutFocusTest {
    private val zelda = LayoutSpot(game = "zelda", system = "switch")

    @Test fun aGameWithItsOwnTileStaysOnIt() {
        assertEquals("game:zelda", LayoutFocus.target("grid", zelda.copy(gameOnGrid = true)).home)
    }

    @Test fun aGameWithoutATileLandsOnItsSystem() {
        assertEquals("s:switch", LayoutFocus.target("grid", zelda).home)
        for (l in listOf("carousel", "list", "console")) assertEquals(l, "s:switch", LayoutFocus.target(l, zelda).home)
    }

    @Test fun shelvesFindTheGameInContinue() {
        assertEquals("c:zelda", LayoutFocus.target("shelves", zelda.copy(continuing = true)).home)
        assertEquals("s:switch", LayoutFocus.target("shelves", zelda).home)
    }

    @Test fun stageFlipsToTheGamesPageAndLandsOnIt() {
        val stage = LayoutFocus.target("stage", zelda)
        assertEquals(LayoutFocus.STAGE_STAGE, stage.home)
        assertEquals("s:switch", stage.group)
        assertEquals("zelda", stage.item)
        assertEquals("col:fav", LayoutFocus.target("stage", LayoutSpot(collection = "fav")).group)
        assertEquals("apps", LayoutFocus.target("stage", LayoutSpot(app = "com.limelight")).group)
    }

    @Test fun orbitAndSpotlightTurnToTheGroup() {
        val orbit = LayoutFocus.target("orbit", zelda)
        assertEquals(LayoutFocus.ORBIT_STAGE, orbit.home)
        assertEquals("s:switch", orbit.group)
        val spot = LayoutFocus.target("spotlight", zelda)
        assertEquals(LayoutFocus.SPOTLIGHT_STAGE, spot.home)
        assertEquals("s:switch", spot.group)
        assertEquals("zelda", spot.item)
    }

    @Test fun aCollectionIsACollectionEverywhere() {
        val fav = LayoutSpot(collection = "fav")
        for (l in listOf("grid", "shelves", "carousel", "list", "console")) assertEquals(l, "col:fav", LayoutFocus.target(l, fav).home)
        assertEquals("col:fav", LayoutFocus.target("orbit", fav).group)
        assertEquals("col:fav", LayoutFocus.target("spotlight", fav).group)
    }

    @Test fun anApp() {
        val app = LayoutSpot(app = "com.limelight")
        assertEquals("app:com.limelight", LayoutFocus.target("grid", app).home)
        assertEquals("apps", LayoutFocus.target("shelves", app).home)
        assertEquals("apps", LayoutFocus.target("spotlight", app).group)
        assertEquals("app:com.limelight", LayoutFocus.target("spotlight", app).item)
    }

    @Test fun nothingInParticular() {
        assertEquals(null, LayoutFocus.target("grid", LayoutSpot()).home)
    }
}
