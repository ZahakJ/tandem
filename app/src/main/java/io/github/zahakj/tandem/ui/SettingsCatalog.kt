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
 * [SettingsPlan] sorts them into seven calm pages: each page is a few short groups, common rows first, the rest folded
 * under "More options". Rows are picked by their group title and label. Every row a feature builds is either placed
 * or dropped on purpose; SettingsPlanTest fails when one isn't, and a debug build shows a stray row under
 * "Not placed" on its page. docs/SETTINGS.md maps every setting to its place.
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
    val ids = listOf("look", "home", "screens", "sound", "controls", "library", "play")

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
        "library" -> "Library & art"
        else -> "Play & system"
    }

    /** The page L1 (-1) or R1 (+1) moves to. */
    fun step(id: String, by: Int): String = ids[(ids.indexOf(id).coerceAtLeast(0) + by + ids.size) % ids.size]
}

/**
 * Where every row goes: plain data, so a test can check it. A [Pick] is a feature's group title and a row label;
 * [Pick.layout] shows it only with the icon grid ("grid") or only with the other layouts ("other").
 */
object SettingsPlan {
    data class Pick(val section: String, val label: String, val layout: String? = null)
    data class Group(val title: String, val main: List<Pick>, val more: List<Pick> = emptyList(), val note: String? = null)

    private fun p(section: String, vararg labels: String) = labels.map { Pick(section, it) }
    private fun grid(section: String, vararg labels: String) = labels.map { Pick(section, it, "grid") }
    private fun other(section: String, vararg labels: String) = labels.map { Pick(section, it, "other") }

    /** The group of scraping sources: their rows are named after the sources, in the order the user chose. */
    const val SOURCES = "Sources, in order"

    /** Every row of a group whose rows change (connected controllers, PadLink). */
    val whole: Set<String> = setOf(ControllersGroup, PadLinkGroup)

    val pages: Map<String, List<Group>> = mapOf(
        "look" to listOf(
            Group("Colour", p("Colour & highlight", "Accent colour", "Highlight style"), p("Colour & highlight", "Surface")),
            Group("Wallpaper", p("Wallpaper", "Top screen", "Bottom screen"), p("Wallpaper", "Picture dimming")),
            Group("Icons", p("System icons", "Icons from", "Icon style")),
            Group("Motion", p("Motion", "Animation style", "Reduce motion"), p("Motion & home", "Floating titles", "Motion intensity")),
            Group("Start over", p("Extras", "Reset look")),
        ),
        "home" to listOf(
            Group(
                "Home screen",
                grid("Icon grid", "Icon size", "Scrolling") + grid("Motion & home", "Icon names", "Recently played strip") +
                    other("Layout", "Home rows") + grid("Icon grid", "Folders open") + other("Layout", "Open folders"),
                grid("Icon grid", "Rows of icons", "Icon roundness", "Space between icons", "Hero split") + other("Layout", "System tiles"),
            ),
            Group("Games", p("Layout", "Games view", "Grid size", "Card shape", "Game titles")),
            Group("While a game runs", p("Layout", "Now playing") + p("Extras", "Home lands on Resume")),
            Group("Dock", p("Dock", "Apps dock") + p("Home & play", "Choose dock apps") + p("Dock", "Dock style"), p("Dock", "Dock on top", "Auto-hide dock")),
            Group("Widgets", p("Widgets", "Customize widgets", "Widgets on the home screen"), p("Widgets", "Widgets on the grid")),
            Group("Top bar", p("Top bar", "Clock", "Battery")),
            Group("Rest", p("Home & play", "Screensaver")),
        ),
        "screens" to listOf(
            Group(
                "Screens", p("Systems & launching", "Single screen") + p("Screens", "Swap screens") + p("Systems & launching", "Apps open on"),
                p("Icon grid", "Swap animation") + p("Screens", "Keep auto-rotate off"),
            ),
            Group(
                "Other screen",
                grid("Icon grid", "Other screen") + other("Bottom screen", "Style") + p("Bottom screen", "Video snaps", "Moving art") +
                    p("Widgets", "Bottom screen when idle"),
                p("Bottom screen", "Art height", "Logo size", "Art fade"),
            ),
            Group("Moving apps", p("Systems & launching", "Hold Back to move apps", "Move games too")),
        ),
        "sound" to listOf(
            Group("Sounds", p("Sound & music", "UI sounds", "Sound pack", "Custom sounds", "Sound volume", "Pip's voice")),
            Group("Music", p("Sound & music", "Music", "Music from", "Music volume", "Next song"), p("Sound & music", "Shuffle my music")),
        ),
        "controls" to listOf(
            Group(
                "Buttons", p("Controls", "Button prompts", "Swap A and B") + p("Icon grid", "X button"),
                p("Controls", "Swap X and Y") + p("Layout", "Corner hints"),
                note = "Hold Select: game switcher · Hold Start: quick settings",
            ),
            Group(HomeButtonGroup, p(HomeButtonGroup, "Controller Home button"), p(HomeButtonGroup, "Hold the Home button")),
            Group(ControllersGroup, emptyList(), note = "On: Nintendo labels"),
            Group(PadLinkGroup, emptyList()),
        ),
        "library" to listOf(
            Group("Games", p("Library", "Sort games by", "Show hidden games", "Rescan games")),
            Group("Systems", p("Systems & launching", "Systems and emulators", "Android apps")),
            Group(
                "Artwork", p("Artwork & details", "Scrape library", "Set up sources", "Artwork to download"),
                p("Artwork & details", "Preferred region", "Preferred language", "Clear downloaded media"),
            ),
            Group(SOURCES, emptyList()),
            Group("RetroAchievements", p("RetroAchievements", "Account", "Show achievements"), p("RetroAchievements", "Look up games again", "Sign out")),
            Group("Discord", p("Discord", "Discord status"), p("Discord", "Disconnect Discord")),
            Group("Bring your details", p("From Cocoon", "Import my Cocoon setup") + p("Your data", "Import details"), p("From Cocoon", "Undo Cocoon import")),
        ),
        "play" to listOf(
            Group(
                "Playing", p("About", "Resume running games") + p("Home & play", "Close previous game", "Ask before closing a game", "Immersion mode"),
                p("Home & play", "Pause games in sleep") + p("Systems & launching", "Launch animation"),
            ),
            Group(
                "Privacy", p("Home & play", "Private mode", "Go offline while a hidden game plays", "Stay offline until the game closes"),
                p("Home & play", "Private mode PIN"),
            ),
            Group("Stats", p("Home & play", "Play stats"), p("Home & play", "Export play stats")),
            Group("Your data", p("Home & play", "Use Tandem as the Home screen") + p("Your data", "Back up", "Restore")),
            Group("About", p("About", "About Tandem"), p("Developer", "Frame timing overlay", "Tour every theme")),
        ),
    )

    /** Rows shown twice in the old screens, or standing for a gallery: built, but never placed. */
    val dropped: List<Pick> =
        p("Library", "Show game titles") + // Home & layout › Games › Game titles
            p("Screens", "Single screen") + // Two screens › Single screen (the one that knows when there is one screen)
            p("Home & play", "Apps dock on the home screen") + // Home & layout › Dock › Apps dock
            p("Icon grid", "Icon pack") + // Look & feel › Icons › Icons from
            p("Icon grid", "Names under icons") + // Home & layout › Icon names (always, highlighted only, off)
            p("Motion & home", "Use Tandem as the Home screen") + // Play & system › Your data
            p("Layout", "Home layout") // the layout gallery at the top of Home & layout
}

/** Words people search for that a row's label doesn't contain (rows can also list their own `keywords`). */
private val Synonyms = mapOf(
    "Accent colour" to "color tint hue highlight colour",
    "Highlight style" to "focus selection glow outline cursor thickness zoom",
    "Surface" to "glass frosted material cards corners",
    "Top screen" to "wallpaper background folder",
    "Bottom screen" to "wallpaper background",
    "Icon style" to "system icons consoles drawn",
    "Animation style" to "bouncy smooth speed",
    "Reduce motion" to "animations still battery",
    "Icon size" to "bigger smaller zoom columns across",
    "Grid size" to "bigger smaller cards columns",
    "Games view" to "list grid coverflow",
    "Card shape" to "box art portrait square circle",
    "Swap screens" to "bottom touch library details flip",
    "Swap animation" to "flip scatter push depth fade instant transition",
    "Keep auto-rotate off" to "rotation orientation portrait landscape sideways tilt",
    "Style" to "bottom screen details art clock",
    "Video snaps" to "preview videos battery",
    "Moving art" to "ken burns pan zoom",
    "UI sounds" to "clicks beeps sfx effects mute",
    "Sound pack" to "recorded cocoon sounds sfx",
    "Music" to "soundtrack bgm mute quiet background",
    "Music from" to "soundtrack folder songs built-in meadow",
    "Button prompts" to "xbox playstation nintendo glyphs",
    "Swap A and B" to "nintendo confirm cancel back",
    "Swap X and Y" to "search options",
    "X button" to "swap search screens",
    "Clock" to "time 24 hour",
    "Battery" to "percentage",
    "Corner hints" to "game switcher quick settings hold select start",
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

    fun take(pick: SettingsPlan.Pick): SettingRow? = rows.remove(rowKey(pick.section, pick.label))?.second

    /** Every row of group [section], in order (for groups whose rows change, like connected controllers). */
    fun takeAll(section: String): List<SettingRow> {
        val prefix = rowKey(section, "")
        val keys = rows.keys.filter { it.startsWith(prefix) }
        return keys.mapNotNull { rows.remove(it)?.second }
    }

    /** Rows nobody picked: a feature added one and the plan doesn't know it yet. */
    fun leftovers(): List<SettingRow> = rows.values.map { it.second }
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
            SettingSection("Extras", listOf(resumeLandingRow())),
        ) + controllerSettings(),
    )
    val look = Store.settings.look
    val settings = Store.settings
    val iconGrid = look.homeLayout == "grid"
    SettingsPlan.dropped.forEach { pool.take(it) }

    fun page(id: String): List<SettingSection> = SettingsPlan.pages.getValue(id).map { g ->
        fun pick(list: List<SettingsPlan.Pick>) = list.mapNotNull { pk ->
            val shown = when (pk.layout) { "grid" -> iconGrid; "other" -> !iconGrid; else -> true }
            // A row for the other layout is taken out unshown, so it never counts as forgotten.
            pool.take(pk)?.takeIf { shown }
        }
        val main = when (g.title) {
            in SettingsPlan.whole -> pick(g.main) + pool.takeAll(g.title)
            // The scraping sources, in the order the user put them.
            SettingsPlan.SOURCES -> pick(ScrapeConfig.settings.sourceOrder.map { SettingsPlan.Pick("Artwork & details", it.label) })
            else -> pick(g.main)
        }
        SettingSection(g.title, main, more = pick(g.more), note = g.note)
    }
    // The theme pack's own music and sounds lead the Sound page, when the theme has any.
    val soundPage = listOf(SettingSection("Theme pack", pool.takeAll("Theme pack"), note = ThemePacks.byId(look.theme)?.name)) + page("sound")
    val pages = SettingsCategories.ids.associateWith { if (it == "sound") soundPage else page(it) }.toMutableMap()
    // Changes with what is hidden and whether it shows, so it is made here rather than picked from the pool.
    pages["play"] = pages.getValue("play").let { p -> p.take(2) + hiddenSettings().let { SettingSection(it.title, it.rows, more = it.more) } + p.drop(2) }
    // A row the plan doesn't know yet still shows, so nothing is ever out of reach; a debug build says so.
    val left = pool.leftovers()
    if (left.isNotEmpty()) {
        android.util.Log.w("Tandem", "Settings rows not placed: " + left.joinToString { it.label })
        pages["play"] = pages.getValue("play") + SettingSection(
            if (io.github.zahakj.tandem.BuildConfig.DEBUG) "Not placed" else "Other", emptyList(), more = left,
        )
    }

    val ra = HomeStore.ra
    val ss = ScrapeConfig.settings
    val shizuku = remember(ShizukuWatch.version) { Shell.ready() }
    val isHome = Facts.run { ensure(ctx); Facts.isHome }
    val theme = Themes.target
    // derivedStateOf: details changing (a scrape, a favourite) only recomposes Settings when the count changes.
    val games by remember { androidx.compose.runtime.derivedStateOf { io.github.zahakj.tandem.Collections.visibleGames().size } }
    val systems = Libraries.all.orEmpty().count { it.games.isNotEmpty() }
    val pack = ThemePacks.byId(look.theme)

    return listOf(
        SettingsCategory(
            "look", "Look & feel", Glyph.Theme, Palette.Accent,
            "Themes and theme packs, colours, wallpapers, system icons and animation.",
            theme.name + (if (look.accent != null) " · own accent" else "") + (if (settings.reduceMotion) " · still" else ""),
            pages.getValue("look"),
        ),
        SettingsCategory(
            "home", "Home & layout", Glyph.Home, Palette.Butter,
            "Your home layout, how games are shown, the apps dock, widgets, the top bar and the screensaver.",
            (HomeLayouts.firstOrNull { it.first == look.homeLayout }?.second?.substringBefore(" (") ?: "Shelves") +
                if (iconGrid) " · ${look.gridAcross} across" else " · ${GameViews.firstOrNull { it.first == look.gameView }?.second ?: "Grid"} of games",
            pages.getValue("home"),
        ),
        SettingsCategory(
            "screens", "Two screens", Glyph.Screens, Palette.Sky,
            "What each screen shows: one screen or two, swapping them, the other screen's art and videos, and moving apps between them.",
            when {
                SystemPrefs.data.singleScreen -> "Top screen only"
                look.swapScreens -> "Library on the bottom"
                else -> "Details below · " + (BottomModes.firstOrNull { it.first == settings.bottomMode }?.second ?: "Details")
            },
            pages.getValue("screens"),
        ),
        SettingsCategory(
            "sound", "Sound & music", Glyph.Music, Palette.Peach,
            "The theme pack's own music and sounds, UI sounds, Pip's voice, and your music or the built-in soundtrack.",
            when {
                pack != null && (pack.music.isNotEmpty() || pack.sounds != null) -> "Using ${pack.name}'s audio"
                else -> (if (look.sounds) SoundPack.of(look.soundPack).label + " sounds" else "Sounds off") + " · " + (if (look.music) "music on" else "music off")
            },
            soundPage,
        ),
        SettingsCategory(
            "controls", "Controls", Glyph.Gamepad, Palette.Lavender,
            "Button prompts, swapping A/B and X/Y, what X does, your controllers and their Home button.",
            Prompts.label(look.prompts) + " prompts" + (if (look.swapAB) " · A/B swapped" else ""),
            pages.getValue("controls"),
        ),
        SettingsCategory(
            "library", "Library & art", Glyph.Cartridge, Palette.Mint,
            "Sorting, hidden games, rescanning, systems and emulators, artwork and its sources, " +
                (if (DiscordPresence.available) "RetroAchievements, Discord" else "RetroAchievements") + " and importing details.",
            listOfNotNull(
                if (games == 0) "No games yet" else "${io.github.zahakj.tandem.plural(games, "game")} · ${io.github.zahakj.tandem.plural(systems, "system")}",
                "${io.github.zahakj.tandem.plural(ss.active.size, "source")} on",
                if (ra.user.isNotBlank() && ra.key.isNotBlank()) "RA ✓" else null,
            ).joinToString(" · "),
            pages.getValue("library"),
        ),
        SettingsCategory(
            "play", "Play & system", Glyph.Chart, Palette.Mint,
            "Resuming and closing games, private mode, hidden folders and games, play stats, Tandem as your Home screen, backups, and about Tandem.",
            (if (shizuku) "Resume ready" else "Resume needs Shizuku") + (if (isHome) " · Home screen" else " · not the Home screen"),
            pages.getValue("play"),
        ),
    )
}
