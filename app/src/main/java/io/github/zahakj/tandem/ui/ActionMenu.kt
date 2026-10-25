package io.github.zahakj.tandem.ui

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/*
 * The Y menus (game, view, icon grid tile, dock app): the highlighted thing lifts out of
 * where it sits and grows a little over a blurred, dimmed screen, its name under it, with a short floating list of
 * actions beside it (icon + label rows; the highlighted row glows with a gradient outline). Primary actions come
 * first; the rest sit under "More…", which scrolls on a short screen. B steps back (More → main → closed) and the
 * item settles back into its place.
 */

/** One action in a Y menu. */
class MenuAction(
    val id: String,
    val label: String,
    val glyph: Glyph? = null,
    val color: Color = Palette.Mint,
    /** A short value shown on the right ("On", "Bottom screen"). */
    val value: String? = null,
    /** Listed under More… instead of the main list. */
    val more: Boolean = false,
    val icon: TIcon? = null,
    /** A second, quieter line under the label ("System · 12 games"). */
    val sub: String? = null,
    /** A picture in place of the glyph (a cover, a system icon), clipped to the icon's rounded square. */
    val art: (@Composable () -> Unit)? = null,
    /** Called when the row is highlighted. */
    val onFocus: (() -> Unit)? = null,
    /** What [value] means, shown when the row is long-pressed (touch has no other way to ask). */
    val help: String? = null,
    /** Can't be undone: the row acts only when A (or a finger) is held until its ring fills (HoldConfirm.kt). */
    val hold: Boolean = false,
    val onClick: () -> Unit,
)

/** A page shown in place of the actions (picking a folder), with its own Back. */
class MenuPage(val title: String, val actions: List<MenuAction>, val onBack: () -> Unit)

/** What a Y menu shows. [art] draws the lifted item (it is clipped to a rounded shape). */
class MenuSpec(
    val routeKey: String,
    val title: String,
    val subtitle: String?,
    val color: Color,
    val actions: List<MenuAction>,
    val page: MenuPage? = null,
    /** The lifted item's shape: square tiles, tall boxes, wide cards. */
    val aspect: Float = 1f,
    val art: @Composable () -> Unit,
)

object ActionMenu {
    /** Where the highlighted item is on screen (set by cards and tiles as they take focus). */
    var origin: LayoutCoordinates? = null
    /** The highlighted item's corner radius as a share of its width, for the lift. */
    var originRound: Float = 0.16f

    /** How blurred the screen behind a Y menu is (0..1). Each source owns its own value; [menuBackdrop] uses the largest. */
    val depth = mutableFloatStateOf(0f)
    /** The Start panel's share of the blur, kept apart so one closing popup can never leave the other's blur behind. */
    val panelDepth = mutableFloatStateOf(0f)

    /** Closes every Y menu (Home pressed, a launch). */
    fun dismiss() {
        Nav.menuFor = null; Nav.groupMenu = false
        IconGrid.menu = null; IconGrid.choosingFolder = false
        Panels.appMenu = null
    }

    /** Remembers [coords] as the place a Y menu lifts the item from. */
    fun focusAt(coords: LayoutCoordinates?, round: Float = 0.16f) {
        if (coords != null) { origin = coords; originRound = round }
    }
}

/** For screen content behind Y menus: blurs it (Android 12+) while a menu is open. */
fun Modifier.menuBackdrop(): Modifier = graphicsLayer {
    val d = maxOf(ActionMenu.depth.floatValue, ActionMenu.panelDepth.floatValue)
    renderEffect = if (d > 0.02f && Build.VERSION.SDK_INT >= 31) {
        val r = 18f * d * density
        BlurEffect(r, r, TileMode.Decal)
    } else null
}

/**
 * Records this item's place on screen while it has focus, so a Y menu can lift it from there. Cheap: only a field
 * write per layout, nothing recomposes.
 */
fun Modifier.liftOrigin(round: Float = 0.16f): Modifier {
    val holder = arrayOfNulls<LayoutCoordinates>(1)
    var focused = false
    return onGloballyPositioned { holder[0] = it; if (focused) ActionMenu.focusAt(it, round) }
        .onFocusChanged { focused = it.isFocused || it.hasFocus; if (focused) ActionMenu.focusAt(holder[0], round) }
}

/**
 * A Y menu. While [visible] it is the top focus layer; [spec] is read every composition (keep its data with
 * [rememberLatest] so it can animate away). [onClose] runs for B on the main page and taps outside.
 */
@Composable
fun ActionMenuHost(visible: Boolean, onClose: () -> Unit, spec: @Composable () -> MenuSpec?) {
    val layer = rememberLayer(visible)
    val open by rememberUpdatedState(visible)
    var more by remember { mutableStateOf(false) }
    val s = spec()?.let { SmartFolders.extendMenu(it) }
    LaunchedEffect(visible) { if (visible) more = false }
    // The screen behind blurs in as the menu opens and clears as it closes.
    val depth = remember { Animatable(0f) }
    LaunchedEffect(visible) {
        depth.animateTo(if (visible) 1f else 0f, if (Motion.style == AnimationStyle.Off) tween(0) else tween(if (visible) 260 else 200))
    }
    LaunchedEffect(Unit) { snapshotDepth(depth) }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { ActionMenu.depth.floatValue = 0f } }
    if (visible) BackHandler {
        when {
            s?.page != null -> s.page.onBack()
            more -> { more = false; FocusMemory.forget(s?.routeKey + ":more") }
            else -> onClose()
        }
    }
    AnimatedVisibility(visible, enter = fadeIn(tween(160)), exit = fadeOut(tween(200))) {
        Box(Modifier.fillMaxSize().background(Palette.NightDeep.copy(alpha = if (Build.VERSION.SDK_INT >= 31) 0.42f else 0.66f)).clickable(onClick = onClose))
    }
    AnimatedVisibility(visible, enter = fadeIn(tween(1)), exit = fadeOut(tween(260))) {
        val still = Motion.style == AnimationStyle.Off
        val lift = transition.animateFloat(
            { if (still) tween(0) else if (targetState == EnterExitState.Visible) spring(0.72f, Spring.StiffnessMediumLow) else tween(240) },
            label = "lift",
        ) { if (it == EnterExitState.Visible) 1f else 0f }
        val m = s ?: return@AnimatedVisibility
        ProvideLayer(layer) {
            Box(
                Modifier.fillMaxSize()
                    .onFocusChanged { Layers.focusIn(layer, it.hasFocus) }
                    .onPreviewKeyEvent { !open && it.isNavKey() }
                    .trapFocus()
                    // A tap outside the item and the list closes the menu, like the dim behind it.
                    .swallowTaps(onClose),
            ) { MenuLayout(m, more, { more = it }, lift::value) }
        }
    }
}

private suspend fun snapshotDepth(depth: Animatable<Float, *>) {
    androidx.compose.runtime.snapshotFlow { depth.value }.collect { ActionMenu.depth.floatValue = it }
}

@Composable
private fun MenuLayout(s: MenuSpec, more: Boolean, setMore: (Boolean) -> Unit, lift: () -> Float) {
    val density = LocalDensity.current
    var self by remember { mutableStateOf(Offset.Zero) }
    // Where the item sits now (captured once per opening, relative to this layer).
    val from: Rect? = remember(s.routeKey) {
        ActionMenu.origin?.takeIf { it.isAttached }?.boundsInRoot()?.takeIf { it.width > 4f && it.height > 4f }
    }
    BoxWithConstraints(Modifier.fillMaxSize().onGloballyPositioned { self = it.positionInRoot() }) {
        val wPx = constraints.maxWidth.toFloat()
        val hPx = constraints.maxHeight.toFloat()
        val margin = with(density) { PanelMargin.toPx() }
        val listW = with(density) { 290.dp.toPx() }
        val gap = with(density) { 22.dp.toPx() }
        val nameH = with(density) { 54.dp.toPx() }
        // The item: where it was, or a sensible spot when it isn't known.
        val src = from?.translate(-self.x, -self.y) ?: run {
            val side = with(density) { 150.dp.toPx() }
            Rect(Offset(wPx * 0.28f - side / 2, hPx / 2 - side / 2), Size(side, side / s.aspect))
        }
        // It grows a little, but never past the room left beside the list.
        val maxW = (wPx - listW - gap - margin * 3).coerceAtLeast(80f).coerceAtMost(with(density) { 260.dp.toPx() })
        val maxH = (hPx - nameH - margin * 2).coerceAtLeast(80f)
        val grow = minOf(1.22f, maxW / src.width, maxH / src.height).coerceAtLeast(0.45f)
        val tw = src.width * grow
        val th = src.height * grow
        // The list goes on whichever side has more room; the item slides over just enough to make space.
        val listRight = src.center.x < wPx / 2
        var cx = src.center.x
        val minCx = margin + if (listRight) tw / 2 else listW + gap + tw / 2
        val maxCx = wPx - margin - if (listRight) tw / 2 + gap + listW else tw / 2
        cx = cx.coerceIn(minCx, maxCx.coerceAtLeast(minCx))
        val cy = src.center.y.coerceIn(margin + th / 2, (hPx - margin - nameH - th / 2).coerceAtLeast(margin + th / 2))
        val target = Rect(Offset(cx - tw / 2, cy - th / 2), Size(tw, th))
        val round = ActionMenu.originRound
        // The lifted item: drawn at its own size and moved and scaled in a layer, so nothing re-lays out per frame.
        Box(
            Modifier.offset { IntOffset(src.left.roundToInt(), src.top.roundToInt()) }
                .size(with(density) { src.width.toDp() }, with(density) { src.height.toDp() })
                .graphicsLayer {
                    val p = lift()
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
                    translationX = (target.left - src.left) * p
                    translationY = (target.top - src.top) * p
                    val sc = 1f + (grow - 1f) * p
                    scaleX = sc; scaleY = sc
                    shape = RoundedCornerShape(percent = (round * 100).roundToInt().coerceIn(4, 50))
                    clip = true
                    shadowElevation = 26f * this.density * p
                    spotShadowColor = s.color
                    ambientShadowColor = s.color
                },
        ) { s.art() }
        // Its name under it.
        Column(
            Modifier.offset { IntOffset((target.center.x - with(density) { 150.dp.toPx() }).roundToInt(), (target.bottom + with(density) { 10.dp.toPx() }).roundToInt()) }
                .width(300.dp).graphicsLayer { alpha = lift().coerceIn(0f, 1f); translationY = (1f - lift()) * 16f * this.density },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(s.title, style = MaterialTheme.typography.titleLarge.onArt(), maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            if (!s.subtitle.isNullOrBlank()) Text(s.subtitle, style = MaterialTheme.typography.labelLarge, color = lerp(s.color, Palette.Text, 0.4f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        // The actions, floating beside it.
        val listX = if (listRight) target.right + gap else target.left - gap - listW
        val listMaxH = hPx - margin * 2
        Box(
            Modifier.offset { IntOffset(listX.roundToInt(), 0) }.width(with(density) { listW.toDp() }).fillMaxSize()
                .padding(vertical = PanelMargin),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                Modifier.graphicsLayer {
                    val p = lift().coerceIn(0f, 1f)
                    alpha = p
                    translationX = (1f - p) * (if (listRight) -40f else 40f) * this.density
                    // Line the list's middle up with the item, as far as the screen allows.
                    val wanted = cy - hPx / 2
                    val room = (listMaxH - size.height) / 2f
                    translationY = wanted.coerceIn(-room.coerceAtLeast(0f), room.coerceAtLeast(0f))
                },
            ) { ActionList(s, more, setMore) }
        }
    }
}

/** The floating card of action rows: the main page, More…, or the caller's own page. */
@Composable
private fun ActionList(s: MenuSpec, more: Boolean, setMore: (Boolean) -> Unit) {
    val page = s.page
    val mainActions = s.actions.filter { !it.more }
    val moreActions = s.actions.filter { it.more }
    val pageKey = when { page != null -> "page:${page.title}"; more -> "more"; else -> "main" }
    Column(
        Modifier.widthIn(max = 290.dp).fillMaxWidth()
            .graphicsLayer { shape = RoundedCornerShape(24.dp); clip = true; shadowElevation = 22f * density }
            .background(Palette.Card.copy(alpha = 0.97f)).swallowTaps().padding(vertical = 10.dp),
    ) {
        Row(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Pip(18.dp, bounce = false)
            Spacer(Modifier.width(8.dp))
            Text(
                when { page != null -> page.title; more -> "More"; else -> "Options" },
                style = MaterialTheme.typography.labelLarge, color = Palette.Muted, maxLines = 1, modifier = Modifier.weight(1f),
            )
            ButtonHint("B", if (page != null || more) "Back" else "Close", Palette.Lavender)
        }
        AnimatedContent(
            pageKey,
            // Takes what's left of the card, so a long page scrolls instead of running off a short screen.
            Modifier.weight(1f, fill = false),
            transitionSpec = {
                val forward = targetState != "main"
                if (Motion.style == AnimationStyle.Off) fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                else (slideInHorizontally(tween(220)) { if (forward) it / 3 else -it / 3 } + fadeIn(tween(180)))
                    .togetherWith(slideOutHorizontally(tween(160)) { if (forward) -it / 4 else it / 4 } + fadeOut(tween(120)))
            },
            label = "menu-page",
        ) { key ->
            val routeKey = s.routeKey + ":" + key
            val rows: List<MenuAction> = when {
                key.startsWith("page:") && page != null -> page.actions
                key == "more" -> listOf(MenuAction("back", "Back", Glyph.Back, Palette.Lavender) { setMore(false) }) + moreActions
                else -> mainActions + listOfNotNull(
                    MenuAction("more", "More…", Glyph.More, Palette.Sky, value = "${moreActions.size}") { FocusMemory.forget(s.routeKey + ":more"); setMore(true) }
                        .takeIf { moreActions.isNotEmpty() },
                )
            }
            val target = rememberFocusTarget(routeKey, rows.map { it.id })
            // Room inside the page (AnimatedContent clips) for the focused row's halo and growth, first and last rows too.
            // Scrolls when the page is taller than the screen allows; the focused row is brought into view.
            Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 9.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                rows.forEachIndexed { i, a -> ActionRow(a, routeKey, a.id == target, i) }
            }
        }
    }
}

/** One action: an icon chip, the label and maybe a value. Highlighted, it glows with a gradient outline. */
@Composable
internal fun ActionRow(a: MenuAction, routeKey: String, wanted: Boolean, index: Int, modifier: Modifier = Modifier, rise: Boolean = true) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    var pressed by remember { mutableStateOf(false) }
    val glow = remember { Animatable(0f) }
    LaunchedEffect(focused) { glow.animateTo(if (focused) 1f else 0f, if (Motion.style == AnimationStyle.Off) tween(0) else tween(160)) }
    val color = a.color
    val density = LocalDensity.current
    val stroke = with(density) { 2.dp.toPx() }
    val click = {
        a.onClick()
        Sounds.select()
    }
    val requester = remember { FocusRequester() }
    val haptic = LocalHapticFeedback.current
    // A row that can't be undone acts only when held; its icon gets the ring and the row fills as it goes.
    val hold = if (a.hold) rememberHold(holdHint(a.label.lowercase().removeSuffix("…"))) { a.onClick() } else null
    Row(
        modifier.fillMaxWidth().height(46.dp).then(if (rise) Modifier.riseIn(routeKey, index, 10.dp, 0.96f) else Modifier)
            .takesFocus(routeKey, a.id, wanted)
            .focusRequester(requester)
            .then(if (hold != null) Modifier.holdToConfirm(hold, requester).holdFill(hold, color, 18.dp) else Modifier)
            .onFocusChanged { if (it.isFocused) { Sounds.move(); a.onFocus?.invoke() } else pressed = false }
            .graphicsLayer {
                val g = glow.value
                val sc = if (pressed) 0.97f else 1f + 0.02f * g
                scaleX = sc; scaleY = sc
            }
            .drawWithCache {
                val r = CornerRadius(size.height / 2.6f)
                val fill = Brush.horizontalGradient(listOf(color.copy(alpha = 0.26f), color.copy(alpha = 0.08f)))
                val ring = Brush.linearGradient(listOf(lerp(color, Color.White, 0.55f), color, lerp(color, Color.White, 0.3f)), Offset.Zero, Offset(size.width, size.height))
                val halo = Brush.horizontalGradient(listOf(color.copy(alpha = 0.22f), Color.Transparent))
                onDrawBehind {
                    val g = glow.value
                    if (g > 0.01f) {
                        drawRoundRect(halo, Offset(-stroke * 2, -stroke * 2), Size(size.width + stroke * 4, size.height + stroke * 4), CornerRadius(r.x + stroke * 2), alpha = g * 0.8f)
                        drawRoundRect(fill, cornerRadius = r, alpha = g)
                        drawRoundRect(ring, Offset(stroke / 2, stroke / 2), Size(size.width - stroke, size.height - stroke), r, style = Stroke(stroke), alpha = g)
                    }
                }
            }
            .focusable(interactionSource = interaction)
            .onKeyEvent { e ->
                val select = e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.NumPadEnter || e.key == Key.ButtonA
                if (!select) return@onKeyEvent false
                val was = pressed
                pressed = e.type == KeyEventType.KeyDown
                if (e.type == KeyEventType.KeyUp && was) click()
                true
            }
            .pointerInput(a) {
                detectTapGestures(
                    onPress = { pressed = true; tryAwaitRelease(); pressed = false },
                    // A row is a control: the first tap acts, and the highlight follows the finger.
                    onTap = { runCatching { requester.requestFocus() }; click() },
                    // A long press explains the row's value, when it has an explanation; otherwise nothing happens.
                    onLongPress = if (a.help == null) null else { _: Offset ->
                        Touch.buzz(haptic)
                        runCatching { requester.requestFocus() }
                        Toasts.show(a.help, 5000)
                    },
                )
            }
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(32.dp).graphicsLayer { shape = RoundedCornerShape(if (hold != null) 16.dp else 10.dp); clip = true }
                .background(color.copy(alpha = 0.2f)).then(if (hold != null) Modifier.holdArc(hold, color) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            when {
                a.art != null -> a.art.invoke()
                a.icon != null -> Box(Modifier.size(22.dp)) { TIconGlyph(a.icon, color) }
                a.glyph != null -> Box(Modifier.size(20.dp)) { GlyphIcon(a.glyph, color) }
            }
        }
        Spacer(Modifier.width(10.dp))
        if (a.sub == null) {
            Text(a.label, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        } else {
            Column(Modifier.weight(1f)) {
                Text(a.label, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(a.sub, style = MaterialTheme.typography.labelSmall, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (a.value != null) {
            Spacer(Modifier.width(6.dp))
            Text(
                a.value, Modifier.widthIn(max = 110.dp).background(color.copy(alpha = 0.18f), RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 2.dp),
                style = MaterialTheme.typography.labelMedium, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** The lifted picture for a menu about a game. */
@Composable
fun MenuGameArt(game: io.github.zahakj.tandem.Game) = GameArt(game)

