package io.github.zahakj.tandem.ui

import io.github.zahakj.tandem.Tx
import io.github.zahakj.tandem.R
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.zahakj.tandem.Button
import io.github.zahakj.tandem.Collections
import io.github.zahakj.tandem.Game
import io.github.zahakj.tandem.LaunchSpec
import io.github.zahakj.tandem.Libraries
import io.github.zahakj.tandem.Privacy
import io.github.zahakj.tandem.data.FolderScreens
import io.github.zahakj.tandem.data.HomeFolder
import io.github.zahakj.tandem.data.IconGridStore
import io.github.zahakj.tandem.data.SmartRule
import io.github.zahakj.tandem.data.Store

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/*
 * The smart folder editor: Start › New › Smart folder…, and Y › Edit rules… or Make this a smart folder on a folder.
 * The rule is a column of settings rows (choosers whose options A shows in a grid, a grid of systems and one of
 * genres, the keyboard for the name and the title pattern). On every change the matching games are worked out in the background and shown on the other
 * screen (SmartPreview, in place of the details). Start or the last row saves; B leaves without saving.
 */

object SmartFolderForm {
    const val ROUTE = "smartform"
    const val PICK_SYSTEMS = "systems"
    const val PICK_GENRES = "genres"

    /** The folder being edited (a fresh id for a new one); null while closed. */
    var folderId by mutableStateOf<String?>(null)
        private set
    /** A new folder, not on the grid until saved. */
    var isNew by mutableStateOf(false)
        private set
    /** A manual folder becoming smart: what it holds goes back on the home grid when saved. */
    var converting by mutableStateOf(false)
        private set
    var name by mutableStateOf("")
    var rule by mutableStateOf(SmartRule())
    var openOn by mutableStateOf<String?>(null)
    /** The picker over the rows: [PICK_SYSTEMS], [PICK_GENRES], or null. */
    var page by mutableStateOf<String?>(null)
    /** The choice whose options show in a grid over the rows (A on a chooser row), or null. */
    internal var choosing by mutableStateOf<FormChoice?>(null)
    /** The games the rule matches now, for the previews; null until first worked out. */
    var matches by mutableStateOf<List<Game>?>(null)
        internal set

    fun openNew() {
        start(IconGridStore.newFolderId(), Tx.s(R.string.grid_type_smart_folder), SmartRule(), null)
        isNew = true
    }

    /** Edits folder [id]'s rules; a manual folder starts from the systems inside it. */
    fun edit(id: String) {
        val f = IconGridStore.folder(id) ?: return
        start(id, f.name, f.rule ?: guess(f), f.openOn)
        converting = f.rule == null && f.items.any { it.isNotEmpty() }
    }

    private fun start(id: String, name: String, rule: SmartRule, openOn: String?) {
        this.name = name; this.rule = rule; this.openOn = openOn
        page = null; matches = null; isNew = false; converting = false
        FocusMemory.forget(ROUTE)
        folderId = id
    }

    /** A first rule for a manual folder: the systems of the systems and games inside it. */
    private fun guess(f: HomeFolder): SmartRule = SmartRule(
        systems = f.items.mapNotNull { k ->
            when {
                k.startsWith("s:") -> k.removePrefix("s:")
                k.startsWith("game:") -> k.removePrefix("game:").substringBefore('/')
                else -> null
            }
        }.distinct(),
    )

    fun close() {
        folderId = null; page = null; matches = null; choosing = null
        Nav.focusTick++
    }

    fun save() {
        val id = folderId ?: return
        if (rule.patternError) { Toasts.show(Tx.s(R.string.grid_form_bad_pattern)); return }
        val n = name.trim().ifEmpty { Tx.s(R.string.grid_type_smart_folder) }
        val count = matches?.size
        when {
            isNew -> {
                IconGridStore.createSmartFolder(id, n, rule, openOn, homeShown(), IconGridStore.config.folders.size)
                Toasts.show(if (count == null) Tx.s(R.string.grid_form_made, n) else Tx.p(R.plurals.grid_form_made_count, count, count, n))
            }
            converting -> {
                IconGridStore.setRule(id, n, rule, openOn, homeShown())
                Toasts.show(Tx.s(R.string.grid_form_converted, n))
            }
            else -> {
                IconGridStore.setRule(id, n, rule, openOn, homeShown())
                Toasts.show(if (count == null) Tx.s(R.string.grid_form_saved, n) else Tx.p(R.plurals.grid_form_saved_count, count, count, n))
            }
        }
        close()
    }
}

/** What the rows offer, read from the library in the background. */
private class FormChoices(
    /** Platform id, name and game count. */
    val systems: List<Triple<String, String, Int>>,
    val genres: List<String>,
    val years: List<Int>,
    /** Package and app name. */
    val emulators: List<Pair<String, String>>,
) {
    companion object {
        val EMPTY = FormChoices(emptyList(), emptyList(), emptyList(), emptyList())

        fun read(): FormChoices {
            val libs = Libraries.all.orEmpty()
            val games = Collections.visibleGames()
            val counts = games.groupingBy { it.platform.id }.eachCount()
            val systems = libs.mapNotNull { lib -> counts[lib.platform.id]?.let { Triple(lib.platform.id, lib.platform.name, it) } }.sortedBy { it.second.lowercase() }
            val years = games.mapNotNullTo(sortedSetOf()) { it.meta.year }.toList()
            val emulators = libs.filter { !it.platform.isApps && it.platform.id in counts }.flatMap { it.platform.players }
                .filter { it.component != LaunchSpec.ANY_APP && it.packageName.isNotBlank() }
                .distinctBy { it.packageName }.map { it.packageName to SmartFolders.appName(it.name) }.sortedBy { it.second.lowercase() }
            return FormChoices(systems, Collections.genreNames(), years, emulators)
        }
    }
}

/** The editor, composed in the app root above the icon grid's menu. */
@Composable
fun SmartFolderFormHost() {
    val id = SmartFolderForm.folderId
    val shown = rememberLatest(id)
    val visible = id != null
    PanelGuard(visible)
    InterceptButtons(visible) { b -> if (b == Button.Start) SmartFolderForm.save(); true }
    if (id != null) {
        // The matches follow every change, a moment after it (typing a pattern doesn't recount on every key).
        val rule = SmartFolderForm.rule
        val libs = Libraries.all
        val meta = Store.meta
        val view = Privacy.view
        LaunchedEffect(id, rule, libs, meta, view) {
            if (SmartFolderForm.matches != null) delay(120)
            SmartFolderForm.matches = withContext(Dispatchers.Default) { Collections.smart(id, rule) }
        }
    }
    OverlayPanel(visible, onClose = { if (SmartFolderForm.page != null) SmartFolderForm.page = null else SmartFolderForm.close() }) {
        if (shown == null) return@OverlayPanel
        val libs = Libraries.all
        val meta = Store.meta
        val view = Privacy.view
        val choices by produceState(FormChoices.EMPTY, libs, meta, view) { value = withContext(Dispatchers.Default) { FormChoices.read() } }
        var help by remember { mutableStateOf<String?>(null) }
        Column(Modifier.fillMaxSize().background(Palette.Card, RoundedCornerShape(26.dp)).padding(top = 14.dp)) {
            FormHeader()
            Row(Modifier.fillMaxWidth().weight(1f).padding(start = 6.dp, end = 18.dp, bottom = 12.dp)) {
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    val page = SmartFolderForm.page
                    if (page == null) RuleRows(choices) { help = it } else PickerPage(page, choices)
                }
                Spacer(Modifier.width(12.dp))
                FormSide(help, Modifier.width(220.dp).fillMaxHeight())
            }
        }
    }
    // A chooser row's options; picking one goes back to the row.
    val choosing = SmartFolderForm.choosing
    val shownChoice = rememberLatest(choosing)
    if (shownChoice != null) {
        ChoicePopup(
            choosing != null, shownChoice.title, shownChoice.help, shownChoice.options, shownChoice.selected, shownChoice.color,
            "${SmartFolderForm.ROUTE}:choose", onClose = { SmartFolderForm.choosing = null; Nav.focusTick++ },
        ) { i -> shownChoice.pick(i) }
    }
}

@Composable
private fun FormHeader() {
    val f = SmartFolderForm
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            val title = when {
                f.page == SmartFolderForm.PICK_SYSTEMS -> Tx.s(R.string.grid_form_systems)
                f.page == SmartFolderForm.PICK_GENRES -> Tx.s(R.string.grid_form_genres)
                f.isNew -> Tx.s(R.string.grid_form_title_new)
                f.converting -> Tx.s(R.string.grid_form_title_convert)
                else -> Tx.s(R.string.grid_form_title_edit)
            }
            Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = if (f.converting) Tx.s(R.string.grid_form_convert_sub, f.name) else f.name
            Text(sub, style = MaterialTheme.typography.bodySmall, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            ButtonHint("A", Tx.s(if (f.page == null) R.string.grid_form_hint_change else R.string.grid_form_hint_pick), Palette.Mint)
            ButtonHint("Start", Tx.s(R.string.grid_form_hint_save), Palette.Butter)
            ButtonHint("B", Tx.s(if (f.page == null) R.string.common_close else R.string.common_back), Palette.Lavender)
        }
    }
}

/** [list]'s item [by] steps from [now] (round), or the first when [now] isn't in it. */
private fun <T> step(list: List<T>, now: T, by: Int): T {
    val i = list.indexOf(now)
    return if (i < 0) list.first() else list[(i + by + list.size) % list.size]
}

/** A choice's options as a grid over the rows: [options] by index, [selected] the one in use. */
internal class FormChoice(val title: String, val help: String, val options: List<PickOption<Int>>, val selected: Int, val color: androidx.compose.ui.graphics.Color, val pick: (Int) -> Unit)

/**
 * A row for a choice of [values], drawn like a settings choice (Choices.kt): a few short ones as a segmented track
 * where A steps round, more as the value where A shows them all in a grid ([FormChoice]). Left and Right step
 * through them and stop at the ends.
 */
private fun <T> chooser(label: String, values: List<T>, now: T, color: androidx.compose.ui.graphics.Color, icon: Glyph, help: String, name: (T) -> String, set: (T) -> Unit): SettingRow {
    val options = values.mapIndexed { i, v -> "$i" to name(v) }
    val at = values.indexOf(now)
    fun move(by: Int) { if (at < 0) set(values.first()) else values.getOrNull(at + by)?.let(set) }
    val inGrid = !fitsOnRow(options)
    return SettingRow(
        label, name(now), color, icon, help = help,
        onLeft = { move(-1) }, onRight = { move(1) }, action = if (inGrid) Tx.s(R.string.common_choose) else null,
        options = options, selected = "$at", pick = { id -> set(values[id.toInt()]) },
    ) {
        if (inGrid) SmartFolderForm.choosing = FormChoice(label, help, values.mapIndexed { i, v -> PickOption(i, name(v)) }, at, color) { i -> set(values[i]) }
        else set(step(values, now, 1))
    }
}

@Composable
private fun RuleRows(choices: FormChoices, onHelp: (String?) -> Unit) {
    val f = SmartFolderForm
    val r = f.rule
    fun set(change: (SmartRule) -> SmartRule) { f.rule = change(f.rule) }
    fun pick(page: String) { FocusMemory.forget("${SmartFolderForm.ROUTE}:$page"); f.page = page }
    // The years in the library, with the chosen ones kept even when no game has them any more.
    val years = remember(choices, r.yearFrom, r.yearTo) { listOf<Int?>(null) + (choices.years + listOfNotNull(r.yearFrom, r.yearTo)).distinct().sorted() }
    val emulators = remember(choices, r.emulator) { listOf<String?>(null) + (choices.emulators.map { it.first } + listOfNotNull(r.emulator)).distinct() }
    val emulatorNames = remember(choices) { choices.emulators.toMap() }
    val rows = listOf(
        // The form's rows are keyed by the first of each pair, not by their labels, so they need no en.
        "name" to SettingRow(Tx.s(R.string.grid_form_name), f.name, Palette.Butter, Glyph.Text, help = Tx.s(R.string.grid_form_name_help), action = Tx.s(R.string.grid_form_type)) {
            TextEntry.ask(Tx.s(R.string.grid_folder_name_title), f.name) { n -> n.trim().takeIf { it.isNotEmpty() }?.let { f.name = it } }
        },
        "systems" to SettingRow(
            Tx.s(R.string.grid_form_systems),
            when (r.systems.size) { 0 -> Tx.s(R.string.grid_form_any); 1 -> SmartFolders.systemName(r.systems[0]); else -> Tx.p(R.plurals.grid_systems, r.systems.size) },
            Palette.Sky, Glyph.Cartridge, help = Tx.s(R.string.grid_form_systems_help), opens = true,
        ) { pick(SmartFolderForm.PICK_SYSTEMS) },
        "genres" to SettingRow(
            Tx.s(R.string.grid_form_genres), when (r.genres.size) { 0 -> Tx.s(R.string.grid_form_any); 1 -> r.genres[0]; else -> Tx.p(R.plurals.grid_genres, r.genres.size) },
            Palette.Lavender, Glyph.Star, help = Tx.s(R.string.grid_form_genres_help), opens = true,
        ) { pick(SmartFolderForm.PICK_GENRES) },
        "pattern" to SettingRow(
            Tx.s(R.string.grid_form_pattern), r.pattern.ifEmpty { Tx.s(R.string.grid_form_pattern_anything) }, Palette.Mint, Glyph.Search,
            help = Tx.s(R.string.grid_form_pattern_help), action = Tx.s(R.string.grid_form_type),
        ) {
            TextEntry.ask(Tx.s(R.string.grid_form_pattern), r.pattern, onChange = { t -> set { it.copy(pattern = t) } }) { t -> set { it.copy(pattern = t) } }
        },
        "regex" to chooser(
            Tx.s(R.string.grid_form_match), listOf(false, true), r.regex, Palette.Mint, Glyph.Text,
            Tx.s(R.string.grid_form_match_help),
            { Tx.s(if (!it) R.string.grid_form_match_plain else if (r.patternError) R.string.grid_form_match_regex_bad else R.string.grid_form_match_regex) },
        ) { v -> set { it.copy(regex = v) } },
        "from" to chooser(Tx.s(R.string.grid_form_year_from), years, r.yearFrom, Palette.Peach, Glyph.Clock, Tx.s(R.string.grid_form_year_from_help), { it?.toString() ?: Tx.s(R.string.grid_form_any) }) { v ->
            set { it.copy(yearFrom = v, yearTo = it.yearTo?.takeIf { to -> v == null || to >= v }) }
        },
        "to" to chooser(Tx.s(R.string.grid_form_year_to), years, r.yearTo, Palette.Peach, Glyph.Clock, Tx.s(R.string.grid_form_year_to_help), { it?.toString() ?: Tx.s(R.string.grid_form_any) }) { v ->
            set { it.copy(yearTo = v, yearFrom = it.yearFrom?.takeIf { from -> v == null || from <= v }) }
        },
        "fav" to SettingRow(Tx.s(R.string.grid_form_favorites), "", Palette.Peach, Glyph.Heart, help = Tx.s(R.string.grid_form_favorites_help), toggle = r.favorites) {
            set { it.copy(favorites = !it.favorites) }
        },
        "played" to chooser(Tx.s(R.string.grid_form_played), SmartRule.PLAYS, r.played, Palette.Sky, Glyph.Play, Tx.s(R.string.grid_form_played_help), SmartRule::playedLabel) { v ->
            set { it.copy(played = v) }
        },
        "status" to chooser(
            Tx.s(R.string.grid_form_status), StatusChoices, r.statuses, Palette.Mint, Glyph.Trophy,
            Tx.s(R.string.grid_form_status_help),
            ::statusChoiceLabel,
        ) { v -> set { it.copy(statuses = v) } },
        "random" to chooser(
            Tx.s(R.string.grid_form_random), SmartRule.RANDOMS, r.random, Palette.Butter, Glyph.Sparkle,
            Tx.s(R.string.grid_form_random_help), { if (it == 0) Tx.s(R.string.grid_form_random_all) else Tx.s(R.string.grid_smart_random, it) },
        ) { v -> set { it.copy(random = v) } },
        "emulator" to chooser(
            Tx.s(R.string.grid_form_emulator), emulators, r.emulator, Palette.Lavender, Glyph.Gamepad, Tx.s(R.string.grid_form_emulator_help),
            { p -> p?.let { emulatorNames[it] ?: SmartFolders.emulatorName(it) } ?: Tx.s(R.string.grid_form_any) },
        ) { v -> set { it.copy(emulator = v) } },
        "sort" to chooser(Tx.s(R.string.grid_form_sort), SmartRule.SORTS, r.sort, Palette.Sky, Glyph.Sort, Tx.s(R.string.grid_form_sort_help), SmartRule::sortLabel) { v ->
            set { it.copy(sort = v) }
        },
        "screen" to chooser(
            Tx.s(R.string.grid_smart_open_on), FolderScreens.ALL, f.openOn, Palette.Sky, Glyph.Screens,
            Tx.s(R.string.grid_form_screen_help),
            FolderScreens::label,
        ) { v -> f.openOn = v },
        "save" to SettingRow(
            Tx.s(if (f.isNew) R.string.grid_form_make else R.string.grid_form_save), f.matches?.let { Tx.p(R.plurals.grid_games, it.size) } ?: "", Palette.Mint, Glyph.Save,
            help = Tx.s(R.string.grid_form_save_help), action = Tx.s(R.string.grid_form_hint_save),
        ) { SmartFolderForm.save() },
    )
    FormList(SmartFolderForm.ROUTE, rows, onHelp)
}

/**
 * Systems or genres to pick, as a grid: each system a tile with its icon, name and games, each genre a chip, ticked
 * while picked; the first clears them all.
 */
@Composable
private fun PickerPage(page: String, choices: FormChoices) {
    val f = SmartFolderForm
    val systems = page == SmartFolderForm.PICK_SYSTEMS
    val picked = if (systems) f.rule.systems else f.rule.genres
    fun pickedHas(k: String) = picked.any { it.equals(k, ignoreCase = !systems) }
    fun setPicked(list: List<String>) { f.rule = if (systems) f.rule.copy(systems = list) else f.rule.copy(genres = list) }
    fun flip(k: String) = setPicked(if (pickedHas(k)) picked.filterNot { it.equals(k, ignoreCase = !systems) } else picked + k)
    val options: List<Pair<String, String>> = if (systems) choices.systems.map { it.first to it.second } else choices.genres.map { it to it }
    // Picked ones no longer in the library still show, so they can be taken off.
    val all = options + picked.filter { p -> options.none { it.first.equals(p, ignoreCase = !systems) } }.map { it to it }
    val counts = remember(choices) { choices.systems.associate { it.first to it.third } }
    val route = "${SmartFolderForm.ROUTE}:$page"
    val keys = listOf("any") + all.map { "o:${it.first}" }
    val target = rememberFocusTarget(route, keys)
    val color = if (systems) Palette.Sky else Palette.Lavender
    val grid = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    KeepInView(grid, keys.indexOf(target))
    androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
        androidx.compose.foundation.lazy.grid.GridCells.Adaptive(if (systems) 150.dp else 170.dp),
        Modifier.fillMaxSize().fadingEdges(grid), state = grid,
        contentPadding = PaddingValues(horizontal = FocusRoomSide, vertical = FocusRoom),
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item("any") {
            val any = picked.isEmpty()
            if (systems) {
                PickTile(Tx.s(R.string.grid_form_any_system), Tx.s(R.string.grid_form_any_clears), any, Palette.Mint, Modifier.takesFocus(route, "any", target == "any"), art = {
                    GlyphChip(Glyph.Sparkle, if (any) Palette.Mint else Palette.Muted, size = 44.dp, round = true)
                }) { setPicked(emptyList()) }
            } else {
                PickTile(Tx.s(R.string.grid_form_any_genre), null, any, Palette.Mint, Modifier.takesFocus(route, "any", target == "any"), art = null) { setPicked(emptyList()) }
            }
        }
        items(all.size, key = { "o:${all[it].first}" }) { i ->
            val (k, label) = all[i]
            val on = pickedHas(k)
            val focus = Modifier.takesFocus(route, "o:$k", target == "o:$k")
            if (systems) {
                val platform = Libraries.byId(k)?.platform
                PickTile(label, counts[k]?.let { Tx.p(R.plurals.grid_games, it) } ?: Tx.s(R.string.grid_form_not_in_library), on, color, focus, art = {
                    if (platform != null) SystemIcon(platform, 48.dp) else GlyphChip(Glyph.Cartridge, Palette.Muted, size = 44.dp, round = true)
                }) { flip(k) }
            } else {
                PickTile(label, null, on, color, focus, art = null) { flip(k) }
            }
        }
        if (all.isEmpty()) item("none", span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
            Text(
                Tx.s(if (systems) R.string.grid_form_no_systems else R.string.grid_form_no_genres),
                Modifier.padding(24.dp), style = MaterialTheme.typography.bodyMedium, color = Palette.Muted, textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * A system or genre in the picker, ticked while picked. A system has its icon above its name; a genre is just its
 * name. Names take two lines and shrink a little rather than being cut, and every tile of a kind is the same height.
 */
@Composable
private fun PickTile(label: String, detail: String?, on: Boolean, color: androidx.compose.ui.graphics.Color, focus: Modifier, art: (@Composable () -> Unit)?, onClick: () -> Unit) {
    FocusCard(focus.height(if (art != null) 128.dp else 58.dp), glow = color, shape = RoundedCornerShape(16.dp), onClick = onClick) {
        Box(Modifier.matchParentSize().background(color.copy(alpha = if (on) 0.2f else 0.05f)))
        if (art != null) {
            Column(Modifier.fillMaxSize().padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.weight(1f).graphicsLayer { alpha = if (on) 1f else 0.6f }, contentAlignment = Alignment.Center) { art() }
                ShrinkText(label, MaterialTheme.typography.titleSmall, Modifier.fillMaxWidth().height(40.dp), maxLines = 2, minScale = 0.75f, textAlign = TextAlign.Center)
                if (detail != null) Text(detail, style = MaterialTheme.typography.labelSmall, color = Palette.Muted, maxLines = 1)
            }
        } else {
            Row(Modifier.fillMaxSize().padding(start = 12.dp, end = 34.dp), verticalAlignment = Alignment.CenterVertically) {
                ShrinkText(label, MaterialTheme.typography.titleSmall, Modifier.fillMaxWidth(), maxLines = 2, minScale = 0.75f)
            }
        }
        TickBadge(on, color, Modifier.align(if (art != null) Alignment.TopEnd else Alignment.CenterEnd).padding(6.dp), size = 22.dp)
    }
}

@Composable
private fun FormList(route: String, rows: List<Pair<String, SettingRow>>, onHelp: (String?) -> Unit) {
    val keys = rows.map { it.first }
    val target = rememberFocusTarget(route, keys)
    val state = rememberLazyListState()
    KeepInView(state, keys.indexOf(target))
    LazyColumn(
        Modifier.fillMaxSize().fadingEdges(state), state = state,
        contentPadding = PaddingValues(horizontal = FocusRoomSide, vertical = FocusRoom), verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(rows, key = { it.first }) { (k, row) ->
            SettingRowCard(row, Modifier.takesFocus(route, k, k == target), onFocus = { onHelp(row.help) })
        }
    }
}

/** Beside the rows: how many games match, the rule in words, the first few, and the highlighted row's help. */
@Composable
private fun FormSide(help: String?, modifier: Modifier) {
    val games = SmartFolderForm.matches
    val rule = SmartFolderForm.rule
    Column(modifier.padding(top = 12.dp)) {
        Text(
            if (rule.patternError) Tx.s(R.string.grid_form_no_games) else games?.let { Tx.p(R.plurals.grid_games, it.size) } ?: Tx.s(R.string.grid_form_counting),
            style = MaterialTheme.typography.headlineSmall, color = Palette.Mint, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Text(
            if (rule.patternError) Tx.s(R.string.grid_form_bad_pattern) else SmartFolders.describe(rule),
            style = MaterialTheme.typography.bodySmall, color = if (rule.patternError) Palette.Peach else Palette.Muted, maxLines = 3, overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(10.dp))
        // The first few, for a single screen; the other screen shows more.
        games?.take(5)?.forEach { g ->
            Text("· ${g.title}", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.weight(1f))
        if (help != null) Text(help, style = MaterialTheme.typography.bodySmall, color = Palette.Muted, maxLines = 5, overflow = TextOverflow.Ellipsis)
    }
}

/** The other screen while the editor is open: the games the rule matches now, as covers. */
@Composable
fun SmartPreview() {
    val f = SmartFolderForm
    val games = f.matches
    val rule = f.rule
    Column(Modifier.fillMaxSize().clearOfNowPlaying().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(f.name.ifBlank { Tx.s(R.string.grid_type_smart_folder) }, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            when {
                rule.patternError -> Tx.s(R.string.grid_form_bad_pattern_short)
                games == null -> Tx.s(R.string.grid_form_looking)
                else -> Tx.p(R.plurals.grid_form_match_count, games.size)
            },
            style = MaterialTheme.typography.titleMedium, color = Palette.Mint, maxLines = 1,
        )
        Text(SmartFolders.describe(rule), style = MaterialTheme.typography.bodySmall, color = Palette.Muted, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(16.dp))
        if (games.isNullOrEmpty() || rule.patternError) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { Pip(110.dp, mood = 1) }
        } else BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            val gap = 10.dp
            val cols = ((maxWidth + gap) / (92.dp + gap)).toInt().coerceIn(3, 8)
            val cell = (maxWidth - gap * (cols - 1)) / cols
            val lines = ((maxHeight + gap) / (cell + gap)).toInt().coerceAtLeast(1)
            val room = cols * lines
            // When they don't all fit, the last spot says how many more there are.
            val shown = if (games.size > room) games.take(room - 1) else games
            val more = games.size - shown.size
            Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                (shown.map<Game, Game?> { it } + if (more > 0) listOf(null) else emptyList()).chunked(cols).forEach { line ->
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        line.forEach { g ->
                            Box(
                                Modifier.size(cell).graphicsLayer { shape = RoundedCornerShape(cell * 0.16f); clip = true }.background(Palette.CardHigh),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (g != null) GameBoxArt(g, cell)
                                else Text("+$more", style = MaterialTheme.typography.titleLarge, color = Palette.Text, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The status choices for a rule, as the [SmartRule.statuses] each stands for. Finished takes completed games too. */
private val StatusChoices: List<List<String>> = listOf(emptyList<String>()) +
    io.github.zahakj.tandem.data.GameStatus.entries.filter { it != io.github.zahakj.tandem.data.GameStatus.Completed }.map { s ->
        if (s == io.github.zahakj.tandem.data.GameStatus.Finished) listOf(s.id, io.github.zahakj.tandem.data.GameStatus.Completed.id) else listOf(s.id)
    } + listOf(io.github.zahakj.tandem.data.GameStatus.Completed.id).let { listOf(it) } + listOf(listOf(SmartRule.STATUS_NONE))

private fun statusChoiceLabel(v: List<String>): String = when {
    v.isEmpty() -> Tx.s(R.string.grid_form_any)
    v == listOf(SmartRule.STATUS_NONE) -> Tx.s(R.string.grid_form_status_none)
    v.size > 1 -> Tx.s(R.string.grid_form_status_finished)
    else -> io.github.zahakj.tandem.data.GameStatus.of(v[0])?.label ?: Tx.s(R.string.grid_form_any)
}
