package io.github.zahakj.tandem.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.zahakj.tandem.Binding
import io.github.zahakj.tandem.Bindings
import io.github.zahakj.tandem.Button
import io.github.zahakj.tandem.Chords
import io.github.zahakj.tandem.Command
import io.github.zahakj.tandem.R
import io.github.zahakj.tandem.Selected
import io.github.zahakj.tandem.Selection
import io.github.zahakj.tandem.Tx
import io.github.zahakj.tandem.data.ControllerStore
import io.github.zahakj.tandem.data.Store
import kotlinx.coroutines.delay

/**
 * Settings › Controls › Buttons › Rebind buttons: every command Tandem has on a button (Bindings.kt) with what it is
 * bound to. A on a command listens for its new button, chord or hold for [LISTEN_MS]; the Back button cancels. A
 * binding another command holds is offered as a swap; one that can't work is refused with the reason. X puts a
 * command back on its default, Y takes its button away (not for Open and Back), and the first row resets them all.
 */
private const val LISTEN_MS = 5_000L
private const val RESET_ALL = "resetall"

/** What the popup over the list is doing. */
private sealed interface Rebinding {
    val command: Command

    /**
     * Waiting for buttons; [why] says why the last attempt was refused (kept as text while the popup is up: a
     * language change meanwhile shows it in the old one until the next attempt).
     */
    data class Listening(override val command: Command, val why: String? = null) : Rebinding

    /** [plan] would take the binding from other commands: swap, or cancel. */
    data class Asking(override val command: Command, val binding: Binding, val plan: Bindings.Plan.Swap) : Rebinding
}

@Composable
fun RebindScreen() {
    val route = Route.Rebind.key
    val map = ControllerStore.live
    val style = Store.settings.look.prompts
    var popup by remember { mutableStateOf<Rebinding?>(null) }
    var focused by remember { mutableStateOf<Command?>(Command.named(FocusMemory.get(route) ?: "")) }

    fun store(m: Map<Command, Binding?>, c: Command, said: String) {
        ControllerStore.setBindings(m)
        Sounds.confirm()
        Toasts.show(said)
        FocusMemory.remember(route, c.name)
        Nav.focusTick++
    }

    /** Carries out [plan] for [c]: stores it, asks about a swap, or says why not. */
    fun apply(c: Command, b: Binding?, plan: Bindings.Plan, listening: Boolean) {
        when (plan) {
            is Bindings.Plan.Done -> {
                popup = null
                store(plan.map, c, if (b == null) Tx.s(R.string.controls_rebind_set_none, c.label) else Tx.s(R.string.controls_rebind_set, c.label, Prompts.bindingText(b, style)))
            }
            is Bindings.Plan.Swap -> popup = if (b != null) Rebinding.Asking(c, b, plan) else null
            is Bindings.Plan.Refused -> {
                Sounds.error()
                if (listening) popup = Rebinding.Listening(c, plan.why) else Toasts.show(plan.why)
            }
            Bindings.Plan.Same -> { popup = null; Toasts.show(Tx.s(R.string.controls_rebind_same, c.label)) }
        }
    }

    val keys = listOf(RESET_ALL) + Command.entries.map { it.name }
    val target = rememberFocusTarget(route, keys)
    val state = rememberRouteListState(route)
    KeepInView(state, keys.indexOf(target) + 1)

    // X resets the highlighted command, Y takes its button away. A starts listening (the row's own click).
    InterceptButtons(popup == null) { b ->
        val c = focused ?: return@InterceptButtons false
        when (b) {
            Button.X -> { apply(c, c.default, Bindings.resetPlan(ControllerStore.bindings, c), listening = false); true }
            Button.Y -> { apply(c, null, Bindings.plan(ControllerStore.bindings, c, null), listening = false); true }
            else -> false
        }
    }

    Row(Modifier.fillMaxSize().padding(start = 14.dp, end = 22.dp)) {
        LazyColumn(
            Modifier.weight(1.3f).fillMaxHeight().fadingEdges(state),
            state = state,
            contentPadding = PaddingValues(start = FocusRoomSide, end = FocusRoomSide, top = FocusRoom, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "h:all") { SettingsHeader(Tx.s(R.string.controls_rebind_title), note = Bindings.changed(map).takeIf { it > 0 }?.let { Tx.p(R.plurals.controls_rebind_changes, it) }) }
            item(key = RESET_ALL) {
                FocusCard(
                    Modifier.fillMaxWidth().takesFocus(route, RESET_ALL, target == RESET_ALL), glow = Palette.Peach, shape = RoundedCornerShape(16.dp),
                    onFocus = {
                        FocusMemory.remember(route, RESET_ALL); focused = null
                        Selection.current = Selected.Setting(
                            Tx.s(R.string.controls_rebind_reset_all), "", Tx.s(R.string.controls_rebind_reset_all_help), Palette.Peach,
                            action = Tx.s(R.string.controls_rebind_reset),
                        )
                    },
                    onClick = {
                        if (Bindings.changed(ControllerStore.bindings) == 0) Toasts.show(Tx.s(R.string.controls_rebind_already_default))
                        else {
                            val before = ControllerStore.bindings
                            ControllerStore.setBindings(Bindings.defaults)
                            Undo.offer(Tx.s(R.string.controls_rebind_reset_done)) { ControllerStore.setBindings(before) }
                        }
                    },
                ) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                        GlyphChip(Glyph.Refresh, Palette.Peach)
                        Spacer(Modifier.width(12.dp))
                        Text(Tx.s(R.string.controls_rebind_reset_all), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            item(key = "h:commands") { SettingsHeader(Tx.s(R.string.controls_rebind_commands)) }
            items(Command.entries, key = { it.name }) { c ->
                CommandRow(c, map[c], style, Modifier.takesFocus(route, c.name, target == c.name), onFocus = {
                    FocusMemory.remember(route, c.name); focused = c
                    Selection.current = Selected.Setting(
                        c.label, map[c]?.let { Prompts.bindingText(it, style) } ?: Tx.s(R.string.controls_binding_none_value), c.help, Palette.Lavender,
                        action = Tx.s(R.string.controls_rebind_rebind),
                    )
                }) { popup = Rebinding.Listening(c) }
            }
        }
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier.weight(0.8f).fillMaxHeight().padding(top = FocusRoom, bottom = 22.dp).clip(RoundedCornerShape(24.dp))
                .background(Palette.Card.copy(alpha = 0.72f)).padding(18.dp),
        ) { CommandDetails(focused, map, style) }
    }

    RebindPopup(
        popup, style,
        onCaptured = { c, b -> apply(c, b, Bindings.plan(ControllerStore.bindings, c, b), listening = true) },
        onSwap = { a -> popup = null; store(a.plan.map, a.command, Tx.s(R.string.controls_rebind_set_swapped, a.command.label, Prompts.bindingText(a.binding, style))) },
        onClose = { popup = null; Nav.focusTick++ },
    )
}

@Composable
private fun CommandRow(c: Command, b: Binding?, style: String, focus: Modifier, onFocus: () -> Unit, onClick: () -> Unit) {
    FocusCard(Modifier.fillMaxWidth().then(focus), glow = Palette.Lavender, shape = RoundedCornerShape(16.dp), onFocus = onFocus, onClick = onClick) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(c.label, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (b != c.default) { Pill(Tx.s(R.string.controls_rebind_changed), Palette.Butter); Spacer(Modifier.width(8.dp)) }
            BindingGlyph(b, style)
        }
    }
}

/** A binding drawn as the hints draw it: one button's glyph, or a chord or hold in words. */
@Composable
private fun BindingGlyph(b: Binding?, style: String, color: Color = Palette.Lavender) {
    val single = b?.takeIf { !it.hold }?.single
    val p = if (single != null) Prompts.physical(single.name, style) else null
    val text = p?.glyph ?: b?.let { Prompts.bindingText(it, style) } ?: Tx.s(R.string.controls_binding_none_value)
    Box(
        Modifier.height(24.dp).widthIn(min = 24.dp).clip(if (p?.square == true) RoundedCornerShape(6.dp) else RoundedCornerShape(50))
            .background(p?.color ?: color.copy(alpha = if (b == null) 0.12f else 0.85f)).padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text, color = p?.onColor ?: if (b == null) Palette.Muted else Palette.OnAccent,
            style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun CommandDetails(c: Command?, map: Map<Command, Binding?>, style: String) {
    Column(Modifier.fillMaxSize()) {
        if (c == null) {
            Text(Tx.s(R.string.controls_rebind_reset_all), style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(8.dp))
            Text(
                Tx.s(R.string.controls_rebind_reset_all_detail),
                style = MaterialTheme.typography.bodyMedium, color = Palette.Muted, maxLines = 6, overflow = TextOverflow.Ellipsis,
            )
        } else {
            Text(c.label, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                BindingGlyph(map[c], style)
                Spacer(Modifier.width(10.dp))
                Text(
                    Tx.s(R.string.controls_rebind_default, c.default?.let { Prompts.bindingText(it, style) } ?: Tx.s(R.string.controls_binding_none)),
                    style = MaterialTheme.typography.labelMedium, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(c.help, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.bodyMedium, color = Palette.Muted, maxLines = 7, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.weight(1f))
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ButtonHint("A", Tx.s(if (c == null) R.string.controls_rebind_reset_all else R.string.controls_rebind_rebind), Palette.Mint)
            if (c != null) {
                ButtonHint("X", Tx.s(R.string.controls_rebind_reset), Palette.Butter)
                if (c !in Bindings.essential) ButtonHint("Y", Tx.s(R.string.controls_rebind_none_hint), Palette.Peach)
            }
            ButtonHint("B", Tx.s(R.string.common_back), Palette.Lavender)
        }
    }
}

/** Listening for a new binding, then the swap question. Every press goes to the capture while it listens (Chords). */
@Composable
private fun RebindPopup(
    popup: Rebinding?, style: String,
    onCaptured: (Command, Binding) -> Unit, onSwap: (Rebinding.Asking) -> Unit, onClose: () -> Unit,
) {
    val listening = popup as? Rebinding.Listening
    PanelGuard(popup != null)
    // The capture lives exactly as long as the listening popup: leaving the screen, or Tandem, ends it.
    DisposableEffect(listening) {
        if (listening != null) {
            Chords.capture = { b ->
                // The Back button pressed on its own cancels, as B does everywhere.
                if (b == ControllerStore.bindings[Command.Back]) onClose() else onCaptured(listening.command, b)
            }
        }
        onDispose { if (listening != null) Chords.capture = null }
    }
    var left by remember { mutableStateOf(LISTEN_MS) }
    LaunchedEffect(listening) {
        if (listening == null) return@LaunchedEffect
        left = LISTEN_MS
        while (left > 0) {
            delay(100)
            // Buttons still held: the binding is being made, so the time waits for it.
            if (Chords.captured.isEmpty()) left -= 100
        }
        Toasts.show(Tx.s(R.string.controls_rebind_nothing_pressed, listening.command.label))
        onClose()
    }
    OverlayPanel(popup != null, onClose = onClose) {
        val p = rememberLatest(popup) ?: return@OverlayPanel
        PanelSurface(Modifier.widthIn(min = 360.dp, max = 560.dp)) {
            when (p) {
                is Rebinding.Listening -> {
                    Text(Tx.s(R.string.controls_rebind_listen_title, p.command.label), style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        Tx.s(R.string.controls_rebind_listen_how),
                        style = MaterialTheme.typography.bodyMedium, color = Palette.Muted, maxLines = 3, overflow = TextOverflow.Ellipsis,
                    )
                    p.why?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = Palette.Peach, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val sofar = Chords.captured
                        Box(Modifier.weight(1f)) {
                            if (sofar.isEmpty()) Text(Tx.s(R.string.controls_rebind_waiting), style = MaterialTheme.typography.titleMedium, color = Palette.Muted, maxLines = 1)
                            else BindingGlyph(Binding(sofar.toSet()), style, Palette.Mint)
                        }
                        Pill(Tx.s(R.string.controls_rebind_seconds, ((left + 999) / 1000).toInt()), Palette.Lavender)
                    }
                    Spacer(Modifier.height(14.dp))
                    ButtonHint("B", Tx.s(R.string.controls_rebind_cancel), Palette.Lavender)
                }
                is Rebinding.Asking -> {
                    val names = p.plan.others.map { it.label }.reduce { a, b -> Tx.s(R.string.controls_rebind_and, a, b) }
                    val old = p.plan.map[p.plan.others.first()]
                    Text(Tx.s(R.string.controls_rebind_swap_title, names), style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        old?.let { Tx.s(R.string.controls_rebind_swap_moves, Prompts.bindingText(p.binding, style), names, p.command.label, Prompts.bindingText(it, style)) }
                            ?: Tx.s(R.string.controls_rebind_swap_none, Prompts.bindingText(p.binding, style), names, p.command.label),
                        style = MaterialTheme.typography.bodyMedium, color = Palette.Muted, maxLines = 4, overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(14.dp))
                    val ask = rememberFocusTarget("rebind:ask", listOf("swap", "cancel"))
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        PillButton(Tx.s(R.string.controls_rebind_swap), Palette.Mint, Modifier.takesFocus("rebind:ask", "swap", ask == "swap")) { onSwap(p) }
                        PillButton(Tx.s(R.string.controls_rebind_cancel), Palette.Lavender, Modifier.takesFocus("rebind:ask", "cancel", ask == "cancel"), onClick = onClose)
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        ButtonHint("A", Tx.s(R.string.common_choose), Palette.Mint)
                        ButtonHint("B", Tx.s(R.string.controls_rebind_cancel), Palette.Lavender)
                    }
                }
            }
        }
    }
}
