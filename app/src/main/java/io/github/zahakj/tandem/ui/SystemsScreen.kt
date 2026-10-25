package io.github.zahakj.tandem.ui

import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.zahakj.tandem.plural
import io.github.zahakj.tandem.CocoonPlatforms
import io.github.zahakj.tandem.Emulators
import io.github.zahakj.tandem.Game
import io.github.zahakj.tandem.LaunchDisplay
import io.github.zahakj.tandem.Library
import io.github.zahakj.tandem.Platform
import io.github.zahakj.tandem.Platforms
import io.github.zahakj.tandem.Selected
import io.github.zahakj.tandem.Selection
import io.github.zahakj.tandem.data.Store
import io.github.zahakj.tandem.data.SystemPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Every system with its games, for the Systems screen. */
private class SystemInfo(val platform: Platform, val games: List<Game>)

/**
 * Systems: turn systems on and off, pick each one's default emulator (installed ones first, with a link to get the
 * rest), choose the screen its games start on, and add game folders from anywhere with the folder picker.
 */
@Composable
fun SystemsScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var version by remember { mutableIntStateOf(0) }
    // Counts leave hidden games out (a hidden system looks empty) unless they are showing.
    val systems by produceState<List<SystemInfo>?>(null, version, io.github.zahakj.tandem.Privacy.view) {
        value = withContext(Dispatchers.IO) { Platforms.all(ctx).map { SystemInfo(it, io.github.zahakj.tandem.Collections.visible(Library.gamesOf(it))) } }
    }
    var open by remember { mutableStateOf<String?>(null) }
    var pickFor by remember { mutableStateOf<String?>(null) }
    val changed: () -> Unit = { version++; scope.launch { reloadLibrary(ctx) } }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        val id = pickFor
        pickFor = null
        if (uri == null || id == null) return@rememberLauncherForActivityResult
        val path = treePath(uri)
        if (path == null) {
            Toasts.show("That folder can't be used, try one on the device or SD card")
            return@rememberLauncherForActivityResult
        }
        runCatching { ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
        SystemPrefs.update { it.copy(trees = it.trees + (path to uri.toString())) }
        SystemPrefs.updateSystem(id) { it.copy(enabled = true, folders = (it.folders + path).distinct()) }
        Toasts.show("Added ${File(path).name}, looking for games…")
        changed()
    }

    val list = systems
    if (list == null) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Pip(80.dp, mood = 1)
            Text("Checking your systems…", style = MaterialTheme.typography.titleMedium, color = Palette.Muted)
        }
        return
    }
    val prefs = SystemPrefs.data
    val mine = list.filter { it.games.isNotEmpty() && prefs.systems[it.platform.id]?.enabled != false }
    val off = list.filter { prefs.systems[it.platform.id]?.enabled == false }
    val more = list.filter { it.games.isEmpty() && prefs.systems[it.platform.id]?.enabled != false }
    val routeKey = Route.Systems.key
    val first = (mine + off + more).firstOrNull()?.platform?.id

    val listState = rememberRouteListState(routeKey)
    LazyColumn(
        Modifier.fillMaxSize().fadingEdges(listState),
        state = listState,
        contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = FocusRoom, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for ((title, group, color) in listOf(
            Triple("In your library", mine, Palette.Mint),
            Triple("Turned off", off, Palette.Lavender),
            Triple("More systems · add a folder to start", more, Palette.Sky),
        )) {
            if (group.isEmpty()) continue
            item(key = "h:$title") { SectionTitle(title, color, Modifier.padding(top = 8.dp)) }
            items(group, key = { it.platform.id }) { info ->
                SystemRow(info, Modifier.remembersFocus(routeKey, info.platform.id, info.platform.id == first)) { open = info.platform.id }
            }
        }
    }

    val shown = list.firstOrNull { it.platform.id == open }
    PanelGuard(open != null)
    OverlayPanel(open != null, onClose = { open = null; Nav.focusTick++ }) {
        if (shown != null) SystemPanel(shown, onChanged = changed, onAddFolder = { pickFor = shown.platform.id; picker.launch(null) })
    }
}

@Composable
private fun SystemRow(info: SystemInfo, modifier: Modifier, onClick: () -> Unit) {
    val p = info.platform
    val color = Palette.forName(p.id)
    val config = SystemPrefs.config(p.id)
    // players reads the installed list, which is Compose state, so this row follows installs and removals.
    val players = p.players
    val default = players.firstOrNull()
    FocusCard(
        modifier.fillMaxWidth(), glow = color,
        onFocus = {
            FocusMemory.remember(Route.Systems.key, p.id)
            Selection.current = Selected.Group(p.name, plural(info.games.size, "game"), info.games, color)
        },
        entry = true,
        onClick = onClick,
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(width = 74.dp, height = 40.dp).clip(RoundedCornerShape(12.dp))
                    .background(Brush.linearGradient(listOf(color, color.copy(alpha = 0.45f)))),
                contentAlignment = Alignment.Center,
            ) { Text(p.shortName, style = MaterialTheme.typography.titleSmall, color = Palette.NightDeep, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(p.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    when {
                        p.isApps -> "Installed games and apps you add"
                        default != null -> "Plays with ${default.name}"
                        p.allPlayers.isEmpty() -> "No emulator listed"
                        else -> "Needs ${p.allPlayers.first().name}"
                    },
                    style = MaterialTheme.typography.bodySmall, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (config.bottomScreen) Pill("Bottom ↓", Palette.Sky)
                if (!config.enabled) Pill("Off", Palette.Muted)
                else if (default == null && !p.isApps) Pill("No emulator", Palette.Peach)
                Pill(plural(info.games.size, "game"), color)
            }
        }
    }
}

/** The popup for one system. */
@Composable
private fun SystemPanel(info: SystemInfo, onChanged: () -> Unit, onAddFolder: () -> Unit) {
    val ctx = LocalContext.current
    val p = info.platform
    val color = Palette.forName(p.id)
    val config = SystemPrefs.config(p.id)
    val routeKey = "system:${p.id}"
    val players = p.players
    val default = players.firstOrNull()
    val hasSecond = Facts.run { ensure(ctx); hasSecondScreen }
    Column(
        Modifier.width(620.dp).background(Palette.Card, RoundedCornerShape(26.dp)).padding(horizontal = 8.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(width = 86.dp, height = 46.dp).clip(RoundedCornerShape(14.dp))
                    .background(Brush.linearGradient(listOf(color, color.copy(alpha = 0.45f)))),
                contentAlignment = Alignment.Center,
            ) { Text(p.shortName, style = MaterialTheme.typography.titleMedium, color = Palette.NightDeep, maxLines = 1) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(p.name, style = MaterialTheme.typography.titleLarge)
                Text(plural(info.games.size, "game"), style = MaterialTheme.typography.bodyMedium, color = color)
            }
        }
        // Fits the screen (the popup room caps the panel), scrolls with soft ends, and leaves room for the highlight.
        val rows = rememberLazyListState()
        LazyColumn(
            Modifier.weight(1f, fill = false).heightIn(max = 360.dp).fadingEdges(rows), state = rows,
            contentPadding = PaddingValues(horizontal = FocusRoomSide, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            item("show") {
                MenuRow("Show in library", onOff(config.enabled), "show", true, routeKey, Palette.Mint) {
                    SystemPrefs.updateSystem(p.id) { it.copy(enabled = !it.enabled) }
                    Toasts.show(if (config.enabled) "${p.shortName} is hidden from your library" else "${p.shortName} is back")
                    onChanged()
                }
            }
            item("screen") {
                MenuRow("Start games on", if (config.bottomScreen) "Bottom screen" else "Top screen", "screen", false, routeKey, Palette.Sky) {
                    if (!hasSecond) {
                        Toasts.show("I can only find one screen")
                    } else {
                        SystemPrefs.updateSystem(p.id) { it.copy(bottomScreen = !it.bottomScreen) }
                        if (SystemPrefs.data.singleScreen) Toasts.show("Single screen mode is on in Settings, so games stay on top")
                    }
                }
            }
            item("companion") {
                // What the bottom screen shows while one of these games plays on the top one (GameCompanion.kt).
                val mode = io.github.zahakj.tandem.GameCompanion.systemMode(p)
                MenuRow("Bottom screen while playing", io.github.zahakj.tandem.GameCompanion.label(mode), "companion", false, routeKey, Palette.Lavender) {
                    SystemPrefs.updateSystem(p.id) { it.copy(companion = io.github.zahakj.tandem.GameCompanion.next(mode)) }
                }
            }
            if (!p.isApps) {
                item("h:emu") { SectionTitle("Emulators", Palette.Butter, Modifier.padding(top = 6.dp)) }
                // Installed first; keyed by position, since imported players may share a name.
                itemsIndexed(p.allPlayers.sortedBy { if (it in players) 0 else 1 }, key = { i, _ -> "emu:$i" }) { _, spec ->
                    val isInstalled = spec in players
                    val label = if (spec.origin != null && !spec.name.contains(spec.origin)) "${spec.name} · ${spec.origin}" else spec.name
                    val value = when {
                        spec == default -> "Default ✓"
                        isInstalled -> "Make default"
                        else -> "Get it"
                    }
                    MenuRow(label, value, "emu:${spec.name}", false, routeKey, if (isInstalled) Palette.Mint else Palette.Peach) {
                        when {
                            spec == default -> Toasts.show("${spec.name} already plays ${p.shortName} games")
                            isInstalled -> {
                                SystemPrefs.updateSystem(p.id) { it.copy(player = spec.name) }
                                Toasts.show("${spec.name} now plays ${p.shortName} games")
                            }
                            else -> if (!Emulators.openStore(ctx, spec)) Toasts.show("Couldn't open a store for ${spec.name}")
                        }
                    }
                }
                item("h:folders") { SectionTitle("Folders", Palette.Lavender, Modifier.padding(top = 6.dp)) }
                items(p.folders, key = { "f:$it" }) { folder ->
                    val dir = Library.folder(folder)
                    val extra = folder in config.folders
                    val value = when {
                        extra -> "Remove"
                        dir.isDirectory -> "Built in"
                        else -> "Not there yet"
                    }
                    MenuRow(shortPath(dir), value, "f:$folder", false, routeKey, if (extra) Palette.Peach else Palette.Lavender) {
                        if (extra) {
                            SystemPrefs.updateSystem(p.id) { it.copy(folders = it.folders - folder) }
                            Toasts.show("Removed ${dir.name}")
                            onChanged()
                        } else {
                            Toasts.show(if (dir.isDirectory) "Games in ${shortPath(dir)} show up here" else "Make ${shortPath(dir)} and put games in it")
                        }
                    }
                }
                item("add") { MenuRow("Add a folder…", "Pick", "add", false, routeKey, Palette.Mint, onAddFolder) }
            }
        }
    }
}

/** "/storage/emulated/0/ROMs/gba" -> "ROMs/gba". */
private fun shortPath(dir: File): String = dir.path.removePrefix(Environment.getExternalStorageDirectory().path + "/")

/** A folder picked in the system picker, as a file path (internal storage or an SD card). */
private fun treePath(uri: Uri): String? {
    val id = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull() ?: return null
    val volume = id.substringBefore(':')
    val rel = id.substringAfter(':', "").trim('/')
    val base = if (volume.equals("primary", ignoreCase = true)) Environment.getExternalStorageDirectory().path else "/storage/$volume"
    return if (rel.isEmpty()) base else "$base/$rel"
}

/** Settings: systems, apps, and how games start. */
@Composable
fun systemsSettings(): SettingSection {
    val ctx = LocalContext.current
    val prefs = SystemPrefs.data
    val hasSecond = Facts.run { ensure(ctx); hasSecondScreen }
    val cocoonFiles = Facts.cocoonPlatformFiles
    return SettingSection(
        "Systems & launching",
        listOf(
            SettingRow(
                "Systems and emulators", "${io.github.zahakj.tandem.Libraries.all.orEmpty().size} systems", Palette.Sky, Glyph.Gamepad,
                "Turn systems on or off, pick each system's emulator and which screen its games start on, and add extra folders.",
                opens = true, keywords = "platforms consoles emulator core default player folders",
            ) { Nav.go(Route.Systems) },
            SettingRow(
                "Android apps", "Games & apps", Palette.Sky, Glyph.Grid,
                "Every app on the device. Mark the ones that are games so they show up in the Android Games system.",
                opens = true, keywords = "android games applications",
            ) { Nav.go(Route.Apps) },
            SettingRow(
                "Single screen", if (!hasSecond) "On · one screen" else onOff(prefs.singleScreen), Palette.Lavender, Glyph.Screens,
                "Leave the second screen alone (or turned off) and use only the top one. Games always start on the top screen too.",
                toggle = if (!hasSecond) null else prefs.singleScreen, keywords = "one screen dual bottom off",
            ) {
                if (!hasSecond) {
                    Toasts.show("Only one screen found, so games always start on it")
                } else {
                    SystemPrefs.update { it.copy(singleScreen = !it.singleScreen) }
                    // The same switch as Appearance › Single screen; the bottom panel follows straight away.
                    (ctx as? io.github.zahakj.tandem.MainActivity)?.showDetails()
                    Toasts.show(if (prefs.singleScreen) "Using both screens" else "Only the top screen now; games start there too")
                }
            },
            run {
                val apps = io.github.zahakj.tandem.data.AppPrefs.data
                val all = io.github.zahakj.tandem.data.AppScreen.ALL
                fun step(d: Int) = io.github.zahakj.tandem.data.AppPrefs.updateAll {
                    it.copy(defaultScreen = all[(all.indexOf(it.defaultScreen).coerceAtLeast(0) + d + all.size) % all.size])
                }
                SettingRow(
                    "Apps open on", if (!hasSecond) "Top · one screen" else io.github.zahakj.tandem.data.AppScreen.label(apps.defaultScreen),
                    Palette.Lavender, Glyph.Screens,
                    "Where an app opens unless it has a screen of its own (Y › Always open on). Auto: the screen you start it from. A touch on the bottom screen opens it there; with the controller it opens where the library is. Games keep their own screen.",
                    onLeft = { step(-1) }, onRight = { step(1) }, keywords = "apps launch screen top bottom main second display open on discord browser",
                ) { step(1) }
            },
            run {
                val apps = io.github.zahakj.tandem.data.AppPrefs.data
                SettingRow(
                    "Hold Back to move apps", onOff(apps.holdBackMoves), Palette.Lavender, Glyph.Swap,
                    "Hold Back for a moment while an app is open and it moves to the other screen, then opens there from then on. Hold it again soon after to undo. Needs Shizuku and the Controller Home button service (Controls).",
                    toggle = apps.holdBackMoves, keywords = "move app other screen swap display back button hold shizuku",
                ) { io.github.zahakj.tandem.data.AppPrefs.updateAll { it.copy(holdBackMoves = !it.holdBackMoves) } }
            },
            run {
                val apps = io.github.zahakj.tandem.data.AppPrefs.data
                SettingRow(
                    "Move games too", onOff(apps.moveGames), Palette.Lavender, Glyph.Gamepad,
                    "Holding Back moves a game to the other screen as well. Off, a game in front always stays where it is.",
                    toggle = apps.moveGames, keywords = "move game other screen hold back",
                ) { io.github.zahakj.tandem.data.AppPrefs.updateAll { it.copy(moveGames = !it.moveGames) } }
            },
            SettingRow(
                "Launch animation", onOff(prefs.launchAnimation), Palette.Lavender, Glyph.Sparkle,
                "The game's art zooms up with a little flourish while its emulator starts. Off starts games straight away.",
                toggle = prefs.launchAnimation, keywords = "start transition zoom",
            ) {
                SystemPrefs.update { it.copy(launchAnimation = !it.launchAnimation) }
            },
            SettingRow(
                "Cocoon platform files", if (cocoonFiles > 0) "$cocoonFiles found" else "None", Palette.Lavender, Glyph.Folder,
                "Cocoon and Daijisho platform files in ROMs/platforms add their emulators to Systems, so a setup you already made keeps working.",
                keywords = "daijisho platforms json am start import",
            ) {
                Toasts.show(
                    if (cocoonFiles > 0) "Their emulators are in Systems, marked ${CocoonPlatforms.ORIGIN}"
                    else "Put Cocoon or Daijisho platform files in ROMs/platforms",
                )
            },
        ),
    )
}

/** In the Y menu: which screen this game starts on. Hidden when there is only one screen. */
@Composable
fun LaunchScreenRow(game: Game, routeKey: String) {
    val ctx = LocalContext.current
    val hasSecond = Facts.run { ensure(ctx); hasSecondScreen }
    if (!hasSecond || SystemPrefs.data.singleScreen) return
    val bottom = LaunchDisplay.wantsBottom(game)
    val systemBottom = SystemPrefs.config(game.platform.id).bottomScreen
    MenuRow("Start on", if (bottom) "Bottom screen" else "Top screen", "screen", false, routeKey, Palette.Butter) {
        val want = !bottom
        Store.update(game.key) { it.copy(screen = if (want == systemBottom) null else if (want) "bottom" else "top") }
    }
}

/** A wide card on the home screen that opens a place rather than a system (Apps, Systems). */
@Composable
fun ShortcutCard(title: String, subtitle: String, color: Color, modifier: Modifier, onFocus: () -> Unit, onClick: () -> Unit) {
    FocusCard(modifier, glow = color, onFocus = onFocus, entry = true, onClick = onClick) {
        Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(color.copy(alpha = 0.55f), Palette.Card))))
        Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.Bottom) {
            Text(title, style = MaterialTheme.typography.headlineSmall, color = Palette.Text, maxLines = 1)
            Text(subtitle, style = MaterialTheme.typography.labelLarge, color = Palette.Text.copy(alpha = 0.75f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

