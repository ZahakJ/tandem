package io.github.zahakj.tandem.ui

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.zahakj.tandem.AndroidApps
import io.github.zahakj.tandem.Button
import io.github.zahakj.tandem.Buttons
import io.github.zahakj.tandem.Emulators
import io.github.zahakj.tandem.LaunchDisplay
import io.github.zahakj.tandem.Launcher
import io.github.zahakj.tandem.Libraries
import io.github.zahakj.tandem.Selected
import io.github.zahakj.tandem.Selection
import io.github.zahakj.tandem.data.AppPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val IconSize = 56.dp

/**
 * Every app with a launcher icon, in a searchable grid. A opens, Y opens the app menu (AppActionMenu.kt: Treat as
 * game, which screen, name, icon...), X searches. Icons load lazily, only for tiles on screen, at the size they're
 * drawn.
 */
@Composable
fun AppsScreen() {
    val ctx = LocalContext.current
    val changes = Emulators.changes
    // Read again after a change in the app menu (a name, Treat as game) and when the library changes.
    val prefs = AppPrefs.data
    val libs = Libraries.all
    val apps by produceState<List<AndroidApps.App>?>(null, changes, prefs, libs) {
        value = withContext(Dispatchers.IO) { AndroidApps.list(ctx) }
    }
    var query by remember { mutableStateOf("") }
    var gamesOnly by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf<AndroidApps.App?>(null) }
    val search = { TextEntry.ask("Search apps", query, onChange = { query = it.trim() }) { query = it.trim() } }

    LaunchedEffect(Unit) {
        Buttons.events.collect { b ->
            if (Nav.route != Route.Apps || Nav.openPanels > 0 || Panels.anyOpen || TextEntry.request != null || LaunchFx.busy) return@collect
            when (b) {
                Button.X -> search()
                Button.Y -> focused?.let { Panels.openAppMenu(it.packageName) }
                else -> {}
            }
        }
    }

    val list = apps
    if (list == null) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Pip(80.dp, mood = 1)
            Text("Finding your apps…", style = MaterialTheme.typography.titleMedium, color = Palette.Muted)
        }
        return
    }
    val q = query.lowercase()
    val shown = list.filter { (!gamesOnly || it.isGame) && (q.isEmpty() || it.label.lowercase().contains(q)) }
    val routeKey = Route.Apps.key

    // Android games go through the launcher (launch animation, play time); every app opens on its own screen.
    val open: (AndroidApps.App) -> Unit = { app -> Dock.open(ctx, app.packageName) }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            FocusCard(Modifier.weight(1f).height(44.dp).remembersFocus(routeKey, "search", false), glow = Palette.Sky, shape = RoundedCornerShape(14.dp), onFocus = { focused = null; Selection.current = Selected.Home }, onClick = search) {
                Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("⌕ ", style = MaterialTheme.typography.titleLarge, color = Palette.Sky)
                    Text(query.ifEmpty { "Search apps" }, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = if (query.isEmpty()) Palette.Muted else Palette.Text, maxLines = 1)
                    ButtonHint("X", "Search", Palette.Sky)
                }
            }
            FocusCard(Modifier.height(44.dp).remembersFocus(routeKey, "filter", false), glow = Palette.Mint, shape = RoundedCornerShape(14.dp), onFocus = { focused = null; Selection.current = Selected.Home }, onClick = { gamesOnly = !gamesOnly }) {
                Row(Modifier.padding(horizontal = 14.dp).fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (gamesOnly) "Games only" else "All apps", style = MaterialTheme.typography.titleSmall)
                }
            }
            Text("${shown.size}", style = MaterialTheme.typography.titleMedium, color = Palette.Muted)
        }
        if (shown.isEmpty()) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Pip(70.dp, mood = 2)
                Text(
                    if (query.isNotEmpty()) "No apps match \"$query\"" else if (gamesOnly) "No games marked yet. Switch to All apps and press Y on a game to add it" else "No apps found",
                    Modifier.padding(horizontal = 24.dp), style = MaterialTheme.typography.titleMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            }
        } else {
            // The search and filter buttons share the screen's focus memory; the grid keeps its own place.
            val gridRoute = "$routeKey:grid"
            val keys = shown.map { it.packageName }
            val target = rememberFocusTarget(gridRoute, keys)
            val grid = rememberRouteGridState(gridRoute)
            KeepInView(grid, keys.indexOf(target))
            val gridFirst = FocusMemory.get(routeKey).let { it == null || it == gridRoute }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(104.dp),
                modifier = Modifier.fadingEdges(grid),
                state = grid,
                contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = FocusRoom, bottom = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(shown, key = { it.packageName }) { app ->
                    AppTile(
                        app, Modifier.aspectRatio(0.9f).liftOrigin().takesFocus(gridRoute, app.packageName, gridFirst && app.packageName == target),
                        onFocus = {
                            focused = app
                            FocusMemory.remember(routeKey, gridRoute)
                            Selection.current = Selected.Group(app.label, if (app.isGame) "Android game" else "Android app", emptyList(), Palette.forName(app.packageName))
                        },
                        onClick = { open(app) },
                    )
                }
            }
        }
    }
}

@Composable
private fun AppTile(app: AndroidApps.App, modifier: Modifier, onFocus: () -> Unit, onClick: () -> Unit) {
    FocusCard(modifier, glow = Palette.forName(app.packageName), onFocus = onFocus, entry = true, onClick = onClick) {
        Column(Modifier.fillMaxSize().padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            AppIcon(app.packageName, Modifier.size(IconSize))
            Spacer(Modifier.height(6.dp))
            Text(app.label, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (app.isGame) {
            Box(
                Modifier.align(Alignment.TopEnd).padding(6.dp).size(20.dp).clip(CircleShape).background(Palette.NightDeep.copy(alpha = 0.75f)),
                contentAlignment = Alignment.Center,
            ) { Text("★", color = Palette.Butter, style = MaterialTheme.typography.labelMedium) }
        }
    }
}

