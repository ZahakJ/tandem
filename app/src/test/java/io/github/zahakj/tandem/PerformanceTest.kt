package io.github.zahakj.tandem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Performance profiles (Performance.kt): the plan for AYN's modes on a fake device. */
class PerformanceTest {
    /** A device that does what it is told, unless [broken]; [log] lists every write. [mode] null is not an AYN. */
    private class Fake(var mode: Int?, var fan: Int? = 0, var hz: Int? = 60, var rates: List<Int> = listOf(60, 120), var broken: Boolean = false) : PerfDevice {
        val log = ArrayList<String>()
        override fun mode() = mode
        override fun setMode(v: Int): Boolean { if (broken) return false; log += "mode $v"; mode = v; return true }
        override fun fan() = fan
        override fun setFan(v: Int): Boolean { if (broken) return false; log += "fan $v"; fan = v; return true }
        override fun hz() = hz
        override fun setHz(v: Int): Boolean { if (broken) return false; log += "hz $v"; hz = v; return true }
        override fun supportedHz() = rates
    }

    private val high = PerfProfile(mode = 2, fan = 3, hz = 120)

    @Test fun appliesTheProfileAndPutsEverythingBack() {
        val d = Fake(mode = 0, fan = 2, hz = 60)
        val c = PerformancePlan.begin(PerfChanges(), high, d)
        assertEquals(PerfChanges(PerfChange(0, 2), PerfChange(2, 3), PerfChange(60, 120), wanted = high), c)
        assertEquals(listOf("mode 2", "fan 3", "hz 120"), d.log)
        val left = PerformancePlan.end(c, d)
        assertFalse(left.any)
        assertNull(left.wanted)
        assertEquals(0, d.mode); assertEquals(2, d.fan); assertEquals(60, d.hz)
        assertEquals(listOf("mode 2", "fan 3", "hz 120", "mode 0", "fan 2", "hz 60"), d.log)
    }

    @Test fun keepLeavesThatModeAlone() {
        val d = Fake(mode = 0, fan = 2, hz = 60)
        val c = PerformancePlan.begin(PerfChanges(), PerfProfile(mode = 1), d)
        assertEquals(PerfChanges(mode = PerfChange(0, 1), wanted = PerfProfile(mode = 1)), c)
        assertEquals(listOf("mode 1"), d.log)
        // A profile that keeps everything changes nothing and shows nothing.
        val none = PerformancePlan.begin(PerfChanges(), PerfProfile(), Fake(mode = 0))
        assertFalse(none.any)
        assertNull(none.wanted)
        assertNull(PerformancePlan.label(PerfProfile()))
    }

    @Test fun aValueAlreadyAsWantedIsNotWrittenDown() {
        val d = Fake(mode = 2, fan = 3, hz = 120)
        val c = PerformancePlan.begin(PerfChanges(), high, d)
        assertFalse(c.any)
        assertEquals(high, c.wanted)
        assertTrue(d.log.isEmpty())
        // So nothing is "put back" over the user's own settings when the game ends.
        PerformancePlan.end(c, d)
        assertTrue(d.log.isEmpty())
    }

    @Test fun aModeTheUserChangedMidGameStaysTheirs() {
        val d = Fake(mode = 0, fan = 2, hz = 60)
        val c = PerformancePlan.begin(PerfChanges(), high, d)
        // Mid-game, AYN's control centre: performance back to Standard, fan to Quiet.
        d.mode = 0; d.fan = 1
        d.log.clear()
        val left = PerformancePlan.end(c, d)
        assertFalse(left.any)
        assertEquals(listOf("hz 60"), d.log)
        assertEquals(0, d.mode); assertEquals(1, d.fan); assertEquals(60, d.hz)
    }

    @Test fun notAnAynDeviceNeverWrites() {
        val d = Fake(mode = null, fan = 2, hz = 60)
        assertFalse(PerformancePlan.isAyn(d))
        val c = PerformancePlan.begin(PerfChanges(), high, d)
        assertEquals(PerfChanges(), c)
        assertTrue(d.log.isEmpty())
        assertTrue(PerformancePlan.isAyn(Fake(mode = 0)))
    }

    @Test fun aCustomFanSpeedIsLeftAlone() {
        val d = Fake(mode = 0, fan = PerformancePlan.FAN_CUSTOM, hz = 60)
        val c = PerformancePlan.begin(PerfChanges(), high, d)
        assertNull(c.fan)
        assertEquals(PerformancePlan.FAN_CUSTOM, d.fan)
        assertEquals(listOf("mode 2", "hz 120"), d.log)
        // And Custom is never one of the offered fan modes.
        assertFalse(PerformancePlan.fans.any { it.first == PerformancePlan.FAN_CUSTOM })
    }

    @Test fun aRefreshRateTheDisplayLacksIsSkipped() {
        val d = Fake(mode = 0, fan = 0, hz = 60, rates = listOf(60))
        val c = PerformancePlan.begin(PerfChanges(), PerfProfile(hz = 120), d)
        assertNull(c.hz)
        assertTrue(d.log.isEmpty())
    }

    @Test fun whatCouldntBePutBackIsKeptToTryAgain() {
        val d = Fake(mode = 0, fan = 0, hz = 60)
        val c = PerformancePlan.begin(PerfChanges(), high, d)
        d.broken = true
        val left = PerformancePlan.end(c, d)
        assertEquals(c.copy(wanted = null), left)
        d.broken = false
        assertFalse(PerformancePlan.end(left, d).any)
        assertEquals(0, d.mode); assertEquals(0, d.fan); assertEquals(60, d.hz)
    }

    @Test fun aSecondGameKeepsTheOriginalToPutBack() {
        // The first game's restore failed: the device still has its profile. The next game wants another one.
        val d = Fake(mode = 2, fan = 3, hz = 120)
        val had = PerfChanges(PerfChange(0, 2), PerfChange(2, 3), PerfChange(60, 120))
        val c = PerformancePlan.begin(had, PerfProfile(mode = 1, fan = 3), d)
        assertEquals(PerfChanges(PerfChange(0, 1), PerfChange(2, 3), PerfChange(60, 120), wanted = PerfProfile(mode = 1, fan = 3)), c)
        assertEquals(listOf("mode 1"), d.log)
        assertFalse(PerformancePlan.end(c, d).any)
        assertEquals(0, d.mode); assertEquals(2, d.fan); assertEquals(60, d.hz)
    }

    @Test fun aGamesOwnChoicesGoOverTheDefault() {
        val base = PerfProfile(mode = 2, fan = PerfProfile.KEEP, hz = 120)
        // Nothing of its own: the default, with Keep meaning null.
        assertEquals(PerfProfile(mode = 2, hz = 120), PerfProfile.resolve(null, base))
        // Keep for the mode, its own fan, the default refresh rate.
        assertEquals(PerfProfile(fan = 1, hz = 120), PerfProfile.resolve(PerfProfile(mode = PerfProfile.KEEP, fan = 1), base))
        assertEquals(PerfProfile(), PerfProfile.resolve(PerfProfile(mode = PerfProfile.KEEP, hz = PerfProfile.KEEP), PerfProfile(mode = 1, hz = 60)))
    }

    @Test fun theLabelNamesWhatIsSet() {
        assertEquals("High performance · Sport · 120 Hz", PerformancePlan.label(high))
        assertEquals("Standard · Off", PerformancePlan.label(PerfProfile(mode = 0, fan = 0)))
        assertEquals("60 Hz", PerformancePlan.label(PerfProfile(hz = 60)))
        assertNull(PerformancePlan.label(null))
    }

    @Test fun theChangesSurviveARestartAsJson() {
        val json = kotlinx.serialization.json.Json { encodeDefaults = false }
        val c = PerfChanges(mode = PerfChange(0, 2), hz = PerfChange(60, 120), wanted = PerfProfile(mode = 2, hz = 120))
        assertEquals(c, json.decodeFromString(PerfChanges.serializer(), json.encodeToString(PerfChanges.serializer(), c)))
        val p = PerfProfile(mode = PerfProfile.KEEP, fan = 1)
        assertEquals(p, json.decodeFromString(PerfProfile.serializer(), json.encodeToString(PerfProfile.serializer(), p)))
    }
}
