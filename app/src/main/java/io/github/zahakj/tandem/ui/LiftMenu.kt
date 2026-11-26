package io.github.zahakj.tandem.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.zahakj.tandem.data.Store
import kotlin.math.roundToInt

/*
 * The icon grid's lift (Y on a tile or an empty spot). The tile rises and grows a little right where it sits (it never
 * slides away), the grid behind blurs and dims, and a short card floats beside it: Open and Edit on a tile; on an empty
 * spot a few groups (New ›, Sort ›…) whose second level slides in where the card is. One highlight serves the whole
 * card: it appears as a small circle on the first row's icon, stretches into the full bar, and glides from row to row.
 * Pip sits on the card's top edge. B (or Y) puts everything back the way it came. See [MenuSpec.compact].
 */

/** The lifted item, its name, and the card beside it, all placed in window coordinates where the item sits. */
@Composable
internal fun LiftLayout(s: MenuSpec, lift: () -> Float) {
    val density = LocalDensity.current
    var self by remember { mutableStateOf(Offset.Zero) }
    val rtl = isRtl
    val from: Rect? = remember(s.routeKey) {
        ActionMenu.origin?.takeIf { it.isAttached }?.boundsInRoot()?.takeIf { it.width > 4f && it.height > 4f }
    }
    BoxWithConstraints(Modifier.fillMaxSize().onGloballyPositioned { self = it.positionInRoot() }, contentAlignment = AbsoluteAlignment.TopLeft) {
        val wPx = constraints.maxWidth.toFloat()
        val hPx = constraints.maxHeight.toFloat()
        fun px(d: androidx.compose.ui.unit.Dp) = with(density) { d.toPx() }
        val margin = px(14.dp)
        val listW = px(LiftCardWidth)
        val gap = px(20.dp)
        val nameH = px(46.dp)
        val src = from?.translate(-self.x, -self.y) ?: run {
            val side = px(120.dp)
            Rect(Offset(wPx * (if (rtl) 0.66f else 0.34f) - side / 2, hPx / 2 - side / 2), Size(side, side / s.aspect))
        }
        // It grows where it is, by up to a fifth and never past 190 dp, kept on the screen with room for its name.
        val grow = minOf(1.18f, px(190.dp) / src.width.coerceAtLeast(1f)).coerceAtLeast(1f)
        val tw = src.width * grow
        val th = src.height * grow
        val cx = src.center.x.coerceIn(margin + tw / 2, (wPx - margin - tw / 2).coerceAtLeast(margin + tw / 2))
        val cy = src.center.y.coerceIn(margin + th / 2, (hPx - margin - nameH - th / 2).coerceAtLeast(margin + th / 2))
        val target = Rect(Offset(cx - tw / 2, cy - th / 2), Size(tw, th))
        // The card goes on the end side (the right; the left in right to left) when it fits there, else on the side
        // with more room, and stays on the screen whatever happens.
        val roomRight = wPx - target.right - gap - margin
        val roomLeft = target.left - gap - margin
        val right = if ((if (rtl) roomLeft else roomRight) >= listW) !rtl else roomRight >= roomLeft
        // An empty spot shows no item: the card opens right where the spot is, from its edge.
        val near = if (s.showItem) (if (right) target.right + gap else target.left - gap - listW) else (if (right) src.left else src.right - listW)
        val listX = near.coerceIn(margin, (wPx - margin - listW).coerceAtLeast(margin))
        val round = ActionMenu.originRound
        // The lifted item: drawn at its own size and moved and scaled in a layer, so nothing re-lays out per frame.
        if (s.showItem) Box(
            Modifier.absoluteOffset { IntOffset(src.left.roundToInt(), src.top.roundToInt()) }
                .size(with(density) { src.width.toDp() }, with(density) { src.height.toDp() })
                .graphicsLayer {
                    val p = lift()
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
                    translationX = (target.left - src.left) * p
                    translationY = (target.top - src.top) * p
                    val sc = 1f + (grow - 1f) * p
                    scaleX = sc; scaleY = sc
                    shape = RoundedCornerShape(percent = (round * 100).roundToInt().coerceIn(0, 50))
                    clip = true
                    shadowElevation = 30f * this.density * p.coerceIn(0f, 1.2f)
                    spotShadowColor = s.color
                    ambientShadowColor = s.color
                },
        ) { s.art() }
        // Its name under it, on the screen and clear of the card.
        if (s.showItem) {
            val nameW = maxOf(tw, px(200.dp)).coerceAtMost(wPx - margin * 2)
            val nameX = (target.center.x - nameW / 2).coerceIn(margin, wPx - margin - nameW)
            Column(
                Modifier.absoluteOffset { IntOffset(nameX.roundToInt(), (target.bottom + px(8.dp)).roundToInt()) }
                    .width(with(density) { nameW.toDp() })
                    .graphicsLayer { val p = lift().coerceIn(0f, 1f); alpha = p; translationY = (1f - p) * 12f * this.density },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ShrinkText(s.title, MaterialTheme.typography.titleMedium.onArt(), maxLines = 1, textAlign = TextAlign.Center)
                if (!s.subtitle.isNullOrBlank()) Text(
                    s.subtitle, style = MaterialTheme.typography.labelMedium.onArt(), color = lerp(s.color, Palette.Text, 0.45f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Box(
            Modifier.absoluteOffset { IntOffset(listX.roundToInt(), 0) }.width(LiftCardWidth).fillMaxHeight().padding(vertical = PanelMargin + 22.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            val perch = px(pipPerch(s.pip))
            Box(
                Modifier.graphicsLayer {
                    // The card (under Pip's perch) lined up with the item's middle, as far as the screen allows; Pip
                    // always stays on the screen with it.
                    val room = ((hPx - (PanelMargin + 22.dp).toPx() * 2) - size.height) / 2f
                    translationY = (cy - hPx / 2 - perch / 2).coerceIn(-room.coerceAtLeast(0f), room.coerceAtLeast(0f))
                },
            ) { LiftCard(s, right, lift) }
        }
    }
}

private val LiftCardWidth = 236.dp
private val LiftRowHeight = 44.dp
private val RowShape = RoundedCornerShape(percent = 50)

/**
 * A row's picture of its value (the edit screen's artwork, colour, hero and logo): 7 dp clear of the highlight's top
 * and bottom, and [RowEndRoom] in from its end, where the pill's round end (a 22 dp radius) is still 39 dp tall, so the
 * picture's corners stay inside the ring with room to spare.
 */
private val RowPictureHeight = LiftRowHeight - 14.dp
private val RowEndRoom = 12.dp

/** How big Pip is on a compact menu: a friend beside a tile, the host on an empty spot. */
private fun pipSize(p: PipPeek) = when (p) {
    PipPeek.None -> 0.dp
    PipPeek.Small -> 56.dp
    PipPeek.Big -> 76.dp
}

/** The room Pip takes above the card: its whole height, but for the bottom edge tucked behind the card's. */
private fun pipPerch(p: PipPeek) = pipSize(p) * 0.86f

/** The card: its rows (a group's or a page's when one is open) and Pip sitting on its top edge. */
@Composable
private fun LiftCard(s: MenuSpec, fromRight: Boolean, lift: () -> Float) {
    val group = ActionMenu.group?.let { id -> s.actions.firstOrNull { it.id == id && it.children != null } }
    val pool = group?.children ?: s.actions
    val choosing = ActionMenu.choosing?.let { id -> pool.firstOrNull { it.id == id && it.choices != null } }
    val page = s.page
    val level = when {
        choosing != null -> "choose:${choosing.id}"
        page != null -> "page:${page.title}"
        group != null -> "group:${group.id}"
        else -> "main"
    }
    var focusedAction by remember { mutableStateOf<MenuAction?>(null) }
    // Each lift starts on its first row (Open), as the highlight's circle first appears there.
    remember(s.routeKey) { FocusMemory.forget(s.routeKey + ":main"); true }
    val still = Motion.style == AnimationStyle.Off || Store.settings.reduceMotion
    Column {
        if (s.pip != PipPeek.None) {
            // Worried over something that can't be taken back, excited inside a group, happy otherwise (PipMoods).
            val mood = PipMoods.lift(focusedAction?.id, deeper = level != "main")
            PerchedPip(s.pip, mood, level, fromRight, still, lift)
        }
        Column(
            Modifier.fillMaxWidth()
                .graphicsLayer {
                    val p = lift().coerceIn(0f, 1f)
                    alpha = p
                    translationX = (1f - p) * (if (fromRight) -34f else 34f) * density
                    val sc = 0.94f + 0.06f * p
                    scaleX = sc; scaleY = sc
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(if (fromRight) 0f else 1f, 0.5f)
                    shape = RoundedCornerShape(22.dp); clip = true; shadowElevation = 22f * density
                }
                .background(Palette.Card.copy(alpha = 0.97f))
                .swallowTaps()
                .animateContentSize(if (still) tween(0) else spring(0.86f, 520f)),
        ) {
            val toEnd = if (isRtl) -1 else 1
            AnimatedContent(
                level,
                transitionSpec = {
                    val forward = targetState != "main" && (initialState == "main" || targetState.startsWith("choose:") || targetState.startsWith("page:"))
                    if (still) fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                    else (slideInHorizontally(tween(230)) { (if (forward) it / 2 else -it / 2) * toEnd } + fadeIn(tween(170)))
                        .togetherWith(slideOutHorizontally(tween(170)) { (if (forward) -it / 3 else it / 3) * toEnd } + fadeOut(tween(110)))
                },
                label = "lift-level",
            ) { key ->
                val routeKey = s.routeKey + ":" + key
                val back: (String) -> MenuAction = { title -> MenuAction("back", title, Glyph.Back, Palette.Lavender) {
                    when {
                        key.startsWith("choose:") -> ActionMenu.choosing = null
                        key.startsWith("page:") -> page?.onBack?.invoke()
                        else -> ActionMenu.group = null
                    }
                } }
                val rows: List<MenuAction> = when {
                    key.startsWith("choose:") && choosing != null -> listOf(back(choosing.label)) + choosing.choices.orEmpty().mapIndexed { i, c ->
                        MenuAction("ch:$i", c.label, c.glyph ?: choosing.glyph, choosing.color, value = if (c.selected) "✓" else null, sub = c.sub, art = c.art) {
                            c.pick()
                            ActionMenu.choosing = null
                        }
                    }
                    key.startsWith("page:") && page != null -> listOf(back(page.title)) + page.actions
                    key.startsWith("group:") && group != null -> listOf(back(group.label)) + group.children.orEmpty()
                    else -> s.actions
                }
                LiftRows(routeKey, rows, onFocus = { focusedAction = it })
            }
            // What else the button does here (hold to pick the tile up), on the first level only.
            if (s.footer != null && level == "main") Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) { ButtonHint("Y", s.footer, Palette.Muted, tappable = false) }
        }
    }
}

/**
 * Pip, sitting on the card's top edge at its far end (away from the item), whole and in plain sight, above the rows so
 * it never covers a word or the highlight. It pops up from behind the card a moment after the card arrives, with a
 * little wobble, and ducks back behind it as it goes; it blinks and bobs while it waits (Pip's own idle), leans in
 * towards the card, hops when a group or page opens, and looks worried over a row that can't be taken back. Reduce
 * motion: simply there, still. The side follows the card, so it mirrors with the layout in right to left.
 */
@Composable
private fun PerchedPip(peek: PipPeek, mood: Int, level: String, fromRight: Boolean, still: Boolean, lift: () -> Float) {
    val size = pipSize(peek)
    // The host on an empty spot greets you; any Pip hops when a group or a page slides in.
    LaunchedEffect(Unit) {
        if (still || peek != PipPeek.Big) return@LaunchedEffect
        kotlinx.coroutines.delay(320)
        PipMind.cheer()
    }
    val firstLevel = remember { level }
    LaunchedEffect(level) { if (!still && level != firstLevel) PipMind.cheer() }
    // As tall as its perch; drawn before (so behind) the card, whose top edge hides Pip's bottom one.
    Box(Modifier.fillMaxWidth().height(pipPerch(peek))) {
        Pip(
            size, mood = mood,
            modifier = Modifier.align(if (fromRight) AbsoluteAlignment.BottomRight else AbsoluteAlignment.BottomLeft)
                .absoluteOffset(x = if (fromRight) (-16).dp else 16.dp)
                .graphicsLayer {
                    val p = if (still) 1f else ((lift() - 0.35f) / 0.65f).coerceIn(0f, 1f)
                    val tucked = size.toPx() * 0.14f
                    // Rises out from behind the card, growing a little; then sits, leaning towards the card's middle.
                    translationY = tucked + (1f - p) * size.toPx() * 0.7f
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
                    val sc = 0.7f + 0.3f * p
                    scaleX = sc; scaleY = sc
                    alpha = if (still) lift().coerceIn(0f, 1f) else p.coerceAtLeast(0.001f)
                    val lean = if (fromRight) -5f else 5f
                    rotationZ = lean + (1f - p) * (if (fromRight) 14f else -14f)
                },
        )
    }
}

/**
 * The gliding highlight's place and shape: where its pill is, and how far the small circle it first shows as has
 * stretched into the full bar. Everything [pill] reads is snapshot state, all of it read before it can return early, so
 * the drawing that calls it redraws the moment the pill is first placed and on every frame of the stretch. (A plain
 * flag once kept the drawing from reading the stretch at all: the circle stayed a circle until the highlight moved.)
 */
@Stable
internal class Glide {
    private val y = Animatable(0f)
    private val stretch = Animatable(0f)
    private var placed by mutableStateOf(false)
    val shown: Boolean get() = placed

    /** The first time: there at once, as the circle (the whole bar when [still]). After that: glides to [top]. */
    suspend fun moveTo(top: Float, still: Boolean) {
        if (!placed) {
            y.snapTo(top)
            stretch.snapTo(if (still) 1f else 0f)
            placed = true
        } else if (still) y.snapTo(top) else y.animateTo(top, spring(0.8f, 600f))
    }

    /** Stretches the circle into the bar, in about a fifth of a second (at once when [still]). */
    suspend fun open(still: Boolean) {
        if (!placed) return
        if (still) stretch.snapTo(1f) else stretch.animateTo(1f, spring(0.8f, 700f))
    }

    /** The pill in a [full]-wide list of [rowH]-tall rows (its round end on the start side), or null before it is placed. */
    fun pill(full: Float, rowH: Float, rtl: Boolean): Rect? {
        val s = stretch.value.coerceIn(0f, 1f)
        val top = y.value
        if (!placed) return null
        val w = rowH + (full - rowH).coerceAtLeast(0f) * s
        val x = if (rtl) full - w else 0f
        return Rect(Offset(x, top), Size(w, rowH))
    }
}

/**
 * The rows of one level, under one highlight: a pill that first shows as a circle on the focused row's icon, stretches
 * to the whole row as the list opens, and glides from row to row on a spring (in a layer: nothing recomposes as it moves).
 */
@Composable
internal fun LiftRows(routeKey: String, rows: List<MenuAction>, onFocus: (MenuAction) -> Unit) {
    val target = rememberFocusTarget(routeKey, rows.map { it.id })
    val tops = remember(routeKey) { mutableStateMapOf<Int, Float>() }
    var focused by remember(routeKey) { mutableIntStateOf(-1) }
    val glide = remember(routeKey) { Glide() }
    val still = Motion.style == AnimationStyle.Off || Store.settings.reduceMotion
    val at = tops[focused]
    LaunchedEffect(focused, at) { glide.moveTo(at ?: return@LaunchedEffect, still) }
    // Its own effect: a glide starting while the circle stretches never stops the stretch halfway.
    LaunchedEffect(glide, glide.shown) { glide.open(still) }
    val color by animateColorAsState(rows.getOrNull(focused)?.color ?: Palette.Mint, if (still) tween(0) else tween(180), label = "lift-pill")
    val density = LocalDensity.current
    val rowH = with(density) { LiftRowHeight.toPx() }
    val stroke = with(density) { 2.dp.toPx() }
    val rtl = isRtl
    Box(
        Modifier.padding(horizontal = 8.dp, vertical = 9.dp).drawBehind {
            val pill = glide.pill(size.width, rowH, rtl) ?: return@drawBehind
            if (focused < 0) return@drawBehind
            val w = pill.width
            val x = pill.left
            val top = pill.top
            val r = CornerRadius(rowH / 2f)
            val halo = Brush.horizontalGradient(listOf(color.copy(alpha = 0.26f), Color.Transparent), startX = if (rtl) x + w else x, endX = if (rtl) x else x + w)
            drawRoundRect(halo, Offset(x - stroke * 2, top - stroke * 2), Size(w + stroke * 4, rowH + stroke * 4), CornerRadius(r.x + stroke * 2), alpha = 0.8f)
            val fill = Brush.horizontalGradient(listOf(color.copy(alpha = 0.3f), color.copy(alpha = 0.1f)), startX = if (rtl) x + w else x, endX = if (rtl) x else x + w)
            drawRoundRect(fill, Offset(x, top), Size(w, rowH), r)
            val ring = Brush.linearGradient(listOf(lerp(color, Color.White, 0.6f), color, lerp(color, Color.White, 0.3f)), Offset(x, top), Offset(x + w, top + rowH))
            drawRoundRect(ring, Offset(x + stroke / 2, top + stroke / 2), Size(w - stroke, rowH - stroke), r, style = Stroke(stroke))
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            rows.forEachIndexed { i, a ->
                LiftRow(
                    a, routeKey, a.id == target, i,
                    Modifier.onPlaced { tops[i] = it.positionInParent().y },
                    onFocused = { focused = i; onFocus(a) },
                )
            }
        }
    }
}

/** One row of the card: an icon chip, the label (and a quieter line), a value, and › on a group. */
@Composable
private fun LiftRow(a: MenuAction, routeKey: String, wanted: Boolean, index: Int, modifier: Modifier, onFocused: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    var pressed by remember { mutableStateOf(false) }
    val requester = remember { FocusRequester() }
    val opens = a.children != null || a.choices != null
    val click: () -> Unit = {
        when {
            a.children != null -> {
                FocusMemory.forget(routeKey.substringBeforeLast(":main") + ":group:" + a.id)
                ActionMenu.group = a.id
                Sounds.select()
            }
            a.choices != null -> {
                ActionMenu.choosing = a.id
                Sounds.select()
            }
            else -> { a.onClick(); Sounds.select() }
        }
    }
    val hold = if (a.hold) rememberHold(holdHint(a.label.lowercase().removeSuffix("…"))) { a.onClick() } else null
    val color = a.color
    Row(
        modifier.fillMaxWidth().height(LiftRowHeight)
            .riseIn(routeKey, index, 8.dp, 0.96f)
            .takesFocus(routeKey, a.id, wanted)
            .focusRequester(requester)
            .then(if (hold != null) Modifier.holdToConfirm(hold, requester).holdFill(hold, color, LiftRowHeight / 2) else Modifier)
            .sideSteps(a.step)
            .onFocusChanged { if (it.isFocused) { Sounds.move(); onFocused(); a.onFocus?.invoke() } else pressed = false }
            // Clipped to the highlight's own shape (a pill as tall as the row), so nothing in the row ever pokes out of it.
            .graphicsLayer {
                val sc = if (pressed) 0.97f else if (focused) 1.015f else 1f
                scaleX = sc; scaleY = sc
                shape = RowShape; clip = true
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
                    onTap = { runCatching { requester.requestFocus() }; click() },
                )
            }
            // The icon chip sits in the pill's round start; the end leaves the pill's round end room for a picture or
            // a value (see [RowPictureHeight]).
            .padding(start = 7.dp, end = RowEndRoom),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(30.dp).graphicsLayer { shape = RoundedCornerShape(if (a.art != null) 9.dp else 15.dp); clip = true }
                .background(color.copy(alpha = if (focused) 0.32f else 0.2f)).then(if (hold != null) Modifier.holdArc(hold, color) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            when {
                a.art != null -> a.art.invoke()
                a.icon != null -> Box(Modifier.size(22.dp)) { TIconGlyph(a.icon, color) }
                a.glyph != null -> Box(Modifier.size(19.dp)) { GlyphIcon(a.glyph, color) }
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            ShrinkText(a.label, MaterialTheme.typography.titleSmall, Modifier.fillMaxWidth(), maxLines = 1, minScale = 0.72f)
            if (a.sub != null) ShrinkText(a.sub, MaterialTheme.typography.labelSmall, Modifier.fillMaxWidth(), maxLines = 1, minScale = 0.8f, color = Palette.Muted)
        }
        if (a.preview != null) {
            Spacer(Modifier.width(8.dp))
            a.preview.invoke(RowPictureHeight)
        } else if (a.value != null) {
            Spacer(Modifier.width(6.dp))
            ValueChip(a.value, color, cycles = a.step != null && a.choices == null, maxWidth = 96.dp)
        }
        if (opens) {
            Spacer(Modifier.width(4.dp))
            GlyphIcon(Glyph.Forward, color.copy(alpha = if (focused) 0.95f else 0.6f), Modifier.size(14.dp))
        }
    }
}
