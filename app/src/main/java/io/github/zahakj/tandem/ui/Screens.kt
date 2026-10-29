package io.github.zahakj.tandem.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.zahakj.tandem.Button
import io.github.zahakj.tandem.Buttons
import io.github.zahakj.tandem.Collections
import io.github.zahakj.tandem.Game
import io.github.zahakj.tandem.GroupTarget
import io.github.zahakj.tandem.Launcher
import io.github.zahakj.tandem.Libraries
import io.github.zahakj.tandem.Library
import io.github.zahakj.tandem.MainActivity
import io.github.zahakj.tandem.PlatformLibrary
import io.github.zahakj.tandem.HiddenFolders
import io.github.zahakj.tandem.Privacy
import io.github.zahakj.tandem.RunningGames
import io.github.zahakj.tandem.Selected
import io.github.zahakj.tandem.Selection
import io.github.zahakj.tandem.data.HomeStore
import io.github.zahakj.tandem.data.Store
import io.github.zahakj.tandem.plural
import kotlinx.coroutines.launch

private val GameSize = 104.dp

/** One row of the home screen and the focus keys of its items, in order. */
private class HomeRow(val id: String, val keys: List<String>)

@Composable
fun HomeScreen(libs: List<PlatformLibrary>) {
    // Other home layouts (grid, carousel, list, console) live in Layouts.kt; "shelves" is the one below.
    if (Store.settings.look.homeLayout != "shelves") return HomeLayout(libs)
    val ctx = LocalContext.current
    val route = Route.Home.key
    // Worked out once per change of the library, its details or what's shown, not on every recomposition.
    val meta = Store.meta
    val showHidden = Privacy.view
    val continuing = remember(libs, meta, showHidden) { Collections.continuePlaying() }
    val collections = remember(libs, meta, showHidden) { Collections.home() }
    val genres = remember(libs, meta, showHidden) { Collections.genres() }
    val genreGames = remember(genres) { genres.values.flatten().distinct() }
    // Systems whose games are all hidden stay out of sight.
    val systems = remember(libs, meta, showHidden) {
        libs.mapNotNull { lib -> Collections.visible(lib.games).takeIf { it.isNotEmpty() }?.let { lib to it } }
    }
    // Package changes, not every return: each check asks the package manager about every pinned app.
    val packages = io.github.zahakj.tandem.Emulators.changes
    val showDock = HomeStore.settings.showDock
    val dockCount = Store.settings.look.dockCount.coerceIn(1, 16)
    val dock = remember(HomeStore.settings.dock, packages, showDock, dockCount) { if (showDock) Dock.pinned(ctx).take(dockCount) else emptyList() }
    val now = if (Store.settings.look.nowPlaying) nowPlaying() else null
    // Rows in the order chosen in Appearance; rows left out of the list are hidden. Systems always stay reachable.
    val sections = Store.settings.look.homeSections.ifEmpty { listOf("systems") }

    val rows = buildList {
        if (now != null) add(HomeRow("now", listOfNotNull("now:resume", "now:more".takeIf { RunningGames.list.size > 1 })))
        if (systems.isEmpty()) add(HomeRow("empty", listOf("empty:rescan")))
        for (s in sections) {
            when (s) {
                "continue" -> if (continuing.isNotEmpty()) add(HomeRow("continue", continuing.map { "c:${it.key}" }))
                "systems" -> add(HomeRow("systems", systems.map { "s:${it.first.platform.id}" } + listOf("apps", "manage")))
                "collections" -> if (collections.isNotEmpty() || genres.isNotEmpty()) {
                    add(HomeRow("collections", collections.map { "col:${it.id}" } + listOfNotNull("genres".takeIf { genres.isNotEmpty() })))
                }
            }
            // Widgets sit under the first row (Continue playing by default).
            if (s == sections.first() && Widgets.homeVisible()) add(HomeRow("widgets", Widgets.list(WidgetArea.Top).map { "w:${it.id}" }))
        }
        if (showDock) add(HomeRow("apps", dock.map { "app:$it" } + "app:*"))
    }
    val allKeys = rows.flatMap { it.keys }
    val target = rememberFocusTarget(route, allKeys)
    val targetRow = rows.indexOfFirst { target in it.keys }
    val outer = rememberRouteListState("home")
    // Now playing and Continue playing fit together, so show both rather than scrolling Now playing away.
    fun snapIndex(i: Int) = if (i == 1 && rows.firstOrNull()?.id == "now") 0 else i
    KeepInView(outer, if (targetRow < 0) -1 else snapIndex(targetRow), room = 0.dp)
    val rowStates = HashMap<String, LazyListState>()
    for (r in rows) key(r.id) {
        val state = rememberRouteListState("home:row:${r.id}")
        rowStates[r.id] = state
        KeepInView(state, r.keys.indexOf(target))
    }

    // L1 / R1 jump a whole row up or down.
    val latestRows by rememberUpdatedState(rows)
    val latestStates by rememberUpdatedState(rowStates)
    LaunchedEffect(Unit) {
        Buttons.events.collect { b ->
            if ((b != Button.L1 && b != Button.R1) || Nav.route != Route.Home || Nav.overlayOpen || Layers.top != null) return@collect
            val rows = latestRows.filter { it.keys.isNotEmpty() }
            if (rows.isEmpty()) return@collect
            val current = FocusMemory.get(route)
            val i = rows.indexOfFirst { current in it.keys }.coerceAtLeast(0)
            val j = (i + if (b == Button.R1) 1 else -1).coerceIn(0, rows.lastIndex)
            if (j == i) return@collect
            val row = rows[j]
            val first = latestStates[row.id]?.firstVisibleItemIndex ?: 0
            FocusMemory.remember(route, row.keys[first.coerceIn(0, row.keys.lastIndex)])
            Nav.focusTick++
        }
    }
    // B on the home screen goes back to the top instead of closing Tandem.
    BackHandler(enabled = Nav.route == Route.Home && !Nav.overlayOpen) {
        FocusMemory.forget(route)
        Nav.focusTick++
    }

    val scope = rememberCoroutineScope()
    // Room above and below each row, so a focused card can grow and glow without being clipped.
    val rowPadding = PaddingValues(horizontal = 22.dp, vertical = FocusRoom)

    @Composable
    fun Section(id: String, title: String?, color: Color, content: @Composable () -> Unit) {
        val index = rows.indexOfFirst { it.id == id }
        // When a row gains focus the list snaps it to the top, so its heading and the whole row are in sight.
        Column(Modifier.onFocusChanged { if (it.hasFocus && index >= 0) scope.launch { runCatching { outer.animateScrollToItem(snapIndex(index)) } } }) {
            if (title != null) SectionTitle(title, color, Modifier.padding(start = 22.dp))
            PaddedScrolling(content = content)
        }
    }

    NoAutoScrolling {
        LazyColumn(
            // Rows snap to the top; the row above peeks through the top padding and fades out, so nothing is cut.
            Modifier.fillMaxSize().fadingEdges(outer, length = 12.dp),
            state = outer,
            contentPadding = PaddingValues(top = 12.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (row in rows) when (row.id) {
                "now" -> if (now != null) item("now") {
                    Section("now", null, Palette.Mint) { NowPlayingCard(now, target) }
                }
                "empty" -> item("empty") {
                    Section("empty", null, Palette.Peach) { EmptyLibrary(libs.isNotEmpty(), target) }
                }
                "continue" -> item("continue") {
                    Section("continue", "Continue playing", Palette.Mint) {
                        LazyRow(Modifier.fadingEdges(rowStates.getValue("continue"), horizontal = true).focusRow(), state = rowStates.getValue("continue"), contentPadding = rowPadding, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            items(continuing, key = { it.key }) { g ->
                                val key = "c:${g.key}"
                                GameCard(
                                    g, Modifier.size(Grid.homeCard).takesFocus(route, key, key == target),
                                    onFocus = { Selection.current = Selected.GameItem(g) },
                                    onClick = { Launcher.launch(ctx, g) },
                                    showTitle = false,
                                )
                            }
                        }
                    }
                }
                "systems" -> item("systems") {
                    Section("systems", "Systems", Palette.Lavender) {
                        LazyRow(Modifier.fadingEdges(rowStates.getValue("systems"), horizontal = true).focusRow(), state = rowStates.getValue("systems"), contentPadding = rowPadding, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            items(systems, key = { it.first.platform.id }) { (lib, games) ->
                                val key = "s:${lib.platform.id}"
                                SystemCard(
                                    lib, Modifier.width(236.dp).height(118.dp).takesFocus(route, key, key == target),
                                    games = games,
                                    onFocus = { Selection.current = Selected.System(lib) },
                                    onClick = { Nav.go(Route.System(lib.platform.id)) },
                                )
                            }
                            item("card:apps") {
                                ShortcutCard(
                                    "Apps", "Everything installed", Palette.Mint,
                                    Modifier.width(170.dp).height(118.dp).takesFocus(route, "apps", "apps" == target),
                                    onFocus = { Selection.current = Selected.Home },
                                    onClick = { Nav.go(Route.Apps) },
                                )
                            }
                            item("card:systems") {
                                ShortcutCard(
                                    "Systems", "Emulators and folders", Palette.Butter,
                                    Modifier.width(170.dp).height(118.dp).takesFocus(route, "manage", "manage" == target),
                                    onFocus = { Selection.current = Selected.Home },
                                    onClick = { Nav.go(Route.Systems) },
                                )
                            }
                        }
                    }
                }
                "collections" -> item("collections") {
                    Section("collections", "Collections", Palette.Peach) {
                        LazyRow(Modifier.fadingEdges(rowStates.getValue("collections"), horizontal = true).focusRow(), state = rowStates.getValue("collections"), contentPadding = rowPadding, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            items(collections, key = { it.id }) { c ->
                                val key = "col:${c.id}"
                                GroupCard(
                                    c.title, c.games, c.color, Modifier.size(GameSize * 1.25f).takesFocus(route, key, key == target),
                                    onFocus = { Selection.current = Selected.Group(c.title, plural(c.games.size, "game"), c.games, c.color, GroupTarget.Collection(c.id)) },
                                    onClick = { openFolder(Route.Collection(c.id)) },
                                    art = ThemePacks.collectionIcon(c.id),
                                )
                            }
                            if (genres.isNotEmpty()) item("genres") {
                                GroupCard(
                                    "Genres", genreGames, Palette.Sky, Modifier.size(GameSize * 1.25f).takesFocus(route, "genres", "genres" == target),
                                    subtitle = plural(genres.size, "genre"),
                                    onFocus = { Selection.current = Selected.Group("Genres", plural(genres.size, "genre"), genreGames, Palette.Sky, GroupTarget.Genres) },
                                    onClick = { Nav.go(Route.Genres) },
                                )
                            }
                        }
                    }
                }
                "widgets" -> item("widgets") {
                    Section("widgets", null, Palette.Butter) { HomeWidgetsRow() }
                }
                "apps" -> item("dock") {
                    Section("apps", "Apps", Palette.Sky) { DockRow(dock, target, rowStates.getValue("apps")) }
                }
            }
        }
    }
}


/** Home with no games to show: what to do next. */
@Composable
private fun EmptyLibrary(allHidden: Boolean, target: String?) {
    val ctx = LocalContext.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Pip(84.dp, mood = 2)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(if (allHidden) "Every game is hidden" else "No games yet", style = MaterialTheme.typography.titleLarge)
            Text(
                if (allHidden) "Turn on Show hidden games in Settings (Start) to see them."
                else "Put games in ROMs/<system>, like ROMs/snes or ROMs/psx, or add a folder in Systems. Then rescan.",
                style = MaterialTheme.typography.bodyMedium, color = Palette.Muted,
            )
        }
        Spacer(Modifier.width(14.dp))
        PillButton(
            if (LibraryScan.scanning) "Looking…" else "Rescan", Palette.Mint,
            Modifier.takesFocus(Route.Home.key, "empty:rescan", target == "empty:rescan"),
        ) { LibraryScan.rescan(ctx) }
    }
}

/** A folder or genre entry in a grid. */
data class GroupItem(
    val key: String, val title: String, val subtitle: String, val games: List<Game>, val color: Color, val route: Route, val target: GroupTarget? = null,
    /** A hidden folder, showing while hidden games are revealed. */
    val hidden: Boolean = false,
)

/**
 * What a grid says when it has nothing to show, and the one thing to do about it ([action] on a button that takes
 * the focus, so an empty page never leaves nothing highlighted). Without an action the button goes back.
 */
private class Empty(val title: String, val detail: String, val action: String = "Go back", val run: (android.content.Context) -> Unit = { Nav.back() })

@Composable
fun SystemScreen(route: Route.System) {
    val lib = Libraries.byId(route.platformId)
    if (lib == null) {
        EmptyState(Empty("This system has gone", "Its folder wasn't found on the last scan."))
        return
    }
    val meta = Store.meta
    val showHidden = Privacy.view
    val sort = Store.settings.sort
    val folders = remember(lib, meta, showHidden, route.folder) {
        if (route.folder != null) emptyList() else lib.groups.mapNotNull { name ->
            val games = Collections.visible(lib.gamesIn(name)).takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val hidden = HiddenFolders.isHidden(lib.platform.id, name)
            GroupItem(
                "f:$name", name, "${lib.platform.shortName} · ${if (hidden) "Hidden · " else ""}${plural(games.size, "game")}", games, Palette.forName(name),
                Route.System(lib.platform.id, name), GroupTarget.System(lib.platform.id, name), hidden,
            )
        }
    }
    // Hidden away (by its own menu, or the private-mode combo) while open: step back out rather than sit on it.
    val gone = !Privacy.canShowFolder(lib.platform.id, null) || (route.folder != null && !Privacy.canShowFolder(lib.platform.id, route.folder))
    LaunchedEffect(gone) {
        if (!gone) return@LaunchedEffect
        if (FolderPopup.route == route) FolderPopup.back() else if (Nav.route == route) Nav.back()
    }
    val games = remember(lib, meta, showHidden, route.folder, sort) { Library.sorted(Collections.visible(lib.gamesIn(route.folder)), sort) }
    val nothing = lib.gamesIn(route.folder).isEmpty() && folders.isEmpty()
    GameGrid(
        route.key, folders, games,
        if (nothing) Empty("Nothing in here", "Copy games into this folder, then rescan.", "Rescan") { LibraryScan.rescan(it) }
        else if (Privacy.enabled) Empty("Nothing to show here", "Its games are tucked away.")
        else Empty("Every game here is hidden", "Hidden games stay out of sight until you show them.", "Show hidden games") { toggleShowHidden() },
    )
}

@Composable
fun CollectionScreen(route: Route.Collection) {
    val meta = Store.meta
    val showHidden = Privacy.view
    val sort = Store.settings.sort
    val libs = Libraries.all
    val c = remember(route.id, libs, meta, showHidden) { Collections.byId(route.id) }
    // Play-based collections and smart folders keep their own order; the rest follow the chosen sort.
    val smart = route.id.startsWith(Collections.SMART)
    val ordered = route.id in ORDERED_COLLECTIONS || smart
    val games = remember(c, sort) { c?.games.orEmpty().let { if (ordered) it else Library.sorted(it, sort) } }
    val empty = if (smart) Empty("No games match", "Nothing in your library fits this folder's rules.", "Edit rules") {
        FolderPopup.close(); SmartFolderForm.edit(route.id.removePrefix(Collections.SMART))
    } else when (route.id) {
        "fav" -> Empty("No favorites yet", "Press Y on a game and pick Add to favorites.")
        "most", "continue" -> Empty("Nothing played yet", "Games you play show up here.")
        "unplayed" -> Empty("You've played them all!", "Every game has had a go. Impressive.")
        "added" -> Empty("No games yet", "New games show up here after a rescan.", "Rescan") { LibraryScan.rescan(it) }
        else -> Empty("Nothing in this genre", "Its games were hidden or removed.")
    }
    GameGrid(route.key, emptyList(), games, empty)
}

/** Collections that keep their own order instead of following "Sort by". */
val ORDERED_COLLECTIONS = setOf("continue", "most", "added")

@Composable
fun GenresScreen() {
    val meta = Store.meta
    val showHidden = Privacy.view
    val libs = Libraries.all
    val folders = remember(libs, meta, showHidden) {
        Collections.genres().map { (name, games) ->
            GroupItem(
                "g:$name", name, "Genre · ${plural(games.size, "game")}", games, Palette.forName(name),
                Route.Collection("genre:$name"), GroupTarget.Collection("genre:$name"),
            )
        }
    }
    GameGrid(
        Route.Genres.key, folders, emptyList(),
        Empty("No genres yet", "Scrape game details, or import a gamelist, and games sort themselves into genres.", "Scrape details") { ScrapeUi.open() },
    )
}

@Composable
private fun EmptyState(empty: Empty) {
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { Selection.current = Selected.Home }
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Pip(80.dp, mood = 2)
        Spacer(Modifier.height(8.dp))
        Text(empty.title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(empty.detail, style = MaterialTheme.typography.bodyMedium, color = Palette.Muted, textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(16.dp))
        // Always something to press, so the highlight never disappears on an empty page.
        PillButton(empty.action, Palette.Mint, Modifier.takesFocus("empty:${Nav.route.key}", "action", true)) { empty.run(ctx) }
        Spacer(Modifier.height(10.dp))
        ButtonHint("B", "Back", Palette.Lavender)
    }
}

@Composable
private fun GameGrid(routeKey: String, folders: List<GroupItem>, games: List<Game>, empty: Empty) {
    val ctx = LocalContext.current
    if (folders.isEmpty() && games.isEmpty()) {
        EmptyState(empty)
        return
    }
    // L2 / R2 jump by letter (or year, rating, page) through these games, in every games view.
    QuickJump.Register(routeKey, games, ordered = routeKey.removePrefix("col:") in ORDERED_COLLECTIONS)
    when (Store.settings.look.gameView) {
        "list" -> return GameListView(routeKey, folders, games)
        "carousel" -> return GameCarouselView(routeKey, folders, games)
    }
    val keys = remember(folders, games) { folders.map { it.key } + games.map { it.key } }
    val target = rememberFocusTarget(routeKey, keys)
    // Folders and games get their own headings when both are there.
    val headings = folders.isNotEmpty() && games.isNotEmpty()
    val targetIndex = keys.indexOf(target).let { i ->
        when {
            i < 0 -> -1
            !headings -> i
            i < folders.size -> i + 1
            else -> i + 2
        }
    }
    val state = rememberRouteGridState(routeKey)
    KeepInView(state, targetIndex)
    val showTitles = Store.settings.showTitles
    val aspect = Grid.aspect(showTitles)
    LazyVerticalGrid(
        columns = Grid.cells(),
        // Rows scrolled under the top bar or past the bottom fade out instead of being cut in half.
        modifier = Modifier.fadingEdges(state),
        state = state,
        // Generous edges, so the focused card can grow and glow without being clipped by the grid.
        contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = FocusRoom, bottom = 40.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (headings) item("h:folders", span = { GridItemSpan(maxLineSpan) }) { SectionTitle("Folders", Palette.Lavender) }
        items(folders, key = { it.key }, contentType = { "folder" }) { f ->
            GroupCard(
                f.title, f.games, f.color,
                // Folders keep a squarer tile even in tall/wide layouts, so their name always fits, and ease into
                // place when one is hidden or revealed instead of jumping.
                Modifier.animateItem().aspectRatio(aspect.coerceIn(0.8f, 1.2f)).takesFocus(routeKey, f.key, f.key == target),
                onFocus = { Selection.current = Selected.Group(f.title, f.subtitle, f.games, f.color, f.target) },
                onClick = { openFolder(f.route) },
                hidden = f.hidden,
            )
        }
        if (headings) item("h:games", span = { GridItemSpan(maxLineSpan) }) { SectionTitle("Games", Palette.Mint, Modifier.padding(top = 6.dp)) }
        items(games, key = { it.key }, contentType = { "game" }) { g ->
            GameCard(
                g, Modifier.aspectRatio(aspect).takesFocus(routeKey, g.key, g.key == target),
                onFocus = { Selection.current = Selected.GameItem(g) },
                onClick = { Launcher.launch(ctx, g) },
            )
        }
    }
}
