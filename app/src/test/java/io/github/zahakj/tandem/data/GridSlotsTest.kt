package io.github.zahakj.tandem.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Where icon grid tiles sit (GridSlots): gaps, moves, Tidy up and changing the number of rows. */
class GridSlotsTest {
    private val e = GridSlots.EMPTY

    @Test
    fun aPackedOrderFromBeforeSlotsShowsTheSameAtAnyRows() {
        val order = listOf("s:snes", "s:gba", "folder:a", "col:fav")
        val present = listOf("s:snes", "s:gba", "col:fav", "folder:a")
        for (rows in 1..5) assertEquals(order, GridSlots.shown(order, 0, rows, present))
    }

    @Test
    fun anOldArrangementClosesUpOverMissingTilesAsBefore() {
        // Saved before slots (lanes 0): a tile that is gone (a hidden system) leaves no gap, like the packed grid did.
        val order = listOf("s:snes", "s:psx", "s:gba")
        assertEquals(listOf("s:snes", "s:gba"), GridSlots.shown(order, 0, 3, listOf("s:snes", "s:gba")))
    }

    @Test
    fun aMissingTileKeepsItsSlotOncePlaced() {
        val order = listOf("s:snes", "s:psx", "s:gba")
        val shown = GridSlots.shown(order, 3, 3, listOf("s:snes", "s:gba"))
        assertEquals(order, shown)
        assertTrue(GridSlots.free(shown, 1, setOf("s:snes", "s:gba")))
    }

    @Test
    fun newTilesFollowTheLastSlotInDefaultOrder() {
        val order = listOf("a", e, "b")
        assertEquals(listOf("a", e, "b", "c", "d"), GridSlots.shown(order, 2, 2, listOf("c", "a", "b", "d")))
    }

    @Test
    fun aDuplicateKeyOnlyKeepsItsFirstSlot() {
        assertEquals(listOf("a", e, "b"), GridSlots.shown(listOf("a", "a", "b"), 2, 2, listOf("a", "b")))
    }

    @Test
    fun insertAtAnEmptySlot() {
        assertEquals(listOf("a", e, e, e, e, "f"), GridSlots.putAt(listOf("a"), "f", 5))
        assertEquals(listOf("a", "f", "b"), GridSlots.putAt(listOf("a", e, "b"), "f", 1))
        // Already on the grid: it leaves a gap where it was.
        assertEquals(listOf(e, "b", "a"), GridSlots.putAt(listOf("a", "b"), "a", 2))
    }

    @Test
    fun moveIntoAGapLeavesAGapBehind() {
        assertEquals(listOf(e, "b", "a"), GridSlots.move(listOf("a", "b", e), 0, 2))
        // Past the end: the list grows, and nothing trails after the last tile.
        assertEquals(listOf(e, "b", e, e, "a"), GridSlots.move(listOf("a", "b"), 0, 4))
        // Onto a tile: the two trade places.
        assertEquals(listOf("b", "a"), GridSlots.move(listOf("a", "b"), 0, 1))
        // Moving the last tile back drops the empty slots after it.
        assertEquals(listOf("a"), GridSlots.move(listOf(e, e, "a"), 2, 0))
    }

    @Test
    fun removingLeavesAGap() {
        assertEquals(listOf("a", e, "c"), GridSlots.clear(listOf("a", "b", "c"), "b"))
        assertEquals(listOf("a"), GridSlots.clear(listOf("a", "b"), "b"))
    }

    @Test
    fun tidyUpPacksInOrder() {
        assertEquals(listOf("a", "b", "c"), GridSlots.tidy(listOf(e, "a", e, e, "b", "c", e)))
        // Tiles that aren't on the grid are let go.
        assertEquals(listOf("a", "c"), GridSlots.tidy(listOf("a", "gone", e, "c"), setOf("a", "c")))
    }

    @Test
    fun trailingEmptySlotsAreNeverKept() {
        assertEquals(listOf("a", e, "b"), GridSlots.trim(listOf("a", e, "b", e, e)))
        assertFalse(GridSlots.hasGaps(listOf("a", "b", e)))
        assertTrue(GridSlots.hasGaps(listOf("a", e, "b")))
    }

    @Test
    fun moreRowsKeepEachTilesColumnAndRow() {
        // 2 rows: column 0 = a, b; column 1 empty; column 2 = c, -.
        val two = listOf("a", "b", e, e, "c")
        val three = GridSlots.relayout(two, 2, 3)
        assertEquals(listOf("a", "b", e, e, e, e, "c"), three)
    }

    @Test
    fun fewerRowsKeepPositionsWhenTheyFit() {
        // 3 rows with nothing in the bottom row: going to 2 rows keeps every column.
        val three = listOf("a", "b", e, e, e, e, "c", "d")
        assertEquals(listOf("a", "b", e, e, "c", "d"), GridSlots.relayout(three, 3, 2))
    }

    @Test
    fun fewerRowsReflowWhenTilesDontFit() {
        // Something in the bottom row: everything flows in reading order, gaps included.
        val three = listOf("a", "b", "c", e, e, e, "d")
        assertEquals(three, GridSlots.relayout(three, 3, 2))
    }

    @Test
    fun aPackedGridAlwaysFlows() {
        val packed = listOf("a", "b", "c", "d", "e")
        assertEquals(packed, GridSlots.relayout(packed, 2, 3))
        assertEquals(packed, GridSlots.relayout(packed, 3, 1))
    }

    @Test
    fun shownAtNewRowsKeepsPlacesAndAddsNewTilesAfter() {
        val stored = listOf("a", e, e, e, "b")
        assertEquals(listOf("a", e, e, e, e, e, "b", "c"), GridSlots.shown(stored, 2, 3, listOf("a", "b", "c")))
    }

    @Test
    fun alwaysAnEmptyColumnAfterTheLastTile() {
        // 5 tiles in 2 rows use 3 columns; one more empty column makes 8 slots.
        assertEquals(8, GridSlots.shownCount(listOf("a", "b", "c", "d", "e"), 2))
        // A screenful at least.
        assertEquals(12, GridSlots.shownCount(listOf("a"), 2, atLeast = 6))
        // Pages of 3 columns: the used page, then a whole empty page.
        assertEquals(12, GridSlots.shownCount(listOf("a", "b", "c"), 2, minEmpty = 3, page = 3))
        assertEquals(0 + 2, GridSlots.shownCount(emptyList(), 2))
    }

    @Test
    fun firstFreeSkipsTilesButNotMissingOnes() {
        val slots = listOf("a", "gone", e, "b")
        assertEquals(1, GridSlots.firstFree(slots, 0, setOf("a", "b")))
        assertEquals(2, GridSlots.firstFree(slots, 0))
        assertEquals(4, GridSlots.firstFree(slots, 3, setOf("a", "b")))
    }
}
