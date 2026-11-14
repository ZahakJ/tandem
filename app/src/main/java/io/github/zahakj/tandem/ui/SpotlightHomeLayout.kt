package io.github.zahakj.tandem.ui

import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

/*
 * The geometry of the Spotlight home (SpotlightHome.kt), kept free of Compose so it can be tested on its own.
 *
 * The screen splits into a narrow rail of covers on one side and the panel about the highlighted game on the
 * other, with the game's art behind both. The rail is one column whose position is one float, the "offset", in
 * tiles: offset 3 puts tile 3 at the rail's vertical centre, where the highlight sits. Every tile's place, size and
 * fade is worked out from that one number, so a scroll is one animated value read in graphics layers.
 */

/**
 * Where the rail and the panel sit across a screen [width] wide and [height] tall (any unit, dp in practice), with
 * [margin] from the edges. [railLeft] false mirrors the whole layout. A wide screen gives the rail about a fifth and
 * the panel under half, leaving the art a clear band between them; a squarer one (the bottom screen) gives the panel
 * everything the rail leaves.
 */
class SpotlightFrame(val width: Float, val height: Float, val railLeft: Boolean, val margin: Float) {
    val wide: Boolean = width >= height * 1.2f
    val railW: Float = width * if (wide) 0.22f else 0.27f
    val railX: Float = if (railLeft) 0f else width - railW
    val panelW: Float = if (wide) width * 0.45f else width - railW - margin * 2f
    val panelX: Float = if (railLeft) width - margin - panelW else margin

    /** The rail's share of the width, measured from the rail's side: where the art starts to show clearly. */
    val railShare: Float get() = railW / width

    /** How far the panel's near edge (the one facing the rail) is from the rail's side, as a share of the width. */
    val panelNear: Float get() = if (railLeft) panelX / width else 1f - (panelX + panelW) / width
}

/**
 * The rail's column: tiles [pitch] apart, the highlighted one at the centre of a viewport [viewH] tall, with [gap]
 * extra room on each side of it because it is drawn [FOCUS] times bigger. A resting tile (cover and title) is [tileH]
 * tall.
 */
class SpotlightRail(val viewH: Float, val pitch: Float, val gap: Float, val tileH: Float = pitch * 0.8f) {
    val centre: Float = viewH / 2f

    /** The centre of the tile [d] tiles from the highlight (negative: above it). */
    fun y(d: Float): Float = centre + d * pitch + sign(d) * gap * abs(d).coerceAtMost(1f)

    /** The tile's size against a resting tile's: [FOCUS] at the highlight, easing back to 1 by the next tile. */
    fun scale(d: Float): Float {
        val t = abs(d).coerceAtMost(1f)
        return 1f + (FOCUS - 1f) * (1f - t * t * (3f - 2f * t))
    }

    /**
     * Full at the highlight, quieter further out, and gone before any of the tile reaches the viewport's edges: it
     * fades over the last [EDGE_FADE] of a pitch between its near edge and the viewport's, so the clip never shows a
     * cut tile.
     */
    fun alpha(d: Float): Float {
        val y = y(d)
        val half = tileH / 2f * scale(d)
        val room = minOf(y - half, viewH - (y + half))
        val edge = (room / (pitch * EDGE_FADE)).coerceIn(0f, 1f)
        val quiet = (1f - 0.22f * abs(d)).coerceIn(0.45f, 1f)
        return edge * quiet
    }

    /** How many tiles fit between the centre and one edge, plus one coming in. */
    val reach: Int get() = (centre / pitch).toInt() + 2

    /** The tiles worth composing at [offset]: those within [reach] of it. */
    fun visible(offset: Float, count: Int): IntRange {
        if (count <= 0) return IntRange.EMPTY
        val c = offset.roundToInt()
        return (c - reach).coerceAtLeast(0)..(c + reach).coerceAtMost(count - 1)
    }

    /** The tile under a touch at [y] in the viewport for [offset]; -1 for none. */
    fun hit(y: Float, offset: Float, count: Int): Int {
        var best = -1
        var bestDist = Float.MAX_VALUE
        for (i in visible(offset, count)) {
            val d = i - offset
            val half = pitch / 2f * if (abs(d) < 0.5f) FOCUS else 1f
            val dist = abs(y - y(d))
            if (dist <= half && dist < bestDist) { best = i; bestDist = dist }
        }
        return best
    }

    companion object {
        /** The highlighted tile's size against the rest. */
        const val FOCUS = 1.22f

        /** How much of a pitch a tile takes to fade out against the viewport's edge. */
        const val EDGE_FADE = 0.12f

        /** The tile at the highlight for [offset]. */
        fun index(offset: Float, count: Int): Int = if (count <= 0) 0 else offset.roundToInt().coerceIn(0, count - 1)

        /** [by] tiles on from where the rail is heading; it stops at the ends. */
        fun stepped(goal: Float, by: Int, count: Int): Float = (goal.roundToInt() + by).coerceIn(0, (count - 1).coerceAtLeast(0)).toFloat()

        /**
         * How many tiles one D-pad repeat moves after the button has been held [heldMs]: one at first, then more for
         * long lists (the activity already sends repeats faster after a moment; this adds on top for the long haul).
         */
        fun holdStep(heldMs: Long): Int = when {
            heldMs < 1500 -> 1
            heldMs < 3000 -> 2
            else -> 3
        }

        /**
         * Where a finger let go at [offset], moving at [velocity] tiles a second, lands: the nearest tile, carried a
         * little further by a flick (never more than twelve tiles). [still] (Reduce motion): no carry at all.
         */
        fun settle(offset: Float, velocity: Float, count: Int, still: Boolean): Float {
            val carry = if (still) 0f else (velocity * 0.25f).coerceIn(-12f, 12f)
            return (offset + carry).roundToInt().coerceIn(0, (count - 1).coerceAtLeast(0)).toFloat()
        }

        /** A drag past either end gives way, as a rubber band: the further past, the less it follows. */
        fun dragged(offset: Float, by: Float, count: Int): Float {
            val last = (count - 1).coerceAtLeast(0).toFloat()
            val next = offset + by
            val outward = (next < 0f && by < 0f) || (next > last && by > 0f)
            return (offset + if (outward) by * 0.35f else by).coerceIn(-0.6f, last + 0.6f)
        }
    }
}

/** What B does on the Spotlight home, in order: leave clean mode, close the description, go to the top, go home. */
enum class SpotlightBack { Clean, Description, Top, Home, Nothing }

/** The Spotlight home's small rules: when the art changes, how groups go round, what B does, how a cover fits. */
object SpotlightSteps {
    /** How long the highlight rests before the art behind it changes, so skimming past games loads none of them. */
    const val ART_SETTLE_MS = 150L

    /** The art's wait: none for the first picture on screen, [ART_SETTLE_MS] for every change after it. */
    fun artDelay(first: Boolean): Long = if (first) 0L else ART_SETTLE_MS

    /** L1 ([by] -1) or R1 (1): the group before or after [current] of [count], going round. */
    fun cycle(current: Int, by: Int, count: Int): Int {
        if (count <= 0) return 0
        return ((current + by) % count + count) % count
    }

    /** What B does with the rail at [index] in group [group], when [home] is the group B returns to. */
    fun back(clean: Boolean, expanded: Boolean, index: Int, group: Int, home: Int): SpotlightBack = when {
        clean -> SpotlightBack.Clean
        expanded -> SpotlightBack.Description
        index > 0 -> SpotlightBack.Top
        group != home -> SpotlightBack.Home
        else -> SpotlightBack.Nothing
    }

    /**
     * A cover's drawn size inside a tile box [boxW] x [boxH], for a cover [aspect] (width / height) wide: as big as
     * fits, so tall boxes and wide ones keep their shape. Unknown or odd shapes are held to the real range of boxes.
     */
    fun coverSize(aspect: Float?, boxW: Float, boxH: Float): Pair<Float, Float> {
        val a = (aspect ?: 0.66f).coerceIn(0.56f, 1.5f)
        return if (boxW / boxH > a) (boxH * a) to boxH else boxW to (boxW / a)
    }
}

/**
 * The clamped story's slow scroll, as a function of the time since the highlight came to rest: it waits [WAIT_MS],
 * scrolls the [overflow] (what the clamp hides) at [speed] per second, linearly, holds the end [HOLD_MS], eases back
 * to the top over [RETURN_MS], and goes round again. Positions are in the units of [overflow] and [speed] (pixels).
 */
object StoryDrift {
    const val WAIT_MS = 2500L
    const val HOLD_MS = 3000L
    const val RETURN_MS = 900L

    /** The scroll speed, in dp a second: slow enough to read along. */
    const val SPEED_DP = 18f

    private fun scrollMs(overflow: Float, speed: Float): Long = (overflow / speed * 1000f).toLong().coerceAtLeast(1L)

    /** One round: the wait, the scroll, the hold and the way back. */
    fun cycleMs(overflow: Float, speed: Float): Long = WAIT_MS + scrollMs(overflow, speed) + HOLD_MS + RETURN_MS

    /** How far down the story is [elapsed] ms after the highlight rested. Nothing to scroll: always the top. */
    fun position(elapsed: Long, overflow: Float, speed: Float): Float {
        if (overflow <= 0f || speed <= 0f || elapsed <= 0L) return 0f
        val scroll = scrollMs(overflow, speed)
        var t = elapsed % cycleMs(overflow, speed)
        if (t < WAIT_MS) return 0f
        t -= WAIT_MS
        if (t < scroll) return overflow * t / scroll
        t -= scroll
        if (t < HOLD_MS) return overflow
        t -= HOLD_MS
        val p = t.toFloat() / RETURN_MS
        return overflow * (1f - p * p * (3f - 2f * p))
    }

    /** How long the story stays where it is from [elapsed] on: the rest of a wait or a hold, else 0 (it is moving). */
    fun stillFor(elapsed: Long, overflow: Float, speed: Float): Long {
        if (overflow <= 0f || speed <= 0f) return Long.MAX_VALUE
        val scroll = scrollMs(overflow, speed)
        val t = elapsed.coerceAtLeast(0L) % cycleMs(overflow, speed)
        return when {
            t < WAIT_MS -> WAIT_MS - t
            t < WAIT_MS + scroll -> 0L
            t < WAIT_MS + scroll + HOLD_MS -> WAIT_MS + scroll + HOLD_MS - t
            else -> 0L
        }
    }
}
