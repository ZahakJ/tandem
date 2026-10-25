package io.github.zahakj.tandem.ui

import androidx.compose.animation.AnimatedVisibility
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

    val recentShown: Boolean
        get() = when (ExtrasStore.settings.recentStrip) {
            "always" -> true
            "off" -> false
            else -> recentOpen
        }

    /** L2 on the icon grid: show or hide the strip, and land on its first game when it opens. */
    fun toggleRecent() {
        when (ExtrasStore.settings.recentStrip) {
            "off" -> { Toasts.show("The Recently played strip is off. Settings › Motion & home"); return }
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
        if (Nav.route == Route.Home && !Nav.overlayOpen) {
            FocusMemory.remember(Route.Home.key, HomeExtras.RESUME)
            Nav.focusTick++
        }
    }
}

/**
 * The Resume page: a big card with the running game's art drifting, its logo floating, play time this session and
 * the buttons. [routeKey] / [target] hook it into the grid's focus like a tile.
 */
@Composable
fun ResumePage(running: RunningGame, routeKey: String, target: String?, modifier: Modifier) {
    val ctx = LocalContext.current
    val game = running.game ?: return
    val color = SystemColors.of(game.platform.id)
    val session = RunningGames.sessionSeconds(running)
    val others = RunningGames.list.count { it.pkg != running.pkg }
    FocusCard(
        modifier.takesFocus(routeKey, HomeExtras.RESUME, target == HomeExtras.RESUME), glow = color, shape = RoundedCornerShape(28.dp),
        onFocus = { Selection.current = Selected.GameItem(game); IconGrid.spot = null; Panels.focusedApp = null },
        entry = true,
        // Y here starts closing the game, so a long press (which is Y elsewhere) opens the game's options instead.
        onLongPress = { Nav.openMenu(game) },
        onClick = { RunningGames.resume(ctx, running) },
    ) { focused ->
        // The card can keep the highlight while the game in it changes (one closed, another started), and onFocus only
        // runs when the highlight arrives: say the new game, or the name above and the bottom screen show the old one.
        LaunchedEffect(focused, game.key) { if (focused) Selection.current = Selected.GameItem(game) }
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
                    Box(Modifier.size(9.dp).clip(CircleShape).background(Palette.Mint).floating(3, bob = 1.5.dp, periodMs = 1400, breathe = 0.2f, tilt = 0f, restsWhenIdle = true))
                    Spacer(Modifier.width(7.dp))
                    Text("CONTINUE", style = MaterialTheme.typography.labelLarge.onArt(), color = Color.White, maxLines = 1)
                }
                // The logo takes whatever height is left, so the text and buttons below always fit the card.
                Box(Modifier.fillMaxWidth().weight(1f).padding(vertical = 6.dp), contentAlignment = Alignment.CenterStart) {
                    if (logo != null) {
                        HeroLogo(game, Modifier.fillMaxWidth(0.66f).riseIn(game.key, 0).floating(game.key.hashCode(), bob = 4.dp, restsWhenIdle = true), maxHeight = 96, textFallback = false)
                    }
                }
                Column(Modifier.riseIn(game.key, 1, 14.dp)) {
                    Text(game.title, style = MaterialTheme.typography.titleLarge.onArt(), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val line = listOfNotNull(
                        game.platform.name,
                        session?.takeIf { it >= 60 }?.let { "${formatPlayTime(it)} this session" },
                        running.appName,
                    ).joinToString(" · ")
                    Text(line, style = MaterialTheme.typography.labelLarge.onArt(), color = lerp(color, Color.White, 0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (roomy) {
                        Spacer(Modifier.height(8.dp))
                        Row(
                            Modifier.graphicsLayer { alpha = if (focused) 1f else 0.75f }.darkChip().padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ButtonHint("A", "Resume", Palette.Mint)
                            ButtonHint("Y", "Close", Palette.Peach)
                            if (others > 0) ButtonHint("X", "Switch", Palette.Sky)
                        }
                    }
                }
            }
        }
    }
}

/** Y on the Resume page: close the game (press twice, closing loses unsaved progress). */
private val resumeConfirm = PressTwice()

fun resumePageClose(ctx: android.content.Context, running: RunningGame) {
    if (!resumeConfirm.press(running.pkg, "Press Y again to close ${running.game?.title ?: running.appName}")) return
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
                Text("Recently played", style = MaterialTheme.typography.labelLarge, color = Palette.Muted, maxLines = 1)
                if (ExtrasStore.settings.recentStrip == "button") {
                    Spacer(Modifier.width(10.dp))
                    ButtonHint("L2", "Hide", Palette.Lavender)
                }
            }
            LazyRow(
                Modifier.fillMaxWidth().height(92.dp).fadingEdges(state, horizontal = true), state = state,
                contentPadding = PaddingValues(horizontal = 30.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp),
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
                                    if (g.key in running) "● Running" else g.platform.shortName, style = MaterialTheme.typography.labelSmall,
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
