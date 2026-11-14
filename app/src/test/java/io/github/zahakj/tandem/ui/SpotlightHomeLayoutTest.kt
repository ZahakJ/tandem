package io.github.zahakj.tandem.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpotlightHomeLayoutTest {
    private val rail = SpotlightRail(viewH = 400f, pitch = 100f, gap = 12f)

    @Test fun wideFrameLeavesTheArtABandBetweenRailAndPanel() {
        val f = SpotlightFrame(832f, 468f, railLeft = true, margin = 24f)
        assertTrue(f.wide)
        assertEquals(0f, f.railX, 0.01f)
        assertEquals(832f * 0.22f, f.railW, 0.01f)
        assertEquals(832f - 24f, f.panelX + f.panelW, 0.01f)
        assertTrue("a clear band of art", f.panelX - (f.railX + f.railW) > 832f * 0.2f)
    }

    @Test fun mirroredFrameSwapsSides() {
        val l = SpotlightFrame(832f, 468f, railLeft = true, margin = 24f)
        val r = SpotlightFrame(832f, 468f, railLeft = false, margin = 24f)
        assertEquals(832f, r.railX + r.railW, 0.01f)
        assertEquals(24f, r.panelX, 0.01f)
        assertEquals(l.railShare, r.railShare, 0.001f)
        assertEquals(l.panelNear, r.panelNear, 0.001f)
        assertTrue(l.panelNear > l.railShare)
    }

    @Test fun squareFrameGivesThePanelTheRest() {
        val f = SpotlightFrame(468f, 538f, railLeft = true, margin = 16f)
        assertTrue(!f.wide)
        assertEquals(468f - 16f, f.panelX + f.panelW, 0.01f)
        assertEquals(f.railW + 16f, f.panelX, 0.01f)
    }

    @Test fun highlightIsCentredAndBiggest() {
        assertEquals(200f, rail.y(0f), 0.01f)
        assertEquals(SpotlightRail.FOCUS, rail.scale(0f), 0.001f)
        assertEquals(1f, rail.scale(1f), 0.001f)
        assertEquals(1f, rail.scale(-3f), 0.001f)
        assertEquals(1f, rail.alpha(0f), 0.001f)
        assertTrue(rail.alpha(1f) < 1f)
        // Neighbours make room for the bigger tile.
        assertEquals(312f, rail.y(1f), 0.01f)
        assertEquals(88f, rail.y(-1f), 0.01f)
        assertEquals(412f, rail.y(2f), 0.01f)
    }

    @Test fun tilesFadeOutPastTheEdges() {
        assertEquals(0f, rail.alpha(4f), 0.001f)
        assertEquals(0f, rail.alpha(-4f), 0.001f)
    }

    @Test fun tilesAreGoneBeforeTheyTouchTheEdges() {
        val r = SpotlightRail(viewH = 600f, pitch = 140f, gap = 17f, tileH = 126f)
        // The distance (in tiles) at which a tile's near edge (half its height) meets the top or the bottom.
        fun touching(top: Boolean): Float {
            var lo = 0f; var hi = 6f
            repeat(60) {
                val mid = (lo + hi) / 2f
                val y = r.y(if (top) -mid else mid)
                val half = r.tileH / 2f * r.scale(mid)
                val inside = if (top) y - half > 0f else y + half < r.viewH
                if (inside) lo = mid else hi = mid
            }
            return hi
        }
        val top = touching(true)
        val bottom = touching(false)
        assertEquals(0f, r.alpha(-top), 0.001f)
        assertEquals(0f, r.alpha(-top - 0.3f), 0.001f)
        assertEquals(0f, r.alpha(bottom), 0.001f)
        assertEquals(0f, r.alpha(bottom + 0.3f), 0.001f)
        assertEquals(1f, r.alpha(0f), 0.001f)
        assertTrue("still showing a little way in", r.alpha(bottom - 0.4f) > 0f)
        // The furthest tile composed starts invisible, so nothing pops in.
        assertEquals(0f, r.alpha(r.reach - 0.5f), 0.001f)
        assertEquals(0f, r.alpha(-(r.reach - 0.5f)), 0.001f)
    }

    @Test fun storyWaitsScrollsHoldsAndComesBack() {
        val over = 90f
        val speed = 30f // three seconds of scrolling
        assertEquals(0f, StoryDrift.position(0, over, speed), 0f)
        assertEquals(0f, StoryDrift.position(StoryDrift.WAIT_MS - 1, over, speed), 0f)
        assertEquals(45f, StoryDrift.position(StoryDrift.WAIT_MS + 1500, over, speed), 0.01f)
        val end = StoryDrift.WAIT_MS + 3000
        assertEquals(over, StoryDrift.position(end, over, speed), 0.01f)
        assertEquals(over, StoryDrift.position(end + StoryDrift.HOLD_MS - 1, over, speed), 0.01f)
        val back = end + StoryDrift.HOLD_MS
        val mid = StoryDrift.position(back + StoryDrift.RETURN_MS / 2, over, speed)
        assertTrue(mid > 0f && mid < over)
        // Then the top again, and a new wait.
        assertEquals(StoryDrift.cycleMs(over, speed), back + StoryDrift.RETURN_MS)
        assertEquals(0f, StoryDrift.position(back + StoryDrift.RETURN_MS + 10, over, speed), 0f)
        assertEquals(0f, StoryDrift.position(5000, 0f, speed), 0f)
    }

    @Test fun storyScrollIsLinear() {
        val a = StoryDrift.position(StoryDrift.WAIT_MS + 500, 300f, 20f)
        val b = StoryDrift.position(StoryDrift.WAIT_MS + 1000, 300f, 20f)
        assertEquals(10f, a, 0.01f)
        assertEquals(20f, b, 0.01f)
    }

    @Test fun storyRestsWithoutFramesWhileItWaits() {
        assertEquals(StoryDrift.WAIT_MS, StoryDrift.stillFor(0, 90f, 30f))
        assertEquals(0L, StoryDrift.stillFor(StoryDrift.WAIT_MS + 10, 90f, 30f))
        assertEquals(StoryDrift.HOLD_MS - 100, StoryDrift.stillFor(StoryDrift.WAIT_MS + 3100, 90f, 30f))
        assertEquals(0L, StoryDrift.stillFor(StoryDrift.WAIT_MS + 3000 + StoryDrift.HOLD_MS + 10, 90f, 30f))
        assertEquals(Long.MAX_VALUE, StoryDrift.stillFor(0, 0f, 30f))
    }

    @Test fun visibleIsAWindowAroundTheOffset() {
        assertEquals(0..4, rail.visible(0f, 100))
        assertEquals(46..54, rail.visible(50.3f, 100))
        assertEquals(95..99, rail.visible(99f, 100))
        assertTrue(rail.visible(0f, 0).isEmpty())
    }

    @Test fun hitFindsTheTileUnderAFinger() {
        assertEquals(5, rail.hit(200f, 5f, 20))
        assertEquals(6, rail.hit(312f, 5f, 20))
        assertEquals(4, rail.hit(90f, 5f, 20))
        assertEquals(-1, rail.hit(200f, 5f, 0))
    }

    @Test fun stepsStopAtTheEnds() {
        assertEquals(1f, SpotlightRail.stepped(0f, 1, 10), 0f)
        assertEquals(0f, SpotlightRail.stepped(0f, -1, 10), 0f)
        assertEquals(9f, SpotlightRail.stepped(8.6f, 3, 10), 0f)
        assertEquals(0f, SpotlightRail.stepped(0f, 1, 0), 0f)
    }

    @Test fun holdingSpeedsUp() {
        assertEquals(1, SpotlightRail.holdStep(0))
        assertEquals(1, SpotlightRail.holdStep(600))
        assertEquals(2, SpotlightRail.holdStep(2000))
        assertEquals(3, SpotlightRail.holdStep(5000))
    }

    @Test fun flingsSettleOnATile() {
        assertEquals(3f, SpotlightRail.settle(3.2f, 0f, 10, still = false), 0f)
        assertEquals(6f, SpotlightRail.settle(3.2f, 12f, 10, still = false), 0f)
        assertEquals(3f, SpotlightRail.settle(3.2f, 12f, 10, still = true), 0f)
        assertEquals(9f, SpotlightRail.settle(8f, 400f, 10, still = false), 0f)
        assertEquals(0f, SpotlightRail.settle(1f, -400f, 10, still = false), 0f)
    }

    @Test fun dragsGiveWayPastTheEnds() {
        assertEquals(3.5f, SpotlightRail.dragged(3f, 0.5f, 10), 0.001f)
        assertEquals(-0.35f, SpotlightRail.dragged(0f, -1f, 10), 0.001f)
        assertEquals(-0.6f, SpotlightRail.dragged(-0.5f, -3f, 10), 0.001f)
        // Coming back in follows the finger.
        assertEquals(0.5f, SpotlightRail.dragged(-0.5f, 1f, 10), 0.001f)
    }

    @Test fun groupsGoRound() {
        assertEquals(1, SpotlightSteps.cycle(0, 1, 4))
        assertEquals(0, SpotlightSteps.cycle(3, 1, 4))
        assertEquals(3, SpotlightSteps.cycle(0, -1, 4))
        assertEquals(0, SpotlightSteps.cycle(0, 1, 1))
        assertEquals(0, SpotlightSteps.cycle(2, 1, 0))
    }

    @Test fun backUnwindsOneThingAtATime() {
        assertEquals(SpotlightBack.Clean, SpotlightSteps.back(clean = true, expanded = true, index = 4, group = 2, home = 0))
        assertEquals(SpotlightBack.Description, SpotlightSteps.back(false, true, 4, 2, 0))
        assertEquals(SpotlightBack.Top, SpotlightSteps.back(false, false, 4, 2, 0))
        assertEquals(SpotlightBack.Home, SpotlightSteps.back(false, false, 0, 2, 0))
        assertEquals(SpotlightBack.Nothing, SpotlightSteps.back(false, false, 0, 0, 0))
    }

    @Test fun artWaitsOnlyAfterTheFirstPicture() {
        assertEquals(0L, SpotlightSteps.artDelay(first = true))
        assertEquals(SpotlightSteps.ART_SETTLE_MS, SpotlightSteps.artDelay(first = false))
    }

    @Test fun coversFitTheirBox() {
        val (pw, ph) = SpotlightSteps.coverSize(0.7f, 100f, 100f)
        assertEquals(70f, pw, 0.01f); assertEquals(100f, ph, 0.01f)
        val (lw, lh) = SpotlightSteps.coverSize(1.4f, 100f, 100f)
        assertEquals(100f, lw, 0.01f); assertEquals(100f / 1.4f, lh, 0.01f)
        // Odd shapes are held to the real range of boxes; unknown is a portrait box.
        assertEquals(56f, SpotlightSteps.coverSize(0.2f, 100f, 100f).first, 0.01f)
        assertEquals(66f, SpotlightSteps.coverSize(null, 100f, 100f).first, 0.01f)
    }
}
