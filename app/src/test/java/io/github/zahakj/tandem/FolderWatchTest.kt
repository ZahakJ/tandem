package io.github.zahakj.tandem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Folder watching's debounce (RescanCoalescer in FolderWatch.kt): one rescan per burst, none during a scan. */
class FolderWatchTest {
    private val quiet = 3_000L

    @Test fun nothingPendingNeverRescans() {
        val c = RescanCoalescer(quiet)
        assertFalse(c.pending)
        assertNull(c.waitFor(1_000))
        assertFalse(c.take(1_000, scanning = false))
    }

    @Test fun aChangeRescansOnceTheQuietTimeHasPassed() {
        val c = RescanCoalescer(quiet)
        c.changed(1_000)
        assertTrue(c.pending)
        assertEquals(3_000L, c.waitFor(1_000))
        assertFalse(c.take(2_000, scanning = false))
        assertEquals(2_000L, c.waitFor(2_000))
        assertTrue(c.take(4_000, scanning = false))
        // Taken: nothing is left to do.
        assertFalse(c.pending)
        assertFalse(c.take(10_000, scanning = false))
    }

    @Test fun aBurstOfCopiesMakesOneRescanAfterTheLastFile() {
        val c = RescanCoalescer(quiet)
        c.changed(1_000)
        c.changed(2_500)
        c.changed(3_900)
        // Three seconds after the first file, but not after the last: still waiting.
        assertFalse(c.take(4_000, scanning = false))
        assertTrue(c.take(6_900, scanning = false))
        assertFalse(c.pending)
    }

    @Test fun nothingStartsWhileAScanRunsAndTheChangeWaitsForIt() {
        val c = RescanCoalescer(quiet)
        c.changed(0)
        assertFalse(c.take(5_000, scanning = true))
        assertTrue(c.pending)
        // The scan ends; the change that waited makes exactly one rescan.
        assertTrue(c.take(6_000, scanning = false))
        assertFalse(c.take(6_000, scanning = false))
    }

    @Test fun changesDuringAScanCoalesceIntoOne() {
        val c = RescanCoalescer(quiet)
        c.changed(0)
        c.changed(1_000)
        assertFalse(c.take(4_000, scanning = true))
        c.changed(4_500)
        assertFalse(c.take(5_000, scanning = false))
        assertTrue(c.take(7_500, scanning = false))
        assertFalse(c.pending)
    }

    @Test fun theWaitNeverGoesNegative() {
        val c = RescanCoalescer(quiet)
        c.changed(0)
        assertEquals(0L, c.waitFor(10_000))
    }
}
