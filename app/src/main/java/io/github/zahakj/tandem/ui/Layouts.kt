package io.github.zahakj.tandem.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import io.github.zahakj.tandem.GroupTarget
import io.github.zahakj.tandem.plural
import io.github.zahakj.tandem.Collections
import io.github.zahakj.tandem.Game
import io.github.zahakj.tandem.Launcher
import io.github.zahakj.tandem.Libraries
import io.github.zahakj.tandem.MainActivity
import io.github.zahakj.tandem.PlatformLibrary
import io.github.zahakj.tandem.Selected
import io.github.zahakj.tandem.data.HomeStore
import io.github.zahakj.tandem.data.Store
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.abs

/** Home layouts offered in Appearance. */
val HomeLayouts = listOf(
    "shelves" to "Shelves",
    "grid" to "Icon grid",
    "carousel" to "Carousel",
    "list" to "List",
    "console" to "Console",
)

/** How games show inside a system or collection. */
val GameViews = listOf("grid" to "Grid", "list" to "List", "carousel" to "Carousel")

// --- Folders as popups ------------------------------------------------------------------------------------

/** A folder or collection shown as a popup over the current screen; folders opened inside it stack, B goes back one. */
object FolderPopup {
    var route by mutableStateOf<Route?>(null)
    private val parents = ArrayList<Route>()

    fun open(r: Route) {
        route?.let { if (it != r) parents.add(it) }
        route = r
    }

    /** B or the back button: the folder it was opened from, or closed. */
    fun back() {
        route = parents.removeLastOrNull()
        if (route == null) Nav.focusTick++
    }

    fun close() {
        parents.clear()
        route = null
        Nav.focusTick++
    }
}

/** Opens a folder or collection: as a page, or as a popup when the user prefers that (the icon grid does by default). */
fun openFolder(route: Route) {
    val look = Store.settings.look
    val iconGridPopup = look.homeLayout == "grid" && look.gridFolders == "popup" && (Nav.route == Route.Home || FolderPopup.route != null)
    val canPop = route is Route.System || route is Route.Collection || (iconGridPopup && route == Route.Genres)
    if ((look.folderPopup || iconGridPopup) && canPop) FolderPopup.open(route) else Nav.go(route)
}

/** The folder popup. Composed in the app root, under the game menu so Y still works inside it. */
@Composable
fun FolderPopupHost() {
    // Going to another page (Start for settings, say) closes the popup.
    androidx.compose.runtime.LaunchedEffect(Nav.route) { if (FolderPopup.route != null) FolderPopup.close() }
    val current = FolderPopup.route
    val shown = rememberLatest(current)
    // Opened from a tile on the icon grid: the popup grows out of that tile and shrinks back into it.
    val morph = rememberMorph(current != null) {
        val r = current ?: return@rememberMorph null
        val key = (SmartFolders.tileKey(r) ?: homeTileKey(r))?.takeIf { it == IconGrid.openedFrom && IconGrid.enabled }
        val item = key?.let { IconGrid.menuData?.first?.item(it) }
        item?.let { tileMorph(key, it.art, it.color) }
    }
    OverlayPanel(current != null, onClose = { FolderPopup.back() }, morph = morph) {
        val r = shown ?: return@OverlayPanel
        val (title, color) = when (r) {
            is Route.System -> listOfNotNull(Libraries.byId(r.platformId)?.platform?.name, r.folder).joinToString("  ›  ") to SystemColors.of(r.platformId)
            is Route.Collection -> (Collections.byId(r.id)?.title.orEmpty()) to (Collections.byId(r.id)?.color ?: Palette.Lavender)
            Route.Genres -> "Genres" to Palette.Sky
            else -> "" to Palette.Lavender
        }
        val look = Store.settings.look
        val header = rememberHeaderFocus()
        val smart = SmartFolders.folderOf(r)
        FolderSheet(title, null, color, onBack = { FolderPopup.back() }, art = SmartFolders.art(r) ?: folderArtFor(r), header = header, actions = {
            // Sort and view, reachable with Up from the first row (or a tap): Up lands on the one above (Focus.kt).
            // A smart folder keeps its own sort (Y › Edit rules…).
            if (r != Route.Genres) {
                if (smart == null) FocusCard(
                    Modifier.height(36.dp).headerControl(header, "sort"), glow = Palette.Sky, shape = RoundedCornerShape(50),
                    onClick = { Store.updateSettings { it.copy(sort = io.github.zahakj.tandem.data.SortOrder.entries[(it.sort.ordinal + 1) % io.github.zahakj.tandem.data.SortOrder.entries.size]) } },
                ) {
                    Row(Modifier.padding(horizontal = 12.dp).align(Alignment.Center), verticalAlignment = Alignment.CenterVertically) {
                        GlyphIcon(Glyph.Sort, Palette.Text, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(Store.settings.sort.label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                    }
                }
                if (smart == null) Spacer(Modifier.width(8.dp))
                FocusCard(
                    Modifier.size(36.dp).headerControl(header, "view"), glow = Palette.Sky, shape = RoundedCornerShape(50),
                    onClick = { Store.updateSettings { it.copy(look = it.look.copy(gameView = if (it.look.gameView == "list") "grid" else "list")) } },
                ) {
                    GlyphIcon(if (look.gameView == "list") Glyph.Grid else Glyph.Sort, Palette.Text, Modifier.padding(8.dp))
                }
            }
        }) {
            when (r) {
                is Route.System -> SystemScreen(r)
                is Route.Collection -> CollectionScreen(r)
                Route.Genres -> GenresScreen()
                else -> {}
            }
        }
    }
}

// --- What the home layouts show -----------------------------------------------------------------------------

/** One thing on the home screen: a system, a collection, the genres, or the game you were last playing. */
data class HomeEntry(
    val key: String,
    val title: String,
    val subtitle: String,
    val short: String,
    val color: Color,
    val games: List<Game>,
    val hero: File?,
    val selected: Selected,
    val game: Game? = null,
    val open: () -> Unit,
)

private fun heroOf(games: List<Game>): File? {
    // Prefer the most recently played game's art, then any game with a hero or screenshot.
    val ordered = games.sortedByDescending { it.meta.lastPlayed }
    for (g in ordered.take(40)) {
        val lib = Libraries.of(g) ?: continue
        (lib.hero(g) ?: lib.screenshot(g))?.let { return it }
    }
    return null
}

@Composable
private fun homeEntries(libs: List<PlatformLibrary>): List<HomeEntry> {
    val ctx = LocalContext.current
    val look = Store.settings.look
    val continuing = Collections.continuePlaying()
    val collections = Collections.home()
    val genres = Collections.genres()
    // Hiding or unhiding a game changes counts, art and which systems show, so those are keys too.
    return remember(libs, continuing, collections, genres.size, look.homeSections, Store.meta, io.github.zahakj.tandem.Privacy.view, LibraryScan.scanning) {
        buildList {
            for (section in look.homeSections.ifEmpty { listOf("systems") }) when (section) {
                "continue" -> continuing.firstOrNull()?.let { g ->
                    val lib = Libraries.of(g)
                    add(
                        HomeEntry(
                            "c:${g.key}", g.title, "Continue · ${g.platform.name}", "▶", Palette.Mint, listOf(g),
                            lib?.let { it.hero(g) ?: it.screenshot(g) }, Selected.GameItem(g), game = g,
                        ) { Launcher.launch(ctx, g) },
                    )
                }
                "systems" -> libs.forEach { lib ->
                    val games = Collections.visible(lib.games)
                    // Systems whose games are all hidden stay out of sight, as on Shelves.
                    if (games.isEmpty()) return@forEach
                    add(
                        HomeEntry(
                            "s:${lib.platform.id}", lib.platform.name, plural(games.size, "game"), lib.platform.shortName,
                            Palette.forName(lib.platform.id), games, heroOf(games), Selected.System(lib),
                        ) { Nav.go(Route.System(lib.platform.id)) },
                    )
                }
                "collections" -> {
                    collections.forEach { c ->
                        add(
                            HomeEntry(
                                "col:${c.id}", c.title, plural(c.games.size, "game"), c.title.take(1), c.color, c.games, heroOf(c.games),
                                Selected.Group(c.title, "Collection", c.games, c.color, GroupTarget.Collection(c.id)),
                            ) { openFolder(Route.Collection(c.id)) },
                        )
                    }
                    if (genres.isNotEmpty()) {
                        val all = genres.values.flatten().distinct()
                        add(
                            HomeEntry(
                                "genres", "Genres", "${genres.size} genres", "G", Palette.Sky, all, heroOf(all),
                                Selected.Group("Genres", "${genres.size} genres", all, Palette.Sky, GroupTarget.Genres),
                            ) { Nav.go(Route.Genres) },
                        )
                    }
                }
            }
            // Nothing to show: one entry that says what to do, so the layout always has something to highlight.
            if (isEmpty()) {
                val hidden = libs.any { it.games.isNotEmpty() }
                val title = if (hidden) "Every game is hidden" else "No games yet"
                val detail = if (hidden) "Settings › Library › Show hidden games" else "Put games in ROMs/<system>, then A to rescan"
                add(
                    HomeEntry(
                        "empty:rescan", title, detail, "?", Palette.Peach, emptyList(), null,
                        Selected.Setting(title, if (hidden) "Hidden" else "Rescan", detail, Palette.Peach, if (hidden) "Settings" else "Rescan"),
                    ) { if (hidden) Nav.go(Route.SettingsPage("library")) else LibraryScan.rescan(ctx) },
                )
            }
        }
    }
}

/** Every home layout except Shelves (which lives in Screens.kt). */
@Composable
fun HomeLayout(libs: List<PlatformLibrary>) {
    // The icon grid lives in IconGrid.kt.
    if (Store.settings.look.homeLayout == "grid") return IconGridHome(libs)
    val entries = homeEntries(libs)
    when (Store.settings.look.homeLayout) {
        "carousel" -> HomeCarousel(entries)
        "list" -> HomeList(entries)
        else -> HomeConsole(entries)
    }
}

private fun covers(games: List<Game>, n: Int = 3): List<File> = games.asSequence().mapNotNull { g -> Libraries.of(g)?.box(g) }.take(n).toList()

/** A home tile: a game tile for "continue", a folder-style tile for everything else. */
@Composable
private fun EntryTile(e: HomeEntry, modifier: Modifier, onFocus: () -> Unit) {
    val g = e.game
    if (g != null) {
        GameCard(g, modifier, onFocus = onFocus, onClick = e.open, showTitle = false)
    } else {
        GroupCard(e.title, e.games, e.color, modifier, onFocus = onFocus, onClick = e.open)
    }
}

// --- Carousel -----------------------------------------------------------------------------------------------

/** One card in a coverflow. */
data class FlowItem(
    val key: String,
    val title: String,
    val subtitle: String,
    val color: Color,
    val selected: Selected,
    val game: Game? = null,
    val games: List<Game> = emptyList(),
    val open: () -> Unit,
)

/**
 * A coverflow: the highlighted card sits big in the middle, its neighbours turn away and shrink to the sides.
 * All the 3D work happens in graphics layers that read the scroll position, so moving costs no recomposition.
 */
@Composable
fun Coverflow(routeKey: String, items: List<FlowItem>, cardWidth: Dp, cardHeight: Dp, modifier: Modifier = Modifier) {
    val start = remember(routeKey) { items.indexOfFirst { it.key == FocusMemory.get(routeKey) }.coerceAtLeast(0) }
    val state = rememberLazyListState(initialFirstVisibleItemIndex = start)
    val scope = rememberCoroutineScope()
    val still = Motion.style == AnimationStyle.Off
    // A jump (L2 / R2, Surprise me) moves the remembered item; bring it to the middle so it can take the focus.
    androidx.compose.runtime.LaunchedEffect(Nav.focusTick) {
        val i = items.indexOfFirst { it.key == FocusMemory.get(routeKey) }
        if (i >= 0 && state.layoutInfo.visibleItemsInfo.none { it.index == i }) state.scrollToItem(i)
    }
    BoxWithConstraints(modifier) {
        val side = ((maxWidth - cardWidth) / 2).coerceAtLeast(0.dp)
        LazyRow(
            Modifier.fillMaxSize().fadingEdges(state, horizontal = true, length = 48.dp).focusRow(),
            state = state,
            contentPadding = PaddingValues(horizontal = side, vertical = FocusRoom),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            itemsIndexed(items, key = { _, it -> it.key }) { i, item ->
                val layer = Modifier.graphicsLayer {
                    val info = state.layoutInfo
                    val me = info.visibleItemsInfo.firstOrNull { it.index == i } ?: return@graphicsLayer
                    val center = (info.viewportStartOffset + info.viewportEndOffset) / 2f
                    val d = ((me.offset + me.size / 2f - center) / me.size).coerceIn(-3f, 3f)
                    val a = abs(d)
                    rotationY = (-d * 38f).coerceIn(-55f, 55f)
                    val s = (1f - 0.14f * a).coerceAtLeast(0.66f)
                    scaleX = s; scaleY = s
                    translationX = -d * size.width * 0.14f
                    alpha = (1f - 0.22f * a).coerceAtLeast(0.25f)
                    cameraDistance = 14f * density
                }
                Box(layer) {
                    val focusMe = Modifier.size(cardWidth, cardHeight).remembersFocus(routeKey, item.key, i == start)
                    val onFocus = {
                        FocusMemory.remember(routeKey, item.key)
                        io.github.zahakj.tandem.Selection.current = item.selected
                        scope.launch { if (still) state.scrollToItem(i) else state.animateScrollToItem(i) }
                        Unit
                    }
                    val g = item.game
                    if (g != null) GameCard(g, focusMe, onFocus = onFocus, onClick = item.open, showTitle = false)
                    else GroupCard(item.title, item.games, item.color, focusMe, onFocus = onFocus, onClick = item.open)
                }
            }
        }
    }
}

/** The name and details of the centred card, under a coverflow. */
@Composable
private fun FlowCaption(title: String, subtitle: String, color: Color) {
    AnimatedContent(title to subtitle, transitionSpec = { fadeIn(Motion.fade(200)) togetherWith fadeOut(Motion.fade(120)) }, label = "caption") { (t, s) ->
        Column(Modifier.fillMaxWidth().riseIn(t, 0, 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(t, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            Text(s, style = MaterialTheme.typography.titleSmall, color = color, maxLines = 1)
        }
    }
}

@Composable
private fun HomeCarousel(entries: List<HomeEntry>) {
    val items = entries.map { e -> FlowItem(e.key, e.title, e.subtitle, e.color, e.selected, e.game, e.games, e.open) }
    val current = entries.firstOrNull { it.key == FocusMemory.get(Route.Home.key) } ?: entries.firstOrNull()
    Column(Modifier.fillMaxSize()) {
        if (Store.settings.look.dockTop) DockBar()
        if (Store.settings.look.nowPlaying) nowPlaying()?.let { NowPlayingCard(it) }
        Coverflow(Route.Home.key, items, 190.dp, 190.dp, Modifier.fillMaxWidth().weight(1f))
        // Read Selection so the caption follows focus.
        val sel = io.github.zahakj.tandem.Selection.current
        val shown = entries.firstOrNull { it.selected == sel } ?: current
        if (shown != null) FlowCaption(shown.title, shown.subtitle, shown.color)
        Spacer(Modifier.height(10.dp))
        if (!Store.settings.look.dockTop) DockBar() else Spacer(Modifier.height(18.dp))
    }
}

// --- List ---------------------------------------------------------------------------------------------------

/** One row in a list layout: small art, a name, a line of details and a value on the right. */
@Composable
private fun ListRow(
    title: String, detail: String, value: String?, color: Color, modifier: Modifier,
    art: @Composable () -> Unit, onFocus: () -> Unit, onClick: () -> Unit,
) {
    FocusCard(modifier.fillMaxWidth().height(58.dp), glow = color, shape = RoundedCornerShape(16.dp), onFocus = onFocus, entry = true, onClick = onClick) {
        Row(Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp))) { art() }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(detail, style = MaterialTheme.typography.labelMedium, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (value != null) Pill(value, color)
        }
    }
}

@Composable
private fun EntryArt(e: HomeEntry) {
    val g = e.game
    if (g != null) { GameArt(g); return }
    val cover = remember(e.key) { covers(e.games, 1).firstOrNull() }
    Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(e.color, e.color.copy(alpha = 0.4f)))), contentAlignment = Alignment.Center) {
        if (cover != null) AsyncImage(cover, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Text(e.short.take(3), style = MaterialTheme.typography.labelLarge, color = Palette.OnAccent)
    }
}

@Composable
private fun HomeList(entries: List<HomeEntry>) {
    val route = Route.Home.key
    var focusedKey by remember { mutableStateOf(FocusMemory.get(route) ?: entries.firstOrNull()?.key) }
    val focused = entries.firstOrNull { it.key == focusedKey } ?: entries.firstOrNull()
    val listState = rememberLazyListState()
    Row(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
        LazyColumn(
            Modifier.weight(1.1f).fillMaxHeight().fadingEdges(listState),
            state = listState,
            contentPadding = PaddingValues(start = FocusRoomSide, end = FocusRoomSide, top = FocusRoom, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (Store.settings.look.nowPlaying) nowPlaying()?.let { r -> item("now") { NowPlayingCard(r) } }
            itemsIndexed(entries, key = { _, e -> e.key }) { i, e ->
                ListRow(
                    e.title, e.subtitle, e.short.takeIf { e.game == null && e.short.length > 1 }, e.color,
                    Modifier.remembersFocus(route, e.key, i == 0), art = { EntryArt(e) },
                    onFocus = { FocusMemory.remember(route, e.key); focusedKey = e.key; io.github.zahakj.tandem.Selection.current = e.selected },
                    onClick = e.open,
                )
            }
            item("dock") { DockBar(inList = true) }
        }
        Spacer(Modifier.width(16.dp))
        Box(Modifier.weight(0.9f).fillMaxHeight().padding(top = FocusRoom, bottom = 22.dp, end = 8.dp).clip(RoundedCornerShape(24.dp))) {
            if (focused != null) EntryPreview(focused)
        }
    }
}

/** A big preview of a home entry: its art, a fan of covers and its name. */
@Composable
private fun EntryPreview(e: HomeEntry) {
    AnimatedContent(e, transitionSpec = { fadeIn(Motion.fade(260)) togetherWith fadeOut(Motion.fade(160)) }, label = "preview") { entry ->
        Box(Modifier.fillMaxSize().background(Palette.Card.copy(alpha = 0.7f))) {
            if (entry.hero != null) KenBurns(entry.hero, Modifier.fillMaxSize().fadeMask(0.25f to 1f, 1f to 0f), moving = Store.settings.look.kenBurns)
            Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(18.dp)) {
                if (entry.game == null) CoverFan(remember(entry.key) { covers(entry.games) }, spread = 1.2f, modifier = Modifier.fillMaxWidth().height(110.dp).riseIn(entry.key).floating(entry.key.hashCode(), bob = 4.dp))
                Spacer(Modifier.height(10.dp))
                Text(entry.title, Modifier.riseIn(entry.key, 1, 14.dp), style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(entry.subtitle, style = MaterialTheme.typography.titleSmall, color = entry.color, maxLines = 1)
            }
        }
    }
}

// --- Console ------------------------------------------------------------------------------------------------

/**
 * Console home, like a TV console's dashboard: the highlighted item's art fills the screen, its name sits large on
 * the left, and one row of big tiles runs along the bottom.
 */
@Composable
private fun HomeConsole(entries: List<HomeEntry>) {
    val route = Route.Home.key
    var focusedKey by remember { mutableStateOf(FocusMemory.get(route) ?: entries.firstOrNull()?.key) }
    val focused = entries.firstOrNull { it.key == focusedKey } ?: entries.firstOrNull()
    Box(Modifier.fillMaxSize()) {
        if (focused != null) {
            AnimatedContent(focused, Modifier.fillMaxSize(), transitionSpec = { fadeIn(Motion.fade(450)) togetherWith fadeOut(Motion.fade(300)) }, label = "console-bg") { e ->
                Box(Modifier.fillMaxSize()) {
                    // The art fades into the backdrop (bottom, and the left where the name sits) rather than under dark bands.
                    if (e.hero != null) {
                        Box(Modifier.fillMaxSize().fadeMask(0f to 0.85f, 0.55f to 0.55f, 1f to 0f).fadeMaskHorizontal(0f to 0.35f, 0.55f to 1f)) {
                            KenBurns(e.hero, Modifier.fillMaxSize(), moving = Store.settings.look.kenBurns)
                        }
                    }
                }
            }
        }
        Column(Modifier.fillMaxSize()) {
            if (Store.settings.look.dockTop) DockBar()
            Spacer(Modifier.weight(1f))
            if (focused != null) {
                Column(Modifier.padding(start = 30.dp, end = 30.dp, bottom = 4.dp).riseIn(focused.key, 0, 16.dp).floating(focused.key.hashCode(), bob = 3.dp, tilt = 0f)) {
                    Text(focused.subtitle.uppercase(), style = MaterialTheme.typography.labelLarge, color = focused.color, maxLines = 1)
                    Text(focused.title, style = MaterialTheme.typography.displaySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            val start = remember { entries.indexOfFirst { it.key == FocusMemory.get(route) }.coerceAtLeast(0) }
            val rowState = rememberLazyListState(initialFirstVisibleItemIndex = start)
            LazyRow(
                Modifier.fadingEdges(rowState, horizontal = true).focusRow(),
                state = rowState,
                contentPadding = PaddingValues(horizontal = 30.dp, vertical = FocusRoom),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                itemsIndexed(entries, key = { _, e -> e.key }) { i, e ->
                    EntryTile(e, Modifier.size(132.dp).remembersFocus(route, e.key, i == start)) {
                        FocusMemory.remember(route, e.key); focusedKey = e.key; io.github.zahakj.tandem.Selection.current = e.selected
                    }
                }
            }
            if (!Store.settings.look.dockTop) DockBar() else Spacer(Modifier.height(20.dp))
        }
    }
}

// --- Game views ---------------------------------------------------------------------------------------------

/** A system or collection as a list: compact rows on the left, the highlighted game's art and story on the right. */
@Composable
fun GameListView(routeKey: String, folders: List<GroupItem>, games: List<Game>) {
    val ctx = LocalContext.current
    var focusedGame by remember { mutableStateOf<Game?>(games.firstOrNull { it.key == FocusMemory.get(routeKey) } ?: games.firstOrNull()) }
    val start = remember(routeKey) { (folders.map { it.key } + games.map { it.key }).indexOf(FocusMemory.get(routeKey)).coerceAtLeast(0) }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = start)
    // A jump (L2 / R2, Surprise me) moves the remembered item; bring it into view so it can take the focus.
    val jumpKeys = remember(folders, games) { folders.map { it.key } + games.map { it.key } }
    KeepInView(listState, Nav.focusTick.let { jumpKeys.indexOf(FocusMemory.get(routeKey)) })
    Row(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
        LazyColumn(
            Modifier.weight(1.15f).fillMaxHeight().fadingEdges(listState),
            state = listState,
            contentPadding = PaddingValues(start = FocusRoomSide, end = FocusRoomSide, top = FocusRoom, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(folders, key = { _, f -> f.key }, contentType = { _, _ -> "folder" }) { i, f ->
                val cover = remember(f.key) { covers(f.games, 1).firstOrNull() }
                ListRow(
                    f.title, f.subtitle, "${f.games.size}", f.color, Modifier.remembersFocus(routeKey, f.key, i == 0),
                    art = {
                        Box(Modifier.fillMaxSize().background(f.color.copy(alpha = 0.6f))) {
                            if (cover != null) AsyncImage(cover, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        }
                    },
                    onFocus = { FocusMemory.remember(routeKey, f.key); focusedGame = null; io.github.zahakj.tandem.Selection.current = Selected.Group(f.title, f.subtitle, f.games, f.color, f.target) },
                    onClick = { openFolder(f.route) },
                )
            }
            itemsIndexed(games, key = { _, g -> g.key }, contentType = { _, _ -> "game" }) { i, g ->
                val m = g.meta
                val detail = listOfNotNull(g.platform.shortName, m.year?.toString(), m.genres.firstOrNull(), m.developer).joinToString(" · ")
                ListRow(
                    g.title + if (m.favorite) "  ♥" else "", detail,
                    if (m.playSeconds > 0) formatPlayTime(m.playSeconds) else null, Palette.forName(g.platform.id),
                    Modifier.remembersFocus(routeKey, g.key, folders.isEmpty() && i == 0),
                    art = { GameArt(g) },
                    onFocus = { FocusMemory.remember(routeKey, g.key); focusedGame = g; io.github.zahakj.tandem.Selection.current = Selected.GameItem(g) },
                    onClick = { Launcher.launch(ctx, g) },
                )
            }
        }
        Spacer(Modifier.width(16.dp))
        Box(Modifier.weight(0.85f).fillMaxHeight().padding(top = FocusRoom, bottom = 22.dp, end = 8.dp).clip(RoundedCornerShape(24.dp)).background(Palette.Card.copy(alpha = 0.7f))) {
            val g = focusedGame
            if (g != null) {
                AnimatedContent(g, transitionSpec = { fadeIn(Motion.fade(240)) togetherWith fadeOut(Motion.fade(140)) }, label = "list-preview") { game ->
                    Column(Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxWidth().weight(0.55f)) {
                            HeroArt(game, Modifier.fillMaxSize().heroFade())
                        }
                        Column(Modifier.fillMaxWidth().weight(0.45f).padding(horizontal = 16.dp, vertical = 10.dp)) {
                            Text(game.title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            game.meta.description?.let {
                                Spacer(Modifier.height(4.dp))
                                Text(it, style = MaterialTheme.typography.bodyMedium, color = Palette.Muted, maxLines = 4, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Pip(90.dp) }
            }
        }
    }
}

/** A system or collection as a coverflow of box art. */
@Composable
fun GameCarouselView(routeKey: String, folders: List<GroupItem>, games: List<Game>) {
    val ctx = LocalContext.current
    val items = remember(folders, games) {
        folders.map { f ->
            FlowItem(f.key, f.title, (if (f.hidden) "Hidden · " else "") + plural(f.games.size, "game"), f.color, Selected.Group(f.title, f.subtitle, f.games, f.color, f.target), games = f.games) { openFolder(f.route) }
        } + games.map { g ->
            val m = g.meta
            FlowItem(
                g.key, g.title, listOfNotNull(g.platform.shortName, m.year?.toString(), m.genres.firstOrNull()).joinToString(" · "),
                Palette.forName(g.platform.id), Selected.GameItem(g), game = g,
            ) { Launcher.launch(ctx, g) }
        }
    }
    val portrait = Store.settings.look.cardShape == "portrait"
    Column(Modifier.fillMaxSize()) {
        Coverflow(routeKey, items, if (portrait) 150.dp else 200.dp, if (portrait) 214.dp else 200.dp, Modifier.fillMaxWidth().weight(1f))
        val sel = io.github.zahakj.tandem.Selection.current
        val shown = items.firstOrNull { it.selected == sel } ?: items.firstOrNull()
        if (shown != null) FlowCaption(shown.title, shown.subtitle, shown.color)
        Spacer(Modifier.height(24.dp))
    }
}

// --- Dock ---------------------------------------------------------------------------------------------------

/** The dock's icon shape from Appearance. */
val dockIconShape: Shape
    get() = when (Store.settings.look.dockIconShape) {
        "circle" -> CircleShape
        "square" -> RoundedCornerShape(4.dp)
        else -> RoundedCornerShape(14.dp)
    }

val DockStyles = listOf("bar" to "Floating bar", "shelf" to "Shelf", "minimal" to "Icons only")

/**
 * The apps dock for the grid, carousel, list and console layouts. Styled from Appearance: a floating bar, a
 * full-width shelf, or bare icons; with or without a background; and optionally hidden until you move into it.
 */
@Composable
fun DockBar(inList: Boolean = false) {
    val ctx = LocalContext.current
    if (!HomeStore.settings.showDock) return
    val look = Store.settings.look
    val resumes = (ctx as? MainActivity)?.resumeCount?.intValue ?: 0
    // Eight apps plus All apps fill the top screen; more would run off its edge.
    val dock = remember(HomeStore.settings.dock, resumes, look.dockCount) { Dock.pinned(ctx).take(look.dockCount.coerceIn(1, 8)) }
    PrefetchAppLooks(dock)
    val route = Route.Home.key
    var inside by remember { mutableStateOf(false) }
    val hidden = look.dockAutoHide && !inside && !inList
    val iconSize by animateDpAsState(if (hidden) 22.dp else 44.dp, Motion.move(), label = "dock-size")
    val labels = look.dockStyle != "minimal" && !hidden
    val background = look.dockBackground && look.dockStyle != "minimal"
    val shape = if (look.dockStyle == "shelf" || inList) RoundedCornerShape(20.dp) else RoundedCornerShape(50)
    Box(
        Modifier.fillMaxWidth().padding(horizontal = if (inList) 0.dp else 22.dp, vertical = 8.dp),
        contentAlignment = if (look.dockStyle == "shelf" || inList) Alignment.CenterStart else Alignment.Center,
    ) {
        Row(
            Modifier
                .then(if (look.dockStyle == "shelf" || inList) Modifier.fillMaxWidth() else Modifier)
                // A background in the dock's shape, not a clip: a highlighted app grows, lifts and glows past the
                // bar's edge, and a clip would cut that off.
                .then(if (background) Modifier.background(Palette.Card.copy(alpha = if (look.material == "outlined") 0.35f else 0.72f), shape) else Modifier)
                .onFocusChanged { inside = it.hasFocus }
                .focusRow()
                .graphicsLayer { alpha = if (hidden) 0.55f else 1f }
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            dock.forEach { pkg ->
                FocusCard(
                    Modifier.remembersFocus(route, "app:$pkg", false).onFocusChanged { if (!it.isFocused && Panels.focusedApp == pkg) Panels.focusedApp = null },
                    glow = Palette.Sky, shape = RoundedCornerShape(16.dp),
                    onFocus = { FocusMemory.remember(route, "app:$pkg"); Panels.focusedApp = pkg; io.github.zahakj.tandem.Selection.current = Selected.App(pkg) },
                    entry = true,
                    onClick = { Dock.open(ctx, pkg) },
                ) {
                    Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        AppIcon(pkg, Modifier.size(iconSize).clip(dockIconShape))
                        AnimatedVisibility(labels) {
                            Text(
                                io.github.zahakj.tandem.RunningGames.appLabel(ctx, pkg), Modifier.width(60.dp).padding(top = 3.dp),
                                style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
            FocusCard(
                Modifier.remembersFocus(route, "app:*", false), glow = Palette.Sky, shape = RoundedCornerShape(16.dp),
                onFocus = { FocusMemory.remember(route, "app:*"); Panels.focusedApp = null; io.github.zahakj.tandem.Selection.current = Selected.App(Dock.ALL_APPS) },
                onClick = { Panels.openApps() },
            ) {
                Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(iconSize).clip(dockIconShape).background(Palette.Sky.copy(alpha = 0.22f)), contentAlignment = Alignment.Center) {
                        GlyphIcon(Glyph.Grid, Palette.Sky, Modifier.padding(iconSize * 0.2f))
                    }
                    AnimatedVisibility(labels) {
                        Text("All apps", Modifier.width(60.dp).padding(top = 3.dp), style = MaterialTheme.typography.labelSmall, maxLines = 1, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}
