package io.github.zahakj.tandem.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** When Compose's accessibility tree is kept up to date (QuietSemantics.kt). */
class QuietSemanticsTest {
    /** The Thor: the AYN Game Assistant (no capabilities) and Tandem's key filter (8). */
    @Test fun servicesThatCantReadTheScreenDontNeedIt() {
        assertFalse(QuietSemantics.needed(touchExploration = false, capabilities = listOf(0, 8)))
        assertFalse(QuietSemantics.needed(touchExploration = false, capabilities = emptyList()))
    }

    @Test fun aScreenReaderOrTouchExplorationNeedsIt() {
        assertTrue(QuietSemantics.needed(touchExploration = false, capabilities = listOf(0, 8, 1 or 2)))
        assertTrue(QuietSemantics.needed(touchExploration = true, capabilities = listOf(0)))
    }
}
