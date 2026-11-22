package io.github.zahakj.tandem.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.focus.focusProperties
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.zahakj.tandem.Button
import io.github.zahakj.tandem.FileChecks
import io.github.zahakj.tandem.MainActivity
import io.github.zahakj.tandem.R
import io.github.zahakj.tandem.Tx
import io.github.zahakj.tandem.data.BackupCategory
import io.github.zahakj.tandem.data.BackupFiles
import io.github.zahakj.tandem.data.BackupSnapshot
import io.github.zahakj.tandem.data.Backups
import io.github.zahakj.tandem.data.RestoreResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/*
 * Settings › Play & system › Your data › Back up and Restore (data/Backup.kt does the file work).
 *
 * Back up: pick categories (all on), whether account keys go too (off), then Back up; a new snapshot is added to
 * ROMs/tandem/backup and older ones stay. Restore: the snapshots, newest first (X deletes one), then the categories
 * to put back, then a confirmation. Tandem saves an automatic snapshot of how things are now before restoring.
 */

object BackupUi {
    enum class Page { BackUp, Snapshots, Categories }

    private const val MAKE = "backup:make"
    private const val LIST = "backup:list"
    private const val PICK = "backup:pick"

    var page by mutableStateOf<Page?>(null)
        private set
    /** Categories for the next backup. */
    var chosen by mutableStateOf(BackupCategory.entries.toSet())
    /** Account keys and logins go into the next backup. */
    var withKeys by mutableStateOf(false)
    /** Null while the folder is being read. */
    var snapshots by mutableStateOf<List<BackupSnapshot>?>(null)
        private set
    var picked by mutableStateOf<BackupSnapshot?>(null)
        private set
    var restoring by mutableStateOf<Set<BackupCategory>>(emptySet())
    /** The snapshot highlighted in the list, for X. */
    var highlighted: BackupSnapshot? = null

    fun openBackUp() {
        FocusMemory.forget(MAKE)
        page = Page.BackUp
    }

    fun openRestore() {
        FocusMemory.forget(LIST)
        snapshots = null
        page = Page.Snapshots
        refresh()
    }

    fun refresh() {
        MainScope().launch { snapshots = withContext(Dispatchers.IO) { Backups.list() } }
    }

    fun pick(s: BackupSnapshot) {
        picked = s
        restoring = s.manifest.holds
        FocusMemory.forget(PICK)
        page = Page.Categories
    }

    /** B: from the categories back to the list, else closed. */
    fun back() {
        if (page == Page.Categories) { page = Page.Snapshots; Nav.focusTick++ } else close()
    }

    fun close() { page = null; Nav.focusTick++ }

    fun backUp(ctx: Context) {
        val cats = chosen
        if (cats.isEmpty()) { Sounds.error(); Toasts.show(Tx.s(R.string.data_backup_pick_one)); return }
        val keys = withKeys
        val sources = Backups.capture(ctx)
        close()
        DataJobs.run("backup", Tx.s(R.string.data_backup_backing_up), { Backups.backUp(ctx, sources, cats, keys) }) { s ->
            Sounds.confirm()
            Tx.s(R.string.data_backup_backed_up, s.dir.name)
        }
    }

    fun restore(ctx: Context) {
        val s = picked ?: return
        val cats = restoring.intersect(s.manifest.holds)
        if (cats.isEmpty()) { Sounds.error(); Toasts.show(Tx.s(R.string.data_backup_pick_restore)); return }
        Confirm.hold(
            Tx.s(R.string.data_backup_restore_ask, Tx.list(cats) { it.label }),
            Tx.s(R.string.data_backup_restore_text),
            Tx.s(R.string.data_backup_restore),
        ) {
            close()
            val sources = Backups.capture(ctx)
            DataJobs.run("restore", Tx.s(R.string.data_backup_restoring), {
                Backups.autoNow(ctx, sources, Tx.s(R.string.data_backup_reason_restoring))
                Backups.restore(ctx, s, cats)
            }) { r -> restored(ctx, r); r.message() }
        }
    }

    /** Screens follow the restored stores by themselves; the screen roles, music and focus are nudged here. */
    private fun restored(ctx: Context, r: RestoreResult) {
        if (r.failed.isEmpty()) Sounds.confirm() else Sounds.error()
        if (r.restored.isEmpty()) return
        FileChecks.clear()
        (ctx as? MainActivity)?.applyScreenRoles()
        Music.refresh()
        Nav.focusTick++
    }

    fun askDelete(s: BackupSnapshot) {
        Confirm.hold(Tx.s(R.string.data_backup_delete_ask), Tx.s(R.string.data_backup_delete_text, title(s), BackupFiles.summary(s.manifest)), Tx.s(R.string.data_backup_delete)) {
            MainScope().launch {
                val ok = withContext(Dispatchers.IO) { runCatching { BackupFiles.delete(s) }.getOrDefault(false) }
                if (highlighted === s) highlighted = null
                Toasts.show(Tx.s(if (ok) R.string.data_backup_deleted else R.string.data_backup_delete_failed))
                refresh()
                Nav.focusTick++
            }
        }
    }

    fun title(s: BackupSnapshot): String = if (s.legacy) Tx.s(R.string.data_backup_older_title) else BackupFiles.date(s.manifest)

    fun kind(s: BackupSnapshot): String = when {
        s.legacy -> Tx.s(R.string.data_backup_older)
        s.manifest.automatic -> Tx.s(R.string.data_backup_automatic)
        else -> Tx.s(R.string.data_backup_manual)
    }

    /** "Before Quick look · 1,204 games · 38 tiles · 2 days ago". */
    fun line(s: BackupSnapshot): String =
        listOfNotNull(s.manifest.reason?.let { Tx.s(R.string.data_backup_before, it) }, BackupFiles.summary(s.manifest)).joinParts()

    @Composable
    fun Host() {
        val ctx = LocalContext.current
        val p = page
        InterceptButtons(p != null && Confirm.request == null) { b ->
            if (b == Button.X && page == Page.Snapshots) highlighted?.let { askDelete(it) }
            true
        }
        // Keeps showing the last page while the popup animates away.
        val shown = rememberLatest(p)
        OverlayPanel(p != null, onClose = { back() }) {
            BoxWithConstraints(contentAlignment = Alignment.Center) {
                val room = maxHeight
                when (shown ?: return@BoxWithConstraints) {
                    Page.BackUp -> BackUpPanel(ctx, room)
                    Page.Snapshots -> SnapshotsPanel(room)
                    Page.Categories -> picked?.let { CategoriesPanel(ctx, it, room) }
                }
            }
        }
    }

    @Composable
    private fun BackUpPanel(ctx: Context, room: Dp) {
        val keys = listOf("yes") + BackupCategory.entries.map { it.id } + listOf("keys", "no")
        val target = rememberFocusTarget(MAKE, keys)
        Panel(room, Tx.s(R.string.data_backup_back_up), Tx.s(R.string.data_backup_back_up_detail), rows = {
            CategoryGrid { c ->
                val on = c in chosen
                TickCard(c.label, c.blurb, on, glyphOf(c), Palette.Sky, Modifier.weight(1f).height(CardHeight).takesFocus(MAKE, c.id, target == c.id)) {
                    chosen = if (on) chosen - c else chosen + c
                    Sounds.toggle(!on)
                }
            }
            SwitchCard(
                Tx.s(R.string.data_backup_keys), Tx.s(R.string.data_backup_keys_detail),
                withKeys, Palette.Peach, Modifier.takesFocus(MAKE, "keys", target == "keys"), glyph = Glyph.Key,
            ) {
                withKeys = !withKeys
                Sounds.toggle(withKeys)
            }
        }) {
            PillButton(Tx.s(R.string.data_backup_cancel), Palette.Mint, Modifier.takesFocus(MAKE, "no", target == "no")) { close() }
            PillButton(
                Tx.s(R.string.data_backup_back_up), Palette.Peach,
                Modifier.takesFocus(MAKE, "yes", target == "yes").graphicsLayer { alpha = if (chosen.isEmpty()) 0.35f else 1f },
            ) { backUp(ctx) }
        }
    }

    @Composable
    private fun SnapshotsPanel(room: Dp) {
        val list = snapshots
        val keys = list.orEmpty().map { key(it) }.ifEmpty { listOf("make") }
        val target = rememberFocusTarget(LIST, keys)
        PanelSurface(Modifier.width(620.dp).heightIn(max = room)) {
            Header(Tx.s(R.string.data_backup_restore), Tx.s(R.string.data_backup_restore_detail))
            when {
                list == null -> Text(
                    Tx.s(R.string.data_backup_looking), Modifier.padding(TextInset), style = MaterialTheme.typography.bodyMedium,
                    color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                list.isEmpty() -> {
                    Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                        PipNote(Tx.s(R.string.data_backup_none), Tx.s(R.string.data_backup_none_detail), mood = 1)
                    }
                    Row(Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp)) {
                        PillButton(Tx.s(R.string.data_backup_now), Palette.Sky, Modifier.takesFocus(LIST, "make", target == "make")) { openBackUp() }
                    }
                }
                else -> {
                    val state = rememberLazyListState()
                    LazyColumn(
                        Modifier.weight(1f, fill = false).fadingEdges(state),
                        state = state,
                        contentPadding = PaddingValues(horizontal = FocusRoomSide, vertical = FocusRoom),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(list, key = { key(it) }) { s ->
                            SnapshotCard(s, Modifier.takesFocus(LIST, key(s), target == key(s)))
                        }
                    }
                    KeepInView(state, keys.indexOf(target))
                    Row(Modifier.padding(horizontal = TextInset, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        ButtonHint("A", Tx.s(R.string.common_choose), Palette.Mint)
                        ButtonHint("X", Tx.s(R.string.data_backup_delete), Palette.Peach)
                        ButtonHint("B", Tx.s(R.string.common_close), Palette.Lavender)
                    }
                }
            }
        }
    }

    private fun key(s: BackupSnapshot) = "s:" + s.id

    @Composable
    private fun SnapshotCard(s: BackupSnapshot, modifier: Modifier) {
        val color = if (s.manifest.automatic) Palette.Lavender else Palette.Sky
        FocusCard(modifier.fillMaxWidth(), glow = color, shape = RoundedCornerShape(14.dp), onFocus = { highlighted = s }, onClick = { pick(s) }) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title(s), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.width(8.dp))
                    Pill(kind(s), color)
                }
                Text(line(s), style = MaterialTheme.typography.bodySmall, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }

    @Composable
    private fun CategoriesPanel(ctx: Context, s: BackupSnapshot, room: Dp) {
        val holds = s.manifest.holds
        val keys = BackupCategory.entries.filter { it in holds }.map { it.id } + listOf("yes", "no")
        val target = rememberFocusTarget(PICK, keys)
        val none = restoring.intersect(holds).isEmpty()
        Panel(room, title(s), line(s), rows = {
            // Ticked: put back from the backup. Not ticked: what is on the device now stays.
            CategoryGrid { c ->
                if (c in holds) {
                    val on = c in restoring
                    TickCard(
                        c.label, Tx.s(if (on) R.string.data_backup_put_back else R.string.data_backup_keep_here), on, glyphOf(c), Palette.Mint,
                        Modifier.weight(1f).height(CardHeight).takesFocus(PICK, c.id, target == c.id),
                    ) {
                        restoring = if (on) restoring - c else restoring + c
                        Sounds.toggle(!on)
                    }
                } else {
                    // Not in this backup: shown faded, so the four categories always read the same.
                    Box(Modifier.weight(1f).height(CardHeight).graphicsLayer { alpha = 0.35f }) {
                        TickCard(c.label, Tx.s(R.string.data_backup_not_in), false, glyphOf(c), Palette.Muted, Modifier.fillMaxSize().focusProperties { canFocus = false }) {}
                    }
                }
            }
            Text(
                Tx.s(if (s.manifest.keys) R.string.data_backup_has_keys else R.string.data_backup_no_keys),
                Modifier.fillMaxWidth().padding(horizontal = TextInset / 2),
                style = MaterialTheme.typography.bodySmall, color = Palette.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }) {
            PillButton(Tx.s(R.string.common_back), Palette.Mint, Modifier.takesFocus(PICK, "no", target == "no")) { back() }
            PillButton(
                Tx.s(R.string.data_backup_restore), Palette.Peach,
                Modifier.takesFocus(PICK, "yes", target == "yes").graphicsLayer { alpha = if (none) 0.35f else 1f },
            ) { restore(ctx) }
        }
    }

    // ---- Pieces ----

    /** The panel for a page with a header and a list of rows that scrolls when the screen is short. */
    @Composable
    private fun Panel(room: Dp, title: String, detail: String, rows: @Composable ColumnScope.() -> Unit, buttons: @Composable () -> Unit) {
        PanelSurface(Modifier.width(600.dp).heightIn(max = room)) {
            Header(title, detail)
            val scroll = rememberScrollState()
            Column(
                Modifier.weight(1f, fill = false).fadingEdges(scroll).verticalScroll(scroll).padding(vertical = FocusRoom / 2),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                content = rows,
            )
            Row(Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) { buttons() }
        }
    }

    @Composable
    private fun Header(title: String, detail: String) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Pip(44.dp, mood = 1)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = Palette.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.height(6.dp))
    }

    private val CardHeight = 72.dp

    private fun glyphOf(c: BackupCategory): Glyph = when (c) {
        BackupCategory.Games -> Glyph.Gamepad
        BackupCategory.Look -> Glyph.Theme
        BackupCategory.Layout -> Glyph.Grid
        BackupCategory.Systems -> Glyph.Cartridge
    }

    /** The four parts of a backup as cards, two to a line, so they read the same on every page. */
    @Composable
    private fun CategoryGrid(card: @Composable RowScope.(BackupCategory) -> Unit) {
        BackupCategory.entries.chunked(2).forEach { line ->
            Row(Modifier.fillMaxWidth().focusRow(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                line.forEach { card(it) }
                if (line.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}
