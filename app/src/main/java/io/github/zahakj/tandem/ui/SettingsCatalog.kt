package io.github.zahakj.tandem.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.ui.Modifier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import io.github.zahakj.tandem.Bindings
import io.github.zahakj.tandem.Command
import io.github.zahakj.tandem.DiscordPresence
import io.github.zahakj.tandem.Libraries
import io.github.zahakj.tandem.MainActivity
import io.github.zahakj.tandem.Shell
import io.github.zahakj.tandem.audio.SoundPack
import io.github.zahakj.tandem.data.ControllerStore
import io.github.zahakj.tandem.data.HomeStore
import io.github.zahakj.tandem.data.ScrapeConfig
import io.github.zahakj.tandem.data.Store
import io.github.zahakj.tandem.data.SystemPrefs

/*
 * How Settings is organised.
 *
 * Every feature still writes its own rows in its own file (appearanceSections, soundSettings, systemsSettings...).
 * [SettingsPlan] sorts them into seven pages, each a few short groups with the rows people change most first, and
 * nothing folded away. A coherent set of rarer rows (the dock and widgets, accounts, performance) is a short page of
 * its own, a [SettingsPlan.Sub], opened from one row on its page. Each setting has one place; rows are picked by
 * their group title and label. Every row a feature builds is either placed or dropped on purpose; SettingsPlanTest
 * fails when one isn't, and a debug build shows a stray row under "Not placed" on its page. docs/SETTINGS.md maps
 * every setting to its place.
 */

/** Galleries above a page's groups (themes on Look & feel, layouts on Home & layout). See [SettingsLayout]. */
class SettingsHeaderSpec(
    val keys: List<List<String>>,
    val content: LazyListScope.(target: String?, onFocusKey: (String) -> Unit) -> Unit,
    val row: (String) -> SettingRow?,
)

/** One Settings page: its tile on the first screen (or, for a [parent]'s page, its row there) and its groups. */
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
    /** The page this one opens from; null for the seven pages on the first screen. */
    val parent: String? = null,
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

    /** The page a subpage opens from; null for the seven pages. */
    fun parentOf(id: String): String? = SettingsPlan.sub(id)?.parent

    fun title(id: String): String = SettingsPlan.sub(id)?.title ?: when (id) {
        "look" -> "Look & feel"
        "home" -> "Home & layout"
        "screens" -> "Two screens"
        "sound" -> "Sound & music"
        "controls" -> "Controls"
        "library" -> "Library & art"
        else -> "Play & system"
    }

    /** The page L1 (-1) or R1 (+1) moves to; from a subpage, its page's neighbour. */
    fun step(id: String, by: Int): String {
        val top = parentOf(id) ?: id
        return ids[(ids.indexOf(top).coerceAtLeast(0) + by + ids.size) % ids.size]
    }
}

/**
 * Where every row goes: plain data, so a test can check it. A [Pick] is a feature's group title and a row label;
 * [Pick.layout] shows it only with the icon grid ("grid"), only with Orbit ("orbit"), only with Spotlight
 * ("spotlight"), with every layout but the icon grid ("nogrid"), with every layout but the icon grid and Spotlight
 * ("other"), or only with the row layouts, none of those three ("rows").
 */
object SettingsPlan {
    data class Pick(val section: String, val label: String, val layout: String? = null)
    data class Group(val title: String, val main: List<Pick>, val note: String? = null)

    /** A short page of its own, opened from a row on page [parent]; [title] names both the row and the page. */
    data class Sub(val id: String, val parent: String, val title: String, val icon: Glyph, val help: String)

    private fun p(section: String, vararg labels: String) = labels.map { Pick(section, it) }
    private fun grid(section: String, vararg labels: String) = labels.map { Pick(section, it, "grid") }
    private fun nogrid(section: String, vararg labels: String) = labels.map { Pick(section, it, "nogrid") }
    private fun orbit(section: String, vararg labels: String) = labels.map { Pick(section, it, "orbit") }
    private fun spotlight(section: String, vararg labels: String) = labels.map { Pick(section, it, "spotlight") }
    private fun other(section: String, vararg labels: String) = labels.map { Pick(section, it, "other") }
    private fun rows(section: String, vararg labels: String) = labels.map { Pick(section, it, "rows") }

    /** The group of a [Pick] that is the row opening a [Sub]; the pick's label is the subpage's id. */
    const val LINK = "›"
    private fun link(id: String, layout: String? = null) = listOf(Pick(LINK, id, layout))

    /** The label of a [Pick] that takes every row of its group, for groups whose rows change (connected controllers). */
    const val ALL = "*"
    private fun all(section: String) = listOf(Pick(section, ALL))

    val subs: List<Sub> = listOf(
        Sub("look/wallpaper", "look", "Wallpaper", Glyph.Image, "The wallpaper on each screen, and how much pictures and videos are dimmed."),
        Sub("look/motion", "look", "Motion", Glyph.Motion, "How things move: the animation style, Reduce motion, floating titles, how lively it is, and how the screens swap."),
        Sub("home/grid", "home", "Grid shape", Glyph.Corners, "The icon grid's rows, how round its icons are, the space between them, and art above the grid."),
        Sub("home/games", "home", "How games show", Glyph.Grid, "How games show inside a system or collection: the view, its size, the card shape, the bookcase and titles."),
        Sub("home/dock", "home", "Dock & widgets", Glyph.Layers, "The apps dock along the home screen, and the widgets on it."),
        Sub("screens/art", "screens", "Art & video", Glyph.Image, "The art on the other screen: video snaps, moving art, the logo's size and how the art fades."),
        Sub("controls/pads", "controls", "Connected controllers", Glyph.Gamepad, "Bluetooth and USB controllers: Nintendo button labels for each one, and what their Home button does."),
        Sub("controls/deck", "controls", "Deck", Glyph.Text, "The Deck: the keyboard and trackpad on the bottom screen for PC games and streams."),
        Sub("library/accounts", "library", "Accounts", Glyph.Trophy, "RetroAchievements and Steam achievements, and your Discord status."),
        Sub("play/perf", "play", "Performance & sleep", Glyph.Battery, "Performance mode, fan and refresh rate while games play, and pausing games while the handheld sleeps."),
        Sub("play/privacy", "play", "Privacy & hidden", Glyph.Shield, "Private mode and its PIN, showing hidden games, and going offline while a hidden game plays."),
        Sub("play/data", "play", "Your data", Glyph.Save, "Back up and restore, bring in your Cocoon setup and gamelist.xml details, and export your play stats."),
        Sub("play/dev", "play", "For tinkerers", Glyph.Chart, "Tools for measuring smoothness and checking themes. You won't need them."),
    )

    fun sub(id: String): Sub? = subs.firstOrNull { it.id == id }

    /** The seven pages (in tile order), then the subpages. */
    val pages: Map<String, List<Group>> = mapOf(
        "look" to listOf(
            Group("Colour", p("Colour & highlight", "Accent colour", "Highlight style", "Surface")),
            Group("Wallpaper, icons & motion", link("look/wallpaper") + p("System icons", "Icon style", "Icons from") + link("look/motion")),
            Group("Start over", p("Extras", "Reset look")),
        ),
        "home" to listOf(
            Group(
                "Home screen",
                grid("Icon grid", "Icon size", "Scrolling") + grid("Motion & home", "Icon names") + grid("Icon grid", "Folders open") +
                    grid("Motion & home", "Recently played strip") + grid("Extras", "Resume page") + link("home/grid", "grid") +
                    orbit("Orbit", "Orbit holds", "Continue ribbon") +
                    spotlight("Spotlight", "Spotlight rail", "Spotlight backdrop", "Spotlight motion") +
                    rows("Layout", "Home rows", "System tiles") + other("Layout", "Open folders") + nogrid("Layout", "Now playing"),
            ),
            Group("Around the home", link("home/games") + link("home/dock") + p("Top bar", "Clock", "Battery") + p("Home & play", "Screensaver")),
        ),
        "screens" to listOf(
            Group(
                "Screens",
                p("Systems & launching", "Single screen") + p("Screens", "Swap screens") + p("Systems & launching", "Apps open on", "Hold Back to move") +
                    p("Screens", "Keep auto-rotate off"),
            ),
            Group("Other screen", grid("Icon grid", "Other screen") + other("Bottom screen", "Style") + p("Widgets", "Bottom screen when idle") + link("screens/art")),
            Group("While playing", p("Screen off", "Bottom screen while playing", "Off after") + p("Systems & launching", "Voice in clips")),
        ),
        "sound" to listOf(
            Group("Sounds", p("Sound & music", "UI sounds", "Custom sounds", "Sound volume", "Pip's voice")),
            Group("Music", p("Sound & music", "Music", "Music volume", "Shuffle my music", "Next song")),
        ),
        "controls" to listOf(
            Group(
                "Buttons", p("Controls", "Button prompts", "Swap A and B", "Swap X and Y") + p("Icon grid", "X button") + p("Controls", "Rebind buttons") +
                    p("Layout", "Corner hints"),
                // Replaced with the live bindings when the page is built (buttonsNote).
                note = "Hold Select: game switcher · Hold Start: quick settings",
            ),
            Group("Controllers", all(PadLinkGroup) + link("controls/pads") + link("controls/deck")),
        ),
        "library" to listOf(
            Group(
                "Games & systems",
                p("Game folders", "Game folders") + p("Library", "Rescan games") + p("Game folders", "Watch game folders") + p("Library", "Sort games by") +
                    p("Systems & launching", "Systems and emulators", "Android apps"),
            ),
            Group("Artwork & accounts", p("Artwork & details", "Scrape library", "Set up sources", "Clear downloaded media") + link("library/accounts")),
        ),
        "play" to listOf(
            Group(
                "Playing",
                p("About", "Resume running games") + p("Home & play", "Close previous game", "Ask before closing a game", "Immersion mode") +
                    p("Systems & launching", "Launch animation") + link("play/perf"),
            ),
            Group(
                "Tandem",
                link("play/privacy") + link("play/data") + p("Home & play", "Use Tandem as the Home screen") +
                    // Updates in builds with the updater; About Tandem in those without.
                    p("About", "Updates", "About Tandem", "Check automatically") + link("play/dev"),
            ),
        ),
        "look/wallpaper" to listOf(Group("Wallpaper", p("Wallpaper", "Top screen", "Bottom screen", "Picture dimming"))),
        "look/motion" to listOf(
            Group("Motion", p("Motion", "Animation style", "Reduce motion") + p("Motion & home", "Floating titles", "Motion intensity") + p("Icon grid", "Swap animation")),
        ),
        "home/grid" to listOf(Group("Grid shape", grid("Icon grid", "Rows of icons", "Icon roundness", "Space between icons", "Hero split"))),
        "home/games" to listOf(Group("How games show", p("Layout", "Games view", "Grid size", "Card shape", "Shelf look", "Game titles"))),
        "home/dock" to listOf(
            Group("Dock", p("Dock", "Apps dock") + p("Home & play", "Choose dock apps") + p("Dock", "Dock style", "Dock on top", "Auto-hide dock")),
            Group("Widgets", p("Widgets", "Customize widgets", "Widgets on the home screen") + grid("Widgets", "Widgets on the grid")),
        ),
        "screens/art" to listOf(Group("Art & video", p("Bottom screen", "Video snaps", "Moving art", "Logo size", "Art fade"))),
        "controls/pads" to listOf(
            Group(ControllersGroup, all(ControllersGroup), note = "On: Nintendo labels"),
            Group(HomeButtonGroup, p(HomeButtonGroup, "Controller Home button", "Hold the Home button")),
        ),
        "controls/deck" to listOf(Group("Deck", p("Deck", "Trackpad sensitivity", "Default layout", "Key sounds", "Key haptics"))),
        "library/accounts" to listOf(
            Group("RetroAchievements", p("RetroAchievements", "Account", "Show achievements", "Look up games again", "Sign out")),
            Group("Steam", p("Steam", "Steam Web API key", "Steam account", "Look up achievements", "Sign out")),
            Group("Discord", p("Discord", "Discord status", "Disconnect Discord")),
        ),
        "play/perf" to listOf(
            Group(
                "Performance & sleep",
                // On an AYN device the three rows; elsewhere, or until Tandem may write system settings, the one note.
                p("Performance while playing", "Performance mode", "Fan mode", "Refresh rate", "Performance profiles") + p("Home & play", "Pause games in sleep"),
            ),
        ),
        "play/privacy" to listOf(
            Group("Privacy & hidden", p("Home & play", "Private mode") + p("Library", "Show hidden games") + p("Home & play", "Offline for hidden games", "Private mode PIN")),
        ),
        "play/data" to listOf(
            Group(
                "Your data",
                p("Your data", "Back up", "Restore") + p("From Cocoon", "Import my Cocoon setup", "Undo Cocoon import") + p("Your data", "Import details") +
                    p("Home & play", "Export play stats"),
            ),
        ),
        "play/dev" to listOf(Group("For tinkerers", p("Developer", "Frame timing overlay", "Tour every theme"))),
    )

    /** The focus key of the row on its page that opens subpage [id] (its group and title, see [rowKey]). */
    fun linkKey(id: String): String? {
        val sub = sub(id) ?: return null
        val group = pages[sub.parent]?.firstOrNull { g -> g.main.any { it.section == LINK && it.label == id } } ?: return null
        return rowKey(group.title, sub.title)
    }

    /** Rows shown twice in the old screens, or standing for a gallery: built, but never placed. */
    val dropped: List<Pick> =
        p("Home & play", "Apps dock on the home screen") + // Home & layout › Dock & widgets › Apps dock
            p("Motion & home", "Use Tandem as the Home screen") // Play & system › Use Tandem as the Home screen
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
    "Shelf look" to "bookcase wood matte open spines shelves wallpaper",
    "Swap screens" to "bottom touch library details flip",
    "Swap animation" to "flip scatter push depth fade instant transition",
    "Keep auto-rotate off" to "rotation orientation portrait landscape sideways tilt",
    "Style" to "bottom screen details art clock",
    "Video snaps" to "preview videos battery",
    "Moving art" to "ken burns pan zoom",
    "UI sounds" to "clicks beeps sfx effects mute sound pack recorded cocoon",
    "Music" to "soundtrack bgm mute quiet background music from folder songs built-in calm meadow tales",
    "Button prompts" to "xbox playstation nintendo glyphs",
    "Swap A and B" to "nintendo confirm cancel back",
    "Swap X and Y" to "search options",
    "Rebind buttons" to "remap controller mapping chord hold bind shortcut",
    "X button" to "swap search screens",
    "Clock" to "time 24 hour",
    "Battery" to "percentage",
    "Corner hints" to "game switcher quick settings hold select start",
)

/** The rows of every feature, taken out one by one as pages pick them. */
private class RowPool(sections: List<SettingSection>) {
    private val rows = LinkedHashMap<String, Pair<String, SettingRow>>()

    init {
        for (s in sections) for (r in s.rows) {
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

/** The Buttons group's aside: what opens the game switcher and the quick settings, as they are bound right now. */
private fun buttonsNote(style: String): String {
    val map = ControllerStore.live
    fun of(c: Command) = map[c]?.let { Prompts.bindingText(it, style) } ?: "No button"
    return "${of(Command.Switcher)}: game switcher · ${of(Command.QuickPanel)}: quick settings"
}

/**
 * The icon grid's Resume page, as one row: whether a game still running gets the page left of the grid
 * (`Look.nowPlaying`, Now playing on the other layouts), and whether coming back to Tandem lands on it
 * (`ExtrasSettings.resumeLanding`, which only means something while the page is there).
 */
@Composable
private fun resumePageRow(): SettingRow {
    val shown = Store.settings.look.nowPlaying
    val lands = io.github.zahakj.tandem.data.ExtrasStore.settings.resumeLanding
    val options = listOf("off" to "Off", "on" to "On", "land" to "Home lands")
    val now = if (!shown) "off" else if (lands) "land" else "on"
    return choice(
        "Resume page", Glyph.Play, Palette.Mint,
        "A game still running gets a Resume page left of the grid, one press from carrying on. Home lands: coming " +
            "back to Tandem while it runs brings you straight to that page. Off, a running game has no page of its own.",
        options, now,
        look = { v -> LayoutPreview("grid", Modifier.fillMaxSize(), rounded = false, resumePage = v != "off") },
    ) { v ->
        updateLook { it.copy(nowPlaying = v != "off") }
        if (v != "off") io.github.zahakj.tandem.data.ExtrasStore.update { it.copy(resumeLanding = v == "land") }
    }.copy(keywords = "now playing running game resume home button landing")
}

/** What a subpage's row on its page says it holds, in a few words. */
private fun subSummary(id: String, rows: List<SettingRow>): String {
    fun value(label: String) = rows.firstOrNull { it.label == label }?.shownValue
    return when (id) {
        "home/grid" -> value("Rows of icons")?.let { if (it == "Auto") "Auto rows" else it }
        "home/dock" -> if (HomeStore.settings.showDock) value("Dock style") else "Dock off"
        "screens/art" -> value("Video snaps")?.let { "Videos: $it" }
        "controls/pads" -> rows.count { it.label !in PadsNotPads }.let { if (it == 0) "None connected" else "$it connected" }
        "controls/deck" -> value("Default layout")
        "library/accounts" -> listOfNotNull(
            "RA ✓".takeIf { HomeStore.ra.user.isNotBlank() && HomeStore.ra.key.isNotBlank() },
            "Steam ✓".takeIf { io.github.zahakj.tandem.data.SteamStore.account.signedIn },
            "Discord ✓".takeIf { DiscordPresence.available && DiscordPresence.linked },
        ).joinToString(" · ").ifEmpty { "Not signed in" }
        "play/privacy" -> if (HomeStore.settings.privateMode) "Private mode on" else "Private mode off"
        "play/data" -> "Backups & imports"
        "play/dev" -> io.github.zahakj.tandem.plural(rows.size, "tool")
        else -> null
    } ?: rows.firstOrNull()?.shownValue.orEmpty()
}

/**
 * Every Settings page, with live values: the seven, then their subpages. Composes every feature's rows (they are
 * cheap: values and lambdas), so the first screen can summarise each page and search can find any row.
 */
@Composable
fun settingsCatalog(): List<SettingsCategory> {
    val ctx = LocalContext.current
    val onRescan = remember(ctx) { { LibraryScan.rescan(ctx) } }
    val pool = RowPool(
        appearanceSections() + listOf(
            librarySettings(onRescan), gameFoldersSettings(), systemsSettings(), scrapingSettings(), homeSettings(), flairSettings(), orbitSettings(), spotlightHomeSettings(), widgetsSettings(),
            achievementsSettings(), steamSettings(), discordSettings(), dataSettings(), aboutSettings(), cocoonSetupSettings(), developerSettings(),
            performanceSettings(), screenOffSettings(), deckSettings(), SettingSection("Extras", listOf(resumePageRow())),
        ) + controllerSettings(),
    )
    val look = Store.settings.look
    val settings = Store.settings
    val bindings = ControllerStore.live
    val iconGrid = look.homeLayout == "grid"
    val orbit = look.homeLayout == "orbit"
    val spotlight = look.homeLayout == "spotlight"
    SettingsPlan.dropped.forEach { pool.take(it) }

    val built = HashMap<String, List<SettingSection>>()
    // A subpage is built where its page links to it (its row shows what it holds), so its rows are taken first.
    fun page(id: String): List<SettingSection> = built[id] ?: SettingsPlan.pages.getValue(id).map { g ->
        val main = g.main.flatMap { pk ->
            val shown = when (pk.layout) {
                "grid" -> iconGrid; "nogrid" -> !iconGrid; "orbit" -> orbit; "spotlight" -> spotlight; "other" -> !iconGrid && !spotlight
                "rows" -> !iconGrid && !orbit && !spotlight; else -> true
            }
            // A row for the other layout is taken out unshown, so it never counts as forgotten.
            when {
                pk.section == SettingsPlan.LINK -> listOfNotNull(subRow(pk.label, page(pk.label))?.takeIf { shown })
                pk.label == SettingsPlan.ALL -> pool.takeAll(pk.section).takeIf { shown }.orEmpty()
                else -> listOfNotNull(pool.take(pk)?.takeIf { shown })
            }
        }
        SettingSection(g.title, main, note = if (g.title == "Buttons") buttonsNote(look.prompts) else g.note)
    }.also { built[id] = it }
    // The theme pack's own music and sounds lead the Sound page, when the theme has any.
    val soundPage = listOf(SettingSection("Theme pack", pool.takeAll("Theme pack"), note = ThemePacks.byId(look.theme)?.name)) + page("sound")
    val pages = SettingsPlan.pages.keys.associateWith { if (it == "sound") soundPage else page(it) }.toMutableMap()
    // A row the plan doesn't know yet still shows, so nothing is ever out of reach; a debug build says so.
    val left = pool.leftovers()
    if (left.isNotEmpty()) {
        android.util.Log.w("Tandem", "Settings rows not placed: " + left.joinToString { it.label })
        pages["play"] = pages.getValue("play") + SettingSection(if (io.github.zahakj.tandem.BuildConfig.DEBUG) "Not placed" else "Other", left)
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

    val top = listOf(
        SettingsCategory(
            "look", "Look & feel", Glyph.Theme, Palette.Accent,
            "Themes and theme packs, colours, wallpapers, system icons and motion.",
            theme.name + (if (look.accent != null) " · own accent" else "") + (if (settings.reduceMotion) " · still" else ""),
            pages.getValue("look"),
        ),
        SettingsCategory(
            "home", "Home & layout", Glyph.Home, Palette.Butter,
            "Your home layout and its screen, how games show inside a system, the dock and widgets, the clock and the screensaver.",
            (HomeLayouts.firstOrNull { it.first == look.homeLayout }?.second?.substringBefore(" (") ?: "Shelves") +
                if (iconGrid) " · ${look.gridAcross} across" else " · ${GameViews.firstOrNull { it.first == look.gameView }?.second ?: "Grid"} of games",
            pages.getValue("home"),
        ),
        SettingsCategory(
            "screens", "Two screens", Glyph.Screens, Palette.Sky,
            "What each screen shows, while you browse and while you play: one screen or two, swapping them, the other screen's art, and moving apps between them.",
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
            "Button prompts, swapping A/B and X/Y, rebinding any button, your controllers and the Deck.",
            Prompts.label(look.prompts) + " prompts" +
                (if (Bindings.swapped(bindings, Command.Open, Command.Back)) " · A/B swapped" else "") +
                (Bindings.changed(bindings).takeIf { it > 0 }?.let { " · ${io.github.zahakj.tandem.plural(it, "button")} rebound" } ?: ""),
            pages.getValue("controls"),
        ),
        SettingsCategory(
            "library", "Library & art", Glyph.Cartridge, Palette.Mint,
            "Game folders, rescanning and sorting, systems and emulators, artwork and its sources, and your " +
                (if (DiscordPresence.available) "RetroAchievements, Steam and Discord accounts." else "RetroAchievements and Steam accounts."),
            listOfNotNull(
                if (games == 0) "No games yet" else "${io.github.zahakj.tandem.plural(games, "game")} · ${io.github.zahakj.tandem.plural(systems, "system")}",
                "${io.github.zahakj.tandem.plural(ss.active.size, "source")} on",
                if (ra.user.isNotBlank() && ra.key.isNotBlank()) "RA ✓" else null,
                if (io.github.zahakj.tandem.data.SteamStore.account.signedIn) "Steam ✓" else null,
            ).joinToString(" · "),
            pages.getValue("library"),
        ),
        SettingsCategory(
            "play", "Play & system", Glyph.Chart, Palette.Mint,
            "Resuming and closing games, performance, privacy and hidden games, backups, Tandem as your Home screen, and updates.",
            (if (shizuku) "Resume ready" else "Resume needs Shizuku") + (if (isHome) " · Home screen" else " · not the Home screen"),
            pages.getValue("play"),
        ),
    )
    val byId = top.associateBy { it.id }
    return top + SettingsPlan.subs.map { s ->
        val parent = byId.getValue(s.parent)
        val sections = pages.getValue(s.id)
        SettingsCategory(s.id, s.title, s.icon, parent.color, s.help, subSummary(s.id, sections.flatMap { it.rows }), sections, parent = s.parent)
    }
}

/** The row on a page that opens subpage [id], or none while the subpage has nothing to show (another layout's). */
private fun subRow(id: String, sections: List<SettingSection>): SettingRow? {
    val sub = SettingsPlan.sub(id) ?: return null
    val rows = sections.flatMap { it.rows }
    val first = rows.firstOrNull() ?: return null
    return SettingRow(sub.title, subSummary(id, rows), first.color, sub.icon, sub.help, opens = true) { Nav.go(SettingsCategories.route(id)) }
}

/** The rows on Connected controllers that aren't a controller. */
private val PadsNotPads = setOf("No other controllers", "Controller Home button", "Hold the Home button")
