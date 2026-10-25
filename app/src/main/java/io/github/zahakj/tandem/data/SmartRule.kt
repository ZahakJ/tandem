package io.github.zahakj.tandem.data

import kotlinx.serialization.Serializable

/**
 * What a smart folder holds ([HomeFolder.rule]): every game that passes all of the set conditions, sorted by
 * [sort]. A condition left at its default doesn't filter, so the default rule matches the whole library. Choices
 * are strings (like [FolderStyle]'s), so a value from a newer version reads as the default instead of failing the
 * whole of icon-grid.json. Pure data: no Android, so it is unit tested.
 */
@Serializable
data class SmartRule(
    /** Platform ids; empty: every system. */
    val systems: List<String> = emptyList(),
    /** Genre names, matched without case; empty: any genre. */
    val genres: List<String> = emptyList(),
    /** Text the title must contain (no case); empty: any title. */
    val pattern: String = "",
    /** [pattern] is a regular expression, searched anywhere in the title. */
    val regex: Boolean = false,
    /** Release years, both ends included; a game without a year never matches once either is set. */
    val yearFrom: Int? = null,
    val yearTo: Int? = null,
    val favorites: Boolean = false,
    /** [PLAYED_ANY], [PLAYED_YES] or [PLAYED_NO]. */
    val played: String = PLAYED_ANY,
    /** Keeps this many matches, picked at random once per session; 0 keeps them all. */
    val random: Int = 0,
    /** The package of the emulator the game starts with; null: any. */
    val emulator: String? = null,
    /** [SORT_NAME], [SORT_RECENT], [SORT_MOST], [SORT_ADDED] or [SORT_SYSTEM]. */
    val sort: String = SORT_NAME,
) {
    /** The compiled [pattern], or null when it is empty, plain text, or not a valid expression ([patternError]). */
    @kotlinx.serialization.Transient
    private val compiled: Regex? = if (regex && pattern.isNotEmpty()) runCatching { Regex(pattern, RegexOption.IGNORE_CASE) }.getOrNull() else null

    /** True when [pattern] is meant as an expression but doesn't parse: such a rule matches nothing. */
    val patternError: Boolean get() = regex && pattern.isNotEmpty() && compiled == null

    /** Whether [g] passes every condition (all but [random] and [sort], which work on the whole list). */
    fun matches(g: SmartGame): Boolean {
        if (systems.isNotEmpty() && g.system !in systems) return false
        if (genres.isNotEmpty() && g.genres.none { have -> genres.any { it.equals(have, ignoreCase = true) } }) return false
        if (pattern.isNotEmpty()) {
            if (regex) { if (compiled?.containsMatchIn(g.title) != true) return false }
            else if (!g.title.contains(pattern, ignoreCase = true)) return false
        }
        if (yearFrom != null || yearTo != null) {
            val y = g.year ?: return false
            if (yearFrom != null && y < yearFrom) return false
            if (yearTo != null && y > yearTo) return false
        }
        if (favorites && !g.favorite) return false
        when (played) {
            PLAYED_YES -> if (!g.played) return false
            PLAYED_NO -> if (g.played) return false
        }
        if (emulator != null && g.emulator != emulator) return false
        return true
    }

    /**
     * The folder's games from [all]: the matches, [random] of them when set, in [sort] order. [seed] keeps the random
     * pick: the same seed picks the same games, and a game joining or leaving the matches changes only that one.
     */
    fun <T> apply(all: List<T>, seed: Long, view: (T) -> SmartGame): List<T> {
        if (patternError) return emptyList()
        var out = all.filter { matches(view(it)) }
        if (random > 0 && out.size > random) out = out.sortedBy { pickRank(seed, view(it).key) }.take(random)
        return sorted(out, view)
    }

    private fun <T> sorted(games: List<T>, view: (T) -> SmartGame): List<T> {
        val keyed = games.map { view(it) to it }
        val byTitle = compareBy<Pair<SmartGame, T>> { it.first.title.lowercase() }
        val order = when (sort) {
            SORT_RECENT -> compareByDescending<Pair<SmartGame, T>> { it.first.lastPlayed }.then(byTitle)
            SORT_MOST -> compareByDescending<Pair<SmartGame, T>> { it.first.playSeconds }.then(byTitle)
            SORT_ADDED -> compareByDescending<Pair<SmartGame, T>> { it.first.added }.then(byTitle)
            SORT_SYSTEM -> compareBy<Pair<SmartGame, T>> { it.first.system }.then(byTitle)
            else -> byTitle
        }
        return keyed.sortedWith(order).map { it.second }
    }

    companion object {
        const val PLAYED_ANY = "any"
        const val PLAYED_YES = "played"
        const val PLAYED_NO = "unplayed"
        const val SORT_NAME = "name"
        const val SORT_RECENT = "recent"
        const val SORT_MOST = "most"
        const val SORT_ADDED = "added"
        const val SORT_SYSTEM = "system"
        val SORTS = listOf(SORT_NAME, SORT_RECENT, SORT_MOST, SORT_ADDED, SORT_SYSTEM)
        val PLAYS = listOf(PLAYED_ANY, PLAYED_YES, PLAYED_NO)
        /** The choices offered for [random]. */
        val RANDOMS = listOf(0, 5, 10, 20, 50)

        fun sortLabel(s: String) = when (s) {
            SORT_RECENT -> "Recently played"
            SORT_MOST -> "Most played"
            SORT_ADDED -> "Date added"
            SORT_SYSTEM -> "System"
            else -> "A–Z"
        }

        fun playedLabel(s: String) = when (s) {
            PLAYED_YES -> "Played"
            PLAYED_NO -> "Unplayed"
            else -> "Any"
        }

        /** A stable pseudo-random rank for [key] under [seed] (64-bit FNV-1a, then mixed). */
        fun pickRank(seed: Long, key: String): Long {
            var h = -0x340d631b7bdddcdbL xor seed
            for (c in key) { h = h xor c.code.toLong(); h *= 0x100000001b3L }
            h = h xor (h ushr 33); h *= -0xae502812aa7333L; h = h xor (h ushr 33)
            return h
        }
    }
}

/** What a [SmartRule] looks at in a game: a plain view, so the rules are tested without a library. */
data class SmartGame(
    val key: String,
    val title: String,
    val system: String,
    /** Tidied genre names (the subfolder when the game has none). */
    val genres: List<String> = emptyList(),
    val year: Int? = null,
    val favorite: Boolean = false,
    val played: Boolean = false,
    val emulator: String? = null,
    val lastPlayed: Long = 0,
    val playSeconds: Long = 0,
    val added: Long = 0,
)

/** Where a folder's games start: [OPEN_TOP], [OPEN_BOTTOM], or null for each game's own choice ([HomeFolder.openOn]). */
object FolderScreens {
    const val OPEN_TOP = "top"
    const val OPEN_BOTTOM = "bottom"
    val ALL = listOf(null, OPEN_TOP, OPEN_BOTTOM)

    fun label(s: String?) = when (s) { OPEN_TOP -> "Top"; OPEN_BOTTOM -> "Bottom"; else -> "Default" }
}
