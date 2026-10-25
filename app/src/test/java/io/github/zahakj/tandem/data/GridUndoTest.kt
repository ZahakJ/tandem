package io.github.zahakj.tandem.data

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The icon grid's Undo (IconGridStore): how many steps it keeps, that they survive a restart, and what comes back. */
class GridUndoTest {
    @get:Rule val tmp = TemporaryFolder()

    @Before
    fun start() {
        IconGridStore.useDir(tmp.root)
        WidgetStore.update { WidgetConfig(widgets = emptyList()) }
    }

    @After
    fun end() = IconGridStore.useDir(null)

    private fun place(order: List<String>) = IconGridStore.update { it.copy(order = order, added = order.filter { k -> k.isNotEmpty() }, lanes = 2) }

    @Test
    fun aStepThatChangesNothingIsNotKept() {
        place(listOf("game:a"))
        assertFalse(IconGridStore.undoable("Nothing") { })
        assertFalse(IconGridStore.canUndo)
        assertNull(IconGridStore.undo())
    }

    @Test
    fun undoTakesBackTheLastStepFirst() {
        place(listOf("game:a", "game:b"))
        val start = IconGridStore.config
        assertTrue(IconGridStore.undoable("Removed a") { IconGridStore.removeFromHome("game:a") })
        val afterOne = IconGridStore.config
        assertTrue(IconGridStore.undoable("Removed b") { IconGridStore.removeFromHome("game:b") })
        assertEquals("Removed b", IconGridStore.undoLabel)
        assertEquals("Removed b", IconGridStore.undo()?.label)
        assertEquals(afterOne, IconGridStore.config)
        assertEquals("Removed a", IconGridStore.undoLabel)
        IconGridStore.undo()
        assertEquals(start, IconGridStore.config)
        assertFalse(IconGridStore.canUndo)
    }

    @Test
    fun itKeepsTheLastTwentySteps() {
        place(listOf("game:0"))
        for (i in 1..25) IconGridStore.undoable("Step $i") { IconGridStore.setSlots(listOf("game:$i"), 2) }
        var n = 0
        while (IconGridStore.undo() != null) n++
        assertEquals(IconGridStore.UNDO_MAX, n)
        // The oldest five are gone: the grid is back to how it was after step 5.
        assertEquals(listOf("game:5"), IconGridStore.config.order)
    }

    @Test
    fun stepsSurviveARestart() {
        place(listOf("game:a", "game:b"))
        val before = IconGridStore.config
        IconGridStore.undoable("Moved 2 tiles") { IconGridStore.moveMany(listOf("game:a", "game:b"), null, GridShown(before.order, before.order.toHashSet(), 2), 4) }
        val moved = IconGridStore.config
        IconGridStore.flush()
        // A new start reads both files again.
        IconGridStore.useDir(tmp.root)
        assertEquals(moved, IconGridStore.config)
        assertEquals("Moved 2 tiles", IconGridStore.undoLabel)
        IconGridStore.undo()
        assertEquals(before.order, IconGridStore.config.order)
        IconGridStore.flush()
        // Nothing left to undo after another restart.
        IconGridStore.useDir(tmp.root)
        assertFalse(IconGridStore.canUndo)
        assertEquals(before.order, IconGridStore.config.order)
    }

    @Test
    fun undoBringsBackAWidgetWithItsSizeAndOptions() {
        val w = Widget("clock-1", "clock", WidgetSize.Wide, WidgetPlace.Grid, mapOf("style" to "big"))
        WidgetStore.updateWidgets { listOf(w) }
        val shown = GridShown(listOf("game:a"), setOf("game:a"), 2)
        IconGridStore.update { it.copy(order = listOf("game:a"), added = listOf("game:a"), lanes = 2) }
        IconGridStore.addWidgetAt("widget:clock-1", GridSpan(2, 2), GridSlots.Extent(2, 2), shown, 2)
        val placed = IconGridStore.config
        // Resized, then removed: two steps.
        IconGridStore.undoable("Resized") {
            IconGridStore.setSpan("widget:clock-1", GridSpan(2, 1), placed.order, 2)
            WidgetStore.updateWidget("clock-1") { it.copy(size = WidgetSize.Medium) }
        }
        IconGridStore.undoable("Removed clock") {
            IconGridStore.remove("widget:clock-1", default = false)
            WidgetStore.updateWidgets { list -> list.filter { it.id != "clock-1" } }
        }
        assertTrue(WidgetStore.widgets.isEmpty())
        assertTrue("widget:clock-1" !in IconGridStore.config.spans)
        IconGridStore.undo()
        assertEquals(GridSpan(2, 1), IconGridStore.config.spans["widget:clock-1"])
        assertEquals(listOf(w.copy(size = WidgetSize.Medium)), WidgetStore.widgets)
        IconGridStore.undo()
        assertEquals(placed, IconGridStore.config)
        assertEquals(GridSpan(2, 2), IconGridStore.config.spans["widget:clock-1"])
        assertEquals(listOf(w), WidgetStore.widgets)
    }

    @Test
    fun undoBringsBackADeletedFolderTree() {
        IconGridStore.update {
            it.copy(
                order = listOf("folder:g"), lanes = 2,
                folders = listOf(HomeFolder("g", "Games", listOf("folder:r", "game:a")), HomeFolder("r", "RPGs", listOf("game:z"))),
            )
        }
        val before = IconGridStore.config
        val home = GridShown(before.order, before.order.toHashSet(), 2)
        IconGridStore.undoable("Deleted Games") { IconGridStore.deleteFolder("g", home, keep = false) }
        assertTrue(IconGridStore.config.folders.isEmpty())
        IconGridStore.undo()
        assertEquals(before, IconGridStore.config)
    }
}
