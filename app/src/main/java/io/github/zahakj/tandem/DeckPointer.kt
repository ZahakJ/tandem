package io.github.zahakj.tandem

import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/*
 * The Deck's trackpad (ui/DeckPanel.kt), as maths with no Android in it: a finger's movement on the bottom screen
 * becomes the game pointer's movement on the top one. DeckPointerTest covers it.
 */

/**
 * The game's pointer: a position on the top screen ([width] x [height] pixels) that finger movement pushes around.
 * Injected mouse events carry an absolute position, so the Deck keeps the position itself and moves it relatively,
 * like a laptop trackpad does.
 */
class DeckPointer(val width: Int, val height: Int, startX: Float = width / 2f, startY: Float = height / 2f) {
    /** The position, with the fraction of a pixel the last move didn't reach kept for the next one. */
    var fx: Float = startX.coerceIn(0f, (width - 1).toFloat())
        private set
    var fy: Float = startY.coerceIn(0f, (height - 1).toFloat())
        private set

    val x: Int get() = fx.roundToInt()
    val y: Int get() = fy.roundToInt()

    /**
     * Moves by a finger delta of ([dx], [dy]) pad pixels made over [dtMs] (0 when unknown), with the user's
     * [sensitivity] (1 = a pad pixel per screen pixel when slow) and [acceleration] (how much faster a quick swipe
     * goes, as a fraction on top). Returns true when the whole-pixel position changed.
     */
    fun move(dx: Float, dy: Float, dtMs: Long, sensitivity: Float, acceleration: Float = ACCELERATION): Boolean {
        val before = x to y
        val distance = sqrt(dx * dx + dy * dy)
        val speed = if (dtMs > 0) distance / dtMs else 0f
        val g = gain(speed, sensitivity, acceleration)
        fx = (fx + dx * g).coerceIn(0f, (width - 1).toFloat())
        fy = (fy + dy * g).coerceIn(0f, (height - 1).toFloat())
        return before != (x to y)
    }

    /** Puts the pointer at ([x], [y]) (a layout change, a new game). */
    fun place(x: Float, y: Float) {
        fx = x.coerceIn(0f, (width - 1).toFloat())
        fy = y.coerceIn(0f, (height - 1).toFloat())
    }

    companion object {
        /** How much faster a fast swipe goes than a slow one, at most (as a fraction on top of the sensitivity). */
        const val ACCELERATION = 1.2f

        /** Finger speed, in pad pixels per ms, at which half of the acceleration is reached. */
        private const val HALF_SPEED = 1.5f

        /** The sensitivity settings offered, and how they read. */
        const val MIN_SENSITIVITY = 0.5f
        const val MAX_SENSITIVITY = 3f
        const val SENSITIVITY_STEP = 0.25f
        const val DEFAULT_SENSITIVITY = 1.5f

        /**
         * Screen pixels per pad pixel at finger [speed]: the [sensitivity] when slow, rising smoothly towards
         * `sensitivity * (1 + acceleration)` for a flick, so small corrections stay precise and a swipe across the
         * pad still crosses the screen.
         */
        fun gain(speed: Float, sensitivity: Float, acceleration: Float = ACCELERATION): Float {
            val s = speed.coerceAtLeast(0f)
            return sensitivity * (1f + acceleration * (s / (s + HALF_SPEED)))
        }
    }
}

/** A scroll strip or two-finger drag: finger movement collected into whole wheel notches. */
class DeckScroll(private val notchPx: Float = NOTCH_PX) {
    private var carried = 0f

    /**
     * Adds [dy] pad pixels of finger movement (down is positive) and returns the notches now due: positive for a
     * finger moving down the pad, as content scrolls the way the finger goes; the rest is carried over.
     */
    fun add(dy: Float): Int {
        carried += dy
        val notches = (carried / notchPx).toInt()
        carried -= notches * notchPx
        return notches
    }

    /** A new gesture starts: nothing carried from the last one. */
    fun reset() {
        carried = 0f
    }

    companion object {
        /** Pad pixels per wheel notch. */
        const val NOTCH_PX = 36f
    }
}

/** What a finished trackpad gesture was: a click, a right click, or a drag (nothing more to send). */
object DeckTap {
    enum class Click { None, Left, Right }

    /** A tap lasts at most this long. */
    const val MAX_TAP_MS = 260L

    /**
     * The click a gesture of [fingers] fingers at most, held for [heldMs] and moved [movedPx] in total, counts as:
     * one finger is a left click, two a right click, and a longer or moving touch is a drag, not a click.
     */
    fun classify(fingers: Int, heldMs: Long, movedPx: Float, slopPx: Float, maxTapMs: Long = MAX_TAP_MS): Click = when {
        heldMs > maxTapMs || abs(movedPx) > slopPx -> Click.None
        fingers >= 2 -> Click.Right
        fingers == 1 -> Click.Left
        else -> Click.None
    }
}

/** Key repeat while a key is held: a pause, then a steady rate. */
object DeckRepeat {
    const val FIRST_MS = 400L
    const val NEXT_MS = 45L

    /** How long to wait before repeat number [n] (1 is the first repeat after the press). */
    fun delayMs(n: Int): Long = if (n <= 1) FIRST_MS else NEXT_MS
}
