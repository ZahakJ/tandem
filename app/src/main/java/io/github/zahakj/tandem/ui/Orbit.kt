package io.github.zahakj.tandem.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.wrapContentSize
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
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import io.github.zahakj.tandem.Collections
import io.github.zahakj.tandem.Game
import coil.compose.AsyncImage
import io.github.zahakj.tandem.Launcher
import io.github.zahakj.tandem.Libraries
import androidx.compose.ui.layout.ContentScale
import io.github.zahakj.tandem.PlatformLibrary
import io.github.zahakj.tandem.R
import io.github.zahakj.tandem.RunningGames
import io.github.zahakj.tandem.Selected
import io.github.zahakj.tandem.Selection
import io.github.zahakj.tandem.Tx
import io.github.zahakj.tandem.data.GameStatus
import io.github.zahakj.tandem.data.HomeStore
import io.github.zahakj.tandem.data.Store
import io.github.zahakj.tandem.data.playStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

/*
 * Orbit, Tandem's own home layout ("orbit"). The lower part of the top screen is the orbit: the home's systems,
 * collections and Tandem's apps as glassy orbs riding a glowing ring seen in perspective, like planets on an orbit
 * seen from a little above. The highlighted orb sits at the front, lowest and biggest, its name under it; the others
 * shrink and sink into the dark as they go round. Left and Right spin the ring. Inside it the spotlight stands that
 * orb's latest games as a carousel of covers on a lit floor (Up goes into it), with the Continue card (the last game
 * played anywhere) apart to their left. Down reaches a quiet dock of round app icons.
 *
 * The geometry lives in OrbitLayout.kt. The spin is one Animatable (the orbit's offset, in orbs) read only in
 * graphics layers and draw lambdas, so a spin recomposes nothing; the covers' entry is one more. Only the settled
 * orb's covers load, at their drawn size.
 */

object Orbit {
    /** Orbit is the home layout. */
    val enabled: Boolean get() = Store.settings.look.homeLayout == "orbit"

    /** The focus route remembering which orb is at the front (the home route remembers which part has the highlight). */
    const val ROUTE = "home:orbit"
    /** Focus keys on the home route: the orbit itself, the Continue card, the empty library's note. */
    const val STAGE = "orbit"
    const val CONTINUE = "orbit:continue"
    const val EMPTY = "orbit:empty"

    val Groups: List<Pair<String, String>> get() = listOf(
        "all" to Tx.s(R.string.home_orbit_holds_all), "games" to Tx.s(R.string.home_orbit_holds_games),
        "systems" to Tx.s(R.string.home_orbit_holds_systems),
    )

    const val SYSTEMS = 0
    const val COLLECTIONS = 1
    const val APPS = 2
}

/** One orb: an icon grid tile ([GridCatalog] builds them, designs and pack art included), its group and one line about it. */
private class Orb(val item: GridItem, val group: Int, val detail: String, val open: () -> Unit)

/** The parts of the home off the orbit that can hold the highlight. */
private enum class OrbitPart { Fan, Continue, Dock }

/** A card in the spotlight's fan: a game, or an app (the All apps orb fans out the dock). */
private class FanCard(val key: String, val game: Game? = null, val pkg: String? = null)

/** Collections whose own order is the point (most played, newest first...): their fan keeps it. */
private val KeepOrder = setOf(io.github.zahakj.tandem.ThisWeek.ID, "most", "added", "continue", Collections.PLAYING, Collections.BACKLOG, Collections.BEATEN)

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
                listOfNotNull(
                    Tx.p(R.plurals.home_orbit_games, item.games.size),
                    if (playing > 0) Tx.p(R.plurals.home_orbit_playing, playing) else item.blurb.ifEmpty { null },
                ).joinParts()
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

/**
 * The spin's spring, after the theme's animation style. Always a spring (Minimal's too, only stiffer): a tween
 * started again on every D-pad repeat drops its speed each time and the orbit moves in steps.
 */
private fun glide(): AnimationSpec<Float> = when (Motion.style) {
    AnimationStyle.Bouncy -> spring(0.72f, 260f)
    AnimationStyle.Smooth -> spring(Spring.DampingRatioNoBouncy, 240f)
    AnimationStyle.Minimal -> spring(Spring.DampingRatioNoBouncy, 520f)
    AnimationStyle.Off -> snap()
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
            // A new goal mid-spin carries on at the speed the orbit already has, so a held D-pad is one glide
            // rather than a start and a stop for every orb.
            if (still) offset.snapTo(to) else offset.animateTo(to, glide(), initialVelocity = if (velocity != 0f) velocity else offset.velocity)
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
    // The game open right now when there is one (Resume), else the last played: it changes as games start and close.
    val last = if (look.orbitContinue) continueGame() else null

    val keys = buildList {
        if (orbs.isNotEmpty()) add(Orbit.STAGE)
        if (last != null && !noGames) add(Orbit.CONTINUE)
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
    val continueReq = remember { FocusRequester() }
    val emptyReq = remember { FocusRequester() }
    val dockReqs = remember { HashMap<String, FocusRequester>() }
    var lastDock by remember { mutableStateOf<String?>(null) }
    var fanFocus by remember { mutableStateOf<String?>(null) }
    // Which part off the orbit last took the highlight: the caption names what is highlighted, and steps aside for
    // the dock (whose icon names itself).
    var part by remember { mutableStateOf(OrbitPart.Fan) }

    fun focusStage() {
        if (orbs.isEmpty()) return
        FocusMemory.remember(route, Orbit.STAGE)
        if (runCatching { stageReq.requestFocus() }.isFailure) Nav.focusTick++
    }

    // L1 / R1: the ring turns one orb (each orb is a group, GroupPaging.kt), from anywhere on the home. L2 / R2 on the
    // ring: the first orb of the section before or after (systems, collections, apps), the bigger jump L2 / R2 make
    // elsewhere. Off the ring R2 still opens the side panel.
    InterceptButtons(true) { b ->
        if (!GroupPaging.free || st.count == 0) return@InterceptButtons false
        val by = if (b == io.github.zahakj.tandem.Button.R1 || b == io.github.zahakj.tandem.Button.R2) 1 else -1
        when (b) {
            io.github.zahakj.tandem.Button.L1, io.github.zahakj.tandem.Button.R1 -> {
                st.step(by)
                if (!st.focused) focusStage()
                true
            }
            io.github.zahakj.tandem.Button.L2, io.github.zahakj.tandem.Button.R2 -> {
                if (!st.focused) return@InterceptButtons false
                val to = OrbitLayout.groupJump(st.orbs.map { it.group }, st.frontIndex, by)
                if (to != st.frontIndex) st.show(to)
                true
            }
            else -> false
        }
    }
    // B: from the covers, the Continue card or the dock, back to the orbit; on the orbit, back round to the first orb
    // (Home never leaves Tandem, as on the other layouts).
    BackHandler(enabled = Nav.route == Route.Home && !Nav.overlayOpen && FolderPopup.route == null && orbs.isNotEmpty()) {
        if (!st.focused) focusStage() else st.show(0)
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val w = maxWidth
        val h = maxHeight
        // The bottom screen (screens swapped) is narrow: fewer orbs either side, so they keep their room.
        val side = if (w < 600.dp) 3 else 4
        st.side = side
        val orbD = min(96.dp, h * 0.22f)
        // From the bottom: the dock (its 50 dp tray 8 dp up) and clear room over it, then the caption (a title and a
        // line, 56 dp) a little under the front orb. The caption never reaches the tray.
        val dockH = if (dockKeys.isNotEmpty()) 58.dp + 14.dp else 10.dp
        val captionH = 56.dp
        val captionGap = 8.dp
        val baseY = h - dockH - captionH - captionGap - orbD / 2
        val rise = min(h * 0.17f, baseY * 0.3f)
        val stageTop = (baseY - rise - orbD * 0.6f).coerceAtLeast(0.dp)
        val stageBottom = baseY + orbD * 0.6f
        // The covers stand on a floor clear of the front orb. They take the room above it, less a little for the
        // focus zoom and the Continue card's label, and shrink so the carousel and the Continue card fit in half
        // the width either side of the middle.
        val fanTop = 10.dp
        val floorY = (baseY - orbD / 2 - 24.dp).coerceAtLeast(fanTop + 60.dp)
        val withContinue = last != null && !noGames
        val cardW = min((floorY - fanTop) / 1.12f / Fan.ASPECT, (w / 2 - 14.dp) / Fan.reach(cards.size, withContinue)).coerceAtLeast(36.dp)
        val cardH = cardW * Fan.ASPECT
        // In right to left the ring runs the other way round: the next orbs on the left, as everything else runs.
        val rtl = isRtl
        val layout = with(density) {
            remember(w, h, side, orbD, rise, rtl) { OrbitLayout(w.toPx(), (baseY - stageTop).toPx(), rise.toPx(), orbD.toPx(), side, mirrored = rtl) }
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
        val tint = animateColorAsState(glowColor, Motion.soft(), label = "orbit-tint")
        val deep = Palette.NightDeep

        // Shade towards the bottom and the sides, under the orbit, the names and the dock: whatever the wallpaper,
        // they read against something calm.
        Box(
            Modifier.fillMaxSize().drawWithCache {
                val fall = Brush.verticalGradient(
                    0f to Color.Transparent, 0.4f to Color.Transparent, 0.75f to deep.copy(alpha = 0.4f), 1f to deep.copy(alpha = 0.72f),
                )
                val sides = Brush.horizontalGradient(
                    0f to deep.copy(alpha = 0.42f), 0.16f to Color.Transparent, 0.84f to Color.Transparent, 1f to deep.copy(alpha = 0.42f),
                )
                onDrawBehind { drawRect(fall); drawRect(sides) }
            },
        )
        // A soft light in the spotlit orb's colour behind the covers, fading in with them.
        Box(
            Modifier.fillMaxSize().graphicsLayer { alpha = 0.3f * entry.value; compositingStrategy = CompositingStrategy.ModulateAlpha }.drawWithCache {
                val c = Offset(size.width / 2f, (fanTop + (floorY - fanTop) * 0.55f).toPx())
                val brush = Brush.radialGradient(listOf(glowColor, glowColor.copy(alpha = 0.3f), Color.Transparent), center = c, radius = cardH.toPx() * 1.6f)
                onDrawBehind { drawRect(brush) }
            },
        )

        if (orbs.isNotEmpty()) {
            OrbitStage(
                st, layout, orbD, ring, tint,
                Modifier.offset(y = stageTop).fillMaxWidth().height(stageBottom - stageTop)
                    .focusRequester(stageReq)
                    .takesFocus(route, Orbit.STAGE, target == Orbit.STAGE),
                onUp = {
                    val to = when {
                        cards.isNotEmpty() -> fanReq
                        noGames -> emptyReq
                        withContinue -> continueReq
                        else -> null
                    }
                    to != null && runCatching { to.requestFocus() }.isSuccess
                },
                onDown = {
                    val key = lastDock?.takeIf { it in dockKeys } ?: dockKeys.getOrNull(dockKeys.size / 2)
                    key != null && runCatching { dockReqs.getOrPut(key) { FocusRequester() }.requestFocus() }.isSuccess
                },
            )
            OrbitCaption(
                st, part,
                Modifier.offset(y = baseY + orbD / 2 + captionGap).align(Alignment.TopCenter).widthIn(max = w * 0.56f).height(captionH),
            )
            // L1 and R1 at the ring's ends, level with the caption: each turns the ring one orb that way.
            if (orbs.size > 1) ShoulderEnds(Modifier.offset(y = baseY + orbD / 2 + captionGap + 8.dp).fillMaxWidth().padding(horizontal = 22.dp))
        }

        // The spotlight: the covers on their floor with the Continue card beside them, a note for an orb with
        // nothing to show, or the empty library. One row: Left and Right walk from the Continue card into the covers.
        val cardWpx = with(density) { cardW.toPx() }
        Box(
            Modifier.offset(y = fanTop).fillMaxWidth().height(floorY - fanTop).focusRow()
                .then(if (cards.isNotEmpty()) Modifier.floorLight(glowColor, Fan.reach(cards.size, false) * cardWpx) else Modifier),
            contentAlignment = Alignment.BottomCenter,
        ) {
            when {
                noGames -> {
                    val hidden = libs.any { it.games.isNotEmpty() }
                    val pick = rememberGameFolderPicker()
                    EmptyNote(
                        Tx.s(if (hidden) R.string.home_orbit_empty_hidden else R.string.home_orbit_empty_none),
                        Tx.s(if (hidden) R.string.home_orbit_empty_show else R.string.home_orbit_empty_pick),
                        Modifier.focusRequester(emptyReq).focusProperties { if (orbs.isNotEmpty()) down = stageReq; left = FocusRequester.Cancel; right = FocusRequester.Cancel }
                            .takesFocus(route, Orbit.EMPTY, target == Orbit.EMPTY),
                        modifier = Modifier.align(Alignment.Center),
                    ) { if (hidden) openSetting("play/privacy", ShowHiddenKey) else pick() }
                }
                cards.isNotEmpty() -> SpotFan(
                    cards, cardW, cardH, entry, route, target, fanFocus, fanReq, stageReq,
                    onFocusCard = { key -> fanFocus = key; part = OrbitPart.Fan },
                )
                spotOrb != null -> OrbNote(spotOrb, entry, if (withContinue) cardW * 2.6f else 340.dp, Modifier.fillMaxSize())
            }
            if (withContinue && last != null) ContinueCard(
                last, Fan.continueX(cards.size, cardWpx), cardW, cardH,
                Modifier.focusRequester(continueReq)
                    .focusProperties {
                        down = if (orbs.isNotEmpty()) stageReq else FocusRequester.Default
                        // Outwards (the start side) stops; towards the covers goes on.
                        start = FocusRequester.Cancel
                        if (cards.isEmpty()) end = FocusRequester.Cancel
                    }
                    .takesFocus(route, Orbit.CONTINUE, target == Orbit.CONTINUE),
                onFocus = { fanFocus = null; part = OrbitPart.Continue },
            )
        }

        if (dockKeys.isNotEmpty()) OrbitDock(
            dock, dockAll, route, target, stageReq.takeIf { orbs.isNotEmpty() }, dockReqs,
            onFocusKey = { lastDock = it; part = OrbitPart.Dock },
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp),
        )
    }
}

/**
 * The orbit itself: one focusable stage that draws its ring and every orb in a graphics layer placed from the spin,
 * and takes the D-pad and touch for the whole orbit. [onUp] / [onDown] move the highlight out of it and say whether
 * they did. [tint] colours the ring after the orb at the front.
 */
@Composable
private fun OrbitStage(
    st: OrbitState, layout: OrbitLayout, orbD: Dp, ring: State<Float>, tint: State<Color>, modifier: Modifier,
    onUp: () -> Boolean, onDown: () -> Boolean,
) {
    val interaction = remember { MutableInteractionSource() }
    val press = remember { TilePress() }
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val glowW = with(LocalDensity.current) { 9.dp.toPx() }
    val lineW = with(LocalDensity.current) { 1.6.dp.toPx() }
    val bead = with(LocalDensity.current) { 2.4.dp.toPx() }
    // A drag spins the ring the way the finger goes, whichever way round it runs.
    val spin = if (layout.mirrored) -1f else 1f
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
                    // The way the arrow points: the neighbour on that side comes to the front (Rtl.kt).
                    Key.DirectionLeft, Key.DirectionRight -> { if (down) st.step(e.sideStep()); true }
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
                    onDragEnd = { st.fling(-tracker.calculateVelocity().x * spin / layout.pxPerOrb); if (!st.focused) st.focusFromTouch(scope) },
                    onDragCancel = { st.fling(0f) },
                ) { change, dx ->
                    change.consume()
                    tracker.addPosition(change.uptimeMillis, change.position)
                    st.dragBy(-dx * spin / layout.pxPerOrb)
                }
            }
            .drawWithCache {
                // The ring, built once per size or colour: a pool of light inside it, a wide soft glow and a fine
                // line, brightest at the front (the bottom) and fading towards the far side behind the covers.
                val c = tint.value
                val light = lerp(c, Color.White, 0.55f)
                val top = layout.centreY - layout.radiusY
                val bottom = layout.centreY + layout.radiusY
                val centre = Offset(layout.centreX, layout.centreY)
                val corner = Offset(layout.centreX - layout.radiusX, top)
                val oval = Size(layout.radiusX * 2f, layout.radiusY * 2f)
                val pool = Brush.radialGradient(listOf(c.copy(alpha = 0.16f), c.copy(alpha = 0.05f), Color.Transparent), center = centre, radius = layout.radiusX)
                val glow = Brush.verticalGradient(0f to c.copy(alpha = 0.02f), 0.55f to c.copy(alpha = 0.08f), 1f to c.copy(alpha = 0.28f), startY = top, endY = bottom)
                val line = Brush.verticalGradient(
                    0f to light.copy(alpha = 0.1f), 0.6f to light.copy(alpha = 0.32f), 1f to light.copy(alpha = 0.85f), startY = top, endY = bottom,
                )
                val glowStroke = Stroke(glowW)
                val lineStroke = Stroke(lineW)
                onDrawBehind {
                    scale(1f, layout.radiusY / layout.radiusX, pivot = centre) { drawCircle(pool, layout.radiusX, centre) }
                    drawOval(glow, corner, oval, style = glowStroke)
                    drawOval(line, corner, oval, style = lineStroke)
                    // Small lights halfway between the orbs ride round with them, so the ring is seen to turn.
                    val off = st.offset.value
                    val frac = off - floor(off)
                    for (k in -(layout.side + 1)..layout.side) {
                        val p = layout.place(k + 0.5f - frac)
                        if (p.alpha < 0.02f) continue
                        drawCircle(light, bead * (0.45f + 0.55f * p.scale * OrbitLayout.FOCUS), Offset(p.x, p.y), alpha = 0.75f * p.alpha * (1f - 0.5f * p.back))
                    }
                }
            },
        // The orbs are moved from the left edge by the layout's own x (already mirrored in right to left).
        contentAlignment = AbsoluteAlignment.TopLeft,
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

/**
 * One orb on the ring: the icon art in a sphere of its colour (lit from the top left, shaded towards the bottom
 * right, with a fine glassy rim), a soft shadow on the ring under it, its colour's glow and the highlight ring at the
 * front, and the dark of the far side coming over it as it goes round. Everything moving is read in the layer and
 * draw lambdas.
 */
@Composable
private fun OrbView(orb: Orb, index: Int, st: OrbitState, layout: OrbitLayout, size: Dp, ring: State<Float>) {
    val item = orb.item
    val color = item.color
    val sizePx = with(LocalDensity.current) { size.toPx() }
    val ringW = with(LocalDensity.current) { (Themes.live.highlightThickness * 0.75f).coerceIn(1.5f, 5f).dp.toPx() }
    val ringColor = lerp(Palette.highlightFor(color), Color.White, 0.5f)
    val fill = remember(color) { Brush.linearGradient(listOf(lerp(color, Color.White, 0.18f), color, lerp(color, Color.Black, 0.3f))) }
    val dim = if (item.hidden) HiddenAlpha else 1f
    val deep = Palette.NightDeep
    Box(
        Modifier.size(size)
            .graphicsLayer {
                val p = layout.place(OrbitLayout.distance(index, st.offset.value, st.count, st.wraps))
                translationX = p.x - sizePx / 2f
                translationY = p.y - sizePx / 2f
                scaleX = p.scale
                scaleY = p.scale
                alpha = p.alpha * dim
                compositingStrategy = CompositingStrategy.ModulateAlpha
            }
            .drawWithCache {
                val r = this.size.width / 2f
                val mid = Offset(r, r)
                val halo = Brush.radialGradient(listOf(color.copy(alpha = 0.55f), color.copy(alpha = 0.16f), Color.Transparent), center = mid, radius = r * 1.75f)
                val foot = Offset(r, r * 2.02f)
                val shadow = Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.5f), Color.Transparent), center = foot, radius = r * 0.95f)
                val shade = Brush.radialGradient(
                    0f to Color.Transparent, 0.62f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.42f),
                    center = Offset(r * 0.78f, r * 0.7f), radius = r * 1.3f,
                )
                val shineAt = Offset(r * 0.66f, r * 0.46f)
                val shine = Brush.radialGradient(listOf(Color.White.copy(alpha = 0.4f), Color.White.copy(alpha = 0f)), center = shineAt, radius = r * 0.72f)
                val rim = Stroke(r * 0.035f)
                val ringStroke = Stroke(ringW)
                val glowStroke = Stroke(ringW * 3.5f)
                onDrawWithContent {
                    val d = OrbitLayout.distance(index, st.offset.value, st.count, st.wraps)
                    val near = 1f - abs(d).coerceAtMost(1f)
                    val back = layout.place(d).back
                    scale(1f, 0.26f, pivot = foot) { drawCircle(shadow, radius = r * 0.95f, center = foot) }
                    drawCircle(halo, radius = r * 1.75f, alpha = 0.35f + 0.65f * near)
                    drawContent()
                    drawCircle(shade, radius = r)
                    drawCircle(shine, radius = r * 0.72f, center = shineAt)
                    drawCircle(Color.White.copy(alpha = 0.3f), radius = r - rim.width / 2f, style = rim)
                    if (back > 0.01f) drawCircle(deep, radius = r, alpha = 0.55f * back)
                    val f = ring.value * near
                    if (f > 0.01f) {
                        drawCircle(ringColor, radius = r + ringW * 1.8f, alpha = f * 0.22f, style = glowStroke)
                        drawCircle(ringColor, radius = r + ringW * 1.6f, alpha = f, style = ringStroke)
                    }
                }
            },
    ) {
        Box(Modifier.fillMaxSize().clip(CircleShape).background(fill)) {
            Box(Modifier.fillMaxSize().padding(size * 0.08f).clip(CircleShape)) { TileArtView(item.art, size) }
        }
        if (item.hidden) HiddenBadge(Modifier.align(Alignment.TopStart))
    }
}

/** Text over the wallpaper: a soft shadow under it, so it reads on a bright patch too. */
private val CaptionShadow = Shadow(Color.Black.copy(alpha = 0.55f), Offset(0f, 2f), 12f)

/**
 * The name of what is highlighted, under the front orb: the orb's, or the game (or app) of the highlighted cover or
 * Continue card, with one line about it after a dot of its colour. On the dock it steps aside: the highlighted icon
 * names itself there, and the orb's name under the orbit would only say something else. A game still running says
 * Resume, with Y's Close beside it ([closes]), on the Continue card and among the covers alike.
 */
@Composable
private fun OrbitCaption(st: OrbitState, part: OrbitPart, modifier: Modifier) {
    val ctx = LocalContext.current
    val sel = Selection.current
    val orb = st.orbs.firstOrNull { it.item.key == st.spot } ?: st.front ?: return
    val off = !st.focused
    val running = off && part != OrbitPart.Dock && sel is Selected.GameItem && runningOf(sel.game) != null
    val caption = when {
        off && part == OrbitPart.Continue && sel is Selected.GameItem ->
            Caption(sel.game.title, Tx.s(if (running) R.string.home_orbit_caption_resume else R.string.home_orbit_caption_continue, sel.game.platform.name), Palette.Mint, running)
        off && part == OrbitPart.Fan && sel is Selected.GameItem -> Caption(
            sel.game.title, if (running) Tx.s(R.string.home_orbit_caption_resume, sel.game.platform.name) else sel.game.platform.name,
            SystemColors.of(sel.game.platform.id), running,
        )
        off && part == OrbitPart.Fan && sel is Selected.App ->
            Caption(
                if (sel.pkg == Dock.ALL_APPS) Tx.s(R.string.home_orbit_all_apps) else remember(sel.pkg) { RunningGames.appLabel(ctx, sel.pkg) },
                Tx.s(R.string.home_orbit_app), Palette.Sky,
            )
        else -> Caption(orb.item.title, orb.detail, orb.item.color)
    }
    val shown = animateFloatAsState(if (off && part == OrbitPart.Dock) 0f else 1f, Motion.soft(), label = "orbit-caption-shown")
    val deep = Palette.NightDeep
    AnimatedContent(
        caption,
        modifier
            .graphicsLayer { alpha = shown.value; compositingStrategy = CompositingStrategy.ModulateAlpha }
            .drawWithCache {
                // A pool of shade under the words, wider than they are, fading out at its edges.
                val mid = Offset(size.width / 2f, size.height * 0.45f)
                val r = size.width * 0.5f
                val brush = Brush.radialGradient(listOf(deep.copy(alpha = 0.5f), deep.copy(alpha = 0.22f), Color.Transparent), center = mid, radius = r)
                onDrawBehind { scale(1f, 0.42f, pivot = mid) { drawCircle(brush, r, mid) } }
            },
        transitionSpec = { fadeIn(Motion.fade(180)) togetherWith fadeOut(Motion.fade(110)) },
        contentAlignment = Alignment.TopCenter, label = "orbit-caption",
    ) { (t, d, c, closes) ->
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                t, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold, shadow = CaptionShadow),
                maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            )
            if (d.isNotEmpty()) Row(Modifier.padding(top = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(c))
                Spacer(Modifier.width(7.dp))
                Text(
                    d, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.titleSmall.copy(shadow = CaptionShadow),
                    color = Palette.Text.copy(alpha = 0.82f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                // Y on a running game closes it (press twice), as on the icon grid's Resume page.
                if (closes) {
                    Spacer(Modifier.width(12.dp))
                    ButtonHint("Y", Tx.s(R.string.home_running_hint_close), Palette.Peach)
                }
            }
        }
    }
}

/** What [OrbitCaption] says: a name, a line about it in [color]'s dot, and whether Y closes the game ([closes]). */
private data class Caption(val title: String, val detail: String, val color: Color, val closes: Boolean = false)

/** A soft shadow on the floor under a cover, so it stands on the stage rather than floating over it. */
private fun Modifier.floorShadow(): Modifier = drawWithCache {
    val foot = Offset(size.width / 2f, size.height)
    val r = size.width * 0.62f
    val brush = Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Black.copy(alpha = 0.2f), Color.Transparent), center = foot, radius = r)
    onDrawBehind { scale(1f, 0.13f, pivot = foot) { drawCircle(brush, r, foot) } }
}

/** The floor the covers stand on: a line of light in [color] along the bottom, [halfSpan] px either side of the middle. */
private fun Modifier.floorLight(color: Color, halfSpan: Float): Modifier = drawWithCache {
    val cx = size.width / 2f
    val y = size.height
    val span = halfSpan * 1.25f
    val line = Brush.horizontalGradient(
        0f to Color.Transparent, 0.5f to lerp(color, Color.White, 0.4f).copy(alpha = 0.55f), 1f to Color.Transparent, startX = cx - span, endX = cx + span,
    )
    val pool = Brush.radialGradient(listOf(color.copy(alpha = 0.32f), Color.Transparent), center = Offset(cx, y), radius = span)
    val hair = 1.2.dp.toPx()
    onDrawBehind {
        scale(1f, 0.12f, pivot = Offset(cx, y)) { drawCircle(pool, span, Offset(cx, y)) }
        drawRect(line, Offset(cx - span, y - hair / 2f), Size(span * 2f, hair))
    }
}

/** A cover's glass: a sheen across its top corner, and [dim] (read while drawing) shading it back into the stage. */
private fun Modifier.coverGlass(dim: () -> Float): Modifier = drawWithCache {
    val sheen = Brush.linearGradient(
        0f to Color.White.copy(alpha = 0.2f), 0.38f to Color.Transparent, start = Offset.Zero, end = Offset(size.width, size.height),
    )
    onDrawBehind {
        drawRect(sheen)
        val d = dim()
        if (d > 0.01f) drawRect(Color.Black, alpha = d)
    }
}

/**
 * The spotlight's covers, one shape and on one floor (see [Fan]): the latest in the middle and biggest, the others
 * smaller, dimmer and turned away a little the further out they are. Each is a [FocusCard] laid out at its real spot
 * (so Left and Right walk them in order); the scale, turn, shade and entry are graphics-layer only. Down goes back
 * to the orbit.
 */
@Composable
private fun SpotFan(
    cards: List<FanCard>, cardW: Dp, cardH: Dp, entry: Animatable<Float, AnimationVector1D>, route: String, target: String?,
    focusedKey: String?, middle: FocusRequester, stage: FocusRequester, onFocusCard: (String) -> Unit,
) {
    val ctx = LocalContext.current
    val cardWpx = with(LocalDensity.current) { cardW.toPx() }
    // offset {} mirrors the covers' places in right to left; their turn and entry glide follow.
    val sign = startSign
    val n = cards.size
    cards.forEachIndexed { rank, card ->
        val p = Fan.place(rank, n, cardWpx)
        val key = "fan:${card.key}"
        val game = card.game
        val pkg = card.pkg
        val glow = game?.let { SystemColors.of(it.platform.id) } ?: Palette.Sky
        // A cover fades in once its picture has arrived: an empty card in the row (only its surface) is a grey slab.
        val box = remember(game?.key, game?.meta?.box) { game?.let { Libraries.of(it)?.box(it) } }
        val ready = remember(card.key, box) { mutableStateOf(box == null) }
        val shown = animateFloatAsState(if (ready.value) 1f else 0f, Motion.fade(180), label = "cover-shown")
        FocusCard(
            Modifier
                .offset { IntOffset(p.x.roundToInt(), 0) }
                .zIndex(if (key == focusedKey) 20f else 10f - abs(Fan.slot(rank)))
                .size(cardW, cardH)
                .graphicsLayer {
                    val e = entry.value
                    transformOrigin = TransformOrigin(0.5f, 1f)
                    val s = p.scale * (0.86f + 0.14f * e)
                    scaleX = s
                    scaleY = s
                    rotationY = p.turn * e * sign
                    cameraDistance = 16f * density
                    translationX = -p.x * (1f - e) * 0.7f * sign
                    // Faded as one picture (not ModulateAlpha): the card's surface must not show through the art.
                    alpha = e.coerceIn(0f, 1f) * shown.value
                }
                .floorShadow()
                .then(if (rank == 0) Modifier.focusRequester(middle) else Modifier)
                .focusProperties { down = stage }
                .takesFocus(route, key, key == target)
                .onFocusChanged { if (!it.isFocused && pkg != null && Panels.focusedApp == pkg) Panels.focusedApp = null },
            glow = glow, shape = RoundedCornerShape(12.dp), entry = true,
            onFocus = {
                onFocusCard(key)
                if (game != null) Selection.current = Selected.GameItem(game)
                else if (pkg != null) { Panels.focusedApp = pkg; Selection.current = Selected.App(pkg) }
            },
            onClick = { if (game != null) Launcher.launch(ctx, game) else if (pkg != null) Dock.open(ctx, pkg) },
        ) { focused ->
            if (game != null && box != null) CoverArt(game, box, cardW) { ready.value = true }
            else if (game != null) GameBoxArt(game, cardW)
            else if (pkg != null) Column(
                Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(lerp(Palette.Card, Palette.Sky, 0.2f), Palette.Card))).padding(8.dp),
                verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AppIcon(pkg, Modifier.size(cardW * 0.56f).clip(RoundedCornerShape(14.dp)))
                Spacer(Modifier.height(10.dp))
                Text(
                    remember(pkg) { RunningGames.appLabel(ctx, pkg) }, style = MaterialTheme.typography.labelLarge,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                )
            }
            val shade = animateFloatAsState(if (focused) 0f else p.dim, Motion.soft(), label = "cover-shade")
            Box(Modifier.fillMaxSize().coverGlass { shade.value })
        }
    }
}

/** A cover's picture from [box], decoded at its drawn size; [onReady] once it is in (or failed, so it never stays hidden). */
@Composable
private fun CoverArt(game: Game, box: java.io.File, size: Dp, onReady: () -> Unit) {
    AsyncImage(
        rememberSizedRequest(box, tilePx(size)), game.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
        onSuccess = { onReady() }, onError = { onReady() },
    )
}

/** The spotlight for an orb with nothing to fan out (one of Tandem's apps): its art big, and what it is. */
@Composable
private fun OrbNote(orb: Orb, entry: Animatable<Float, AnimationVector1D>, width: Dp, modifier: Modifier) {
    Column(
        modifier.graphicsLayer {
            alpha = entry.value.coerceIn(0f, 1f)
            translationY = (1f - entry.value) * 24f * density
            compositingStrategy = CompositingStrategy.ModulateAlpha
        },
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(96.dp).floorShadow().clip(RoundedCornerShape(28.dp))) { TileArtView(orb.item.art, 96.dp) }
        Spacer(Modifier.height(14.dp))
        val line = orb.item.blurb.ifEmpty { if (orb.item.kind == TileKind.AllApps) Tx.s(R.string.home_orbit_note_pin) else orb.item.type }
        Text(
            line, Modifier.widthIn(max = width).padding(horizontal = 16.dp),
            style = MaterialTheme.typography.titleMedium.copy(shadow = CaptionShadow),
            color = Palette.Text.copy(alpha = 0.8f), maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
        )
    }
}

/**
 * The game running now, else the last game played anywhere ([continueGame]), as a cover standing on the same floor
 * as the carousel, apart to its left, under a "Resume" (or "Continue") label and with a play badge. A picks it up
 * again, bringing a running game back where it is; Y closes a running one (press twice, App.kt), and a long press
 * opens the game menu, as on the icon grid's Resume page.
 */
@Composable
private fun ContinueCard(game: Game, x: Float, cardW: Dp, cardH: Dp, modifier: Modifier, onFocus: () -> Unit) {
    val ctx = LocalContext.current
    val running = runningOf(game) != null
    val w = cardW * Fan.CONTINUE
    val mint = Palette.Mint
    Column(Modifier.offset { IntOffset(x.roundToInt(), 0) }.width(w), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            GlyphIcon(Glyph.Play, Palette.readable(mint), Modifier.size(11.dp))
            Spacer(Modifier.width(5.dp))
            Text(
                Tx.s(if (running) R.string.home_orbit_resume else R.string.home_orbit_continue),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = 1.2.sp, shadow = CaptionShadow),
                color = Palette.readable(mint), maxLines = 1, softWrap = false,
            )
        }
        FocusCard(
            modifier.size(w, cardH * Fan.CONTINUE).floorShadow(), glow = mint, shape = RoundedCornerShape(12.dp), entry = true,
            onFocus = { onFocus(); Selection.current = Selected.GameItem(game) },
            onLongPress = { Nav.openMenu(game) },
            onClick = { Launcher.launch(ctx, game) },
        ) { focused ->
            // The card keeps the highlight while its game changes (the running one closed, another found running) and
            // onFocus only runs when the highlight arrives: say the new game, so the caption and Y act on it.
            LaunchedEffect(focused, game.key) { if (focused) Selection.current = Selected.GameItem(game) }
            GameBoxArt(game, w)
            Box(Modifier.fillMaxSize().coverGlass { 0f })
            Box(
                Modifier.align(Alignment.BottomEnd).padding(7.dp).size(26.dp).clip(CircleShape).background(mint),
                contentAlignment = Alignment.Center,
            ) { GlyphIcon(Glyph.Play, Palette.OnAccent, Modifier.padding(start = 2.dp).size(13.dp)) }
        }
    }
}

/**
 * The dock under the orbit: the pinned apps as small round icons on a faint glass tray, quieter than the orbit until
 * it holds the highlight. The highlighted icon shows its name above it. Up goes back to the orbit.
 */
@Composable
private fun OrbitDock(
    dock: List<String>, withAll: Boolean, route: String, target: String?, stage: FocusRequester?,
    requesters: HashMap<String, FocusRequester>, onFocusKey: (String) -> Unit, modifier: Modifier,
) {
    val ctx = LocalContext.current
    var lit by remember { mutableStateOf<String?>(null) }
    val calm = animateFloatAsState(if (lit != null) 1f else 0.72f, Motion.soft(), label = "orbit-dock")
    val tray = Palette.Card.copy(alpha = 0.42f)
    val edge = Palette.Text.copy(alpha = 0.1f)
    Row(
        modifier.focusRow()
            .graphicsLayer { alpha = calm.value }
            .drawWithCache {
                val radius = androidx.compose.ui.geometry.CornerRadius(size.height / 2f)
                val hair = Stroke(1.dp.toPx())
                onDrawBehind {
                    drawRoundRect(tray, cornerRadius = radius)
                    drawRoundRect(edge, cornerRadius = radius, style = hair)
                }
            }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        @Composable
        fun Icon(key: String, pkg: String, onClick: () -> Unit, content: @Composable () -> Unit) {
            Box {
                FocusCard(
                    Modifier.size(38.dp)
                        .focusRequester(requesters.getOrPut(key) { FocusRequester() })
                        .focusProperties { if (stage != null) up = stage }
                        .takesFocus(route, key, key == target)
                        .onFocusChanged {
                            if (!it.isFocused && Panels.focusedApp == pkg) Panels.focusedApp = null
                            if (!it.isFocused && lit == key) lit = null
                        },
                    glow = Palette.Sky, shape = CircleShape, entry = pkg != Dock.ALL_APPS,
                    onFocus = {
                        lit = key
                        onFocusKey(key)
                        Panels.focusedApp = pkg.takeIf { it != Dock.ALL_APPS }
                        Selection.current = Selected.App(pkg)
                    },
                    onClick = onClick,
                ) { content() }
                // The name floats above the icon without taking room in the row.
                if (lit == key) Box(Modifier.matchParentSize()) {
                    Text(
                        if (pkg == Dock.ALL_APPS) Tx.s(R.string.home_orbit_all_apps) else remember(pkg) { RunningGames.appLabel(ctx, pkg) },
                        Modifier.offset(y = (-36).dp).wrapContentSize(unbounded = true)
                            .clip(RoundedCornerShape(50)).background(Palette.CardHigh.copy(alpha = 0.94f)).padding(horizontal = 12.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelLarge, maxLines = 1, softWrap = false,
                    )
                }
            }
        }
        dock.forEach { pkg ->
            Icon("app:$pkg", pkg, { Dock.open(ctx, pkg) }) { AppIcon(pkg, Modifier.fillMaxSize().padding(4.dp).clip(CircleShape)) }
        }
        if (withAll) Icon("app:*", Dock.ALL_APPS, { Panels.openApps() }) {
            Box(Modifier.fillMaxSize().background(Palette.Sky.copy(alpha = 0.22f)), contentAlignment = Alignment.Center) {
                GlyphIcon(Glyph.Grid, Palette.Sky, Modifier.size(19.dp))
            }
        }
    }
}

/** Orbit's rows in Home & layout › Home screen (shown while Orbit is the layout). */
@Composable
fun orbitSettings(): SettingSection {
    val look = Store.settings.look
    return SettingSection(Tx.s(R.string.home_orbit_settings), en = "Orbit",
        rows = listOf(
            choice(
                Tx.s(R.string.home_orbit_holds), Glyph.Home, Palette.Lavender, Tx.s(R.string.home_orbit_holds_help),
                Orbit.Groups, look.orbitGroups, en = "Orbit holds",
            ) { v -> updateLook { it.copy(orbitGroups = v) } }.copy(keywords = "orbit orbs planets systems collections apps groups"),
            // Its English name stays "Continue ribbon" (an id: Settings places the row, focus memory and search by it),
            // though it is a card now.
            toggle(
                Tx.s(R.string.home_orbit_continue_card), Glyph.Play, Palette.Mint, Tx.s(R.string.home_orbit_continue_card_help),
                look.orbitContinue, en = "Continue ribbon",
                look = { on -> LayoutPreview("orbit", Modifier.fillMaxSize(), rounded = false, look = look.copy(orbitContinue = on)) },
            ) { v -> updateLook { it.copy(orbitContinue = v) } }.copy(keywords = "orbit continue ribbon card resume last played recent"),
        ),
    )
}
