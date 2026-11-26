package io.github.zahakj.tandem.ui

import io.github.zahakj.tandem.Tx
import io.github.zahakj.tandem.R
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.togetherWith
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
import io.github.zahakj.tandem.data.ArtSlot
import io.github.zahakj.tandem.data.FolderStyle
import io.github.zahakj.tandem.data.SystemArt
import io.github.zahakj.tandem.data.SystemArtFetch
import io.github.zahakj.tandem.data.WebOption
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
    enum class Page(@androidx.annotation.StringRes private val res: Int) {
        Icon(R.string.grid_design_icon), Colour(R.string.grid_design_colour), Hero(R.string.grid_design_hero), Title(R.string.grid_design_title);
        val label: String get() = Tx.s(res)
    }

    /** The tile being designed. */
    var key by mutableStateOf<String?>(null)
        private set
    /** What the previews show: the saved style, or the highlighted choice. */
    var draft by mutableStateOf(FolderStyle.DEFAULT)
    var saved by mutableStateOf(FolderStyle.DEFAULT)
        private set
    var page by mutableStateOf<Page?>(null)
    /** Which way the last L1 / R1 went (-1, 1), so the next page slides in from that side; 0 for A and B. */
    internal var turned = 0
    /** Which picture slot a file picked from the device goes to. */
    internal var picking: Page? = null

    private fun route(k: String) = "folderdesign:$k"
    fun pageRoute(k: String, p: Page) = "folderdesign:$k:${p.name}"

    /** Opened straight on a page (from the edit screen's Look rows): B there goes back to the edit screen. */
    internal var direct = false

    fun open(k: String, at: Page? = null) {
        val st = IconGridStore.config.styleOf(k)
        saved = st; draft = st; page = at
        direct = at != null
        FocusMemory.forget(route(k))
        if (at != null) FocusMemory.forget(pageRoute(k, at))
        key = k
    }

    fun close() {
        key = null; page = null; direct = false
        Nav.focusTick++
    }

    /** A choice was kept: back to the rows, or (opened straight on a page) back to the edit screen. */
    fun done() {
        turned = 0
        if (direct) close() else page = null
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

/** Pictures the user keeps for art, in ROMs/tandem/art (and one folder down: uploads land in folders/). */
private fun artPictures(): List<File> {
    val root = File(Library.romsDir, "tandem/art")
    val exts = setOf("png", "jpg", "jpeg", "webp", "gif")
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
            FolderDesign.turned = if (b == Button.R1) 1 else -1
            FolderDesign.page = next
            // One page turn, one sound and one feel: the highlight settling on the new page stays quiet (SettleGate).
            Sounds.page()
        }
        true
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val k = FolderDesign.key ?: return@rememberLauncherForActivityResult
        val slot = FolderDesign.picking ?: return@rememberLauncherForActivityResult
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val name = when (slot) { FolderDesign.Page.Hero -> "hero"; FolderDesign.Page.Title -> "logo"; else -> "icon" }
            // Checked, scaled down and copied into ROMs/tandem/art/folders (AnimatedArt.import); GIFs and animated WebPs move.
            val result = withContext(Dispatchers.IO) { AnimatedArt.import(ctx, uri, k, name) }
            val file = result.file
            if (file == null) {
                Sounds.error()
                Toasts.show(
                    when (result.problem) {
                        io.github.zahakj.tandem.data.ArtUpload.Problem.TooBig -> Tx.s(R.string.grid_design_upload_too_big)
                        io.github.zahakj.tandem.data.ArtUpload.Problem.TooLarge -> Tx.s(R.string.grid_design_upload_too_large)
                        io.github.zahakj.tandem.data.ArtUpload.Problem.NotAPicture -> Tx.s(R.string.grid_design_upload_not_picture)
                        else -> Tx.s(R.string.grid_design_picture_failed)
                    },
                    5000,
                )
                return@launch
            }
            val s = FolderDesign.saved
            FolderDesign.save(
                when (slot) {
                    FolderDesign.Page.Hero -> s.copy(hero = FolderStyle.HERO_FILE, heroFile = file.path)
                    FolderDesign.Page.Title -> s.copy(logo = FolderStyle.LOGO_FILE, logoFile = file.path)
                    else -> s.copy(icon = FolderStyle.ICON_FILE, iconFile = file.path)
                },
            )
            FolderDesign.done()
            Toasts.show(Tx.s(R.string.grid_design_picture_saved))
        }
    }
    OverlayPanel(visible, onClose = {
        when {
            FolderDesign.page == null || FolderDesign.direct -> FolderDesign.close()
            else -> { FolderDesign.draft = FolderDesign.saved; FolderDesign.turned = 0; FolderDesign.page = null }
        }
    }) {
        val k = shownKey ?: return@OverlayPanel
        val config = IconGridStore.config
        // The tile as the grid makes it before any style (its own art, colour and games).
        val plain = remember(k, config) { IconGrid.menuData?.first?.item(k)?.let { it.plain ?: it } } ?: run {
            LaunchedEffect(Unit) { FolderDesign.close() }
            return@OverlayPanel
        }
        val draft = FolderDesign.draft
        // A set's pictures are drawn in the background: the preview looks again when they are ready.
        val preview = remember(plain, draft, SetArt.revision, WebArt.revision, SystemArtFetch.revision) { FolderLooks.restyle(plain, draft) }
        LaunchedEffect(k) { if (plain.kind == TileKind.System) plain.platformId?.let(SetArt::warmSystem) }
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
                // L1 / R1 slide the next page in from the side they point to; A and B (into a page and back out of
                // it) the way the edit screen's pages go. A short slide and fade, both pages in layers.
                val still = Motion.style == AnimationStyle.Off || Store.settings.reduceMotion
                val toEnd = if (isRtl) -1 else 1
                androidx.compose.animation.AnimatedContent(
                    FolderDesign.page, Modifier.weight(1f).fillMaxHeight(),
                    transitionSpec = {
                        val turned = FolderDesign.turned
                        val dir = when {
                            turned != 0 -> turned
                            targetState == null -> -1
                            else -> 1
                        } * toEnd
                        if (still) androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(0)) togetherWith androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(0))
                        else (androidx.compose.animation.slideInHorizontally(androidx.compose.animation.core.tween(220, easing = Motion.EmphasizedDecelerate)) { dir * it / 6 } +
                            androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(180)))
                            .togetherWith(androidx.compose.animation.slideOutHorizontally(androidx.compose.animation.core.tween(150)) { -dir * it / 8 } + androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(110)))
                    },
                    label = "design-page",
                ) { page ->
                    Box(Modifier.fillMaxSize()) {
                        if (page == null) Rows(k, plain) else ChoicePage(k, page, plain, pictures, launchPick)
                    }
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
            Text(FolderDesign.page?.let { Tx.s(R.string.grid_design_page, it.label) } ?: Tx.s(R.string.grid_design_title_main), style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(listOf(plain.title, plain.type).joinParts(), style = MaterialTheme.typography.bodySmall, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            // The pages as tabs, the one shown filled: L1 / R1 step through them.
            ButtonHint("L1", "", Palette.Muted)
            Row(
                Modifier.background(Palette.CardHigh, RoundedCornerShape(50)).padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                for (p in FolderDesign.Page.entries) {
                    val on = p == FolderDesign.page
                    Box(
                        Modifier.background(if (on) Palette.Mint.copy(alpha = 0.3f) else Color.Transparent, RoundedCornerShape(50))
                            .swallowTaps {
                                val from = FolderDesign.page?.ordinal ?: -1
                                FolderDesign.draft = FolderDesign.saved
                                FolderDesign.turned = if (p.ordinal >= from) 1 else -1
                                FolderDesign.page = p
                                Sounds.page()
                            }
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    ) { Text(p.label, style = MaterialTheme.typography.labelMedium, color = if (on) Palette.Text else Palette.Muted, maxLines = 1) }
                }
            }
            ButtonHint("R1", "", Palette.Muted)
            ButtonHint("A", Tx.s(if (FolderDesign.page == null) R.string.grid_design_hint_change else R.string.grid_design_hint_use), Palette.Mint)
            ButtonHint("B", Tx.s(R.string.common_back), Palette.Lavender)
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
        FolderDesign.turned = 0
        FolderDesign.page = p
    }
    LazyColumn(
        Modifier.fillMaxSize().fadingEdges(state), state = state,
        contentPadding = PaddingValues(horizontal = FocusRoomSide, vertical = FocusRoom), verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        items(rows, key = { it }) { r ->
            val m = Modifier.takesFocus(route, r, r == target)
            when (r) {
                "icon" -> DesignRow(FolderDesign.Page.Icon.label, iconLabel(plain, s), Palette.Mint, m, glyph = Glyph.Image) { open(FolderDesign.Page.Icon, iconId(s)) }
                "colour" -> DesignRow(
                    FolderDesign.Page.Colour.label,
                    Tx.s(when { s.color == null -> R.string.grid_design_theme; s.colorFromArt -> R.string.grid_design_from_art; else -> R.string.grid_design_custom }), Palette.Peach, m,
                    swatch = s.color?.let { Color(it) }, glyph = Glyph.Theme,
                ) { open(FolderDesign.Page.Colour, colourId(s)) }
                "hero" -> DesignRow(FolderDesign.Page.Hero.label, heroLabel(plain, s), Palette.Sky, m, glyph = Glyph.Layers) { open(FolderDesign.Page.Hero, heroId(s)) }
                "title" -> DesignRow(FolderDesign.Page.Title.label, titleLabel(plain, s), Palette.Butter, m, glyph = Glyph.Text) { open(FolderDesign.Page.Title, titleId(s)) }
                "shape" -> DesignRow(
                    Tx.s(R.string.grid_design_shape),
                    Tx.s(when (s.shape) { FolderStyle.SHAPE_ROUNDED -> R.string.grid_design_shape_rounded; FolderStyle.SHAPE_CIRCLE -> R.string.grid_design_shape_circle; else -> R.string.grid_design_theme }),
                    Palette.Lavender, m, glyph = Glyph.Corners,
                ) {
                    val next = when (s.shape) { FolderStyle.SHAPE_THEME -> FolderStyle.SHAPE_ROUNDED; FolderStyle.SHAPE_ROUNDED -> FolderStyle.SHAPE_CIRCLE; else -> FolderStyle.SHAPE_THEME }
                    FolderDesign.save(s.copy(shape = next))
                }
                "frame" -> DesignRow(Tx.s(R.string.grid_design_frame), Tx.s(if (s.frame) R.string.grid_design_frame_theme else R.string.grid_design_frame_none), Palette.Lavender, m, glyph = Glyph.Highlight) {
                    FolderDesign.save(s.copy(frame = !s.frame))
                    if (s.icon != FolderStyle.ICON_GAME) Toasts.show(Tx.s(R.string.grid_design_frame_note))
                }
                // Undoable, so no question first: the toast offers Undo while the designer is still open.
                "reset" -> DesignRow(Tx.s(R.string.grid_design_reset), if (s.isDefault) Tx.s(R.string.grid_design_reset_following) else null, Palette.Peach, m, glyph = Glyph.Restore) {
                    if (s.isDefault) return@DesignRow
                    val before = s
                    FolderDesign.save(FolderStyle.DEFAULT)
                    Sounds.toggle(false)
                    Undo.offer(Tx.s(R.string.grid_design_reset_done)) { if (FolderDesign.key == key) FolderDesign.save(before) else IconGridStore.setStyle(key, before) }
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
    FolderStyle.ICON_GAME -> if (s.iconRef?.startsWith("app:") == true) Tx.s(R.string.grid_design_app_icon) else FolderLooks.gameOf(plain, s.iconRef)?.title ?: Tx.s(R.string.grid_design_game_cover)
    FolderStyle.ICON_MOSAIC -> Tx.s(R.string.grid_design_cover_mosaic)
    FolderStyle.ICON_FAN -> Tx.s(R.string.grid_design_cover_fan)
    // The glyph's own name (Sword, Puzzle…), as Tandem's icons are named.
    FolderStyle.ICON_GENRE -> Tx.s(R.string.grid_design_genre_glyph, s.glyph ?: genreIcon(plain.title).name)
    FolderStyle.ICON_GLYPH -> Tx.s(R.string.grid_design_glyph)
    FolderStyle.ICON_EMOJI -> s.emoji ?: Tx.s(R.string.grid_design_emoji)
    FolderStyle.ICON_FILE -> s.iconFile?.let { File(it).name } ?: Tx.s(R.string.grid_design_picture)
    else -> webLabel(s.webIcon) ?: Tx.s(R.string.grid_design_theme)
}

private fun heroLabel(plain: GridItem, s: FolderStyle): String = when (s.hero) {
    FolderStyle.HERO_SWEEP -> Tx.s(R.string.grid_design_colour_sweep)
    FolderStyle.HERO_WALL -> Tx.s(R.string.grid_design_cover_wall)
    FolderStyle.HERO_GAME -> FolderLooks.gameOf(plain, s.heroRef)?.title ?: Tx.s(R.string.grid_design_game_hero)
    FolderStyle.HERO_FILE -> s.heroFile?.let { File(it).name } ?: Tx.s(R.string.grid_design_picture)
    else -> webLabel(s.webHero) ?: Tx.s(R.string.grid_design_theme)
}

private fun titleLabel(plain: GridItem, s: FolderStyle): String = when (s.logo) {
    FolderStyle.LOGO_TEXT -> Tx.s(R.string.grid_design_text, weightName(s.textWeight), sizeName(s.textSize))
    FolderStyle.LOGO_GAME -> FolderLooks.gameOf(plain, s.logoRef)?.title?.let { Tx.s(R.string.grid_design_logo, it) } ?: Tx.s(R.string.grid_design_logo_any)
    FolderStyle.LOGO_FILE -> s.logoFile?.let { File(it).name } ?: Tx.s(R.string.grid_design_picture)
    else -> webLabel(s.webLogo) ?: Tx.s(R.string.grid_design_theme)
}

/** A web picture's name ("Console photo · Wikimedia Commons"), for a slot showing one. */
private fun webLabel(id: String?): String? = WebOption.parse(id)?.let(SystemArtUi::optionLabel)

private fun weightName(w: Int) = Tx.s(when { w <= 600 -> R.string.grid_design_weight_light; w >= 900 -> R.string.grid_design_weight_heavy; else -> R.string.grid_design_weight_bold })
private fun sizeName(s: Int) = Tx.s(when (s) { 0 -> R.string.grid_design_size_small; 2 -> R.string.grid_design_size_large; else -> R.string.grid_design_size_medium })

private fun iconId(s: FolderStyle) = when (s.icon) {
    FolderStyle.ICON_AUTO -> if (s.webIcon != null) "web:${s.webIcon}" else if (s.artSet != null) "set:${s.artSet}" else s.icon
    FolderStyle.ICON_GAME -> "ref:${s.iconRef}"
    FolderStyle.ICON_GLYPH -> "glyph:${s.glyph}"
    FolderStyle.ICON_GENRE -> "genre:${s.glyph}"
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
    FolderStyle.HERO_AUTO -> if (s.webHero != null) "web:${s.webHero}" else if (s.artSet != null) "set:${s.artSet}" else s.hero
    FolderStyle.HERO_GAME -> "ref:${s.heroRef}"
    FolderStyle.HERO_FILE -> "file:${s.heroFile}"
    else -> s.hero
}

private fun titleId(s: FolderStyle) = when (s.logo) {
    FolderStyle.LOGO_AUTO -> if (s.webLogo != null) "web:${s.webLogo}" else if (s.artSet != null) "set:${s.artSet}" else s.logo
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
    val choices = remember(page, plain, base, pictures, io.github.zahakj.tandem.Lang.revision.intValue, SystemArtFetch.revision, SystemArtFetch.run?.finished) { choicesFor(ctx, page, plain, base, pictures, pickFile, scope) }
    val route = FolderDesign.pageRoute(key, page)
    val target = rememberFocusTarget(route, choices.map { it.id })
    val state = rememberLazyGridState()
    // The choices in groups (Upload, Made from inside, Covers, Tandem's glyphs, Emoji, Your pictures), each under a
    // small heading that takes a whole line.
    val cells = remember(choices) {
        val out = ArrayList<Pair<Int, Choice?>>()
        var last = 0
        for (c in choices) {
            val sec = choiceSection(page, c.id)
            if (sec != 0 && sec != last) { last = sec; out += sec to null }
            out += sec to c
        }
        out
    }
    KeepInView(state, cells.indexOfFirst { it.second?.id == target })
    val cell = if (wide) 132.dp else 82.dp
    LazyVerticalGrid(
        GridCells.Adaptive(cell + 8.dp),
        Modifier.fillMaxSize().fadingEdges(state), state = state,
        contentPadding = PaddingValues(horizontal = FocusRoomSide, vertical = FocusRoom),
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        for ((sec, choice) in cells) if (choice == null) {
            item(key = "h:$sec", span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                Text(
                    Tx.s(sec), Modifier.padding(start = 4.dp, top = 4.dp), style = MaterialTheme.typography.labelLarge, color = Palette.Muted,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        } else if (choice.id == "device") item(key = choice.id, span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
            // Upload your own: a whole line, with room to say every kind of picture it takes and how big.
            FocusCard(
                Modifier.fillMaxWidth().height(76.dp).takesFocus(route, choice.id, choice.id == target),
                glow = Palette.Mint, shape = RoundedCornerShape(16.dp), onClick = { choice.action?.invoke() },
            ) { UploadCard() }
        } else item(key = choice.id) {
            val c = choice
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
                        FolderDesign.done()
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

/** The heading a choice sits under on its page (a string id), or 0 for none (colours). */
private fun choiceSection(page: FolderDesign.Page, id: String): Int = when {
    page == FolderDesign.Page.Colour -> 0
    // Upload your own says what it is itself.
    id == "device" -> 0
    id == "fetch" || id.startsWith("web:") -> R.string.grid_design_group_web
    id.startsWith("set:") -> R.string.grid_design_group_sets
    id.startsWith("file:") -> R.string.grid_design_group_yours
    id.startsWith("glyph:") -> R.string.grid_design_group_glyphs
    id.startsWith("emoji:") -> R.string.grid_design_group_emoji
    id.startsWith("ref:") -> if (page == FolderDesign.Page.Icon) R.string.grid_design_group_covers else R.string.grid_design_group_game_art
    else -> R.string.grid_design_group_styles
}

private fun choicesFor(
    ctx: Context, page: FolderDesign.Page, plain: GridItem, base: FolderStyle, pictures: List<File>,
    pickFile: (FolderDesign.Page) -> Unit, scope: kotlinx.coroutines.CoroutineScope,
): List<Choice> {
    val strong = FolderLooks.strongGames(plain.games).take(60)
    // First on every picture page: a picture (or a GIF or animated WebP, which moves) from the device.
    // Drawn by UploadCard, a whole line of its own.
    val device = Choice("device", Tx.s(R.string.grid_design_upload), base, action = { pickFile(page) }) { _ -> }
    return when (page) {
        FolderDesign.Page.Icon -> buildList {
            add(device)
            fun icon(id: String, label: String, st: FolderStyle) = add(Choice(id, label, st) { s -> StyledTile(plain, st, s) })
            icon(FolderStyle.ICON_AUTO, Tx.s(R.string.grid_design_theme), base.copy(icon = FolderStyle.ICON_AUTO, iconRef = null, artSet = null, webIcon = null))
            if (FolderLooks.sources(plain, 2).size >= 2) {
                icon(FolderStyle.ICON_MOSAIC, Tx.s(R.string.grid_design_mosaic), base.copy(icon = FolderStyle.ICON_MOSAIC))
                icon(FolderStyle.ICON_FAN, Tx.s(R.string.grid_design_fan), base.copy(icon = FolderStyle.ICON_FAN))
                val g = FolderLooks.glyph(base.glyph) ?: genreIcon(plain.title)
                icon("genre:${g.name}", Tx.s(R.string.grid_design_genre), base.copy(icon = FolderStyle.ICON_GENRE, glyph = g.name))
            }
            webChoices(plain, base, page)?.let { addAll(it) }
            setChoices(plain, base, page)?.let { addAll(it) }
            for (g in strong.filter { Libraries.of(it)?.box(it) != null }.take(18)) icon("ref:game:${g.key}", g.title, base.copy(icon = FolderStyle.ICON_GAME, iconRef = "game:${g.key}"))
            val apps = (plain.art as? TileArt.Folder)?.inside.orEmpty().filterIsInstance<TileArt.App>().take(12)
            for (a in apps) icon("ref:app:${a.pkg}", Tx.s(R.string.grid_design_app_icon), base.copy(icon = FolderStyle.ICON_GAME, iconRef = "app:${a.pkg}"))
            for (t in TIcon.entries) icon("glyph:${t.name}", t.name, base.copy(icon = FolderStyle.ICON_GLYPH, glyph = t.name))
            for (e in Emojis) icon("emoji:$e", e, base.copy(icon = FolderStyle.ICON_EMOJI, emoji = e))
            for (f in pictures) icon("file:${f.path}", f.nameWithoutExtension, base.copy(icon = FolderStyle.ICON_FILE, iconFile = f.path))
        }
        FolderDesign.Page.Colour -> buildList {
            fun swatch(id: String, label: String, st: FolderStyle, c: Color) = add(Choice(id, label, st) { s -> Box(Modifier.size(s * 0.62f).clip(CircleShape).background(c)) })
            swatch("auto", Tx.s(R.string.grid_design_theme), base.copy(color = null, colorFromArt = false), FolderLooks.restyle(plain, base.copy(color = null)).color)
            add(Choice("art", Tx.s(R.string.grid_design_from_art), base, action = {
                val src = FolderLooks.artSource(FolderLooks.restyle(plain, base))
                if (src == null) Toasts.show(Tx.s(R.string.grid_design_no_picture))
                else scope.launch {
                    val col = withContext(Dispatchers.IO) { FolderArtCache.dominant(ctx, src) }
                    if (col == null) Toasts.show(Tx.s(R.string.grid_design_no_colour)) else { FolderDesign.save(FolderDesign.saved.copy(color = col, colorFromArt = true)); FolderDesign.done() }
                }
            }) { s -> StyledTile(plain, base, s * 0.62f) })
            val theme = listOf(Palette.Accent, Palette.Mint, Palette.Peach, Palette.Lavender, Palette.Sky, Palette.Butter)
            for (c in (theme + Swatches.map { Color(it) }).distinct()) {
                val argb = c.toArgb()
                swatch("c:$argb", "", base.copy(color = argb, colorFromArt = false), c)
            }
        }
        FolderDesign.Page.Hero -> buildList {
            add(device)
            fun hero(id: String, label: String, st: FolderStyle) = add(Choice(id, label, st) { _ -> HeroThumb(plain, st) })
            hero(FolderStyle.HERO_AUTO, Tx.s(R.string.grid_design_theme), base.copy(hero = FolderStyle.HERO_AUTO, heroRef = null, heroFile = null, webHero = null))
            hero(FolderStyle.HERO_SWEEP, Tx.s(R.string.grid_design_colour_sweep), base.copy(hero = FolderStyle.HERO_SWEEP))
            if (plain.games.size >= 4) hero(FolderStyle.HERO_WALL, Tx.s(R.string.grid_design_cover_wall), base.copy(hero = FolderStyle.HERO_WALL))
            webChoices(plain, base, page)?.let { addAll(it) }
            setChoices(plain, base, page)?.let { addAll(it) }
            for (g in strong.filter { FolderLooks.gameHero(it) != null }.take(12)) hero("ref:${g.key}", g.title, base.copy(hero = FolderStyle.HERO_GAME, heroRef = g.key))
            for (f in pictures) hero("file:${f.path}", f.nameWithoutExtension, base.copy(hero = FolderStyle.HERO_FILE, heroFile = f.path))
        }
        FolderDesign.Page.Title -> buildList {
            add(device)
            fun title(id: String, label: String, st: FolderStyle) = add(Choice(id, label, st) { _ -> TitleThumb(plain, st) })
            title(FolderStyle.LOGO_AUTO, Tx.s(R.string.grid_design_theme), base.copy(logo = FolderStyle.LOGO_AUTO, logoRef = null, logoFile = null, webLogo = null))
            for (size in 0..2) for (w in listOf(600, 800, 900)) {
                title("text:$w:$size", Tx.s(R.string.grid_design_weight_size, weightName(w), sizeName(size)), base.copy(logo = FolderStyle.LOGO_TEXT, textWeight = w, textSize = size))
            }
            webChoices(plain, base, page)?.let { addAll(it) }
            setChoices(plain, base, page)?.let { addAll(it) }
            for (g in strong.filter { Libraries.of(it)?.logo(it) != null }.take(12)) title("ref:${g.key}", g.title, base.copy(logo = FolderStyle.LOGO_GAME, logoRef = g.key))
            for (f in pictures) title("file:${f.path}", f.nameWithoutExtension, base.copy(logo = FolderStyle.LOGO_FILE, logoFile = f.path))
        }
    }
}

/** Upload your own: what it is and, beside it, every kind of picture it takes and how big, nothing cut off. */
@Composable
private fun UploadCard() {
    Row(
        Modifier.fillMaxSize().background(Palette.CardHigh).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlyphChip(Glyph.Plus, Palette.Mint, 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(0.9f)) {
            ShrinkText(Tx.s(R.string.grid_design_upload), MaterialTheme.typography.titleSmall, Modifier.fillMaxWidth(), maxLines = 1)
            ShrinkText(Tx.s(R.string.grid_design_upload_sub), MaterialTheme.typography.labelMedium, Modifier.fillMaxWidth(), maxLines = 1, color = Palette.Mint)
        }
        Spacer(Modifier.width(12.dp))
        // About a hundred letters on two or three lines (more than half the card's width is theirs).
        Text(
            Tx.s(R.string.grid_design_upload_details), Modifier.weight(1.1f), style = MaterialTheme.typography.labelSmall,
            color = Palette.Muted, maxLines = 3, overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * A system's choices among Tandem's art sets on [page] (null for any other tile): each set, drawn for this system,
 * and, when a set is on for every system, one to keep the pack's art here. A set dresses the icon, hero and logo
 * together; choosing one on a page puts that page's part back to the set (pictures uploaded for the others stay).
 */
private fun setChoices(plain: GridItem, base: FolderStyle, page: FolderDesign.Page): List<Choice>? {
    val id = plain.platformId?.takeIf { plain.kind == TileKind.System } ?: return null
    // A set chosen on a page also puts that page's part back from a web picture to the set.
    fun styled(set: String) = when (page) {
        FolderDesign.Page.Hero -> base.copy(hero = FolderStyle.HERO_AUTO, heroRef = null, heroFile = null, artSet = set, webHero = null)
        FolderDesign.Page.Title -> base.copy(logo = FolderStyle.LOGO_AUTO, logoRef = null, logoFile = null, artSet = set, webLogo = null)
        else -> base.copy(icon = FolderStyle.ICON_AUTO, iconRef = null, iconFile = null, artSet = set, webIcon = null)
    }
    return buildList {
        for (set in ArtSet.entries) add(Choice("set:${set.id}", set.label, styled(set.id)) { s ->
            when (page) {
                FolderDesign.Page.Hero -> SetHeroPreview(set.id, id, modifier = Modifier.fillMaxSize(), logo = false)
                FolderDesign.Page.Title -> Box(Modifier.fillMaxSize().background(lerp(plain.color, Palette.Night, 0.35f)), contentAlignment = Alignment.Center) {
                    SetLogoPreview(set.id, id, Modifier.fillMaxWidth(0.8f))
                }
                else -> Box(Modifier.size(s).clip(RoundedCornerShape(s * 0.2f))) { SetIconPreview(set.id, id) }
            }
        })
        // Also keeps the system out of real art for every system (Look.systemArt).
        if (Store.settings.look.artSet != null || Store.settings.look.systemArt != null) {
            val st = styled(io.github.zahakj.tandem.data.ArtSets.PACK)
            add(Choice("set:${io.github.zahakj.tandem.data.ArtSets.PACK}", Tx.s(R.string.grid_design_set_none), st) { s ->
                when (page) {
                    FolderDesign.Page.Hero -> HeroThumb(plain, st)
                    FolderDesign.Page.Title -> TitleThumb(plain, st)
                    else -> StyledTile(plain, st, s)
                }
            })
        }
    }
}

/**
 * A system's real art on [page] (null for any other tile): Fetch from the web (again), then every picture fetched for
 * it that suits the page, each made as it would show (an icon on the system's colour, a hero, a see-through logo).
 */
private fun webChoices(plain: GridItem, base: FolderStyle, page: FolderDesign.Page): List<Choice>? {
    val id = plain.platformId?.takeIf { plain.kind == TileKind.System } ?: return null
    val slot = when (page) {
        FolderDesign.Page.Hero -> ArtSlot.Hero
        FolderDesign.Page.Title -> ArtSlot.Logo
        FolderDesign.Page.Icon -> ArtSlot.Icon
        else -> return null
    }
    val run = SystemArtFetch.run?.takeIf { !it.finished }
    val options = SystemArt.forSlot(slot, WebArt.available(id, slot).keys)
    return buildList {
        val fetchLabel = when {
            run != null -> Tx.s(R.string.grid_design_web_fetching)
            options.isEmpty() -> Tx.s(R.string.grid_design_web_fetch)
            else -> Tx.s(R.string.grid_design_web_refetch)
        }
        add(Choice("fetch", fetchLabel, base, action = { SystemArtUi.fetch(listOf(id)) }) { s ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (run != null) ProgressBar(run.fraction, Modifier.fillMaxWidth(0.7f))
                else GlyphChip(Glyph.Download, Palette.Peach, if (page == FolderDesign.Page.Icon) s * 0.5f else 40.dp)
            }
        })
        for (o in options) {
            val st = SystemArt.withChoice(base, slot, o.id)
            add(Choice("web:${o.id}", SystemArtUi.optionLabel(o), st) { s -> WebThumb(id, o, slot, plain, s) })
        }
    }
}

/** A fetched picture as [slot] would show it, made in the background: the system's colour until it is ready. */
@Composable
private fun WebThumb(id: String, o: WebOption, slot: ArtSlot, plain: GridItem, s: Dp) {
    val file = remember(id, o, slot, WebArt.revision, SystemArtFetch.revision) { WebArt.file(id, o, slot) }
    val m = when (slot) {
        ArtSlot.Icon -> Modifier.size(s).clip(RoundedCornerShape(s * 0.2f))
        else -> Modifier.fillMaxSize()
    }
    Box(m.background(if (slot == ArtSlot.Logo) lerp(plain.color, Palette.Night, 0.35f) else plain.color), contentAlignment = Alignment.Center) {
        if (file != null) AsyncImage(
            rememberSizedRequest(file, if (slot == ArtSlot.Icon) 256 else 480), null,
            if (slot == ArtSlot.Logo) Modifier.fillMaxWidth(0.8f).fillMaxHeight(0.7f) else Modifier.fillMaxSize(),
            contentScale = if (slot == ArtSlot.Logo) ContentScale.Fit else ContentScale.Crop,
        )
    }
}

/** A small picture of the hero [st] gives. */
@Composable
private fun HeroThumb(plain: GridItem, st: FolderStyle) {
    val item = remember(plain, st) { FolderLooks.restyle(plain, st) }
    val hero = remember(item, SetArt.revision, WebArt.revision) { FolderLooks.hero(item) }
    HeroPicture(item, hero, Modifier.fillMaxSize())
}

@Composable
private fun TitleThumb(plain: GridItem, st: FolderStyle) {
    val item = remember(plain, st) { FolderLooks.restyle(plain, st) }
    Box(Modifier.fillMaxSize().background(lerp(item.color, Palette.Night, 0.35f)), contentAlignment = Alignment.Center) { TitleArt(item, 0.55f) }
}

/** The hero, or the colour sweep (with the folder's glyph) when there is none. */
@Composable
internal fun HeroPicture(item: GridItem, hero: File?, modifier: Modifier) {
    Box(modifier) {
        if (hero != null) AsyncImage(rememberSizedRequest(hero, 640), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else if (item.style?.hero == FolderStyle.HERO_WALL) CoverWall(item.games, item.color, Modifier.fillMaxSize(), moving = false)
        else ColorSweep(item.color, Modifier.fillMaxSize().glyphEcho(FolderLooks.sweepGlyph(item)))
    }
}

/** What stands over the hero: the text title, the logo, or nothing (the icon shows instead). */
@Composable
internal fun TitleArt(item: GridItem, scale: Float) {
    val st = item.style ?: FolderStyle.DEFAULT
    val logo = remember(item, SetArt.revision, WebArt.revision) { FolderLooks.logo(item) }
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
        val hero = remember(preview, SetArt.revision, WebArt.revision) { FolderLooks.hero(preview) }
        Box(
            Modifier.fillMaxWidth().aspectRatio(16f / 9f).graphicsLayer { shape = RoundedCornerShape(14.dp); clip = true },
            contentAlignment = Alignment.Center,
        ) {
            HeroPicture(preview, hero, Modifier.fillMaxSize())
            TitleArt(preview, 0.5f)
        }
        Text(Tx.s(R.string.grid_design_other_screen), Modifier.padding(top = 4.dp), style = MaterialTheme.typography.labelSmall, color = Palette.Muted, maxLines = 1)
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
