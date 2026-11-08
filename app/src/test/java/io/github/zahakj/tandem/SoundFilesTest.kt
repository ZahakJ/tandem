package io.github.zahakj.tandem

import io.github.zahakj.tandem.audio.Sfx
import io.github.zahakj.tandem.ui.SoundFiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Recorded UI sounds (SoundFiles.kt): which file in a Cocoon-style sound pack plays for which of Tandem's sounds. */
class SoundFilesTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun cocoonNamesMapToTandemSounds() {
        val dir = tmp.newFolder("sounds")
        for (n in listOf("navigate.ogg", "select.mp3", "back.ogg", "launch.mp3", "grid_zoom_in.ogg", "readme.txt")) java.io.File(dir, n).writeText("x")
        val m = SoundFiles.map(dir)
        assertEquals("navigate.ogg", m[Sfx.Move.ordinal]?.name)
        assertEquals("select.mp3", m[Sfx.Confirm.ordinal]?.name)
        assertEquals("back.ogg", m[Sfx.MenuClose.ordinal]?.name)
        assertEquals("launch.mp3", m[Sfx.Launch.ordinal]?.name)
        assertEquals("grid_zoom_in.ogg", m[Sfx.Page.ordinal]?.name)
        // Not in the pack: the synthesized sound plays.
        assertNull(m[Sfx.Error.ordinal])
        assertNull(m[Sfx.Favorite.ordinal])
    }
}
