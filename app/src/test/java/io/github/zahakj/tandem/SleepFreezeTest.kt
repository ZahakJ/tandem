package io.github.zahakj.tandem

import org.junit.Assert.assertEquals
import org.junit.Test

/** Finding a game's processes to pause in sleep (SleepFreeze.kt). */
class SleepFreezeTest {
    private val ps = """
          PID NAME
         2214 system_server
         8123 org.azahar_emu.azahar
         8190 org.azahar_emu.azahar:helper
         9001 org.azahar_emu.azahar.other
         9100 dev.eden.eden_emulator
    """.trimIndent()

    @Test fun findsThePackageAndItsSubProcessesOnly() {
        assertEquals(listOf(8123, 8190), SleepFreeze.pidsOf(ps, "org.azahar_emu.azahar"))
        assertEquals(listOf(9100), SleepFreeze.pidsOf(ps, "dev.eden.eden_emulator"))
        assertEquals(emptyList<Int>(), SleepFreeze.pidsOf(null, "x"))
    }
}
