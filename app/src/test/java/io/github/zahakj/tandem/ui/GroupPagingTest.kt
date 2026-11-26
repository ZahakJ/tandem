package io.github.zahakj.tandem.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GroupPagingTest {
    private val home = listOf("c:continue", "s:gba", "s:snes", "s:psx", "col:fav", "col:most", "genres")

    @Test fun shouldersStepOneGroupAndWrapRound() {
        assertEquals("s:snes", GroupPaging.step(home, "s:gba", 1))
        assertEquals("c:continue", GroupPaging.step(home, "s:gba", -1))
        assertEquals("c:continue", GroupPaging.step(home, "genres", 1))
        assertEquals("genres", GroupPaging.step(home, "c:continue", -1))
    }

    @Test fun shelvesStopAtTheEnds() {
        assertNull(GroupPaging.step(home, "genres", 1, wrap = false))
        assertNull(GroupPaging.step(home, "c:continue", -1, wrap = false))
        assertEquals("col:most", GroupPaging.step(home, "genres", -1, wrap = false))
    }

    @Test fun fromOutsideTheGroupsForwardIsTheFirstAndBackTheLast() {
        assertEquals("c:continue", GroupPaging.step(home, "app:com.limelight", 1))
        assertEquals("genres", GroupPaging.step(home, null, -1))
    }

    @Test fun oneGroupGoesNowhere() {
        assertNull(GroupPaging.step(listOf("s:gba"), "s:gba", 1))
        assertNull(GroupPaging.step(emptyList(), null, 1))
        assertEquals(null to null, GroupPaging.around(listOf("s:gba"), "s:gba"))
    }

    @Test fun theStripNamesBothNeighbours() {
        assertEquals("s:gba" to "s:psx", GroupPaging.around(home, "s:snes"))
        assertEquals("genres" to "s:gba", GroupPaging.around(home, "c:continue"))
        assertEquals(null to "s:gba", GroupPaging.around(home, "c:continue", wrap = false))
    }

    @Test fun sectionsAreContinueSystemsCollectionsApps() {
        assertEquals(GroupPaging.CONTINUE, GroupPaging.section("c:continue"))
        assertEquals(GroupPaging.CONTINUE, GroupPaging.section("col:continue"))
        assertEquals(GroupPaging.SYSTEMS, GroupPaging.section("s:gba"))
        assertEquals(GroupPaging.COLLECTIONS, GroupPaging.section("col:fav"))
        assertEquals(GroupPaging.COLLECTIONS, GroupPaging.section("genres"))
        assertEquals(GroupPaging.APPS, GroupPaging.section("apps"))
        assertEquals(GroupPaging.APPS, GroupPaging.section("app:com.limelight"))
    }

    @Test fun sectionJumpLandsOnEachSectionsFirstGroup() {
        val keys = home + listOf("apps")
        assertEquals("s:gba", GroupPaging.sectionJump(keys, "c:continue", 1))
        assertEquals("col:fav", GroupPaging.sectionJump(keys, "s:psx", 1))
        assertEquals("s:gba", GroupPaging.sectionJump(keys, "col:most", -1))
        assertEquals("c:continue", GroupPaging.sectionJump(keys, "apps", 1))
        assertNull(GroupPaging.sectionJump(listOf("s:gba", "s:nes"), "s:gba", 1))
    }

    @Test fun theChipWindowKeepsTheCurrentChipInView() {
        assertEquals(0 until 5, GroupPaging.window(5, 3, 9))
        assertEquals(0 until 7, GroupPaging.window(30, 1, 7))
        assertEquals(7 until 14, GroupPaging.window(30, 10, 7))
        assertEquals(23 until 30, GroupPaging.window(30, 29, 7))
        assertEquals(IntRange.EMPTY, GroupPaging.window(0, 0, 7))
    }
}
