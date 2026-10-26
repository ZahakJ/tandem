package io.github.zahakj.tandem.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/*
 * Edges and room, in one place (see docs/UI-RULES.md).
 *
 * A highlighted FocusCard reaches past its own bounds: it grows by up to MaxFocusGrowth in total (7 dp a side on
 * a square tile, more vertically on a tall one), lifts 4 dp (7 dp in the Lift style) and casts a glow 10-14 dp
 * deep. The ring is drawn inside the card, so it never adds to that. Containers leave [FocusRoom] for it on the
 * edges where a card can sit, and every scrolling container fades its content out at an edge that has more
 * behind it ([fadingEdges]) instead of cutting cards in half.
 */

/** Room to leave above and below focusable items: growth (7) + lift (4-7) + the top of the glow. */
val FocusRoom = 20.dp

/** Room to leave beside focusable items in a horizontal row. */
val FocusRoomSide = 16.dp

/** How far a scrolling container fades out at an edge with more content behind it. */
val FadeEdge = 22.dp

/** The gap every popup keeps from the screen's edges. */
val PanelMargin = 16.dp

/** The least inner padding between a container's edge and any text in it. */
val TextInset = 12.dp

/**
 * Fades a scrolling container's content out at the edges where more content is waiting (an alpha mask, so the
 * backdrop shows through), instead of a hard cut. The fade comes and goes as the list reaches its ends, so a list
 * at rest shows its first item whole. Put it on the scrolling container itself.
 */
@Composable
fun Modifier.fadingEdges(state: ScrollableState, horizontal: Boolean = false, length: Dp = FadeEdge): Modifier {
    val start = animateFloatAsState(if (state.canScrollBackward) 1f else 0f, tween(180), label = "fade-start")
    val end = animateFloatAsState(if (state.canScrollForward) 1f else 0f, tween(180), label = "fade-end")
    return edgeFade(horizontal, length, { start.value }, { end.value })
}

/**
 * The mask behind [fadingEdges]: [start] and [end] (0..1) say how strongly each edge fades. Read in the draw phase,
 * so a changing fade never recomposes; the offscreen layer is only used while something is fading.
 */
fun Modifier.edgeFade(horizontal: Boolean, length: Dp, start: () -> Float, end: () -> Float): Modifier =
    graphicsLayer {
        compositingStrategy = if (start() > 0.001f || end() > 0.001f) CompositingStrategy.Offscreen else CompositingStrategy.Auto
    }.drawWithContent {
        drawContent()
        val s = start()
        val e = end()
        val px = length.toPx()
        val main = if (horizontal) size.width else size.height
        val len = minOf(px, main / 3f)
        if (len <= 0f) return@drawWithContent
        if (s > 0.001f) {
            val brush = if (horizontal) Brush.horizontalGradient(0f to Color.Black.copy(alpha = 1f - s), 1f to Color.Black, startX = 0f, endX = len)
            else Brush.verticalGradient(0f to Color.Black.copy(alpha = 1f - s), 1f to Color.Black, startY = 0f, endY = len)
            drawRect(brush, Offset.Zero, if (horizontal) Size(len, size.height) else Size(size.width, len), blendMode = BlendMode.DstIn)
        }
        if (e > 0.001f) {
            val at = main - len
            val brush = if (horizontal) Brush.horizontalGradient(0f to Color.Black, 1f to Color.Black.copy(alpha = 1f - e), startX = at, endX = main)
            else Brush.verticalGradient(0f to Color.Black, 1f to Color.Black.copy(alpha = 1f - e), startY = at, endY = main)
            drawRect(
                brush, if (horizontal) Offset(at, 0f) else Offset(0f, at),
                if (horizontal) Size(len, size.height) else Size(size.width, len), blendMode = BlendMode.DstIn,
            )
        }
    }

/**
 * Lets a scrolling row reach [horizontal] past its slot on both sides (its content padded back in by the same), so
 * a highlighted card at the row's start or end has room to grow and glow inside the row's clip, while the cards
 * still line up with everything else at rest.
 */
fun Modifier.bleed(horizontal: Dp): Modifier = layout { measurable, constraints ->
    val extra = horizontal.roundToPx()
    val wide = if (constraints.hasBoundedWidth) constraints.maxWidth + 2 * extra else constraints.maxWidth
    val placeable = measurable.measure(constraints.copy(minWidth = (constraints.minWidth + 2 * extra).coerceAtMost(wide), maxWidth = wide))
    val width = (placeable.width - 2 * extra).coerceIn(constraints.minWidth, constraints.maxWidth)
    layout(width, placeable.height) { placeable.place(-extra, 0) }
}

/** A soft shadow under text that sits on artwork, so it reads without a dark band behind it. */
fun TextStyle.onArt(): TextStyle = copy(shadow = Shadow(Color.Black.copy(alpha = 0.7f), Offset(0f, 1.5f), 8f))

/**
 * The area a popup may use: the whole screen less [PanelMargin] on every side, centred. [content] gets the room as
 * constraints (maxHeight), so a panel can size its scrolling part to fit instead of running off the screen.
 */
@Composable
fun PopupRoom(content: @Composable BoxWithConstraintsScope.() -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().padding(PanelMargin), contentAlignment = Alignment.Center, content = content)
}

/**
 * Lays [content] out at its natural size and, when that is bigger than the space it gets, scales it down (keeping
 * its shape) until it fits whole, placed by [alignment]. It never scales up. For small places that change size, like
 * widgets on the icon grid, so a line that would not fit shrinks a little instead of being cut. With [wrap], text
 * may wrap at the width it gets and only the height is scaled to fit; without it every line keeps its whole width.
 * Weighted children don't work inside it without [wrap] (the width is unbounded).
 */
@Composable
fun ShrinkToFit(modifier: Modifier = Modifier, alignment: Alignment = Alignment.Center, wrap: Boolean = false, content: @Composable () -> Unit) {
    Layout(content, modifier) { measurables, constraints ->
        val loose = if (wrap && constraints.hasBoundedWidth) Constraints(maxWidth = constraints.maxWidth) else Constraints()
        val placeables = measurables.map { it.measure(loose) }
        val w = placeables.maxOfOrNull { it.width } ?: 0
        val h = placeables.maxOfOrNull { it.height } ?: 0
        // As big as the content, within the constraints: a fixed size (fillMaxSize, size) shows it scaled inside.
        val boxW = if (constraints.hasBoundedWidth) w.coerceIn(constraints.minWidth, constraints.maxWidth) else w.coerceAtLeast(constraints.minWidth)
        val boxH = if (constraints.hasBoundedHeight) h.coerceIn(constraints.minHeight, constraints.maxHeight) else h.coerceAtLeast(constraints.minHeight)
        layout(boxW, boxH) {
            for (p in placeables) {
                val scale = minOf(1f, if (p.width > 0) boxW.toFloat() / p.width else 1f, if (p.height > 0) boxH.toFloat() / p.height else 1f)
                val shown = IntSize((p.width * scale).roundToInt(), (p.height * scale).roundToInt())
                val at = alignment.align(shown, IntSize(boxW, boxH), layoutDirection)
                p.placeWithLayer(at.x, at.y) { scaleX = scale; scaleY = scale; transformOrigin = TransformOrigin(0f, 0f) }
            }
        }
    }
}

/**
 * A one-line summary made of parts joined by " · " ("191 games · 9 systems · 12 GB"): shows as many whole parts
 * from the start as fit the width, instead of cutting the last one mid-word. Only a first part that doesn't fit on
 * its own shrinks a little (to 85 %), and only one still too long after that is ellipsized.
 */
@Composable
fun PartsText(text: String, style: TextStyle, modifier: Modifier = Modifier, color: Color = Color.Unspecified) {
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier) {
        val maxW = constraints.maxWidth
        val shown = remember(text, style, maxW) {
            if (maxW == Constraints.Infinity) return@remember text to style
            fun width(s: String) = measurer.measure(s, style, maxLines = 1, softWrap = false).size.width
            val parts = text.split(" · ")
            var best = parts.first()
            for (k in 2..parts.size) {
                val candidate = parts.take(k).joinToString(" · ")
                if (width(candidate) <= maxW) best = candidate else break
            }
            val w = width(best)
            val fitted = if (w > maxW && w * 0.85f <= maxW) style.copy(fontSize = style.fontSize * (maxW.toFloat() / w * 0.98f)) else style
            best to fitted
        }
        Text(shown.first, style = shown.second, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
