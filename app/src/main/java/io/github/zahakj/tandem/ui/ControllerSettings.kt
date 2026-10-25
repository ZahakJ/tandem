package io.github.zahakj.tandem.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import io.github.zahakj.tandem.ControllerHomeService
import io.github.zahakj.tandem.Gamepads
import io.github.zahakj.tandem.MainActivity
import io.github.zahakj.tandem.data.ControllerStore

/** Group titles of these rows, picked into Settings › Controls by [settingsCatalog]. */
const val HomeButtonGroup = "Controller Home button"
const val ControllersGroup = "Game controllers"

/**
 * Settings › Controls: the controller Home button (an optional accessibility service, see ControllerHomeService.kt)
 * and the controllers connected now, each with its own Nintendo-labels swap.
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

@Composable
fun controllerSettings(): List<SettingSection> {
    val ctx = LocalContext.current
    val resumes = settingsResumes()
    // Re-checked when coming back from Android's Accessibility settings.
    val serviceOn = remember(resumes) { ControllerHomeService.enabled(ctx) }
    // Switched off there on purpose: Tandem won't switch it back on by itself (A11yHeal).
    androidx.compose.runtime.LaunchedEffect(resumes) { io.github.zahakj.tandem.A11yHeal.backFromSettings(ctx) }
    val version = Gamepads.version
    val pads = remember(version, resumes) { Gamepads.connected().map { it.descriptor to it.name.orEmpty().ifBlank { "Controller" } } }
    val cs = ControllerStore.settings

    val home = SettingSection(
        HomeButtonGroup,
        listOf(
            SettingRow(
                "Controller Home button", if (serviceOn) "On" else "Off", Palette.Lavender, Glyph.Home,
                "Many controllers in X-input mode send their Home " +
                    "(guide) button as a button Android ignores. Inside Tandem it always works like Home. To make it work in " +
                    "games and other apps too, switch on \"Tandem: Controller Home button\" in Android's Accessibility " +
                    "settings (A opens them; if it is greyed out, allow restricted settings in Tandem's App info first). " +
                    "Tandem then looks only at that one button, only from external controllers; the built-in controls " +
                    "are never touched.",
                opens = true, action = "Open", keywords = "guide xbox button mode accessibility emulator bluetooth gamepad",
            ) {
                io.github.zahakj.tandem.A11yHeal.openingSettings(ctx)
                val tries = listOf(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS), Intent(Settings.ACTION_SETTINGS))
                for (i in tries) if (runCatching { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess) break
            },
            choice(
                "Hold the Home button", Glyph.Swap, Palette.Lavender,
                "What holding a controller's Home button does: Tandem's game switcher, or Android's recent apps (needs " +
                    "Controller Home button on; without it, the game switcher).",
                listOf("switcher" to "Game switcher", "recents" to "Recent apps"), cs.holdHome,
            ) { v -> ControllerStore.update { it.copy(holdHome = v) } },
        ),
        Glyph.Home,
    )

    // Two identical pads get numbered, so each row has its own name.
    val seen = HashMap<String, Int>()
    val padRows = pads.map { (descriptor, name) ->
        val n = (seen[name] ?: 0) + 1
        seen[name] = n
        val label = if (n > 1) "$name $n" else name
        val on = descriptor in cs.nintendo
        toggle(
            label, Glyph.Gamepad, Palette.Lavender,
            "Nintendo button labels: swaps A/B and X/Y for this controller only, so the button printed A confirms. " +
                "Use it for Switch-style pads in X-input mode. It applies inside Tandem; the built-in controls and " +
                "other controllers are unaffected, and Swap A and B above still applies on top. Emulators have their " +
                "own per-controller button mapping in their settings.",
            on,
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
                "No other controllers", "Connect one", Palette.Lavender, Glyph.Gamepad,
                "Pair a Bluetooth controller or plug one in and it shows up here, with its own Nintendo button labels " +
                    "switch (swap A/B and X/Y for that controller only). The built-in controls follow Swap A and B above.",
                keywords = "bluetooth gamepad nintendo labels swap",
            ) { Toasts.show("Pair or plug in a controller, it appears here") },
        )
    }
    return listOf(home, SettingSection(ControllersGroup, rows, Glyph.Gamepad, note = "On: Nintendo labels"))
}
