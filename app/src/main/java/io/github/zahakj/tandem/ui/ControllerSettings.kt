package io.github.zahakj.tandem.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import io.github.zahakj.tandem.ControllerHomeService
import io.github.zahakj.tandem.Gamepads
import io.github.zahakj.tandem.Lang
import io.github.zahakj.tandem.MainActivity
import io.github.zahakj.tandem.R
import io.github.zahakj.tandem.Tx
import io.github.zahakj.tandem.data.ControllerStore

/** The English names of these rows' groups (SettingSection.en), picked into Settings › Controls by [settingsCatalog]. */
const val HomeButtonGroup = "Controller Home button"
const val ControllersGroup = "Game controllers"
const val PadLinkGroup = "Play with a controller"

/**
 * Settings › Controls and its Connected controllers page: the controller Home button (an optional accessibility service, see ControllerHomeService.kt)
 * the controllers connected now, each with its own Nintendo-labels swap, and which one plays as the built-in
 * controller in games (PadLink.kt).
 */
/**
 * [MainActivity.resumeCount] as Settings sees it: it stays composed while hidden (KeptScreen), and a return to Tandem
 * would otherwise rebuild every row of it on the home. Here it only moves while Settings is open (and catches up as
 * it opens).
 */
@Composable
fun settingsResumes(): Int {
    val live = (LocalContext.current as? MainActivity)?.resumeCount ?: return 0
    val seen = androidx.compose.runtime.remember(live) { androidx.compose.runtime.mutableIntStateOf(live.intValue) }
    androidx.compose.runtime.LaunchedEffect(live) {
        androidx.compose.runtime.snapshotFlow { if (Nav.inSettings) live.intValue else -1 }.collect { if (it >= 0) seen.intValue = it }
    }
    return seen.intValue
}

/** Connected controllers (descriptor to name) as choices, two alike numbered so each has its own name. */
private fun padChoices(pads: List<Pair<String, String>>): List<Pair<String, String>> {
    val seen = HashMap<String, Int>()
    return pads.map { (descriptor, name) ->
        val n = (seen[name] ?: 0) + 1
        seen[name] = n
        descriptor to if (n > 1) Tx.s(R.string.controls_pad_numbered, name, n) else name
    }
}

@Composable
fun controllerSettings(): List<SettingSection> {
    val ctx = LocalContext.current
    val resumes = settingsResumes()
    // Re-checked when coming back from Android's Accessibility settings.
    val serviceOn = remember(resumes) { ControllerHomeService.enabled(ctx) }
    // Switched off there on purpose: Tandem won't switch it back on by itself (A11yHeal).
    androidx.compose.runtime.LaunchedEffect(resumes) { io.github.zahakj.tandem.A11yHeal.backFromSettings(ctx) }
    val version = Gamepads.version
    // The name for a controller without one is looked up outside remember, so it follows the language.
    val unnamed = Tx.s(R.string.controls_pad_unnamed)
    val pads = remember(version, resumes) { Gamepads.connected().map { it.descriptor to it.name.orEmpty() } }.map { (d, n) -> d to n.ifBlank { unnamed } }
    val cs = ControllerStore.settings

    val home = SettingSection(
        Tx.s(R.string.controls_group_home_button),
        listOf(
            SettingRow(
                Tx.s(R.string.controls_home_button), onOff(serviceOn), Palette.Lavender, Glyph.Home,
                Tx.s(R.string.controls_home_button_help),
                opens = true, action = Tx.s(R.string.controls_open), keywords = "guide xbox button mode accessibility emulator bluetooth gamepad",
                en = "Controller Home button",
            ) {
                io.github.zahakj.tandem.A11yHeal.openingSettings(ctx)
                val tries = listOf(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS), Intent(Settings.ACTION_SETTINGS))
                for (i in tries) if (runCatching { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess) break
            },
            choice(
                Tx.s(R.string.controls_hold_home), Glyph.Swap, Palette.Lavender, Tx.s(R.string.controls_hold_home_help),
                listOf("switcher" to Tx.s(R.string.controls_hold_home_switcher), "recents" to Tx.s(R.string.controls_hold_home_recents)), cs.holdHome,
                en = "Hold the Home button",
            ) { v -> ControllerStore.update { it.copy(holdHome = v) } },
        ),
        Glyph.Home, en = HomeButtonGroup,
    )

    // Two identical pads get numbered, so each row has its own name.
    val names = padChoices(pads)
    val padRows = pads.mapIndexed { i, (descriptor, name) ->
        val label = names[i].second
        val on = descriptor in cs.nintendo
        toggle(
            label, Glyph.Gamepad, Palette.Lavender, Tx.s(R.string.controls_pad_nintendo_help), on,
        ) { set ->
            ControllerStore.update { s ->
                if (set) s.copy(nintendo = s.nintendo + descriptor, names = s.names + (descriptor to name))
                else s.copy(nintendo = s.nintendo - descriptor, names = s.names - descriptor)
            }
        }.copy(keywords = "nintendo labels swap a b x y layout switch pro bluetooth gamepad $name")
    }
    val rows = padRows.ifEmpty {
        listOf(
            SettingRow(
                Tx.s(R.string.controls_no_pads), Tx.s(R.string.controls_no_pads_value), Palette.Lavender, Glyph.Gamepad,
                Tx.s(R.string.controls_no_pads_help),
                keywords = "bluetooth gamepad nintendo labels swap", en = "No other controllers",
            ) { Toasts.show(Tx.s(R.string.controls_no_pads_toast)) },
        )
    }

    // Controller passthrough (PadLink.kt): which controller plays as the built-in one in games.
    val linkState = io.github.zahakj.tandem.PadLink.state
    val linkReady = remember(version, resumes, ShizukuWatch.version) { io.github.zahakj.tandem.PadLink.available(ctx) }
    val linkRows = if (!linkReady) {
        listOf(
            SettingRow(
                Tx.s(R.string.controls_link), Tx.s(R.string.controls_link_unavailable), Palette.Lavender, Glyph.Gamepad,
                remember(resumes, Lang.revision.intValue) { io.github.zahakj.tandem.PadLink.whyNot(ctx) },
                keywords = "passthrough xbox bluetooth controller player 1 shizuku root", en = "Controller in games",
            ) { Toasts.show(io.github.zahakj.tandem.PadLink.whyNot(ctx)) },
        )
    } else {
        listOf(
            choice(
                Tx.s(R.string.controls_link), Glyph.Gamepad, Palette.Lavender, Tx.s(R.string.controls_link_help),
                // Built-in, then each connected controller by name (two alike get numbered, as on Connected controllers).
                listOf("" to Tx.s(R.string.controls_link_builtin)) + padChoices(pads),
                linkState.linked.orEmpty(),
                en = "Controller in games",
            ) { v -> io.github.zahakj.tandem.PadLink.choose(v.ifEmpty { null }) }
                .copy(keywords = "passthrough xbox bluetooth controller player 1 switch use this controller ${pads.joinToString(" ") { it.second }}"),
            toggle(
                Tx.s(R.string.controls_link_auto), Glyph.Gamepad, Palette.Lavender, Tx.s(R.string.controls_link_auto_help),
                cs.usePads,
                en = "Always use a connected pad",
            ) { on ->
                ControllerStore.update { it.copy(usePads = on) }
                io.github.zahakj.tandem.PadLink.devicesChanged()
            }.copy(keywords = "auto passthrough xbox bluetooth controller player 1"),
        )
    }
    val linkNote = if (linkState.linked != null) Tx.s(R.string.controls_link_in_use, io.github.zahakj.tandem.PadLink.linked?.name.toString()) else null
    val link = SettingSection(Tx.s(R.string.controls_group_pad_link), linkRows, Glyph.Gamepad, note = linkNote, en = PadLinkGroup)
    val controllers = SettingSection(Tx.s(R.string.controls_group_controllers), rows, Glyph.Gamepad, note = Tx.s(R.string.controls_pads_note), en = ControllersGroup)
    return listOf(home, controllers, link)
}
