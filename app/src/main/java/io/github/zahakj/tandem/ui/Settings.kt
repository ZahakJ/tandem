package io.github.zahakj.tandem.ui

import io.github.zahakj.tandem.FileChecks
import io.github.zahakj.tandem.plural
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
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
    val onClick: () -> Unit,
) {
    val kind: RowKind get() = when {
        toggle != null -> RowKind.Toggle
        slider != null -> RowKind.Slider
        onLeft != null || onRight != null -> RowKind.Choice
        opens -> RowKind.Link
        else -> RowKind.Action
    }

    /** What A does, for the hints. */
    val aLabel: String get() = action ?: when (kind) {
        RowKind.Toggle -> "Switch"
        RowKind.Slider, RowKind.Choice -> "Next"
        RowKind.Link -> "Open"
        RowKind.Action -> "Do it"
    }

    /** The value as words (switches read On/Off). */
    val shownValue: String get() = if (toggle != null) onOff(toggle) else value
}

enum class RowKind { Toggle, Choice, Slider, Link, Action }

/**
 * A titled group of rows. [rows] are the common ones; [more] are tucked away under "More options" until asked
 * for. [note] is a short aside shown next to the title ("For the icon grid").
 */
data class SettingSection(
    val title: String,
    val rows: List<SettingRow>,
    val icon: Glyph? = null,
    val more: List<SettingRow> = emptyList(),
    val note: String? = null,
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

/** Settings screen state that outlives a screen: open "More options" groups and the row search jumped to. */
object SettingsUi {
    private val expanded = mutableStateMapOf<String, Boolean>()
    fun isOpen(route: String, group: String) = expanded["$route|$group"] == true
    fun setOpen(route: String, group: String, open: Boolean) { expanded["$route|$group"] = open }

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

/** The focus key of a row: its group and label. Search and the "More options" switch use the same keys. */
fun rowKey(section: String, label: String) = "$section/$label"
private fun moreKey(section: String) = "more:$section"

@Composable
fun librarySettings(onRescan: () -> Unit): SettingSection {
    val s = Store.settings
    // Recomposes only when the number changes, not on every change to a game's details (a scrape makes many).
    val gameCount by remember { androidx.compose.runtime.derivedStateOf { io.github.zahakj.tandem.Collections.visibleGames().size } }
    return SettingSection(
        "Library",
        listOf(
            choice(
                "Sort games by", Glyph.Sort, Palette.Mint, "The order games appear in inside each system and collection.",
                SortOrder.entries.map { it.name to it.label }, s.sort.name,
            ) { v -> Store.updateSettings { it.copy(sort = SortOrder.valueOf(v)) } }.copy(keywords = "order alphabetical recent"),
            SettingRow(
                "Show game titles", onOff(s.showTitles), Palette.Mint, Glyph.Text, toggle = s.showTitles,
                help = "Write each game's name under its card. Handy when box art looks alike.", keywords = "names labels",
            ) { Store.updateSettings { it.copy(showTitles = !it.showTitles) } },
            SettingRow(
                "Show hidden games", if (io.github.zahakj.tandem.Privacy.enabled) "Private" else onOff(s.showHidden), Palette.Mint, Glyph.Eye,
                toggle = if (io.github.zahakj.tandem.Privacy.enabled) null else s.showHidden,
                help = "Games, folders and systems you hid with Y stay out of sight unless this is on. Private mode always keeps them hidden.",
                keywords = "unhide",
            ) { toggleShowHidden() },
            SettingRow(
                "Rescan games",
                if (LibraryScan.scanning) "Looking…" else plural(gameCount, "game"),
                Palette.Mint, Glyph.Refresh,
                help = "Look through the ROMs folder again, for games you just copied over. Tandem also checks by itself whenever you come back to it.",
                keywords = "refresh scan reload find new games",
                onClick = onRescan,
            ),
        ),
    )
}

@Composable
fun dataSettings(): SettingSection {
    return SettingSection(
        "Your data",
        listOf(
            SettingRow(
                "Import details", if (DataJobs.busy == "import") "Importing…" else "Cocoon · ES-DE", Palette.Sky, icon = Glyph.Download,
                help = "Fill in names, descriptions and artwork links from gamelist.xml files (ES-DE, Cocoon backups). Only blanks are filled: what you changed in Tandem stays.",
                keywords = "gamelist xml es-de emulationstation metadata",
            ) {
                DataJobs.run("import", "Importing details…", { Importer.importAll(Libraries.all.orEmpty()).games }) { n ->
                    if (n > 0) "Filled in details for ${plural(n, "game")}" else "No gamelist.xml files found"
                }
            },
            SettingRow(
                "Back up", if (DataJobs.busy == "backup") "Saving…" else "ROMs/tandem/backup", Palette.Sky, icon = Glyph.Save,
                help = "Saves a new backup next to your games: game data, look, layout and folders, systems and emulators. " +
                    "Older backups stay, so you can go back to any of them after a reinstall.",
                keywords = "back up tandem backup export save copy snapshot layout folders",
                opens = true,
            ) { BackupUi.openBackUp() },
            SettingRow(
                "Restore", if (DataJobs.busy == "restore") "Restoring…" else "Pick a backup", Palette.Sky, icon = Glyph.Restore,
                help = "Pick a backup, newest first, then what to put back. Tandem saves an automatic backup of how things are now before it restores.",
                keywords = "restore backup import load snapshot undo",
                opens = true,
            ) { BackupUi.openRestore() },
        ),
    )
}

@Composable
fun aboutSettings(): SettingSection {
    // Follows Shizuku starting, stopping and the permission answer, not just coming back to Tandem.
    val ready = remember(ShizukuWatch.version) { Shell.ready() }
    return SettingSection(
        "About",
        listOf(
            SettingRow(
                "Resume running games", if (ready) "Ready" else "Allow in Shizuku", Palette.Lavender, icon = Glyph.Gamepad,
                help = "With Shizuku running, picking a game that is still open brings it back instead of restarting it.",
                keywords = "shizuku continue suspend",
            ) { askShizuku() },
            SettingRow(
                "About Tandem", "GPL-3.0", Palette.Lavender, icon = Glyph.Heart,
                help = "Free and open source. No ads, no accounts, no tracking. Your data stays on your device.",
                keywords = "version license open source credits",
            ) { Toasts.show("Made with love, free forever. Hi from Pip!") },
        ),
    )
}

/** Asks Shizuku for permission (or explains what to do first). Used by Settings and the first-run welcome. */
fun askShizuku() {
    val alive = runCatching { rikka.shizuku.Shizuku.pingBinder() }.getOrDefault(false)
    when {
        Shell.ready() -> Toasts.show("Resume is on: games pick up right where you left them")
        alive -> rikka.shizuku.Shizuku.requestPermission(1)
        else -> Toasts.show("Start the Shizuku app first, then come back here")
    }
}

fun onOff(b: Boolean) = if (b) "On" else "Off"

/**
 * Slow data work from Settings (import, backup, restore): runs off the main thread with a toast when it starts and
 * when it's done, one at a time, so the screen never freezes and a second press can't start it twice.
 */
object DataJobs {
    var busy by mutableStateOf<String?>(null)
        private set

    fun <T> run(id: String, starting: String, work: () -> T, done: (T) -> String) {
        if (busy != null) { Toasts.show("One moment, still working…"); return }
        busy = id
        Toasts.show(starting)
        kotlinx.coroutines.MainScope().launch {
            val result = runCatching { withContext(Dispatchers.IO) { work() } }
            busy = null
            Toasts.show(result.fold(done) { "That didn't work: ${it.message ?: "unknown problem"}" })
        }
    }
}

/** One line of the settings list: a group heading, a row, or a group's "More options" switch. */
private sealed interface SettingsLine {
    val key: String
    class Heading(val section: SettingSection) : SettingsLine { override val key = "h:${section.title}" }
    class Item(val section: SettingSection, val row: SettingRow, val extra: Boolean) : SettingsLine {
        override val key = rowKey(section.title, row.label)
    }
    class More(val section: SettingSection, val open: Boolean) : SettingsLine { override val key = moreKey(section.title) }
}

/**
 * The two-pane settings layout: grouped rows on the left, and on the right a panel that explains the highlighted
 * setting (with a live preview where one helps). [header] adds extra items above the sections (the theme gallery
 * on Look & feel) and may report focus through its callback to change the side panel.
 *
 * Each group shows its common rows; its [SettingSection.more] rows stay folded under a "More options" row until
 * it is opened (search opens it by itself when it jumps to one of them).
 */
@Composable
fun SettingsLayout(
    routeKey: String,
    sections: List<SettingSection>,
    sidePanelFallback: @Composable () -> Unit = { AboutPanel() },
    /**
     * Items above the sections (galleries). [header] must add exactly one list item per entry of [headerKeys], each
     * entry listing the focus keys inside that item; it gets the focus target and a callback for when one of them
     * is focused.
     */
    header: (LazyListScope.(target: String?, onFocusKey: (String) -> Unit) -> Unit)? = null,
    headerKeys: List<List<String>> = emptyList(),
    headerRow: (String) -> SettingRow? = { null },
) {
    var focusedKey by remember { mutableStateOf(FocusMemory.get(routeKey)) }
    val lines = buildList {
        for (s in sections) {
            if (s.rows.isEmpty() && s.more.isEmpty()) continue
            add(SettingsLine.Heading(s))
            s.rows.forEach { add(SettingsLine.Item(s, it, extra = false)) }
            if (s.more.isNotEmpty()) {
                val open = SettingsUi.isOpen(routeKey, s.title) || s.rows.isEmpty()
                if (open) s.more.forEach { add(SettingsLine.Item(s, it, extra = true)) }
                if (s.rows.isNotEmpty()) add(SettingsLine.More(s, open))
            }
        }
    }
    val focusedLine = lines.firstOrNull { it.key == focusedKey }
    val focusedRow = focusedKey?.let(headerRow) ?: when (focusedLine) {
        is SettingsLine.Item -> focusedLine.row
        is SettingsLine.More -> moreRow(focusedLine.section, focusedLine.open) {}
        else -> null
    }
    // Rows come and go (signing in adds some, a group folds away); the remembered item, or its neighbour, gets
    // focus and is scrolled to. Galleries and rows share one target, so focus can never end up nowhere.
    val lineKeys = lines.filter { it !is SettingsLine.Heading }.map { it.key }
    val target = rememberFocusTarget(routeKey, headerKeys.flatten() + lineKeys)
    val state = rememberRouteListState(routeKey)
    run {
        var index = headerKeys.indexOfFirst { target != null && target in it }
        if (index < 0) lines.indexOfFirst { it.key == target }.takeIf { it >= 0 }?.let { index = headerKeys.size + it }
        KeepInView(state, index)
    }
    Row(Modifier.fillMaxSize().padding(start = 14.dp, end = 22.dp)) {
        LazyColumn(
            // Rows scrolled under the top bar or off the bottom fade out instead of being cut in half.
            Modifier.weight(1.3f).fillMaxHeight().fadingEdges(state),
            state = state,
            // Room on every side for the focused row to grow and glow (the fade mask clips to the list).
            contentPadding = PaddingValues(start = FocusRoomSide, end = FocusRoomSide, top = FocusRoom, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // A gallery above the rows is explained on the details screen like a row (or, without a row of its own, by
            // the page's summary: see SettingsIdle).
            header?.invoke(this, target) { key ->
                FocusMemory.remember(routeKey, key); focusedKey = key
                Selection.current = headerRow(key)?.let { Selected.Setting(it.label, it.shownValue, it.help, it.color, it.aLabel) } ?: Selected.Home
            }
            // contentType lets the list reuse a scrolled-away row's layout for the next row of the same kind.
            items(lines, key = { it.key }, contentType = { it.javaClass }) { line ->
                when (line) {
                    is SettingsLine.Heading -> SettingsHeader(line.section.title, line.section.note)
                    is SettingsLine.Item -> {
                        val row = line.row
                        SettingRowCard(row, Modifier.takesFocus(routeKey, line.key, line.key == target), flashKey = line.key) {
                            FocusMemory.remember(routeKey, line.key)
                            focusedKey = line.key
                            // The bottom screen explains the setting too.
                            Selection.current = Selected.Setting(row.label, row.shownValue, row.help, row.color, row.aLabel)
                        }
                    }
                    is SettingsLine.More -> {
                        val row = moreRow(line.section, line.open) {
                            SettingsUi.setOpen(routeKey, line.section.title, !line.open)
                            // Opening moves the highlight to the first row that appeared; folding keeps it here.
                            if (!line.open) line.section.more.firstOrNull()?.let {
                                FocusMemory.remember(routeKey, rowKey(line.section.title, it.label))
                                Nav.focusTick++
                            }
                        }
                        SettingRowCard(row, Modifier.takesFocus(routeKey, line.key, line.key == target), subtle = true) {
                            FocusMemory.remember(routeKey, line.key)
                            focusedKey = line.key
                            Selection.current = Selected.Setting(row.label, row.value, row.help, row.color, row.aLabel)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(0.8f).fillMaxHeight().padding(top = FocusRoom, bottom = 22.dp)) {
            SidePanel(focusedRow, sidePanelFallback)
        }
    }
}

/** The row that opens or folds a group's extra options. */
private fun moreRow(section: SettingSection, open: Boolean, onClick: () -> Unit) = SettingRow(
    if (open) "Fewer options" else "More options",
    if (open) "Hide ${section.more.size}" else "${section.more.size} more",
    Palette.Muted, null,
    "The less common ${section.title.lowercase()} settings: " + section.more.joinToString(", ") { it.label } + ".",
    opens = !open, action = if (open) "Hide" else "Show",
    onClick = onClick,
)

/** A section heading in a settings list: small, spaced capitals with a hairline, and an optional aside. */
@Composable
fun SettingsHeader(title: String, note: String? = null) {
    Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 2.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title.uppercase(), style = MaterialTheme.typography.labelLarge, color = Palette.Muted, letterSpacing = androidx.compose.ui.unit.TextUnit(1.4f, androidx.compose.ui.unit.TextUnitType.Sp), maxLines = 1)
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f).height(1.dp).background(Palette.Muted.copy(alpha = 0.22f)))
        if (note != null) {
            Spacer(Modifier.width(10.dp))
            Text(note, style = MaterialTheme.typography.labelMedium, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 200.dp))
        }
    }
}

/**
 * One settings row as a card: icon chip, label, and on the right what kind of row it is: a switch, ‹ value › for
 * choices, a gauge for numbers, a chevron for links. [flashKey] pulses the row once when search jumps to it;
 * [subtle] draws the quieter "More options" rows.
 */
@Composable
fun SettingRowCard(row: SettingRow, modifier: Modifier = Modifier, flashKey: String? = null, subtle: Boolean = false, onFocus: () -> Unit) {
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
            val handler = when (e.key) { Key.DirectionLeft -> row.onLeft; Key.DirectionRight -> row.onRight; else -> null }
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
                .padding(horizontal = 12.dp, vertical = if (subtle) 6.dp else 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (row.icon != null) {
                GlyphChip(row.icon, row.color)
                Spacer(Modifier.width(12.dp))
            } else if (subtle) {
                Spacer(Modifier.width(42.dp))
            }
            Text(
                row.label, Modifier.weight(1f),
                style = if (subtle) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                color = if (subtle) Palette.Muted else Palette.Text,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(10.dp))
            when (row.kind) {
                RowKind.Toggle -> Toggle(row.toggle == true, row.color)
                else -> Row(Modifier.widthIn(max = 220.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (row.slider != null) {
                        Gauge(row.slider, row.color)
                        Spacer(Modifier.width(8.dp))
                    }
                    if (stepper) Text("‹", style = MaterialTheme.typography.titleLarge, color = if (focused) row.color else Palette.Muted.copy(alpha = 0.35f))
                    if (stepper) Spacer(Modifier.width(6.dp))
                    if (row.value.isNotEmpty()) ValuePill(row.value, if (subtle) Palette.Muted else row.color, Modifier.weight(1f, fill = false))
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

/** A value tag that ellipsizes instead of pushing the label away. */
@Composable
fun ValuePill(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.18f)).padding(horizontal = 10.dp, vertical = 3.dp)) {
        Text(text, color = color, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A small level gauge for number rows. */
@Composable
private fun Gauge(fraction: Float, color: Color) {
    val f = fraction.coerceIn(0f, 1f)
    Box(Modifier.width(44.dp).height(6.dp).clip(CircleShape).background(Palette.Muted.copy(alpha = 0.25f))) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(f.coerceAtLeast(0.06f)).clip(CircleShape).background(color))
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
                // Help is cut short so the preview keeps its room; the preview shrinks to fit what's left.
                if (r.help != null) Text(r.help, style = MaterialTheme.typography.bodyMedium, color = Palette.Muted, maxLines = 3, overflow = TextOverflow.Ellipsis)
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

/** The button hints under the side panel: what A does for this kind of row, ◀▶ when it steps, search and back. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsHints(r: SettingRow?) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (r != null) ButtonHint("A", r.aLabel, r.color)
        if (r != null && (r.onLeft != null || r.onRight != null)) ButtonHint("◀▶", "Change", r.color)
        ButtonHint("X", "Search", Palette.Sky)
        ButtonHint("B", "Back", Palette.Muted)
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
        Text("Tandem", style = MaterialTheme.typography.titleLarge)
        Text("Open source · no tracking", style = MaterialTheme.typography.bodyMedium, color = Palette.Muted, textAlign = TextAlign.Center)
    }
}
