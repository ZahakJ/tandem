package io.github.zahakj.tandem.ui

/*
 * The rules of the Stage home (StageHome.kt), as plain Kotlin so they are tested (StageLayoutTest): which cover a page
 * lands on, how its row scrolls, what L2 / R2 and B do there.
 */
object StageLayout {
    /**
     * The cover a page lands on when L1 / R1 bring it in: the one remembered for that page when it is still there,
     * else the most recently played ([recent], a key), else the first. -1 for an empty page.
     */
    fun landing(keys: List<String>, remembered: String?, recent: String?): Int = when {
        keys.isEmpty() -> -1
        remembered != null && remembered in keys -> keys.indexOf(remembered)
        recent != null && recent in keys -> keys.indexOf(recent)
        else -> 0
    }

    /** How many covers sit before the highlighted one once the row scrolls: about a third of the row's room. */
    fun slot(rowWidth: Float, pitch: Float): Int = if (pitch <= 0f) 0 else ((rowWidth / 3f) / pitch).toInt().coerceAtLeast(0)

    /**
     * The row's scroll, in covers, for the highlight at [offset] (a cover index, moving between two while it glides):
     * none until the highlight passes [slot], then it stays [slot] covers from the start.
     */
    fun scroll(offset: Float, slot: Int): Float = (offset - slot).coerceAtLeast(0f)

    /** Left / Right: the cover [by] along, stopping at the ends (no wrapping round). */
    fun step(index: Int, by: Int, count: Int): Int = if (count <= 0) -1 else (index + by).coerceIn(0, count - 1)

    /** The covers worth composing for a row scrolled to [scroll] that shows [shown] at a time: a few either side. */
    fun window(scroll: Float, shown: Int, count: Int): IntRange {
        if (count <= 0) return IntRange.EMPTY
        val first = (scroll.toInt() - 2).coerceAtLeast(0)
        return first..minOf(count - 1, scroll.toInt() + shown + 2)
    }

    /** A title's letter for L2 / R2: A to Z, or # for anything else (digits, symbols, other scripts). */
    fun letter(title: String): Char {
        val c = title.trimStart().firstOrNull()?.uppercaseChar() ?: return '#'
        return if (c in 'A'..'Z') c else '#'
    }

    /**
     * L2 (-1) / R2 (+1) in a row of [titles]: the first cover of the letter after, or the start of this letter (then
     * the letter before). In a row of no more than [page] covers, simply the first or the last.
     */
    fun letterJump(titles: List<String>, index: Int, by: Int, page: Int): Int {
        val n = titles.size
        if (n == 0) return -1
        val i = index.coerceIn(0, n - 1)
        if (n <= page) return if (by > 0) n - 1 else 0
        fun startOf(j: Int): Int { var k = j; while (k > 0 && letter(titles[k - 1]) == letter(titles[j])) k--; return k }
        return if (by > 0) {
            var j = i
            while (j < n && letter(titles[j]) == letter(titles[i])) j++
            if (j >= n) n - 1 else j
        } else {
            val start = startOf(i)
            if (start < i) start else if (start == 0) 0 else startOf(start - 1)
        }
    }

    enum class Back { Top, Home, Nothing }

    /** B on a page: back to its first cover; from there, to the home page (Continue); there, nothing more. */
    fun back(index: Int, page: Int, home: Int): Back = when {
        index > 0 -> Back.Top
        page != home -> Back.Home
        else -> Back.Nothing
    }
}
