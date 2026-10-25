package io.github.zahakj.tandem.data

/**
 * Editing the play history: changing a session's length or start, deleting and merging sessions, and working out a
 * game's play time, play count and last played again from its sessions. Pure functions on lists, so they can be
 * tested off the device; [HomeStore.editSessions] applies them.
 *
 * A game's totals can hold play that has no session behind it (imported from another frontend, or recorded before
 * Tandem kept sessions). [recompute] keeps that part as it was and replaces only the part the sessions account for.
 */
object PlayHistory {
    /** "Merge with next" is offered when the next session starts at most this long after this one ends. */
    const val MERGE_WINDOW_MS = 10 * 60 * 1000L
    const val MIN_SECONDS = 60L
    const val MAX_SECONDS = 24 * 60 * 60L

    /**
     * How long after a session's end its game's "last played" may lie and still come from that session: the session
     * is recorded when Tandem comes back, and stints merged on recording or capped at six hours end earlier.
     */
    private const val LATE_MS = 6 * 60 * 60 * 1000L + 5 * 60 * 1000L

    fun same(a: PlaySession, b: PlaySession): Boolean = a.k == b.k && a.s == b.s

    /** Oldest first, and no two sessions of one game starting at the same moment (a start is part of its identity). */
    fun ordered(list: List<PlaySession>): List<PlaySession> {
        val seen = HashSet<Pair<String, Long>>()
        return list.sortedBy { it.s }.map { s ->
            var x = s
            while (!seen.add(x.k to x.s)) x = x.copy(s = x.s + 1000)
            x
        }.sortedBy { it.s }
    }

    /** The session that follows [s] in [list] (any game), or null. */
    fun next(list: List<PlaySession>, s: PlaySession): PlaySession? {
        val o = ordered(list)
        val i = o.indexOfFirst { same(it, s) }
        return if (i < 0) null else o.getOrNull(i + 1)
    }

    /** True when [b], the session after [a], is the same game and starts within [MERGE_WINDOW_MS] of [a]'s end. */
    fun canMerge(a: PlaySession, b: PlaySession?): Boolean =
        b != null && a.k == b.k && b.s >= a.s && b.s - a.end <= MERGE_WINDOW_MS

    /** [s] and the session after it become one: [s]'s start, both lengths added. Unchanged when they can't merge. */
    fun mergeNext(list: List<PlaySession>, s: PlaySession): List<PlaySession> {
        val o = ordered(list)
        val i = o.indexOfFirst { same(it, s) }
        if (i < 0) return o
        val a = o[i]
        val b = o.getOrNull(i + 1)
        if (!canMerge(a, b)) return o
        return o.toMutableList().apply {
            this[i] = a.copy(d = a.d + b!!.d)
            removeAt(i + 1)
        }
    }

    fun setLength(list: List<PlaySession>, s: PlaySession, seconds: Long): List<PlaySession> =
        ordered(list.map { if (same(it, s)) it.copy(d = seconds.coerceIn(MIN_SECONDS, MAX_SECONDS)) else it })

    /** Moves [s] to start at [start]. Returns the new list and the session as it is now. */
    fun moveTo(list: List<PlaySession>, s: PlaySession, start: Long): Pair<List<PlaySession>, PlaySession> {
        val rest = list.filterNot { same(it, s) }
        var at = start.coerceAtLeast(0)
        while (rest.any { it.k == s.k && it.s == at }) at += 1000
        val moved = s.copy(s = at)
        return ordered(rest + moved) to moved
    }

    fun delete(list: List<PlaySession>, s: PlaySession): List<PlaySession> = list.filterNot { same(it, s) }

    /** Games whose sessions differ between [before] and [after]. */
    fun changedKeys(before: List<PlaySession>, after: List<PlaySession>): Set<String> {
        val a = before.toSet()
        val b = after.toSet()
        return ((a - b) + (b - a)).mapTo(HashSet()) { it.k }
    }

    /**
     * [meta]'s play time, play count and last played, worked out again after its sessions went from [before] to
     * [after] (both only this game's). Whatever the old totals held beyond the old sessions stays.
     */
    fun recompute(meta: GameMeta, before: List<PlaySession>, after: List<PlaySession>): GameMeta {
        val extraSeconds = (meta.playSeconds - before.sumOf { it.d }).coerceAtLeast(0)
        val extraPlays = (meta.playCount - before.size).coerceAtLeast(0)
        val fromSessions = before.any { meta.lastPlayed in it.s..(it.end + LATE_MS) }
        val extraLast = if (!fromSessions && (extraSeconds > 0 || extraPlays > 0)) meta.lastPlayed else 0L
        return meta.copy(
            playSeconds = extraSeconds + after.sumOf { it.d },
            playCount = extraPlays + after.size,
            lastPlayed = maxOf(after.maxOfOrNull { it.end } ?: 0L, extraLast),
        )
    }
}
