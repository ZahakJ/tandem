package io.github.zahakj.tandem.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Home folders as shortcuts, nesting, deleting a folder, sorting inside one and moving many tiles at once
 * (GridTree, FolderSort, GridSlots.moveMany), on plain configs.
 */
class GridTreeTest {
    private val e = GridSlots.EMPTY

    private fun cfg(order: List<String> = emptyList(), vararg folders: HomeFolder, hidden: List<String> = emptyList(), added: List<String> = emptyList()) =
        IconGridConfig(order = order, hidden = hidden, added = added, folders = folders.toList(), lanes = 2, version = GridTree.SHORTCUTS)

    private fun home(c: IconGridConfig, lanes: Int = 2, ext: Map<String, GridSlots.Extent> = emptyMap()) =
        GridShown(c.order, c.order.filter { it.isNotEmpty() }.toHashSet(), lanes, ext)

    private fun folder(c: IconGridConfig, id: String) = GridTree.folder(c, id)!!

    // --- Shortcuts and migration ------------------------------------------------------------------------------------

    @Test
    fun anOldArrangementKeepsWhatWasInFoldersOffTheHomeGrid() {
        val old = IconGridConfig(
            order = listOf("s:gb", "folder:a", "game:x"),
            added = listOf("game:x", "game:y", "app:z"),
            folders = listOf(HomeFolder("a", "Stuff", listOf("s:gba", "game:y", "app:z"))),
        )
        val c = GridTree.migrate(old)
        assertEquals(GridTree.SHORTCUTS, c.version)
        // A system that was in a folder stays hidden from the home grid; added things are no longer added.
        assertTrue("s:gba" in c.hidden)
        assertFalse(GridTree.onHome(c, "s:gba", default = true))
        assertFalse(GridTree.onHome(c, "game:y", default = false))
        assertTrue(GridTree.onHome(c, "game:x", default = false))
        assertTrue(GridTree.onHome(c, "s:gb", default = true))
        assertEquals(listOf("s:gba", "game:y", "app:z"), c.folders[0].items)
        // Only once: a migrated config is left as it is.
        assertEquals(c, GridTree.migrate(c))
        // Read from a file it is migrated too, and survives a round trip.
        val read = GridJson.decode(GridJson.encode(old))
        assertEquals(c, read)
        assertEquals(read, GridJson.decode(GridJson.encode(read)))
    }

    @Test
    fun aTileCanSitInSeveralFoldersAndOnHome() {
        var c = cfg(listOf("s:gb", "game:x", "folder:a", "folder:b"), HomeFolder("a", "A"), HomeFolder("b", "B"), added = listOf("game:x"))
        c = GridTree.addInto(c, "game:x", "a")
        c = GridTree.addInto(c, "game:x", "b")
        assertEquals(listOf("game:x"), folder(c, "a").items)
        assertEquals(listOf("game:x"), folder(c, "b").items)
        assertTrue(GridTree.onHome(c, "game:x", false))
        assertEquals(1, c.order.indexOf("game:x"))
        // Moving it from folder a into folder c leaves it in b and on home.
        c = c.copy(folders = c.folders + HomeFolder("c", "C"), order = c.order + "folder:c")
        c = GridTree.moveInto(c, "game:x", "a", "c")!!
        assertEquals(emptyList<String>(), folder(c, "a").items)
        assertEquals(listOf("game:x"), folder(c, "b").items)
        assertEquals(listOf("game:x"), folder(c, "c").items)
        assertTrue(GridTree.onHome(c, "game:x", false))
        // Moving one from the home grid takes it off there (a default is remembered as hidden), folders untouched.
        c = GridTree.moveInto(c, "s:gb", null, "a")!!
        assertTrue("s:gb" in c.hidden)
        assertEquals(e, c.order[0])
        assertEquals(listOf("s:gb"), folder(c, "a").items)
    }

    @Test
    fun takingOutGoesUpALevel() {
        var c = cfg(listOf("folder:g"), HomeFolder("g", "Games", listOf("folder:r", "game:z")), HomeFolder("r", "RPGs", listOf("game:x")))
        // Out of RPGs: into Games, after RPGs.
        c = GridTree.takeOut(c, "game:x", "r", home(c), default = false)
        assertEquals(listOf("folder:r", "game:z", "game:x"), folder(c, "g").items)
        assertTrue(folder(c, "r").items.none { it.isNotEmpty() })
        // Out of Games: onto the home grid, after the folder.
        c = GridTree.takeOut(c, "game:z", "g", home(c), default = false)
        assertEquals(listOf("folder:g", "game:z"), c.order)
        assertTrue("game:z" in c.added)
        // A folder taken out of its folder sits on the home grid.
        c = GridTree.takeOut(c, "folder:r", "g", home(c), default = false)
        assertNull(GridTree.parentOf(c, "r"))
        assertTrue("folder:r" in c.order)
    }

    // --- Nesting ----------------------------------------------------------------------------------------------------

    @Test
    fun foldersNestTwoLevelsAndNeverInsideThemselves() {
        val c = cfg(
            listOf("folder:g", "folder:o", "folder:e"),
            HomeFolder("g", "Games", listOf("folder:r")), HomeFolder("r", "RPGs"), HomeFolder("o", "Other"), HomeFolder("e", "Empty"),
        )
        assertEquals(1, GridTree.depthOf(c, "g"))
        assertEquals(2, GridTree.depthOf(c, "r"))
        assertEquals(listOf("Games", "RPGs"), GridTree.trail(c, "r").map { it.name })
        // Into itself, or into a folder inside it: no.
        assertFalse(GridTree.canNest(c, "folder:g", "g"))
        assertFalse(GridTree.canNest(c, "folder:g", "r"))
        // Games holds a folder, so it can't go one level down.
        assertFalse(GridTree.canNest(c, "folder:g", "o"))
        // An empty folder can go into a top-level one, not into RPGs (that would be level 3).
        assertTrue(GridTree.canNest(c, "folder:e", "o"))
        assertFalse(GridTree.canNest(c, "folder:e", "r"))
        assertFalse(GridTree.canHoldFolder(c, "r"))
        assertTrue(GridTree.canHoldFolder(c, "g"))
        // A refused move changes nothing.
        assertNull(GridTree.moveInto(c, "folder:g", null, "r"))
        assertEquals(c, GridTree.addInto(c, "folder:g", "r"))
        // A folder moved in leaves the home grid: it has one place.
        val moved = GridTree.moveInto(c, "folder:e", null, "o")!!
        assertEquals(listOf("folder:e"), folder(moved, "o").items)
        assertTrue("folder:e" !in moved.order)
        assertEquals("o", GridTree.parentOf(moved, "e")?.id)
        // And moving it on takes it out of Other.
        val again = GridTree.moveInto(moved, "folder:e", "o", "g")!!
        assertTrue("folder:e" !in folder(again, "o").items)
        assertEquals("g", GridTree.parentOf(again, "e")?.id)
    }

    @Test
    fun hiddenFoldersHideWhatTheirSubfoldersHold() {
        val folders = listOf(HomeFolder("g", "Games", listOf("folder:r", "s:gb"), hidden = true), HomeFolder("r", "RPGs", listOf("game:x")))
        assertEquals(setOf("folder:r", "s:gb", "game:x"), GridTree.hiddenTiles(folders))
        assertEquals(emptySet<String>(), GridTree.hiddenTiles(folders, skip = setOf("g")))
    }

    @Test
    fun aNewFolderFromATileInsideAFolderGoesInThatFolder() {
        val c = cfg(listOf("folder:g"), HomeFolder("g", "Games", listOf("game:a", "game:b")), HomeFolder("r", "RPGs"))
        val shown = GridShown(listOf("game:a", "game:b"), setOf("game:a", "game:b"), 4)
        val n = GridTree.createFolder(c, "n", "New", 0, listOf("game:b"), "g", home(c), move = true, fromShown = shown)
        assertEquals(listOf("game:a", "folder:n"), folder(n, "g").items)
        assertEquals(listOf("game:b"), folder(n, "n").items)
        // From a folder two levels down, the new folder goes on the home grid instead.
        val deep = cfg(listOf("folder:g"), HomeFolder("g", "Games", listOf("folder:r")), HomeFolder("r", "RPGs", listOf("game:x")))
        val d = GridTree.createFolder(deep, "n", "New", 0, listOf("game:x"), "r", home(deep), move = true)
        assertNull(GridTree.parentOf(d, "n"))
        assertTrue("folder:n" in d.order)
        assertEquals(listOf("game:x"), folder(d, "n").items)
        // Several from the home grid: the folder takes the first one's slot; Also add leaves them where they are.
        val h = cfg(listOf("game:a", "game:b", "game:c"), added = listOf("game:a", "game:b", "game:c"))
        val m = GridTree.createFolder(h, "n", "New", 0, listOf("game:a", "game:c"), null, home(h), move = true)
        assertEquals(listOf("folder:n", "game:b"), m.order)
        assertEquals(listOf("game:a", "game:c"), folder(m, "n").items)
        val a = GridTree.createFolder(h, "n", "New", 0, listOf("game:a", "game:c"), null, home(h), move = false)
        assertEquals(listOf("game:a", "game:b", "game:c", "folder:n"), a.order)
    }

    // --- Deleting a folder ------------------------------------------------------------------------------------------

    @Test
    fun deletingAFolderCanKeepItsContents() {
        val c = cfg(
            listOf("s:gb", "folder:g", e, "game:y", e, e),
            HomeFolder("g", "Games", listOf("game:x", "folder:r", "game:y", "s:gba")), HomeFolder("r", "RPGs", listOf("game:z")),
            hidden = listOf("s:gba"), added = listOf("game:y"),
        ).copy(styles = mapOf("folder:g" to FolderStyle(shape = FolderStyle.SHAPE_CIRCLE)))
        val out = GridTree.deleteFolder(c, "g", home(c), keep = true, defaults = setOf("s:gb", "s:gba"))
        assertNull(GridTree.folder(out, "g"))
        // Into its place and the free slots after it; game:y was on home already and stays where it was.
        assertEquals(listOf("s:gb", "game:x", "folder:r", "game:y", "s:gba"), out.order)
        assertTrue("s:gba" !in out.hidden)
        assertTrue("game:x" in out.added)
        // The subfolder is on the home grid now, still holding its game.
        assertNull(GridTree.parentOf(out, "r"))
        assertEquals(listOf("game:z"), folder(out, "r").items)
        assertTrue("folder:g" !in out.styles)
    }

    @Test
    fun deletingAFolderWithItsSubfoldersLeavesGamesAlone() {
        val c = cfg(
            listOf("folder:g", "game:x"),
            HomeFolder("g", "Games", listOf("game:x", "folder:r")), HomeFolder("r", "RPGs", listOf("game:z")), HomeFolder("o", "Other", listOf("game:z")),
            added = listOf("game:x"),
        )
        val out = GridTree.deleteFolder(c, "g", home(c), keep = false)
        assertEquals(listOf("o"), out.folders.map { it.id })
        assertEquals(listOf(e, "game:x"), out.order)
        assertTrue("game:x" in out.added)
        assertEquals(listOf("game:z"), folder(out, "o").items)
        // Inside a folder, keeping the contents moves them into the parent.
        val nested = cfg(listOf("folder:g"), HomeFolder("g", "Games", listOf("folder:r", "game:a")), HomeFolder("r", "RPGs", listOf("game:z", "game:a")))
        val up = GridTree.deleteFolder(nested, "r", home(nested), keep = true)
        assertEquals(listOf("game:z", "game:a"), folder(up, "g").items)
    }

    // --- Sorting inside a folder ------------------------------------------------------------------------------------

    @Test
    fun aFolderSortsLiveAndKeepsItsOwnOrderUnderneath() {
        val facts = mapOf(
            "game:b" to FolderSort.Facts("Banjo", lastPlayed = 300, played = 10, added = 1, system = "N64"),
            "game:a" to FolderSort.Facts("Azure", lastPlayed = 100, played = 99, added = 3, system = "SNES"),
            "game:c" to FolderSort.Facts("Castle", lastPlayed = 200, played = 50, added = 2, system = "GBA"),
            "folder:z" to FolderSort.Facts("Zzz", folder = true),
        )
        val manual = listOf("game:b", e, "game:a", "folder:z", "game:c", "game:gone")
        fun sort(s: String) = FolderSort.apply(manual, s) { facts[it] }
        assertEquals(listOf("game:b", "game:a", "folder:z", "game:c", "game:gone"), sort(FolderSort.MANUAL))
        assertEquals(listOf("folder:z", "game:a", "game:b", "game:c", "game:gone"), sort(FolderSort.NAME))
        assertEquals(listOf("folder:z", "game:b", "game:c", "game:a", "game:gone"), sort(FolderSort.RECENT))
        assertEquals(listOf("folder:z", "game:a", "game:c", "game:b", "game:gone"), sort(FolderSort.MOST))
        assertEquals(listOf("folder:z", "game:a", "game:c", "game:b", "game:gone"), sort(FolderSort.ADDED))
        assertEquals(listOf("folder:z", "game:c", "game:b", "game:a", "game:gone"), sort(FolderSort.SYSTEM))
        // Something added to a sorted folder joins the end of its own order.
        val c = cfg(listOf("folder:f"), HomeFolder("f", "F", manual, sort = FolderSort.NAME))
        val shown = GridShown(listOf("game:a"), setOf("game:a"), 4)
        val added = GridTree.addInto(c, "game:new", "f", 0, shown)
        assertEquals(manual + "game:new", folder(added, "f").items)
        assertEquals(FolderSort.NAME, folder(added, "f").sort)
    }

    // --- Several at once --------------------------------------------------------------------------------------------

    @Test
    fun manyTilesMoveToASpotInTheirOrder() {
        // 2 lanes: slots 0 1 / 2 3 / 4 5 ...
        val slots = listOf("a", "b", "c", "d", "e", "f")
        // In reading order (a, c, e), whatever order they were marked in; f stays put, so c and e go after it.
        assertEquals(listOf(e, "b", e, "d", "a", "f", "c", "e"), GridSlots.moveMany(slots, 2, emptyMap(), listOf("e", "c", "a"), 4))
        // "a" can't go in 1 (b) or 2 (c), so 3, which "d" freed; then d in the next free slot.
        assertEquals(listOf(e, "b", "c", "a", "e", "f", "d"), GridSlots.moveMany(slots, 2, emptyMap(), listOf("a", "d"), 1))
    }

    @Test
    fun manyTilesMoveAroundAWidget() {
        // A 2×2 widget in slots 2, 3, 4, 5 of a 2-lane grid.
        val ext = mapOf("w" to GridSlots.Extent(2, 2))
        val slots = listOf("a", "b", "w", e, e, e, "c")
        // a and b go after the widget, skipping the cells it covers.
        assertEquals(listOf(e, e, "w", e, e, e, "c", "a", "b"), GridSlots.moveMany(slots, 2, ext, listOf("a", "b"), 2))
        // The widget moves whole to the first place it fits.
        val moved = GridSlots.moveMany(slots, 2, ext, listOf("w"), 7)
        assertEquals(8, moved.indexOf("w"))
        assertEquals(listOf("a", "b"), moved.take(2))
        // Through the store, the home grid saves where they went.
        val c = cfg(slots, added = listOf("a", "b", "c", "w"))
        val h = GridShown(slots, setOf("a", "b", "c", "w"), 2, ext)
        assertEquals(listOf(e, e, "w", e, e, e, "c", "a", "b"), GridTree.moveManyHome(c, listOf("b", "a"), h, 2).order)
    }

    @Test
    fun manyIntoAFolderSkipWhatCantGo() {
        val c = cfg(listOf("folder:g", "folder:r", "game:a"), HomeFolder("g", "G", listOf("folder:x")), HomeFolder("x", "X"), HomeFolder("r", "R"), added = listOf("game:a"))
        // g can't go into r (it holds a folder), game:a and r itself can't go into r; game:a goes.
        val out = GridTree.intoFolder(c, listOf("folder:g", "folder:r", "game:a"), null, "r", move = true)
        assertEquals(listOf("game:a"), folder(out, "r").items)
        assertTrue("folder:g" in out.order)
        assertFalse(GridTree.onHome(out, "game:a", false))
        val kept = GridTree.intoFolder(c, listOf("game:a"), null, "x", move = false)
        assertTrue(GridTree.onHome(kept, "game:a", false))
        assertNotNull(folder(kept, "x").items.firstOrNull { it == "game:a" })
        // Removing many off home leaves folders alone.
        val removed = GridTree.removeMany(c, listOf("game:a", "folder:g"), null)
        assertFalse(GridTree.onHome(removed, "game:a", false))
        assertTrue("folder:g" in removed.order)
    }
}
