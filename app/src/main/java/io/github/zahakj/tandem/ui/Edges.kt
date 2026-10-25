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
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

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
