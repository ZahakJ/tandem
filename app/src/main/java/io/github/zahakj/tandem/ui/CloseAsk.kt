package io.github.zahakj.tandem.ui

import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import io.github.zahakj.tandem.Button
import io.github.zahakj.tandem.CloseCheck
import io.github.zahakj.tandem.Launcher
import io.github.zahakj.tandem.Libraries
import io.github.zahakj.tandem.Privacy
import io.github.zahakj.tandem.RunningGame
import io.github.zahakj.tandem.RunningGames
import io.github.zahakj.tandem.data.HomeStore
import io.github.zahakj.tandem.data.Store

/**
 * A launch that would close games still running, waiting for the player to choose (see [Launcher.launch]):
 * resume the running game (the safe choice, highlighted first), close it and start [plan]'s game (A twice), or
 * cancel (B). Y turns the question off.
 */
class CloseAsk(val ctx: Context, val plan: Launcher.Plan, val closing: List<RunningGame>, val id: Int) {
    companion object {
        const val ROUTE = "closeask"
    }
}

/** "Close and start" needs a second press, like closing a game in the switcher. */
private val closeConfirm = PressTwice()

/** The most games listed one by one when several would close; the rest are counted. */
private const val MAX_LISTED = 3

/** How a running game may be shown: never named, pictured or timed while private mode hides it. */
private fun RunningGame.entry(): CloseCheck.Entry =
    CloseCheck.Entry(game?.title, appName, hidden = game?.let { !Privacy.canShow(it) } ?: false)

/** One line under a running game's name: how long it has been played this time, or where it runs. */
private fun RunningGame.detail(): String? {
    val g = game ?: return "$appName · opened outside Tandem"
    if (!Privacy.canShow(g)) return null
    val played = RunningGames.sessionSeconds(this)
    return if (played != null && played > 0) "$appName · played ${formatPlayTime(played)} this time" else "${g.platform.shortName} · $appName"
}

/** Placed in [LaunchOverlay], so it shows wherever the library is drawn. */
@Composable
internal fun CloseAskPanel() {
    val ask = LaunchFx.closeAsk
    val shown = rememberLatest(ask)
    val cancel: () -> Unit = { LaunchFx.endCloseAsk() }
    PanelGuard(ask != null)
    // Y: don't ask again. Every other button stays with the question.
    InterceptButtons(ask != null) { b ->
        if (b == Button.Y) {
            HomeStore.updateSettings { it.copy(askBeforeClosing = false) }
            cancel()
            Sounds.toggle(false)
            Toasts.show("I won't ask again. Turn it back on in Settings › Play & stats › Playing", 4500)
        }
        true
    }
    LaunchedEffect(ask?.id) { if (ask != null) Sounds.pipSays("Wait, a game is still running") }
    OverlayPanel(ask != null, onClose = cancel) {
        val a = shown ?: return@OverlayPanel
        val entries = a.closing.map { it.entry() }
        val first = a.closing.first()
        val (title, body) = CloseCheck.headline(entries, a.plan.game.title)
        val target = rememberFocusTarget(CloseAsk.ROUTE, listOf("resume", "close", "cancel"))
        PanelSurface(Modifier.widthIn(min = 380.dp, max = 620.dp)) {
            if (a.closing.size == 1) {
                SingleHeader(a, first, entries.first(), title)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Pip(60.dp, mood = 3)
                    Spacer(Modifier.width(14.dp))
                    Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = Palette.Muted, maxLines = 3, overflow = TextOverflow.Ellipsis)
            if (a.closing.size > 1) {
                Spacer(Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    a.closing.take(MAX_LISTED).forEach { r -> GameLine(r, r.entry()) }
                    val more = a.closing.size - MAX_LISTED
                    if (more > 0) Text("and $more more", style = MaterialTheme.typography.labelMedium, color = Palette.Muted, maxLines = 1)
                }
            }
            Spacer(Modifier.height(14.dp))
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AskButton(CloseCheck.resumeLabel(entries.first()), Palette.Mint, Modifier.takesFocus(CloseAsk.ROUTE, "resume", target == "resume")) {
                    cancel()
                    RunningGames.resume(a.ctx, first)
                }
                val armed = closeConfirm.armed == "close:${a.id}"
                AskButton(
                    if (armed) "Press again to close" else "Close and start ${a.plan.game.title}", Palette.Peach,
                    Modifier.takesFocus(CloseAsk.ROUTE, "close", target == "close"),
                ) {
                    if (closeConfirm.press("close:${a.id}")) {
                        cancel()
                        Launcher.proceed(a.ctx, a.plan)
                    }
                }
                AskButton("Cancel", Palette.Lavender, Modifier.takesFocus(CloseAsk.ROUTE, "cancel", target == "cancel"), onClick = cancel)
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                ButtonHint("A", "Choose", Palette.Mint)
                ButtonHint("B", "Cancel", Palette.Lavender)
                ButtonHint("Y", "Don't ask again", Palette.Butter)
            }
        }
    }
}

/**
 * One running game: its cover (with a little worried wobble as the question appears), its hero art fading in
 * behind, and Pip peeking from the corner. A hidden game shows a plain shield instead, and no name or time.
 */
@Composable
private fun SingleHeader(a: CloseAsk, r: RunningGame, e: CloseCheck.Entry, title: String) {
    val game = r.game?.takeIf { !e.hidden }
    val color = game?.let { Palette.forName(it.platform.id) } ?: Palette.Lavender
    val wobble = remember(a.id) { Animatable(0f) }
    LaunchedEffect(a.id) {
        if (Store.settings.reduceMotion || Themes.still) return@LaunchedEffect
        for (to in listOf(-4f, 3.5f, -2f, 1f, 0f)) wobble.animateTo(to, tween(90))
    }
    Box(
        Modifier.fillMaxWidth().height(132.dp).clip(RoundedCornerShape(18.dp))
            // The game's own colour, deepened: no grey band behind the text.
            .background(lerp(color, Palette.Card, 0.82f)),
    ) {
        val lib = game?.let { Libraries.of(it) }
        val hero = game?.let { lib?.hero(it) ?: lib?.screenshot(it) }
        // The art fades out on the left, where the text sits.
        if (hero != null) AsyncImage(hero, null, Modifier.fillMaxSize().fadeMaskHorizontal(0f to 0f, 0.4f to 0.2f, 1f to 0.75f), contentScale = ContentScale.Crop)
        Row(Modifier.fillMaxSize().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.fillMaxHeight().width(112.dp)) {
                Box(
                    Modifier.size(104.dp).align(Alignment.CenterStart)
                        .graphicsLayer { rotationZ = wobble.value }
                        .shadow(10.dp, RoundedCornerShape(14.dp), ambientColor = color, spotColor = color)
                        .clip(RoundedCornerShape(14.dp)),
                ) { Cover(r, e, 104.dp) }
                Pip(44.dp, Modifier.align(Alignment.BottomEnd).offset(x = 6.dp, y = 4.dp), mood = 3)
            }
            Column(Modifier.weight(1f).padding(start = 12.dp, end = 4.dp)) {
                val kicker = if (e.hidden) "● STILL RUNNING" else "● STILL RUNNING IN ${r.appName.uppercase()}"
                Text(kicker, style = MaterialTheme.typography.labelMedium.onArt(), color = Palette.Peach, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(title, style = MaterialTheme.typography.titleLarge.onArt(), maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (!e.hidden) r.detail()?.let {
                    Text(it, style = MaterialTheme.typography.labelMedium.onArt(), color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** One of several running games, in the list. */
@Composable
private fun GameLine(r: RunningGame, e: CloseCheck.Entry) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp))) { Cover(r, e, 44.dp) }
        Column(Modifier.padding(start = 10.dp)) {
            Text(CloseCheck.name(e), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!e.hidden) r.detail()?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}

/** The game's cover, the emulator's icon when it isn't a Tandem game, or a shield for a hidden one. */
@Composable
private fun Cover(r: RunningGame, e: CloseCheck.Entry, size: Dp) {
    val game = r.game
    when {
        e.hidden -> Box(Modifier.fillMaxSize().background(Palette.Lavender.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
            GlyphIcon(Glyph.Shield, Palette.Lavender, Modifier.size(size * 0.5f))
        }
        game != null -> GameArt(game)
        else -> Box(Modifier.fillMaxSize().background(Palette.CardHigh), contentAlignment = Alignment.Center) {
            AppIcon(r.pkg, Modifier.size(size * 0.7f))
        }
    }
}

/** A one-line rounded button; long game titles ellipsize instead of running off the panel. */
@Composable
private fun AskButton(text: String, color: Color, modifier: Modifier, onClick: () -> Unit) {
    FocusCard(modifier.height(42.dp).widthIn(max = 300.dp), glow = color, shape = RoundedCornerShape(21.dp), onClick = onClick) {
        Box(Modifier.fillMaxHeight().background(color.copy(alpha = 0.22f)).padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
            Text(text, style = MaterialTheme.typography.titleSmall, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
