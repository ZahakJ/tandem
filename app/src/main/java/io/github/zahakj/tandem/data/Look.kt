package io.github.zahakj.tandem.data

import kotlinx.serialization.Serializable

/**
 * Everything about how Tandem looks, sounds and responds to buttons. Stored inside [Settings.look].
 *
 * Fields that are `null` follow the chosen theme; setting them overrides the theme. Every default here matches the
 * original Tandem look, so a fresh install (or "Reset appearance") looks exactly like Pastel Night.
 */
@Serializable
data class Look(
    /** A built-in theme id ("pastel-night"), or "pack:<folder>" / "cocoon:<folder>" for an imported theme pack. */
    val theme: String = "pastel-night",
    /** "#RRGGBB" to replace the theme's accent, or null for the theme's own. */
    val accent: String? = null,
    /** "item" = each system/collection glows in its own colour; "accent" = everything glows in the accent colour. */
    val highlightColor: String = "item",

    // Overrides of the theme (null = theme decides).
    val highlight: String? = null,
    val highlightThickness: Float? = null,
    val focusScale: Float? = null,
    val corner: Float? = null,
    val material: String? = null,
    val motion: String? = null,

    /** Game card shape: "rounded", "square", "circle", "portrait" (box art), "landscape". */
    val cardShape: String = "rounded",
    /** "small", "medium", "large", "huge", or "cols:<n>" for a fixed number of columns. */
    val gridSize: String = "medium",
    /** System tiles on the home screen: "pastel", "solid", "glass", "mono". */
    val systemTiles: String = "pastel",

    /** Wallpaper per screen: "theme", a backdrop style id ("blobs", "starfield"...), "hero", or "file:<path>". */
    val wallTop: String = "theme",
    val wallBottom: String = "theme",
    /** How much a picture or video wallpaper is darkened, so text stays readable. */
    val wallDim: Float = 0.35f,

    /** Home screen layout: "shelves" (rows), "grid", "carousel", "list" or "console". */
    val homeLayout: String = "shelves",
    /** How games show inside a system or collection: "grid", "list" or "carousel". */
    val gameView: String = "grid",
    /** Open folders and collections as a popup over the current screen instead of a new page. */
    val folderPopup: Boolean = false,
    /** Columns of tiles in the grid home layout. */
    val homeColumns: Int = 5,
    /** Share of the screen for the hero banner above the grid home layout; 0 = no split (the default). */
    val gridHeroSplit: Float = 0f,
    /** Icon grid home: how many icons fit across the screen (5 = large icons, 9 = small ones). */
    val gridAcross: Int = 7,
    /** Icon grid rows (sideways and paged scrolling): 0 works them out from the icon size; 1-5 fixes them (1 = one row of big icons with details above) and sizes the icons to fill the height. */
    val gridRows: Int = 0,
    /** Icon grid scrolling: "horizontal" (sideways), "paged" (a page at a time, like the 3DS) or "vertical". */
    val gridScroll: String = "horizontal",
    /** Icon corner roundness, as a share of the icon size (0 = square, 0.5 = circle). */
    val gridRoundness: Float = 0.2f,
    /** Space between icons, as a share of the icon size. */
    val gridGap: Float = 0.26f,
    /** Names under the icons. */
    val gridLabels: Boolean = false,
    /** The other screen in icon grid mode: "spotlight" (big icon, art and name) or "classic" (Tandem's details). */
    val gridDetails: String = "spotlight",
    /** Folders opened from the icon grid: "popup" (a big panel over the grid) or "fullscreen" (a page). */
    val gridFolders: String = "popup",
    /** What X does: "swap-grid" (swaps screens on the icon grid, searches elsewhere), "swap" (always swaps) or "search". */
    val xButton: String = "swap-grid",
    /**
     * Where system and collection art comes from: "auto" (the theme pack's own art, else the first Cocoon smart-folder
     * pack in ROMs/assets/smart_folders), "none" (drawn by Tandem), a theme pack id ("pack:x", "cocoon:x"), or a
     * smart-folder pack's folder name. See ThemePacks.artSource.
     */
    val iconPack: String = "auto",
    /** In the grid home layout the hero banner folds away once you move down to this row (1-based); 0 = never. */
    val noSplitFromRow: Int = 2,
    /** Show the "Now playing" card on the home screen while a game runs in the background. */
    val nowPlaying: Boolean = true,
    /** How long to rest on a game before its video snap starts, in ms. */
    val videoDelayMs: Int = 650,

    // The apps dock.
    /** "bar" (floating bar), "shelf" (a row like the others) or "minimal" (icons only). */
    val dockStyle: String = "bar",
    val dockTop: Boolean = false,
    val dockAutoHide: Boolean = false,
    val dockBackground: Boolean = true,
    val dockCount: Int = 8,
    /** "rounded", "circle" or "square". */
    val dockIconShape: String = "rounded",

    /** Home rows in order; a row that isn't listed is hidden. */
    val homeSections: List<String> = listOf("continue", "systems", "collections"),
    val fullscreenFolders: Boolean = false,
    val cornerHints: Boolean = false,

    // Bottom screen hero.
    /** Height of the hero banner in the details view, as a share of the screen. */
    val heroSplit: Float = 0.475f,
    /** Logo width as a share of the screen width. */
    val heroLogoScale: Float = 0.7f,
    /** Logo width over a system or folder's art on the other screen, as a share of it; 0 = Tandem's own size. */
    val heroFolderLogoScale: Float = 0f,
    /** "fade", "strong", "vignette", "none". */
    val heroGradient: String = "fade",
    /** Play video snaps from ROMs/downloaded_media/<system>/videos/ when a game is highlighted. */
    val videoSnaps: Boolean = true,
    /** Slow pan and zoom on hero art. */
    val kenBurns: Boolean = true,

    val battery: Boolean = true,
    /** "system", "12", "24", "off". */
    val clock: String = "system",

    /** Button prompt style: "tandem", "xbox", "playstation", "nintendo". */
    val prompts: String = "tandem",
    val swapAB: Boolean = false,
    val swapXY: Boolean = false,

    val singleScreen: Boolean = false,
    /** Library on the bottom screen, details on the top (X on the icon grid swaps them). */
    val swapScreens: Boolean = false,

    val sounds: Boolean = true,
    val soundVolume: Float = 0.5f,
    /** UI sound pack: "modern", "playful" (the original marimba set; "soft" before), "chip", "bubbly", "minimal". */
    val soundPack: String = "modern",
    val music: Boolean = true,
    val musicVolume: Float = 0.4f,
    /** Tandem's own soundtrack pack: "meadow" (Meadow Tales, acoustic) or "classic" (Pastel Arcade, synth and chiptune). */
    val musicPack: String = "meadow",
    /** Tandem's own soundtrack: "theme" (the pack's track that suits the theme), "shuffle" (within the pack), or a track id. */
    val musicTrack: String = "theme",
    /** Play the files in ROMs/tandem/music instead of the soundtrack, when there are any. */
    val myMusic: Boolean = true,
    /** Shuffle the files in ROMs/tandem/music. */
    val musicShuffle: Boolean = true,
    /** Little system jingles: "off" (the sound pack's launch sting), "launch" (when a game starts) or "all" (also when a system opens). */
    val jingles: String = "off",
    /** A game's own jingle (downloaded_media/<system>/jingles) plays when you rest on it. */
    val gameJingles: Boolean = true,
    /** Pip babbles when it shows a message. */
    val pipVoice: Boolean = true,

    val splash: Boolean = true,

    /** Per theme pack (by id): which parts of it are used. Missing = everything on. */
    val packParts: Map<String, PackParts> = emptyMap(),
    /** System icon style for drawn icons ("neon", "pixel"...), or null for the theme's own. */
    val iconStyle: String? = null,
)

/** Which parts of a theme pack are used. Each can be switched off on its own in Theme packs. */
@Serializable
data class PackParts(
    val colors: Boolean = true,
    val wallpaper: Boolean = true,
    /** System and collection icons. */
    val icons: Boolean = true,
    /** System heroes and logos. */
    val heroes: Boolean = true,
    /** Frames over game covers (a pack's icon overlays). Off until turned on: not every pack's frames suit every cover. */
    val frames: Boolean = false,
)
