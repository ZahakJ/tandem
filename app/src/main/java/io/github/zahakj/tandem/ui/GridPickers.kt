package io.github.zahakj.tandem.ui

import io.github.zahakj.tandem.Tx
import io.github.zahakj.tandem.R
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.zahakj.tandem.Button
import io.github.zahakj.tandem.Collections
import io.github.zahakj.tandem.Game
import io.github.zahakj.tandem.Libraries
import io.github.zahakj.tandem.Privacy
import io.github.zahakj.tandem.Selected
import io.github.zahakj.tandem.Selection
import io.github.zahakj.tandem.data.GridSlots
import io.github.zahakj.tandem.data.IconGridStore
import io.github.zahakj.tandem.data.Store

/*
 * Add here › Game…: every game, grouped by system, to put on the grid at an empty spot. A picks (several at once),
 * X searches with the on-screen keyboard (the list narrows as you type), Start or the Add button puts the picked
 * games in that spot and the free spots after it, in the order they were picked.
 */

private const val PICK_ROUTE = "gridpick"

/** A game's key on the grid. */
private fun gameKey(g: Game) = "game:${g.key}"

@Composable
internal fun GamePickPanel() {
    val ctx = LocalContext.current
    val target = IconGrid.pickingGames
    val visible = target != null
    val shownTarget = rememberLatest(target)
    PanelGuard(visible)
    var query by remember { mutableStateOf("") }
    val picked = remember { mutableStateListOf<String>() }
    LaunchedEffect(visible) { if (visible) { query = ""; picked.clear() } }
    // With no games at all, the first button picks the folder they are in instead.
    val pickFolder = rememberGameFolderPicker()
    val close: () -> Unit = { IconGrid.pickingGames = null; Nav.focusTick++ }
    val search: () -> Unit = { TextEntry.ask(Tx.s(R.string.grid_pick_find), query, onChange = { query = it }) { query = it } }
    val place: () -> Unit = place@{
        val t = shownTarget ?: return@place
        if (picked.isEmpty()) { Toasts.show(Tx.s(R.string.grid_pick_first)); Sounds.error(); return@place }
        val keys = picked.toList()
        val first = if (t.folder == null) {
            IconGridStore.addAllAt(keys, homeShown(), t.index)
        } else {
            val s = IconGrid.folderShown?.takeIf { IconGrid.openFolder == t.folder } ?: return@place
            Libraries.games.firstOrNull { gameKey(it) == keys.first() }?.let { g -> IconGrid.menuData?.first?.item(gameKey(g)) }?.let { suggestForFirst(ctx, t.folder, it) }
            IconGridStore.addAllToFolder(keys, t.folder, s, t.index)
        }
        if (first != null) FocusMemory.remember(if (t.folder == null) Route.Home.key else IconGrid.folderRoute(t.folder), first)
        Sounds.confirm()
        Toasts.show(Tx.p(R.plurals.grid_pick_added, keys.size))
        close()
    }
    InterceptButtons(visible && TextEntry.request == null) { b ->
        when (b) {
            Button.X -> search()
            Button.Start -> place()
            else -> Unit
        }
        true
    }
    OverlayPanel(visible, onClose = close) {
        if (shownTarget == null) return@OverlayPanel
        val meta = Store.meta
        val libs = Libraries.all
        val hiddenView = Privacy.view
        val config = IconGridStore.config
        // Games already where they would go (on the home grid, or in this folder) show faded; they can't go in twice.
        // A folder holds shortcuts, so a game on the home grid or in another folder can still be added to it.
        val into = shownTarget.folder
        val taken = remember(config, into) {
            (if (into == null) config.added else IconGridStore.folder(into)?.items.orEmpty()).filter { it.startsWith("game:") }.toHashSet()
        }
        val all = remember(libs, meta, hiddenView) { Collections.visibleGames() }
        val q = normalize(query)
        val groups = remember(all, q) {
            all.filter { q.isEmpty() || normalize(it.title).contains(q) }
                .groupBy { it.platform.id }.values.map { games -> games.first().platform.name to games.sortedBy { it.title.lowercase() } }
                .sortedBy { it.first.lowercase() }
        }
        val keys = remember(groups) { listOf("add") + groups.flatMap { (_, games) -> games.map { gameKey(it) } } }
        val focusTarget = rememberFocusTarget(PICK_ROUTE, keys)
        val grid = rememberLazyGridState()
        FolderSheet(
            Tx.s(R.string.grid_pick_title),
            if (picked.isEmpty()) groups.sumOf { it.second.size }.let { n -> if (q.isEmpty()) Tx.p(R.plurals.grid_games, n) else Tx.p(R.plurals.grid_pick_matching, n, n, query.trim()) }
            else Tx.p(R.plurals.grid_pick_picked, picked.size),
            Palette.Mint, onBack = close, art = TileArt.Symbol(Glyph.Cartridge, Palette.Mint),
            actions = {
                ButtonHint("A", Tx.s(R.string.grid_pick_hint_pick), Palette.Mint); Spacer(Modifier.width(10.dp))
                ButtonHint("X", Tx.s(R.string.grid_pick_hint_search), Palette.Sky); Spacer(Modifier.width(10.dp))
                ButtonHint("Start", Tx.s(R.string.grid_pick_hint_add), Palette.Butter)
            },
        ) {
            PaddedScrolling {
                LazyVerticalGrid(
                    GridCells.Adaptive(88.dp),
                    Modifier.fillMaxSize().fadingEdges(grid),
                    state = grid,
                    contentPadding = PaddingValues(horizontal = FocusRoomSide + 4.dp, vertical = FocusRoom),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item("add", span = { GridItemSpan(maxLineSpan) }) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            val none = Libraries.games.isEmpty()
                            PillButton(
                                when {
                                    none -> Tx.s(R.string.grid_pick_games_folder)
                                    picked.isEmpty() -> Tx.s(R.string.grid_pick_add_here)
                                    else -> Tx.p(R.plurals.grid_pick_add_n_here, picked.size)
                                },
                                Palette.Mint, Modifier.takesFocus(PICK_ROUTE, "add", focusTarget == "add"),
                            ) { if (none) pickFolder() else place() }
                            PillButton(if (q.isEmpty()) Tx.s(R.string.grid_pick_search) else Tx.s(R.string.grid_pick_searching, query.trim()), Palette.Sky, Modifier) { search() }
                            if (q.isNotEmpty()) PillButton(Tx.s(R.string.grid_pick_show_all), Palette.Lavender, Modifier) { query = "" }
                        }
                    }
                    if (groups.isEmpty()) item("none", span = { GridItemSpan(maxLineSpan) }) {
                        PipNote(
                            Tx.s(if (q.isEmpty()) R.string.grid_pick_none else R.string.grid_pick_no_matches),
                            Tx.s(if (q.isEmpty()) R.string.grid_pick_none_help else R.string.grid_pick_no_matches_help), mood = 2,
                        )
                    }
                    for ((name, games) in groups) {
                        item("h:$name", span = { GridItemSpan(maxLineSpan) }, contentType = "header") {
                            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                                Spacer(Modifier.width(8.dp))
                                Text(Tx.p(R.plurals.grid_games, games.size), style = MaterialTheme.typography.labelMedium, color = Palette.Muted, maxLines = 1)
                            }
                        }
                        items(games, key = { gameKey(it) }, contentType = { "game" }) { g ->
                            val k = gameKey(g)
                            val on = k in picked
                            val already = k in taken
                            Box(Modifier.aspectRatio(0.8f).alpha(if (already) 0.35f else 1f)) {
                                GameCard(
                                    g, Modifier.fillMaxSize().takesFocus(PICK_ROUTE, k, focusTarget == k),
                                    onFocus = { Selection.current = Selected.GameItem(g) },
                                    onClick = {
                                        when {
                                            already -> Toasts.show(Tx.s(R.string.grid_pick_already, g.title))
                                            on -> picked.remove(k)
                                            else -> picked.add(k)
                                        }
                                    },
                                    showTitle = true,
                                )
                                if (on) PickMark(picked.indexOf(k) + 1, Modifier.align(Alignment.TopEnd).padding(6.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The number of a picked game, in the order they go on the grid. */
@Composable
private fun PickMark(n: Int, modifier: Modifier) {
    Box(
        modifier.size(26.dp).graphicsLayer { shadowElevation = 6f * density; shape = CircleShape; clip = true }.background(Palette.Mint),
        contentAlignment = Alignment.Center,
    ) {
        Text("$n", style = MaterialTheme.typography.labelLarge, color = Palette.NightDeep, maxLines = 1)
    }
}
