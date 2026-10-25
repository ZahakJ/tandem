package io.github.zahakj.tandem.data

import io.github.zahakj.tandem.data.GridSlots.Extent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Widgets that cover more than one cell (GridSlots extents). The grids here are sideways with 3 rows: slot i is in
 * column i / 3, row i % 3, so a 2 x 2 widget at slot 0 covers slots 0, 1, 3 and 4.
 */
class GridSpansTest {
    private val e = GridSlots.EMPTY
    private val big = Extent(2, 2)
    private val wide = Extent(2, 1)

    @Test
    fun spansTurnIntoLinesAndLanes() {
        // Sideways, lanes are rows: a 2 x 1 (two columns, one row) widget is two lines long and one lane across.
        assertEquals(Extent(2, 1), GridSlots.extent(GridSpan(2, 1), vertical = false))
        // Up and down, lanes are columns.
        assertEquals(Extent(1, 2), GridSlots.extent(GridSpan(2, 1), vertical = true))
    }

    @Test
    fun aWidgetCoversItsCellsAndNothingElseGoesThere() {
        val slots = listOf("w", "a")
        val ext = mapOf("w" to big)
        assertEquals(listOf(0, 1, 3, 4), GridSlots.cells(0, 3, big))
        // "a" sat in a covered cell: it moves to the first free slot after it.
        assertEquals(listOf("w", e, "a"), GridSlots.settle(slots, 3, ext))
        val owners = GridSlots.owners(listOf("w", e, "a"), 3, ext)
        assertEquals(0, owners[1]); assertEquals(0, owners[3]); assertEquals(0, owners[4]); assertEquals(2, owners[2])
    }

    @Test
    fun aWidgetNeverRunsPastTheLastRow() {
        assertNull(GridSlots.cells(2, 3, big))
        // Placed in the bottom row, it moves on to the first place where it fits whole.
        assertEquals(listOf(e, e, e, "w"), GridSlots.settle(listOf(e, e, "w"), 3, mapOf("w" to big)))
    }

    @Test
    fun fitsAndFirstFitRespectCoveredCells() {
        val slots = listOf("w", e, "a")
        val ext = mapOf("w" to big, "n" to wide)
        assertFalse(GridSlots.fits(slots, 3, ext, "n", 1))
        assertTrue(GridSlots.fits(slots, 3, ext, "n", 5))
        // A single tile: the first slot that no widget covers.
        assertEquals(5, GridSlots.firstFit(slots, 3, ext, 1))
        // A 2 x 2 needs two free rows in two free columns.
        assertEquals(6, GridSlots.firstFit(slots, 3, ext, 0, big))
    }

    @Test
    fun aMissingTileIsAGapAWidgetCanUse() {
        val slots = listOf("gone")
        assertTrue(GridSlots.fits(slots, 3, mapOf("w" to big), "w", 0, present = emptySet()))
        assertFalse(GridSlots.fits(slots, 3, mapOf("w" to big), "w", 0, present = setOf("gone")))
    }

    @Test
    fun movingAWidgetSwapsTheTilesItPassesIntoTheCellsItLeaves() {
        // Columns: [w w -] [w w -] [a b -]
        val slots = listOf("w", e, e, e, e, e, "a", "b")
        val ext = mapOf("w" to big)
        val right = GridSlots.step(slots, 3, ext, "w", 1, 0)!!
        // Now: [a b -] [w w -] [w w -]
        assertEquals(listOf("a", "b", e, "w"), right)
        // Down one row: the bottom row of its columns was free.
        val down = GridSlots.step(slots, 3, ext, "w", 0, 1)!!
        assertEquals(listOf(e, "w", e, e, e, e, "a", "b"), down)
        // Up from the top row or left from the first column: no.
        assertNull(GridSlots.step(slots, 3, ext, "w", 0, -1))
        assertNull(GridSlots.step(slots, 3, ext, "w", -1, 0))
        // Down again from row 1 would run past the last row.
        assertNull(GridSlots.step(down, 3, ext, "w", 0, 1))
    }

    @Test
    fun aWidgetStopsAtAnotherWidget() {
        val slots = listOf("w", e, e, e, e, e, "v")
        val ext = mapOf("w" to big, "v" to big)
        assertNull(GridSlots.step(slots, 3, ext, "w", 1, 0))
    }

    @Test
    fun aTileHopsOverAWidget() {
        // Columns: [a - -] [w w -] [w w -] [- - -]
        val slots = listOf("a", e, e, "w")
        val ext = mapOf("w" to big)
        val moved = GridSlots.step(slots, 3, ext, "a", 1, 0)!!
        // It lands in column 3, past both of the widget's columns.
        assertEquals(listOf(e, e, e, "w", e, e, e, e, e, "a"), moved)
        // Down the first column is free as usual.
        assertEquals(listOf(e, "a", e, "w"), GridSlots.step(slots, 3, ext, "a", 0, 1))
    }

    @Test
    fun resizingMovesTilesOutOfTheWay() {
        // Columns: [w a b] [c - -]
        val slots = listOf("w", "a", "b", "c")
        val grown = GridSlots.resize(slots, 3, emptyMap(), "w", big)!!
        // w covers 0, 1, 3, 4: a and c move on to the first free slots after their own.
        assertEquals(listOf("w", e, "b", e, e, "a", "c"), grown)
        // Shrinking back leaves the gaps (nothing jumps back).
        assertEquals(grown, GridSlots.resize(grown, 3, mapOf("w" to big), "w", GridSlots.ONE))
        // Too tall for the bottom row, or into another widget: no.
        assertNull(GridSlots.resize(listOf(e, e, "w"), 3, emptyMap(), "w", big))
        assertNull(GridSlots.resize(listOf("w", e, e, "v"), 3, mapOf("v" to wide), "w", big))
    }

    @Test
    fun fewerRowsKeepAWidgetWhereItFitsAndMoveItWhereItDoesnt() {
        // 3 rows: a 2 x 2 in rows 0 and 1 of columns 1 and 2.
        val three = listOf("a", e, e, "w")
        val ext = mapOf("w" to big)
        // To 2 rows: every tile keeps its column and row.
        assertEquals(listOf("a", e, "w"), GridSlots.shown(three, 3, 2, listOf("a", "w"), ext))
        // To 1 row the widget is one row high at most (never wider than the grid), so it still fits.
        assertEquals(listOf("a", "w"), GridSlots.shown(three, 3, 1, listOf("a", "w"), ext))
        // In rows 1 and 2 of 3, going to 2 rows can't keep its row: it flows in reading order and settles.
        val low = listOf("a", e, e, e, "w")
        val two = GridSlots.shown(low, 3, 2, listOf("a", "w"), ext)
        assertEquals(listOf("a", e, e, e, "w"), two)
        assertTrue(GridSlots.cells(two.indexOf("w"), 2, big) != null)
    }

    @Test
    fun newTilesNeverLandUnderAWidget() {
        // The widget is the last thing: a tile added after it goes past its covered cells.
        val shown = GridSlots.shown(listOf("a", e, e, "w"), 3, 3, listOf("a", "w", "n"), mapOf("w" to big))
        assertEquals(listOf("a", e, e, "w", e, "n"), shown)
    }

    @Test
    fun tidyUpPacksAroundWidgets() {
        val slots = listOf(e, e, e, "a", e, e, "w", e, e, "b", "c")
        val ext = mapOf("w" to big)
        // a first, then the widget in the first place it fits whole (rows 1 and 2 of columns 0 and 1), then b and c
        // in the first free slots after it.
        assertEquals(listOf("a", "w", e, "b", e, e, "c"), GridSlots.tidy(slots, lanes = 3, ext = ext))
        // Without widgets it packs as before.
        assertEquals(listOf("a", "w", "b", "c"), GridSlots.tidy(slots))
    }

    @Test
    fun aWidgetNeverStraddlesTwoPages() {
        // Pages of 2 columns: a two-column widget in column 1 would show half on each page.
        assertNull(GridSlots.cells(3, 3, big, page = 2))
        assertEquals(listOf(6, 7, 9, 10), GridSlots.cells(6, 3, big, page = 2))
        assertEquals(listOf("a", e, e, e, e, e, "w"), GridSlots.settle(listOf("a", e, e, "w"), 3, mapOf("w" to big), page = 2))
        // Moving right off a full page goes on to the next page's first column, and back again.
        val next = GridSlots.step(listOf("w"), 3, mapOf("w" to big), "w", 1, 0, page = 2)!!
        assertEquals(listOf(e, e, e, e, e, e, "w"), next)
        assertEquals(listOf("w"), GridSlots.step(next, 3, mapOf("w" to big), "w", -1, 0, page = 2))
    }

    @Test
    fun theCanvasReachesPastAWidgetsLastColumn() {
        // A 2 x 2 at column 0 uses two columns; one more empty column makes three.
        assertEquals(9, GridSlots.shownCount(listOf("w"), 3, ext = mapOf("w" to big)))
        // Two screens of 4 columns after the last item.
        assertEquals((2 + 8) * 3, GridSlots.shownCount(listOf("w"), 3, minEmpty = 8, ext = mapOf("w" to big)))
    }

    @Test
    fun theDpadKeepsItsRowAcrossAWidget() {
        // 3 rows; a 2×2 widget in slot 3 covers 3, 4, 6 and 7.
        val slots = listOf("a", "b", "c", "w")
        val own = GridSlots.owners(slots, 3, mapOf("w" to Extent(2, 2)))
        // Right from row 1 lands on the widget (by its cell in row 1)...
        assertEquals(4, GridSlots.stepFocus(15, own, 3, 1, 1, 1, 0))
        // ...and Right again leaves it in row 1, not row 0.
        assertEquals(10, GridSlots.stepFocus(15, own, 3, 3, 4, 1, 0))
        assertEquals(9, GridSlots.stepFocus(15, own, 3, 3, 3, 1, 0))
        // Down stays in the column; past the last row (or left of the first column) focus may leave the grid.
        assertEquals(2, GridSlots.stepFocus(15, own, 3, 1, 1, 0, 1))
        assertNull(GridSlots.stepFocus(15, own, 3, 2, 2, 0, 1))
        assertNull(GridSlots.stepFocus(15, own, 3, 0, 0, -1, 0))
        // Past the last cell: nothing further, focus stays.
        assertEquals(-1, GridSlots.stepFocus(15, own, 3, 13, 13, 1, 0))
        // Down through the widget's own cells, out below it.
        assertEquals(5, GridSlots.stepFocus(15, own, 3, 3, 3, 0, 1))
    }
}
