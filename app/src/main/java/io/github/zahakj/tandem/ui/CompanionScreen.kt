package io.github.zahakj.tandem.ui

import android.app.Activity
import android.app.ActivityOptions
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.SystemClock
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.draw.drawWithContent
import io.github.zahakj.tandem.Backdrop
import io.github.zahakj.tandem.CompanionActivity
import io.github.zahakj.tandem.Libraries
import io.github.zahakj.tandem.CompanionInfo
import io.github.zahakj.tandem.GameCompanion
import io.github.zahakj.tandem.RunningGames
import io.github.zahakj.tandem.Shell
import io.github.zahakj.tandem.data.Store
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.Date
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * The bottom screen while a game plays on the top one (GameCompanion.kt): the game's art, or Tandem's companion.
 *
 * It must cost next to nothing while the game runs. Nothing here uses an infinite transition: the entrance is one
 * short animation, and all the motion while bright (the art's slow pan and zoom, the logo's sway and shine, Pip's
 * groove and its notes) reads one [SteadyClock] in graphics-layer and draw lambdas (no recomposition), ticking at a
 * steady 30 fps. It is not the shared Ambient clock: that one calms down when nobody touches Tandem, and here the
 * player is busy with the game, so it froze and stuttered (docs/PERFORMANCE.md, "The in-game companion"). The timer
 * recomposes one Text once a second. After a minute without a touch the screen dims and the motion eases into its
 * rest pose over the fade, then stops: only the minute timer and a burn-in drift of a pixel or two every 40 seconds remain (well
 * under 1 fps). A touch wakes it all again (easing back in); while dimmed, the first touch only wakes.
 */

private const val DIM_AFTER_MS = 60_000L

/** How long the dim fades, and the motion eases to rest. */
private const val DIM_FADE_MS = 1_800

/** The longest the entrance waits for Tandem's bottom-screen window to go (it normally goes within a second). */
private const val COVERED_MAX_MS = 3_000L

@Composable
fun CompanionScreen(info: CompanionInfo, activity: CompanionActivity) {
    val resumed = rememberResumed()
    val motion = !Themes.still && !Store.settings.reduceMotion
    var wakeAt by remember { mutableLongStateOf(SystemClock.uptimeMillis()) }
    var dim by remember { mutableStateOf(false) }
    LaunchedEffect(wakeAt, resumed) {
        dim = false
        if (!resumed) return@LaunchedEffect
        delay(DIM_AFTER_MS)
        dim = true
    }
    val wake = { wakeAt = SystemClock.uptimeMillis() }
    // The companion opens underneath Tandem's own bottom-screen window (GameCompanion.tandemBelow): its entrance
    // waits until that window goes, so it plays where it can be seen instead of finishing unseen and popping in.
    var uncovered by remember(info.key) { mutableStateOf(!GameCompanion.tandemBelow) }
    LaunchedEffect(info.key) {
        withTimeoutOrNull(COVERED_MAX_MS) { snapshotFlow { GameCompanion.tandemBelow }.first { !it } }
        uncovered = true
    }
    // A soft fade in for Tandem's companion, again for each new game; the art has its own, staged entrance.
    val enter = remember(info.key) { Animatable(if (motion) 0f else 1f) }
    LaunchedEffect(info.key, uncovered) { if (uncovered) enter.animateTo(1f, tween(1100)) }
    val dimLevel = animateFloatAsState(if (dim) 0.3f else 1f, tween(if (dim) DIM_FADE_MS else 300), label = "companion-dim")
    // The motion clock: steady while bright, whatever the input; it eases to rest with the dim and stops at once in
    // the background or when motion is off (reduce motion, animations Off, battery saver).
    val steady = rememberSteadyClock(on = resumed && !dim && motion, ease = resumed && motion, easeOutMs = DIM_FADE_MS)
    val shift = rememberBurnInShift(dim && resumed)
    val tint = Palette.forName(info.systemId)
    Box(
        Modifier.fillMaxSize()
            .background(Palette.Night)
            .background(Brush.radialGradient(listOf(tint.copy(alpha = 0.22f), Color.Transparent), radius = 900f))
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val wasDim = dim
                    wake()
                    if (wasDim) {
                        // Dimmed: this touch only wakes the screen; it never presses a button by accident.
                        down.consume()
                        do {
                            val e = awaitPointerEvent(PointerEventPass.Initial)
                            e.changes.forEach { it.consume() }
                        } while (e.changes.any { it.pressed })
                    }
                }
            },
    ) {
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                alpha = (if (info.mode == GameCompanion.TANDEM) enter.value else 1f) * dimLevel.value
                translationX = shift.value.x
                translationY = shift.value.y
            },
        ) {
            Crossfade(info.mode == GameCompanion.TANDEM, animationSpec = tween(400), label = "companion-mode") { tandem ->
                if (tandem) CompanionFace(info, activity, dim, resumed, motion, steady, wake)
                else ArtFace(info, activity, dim, resumed, motion, uncovered, steady)
            }
        }
        // A controller offering itself, or a note about the one in use (PadLink.kt); above the dim, so it is seen.
        PadLinkCard(Modifier.align(Alignment.BottomCenter))
    }
}

/** How often the dimmed picture drifts to its next spot, and how long one drift takes. */
private const val BURN_IN_EVERY_MS = 40_000L
private const val BURN_IN_DRIFT_MS = 6_000f

/** The dimmed picture's spots, in pixels: a slow loop around where it belongs, never more than 2 px away. */
private val BurnInPath = listOf(
    Offset(0f, 0f), Offset(1f, 0f), Offset(2f, 1f), Offset(1f, 2f), Offset(0f, 1f),
    Offset(-1f, 0f), Offset(-2f, -1f), Offset(-1f, -2f), Offset(0f, -1f),
)

/**
 * Burn-in care for a dimmed screen ([on]): the whole picture drifts by a pixel or two every [BURN_IN_EVERY_MS], too
 * little and too slowly to notice. Off, it sits still where it belongs. Read it in a graphics layer, in pixels.
 */
@Composable
fun rememberBurnInShift(on: Boolean): State<Offset> {
    val shift = remember { mutableStateOf(Offset.Zero) }
    LaunchedEffect(on) {
        if (!on) {
            shift.value = Offset.Zero
            return@LaunchedEffect
        }
        var step = 0
        while (true) {
            delay(BURN_IN_EVERY_MS)
            step = (step + 1) % BurnInPath.size
            driftTo(shift, BurnInPath[step])
        }
    }
    return shift
}

/** Eases [shift] to [to] over [BURN_IN_DRIFT_MS] at about 10 fps (the main thread sleeps in between). */
private suspend fun driftTo(shift: androidx.compose.runtime.MutableState<Offset>, to: Offset) {
    val from = shift.value
    val start = SystemClock.uptimeMillis()
    while (true) {
        delay(100)
        val p = ((SystemClock.uptimeMillis() - start) / BURN_IN_DRIFT_MS).coerceAtMost(1f)
        val e = FastOutSlowInEasing.transform(p)
        shift.value = Offset(from.x + (to.x - from.x) * e, from.y + (to.y - from.y) * e)
        if (p >= 1f) return
    }
}

/** Now, ticking once a second while bright and every 30 seconds while dimmed; still while the screen is off. */
@Composable
private fun rememberTick(dim: Boolean, resumed: Boolean): State<Long> =
    produceState(System.currentTimeMillis(), dim, resumed) {
        value = System.currentTimeMillis()
        if (!resumed) return@produceState
        while (true) {
            val now = System.currentTimeMillis()
            delay(if (dim) 30_000L else 1_000L - now % 1_000L)
            value = System.currentTimeMillis()
        }
    }

/** "0:12:34" while bright; "12 min" while dimmed, when it only changes once a minute. */
private fun sessionText(ms: Long, seconds: Boolean): String {
    val s = (ms / 1000).coerceAtLeast(0)
    if (!seconds) return formatPlayLong(s).let { if (s < 60) "just started" else it }
    return "%d:%02d:%02d".format(s / 3600, (s / 60) % 60, s % 60)
}

private fun totalSeconds(info: CompanionInfo, now: Long): Long =
    (Store.meta[info.key]?.playSeconds ?: 0L) + ((now - info.start) / 1000).coerceAtLeast(0)

// ---- Game art ----

/*
 * The same title card as Spotlight's (Spotlight.kt), for every game whatever art it has: the best picture for this
 * screen ([ArtPick]: key art, else a title screen or gameplay shot, else the box) covering the whole screen, cropped
 * on its busy part and fading into the system's deepened colour towards the lower left; there the animated logo (or
 * the title) and the pills and play time, always the same lines.
 *
 * The entrance, once the screen is uncovered and the picture is in: the art fades up while it settles from a slight
 * zoom, the logo pops in with its shine, then the pills follow. After that, on the [SteadyClock], the art keeps panning
 * and zooming slowly, the logo sways and bobs gently and its shine passes now and then. The pan and the zoom are waves
 * of different lengths, so they never all turn at once and the picture never seems to stop; while dimmed the clock
 * stands still, so a dimmed screen costs nothing.
 */

@Composable
private fun ArtFace(
    info: CompanionInfo, activity: CompanionActivity, dim: Boolean, resumed: Boolean, motion: Boolean, uncovered: Boolean,
    steady: SteadyClock,
) {
    val seed = info.key.hashCode() and 0x7fffffff
    val turn = (seed % 1000) / 1000f
    val dirX = if (seed % 2 == 0) 1f else -1f
    val dirY = if ((seed / 2) % 2 == 0) 1f else -1f
    val game = remember(info.key) { Libraries.byKey(info.key) }
    // The game's pictures from the library; the ones in the intent if the library isn't loaded in this process yet.
    val cands = remember(info.key) {
        game?.let { g -> Libraries.of(g)?.backdrops(g) }?.takeIf { it.isNotEmpty() }
            ?: listOfNotNull(info.hero?.let { Backdrop(File(it), Backdrop.Kind.Key) }, info.box?.let { Backdrop(File(it), Backdrop.Kind.Box) })
    }
    val tint = Palette.forName(info.systemId)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wPx = constraints.maxWidth
        val hPx = constraints.maxHeight
        val wide = maxWidth > maxHeight * 1.3f
        val logoW = minOf(maxWidth * 0.7f, 380.dp)
        val choice = rememberArtChoice(cands, wPx, hPx)
        var artReady by remember(info.key) { mutableStateOf(cands.isEmpty()) }
        var logoIn by remember(info.key) { mutableStateOf(!motion) }
        val intro = remember(info.key) { Animatable(if (motion) 0f else 1f) }
        LaunchedEffect(info.key, uncovered) {
            if (!uncovered) return@LaunchedEffect
            // The pictures are local files and load at once; a slow one doesn't hold the entrance for long.
            withTimeoutOrNull(700) { snapshotFlow { artReady }.first { it } }
            launch { delay(380); logoIn = true }
            intro.animateTo(1f, tween(1700, easing = LinearEasing))
        }
        // What the art fades into: the system's colour, deepened.
        Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.lerp(tint, Palette.Night, 0.84f)))
        if (choice != null) {
            AsyncImage(
                rememberSizedRequest(choice.file, maxOf(wPx, hPx)), null,
                Modifier.fillMaxSize().companionFade(wide).graphicsLayer {
                    alpha = stage(intro.value, 0f, 0.45f)
                    // Settles from a slight zoom as it fades in, then the slow pan and zoom. The pan never goes further
                    // than the zoom leaves room for, so no edge ever shows.
                    val s = (1.08f + 0.04f * steady.wave(61_000, turn)) * (1f + 0.06f * (1f - stage(intro.value, 0f, 0.75f)))
                    scaleX = s; scaleY = s
                    val room = (s - 1f) / 2f * 0.8f
                    translationX = room * size.width * steady.wave(50_000, turn + 0.3f) * dirX
                    translationY = room * size.height * steady.wave(37_000, turn + 0.6f) * dirY * 0.5f
                },
                alignment = choice.align, contentScale = ContentScale.Crop,
                onSuccess = { artReady = true }, onError = { artReady = true },
            )
        } else if (cands.isNotEmpty()) {
            LaunchedEffect(Unit) { delay(500); artReady = true }
        }
        Column(
            Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(Modifier.heightIn(min = 60.dp), contentAlignment = Alignment.BottomStart) {
                if (logoIn && game != null) {
                    AnimatedLogo(game, maxWidth = logoW, maxHeight = 110.dp, entranceDelayMs = 0, steady = steady)
                } else if (logoIn) {
                    Text(
                        info.title, style = MaterialTheme.typography.displaySmall.onArt(), color = Color.White,
                        maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.logoLife(info.key, 0, steady = steady),
                    )
                }
            }
            Column(
                Modifier.padding(top = 6.dp).graphicsLayer {
                    val s = stage(intro.value, 0.5f, 1f)
                    alpha = s
                    translationY = (1f - s) * 10f * density
                },
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val now by rememberTick(dim, resumed)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (info.system.isNotEmpty()) Pill(info.system, androidx.compose.ui.graphics.lerp(tint, Color.White, 0.35f), Modifier.darkChip())
                    Pill("Playing " + sessionText(now - info.start + info.earlier, seconds = !dim), Palette.Mint, Modifier.darkChip())
                }
                // Always a line here, so every game's screen has the same shape.
                val total = totalSeconds(info, now)
                Text(
                    if (total >= 60) "${formatPlayLong(total)} in total" else "First time playing",
                    style = MaterialTheme.typography.labelLarge.onArt(), color = Color.White.copy(alpha = 0.78f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        // Two small chips, hidden while dimmed: back to Tandem, and Tandem's companion instead of the art.
        if (!dim) {
            Row(
                Modifier.fillMaxWidth().padding(14.dp).graphicsLayer { alpha = stage(intro.value, 0.55f, 1f) },
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                SmallChip("Back to Tandem", Palette.Mint) { GameCompanion.backToTandem(activity) }
                SmallChip("Companion", Palette.Lavender) { activity.switchMode() }
            }
        }
    }
}

/** The art fading into the deepened colour behind it towards the lower left (wide screens) or the bottom. */
private fun Modifier.companionFade(wide: Boolean): Modifier =
    graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }.drawWithContent {
        drawContent()
        val brush = if (wide) {
            Brush.radialGradient(
                0f to Color.Black.copy(alpha = 0.3f), 0.45f to Color.Black.copy(alpha = 0.55f), 0.8f to Color.Black.copy(alpha = 0.92f), 1f to Color.Black,
                center = Offset(0f, size.height), radius = maxOf(size.width * 0.82f, size.height * 1.2f),
            )
        } else {
            Brush.verticalGradient(0f to Color.Black, 0.4f to Color.Black, 0.78f to Color.Black.copy(alpha = 0.5f), 1f to Color.Black.copy(alpha = 0.32f))
        }
        drawRect(brush, blendMode = androidx.compose.ui.graphics.BlendMode.DstIn)
    }

/** [t]'s progress through the part of the entrance from [from] to [to], eased. */
private fun stage(t: Float, from: Float, to: Float): Float = FastOutSlowInEasing.transform(((t - from) / (to - from)).coerceIn(0f, 1f))

@Composable
private fun SmallChip(text: String, color: Color, onTap: () -> Unit) {
    Box(Modifier.darkChip().clickable(onClick = onTap).padding(horizontal = 14.dp, vertical = 8.dp)) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = color, maxLines = 1)
    }
}

// ---- Tandem companion ----

/** Session milestones Pip cheers for, in minutes, with what it says. */
private fun milestoneLine(minutes: Long): String? = when {
    minutes >= 240 -> "${minutes / 60} hours! A legendary run"
    minutes >= 120 -> "${minutes / 60} hours in. Water and a stretch?"
    minutes >= 60 -> "An hour already! Having fun?"
    minutes >= 30 -> "Half an hour in. Nice rhythm!"
    else -> null
}

private fun milestoneOf(minutes: Long): Int = when {
    minutes >= 60 -> (minutes / 60).toInt() + 1
    minutes >= 30 -> 1
    else -> 0
}

@Composable
private fun CompanionFace(
    info: CompanionInfo, activity: CompanionActivity, dim: Boolean, resumed: Boolean, motion: Boolean, steady: SteadyClock,
    wake: () -> Unit,
) {
    val ctx = LocalContext.current
    val now by rememberTick(dim, resumed)
    val minutes = (now - info.start + info.earlier) / 60_000
    // Pip grooves gently all the time while the screen is bright (on the steady clock), and cheers (with a hop and
    // sparkles) at each milestone, waking the screen for it.
    val cheer = remember { Animatable(0f) }
    val milestone = milestoneOf(minutes)
    val firstMilestone = remember(info.key) { milestone }
    LaunchedEffect(milestone) {
        if (milestone == firstMilestone || milestone == 0) return@LaunchedEffect
        wake()
        if (!motion) return@LaunchedEffect
        cheer.snapTo(0f); cheer.animateTo(1f, tween(2600, easing = LinearEasing)); cheer.snapTo(0f)
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // Clock and battery.
        val battery by produceState(-1 to false, resumed) {
            if (!resumed) return@produceState
            while (true) {
                value = withContext(Dispatchers.IO) { runCatching { SystemControls.battery(ctx) }.getOrDefault(-1 to false) }
                delay(60_000)
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(Clock.always(Date(now)), style = MaterialTheme.typography.displaySmall.copy(fontSize = 40.sp, lineHeight = 44.sp), maxLines = 1)
                Text(
                    android.text.format.DateFormat.format("EEEE, d MMMM", Date(now)).toString(),
                    style = MaterialTheme.typography.labelLarge, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            val (level, charging) = battery
            if (level >= 0) Pill((if (charging) "Charging " else "") + "$level%", if (level <= 20 && !charging) Palette.Peach else Palette.Mint)
        }
        // Pip, playing along, and the game.
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            PipPlaying(112.dp, steady, cheer = { cheer.value })
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(info.title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    listOf(info.system, "Playing " + sessionText(now - info.start + info.earlier, seconds = !dim)).filter { it.isNotEmpty() }.joinToString(" · "),
                    style = MaterialTheme.typography.titleSmall, color = Palette.Mint, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                val total = totalSeconds(info, now)
                if (total >= 60) Text("${formatPlayLong(total)} in total", style = MaterialTheme.typography.labelLarge, color = Palette.Muted, maxLines = 1)
                milestoneLine(minutes)?.let {
                    Text(it, style = MaterialTheme.typography.labelLarge, color = Palette.Butter, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Spacer(Modifier.weight(1f))
        QuickBar()
        Toggles()
        LeaveRow(info)
    }
}

/** Back to Tandem, the game's art, or close the game (with Shizuku; tap twice). */
@Composable
private fun LeaveRow(info: CompanionInfo) {
    val activity = LocalContext.current as CompanionActivity
    val scope = rememberCoroutineScope()
    run {
        val canClose by produceState(false, info.pkg) {
            value = info.pkg != null && withContext(Dispatchers.IO) { Shell.ready() }
        }
        var confirmClose by remember { mutableStateOf(false) }
        LaunchedEffect(confirmClose) { if (confirmClose) { delay(3500); confirmClose = false } }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BigButton("Back to Tandem", Palette.Mint) { GameCompanion.backToTandem(activity) }
            BigButton("Game art", Palette.Lavender) { activity.switchMode() }
            if (canClose) {
                BigButton(if (confirmClose) "Tap to close" else "Close game", Palette.Peach) {
                    if (!confirmClose) { confirmClose = true; return@BigButton }
                    val pkg = info.pkg ?: return@BigButton
                    scope.launch {
                        withContext(Dispatchers.IO) { RunningGames.close(activity, pkg) }
                        GameCompanion.backToTandem(activity)
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.BigButton(text: String, color: Color, onTap: () -> Unit) {
    Box(
        Modifier.weight(1f).height(52.dp).clip(RoundedCornerShape(18.dp)).background(color.copy(alpha = 0.2f)).clickable(onClick = onTap),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = MaterialTheme.typography.titleSmall, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 8.dp)) }
}

/** The dock's apps (the quick bar). A tap opens the app here, on the bottom screen, next to the game. */
@Composable
private fun QuickBar() {
    val activity = LocalContext.current as Activity
    val apps by produceState(emptyList<Pair<String, String>>()) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                Dock.pinned(activity).filterNot { it.startsWith("tandem:") }.map { it to RunningGames.appLabel(activity, it) }
            }.getOrDefault(emptyList())
        }
    }
    if (apps.isEmpty()) return
    val scroll = rememberScrollState()
    Row(
        Modifier.fillMaxWidth().fadingEdges(scroll, horizontal = true).horizontalScroll(scroll),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for ((pkg, label) in apps) {
            Column(
                Modifier.width(78.dp).clip(RoundedCornerShape(16.dp)).background(Palette.Card).clickable { openHere(activity, pkg) }.padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AppIcon(pkg, Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)))
                Spacer(Modifier.height(4.dp))
                Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 6.dp))
            }
        }
    }
}

/** Opens [pkg] on the display [activity] is on (the bottom screen). */
private fun openHere(activity: Activity, pkg: String) {
    val intent = Dock.launchIntent(activity, pkg)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED) ?: return
    val display = activity.display?.displayId ?: return
    runCatching { activity.startActivity(intent, ActivityOptions.makeBasic().setLaunchDisplayId(display).toBundle()) }
}

/** Brightness, volume and Wi-Fi, without leaving the game. */
@Composable
private fun Toggles() {
    val activity = LocalContext.current as Activity
    val scope = rememberCoroutineScope()
    var tick by remember { mutableLongStateOf(0L) }
    val state by produceState(Triple(-1, -1, false), tick) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                Triple(
                    if (SystemControls.canSetBrightness(activity)) SystemControls.brightnessLevel(activity) else -1,
                    SystemControls.volume(activity), SystemControls.wifiOn(activity),
                )
            }.getOrDefault(Triple(-1, -1, false))
        }
    }
    fun io(work: () -> Unit) = scope.launch { withContext(Dispatchers.IO) { runCatching(work) }; tick++ }
    val (bright, volume, wifi) = state
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Stepper("Light", if (bright >= 0) "${bright * 100 / SystemControls.BRIGHT_STEPS}%" else "–", Palette.Butter, enabled = bright >= 0) { by ->
            io { SystemControls.setBrightnessLevel(activity, bright + by * 2) }
        }
        Stepper("Volume", if (volume >= 0) "$volume" else "–", Palette.Sky, enabled = volume >= 0) { by ->
            io { SystemControls.setVolume(activity, volume + by) }
        }
        Box(
            Modifier.weight(0.8f).height(52.dp).clip(RoundedCornerShape(16.dp))
                .background(if (wifi) Palette.Mint.copy(alpha = 0.25f) else Palette.Card)
                .clickable {
                    io {
                        if (!SystemControls.setWifi(!wifi)) {
                            val i = Intent(android.provider.Settings.Panel.ACTION_WIFI).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            val d = activity.display?.displayId
                            activity.runOnUiThread {
                                runCatching { activity.startActivity(i, d?.let { ActivityOptions.makeBasic().setLaunchDisplayId(it).toBundle() }) }
                            }
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) { Text(if (wifi) "Wi-Fi on" else "Wi-Fi off", style = MaterialTheme.typography.labelLarge, color = if (wifi) Palette.Mint else Palette.Muted, maxLines = 1) }
    }
}

@Composable
private fun RowScope.Stepper(label: String, value: String, color: Color, enabled: Boolean, onStep: (Int) -> Unit) {
    Row(
        Modifier.weight(1f).height(52.dp).clip(RoundedCornerShape(16.dp)).background(Palette.Card)
            .graphicsLayer { alpha = if (enabled) 1f else 0.35f },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepTap("−", color, enabled) { onStep(-1) }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = Palette.Muted, maxLines = 1)
            Text(value, style = MaterialTheme.typography.labelLarge, color = color, maxLines = 1)
        }
        StepTap("+", color, enabled) { onStep(1) }
    }
}

@Composable
private fun StepTap(symbol: String, color: Color, enabled: Boolean, onTap: () -> Unit) {
    Box(Modifier.size(40.dp, 52.dp).clickable(enabled = enabled, onClick = onTap), contentAlignment = Alignment.Center) {
        Text(symbol, style = MaterialTheme.typography.titleLarge, color = color)
    }
}

// ---- Pip, playing ----

/**
 * Pip's "playing" emote: eyes happily shut, a big smile, a tiny controller in its hands and music notes around it.
 * On [steady] it grooves gently all the time: a soft bob to the beat, a sway, the controller tilting, the notes floating
 * up and fading; its energy scales all of it, so it eases into the resting picture (notes beside Pip) when the clock
 * stops. [cheer] (0..1) makes it hop three times with sparkles, for session milestones. Both are read only while
 * drawing, and at rest it is a still picture: nothing redraws.
 */
@Composable
fun PipPlaying(size: Dp, steady: SteadyClock, cheer: () -> Float, modifier: Modifier = Modifier) {
    Canvas(
        modifier.size(size).graphicsLayer {
            val e = steady.energy; val c = cheer()
            val bob = (0.5f + 0.5f * steady.wave(1_400)) * e
            translationY = -abs(sin(c * PI.toFloat() * 3f)) * this.size.height * 0.14f - bob * this.size.height * 0.025f
            rotationZ = steady.wave(2_800, 0.25f) * 3f * e
        },
    ) { drawPipPlaying(steady, cheer()) }
}

private fun DrawScope.drawPipPlaying(steady: SteadyClock, c: Float) {
    val e = steady.energy
    val w = size.width
    val h = size.height
    val dark = Themes.live.dark
    val screen = if (dark) Palette.NightDeep else Palette.Text
    val screen2 = if (dark) Palette.Night else Palette.Text.copy(alpha = 0.85f)
    val face = if (dark) Palette.Text else Palette.Night
    val r = CornerRadius(w * 0.14f)
    val top = Size(w * 0.76f, h * 0.42f)
    val bottom = Size(w * 0.66f, h * 0.34f)
    val topAt = Offset((w - top.width) / 2, h * 0.08f)
    val bottomAt = Offset((w - bottom.width) / 2, h * 0.56f)
    // Body, as the everyday Pip.
    drawRoundRect(Palette.Lavender.copy(alpha = 0.7f), Offset(w * 0.32f, h * 0.48f), Size(w * 0.36f, h * 0.1f), CornerRadius(w * 0.05f))
    drawRoundRect(Palette.Mint, topAt, top, r)
    drawRoundRect(Palette.Sky, bottomAt, bottom, r)
    val inset = w * 0.065f
    drawRoundRect(screen, topAt + Offset(inset, inset), Size(top.width - 2 * inset, top.height - 2 * inset), CornerRadius(w * 0.08f))
    drawRoundRect(screen2, bottomAt + Offset(inset, inset * 0.8f), Size(bottom.width - 2 * inset, bottom.height - 1.6f * inset), CornerRadius(w * 0.07f))
    // Face: happy shut eyes (in the zone) and an open smile.
    val cx = w / 2
    val eyeY = topAt.y + top.height * 0.46f
    val eyeDx = top.width * 0.2f
    val eyeR = w * 0.062f
    for (side in listOf(-1, 1)) {
        drawArc(
            face, 200f, 140f, false, topLeft = Offset(cx + side * eyeDx - eyeR, eyeY - eyeR * 0.4f), size = Size(eyeR * 2, eyeR * 1.6f),
            style = Stroke(width = w * 0.034f, cap = StrokeCap.Round),
        )
        drawCircle(Palette.Peach.copy(alpha = 0.8f), w * 0.038f, Offset(cx + side * eyeDx * 1.5f, eyeY + w * 0.065f))
    }
    drawArc(face, 0f, 180f, true, topLeft = Offset(cx - w * 0.075f, eyeY + w * 0.015f), size = Size(w * 0.15f, w * 0.12f))
    drawArc(Palette.Peach, 30f, 120f, true, topLeft = Offset(cx - w * 0.05f, eyeY + w * 0.06f), size = Size(w * 0.1f, w * 0.07f))
    // The tiny controller, held in front of the lower half; it tilts to the beat.
    val pad = Size(w * 0.62f, h * 0.17f)
    val padAt = Offset((w - pad.width) / 2, h * 0.7f)
    val padC = padAt + Offset(pad.width / 2, pad.height / 2)
    rotate(steady.wave(1_400, 0.5f) * 5f * e, padC) {
        val grip = pad.height * 0.62f
        drawCircle(Palette.Lavender, grip, Offset(padAt.x + grip * 0.7f, padC.y + pad.height * 0.12f))
        drawCircle(Palette.Lavender, grip, Offset(padAt.x + pad.width - grip * 0.7f, padC.y + pad.height * 0.12f))
        drawRoundRect(Palette.Lavender, padAt, pad, CornerRadius(pad.height * 0.5f))
        // D-pad, two buttons.
        val arm = pad.height * 0.16f
        val dp = Offset(padAt.x + pad.width * 0.27f, padC.y)
        drawRoundRect(screen, dp - Offset(arm * 1.5f, arm / 2), Size(arm * 3, arm), CornerRadius(arm / 3))
        drawRoundRect(screen, dp - Offset(arm / 2, arm * 1.5f), Size(arm, arm * 3), CornerRadius(arm / 3))
        drawCircle(Palette.Peach, arm * 0.75f, Offset(padAt.x + pad.width * 0.7f, padC.y + arm * 0.6f))
        drawCircle(Palette.Butter, arm * 0.75f, Offset(padAt.x + pad.width * 0.8f, padC.y - arm * 0.6f))
        // Little hands on the grips.
        drawCircle(Palette.Mint, grip * 0.5f, Offset(padAt.x + grip * 0.4f, padC.y + pad.height * 0.05f))
        drawCircle(Palette.Mint, grip * 0.5f, Offset(padAt.x + pad.width - grip * 0.4f, padC.y + pad.height * 0.05f))
    }
    // Music notes: floating up and fading in and out in turn; at rest they sit beside Pip.
    val notes = listOf(
        Triple(Offset(w * 0.9f, h * 0.3f), Palette.Butter, 0f),
        Triple(Offset(w * 0.08f, h * 0.22f), Palette.Peach, 0.33f),
        Triple(Offset(w * 0.94f, h * 0.62f), Palette.Sky, 0.66f),
    )
    fun lift(p: Float) = Offset(sin(p * PI.toFloat() * 2f) * w * 0.02f, -p * h * 0.22f + h * 0.06f)
    val rest = lift(0.3f)
    for ((at, color, lag) in notes) {
        val p = steady.loop(3_600, lag)
        val alpha = 0.9f + (sin(p * PI.toFloat()) * 0.95f - 0.9f) * e
        drawNote(at + rest + (lift(p) - rest) * e, w * 0.05f, color.copy(alpha = alpha.coerceIn(0f, 1f)))
    }
    // Milestone sparkles.
    if (c > 0f) {
        val a = sin(c * PI.toFloat()).coerceIn(0f, 1f)
        for ((i, at) in listOf(Offset(w * 0.12f, h * 0.06f), Offset(w * 0.88f, h * 0.08f), Offset(w * 0.04f, h * 0.55f), Offset(w * 0.5f, h * 0.0f)).withIndex()) {
            val s = w * (0.035f + 0.02f * sin((c * 6f + i) * PI.toFloat()).coerceAtLeast(0f))
            drawSparkle(at, s, Palette.Butter.copy(alpha = a))
        }
    }
}

private fun DrawScope.drawNote(at: Offset, r: Float, color: Color) {
    drawOval(color, at - Offset(r, r * 0.75f), Size(r * 2, r * 1.5f))
    val stemX = at.x + r * 0.9f
    drawLine(color, Offset(stemX, at.y), Offset(stemX, at.y - r * 3.2f), strokeWidth = r * 0.38f, cap = StrokeCap.Round)
    drawLine(color, Offset(stemX, at.y - r * 3.2f), Offset(stemX + r * 1.3f, at.y - r * 2.2f), strokeWidth = r * 0.38f, cap = StrokeCap.Round)
}

private fun DrawScope.drawSparkle(at: Offset, s: Float, color: Color) {
    drawLine(color, at - Offset(s, 0f), at + Offset(s, 0f), strokeWidth = s * 0.35f, cap = StrokeCap.Round)
    drawLine(color, at - Offset(0f, s), at + Offset(0f, s), strokeWidth = s * 0.35f, cap = StrokeCap.Round)
}
