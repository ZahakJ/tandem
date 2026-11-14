package io.github.zahakj.tandem.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/*
 * Tandem's own trophies: earned from play history and the games' statuses, on every system (Switch games
 * included, which have no achievements of their own). Worked out from the data each time, so they can never
 * disagree with it; only the day each was first earned is kept (TrophyStore).
 */

enum class TrophyTier(val label: String) { Bronze("Bronze"), Silver("Silver"), Gold("Gold"), Platinum("Platinum") }

/** What the trophies look at, gathered once from the library and the sessions. */
class TrophyFacts(metas: List<GameMeta>, sessions: List<PlaySession>, val libraryGames: Int, zone: ZoneId = ZoneId.systemDefault()) {
    val totalSeconds: Long = metas.sumOf { it.playSeconds }
    val mostInOneGame: Long = metas.maxOfOrNull { it.playSeconds } ?: 0
    val longestSession: Long = sessions.maxOfOrNull { it.d } ?: 0
    val sessionCount: Int = sessions.size
    val gamesPlayed: Int = metas.count { it.playCount > 0 || it.playSeconds > 0 || it.lastPlayed > 0 }
    val finished: Int = metas.count { it.playStatus?.beaten == true }
    val completed: Int = metas.count { it.playStatus == GameStatus.Completed }
    val backlog: Int = metas.count { it.playStatus == GameStatus.Backlog }

    /** The most games first finished in any one calendar year. */
    val bestYear: Int = metas.filter { it.finishedAt > 0 }
        .groupingBy { Instant.ofEpochMilli(it.finishedAt).atZone(zone).year }.eachCount().values.maxOrNull() ?: 0

    private val hours: List<Int> = sessions.map { Instant.ofEpochMilli(it.s).atZone(zone).hour }
    val lateNight: Boolean = hours.any { it in 2..4 }
    val earlyMorning: Boolean = hours.any { it in 5..6 }

    /** The longest run of days in a row with any play. */
    val bestStreak: Int = run {
        val days = sessions.map { Instant.ofEpochMilli(it.s).atZone(zone).toLocalDate() }.toSortedSet()
        var best = 0
        var run = 0
        var prev: LocalDate? = null
        for (d in days) {
            run = if (prev != null && prev.plusDays(1) == d) run + 1 else 1
            best = maxOf(best, run)
            prev = d
        }
        best
    }

    /** Systems with any play, by the system part of the session keys ("snes/Game.sfc"). */
    val systemsPlayed: Int = sessions.mapTo(HashSet()) { it.k.substringBefore('/') }.size
}

/** One trophy: [have] of [goal] so far ([goal] 1 for a yes-or-no one). */
class Trophy(val id: String, val title: String, val detail: String, val tier: TrophyTier, val goal: Long, val have: (TrophyFacts) -> Long) {
    fun earned(f: TrophyFacts): Boolean = have(f) >= goal

    /** How far along, 0..1. */
    fun progress(f: TrophyFacts): Float = (have(f).toFloat() / goal).coerceIn(0f, 1f)
}

object TrophyList {
    private const val H = 3600L
    private fun yes(b: Boolean) = if (b) 1L else 0L

    val all: List<Trophy> = listOf(
        Trophy("first-play", "Press start", "Play your first game in Tandem", TrophyTier.Bronze, 1) { it.sessionCount.toLong() },
        Trophy("hour", "One more level", "Play for an hour in all", TrophyTier.Bronze, H) { it.totalSeconds },
        Trophy("ten-hours", "Settling in", "Play for 10 hours in all", TrophyTier.Bronze, 10 * H) { it.totalSeconds },
        Trophy("hundred-hours", "Centurion", "Play for 100 hours in all", TrophyTier.Gold, 100 * H) { it.totalSeconds },
        Trophy("thousand-hours", "Lifer", "Play for 1,000 hours in all", TrophyTier.Platinum, 1000 * H) { it.totalSeconds },
        Trophy("marathon", "Marathon", "Play for 3 hours in one sitting", TrophyTier.Silver, 3 * H) { it.longestSession },
        Trophy("devoted", "Devoted", "Spend 25 hours in one game", TrophyTier.Silver, 25 * H) { it.mostInOneGame },
        Trophy("second-home", "Second home", "Spend 100 hours in one game", TrophyTier.Gold, 100 * H) { it.mostInOneGame },
        Trophy("week", "Week on", "Play 7 days in a row", TrophyTier.Silver, 7) { it.bestStreak.toLong() },
        Trophy("month", "Unbroken", "Play 30 days in a row", TrophyTier.Gold, 30) { it.bestStreak.toLong() },
        Trophy("regular", "Regular", "Play 100 sessions", TrophyTier.Silver, 100) { it.sessionCount.toLong() },
        Trophy("night-owl", "Night owl", "Start a game between 2 and 5 at night", TrophyTier.Bronze, 1) { yes(it.lateNight) },
        Trophy("early-bird", "Early bird", "Start a game before 7 in the morning", TrophyTier.Bronze, 1) { yes(it.earlyMorning) },
        Trophy("travelled", "Well travelled", "Play games on 3 systems", TrophyTier.Bronze, 3) { it.systemsPlayed.toLong() },
        Trophy("globetrotter", "Globetrotter", "Play games on 8 systems", TrophyTier.Silver, 8) { it.systemsPlayed.toLong() },
        Trophy("sampler", "Sampler", "Play 25 different games", TrophyTier.Silver, 25) { it.gamesPlayed.toLong() },
        Trophy("plans", "Big plans", "Put 5 games in the backlog", TrophyTier.Bronze, 5) { it.backlog.toLong() },
        Trophy("credits", "Credits roll", "Finish a game", TrophyTier.Bronze, 1) { it.finished.toLong() },
        Trophy("closer", "Closer", "Finish 5 games", TrophyTier.Silver, 5) { it.finished.toLong() },
        Trophy("finisher", "Finisher", "Finish 25 games", TrophyTier.Gold, 25) { it.finished.toLong() },
        Trophy("completionist", "Completionist", "Complete a game 100%", TrophyTier.Silver, 1) { it.completed.toLong() },
        Trophy("perfectionist", "Perfectionist", "Complete 10 games 100%", TrophyTier.Gold, 10) { it.completed.toLong() },
        Trophy("monthly", "A game a month", "Finish 12 games in one year", TrophyTier.Gold, 12) { it.bestYear.toLong() },
        Trophy("collector", "Collector", "Have 100 games in your library", TrophyTier.Bronze, 100) { it.libraryGames.toLong() },
        Trophy("archivist", "Archivist", "Have 500 games in your library", TrophyTier.Silver, 500) { it.libraryGames.toLong() },
    )

    /** Every other trophy earned. */
    val master = Trophy("master", "Tandem master", "Earn every other trophy", TrophyTier.Platinum, all.size.toLong()) { f -> all.count { it.earned(f) }.toLong() }

    val withMaster: List<Trophy> = all + master
}
