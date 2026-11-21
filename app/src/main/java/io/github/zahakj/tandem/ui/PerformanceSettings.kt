package io.github.zahakj.tandem.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import io.github.zahakj.tandem.Game
import io.github.zahakj.tandem.PerfProfile
import io.github.zahakj.tandem.Performance
import io.github.zahakj.tandem.PerformancePlan
import io.github.zahakj.tandem.data.HomeStore
import io.github.zahakj.tandem.data.Store

/*
 * The rows for performance profiles (Performance.kt): the global profile in Settings › Play & system › Playing, and a
 * game's own in its Y menu. On a device that isn't an AYN, one note stands in for the rows.
 */

private const val KEEP_ID = "keep"
private const val PERF_KEYWORDS = "ayn thor performance mode fan speed cooling refresh rate hz 60 120 fps battery heat quiet standard high profile while playing"

/** "Keep" first, then AYN's values, as a choice row's options. */
private fun keepThen(values: List<Pair<Int, String>>): List<Pair<String, String>> = listOf(KEEP_ID to "Keep") + values.map { it.first.toString() to it.second }

/** Settings › Play & system › Playing: Performance mode, Fan mode and Refresh rate. */
@Composable
fun performanceSettings(): SettingSection {
    val ctx = LocalContext.current
    val resumes = settingsResumes()
    val ayn = remember { Performance.available(ctx) }
    // Read again each time Settings comes back in front: the user may just have allowed it in Android's page.
    val allowed = remember(resumes, ShizukuWatch.version) { Performance.canWrite(ctx) }
    val hz = remember { Performance.supportedHz(ctx) }
    val p = HomeStore.settings.perf
    fun set(change: (PerfProfile) -> PerfProfile) = HomeStore.updateSettings { it.copy(perf = change(it.perf)) }
    fun shown(v: Int?) = v?.toString() ?: KEEP_ID
    val rows = when {
        !ayn -> listOf(
            SettingRow(
                "Performance profiles", "AYN devices only", Palette.Peach, Glyph.Motion,
                "On an AYN handheld, Tandem can set the performance mode, fan mode and refresh rate while a game plays, per game or for all of them. " +
                    "This device doesn't have AYN's modes, so there is nothing to set here.",
                keywords = PERF_KEYWORDS,
            ) { Toasts.show("This device doesn't have AYN's performance modes") },
        )
        !allowed -> listOf(
            SettingRow(
                "Performance profiles", "Needs Shizuku", Palette.Peach, Glyph.Motion,
                "Set the performance mode, fan mode and refresh rate while a game plays, and put them back after. " +
                    "Tandem changes them the way AYN's control centre does; Android lets only the shell write those keys, so this needs Shizuku running with Tandem allowed.",
                keywords = PERF_KEYWORDS,
            ) { Toasts.show("This needs Shizuku running, with Tandem allowed") },
        )
        else -> listOf(
            choice(
                "Performance mode", Glyph.Motion, Palette.Peach,
                "The AYN performance mode while a game plays: Standard, Performance or High performance. Keep leaves whatever is set. " +
                    "It goes back to how it was when you come back to Tandem; a game's own choice (Y on the game) wins over this.",
                keepThen(PerformancePlan.modes), shown(p.mode),
            ) { v -> set { it.copy(mode = v.toIntOrNull()) } }.copy(keywords = PERF_KEYWORDS),
            choice(
                "Fan mode", Glyph.Refresh, Palette.Peach,
                "The fan while a game plays: Off, Quiet, Smart or Sport. Keep leaves whatever is set, and a custom speed from AYN's app is always left alone.",
                keepThen(PerformancePlan.fans), shown(p.fan),
            ) { v -> set { it.copy(fan = v.toIntOrNull()) } }.copy(keywords = PERF_KEYWORDS),
            choice(
                "Refresh rate", Glyph.Screens, Palette.Peach,
                "The top screen's refresh rate while a game plays. 60 Hz saves battery and heat; 120 Hz is smoother for games that can run that fast. Keep leaves whatever is set.",
                keepThen(hz.map { it to PerformancePlan.hzName(it) }), shown(p.hz),
            ) { v -> set { it.copy(hz = v.toIntOrNull()) } }.copy(keywords = PERF_KEYWORDS),
        )
    }
    return SettingSection("Performance while playing", rows)
}

/**
 * Y on a game › More…: the game's own profile, one row per mode. Each cycles from "Use default" (the Playing setting)
 * through "Keep" and AYN's values. Nothing on a device without AYN's modes.
 */
fun performanceActions(ctx: Context, g: Game): List<MenuAction> {
    if (!Performance.available(ctx)) return emptyList()
    val own = g.meta.perf ?: PerfProfile()
    val base = HomeStore.settings.perf
    fun next(cur: Int?, values: List<Int>, by: Int = 1): Int? {
        val all = listOf<Int?>(null, PerfProfile.KEEP) + values
        return all[Math.floorMod(all.indexOf(cur) + by, all.size)]
    }
    fun name(v: Int?, default: Int?, label: (Int) -> String) = when (v) {
        null -> "Default · " + (default?.let(label) ?: "Keep")
        PerfProfile.KEEP -> "Keep"
        else -> label(v)
    }
    // All default again: nothing of its own to keep.
    fun set(p: PerfProfile) = Store.update(g.key) { it.copy(perf = p.takeIf { it.any }) }
    val help = "For this game only; Default follows Settings › Play & system › Playing. Tap to go to the next choice."
    return listOf(
        MenuAction("perfmode", "Performance mode", Glyph.Motion, Palette.Peach, value = name(own.mode, base.mode, PerformancePlan::modeName), more = true, help = help,
            step = { by -> set(own.copy(mode = next(own.mode, PerformancePlan.modes.map { it.first }, by))) }) {
            set(own.copy(mode = next(own.mode, PerformancePlan.modes.map { it.first })))
        },
        MenuAction("perffan", "Fan mode", Glyph.Refresh, Palette.Peach, value = name(own.fan, base.fan, PerformancePlan::fanName), more = true, help = help,
            step = { by -> set(own.copy(fan = next(own.fan, PerformancePlan.fans.map { it.first }, by))) }) {
            set(own.copy(fan = next(own.fan, PerformancePlan.fans.map { it.first })))
        },
        MenuAction("perfhz", "Refresh rate", Glyph.Screens, Palette.Peach, value = name(own.hz, base.hz, PerformancePlan::hzName), more = true, help = help,
            step = { by -> set(own.copy(hz = next(own.hz, Performance.supportedHz(ctx), by))) }) {
            set(own.copy(hz = next(own.hz, Performance.supportedHz(ctx))))
        },
    )
}
