package io.github.zahakj.tandem.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Shelf's packaging table, the real-box-scan test and cutting a spine out of a wraparound scan. */
class PackagingTest {
    private fun spec(id: String) = assertNotNullSpec(Packaging.of(listOf(id)), id)
    private fun assertNotNullSpec(s: PackSpec?, id: String): PackSpec { assertNotNull(id, s); return s!! }

    @Test fun everySpecIsAPlausibleBox() {
        for (s in Packaging.all) {
            assertTrue(s.key, s.heightMm in 100f..200f)
            assertTrue(s.key, s.coverRatio in 0.56f..1.5f)
            assertTrue(s.key, s.spineRatio in 0.04f..0.3f)
            assertTrue(s.key, s.frontBand in 0.04f..0.15f)
            assertTrue(s.key, s.spineBand in 0.05f..0.2f)
            assertTrue(s.key, s.wordmark.isNotBlank())
        }
    }

    @Test fun casesHaveTheirRealShapes() {
        // A Switch case is slim and tall, a PS2 case taller and thicker, DS and 3DS nearly square, jewel cases
        // thin squares, SNES and N64 boxes wide.
        val switch = spec("switch"); val ps2 = spec("ps2"); val ds = spec("nds"); val psx = spec("psx"); val snes = spec("snes")
        assertTrue(switch.coverRatio < 0.65f)
        assertTrue(ps2.heightMm > switch.heightMm && ps2.spineMm > switch.spineMm)
        assertTrue(ds.coverRatio in 0.85f..0.95f)
        assertEquals(1f, psx.coverRatio, 0.01f)
        assertTrue(psx.spineRatio < 0.1f)
        assertTrue(snes.coverRatio > 1.3f && snes.cardboard)
        assertFalse(switch.cardboard)
    }

    @Test fun systemsAreFoundByAnyOfTheirNames() {
        assertEquals("n3ds", Packaging.of(listOf("3ds"))?.key)
        assertEquals("psx", Packaging.of(listOf("someid", "ps1"))?.key)
        assertEquals("genesis", Packaging.of(listOf("megadrive"))?.key)
        assertNull(Packaging.of(listOf("windows", "arcade")))
        assertEquals("MEGA DRIVE", Packaging.of(listOf("genesis"), Region.EU)?.wordmark)
        assertEquals("SEGA GENESIS", Packaging.of(listOf("genesis"), Region.US)?.wordmark)
        assertTrue(Packaging.of(listOf("nds"), Region.EU)!!.spineMm > Packaging.of(listOf("nds"))!!.spineMm)
    }

    @Test fun realScansAreToldFromIconsAndGrids() {
        val switch = spec("switch")
        assertTrue(Packaging.isScan(0.62f, switch))
        assertTrue(Packaging.isScan(1050f / 1700f, switch))
        assertFalse("a square store icon gets the band", Packaging.isScan(1f, switch))
        assertFalse("a 2:3 launcher grid gets the band", Packaging.isScan(600f / 900f, switch))
        assertTrue("from the physical box folder it always is", Packaging.isScan(1f, switch, fromScans = true))
        assertTrue("nothing known: shown as it is", Packaging.isScan(null, switch))
        assertTrue("no packaging: shown as it is", Packaging.isScan(1f, null))
        val ds = spec("nds")
        assertTrue(Packaging.isScan(0.89f, ds))
        assertFalse(Packaging.isScan(1f, ds))
    }

    @Test fun spineIsCutFromTheMiddleOfAWraparound() {
        val switch = spec("switch")
        // A Switch insert: 99 mm back, 10 mm spine, 99 mm front, 161 mm tall, at 10 px a millimetre.
        val r = Packaging.spineIn(2080, 1610, switch)
        assertNotNull(r)
        assertEquals(1040f, (r!!.first + r.last) / 2f, 4f)
        assertEquals(100f, (r.last - r.first + 1).toFloat(), 6f)
        // The front is the right part, or the left for a box that opens right to left.
        assertEquals(2079, Packaging.frontIn(2080, 1610, switch, rightToLeft = false)!!.last)
        assertEquals(0, Packaging.frontIn(2080, 1610, switch, rightToLeft = true)!!.first)
    }

    @Test fun picturesThatArentWraparoundsAreLeftWhole() {
        val switch = spec("switch")
        assertNull("a front scan", Packaging.spineIn(990, 1610, switch))
        assertNull("a picture far too wide", Packaging.spineIn(4000, 1000, switch))
        assertNull(Packaging.frontIn(990, 1610, switch, rightToLeft = false))
        assertTrue(Packaging.isSpineShaped(100, 1610))
        assertTrue("stored on its side", Packaging.isSpineShaped(1610, 100))
        assertFalse(Packaging.isSpineShaped(990, 1610))
    }

    @Test fun aPrintedBandMarksARealScan() {
        val spec = Packaging.of(listOf("n3ds"), Region.US)!!
        val w = 90; val h = 100
        val white = 0xFFF2F2F0.toInt(); val art = 0xFF2050A0.toInt()
        // A scan: the art, with the white strip down its right edge.
        val scan = IntArray(w * h) { i -> if (i % w >= w - 9) white else art }
        assertTrue(Packaging.hasBand(scan, w, h, spec))
        // Store art: the picture runs to every edge.
        assertFalse(Packaging.hasBand(IntArray(w * h) { art }, w, h, spec))
    }
}
