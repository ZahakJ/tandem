package io.github.zahakj.tandem.data

/**
 * One vocabulary for genres, shared by search's Genre filter, smart-folder genre rules and This week. Scrapers spell
 * the same genre many ways ("Platform", "Platformers"; "Role Playing Game", "Role-Playing"; "Mystery & Visual
 * Novels"), so each free-text genre falls into buckets by keyword, most specific first ("Action RPG" is RPG,
 * "Action-Adventure" adventure). A compound joined by "&", "/", "," or "and" falls into the bucket of each of its
 * parts ("RPG & Strategy" is RPG and strategy; a part without a keyword, like "Click" in "Point & Click", goes with
 * the rest). A genre with no keyword at all is its own bucket, spelt loosely (case, hyphens, a plural). A bucket is
 * shown by how the library writes it most often. Pure Kotlin: no Android, so it is unit tested.
 */
object Genres {
    private val Buckets: List<Pair<String, List<String>>> = listOf(
        "rpg" to listOf("rpg", "role", "jrpg"),
        "metroidvania" to listOf("metroidvania"),
        "roguelike" to listOf("rogue"),
        "horror" to listOf("horror", "survival"),
        "rhythm" to listOf("rhythm", "music", "danc"),
        "fighting" to listOf("fight", "versus"),
        "beatemup" to listOf("beat 'em", "beat-'em", "beat em", "brawler"),
        "shooter" to listOf("shoot", "shmup", "fps", "gun", "bullet"),
        "racing" to listOf("racing", "driving", "race", "kart"),
        "sports" to listOf("sport", "soccer", "football", "golf", "tennis", "basketball", "baseball", "hockey", "skate", "wrestl", "boxing"),
        "puzzle" to listOf("puzzle", "match"),
        "strategy" to listOf("strateg", "tactic", "rts", "4x", "tower defen"),
        "simulation" to listOf("simulat", "sim", "life", "farm", "management", "tycoon"),
        "platformer" to listOf("platform", "jump"),
        "stealth" to listOf("stealth"),
        "visualnovel" to listOf("visual novel", "novel", "mystery", "interactive fiction"),
        "party" to listOf("party", "mini-game", "minigame"),
        "board" to listOf("board", "card", "casino", "pinball", "trivia", "quiz"),
        "sandbox" to listOf("sandbox", "open world", "building", "craft"),
        "adventure" to listOf("adventure", "point"),
        "action" to listOf("action", "hack", "slash", "arcade"),
    )

    private val Joins = Regex("""\s*(?:&|/|,|;|\+|\band\b)\s*""", RegexOption.IGNORE_CASE)
    private val Spaces = Regex("""\s+""")
    private val NotWord = Regex("""[^\p{L}\p{N}]+""")

    /** A genre's text as it shows: trimmed, single spaces, a capital first letter ("RPG" stays "RPG"). */
    fun tidy(s: String): String = s.trim().replace(Spaces, " ").replaceFirstChar { it.uppercase() }

    private fun keyword(s: String): String? {
        val g = s.lowercase()
        return Buckets.firstOrNull { (_, words) -> words.any { it in g } }?.first
    }

    /** A genre without a keyword, spelt loosely: "Educational games" and "educational-game" are one. */
    private fun loose(s: String): String =
        s.lowercase().split(NotWord).filter { it.isNotEmpty() && it != "game" && it != "games" }
            .joinToString(" ") { if (it.length > 3 && it.endsWith("s") && !it.endsWith("ss")) it.dropLast(1) else it }

    /** [genre]'s buckets, each with how that part of it is written ("RPG & Strategy": rpg "RPG", strategy "Strategy"). */
    fun parts(genre: String): List<Pair<String, String>> {
        partsOf[genre]?.let { return it }
        val whole = tidy(genre)
        val out = if (whole.isEmpty()) emptyList() else {
            val pieces = whole.split(Joins).map(::tidy).filter { it.isNotEmpty() }
            val known = pieces.mapNotNull { p -> keyword(p)?.let { it to p } }
            when {
                // Two parts in one bucket ("Mystery & Visual Novels") show by the longer, more telling one.
                known.isNotEmpty() -> known.groupBy { it.first }.map { (b, ps) -> b to ps.maxBy { it.second.length }.second }
                else -> listOf((loose(whole).ifEmpty { whole.lowercase() }) to whole)
            }
        }
        // A library has a few hundred genre spellings at most; looked up for every game on every pass.
        if (partsOf.size > 4096) partsOf.clear()
        partsOf[genre] = out
        return out
    }

    private val partsOf = java.util.concurrent.ConcurrentHashMap<String, List<Pair<String, String>>>()

    /** The buckets [genre] falls in (most often one). */
    fun buckets(genre: String): List<String> = parts(genre).map { it.first }

    /** The buckets of all of [genres], once each. */
    fun bucketsOf(genres: Iterable<String>): List<String> = genres.flatMap(::buckets).distinct()

    /** Whether a game with [have] is of the genre [wanted]: they share a bucket. */
    fun matches(wanted: String, have: Iterable<String>): Boolean {
        val w = buckets(wanted)
        return w.isNotEmpty() && have.any { h -> buckets(h).any { it in w } }
    }

    /**
     * Each bucket in [genres] (every genre of every game, repeats counted) with the name it shows by: how it is
     * written most often, then the shorter spelling.
     */
    fun names(genres: Iterable<String>): Map<String, String> {
        val counts = HashMap<String, HashMap<String, Int>>()
        for (g in genres) for ((b, text) in parts(g)) counts.getOrPut(b) { HashMap() }.merge(text, 1, Int::plus)
        return counts.mapValues { (_, c) ->
            c.entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key.length }.thenBy { it.key }).first().key
        }
    }

    /** The genres to offer as choices: one name per bucket, in alphabetical order. */
    fun choices(genres: Iterable<String>): List<String> = names(genres).values.sortedBy { it.lowercase() }
}
