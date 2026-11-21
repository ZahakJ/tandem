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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import io.github.zahakj.tandem.Button
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

/** A system with its games and its pictures (looked up off the main thread along with the games). */
internal class SystemEntry(val platform: Platform, val games: List<Game>, val icon: File?, val hero: File?, val maker: String?) {
    val id: String get() = platform.id
    val color: Color get() = SystemColors.of(platform.id)

    /** Whether a search for [q] finds it: by name, short name, maker, id or another name it goes by. */
    fun matches(q: String): Boolean =
        q.isEmpty() || listOfNotNull(platform.name, platform.shortName, platform.id, maker).any { it.contains(q, ignoreCase = true) } ||
            platform.aliases.any { it.contains(q, ignoreCase = true) }
}

/** The Systems screen's tabs, instead of three long sections one under the other. */
internal enum class SystemsTab(val label: String, val short: String) {
    Library("In your library", "Library"), Off("Turned off", "Off"), More("More systems", "More");

    val color: Color get() = when (this) { Library -> Palette.Mint; Off -> Palette.Lavender; More -> Palette.Sky }

    companion object {
        /** A system turned off is Off whatever it holds; otherwise it is in the library once it has a game. */
        fun of(e: SystemEntry): SystemsTab = when {
            SystemPrefs.data.systems[e.id]?.enabled == false -> Off
            e.games.isEmpty() -> More
            else -> Library
        }
    }
}

/**
 * Most games first, so what you play is at the start of the library; ties (every system in More has none) go by
 * maker, then name, which keeps a maker's systems together instead of scattering them through the alphabet.
 */
private val SystemOrder = compareByDescending<SystemEntry> { it.games.size }
    .thenBy { SystemMakers.rank(it.maker) }.thenBy { it.maker.orEmpty() }.thenBy { it.platform.name.lowercase() }

/** The tab you were on, kept while you're elsewhere so coming back finds it. */
private object SystemsMemory {
    var tab by mutableStateOf<SystemsTab?>(null)
}

/** The grid's place in the screen's focus memory (the search field and tabs have their own keys). */
private const val GRID = "grid"

/** The narrowest a system tile gets; the grid fits as many across as that allows. */
private val TileMin = 118.dp
private val TileGap = 14.dp
private val TilePad = 10.dp
/** Under the art: the name and the game count. */
private val TileText = 50.dp

/**
 * Systems: every system Tandem knows as a grid of tiles with its art, in three tabs (In your library, Turned off,
 * More systems) with a search over names and makers. A on a system opens its page (SystemPage.kt): its emulators,
 * the screen its games start on, what the bottom screen shows meanwhile, and its folders. X searches, L1 and R1 flip
 * the tabs.
 */
@Composable
fun SystemsScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var version by remember { mutableIntStateOf(0) }
    val packs = IconPacks.version
    // Counts leave hidden games out (a hidden system looks empty) unless they are showing. The pack art is found
    // here too, off the main thread, so no tile looks for files while it is drawn.
    val systems by produceState<List<SystemEntry>?>(null, version, io.github.zahakj.tandem.Privacy.view, packs) {
        value = withContext(Dispatchers.IO) {
            val all = Platforms.all(ctx)
            val found = Library.find(all).games
            all.map { p ->
                SystemEntry(
                    p, io.github.zahakj.tandem.Collections.visible(found[p.id].orEmpty()),
                    IconPacks.systemIcon(p.id), IconPacks.systemHero(p.id), SystemMakers.of(p.id),
                )
            }
        }
    }
    var open by remember { mutableStateOf<String?>(null) }
    var pickFor by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
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
    val byTab = remember(list, prefs) { list.groupBy { SystemsTab.of(it) }.mapValues { (_, v) -> v.sortedWith(SystemOrder) } }
    val q = query.trim()
    val found = remember(byTab, q) { SystemsTab.entries.associateWith { t -> byTab[t].orEmpty().filter { it.matches(q) } } }
    val tab = SystemsMemory.tab ?: if (byTab[SystemsTab.Library].isNullOrEmpty()) SystemsTab.More else SystemsTab.Library
    val routeKey = Route.Systems.key
    val shown = found[tab].orEmpty()

    fun choose(t: SystemsTab, moveFocus: Boolean) {
        if (t == tab) return
        SystemsMemory.tab = t
        Sounds.page()
        // From the grid, the highlight goes to the new tab's grid (or its tab, when it's empty).
        if (moveFocus) Nav.focusTick++
    }
    val search = { TextEntry.ask("Search systems or makers", query, onChange = { query = it }) { query = it } }
    // A search with nothing on this tab shows the tab that has some.
    LaunchedEffect(q) {
        if (q.isNotEmpty() && found[tab].isNullOrEmpty()) SystemsTab.entries.firstOrNull { found[it].orEmpty().isNotEmpty() }?.let { SystemsMemory.tab = it }
    }

    // X searches; L1 and R1 flip the tabs, as they turn pages elsewhere.
    InterceptButtons(true) { b ->
        val free = Nav.route == Route.Systems && !Nav.overlayOpen && Layers.top == null
        when {
            !free -> false
            b == Button.X -> { search(); true }
            b == Button.L1 || b == Button.R1 -> {
                val all = SystemsTab.entries
                choose(all[(all.indexOf(tab) + (if (b == Button.R1) 1 else -1) + all.size) % all.size], FocusMemory.get(routeKey).let { it == null || it == GRID })
                true
            }
            else -> false
        }
    }

    fun close() {
        val id = open ?: return
        open = null
        // Back lands on the tile that was opened, on whichever tab it is now (one just turned off is under Turned off).
        list.firstOrNull { it.id == id }?.let { e ->
            val t = SystemsTab.of(e)
            if (e.matches(q)) {
                SystemsMemory.tab = t
                FocusMemory.remember("$routeKey:${t.name}", id)
                FocusMemory.remember(routeKey, GRID)
            }
        }
        Nav.focusTick++
    }

    // The grid has the focus unless the search field or a tab had it last, or there is nothing in the grid.
    val memory = FocusMemory.get(routeKey)
    val inGrid = (memory == null || memory == GRID) && shown.isNotEmpty()
    val headerWant = if (inGrid) null else memory?.takeIf { it != GRID } ?: "tab:${tab.name}"
    val header: () -> Unit = { Selection.current = Selected.Home }

    Column(Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(start = 30.dp, end = 30.dp, top = 6.dp)) {
            val wide = maxWidth >= 760.dp
            Row(Modifier.fillMaxWidth().focusRow(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                SearchField(
                    query, if (wide) "Search systems or makers" else "Search", Modifier.weight(1f).takesFocus(routeKey, "search", headerWant == "search"),
                    onFocus = header, onClick = search,
                )
                SystemsTab.entries.forEach { t ->
                    FilterTab(
                        if (wide) t.label else t.short, found[t].orEmpty().size, t.color, t == tab,
                        Modifier.takesFocus(routeKey, "tab:${t.name}", headerWant == "tab:${t.name}"), onFocus = header,
                    ) { choose(t, false) }
                }
            }
        }
        if (shown.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Pip(70.dp, mood = 2)
                Spacer(Modifier.height(10.dp))
                Text(
                    when {
                        q.isNotEmpty() -> "No systems match \"$q\""
                        tab == SystemsTab.Off -> "Nothing is turned off. A system you turn off waits here, games and all"
                        tab == SystemsTab.More -> "Every system Tandem knows has games already"
                        else -> "No games yet. Open a system in More systems and add its folder"
                    },
                    style = MaterialTheme.typography.titleMedium, color = Palette.Muted, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            }
        } else {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                // Room at the sides for a highlighted tile to grow and glow without touching the screen edge.
                val side = 30.dp
                val columns = ((maxWidth - side * 2 + TileGap) / (TileMin + TileGap)).toInt().coerceIn(2, 9)
                val cell = (maxWidth - side * 2 - TileGap * (columns - 1)) / columns
                val art = cell - TilePad * 2
                // Each tab keeps its own place in the grid and its own highlighted tile.
                val gridRoute = "$routeKey:${tab.name}"
                val keys = shown.map { it.id }
                val target = rememberFocusTarget(gridRoute, keys)
                val grid = rememberRouteGridState(gridRoute)
                KeepInView(grid, keys.indexOf(target))
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    modifier = Modifier.fillMaxSize().fadingEdges(grid),
                    state = grid,
                    contentPadding = PaddingValues(start = side, end = side, top = FocusRoom, bottom = 32.dp),
                    horizontalArrangement = Arrangement.spacedBy(TileGap),
                    verticalArrangement = Arrangement.spacedBy(TileGap),
                ) {
                    items(shown, key = { it.id }) { e ->
                        SystemTile(
                            e, art, Modifier.height(art + TilePad * 2 + TileText).takesFocus(gridRoute, e.id, inGrid && e.id == target),
                            onFocus = {
                                FocusMemory.remember(routeKey, GRID)
                                Selection.current = Selected.Group(e.platform.name, plural(e.games.size, "game"), e.games, e.color)
                            },
                        ) { open = e.id }
                    }
                }
            }
        }
    }

    val shownEntry = rememberLatest(list.firstOrNull { it.id == open })
    PanelGuard(open != null)
    OverlayPanel(open != null, onClose = { close() }) {
        val e = shownEntry ?: return@OverlayPanel
        BoxWithConstraints(contentAlignment = Alignment.Center) {
            SystemPage(e, maxWidth, maxHeight, onChanged = changed, onAddFolder = { pickFor = e.id; picker.launch(null) })
        }
    }
}

/**
 * One system as a tile: its art (the icon pack's, else Tandem's drawing of the device), its name and games, and
 * on the art Off, No emulator or Bottom when they apply, kept to its corner; the emulator that plays it sits by the count.
 */
@Composable
private fun SystemTile(e: SystemEntry, art: Dp, modifier: Modifier, onFocus: () -> Unit, onClick: () -> Unit) {
    val p = e.platform
    val color = e.color
    val config = SystemPrefs.config(p.id)
    // players reads the installed list, which is Compose state, so the tile follows installs and removals.
    val default = if (p.isApps) null else p.players.firstOrNull()
    val off = !config.enabled
    FocusCard(modifier, glow = color, shape = RoundedCornerShape(20.dp), onFocus = onFocus, entry = true, onClick = onClick) {
        Column(
            Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(color.copy(alpha = 0.2f), color.copy(alpha = 0.03f)))).padding(TilePad),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.size(art)) {
                SystemArt(e, art, Modifier.graphicsLayer { alpha = if (off) 0.4f else 1f })
                Column(Modifier.align(Alignment.TopStart).padding(5.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    when {
                        off -> ArtBadge("Off", Palette.Muted)
                        !p.isApps && default == null -> ArtBadge("No emulator", Palette.Peach)
                    }
                    if (config.bottomScreen) ArtBadge("Bottom", Palette.Sky, Glyph.Screens)
                }
            }
            Spacer(Modifier.height(7.dp))
            Text(p.name, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
            // The count in a readable tone with the system's colour as a dot (some system colours are too dark to
            // read as text), and the emulator's icon beside it rather than over the logo.
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (e.games.isNotEmpty()) {
                    Box(Modifier.size(7.dp).clip(RoundedCornerShape(50)).background(color))
                    Spacer(Modifier.width(5.dp))
                }
                Text(
                    if (e.games.isEmpty()) default?.name ?: "No games yet" else plural(e.games.size, "game"),
                    Modifier.weight(1f, fill = false),
                    style = MaterialTheme.typography.labelMedium, color = if (e.games.isEmpty()) Palette.Muted else Palette.Text.copy(alpha = 0.85f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (default != null && default.component != io.github.zahakj.tandem.LaunchSpec.ANY_APP) {
                    Spacer(Modifier.width(6.dp))
                    AppIcon(default.packageName, Modifier.size(18.dp))
                }
            }
        }
    }
}

/** A small dark label over art, readable on any picture. */
@Composable
internal fun ArtBadge(text: String, color: Color, glyph: Glyph? = null) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(Palette.NightDeep.copy(alpha = 0.82f)).padding(horizontal = 7.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (glyph != null) {
            Box(Modifier.size(11.dp)) { GlyphIcon(glyph, color) }
            Spacer(Modifier.width(4.dp))
        }
        Text(text, style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1)
    }
}

/** A system's picture at [size]: the icon pack's own (as on the home grid), else Tandem's drawing of the device. */
@Composable
internal fun SystemArt(e: SystemEntry, size: Dp, modifier: Modifier = Modifier) {
    val icon = e.icon
    if (icon != null) {
        AsyncImage(
            rememberSizedRequest(icon, tilePx(size)), e.platform.name,
            modifier.size(size).clip(RoundedCornerShape(size * 0.16f)), contentScale = ContentScale.Crop,
        )
    } else {
        DrawnIcon(e.platform.shortName.ifBlank { e.platform.name }, Devices.of(e.id), e.color, size, modifier)
    }
}

/** "/storage/emulated/0/ROMs/gba" -> "ROMs/gba". */
internal fun shortPath(dir: File): String = dir.path.removePrefix(Environment.getExternalStorageDirectory().path + "/")

/** A folder picked in the system picker, as a file path (internal storage or an SD card), or null off local storage. */
fun treePath(uri: Uri): String? =
    io.github.zahakj.tandem.LibraryRoots.pathOf(uri.authority, runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull(), Environment.getExternalStorageDirectory().path)

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
                // It only works with both, so the row says which one is missing instead of claiming On.
                val needs = when {
                    !io.github.zahakj.tandem.Shell.ready() -> "Needs Shizuku"
                    io.github.zahakj.tandem.ControllerHomeService.instance == null -> "Needs the Home button service"
                    else -> null
                }
                // Moving apps, and games too, as one choice: games only ever move along with apps.
                val options = listOf("off" to "Off", "apps" to "Apps", "games" to "Apps and games")
                val now = if (!apps.holdBackMoves) "off" else if (apps.moveGames) "games" else "apps"
                fun step(d: Int) {
                    val v = options[(options.indexOfFirst { it.first == now } + d + options.size) % options.size].first
                    io.github.zahakj.tandem.data.AppPrefs.updateAll { it.copy(holdBackMoves = v != "off", moveGames = if (v == "off") it.moveGames else v == "games") }
                }
                SettingRow(
                    "Hold Back to move", needs ?: options.first { it.first == now }.second, Palette.Lavender, Glyph.Swap,
                    "Hold Back for a moment while an app is open and it moves to the other screen, then opens there from then on. Hold it again soon after to undo. " +
                        "Apps and games: a game in front moves too; with Apps, a game always stays where it is. Needs Shizuku and the Controller Home button service (Controls).",
                    onLeft = if (needs != null) null else ({ step(-1) }), onRight = if (needs != null) null else ({ step(1) }),
                    keywords = "move app other screen swap display back button hold shizuku move games too",
                ) {
                    if (needs != null) Toasts.show(needs.replaceFirst("Needs", "This needs")) else step(1)
                }
            },
            SettingRow(
                "Voice in clips", onOff(prefs.clipVoice), Palette.Lavender, Glyph.Volume,
                "A clip from the game's companion (Clip, on the bottom screen) takes your voice from the microphone along with the game's sound. Off records the game alone. Nothing is heard outside a clip.",
                toggle = prefs.clipVoice, keywords = "clip record video microphone mic voice sound audio",
            ) {
                SystemPrefs.update { it.copy(clipVoice = !it.clipVoice) }
            },
            SettingRow(
                "Launch animation", onOff(prefs.launchAnimation), Palette.Lavender, Glyph.Sparkle,
                "The game's art zooms up with a little flourish while its emulator starts. Off starts games straight away.",
                toggle = prefs.launchAnimation, keywords = "start transition zoom",
            ) {
                SystemPrefs.update { it.copy(launchAnimation = !it.launchAnimation) }
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

