package io.github.zahakj.tandem.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.zIndex
import io.github.zahakj.tandem.Collections
import io.github.zahakj.tandem.Game
import io.github.zahakj.tandem.Launcher
import io.github.zahakj.tandem.PlatformLibrary
import io.github.zahakj.tandem.RunningGames
import io.github.zahakj.tandem.Selected
import io.github.zahakj.tandem.Selection
import io.github.zahakj.tandem.data.GameStatus
import io.github.zahakj.tandem.data.HomeStore
import io.github.zahakj.tandem.data.Store
import io.github.zahakj.tandem.data.playStatus
import io.github.zahakj.tandem.plural
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

/*
 * Orbit, Tandem's own home layout ("orbit"). The lower part of the top screen is the orbit: the home's systems,
 * collections and Tandem's apps as round orbs along an arc that bows downward, like planets on an orbit seen from a
 * little above. The highlighted orb sits at the front, lowest and biggest; Left and Right spin the orbit. Above it,
 * the spotlight fans out that orb's latest games like cards in a hand (Up goes into the fan), and a Continue ribbon
 * over the fan holds the last game played anywhere. Down reaches a small dock of round app icons.
 *
 * The geometry lives in OrbitLayout.kt. The spin is one Animatable (the orbit's offset, in orbs) read only in
 * graphics layers and draw lambdas, so a spin recomposes nothing; the fan's entry is one more. Only the settled
 * orb's fan loads covers, at their drawn size.
 */

object Orbit {
    /** Orbit is the home layout. */
    val enabled: Boolean get() = Store.settings.look.homeLayout == "orbit"

    /** The focus route remembering which orb is at the front (the home route remembers which part has the highlight). */
    const val ROUTE = "home:orbit"
    /** Focus keys on the home route: the orbit itself, the Continue ribbon, the empty library's note. */
    const val STAGE = "orbit"
    const val CONTINUE = "orbit:continue"
    const val EMPTY = "orbit:empty"

    val Groups = listOf("all" to "Systems, collections, apps", "games" to "Systems and collections", "systems" to "Systems only")

    const val SYSTEMS = 0
    const val COLLECTIONS = 1
    const val APPS = 2
}

/** One orb: an icon grid tile ([GridCatalog] builds them, designs and pack art included), its group and one line about it. */
private class Orb(val item: GridItem, val group: Int, val detail: String, val open: () -> Unit)

/** A card in the spotlight's fan: a game, or an app (the All apps orb fans out the dock). */
private class FanCard(val key: String, val game: Game? = null, val pkg: String? = null)

/** Collections whose own order is the point (most played, newest first...): their fan keeps it. */
private val KeepOrder = setOf("most", "added", "continue", Collections.PLAYING, Collections.BACKLOG, Collections.BEATEN)

private fun buildOrbs(catalog: GridCatalog, groups: String): List<Orb> {
    val keys = catalog.defaults + "allapps"
    return keys.mapNotNull { key ->
        val group = when {
            key.startsWith("s:") -> Orbit.SYSTEMS
            key.startsWith("col:") || key == "genres" -> Orbit.COLLECTIONS
            else -> Orbit.APPS
        }
        val wanted = when (groups) {
            "systems" -> group == Orbit.SYSTEMS
            "games" -> group != Orbit.APPS
            else -> true
        }
        if (!wanted) return@mapNotNull null
        val item = catalog.item(key) ?: return@mapNotNull null
        val detail = when (item.kind) {
            TileKind.System -> {
                val playing = item.games.count { it.meta.playStatus == GameStatus.Playing }
                listOfNotNull(plural(item.games.size, "game"), if (playing > 0) "$playing playing" else item.blurb.ifEmpty { null }).joinToString(" · ")
            }
            TileKind.Collection, TileKind.Genres -> item.detail
            else -> item.blurb.ifEmpty { item.type }
        }
        // A system opens as a page or a popup, as the other layouts open it (the icon grid's folder view lives in the grid).
        val open: () -> Unit = item.platformId?.takeIf { item.kind == TileKind.System }?.let { id -> { openFolder(Route.System(id)) } } ?: item.open
        Orb(item, group, detail, open)
    }
}

/**
 * The fan for [orb]: its most recently played games (the latest in the middle), then the most played, then
 * favourites and the rest, three or five of them. Collections whose order means something keep it.
 */
private fun fanGames(orb: Orb): List<Game> {
    val games = orb.item.games
    if (games.isEmpty()) return emptyList()
    val picked = if (orb.item.collectionId in KeepOrder) games.take(5) else {
        val out = ArrayList<Game>(5)
        val seen = HashSet<String>()
        fun add(list: Sequence<Game>) { for (g in list) { if (out.size >= 5) return; if (seen.add(g.key)) out += g } }
        add(games.asSequence().filter { it.meta.lastPlayed > 0 }.sortedByDescending { it.meta.lastPlayed })
        add(games.asSequence().filter { it.meta.playSeconds > 0 }.sortedByDescending { it.meta.playSeconds })
        add(games.asSequence().filter { it.meta.favorite })
        add(games.asSequence())
        out
    }
    return picked.take(Fan.count(picked.size))
}

/** The orbit's live state: the spin, which orb is at the front, and whether the orbit holds the highlight. */
private class OrbitState(initial: Float, private val scope: CoroutineScope) {
    val offset: Animatable<Float, AnimationVector1D> = Animatable(initial)
    /** Where the orbit is heading (the offset once the spin settles). */
    var goal by mutableFloatStateOf(initial)
    /** The orb the spotlight shows: the front one once the highlight rests there (see [Selection.follow]). */
    var spot by mutableStateOf<String?>(null)
    var focused by mutableStateOf(false)
    // Set on every composition, read by the key, touch and button handlers.
    var orbs: List<Orb> = emptyList()
    var side = 5
    var layout: OrbitLayout? = null

    val count: Int get() = orbs.size
    val wraps: Boolean get() = OrbitLayout.wraps(count, side)
    val frontIndex: Int get() = OrbitLayout.index(goal, count, wraps)
    val front: Orb? get() = orbs.getOrNull(frontIndex)
    private val still: Boolean get() = Motion.style == AnimationStyle.Off

    /** Spins the orbit so it settles at [to]; [velocity] (orbs a second) carries a finger's flick into the spring. */
    fun goTo(to: Float, velocity: Float = 0f) {
        val before = frontIndex
        goal = to
        val o = front ?: return
        if (frontIndex != before) Sounds.move()
        FocusMemory.remember(Orbit.ROUTE, o.item.key)
        // While the D-pad is held the spotlight and the other screen wait for the orbit to rest, so skimming past
        // orbs never loads their covers.
        Selection.follow {
            spot = o.item.key
            if (focused) Selection.current = o.item.selected
        }
        scope.launch {
            if (still) offset.snapTo(to) else offset.animateTo(to, Motion.move(), initialVelocity = velocity)
            // A long spin one way leaves the numbers far from zero: bring them back by whole turns, which look the same.
            val n = count
            if (wraps && n > 0 && abs(goal) > n * 8f) {
                val turns = floor(goal / n) * n
                goal -= turns
                offset.snapTo(offset.value - turns)
            }
        }
    }

    fun step(by: Int) = goTo(OrbitLayout.stepped(goal, by, count, wraps))

    /** Brings orb [i] to the front. */
    fun show(i: Int) { if (i in 0 until count) goTo(OrbitLayout.target(goal, i, count, wraps)) }

    fun dragBy(by: Float) { scope.launch { offset.snapTo(offset.value + by) } }

    fun stop() { scope.launch { offset.stop() } }

    /** A finger let go: settle on the nearest orb, carried on by a quick flick (not with Reduce motion). */
    fun fling(velocity: Float) {
        goTo(OrbitLayout.settle(offset.value, velocity, count, wraps, still), if (still) 0f else velocity)
    }

    fun open() { front?.open?.invoke() }
}

/** The Orbit home. Composed by [HomeLayout]. */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun OrbitHome(libs: List<PlatformLibrary>) {
    IconPacksDriver()
    val ctx = LocalContext.current
    val look = Store.settings.look
    val route = Route.Home.key
    val catalog = rememberCatalog(libs)
    val orbs = remember(catalog, look.orbitGroups) { buildOrbs(catalog, look.orbitGroups) }
    val orbKeys = remember(orbs) { orbs.map { it.item.key } }

    // Package changes, not every return: each check asks the package manager about every pinned app.
    val packages = io.github.zahakj.tandem.Emulators.changes
    val showDock = HomeStore.settings.showDock
    val pinned = remember(HomeStore.settings.dock, packages) { Dock.pinned(ctx) }
    val dock = if (showDock) pinned.take(look.dockCount.coerceIn(1, 8)) else emptyList()
    PrefetchAppLooks(dock)
    // All apps is an orb; without the apps group it moves into the dock.
    val dockAll = showDock && "allapps" !in orbKeys
    val dockKeys = dock.map { "app:$it" } + if (dockAll) listOf("app:*") else emptyList()

    val scope = rememberCoroutineScope()
    val orbTarget = rememberFocusTarget(Orbit.ROUTE, orbKeys)
    val st = remember {
        OrbitState(orbKeys.indexOf(orbTarget).coerceAtLeast(0).toFloat(), scope).also { it.orbs = orbs; it.spot = it.front?.item?.key }
    }
    st.orbs = orbs
    // The orbs changed (a rescan, a hidden system) or focus was handed back somewhere else: put the remembered orb, or
    // its nearest neighbour, at the front without a spin.
    LaunchedEffect(orbTarget, orbKeys, st.side) {
        val i = orbKeys.indexOf(orbTarget)
        if (i < 0) return@LaunchedEffect
        val want = if (st.wraps) OrbitLayout.target(st.goal, i, st.count, true) else i.toFloat()
        if (abs(want - st.goal) > 0.001f || abs(st.offset.value - want) > 0.001f && !st.offset.isRunning) {
            st.goal = want
            st.offset.snapTo(want)
        }
        st.spot = orbTarget
    }

    val spotOrb = orbs.firstOrNull { it.item.key == st.spot } ?: st.front
    val noGames = Collections.visibleGames().isEmpty()
    val cards = remember(spotOrb, pinned, noGames) {
        when {
            spotOrb == null || noGames -> emptyList()
            spotOrb.item.kind == TileKind.AllApps -> pinned.take(5).let { it.take(Fan.count(it.size)) }.map { FanCard("app:$it", pkg = it) }
            else -> fanGames(spotOrb).map { FanCard(it.key, game = it) }
        }
    }
    val last = if (look.orbitContinue) Collections.continuePlaying().firstOrNull() else null

    val keys = buildList {
        if (orbs.isNotEmpty()) add(Orbit.STAGE)
        if (last != null) add(Orbit.CONTINUE)
        if (noGames) add(Orbit.EMPTY)
        cards.forEach { add("fan:${it.key}") }
        addAll(dockKeys)
    }
    // Coming from another layout, the home's remembered item (a grid tile, a shelf card) means nothing here: start
    // on the orbit rather than on whatever neighbour of it happens to be in this list (a dock app).
    remember(orbs.isNotEmpty()) {
        if (orbs.isNotEmpty() && FocusMemory.get(route).let { it == null || it !in keys }) FocusMemory.remember(route, Orbit.STAGE)
        0
    }
    val target = rememberFocusTarget(route, keys)
    val stageReq = remember { FocusRequester() }
    val fanReq = remember { FocusRequester() }
    val ribbonReq = remember { FocusRequester() }
    val emptyReq = remember { FocusRequester() }
    val dockReqs = remember { HashMap<String, FocusRequester>() }
    var lastDock by remember { mutableStateOf<String?>(null) }
    var fanFocus by remember { mutableStateOf<String?>(null) }

    fun focusStage() {
        if (orbs.isEmpty()) return
        FocusMemory.remember(route, Orbit.STAGE)
        if (runCatching { stageReq.requestFocus() }.isFailure) Nav.focusTick++
    }

    // L1 / R1: the first orb of the group before or after (systems, collections, apps), from anywhere on the home.
    InterceptButtons(true) { b ->
        val free = Nav.route == Route.Home && !Nav.overlayOpen && Layers.top == null && FolderPopup.route == null
        if (!free || (b != io.github.zahakj.tandem.Button.L1 && b != io.github.zahakj.tandem.Button.R1) || st.count == 0) return@InterceptButtons false
        val to = OrbitLayout.groupJump(st.orbs.map { it.group }, st.frontIndex, if (b == io.github.zahakj.tandem.Button.R1) 1 else -1)
        if (to != st.frontIndex) st.show(to)
        if (!st.focused) focusStage()
        true
    }
    // B: from the fan, the ribbon or the dock, back to the orbit; on the orbit, back round to the first orb (Home
    // never leaves Tandem, as on the other layouts).
    BackHandler(enabled = Nav.route == Route.Home && !Nav.overlayOpen && FolderPopup.route == null && orbs.isNotEmpty()) {
        if (!st.focused) focusStage() else st.show(0)
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val w = maxWidth
        val h = maxHeight
        // The bottom screen (screens swapped) is narrow: fewer orbs either side, so they keep their room.
        val side = if (w < 600.dp) 3 else 5
        st.side = side
        val orbD = min(84.dp, h * 0.2f)
        val dockH = if (dockKeys.isNotEmpty()) 62.dp else 10.dp
        val captionH = 66.dp
        val baseY = h - dockH - captionH - orbD / 2 - 2.dp
        val rise = h * 0.3f
        val stageTop = (baseY - rise - orbD * 0.6f).coerceAtLeast(0.dp)
        val stageBottom = baseY + orbD * 0.6f
        val ribbonH = 46.dp
        val fanTop = if (last != null) 8.dp + ribbonH + 10.dp else 12.dp
        val fanBottom = baseY - orbD / 2 - 12.dp
        // The middle card is 1.16 times the rest and the outer ones drop a little (Fan.place): fit them all.
        val cardH = ((fanBottom - fanTop) / 1.3f).coerceIn(60.dp, 170.dp)
        val cardW = cardH * 0.72f
        val layout = with(density) {
            remember(w, h, side, orbD) { OrbitLayout(w.toPx(), (baseY - stageTop).toPx(), rise.toPx(), orbD.toPx(), side) }
        }
        st.layout = layout
        val ring = animateFloatAsState(if (st.focused) 1f else 0f, Motion.soft(), label = "orbit-ring")
        val entry = remember { Animatable(1f) }
        val spotKey = spotOrb?.item?.key
        LaunchedEffect(spotKey) {
            if (Motion.style == AnimationStyle.Off) entry.snapTo(1f)
            else { entry.snapTo(0f); entry.animateTo(1f, Motion.move()) }
        }
        val glowColor = spotOrb?.item?.color ?: Palette.Lavender

        // A soft light in the spotlit orb's colour behind the fan, fading in with it.
        Box(
            Modifier.fillMaxWidth().height(fanBottom + orbD).graphicsLayer { alpha = 0.22f * entry.value }.drawWithCache {
                val brush = Brush.radialGradient(
                    listOf(glowColor, Color.Transparent), center = Offset(size.width / 2f, size.height * 0.55f), radius = size.height * 0.75f,
                )
                onDrawBehind { drawRect(brush) }
            },
        )

        if (orbs.isNotEmpty()) {
            OrbitStage(
                st, layout, orbD, ring,
                Modifier.offset(y = stageTop).fillMaxWidth().height(stageBottom - stageTop)
                    .focusRequester(stageReq)
                    .takesFocus(route, Orbit.STAGE, target == Orbit.STAGE),
                onUp = {
                    when {
                        cards.isNotEmpty() -> runCatching { fanReq.requestFocus() }.isSuccess
                        noGames -> runCatching { emptyReq.requestFocus() }.isSuccess
                        last != null -> runCatching { ribbonReq.requestFocus() }.isSuccess
                        else -> false
                    }
                },
                onDown = {
                    val key = lastDock?.takeIf { it in dockKeys } ?: dockKeys.getOrNull(dockKeys.size / 2)
                    key != null && runCatching { dockReqs.getOrPut(key) { FocusRequester() }.requestFocus() }.isSuccess
                },
            )
            OrbitCaption(
                st,
                Modifier.offset(y = baseY + orbD / 2 + 4.dp).align(Alignment.TopCenter).widthIn(max = w * 0.6f).height(captionH),
            )
        }

        // The spotlight: the fan, a note for an orb with nothing to fan out, or the empty library.
        val zone = Modifier.offset(y = fanTop).fillMaxWidth().height(fanBottom - fanTop)
        when {
            noGames -> Box(zone, contentAlignment = Alignment.Center) {
                val hidden = libs.any { it.games.isNotEmpty() }
                val pick = rememberGameFolderPicker()
                EmptyNote(
                    if (hidden) "Every game is hidden" else "No games yet. Pick the folder your games are in",
                    if (hidden) "Show hidden games" else "Pick your games folder",
                    Modifier.focusRequester(emptyReq).focusProperties { if (orbs.isNotEmpty()) down = stageReq; left = FocusRequester.Cancel; right = FocusRequester.Cancel }
                        .takesFocus(route, Orbit.EMPTY, target == Orbit.EMPTY),
                ) { if (hidden) Nav.go(Route.SettingsPage("library")) else pick() }
            }
            cards.isNotEmpty() -> SpotFan(
                cards, cardW, cardH, entry, route, target, fanFocus, fanReq, stageReq, ribbonReq.takeIf { last != null },
                onFocusCard = { key -> fanFocus = key }, modifier = zone,
            )
            spotOrb != null -> OrbNote(spotOrb, entry, zone)
        }

        if (last != null) ContinueRibbon(
            last, ribbonH,
            Modifier.align(Alignment.TopCenter).padding(top = 8.dp).width(min(380.dp, w - 48.dp))
                .focusRequester(ribbonReq)
                .focusProperties {
                    down = if (cards.isNotEmpty()) fanReq else if (orbs.isNotEmpty()) stageReq else FocusRequester.Default
                    left = FocusRequester.Cancel
                    right = FocusRequester.Cancel
                }
                .takesFocus(route, Orbit.CONTINUE, target == Orbit.CONTINUE),
            onFocus = { fanFocus = null },
        )

        if (dockKeys.isNotEmpty()) OrbitDock(
            dock, dockAll, route, target, stageReq.takeIf { orbs.isNotEmpty() }, dockReqs,
            onFocusKey = { lastDock = it },
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp),
        )
    }
}

/**
 * The orbit itself: one focusable stage that draws every orb in a graphics layer placed from the spin, the faint
 * line of the track under them, and takes the D-pad and touch for the whole orbit. [onUp] / [onDown] move the
 * highlight out of it and say whether they did.
 */
@Composable
private fun OrbitStage(
    st: OrbitState, layout: OrbitLayout, orbD: Dp, ring: State<Float>, modifier: Modifier,
    onUp: () -> Boolean, onDown: () -> Boolean,
) {
    val interaction = remember { MutableInteractionSource() }
    val press = remember { TilePress() }
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val muted = Palette.Muted
    val hair = with(LocalDensity.current) { 1.5.dp.toPx() }
    Box(
        modifier
            .onFocusChanged {
                val was = st.focused
                st.focused = it.isFocused
                if (it.isFocused && !was) {
                    Panels.focusedApp = null
                    st.front?.let { o -> Selection.current = o.item.selected }
                    Sounds.move()
                } else if (!it.isFocused) press.pressed = false
            }
            .onKeyEvent { e ->
                val down = e.type == KeyEventType.KeyDown
                when (e.key) {
                    Key.DirectionLeft -> { if (down) st.step(-1); true }
                    Key.DirectionRight -> { if (down) st.step(1); true }
                    Key.DirectionUp -> if (down) onUp() else false
                    // Nothing below the dock-less orbit: Down stays put rather than wandering off.
                    Key.DirectionDown -> { if (down) onDown(); true }
                    else -> tileKey(e, press, { st.open() }, null)
                }
            }
            .focusable(interactionSource = interaction)
            .pointerInput(layout) {
                detectTapGestures(
                    onTap = { pos -> tapOrb(st, pos, scope) },
                    onLongPress = { pos -> holdOrb(st, pos, haptic, scope) },
                )
            }
            .pointerInput(layout) {
                val tracker = VelocityTracker()
                detectHorizontalDragGestures(
                    onDragStart = { tracker.resetTracking(); st.stop() },
                    onDragEnd = { st.fling(-tracker.calculateVelocity().x / layout.pxPerOrb); if (!st.focused) st.focusFromTouch(scope) },
                    onDragCancel = { st.fling(0f) },
                ) { change, dx ->
                    change.consume()
                    tracker.addPosition(change.uptimeMillis, change.position)
                    st.dragBy(-dx / layout.pxPerOrb)
                }
            }
            .drawWithCache {
                // The orbit's path, drawn once per size: a faint line through where the orbs ride.
                val path = Path()
                var d = -(layout.side + 0.7f)
                var first = true
                while (d <= layout.side + 0.7f) {
                    val p = layout.place(d)
                    if (first) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
                    first = false
                    d += 0.1f
                }
                val brush = Brush.horizontalGradient(
                    0f to Color.Transparent, 0.18f to muted.copy(alpha = 0.32f), 0.5f to muted.copy(alpha = 0.42f),
                    0.82f to muted.copy(alpha = 0.32f), 1f to Color.Transparent,
                )
                val stroke = Stroke(hair, cap = StrokeCap.Round)
                onDrawBehind { drawPath(path, brush, style = stroke) }
            },
    ) {
        st.orbs.forEachIndexed { i, orb -> OrbView(orb, i, st, layout, orbD, ring) }
    }
}

/** The stage takes the highlight after a touch moved the orbit (so the D-pad carries on from the orb now in front). */
private fun OrbitState.focusFromTouch(scope: CoroutineScope) {
    scope.launch { FocusMemory.remember(Route.Home.key, Orbit.STAGE); Nav.focusTick++ }
}

/** A tap: the front orb opens when the orbit has the highlight; any other orb (or a first tap) comes to the front. */
private fun tapOrb(st: OrbitState, pos: Offset, scope: CoroutineScope) {
    val layout = st.layout ?: return
    val i = layout.hit(pos.x, pos.y, st.offset.value, st.count, st.wraps)
    if (i < 0) return
    if (i == st.frontIndex && st.focused && !st.offset.isRunning) { st.open(); Sounds.select(); return }
    st.show(i)
    if (!st.focused) st.focusFromTouch(scope)
}

/** A long press is Y: the orb comes to the front and takes the highlight, then its menu opens. */
private fun holdOrb(st: OrbitState, pos: Offset, haptic: HapticFeedback, scope: CoroutineScope) {
    val layout = st.layout ?: return
    val i = layout.hit(pos.x, pos.y, st.offset.value, st.count, st.wraps)
    if (i < 0) return
    Touch.buzz(haptic)
    st.show(i)
    st.front?.let { Selection.current = it.item.selected }
    scope.launch {
        Touch.holdForY({ st.focused }) { FocusMemory.remember(Route.Home.key, Orbit.STAGE); Nav.focusTick++ }
    }
}

/** One orb on the track: the icon art in a circle of its colour, a soft halo, and the highlight ring at the front. */
@Composable
private fun OrbView(orb: Orb, index: Int, st: OrbitState, layout: OrbitLayout, size: Dp, ring: State<Float>) {
    val item = orb.item
    val color = item.color
    val sizePx = with(LocalDensity.current) { size.toPx() }
    val ringW = with(LocalDensity.current) { (Themes.live.highlightThickness * 0.75f).coerceIn(1.5f, 5f).dp.toPx() }
    val ringColor = lerp(Palette.highlightFor(color), Color.White, 0.5f)
    val fill = remember(color) { Brush.linearGradient(listOf(lerp(color, Color.White, 0.18f), color, lerp(color, Color.Black, 0.3f))) }
    val dim = if (item.hidden) HiddenAlpha else 1f
    Box(
        Modifier.size(size)
            .graphicsLayer {
                val p = layout.place(OrbitLayout.distance(index, st.offset.value, st.count, st.wraps))
                translationX = p.x - sizePx / 2f
                translationY = p.y - sizePx / 2f
                scaleX = p.scale
                scaleY = p.scale
                alpha = p.alpha * dim
            }
            .drawWithCache {
                val r = this.size.width / 2f
                val halo = Brush.radialGradient(listOf(color.copy(alpha = 0.45f), color.copy(alpha = 0.12f), Color.Transparent), radius = r * 1.5f)
                val rim = Stroke(r * 0.04f)
                val ringStroke = Stroke(ringW)
                onDrawWithContent {
                    drawCircle(halo, radius = r * 1.5f)
                    drawContent()
                    drawCircle(Color.White.copy(alpha = 0.28f), radius = r - rim.width / 2f, style = rim)
                    val near = 1f - abs(OrbitLayout.distance(index, st.offset.value, st.count, st.wraps)).coerceAtMost(1f)
                    val f = ring.value * near
                    if (f > 0.01f) drawCircle(ringColor, radius = r + ringW * 1.6f, alpha = f, style = ringStroke)
                }
            },
    ) {
        Box(Modifier.fillMaxSize().clip(CircleShape).background(fill)) {
            Box(Modifier.fillMaxSize().padding(size * 0.08f).clip(CircleShape)) { TileArtView(item.art, size) }
        }
        if (item.hidden) HiddenBadge(Modifier.align(Alignment.TopStart))
    }
}

/** The name of what is highlighted, under the front orb: the orb's, or the fan card's or ribbon's game. */
@Composable
private fun OrbitCaption(st: OrbitState, modifier: Modifier) {
    val ctx = LocalContext.current
    val sel = Selection.current
    val orb = st.orbs.firstOrNull { it.item.key == st.spot } ?: st.front ?: return
    val (title, detail, color) = when {
        !st.focused && sel is Selected.GameItem -> Triple(sel.game.title, sel.game.platform.name, SystemColors.of(sel.game.platform.id))
        !st.focused && sel is Selected.App ->
            Triple(if (sel.pkg == Dock.ALL_APPS) "All apps" else remember(sel.pkg) { RunningGames.appLabel(ctx, sel.pkg) }, "App", Palette.Sky)
        else -> Triple(orb.item.title, orb.detail, orb.item.color)
    }
    AnimatedContent(
        title to detail, modifier,
        transitionSpec = { fadeIn(Motion.fade(180)) togetherWith fadeOut(Motion.fade(110)) },
        contentAlignment = Alignment.TopCenter, label = "orbit-caption",
    ) { (t, d) ->
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(t, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            // On a chip of the card colour: the line sits on the wallpaper, where a pale patch would swallow it.
            if (d.isNotEmpty()) Box(
                Modifier.padding(top = 4.dp).clip(RoundedCornerShape(50)).background(Palette.Card.copy(alpha = 0.82f)).padding(horizontal = 12.dp, vertical = 3.dp),
            ) {
                Text(
                    d, style = MaterialTheme.typography.titleSmall, color = Palette.readable(color),
                    maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/**
 * The spotlight's fan: real covers at their own proportions, tilted like cards in a hand, the latest in the middle
 * and larger. Each card is a [FocusCard] placed at its real spot (so Left and Right walk them in order); the tilt,
 * the size and the entry are graphics-layer only. Down goes back to the orbit, Up to the Continue ribbon.
 */
@Composable
private fun SpotFan(
    cards: List<FanCard>, cardW: Dp, cardH: Dp, entry: Animatable<Float, AnimationVector1D>, route: String, target: String?,
    focusedKey: String?, middle: FocusRequester, stage: FocusRequester, up: FocusRequester?, onFocusCard: (String) -> Unit, modifier: Modifier,
) {
    val ctx = LocalContext.current
    val cardWpx = with(LocalDensity.current) { cardW.toPx() }
    // Room above the middle card for its larger size.
    val inset = with(LocalDensity.current) { (cardH * 0.08f).toPx() }
    Box(modifier.focusRow(), contentAlignment = Alignment.TopCenter) {
        val n = cards.size
        cards.forEachIndexed { rank, card ->
            val p = Fan.place(rank, n, cardWpx)
            val key = "fan:${card.key}"
            val game = card.game
            val pkg = card.pkg
            val glow = game?.let { SystemColors.of(it.platform.id) } ?: Palette.Sky
            FocusCard(
                Modifier
                    .offset { IntOffset(p.x.roundToInt(), (p.y + inset).roundToInt()) }
                    .zIndex(if (key == focusedKey) 20f else 10f - abs(Fan.slot(rank)))
                    .size(cardW, cardH)
                    .graphicsLayer {
                        val e = entry.value
                        rotationZ = p.rotation * e
                        val s = 0.82f + (p.scale - 0.82f) * e
                        scaleX = s
                        scaleY = s
                        translationX = -p.x * (1f - e)
                        alpha = e.coerceIn(0f, 1f)
                    }
                    .then(if (rank == 0) Modifier.focusRequester(middle) else Modifier)
                    .focusProperties { down = stage; if (up != null) this.up = up }
                    .takesFocus(route, key, key == target)
                    .onFocusChanged { if (!it.isFocused && pkg != null && Panels.focusedApp == pkg) Panels.focusedApp = null },
                glow = glow, shape = RoundedCornerShape(12.dp), entry = true,
                onFocus = {
                    onFocusCard(key)
                    if (game != null) Selection.current = Selected.GameItem(game)
                    else if (pkg != null) { Panels.focusedApp = pkg; Selection.current = Selected.App(pkg) }
                },
                onClick = { if (game != null) Launcher.launch(ctx, game) else if (pkg != null) Dock.open(ctx, pkg) },
            ) {
                if (game != null) GameBoxArt(game, cardW)
                else if (pkg != null) Column(
                    Modifier.fillMaxSize().background(lerp(Palette.Card, Palette.Sky, 0.12f)).padding(8.dp),
                    verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    AppIcon(pkg, Modifier.size(cardW * 0.6f).clip(RoundedCornerShape(14.dp)))
                    Spacer(Modifier.height(8.dp))
                    Text(
                        remember(pkg) { RunningGames.appLabel(ctx, pkg) }, style = MaterialTheme.typography.labelMedium,
                        maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/** The spotlight for an orb with nothing to fan out (one of Tandem's apps): its art big, and what it is. */
@Composable
private fun OrbNote(orb: Orb, entry: Animatable<Float, AnimationVector1D>, modifier: Modifier) {
    Column(
        modifier.graphicsLayer { alpha = entry.value.coerceIn(0f, 1f); translationY = (1f - entry.value) * 24f * density },
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(92.dp).clip(RoundedCornerShape(26.dp))) { TileArtView(orb.item.art, 92.dp) }
        Spacer(Modifier.height(12.dp))
        val line = orb.item.blurb.ifEmpty { if (orb.item.kind == TileKind.AllApps) "Pin apps to the dock to see them here" else orb.item.type }
        Text(
            line, Modifier.widthIn(max = 420.dp).padding(horizontal = 16.dp), style = MaterialTheme.typography.titleMedium,
            color = Palette.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
        )
    }
}

/** The last game played anywhere, as a wide card over the fan: A picks it up again. */
@Composable
private fun ContinueRibbon(game: Game, height: Dp, modifier: Modifier, onFocus: () -> Unit) {
    val ctx = LocalContext.current
    val running = RunningGames.list.any { it.game?.key == game.key }
    val color = SystemColors.of(game.platform.id)
    FocusCard(
        modifier.height(height), glow = Palette.Mint, shape = RoundedCornerShape(height / 2), entry = true,
        onFocus = { onFocus(); Selection.current = Selected.GameItem(game) },
        onClick = { Launcher.launch(ctx, game) },
    ) {
        Row(
            Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(lerp(color, Palette.Card, 0.55f), Palette.Card.copy(alpha = 0.85f)))),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.padding(start = 6.dp).size(height - 12.dp).clip(CircleShape)) { GameBoxArt(game, height) }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(
                    if (running) "Resume" else "Continue", style = MaterialTheme.typography.labelMedium,
                    color = Palette.readable(Palette.Mint), maxLines = 1,
                )
                Text(game.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(
                game.platform.shortName, Modifier.padding(end = 18.dp), style = MaterialTheme.typography.labelMedium,
                color = Palette.Muted, maxLines = 1,
            )
        }
    }
}

/** The dock under the orbit: the pinned apps as small round icons. Up goes back to the orbit. */
@Composable
private fun OrbitDock(
    dock: List<String>, withAll: Boolean, route: String, target: String?, stage: FocusRequester?,
    requesters: HashMap<String, FocusRequester>, onFocusKey: (String) -> Unit, modifier: Modifier,
) {
    val ctx = LocalContext.current
    Row(modifier.focusRow(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        @Composable
        fun Icon(key: String, pkg: String, onClick: () -> Unit, content: @Composable () -> Unit) {
            FocusCard(
                Modifier.size(44.dp)
                    .focusRequester(requesters.getOrPut(key) { FocusRequester() })
                    .focusProperties { if (stage != null) up = stage }
                    .takesFocus(route, key, key == target)
                    .onFocusChanged { if (!it.isFocused && Panels.focusedApp == pkg) Panels.focusedApp = null },
                glow = Palette.Sky, shape = CircleShape, entry = pkg != Dock.ALL_APPS,
                onFocus = {
                    onFocusKey(key)
                    Panels.focusedApp = pkg.takeIf { it != Dock.ALL_APPS }
                    Selection.current = Selected.App(pkg)
                },
                onClick = onClick,
            ) { content() }
        }
        dock.forEach { pkg ->
            Icon("app:$pkg", pkg, { Dock.open(ctx, pkg) }) { AppIcon(pkg, Modifier.fillMaxSize().padding(5.dp).clip(CircleShape)) }
        }
        if (withAll) Icon("app:*", Dock.ALL_APPS, { Panels.openApps() }) {
            Box(Modifier.fillMaxSize().background(Palette.Sky.copy(alpha = 0.22f)), contentAlignment = Alignment.Center) {
                GlyphIcon(Glyph.Grid, Palette.Sky, Modifier.size(22.dp))
            }
        }
    }
}

/** Orbit's rows in Home & layout › Home screen (shown while Orbit is the layout). */
@Composable
fun orbitSettings(): SettingSection {
    val look = Store.settings.look
    return SettingSection(
        "Orbit",
        listOf(
            choice(
                "Orbit holds", Glyph.Home, Palette.Lavender,
                "What rides the orbit: your systems, collections and Tandem's apps, or fewer of them. L1 and R1 jump between the groups.",
                Orbit.Groups, look.orbitGroups,
            ) { v -> updateLook { it.copy(orbitGroups = v) } }.copy(keywords = "orbit orbs planets systems collections apps groups"),
            toggle(
                "Continue ribbon", Glyph.Play, Palette.Mint,
                "The last game you played, as a wide card above the spotlight's fan. A picks it up again.",
                look.orbitContinue,
            ) { v -> updateLook { it.copy(orbitContinue = v) } }.copy(keywords = "orbit continue resume last played recent"),
        ),
    )
}
