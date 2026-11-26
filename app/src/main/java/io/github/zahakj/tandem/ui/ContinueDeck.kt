package io.github.zahakj.tandem.ui

/*
 * The games behind the icon grid's wide first card (HomeExtras.kt): the ones running now first (the card is the
 * Resume page then), then the last ones played, a handful at most. L2 / R2 step through them while the card has the
 * highlight, wrapping round, and a row of dots says where you are. A game private mode keeps out of sight is never
 * one of them, running or not. Plain Kotlin, tested in ContinueDeckTest.
 */
object ContinueDeck {
    /** How many games the card steps through. */
    const val MAX = 5

    /**
     * The card's games: [running] first (in their order), then [recent], each once (by [key]), none that [canShow]
     * refuses, [max] at most.
     */
    fun <T> pick(running: List<T>, recent: List<T>, key: (T) -> String, canShow: (T) -> Boolean, max: Int = MAX): List<T> {
        val seen = HashSet<String>()
        val out = ArrayList<T>(max)
        for (g in running.asSequence() + recent.asSequence()) {
            if (out.size >= max) break
            if (canShow(g) && seen.add(key(g))) out += g
        }
        return out
    }

    /** Which of [keys] the card shows: [wanted] while it is still one of them, else the first. -1 with none. */
    fun shown(keys: List<String>, wanted: String?): Int = if (keys.isEmpty()) -1 else keys.indexOf(wanted).coerceAtLeast(0)

    /** L2 (-1) or R2 (+1) on the card: the game before or after, wrapping round. */
    fun step(at: Int, by: Int, size: Int): Int = if (size <= 0) -1 else Math.floorMod(at + by, size)
}
