package io.github.zahakj.tandem.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import io.github.zahakj.tandem.Bindings
import io.github.zahakj.tandem.Command
import io.github.zahakj.tandem.MainActivity
import io.github.zahakj.tandem.R
import io.github.zahakj.tandem.Selected
import io.github.zahakj.tandem.Selection
import io.github.zahakj.tandem.Tx
import io.github.zahakj.tandem.data.ControllerStore
import io.github.zahakj.tandem.data.Look
import io.github.zahakj.tandem.data.Store
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Date

internal fun updateLook(change: (Look) -> Look) = Store.updateSettings { it.copy(look = change(it.look)) }

/** The item after (or before, for [step] = -1) [current] in [list], wrapping around. */
private fun <T> stepIn(list: List<T>, current: T, step: Int): T {
    if (list.isEmpty()) return current
    val i = list.indexOf(current).let { if (it < 0) 0 else it }
    return list[((i + step) % list.size + list.size) % list.size]
}

/**
 * A row that picks one of [options] (id to label), showing them all (Choices.kt): a few short ones as a segmented
 * control on the row, where A steps on; more as the value, where A opens the picker. Left and Right move along the
 * options and stop at the ends. [look] draws one option as a miniature and [details] gives some a line: the
 * picker's cards show them; without a [preview] of its own the side panel plays every option in turn
 * ([ChoiceShowcase]).
 */
internal fun choice(
    label: String, icon: Glyph, color: Color, help: String,
    options: List<Pair<String, String>>, current: String,
    preview: (@Composable () -> Unit)? = null,
    look: (@Composable (String) -> Unit)? = null,
    details: Map<String, String>? = null,
    extra: Pair<String, () -> Unit>? = null,
    en: String = label,
    set: (String) -> Unit,
): SettingRow {
    val ids = options.map { it.first }
    val at = ids.indexOf(current).coerceAtLeast(0)
    fun move(d: Int) { ids.getOrNull(at + d)?.let(set) }
    lateinit var row: SettingRow
    row = SettingRow(
        label, options.firstOrNull { it.first == current }?.second ?: options.first().second, color, icon, help,
        onLeft = { move(-1) }, onRight = { move(1) },
        preview = preview ?: look?.let { l -> { ChoiceShowcase(options, current, color) { l(it) } } },
        options = options, selected = current, pick = set, optionLook = look, optionDetails = details, pickerExtra = extra, en = en,
        onClick = { if (row.picker) ChoicePicker.open(row) else set(stepIn(ids, current, 1)) },
    )
    return row
}

/** A row for a number. `null` means "the theme decides", shown as [themeLabel] and reachable by stepping below [min]. */
internal fun number(
    label: String, icon: Glyph, color: Color, help: String,
    value: Float?, min: Float, max: Float, step: Float, format: (Float) -> String,
    themeValue: Float? = null,
    preview: (@Composable () -> Unit)? = null,
    en: String = label,
    set: (Float?) -> Unit,
): SettingRow {
    val nullable = themeValue != null
    // Left/right stop at the ends (and step onto "Theme" below the minimum); A keeps going round.
    fun move(d: Int, wrap: Boolean) {
        val cur = value ?: themeValue ?: min
        val next = cur + d * step
        when {
            nullable && value == null && d < 0 -> if (wrap) set(max)
            nullable && value == null -> set((Math.round(next / step) * step).coerceIn(min, max))
            nullable && next < min - step / 2 -> set(null)
            next > max + step / 2 -> if (wrap) set(if (nullable) null else min)
            next < min - step / 2 -> if (wrap) set(max)
            else -> set((Math.round(next / step) * step).coerceIn(min, max))
        }
    }
    val shown = format(value ?: themeValue ?: min)
    val at = ((value ?: themeValue ?: min) - min) / (max - min).coerceAtLeast(0.0001f)
    return SettingRow(
        label, shown, color, icon, help,
        onLeft = { move(-1, wrap = false) }, onRight = { move(1, wrap = false) }, preview = preview, onClick = { move(1, wrap = true) },
        slider = at, themed = if (nullable) value == null else null, en = en,
    )
}

/**
 * A switch. [look] draws the setting's effect Off and On: without a [preview] of its own, the side panel plays the
 * two in turn ([ToggleShowcase]), and switching shows the new look at once.
 */
internal fun toggle(
    label: String, icon: Glyph, color: Color, help: String, on: Boolean,
    preview: (@Composable () -> Unit)? = null,
    look: (@Composable (Boolean) -> Unit)? = null,
    en: String = label,
    set: (Boolean) -> Unit,
) = SettingRow(
    label, onOff(on), color, icon, help, toggle = on,
    preview = preview ?: look?.let { l -> { ToggleShowcase(on, color) { l(it) } } },
    en = en,
    onClick = { set(!on) },
)

internal fun pct(v: Float) = Tx.s(R.string.common_percent, Math.round(v * 100))

/** [Look]'s defaults for everything about how Tandem looks, keeping [old]'s buttons, screens, sounds and music. */
internal fun resetLook(old: Look): Look = Look().copy(
    prompts = old.prompts, swapAB = old.swapAB, swapXY = old.swapXY, xButton = old.xButton,
    singleScreen = old.singleScreen, swapScreens = old.swapScreens,
    sounds = old.sounds, soundVolume = old.soundVolume, soundPack = old.soundPack, soundFiles = old.soundFiles,
    music = old.music, musicVolume = old.musicVolume, myMusic = old.myMusic, soundtrack = old.soundtrack, soundtrackPicked = old.soundtrackPicked, musicShuffle = old.musicShuffle,
    pipVoice = old.pipVoice, packParts = old.packParts,
)

/** Home row orders on offer: ids in order, and a friendly name. */
private val HomeRowOrders = listOf(
    listOf("continue", "systems", "collections"),
    listOf("systems", "continue", "collections"),
    listOf("systems", "collections", "continue"),
    listOf("continue", "systems"),
    listOf("systems", "collections"),
    listOf("systems"),
)

private fun homeLabel(ids: List<String>) = ids.joinToString(" · ") {
    when (it) {
        "continue" -> Tx.s(R.string.look_home_rows_continue); "systems" -> Tx.s(R.string.look_home_rows_systems)
        "collections" -> Tx.s(R.string.look_home_rows_collections); else -> it
    }
}

/** The icon grid home's settings (the "grid" layout). */
private fun iconGridSettingRows(look: Look): List<SettingRow> {
    val preview: @Composable () -> Unit = { LayoutPreview("grid", Modifier.fillMaxWidth().aspectRatio(1.6f)) }
    /** The grid with one setting changed, filling a showcase frame or a picker card. */
    val grid: @Composable (Look) -> Unit = { l ->
        LayoutPreview("grid", Modifier.fillMaxSize(), rounded = false, look = l, focusLabel = io.github.zahakj.tandem.data.ExtrasStore.settings.focusLabels)
    }
    return listOf(
        choice(
            Tx.s(R.string.look_grid_icon_size), Glyph.Grid, Palette.Sky, Tx.s(R.string.look_grid_icon_size_help),
            (5..9).map { "$it" to Tx.s(R.string.look_grid_icon_size_option, IconGrid.sizeName(it), it) }, look.gridAcross.toString(),
            look = { v -> grid(look.copy(gridAcross = v.toInt(), gridRows = 0)) },
            en = "Icon size",
        ) { v -> updateLook { it.copy(gridAcross = v.toInt()) } },
        choice(
            Tx.s(R.string.look_grid_rows), Glyph.Grid, Palette.Sky, Tx.s(R.string.look_grid_rows_help),
            listOf("0" to Tx.s(R.string.look_grid_rows_auto)) + (1..5).map { "$it" to Tx.p(R.plurals.look_grid_rows_n, it) }, look.gridRows.toString(),
            look = { v -> grid(look.copy(gridRows = v.toInt())) },
            en = "Rows of icons",
        ) { v -> updateLook { it.copy(gridRows = v.toInt()) } },
        choice(
            Tx.s(R.string.look_grid_scrolling), Glyph.Motion, Palette.Sky, Tx.s(R.string.look_grid_scrolling_help),
            listOf(
                "horizontal" to Tx.s(R.string.look_grid_scrolling_sideways), "paged" to Tx.s(R.string.look_grid_scrolling_pages),
                "vertical" to Tx.s(R.string.look_grid_scrolling_vertical),
            ),
            look.gridScroll,
            look = { v -> grid(look.copy(gridScroll = v)) },
            en = "Scrolling",
        ) { v -> updateLook { it.copy(gridScroll = v) } },
        // Corners from sharp to round: a slider with a name for each step, and real tiles in the side panel that ease to
        // each new shape (a tile can keep its own corners: Y › Edit › Look).
        number(
            Tx.s(R.string.look_grid_roundness), Glyph.Corners, Palette.Sky, Tx.s(R.string.look_grid_roundness_help),
            look.gridRoundness, 0f, 0.5f, 0.05f, { cornerName(it) },
            preview = { CornerPreview(look.gridRoundness, Modifier.fillMaxWidth().aspectRatio(1.6f)) }, en = "Icon roundness",
        ) { v -> updateLook { it.copy(gridRoundness = v ?: 0.2f) } },
        choice(
            Tx.s(R.string.look_grid_animated), Glyph.Image, Palette.Sky, Tx.s(R.string.look_grid_animated_help),
            listOf(
                AnimatedArt.ALWAYS to Tx.s(R.string.look_grid_animated_always), AnimatedArt.FOCUSED to Tx.s(R.string.look_grid_animated_focused),
                AnimatedArt.OFF to Tx.s(R.string.look_grid_animated_off),
            ),
            io.github.zahakj.tandem.data.ExtrasStore.settings.animatedArt,
            en = "Animated icons",
        ) { v -> io.github.zahakj.tandem.data.ExtrasStore.update { it.copy(animatedArt = v) } },
        number(
            Tx.s(R.string.look_grid_gap), Glyph.Grid, Palette.Sky, Tx.s(R.string.look_grid_gap_help),
            look.gridGap, 0.1f, 0.5f, 0.04f, ::pct, preview = preview, en = "Space between icons",
        ) { v -> updateLook { it.copy(gridGap = v ?: 0.26f) } },
        number(
            Tx.s(R.string.look_grid_hero_split), Glyph.Screens, Palette.Sky, Tx.s(R.string.look_grid_hero_split_help),
            look.gridHeroSplit, 0f, 0.5f, 0.05f, { if (it <= 0f) Tx.s(R.string.look_grid_hero_split_none) else pct(it) }, preview = preview,
            en = "Hero split",
        ) { v -> updateLook { it.copy(gridHeroSplit = v ?: 0f) } },
        choice(
            Tx.s(R.string.look_grid_other_screen), Glyph.Screens, Palette.Sky, Tx.s(R.string.look_grid_other_screen_help),
            listOf("spotlight" to Tx.s(R.string.look_grid_other_screen_spotlight), "classic" to Tx.s(R.string.look_grid_other_screen_classic)), look.gridDetails,
            look = { v -> DeviceScene(Shows.Library, if (v == "spotlight") Shows.Spotlight else Shows.Details) },
            en = "Other screen",
        ) { v -> updateLook { it.copy(gridDetails = v) } },
        choice(
            Tx.s(R.string.look_grid_folders), Glyph.Folder, Palette.Sky, Tx.s(R.string.look_grid_folders_help),
            listOf("popup" to Tx.s(R.string.look_grid_folders_popup), "fullscreen" to Tx.s(R.string.look_grid_folders_page)), look.gridFolders,
            look = { v -> FolderOpenScene(v) },
            en = "Folders open",
        ) { v -> updateLook { it.copy(gridFolders = v) } },
        choice(
            Tx.s(R.string.look_grid_x_button), Glyph.Swap, Palette.Sky, Tx.s(R.string.look_grid_x_button_help),
            listOf(
                "swap-grid" to Tx.s(R.string.look_grid_x_button_swap_grid), "swap" to Tx.s(R.string.look_grid_x_button_swap),
                "search" to Tx.s(R.string.look_grid_x_button_search),
            ),
            look.xButton,
            en = "X button",
        ) { v -> updateLook { it.copy(xButton = v) } },
        choice(
            Tx.s(R.string.look_grid_swap_animation), Glyph.Swap, Palette.Sky, Tx.s(R.string.look_grid_swap_animation_help),
            listOf(
                "scatter" to Tx.s(R.string.look_swap_scatter), "push" to Tx.s(R.string.look_swap_push), "flip" to Tx.s(R.string.look_swap_flip),
                "depth" to Tx.s(R.string.look_swap_depth), "fade" to Tx.s(R.string.look_swap_fade), "instant" to Tx.s(R.string.look_swap_instant),
            ),
            look.swapStyle,
            look = { v -> SwapScene(v) },
            en = "Swap animation",
        ) { v -> updateLook { it.copy(swapStyle = v) } },
    )
}

/** A corner rounding (a share of the side, 0 … 0.5) as a word and its amount: "Rounded · 20%". */
internal fun cornerName(v: Float): String {
    val word = Tx.s(
        when {
            v < 0.025f -> R.string.look_grid_corner_sharp
            v < 0.075f -> R.string.look_grid_corner_crisp
            v < 0.125f -> R.string.look_grid_corner_soft
            v < 0.225f -> R.string.look_grid_corner_rounded
            v < 0.325f -> R.string.look_grid_corner_round
            v < 0.475f -> R.string.look_grid_corner_pebble
            else -> R.string.look_grid_corner_circle
        },
    )
    return listOf(word, pct(v)).joinParts()
}

/**
 * Icon roundness, live: a few tiles in the theme's colours (the middle one ringed, as the highlight draws it) whose
 * corners ease to each new value as Left and Right step it, with the name of the shape under them.
 */
@Composable
private fun CornerPreview(value: Float, modifier: Modifier) {
    val share by androidx.compose.animation.core.animateFloatAsState(value.coerceIn(0f, 0.5f), Motion.focus(), label = "corner-preview")
    val t = Themes.live
    val colors = listOf(t.mint, t.accent, t.peach)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Canvas(Modifier.fillMaxWidth().weight(1f)) {
            val gap = size.width * 0.06f
            val tw = minOf((size.width - gap * 4) / 3f, size.height * 0.8f)
            val left = (size.width - (tw * 3 + gap * 2)) / 2f
            val top = (size.height - tw) / 2f
            for (i in 0 until 3) {
                val x = left + i * (tw + gap)
                val c = colors[i]
                val r = CornerRadius(tw * share)
                drawRoundRect(
                    Brush.linearGradient(listOf(androidx.compose.ui.graphics.lerp(c, Color.White, 0.18f), c, androidx.compose.ui.graphics.lerp(c, Color.Black, 0.25f)), Offset(x, top), Offset(x + tw, top + tw)),
                    Offset(x, top), Size(tw, tw), r,
                )
                if (i == 1) {
                    val o = tw * 0.06f
                    drawRoundRect(t.text, Offset(x - o, top - o), Size(tw + o * 2, tw + o * 2), CornerRadius(r.x + o), style = Stroke(tw * 0.025f))
                }
            }
        }
        Text(cornerName(value), style = MaterialTheme.typography.labelLarge, color = Palette.Muted, maxLines = 1)
    }
}

/** The Look & feel page of Settings (it kept its old route, so links to "Appearance" still land here). */
@Composable
fun AppearanceScreen() {
    IconPacksDriver()
    val look = Store.settings.look
    // The first visit opens on the theme in use.
    remember { if (FocusMemory.get(Route.Appearance.key) == null) FocusMemory.remember(Route.Appearance.key, "theme:${look.theme}") }
    LaunchedEffect(Unit) { ThemePacks.refresh() }
    // After a theme change has faded in, make sure the highlighted card still holds the focus (and is in view).
    LaunchedEffect(look.theme) {
        delay(600)
        Nav.focusTick++
    }
    SettingsPage("look")
}

/**
 * Every appearance section, as the old Appearance screen listed them. Settings › Look & feel, Home & layout, Two
 * screens, Controls and Sound pick their groups out of these by title and label (see SettingsCatalog.kt); a row
 * added here that no page picks still shows, under "Not placed" on Play & system (SettingsPlanTest catches it first).
 */
@Composable
fun appearanceSections(): List<SettingSection> {
    val ctx = LocalContext.current
    val activity = ctx as? MainActivity
    val look = Store.settings.look
    val settings = Store.settings
    val bindings = ControllerStore.live
    val rebound = Bindings.changed(bindings)
    val base = Themes.byId(look.theme) ?: BuiltInThemes.PastelNight
    // Read in the background (Facts), never while composing: Settings opens without touching the disk.
    Facts.ensure(ctx)
    val wallFiles = Facts.wallpapers
    val trackCount = Facts.musicTracks

    val wallOptions = buildList {
        add("theme" to Tx.s(R.string.look_wall_theme))
        BackdropStyle.entries.forEach { add(it.id to it.label) }
        add("hero" to Tx.s(R.string.look_wall_hero))
        wallFiles.forEach { add("file:${it.path}" to it.name) }
    }
    val accentOptions = listOf("" to Tx.s(R.string.look_accent_theme)) + AccentSwatches.map { it.second.toHex() to Tx.s(it.first) } +
        (look.accent?.takeIf { a -> AccentSwatches.none { it.second.toHex() == a } }?.let { listOf(it to it) } ?: emptyList())

    return listOf(
        SettingSection(Tx.s(R.string.look_group_colour), en = "Colour & highlight", rows = listOf(
            choice(
                Tx.s(R.string.look_accent), Glyph.Sparkle, Palette.Accent, Tx.s(R.string.look_accent_help),
                accentOptions, look.accent ?: "",
                preview = { AccentPreview() },
                look = { id -> AccentScene(Themes.resolve(look.copy(accent = id.ifEmpty { null })).accent) },
                extra = Tx.s(R.string.look_accent_type) to {
                    TextEntry.ask(Tx.s(R.string.look_accent_hex), look.accent ?: "#") { text ->
                        val c = parseColor(text)
                        if (c == null) { Toasts.show(Tx.s(R.string.look_accent_bad)); Sounds.error() }
                        else updateLook { it.copy(accent = c.toHex(), highlightColor = "accent") }
                    }
                },
                en = "Accent colour",
            ) { id ->
                if (id.isEmpty()) updateLook { it.copy(accent = null, highlightColor = "item") }
                else updateLook { it.copy(accent = id, highlightColor = "accent") }
            },
            choice(
                Tx.s(R.string.look_highlight), Glyph.Highlight, Palette.Lavender, Tx.s(R.string.look_highlight_help),
                listOf("" to Tx.s(R.string.look_theme_decides, base.highlight.label)) + HighlightStyle.entries.map { it.id to it.label }, look.highlight ?: "",
                look = { v -> HighlightScene(HighlightStyle.of(v) ?: base.highlight) },
                en = "Highlight style",
            ) { v -> updateLook { it.copy(highlight = v.ifEmpty { null }) } },
            choice(
                Tx.s(R.string.look_surface), Glyph.Layers, Palette.Sky, Tx.s(R.string.look_surface_help),
                listOf("" to Tx.s(R.string.look_theme_decides, base.material.label)) + SurfaceMaterial.entries.map { it.id to it.label }, look.material ?: "",
                look = { v -> SurfaceScene(SurfaceMaterial.of(v) ?: base.material) },
                en = "Surface",
            ) { v -> updateLook { it.copy(material = v.ifEmpty { null }) } },
        )),
        SettingSection(Tx.s(R.string.look_group_icons), en = "System icons", rows = listOf(
            choice(
                Tx.s(R.string.look_icon_style), Glyph.Gamepad, Palette.Accent, Tx.s(R.string.look_icon_style_help),
                listOf("" to Tx.s(R.string.look_theme_decides, base.iconStyle.label)) + IconStyle.entries.map { it.id to it.label }, look.iconStyle ?: "",
                look = { v -> IconStyleScene(IconStyle.of(v) ?: base.iconStyle) },
                en = "Icon style",
            ) { v -> updateLook { it.copy(iconStyle = v.ifEmpty { null }) } },
            choice(
                Tx.s(R.string.look_icons_from), Glyph.Image, Palette.Accent, Tx.s(R.string.look_icons_from_help),
                ThemePacks.iconSources(), look.iconPack,
                preview = { IconsPreview() },
                en = "Icons from",
            ) { v -> updateLook { it.copy(iconPack = v) } },
            choice(
                Tx.s(R.string.look_art_set), Glyph.Sparkle, Palette.Lavender, Tx.s(R.string.look_art_set_help),
                listOf("" to Tx.s(R.string.look_art_set_off)) + ArtSet.entries.map { it.id to it.label }, look.artSet ?: "",
                look = { v -> ArtSetScene(v) },
                en = "Art set",
            ) { v ->
                updateLook { it.copy(artSet = v.ifEmpty { null }) }
                SetArt.warmLibrary()
            },
        ) + SystemArtUi.rows()),
        SettingSection(Tx.s(R.string.look_group_motion), en = "Motion", rows = listOf(
            choice(
                Tx.s(R.string.look_animation), Glyph.Motion, Palette.Mint, Tx.s(R.string.look_animation_help),
                listOf("" to Tx.s(R.string.look_theme_decides, base.animation.label)) + AnimationStyle.entries.map { it.id to it.label }, look.motion ?: "",
                look = { v -> MotionStyleScene(AnimationStyle.of(v) ?: base.animation) },
                en = "Animation style",
            ) { v -> updateLook { it.copy(motion = v.ifEmpty { null }) } },
            toggle(
                Tx.s(R.string.look_reduce_motion), Glyph.Motion, Palette.Mint, Tx.s(R.string.look_reduce_motion_help),
                settings.reduceMotion,
                look = { on -> StillnessScene(on) },
                en = "Reduce motion",
            ) { on -> Store.updateSettings { it.copy(reduceMotion = on) } },
            toggle(
                Tx.s(R.string.look_travel), Glyph.Highlight, Palette.Mint, Tx.s(R.string.look_travel_help),
                look.highlightTravel,
                look = { on -> TravelScene(on) },
                en = "Travelling highlight",
            ) { on -> updateLook { it.copy(highlightTravel = on) } }.copy(keywords = "highlight ring cursor focus travel glide spring slide move lift icon grid"),
        )),
        SettingSection(Tx.s(R.string.look_group_wallpaper), en = "Wallpaper", rows = listOf(
            choice(
                Tx.s(R.string.look_wall_top), Glyph.Image, Palette.Peach, Tx.s(R.string.look_wall_top_help),
                wallOptions, look.wallTop.takeIf { w -> wallOptions.any { it.first == w } } ?: "theme", // tx-ok: an option id
                preview = { WallpaperPreview(look.wallTop, ScreenSlot.Top) },
                look = { v -> WallpaperArt(v, ScreenSlot.Top, moving = false) },
                en = "Top screen",
            ) { v -> updateLook { it.copy(wallTop = v) } },
            choice(
                Tx.s(R.string.look_wall_bottom), Glyph.Image, Palette.Peach, Tx.s(R.string.look_wall_bottom_help),
                wallOptions, look.wallBottom.takeIf { w -> wallOptions.any { it.first == w } } ?: "theme", // tx-ok: an option id
                preview = { WallpaperPreview(look.wallBottom, ScreenSlot.Bottom) },
                look = { v -> WallpaperArt(v, ScreenSlot.Bottom, moving = false) },
                en = "Bottom screen",
            ) { v -> updateLook { it.copy(wallBottom = v) } },
            number(
                Tx.s(R.string.look_wall_dim), Glyph.Image, Palette.Peach, Tx.s(R.string.look_wall_dim_help),
                look.wallDim, 0f, 0.8f, 0.05f, ::pct,
                preview = { Box(Modifier.fillMaxWidth().aspectRatio(1.6f).clip(RoundedCornerShape(16.dp))) { DimScene(look.wallDim) } },
                en = "Picture dimming",
            ) { v -> updateLook { it.copy(wallDim = v ?: 0.35f) } },
        )),
        SettingSection(Tx.s(R.string.look_group_layout), en = "Layout", rows = listOf(
            choice(
                Tx.s(R.string.look_games_view), Glyph.Grid, Palette.Butter, Tx.s(R.string.look_games_view_help),
                GameViews, look.gameView,
                look = { v -> LayoutPreview("view-$v", Modifier.fillMaxSize(), rounded = false) },
                en = "Games view",
            ) { v -> updateLook { it.copy(gameView = v) } },
            choice(
                Tx.s(R.string.look_games_grid_size), Glyph.Grid, Palette.Butter, Tx.s(R.string.look_games_grid_size_help),
                Grid.sizes, look.gridSize,
                look = { v -> GridPreview(size = v) },
                en = "Grid size",
            ) { v -> updateLook { it.copy(gridSize = v) } },
            choice(
                Tx.s(R.string.look_games_card_shape), Glyph.Corners, Palette.Butter, Tx.s(R.string.look_games_card_shape_help),
                Grid.shapes, look.cardShape,
                look = { v -> GridPreview(shape = v) },
                en = "Card shape",
            ) { v -> updateLook { it.copy(cardShape = v) } },
            choice(
                Tx.s(R.string.look_games_shelf_look), Glyph.Layers, Palette.Butter, Tx.s(R.string.look_games_shelf_look_help),
                ShelfLooks.choices, ShelfLooks.current(),
                look = { v -> LayoutPreview("view-shelf", Modifier.fillMaxSize(), rounded = false, look = look.copy(shelfLook = v)) },
                en = "Shelf look",
            ) { v -> updateLook { it.copy(shelfLook = v) } },
            choice(
                Tx.s(R.string.look_home_system_tiles), Glyph.Highlight, Palette.Butter, Tx.s(R.string.look_home_system_tiles_help),
                Grid.tileStyles, look.systemTiles,
                look = { v -> SystemTilesScene(v) },
                en = "System tiles",
            ) { v -> updateLook { it.copy(systemTiles = v) } },
            toggle(
                Tx.s(R.string.look_games_titles), Glyph.Text, Palette.Butter, Tx.s(R.string.look_games_titles_help), settings.showTitles,
                look = { on -> GridPreview(titles = on) },
                en = "Game titles",
            ) { on -> Store.updateSettings { it.copy(showTitles = on) } },
            choice(
                Tx.s(R.string.look_home_rows), Glyph.Home, Palette.Butter, Tx.s(R.string.look_home_rows_help),
                HomeRowOrders.map { it.joinToString(",") to homeLabel(it) }, look.homeSections.joinToString(","),
                look = { v -> HomeRowsScene(v.split(",")) },
                en = "Home rows",
            ) { v -> updateLook { it.copy(homeSections = v.split(",")) } },
            choice(
                Tx.s(R.string.look_home_open_folders), Glyph.Folder, Palette.Butter, Tx.s(R.string.look_home_open_folders_help),
                listOf(
                    "page" to Tx.s(R.string.look_home_open_folders_page), "fullscreen" to Tx.s(R.string.look_home_open_folders_fullscreen),
                    "popup" to Tx.s(R.string.look_home_open_folders_popup),
                ),
                when { look.folderPopup -> "popup"; look.fullscreenFolders -> "fullscreen"; else -> "page" },
                look = { v -> FolderOpenScene(v) },
                en = "Open folders",
            ) { v -> updateLook { it.copy(folderPopup = v == "popup", fullscreenFolders = v == "fullscreen") } },
            toggle(
                Tx.s(R.string.look_home_now_playing), Glyph.Gamepad, Palette.Butter, Tx.s(R.string.look_home_now_playing_help), look.nowPlaying,
                look = { on -> NowPlayingScene(on) },
                en = "Now playing",
            ) { on -> updateLook { it.copy(nowPlaying = on) } },
            toggle(
                Tx.s(R.string.look_corner_hints), Glyph.Gamepad, Palette.Butter, Tx.s(R.string.look_corner_hints_help),
                look.cornerHints,
                look = { on -> CornerHintsScene(on) },
                en = "Corner hints",
            ) { on -> updateLook { it.copy(cornerHints = on) } },
        )),
        SettingSection(Tx.s(R.string.look_group_icon_grid), iconGridSettingRows(look), en = "Icon grid"),
        SettingSection(Tx.s(R.string.look_group_dock), en = "Dock", rows = listOf(
            toggle(
                Tx.s(R.string.look_dock_apps), Glyph.Grid, Palette.Lavender, Tx.s(R.string.look_dock_apps_help),
                io.github.zahakj.tandem.data.HomeStore.settings.showDock,
                look = { on -> DockScene(on, look.dockStyle, look.dockTop, look.dockAutoHide) },
                en = "Apps dock",
            ) { on -> io.github.zahakj.tandem.data.HomeStore.updateSettings { it.copy(showDock = on) } },
            choice(
                Tx.s(R.string.look_dock_style), Glyph.Layers, Palette.Lavender, Tx.s(R.string.look_dock_style_help),
                DockStyles, look.dockStyle,
                look = { v -> DockScene(true, v, look.dockTop, look.dockAutoHide) },
                en = "Dock style",
            ) { v -> updateLook { it.copy(dockStyle = v) } },
            toggle(
                Tx.s(R.string.look_dock_top), Glyph.Swap, Palette.Lavender, Tx.s(R.string.look_dock_top_help), look.dockTop,
                look = { on -> DockScene(true, look.dockStyle, on, look.dockAutoHide) },
                en = "Dock on top",
            ) { on -> updateLook { it.copy(dockTop = on) } },
            toggle(
                Tx.s(R.string.look_dock_auto_hide), Glyph.Eye, Palette.Lavender, Tx.s(R.string.look_dock_auto_hide_help), look.dockAutoHide,
                look = { on -> DockScene(true, look.dockStyle, look.dockTop, on) },
                en = "Auto-hide dock",
            ) { on -> updateLook { it.copy(dockAutoHide = on) } },
        )),
        SettingSection(Tx.s(R.string.look_group_bottom), en = "Bottom screen", rows = listOf(
            choice(
                Tx.s(R.string.look_bottom_style), Glyph.Screens, Palette.Sky, Tx.s(R.string.look_bottom_style_help),
                BottomModes, settings.bottomMode,
                look = { v -> BottomPreview(v) },
                en = "Style",
            ) { v -> Store.updateSettings { it.copy(bottomMode = v) } },
            number(
                Tx.s(R.string.look_bottom_logo_size), Glyph.Image, Palette.Sky, Tx.s(R.string.look_bottom_logo_size_help),
                look.heroLogoScale, 0.3f, 1f, 0.1f, ::pct,
                preview = { Box(Modifier.fillMaxWidth().aspectRatio(1.6f).clip(RoundedCornerShape(16.dp))) { LogoScene(look.heroLogoScale) } },
                en = "Logo size",
            ) { v -> updateLook { it.copy(heroLogoScale = v ?: 0.7f) } },
            choice(
                Tx.s(R.string.look_bottom_art_fade), Glyph.Layers, Palette.Sky, Tx.s(R.string.look_bottom_art_fade_help),
                Hero.gradients, look.heroGradient,
                look = { v -> ArtFadeScene(v) },
                en = "Art fade",
            ) { v -> updateLook { it.copy(heroGradient = v) } },
            choice(
                Tx.s(R.string.look_bottom_video_snaps), Glyph.Image, Palette.Sky, Tx.s(R.string.look_bottom_video_snaps_help),
                listOf(
                    "off" to Tx.s(R.string.common_off), "350" to Tx.s(R.string.look_bottom_video_snaps_fast),
                    "650" to Tx.s(R.string.look_bottom_video_snaps_balanced), "1500" to Tx.s(R.string.look_bottom_video_snaps_saver),
                ),
                if (!look.videoSnaps) "off" else look.videoDelayMs.toString().takeIf { it in setOf("350", "650", "1500") } ?: "650", // tx-ok: option ids
                look = { v -> VideoSnapScene(v.toIntOrNull()) },
                en = "Video snaps",
            ) { v -> updateLook { if (v == "off") it.copy(videoSnaps = false) else it.copy(videoSnaps = true, videoDelayMs = v.toInt()) } },
            toggle(
                Tx.s(R.string.look_bottom_moving_art), Glyph.Motion, Palette.Sky, Tx.s(R.string.look_bottom_moving_art_help), look.kenBurns,
                look = { on -> MovingArtScene(on) },
                en = "Moving art",
            ) { on -> updateLook { it.copy(kenBurns = on) } },
        )),
        SettingSection(Tx.s(R.string.look_group_top_bar), en = "Top bar", rows = listOf(
            toggle(
                Tx.s(R.string.look_top_battery), Glyph.Battery, Palette.Mint, Tx.s(R.string.look_top_battery_help), look.battery,
                look = { on -> TopBarScene(look.clock, on) },
                en = "Battery",
            ) { on -> updateLook { it.copy(battery = on) } },
            choice(
                Tx.s(R.string.look_top_clock), Glyph.Clock, Palette.Mint, Tx.s(R.string.look_top_clock_help),
                listOf(
                    "system" to Tx.s(R.string.look_top_clock_system), "12" to Tx.s(R.string.look_top_clock_12),
                    "24" to Tx.s(R.string.look_top_clock_24), "off" to Tx.s(R.string.look_top_clock_hidden),
                ),
                look.clock,
                look = { v -> TopBarScene(v, look.battery) },
                en = "Clock",
            ) { v -> updateLook { it.copy(clock = v) } },
        )),
        SettingSection(Tx.s(R.string.look_group_controls), en = "Controls", rows = listOf(
            choice(
                Tx.s(R.string.look_controls_prompts), Glyph.Gamepad, Palette.Lavender, Tx.s(R.string.look_controls_prompts_help),
                Prompts.styles, look.prompts,
                look = { v -> PromptsScene(v) },
                en = "Button prompts",
            ) { v -> updateLook { it.copy(prompts = v) } },
            // The swaps are bindings (Bindings.swap): Open on B and Back on A, Options on X and Action on Y.
            toggle(
                Tx.s(R.string.look_controls_swap_ab), Glyph.Swap, Palette.Lavender, Tx.s(R.string.look_controls_swap_ab_help),
                Bindings.swapped(bindings, Command.Open, Command.Back),
                look = { on -> SwapButtonsScene(on, "A", "B", Tx.s(R.string.look_controls_does_open), Tx.s(R.string.common_back)) },
                en = "Swap A and B",
            ) { on -> ControllerStore.setBindings(Bindings.swap(bindings, Command.Open, Command.Back, on)) },
            toggle(
                Tx.s(R.string.look_controls_swap_xy), Glyph.Swap, Palette.Lavender, Tx.s(R.string.look_controls_swap_xy_help),
                Bindings.swapped(bindings, Command.Options, Command.Action),
                look = { on -> SwapButtonsScene(on, "X", "Y", Tx.s(R.string.look_controls_does_search), Tx.s(R.string.look_controls_does_options)) },
                en = "Swap X and Y",
            ) { on -> ControllerStore.setBindings(Bindings.swap(bindings, Command.Options, Command.Action, on)) },
            SettingRow(
                Tx.s(R.string.look_controls_rebind),
                if (rebound == 0) Tx.s(R.string.look_controls_rebind_defaults) else Tx.p(R.plurals.look_controls_rebind_changes, rebound),
                Palette.Lavender, Glyph.Gamepad, Tx.s(R.string.look_controls_rebind_help),
                opens = true, keywords = "remap mapping custom controls chord hold shortcut keys buttons favourite screenshot clip side panel",
                en = "Rebind buttons",
            ) { Nav.go(Route.Rebind) },
            // A light tick from the motor on every highlight move (Feel.kt); picking one plays it once.
            choice(
                Tx.s(R.string.controls_feel), Glyph.Motion, Palette.Lavender, Tx.s(R.string.controls_feel_help),
                Feel.levels, io.github.zahakj.tandem.data.ExtrasStore.settings.feelMoves,
                en = "Feel each move",
            ) { v -> io.github.zahakj.tandem.data.ExtrasStore.update { it.copy(feelMoves = v) }; Feel.sample(v) },
        )),
        SettingSection(Tx.s(R.string.look_group_screens), en = "Screens", rows = listOf(
            toggle(
                Tx.s(R.string.look_screens_swap), Glyph.Swap, Palette.Peach, Tx.s(R.string.look_screens_swap_help),
                look.swapScreens,
                look = { on -> DeviceScene(if (on) Shows.Details else Shows.Library, if (on) Shows.Library else Shows.Details) },
                en = "Swap screens",
            ) { on -> updateLook { it.copy(swapScreens = on) }; activity?.applyScreenRoles() },
            toggle(
                Tx.s(R.string.look_screens_rotation), Glyph.Screens, Palette.Peach, Tx.s(R.string.look_screens_rotation_help),
                io.github.zahakj.tandem.data.SystemPrefs.data.keepRotationOff,
                en = "Keep auto-rotate off",
            ) { on -> io.github.zahakj.tandem.data.SystemPrefs.update { it.copy(keepRotationOff = on) } },
        )),
        soundSettings(trackCount),
        SettingSection(Tx.s(R.string.share_group), en = "Share looks", rows = listOf(
            SettingRow(
                Tx.s(R.string.share_title), "", Palette.Mint, Glyph.Sparkle, Tx.s(R.string.share_row_help),
                opens = true, keywords = "share export send qr code file theme gallery community upload",
                en = "Share my look",
            ) { Nav.go(Route.ShareLook) },
            SettingRow(
                Tx.s(R.string.share_get_title), "", Palette.Sky, Glyph.Download, Tx.s(R.string.share_get_row_help),
                opens = true, keywords = "import download gallery community themes browse link qr file",
                en = "Get a look",
            ) { Nav.go(Route.GetLook) },
        )),
        SettingSection(Tx.s(R.string.look_group_extras), en = "Extras", rows = listOf(
            SettingRow(
                Tx.s(R.string.look_reset), BuiltInThemes.PastelNight.name, Palette.Peach, Glyph.Restore, Tx.s(R.string.look_reset_help),
                keywords = "default factory start over appearance",
                en = "Reset look",
            ) {
                // Every look setting at once can't be undone, so it asks first.
                Confirm.hold(Tx.s(R.string.look_reset_ask), Tx.s(R.string.look_reset_ask_body), Tx.s(R.string.look_reset_ask_do)) {
                    io.github.zahakj.tandem.data.Backups.auto(ctx, "reset look") // tx-ok: the reason is saved with the backup
                    Store.updateSettings { it.copy(look = resetLook(it.look), reduceMotion = false) }
                    activity?.applyScreenRoles()
                    Music.refresh()
                    Toasts.show(Tx.s(R.string.look_reset_done, BuiltInThemes.PastelNight.name))
                }
            },
        )),
    ) + listOfNotNull(themePackAudioSettings())
}

/**
 * The galleries at the top of Look & feel: built-in themes, then theme packs (with the pack manager card). Each is
 * one list item with its title, so highlighting a card brings the title along ([showsFromTop]); the first one brings
 * the page's top. Inside, the rows scroll with the usual room ([PaddedScrolling]), not the settings list's.
 */
fun lookHeader(routeKey: String): SettingsHeaderSpec = SettingsHeaderSpec(
    keys = listOf(builtInThemeKeys(), packThemeKeys()),
    content = { target, onFocusKey ->
        val look = Store.settings.look
        item(key = "gallery") {
            Column(Modifier.showsFromTop(FocusRoom)) {
                SettingsHeader(Tx.s(R.string.look_gallery_themes), Tx.s(R.string.look_gallery_themes_note))
                Spacer(Modifier.height(8.dp))
                PaddedScrolling { BuiltInThemeRow(look.theme, target, onFocusKey) }
            }
        }
        item(key = "packs") {
            Column(Modifier.showsFromTop()) {
                SettingsHeader(Tx.s(R.string.look_gallery_packs), "Cocoon · Tandem") // tx-ok: the two pack formats' names
                Spacer(Modifier.height(8.dp))
                PaddedScrolling { PackThemeRow(look.theme, target, onFocusKey) }
            }
        }
    },
    row = { key -> themeGalleryRow(key) },
)

/** The layout gallery at the top of Home & layout, with its title (see [lookHeader]). */
fun layoutHeader(routeKey: String): SettingsHeaderSpec = SettingsHeaderSpec(
    keys = listOf(HomeLayouts.map { "layout:${it.first}" }),
    content = { target, onFocusKey ->
        item(key = "layouts") {
            Column(Modifier.showsFromTop(FocusRoom)) {
                SettingsHeader(Tx.s(R.string.look_gallery_layouts), Tx.s(R.string.look_gallery_layouts_note))
                Spacer(Modifier.height(8.dp))
                PaddedScrolling { LayoutGallery(routeKey, Store.settings.look.homeLayout, target, onFocusKey) }
            }
        }
    },
    row = { key ->
        if (!key.startsWith("layout:")) return@SettingsHeaderSpec null
        val id = key.removePrefix("layout:")
        val look = Store.settings.look
        SettingRow(
            HomeLayouts.firstOrNull { it.first == id }?.second ?: id,
            Tx.s(if (id == look.homeLayout) R.string.look_in_use else R.string.look_gallery_layout_kind),
            Palette.Butter, Glyph.Home, LayoutBlurbs[id]?.let { Tx.s(it) },
            preview = { LayoutPreview(id, Modifier.fillMaxWidth().aspectRatio(1.6f)) },
        ) {}
    },
)

/** The side panel for a theme or pack card in the galleries. */
private fun themeGalleryRow(key: String): SettingRow? {
    val look = Store.settings.look
    if (key == MANAGE_PACKS_KEY) {
        return SettingRow(
            Tx.s(R.string.look_gallery_packs),
            if (ThemePacks.packs.isEmpty()) Tx.s(R.string.look_packs_none) else Tx.p(R.plurals.look_packs_installed, ThemePacks.packs.size),
            Palette.Accent, Glyph.Download, Tx.s(R.string.look_packs_manage_help),
            opens = true,
        ) {}
    }
    if (!key.startsWith("theme:")) return null
    val id = key.removePrefix("theme:")
    ThemePacks.byId(id)?.let { p ->
        val spec = Themes.withParts(p.spec, look)
        return SettingRow(
            p.name, if (p.id == look.theme) Tx.s(R.string.look_in_use) else p.format.label, spec.accent, Glyph.Theme,
            listOfNotNull(
                p.description.lineSequence().firstOrNull()?.take(140)?.ifBlank { null },
                p.author.ifBlank { null }?.let { Tx.s(R.string.look_pack_by_sentence, it) },
                Tx.s(R.string.look_pack_contents_sentence, p.contents.replaceFirstChar { it.uppercase() }),
                p.creditLines().takeIf { it.isNotEmpty() }?.let { Tx.s(R.string.look_pack_art, Tx.list(it)) },
                Tx.s(R.string.look_pack_uses_hint),
            ).joinToString("  "),
            // One miniature, scaled to the panel: two stacked previews would spill over the hints.
            preview = { ThemeMiniature(spec, p, Modifier.fillMaxWidth().aspectRatio(1.6f).clip(RoundedCornerShape(16.dp)), moving = true, tile = 56.dp) },
        ) {}
    }
    val t = Themes.byId(id) ?: return null
    return SettingRow(
        t.name, if (t.id == look.theme) Tx.s(R.string.look_in_use) else t.source, t.accent, Glyph.Theme,
        Tx.s(R.string.look_theme_uses_hint, t.tagline),
        preview = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                ThemeMiniature(t, null, Modifier.fillMaxWidth().aspectRatio(1.7f).clip(RoundedCornerShape(16.dp)), moving = true, tile = 56.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    t.accents.take(6).forEach { c -> Box(Modifier.size(14.dp).clip(CircleShape).background(c)) }
                }
            }
        },
    ) {}
}

/**
 * A tiny drawing of Tandem in [t]: its background and glow, a system tile, two game cards (one highlighted in the
 * theme's highlight style, corners and surface), and a couple of text lines. Drawn with a single Canvas.
 */
@Composable
fun ThemePreview(t: ThemeSpec, modifier: Modifier = Modifier, rounded: Boolean = true) {
    Canvas(modifier.clip(if (rounded) RoundedCornerShape(16.dp) else RoundedCornerShape(0.dp))) {
        val w = size.width
        val h = size.height
        val u = h / 100f
        drawRect(Brush.verticalGradient(listOf(t.background, t.backgroundDeep)))
        drawCircle(Brush.radialGradient(listOf(t.accent.copy(alpha = 0.32f), Color.Transparent), Offset(w * 0.85f, h * 0.1f), h * 0.75f), h * 0.75f, Offset(w * 0.85f, h * 0.1f))
        drawCircle(Brush.radialGradient(listOf(t.peach.copy(alpha = 0.22f), Color.Transparent), Offset(w * 0.1f, h * 0.95f), h * 0.7f), h * 0.7f, Offset(w * 0.1f, h * 0.95f))
        // Top bar: a dot, a title and a clock.
        drawCircle(t.mint, 3.6f * u, Offset(8 * u, 10 * u))
        drawRoundRect(t.text, Offset(14 * u, 8 * u), Size(w * 0.22f, 4.2f * u), CornerRadius(2 * u))
        drawRoundRect(t.text.copy(alpha = 0.7f), Offset(w - 22 * u, 8 * u), Size(14 * u, 4.2f * u), CornerRadius(2 * u))
        val corner = CornerRadius(t.corner * 0.3f * u)
        val top = 24 * u
        val ch = 44 * u
        // System tile.
        val tileW = w * 0.36f
        drawRoundRect(Brush.linearGradient(listOf(t.peach, t.peach.copy(alpha = 0.35f), t.card), Offset(6 * u, top), Offset(6 * u + tileW, top + ch)), Offset(6 * u, top), Size(tileW, ch), corner)
        drawRoundRect(t.onAccent, Offset(10 * u, top + 8 * u), Size(tileW * 0.4f, 6 * u), CornerRadius(2 * u))
        drawRoundRect(t.onAccent.copy(alpha = 0.6f), Offset(10 * u, top + 17 * u), Size(tileW * 0.55f, 3.2f * u), CornerRadius(2 * u))
        // A plain card and a highlighted one.
        // Both cards always fit inside the preview, whatever its width (they were cut off on the right).
        val cw = minOf(ch, (w - (6 * u + tileW + 6 * u) - 12 * u) / 2f)
        val x1 = 6 * u + tileW + 6 * u
        card(t, Offset(x1, top), Size(cw, ch), corner, t.sky, focused = false, u = u)
        card(t, Offset(x1 + cw + 6 * u, top), Size(cw, ch), corner, t.lavender, focused = true, u = u)
        // Text lines.
        drawRoundRect(t.text.copy(alpha = 0.85f), Offset(6 * u, 80 * u), Size(w * 0.42f, 4.5f * u), CornerRadius(2 * u))
        drawRoundRect(t.muted.copy(alpha = 0.7f), Offset(6 * u, 88 * u), Size(w * 0.6f, 3.5f * u), CornerRadius(2 * u))
    }
}

private fun DrawScope.card(t: ThemeSpec, at: Offset, size: Size, corner: CornerRadius, art: Color, focused: Boolean, u: Float) {
    val s = if (focused) t.focusScale.coerceAtMost(1.12f) else 1f
    val grown = Size(size.width * s, size.height * s)
    val o = Offset(at.x - (grown.width - size.width) / 2, at.y - (grown.height - size.height) / 2 - if (focused) 1.5f * u else 0f)
    if (focused && t.highlight != HighlightStyle.Outline) {
        drawCircle(Brush.radialGradient(listOf(t.glow.copy(alpha = 0.45f), Color.Transparent), o + Offset(grown.width / 2, grown.height / 2), grown.width), grown.width, o + Offset(grown.width / 2, grown.height / 2))
    }
    val fill = when (t.material) {
        SurfaceMaterial.Solid -> t.cardHigh
        SurfaceMaterial.Frosted -> t.cardHigh.copy(alpha = 0.7f)
        SurfaceMaterial.Outlined -> t.cardHigh.copy(alpha = 0.3f)
    }
    drawRoundRect(fill, o, grown, corner)
    drawRoundRect(Brush.linearGradient(listOf(art.copy(alpha = 0.85f), art.copy(alpha = 0.25f)), o, o + Offset(grown.width, grown.height)), o + Offset(3 * u, 3 * u), Size(grown.width - 6 * u, grown.height - 6 * u), corner)
    if (t.material == SurfaceMaterial.Outlined) drawRoundRect(t.muted.copy(alpha = 0.6f), o, grown, corner, style = Stroke(0.8f * u))
    if (focused && t.highlight != HighlightStyle.Lift && t.highlightThickness > 0) {
        val sw = (t.highlightThickness * 0.45f * u).coerceAtLeast(0.8f * u)
        val brush = if (t.highlight == HighlightStyle.Outline) Brush.linearGradient(listOf(t.glow, t.glow)) else Brush.linearGradient(listOf(t.text, t.glow, t.text), o, o + Offset(grown.width, grown.height))
        drawRoundRect(brush, o, grown, corner, style = Stroke(sw))
    }
}

/** The live look (current theme with every override), a bit larger. */
@Composable
private fun LookPreview() = ThemePreview(Themes.target, Modifier.fillMaxWidth().aspectRatio(1.45f))

/** The look in the accent now, over every swatch side by side, the one in use ringed. */
@Composable
private fun AccentPreview() {
    val mine = Store.settings.look.accent
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        LookPreview()
        AccentSwatches.chunked(7).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                row.forEach { (_, c) ->
                    val on = mine == c.toHex()
                    Box(
                        Modifier.size(22.dp).clip(CircleShape)
                            .then(if (on) Modifier.border(2.dp, Palette.Text, CircleShape).padding(3.dp) else Modifier)
                            .clip(CircleShape).background(c),
                    )
                }
            }
        }
    }
}

@Composable
private fun WallpaperPreview(choice: String, slot: ScreenSlot) {
    val aspect = if (slot == ScreenSlot.Top) 16f / 9f else 0.87f
    Box(Modifier.fillMaxWidth(if (slot == ScreenSlot.Top) 1f else 0.6f).aspectRatio(aspect).clip(RoundedCornerShape(16.dp))) {
        WallpaperArt(choice, slot, moving = true)
    }
}

/**
 * Wallpaper [choice] filling its box: a backdrop, the theme's own, game art, or a picture or video of yours. Not
 * [moving] (the picker's many cards), backdrops hold still and a video shows as a play mark.
 */
@Composable
private fun WallpaperArt(choice: String, slot: ScreenSlot, moving: Boolean) {
    val tint = Palette.Lavender
    val theme = Themes.live
    Box(Modifier.fillMaxSize()) {
        when {
            choice == "theme" -> {
                val f = if (slot == ScreenSlot.Top) theme.wallpaperTop else theme.wallpaperBottom
                if (f != null) MediaThumb(f, moving) else Backdrop(theme.backdrop, tint, moving = moving)
            }
            choice == "hero" -> MiniScreen { t, _ -> miniArt(t, 0f, 0f, size.width, size.height); drawRect(Color.Black.copy(alpha = 0.25f)) }
            choice.startsWith("file:") -> MediaThumb(File(choice.removePrefix("file:")), moving)
            else -> Backdrop(BackdropStyle.of(choice) ?: theme.backdrop, tint, moving = moving)
        }
    }
}

@Composable
private fun MediaThumb(file: File, moving: Boolean = true) {
    val video = file.extension.lowercase() in ThemePacks.videoExtensions
    when {
        video && moving -> LoopingVideo(file, Modifier.fillMaxSize())
        video -> Box(Modifier.fillMaxSize().background(Palette.NightDeep), contentAlignment = Alignment.Center) {
            Text("▶", style = MaterialTheme.typography.headlineSmall, color = Palette.Muted)
        }
        else -> AsyncImage(file, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}

/** Six of the user's systems as their icons show now (a pack's art, or drawn). */
@Composable
private fun IconsPreview() {
    val systems = previewSystems(6)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        systems.take(6).chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { (id, short) ->
                    val lib = io.github.zahakj.tandem.Libraries.byId(id)
                    val pack = if (lib == null) ThemePacks.systemIcon(id) else null
                    when {
                        lib != null -> SystemIcon(lib.platform, 72.dp)
                        pack != null -> AsyncImage(pack, null, Modifier.size(72.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
                        else -> DrawnIcon(short, Devices.of(id), Palette.forName(id), 72.dp)
                    }
                }
            }
        }
    }
}

/** A few game cards in a grid [size] and card [shape], with or without [titles] (the settings in use by default). */
@Composable
private fun GridPreview(
    size: String = Store.settings.look.gridSize, shape: String = Store.settings.look.cardShape, titles: Boolean = Store.settings.showTitles,
) {
    val art = when (shape) { "portrait" -> 0.7f; "landscape" -> 1.45f; else -> 1f }
    val cols = size.removePrefix("cols:").toIntOrNull() ?: when (size) { "small" -> 6; "large" -> 4; "huge" -> 3; else -> 5 }
    val clip = when (shape) { "circle" -> CircleShape; "square" -> RoundedCornerShape(3.dp); else -> Palette.cardShape }
    val colors = Palette.accents
    Column(Modifier.background(Palette.NightDeep).padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(2) { r ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(cols.coerceAtMost(8)) { i ->
                    Column(Modifier.weight(1f)) {
                        Box(
                            Modifier.fillMaxWidth().aspectRatio(art).clip(clip)
                                .background(Brush.linearGradient(listOf(colors[(i + r) % colors.size], Palette.CardHigh))),
                        )
                        if (titles) {
                            Spacer(Modifier.height(3.dp))
                            Box(Modifier.fillMaxWidth(0.7f).height(3.dp).clip(CircleShape).background(Palette.Muted).align(Alignment.CenterHorizontally))
                        }
                    }
                }
            }
        }
    }
}

/** A sketch of the bottom screen in the given style, as tall as the box it gets. */
@Composable
private fun BottomPreview(mode: String) {
    Box(Modifier.fillMaxSize().background(Palette.Night), contentAlignment = Alignment.Center) {
        BottomSketch(mode, Modifier.fillMaxHeight().padding(vertical = 8.dp).aspectRatio(0.87f))
    }
}

@Composable
private fun BottomSketch(mode: String, modifier: Modifier) {
    Box(modifier.clip(RoundedCornerShape(12.dp)).background(Palette.NightDeep)) {
        val art = Brush.linearGradient(listOf(Palette.Lavender, Palette.Peach, Palette.Sky))
        when (mode) {
            "clock" -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(Clock.always(Date()), style = MaterialTheme.typography.headlineSmall)
                Box(Modifier.width(60.dp).height(4.dp).clip(CircleShape).background(Palette.Muted))
            }
            "art" -> Box(Modifier.fillMaxSize().background(art)) {
                Box(Modifier.align(Alignment.BottomCenter).padding(14.dp).width(70.dp).height(14.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.85f)))
            }
            "both" -> Box(Modifier.fillMaxSize().background(art)) {
                Column(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(8.dp).clip(RoundedCornerShape(10.dp))
                        .background(Palette.Card.copy(alpha = 0.75f)).padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) { PreviewLines() }
            }
            // The art fading down into the background, the info on its faded part.
            else -> Box(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().background(art).drawWithContent {
                    drawContent()
                    drawRect(Brush.verticalGradient(0.3f to Color.Transparent, 0.62f to Palette.NightDeep.copy(alpha = 0.88f), 1f to Palette.NightDeep.copy(alpha = 0.92f)))
                })
                Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { PreviewLines() }
            }
        }
    }
}

@Composable
private fun PreviewLines() {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf(Palette.Mint, Palette.Butter, Palette.Lavender).forEach { Box(Modifier.width(18.dp).height(6.dp).clip(CircleShape).background(it)) }
    }
    Box(Modifier.fillMaxWidth(0.9f).height(4.dp).clip(CircleShape).background(Palette.Muted.copy(alpha = 0.7f)))
    Box(Modifier.fillMaxWidth(0.7f).height(4.dp).clip(CircleShape).background(Palette.Muted.copy(alpha = 0.7f)))
}


/** What each home layout is like, for the side panel (layout id to string). */
internal val LayoutBlurbs = mapOf(
    "shelves" to R.string.look_layout_shelves_help,
    "grid" to R.string.look_layout_grid_help,
    "carousel" to R.string.look_layout_carousel_help,
    "list" to R.string.look_layout_list_help,
    "console" to R.string.look_layout_console_help,
    "orbit" to R.string.look_layout_orbit_help,
    "spotlight" to R.string.look_layout_spotlight_help,
    "stage" to R.string.look_layout_stage_help,
)

/** Every home layout as a live sketch in the current theme. A switches to it. */
@Composable
private fun LayoutGallery(routeKey: String, current: String, target: String?, onFocusKey: (String) -> Unit) {
    GalleryRow("layouts", HomeLayouts.map { "layout:${it.first}" }, target) {
        items(HomeLayouts, key = { it.first }) { (id, name) ->
            val key = "layout:$id"
            FocusCard(
                Modifier.width(150.dp).height(122.dp).galleryItem(key).takesFocus(routeKey, key, key == target),
                glow = Palette.Butter, shape = RoundedCornerShape(18.dp),
                onFocus = { onFocusKey(key) },
                onClick = { updateLook { it.copy(homeLayout = id) } },
            ) {
                Column(Modifier.fillMaxSize()) {
                    LayoutPreview(id, Modifier.fillMaxWidth().weight(1f), rounded = false)
                    Row(
                        Modifier.fillMaxWidth().background(Palette.Card).padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(name.substringBefore(" ("), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (id == current) Box(Modifier.size(8.dp).clip(CircleShape).background(Palette.Butter))
                    }
                }
            }
        }
    }
}

/**
 * A sketch of a layout in the live theme: "shelves", "grid", "carousel", "list", "console", or a games view
 * ("view-grid", "view-list", "view-carousel"). One Canvas, no images. The grid follows the icon grid's own settings;
 * as a [sketch] (the welcome's choices) it is a calm four by two with a plain dock instead.
 *
 * Showcases pass a [look] with one setting changed, and for the icon grid what lives outside [Look]: a name under
 * the highlighted icon only ([focusLabel]), the Recently played strip ([strip]: its opacity, 0 for none) and the
 * Resume page left of the grid ([resumePage]). [drift] (0 to 1) moves Spotlight's art.
 */
@Composable
fun LayoutPreview(
    id: String, modifier: Modifier = Modifier, rounded: Boolean = true, sketch: Boolean = false,
    look: Look = Store.settings.look, focusLabel: Boolean = false, strip: Float = 0f, resumePage: Boolean = false,
    drift: androidx.compose.runtime.State<Float>? = null,
) {
    val t = Themes.live
    // Shapes only, so in right to left the sketch is the mirror image, as the layouts themselves are; Spotlight's
    // rail keeps the side its setting names (SpotlightHome), so its sketch doesn't.
    Canvas(modifier.clip(RoundedCornerShape(if (rounded) 16.dp else 0.dp)).then(if (id == "spotlight") Modifier else Modifier.mirroredInRtl())) {
        val w = size.width
        val h = size.height
        val u = h / 100f
        val r = CornerRadius(minOf(t.corner * 0.25f, 5f) * u)
        val accents = t.accents
        drawRect(Brush.verticalGradient(listOf(t.background, t.backgroundDeep)))
        fun tile(x: Float, y: Float, tw: Float, th: Float, i: Int, focused: Boolean = false) {
            drawRoundRect(Brush.linearGradient(listOf(accents[i % accents.size], t.cardHigh), Offset(x, y), Offset(x + tw, y + th)), Offset(x, y), Size(tw, th), r)
            if (focused) drawRoundRect(t.text, Offset(x, y), Size(tw, th), r, style = Stroke(1.2f * u))
        }
        fun bar(x: Float, y: Float, bw: Float, c: Color = t.text, hh: Float = 3.5f) = drawRoundRect(c, Offset(x, y), Size(bw, hh * u), CornerRadius(2 * u))
        fun dock(y: Float) {
            val dw = w * 0.5f
            drawRoundRect(t.card.copy(alpha = 0.85f), Offset((w - dw) / 2, y), Size(dw, 9 * u), CornerRadius(5 * u))
            for (i in 0 until 5) drawCircle(accents[(i + 2) % accents.size], 2.6f * u, Offset((w - dw) / 2 + dw * (i + 0.5f) / 5, y + 4.5f * u))
        }
        when (id) {
            "grid" -> if (sketch) {
                // Four by two rounded squares, the first ringed, over a plain pill dock.
                val across = 4
                val gap = w * 0.045f
                val tw = minOf((w * 0.8f - gap * (across - 1)) / across, (h - 34 * u - gap) / 2)
                val left = (w - (tw * across + gap * (across - 1))) / 2
                val top = (h - 20 * u - (tw * 2 + gap)) / 2
                val cr = CornerRadius(tw * 0.26f)
                for (row in 0 until 2) for (c in 0 until across) {
                    val x = left + c * (tw + gap)
                    val y = top + row * (tw + gap)
                    val a = accents[(row * across + c) % accents.size]
                    drawRoundRect(
                        Brush.linearGradient(listOf(a, androidx.compose.ui.graphics.lerp(a, t.background, 0.35f)), Offset(x, y), Offset(x + tw, y + tw)),
                        Offset(x, y), Size(tw, tw), cr,
                    )
                    if (c == 0 && row == 0) {
                        val o = 1.8f * u
                        drawRoundRect(t.text, Offset(x - o, y - o), Size(tw + 2 * o, tw + 2 * o), CornerRadius(cr.x + o), style = Stroke(1.2f * u))
                    }
                }
                val dw = w * 0.3f
                drawRoundRect(t.card.copy(alpha = 0.9f), Offset((w - dw) / 2, h - 17 * u), Size(dw, 10 * u), CornerRadius(5 * u))
                for (i in 0 until 3) drawCircle(accents[(i + 2) % accents.size], 2.8f * u, Offset((w - dw) / 2 + dw * (i + 0.5f) / 3, h - 12 * u))
            } else {
                // The icon grid as it really lays out: square icons, a peek of the next column, a pill dock.
                val split = if (look.gridHeroSplit <= 0f) 0f else h * look.gridHeroSplit.coerceIn(0.15f, 0.5f)
                if (split > 0) {
                    drawRect(Brush.linearGradient(listOf(t.lavender, t.peach, t.background)), Offset.Zero, Size(w, split))
                    bar(6 * u, split - 10 * u, w * 0.3f, t.text, 5f)
                }
                val across = look.gridAcross.coerceIn(4, 10)
                val g = look.gridGap.coerceIn(0.08f, 0.6f)
                val sideways = look.gridScroll == "horizontal"
                val downward = look.gridScroll == "vertical"
                val peek = if (sideways) 0.42f else 0f
                val dockH = 16 * u
                // The Resume page takes the left of the screen, the grid starting after it.
                val left = if (resumePage) w * 0.27f else 0f
                if (resumePage) {
                    drawRoundRect(t.card, Offset(4 * u, split + 4 * u), Size(left - 8 * u, h - split - dockH - 6 * u), CornerRadius(5 * u))
                    drawRoundRect(Brush.linearGradient(listOf(t.sky, t.lavender, t.peach)), Offset(7 * u, split + 7 * u), Size(left - 14 * u, (h - split - dockH) * 0.5f), CornerRadius(4 * u))
                    bar(7 * u, split + (h - split - dockH) * 0.62f, left * 0.5f, t.text, 4f)
                    drawRoundRect(t.mint, Offset(7 * u, split + (h - split - dockH) * 0.75f), Size(left * 0.35f, 7 * u), CornerRadius(3.5f * u))
                }
                // The Recently played strip along the top: covers in a band, faded while it waits for L2.
                val stripH = if (strip > 0f) 15 * u else 0f
                if (strip > 0f) {
                    drawRoundRect(t.card, Offset(left + 4 * u, split + 3 * u), Size(w - left - 8 * u, stripH - 2 * u), CornerRadius(4 * u), alpha = strip)
                    for (i in 0 until 7) tile(left + 7 * u + i * 11 * u, split + 5 * u, 8 * u, stripH - 6 * u, i + 3)
                    if (strip < 1f) drawRoundRect(t.card, Offset(left + 4 * u, split + 3 * u), Size(w - left - 8 * u, stripH - 2 * u), CornerRadius(4 * u), alpha = 1f - strip)
                }
                val margin = left + w * 0.036f
                val labelShare = if (look.gridLabels) 0.22f else 0f
                val areaTop = split + stripH + 4 * u
                val areaH = h - areaTop - dockH
                var tw = (w - left - w * 0.072f) / (across + (across - 1) * g + if (peek > 0f) g + peek else 0f)
                // A fixed number of rows: the icons grow until the rows fill the height, and the columns follow.
                val fixedRows = look.gridRows.coerceIn(0, 6)
                if (fixedRows > 0) tw = areaH / (fixedRows * (1 + labelShare) + (fixedRows - 1) * g)
                val gap = tw * g
                var label = tw * labelShare
                tw = minOf(tw, areaH - label)
                label = tw * labelShare
                val rows = if (fixedRows > 0) fixedRows else maxOf(1, ((areaH + gap) / (tw + label + gap)).toInt())
                val gridH = rows * (tw + label) + (rows - 1) * gap
                val top = if (downward) areaTop + 2 * u else areaTop + (areaH - gridH) / 2
                val cr = CornerRadius(tw * look.gridRoundness.coerceIn(0f, 0.5f))
                val cols = if (fixedRows > 0) ((w - margin) / (tw + gap)).toInt() + 1 else across + if (sideways) 1 else 0
                // Up and down: a peek of the next row under the last one.
                val shownRows = rows + if (downward) 1 else 0
                for (c in 0 until cols) for (row in 0 until shownRows) {
                    val x = margin + c * (tw + gap)
                    val y = top + row * (tw + label + gap)
                    // Only whole icons: one the edge would cut (the peek of the next column or row) is left out, and
                    // an arrow says the grid goes on that way.
                    if (x + tw > w - w * 0.03f || y + tw + label > h - dockH + u) continue
                    val a = accents[(c * rows + row) % accents.size]
                    drawRoundRect(
                        Brush.linearGradient(listOf(a, androidx.compose.ui.graphics.lerp(a, t.background, 0.35f)), Offset(x, y), Offset(x + tw, y + tw)),
                        Offset(x, y), Size(tw, tw), cr,
                    )
                    if (label > 0f) drawRoundRect(t.muted, Offset(x + tw * 0.2f, y + tw + label * 0.35f), Size(tw * 0.6f, label * 0.3f), CornerRadius(label))
                    if (c == 0 && row == 0) {
                        val o = 1.6f * u
                        drawRoundRect(t.text, Offset(x - o, y - o), Size(tw + 2 * o, tw + 2 * o), CornerRadius(cr.x + o), style = Stroke(1.1f * u))
                        // The highlighted icon's name, when only it has one.
                        if (label == 0f && focusLabel) drawRoundRect(t.text, Offset(x + tw * 0.1f, y + tw + 2.4f * u), Size(tw * 0.8f, 2.6f * u), CornerRadius(1.3f * u))
                    }
                }
                fun arrow(a: Offset, b: Offset, c: Offset) = drawPath(
                    androidx.compose.ui.graphics.Path().apply { moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(c.x, c.y) }, t.muted, style = Stroke(1.2f * u),
                )
                val mid = top + gridH / 2
                if (sideways || fixedRows > 0) arrow(Offset(w - 5 * u, mid - 4 * u), Offset(w - 2 * u, mid), Offset(w - 5 * u, mid + 4 * u))
                if (downward) arrow(Offset(w / 2 - 4 * u, h - dockH - 3.5f * u), Offset(w / 2, h - dockH - 0.5f * u), Offset(w / 2 + 4 * u, h - dockH - 3.5f * u))
                // Pages: dots for the page you are on.
                if (look.gridScroll == "paged") for (i in 0 until 3) drawCircle(if (i == 0) t.text else t.muted, 1.4f * u, Offset(w / 2 + (i - 1) * 5 * u, h - dockH - 1 * u))
                val dw = w * 0.36f
                drawRoundRect(t.card.copy(alpha = 0.9f), Offset((w - dw) / 2, h - dockH + 3 * u), Size(dw, 10 * u), CornerRadius(5 * u))
                for (i in 0 until 6) drawCircle(accents[(i + 2) % accents.size], 2.8f * u, Offset((w - dw) / 2 + dw * (i + 0.5f) / 6, h - dockH + 8 * u))
                drawCircle(t.muted, 2.4f * u, Offset(6 * u, h - dockH + 8 * u))
                drawCircle(t.muted, 2.4f * u, Offset(w - 6 * u, h - dockH + 8 * u))
            }
            "carousel" -> {
                val cw = 40 * u
                val cy = 14 * u
                for (d in listOf(-2, -1, 1, 2)) {
                    val s = 1f - 0.22f * kotlin.math.abs(d)
                    val tw = cw * s
                    val x = w / 2 + d * cw * 0.72f - tw / 2
                    tile(x, cy + (cw - tw) / 2, tw, tw, d + 3)
                }
                tile(w / 2 - cw / 2, cy, cw, cw, 0, focused = true)
                bar(w / 2 - w * 0.15f, 62 * u, w * 0.3f, t.text, 5f)
                bar(w / 2 - w * 0.1f, 70 * u, w * 0.2f, t.muted)
                dock(h - 13 * u)
            }
            "list", "view-list" -> {
                for (i in 0 until 6) {
                    val y = 6 * u + i * 15 * u
                    drawRoundRect(if (i == 1) t.cardHigh else t.card.copy(alpha = 0.7f), Offset(5 * u, y), Size(w * 0.52f, 12 * u), r)
                    tile(7 * u, y + 2 * u, 8 * u, 8 * u, i)
                    bar(18 * u, y + 3 * u, w * 0.26f)
                    bar(18 * u, y + 7.5f * u, w * 0.18f, t.muted, 2.5f)
                }
                drawRoundRect(Brush.verticalGradient(listOf(t.lavender, t.card)), Offset(w * 0.6f, 6 * u), Size(w * 0.36f, h - 12 * u), r)
                bar(w * 0.63f, h - 22 * u, w * 0.25f, t.text, 4.5f)
            }
            "console" -> {
                drawRect(Brush.linearGradient(listOf(t.sky, t.lavender, t.background), Offset(w, 0f), Offset(0f, h)))
                drawRect(Brush.verticalGradient(listOf(Color.Transparent, t.background)), Offset(0f, h * 0.4f), Size(w, h * 0.6f))
                bar(8 * u, 44 * u, w * 0.18f, t.peach, 3f)
                bar(8 * u, 50 * u, w * 0.4f, t.text, 7f)
                val tw = 20 * u
                for (i in 0 until 7) tile(8 * u + i * (tw + 4 * u), 64 * u, tw, tw, i, focused = i == 0)
                dock(h - 11 * u)
            }
            "orbit" -> {
                // Drawn with Orbit's own geometry (OrbitLayout, Fan) in its proportions: orbs on a glowing ring seen
                // in perspective, the front one big and ringed with its name under it, the covers standing on a lit
                // floor inside the ring with the Continue card apart to their left, and the quiet dock below.
                val baseY = 62 * u
                val orbit = OrbitLayout(w, baseY, 17 * u, 22 * u, side = 4)
                val lav = t.lavender
                val ringTop = orbit.centreY - orbit.radiusY
                val corner = Offset(orbit.centreX - orbit.radiusX, ringTop)
                val oval = Size(orbit.radiusX * 2, orbit.radiusY * 2)
                drawOval(Brush.verticalGradient(listOf(lav.copy(alpha = 0.02f), lav.copy(alpha = 0.28f)), ringTop, baseY), corner, oval, style = Stroke(2.2f * u))
                drawOval(Brush.verticalGradient(listOf(t.text.copy(alpha = 0.1f), t.text.copy(alpha = 0.8f)), ringTop, baseY), corner, oval, style = Stroke(0.4f * u))
                for (i in listOf(-4, 4, -3, 3, -2, 2, -1, 1, 0)) {
                    val p = orbit.place(i.toFloat())
                    val rr = 11 * u * p.scale
                    val c = accents[(i + 8) % accents.size]
                    val at = Offset(p.x, p.y)
                    if (i == 0) drawCircle(c.copy(alpha = 0.3f), rr * 1.6f, at)
                    drawCircle(Brush.linearGradient(listOf(androidx.compose.ui.graphics.lerp(c, Color.White, 0.25f), androidx.compose.ui.graphics.lerp(c, t.background, 0.35f)), Offset(p.x - rr, p.y - rr), Offset(p.x + rr, p.y + rr)), rr, at, alpha = p.alpha)
                    if (p.back > 0f) drawCircle(t.backgroundDeep, rr, at, alpha = 0.55f * p.back)
                    if (i == 0) drawCircle(t.text, rr + 1.4f * u, at, style = Stroke(0.9f * u))
                }
                bar(w / 2 - w * 0.1f, baseY + 13.5f * u, w * 0.2f, t.text, 4.5f)
                bar(w / 2 - w * 0.07f, baseY + 20 * u, w * 0.14f, t.muted, 2.5f)
                val floorY = baseY - 16.6f * u
                val cw = minOf((floorY - 2.3f * u) / 1.12f / Fan.ASPECT, (w / 2 - 3.3f * u) / Fan.reach(5, look.orbitContinue))
                val ch = cw * Fan.ASPECT
                drawRect(
                    Brush.horizontalGradient(listOf(Color.Transparent, lav.copy(alpha = 0.6f), Color.Transparent), w / 2 - cw * 2.2f, w / 2 + cw * 2.2f),
                    Offset(w / 2 - cw * 2.2f, floorY - 0.2f * u), Size(cw * 4.4f, 0.4f * u),
                )
                for (rank in listOf(4, 3, 2, 1, 0)) {
                    val f = Fan.place(rank, 5, cw)
                    val fw = cw * f.scale
                    val fh = ch * f.scale
                    tile(w / 2 + f.x - fw / 2, floorY - fh, fw, fh, rank + 1, focused = false)
                    if (f.dim > 0f) drawRoundRect(Color.Black, Offset(w / 2 + f.x - fw / 2, floorY - fh), Size(fw, fh), r, alpha = f.dim)
                }
                if (look.orbitContinue) {
                    val kw = cw * Fan.CONTINUE
                    val kh = ch * Fan.CONTINUE
                    val kx = w / 2 + Fan.continueX(5, cw) - kw / 2
                    tile(kx, floorY - kh, kw, kh, 6)
                    drawCircle(t.mint, 2.6f * u, Offset(kx + kw - 4 * u, floorY - 4 * u))
                    bar(kx + kw * 0.2f, floorY - kh - 4.5f * u, kw * 0.6f, t.mint, 2.2f)
                }
                val dw = w * 0.34f
                drawRoundRect(t.card.copy(alpha = 0.5f), Offset((w - dw) / 2, h - 10.5f * u), Size(dw, 8 * u), CornerRadius(4 * u))
                for (i in 0 until 5) drawCircle(accents[(i + 2) % accents.size].copy(alpha = 0.8f), 2.4f * u, Offset((w - dw) / 2 + dw * (i + 0.5f) / 5, h - 6.5f * u))
            }
            "spotlight" -> {
                // The art across the screen fading towards the rail, the rail of covers with the middle one ringed,
                // and the panel's logo, chips, lines of story and buttons on the other side.
                val left = look.spotlightRail != "right"
                fun x(at: Float, bw: Float) = if (left) at else w - at - bw
                // The backdrop: the art (drifting while [drift] runs), the cover blurred to soft colour, or the theme dimmed.
                when (look.spotlightBackdrop) {
                    "dim" -> drawRect(Brush.radialGradient(listOf(t.card, t.backgroundDeep), Offset(w * 0.6f, h * 0.4f), w * 0.7f))
                    "blur" -> drawRect(
                        Brush.linearGradient(
                            listOf(androidx.compose.ui.graphics.lerp(t.sky, t.background, 0.45f), androidx.compose.ui.graphics.lerp(t.lavender, t.background, 0.4f), androidx.compose.ui.graphics.lerp(t.peach, t.background, 0.45f)),
                            Offset(w, 0f), Offset(0f, h),
                        ),
                    )
                    else -> {
                        val dx = kotlin.math.sin((drift?.value ?: 0f) * 2 * Math.PI).toFloat() * w * 0.08f
                        drawRect(Brush.linearGradient(listOf(t.sky, t.lavender, t.peach), Offset(w + dx, 0f), Offset(dx, h)))
                        drawCircle(t.butter.copy(alpha = 0.7f), 16 * u, Offset(w * 0.68f + dx, 30 * u))
                    }
                }
                drawRect(
                    Brush.horizontalGradient(
                        if (left) listOf(t.background, t.background.copy(alpha = 0.2f), t.background.copy(alpha = 0.55f))
                        else listOf(t.background.copy(alpha = 0.55f), t.background.copy(alpha = 0.2f), t.background),
                    ),
                )
                val rw = w * 0.22f
                val tw = rw * 0.5f
                bar(x(rw * 0.2f, rw * 0.6f), 5 * u, rw * 0.6f, t.text, 3f)
                for (i in -2..2) {
                    val s = if (i == 0) 1.25f else 1f
                    val th = tw * 1.3f * s
                    val cy = h * 0.55f + i * 25 * u
                    tile(x((rw - tw * s) / 2, tw * s), cy - th / 2, tw * s, th, i + 3, focused = i == 0)
                }
                val px = w * 0.52f
                val pw = w * 0.42f
                bar(x(px, pw * 0.7f), 26 * u, pw * 0.7f, t.text, 8f)
                for (i in 0 until 3) drawRoundRect(accents[(i + 1) % accents.size], Offset(x(px + i * 13 * u, 11 * u), 40 * u), Size(11 * u, 4 * u), CornerRadius(2 * u))
                for (i in 0 until 4) bar(x(px, pw * (if (i == 3) 0.6f else 1f)), (50 + i * 6) * u, pw * (if (i == 3) 0.6f else 1f), t.muted, 2.5f)
                for (i in 0 until 3) bar(x(px + i * 16 * u, 12 * u), 78 * u, 12 * u, t.text, 3f)
                for (i in 0 until 4) drawCircle(accents[i % accents.size], 2.4f * u, Offset(x(px + 2.4f * u + i * 12 * u, 0f), 89 * u))
            }
            "stage" -> {
                // The highlighted game's art fading in towards the end, the strip of short names on top, the game's
                // words above the row and the row of covers, the second one big and ringed, the group's name under it.
                when (look.stageBackdrop) {
                    "dim" -> drawRect(Brush.radialGradient(listOf(t.card, t.backgroundDeep), Offset(w * 0.6f, h * 0.4f), w * 0.7f))
                    "blur" -> drawRect(
                        Brush.linearGradient(
                            listOf(androidx.compose.ui.graphics.lerp(t.peach, t.background, 0.45f), androidx.compose.ui.graphics.lerp(t.sky, t.background, 0.4f)),
                            Offset(w, 0f), Offset(0f, h),
                        ),
                    )
                    else -> {
                        val dx = kotlin.math.sin((drift?.value ?: 0f) * 2 * Math.PI).toFloat() * w * 0.06f
                        drawRect(Brush.linearGradient(listOf(t.peach, t.lavender, t.sky), Offset(w + dx, 0f), Offset(dx, h)))
                        drawCircle(t.butter.copy(alpha = 0.7f), 15 * u, Offset(w * 0.72f + dx, 26 * u))
                    }
                }
                drawRect(Brush.horizontalGradient(listOf(t.background, t.background.copy(alpha = 0.15f))))
                drawRect(Brush.verticalGradient(listOf(Color.Transparent, t.background), h * 0.45f, h))
                val sw = w * 0.5f
                drawRoundRect(t.card.copy(alpha = 0.85f), Offset((w - sw) / 2, 3 * u), Size(sw, 8 * u), CornerRadius(4 * u))
                val chip = (sw - 8 * u) / 5
                for (i in 0 until 5) drawRoundRect(if (i == 2) accents[1] else t.muted.copy(alpha = 0.5f), Offset((w - sw) / 2 + 4 * u + i * chip, 4.5f * u), Size(chip - 2 * u, 5 * u), CornerRadius(2.5f * u))
                bar(8 * u, 17 * u, w * 0.3f, t.text, 7f)
                bar(8 * u, 27 * u, w * 0.22f, accents[1], 3f)
                bar(8 * u, 33 * u, w * 0.36f, t.muted, 2.5f)
                bar(8 * u, 38 * u, w * 0.3f, t.muted, 2.5f)
                bar(8 * u, 44 * u, w * 0.16f, t.mint, 2.5f)
                val ch = h * when (look.stageCovers) { "s" -> 0.22f; "l" -> 0.32f; else -> 0.27f }
                val cw = ch * when (look.cardShape) { "portrait" -> 0.72f; "landscape" -> 1.45f; else -> 1f }
                val base = h - 10 * u
                for (i in 0 until 9) {
                    val s = if (i == 1) 1.18f else 1f
                    val x = 8 * u + i * (cw + 3 * u) - (cw * s - cw) / 2 + if (i > 1) (cw * 0.18f) / 2 else 0f
                    if (x > w) break
                    tile(x, base - ch * s, cw * s, ch * s, i + 1, focused = i == 1)
                }
                bar(8 * u, h - 6 * u, w * 0.2f, t.text, 2.5f)
            }
            "view-carousel" -> {
                val cw = 44 * u
                for (d in listOf(-2, -1, 1, 2)) {
                    val s = 1f - 0.22f * kotlin.math.abs(d)
                    val tw = cw * s
                    tile(w / 2 + d * cw * 0.7f - tw / 2, 12 * u + (cw - tw) / 2, tw, tw, d + 3)
                }
                tile(w / 2 - cw / 2, 12 * u, cw, cw, 0, focused = true)
                bar(w / 2 - w * 0.17f, 66 * u, w * 0.34f, t.text, 5f)
                bar(w / 2 - w * 0.12f, 75 * u, w * 0.24f, t.muted)
            }
            "view-shelf" -> {
                // Two shelves of spines, one game turned out to show its cover, in the bookcase Shelf look chooses: warm
                // wood, matte boards in the theme's colours over a back panel, or open (only a thin line).
                val open = look.shelfLook == ShelfLooks.OPEN
                val wood = when (look.shelfLook) { ShelfLooks.MATTE -> t.cardHigh; ShelfLooks.OPEN -> t.muted.copy(alpha = 0.6f); else -> Color(0xFF8A5A34) }
                when (look.shelfLook) {
                    ShelfLooks.MATTE -> drawRoundRect(t.card.copy(alpha = 0.8f), Offset(3 * u, 4 * u), Size(w - 6 * u, 84 * u), CornerRadius(3 * u))
                    ShelfLooks.OPEN -> Unit
                    else -> drawRoundRect(Color(0xFF4A2F1C), Offset(3 * u, 4 * u), Size(w - 6 * u, 84 * u), CornerRadius(3 * u))
                }
                for (row in 0 until 2) {
                    val top = 8 * u + row * 42 * u
                    for (i in 0 until 12) {
                        val x0 = 8 * u + i * 9 * u + if (i > 1) 20 * u else 0f
                        if (i == 2 && row == 0) tile(8 * u + 2 * 9 * u, top, 26 * u, 32 * u, 0, focused = true)
                        else tile(x0, top + 2 * u, 7 * u, 30 * u, row * 5 + i)
                    }
                    drawRoundRect(wood, Offset(4 * u, top + 33 * u), Size(w - 8 * u, if (open) 1.2f * u else 4 * u), CornerRadius(1.5f * u))
                }
            }
            "view-case" -> {
                // Cartridges in rows on a dark foam tray.
                drawRoundRect(Color(0xFF26262C), Offset(4 * u, 4 * u), Size(w - 8 * u, 84 * u), CornerRadius(6 * u))
                val cols = 7
                val cw = (w - 20 * u - 3 * u * (cols - 1)) / cols
                for (row in 0 until 3) for (c in 0 until cols) tile(10 * u + c * (cw + 3 * u), 9 * u + row * 26 * u, cw, 21 * u, row + c, focused = row == 0 && c == 3)
            }
            "view-grid" -> {
                val cols = 6
                val gap = 3 * u
                val tw = (w - 12 * u - gap * (cols - 1)) / cols
                for (row in 0 until 3) for (c in 0 until cols) tile(6 * u + c * (tw + gap), 6 * u + row * (tw + gap), tw, tw, row + c, focused = row == 0 && c == 1)
            }
            else -> { // shelves
                bar(6 * u, 6 * u, w * 0.2f, t.mint)
                for (i in 0 until 5) tile(6 * u + i * 19 * u, 12 * u, 16 * u, 16 * u, i, focused = i == 0)
                bar(6 * u, 34 * u, w * 0.18f, t.lavender)
                for (i in 0 until 3) tile(6 * u + i * 38 * u, 40 * u, 35 * u, 18 * u, i + 2)
                bar(6 * u, 64 * u, w * 0.2f, t.peach)
                for (i in 0 until 5) tile(6 * u + i * 21 * u, 70 * u, 18 * u, 18 * u, i + 1)
            }
        }
    }
}

/** Optional button reminders in the bottom-right corner of the top screen. */
@Composable
fun CornerHints() {
    val look = Store.settings.look
    val show = look.cornerHints && !isSettingsRoute(Nav.route) &&
        Nav.menuFor == null && !Nav.search && !Nav.groupMenu && !LayoutSwitch.open && !((IconGrid.enabled || Orbit.enabled || SpotlightMode.enabled || StageMode.enabled) && Nav.route == Route.Home)
    val alpha by animateFloatAsState(if (show) 1f else 0f, Motion.fade(250), label = "hints")
    if (alpha == 0f) return
    val game = Selection.current is Selected.GameItem
    Box(Modifier.fillMaxSize().padding(14.dp).graphicsLayer { this.alpha = alpha }, contentAlignment = Alignment.BottomEnd) {
        Row(
            Modifier.clip(RoundedCornerShape(50)).background(Palette.Card.copy(alpha = 0.82f)).padding(horizontal = 12.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            ButtonHint("A", Tx.s(if (game) R.string.look_hints_play else R.string.look_hints_open), Palette.Mint)
            if (game) ButtonHint("Y", Tx.s(R.string.look_hints_options), Palette.Butter)
            ButtonHint("X", Tx.s(R.string.look_hints_search), Palette.Sky)
            ButtonHint("Start", Tx.s(R.string.look_hints_menu), Palette.Lavender)
        }
    }
}
