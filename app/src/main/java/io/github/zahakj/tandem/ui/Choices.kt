package io.github.zahakj.tandem.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.zahakj.tandem.R
import io.github.zahakj.tandem.Selected
import io.github.zahakj.tandem.Selection
import io.github.zahakj.tandem.Tx

/*
 * Choices that show their options, the one kit every screen uses, so none is a blind ‹ value › stepper:
 *
 *  - a few short options: a segmented track ([SegmentTrack]), every option in sight and the one in use lit by a pill
 *    that slides to it. Left and Right move it and stop at the ends, A steps round, a tap picks;
 *  - many options or long names (a system, a language, a look): the value in a pill with a grid mark ([PickValue]),
 *    and A opens [ChoicePopup], a grid of option cards, each with its picture when it has one, the one in use ticked
 *    and highlighted when it opens. A picks, B closes; either way the highlight goes back to the row;
 *  - numbers: a slider ([ValueTrack]) with its track, fill, knob and value; Left and Right adjust it. A number the
 *    theme can decide shows a "Theme" stop at the low end, filled while the theme decides;
 *  - things switched on and off that have a picture or an icon (kinds of artwork, backup parts): tiles with a tick
 *    ([PictureToggle], [TickCard], [TickChip]), not rows of "On" and "Off".
 *
 * Settings rows (SettingRowCard) draw the same pieces as the other screens' rows, so a choice looks and behaves the
 * same wherever it is.
 */

/** One option of a choice: its [label], a line on what it means, and a picture ([art]) or an icon ([glyph]). */
class PickOption<T>(
    val value: T,
    val label: String,
    val detail: String? = null,
    val glyph: Glyph? = null,
    val art: (@Composable () -> Unit)? = null,
)

/** How tall a segmented track (and a [PickValue]) is. */
private val TrackHeight = 30.dp

/** Options that fit on a settings row as a segmented track: two to four, each short. */
internal fun fitsOnRow(options: List<Pair<String, String>>): Boolean =
    options.size in 2..4 && options.all { labelWidth(it.second) <= 13 } && options.sumOf { labelWidth(it.second) } <= 30

/** A label's width counted in Latin letters: a Japanese or Chinese character is about two of them wide. */
internal fun labelWidth(s: String): Int = s.sumOf { if (CjkBreaks.isCjk(it)) 2 else 1 }

/**
 * [labels] side by side in a rounded track, the [selected] one lit by a pill that slides to it. [onPick] makes each
 * option tappable; the D-pad is the row's (see [SegmentRow], SettingRowCard). Given a width, the options share it;
 * without one each gets the width of the longest (within what the row leaves), so the track sits at the end of a row.
 */
@Composable
fun SegmentTrack(labels: List<String>, selected: Int, color: Color, modifier: Modifier = Modifier, onPick: ((Int) -> Unit)? = null) {
    if (labels.isEmpty()) return
    val n = labels.size
    val at by animateFloatAsState(selected.coerceIn(0, n - 1).toFloat(), Motion.focus(), label = "segment")
    // The tap handlers outlive a recomposition; they call the row's latest pick, not the one they were made with.
    val pick by rememberUpdatedState(onPick)
    val lit = selected in labels.indices
    Layout(
        content = {
            labels.forEachIndexed { i, label ->
                val ink by animateColorAsState(if (i == selected) Palette.OnAccent else Palette.Text.copy(alpha = 0.78f), Motion.fade(160), label = "segment-ink")
                Box(
                    Modifier.fillMaxHeight()
                        .then(if (onPick != null) Modifier.clip(RoundedCornerShape(50)).clickable(remember { MutableInteractionSource() }, null) { pick?.invoke(i) } else Modifier)
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    ShrinkToFit { Text(label, style = MaterialTheme.typography.labelLarge, color = ink, maxLines = 1) }
                }
            }
        },
        // The pill is drawn, so its glide doesn't recompose the labels.
        modifier = modifier.height(TrackHeight).clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.1f)).padding(3.dp)
            .drawBehind {
                if (!lit) return@drawBehind
                val w = size.width / n
                // Options run from the start side, so in right to left the first is on the right and the pill with it.
                mirroredInRtl { drawRoundRect(color, Offset(w * at, 0f), Size(w, size.height), CornerRadius(size.height / 2)) }
            },
    ) { measurables, constraints ->
        val h = constraints.maxHeight
        val seg = if (constraints.hasFixedWidth) constraints.maxWidth / n else {
            val natural = measurables.maxOf { it.maxIntrinsicWidth(h) }
            val room = if (constraints.hasBoundedWidth) constraints.maxWidth / n else natural
            natural.coerceAtMost(room).coerceAtLeast(constraints.minWidth / n)
        }
        val placeables = measurables.map { it.measure(Constraints.fixed(seg, h)) }
        layout(seg * n, h) { placeables.forEachIndexed { i, p -> p.placeRelative(i * seg, 0) } }
    }
}

/**
 * A row for a choice with a few short options: its [label] (and a line, [detail]) beside a [SegmentTrack] of
 * [labels]. One stop for the D-pad: Left and Right move the choice and stop at the ends, A steps round to the next.
 * [trackWidth] is how wide the track is; null puts it under the label, across the whole row (a narrow column).
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

/** The chosen value of a choice that opens a [ChoicePopup]: the value in a pill, with the grid mark that says so. */
@Composable
fun PickValue(value: String, color: Color, modifier: Modifier = Modifier) {
    Row(
        modifier.heightIn(min = TrackHeight).clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.16f)).padding(start = 12.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(value, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.labelLarge, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.width(6.dp))
        // The mark for "opens a grid of options", where a stepper had its arrows.
        GlyphIcon(Glyph.Grid, color.copy(alpha = 0.8f), Modifier.size(14.dp))
    }
}

/**
 * A row for a choice with many options: its [label], a line ([detail]), and the chosen [value] with the choice's
 * picture ([art]) when it has one. A opens the options ([ChoicePopup]).
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
            PickValue(value, color)
        }
    }
}

/**
 * A number as a slider: a track filled up to [fraction] (0..1) with a knob there, which glides when the number
 * changes (drawn, so a step doesn't recompose the row). [value] shows the number after it; [start] and [end] name
 * the ends, under it. [themed] adds a "Theme" stop in front for a number the theme can decide, filled (and the track
 * greyed) while it does; null for numbers without one. [trackWidth] fixes the track's width; null lets it fill.
 */
@Composable
fun ValueTrack(
    fraction: Float, color: Color, modifier: Modifier = Modifier, start: String? = null, end: String? = null,
    value: String? = null, themed: Boolean? = null, focused: Boolean = false, trackWidth: Dp? = null,
) {
    val at = animateFloatAsState(fraction.coerceIn(0f, 1f), Motion.focus(), label = "track")
    val fill = if (themed == true) Palette.Muted.copy(alpha = 0.7f) else color
    val knobInk = Palette.OnAccent
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (themed != null) {
            Box(
                Modifier.clip(RoundedCornerShape(50))
                    .then(if (themed) Modifier.background(color) else Modifier.border(1.dp, Palette.Muted.copy(alpha = 0.5f), RoundedCornerShape(50)))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            ) { Text(Tx.s(R.string.settings_slider_theme), style = MaterialTheme.typography.labelMedium, color = if (themed) Palette.OnAccent else Palette.Muted, maxLines = 1) }
            Spacer(Modifier.width(8.dp))
        }
        Column(if (trackWidth != null) Modifier.width(trackWidth) else Modifier.weight(1f)) {
            // Drawn from the start side: in right to left the fill grows leftwards (Left on the D-pad adds).
            Canvas(Modifier.fillMaxWidth().height(20.dp).mirroredInRtl()) {
                val h = 6.dp.toPx()
                val r = 8.dp.toPx()
                val span = size.width - 2 * r
                val y = size.height / 2
                drawRoundRect(fill.copy(alpha = 0.2f), Offset(r, y - h / 2), Size(span, h), CornerRadius(h / 2))
                val x = r + span * at.value
                drawRoundRect(fill, Offset(r, y - h / 2), Size((x - r).coerceAtLeast(h), h), CornerRadius(h / 2))
                if (focused) drawCircle(fill.copy(alpha = 0.25f), r * 1.6f, Offset(x, y))
                drawCircle(fill, r, Offset(x, y))
                drawCircle(knobInk.copy(alpha = 0.9f), r * 0.45f, Offset(x, y))
            }
            if (start != null || end != null) {
                Row(Modifier.fillMaxWidth().padding(top = 2.dp)) {
                    Text(start.orEmpty(), Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = Palette.Muted, maxLines = 1)
                    Text(end.orEmpty(), style = MaterialTheme.typography.labelSmall, color = Palette.Muted, maxLines = 1)
                }
            }
        }
        if (value != null) {
            Spacer(Modifier.width(10.dp))
            Text(value, Modifier.widthIn(min = 44.dp), style = MaterialTheme.typography.labelLarge, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
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

/** The focus key of the extra card at the end of a [ChoicePopup]. */
private const val EXTRA_KEY = "extra"

/**
 * The options of a choice as a grid of cards in a popup: each with its picture or icon, its name and a line on it;
 * the one in use ticked and tinted, and highlighted when the popup opens. A picks and closes, B closes; [onClose]
 * hands the highlight back to the row that opened it (bump [Nav.focusTick]). [extra] is one more card at the end
 * that does something else (Type a colour…). [onFocus] hears the option highlighted (null for the extra card).
 *
 * Cards with pictures are sized so every row of them fits the popup whole when it can; when there are too many
 * the grid scrolls, keeping the highlighted card in view.
 */
@Composable
fun <T> ChoicePopup(
    open: Boolean, title: String, subtitle: String?, choices: List<PickOption<T>>, selected: T?, color: Color, route: String,
    onClose: () -> Unit, glyph: Glyph? = null, extra: Pair<String, () -> Unit>? = null,
    onFocus: ((PickOption<T>?) -> Unit)? = null, onPick: (T) -> Unit,
) {
    val shown = rememberLatest(if (open) choices else null)
    PanelGuard(open)
    // Shoulder buttons, Start and the rest stay with the popup, not the screen under it.
    InterceptButtons(open) { true }
    OverlayPanel(open, onClose = onClose) {
        val list = shown ?: return@OverlayPanel
        BoxWithConstraints(contentAlignment = Alignment.Center) {
            val keys = list.indices.map { "c:$it" } + listOfNotNull(EXTRA_KEY.takeIf { extra != null })
            // The option in use holds the highlight each time the popup opens.
            remember(open) {
                if (open) {
                    FocusMemory.forget(route)
                    val at = list.indexOfFirst { it.value == selected }
                    if (at >= 0) FocusMemory.remember(route, "c:$at")
                }
            }
            val target = rememberFocusTarget(route, keys)
            val pictures = list.any { it.art != null }
            val details = list.any { it.detail != null }
            val count = keys.size
            val columns = when {
                count <= 4 -> count
                pictures || maxWidth < 700.dp -> 4
                else -> 5
            }.coerceAtLeast(1)
            val width = (columns * (if (pictures) 196 else 176)).dp.coerceAtMost(maxWidth)
            val now = list.firstOrNull { it.value == selected }?.label
            PanelSurface(Modifier.width(width).heightIn(max = maxHeight)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    glyph?.let { GlyphChip(it, color, 40.dp); Spacer(Modifier.width(12.dp)) }
                    Column(Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (now != null) Text(Tx.s(R.string.settings_picker_now, now), style = MaterialTheme.typography.titleSmall, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Palette.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.width(12.dp))
                    ButtonHint("A", Tx.s(R.string.common_choose), color)
                    Spacer(Modifier.width(12.dp))
                    ButtonHint("B", Tx.s(R.string.common_back), Palette.Lavender)
                }
                Spacer(Modifier.height(6.dp))
                // The space left under the title, so picture cards can be sized to fit it.
                BoxWithConstraints(Modifier.weight(1f, fill = false)) {
                    val gap = 10.dp
                    val rows = (count + columns - 1) / columns
                    val tileH = if (!pictures) (if (details) 78.dp else 56.dp) else {
                        val band = if (details) 50.dp else 34.dp
                        val cardW = (maxWidth - FocusRoomSide - gap * (columns - 1)) / columns
                        val ideal = cardW / 1.6f + band + 6.dp
                        val fit = (maxHeight - FocusRoom * 2 - gap * (rows - 1)) / rows
                        // Never so small the picture stops reading; past that the grid scrolls instead.
                        minOf(ideal, fit).coerceAtLeast(band + 64.dp)
                    }
                    val grid = rememberLazyGridState()
                    KeepInView(grid, keys.indexOf(target))
                    LazyVerticalGrid(
                        GridCells.Fixed(columns), Modifier.fadingEdges(grid), state = grid,
                        contentPadding = PaddingValues(horizontal = FocusRoomSide / 2, vertical = FocusRoom),
                        horizontalArrangement = Arrangement.spacedBy(gap), verticalArrangement = Arrangement.spacedBy(gap),
                    ) {
                        itemsIndexed(list, key = { i, _ -> "c:$i" }) { i, c ->
                            ChoiceTile(
                                c.label, c.detail, c.glyph, c.art, c.value == selected, color, pictures,
                                Modifier.height(tileH).takesFocus(route, "c:$i", target == "c:$i"),
                                onFocus = { onFocus?.invoke(c) },
                            ) { onPick(c.value); Sounds.toggle(); onClose() }
                        }
                        if (extra != null) item(key = EXTRA_KEY) {
                            ChoiceTile(
                                extra.first, null, Glyph.Plus.takeIf { !pictures }, if (pictures) ({ ExtraArt(color) }) else null, false, color, pictures,
                                Modifier.height(tileH).takesFocus(route, EXTRA_KEY, target == EXTRA_KEY),
                                onFocus = { onFocus?.invoke(null) },
                            ) { onClose(); extra.second() }
                        }
                    }
                }
            }
        }
    }
}

/**
 * One option in a [ChoicePopup]. In a popup with [pictures], the picture fills the top of the card and the name sits
 * under it; otherwise the icon sits beside the name. The one in use ([on]) is tinted deeper, its name in the colour,
 * with a tick in the corner.
 */
@Composable
private fun ChoiceTile(
    label: String, detail: String?, glyph: Glyph?, art: (@Composable () -> Unit)?, on: Boolean, color: Color, pictures: Boolean,
    modifier: Modifier, onFocus: () -> Unit, onClick: () -> Unit,
) {
    FocusCard(modifier.fillMaxWidth(), glow = color, shape = RoundedCornerShape(16.dp), onFocus = onFocus, onClick = onClick) {
        Box(
            Modifier.fillMaxSize()
                .background(Brush.verticalGradient(listOf(color.copy(alpha = if (on) 0.3f else 0.1f), color.copy(alpha = if (on) 0.1f else 0.03f)))),
        ) {
            val ink = if (on) color else Palette.Text
            if (pictures) {
                Column(Modifier.fillMaxSize().padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(11.dp)).background(Palette.NightDeep.copy(alpha = 0.35f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        when {
                            art != null -> art()
                            glyph != null -> GlyphChip(glyph, color, size = 40.dp, round = true)
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    ShrinkToFit(Modifier.fillMaxWidth()) { Text(label, style = MaterialTheme.typography.titleSmall, color = ink, maxLines = 1, textAlign = TextAlign.Center) }
                    if (detail != null) Text(detail, style = MaterialTheme.typography.labelSmall, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            } else {
                Row(Modifier.fillMaxSize().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (glyph != null) {
                        GlyphChip(glyph, color, size = 30.dp, round = true)
                        Spacer(Modifier.width(8.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(label, style = MaterialTheme.typography.titleSmall, color = ink, maxLines = if (detail == null) 2 else 1, overflow = TextOverflow.Ellipsis)
                        if (detail != null) Text(detail, style = MaterialTheme.typography.labelSmall, color = Palette.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.width(24.dp))
                }
            }
            if (on) TickBadge(true, color, Modifier.align(Alignment.TopEnd).padding(6.dp), size = 20.dp)
        }
    }
}

/** The picture on an extra card (Type a colour…): a plus on the choice's colour. */
@Composable
private fun ExtraArt(color: Color) {
    Box(Modifier.fillMaxSize().background(color.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
        Text("+", style = MaterialTheme.typography.displaySmall, color = color)
    }
}

/**
 * The popup a settings choice row with many options opens (A on the row): a [ChoicePopup] of the row's options, each
 * card with the row's miniature of it when it has one. The row keeps its place in [FocusMemory], and [close] hands
 * the highlight back to it.
 */
object ChoicePicker {
    internal const val ROUTE = "choicepicker"

    var row by mutableStateOf<SettingRow?>(null)
        private set

    fun open(r: SettingRow) {
        if (r.options.isNullOrEmpty()) return
        row = r
    }

    fun close() { row = null; Nav.focusTick++ }
}

/** Place once in the app root, over the screens. */
@Composable
fun ChoicePickerOverlay() {
    val open = ChoicePicker.row
    val r = rememberLatest(open) ?: return
    val look = r.optionLook
    val choices = remember(r) {
        r.options.orEmpty().map { (id, label) -> PickOption(id, label, art = look?.let { l -> { l(id) } }) }
    }
    ChoicePopup(
        open != null, r.label, null, choices, r.selected, r.color, ChoicePicker.ROUTE,
        onClose = { ChoicePicker.close() }, glyph = r.icon, extra = r.pickerExtra,
        onFocus = { c ->
            Selection.current = if (c != null) Selected.Setting(r.label, c.label, r.help, r.color, Tx.s(R.string.common_choose))
            else Selected.Setting(r.label, r.pickerExtra?.first.orEmpty(), r.help, r.color, Tx.s(R.string.settings_a_open))
        },
    ) { id -> if (id != r.selected) r.pick?.invoke(id) }
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
