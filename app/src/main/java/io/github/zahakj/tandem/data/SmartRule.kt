package io.github.zahakj.tandem.data

import io.github.zahakj.tandem.Tx
import io.github.zahakj.tandem.R
import kotlinx.serialization.Serializable

/**
 * What a smart folder holds ([HomeFolder.rule]): every game that passes all of the set conditions (or any,
 * [match]), sorted by [sort]. A condition left at its default doesn't filter, so the default rule matches the whole library. Choices
 * are strings (like [FolderStyle]'s), so a value from a newer version reads as the default instead of failing the
 * whole of icon-grid.json. Pure data: no Android, so it is unit tested.
 */
@Serializable
data class SmartRule(
    /** Platform ids; empty: every system. */
    val systems: List<String> = emptyList(),
    /** Genre names, matched by their buckets (Genres.kt: spelling, case and compounds don't matter); empty: any genre. */
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
    /** [GameStatus] ids, any of which a game must have ("none" for no status); empty: any status. */
    val statuses: List<String> = emptyList(),
    /** [SORT_NAME], [SORT_RECENT], [SORT_MOST], [SORT_ADDED] or [SORT_SYSTEM]. */
    val sort: String = SORT_NAME,
    /** [MATCH_ALL]: a game passes every condition set; [MATCH_ANY]: at least one of them. */
    val match: String = MATCH_ALL,
    /** Rating bounds, 0..1 like [GameMeta.rating]; a game without a rating never matches once either is set. */
    val ratingFrom: Float? = null,
    val ratingTo: Float? = null,
    /** Text the developer or the publisher must contain (no case); empty: anyone. */
    val developer: String = "",
    val publisher: String = "",
    /** Play time bounds in hours: more than [playedMoreHours], less than [playedLessHours]. */
    val playedMoreHours: Int? = null,
    val playedLessHours: Int? = null,
    /** "" any; "within:N" played in the last N days; "notwithin:N" not played in them (or never); [LAST_NEVER] never played. */
    val lastPlayed: String = "",
) {
    /** The compiled [pattern], or null when it is empty, plain text, or not a valid expression ([patternError]). */
    @kotlinx.serialization.Transient
    private val compiled: Regex? = if (regex && pattern.isNotEmpty()) runCatching { Regex(pattern, RegexOption.IGNORE_CASE) }.getOrNull() else null

    /** True when [pattern] is meant as an expression but doesn't parse: such a rule matches nothing. */
    val patternError: Boolean get() = regex && pattern.isNotEmpty() && compiled == null

    /**
     * Whether [g] passes the conditions (all but [random] and [sort], which work on the whole list): every one set,
     * or with [MATCH_ANY] at least one. No condition set matches every game either way. [now] is for [lastPlayed].
     */
    fun matches(g: SmartGame, now: Long = System.currentTimeMillis()): Boolean {
        val checks = checks(g, now)
        if (checks.isEmpty()) return true
        return if (match == MATCH_ANY) checks.any { it() } else checks.all { it() }
    }

    /** One check per condition that is set, in the form's order. */
    private fun checks(g: SmartGame, now: Long): List<() -> Boolean> = buildList {
        if (systems.isNotEmpty()) add { g.system in systems }
        // By bucket (Genres.kt): "Platformers" finds "Platform", "Action & Adventure" either.
        if (genres.isNotEmpty()) add { genres.any { Genres.matches(it, g.genres) } }
        if (pattern.isNotEmpty()) add { if (regex) compiled?.containsMatchIn(g.title) == true else g.title.contains(pattern, ignoreCase = true) }
        if (yearFrom != null || yearTo != null) add {
            val y = g.year
            y != null && (yearFrom == null || y >= yearFrom) && (yearTo == null || y <= yearTo)
        }
        if (favorites) add { g.favorite }
        when (played) {
            PLAYED_YES -> add { g.played }
            PLAYED_NO -> add { !g.played }
        }
        if (emulator != null) add { g.emulator == emulator }
        if (statuses.isNotEmpty()) add { (g.status ?: STATUS_NONE) in statuses }
        if (ratingFrom != null || ratingTo != null) add {
            val r = g.rating
            // A hair of slack: a rating of 0.8 is "at least 4 stars" however it was rounded.
            r != null && (ratingFrom == null || r >= ratingFrom - 0.001f) && (ratingTo == null || r <= ratingTo + 0.001f)
        }
        if (developer.isNotBlank()) add { g.developer?.contains(developer.trim(), ignoreCase = true) == true }
        if (publisher.isNotBlank()) add { g.publisher?.contains(publisher.trim(), ignoreCase = true) == true }
        if (playedMoreHours != null) add { g.playSeconds > playedMoreHours * 3600L }
        if (playedLessHours != null) add { g.playSeconds < playedLessHours * 3600L }
        lastPlayedRule()?.let { (kind, days) ->
            val since = now - days * DAY_MS
            when (kind) {
                LAST_WITHIN -> add { g.lastPlayed > 0 && g.lastPlayed >= since }
                LAST_NOT_WITHIN -> add { g.lastPlayed < since }
                else -> add { g.lastPlayed == 0L && !g.played }
            }
        }
    }

    /** [lastPlayed] read: its kind and days ("within:30" is within, 30); null for any, or a value not understood. */
    fun lastPlayedRule(): Pair<String, Int>? {
        if (lastPlayed == LAST_NEVER) return LAST_NEVER to 0
        val kind = lastPlayed.substringBefore(':')
        val days = lastPlayed.substringAfter(':', "").toIntOrNull()?.takeIf { it > 0 } ?: return null
        return if (kind == LAST_WITHIN || kind == LAST_NOT_WITHIN) kind to days else null
    }

    /**
     * The folder's games from [all]: the matches, [random] of them when set, in [sort] order. [seed] keeps the random
     * pick: the same seed picks the same games, and a game joining or leaving the matches changes only that one.
     */
    fun <T> apply(all: List<T>, seed: Long, view: (T) -> SmartGame): List<T> {
        if (patternError) return emptyList()
        val now = System.currentTimeMillis()
        var out = all.filter { matches(view(it), now) }
        if (random > 0 && out.size > random) out = out.sortedBy { pickRank(seed, view(it).key) }.take(random)
        return sorted(out, view)
    }

    private fun <T> sorted(games: List<T>, view: (T) -> SmartGame): List<T> {
        val keyed = games.map { view(it) to it }
        val byTitle = compareBy<Pair<SmartGame, T>> { it.first.sortName }
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
        const val STATUS_NONE = "none"
        const val SORT_NAME = "name"
        const val SORT_RECENT = "recent"
        const val SORT_MOST = "most"
        const val SORT_ADDED = "added"
        const val SORT_SYSTEM = "system"
        const val MATCH_ALL = "all"
        const val MATCH_ANY = "any"
        val MATCHES = listOf(MATCH_ALL, MATCH_ANY)
        const val LAST_WITHIN = "within"
        const val LAST_NOT_WITHIN = "notwithin"
        const val LAST_NEVER = "never"
        /** The choices offered for [lastPlayed]. */
        val LAST_PLAYED = listOf("", "within:1", "within:7", "within:30", "within:90", "within:365", "notwithin:30", "notwithin:90", "notwithin:365", LAST_NEVER)
        /** The choices offered for the play time bounds, in hours. */
        val HOURS = listOf<Int?>(null, 1, 2, 5, 10, 20, 50, 100)
        /** The choices offered for the rating bounds: stars out of five, as 0..1. */
        val RATINGS = listOf<Float?>(null, 0.2f, 0.4f, 0.5f, 0.6f, 0.7f, 0.8f, 0.9f, 1f)
        private const val DAY_MS = 24 * 60 * 60 * 1000L
        val SORTS = listOf(SORT_NAME, SORT_RECENT, SORT_MOST, SORT_ADDED, SORT_SYSTEM)
        val PLAYS = listOf(PLAYED_ANY, PLAYED_YES, PLAYED_NO)
        /** The choices offered for [random]. */
        val RANDOMS = listOf(0, 5, 10, 20, 50)

        fun sortLabel(s: String) = Tx.s(
            when (s) {
                SORT_RECENT -> R.string.grid_sort_recent
                SORT_MOST -> R.string.grid_sort_most
                SORT_ADDED -> R.string.grid_sort_added
                SORT_SYSTEM -> R.string.grid_sort_system
                else -> R.string.grid_sort_name
            },
        )

        fun playedLabel(s: String) = Tx.s(
            when (s) {
                PLAYED_YES -> R.string.grid_smart_played_yes
                PLAYED_NO -> R.string.grid_smart_played_no
                else -> R.string.grid_smart_played_any
            },
        )

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
    /** The [GameStatus] id, or null. */
    val status: String? = null,
    /** 0..1, or null when unknown. */
    val rating: Float? = null,
    val developer: String? = null,
    val publisher: String? = null,
    /** What the title sorts by (lowercase, a leading article skipped: Library.nameKey). */
    val sortName: String = title.lowercase(),
)

/** Where a folder's games start: [OPEN_TOP], [OPEN_BOTTOM], or null for each game's own choice ([HomeFolder.openOn]). */
object FolderScreens {
    const val OPEN_TOP = "top"
    const val OPEN_BOTTOM = "bottom"
    val ALL = listOf(null, OPEN_TOP, OPEN_BOTTOM)

    fun label(s: String?) = Tx.s(when (s) { OPEN_TOP -> R.string.grid_screens_top; OPEN_BOTTOM -> R.string.grid_screens_bottom; else -> R.string.grid_screens_default })
}
