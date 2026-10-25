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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import io.github.zahakj.tandem.MainActivity
import io.github.zahakj.tandem.Selected
import io.github.zahakj.tandem.Selection
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

/** A row that picks one of [options] (id to label); A and right step forward, left steps back. */
internal fun choice(
    label: String, icon: Glyph, color: Color, help: String,
    options: List<Pair<String, String>>, current: String,
    preview: (@Composable () -> Unit)? = null,
    set: (String) -> Unit,
): SettingRow {
    val ids = options.map { it.first }
    val step = { d: Int -> set(stepIn(ids, current, d)) }
    return SettingRow(
        label, options.firstOrNull { it.first == current }?.second ?: options.first().second, color, icon, help,
        onLeft = { step(-1) }, onRight = { step(1) }, preview = preview, onClick = { step(1) },
    )
}

/** A row for a number. `null` means "the theme decides", shown as [themeLabel] and reachable by stepping below [min]. */
internal fun number(
    label: String, icon: Glyph, color: Color, help: String,
    value: Float?, min: Float, max: Float, step: Float, format: (Float) -> String,
    themeValue: Float? = null,
    preview: (@Composable () -> Unit)? = null,
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
    val shown = if (value == null && themeValue != null) "Theme · ${format(themeValue)}" else format(value ?: min)
    val at = ((value ?: themeValue ?: min) - min) / (max - min).coerceAtLeast(0.0001f)
    return SettingRow(
        label, shown, color, icon, help,
        onLeft = { move(-1, wrap = false) }, onRight = { move(1, wrap = false) }, preview = preview, onClick = { move(1, wrap = true) },
        slider = at,
    )
}

internal fun toggle(label: String, icon: Glyph, color: Color, help: String, on: Boolean, preview: (@Composable () -> Unit)? = null, set: (Boolean) -> Unit) =
    SettingRow(label, onOff(on), color, icon, help, toggle = on, preview = preview, onClick = { set(!on) })

internal fun pct(v: Float) = "${Math.round(v * 100)}%"

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
    when (it) { "continue" -> "Continue"; "systems" -> "Systems"; "collections" -> "Collections"; else -> it }
}

/** The icon grid home's settings (the "grid" layout). */
private fun iconGridSettingRows(look: Look): List<SettingRow> {
    val preview: @Composable () -> Unit = { LayoutPreview("grid", Modifier.fillMaxWidth().aspectRatio(1.6f)) }
    return listOf(
        choice(
            "Icon size", Glyph.Grid, Palette.Sky,
            "How many icons fit across the screen, like the 3DS HOME menu's zoom. L1 and R1 change it on the grid too.",
            (5..9).map { "$it" to "${IconGrid.sizeName(it)} · $it across" }, look.gridAcross.toString(), preview = preview,
        ) { v -> updateLook { it.copy(gridAcross = v.toInt()) } },
        choice(
            "Rows of icons", Glyph.Grid, Palette.Sky,
            "Fix the number of rows and the icons grow to fill the screen. One row is the 3DS HOME menu zoomed all the way in: big icons with the highlighted item's details above them. Auto follows Icon size. L1 and R1 change the rows on the grid (L1 on Large icons goes on to fewer rows).",
            listOf("0" to "Auto", "1" to "1 row") + (2..5).map { "$it" to "$it rows" }, look.gridRows.toString(), preview = preview,
        ) { v -> updateLook { it.copy(gridRows = v.toInt()) } },
        choice(
            "Scrolling", Glyph.Motion, Palette.Sky,
            "Sideways with a peek of what's next, a page at a time (like the 3DS), or up and down.",
            listOf("horizontal" to "Sideways", "paged" to "Pages", "vertical" to "Up and down"), look.gridScroll, preview = preview,
        ) { v -> updateLook { it.copy(gridScroll = v) } },
        number(
            "Icon roundness", Glyph.Corners, Palette.Sky, "From square corners to circles.",
            look.gridRoundness, 0f, 0.5f, 0.05f, ::pct, preview = preview,
        ) { v -> updateLook { it.copy(gridRoundness = v ?: 0.2f) } },
        number(
            "Space between icons", Glyph.Grid, Palette.Sky, "The gap between icons, as a share of an icon.",
            look.gridGap, 0.1f, 0.5f, 0.04f, ::pct, preview = preview,
        ) { v -> updateLook { it.copy(gridGap = v ?: 0.26f) } },
        toggle("Names under icons", Glyph.Text, Palette.Sky, "Write each icon's name under it.", look.gridLabels, preview = preview) { on ->
            updateLook { it.copy(gridLabels = on) }
        },
        number(
            "Hero split", Glyph.Screens, Palette.Sky,
            "Art of the highlighted icon above the grid. No split (the default) gives the icons the whole screen.",
            look.gridHeroSplit, 0f, 0.5f, 0.05f, { if (it <= 0f) "No split" else pct(it) }, preview = preview,
        ) { v -> updateLook { it.copy(gridHeroSplit = v ?: 0f) } },
        choice(
            "Other screen", Glyph.Screens, Palette.Sky,
            "Spotlight shows the highlighted icon big over its colour (or its icon pack art) and games full-screen. Classic shows Tandem's details.",
            listOf("spotlight" to "Spotlight", "classic" to "Classic details"), look.gridDetails,
        ) { v -> updateLook { it.copy(gridDetails = v) } },
        choice(
            "Folders open", Glyph.Folder, Palette.Sky, "As a big rounded panel over the grid, or as a full page.",
            listOf("popup" to "Over the grid", "fullscreen" to "Full page"), look.gridFolders,
        ) { v -> updateLook { it.copy(gridFolders = v) } },
        choice(
            "X button", Glyph.Swap, Palette.Sky,
            "Swap the two screens with X on the icon grid (search is in the Y menu), everywhere, or keep X for search.",
            listOf("swap-grid" to "Swap on the grid", "swap" to "Swap everywhere", "search" to "Search"), look.xButton,
        ) { v -> updateLook { it.copy(xButton = v) } },
        choice(
            "Icon pack", Glyph.Image, Palette.Sky,
            "System and collection icons from a Cocoon smart-folder pack in ROMs/assets/smart_folders. Auto uses your Cocoon theme's own pack. Without one, Tandem draws its own.",
            ThemePacks.iconSources(), look.iconPack,
        ) { v -> updateLook { it.copy(iconPack = v) } },
    )
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
 * added here that no page picks still shows, under its page's "More options".
 */
@Composable
fun appearanceSections(): List<SettingSection> {
    val ctx = LocalContext.current
    val activity = ctx as? MainActivity
    val look = Store.settings.look
    val settings = Store.settings
    val base = Themes.byId(look.theme) ?: BuiltInThemes.PastelNight
    // Read in the background (Facts), never while composing: Settings opens without touching the disk.
    Facts.ensure(ctx)
    val wallFiles = Facts.wallpapers
    val trackCount = Facts.musicTracks

    val wallOptions = buildList {
        add("theme" to "Theme default")
        BackdropStyle.entries.forEach { add(it.id to it.label) }
        add("hero" to "Game art (blurred)")
        wallFiles.forEach { add("file:${it.path}" to it.name) }
    }
    val accentOptions = listOf("" to "Theme") + AccentSwatches.map { it.second.toHex() to it.first } +
        (look.accent?.takeIf { a -> AccentSwatches.none { it.second.toHex() == a } }?.let { listOf(it to it) } ?: emptyList())

    return listOf(
        SettingSection(
            "Colour & highlight",
            listOf(
                choice(
                    "Accent colour", Glyph.Sparkle, Palette.Accent,
                    "Your own accent for toggles and highlights. Left and right try the swatches; A lets you type any colour, like #FF7AB6.",
                    accentOptions, look.accent ?: "",
                    preview = { AccentPreview() },
                ) { id ->
                    if (id.isEmpty()) updateLook { it.copy(accent = null, highlightColor = "item") }
                    else updateLook { it.copy(accent = id, highlightColor = "accent") }
                }.copy(onClick = {
                    TextEntry.ask("Accent colour (hex)", look.accent ?: "#") { text ->
                        val c = parseColor(text)
                        if (c == null) { Toasts.show("That doesn't look like a colour"); Sounds.error() }
                        else updateLook { it.copy(accent = c.toHex(), highlightColor = "accent") }
                    }
                }),
                choice(
                    "Highlight colour", Glyph.Highlight, Palette.Accent,
                    "Each system and collection glows in its own colour, or everything glows in your accent.",
                    listOf("item" to "Item colour", "accent" to "Accent"), look.highlightColor,
                    preview = { LookPreview() },
                ) { v -> updateLook { it.copy(highlightColor = v) } },
                choice(
                    "Highlight style", Glyph.Highlight, Palette.Lavender,
                    "How the highlighted card is marked: a soft glow, a crisp outline, a lift with a big shadow, or a gently pulsing ring.",
                    listOf("" to "Theme · ${base.highlight.label}") + HighlightStyle.entries.map { it.id to it.label }, look.highlight ?: "",
                    preview = { LookPreview() },
                ) { v -> updateLook { it.copy(highlight = v.ifEmpty { null }) } },
                number(
                    "Highlight thickness", Glyph.Corners, Palette.Lavender, "The width of the ring around the highlighted card.",
                    look.highlightThickness, 0f, 8f, 1f, { "${it.toInt()} dp" }, themeValue = base.highlightThickness,
                    preview = { LookPreview() },
                ) { v -> updateLook { it.copy(highlightThickness = v) } },
                number(
                    "Focus zoom", Glyph.Grid, Palette.Lavender,
                    "How much the highlighted card grows. Big rows and wide tiles always grow a little less, so nothing spills off the screen.",
                    look.focusScale, 1f, 1.2f, 0.02f, { "${Math.round((it - 1) * 100)}%" }, themeValue = base.focusScale,
                    preview = { LookPreview() },
                ) { v -> updateLook { it.copy(focusScale = v) } },
                number(
                    "Card corners", Glyph.Corners, Palette.Sky, "How round the corners of cards and tiles are.",
                    look.corner, 0f, 32f, 2f, { "${it.toInt()} dp" }, themeValue = base.corner,
                    preview = { LookPreview() },
                ) { v -> updateLook { it.copy(corner = v) } },
                choice(
                    "Surface", Glyph.Layers, Palette.Sky,
                    "What cards are made of: solid, frosted glass that lets the wallpaper glow through, or light outlines.",
                    listOf("" to "Theme · ${base.material.label}") + SurfaceMaterial.entries.map { it.id to it.label }, look.material ?: "",
                    preview = { LookPreview() },
                ) { v -> updateLook { it.copy(material = v.ifEmpty { null }) } },
            ),
        ),
        SettingSection(
            "System icons",
            listOf(
                choice(
                    "Icon style", Glyph.Gamepad, Palette.Accent,
                    "How Tandem draws system icons when a theme pack doesn't bring its own: a little device of the right kind with the system's name, as glossy bubbles, neon tubes, pixels, LCD, chrome and more.",
                    listOf("" to "Theme · ${base.iconStyle.label}") + IconStyle.entries.map { it.id to it.label }, look.iconStyle ?: "",
                    preview = { IconsPreview(drawn = true) },
                ) { v -> updateLook { it.copy(iconStyle = v.ifEmpty { null }) } },
                choice(
                    "Icons from", Glyph.Image, Palette.Accent,
                    "Use a pack's system icons, heroes and logos with any theme, or always draw them. Auto uses the theme pack's own art; with a built-in theme it borrows the first Cocoon smart-folder pack in ROMs/assets/smart_folders. Drawn by Tandem shows the theme's icon style.",
                    ThemePacks.iconSources(), look.iconPack,
                    preview = { IconsPreview(drawn = false) },
                ) { v -> updateLook { it.copy(iconPack = v) } },
            ),
        ),
        SettingSection(
            "Motion",
            listOf(
                choice(
                    "Animation style", Glyph.Motion, Palette.Mint,
                    "Bouncy is playful, Smooth glides without overshoot, Minimal is quick and subtle, Off changes things instantly.",
                    listOf("" to "Theme · ${base.animation.label}") + AnimationStyle.entries.map { it.id to it.label }, look.motion ?: "",
                    preview = { MotionPreview() },
                ) { v -> updateLook { it.copy(motion = v.ifEmpty { null }) } },
                toggle(
                    "Reduce motion", Glyph.Motion, Palette.Mint,
                    "Turns off moving wallpapers, panning art and bouncy animations everywhere. Battery saver does the same for wallpapers.",
                    settings.reduceMotion,
                ) { on -> Store.updateSettings { it.copy(reduceMotion = on) } },
            ),
        ),
        SettingSection(
            "Wallpaper",
            listOf(
                choice(
                    "Top screen", Glyph.Image, Palette.Peach,
                    "An animated backdrop, the highlighted game's art, or your own picture or video. Put files in ROMs/tandem/wallpapers.",
                    wallOptions, look.wallTop.takeIf { w -> wallOptions.any { it.first == w } } ?: "theme",
                    preview = { WallpaperPreview(look.wallTop, ScreenSlot.Top) },
                ) { v -> updateLook { it.copy(wallTop = v) } },
                choice(
                    "Bottom screen", Glyph.Image, Palette.Peach,
                    "The same choices for the second screen. Game art and videos look great down there.",
                    wallOptions, look.wallBottom.takeIf { w -> wallOptions.any { it.first == w } } ?: "theme",
                    preview = { WallpaperPreview(look.wallBottom, ScreenSlot.Bottom) },
                ) { v -> updateLook { it.copy(wallBottom = v) } },
                number(
                    "Picture dimming", Glyph.Image, Palette.Peach, "Darkens picture and video wallpapers so text on top stays easy to read.",
                    look.wallDim, 0f, 0.8f, 0.05f, ::pct,
                ) { v -> updateLook { it.copy(wallDim = v ?: 0.35f) } },
                SettingRow(
                    "Wallpaper folder", if (wallFiles.isEmpty()) "Empty" else "${wallFiles.size} files", Palette.Peach, Glyph.Folder,
                    "Copy pictures (PNG, JPG, WebP) or videos (MP4, WebM) into ROMs/tandem/wallpapers and they show up in the lists above.",
                ) {
                    File(wallpaperDir.path).mkdirs()
                    Facts.refresh(ctx)
                    Toasts.show(if (wallFiles.isEmpty()) "Add pictures or videos to ROMs/tandem/wallpapers" else "${wallFiles.size} wallpapers ready")
                },
            ),
        ),
        SettingSection(
            "Layout",
            listOf(
                choice(
                    "Home layout", Glyph.Home, Palette.Butter,
                    "Shelves of rows, an icon grid, a coverflow carousel, a compact list, or a console-style dashboard.",
                    HomeLayouts, look.homeLayout, preview = { LayoutPreview(look.homeLayout, Modifier.fillMaxWidth().aspectRatio(1.6f)) },
                ) { v -> updateLook { it.copy(homeLayout = v) } },
                choice(
                    "Games view", Glyph.Grid, Palette.Butter,
                    "How games show inside a system or collection: a grid of cards, a list with details (great for big libraries), or a coverflow.",
                    GameViews, look.gameView, preview = { LayoutPreview("view-${look.gameView}", Modifier.fillMaxWidth().aspectRatio(1.6f)) },
                ) { v -> updateLook { it.copy(gameView = v) } },
                choice(
                    "Grid size", Glyph.Grid, Palette.Butter, "How big game cards are in a system, or a fixed number of columns.",
                    Grid.sizes, look.gridSize, preview = { GridPreview() },
                ) { v -> updateLook { it.copy(gridSize = v) } },
                choice(
                    "Card shape", Glyph.Corners, Palette.Butter,
                    "Rounded, square, circle, tall box-art cards, or wide cards for screenshots and banners.",
                    Grid.shapes, look.cardShape, preview = { GridPreview() },
                ) { v -> updateLook { it.copy(cardShape = v) } },
                choice(
                    "System tiles", Glyph.Highlight, Palette.Butter, "The look of the system tiles on the home screen.",
                    Grid.tileStyles, look.systemTiles,
                ) { v -> updateLook { it.copy(systemTiles = v) } },
                toggle(
                    "Game titles", Glyph.Text, Palette.Butter, "Write each game's name under its card.", settings.showTitles,
                    preview = { GridPreview() },
                ) { on -> Store.updateSettings { it.copy(showTitles = on) } },
                choice(
                    "Home rows", Glyph.Home, Palette.Butter, "Which rows the home screen shows, and in what order.",
                    HomeRowOrders.map { it.joinToString(",") to homeLabel(it) }, look.homeSections.joinToString(","),
                ) { v -> updateLook { it.copy(homeSections = v.split(",")) } },
                choice(
                    "Open folders", Glyph.Folder, Palette.Butter,
                    "As a page, as a fullscreen page (the top bar slides away so the grid gets the whole screen), or as a popup over the screen you're on.",
                    listOf("page" to "Page", "fullscreen" to "Fullscreen", "popup" to "Popup"),
                    when { look.folderPopup -> "popup"; look.fullscreenFolders -> "fullscreen"; else -> "page" },
                ) { v -> updateLook { it.copy(folderPopup = v == "popup", fullscreenFolders = v == "fullscreen") } },
                toggle(
                    "Now playing", Glyph.Gamepad, Palette.Butter,
                    "While a game runs in the background, show it at the top of the home screen so you can jump back in.", look.nowPlaying,
                ) { on -> updateLook { it.copy(nowPlaying = on) } },
                toggle(
                    "Corner hints", Glyph.Gamepad, Palette.Butter, "A small reminder of the buttons in the corner of the top screen.",
                    look.cornerHints,
                ) { on -> updateLook { it.copy(cornerHints = on) } },
            ),
        ),
        SettingSection(
            "Icon grid",
            iconGridSettingRows(look),
        ),
        SettingSection(
            "Dock",
            listOf(
                toggle(
                    "Apps dock", Glyph.Grid, Palette.Lavender, "Your favourite Android apps on the home screen. Press Y on one to move or remove it.",
                    io.github.zahakj.tandem.data.HomeStore.settings.showDock,
                ) { on -> io.github.zahakj.tandem.data.HomeStore.updateSettings { it.copy(showDock = on) } },
                choice(
                    "Dock style", Glyph.Layers, Palette.Lavender, "A floating bar, a full-width shelf, or just the icons. Applies to every layout except Shelves.",
                    DockStyles, look.dockStyle,
                ) { v -> updateLook { it.copy(dockStyle = v) } },
                toggle("Dock on top", Glyph.Swap, Palette.Lavender, "Put the dock above the tiles instead of below them.", look.dockTop) { on ->
                    updateLook { it.copy(dockTop = on) }
                },
                toggle(
                    "Auto-hide dock", Glyph.Eye, Palette.Lavender, "The dock stays small and faded until you move into it.", look.dockAutoHide,
                ) { on -> updateLook { it.copy(dockAutoHide = on) } },
                toggle(
                    "Dock background", Glyph.Layers, Palette.Lavender, "A soft panel behind the dock. Turn it off for icons floating over the wallpaper.",
                    look.dockBackground,
                ) { on -> updateLook { it.copy(dockBackground = on) } },
                number(
                    "Icons on dock", Glyph.Grid, Palette.Lavender, "The most apps the dock shows (All apps is always there too). Eight fill the top screen; more would run off its edge.",
                    look.dockCount.toFloat().coerceAtMost(8f), 3f, 8f, 1f, { "${it.toInt()}" },
                ) { v -> updateLook { it.copy(dockCount = (v ?: 8f).toInt()) } },
                choice(
                    "Dock icon shape", Glyph.Corners, Palette.Lavender, "Rounded squares, circles or squares for app icons.",
                    listOf("rounded" to "Rounded", "circle" to "Circle", "square" to "Square"), look.dockIconShape,
                ) { v -> updateLook { it.copy(dockIconShape = v) } },
            ),
        ),
        SettingSection(
            "Bottom screen",
            listOf(
                choice(
                    "Style", Glyph.Screens, Palette.Sky,
                    "Details shows art above the game's info. Art + info lets the moving art fill the screen with info on top. Animated art is pure eye candy. Clock keeps it calm.",
                    BottomModes, settings.bottomMode, preview = { BottomPreview(settings.bottomMode) },
                ) { v -> Store.updateSettings { it.copy(bottomMode = v) } },
                number(
                    "Art height", Glyph.Screens, Palette.Sky, "How much of the details screen the game's art takes.",
                    look.heroSplit, 0.3f, 0.65f, 0.05f, ::pct, preview = { BottomPreview("details") },
                ) { v -> updateLook { it.copy(heroSplit = v ?: 0.475f) } },
                number(
                    "Logo size", Glyph.Image, Palette.Sky, "The size of the game's logo over its art.",
                    look.heroLogoScale, 0.3f, 1f, 0.1f, ::pct,
                ) { v -> updateLook { it.copy(heroLogoScale = v ?: 0.7f) } },
                choice(
                    "Art fade", Glyph.Layers, Palette.Sky, "How the art blends into the background below it.",
                    Hero.gradients, look.heroGradient,
                ) { v -> updateLook { it.copy(heroGradient = v) } },
                choice(
                    "Video snaps", Glyph.Image, Palette.Sky,
                    "Plays a game's video (from ROMs/downloaded_media/<system>/videos) after you rest on it. Fast starts sooner; Battery saver waits longer so quick scrolling never decodes video. Always muted, and stopped while you play.",
                    listOf("off" to "Off", "350" to "Fast", "650" to "Balanced", "1500" to "Battery saver"),
                    if (!look.videoSnaps) "off" else look.videoDelayMs.toString().takeIf { it in setOf("350", "650", "1500") } ?: "650",
                ) { v -> updateLook { if (v == "off") it.copy(videoSnaps = false) else it.copy(videoSnaps = true, videoDelayMs = v.toInt()) } },
                toggle(
                    "Moving art", Glyph.Motion, Palette.Sky, "Game art slowly pans and zooms, like a living poster.", look.kenBurns,
                ) { on -> updateLook { it.copy(kenBurns = on) } },
            ),
        ),
        SettingSection(
            "Top bar",
            listOf(
                toggle("Battery", Glyph.Battery, Palette.Mint, "Show the battery percentage next to the clock.", look.battery) { on ->
                    updateLook { it.copy(battery = on) }
                },
                choice(
                    "Clock", Glyph.Clock, Palette.Mint, "12-hour, 24-hour, whatever the system uses, or no clock at all.",
                    Clock.formats, look.clock, preview = { ClockPreview() },
                ) { v -> updateLook { it.copy(clock = v) } },
            ),
        ),
        SettingSection(
            "Controls",
            listOf(
                choice(
                    "Button prompts", Glyph.Gamepad, Palette.Lavender, "How buttons are drawn in hints: Tandem's pastels, or Xbox, PlayStation or Nintendo style.",
                    Prompts.styles, look.prompts, preview = { PromptPreview() },
                ) { v -> updateLook { it.copy(prompts = v) } },
                toggle(
                    "Swap A and B", Glyph.Swap, Palette.Lavender, "Confirm with B and go back with A, like on a Nintendo console. Hints follow along.",
                    look.swapAB, preview = { PromptPreview() },
                ) { on -> updateLook { it.copy(swapAB = on) } },
                toggle(
                    "Swap X and Y", Glyph.Swap, Palette.Lavender, "Search with Y and open options with X.", look.swapXY,
                    preview = { PromptPreview() },
                ) { on -> updateLook { it.copy(swapXY = on) } },
            ),
        ),
        SettingSection(
            "Screens",
            listOf(
                toggle(
                    "Single screen", Glyph.Screens, Palette.Peach,
                    "Leave the second screen alone (or turned off) and use only the top one. Games always start on the top screen too.", io.github.zahakj.tandem.data.SystemPrefs.data.singleScreen,
                ) { on -> io.github.zahakj.tandem.data.SystemPrefs.update { it.copy(singleScreen = on) }; activity?.showDetails() },
                toggle(
                    "Swap screens", Glyph.Swap, Palette.Peach,
                    "The library moves to the bottom (touch) screen and the details go on top, instantly. X does this on the icon grid. Games still open on the top screen.",
                    look.swapScreens,
                ) { on -> updateLook { it.copy(swapScreens = on) }; activity?.applyScreenRoles() },
            ),
        ),
        soundSettings(trackCount),
        SettingSection(
            "Extras",
            listOf(
                toggle("Startup splash", Glyph.Sparkle, Palette.Mint, "Pip wakes up while Tandem gathers your games. With games already found, the home shows at once.", look.splash) { on ->
                    updateLook { it.copy(splash = on) }
                },
                SettingRow(
                    "Language", "English", Palette.Mint, Glyph.Text,
                    "Tandem is in English for now. Translations are very welcome: see the project page on GitHub.",
                ) { Toasts.show("More languages are on the way") },
                SettingRow(
                    "Reload theme packs", "${Themes.packs.size} found", Palette.Mint, Glyph.Refresh,
                    "Looks for theme packs in ROMs/tandem/themes (Tandem format) and ROMs/themes (Cocoon themes).",
                ) {
                    ThemePacks.reload()
                    Toasts.show(if (Themes.packs.isEmpty()) "No theme packs found" else "Found ${Themes.packs.size} theme packs")
                },
                SettingRow(
                    "Reset appearance", "Pastel Night", Palette.Peach, Glyph.Restore,
                    "Back to Tandem's original look and sound. Your library, favourites and play time aren't touched.",
                    keywords = "default factory start over",
                ) {
                    // Every look setting at once can't be undone, so it asks first.
                    Confirm.hold("Reset the whole look?", "Theme, layout, wallpapers, buttons and sounds go back to how Tandem first looked. Games, favourites and play time stay.", "Reset") {
                        io.github.zahakj.tandem.data.Backups.auto(ctx, "reset appearance")
                        Store.updateSettings { it.copy(look = Look(), reduceMotion = false, bottomMode = "details") }
                        activity?.applyScreenRoles()
                        Music.refresh()
                        Toasts.show("Back to Pastel Night")
                    }
                },
            ),
        ),
    )
}

/** The galleries at the top of Look & feel: built-in themes, then theme packs (with the pack manager card). */
fun lookHeader(routeKey: String): SettingsHeaderSpec = SettingsHeaderSpec(
    keys = listOf(emptyList(), builtInThemeKeys(), emptyList(), packThemeKeys()),
    content = { target, onFocusKey ->
        val look = Store.settings.look
        item(key = "gallery-title") { SettingsHeader("Themes", "A uses one · packs below") }
        item(key = "gallery") { BuiltInThemeRow(look.theme, target, onFocusKey) }
        item(key = "packs-title") { SettingsHeader("Theme packs", "Cocoon · Tandem") }
        item(key = "packs") { PackThemeRow(look.theme, target, onFocusKey) }
    },
    row = { key -> themeGalleryRow(key) },
)

/** The layout gallery at the top of Home & layout. */
fun layoutHeader(routeKey: String): SettingsHeaderSpec = SettingsHeaderSpec(
    keys = listOf(emptyList(), HomeLayouts.map { "layout:${it.first}" }),
    content = { target, onFocusKey ->
        item(key = "layouts-title") { SettingsHeader("Home layout", "A switches") }
        item(key = "layouts") { LayoutGallery(routeKey, Store.settings.look.homeLayout, target, onFocusKey) }
    },
    row = { key ->
        if (!key.startsWith("layout:")) return@SettingsHeaderSpec null
        val id = key.removePrefix("layout:")
        val look = Store.settings.look
        SettingRow(
            HomeLayouts.firstOrNull { it.first == id }?.second ?: id, if (id == look.homeLayout) "In use" else "Layout",
            Palette.Butter, Glyph.Home, LayoutBlurbs[id],
            preview = { LayoutPreview(id, Modifier.fillMaxWidth().aspectRatio(1.6f)) },
        ) {}
    },
)

/** The side panel for a theme or pack card in the galleries. */
private fun themeGalleryRow(key: String): SettingRow? {
    val look = Store.settings.look
    if (key == MANAGE_PACKS_KEY) {
        return SettingRow(
            "Theme packs", if (ThemePacks.packs.isEmpty()) "None yet" else "${ThemePacks.packs.size} installed", Palette.Accent, Glyph.Download,
            "Import Tandem packs or Cocoon packs from a folder or a .zip: colours, wallpapers, system icons, heroes and logos. " +
                "Packs Cocoon already downloaded to ROMs/themes show up by themselves. Choose which parts of a pack to use, or remove it.",
            opens = true,
        ) {}
    }
    if (!key.startsWith("theme:")) return null
    val id = key.removePrefix("theme:")
    ThemePacks.byId(id)?.let { p ->
        val spec = Themes.withParts(p.spec, look)
        return SettingRow(
            p.name, if (p.id == look.theme) "In use" else p.format.label, spec.accent, Glyph.Theme,
            listOfNotNull(
                p.description.lineSequence().firstOrNull()?.take(140)?.ifBlank { null },
                p.author.ifBlank { null }?.let { "By $it." },
                p.contents.replaceFirstChar { it.uppercase() } + ".",
                p.credits.takeIf { it.isNotEmpty() }?.joinToString(", ", prefix = "Art: "),
                "A uses it; the pack manager chooses which parts.",
            ).joinToString("  "),
            // One miniature, scaled to the panel: two stacked previews would spill over the hints.
            preview = { ThemeMiniature(spec, p, Modifier.fillMaxWidth().aspectRatio(1.6f).clip(RoundedCornerShape(16.dp)), moving = true, tile = 56.dp) },
        ) {}
    }
    val t = Themes.byId(id) ?: return null
    return SettingRow(
        t.name, if (t.id == look.theme) "In use" else t.source, t.accent, Glyph.Theme,
        t.tagline + ". " + (if (t.id in BuiltInThemes.gameIds) "Its own animated backdrop and ${t.iconStyle.label.lowercase()} system icons. " else "") + "A uses it.",
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

@Composable
private fun AccentPreview() {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        LookPreview()
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AccentSwatches.take(7).forEach { (_, c) ->
                val on = Store.settings.look.accent == c.toHex()
                Box(Modifier.size(if (on) 20.dp else 16.dp).clip(CircleShape).background(c))
            }
        }
    }
}

/** A ball that slides back and forth using the current animation style. */
@Composable
private fun MotionPreview() {
    val x = remember { Animatable(0f) }
    LaunchedEffect(Motion.style) {
        while (true) {
            x.animateTo(1f, Motion.move()); delay(700)
            x.animateTo(0f, Motion.move()); delay(700)
        }
    }
    Box(Modifier.fillMaxWidth().height(64.dp).clip(RoundedCornerShape(18.dp)).background(Palette.NightDeep.copy(alpha = 0.6f)).padding(12.dp)) {
        Box(
            Modifier.size(40.dp).graphicsLayer { translationX = x.value * (size.width * 4.2f) }
                .clip(Palette.cardShape).background(Brush.linearGradient(listOf(Palette.Accent, Palette.Lavender))),
        )
    }
}

@Composable
private fun WallpaperPreview(choice: String, slot: ScreenSlot) {
    val tint = Palette.Lavender
    val aspect = if (slot == ScreenSlot.Top) 16f / 9f else 0.87f
    Box(Modifier.fillMaxWidth(if (slot == ScreenSlot.Top) 1f else 0.6f).aspectRatio(aspect).clip(RoundedCornerShape(16.dp))) {
        val theme = Themes.live
        when {
            choice == "theme" -> {
                val f = if (slot == ScreenSlot.Top) theme.wallpaperTop else theme.wallpaperBottom
                if (f != null) MediaThumb(f) else Backdrop(theme.backdrop, tint)
            }
            choice == "hero" -> {
                Backdrop(theme.backdrop, tint)
                Text("Follows the highlighted game", Modifier.align(Alignment.Center).padding(12.dp), style = MaterialTheme.typography.labelLarge)
            }
            choice.startsWith("file:") -> MediaThumb(File(choice.removePrefix("file:")))
            else -> Backdrop(BackdropStyle.of(choice) ?: theme.backdrop, tint)
        }
    }
}

@Composable
private fun MediaThumb(file: File) {
    if (file.extension.lowercase() in ThemePacks.videoExtensions) LoopingVideo(file, Modifier.fillMaxSize())
    else AsyncImage(file, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
}

/** Six of the user's systems as icons: drawn in the current style, or as they show now (pack art or drawn). */
@Composable
private fun IconsPreview(drawn: Boolean) {
    val systems = previewSystems(6)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        systems.take(6).chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { (id, short) ->
                    val lib = io.github.zahakj.tandem.Libraries.byId(id)
                    if (!drawn && lib != null) SystemIcon(lib.platform, 72.dp)
                    else if (!drawn) {
                        val pack = ThemePacks.systemIcon(id)
                        if (pack != null) AsyncImage(pack, null, Modifier.size(72.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
                        else DrawnIcon(short, Devices.of(id), Palette.forName(id), 72.dp)
                    } else DrawnIcon(short, Devices.of(id), Palette.forName(id), 72.dp)
                }
            }
        }
    }
}

/** A few cards in the chosen grid size and shape. */
@Composable
private fun GridPreview() {
    val aspect = Grid.aspect(Store.settings.showTitles)
    val cols = Store.settings.look.gridSize.removePrefix("cols:").toIntOrNull()
        ?: when (Store.settings.look.gridSize) { "small" -> 6; "large" -> 4; "huge" -> 3; else -> 5 }
    val shape = Grid.gameShape
    val colors = Palette.accents
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(2) { r ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(cols.coerceAtMost(8)) { i ->
                    Column(Modifier.weight(1f)) {
                        Box(
                            Modifier.fillMaxWidth().aspectRatio(if (Store.settings.showTitles) aspect / 0.82f else aspect).clip(shape)
                                .background(Brush.linearGradient(listOf(colors[(i + r) % colors.size], Palette.CardHigh))),
                        )
                        if (Store.settings.showTitles) {
                            Spacer(Modifier.height(3.dp))
                            Box(Modifier.fillMaxWidth(0.7f).height(3.dp).clip(CircleShape).background(Palette.Muted).align(Alignment.CenterHorizontally))
                        }
                    }
                }
            }
        }
    }
}

/** A sketch of the bottom screen in the given style. */
@Composable
private fun BottomPreview(mode: String) {
    val split = Store.settings.look.heroSplit
    Box(Modifier.fillMaxWidth(0.62f).aspectRatio(0.87f).clip(RoundedCornerShape(16.dp)).background(Palette.NightDeep)) {
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
            else -> Column(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxWidth().weight(split).background(art))
                Column(Modifier.fillMaxWidth().weight(1f - split).padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { PreviewLines() }
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

@Composable
private fun ClockPreview() {
    val now by rememberMinute()
    Text(Clock.format(now) ?: "No clock", style = MaterialTheme.typography.displaySmall)
}

@Composable
private fun PromptPreview() {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ButtonHint("A", "Play", Palette.Mint, tappable = false)
        ButtonHint("B", "Back", Palette.Peach, tappable = false)
        ButtonHint("X", "Search", Palette.Sky, tappable = false)
        ButtonHint("Y", "Options", Palette.Butter, tappable = false)
    }
}

private val LayoutBlurbs = mapOf(
    "shelves" to "Rows you scroll through: continue playing, systems, collections and your apps. Tandem's classic home.",
    "grid" to "Every system, folder, app and game as a square icon on one dense grid that scrolls sideways, with a pill dock. The other screen shows the highlighted icon big; X swaps the screens.",
    "carousel" to "A coverflow: the highlighted card sits big in the middle and the others turn away to the sides.",
    "list" to "Compact rows with a big preview beside them. Fast to scan, great for huge libraries.",
    "console" to "A console-style dashboard: the highlighted item's art fills the screen with one row of big tiles along the bottom.",
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
 * ("view-grid", "view-list", "view-carousel"). One Canvas, no images.
 */
@Composable
fun LayoutPreview(id: String, modifier: Modifier = Modifier, rounded: Boolean = true) {
    val t = Themes.live
    val look = Store.settings.look
    Canvas(modifier.clip(RoundedCornerShape(if (rounded) 16.dp else 0.dp))) {
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
            "grid" -> {
                // The icon grid as it really lays out: square icons, a peek of the next column, a pill dock.
                val split = if (look.gridHeroSplit <= 0f) 0f else h * look.gridHeroSplit.coerceIn(0.15f, 0.5f)
                if (split > 0) {
                    drawRect(Brush.linearGradient(listOf(t.lavender, t.peach, t.background)), Offset.Zero, Size(w, split))
                    bar(6 * u, split - 10 * u, w * 0.3f, t.text, 5f)
                }
                val across = look.gridAcross.coerceIn(4, 10)
                val g = look.gridGap.coerceIn(0.08f, 0.6f)
                val sideways = look.gridScroll == "horizontal"
                val peek = if (sideways) 0.42f else 0f
                val margin = w * 0.036f
                var tw = (w - margin * 2) / (across + (across - 1) * g + if (peek > 0f) g + peek else 0f)
                val gap = tw * g
                val label = if (look.gridLabels) tw * 0.22f else 0f
                val dockH = 16 * u
                val areaTop = split + 4 * u
                val areaH = h - areaTop - dockH
                tw = minOf(tw, areaH - label)
                val rows = maxOf(1, ((areaH + gap) / (tw + label + gap)).toInt())
                val gridH = rows * (tw + label) + (rows - 1) * gap
                val top = areaTop + (areaH - gridH) / 2
                val cr = CornerRadius(tw * look.gridRoundness.coerceIn(0f, 0.5f))
                val cols = across + if (sideways) 1 else 0
                for (c in 0 until cols) for (row in 0 until rows) {
                    val x = margin + c * (tw + gap)
                    val y = top + row * (tw + label + gap)
                    val a = accents[(c * rows + row) % accents.size]
                    val fade = if (sideways && c == cols - 1) 0.45f else 1f
                    drawRoundRect(
                        Brush.linearGradient(listOf(a, androidx.compose.ui.graphics.lerp(a, t.background, 0.35f)), Offset(x, y), Offset(x + tw, y + tw)),
                        Offset(x, y), Size(tw, tw), cr, alpha = fade,
                    )
                    if (label > 0f) drawRoundRect(t.muted, Offset(x + tw * 0.2f, y + tw + label * 0.35f), Size(tw * 0.6f, label * 0.3f), CornerRadius(label), alpha = fade)
                    if (c == 0 && row == 0) {
                        val o = 1.6f * u
                        drawRoundRect(t.text, Offset(x - o, y - o), Size(tw + 2 * o, tw + 2 * o), CornerRadius(cr.x + o), style = Stroke(1.1f * u))
                    }
                }
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
        Nav.menuFor == null && !Nav.search && !Nav.groupMenu && !(IconGrid.enabled && Nav.route == Route.Home)
    val alpha by animateFloatAsState(if (show) 1f else 0f, Motion.fade(250), label = "hints")
    if (alpha == 0f) return
    val game = Selection.current is Selected.GameItem
    Box(Modifier.fillMaxSize().padding(14.dp).graphicsLayer { this.alpha = alpha }, contentAlignment = Alignment.BottomEnd) {
        Row(
            Modifier.clip(RoundedCornerShape(50)).background(Palette.Card.copy(alpha = 0.82f)).padding(horizontal = 12.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            ButtonHint("A", if (game) "Play" else "Open", Palette.Mint)
            if (game) ButtonHint("Y", "Options", Palette.Butter)
            ButtonHint("X", "Search", Palette.Sky)
            ButtonHint("Start", "Menu", Palette.Lavender)
        }
    }
}
