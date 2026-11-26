package io.github.zahakj.tandem.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import io.github.zahakj.tandem.Button
import io.github.zahakj.tandem.Collections
import io.github.zahakj.tandem.Dates
import io.github.zahakj.tandem.Game
import io.github.zahakj.tandem.Lang
import io.github.zahakj.tandem.Launcher
import io.github.zahakj.tandem.Libraries
import io.github.zahakj.tandem.PlatformLibrary
import io.github.zahakj.tandem.Privacy
import io.github.zahakj.tandem.R
import io.github.zahakj.tandem.RunningGames
import io.github.zahakj.tandem.Selected
import io.github.zahakj.tandem.Selection
import io.github.zahakj.tandem.Tx
import io.github.zahakj.tandem.data.HomeStore
import io.github.zahakj.tandem.data.Store
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/*
 * Stage, a home layout that is horizontal at both levels ("stage"). Each group is a page of its own: Continue (the
 * game running and the last played), each system with games, each collection, then the apps. L1 / R1 slide the whole
 * page out the way the press went and the next one in; on a page, Left / Right move along one row of covers, the
 * highlight held a third of the way along once the row scrolls, and the highlighted game takes over the backdrop,
 * its logo, facts, story and play time standing above-left of the row. The group strip runs along the top (Up reaches
 * it; Left / Right there flip pages live), the group's name and the buttons along the bottom, and on the other screen
 * a flip shows the group's own card for a moment before the game.
 *
 * Cheap by design: the row's glide is one Animatable read only in placement, only the covers near the view are
 * composed, and the backdrop and the words follow the highlight once it rests (Selection.follow).
 */

object StageMode {
    /** Stage is the home layout. */
    val enabled: Boolean get() = Store.settings.look.homeLayout == "stage"

    /** The focus route remembering the page; each page remembers its game under [itemRoute]. */
    const val ROUTE = "home:stage"
    /** Focus keys on the home route: the stage, or the empty library's note. */
    const val STAGE = "stage"
    const val EMPTY = "stage:empty"
    /** The keys of the Continue and apps pages. */
    const val CONTINUE = "c:continue"
    const val APPS = "apps"
    internal const val SEE_ALL = "stage:all"

    private fun itemRoute(page: String) = "home:stage:$page"

    val Covers: List<Pair<String, String>>
        get() = listOf("s" to Tx.s(R.string.home_stage_covers_s), "m" to Tx.s(R.string.home_stage_covers_m), "l" to Tx.s(R.string.home_stage_covers_l))

    internal fun rememberSpot(page: String, item: String?) {
        FocusMemory.remember(ROUTE, page)
        if (item != null) FocusMemory.remember(itemRoute(page), item)
    }

    internal fun remembered(page: String): String? = FocusMemory.get(itemRoute(page))
    internal val rememberedPage: String? get() = FocusMemory.get(ROUTE)
}

/** One cover on a page: a game, an app (or one of Tandem's), or the "See all" card at the end of a system's row. */
private class StageEntry(val key: String, val title: String, val game: Game? = null, val item: GridItem? = null, val all: Route? = null) {
    val pkg: String? get() = item?.pkg?.takeIf { item.kind == TileKind.App }
    val selected: Selected? get() = game?.let { Selected.GameItem(it) } ?: pkg?.let { Selected.App(it) } ?: item?.selected
}

/** A page: its key (GroupPaging's), the strip's short name, its name, colour, covers and what the other screen shows for it. */
private class StagePage(
    val key: String, val label: String, val title: String, val color: Color, val entries: List<StageEntry>, val games: Int,
    val selected: Selected, val recent: String?,
)

private fun buildPages(catalog: GridCatalog, pinned: List<String>, holds: String, running: List<Game>): List<StagePage> {
    val out = ArrayList<StagePage>()
    fun recentOf(games: List<Game>) = games.filter { it.meta.lastPlayed > 0 }.maxByOrNull { it.meta.lastPlayed }?.key
    val deck = ContinueDeck.pick(running, Collections.continuePlaying(), { it.key }, { Privacy.canShow(it) }, max = 15)
    if (deck.isNotEmpty()) {
        val title = Tx.s(R.string.home_shelves_continue)
        out += StagePage(
            StageMode.CONTINUE, Tx.s(R.string.home_strip_continue), title, Palette.Mint, deck.map { StageEntry(it.key, it.title, game = it) }, deck.size,
            Selected.Group(title, Tx.p(R.plurals.home_games_count, deck.size), deck, Palette.Mint), deck.first().key,
        )
    }
    for (key in catalog.defaults) {
        if (!key.startsWith("s:")) continue
        val item = catalog.item(key)?.takeIf { it.games.isNotEmpty() } ?: continue
        val id = item.platformId
        val short = id?.let { Libraries.byId(it)?.platform?.shortName }?.ifBlank { null } ?: item.title
        val entries = item.games.map { StageEntry(it.key, it.title, game = it) } +
            listOfNotNull(id?.let { StageEntry(StageMode.SEE_ALL, Tx.s(R.string.home_stage_see_all), all = Route.System(it)) })
        out += StagePage(key, short, item.title, item.color, entries, item.games.size, item.selected, recentOf(item.games))
    }
    if (holds != "systems") for (key in catalog.defaults) {
        if (!key.startsWith("col:")) continue
        val item = catalog.item(key)?.takeIf { it.games.isNotEmpty() } ?: continue
        val entries = item.games.map { StageEntry(it.key, it.title, game = it) } +
            listOfNotNull(item.collectionId?.let { StageEntry(StageMode.SEE_ALL, Tx.s(R.string.home_stage_see_all), all = Route.Collection(it)) })
        out += StagePage(key, item.title, item.title, item.color, entries, item.games.size, item.selected, recentOf(item.games))
    }
    if (holds == "all") {
        val appKeys = catalog.defaults.filter { !it.startsWith("s:") && !it.startsWith("col:") && it != "genres" } + "allapps" + pinned.map { "app:$it" }
        val seen = HashSet<Any>()
        val apps = appKeys.mapNotNull { k ->
            val item = catalog.item(k) ?: return@mapNotNull null
            // A Tandem app pinned to the dock is the same app as its own tile.
            if (!seen.add(item.builtIn ?: item.pkg ?: k)) return@mapNotNull null
            StageEntry(k, item.title, item = item)
        }
        if (apps.isNotEmpty()) {
            val title = Tx.s(R.string.home_spotlight_apps)
            out += StagePage(StageMode.APPS, title, title, Palette.Sky, apps, 0, Selected.App(Dock.ALL_APPS), null)
        }
    }
    return out
}

/** The stage's live state: the page, the highlighted cover and its glide, what the words and backdrop show. */
private class StageState(page: Int, index: Int, private val scope: CoroutineScope) {
    var pages: List<StagePage> = emptyList()
    var page by mutableIntStateOf(page)
    var index by mutableIntStateOf(index)
    val offset = Animatable(index.toFloat())
    /** The cover whose words and backdrop show: the highlighted one once the highlight rests. */
    var spot by mutableStateOf<String?>(null)
    var focused by mutableStateOf(false)
    /** Up took the highlight onto the group strip: Left / Right flip pages there. */
    var onStrip by mutableStateOf(false)
    /** The way the last flip went (+1 R1, -1 L1), for the slide. */
    var turn = 1
    /** Bumped on every flip: the other screen shows the group's card for a moment. */
    var flips by mutableIntStateOf(0)

    val current: StagePage? get() = pages.getOrNull(page)
    val entries: List<StageEntry> get() = current?.entries.orEmpty()
    val entry: StageEntry? get() = entries.getOrNull(index)
    private val still: Boolean get() = Motion.style == AnimationStyle.Off || Store.settings.reduceMotion

    fun select(e: StageEntry) {
        Panels.focusedApp = e.pkg
        e.selected?.let { Selection.current = it }
    }

    /** The highlight reached [entry]: remember it, and once it rests the words, the backdrop and the other screen follow. */
    private fun arrived() {
        val p = current ?: return
        val e = entry ?: return
        StageMode.rememberSpot(p.key, e.key)
        Selection.follow {
            spot = e.key
            if (focused) select(e)
        }
    }

    fun goTo(i: Int) {
        if (i < 0 || i >= entries.size) return
        if (i != index) Sounds.move()
        index = i
        arrived()
        scope.launch { if (still) offset.snapTo(i.toFloat()) else offset.animateTo(i.toFloat(), Motion.move()) }
    }

    fun step(by: Int) = goTo(StageLayout.step(index, by, entries.size))

    /** L1 / R1: the page before or after, wrapping round. */
    fun flip(by: Int) {
        val to = GroupPaging.step(pages.map { it.key }, current?.key, by) ?: return
        show(pages.indexOfFirst { it.key == to }, by)
    }

    /** Page [p], on the cover it remembers (else its last played, else its first), coming in from the side of [dir]. */
    fun show(p: Int, dir: Int) {
        val pg = pages.getOrNull(p) ?: return
        if (p == page) return
        turn = dir
        page = p
        val keys = pg.entries.map { it.key }
        val i = StageLayout.landing(keys, StageMode.remembered(pg.key), pg.recent).coerceAtLeast(0)
        index = i
        spot = keys.getOrNull(i)
        StageMode.rememberSpot(pg.key, spot)
        scope.launch { offset.snapTo(i.toFloat()) }
        flips++
        Sounds.page()
    }

    fun open(ctx: android.content.Context) {
        val e = entry ?: return
        when {
            e.game != null -> Launcher.launch(ctx, e.game)
            e.all != null -> openFolder(e.all)
            else -> e.item?.open?.invoke()
        }
    }
}

/** The Stage home. Composed by [HomeLayout]. */
@Composable
fun StageHome(libs: List<PlatformLibrary>) {
    IconPacksDriver()
    val ctx = LocalContext.current
    val look = Store.settings.look
    val route = Route.Home.key
    val catalog = rememberCatalog(libs)
    // Package changes, not every return: each check asks the package manager about every pinned app.
    val packages = io.github.zahakj.tandem.Emulators.changes
    val pinned = remember(HomeStore.settings.dock, packages) { Dock.pinned(ctx) }
    val running = RunningGames.list
    val pages = remember(catalog, pinned, look.stageGroups, running, Store.meta, Privacy.view, Lang.revision.intValue) {
        buildPages(catalog, pinned, look.stageGroups, running.mapNotNull { it.game })
    }
    val noGames = Collections.visibleGames().isEmpty()

    val scope = rememberCoroutineScope()
    val st = remember {
        val p = pages.indexOfFirst { it.key == StageMode.rememberedPage }.coerceAtLeast(0)
        val i = pages.getOrNull(p)?.let { pg -> StageLayout.landing(pg.entries.map { it.key }, StageMode.remembered(pg.key), pg.recent) }?.coerceAtLeast(0) ?: 0
        StageState(p, i, scope).also { it.pages = pages; it.spot = it.entry?.key }
    }
    st.pages = pages
    // The pages changed (a rescan, a game started, something hidden): the same page and game where they still are.
    LaunchedEffect(pages) {
        val p = pages.indexOfFirst { it.key == StageMode.rememberedPage }.takeIf { it >= 0 } ?: st.page.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
        st.page = p
        val i = st.entries.indexOfFirst { it.key == st.current?.key?.let(StageMode::remembered) }.takeIf { it >= 0 } ?: st.index.coerceIn(0, (st.entries.size - 1).coerceAtLeast(0))
        st.index = i
        if (st.offset.value != i.toFloat() && !st.offset.isRunning) st.offset.snapTo(i.toFloat())
        if (st.entries.none { it.key == st.spot }) st.spot = st.entry?.key
    }
    // A flip shows the group's own card on the other screen for a moment, then the game again.
    LaunchedEffect(st.flips) {
        if (st.flips == 0 || !st.focused) return@LaunchedEffect
        st.current?.let { Selection.current = it.selected }
        delay(1200)
        if (st.focused) st.entry?.let { st.select(it) }
    }

    val keys = if (noGames || pages.isEmpty()) listOf(StageMode.EMPTY) else listOf(StageMode.STAGE)
    // Coming from another layout, the home's remembered item (a grid tile, a shelf card) means nothing here.
    remember(keys) {
        if (FocusMemory.get(route).let { it == null || it !in keys }) FocusMemory.remember(route, keys.first())
        0
    }
    val target = rememberFocusTarget(route, keys)
    val stageReq = remember { FocusRequester() }
    fun focusStage() {
        if (pages.isEmpty()) return
        FocusMemory.remember(route, StageMode.STAGE)
        if (runCatching { stageReq.requestFocus() }.isFailure) Nav.focusTick++
    }

    InterceptButtons(true) { b ->
        if (!GroupPaging.free || st.pages.isEmpty() || noGames) return@InterceptButtons false
        when (b) {
            // L1 / R1: the page before or after, from anywhere on the home (the dock too).
            Button.L1, Button.R1 -> {
                st.onStrip = false
                st.flip(if (b == Button.R1) 1 else -1)
                if (!st.focused) focusStage()
                true
            }
            // L2 / R2 on the row: the letter before or after (or its ends, on a short row). Off it R2 is the side panel.
            Button.L2, Button.R2 -> {
                if (!st.focused) return@InterceptButtons false
                val titles = st.entries.map { it.title }
                st.goTo(StageLayout.letterJump(titles, st.index, if (b == Button.R2) 1 else -1, page = 8))
                true
            }
            // Y and its friends act on the game, never on the group card still showing after a flip.
            Button.Y, Button.Grab -> { if (st.focused) st.entry?.let { st.select(it) }; false }
            // Select: Surprise me slides to the game's page and lands on it.
            Button.Select -> {
                val pool = Collections.visibleGames()
                val pick = pool.filter { it.meta.playCount == 0 && it.meta.playSeconds == 0L }.ifEmpty { pool }.randomOrNull() ?: return@InterceptButtons false
                val p = st.pages.indexOfFirst { it.key == "s:${pick.platform.id}" }
                if (p < 0) return@InterceptButtons false
                st.show(p, 1)
                st.goTo(st.entries.indexOfFirst { it.key == pick.key })
                if (!st.focused) focusStage()
                Toasts.show(Tx.s(R.string.home_surprise, pick.title))
                true
            }
            else -> false
        }
    }
    // B, one step at a time: off the strip, the row's first cover, then the Continue page. Home never leaves Tandem.
    BackHandler(enabled = Nav.route == Route.Home && !Nav.overlayOpen && FolderPopup.route == null && pages.isNotEmpty() && !noGames) {
        if (st.onStrip) { st.onStrip = false; return@BackHandler }
        when (StageLayout.back(st.index, st.page, 0)) {
            StageLayout.Back.Top -> st.goTo(0)
            StageLayout.Back.Home -> st.show(0, -1)
            StageLayout.Back.Nothing -> if (!st.focused) focusStage()
        }
    }

    Column(Modifier.fillMaxSize()) {
        if (noGames || pages.isEmpty()) {
            val hidden = libs.any { it.games.isNotEmpty() }
            val pick = rememberGameFolderPicker()
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                EmptyNote(
                    Tx.s(if (hidden) R.string.home_spotlight_hidden else R.string.home_spotlight_none),
                    Tx.s(if (hidden) R.string.home_spotlight_show_hidden else R.string.home_spotlight_pick),
                    Modifier.takesFocus(route, StageMode.EMPTY, target == StageMode.EMPTY),
                ) { if (hidden) openSetting("play/privacy", ShowHiddenKey) else pick() }
            }
        } else {
            val swipe = with(LocalDensity.current) { 80.dp.toPx() }
            val rtl = isRtl
            BoxWithConstraints(
                Modifier.fillMaxWidth().weight(1f)
                    .focusRequester(stageReq)
                    .takesFocus(route, StageMode.STAGE, target == StageMode.STAGE)
                    .stageKeys(st, ctx)
                    .focusable(interactionSource = remember { MutableInteractionSource() })
                    // A sideways drag flips the page, the way the finger went.
                    .pointerInput(rtl) {
                        var dx = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { dx = 0f },
                            onDragEnd = { if (abs(dx) > swipe) st.flip((if (dx < 0) 1 else -1) * (if (rtl) -1 else 1)) },
                        ) { _, d -> dx += d }
                    },
            ) {
                val h = maxHeight
                val w = maxWidth
                val stripH = 40.dp
                val footH = 30.dp
                val share = when (look.stageCovers) { "s" -> 0.24f; "l" -> 0.36f; else -> 0.3f }
                val coverH = h * share
                val coverW = coverH * when (look.cardShape) { "portrait" -> 0.72f; "landscape" -> 1.45f; else -> 1f }
                val rowH = coverH * 1.18f + 26.dp
                val still = Motion.style == AnimationStyle.Off || Store.settings.reduceMotion
                val sign = startSign
                AnimatedContent(
                    st.page, Modifier.fillMaxSize(), label = "stage-page",
                    transitionSpec = {
                        val dir = st.turn * sign.toInt()
                        if (still) fadeIn(tween(120)) togetherWith fadeOut(tween(90))
                        else (slideInHorizontally(tween(280, easing = Motion.EmphasizedDecelerate)) { it * dir } + fadeIn(tween(200))) togetherWith
                            (slideOutHorizontally(tween(280, easing = Motion.EmphasizedDecelerate)) { -it * dir } + fadeOut(tween(220)))
                    },
                ) { p ->
                    StagePageView(st, p, look.stageBackdrop, w, h, stripH, footH, rowH, coverW, coverH)
                }
                // The group strip: where L1 / R1 go, lit while Up has the highlight on it.
                val groups = remember(pages) { pages.map { StripGroup(it.key, it.label, it.color) } }
                Box(
                    Modifier.align(Alignment.TopCenter).padding(top = 6.dp).height(stripH - 6.dp)
                        .then(if (st.onStrip && st.focused) Modifier.border(2.dp, Palette.highlightFor(st.current?.color ?: Palette.Accent), RoundedCornerShape(50)) else Modifier)
                        .darkChip().padding(horizontal = 8.dp, vertical = 3.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    GroupStrip(groups, st.current?.key, chips = 9) { key ->
                        val i = pages.indexOfFirst { it.key == key }
                        if (i >= 0) st.show(i, if (i > st.page) 1 else -1)
                        focusStage()
                    }
                }
            }
        }
        DockBar()
    }
}

/** One page: the backdrop, the highlighted game's words, the row of covers and the footer. */
@Composable
private fun StagePageView(
    st: StageState, p: Int, backdrop: String, w: Dp, h: Dp, stripH: Dp, footH: Dp, rowH: Dp, coverW: Dp, coverH: Dp,
) {
    val page = st.pages.getOrNull(p) ?: return
    val live = p == st.page
    // A page sliding out keeps showing what it showed.
    val frozen = remember(p) { st.index }
    val at = if (live) st.index else frozen
    val shown = (if (live) page.entries.firstOrNull { it.key == st.spot } else null) ?: page.entries.getOrNull(at)
    Box(Modifier.fillMaxSize()) {
        StageBackdrop(shown?.game, backdrop)
        val rowTop = h - footH - rowH
        Box(
            Modifier.padding(start = 28.dp, end = 28.dp, top = stripH + 6.dp).fillMaxWidth(if (w > 600.dp) 0.5f else 0.9f).height((rowTop - stripH - 10.dp).coerceAtLeast(40.dp)),
            contentAlignment = Alignment.BottomStart,
        ) { StageWords(shown, page) }
        StageRow(st, page, live, at, Modifier.offset(y = rowTop).fillMaxWidth().height(rowH), w, coverW, coverH)
        StageFooter(page, page.entries.getOrNull(at), Modifier.align(Alignment.BottomStart).fillMaxWidth().height(footH).padding(horizontal = 28.dp))
    }
}

/** The highlighted game's key art (or its cover blurred, or nothing but the theme), fading into the theme below and at the start. */
@Composable
private fun StageBackdrop(game: Game?, mode: String) {
    if (mode == "dim") return
    AnimatedContent(game, Modifier.fillMaxSize(), contentKey = { it?.key }, transitionSpec = { fadeIn(Motion.fade(420)) togetherWith fadeOut(Motion.fade(300)) }, label = "stage-art") { g ->
        if (g == null) return@AnimatedContent
        val lib = Libraries.of(g)
        if (mode == "blur") {
            val cover = remember(g.key, Store.meta) { lib?.box(g) }
            if (cover != null) AsyncImage(
                cover, null, Modifier.fillMaxSize().blur(34.dp).graphicsLayer { alpha = 0.55f }.fadeMask(0f to 1f, 0.7f to 0.5f, 1f to 0.15f),
                contentScale = ContentScale.Crop,
            )
            return@AnimatedContent
        }
        val art = remember(g.key, Store.meta) { lib?.let { it.hero(g) ?: it.screenshot(g) ?: it.box(g) } }
        if (art != null) Box(Modifier.fillMaxSize().fadeMask(0f to 0.95f, 0.55f to 0.6f, 1f to 0.12f).fadeMaskHorizontal(0f to 0.4f, 0.5f to 1f)) {
            KenBurns(art, Modifier.fillMaxSize(), moving = Store.settings.look.kenBurns)
        }
    }
}

/** The logo (or the name), a line of facts, two lines of the story and the play line, rising in on a change. */
@Composable
private fun StageWords(e: StageEntry?, page: StagePage) {
    AnimatedContent(e, contentKey = { it?.key }, transitionSpec = { fadeIn(Motion.fade(220)) togetherWith fadeOut(Motion.fade(120)) }, label = "stage-words") { entry ->
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            val g = entry?.game
            when {
                g != null -> {
                    HeroLogo(g, Modifier.riseIn(g.key, 0).widthIn(max = 380.dp), maxHeight = 64, textColor = Color.White)
                    val m = g.meta
                    val facts = listOfNotNull(m.year?.toString(), m.developer, m.genres.firstOrNull()?.let { Collections.tidyGenre(it) }, g.platform.shortName)
                    if (facts.isNotEmpty()) Text(
                        facts.joinParts(), Modifier.riseIn(g.key, 1, 12.dp), style = MaterialTheme.typography.labelLarge.onArt(),
                        color = lerp(page.color, Color.White, 0.55f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    m.description?.trim()?.takeIf { it.isNotEmpty() }?.let {
                        Text(it, Modifier.riseIn(g.key, 2, 12.dp), style = MaterialTheme.typography.bodyMedium.onArt(), color = Color.White.copy(alpha = 0.9f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    val runs = RunningGames.list.any { it.game?.key == g.key }
                    val play = listOfNotNull(
                        Tx.s(R.string.screens_running).takeIf { runs },
                        m.playSeconds.takeIf { it >= 60 }?.let { formatPlayTime(it) },
                        m.lastPlayed.takeIf { it > 0 }?.let { Dates.relative(it) },
                    )
                    if (play.isNotEmpty()) Text(play.joinParts(), style = MaterialTheme.typography.titleSmall.onArt(), color = Palette.Mint, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                entry?.all != null -> {
                    Text(page.title, style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold).onArt(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(Tx.p(R.plurals.home_stage_see_all_games, page.games, page.games), style = MaterialTheme.typography.titleSmall.onArt(), color = page.color, maxLines = 1)
                }
                entry != null -> {
                    Text(entry.title, style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold).onArt(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(entry.item?.type?.ifBlank { null } ?: Tx.s(R.string.home_orbit_app), style = MaterialTheme.typography.titleSmall.onArt(), color = page.color, maxLines = 1)
                }
            }
        }
    }
}

/**
 * The row of covers. Each is placed from the glide (one Animatable) in the layout phase only; the highlighted one is
 * bigger and ringed in the highlight's colour, a running game carries a mint dot, and the row scrolls once the
 * highlight is a third of the way along.
 */
@Composable
private fun StageRow(st: StageState, page: StagePage, live: Boolean, at: Int, modifier: Modifier, w: Dp, coverW: Dp, coverH: Dp) {
    val ctx = LocalContext.current
    val density = LocalDensity.current
    val gap = 14.dp
    val margin = 30.dp
    val pitchPx = with(density) { (coverW + gap).toPx() }
    val marginPx = with(density) { margin.toPx() }
    val slot = StageLayout.slot(with(density) { (w - margin * 2).toPx() }, pitchPx)
    val shown = ((w - margin) / (coverW + gap)).toInt() + 1
    val count = page.entries.size
    val glide: () -> Float = { if (live) st.offset.value else at.toFloat() }
    val window by remember(page, live, slot, shown) { derivedStateOf { StageLayout.window(StageLayout.scroll(glide(), slot), shown, count) } }
    val running = RunningGames.list.mapNotNull { it.game?.key }.toHashSet()
    val ringColor = Palette.highlightFor(page.color)
    val ringW = with(density) { (Themes.live.highlightThickness * 0.75f).coerceIn(1.2f, 6f).dp.toPx() }
    Box(modifier) {
        for (i in window) {
            val e = page.entries[i]
            key(e.key) {
                val on by remember(i, live) { derivedStateOf { live && st.index == i } }
                val grow by animateFloatAsState(if (on) 1.18f else 1f, Motion.focus(), label = "stage-cover")
                val ring by animateFloatAsState(if (on && st.focused && !st.onStrip) 1f else 0f, Motion.soft(), label = "stage-ring")
                Box(
                    Modifier.align(Alignment.BottomStart).padding(bottom = 12.dp)
                        .offset { IntOffset((marginPx + (i - StageLayout.scroll(glide(), slot)) * pitchPx).roundToInt(), 0) }
                        .size(coverW, coverH)
                        .graphicsLayer {
                            transformOrigin = TransformOrigin(0.5f, 1f)
                            scaleX = grow; scaleY = grow
                            alpha = if (on || !st.focused) 1f else 0.82f
                        }
                        .drawWithContent {
                            drawContent()
                            if (ring > 0.01f) {
                                val inset = 4.dp.toPx() + ringW / 2f
                                drawRoundRect(
                                    ringColor, Offset(-inset, -inset), Size(size.width + inset * 2, size.height + inset * 2),
                                    CornerRadius(14.dp.toPx() + inset), alpha = ring, style = Stroke(ringW),
                                )
                            }
                        }
                        .pointerInput(e.key, live) {
                            detectTapGestures {
                                if (!live) return@detectTapGestures
                                if (st.index == i && st.focused) st.open(ctx) else st.goTo(i)
                            }
                        },
                ) {
                    Box(
                        Modifier.fillMaxSize().graphicsLayer {
                            shape = RoundedCornerShape(14.dp); clip = true
                            shadowElevation = (if (on) 18f else 8f) * this.density
                            ambientShadowColor = ringColor; spotShadowColor = Color.Black
                        },
                    ) { StageCover(e, page, coverW) }
                    if (e.game != null && e.game.key in running) {
                        Box(Modifier.align(Alignment.TopEnd).padding(7.dp).size(11.dp).clip(CircleShape).background(Palette.Mint).border(2.dp, Palette.NightDeep, CircleShape))
                    }
                }
            }
        }
    }
}

@Composable
private fun StageCover(e: StageEntry, page: StagePage, coverW: Dp) {
    val g = e.game
    when {
        g != null -> GameArt(g)
        e.all != null -> Box(Modifier.fillMaxSize().background(lerp(page.color, Palette.Night, 0.45f)), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                GlyphIcon(Glyph.Forward, Color.White, Modifier.size(coverW * 0.26f))
                Spacer(Modifier.height(6.dp))
                Text(e.title, style = MaterialTheme.typography.labelLarge, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        e.item != null -> TileArtView(e.item.art, coverW)
    }
}

/** The group's name and size on the left; what A and Y do on the right. */
@Composable
private fun StageFooter(page: StagePage, e: StageEntry?, modifier: Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(9.dp).clip(CircleShape).background(page.color))
        Spacer(Modifier.width(8.dp))
        Text(
            listOfNotNull(page.title, page.games.takeIf { it > 0 }?.let { Tx.p(R.plurals.home_games_count, it) }).joinParts(),
            Modifier.weight(1f), style = MaterialTheme.typography.labelLarge.onArt(), color = Palette.Text, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        val g = e?.game
        val runs = g != null && RunningGames.list.any { it.game?.key == g.key }
        // Trying layouts A keeps the layout instead (the try-on pill says so).
        Row(
            Modifier.awayWhileTrying().darkChip().padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            ButtonHint(
                "A", Tx.s(if (g == null) R.string.screens_hint_open else if (runs) R.string.home_resume_hint_resume else R.string.screens_hint_play),
                Palette.Mint,
            )
            if (g != null) ButtonHint("Y", Tx.s(if (runs) R.string.common_close else R.string.screens_hint_options), if (runs) Palette.Peach else Palette.Butter)
        }
    }
}

/**
 * The stage's buttons: Left / Right along the row (faster the longer they are held) or, on the strip, through the
 * pages; Up onto the strip (again: the top bar); Down off it, or on to the dock; A plays or opens.
 */
@Composable
private fun Modifier.stageKeys(st: StageState, ctx: android.content.Context): Modifier {
    val press = remember { TilePress() }
    return onFocusChanged {
        val was = st.focused
        st.focused = it.isFocused
        if (it.isFocused && !was) st.entry?.let { e -> st.select(e) }
        else if (!it.isFocused) { press.pressed = false; st.onStrip = false; if (Panels.focusedApp == st.entry?.pkg) Panels.focusedApp = null }
    }.onKeyEvent { e ->
        val down = e.type == KeyEventType.KeyDown
        val first = e.nativeKeyEvent.repeatCount == 0
        when (e.key) {
            Key.DirectionLeft, Key.DirectionRight -> {
                if (down) {
                    if (st.onStrip) st.flip(e.sideStep())
                    else st.step(e.sideStep() * SpotlightRail.holdStep(e.nativeKeyEvent.eventTime - e.nativeKeyEvent.downTime))
                }
                true
            }
            Key.DirectionUp -> {
                if (down && first) {
                    if (!st.onStrip) { st.onStrip = true; Sounds.move() } else if (TopBarFocus.enter()) st.onStrip = false
                }
                true
            }
            Key.DirectionDown -> if (st.onStrip) { if (down) { st.onStrip = false; Sounds.move() }; true } else false
            else -> if (st.onStrip && (e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.ButtonA)) {
                if (!down) { st.onStrip = false; Sounds.move() }
                true
            } else tileKey(e, press, { st.open(ctx) }, null)
        }
    }
}

/** Stage's rows in Home & layout › Home screen (shown while Stage is the layout). */
@Composable
fun stageSettings(): SettingSection {
    val look = Store.settings.look
    return SettingSection(Tx.s(R.string.home_stage_settings), en = "Stage",
        rows = listOf(
            choice(
                Tx.s(R.string.home_stage_holds), Glyph.Home, Palette.Lavender, Tx.s(R.string.home_stage_holds_help),
                Orbit.Groups, look.stageGroups, en = "Stage holds",
                look = { v -> LayoutPreview("stage", Modifier.fillMaxSize(), rounded = false, look = look.copy(stageGroups = v)) },
            ) { v -> updateLook { it.copy(stageGroups = v) } }.copy(keywords = "stage pages groups systems collections apps"),
            choice(
                Tx.s(R.string.home_stage_covers), Glyph.Grid, Palette.Mint, Tx.s(R.string.home_stage_covers_help),
                StageMode.Covers, look.stageCovers, en = "Stage cover size",
                look = { v -> LayoutPreview("stage", Modifier.fillMaxSize(), rounded = false, look = look.copy(stageCovers = v)) },
            ) { v -> updateLook { it.copy(stageCovers = v) } }.copy(keywords = "stage covers size small medium large bigger smaller"),
            choice(
                Tx.s(R.string.home_stage_backdrop), Glyph.Image, Palette.Peach, Tx.s(R.string.home_stage_backdrop_help),
                SpotlightMode.Backdrops, look.stageBackdrop, en = "Stage backdrop",
                look = { v -> LayoutPreview("stage", Modifier.fillMaxSize(), rounded = false, look = look.copy(stageBackdrop = v)) },
            ) { v -> updateLook { it.copy(stageBackdrop = v) } }.copy(keywords = "stage backdrop background art hero blur cover dim wallpaper"),
        ),
    )
}
