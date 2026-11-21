package io.github.zahakj.tandem.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * Choices that show their options. A choice with a few short options is a segmented track on its row: every option
 * is in sight, the chosen one lit by a pill that slides, Left and Right move it (stopping at the ends), A steps
 * round, a tap picks. A choice with many options (a system, a language, an emulator) is a row with its value that
 * opens a grid of option cards, each with its picture when it has one. Things switched on and off that have a
 * picture (kinds of artwork, backup parts) are tiles with the picture and a check, not rows of "On" and "Off".
 */

/** One option of a choice: its [label], a line on what it means, and a picture ([art]) or an icon ([glyph]). */
class PickOption<T>(
    val value: T,
    val label: String,
    val detail: String? = null,
    val glyph: Glyph? = null,
    val art: (@Composable () -> Unit)? = null,
)

/** How tall a segmented track is. */
private val TrackHeight = 30.dp

/**
 * [labels] side by side in a rounded track, the [selected] one lit by a pill that slides to it. [onPick] makes each
 * option tappable; the D-pad is the row's (see [SegmentRow]).
 */
@Composable
fun SegmentTrack(labels: List<String>, selected: Int, color: Color, modifier: Modifier = Modifier, onPick: ((Int) -> Unit)? = null) {
    if (labels.isEmpty()) return
    BoxWithConstraints(
        modifier.height(TrackHeight).clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.1f)).padding(3.dp),
    ) {
        val n = labels.size
        val seg = maxWidth / n
        val x by animateDpAsState(seg * selected.coerceIn(0, n - 1), Motion.focus(), label = "segment")
        if (selected in labels.indices) {
            Box(Modifier.offset(x = x).width(seg).fillMaxHeight().clip(RoundedCornerShape(50)).background(color))
        }
        Row(Modifier.fillMaxSize()) {
            labels.forEachIndexed { i, label ->
                val on = i == selected
                val ink by animateColorAsState(if (on) Palette.OnAccent else Palette.Text.copy(alpha = 0.78f), Motion.fade(160), label = "segment-ink")
                Box(
                    Modifier.weight(1f).fillMaxHeight()
                        .then(if (onPick != null) Modifier.clip(RoundedCornerShape(50)).clickable(remember { MutableInteractionSource() }, null) { onPick(i) } else Modifier)
                        .padding(horizontal = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    ShrinkToFit { Text(label, style = MaterialTheme.typography.labelLarge, color = ink, maxLines = 1) }
                }
            }
        }
    }
}

/**
 * A row for a choice with a few short options: its [label] (and a line, [detail]) beside a [SegmentTrack] of
 * [labels]. One stop for the D-pad: Left and Right move the choice and stop at the ends, A steps round to the next.
 * [trackWidth] is how wide the track is; null shares the row with the label.
 */
@Composable
fun SegmentRow(
    label: String, detail: String?, labels: List<String>, selected: Int, color: Color, modifier: Modifier,
    glyph: Glyph? = null, trackWidth: Dp? = 300.dp, onFocus: () -> Unit = {}, onPick: (Int) -> Unit,
) {
    val n = labels.size
    FocusCard(
        modifier.fillMaxWidth().sideSteps { by ->
            val next = (selected + by).coerceIn(0, n - 1)
            if (next != selected) onPick(next)
        },
        glow = color, shape = RoundedCornerShape(16.dp), lifts = false, onFocus = onFocus,
        onClick = { if (n > 0) onPick((selected + 1) % n) },
    ) {
        // Beside the label, or under it across the whole row when there is no [trackWidth] (a narrow column).
        Column(Modifier.fillMaxWidth().padding(start = if (glyph != null) 10.dp else 14.dp, end = 10.dp, top = 8.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (glyph != null) {
                    GlyphChip(glyph, color, size = 32.dp, round = true)
                    Spacer(Modifier.width(10.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(label, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (trackWidth != null) {
                    Spacer(Modifier.width(10.dp))
                    SegmentTrack(labels, selected, color, Modifier.width(trackWidth), onPick = onPick)
                }
            }
            if (trackWidth == null) {
                Spacer(Modifier.height(6.dp))
                SegmentTrack(labels, selected, color, Modifier.fillMaxWidth(), onPick = onPick)
            }
        }
    }
}

/** A row with a switch: its [label], a line on it ([detail]), and the switch, which A flips. */
@Composable
fun SwitchCard(label: String, detail: String?, on: Boolean, color: Color, modifier: Modifier, glyph: Glyph? = null, onFocus: () -> Unit = {}, onClick: () -> Unit) {
    FocusCard(modifier.fillMaxWidth(), glow = color, shape = RoundedCornerShape(16.dp), lifts = false, onFocus = onFocus, onClick = onClick) {
        Row(Modifier.fillMaxWidth().padding(start = if (glyph != null) 10.dp else 14.dp, end = 12.dp, top = 9.dp, bottom = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            if (glyph != null) {
                GlyphChip(glyph, color, size = 32.dp, round = true)
                Spacer(Modifier.width(10.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(10.dp))
            Toggle(on, color)
        }
    }
}

/**
 * A row for a choice with many options: its [label], a line ([detail]), and the chosen [value] with the choice's
 * picture ([art]) when it has one. A opens the options ([ChoicePopup]); the grid mark says so.
 */
@Composable
fun PickRow(
    label: String, detail: String?, value: String, color: Color, modifier: Modifier,
    glyph: Glyph? = null, art: (@Composable () -> Unit)? = null, onFocus: () -> Unit = {}, onClick: () -> Unit,
) {
    FocusCard(modifier.fillMaxWidth(), glow = color, shape = RoundedCornerShape(16.dp), lifts = false, onFocus = onFocus, onClick = onClick) {
        Row(Modifier.fillMaxWidth().padding(start = if (glyph != null || art != null) 10.dp else 14.dp, end = 10.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            when {
                art != null -> Box(Modifier.size(32.dp).clip(RoundedCornerShape(9.dp)), contentAlignment = Alignment.Center) { art() }
                glyph != null -> GlyphChip(glyph, color, size = 32.dp, round = true)
            }
            if (glyph != null || art != null) Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(10.dp))
            Row(
                Modifier.heightIn(min = TrackHeight).clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.16f)).padding(start = 12.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(value, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.labelLarge, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.width(6.dp))
                // The mark for "opens a grid of options", where a stepper had its arrows.
                GlyphIcon(Glyph.Grid, color.copy(alpha = 0.8f), Modifier.size(14.dp))
            }
        }
    }
}

/**
 * Where a number sits between its ends, as a slider: a track filled up to [fraction] (0..1) with a knob there,
 * which glides when the number changes. [start] and [end] name the ends, under it.
 */
@Composable
fun ValueTrack(fraction: Float, color: Color, modifier: Modifier = Modifier, start: String? = null, end: String? = null) {
    val f by androidx.compose.animation.core.animateFloatAsState(fraction.coerceIn(0f, 1f), Motion.focus(), label = "track")
    Column(modifier) {
        BoxWithConstraints(Modifier.fillMaxWidth().height(16.dp), contentAlignment = Alignment.CenterStart) {
            val knob = 16.dp
            Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(color.copy(alpha = 0.18f)))
            Box(Modifier.width((maxWidth - knob) * f + knob / 2).height(6.dp).clip(CircleShape).background(color))
            Box(Modifier.offset(x = (maxWidth - knob) * f).size(knob).clip(CircleShape).background(color).padding(4.dp).clip(CircleShape).background(Palette.OnAccent))
        }
        if (start != null || end != null) {
            Row(Modifier.fillMaxWidth().padding(top = 2.dp)) {
                Text(start.orEmpty(), Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = Palette.Muted, maxLines = 1)
                Text(end.orEmpty(), style = MaterialTheme.typography.labelSmall, color = Palette.Muted, maxLines = 1)
            }
        }
    }
}

/** How a [ChoicePopup] or [PictureToggle] says "this one": a filled round check. */
@Composable
fun TickBadge(on: Boolean, color: Color, modifier: Modifier = Modifier, size: Dp = 22.dp) {
    val fill by animateColorAsState(if (on) color else Palette.Muted.copy(alpha = 0.22f), Motion.fade(160), label = "check")
    Box(modifier.size(size).clip(CircleShape).background(fill), contentAlignment = Alignment.Center) {
        if (on) Text("✓", style = MaterialTheme.typography.labelMedium, color = Palette.OnAccent, maxLines = 1)
    }
}

/**
 * The options of a choice as a grid of cards in a popup: each with its picture or icon, its name and a line on it;
 * the chosen one checked and tinted, and highlighted when the popup opens. A picks and closes, B closes.
 */
@Composable
fun <T> ChoicePopup(
    open: Boolean, title: String, subtitle: String?, choices: List<PickOption<T>>, selected: T?, color: Color, route: String,
    onClose: () -> Unit, onPick: (T) -> Unit,
) {
    val shown = rememberLatest(if (open) choices else null)
    OverlayPanel(open, onClose = onClose) {
        val list = shown ?: return@OverlayPanel
        BoxWithConstraints(contentAlignment = Alignment.Center) {
            val keys = list.indices.map { "c:$it" }
            // The chosen option holds the highlight each time the popup opens.
            remember(open) {
                val at = list.indexOfFirst { it.value == selected }
                if (open && at >= 0) FocusMemory.remember(route, "c:$at")
            }
            val target = rememberFocusTarget(route, keys)
            val pictures = list.any { it.art != null }
            val columns = when {
                list.size <= 3 -> list.size
                list.size == 4 -> 4
                pictures || maxWidth < 700.dp -> 4
                else -> 5
            }.coerceAtLeast(1)
            val tileH = if (pictures) 118.dp else if (list.any { it.detail != null }) 78.dp else 56.dp
            val width = (columns * 176).dp.coerceAtMost(maxWidth)
            PanelSurface(Modifier.width(width).heightIn(max = maxHeight)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Palette.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.width(12.dp))
                    ButtonHint("A", "Pick", Palette.Mint)
                    Spacer(Modifier.width(12.dp))
                    ButtonHint("B", "Close", Palette.Lavender)
                }
                Spacer(Modifier.height(6.dp))
                val grid = rememberLazyGridState()
                KeepInView(grid, keys.indexOf(target))
                LazyVerticalGrid(
                    GridCells.Fixed(columns), Modifier.weight(1f, fill = false).fadingEdges(grid), state = grid,
                    contentPadding = PaddingValues(horizontal = FocusRoomSide / 2, vertical = FocusRoom),
                    horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    itemsIndexed(list, key = { i, _ -> "c:$i" }) { i, c ->
                        ChoiceTile(
                            c, c.value == selected, color, Modifier.height(tileH).takesFocus(route, "c:$i", target == "c:$i"),
                        ) { onPick(c.value); onClose() }
                    }
                }
            }
        }
    }
}

/** One option in a [ChoicePopup]. */
@Composable
private fun <T> ChoiceTile(c: PickOption<T>, on: Boolean, color: Color, modifier: Modifier, onClick: () -> Unit) {
    FocusCard(modifier, glow = color, shape = RoundedCornerShape(16.dp), onClick = onClick) {
        Box(
            Modifier.fillMaxSize()
                .background(Brush.verticalGradient(listOf(color.copy(alpha = if (on) 0.3f else 0.1f), color.copy(alpha = if (on) 0.1f else 0.03f)))),
        ) {
            if (c.art != null) {
                Column(Modifier.fillMaxSize().padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { c.art.invoke() }
                    Spacer(Modifier.height(4.dp))
                    ShrinkToFit(Modifier.fillMaxWidth()) { Text(c.label, style = MaterialTheme.typography.titleSmall, maxLines = 1, textAlign = TextAlign.Center) }
                    if (c.detail != null) Text(c.detail, style = MaterialTheme.typography.labelSmall, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            } else {
                Row(Modifier.fillMaxSize().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (c.glyph != null) {
                        GlyphChip(c.glyph, color, size = 30.dp, round = true)
                        Spacer(Modifier.width(8.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(c.label, style = MaterialTheme.typography.titleSmall, maxLines = if (c.detail == null) 2 else 1, overflow = TextOverflow.Ellipsis)
                        if (c.detail != null) Text(c.detail, style = MaterialTheme.typography.labelSmall, color = Palette.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.width(24.dp))
                }
            }
            if (on) TickBadge(true, color, Modifier.align(Alignment.TopEnd).padding(6.dp), size = 20.dp)
        }
    }
}

/**
 * Something switched on and off that has a picture, as a tile: the picture ([art], or [glyph] on a chip), its
 * name, a line on it, and a check. Off, the tile fades back and loses its tint.
 */
@Composable
fun PictureToggle(
    label: String, detail: String?, on: Boolean, color: Color, modifier: Modifier,
    glyph: Glyph? = null, art: (@Composable () -> Unit)? = null, onFocus: () -> Unit = {}, onClick: () -> Unit,
) {
    FocusCard(modifier, glow = color, shape = RoundedCornerShape(16.dp), onFocus = onFocus, onClick = onClick) {
        Column(
            Modifier.fillMaxSize()
                .background(Brush.verticalGradient(listOf(color.copy(alpha = if (on) 0.24f else 0.06f), color.copy(alpha = if (on) 0.06f else 0.02f))))
                .padding(8.dp),
        ) {
            Box(Modifier.fillMaxWidth().weight(1f)) {
                Box(
                    Modifier.fillMaxSize().clip(RoundedCornerShape(11.dp)).background(Palette.NightDeep.copy(alpha = 0.35f))
                        .graphicsLayer { alpha = if (on) 1f else 0.45f },
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        art != null -> art()
                        glyph != null -> GlyphChip(glyph, if (on) color else Palette.Muted, size = 40.dp, round = true)
                    }
                }
                TickBadge(on, color, Modifier.align(Alignment.TopEnd).padding(5.dp))
            }
            Spacer(Modifier.height(6.dp))
            Text(label, style = MaterialTheme.typography.titleSmall, color = if (on) Palette.Text else Palette.Text.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (detail != null) Text(detail, style = MaterialTheme.typography.labelSmall, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * Something switched on and off with an icon and a line about it, as a card for a grid of two or three across: the
 * icon, its name over two lines on it, and a tick. Off, it fades back.
 */
@Composable
fun TickCard(label: String, detail: String?, on: Boolean, glyph: Glyph, color: Color, modifier: Modifier, onFocus: () -> Unit = {}, onClick: () -> Unit) {
    FocusCard(modifier, glow = color, shape = RoundedCornerShape(16.dp), lifts = false, onFocus = onFocus, onClick = onClick) {
        Row(
            Modifier.fillMaxSize()
                .background(Brush.verticalGradient(listOf(color.copy(alpha = if (on) 0.22f else 0.06f), color.copy(alpha = if (on) 0.06f else 0.02f))))
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlyphChip(glyph, if (on) color else Palette.Muted, size = 38.dp, round = true)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f).graphicsLayer { alpha = if (on) 1f else 0.7f }) {
                Text(label, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (detail != null) Text(detail, style = MaterialTheme.typography.labelSmall, color = Palette.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(8.dp))
            TickBadge(on, color)
        }
    }
}

/** A compact on/off chip with a check, for short words without pictures (kinds of details). */
@Composable
fun TickChip(label: String, on: Boolean, color: Color, modifier: Modifier, note: String? = null, onFocus: () -> Unit = {}, onClick: () -> Unit) {
    FocusCard(modifier.height(40.dp), glow = color, shape = RoundedCornerShape(50), lifts = false, onFocus = onFocus, onClick = onClick) {
        Row(
            Modifier.fillMaxSize().background(color.copy(alpha = if (on) 0.2f else 0.06f)).padding(start = 6.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TickBadge(on, color, size = 26.dp)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelLarge, color = if (on) Palette.Text else Palette.Text.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (note != null) Text(note, style = MaterialTheme.typography.labelSmall, color = Palette.Peach, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
