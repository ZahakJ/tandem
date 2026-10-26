package io.github.zahakj.tandem

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

/** The game screenshot's display lookup and file name (GameShot.kt). */
class GameShotTest {
    @Test fun displaysComePrimaryFirst() {
        val dump = """
            Display 4630946482288158084 (HWC display 3): port=132 pnpId=QCM displayName=""
            Display 4630946441858561667 (HWC display 0): port=131 pnpId=QCM displayName=""
        """.trimIndent()
        assertEquals(listOf("4630946441858561667", "4630946482288158084"), GameShot.displays(dump))
        assertEquals(emptyList<String>(), GameShot.displays(null))
    }

    @Test fun fileNamesAreSafe() {
        val at = LocalDateTime.of(2026, 10, 25, 21, 5, 9)
        assertEquals("Mario Odyssey 2026-10-25 21.05.09.png", GameShot.fileName("Mario: Odyssey", at))
        assertEquals("Screenshot 2026-10-25 21.05.09.png", GameShot.fileName(" /?* ", at))
    }
}
