package io.github.zahakj.tandem.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import io.github.zahakj.tandem.R
import io.github.zahakj.tandem.Selected
import io.github.zahakj.tandem.Selection
import io.github.zahakj.tandem.Tx
import io.github.zahakj.tandem.audio.SoundPack
import io.github.zahakj.tandem.data.GalleryEntry
import io.github.zahakj.tandem.data.GalleryIndex
import io.github.zahakj.tandem.data.GalleryUrls
import io.github.zahakj.tandem.data.Store
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/*
 * Share my look (Settings › Look & feel › Share my look) and Get a look (… › Get a look, with the community gallery).
 * The work is in LookShare.kt; the file format in data/LookFile.kt.
 */

// ---- Share my look ----

@Composable
fun ShareLookScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val route = Route.ShareLook.key
    var draft by remember { mutableStateOf(LookShare.draft(ctx)) }
    var busy by remember { mutableStateOf(false) }
    val look = Store.settings.look
    val spec = Themes.target
    val galleryId by produceState<String?>(null, look.theme) { value = withContext(Dispatchers.IO) { LookShare.galleryIdInUse() } }
    val name = draft.name.ifBlank { spec.name }
    // The QR code goes when the page does.
    DisposableEffect(Unit) { onDispose { LookQr.link = null } }

    /** Builds the file off the main thread, then hands it to [then]. */
    fun exported(then: (LookShare.Exported) -> Unit) {
        if (busy) return
        busy = true
        Toasts.show(Tx.s(R.string.share_making))
        scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { LookShare.export(ctx, draft) } }
            busy = false
            result.onSuccess { e ->
                then(e)
                if (e.videoLeftOut) Toasts.show(Tx.s(R.string.share_video_left_out), 4500)
                else if (e.heroesLeftOut) Toasts.show(Tx.s(R.string.share_heroes_left_out), 4500)
            }.onFailure { Toasts.show(LookShare.message(it)); Sounds.error() }
        }
    }
    val saveAs = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        exported { e ->
            scope.launch {
                val ok = withContext(Dispatchers.IO) { runCatching { LookShare.saveTo(ctx, e, uri) }.isSuccess }
                if (ok) { Toasts.show(Tx.s(R.string.share_saved_there)); Sounds.confirm() } else Toasts.show(Tx.s(R.string.share_error_cant_save))
            }
        }
    }
    fun openForm() {
        exported { e ->
            scope.launch {
                val saved = withContext(Dispatchers.IO) { runCatching { LookShare.saveToLooks(e, forGitHub = true) }.getOrNull() }
                val url = GalleryUrls.shareForm(e.manifest.name, e.manifest.author, e.manifest.description)
                val opened = runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
                when {
                    !opened -> { Toasts.show(Tx.s(R.string.share_no_browser), 4500); LookQr.title = Tx.s(R.string.share_form_qr_title); LookQr.link = url }
                    saved != null -> Toasts.show(Tx.s(R.string.share_form_attach, "ROMs/tandem/looks/${saved.name}"), 6000) // tx-ok: a path
                }
            }
        }
    }

    val keys = listOf("name", "author", "about", "save", "saveas", "qr", "gallery")
    val target = rememberFocusTarget(route, keys)
    fun focus(title: String, value: String, help: String, color: Color) {
        Selection.current = Selected.Setting(title, value, help, color)
    }
    Row(Modifier.fillMaxSize().padding(start = 14.dp, end = 22.dp)) {
        val scroll = rememberScrollState()
        Column(
            Modifier.weight(1.1f).fillMaxHeight().fadingEdges(scroll).verticalScroll(scroll)
                .padding(start = FocusRoomSide, end = FocusRoomSide, top = FocusRoom, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SettingsHeader(Tx.s(R.string.share_about_heading), Tx.s(R.string.share_about_note))
            MenuRow(Tx.s(R.string.share_name), name, "name", route, target, Palette.Mint, Glyph.Pencil) {
                TextEntry.ask(Tx.s(R.string.share_name), draft.name.ifBlank { spec.name }) { v -> draft = draft.copy(name = v.trim().take(60)); LookShare.saveDraft(ctx, draft) }
            }
            MenuRow(Tx.s(R.string.share_author), draft.author.ifBlank { Tx.s(R.string.share_author_none) }, "author", route, target, Palette.Mint, Glyph.Heart) {
                TextEntry.ask(Tx.s(R.string.share_author_ask), draft.author) { v -> draft = draft.copy(author = v.trim().take(40)); LookShare.saveDraft(ctx, draft) }
            }
            MenuRow(Tx.s(R.string.share_description), draft.description.ifBlank { Tx.s(R.string.share_description_none) }, "about", route, target, Palette.Mint, Glyph.Text) {
                TextEntry.ask(Tx.s(R.string.share_description), draft.description) { v -> draft = draft.copy(description = v.trim().take(300)); LookShare.saveDraft(ctx, draft) }
            }
            Spacer(Modifier.height(6.dp))
            SettingsHeader(Tx.s(R.string.share_send_heading), Tx.s(R.string.share_send_note))
            MenuRow(Tx.s(R.string.share_save), null, "save", route, target, Palette.Sky, Glyph.Save) {
                exported { e ->
                    scope.launch {
                        val f = withContext(Dispatchers.IO) { runCatching { LookShare.saveToLooks(e) }.getOrNull() }
                        if (f != null) { Toasts.show(Tx.s(R.string.share_saved, "ROMs/tandem/looks/${f.name}"), 4500); Sounds.confirm() } // tx-ok: a path
                        else Toasts.show(Tx.s(R.string.share_error_cant_save))
                    }
                }
            }
            MenuRow(Tx.s(R.string.share_save_as), null, "saveas", route, target, Palette.Sky, Glyph.Download) {
                runCatching { saveAs.launch("${PackImport.safeName(name)}.tandem") }.onFailure { Toasts.show(Tx.s(R.string.look_packs_no_picker)) }
            }
            MenuRow(
                Tx.s(if (LookQr.link != null) R.string.share_qr_hide else R.string.share_qr_show), null, "qr", route, target, Palette.Lavender, Glyph.Screens,
            ) {
                when {
                    LookQr.link != null -> LookQr.link = null
                    galleryId != null -> { LookQr.title = name; LookQr.link = GalleryUrls.page(galleryId!!) }
                    else -> Confirm.ask(
                        Tx.s(R.string.share_qr_no_link), Tx.s(R.string.share_qr_no_link_body), Tx.s(R.string.share_gallery),
                    ) { openForm() }
                }
            }
            MenuRow(Tx.s(R.string.share_gallery), null, "gallery", route, target, Palette.Peach, Glyph.Sparkle) {
                Confirm.ask(Tx.s(R.string.share_gallery_ask), Tx.s(R.string.share_gallery_ask_body), Tx.s(R.string.share_gallery_go)) { openForm() }
            }
            // The other screen explains the page: what goes in, how the QR code and the gallery work.
            LaunchedEffect(name) {
                focus(name, Tx.s(R.string.share_title), "${Tx.s(R.string.share_privacy)}\n\n${Tx.s(R.string.share_qr_help)} ${Tx.s(R.string.share_gallery_help)}", Palette.Mint)
            }
        }
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier.weight(0.9f).fillMaxHeight().padding(top = FocusRoom, bottom = 22.dp).clip(RoundedCornerShape(24.dp))
                .background(Palette.Card.copy(alpha = 0.72f)).padding(18.dp),
        ) {
            val qr = LookQr.link
            if (qr != null) QrPanel(qr, LookQr.title) else ShareSummary(spec, name, draft.author)
        }
    }
}

/** The side panel: the look in miniature, what goes in and what never does. */
@Composable
private fun ShareSummary(spec: ThemeSpec, name: String, author: String) {
    val look = Store.settings.look
    val art = ThemePacks.artSource
    Column(Modifier.fillMaxSize()) {
        ThemeMiniature(spec, ThemePacks.byId(look.theme), Modifier.fillMaxWidth().aspectRatio(1.7f).clip(RoundedCornerShape(16.dp)), moving = true, tile = 48.dp)
        Spacer(Modifier.height(10.dp))
        Text(name, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (author.isNotBlank()) Text(Tx.s(R.string.look_packs_by, author), style = MaterialTheme.typography.titleSmall, color = spec.accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(8.dp))
        val scroll = rememberScrollState()
        Column(Modifier.weight(1f).fadingEdges(scroll).verticalScroll(scroll), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(Tx.s(R.string.share_goes_in), style = MaterialTheme.typography.labelLarge, color = Palette.Muted)
            val parts = buildList {
                add(Tx.s(R.string.share_part_theme, spec.name))
                if (look.wallTop.startsWith("file:") || look.wallBottom.startsWith("file:") || spec.wallpaperTop != null || spec.wallpaperBottom != null) add(Tx.s(R.string.share_part_wallpaper))
                if (art != null) add(Tx.s(R.string.share_part_icons))
                add(Tx.s(R.string.share_part_layout, HomeLayouts.firstOrNull { it.first == look.homeLayout }?.second ?: look.homeLayout))
                add(Tx.s(R.string.share_part_sounds, SoundPack.of(look.soundPack).label))
                add(Tx.s(R.string.share_part_motion))
            }
            Text(parts.joinParts().replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodyMedium)
            Text(Tx.s(R.string.share_privacy), style = MaterialTheme.typography.bodyMedium, color = Palette.Muted)
        }
    }
}

/** A QR code with what it opens, for the side panel when there is no second screen. */
@Composable
private fun QrPanel(link: String, title: String) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        QrCode(link, Modifier.size(200.dp))
        Spacer(Modifier.height(10.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(Tx.s(R.string.share_qr_scan), style = MaterialTheme.typography.bodySmall, color = Palette.Muted, textAlign = TextAlign.Center)
    }
}

/** The other screen while Show QR is on: a big code to scan with a phone's camera, and the address under it. */
@Composable
fun LookQrScreen() {
    val link = LookQr.link ?: return
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(LookQr.title, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(12.dp))
        Box(Modifier.weight(1f, fill = false).aspectRatio(1f), contentAlignment = Alignment.Center) { QrCode(link, Modifier.fillMaxSize()) }
        Spacer(Modifier.height(12.dp))
        Text(Tx.s(R.string.share_qr_scan), style = MaterialTheme.typography.bodyLarge, color = Palette.Muted, textAlign = TextAlign.Center)
        // An address reads left to right in every language.
        Text(link, style = MaterialTheme.typography.labelMedium.copy(textDirection = TextDirection.Ltr), color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * [text] as a QR code (ZXing, Apache-2.0): black modules on a white square with a quiet zone, never mirrored in a
 * right-to-left layout (a mirrored code doesn't scan).
 */
@Composable
fun QrCode(text: String, modifier: Modifier = Modifier) {
    val matrix = remember(text) {
        runCatching {
            QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.MARGIN to 2, EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M))
        }.getOrNull()
    } ?: return
    Canvas(modifier.aspectRatio(1f).clip(RoundedCornerShape(12.dp)).background(Color.White)) {
        val n = matrix.width
        val cell = size.minDimension / n
        for (y in 0 until n) for (x in 0 until n) {
            if (matrix[x, y]) drawRect(Color.Black, Offset(x * cell, y * cell), Size(cell + 0.6f, cell + 0.6f))
        }
    }
}

// ---- Get a look ----

/** The gallery's list, kept while Tandem runs. */
private object GalleryState {
    var entries by mutableStateOf<List<GalleryEntry>?>(null)
    var error by mutableStateOf<String?>(null)
    var loading by mutableStateOf(false)
    var query by mutableStateOf("")
    var tag by mutableStateOf<String?>(null)
}

@Composable
fun GetLookScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val route = Route.GetLook.key
    var focused by remember { mutableStateOf<String?>(null) }

    fun staging(work: () -> LookShare.Staged) {
        if (LookShare.busy) return
        LookShare.busy = true
        Toasts.show(Tx.s(R.string.share_getting))
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { work() } }
            LookShare.busy = false
            r.onSuccess { LookShare.show(it); FocusMemory.forget("lookpreview") }.onFailure { Toasts.show(LookShare.message(it), 4500); Sounds.error() }
        }
    }
    fun load(force: Boolean) {
        if (GalleryState.loading || (!force && GalleryState.entries != null)) return
        GalleryState.loading = true
        scope.launch {
            // What came last time shows at once; the gallery is asked only now, because this page was opened.
            if (GalleryState.entries == null) GalleryState.entries = withContext(Dispatchers.IO) { runCatching { LookShare.galleryIndex(ctx, cached = true) }.getOrNull()?.takeIf { it.isNotEmpty() } }
            val r = withContext(Dispatchers.IO) { runCatching { LookShare.galleryIndex(ctx) } }
            GalleryState.loading = false
            r.onSuccess { GalleryState.entries = it; GalleryState.error = null }.onFailure { GalleryState.error = LookShare.message(it) }
        }
    }
    fun fromGallery(id: String) {
        staging {
            val entry = LookShare.galleryIndex(ctx).firstOrNull { it.id == id } ?: throw io.github.zahakj.tandem.data.HttpError(404, "")
            LookShare.fromGallery(ctx, entry)
        }
    }
    // Asked each time the page opens: a list that hasn't changed costs one small "not modified" answer.
    LaunchedEffect(Unit) { load(true) }
    // Opened from a link (a QR code, a web page): ask before going online for it.
    val link = LookShare.link
    LaunchedEffect(link) {
        if (link == null) return@LaunchedEffect
        LookShare.link = null
        when (link) {
            is GalleryUrls.Link.Gallery -> Confirm.ask(Tx.s(R.string.share_link_ask), Tx.s(R.string.share_link_ask_gallery, link.id), Tx.s(R.string.share_link_get)) { fromGallery(link.id) }
            is GalleryUrls.Link.Web -> Confirm.ask(
                Tx.s(R.string.share_link_ask), Tx.s(R.string.share_link_ask_web, Uri.parse(link.url).host.orEmpty()), Tx.s(R.string.share_link_get),
            ) { staging { LookShare.fromUrl(ctx, link.url) } }
        }
    }

    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) staging { LookShare.fromUri(ctx, uri) }
    }
    fun askLink() {
        TextEntry.ask(Tx.s(R.string.share_from_link_ask), "https://") { text -> // tx-ok: the start of an address
            val t = text.trim()
            when (val l = GalleryUrls.parse(t)) {
                is GalleryUrls.Link.Gallery -> fromGallery(l.id)
                is GalleryUrls.Link.Web -> staging { LookShare.fromUrl(ctx, l.url) }
                null -> if (GalleryUrls.safeUrl(t) != null) staging { LookShare.fromUrl(ctx, t) } else Toasts.show(Tx.s(R.string.share_error_https))
            }
        }
    }

    val all = GalleryState.entries.orEmpty()
    val shown = remember(all, GalleryState.query, GalleryState.tag) { GalleryIndex.search(all, GalleryState.query, GalleryState.tag) }
    val tags = remember(all) { GalleryIndex.tags(all).take(6) }
    val actions = listOf("a:file", "a:link", "a:search")
    val keys = actions + listOfNotNull("undo".takeIf { LookShare.undoable }) + (if (tags.isEmpty()) emptyList() else listOf("tag:") + tags.map { "tag:$it" }) +
        shown.map { "look:${it.id}" }
    val target = rememberFocusTarget(route, keys)
    val grid = rememberLazyGridState()
    val firstLook = keys.indexOfFirst { it.startsWith("look:") }
    val at = keys.indexOf(target)
    // Grid items before the looks: the actions, undo, the tags and the heading; each look is an item of its own.
    val undoItem = if (LookShare.undoable) 1 else 0
    val tagItem = if (tags.isEmpty()) 0 else 1
    KeepInView(
        grid,
        when {
            target == "undo" -> 1
            target?.startsWith("tag:") == true -> 1 + undoItem
            firstLook < 0 || at < firstLook -> 0
            else -> 2 + undoItem + tagItem + (at - firstLook)
        },
    )

    Row(Modifier.fillMaxSize().padding(start = 14.dp, end = 22.dp)) {
        LazyVerticalGrid(
            GridCells.Fixed(2),
            Modifier.weight(1.3f).fillMaxHeight().fadingEdges(grid),
            state = grid,
            contentPadding = PaddingValues(start = FocusRoomSide, end = FocusRoomSide, top = FocusRoom, bottom = 40.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "actions", span = { GridItemSpan(maxLineSpan) }) {
                Row(Modifier.fillMaxWidth().focusRow(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    LookActionTile(Tx.s(R.string.share_from_file), Tx.s(R.string.share_from_file_detail), Glyph.Folder, Palette.Mint, "a:file", target, route, { focused = null }) {
                        runCatching { pickFile.launch(arrayOf("*/*")) }.onFailure { Toasts.show(Tx.s(R.string.look_packs_no_picker)) }
                    }
                    LookActionTile(Tx.s(R.string.share_from_link), Tx.s(R.string.share_from_link_detail), Glyph.Download, Palette.Sky, "a:link", target, route, { focused = null }) { askLink() }
                    val searching = GalleryState.query.isNotBlank()
                    LookActionTile(
                        Tx.s(if (searching) R.string.share_search_clear else R.string.share_search),
                        if (searching) GalleryState.query else Tx.s(R.string.share_search_detail), Glyph.Search, Palette.Butter, "a:search", target, route, { focused = null },
                    ) {
                        if (searching) GalleryState.query = ""
                        else TextEntry.ask(Tx.s(R.string.share_search), GalleryState.query) { GalleryState.query = it.trim() }
                    }
                }
            }
            if (LookShare.undoable) item(key = "undo", span = { GridItemSpan(maxLineSpan) }) {
                MenuRow(Tx.s(R.string.share_undo), null, "undo", route, target, Palette.Peach, Glyph.Restore) {
                    LookShare.undo(ctx)
                    Toasts.show(Tx.s(R.string.share_undone))
                    Sounds.undo()
                }
            }
            if (tags.isNotEmpty()) item(key = "tags", span = { GridItemSpan(maxLineSpan) }) {
                Row(Modifier.fillMaxWidth().focusRow(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (listOf<String?>(null) + tags).forEach { t ->
                        val k = "tag:" + t.orEmpty()
                        TickChip(t ?: Tx.s(R.string.share_tag_all), GalleryState.tag == t, Palette.Lavender, Modifier.weight(1f).takesFocus(route, k, target == k)) {
                            GalleryState.tag = t
                        }
                    }
                }
            }
            item(key = "heading", span = { GridItemSpan(maxLineSpan) }) {
                SettingsHeader(
                    when {
                        GalleryState.loading && all.isEmpty() -> Tx.s(R.string.share_gallery_loading)
                        all.isEmpty() -> Tx.s(R.string.share_gallery_heading)
                        else -> Tx.p(R.plurals.share_gallery_count, shown.size, shown.size)
                    },
                    Tx.s(R.string.share_gallery_note),
                )
            }
            val error = GalleryState.error
            if (shown.isEmpty()) item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    when {
                        error != null && all.isEmpty() -> error
                        all.isNotEmpty() -> Tx.s(R.string.share_gallery_no_match)
                        GalleryState.loading -> Tx.s(R.string.share_gallery_loading)
                        else -> Tx.s(R.string.share_gallery_empty)
                    },
                    Modifier.padding(horizontal = 8.dp, vertical = 6.dp), style = MaterialTheme.typography.bodyMedium, color = Palette.Muted,
                )
            }
            items(shown, key = { it.id }) { e ->
                val k = "look:${e.id}"
                GalleryCard(e, Modifier.takesFocus(route, k, k == target), onFocus = {
                    FocusMemory.remember(route, k); focused = e.id
                    Selection.current = Selected.Setting(e.name, e.author, e.description.ifBlank { null }, Palette.Accent, Tx.s(R.string.share_preview_open))
                }) { staging { LookShare.fromGallery(ctx, e) } }
            }
        }
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier.weight(0.8f).fillMaxHeight().padding(top = FocusRoom, bottom = 22.dp).clip(RoundedCornerShape(24.dp))
                .background(Palette.Card.copy(alpha = 0.72f)).padding(18.dp),
        ) {
            val e = all.firstOrNull { it.id == focused }
            if (e != null) GalleryDetails(e) else GetLookHelp()
        }
    }
    LookPreviewPanel()
}

/** A way in: an icon over two short lines, a third of the grid's width (as Theme packs' import tiles). */
@Composable
private fun RowScope.LookActionTile(
    label: String, detail: String, icon: Glyph, color: Color, key: String, target: String?, route: String, onFocus: () -> Unit, onClick: () -> Unit,
) {
    FocusCard(
        Modifier.weight(1f).height(68.dp).takesFocus(route, key, key == target), glow = color, shape = RoundedCornerShape(16.dp),
        onFocus = { FocusMemory.remember(route, key); onFocus() }, onClick = onClick,
    ) {
        Row(Modifier.fillMaxSize().background(color.copy(alpha = 0.08f)).padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            GlyphChip(icon, color, size = 36.dp, round = true)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                ShrinkText(label, MaterialTheme.typography.titleSmall, Modifier.fillMaxWidth(), maxLines = 1, minScale = 0.75f)
                Text(detail, style = MaterialTheme.typography.labelSmall, color = Palette.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** A gallery preview picture, downloaded (size-capped) the first time it shows. */
@Composable
private fun previewOf(e: GalleryEntry): File? {
    val ctx = LocalContext.current
    return produceState<File?>(null, e.id, e.sha256) {
        value = withContext(Dispatchers.IO) { runCatching { LookShare.downloads(ctx).preview(e) }.getOrNull() }
    }.value
}

/** A gallery look as a card: its preview, then its name and who made it (the pack manager's cards, again). */
@Composable
private fun GalleryCard(e: GalleryEntry, focus: Modifier, onFocus: () -> Unit, onClick: () -> Unit) {
    val picture = previewOf(e)
    FocusCard(Modifier.fillMaxWidth().then(focus), glow = Palette.Accent, shape = RoundedCornerShape(18.dp), onFocus = onFocus, onClick = onClick) {
        Column(Modifier.fillMaxWidth().padding(8.dp)) {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp)).background(Palette.CardHigh)) {
                if (picture != null) AsyncImage(picture, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                if (!e.supported) Pill(Tx.s(R.string.share_needs_update), Palette.Peach, Modifier.align(Alignment.TopEnd).padding(6.dp).darkChip())
            }
            Spacer(Modifier.height(8.dp))
            Text(e.name, Modifier.padding(horizontal = 4.dp), style = MaterialTheme.typography.titleSmall, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis)
            ShrinkText(
                e.author.ifBlank { null }?.let { Tx.s(R.string.look_packs_by, it) } ?: e.tags.joinToString(" · "),
                MaterialTheme.typography.labelMedium, Modifier.fillMaxWidth().padding(horizontal = 4.dp), maxLines = 1, minScale = 0.75f, color = Palette.Muted,
            )
        }
    }
}

@Composable
private fun GalleryDetails(e: GalleryEntry) {
    val picture = previewOf(e)
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(16.dp)).background(Palette.CardHigh)) {
            if (picture != null) AsyncImage(picture, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        Spacer(Modifier.height(12.dp))
        Text(e.name, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (e.author.isNotBlank()) Text(Tx.s(R.string.look_packs_by, e.author), style = MaterialTheme.typography.titleSmall, color = Palette.Accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(8.dp))
        val scroll = rememberScrollState()
        Column(Modifier.weight(1f).fadingEdges(scroll).verticalScroll(scroll), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (e.description.isNotBlank()) Text(e.description, style = MaterialTheme.typography.bodyMedium, color = Palette.Muted)
            if (e.tags.isNotEmpty()) Text(e.tags.joinToString(" · "), style = MaterialTheme.typography.labelLarge, color = Palette.Lavender)
            Text(
                android.text.format.Formatter.formatShortFileSize(Tx.context, e.size),
                style = MaterialTheme.typography.labelMedium, color = Palette.Muted,
            )
        }
    }
}

@Composable
private fun GetLookHelp() {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Pip(72.dp, mood = 1)
        Spacer(Modifier.height(12.dp))
        Text(Tx.s(R.string.share_get_title), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(Tx.s(R.string.share_get_help), style = MaterialTheme.typography.bodyMedium, color = Palette.Muted, textAlign = TextAlign.Center)
    }
}

/**
 * The preview card for a look about to be applied: its picture, name, author and colours, where it came from and
 * what it changes. Nothing changes until "Use this look".
 */
@Composable
fun LookPreviewPanel() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val shown = rememberLatest(LookShare.staged)
    OverlayPanel(LookShare.staged != null, onClose = { LookShare.discard() }) {
        val s = shown ?: return@OverlayPanel
        val routeKey = "lookpreview"
        val target = rememberFocusTarget(routeKey, listOf("use", "cancel"))
        Column(Modifier.width(560.dp).background(Palette.Card, RoundedCornerShape(24.dp)).padding(18.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Box(Modifier.width(220.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(14.dp)).background(swatchBrush(s.colors))) {
                    s.picture?.let { AsyncImage(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(s.name.ifBlank { Tx.s(R.string.share_untitled) }, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (s.manifest.author.isNotBlank()) Text(Tx.s(R.string.look_packs_by, s.manifest.author), style = MaterialTheme.typography.titleSmall, color = s.colors.getOrNull(2) ?: Palette.Accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        when (val src = s.source) {
                            LookShare.Source.Picked -> Tx.s(R.string.share_from_a_file)
                            is LookShare.Source.Web -> Tx.s(R.string.share_from_site, src.host)
                            is LookShare.Source.Gallery -> Tx.s(R.string.share_from_gallery)
                        },
                        style = MaterialTheme.typography.labelMedium, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    if (s.manifest.description.isNotBlank()) Text(s.manifest.description, style = MaterialTheme.typography.bodySmall, color = Palette.Muted, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                s.colors.forEach { c -> Box(Modifier.size(22.dp).clip(CircleShape).background(c)) }
            }
            Spacer(Modifier.height(10.dp))
            Text(Tx.s(R.string.share_preview_changes), style = MaterialTheme.typography.labelLarge, color = Palette.Muted)
            Text(changes(s).joinParts().replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodyMedium)
            Text(Tx.s(R.string.share_preview_keeps), style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
            Spacer(Modifier.height(10.dp))
            Column(Modifier.heightIn(max = 120.dp).padding(horizontal = FocusRoomSide), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                MenuRow(Tx.s(R.string.share_preview_use), null, "use", routeKey, target, Palette.Mint, Glyph.Sparkle) {
                    scope.launch {
                        runCatching { LookShare.install(ctx, s) }.onFailure { Toasts.show(LookShare.message(it)); Sounds.error(); LookShare.discard() }
                        Sounds.confirm()
                        Nav.focusTick++
                    }
                }
                MenuRow(Tx.s(R.string.share_preview_cancel), null, "cancel", routeKey, target, Palette.Lavender, Glyph.Close) { LookShare.discard() }
            }
        }
    }
}

/** What a look changes, a few words each: "colours · wallpaper · 62 system icons · home layout · sounds · motion". */
@Composable
private fun changes(s: LookShare.Staged): List<String> {
    val l = s.manifest.look
    return buildList {
        add(Tx.s(R.string.look_pack_has_colours))
        if (s.wallpapers > 0) add(Tx.s(R.string.look_pack_has_wallpaper))
        if (s.icons > 0) add(Tx.p(R.plurals.look_pack_has_icons, s.icons, s.icons))
        if (s.collections > 0) add(Tx.s(R.string.share_change_collections))
        if (s.heroes > 0) add(Tx.p(R.plurals.look_pack_has_heroes, s.heroes, s.heroes))
        l.homeLayout?.let { id -> add(Tx.s(R.string.share_change_layout, HomeLayouts.firstOrNull { it.first == id }?.second ?: id)) }
        l.soundPack?.let { add(Tx.s(R.string.share_change_sounds, SoundPack.of(it).label)) }
        if (l.motion != null || l.swapStyle != null) add(Tx.s(R.string.share_change_motion))
    }
}

private fun swatchBrush(colors: List<Color>): Brush =
    if (colors.size >= 2) Brush.linearGradient(colors.take(3)) else Brush.linearGradient(listOf(Palette.Card, Palette.CardHigh))
