package io.github.zahakj.tandem.data

/**
 * Search's filters (the chips above the results): a system, a genre, a span of years, a lowest rating, a status,
 * played or never played, favourites. Each one left at its default doesn't filter. They combine with each other and
 * with the typed text (all must hold), and with no text they browse the whole library. They are a [SmartRule]
 * underneath, so a filter means exactly what the same condition means in a smart folder. Kept for the session only.
 * Pure: no Android, so it is unit tested.
 */
data class SearchFilter(
    val system: String? = null,
    val genre: String? = null,
    /** Release years, both ends included (a decade: 1990..1999). */
    val yearFrom: Int? = null,
    val yearTo: Int? = null,
    /** The lowest rating, 0..1 like [GameMeta.rating]. */
    val rating: Float? = null,
    /** A [GameStatus] id, or [SmartRule.STATUS_NONE] for games without one. */
    val status: String? = null,
    /** [SmartRule.PLAYED_ANY], [SmartRule.PLAYED_YES] or [SmartRule.PLAYED_NO]. */
    val played: String = SmartRule.PLAYED_ANY,
    val favorites: Boolean = false,
) {
    /** The filters, in the order their chips show. */
    enum class Chip { System, Genre, Year, Rating, Status, Played, Favorites }

    fun isSet(c: Chip): Boolean = when (c) {
        Chip.System -> system != null
        Chip.Genre -> genre != null
        Chip.Year -> yearFrom != null || yearTo != null
        Chip.Rating -> rating != null
        Chip.Status -> status != null
        Chip.Played -> played != SmartRule.PLAYED_ANY
        Chip.Favorites -> favorites
    }

    /** How many filters are set. */
    val count: Int get() = Chip.entries.count(::isSet)
    val active: Boolean get() = count > 0

    /** This with [c] back at its default. */
    fun cleared(c: Chip): SearchFilter = when (c) {
        Chip.System -> copy(system = null)
        Chip.Genre -> copy(genre = null)
        Chip.Year -> copy(yearFrom = null, yearTo = null)
        Chip.Rating -> copy(rating = null)
        Chip.Status -> copy(status = null)
        Chip.Played -> copy(played = SmartRule.PLAYED_ANY)
        Chip.Favorites -> copy(favorites = false)
    }

    /** The same conditions as a smart-folder rule (every one must hold). */
    fun rule(): SmartRule = SmartRule(
        systems = listOfNotNull(system),
        genres = listOfNotNull(genre),
        yearFrom = yearFrom,
        yearTo = yearTo,
        ratingFrom = rating,
        statuses = listOfNotNull(status),
        played = played,
        favorites = favorites,
        match = SmartRule.MATCH_ALL,
    )

    companion object {
        /** The decades offered for the year filter: those [years] fall in, newest first. */
        fun decades(years: Collection<Int>): List<Int> = years.filter { it in 1950..2100 }.map { it / 10 * 10 }.distinct().sortedDescending()

        /** The lowest ratings offered: 2, 3, 4 and 5 stars of five. */
        val RATINGS = listOf(0.4f, 0.6f, 0.8f, 1f)

        /**
         * The games of [all] that hold [text] (already normalized, as search does; empty: any) and pass [filter], in
         * [all]'s order: the first [limit] of them, and how many there are. With no text and no filter, nothing
         * (search shows its prompt instead). [title] gives a game's normalized title, [view] what the rule looks at.
         */
        fun <T> results(all: List<T>, text: String, filter: SearchFilter, limit: Int, title: (T) -> String, view: (T) -> SmartGame): Pair<List<T>, Int> {
            if (text.isEmpty() && !filter.active) return emptyList<T>() to 0
            val rule = filter.rule()
            val now = System.currentTimeMillis()
            val out = ArrayList<T>()
            var total = 0
            for (g in all) {
                if (text.isNotEmpty() && !title(g).contains(text)) continue
                if (filter.active && !rule.matches(view(g), now)) continue
                total++
                if (out.size < limit) out += g
            }
            return out to total
        }
    }
}
