package io.github.zahakj.tandem.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.zahakj.tandem.Selected
import io.github.zahakj.tandem.Selection

/*
 * How a settings row shows its choices, so none is a blind ‹ value › stepper:
 *
 *  - a few short options: a segmented control on the row itself, every option in sight and the one in use filled
 *    with the row's colour; Left and Right move along it, A steps on;
 *  - many options or long names: the value with a small grid mark, and A opens [ChoicePicker], a popup of option
 *    cards (each with its miniature when the setting changes how something looks), the one in use highlighted;
 *  - numbers: a slider with its track, fill, knob and value; Left and Right adjust it. A row whose number the theme
 *    can decide shows a "Theme" stop at the low end, filled while the theme decides.
 */

/** Options that fit on the row as a segmented control: two to four, each short. */
internal fun fitsOnRow(options: List<Pair<String, String>>): Boolean =
    options.size in 2..4 && options.all { it.second.length <= 13 } && options.sumOf { it.second.length } <= 30

/** Every option of a choice row side by side, the one in use filled; a tap picks one. */
@Composable
internal fun Segmented(options: List<Pair<String, String>>, selected: String?, color: Color, focused: Boolean, onPick: (String) -> Unit) {
    // The tap handlers outlive a recomposition; they call the row's latest pick, not the one they were made with.
    val pick by rememberUpdatedState(onPick)
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(Palette.Muted.copy(alpha = if (focused) 0.2f else 0.13f)).padding(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for ((id, label) in options) {
            val on = id == selected
            val fill by animateColorAsState(if (on) color else Color.Transparent, Motion.fade(160), label = "segment")
            Box(
                Modifier.clip(RoundedCornerShape(50)).background(fill)
                    .pointerInput(id) { detectTapGestures { pick(id) } }
                    .padding(horizontal = 11.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label, style = MaterialTheme.typography.labelLarge, maxLines = 1,
                    color = when { on -> Palette.OnAccent; focused -> Palette.Text; else -> Palette.Muted },
                )
            }
        }
    }
}

/**
 * A number row's slider: a "Theme" stop when the theme can decide ([themed] true while it does), the track filled
 * to [fraction] with a knob, and the value. The knob glides (drawn, so a step doesn't recompose the row).
 */
@Composable
internal fun SliderBar(fraction: Float, value: String, color: Color, themed: Boolean?, focused: Boolean) {
    val at = animateFloatAsState(fraction.coerceIn(0f, 1f), Motion.focus(), label = "slider")
    val track = Palette.Muted.copy(alpha = 0.25f)
    val knobInk = Palette.OnAccent
    val fill = if (themed == true) Palette.Muted.copy(alpha = 0.7f) else color
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (themed != null) {
            Box(
                Modifier.clip(RoundedCornerShape(50))
                    .then(if (themed) Modifier.background(color) else Modifier.border(1.dp, Palette.Muted.copy(alpha = 0.5f), RoundedCornerShape(50)))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            ) { Text("Theme", style = MaterialTheme.typography.labelMedium, color = if (themed) Palette.OnAccent else Palette.Muted, maxLines = 1) }
            Spacer(Modifier.width(8.dp))
        }
        Canvas(Modifier.width(132.dp).height(22.dp)) {
            val h = 6.dp.toPx()
            val r = 9.dp.toPx()
            val left = r
            val span = size.width - 2 * r
            val y = size.height / 2
            drawRoundRect(track, Offset(left, y - h / 2), Size(span, h), CornerRadius(h / 2))
            val x = left + span * at.value
            drawRoundRect(fill, Offset(left, y - h / 2), Size((x - left).coerceAtLeast(h), h), CornerRadius(h / 2))
            if (focused) drawCircle(fill.copy(alpha = 0.25f), r * 1.6f, Offset(x, y))
            drawCircle(fill, r, Offset(x, y))
            drawCircle(knobInk.copy(alpha = 0.85f), r * 0.38f, Offset(x, y))
        }
        Spacer(Modifier.width(10.dp))
        Text(value, Modifier.widthIn(min = 44.dp), style = MaterialTheme.typography.labelLarge, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** The small two-by-two mark beside a value that opens a picker. */
@Composable
internal fun PickerMark(color: Color, focused: Boolean) {
    val ink = if (focused) color else Palette.Muted.copy(alpha = 0.6f)
    Canvas(Modifier.size(16.dp)) {
        val s = size.width * 0.42f
        val gap = size.width - 2 * s
        for (x in 0..1) for (y in 0..1) {
            drawRoundRect(ink, Offset(x * (s + gap), y * (s + gap)), Size(s, s), CornerRadius(s * 0.3f))
        }
    }
}

/**
 * The picker a choice row with many options opens (A on the row): a grid of option cards, each with its miniature
 * when the row has one, starting on the option in use. A picks and closes, B closes; either way the highlight goes
 * back to the row (the row keeps its place in [FocusMemory], and [Nav.focusTick] hands focus back).
 */
object ChoicePicker {
    internal const val ROUTE = "choicepicker"

    var row by mutableStateOf<SettingRow?>(null)
        private set

    fun open(r: SettingRow) {
        if (r.options.isNullOrEmpty()) return
        FocusMemory.forget(ROUTE)
        r.selected?.let { FocusMemory.remember(ROUTE, it) }
        row = r
    }

    fun close() { row = null; Nav.focusTick++ }
}

private const val EXTRA_KEY = "\u0000extra"

/** Place once in the app root, over the screens. */
@Composable
fun ChoicePickerOverlay() {
    val open = ChoicePicker.row
    val shown = rememberLatest(open)
    PanelGuard(open != null)
    InterceptButtons(open != null) { true }
    OverlayPanel(open != null, onClose = { ChoicePicker.close() }) {
        val r = shown ?: return@OverlayPanel
        val options = r.options.orEmpty()
        val keys = options.map { it.first } + listOfNotNull(EXTRA_KEY.takeIf { r.pickerExtra != null })
        val target = rememberFocusTarget(ChoicePicker.ROUTE, keys)
        val grid = rememberLazyGridState(initialFirstVisibleItemIndex = options.indexOfFirst { it.first == r.selected }.coerceAtLeast(0))
        val look = r.optionLook
        PanelSurface(Modifier.fillMaxWidth(if (look != null) 0.86f else 0.7f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                r.icon?.let { GlyphChip(it, r.color, 40.dp); Spacer(Modifier.width(12.dp)) }
                Column(Modifier.weight(1f)) {
                    Text(r.label, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "Now: " + (options.firstOrNull { it.first == r.selected }?.second ?: r.value),
                        style = MaterialTheme.typography.titleSmall, color = r.color, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(12.dp))
                ButtonHint("A", "Choose", r.color)
                Spacer(Modifier.width(14.dp))
                ButtonHint("B", "Back", Palette.Muted)
            }
            Spacer(Modifier.height(10.dp))
            PaddedScrolling {
                LazyVerticalGrid(
                    GridCells.Adaptive(if (look != null) 176.dp else 190.dp),
                    Modifier.fillMaxWidth().weight(1f, fill = false).fadingEdges(grid),
                    state = grid,
                    contentPadding = PaddingValues(horizontal = FocusRoomSide, vertical = FocusRoom),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    itemsIndexed(options, key = { _, it -> it.first }) { _, (id, label) ->
                        OptionCard(
                            label, id == r.selected, r.color, look?.let { l -> { l(id) } },
                            Modifier.takesFocus(ChoicePicker.ROUTE, id, id == target),
                            onFocus = { Selection.current = Selected.Setting(r.label, label, r.help, r.color, "Choose") },
                        ) {
                            ChoicePicker.close()
                            if (id != r.selected) r.pick?.invoke(id)
                            Sounds.toggle()
                        }
                    }
                    r.pickerExtra?.let { (label, action) ->
                        item(key = EXTRA_KEY) {
                            OptionCard(
                                label, false, r.color, if (look != null) ({ ExtraArt(r.color) }) else null,
                                Modifier.takesFocus(ChoicePicker.ROUTE, EXTRA_KEY, target == EXTRA_KEY),
                                onFocus = { Selection.current = Selected.Setting(r.label, label, r.help, r.color, "Open") },
                            ) { ChoicePicker.close(); action() }
                        }
                    }
                }
            }
        }
    }
}

/** One option in the picker: its miniature (when it has one) over its name, and a tick on the one in use. */
@Composable
private fun OptionCard(label: String, current: Boolean, color: Color, art: (@Composable () -> Unit)?, modifier: Modifier, onFocus: () -> Unit, onClick: () -> Unit) {
    FocusCard(modifier.fillMaxWidth(), glow = color, shape = RoundedCornerShape(16.dp), onFocus = onFocus, onClick = onClick) {
        Column(
            Modifier.fillMaxWidth()
                .then(if (current) Modifier.border(2.dp, color, RoundedCornerShape(16.dp)) else Modifier),
        ) {
            if (art != null) {
                Box(Modifier.fillMaxWidth().aspectRatio(1.6f).clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)).background(Palette.NightDeep)) { art() }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(label, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis, color = if (current) color else Palette.Text)
                if (current) {
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.size(20.dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
                        Text("✓", style = MaterialTheme.typography.labelMedium, color = Palette.OnAccent)
                    }
                }
            }
        }
    }
}

/** The picture on an extra card (Type a colour…): a plus on the row's colour. */
@Composable
private fun ExtraArt(color: Color) {
    Box(Modifier.fillMaxSize().background(color.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
        Text("+", style = MaterialTheme.typography.displaySmall, color = color)
    }
}
