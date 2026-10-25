package io.github.zahakj.tandem.ui

import io.github.zahakj.tandem.Privacy

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import io.github.zahakj.tandem.Button
import io.github.zahakj.tandem.Buttons
import io.github.zahakj.tandem.FileChecks
import io.github.zahakj.tandem.Game
import io.github.zahakj.tandem.Libraries
import io.github.zahakj.tandem.data.Candidate
import io.github.zahakj.tandem.data.Gallery
import io.github.zahakj.tandem.data.GameMeta
import io.github.zahakj.tandem.data.MediaKind
import io.github.zahakj.tandem.data.MetaField
import io.github.zahakj.tandem.data.Scraper
import io.github.zahakj.tandem.data.Store
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Popups for one game's details and artwork, plus the "clear downloaded media" confirmation.
 */
object GameEditor {
    var editing by mutableStateOf<Game?>(null)
        private set
    var artFor by mutableStateOf<Game?>(null)
        private set
    var clearing by mutableStateOf<Pair<Int, Long>?>(null)
        private set
    /** Bumped after media is cleared, so sizes shown elsewhere refresh. */
    var clearCount by mutableIntStateOf(0)

    val isOpen get() = editing != null || artFor != null || clearing != null

    // The screen underneath can't take focus while one of these is open (see Layers), so nothing needs holding.
    fun edit(g: Game) { FocusMemory.forget("edit:${g.key}"); artFor = null; editing = g }

    fun chooseArt(g: Game) {
        if (Privacy.isHidden(g)) { Toasts.show("Hidden games are never sent anywhere. Unhide it to look for art"); return }
        editing = null; artFor = g
    }

    fun confirmClear(size: Pair<Int, Long>?) { FocusMemory.forget("clearmedia"); clearing = size ?: (0 to 0L) }

    fun close() {
        if (!isOpen) return
        editing = null; artFor = null; clearing = null
        FileChecks.clear()
        Nav.focusTick++
    }
}

/** Rendered once in the app root, below the keyboard so text entry opens on top. */
@Composable
fun GameEditorOverlays() {
    EditDetailsPanel()
    ArtChooserPanel()
    ClearMediaPanel()
}

// ---------------------------------------------------------------------------------------------------------------
// Edit details

private class Field(
    val id: String,
    val label: String,
    val show: (GameMeta, Game) -> String?,
    val prompt: String,
    val initial: (GameMeta, Game) -> String,
    /** Applies typed text; null text means "cleared". Returns null when the text isn't valid. */
    val apply: (GameMeta, String?) -> GameMeta?,
)

private val FIELDS = listOf(
    Field(MetaField.TITLE, "Title", { m, g -> m.title ?: g.fileTitle }, "Title", { _, g -> g.title }, { m, v -> m.copy(title = v) }),
    Field(MetaField.YEAR, "Year", { m, _ -> m.year?.toString() }, "Release year (e.g. 1998)", { m, _ -> m.year?.toString().orEmpty() }) { m, v ->
        if (v == null) m.copy(year = null) else v.toIntOrNull()?.takeIf { it in 1950..2100 }?.let { m.copy(year = it) }
    },
    Field(MetaField.GENRES, "Genres", { m, _ -> m.genres.joinToString(", ").ifEmpty { null } }, "Genres, separated by commas", { m, _ -> m.genres.joinToString(", ") }) { m, v ->
        m.copy(genres = v?.split(',', ';')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty())
    },
    Field(MetaField.DEVELOPER, "Developer", { m, _ -> m.developer }, "Developer", { m, _ -> m.developer.orEmpty() }, { m, v -> m.copy(developer = v) }),
    Field(MetaField.PUBLISHER, "Publisher", { m, _ -> m.publisher }, "Publisher", { m, _ -> m.publisher.orEmpty() }, { m, v -> m.copy(publisher = v) }),
    Field(MetaField.PLAYERS, "Players", { m, _ -> m.players }, "Players (e.g. 1-4)", { m, _ -> m.players.orEmpty() }, { m, v -> m.copy(players = v) }),
    Field(MetaField.DESCRIPTION, "Description", { m, _ -> m.description }, "Description", { m, _ -> m.description.orEmpty() }, { m, v -> m.copy(description = v) }),
)

@Composable
private fun EditDetailsPanel() {
    val game = GameEditor.editing
    val shown = rememberLatest(game)
    var focused by remember { mutableStateOf<String?>(null) }
    // The fields and extras, or one of the extras' pickers (B goes back to the fields first).
    var page by remember(game) { mutableStateOf(EditPage.Main) }
    LaunchedEffect(game) {
        if (game == null) return@LaunchedEffect
        Buttons.events.collect { b ->
            if (b != Button.X || TextEntry.request != null || page != EditPage.Main) return@collect
            val id = focused?.takeIf { f -> FIELDS.any { it.id == f } } ?: return@collect
            val locked = id in game.meta.lockedFields
            Store.update(game.key) { it.copy(lockedFields = if (locked) it.lockedFields - id else it.lockedFields + id) }
            Toasts.show(if (locked) "Unlocked: scraping may update it" else "Locked: scraping won't touch it")
        }
    }
    OverlayPanel(game != null, onClose = { if (page != EditPage.Main) { page = EditPage.Main; Nav.focusTick++ } else GameEditor.close() }) {
        val g = shown ?: return@OverlayPanel
        val meta = g.meta
        val routeKey = "edit:${g.key}"
        val back: () -> Unit = { page = EditPage.Main; Nav.focusTick++ }
        Column(
            Modifier.width(620.dp).background(Palette.Card, RoundedCornerShape(26.dp)).padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(Modifier.padding(horizontal = 34.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(12.dp))) { GameArt(g) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    val heading = when (page) { EditPage.Main -> "Edit details"; EditPage.Apps -> "Companion app"; EditPage.Achievements -> "RetroAchievements match" }
                    Text(heading, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${g.platform.name} · ${g.file.name}", style = MaterialTheme.typography.bodySmall, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            when (page) {
                EditPage.Apps -> CompanionAppPage(g, routeKey, Modifier.weight(1f, fill = false), back)
                EditPage.Achievements -> AchievementsPage(g, routeKey, Modifier.weight(1f, fill = false), back)
                EditPage.Main -> Unit
            }
            // Scrolls inside the panel with soft ends; the panel itself never outgrows the screen.
            val fields = androidx.compose.foundation.lazy.rememberLazyListState()
            if (page == EditPage.Main) LazyColumn(
                Modifier.fillMaxWidth().weight(1f, fill = false).heightIn(max = 300.dp).fadingEdges(fields),
                state = fields,
                contentPadding = PaddingValues(horizontal = 34.dp, vertical = FocusRoom),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                items(FIELDS, key = { it.id }) { f ->
                    val value = f.show(meta, g)
                    val locked = f.id in meta.lockedFields
                    FocusCard(
                        Modifier.fillMaxWidth().remembersFocus(routeKey, f.id, f == FIELDS.first()),
                        glow = if (locked) Palette.Butter else Palette.Mint, shape = RoundedCornerShape(14.dp),
                        onFocus = { focused = f.id; FocusMemory.remember(routeKey, f.id) },
                        onClick = {
                            TextEntry.ask(f.prompt, f.initial(meta, g)) { typed ->
                                val text = typed.trim().ifEmpty { null }
                                val updated = f.apply(Store.get(g.key), text)
                                if (updated == null) {
                                    Toasts.show("That doesn't look right for ${f.label.lowercase()}")
                                } else {
                                    // Typing something locks it; clearing it hands the field back to scraping.
                                    Store.update(g.key) { cur ->
                                        (f.apply(cur, text) ?: cur).copy(lockedFields = if (text == null) cur.lockedFields - f.id else cur.lockedFields + f.id)
                                    }
                                    Toasts.show(if (text == null) "${f.label} cleared" else "${f.label} saved and locked")
                                }
                            }
                        },
                    ) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(f.label, Modifier.width(96.dp), style = MaterialTheme.typography.titleSmall)
                            Text(
                                value ?: "Not set", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
                                color = if (value == null) Palette.Muted else Palette.Text,
                                maxLines = if (f.id == MetaField.DESCRIPTION) 2 else 1, overflow = TextOverflow.Ellipsis,
                            )
                            if (locked) { Spacer(Modifier.width(8.dp)); Pill("Locked", Palette.Butter) }
                        }
                    }
                }
                if (meta.lockedFields.isNotEmpty()) item("unlock") {
                    FocusCard(
                        Modifier.fillMaxWidth().remembersFocus(routeKey, "unlock", false),
                        glow = Palette.Lavender, shape = RoundedCornerShape(14.dp),
                        onFocus = { focused = "unlock"; FocusMemory.remember(routeKey, "unlock") },
                        onClick = {
                            Store.update(g.key) { it.copy(lockedFields = emptySet()) }
                            Toasts.show("Everything unlocked")
                        },
                    ) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Unlock everything", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                            Pill("${meta.lockedFields.size} locked", Palette.Lavender)
                        }
                    }
                }
                gameExtrasItems(g, routeKey, focus = { focused = it }, open = { page = it })
            }
            Row(Modifier.padding(horizontal = 34.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                ButtonHint("A", if (page == EditPage.Main) "Edit" else "Choose", Palette.Mint)
                if (page == EditPage.Main) ButtonHint("X", "Lock / unlock", Palette.Butter)
                ButtonHint("B", if (page == EditPage.Main) "Close" else "Back", Palette.Peach)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------
// Choose artwork

private val CHOOSER_KINDS = listOf(MediaKind.Box, MediaKind.Hero, MediaKind.Logo)

private fun aspect(kind: MediaKind) = when (kind) { MediaKind.Hero -> 16f / 9f; MediaKind.Logo -> 2f; else -> 1f }

@Composable
private fun ArtChooserPanel() {
    val game = GameEditor.artFor
    val shown = rememberLatest(game)
    var kind by remember(game) { mutableStateOf(MediaKind.Box) }
    var gallery by remember(game) { mutableStateOf<Gallery?>(null) }
    var saving by remember(game) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current

    // Network only now: the user asked for art for this game.
    LaunchedEffect(game) { if (game != null) gallery = Scraper.gallery(game) }
    LaunchedEffect(game) {
        if (game == null) return@LaunchedEffect
        Buttons.events.collect { b ->
            val i = CHOOSER_KINDS.indexOf(kind)
            when (b) {
                Button.L1 -> kind = CHOOSER_KINDS[(i + CHOOSER_KINDS.size - 1) % CHOOSER_KINDS.size]
                Button.R1 -> kind = CHOOSER_KINDS[(i + 1) % CHOOSER_KINDS.size]
                else -> Unit
            }
        }
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val g = GameEditor.artFor ?: return@rememberLauncherForActivityResult
        if (uri == null) return@rememberLauncherForActivityResult
        val k = kind
        saving = true
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    ctx.contentResolver.openInputStream(uri)?.use { input ->
                        val bytes = input.readBytes()
                        bytes.size <= 40 shl 20 && Scraper.useLocal(g, k, bytes)
                    } ?: false
                }.getOrDefault(false)
            }
            saving = false
            Toasts.show(if (ok) "${k.label} updated from your picture" else "Couldn't use that picture")
        }
    }

    OverlayPanel(game != null, onClose = { GameEditor.close() }) {
        val g = shown ?: return@OverlayPanel
        val routeKey = "art:${g.key}:${kind.name}"
        val lib = Libraries.of(g)
        val current: File? = when (kind) {
            MediaKind.Box -> lib?.box(g)
            MediaKind.Hero -> lib?.hero(g)
            else -> lib?.logo(g)
        }
        val locked = kind.field in g.meta.lockedFields
        Column(Modifier.fillMaxSize().background(Palette.Card, RoundedCornerShape(26.dp)).padding(top = 14.dp)) {
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.height(52.dp).aspectRatio(aspect(kind)).clip(RoundedCornerShape(10.dp)).background(Palette.NightDeep),
                    contentAlignment = Alignment.Center,
                ) {
                    if (current != null) AsyncImage(current, null, Modifier.fillMaxSize(), contentScale = if (kind == MediaKind.Logo) ContentScale.Fit else ContentScale.Crop)
                    else Text("None", style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Choose artwork", style = MaterialTheme.typography.titleLarge)
                    Text(g.title + if (locked) " · your pick is locked" else "", style = MaterialTheme.typography.bodySmall, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (saving) Pill("Saving…", Palette.Butter, Modifier.padding(end = 10.dp))
                ButtonHint("L1", "", Palette.Lavender)
                Spacer(Modifier.width(6.dp))
                CHOOSER_KINDS.forEach { k ->
                    val on = k == kind
                    FocusCard(
                        Modifier.padding(horizontal = 4.dp).height(34.dp), glow = Palette.Lavender, shape = RoundedCornerShape(17.dp),
                        onClick = { kind = k },
                    ) {
                        Box(Modifier.fillMaxSize().background(if (on) Palette.Lavender else Palette.CardHigh).padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                            Text(k.label.substringBefore(" /"), style = MaterialTheme.typography.titleSmall, color = if (on) Palette.NightDeep else Palette.Text)
                        }
                    }
                }
                Spacer(Modifier.width(6.dp))
                ButtonHint("R1", "", Palette.Lavender)
            }
            val gal = gallery
            val candidates = gal?.byKind?.get(kind).orEmpty()
            val minSize = when (kind) { MediaKind.Hero -> 200.dp; MediaKind.Logo -> 170.dp; else -> 112.dp }
            val tiles = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
            LazyVerticalGrid(
                GridCells.Adaptive(minSize),
                Modifier.weight(1f).fillMaxWidth().fadingEdges(tiles),
                state = tiles,
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = FocusRoom),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item(key = "device:${kind.name}") {
                    ActionTile("＋", "From this device", kind, Palette.Mint, Modifier.remembersFocus(routeKey, "device", true)) {
                        if (!saving) pick.launch("image/*")
                    }
                }
                if (locked) item(key = "auto:${kind.name}") {
                    ActionTile("↺", "Back to automatic", kind, Palette.Lavender, Modifier) {
                        Scraper.resetArt(g, kind)
                        Toasts.show("${kind.label} is automatic again")
                    }
                }
                items(candidates, key = { "${kind.name}:${it.url}" }) { c ->
                    CandidateTile(c) {
                        if (saving) return@CandidateTile
                        saving = true
                        scope.launch {
                            val ok = Scraper.use(g, c)
                            saving = false
                            Toasts.show(if (ok) "${c.kind.label} updated ✨" else "Couldn't download that one, try another")
                        }
                    }
                }
            }
            // Loading, empty and error states sit under the grid's action tiles.
            Box(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp)) {
                when {
                    gal == null -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Pip(30.dp, mood = 1)
                        Spacer(Modifier.width(8.dp))
                        Text("Looking through every source…", style = MaterialTheme.typography.bodyMedium, color = Palette.Muted)
                    }
                    candidates.isEmpty() -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Pip(30.dp, mood = 2)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            (gal.notes.firstOrNull()?.let { "$it. " } ?: "") + "Nothing found online for this one. Adding a ScreenScraper or SteamGridDB account in Settings finds more.",
                            style = MaterialTheme.typography.bodyMedium, color = Palette.Muted, maxLines = 2,
                        )
                    }
                    else -> Text(
                        "${candidates.size} found" + (gal.notes.takeIf { it.isNotEmpty() }?.joinToString(prefix = " · ") ?: "") + " · your pick is locked so scraping keeps it",
                        style = MaterialTheme.typography.bodySmall, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionTile(symbol: String, label: String, kind: MediaKind, color: androidx.compose.ui.graphics.Color, modifier: Modifier, onClick: () -> Unit) {
    FocusCard(modifier.aspectRatio(aspect(kind)), glow = color, shape = RoundedCornerShape(16.dp), onClick = onClick) {
        Column(
            Modifier.fillMaxSize().background(Brush.linearGradient(listOf(color.copy(alpha = 0.35f), Palette.CardHigh))).padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
        ) {
            Text(symbol, style = MaterialTheme.typography.headlineSmall, color = color)
            Text(label, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center, maxLines = 2)
        }
    }
}

@Composable
private fun CandidateTile(c: Candidate, onClick: () -> Unit) {
    FocusCard(Modifier.aspectRatio(aspect(c.kind)), glow = Palette.Sky, shape = RoundedCornerShape(16.dp), onClick = onClick) {
        Box(Modifier.fillMaxSize().background(Palette.NightDeep)) {
            AsyncImage(
                c.thumb, null, Modifier.fillMaxSize().padding(if (c.kind == MediaKind.Logo) 8.dp else 0.dp),
                contentScale = if (c.kind == MediaKind.Box || c.kind == MediaKind.Hero) ContentScale.Crop else ContentScale.Fit,
            )
            Text(
                listOf(c.source, c.note).filter { it.isNotBlank() }.joinToString(" · "),
                Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(horizontal = 7.dp, vertical = 4.dp),
                // A text shadow keeps it readable on any picture, without a dark band across the art.
                style = MaterialTheme.typography.labelSmall.copy(
                    shadow = androidx.compose.ui.graphics.Shadow(androidx.compose.ui.graphics.Color.Black, androidx.compose.ui.geometry.Offset(0f, 1.5f), 6f),
                ),
                color = androidx.compose.ui.graphics.Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------
// Clear downloaded media

@Composable
private fun ClearMediaPanel() {
    val info = GameEditor.clearing
    val shown = rememberLatest(info)
    var busy by remember(info) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    OverlayPanel(info != null, onClose = { if (!busy) GameEditor.close() }) {
        val (n, bytes) = shown ?: return@OverlayPanel
        Column(
            Modifier.width(470.dp).background(Palette.Card, RoundedCornerShape(26.dp)).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Pip(64.dp, mood = 2)
            Text("Clear downloaded media?", style = MaterialTheme.typography.titleLarge)
            Text(
                "This deletes the $n pictures and clips Tandem downloaded (${"%.1f".format(bytes / 1048576.0)} MB). " +
                    "Art you picked by hand and art from other apps stays.",
                style = MaterialTheme.typography.bodyMedium, color = Palette.Muted, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            Row(Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                PillButton("Keep them", Palette.Mint, Modifier.remembersFocus("clearmedia", "keep", true)) { if (!busy) GameEditor.close() }
                HoldButton(if (busy) "Clearing…" else "Clear", Palette.Peach, Modifier.remembersFocus("clearmedia", "clear", false), hint = holdHint("clear them")) {
                    if (busy) return@HoldButton
                    busy = true
                    scope.launch {
                        val (count, freed) = withContext(Dispatchers.IO) { Scraper.clearDownloaded() }
                        busy = false
                        GameEditor.clearCount++
                        GameEditor.close()
                        Toasts.show("Cleared $count files, ${"%.1f".format(freed / 1048576.0)} MB freed")
                    }
                }
            }
        }
    }
}
