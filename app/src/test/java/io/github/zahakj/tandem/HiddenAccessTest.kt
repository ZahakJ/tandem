package io.github.zahakj.tandem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** When Start › Hidden is offered (HiddenItems.kt), without Android. */
class HiddenAccessTest {

    @Test fun privateModeOffShowsTheRowWhenSomethingIsHidden() {
        assertTrue(HiddenAccess.startRow(privateMode = false, revealed = false, count = 1))
        assertTrue(HiddenAccess.startRow(privateMode = false, revealed = false, count = 40))
        // "Revealed" only means something in private mode; it changes nothing here.
        assertTrue(HiddenAccess.startRow(privateMode = false, revealed = true, count = 3))
    }

    @Test fun privateModeHidesTheRowUntilRevealed() {
        assertFalse(HiddenAccess.startRow(privateMode = true, revealed = false, count = 1))
        assertFalse(HiddenAccess.startRow(privateMode = true, revealed = false, count = 40))
        assertTrue(HiddenAccess.startRow(privateMode = true, revealed = true, count = 1))
    }

    @Test fun nothingHiddenMeansNoRow() {
        for (privateMode in listOf(false, true)) for (revealed in listOf(false, true)) {
            assertFalse(HiddenAccess.startRow(privateMode, revealed, count = 0))
        }
    }

    @Test fun peekKeysIgnoreTheFolderNamesCase() {
        assertEquals(Peeks.folderKey("snes", "RPG"), Peeks.folderKey("snes", "rpg"))
        // A whole system is not one of its folders.
        assertTrue(Peeks.folderKey("snes", null) != Peeks.folderKey("snes", "snes"))
        assertTrue(Peeks.None.isEmpty)
        assertFalse(Peeks(games = setOf("snes/Mario.sfc")).isEmpty)
    }
}
