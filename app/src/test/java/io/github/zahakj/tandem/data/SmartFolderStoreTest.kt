package io.github.zahakj.tandem.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Smart folders in icon-grid.json and IconGridStore: reading files from before rules existed, a rule saved and read
 * back, unknown values, and making, converting and freezing folders at the model level.
 */
class SmartFolderStoreTest {
    @Before
    fun start() = IconGridStore.update {
        IconGridConfig(
            order = listOf("s:gb", "folder:mine", "s:nds"),
            added = listOf("game:gb/a.gb"),
            folders = listOf(HomeFolder("mine", "Mine", listOf("game:gb/a.gb", "s:gba"))),
        )
    }

    private fun shown(lanes: Int = 3): GridShown {
        val c = IconGridStore.config
        val here = (listOf("s:gb", "s:nds") + c.folders.map { "folder:${it.id}" } + c.order.filter { it.isNotEmpty() && c.folders.none { f -> it in f.items } }).distinct()
        val slots = GridSlots.shown(c.order, c.lanes, lanes, here)
        return GridShown(slots, slots.filter { it.isNotEmpty() }.toHashSet(), lanes)
    }

    @Test
    fun oldFilesReadAsManualFolders() {
        val old = """{"order":["folder:f1"],"folders":[{"id":"f1","name":"RPGs","items":["game:snes/a.sfc"],"color":2}]}"""
        val f = GridJson.decode(old).folders.single()
        assertNull(f.rule)
        assertNull(f.openOn)
        assertEquals(listOf("game:snes/a.sfc"), f.items)
    }

    @Test
    fun aManualFolderWritesNoRule() {
        val text = GridJson.encode(IconGridConfig(folders = listOf(HomeFolder("f1", "RPGs"))))
        assertTrue("rule" !in text && "openOn" !in text)
    }

    @Test
    fun aRuleSurvivesSavingAndReading() {
        val rule = SmartRule(
            systems = listOf("n3ds"), genres = listOf("RPG"), pattern = "^Poke", regex = true, yearFrom = 1996, yearTo = 2016,
            favorites = true, played = SmartRule.PLAYED_NO, random = 10, emulator = "org.citra.emu", sort = SmartRule.SORT_RECENT,
        )
        val config = IconGridConfig(version = GridTree.SHORTCUTS, folders = listOf(HomeFolder("f1", "Pokémon", rule = rule, openOn = FolderScreens.OPEN_BOTTOM)))
        val back = GridJson.decode(GridJson.encode(config))
        assertEquals(config, back)
        assertEquals(rule, back.folders.single().rule)
        assertTrue(back.folders.single().smart)
    }

    @Test
    fun aDefaultRuleIsStillASmartFolder() {
        val back = GridJson.decode(GridJson.encode(IconGridConfig(folders = listOf(HomeFolder("f1", "All", rule = SmartRule())))))
        assertEquals(SmartRule(), back.folders.single().rule)
    }

    @Test
    fun unknownValuesDontLoseTheGrid() {
        // A file from a newer version: new fields in the rule, a sort and a played value this one doesn't know.
        val newer = """{"order":["folder:f1","s:gb"],"folders":[{"id":"f1","name":"Best","rule":{"systems":["gb"],"sort":"rating","played":"often","mood":"happy"}}]}"""
        val c = GridJson.decode(newer)
        assertEquals(listOf("folder:f1", "s:gb"), c.order)
        val r = c.folders.single().rule
        assertNotNull(r)
        assertEquals(listOf("gb"), r!!.systems)
        assertEquals("rating", r.sort)
    }

    @Test
    fun aNewSmartFolderGoesInTheFirstFreeSlot() {
        val rule = SmartRule(systems = listOf("gb"))
        IconGridStore.createSmartFolder("s1", "Game Boy", rule, null, shown(), 0)
        val c = IconGridStore.config
        assertEquals(rule, IconGridStore.folder("s1")?.rule)
        // Nothing on the grid moved to make room.
        assertTrue("folder:s1" in c.order)
        assertEquals(listOf("s:gb", "folder:mine", "s:nds"), c.order.filter { it.isNotEmpty() && it != "folder:s1" })
        assertTrue(IconGridStore.folder("s1")!!.items.isEmpty())
    }

    @Test
    fun makingAFolderSmartPutsWhatWasInsideBackOnTheGrid() {
        val before = shown()
        IconGridStore.setRule("mine", "Game Boy", SmartRule(systems = listOf("gb")), FolderScreens.OPEN_TOP, before)
        val c = IconGridStore.config
        val f = IconGridStore.folder("mine")!!
        assertEquals("Game Boy", f.name)
        assertEquals(FolderScreens.OPEN_TOP, f.openOn)
        assertTrue(f.items.isEmpty())
        // Both things that were inside are on the home grid now, after the folder.
        assertTrue(c.order.indexOf("game:gb/a.gb") > c.order.indexOf("folder:mine"))
        assertTrue(c.order.indexOf("s:gba") > c.order.indexOf("folder:mine"))
        assertTrue(IconGridStore.inFolders().isEmpty())
    }

    @Test
    fun editingRulesLeavesTheGridAlone() {
        IconGridStore.createSmartFolder("s1", "GB", SmartRule(systems = listOf("gb")), null, shown(), 0)
        val order = IconGridStore.config.order
        IconGridStore.setRule("s1", "GB unplayed", SmartRule(systems = listOf("gb"), played = SmartRule.PLAYED_NO), null, shown())
        assertEquals(order, IconGridStore.config.order)
        assertEquals(SmartRule.PLAYED_NO, IconGridStore.folder("s1")?.rule?.played)
    }

    @Test
    fun freezingKeepsEveryGameAsShortcuts() {
        IconGridStore.createSmartFolder("s1", "GB", SmartRule(systems = listOf("gb")), null, shown(), 0)
        // gb/a.gb sits in another folder already; folders hold shortcuts, so it is in both.
        val kept = IconGridStore.freezeFolder("s1", listOf("game:gb/a.gb", "game:gb/b.gb", "game:gb/c.gb"))
        val f = IconGridStore.folder("s1")!!
        assertEquals(3, kept)
        assertNull(f.rule)
        assertEquals(listOf("game:gb/a.gb", "game:gb/b.gb", "game:gb/c.gb"), f.items)
        assertEquals(listOf("game:gb/a.gb", "s:gba"), IconGridStore.folder("mine")!!.items)
        // Nothing new lands on the home grid.
        assertTrue("game:gb/b.gb" !in IconGridStore.config.added)
    }

    @Test
    fun nothingIsPutInASmartFolderByHand() {
        IconGridStore.createSmartFolder("s1", "GB", SmartRule(), null, shown(), 0)
        assertTrue(!IconGridStore.moveIntoFolder("s:nds", null, "s1"))
        assertTrue(!GridTree.canHoldFolder(IconGridStore.config, "s1"))
        assertTrue(IconGridStore.folder("s1")!!.items.isEmpty())
        assertTrue("s:nds" in IconGridStore.config.order)
        assertNull(IconGridStore.addAllToFolder(listOf("game:gb/z.gb"), "s1", shown(), 0))
        assertTrue(IconGridStore.folder("s1")!!.items.isEmpty())
    }

    @Test
    fun openOnIsKeptPerFolder() {
        IconGridStore.setOpenOn("mine", FolderScreens.OPEN_BOTTOM)
        assertEquals(FolderScreens.OPEN_BOTTOM, IconGridStore.folder("mine")?.openOn)
        IconGridStore.setOpenOn("mine", null)
        assertNull(IconGridStore.folder("mine")?.openOn)
    }
}
