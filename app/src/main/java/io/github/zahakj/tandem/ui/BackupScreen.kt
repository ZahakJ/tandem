package io.github.zahakj.tandem.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
 * Settings › Data & about › Back up and Restore (data/Backup.kt does the file work).
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
        if (cats.isEmpty()) { Sounds.error(); Toasts.show("Pick at least one thing to back up"); return }
        val keys = withKeys
        val sources = Backups.capture(ctx)
        close()
        DataJobs.run("backup", "Backing up…", { Backups.backUp(ctx, sources, cats, keys) }) { s ->
            Sounds.confirm()
            "Backed up to ROMs/tandem/backup/${s.dir.name}"
        }
    }

    fun restore(ctx: Context) {
        val s = picked ?: return
        val cats = restoring.intersect(s.manifest.holds)
        if (cats.isEmpty()) { Sounds.error(); Toasts.show("Pick at least one thing to restore"); return }
        Confirm.hold(
            "Restore ${cats.joinToString(", ") { it.label }}?",
            "What Tandem has now for these is replaced. An automatic backup of how things are now is saved first.",
            "Restore",
        ) {
            close()
            val sources = Backups.capture(ctx)
            DataJobs.run("restore", "Restoring…", {
                Backups.autoNow(ctx, sources, "restoring")
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
        Confirm.hold("Delete this backup?", "${title(s)} · ${BackupFiles.summary(s.manifest)}. This can't be undone.", "Delete") {
            MainScope().launch {
                val ok = withContext(Dispatchers.IO) { runCatching { BackupFiles.delete(s) }.getOrDefault(false) }
                if (highlighted === s) highlighted = null
                Toasts.show(if (ok) "Backup deleted" else "Couldn't delete that backup")
                refresh()
                Nav.focusTick++
            }
        }
    }

    fun title(s: BackupSnapshot): String = if (s.legacy) "Older backup" else BackupFiles.date(s.manifest)

    fun kind(s: BackupSnapshot): String = when {
        s.legacy -> "Older"
        s.manifest.automatic -> "Automatic"
        else -> "Manual"
    }

    /** "Before Quick look · 1,204 games · 38 tiles · 2 days ago". */
    fun line(s: BackupSnapshot): String =
        listOfNotNull(s.manifest.reason?.let { "Before $it" }, BackupFiles.summary(s.manifest)).joinToString(" · ")

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
        Panel(room, "Back up", "A new backup in ROMs/tandem/backup on this device. Older ones stay.", rows = {
            BackupCategory.entries.forEach { c ->
                val on = c in chosen
                OptionRow(c.label, c.blurb, if (on) "On" else "Off", on, Palette.Sky, Modifier.takesFocus(MAKE, c.id, target == c.id)) {
                    chosen = if (on) chosen - c else chosen + c
                    Sounds.toggle(!on)
                }
            }
            OptionRow(
                "Account keys and logins", "Scraping and RetroAchievements. The backup folder is readable by other apps.",
                if (withKeys) "Included" else "Left out", withKeys, Palette.Peach, Modifier.takesFocus(MAKE, "keys", target == "keys"),
            ) {
                withKeys = !withKeys
                Sounds.toggle(withKeys)
            }
        }) {
            PillButton("Cancel", Palette.Mint, Modifier.takesFocus(MAKE, "no", target == "no")) { close() }
            PillButton(
                "Back up", Palette.Peach,
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
            Header("Restore", "Pick a backup, then what to put back.")
            when {
                list == null -> Text(
                    "Looking in ROMs/tandem/backup…", Modifier.padding(TextInset), style = MaterialTheme.typography.bodyMedium,
                    color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                list.isEmpty() -> {
                    Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                        PipNote("No backups yet", "Back up saves one in ROMs/tandem/backup.", mood = 1)
                    }
                    Row(Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp)) {
                        PillButton("Back up now", Palette.Sky, Modifier.takesFocus(LIST, "make", target == "make")) { openBackUp() }
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
                        ButtonHint("A", "Choose", Palette.Mint)
                        ButtonHint("X", "Delete", Palette.Peach)
                        ButtonHint("B", "Close", Palette.Lavender)
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
            BackupCategory.entries.forEach { c ->
                if (c in holds) {
                    val on = c in restoring
                    OptionRow(c.label, c.blurb, if (on) "Restore" else "Keep", on, Palette.Mint, Modifier.takesFocus(PICK, c.id, target == c.id)) {
                        restoring = if (on) restoring - c else restoring + c
                        Sounds.toggle(!on)
                    }
                } else {
                    // Not in this backup: shown faded, so the four categories always read the same.
                    OptionText(c.label, "Not in this backup", Modifier.graphicsLayer { alpha = 0.35f })
                }
            }
            Text(
                if (s.manifest.keys) "Includes account keys." else "Account keys on this device stay as they are.",
                Modifier.fillMaxWidth().padding(horizontal = TextInset / 2),
                style = MaterialTheme.typography.bodySmall, color = Palette.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }) {
            PillButton("Back", Palette.Mint, Modifier.takesFocus(PICK, "no", target == "no")) { back() }
            PillButton(
                "Restore", Palette.Peach,
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

    /** A switch-like row: a label, a line about it, and its state in a pill. */
    @Composable
    private fun OptionRow(label: String, detail: String, value: String, on: Boolean, color: Color, modifier: Modifier, onClick: () -> Unit) {
        FocusCard(modifier.fillMaxWidth(), glow = color, shape = RoundedCornerShape(14.dp), onClick = onClick) {
            OptionContent(label, detail) { Pill(value, if (on) color else Palette.Muted) }
        }
    }

    @Composable
    private fun OptionText(label: String, detail: String, modifier: Modifier) {
        androidx.compose.foundation.layout.Box(modifier.fillMaxWidth()) { OptionContent(label, detail) {} }
    }

    @Composable
    private fun OptionContent(label: String, detail: String, end: @Composable () -> Unit) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(8.dp))
            end()
        }
    }
}
