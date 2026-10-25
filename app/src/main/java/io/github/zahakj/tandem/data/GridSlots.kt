package io.github.zahakj.tandem.data

/**
 * Where tiles sit on the icon grid (and inside a home folder): a list of slots in reading order, "" for an empty
 * slot. Slot `i` sits at line `i / lanes`, lane `i % lanes`: a sideways grid has a lane per row and a line per
 * column, an up-and-down grid a lane per column. Keys that aren't on the grid right now (a hidden system, an app that
 * was uninstalled) keep their slot and show as a gap, so they come back to the same place.
 *
 * A widget can cover more than one cell ([Extent]): its key sits in its first cell (the top left one), and the other
 * cells it covers stay empty in the list but are taken, so nothing else goes there. The functions that place things
 * take the extents as a map; keys not in it cover one cell.
 *
 * Plain lists in, plain lists out, so it is tested without Android (GridSlotsTest).
 */
object GridSlots {
    const val EMPTY = ""

    /** The most slots a grid keeps: 200 columns of 5 rows, more than anyone fills. */
    const val MAX = 1000

    /** A multi-cell item in slot terms: [lines] along the scroll and [lanes] across it. */
    data class Extent(val lines: Int, val lanes: Int) {
        val single: Boolean get() = lines <= 1 && lanes <= 1
    }

    val ONE = Extent(1, 1)

    /** [span] (columns by rows on screen) in slot terms: a sideways grid's lanes are rows, an up-and-down grid's columns. */
    fun extent(span: GridSpan, vertical: Boolean): Extent =
        if (vertical) Extent(span.h.coerceAtLeast(1), span.w.coerceAtLeast(1)) else Extent(span.w.coerceAtLeast(1), span.h.coerceAtLeast(1))

    /** [key]'s extent, never wider than the grid. */
    fun extentOf(ext: Map<String, Extent>, key: String, lanes: Int): Extent {
        val e = ext[key] ?: return ONE
        return Extent(e.lines.coerceAtLeast(1), e.lanes.coerceIn(1, lanes.coerceAtLeast(1)))
    }

    /**
     * The slots an item of extent [e] covers from slot [at], its first cell first; null when it runs past the last
     * lane, or (with [page] lines a page, the paged grid) over the edge of a page.
     */
    fun cells(at: Int, lanes: Int, e: Extent, page: Int = 0): List<Int>? {
        val l = lanes.coerceAtLeast(1)
        if (at < 0) return null
        val lane = at % l
        val line = at / l
        if (lane + e.lanes > l) return null
        // Never longer than a page (a page one column wide), so it always has somewhere to go.
        val lines = if (page > 0) minOf(e.lines, page) else e.lines
        if (page > 0 && line % page + lines > page) return null
        val out = ArrayList<Int>(lines * e.lanes)
        for (dl in 0 until lines) for (dk in 0 until e.lanes) {
            val c = (line + dl) * l + lane + dk
            if (c >= MAX) return null
            out.add(c)
        }
        return out
    }

    /**
     * For each slot, the slot of the item covering it (its own for a one-cell tile), or -1 when nothing does. Long
     * enough to reach the last covered cell.
     */
    fun owners(slots: List<String>, lanes: Int, ext: Map<String, Extent> = emptyMap(), page: Int = 0): IntArray {
        var size = slots.size
        val covers = ArrayList<Pair<Int, List<Int>>>()
        for ((i, k) in slots.withIndex()) {
            if (k.isEmpty()) continue
            val e = extentOf(ext, k, lanes)
            val c = if (e.single) listOf(i) else cells(i, lanes, e, page) ?: listOf(i)
            covers.add(i to c)
            size = maxOf(size, c.last() + 1)
        }
        val out = IntArray(size) { -1 }
        for ((i, c) in covers) for (x in c) if (out[x] < 0) out[x] = i
        return out
    }

    /**
     * [slots] with room for every multi-cell item. Items are taken in reading order and keep their slot when all their
     * cells are free; the rest (a tile under a widget, a widget that would run past the last lane) move to the first
     * slot from their own where they fit. Covered cells stay empty.
     */
    fun settle(slots: List<String>, lanes: Int, ext: Map<String, Extent>, page: Int = 0): List<String> {
        if (ext.isEmpty()) return slots
        val taken = HashSet<Int>()
        val out = MutableList(slots.size) { EMPTY }
        val left = ArrayList<Pair<Int, String>>()
        for ((i, k) in slots.withIndex()) {
            if (k.isEmpty()) continue
            val c = cells(i, lanes, extentOf(ext, k, lanes), page)
            if (c != null && c.none { it in taken }) {
                taken.addAll(c)
                out[i] = k
            } else {
                left.add(i to k)
            }
        }
        if (left.isEmpty()) return slots
        for ((from, k) in left) {
            val e = extentOf(ext, k, lanes)
            var at = from
            while (at < MAX) {
                val c = cells(at, lanes, e, page)
                if (c != null && c.none { it in taken }) {
                    taken.addAll(c)
                    while (out.size <= at) out.add(EMPTY)
                    out[at] = k
                    break
                }
                at++
            }
        }
        return trim(out)
    }

    /** A slot whose owner shows no tile ([present] given) and is one cell: a gap that can be built over. */
    private fun weak(slots: List<String>, o: Int, ext: Map<String, Extent>, lanes: Int, present: Set<String>?): Boolean =
        present != null && slots[o] !in present && extentOf(ext, slots[o], lanes).single

    /** True when [key] (at its extent) can go in slot [at]: every cell it needs is free, or already its own. */
    fun fits(slots: List<String>, lanes: Int, ext: Map<String, Extent>, key: String, at: Int, present: Set<String>? = null, page: Int = 0): Boolean {
        val c = cells(at, lanes, extentOf(ext, key, lanes), page) ?: return false
        val own = owners(slots, lanes, ext, page)
        return c.all { x ->
            val o = own.getOrElse(x) { -1 }
            o < 0 || slots[o] == key || weak(slots, o, ext, lanes, present)
        }
    }

    /** The first slot from [from] on where an item of extent [e] fits (it may be past the end). */
    fun firstFit(slots: List<String>, lanes: Int, ext: Map<String, Extent>, from: Int, e: Extent = ONE, present: Set<String>? = null, page: Int = 0): Int {
        val own = owners(slots, lanes, ext, page)
        val need = Extent(e.lines, e.lanes.coerceIn(1, lanes.coerceAtLeast(1)))
        var at = from.coerceAtLeast(0)
        while (at < MAX) {
            val c = cells(at, lanes, need, page)
            if (c != null && c.all { x -> own.getOrElse(x) { -1 }.let { o -> o < 0 || weak(slots, o, ext, lanes, present) } }) return at
            at++
        }
        return MAX
    }

    /**
     * Move mode: [key] one cell along ([dLine] lines, [dLane] lanes). A one-cell tile trades places with what is there,
     * hopping over a widget in the way. A widget takes its new cells, and the tiles that were in them fill the cells it
     * left. Null when it can't go that way: an edge, or another widget it can't pass.
     */
    fun step(slots: List<String>, lanes: Int, ext: Map<String, Extent>, key: String, dLine: Int, dLane: Int, page: Int = 0): List<String>? {
        val l = lanes.coerceAtLeast(1)
        val from = slots.indexOf(key)
        if (from < 0) return null
        val own = owners(slots, l, ext, page)
        fun owner(x: Int) = own.getOrElse(x) { -1 }
        fun wide(o: Int) = o >= 0 && !extentOf(ext, slots[o], l).single
        val e = extentOf(ext, key, l)
        val out = slots.toMutableList()
        fun grow(to: Int) { while (out.size <= to) out.add(EMPTY) }
        if (e.single) {
            var line = from / l
            var lane = from % l
            var to: Int
            do {
                line += dLine; lane += dLane
                if (line < 0 || lane !in 0 until l) return null
                to = line * l + lane
                if (to >= MAX) return null
            } while (wide(owner(to)))
            grow(to)
            out[from] = out[to]
            out[to] = key
            return trim(out)
        }
        var line = from / l + dLine
        val lane = from % l + dLane
        if (page > 0 && dLine != 0 && line >= 0 && line % page + minOf(e.lines, page) > page) {
            // Over the edge of a page: on to the next page's first column, or back to the last that fits on the one before.
            line = if (dLine > 0) (line / page + 1) * page else line / page * page + page - minOf(e.lines, page)
        }
        if (line < 0 || lane < 0) return null
        val to = line * l + lane
        val old = cells(from, l, e, page) ?: return null
        val new = cells(to, l, e, page) ?: return null
        val oldSet = old.toHashSet()
        val newSet = new.toHashSet()
        val gained = new.filter { it !in oldSet }
        val freed = old.filter { it !in newSet }
        // What sits in the cells it takes: nothing, or one-cell tiles (another widget stops it).
        val displaced = ArrayList<String>()
        for (x in gained) {
            val o = owner(x)
            if (o < 0) { displaced.add(EMPTY); continue }
            if (wide(o)) return null
            displaced.add(slots[o])
        }
        grow(maxOf(new.last(), old.last()))
        for (x in gained) out[x] = EMPTY
        out[from] = EMPTY
        out[to] = key
        for ((n, x) in freed.withIndex()) out[x] = displaced.getOrElse(n) { EMPTY }
        return trim(out)
    }

    /**
     * [key] given extent [to], its first cell staying put. One-cell tiles in the cells it now needs move to the first
     * free slots after it. Null when it can't: it would run past the last lane, or into another widget.
     */
    fun resize(slots: List<String>, lanes: Int, ext: Map<String, Extent>, key: String, to: Extent, page: Int = 0): List<String>? {
        val l = lanes.coerceAtLeast(1)
        val at = slots.indexOf(key)
        if (at < 0 || to.lanes > l) return null
        val c = cells(at, l, to, page) ?: return null
        val own = owners(slots, l, ext, page)
        for (x in c) {
            val o = own.getOrElse(x) { -1 }
            if (o >= 0 && slots[o] != key && !extentOf(ext, slots[o], l).single) return null
        }
        return settle(slots, l, ext + (key to to), page)
    }

    /** [slots] without its empty slots at the end (they are never saved). */
    fun trim(slots: List<String>): List<String> {
        var n = slots.size
        while (n > 0 && slots[n - 1].isEmpty()) n--
        return if (n == slots.size) slots else slots.subList(0, n).toList()
    }

    /** True when some empty slot sits before the last tile. */
    fun hasGaps(slots: List<String>): Boolean = trim(slots).any { it.isEmpty() }

    /**
     * [slots] arranged for [stored] lanes, shown with [lanes]. Each tile keeps its line and lane when every one of them
     * (widgets whole) still fits; otherwise (or when nothing was placed on purpose, so there are no gaps) they flow in
     * reading order, gaps included, as the packed grid always did.
     */
    fun relayout(slots: List<String>, stored: Int, lanes: Int, ext: Map<String, Extent> = emptyMap()): List<String> {
        val s = trim(slots)
        if (stored <= 0 || lanes <= 0 || stored == lanes || !hasGaps(s) && s.none { ext[it]?.single == false }) return s
        if (s.indices.any { s[it].isNotEmpty() && it % stored + extentOf(ext, s[it], lanes).lanes > lanes }) return s
        val out = ArrayList<String>()
        for ((i, k) in s.withIndex()) {
            if (k.isEmpty()) continue
            val at = (i / stored) * lanes + i % stored
            while (out.size <= at) out.add(EMPTY)
            out[at] = k
        }
        return out
    }

    /**
     * What a grid of [lanes] shows: the stored keys at their places (keys not in [present] stay, the grid shows them
     * as gaps) and anything in [present] that has no place yet after the last slot, in [present]'s order. A
     * [stored] lane count of 0 is an arrangement from before slots: its missing keys close up, as they used to.
     * Widgets ([ext]) then get the room they need ([settle]).
     */
    fun shown(slots: List<String>, stored: Int, lanes: Int, present: List<String>, ext: Map<String, Extent> = emptyMap(), page: Int = 0): List<String> {
        val here = present.toHashSet()
        val seen = HashSet<String>()
        val base = slots.map { k ->
            when {
                k.isEmpty() -> EMPTY
                !seen.add(k) -> EMPTY
                else -> k
            }
        }.let { if (stored <= 0) it.filter { k -> k.isEmpty() || k in here } else it }
        val out = relayout(base, stored, lanes, ext).toMutableList()
        for (k in present) if (k !in seen) { out.add(k); seen.add(k) }
        return settle(out, lanes, ext, page)
    }

    /** True when slot [i] of [slots] shows no tile: empty, past the end, or holding a key not in [present]. */
    fun free(slots: List<String>, i: Int, present: Set<String>): Boolean = i >= slots.size || slots[i].isEmpty() || slots[i] !in present

    /**
     * [key] put in slot [at]. A key already elsewhere in [slots] leaves a gap there; a key not on the grid that held
     * the slot gives it up.
     */
    fun putAt(slots: List<String>, key: String, at: Int): List<String> {
        val out = slots.map { if (it == key) EMPTY else it }.toMutableList()
        if (at !in 0 until MAX) return trim(out + key)
        while (out.size <= at) out.add(EMPTY)
        out[at] = key
        return trim(out)
    }

    /**
     * Several tiles moved at once: [keys] leave their slots, then go to slot [at] and the free slots after it (cells
     * a widget covers are skipped, a widget takes the first place it fits whole), in the order they were in. Keys not
     * in [present] (when given) hold no slot. Tiles that aren't moving stay where they are.
     */
    fun moveMany(slots: List<String>, lanes: Int, ext: Map<String, Extent>, keys: List<String>, at: Int, present: Set<String>? = null, page: Int = 0): List<String> {
        val moving = keys.filter { it.isNotEmpty() }.toHashSet()
        if (moving.isEmpty()) return slots
        val ordered = slots.filter { it in moving }.distinct() + keys.filter { it in moving && it !in slots }.distinct()
        var out: List<String> = slots.map { if (it in moving) EMPTY else it }
        val here = present?.let { it + moving }
        var from = at.coerceAtLeast(0)
        for (k in ordered) {
            val slot = firstFit(out, lanes, ext, from, extentOf(ext, k, lanes), here, page)
            out = putAt(out, k, slot)
            from = slot + 1
        }
        return trim(out)
    }

    /**
     * The D-pad on a grid of [size] cells: one cell from [cell] ([dLine] lines, [dLane] lanes), staying in the same
     * lane (or line), so a row never drifts to its neighbour. [item] is the slot of what has focus (a widget spans
     * several cells, [cell] is the one the D-pad came in on); its own cells are passed through, and a cell a widget
     * covers ([owners]) stands for that widget. Returns the cell landed on, -1 when there is nothing further that way
     * (stay), or null at the grid's edge (focus may leave the grid: the dock, the strip).
     */
    fun stepFocus(size: Int, owners: IntArray, lanes: Int, item: Int, cell: Int, dLine: Int, dLane: Int): Int? {
        val l = lanes.coerceAtLeast(1)
        var line = cell / l
        var lane = cell % l
        while (true) {
            line += dLine; lane += dLane
            if (line < 0 || lane !in 0 until l) return null
            val c = line * l + lane
            if (c >= size) return -1
            val o = owners.getOrElse(c) { -1 }.let { if (it >= 0) it else c }
            if (o != item) return c
        }
    }

    /** Swaps slots [from] and [to] (a tile into a gap leaves a gap behind). */
    fun move(slots: List<String>, from: Int, to: Int): List<String> {
        if (from == to || from !in slots.indices || to !in 0 until MAX) return slots
        val out = slots.toMutableList()
        while (out.size <= to) out.add(EMPTY)
        val k = out[from]
        out[from] = out[to]
        out[to] = k
        return trim(out)
    }

    /** [key]'s slot emptied, leaving a gap. */
    fun clear(slots: List<String>, key: String): List<String> = trim(slots.map { if (it == key) EMPTY else it })

    /** The first slot from [from] on that shows no tile (past the end if none does). */
    fun firstFree(slots: List<String>, from: Int, present: Set<String>? = null): Int {
        var i = from.coerceAtLeast(0)
        while (i < slots.size && !(slots[i].isEmpty() || present != null && slots[i] !in present)) i++
        return i
    }

    /**
     * Every tile packed to the front, in the same order; keys not in [present] (when given) are let go. With widgets
     * ([ext], in a grid of [lanes]) each goes in turn into the first place it fits, so small tiles fill the holes
     * beside a widget.
     */
    fun tidy(slots: List<String>, present: Set<String>? = null, lanes: Int = 1, ext: Map<String, Extent> = emptyMap(), page: Int = 0): List<String> {
        val keys = slots.filter { it.isNotEmpty() && (present == null || it in present) }.distinct()
        if (keys.none { ext[it]?.single == false }) return keys
        val taken = HashSet<Int>()
        val out = ArrayList<String>()
        var start = 0
        for (k in keys) {
            val e = extentOf(ext, k, lanes)
            var at = start
            while (at < MAX) {
                val c = cells(at, lanes, e, page)
                if (c != null && c.none { it in taken }) {
                    taken.addAll(c)
                    while (out.size <= at) out.add(EMPTY)
                    out[at] = k
                    break
                }
                at++
            }
            while (start in taken) start++
        }
        return trim(out)
    }

    /**
     * How many slots a grid shows for [slots] laid out in [lanes]: the used lines (widgets whole), at least
     * [minEmpty] empty lines after the last tile, at least [atLeast] lines, and, with [page] lines a page, whole pages.
     */
    fun shownCount(slots: List<String>, lanes: Int, minEmpty: Int = 1, atLeast: Int = 0, page: Int = 0, ext: Map<String, Extent> = emptyMap()): Int {
        val l = lanes.coerceAtLeast(1)
        val t = trim(slots)
        var used = (t.size + l - 1) / l
        if (ext.isNotEmpty()) for ((i, k) in t.withIndex()) if (k.isNotEmpty()) ext[k]?.let { used = maxOf(used, i / l + it.lines) }
        var lines = maxOf(used + minEmpty, atLeast)
        if (page > 0) lines = (lines + page - 1) / page * page
        return (lines * l).coerceAtMost(MAX / l * l)
    }
}
