package io.github.zahakj.tandem.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class OrbitLayoutTest {
    private val layout = OrbitLayout(width = 1920f, baseY = 600f, rise = 250f, orb = 190f, side = 5)

    @Test fun frontOrbIsCentredLowestAndBiggest() {
        val front = layout.place(0f)
        assertEquals(960f, front.x, 0.01f)
        assertEquals(600f, front.y, 0.01f)
        assertEquals(1f, front.scale, 0.001f)
        for (d in listOf(-3f, -1f, -0.5f, 0.5f, 1f, 3f)) {
            val p = layout.place(d)
            assertTrue("orb $d is higher than the front", p.y < front.y)
            assertTrue("orb $d is smaller than the front", p.scale < front.scale)
            assertTrue("orb $d is behind the front", p.depth < front.depth)
        }
    }

    @Test fun neighboursAreAboutAFifthSmallerAfterTheFrontSwell() {
        // The front orb is FOCUS times its neighbours' size (less the slight depth shrink).
        val ratio = layout.place(0f).scale / layout.place(1f).scale
        assertTrue("ratio $ratio", ratio in 1.55f..1.7f)
    }

    @Test fun theTrackIsSymmetricAndRisesTowardsTheEnds() {
        var lastY = layout.place(0f).y
        var lastX = layout.place(0f).x
        for (i in 1..5) {
            val right = layout.place(i.toFloat())
            val left = layout.place(-i.toFloat())
            assertEquals(960f - right.x, left.x - 960f, 0.01f)
            assertEquals(right.y, left.y, 0.01f)
            assertTrue("rises at $i", right.y < lastY)
            assertTrue("moves right at $i", right.x > lastX)
            lastY = right.y; lastX = right.x
        }
    }

    @Test fun visibleOrbsStayOnTheStage() {
        for (i in -5..5) {
            val p = layout.place(i.toFloat())
            val r = layout.orb / 2f * p.scale
            assertTrue("orb $i inside on the left", p.x - r >= 0f)
            assertTrue("orb $i inside on the right", p.x + r <= layout.width)
        }
    }

    @Test fun neighboursDoNotOverlap() {
        for (i in 0..4) {
            val a = layout.place(i.toFloat())
            val b = layout.place(i + 1f)
            val reach = layout.orb / 2f * (a.scale + b.scale)
            // Near the ring's sides the orbs climb as much as they move across, so measure the real distance.
            val dx = b.x - a.x
            val dy = b.y - a.y
            assertTrue("orbs $i and ${i + 1} overlap", dx * dx + dy * dy > reach * reach)
        }
    }

    @Test fun orbsDimAndShrinkSteadilyRoundTheRing() {
        // No jump between the neighbours: each orb further round is a little smaller and further back.
        var last = layout.place(1f)
        for (i in 2..5) {
            val p = layout.place(i.toFloat())
            assertTrue("orb $i is smaller", p.scale < last.scale)
            assertTrue("orb $i is further back", p.back > last.back)
            assertTrue("orb $i shrinks gently", p.scale > last.scale * 0.8f)
            last = p
        }
        assertEquals(1f, layout.place(5f).back, 0.001f)
        // The ring's front point is the front orb's centre.
        assertEquals(layout.baseY, layout.centreY + layout.radiusY, 0.01f)
    }

    @Test fun orbsFadeOutPastTheSide() {
        assertTrue(layout.place(5f).alpha > 0.3f)
        assertEquals(0f, layout.place(6f).alpha, 0.001f)
        assertEquals(0f, layout.place(-9f).alpha, 0.001f)
    }

    @Test fun distanceTakesTheShortWayRoundWhenWrapping() {
        assertEquals(1f, OrbitLayout.distance(0, 19f, 20, wraps = true), 0.001f)
        assertEquals(-1f, OrbitLayout.distance(19, 0f, 20, wraps = true), 0.001f)
        assertEquals(19f, OrbitLayout.distance(19, 0f, 20, wraps = false), 0.001f)
        assertEquals(0.5f, OrbitLayout.distance(3, 2.5f, 20, wraps = true), 0.001f)
        // An offset that has spun round a few times still finds the orbs.
        assertEquals(0f, OrbitLayout.distance(4, 64f, 20, wraps = true), 0.001f)
    }

    @Test fun indexWrapsOrClamps() {
        assertEquals(0, OrbitLayout.index(20f, 20, wraps = true))
        assertEquals(19, OrbitLayout.index(-1f, 20, wraps = true))
        assertEquals(4, OrbitLayout.index(3.6f, 20, wraps = true))
        assertEquals(2, OrbitLayout.index(7f, 3, wraps = false))
        assertEquals(0, OrbitLayout.index(-2f, 3, wraps = false))
    }

    @Test fun onlyLongOrbitsWrap() {
        assertFalse(OrbitLayout.wraps(2))
        assertFalse(OrbitLayout.wraps(12))
        assertTrue(OrbitLayout.wraps(13))
    }

    @Test fun steppingStopsAtTheEndsUnlessItWraps() {
        assertEquals(0f, OrbitLayout.stepped(0f, -1, 5, wraps = false), 0.001f)
        assertEquals(4f, OrbitLayout.stepped(4f, 1, 5, wraps = false), 0.001f)
        assertEquals(-1f, OrbitLayout.stepped(0f, -1, 20, wraps = true), 0.001f)
        assertEquals(20f, OrbitLayout.stepped(19f, 1, 20, wraps = true), 0.001f)
        assertEquals(19, OrbitLayout.index(OrbitLayout.stepped(0f, -1, 20, wraps = true), 20, wraps = true))
    }

    @Test fun targetGoesTheShortWayRound() {
        assertEquals(20f, OrbitLayout.target(19f, 0, 20, wraps = true), 0.001f)
        assertEquals(-1f, OrbitLayout.target(0f, 19, 20, wraps = true), 0.001f)
        assertEquals(7f, OrbitLayout.target(2f, 7, 20, wraps = false), 0.001f)
    }

    @Test fun aFlickCarriesFurtherUnlessMotionIsReduced() {
        assertEquals(3f, OrbitLayout.settle(3.2f, 0f, 20, wraps = true, still = false), 0.001f)
        assertTrue(OrbitLayout.settle(3.2f, 20f, 20, wraps = true, still = false) > 6f)
        assertEquals(3f, OrbitLayout.settle(3.2f, 20f, 20, wraps = true, still = true), 0.001f)
        // Never more than eight orbs, and never past the ends of an orbit that doesn't wrap.
        assertEquals(11f, OrbitLayout.settle(3f, 500f, 40, wraps = true, still = false), 0.001f)
        assertEquals(4f, OrbitLayout.settle(3f, 500f, 5, wraps = false, still = false), 0.001f)
        assertEquals(0f, OrbitLayout.settle(1f, -500f, 5, wraps = false, still = false), 0.001f)
    }

    @Test fun groupJumpGoesToTheNextOrPreviousGroupsFirstOrb() {
        // Four systems, two collections, three apps.
        val groups = listOf(0, 0, 0, 0, 1, 1, 2, 2, 2)
        assertEquals(4, OrbitLayout.groupJump(groups, 2, 1))
        assertEquals(6, OrbitLayout.groupJump(groups, 4, 1))
        assertEquals(0, OrbitLayout.groupJump(groups, 7, 1))
        assertEquals(6, OrbitLayout.groupJump(groups, 1, -1))
        assertEquals(0, OrbitLayout.groupJump(groups, 5, -1))
        assertEquals(3, OrbitLayout.groupJump(listOf(0, 0, 0, 0), 3, 1))
    }

    @Test fun hitFindsTheFrontmostOrbUnderTheFinger() {
        val front = layout.place(0f)
        assertEquals(7, layout.hit(front.x, front.y, 7f, 20, wraps = true))
        val right = layout.place(1f)
        assertEquals(8, layout.hit(right.x, right.y, 7f, 20, wraps = true))
        val left = layout.place(-1f)
        assertEquals(19, layout.hit(left.x, left.y, 0f, 20, wraps = true))
        assertEquals(-1, layout.hit(front.x, 10f, 7f, 20, wraps = true))
    }

    @Test fun visibleListsEachOrbOnce() {
        val few = layout.visible(1f, 3, wraps = false)
        assertEquals(listOf(0, 1, 2), few)
        val many = layout.visible(0f, 20, wraps = true)
        assertEquals(13, many.size)
        assertEquals(many.size, many.toSet().size)
        assertTrue(19 in many && 1 in many)
    }

    @Test fun aFewOrbsStayCentredOnTheFocusedOne() {
        // With two orbs, the focused one is still the front, centred orb.
        val p = layout.place(OrbitLayout.distance(1, 1f, 2, wraps = false))
        assertEquals(960f, p.x, 0.01f)
    }

    @Test fun fanPutsTheLatestInTheMiddleAndAlternates() {
        assertEquals(listOf(0, 1, -1, 2, -2), (0 until 5).map { Fan.slot(it) })
        assertEquals(listOf(4, 2, 0, 1, 3), Fan.leftToRight(5))
        assertEquals(listOf(2, 0, 1), Fan.leftToRight(3))
        val middle = Fan.place(0, 5, 100f)
        assertEquals(0f, middle.x, 0.001f)
        assertEquals(0f, middle.turn, 0.001f)
        assertEquals(0f, middle.dim, 0.001f)
        for (r in 1 until 5) {
            val p = Fan.place(r, 5, 100f)
            assertTrue("card $r is smaller than the middle", p.scale < middle.scale)
            assertTrue("card $r is dimmer than the middle", p.dim > middle.dim)
            assertTrue("card $r turns away from the middle", p.turn * p.x > 0f)
            assertTrue("card $r turns only a little", abs(p.turn) <= 25f)
        }
    }

    @Test fun carouselCoversTuckOnlyALittleBehindEachOther() {
        // Left to right, each cover's left edge is past the previous one's, and at most about a fifth is hidden.
        val w = 100f
        val places = Fan.leftToRight(5).map { Fan.place(it, 5, w) }
        for (i in 1 until places.size) {
            val a = places[i - 1]
            val b = places[i]
            val aRight = a.x + w * a.scale / 2f
            val bLeft = b.x - w * b.scale / 2f
            assertTrue("cover $i starts right of the one before", bLeft > a.x - w * a.scale / 2f)
            val hidden = (aRight - bLeft) / (w * minOf(a.scale, b.scale))
            assertTrue("covers $i overlap by $hidden", hidden in 0f..0.3f)
        }
    }

    @Test fun continueStandsApartLeftOfTheCarousel() {
        for (n in listOf(0, 1, 2, 3, 5)) {
            val c = Fan.continueX(n, 100f)
            val leftmost = Fan.leftToRight(n).firstOrNull()?.let { Fan.place(it, n, 100f) }
            val edge = leftmost?.let { it.x - 100f * it.scale / 2f } ?: -50f
            assertTrue("continue clears $n covers", c + 100f * Fan.CONTINUE / 2f < edge)
            // reach() covers the Continue card's left edge, which is what Orbit fits into half the screen.
            assertEquals(-(c - 100f * Fan.CONTINUE / 2f), Fan.reach(n, true) * 100f, 0.01f)
        }
    }

    @Test fun fanShowsFiveOrThreeSoThereIsAMiddle() {
        assertEquals(listOf(0, 1, 2, 3, 3, 5, 5), (0..6).map { Fan.count(it) })
        // Two cards sit either side of the centre, without touching.
        val a = Fan.place(0, 2, 100f)
        val b = Fan.place(1, 2, 100f)
        assertEquals(-a.x, b.x, 0.001f)
        assertTrue(b.x - a.x > 100f * a.scale)
    }

    @Test fun mirroredRingPutsTheNextOrbsOnTheLeftAndHitsThem() {
        val rtl = OrbitLayout(width = 1920f, baseY = 600f, rise = 250f, orb = 190f, side = 5, mirrored = true)
        for (d in listOf(-3f, -1f, 0f, 0.5f, 2f)) {
            val a = layout.place(d)
            val b = rtl.place(d)
            assertEquals(1920f - a.x, b.x, 0.01f)
            assertEquals(a.y, b.y, 0.01f)
            assertEquals(a.scale, b.scale, 0.0001f)
        }
        assertTrue(rtl.place(1f).x < 960f)
        // A touch on the orb drawn left of the front finds the next one.
        val next = rtl.place(1f)
        assertEquals(1, rtl.hit(next.x, next.y, 0f, 20, false))
    }
}
