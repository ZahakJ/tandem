package io.github.zahakj.tandem.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import io.github.zahakj.tandem.MainActivity
import io.github.zahakj.tandem.data.HomeSettings
import io.github.zahakj.tandem.data.HomeStore
import io.github.zahakj.tandem.data.Look
import io.github.zahakj.tandem.data.Settings
import io.github.zahakj.tandem.data.Store
import io.github.zahakj.tandem.data.WidgetConfig
import io.github.zahakj.tandem.data.WidgetStore

/**
 * Quick looks: a whole setup in one press, for people who'd rather not tune twenty rows. Each one changes a handful
 * of related settings together, says exactly what it changes, and can be undone with a second press (the card
 * says so) until another one is picked. Themes are left alone, except that "Icon grid look" puts on an installed
 * Cocoon theme pack when a built-in theme is in use.
 */
class LookPreset(
    val id: String,
    val title: String,
    val color: Color,
    val icon: Glyph,
    /** Which home layout the preview card draws; empty for the one in use. */
    val layout: String,
    val blurb: String,
    val changes: List<String>,
    /** True when the current settings already look like this preset. */
    val matches: (Look, Settings) -> Boolean,
    val apply: (Context) -> Unit,
)

object Presets {
    private class Snapshot(val settings: Settings, val home: HomeSettings, val widgets: WidgetConfig, val grid: io.github.zahakj.tandem.data.IconGridConfig)

    /** The preset applied last, while it can still be undone. */
    var undoable by mutableStateOf<String?>(null)
        private set
    private var before: Snapshot? = null

    val all: List<LookPreset> get() = listOf(Cozy, Cocoon, Minimal, Battery)

    fun byId(id: String) = all.firstOrNull { it.id == id }

    /** A on a preset: applies it, or undoes it if it was the last one applied. */
    fun press(ctx: Context, p: LookPreset) {
        if (undoable == p.id) { undo(ctx); return }
        // Undo always goes back to how things were before the first of a run of presets.
        if (undoable == null) before = Snapshot(Store.settings, HomeStore.settings, WidgetStore.config, io.github.zahakj.tandem.data.IconGridStore.config)
        io.github.zahakj.tandem.data.Backups.auto(ctx, "Quick look")
        p.apply(ctx)
        undoable = p.id
        refresh(ctx)
        Sounds.confirm()
        Toasts.show("${p.title} is on. Press A on it again to undo")
    }

    fun undo(ctx: Context) {
        val b = before ?: return
        Store.updateSettings { b.settings }
        HomeStore.updateSettings { b.home }
        WidgetStore.update { b.widgets }
        io.github.zahakj.tandem.data.IconGridStore.update { b.grid }
        undoable = null
        before = null
        refresh(ctx)
        Toasts.show("Back to how it was")
    }

    /** Applies [p] without the undo toast (the first-run welcome). */
    fun applyQuietly(ctx: Context, p: LookPreset) { p.apply(ctx); refresh(ctx) }

    private fun refresh(ctx: Context) {
        (ctx as? MainActivity)?.applyScreenRoles()
        Music.refresh()
    }

    private fun look(change: (Look) -> Look) = Store.updateSettings { it.copy(look = change(it.look)) }

    private val Cozy = LookPreset(
        "cozy", "Cozy shelves", Palette.Butter, Glyph.Home, "shelves",
        "Tandem's own home: rows of recent games, systems and collections, with details and art on the bottom screen.",
        listOf("Shelves home", "Grid of games inside systems", "Details on the bottom screen", "Floating apps dock", "Library on the top screen"),
        matches = { l, s -> l.homeLayout == "shelves" && !l.swapScreens && s.bottomMode == "details" },
    ) {
        look {
            it.copy(
                homeLayout = "shelves", gameView = "grid", folderPopup = false, fullscreenFolders = false, swapScreens = false,
                dockStyle = "bar", nowPlaying = true, homeSections = Look().homeSections,
            )
        }
        Store.updateSettings { it.copy(bottomMode = "details") }
        HomeStore.updateSettings { it.copy(showDock = true) }
    }

    private val Cocoon = LookPreset(
        // The id stays "cocoon" so saved settings and search keep finding it.
        "cocoon", "Icon grid look", Palette.Sky, Glyph.Grid, "grid",
        "One dense grid of icons that scrolls sideways, the highlighted icon big on the other screen, X swaps the screens.",
        listOf(
            "Icon grid home, 7 across, sideways", "Spotlight on the other screen", "X swaps the screens on the grid",
            "Folders open over the grid", "Pill dock with your apps", "Your Cocoon theme pack, if one is installed",
            "With a Cocoon backup: your exact home, dock and theme",
        ),
        matches = { l, _ -> l.homeLayout == "grid" && l.gridDetails == "spotlight" },
    ) { ctx ->
        look {
            it.copy(
                homeLayout = "grid", gridScroll = "horizontal", gridAcross = 7, gridHeroSplit = 0f, gridLabels = false,
                gridDetails = "spotlight", gridFolders = "popup", xButton = "swap-grid", iconPack = "auto", dockStyle = "bar",
            )
        }
        // With a Cocoon backup, the backup's setup comes over whole (CocoonSetup.kt): layout, dock, theme.
        if (CocoonSetup.newestBackup() != null) { CocoonSetup.run(ctx); return@LookPreset }
        HomeStore.updateSettings { it.copy(showDock = true) }
        // An installed Cocoon pack comes in whole when a built-in theme is on: the most recently installed one.
        if (ThemePacks.byId(Store.settings.look.theme) == null) {
            val pack = ThemePacks.newestCocoonPack()
            if (pack != null) applyTheme(pack.id)
        }
    }

    private val Minimal = LookPreset(
        "minimal", "Minimal", Palette.Lavender, Glyph.Sort, "list",
        "Calm and quick: a compact list, crisp outlines instead of glows, short animations, no music and fewer extras.",
        listOf("List home and list of games", "Outline highlight, minimal motion", "Music and jingles off", "Icons-only dock that hides", "No widgets row, no splash"),
        matches = { l, _ -> l.homeLayout == "list" && l.motion == AnimationStyle.Minimal.id },
    ) {
        look {
            it.copy(
                homeLayout = "list", gameView = "list", motion = AnimationStyle.Minimal.id, highlight = HighlightStyle.Outline.id,
                music = false, jingles = "off", gameJingles = false, splash = false, cornerHints = false, dockStyle = "minimal", dockAutoHide = true,
            )
        }
        WidgetStore.update { it.copy(homeEnabled = false) }
    }

    private val Battery = LookPreset(
        "battery", "Battery saver", Palette.Mint, Glyph.Battery, "",
        "Keeps the screens still and the speakers quiet so the battery lasts longer. Your layout and theme stay as they are.",
        listOf("Reduce motion on (still wallpapers)", "No video snaps", "No panning art", "Music off", "Quick, minimal animations"),
        matches = { l, s -> s.reduceMotion && !l.videoSnaps && !l.music },
    ) {
        look { it.copy(videoSnaps = false, kenBurns = false, music = false, motion = AnimationStyle.Minimal.id) }
        Store.updateSettings { it.copy(reduceMotion = true) }
    }
}
