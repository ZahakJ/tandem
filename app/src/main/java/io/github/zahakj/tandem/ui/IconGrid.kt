package io.github.zahakj.tandem.ui

import androidx.compose.foundation.layout.widthIn
import androidx.compose.animation.slideInVertically
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import io.github.zahakj.tandem.Button
import io.github.zahakj.tandem.Buttons
import io.github.zahakj.tandem.Collections
import io.github.zahakj.tandem.Game
import io.github.zahakj.tandem.GameCollection
import io.github.zahakj.tandem.GroupTarget
import io.github.zahakj.tandem.Launcher
import io.github.zahakj.tandem.Libraries
import io.github.zahakj.tandem.MainActivity
import io.github.zahakj.tandem.PlatformLibrary
import io.github.zahakj.tandem.HiddenFolders
import io.github.zahakj.tandem.Privacy
import io.github.zahakj.tandem.RunningGames
import io.github.zahakj.tandem.Selected
import io.github.zahakj.tandem.Selection
import io.github.zahakj.tandem.data.ExtrasStore
import io.github.zahakj.tandem.data.FolderSort
import io.github.zahakj.tandem.data.GridShown
import io.github.zahakj.tandem.data.GridSlots
import io.github.zahakj.tandem.data.GridTree
import io.github.zahakj.tandem.data.HomeStore
import io.github.zahakj.tandem.data.IconGridStore
import io.github.zahakj.tandem.data.Store
import io.github.zahakj.tandem.plural
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

/*
 * The icon grid home ("grid" layout), in the spirit of the 3DS HOME menu: every system, collection,
 * folder, app and pinned game as a same-size square icon on one dense grid that scrolls sideways (or a page at a time,
 * or up and down), a floating pill dock of apps, and the highlighted item shown big on the other screen
 * (Spotlight.kt). X swaps the two screens. Y opens the tile menu: move tiles with the D-pad, make folders, add and
 * remove things. The arrangement lives in IconGridStore.
 */

/** What kind of thing a tile is. */
enum class TileKind { System, Collection, Genres, Genre, App, Game, Folder, AllApps, Now, BuiltIn, Empty, Widget }

/** One icon on the grid. */
class GridItem(
    val key: String,
    val kind: TileKind,
    val title: String,
    /** "System", "Folder", "App"... shown under the name on the other screen. */
    val type: String,
    /** "42 games", "3 items"; may be empty. */
    val detail: String,
    val color: Color,
    val art: TileArt,
    val games: List<Game>,
    val selected: Selected,
    /** Shows on the home grid unless the user hid it (systems and collections). */
    val default: Boolean,
    val open: () -> Unit,
    val game: Game? = null,
    val pkg: String? = null,
    val platformId: String? = null,
    val folderId: String? = null,
    val collectionId: String? = null,
    /** A line about it for the other screen ("Games you've hearted"). */
    val blurb: String = "",
    val builtIn: BuiltInApp? = null,
    /** A folder's smart-folder art names in the icon pack (FolderArtNames), for its hero and logo on the other screen. */
    val packNames: List<String> = emptyList(),
    /** A hidden system or folder, showing while hidden games are revealed: dimmed, with the hidden mark. */
    val hidden: Boolean = false,
    /** Its designed look (FolderLooks.kt), when it has one; [plain] is the tile before it was applied. */
    val style: io.github.zahakj.tandem.data.FolderStyle? = null,
    val plain: GridItem? = null,
    /** A widget on the grid: its id in WidgetStore (see GridWidgets). */
    val widgetId: String? = null,
)

/** Tile menu target: [key] on the home grid ([folder] null) or inside a folder. */
data class TileMenuTarget(val key: String, val folder: String?)

/**
 * A tile being moved with the D-pad: the slots it is moving in (with gaps), and those slots before the move (for
 * Cancel). [carried]: Y let go of the D-pad, so it can be pasted on an empty spot anywhere (see GridSpots.kt).
 */
class MovingTile(val key: String, val folder: String?, val order: SnapshotStateList<String>, val original: List<String>) {
    var carried by mutableStateOf(false)
    /** A widget resized while moving (Y): its new size, saved when it is dropped. */
    var span by mutableStateOf<io.github.zahakj.tandem.data.GridSpan?>(null)
}

object IconGrid {
    /** The icon grid is the home layout. */
    val enabled: Boolean get() = Store.settings.look.homeLayout == "grid"

    /** How many icon grid homes are composed now (on either screen). */
    var composed by androidx.compose.runtime.mutableIntStateOf(0)
        internal set

    /** The icon grid is what Home shows: the chosen layout, or a grid on screen right now. */
    val isHome: Boolean get() = enabled || composed > 0

    /** The tile shown on the other screen: the last grid tile that had focus (see [Spotlight]). */
    var spot by mutableStateOf<GridItem?>(null)
    var menu by mutableStateOf<TileMenuTarget?>(null)
    /** The tile menu's second page: picking a folder. */
    var choosingFolder by mutableStateOf(false)
    /** Which page [choosingFolder] shows: "move", "add" (Also add to folder), "delete" or "sort". */
    var folderPage by mutableStateOf<String?>(null)
    var openFolder by mutableStateOf<String?>(null)
    var adding by mutableStateOf(false)
    var moving by mutableStateOf<MovingTile?>(null)
    /** The size and corner the highlighted tile is drawn at, so its lifted copy in the Y menu is the same picture. */
    internal var liftTile: Dp = 84.dp
    internal var liftCorner: Dp = 16.dp
    /** Tiles marked for a bulk action (GridSelect.kt), on the home grid or in one folder. */
    var selecting by mutableStateOf<GridSelection?>(null)
    /**
     * What the tile menu needs, handed over from the grid (it is composed elsewhere, see [IconGridMenuHost]). State,
     * set after the grid composes: the grid is laid out (and composed) after the menu host, so a plain field read
     * there was still empty on a fresh start and the host never looked again, leaving every Y and Add here menu dead.
     */
    var menuData by mutableStateOf<Pair<GridCatalog, List<String>>?>(null)
        internal set
    /** Add to home shows only one kind of thing ("system", "collection", "app", "tandem"); null: everything. */
    var addFilter by mutableStateOf<String?>(null)
    /** The game picker (Add here › Game…) is open, for this spot. */
    var pickingGames by mutableStateOf<SlotTarget?>(null)
    /** The grid scrolls up and down: its lanes are columns, not rows. */
    val vertical: Boolean get() = Store.settings.look.gridScroll == "vertical"
    /** The home tile a folder popup was opened from, so the popup grows out of it and shrinks back into it. */
    var openedFrom: String? = null
    /** The home grid and the open folder as they show now, for edits made from menus (see GridSpots.kt). */
    var home: io.github.zahakj.tandem.data.GridShown? = null
    var folderShown: io.github.zahakj.tandem.data.GridShown? = null
    /** Add to home puts what is picked in this slot (an empty spot's "Add to this spot…"); null: after the last tile. */
    var addAt: SlotTarget? = null

    /** Focus routes for the folder panel and the add panel. */
    fun folderRoute(id: String) = "gridfolder:$id"
    const val ADD_ROUTE = "gridadd"
    const val MENU_ROUTE = "gridmenu"

    /** Opens the tile menu for [key]. */
    fun openMenu(key: String, folder: String?) {
        FocusMemory.forget(MENU_ROUTE)
        choosingFolder = false
        folderPage = null
        menu = TileMenuTarget(key, folder)
    }

    fun closeMenu() { menu = null; choosingFolder = false; folderPage = null; Nav.focusTick++ }

    /** Opens page [page] of the tile menu ("move", "add", "delete", "sort"). */
    fun menuPage(page: String) { FocusMemory.forget(MENU_ROUTE + ":page"); folderPage = page; choosingFolder = true }

    /** How many rows the grid shows now, so L1 / R1 can step on from the automatic size to fixed rows. */
    internal var autoRows = 2
    /** The grid is on the bottom screen (screens swapped), where at most 3 rows fit. */
    internal var onBottom = false
    /** Lines (columns of a sideways grid, rows of an up-and-down one) on one screen of the home grid. */
    internal var screenLines = 4

    /** The zoom an L1 / R1 press on the grid asks for, done when that button comes up alone ([shoulderUp]). */
    private var armedZoom = 0

    fun armZoom(by: Int) { armedZoom = by }

    /** L1 ([r1] false) or R1 came up; [solo]: pressed and released with no other button. */
    fun shoulderUp(r1: Boolean, solo: Boolean) {
        val by = armedZoom
        armedZoom = 0
        if (solo && by == (if (r1) 1 else -1)) zoom(by)
    }

    /** L1 / R1: bigger or smaller icons, like the 3DS HOME menu's zoom. */
    fun zoom(by: Int) {
        val look = Store.settings.look
        val sideways = look.gridScroll != "vertical"
        // Fixed rows: R1 makes the icons smaller, so one more row fits; L1 goes down to a single row
        // of big icons (the 3DS HOME menu's biggest zoom). L1 on the biggest automatic size steps into fixed rows.
        // The bottom screen's grid always has fixed rows (3 when automatic, at most 3).
        val fromAuto = sideways && look.gridRows <= 0 && (onBottom || by < 0 && look.gridAcross <= 5)
        if (sideways && (look.gridRows > 0 || fromAuto)) {
            val most = if (onBottom) 3 else 5
            val now = if (fromAuto) autoRows else look.gridRows.coerceAtMost(most)
            val rows = (now + by).coerceIn(1, most)
            if (rows == now) return
            Store.updateSettings { it.copy(look = it.look.copy(gridRows = rows)) }
            Toasts.show(if (rows == 1) "Icons: 1 row" else "Icons: $rows rows")
            return
        }
        val now = look.gridAcross
        val next = (now + by).coerceIn(5, 9)
        if (next == now) return
        Store.updateSettings { it.copy(look = it.look.copy(gridAcross = next)) }
        Toasts.show("Icons: ${sizeName(next)}")
    }

    fun sizeName(across: Int): String = when {
        across <= 5 -> "Large"
        across <= 6 -> "Large-ish"
        across == 7 -> "Medium"
        across == 8 -> "Small-ish"
        else -> "Small"
    }
}

// --- What the tiles are ---------------------------------------------------------------------------------------

private val FolderColors = listOf(0xFF5B8DEF, 0xFF8B6CEF, 0xFF43BE86, 0xFFF29A4A, 0xFFEC6C98, 0xFF35B3C6, 0xFFE0B23A)

fun folderColor(i: Int): Color = Color(FolderColors[abs(i) % FolderColors.size])

private val AllAppsColor = Color(0xFF7F8BB0)

/** Each collection's own colour and drawn icon, so no two collections ever look alike (TandemIcons.kt). */
private val CollectionLooks: Map<String, Pair<TIcon, Long>> = mapOf(
    "fav" to (TIcon.Heart to 0xFFEC5B8C),
    "most" to (TIcon.Flame to 0xFFF26A3D),
    "added" to (TIcon.NewBox to 0xFF4AA8E8),
    "continue" to (TIcon.Rewind to 0xFF35B9A0),
    "unplayed" to (TIcon.Sprout to 0xFF76BE4A),
)

/** What each collection is, for the other screen. */
private val CollectionBlurbs = mapOf(
    "fav" to "Games you've hearted. Y › Add to favorites on any game",
    "most" to "Your most played games, by time played",
    "added" to "The newest games in your library",
    "continue" to "Pick up where you left off",
    "unplayed" to "Fresh adventures you haven't started yet",
)

/** Hues for single genres, handed out in order so neighbours differ. */
private val GenreHues = listOf(0xFF6C7BEA, 0xFFE9774F, 0xFF3FB58C, 0xFFD45FB4, 0xFFE2AE36, 0xFF3D9FD6, 0xFFA66CE8, 0xFF8BBF3F, 0xFFE95F6A, 0xFF4CC0C0, 0xFFB98A5A, 0xFF7E8FB8)

private val GenresColor = Color(0xFF6C6BEA)

private object AppLabels {
    private val cache = HashMap<String, String>()
    /** The name the user gave the app, else its own label (looked up once). */
    fun of(ctx: Context, pkg: String): String =
        io.github.zahakj.tandem.data.AppPrefs.name(pkg) ?: cache.getOrPut(pkg) { RunningGames.appLabel(ctx, pkg) }
}

/**
 * Everything that can be a tile, looked up by key. Built once per change of the library, its details, the
 * arrangement or the icon pack; tiles are made on first use.
 */
class GridCatalog(private val ctx: Context, libs: List<PlatformLibrary>) {
    private val systems: Map<String, Pair<PlatformLibrary, List<Game>>> = LinkedHashMap<String, Pair<PlatformLibrary, List<Game>>>().apply {
        for (lib in libs) Collections.visible(lib.games).takeIf { it.isNotEmpty() }?.let { put(lib.platform.id, lib to it) }
    }
    private val collections: Map<String, GameCollection> = LinkedHashMap<String, GameCollection>().apply {
        Collections.continuePlaying().takeIf { it.isNotEmpty() }?.let { put("continue", GameCollection("continue", "Recently played", it, Palette.Mint)) }
        for (c in Collections.home()) put(c.id, c)
    }
    val genres: Map<String, List<Game>> = Collections.genres()
    private val genreIndex: Map<String, Int> = genres.keys.withIndex().associate { (i, k) -> k to i }
    private val games: Map<String, Game> by lazy { Collections.visible(Libraries.games).associateBy { it.key } }
    private val made = HashMap<String, GridItem?>()

    /**
     * Pack pictures for collections, only where each is a picture of its own: packs often reuse one image (the same
     * "play" icon for Most played and Recently played), and two lookalike tiles are worse than a drawn icon.
     */
    private val collectionPictures: Map<String, File> = run {
        val found = collections.keys.mapNotNull { id -> IconPacks.collectionIcon(id)?.let { id to it } }
        val counts = found.groupingBy { IconPacks.fingerprint(it.second) }.eachCount()
        found.filter { counts[IconPacks.fingerprint(it.second)] == 1 }.toMap()
    }

    /** Tiles that show on the home grid unless hidden, in their default order. Recently played is a strip now (L2). */
    val defaults: List<String> = systems.keys.map { "s:$it" } + collections.keys.filter { it != "continue" }.map { "col:$it" } +
        listOfNotNull("genres".takeIf { genres.isNotEmpty() }) + BuiltInApps.all.filter { it.onHome }.map { it.key }

    /** Folders being built now: a folder that (in broken data) holds itself shows as missing inside itself. */
    private val building = HashSet<String>()

    fun item(key: String): GridItem? {
        if (made.containsKey(key)) return made[key]
        if (!building.add(key)) return null
        try {
            return FolderLooks.styled(build(key)).also { made[key] = it }
        } finally {
            building.remove(key)
        }
    }

    /** The tile (made so far) that stands for [s], so the other screen shows it as what it is, not as a folder. */
    fun find(s: Selected): GridItem? = made.values.firstOrNull { it != null && (it.selected === s || it.selected == s) }

    /** Apps that are emulators of some system, so their tiles say "Emulator". */
    private val emulatorPkgs: Set<String> by lazy {
        libs.flatMap { lib -> lib.platform.allPlayers.filter { it.component != io.github.zahakj.tandem.LaunchSpec.ANY_APP }.map { it.packageName } }
            .filter { it.isNotBlank() }.toSet()
    }

    private fun build(key: String): GridItem? {
        val kind = key.substringBefore(':')
        val id = key.substringAfter(':', "")
        return when {
            kind == "s" -> systems[id]?.let { (lib, games) -> system(lib, games) }
            kind == "col" -> collections[id]?.let { collection(it) }
            key == "genres" -> genresTile()
            kind == "genre" -> genres[id]?.let { genre(id, it) }
            kind == "app" -> app(id)
            kind == "game" -> games[id]?.let { game(it, "game:${it.key}", "Game") }
            kind == "folder" -> IconGridStore.folder(id)?.takeIf { Privacy.canShowHomeFolder(it) }?.let { folder(it) }
            kind == "tandem" -> BuiltInApps.byId(id)?.let { builtIn(it) }
            kind == "widget" -> widget(id)
            key == "allapps" -> GridItem(
                key, TileKind.AllApps, "All apps", "Apps", "Everything installed", AllAppsColor,
                TileArt.Tandem(TIcon.AllApps, AllAppsColor), emptyList(),
                Selected.Group("All apps", "Apps", emptyList(), AllAppsColor), default = false, open = { Panels.openApps() },
                blurb = "Every Android app on this device",
            )
            key == "now" -> nowPlaying()?.game?.let { g -> game(g, "now", "Now playing") }
            else -> null
        }
    }

    private fun system(lib: PlatformLibrary, games: List<Game>): GridItem {
        val id = lib.platform.id
        val color = SystemColors.of(id)
        val art = IconPacks.systemIcon(id)?.let { TileArt.Picture(it) } ?: TileArt.Monogram(lib.platform.shortName, color)
        val played = games.sumOf { it.meta.playSeconds }
        return GridItem(
            "s:$id", TileKind.System, lib.platform.name, "System", plural(games.size, "game"), color, art, games,
            Selected.System(lib), default = true, open = { openFolder(Route.System(id)) }, platformId = id,
            blurb = if (played >= 60) "${formatPlayLong(played)} played" else "",
            hidden = io.github.zahakj.tandem.HiddenFolders.isHidden(id, null),
        )
    }

    private fun collection(c: GameCollection): GridItem {
        val look = CollectionLooks[c.id]
        val color = look?.second?.let { Color(it) } ?: c.color
        val art = collectionPictures[c.id]?.let { TileArt.Picture(it) } ?: TileArt.Tandem(look?.first ?: TIcon.Star, color)
        return GridItem(
            "col:${c.id}", TileKind.Collection, c.title, "Collection", plural(c.games.size, "game"), color, art, c.games,
            Selected.Group(c.title, plural(c.games.size, "game"), c.games, color, GroupTarget.Collection(c.id)),
            default = c.id != "continue", open = { openFolder(Route.Collection(c.id)) }, collectionId = c.id,
            blurb = CollectionBlurbs[c.id].orEmpty(),
        )
    }

    private fun genresTile(): GridItem {
        val all = genres.values.flatten().distinct()
        return GridItem(
            "genres", TileKind.Genres, "Genres", "Collection", plural(genres.size, "genre"), GenresColor,
            TileArt.Tandem(TIcon.Genres, GenresColor),
            all, Selected.Group("Genres", plural(genres.size, "genre"), all, GenresColor, GroupTarget.Genres),
            default = true, open = { openFolder(Route.Genres) },
            blurb = genres.keys.take(4).joinToString(", ") + if (genres.size > 4) "…" else "",
        )
    }

    private fun genre(name: String, games: List<Game>): GridItem {
        val color = Color(GenreHues[(genreIndex[name] ?: 0) % GenreHues.size])
        return GridItem(
            "genre:$name", TileKind.Genre, name, "Genre", plural(games.size, "game"), color,
            TileArt.Tandem(genreIcon(name), color), games,
            Selected.Group(name, plural(games.size, "game"), games, color, GroupTarget.Collection("genre:$name")),
            default = false, open = { openFolder(Route.Collection("genre:$name")) },
            blurb = "Games tagged $name",
        )
    }

    private fun builtIn(app: BuiltInApp, key: String = app.key): GridItem = GridItem(
        key, TileKind.BuiltIn, app.title, "Tandem app", "", app.color, app.art, emptyList(),
        Selected.Group(app.title, app.blurb, emptyList(), app.color), default = true, open = { app.open(ctx) },
        blurb = app.blurb, builtIn = app,
    )

    private fun app(pkg: String): GridItem? {
        // Tandem's own apps under an app key ("app:tandem:settings", as the dock lists them) are built-in apps.
        if (pkg.startsWith("tandem:")) return Dock.builtIn(pkg)?.let { builtIn(it, "app:$pkg") }
        if (!Dock.launchable(ctx, pkg)) return null
        val label = AppLabels.of(ctx, pkg)
        val color = SystemColors.of("app:$pkg")
        val type = if (pkg in emulatorPkgs) "Emulator" else "App"
        return GridItem(
            "app:$pkg", TileKind.App, label, type, "", color, TileArt.App(pkg), emptyList(),
            Selected.Group(label, type, emptyList(), color), default = false, open = { Dock.open(ctx, pkg) }, pkg = pkg,
        )
    }

    /** A widget placed on the grid (GridWidgets.kt); gone when its kind isn't available (signed out). */
    private fun widget(id: String): GridItem? {
        val w = Widgets.byId(id)?.takeIf { it.place == io.github.zahakj.tandem.data.WidgetPlace.Grid } ?: return null
        val kind = Widgets.kind(w)?.takeIf { it.available() } ?: return null
        return GridItem(
            GridWidgets.key(id), TileKind.Widget, kind.name, "Widget", GridWidgets.span(GridWidgets.key(id)).label, kind.accent,
            TileArt.Symbol(Glyph.Layers, kind.accent), emptyList(), Selected.Group(kind.name, "Widget", emptyList(), kind.accent),
            default = false, open = {}, blurb = kind.blurb, widgetId = id,
        )
    }

    private fun game(g: Game, key: String, type: String): GridItem {
        val color = SystemColors.of(g.platform.id)
        return GridItem(
            key, if (key == "now") TileKind.Now else TileKind.Game, g.title, type, g.platform.name, color, TileArt.GameBox(g), listOf(g),
            Selected.GameItem(g), default = false, open = { Launcher.launch(ctx, g) }, game = g,
        )
    }

    private fun folder(f: io.github.zahakj.tandem.data.HomeFolder): GridItem {
        if (f.smart) return SmartFolders.tile(f)
        val color = folderColor(f.color)
        val inside = f.items.mapNotNull { item(it) }
        val games = inside.flatMap { it.games }.distinct()
        // The icon pack's smart-folder art (Apps, Emulators...), else Tandem's drawn folder with a live mosaic of
        // what's inside (up to twelve things, four at a time).
        val names = FolderArtNames.of(f)
        val art = IconPacks.folderIcon(names)?.let { TileArt.Picture(it) }
            ?: TileArt.Folder(color, Glyph.Folder, inside.map { it.art }.filter { it !is TileArt.Folder }.take(12), f.name.trim().take(1).uppercase().ifEmpty { null })
        return GridItem(
            "folder:${f.id}", TileKind.Folder, f.name, "Folder", plural(inside.size, "item"), color,
            art, games,
            Selected.Group(f.name, plural(inside.size, "item"), games, color), default = false,
            open = { FocusMemory.forget(IconGrid.folderRoute(f.id)); IconGrid.openFolder = f.id }, folderId = f.id,
            blurb = if (inside.isEmpty()) "An empty folder. Y › Move to folder on any tile" else inside.take(3).joinToString(", ") { it.title } + if (inside.size > 3) "…" else "",
            packNames = names, hidden = f.hidden,
        )
    }

    /**
     * The tiles on the home grid now, in the default order (their slots are in IconGridStore). Folders hold
     * shortcuts, so a tile in a folder can be here too; a folder inside another folder isn't.
     */
    fun present(): List<String> {
        val c = IconGridStore.config
        val hidden = c.hidden.toHashSet()
        val nested = c.folders.flatMapTo(HashSet()) { f -> f.items.filter { it.startsWith("folder:") && it != "folder:${f.id}" } }
        val present = LinkedHashSet<String>()
        for (k in defaults) if (k !in hidden) present += k
        for (k in c.added) if (item(k) != null) present += k
        for (f in c.folders) if (Privacy.canShowHomeFolder(f) && "folder:${f.id}" !in nested) present += "folder:${f.id}"
        return present.toList()
    }

    /** The home grid's tiles, in the user's order (their slots without the gaps): their arrangement first, anything new after it. */
    fun arranged(): List<String> {
        val c = IconGridStore.config
        val present = present()
        val here = present.toHashSet()
        return io.github.zahakj.tandem.data.GridSlots.shown(c.order, c.lanes, c.lanes, present).filter { it in here }
    }

    /**
     * Things that can be added to a grid that already shows [here] (the home grid's tiles, or a folder's): systems
     * and collections, built-in apps, single genres. Folders hold shortcuts, so being in some folder doesn't count.
     */
    fun addable(here: Set<String>): List<String> {
        val out = ArrayList<String>()
        for (k in defaults) if (k !in here) out += k
        if ("continue" in collections && "col:continue" !in here) out += "col:continue"
        if ("allapps" !in here) out += "allapps"
        for (a in BuiltInApps.all) if (!a.onHome && a.key !in here) out += a.key
        for (g in genres.keys) { val k = "genre:$g"; if (k !in here) out += k }
        return out
    }

    /** What sorting a folder needs to know about [key] (FolderSort). */
    fun sortFacts(key: String): io.github.zahakj.tandem.data.FolderSort.Facts? {
        val it = item(key) ?: return null
        return io.github.zahakj.tandem.data.FolderSort.Facts(
            it.title,
            lastPlayed = it.games.maxOfOrNull { g -> g.meta.lastPlayed } ?: 0L,
            played = it.games.sumOf { g -> g.meta.playSeconds },
            added = it.games.maxOfOrNull { g -> g.meta.firstSeen } ?: 0L,
            system = it.game?.platform?.name ?: it.platformId?.let { id -> systems[id]?.first?.platform?.name }.orEmpty(),
            folder = it.kind == TileKind.Folder,
        )
    }
}

@Composable
private fun rememberCatalog(libs: List<PlatformLibrary>): GridCatalog {
    val ctx = LocalContext.current
    val meta = Store.meta
    val hidden = Privacy.view
    val config = IconGridStore.config
    val pack = IconPacks.version
    val running = nowPlaying()?.game?.key
    // Apps installed or removed (package broadcasts), not every return to Tandem: a new catalog rebuilds every tile.
    val packages = io.github.zahakj.tandem.Emulators.changes
    // Renamed or re-iconed apps (AppPrefs) show at once.
    val apps = io.github.zahakj.tandem.data.AppPrefs.data
    // Widgets placed on the grid come and go with the widget list.
    val widgets = io.github.zahakj.tandem.data.WidgetStore.config.widgets
    return remember(libs, meta, hidden, config, pack, running, packages, apps, widgets) { GridCatalog(ctx, libs) }
}

// --- Sizes ----------------------------------------------------------------------------------------------------

/** Where the highlighted icon's name tag starts below the icon (just over its outline), and how far it reaches. */
private val FocusLabelTop = 5.dp
val FocusLabelReach = 28.dp

private fun Dp.roundToPxIn(d: androidx.compose.ui.unit.Density): Int = with(d) { roundToPx() }

/** A single row's icons take this share of the height; the rest is the details above the row and the focus room. */
private const val SingleRowShare = 0.52f

/** Icon size, gap and how many fit, worked out from the space there is. */
class GridMetrics(val tile: Dp, val gap: Dp, val rows: Int, val cols: Int, val label: Dp, val margin: Dp, val pad: Dp) {
    val cell: Dp get() = tile + label
}

private fun gridMetrics(w: Dp, h: Dp, mode: String, across: Int, gapShare: Float, labels: Boolean, focusLabels: Boolean = false, rows: Int = 0): GridMetrics {
    val margin = 30.dp
    val a = across.coerceIn(4, 10)
    val g = gapShare.coerceIn(0.08f, 0.6f)
    // Sideways scrolling shows a peek of the next column, so you can tell there's more.
    val peek = if (mode == "horizontal") 0.42f else 0f
    val units = a + (a - 1) * g + if (peek > 0f) g + peek else 0f
    var tile = (w - margin * 2) / units
    val label = if (labels) (tile.value * 0.2f).coerceIn(15f, 20f).dp else 0.dp
    // Room above and below the icons for a highlighted icon to grow and glow.
    // The highlighted icon's name tag hangs below it ([FocusLabelReach]), so the last row needs room for it too.
    val pad = (tile * g * 0.6f).coerceAtLeast(if (focusLabels) FocusLabelReach + 4.dp else FocusRoom)
    if (mode == "vertical") {
        return GridMetrics(tile, tile * g, Int.MAX_VALUE, a, label, margin, pad)
    }
    if (rows > 0) {
        // Fixed rows: the icons grow to fill the height, but never so big that fewer than about
        // three and a half fit across (the bottom screen is narrow).
        val r = rows.coerceIn(1, 6)
        val basePad = if (focusLabels) FocusLabelReach + 4.dp else FocusRoom
        val lab = if (labels) 18.dp else 0.dp
        var t = (h - basePad * 2 - lab * r) / (r + g * (r - 1))
        val grown = t * g * 0.6f
        if (grown > basePad) t = (h - grown * 2 - lab * r) / (r + g * (r - 1))
        t = if (r == 1) {
            // One row, like the 3DS HOME menu zoomed all the way in: big icons, about three and a half across (two
            // and a half on the narrow bottom screen), leaving room above the row for the highlighted item's details.
            min(min(t, (h - basePad * 2) * SingleRowShare), (w - margin * 2) / (2.6f + 2.6f * g))
        } else {
            min(t, (w - margin * 2) / (3.4f + 3.4f * g))
        }.coerceAtLeast(24.dp)
        val gp = t * g
        val cols = floor(((w - margin * 2 + gp) / (t + gp)).toDouble()).toInt().coerceAtLeast(1)
        return GridMetrics(t, gp, r, cols, lab, margin, (t * g * 0.6f).coerceAtLeast(basePad))
    }
    // Never so big that not even one row fits.
    tile = min(tile, h - pad * 2 - label)
    val gap = tile * g
    val rows = floor(((h - pad * 2 + gap) / (tile + label + gap)).toDouble()).toInt().coerceAtLeast(1)
    return GridMetrics(tile, gap, rows, a, label, margin, pad)
}

// --- The home screen -------------------------------------------------------------------------------------------

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun IconGridHome(libs: List<PlatformLibrary>) {
    IconPacksDriver()
    androidx.compose.runtime.DisposableEffect(Unit) { IconGrid.composed++; onDispose { IconGrid.composed-- } }
    val ctx = LocalContext.current
    val look = Store.settings.look
    val route = Route.Home.key
    val catalog = rememberCatalog(libs)
    val moving = IconGrid.moving?.takeIf { it.folder == null }
    val present = remember(catalog) { catalog.present() }
    val presentSet = remember(present) { present.toHashSet() }
    // The slots' keys (empty spots included) and the tiles', filled in below once the grid knows its rows; read by
    // the button handlers.
    val view = remember { arrayOf(emptyList<String>(), emptyList()) }
    // A running game gets the Resume page left of the grid (not a tile); recent games get the strip on top.
    val running = if (moving == null && look.nowPlaying) resumable() else null
    ResumeLanding(running)
    val recent = rememberRecentGames()
    val recentKeys = if (HomeExtras.recentShown && moving == null) recent.map { HomeExtras.recentKey(it) } else emptyList()

    // Package changes, not every return: each check asks the package manager about every pinned app.
    val packages = io.github.zahakj.tandem.Emulators.changes
    val showDock = HomeStore.settings.showDock
    val dock = remember(HomeStore.settings.dock, packages, look.dockCount, showDock) {
        if (showDock) Dock.pinned(ctx).take(look.dockCount.coerceIn(1, 16)) else emptyList()
    }
    val dockKeys = if (showDock) listOf("app:*") + dock.map { "app:$it" } else emptyList()
    val resumeKeys = if (running != null) listOf(HomeExtras.RESUME) else emptyList()

    // Y: the tile menu, or Add here on an empty spot (or close the running game on the Resume page); X there switches
    // games; L1 / R1 zoom; L2 the Recently played strip. Y while moving a tile carries it instead.
    InterceptButtons(true) { b ->
        val free = Nav.route == Route.Home && !Nav.overlayOpen && Layers.top == null && FolderPopup.route == null
        val at = FocusMemory.get(route)
        val mv = IconGrid.moving
        when {
            // Select while moving: the tile goes back and is marked, and several can be picked (GridSelect.kt).
            mv != null && mv.folder == null && !mv.carried -> {
                when (b) {
                    Button.Y -> carryMove()
                    Button.Select -> GridSelect.fromMove()
                    // L1 / R1: a screen along, or back.
                    Button.L1, Button.R1 -> moveTilePage(if (b == Button.R1) 1 else -1, IconGrid.screenLines)
                    else -> Unit
                }
                true
            }
            !free -> false
            b == Button.Select && GridSelect.on(null) -> {
                at?.takeIf { it in view[1] }?.let { GridSelect.toggle(null, it) }
                true
            }
            at == HomeExtras.RESUME && running != null && b == Button.Y -> { resumePageClose(ctx, running); true }
            at == HomeExtras.RESUME && running != null && b == Button.X && RunningGames.list.size > 1 -> { Panels.openSwitcher(); true }
            b == Button.Y && Panels.focusedApp == null -> {
                val key = at?.takeIf { it in view[1] || isGapKey(it) && it in view[0] } ?: return@InterceptButtons false
                // Carrying a tile: only empty spots take it (A or Y there).
                if (mv == null || isGapKey(key)) IconGrid.openMenu(key, null)
                true
            }
            // Zooms on release, and only when no other button joined in (L1 + R1, L1 + R1 + Select): Chords.
            b == Button.L1 || b == Button.R1 -> { IconGrid.armZoom(if (b == Button.R1) 1 else -1); true }
            b == Button.L2 -> { HomeExtras.toggleRecent(); true }
            // R2: a page further along the grid, onto a tile (never an empty spot).
            b == Button.R2 -> {
                val vertical = look.gridScroll == "vertical"
                val lanes = IconGrid.home?.lanes ?: 1
                if (at in view[0]) jumpAlong(route, view[0], lanes, if (vertical) 3 else look.gridAcross)
                else (ctx as? MainActivity)?.pageJump(if (vertical) android.view.KeyEvent.KEYCODE_DPAD_DOWN else android.view.KeyEvent.KEYCODE_DPAD_RIGHT, if (vertical) 3 else look.gridAcross)
                true
            }
            else -> false
        }
    }
    // B while moving (or carrying a tile) puts the tile back; otherwise B on Home goes back to the first tile (never
    // leaves Tandem), past any empty spots.
    val anyMove = IconGrid.moving
    val homeSelect = IconGrid.selecting?.takeIf { it.folder == null }
    BackHandler(enabled = anyMove != null && (anyMove.folder == null || anyMove.carried)) { cancelMove() }
    // B always ends a move, whichever screen the grid is on: this one is added when the move starts, so it comes
    // before every handler registered earlier (with swapped screens one of those used to swallow B). A menu or the
    // keyboard over the grid keeps B for itself; with a folder open while carrying, B goes up a level there first.
    PriorityBack(
        anyMove != null && Nav.route == Route.Home && IconGrid.menu == null && TextEntry.request == null &&
            (IconGrid.openFolder == null || anyMove.folder != null && !anyMove.carried),
    ) { cancelMoveNow() }
    PriorityBack(
        homeSelect != null && anyMove == null && IconGrid.menu == null && IconGrid.openFolder == null && TextEntry.request == null,
    ) { GridSelect.clear() }
    // A move left alone for a minute goes back by itself, so it can never be stuck.
    LaunchedEffect(anyMove) {
        if (anyMove == null) return@LaunchedEffect
        while (IconGrid.moving === anyMove) {
            kotlinx.coroutines.delay(5_000)
            if (IconGrid.moving === anyMove && android.os.SystemClock.elapsedRealtime() - io.github.zahakj.tandem.Idle.last > 60_000) {
                cancelMoveNow()
                Toasts.show("Move cancelled: it went back to its spot")
            }
        }
    }
    // B while tiles are marked: the marks go (the tiles stay where they are).
    BackHandler(enabled = homeSelect != null && anyMove == null && Nav.route == Route.Home && !Nav.overlayOpen) { GridSelect.clear() }
    BackHandler(enabled = anyMove == null && homeSelect == null && Nav.route == Route.Home && !Nav.overlayOpen) {
        if (HomeExtras.recentOpen && FocusMemory.get(route)?.startsWith("recent:") == true) HomeExtras.toggleRecent()
        else view[1].firstOrNull()?.let { FocusMemory.remember(route, it) }
        Nav.focusTick++
    }

    // On the bottom screen (screens swapped) the grid is like the 3DS bottom screen: fewer, bigger, touch-friendly
    // icons in fixed rows, and the highlighted item's name and details in a header above them.
    val slot = LocalScreenSlot.current
    val onBottom = slot == ScreenSlot.Bottom
    val px = LocalDensity.current.density
    val flow = SwapFx.flow(slot)
    // Touch drag and drop (GridDrag.kt): this box follows the finger over the grid and its folder popup.
    Box(Modifier.fillMaxSize().gridDragHost()) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val split = if (onBottom || look.gridHeroSplit <= 0f) 0.dp else maxHeight * look.gridHeroSplit.coerceIn(0.15f, 0.55f)
            val mode = look.gridScroll
            val rows = if (onBottom) look.gridRows.takeIf { it > 0 }?.coerceAtMost(3) ?: 3 else look.gridRows
            // One row of big icons: the highlighted item's details move into the room above the row (RowDetails), so the
            // bottom screen's header steps aside for them.
            val single = rows == 1 && mode != "vertical"
            val headerH = if (onBottom && !single) 86.dp else 0.dp
            // Pip and the highlighted item's name get their own strip on top; the grid (and its focus room) starts below it.
            val captionH = if (onBottom) 0.dp else CaptionHeight
            val dockH = if (showDock) 74.dp else 44.dp
            // Shown always, the strip has its own room above the grid. Shown with L2 it slides over the top of the grid
            // instead, so opening it never changes the number of rows (which would move every tile to a new column).
            val stripAlways = ExtrasStore.settings.recentStrip == "always"
            val stripH = if (stripAlways && recent.isNotEmpty() && moving == null) 120.dp else 0.dp
            val areaH = maxHeight - split - dockH - stripH - headerH - captionH
            val focusLabels = false // the highlighted name now sits beside Pip, not under the tile
            // Remembered: a new GridMetrics object on every recomposition made the whole grid recompose on each move.
            val m = remember(maxWidth, areaH, mode, look.gridAcross, look.gridGap, look.gridLabels, rows) {
                gridMetrics(maxWidth, areaH, mode, look.gridAcross, look.gridGap, look.gridLabels, focusLabels, rows)
            }
            // A page shows whole columns only, centred: the next page starts just off the screen, never peeking in.
            val pageSide = if (mode == "paged") ((maxWidth - (m.tile * m.cols + m.gap * (m.cols - 1))) / 2).coerceAtLeast(m.margin) else m.margin
            androidx.compose.runtime.SideEffect { IconGrid.autoRows = m.rows; IconGrid.onBottom = onBottom }
            // Room left above the (vertically centred) single row, for the details.
            val detailsH = if (single && split == 0.dp) ((areaH - m.cell - m.pad * 2) / 2).coerceAtLeast(0.dp) else 0.dp
            SwapFx.tileCorner = with(LocalDensity.current) { (m.tile * look.gridRoundness.coerceIn(0f, 0.5f)).toPx() }
            val corner = m.tile * look.gridRoundness.coerceIn(0f, 0.5f)
            // The slots: a lane per row (sideways) or per column (up and down). Each tile sits in its saved slot, kept
            // through a change of rows when it still fits (GridSlots). While a tile moves, its live slots (read as a fresh
            // list, so everything below follows each step).
            val paged = mode == "paged"
            val vertical = mode == "vertical"
            val lanes = if (vertical) m.cols else m.rows.coerceAtLeast(1)
            val page = if (paged) m.cols else 0
            val config = IconGridStore.config
            // Widgets cover more than one cell: their sizes in slot terms (a moving widget may have a new size).
            val spans = config.spans
            val mvSpan = moving?.span
            val ext = remember(spans, presentSet, vertical, moving?.key, mvSpan) {
                val all = spans.filterKeys { it in presentSet }.let { if (moving != null && mvSpan != null) it + (moving.key to mvSpan) else it }
                all.mapValues { GridSlots.extent(it.value, vertical) }
            }
            val stored = remember(catalog, present, lanes, ext, page) { GridSlots.shown(config.order, config.lanes, lanes, present, ext, page) }
            val slots = moving?.order?.toList() ?: stored
            // A free canvas past the last tile: two screens of empty columns (rows up and down), whole pages when paged.
            // Only what is placed is saved; the empty end is never kept.
            val screenLines = if (vertical) ((areaH - m.pad * 2 + m.gap) / (m.cell + m.gap)).toInt().coerceAtLeast(1) else m.cols
            androidx.compose.runtime.SideEffect { IconGrid.screenLines = screenLines }
            val count = GridSlots.shownCount(slots, lanes, minEmpty = screenLines * 2, atLeast = if (vertical) 0 else m.cols, page = page, ext = ext)
            val owners = remember(slots, lanes, ext, page) { GridSlots.owners(slots, lanes, ext, page) }
            val cells = remember(slots, count, catalog, presentSet) {
                List(count) { i -> slots.getOrNull(i)?.takeIf { it in presentSet }?.let { catalog.item(it) } }
            }
            // A cell another item covers (a widget's other cells) shows nothing and takes no focus.
            val keys = remember(cells, owners) {
                cells.mapIndexed { i, it -> it?.key ?: if (owners.getOrElse(i) { -1 }.let { o -> o >= 0 && o != i && cells.getOrNull(o) != null }) coveredKey(i) else gapKey(i) }
            }
            val focusKeys = remember(keys) { keys.filter { !isCoveredKey(it) } }
            val items = remember(cells) { cells.filterNotNull() }
            val gridKeys = remember(items) { items.map { it.key } }
            view[0] = keys
            view[1] = gridKeys
            IconGrid.home = remember(slots, presentSet, lanes, ext, page) { GridShown(GridSlots.trim(slots), presentSet, lanes, ext, page) }
            // The tile menu is composed in the app root (IconGridMenuHost), above the blurred screen.
            androidx.compose.runtime.SideEffect { IconGrid.menuData = catalog to gridKeys }
            val target = rememberFocusTarget(route, recentKeys + resumeKeys + focusKeys + dockKeys)
            // Each widget's size on screen, by key, for drawing it.
            val widgetSpans = remember(spans, mvSpan, moving?.key) { if (moving != null && mvSpan != null) spans + (moving.key to mvSpan) else spans }
            val stripOver = !stripAlways && recentKeys.isNotEmpty()
            // While the strip is over the grid, the grid fades back (it isn't covered by a dark band).
            val gridAlpha by animateFloatAsState(if (stripOver) 0.35f else 1f, Motion.soft(), label = "grid-dim")
            // Moving down from the strip into the grid (or to the dock) puts the strip away again.
            val closeStrip: () -> Unit = { if (stripOver) HomeExtras.recentOpen = false }
            Column(Modifier.fillMaxSize()) {
                if (captionH > 0.dp) Spacer(Modifier.height(captionH))
                if (onBottom) BottomGridHeader(Modifier.fillMaxWidth().height(headerH).swapArrive(slot, 180f, 0f, -flow * 24f * px))
                if (split > 0.dp) GridHeroBanner(items, Modifier.fillMaxWidth().height(split))
                if (stripAlways) RecentStrip(route, target, if (moving == null) recent else emptyList())
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    Box(Modifier.fillMaxSize().padding(bottom = dockH).graphicsLayer { alpha = gridAlpha * (if (GridDrag.drag != null) 0.9f else 1f) }, contentAlignment = Alignment.Center) {
                        if (items.isEmpty() && running == null) {
                            EmptyGrid(libs.isNotEmpty())
                        } else {
                            TileGrid(
                                cells, keys, owners, widgetSpans, route, target, m, corner, mode, look.gridLabels, moving, running, pageSide,
                                onFocus = { item -> closeStrip(); Panels.focusedApp = null; Selection.follow { IconGrid.spot = item; Selection.current = item.selected } },
                                onResumeFocus = closeStrip,
                            )
                        }
                        if (detailsH > 28.dp && items.isNotEmpty()) RowDetails(
                            items, onBottom,
                            Modifier.align(Alignment.TopCenter).fillMaxWidth().height(detailsH).swapArrive(slot, 160f, 0f, -flow * 20f * px),
                        )
                    }
                    if (!stripAlways) RecentStrip(route, target, if (moving == null) recent else emptyList(), overlay = true, modifier = Modifier.align(Alignment.TopCenter))
                }
            }
            if (showDock) PillDock(dock, target, Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp).swapArrive(slot, 300f, 0f, 64f * px, 0.92f, 200f, damping = 0.7f).onPlaced { SwapFx.dock = it }.onFocusChanged { if (it.hasFocus) closeStrip() })
            GridCornerHints(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(dockH).swapArrive(slot, 340f, 0f, 20f * px))
            if (!onBottom) GridLogo(
                moving == null && !single,
                Modifier.align(Alignment.TopStart).fillMaxWidth().height(captionH).padding(start = 12.dp, end = 18.dp).swapArrive(slot, 220f, 0f, -flow * 16f * px),
            )
            // In the caption strip (its name is hidden while moving), or over the bottom screen's header.
            val hint = IconGrid.moving?.takeIf { it.folder == null || it.carried }
            if (hint != null) MoveHint(hint.carried, GridWidgets.idOf(hint.key) != null, Modifier.align(Alignment.TopCenter).padding(top = if (onBottom) 10.dp else 1.dp))
            else SelectPill(null, Modifier.align(Alignment.TopCenter).padding(top = if (onBottom) 10.dp else 1.dp))
        }

        HomeFolderPanel(catalog)
        AddToHomePanel(catalog, presentSet)
        GamePickPanel()
        // The tile being dragged, over everything here (the folder popup too).
        DragGhost()
    }
}

/**
 * Pip in the corner with the highlighted item's name beside it: Tandem's logo on the icon grid. The name lives here,
 * in one fixed spot that cross-fades as you move, instead of on a tag under the tile where it crowded the grid.
 */
@Composable
private fun GridLogo(nameAllowed: Boolean, modifier: Modifier) {
    val hour = remember { java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) }
    val showName = io.github.zahakj.tandem.data.ExtrasStore.settings.focusLabels
    val name = when (val s = io.github.zahakj.tandem.Selection.current) {
        is io.github.zahakj.tandem.Selected.GameItem -> s.game.title
        is io.github.zahakj.tandem.Selected.System -> s.library.platform.name
        is io.github.zahakj.tandem.Selected.Group -> s.title
        else -> ""
    }
    // Pip and the name step aside whenever anything opens over the grid (a folder, a menu, a panel, search), so
    // neither peeks out above a popup's edge; the name also while a tile is being moved (the move hints use the strip).
    val popup = Nav.route != Route.Home || Nav.overlayOpen || Layers.top != null || IconGrid.menu != null ||
        IconGrid.openFolder != null || IconGrid.adding || FolderPopup.route != null || StartMenu.open
    val covered = popup || !nameAllowed || IconGrid.selecting != null
    val logoAlpha by animateFloatAsState(if (popup) 0f else 1f, Motion.fade(if (popup) 90 else 220), label = "grid-logo-alpha")
    val nameAlpha by animateFloatAsState(if (covered) 0f else 1f, Motion.fade(if (covered) 90 else 220), label = "grid-name-alpha")
    Row(modifier.graphicsLayer { alpha = logoAlpha }, verticalAlignment = Alignment.CenterVertically) {
        Pip(30.dp, mood = if (hour in 0..5) 2 else 0)
        if (showName) {
            AnimatedContent(
                name, Modifier.weight(1f).graphicsLayer { alpha = nameAlpha },
                transitionSpec = { (fadeIn(Motion.fade(200)) + slideInVertically { it / 3 }) togetherWith fadeOut(Motion.fade(120)) },
                contentAlignment = Alignment.CenterStart,
                label = "grid-name",
            ) { n ->
                if (n.isNotEmpty()) Text(
                    n, Modifier.padding(start = 10.dp), style = MaterialTheme.typography.titleMedium.onArt(),
                    color = Palette.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false,
                )
            }
        }
    }
}

/** The strip above the icon grid for Pip and the highlighted item's name. */
private val CaptionHeight = 36.dp

/**
 * The grid of icons, scrolling sideways, by page, or up and down; the Resume page leads it while a game runs. [cells]
 * are its slots (null: an empty spot), [keys] their keys.
 */
@Composable
private fun TileGrid(
    cells: List<GridItem?>, keys: List<String>, owners: IntArray, spans: Map<String, io.github.zahakj.tandem.data.GridSpan>,
    route: String, target: String?, m: GridMetrics, corner: Dp, mode: String, labels: Boolean,
    moving: MovingTile?, running: io.github.zahakj.tandem.RunningGame?, pageSide: Dp, onFocus: (GridItem) -> Unit, onResumeFocus: () -> Unit = {},
) {
    val state = rememberRouteGridState("home:icons:$mode")
    val vertical = mode == "vertical"
    val paged = mode == "paged"
    // Lazy indices of tiles are shifted by one while the Resume page sits first.
    val off = if (running != null) 1 else 0
    val targetIndex = keys.indexOf(target)
    val movingIndex = moving?.takeIf { !it.carried }?.let { mv -> keys.indexOf(mv.key) } ?: -1
    val keep = when {
        movingIndex >= 0 -> movingIndex + off
        target == HomeExtras.RESUME && running != null -> 0
        targetIndex >= 0 -> targetIndex + off
        else -> -1
    }
    // Pages keep themselves in view (below): a scroll from here would leave the grid between two pages.
    KeepTileInView(state, if (paged) -1 else keep, vertical, follow = movingIndex >= 0)
    val zoom = rememberTileZoom(m.tile)
    KeepZoomAnchor(state, m, keep, enabled = !vertical && !paged)
    // Every tile's picture is decoded ahead into the image memory cache, at the size tiles draw it, nearest the
    // highlighted tile first. Columns scrolling in then show their art in their first frame (a memory hit draws at
    // once, with no crossfade) instead of popping in one by one.
    val preCtx = LocalContext.current
    val prePx = tilePx(m.tile)
    LaunchedEffect(cells, prePx) {
        val loader = coil.Coil.imageLoader(preCtx)
        val from = targetIndex.coerceAtLeast(0)
        cells.indices.sortedBy { kotlin.math.abs(it - from) }.asSequence()
            .mapNotNull { (cells[it]?.art as? TileArt.Picture)?.file }.distinct().take(240)
            .forEach { f -> loader.enqueue(sizedRequest(preCtx, f, prePx)) }
    }
    // Pages, like the 3DS HOME menu: when focus crosses to the next page, the whole strip slides one page along
    // (one emphasized 220 ms tween, a plain scroll: every tile stays drawn, nothing fades). The distance is worked out
    // from tiles that are laid out now, instead of animateScrollToItem towards a tile that isn't laid out yet (which
    // estimates the way and can jump there).
    var focusedIndex by remember { mutableStateOf(targetIndex) }
    if (paged) {
        val perPage = (m.rows * m.cols).coerceAtLeast(1)
        val at = if (movingIndex >= 0) movingIndex else focusedIndex
        val page = when {
            at >= 0 -> at / perPage
            at == -2 -> -1
            else -> 0
        }
        val opened = remember { booleanArrayOf(true) }
        LaunchedEffect(page, perPage, off, Nav.focusTick) {
            snapshotFlow { state.layoutInfo.totalItemsCount }.first { it > 0 }
            // The first time (opening Home) it lands at once; afterwards it slides.
            val instant = opened[0] || Motion.style == AnimationStyle.Off
            opened[0] = false
            runCatching {
                if (page < 0) { if (instant) state.scrollToItem(0) else state.animateScrollToItem(0) }
                else state.slideToColumn(page * m.cols, off, m.rows, instant)
            }
        }
    }
    // Arriving with a swap (SwapFx.kt): each tile flies in from a scattered place against the flow and settles on a
    // spring, staggered by its distance from the highlighted tile. Worked out here once; the layer only reads a clock.
    val slot = LocalScreenSlot.current
    val flow = SwapFx.flow(slot)
    val px = LocalDensity.current.density
    val fi = targetIndex.coerceAtLeast(0)
    fun cell(i: Int): Pair<Int, Int> = if (vertical) (i % m.cols) to (i / m.cols) else (i / m.rows.coerceAtLeast(1)) to (i % m.rows.coerceAtLeast(1))
    val (fc, fr) = cell(fi)
    val lanes = if (vertical) m.cols else m.rows.coerceAtLeast(1)
    val cellH = if (labels) m.cell else m.tile
    val labelRoom = if (labels) m.label else 0.dp
    // A widget taller than the grid (two rows high with one row of icons) is drawn as tall as the grid, at the size
    // that fits, instead of running past the row and being cut.
    fun fitSpan(span: io.github.zahakj.tandem.data.GridSpan) =
        if (vertical) span.copy(w = span.w.coerceAtMost(lanes)) else span.copy(h = span.h.coerceAtMost(lanes))
    // Widgets whose first cell is composed now. When it scrolls out at the start edge while the widget's second
    // column (or row) still shows, that cell draws the widget instead (GridWidgetStandIn), so it never half vanishes.
    val anchorsOn = remember { androidx.compose.runtime.mutableStateMapOf<String, Boolean>() }
    val tileContent: @Composable (Int, GridItem?) -> Unit = { i, item ->
        val (c, r) = cell(i)
        val dc = (c - fc).toFloat()
        val dr = (r - fr).toFloat()
        val dist = kotlin.math.hypot(dc, dr)
        val reach = (24f + 10f * minOf(dist, 5f)) * px
        val arrive = if (dist < 0.01f) {
            Modifier.swapArrive(slot, 20f, 0f, -flow * 34f * px, 0.62f, 120f)
        } else {
            Modifier.swapArrive(slot, 50f + minOf(dist, 7f) * 32f, dc / dist * reach, dr / dist * reach - flow * 84f * px, 0.72f, 150f)
        }
        val key = keys[i]
        if (item == null && isCoveredKey(key)) {
            // Under a widget: room only, unless the widget's first cell has scrolled away (see anchorsOn).
            val o = owners.getOrElse(i) { -1 }
            val anchor = cells.getOrNull(o)?.takeIf { it.kind == TileKind.Widget && i == o + lanes }
            val w = anchor?.widgetId?.let { Widgets.byId(it) }
            if (anchor != null && w != null && anchorsOn[anchor.key] != true) {
                val span = spans[anchor.key] ?: io.github.zahakj.tandem.data.GridSpan.ONE
                GridWidgetStandIn(
                    w, fitSpan(span), m.tile, cellH, m.gap, labelRoom,
                    dx = if (vertical) 0.dp else m.tile + m.gap, dy = if (vertical) cellH + m.gap else 0.dp,
                )
            } else CoveredSlot(m.tile, cellH)
        } else if (item == null) {
            // An empty spot: A opens Add here (with Paste while a tile is carried).
            val dropHere by remember(key) { androidx.compose.runtime.derivedStateOf { GridDrag.hover(null, key) > 0 } }
            EmptySlot(
                m.tile, corner, labelRoom,
                arrive.graphicsLayer { val s = zoom.scale; scaleX = s; scaleY = s }.takesFocus(route, key, key == target),
                onFocus = { focusedIndex = i; onFocus(EmptySpotItem) },
                onClick = { IconGrid.openMenu(key, null) },
                hovered = dropHere,
            )
        } else if (item.kind == TileKind.Widget) {
            val w = item.widgetId?.let { Widgets.byId(it) }
            androidx.compose.runtime.DisposableEffect(item.key) {
                anchorsOn[item.key] = true
                onDispose { anchorsOn.remove(item.key) }
            }
            val span = spans[item.key] ?: io.github.zahakj.tandem.data.GridSpan.ONE
            val lifted by remember(item.key) { androidx.compose.runtime.derivedStateOf { GridDrag.lifted(item.key) } }
            if (w != null) GridWidgetCell(
                w, fitSpan(span), m.tile, cellH, m.gap, labelRoom,
                arrive.graphicsLayer { val s = zoom.scale; scaleX = s; scaleY = s }.onPlaced { SwapFx.tiles[item.key] = it }.takesFocus(route, item.key, item.key == target),
                moving = moving?.key == item.key,
                onFocus = { focusedIndex = i; onFocus(item) },
                // Held and moved by touch, it is dragged whole (it keeps its own taps and long press).
                drag = Modifier.gridTouch(item.key, null, item, span, watchOnly = true).graphicsLayer { alpha = if (lifted) 0.28f else 1f },
            ) else CoveredSlot(m.tile, cellH)
        } else {
            // Only the tiles whose mark changes recompose.
            val marked by remember(item.key) { androidx.compose.runtime.derivedStateOf { GridSelect.marked(null, item.key) } }
            IconTile(
                item, m.tile, corner, labels,
                arrive.graphicsLayer { val s = zoom.scale; scaleX = s; scaleY = s }.onPlaced { SwapFx.tiles[item.key] = it }.takesFocus(route, item.key, item.key == target),
                moving = moving?.key == item.key,
                marked = marked,
                onFocus = { focusedIndex = i; onFocus(item) },
                onClick = {
                    when {
                        GridSelect.on(null) -> GridSelect.toggle(null, item.key)
                        canOpen(item) -> { IconGrid.openedFrom = item.key; item.open() }
                    }
                },
                onLongClick = { if (IconGrid.moving == null) GridSelect.toggle(null, item.key) },
                dragGrid = "".takeIf { item.kind != TileKind.Now },
            )
        }
    }
    // This grid takes drops (GridDrag.kt): the finger is mapped to its slots through the lazy grid's layout.
    val zone = rememberDropZone(null, state)
    val density = LocalDensity.current
    androidx.compose.runtime.SideEffect {
        zone.off = off
        zone.vertical = vertical
        zone.lanes = lanes
        zone.keys = keys
        zone.owners = owners
        zone.tilePx = with(density) { m.tile.toPx() }
        zone.gapPx = with(density) { m.gap.toPx() }
        zone.turnPage = if (!paged) null else { dir ->
            val perPage = (m.rows * m.cols).coerceAtLeast(1)
            val pageNow = focusedIndex.coerceAtLeast(0) / perPage
            val last = (keys.size - 1).coerceAtLeast(0) / perPage
            focusedIndex = ((pageNow + dir).coerceIn(0, last)) * perPage
        }
    }
    val dropArea = Modifier.onGloballyPositioned { zone.coords = it }
    // Rows and columns hold under a fast or held D-pad: focus moves slot by slot (SlotFocus).
    val nav = remember { SlotFocus() }
    val moveKeys = Modifier.onPreviewKeyEvent { e -> moving != null && handleMoveKey(e, lanes, vertical, if (paged) m.cols else 0) || slotKey(e, nav, route, keys, owners, lanes, vertical) }
    val place = tilePlacement()
    val scrolling: @Composable (@Composable () -> Unit) -> Unit = { content ->
        if (paged) NoAutoScrolling(content) else PaddedScrolling(m.margin + m.tile * 0.25f, content)
    }
    val resumeFocus = Modifier.onFocusChanged { if (it.isFocused) { focusedIndex = -2; onResumeFocus() } }
    scrolling {
        if (vertical) {
            LazyVerticalGrid(
                GridCells.Fixed(m.cols),
                Modifier.fillMaxSize().then(moveKeys).then(dropArea).fadingEdges(state),
                state = state,
                contentPadding = PaddingValues(start = m.margin, end = m.margin, top = m.pad, bottom = m.pad + 8.dp),
                horizontalArrangement = Arrangement.spacedBy(m.gap),
                verticalArrangement = Arrangement.spacedBy(m.gap),
            ) {
                if (running != null) item(HomeExtras.RESUME, span = { GridItemSpan(maxLineSpan) }) {
                    ResumePage(running, route, target, Modifier.fillMaxWidth().height(m.tile * 1.9f).then(resumeFocus))
                }
                itemsIndexed(cells, key = { i, _ -> keys[i] }, contentType = { i, it -> cellType(keys[i], it) }) { i, item ->
                    // Only the tiles gaining or losing focus recompose for their z order, not every visible tile.
                    val front by remember(i) { androidx.compose.runtime.derivedStateOf { i == focusedIndex } }
                    Box(Modifier.animateItem(placementSpec = place).zIndex(if (front || keys[i] == moving?.key) 1f else 0f).slotFocus(nav, keys[i]), contentAlignment = Alignment.Center) { tileContent(i, item) }
                }
                if (Widgets.homeVisible()) item("widgets", span = { GridItemSpan(maxLineSpan) }) { HomeWidgetsRow(inset = 0.dp) }
            }
        } else {
            val height = m.cell * m.rows + m.gap * (m.rows - 1) + m.pad * 2
            // Paged: room after the last tiles for the rest of the last page, so every page (the last too) can sit
            // in exactly the same place.
            val perPage = (m.rows * m.cols).coerceAtLeast(1)
            val usedCols = ceil(cells.size / m.rows.coerceAtLeast(1).toFloat()).toInt()
            val pageCols = ceil(cells.size / perPage.toFloat()).toInt().coerceAtLeast(1) * m.cols
            val endPad = if (paged) pageSide + (m.tile + m.gap) * (pageCols - usedCols).coerceAtLeast(0) else m.margin + m.tile * 0.6f
            Box(Modifier.fillMaxWidth().height(height)) {
                LazyHorizontalGrid(
                    GridCells.Fixed(m.rows),
                    // Pages need no soft edges: nothing sits at an edge between slides.
                    Modifier.fillMaxSize().then(moveKeys).then(dropArea).then(if (paged) Modifier else Modifier.fadingEdges(state, horizontal = true)),
                    state = state,
                    contentPadding = PaddingValues(start = if (paged) pageSide else m.margin, end = endPad, top = m.pad, bottom = m.pad),
                    horizontalArrangement = Arrangement.spacedBy(m.gap),
                    verticalArrangement = Arrangement.spacedBy(m.gap),
                ) {
                    if (running != null) item(HomeExtras.RESUME, span = { GridItemSpan(maxLineSpan) }) {
                        val wide = (m.tile + m.gap) * minOf(4, m.cols - 1).coerceAtLeast(2) - m.gap
                        ResumePage(running, route, target, Modifier.width(wide).fillMaxHeight().then(resumeFocus))
                    }
                    itemsIndexed(cells, key = { i, _ -> keys[i] }, contentType = { i, it -> cellType(keys[i], it) }) { i, item ->
                        // Only the tiles gaining or losing focus recompose for their z order, not every visible tile.
                        val front by remember(i) { androidx.compose.runtime.derivedStateOf { i == focusedIndex } }
                        Box(Modifier.animateItem(placementSpec = place).zIndex(if (front || keys[i] == moving?.key) 1f else 0f).slotFocus(nav, keys[i]), contentAlignment = Alignment.Center) { tileContent(i, item) }
                    }
                }
            }
            if (paged) {
                val perPage = m.rows * m.cols
                val pages = ceil(cells.size / perPage.toFloat()).toInt()
                if (pages > 1) PageDots(pages, (focusedIndex.coerceAtLeast(0)) / perPage)
            }
        }
    }
}

/**
 * Tiles glide into a new slot on a spring (moving one or many, a widget making room, Tidy up, a sort, Undo),
 * following the animation style.
 */
private fun tilePlacement(): androidx.compose.animation.core.FiniteAnimationSpec<androidx.compose.ui.unit.IntOffset> = when (Motion.style) {
    AnimationStyle.Off -> androidx.compose.animation.core.snap()
    AnimationStyle.Minimal -> tween(160)
    else -> androidx.compose.animation.core.spring(0.8f, androidx.compose.animation.core.Spring.StiffnessMediumLow, androidx.compose.ui.unit.IntOffset(1, 1))
}

/**
 * L1 / R1 zoom: the grid lays out at the new size at once and each tile grows or shrinks from its old size into it
 * (one clock, read only in the tiles' graphics layers, so nothing recomposes or re-measures while it plays).
 */
private class TileZoom(var tile: Dp) {
    var from by androidx.compose.runtime.mutableFloatStateOf(1f)
    /** Set in composition until the animation picks it up, so the first frame at the new size still shows the old one. */
    var armed by mutableStateOf(false)
    val anim = androidx.compose.animation.core.Animatable(1f)
    val scale: Float get() = if (armed) from else anim.value
}

@Composable
private fun rememberTileZoom(tile: Dp): TileZoom {
    val z = remember { TileZoom(tile) }
    val still = Motion.style == AnimationStyle.Off || Store.settings.reduceMotion
    if (z.tile != tile) {
        val visual = androidx.compose.runtime.snapshots.Snapshot.withoutReadObservation { z.scale } * (z.tile / tile)
        z.tile = tile
        if (!still && visual in 0.25f..4f && kotlin.math.abs(visual - 1f) > 0.01f) { z.from = visual; z.armed = true }
    }
    LaunchedEffect(tile) {
        if (!z.armed) return@LaunchedEffect
        z.anim.snapTo(z.from)
        z.armed = false
        z.anim.animateTo(1f, Motion.move())
    }
    return z
}

/**
 * When L1 / R1 changes the number of rows, the highlighted tile keeps its place on screen: the grid is told, before it
 * lays out the new rows, to put that tile's column where the tile's centre was. Nothing jumps; the other tiles glide
 * to their new places (animateItem) and every tile eases to its new size ([TileZoom]).
 */
@Composable
private fun KeepZoomAnchor(state: LazyGridState, m: GridMetrics, index: Int, enabled: Boolean) {
    val last = remember { intArrayOf(m.rows) }
    val rows = m.rows
    val tilePx = with(LocalDensity.current) { m.tile.roundToPx() }
    // After the change is applied and before the grid measures it, while its layout info still describes the old rows.
    androidx.compose.runtime.SideEffect {
        if (last[0] == rows) return@SideEffect
        last[0] = rows
        if (!enabled || index < 0) return@SideEffect
        val info = state.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index } ?: return@SideEffect
        // Same centre as before; near the start of the list the grid clamps it, so the first column never drifts right.
        val start = info.offset.x + (info.size.width - tilePx) / 2
        state.requestScrollToItem(index, -start)
    }
}

/**
 * Scrolls a sideways grid so tile column [col] starts the viewport: at once ([instant]) or in one emphasized slide.
 * Worked out from a tile that is laid out now (columns are all the same width), so it never jumps.
 */
private suspend fun LazyGridState.slideToColumn(col: Int, off: Int, rows: Int, instant: Boolean) {
    val info = layoutInfo
    val ref = info.visibleItemsInfo.firstOrNull { it.index >= off }
    if (ref == null) { scrollToItem(col * rows.coerceAtLeast(1) + off); return }
    val stride = ref.size.width + info.mainAxisItemSpacing
    val refCol = (ref.index - off) / rows.coerceAtLeast(1)
    val delta = (ref.offset.x + (col - refCol) * stride).toFloat()
    if (kotlin.math.abs(delta) < 1f) return
    if (instant) scrollBy(delta)
    else animateScrollBy(delta, tween(Motion.screenMs.coerceAtLeast(1), easing = Motion.EmphasizedDecelerate))
}

/** Keeps the target tile on screen when the grid opens or focus is handed back (sideways grids check x, not y). */
@Composable
private fun KeepTileInView(state: LazyGridState, index: Int, vertical: Boolean, follow: Boolean = false) {
    val roomPx = with(LocalDensity.current) { KeepRoom.toPx() }
    val latest by androidx.compose.runtime.rememberUpdatedState(index)
    // Only when the grid opens, when focus is handed back (focusTick) or while a tile is being moved ([follow]).
    // Not on every D-pad move: the focused tile is already being scrolled into view smoothly by focus itself, and
    // jumping here as well snapped the whole grid by several columns at once (the "blink" when scrolling sideways).
    LaunchedEffect(state, Nav.focusTick, if (follow) index else Unit) {
        val index = latest
        if (index < 0) return@LaunchedEffect
        snapshotFlow { state.layoutInfo.totalItemsCount }.first { it > index }
        val info = state.layoutInfo
        val it = info.visibleItemsInfo.firstOrNull { v -> v.index == index }
        val start = if (it == null) 0 else if (vertical) it.offset.y else it.offset.x
        val size = if (it == null) 0 else if (vertical) it.size.height else it.size.width
        runCatching {
            when {
                // Off screen: bring it in, clear of the faded edge.
                it == null -> { state.scrollToItem(index); if (index > 0) state.scrollBy(-roomPx) }
                // Partly hidden: move just enough to show it whole, not a page.
                start < info.viewportStartOffset -> state.scrollBy(start - info.viewportStartOffset - roomPx)
                start + size > info.viewportEndOffset -> state.scrollBy(start + size - info.viewportEndOffset + roomPx)
            }
        }
    }
}

/** Lazy content types: tiles, empty spots, cells under a widget and widgets compose differently. */
private fun cellType(key: String, item: GridItem?): Int = when {
    item == null && isCoveredKey(key) -> 2
    item == null -> 1
    item.kind == TileKind.Widget -> 3
    else -> 0
}

@Composable
private fun PageDots(pages: Int, current: Int) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Row(Modifier.padding(bottom = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (p in 0 until pages) {
                val on = p == current
                Box(Modifier.size(if (on) 8.dp else 6.dp).clip(CircleShape).background(if (on) Palette.Text else Palette.Muted.copy(alpha = 0.5f)))
            }
        }
    }
}

/** The highlighted tile's art above the grid, when the hero split is on. */
@Composable
private fun GridHeroBanner(items: List<GridItem>, modifier: Modifier) {
    // Read here, not by the caller, so a move recomposes only this banner.
    val item = IconGrid.spot?.takeIf { s -> s.kind == TileKind.Empty || items.any { it.key == s.key } } ?: items.firstOrNull() ?: return
    AnimatedContent(item, modifier, contentKey = { it.key }, transitionSpec = { fadeIn(Motion.fade(320)) togetherWith fadeOut(Motion.fade(220)) }, label = "grid-hero") { it ->
        val key = it.key
        val hero = remember(key) { heroFor(it) }
        Box(Modifier.fillMaxSize()) {
            if (hero != null) {
                Box(Modifier.fillMaxSize().fadeMask(0f to 1f, 0.6f to 0.9f, 1f to 0f).fadeMaskHorizontal(0f to 0.4f, 0.55f to 1f)) {
                    KenBurns(hero, Modifier.fillMaxSize(), moving = Store.settings.look.kenBurns)
                }
            } else {
                Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(it.color.copy(alpha = 0.5f), Color.Transparent))))
            }
            Column(Modifier.align(Alignment.BottomStart).padding(start = 30.dp, bottom = 6.dp, end = 30.dp).riseIn(key, 0, 14.dp).floating(key.hashCode(), bob = 3.dp, tilt = 0f)) {
                Text(it.type.uppercase(), style = MaterialTheme.typography.labelLarge, color = lerp(it.color, Color.White, 0.35f), maxLines = 1)
                Text(it.title, style = MaterialTheme.typography.displaySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/**
 * One row of big icons (the 3DS HOME menu zoomed all the way in): the highlighted item's details fill the room above
 * the row: its kind, name, count or play time, and a small strip of its hero art fading in at the side. Each new item
 * rises in (Flair); everything steps aside while a popup, menu or move is on, like the name beside Pip does.
 */
@Composable
private fun RowDetails(items: List<GridItem>, pip: Boolean, modifier: Modifier) {
    // Read here, not by the caller, so a move recomposes only the details.
    val item = IconGrid.spot?.takeIf { s -> s.kind == TileKind.Empty || items.any { it.key == s.key } } ?: items.firstOrNull() ?: return
    val covered = Nav.route != Route.Home || Nav.overlayOpen || Layers.top != null || IconGrid.menu != null ||
        IconGrid.openFolder != null || IconGrid.adding || IconGrid.moving != null || FolderPopup.route != null || StartMenu.open
    val shown by animateFloatAsState(if (covered) 0f else 1f, Motion.fade(if (covered) 90 else 220), label = "row-details-alpha")
    val kenBurns = Store.settings.look.kenBurns
    BoxWithConstraints(modifier.graphicsLayer { alpha = shown }) {
        // A kind label on top when there is room for three lines; a larger name when there is room for two.
        val tall = maxHeight >= 84.dp
        val big = maxHeight >= 60.dp
        // The hero strip only where there is width to spare beside the name (not on the narrow bottom screen).
        val wide = maxWidth > 560.dp
        AnimatedContent(
            item, Modifier.fillMaxSize(), contentKey = { it.key },
            transitionSpec = { fadeIn(Motion.fade(260)) togetherWith fadeOut(Motion.fade(140)) },
            label = "row-details",
        ) { it ->
            val key = it.key
            val hero = remember(key) { heroFor(it) }
            val line = remember(key) {
                val played = it.games.sumOf { g -> g.meta.playSeconds }
                listOfNotNull(
                    it.detail.takeIf { d -> d.isNotBlank() && d != it.type },
                    if (played >= 60) "Played ${formatPlayLong(played)}" else null,
                    it.blurb.takeIf { b -> b.isNotBlank() },
                ).joinToString(" · ")
            }
            Box(Modifier.fillMaxSize()) {
                // The hero strip: the item's wide art, faded into the wallpaper on every side (no band, no frame).
                if (hero != null && wide) Box(
                    Modifier.align(Alignment.CenterEnd).fillMaxHeight().fillMaxWidth(0.42f)
                        .fadeMask(0f to 0f, 0.3f to 1f, 0.7f to 1f, 1f to 0f).fadeMaskHorizontal(0f to 0f, 0.45f to 0.85f, 1f to 0.35f),
                ) { KenBurns(hero, Modifier.fillMaxSize(), moving = kenBurns) }
                Row(
                    Modifier.align(Alignment.CenterStart).fillMaxWidth(if (hero != null && wide) 0.7f else 1f)
                        .padding(horizontal = 30.dp).riseIn(key, 0, 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (pip) { Pip(if (big) 40.dp else 30.dp); Spacer(Modifier.width(12.dp)) }
                    Column(Modifier.weight(1f)) {
                        if (tall) Text(
                            it.type.uppercase(), style = MaterialTheme.typography.labelLarge.onArt(), color = lerp(it.color, Palette.Text, 0.35f),
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            it.title, style = (if (big) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleLarge).onArt(),
                            color = Palette.Text, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        val sub = if (tall) line else listOf(it.type, line).filter { s -> s.isNotBlank() }.joinToString(" · ")
                        if (sub.isNotEmpty()) Text(
                            sub, style = MaterialTheme.typography.labelLarge.onArt(),
                            color = if (tall) Palette.Muted else lerp(it.color, Palette.Text, 0.45f),
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/** The best wide picture for a tile: the pack's hero, a game's own hero, or one from the games inside. */
fun heroFor(item: GridItem): File? {
    if (FolderLooks.decidesHero(item)) return FolderLooks.hero(item)
    item.platformId?.let { id -> IconPacks.systemHero(id)?.let { return it } }
    item.collectionId?.let { id -> IconPacks.collectionHero(id)?.let { return it } }
    IconPacks.folderHero(item.packNames)?.let { return it }
    val ordered = item.games.sortedByDescending { it.meta.lastPlayed }
    for (g in ordered.take(40)) {
        val lib = Libraries.of(g) ?: continue
        (lib.hero(g) ?: lib.screenshot(g))?.let { return it }
    }
    return null
}

/** No tiles at all: say what to do. */
@Composable
private fun EmptyGrid(allHidden: Boolean) {
    val ctx = LocalContext.current
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
        Pip(84.dp, mood = 2)
        Spacer(Modifier.height(8.dp))
        Text(if (allHidden) "Your home grid is empty" else "No games yet", style = MaterialTheme.typography.titleLarge)
        Text(
            if (allHidden) "Press Y and pick Add to home, or turn on Show hidden games in Settings."
            else "Put games in ROMs/<system>, like ROMs/snes, then rescan.",
            style = MaterialTheme.typography.bodyMedium, color = Palette.Muted, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        PillButton(if (LibraryScan.scanning) "Looking…" else "Rescan", Palette.Mint, Modifier.takesFocus(Route.Home.key, "empty:rescan", true)) {
            LibraryScan.rescan(ctx)
        }
    }
}

// --- One icon --------------------------------------------------------------------------------------------------

/**
 * A square icon. Highlighted, it grows a little, lifts with a glow in its own colour and gets a light outline a
 * few dp outside its edge (all drawn in layers, so focus changes never recompose the art). Being moved, it wobbles.
 */
@Composable
fun IconTile(
    item: GridItem,
    tile: Dp,
    corner: Dp,
    label: Boolean,
    modifier: Modifier = Modifier,
    moving: Boolean = false,
    /** Marked for a bulk action (GridSelect.kt): a check badge, and the tile sits a little smaller. */
    marked: Boolean = false,
    onFocus: () -> Unit,
    onClick: () -> Unit,
    /** A long A (or a long press on the screen); null: a long press is a plain one. */
    onLongClick: (() -> Unit)? = null,
    /** On a grid that takes drags (GridDrag.kt): the home grid ("" ) or that folder's id; null: no dragging. */
    dragGrid: String? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val press = remember { TilePress() }
    val pressed = press.pressed
    val theme = Themes.live
    val dragFrom = dragGrid?.ifEmpty { null }
    // Only the tile being dragged and the one under it recompose as a drag goes on.
    val lifted by remember(item.key, dragGrid) { androidx.compose.runtime.derivedStateOf { dragGrid != null && GridDrag.lifted(item.key) } }
    val dropHere by remember(item.key, dragGrid) { androidx.compose.runtime.derivedStateOf { if (dragGrid == null) 0 else GridDrag.hover(dragFrom, item.key) } }
    // A theme's hover scale of 1.0 means no grow at all; Tandem's themes grow a little.
    val grow = theme.focusScale.coerceIn(1f, 1.12f)
    val shrink = if (marked) 0.9f else 1f
    val scale = animateFloatAsState(
        when {
            moving -> grow + 0.12f
            dropHere == 2 -> grow + 0.1f
            dropHere == 1 -> grow
            pressed -> 0.93f * shrink
            focused -> grow * shrink
            else -> shrink
        },
        Motion.focus(), label = "tile-scale",
    )
    val lift = animateFloatAsState(if (focused || moving || dropHere > 0) 1f else 0f, Motion.soft(), label = "tile-lift")
    val ghost by animateFloatAsState(if (lifted) 0.28f else 1f, Motion.fade(140), label = "tile-ghost")
    // Being moved, it rises off the grid (bigger, higher, a deeper shadow) and glides from slot to slot.
    val raise = animateFloatAsState(if (moving) 1f else 0f, Motion.move(), label = "tile-raise")
    val risePx = with(LocalDensity.current) { 7.dp.toPx() }
    val glow = Palette.highlightFor(item.color)
    val ring = lerp(glow, Color.White, 0.62f)
    // A designed folder may be a rounded square or a circle instead of the theme's shape (FolderLooks.kt).
    val shape = FolderLooks.shape(item.style, tile, corner)
    val density = LocalDensity.current
    // The theme's highlight thickness (4 by default gives 3 dp; a thickness below 1 gives a finer ring).
    val ringW = with(density) { (theme.highlightThickness * 0.75f).coerceIn(1.2f, 6f).dp.toPx() }
    val ringGap = with(density) { 3.5.dp.toPx() }
    val cornerPx = with(density) { FolderLooks.cornerOf(item.style, tile, corner).toPx() }
    val glowDp = 16f
    Column(
        modifier.width(tile).graphicsLayer { alpha = (if (item.hidden) HiddenAlpha else 1f) * ghost },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(tile)
                .liftOrigin(FolderLooks.cornerOf(item.style, tile, corner) / tile)
                .then(if (dragGrid != null) Modifier.gridTouch(item.key, dragFrom, item) else Modifier.touchEntry())
                .onFocusChanged { if (it.isFocused) { IconGrid.liftTile = tile; IconGrid.liftCorner = corner; onFocus(); Sounds.move() } else press.pressed = false }
                .focusable(interactionSource = interaction)
                .onKeyEvent { e -> tileKey(e, press, onClick, onLongClick) }
                .pointerInput(onClick, onLongClick) {
                    detectTapGestures(
                        onPress = { press.pressed = true; tryAwaitRelease(); press.pressed = false },
                        onTap = { onClick(); if (IconGrid.selecting == null) Sounds.select() },
                    )
                }
                .graphicsLayer {
                    scaleX = scale.value; scaleY = scale.value
                    translationY = -risePx * raise.value
                }
                .drawWithCache {
                    val stroke = Stroke(ringW)
                    val inflate = ringGap + ringW / 2f
                    val topLeft = Offset(-inflate, -inflate)
                    val ringSize = Size(size.width + inflate * 2, size.height + inflate * 2)
                    val radius = CornerRadius(cornerPx + inflate)
                    onDrawWithContent {
                        drawContent()
                        val l = lift.value
                        if (l > 0.01f) drawRoundRect(ring, topLeft, ringSize, radius, alpha = l.coerceAtMost(1f), style = stroke)
                    }
                },
        ) {
            Box(
                Modifier.fillMaxSize().graphicsLayer {
                    val l = lift.value
                    this.shape = shape
                    clip = true
                    shadowElevation = (l + raise.value * 1.2f) * glowDp * this.density
                    ambientShadowColor = glow.copy(alpha = 0.5f)
                    spotShadowColor = glow
                },
            ) { TileArtView(item.art, tile) }
            if (item.kind == TileKind.Now) NowBadge(Modifier.align(Alignment.BottomEnd).padding(tile * 0.06f))
            if (item.hidden) HiddenBadge(Modifier.align(Alignment.TopStart))
            // Inside the tile's own corner, so nothing around it can clip it.
            if (marked) CheckBadge((tile * 0.3f).coerceIn(18.dp, 30.dp), Modifier.align(Alignment.TopEnd).padding(tile * 0.04f))
        }
        if (label) {
            Text(
                item.title, Modifier.width(tile + 12.dp).padding(top = 4.dp), style = MaterialTheme.typography.labelMedium,
                color = Palette.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            )
        }
    }
}

/** How long A is held for a long press (marking a tile). */
private const val LongPressMs = 450L

/** A tile's press: shown pressed while A is down; [long] once a long press fired, so its release does nothing. */
internal class TilePress {
    var pressed by mutableStateOf(false)
    var long = false
}

/**
 * A on a tile: only a release after a press on the same tile counts (like [FocusCard]). With [onLong], holding it
 * [LongPressMs] runs that instead: at once while it is held (key repeats), or on the release.
 */
internal fun tileKey(e: KeyEvent, p: TilePress, onClick: () -> Unit, onLong: (() -> Unit)?): Boolean {
    val select = e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.NumPadEnter || e.key == Key.ButtonA
    if (!select) return false
    val ne = e.nativeKeyEvent
    val held = ne.eventTime - ne.downTime
    if (e.type == KeyEventType.KeyDown) {
        if (ne.repeatCount == 0) { p.pressed = true; p.long = false }
        else if (onLong != null && p.pressed && !p.long && held >= LongPressMs) { p.long = true; p.pressed = false; onLong() }
        return true
    }
    if (e.type != KeyEventType.KeyUp) return true
    val was = p.pressed
    p.pressed = false
    if (p.long) { p.long = false; return true }
    if (!was) return true
    if (onLong != null && held >= LongPressMs) onLong()
    else { onClick(); if (IconGrid.selecting == null) Sounds.select() }
    return true
}

/** A select key on a tile: only a release after a press on the same tile counts (like [FocusCard]). */
internal fun selectKey(e: KeyEvent, pressed: () -> Boolean, setPressed: (Boolean) -> Unit, onClick: () -> Unit): Boolean {
    val select = e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.NumPadEnter || e.key == Key.ButtonA
    if (!select) return false
    val was = pressed()
    setPressed(e.type == KeyEventType.KeyDown)
    if (e.type == KeyEventType.KeyUp && was) { onClick(); Sounds.select() }
    return true
}

@Composable
private fun NowBadge(modifier: Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(50)).background(Palette.NightDeep.copy(alpha = 0.82f)).padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(Palette.Mint))
        Spacer(Modifier.width(4.dp))
        Text("Playing", style = MaterialTheme.typography.labelSmall, color = Palette.Mint, maxLines = 1)
    }
}

// --- Moving tiles ----------------------------------------------------------------------------------------------

/** Starts moving [key] through [current], the slots it sits in as they show now (with gaps). */
internal fun startMove(key: String, folder: String?, current: List<String>) {
    if (key == "now") { Toasts.show("Now playing stays at the front"); return }
    folder?.let { IconGridStore.folder(it) }?.takeIf { it.sort != io.github.zahakj.tandem.data.FolderSort.MANUAL }?.let {
        Toasts.show("Sorted ${io.github.zahakj.tandem.data.FolderSort.label(it.sort)}. Y › Sort › Manual to move things by hand"); return
    }
    IconGrid.selecting = null
    IconGrid.moving =MovingTile(key, folder, mutableStateListOf<String>().apply { addAll(current) }, current)
    Toasts.show(if (GridWidgets.idOf(key) != null) "Move it with the D-pad · A to drop it · Y to resize it" else "Move it with the D-pad · A to drop it · Y to carry it")
}

/**
 * A tile opens unless one is moving; while one is carried, folders still open, to paste into them (a carried folder
 * only into folders it fits in: never itself or one inside it).
 */
internal fun canOpen(item: GridItem): Boolean {
    val mv = IconGrid.moving ?: return true
    val into = item.folderId ?: return false
    return mv.carried && item.kind == TileKind.Folder && GridWidgets.idOf(mv.key) == null &&
        (!mv.key.startsWith("folder:") || io.github.zahakj.tandem.data.GridTree.canNest(IconGridStore.config, mv.key, into))
}

/** A drops the moving tile: its slots are saved as they show, placed for [lanes] (and a resized widget's new size). */
private fun dropMove(lanes: Int) {
    val mv = IconGrid.moving ?: return
    val order = mv.order.toList()
    val span = mv.span
    val name = IconGrid.menuData?.first?.item(mv.key)?.title ?: "it"
    val moved = GridSlots.trim(order) != GridSlots.trim(mv.original) || span != null
    IconGrid.moving = null
    if (!moved) { Sounds.select(); return }
    GridUndoUi.run(if (span != null) "Resized $name" else "Moved $name") {
        when {
            mv.folder != null -> IconGridStore.setFolderItems(mv.folder, order, lanes)
            span != null -> {
                IconGridStore.setSpan(mv.key, span, order, lanes)
                GridWidgets.idOf(mv.key)?.let { Widgets.setSize(it, GridWidgets.sizeFor(span)) }
            }
            else -> IconGridStore.setSlots(order, lanes)
        }
    }
    Sounds.select()
}

private fun cancelMove() {
    IconGrid.moving = null
    Nav.focusTick++
}

/** B, a tap away from the tile, or 60 s without input: the moving tile goes back where it was. */
internal fun cancelMoveNow() {
    if (IconGrid.moving == null) return
    cancelMove()
    Sounds.back()
}

/** A tap on the moving tile: dropped where it is, on whichever grid it is moving in. */
internal fun dropMoveNow() {
    val mv = IconGrid.moving ?: return
    val lanes = (if (mv.folder == null) IconGrid.home?.lanes else IconGrid.folderShown?.takeIf { IconGrid.openFolder == mv.folder }?.lanes) ?: return cancelMoveNow()
    dropMove(lanes)
    Nav.focusTick++
}

/**
 * L1 / R1 while moving: the tile goes a screen along (or back), a slot at a time so it hops widgets and keeps its
 * row. [lines]: lines on a screen.
 */
internal fun moveTilePage(dir: Int, lines: Int) {
    val mv = IconGrid.moving?.takeIf { !it.carried } ?: return
    val home = mv.folder == null
    val shown = (if (home) IconGrid.home else IconGrid.folderShown) ?: return
    val ext = if (!home) emptyMap() else {
        val own = mv.span?.let { GridSlots.extent(it, IconGrid.vertical) }
        if (own != null) shown.ext + (mv.key to own) else shown.ext
    }
    var order = mv.order.toList()
    var moved = false
    repeat(lines.coerceAtLeast(1)) {
        val next = GridSlots.step(order, shown.lanes, ext, mv.key, dir, 0, if (home) shown.page else 0) ?: return@repeat
        order = next
        moved = true
    }
    if (!moved) { Sounds.error(); return }
    mv.order.clear()
    mv.order.addAll(order)
    Sounds.page()
}

/**
 * D-pad while moving: the tile steps one slot that way, trading places with what is there (an empty spot, so gaps
 * stay where they are), hopping over a widget in the way. A widget steps one cell, and the tiles in the cells it
 * takes fill the ones it leaves (GridSlots.step). Rows and columns are kept; it can go on past the last tile, over
 * the whole empty canvas. [lanes]: rows of a sideways grid, columns of an up-and-down one; [page]: columns a page.
 */
private fun handleMoveKey(e: KeyEvent, lanes: Int, vertical: Boolean, page: Int = 0): Boolean {
    val mv = IconGrid.moving ?: return false
    // B on the grid itself, so it cancels even when Back never reaches the activity (a touch on the other screen
    // can give that screen's window the keys). Carrying with a folder open, B goes up a level there instead.
    if (e.key == Key.Back || e.key == Key.Escape || e.key == Key.ButtonB) {
        if (mv.carried && IconGrid.openFolder != null) return false
        if (e.type == KeyEventType.KeyUp) cancelMoveNow()
        return true
    }
    if (mv.carried) return false
    val select =e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.NumPadEnter || e.key == Key.ButtonA
    if (select) {
        if (e.type == KeyEventType.KeyUp) dropMove(lanes)
        return true
    }
    val (dLine, dLane) = when (e.key) {
        Key.DirectionLeft -> if (vertical) 0 to -1 else -1 to 0
        Key.DirectionRight -> if (vertical) 0 to 1 else 1 to 0
        Key.DirectionUp -> if (vertical) -1 to 0 else 0 to -1
        Key.DirectionDown -> if (vertical) 1 to 0 else 0 to 1
        else -> return false
    }
    if (e.type != KeyEventType.KeyDown) return true
    // Widgets only live on the home grid; inside a folder every tile is one cell.
    val ext = if (mv.folder != null) emptyMap() else {
        val shown = IconGrid.home?.ext.orEmpty()
        val own = mv.span?.let { GridSlots.extent(it, vertical) }
        if (own != null) shown + (mv.key to own) else shown
    }
    val next = GridSlots.step(mv.order.toList(), lanes, ext, mv.key, dLine, dLane, page)
    if (next != null) {
        mv.order.clear()
        mv.order.addAll(next)
        Sounds.move()
    }
    return true
}

/**
 * The D-pad on a grid of slots, worked out from the slots (GridSlots.stepFocus) instead of Compose's nearest-item
 * search, which could slip to the row above or below over gaps, widgets and pages when moving fast. Each cell gets
 * a requester; [item] / [cell]: what has focus and the cell the D-pad came in on (a widget covers several).
 */
internal class SlotFocus {
    private val requesters = HashMap<String, androidx.compose.ui.focus.FocusRequester>()
    val composed = HashSet<String>()
    var item = -1
    var cell = -1

    fun requester(key: String) = requesters.getOrPut(key) { androidx.compose.ui.focus.FocusRequester() }
}

/** Gives grid cell [key] its requester for [SlotFocus]. */
@Composable
internal fun Modifier.slotFocus(nav: SlotFocus, key: String): Modifier {
    androidx.compose.runtime.DisposableEffect(nav, key) {
        nav.composed.add(key)
        onDispose { nav.composed.remove(key) }
    }
    return this.then(Modifier.focusRequester(nav.requester(key)))
}

/**
 * A D-pad press on a grid with [keys] in its slots ([owners]: who covers each cell; [lanes]: rows of a sideways grid,
 * columns of an up-and-down one): one cell along the same row or column, every press and every key repeat. False
 * at the grid's edge, so focus can leave it (the dock, the strip, the Resume page).
 */
internal fun slotKey(e: KeyEvent, nav: SlotFocus, route: String, keys: List<String>, owners: IntArray, lanes: Int, vertical: Boolean): Boolean {
    // B with tiles marked clears the marks here too, whichever window the keys reach (see handleMoveKey).
    if ((e.key == Key.Back || e.key == Key.ButtonB) && IconGrid.selecting != null && IconGrid.moving == null && IconGrid.menu == null) {
        if (e.type == KeyEventType.KeyUp) GridSelect.clear()
        return true
    }
    if (e.type != KeyEventType.KeyDown) return false
    val (dLine, dLane) = when (e.key) {
        Key.DirectionLeft -> if (vertical) 0 to -1 else -1 to 0
        Key.DirectionRight -> if (vertical) 0 to 1 else 1 to 0
        Key.DirectionUp -> if (vertical) -1 to 0 else 0 to -1
        Key.DirectionDown -> if (vertical) 1 to 0 else 0 to 1
        else -> return false
    }
    val cur = keys.indexOf(FocusMemory.get(route))
    if (cur < 0) return false
    val from = if (nav.item == cur && nav.cell >= 0) nav.cell else cur
    val c = GridSlots.stepFocus(keys.size, owners, lanes, cur, from, dLine, dLane) ?: return false
    if (c < 0) return true
    val o = owners.getOrElse(c) { -1 }.let { if (it >= 0) it else c }
    val k = keys.getOrNull(o)?.takeIf { !isCoveredKey(it) } ?: return true
    nav.item = o
    nav.cell = c
    if (k in nav.composed && runCatching { nav.requester(k).requestFocus() }.isSuccess) return true
    // Not composed yet (far along a page): remembered, and the grid brings it in and focuses it.
    FocusMemory.remember(route, k)
    Nav.focusTick++
    return true
}

/**
 * A Back handler that goes first while [enabled]: it is added to the dispatcher each time it turns on, so it comes
 * before every handler added earlier, wherever this screen is composed (BackHandler keeps its place from when it
 * was first composed, however late it turns on).
 */
@Composable
internal fun PriorityBack(enabled: Boolean, onBack: () -> Unit) {
    val owner = androidx.activity.compose.LocalOnBackPressedDispatcherOwner.current ?: return
    val latest by androidx.compose.runtime.rememberUpdatedState(onBack)
    androidx.compose.runtime.DisposableEffect(enabled, owner) {
        if (!enabled) return@DisposableEffect onDispose { }
        val callback = object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = latest()
        }
        owner.onBackPressedDispatcher.addCallback(callback)
        onDispose { callback.remove() }
    }
}

/** While moving (or carrying) a tile: what the buttons do. */
@Composable
private fun MoveHint(carried: Boolean, widget: Boolean, modifier: Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(50)).background(Palette.Card.copy(alpha = 0.92f)).padding(horizontal = 14.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        if (carried) {
            ButtonHint("A", "Paste on an empty spot", Palette.Mint)
            ButtonHint("B", "Cancel", Palette.Lavender)
        } else {
            // Short words, so it fits the narrow bottom screen too.
            ButtonHint("◀▶", "Move", Palette.Sky)
            ButtonHint("A", "Drop", Palette.Mint)
            ButtonHint("B", "Cancel", Palette.Lavender)
            ButtonHint("Y", if (widget) "Resize" else "Carry", Palette.Butter)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                ButtonHint("L1", "", Palette.Sky)
                ButtonHint("R1", "Page", Palette.Sky)
            }
        }
    }
}

// --- Dock and corner buttons -----------------------------------------------------------------------------------

/** A floating pill of app icons, with the apps drawer first. */
@Composable
private fun PillDock(dock: List<String>, target: String?, modifier: Modifier) {
    val ctx = LocalContext.current
    val route = Route.Home.key
    val look = Store.settings.look
    val shape = dockIconShape
    PrefetchAppLooks(dock)
    Row(
        // A background in the pill shape, not a clip: a highlighted icon grows and glows past the pill.
        modifier.background(Palette.Card.copy(alpha = if (look.dockBackground) 0.86f else 0f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        DockIcon("app:*", target, route, CircleShape, onFocus = { Panels.focusedApp = null }, onClick = { Panels.openApps() }) {
            Box(Modifier.fillMaxSize().background(Palette.Sky.copy(alpha = 0.22f)), contentAlignment = Alignment.Center) {
                GlyphIcon(Glyph.Grid, Palette.Sky, Modifier.padding(9.dp))
            }
        }
        for (pkg in dock) {
            // Tandem's own apps in the dock (Settings) have no Android app menu for Y.
            val own = Dock.builtIn(pkg) != null
            DockIcon("app:$pkg", target, route, shape, onFocus = { Panels.focusedApp = if (own) null else pkg }, onClick = { Dock.open(ctx, pkg) }, onBlur = {
                if (Panels.focusedApp == pkg) Panels.focusedApp = null
            }) { AppIcon(pkg, Modifier.fillMaxSize()) }
        }
    }
}

@Composable
private fun DockIcon(
    key: String, target: String?, route: String, shape: androidx.compose.ui.graphics.Shape,
    onFocus: () -> Unit, onClick: () -> Unit, onBlur: () -> Unit = {}, content: @Composable BoxScope.() -> Unit,
) {
    FocusCard(
        Modifier.size(42.dp).takesFocus(route, key, key == target).onFocusChanged { if (!it.isFocused) onBlur() },
        glow = Palette.Sky, shape = shape,
        // On the icon grid a long press never opens a menu (Y does); a hold here just highlights.
        onLongPress = {},
        // An app (or All apps, "app:*") shows its own details on the other screen.
        onFocus = { onFocus(); IconGrid.spot = null; Selection.current = if (key.startsWith("app:")) Selected.App(key.removePrefix("app:")) else Selected.Home },
        entry = key != "app:*",
        onClick = onClick,
    ) { content() }
}

/** Bottom corners: X swaps the screens (or searches), Y opens the tile menu. Tappable on a touch screen too. */
@Composable
private fun GridCornerHints(modifier: Modifier) {
    val ctx = LocalContext.current
    val swaps = ScreenRoles.xSwaps()
    Box(modifier.padding(horizontal = 18.dp)) {
        CornerButton("X", if (swaps) Glyph.Swap else Glyph.Eye, Modifier.align(Alignment.CenterStart)) {
            if (swaps) ScreenRoles.toggle(ctx) else Nav.search = true
        }
        CornerButton("Y", Glyph.Sort, Modifier.align(Alignment.CenterEnd)) {
            FocusMemory.get(Route.Home.key)?.let { k -> if (!k.startsWith("app:")) IconGrid.openMenu(k, null) }
        }
    }
}

@Composable
private fun CornerButton(button: String, glyph: Glyph, modifier: Modifier, onTap: () -> Unit) {
    Row(
        modifier.clip(RoundedCornerShape(50)).background(Palette.Card.copy(alpha = 0.7f)).swallowTaps(onTap)
            .padding(horizontal = 7.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        ButtonHint(button, "", Palette.Muted)
        GlyphIcon(glyph, Palette.Text, Modifier.size(20.dp))
    }
}

// --- Tile menu ---------------------------------------------------------------------------------------------------

/**
 * Y on a tile: the tile lifts out over the blurred grid with its actions beside it. Composed in the
 * app root (outside the blurred screen) by [IconGridMenuHost].
 */
@Composable
private fun TileMenuPanel(catalog: GridCatalog, arranged: List<String>) {
    val ctx = LocalContext.current
    val open = IconGrid.menu != null
    val about = rememberLatest(IconGrid.menu)
    PanelGuard(open)
    InterceptButtons(open) { true }
    ActionMenuHost(open, onClose = { IconGrid.closeMenu() }) spec@{
        val t = about ?: return@spec null
        // Tiles marked on this grid: what to do with them (GridSelect.kt), on a tile or an empty spot alike.
        if (GridSelect.on(t.folder)) return@spec selectionMenuSpec(t, catalog, ctx)
        // An empty spot: Add here (GridSpots.kt).
        if (isGapKey(t.key)) return@spec gapMenuSpec(t, catalog, ctx)
        val item = catalog.item(t.key) ?: return@spec null
        // A widget: its options, move, resize and remove (GridWidgets.kt).
        if (item.kind == TileKind.Widget) return@spec widgetMenuSpec(item, homeShown().slots, ctx)
        tileMenuSpec(t, item, catalog, ctx)
    }
}

/** Y on a tile (not an empty spot or a widget): its actions, and the pages for folders, sorting and deleting. */
private fun tileMenuSpec(t: TileMenuTarget, item: GridItem, catalog: GridCatalog, ctx: Context): MenuSpec {
    val routeKey = IconGrid.MENU_ROUTE + ":" + t.key
    val inFolder = t.folder
    val c = IconGridStore.config
    val holder = inFolder?.let { IconGridStore.folder(it) }
    val sorted = holder != null && holder.sort != FolderSort.MANUAL
    val isFolder = item.kind == TileKind.Folder
    // The slots the tile sits in as they show (with gaps), for Move.
    val siblings = if (inFolder == null) homeShown().slots
    else IconGrid.folderShown?.takeIf { IconGrid.openFolder == inFolder }?.slots ?: holder?.items.orEmpty()
    val route = if (inFolder == null) Route.Home.key else IconGrid.folderRoute(inFolder)
    // A new folder made from here goes in this folder when it can hold one, else on the home grid; a folder tile only
    // fits in it when that isn't too deep.
    val newDepth = (if (inFolder != null && GridTree.canHoldFolder(c, inFolder)) GridTree.depthOf(c, inFolder) else 0) + 1
    val canMake = !isFolder || newDepth + 1 + GridTree.heightOf(c, item.folderId.orEmpty()) <= GridTree.MAX_DEPTH
    val actions = buildList {
        // An app gets the same menu as in the dock and the drawer (AppActionMenu.kt); it's already on the grid.
        val pkg = item.pkg?.takeIf { item.kind == TileKind.App }
        if (pkg != null) addAll(appMenuActions(ctx, pkg, done = { IconGrid.closeMenu() }, close = { IconGrid.closeMenu() }).filter { it.id != "home" })
        else             add(MenuAction("open", when { item.kind != TileKind.Game -> "Open"; RunningGames.list.any { it.game?.key == item.game?.key } -> "Resume"; else -> "Play" }, Glyph.Play, Palette.Mint) { IconGrid.closeMenu(); IconGrid.openedFrom = t.key.takeIf { inFolder == null }; item.open() })
        if (item.kind != TileKind.Now) {
            if (!sorted) add(MenuAction("move", "Move", Glyph.Move, Palette.Sky, value = "◀▶") { IconGrid.closeMenu(); startMove(t.key, inFolder, siblings) })
            add(MenuAction("select", "Select", Glyph.Highlight, Palette.Sky, sub = "Mark several, then act on them all") {
                IconGrid.closeMenu(); GridSelect.start(inFolder, t.key)
            })
            // Folders hold shortcuts: a tile can go into another folder and stay here too. A folder has one place.
            if (canMake || folderChoices(listOf(t.key), inFolder, move = true) {}.isNotEmpty()) {
                add(MenuAction("folder", "Move to folder…", Glyph.Folder, Palette.Butter) { IconGrid.menuPage("move") })
            }
            if (!isFolder) add(MenuAction("also", "Also add to folder…", Glyph.Plus, Palette.Butter, sub = "It stays here too") { IconGrid.menuPage("add") })
        }
        if (inFolder != null) {
            val up = IconGridStore.parentOf(inFolder)
            add(MenuAction("out", if (up != null) "Move up to ${up.name}" else "Take out of the folder", Glyph.Back, Palette.Butter) {
                keepPlaces(inFolder)
                GridUndoUi.run("Took ${item.title} out of ${holder?.name ?: "the folder"}") {
                    IconGridStore.takeOut(t.key, inFolder, homeShown(), item.default)
                }
                IconGrid.closeMenu()
            })
        }
        if (item.game != null || item.platformId != null || item.collectionId != null || item.kind == TileKind.Genres || item.kind == TileKind.Genre) {
            add(MenuAction("options", if (item.game != null) "Game options" else "Sort and view", Glyph.Sort, Palette.Sky) {
                IconGrid.menu = null
                val g = item.game
                when {
                    g != null -> Nav.openMenu(g)
                    item.platformId != null -> Nav.openGroupMenu(GroupTarget.System(item.platformId))
                    item.kind == TileKind.Genres -> Nav.openGroupMenu(GroupTarget.Genres)
                    item.kind == TileKind.Genre -> Nav.openGroupMenu(GroupTarget.Collection(item.key))
                    item.collectionId != null -> Nav.openGroupMenu(GroupTarget.Collection(item.collectionId))
                }
            })
        }
        if (FolderLooks.designable(item)) add(MenuAction("design", "Design folder…", Glyph.Wand, Palette.Lavender) {
            IconGrid.closeMenu(); FolderDesign.open(t.key)
        })
        val fid = item.folderId?.takeIf { isFolder }
        if (fid != null) {
            add(MenuAction("rename", "Rename folder", Glyph.Pencil, Palette.Butter) {
                IconGrid.closeMenu()
                TextEntry.ask("Rename the folder", item.title) { name -> name.trim().takeIf { it.isNotEmpty() }?.let { IconGridStore.renameFolder(fid, it) } }
            })
            val own = IconGridStore.folder(fid)?.sort ?: FolderSort.MANUAL
            // A smart folder keeps the order its rule sets.
            if (IconGridStore.folder(fid)?.smart != true) add(MenuAction("sortin", "Sort inside…", Glyph.Sort, Palette.Sky, value = FolderSort.label(own)) { IconGrid.menuPage("sort:$fid") })
            add(MenuAction("delete", "Delete folder…", Glyph.Trash, Palette.Peach, more = true) {
                val f = IconGridStore.folder(fid)
                if (f != null && f.items.none { it.isNotEmpty() }) {
                    GridUndoUi.run("Deleted ${f.name}") { IconGridStore.deleteFolder(fid, homeShown(), keep = true) }
                    IconGrid.closeMenu()
                } else IconGrid.menuPage("delete:$fid")
            })
            // Hiding is undoable (the toast offers it); unhiding is the plain Unhide.
            if (item.hidden) add(hideAction("Unhide this folder", true, undo = { HiddenFolders.setGridFolderHidden(fid, true) }) {
                IconGrid.closeMenu()
                keepPlaces(inFolder)
                HiddenFolders.setGridFolderHidden(fid, false)
            }) else add(MenuAction("hidefolder", "Hide this folder", Glyph.Eye, Palette.Lavender, more = true) {
                IconGrid.closeMenu()
                keepPlaces(inFolder)
                GridUndoUi.run("Hid ${item.title}") { HiddenFolders.setGridFolderHidden(fid, true) }
            })
        }
        val system = item.platformId?.takeIf { item.kind == TileKind.System }
        if (system != null) {
            val listed = HiddenFolders.listed(system, null)
            if (listed) add(hideAction("Unhide this system", true, undo = { HiddenFolders.hide(system, null) }) {
                IconGrid.closeMenu()
                keepPlaces(inFolder)
                HiddenFolders.unhide(system, null)
            }) else add(MenuAction("hidefolder", "Hide this system", Glyph.Eye, Palette.Lavender, more = true) {
                IconGrid.closeMenu()
                keepPlaces(inFolder)
                GridUndoUi.run("Hid ${item.title}", systems = listOf(system)) { HiddenFolders.hide(system, null) }
                Nav.focusTick++
            })
        }
        if (item.kind != TileKind.Now && !isFolder) add(MenuAction("remove", if (inFolder == null) "Remove from home" else "Remove from folder", Glyph.Close, Palette.Peach, more = true) {
            keepPlaces(inFolder)
            GridUndoUi.run("Removed ${item.title}") {
                if (inFolder == null) IconGridStore.removeFromHome(t.key) else IconGridStore.removeFromFolder(t.key, inFolder)
            }
            IconGrid.closeMenu()
        })
        if (inFolder == null) add(MenuAction("add", "Add to home…", Glyph.Plus, Palette.Mint, more = true) {
            IconGrid.menu = null
            FocusMemory.forget(IconGrid.ADD_ROUTE)
            IconGrid.addAt = null
            IconGrid.addFilter = null
            IconGrid.adding = true
        })
        if (inFolder != null) add(MenuAction("sortfolder", "Sort this folder…", Glyph.Sort, Palette.Sky, value = FolderSort.label(holder?.sort ?: FolderSort.MANUAL), more = true) {
            IconGrid.menuPage("sort:$inFolder")
        })
        add(MenuAction("tidy", "Tidy up", Glyph.Sparkle, Palette.Mint, more = true) {
            IconGrid.closeMenu()
            if (inFolder == null) tidyHome() else tidyFolder(inFolder)
        })
        GridUndoUi.action { IconGrid.closeMenu() }?.let { add(it) }
        add(MenuAction("search", "Search", Glyph.Search, Palette.Sky, more = true) { IconGrid.menu = null; Nav.search = true })
        add(MenuAction("swap", "Swap screens", Glyph.Swap, Palette.Lavender, value = if (ScreenRoles.xSwaps()) "X" else null, more = true) {
            IconGrid.closeMenu(); ScreenRoles.toggle(ctx)
        })
    }
    val p = IconGrid.folderPage.takeIf { IconGrid.choosingFolder }
    val back = { IconGrid.choosingFolder = false; IconGrid.folderPage = null }
    val page = when {
        p == null -> null
        p == "move" || p == "add" -> {
            val move = p == "move"
            val pick: (io.github.zahakj.tandem.data.HomeFolder) -> Unit = { f ->
                keepPlaces(inFolder)
                suggestForFirst(ctx, f.id, item)
                GridUndoUi.run((if (move) "Moved ${item.title} into " else "Added ${item.title} to ") + f.name) {
                    if (move) IconGridStore.moveIntoFolder(t.key, inFolder, f.id) else IconGridStore.addToFolder(t.key, f.id)
                }
                IconGrid.closeMenu()
            }
            val rows = folderChoices(listOf(t.key), inFolder, move, pick)
            MenuPage(
                if (move) "Move to folder" else "Also add to folder",
                rows + listOfNotNull(MenuAction("new", "New folder…", Glyph.Plus, Palette.Mint) {
                    IconGrid.closeMenu()
                    TextEntry.ask("Name the folder", "New folder") { name ->
                        val n = name.trim().ifEmpty { "Folder" }
                        keepPlaces(inFolder)
                        var made = ""
                        GridUndoUi.run("Made the folder $n") {
                            made = IconGridStore.createFolder(n, listOf(t.key), inFolder, homeShown(), IconGridStore.config.folders.size, move = move, fromShown = shownFor(inFolder))
                        }
                        FolderDesign.suggest(ctx, made, item)
                        FocusMemory.remember(routeFor(IconGridStore.parentOf(made)?.id), "folder:$made")
                        Nav.focusTick++
                    }
                }.takeIf { canMake }),
                onBack = back,
            )
        }
        p.startsWith("sort:") -> {
            val id = p.removePrefix("sort:")
            val now = IconGridStore.folder(id)?.sort ?: FolderSort.MANUAL
            MenuPage(
                "Sort ${IconGridStore.folder(id)?.name ?: "folder"}",
                FolderSort.ALL.map { (s, label) ->
                    MenuAction("s:$s", label, Glyph.Sort, Palette.Sky, value = if (s == now) "✓" else null, sub = if (s == FolderSort.MANUAL) "Your own order, kept under every sort" else null) {
                        IconGridStore.setFolderSort(id, s)
                        Sounds.toggle(true)
                        Toasts.show(if (s == FolderSort.MANUAL) "Back to your own order" else "Sorted $label")
                        IconGrid.closeMenu()
                    }
                },
                onBack = back,
            )
        }
        p.startsWith("delete:") -> {
            val id = p.removePrefix("delete:")
            val f = IconGridStore.folder(id)
            val up = IconGridStore.parentOf(id)?.name ?: "the home grid"
            val subs = GridTree.descendants(c, id).size
            MenuPage(
                "Delete ${f?.name ?: "folder"}?",
                listOf(
                    MenuAction("keep", "Keep its contents", Glyph.Folder, Palette.Mint, sub = "They move up to $up, into its place") {
                        GridUndoUi.run("Deleted ${f?.name ?: "the folder"}") { IconGridStore.deleteFolder(id, homeShown(), keep = true, defaults = catalog.defaults.toSet()) }
                        // Focus lands on the first thing that took the folder's place.
                        f?.items?.firstOrNull { it.isNotEmpty() }?.let { FocusMemory.remember(route, it) }
                        IconGrid.closeMenu()
                    },
                    MenuAction(
                        "all", if (subs > 0) "Delete it and its subfolders" else "Delete it and its shortcuts", Glyph.Trash, Palette.Peach,
                        sub = "Games and apps themselves stay", hold = true,
                    ) {
                        GridUndoUi.run("Deleted ${f?.name ?: "the folder"}") { IconGridStore.deleteFolder(id, homeShown(), keep = false) }
                        IconGrid.closeMenu()
                    },
                ),
                onBack = back,
            )
        }
        else -> null
    }
    return MenuSpec(
        routeKey, item.title, listOf(item.type, item.detail).filter { it.isNotEmpty() }.joinToString(" · "), item.color, actions, page,
    ) { LiftedTileArt(item) }
}


/**
 * A tile lifted into its Y menu: drawn exactly as on the grid (the same art at the same size, in the same shape and
 * corner, so a designed folder's letter or icon keeps its proportions) and scaled as a whole to the room it has.
 */
@Composable
internal fun LiftedTileArt(item: GridItem) {
    val tile = IconGrid.liftTile
    val shape = FolderLooks.shape(item.style, tile, IconGrid.liftCorner)
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val s = if (tile > 0.dp) maxWidth / tile else 1f
        Box(
            Modifier.requiredSize(tile).graphicsLayer { scaleX = s; scaleY = s; this.shape = shape; clip = true },
        ) { TileArtView(item.art, tile) }
    }
}

/** The icon grid's Y menu, composed in the app root so it sits above the blurred screen. */
@Composable
fun IconGridMenuHost() {
    val data = IconGrid.menuData ?: return
    TileMenuPanel(data.first, data.second)
}

// --- Folders of tiles ------------------------------------------------------------------------------------------

/**
 * A folder the user made: its tiles in a big rounded panel over the grid. A folder inside opens in the same panel,
 * with the way back in the header (Home › Games › RPGs); B goes up one level.
 */
@Composable
private fun HomeFolderPanel(catalog: GridCatalog) {
    val id = IconGrid.openFolder
    val shown = rememberLatest(id)
    val visible = id != null
    PanelGuard(visible)
    // B while moving a tile in here puts it back; with tiles marked, the marks go; otherwise up a level (or closed).
    // While carrying a tile, B goes up like any other time.
    val close: () -> Unit = {
        val open = IconGrid.openFolder
        when {
            IconGrid.moving?.let { it.folder != null && !it.carried } == true -> cancelMove()
            open != null && GridSelect.on(open) -> GridSelect.clear()
            else -> {
                val up = open?.let { IconGridStore.parentOf(it) }
                if (up != null && open != null) {
                    FocusMemory.remember(IconGrid.folderRoute(up.id), "folder:$open")
                    IconGrid.openFolder = up.id
                    Sounds.back()
                } else IconGrid.openFolder = null
                Nav.focusTick++
            }
        }
    }
    // The panel grows out of the folder's tile and shrinks back into it (PanelMorph.kt).
    val morph = rememberMorph(visible) {
        val key = "folder:$id"
        catalog.item(key)?.let { tileMorph(key.takeIf { IconGrid.openedFrom == it }, it.art, it.color) }
    }
    OverlayPanel(visible, onClose = close, morph = morph) {
        val fid = shown ?: return@OverlayPanel
        // Gone, or hidden away while open (its own menu, or the private-mode combo): close it.
        val folder = IconGridStore.folder(fid)?.takeIf { Privacy.canShowHomeFolder(it) }
        if (folder == null) {
            LaunchedEffect(Unit) { IconGrid.openFolder = null; Nav.focusTick++ }
            return@OverlayPanel
        }
        val moving = IconGrid.moving?.takeIf { it.folder == fid }
        val route = IconGrid.folderRoute(fid)
        val sorted = folder.sort != io.github.zahakj.tandem.data.FolderSort.MANUAL
        // The slots' keys (empty spots included), filled in below once the panel knows its columns.
        val view = remember(fid) { arrayOf(emptyList<String>()) }
        val tiles = remember(folder.items, catalog) { folder.items.count { it.isNotEmpty() && catalog.item(it) != null } }
        val selecting = IconGrid.selecting?.takeIf { it.folder == fid } != null
        InterceptButtons(visible) { b ->
            val mv = IconGrid.moving
            if (mv != null && !mv.carried) {
                if (mv.folder == fid) when (b) {
                    Button.Y -> carryMove()
                    Button.Select -> GridSelect.fromMove()
                    Button.L1, Button.R1 -> moveTilePage(if (b == Button.R1) 1 else -1, 3)
                    else -> Unit
                }
                return@InterceptButtons true
            }
            val at = FocusMemory.get(route)?.takeIf { it in view[0] }
            when {
                // A tile's menu, or Add here on an empty spot (the only thing Y does while a tile is carried); with
                // tiles marked, what to do with them.
                b == Button.Y && IconGrid.menu == null -> at?.takeIf { mv == null || isGapKey(it) }?.let { IconGrid.openMenu(it, fid) }
                // Select marks (or unmarks) the highlighted tile; a long A does the same.
                b == Button.Select && GridSelect.on(fid) -> at?.let { GridSelect.toggle(fid, it) }
            }
            true
        }
        // The way here from the home grid, for the header.
        val config = IconGridStore.config
        val trail = remember(config, fid) { listOf("Home") + io.github.zahakj.tandem.data.GridTree.trail(config, fid).dropLast(1).map { it.name } }
        val subtitle = plural(tiles, "item") + if (sorted) " · ${io.github.zahakj.tandem.data.FolderSort.label(folder.sort)}" else ""
        FolderSheet(folder.name, subtitle, folderColor(folder.color), onBack = close, art = catalog.item("folder:$fid")?.art, trail = trail, actions = {
            when {
                moving != null && !moving.carried -> {
                    ButtonHint("A", "Drop", Palette.Mint); Spacer(Modifier.width(10.dp))
                    ButtonHint("B", "Cancel", Palette.Lavender); Spacer(Modifier.width(10.dp))
                    ButtonHint("Y", "Carry", Palette.Butter); Spacer(Modifier.width(10.dp))
                    ButtonHint("L1", "", Palette.Sky); Spacer(Modifier.width(3.dp))
                    ButtonHint("R1", "Page", Palette.Sky)
                }
                IconGrid.moving?.carried == true -> ButtonHint("A", "Paste on an empty spot", Palette.Mint)
                selecting -> SelectPill(fid, compact = true)
                else -> ButtonHint("Y", "Options", Palette.Butter)
            }
        }) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val tile = 84.dp
                val gap = 22.dp
                val labelH = 20.dp
                val cols = ((maxWidth - 40.dp + gap) / (tile + gap)).toInt().coerceAtLeast(1)
                // Empty spots fill the panel, and there is always a row of them after the last thing inside.
                val fit = ((maxHeight - FocusRoom * 2 + gap) / (tile + labelH + gap)).toInt().coerceAtLeast(1)
                val present = remember(folder.items, catalog) { folder.items.filter { it.isNotEmpty() && catalog.item(it) != null } }
                val presentSet = remember(present) { present.toHashSet() }
                // Sorted, the folder shows its things packed in that order; the manual order stays underneath.
                val stored = remember(folder.items, folder.lanes, folder.sort, cols, present, catalog) {
                    if (sorted) io.github.zahakj.tandem.data.FolderSort.apply(present, folder.sort) { catalog.sortFacts(it) }
                    else GridSlots.shown(folder.items, folder.lanes, cols, present)
                }
                val slots = moving?.order?.toList() ?: stored
                val count = GridSlots.shownCount(slots, cols, minEmpty = 1, atLeast = fit)
                val cells = remember(slots, count, catalog, presentSet) {
                    List(count) { i -> slots.getOrNull(i)?.takeIf { it in presentSet }?.let { catalog.item(it) } }
                }
                val keys = remember(cells) { cells.mapIndexed { i, it -> it?.key ?: gapKey(i) } }
                view[0] = keys
                IconGrid.folderShown = remember(slots, presentSet, cols) { GridShown(GridSlots.trim(slots), presentSet, cols) }
                val target = rememberFocusTarget(route, keys)
                val state = rememberLazyGridState()
                val nav = remember(fid) { SlotFocus() }
                // Drops land here too (GridDrag.kt); dragging out past its edge goes back up a level.
                val zone = rememberDropZone(fid, state)
                val density = LocalDensity.current
                androidx.compose.runtime.SideEffect {
                    zone.vertical = true; zone.lanes = cols; zone.keys = keys; zone.owners = IntArray(0)
                    zone.tilePx = with(density) { tile.toPx() }; zone.gapPx = with(density) { gap.toPx() }
                }
                KeepTileInView(state, keys.indexOf(moving?.takeIf { !it.carried }?.key ?: target), vertical = true, follow = moving != null && !moving.carried)
                // What's inside rises in one after another as the panel opens (not when scrolled to later).
                val openedAt = remember(fid) { android.os.SystemClock.uptimeMillis() }
                val corner = tile * Store.settings.look.gridRoundness.coerceIn(0f, 0.5f)
                PaddedScrolling {
                    LazyVerticalGrid(
                        GridCells.Fixed(cols),
                        Modifier.fillMaxSize().onGloballyPositioned { zone.coords = it }.fadingEdges(state).onPreviewKeyEvent { e ->
                            moving != null && handleMoveKey(e, cols, vertical = true) || slotKey(e, nav, route, keys, IntArray(0), cols, vertical = true)
                        },
                        state = state,
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = FocusRoom),
                        horizontalArrangement = Arrangement.spacedBy(gap, Alignment.CenterHorizontally),
                        verticalArrangement = Arrangement.spacedBy(gap),
                    ) {
                        itemsIndexed(cells, key = { i, _ -> keys[i] }, contentType = { i, it -> cellType(keys[i], it) }) { i, item ->
                            val fresh = remember { android.os.SystemClock.uptimeMillis() - openedAt < 450 }
                            Box(
                                // Tiles glide to new places on the same spring as on the home grid (a bulk move, a sort).
                                Modifier.animateItem(placementSpec = tilePlacement()).zIndex(if (keys[i] == moving?.key) 1f else 0f).slotFocus(nav, keys[i]).then(if (fresh) Modifier.riseIn(fid, 0, 18.dp, 0.82f, delayMs = 70L + minOf(i, 11) * 24L) else Modifier),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (item == null) {
                                    val key = keys[i]
                                    val dropHere by remember(key, fid) { androidx.compose.runtime.derivedStateOf { GridDrag.hover(fid, key) > 0 } }
                                    EmptySlot(
                                        tile, corner, labelH, Modifier.takesFocus(route, key, key == target),
                                        onFocus = { Selection.follow { IconGrid.spot = EmptySpotItem; Selection.current = EmptySpotItem.selected } },
                                        onClick = { IconGrid.openMenu(key, fid) },
                                        hovered = dropHere,
                                    )
                                } else {
                                    val marked by remember(item.key, fid) { androidx.compose.runtime.derivedStateOf { GridSelect.marked(fid, item.key) } }
                                    IconTile(
                                        item, tile, corner, label = true,
                                        modifier = Modifier.takesFocus(route, item.key, item.key == target),
                                        moving = moving?.key == item.key,
                                        marked = marked,
                                        onFocus = { Selection.follow { IconGrid.spot = item; Selection.current = item.selected } },
                                        onClick = {
                                            when {
                                                GridSelect.on(fid) -> GridSelect.toggle(fid, item.key)
                                                canOpen(item) -> { IconGrid.openedFrom = null; item.open() }
                                            }
                                        },
                                        onLongClick = { if (IconGrid.moving == null) GridSelect.toggle(fid, item.key) },
                                        dragGrid = fid,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * The rounded panel folders open in, over the grid: a title bar with a back button and the folder's name, room
 * for chips on the right, and the contents. Used for systems and collections too (see [FolderPopupHost]).
 */
@Composable
fun FolderSheet(
    title: String,
    subtitle: String?,
    color: Color,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    /** The folder's own icon, floating in the header (else a dot in its colour). */
    art: TileArt? = null,
    /** The folders above this one, from "Home" down, shown before its name as a breadcrumb (Home › Games › RPGs). */
    trail: List<String> = emptyList(),
    /** Set when [actions] holds buttons ([headerControl]): Up from the content reaches them, Down comes back. */
    header: HeaderFocus? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    Column(
        Modifier.fillMaxSize()
            .graphicsLayer { shape = RoundedCornerShape(30.dp); clip = true; shadowElevation = 24f * density }
            .background(Palette.Card.copy(alpha = 0.97f)).swallowTaps(),
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 16.dp, top = 16.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.clip(RoundedCornerShape(50)).background(Palette.CardHigh).swallowTaps(onBack).padding(start = 6.dp, end = 10.dp, top = 5.dp, bottom = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ButtonHint("B", "", Palette.Muted)
                Spacer(Modifier.width(6.dp))
                BackArrow(Palette.Text, Modifier.size(18.dp))
            }
            Spacer(Modifier.width(12.dp))
            if (art != null) {
                // Rounded like the tile on Home, not a fixed 10 dp: icon-pack art carries its own frame near its
                // edges, and a rounder clip than the tile's cut that frame off at the corners.
                val roundness = Store.settings.look.gridRoundness.coerceIn(0f, 0.5f)
                val artShape = remember(roundness) { RoundedCornerShape(androidx.compose.foundation.shape.CornerSize(roundness * 100f)) }
                Box(
                    Modifier.size(38.dp).riseIn(title, 0, 10.dp).floating(title.hashCode(), bob = 2.5.dp)
                        .graphicsLayer { shape = artShape; clip = true; shadowElevation = 8f * density; spotShadowColor = color },
                ) { TileArtView(art, 38.dp) }
                Spacer(Modifier.width(10.dp))
            } else {
                Box(Modifier.size(10.dp).clip(CircleShape).background(color))
                Spacer(Modifier.width(8.dp))
            }
            Column(Modifier.weight(1f)) {
                val crumbSize = MaterialTheme.typography.titleSmall.fontSize
                val heading = remember(title, trail, crumbSize) {
                    if (trail.isEmpty()) androidx.compose.ui.text.AnnotatedString(title) else androidx.compose.ui.text.buildAnnotatedString {
                        // The folders above, shortened, so the name itself stays in view.
                        pushStyle(androidx.compose.ui.text.SpanStyle(color = Palette.Muted, fontSize = crumbSize))
                        for (t in trail) append((if (t.length > 14) t.take(13).trimEnd() + "…" else t) + "  ›  ")
                        pop()
                        append(title)
                    }
                }
                Text(heading, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.labelMedium, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(10.dp))
            if (header != null) Row(Modifier.headerFocus(header), verticalAlignment = Alignment.CenterVertically) { actions() } else actions()
        }
        Box(Modifier.fillMaxWidth().weight(1f)) { content() }
    }
}

@Composable
private fun BackArrow(color: Color, modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        val s = w * 0.12f
        drawLine(color, Offset(w * 0.15f, w / 2), Offset(w * 0.85f, w / 2), s, StrokeCap.Round)
        drawLine(color, Offset(w * 0.15f, w / 2), Offset(w * 0.45f, w * 0.2f), s, StrokeCap.Round)
        drawLine(color, Offset(w * 0.15f, w / 2), Offset(w * 0.45f, w * 0.8f), s, StrokeCap.Round)
    }
}

// --- Adding things -----------------------------------------------------------------------------------------------

/** Whether [key] is the kind of thing Add here › [filter] lists (null: anything). */
private fun addKind(key: String, filter: String?): Boolean = when (filter) {
    "system" -> key.startsWith("s:")
    "collection" -> key.startsWith("col:") || key == "genres" || key.startsWith("genre:")
    "app" -> key == "allapps" || key.startsWith("app:") && !key.startsWith("app:tandem:")
    "tandem" -> key.startsWith("tandem:")
    else -> true
}

/** The add panel's title and what it says when there is nothing left to add. */
private fun addWords(filter: String?, here: Boolean): Pair<String, String> = when (filter) {
    "system" -> "Add a system here" to "Every system is already on the grid or in a folder. Systems taken off the grid show up here."
    "collection" -> "Add a collection or genre here" to "Every collection and genre is already on the grid or in a folder."
    "app" -> "Add an app here" to "Every app is already on the grid or in a folder."
    "tandem" -> "Add a Tandem app here" to "Every Tandem app is already on the grid or in a folder."
    else -> (if (here) "Add to this spot" else "Add to home") to "Every system, collection and app is on your home grid."
}

/** Y › Add to home: systems and collections taken off, genres, the apps drawer, and any app. A adds, B closes. */
@Composable
private fun AddToHomePanel(catalog: GridCatalog, onHome: Set<String>) {
    val ctx = LocalContext.current
    val visible = IconGrid.adding
    PanelGuard(visible)
    InterceptButtons(visible) { true }
    val close: () -> Unit = { IconGrid.adding = false; IconGrid.addAt = null; Nav.focusTick++ }
    // From an empty spot's Add here: the pick goes in that slot, and the panel closes on it.
    val spot = IconGrid.addAt
    // Add here › System…, App…: only that kind of thing.
    val filter = IconGrid.addFilter
    OverlayPanel(visible, onClose = close) {
        val apps by produceState<List<AppEntry>?>(null) { value = withContext(Dispatchers.IO) { Dock.allApps(ctx) } }
        // What the grid it adds to shows already: the home grid's tiles, or the folder's (folders hold shortcuts, so
        // something in another folder can still be added).
        val addInto = spot?.folder
        val config = IconGridStore.config
        val here = remember(onHome, addInto, config) { if (addInto == null) onHome else IconGridStore.folder(addInto)?.items.orEmpty().toHashSet() }
        val keys = remember(catalog, here, apps, filter) {
            (catalog.addable(here) + apps.orEmpty().map { "app:${it.pkg}" }.filter { it !in here }).filter { addKind(it, filter) }
        }
        val items = remember(keys, catalog) { keys.mapNotNull { catalog.item(it) } }
        val route = IconGrid.ADD_ROUTE
        val target = rememberFocusTarget(route, items.map { it.key })
        val (title, none) = addWords(filter, spot != null)
        val waiting = apps == null && (filter == null || filter == "app")
        FolderSheet(title, if (waiting) "Gathering apps…" else plural(items.size, "thing"), Palette.Mint, onBack = close, art = TileArt.Tandem(TIcon.NewBox, Palette.Mint), actions = {
            ButtonHint("A", "Add", Palette.Mint)
        }) {
            if (items.isEmpty() && !waiting) {
                PipNote("Nothing left to add", none, mood = 1, modifier = Modifier.align(Alignment.Center))
            } else {
                val state = rememberLazyGridState()
                KeepTileInView(state, items.indexOfFirst { it.key == target }, vertical = true)
                PaddedScrolling {
                    LazyVerticalGrid(
                        GridCells.Adaptive(96.dp),
                        Modifier.fillMaxSize().fadingEdges(state),
                        state = state,
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = FocusRoom),
                        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        itemsIndexed(items, key = { _, it -> it.key }) { _, item ->
                            Box(contentAlignment = Alignment.Center) {
                                IconTile(
                                    item, 76.dp, 76.dp * Store.settings.look.gridRoundness.coerceIn(0f, 0.5f), label = true,
                                    modifier = Modifier.takesFocus(route, item.key, item.key == target),
                                    onFocus = { IconGrid.spot = item; Selection.current = item.selected },
                                    onClick = add@{
                                        val at = IconGrid.addAt
                                        if (at != null) {
                                            val into = at.folder
                                            if (into == null) IconGridStore.addAt(item.key, item.default, homeShown(), at.index)
                                            else IconGrid.folderShown?.let { s ->
                                                suggestForFirst(ctx, into, item)
                                                IconGridStore.addToFolder(item.key, into, at.index, s)
                                            }
                                            FocusMemory.remember(if (into == null) Route.Home.key else IconGrid.folderRoute(into), item.key)
                                            close()
                                            Sounds.confirm()
                                            Toasts.show(if (into == null) "${item.title} is on your home grid" else "${item.title} is in the folder")
                                            return@add
                                        }
                                        IconGridStore.add(item.key, item.default)
                                        if (item.key !in IconGridStore.config.order && IconGridStore.config.order.isNotEmpty()) {
                                            IconGridStore.setOrder(IconGridStore.config.order + item.key)
                                        }
                                        Sounds.confirm()
                                        Toasts.show("${item.title} is on your home grid")
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Game menu helper: is this game pinned to the icon grid? */
fun isPinnedToHome(game: Game): Boolean {
    val k = "game:${game.key}"
    return k in IconGridStore.config.added || IconGridStore.inFolders().contains(k)
}

/** Game menu: pin a game to the icon grid, or take it off. */
fun togglePinToHome(game: Game) {
    val k = "game:${game.key}"
    if (isPinnedToHome(game)) {
        IconGridStore.remove(k, default = false)
        Toasts.show("Taken off the home grid")
    } else {
        IconGridStore.add(k, default = false)
        Toasts.show("Pinned to the home grid ★")
    }
}

/** The icon grid tile that opens [r] ("s:gba", "col:fav", "genres"...), or null. */
fun homeTileKey(r: Route): String? = when (r) {
    is Route.System -> if (r.folder == null) "s:${r.platformId}" else null
    is Route.Collection -> if (r.id.startsWith("genre:")) r.id else "col:${r.id}"
    Route.Genres -> "genres"
    else -> null
}

/** The icon a folder popup shows in its header: the same art as its tile on the icon grid. */
fun folderArtFor(r: Route): TileArt? {
    homeTileKey(r)?.let { k -> IconGrid.menuData?.first?.item(k)?.art?.let { return it } }
    return when (r) {
        is Route.System -> IconPacks.systemIcon(r.platformId)?.let { TileArt.Picture(it) }
            ?: Libraries.byId(r.platformId)?.platform?.let { TileArt.Monogram(it.shortName, SystemColors.of(it.id)) }
        Route.Genres -> TileArt.Tandem(TIcon.Genres, Palette.Sky)
        else -> null
    }
}

/**
 * The bottom screen's header over the grid (screens swapped): Pip, and the highlighted item's name, kind and count
 * or play time, cross-fading as you move, like the 3DS bottom screen gives the top line to what you're on.
 */
@Composable
private fun BottomGridHeader(modifier: Modifier) {
    val item = IconGrid.spot
    Row(modifier.padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Pip(40.dp)
        Spacer(Modifier.width(12.dp))
        AnimatedContent(
            item, Modifier.weight(1f), contentKey = { it?.key },
            transitionSpec = { (fadeIn(Motion.fade(220)) + slideInVertically { it / 4 }) togetherWith fadeOut(Motion.fade(120)) },
            label = "bottom-grid-name",
        ) { it ->
            Column {
                Text(
                    it?.title ?: "Home", style = MaterialTheme.typography.titleLarge.onArt(), color = Palette.Text,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                val line = listOfNotNull(it?.type, it?.detail?.takeIf { d -> d.isNotBlank() }, it?.blurb?.takeIf { b -> b.isNotBlank() }).joinToString(" · ")
                if (line.isNotEmpty()) Text(
                    line, style = MaterialTheme.typography.labelLarge,
                    color = it?.color?.let { c -> lerp(c, Palette.Text, 0.45f) } ?: Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
