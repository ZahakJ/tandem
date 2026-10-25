package io.github.zahakj.tandem.ui

import android.content.Context
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import io.github.zahakj.tandem.Button
import io.github.zahakj.tandem.Libraries
import io.github.zahakj.tandem.Library
import io.github.zahakj.tandem.Selection
import io.github.zahakj.tandem.data.FolderStyle
import io.github.zahakj.tandem.data.IconGridStore
import io.github.zahakj.tandem.data.Store
import io.github.zahakj.tandem.data.styleOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/*
 * Y › Design folder…: a folder's (or a system's, collection's, genre's) icon, colour, hero, title and shape, chosen
 * with the controller. The rows on the left open a page of choices; highlighting a choice previews it at once, both
 * beside the rows and on the other screen (Spotlight), A keeps it and B steps back. L1 / R1 step between pages. Every
 * choice is saved as it is made (IconGridStore.styles); Reset to theme drops it. The art is drawn by FolderLooks.kt.
 */

object FolderDesign {
    enum class Page(val label: String) { Icon("Icon"), Colour("Colour"), Hero("Hero"), Title("Title") }

    /** The tile being designed. */
    var key by mutableStateOf<String?>(null)
        private set
    /** What the previews show: the saved style, or the highlighted choice. */
    var draft by mutableStateOf(FolderStyle.DEFAULT)
    var saved by mutableStateOf(FolderStyle.DEFAULT)
        private set
    var page by mutableStateOf<Page?>(null)
    /** Which picture slot a file picked from the device goes to. */
    internal var picking: Page? = null

    private fun route(k: String) = "folderdesign:$k"
    fun pageRoute(k: String, p: Page) = "folderdesign:$k:${p.name}"

    fun open(k: String) {
        val st = IconGridStore.config.styleOf(k)
        saved = st; draft = st; page = null
        FocusMemory.forget(route(k))
        key = k
    }

    fun close() {
        key = null; page = null
        Nav.focusTick++
    }

    fun rowsRoute(): String = route(key.orEmpty())

    /** Keeps [style] for the tile being designed. */
    fun save(style: FolderStyle) {
        val k = key ?: return
        saved = style; draft = style
        IconGridStore.setStyle(k, style)
    }

    /**
     * A new folder made from a game (or a system) starts with a strong look: the best game's cover as its icon and
     * that cover's own colour. Worked out in the background; nothing changes if there is no cover.
     */
    fun suggest(ctx: Context, folderId: String, first: GridItem) {
        val g = FolderLooks.strongGames(first.games).take(40).firstOrNull { Libraries.of(it)?.box(it) != null } ?: return
        val box = Libraries.of(g)?.box(g) ?: return
        var color: Int? = null
        inBackground({ color = FolderArtCache.dominant(ctx, box.path) }) {
            val k = "folder:$folderId"
            if (IconGridStore.folder(folderId) == null || !IconGridStore.config.styleOf(k).isDefault) return@inBackground
            IconGridStore.setStyle(k, FolderStyle(icon = FolderStyle.ICON_GAME, iconRef = "game:${g.key}", color = color, colorFromArt = color != null))
        }
    }
}

/** One choice on a page. [action] runs instead of choosing [style] (pick a file, take the colour from the art). */
private class Choice(
    val id: String,
    val label: String,
    val style: FolderStyle,
    val action: (() -> Unit)? = null,
    val preview: @Composable (Dp) -> Unit,
)

private val Emojis = listOf("🎮", "🕹️", "👾", "⭐", "❤️", "🔥", "🏆", "🌙", "🌸", "🍄", "⚔️", "🚀", "🏁", "🧩", "🎵", "👻", "🐉", "💎", "🌿", "☕", "📚", "🎲", "🌊", "⚡")

private val Swatches = listOf(
    0xFF5B8DEF, 0xFF8B6CEF, 0xFF43BE86, 0xFFF29A4A, 0xFFEC6C98, 0xFF35B3C6, 0xFFE0B23A, 0xFFE8475A,
    0xFF2F5FD0, 0xFF7F8BB0, 0xFF2E9E3E, 0xFFB04AD0, 0xFF3A3F55, 0xFF8A6B4E,
)

/** Pictures the user keeps for art, in ROMs/tandem/art (and one folder down). */
private fun artPictures(): List<File> {
    val root = File(Library.romsDir, "tandem/art")
    val exts = setOf("png", "jpg", "jpeg", "webp")
    return root.walkTopDown().maxDepth(2).filter { it.isFile && it.extension.lowercase() in exts }.sortedBy { it.name.lowercase() }.take(60).toList()
}

/** The design editor, composed in the app root above the icon grid's menu. */
@Composable
fun FolderDesignHost() {
    val ctx = LocalContext.current
    val key = FolderDesign.key
    val shownKey = rememberLatest(key)
    val visible = key != null
    PanelGuard(visible)
    // The other screen is the live preview: it keeps moving while the editor is open (Ambient.livePreview).
    androidx.compose.runtime.DisposableEffect(visible) {
        Ambient.livePreview = visible
        onDispose { Ambient.livePreview = false }
    }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    // L1 / R1 step between the pages; every other button stays with the editor.
    InterceptButtons(visible) { b ->
        val pages = FolderDesign.Page.entries
        if (b == Button.L1 || b == Button.R1) {
            val now = FolderDesign.page
            val i = if (now == null) (if (b == Button.R1) -1 else 0) else pages.indexOf(now)
            val next = pages[(i + (if (b == Button.R1) 1 else -1) + pages.size) % pages.size]
            FolderDesign.draft = FolderDesign.saved
            FolderDesign.page = next
            Sounds.move()
        }
        true
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val k = FolderDesign.key ?: return@rememberLauncherForActivityResult
        val slot = FolderDesign.picking ?: return@rememberLauncherForActivityResult
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val file = withContext(Dispatchers.IO) {
                runCatching {
                    val dir = File(Library.romsDir, "tandem/art/folders").apply { mkdirs() }
                    val ext = ctx.contentResolver.getType(uri)?.substringAfter('/')?.let { if (it == "jpeg") "jpg" else it } ?: "png"
                    val safe = k.replace(Regex("[^A-Za-z0-9._-]"), "_")
                    val out = File(dir, "$safe-${slot.name.lowercase()}-${System.currentTimeMillis() % 100000}.$ext")
                    ctx.contentResolver.openInputStream(uri)?.use { input -> out.outputStream().use { input.copyTo(it) } }
                    out.takeIf { it.isFile && it.length() > 0 }
                }.getOrNull()
            }
            if (file == null) { Toasts.show("Couldn't use that picture"); return@launch }
            val s = FolderDesign.saved
            FolderDesign.save(
                when (slot) {
                    FolderDesign.Page.Hero -> s.copy(hero = FolderStyle.HERO_FILE, heroFile = file.path)
                    FolderDesign.Page.Title -> s.copy(logo = FolderStyle.LOGO_FILE, logoFile = file.path)
                    else -> s.copy(icon = FolderStyle.ICON_FILE, iconFile = file.path)
                },
            )
            FolderDesign.page = null
            Toasts.show("Saved to ROMs/tandem/art/folders")
        }
    }
    OverlayPanel(visible, onClose = {
        if (FolderDesign.page != null) { FolderDesign.draft = FolderDesign.saved; FolderDesign.page = null } else FolderDesign.close()
    }) {
        val k = shownKey ?: return@OverlayPanel
        val config = IconGridStore.config
        // The tile as the grid makes it before any style (its own art, colour and games).
        val plain = remember(k, config) { IconGrid.menuData?.first?.item(k)?.let { it.plain ?: it } } ?: run {
            LaunchedEffect(Unit) { FolderDesign.close() }
            return@OverlayPanel
        }
        val draft = FolderDesign.draft
        val preview = remember(plain, draft) { FolderLooks.restyle(plain, draft) }
        // Live on the other screen too: Spotlight shows the highlighted tile, so the preview stands in for it.
        LaunchedEffect(preview) { IconGrid.spot = preview; Selection.current = preview.selected }
        androidx.compose.runtime.DisposableEffect(k) {
            onDispose { IconGrid.spot = IconGrid.menuData?.first?.item(k) ?: IconGrid.spot }
        }
        val pictures by produceState(emptyList<File>()) { value = withContext(Dispatchers.IO) { runCatching { artPictures() }.getOrDefault(emptyList()) } }
        val launchPick: (FolderDesign.Page) -> Unit = { p -> FolderDesign.picking = p; runCatching { pick.launch("image/*") } }
        Column(Modifier.fillMaxSize().background(Palette.Card, RoundedCornerShape(26.dp)).padding(top = 14.dp)) {
            Header(plain, preview)
            Row(Modifier.fillMaxWidth().weight(1f).padding(start = 6.dp, end = 18.dp, bottom = 12.dp)) {
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    val page = FolderDesign.page
                    if (page == null) Rows(k, plain) else ChoicePage(k, page, plain, pictures, launchPick)
                }
                Spacer(Modifier.width(12.dp))
                PreviewPane(preview, Modifier.width(232.dp).fillMaxHeight())
            }
        }
    }
}

@Composable
private fun Header(plain: GridItem, preview: GridItem) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(FolderDesign.page?.let { "Design · ${it.label}" } ?: "Design folder", style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(listOf(plain.title, plain.type).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            ButtonHint("L1", "", Palette.Muted)
            ButtonHint("R1", "Pages", Palette.Muted)
            ButtonHint("A", if (FolderDesign.page == null) "Change" else "Use", Palette.Mint)
            ButtonHint("B", "Back", Palette.Lavender)
        }
    }
}

/** The main rows: what each part is now; A opens its page (shape and frame change in place). */
@Composable
private fun Rows(key: String, plain: GridItem) {
    val s = FolderDesign.saved
    val route = FolderDesign.rowsRoute()
    val rows = listOf("icon", "colour", "hero", "title", "shape", "frame", "reset")
    val target = rememberFocusTarget(route, rows)
    val state = rememberLazyListState()
    KeepInView(state, rows.indexOf(target))
    fun open(p: FolderDesign.Page, current: String) {
        FocusMemory.remember(FolderDesign.pageRoute(key, p), current)
        FolderDesign.page = p
    }
    LazyColumn(
        Modifier.fillMaxSize().fadingEdges(state), state = state,
        contentPadding = PaddingValues(horizontal = FocusRoomSide, vertical = FocusRoom), verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        items(rows, key = { it }) { r ->
            val m = Modifier.takesFocus(route, r, r == target)
            when (r) {
                "icon" -> DesignRow("Icon", iconLabel(plain, s), Palette.Mint, m, glyph = Glyph.Image) { open(FolderDesign.Page.Icon, iconId(s)) }
                "colour" -> DesignRow(
                    "Colour", when { s.color == null -> "Theme"; s.colorFromArt -> "From the art"; else -> "Custom" }, Palette.Peach, m,
                    swatch = s.color?.let { Color(it) }, glyph = Glyph.Theme,
                ) { open(FolderDesign.Page.Colour, colourId(s)) }
                "hero" -> DesignRow("Hero", heroLabel(plain, s), Palette.Sky, m, glyph = Glyph.Layers) { open(FolderDesign.Page.Hero, heroId(s)) }
                "title" -> DesignRow("Title", titleLabel(plain, s), Palette.Butter, m, glyph = Glyph.Text) { open(FolderDesign.Page.Title, titleId(s)) }
                "shape" -> DesignRow(
                    "Shape", when (s.shape) { FolderStyle.SHAPE_ROUNDED -> "Rounded square"; FolderStyle.SHAPE_CIRCLE -> "Circle"; else -> "Theme" }, Palette.Lavender, m, glyph = Glyph.Corners,
                ) {
                    val next = when (s.shape) { FolderStyle.SHAPE_THEME -> FolderStyle.SHAPE_ROUNDED; FolderStyle.SHAPE_ROUNDED -> FolderStyle.SHAPE_CIRCLE; else -> FolderStyle.SHAPE_THEME }
                    FolderDesign.save(s.copy(shape = next))
                }
                "frame" -> DesignRow("Cover frame", if (s.frame) "Theme's frame" else "No frame", Palette.Lavender, m, glyph = Glyph.Highlight) {
                    FolderDesign.save(s.copy(frame = !s.frame))
                    if (s.icon != FolderStyle.ICON_GAME) Toasts.show("Frames show on a game cover icon, when the theme has them")
                }
                // Undoable, so no question first: the toast offers Undo while the designer is still open.
                "reset" -> DesignRow("Reset to theme", if (s.isDefault) "Following the theme" else null, Palette.Peach, m, glyph = Glyph.Restore) {
                    if (s.isDefault) return@DesignRow
                    val before = s
                    FolderDesign.save(FolderStyle.DEFAULT)
                    Sounds.toggle(false)
                    Undo.offer("Back to the theme's look") { if (FolderDesign.key == key) FolderDesign.save(before) else IconGridStore.setStyle(key, before) }
                }
            }
        }
    }
}

@Composable
private fun DesignRow(label: String, value: String?, color: Color, modifier: Modifier, swatch: Color? = null, glyph: Glyph? = null, onClick: () -> Unit) {
    FocusCard(modifier.fillMaxWidth().height(48.dp), glow = color, shape = RoundedCornerShape(14.dp), onClick = onClick) {
        Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (glyph != null) { GlyphChip(glyph, color, 28.dp); Spacer(Modifier.width(10.dp)) }
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (swatch != null) { Box(Modifier.size(16.dp).clip(CircleShape).background(swatch)); Spacer(Modifier.width(6.dp)) }
            if (value != null) Text(value, Modifier.width(150.dp), style = MaterialTheme.typography.labelLarge, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End)
        }
    }
}

// --- Labels and the id of the current choice on each page --------------------------------------------------------

private fun iconLabel(plain: GridItem, s: FolderStyle): String = when (s.icon) {
    FolderStyle.ICON_GAME -> if (s.iconRef?.startsWith("app:") == true) "App icon" else FolderLooks.gameOf(plain, s.iconRef)?.title ?: "Game cover"
    FolderStyle.ICON_MOSAIC -> "Cover mosaic"
    FolderStyle.ICON_FAN -> "Cover fan"
    FolderStyle.ICON_GLYPH -> "Glyph"
    FolderStyle.ICON_EMOJI -> s.emoji ?: "Emoji"
    FolderStyle.ICON_FILE -> s.iconFile?.let { File(it).name } ?: "Picture"
    else -> "Theme"
}

private fun heroLabel(plain: GridItem, s: FolderStyle): String = when (s.hero) {
    FolderStyle.HERO_SWEEP -> "Colour sweep"
    FolderStyle.HERO_GAME -> FolderLooks.gameOf(plain, s.heroRef)?.title ?: "A game's hero"
    FolderStyle.HERO_FILE -> s.heroFile?.let { File(it).name } ?: "Picture"
    else -> "Theme"
}

private fun titleLabel(plain: GridItem, s: FolderStyle): String = when (s.logo) {
    FolderStyle.LOGO_TEXT -> "Text · ${weightName(s.textWeight)} · ${sizeName(s.textSize)}"
    FolderStyle.LOGO_GAME -> "Logo: " + (FolderLooks.gameOf(plain, s.logoRef)?.title ?: "a game")
    FolderStyle.LOGO_FILE -> s.logoFile?.let { File(it).name } ?: "Picture"
    else -> "Theme"
}

private fun weightName(w: Int) = when { w <= 600 -> "Light"; w >= 900 -> "Heavy"; else -> "Bold" }
private fun sizeName(s: Int) = when (s) { 0 -> "Small"; 2 -> "Large"; else -> "Medium" }

private fun iconId(s: FolderStyle) = when (s.icon) {
    FolderStyle.ICON_GAME -> "ref:${s.iconRef}"
    FolderStyle.ICON_GLYPH -> "glyph:${s.glyph}"
    FolderStyle.ICON_EMOJI -> "emoji:${s.emoji}"
    FolderStyle.ICON_FILE -> "file:${s.iconFile}"
    else -> s.icon
}

private fun colourId(s: FolderStyle) = when {
    s.color == null -> "auto"
    s.colorFromArt -> "art"
    else -> "c:${s.color}"
}

private fun heroId(s: FolderStyle) = when (s.hero) {
    FolderStyle.HERO_GAME -> "ref:${s.heroRef}"
    FolderStyle.HERO_FILE -> "file:${s.heroFile}"
    else -> s.hero
}

private fun titleId(s: FolderStyle) = when (s.logo) {
    FolderStyle.LOGO_TEXT -> "text:${s.textWeight}:${s.textSize}"
    FolderStyle.LOGO_GAME -> "ref:${s.logoRef}"
    FolderStyle.LOGO_FILE -> "file:${s.logoFile}"
    else -> s.logo
}

// --- Pages of choices --------------------------------------------------------------------------------------------

/** A tile as it would look with [style], at [size] (for icon choices). */
@Composable
private fun StyledTile(plain: GridItem, style: FolderStyle, size: Dp) {
    val item = remember(plain, style) { FolderLooks.restyle(plain, style) }
    val corner = size * Store.settings.look.gridRoundness.coerceIn(0f, 0.5f)
    Box(Modifier.size(size).graphicsLayer { shape = FolderLooks.shape(style, size, corner); clip = true }) { TileArtView(item.art, size) }
}

@Composable
private fun ChoicePage(key: String, page: FolderDesign.Page, plain: GridItem, pictures: List<File>, pickFile: (FolderDesign.Page) -> Unit) {
    val ctx = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val base = FolderDesign.saved
    val wide = page == FolderDesign.Page.Hero || page == FolderDesign.Page.Title
    val choices = remember(page, plain, base, pictures) { choicesFor(ctx, page, plain, base, pictures, pickFile, scope) }
    val route = FolderDesign.pageRoute(key, page)
    val target = rememberFocusTarget(route, choices.map { it.id })
    val state = rememberLazyGridState()
    KeepInView(state, choices.indexOfFirst { it.id == target })
    val cell = if (wide) 132.dp else 82.dp
    LazyVerticalGrid(
        GridCells.Adaptive(cell + 8.dp),
        Modifier.fillMaxSize().fadingEdges(state), state = state,
        contentPadding = PaddingValues(horizontal = FocusRoomSide, vertical = FocusRoom),
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(choices, key = { it.id }) { c ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val chosen = c.id == when (page) {
                    FolderDesign.Page.Icon -> iconId(base)
                    FolderDesign.Page.Colour -> colourId(base)
                    FolderDesign.Page.Hero -> heroId(base)
                    FolderDesign.Page.Title -> titleId(base)
                }
                FocusCard(
                    Modifier.fillMaxWidth().aspectRatio(if (wide) 16f / 9f else 1f).takesFocus(route, c.id, c.id == target),
                    glow = if (chosen) Palette.Mint else Palette.Sky, shape = RoundedCornerShape(14.dp),
                    onFocus = { if (c.action == null) FolderDesign.draft = c.style },
                    onClick = {
                        val act = c.action
                        if (act != null) { act(); return@FocusCard }
                        var next = c.style
                        FolderDesign.save(next)
                        FolderDesign.page = null
                        // A colour taken from the art follows a new icon.
                        if (page == FolderDesign.Page.Icon && next.colorFromArt) {
                            val src = FolderLooks.artSource(FolderLooks.restyle(plain, next)) ?: return@FocusCard
                            scope.launch {
                                val col = withContext(Dispatchers.IO) { FolderArtCache.dominant(ctx, src) } ?: return@launch
                                if (FolderDesign.key == key) { next = FolderDesign.saved.copy(color = col); FolderDesign.save(next) }
                            }
                        }
                    },
                ) {
                    Box(Modifier.fillMaxSize().background(Palette.CardHigh), contentAlignment = Alignment.Center) { c.preview(cell) }
                }
                Text(
                    c.label, Modifier.padding(top = 3.dp), style = MaterialTheme.typography.labelMedium,
                    color = if (chosen) Palette.Mint else Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun choicesFor(
    ctx: Context, page: FolderDesign.Page, plain: GridItem, base: FolderStyle, pictures: List<File>,
    pickFile: (FolderDesign.Page) -> Unit, scope: kotlinx.coroutines.CoroutineScope,
): List<Choice> {
    val strong = FolderLooks.strongGames(plain.games).take(60)
    val device = Choice("device", "From this device…", base, action = { pickFile(page) }) { s ->
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            GlyphIcon(Glyph.Plus, Palette.Mint, Modifier.size(s * 0.3f))
            Text("Browse", style = MaterialTheme.typography.labelMedium, color = Palette.Mint, maxLines = 1)
        }
    }
    return when (page) {
        FolderDesign.Page.Icon -> buildList {
            fun icon(id: String, label: String, st: FolderStyle) = add(Choice(id, label, st) { s -> StyledTile(plain, st, s) })
            icon(FolderStyle.ICON_AUTO, "Theme", base.copy(icon = FolderStyle.ICON_AUTO, iconRef = null))
            if (FolderLooks.sources(plain, 2).size >= 2) {
                icon(FolderStyle.ICON_MOSAIC, "Mosaic", base.copy(icon = FolderStyle.ICON_MOSAIC))
                icon(FolderStyle.ICON_FAN, "Fan", base.copy(icon = FolderStyle.ICON_FAN))
            }
            for (g in strong.filter { Libraries.of(it)?.box(it) != null }.take(18)) icon("ref:game:${g.key}", g.title, base.copy(icon = FolderStyle.ICON_GAME, iconRef = "game:${g.key}"))
            val apps = (plain.art as? TileArt.Folder)?.inside.orEmpty().filterIsInstance<TileArt.App>().take(12)
            for (a in apps) icon("ref:app:${a.pkg}", "App icon", base.copy(icon = FolderStyle.ICON_GAME, iconRef = "app:${a.pkg}"))
            for (t in TIcon.entries) icon("glyph:${t.name}", t.name, base.copy(icon = FolderStyle.ICON_GLYPH, glyph = t.name))
            for (e in Emojis) icon("emoji:$e", e, base.copy(icon = FolderStyle.ICON_EMOJI, emoji = e))
            for (f in pictures) icon("file:${f.path}", f.nameWithoutExtension, base.copy(icon = FolderStyle.ICON_FILE, iconFile = f.path))
            add(device)
        }
        FolderDesign.Page.Colour -> buildList {
            fun swatch(id: String, label: String, st: FolderStyle, c: Color) = add(Choice(id, label, st) { s -> Box(Modifier.size(s * 0.62f).clip(CircleShape).background(c)) })
            swatch("auto", "Theme", base.copy(color = null, colorFromArt = false), FolderLooks.restyle(plain, base.copy(color = null)).color)
            add(Choice("art", "From the art", base, action = {
                val src = FolderLooks.artSource(FolderLooks.restyle(plain, base))
                if (src == null) Toasts.show("This icon has no picture to take a colour from")
                else scope.launch {
                    val col = withContext(Dispatchers.IO) { FolderArtCache.dominant(ctx, src) }
                    if (col == null) Toasts.show("No clear colour in that art") else { FolderDesign.save(FolderDesign.saved.copy(color = col, colorFromArt = true)); FolderDesign.page = null }
                }
            }) { s -> StyledTile(plain, base, s * 0.62f) })
            val theme = listOf(Palette.Accent, Palette.Mint, Palette.Peach, Palette.Lavender, Palette.Sky, Palette.Butter)
            for (c in (theme + Swatches.map { Color(it) }).distinct()) {
                val argb = c.toArgb()
                swatch("c:$argb", "", base.copy(color = argb, colorFromArt = false), c)
            }
        }
        FolderDesign.Page.Hero -> buildList {
            fun hero(id: String, label: String, st: FolderStyle) = add(Choice(id, label, st) { _ -> HeroThumb(plain, st) })
            hero(FolderStyle.HERO_AUTO, "Theme", base.copy(hero = FolderStyle.HERO_AUTO, heroRef = null, heroFile = null))
            hero(FolderStyle.HERO_SWEEP, "Colour sweep", base.copy(hero = FolderStyle.HERO_SWEEP))
            for (g in strong.filter { FolderLooks.gameHero(it) != null }.take(12)) hero("ref:${g.key}", g.title, base.copy(hero = FolderStyle.HERO_GAME, heroRef = g.key))
            for (f in pictures) hero("file:${f.path}", f.nameWithoutExtension, base.copy(hero = FolderStyle.HERO_FILE, heroFile = f.path))
            add(device)
        }
        FolderDesign.Page.Title -> buildList {
            fun title(id: String, label: String, st: FolderStyle) = add(Choice(id, label, st) { _ -> TitleThumb(plain, st) })
            title(FolderStyle.LOGO_AUTO, "Theme", base.copy(logo = FolderStyle.LOGO_AUTO, logoRef = null, logoFile = null))
            for (size in 0..2) for (w in listOf(600, 800, 900)) {
                title("text:$w:$size", "${weightName(w)} · ${sizeName(size)}", base.copy(logo = FolderStyle.LOGO_TEXT, textWeight = w, textSize = size))
            }
            for (g in strong.filter { Libraries.of(it)?.logo(it) != null }.take(12)) title("ref:${g.key}", g.title, base.copy(logo = FolderStyle.LOGO_GAME, logoRef = g.key))
            for (f in pictures) title("file:${f.path}", f.nameWithoutExtension, base.copy(logo = FolderStyle.LOGO_FILE, logoFile = f.path))
            add(device)
        }
    }
}

/** A small picture of the hero [st] gives. */
@Composable
private fun HeroThumb(plain: GridItem, st: FolderStyle) {
    val item = remember(plain, st) { FolderLooks.restyle(plain, st) }
    val hero = remember(item) { FolderLooks.hero(item) }
    HeroPicture(item, hero, Modifier.fillMaxSize())
}

@Composable
private fun TitleThumb(plain: GridItem, st: FolderStyle) {
    val item = remember(plain, st) { FolderLooks.restyle(plain, st) }
    Box(Modifier.fillMaxSize().background(lerp(item.color, Palette.Night, 0.35f)), contentAlignment = Alignment.Center) { TitleArt(item, 0.55f) }
}

/** The hero, or the colour sweep (with the folder's glyph) when there is none. */
@Composable
private fun HeroPicture(item: GridItem, hero: File?, modifier: Modifier) {
    Box(modifier) {
        if (hero != null) AsyncImage(rememberSizedRequest(hero, 640), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else ColorSweep(item.color, Modifier.fillMaxSize().glyphEcho(FolderLooks.sweepGlyph(item)))
    }
}

/** What stands over the hero: the text title, the logo, or nothing (the icon shows instead). */
@Composable
private fun TitleArt(item: GridItem, scale: Float) {
    val st = item.style ?: FolderStyle.DEFAULT
    val logo = remember(item) { FolderLooks.logo(item) }
    when {
        st.logo == FolderStyle.LOGO_TEXT -> FolderTitle(item.title, st, Modifier.padding(6.dp), scale)
        logo != null -> AsyncImage(rememberSizedRequest(logo, 480), null, Modifier.fillMaxWidth(0.8f).fillMaxHeight(0.7f), contentScale = ContentScale.Fit)
        else -> Text(item.title, Modifier.padding(6.dp), style = MaterialTheme.typography.labelLarge.onArt(), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** The tile and a small copy of the other screen, as they will look. */
@Composable
private fun PreviewPane(preview: GridItem, modifier: Modifier) {
    Column(modifier.padding(top = 10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        val tile = 124.dp
        val corner = tile * Store.settings.look.gridRoundness.coerceIn(0f, 0.5f)
        Box(
            Modifier.size(tile).graphicsLayer {
                shape = FolderLooks.shape(preview.style, tile, corner); clip = true
                shadowElevation = 14f * density; spotShadowColor = preview.color; ambientShadowColor = preview.color
            },
        ) { TileArtView(preview.art, tile) }
        Spacer(Modifier.height(14.dp))
        val hero = remember(preview) { FolderLooks.hero(preview) }
        Box(
            Modifier.fillMaxWidth().aspectRatio(16f / 9f).graphicsLayer { shape = RoundedCornerShape(14.dp); clip = true },
            contentAlignment = Alignment.Center,
        ) {
            HeroPicture(preview, hero, Modifier.fillMaxSize())
            TitleArt(preview, 0.5f)
        }
        Text("Other screen", Modifier.padding(top = 4.dp), style = MaterialTheme.typography.labelSmall, color = Palette.Muted, maxLines = 1)
    }
}

/** A folder's name as its title, in the weight and size the style asks for, readable on any art. */
@Composable
fun FolderTitle(title: String, style: FolderStyle, modifier: Modifier = Modifier, scale: Float = 1f) {
    val size = (when (style.textSize) { 0 -> 26f; 2 -> 46f; else -> 36f }) * scale
    Text(
        title, modifier,
        style = MaterialTheme.typography.displaySmall.copy(
            fontSize = size.sp, lineHeight = (size * 1.08f).sp, fontWeight = FontWeight(style.textWeight.coerceIn(100, 900)), letterSpacing = (-0.4).sp,
        ).onArt(),
        color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
    )
}
