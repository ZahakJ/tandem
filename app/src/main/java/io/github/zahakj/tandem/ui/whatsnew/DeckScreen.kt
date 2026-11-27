package io.github.zahakj.tandem.ui.whatsnew

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.zahakj.tandem.Button
import io.github.zahakj.tandem.Buttons
import io.github.zahakj.tandem.R
import io.github.zahakj.tandem.Tx
import io.github.zahakj.tandem.ui.AnimationStyle
import io.github.zahakj.tandem.ui.ButtonHint
import io.github.zahakj.tandem.ui.FocusRoom
import io.github.zahakj.tandem.ui.Glyph
import io.github.zahakj.tandem.ui.GlyphChip
import io.github.zahakj.tandem.ui.InterceptButtons
import io.github.zahakj.tandem.ui.Motion
import io.github.zahakj.tandem.ui.OverlayPanel
import io.github.zahakj.tandem.ui.Palette
import io.github.zahakj.tandem.ui.PillButton
import io.github.zahakj.tandem.ui.Pip
import io.github.zahakj.tandem.ui.ShrinkText
import io.github.zahakj.tandem.ui.fadeWhole
import io.github.zahakj.tandem.ui.isRtl
import io.github.zahakj.tandem.ui.rememberFocusTarget
import io.github.zahakj.tandem.ui.rememberLatest
import io.github.zahakj.tandem.ui.showcaseStill
import io.github.zahakj.tandem.ui.sideStep
import io.github.zahakj.tandem.ui.startSign
import io.github.zahakj.tandem.ui.swallowTaps
import io.github.zahakj.tandem.ui.takesFocus
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.sign

/*
 * The deck on the top screen: a big glassy card over the dimmed home, the slide's picture on the start side and its
 * words on the end side with Pip above them, the dots and the buttons along the bottom. ◀ ▶ (as the arrows point:
 * in Arabic Left is the next slide), L1 / R1 and a swipe walk the slides, which slide in from the way you went; A is
 * Next (Done on the last), B closes. Reduce motion: slides swap with a short fade and every picture holds still.
 * The other screen shows the deck's contents with the slide on show lit ([WhatsNewCompanion]).
 */

/** The size every slide picture is drawn at; [DesignBox] scales it to the room it gets. */
internal val DesignW = 360.dp
internal val DesignH = 232.dp

/** A slide's colour in the live theme. */
internal fun Tint.color(): Color = when (this) {
    Tint.Peach -> Palette.Peach
    Tint.Mint -> Palette.Mint
    Tint.Lavender -> Palette.Lavender
    Tint.Sky -> Palette.Sky
    Tint.Butter -> Palette.Butter
}

/**
 * Lays [content] out at the design size ([DesignW] × [DesignH]) and scales it, keeping its shape, to fit whatever room
 * it gets, centred: so a picture drawn once fits the deck, the side panel's showcase and the other screen alike.
 */
@Composable
internal fun DesignBox(modifier: Modifier = Modifier, width: Dp = DesignW, height: Dp = DesignH, content: @Composable BoxScope.() -> Unit) {
    Layout({ Box(Modifier.size(width, height), content = content) }, modifier) { measurables, constraints ->
        val w = width.roundToPx()
        val h = height.roundToPx()
        val p = measurables.first().measure(Constraints.fixed(w, h))
        val boxW = if (constraints.hasBoundedWidth) constraints.maxWidth else w
        val boxH = if (constraints.hasBoundedHeight) constraints.maxHeight else (h.toLong() * boxW / w).toInt()
        val s = minOf(boxW.toFloat() / w, boxH.toFloat() / h)
        layout(boxW, boxH) { p.placeWithLayer((boxW - w) / 2, (boxH - h) / 2) { scaleX = s; scaleY = s } }
    }
}

/** How far a slide's parts have arrived ([arrive]); null outside a slide: everything simply shows. */
private val LocalArrival = compositionLocalOf<State<Float>?> { null }

/**
 * A slide's picture, its parts arriving one after another ([arrive]) each time it comes on screen, the way the
 * drawings in Astrolabe's notes play. Reduce motion: all there at once.
 */
@Composable
internal fun Arrivals(content: @Composable () -> Unit) {
    val still = showcaseStill()
    val clock = remember { Animatable(if (still) 99f else 0f) }
    LaunchedEffect(Unit) {
        if (still) return@LaunchedEffect
        delay(140)
        clock.animateTo(24f, tween(24 * 90, easing = LinearEasing))
    }
    CompositionLocalProvider(LocalArrival provides clock.asState(), content = content)
}

/** Part [i] of a slide's picture: fades and rises into place in its turn. In a layer, so nothing re-lays out. */
@Composable
internal fun Modifier.arrive(i: Int): Modifier {
    val clock = LocalArrival.current ?: return this
    return graphicsLayer {
        val p = ((clock.value - i * 1.4f) / 3f).coerceIn(0f, 1f)
        val e = 1f - (1f - p) * (1f - p)
        fadeWhole(e)
        translationY = (1f - e) * 12.dp.toPx()
        val s = 0.94f + 0.06f * e
        scaleX = s; scaleY = s
    }
}

/** 0 to 1 over [periodMs], round and round; held at [still] with Reduce motion. Read it in draw or layer lambdas. */
@Composable
internal fun rememberLoop(periodMs: Int, still: Float): State<Float> =
    // Holds its frame once Tandem has settled, like every other loop (rememberIdleLoop).
    io.github.zahakj.tandem.ui.rememberIdleLoop(periodMs, showcaseStill(), still)

/** Where [t] is between [a] and [b], eased out: 0 before, 1 after. */
internal fun seg(t: Float, a: Float, b: Float): Float {
    val p = ((t - a) / (b - a)).coerceIn(0f, 1f)
    return 1f - (1f - p) * (1f - p)
}

/** The deck over the top screen; nothing while it is closed. */
@Composable
fun WhatsNewOverlay() {
    val visible = WhatsNew.open && WhatsNew.deck != null
    val shown = rememberLatest(WhatsNew.deck)
    // L1 and R1 walk the slides (R1 forward in every language); the deck owns every other button while it shows.
    InterceptButtons(visible) { b ->
        if (!Buttons.repeat) when (b) {
            Button.L1 -> WhatsNew.step(-1)
            Button.R1 -> WhatsNew.step(1)
            else -> Unit
        }
        true
    }
    OverlayPanel(visible, onClose = { WhatsNew.close() }) {
        val d = shown ?: return@OverlayPanel
        DeckPanel(d)
    }
}

@Composable
private fun DeckPanel(d: Deck) {
    val index = WhatsNew.index.coerceIn(0, d.slides.lastIndex)
    val slide = d.slides[index]
    val last = index == d.slides.lastIndex
    val tint by animateColorAsState(slide.tint.color(), tween(520), label = "wn-tint")
    val rtl = isRtl
    val sign = startSign
    val still = showcaseStill()
    val target = rememberFocusTarget(WhatsNew.ROUTE, listOf("next"))
    Column(
        Modifier.fillMaxSize()
            .clip(RoundedCornerShape(30.dp))
            .background(Palette.Card.copy(alpha = 0.97f))
            .drawBehind {
                // The slide's colour glowing from the top corner on the end side, the accent from the opposite one.
                val w = size.width
                val end = if (rtl) 0f else w
                drawCircle(Brush.radialGradient(listOf(tint.copy(alpha = 0.26f), Color.Transparent), Offset(end, 0f), w * 0.62f), w * 0.62f, Offset(end, 0f))
                drawCircle(Brush.radialGradient(listOf(Palette.Accent.copy(alpha = 0.12f), Color.Transparent), Offset(w - end, size.height), w * 0.5f), w * 0.5f, Offset(w - end, size.height))
            }
            .border(1.dp, tint.copy(alpha = 0.22f), RoundedCornerShape(30.dp))
            .swallowTaps()
            .swipes(rtl)
            .onPreviewKeyEvent { e ->
                val s = e.sideStep()
                if (s == 0) return@onPreviewKeyEvent false
                if (e.type == KeyEventType.KeyDown) WhatsNew.step(s)
                true
            }
            .padding(start = 22.dp, end = 22.dp, top = 16.dp, bottom = FocusRoom),
    ) {
        Header(d, index, tint)
        Spacer(Modifier.height(12.dp))
        val dir = WhatsNew.dir
        // Forward: the new slide comes in from the end side (the left in right to left); back, from the start side.
        val spec: androidx.compose.animation.AnimatedContentTransitionScope<Int>.() -> androidx.compose.animation.ContentTransform = {
            if (still || Motion.style == AnimationStyle.Off) fadeIn(tween(160)) togetherWith fadeOut(tween(100))
            else (slideInHorizontally(tween(380)) { (it / 3 * dir * sign).toInt() } + fadeIn(tween(260))) togetherWith
                (slideOutHorizontally(tween(320)) { (-it / 4 * dir * sign).toInt() } + fadeOut(tween(180)))
        }
        Row(Modifier.fillMaxWidth().weight(1f), verticalAlignment = Alignment.CenterVertically) {
            AnimatedContent(index, Modifier.weight(1.32f).fillMaxHeight(), transitionSpec = spec, label = "wn-visual") { i ->
                val s = d.slides[i]
                val c = s.tint.color()
                Box(
                    Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp))
                        .background(Brush.verticalGradient(listOf(lerp(Palette.NightDeep, c, 0.1f), Palette.NightDeep)))
                        .border(1.dp, c.copy(alpha = 0.28f), RoundedCornerShape(24.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Arrivals { DesignBox(Modifier.fillMaxSize().padding(10.dp)) { s.visual() } }
                }
            }
            Spacer(Modifier.width(22.dp))
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
                // The host: stays put, changes face with each slide and hops when you move on (WhatsNew.go).
                Pip(52.dp, mood = slide.mood)
                Spacer(Modifier.height(8.dp))
                AnimatedContent(index, transitionSpec = spec, label = "wn-words") { i -> SlideWords(d.slides[i]) }
            }
        }
        Spacer(Modifier.height(12.dp))
        Footer(d, index, last, tint, target)
    }
}

@Composable
private fun Header(d: Deck, index: Int, tint: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.clip(RoundedCornerShape(50)).background(tint.copy(alpha = 0.2f)).padding(start = 4.dp, end = 12.dp, top = 3.dp, bottom = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlyphChip(Glyph.Sparkle, tint, size = 22.dp, round = true)
            Spacer(Modifier.width(6.dp))
            Text(Tx.s(R.string.whatsnew_eyebrow), style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold), color = tint, maxLines = 1)
        }
        Spacer(Modifier.width(12.dp))
        Text(
            Tx.s(R.string.whatsnew_deck_title, shortVersion(d.version)), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
            maxLines = 1,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            Tx.s(d.tagline), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = Palette.Muted,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(10.dp))
        Text("${index + 1} / ${d.slides.size}", style = MaterialTheme.typography.titleSmall, color = Palette.Muted, maxLines = 1)
    }
}

/** The slide's chip (its short name), title and lines. */
@Composable
private fun SlideWords(s: Slide) {
    val c = s.tint.color()
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.clip(RoundedCornerShape(50)).background(c.copy(alpha = 0.18f)).padding(start = 3.dp, end = 10.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlyphChip(s.glyph, c, size = 22.dp, round = true)
            Spacer(Modifier.width(6.dp))
            Text(Tx.s(s.short), style = MaterialTheme.typography.labelLarge, color = c, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(10.dp))
        ShrinkText(Tx.s(s.title), MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold), maxLines = 2)
        Spacer(Modifier.height(8.dp))
        Text(
            Tx.s(s.body), style = MaterialTheme.typography.bodyLarge, color = Palette.Text.copy(alpha = 0.82f),
            maxLines = 6, overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun Footer(d: Deck, index: Int, last: Boolean, tint: Color, target: String?) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        DeckDots(d, index, tint)
        Spacer(Modifier.weight(1f))
        ButtonHint("◀▶", Tx.s(R.string.whatsnew_hint_slides), Palette.Lavender)
        Spacer(Modifier.width(14.dp))
        ButtonHint("B", Tx.s(R.string.whatsnew_hint_close), Palette.Lavender, onClick = { WhatsNew.close() })
        Spacer(Modifier.width(16.dp))
        PillButton(
            Tx.s(if (last) R.string.whatsnew_done else R.string.whatsnew_next), tint,
            Modifier.takesFocus(WhatsNew.ROUTE, "next", target == "next"), filled = true,
        ) { if (last) WhatsNew.close() else WhatsNew.step(1) }
    }
}

/** A dot per slide, the one on show a longer pill in its colour; a tap goes there. */
@Composable
private fun DeckDots(d: Deck, index: Int, tint: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        d.slides.forEachIndexed { i, _ ->
            val on = i == index
            val w by animateDpAsState(if (on) 22.dp else 8.dp, Motion.soft(), label = "wn-dot")
            Box(
                Modifier.height(8.dp).width(w).clip(CircleShape)
                    .background(if (on) tint else Palette.Muted.copy(alpha = 0.4f))
                    .swallowTaps { WhatsNew.go(i) },
            )
        }
    }
}

/** A sideways swipe turns a slide: towards the start of the reading direction is forward. */
private fun Modifier.swipes(rtl: Boolean): Modifier = pointerInput(rtl) {
    var dx = 0f
    detectHorizontalDragGestures(
        onDragStart = { dx = 0f },
        onDragEnd = {
            if (abs(dx) > 48.dp.toPx()) {
                val forward = (if (rtl) 1f else -1f) * sign(dx) > 0f
                WhatsNew.step(if (forward) 1 else -1)
            }
        },
    ) { change, amount ->
        dx += amount
        change.consume()
    }
}

/**
 * The other screen while the deck shows: the deck's contents, the slide on show lit in its colour with its number,
 * Pip in the same mood as on top, and the buttons. A tap on a name goes to that slide.
 */
@Composable
fun WhatsNewCompanion(modifier: Modifier = Modifier) {
    val d = rememberLatest(WhatsNew.deck) ?: return
    val index = WhatsNew.index.coerceIn(0, d.slides.lastIndex)
    val slide = d.slides[index]
    val tint by animateColorAsState(slide.tint.color(), tween(520), label = "wn-companion-tint")
    Column(
        modifier.fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Palette.Card.copy(alpha = 0.96f))
            .border(1.dp, tint.copy(alpha = 0.25f), RoundedCornerShape(28.dp))
            .swallowTaps()
            .padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Pip(46.dp, mood = slide.mood)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(Tx.s(R.string.whatsnew_eyebrow), style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold), color = tint, maxLines = 1)
                Text(
                    Tx.s(R.string.whatsnew_deck_title, shortVersion(d.version)), style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Text("${index + 1} / ${d.slides.size}", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold), color = tint, maxLines = 1)
        }
        Spacer(Modifier.height(12.dp))
        d.slides.forEachIndexed { i, s ->
            val on = i == index
            val c = s.tint.color()
            Row(
                Modifier.fillMaxWidth().height(38.dp).clip(RoundedCornerShape(50))
                    .background(if (on) c.copy(alpha = 0.24f) else Color.Transparent)
                    .swallowTaps { WhatsNew.go(i) }
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlyphChip(s.glyph, c, size = 26.dp, round = true)
                Spacer(Modifier.width(10.dp))
                Text(
                    Tx.s(s.short), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall.copy(fontWeight = if (on) FontWeight.ExtraBold else FontWeight.SemiBold),
                    color = if (on) Palette.Text else Palette.Text.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (on) Box(Modifier.padding(end = 8.dp).size(8.dp).clip(CircleShape).background(c))
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                ButtonHint("L1", "", Palette.Lavender, onClick = { WhatsNew.step(-1) })
                ButtonHint("R1", Tx.s(R.string.whatsnew_hint_slides), Palette.Lavender, onClick = { WhatsNew.step(1) })
            }
            val last = index == d.slides.lastIndex
            ButtonHint("A", Tx.s(if (last) R.string.whatsnew_done else R.string.whatsnew_next), tint, onClick = { if (last) WhatsNew.close() else WhatsNew.step(1) })
            ButtonHint("B", Tx.s(R.string.whatsnew_hint_close), Palette.Lavender, onClick = { WhatsNew.close() })
        }
    }
}
