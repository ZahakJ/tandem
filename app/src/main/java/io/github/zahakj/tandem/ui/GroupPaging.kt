package io.github.zahakj.tandem.ui

/*
 * L1 and R1 on Home: the previous or next group, on every layout. A group is what a home is made of: Continue (the
 * game running or last played), each system with games, each collection (Favourites, Most played, your own, the
 * genres), then the apps. Each layout shows the step its own way:
 *
 * - Shelves: the shelf above or below (each shelf holds a kind of group), stopping at the first and last.
 * - Carousel, List, Console: the card or row before or after (each one is a group), wrapping round.
 * - Orbit: the ring turns one orb; L2 / R2 (on the ring) jump to the first system, collection or app instead.
 * - Spotlight and Stage: the rail or the stage of the group before or after, wrapping round.
 * - Icon grid: the shoulders zoom. The grid is laid out by hand and has no groups to page; zoom is what people know
 *   it for, and the strip says so.
 *
 * R1 is always the next group and L1 the one before, in every language: in right to left the groups run from the
 * right, so the next one is on the left, and the strip showing them (GroupStrip.kt) is mirrored with them. Paging
 * only ever moves what is on screen: never a layout hidden under a popup, a folder or the Start panel ([free]).
 * The rules are plain Kotlin, tested in GroupPagingTest.
 */
object GroupPaging {
    const val CONTINUE = 0
    const val SYSTEMS = 1
    const val COLLECTIONS = 2
    const val APPS = 3

    /** Which part of the home a home key is in: Continue, the systems, the collections (genres too) or the apps. */
    fun section(key: String): Int = when {
        key.startsWith("c:") || key.startsWith("recent:") || key == "continue" || key == "col:continue" -> CONTINUE
        key.startsWith("s:") -> SYSTEMS
        key.startsWith("col:") || key == "genres" || key.startsWith("genre:") -> COLLECTIONS
        else -> APPS
    }

    /**
     * The group [by] steps from [current] in [keys]: round from the last to the first when [wrap], else stopping at the
     * ends. Null when there is nowhere to go. A [current] that isn't a group (the dock has the highlight) starts from
     * the first group going forward and the last going back.
     */
    fun step(keys: List<String>, current: String?, by: Int, wrap: Boolean = true): String? {
        if (keys.isEmpty() || by == 0) return null
        val n = keys.size
        val i = keys.indexOf(current)
        if (i < 0) return if (by > 0) keys.first() else keys.last()
        val j = if (wrap) Math.floorMod(i + by, n) else (i + by).coerceIn(0, n - 1)
        return keys[j].takeIf { j != i }
    }

    /**
     * The first group of the section before or after [current]'s (Continue, systems, collections, apps), wrapping
     * round: L2 / R2 on Orbit's ring. Null with only one section.
     */
    fun sectionJump(keys: List<String>, current: String?, by: Int): String? {
        val starts = keys.indices.filter { it == 0 || section(keys[it]) != section(keys[it - 1]) }
        if (starts.size < 2 || by == 0) return null
        val i = keys.indexOf(current).coerceAtLeast(0)
        val here = starts.indexOfLast { it <= i }.coerceAtLeast(0)
        return keys[starts[Math.floorMod(here + by, starts.size)]]
    }

    /**
     * The groups either side of [current], for the strip: the one L1 goes to, the one R1 goes to (null where it stops,
     * or with a single group). With [wrap] and only two groups both sides name the other one.
     */
    fun around(keys: List<String>, current: String?, wrap: Boolean = true): Pair<String?, String?> =
        step(keys, current, -1, wrap) to step(keys, current, 1, wrap)

    /**
     * Which of [keys] a strip of [room] chips shows so that [current] stays in view, as near the middle as the ends
     * allow: the first index and how many. Every key when they all fit.
     */
    fun window(count: Int, current: Int, room: Int): IntRange {
        if (count <= 0 || room <= 0) return IntRange.EMPTY
        if (count <= room) return 0 until count
        val start = (current.coerceIn(0, count - 1) - room / 2).coerceIn(0, count - room)
        return start until start + room
    }

    /** The last group the highlight was on (Carousel, List, Console), for the shoulders pressed from the dock. */
    var last: String? = null

    /** Home is showing with nothing over it, so the shoulders may page it. */
    val free: Boolean
        get() = Nav.route == Route.Home && !Nav.overlayOpen && Layers.top == null && FolderPopup.route == null &&
            IconGrid.openFolder == null && !LayoutSwitch.open && !StartMenu.open
}
