package io.github.zahakj.tandem.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Designed folder looks (FolderStyle.kt): how they are kept, read back and reset. */
class FolderStyleTest {
    private val designed = FolderStyle(
        icon = FolderStyle.ICON_GAME, iconRef = "game:snes/Zelda.sfc", color = 0xFF5B8DEF.toInt(), colorFromArt = true,
        hero = FolderStyle.HERO_SWEEP, logo = FolderStyle.LOGO_TEXT, textWeight = 900, textSize = 2,
        shape = FolderStyle.SHAPE_CIRCLE, frame = false,
    )

    @Test fun aStyleSurvivesTheRoundTrip() {
        val config = IconGridConfig(version = GridTree.SHORTCUTS, folders = listOf(HomeFolder("a1", "RPGs", listOf("game:snes/Zelda.sfc")))).withStyle("folder:a1", designed)
        val back = GridJson.decode(GridJson.encode(config))
        assertEquals(designed, back.styleOf("folder:a1"))
        assertEquals(config, back)
    }

    @Test fun onlyWhatChangedIsWritten() {
        val text = GridJson.encode(IconGridConfig().withStyle("s:gba", FolderStyle(shape = FolderStyle.SHAPE_ROUNDED)))
        assertTrue(text, "\"shape\":\"rounded\"" in text)
        assertFalse(text, "\"icon\"" in text)
    }

    /** icon-grid.json from before styles existed: folders keep working and wear the theme's look. */
    @Test fun oldConfigsHaveNoStylesAndFollowTheTheme() {
        val old = """{"order":["folder:x"],"folders":[{"id":"x","name":"Apps","items":["app:com.foo"],"color":2,"art":"android_app"}]}"""
        val c = GridJson.decode(old)
        assertEquals(1, c.folders.size)
        assertTrue(c.styles.isEmpty())
        assertEquals(FolderStyle.DEFAULT, c.styleOf("folder:x"))
        assertTrue(c.styleOf("folder:x").isDefault)
    }

    @Test fun unknownFieldsAndBrokenFilesAreHarmless() {
        val future = """{"styles":{"genres":{"icon":"emoji","emoji":"🎲","sparkle":true}}}"""
        assertEquals("🎲", GridJson.decode(future).styleOf("genres").emoji)
        assertEquals(IconGridConfig(version = GridTree.SHORTCUTS), GridJson.decode("{not json"))
    }

    @Test fun resetLeavesNoTrace() {
        val c = IconGridConfig().withStyle("col:fav", designed)
        assertFalse(c.styles.isEmpty())
        assertTrue(c.resetStyle("col:fav").styles.isEmpty())
        // Saving the default look is a reset too.
        assertTrue(c.withStyle("col:fav", FolderStyle()).styles.isEmpty())
    }

    @Test fun aRenamedFolderKeepsItsLook() {
        val c = IconGridConfig(folders = listOf(HomeFolder("a1", "RPGs"))).withStyle("folder:a1", designed)
        val renamed = c.copy(folders = c.folders.map { it.copy(name = "Role-playing") })
        assertEquals(designed, renamed.styleOf("folder:a1"))
    }

    @Test fun deletedFoldersLoseTheirStyleButSystemsKeepTheirs() {
        val c = IconGridConfig(folders = listOf(HomeFolder("a1", "RPGs")))
            .withStyle("folder:a1", designed).withStyle("folder:gone", designed).withStyle("s:gba", designed)
        val pruned = c.pruneStyles()
        assertEquals(setOf("folder:a1", "s:gba"), pruned.styles.keys)
    }

    @Test fun theIconHashFollowsTheIcon() {
        assertEquals(designed.iconHash(), designed.copy(hero = FolderStyle.HERO_AUTO).iconHash())
        assertTrue(designed.iconHash() != designed.copy(iconRef = "game:snes/Mario.sfc").iconHash())
    }
}

/** The colour taken from a folder's art (ArtColor in FolderStyle.kt). */
class ArtColorTest {
    private fun hue(c: Int): Float = FloatArray(3).also { ArtColor.toHsv((c shr 16) and 0xff, (c shr 8) and 0xff, c and 0xff, it) }[0]

    @Test fun theMainHueWins() {
        val red = ArtColor.argb(220, 40, 40)
        val blue = ArtColor.argb(40, 60, 220)
        val px = IntArray(100) { if (it < 70) red else blue }
        val c = ArtColor.dominant(px)
        assertNotNull(c)
        val h = hue(c!!)
        assertTrue("hue $h", h < 15f || h > 345f)
    }

    @Test fun greysBlackWhiteAndSeeThroughDontCount() {
        val grey = ArtColor.argb(128, 128, 128)
        val white = ArtColor.argb(255, 255, 255)
        val black = ArtColor.argb(5, 5, 5)
        val clearGreen = 0x0000FF00
        val green = ArtColor.argb(40, 200, 60)
        val px = IntArray(200) { when (it % 10) { 0 -> green; 1, 2 -> grey; 3, 4 -> white; 5, 6 -> black; else -> clearGreen } }
        val h = hue(ArtColor.dominant(px)!!)
        assertTrue("hue $h", h in 100f..150f)
        assertNull(ArtColor.dominant(IntArray(50) { grey }))
        assertNull(ArtColor.dominant(IntArray(0)))
    }

    @Test fun theResultReadsUnderWhiteText() {
        val pale = ArtColor.argb(255, 235, 240)
        val c = ArtColor.dominant(IntArray(10) { ArtColor.argb(250, 170, 190) } + IntArray(5) { pale })
        assertNotNull(c)
        val hsv = FloatArray(3)
        ArtColor.toHsv((c!! shr 16) and 0xff, (c shr 8) and 0xff, c and 0xff, hsv)
        assertTrue("value ${hsv[2]}", hsv[2] <= 0.87f)
        assertTrue("saturation ${hsv[1]}", hsv[1] >= 0.37f)
    }

    @Test fun hsvRoundTrips() {
        for (c in listOf(ArtColor.argb(10, 200, 90), ArtColor.argb(200, 30, 180), ArtColor.argb(240, 220, 20))) {
            val hsv = FloatArray(3)
            ArtColor.toHsv((c shr 16) and 0xff, (c shr 8) and 0xff, c and 0xff, hsv)
            val back = ArtColor.fromHsv(hsv[0], hsv[1], hsv[2])
            for (shift in listOf(16, 8, 0)) assertTrue(kotlin.math.abs(((back shr shift) and 0xff) - ((c shr shift) and 0xff)) <= 1)
        }
    }
}
