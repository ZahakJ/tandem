package io.github.zahakj.tandem.ui

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.zahakj.tandem.CompanionActivity
import io.github.zahakj.tandem.CompanionInfo
import io.github.zahakj.tandem.DeckKey
import io.github.zahakj.tandem.DeckKeyKind
import io.github.zahakj.tandem.DeckLayout
import io.github.zahakj.tandem.DeckLayouts
import io.github.zahakj.tandem.DeckModifiers
import io.github.zahakj.tandem.DeckMouse
import io.github.zahakj.tandem.DeckPointer
import io.github.zahakj.tandem.DeckRepeat
import io.github.zahakj.tandem.DeckScroll
import io.github.zahakj.tandem.DeckSession
import io.github.zahakj.tandem.DeckTap
import io.github.zahakj.tandem.GameCompanion
import io.github.zahakj.tandem.data.HomeStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/*
 * The Deck: a keyboard and a trackpad on the bottom screen for a PC game (GameNative, Winlator) or a Moonlight
 * stream playing on the top one, a mode of the companion next to Game art and Tandem companion (GameCompanion.kt).
 * What is typed goes to the game through Shizuku (Deck.kt): directly into the input manager when Android lets
 * Tandem at it, else line by line through a shell; the status chip says which. Touch only: the controller stays with
 * the game. Keys are drawn like the controller keyboard's (TextEntry.kt, KeyFace).
 */

private val KeyGap = 4.dp

@Composable
fun DeckPanel(info: CompanionInfo, activity: CompanionActivity) {
    val ctx = LocalContext.current
    val s = HomeStore.settings
    DisposableEffect(info.key) {
        val token = DeckSession.open(ctx)
        onDispose { DeckSession.close(token) }
    }
    // The companion may be in a fresh process (brought back after a restart) where nothing made the sounds yet.
    LaunchedEffect(Unit) { Sounds.init(ctx) }
    var layoutId by remember { mutableStateOf(s.deckLayout) }
    var numpad by remember { mutableStateOf(false) }
    var fn by remember { mutableStateOf(false) }
    val layout = remember(layoutId, numpad, fn) { if (layoutId == DeckLayouts.GAME) DeckLayouts.game else DeckLayouts.full(numpad, fn) }
    val toggles = remember(numpad, fn) { setOfNotNull(DeckLayouts.TOGGLE_NUMPAD.takeIf { numpad }, DeckLayouts.TOGGLE_FN.takeIf { fn }) }
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Deck", style = MaterialTheme.typography.titleMedium, maxLines = 1)
            StatusChip(ctx)
            Spacer(Modifier.weight(1f))
            Chip("Game art", Palette.Lavender) { activity.setMode(GameCompanion.ART) }
            Chip("Companion", Palette.Mint) { activity.setMode(GameCompanion.TANDEM) }
        }
        if (!activity.gameOnTop) {
            // The game is on the bottom screen, and this is on the top one: keys would land on this very window.
            Text(
                "The Deck types into a game on the top screen. This game plays on the bottom one, so there is nothing for it to do here.",
                Modifier.fillMaxWidth().padding(top = 20.dp), style = MaterialTheme.typography.bodyLarge, color = Palette.Muted, maxLines = 4, overflow = TextOverflow.Ellipsis,
            )
            return@Column
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((id, name) in DeckLayouts.choices) Chip(name, Palette.Sky, strong = id == layoutId) { if (layoutId != id) { layoutId = id; Sounds.select() } }
            Text(
                "Tap Shift, Ctrl or Alt for the next key; hold to lock it",
                Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End,
            )
        }
        Keyboard(layout, toggles, s.deckSounds, s.deckHaptics, Modifier.fillMaxWidth().weight(0.58f)) { code ->
            when (code) {
                DeckLayouts.TOGGLE_NUMPAD -> numpad = !numpad
                DeckLayouts.TOGGLE_FN -> fn = !fn
            }
        }
        Trackpad(s.deckSensitivity, s.deckSounds, s.deckHaptics, Modifier.fillMaxWidth().weight(0.42f))
    }
}

/** Which way the keys reach the game, and a word on it when tapped. */
@Composable
private fun StatusChip(ctx: Context) {
    val status = DeckSession.status
    val (text, color) = when (status) {
        DeckSession.Status.Direct -> "Direct" to Palette.Mint
        DeckSession.Status.Shell -> "Shell" to Palette.Butter
        DeckSession.Status.NoShizuku -> "Needs Shizuku" to Palette.Peach
        else -> "Connecting…" to Palette.Muted
    }
    val said = when (status) {
        DeckSession.Status.Direct -> "Keys and the pointer go straight into Android's input service, in a few milliseconds"
        DeckSession.Status.Shell -> "Android kept Tandem from the input service, so each key runs an input command: it takes a moment, and clicks land where the pad's cursor points"
        DeckSession.Status.NoShizuku -> "Start Shizuku and allow Tandem, then open the Deck again"
        else -> "Asking Shizuku…"
    }
    Chip(text, color) { Toast.makeText(ctx.applicationContext, said, Toast.LENGTH_LONG).show() }
}

@Composable
private fun Chip(text: String, color: Color, strong: Boolean = false, onTap: () -> Unit) {
    Box(
        Modifier.height(32.dp).clip(RoundedCornerShape(50)).background(color.copy(alpha = if (strong) 0.36f else 0.16f)).clickable(onClick = onTap).padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = MaterialTheme.typography.labelLarge, color = if (strong) Palette.Text else color, maxLines = 1) }
}

// ---- Keyboard ----

@Composable
private fun Keyboard(layout: DeckLayout, toggles: Set<Int>, sounds: Boolean, haptics: Boolean, modifier: Modifier, onToggle: (Int) -> Unit) {
    // Read here so every key recomposes when a modifier changes (the modifiers themselves are plain state).
    val tick = DeckSession.modifierTick
    val shift = DeckSession.modifiers.state(DeckModifiers.Mod.Shift) != DeckModifiers.State.Off
    Column(modifier, verticalArrangement = Arrangement.spacedBy(KeyGap)) {
        for (row in layout.rows) {
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(KeyGap)) {
                for (key in row) {
                    if (key.kind == DeckKeyKind.Gap) Spacer(Modifier.weight(key.width))
                    else KeyButton(key, shift, tick, key.code in toggles, sounds, haptics, onToggle, Modifier.weight(key.width).fillMaxHeight())
                }
            }
        }
    }
}

/**
 * One key. A plain key goes down on the touch and up when the finger lifts, repeating while held; a mouse key is
 * held as long as the finger is; a modifier arms on a tap and locks on a hold; a toggle switches part of the layout.
 */
@Composable
private fun KeyButton(key: DeckKey, shift: Boolean, tick: Int, toggled: Boolean, sounds: Boolean, haptics: Boolean, onToggle: (Int) -> Unit, modifier: Modifier) {
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    var pressed by remember { mutableStateOf(false) }
    val mod = if (key.kind == DeckKeyKind.Modifier) DeckModifiers.Mod.forCode(key.code) else null
    val state = if (mod != null) DeckSession.modifiers.state(mod) else DeckModifiers.State.Off
    val tint = when (key.kind) {
        DeckKeyKind.Mouse -> Palette.Peach
        DeckKeyKind.Toggle -> Palette.Sky
        else -> Palette.Lavender
    }
    val feedback = {
        if (sounds) Sounds.key()
        if (haptics) DeckTick.tick(view.context)
    }
    val locked = state == DeckModifiers.State.Locked
    Box(
        modifier.clip(RoundedCornerShape(10.dp))
            .background(if (locked || toggled) tint.copy(alpha = 0.28f) else Palette.Card)
            .pointerInput(key, sounds, haptics) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    pressed = true
                    feedback()
                    var repeat: Job? = null
                    var held = false
                    when (key.kind) {
                        DeckKeyKind.Key -> {
                            DeckSession.keyDown(key)
                            if (key.repeats) repeat = scope.launch {
                                var n = 1
                                while (true) { delay(DeckRepeat.delayMs(n)); DeckSession.keyRepeat(key, n++) }
                            }
                        }
                        DeckKeyKind.Mouse -> DeckMouse.forCode(key.code)?.let { DeckSession.mouseDown(it) }
                        else -> Unit
                    }
                    try {
                        while (true) {
                            // A modifier held past the long-press time locks; nothing else watches the clock.
                            val e = if (mod != null && !held) withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) { awaitPointerEvent() } else awaitPointerEvent()
                            if (e == null) { held = true; DeckSession.modifierHold(mod!!); feedback(); continue }
                            val c = e.changes.firstOrNull { it.id == down.id } ?: break
                            c.consume()
                            if (!c.pressed) break
                        }
                    } finally {
                        pressed = false
                        repeat?.cancel()
                        when (key.kind) {
                            DeckKeyKind.Key -> DeckSession.keyUp(key)
                            DeckKeyKind.Mouse -> DeckMouse.forCode(key.code)?.let { DeckSession.mouseUp(it) }
                            DeckKeyKind.Modifier -> if (!held && mod != null) DeckSession.modifierTap(mod)
                            DeckKeyKind.Toggle -> onToggle(key.code)
                            DeckKeyKind.Gap -> Unit
                        }
                    }
                }
            },
    ) {
        val label = key.labelWith(shift)
        KeyFace(
            label, on = pressed || state == DeckModifiers.State.Armed, tint = tint,
            style = if (label.length <= 2) MaterialTheme.typography.titleSmall else MaterialTheme.typography.labelMedium,
        )
    }
    // Keeps the key following the modifiers' changes: [tick] has no other reader here.
    @Suppress("UNUSED_EXPRESSION") tick
}

// ---- Trackpad ----

@Composable
private fun Trackpad(sensitivity: Float, sounds: Boolean, haptics: Boolean, modifier: Modifier) {
    val view = LocalView.current
    val click = {
        if (sounds) Sounds.select()
        if (haptics) DeckTick.tick(view.context)
    }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Pad(sensitivity, click, Modifier.weight(1f).fillMaxHeight())
        ScrollStrip(Modifier.width(36.dp).fillMaxHeight())
    }
}

/**
 * The pad: a drag moves the game's pointer (a finger that moves further than the touch slop), a tap clicks, a
 * two-finger tap right-clicks, and two fingers dragging scroll. The mark shows where the pointer is on the game's
 * screen, which matters most on the shell path, where the game's own pointer doesn't follow.
 */
@Composable
private fun Pad(sensitivity: Float, click: () -> Unit, modifier: Modifier) {
    Box(
        modifier.clip(RoundedCornerShape(16.dp)).background(Palette.Card)
            .pointerInput(sensitivity) {
                val slop = viewConfiguration.touchSlop
                awaitEachGesture {
                    val first = awaitFirstDown(requireUnconsumed = false)
                    first.consume()
                    val startedAt = first.uptimeMillis
                    var endedAt = startedAt
                    var fingers = 1
                    var moved = 0f
                    var dragging = false
                    var leadId = first.id
                    var lastT = startedAt
                    val scroll = DeckScroll()
                    while (true) {
                        val e = awaitPointerEvent()
                        e.changes.forEach { it.consume() }
                        val pressed = e.changes.filter { it.pressed }
                        endedAt = e.changes.maxOf { it.uptimeMillis }
                        if (pressed.isEmpty()) break
                        fingers = maxOf(fingers, pressed.size)
                        val lead = pressed.firstOrNull { it.id == leadId } ?: pressed.first().also { leadId = it.id; lastT = it.uptimeMillis }
                        val d = lead.position - lead.previousPosition
                        moved += d.getDistance()
                        if (dragging || moved > slop) {
                            dragging = true
                            if (pressed.size >= 2) DeckSession.scroll(scroll.add(d.y))
                            else DeckSession.moveBy(d.x, d.y, lead.uptimeMillis - lastT, sensitivity)
                        }
                        lastT = lead.uptimeMillis
                    }
                    val c = DeckTap.classify(fingers, endedAt - startedAt, moved, slop)
                    if (c != DeckTap.Click.None) { click(); DeckSession.click(c) }
                }
            }
            .drawWithContent {
                drawContent()
                val at = Offset(DeckSession.cursorX * size.width, DeckSession.cursorY * size.height)
                drawCircle(Palette.Sky.copy(alpha = 0.35f), 9.dp.toPx(), at, style = Stroke(1.5.dp.toPx()))
                drawCircle(Palette.Sky, 4.dp.toPx(), at)
            },
    ) {
        Text(
            "Drag to point · tap to click · two fingers for a right click or to scroll",
            Modifier.align(Alignment.BottomCenter).padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelSmall, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
        )
    }
}

/** The wheel: a finger moving up or down the strip turns it a notch every few millimetres. */
@Composable
private fun ScrollStrip(modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(Palette.Card)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    val scroll = DeckScroll()
                    while (true) {
                        val e = awaitPointerEvent()
                        e.changes.forEach { it.consume() }
                        val c = e.changes.firstOrNull { it.id == down.id } ?: break
                        if (!c.pressed) break
                        DeckSession.scroll(scroll.add(c.position.y - c.previousPosition.y))
                    }
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text("▲", Modifier.padding(top = 10.dp), style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
        Box(Modifier.width(3.dp).weight(1f).padding(vertical = 8.dp).clip(RoundedCornerShape(50)).background(Palette.Muted.copy(alpha = 0.25f)))
        Text("▼", Modifier.padding(bottom = 10.dp), style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
    }
}

// ---- Settings ----

private fun timesLabel(v: Float): String = (if (v == v.toInt().toFloat()) v.toInt().toString() else "%.2f".format(v).trimEnd('0')) + "×"

/** Settings › Play & system › Deck. */
@Composable
fun deckSettings(): SettingSection {
    val s = HomeStore.settings
    return SettingSection(
        "Deck",
        listOf(
            number(
                "Trackpad sensitivity", Glyph.Move, Palette.Sky,
                "How far the game's pointer goes for a finger's movement on the Deck's trackpad. Quick swipes always go further than slow, careful ones.",
                s.deckSensitivity, DeckPointer.MIN_SENSITIVITY, DeckPointer.MAX_SENSITIVITY, DeckPointer.SENSITIVITY_STEP, ::timesLabel,
            ) { v -> HomeStore.updateSettings { it.copy(deckSensitivity = v ?: DeckPointer.DEFAULT_SENSITIVITY) } }
                .copy(keywords = "deck trackpad mouse pointer speed sensitivity acceleration touchpad pc windows stream"),
            choice(
                "Default layout", Glyph.Key, Palette.Sky,
                "The keyboard the Deck opens with: the full keyboard, or the compact one with the WASD cluster and mouse buttons. Switch any time with the chips on the Deck.",
                DeckLayouts.choices, s.deckLayout,
            ) { v -> HomeStore.updateSettings { it.copy(deckLayout = v) } }.copy(keywords = "deck keyboard layout qwerty wasd game keys"),
            toggle("Key sounds", Glyph.Volume, Palette.Sky, "A soft click for each key and tap on the Deck.", s.deckSounds) { on ->
                HomeStore.updateSettings { it.copy(deckSounds = on) }
            }.copy(keywords = "deck keyboard click sound typing"),
            toggle("Key haptics", Glyph.Motion, Palette.Sky, "A tiny buzz for each key and tap on the Deck.", s.deckHaptics) { on ->
                HomeStore.updateSettings { it.copy(deckHaptics = on) }
            }.copy(keywords = "deck keyboard vibrate vibration haptic feedback buzz"),
        ),
    )
}

/**
 * The haptic tick for a key or a tap, through the Vibrator rather than the view's haptic feedback: Android drops
 * the latter when touch vibration is off system-wide, and the Deck's own setting is the switch here.
 */
private object DeckTick {
    private var vibrator: Vibrator? = null
    private val effect by lazy { VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK) }

    fun tick(ctx: Context) {
        val v = vibrator ?: ctx.applicationContext.getSystemService(Vibrator::class.java)?.also { vibrator = it } ?: return
        if (v.hasVibrator()) runCatching { v.vibrate(effect) }
    }
}
