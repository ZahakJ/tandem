package io.github.zahakj.tandem.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.zahakj.tandem.Button
import io.github.zahakj.tandem.DateKind
import io.github.zahakj.tandem.Dates
import io.github.zahakj.tandem.Game
import io.github.zahakj.tandem.Libraries
import io.github.zahakj.tandem.Privacy
import io.github.zahakj.tandem.R
import io.github.zahakj.tandem.Selected
import io.github.zahakj.tandem.Selection
import io.github.zahakj.tandem.Tx
import io.github.zahakj.tandem.data.PlazaStore
import io.github.zahakj.tandem.data.Store
import io.github.zahakj.tandem.pass.AvatarSpec
import io.github.zahakj.tandem.pass.PassBook
import io.github.zahakj.tandem.pass.TandemPass
import io.github.zahakj.tandem.pass.Visitor
import kotlinx.coroutines.delay

/*
 * The Plaza (Route.Tool("plaza")): everyone you passed with Tandem Pass, strolling about a little square on the top
 * screen, with a strip of their cards along the bottom. The one highlighted gets a spotlight and waves; the bottom
 * screen shows their whole card. L1 / R1 switch New and Everyone, A (or Y) on someone opens Wave back, Hide and
 * Block, X opens Me. Newcomers are greeted with the Passby jingle.
 */

/** What the bottom screen shows for Tandem Pass: someone's card on the Plaza, or yours while you make it. */
object PlazaBottom {
    var showPlaza by mutableStateOf(false)
    var showMe by mutableStateOf(false)
    var visitor by mutableStateOf<Visitor?>(null)

    val active: Boolean get() = (showPlaza || showMe) && !Privacy.enabled
}

object PlazaUi {
    const val ID = "plaza"

    /** The crumb of the Plaza's own screens that aren't built-in apps (Me). */
    fun crumb(id: String): String? = when (id) {
        MeEditor.ROUTE_ID -> Tx.s(R.string.plaza_me_title)
        else -> null
    }

    /** Opens the Plaza, or Me first while there is no Me yet. */
    fun open() {
        if (!PlazaStore.data.made) MeEditor.open(plazaAfter = true) else Nav.go(Route.Tool(ID))
    }
}

private const val ROUTE = "tool:plaza"

/** The most people strolling about at once (the newest); everyone is in the strip. */
private const val CROWD = 14

@Composable
fun PlazaScreen() {
    val ctx = LocalContext.current
    if (Privacy.enabled) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            PipNote(Tx.s(R.string.plaza_private_title), Tx.s(R.string.plaza_private_detail), mood = 2)
        }
        return
    }
    val data = PlazaStore.data
    var onlyNew by remember { mutableStateOf(PassBook.newCount(PlazaStore.data) > 0) }
    val shown = remember(data.visitors, onlyNew) { PassBook.shown(data, onlyNew) }
    val keys = shown.map { it.key }
    val target = rememberFocusTarget(ROUTE, keys)
    var menuFor by remember { mutableStateOf<Visitor?>(null) }
    val focused = PlazaBottom.visitor?.key?.takeIf { it in keys }

    DisposableEffect(Unit) {
        PlazaBottom.showPlaza = true
        onDispose {
            PlazaBottom.showPlaza = false
            PlazaBottom.visitor = null
            // Everyone shown has been seen: the New filter and the badge start again from here.
            PlazaStore.update { PassBook.seen(it) }
        }
    }
    // Newcomers are greeted: the Passby jingle once on arriving, and for anyone who turns up while you look.
    LaunchedEffect(Unit) { if (PassBook.newCount(PlazaStore.data) > 0) { delay(420); Sounds.goodNews() } }
    val arrivals = TandemPass.arrivals
    val seenArrivals = remember { intArrayOf(arrivals) }
    LaunchedEffect(arrivals) { if (arrivals != seenArrivals[0]) { seenArrivals[0] = arrivals; Sounds.goodNews() } }
    LaunchedEffect(shown.isEmpty()) { if (shown.isEmpty()) { PlazaBottom.visitor = null; Selection.current = Selected.Home } }

    InterceptButtons(Nav.route == Route.Tool(PlazaUi.ID) && !Nav.overlayOpen && Layers.top == null && menuFor == null) { b ->
        when (b) {
            Button.L1, Button.R1 -> { onlyNew = !onlyNew; FocusMemory.forget(ROUTE); Nav.focusTick++; Sounds.page(); true }
            Button.X -> { MeEditor.open(); true }
            Button.Y -> { shown.firstOrNull { it.key == focused }?.let { menuFor = it }; true }
            else -> false
        }
    }

    Box(Modifier.fillMaxSize()) {
        PlazaScene(shown.take(CROWD), focused, data.me.avatar, Modifier.fillMaxSize())
        PlazaHeader(onlyNew, data.visitors.count { !it.hidden }, Modifier.align(Alignment.TopStart).padding(start = 20.dp, top = 6.dp)) { onlyNew = it; FocusMemory.forget(ROUTE); Nav.focusTick++ }
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(136.dp)) {
            when {
                shown.isNotEmpty() -> VisitorStrip(shown, target) { menuFor = it }
                data.visitors.none { !it.hidden } -> {
                    val off = TandemPass.status == TandemPass.Status.Off
                    EmptyNote(
                        Tx.s(if (off) R.string.plaza_empty_off else R.string.plaza_empty_none),
                        Tx.s(if (off) R.string.plaza_empty_off_action else R.string.plaza_empty_none_action),
                        Modifier.takesFocus(ROUTE, "empty", true), Modifier.align(Alignment.Center), mood = if (off) 2 else 0,
                    ) { if (off) PassSwitch.turnOn(ctx) else MeEditor.open() }
                }
                else -> EmptyNote(
                    Tx.s(R.string.plaza_empty_new), Tx.s(R.string.plaza_empty_new_action),
                    Modifier.takesFocus(ROUTE, "empty", true), Modifier.align(Alignment.Center), mood = 0,
                ) { onlyNew = false; Nav.focusTick++ }
            }
        }
        Row(
            Modifier.align(Alignment.TopEnd).padding(end = 20.dp, top = 6.dp).clip(RoundedCornerShape(50)).background(Palette.Card.copy(alpha = 0.88f)).padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            if (shown.isNotEmpty()) ButtonHint("Y", Tx.s(R.string.plaza_hint_options), Palette.Peach)
            ButtonHint("X", Tx.s(R.string.plaza_hint_me), Palette.Butter)
        }
    }
    VisitorMenu(menuFor) { menuFor = null }
}

// --- The header ----------------------------------------------------------------------------------------------------

@Composable
private fun PlazaHeader(onlyNew: Boolean, everyone: Int, modifier: Modifier, pick: (Boolean) -> Unit) {
    val status = TandemPass.status
    val (line, color) = statusLine(status)
    // On soft pills of their own, so they read clearly over the sky at any hour.
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            Modifier.clip(RoundedCornerShape(50)).background(Palette.Card.copy(alpha = 0.88f)).padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.size(9.dp).clip(CircleShape).background(color))
            Text(line, style = MaterialTheme.typography.labelLarge.onArt(), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("·", style = MaterialTheme.typography.labelLarge.onArt(), color = Palette.Muted) // tx-ok: a separator
            Text(Tx.p(R.plurals.plaza_met_count, everyone, everyone), style = MaterialTheme.typography.labelLarge.onArt(), color = Palette.Muted, maxLines = 1)
        }
        val rtl = isRtl
        Row(
            Modifier.clip(RoundedCornerShape(50)).background(Palette.Card.copy(alpha = 0.88f)).padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ButtonHint(if (rtl) "R1" else "L1", "", Palette.Muted)
            for ((on, res) in listOf(true to R.string.plaza_filter_new, false to R.string.plaza_filter_all)) {
                val sel = on == onlyNew
                Box(
                    Modifier.height(28.dp).clip(RoundedCornerShape(50))
                        .background(if (sel) Palette.Accent else Color.Transparent)
                        .pointerInput(on) { detectTapGestures { pick(on); Sounds.page() } }
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center,
                ) { FitText(Tx.s(res), MaterialTheme.typography.labelLarge, color = if (sel) Palette.OnAccent else Palette.Text) }
            }
            ButtonHint(if (rtl) "L1" else "R1", "", Palette.Muted)
        }
    }
}

/** A short line and a colour for what Tandem Pass is doing. */
@Composable
internal fun statusLine(s: TandemPass.Status): Pair<String, Color> = when (s) {
    TandemPass.Status.Passing -> Tx.s(if (TandemPass.findable) R.string.plaza_status_passing else R.string.plaza_status_find_only) to Palette.Mint
    TandemPass.Status.Paused -> Tx.s(R.string.plaza_status_paused) to Palette.Butter
    TandemPass.Status.Off -> Tx.s(R.string.plaza_status_off) to Palette.Muted
    TandemPass.Status.NotMade -> Tx.s(R.string.plaza_status_not_made) to Palette.Butter
    TandemPass.Status.NeedsPermission -> Tx.s(R.string.plaza_status_needs) to Palette.Peach
    TandemPass.Status.BluetoothOff -> Tx.s(R.string.plaza_status_bt_off) to Palette.Peach
    TandemPass.Status.Private -> Tx.s(R.string.plaza_status_private) to Palette.Lavender
    TandemPass.Status.OldAndroid -> Tx.s(R.string.plaza_status_old) to Palette.Muted
    TandemPass.Status.NoBluetooth -> Tx.s(R.string.plaza_status_no_bt) to Palette.Muted
}

// --- The strip of cards ----------------------------------------------------------------------------------------

@Composable
private fun BoxScope.VisitorStrip(shown: List<Visitor>, target: String?, open: (Visitor) -> Unit) {
    val state = rememberLazyListState()
    val keys = shown.map { it.key }
    KeepInView(state, keys.indexOf(target ?: keys.firstOrNull()))
    LazyRow(
        Modifier.align(Alignment.BottomCenter).fillMaxWidth().fadingEdges(state, horizontal = true),
        state = state,
        contentPadding = PaddingValues(horizontal = 22.dp, vertical = FocusRoom),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        itemsIndexed(shown, key = { _, v -> v.key }) { i, v ->
            val wanted = v.key == (target ?: keys.firstOrNull())
            VisitorChip(v, Modifier.takesFocus(ROUTE, v.key, wanted).riseIn(v.key, i.coerceAtMost(8), 14.dp)) { open(v) }
        }
    }
}

@Composable
private fun VisitorChip(v: Visitor, modifier: Modifier, open: () -> Unit) {
    val theme = avatarTheme(v.avatar)
    FocusCard(
        modifier.width(84.dp).height(104.dp), glow = theme, shape = RoundedCornerShape(18.dp), entry = true,
        onFocus = { PlazaBottom.visitor = v; Selection.current = Selected.Home },
        onLongPress = open,
        onClick = open,
    ) {
        Column(Modifier.fillMaxSize().background(Palette.Card)) {
            // The character, head and shoulders, kept inside its own window: the name below is on its own band.
            Box(Modifier.weight(1f).fillMaxWidth().clipToBounds().background(Brush.verticalGradient(listOf(theme.copy(alpha = 0.7f), theme.copy(alpha = 0.18f))))) {
                Canvas(Modifier.fillMaxSize()) {
                    // Zoomed out a little when a tall hat (a top hat, bunny ears) would leave the window.
                    val reach = 78f - (avatarTop(v.avatar) - 1f)
                    val u = kotlin.math.min(kotlin.math.min(size.width / 100f, size.height / 120f) * 1.55f, size.height * 0.98f / reach)
                    withTransform({ translate(size.width / 2f - 50f * u, size.height * 0.98f - 78f * u); scale(u, u, Offset.Zero) }) {
                        drawAvatar(v.avatar, shadow = false, box = Size(100f, 120f))
                    }
                }
                if (v.fresh) Box(
                    Modifier.align(Alignment.TopEnd).padding(5.dp).clip(RoundedCornerShape(50)).background(Palette.Peach).padding(horizontal = 6.dp, vertical = 1.dp),
                ) { Text(Tx.s(R.string.plaza_new_badge), style = MaterialTheme.typography.labelSmall, color = Palette.OnAccent, maxLines = 1) }
                if (v.wavedAtMe) Text("👋", Modifier.align(Alignment.TopStart).padding(5.dp), style = MaterialTheme.typography.labelLarge) // tx-ok: an emoji
            }
            Text(
                displayName(v.name), Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            )
        }
    }
}

// --- The square ----------------------------------------------------------------------------------------------------

/**
 * The square itself (PlazaArt.kt) with the crowd living on it (PlazaWorld.kt). One frame clock steps the world and
 * redraws the canvas, about 60 times a second however fast the screen refreshes; it rests while something covers the
 * screen or nobody has used Tandem for a while, and with Reduce motion everyone simply stands about, still.
 */
@Composable
private fun PlazaScene(crowd: List<Visitor>, focused: String?, me: AvatarSpec, modifier: Modifier) {
    val still = Themes.still || Store.settings.reduceMotion
    val on = rememberAmbient(!still)
    val world = remember { PlazaWorld(System.nanoTime()) }
    val painter = remember { PainterKeeper.take() }
    DisposableEffect(painter) { onDispose { PainterKeeper.give(painter) } }
    val accents = Palette.accents
    val look = remember(accents) { PainterKeeper.look(accents) }
    val frame = remember { mutableLongStateOf(0L) }
    val time = remember { floatArrayOf(0f) }
    var hour by remember { mutableFloatStateOf(hourNow()) }

    SideEffect {
        world.sync(me, crowd.map { it.key }, crowd.map { it.avatar }, crowd.map { it.fresh })
        world.focus(focused)
        if (still) world.arrange()
        frame.longValue++
    }
    // The light follows the clock.
    LaunchedEffect(Unit) { while (true) { delay(30_000); hour = hourNow() } }
    LaunchedEffect(on, still) {
        if (!on || still) return@LaunchedEffect
        var last = 0L
        while (true) {
            val resting = Ambient.covered || Ambient.resting
            if (resting) { last = 0L; delay(200); continue }
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else (now - last) / 1e9f
                // On a 120 Hz screen every other vsync is enough: half the work, the same smooth motion.
                if (last != 0L && dt < 0.0135f) return@withFrameNanos
                last = now
                world.step(dt)
                time[0] += dt.coerceAtMost(0.05f)
                frame.longValue = now
            }
        }
    }
    Canvas(modifier) {
        frame.longValue
        painter.draw(this, world, time[0], hour, look, still)
    }
}

/**
 * The square's painter, kept a minute after the Plaza goes: the town it paints once (two screen-sized pictures, drawn
 * in software on the main thread) is ready again when you come back, from Me (X and B) or soon after. Then it goes,
 * with its pictures. Main thread only.
 */
private object PainterKeeper {
    private var kept: PlazaPainter? = null
    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    private val drop = Runnable { kept = null }

    fun take(): PlazaPainter {
        main.removeCallbacks(drop)
        return kept?.also { kept = null } ?: PlazaPainter()
    }

    fun give(p: PlazaPainter) {
        kept = p
        main.removeCallbacks(drop)
        main.postDelayed(drop, 60_000)
    }

    private var look: PlazaLook? = null

    /** The look for [accents]: the same object while they stay, so the kept town isn't painted again. */
    fun look(accents: List<Color>): PlazaLook = look?.takeIf { it.accents == accents } ?: PlazaLook(accents).also { look = it }
}

/** The hour of the day now, with its minutes (14.5 is half past two in the afternoon). */
private fun hourNow(): Float = java.time.LocalTime.now().let { it.hour + it.minute / 60f }

// --- Someone's menu ------------------------------------------------------------------------------------------------

@Composable
private fun VisitorMenu(v: Visitor?, close: () -> Unit) {
    val shown = rememberLatest(v)
    val route = "$ROUTE:menu"
    PanelGuard(v != null)
    OverlayPanel(v != null, onClose = close) {
        val s = shown ?: return@OverlayPanel
        val live = PlazaStore.data.visitors.firstOrNull { it.key == s.key } ?: s
        val target = rememberFocusTarget(route, listOf("wave", "hide", "block"))
        val name = displayName(live.name)
        PanelSurface(Modifier.width(420.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 10.dp)) {
                Box(Modifier.size(52.dp).clip(CircleShape).background(avatarTheme(live.avatar).copy(alpha = 0.5f))) {
                    Canvas(Modifier.fillMaxSize()) {
                        val u = kotlin.math.min(kotlin.math.min(size.width / 100f, size.height / 120f) * 1.7f, size.height / (80f - (avatarTop(live.avatar) - 1f)))
                        withTransform({ translate(size.width / 2f - 50f * u, size.height - 80f * u); scale(u, u, Offset.Zero) }) {
                            drawAvatar(live.avatar, shadow = false, box = Size(100f, 120f))
                        }
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(name, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(Tx.s(greetingRes(live.greeting)), style = MaterialTheme.typography.bodyMedium, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MenuRow(
                    Tx.s(if (live.waved) R.string.plaza_menu_unwave else R.string.plaza_menu_wave), null, "wave", route, target, Palette.Butter, Glyph.Heart,
                ) {
                    val on = !live.waved
                    PlazaStore.update { PassBook.setWave(it, live.key, on) }
                    Sounds.toggle(on)
                    Toasts.show(if (on) Tx.s(R.string.plaza_waved_done, name) else Tx.s(R.string.plaza_unwaved_done))
                    close()
                }
                MenuRow(Tx.s(R.string.plaza_menu_hide), null, "hide", route, target, Palette.Lavender, Glyph.Eye) {
                    PlazaStore.update { PassBook.hide(it, live.key) }
                    Sounds.toggle(false)
                    close()
                    Undo.offer(Tx.s(R.string.plaza_hidden_done, name)) { PlazaStore.update { PassBook.unhide(it, live.key) } }
                    Nav.focusTick++
                }
                HoldMenuRow(Tx.s(R.string.plaza_menu_block), "block", route, target) {
                    val was = PlazaStore.data.visitors.firstOrNull { it.key == live.key }
                    PlazaStore.update { PassBook.block(it, live.key) }
                    close()
                    Undo.offer(Tx.s(R.string.plaza_blocked_done, name)) { PlazaStore.update { PassBook.unblock(it, live.key, was) } }
                    Nav.focusTick++
                }
            }
            Text(Tx.s(R.string.plaza_menu_note), style = MaterialTheme.typography.bodySmall, color = Palette.Muted, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 10.dp, start = 4.dp))
        }
    }
}

// --- Cards: the bottom screen ----------------------------------------------------------------------------------

/** The bottom screen while the Plaza or Me is open (BottomScreen.kt asks [PlazaBottom.active] first). */
@Composable
fun PlazaBottomView() {
    val v = PlazaBottom.visitor?.let { f -> PlazaStore.data.visitors.firstOrNull { it.key == f.key } ?: f }
    AnimatedContent(
        if (PlazaBottom.showMe) "me" else v?.key ?: "none", label = "plaza-bottom",
        transitionSpec = { fadeIn(Motion.fade(220)) togetherWith fadeOut(Motion.fade(160)) },
    ) { which ->
        Box(Modifier.fillMaxSize().padding(18.dp), contentAlignment = Alignment.Center) {
            when (which) {
                "me" -> MyCard(Modifier.fillMaxSize())
                "none" -> PlazaIdle()
                else -> v?.let { VisitorCard(it, Modifier.fillMaxSize()) }
            }
        }
    }
}

@Composable
private fun PlazaIdle() {
    val d = PlazaStore.data
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        AvatarView(d.me.avatar, Modifier.size(160.dp, 190.dp), waving = true, headroom = avatarHeadroom(d.me.avatar))
        Text(displayName(d.me.name), style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(Tx.p(R.plurals.plaza_your_passes, d.passes, d.passes), style = MaterialTheme.typography.bodyLarge, color = Palette.Muted, maxLines = 1)
        val (line, color) = statusLine(TandemPass.status)
        Pill(line, color)
    }
}

/** Your own card, as people you pass will see it. */
@Composable
fun MyCard(modifier: Modifier = Modifier) {
    val d = PlazaStore.data
    val (fav, recent) = remember(d.me, Libraries.all, Store.meta) { TandemPass.previewTitles() }
    PassCardView(d.me.avatar, d.me.name, d.me.greeting, fav, recent, modifier, lines = listOf(Tx.p(R.plurals.plaza_their_passes, d.passes, d.passes)), header = Tx.s(R.string.plaza_card_preview_header))
}

@Composable
private fun VisitorCard(v: Visitor, modifier: Modifier) {
    val times = Tx.p(R.plurals.plaza_passed_times, v.times, v.times)
    val last = lastLine(v.lastMet)
    val lines = buildList {
        add(listOf(times, last).joinParts())
        add(Tx.p(R.plurals.plaza_their_passes, v.theirPasses, v.theirPasses))
        add(Tx.s(R.string.plaza_first_met, Dates.text(v.firstMet, DateKind.Added)))
    }
    val badge = when {
        v.wavedAtMe -> Tx.s(R.string.plaza_waved_at_you)
        v.waved && v.waveSent -> Tx.s(R.string.plaza_wave_sent)
        v.waved -> Tx.s(R.string.plaza_you_waved)
        else -> null
    }
    PassCardView(v.avatar, v.name, v.greeting, v.favorite, v.recent, modifier, lines = lines, badge = badge, react = v.lastMet)
}

/** "last at 18:40" today, "last 21 Nov" before. */
private fun lastLine(ms: Long): String {
    val day = java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
    return if (day == java.time.LocalDate.now()) Tx.s(R.string.plaza_last_at, android.text.format.DateFormat.getTimeFormat(Tx.context).format(java.util.Date(ms)))
    else Tx.s(R.string.plaza_last_on, Dates.text(ms, DateKind.DayMonth))
}

/**
 * A Tandem Pass card, big: the character waving in its own colour, the greeting in a bubble, the name, the games
 * (with their art when the game is in your library too), and a few lines about the passes.
 */
@Composable
fun PassCardView(
    avatar: AvatarSpec, name: String, greeting: Int, favorite: String?, recent: String?, modifier: Modifier = Modifier,
    lines: List<String> = emptyList(), badge: String? = null, header: String? = null, react: Any? = null,
) {
    val theme = avatarTheme(avatar)
    Column(
        modifier.clip(RoundedCornerShape(28.dp))
            .background(Brush.verticalGradient(listOf(theme.copy(alpha = 0.6f), Palette.Card, Palette.Card)))
            .border(2.dp, theme.copy(alpha = 0.8f), RoundedCornerShape(28.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (header != null) Text(header, style = MaterialTheme.typography.labelLarge, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        GreetingBubble(Tx.s(greetingRes(greeting)), theme, Modifier.padding(top = 4.dp))
        Box(Modifier.weight(1f).fillMaxWidth()) {
            AvatarView(avatar, Modifier.fillMaxSize().padding(vertical = 4.dp), react = react, waving = true, headroom = avatarHeadroom(avatar))
            if (badge != null) Box(Modifier.align(Alignment.TopEnd).clip(RoundedCornerShape(50)).background(Palette.Butter).padding(horizontal = 10.dp, vertical = 3.dp)) {
                FitText(badge, MaterialTheme.typography.labelMedium, color = Palette.OnAccent)
            }
        }
        Text(displayName(name), style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        for (l in lines) Text(l, style = MaterialTheme.typography.bodyMedium, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (favorite != null || recent != null) {
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (favorite != null) GameLine(Tx.s(R.string.plaza_fav_label), favorite, Palette.Peach, Modifier.weight(1f))
                if (recent != null) GameLine(Tx.s(R.string.plaza_recent_label), recent, Palette.Sky, Modifier.weight(1f))
            }
        }
    }
}

/** A game on a card: its art when you have it, its title, and what it is to them. */
@Composable
private fun GameLine(label: String, title: String, color: Color, modifier: Modifier) {
    val game = remember(title, Libraries.all) { PlazaGames.find(title) }
    Row(modifier.clip(RoundedCornerShape(14.dp)).background(color.copy(alpha = 0.16f)).padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(9.dp)).background(Palette.CardHigh)) {
            if (game != null) GameArt(game) else TandemIconTile(TIcon.Controller, color)
        }
        Column(Modifier.padding(start = 8.dp).weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(title, style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** A card's game title in your own library, matched loosely (case, punctuation and region tags aside); never a hidden one. */
object PlazaGames {
    private var cache: Pair<Any?, Map<String, Game>>? = null

    fun key(title: String): String = title.lowercase().replace(Regex("""[(\[].*?[)\]]"""), "").filter { it.isLetterOrDigit() }

    fun find(title: String): Game? {
        val libs = Libraries.all
        val map = cache?.takeIf { it.first === libs }?.second ?: Libraries.games.filter { !Privacy.isHidden(it) }
            .groupBy { key(it.title) }.mapValues { it.value.first() }.also { cache = libs to it }
        return map[key(title)]
    }
}

/** The Plaza tile's badge: how many people are new there (nothing in private mode or when there are none). */
@Composable
fun PlazaBadge(size: androidx.compose.ui.unit.Dp, modifier: Modifier) {
    if (Privacy.enabled) return
    val n = PassBook.newCount(PlazaStore.data)
    if (n == 0) return
    val d = (size.value * 0.3f).coerceIn(16f, 30f).dp
    val sp = with(androidx.compose.ui.platform.LocalDensity.current) { (d * 0.56f).toSp() }
    Box(
        modifier.padding(size * 0.05f).size(d).clip(CircleShape).background(Color(0xFFFF5A6E)).border(2.dp, Color.White, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (n > 9) "9+" else "$n", color = Color.White, maxLines = 1,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = sp, lineHeight = sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Black),
        )
    }
}

// --- Arrivals anywhere ---------------------------------------------------------------------------------------------

/**
 * Someone new passed while Tandem is in front but not on the Plaza: the Passby jingle and a toast that opens the
 * Plaza. Composed once at the root of the library.
 */
@Composable
fun PlazaArrivals() {
    // Private mode coming on pauses passing at once (and going off lets it pass again).
    val ctx = LocalContext.current
    val private = Privacy.enabled
    LaunchedEffect(private) { TandemPass.refresh(ctx) }
    val arrivals = TandemPass.arrivals
    val seen = remember { intArrayOf(arrivals) }
    LaunchedEffect(arrivals) {
        if (arrivals == seen[0]) return@LaunchedEffect
        seen[0] = arrivals
        if (Nav.route == Route.Tool(PlazaUi.ID) || Privacy.enabled) return@LaunchedEffect
        Sounds.goodNews()
        val name = TandemPass.lastArrival?.let { displayName(it) } ?: return@LaunchedEffect
        Toasts.show(Tx.s(R.string.plaza_arrival, name), Toasts.DEFAULT_MS + 1500, Toasts.Action(Tx.s(R.string.plaza_arrival_open)) { PlazaUi.open() })
    }
}
