package io.github.zahakj.tandem.ui

import io.github.zahakj.tandem.R
import io.github.zahakj.tandem.Tx
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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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
 * A panel that slides in from the right edge over a dimmed screen, the full height of it; closes on tap outside or
 * B, like [OverlayPanel]. [content] lays out the panel itself (its width, its rounded inner edge).
 */
@Composable
fun SidePanel(visible: Boolean, onClose: () -> Unit, content: @Composable () -> Unit) = Scrim(visible, onClose, side = true) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) { content() }
}

/**
 * Dims the screen and closes on tap or B. While open, the popup is the top focus layer: only its items may take
 * focus, and the D-pad can't leave it. While it animates away it ignores the D-pad and A, so a quick press after
 * closing goes to the screen, not to the fading popup. With a [morph] the popup grows out of a tile (PanelMorph.kt)
 * and its content gets the progress; otherwise it pops in.
 */
@Composable
private fun Scrim(visible: Boolean, onClose: () -> Unit, morph: PanelMorph? = null, side: Boolean = false, content: @Composable (progress: (() -> Float)?) -> Unit) {
    val layer = rememberLayer(visible)
    val open by rememberUpdatedState(visible)
    if (visible) BackHandler(onBack = onClose)
    AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut()) {
        Box(Modifier.fillMaxSize().background(Palette.NightDeep.copy(alpha = 0.72f)).clickable(onClick = onClose))
    }
    // Reduce motion: no growing out of the tile, just a short fade.
    val moving = morph != null && Flair.entering
    val rtl = isRtl
    AnimatedVisibility(
        visible,
        enter = when { moving -> EnterTransition.None; side -> sideIn(rtl); morph != null -> fadeIn(tween(140)); else -> popIn },
        exit = when { moving -> ExitTransition.None; side -> sideOut(rtl); morph != null -> fadeOut(tween(120)); else -> popOut },
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

/**
 * A popup row. Takes focus when its key was the last used, or [first] when nothing was. [glyph] puts an icon chip in
 * front; [onSide] makes its value a choice that Left and Right step back and forward through (A is [onClick]).
 * Give such a choice its [choices] (a few short words, [value] among them) and they all show, as a segmented track
 * with the value lit, instead of the value alone between arrows.
 */
@Composable
fun MenuRow(
    label: String, value: String?, key: String, first: Boolean, routeKey: String, color: Color = Palette.Mint,
    glyph: Glyph? = null, onSide: ((Int) -> Unit)? = null, choices: List<String>? = null, onClick: () -> Unit,
) {
    MenuItem(label, value, Modifier.remembersFocus(routeKey, key, first), color, glyph, onSide, choices, onClick)
}

/** A popup row that takes focus when [key] is the popup's focus [target]. */
@Composable
fun MenuRow(
    label: String, value: String?, key: String, routeKey: String, target: String?, color: Color = Palette.Mint,
    glyph: Glyph? = null, onSide: ((Int) -> Unit)? = null, choices: List<String>? = null, onClick: () -> Unit,
) {
    MenuItem(label, value, Modifier.takesFocus(routeKey, key, key == target), color, glyph, onSide, choices, onClick)
}

/** How tall a popup row is: rounded right off at the ends, so a list of them reads as bubbles. */
private val MenuRowHeight = 42.dp

/** A popup row for something that can't be undone: it acts only when held until its ring fills (HoldConfirm.kt). */
@Composable
fun HoldMenuRow(label: String, key: String, routeKey: String, target: String?, color: Color = Palette.Peach, onConfirm: () -> Unit) {
    val hold = rememberHold(holdHint(label.lowercase()), onConfirm)
    val requester = remember { androidx.compose.ui.focus.FocusRequester() }
    FocusCard(
        Modifier.fillMaxWidth().height(MenuRowHeight).takesFocus(routeKey, key, key == target).focusRequester(requester).holdToConfirm(hold, requester),
        glow = color, shape = RoundedCornerShape(50), lifts = false, onClick = {},
    ) {
        Row(Modifier.fillMaxSize().holdFill(hold, color, MenuRowHeight / 2).padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.width(8.dp))
            HoldRing(hold, color)
        }
    }
}

@Composable
private fun MenuItem(label: String, value: String?, focus: Modifier, color: Color, glyph: Glyph?, onSide: ((Int) -> Unit)?, choices: List<String>?, onClick: () -> Unit) {
    val at = choices?.indexOf(value) ?: -1
    FocusCard(
        Modifier.fillMaxWidth().height(MenuRowHeight).then(focus).sideSteps(onSide),
        glow = color, shape = RoundedCornerShape(50), lifts = false, onClick = onClick,
    ) {
        Row(Modifier.fillMaxSize().padding(start = if (glyph != null) 6.dp else 16.dp, end = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            if (glyph != null) {
                GlyphChip(glyph, color, size = 30.dp, round = true)
                Spacer(Modifier.width(10.dp))
            }
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (choices != null && at >= 0) {
                Spacer(Modifier.width(8.dp))
                // A tap on an option steps there.
                SegmentTrack(
                    choices, at, color, Modifier.width((choices.size * 84).dp.coerceAtMost(280.dp)),
                    onPick = { i -> if (i != at) onSide?.invoke(i - at) },
                )
            } else if (value != null) { Spacer(Modifier.width(8.dp)); ValueChip(value, color, cycles = onSide != null) }
        }
    }
}

/**
 * A row's value as a coloured chip; with [cycles] it shows ◂ ▸, for a choice that Left and Right step through
 * ([sideSteps]).
 */
@Composable
internal fun ValueChip(value: String, color: Color, cycles: Boolean = false, modifier: Modifier = Modifier, maxWidth: Dp = 180.dp) {
    Row(
        modifier.widthIn(max = maxWidth).background(color.copy(alpha = 0.18f), RoundedCornerShape(50))
            .padding(horizontal = if (cycles) 7.dp else 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val arrow = color.copy(alpha = 0.6f)
        // The arrows point outwards on both sides; the row puts its start on the right in right to left.
        val rtl = isRtl
        if (cycles) Text(if (rtl) "▸" else "◂", Modifier.padding(end = 4.dp), style = MaterialTheme.typography.labelMedium, color = arrow)
        // "▸" (opens a page) points towards the end, the left in right to left.
        FitText(if (rtl && value == "▸") "◂" else value, MaterialTheme.typography.labelMedium, Modifier.weight(1f, fill = false), color = color)
        if (cycles) Text(if (rtl) "◂" else "▸", Modifier.padding(start = 4.dp), style = MaterialTheme.typography.labelMedium, color = arrow)
    }
}

/** Left and Right call [step] with -1 and 1 (a row whose value is a choice); without one they move the focus as usual. */
internal fun Modifier.sideSteps(step: ((Int) -> Unit)?): Modifier = if (step == null) this else onPreviewKeyEvent { e ->
    // Towards the start or the end, whichever side that is (Rtl.kt).
    val by = e.sideStep()
    if (by == 0) return@onPreviewKeyEvent false
    if (e.type == KeyEventType.KeyDown) { step(by); Sounds.move() }
    true
}

/** What hiding a game (or folder, or system) says, without giving a hidden game away in private mode. */
private fun hiddenToast(): String = when {
    Privacy.enabled -> Tx.s(R.string.menus_hidden_away)
    Privacy.showHidden -> Tx.s(R.string.menus_hidden_still_shows)
    // It is gone from view, so the way back is Undo (Select, or a tap) or Start › Hidden, never a button on it.
    else -> Tx.s(R.string.menus_hidden_listed)
}

/**
 * Hide or Unhide for a folder or a whole system (under More…). Hiding takes everything inside with it, everywhere,
 * games added later included; while hidden games show, the folder stays, dimmed, with the hidden mark.
 */
fun hideAction(label: String, hidden: Boolean, undo: (() -> Unit)? = null, run: () -> Unit) = MenuAction("hidefolder", label, Glyph.Eye, Palette.Lavender, more = true) {
    run()
    Sounds.toggle(hidden)
    val said = if (hidden) Tx.s(R.string.menus_hidden_back) else hiddenToast()
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
        val players = g.platform.playersFor(g)
        val current = g.platform.playerFor(g)
        val routeKey = "menu:${g.key}"
        val runningIn = runningOf(g)
        val running = runningIn != null
        val actions = buildList {
            add(MenuAction("play", Tx.s(if (running) R.string.menus_game_resume else R.string.menus_game_play), Glyph.Play, Palette.Mint) { Nav.closeOverlays(); Launcher.launch(ctx, g) })
            // Still open: closing it sits right under Resume, wherever the menu was opened from.
            if (runningIn != null) add(closeGameAction(ctx, runningIn) { Nav.closeMenus() })
            add(MenuAction("fav", Tx.s(if (meta.favorite) R.string.menus_game_unfavorite else R.string.menus_game_favorite), Glyph.Heart, Palette.Peach, value = if (meta.favorite) "♥" else null) { toggleFavorite(g) })
            val queued = UpNext.contains(g)
            add(StatusUi.action(g) { StatusUi.focusPage(routeKey, g); StatusUi.pageFor = g.key })
            add(MenuAction("upnext", Tx.s(if (queued) R.string.menus_game_upnext_remove else R.string.menus_game_upnext_add), icon = TIcon.UpNext, color = Palette.Sky, value = if (queued) "✓" else null) { UpNext.toggle(g) })
            // On this week's board: another pick for its row, Not for me, Played it elsewhere (WeekBoardUi.kt).
            addAll(weekMenuActions(g) { Nav.closeMenus() })
            if (IconGrid.enabled) {
                val pinned = isPinnedToHome(g)
                add(MenuAction("home", Tx.s(if (pinned) R.string.menus_game_unpin_home else R.string.menus_game_pin_home), Glyph.Star, Palette.Butter, value = if (pinned) "★" else null) { togglePinToHome(g) })
            }
            // Peanuts Native can build this Switch game to run natively (once, 5 to 40 minutes on the device).
            val pn = io.github.zahakj.tandem.PeanutsNative
            if (g.platform.id == "switch") pn.refresh()
            if (pn.buildable(g)) add(MenuAction("native", Tx.s(R.string.menus_game_native), Glyph.Star, Palette.Mint, help = Tx.s(R.string.menus_game_native_help)) {
                Nav.closeOverlays(); pn.build(ctx, g)
            })
            pn.building?.takeIf { (name, _) -> name.equals(pn.of(g)?.name, ignoreCase = true) }?.let { (_, how) ->
                add(MenuAction("native-progress", Tx.s(R.string.menus_game_native_building), Glyph.Star, Palette.Mint, value = how) { pn.refresh(0) })
            }
            add(MenuAction("notes", Tx.s(R.string.menus_game_notes), icon = TIcon.Notes, color = Palette.Mint, help = Tx.s(R.string.menus_game_notes_help)) {
                Nav.closeOverlays(); NotesUi.openFor(g)
            })
            // Only when the game has one (ROMs/manuals, or chosen in Edit details): a row that opens nothing helps nobody.
            val manual = remember(g.key, io.github.zahakj.tandem.Manuals.version, meta.manual) { io.github.zahakj.tandem.Manuals.forGame(g) }
            if (manual != null) add(MenuAction("manual", Tx.s(R.string.menus_game_manual), icon = TIcon.Book, color = Palette.Butter, value = io.github.zahakj.tandem.Manuals.kindOf(manual)?.label ?: Tx.s(R.string.menus_game_manual_pictures),
                help = Tx.s(R.string.menus_game_manual_help, io.github.zahakj.tandem.Manuals.shortPath(manual))) {
                Nav.closeOverlays(); ManualUi.open(g)
            })
            // A Steam game's achievements, once the Steam account is set (SteamUi.kt).
            if (SteamUi.applies(g)) {
                val steam = io.github.zahakj.tandem.SteamAchievements.cached(g)
                add(MenuAction("steam", Tx.s(R.string.menus_game_achievements), Glyph.Trophy, Palette.Sky, value = steam?.takeIf { it.total > 0 }?.let { "${it.unlocked} / ${it.total}" },
                    help = Tx.s(R.string.menus_game_achievements_help)) {
                    Nav.closeOverlays(); SteamUi.openFor(g)
                })
            }
            add(MenuAction("edit", Tx.s(R.string.menus_game_edit), Glyph.Pencil, Palette.Butter, value = if (meta.lockedFields.isNotEmpty()) Tx.p(R.plurals.menus_game_edit_locked, meta.lockedFields.size) else null,
                help = Tx.s(R.string.menus_game_edit_help)) {
                GameEditor.edit(g); Nav.closeOverlays()
            })
            if (!g.platform.isApps) {
                // The game's own default: Peanuts Native for a game it has built, else the system's first.
                val pn = io.github.zahakj.tandem.PeanutsNative
                val natural = players.firstOrNull { pn.ready(g) && it.packageName == pn.appFor(g) } ?: players.firstOrNull()
                fun setEmulator(next: io.github.zahakj.tandem.LaunchSpec) = Store.update(g.key) { it.copy(emulator = if (next == natural) null else next.name) }
                fun switchEmulator(by: Int) = setEmulator(players[Math.floorMod(players.indexOf(current) + by, players.size)])
                add(MenuAction("emu", Tx.s(R.string.menus_game_emulator), Glyph.Gamepad, Palette.Sky, value = current?.name ?: Tx.s(R.string.menus_game_emulator_none), more = true,
                    help = Tx.s(R.string.menus_game_emulator_help),
                    step = if (players.size > 1) ::switchEmulator else null,
                    choices = if (players.size > 1) players.map { p ->
                        MenuChoice(
                            p.name, p == current, sub = if (p == natural) Tx.s(R.string.menus_game_emulator_system) else null,
                            art = p.packageName.takeIf { it.isNotBlank() }?.let { pkg -> { AppTileIcon(pkg, 30.dp) } },
                        ) { setEmulator(p) }
                    } else null) {
                    Toasts.show(if (current == null) Tx.s(R.string.menus_game_emulator_get, g.platform.name) else Tx.s(R.string.menus_game_emulator_switch))
                })
            }
            if (screenRow) {
                val bottom = LaunchDisplay.wantsBottom(g)
                val systemBottom = SystemPrefs.config(g.platform.id).bottomScreen
                fun setScreen(want: Boolean) = Store.update(g.key) { it.copy(screen = if (want == systemBottom) null else if (want) "bottom" else "top") }
                val systems = Tx.s(R.string.menus_game_screen_system)
                add(MenuAction("screen", Tx.s(R.string.menus_game_screen), Glyph.Screens, Palette.Butter, value = Tx.s(if (bottom) R.string.menus_game_screen_bottom else R.string.menus_game_screen_top), more = true,
                    help = Tx.s(R.string.menus_game_screen_help), step = { setScreen(!bottom) },
                    choices = listOf(
                        MenuChoice(Tx.s(R.string.menus_game_screen_top), !bottom, sub = systems.takeIf { !systemBottom }) { setScreen(false) },
                        MenuChoice(Tx.s(R.string.menus_game_screen_bottom), bottom, sub = systems.takeIf { systemBottom }) { setScreen(true) },
                    )) { setScreen(!bottom) })
                // What the bottom screen shows while it plays on the top one (GameCompanion.kt).
                val companion = io.github.zahakj.tandem.GameCompanion.modeFor(g)
                // PC games and streams also get the Deck, a keyboard and trackpad for the game.
                val deck = io.github.zahakj.tandem.DeckGames.suits(g.platform.id, current?.packageName)
                val inherited = io.github.zahakj.tandem.GameCompanion.systemMode(g.platform)
                fun setCompanion(next: String) = Store.update(g.key) { it.copy(companion = if (next == inherited) null else next) }
                add(MenuAction("companion", Tx.s(R.string.menus_game_companion), Glyph.Screens, Palette.Lavender, value = io.github.zahakj.tandem.GameCompanion.label(companion), more = true,
                    help = Tx.s(if (deck) R.string.menus_game_companion_help_deck else R.string.menus_game_companion_help),
                    step = { by -> setCompanion(io.github.zahakj.tandem.GameCompanion.next(companion, deck, by)) },
                    choices = io.github.zahakj.tandem.GameCompanion.choices(deck).map { (id, label) ->
                        MenuChoice(label, id == companion, sub = Tx.s(R.string.menus_game_companion_system).takeIf { id == inherited }) { setCompanion(id) }
                    }) { setCompanion(io.github.zahakj.tandem.GameCompanion.next(companion, deck, 1)) })
            }
            // The game's own performance, fan and refresh-rate modes, on an AYN device (Performance.kt).
            addAll(performanceActions(ctx, g))
            add(MenuAction("art", Tx.s(R.string.menus_game_art), Glyph.Image, Palette.Butter, more = true) { GameEditor.chooseArt(g); Nav.closeOverlays() })
            add(MenuAction("scrape", Tx.s(R.string.menus_game_scrape), Glyph.Download, Palette.Butter, more = true) { Nav.closeOverlays(); ScrapeUi.scrapeOne(g) })
            // A game in a hidden folder stays hidden with it, whatever its own switch says.
            val byFolder = HiddenFolders.hides(g)
            add(MenuAction("hide", Tx.s(if (meta.hidden) R.string.menus_game_unhide else R.string.menus_game_hide), Glyph.Eye, Palette.Lavender, value = if (byFolder) Tx.s(R.string.menus_game_folder_hidden) else null, more = true,
                help = if (byFolder) Tx.s(R.string.menus_game_folder_hidden_help) else null) {
                val hiding = !meta.hidden
                Store.update(g.key) { it.copy(hidden = hiding) }
                Sounds.toggle(!hiding)
                Undo.offer(if (hiding) hiddenToast() else Tx.s(R.string.menus_hidden_back)) { Store.update(g.key) { it.copy(hidden = !hiding) } }
                Nav.closeMenus()
            })
            if (meta.everPlayed) add(MenuAction("sessions", Tx.s(R.string.menus_game_sessions), Glyph.Clock, Palette.Sky, more = true) { PlaySessions.openFor(g) })
            if (meta.everPlayed) add(MenuAction("reset", Tx.s(R.string.menus_game_reset), Glyph.Refresh, Palette.Peach, value = if (meta.playSeconds >= 60) formatPlayTime(meta.playSeconds) else null, more = true,
                help = Tx.s(R.string.menus_game_reset_help)) {
                Confirm.hold(
                    Tx.s(R.string.menus_game_reset_title),
                    Tx.s(R.string.menus_game_reset_detail, g.title),
                    Tx.s(R.string.menus_game_reset_yes),
                ) {
                    Store.update(g.key) { it.copy(playSeconds = 0, playCount = 0, lastPlayed = 0) }
                    io.github.zahakj.tandem.data.HomeStore.forgetGame(g.key)
                    Toasts.show(Tx.s(R.string.menus_game_reset_done))
                    FocusMemory.remember("$routeKey:main", "play")
                }
            })
            // Files, art and Tandem's record, after a popup that lists them (DeleteGame.kt). Not for a game of
            // DroidDeck's or GameNative's library: it goes when it is uninstalled there.
            if (!io.github.zahakj.tandem.AppLibraries.owns(g)) add(MenuAction("delete", Tx.s(if (g.platform.isApps) R.string.menus_game_uninstall else R.string.menus_game_delete), Glyph.Trash, Palette.Peach, value = if (running) Tx.s(R.string.menus_game_running) else null, more = true,
                help = if (running) Tx.s(R.string.menus_game_running_help) else null) {
                DeleteGame.ask(ctx, g)
            })
            add(MenuAction("view", Tx.s(R.string.menus_game_view), Glyph.Sort, Palette.Sky, value = Tx.s(R.string.menus_game_view_value), more = true) {
                Nav.menuFor = null
                Nav.openGroupMenu(routeTarget())
            })
        }
        val page = if (StatusUi.pageFor == g.key) StatusUi.page(g, onBack = { StatusUi.pageFor = null }) { StatusUi.pageFor = null; Nav.closeMenus() } else null
        MenuSpec(routeKey, g.title, listOfNotNull(g.platform.name, meta.year?.toString()).joinParts(), SystemColors.of(g.platform.id), actions, page) { GameArt(g) }
    }
}

/** Title for a group menu's target. */
private fun targetTitle(t: GroupTarget?): String = when (t) {
    is GroupTarget.System -> listOfNotNull(Libraries.byId(t.platformId)?.platform?.name ?: Tx.s(R.string.menus_group_games), t.folder).joinToString(" › ")
    is GroupTarget.Collection -> Collections.byId(t.id)?.title ?: Tx.s(R.string.menus_group_collection)
    GroupTarget.Genres -> Tx.s(R.string.menus_group_genres)
    null -> Tx.s(R.string.menus_group_view)
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
            if (canOpen && route != null) add(MenuAction("open", Tx.s(R.string.menus_group_open), Glyph.Play, Palette.Mint) {
                Nav.closeMenus()
                if (route is Route.System || route is Route.Collection) openFolder(route) else Nav.go(route)
            })
            if (system != null && system.folder == null) add(MenuAction("rescan", Tx.s(R.string.menus_group_rescan), Glyph.Refresh, Palette.Mint, value = if (LibraryScan.scanning) Tx.s(R.string.menus_group_looking) else null) {
                Nav.closeMenus(); LibraryScan.rescan(ctx)
            })
            if (system != null) add(MenuAction("scrape", Tx.s(R.string.menus_group_scrape), Glyph.Download, Palette.Butter) {
                Nav.closeMenus()
                ScrapeUi.open(if (system.folder == null) ScrapeScope.System(system.platformId) else ScrapeScope.Folder(system.platformId, system.folder))
            })
            if (system != null && HiddenFolders.canHide(system.platformId, system.folder)) {
                val listed = HiddenFolders.listed(system.platformId, system.folder)
                val undo = { if (listed) HiddenFolders.hide(system.platformId, system.folder) else HiddenFolders.unhide(system.platformId, system.folder) }
                val label = when {
                    system.folder == null -> if (listed) R.string.menus_group_unhide_system else R.string.menus_group_hide_system
                    else -> if (listed) R.string.menus_group_unhide_folder else R.string.menus_group_hide_folder
                }
                add(hideAction(Tx.s(label), listed, undo) {
                    Nav.closeMenus()
                    if (listed) HiddenFolders.unhide(system.platformId, system.folder) else HiddenFolders.hide(system.platformId, system.folder)
                })
            }
            add(MenuAction("sort", Tx.s(R.string.menus_group_sort), Glyph.Sort, Palette.Sky, value = if (ordered) Tx.s(R.string.menus_group_own_order) else s.sort.label,
                help = Tx.s(if (ordered) R.string.menus_group_own_order_help else R.string.menus_group_sort_help)) {
                if (ordered) Toasts.show(Tx.s(R.string.menus_group_own_order_toast))
                else Store.updateSettings { it.copy(sort = SortOrder.entries[(it.sort.ordinal + 1) % SortOrder.entries.size]) }
            })
            add(MenuAction("titles", Tx.s(R.string.menus_group_titles), Glyph.Text, Palette.Sky, value = onOff(s.showTitles),
                help = Tx.s(R.string.menus_group_titles_help)) {
                Store.updateSettings { it.copy(showTitles = !it.showTitles) }
            })
            add(MenuAction("hidden", Tx.s(R.string.menus_group_hidden), Glyph.Eye, Palette.Lavender, value = if (Privacy.enabled) Tx.s(R.string.menus_group_private) else onOff(Privacy.showHidden),
                help = Tx.s(if (Privacy.enabled) R.string.menus_group_private_help else R.string.menus_group_hidden_help)) {
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
        MenuSpec("groupmenu", targetTitle(t), Tx.s(R.string.menus_group_subtitle), color, actions) { TileArtView(art, 120.dp) }
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

/** Favourites [g], or takes it out again, with its sound and an Undo: the Y menu's row and the Favourite button. */
internal fun toggleFavorite(g: Game) {
    val was = Store.get(g.key).favorite
    Store.update(g.key) { it.copy(favorite = !was) }
    if (was) Sounds.toggle(false) else Sounds.favorite()
    Undo.offer(Tx.s(if (was) R.string.menus_fav_removed else R.string.menus_fav_added)) { Store.update(g.key) { it.copy(favorite = was) } }
}

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
                    PillButton(Tx.s(R.string.menus_confirm_cancel), Palette.Mint, Modifier.takesFocus("confirm", "no", target == "no")) { Confirm.close() }
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

// The keys' names are their focus keys too; Space and Clear show in the language (keyLabel).
private val KEYS = ("ABCDEFG" + "HIJKLMN" + "OPQRSTU" + "VWXYZ12" + "3456789" + "0").map { it.toString() } + listOf("Space", "⌫", "Clear")

private fun keyLabel(k: String): String = when (k) {
    "Space" -> Tx.s(R.string.menus_keyboard_space)
    "Clear" -> Tx.s(R.string.common_clear)
    else -> k
}

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
                        Text(Tx.s(R.string.menus_search_prompt), style = MaterialTheme.typography.titleLarge, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    } else {
                        TailText("⌕ $query", Palette.Text)
                    }
                }
                Spacer(Modifier.height(4.dp))
                // A–Z in their usual order in every language.
                LeftToRight { LazyVerticalGrid(GridCells.Fixed(7), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(keyLabel(k), style = MaterialTheme.typography.titleSmall, maxLines = 1) }
                        }
                    }
                } }
                Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ButtonHint("X", Tx.s(R.string.menus_keyboard_delete), Palette.Peach)
                    ButtonHint("B", Tx.s(R.string.common_close), Palette.Lavender)
                    if (resultKeys.isNotEmpty()) ButtonHint("◀▶", Tx.s(R.string.menus_search_results), Palette.Sky)
                }
            }
            Spacer(Modifier.width(14.dp))
            Box(Modifier.weight(1f).fillMaxHeight()) {
                if (results.isEmpty() && finds.isEmpty()) {
                    Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                        Pip(70.dp, mood = if (q.isEmpty()) 0 else 2)
                        Text(if (q.isEmpty()) Tx.s(R.string.menus_search_empty) else Tx.s(R.string.menus_search_none, query.trim()), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (q.isNotEmpty()) Text(Tx.s(R.string.menus_search_accents), style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
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
                            Text(Tx.p(if (results.size >= 60) R.plurals.menus_search_matches_more else R.plurals.menus_search_matches, results.size), style = MaterialTheme.typography.labelLarge, color = Palette.Muted)
                            Spacer(Modifier.weight(1f))
                            ButtonHint("A", Tx.s(R.string.menus_search_play), Palette.Mint)
                            ButtonHint("Y", Tx.s(R.string.menus_search_options), Palette.Butter)
                            ButtonHint("B", Tx.s(R.string.menus_search_keys), Palette.Lavender)
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
        m < 1 -> Tx.s(R.string.menus_time_short_under)
        m < 60 -> Tx.p(R.plurals.menus_time_short_minutes, m.toInt())
        else -> (m / 60).toInt().let { h -> Tx.p(R.plurals.menus_time_short_hours, h, h, (m % 60).toInt()) }
    }
}

/** Play time in words: "1 min", "45 min", "2 h 5 min". */
fun formatPlayLong(seconds: Long): String {
    val m = seconds / 60
    return when {
        m < 1 -> Tx.s(R.string.menus_time_long_under)
        m < 60 -> Tx.p(R.plurals.menus_time_long_minutes, m.toInt())
        m % 60 == 0L -> Tx.p(R.plurals.menus_time_long_hours, (m / 60).toInt())
        else -> (m / 60).toInt().let { h -> Tx.p(R.plurals.menus_time_long_hours_minutes, h, h, (m % 60).toInt()) }
    }
}
