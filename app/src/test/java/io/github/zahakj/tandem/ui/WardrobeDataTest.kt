package io.github.zahakj.tandem.ui

import androidx.compose.ui.graphics.Color
import io.github.zahakj.tandem.pass.AvatarPart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The generated wardrobe agrees with the parts' counts, and every colour it names exists. */
class WardrobeDataTest {
    private val shelves = mapOf(
        AvatarPart.Headwear to WardrobeData.hats, AvatarPart.Top to WardrobeData.tops, AvatarPart.Hair to WardrobeData.hair,
        AvatarPart.Eyes to WardrobeData.eyes, AvatarPart.Glasses to WardrobeData.glasses, AvatarPart.Earrings to WardrobeData.earrings,
        AvatarPart.FaceExtra to WardrobeData.extras, AvatarPart.Beard to WardrobeData.beards,
    )

    @Test fun everyChoiceHasItsDrawing() {
        for ((part, data) in shelves) {
            assertEquals(part.name, part.count, data.size)
            // Choice 0 is always "none" or one drawn by AvatarArt itself.
            assertEquals(part.name, null, data[0])
            for ((i, texts) in data.withIndex()) {
                if (texts == null) continue
                assertTrue("${part.name} $i", texts.size == 1 || texts.size == 6)
                for (t in texts) assertTrue("${part.name} $i", t.lines().any { it.startsWith("L ") })
            }
        }
        // The first choices drawn by hand keep their numbers: from here on everything is in the wardrobe.
        assertTrue(WardrobeData.hair.drop(14).all { it != null })
        assertTrue(WardrobeData.tops.drop(5).all { it != null })
    }

    @Test fun everyColourExists() {
        val k = WardrobeInk().apply {
            cloth = Color(0xFF63A8EE); hair = Color(0xFF4A3226); skin = Color(0xFFF1C09A); iris = Color(0xFF6B4226)
            ink = Color(0xFF2A1D2A); topColor = 5
        }
        val ref = Regex("""(?<=\s)k(\d+)(?=\s|$)""", RegexOption.MULTILINE)
        for (data in shelves.values) for (texts in data) for (t in texts.orEmpty()) for (m in ref.findAll(t)) {
            assertNotEquals(m.value, Color.Magenta, wardrobeColor(m.groupValues[1].toInt(), k))
        }
    }
}
