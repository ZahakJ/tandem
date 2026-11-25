package io.github.zahakj.tandem.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Where each of a tile's old Y menu actions went (EditSections.kt): every one has a place, none is lost. */
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
        assertEquals(EditSection.Name, EditSections.of("rename"))
        assertEquals(EditSection.Look, EditSections.of("icon"))
        assertEquals(EditSection.Look, EditSections.of("resize"))
        assertEquals(EditSection.Contents, EditSections.of("rules"))
        assertEquals(EditSection.Contents, EditSections.of("sortin"))
        assertEquals(EditSection.Contents, EditSections.of("viewin"))
        assertEquals(EditSection.Contents, EditSections.of("act:3"))
        assertEquals(EditSection.Behaviour, EditSections.of("screen"))
        assertEquals(EditSection.Organize, EditSections.of("folder"))
        assertEquals(EditSection.Organize, EditSections.of("also"))
        assertEquals(EditSection.Organize, EditSections.of("out"))
        assertEquals(EditSection.Remove, EditSections.of("delete"))
        assertEquals(EditSection.Remove, EditSections.of("hidefolder"))
        assertEquals(EditSection.Remove, EditSections.of("uninstall"))
    }

    @Test fun anActionNobodyKnowsYetStillShowsSomewhere() {
        assertEquals(EditSection.Behaviour, EditSections.of("something-new"))
    }
}
