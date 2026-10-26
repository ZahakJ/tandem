package io.github.zahakj.tandem.ui

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import io.github.zahakj.tandem.DiscordPresence
import io.github.zahakj.tandem.Libraries
import io.github.zahakj.tandem.MainActivity
import io.github.zahakj.tandem.Shell
import io.github.zahakj.tandem.audio.SoundPack
import io.github.zahakj.tandem.data.HomeStore
import io.github.zahakj.tandem.data.ScrapeConfig
import io.github.zahakj.tandem.data.Store
import io.github.zahakj.tandem.data.SystemPrefs

/*
 * How Settings is organised.
 *
 * Every feature still writes its own rows in its own file (appearanceSections, soundSettings, systemsSettings...).
 * This file sorts them into nine calm pages: each page is a few short groups, common rows first, the rest folded
 * under "More options". Rows are picked by their group title and label. Nothing can get lost: a row that no page
 * picks (a feature added a new one) still shows, in the "More options" of the page its group belongs to
 * (see [Owners]). docs/SETTINGS.md maps every setting to its place.
 */

/** Galleries above a page's groups (themes on Look & feel, layouts on Home & layout). See [SettingsLayout]. */
class SettingsHeaderSpec(
    val keys: List<List<String>>,
    val content: LazyListScope.(target: String?, onFocusKey: (String) -> Unit) -> Unit,
    val row: (String) -> SettingRow?,
)

/** One Settings page: its tile on the first screen and its groups. */
class SettingsCategory(
    val id: String,
    val title: String,
    val icon: Glyph,
    val color: Color,
    /** What lives here, in a sentence (side panel and search). */
    val blurb: String,
    /** The current state in a few words, under the tile's title. */
    val summary: String,
    val sections: List<SettingSection>,
) {
    val route: Route get() = SettingsCategories.route(id)

    /** The first few rows of the page, shown with their values beside the tile. */
    val highlights: List<SettingRow> get() = sections.flatMap { it.rows }.take(4)
}

object SettingsCategories {
    /** Page ids in the order the tiles show (and L1/R1 step through them). */
    val ids = listOf("look", "home", "screens", "sound", "controls", "library", "art", "play", "data")

    /** Look & feel keeps the old Appearance route, so everything that opened Appearance lands on it. */
    fun route(id: String): Route = if (id == "look") Route.Appearance else Route.SettingsPage(id)

    fun idOf(route: Route): String? = when (route) {
        Route.Appearance -> "look"
        is Route.SettingsPage -> route.id
        else -> null
    }

    fun title(id: String): String = when (id) {
        "look" -> "Look & feel"
        "home" -> "Home & layout"
        "screens" -> "Two screens"
        "sound" -> "Sound & music"
        "controls" -> "Controls"
        "library" -> "Library"
        "art" -> "Art & accounts"
        "play" -> "Play & stats"
        else -> "Data & about"
    }

    /** The page L1 (-1) or R1 (+1) moves to. */
    fun step(id: String, by: Int): String = ids[(ids.indexOf(id).coerceAtLeast(0) + by + ids.size) % ids.size]
}

/** Which page a feature's group belongs to, for rows no page picked by name. */
private val Owners = mapOf(
    "Colour & highlight" to "look", "System icons" to "look", "Motion" to "look", "Wallpaper" to "look", "Extras" to "look",
    "Layout" to "home", "Icon grid" to "home", "Dock" to "home", "Top bar" to "home", "Widgets" to "home",
    "Bottom screen" to "screens", "Screens" to "screens",
    "Sound & music" to "sound",
    "Controls" to "controls", HomeButtonGroup to "controls", ControllersGroup to "controls", PadLinkGroup to "controls",
    "Library" to "library", "Systems & launching" to "library",
    "Artwork & details" to "art", "RetroAchievements" to "art", "Discord" to "art",
    "Home & play" to "play", "Motion & home" to "look",
    "Your data" to "data", "About" to "data", "From Cocoon" to "library", "Developer" to "data",
)

/** Words people search for that a row's label doesn't contain (rows can also list their own `keywords`). */
private val Synonyms = mapOf(
    "Accent colour" to "color tint hue",
    "Highlight style" to "focus selection glow outline cursor",
    "Highlight colour" to "focus color",
    "Focus zoom" to "grow scale bigger",
    "Surface" to "glass frosted material cards",
    "Top screen" to "wallpaper background",
    "Bottom screen" to "wallpaper background",
    "Icon style" to "system icons consoles drawn",
    "Animation style" to "bouncy smooth speed",
    "Reduce motion" to "animations still battery",
    "Home layout" to "icon grid shelves carousel list console",
    "Icon size" to "bigger smaller zoom columns across",
    "Grid size" to "bigger smaller cards columns",
    "Games view" to "list grid coverflow",
    "Card shape" to "box art portrait square circle",
    "Swap screens" to "bottom touch library details flip",
    "Style" to "bottom screen details art clock",
    "Video snaps" to "preview videos battery",
    "Moving art" to "ken burns pan zoom",
    "UI sounds" to "clicks beeps sfx effects mute",
    "Background music" to "soundtrack bgm mute quiet",
    "Soundtrack" to "song tracks",
    "Button prompts" to "xbox playstation nintendo glyphs",
    "Swap A and B" to "nintendo confirm cancel back",
    "Swap X and Y" to "search options",
    "X button" to "swap search screens",
    "Clock" to "time 24 hour",
    "Battery" to "percentage",
)

/** The rows of every feature, taken out one by one as pages pick them. */
private class RowPool(sections: List<SettingSection>) {
    private val rows = LinkedHashMap<String, Pair<String, SettingRow>>()

    init {
        for (s in sections) for (r in s.rows + s.more) {
            val extra = Synonyms[r.label]
            rows.putIfAbsent(rowKey(s.title, r.label), s.title to if (extra == null) r else r.copy(keywords = "${r.keywords} $extra"))
        }
    }

    /** Takes the rows of group [section] with these labels, in this order; missing ones are skipped. */
    fun take(section: String, vararg labels: String): List<SettingRow> = labels.mapNotNull { rows.remove(rowKey(section, it))?.second }

    /** Every row of group [section], in order (for groups whose rows change, like connected controllers). */
    fun takeAll(section: String): List<SettingRow> {
        val prefix = rowKey(section, "")
        val keys = rows.keys.filter { it.startsWith(prefix) }
        return keys.mapNotNull { rows.remove(it)?.second }
    }

    /** Duplicates of a row shown elsewhere: removed without showing. */
    fun drop(section: String, vararg labels: String) { labels.forEach { rows.remove(rowKey(section, it)) } }

    /** Rows nobody picked, by the page their group belongs to. */
    fun leftovers(): Map<String, List<SettingRow>> = rows.values.groupBy({ Owners[it.first] ?: "data" }, { it.second })
}

/** ExtrasSettings.resumeLanding had no row anywhere; it belongs with the icon grid's Resume page. */
private fun resumeLandingRow(): SettingRow {
    val on = io.github.zahakj.tandem.data.ExtrasStore.settings.resumeLanding
    return SettingRow(
        "Home lands on Resume", onOff(on), Palette.Mint, Glyph.Play,
        "Pressing Home while a game runs brings you to the Resume page left of the icon grid, with the game one press away. Off, Home goes to the grid itself.",
        toggle = on, keywords = "resume page home button running game",
    ) { io.github.zahakj.tandem.data.ExtrasStore.update { it.copy(resumeLanding = !it.resumeLanding) } }
}

/** A group: [main] rows always show, [more] fold away. Empty groups are left out by the layout. */
private fun group(title: String, main: List<SettingRow>, more: List<SettingRow> = emptyList(), icon: Glyph? = null, note: String? = null) =
    SettingSection(title, main, icon, more, note)

/**
 * Every Settings page, with live values. Composes every feature's rows (they are cheap: values and lambdas), so
 * the first screen can summarise each page and search can find any row.
 */
@Composable
fun settingsCatalog(): List<SettingsCategory> {
    val ctx = LocalContext.current
    val onRescan = remember(ctx) { { LibraryScan.rescan(ctx) } }
    val pool = RowPool(
        appearanceSections() + listOf(
            librarySettings(onRescan), systemsSettings(), scrapingSettings(), homeSettings(), flairSettings(), widgetsSettings(),
            achievementsSettings(), discordSettings(), dataSettings(), aboutSettings(), cocoonSetupSettings(), developerSettings(),
        ) + controllerSettings(),
    )
    val look = Store.settings.look
    val settings = Store.settings
    val iconGrid = look.homeLayout == "grid"

    // Shown twice in the old screens; each now lives in one place.
    pool.drop("Library", "Show game titles") // Home & layout › Games › Game titles
    pool.drop("Screens", "Single screen") // Two screens › Single screen (the one that knows when there is one screen)
    pool.drop("Home & play", "Apps dock on the home screen") // Home & layout › Dock › Apps dock
    pool.drop("Icon grid", "Icon pack") // Look & feel › Icons › Icons from
    pool.drop("Icon grid", "Names under icons") // Icon grid › Icon names (always, highlighted only, off)
    pool.drop("Motion & home", "Use Tandem as the Home screen") // Data & about › Home screen

    val lookPage = listOf(
        group("Colour", pool.take("Colour & highlight", "Accent colour", "Highlight style"),
            pool.take("Colour & highlight", "Highlight colour", "Highlight thickness", "Focus zoom", "Card corners", "Surface")),
        group("Wallpaper", pool.take("Wallpaper", "Top screen", "Bottom screen"), pool.take("Wallpaper", "Picture dimming", "Wallpaper folder")),
        group("Icons", pool.take("System icons", "Icons from", "Icon style")),
        group("Motion", pool.take("Motion", "Animation style") + pool.take("Motion & home", "Floating titles") + pool.take("Motion", "Reduce motion"),
            pool.take("Motion & home", "Motion intensity") + pool.take("Extras", "Startup splash")),
        group("Start over", pool.take("Extras", "Reset appearance"), pool.take("Extras", "Reload theme packs")),
    )

    val gridGroup = group(
        "Icon grid",
        pool.take("Icon grid", "Icon size", "Rows of icons", "Scrolling") + pool.take("Motion & home", "Icon names", "Recently played strip"),
        pool.take("Icon grid", "Icon roundness", "Space between icons", "Hero split", "Other screen", "Folders open") + resumeLandingRow(),
        note = if (iconGrid) "Your layout" else "For the Icon grid layout",
    )
    val homeGroup = group(
        "Home screen",
        if (iconGrid) pool.take("Layout", "Home layout", "Now playing") else pool.take("Layout", "Home layout", "Home rows", "Now playing"),
        if (iconGrid) pool.take("Layout", "Home rows", "System tiles", "Open folders") else pool.take("Layout", "System tiles", "Open folders"),
    )
    val homePage = buildList {
        add(homeGroup)
        // The layout in use comes first; the icon grid's own options sink to the bottom while another layout is on.
        if (iconGrid) add(gridGroup)
        add(group("Games", pool.take("Layout", "Games view", "Grid size", "Card shape", "Game titles")))
        add(group("Dock", pool.take("Dock", "Apps dock") + pool.take("Home & play", "Choose dock apps") + pool.take("Dock", "Dock style"),
            pool.take("Dock", "Dock on top", "Auto-hide dock", "Dock background", "Icons on dock", "Dock icon shape")))
        add(group("Widgets", pool.take("Widgets", "Customize widgets", "Widgets on the home screen"), pool.take("Widgets", "Add to Up next")))
        add(group("Top bar", pool.take("Top bar", "Clock", "Battery")))
        if (!iconGrid) add(gridGroup)
    }

    val screensPage = listOf(
        group("Screens", pool.take("Systems & launching", "Single screen") + pool.take("Screens", "Swap screens") + pool.take("Systems & launching", "Apps open on"),
            pool.take("Systems & launching", "Hold Back to move apps", "Move games too")),
        group("Bottom screen", pool.take("Bottom screen", "Style", "Video snaps", "Moving art") + pool.take("Widgets", "Bottom screen when idle"),
            pool.take("Bottom screen", "Art height", "Logo size", "Art fade")),
    )

    val soundPage = listOf(
        group("Sounds", pool.take("Sound & music", "UI sounds", "Sound pack", "Sound volume"), pool.take("Sound & music", "System jingles", "Play jingles", "Pip's voice")),
        group("Music", pool.take("Sound & music", "Background music", "Soundtrack", "Music volume", "Next song"),
            pool.take("Sound & music", "My music folder", "Shuffle my music")),
    )

    val controlsPage = listOf(
        group("Buttons", pool.take("Controls", "Button prompts", "Swap A and B", "Swap X and Y") + pool.take("Icon grid", "X button")),
        group(HomeButtonGroup, pool.take(HomeButtonGroup, "Controller Home button"), pool.take(HomeButtonGroup, "Hold the Home button")),
        group(ControllersGroup, pool.takeAll(ControllersGroup), note = "On: Nintendo labels"),
        group(PadLinkGroup, pool.takeAll(PadLinkGroup)),
        group("Shortcuts", pool.take("Home & play", "Game switcher", "Quick settings panel"), pool.take("Layout", "Corner hints")),
    )

    val libraryPage = listOf(
        group("Games", pool.take("Library", "Sort games by", "Show hidden games", "Rescan games")),
        group("Systems", pool.take("Systems & launching", "Systems and emulators", "Android apps"), pool.take("Systems & launching", "Cocoon platform files")),
        group("Bring your details", pool.take("From Cocoon", "Import my Cocoon setup") + pool.take("Your data", "Import details"), pool.take("From Cocoon", "Undo Cocoon import")),
    )

    val artPage = listOf(
        group("Artwork", pool.take("Artwork & details", "Scrape library", "Set up sources", "Artwork to download"),
            pool.take("Artwork & details", "Preferred region", "Preferred language", "Clear downloaded media")),
        group("Sources, in order", pool.take("Artwork & details", *ScrapeConfig.settings.sourceOrder.map { it.label }.toTypedArray())),
        group("RetroAchievements", pool.take("RetroAchievements", "Account", "Show achievements"), pool.take("RetroAchievements", "Look up games again", "Sign out")),
        group("Discord", pool.take("Discord", "Discord status"), pool.take("Discord", "Disconnect Discord")),
    )

    val playPage = listOf(
        group("Playing", pool.take("About", "Resume running games") + pool.take("Home & play", "Close previous game", "Ask before closing a game"),
            pool.take("Systems & launching", "Launch animation")),
        group("Stats", pool.take("Home & play", "Play stats"), pool.take("Home & play", "Export play stats")),
        group("Privacy & rest", pool.take("Home & play", "Private mode", "Go offline while a hidden game plays", "Screensaver"),
            pool.take("Home & play", "Private mode PIN")),
        // Changes with what is hidden and whether it shows, so it is made here rather than picked from the pool.
        hiddenSettings().let { group(it.title, it.rows, it.more) },
    )

    val dataPage = listOf(
        group("Home screen", pool.take("Home & play", "Use Tandem as the Home screen")),
        group("Your data", pool.take("Your data", "Back up", "Restore")),
        group("About", pool.take("About", "About Tandem") + pool.take("Extras", "Language")),
        group("Developer", pool.take("Developer", "Frame timing overlay")),
    )

    // Whatever no page picked goes under its page's last "More options", so every setting stays reachable.
    val left = pool.leftovers()
    fun withLeftovers(id: String, page: List<SettingSection>): List<SettingSection> {
        val extra = left[id].orEmpty()
        if (extra.isEmpty()) return page
        return page + group("Other", emptyList(), extra)
    }

    val ra = HomeStore.ra
    val ss = ScrapeConfig.settings
    val shizuku = remember(ShizukuWatch.version) { Shell.ready() }
    val isHome = Facts.run { ensure(ctx); Facts.isHome }
    val theme = Themes.target
    // derivedStateOf: details changing (a scrape, a favourite) only recomposes Settings when the count changes.
    val games by remember { androidx.compose.runtime.derivedStateOf { io.github.zahakj.tandem.Collections.visibleGames().size } }
    val systems = Libraries.all.orEmpty().count { it.games.isNotEmpty() }

    return listOf(
        SettingsCategory(
            "look", "Look & feel", Glyph.Theme, Palette.Accent,
            "Themes and theme packs, colours, wallpapers, system icons and animation.",
            theme.name + (if (look.accent != null) " · own accent" else "") + (if (settings.reduceMotion) " · still" else ""),
            withLeftovers("look", lookPage),
        ),
        SettingsCategory(
            "home", "Home & layout", Glyph.Home, Palette.Butter,
            "Your home layout, how games are shown, the apps dock, widgets and the top bar.",
            (HomeLayouts.firstOrNull { it.first == look.homeLayout }?.second?.substringBefore(" (") ?: "Shelves") +
                if (iconGrid) " · ${look.gridAcross} across" else " · ${GameViews.firstOrNull { it.first == look.gameView }?.second ?: "Grid"} of games",
            withLeftovers("home", homePage),
        ),
        SettingsCategory(
            "screens", "Two screens", Glyph.Screens, Palette.Sky,
            "What each screen shows: one screen or two, swapping them, and the bottom screen's art and videos.",
            when {
                SystemPrefs.data.singleScreen -> "Top screen only"
                look.swapScreens -> "Library on the bottom"
                else -> "Details below · " + (BottomModes.firstOrNull { it.first == settings.bottomMode }?.second ?: "Details")
            },
            withLeftovers("screens", screensPage),
        ),
        SettingsCategory(
            "sound", "Sound & music", Glyph.Music, Palette.Peach,
            "UI sounds, the soundtrack or your own music, jingles and Pip's voice.",
            (if (look.sounds) SoundPack.of(look.soundPack).label + " sounds" else "Sounds off") + " · " + (if (look.music) "music on" else "music off"),
            withLeftovers("sound", soundPage),
        ),
        SettingsCategory(
            "controls", "Controls", Glyph.Gamepad, Palette.Lavender,
            "Button prompts, swapping A/B and X/Y, what X does, the hold-button shortcuts, your controllers and their Home button.",
            Prompts.label(look.prompts) + " prompts" + (if (look.swapAB) " · A/B swapped" else ""),
            withLeftovers("controls", controlsPage),
        ),
        SettingsCategory(
            "library", "Library", Glyph.Cartridge, Palette.Mint,
            "Sorting, hidden games, rescanning, systems and emulators, Android apps, and importing details.",
            if (games == 0) "No games yet" else "${io.github.zahakj.tandem.plural(games, "game")} · ${io.github.zahakj.tandem.plural(systems, "system")}",
            withLeftovers("library", libraryPage),
        ),
        SettingsCategory(
            "art", "Art & accounts", Glyph.Download, Palette.Butter,
            "Scraping artwork and details, its sources (LaunchBox, ScreenScraper, IGDB, SteamGridDB, libretro, HowLongToBeat) and their keys, " +
                if (DiscordPresence.available) "RetroAchievements and Discord." else "and RetroAchievements.",
            listOfNotNull(
                "${io.github.zahakj.tandem.plural(ss.active.size, "source")} on",
                if (ra.user.isNotBlank() && ra.key.isNotBlank()) "RA ✓" else null,
                if (DiscordPresence.available && DiscordPresence.linked) "Discord ✓" else null,
            ).joinToString(" · "),
            withLeftovers("art", artPage),
        ),
        SettingsCategory(
            "play", "Play & stats", Glyph.Chart, Palette.Mint,
            "Resuming games, closing the last one, the launch animation, play stats, private mode, hidden folders and games, and the screensaver.",
            (if (shizuku) "Resume ready" else "Resume needs Shizuku") + " · ${io.github.zahakj.tandem.plural(HomeStore.sessions.size, "session")}",
            withLeftovers("play", playPage),
        ),
        SettingsCategory(
            "data", "Data & about", Glyph.Info, Palette.Lavender,
            "Tandem as your Home screen, backing up and restoring, and about Tandem.",
            if (isHome) "Home screen · on" else "Not the Home screen yet",
            withLeftovers("data", dataPage),
        ),
    )
}
