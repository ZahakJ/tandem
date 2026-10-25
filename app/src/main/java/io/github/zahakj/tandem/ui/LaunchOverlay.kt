package io.github.zahakj.tandem.ui

import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import io.github.zahakj.tandem.Emulators
import io.github.zahakj.tandem.LaunchProblem
import io.github.zahakj.tandem.Launcher
import io.github.zahakj.tandem.Processes
import io.github.zahakj.tandem.data.Store
import io.github.zahakj.tandem.data.SystemPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** The launch animation and Pip's launch problems. Shown above everything by [LaunchOverlay]. */
object LaunchFx {
    class Launch(val ctx: Context, val plan: Launcher.Plan, val id: Int) {
        /** Set once the emulator has been asked to start: a launch is never started twice. */
        @Volatile var started = false
    }

    /** The game being launched right now, while its art zooms. */
    var playing by mutableStateOf<Launch?>(null)
        private set

    /** Something that stopped the last launch, explained by Pip. */
    var problem by mutableStateOf<LaunchProblem?>(null)

    private var serial = 0
    private var hosts = 0

    /** True while a launch is under way; a second press waits for it instead of starting twice. */
    val busy: Boolean get() = playing != null

    /** Animate only when the overlay is on screen and the user hasn't turned motion down. */
    fun canAnimate(): Boolean = hosts > 0 && !Store.settings.reduceMotion && SystemPrefs.data.launchAnimation

    fun play(ctx: Context, plan: Launcher.Plan) { playing = Launch(ctx, plan, ++serial); PipMind.cheer() }

    /** A launch waiting for an answer: starting it would close the games in it (CloseAsk.kt). */
    var closeAsk by mutableStateOf<CloseAsk?>(null)
        private set

    fun askToClose(ctx: Context, plan: Launcher.Plan, closing: List<io.github.zahakj.tandem.RunningGame>) {
        FocusMemory.forget(CloseAsk.ROUTE)
        closeAsk = CloseAsk(ctx, plan, closing, ++serial)
    }

    fun endCloseAsk() { closeAsk = null; Nav.focusTick++ }

    internal fun done() { playing = null; Nav.focusTick++ }
    internal fun attach() { hosts++ }
    internal fun detach() { hosts-- }
}

/** Counts popups owned by features, so the app's own button handling stays out of their way. */
@Composable
fun PanelGuard(visible: Boolean) {
    if (!visible) return
    DisposableEffect(Unit) {
        Nav.openPanels++
        onDispose { Nav.openPanels-- }
    }
}

/** Place once in the app root, above the screens. */
@Composable
fun LaunchOverlay() {
    val ctx = LocalContext.current
    DisposableEffect(Unit) {
        LaunchFx.attach()
        onDispose { LaunchFx.detach() }
    }
    // Newly installed or removed apps show up in Android Games, but only once Tandem is in front again, so nothing
    // runs while a game is playing.
    val lifecycle = (ctx as? androidx.lifecycle.LifecycleOwner)?.lifecycle
    val changes = Emulators.changes
    LaunchedEffect(changes) {
        if (changes == 0 || lifecycle == null) return@LaunchedEffect
        delay(800)
        lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.RESUMED) }
        reloadLibrary(ctx)
    }
    LaunchFx.playing?.let { LaunchAnimation(it) }
    CloseAskPanel()
    ProblemPanel()
}

/**
 * The game's art pops forward, then zooms past the screen while everything fades to night; the emulator opens on
 * the last frame. Games going to the bottom screen drop downwards instead.
 */
@Composable
private fun LaunchAnimation(launch: LaunchFx.Launch) {
    val plan = launch.plan
    val game = plan.game
    val pop = remember(launch.id) { Animatable(0f) }
    val zoom = remember(launch.id) { Animatable(0f) }
    val focus = remember { FocusRequester() }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    // With the screens swapped this overlay lives in the bottom-screen window, which is closed as the game takes
    // over: the effect below is cancelled mid-wait. The launch must still end there, or the next time that window is
    // composed (Home, say) it would start the game all over again.
    DisposableEffect(launch.id) { onDispose { if (launch.started && LaunchFx.playing === launch) LaunchFx.done() } }
    LaunchedEffect(launch.id) {
        if (launch.started) { LaunchFx.done(); return@LaunchedEffect }
        // Take focus so the D-pad can't move the grid underneath mid-launch.
        runCatching { focus.requestFocus() }
        // Ask Shizuku which apps are running while the art animates, so starting is instant at the end.
        launch(Dispatchers.IO) { Processes.names(maxAgeMs = 0) }
        launch { pop.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow)) }
        delay(240)
        zoom.animateTo(1f, tween(360, easing = FastOutSlowInEasing))
        launch.started = true
        val problem = withContext(Dispatchers.IO) { Launcher.start(launch.ctx, plan) }
        if (problem != null) {
            LaunchFx.done()
            LaunchFx.problem = problem
            return@LaunchedEffect
        }
        // Stay dark while the emulator comes up, then clear so Tandem looks normal when the player returns. A cold
        // start (Eden, say) can take longer than that: until Tandem is actually covered, a few seconds at most, or
        // the library would flash back between the launch and the game.
        delay(1200)
        if (!plan.bottom) withTimeoutOrNull(3_800) { lifecycle.currentStateFlow.first { !it.isAtLeast(Lifecycle.State.STARTED) } }
        LaunchFx.done()
    }
    val p = pop.value
    val z = zoom.value
    val bottom = remember(launch.id) { plan.bottom && io.github.zahakj.tandem.LaunchDisplay.optionsFor(launch.ctx, true) != null }
    Box(
        Modifier.fillMaxSize()
            .focusRequester(focus).focusable().onKeyEvent { true }
            .background(Palette.NightDeep.copy(alpha = (0.55f * p.coerceAtMost(1f) + 0.45f * z).coerceIn(0f, 1f))),
        contentAlignment = Alignment.Center,
    ) {
        // The art sits at the very centre of the screen and the name hangs below it without moving it: the zoom grows
        // from the middle and covers the screen evenly, never pushed up and cut off at the top (the bottom screen is
        // short), and nothing shifts as the name fades.
        Box(
            Modifier.size(ART)
                .graphicsLayer {
                    val s = (0.55f + 0.45f * p) * (if (bottom) 1f - 0.4f * z else 1f + 3.2f * z * z)
                    scaleX = s; scaleY = s
                    alpha = (1f - z * z).coerceIn(0f, 1f)
                    translationY = if (bottom) z * size.height * 3f else 0f
                }
                .shadow(28.dp, RoundedCornerShape(26.dp), ambientColor = Palette.forName(game.platform.id), spotColor = Palette.forName(game.platform.id))
                .clip(RoundedCornerShape(26.dp)),
        ) { GameArt(game) }
        Column(
            Modifier
                .layout { m, c ->
                    // Reports its own size to the centring Box, then draws a gap below the art's bottom edge.
                    val placed = m.measure(c)
                    layout(placed.width, placed.height) { placed.place(0, (ART / 2 + 18.dp).roundToPx() + placed.height / 2) }
                }
                .graphicsLayer { alpha = (p * (1f - 2f * z)).coerceIn(0f, 1f) },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(game.title, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Pill(if (game.platform.isApps) "Android" else plan.spec.name, Palette.forName(game.platform.id))
                if (bottom) Pill("Bottom screen ↓", Palette.Sky)
            }
        }
    }
}

private val ART = 170.dp

/** Pip explains why a game didn't start, and offers the fix. */
@Composable
private fun ProblemPanel() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val current = LaunchFx.problem
    val shown = rememberLatest(current)
    val close: () -> Unit = { LaunchFx.problem = null; Nav.focusTick++ }
    PanelGuard(current != null)
    OverlayPanel(current != null, onClose = close) {
        val p = shown ?: return@OverlayPanel
        val game = p.game
        val text = describe(p)
        Row(
            Modifier.width(600.dp).clip(RoundedCornerShape(26.dp)).background(Palette.Card).padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Pip(96.dp, mood = 2)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text.first, style = MaterialTheme.typography.titleLarge)
                Text(text.second, style = MaterialTheme.typography.bodyMedium, color = Palette.Muted)
                Spacer(Modifier.height(4.dp))
                val actions = buildList<Pair<String, () -> Unit>> {
                    when (p) {
                        is LaunchProblem.NoEmulator -> {
                            p.suggested?.let { s -> add("Get ${s.name}" to { close(); if (!Emulators.openStore(ctx, s)) Toasts.show("Couldn't open a store for ${s.name}") }) }
                            add("Systems" to { close(); Nav.go(Route.Systems) })
                        }
                        is LaunchProblem.WontOpen -> {
                            if (game.platform.players.size > 1) add("Pick another emulator" to { close(); Nav.go(Route.Systems) })
                            add("Update ${p.spec.name}" to { close(); Emulators.openStore(ctx, p.spec) })
                        }
                        is LaunchProblem.FileMissing -> add("Rescan" to { close(); scope.launch { reloadLibrary(ctx, announce = true) } })
                        is LaunchProblem.AppMissing -> add("Hide it" to {
                            close(); Store.update(game.key) { it.copy(hidden = true) }; Toasts.show("Hidden from Android Games")
                        })
                        is LaunchProblem.BadShortcut -> {}
                    }
                    add("OK" to close)
                }
                // Wraps onto a second line rather than running off the panel with long emulator names.
                @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    actions.forEachIndexed { i, (label, action) ->
                        FocusCard(
                            Modifier.takesFocus("problem:${game.key}", label, i == 0),
                            glow = if (i == 0) Palette.Mint else Palette.Lavender, shape = RoundedCornerShape(14.dp), onClick = action,
                        ) {
                            Text(label, Modifier.padding(horizontal = 14.dp, vertical = 9.dp), style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }
    }
}

/** Title and friendly explanation for a launch problem. */
private fun describe(p: LaunchProblem): Pair<String, String> {
    val game = p.game
    val system = game.platform.name
    return when (p) {
        is LaunchProblem.NoEmulator -> if (p.suggested != null) {
            "${p.suggested.name} isn't installed" to "I play $system games with ${p.suggested.name}. Get it, open it once to set it up, then press A on ${game.title} again."
        } else {
            "No emulator for $system yet" to "Add one in ROMs/tandem/platforms.json, or drop a Cocoon platform file into ROMs/platforms."
        }
        is LaunchProblem.WontOpen ->
            "${p.spec.name} didn't open" to ("It's installed, but wouldn't take ${game.title}. It may need an update, or a different emulator may work better." +
                (p.detail?.let { "\n($it)" } ?: ""))
        is LaunchProblem.FileMissing -> "I can't find this game" to "${game.file.name} was moved or deleted. A rescan will tidy up the list."
        is LaunchProblem.BadShortcut -> "This shortcut needs a game id" to
            "${game.file.name}: ${p.detail}. Vita .dpt files need a line like \"[vita_game_id] PCSA00011\"; Windows .pcgame files hold the GameNative app id."
        is LaunchProblem.AppMissing -> "${game.title} isn't installed anymore" to "Reinstall it from its store, or hide it from Android Games."
    }
}
