package io.github.zahakj.tandem.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Where each of a tile's rows goes on the edit screen (EditSections.kt): every one has a place, none is alone. */
class EditSectionsTest {
    /** Every action id the tile, app, widget and smart folder menus had before the lift and the edit screen. */
    private val oldMenu = listOf(
        // Tiles (tileMenuSpec) and their More…
        "open", "closegame", "status", "move", "select", "folder", "also", "out", "options", "design", "rename", "sortin", "viewin",
        "delete", "hidefolder", "arrange", "unarrange", "remove", "add", "sortfolder", "tidy", "undo", "search", "swap",
        // Smart folders (SmartFolders.extendMenu)
        "rules", "screen", "freeze", "smart",
        // Apps (appMenuActions)
        "other", "home", "dock", "icon", "game", "hide", "info", "uninstall",
        // Widgets
        "resize", "act:0",
    )

    /** The Look rows the edit screen builds itself, for a designable tile (TileEditor.lookRows). */
    private val designLook = listOf("art", "colour", "hero", "logo", "shape", "corners")

    private fun arrange(vararg ids: String) = EditSections.arrange(ids.toList()) { it }

    @Test fun everyOldActionHasAPlace() {
        for (id in oldMenu) {
            val s = EditSections.of(id)
            assertTrue("$id is nowhere", s != null || id in EditSections.elsewhere)
        }
    }

    @Test fun moveAndSelectLeaveTheMenus() {
        assertNull(EditSections.of("move"))
        assertNull(EditSections.of("select"))
    }

    @Test fun theLiftKeepsOpenAndCloseGame() {
        assertNull(EditSections.of("open"))
        assertNull(EditSections.of("closegame"))
    }

    @Test fun eachActionGoesWhereItMakesSense() {
        assertEquals(EditSection.Look, EditSections.of("rename"))
        assertEquals(EditSection.Look, EditSections.of("icon"))
        assertEquals(EditSection.Look, EditSections.of("resize"))
        for (id in designLook) assertEquals(id, EditSection.Look, EditSections.of(id))
        assertEquals(EditSection.Options, EditSections.of("rules"))
        assertEquals(EditSection.Options, EditSections.of("sortin"))
        assertEquals(EditSection.Options, EditSections.of("viewin"))
        assertEquals(EditSection.Options, EditSections.of("act:3"))
        assertEquals(EditSection.Options, EditSections.of("screen"))
        assertEquals(EditSection.Options, EditSections.of("status"))
        assertEquals(EditSection.Organize, EditSections.of("folder"))
        assertEquals(EditSection.Organize, EditSections.of("also"))
        assertEquals(EditSection.Organize, EditSections.of("out"))
        assertEquals(EditSection.Organize, EditSections.of("delete"))
        assertEquals(EditSection.Organize, EditSections.of("hidefolder"))
        assertEquals(EditSection.Organize, EditSections.of("uninstall"))
    }

    @Test fun anActionNobodyKnowsYetStillShowsSomewhere() {
        assertEquals(EditSection.Options, EditSections.of("something-new"))
    }

    @Test fun aFolderHasThreeFullSections() {
        // A home folder: its Look rows, then tileMenuSpec's and the smart folder rows, in menu order.
        val s = arrange(*(designLook + listOf("folder", "rename", "sortin", "viewin", "delete", "hidefolder", "screen")).toTypedArray())
        assertEquals(listOf(EditSection.Look, EditSection.Options, EditSection.Organize), s.keys.toList())
        assertEquals("rename", s.getValue(EditSection.Look).first())
        assertEquals(listOf("sortin", "viewin", "screen"), s.getValue(EditSection.Options))
        // Where it lives first, then hiding, then deleting.
        assertEquals(listOf("folder", "hidefolder", "delete"), s.getValue(EditSection.Organize))
        assertEquals(EditSection.Look, EditSections.opening(s))
    }

    @Test fun anAppKeepsItsNameAndIconInLook() {
        val s = arrange("other", "screen", "dock", "rename", "icon", "game", "hide", "info", "uninstall", "folder", "also", "remove", "shape", "corners")
        assertEquals(listOf("rename", "icon", "shape", "corners"), s.getValue(EditSection.Look))
        assertEquals(listOf("screen", "other", "game", "dock", "hide", "info"), s.getValue(EditSection.Options))
        assertEquals(listOf("folder", "also", "remove", "uninstall"), s.getValue(EditSection.Organize))
        assertEquals(EditSection.Look, EditSections.opening(s))
    }

    @Test fun aGameOpensOnItsOptions() {
        val s = arrange("status", "folder", "also", "options", "remove", "shape", "corners")
        assertEquals(listOf(EditSection.Look, EditSection.Options, EditSection.Organize), s.keys.toList())
        assertEquals(EditSection.Options, EditSections.opening(s))
    }

    @Test fun aWidgetIsOneList() {
        // Its size and Remove would each be alone: they join its options.
        val s = arrange("options", "resize", "act:0", "act:1", "remove")
        assertEquals(listOf(EditSection.Options), s.keys.toList())
        assertEquals(listOf("resize", "options", "act:0", "act:1", "remove"), s.getValue(EditSection.Options))
    }

    @Test fun noSectionIsLeftWithOneRowWhileOthersHaveMore() {
        val s = arrange("rename", "sortin", "viewin", "folder", "delete")
        assertTrue(s.values.all { it.size > 1 })
        assertEquals(5, s.values.sumOf { it.size })
    }

    @Test fun aLoneOptionJoinsItsNeighbour() {
        val s = arrange("rename", "shape", "corners", "screen", "folder", "delete")
        assertEquals(listOf(EditSection.Look, EditSection.Organize), s.keys.toList())
        assertEquals(listOf("rename", "shape", "corners", "screen"), s.getValue(EditSection.Look))
    }
}
