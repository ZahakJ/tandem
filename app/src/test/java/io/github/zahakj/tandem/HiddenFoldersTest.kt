package io.github.zahakj.tandem

import io.github.zahakj.tandem.data.HiddenFolder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val ROOT = "/storage/emulated/0/ROMs"

/** Which games hidden folders, systems and icon-grid folders hide (HiddenFolders.kt), without Android. */
class HiddenFoldersTest {

    /** A game as the rules see it: its system, top-level subfolder, file and key. */
    private data class G(val system: String, val group: String?, val file: String) {
        val key: String get() = "$system/${file.substringAfterLast('/')}"
        fun hiddenBy(rules: HideRules) = rules.hides(system, group, HiddenPaths.key(file, ROOT), key)
    }

    private val library = listOf(
        G("snes", null, "$ROOT/snes/Super Mario World.sfc"),
        G("snes", "RPG", "$ROOT/snes/RPG/Chrono Trigger.sfc"),
        G("snes", "RPG", "$ROOT/snes/RPG/Japan/Live A Live.sfc"),
        G("snes", "RPG", "$ROOT/snes/RPG/Japan/Imports/Tengai Makyou Zero.sfc"),
        G("snes", "Rpg Maker", "$ROOT/snes/Rpg Maker/RPG Tsukuru.sfc"),
        G("n3ds", "RPG", "$ROOT/3ds/RPG/Bravely Default.3ds"),
        G("gba", "RPG", "$ROOT/gba/RPG/Golden Sun.gba"),
        G("psp", "Private", "/storage/1234-ABCD/PSP Games/Private/Game.iso"),
    )

    /** What [visible] (Collections.visible) would show with these rules and hidden games tucked away. */
    private fun shown(rules: HideRules) = library.filterNot { it.hiddenBy(rules) }.map { it.file.substringAfterLast('/') }

    @Test fun pathsAreStoredRelativeToRomsAndComparedWithoutCase() {
        assertEquals("snes/RPG", HiddenPaths.stored("$ROOT/snes/RPG/", ROOT))
        assertEquals("snes/rpg", HiddenPaths.key("$ROOT/SNES/RPG", ROOT))
        assertEquals("", HiddenPaths.key(ROOT, ROOT))
        // Outside ROMs: the absolute path.
        assertEquals("/storage/1234-abcd/psp games/private", HiddenPaths.key("/storage/1234-ABCD/PSP Games/Private", ROOT))
        // A sibling whose name starts the same is not inside.
        assertEquals("/storage/emulated/0/romsextra/x", HiddenPaths.key("/storage/emulated/0/ROMsExtra/x", ROOT))
    }

    @Test fun coveredWalksUpEveryFolderButNotSiblingsWithTheSamePrefix() {
        val hidden = setOf("snes/rpg")
        assertTrue(HiddenPaths.covered("snes/rpg/chrono trigger.sfc", hidden))
        assertTrue(HiddenPaths.covered("snes/rpg/japan/imports/tengai makyou zero.sfc", hidden))
        assertFalse(HiddenPaths.covered("snes/rpg maker/rpg tsukuru.sfc", hidden))
        assertFalse(HiddenPaths.covered("snes/super mario world.sfc", hidden))
        assertFalse(HiddenPaths.covered("anything", emptySet()))
    }

    @Test fun hidingAFolderHidesEverythingInsideItAtAnyDepth() {
        val rules = HideRules(listOf(HiddenFolder("snes", "RPG", listOf("snes/RPG"))), emptySet())
        assertEquals(
            listOf("Super Mario World.sfc", "RPG Tsukuru.sfc", "Bravely Default.3ds", "Golden Sun.gba", "Game.iso"),
            shown(rules),
        )
        assertTrue(rules.folderHidden("snes", "rpg"))
        assertFalse(rules.folderHidden("gba", "RPG"))
        assertFalse(rules.systemHidden("snes"))
    }

    @Test fun aNestedFolderHidesOnlyItsOwnBranch() {
        // A folder inside a top-level folder (its name isn't a group, so only its path counts).
        val nested = HideRules(listOf(HiddenFolder("snes", "RPG/Japan", listOf("snes/RPG/Japan"))), emptySet())
        assertTrue(library[2].hiddenBy(nested))
        assertTrue(library[3].hiddenBy(nested))
        assertFalse(library[1].hiddenBy(nested))
        assertFalse(library[0].hiddenBy(nested))
    }

    @Test fun gamesCopiedInLaterAreHiddenToo() {
        val rules = HideRules(listOf(HiddenFolder("snes", "RPG", listOf("snes/RPG"))), emptySet())
        assertTrue(G("snes", "RPG", "$ROOT/snes/RPG/New Arrival.sfc").hiddenBy(rules))
        assertTrue(G("snes", "RPG", "$ROOT/snes/RPG/Brand/New/Folder/Deep.sfc").hiddenBy(rules))
    }

    @Test fun theFolderNameKeepsItHiddenUnderAnotherOfTheSystemsFolders() {
        // Paths only list snes/RPG, but a second folder for the same system (sfc/RPG) shows as the same group.
        val rules = HideRules(listOf(HiddenFolder("snes", "RPG", listOf("snes/RPG"))), emptySet())
        assertTrue(G("snes", "RPG", "$ROOT/sfc/RPG/Secret of Mana.sfc").hiddenBy(rules))
    }

    @Test fun foldersOutsideRomsUseTheirAbsolutePath() {
        val rules = HideRules(listOf(HiddenFolder("psp", "Elsewhere", listOf("/storage/1234-ABCD/PSP Games/Private"))), emptySet())
        assertEquals(listOf("Game.iso"), library.filter { it.hiddenBy(rules) }.map { it.file.substringAfterLast('/') })
    }

    @Test fun hidingASystemHidesAllOfItsGamesOnly() {
        val rules = HideRules(listOf(HiddenFolder("snes", null, listOf("snes"))), emptySet())
        assertEquals(listOf("Bravely Default.3ds", "Golden Sun.gba", "Game.iso"), shown(rules))
        assertTrue(rules.systemHidden("snes"))
        assertTrue(rules.hidesKey("snes/Anything.sfc"))
        assertFalse(rules.hidesKey("gba/Golden Sun.gba"))
    }

    @Test fun aHiddenIconGridFolderHidesTheSystemsAndGamesInsideIt() {
        val rules = HideRules(emptyList(), setOf("s:gba", "game:n3ds/Bravely Default.3ds", "app:com.example", "col:fav"))
        assertEquals(
            listOf("Super Mario World.sfc", "Chrono Trigger.sfc", "Live A Live.sfc", "Tengai Makyou Zero.sfc", "RPG Tsukuru.sfc", "Game.iso"),
            shown(rules),
        )
        assertTrue(rules.hidesKey("n3ds/Bravely Default.3ds"))
    }

    @Test fun nothingHiddenHidesNothing() {
        assertTrue(HideRules.None.isEmpty)
        assertEquals(library.size, shown(HideRules.None).size)
    }
}
