package io.github.zahakj.tandem.ui

import io.github.zahakj.tandem.FileChecks
import io.github.zahakj.tandem.R
import io.github.zahakj.tandem.Tx
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import io.github.zahakj.tandem.Libraries
import io.github.zahakj.tandem.Library
import io.github.zahakj.tandem.Selected
import io.github.zahakj.tandem.Selection
import io.github.zahakj.tandem.Shell
import io.github.zahakj.tandem.data.Importer
import io.github.zahakj.tandem.data.SortOrder
import io.github.zahakj.tandem.data.Store
import java.io.File

/**
 * One row on a settings screen: a label, its current value, and what pressing A does.
 *
 * Rows come in five kinds, drawn the same way everywhere (see [kind]): a switch ([toggle]), a choice or a slider
 * the D-pad steps through ([onLeft]/[onRight]; [slider] adds a small gauge), a link to another screen ([opens]),
 * and an action (anything else). [help] is shown in the side panel next to the live [preview]; [keywords] are extra
 * words Settings search matches (synonyms, the old name of a setting).
 */
data class SettingRow(
    val label: String,
    val value: String,
    val color: Color = Palette.Mint,
    val icon: Glyph? = null,
    val help: String? = null,
    val toggle: Boolean? = null,
    val opens: Boolean = false,
    val onLeft: (() -> Unit)? = null,
    val onRight: (() -> Unit)? = null,
    val preview: (@Composable () -> Unit)? = null,
    val keywords: String = "",
    /** For number rows: where the value sits between its minimum and maximum (0..1), drawn as a small gauge. */
    val slider: Float? = null,
    /** What A does, when the row kind doesn't say it well ("Show", "Hide"). */
    val action: String? = null,
    /** For choice rows: every option (id to label), so the row can show them all (see Choices.kt), and the one in use. */
    val options: List<Pair<String, String>>? = null,
    val selected: String? = null,
    /** Chooses an option by id: from the segmented control, the picker, or a tap. */
    val pick: ((String) -> Unit)? = null,
    /** A miniature of one option, for the picker's cards. */
    val optionLook: (@Composable (String) -> Unit)? = null,
    /** One more card at the end of the picker that does something else (Type a colour…), and what it does. */
    val pickerExtra: Pair<String, () -> Unit>? = null,
    /** For number rows the theme can decide: whether it does now. Null for rows without a "Theme" value. */
    val themed: Boolean? = null,
    /**
     * What the row is, in every language: its English label, which SettingsPlan places it by, focus and search jumps
     * remember it by, and English search finds it by. Pass it whenever [label] comes from the string resources
     * (docs/TRANSLATING.md); it never shows.
     */
    val en: String = label,
    val onClick: () -> Unit,
) {
    val kind: RowKind get() = when {
        toggle != null -> RowKind.Toggle
        slider != null -> RowKind.Slider
        options != null || onLeft != null || onRight != null -> RowKind.Choice
        opens -> RowKind.Link
        else -> RowKind.Action
    }

    /** A choice whose options all fit on the row, as a segmented control. */
    val segmented: Boolean get() = options != null && fitsOnRow(options)

    /** A choice with too many options (or too long) for the row: A opens [ChoicePicker]. */
    val picker: Boolean get() = options != null && !segmented

    /** What A does, for the hints. */
    val aLabel: String get() = action ?: when (kind) {
        RowKind.Toggle -> Tx.s(R.string.settings_a_switch)
        RowKind.Choice -> Tx.s(if (picker) R.string.common_choose else R.string.settings_a_next)
        RowKind.Slider -> Tx.s(R.string.settings_a_next)
        RowKind.Link -> Tx.s(R.string.settings_a_open)
        RowKind.Action -> Tx.s(R.string.settings_a_do)
    }

    /** The value as words (switches read On/Off). */
    val shownValue: String get() = if (toggle != null) onOff(toggle) else value
}

enum class RowKind { Toggle, Choice, Slider, Link, Action }

/**
 * A titled group of rows, every one of them shown: Settings folds nothing away (rarer rows get a short page of their
 * own instead, see SettingsPlan). [note] is a short aside shown next to the title ("For the icon grid").
 */
data class SettingSection(
    val title: String,
    val rows: List<SettingRow>,
    val icon: Glyph? = null,
    val note: String? = null,
    /** The group's English title, never shown: what SettingsPlan picks rows by (see [SettingRow.en]). */
    val en: String = title,
)

/**
 * The open Settings page's summary, which the details screen explains while nothing on the page has published a
 * row (the first frames after it opens, a gallery without a row, the selection reset when a screen opens).
 */
object SettingsIdle {
    private var last by mutableStateOf<Selected.Setting?>(null)

    /**
     * The summary to show: the page last opened, kept for screens reached from it (the sound tester, say), and
     * none on the page tiles, where the top bar shows Pip's greeting.
     */
    val page: Selected.Setting? get() = last.takeIf { Nav.inSettings && Nav.route != Route.Settings }

    /** Publishes [summary] from its page at [route], from the first frame it composes (not while it fades out). */
    @Composable
    fun Publish(summary: Selected.Setting, route: Route) {
        androidx.compose.runtime.SideEffect { if (Nav.route == route) last = summary }
    }
}

/** Settings screen state that outlives a screen: the row search jumped to. */
object SettingsUi {
    /** The row to pulse once (after a search jump), and a counter so the same row can pulse again. */
    var flashKey by mutableStateOf<String?>(null)
        private set
    var flashSerial by mutableIntStateOf(0)
        private set
    private var flashAt = 0L
    fun flash(key: String) { flashKey = key; flashSerial++; flashAt = android.os.SystemClock.uptimeMillis() }

    /** True for a short while after a jump to [key], so a row scrolled back into view later doesn't pulse again. */
    fun shouldFlash(key: String?) = key != null && key == flashKey && android.os.SystemClock.uptimeMillis() - flashAt < 2500
}

/** The focus key of a row: its group's and its own English names ([SettingRow.en]). Search uses the same keys. */
fun rowKey(section: String, label: String) = "$section/$label"

/**
 * [LibraryScan.scanning] as Settings sees it. Settings stays composed while hidden (KeptScreen), and Tandem rescans
 * every time you come back to it: read directly, the flag rebuilt every row of the hidden Settings twice per return
 * (about 10 ms of the main thread each). Here it only follows while Settings is open.
 */
@Composable
private fun settingsScanning(): Boolean {
    val seen = remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        androidx.compose.runtime.snapshotFlow { Nav.inSettings && LibraryScan.scanning }.collect { seen.value = it }
    }
    return seen.value
}

@Composable
fun librarySettings(onRescan: () -> Unit): SettingSection {
    val s = Store.settings
    val scanning = settingsScanning()
    // Recomposes only when the number changes, not on every change to a game's details (a scrape makes many).
    val gameCount by remember { androidx.compose.runtime.derivedStateOf { io.github.zahakj.tandem.Collections.visibleGames().size } }
    val manuals = io.github.zahakj.tandem.Manuals.count
    return SettingSection(Tx.s(R.string.settings_library), en = "Library", rows = listOf(
        choice(
            Tx.s(R.string.settings_library_sort), Glyph.Sort, Palette.Mint, Tx.s(R.string.settings_library_sort_help),
            SortOrder.entries.map { it.name to it.label }, s.sort.name,
            en = "Sort games by",
        ) { v -> Store.updateSettings { it.copy(sort = SortOrder.valueOf(v)) } }.copy(keywords = "order alphabetical recent"),
        SettingRow(
            Tx.s(R.string.settings_library_hidden),
            if (io.github.zahakj.tandem.Privacy.enabled) Tx.s(R.string.settings_library_hidden_private) else onOff(s.showHidden), Palette.Lavender, Glyph.Eye,
            toggle = if (io.github.zahakj.tandem.Privacy.enabled) null else s.showHidden,
            help = Tx.s(R.string.settings_library_hidden_help),
            keywords = "unhide hidden folders systems tuck away list",
            en = "Show hidden games",
        ) { toggleShowHidden() },
        SettingRow(
            Tx.s(R.string.settings_library_rescan),
            when {
                scanning -> Tx.s(R.string.settings_library_rescan_looking)
                manuals == 0 -> Tx.p(R.plurals.settings_library_games, gameCount)
                else -> Tx.s(R.string.settings_library_games_manuals, Tx.p(R.plurals.settings_library_games, gameCount), Tx.p(R.plurals.settings_library_manuals, manuals))
            },
            Palette.Mint, Glyph.Refresh,
            help = Tx.s(R.string.settings_library_rescan_help),
            keywords = "refresh scan reload find new games manual guide pdf cbz walkthrough instructions booklet faq book read",
            en = "Rescan games",
            onClick = onRescan,
        ),
    ))
}


/** What a status row says for an app's library ([io.github.zahakj.tandem.AppLibraries.Status]): its value and help. */
private class AppLibraryTexts(
    val found: Int, val shared: Int, val root: Int, val notInstalled: Int, val notInstalledHelp: Int,
    val needsRoot: Int, val needsRootHelp: Int, val off: Int, val checking: Int,
)

private fun appLibraryStatus(status: io.github.zahakj.tandem.AppLibraries.Status, t: AppLibraryTexts): Pair<String, String> = when (status) {
    is io.github.zahakj.tandem.AppLibraries.Status.Found -> Tx.p(t.found, status.count) to Tx.s(if (status.shared) t.shared else t.root)
    io.github.zahakj.tandem.AppLibraries.Status.NotInstalled -> Tx.s(t.notInstalled) to Tx.s(t.notInstalledHelp)
    io.github.zahakj.tandem.AppLibraries.Status.NeedsRoot -> Tx.s(t.needsRoot) to Tx.s(t.needsRootHelp)
    io.github.zahakj.tandem.AppLibraries.Status.Off -> onOff(false) to Tx.s(t.off)
    io.github.zahakj.tandem.AppLibraries.Status.Checking -> Tx.s(R.string.settings_library_rescan_looking) to Tx.s(t.checking)
}

/**
 * DroidDeck's installed games in the Windows system (AppLibraries.kt): what Tandem found there, every store counted,
 * and the switch. A on the status looks again (a rescan).
 */
@Composable
fun droidDeckSettings(onRescan: () -> Unit): SettingSection {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val on = io.github.zahakj.tandem.data.SystemPrefs.data.droidDeck
    val scanning = settingsScanning()
    val (value, help) = appLibraryStatus(io.github.zahakj.tandem.AppLibraries.droidDeck, AppLibraryTexts(
        R.plurals.library_droiddeck_found, R.string.library_droiddeck_help_shared, R.string.library_droiddeck_help_root,
        R.string.library_droiddeck_not_installed, R.string.library_droiddeck_help_not_installed,
        R.string.library_droiddeck_needs_root, R.string.library_droiddeck_help_needs_root,
        R.string.library_droiddeck_help_off, R.string.library_droiddeck_help_checking,
    ))
    return SettingSection("DroidDeck", rows = listOf( // tx-ok: an app's name
        SettingRow(
            Tx.s(R.string.library_droiddeck_status), if (scanning) Tx.s(R.string.settings_library_rescan_looking) else value, Palette.Sky, Glyph.Refresh,
            help = help,
            keywords = "droiddeck steam epic gog amazon xbox ubisoft ea rockstar battlenet itch gamejolt proton pc windows games installed import root library store",
            en = "DroidDeck library",
            onClick = onRescan,
        ),
        toggle(
            Tx.s(R.string.library_droiddeck_show), Glyph.Cartridge, Palette.Sky, Tx.s(R.string.library_droiddeck_show_help), on,
            en = "Show DroidDeck games",
        ) { v ->
            io.github.zahakj.tandem.data.SystemPrefs.update { it.copy(droidDeck = v) }
            LibraryScan.request(ctx, announce = false, onlyIfChanged = false)
        }.copy(keywords = "droiddeck steam epic gog amazon store pc windows games import automatic"),
    ))
}

/**
 * GameNative's downloaded games in the Windows system (AppLibraries.kt): what Tandem found there, every store counted,
 * and the switch. A on the status looks again (a rescan).
 */
@Composable
fun gameNativeSettings(onRescan: () -> Unit): SettingSection {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val on = io.github.zahakj.tandem.data.SystemPrefs.data.gameNative
    val scanning = settingsScanning()
    // GameNative has no provider: it is always read with root.
    val (value, help) = appLibraryStatus(io.github.zahakj.tandem.AppLibraries.gameNative, AppLibraryTexts(
        R.plurals.library_gamenative_found, R.string.library_gamenative_help_root, R.string.library_gamenative_help_root,
        R.string.library_gamenative_not_installed, R.string.library_gamenative_help_not_installed,
        R.string.library_gamenative_needs_root, R.string.library_gamenative_help_needs_root,
        R.string.library_gamenative_help_off, R.string.library_gamenative_help_checking,
    ))
    return SettingSection("GameNative", rows = listOf( // tx-ok: an app's name
        SettingRow(
            Tx.s(R.string.library_gamenative_status), if (scanning) Tx.s(R.string.settings_library_rescan_looking) else value, Palette.Sky, Glyph.Refresh,
            help = help,
            keywords = "gamenative steam epic gog amazon pc windows games downloaded installed import root library store",
            en = "GameNative library",
            onClick = onRescan,
        ),
        toggle(
            Tx.s(R.string.library_gamenative_show), Glyph.Cartridge, Palette.Sky, Tx.s(R.string.library_gamenative_show_help), on,
            en = "Show GameNative games",
        ) { v ->
            io.github.zahakj.tandem.data.SystemPrefs.update { it.copy(gameNative = v) }
            LibraryScan.request(ctx, announce = false, onlyIfChanged = false)
        }.copy(keywords = "gamenative steam epic gog amazon store pc windows games import automatic"),
    ))
}


@Composable
fun dataSettings(): SettingSection {
    return SettingSection(Tx.s(R.string.settings_data), en = "Your data", rows = listOf(
        SettingRow(
            Tx.s(R.string.settings_data_import),
            if (DataJobs.busy == "import") Tx.s(R.string.settings_data_importing) else "Cocoon · ES-DE", // tx-ok: app names
            Palette.Sky, icon = Glyph.Download,
            help = Tx.s(R.string.settings_data_import_help),
            keywords = "gamelist xml es-de emulationstation metadata",
            en = "Import details",
        ) {
            DataJobs.run("import", Tx.s(R.string.settings_data_importing_details), { Importer.importAll(Libraries.all.orEmpty()).games }) { n ->
                if (n > 0) Tx.p(R.plurals.settings_data_imported, n) else Tx.s(R.string.settings_data_import_none)
            }
        },
        SettingRow(
            Tx.s(R.string.settings_data_back_up),
            if (DataJobs.busy == "backup") Tx.s(R.string.settings_data_saving) else "ROMs/tandem/backup", // tx-ok: a folder
            Palette.Sky, icon = Glyph.Save,
            help = Tx.s(R.string.settings_data_back_up_help),
            keywords = "back up tandem backup export save copy snapshot layout folders",
            opens = true,
            en = "Back up",
        ) { BackupUi.openBackUp() },
        SettingRow(
            Tx.s(R.string.settings_data_restore),
            Tx.s(if (DataJobs.busy == "restore") R.string.settings_data_restoring else R.string.settings_data_restore_pick),
            Palette.Sky, icon = Glyph.Restore,
            help = Tx.s(R.string.settings_data_restore_help),
            keywords = "restore backup import load snapshot undo",
            opens = true,
            en = "Restore",
        ) { BackupUi.openRestore() },
    ))
}

@Composable
fun aboutSettings(): SettingSection {
    // Follows Shizuku starting, stopping and the permission answer, not just coming back to Tandem.
    val ready = remember(ShizukuWatch.version) { Shell.ready() }
    return SettingSection(Tx.s(R.string.settings_about), en = "About", rows = listOf(
        SettingRow(
            Tx.s(R.string.settings_about_resume), Tx.s(if (ready) R.string.settings_about_resume_ready else R.string.settings_about_resume_allow),
            Palette.Lavender, icon = Glyph.Gamepad,
            help = Tx.s(R.string.settings_about_resume_help),
            keywords = "shizuku continue suspend",
            en = "Resume running games",
        ) { askShizuku() },
    ) + updateRows().ifEmpty {
        listOf(
            SettingRow(
                Tx.s(R.string.settings_about_tandem), Tx.s(R.string.settings_about_version, io.github.zahakj.tandem.BuildConfig.VERSION_NAME),
                Palette.Lavender, icon = Glyph.Heart,
                help = Tx.s(R.string.settings_about_help), keywords = "version license open source credits",
                en = "About Tandem",
            ) { Toasts.show(Tx.s(R.string.settings_about_hi)) },
        )
    })
}

/**
 * Updates (this version, where an update stands, and A to check) and Check automatically (Updates.kt), standing in
 * for About Tandem; none in a build without the updater (F-Droid), which shows About Tandem instead.
 */
@Composable
private fun updateRows(): List<SettingRow> {
    if (!io.github.zahakj.tandem.BuildConfig.SELF_UPDATE) return emptyList()
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val updates = io.github.zahakj.tandem.Updates
    val found = updates.found
    return listOf(
        SettingRow(
            Tx.s(R.string.settings_updates), updates.statusLine(), Palette.Lavender, icon = Glyph.Download,
            help = Tx.s(R.string.settings_updates_help, io.github.zahakj.tandem.BuildConfig.VERSION_NAME, Tx.s(R.string.settings_about_help)),
            keywords = "update upgrade new version release download install github check now about license open source credits",
            action = Tx.s(if (found != null) R.string.settings_updates_see else R.string.settings_updates_check),
            en = "Updates",
        ) { if (found != null) UpdatePopup.show() else updates.check(ctx) },
        toggle(
            Tx.s(R.string.settings_updates_auto), Glyph.Refresh, Palette.Lavender, Tx.s(R.string.settings_updates_auto_help),
            Store.settings.checkUpdates,
            en = "Check automatically",
        ) { on -> Store.updateSettings { it.copy(checkUpdates = on) } }.copy(keywords = "check for updates automatic daily github notification notify"),
    )
}

/** Asks Shizuku for permission (or explains what to do first). Used by Settings and the first-run welcome. */
fun askShizuku() {
    val alive = runCatching { rikka.shizuku.Shizuku.pingBinder() }.getOrDefault(false)
    when {
        Shell.ready() -> Toasts.show(Tx.s(R.string.settings_shizuku_on))
        alive -> rikka.shizuku.Shizuku.requestPermission(1)
        else -> Toasts.show(Tx.s(R.string.settings_shizuku_start))
    }
}

/** On or Off, in Tandem's language. */
fun onOff(b: Boolean) = Tx.s(if (b) R.string.common_on else R.string.common_off)

/**
 * Slow data work from Settings (import, backup, restore): runs off the main thread with a toast when it starts and
 * when it's done, one at a time, so the screen never freezes and a second press can't start it twice.
 */
object DataJobs {
    var busy by mutableStateOf<String?>(null)
        private set

    fun <T> run(id: String, starting: String, work: () -> T, done: (T) -> String) {
        if (busy != null) { Toasts.show(Tx.s(R.string.settings_job_busy)); return }
        busy = id
        Toasts.show(starting)
        kotlinx.coroutines.MainScope().launch {
            val result = runCatching { withContext(Dispatchers.IO) { work() } }
            busy = null
            Toasts.show(result.fold(done) { Tx.s(R.string.settings_job_failed, it.message ?: Tx.s(R.string.settings_job_unknown)) })
        }
    }
}

/**
 * One row of the settings list. The first row of a group carries the group's [heading], drawn above it in the same
 * list item, so highlighting the row brings its heading into view in the same scroll (see [showsFromTop]).
 */
private class SettingsLine(val section: SettingSection, val row: SettingRow, val heading: SettingSection?) {
    val key = rowKey(section.en, row.en)
}

/**
 * How much room focus scrolling keeps around the highlighted settings row: more than a row (with its gap and the
 * edge fade), so the next row is already whole on screen when the highlight reaches it.
 */
val SettingsScrollRoom = 88.dp

/**
 * The two-pane settings layout: grouped rows on the left, every one of them shown, and on the right a panel that
 * explains the highlighted setting (with a live preview where one helps). [header] adds extra items above the
 * sections (the theme gallery on Look & feel) and may report focus through its callback to change the side panel.
 */
@Composable
fun SettingsLayout(
    routeKey: String,
    sections: List<SettingSection>,
    sidePanelFallback: @Composable () -> Unit = { AboutPanel() },
    /**
     * Items above the sections (galleries). [header] must add exactly one list item per entry of [headerKeys], each
     * entry listing the focus keys inside that item; it gets the focus target and a callback for when one of them
     * is focused. Each item brings its own title along when highlighted ([showsFromTop]).
     */
    header: (LazyListScope.(target: String?, onFocusKey: (String) -> Unit) -> Unit)? = null,
    headerKeys: List<List<String>> = emptyList(),
    headerRow: (String) -> SettingRow? = { null },
) {
    // Written as the highlight moves and read only by the side panel, so a move doesn't recompose the list.
    val focusedKey = remember { mutableStateOf(FocusMemory.get(routeKey)) }
    val lines = sections.flatMap { s -> s.rows.mapIndexed { i, r -> SettingsLine(s, r, heading = s.takeIf { i == 0 }) } }
    // Rows come and go (signing in adds some); the remembered item, or its neighbour, gets focus and is scrolled to.
    // Galleries and rows share one target, so focus can never end up nowhere.
    val target = rememberFocusTarget(routeKey, headerKeys.flatten() + lines.map { it.key })
    val state = rememberRouteListState(routeKey)
    run {
        var index = headerKeys.indexOfFirst { target != null && target in it }
        if (index < 0) lines.indexOfFirst { it.key == target }.takeIf { it >= 0 }?.let { index = headerKeys.size + it }
        KeepInView(state, index, room = SettingsScrollRoom)
    }
    val onFocus: (String, SettingRow?) -> Unit = { key, row ->
        FocusMemory.remember(routeKey, key)
        focusedKey.value = key
        // The bottom screen explains the setting too.
        Selection.current = row?.let { Selected.Setting(it.label, it.shownValue, it.help, it.color, it.aLabel) } ?: Selected.Home
    }
    Row(Modifier.fillMaxSize().padding(start = 14.dp, end = 22.dp)) {
        ListScrolling(state, SettingsScrollRoom) {
            LazyColumn(
                // Rows scrolled under the top bar or off the bottom fade out instead of being cut in half.
                Modifier.weight(1.3f).fillMaxHeight().fadingEdges(state),
                state = state,
                // Room on every side for the focused row to grow and glow (the fade mask clips to the list).
                contentPadding = PaddingValues(start = FocusRoomSide, end = FocusRoomSide, top = FocusRoom, bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // A gallery above the rows is explained on the details screen like a row (or, without a row of its own,
                // by the page's summary: see SettingsIdle).
                header?.invoke(this, target) { key -> onFocus(key, headerRow(key)) }
                // contentType lets the list reuse a scrolled-away row's layout for the next row of the same kind.
                itemsIndexed(lines, key = { _, it -> it.key }, contentType = { _, it -> if (it.heading != null) 1 else 0 }) { i, line ->
                    // The first row of the page (with no gallery above) brings the page's very top along.
                    val top = if (i == 0 && headerKeys.isEmpty()) FocusRoom else 0.dp
                    Column(if (line.heading != null || top > 0.dp) Modifier.showsFromTop(top) else Modifier) {
                        line.heading?.let { SettingsHeader(it.title, it.note); Spacer(Modifier.height(8.dp)) }
                        SettingRowCard(line.row, Modifier.takesFocus(routeKey, line.key, line.key == target), flashKey = line.key) {
                            onFocus(line.key, line.row)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(0.8f).fillMaxHeight().padding(top = FocusRoom, bottom = 22.dp)) {
            SettingsSide(focusedKey, lines, headerRow, sidePanelFallback)
        }
    }
}

/** The side panel for the highlighted row; its own scope, so the highlight moving recomposes only the panel. */
@Composable
private fun SettingsSide(focusedKey: androidx.compose.runtime.State<String?>, lines: List<SettingsLine>, headerRow: (String) -> SettingRow?, fallback: @Composable () -> Unit) {
    val key = focusedKey.value
    SidePanel(key?.let(headerRow) ?: lines.firstOrNull { it.key == key }?.row, fallback)
}

/** A section heading in a settings list: small, spaced capitals with a hairline, and an optional aside. */
@Composable
fun SettingsHeader(title: String, note: String? = null) {
    Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 2.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title.uppercase(io.github.zahakj.tandem.Lang.locale), style = MaterialTheme.typography.labelLarge, color = Palette.Muted, letterSpacing = androidx.compose.ui.unit.TextUnit(1.4f, androidx.compose.ui.unit.TextUnitType.Sp), maxLines = 1)
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f).height(1.dp).background(Palette.Muted.copy(alpha = 0.22f)))
        if (note != null) {
            Spacer(Modifier.width(10.dp))
            Text(note, style = MaterialTheme.typography.labelMedium, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 200.dp))
        }
    }
}

/**
 * One settings row as a card: icon chip, label, and on the right what kind of row it is: a switch, every option as a
 * segmented control (or the value and a grid mark when A opens the picker), a slider for numbers, a chevron for
 * links. [flashKey] pulses the row once when search jumps to it.
 */
@Composable
fun SettingRowCard(row: SettingRow, modifier: Modifier = Modifier, flashKey: String? = null, onFocus: () -> Unit) {
    val stepper = row.onLeft != null || row.onRight != null
    // A jump from search pulses the row's outline twice, so the eye finds it.
    val flash = remember { Animatable(0f) }
    val serial = SettingsUi.flashSerial
    LaunchedEffect(serial) {
        if (SettingsUi.shouldFlash(flashKey)) {
            repeat(2) { flash.animateTo(1f, tween(260)); flash.animateTo(0f, tween(420)) }
        }
    }
    FocusCard(
        modifier.fillMaxWidth().onPreviewKeyEvent { e ->
            // Left and Right step the way the arrow points: in right to left the options and the slider run leftwards.
            val handler = when (e.sideStep()) { -1 -> row.onLeft; 1 -> row.onRight; else -> null }
            if (handler == null) return@onPreviewKeyEvent false
            if (e.type == KeyEventType.KeyDown) { handler(); Sounds.toggle() }
            true
        },
        glow = row.color, shape = RoundedCornerShape(16.dp), onFocus = onFocus,
        onClick = { row.onClick(); if (row.toggle != null) Sounds.toggle(!row.toggle) },
    ) { focused ->
        Row(
            Modifier.fillMaxWidth()
                .border(2.dp, row.color.copy(alpha = flash.value * 0.9f), RoundedCornerShape(16.dp))
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (row.icon != null) {
                GlyphChip(row.icon, row.color)
                Spacer(Modifier.width(12.dp))
            }
            LabelAndEnd(Modifier.weight(1f)) {
                Text(
                    row.label,
                    style = MaterialTheme.typography.titleMedium,
                    color = Palette.Text,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                when {
                    row.kind == RowKind.Toggle -> Toggle(row.toggle == true, row.color)
                    row.slider != null -> ValueTrack(row.slider, row.color, value = row.value, themed = row.themed, focused = focused, trackWidth = 132.dp)
                    row.segmented -> {
                        val options = row.options.orEmpty()
                        SegmentTrack(options.map { it.second }, options.indexOfFirst { it.first == row.selected }, row.color, Modifier.widthIn(max = 360.dp)) { i ->
                            val id = options[i].first
                            if (id != row.selected) { row.pick?.invoke(id); Sounds.toggle() }
                        }
                    }
                    row.picker -> PickValue(row.value, row.color, Modifier.widthIn(max = 240.dp))
                    // A row that steps without saying its options (built outside Settings, like the smart folder form).
                    else -> Row(Modifier.widthIn(max = 220.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (stepper) Text("‹", style = MaterialTheme.typography.titleLarge, color = if (focused) row.color else Palette.Muted.copy(alpha = 0.35f))
                        if (stepper) Spacer(Modifier.width(6.dp))
                        if (row.value.isNotEmpty()) ValuePill(row.value, row.color, Modifier.weight(1f, fill = false))
                        if (stepper) Spacer(Modifier.width(6.dp))
                        if (stepper) Text("›", style = MaterialTheme.typography.titleLarge, color = if (focused) row.color else Palette.Muted.copy(alpha = 0.35f))
                        if (row.kind == RowKind.Link) {
                            Spacer(Modifier.width(8.dp))
                            Text("›", style = MaterialTheme.typography.titleLarge, color = Palette.Muted)
                        }
                    }
                }
            }
        }
    }
}

/**
 * A row's label and the control at its end. The control is measured first but may not take the room the label
 * needs: the label keeps its own width up to half the row, and the control fits what is left (a segmented control
 * shrinks its options to it).
 */
@Composable
private fun LabelAndEnd(modifier: Modifier, content: @Composable () -> Unit) {
    Layout(content, modifier) { measurables, constraints ->
        val total = constraints.maxWidth
        val gap = 10.dp.roundToPx()
        val label = measurables[0]
        val end = measurables.getOrNull(1)
        val keep = minOf(label.maxIntrinsicWidth(Constraints.Infinity), total / 2)
        val endP = end?.measure(Constraints(maxWidth = (total - keep - gap).coerceAtLeast(0)))
        val endW = endP?.let { it.width + gap } ?: 0
        val labelP = label.measure(Constraints(maxWidth = (total - endW).coerceAtLeast(0)))
        val h = maxOf(labelP.height, endP?.height ?: 0)
        layout(total, h) {
            // Relative: the label at the start, the control at the end, whichever side that is.
            labelP.placeRelative(0, (h - labelP.height) / 2)
            endP?.placeRelative(total - endP.width, (h - endP.height) / 2)
        }
    }
}

/** A value tag that ellipsizes instead of pushing the label away. */
@Composable
fun ValuePill(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.18f)).padding(horizontal = 10.dp, vertical = 3.dp)) {
        FitText(text, MaterialTheme.typography.labelLarge, color = color)
    }
}

/** An on/off switch whose knob slides across. */
@Composable
fun Toggle(on: Boolean, color: Color) {
    val x by animateDpAsState(if (on) 18.dp else 0.dp, Motion.focus(), label = "knob")
    val track by animateColorAsState(if (on) color else Palette.Muted.copy(alpha = 0.35f), Motion.fade(180), label = "track")
    Box(Modifier.size(width = 42.dp, height = 24.dp).clip(CircleShape).background(track).padding(3.dp)) {
        Box(Modifier.offset(x = x).size(18.dp).clip(CircleShape).background(if (on) Palette.OnAccent else Palette.Text))
    }
}

/**
 * Explains the highlighted setting, with a live preview when the row has one. The panel is laid out in fixed
 * bands (title, help, preview, hints) and the preview is scaled down to the space it gets, so a tall preview can
 * never spill over the text or the hints. Old and new contents barely overlap while they cross-fade.
 */
@Composable
private fun SidePanel(row: SettingRow?, fallback: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp)).background(Palette.Card.copy(alpha = 0.72f)).padding(18.dp),
    ) {
        AnimatedContent(
            row,
            transitionSpec = { fadeIn(tween(160, delayMillis = 70)) togetherWith fadeOut(tween(70)) },
            contentKey = { it?.label }, label = "side",
        ) { r ->
            if (r == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { fallback() }
            } else {
                SidePanelContent(r)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SidePanelContent(r: SettingRow) {
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (r.icon != null) { GlyphChip(r.icon, r.color, 40.dp); Spacer(Modifier.width(12.dp)) }
            Column(Modifier.weight(1f)) {
                Text(r.label, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (r.shownValue.isNotEmpty()) {
                    Text(r.shownValue, style = MaterialTheme.typography.titleSmall, color = r.color, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Column(Modifier.fillMaxWidth().weight(1f).clipToBounds()) {
            if (r.preview != null) {
                // Help is cut short (five lines) so the preview keeps its room; the preview shrinks to fit what's left.
                if (r.help != null) Text(r.help, style = MaterialTheme.typography.bodyMedium, color = Palette.Muted, maxLines = 5, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(12.dp))
                FitInside(Modifier.fillMaxWidth().weight(1f)) { r.preview.invoke() }
            } else {
                val help = rememberScrollState()
                Box(Modifier.fillMaxSize().fadingEdges(help).verticalScroll(help)) {
                    if (r.help != null) Text(r.help, style = MaterialTheme.typography.bodyMedium, color = Palette.Muted)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        SettingsHints(r)
    }
}

/**
 * The button hints under the side panel: what A does for this kind of row (Next on a segmented choice, Choose where
 * it opens the picker), ◀▶ to move along the choices or slide a number, search and back.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsHints(r: SettingRow?) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (r != null) ButtonHint("A", r.aLabel, r.color)
        if (r != null && (r.onLeft != null || r.onRight != null)) {
            ButtonHint("◀▶", Tx.s(if (r.kind == RowKind.Slider) R.string.settings_hint_adjust else R.string.settings_hint_change), r.color)
        }
        ButtonHint("X", Tx.s(R.string.settings_hint_search), Palette.Sky)
        ButtonHint("B", Tx.s(R.string.common_back), Palette.Muted)
    }
}

/**
 * Lays [content] out at the full width and its natural height, then scales it down (keeping its shape) when that
 * height doesn't fit, centred in the space. Previews of any size fit the side panel this way.
 */
@Composable
fun FitInside(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Layout(content, modifier.clipToBounds()) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = if (constraints.hasBoundedHeight) constraints.maxHeight else Constraints.Infinity
        val placeables = measurables.map { it.measure(Constraints(maxWidth = width)) }
        val natural = placeables.maxOfOrNull { it.height } ?: 0
        val boxH = if (height == Constraints.Infinity) natural else height
        layout(width, boxH) {
            placeables.forEach { p ->
                val scale = if (p.height > boxH && p.height > 0) boxH.toFloat() / p.height else 1f
                val x = (width - p.width) / 2
                val y = (boxH - p.height) / 2
                p.placeWithLayer(x, y) { scaleX = scale; scaleY = scale }
            }
        }
    }
}

@Composable
private fun AboutPanel() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Pip(104.dp)
        Spacer(Modifier.height(10.dp))
        Text("Tandem", style = MaterialTheme.typography.titleLarge) // tx-ok: the app's name
        Text(Tx.s(R.string.settings_about_panel), style = MaterialTheme.typography.bodyMedium, color = Palette.Muted, textAlign = TextAlign.Center)
    }
}
