package io.github.zahakj.tandem.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import io.github.zahakj.tandem.Game
import io.github.zahakj.tandem.PerfProfile
import io.github.zahakj.tandem.Performance
import io.github.zahakj.tandem.PerformancePlan
import io.github.zahakj.tandem.R
import io.github.zahakj.tandem.Tx
import io.github.zahakj.tandem.data.HomeStore
import io.github.zahakj.tandem.data.Store

/*
 * The rows for performance profiles (Performance.kt): the global profile in Settings › Play & system › Performance & sleep, and a
 * game's own in its Y menu. On a device that isn't an AYN, one note stands in for the rows.
 */

private const val KEEP_ID = "keep"
private const val PERF_KEYWORDS = "ayn thor performance mode fan speed cooling refresh rate hz 60 120 fps battery heat quiet standard high profile while playing"

/** "Keep" first, then AYN's values, as a choice row's options. */
private fun keepThen(values: List<Int>, name: (Int) -> String): List<Pair<String, String>> =
    listOf(KEEP_ID to Tx.s(R.string.play_perf_keep)) + values.map { it.toString() to name(it) }

/** Settings › Play & system › Performance & sleep: Performance mode, Fan mode and Refresh rate. */
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
                Tx.s(R.string.play_perf_profiles), Tx.s(R.string.play_perf_ayn_only), Palette.Peach, Glyph.Motion,
                Tx.s(R.string.play_perf_ayn_only_help),
                keywords = PERF_KEYWORDS, en = "Performance profiles",
            ) { Toasts.show(Tx.s(R.string.play_perf_ayn_only_toast)) },
        )
        !allowed -> listOf(
            SettingRow(
                Tx.s(R.string.play_perf_profiles), Tx.s(R.string.play_perf_needs_shizuku), Palette.Peach, Glyph.Motion,
                Tx.s(R.string.play_perf_needs_shizuku_help),
                keywords = PERF_KEYWORDS, en = "Performance profiles",
            ) { Toasts.show(Tx.s(R.string.play_perf_needs_shizuku_toast)) },
        )
        else -> listOf(
            choice(
                Tx.s(R.string.play_perf_mode), Glyph.Motion, Palette.Peach, Tx.s(R.string.play_perf_mode_help),
                keepThen(PerformancePlan.modes.map { it.first }, PerformancePlan::modeName), shown(p.mode),
                en = "Performance mode",
            ) { v -> set { it.copy(mode = v.toIntOrNull()) } }.copy(keywords = PERF_KEYWORDS),
            choice(
                Tx.s(R.string.play_perf_fan), Glyph.Refresh, Palette.Peach, Tx.s(R.string.play_perf_fan_help),
                keepThen(PerformancePlan.fans.map { it.first }, PerformancePlan::fanName), shown(p.fan),
                en = "Fan mode",
            ) { v -> set { it.copy(fan = v.toIntOrNull()) } }.copy(keywords = PERF_KEYWORDS),
            choice(
                Tx.s(R.string.play_perf_refresh), Glyph.Screens, Palette.Peach, Tx.s(R.string.play_perf_refresh_help),
                keepThen(hz, PerformancePlan::hzName), shown(p.hz),
                en = "Refresh rate",
            ) { v -> set { it.copy(hz = v.toIntOrNull()) } }.copy(keywords = PERF_KEYWORDS),
        )
    }
    return SettingSection(Tx.s(R.string.play_perf_group), rows, en = "Performance while playing")
}

/**
 * Y on a game › More…: the game's own profile, one row per mode. A shows each mode's options, from "Default" (the
 * Playing setting) through "Keep" and AYN's values; Left and Right step through them. Nothing on a device without
 * AYN's modes.
 */
fun performanceActions(ctx: Context, g: Game): List<MenuAction> {
    if (!Performance.available(ctx)) return emptyList()
    val own = g.meta.perf ?: PerfProfile()
    val base = HomeStore.settings.perf
    fun all(values: List<Int>) = listOf<Int?>(null, PerfProfile.KEEP) + values
    fun next(cur: Int?, values: List<Int>, by: Int = 1): Int? {
        val all = all(values)
        return all[Math.floorMod(all.indexOf(cur) + by, all.size)]
    }
    fun name(v: Int?, default: Int?, label: (Int) -> String) = when (v) {
        null -> Tx.s(R.string.play_perf_default_is, default?.let(label) ?: Tx.s(R.string.play_perf_keep))
        PerfProfile.KEEP -> Tx.s(R.string.play_perf_keep)
        else -> label(v)
    }
    // All default again: nothing of its own to keep.
    fun set(p: PerfProfile) = Store.update(g.key) { it.copy(perf = p.takeIf { it.any }) }
    /** Every option of one mode, the one in use ticked; Default says what it follows, Keep what it means. */
    fun options(cur: Int?, values: List<Int>, default: Int?, label: (Int) -> String, pick: (Int?) -> Unit) = all(values).map { v ->
        MenuChoice(
            when (v) { null -> Tx.s(R.string.play_perf_default); PerfProfile.KEEP -> Tx.s(R.string.play_perf_keep); else -> label(v) }, v == cur,
            sub = when (v) {
                null -> Tx.s(R.string.play_perf_as_in_settings, default?.let(label) ?: Tx.s(R.string.play_perf_keep))
                PerfProfile.KEEP -> Tx.s(R.string.play_perf_keep_sub)
                else -> null
            },
        ) { pick(v) }
    }
    val help = Tx.s(R.string.play_perf_game_help)
    val modes = PerformancePlan.modes.map { it.first }
    val fans = PerformancePlan.fans.map { it.first }
    val hz = Performance.supportedHz(ctx)
    return listOf(
        MenuAction("perfmode", Tx.s(R.string.play_perf_mode), Glyph.Motion, Palette.Peach, value = name(own.mode, base.mode, PerformancePlan::modeName), more = true, help = help,
            step = { by -> set(own.copy(mode = next(own.mode, modes, by))) },
            choices = options(own.mode, modes, base.mode, PerformancePlan::modeName) { v -> set(own.copy(mode = v)) }) {
            set(own.copy(mode = next(own.mode, modes)))
        },
        MenuAction("perffan", Tx.s(R.string.play_perf_fan), Glyph.Refresh, Palette.Peach, value = name(own.fan, base.fan, PerformancePlan::fanName), more = true, help = help,
            step = { by -> set(own.copy(fan = next(own.fan, fans, by))) },
            choices = options(own.fan, fans, base.fan, PerformancePlan::fanName) { v -> set(own.copy(fan = v)) }) {
            set(own.copy(fan = next(own.fan, fans)))
        },
        MenuAction("perfhz", Tx.s(R.string.play_perf_refresh), Glyph.Screens, Palette.Peach, value = name(own.hz, base.hz, PerformancePlan::hzName), more = true, help = help,
            step = { by -> set(own.copy(hz = next(own.hz, hz, by))) },
            choices = options(own.hz, hz, base.hz, PerformancePlan::hzName) { v -> set(own.copy(hz = v)) }) {
            set(own.copy(hz = next(own.hz, hz)))
        },
    )
}
