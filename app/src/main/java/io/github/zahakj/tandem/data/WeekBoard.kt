package io.github.zahakj.tandem.data

import kotlinx.serialization.Serializable
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.IsoFields
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow
import kotlin.random.Random

/*
 * This week: a board of games to play, worked out on the device from what Tandem already knows (play time,
 * sessions, statuses, favourites, genres, ratings, HowLongToBeat, achievements, when a game was added). Pure Kotlin,
 * no Android and no network: the screen hands it plain [WeekGame]s and shows the [WeekBoardResult] it gets back.
 *
 * - The taste profile is counted from the games played, each time, and never stored.
 * - Each slot ranks its candidates, then draws from the top few with a seed made of the ISO week, the slot, the
 *   re-roll count and a salt kept on the device: the board stays the same all week, and differs from another
 *   handheld's with the same library.
 * - Picks shown in the last four weeks, and ones re-rolled away, are avoided while anything else is left.
 * - Once a game is on the board, games of its system and genre score a little lower (a greedy diversity step), so
 *   one system or genre doesn't fill it.
 * - What the board showed this week sticks while it still qualifies, so playing one game doesn't reshuffle the rest.
 */

/** One game as the board sees it. [achEarned] is -1 when the achievements aren't known. Times in ms, durations in s. */
data class WeekGame(
    val key: String,
    val title: String,
    val platform: String,
    val genres: List<String> = emptyList(),
    val developer: String? = null,
    val rating: Float? = null,
    val favorite: Boolean = false,
    val hidden: Boolean = false,
    val status: String? = null,
    val statusAt: Long = 0,
    val finishedAt: Long = 0,
    val playSeconds: Long = 0,
    val lastPlayed: Long = 0,
    val firstSeen: Long = 0,
    val ttbMain: Int = 0,
    val achEarned: Int = -1,
    val achTotal: Int = 0,
) {
    val gameStatus: GameStatus? get() = GameStatus.entries.firstOrNull { it.id == status }
    val beaten: Boolean get() = gameStatus?.beaten == true
}

/** The board's rows, in the order they show. */
enum class WeekSlot(val id: String, val size: Int) {
    UpNext("upnext", 8), Pick("pick", 1), Resume("resume", 4), Almost("almost", 4), Short("short", 4), Because("because", 4),
    Hunt("hunt", 4), Gem("gem", 4), Shelf("shelf", 4), New("new", 4), Genre("genre", 6), Explore("explore", 6);

    companion object {
        fun of(id: String): WeekSlot? = entries.firstOrNull { it.id == id }
    }
}

/** Why a game is on the board: values only, worded by the screen in the language of the moment. */
sealed interface WeekWhy {
    /** Pick of the week: the best-rated game not started yet in a genre played for [seconds]. */
    data class Taste(val genre: String, val seconds: Long) : WeekWhy
    data object TopRated : WeekWhy
    /** In the backlog since [since]. */
    data class Waiting(val since: Long) : WeekWhy
    data object Queued : WeekWhy
    data class LeftOff(val days: Int, val seconds: Long) : WeekWhy
    /** About [seconds] of the main story left, by HowLongToBeat. */
    data class TimeLeft(val seconds: Int) : WeekWhy
    data class AchLeft(val earned: Int, val total: Int) : WeekWhy
    /** HowLongToBeat's main story: [seconds]. */
    data class Short(val seconds: Int) : WeekWhy
    data class Studio(val finished: String, val developer: String) : WeekWhy
    data class Series(val finished: String) : WeekWhy
    data class SameGenre(val finished: String, val genre: String) : WeekWhy
    data class Gem(val rating: Float) : WeekWhy
    data class Shelved(val since: Long) : WeekWhy
    data class Backlogged(val since: Long) : WeekWhy
    data class Untouched(val since: Long) : WeekWhy
    data class Hunt(val earned: Int, val total: Int) : WeekWhy
    data class Added(val days: Int) : WeekWhy
    data class YourGenre(val genre: String, val seconds: Long) : WeekWhy
    /** A genre rarely or never played ([never]: nothing has been played at all yet). */
    data class NewGenre(val genre: String, val never: Boolean) : WeekWhy
}

/** A supporting fact for the bottom screen, beside the reason. */
sealed interface WeekFact {
    data class TimeLeft(val seconds: Int) : WeekFact
    data class Hltb(val seconds: Int) : WeekFact
    data class Achievements(val earned: Int, val total: Int) : WeekFact
    data class GenreTime(val genre: String, val seconds: Long) : WeekFact
    data class Played(val seconds: Long) : WeekFact
}

data class WeekPick(val key: String, val slot: WeekSlot, val why: WeekWhy, val facts: List<WeekFact> = emptyList())

/** A row: its slot, its subject ([subject]: a finished game's title, or a genre), its picks. */
data class WeekRow(val slot: WeekSlot, val picks: List<WeekPick>, val subject: String? = null)

data class WeekBoardResult(val week: String, val rows: List<WeekRow>) {
    val picks: List<WeekPick> = rows.flatMap { it.picks }
    private val byKey = picks.associateBy { it.key }
    fun pickOf(key: String): WeekPick? = byKey[key]
    val isEmpty: Boolean get() = picks.isEmpty()

    companion object {
        val Empty = WeekBoardResult("", emptyList())
    }
}

/** A game the board showed in [week] (an ISO week, "2026-W48"), in [slot]. */
@Serializable
data class WeekShown(val week: String, val slot: String, val key: String)

/**
 * What the board keeps on the device (`week.json`, in backups with the game data, never in a shared look): whether
 * it shows, its salt, the games said to be not for you, what it showed lately, and this week's re-rolls.
 */
@Serializable
data class WeekState(
    val on: Boolean = true,
    /** Mixed into every draw; made once on the device. 0 until then. */
    val salt: Long = 0,
    /** Never suggested again (Y › Not for me), until Reset suggestions. */
    val notForMe: Set<String> = emptySet(),
    /** What the board showed, this week and the last few. */
    val shown: List<WeekShown> = emptyList(),
    /** The week [rerolls] and [skipped] belong to. */
    val week: String = "",
    /** X presses per slot id this week: each draws a little deeper down the slot's ranking. */
    val rerolls: Map<String, Int> = emptyMap(),
    /** Games re-rolled away this week: avoided while anything else is left. */
    val skipped: Set<String> = emptySet(),
)

object WeekBoard {
    const val DAY = 86_400_000L
    private const val HOUR = 3600
    /** Weeks a pick stays out of the board after it showed. */
    const val AVOID_WEEKS = 4
    /** Weeks of [WeekState.shown] kept. */
    const val KEEP_WEEKS = 8
    /** The draw's window: the top this many candidates (the hero: fewer), wider with each re-roll. */
    const val WINDOW = 8
    const val HERO_WINDOW = 4

    /** The ISO week of [ms] in [zone]: "2026-W48". Weeks start on Monday, whatever calendar is shown. */
    fun weekOf(ms: Long, zone: ZoneId): String = weekOf(Instant.ofEpochMilli(ms).atZone(zone).toLocalDate())

    fun weekOf(d: LocalDate): String = "%04d-W%02d".format(java.util.Locale.ROOT, d.get(IsoFields.WEEK_BASED_YEAR), d.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR))

    /** The Monday the week of [ms] began. */
    fun mondayOf(ms: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate().with(DayOfWeek.MONDAY)

    /** The [n] weeks before the week of [ms], newest first. */
    fun weeksBefore(ms: Long, zone: ZoneId, n: Int): List<String> {
        val d = mondayOf(ms, zone)
        return (1..n).map { weekOf(d.minusWeeks(it.toLong())) }
    }

    /** FNV-1a, 64 bits: the same seed on every device and every run for the same text. */
    fun fnv(s: String): Long {
        var h = -0x340d631b7bdddcdbL
        for (c in s) {
            h = h xor (c.code.toLong() and 0xff)
            h *= 0x100000001b3L
            if (c.code > 0xff) { h = h xor (c.code.toLong() ushr 8); h *= 0x100000001b3L }
        }
        return h
    }

    // ------------------------------------------------------------------------------------------------ genres

    /** A game's genre buckets, as search and smart folders see them (Genres.kt): "RPG & Strategy" is both. */
    fun bucketsOf(g: WeekGame): List<String> = Genres.bucketsOf(g.genres)

    /**
     * A title's series, for "more of the same": what comes before a colon, a dash or a bracket, without a trailing
     * number or Roman numeral ("Mega Man 2" and "Mega Man X" are both "mega man"). Null when too short to mean much.
     */
    fun seriesOf(title: String): String? {
        var t = title.lowercase().substringBefore(':').substringBefore(" - ").substringBefore('(').substringBefore('[').trim()
        t = t.replace(SeriesTail, "").trim()
        return t.takeIf { it.length >= 4 }
    }

    private val SeriesTail = Regex("""\s+(\d+|[ivx]+|x|zero|advance|ds|64|dx|plus|deluxe)$""")

    // ------------------------------------------------------------------------------------------------ taste

    /**
     * What you like, counted from what you played: each game weighs log(1 + hours) × a factor for its status ×
     * a decay with a 90-day half-life since you last touched it. Genres, developers and systems add up those
     * weights and are scaled so the favourite is 1.
     */
    class Taste(games: List<WeekGame>, now: Long) {
        val genre = HashMap<String, Double>()
        val developer = HashMap<String, Double>()
        val platform = HashMap<String, Double>()
        /** Seconds played per genre bucket (for the reasons). */
        val genreSeconds = HashMap<String, Long>()
        /** How each bucket is written most often in the library: the name the reasons use. */
        val genreName = HashMap<String, String>()
        val empty: Boolean

        init {
            genreName.putAll(Genres.names(games.asSequence().flatMap { it.genres }.asIterable()))
            var any = false
            for (g in games) {
                val w = weight(g, now)
                if (w <= 0.0) continue
                any = true
                for (b in bucketsOf(g)) {
                    genre.merge(b, w, Double::plus)
                    genreSeconds.merge(b, g.playSeconds, Long::plus)
                }
                g.developer?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }?.let { developer.merge(it, w, Double::plus) }
                platform.merge(g.platform, w, Double::plus)
            }
            empty = !any
            for (m in listOf(genre, developer, platform)) {
                val top = m.values.maxOrNull() ?: continue
                if (top > 0) m.replaceAll { _, v -> v / top }
            }
        }

        /** 0..1: how much [g] is your kind of game. */
        fun of(g: WeekGame): Double {
            val gen = bucketsOf(g).maxOfOrNull { genre[it] ?: 0.0 } ?: 0.0
            val dev = g.developer?.trim()?.lowercase()?.let { developer[it] } ?: 0.0
            val sys = platform[g.platform] ?: 0.0
            return 0.6 * gen + 0.25 * dev + 0.15 * sys
        }

        /** Your genres, most liked first. */
        val topGenres: List<String> get() = genre.entries.sortedWith(compareByDescending<Map.Entry<String, Double>> { it.value }.thenBy { it.key }).map { it.key }

        fun nameOf(bucket: String): String = genreName[bucket] ?: bucket.replaceFirstChar { it.uppercase() }

        companion object {
            fun weight(g: WeekGame, now: Long): Double {
                var hours = g.playSeconds / 3600.0
                // Played elsewhere: finished without a session here still says something about you.
                if (g.beaten && hours < 2.0) hours = 2.0
                if (hours <= 0.0) return 0.0
                val factor = when (g.gameStatus) {
                    GameStatus.Completed -> 1.5; GameStatus.Finished -> 1.3; GameStatus.Playing, GameStatus.Endless -> 1.0
                    GameStatus.Shelved -> 0.4; GameStatus.Backlog -> 0.2; null -> 0.8
                }
                val last = maxOf(g.lastPlayed, g.finishedAt, g.statusAt)
                val days = if (last > 0) ((now - last).coerceAtLeast(0) / DAY.toDouble()) else 365.0
                val decay = 0.5.pow(days / 90.0)
                return ln(1.0 + hours) * factor * decay * (if (g.favorite) 1.4 else 1.0)
            }
        }
    }

    // ------------------------------------------------------------------------------------------------ the board

    private class Cand(val g: WeekGame, val score: Double, val why: WeekWhy)

    /** Not really started: under 20 minutes played, and nothing said about it but maybe Backlog. */
    private fun fresh(g: WeekGame) = g.playSeconds < 20 * 60 && (g.status == null || g.gameStatus == GameStatus.Backlog)

    private fun quality(g: WeekGame): Double = (g.rating ?: 0.5f).toDouble().coerceIn(0.0, 1.0)

    private fun days(now: Long, then: Long): Int = ((now - then).coerceAtLeast(0) / DAY).toInt()

    /**
     * The board for the week of [now]. [games] is the library (hidden ones are left out here too); [upNext] the Up
     * next list's game keys in order; [state] what the device keeps.
     */
    fun build(games: List<WeekGame>, now: Long, zone: ZoneId, state: WeekState, upNext: List<String> = emptyList()): WeekBoardResult {
        val week = weekOf(now, zone)
        val visible = games.filter { !it.hidden }
        val taste = Taste(visible, now)
        val pool = visible.filter { it.key !in state.notForMe }
        val byKey = pool.associateBy { it.key }
        val thisWeek = state.week == week
        val rerolls = if (thisWeek) state.rerolls else emptyMap()
        val skipped = if (thisWeek) state.skipped else emptySet()
        val recentWeeks = weeksBefore(now, zone, AVOID_WEEKS).toSet()
        val recent = state.shown.filter { it.week in recentWeeks }.map { it.key }.toSet()
        val sticky = state.shown.filter { it.week == week }.groupBy({ it.slot }, { it.key })

        val used = HashSet<String>()
        val onBoard = ArrayList<WeekGame>()
        val rows = ArrayList<WeekRow>()

        fun add(slot: WeekSlot, cands: List<Cand>, subject: String? = null, min: Int = 1) {
            val ranked = cands.filter { it.g.key !in used }.sortedWith(compareByDescending<Cand> { it.score }.thenBy { it.g.key })
            if (ranked.size < min) return
            val n = rerolls[slot.id] ?: 0
            val chosen = ArrayList<Cand>()
            // What showed this week stays while it still qualifies (and wasn't re-rolled away).
            for (k in sticky[slot.id].orEmpty()) {
                if (chosen.size >= slot.size) break
                ranked.firstOrNull { it.g.key == k && k !in skipped }?.let { chosen += it }
            }
            var pos = chosen.size
            while (chosen.size < slot.size) {
                val left = ranked.filter { c -> chosen.none { it.g.key == c.g.key } }
                if (left.isEmpty()) break
                val unseen = left.filter { it.g.key !in recent && it.g.key !in skipped }
                // Only what showed lately (or was re-rolled away) is left: it fills an empty row, never a started one.
                if (unseen.isEmpty() && chosen.isNotEmpty()) break
                val fresh = unseen.ifEmpty { left }
                val window = (if (slot == WeekSlot.Pick) HERO_WINDOW else WINDOW) * (1 + n)
                // Diversity: games of a system or genre already on the board score lower, more so the more there are.
                // Only the best few are weighed (a lower score only falls further), so a big library stays quick.
                val systems = HashMap<String, Int>()
                val genres = HashMap<String, Int>()
                for (o in onBoard + chosen.map { it.g }) {
                    systems.merge(o.platform, 1, Int::plus)
                    for (b in bucketsOf(o)) genres.merge(b, 1, Int::plus)
                }
                val adjusted = fresh.take(window * 4 + onBoard.size).map { c ->
                    val sameSystem = systems[c.g.platform] ?: 0
                    val sameGenre = bucketsOf(c.g).maxOfOrNull { genres[it] ?: 0 } ?: 0
                    c to c.score / (1.0 + 0.35 * sameSystem + 0.2 * sameGenre)
                }.sortedWith(compareByDescending<Pair<Cand, Double>> { it.second }.thenBy { it.first.g.key })
                val top = adjusted.take(window)
                val rng = Random(fnv("$week|${slot.id}|$n|$pos|${subject.orEmpty()}") xor state.salt)
                // Higher ranks are likelier (1, 1/2, 1/3...), and so are scores close to the best: a game far behind
                // (another tier of its slot) almost never wins the draw.
                val best = top.first().second
                val weights = top.mapIndexed { i, (_, s) -> (if (best > 0) (s / best).coerceIn(0.0, 1.0).pow(3) else 1.0) / (1 + i) }
                var r = rng.nextDouble() * weights.sum()
                var at = 0
                while (at < top.lastIndex && r >= weights[at]) { r -= weights[at]; at++ }
                chosen += top[at].first
                pos++
            }
            if (chosen.isEmpty()) return
            for (c in chosen) { used += c.g.key; onBoard += c.g }
            rows += WeekRow(slot, chosen.map { WeekPick(it.g.key, slot, it.why, facts(it.g, it.why, taste)) }, subject)
        }

        // Up next: your own queue, first, in your order.
        val queued = upNext.distinct().mapNotNull { byKey[it] }.filter { !it.beaten }.take(WeekSlot.UpNext.size)
        if (queued.isNotEmpty()) {
            for (g in queued) { used += g.key; onBoard += g }
            rows += WeekRow(WeekSlot.UpNext, queued.map { WeekPick(it.key, WeekSlot.UpNext, WeekWhy.Queued, facts(it, WeekWhy.Queued, taste)) })
        }

        // Pick of the week: not started, your kind of game, well rated.
        add(WeekSlot.Pick, pool.filter { fresh(it) }.map { g ->
            val top = bucketsOf(g).maxByOrNull { taste.genre[it] ?: 0.0 }
            val seconds = top?.let { taste.genreSeconds[it] } ?: 0L
            val why = when {
                top != null && seconds >= 2L * HOUR && (taste.genre[top] ?: 0.0) >= 0.3 -> WeekWhy.Taste(taste.nameOf(top), seconds)
                g.gameStatus == GameStatus.Backlog && g.statusAt > 0 -> WeekWhy.Waiting(g.statusAt)
                else -> WeekWhy.TopRated
            }
            Cand(g, 0.5 * taste.of(g) + 0.4 * quality(g) + (if (g.gameStatus == GameStatus.Backlog) 0.1 else 0.0) + (if (g.favorite) 0.05 else 0.0), why)
        })

        // Almost there: a few hours left by HowLongToBeat, or most of the achievements.
        add(WeekSlot.Almost, pool.filter { !it.beaten && it.playSeconds > 0 && it.gameStatus != GameStatus.Endless }.mapNotNull { g ->
            val left = if (g.ttbMain > 0) g.ttbMain - g.playSeconds else Long.MAX_VALUE
            val achFraction = if (g.achEarned >= 0 && g.achTotal >= 5) g.achEarned / g.achTotal.toDouble() else 0.0
            when {
                left in 1..(3L * HOUR) -> Cand(g, 1.0 - left / (3.0 * HOUR) + 0.2 * quality(g), WeekWhy.TimeLeft(left.toInt()))
                achFraction >= 0.7 && g.achEarned < g.achTotal -> Cand(g, achFraction + 0.2 * quality(g), WeekWhy.AchLeft(g.achEarned, g.achTotal))
                else -> null
            }
        })

        // Short tonight: not started, and done in an evening by HowLongToBeat.
        add(WeekSlot.Short, pool.filter { fresh(it) && it.ttbMain in 1..(3 * HOUR) }.map { g ->
            Cand(g, 0.5 * quality(g) + 0.3 * taste.of(g) + 0.2 * (1.0 - g.ttbMain / (3.0 * HOUR)), WeekWhy.Short(g.ttbMain))
        })

        // Because you finished X: a finish of the last six months, newest first, with more of its series or studio
        // left to play; failing that, a well-rated game of its genre.
        val anchors = visible.filter { it.beaten && maxOf(it.finishedAt, it.statusAt) > now - 180 * DAY }
            .sortedWith(compareByDescending<WeekGame> { maxOf(it.finishedAt, it.statusAt) }.thenBy { it.key }).take(3)
        fun alike(a: WeekGame): List<Cand> {
            val dev = a.developer?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
            val series = seriesOf(a.title)
            return pool.filter { fresh(it) && it.key != a.key && it.key !in used }.mapNotNull { g ->
                when {
                    series != null && seriesOf(g.title) == series && g.title != a.title -> Cand(g, 1.2 + 0.5 * quality(g), WeekWhy.Series(a.title))
                    dev != null && g.developer?.trim()?.lowercase() == dev -> Cand(g, 1.0 + 0.5 * quality(g), WeekWhy.Studio(a.title, a.developer!!.trim()))
                    else -> null
                }
            }
        }
        fun sameGenre(a: WeekGame): List<Cand> {
            val aBuckets = bucketsOf(a).toSet()
            return pool.filter { fresh(it) && it.key != a.key && it.key !in used && bucketsOf(it).any { b -> b in aBuckets } && (it.rating ?: 0f) >= 0.7f }.map { g ->
                Cand(g, 0.4 + 0.5 * quality(g), WeekWhy.SameGenre(a.title, taste.nameOf(bucketsOf(g).first { it in aBuckets })))
            }
        }
        (anchors.firstNotNullOfOrNull { a -> alike(a).takeIf { it.isNotEmpty() }?.let { a to it } }
            ?: anchors.firstNotNullOfOrNull { a -> sameGenre(a).takeIf { it.isNotEmpty() }?.let { a to it } })
            ?.let { (a, cands) -> add(WeekSlot.Because, cands, subject = a.title) }

        // Pick up where you left off: being played, last touched between three days and four months ago.
        add(WeekSlot.Resume, pool.filter { g ->
            val d = days(now, g.lastPlayed)
            g.lastPlayed > 0 && d in 3..120 && !g.beaten && g.gameStatus != GameStatus.Shelved && g.gameStatus != GameStatus.Endless &&
                (g.gameStatus == GameStatus.Playing || g.playSeconds >= 30L * 60)
        }.map { g ->
            val d = days(now, g.lastPlayed)
            Cand(g, 0.6 * (ln(1.0 + g.playSeconds / 3600.0) / ln(101.0)).coerceAtMost(1.0) + 0.4 * (1.0 - abs(d - 21) / 120.0), WeekWhy.LeftOff(d, g.playSeconds))
        })

        // Achievement hunt: sets you've started and not got far in.
        add(WeekSlot.Hunt, pool.filter { it.achEarned >= 1 && it.achTotal >= 10 && it.achEarned < it.achTotal && it.achEarned / it.achTotal.toDouble() < 0.7 }.map { g ->
            val recency = if (g.lastPlayed > 0) 0.5.pow(days(now, g.lastPlayed) / 60.0) else 0.0
            Cand(g, 0.5 * (g.achEarned / g.achTotal.toDouble()) + 0.3 * recency + 0.2 * quality(g), WeekWhy.Hunt(g.achEarned, g.achTotal))
        })

        // Hidden gem: among your best-rated, not started, outside the genres you play most.
        val ratings = pool.mapNotNull { it.rating }.sorted()
        if (ratings.isNotEmpty()) {
            val p90 = if (ratings.size >= 10) ratings[(ratings.size * 0.9).toInt().coerceAtMost(ratings.lastIndex)] else 0.8f
            val bar = maxOf(p90, 0.7f)
            val usual = taste.topGenres.take(3).toSet()
            add(WeekSlot.Gem, pool.filter { g -> fresh(g) && (g.rating ?: 0f) >= bar && bucketsOf(g).none { it in usual } }.map { g ->
                Cand(g, (g.rating ?: 0f).toDouble() + 0.1 * taste.of(g), WeekWhy.Gem(g.rating ?: 0f))
            })
        }

        // From the shelf: set aside, or waiting in the backlog, for two months; after those, games in the library
        // half a year and never started. Each kind is a tier above the next.
        add(WeekSlot.Shelf, pool.mapNotNull { g ->
            val s = g.gameStatus
            val (tier, why) = when {
                s == GameStatus.Shelved && g.statusAt > 0 && now - g.statusAt > 60 * DAY -> 2 to WeekWhy.Shelved(g.statusAt)
                s == GameStatus.Backlog && g.statusAt > 0 && now - g.statusAt > 60 * DAY -> 1 to WeekWhy.Backlogged(g.statusAt)
                s == null && g.playSeconds == 0L && g.lastPlayed == 0L && g.firstSeen > 0 && now - g.firstSeen > 180 * DAY -> 0 to WeekWhy.Untouched(g.firstSeen)
                else -> return@mapNotNull null
            }
            val since = maxOf(g.statusAt, g.firstSeen)
            Cand(g, 2.0 * tier + 0.4 * quality(g) + 0.4 * taste.of(g) + 0.2 * (days(now, since) / 365.0).coerceAtMost(1.0), why)
        })

        // New in your library: added in the last two weeks, not played. Not right after the first scan, when
        // everything is new.
        val newOnes = pool.filter { it.firstSeen > 0 && now - it.firstSeen <= 14 * DAY && it.playSeconds == 0L && it.lastPlayed == 0L }
        if (newOnes.isNotEmpty() && newOnes.size * 2 <= visible.size) {
            add(WeekSlot.New, newOnes.map { g -> Cand(g, 0.5 * quality(g) + 0.3 * taste.of(g) + 0.2 * (1.0 - days(now, g.firstSeen) / 14.0), WeekWhy.Added(days(now, g.firstSeen))) })
        }

        // Genre shelves: your favourite genre, then one you rarely play.
        fun genreCands(b: String, why: WeekWhy) = pool.filter { g -> !g.beaten && g.playSeconds < HOUR && b in bucketsOf(g) }.map { g ->
            Cand(g, 0.6 * quality(g) + 0.25 * (taste.developer[g.developer?.trim()?.lowercase()] ?: 0.0) + 0.15 * (taste.platform[g.platform] ?: 0.0), why)
        }
        if (!taste.empty) taste.topGenres.firstOrNull { b -> genreCands(b, WeekWhy.TopRated).count { it.g.key !in used } >= 3 }?.let { b ->
            add(WeekSlot.Genre, genreCands(b, WeekWhy.YourGenre(taste.nameOf(b), taste.genreSeconds[b] ?: 0L)), subject = taste.nameOf(b), min = 3)
        }
        val all = pool.flatMap { bucketsOf(it) }.groupingBy { it }.eachCount()
        val rare = all.filter { (b, count) -> count >= 4 && (taste.genre[b] ?: 0.0) < 0.15 && rows.none { it.slot == WeekSlot.Genre && it.subject == taste.nameOf(b) } }
            .keys.sorted()
        if (rare.isNotEmpty()) {
            val b = rare[Random(fnv("$week|explore|${rerolls[WeekSlot.Explore.id] ?: 0}") xor state.salt).nextInt(rare.size)]
            add(WeekSlot.Explore, genreCands(b, WeekWhy.NewGenre(taste.nameOf(b), taste.empty)), subject = taste.nameOf(b), min = 3)
        }

        // The rows in their own order (Up next and the hero first), whatever order they were filled in.
        return WeekBoardResult(week, rows.sortedBy { it.slot.ordinal })
    }

    /** Up to three facts that back a pick up, none repeating its reason. */
    private fun facts(g: WeekGame, why: WeekWhy, taste: Taste): List<WeekFact> = buildList {
        val left = if (g.ttbMain > 0 && g.playSeconds > 0) g.ttbMain - g.playSeconds else 0
        if (left > 0 && why !is WeekWhy.TimeLeft) add(WeekFact.TimeLeft(left.toInt()))
        if (g.ttbMain > 0 && g.playSeconds == 0L && why !is WeekWhy.Short) add(WeekFact.Hltb(g.ttbMain))
        if (g.achEarned >= 0 && g.achTotal > 0 && why !is WeekWhy.AchLeft && why !is WeekWhy.Hunt) add(WeekFact.Achievements(g.achEarned, g.achTotal))
        if (g.playSeconds >= 60 && why !is WeekWhy.LeftOff) add(WeekFact.Played(g.playSeconds))
        val top = bucketsOf(g).maxByOrNull { taste.genreSeconds[it] ?: 0L }
        val secs = top?.let { taste.genreSeconds[it] } ?: 0L
        if (top != null && secs >= HOUR && why !is WeekWhy.Taste && why !is WeekWhy.YourGenre) add(WeekFact.GenreTime(taste.nameOf(top), secs))
    }.take(3)

    // ------------------------------------------------------------------------------------------------ state changes

    /**
     * [state] with [board] remembered as what showed this week: its picks (not Up next, your own list) replace
     * this week's, and weeks older than [KEEP_WEEKS] go. Returns [state] itself when nothing changed.
     */
    fun remember(state: WeekState, board: WeekBoardResult, now: Long, zone: ZoneId): WeekState {
        if (board.week.isEmpty()) return state
        val keep = (weeksBefore(now, zone, KEEP_WEEKS) + board.week).toSet()
        val mine = board.picks.filter { it.slot != WeekSlot.UpNext }.map { WeekShown(board.week, it.slot.id, it.key) }
        val others = state.shown.filter { it.week != board.week && it.week in keep }
        val sameWeek = state.week == board.week
        val next = state.copy(
            shown = others + mine, week = board.week,
            rerolls = if (sameWeek) state.rerolls else emptyMap(), skipped = if (sameWeek) state.skipped else emptySet(),
        )
        return if (next == state) state else next
    }

    /** [state] after X on [slot]: its picks are skipped this week and the next draw goes a little deeper. */
    fun reroll(state: WeekState, board: WeekBoardResult, slot: WeekSlot): WeekState {
        val week = board.week
        val sameWeek = state.week == week
        val keys = board.rows.filter { it.slot == slot }.flatMap { r -> r.picks.map { it.key } }
        val rerolls = (if (sameWeek) state.rerolls else emptyMap()).let { it + (slot.id to ((it[slot.id] ?: 0) + 1)) }
        return state.copy(
            week = week, rerolls = rerolls,
            skipped = (if (sameWeek) state.skipped else emptySet()) + keys,
            shown = state.shown.filterNot { it.week == week && it.slot == slot.id },
        )
    }

    /** [state] with [key] never suggested again. */
    fun notForMe(state: WeekState, key: String): WeekState = state.copy(notForMe = state.notForMe + key)

    /** Reset suggestions: forgets not-for-me, what showed and the re-rolls; the switch and the salt stay. */
    fun reset(state: WeekState): WeekState = WeekState(on = state.on, salt = state.salt)
}
