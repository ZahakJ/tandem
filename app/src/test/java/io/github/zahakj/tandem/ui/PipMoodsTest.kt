package io.github.zahakj.tandem.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pip's face beside a tile's rows (PipMoods.kt): worried only over a row that can't be taken back. */
class PipMoodsTest {
    /** Every row the edit screen can show, by section, and whether it takes something away for good. */
    private val look = listOf("rename", "art", "colour", "hero", "logo", "icon", "resize", "shape", "corners", "frame", "resetlook")
    private val options = listOf(
        "status", "options", "rules", "smart", "freeze", "sortin", "viewin", "arrange", "unarrange", "act:0",
        "screen", "other", "game", "dock", "hide", "info",
    )
    private val organizeSafe = listOf("folder", "also", "out", "home", "sortfolder", "hidefolder")
    private val organizeFinal = listOf("remove", "delete", "uninstall")

    @Test fun colourIsNotWorrying() {
        // Colour is drawn in Peach, the colour of Delete: the colour never decides.
        assertEquals(PipMoods.EXCITED, PipMoods.editor("colour", EditSection.Look))
        assertEquals(PipMoods.HAPPY, PipMoods.lift("colour", deeper = false))
    }

    @Test fun everyLookRowIsExcitedNeverWorried() {
        for (id in look) assertEquals(id, PipMoods.EXCITED, PipMoods.editor(id, EditSection.Look))
    }

    @Test fun optionsAreHappy() {
        for (id in options) assertEquals(id, PipMoods.HAPPY, PipMoods.editor(id, EditSection.Options))
    }

    @Test fun organizeWorriesOnlyOverWhatCantBeUndone() {
        for (id in organizeSafe) assertEquals(id, PipMoods.HAPPY, PipMoods.editor(id, EditSection.Organize))
        for (id in organizeFinal) assertEquals(id, PipMoods.WORRIED, PipMoods.editor(id, EditSection.Organize))
    }

    @Test fun aSectionThatTookALoneRowStillWorriesOverIt() {
        // A widget's Remove joins its Options; Delete in Look (should it ever land there) is still worrying.
        assertEquals(PipMoods.WORRIED, PipMoods.editor("remove", EditSection.Options))
        assertEquals(PipMoods.WORRIED, PipMoods.editor("delete", EditSection.Look))
    }

    @Test fun theLiftWorriesOverClosingAGameAndDeletingEverything() {
        assertEquals(PipMoods.WORRIED, PipMoods.lift("closegame", deeper = false))
        // Delete folder › Delete everything inside, a page of the card.
        assertEquals(PipMoods.WORRIED, PipMoods.lift("all", deeper = true))
        assertEquals(PipMoods.HAPPY, PipMoods.lift("open", deeper = false))
        // Inside a group or a page, anything else is excitement, Back and the choices too.
        for (id in listOf("back", "ch:0", "keep", "folder", "smart")) assertEquals(id, PipMoods.EXCITED, PipMoods.lift(id, deeper = true))
    }

    @Test fun peachRowsThatAreNotFinal() {
        // Favourite, an empty spot's New › Collection, the performance rows, a game's "Is a game": all Peach, none final.
        for (id in listOf("fav", "collection", "perfmode", "perffan", "perfhz", "game", "resetlook")) assertFalse(id, PipMoods.worried(id))
        assertTrue(PipMoods.worried("reset"))
    }

    @Test fun nothingHighlightedIsNeverWorried() {
        assertEquals(PipMoods.HAPPY, PipMoods.lift(null, deeper = false))
        assertEquals(PipMoods.HAPPY, PipMoods.editor(null, EditSection.Organize))
    }
}
