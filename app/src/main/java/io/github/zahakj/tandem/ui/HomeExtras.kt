package io.github.zahakj.tandem.ui

import io.github.zahakj.tandem.R
import io.github.zahakj.tandem.Tx
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.BoxWithConstraints
import io.github.zahakj.tandem.Libraries
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.zahakj.tandem.Collections
import io.github.zahakj.tandem.Game
import io.github.zahakj.tandem.Launcher
import io.github.zahakj.tandem.MainActivity
import io.github.zahakj.tandem.Privacy
import io.github.zahakj.tandem.RunningGame
import io.github.zahakj.tandem.RunningGames
import io.github.zahakj.tandem.Selected
import io.github.zahakj.tandem.Selection
import io.github.zahakj.tandem.data.ExtrasStore
import io.github.zahakj.tandem.data.Store

/*
 * Two things around the icon grid, in place of the old Now playing and Recently played tiles:
 * - The Resume page, left of the first column: shown while a game is running, the
 *   game's moving art with its logo floating over it, play time this session and what A / Y / X do. Coming Home from
 *   a running game lands on it (Motion & home settings).
 * - The Recently played strip: the last games along the top of the grid, on demand (L2) or always.
 */

object HomeExtras {
    /** The strip is showing because L2 asked for it (the "always" setting shows it regardless). */
    var recentOpen by mutableStateOf(false)

    /** The resume count the Resume page last landed for, so it lands once per return to Tandem. */
    var landedFor by mutableIntStateOf(-1)

    const val RESUME = "resume"
    fun recentKey(g: Game) = "recent:${g.key}"

    /** The game the wide first card shows, chosen with L2 / R2 (null: the first, a running game when there is one). */
    var cardKey by mutableStateOf<String?>(null)

    /**
     * The card holds the highlight right now. The grid's buttons ask this rather than the remembered item, which can
     * name something else while the card keeps the focus (a layout tried and put back remembers where it was), and
     * then R2 on the card opened the side panel.
     */
    var cardFocused = false

    /** L2 (-1) / R2 (+1) on the card: the game before or after in [deck]. */
    fun stepCard(deck: List<Game>, by: Int) {
        if (deck.size < 2) return
        val at = ContinueDeck.shown(deck.map { it.key }, cardKey)
        cardKey = deck[ContinueDeck.step(at, by, deck.size)].key
        cardTurn = by
        Sounds.move()
    }

    /** Which way the card last turned (+1 R2, -1 L2), for its slide. */
    var cardTurn = 1
        private set

    val recentShown: Boolean
        get() = when (ExtrasStore.settings.recentStrip) {
            "always" -> true
            "off" -> false
            else -> recentOpen
        }

    /** L2 on the icon grid: show or hide the strip, and land on its first game when it opens. */
    fun toggleRecent() {
        when (ExtrasStore.settings.recentStrip) {
            "off" -> { Toasts.show(Tx.s(R.string.home_recent_off)); return }
            "always" -> {
                Collections.continuePlaying().firstOrNull()?.let { FocusMemory.remember(Route.Home.key, recentKey(it)); Nav.focusTick++ }
                return
            }
        }
        recentOpen = !recentOpen
        if (recentOpen) Collections.continuePlaying().firstOrNull()?.let { FocusMemory.remember(Route.Home.key, recentKey(it)) }
        Nav.focusTick++
    }
}

/** The running game the Resume page shows, if any. */
fun resumable(): RunningGame? = nowPlaying()?.takeIf { it.game != null && IconGrid.enabled }

/** Lands on the Resume page once each time Tandem comes back while a game runs. */
@Composable
fun ResumeLanding(running: RunningGame?) {
    val ctx = LocalContext.current
    // Counted a few frames after each return (MainActivity.returnCount), not with the later re-checks.
    val resumes = (ctx as? MainActivity)?.returnCount?.intValue ?: 0
    val key = running?.game?.key
    LaunchedEffect(resumes, key) {
        if (key == null || !ExtrasStore.settings.resumeLanding || HomeExtras.landedFor == resumes || resumes < 1) return@LaunchedEffect
        HomeExtras.landedFor = resumes
        // The card shows the running game again, whichever L2 / R2 had picked before.
        HomeExtras.cardKey = null
        if (Nav.route == Route.Home && !Nav.overlayOpen) {
            FocusMemory.remember(Route.Home.key, HomeExtras.RESUME)
            Nav.focusTick++
        }
    }
}

/**
 * The wide card leading the icon grid ([HomeExtras.RESUME]). While [game] runs ([running]) it is the Resume page: its
 * art drifting, its logo floating, play time this session; A resumes it, Y closes it (twice), X switches games. With
 * Continue card on it shows the last games played the rest of the time: A plays, Y opens the game's options. L2 / R2
 * step through [deck] while it has the highlight, the card sliding the way they went, its dots saying where.
 * [routeKey] / [target] hook it into the grid's focus like a tile.
 */
@Composable
fun ResumePage(game: Game, running: RunningGame?, deck: List<Game>, routeKey: String, target: String?, modifier: Modifier) {
    val ctx = LocalContext.current
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { HomeExtras.cardFocused = false } }
    FocusCard(
        modifier.onFocusChanged { HomeExtras.cardFocused = it.hasFocus }.takesFocus(routeKey, HomeExtras.RESUME, target == HomeExtras.RESUME), glow = SystemColors.of(game.platform.id), shape = RoundedCornerShape(28.dp),
        onFocus = { Selection.current = Selected.GameItem(game); IconGrid.spot = null; Panels.focusedApp = null },
        entry = true,
        // Y here may start closing the game, so a long press (which is Y elsewhere) opens the game's options instead.
        onLongPress = { Nav.openMenu(game) },
        onClick = { if (running != null) RunningGames.resume(ctx, running) else Launcher.launch(ctx, game) },
    ) { focused ->
        // The card can keep the highlight while the game in it changes (one closed, another started, L2 / R2), and
        // onFocus only runs when the highlight arrives: say the new game, or the name above and the bottom screen
        // show the old one.
        LaunchedEffect(focused, game.key) { if (focused) Selection.current = Selected.GameItem(game) }
        val still = Motion.style == AnimationStyle.Off || Store.settings.reduceMotion
        val sign = startSign
        AnimatedContent(
            game to running, Modifier.fillMaxSize(), label = "continue-card",
            contentKey = { it.first.key },
            transitionSpec = {
                val dir = HomeExtras.cardTurn * sign.toInt()
                if (still) fadeIn(Motion.fade(120)) togetherWith fadeOut(Motion.fade(80))
                else (slideInHorizontally(Motion.move()) { it / 3 * dir } + fadeIn(Motion.fade(200))) togetherWith
                    (slideOutHorizontally(Motion.move()) { -it / 3 * dir } + fadeOut(Motion.fade(120)))
            },
        ) { (g, r) -> CardFace(g, r, focused, deck) }
    }
}

/** What the wide card shows of [game]: its art, its logo, a line about it and the buttons. */
@Composable
private fun CardFace(game: Game, running: RunningGame?, focused: Boolean, deck: List<Game>) {
    val color = SystemColors.of(game.platform.id)
    val session = running?.let { RunningGames.sessionSeconds(it) }
    val others = if (running == null) 0 else RunningGames.list.count { it.pkg != running.pkg }
    // Only a cut-out logo floats over the art (Library.logo skips opaque title cards); without one the name below
    // says it, rather than the name twice.
    val logo = remember(game.key, Store.meta) { Libraries.of(game)?.logo(game) }
    BoxWithConstraints(Modifier.fillMaxSize().background(lerp(color, Palette.Night, 0.55f))) {
        // Short cards (a hero banner above the grid, say) drop the button hints before anything gets cut.
        val roomy = maxHeight >= 168.dp
        // A soft blur of the art fills the card behind the text; the art itself sits crisp on the right and fades
        // in from the left, so any hard edge in the picture (scraped art is often a composite of box, device
        // and logo) or around it melts into the blur instead of showing as a seam.
        val still = remember(game.key, Store.meta) { Libraries.of(game)?.let { it.hero(game) ?: it.screenshot(game) ?: it.box(game) } }
        if (still != null) {
            AsyncImage(
                still, null, Modifier.fillMaxSize().blur(26.dp).graphicsLayer { alpha = 0.5f },
                contentScale = ContentScale.Crop,
            )
        }
        Box(Modifier.fillMaxSize().fadeMaskHorizontal(0f to 0f, 0.32f to 0.15f, 0.55f to 0.95f, 0.85f to 0.9f, 1f to 0.3f)) {
            HeroArt(game, Modifier.fillMaxSize())
        }
        Column(Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // The breathing dot: this game is running now.
                if (running != null) {
                    Box(Modifier.size(9.dp).clip(CircleShape).background(Palette.Mint).floating(3, bob = 1.5.dp, periodMs = 1400, breathe = 0.2f, tilt = 0f, restsWhenIdle = true))
                    Spacer(Modifier.width(7.dp))
                }
                Text(
                    Tx.s(if (running != null) R.string.home_resume_continue else R.string.home_continue_last), Modifier.weight(1f, fill = false),
                    style = MaterialTheme.typography.labelLarge.onArt(), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (deck.size > 1) {
                    Spacer(Modifier.weight(1f))
                    DeckDots(deck, game.key, focused)
                }
            }
            // The logo takes whatever height is left, so the text and buttons below always fit the card.
            Box(Modifier.fillMaxWidth().weight(1f).padding(vertical = 6.dp), contentAlignment = Alignment.CenterStart) {
                if (logo != null) {
                    HeroLogo(game, Modifier.fillMaxWidth(0.66f).riseIn(game.key, 0).floating(game.key.hashCode(), bob = 4.dp, restsWhenIdle = true), maxHeight = 96, textFallback = false)
                }
            }
            Column(Modifier.riseIn(game.key, 1, 14.dp)) {
                Text(game.title, style = MaterialTheme.typography.titleLarge.onArt(), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val m = game.meta
                val line = if (running != null) listOfNotNull(
                    game.platform.name,
                    session?.takeIf { it >= 60 }?.let { Tx.s(R.string.home_resume_session, formatPlayTime(it)) },
                    running.appName,
                ) else listOfNotNull(
                    game.platform.name,
                    m.playSeconds.takeIf { it >= 60 }?.let { formatPlayTime(it) },
                    m.lastPlayed.takeIf { it > 0 }?.let { io.github.zahakj.tandem.Dates.relative(it) },
                )
                Text(line.joinParts(), style = MaterialTheme.typography.labelLarge.onArt(), color = lerp(color, Color.White, 0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (roomy) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier.graphicsLayer { alpha = if (focused) 1f else 0.75f }.darkChip().padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (running != null) {
                            ButtonHint("A", Tx.s(R.string.home_resume_hint_resume), Palette.Mint)
                            ButtonHint("Y", Tx.s(R.string.common_close), Palette.Peach)
                            if (others > 0) ButtonHint("X", Tx.s(R.string.home_resume_hint_switch), Palette.Sky)
                        } else {
                            ButtonHint("A", Tx.s(R.string.home_continue_hint_play), Palette.Mint)
                            ButtonHint("Y", Tx.s(R.string.home_continue_hint_options), Palette.Butter)
                        }
                    }
                }
            }
        }
    }
}

/** L2, a dot for each game on the card (its own lit), R2: they step through them while the card has the highlight. */
@Composable
private fun DeckDots(deck: List<Game>, shown: String, focused: Boolean) {
    Row(
        Modifier.graphicsLayer { alpha = if (focused) 1f else 0.6f }.darkChip().padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        // Trying layouts L2 and R2 change the layout's size instead: the dots stay, the buttons fade.
        Box(Modifier.awayWhileTrying()) { ButtonHint("L2", "", Palette.Lavender, tappable = focused && !LayoutSwitch.open) }
        deck.forEach { g ->
            val on = g.key == shown
            Box(Modifier.size(if (on) 8.dp else 6.dp).clip(CircleShape).background(if (on) Color.White else Color.White.copy(alpha = 0.4f)))
        }
        Box(Modifier.awayWhileTrying()) { ButtonHint("R2", "", Palette.Lavender, tappable = focused && !LayoutSwitch.open) }
    }
}

/** The games the wide card steps through: the running ones (Resume page on), then the last played (Continue card on). */
@Composable
fun rememberContinueDeck(enabled: Boolean): List<Game> {
    val running = RunningGames.list
    val meta = Store.meta
    val hidden = Privacy.view
    val look = Store.settings.look
    val extras = ExtrasStore.settings
    return remember(enabled, running, meta, hidden, Libraries.all, look.nowPlaying, extras.continueCard) {
        if (!enabled) emptyList()
        else ContinueDeck.pick(
            if (look.nowPlaying) running.mapNotNull { it.game } else emptyList(),
            if (extras.continueCard) Collections.continuePlaying() else emptyList(),
            { it.key }, { Privacy.canShow(it) },
        )
    }
}


/** Y on the Resume page: close the game (press twice, closing loses unsaved progress). */
private val resumeConfirm = PressTwice()

fun resumePageClose(ctx: android.content.Context, running: RunningGame) {
    if (!resumeConfirm.press(running.pkg, Tx.s(R.string.home_resume_close_again, running.game?.title ?: running.appName))) return
    closeRunning(ctx, running) { Nav.focusTick++ }
}

/**
 * The Recently played strip along the top of the icon grid. Always on, it takes its own room above the grid; shown
 * with L2 ([overlay]), it slides down over the top of the grid on a soft sheet instead, so the grid underneath keeps
 * its rows and every tile stays exactly where it was.
 */
@Composable
fun RecentStrip(routeKey: String, target: String?, games: List<Game>, overlay: Boolean = false, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    AnimatedVisibility(
        HomeExtras.recentShown && games.isNotEmpty(), modifier,
        enter = if (overlay) slideInVertically(Motion.move()) { -it } + fadeIn() else expandVertically(Motion.move()) + fadeIn(),
        exit = if (overlay) slideOutVertically(Motion.move()) { -it } + fadeOut() else shrinkVertically(Motion.move()) + fadeOut(),
    ) {
        val state = rememberLazyListState()
        val running = RunningGames.list.mapNotNull { it.game?.key }.toHashSet()
        val sheet = if (overlay) {
            Modifier.fillMaxWidth()
                .graphicsLayer { shape = RoundedCornerShape(bottomStart = 26.dp, bottomEnd = 26.dp); clip = false; shadowElevation = 18f * density }
                .background(Palette.Card.copy(alpha = 0.97f), RoundedCornerShape(bottomStart = 26.dp, bottomEnd = 26.dp))
                .padding(bottom = 4.dp)
        } else Modifier
        Column(sheet) {
            Row(Modifier.padding(start = 30.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                GlyphIcon(Glyph.Clock, Palette.Mint, Modifier.size(15.dp))
                Spacer(Modifier.width(6.dp))
                Text(Tx.s(R.string.home_recent_title), style = MaterialTheme.typography.labelLarge, color = Palette.Muted, maxLines = 1)
                if (ExtrasStore.settings.recentStrip == "button") {
                    Spacer(Modifier.width(10.dp))
                    ButtonHint("L2", Tx.s(R.string.home_recent_hint_hide), Palette.Lavender)
                }
            }
            LazyRow(
                Modifier.fillMaxWidth().height(68.dp + FocusRoom * 2).fadingEdges(state, horizontal = true), state = state,
                contentPadding = PaddingValues(horizontal = 30.dp, vertical = FocusRoom), horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                itemsIndexed(games, key = { _, g -> g.key }) { _, g ->
                    val k = HomeExtras.recentKey(g)
                    FocusCard(
                        Modifier.width(150.dp).fillMaxHeight().takesFocus(routeKey, k, k == target), glow = SystemColors.of(g.platform.id),
                        shape = RoundedCornerShape(16.dp),
                        onFocus = { Selection.current = Selected.GameItem(g); IconGrid.spot = null; Panels.focusedApp = null },
                        entry = true,
                        onClick = { Launcher.launch(ctx, g) },
                    ) {
                        Row(Modifier.fillMaxSize().padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(56.dp).clip(RoundedCornerShape(11.dp))) { GameArt(g) }
                            Column(Modifier.weight(1f).padding(start = 8.dp)) {
                                Text(g.title, style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(
                                    if (g.key in running) Tx.s(R.string.home_recent_running) else g.platform.shortName, style = MaterialTheme.typography.labelSmall,
                                    color = if (g.key in running) Palette.Mint else Palette.Muted, maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The games the strip shows (hidden ones only while revealed). */
@Composable
fun rememberRecentGames(): List<Game> {
    val meta = Store.meta
    val hidden = Privacy.view
    return remember(meta, hidden, io.github.zahakj.tandem.Libraries.all) { Collections.continuePlaying().take(10) }
}
