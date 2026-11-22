package io.github.zahakj.tandem.ui

import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.zahakj.tandem.Button
import io.github.zahakj.tandem.NoteStore
import io.github.zahakj.tandem.OpenNote
import io.github.zahakj.tandem.R
import io.github.zahakj.tandem.Tx
import io.github.zahakj.tandem.data.NoteDoc
import io.github.zahakj.tandem.data.NoteDoc.Block
import io.github.zahakj.tandem.data.NoteFiles
import java.io.File

/*
 * A note on the top screen: its blocks as a list of cards. A edits the highlighted one (text with the keyboard, a
 * checklist item ticks, a picture opens for drawing on, a glossary entry opens its cells), Y has the block's other
 * actions, X adds something after it. Every change is saved at once.
 */

/** One line of the note's list: its title, a block, a glossary's headings or entry, or the last "add" card. */
private sealed interface NoteItem {
    val key: String
    val focusable: Boolean get() = true

    data object Title : NoteItem { override val key = "title" }
    data class Part(val i: Int, val block: Block) : NoteItem { override val key = "b$i" }
    data class Headings(val i: Int, val table: Block.Table) : NoteItem {
        override val key = "b$i:h"
        override val focusable get() = false
    }
    data class Entry(val i: Int, val row: Int, val cells: List<String>, val columns: Int) : NoteItem { override val key = "b$i:r$row" }
    data class AddEntry(val i: Int) : NoteItem { override val key = "b$i:add" }
    data object Add : NoteItem { override val key = "add" }
}

private fun itemsOf(blocks: List<Block>): List<NoteItem> = buildList {
    add(NoteItem.Title)
    blocks.forEachIndexed { i, b ->
        if (b is Block.Table) {
            val cols = maxOf(1, b.header.size)
            add(NoteItem.Headings(i, b))
            b.rows.forEachIndexed { r, cells -> add(NoteItem.Entry(i, r, cells, cols)) }
            add(NoteItem.AddEntry(i))
        } else add(NoteItem.Part(i, b))
    }
    add(NoteItem.Add)
}

/** The block a focus key belongs to: -1 for the title, the last block for "add". */
private fun blockOf(key: String?, blocks: List<Block>): Int = when {
    key == null || key == "title" -> -1
    key == "add" -> blocks.size - 1
    else -> key.removePrefix("b").substringBefore(':').toIntOrNull() ?: (blocks.size - 1)
}

private fun noteRoute(note: OpenNote) = "${Route.Tool("notes").key}:note:${note.file.path}"

/** Highlights block [i] of [note] once the list shows it. */
private fun focusBlock(note: OpenNote, key: String) {
    FocusMemory.remember(noteRoute(note), key)
    Nav.focusTick++
}

/** The path a note's link to [file] is written with (decoded; NoteDoc writes it escaped). */
fun relPath(note: File, file: File): String = NoteDoc.decodePath(NoteFiles.link(note, file))

@Composable
fun NoteScreen(note: OpenNote) {
    val app = BuiltInApps.byId("notes")!!
    val route = noteRoute(note)
    BackHandler(enabled = toolActive("notes")) { NotesUi.closeNote() }
    val blocks = note.blocks
    val items = remember(blocks) { itemsOf(blocks.orEmpty()) }
    val keys = items.filter { it.focusable }.map { it.key }
    val target = rememberFocusTarget(route, keys)
    InterceptButtons(toolActive("notes") && blocks != null) { b ->
        val at = FocusMemory.get(route)
        when (b) {
            Button.X -> { addMenu(note, blockOf(at, blocks.orEmpty())); true }
            Button.Y -> { items.firstOrNull { it.key == at }?.let { itemMenu(note, it) }; true }
            else -> false
        }
    }
    val book = NotesUi.book
    ToolScaffold(app, book?.let { NoteStore.gameOf(it)?.title ?: it.name }, hints = {
        ButtonHint("A", Tx.s(R.string.apps_note_edit), Palette.Mint)
        ButtonHint("X", Tx.s(R.string.apps_note_add), Palette.Sky)
        ButtonHint("Y", Tx.s(R.string.apps_notes_options), Palette.Butter)
        ButtonHint("B", Tx.s(R.string.apps_note_notes), Palette.Lavender)
    }) {
        if (blocks == null) return@ToolScaffold
        val state = rememberRouteListState(route)
        KeepInView(state, items.indexOfFirst { it.key == target })
        LazyColumn(
            Modifier.fillMaxSize().fadingEdges(state), state = state,
            contentPadding = PaddingValues(horizontal = FocusRoomSide, vertical = FocusRoom), verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(items, key = { it.key }) { item ->
                val focus = Modifier.takesFocus(route, item.key, item.key == target)
                when (item) {
                    NoteItem.Title -> BlockCard(focus, app.color, onClick = { renameNote(note) }) {
                        Text(note.title, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            Tx.s(R.string.apps_note_edited, DateUtils.getRelativeTimeSpanString(note.file.lastModified()).toString()),
                            style = MaterialTheme.typography.labelMedium, color = Palette.Muted, maxLines = 1,
                        )
                    }
                    is NoteItem.Part -> PartCard(note, item, focus, app.color)
                    is NoteItem.Headings -> Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 6.dp)) {
                        val cols = maxOf(1, item.table.header.size)
                        for (k in 0 until cols) GlossaryCell(note.file, item.table.header.getOrElse(k) { "" }, Modifier.weight(cellWeight(k)), header = true)
                    }
                    is NoteItem.Entry -> BlockCard(focus, app.color, onClick = { RowEditor.open(note, item.i, item.row) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            for (k in 0 until item.columns) GlossaryCell(note.file, item.cells.getOrElse(k) { "" }, Modifier.weight(cellWeight(k)).padding(end = 8.dp))
                        }
                    }
                    is NoteItem.AddEntry -> BlockCard(focus, app.color, onClick = { addEntry(note, item.i) }) {
                        Text(Tx.s(R.string.apps_note_add_entry), style = MaterialTheme.typography.titleSmall, color = Palette.Muted, maxLines = 1)
                    }
                    NoteItem.Add -> BlockCard(focus, Palette.Sky, onClick = { addMenu(note, blocks.size - 1) }) {
                        Text(
                            if (blocks.isEmpty()) Tx.s(R.string.apps_note_empty) else Tx.s(R.string.apps_note_add_more),
                            style = MaterialTheme.typography.titleSmall, color = Palette.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/** The first column of a glossary (the symbol) is narrower. */
private fun cellWeight(k: Int) = if (k == 0) 0.45f else 1f

@Composable
private fun BlockCard(focus: Modifier, color: androidx.compose.ui.graphics.Color, onClick: () -> Unit, content: @Composable () -> Unit) {
    FocusCard(Modifier.fillMaxWidth().then(focus), glow = color, shape = RoundedCornerShape(16.dp), entry = true, onClick = onClick) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { content() }
    }
}

@Composable
private fun PartCard(note: OpenNote, item: NoteItem.Part, focus: Modifier, color: androidx.compose.ui.graphics.Color) {
    when (val b = item.block) {
        is Block.Text -> BlockCard(focus, color, onClick = { editText(note, item.i) }) { MdText(b.text) }
        is Block.Task -> BlockCard(focus, color, onClick = { toggleTask(note, item.i) }) { TaskLine(b.text, b.done) }
        is Block.Picture -> {
            val file = remember(b.path, NoteStore.version) { NoteStore.picture(note.file, b.path) }
            BlockCard(focus, color, onClick = { drawOn(note, item.i) }) {
                NotePicture(file, 1200, Modifier.fillMaxWidth().heightIn(max = 260.dp).clip(RoundedCornerShape(10.dp)))
                if (b.caption.isNotEmpty()) Text(b.caption, style = MaterialTheme.typography.labelLarge, color = Palette.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        is Block.Table -> Unit
    }
}

// --- Actions -----------------------------------------------------------------------------------------------------------

/** A new note's first step: write, list, fill in a glossary entry or draw (NotesUi.create). */
fun startNote(note: OpenNote, kind: NotesUi.Kind) {
    when (kind) {
        NotesUi.Kind.Note -> NoteInput.ask(Tx.s(R.string.apps_note_write), multiLine = true) { t ->
            if (t.isNotBlank()) note.edit { NoteDoc.insert(it, it.size, Block.Text(t.trimEnd())) }
        }
        NotesUi.Kind.Checklist -> askTask(note, -1)
        NotesUi.Kind.Glossary -> RowEditor.open(note, 0, 0)
        NotesUi.Kind.Sketch -> SketchPad.open(note.file, null, square = false, title = Tx.s(R.string.apps_note_sketch), onDiscard = { dropIfEmpty(note) }) { png ->
            note.edit { it + Block.Picture(relPath(note.file, png), "") }
        }
    }
}

/** A sketch note left without a drawing goes again, rather than staying as an empty note. */
private fun dropIfEmpty(note: OpenNote) {
    val f = note.file
    NoteStore.later({ f.length() == 0L && NoteFiles.delete(f) }) { gone ->
        if (gone == true) { NotesUi.closeNote(); NoteStore.changed() }
    }
}

private fun renameNote(note: OpenNote) = NoteInput.ask(Tx.s(R.string.apps_notes_rename_note), note.title) { name ->
    note.rename(name) { to -> if (to == null && NoteFiles.safeName(name) != note.title) Toasts.show(Tx.s(R.string.apps_note_rename_failed)) }
}

private fun editText(note: OpenNote, i: Int) {
    val b = note.blocks?.getOrNull(i) as? Block.Text ?: return
    NoteInput.ask(Tx.s(R.string.apps_note_edit_text), b.text, multiLine = true) { t ->
        note.edit { blocks -> blocks.toMutableList().apply { if (t.isBlank()) removeAt(i) else set(i, Block.Text(t.trimEnd())) } }
    }
}

private fun toggleTask(note: OpenNote, i: Int) {
    note.edit { blocks -> blocks.toMutableList().apply { (get(i) as? Block.Task)?.let { set(i, it.copy(done = !it.done)) } } }
    Sounds.toggle((note.blocks?.getOrNull(i) as? Block.Task)?.done == true)
}

/** Asks for checklist items one after another, after block [after]; an empty one stops. */
private fun askTask(note: OpenNote, after: Int) {
    NoteInput.ask(Tx.s(R.string.apps_note_new_item)) { t ->
        if (t.isBlank()) return@ask
        note.edit { NoteDoc.insert(it, after, Block.Task(t.trim(), false)) }
        focusBlock(note, "b${after + 1}")
        askTask(note, after + 1)
    }
}

/** Opens picture [i] for drawing on (a drawing made here opens as it was drawn). */
private fun drawOn(note: OpenNote, i: Int) {
    val b = note.blocks?.getOrNull(i) as? Block.Picture ?: return
    val file = NoteStore.picture(note.file, b.path) ?: run { Toasts.show(Tx.s(R.string.apps_note_picture_gone)); return }
    SketchPad.open(note.file, file, square = false, title = if (NoteStore.loadSketch(file) != null) Tx.s(R.string.apps_note_drawing) else Tx.s(R.string.apps_note_draw_on)) { png ->
        if (png != file) note.edit { blocks -> blocks.toMutableList().apply { set(i, b.copy(path = relPath(note.file, png))) } }
    }
}

private fun addEntry(note: OpenNote, near: Int) {
    var at: Pair<Int, Int>? = null
    note.edit { blocks -> NoteDoc.addRow(blocks, near, NotesUi.glossaryHeader()).let { (b, t, r) -> at = t to r; b } }
    at?.let { (t, r) -> focusBlock(note, "b$t:r$r"); RowEditor.open(note, t, r) }
}

/** X: what can be added after block [after]. */
private fun addMenu(note: OpenNote, after: Int) {
    fun focusNew() = focusBlock(note, "b${after + 1}")
    NotesMenu.show(
        Tx.s(R.string.apps_note_add_to, note.title), listOf(
            NotesMenuRow("text", Tx.s(R.string.apps_note_add_text), color = Palette.Mint) {
                NoteInput.ask(Tx.s(R.string.apps_note_new_text), multiLine = true) { t ->
                    if (t.isNotBlank()) { note.edit { NoteDoc.insert(it, after, Block.Text(t.trimEnd())) }; focusNew() }
                }
            },
            NotesMenuRow("task", Tx.s(R.string.apps_note_add_tasks), color = Palette.Mint) { askTask(note, after) },
            NotesMenuRow("entry", Tx.s(R.string.apps_note_add_glossary), value = Tx.s(R.string.apps_note_add_glossary_what), color = Palette.Butter) { addEntry(note, after) },
            NotesMenuRow("sketch", Tx.s(R.string.apps_note_sketch), color = Palette.Sky) {
                SketchPad.open(note.file, null, square = false, title = Tx.s(R.string.apps_note_sketch)) { png ->
                    note.edit { NoteDoc.insert(it, after, Block.Picture(relPath(note.file, png), "")) }
                    focusNew()
                }
            },
            NotesMenuRow("picture", Tx.s(R.string.apps_note_add_picture), color = Palette.Lavender) {
                ImagePick.start("gallery", Tx.s(R.string.apps_note_pick_picture, note.title)) { f ->
                    NoteStore.later({ NoteStore.importPicture(note.file, f) }) { copy ->
                        if (copy == null) Toasts.show(Tx.s(R.string.apps_note_copy_failed))
                        else { note.edit { NoteDoc.insert(it, after, Block.Picture(relPath(note.file, copy), "")) }; focusNew() }
                    }
                }
            },
        ),
    )
}

/** Y on a line of the note. */
private fun itemMenu(note: OpenNote, item: NoteItem) {
    val blocks = note.blocks ?: return
    fun moveBy(i: Int, by: Int) = NotesMenuRow(if (by < 0) "up" else "down", if (by < 0) Tx.s(R.string.apps_note_move_up) else Tx.s(R.string.apps_note_move_down), color = Palette.Sky) {
        note.edit { NoteDoc.move(it, i, by) }
        focusBlock(note, "b${(i + by).coerceIn(0, blocks.size - 1)}")
    }
    fun delete(i: Int, @StringRes label: Int, @StringRes done: Int) = NotesMenuRow("delete", Tx.s(label), color = Palette.Peach) {
        val before = note.blocks ?: return@NotesMenuRow
        note.edit { it.toMutableList().apply { removeAt(i) } }
        Nav.focusTick++
        Undo.offer(Tx.s(done)) { note.edit { before } }
    }
    when (item) {
        NoteItem.Title -> NotesMenu.show(note.title, listOf(
            NotesMenuRow("rename", Tx.s(R.string.apps_notes_rename), color = Palette.Butter) { renameNote(note) },
            NotesMenuRow("delete", Tx.s(R.string.apps_notes_delete_note), color = Palette.Peach, hold = true) {
                val f = note.file
                NoteStore.later({ NoteFiles.delete(f) }) { ok ->
                    Toasts.show(if (ok == true) Tx.s(R.string.apps_notes_note_deleted) else Tx.s(R.string.apps_notes_delete_failed))
                    NotesUi.closeNote()
                    NoteStore.changed()
                }
            },
        ))
        is NoteItem.Part -> when (val b = item.block) {
            is Block.Text -> NotesMenu.show(Tx.s(R.string.apps_note_text), listOf(
                NotesMenuRow("edit", Tx.s(R.string.apps_note_edit), color = Palette.Mint) { editText(note, item.i) },
                moveBy(item.i, -1), moveBy(item.i, 1), delete(item.i, R.string.apps_note_delete_text, R.string.apps_note_deleted_text),
            ))
            is Block.Task -> NotesMenu.show(b.text.ifEmpty { Tx.s(R.string.apps_note_task) }, listOf(
                NotesMenuRow("tick", if (b.done) Tx.s(R.string.apps_note_untick) else Tx.s(R.string.apps_note_tick), color = Palette.Mint) { toggleTask(note, item.i) },
                NotesMenuRow("edit", Tx.s(R.string.apps_note_edit), color = Palette.Butter) {
                    NoteInput.ask(Tx.s(R.string.apps_note_edit_item), b.text) { t ->
                        note.edit { l -> l.toMutableList().apply { if (t.isBlank()) removeAt(item.i) else set(item.i, b.copy(text = t.trim())) } }
                    }
                },
                moveBy(item.i, -1), moveBy(item.i, 1), delete(item.i, R.string.apps_note_delete_item, R.string.apps_note_deleted_item),
            ))
            is Block.Picture -> NotesMenu.show(b.caption.ifEmpty { Tx.s(R.string.apps_note_picture) }, listOf(
                NotesMenuRow("draw", Tx.s(R.string.apps_note_draw_crop), color = Palette.Mint) { drawOn(note, item.i) },
                NotesMenuRow("caption", Tx.s(R.string.apps_note_caption), value = b.caption.ifEmpty { null }, color = Palette.Butter) {
                    NoteInput.ask(Tx.s(R.string.apps_note_caption), b.caption) { t -> note.edit { l -> l.toMutableList().apply { set(item.i, b.copy(caption = t.trim())) } } }
                },
                moveBy(item.i, -1), moveBy(item.i, 1), delete(item.i, R.string.apps_note_delete_picture, R.string.apps_note_deleted_picture),
            ))
            is Block.Table -> Unit
        }
        is NoteItem.Entry, is NoteItem.AddEntry -> {
            val i = if (item is NoteItem.Entry) item.i else (item as NoteItem.AddEntry).i
            val table = blocks.getOrNull(i) as? Block.Table ?: return
            NotesMenu.show(Tx.s(R.string.apps_note_glossary), buildList {
                if (item is NoteItem.Entry) {
                    add(NotesMenuRow("edit", Tx.s(R.string.apps_note_edit_entry), color = Palette.Mint) { RowEditor.open(note, i, item.row) })
                    add(NotesMenuRow("delete", Tx.s(R.string.apps_note_delete_entry), color = Palette.Peach) {
                        val before = note.blocks ?: return@NotesMenuRow
                        note.edit { l -> l.toMutableList().apply { set(i, table.copy(rows = table.rows.filterIndexed { r, _ -> r != item.row })) } }
                        Nav.focusTick++
                        Undo.offer(Tx.s(R.string.apps_note_deleted_entry)) { note.edit { before } }
                    })
                }
                add(NotesMenuRow("headings", Tx.s(R.string.apps_note_edit_headings), value = table.header.joinToString(" · "), color = Palette.Butter) { RowEditor.open(note, i, -1) })
                add(NotesMenuRow("column", Tx.s(R.string.apps_note_add_column), color = Palette.Sky) {
                    NoteInput.ask(Tx.s(R.string.apps_note_column_heading)) { h ->
                        if (h.isNotBlank()) note.edit { l -> l.toMutableList().apply { set(i, table.copy(header = table.header + h.trim(), rows = table.rows.map { it + "" })) } }
                    }
                })
                add(moveBy(i, -1)); add(moveBy(i, 1))
                add(NotesMenuRow("deltable", Tx.s(R.string.apps_note_delete_glossary), color = Palette.Peach) {
                    val before = note.blocks ?: return@NotesMenuRow
                    note.edit { it.toMutableList().apply { removeAt(i) } }
                    Nav.focusTick++
                    Undo.offer(Tx.s(R.string.apps_note_deleted_glossary)) { note.edit { before } }
                })
            })
        }
        is NoteItem.Headings, NoteItem.Add -> addMenu(note, blocks.size - 1)
    }
}

// --- A glossary entry ----------------------------------------------------------------------------------------------

/** The popup for one glossary entry (or its headings): each cell typed (A) or drawn (Y). */
object RowEditor {
    class Target(val note: OpenNote, val table: Int, val row: Int)

    var target by mutableStateOf<Target?>(null)
        private set

    /** [row] -1 edits the headings. */
    fun open(note: OpenNote, table: Int, row: Int) {
        FocusMemory.forget(ROUTE)
        target = Target(note, table, row)
    }

    fun close() { target = null; Nav.focusTick++ }

    const val ROUTE = "noterow"

    fun set(t: Target, col: Int, value: String) {
        t.note.edit { blocks ->
            val table = blocks.getOrNull(t.table) as? Block.Table ?: return@edit blocks
            fun put(cells: List<String>) = List(maxOf(cells.size, col + 1)) { k -> if (k == col) value else cells.getOrElse(k) { "" } }
            blocks.toMutableList().apply {
                set(t.table, if (t.row < 0) table.copy(header = put(table.header)) else table.copy(rows = table.rows.mapIndexed { r, cells -> if (r == t.row) put(cells) else cells }))
            }
        }
    }

    fun cells(t: Target): Pair<List<String>, List<String>>? {
        val table = t.note.blocks?.getOrNull(t.table) as? Block.Table ?: return null
        val cells = if (t.row < 0) table.header else table.rows.getOrNull(t.row) ?: return null
        val cols = maxOf(table.header.size, cells.size, 1)
        return List(cols) { table.header.getOrElse(it) { "" } } to List(cols) { cells.getOrElse(it) { "" } }
    }

    fun type(t: Target, col: Int) {
        val (header, cells) = cells(t) ?: return
        val cell = cells[col]
        val start = if (NoteDoc.cellPicture(cell) != null) "" else cell
        NoteInput.ask(if (t.row < 0) Tx.s(R.string.apps_note_heading_n, col + 1) else header[col].ifEmpty { Tx.s(R.string.apps_note_column_n, col + 1) }, start) { v -> set(t, col, v.trim()) }
    }

    fun draw(t: Target, col: Int) {
        if (t.row < 0) return
        val (header, cells) = cells(t) ?: return
        val note = t.note.file
        val existing = NoteDoc.cellPicture(cells[col])?.let { NoteStore.picture(note, it) }
        SketchPad.open(note, existing, square = true, title = Tx.s(R.string.apps_note_draw_cell, header[col].ifEmpty { Tx.s(R.string.apps_note_draw_symbol) })) { png ->
            set(t, col, NoteDoc.pictureCell(relPath(note, png)))
        }
    }
}

@Composable
fun RowEditorOverlay() {
    val t = RowEditor.target
    val shown = rememberLatest(t)
    PanelGuard(t != null)
    InterceptButtons(t != null) { b ->
        val col = FocusMemory.get(RowEditor.ROUTE)?.removePrefix("c")?.toIntOrNull()
        if (b == Button.Y && t != null && col != null) RowEditor.draw(t, col)
        true
    }
    OverlayPanel(t != null, onClose = { RowEditor.close() }) {
        val tg = shown ?: return@OverlayPanel
        val (header, cells) = RowEditor.cells(tg) ?: return@OverlayPanel
        val keys = cells.indices.map { "c$it" } + listOfNotNull("delete".takeIf { tg.row >= 0 })
        val target = rememberFocusTarget(RowEditor.ROUTE, keys)
        PanelSurface(Modifier.width(560.dp)) {
            Text(if (tg.row < 0) Tx.s(R.string.apps_note_headings) else Tx.s(R.string.apps_note_entry_n, tg.row + 1), Modifier.padding(start = 6.dp, bottom = 8.dp), style = MaterialTheme.typography.titleLarge, maxLines = 1)
            val scroll = rememberScrollState()
            Column(
                Modifier.weight(1f, fill = false).fadingEdges(scroll).verticalScroll(scroll).padding(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                cells.forEachIndexed { k, cell ->
                    FocusCard(
                        Modifier.fillMaxWidth().takesFocus(RowEditor.ROUTE, "c$k", "c$k" == target), glow = Palette.Mint, shape = RoundedCornerShape(14.dp),
                        onLongPress = { RowEditor.draw(tg, k) }, onClick = { RowEditor.type(tg, k) },
                    ) {
                        Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (tg.row < 0) Tx.s(R.string.apps_note_column_n, k + 1) else header[k].ifEmpty { Tx.s(R.string.apps_note_column_n, k + 1) }, Modifier.width(120.dp),
                                style = MaterialTheme.typography.labelLarge, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                            if (cell.isEmpty()) Text(if (tg.row < 0) Tx.s(R.string.apps_note_type) else Tx.s(R.string.apps_note_type_or_draw), style = MaterialTheme.typography.bodyLarge, color = Palette.Muted.copy(alpha = 0.6f), maxLines = 1)
                            else GlossaryCell(tg.note.file, cell, Modifier.weight(1f), pictureSize = 72.dp)
                        }
                    }
                }
                if (tg.row >= 0) MenuRow(Tx.s(R.string.apps_note_delete_entry), null, "delete", RowEditor.ROUTE, target, Palette.Peach) {
                    val before = tg.note.blocks
                    tg.note.edit { blocks ->
                        val table = blocks.getOrNull(tg.table) as? Block.Table ?: return@edit blocks
                        blocks.toMutableList().apply { set(tg.table, table.copy(rows = table.rows.filterIndexed { r, _ -> r != tg.row })) }
                    }
                    RowEditor.close()
                    if (before != null) Undo.offer(Tx.s(R.string.apps_note_deleted_entry)) { tg.note.edit { before } }
                }
            }
            Spacer(Modifier.size(8.dp))
            Row(Modifier.padding(horizontal = 6.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                ButtonHint("A", Tx.s(R.string.apps_note_type), Palette.Mint)
                if (tg.row >= 0) ButtonHint("Y", Tx.s(R.string.apps_note_draw), Palette.Sky)
                ButtonHint("B", Tx.s(R.string.apps_note_done), Palette.Lavender)
            }
        }
    }
}
