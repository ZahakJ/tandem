package io.github.zahakj.tandem.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** What button prompts say (Controls.kt): Start and Select by name, except in the Nintendo style. */
class PromptsTest {
    @Test fun startAndSelectAreNamedOutsideTheNintendoStyle() {
        for (style in listOf("tandem", "xbox", "playstation", "something new")) {
            assertEquals("Select", Prompts.text("Select", style))
            assertEquals("Start", Prompts.text("Start", style))
        }
    }

    @Test fun theNintendoStyleShowsPlusAndMinus() {
        assertEquals("−", Prompts.text("Select", "nintendo"))
        assertEquals("+", Prompts.text("Start", "nintendo"))
    }

    @Test fun otherButtonsKeepTheirLetters() {
        for (b in listOf("A", "B", "X", "Y", "L1", "R1", "L", "R")) for (style in Prompts.styles.map { it.first }) {
            assertEquals(b, Prompts.text(b, style))
        }
    }
}
