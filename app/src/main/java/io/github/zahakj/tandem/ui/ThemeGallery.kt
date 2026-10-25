package io.github.zahakj.tandem.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import io.github.zahakj.tandem.Libraries
import io.github.zahakj.tandem.data.Store
import java.io.File

/*
 * The theme galleries at the top of Appearance: built-in themes and theme packs, each card a live miniature of the
 * theme (its own backdrop, real system tiles in its icon style, its colours). The highlighted card's backdrop
 * moves; the others hold still so a row of them stays cheap.
 */

private val LocalGalleryRow = compositionLocalOf<RowFocus?> { null }

/** Joins the gallery row's focus memory (see [GalleryRow]). */
@Composable
fun Modifier.galleryItem(key: String): Modifier = LocalGalleryRow.current?.let { rowItem(it, key) } ?: this

/** Focus keys of the built-in theme cards, in order. */
fun builtInThemeKeys(): List<String> = BuiltInThemes.list.map { "theme:${it.id}" }

/** Focus keys of the pack row: each pack, then the card that opens the pack manager. */
fun packThemeKeys(): List<String> = ThemePacks.packs.map { "theme:${it.id}" } + MANAGE_PACKS_KEY

const val MANAGE_PACKS_KEY = "packs:manage"

/**
 * Uses theme [id]. A pack comes in whole, like in the app it was made for: its wallpapers go on both screens and its
 * own icons, heroes and logos are used (its switches in Theme packs still apply).
 */
fun applyTheme(id: String) {
    val pack = ThemePacks.byId(id)
    Store.updateSettings { s ->
        var l = s.look.copy(theme = id)
        if (pack != null) {
            if (pack.spec.wallpaperTop != null) l = l.copy(wallTop = "theme")
            if (pack.spec.wallpaperBottom != null) l = l.copy(wallBottom = "theme")
            if (!pack.art.isEmpty) l = l.copy(iconPack = "auto")
        }
        s.copy(look = l)
    }
}

/** The built-in themes, game-flavoured first. */
@Composable
fun BuiltInThemeRow(current: String, target: String?, onFocusKey: (String) -> Unit) {
    val keys = builtInThemeKeys()
    GalleryRow("themes", keys, target) {
        items(BuiltInThemes.list, key = { it.id }) { t ->
            val key = "theme:${t.id}"
            ThemeCard(t, null, t.id == current, key, key == target, onFocusKey) { applyTheme(t.id) }
        }
    }
}

/** Installed theme packs, and a card for the pack manager (import, remove, choose parts). */
@Composable
fun PackThemeRow(current: String, target: String?, onFocusKey: (String) -> Unit) {
    val packs = ThemePacks.packs
    GalleryRow("packs", packThemeKeys(), target) {
        items(packs, key = { it.id }) { p ->
            val key = "theme:${p.id}"
            val spec = Themes.withParts(p.spec, Store.settings.look)
            ThemeCard(spec, p, p.id == current, key, key == target, onFocusKey) { applyTheme(p.id) }
        }
        item(key = MANAGE_PACKS_KEY) {
            FocusCard(
                Modifier.width(CardWidth).height(CardHeight).galleryItem(MANAGE_PACKS_KEY).takesFocus(Route.Appearance.key, MANAGE_PACKS_KEY, target == MANAGE_PACKS_KEY),
                glow = Palette.Accent, shape = RoundedCornerShape(18.dp),
                onFocus = { onFocusKey(MANAGE_PACKS_KEY) }, onClick = { Nav.go(Route.ThemePacks) },
            ) {
                Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    GlyphChip(Glyph.Download, Palette.Accent, 40.dp)
                    Spacer(Modifier.height(8.dp))
                    Text(if (packs.isEmpty()) "Add theme packs" else "Manage packs", style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(if (packs.isEmpty()) "Import a folder or .zip" else "${packs.size} installed", style = MaterialTheme.typography.labelMedium, color = Palette.Muted, maxLines = 1)
                }
            }
        }
    }
}

private val CardWidth = 176.dp
private val CardHeight = 138.dp

/**
 * A row of gallery cards that keeps the focus target composed and in view (a target scrolled off the row could
 * never take focus, which left nothing highlighted), remembers its scroll position, and fades at its edges.
 */
@Composable
fun GalleryRow(
    name: String,
    keys: List<String>,
    target: String?,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    val state = rememberRouteListState("${Route.Appearance.key}/$name")
    KeepInView(state, keys.indexOf(target))
    // Left/Right stop at the row's ends instead of jumping to the top bar, and coming back from the rows
    // above or below lands on the card last highlighted here.
    val memory = remember(name) { RowFocus() }
    CompositionLocalProvider(LocalGalleryRow provides memory) {
        LazyRow(
            Modifier.fillMaxWidth().fadingEdges(state, horizontal = true).focusRow(memory),
            state = state,
            contentPadding = PaddingValues(horizontal = FocusRoomSide, vertical = FocusRoom),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            content = content,
        )
    }
}

/** One theme as a card: a live miniature on top, its name on its own card colour below. */
@Composable
private fun ThemeCard(
    t: ThemeSpec,
    pack: ThemePack?,
    inUse: Boolean,
    key: String,
    wanted: Boolean,
    onFocusKey: (String) -> Unit,
    onClick: () -> Unit,
) {
    FocusCard(
        Modifier.width(CardWidth).height(CardHeight).galleryItem(key).takesFocus(Route.Appearance.key, key, wanted),
        glow = t.accent, shape = RoundedCornerShape(18.dp),
        onFocus = { onFocusKey(key) }, onClick = onClick,
    ) { focused ->
        Column(Modifier.fillMaxSize()) {
            ThemeMiniature(t, pack, Modifier.fillMaxWidth().weight(1f), moving = focused)
            Row(
                Modifier.fillMaxWidth().background(t.card).padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(t.name, style = MaterialTheme.typography.labelLarge, color = t.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (pack != null && pack.author.isNotBlank()) {
                        Text("by ${pack.author}", style = MaterialTheme.typography.labelSmall, color = t.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (inUse) Box(Modifier.size(9.dp).clip(CircleShape).background(t.accent))
            }
        }
    }
}

/** Systems to show on previews: the user's own first, then a few well-known ones. */
@Composable
fun previewSystems(n: Int = 3): List<Pair<String, String>> {
    val libs = Libraries.all
    return remember(libs) {
        val own = libs.orEmpty().filter { !it.platform.isApps }.map { it.platform.id to it.platform.shortName }
        (own + listOf("n3ds" to "3DS", "gba" to "GBA", "psx" to "PS1", "snes" to "SNES", "switch" to "Switch", "psp" to "PSP"))
            .distinctBy { it.first }.take(n.coerceAtLeast(3) + 3)
    }
}

/**
 * [t] in miniature: its wallpaper or backdrop, three real system tiles (the pack's icons, or drawn in the theme's
 * icon style), and the theme's screen overlay. Used by the gallery, the side panel and the pack manager.
 */
@Composable
fun ThemeMiniature(t: ThemeSpec, pack: ThemePack?, modifier: Modifier = Modifier, moving: Boolean = false, tile: Dp = 40.dp) {
    val systems = previewSystems()
    val parts = pack?.let { ThemePacks.parts(it.id) }
    Box(modifier.background(t.backgroundDeep)) {
        val picture: File? = pack?.preview ?: t.wallpaperTop?.takeIf { it.extension.lowercase() in ThemePacks.imageExtensions }
        if (picture != null) {
            AsyncImage(picture, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Backdrop(t.backdrop, t.lavender, Modifier.fillMaxSize(), theme = t, moving = moving)
        }
        if (pack?.preview == null) {
            val icons: List<File?> = if (pack != null && parts?.icons != false) {
                systems.mapNotNull { (id, _) -> pack.art.system(ThemePacks.systemIds(id), "icon") }.take(3)
            } else emptyList()
            Row(Modifier.align(Alignment.Center), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (icons.size >= 3) {
                    icons.forEach { f ->
                        AsyncImage(f, null, Modifier.size(tile).clip(RoundedCornerShape(tile * (t.corner / 104f).coerceIn(0.05f, 0.3f))), contentScale = ContentScale.Crop)
                    }
                } else {
                    systems.take(3).forEachIndexed { i, (id, short) ->
                        val color = t.accents[(id.hashCode() and 0x7fffffff) % t.accents.size]
                        DrawnIcon(short, Devices.of(id), color, tile, style = t.iconStyle, theme = t, showLabel = tile >= 36.dp)
                    }
                }
            }
        }
        if (t.overlay != ScreenOverlay.None) MiniOverlay(t)
    }
}

@Composable
private fun MiniOverlay(t: ThemeSpec) {
    androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
        val step = 3f * density
        var y = 0f
        val ink = if (t.overlay == ScreenOverlay.Scanlines) Color.Black else t.backgroundDeep
        while (y < size.height) { drawRect(ink, androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Size(size.width, density * 0.8f), alpha = 0.16f); y += step }
    }
}
