package io.github.zahakj.tandem.ui

import android.text.format.DateFormat
import android.text.format.DateUtils
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.zahakj.tandem.Button
import io.github.zahakj.tandem.Game
import io.github.zahakj.tandem.Libraries
import io.github.zahakj.tandem.NoteStore
import io.github.zahakj.tandem.OpenNote
import io.github.zahakj.tandem.Privacy
import io.github.zahakj.tandem.R
import io.github.zahakj.tandem.RunningGames
import io.github.zahakj.tandem.Selected
import io.github.zahakj.tandem.Selection
import io.github.zahakj.tandem.Tx
import io.github.zahakj.tandem.data.NoteDoc
import io.github.zahakj.tandem.data.NoteEntry
import io.github.zahakj.tandem.data.NoteFiles
import io.github.zahakj.tandem.data.Notebook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/*
 * Notes: notes while you play, kept per game as plain Markdown in ROMs/notes (data/Notes.kt). Three pages on the top
 * screen: every notebook (General, then one per game), a notebook's notes, and a note (NoteEditor.kt). Opened from a
 * game (its Y menu), it goes straight to that game's notebook. During play the companion has its own touch version
 * (CompanionNotes.kt).
 */

/** What the Notes app shows, kept while it is away (the Gallery picking a picture, a game played). */
object NotesUi {
    /** The notebook open; null for the list of notebooks. */
    var book by mutableStateOf<Notebook?>(null)
    /** The note open, over its notebook. */
    var note by mutableStateOf<OpenNote?>(null)
    /** What the notebook list is narrowed to (empty: every notebook). */
    var query by mutableStateOf("")

    /** From the home grid: the list of notebooks. */
    fun reset() { book = null; note = null; query = "" }

    /** Opens the Notes app on [game]'s notebook (it is made with the first note). */
    fun openFor(game: Game) {
        NoteStore.later({ NoteStore.bookFor(game) }) { b ->
            note = null
            query = ""
            book = b
            Nav.go(Route.Tool("notes"))
        }
    }

    fun openNote(file: File) { note = OpenNote(file) }

    fun closeNote() { note = null; Nav.focusTick++ }

    /** The kinds of new note: each a Markdown note that starts differently. */
    enum class Kind(@StringRes private val res: Int) {
        Note(R.string.apps_notes_kind_note), Checklist(R.string.apps_notes_kind_checklist), Glossary(R.string.apps_notes_kind_glossary), Sketch(R.string.apps_notes_kind_sketch);
        val label: String get() = Tx.s(res)
    }

    /** A new glossary's headings, in the language of the moment (they are the note's text from then on). */
    fun glossaryHeader(): List<String> = listOf(Tx.s(R.string.apps_notes_glossary_symbol), Tx.s(R.string.apps_notes_glossary_meaning))

    /** A new note's name: what it is and when ("Note 3 Nov 14.02"); rename it any time. It is a file name, kept in the language it was made in. */
    fun newName(what: String): String = "$what ${DateFormat.format("d MMM HH.mm", System.currentTimeMillis())}"

    /** What a new note of [kind] starts with: a glossary has its headings and a first, empty entry. */
    fun newText(kind: Kind): String =
        if (kind == Kind.Glossary) glossaryHeader().let { h -> NoteDoc.write(listOf(NoteDoc.Block.Table(h, listOf(h.map { "" })))) } else ""

    /** A new note of [kind] in [book], opened ready to write in. */
    fun create(book: Notebook, kind: Kind) {
        val text = newText(kind)
        NoteStore.later({ NoteStore.create(book, newName(kind.label), text) }) { file ->
            file ?: run { Toasts.show(Tx.s(R.string.apps_notes_make_failed)); return@later }
            // The notebook as it is on disk now (a game's is made with its first note).
            this.book = Notebook(file.parentFile ?: book.dir, book.gameKey)
            val open = OpenNote(file, NoteDoc.parse(text))
            note = open
            startNote(open, kind)
        }
    }
}

/** The Notes app: the page for what [NotesUi] has open, and its popups. */
@Composable
fun NotesScreen() {
    val note = NotesUi.note
    val book = NotesUi.book
    when {
        note != null -> NoteScreen(note)
        book != null -> NotebookScreen(book)
        else -> NotebooksScreen()
    }
    NotesMenuOverlay()
    RowEditorOverlay()
    SketchPadOverlay()
    NoteInputOverlay()
}

/** Every notebook Tandem may show, with its notes; a running game's notebook is listed even before its first note. */
@Composable
private fun rememberNotebooks(): List<Pair<Notebook, List<NoteEntry>>>? {
    val running = RunningGames.list.mapNotNull { it.game }
    val scan by produceState<List<Pair<Notebook, List<NoteEntry>>>?>(null, NoteStore.version, Libraries.all, Privacy.view, running) {
        value = withContext(Dispatchers.IO) {
            val found = NoteStore.scan()
            val general = found.firstOrNull { it.first.name == NoteFiles.GENERAL } ?: (Notebook(File(NoteStore.root, NoteFiles.GENERAL), null) to emptyList())
            val playing = running.filter { g -> Privacy.canShow(g) && found.none { it.first.gameKey == g.key } }.map { NoteStore.bookFor(it) to emptyList<NoteEntry>() }
            val keys = running.map { it.key }.toSet()
            val games = found.filter { it.first.gameKey != null }
                .sortedWith(compareBy<Pair<Notebook, List<NoteEntry>>> { it.first.gameKey !in keys }.thenByDescending { it.second.firstOrNull()?.modified ?: 0L })
            listOf(general) + playing + games + found.filter { it.first.gameKey == null && it.first.name != NoteFiles.GENERAL }
        }
    }
    return scan
}

// The General notebook's folder keeps its English name; only what is shown follows the language.
private fun bookTitle(b: Notebook): String =
    NoteStore.gameOf(b)?.title ?: if (b.gameKey == null && b.name == NoteFiles.GENERAL) Tx.s(R.string.apps_notes_general) else b.name

private fun bookColor(b: Notebook, fallback: Color): Color = NoteStore.gameOf(b)?.let { SystemColors.of(it.platform.id) } ?: fallback

private fun ago(time: Long): String = DateUtils.getRelativeTimeSpanString(time, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()

/** One line about a note: how far its checklist is, its first words, or what it holds. */
fun noteLine(e: NoteEntry): String {
    val s = e.summary
    return listOfNotNull(
        Tx.s(R.string.apps_notes_tasks_done, s.done, s.tasks).takeIf { s.tasks > 0 },
        Tx.p(R.plurals.apps_notes_entries, s.rows).takeIf { s.rows > 0 },
        Tx.p(R.plurals.apps_notes_pictures, s.pictures).takeIf { s.pictures > 0 && s.firstLine.isEmpty() },
        s.firstLine.takeIf { it.isNotEmpty() },
    ).joinToString(" · ").ifEmpty { Tx.s(R.string.apps_notes_empty_note) }
}

@Composable
private fun BookArt(b: Notebook, color: Color) {
    val g = NoteStore.gameOf(b)
    if (g != null) GameArt(g) else TandemIconTile(if (b.name == NoteFiles.GENERAL) TIcon.Notes else TIcon.Folder, color)
}

// --- Notebooks ------------------------------------------------------------------------------------------------------

@Composable
private fun NotebooksScreen() {
    val app = BuiltInApps.byId("notes")!!
    val route = Route.Tool("notes").key
    val books = rememberNotebooks()
    val query = NotesUi.query
    val results by produceState<List<NoteEntry>?>(null, books, query) {
        val q = normalize(query)
        value = if (q.isEmpty()) null else withContext(Dispatchers.IO) {
            books.orEmpty().flatMap { it.second }.filter { normalize(it.title).contains(q) || normalize(it.text).contains(q) }.sortedByDescending { it.modified }
        }
    }
    val rows = results?.map { "n:${it.file.path}" } ?: books.orEmpty().map { "b:${it.first.dir.path}" }
    val keys = listOf("chip:search") + rows
    val target = rememberFocusTarget(route, keys)
    fun search() = NoteInput.ask(Tx.s(R.string.apps_notes_search), NotesUi.query) { NotesUi.query = it.trim() }
    fun newMenu() = NotesMenu.show(
        Tx.s(R.string.apps_notes_new), listOf(
            NotesMenuRow("note", Tx.s(R.string.apps_notes_new_general), color = Palette.Mint) { NotesUi.create(NoteFiles.general(NoteStore.root), NotesUi.Kind.Note) },
            NotesMenuRow("book", Tx.s(R.string.apps_notes_new_book), color = Palette.Sky) {
                NoteInput.ask(Tx.s(R.string.apps_notes_name_book)) { name ->
                    NoteStore.later({ NoteFiles.newBook(NoteStore.root, name) }) { b ->
                        if (b == null) Toasts.show(Tx.s(R.string.apps_notes_name_taken)) else { NoteStore.changed(); NotesUi.book = b }
                    }
                }
            },
        ),
    )
    InterceptButtons(toolActive("notes")) { b ->
        val at = FocusMemory.get(route)
        when (b) {
            Button.X -> { newMenu(); true }
            Button.Select -> { search(); true }
            Button.Y -> {
                books?.firstOrNull { "b:${it.first.dir.path}" == at }?.let { (book, notes) -> bookMenu(book, notes) }
                    ?: results?.firstOrNull { "n:${it.file.path}" == at }?.let { noteMenu(it) }
                true
            }
            else -> false
        }
    }
    val total = books?.sumOf { it.second.size } ?: 0
    ToolScaffold(app, books?.let { Tx.p(R.plurals.apps_notes_count, total) }, hints = {
        ButtonHint("A", Tx.s(R.string.apps_notes_open), Palette.Mint)
        ButtonHint("X", Tx.s(R.string.apps_notes_new), Palette.Sky)
        ButtonHint("Y", Tx.s(R.string.apps_notes_options), Palette.Butter)
        ButtonHint("Select", Tx.s(R.string.apps_notes_search_hint), Palette.Lavender)
    }) {
        if (books == null) {
            PipNote(Tx.s(R.string.apps_notes_opening), "ROMs/⁠notes", mood = 1, modifier = Modifier.align(Alignment.Center))
            return@ToolScaffold
        }
        Column(Modifier.fillMaxSize()) {
            ToolChips(route, listOf("search" to if (query.isEmpty()) Tx.s(R.string.apps_notes_search) else Tx.s(R.string.apps_notes_search_clear, query)), "", target, app.color) {
                if (query.isEmpty()) search() else NotesUi.query = ""
            }
            val state = rememberRouteListState("$route:${query.isNotEmpty()}")
            KeepInView(state, rows.indexOf(target))
            val list = results
            LazyColumn(
                Modifier.fillMaxWidth().weight(1f).fadingEdges(state), state = state,
                contentPadding = PaddingValues(horizontal = FocusRoomSide, vertical = FocusRoom), verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (list != null) {
                    if (list.isEmpty()) item("none") {
                        Text(Tx.s(R.string.apps_notes_search_none, query), Modifier.padding(12.dp), style = MaterialTheme.typography.titleMedium, color = Palette.Muted, maxLines = 2)
                    }
                    items(list, key = { "n:${it.file.path}" }) { e ->
                        val k = "n:${e.file.path}"
                        val color = bookColor(e.book, app.color)
                        ToolRow(
                            e.title, Tx.s(R.string.apps_notes_found, bookTitle(e.book), noteLine(e)), color, Modifier.takesFocus(route, k, k == target), tag = ago(e.modified),
                            onFocus = { Selection.current = selectedFor(e.book, e.title, color) },
                            onClick = { NotesUi.book = e.book; NotesUi.openNote(e.file) },
                        ) { BookArt(e.book, color) }
                    }
                } else {
                    items(books, key = { "b:${it.first.dir.path}" }) { (b, notes) ->
                        val k = "b:${b.dir.path}"
                        val color = bookColor(b, app.color)
                        val playing = b.gameKey != null && RunningGames.list.any { it.game?.key == b.gameKey }
                        ToolRow(
                            bookTitle(b), if (notes.isEmpty()) Tx.s(R.string.apps_notes_none_yet) else Tx.p(R.plurals.apps_notes_book_line, notes.size, notes.size, notes.first().title),
                            color, Modifier.takesFocus(route, k, k == target),
                            tag = if (playing) Tx.s(R.string.apps_notes_playing) else notes.firstOrNull()?.let { ago(it.modified) },
                            onFocus = { Selection.current = selectedFor(b, bookTitle(b), color) },
                            onClick = { NotesUi.book = b },
                        ) { BookArt(b, color) }
                    }
                }
            }
        }
    }
}

/** What the other screen shows for a notebook or note: its game, or the note's name. */
private fun selectedFor(b: Notebook, title: String, color: Color): Selected =
    NoteStore.gameOf(b)?.let { Selected.GameItem(it) } ?: Selected.Group(title, Tx.s(R.string.apps_notes_selected, bookTitle(b)), emptyList(), color)

private fun bookMenu(b: Notebook, notes: List<NoteEntry>) {
    val rows = buildList {
        add(NotesMenuRow("open", Tx.s(R.string.apps_notes_open), color = Palette.Mint) { NotesUi.book = b })
        add(NotesMenuRow("new", Tx.s(R.string.apps_notes_new_here), color = Palette.Sky) { NotesUi.create(b, NotesUi.Kind.Note) })
        if (b.gameKey == null && b.name != NoteFiles.GENERAL) add(NotesMenuRow("rename", Tx.s(R.string.apps_notes_rename), color = Palette.Butter) {
            NoteInput.ask(Tx.s(R.string.apps_notes_rename_book), b.name) { name ->
                val clean = NoteFiles.safeName(name)
                NoteStore.later({ clean.isNotEmpty() && !File(b.dir.parentFile, clean).exists() && b.dir.renameTo(File(b.dir.parentFile, clean)) }) { ok ->
                    if (ok == true) NoteStore.changed() else if (clean != b.name) Toasts.show(Tx.s(R.string.apps_notes_name_taken))
                }
            }
        })
        if (notes.isEmpty() && b.dir.exists() && b.name != NoteFiles.GENERAL) add(NotesMenuRow("delete", Tx.s(R.string.apps_notes_delete_book), color = Palette.Peach, hold = true) {
            NoteStore.later({ NoteFiles.isEmpty(b) && b.dir.deleteRecursively() }) { ok ->
                if (ok == true) { Toasts.show(Tx.s(R.string.apps_notes_book_deleted)); NoteStore.changed() } else Toasts.show(Tx.s(R.string.apps_notes_book_not_empty))
            }
        })
    }
    NotesMenu.show(bookTitle(b), rows)
}

/** Y on a note in a list: rename, move, delete. */
fun noteMenu(e: NoteEntry) {
    NotesMenu.show(
        e.title, listOf(
            NotesMenuRow("open", Tx.s(R.string.apps_notes_open), color = Palette.Mint) { NotesUi.book = e.book; NotesUi.openNote(e.file) },
            NotesMenuRow("rename", Tx.s(R.string.apps_notes_rename), color = Palette.Butter) {
                NoteInput.ask(Tx.s(R.string.apps_notes_rename_note), e.title) { name ->
                    NoteStore.later({ NoteFiles.rename(e.file, name) }) { NoteStore.changed() }
                }
            },
            NotesMenuRow("move", Tx.s(R.string.apps_notes_move), color = Palette.Sky) { moveMenu(e) },
            NotesMenuRow("delete", Tx.s(R.string.apps_notes_delete_note), color = Palette.Peach, hold = true) {
                NoteStore.later({ NoteFiles.delete(e.file) }) { ok ->
                    Toasts.show(if (ok == true) Tx.s(R.string.apps_notes_note_deleted) else Tx.s(R.string.apps_notes_delete_failed))
                    NoteStore.changed()
                    Nav.focusTick++
                }
            },
        ),
    )
}

private fun moveMenu(e: NoteEntry) {
    NoteStore.later({ NoteStore.scan().map { it.first }.filter { it.dir != e.book.dir } }) { books ->
        val all = books.orEmpty().let { list -> if (list.none { it.name == NoteFiles.GENERAL } && e.book.name != NoteFiles.GENERAL) listOf(Notebook(File(NoteStore.root, NoteFiles.GENERAL), null)) + list else list }
        if (all.isEmpty()) { Toasts.show(Tx.s(R.string.apps_notes_need_book)); return@later }
        NotesMenu.show(Tx.s(R.string.apps_notes_move_to, e.title), all.map { b ->
            NotesMenuRow("to:${b.dir.path}", bookTitle(b), color = Palette.Sky) {
                NoteStore.later({ b.dir.mkdirs(); NoteFiles.move(e.file, b) }) { to ->
                    Toasts.show(if (to != null) Tx.s(R.string.apps_notes_moved, bookTitle(b)) else Tx.s(R.string.apps_notes_move_failed))
                    NoteStore.changed()
                }
            }
        })
    }
}

// --- A notebook -----------------------------------------------------------------------------------------------------

@Composable
private fun NotebookScreen(book: Notebook) {
    val app = BuiltInApps.byId("notes")!!
    val route = "${Route.Tool("notes").key}:book:${book.dir.path}"
    androidx.activity.compose.BackHandler(enabled = toolActive("notes")) { NotesUi.book = null; Nav.focusTick++ }
    val notes by produceState<List<NoteEntry>?>(null, book, NoteStore.version) { value = withContext(Dispatchers.IO) { NoteFiles.notes(book) } }
    val color = bookColor(book, app.color)
    val kinds = NotesUi.Kind.entries.map { "new:${it.name}" to Tx.s(R.string.apps_notes_new_kind, it.label) }
    val rows = notes.orEmpty().map { it.file.path }
    val target = rememberFocusTarget(route, kinds.map { "chip:${it.first}" } + rows)
    InterceptButtons(toolActive("notes")) { b ->
        when (b) {
            Button.X -> {
                NotesMenu.show(Tx.s(R.string.apps_notes_new_in, bookTitle(book)), NotesUi.Kind.entries.map { k -> NotesMenuRow(k.name, k.label, color = Palette.Sky) { NotesUi.create(book, k) } })
                true
            }
            Button.Y -> { notes?.firstOrNull { it.file.path == FocusMemory.get(route) }?.let { noteMenu(it) }; true }
            else -> false
        }
    }
    ToolScaffold(app, bookTitle(book), hints = {
        ButtonHint("A", Tx.s(R.string.apps_notes_open), Palette.Mint)
        ButtonHint("X", Tx.s(R.string.apps_notes_new), Palette.Sky)
        ButtonHint("Y", Tx.s(R.string.apps_notes_options), Palette.Butter)
        ButtonHint("B", Tx.s(R.string.apps_notes_notebooks), Palette.Lavender)
    }) {
        Column(Modifier.fillMaxSize()) {
            ToolChips(route, kinds, "", target, color) { id -> NotesUi.create(book, NotesUi.Kind.valueOf(id.removePrefix("new:"))) }
            val list = notes
            when {
                list == null -> Unit
                list.isEmpty() -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        Tx.s(R.string.apps_notes_book_empty, bookTitle(book)), Modifier.padding(24.dp),
                        style = MaterialTheme.typography.titleMedium, color = Palette.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                }
                else -> {
                    val state = rememberRouteListState(route)
                    KeepInView(state, rows.indexOf(target))
                    LazyColumn(
                        Modifier.fillMaxWidth().weight(1f).fadingEdges(state), state = state,
                        contentPadding = PaddingValues(horizontal = FocusRoomSide, vertical = FocusRoom), verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(list, key = { it.file.path }) { e ->
                            ToolRow(
                                e.title, noteLine(e), color, Modifier.takesFocus(route, e.file.path, e.file.path == target), tag = ago(e.modified),
                                onFocus = { Selection.current = selectedFor(book, e.title, color) },
                                onClick = { NotesUi.openNote(e.file) },
                            ) { NoteThumb(e, color) }
                        }
                    }
                }
            }
        }
    }
}

/** A note's picture in a list: its first picture, else a symbol for what it mostly is. */
@Composable
private fun NoteThumb(e: NoteEntry, color: Color) {
    val first = remember(e.text) {
        NoteDoc.parse(e.text).firstNotNullOfOrNull { b -> (b as? NoteDoc.Block.Picture)?.path }
    }
    val file = remember(first, NoteStore.version) { first?.let { NoteStore.picture(e.file, it) } }
    if (file != null) NotePicture(file, 160, Modifier.fillMaxSize(), crop = true)
    else TandemIconTile(if (e.summary.tasks > 0) TIcon.UpNext else if (e.summary.rows > 0) TIcon.Book else TIcon.Notes, color)
}

// --- Menus ------------------------------------------------------------------------------------------------------------

/** A row in a Notes popup menu; [hold] for something that can't be undone. */
class NotesMenuRow(val id: String, val label: String, val value: String? = null, val color: Color = Palette.Mint, val hold: Boolean = false, val action: () -> Unit)

/** The Notes app's popup menus (Y on a note or block, X for something new). */
object NotesMenu {
    class Request(val title: String, val rows: List<NotesMenuRow>)

    var request by mutableStateOf<Request?>(null)
        private set

    fun show(title: String, rows: List<NotesMenuRow>) {
        FocusMemory.forget(ROUTE)
        request = Request(title, rows)
    }

    fun close() { request = null; Nav.focusTick++ }

    const val ROUTE = "notesmenu"
}

@Composable
private fun NotesMenuOverlay() {
    val req = NotesMenu.request
    val shown = rememberLatest(req)
    PanelGuard(req != null)
    InterceptButtons(req != null) { true }
    OverlayPanel(req != null, onClose = { NotesMenu.close() }) {
        val r = shown ?: return@OverlayPanel
        val target = rememberFocusTarget(NotesMenu.ROUTE, r.rows.map { it.id })
        PanelSurface(Modifier.width(440.dp)) {
            Text(r.title, Modifier.padding(start = 6.dp, bottom = 8.dp), style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val scroll = rememberScrollState()
            Column(
                Modifier.weight(1f, fill = false).fadingEdges(scroll).verticalScroll(scroll).padding(horizontal = 6.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                for (row in r.rows) {
                    val act = { NotesMenu.close(); row.action() }
                    if (row.hold) HoldMenuRow(row.label, row.id, NotesMenu.ROUTE, target, row.color) { act() }
                    else MenuRow(row.label, row.value, row.id, NotesMenu.ROUTE, target, row.color) { act() }
                }
            }
        }
    }
}
