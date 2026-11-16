package io.github.zahakj.tandem.ui

import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import coil.compose.AsyncImage
import io.github.zahakj.tandem.Button
import io.github.zahakj.tandem.Collections
import io.github.zahakj.tandem.Game
import io.github.zahakj.tandem.Launcher
import io.github.zahakj.tandem.Libraries
import io.github.zahakj.tandem.PlatformLibrary
import io.github.zahakj.tandem.RunningGames
import io.github.zahakj.tandem.Selected
import io.github.zahakj.tandem.Selection
import io.github.zahakj.tandem.data.GameMeta
import io.github.zahakj.tandem.data.HomeStore
import io.github.zahakj.tandem.data.Store
import io.github.zahakj.tandem.data.playStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.ceil

/*
 * Spotlight, a game-first home layout ("spotlight"; not the icon grid's other screen in Spotlight.kt). The
 * highlighted game's art fills the screen. A narrow rail of covers runs down one side with the highlight at its
 * middle, and the other side holds the game's logo, a line of facts, its story and how much you've played it. L1
 * and R1 step through the groups: each system with games, the collections, then the apps. X opens the whole story;
 * pushing away from the rail (or a tap on the art) hides everything but the art.
 *
 * The geometry lives in SpotlightHomeLayout.kt. The rail's scroll is one Animatable (the offset, in tiles) read
 * only in graphics layers, and only the tiles near it are composed, so a library of thousands costs a dozen tiles.
 * The art waits for the highlight to rest (Selection.follow, then a short settle), is decoded at the screen's size,
 * and fades into the theme's background under one mask, so a crossfade draws two pictures at most. The mask takes
 * most of the art away where the panel's words sit, so they read over any picture. A story longer than its clamp
 * scrolls itself slowly once the highlight rests (StoryDrift), moved in a layer, never recomposed per frame.
 */

object SpotlightMode {
    /** Spotlight is the home layout. */
    val enabled: Boolean get() = Store.settings.look.homeLayout == "spotlight"

    /** The focus route remembering the rail's group and game. */
    const val ROUTE = "home:spotlight"
    private const val ITEM = "home:spotlight:item"
    /** Focus keys on the home route: the rail, or the empty library's note. */
    const val STAGE = "spotlight"
    const val EMPTY = "spotlight:empty"

    val Rails = listOf("left" to "Left", "right" to "Right")
    val Backdrops = listOf("art" to "Art", "blur" to "Blurred cover", "dim" to "Dim")

    internal fun rememberSpot(group: String, item: String) {
        FocusMemory.remember(ROUTE, group)
        FocusMemory.remember(ITEM, item)
    }

    internal val rememberedGroup: String? get() = FocusMemory.get(ROUTE)
    internal val rememberedItem: String? get() = FocusMemory.get(ITEM)

    /** X belongs to the layout (the whole story) unless the user gave X to swapping or search everywhere. */
    val ownsX: Boolean get() = Store.settings.look.xButton.let { it != "swap" && it != "search" }
}

/** One tile on the rail: a game, or an app or one of Tandem's apps (a [GridItem] from the catalog). */
private class RailEntry(val key: String, val title: String, val game: Game? = null, val item: GridItem? = null) {
    /** The package of an Android app, for its Y menu and the side panel. */
    val pkg: String? get() = item?.pkg?.takeIf { item.kind == TileKind.App }

    val selected: Selected get() = game?.let { Selected.GameItem(it) } ?: pkg?.let { Selected.App(it) } ?: item!!.selected
}

/** A group the rail shows: a system, a collection, or the apps. */
private class RailGroup(val key: String, val title: String, val color: Color, val entries: List<RailEntry>)

private const val RECENT = "col:continue"

/** Each system with games, then the collections (Recently played first), then the apps: Tandem's own and the dock's. */
private fun buildGroups(catalog: GridCatalog, pinned: List<String>): List<RailGroup> {
    val out = ArrayList<RailGroup>()
    fun games(item: GridItem) = item.games.map { RailEntry(it.key, it.title, game = it) }
    for (key in catalog.defaults) {
        if (!key.startsWith("s:")) continue
        val item = catalog.item(key)?.takeIf { it.games.isNotEmpty() } ?: continue
        val short = item.platformId?.let { Libraries.byId(it)?.platform?.shortName } ?: item.title
        out += RailGroup(key, short, item.color, games(item))
    }
    for (key in listOf(RECENT) + catalog.defaults.filter { it.startsWith("col:") }) {
        val item = catalog.item(key)?.takeIf { it.games.isNotEmpty() } ?: continue
        out += RailGroup(key, item.title, item.color, games(item))
    }
    val appKeys = catalog.defaults.filter { !it.startsWith("s:") && !it.startsWith("col:") && it != "genres" } + "allapps" + pinned.map { "app:$it" }
    val seen = HashSet<Any>()
    val apps = appKeys.mapNotNull { k ->
        val item = catalog.item(k) ?: return@mapNotNull null
        // A Tandem app pinned to the dock is the same app as its own tile.
        if (!seen.add(item.builtIn ?: item.pkg ?: k)) return@mapNotNull null
        RailEntry(k, item.title, item = item)
    }
    if (apps.isNotEmpty()) out += RailGroup("apps", "Apps", Palette.Sky, apps)
    return out
}

/** The rail's live state: its group, its scroll, what the panel and the art show, and the clean and story modes. */
private class RailState(group: Int, index: Int, private val scope: CoroutineScope) {
    val offset: Animatable<Float, AnimationVector1D> = Animatable(index.toFloat())
    /** Where the rail is heading (the offset once the scroll settles). */
    var goal by mutableFloatStateOf(index.toFloat())
    var group by mutableIntStateOf(group)
    /** The entry the panel shows: the highlighted one once the highlight rests (see [Selection.follow]). */
    var spot by mutableStateOf<String?>(null)
    var focused by mutableStateOf(false)
    /** Only the art: the rail and the panel faded out. */
    var clean by mutableStateOf(false)
    /** The whole story, scrolling, instead of its first lines. */
    var expanded by mutableStateOf(false)
    val story = ScrollState(0)
    /** Counts the buttons that move anything: each one stops the story's own scroll and starts its wait again. */
    var nudges by mutableIntStateOf(0)
    /** The rail's entry: 0 to 1 each time the group changes. */
    val railIn = Animatable(1f)
    // Set on every composition, read by the key, touch and button handlers.
    var groups: List<RailGroup> = emptyList()
    var rail: SpotlightRail? = null

    val currentGroup: RailGroup? get() = groups.getOrNull(group)
    val entries: List<RailEntry> get() = currentGroup?.entries.orEmpty()
    val count: Int get() = entries.size
    val index: Int get() = SpotlightRail.index(goal, count)
    val current: RailEntry? get() = entries.getOrNull(index)
    val spotEntry: RailEntry? get() = spot?.let { k -> entries.firstOrNull { it.key == k } } ?: current
    private val still: Boolean get() = Motion.style == AnimationStyle.Off

    /** Tells the rest of Tandem what is highlighted: the other screen, Y's menu, the side panel. */
    fun select(e: RailEntry) {
        Panels.focusedApp = e.pkg
        Selection.current = e.selected
    }

    /** A button moved something: the story's own scroll goes back to the top and waits for the highlight to rest. */
    fun nudge() { nudges++ }

    /** The highlight reached [current]: remember it, and once it rests the panel, the art and the other screen follow. */
    private fun arrived() {
        nudge()
        val g = currentGroup ?: return
        val e = current ?: return
        SpotlightMode.rememberSpot(g.key, e.key)
        Selection.follow {
            if (spot != e.key) { expanded = false; scope.launch { story.scrollTo(0) } }
            spot = e.key
            if (focused) select(e)
        }
    }

    /** Scrolls the rail so it settles at [to]; [velocity] (tiles a second) carries a finger's flick into the spring. */
    fun goTo(to: Float, velocity: Float = 0f) {
        val before = index
        goal = to
        if (index != before) Sounds.move()
        arrived()
        scope.launch { if (still) offset.snapTo(to) else offset.animateTo(to, Motion.move(), initialVelocity = velocity) }
    }

    fun step(by: Int) = goTo(SpotlightRail.stepped(goal, by, count))

    fun show(i: Int) { if (i in 0 until count) goTo(i.toFloat()) }

    /** Group [g], from its top. */
    fun showGroup(g: Int) {
        if (g !in groups.indices) return
        if (g != group) Sounds.move()
        group = g
        goal = 0f
        scope.launch {
            offset.snapTo(0f)
            if (still) railIn.snapTo(1f) else { railIn.snapTo(0f); railIn.animateTo(1f, Motion.move()) }
        }
        arrived()
    }

    fun dragBy(by: Float) { scope.launch { offset.snapTo(SpotlightRail.dragged(offset.value, by, count)) } }

    fun stop() { scope.launch { offset.stop() } }

    fun fling(velocity: Float) = goTo(SpotlightRail.settle(offset.value, velocity, count, still), if (still) 0f else velocity)

    fun open(ctx: android.content.Context) {
        val e = current ?: return
        if (e.game != null) Launcher.launch(ctx, e.game) else e.item?.open?.invoke()
    }

    /** X: the whole story, or back to its first lines. Only a game with one has a story to open. */
    fun toggleStory() {
        val e = spotEntry ?: return
        if (!expanded && e.game?.meta?.description.isNullOrBlank()) return
        nudge()
        expanded = !expanded
        if (!expanded) scope.launch { story.scrollTo(0) }
        Sounds.move()
    }

    /** Up / Down with the story open scroll it instead of the rail. */
    fun scrollStory(by: Float) { scope.launch { story.animateScrollBy(by) } }
}

/** The Spotlight home. Composed by [HomeLayout]. */
@Composable
fun SpotlightHome(libs: List<PlatformLibrary>) {
    IconPacksDriver()
    val ctx = LocalContext.current
    val look = Store.settings.look
    val route = Route.Home.key
    val catalog = rememberCatalog(libs)
    // Package changes, not every return: each check asks the package manager about every pinned app.
    val packages = io.github.zahakj.tandem.Emulators.changes
    val pinned = remember(HomeStore.settings.dock, packages) { Dock.pinned(ctx) }
    PrefetchAppLooks(pinned)
    val groups = remember(catalog, pinned) { buildGroups(catalog, pinned) }
    val noGames = Collections.visibleGames().isEmpty()
    // B comes back here from the top of any other group: Recently played (the last game first), else the first system.
    val home = groups.indexOfFirst { it.key == RECENT }.coerceAtLeast(0)

    val scope = rememberCoroutineScope()
    val st = remember {
        val g = groups.indexOfFirst { it.key == SpotlightMode.rememberedGroup }.takeIf { it >= 0 } ?: home
        val i = groups.getOrNull(g)?.entries?.indexOfFirst { it.key == SpotlightMode.rememberedItem }?.coerceAtLeast(0) ?: 0
        RailState(g, i, scope).also { it.groups = groups; it.spot = it.current?.key }
    }
    st.groups = groups
    // The groups changed (a rescan, a play session's new times, a hidden system): keep the same group and game where
    // they still are, without a scroll.
    LaunchedEffect(groups) {
        val g = groups.indexOfFirst { it.key == SpotlightMode.rememberedGroup }.takeIf { it >= 0 } ?: st.group.coerceIn(0, (groups.size - 1).coerceAtLeast(0))
        st.group = g
        val i = st.entries.indexOfFirst { it.key == SpotlightMode.rememberedItem }.takeIf { it >= 0 } ?: st.index
        if (st.goal != i.toFloat() || st.offset.value != i.toFloat() && !st.offset.isRunning) {
            st.goal = i.toFloat()
            st.offset.snapTo(i.toFloat())
        }
        if (st.entries.none { it.key == st.spot }) st.spot = st.current?.key
    }

    val keys = if (noGames || groups.isEmpty()) listOf(SpotlightMode.EMPTY) else listOf(SpotlightMode.STAGE)
    // Coming from another layout, the home's remembered item (a grid tile, a shelf card) means nothing here.
    remember(keys) {
        if (FocusMemory.get(route).let { it == null || it !in keys }) FocusMemory.remember(route, keys.first())
        0
    }
    val target = rememberFocusTarget(route, keys)
    val stageReq = remember { FocusRequester() }

    fun focusStage() {
        if (groups.isEmpty()) return
        FocusMemory.remember(route, SpotlightMode.STAGE)
        if (runCatching { stageReq.requestFocus() }.isFailure) Nav.focusTick++
    }

    // L1 / R1: the group before or after, from its top. X: the whole story (or out of clean mode).
    InterceptButtons(true) { b ->
        val free = Nav.route == Route.Home && !Nav.overlayOpen && Layers.top == null && FolderPopup.route == null
        if (!free || st.groups.isEmpty() || noGames) return@InterceptButtons false
        when (b) {
            Button.L1, Button.R1 -> {
                st.clean = false
                st.showGroup(SpotlightSteps.cycle(st.group, if (b == Button.R1) 1 else -1, st.groups.size))
                if (!st.focused) focusStage()
                true
            }
            Button.X -> {
                if (!SpotlightMode.ownsX) return@InterceptButtons false
                if (st.clean) st.clean = false else st.toggleStory()
                true
            }
            else -> false
        }
    }
    // B, one thing at a time: out of clean mode, the story closed, the rail's top, then the home group. Home never
    // leaves Tandem, as on the other layouts.
    BackHandler(enabled = Nav.route == Route.Home && !Nav.overlayOpen && FolderPopup.route == null && groups.isNotEmpty() && !noGames) {
        st.nudge()
        when (SpotlightSteps.back(st.clean, st.expanded, st.index, st.group, home)) {
            SpotlightBack.Clean -> st.clean = false
            SpotlightBack.Description -> st.toggleStory()
            SpotlightBack.Top -> st.show(0)
            SpotlightBack.Home -> st.showGroup(home)
            SpotlightBack.Nothing -> if (!st.focused) focusStage()
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = maxWidth
        val h = maxHeight
        val density = LocalDensity.current
        val railLeft = look.spotlightRail != "right"
        val frame = remember(w, h, railLeft) { SpotlightFrame(w.value, h.value, railLeft, if (w >= 600.dp) 28f else 16f) }
        val chrome = animateFloatAsState(if (st.clean) 0f else 1f, Motion.fade(260), label = "spotlight-chrome")

        SpotBackdrop(st, frame, chrome, look.spotlightBackdrop, look.spotlightMotion)
        // A tap on the art: only the art (a catcher on top brings everything back, below).
        Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { st.clean = true } })

        if (noGames || groups.isEmpty()) {
            val hidden = libs.any { it.games.isNotEmpty() }
            val pick = rememberGameFolderPicker()
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyNote(
                    if (hidden) "Every game is hidden" else "No games yet. Pick the folder your games are in",
                    if (hidden) "Show hidden games" else "Pick your games folder",
                    Modifier.takesFocus(route, SpotlightMode.EMPTY, target == SpotlightMode.EMPTY),
                ) { if (hidden) Nav.go(Route.SettingsPage("library")) else pick() }
            }
            return@BoxWithConstraints
        }

        val headerH = 46.dp
        val viewH = h - headerH - 10.dp
        val railW = frame.railW.dp
        // Room either side for the bigger highlighted tile and its ring.
        val boxW = railW - 40.dp
        val boxH = min(boxW, viewH * 0.19f)
        val labelH = 20.dp
        val tileH = boxH + 6.dp + labelH
        val pitch = tileH + 14.dp
        val rail = with(density) {
            remember(viewH, pitch, tileH) { SpotlightRail(viewH.toPx(), pitch.toPx(), (tileH * (SpotlightRail.FOCUS - 1f) / 2f + 4.dp).toPx(), tileH.toPx()) }
        }
        st.rail = rail
        val ring = animateFloatAsState(if (st.focused && !st.clean) 1f else 0f, Motion.soft(), label = "spotlight-ring")

        Column(
            Modifier.offset(x = frame.railX.dp).width(railW).fillMaxHeight()
                .graphicsLayer { alpha = chrome.value }
                .focusRequester(stageReq)
                .takesFocus(route, SpotlightMode.STAGE, target == SpotlightMode.STAGE)
                .railKeys(st, railLeft, ctx)
                .focusable(interactionSource = remember { MutableInteractionSource() }),
        ) {
            RailHeader(st, Modifier.fillMaxWidth().height(headerH).padding(horizontal = 14.dp))
            RailView(st, rail, boxW, boxH, tileH, ring, Modifier.fillMaxWidth().height(viewH))
        }

        Box(
            Modifier.offset(x = frame.panelX.dp).width(frame.panelW.dp).fillMaxHeight().padding(vertical = 14.dp)
                .graphicsLayer { alpha = chrome.value },
            contentAlignment = Alignment.CenterStart,
        ) {
            SpotPanel(st, h - 28.dp)
        }
        // Clean mode: the rail and the panel are faded out but still there; any tap brings them back instead.
        if (st.clean) Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { st.clean = false } })
    }
}

/**
 * The rail's buttons: Up and Down move it (faster the longer they are held), or scroll the story while it is open;
 * pushing away from the rail leaves only the art; A plays or opens. In clean mode any of them brings everything back.
 */
@Composable
private fun Modifier.railKeys(st: RailState, railLeft: Boolean, ctx: android.content.Context): Modifier {
    val press = remember { TilePress() }
    val storyStep = with(LocalDensity.current) { 120.dp.toPx() }
    return onFocusChanged {
        val was = st.focused
        st.focused = it.isFocused
        if (it.isFocused && !was) st.current?.let { e -> st.select(e) }
        else if (!it.isFocused) { press.pressed = false; if (Panels.focusedApp == st.current?.pkg) Panels.focusedApp = null }
    }.onKeyEvent { e ->
        val down = e.type == KeyEventType.KeyDown
        val nav = e.key == Key.DirectionUp || e.key == Key.DirectionDown || e.key == Key.DirectionLeft || e.key == Key.DirectionRight
        if (st.clean && (nav || e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.ButtonA)) {
            if (down && e.nativeKeyEvent.repeatCount == 0) st.clean = false
            return@onKeyEvent true
        }
        when (e.key) {
            Key.DirectionUp, Key.DirectionDown -> {
                if (down) {
                    val dir = if (e.key == Key.DirectionUp) -1 else 1
                    // Up from the first cover goes to the top bar (Stats, Quick), which nothing else leads to here.
                    if (dir < 0 && !st.expanded && st.index == 0 && e.nativeKeyEvent.repeatCount == 0 && TopBarFocus.enter()) return@onKeyEvent true
                    if (st.expanded) st.scrollStory(dir * storyStep)
                    else st.step(dir * SpotlightRail.holdStep(e.nativeKeyEvent.eventTime - e.nativeKeyEvent.downTime))
                }
                true
            }
            Key.DirectionLeft, Key.DirectionRight -> {
                val away = (e.key == Key.DirectionRight) == railLeft
                if (down && away && e.nativeKeyEvent.repeatCount == 0) { st.clean = true; Sounds.move() }
                true
            }
            else -> tileKey(e, press, { st.open(ctx) }, null)
        }
    }
}

/** The group's name over the rail, on a dot of its colour, and where in it the highlight is. */
@Composable
private fun RailHeader(st: RailState, modifier: Modifier) {
    val g = st.currentGroup ?: return
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(g.color))
        Spacer(Modifier.width(8.dp))
        Text(
            g.title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall.overArt(),
            color = Palette.Text, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(6.dp))
        Text("${st.index + 1}/${st.count}", style = MaterialTheme.typography.labelSmall, color = Palette.Muted, maxLines = 1)
    }
}

/**
 * The tiles near the highlight, each placed in its own graphics layer from the scroll offset, so a scroll moves
 * layers and composes only the tiles coming into view. Takes taps and drags for the whole rail.
 */
@Composable
private fun RailView(st: RailState, rail: SpotlightRail, boxW: Dp, boxH: Dp, tileH: Dp, ring: State<Float>, modifier: Modifier) {
    val ctx = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val group = st.group
    val entries = st.entries
    val range by remember(rail, entries) { derivedStateOf { rail.visible(st.offset.value, entries.size) } }
    // Each cover's place on screen, so a Y menu lifts the highlighted cover from where it is, in its own shape.
    val covers = remember(entries) { HashMap<Int, Pair<LayoutCoordinates, Float>>() }
    LaunchedEffect(st.index, st.focused, entries) { covers[st.index]?.let { (c, round) -> ActionMenu.focusAt(c, round) } }
    // Every cover's shape ahead of the scroll, so a tile is never composed in the wrong box.
    LaunchedEffect(entries) { CoverShapes.warm(ctx, entries.mapNotNull { it.game }) }
    val g = st.currentGroup
    val glow = Palette.highlightFor(g?.color ?: Palette.Mint)
    val theme = Themes.live
    val ringColor = if (contrast(glow, theme.background) < 2.2f) lerp(glow, theme.text, 0.65f) else glow
    Box(
        modifier.clipToBounds()
            .graphicsLayer { alpha = st.railIn.value; translationY = (1f - st.railIn.value) * 18f * density }
            .pointerInput(rail, group) {
                detectTapGestures(
                    onTap = { pos -> tapRail(st, pos.y, ctx, scope) },
                    onLongPress = { pos ->
                        val i = rail.hit(pos.y, st.offset.value, st.count)
                        if (i >= 0 && !st.clean) {
                            Touch.buzz(haptic)
                            st.show(i)
                            st.current?.let { st.select(it) }
                            scope.launch { Touch.holdForY({ st.focused }) { st.focusFromTouch(scope) } }
                        }
                    },
                )
            }
            .pointerInput(rail, group) {
                val tracker = VelocityTracker()
                detectVerticalDragGestures(
                    onDragStart = { tracker.resetTracking(); st.stop() },
                    onDragEnd = { st.fling(-tracker.calculateVelocity().y / rail.pitch); if (!st.focused) st.focusFromTouch(scope) },
                    onDragCancel = { st.fling(0f) },
                ) { change, dy ->
                    change.consume()
                    tracker.addPosition(change.uptimeMillis, change.position)
                    st.dragBy(-dy / rail.pitch)
                }
            },
    ) {
        for (i in range) {
            val e = entries.getOrNull(i) ?: continue
            key(e.key) { RailTile(e, i, st, rail, boxW, boxH, tileH, ring, ringColor, covers) }
        }
    }
}

/** The rail takes the highlight after a touch moved it, so the D-pad carries on from there. */
private fun RailState.focusFromTouch(scope: CoroutineScope) {
    scope.launch { FocusMemory.remember(Route.Home.key, SpotlightMode.STAGE); Nav.focusTick++ }
}

/** A tap: the highlighted tile opens when the rail has the highlight; any other comes to the middle first. */
private fun tapRail(st: RailState, y: Float, ctx: android.content.Context, scope: CoroutineScope) {
    if (st.clean) { st.clean = false; return }
    val rail = st.rail ?: return
    val i = rail.hit(y, st.offset.value, st.count)
    if (i < 0) return
    if (i == st.index && st.focused && !st.offset.isRunning) { st.open(ctx); Sounds.select(); return }
    st.show(i)
    if (!st.focused) st.focusFromTouch(scope)
}

/** One tile: the cover at its own shape (an app's icon square), the ring when highlighted, the name under it. */
@Composable
private fun RailTile(
    e: RailEntry, i: Int, st: RailState, rail: SpotlightRail, boxW: Dp, boxH: Dp, tileH: Dp, ring: State<Float>, ringColor: Color,
    covers: HashMap<Int, Pair<LayoutCoordinates, Float>>,
) {
    val ctx = LocalContext.current
    val density = LocalDensity.current
    val tileHpx = with(density) { tileH.toPx() }
    val game = e.game
    val aspect = game?.let { CoverShapes.of(ctx, it) }
    val (cw, ch) = if (game != null) SpotlightSteps.coverSize(aspect, boxW.value, boxH.value) else min(boxW, boxH).value.let { it to it }
    val corner = if (game != null) 8.dp else (ch * 0.22f).dp
    val ringW = with(density) { Themes.live.highlightThickness.coerceIn(2f, 5f).dp.toPx() }
    val running = game != null && RunningGames.list.any { it.game?.key == game.key }
    Column(
        Modifier.fillMaxWidth().height(tileH).graphicsLayer {
            val d = i - st.offset.value
            translationY = rail.y(d) - tileHpx / 2f
            alpha = rail.alpha(d)
            // Applied to each draw, not through an offscreen buffer: a buffer is the tile's own size and would cut
            // the cover where it has grown past the tile, which showed as a flat top while the highlight moved.
            compositingStrategy = CompositingStrategy.ModulateAlpha
        },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Only the cover grows at the highlight, up from its bottom edge; the title keeps the rail's width.
        Box(
            Modifier.fillMaxWidth().height(boxH).graphicsLayer {
                val s = rail.scale(i - st.offset.value)
                scaleX = s
                scaleY = s
                transformOrigin = TransformOrigin(0.5f, 1f)
            },
            contentAlignment = Alignment.BottomCenter,
        ) {
            Box(
                Modifier.size(cw.dp, ch.dp)
                    .onGloballyPositioned { c ->
                        covers[i] = c to corner.value / minOf(cw, ch)
                        if (i == st.index && st.focused) ActionMenu.focusAt(c, corner.value / minOf(cw, ch))
                    }
                    .drawWithCache {
                        val o = ringW * 1.6f
                        val topLeft = Offset(-o, -o)
                        val ringSize = Size(size.width + o * 2f, size.height + o * 2f)
                        val radius = CornerRadius(corner.toPx() + o)
                        val stroke = Stroke(ringW)
                        onDrawWithContent {
                            drawContent()
                            val f = ring.value * (1f - abs(i - st.offset.value)).coerceIn(0f, 1f)
                            if (f > 0.01f) drawRoundRect(ringColor, topLeft, ringSize, radius, alpha = f, style = stroke)
                        }
                    }
                    .clip(RoundedCornerShape(corner)),
            ) {
                // Decoded for the highlighted size, so the tile stays sharp when it grows.
                val decode = (maxOf(cw, ch) * SpotlightRail.FOCUS).dp
                // Until the cover's shape is known it is fitted, never cropped, so no cartridge loses its top for a frame.
                if (game != null) GameBoxArt(game, decode, if (aspect == null) ContentScale.Fit else ContentScale.Crop)
                else e.item?.let { TileArtView(it.art, decode) }
                if (running) Box(Modifier.align(Alignment.TopEnd).padding(5.dp).size(9.dp).clip(CircleShape).background(Palette.Mint))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            e.title, Modifier.padding(horizontal = 6.dp), style = MaterialTheme.typography.labelMedium.overArt(),
            color = Palette.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
        )
    }
}

/**
 * Everything behind the rail and the panel, reaching up under the top bar: the highlighted game's key art (else a
 * screenshot, else its cover blurred), or an app's icon blurred, fading into the theme's background towards the
 * rail and the bottom so the words on it read on dark and light themes alike. It changes only once the highlight
 * has rested a moment, cross-fading.
 */
@Composable
private fun SpotBackdrop(st: RailState, frame: SpotlightFrame, chrome: State<Float>, mode: String, motion: Boolean) {
    val slot = LocalScreenSlot.current
    WallpaperCover.Cover(slot, true)
    val wanted = st.spotEntry
    var shown by remember { mutableStateOf<RailEntry?>(null) }
    val first = remember { booleanArrayOf(true) }
    LaunchedEffect(wanted?.key, mode) {
        delay(SpotlightSteps.artDelay(first[0]))
        first[0] = false
        shown = wanted
    }
    val night = Palette.Night
    Box(
        Modifier.fillMaxSize()
            .layout { m, c ->
                // The art goes on under the top bar, which lies over the screen.
                val extra = TopBarHeight.roundToPx()
                val p = m.measure(Constraints.fixed(c.maxWidth, c.maxHeight + extra))
                layout(c.maxWidth, c.maxHeight) { p.place(0, -extra) }
            }
            .background(night),
    ) {
        if (mode != "dim") Crossfade(
            shown, Modifier.fillMaxSize().spotlightFade(frame, chrome), animationSpec = Motion.fade(420), label = "spotlight-art",
        ) { e -> if (e != null) BackdropArt(e, mode, motion) }
    }
}

/** One picture behind everything, decoded no bigger than the screen. */
@Composable
private fun BackdropArt(e: RailEntry, mode: String, motion: Boolean) {
    Box(Modifier.fillMaxSize()) {
        val game = e.game
        if (game != null) {
            SpotlightArt(game, mode, motion)
        } else e.item?.let { item ->
            Box(Modifier.fillMaxSize().background(lerp(item.color, Palette.Night, 0.55f)))
            Box(Modifier.fillMaxSize().blur(48.dp, BlurredEdgeTreatment.Rectangle).graphicsLayer { alpha = 0.6f }) {
                TileArtView(item.art, 96.dp)
            }
        }
    }
}

/** How much of the art the panel's words take away where they sit: the background shows through 80 to 90 %. */
private const val WORDS_FADE = 0.8f

/**
 * A game's picture filling its space, decoded no bigger than that: its key art, else a screenshot ([mode] "art"),
 * else (or with [mode] "blur") its cover blurred over the system's colour. Shared with the other screen.
 */
@Composable
internal fun SpotlightArt(game: Game, mode: String, motion: Boolean) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val px = maxOf(constraints.maxWidth, constraints.maxHeight)
        val lib = Libraries.of(game)
        val art = if (mode == "art") remember(game.key, game.meta.hero, game.meta.screenshot) { lib?.hero(game) ?: lib?.screenshot(game) } else null
        if (art != null) {
            KenBurns(rememberSizedRequest(art, px), Modifier.fillMaxSize(), moving = motion)
        } else {
            val box = remember(game.key, game.meta.box) { lib?.box(game) }
            val c = SystemColors.of(game.platform.id)
            Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(c.copy(alpha = 0.5f), c.copy(alpha = 0.12f)))))
            // A small decode, blurred: the cover's colours without its detail. Before Android 12 the blur is
            // left out and the small picture stretched soft does the work alone.
            if (box != null) AsyncImage(
                rememberSizedRequest(box, 160), null,
                Modifier.fillMaxSize().blur(36.dp, BlurredEdgeTreatment.Rectangle).graphicsLayer { alpha = 0.75f },
                contentScale = ContentScale.Crop,
            )
        }
    }
}

/**
 * The art's fade into the background: towards the rail (most), the bottom, and, much more, the block where the
 * panel's words sit (from just above the chips down to the bottom, the panel's width and a short ease towards the
 * centre), so the story reads over any art while the art keeps its colour everywhere else. All in one offscreen
 * layer. In clean mode ([chrome] 0) the fade lifts and the art shows whole. The masks are drawn as their inverse
 * (DstOut) at the chrome's alpha, so nothing is built while it animates.
 */
private fun Modifier.spotlightFade(frame: SpotlightFrame, chrome: State<Float>): Modifier =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }.drawWithCache {
        val rail = frame.railShare.coerceIn(0.05f, 0.9f)
        val panel = frame.panelNear.coerceIn(rail, 0.95f)
        // How much of the art shows across the screen, with the rail on the left; mirrored for a right rail.
        val stops = if (frame.wide) listOf(
            0f to 0.22f, rail to 0.42f, rail + (panel - rail) * 0.4f to 1f, panel - 0.02f to 0.9f, (panel + 0.12f).coerceAtMost(0.99f) to 0.5f, 1f to 0.42f,
        ) else listOf(0f to 0.22f, rail to 0.4f, (rail + 0.12f).coerceAtMost(0.99f) to 0.42f, 1f to 0.5f)
        val across = (if (frame.railLeft) stops else stops.map { (x, a) -> 1f - x to a }.reversed())
            .map { (x, a) -> x to Color.Black.copy(alpha = 1f - a) }.toTypedArray()
        val horizontal = Brush.horizontalGradient(*across)
        val vertical = Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.25f), 0.14f to Color.Transparent, 0.62f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.6f))
        val words = wordsMask(frame)
        onDrawWithContent {
            drawContent()
            val c = chrome.value
            if (c > 0.001f) {
                drawRect(horizontal, alpha = c, blendMode = BlendMode.DstOut)
                drawRect(vertical, alpha = c, blendMode = BlendMode.DstOut)
                drawRect(words, alpha = c * WORDS_FADE, blendMode = BlendMode.DstOut)
            }
        }
    }

/**
 * The block under the panel's words as one mask: across, nothing until a little before the panel's near edge and
 * all of it from there to the screen's edge; down, nothing over the top of the logo and all of it from where the
 * chips start. The two ramps are multiplied (DST_IN), so the block's corners ease out both ways.
 */
private fun CacheDrawScope.wordsMask(frame: SpotlightFrame): Brush {
    val k = size.width / frame.width
    // The art reaches up under the top bar: the frame's top is this far down the layer.
    val top = size.height - frame.height * k
    val near = (if (frame.railLeft) frame.panelX else frame.panelX + frame.panelW) * k
    val ease = frame.width * 0.07f * k * if (frame.railLeft) -1f else 1f
    val across = LinearGradientShader(
        Offset(near + ease, 0f), Offset(near, 0f),
        listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f), Color.Black), listOf(0f, 0.6f, 1f),
    )
    val down = LinearGradientShader(
        Offset(0f, top + frame.height * 0.2f * k), Offset(0f, top + frame.height * 0.36f * k),
        listOf(Color.Transparent, Color.Black),
    )
    return ShaderBrush(android.graphics.ComposeShader(across, down, android.graphics.PorterDuff.Mode.DST_IN))
}

/**
 * Text over the art: a soft dark shadow under light words on dark themes, a soft halo of the background around dark
 * words on light ones, so either reads where a bright or busy patch of art comes through the fade.
 */
@Composable
internal fun TextStyle.overArt(): TextStyle =
    if (Themes.live.dark) onArt() else copy(shadow = Shadow(Palette.Night.copy(alpha = 0.9f), Offset.Zero, 10f))

/** The panel: the game's or app's own, cross-fading when the highlight rests on another. */
@Composable
private fun SpotPanel(st: RailState, maxH: Dp) {
    val entry = st.spotEntry ?: return
    AnimatedContent(
        entry, Modifier.fillMaxWidth(), contentKey = { it.key },
        transitionSpec = { fadeIn(Motion.fade(200)) togetherWith fadeOut(Motion.fade(120)) },
        contentAlignment = Alignment.CenterStart, label = "spotlight-panel",
    ) { e ->
        if (e.game != null) GamePanel(st, e.game, maxH) else e.item?.let { AppPanel(it) }
    }
}

private val Spaces = Regex("""\s+""")

/**
 * A game's panel: its logo (or its name), a line of facts, its story clamped to a few lines (X opens it all,
 * scrolling), what you've played of it, and the buttons. Tapping the words opens or closes the story too.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GamePanel(st: RailState, game: Game, maxH: Dp) {
    val meta = game.meta
    val running = RunningGames.list.any { it.game?.key == game.key }
    val logo = remember(game.key, Store.meta) { Libraries.of(game)?.logo(game) }
    val story = remember(meta.description) { meta.description?.replace(Spaces, " ")?.trim()?.takeIf { it.isNotEmpty() } }
    val expanded = st.expanded
    BoxWithConstraints(Modifier.fillMaxWidth().heightIn(max = maxH)) {
        val width = maxWidth
        val logoH = if (expanded) 64.dp else (maxH * 0.24f).coerceIn(56.dp, 140.dp)
        // What the story may take: the panel less the logo and about 150 dp of facts, stats and buttons.
        val storyH = (maxH - logoH - 150.dp).coerceAtLeast(60.dp)
        val lines = if (maxH < 380.dp) 4 else 6
        Column(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().pointerInput(game.key) { detectTapGestures { st.toggleStory() } }) {
                if (logo != null) {
                    AnimatedLogo(game, maxWidth = width * 0.85f, maxHeight = logoH, sway = false)
                } else {
                    Text(
                        game.title, style = (if (expanded) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.displaySmall).overArt(),
                        color = Palette.Text, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(10.dp))
                FactChips(game, running)
                if (story != null) {
                    Spacer(Modifier.height(12.dp))
                    val body = MaterialTheme.typography.bodyLarge.overArt()
                    if (expanded) {
                        Box(Modifier.fillMaxWidth().heightIn(max = storyH).fadingEdges(st.story).verticalScroll(st.story)) {
                            Text(story, style = body, color = Palette.Text)
                        }
                    } else {
                        ClampedStory(st, story, body, lines)
                    }
                }
            }
            if (!expanded) {
                Spacer(Modifier.height(14.dp))
                PlayStats(game)
            }
            Spacer(Modifier.height(14.dp))
            PanelHints(if (running) "Resume" else "Play", menu = true, story = story != null, expanded = expanded)
        }
    }
}

/**
 * The clamped story's measures and scroll: where its [lines]-th line ends (written while the text is laid out, read
 * by the box's own layout, never observed), what that hides ([overflow], px) and how far it has scrolled ([drift], px).
 */
private class StoryClamp {
    var height = 0
    var hidden = 0f
    var overflow by mutableFloatStateOf(0f)
    var drift by mutableFloatStateOf(0f)
}

/**
 * The story's first [lines] lines, and the rest under a fade. When the highlight rests on it and there is more, it
 * scrolls itself slowly through to the end and back ([StoryDrift]), until a button moves something ([RailState.nudge]).
 * The whole text is laid out once and moved in its own layer, so a frame of the scroll redraws only this box.
 */
@Composable
private fun ClampedStory(st: RailState, story: String, style: TextStyle, lines: Int) {
    val clamp = remember(story, lines) { StoryClamp() }
    val speed = with(LocalDensity.current) { StoryDrift.SPEED_DP.dp.toPx() }
    val overflow = clamp.overflow
    LaunchedEffect(clamp, overflow, st.nudges, st.clean, Themes.still) {
        clamp.drift = 0f
        if (overflow < 1f || st.clean || Themes.still) return@LaunchedEffect
        val start = withFrameMillis { it }
        while (true) {
            val now = withFrameMillis { it } - start
            clamp.drift = StoryDrift.position(now, overflow, speed)
            val rest = StoryDrift.stillFor(now, overflow, speed)
            if (rest > 0L) delay(rest)
        }
    }
    Box(
        Modifier.fillMaxWidth().storyFade(clamp).clipToBounds().layout { m, c ->
            val p = m.measure(c.copy(minHeight = 0, maxHeight = Constraints.Infinity))
            val h = clamp.height.takeIf { it > 0 }?.coerceAtMost(p.height) ?: p.height
            val hidden = (p.height - h).toFloat()
            if (clamp.hidden != hidden) { clamp.hidden = hidden; clamp.overflow = hidden }
            layout(p.width, h) { p.place(0, 0) }
        },
    ) {
        Text(
            story, Modifier.fillMaxWidth().graphicsLayer { translationY = -clamp.drift },
            style = style, color = Palette.Text,
            onTextLayout = { t -> clamp.height = if (t.lineCount > lines) ceil(t.getLineBottom(lines - 1)).toInt() else t.size.height },
        )
    }
}

/** The story box's edges fade where there is more of it above or below: the top once it has scrolled, the bottom until its end. */
private fun Modifier.storyFade(clamp: StoryClamp): Modifier =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }.drawWithCache {
        val top = Brush.verticalGradient(0f to Color.Black, 0.18f to Color.Transparent)
        val bottom = Brush.verticalGradient(0.7f to Color.Transparent, 1f to Color.Black)
        val reach = size.height * 0.18f
        onDrawWithContent {
            drawContent()
            val over = clamp.overflow
            if (over < 1f) return@onDrawWithContent
            val d = clamp.drift
            val a = (d / reach).coerceIn(0f, 1f)
            val b = ((over - d) / reach).coerceIn(0f, 1f)
            if (a > 0.001f) drawRect(top, alpha = a, blendMode = BlendMode.DstOut)
            if (b > 0.001f) drawRect(bottom, alpha = b, blendMode = BlendMode.DstOut)
        }
    }

/** System, year, developer, genres, players: whatever is known, each on a small chip. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FactChips(game: Game, running: Boolean) {
    val meta = game.meta
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        @Composable
        fun chip(text: String, color: Color) = Pill(text, Palette.readable(color), Modifier.darkChip())
        if (running) chip("● Running", Palette.Mint)
        chip(game.platform.shortName, SystemColors.of(game.platform.id))
        meta.year?.let { chip("$it", Palette.Butter) }
        meta.developer?.takeIf { it.isNotBlank() }?.let { chip(it.trim(), Palette.Peach) }
        meta.genres.take(2).forEach { chip(Collections.tidyGenre(it), Palette.Lavender) }
        playersLabel(meta)?.let { chip(it, Palette.Sky) }
        if (meta.favorite) chip("♥", Palette.Peach)
    }
}

private fun playersLabel(m: GameMeta): String? {
    val p = m.players?.trim()?.replace('-', '–')?.takeIf { it.isNotEmpty() } ?: return null
    return when {
        p == "1" -> "1 player"
        p.first().isDigit() -> "$p players"
        else -> p
    }
}

/** Play time, when it was last played, how many sessions, its status and achievements: the ones there are. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlayStats(game: Game) {
    val meta = game.meta
    val stats = buildList {
        playProgress(meta)?.let { add(it to "Played") }
        if (meta.lastPlayed > 0) add(ago(meta.lastPlayed) to "Last played")
        if (meta.playCount > 0) add("${meta.playCount}" to if (meta.playCount == 1) "Session" else "Sessions")
        meta.playStatus?.let { add(it.label to "Status") }
        trophyChip(game)?.removePrefix("🏆 ")?.let { add(it to "Achievements") }
    }
    if (stats.isEmpty()) {
        Text("Not played yet", style = MaterialTheme.typography.titleSmall.overArt(), color = Palette.Muted, maxLines = 1)
        return
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        stats.forEach { (value, label) ->
            Column {
                Text(value, style = MaterialTheme.typography.titleMedium.overArt(), color = Palette.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(label, style = MaterialTheme.typography.labelSmall.overArt(), color = Palette.Muted, maxLines = 1)
            }
        }
    }
}

/** "3 minutes ago", "Yesterday", or "Just now". */
private fun ago(time: Long): String {
    val now = System.currentTimeMillis()
    return if (now - time < 60_000) "Just now" else DateUtils.getRelativeTimeSpanString(time, now, DateUtils.MINUTE_IN_MILLIS).toString()
}

/** An app's panel: its name, what it is, and A to open it. */
@Composable
private fun AppPanel(item: GridItem) {
    Column(Modifier.fillMaxWidth()) {
        Text(item.title, style = MaterialTheme.typography.displaySmall.overArt(), color = Palette.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
        val line = item.blurb.ifEmpty { item.type }
        if (line.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(line, style = MaterialTheme.typography.titleMedium.overArt(), color = Palette.Muted, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(18.dp))
        PanelHints("Open", menu = item.kind == TileKind.App, story = false, expanded = false)
    }
}

/** The button line under the panel. Each hint can be tapped. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PanelHints(a: String, menu: Boolean, story: Boolean, expanded: Boolean) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ButtonHint("A", a, Palette.Mint)
        if (menu) ButtonHint("Y", "Menu", Palette.Butter)
        if (story && SpotlightMode.ownsX) ButtonHint("X", if (expanded) "Less" else "More", Palette.Sky)
        Row(verticalAlignment = Alignment.CenterVertically) {
            ButtonHint("L1", "", Palette.Lavender)
            Spacer(Modifier.width(4.dp))
            ButtonHint("R1", "Groups", Palette.Lavender)
        }
    }
}

/** Spotlight's rows in Home & layout › Home screen (shown while Spotlight is the layout). */
@Composable
fun spotlightHomeSettings(): SettingSection {
    val look = Store.settings.look
    return SettingSection(
        "Spotlight",
        listOf(
            choice(
                "Spotlight rail", Glyph.Home, Palette.Lavender,
                "Which side the rail of covers runs down. The game's details take the other side.",
                SpotlightMode.Rails, look.spotlightRail,
            ) { v -> updateLook { it.copy(spotlightRail = v) } }.copy(keywords = "spotlight rail side left right mirror covers"),
            choice(
                "Spotlight backdrop", Glyph.Image, Palette.Peach,
                "What fills the screen behind the highlighted game: its key art, its cover blurred, or just the theme's colour.",
                SpotlightMode.Backdrops, look.spotlightBackdrop,
            ) { v -> updateLook { it.copy(spotlightBackdrop = v) } }.copy(keywords = "spotlight backdrop background art hero blur cover dim wallpaper"),
            toggle(
                "Spotlight motion", Glyph.Sparkle, Palette.Mint,
                "The art behind the game drifts and zooms in very slowly. Off with Reduce motion too.",
                look.spotlightMotion,
            ) { v -> updateLook { it.copy(spotlightMotion = v) } }.copy(keywords = "spotlight motion zoom ken burns drift animation"),
        ),
    )
}
