package io.github.zahakj.tandem.ui

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.zahakj.tandem.CompanionActivity
import io.github.zahakj.tandem.CompanionInfo
import io.github.zahakj.tandem.GameShot
import io.github.zahakj.tandem.NoteStore
import io.github.zahakj.tandem.OpenNote
import io.github.zahakj.tandem.data.NoteDoc
import io.github.zahakj.tandem.data.NoteDoc.Block
import io.github.zahakj.tandem.data.NoteEntry
import io.github.zahakj.tandem.data.NoteFiles
import io.github.zahakj.tandem.data.Notebook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/*
 * Notes on the bottom screen while a game plays (the companion, GameCompanion.kt): the game's notebook, by touch, so
 * the controller stays with the game. Write (Android's keyboard takes the controller only while it is up), tick,
 * fill in a glossary, draw, and take a screenshot of the game straight into a note. The same files as the Notes app.
 */

object CompanionNotes {
    var open by mutableStateOf(false)
    /** The game the panel was opened for; another game starts it afresh. */
    private var forKey: String? = null
    var note by mutableStateOf<OpenNote?>(null)

    /** Text being typed, what it is for, and what happens with it. */
    class Typing(val title: String, val initial: String, val multiLine: Boolean, val onDone: (String) -> Unit)

    var typing by mutableStateOf<Typing?>(null)

    fun show(key: String) {
        if (forKey != key) {
            note = null; typing = null; forKey = key
            if (SketchPad.session?.owner == OWNER) SketchPad.close()
        }
        open = true
    }

    fun hide() { open = false; typing = null }

    /** The companion now shows [key]'s game: the panel stays open only for the game it was opened for. */
    fun follow(key: String) { if (forKey != key) hide() }

    fun type(title: String, initial: String = "", multiLine: Boolean = false, onDone: (String) -> Unit) {
        typing = Typing(title, initial, multiLine, onDone)
    }
}

@Composable
fun CompanionNotesPanel(info: CompanionInfo, activity: CompanionActivity) {
    val ctx = LocalContext.current
    val say: (String) -> Unit = { Toast.makeText(ctx.applicationContext, it, Toast.LENGTH_SHORT).show() }
    val book by produceState<Notebook?>(null, info.key) { value = withContext(Dispatchers.IO) { NoteStore.bookFor(info.key, info.title, info.system) } }
    val typing = CompanionNotes.typing
    val sketch = SketchPad.session?.takeIf { it.owner == OWNER }
    val note = CompanionNotes.note
    // The keyboard needs the window's focus: taken while typing, given back to the game after.
    DisposableEffect(typing != null) {
        val on = typing != null
        if (on) activity.typing(true)
        onDispose { if (on) activity.typing(false) }
    }
    // While typing this window has the controller, and B arrives as Back: one step back here, never closing the
    // companion.
    androidx.activity.compose.BackHandler {
        when {
            typing != null -> CompanionNotes.typing = null
            sketch != null -> SketchPad.done()
            note != null -> CompanionNotes.note = null
            else -> CompanionNotes.hide()
        }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        when {
            typing != null -> TypingPage(typing)
            sketch != null -> SketchPage(sketch)
            note != null -> NotePage(note, activity, say)
            else -> ListPage(info, book, activity, say)
        }
    }
}

private const val OWNER = "companion"

@Composable
private fun Header(title: String, back: String, onBack: () -> Unit, onTitle: (() -> Unit)? = null, right: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        PanelChip("‹ $back", Palette.Lavender, onTap = onBack)
        Text(
            title, Modifier.weight(1f).then(if (onTitle != null) Modifier.clickable(onClick = onTitle) else Modifier),
            style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        right()
    }
}

@Composable
private fun PanelChip(text: String, color: Color, strong: Boolean = false, onTap: () -> Unit) {
    Box(
        Modifier.height(40.dp).clip(RoundedCornerShape(50)).background(color.copy(alpha = if (strong) 0.32f else 0.18f)).clickable(onClick = onTap)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = MaterialTheme.typography.labelLarge, color = if (strong) Palette.Text else color, maxLines = 1) }
}

/** The buttons along the bottom: what can be added. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddRow(vararg buttons: Pair<String, () -> Unit>) {
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val colors = listOf(Palette.Mint, Palette.Sky, Palette.Butter, Palette.Lavender, Palette.Peach)
        buttons.forEachIndexed { i, (label, act) -> PanelChip(label, colors[i % colors.size], onTap = act) }
    }
}

// --- The game's notes -----------------------------------------------------------------------------------------------

@Composable
private fun ColumnScope.ListPage(info: CompanionInfo, book: Notebook?, activity: CompanionActivity, say: (String) -> Unit) {
    val notes by produceState<List<NoteEntry>?>(null, book, NoteStore.version) {
        value = book?.let { b -> withContext(Dispatchers.IO) { NoteFiles.notes(b) } }
    }
    Header("Notes · ${info.title}", "Game", { CompanionNotes.hide() })
    val list = notes
    Box(Modifier.fillMaxWidth().weight(1f)) {
        if (list != null && list.isEmpty()) {
            Text(
                "No notes for this game yet. Start one below; they're kept in ROMs/⁠notes/⁠${book?.name.orEmpty()}.",
                Modifier.align(Alignment.Center).padding(16.dp), style = MaterialTheme.typography.bodyLarge, color = Palette.Muted, maxLines = 4,
            )
        } else if (list != null) {
            val state = rememberLazyListState()
            LazyColumn(Modifier.fillMaxSize().fadingEdges(state), state = state, contentPadding = PaddingValues(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(list, key = { it.file.path }) { e ->
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Palette.Card).clickable { CompanionNotes.note = OpenNote(e.file) }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                    ) {
                        Text(e.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(noteLine(e), style = MaterialTheme.typography.labelMedium, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
    val b = book ?: return
    fun make(kind: NotesUi.Kind) = newNote(b, kind, say)
    AddRow(
        "+ Note" to { make(NotesUi.Kind.Note) }, "+ Checklist" to { make(NotesUi.Kind.Checklist) }, "+ Glossary" to { make(NotesUi.Kind.Glossary) },
        "+ Sketch" to { make(NotesUi.Kind.Sketch) }, "+ Screenshot" to { shotNote(b, activity, say) },
    )
}

private fun newNote(book: Notebook, kind: NotesUi.Kind, say: (String) -> Unit) {
    val text = NotesUi.newText(kind)
    NoteStore.later({ NoteStore.create(book, NotesUi.newName(kind.label), text) }) { file ->
        file ?: run { say("Couldn't make a note in ROMs/notes"); return@later }
        val note = OpenNote(file, NoteDoc.parse(text))
        CompanionNotes.note = note
        when (kind) {
            NotesUi.Kind.Note -> CompanionNotes.type("Write your note", multiLine = true) { t -> if (t.isNotBlank()) note.edit { it + Block.Text(t.trimEnd()) } }
            NotesUi.Kind.Checklist -> typeTasks(note)
            NotesUi.Kind.Glossary -> Unit
            NotesUi.Kind.Sketch -> SketchPad.open(file, null, square = false, title = "Sketch", owner = OWNER, say = say, onDiscard = { dropIfEmpty(note) }) { png ->
                note.edit { it + Block.Picture(relPath(file, png), "") }
            }
        }
    }
}

/** A new note holding a screenshot of the game, taken now. */
private fun shotNote(book: Notebook, activity: CompanionActivity, say: (String) -> Unit) {
    val onTop = activity.gameOnTop
    NoteStore.later({
        val file = NoteStore.create(book, NotesUi.newName("Screenshot"))
        val r = NoteStore.screenshot(file, onTop)
        if (r is GameShot.Result.Failed) NoteFiles.delete(file)
        file to r
    }) { r ->
        val (file, shot) = r ?: return@later
        if (shot !is GameShot.Result.Saved) { NoteStore.changed(); say((shot as? GameShot.Result.Failed)?.why ?: "Couldn't take the screenshot"); return@later }
        val blocks = listOf(Block.Picture(relPath(file, shot.file), ""))
        NoteStore.save(file, blocks)
        CompanionNotes.note = OpenNote(file, blocks)
    }
}

private fun dropIfEmpty(note: OpenNote) {
    val f = note.file
    NoteStore.later({ f.length() == 0L && NoteFiles.delete(f) }) { gone ->
        if (gone == true) { CompanionNotes.note = null; NoteStore.changed() }
    }
}

private fun typeTasks(note: OpenNote) {
    CompanionNotes.type("New checklist item (leave it empty to stop)") { t ->
        if (t.isNotBlank()) {
            note.edit { it + Block.Task(t.trim(), false) }
            typeTasks(note)
        }
    }
}

// --- A note -------------------------------------------------------------------------------------------------------

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ColumnScope.NotePage(note: OpenNote, activity: CompanionActivity, say: (String) -> Unit) {
    val blocks = note.blocks
    var picked by remember(note) { mutableIntStateOf(-1) }
    Header(note.title, "Notes", { CompanionNotes.note = null }, onTitle = {
        CompanionNotes.type("Rename the note", note.title) { name -> note.rename(name) { } }
    })
    val state = rememberLazyListState()
    LazyColumn(
        Modifier.fillMaxWidth().weight(1f).fadingEdges(state), state = state,
        contentPadding = PaddingValues(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (blocks != null && blocks.isEmpty()) item("empty") {
            Text("Empty: add something below. Tap a line to change it; hold it to move or delete it.", Modifier.padding(8.dp), style = MaterialTheme.typography.bodyLarge, color = Palette.Muted)
        }
        itemsIndexed(blocks.orEmpty(), key = { i, _ -> i }) { i, b ->
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(if (picked == i) Palette.CardHigh else Palette.Card)
                    .then(if (b is Block.Table) Modifier else Modifier.combinedClickable(onLongClick = { picked = if (picked == i) -1 else i }) { tapBlock(note, i, say) })
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                when (b) {
                    is Block.Text -> MdText(b.text, maxLines = 30)
                    is Block.Task -> TaskLine(b.text, b.done)
                    is Block.Picture -> {
                        val file = remember(b.path, NoteStore.version) { NoteStore.picture(note.file, b.path) }
                        NotePicture(file, 1000, Modifier.fillMaxWidth().heightIn(max = 220.dp).clip(RoundedCornerShape(10.dp)))
                        if (b.caption.isNotEmpty()) Text(b.caption, style = MaterialTheme.typography.labelLarge, color = Palette.Muted, maxLines = 2)
                    }
                    is Block.Table -> Glossary(note, i, b, say, onHold = { picked = if (picked == i) -1 else i })
                }
                if (picked == i) BlockActions(note, i, b) { picked = -1 }
            }
        }
    }
    AddRow(
        "+ Text" to { CompanionNotes.type("New text", multiLine = true) { t -> if (t.isNotBlank()) note.edit { it + Block.Text(t.trimEnd()) } } },
        "+ Item" to { typeTasks(note) },
        "+ Entry" to { note.edit { NoteDoc.addRow(it, it.size - 1).first } },
        "+ Sketch" to {
            SketchPad.open(note.file, null, square = false, title = "Sketch", owner = OWNER, say = say) { png -> note.edit { it + Block.Picture(relPath(note.file, png), "") } }
        },
        "+ Screenshot" to {
            val onTop = activity.gameOnTop
            val f = note.file
            NoteStore.later({ NoteStore.screenshot(f, onTop) }) { r ->
                when (r) {
                    is GameShot.Result.Saved -> note.edit { it + Block.Picture(relPath(f, r.file), "") }
                    is GameShot.Result.Failed -> say(r.why)
                    null -> say("Couldn't take the screenshot")
                }
            }
        },
    )
}

/** A tap on a block: type its text, tick it, or draw on the picture. */
private fun tapBlock(note: OpenNote, i: Int, say: (String) -> Unit) {
    when (val b = note.blocks?.getOrNull(i)) {
        is Block.Text -> CompanionNotes.type("Edit the text", b.text, multiLine = true) { t ->
            note.edit { l -> l.toMutableList().apply { if (t.isBlank()) removeAt(i) else set(i, Block.Text(t.trimEnd())) } }
        }
        is Block.Task -> note.edit { l -> l.toMutableList().apply { set(i, b.copy(done = !b.done)) } }
        is Block.Picture -> {
            val file = NoteStore.picture(note.file, b.path) ?: run { say("That picture isn't there any more"); return }
            SketchPad.open(note.file, file, square = false, title = "Draw on it", owner = OWNER, say = say) { png ->
                if (png != file) note.edit { l -> l.toMutableList().apply { set(i, b.copy(path = relPath(note.file, png))) } }
            }
        }
        else -> Unit
    }
}

/** What a held block offers: move it, change it, delete it. */
@Composable
private fun BlockActions(note: OpenNote, i: Int, b: Block, done: () -> Unit) {
    AddRow(
        *listOfNotNull(
            ("Edit" to {
                CompanionNotes.type("Edit the item", b.text()) { t ->
                    note.edit { l -> l.toMutableList().apply { if (t.isBlank()) removeAt(i) else set(i, (b as Block.Task).copy(text = t.trim())) } }
                }
                done()
            }).takeIf { b is Block.Task },
            ("Caption" to {
                CompanionNotes.type("Caption", (b as Block.Picture).caption) { t -> note.edit { l -> l.toMutableList().apply { set(i, b.copy(caption = t.trim())) } } }
                done()
            }).takeIf { b is Block.Picture },
            "Up" to { note.edit { NoteDoc.move(it, i, -1) }; done() },
            "Down" to { note.edit { NoteDoc.move(it, i, 1) }; done() },
            "Delete" to { note.edit { it.toMutableList().apply { removeAt(i) } }; done() },
        ).toTypedArray(),
    )
}

private fun Block.text(): String = (this as? Block.Task)?.text ?: (this as? Block.Text)?.text.orEmpty()

/** A glossary: tap the symbol to draw it and a meaning to type it; hold a cell for the other. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Glossary(note: OpenNote, i: Int, t: Block.Table, say: (String) -> Unit, onHold: () -> Unit) {
    val cols = maxOf(1, t.header.size)
    fun weight(k: Int) = if (k == 0) 0.5f else 1f
    Row(Modifier.fillMaxWidth().combinedClickable(onLongClick = onHold) {}) {
        for (k in 0 until cols) GlossaryCell(note.file, t.header.getOrElse(k) { "" }, Modifier.weight(weight(k)), header = true)
    }
    t.rows.forEachIndexed { r, cells ->
        Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
            for (k in 0 until cols) {
                val cell = cells.getOrElse(k) { "" }
                val draw = { drawCell(note, i, r, k, say) }
                val type = { typeCell(note, i, r, k, t.header.getOrElse(k) { "" }) }
                Box(
                    Modifier.weight(weight(k)).heightIn(min = 52.dp).padding(end = 6.dp).clip(RoundedCornerShape(10.dp))
                        .background(Palette.NightDeep.copy(alpha = 0.5f))
                        .combinedClickable(onLongClick = if (k == 0) type else draw, onClick = if (k == 0) draw else type).padding(6.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (cell.isEmpty()) Text(if (k == 0) "Draw" else "Type", style = MaterialTheme.typography.labelLarge, color = Palette.Muted.copy(alpha = 0.6f))
                    else GlossaryCell(note.file, cell, pictureSize = 56.dp)
                }
            }
        }
    }
}

private fun setCell(note: OpenNote, i: Int, r: Int, k: Int, value: String) = note.edit { blocks ->
    val t = blocks.getOrNull(i) as? Block.Table ?: return@edit blocks
    blocks.toMutableList().apply {
        set(i, t.copy(rows = t.rows.mapIndexed { j, cells -> if (j != r) cells else List(maxOf(cells.size, k + 1)) { c -> if (c == k) value else cells.getOrElse(c) { "" } } }))
    }
}

private fun typeCell(note: OpenNote, i: Int, r: Int, k: Int, heading: String) {
    val cell = ((note.blocks?.getOrNull(i) as? Block.Table)?.rows?.getOrNull(r)?.getOrNull(k)).orEmpty()
    CompanionNotes.type(heading.ifEmpty { "Column ${k + 1}" }, if (NoteDoc.cellPicture(cell) != null) "" else cell) { v -> setCell(note, i, r, k, v.trim()) }
}

private fun drawCell(note: OpenNote, i: Int, r: Int, k: Int, say: (String) -> Unit) {
    val cell = ((note.blocks?.getOrNull(i) as? Block.Table)?.rows?.getOrNull(r)?.getOrNull(k)).orEmpty()
    val existing: File? = NoteDoc.cellPicture(cell)?.let { NoteStore.picture(note.file, it) }
    SketchPad.open(note.file, existing, square = true, title = "Draw the symbol", owner = OWNER, say = say) { png ->
        setCell(note, i, r, k, NoteDoc.pictureCell(relPath(note.file, png)))
    }
}

// --- Typing and drawing -------------------------------------------------------------------------------------------------

@Composable
private fun ColumnScope.TypingPage(t: CompanionNotes.Typing) {
    val text = remember(t) { mutableStateOf(TextFieldValue(t.initial, TextRange(t.initial.length))) }
    val done = { CompanionNotes.typing = null; t.onDone(text.value.text) }
    Header(t.title, "Cancel", { CompanionNotes.typing = null }) { PanelChip("Done", Palette.Mint, strong = true, onTap = done) }
    Column(Modifier.fillMaxWidth().weight(1f).imePadding()) {
        NoteTextField(text, t.multiLine, Modifier.fillMaxWidth().then(if (t.multiLine) Modifier.weight(1f) else Modifier), onDone = done)
    }
}

@Composable
private fun ColumnScope.SketchPage(s: SketchSession) {
    Header(s.title, "Discard", { SketchPad.discard() }) { PanelChip("Done", Palette.Mint, strong = true) { SketchPad.done() } }
    SketchTools(s.state, Modifier.fillMaxWidth())
    SketchCanvas(s.state, Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(12.dp)))
}
