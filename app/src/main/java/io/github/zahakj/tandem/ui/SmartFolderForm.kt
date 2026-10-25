package io.github.zahakj.tandem.ui

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
import io.github.zahakj.tandem.plural
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/*
 * The smart folder editor: Start › New › Smart folder…, and Y › Edit rules… or Make this a smart folder on a folder.
 * The rule is a column of settings rows (‹value› choosers, pickers for systems and genres, the keyboard for the name
 * and the title pattern). On every change the matching games are worked out in the background and shown on the other
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
    /** The games the rule matches now, for the previews; null until first worked out. */
    var matches by mutableStateOf<List<Game>?>(null)
        internal set

    fun openNew() {
        start(IconGridStore.newFolderId(), "Smart folder", SmartRule(), null)
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
        folderId = null; page = null; matches = null
        Nav.focusTick++
    }

    fun save() {
        val id = folderId ?: return
        if (rule.patternError) { Toasts.show("The title pattern isn't a valid regular expression"); return }
        val n = name.trim().ifEmpty { "Smart folder" }
        val count = matches?.let { " · ${plural(it.size, "game")}" }.orEmpty()
        when {
            isNew -> {
                IconGridStore.createSmartFolder(id, n, rule, openOn, homeShown(), IconGridStore.config.folders.size)
                Toasts.show("Made the smart folder $n$count")
            }
            converting -> {
                IconGridStore.setRule(id, n, rule, openOn, homeShown())
                Toasts.show("$n is a smart folder now. What was inside is back on the home grid")
            }
            else -> {
                IconGridStore.setRule(id, n, rule, openOn, homeShown())
                Toasts.show("Saved $n$count")
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
}

@Composable
private fun FormHeader() {
    val f = SmartFolderForm
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            val title = when {
                f.page == SmartFolderForm.PICK_SYSTEMS -> "Systems"
                f.page == SmartFolderForm.PICK_GENRES -> "Genres"
                f.isNew -> "New smart folder"
                f.converting -> "Make a smart folder"
                else -> "Edit rules"
            }
            Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = if (f.converting) "${f.name} · What's inside goes back on the home grid" else f.name
            Text(sub, style = MaterialTheme.typography.bodySmall, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            ButtonHint("A", if (f.page == null) "Change" else "Pick", Palette.Mint)
            ButtonHint("Start", "Save", Palette.Butter)
            ButtonHint("B", if (f.page == null) "Close" else "Back", Palette.Lavender)
        }
    }
}

/** [list]'s item [by] steps from [now] (round), or the first when [now] isn't in it. */
private fun <T> step(list: List<T>, now: T, by: Int): T {
    val i = list.indexOf(now)
    return if (i < 0) list.first() else list[(i + by + list.size) % list.size]
}

/** A ‹value› row stepping through [values]; A steps forward like Right. */
private fun <T> chooser(label: String, values: List<T>, now: T, color: androidx.compose.ui.graphics.Color, icon: Glyph, help: String, name: (T) -> String, set: (T) -> Unit) =
    SettingRow(
        label, name(now), color, icon, help = help,
        onLeft = { set(step(values, now, -1)) }, onRight = { set(step(values, now, 1)) },
    ) { set(step(values, now, 1)) }

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
        "name" to SettingRow("Name", f.name, Palette.Butter, Glyph.Text, help = "What the folder is called on the grid.", action = "Type") {
            TextEntry.ask("Name the folder", f.name) { n -> n.trim().takeIf { it.isNotEmpty() }?.let { f.name = it } }
        },
        "systems" to SettingRow(
            "Systems", when (r.systems.size) { 0 -> "Any"; 1 -> SmartFolders.systemName(r.systems[0]); else -> plural(r.systems.size, "system") },
            Palette.Sky, Glyph.Cartridge, help = "Only games from these systems. None picked: every system.", opens = true,
        ) { pick(SmartFolderForm.PICK_SYSTEMS) },
        "genres" to SettingRow(
            "Genres", when (r.genres.size) { 0 -> "Any"; 1 -> r.genres[0]; else -> plural(r.genres.size, "genre") },
            Palette.Lavender, Glyph.Star, help = "Games with any of these genres (from scraped details, or the subfolder they sit in).", opens = true,
        ) { pick(SmartFolderForm.PICK_GENRES) },
        "pattern" to SettingRow(
            "Title has", r.pattern.ifEmpty { "Anything" }, Palette.Mint, Glyph.Search,
            help = "Only titles containing this text, in any case. As a regex it is searched anywhere in the title.", action = "Type",
        ) {
            TextEntry.ask("Title has", r.pattern, onChange = { t -> set { it.copy(pattern = t) } }) { t -> set { it.copy(pattern = t) } }
        },
        "regex" to chooser(
            "Match as", listOf(false, true), r.regex, Palette.Mint, Glyph.Text,
            "Plain text matches the words as typed. Regex reads the pattern as a regular expression, like ^Super|Kart\$.",
            { if (!it) "Plain text" else if (r.patternError) "Regex · not valid" else "Regex" },
        ) { v -> set { it.copy(regex = v) } },
        "from" to chooser("From year", years, r.yearFrom, Palette.Peach, Glyph.Clock, "Released this year or later. Games without a year are left out once a year is set.", { it?.toString() ?: "Any" }) { v ->
            set { it.copy(yearFrom = v, yearTo = it.yearTo?.takeIf { to -> v == null || to >= v }) }
        },
        "to" to chooser("To year", years, r.yearTo, Palette.Peach, Glyph.Clock, "Released this year or earlier.", { it?.toString() ?: "Any" }) { v ->
            set { it.copy(yearTo = v, yearFrom = it.yearFrom?.takeIf { from -> v == null || from <= v }) }
        },
        "fav" to SettingRow("Favorites only", "", Palette.Peach, Glyph.Heart, help = "Only games you've hearted.", toggle = r.favorites) {
            set { it.copy(favorites = !it.favorites) }
        },
        "played" to chooser("Played", SmartRule.PLAYS, r.played, Palette.Sky, Glyph.Play, "Played: games with any play history. Unplayed: games never started.", SmartRule::playedLabel) { v ->
            set { it.copy(played = v) }
        },
        "random" to chooser(
            "How many", SmartRule.RANDOMS, r.random, Palette.Butter, Glyph.Sparkle,
            "Random keeps that many of the matches, picked afresh each time Tandem starts.", { if (it == 0) "All" else "Random $it" },
        ) { v -> set { it.copy(random = v) } },
        "emulator" to chooser(
            "Emulator", emulators, r.emulator, Palette.Lavender, Glyph.Gamepad, "Only games that start with this emulator.",
            { p -> p?.let { emulatorNames[it] ?: SmartFolders.emulatorName(it) } ?: "Any" },
        ) { v -> set { it.copy(emulator = v) } },
        "sort" to chooser("Sort", SmartRule.SORTS, r.sort, Palette.Sky, Glyph.Sort, "The order the games show in.", SmartRule::sortLabel) { v ->
            set { it.copy(sort = v) }
        },
        "screen" to chooser(
            "Open games on", FolderScreens.ALL, f.openOn, Palette.Sky, Glyph.Screens,
            "The screen games opened from this folder start on. Default: each game's own choice. A game's own screen setting still wins.",
            FolderScreens::label,
        ) { v -> f.openOn = v },
        "save" to SettingRow(
            if (f.isNew) "Make the folder" else "Save", f.matches?.let { plural(it.size, "game") } ?: "", Palette.Mint, Glyph.Save,
            help = "Start saves too.", action = "Save",
        ) { SmartFolderForm.save() },
    )
    FormList(SmartFolderForm.ROUTE, rows, onHelp)
}

/** Systems or genres to pick, each with a switch, under one that clears them all. */
@Composable
private fun PickerPage(page: String, choices: FormChoices) {
    val f = SmartFolderForm
    val systems = page == SmartFolderForm.PICK_SYSTEMS
    val picked = if (systems) f.rule.systems else f.rule.genres
    fun pickedHas(k: String) = picked.any { it.equals(k, ignoreCase = !systems) }
    fun setPicked(list: List<String>) { f.rule = if (systems) f.rule.copy(systems = list) else f.rule.copy(genres = list) }
    val options: List<Pair<String, String>> = if (systems) choices.systems.map { it.first to it.second } else choices.genres.map { it to it }
    // Picked ones no longer in the library still show, so they can be taken off.
    val all = options + picked.filter { p -> options.none { it.first.equals(p, ignoreCase = !systems) } }.map { it to it }
    val counts = remember(choices) { choices.systems.associate { it.first to it.third } }
    val rows = listOf(
        "any" to SettingRow(if (systems) "Any system" else "Any genre", "", Palette.Mint, Glyph.Sparkle, help = "Clears the picks.", toggle = picked.isEmpty()) { setPicked(emptyList()) },
    ) + all.map { (k, label) ->
        "o:$k" to SettingRow(label, "", if (systems) Palette.Sky else Palette.Lavender, help = counts[k]?.let { plural(it, "game") }, toggle = pickedHas(k)) {
            setPicked(if (pickedHas(k)) picked.filterNot { it.equals(k, ignoreCase = !systems) } else picked + k)
        }
    }
    if (all.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                if (systems) "No systems with games yet" else "No genres yet. Scrape game details and games sort themselves into genres.",
                Modifier.padding(24.dp), style = MaterialTheme.typography.bodyMedium, color = Palette.Muted, textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis,
            )
        }
    }
    FormList("${SmartFolderForm.ROUTE}:$page", if (all.isEmpty()) rows.take(1) else rows) {}
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
            if (rule.patternError) "No games" else games?.let { plural(it.size, "game") } ?: "Counting…",
            style = MaterialTheme.typography.headlineSmall, color = Palette.Mint, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Text(
            if (rule.patternError) "The title pattern isn't a valid regular expression" else SmartFolders.describe(rule),
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
        Text(f.name.ifBlank { "Smart folder" }, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            when {
                rule.patternError -> "Not a valid pattern"
                games == null -> "Looking…"
                else -> "${plural(games.size, "game")} match"
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
