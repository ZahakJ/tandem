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
 * The orbs ride a ring seen in perspective from a little above: an ellipse whose nearest point, at the bottom, is
 * the front. The focused orb sits there, lowest and biggest; its neighbours shrink, dim and rise as they go round
 * towards the back, the last visible ones near the ellipse's widest point, so the row reads as a ring in depth
 * rather than a curved line (Orbit.kt draws the whole ellipse as a faint glowing path, its far side behind the
 * covers). The orbit's position is one float, the "offset", in orbs: offset 3 puts orb 3 at the front, 3.5 sits
 * halfway to orb 4. Everything below is worked out from that one number, so a spin is one animated value read in
 * graphics layers.
 */

/**
 * Where one orb is drawn: its centre in px, its scale (1 = the focused orb's size), its alpha, its depth (higher in
 * front) and how far round towards the back it is ([back]: 0 at the front, 1 at the last visible orb).
 */
class OrbPlace(val x: Float, val y: Float, val scale: Float, val alpha: Float, val depth: Float, val back: Float)

/**
 * The orbit's ring on a stage [width] px wide. The front orb's centre sits at [baseY]; the ring climbs [rise] px
 * towards the last visible orbs. [orb] is the focused orb's diameter; [side] orbs show on each side of it. [mirrored]
 * (right to left) puts the next orbs on the left instead of the right, so Left on the D-pad brings them round.
 */
class OrbitLayout(val width: Float, val baseY: Float, val rise: Float, val orb: Float, val side: Int = 5, val mirrored: Boolean = false) {
    /** How far round the ellipse the last visible orb is (radians from the front): just short of its widest point. */
    private val reach = 1.4f
    /** The angle between neighbouring orbs. */
    private val step = reach / side
    /** Extra room either side of the front orb, which is bigger than the rest. */
    private val gap = orb * 0.12f
    private val sag = 1f - cos(reach)

    /** The ring's half-width, so the last visible orb stays a little inside the stage. */
    val radiusX = (width / 2f - orb * 0.3f - gap) / sin(reach)
    /** The ring's half-height: how flat the perspective makes it. */
    val radiusY = rise / sag
    /** The ring's centre: its far side is [radiusY] above, the front orb's centre [radiusY] below. */
    val centreX: Float get() = width / 2f
    val centreY: Float get() = baseY - radiusY

    /** About how many px one orb's worth of spin is at the front: a finger dragged this far moves the orbit one orb. */
    val pxPerOrb: Float get() = radiusX * sin(step) + gap

    /** The orb [d] orbs from the front (negative: to the left; to the right when [mirrored]). */
    fun place(d: Float): OrbPlace {
        val a = abs(d)
        val angle = (d.coerceIn(-(side + 1f), side + 1f)) * step
        // How far towards the back the orb has gone: 0 at the front, 1 at the last visible orb.
        val back = ((1f - cos(angle)) / sag).coerceIn(0f, 1.4f)
        val across = radiusX * sin(angle) + sign(d) * gap * a.coerceAtMost(1f)
        val x = width / 2f + if (mirrored) -across else across
        val y = baseY - rise * back
        // The front orb swells to FOCUS times its neighbours' size, easing in as it reaches the front; the others
        // shrink with depth, to about two thirds of their size at the last visible one.
        val t = a.coerceAtMost(1f)
        val bump = 1f - t * t * (3f - 2f * t)
        val b = back.coerceAtMost(1f)
        val scale = (1f + (FOCUS - 1f) * bump) * (1f - 0.38f * b) / FOCUS
        val fadeOut = (side + 1f - a).coerceIn(0f, 1f)
        val alpha = (1f - 0.3f * b) * fadeOut
        return OrbPlace(x, y, scale, alpha, -a, b)
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

/**
 * Where one cover of the spotlight's carousel sits: its centre's px from the carousel's middle, its scale (around
 * the bottom edge, so every cover stands on the same floor), its turn in degrees about the vertical axis (positive
 * turns the right edge away) and how much it is dimmed (0 to 1) while it isn't highlighted.
 */
class FanPlace(val x: Float, val scale: Float, val turn: Float, val dim: Float)

/**
 * The spotlight's carousel of covers, all one shape and standing on one floor: the latest game (rank 0) in the
 * middle and biggest, the next ones alternating right and left, each smaller, dimmer and turned a little further
 * away, like the outside of a turning drum. Each tucks a fifth of its width behind the one nearer the middle, so the
 * row reads in depth without the covers piling up. The Continue card stands apart, left of them all.
 */
object Fan {
    /** A cover's height for its width. */
    const val ASPECT = 4f / 3f
    /** The Continue card's size against the middle cover. */
    const val CONTINUE = 0.82f
    /** The room between the Continue card and the carousel, in middle-cover widths. */
    private const val APART = 0.24f

    /** How many cards a fan shows out of [available]: five or three, so there is always a middle, or all of one or two. */
    fun count(available: Int): Int = when {
        available >= 5 -> 5
        available >= 3 -> 3
        else -> available.coerceAtLeast(0)
    }

    /** Which slot rank [rank] takes, counting from the middle: 0, then 1, -1, 2, -2. */
    fun slot(rank: Int): Int = if (rank == 0) 0 else if (rank % 2 == 1) (rank + 1) / 2 else -(rank / 2)

    private fun scaleAt(a: Int) = 1f - 0.17f * a

    /** How far from the middle the cover [a] slots out sits, in middle-cover widths. */
    private fun xAt(a: Int): Float {
        var x = 0f
        for (i in 0 until a) x += (scaleAt(i) + scaleAt(i + 1)) / 2f * 0.8f
        return x
    }

    /** Card [rank] of [n], for covers [cardW] px wide (the middle one's width). */
    fun place(rank: Int, n: Int, cardW: Float): FanPlace {
        if (n == 2) {
            // Two cards have no middle: side by side, the same size, a little apart.
            val s = if (rank == 0) -1f else 1f
            return FanPlace(s * cardW * 0.48f, 0.9f, s * 6f, 0f)
        }
        val s = slot(rank)
        val a = abs(s)
        return FanPlace(sign(s.toFloat()) * xAt(a) * cardW, scaleAt(a), s * 11f, (0.2f * a).coerceAtMost(0.36f))
    }

    /** The Continue card's centre, from the carousel's middle, beside [n] covers [cardW] px wide. */
    fun continueX(n: Int, cardW: Float): Float = -(leftEdge(n) + APART + CONTINUE / 2f) * cardW

    /**
     * How far left of the middle the carousel reaches, in middle-cover widths, with the Continue card when
     * [withContinue]. Orbit shrinks the covers so this fits in half the screen.
     */
    fun reach(n: Int, withContinue: Boolean): Float =
        if (withContinue) leftEdge(n) + APART + CONTINUE else leftEdge(n).coerceAtLeast(0.5f)

    private fun leftEdge(n: Int): Float = when {
        // No covers: an orb's note (its art and a line or two) stands in the middle instead, wider than a cover.
        n <= 0 -> 1.4f
        n == 2 -> 0.48f + 0.45f
        else -> xAt((n - 1) / 2) + scaleAt((n - 1) / 2) / 2f
    }

    /** The ranks of [n] cards from left to right: the order the D-pad walks through them. */
    fun leftToRight(n: Int): List<Int> = (0 until n).sortedBy { slot(it) }
}
