package io.github.zahakj.tandem.ui

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import io.github.zahakj.tandem.Button
import io.github.zahakj.tandem.Libraries
import io.github.zahakj.tandem.Privacy
import io.github.zahakj.tandem.RunningGame
import io.github.zahakj.tandem.RunningGames
import io.github.zahakj.tandem.Screensaver
import io.github.zahakj.tandem.Selected
import io.github.zahakj.tandem.Selection

/** Which of this area's popups are open. Only one at a time. */
object Panels {
    var switcher by mutableStateOf(false)
    var quick by mutableStateOf(false)
    var apps by mutableStateOf(false)
    var appMenu by mutableStateOf<String?>(null)
    var homeExplainer by mutableStateOf(false)

    /** The dock or drawer app that is highlighted, for Y. */
    var focusedApp by mutableStateOf<String?>(null)
    /** The highlighted card in the game switcher, for X. */
    var focusedRunning by mutableStateOf<String?>(null)

    val anyOpen: Boolean get() = switcher || quick || apps || appMenu != null || homeExplainer || Screensaver.active || WidgetUi.popupOpen

    private fun blocked() = TextEntry.request != null

    /** Closes the library's own popups too, without handing focus back to the screen underneath. */
    private fun clearAll() {
        Nav.menuFor = null; Nav.groupMenu = false; Nav.search = false
        switcher = false; quick = false; apps = false; appMenu = null; homeExplainer = false
    }

    fun openSwitcher() {
        if (blocked()) return
        if (switcher) { close(); return }
        clearAll()
        // A fresh focus key each time, so it opens on the first game rather than one closed earlier.
        switcherGen++
        switcher = true
    }

    fun toggleQuick() {
        if (blocked()) return
        if (quick) { close(); return }
        // Always opens on the first control (brightness), not on whatever was used last time.
        FocusMemory.forget("quick")
        clearAll(); quick = true
    }

    fun openApps() { if (!blocked()) { FocusMemory.forget("apps"); clearAll(); apps = true } }
    /** The app menu (AppActionMenu.kt); over the All apps drawer it opens on top, and closing it goes back there. */
    fun openAppMenu(pkg: String) {
        if (blocked()) return
        FocusMemory.forget("appmenu:$pkg:main")
        val drawer = apps
        clearAll()
        apps = drawer
        appMenu = pkg
    }

    /** Closes the app menu only, giving focus back to what is under it (the drawer, the dock, the Apps screen). */
    fun closeAppMenu() {
        appMenu = null
        Nav.focusTick++
    }
    fun openHomeExplainer() { if (!blocked()) { FocusMemory.forget("homeexplainer"); clearAll(); homeExplainer = true } }

    /** Closes whatever is open and gives focus back to the screen. */
    fun close() {
        switcher = false; quick = false; apps = false; appMenu = null; homeExplainer = false
        Nav.focusTick++
    }
}

/** The most recent running game that may be shown (private games stay out of sight), for Now Playing. */
fun nowPlaying(): RunningGame? = RunningGames.list.firstOrNull { r -> r.game != null && Privacy.canShow(r.game) }

/** How a running game is named on screen: hidden games show only their emulator while private. */
private fun RunningGame.shownTitle(): String = game?.takeIf { Privacy.canShow(it) }?.title ?: appName

private fun RunningGame.detail(): String {
    val g = game?.takeIf { Privacy.canShow(it) }
    val played = RunningGames.sessionSeconds(this)
    return when {
        g == null -> if (game == null) "Opened outside Tandem" else "Running"
        played != null && played > 0 -> "${g.platform.shortName} · played ${formatPlayTime(played)} this time"
        else -> "${g.platform.shortName} · $appName"
    }
}

/**
 * A "press again to confirm" that expires after [WINDOW_MS]; closing an emulator loses unsaved progress. The
 * prompt (the toast passed to [press], and any "Again" label reading [armed]) lasts exactly as long as the window,
 * so a prompt on screen always means the next press confirms, and a press after it has gone starts over.
 */
class PressTwice {
    var armed by mutableStateOf<String?>(null)
        private set
    private var at = 0L
    private val expire = Runnable { armed = null }

    /** True when this is the confirming press; otherwise arms [id] and shows [prompt] for as long as it stays armed. */
    fun press(id: String, prompt: String? = null): Boolean {
        val now = System.currentTimeMillis()
        main.removeCallbacks(expire)
        if (armed == id && now - at < WINDOW_MS) { armed = null; return true }
        armed = id; at = now
        main.postDelayed(expire, WINDOW_MS)
        prompt?.let { Toasts.show(it, WINDOW_MS) }
        return false
    }

    companion object {
        const val WINDOW_MS = 4000L
        private val main = android.os.Handler(android.os.Looper.getMainLooper())
    }
}

private val switcherConfirm = PressTwice()
private var switcherGen by mutableIntStateOf(0)


fun closeRunning(ctx: Context, r: RunningGame, onDone: () -> Unit = {}) {
    var ok = false
    inBackground({ ok = RunningGames.close(ctx, r.pkg) }) {
        Toasts.show(if (ok) "Closed ${r.shownTitle()}" else "Couldn't close it. Is Shizuku running?")
        onDone()
    }
}

/** X in the switcher: close the highlighted game (press twice). */
fun switcherCloseFocused(ctx: Context) {
    val list = RunningGames.list
    val r = list.firstOrNull { it.pkg == Panels.focusedRunning } ?: return
    if (!switcherConfirm.press("one:${r.pkg}", "Press X again to close ${r.shownTitle()}")) return
    // Land on the neighbour once this card is gone.
    val i = list.indexOf(r)
    val next = (list.getOrNull(i + 1) ?: list.getOrNull(i - 1))?.pkg
    closeRunning(ctx, r) {
        if (RunningGames.list.isEmpty()) {
            Panels.close()
        } else {
            next?.let { FocusMemory.remember("switcher:${switcherGen + 1}", it) }
            switcherGen++
        }
    }
}

/** Y in the switcher: close everything (press twice). */
fun switcherCloseAll(ctx: Context) {
    if (RunningGames.list.isEmpty()) return
    if (!switcherConfirm.press("all", "Press Y again to close all games")) return
    var n = 0
    inBackground({ n = RunningGames.closeAll(ctx) }) {
        Toasts.show(if (n > 0) "Closed $n ${if (n == 1) "game" else "games"}. All tidy!" else "Couldn't close them. Is Shizuku running?")
        if (RunningGames.list.isEmpty()) Panels.close()
    }
}

/** Long-press Select, Select + Start, or the top bar: every running game, to resume or close. */
@Composable
fun GameSwitcher() {
    val ctx = LocalContext.current
    LaunchedEffect(Panels.switcher) {
        if (Panels.switcher) kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { RunningGames.refresh(ctx, maxAgeMs = 1500) }
    }
    OverlayPanel(Panels.switcher, onClose = { Panels.close() }) {
        val list = RunningGames.list
        PanelSurface(Modifier.widthIn(min = 420.dp, max = 780.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Running now", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.width(10.dp))
                if (list.isNotEmpty()) Pill("${list.size} open", Palette.Mint)
            }
            Spacer(Modifier.height(12.dp))
            when {
                !RunningGames.canSee -> PipNote(
                    "I can't see other apps yet",
                    "Start Shizuku and allow Tandem in it, and I'll show every running game here.",
                )
                list.isEmpty() -> PipNote("Nothing running", "Start a game and it'll wait for you here.", mood = 2)
                else -> {
                    val gen = switcherGen
                    val routeKey = "switcher:$gen"
                    val target = rememberFocusTarget(routeKey, list.map { it.pkg } + "closeall")
                    val row = androidx.compose.foundation.lazy.rememberLazyListState()
                    LazyRow(
                        Modifier.fadingEdges(row, horizontal = true).focusRow(),
                        state = row,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(horizontal = FocusRoomSide, vertical = FocusRoom),
                    ) {
                        items(list, key = { it.pkg }) { r ->
                            SwitcherCard(r, Modifier.takesFocus(routeKey, r.pkg, r.pkg == target))
                        }
                        item("closeall") {
                            FocusCard(
                                Modifier.width(120.dp).height(232.dp).takesFocus(routeKey, "closeall", target == "closeall"),
                                glow = Palette.Peach,
                                onFocus = { Panels.focusedRunning = null },
                                onClick = { switcherCloseAll(ctx) },
                            ) {
                                Column(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("✕", style = MaterialTheme.typography.displaySmall, color = Palette.Peach)
                                    Text(
                                        if (switcherConfirm.armed == "all") "Again to\nclose all" else "Close all",
                                        style = MaterialTheme.typography.titleSmall,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                if (list.isNotEmpty()) {
                    ButtonHint("A", "Resume", Palette.Mint)
                    ButtonHint("X", "Close", Palette.Peach)
                    ButtonHint("Y", "Close all", Palette.Butter)
                }
                ButtonHint("B", "Back", Palette.Lavender)
            }
            Text(
                "Closing a game is like turning the console off: save first!",
                Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodySmall, color = Palette.Muted,
            )
        }
    }
}

@Composable
private fun SwitcherCard(r: RunningGame, modifier: Modifier) {
    val ctx = LocalContext.current
    val shown = r.game?.takeIf { Privacy.canShow(it) }
    val color = shown?.let { Palette.forName(it.platform.id) } ?: Palette.Lavender
    Column(modifier.width(170.dp)) {
        FocusCard(
            Modifier.fillMaxWidth().height(170.dp).onFocusChanged { if (!it.isFocused && Panels.focusedRunning == r.pkg) Panels.focusedRunning = null },
            glow = color,
            onFocus = {
                Panels.focusedRunning = r.pkg
                Selection.current = if (shown != null) Selected.GameItem(shown) else Selected.Home
            },
            onClick = { Panels.close(); RunningGames.resume(ctx, r) },
        ) {
            if (shown != null) GameArt(shown) else AppIcon(r.pkg, Modifier.align(Alignment.Center).size(84.dp))
            Pill("● Running", Palette.Mint, Modifier.align(Alignment.TopStart).padding(8.dp).darkChip())
            if (switcherConfirm.armed == "one:${r.pkg}") {
                // The card's own colour, deepened, rather than a grey veil over the art.
                Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.lerp(color, Palette.NightDeep, 0.72f).copy(alpha = 0.88f)), contentAlignment = Alignment.Center) {
                    Text("X again\nto close", style = MaterialTheme.typography.titleMedium, color = Palette.Peach, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(r.shownTitle(), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(r.detail(), style = MaterialTheme.typography.labelMedium, color = Palette.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}


/** The card at the top of the home screen while a game runs in the background. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NowPlayingCard(r: RunningGame) = NowPlayingCard(r, FocusMemory.get(Route.Home.key))

/** [target] is the home screen's focus target (see rememberFocusTarget). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NowPlayingCard(r: RunningGame, target: String?) {
    val ctx = LocalContext.current
    val game = r.game ?: return
    val lib = Libraries.of(game)
    val color = Palette.forName(game.platform.id)
    val route = Route.Home.key
    val others = RunningGames.list.size - 1
    // The card usually appears a moment after Tandem comes back, above where the list is scrolled; show it.
    val reveal = remember { BringIntoViewRequester() }
    LaunchedEffect(r.pkg) { runCatching { reveal.bringIntoView() } }
    // Y on the card closes the game (twice), as on the icon grid's Resume card; a long press does the same by touch.
    var resumeFocused by remember { mutableStateOf(false) }
    InterceptButtons(resumeFocused) { b -> if (b == Button.Y) { resumePageClose(ctx, r); true } else false }
    Column(Modifier.bringIntoViewRequester(reveal).padding(horizontal = 22.dp)) {
        SectionTitle("Now playing", Palette.Mint)
        Row(Modifier.fillMaxWidth().height(112.dp).padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FocusCard(
                Modifier.weight(1f).fillMaxHeight().onFocusChanged { resumeFocused = it.hasFocus }.takesFocus(route, "now:resume", target == "now:resume"),
                glow = color,
                onFocus = { Selection.current = Selected.GameItem(game) },
                onLongPress = { resumePageClose(ctx, r) },
                onClick = { RunningGames.resume(ctx, r) },
            ) {
                val hero = lib?.hero(game) ?: lib?.screenshot(game)
                // The art fades out on the left, where the text sits, instead of sitting under a dark band.
                if (hero != null) AsyncImage(hero, null, Modifier.fillMaxSize().fadeMaskHorizontal(0f to 0.12f, 0.45f to 0.4f, 1f to 1f), contentScale = ContentScale.Crop)
                Row(Modifier.fillMaxSize().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.fillMaxHeight().aspectRatio(1f).shadow(6.dp, RoundedCornerShape(12.dp)).clip(RoundedCornerShape(12.dp))) { GameArt(game) }
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text("● PAUSED IN ${r.appName.uppercase()}", style = MaterialTheme.typography.labelMedium, color = Palette.Mint, maxLines = 1)
                        Text(game.title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(r.detail(), style = MaterialTheme.typography.labelMedium, color = Palette.Muted, maxLines = 1)
                    }
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        ButtonHint("A", "Resume", Palette.Mint)
                        ButtonHint("Y", "Close", Palette.Peach)
                    }
                    Spacer(Modifier.width(6.dp))
                }
            }
            if (others > 0) {
                FocusCard(
                    Modifier.width(92.dp).fillMaxHeight().takesFocus(route, "now:more", target == "now:more"),
                    glow = Palette.Lavender,
                    onFocus = { Selection.current = Selected.Home },
                    onClick = { Panels.openSwitcher() },
                ) {
                    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("+$others", style = MaterialTheme.typography.headlineSmall, color = Palette.Lavender)
                        Text("more", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

/** The space around the now-playing banner. */
val NowPlayingBannerMargin = 12.dp

/** About how tall the now-playing banner is without its margin: two lines of text and 6 dp of padding each side. */
val NowPlayingBannerHeight = 48.dp

/** How far down the details screen the now-playing banner reaches, in pixels with its margin; 0 while it's hidden. */
object NowPlayingRoom {
    var height by mutableIntStateOf(0)
}

/**
 * Lays details out below the now-playing banner instead of under it: the content gets the height the banner leaves
 * and is centred in that, so nothing is pushed off the bottom edge. Read in the layout phase only, so the banner
 * coming, growing or going re-lays out the screen without recomposing it (like [clearOfScrapeCard]).
 */
fun Modifier.clearOfNowPlaying(): Modifier = layout { m, c ->
    val room = if (c.hasBoundedHeight) NowPlayingRoom.height.coerceIn(0, c.maxHeight) else 0
    val p = m.measure(c.copy(minHeight = (c.minHeight - room).coerceAtLeast(0), maxHeight = if (c.hasBoundedHeight) c.maxHeight - room else c.maxHeight))
    layout(p.width, p.height + room) { p.place(0, room) }
}

/** A slim banner on the bottom screen while a game runs in the background; tap to go back to it. */
@Composable
fun NowPlayingBanner(modifier: Modifier = Modifier) {
    val r = nowPlaying()
    val selected = (Selection.current as? Selected.GameItem)?.game
    // No banner when the running game is the one already shown in detail.
    val show = r != null && selected?.key != r.game?.key
    val last = rememberLatest(r)
    AnimatedVisibility(show, modifier, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
        val shown = last ?: return@AnimatedVisibility
        val game = shown.game ?: return@AnimatedVisibility
        Row(
            // The running game's own colour, deepened, not a dark band.
            Modifier.padding(NowPlayingBannerMargin).clip(RoundedCornerShape(50)).background(androidx.compose.ui.graphics.lerp(Palette.forName(game.platform.id), Palette.NightDeep, 0.78f).copy(alpha = 0.92f))
                .swallowTaps { Host.activity?.let { RunningGames.resume(it, shown) } }
                .padding(start = 6.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(34.dp).clip(RoundedCornerShape(50))) { GameArt(game) }
            Column(Modifier.padding(start = 9.dp).widthIn(max = 300.dp)) {
                Text("● Now playing", style = MaterialTheme.typography.labelSmall, color = Palette.Mint)
                Text(game.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(10.dp))
            Pill("Tap to resume", Palette.Mint)
        }
    }
}

/** Small buttons for the top bar: running games, play stats and the quick panel. */
@Composable
fun TopBarButtons() {
    val n = RunningGames.list.size
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (n > 0) TopChip("▶ $n open", Palette.Mint) { Panels.openSwitcher() }
        if (Nav.route != Route.Stats) TopChip("★ Stats", Palette.Butter) { Nav.go(Route.Stats) }
        TopChip("◐ Quick", Palette.Lavender) { Panels.toggleQuick() }
    }
}

@Composable
private fun TopChip(label: String, color: Color, onClick: () -> Unit) {
    FocusCard(
        Modifier.height(30.dp),
        glow = color,
        shape = RoundedCornerShape(50),
        onFocus = { Selection.current = Selected.Home },
        onClick = onClick,
    ) {
        Box(Modifier.fillMaxHeight().background(color.copy(alpha = 0.16f)).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = color, maxLines = 1)
        }
    }
}
