package io.github.zahakj.tandem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeviceInfoTest {
    @Test fun freeMemoryIsTheKernelsMemAvailableInBytes() {
        val meminfo = """
            MemTotal:       15621188 kB
            MemFree:         1519084 kB
            MemAvailable:   10137708 kB
            Cached:          5401252 kB
        """.trimIndent()
        assertEquals(10137708L * 1024, DeviceInfo.memAvailable(meminfo))
    }

    @Test fun anOldKernelWithoutMemAvailableGivesNothing() {
        assertNull(DeviceInfo.memAvailable("MemTotal: 2048 kB\nMemFree: 512 kB"))
    }
}
