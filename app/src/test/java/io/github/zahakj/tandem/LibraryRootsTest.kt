package io.github.zahakj.tandem

import io.github.zahakj.tandem.LibraryRoots.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Extra game folders (LibraryRoots.kt): the picker's answers as paths, what may be added, and how roots merge. */
class LibraryRootsTest {
    private val internal = "/storage/emulated/0"
    private val roms = "$internal/ROMs"
    private val ext = LibraryRoots.EXTERNAL_STORAGE

    @Test fun aFolderOnInternalStorageResolvesUnderIt() {
        assertEquals("$internal/Games", LibraryRoots.pathOf(ext, "primary:Games", internal))
        assertEquals("$internal/Games/More", LibraryRoots.pathOf(ext, "primary:Games/More/", internal))
        assertEquals(internal, LibraryRoots.pathOf(ext, "primary:", internal))
    }

    @Test fun anSdCardResolvesUnderStorage() {
        assertEquals("/storage/1234-ABCD/Games", LibraryRoots.pathOf(ext, "1234-ABCD:Games", internal))
        assertEquals("/storage/1234-ABCD", LibraryRoots.pathOf(ext, "1234-ABCD:", internal))
    }

    @Test fun aDownloadsFolderOnTheDeviceIsItsRawPath() {
        assertEquals("/storage/emulated/0/Download/Games", LibraryRoots.pathOf(LibraryRoots.DOWNLOADS, "raw:/storage/emulated/0/Download/Games/", internal))
        // A download by id has no path to read.
        assertNull(LibraryRoots.pathOf(LibraryRoots.DOWNLOADS, "123", internal))
    }

    @Test fun otherProvidersAndOddIdsAreRefused() {
        assertNull(LibraryRoots.pathOf("com.google.android.apps.docs.storage", "folder:abc", internal))
        assertNull(LibraryRoots.pathOf(ext, "nocolon", internal))
        assertNull(LibraryRoots.pathOf(ext, ":Games", internal))
        assertNull(LibraryRoots.pathOf(ext, "weird volume:Games", internal))
        assertNull(LibraryRoots.pathOf(null, "primary:Games", internal))
        assertNull(LibraryRoots.pathOf(ext, null, internal))
    }

    @Test fun theRomsFolderAndItsInsidesAndParentsCantBeAdded() {
        assertEquals(Verdict.IsRoms, LibraryRoots.check(roms, roms, emptyList()))
        assertEquals(Verdict.IsRoms, LibraryRoots.check("$internal/roms/", roms, emptyList()))
        assertEquals(Verdict.InsideRoms, LibraryRoots.check("$roms/n3ds", roms, emptyList()))
        assertEquals(Verdict.AboveRoms, LibraryRoots.check(internal, roms, emptyList()))
        assertEquals(Verdict.Ok, LibraryRoots.check("$internal/ROMs2", roms, emptyList()))
    }

    @Test fun aRootAddedTwiceOrInsideAnotherIsAlready() {
        val have = listOf("/storage/1234-ABCD/Games")
        assertEquals(Verdict.Already, LibraryRoots.check("/storage/1234-ABCD/Games", roms, have))
        assertEquals(Verdict.Already, LibraryRoots.check("/storage/1234-ABCD/games/", roms, have))
        assertEquals(Verdict.Already, LibraryRoots.check("/storage/1234-ABCD/Games/gba", roms, have))
        // A folder above an added one is fine: the added one's games win by key, nothing is lost.
        assertEquals(Verdict.Ok, LibraryRoots.check("/storage/1234-ABCD", roms, have))
    }

    @Test fun labelsNameTheFolderAndTheCard() {
        assertEquals("Games", LibraryRoots.label("$internal/Games", internal))
        assertEquals("Games · SD card 1234-ABCD", LibraryRoots.label("/storage/1234-ABCD/Games/", internal))
        assertEquals("1234-ABCD · SD card 1234-ABCD", LibraryRoots.label("/storage/1234-ABCD", internal))
        assertNull(LibraryRoots.where("$internal/Games", internal))
        assertNull(LibraryRoots.where("/storage/emulated/0/Games", internal))
        assertEquals("SD card 1234-ABCD", LibraryRoots.where("/storage/1234-ABCD", internal))
        assertTrue(LibraryRoots.removable("/storage/1234-ABCD/Games", internal))
        assertFalse(LibraryRoots.removable("$internal/Games", internal))
    }

    @Test fun shortPathsDropTheStoragePrefix() {
        assertEquals("Games/n3ds", LibraryRoots.shortPath("$internal/Games/n3ds", internal))
        assertEquals("1234-ABCD/Games", LibraryRoots.shortPath("/storage/1234-ABCD/Games", internal))
        assertEquals("Internal storage", LibraryRoots.shortPath(internal, internal))
        assertEquals("/data/local/x", LibraryRoots.shortPath("/data/local/x", internal))
    }

    @Test fun gamesFromEveryRootMergeWithTheRomsFolderWinning() {
        val roms = listOf("n3ds/Zelda.3ds" to "roms", "gba/Metroid.gba" to "roms")
        val card = listOf("n3ds/Zelda.3ds" to "card", "n3ds/Pokemon.3ds" to "card")
        val second = listOf("n3ds/Pokemon.3ds" to "second", "nds/Mario.nds" to "second")
        val merged = LibraryRoots.merge(listOf(roms, card, second)) { it.first }
        assertEquals(
            listOf("n3ds/Zelda.3ds" to "roms", "gba/Metroid.gba" to "roms", "n3ds/Pokemon.3ds" to "card", "nds/Mario.nds" to "second"),
            merged,
        )
    }

    @Test fun anUnavailableRootSimplyContributesNothing() {
        // The SD card is out: its listing is empty, the other roots' games stay as they were.
        val merged = LibraryRoots.merge(listOf(listOf("a" to 1), emptyList(), listOf("b" to 2))) { it.first }
        assertEquals(listOf("a" to 1, "b" to 2), merged)
    }

    @Test fun artIsLookedUpInTheGamesOwnRootThenTheRomsFolder() {
        assertEquals(listOf("/storage/1234-ABCD/Games", roms), LibraryRoots.artOrder("/storage/1234-ABCD/Games", roms))
        assertEquals(listOf(roms), LibraryRoots.artOrder(null, roms))
        assertEquals(listOf(roms), LibraryRoots.artOrder("$roms/", roms))
    }
}
