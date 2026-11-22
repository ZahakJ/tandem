package io.github.zahakj.tandem.ui

import androidx.annotation.StringRes
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
import io.github.zahakj.tandem.R
import io.github.zahakj.tandem.Shell
import io.github.zahakj.tandem.Tx
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
    /** [title] in English, never shown: English words find the page in every language. */
    val en: String = title,
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

    /** A page's name, in Tandem's language. */
    fun title(id: String): String = Tx.s(titleRes(id))

    /** A page's name in English: never shown, for search. */
    fun titleEn(id: String): String = Tx.en(titleRes(id))

    @StringRes private fun titleRes(id: String): Int = SettingsPlan.sub(id)?.name ?: when (id) {
        "look" -> R.string.settings_page_look
        "home" -> R.string.settings_page_home
        "screens" -> R.string.settings_page_screens
        "sound" -> R.string.settings_page_sound
        "controls" -> R.string.settings_page_controls
        "library" -> R.string.settings_page_library
        else -> R.string.settings_page_play
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

    /** A group of a page: [title] is its English name (its rows' focus keys use it), [name] what it shows. */
    data class Group(val title: String, @StringRes val name: Int, val main: List<Pick>, @StringRes val note: Int? = null)

    /**
     * A short page of its own, opened from a row on page [parent]; [title] (English, a key) and [name] (shown) name
     * both the row and the page.
     */
    data class Sub(val id: String, val parent: String, val title: String, @StringRes val name: Int, val icon: Glyph, @StringRes val help: Int)

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
        Sub("look/wallpaper", "look", "Wallpaper", R.string.settings_sub_wallpaper, Glyph.Image, R.string.settings_sub_wallpaper_help),
        Sub("look/motion", "look", "Motion", R.string.settings_sub_motion, Glyph.Motion, R.string.settings_sub_motion_help),
        Sub("home/grid", "home", "Grid shape", R.string.settings_sub_grid, Glyph.Corners, R.string.settings_sub_grid_help),
        Sub("home/games", "home", "How games show", R.string.settings_sub_games, Glyph.Grid, R.string.settings_sub_games_help),
        Sub("home/dock", "home", "Dock & widgets", R.string.settings_sub_dock, Glyph.Layers, R.string.settings_sub_dock_help),
        Sub("screens/art", "screens", "Art & video", R.string.settings_sub_art, Glyph.Image, R.string.settings_sub_art_help),
        Sub("controls/pads", "controls", "Connected controllers", R.string.settings_sub_pads, Glyph.Gamepad, R.string.settings_sub_pads_help),
        Sub("controls/deck", "controls", "Deck", R.string.settings_sub_deck, Glyph.Text, R.string.settings_sub_deck_help),
        Sub("library/accounts", "library", "Accounts", R.string.settings_sub_accounts, Glyph.Trophy, R.string.settings_sub_accounts_help),
        Sub("library/droiddeck", "library", "DroidDeck", R.string.settings_sub_droiddeck, Glyph.Cartridge, R.string.settings_sub_droiddeck_help),
        Sub("play/perf", "play", "Performance & sleep", R.string.settings_sub_perf, Glyph.Battery, R.string.settings_sub_perf_help),
        Sub("play/privacy", "play", "Privacy & hidden", R.string.settings_sub_privacy, Glyph.Shield, R.string.settings_sub_privacy_help),
        Sub("play/data", "play", "Your data", R.string.settings_sub_data, Glyph.Save, R.string.settings_sub_data_help),
        Sub("play/dev", "play", "For tinkerers", R.string.settings_sub_dev, Glyph.Chart, R.string.settings_sub_dev_help),
    )

    fun sub(id: String): Sub? = subs.firstOrNull { it.id == id }

    /** The seven pages (in tile order), then the subpages. */
    val pages: Map<String, List<Group>> = mapOf(
        "look" to listOf(
            Group("Colour", R.string.settings_group_colour, p("Colour & highlight", "Accent colour", "Highlight style", "Surface")),
            Group("Wallpaper, icons & motion", R.string.settings_group_wallpaper_icons, link("look/wallpaper") + p("System icons", "Icon style", "Icons from") + link("look/motion")),
            Group("Share", R.string.settings_group_share, p("Share looks", "Share my look", "Get a look")),
            Group("Language", R.string.settings_group_language, p("Language", "Language", "Calendar")),
            Group("Start over", R.string.settings_group_start_over, p("Extras", "Reset look")),
        ),
        "home" to listOf(
            Group(
                "Home screen", R.string.settings_group_home_screen,
                grid("Icon grid", "Icon size", "Scrolling") + grid("Motion & home", "Icon names") + grid("Icon grid", "Folders open") +
                    grid("Motion & home", "Recently played strip") + grid("Extras", "Resume page") + link("home/grid", "grid") +
                    orbit("Orbit", "Orbit holds", "Continue ribbon") +
                    spotlight("Spotlight", "Spotlight rail", "Spotlight backdrop", "Spotlight motion") +
                    rows("Layout", "Home rows", "System tiles") + other("Layout", "Open folders") + nogrid("Layout", "Now playing"),
            ),
            Group("Around the home", R.string.settings_group_around_home, link("home/games") + link("home/dock") + p("Top bar", "Clock", "Battery") + p("Home & play", "Screensaver")),
        ),
        "screens" to listOf(
            Group(
                "Screens", R.string.settings_group_screens,
                p("Systems & launching", "Single screen") + p("Screens", "Swap screens") + p("Systems & launching", "Apps open on", "Hold Back to move") +
                    p("Screens", "Keep auto-rotate off"),
            ),
            Group("Other screen", R.string.settings_group_other_screen, grid("Icon grid", "Other screen") + other("Bottom screen", "Style") + p("Widgets", "Bottom screen when idle") + link("screens/art")),
            Group("While playing", R.string.settings_group_while_playing, p("Screen off", "Bottom screen while playing", "Off after") + p("Systems & launching", "Voice in clips")),
        ),
        "sound" to listOf(
            Group("Sounds", R.string.settings_group_sounds, p("Sound & music", "UI sounds", "Custom sounds", "Sound volume", "Pip's voice")),
            Group("Music", R.string.settings_group_music, p("Sound & music", "Music", "Music volume", "Shuffle my music", "Next song")),
        ),
        "controls" to listOf(
            Group(
                "Buttons", R.string.settings_group_buttons,
                p("Controls", "Button prompts", "Swap A and B", "Swap X and Y") + p("Icon grid", "X button") + p("Controls", "Rebind buttons") +
                    p("Layout", "Corner hints"),
                // Replaced with the live bindings when the page is built (buttonsNote).
                note = R.string.settings_group_buttons_note,
            ),
            Group("Controllers", R.string.settings_group_controllers, all(PadLinkGroup) + link("controls/pads") + link("controls/deck")),
        ),
        "library" to listOf(
            Group(
                "Games & systems", R.string.settings_group_games_systems,
                p("Game folders", "Game folders") + p("Library", "Rescan games") + p("Game folders", "Watch game folders") + p("Library", "Sort games by") +
                    p("Systems & launching", "Systems and emulators", "Android apps") + link("library/droiddeck"),
            ),
            Group("Artwork & accounts", R.string.settings_group_artwork_accounts, p("Artwork & details", "Scrape library", "Set up sources", "Clear downloaded media") + link("library/accounts")),
        ),
        "play" to listOf(
            Group(
                "Playing", R.string.settings_group_playing,
                p("About", "Resume running games") + p("Home & play", "Close previous game", "Ask before closing a game", "Immersion mode") +
                    p("Systems & launching", "Launch animation") + link("play/perf"),
            ),
            Group(
                "Tandem", R.string.settings_group_tandem,
                link("play/privacy") + link("play/data") + p("Home & play", "Use Tandem as the Home screen") +
                    // Updates in builds with the updater; About Tandem in those without.
                    p("About", "Updates", "About Tandem", "Check automatically") + link("play/dev"),
            ),
        ),
        "look/wallpaper" to listOf(Group("Wallpaper", R.string.settings_group_wallpaper, p("Wallpaper", "Top screen", "Bottom screen", "Picture dimming"))),
        "look/motion" to listOf(
            Group("Motion", R.string.settings_group_motion, p("Motion", "Animation style", "Reduce motion") + p("Motion & home", "Floating titles", "Motion intensity") + p("Icon grid", "Swap animation")),
        ),
        "home/grid" to listOf(Group("Grid shape", R.string.settings_group_grid_shape, grid("Icon grid", "Rows of icons", "Icon roundness", "Space between icons", "Hero split"))),
        "home/games" to listOf(Group("How games show", R.string.settings_group_how_games_show, p("Layout", "Games view", "Grid size", "Card shape", "Shelf look", "Game titles"))),
        "home/dock" to listOf(
            Group("Dock", R.string.settings_group_dock, p("Dock", "Apps dock") + p("Home & play", "Choose dock apps") + p("Dock", "Dock style", "Dock on top", "Auto-hide dock")),
            Group("Widgets", R.string.settings_group_widgets, p("Widgets", "Customize widgets", "Widgets on the home screen") + grid("Widgets", "Widgets on the grid")),
        ),
        "screens/art" to listOf(Group("Art & video", R.string.settings_group_art_video, p("Bottom screen", "Video snaps", "Moving art", "Logo size", "Art fade"))),
        "controls/pads" to listOf(
            Group(ControllersGroup, R.string.settings_group_game_controllers, all(ControllersGroup), note = R.string.settings_group_pads_note),
            Group(HomeButtonGroup, R.string.settings_group_home_button, p(HomeButtonGroup, "Controller Home button", "Hold the Home button")),
        ),
        "controls/deck" to listOf(Group("Deck", R.string.settings_group_deck, p("Deck", "Trackpad sensitivity", "Default layout", "Key sounds", "Key haptics"))),
        "library/accounts" to listOf(
            Group("RetroAchievements", R.string.settings_group_retroachievements, p("RetroAchievements", "Account", "Show achievements", "Look up games again", "Sign out")),
            Group("Steam", R.string.settings_group_steam, p("Steam", "Steam Web API key", "Steam account", "Look up achievements", "Sign out")),
            Group("Discord", R.string.settings_group_discord, p("Discord", "Discord status", "Disconnect Discord")),
        ),
        "library/droiddeck" to listOf(Group("DroidDeck", R.string.settings_sub_droiddeck, p("DroidDeck", "DroidDeck library", "Show DroidDeck games"))),
        "play/perf" to listOf(
            Group(
                "Performance & sleep", R.string.settings_group_perf_sleep,
                // On an AYN device the three rows; elsewhere, or until Tandem may write system settings, the one note.
                p("Performance while playing", "Performance mode", "Fan mode", "Refresh rate", "Performance profiles") + p("Home & play", "Pause games in sleep"),
            ),
        ),
        "play/privacy" to listOf(
            Group("Privacy & hidden", R.string.settings_group_privacy_hidden, p("Home & play", "Private mode") + p("Library", "Show hidden games") + p("Home & play", "Offline for hidden games", "Private mode PIN")),
        ),
        "play/data" to listOf(
            Group(
                "Your data", R.string.settings_group_your_data,
                p("Your data", "Back up", "Restore") + p("From Cocoon", "Import my Cocoon setup", "Undo Cocoon import") + p("Your data", "Import details") +
                    p("Home & play", "Export play stats"),
            ),
        ),
        "play/dev" to listOf(Group("For tinkerers", R.string.settings_group_tinkerers, p("Developer", "Frame timing overlay", "Tour every theme"))),
    )

    /** The focus key of the row on its page that opens subpage [id] (its group and title, see [rowKey]). */
    fun linkKey(id: String): String? {
        val sub = sub(id) ?: return null
        val group = pages[sub.parent]?.firstOrNull { g -> g.main.any { it.section == LINK && it.label == id } } ?: return null
        return rowKey(group.title, sub.title)
    }

    /**
     * Rows a feature builds that no page should show (a second copy of a setting placed elsewhere): built, but never
     * placed. None today; rather than drop a duplicate, take it out of the feature.
     */
    val dropped: List<Pick> = emptyList()
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
            val extra = Synonyms[r.en]
            rows.putIfAbsent(rowKey(s.en, r.en), s.title to if (extra == null) r else r.copy(keywords = "${r.keywords} $extra"))
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
    fun of(c: Command) = map[c]?.let { Prompts.bindingText(it, style) } ?: Tx.s(R.string.settings_buttons_none)
    return Tx.s(R.string.settings_buttons_note, of(Command.Switcher), of(Command.QuickPanel))
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
    val options = listOf("off" to Tx.s(R.string.common_off), "on" to Tx.s(R.string.common_on), "land" to Tx.s(R.string.settings_resume_page_land))
    val now = if (!shown) "off" else if (lands) "land" else "on"
    return choice(
        Tx.s(R.string.settings_resume_page), Glyph.Play, Palette.Mint, Tx.s(R.string.settings_resume_page_help),
        options, now,
        look = { v -> LayoutPreview("grid", Modifier.fillMaxSize(), rounded = false, resumePage = v != "off") },
        en = "Resume page",
    ) { v ->
        updateLook { it.copy(nowPlaying = v != "off") }
        if (v != "off") io.github.zahakj.tandem.data.ExtrasStore.update { it.copy(resumeLanding = v == "land") }
    }.copy(keywords = "now playing running game resume home button landing")
}

/** What a subpage's row on its page says it holds, in a few words. */
private fun subSummary(id: String, rows: List<SettingRow>): String {
    fun row(en: String) = rows.firstOrNull { it.en == en }
    fun value(en: String) = row(en)?.shownValue
    return when (id) {
        // "0" is the Auto option, whatever it reads in the language.
        "home/grid" -> row("Rows of icons")?.let { if (it.selected == "0") Tx.s(R.string.settings_sub_grid_auto) else it.shownValue }
        "home/dock" -> if (HomeStore.settings.showDock) value("Dock style") else Tx.s(R.string.settings_sub_dock_off)
        "screens/art" -> value("Video snaps")?.let { Tx.s(R.string.settings_sub_art_videos, it) }
        "controls/pads" -> rows.count { it.en !in PadsNotPads }.let { if (it == 0) Tx.s(R.string.settings_sub_pads_none) else Tx.p(R.plurals.settings_sub_pads_connected, it) }
        "controls/deck" -> value("Default layout")
        "library/accounts" -> listOfNotNull(
            "RA ✓".takeIf { HomeStore.ra.user.isNotBlank() && HomeStore.ra.key.isNotBlank() },
            "Steam ✓".takeIf { io.github.zahakj.tandem.data.SteamStore.account.signedIn },
            "Discord ✓".takeIf { DiscordPresence.available && DiscordPresence.linked },
        ).joinParts().ifEmpty { Tx.s(R.string.settings_sub_accounts_none) }
        "library/droiddeck" -> row("DroidDeck library")?.value
        "play/privacy" -> Tx.s(if (HomeStore.settings.privateMode) R.string.settings_sub_privacy_on else R.string.settings_sub_privacy_off)
        "play/data" -> Tx.s(R.string.settings_sub_data_summary)
        "play/dev" -> Tx.p(R.plurals.settings_sub_dev_tools, rows.size)
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
            librarySettings(onRescan), droidDeckSettings(onRescan), gameFoldersSettings(), systemsSettings(), scrapingSettings(), homeSettings(), flairSettings(), orbitSettings(), spotlightHomeSettings(), widgetsSettings(),
            achievementsSettings(), steamSettings(), discordSettings(), dataSettings(), aboutSettings(), cocoonSetupSettings(), developerSettings(),
            performanceSettings(), screenOffSettings(), deckSettings(),
            SettingSection("Extras", listOf(resumePageRow())), // tx-ok: a plan key, never shown (the plan places the row)
            languageSettings(),
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
        val note = if (g.title == "Buttons") buttonsNote(look.prompts) else g.note?.let { Tx.s(it) }
        SettingSection(Tx.s(g.name), main, note = note, en = g.title)
    }.also { built[id] = it }
    // The theme pack's own music and sounds lead the Sound page, when the theme has any.
    val soundPage = listOf(
        SettingSection(Tx.s(R.string.settings_group_theme_pack), pool.takeAll("Theme pack"), note = ThemePacks.byId(look.theme)?.name, en = "Theme pack"),
    ) + page("sound")
    val pages = SettingsPlan.pages.keys.associateWith { if (it == "sound") soundPage else page(it) }.toMutableMap()
    // A row the plan doesn't know yet still shows, so nothing is ever out of reach; a debug build says so.
    val left = pool.leftovers()
    if (left.isNotEmpty()) {
        android.util.Log.w("Tandem", "Settings rows not placed: " + left.joinToString { it.label })
        val debug = io.github.zahakj.tandem.BuildConfig.DEBUG
        pages["play"] = pages.getValue("play") +
            SettingSection(Tx.s(if (debug) R.string.settings_group_not_placed else R.string.settings_group_other), left, en = if (debug) "Not placed" else "Other")
    }

    val ra = HomeStore.ra
    val ss = ScrapeConfig.settings
    val shizuku = remember(ShizukuWatch.version) { Shell.ready() }
    val isHome = Facts.run { ensure(ctx); Facts.isHome }
    // derivedStateOf: details changing (a scrape, a favourite) only recomposes Settings when the count changes.
    val games by remember { androidx.compose.runtime.derivedStateOf { io.github.zahakj.tandem.Collections.visibleGames().size } }
    val systems = Libraries.all.orEmpty().count { it.games.isNotEmpty() }

    // A page tile's summary is a few parts joined with " · " (PartsText draws them so), each part a whole phrase.
    fun parts(vararg p: String?) = p.filterNotNull().joinParts()
    fun tile(id: String, icon: Glyph, color: Color, @StringRes blurb: Int, summary: String) =
        SettingsCategory(id, SettingsCategories.title(id), icon, color, Tx.s(blurb), summary, pages.getValue(id), en = SettingsCategories.titleEn(id))
    val top = listOf(
        tile(
            "look", Glyph.Theme, Palette.Accent, R.string.settings_page_look_blurb,
            parts(
                Tx.s(R.string.settings_page_look_own_accent).takeIf { look.accent != null },
                Tx.s(R.string.settings_page_look_still).takeIf { settings.reduceMotion },
            ),
        ),
        tile(
            "home", Glyph.Home, Palette.Butter, R.string.settings_page_home_blurb,
            parts(
                HomeLayouts.firstOrNull { it.first == look.homeLayout }?.second?.substringBefore(" (") ?: Tx.s(R.string.settings_page_home_shelves),
                if (iconGrid) Tx.s(R.string.settings_page_home_across, look.gridAcross)
                else Tx.s(R.string.settings_page_home_of_games, GameViews.firstOrNull { it.first == look.gameView }?.second ?: Tx.s(R.string.settings_page_home_grid)),
            ),
        ),
        tile(
            "screens", Glyph.Screens, Palette.Sky, R.string.settings_page_screens_blurb,
            when {
                SystemPrefs.data.singleScreen -> Tx.s(R.string.settings_page_screens_single)
                look.swapScreens -> Tx.s(R.string.settings_page_screens_swapped)
                else -> Tx.s(
                    R.string.settings_page_screens_details,
                    BottomModes.firstOrNull { it.first == settings.bottomMode }?.second ?: Tx.s(R.string.settings_page_screens_details_mode),
                )
            },
        ),
        SettingsCategory(
            "sound", SettingsCategories.title("sound"), Glyph.Music, Palette.Peach, Tx.s(R.string.settings_page_sound_blurb),
            // Pack names (a theme pack's, or a sound pack's) are long and the side panel shows them in full: the tile
            // stays quiet and only says so when sounds are off.
            if (look.sounds) "" else Tx.s(R.string.settings_page_sound_sounds_off),
            soundPage, en = SettingsCategories.titleEn("sound"),
        ),
        tile(
            "controls", Glyph.Gamepad, Palette.Lavender, R.string.settings_page_controls_blurb,
            parts(
                Tx.s(R.string.settings_page_controls_prompts, Prompts.label(look.prompts)),
                Tx.s(R.string.settings_page_controls_swapped).takeIf { Bindings.swapped(bindings, Command.Open, Command.Back) },
                Bindings.changed(bindings).takeIf { it > 0 }?.let { Tx.p(R.plurals.settings_page_controls_rebound, it) },
            ),
        ),
        tile(
            "library", Glyph.Cartridge, Palette.Mint,
            if (DiscordPresence.available) R.string.settings_page_library_blurb_discord else R.string.settings_page_library_blurb,
            parts(
                if (games == 0) Tx.s(R.string.settings_page_library_no_games) else Tx.p(R.plurals.settings_page_library_games, games),
                if (games == 0) null else Tx.p(R.plurals.settings_page_library_systems, systems),
                Tx.p(R.plurals.settings_page_library_sources, ss.active.size),
                if (ra.user.isNotBlank() && ra.key.isNotBlank()) "RA ✓" else null,
                if (io.github.zahakj.tandem.data.SteamStore.account.signedIn) "Steam ✓" else null,
            ),
        ),
        tile(
            "play", Glyph.Chart, Palette.Mint, R.string.settings_page_play_blurb,
            parts(
                Tx.s(if (shizuku) R.string.settings_page_play_resume_ready else R.string.settings_page_play_resume_shizuku),
                Tx.s(if (isHome) R.string.settings_page_play_home else R.string.settings_page_play_not_home),
            ),
        ),
    )
    val byId = top.associateBy { it.id }
    return top + SettingsPlan.subs.map { s ->
        val parent = byId.getValue(s.parent)
        val sections = pages.getValue(s.id)
        SettingsCategory(
            s.id, Tx.s(s.name), s.icon, parent.color, Tx.s(s.help), subSummary(s.id, sections.flatMap { it.rows }), sections,
            parent = s.parent, en = s.title,
        )
    }
}

/** The row on a page that opens subpage [id], or none while the subpage has nothing to show (another layout's). */
private fun subRow(id: String, sections: List<SettingSection>): SettingRow? {
    val sub = SettingsPlan.sub(id) ?: return null
    val rows = sections.flatMap { it.rows }
    val first = rows.firstOrNull() ?: return null
    return SettingRow(Tx.s(sub.name), subSummary(id, rows), first.color, sub.icon, Tx.s(sub.help), opens = true, en = sub.title) {
        Nav.go(SettingsCategories.route(id))
    }
}

/** The rows on Connected controllers that aren't a controller. */
private val PadsNotPads = setOf("No other controllers", "Controller Home button", "Hold the Home button")
