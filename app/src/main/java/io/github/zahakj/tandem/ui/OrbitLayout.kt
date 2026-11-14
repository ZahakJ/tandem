package io.github.zahakj.tandem.ui

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin

/*
 * The geometry of the Orbit home (Orbit.kt), kept free of Compose so it can be tested on its own.
 *
 * The orbs ride an ellipse seen from slightly above: the focused orb is at the front, the lowest and biggest point,
 * and its neighbours rise and shrink as they go round towards the far side, so a long row reads as a curve. The
 * orbit's position is one float, the "offset", in orbs: offset 3 puts orb 3 at the front, 3.5 sits halfway to orb 4.
 * Everything below is worked out from that one number, so a spin is one animated value read in graphics layers.
 */

/** Where one orb is drawn: its centre in px, its scale (1 = the focused orb's size), its alpha and its depth (higher in front). */
class OrbPlace(val x: Float, val y: Float, val scale: Float, val alpha: Float, val depth: Float)

/**
 * The orbit's track on a stage [width] px wide. The front orb's centre sits at [baseY]; the track climbs [rise] px
 * towards its visible ends. [orb] is the focused orb's diameter; [side] orbs show on each side of it.
 */
class OrbitLayout(val width: Float, val baseY: Float, val rise: Float, val orb: Float, val side: Int = 5) {
    /** How far round the ellipse the last visible orb is (radians from the front). */
    private val reach = 1.25f
    /** The angle between neighbouring orbs. */
    private val step = reach / (side + 0.6f)
    /** Extra room either side of the front orb, which is bigger than the rest. */
    private val gap = orb * 0.12f
    /** The ellipse's half-width, so the last visible orb's centre stays a little inside the stage. */
    private val radius = (width / 2f - orb * 0.35f - gap) / sin(reach)
    private val sag = 1f - cos(reach)

    /** About how many px one orb's worth of spin is at the front: a finger dragged this far moves the orbit one orb. */
    val pxPerOrb: Float get() = radius * sin(step) + gap

    /** The orb [d] orbs from the front (negative: to the left). */
    fun place(d: Float): OrbPlace {
        val a = abs(d)
        val angle = (d.coerceIn(-(side + 1f), side + 1f)) * step
        // How far towards the back the orb has gone: 0 at the front, 1 at the visible ends.
        val back = ((1f - cos(angle)) / sag).coerceIn(0f, 1.4f)
        val x = width / 2f + radius * sin(angle) + sign(d) * gap * a.coerceAtMost(1f)
        val y = baseY - rise * back
        // The front orb swells to FOCUS times its neighbours' size, easing in as it reaches the front.
        val t = a.coerceAtMost(1f)
        val bump = 1f - t * t * (3f - 2f * t)
        val scale = (1f + (FOCUS - 1f) * bump) * (1f - 0.45f * back.coerceAtMost(1f)) / FOCUS
        val fadeOut = (side + 1f - a).coerceIn(0f, 1f)
        val alpha = (1f - 0.5f * back.coerceAtMost(1f)) * fadeOut
        return OrbPlace(x, y, scale, alpha, -a)
    }

    /** The orb under a touch at ([x], [y]), the frontmost one when two overlap; -1 for none. */
    fun hit(x: Float, y: Float, offset: Float, count: Int, wraps: Boolean): Int {
        var best = -1
        var bestDepth = Float.NEGATIVE_INFINITY
        for (i in visible(offset, count, wraps)) {
            val p = place(distance(i, offset, count, wraps))
            if (p.alpha < 0.15f) continue
            // A little more than the drawn circle, so small orbs near the ends stay easy to hit.
            val r = orb / 2f * p.scale + orb * 0.08f
            val dx = x - p.x
            val dy = y - p.y
            if (dx * dx + dy * dy <= r * r && p.depth > bestDepth) { best = i; bestDepth = p.depth }
        }
        return best
    }

    /** The orbs worth drawing for [offset]: those within reach of the front, each once. */
    fun visible(offset: Float, count: Int, wraps: Boolean): List<Int> {
        if (count <= 0) return emptyList()
        val centre = offset.roundToInt()
        val out = ArrayList<Int>(side * 2 + 3)
        for (k in -(side + 1)..(side + 1)) {
            val i = centre + k
            val idx = if (wraps) floorMod(i, count) else i
            if (idx in 0 until count && idx !in out) out += idx
        }
        return out
    }

    companion object {
        /** The focused orb's size against its neighbours'. */
        const val FOCUS = 1.6f

        /** An orbit wraps round when it has enough orbs that the seam is always out of sight. */
        fun wraps(count: Int, side: Int = 5): Boolean = count >= side * 2 + 3

        /** How many orbs orb [i] is from the front at [offset]; with [wraps], the short way round. */
        fun distance(i: Int, offset: Float, count: Int, wraps: Boolean): Float {
            val d = i - offset
            if (!wraps || count <= 0) return d
            val m = d - floor(d / count) * count
            return if (m >= count / 2f) m - count else m
        }

        /** The orb at the front for [offset]. */
        fun index(offset: Float, count: Int, wraps: Boolean): Int {
            if (count <= 0) return 0
            val i = offset.roundToInt()
            return if (wraps) floorMod(i, count) else i.coerceIn(0, count - 1)
        }

        /**
         * Where the offset should go to put orb [to] at the front, starting from [offset]: the short way round when it
         * [wraps], so going from the last orb to the first is one step on, not a spin back through all of them.
         */
        fun target(offset: Float, to: Int, count: Int, wraps: Boolean): Float {
            if (!wraps) return to.toFloat()
            return offset + distance(to, offset, count, true)
        }

        /** One D-pad step ([by] = -1 or 1) from the offset the orbit is heading to; it stops at the ends unless it [wraps]. */
        fun stepped(goal: Float, by: Int, count: Int, wraps: Boolean): Float {
            val next = goal.roundToInt() + by
            return if (wraps) next.toFloat() else next.coerceIn(0, (count - 1).coerceAtLeast(0)).toFloat()
        }

        /**
         * Where a finger let go at [offset], moving at [velocity] orbs a second, lands: the nearest orb, carried a
         * little further by a quick flick (never more than eight orbs). [still] (Reduce motion): no carry at all.
         */
        fun settle(offset: Float, velocity: Float, count: Int, wraps: Boolean, still: Boolean): Float {
            val carry = if (still) 0f else (velocity * 0.22f).coerceIn(-8f, 8f)
            val landed = (offset + carry).roundToInt()
            return if (wraps) landed.toFloat() else landed.coerceIn(0, (count - 1).coerceAtLeast(0)).toFloat()
        }

        /**
         * L1 ([dir] -1) or R1 (1): the first orb of the group before or after the one orb [index] is in, going round.
         * [groups] gives each orb's group, with each group's orbs together. Stays put with only one group.
         */
        fun groupJump(groups: List<Int>, index: Int, dir: Int): Int {
            if (groups.isEmpty()) return 0
            val starts = groups.indices.filter { it == 0 || groups[it] != groups[it - 1] }
            if (starts.size < 2) return index.coerceIn(0, groups.size - 1)
            val here = starts.indexOfLast { it <= index }.coerceAtLeast(0)
            return starts[floorMod(here + dir, starts.size)]
        }

        private fun floorMod(a: Int, n: Int): Int = ((a % n) + n) % n
    }
}

/** Where one card of the spotlight's fan sits, from the fan's centre: px, degrees of tilt and scale. */
class FanPlace(val x: Float, val y: Float, val rotation: Float, val scale: Float)

/**
 * The spotlight's fan of covers, like cards in a hand: the latest game (rank 0) in the middle and a little larger,
 * the next ones alternating right and left, each tilted a few degrees more and dropping a little lower.
 */
object Fan {
    /** How many cards a fan shows out of [available]: five or three, so there is always a middle, or all of one or two. */
    fun count(available: Int): Int = when {
        available >= 5 -> 5
        available >= 3 -> 3
        else -> available.coerceAtLeast(0)
    }

    /** Which slot rank [rank] takes, counting from the middle: 0, then 1, -1, 2, -2. */
    fun slot(rank: Int): Int = if (rank == 0) 0 else if (rank % 2 == 1) (rank + 1) / 2 else -(rank / 2)

    /** Card [rank] of [n], for cards [cardW] px wide. */
    fun place(rank: Int, n: Int, cardW: Float): FanPlace {
        // Two cards have no middle: shift them so the pair is centred.
        val s = slot(rank) - if (n == 2) 0.5f else 0f
        val a = abs(s)
        return FanPlace(
            x = s * cardW * 0.8f,
            y = a * a * cardW * 0.07f,
            rotation = s * 5f,
            scale = if (rank == 0) 1.16f else 1f - 0.05f * a,
        )
    }

    /** The ranks of [n] cards from left to right: the order the D-pad walks through them. */
    fun leftToRight(n: Int): List<Int> = (0 until n).sortedBy { slot(it) }
}
