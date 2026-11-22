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
import io.github.zahakj.tandem.Lang
import io.github.zahakj.tandem.Libraries
import io.github.zahakj.tandem.R
import io.github.zahakj.tandem.Tx
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
        if (Privacy.isHidden(g)) { Toasts.show(Tx.s(R.string.library_edit_hidden_art)); return }
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

/** A field to edit. Its words are resource ids, read when shown (FIELDS lives as long as the app). */
private class Field(
    val id: String,
    @androidx.annotation.StringRes private val labelRes: Int,
    val show: (GameMeta, Game) -> String?,
    @androidx.annotation.StringRes private val promptRes: Int,
    val initial: (GameMeta, Game) -> String,
    /** Applies typed text; null text means "cleared". Returns null when the text isn't valid. */
    val apply: (GameMeta, String?) -> GameMeta?,
) {
    val label: String get() = Tx.s(labelRes)
    val prompt: String get() = Tx.s(promptRes)
}

private val FIELDS = listOf(
    Field(MetaField.TITLE, R.string.library_edit_title_field, { m, g -> m.title ?: g.fileTitle }, R.string.library_edit_title_field, { _, g -> g.title }, { m, v -> m.copy(title = v) }),
    Field(MetaField.YEAR, R.string.library_edit_year, { m, _ -> m.year?.toString() }, R.string.library_edit_year_ask, { m, _ -> m.year?.toString().orEmpty() }) { m, v ->
        if (v == null) m.copy(year = null) else v.toIntOrNull()?.takeIf { it in 1950..2100 }?.let { m.copy(year = it) }
    },
    Field(MetaField.GENRES, R.string.library_edit_genres, { m, _ -> m.genres.joinToString(", ").ifEmpty { null } }, R.string.library_edit_genres_ask, { m, _ -> m.genres.joinToString(", ") }) { m, v ->
        m.copy(genres = v?.split(',', ';')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty())
    },
    Field(MetaField.DEVELOPER, R.string.library_edit_developer, { m, _ -> m.developer }, R.string.library_edit_developer, { m, _ -> m.developer.orEmpty() }, { m, v -> m.copy(developer = v) }),
    Field(MetaField.PUBLISHER, R.string.library_edit_publisher, { m, _ -> m.publisher }, R.string.library_edit_publisher, { m, _ -> m.publisher.orEmpty() }, { m, v -> m.copy(publisher = v) }),
    Field(MetaField.PLAYERS, R.string.library_edit_players, { m, _ -> m.players }, R.string.library_edit_players_ask, { m, _ -> m.players.orEmpty() }, { m, v -> m.copy(players = v) }),
    Field(MetaField.DESCRIPTION, R.string.library_edit_description, { m, _ -> m.description }, R.string.library_edit_description, { m, _ -> m.description.orEmpty() }, { m, v -> m.copy(description = v) }),
)

/** A kind of art on the chooser's tabs, short ("Hero", not "Hero / fanart"). */
private fun chooserLabel(k: MediaKind): String = if (k == MediaKind.Hero) Tx.s(R.string.scrape_tile_hero) else k.label

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
            Toasts.show(Tx.s(if (locked) R.string.library_edit_unlocked else R.string.library_edit_locked))
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
                    val heading = Tx.s(when (page) { EditPage.Main -> R.string.library_edit_heading; EditPage.Apps -> R.string.library_edit_heading_app; EditPage.Achievements -> R.string.library_edit_heading_ra })
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
                                    Toasts.show(Tx.s(R.string.library_edit_invalid, f.label.lowercase(Lang.locale)))
                                } else {
                                    // Typing something locks it; clearing it hands the field back to scraping.
                                    Store.update(g.key) { cur ->
                                        (f.apply(cur, text) ?: cur).copy(lockedFields = if (text == null) cur.lockedFields - f.id else cur.lockedFields + f.id)
                                    }
                                    Toasts.show(Tx.s(if (text == null) R.string.library_edit_cleared else R.string.library_edit_saved, f.label))
                                }
                            }
                        },
                    ) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(f.label, Modifier.width(96.dp), style = MaterialTheme.typography.titleSmall)
                            Text(
                                value ?: Tx.s(R.string.library_edit_not_set), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
                                color = if (value == null) Palette.Muted else Palette.Text,
                                maxLines = if (f.id == MetaField.DESCRIPTION) 2 else 1, overflow = TextOverflow.Ellipsis,
                            )
                            if (locked) { Spacer(Modifier.width(8.dp)); Pill(Tx.s(R.string.library_edit_locked_pill), Palette.Butter) }
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
                            Toasts.show(Tx.s(R.string.library_edit_everything_unlocked))
                        },
                    ) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(Tx.s(R.string.library_edit_unlock_all), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                            Pill(Tx.p(R.plurals.library_edit_locked_count, meta.lockedFields.size), Palette.Lavender)
                        }
                    }
                }
                gameExtrasItems(g, routeKey, focus = { focused = it }, open = { page = it })
            }
            Row(Modifier.padding(horizontal = 34.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                ButtonHint("A", Tx.s(if (page == EditPage.Main) R.string.library_edit_hint_edit else R.string.common_choose), Palette.Mint)
                if (page == EditPage.Main) ButtonHint("X", Tx.s(R.string.library_edit_hint_lock), Palette.Butter)
                ButtonHint("B", Tx.s(if (page == EditPage.Main) R.string.common_close else R.string.common_back), Palette.Peach)
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
            Toasts.show(if (ok) Tx.s(R.string.library_art_from_picture, k.label) else Tx.s(R.string.library_art_bad_picture))
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
                    else Text(Tx.s(R.string.library_art_none), style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(Tx.s(R.string.library_art_title), style = MaterialTheme.typography.titleLarge)
                    Text(if (locked) Tx.s(R.string.library_art_locked, g.title) else g.title, style = MaterialTheme.typography.bodySmall, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (saving) Pill(Tx.s(R.string.library_art_saving), Palette.Butter, Modifier.padding(end = 10.dp))
                ButtonHint("L1", "", Palette.Lavender)
                Spacer(Modifier.width(6.dp))
                CHOOSER_KINDS.forEach { k ->
                    val on = k == kind
                    FocusCard(
                        Modifier.padding(horizontal = 4.dp).height(34.dp), glow = Palette.Lavender, shape = RoundedCornerShape(17.dp),
                        onClick = { kind = k },
                    ) {
                        Box(Modifier.fillMaxSize().background(if (on) Palette.Lavender else Palette.CardHigh).padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                            Text(chooserLabel(k), style = MaterialTheme.typography.titleSmall, color = if (on) Palette.NightDeep else Palette.Text)
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
                    ActionTile("＋", Tx.s(R.string.library_art_from_device), kind, Palette.Mint, Modifier.remembersFocus(routeKey, "device", true)) {
                        if (!saving) pick.launch("image/*")
                    }
                }
                if (locked) item(key = "auto:${kind.name}") {
                    ActionTile("↺", Tx.s(R.string.library_art_automatic), kind, Palette.Lavender, Modifier) {
                        Scraper.resetArt(g, kind)
                        Toasts.show(Tx.s(R.string.library_art_automatic_again, kind.label))
                    }
                }
                items(candidates, key = { "${kind.name}:${it.url}" }) { c ->
                    CandidateTile(c) {
                        if (saving) return@CandidateTile
                        saving = true
                        scope.launch {
                            val ok = Scraper.use(g, c)
                            saving = false
                            Toasts.show(if (ok) Tx.s(R.string.library_art_updated, c.kind.label) else Tx.s(R.string.library_art_download_failed))
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
                        Text(Tx.s(R.string.library_art_looking), style = MaterialTheme.typography.bodyMedium, color = Palette.Muted)
                    }
                    candidates.isEmpty() -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Pip(30.dp, mood = 2)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            gal.notes.firstOrNull()?.let { Tx.s(R.string.library_art_nothing_note, it) } ?: Tx.s(R.string.library_art_nothing),
                            style = MaterialTheme.typography.bodyMedium, color = Palette.Muted, maxLines = 2,
                        )
                    }
                    else -> Text(
                        Tx.s(R.string.library_art_found_line, Tx.p(R.plurals.library_art_found, candidates.size) + (gal.notes.takeIf { it.isNotEmpty() }?.joinToString(prefix = " · ") ?: "")),
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
                listOf(c.source, c.note).filter { it.isNotBlank() }.joinParts(),
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
            Text(Tx.s(R.string.library_clear_title), style = MaterialTheme.typography.titleLarge)
            Text(
                Tx.p(R.plurals.library_clear_text, n, n, String.format(Lang.locale, "%.1f", bytes / 1048576.0)),
                style = MaterialTheme.typography.bodyMedium, color = Palette.Muted, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            Row(Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                PillButton(Tx.s(R.string.library_clear_keep), Palette.Mint, Modifier.remembersFocus("clearmedia", "keep", true)) { if (!busy) GameEditor.close() }
                HoldButton(Tx.s(if (busy) R.string.library_clear_clearing else R.string.library_clear_clear), Palette.Peach, Modifier.remembersFocus("clearmedia", "clear", false), hint = holdHint(Tx.s(R.string.library_clear_hold))) {
                    if (busy) return@HoldButton
                    busy = true
                    scope.launch {
                        val (count, freed) = withContext(Dispatchers.IO) { Scraper.clearDownloaded() }
                        busy = false
                        GameEditor.clearCount++
                        GameEditor.close()
                        Toasts.show(Tx.p(R.plurals.library_clear_done, count, count, String.format(Lang.locale, "%.1f", freed / 1048576.0)))
                    }
                }
            }
        }
    }
}
