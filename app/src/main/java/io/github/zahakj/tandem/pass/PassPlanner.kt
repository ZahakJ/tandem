package io.github.zahakj.tandem.pass

/**
 * When Tandem Pass does what, kept small for the battery and plain for the tests (PassPlannerTest):
 *
 * - **Rotation.** The beacon token, and the advertisement with it, change every [ROTATE_MS] give or take
 *   [ROTATE_JITTER_MS] (a fixed rhythm would itself be a fingerprint).
 * - **Scanning** runs in short windows ([Duty]): a few seconds every half minute or so while Tandem is in front, much
 *   less while a game plays. Advertising, which costs little, stays on.
 * - **Exchanges** are capped at [MAX_PER_HOUR] per rolling hour, and a beacon token met once isn't connected to again.
 * - **Who connects.** Both sides usually see each other; the one with the smaller token connects at once, the other
 *   waits [FALLBACK_MS] and connects only if nothing has come of it (the first may not have seen it at all).
 */
class PassPlanner(
    private val maxPerHour: Int = MAX_PER_HOUR,
) {
    companion object {
        const val ROTATE_MS = 15L * 60 * 1000
        const val ROTATE_JITTER_MS = 2L * 60 * 1000
        const val MAX_PER_HOUR = 12
        const val HOUR_MS = 60L * 60 * 1000
        const val FALLBACK_MS = 40_000L
        /** A token seen is remembered a little longer than it can live. */
        const val TOKEN_MEMORY_MS = ROTATE_MS + ROTATE_JITTER_MS + 5 * 60 * 1000
        /** A failed connection to a token is tried again after this. */
        const val RETRY_MS = 90_000L
        const val MAX_TRIES = 3

        /** When to rotate next, from [now] and a [random] number in 0..1. */
        fun nextRotation(now: Long, random: Double): Long =
            now + ROTATE_MS - ROTATE_JITTER_MS + (random.coerceIn(0.0, 1.0) * 2 * ROTATE_JITTER_MS).toLong()

        /** Unsigned, byte by byte: the side whose token is smaller connects first. */
        fun initiates(mine: ByteArray, theirs: ByteArray): Boolean {
            for (i in 0 until minOf(mine.size, theirs.size)) {
                val a = mine[i].toInt() and 0xff
                val b = theirs[i].toInt() and 0xff
                if (a != b) return a < b
            }
            return mine.size <= theirs.size
        }
    }

    /** How the scanner duty-cycles: [onMs] scanning, then [offMs] resting. */
    enum class Duty(val onMs: Long, val offMs: Long) {
        /** Tandem on screen. */
        Front(8_000, 22_000),
        /** A game in front, with Pass while playing on (the foreground notification shows). */
        Background(6_000, 84_000),
    }

    private class Seen(var firstAt: Long, var lastAt: Long, var tries: Int = 0, var lastTry: Long = 0, var done: Boolean = false)

    private val seen = HashMap<String, Seen>()
    private val exchanges = ArrayDeque<Long>()

    /** Exchanges started in the hour before [now]. */
    fun recent(now: Long): Int { prune(now); return exchanges.size }

    private fun prune(now: Long) {
        while (exchanges.isNotEmpty() && now - exchanges.first() >= HOUR_MS) exchanges.removeFirst()
        seen.entries.removeAll { now - it.value.lastAt > TOKEN_MEMORY_MS }
    }

    /**
     * A Tandem was heard with beacon [theirs] (hex; for an advertisement without a token, the address it came from).
     * True when we should connect to it now: not met under this token yet, under the hourly cap, our turn (or the
     * other side has had its chance), and not tried too often.
     */
    fun shouldConnect(theirs: String, mine: ByteArray, theirToken: ByteArray?, now: Long): Boolean {
        prune(now)
        val s = seen.getOrPut(theirs) { Seen(now, now) }
        s.lastAt = now
        if (s.done || s.tries >= MAX_TRIES) return false
        if (s.tries > 0 && now - s.lastTry < RETRY_MS) return false
        if (exchanges.size >= maxPerHour) return false
        val first = theirToken == null || initiates(mine, theirToken)
        return first || now - s.firstAt >= FALLBACK_MS
    }

    /** A connection to [theirs] is starting: it counts against the hour. */
    fun started(theirs: String, now: Long) {
        val s = seen.getOrPut(theirs) { Seen(now, now) }
        s.tries++
        s.lastTry = now
        exchanges.addLast(now)
    }

    /** An exchange with [theirs] finished (either side connected): no more connections to that token. */
    fun finished(theirs: String, now: Long) {
        val s = seen.getOrPut(theirs) { Seen(now, now) }
        s.done = true
        s.lastAt = now
    }

    /** The other side connected to us: it counts against the hour too, so a crowd can't keep the radio busy. */
    fun answered(now: Long): Boolean {
        prune(now)
        if (exchanges.size >= maxPerHour) return false
        exchanges.addLast(now)
        return true
    }

    /** A new rotation: tokens of the last one are still remembered until they expire. */
    fun clear() { seen.clear(); exchanges.clear() }
}
