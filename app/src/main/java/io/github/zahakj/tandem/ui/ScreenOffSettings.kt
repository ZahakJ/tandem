package io.github.zahakj.tandem.ui

import androidx.compose.runtime.Composable
import io.github.zahakj.tandem.GameCompanion
import io.github.zahakj.tandem.ScreenOff
import io.github.zahakj.tandem.data.HomeStore

/**
 * The bottom screen while a game plays, for everyone at once (Settings › Two screens › While playing): which of the
 * companion's faces it shows unless a system or game chose its own (GameCompanion.kt), and the Off after timer that
 * turns any of them dark after some minutes without a touch (ScreenOff.kt).
 */
@Composable
fun screenOffSettings(): SettingSection {
    val s = HomeStore.settings
    return SettingSection(
        "Screen off",
        listOf(
            choice(
                "Bottom screen while playing", Glyph.Screens, Palette.Lavender,
                "What the bottom screen shows while a game plays on the top one: the game's art, Tandem's companion, " +
                    "Screen off (black and still until you tap it, the biggest battery saving on this screen) or nothing at all, for an emulator " +
                    "that draws there itself. 3DS and DS emulators keep their own screen whatever this says. Y on a system or a game chooses for " +
                    "that one alone.",
                GameCompanion.choices, s.companion ?: GameCompanion.ART,
                look = { mode ->
                    DeviceScene(
                        Shows.Game,
                        when (mode) { GameCompanion.TANDEM -> Shows.Companion; GameCompanion.DARK -> Shows.Off; GameCompanion.OFF -> Shows.Emulator; else -> Shows.Art },
                    )
                },
            ) { mode -> HomeStore.updateSettings { it.copy(companion = mode.takeIf { m -> m != GameCompanion.ART }) } }
                .copy(keywords = "screen off dark black oled battery companion game art leave to emulator bottom screen while playing dim"),
            choice(
                "Off after", Glyph.Clock, Palette.Lavender,
                "Minutes without a touch on the bottom screen before it goes dark while a game plays, whatever it shows, until you tap it. " +
                    "Never keeps the dimmed art or companion up all game long. With Screen off, this is also how long a tap lights the screen.",
                ScreenOff.OFF_AFTER_MINUTES.map { it.toString() to ScreenOff.offAfterLabel(it) }, s.companionOffAfter.toString(),
            ) { minutes -> HomeStore.updateSettings { it.copy(companionOffAfter = minutes.toIntOrNull() ?: 0) } }
                .copy(keywords = "idle timer timeout minutes dark screen off battery bottom screen dim never"),
        ),
    )
}
