package io.github.zahakj.tandem.ui

import androidx.compose.runtime.Composable
import io.github.zahakj.tandem.GameCompanion
import io.github.zahakj.tandem.R
import io.github.zahakj.tandem.ScreenOff
import io.github.zahakj.tandem.Tx
import io.github.zahakj.tandem.data.HomeStore

/**
 * The bottom screen while a game plays, for everyone at once (Settings › Two screens › While playing): which of the
 * companion's faces it shows unless a system or game chose its own (GameCompanion.kt), and the Off after timer that
 * turns any of them dark after some minutes without a touch (ScreenOff.kt).
 */
@Composable
fun screenOffSettings(): SettingSection {
    val s = HomeStore.settings
    return SettingSection(Tx.s(R.string.screens_off_group), en = "Screen off", rows = listOf(
        choice(
            Tx.s(R.string.screens_off_while_playing), Glyph.Screens, Palette.Lavender, Tx.s(R.string.screens_off_while_playing_help),
            GameCompanion.choices, s.companion ?: GameCompanion.ART,
            en = "Bottom screen while playing",
            look = { mode ->
                DeviceScene(
                    Shows.Game,
                    when (mode) { GameCompanion.TANDEM -> Shows.Companion; GameCompanion.DARK -> Shows.Off; GameCompanion.OFF -> Shows.Emulator; else -> Shows.Art },
                )
            },
        ) { mode -> HomeStore.updateSettings { it.copy(companion = mode.takeIf { m -> m != GameCompanion.ART }) } }
            .copy(keywords = "screen off dark black oled battery companion game art leave to emulator bottom screen while playing dim"),
        choice(
            Tx.s(R.string.screens_off_after), Glyph.Clock, Palette.Lavender, Tx.s(R.string.screens_off_after_help),
            ScreenOff.OFF_AFTER_MINUTES.map { it.toString() to ScreenOff.offAfterLabel(it) }, s.companionOffAfter.toString(),
            en = "Off after",
        ) { minutes -> HomeStore.updateSettings { it.copy(companionOffAfter = minutes.toIntOrNull() ?: 0) } }
            .copy(keywords = "idle timer timeout minutes dark screen off battery bottom screen dim never"),
    ))
}
