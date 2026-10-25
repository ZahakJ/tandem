package io.github.zahakj.tandem

import org.junit.Assert.assertEquals
import org.junit.Test

/** Which starting-window style the next cold start uses for a theme's backdrop colour (StartLook.kt). */
class StartLookTest {
    private val palette = StartLook.COLORS

    private fun pick(rgb: Long) = StartLook.nearest(rgb.toInt(), palette)

    @Test fun anExactColourPicksItsOwnStyle() {
        for (i in palette.indices) assertEquals(i, StartLook.nearest(palette[i], palette))
    }

    @Test fun builtInThemesLandOnTheirFamily() {
        assertEquals(0, pick(0xFF0F0E17)) // Pastel Night, the default
        assertEquals(1, pick(0xFF000000)) // OLED Black, High Contrast, Crimson Visor
        assertEquals(2, pick(0xFF071321)) // Ocean
        assertEquals(3, pick(0xFF120823)) // Sunset Racer
        assertEquals(5, pick(0xFF0C150E)) // Forest
        assertEquals(7, pick(0xFFF0E9DC)) // Paper
        assertEquals(8, pick(0xFFFBEBF5)) // Cotton Candy
        assertEquals(9, pick(0xFF93B40F)) // Handheld Green
    }

    @Test fun lightAndDarkNeverMix() {
        // A pale pack colour never opens on a dark window, nor a dark one on a light window.
        assertEquals(true, pick(0xFFE8E8E8) in 7..8)
        assertEquals(true, pick(0xFF202020) !in 7..9)
    }
}
