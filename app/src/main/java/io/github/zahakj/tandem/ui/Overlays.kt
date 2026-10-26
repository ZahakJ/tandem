package io.github.zahakj.tandem.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.zahakj.tandem.Button
import io.github.zahakj.tandem.Collections
import io.github.zahakj.tandem.Game
import io.github.zahakj.tandem.GroupTarget
import io.github.zahakj.tandem.LaunchDisplay
import io.github.zahakj.tandem.Launcher
import io.github.zahakj.tandem.Libraries
import io.github.zahakj.tandem.HiddenFolders
import io.github.zahakj.tandem.Privacy
import io.github.zahakj.tandem.Selected
import io.github.zahakj.tandem.Selection
import io.github.zahakj.tandem.data.ScrapeScope
import io.github.zahakj.tandem.data.SortOrder
import io.github.zahakj.tandem.data.Store
import io.github.zahakj.tandem.data.SystemPrefs
import io.github.zahakj.tandem.everPlayed
import io.github.zahakj.tandem.plural
import java.text.Normalizer

/**
 * A centred panel over a dimmed screen; closes on tap outside or B. Use for any popup. The panel gets the screen
 * less [PanelMargin] on every side, so it can never run off the screen; a taller panel scrolls its middle part.
 */
@Composable
fun OverlayPanel(visible: Boolean, onClose: () -> Unit, morph: PanelMorph? = null, content: @Composable () -> Unit) = Scrim(visible, onClose, morph) { progress ->
    PopupRoom { if (morph != null && progress != null) MorphBox(morph, progress) { content() } else content() }
}

/**
 * Dims the screen and closes on tap or B. While open, the popup is the top focus layer: only its items may take
 * focus, and the D-pad can't leave it. While it animates away it ignores the D-pad and A, so a quick press after
 * closing goes to the screen, not to the fading popup. With a [morph] the popup grows out of a tile (PanelMorph.kt)
 * and its content gets the progress; otherwise it pops in.
 */
@Composable
private fun Scrim(visible: Boolean, onClose: () -> Unit, morph: PanelMorph? = null, content: @Composable (progress: (() -> Float)?) -> Unit) {
    val layer = rememberLayer(visible)
    val open by rememberUpdatedState(visible)
    if (visible) BackHandler(onBack = onClose)
    AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut()) {
        Box(Modifier.fillMaxSize().background(Palette.NightDeep.copy(alpha = 0.72f)).clickable(onClick = onClose))
    }
    // Reduce motion: no growing out of the tile, just a short fade.
    val moving = morph != null && Flair.entering
    AnimatedVisibility(
        visible,
        enter = if (moving) EnterTransition.None else if (morph != null) fadeIn(tween(140)) else popIn,
        exit = if (moving) ExitTransition.None else if (morph != null) fadeOut(tween(120)) else popOut,
    ) {
        val progress = if (moving) {
            val p = transition.animateFloat({ spring(dampingRatio = 0.86f, stiffness = 420f, visibilityThreshold = 0.002f) }, label = "panel-morph") {
                if (it == EnterExitState.Visible) 1f else 0f
            }
            p::value
        } else null
        ProvideLayer(layer) {
            Box(
                Modifier.fillMaxSize()
                    .onFocusChanged { Layers.focusIn(layer, it.hasFocus) }
                    .onPreviewKeyEvent { !open && it.isNavKey() }
                    .trapFocus(),
            ) { content(progress) }
        }
    }
}

/** A popup row. Takes focus when its key was the last used, or [first] when nothing was. */
@Composable
fun MenuRow(label: String, value: String?, key: String, first: Boolean, routeKey: String, color: Color = Palette.Mint, onClick: () -> Unit) {
    MenuItem(label, value, Modifier.remembersFocus(routeKey, key, first), color, onClick)
}

/** A popup row that takes focus when [key] is the popup's focus [target]. */
@Composable
fun MenuRow(label: String, value: String?, key: String, routeKey: String, target: String?, color: Color = Palette.Mint, onClick: () -> Unit) {
    MenuItem(label, value, Modifier.takesFocus(routeKey, key, key == target), color, onClick)
}

/** A popup row for something that can't be undone: it acts only when held until its ring fills (HoldConfirm.kt). */
@Composable
fun HoldMenuRow(label: String, key: String, routeKey: String, target: String?, color: Color = Palette.Peach, onConfirm: () -> Unit) {
    val hold = rememberHold(holdHint(label.lowercase()), onConfirm)
    val requester = remember { androidx.compose.ui.focus.FocusRequester() }
    FocusCard(
        Modifier.fillMaxWidth().takesFocus(routeKey, key, key == target).focusRequester(requester).holdToConfirm(hold, requester),
        glow = color, shape = RoundedCornerShape(14.dp), onClick = {},
    ) {
        Row(Modifier.fillMaxWidth().holdFill(hold, color, 14.dp).padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.width(8.dp))
            HoldRing(hold, color)
        }
    }
}

@Composable
private fun MenuItem(label: String, value: String?, focus: Modifier, color: Color, onClick: () -> Unit) {
    FocusCard(Modifier.fillMaxWidth().then(focus), glow = color, shape = RoundedCornerShape(14.dp), onClick = onClick) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (value != null) { Spacer(Modifier.width(8.dp)); Pill(value, color) }
        }
    }
}

/** What hiding a game (or folder, or system) says, without giving a hidden game away in private mode. */
private fun hiddenToast(): String = when {
    Privacy.enabled -> "Hidden away"
    Privacy.showHidden -> "Hidden. It still shows while hidden games are shown"
    // It is gone from view, so the way back is Undo (Select, or a tap) or Start › Hidden, never a button on it.
    else -> "Hidden. Start › Hidden lists it"
}

/**
 * Hide or Unhide for a folder or a whole system (under More…). Hiding takes everything inside with it, everywhere,
 * games added later included; while hidden games show, the folder stays, dimmed, with the hidden mark.
 */
fun hideAction(label: String, hidden: Boolean, undo: (() -> Unit)? = null, run: () -> Unit) = MenuAction("hidefolder", label, Glyph.Eye, Palette.Lavender, more = true) {
    run()
    Sounds.toggle(hidden)
    val said = if (hidden) "Back in your library" else hiddenToast()
    if (undo != null) Undo.offer(said, undo) else Toasts.show(said)
    Nav.focusTick++
}

/** Y on a game: the game lifts out with play, favorite, Up next and pin first; the rest under More…. */
@Composable
fun GameMenu() {
    val game = Nav.menuFor
    val ctx = LocalContext.current
    // Keep showing the last game while the menu animates away.
    val shown = rememberLatest(game)
    val screenRow = remember { LaunchDisplay.secondary(ctx) != null } && !SystemPrefs.data.singleScreen
    ActionMenuHost(game != null, onClose = { Nav.closeMenus() }) spec@{
        val g = shown ?: return@spec null
        val meta = g.meta
        val players = g.platform.players.let { all -> all.filter { it.accepts(g.file.name) }.ifEmpty { all } }
        val current = g.platform.playerFor(g)
        val routeKey = "menu:${g.key}"
        val running = io.github.zahakj.tandem.RunningGames.list.any { it.game?.key == g.key }
        val actions = buildList {
            add(MenuAction("play", if (running) "Resume" else "Play", Glyph.Play, Palette.Mint) { Nav.closeOverlays(); Launcher.launch(ctx, g) })
            add(MenuAction("fav", if (meta.favorite) "Unfavorite" else "Favorite", Glyph.Heart, Palette.Peach, value = if (meta.favorite) "♥" else null) {
                val was = meta.favorite
                Store.update(g.key) { it.copy(favorite = !was) }
                if (was) Sounds.toggle(false) else Sounds.favorite()
                Undo.offer(if (was) "Removed from favorites" else "Added to favorites ♥") { Store.update(g.key) { it.copy(favorite = was) } }
            })
            val queued = UpNext.contains(g)
            add(MenuAction("upnext", if (queued) "Take off Up next" else "Add to Up next", icon = TIcon.UpNext, color = Palette.Sky, value = if (queued) "✓" else null) { UpNext.toggle(g) })
            if (IconGrid.enabled) {
                val pinned = isPinnedToHome(g)
                add(MenuAction("home", if (pinned) "Unpin from home" else "Pin to home", Glyph.Star, Palette.Butter, value = if (pinned) "★" else null) { togglePinToHome(g) })
            }
            add(MenuAction("edit", "Edit details", Glyph.Pencil, Palette.Butter, value = if (meta.lockedFields.isNotEmpty()) "${meta.lockedFields.size} locked" else null,
                help = "Locked details stay as you set them: scraping and gamelist imports leave them alone.") {
                GameEditor.edit(g); Nav.closeOverlays()
            })
            if (!g.platform.isApps) add(
                MenuAction("emu", "Emulator", Glyph.Gamepad, Palette.Sky, value = current?.name ?: "Not installed", more = true,
                    help = "The emulator this game starts in. Tap to switch to the next installed one; the first is the system's own choice.") {
                    if (players.size > 1) {
                        val next = players[(players.indexOf(current) + 1) % players.size]
                        Store.update(g.key) { it.copy(emulator = if (next == players.first()) null else next.name) }
                    } else {
                        Toasts.show(if (current == null) "Get an emulator for ${g.platform.name} in Systems" else "Install another emulator to switch")
                    }
                },
            )
            if (screenRow) {
                val bottom = LaunchDisplay.wantsBottom(g)
                val systemBottom = SystemPrefs.config(g.platform.id).bottomScreen
                add(MenuAction("screen", "Start on", Glyph.Screens, Palette.Butter, value = if (bottom) "Bottom screen" else "Top screen", more = true,
                    help = "The screen this game opens on. Set back to the system's screen, it follows the system again.") {
                    val want = !bottom
                    Store.update(g.key) { it.copy(screen = if (want == systemBottom) null else if (want) "bottom" else "top") }
                })
                // What the bottom screen shows while it plays on the top one (GameCompanion.kt).
                val companion = io.github.zahakj.tandem.GameCompanion.modeFor(g)
                add(MenuAction("companion", "Bottom screen while playing", Glyph.Screens, Palette.Lavender, value = io.github.zahakj.tandem.GameCompanion.label(companion), more = true,
                    help = "What the bottom screen shows while this game plays on the top one.") {
                    val next = io.github.zahakj.tandem.GameCompanion.next(companion)
                    val inherited = io.github.zahakj.tandem.GameCompanion.systemMode(g.platform)
                    Store.update(g.key) { it.copy(companion = if (next == inherited) null else next) }
                })
            }
            add(MenuAction("art", "Choose artwork", Glyph.Image, Palette.Butter, more = true) { GameEditor.chooseArt(g); Nav.closeOverlays() })
            add(MenuAction("scrape", "Scrape this game", Glyph.Download, Palette.Butter, more = true) { Nav.closeOverlays(); ScrapeUi.scrapeOne(g) })
            // A game in a hidden folder stays hidden with it, whatever its own switch says.
            val byFolder = HiddenFolders.hides(g)
            add(MenuAction("hide", if (meta.hidden) "Unhide this game" else "Hide this game", Glyph.Eye, Palette.Lavender, value = if (byFolder) "Folder hidden" else null, more = true,
                help = if (byFolder) "Its folder is hidden, so it stays hidden with it whatever this says." else null) {
                val hiding = !meta.hidden
                Store.update(g.key) { it.copy(hidden = hiding) }
                Sounds.toggle(!hiding)
                Undo.offer(if (hiding) hiddenToast() else "Back in your library") { Store.update(g.key) { it.copy(hidden = !hiding) } }
                Nav.closeMenus()
            })
            if (meta.everPlayed) add(MenuAction("sessions", "Play sessions…", Glyph.Clock, Palette.Sky, more = true) { PlaySessions.openFor(g) })
            if (meta.everPlayed) add(MenuAction("reset", "Reset play history", Glyph.Refresh, Palette.Peach, value = if (meta.playSeconds >= 60) formatPlayTime(meta.playSeconds) else null, more = true,
                help = "The play time so far. Resetting forgets it, with the play count and sessions.") {
                Confirm.hold(
                    "Reset play history?",
                    "Play time, play count and sessions for ${g.title} will be forgotten. This can't be undone.",
                    "Reset",
                ) {
                    Store.update(g.key) { it.copy(playSeconds = 0, playCount = 0, lastPlayed = 0) }
                    io.github.zahakj.tandem.data.HomeStore.forgetGame(g.key)
                    Toasts.show("Play history cleared")
                    FocusMemory.remember("$routeKey:main", "play")
                }
            })
            // Files, art and Tandem's record, after a popup that lists them (DeleteGame.kt).
            add(MenuAction("delete", if (g.platform.isApps) "Uninstall app…" else "Delete game…", Glyph.Trash, Palette.Peach, value = if (running) "Running" else null, more = true,
                help = if (running) "This game is still running." else null) {
                DeleteGame.ask(ctx, g)
            })
            add(MenuAction("view", "View options", Glyph.Sort, Palette.Sky, value = "Sort, titles…", more = true) {
                Nav.menuFor = null
                Nav.openGroupMenu(routeTarget())
            })
        }
        MenuSpec(routeKey, g.title, listOfNotNull(g.platform.name, meta.year?.toString()).joinToString(" · "), SystemColors.of(g.platform.id), actions) { GameArt(g) }
    }
}

/** Title for a group menu's target. */
private fun targetTitle(t: GroupTarget?): String = when (t) {
    is GroupTarget.System -> listOfNotNull(Libraries.byId(t.platformId)?.platform?.name ?: "Games", t.folder).joinToString(" › ")
    is GroupTarget.Collection -> Collections.byId(t.id)?.title ?: "Collection"
    GroupTarget.Genres -> "Genres"
    null -> "View"
}

private fun targetRoute(t: GroupTarget?): Route? = when (t) {
    is GroupTarget.System -> Route.System(t.platformId, t.folder)
    is GroupTarget.Collection -> Route.Collection(t.id)
    GroupTarget.Genres -> Route.Genres
    null -> null
}

/**
 * Y on a grid or on a system, folder or collection tile: open it, rescan or scrape it, and how games are sorted and
 * shown (these apply to every grid). The tile lifts out like the game menu.
 */
@Composable
fun GroupMenu() {
    val ctx = LocalContext.current
    val open = Nav.groupMenu
    // Remember what the menu was about while it animates away.
    val about = rememberLatest(if (open) listOf(Nav.groupTarget) else null)
    ActionMenuHost(open, onClose = { Nav.closeMenus() }) spec@{
        val list = about ?: return@spec null
        val t = list.firstOrNull()
        val s = Store.settings
        val route = targetRoute(t)
        val canOpen = route != null && route != Nav.route
        val system = t as? GroupTarget.System
        val ordered = t is GroupTarget.Collection && t.id in ORDERED_COLLECTIONS
        val color = when (t) {
            is GroupTarget.System -> SystemColors.of(t.platformId)
            is GroupTarget.Collection -> Collections.byId(t.id)?.color ?: Palette.Lavender
            GroupTarget.Genres -> Palette.Sky
            null -> Palette.Lavender
        }
        val actions = buildList {
            if (canOpen && route != null) add(MenuAction("open", "Open", Glyph.Play, Palette.Mint) {
                Nav.closeMenus()
                if (route is Route.System || route is Route.Collection) openFolder(route) else Nav.go(route)
            })
            if (system != null && system.folder == null) add(MenuAction("rescan", "Rescan games", Glyph.Refresh, Palette.Mint, value = if (LibraryScan.scanning) "Looking…" else null) {
                Nav.closeMenus(); LibraryScan.rescan(ctx)
            })
            if (system != null) add(MenuAction("scrape", "Scrape these games", Glyph.Download, Palette.Butter) {
                Nav.closeMenus()
                ScrapeUi.open(if (system.folder == null) ScrapeScope.System(system.platformId) else ScrapeScope.Folder(system.platformId, system.folder))
            })
            if (system != null && HiddenFolders.canHide(system.platformId, system.folder)) {
                val listed = HiddenFolders.listed(system.platformId, system.folder)
                val what = if (system.folder == null) "this system" else "this folder"
                val undo = { if (listed) HiddenFolders.hide(system.platformId, system.folder) else HiddenFolders.unhide(system.platformId, system.folder) }
                add(hideAction(if (listed) "Unhide $what" else "Hide $what", listed, undo) {
                    Nav.closeMenus()
                    if (listed) HiddenFolders.unhide(system.platformId, system.folder) else HiddenFolders.hide(system.platformId, system.folder)
                })
            }
            add(MenuAction("sort", "Sort by", Glyph.Sort, Palette.Sky, value = if (ordered) "Own order" else s.sort.label,
                help = if (ordered) "This list keeps its own order." else "How games are sorted in every system and list. Tap for the next order.") {
                if (ordered) Toasts.show("This list keeps its own order. Sort applies to systems and other lists")
                else Store.updateSettings { it.copy(sort = SortOrder.entries[(it.sort.ordinal + 1) % SortOrder.entries.size]) }
            })
            add(MenuAction("titles", "Game titles", Glyph.Text, Palette.Sky, value = onOff(s.showTitles),
                help = "Names under the game cards, in every grid.") {
                Store.updateSettings { it.copy(showTitles = !it.showTitles) }
            })
            add(MenuAction("hidden", "Show hidden games", Glyph.Eye, Palette.Lavender, value = if (Privacy.enabled) "Private" else onOff(Privacy.showHidden),
                help = if (Privacy.enabled) "Private mode keeps hidden games out of sight until your PIN shows them." else "Hidden games show, dimmed, while this is on.") {
                toggleShowHidden()
            })
        }
        val art: TileArt = IconGrid.spot?.takeIf { sp ->
            when (t) {
                is GroupTarget.System -> sp.platformId == t.platformId && t.folder == null
                is GroupTarget.Collection -> sp.collectionId == t.id || sp.key == t.id
                GroupTarget.Genres -> sp.kind == TileKind.Genres
                null -> false
            }
        }?.art ?: when (t) {
            is GroupTarget.System -> IconPacks.systemIcon(t.platformId)?.let { TileArt.Picture(it) }
                ?: TileArt.Monogram(Libraries.byId(t.platformId)?.platform?.shortName ?: "?", color)
            GroupTarget.Genres -> TileArt.Tandem(TIcon.Genres, color)
            else -> TileArt.Tandem(TIcon.Star, color)
        }
        MenuSpec("groupmenu", targetTitle(t), "View options", color, actions) { TileArtView(art, 120.dp) }
    }
}

// ---------------------------------------------------------------------------------------------------------------
// Confirm

data class ConfirmRequest(
    val title: String, val detail: String, val yes: String, val action: () -> Unit,
    /** An optional third choice between Cancel and [yes] ("Skip it this time"). */
    val alt: String? = null, val altAction: () -> Unit = {},
    /** Something that can't be undone: [yes] acts only when held (HoldConfirm.kt). */
    val hold: Boolean = false,
)

/** A small "are you sure?" popup for anything that can't be undone. Opens on Cancel, so a stray A is harmless. */
object Confirm {
    var request by mutableStateOf<ConfirmRequest?>(null)

    fun ask(title: String, detail: String, yes: String, alt: String? = null, altAction: () -> Unit = {}, action: () -> Unit) {
        FocusMemory.forget("confirm")
        request = ConfirmRequest(title, detail, yes, action, alt, altAction)
    }

    /**
     * For something that can't be undone: the popup opens on [yes], which acts only once A (or a finger) has been
     * held down until its ring fills, so a stray press does nothing. B or Cancel backs out.
     */
    fun hold(title: String, detail: String, yes: String, action: () -> Unit) {
        FocusMemory.forget("confirm")
        FocusMemory.remember("confirm", "yes")
        request = ConfirmRequest(title, detail, yes, action, hold = true)
    }

    fun close() { request = null; Nav.focusTick++ }
}

@Composable
fun ConfirmOverlay() {
    val req = Confirm.request
    val shown = rememberLatest(req)
    InterceptButtons(req != null) { true }
    OverlayPanel(req != null, onClose = { Confirm.close() }) {
        val r = shown ?: return@OverlayPanel
        val target = rememberFocusTarget("confirm", listOfNotNull("no", "alt".takeIf { r.alt != null }, "yes"))
        PanelSurface(Modifier.width(if (r.alt != null) 560.dp else 470.dp)) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Pip(60.dp, mood = 2)
                Text(r.title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(r.detail, style = MaterialTheme.typography.bodyMedium, color = Palette.Muted, textAlign = TextAlign.Center, maxLines = 5, overflow = TextOverflow.Ellipsis)
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    PillButton("Cancel", Palette.Mint, Modifier.takesFocus("confirm", "no", target == "no")) { Confirm.close() }
                    if (r.alt != null) PillButton(r.alt, Palette.Lavender, Modifier.takesFocus("confirm", "alt", target == "alt")) {
                        Confirm.close()
                        r.altAction()
                    }
                    if (r.hold) HoldButton(r.yes, Palette.Peach, Modifier.takesFocus("confirm", "yes", target == "yes")) {
                        Confirm.close()
                        r.action()
                    } else PillButton(r.yes, Palette.Peach, Modifier.takesFocus("confirm", "yes", target == "yes")) {
                        Confirm.close()
                        r.action()
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------
// Search

private val KEYS = ("ABCDEFG" + "HIJKLMN" + "OPQRSTU" + "VWXYZ12" + "3456789" + "0").map { it.toString() } + listOf("Space", "⌫", "Clear")

/** The search text stays between visits; Clear (or X) empties it. */
object SearchState {
    var query by mutableStateOf("")
    /** Whether the results (rather than the keyboard) were used last, so focus goes back to the right side. */
    var inResults by mutableStateOf(false)
}

/** One game with its title prepared for matching. */
private class Searchable(val game: Game, val text: String)

/** X anywhere: a controller-friendly keyboard on the left, live results on the right. */
@Composable
fun SearchOverlay() {
    val ctx = LocalContext.current
    val open = Nav.search
    // X deletes a letter and Y opens the game menu for the highlighted result, while search is on top.
    InterceptButtons(open && Nav.menuFor == null && Nav.groupMenu.not() && TextEntry.request == null && !GameEditor.isOpen) { b ->
        when (b) {
            Button.X -> SearchState.query = SearchState.query.dropLast(1)
            Button.Y -> (Selection.current as? Selected.GameItem)?.takeIf { SearchState.inResults }?.let { Nav.openMenu(it.game) }
            else -> Unit
        }
        true
    }
    LaunchedEffect(open) { if (open) SearchState.inResults = false }
    Scrim(open, onClose = { Nav.closeOverlays() }) {
        val query = SearchState.query
        val meta = Store.meta
        val showHidden = Privacy.view
        val libs = Libraries.all
        val index = remember(libs, meta, showHidden) { Collections.visibleGames().map { Searchable(it, normalize(it.title)) } }
        val q = normalize(query)
        val results = remember(q, index) { if (q.isEmpty()) emptyList() else index.filter { it.text.contains(q) }.take(60).map { it.game } }
        // Systems, apps and settings that match too, as chips above the games (QuickFind.kt).
        val finds = rememberQuickFinds(q)
        // The bottom screen previews the first match while typing.
        LaunchedEffect(results) {
            if (!SearchState.inResults) results.firstOrNull()?.let { Selection.current = Selected.GameItem(it) }
        }
        LaunchedEffect(results.isEmpty() && finds.isEmpty()) { if (results.isEmpty() && finds.isEmpty()) SearchState.inResults = false }
        // B in the results goes back to the keyboard first.
        BackHandler(enabled = open && SearchState.inResults && Nav.menuFor == null) { SearchState.inResults = false }
        val keyTarget = rememberFocusTarget("search", KEYS.map { "key:$it" })
        val resultKeys = finds.map { it.key } + results.map { it.key }
        val resultTarget = rememberFocusTarget("search:results", resultKeys)
        Row(Modifier.fillMaxSize().padding(PanelMargin).clip(RoundedCornerShape(26.dp)).background(Palette.Card).padding(14.dp)) {
            Column(Modifier.width(310.dp).onFocusChanged { if (it.hasFocus) SearchState.inResults = false }) {
                Box(Modifier.padding(horizontal = 8.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Palette.NightDeep).padding(12.dp)) {
                    if (query.isEmpty()) {
                        Text("⌕ Games, apps, settings", style = MaterialTheme.typography.titleLarge, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    } else {
                        TailText("⌕ $query", Palette.Text)
                    }
                }
                Spacer(Modifier.height(4.dp))
                LazyVerticalGrid(GridCells.Fixed(7), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(KEYS, key = { it }, span = { k -> GridItemSpan(if (k.length > 1 || k == "⌫") 2 else 1) }) { k ->
                        FocusCard(
                            Modifier.height(34.dp).takesFocus("search", "key:$k", !SearchState.inResults && keyTarget == "key:$k"),
                            glow = Palette.Mint, shape = RoundedCornerShape(10.dp),
                            onClick = {
                                SearchState.query = when (k) {
                                    "⌫" -> query.dropLast(1)
                                    "Clear" -> ""
                                    // No leading or double spaces: they'd only make "no matches" look odd.
                                    "Space" -> if (query.isBlank() || query.endsWith(" ")) query else "$query "
                                    else -> query + k.lowercase()
                                }
                            },
                        ) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(k, style = MaterialTheme.typography.titleSmall, maxLines = 1) }
                        }
                    }
                }
                Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ButtonHint("X", "Delete", Palette.Peach)
                    ButtonHint("B", "Close", Palette.Lavender)
                    if (resultKeys.isNotEmpty()) ButtonHint("◀▶", "Results", Palette.Sky)
                }
            }
            Spacer(Modifier.width(14.dp))
            Box(Modifier.weight(1f).fillMaxHeight()) {
                if (results.isEmpty() && finds.isEmpty()) {
                    Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                        Pip(70.dp, mood = if (q.isEmpty()) 0 else 2)
                        Text(if (q.isEmpty()) "Type a few letters" else "No matches for \"${query.trim()}\"", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (q.isNotEmpty()) Text("Accents and punctuation don't matter", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
                    }
                } else {
                    Column(Modifier.fillMaxSize()) {
                        val grid = rememberLazyGridState()
                        LazyVerticalGrid(
                            GridCells.Adaptive(92.dp), Modifier.weight(1f).fadingEdges(grid).onFocusChanged { if (it.hasFocus) SearchState.inResults = true },
                            state = grid,
                            // Room on every side, so the highlighted result (which grows) is never cut off at the top;
                            // rows scrolled past fade out instead of being cut.
                            contentPadding = PaddingValues(horizontal = FocusRoomSide, vertical = FocusRoom), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            if (finds.isNotEmpty()) {
                                item("quickfind", span = { GridItemSpan(maxLineSpan) }) { QuickFindRow(finds, "search:results", SearchState.inResults, resultTarget) }
                            }
                            items(results, key = { it.key }) { g ->
                                GameCard(
                                    g, Modifier.aspectRatio(0.8f).takesFocus("search:results", g.key, SearchState.inResults && resultTarget == g.key),
                                    onFocus = { Selection.current = Selected.GameItem(g) },
                                    onClick = { Nav.closeOverlays(); Launcher.launch(ctx, g) }, showTitle = true,
                                )
                            }
                        }
                        if (SearchState.inResults) {
                            // The chips are one grid item above the games.
                            val at = resultKeys.indexOf(resultTarget)
                            KeepInView(grid, if (finds.isEmpty() || at < 0) at else if (at < finds.size) 0 else at - finds.size + 1)
                        }
                        Row(Modifier.padding(horizontal = 18.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(plural(results.size, "match", "matches") + if (results.size >= 60) "+" else "", style = MaterialTheme.typography.labelLarge, color = Palette.Muted)
                            Spacer(Modifier.weight(1f))
                            ButtonHint("A", "Play", Palette.Mint)
                            ButtonHint("Y", "Options", Palette.Butter)
                            ButtonHint("B", "Keys", Palette.Lavender)
                        }
                    }
                }
            }
        }
    }
}

/** One line of text that always shows its end (the latest letters typed), scrolling the start out of view. */
@Composable
fun TailText(text: String, color: Color, caret: Boolean = true) {
    val scroll = rememberScrollState()
    LaunchedEffect(text, scroll.maxValue) { scroll.scrollTo(scroll.maxValue) }
    Row(Modifier.fillMaxWidth().horizontalScroll(scroll, enabled = false), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleLarge, color = color, maxLines = 1, softWrap = false)
        if (caret) Text("▏", style = MaterialTheme.typography.titleLarge, color = Palette.Mint, maxLines = 1)
    }
}

private val MARKS = Regex("\\p{Mn}+")
private val SPACES = Regex(" +")

/** "Pokémon: Let's Go!" -> "pokemon let s go": accents dropped, punctuation turned into spaces. */
fun normalize(s: String): String =
    Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(MARKS, "")
        .map { if (it.isLetterOrDigit()) it else ' ' }.joinToString("").replace(SPACES, " ").trim()

/** Compact play time for pills and stats: "45m", "2h 5m". */
fun formatPlayTime(seconds: Long): String {
    val m = seconds / 60
    return when {
        m < 1 -> "under 1m"
        m < 60 -> "${m}m"
        else -> "${m / 60}h ${m % 60}m"
    }
}

/** Play time in words: "1 min", "45 min", "2 h 5 min". */
fun formatPlayLong(seconds: Long): String {
    val m = seconds / 60
    return when {
        m < 1 -> "under a minute"
        m < 60 -> "$m min"
        m % 60 == 0L -> "${m / 60} h"
        else -> "${m / 60} h ${m % 60} min"
    }
}
