package io.github.zahakj.tandem.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import io.github.zahakj.tandem.Lang

/*
 * Right to left (Arabic). Compose mirrors rows, start/end padding and alignment, offsets and placeRelative by itself;
 * these helpers are for what it doesn't: D-pad Left/Right handlers, translationX slides, drawings and horizontal
 * gradients, and pictures that point somewhere (docs/TRANSLATING.md, "Right to left").
 *
 * The one rule for the D-pad: Left and Right move things the way the arrow points on screen. Something laid out from
 * the start side (a segmented choice, a slider's fill, a row of covers, pages) runs right to left in Arabic, so there
 * Left steps forward (the next option, a bigger number, the next cover) and Right steps back, as Android's own
 * sliders do in a right-to-left language. Physical things (the controller test's buttons, the sketch pad's pen,
 * Pip's glance, the sounds' stereo pan) never mirror.
 */

/** The pure part, unit tested (RtlTest). */
object Sides {
    /** The step along a start-to-end run a sideways press makes: [left] for D-pad Left; +1 is towards the end. */
    fun step(left: Boolean, rtl: Boolean): Int = if (left != rtl) -1 else 1

    /** +1 when the start side is the left, -1 when it is the right: multiplies a translationX meant "towards the end". */
    fun sign(rtl: Boolean): Float = if (rtl) -1f else 1f

    /** Where [x] measured from the start side lies from the left, in something [width] wide. */
    fun x(x: Float, width: Float, rtl: Boolean): Float = if (rtl) width - x else x

    /**
     * [text] kept left to right inside right-to-left text (a file path, a version, a URL, a number with its unit
     * built in code): a left-to-right isolate, so its pieces never swap round the slashes, dots or signs.
     */
    fun ltr(text: String): String = "⁦$text⁩"
}

/** The app lays out right to left now (any thread: key handlers, click handlers). */
val rtlNow: Boolean get() = Lang.layoutDirection == LayoutDirection.Rtl

/** Inside a composable: laid out right to left here. */
val isRtl: Boolean
    @Composable @ReadOnlyComposable get() = LocalLayoutDirection.current == LayoutDirection.Rtl

/** +1 left to right, -1 right to left, for translationX "towards the end". */
val startSign: Float
    @Composable @ReadOnlyComposable get() = Sides.sign(isRtl)

/**
 * The step a D-pad Left or Right press makes along something that runs from the start side (see the rule above):
 * -1 towards the start, +1 towards the end, 0 for any other key.
 */
fun KeyEvent.sideStep(): Int = when (key) {
    Key.DirectionLeft -> Sides.step(left = true, rtl = rtlNow)
    Key.DirectionRight -> Sides.step(left = false, rtl = rtlNow)
    else -> 0
}

/**
 * Parts of a line joined by [separator] ("NES · 1990 · Puzzle"). In right to left each part is isolated, so the line
 * reads from the right even when its first part is a Latin name, and no part trades places with its neighbours.
 * Only for text shown now, never for text kept (a note, a saved name): the isolates would stay in it.
 */
fun Iterable<String>.joinParts(separator: String = " · "): String =
    if (rtlNow) joinToString(separator) { "⁨$it⁩" } else joinToString(separator)

/**
 * [content] laid out left to right in every language: things whose order is physical or fixed, not read (the A–Z
 * keyboards, the PIN pad, pictures of the controller and of the screens).
 */
@Composable
fun LeftToRight(content: @Composable () -> Unit) =
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr, content = content)

/** This is right to left. */
val DrawScope.rtl: Boolean get() = layoutDirection == LayoutDirection.Rtl

/** [block] drawn mirrored in right to left (shapes only: text drawn inside would read backwards). */
inline fun DrawScope.mirroredInRtl(block: DrawScope.() -> Unit) {
    if (rtl) scale(-1f, 1f, center) { block() } else block()
}

/** A gradient across the width from the start side ([colors] first to last), mirrored in right to left. */
fun startGradient(colors: List<Color>, rtl: Boolean): Brush = Brush.horizontalGradient(if (rtl) colors.asReversed() else colors)

/** [startGradient] for this drawing's direction. */
fun DrawScope.startGradient(colors: List<Color>): Brush = startGradient(colors, rtl)

/**
 * A picture that points (a back arrow, a chevron, a play-onwards triangle, lines ranged from the start) drawn mirrored
 * in right to left. Not for pictures of things (a gamepad, a clock, a speaker, media controls) or anything with text.
 */
fun Modifier.mirroredInRtl(): Modifier = drawWithContent { if (rtl) scale(-1f, 1f) { this@drawWithContent.drawContent() } else drawContent() }
