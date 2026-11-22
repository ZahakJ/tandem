package io.github.zahakj.tandem.data

import androidx.annotation.StringRes
import io.github.zahakj.tandem.R
import io.github.zahakj.tandem.Tx
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/*
 * Tandem's own trophies: earned from play history and the games' statuses, on every system (Switch games
 * included, which have no achievements of their own). Worked out from the data each time, so they can never
 * disagree with it; only the day each was first earned is kept (TrophyStore).
 */

enum class TrophyTier(@StringRes private val res: Int) {
    Bronze(R.string.stats_tier_bronze), Silver(R.string.stats_tier_silver), Gold(R.string.stats_tier_gold), Platinum(R.string.stats_tier_platinum);
    val label: String get() = Tx.s(res)
}

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

/** One trophy: [have] of [goal] so far ([goal] 1 for a yes-or-no one). Its name and how to earn it are looked up when shown. */
class Trophy(val id: String, @StringRes private val titleRes: Int, @StringRes private val detailRes: Int, val tier: TrophyTier, val goal: Long, val have: (TrophyFacts) -> Long) {
    val title: String get() = Tx.s(titleRes)
    val detail: String get() = Tx.s(detailRes)

    fun earned(f: TrophyFacts): Boolean = have(f) >= goal

    /** How far along, 0..1. */
    fun progress(f: TrophyFacts): Float = (have(f).toFloat() / goal).coerceIn(0f, 1f)
}

object TrophyList {
    private const val H = 3600L
    private fun yes(b: Boolean) = if (b) 1L else 0L

    val all: List<Trophy> = listOf(
        Trophy("first-play", R.string.stats_trophy_first_play, R.string.stats_trophy_first_play_how, TrophyTier.Bronze, 1) { it.sessionCount.toLong() },
        Trophy("hour", R.string.stats_trophy_hour, R.string.stats_trophy_hour_how, TrophyTier.Bronze, H) { it.totalSeconds },
        Trophy("ten-hours", R.string.stats_trophy_ten_hours, R.string.stats_trophy_ten_hours_how, TrophyTier.Bronze, 10 * H) { it.totalSeconds },
        Trophy("hundred-hours", R.string.stats_trophy_hundred_hours, R.string.stats_trophy_hundred_hours_how, TrophyTier.Gold, 100 * H) { it.totalSeconds },
        Trophy("thousand-hours", R.string.stats_trophy_thousand_hours, R.string.stats_trophy_thousand_hours_how, TrophyTier.Platinum, 1000 * H) { it.totalSeconds },
        Trophy("marathon", R.string.stats_trophy_marathon, R.string.stats_trophy_marathon_how, TrophyTier.Silver, 3 * H) { it.longestSession },
        Trophy("devoted", R.string.stats_trophy_devoted, R.string.stats_trophy_devoted_how, TrophyTier.Silver, 25 * H) { it.mostInOneGame },
        Trophy("second-home", R.string.stats_trophy_second_home, R.string.stats_trophy_second_home_how, TrophyTier.Gold, 100 * H) { it.mostInOneGame },
        Trophy("week", R.string.stats_trophy_week, R.string.stats_trophy_week_how, TrophyTier.Silver, 7) { it.bestStreak.toLong() },
        Trophy("month", R.string.stats_trophy_month, R.string.stats_trophy_month_how, TrophyTier.Gold, 30) { it.bestStreak.toLong() },
        Trophy("regular", R.string.stats_trophy_regular, R.string.stats_trophy_regular_how, TrophyTier.Silver, 100) { it.sessionCount.toLong() },
        Trophy("night-owl", R.string.stats_trophy_night_owl, R.string.stats_trophy_night_owl_how, TrophyTier.Bronze, 1) { yes(it.lateNight) },
        Trophy("early-bird", R.string.stats_trophy_early_bird, R.string.stats_trophy_early_bird_how, TrophyTier.Bronze, 1) { yes(it.earlyMorning) },
        Trophy("travelled", R.string.stats_trophy_travelled, R.string.stats_trophy_travelled_how, TrophyTier.Bronze, 3) { it.systemsPlayed.toLong() },
        Trophy("globetrotter", R.string.stats_trophy_globetrotter, R.string.stats_trophy_globetrotter_how, TrophyTier.Silver, 8) { it.systemsPlayed.toLong() },
        Trophy("sampler", R.string.stats_trophy_sampler, R.string.stats_trophy_sampler_how, TrophyTier.Silver, 25) { it.gamesPlayed.toLong() },
        Trophy("plans", R.string.stats_trophy_plans, R.string.stats_trophy_plans_how, TrophyTier.Bronze, 5) { it.backlog.toLong() },
        Trophy("credits", R.string.stats_trophy_credits, R.string.stats_trophy_credits_how, TrophyTier.Bronze, 1) { it.finished.toLong() },
        Trophy("closer", R.string.stats_trophy_closer, R.string.stats_trophy_closer_how, TrophyTier.Silver, 5) { it.finished.toLong() },
        Trophy("finisher", R.string.stats_trophy_finisher, R.string.stats_trophy_finisher_how, TrophyTier.Gold, 25) { it.finished.toLong() },
        Trophy("completionist", R.string.stats_trophy_completionist, R.string.stats_trophy_completionist_how, TrophyTier.Silver, 1) { it.completed.toLong() },
        Trophy("perfectionist", R.string.stats_trophy_perfectionist, R.string.stats_trophy_perfectionist_how, TrophyTier.Gold, 10) { it.completed.toLong() },
        Trophy("monthly", R.string.stats_trophy_monthly, R.string.stats_trophy_monthly_how, TrophyTier.Gold, 12) { it.bestYear.toLong() },
        Trophy("collector", R.string.stats_trophy_collector, R.string.stats_trophy_collector_how, TrophyTier.Bronze, 100) { it.libraryGames.toLong() },
        Trophy("archivist", R.string.stats_trophy_archivist, R.string.stats_trophy_archivist_how, TrophyTier.Silver, 500) { it.libraryGames.toLong() },
    )

    /** Every other trophy earned. */
    val master = Trophy("master", R.string.stats_trophy_master, R.string.stats_trophy_master_how, TrophyTier.Platinum, all.size.toLong()) { f -> all.count { it.earned(f) }.toLong() }

    val withMaster: List<Trophy> = all + master
}
