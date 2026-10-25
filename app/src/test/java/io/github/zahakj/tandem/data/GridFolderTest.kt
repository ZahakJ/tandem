package io.github.zahakj.tandem.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Making folders on the home grid at the model level (IconGridStore, without files): an empty folder in an empty
 * spot, a folder made from a tile, and that both survive being saved and read back (a restart) and a change of rows.
 */
class GridFolderTest {
    private val e = GridSlots.EMPTY

    /** An arrangement from before slots (lanes 0), like an imported Cocoon home, shown in 3 rows. */
    private val packed = IconGridConfig(order = listOf("s:gb", "s:gba", "s:nds", "folder:apps"), folders = listOf(HomeFolder("apps", "Apps", listOf("app:x"))))
    private val present = listOf("s:gb", "s:gba", "s:nds", "folder:apps")

    @Before
    fun start() = IconGridStore.update { packed }

    private fun shown(lanes: Int = 3): GridShown {
        val c = IconGridStore.config
        val here = present + c.folders.map { "folder:${it.id}" }
        val slots = GridSlots.shown(c.order, c.lanes, lanes, here.distinct())
        return GridShown(slots, slots.filter { it.isNotEmpty() }.toHashSet(), lanes)
    }

    @Test
    fun anEmptyFolderGoesInTheEmptySpot() {
        val id = IconGridStore.createFolderAt("Racing", shown(), 7, 0)
        val c = IconGridStore.config
        val f = IconGridStore.folder(id)
        assertNotNull(f)
        f!!
        assertEquals("Racing", f.name)
        assertTrue(f.items.isEmpty())
        assertEquals(7, c.order.indexOf("folder:$id"))
        assertEquals(3, c.lanes)
        // Shown again it stays in that spot, empty, with everything else where it was.
        val again = GridSlots.shown(c.order, c.lanes, 3, present + "folder:$id")
        assertEquals(listOf("s:gb", "s:gba", "s:nds", "folder:apps", e, e, e, "folder:$id"), again)
    }

    @Test
    fun aFolderSurvivesARestart() {
        val id = IconGridStore.createFolderAt("Racing", shown(), 5, 0)
        val back = GridJson.decode(GridJson.encode(IconGridStore.config))
        assertEquals(IconGridStore.config, back)
        assertEquals(5, back.order.indexOf("folder:$id"))
        assertEquals(listOf<String>(), back.folders.first { it.id == id }.items)
    }

    @Test
    fun aFolderMadeFromATileTakesItsPlace() {
        val id = IconGridStore.createFolder("Handhelds", "s:gba", shown(), 1)
        val c = IconGridStore.config
        assertEquals(1, c.order.indexOf("folder:$id"))
        assertEquals(listOf("s:gba"), IconGridStore.folder(id)!!.items)
        assertTrue("s:gba" in IconGridStore.inFolders())
        assertTrue("s:gba" !in c.order)
    }

    @Test
    fun aFolderKeepsItsSpotWhenTheRowsChange() {
        val id = IconGridStore.createFolderAt("Racing", shown(), 7, 0)
        val c = IconGridStore.config
        // 3 rows, slot 7 is column 2, row 1; with 4 rows it is still column 2, row 1 (slot 9).
        val four = GridSlots.shown(c.order, c.lanes, 4, present + "folder:$id")
        assertEquals(9, four.indexOf("folder:$id"))
        assertEquals(4, four.indexOf("folder:apps"))
    }

    @Test
    fun pickedGamesFillTheFollowingFreeSpots() {
        val first = IconGridStore.addAllAt(listOf("game:a", "game:b", "game:c"), shown(), 2)
        assertEquals("game:a", first)
        // Slots 2 and 3 are taken: the games go in 4, 5 and 6, in the order they were picked.
        assertEquals(listOf("s:gb", "s:gba", "s:nds", "folder:apps", "game:a", "game:b", "game:c"), IconGridStore.config.order)
        assertTrue(IconGridStore.config.added.containsAll(listOf("game:a", "game:b", "game:c")))
        // Into a folder: after what is inside, as shortcuts: they stay on the home grid too.
        val apps = IconGridStore.folder("apps")!!
        val inside = GridShown(apps.items, apps.items.toHashSet(), 4)
        IconGridStore.addAllToFolder(listOf("game:c", "game:d"), "apps", inside, 0)
        assertEquals(listOf("app:x", "game:c", "game:d"), IconGridStore.folder("apps")!!.items)
        assertTrue("game:c" in IconGridStore.config.order && "game:c" in IconGridStore.config.added)
    }

    @Test
    fun aFolderNeverLandsUnderAWidget() {
        val s = shown()
        val withWidget = GridShown(s.slots, s.present + "widget:w", 3, mapOf("widget:w" to GridSlots.Extent(2, 2)))
        IconGridStore.addWidgetAt("widget:w", GridSpan(2, 2), GridSlots.Extent(2, 2), withWidget, 6)
        val c = IconGridStore.config
        assertEquals(6, c.order.indexOf("widget:w"))
        assertEquals(GridSpan(2, 2), c.spans["widget:w"])
        val now = GridShown(c.order, c.order.filter { it.isNotEmpty() }.toHashSet(), 3, mapOf("widget:w" to GridSlots.Extent(2, 2)))
        // The first free spot skips the widget's cells (6, 7, 9, 10).
        assertEquals(4, now.firstFree(4))
        assertEquals(8, now.firstFree(6))
        val id = IconGridStore.createFolder("Later", null, now, 0)
        assertEquals(4, IconGridStore.config.order.indexOf("folder:$id"))
        // Removing the widget forgets its size.
        IconGridStore.remove("widget:w", default = false)
        assertTrue("widget:w" !in IconGridStore.config.spans)
    }
}
