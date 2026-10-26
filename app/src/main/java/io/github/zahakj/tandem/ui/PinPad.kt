package io.github.zahakj.tandem.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.zahakj.tandem.Button
import io.github.zahakj.tandem.HiddenItems
import io.github.zahakj.tandem.PinCode
import io.github.zahakj.tandem.PinVault
import io.github.zahakj.tandem.Privacy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/*
 * The private mode PIN pad (PrivacyPin.kt keeps the PIN). A popup with a phone-style keypad: the D-pad and A type,
 * X deletes, Start is OK, Y is Forgot PIN, B cancels; every key and hint can be tapped. Setting a PIN asks for it
 * twice. Wrong tries wait longer each time, and the pad counts the wait down.
 */

/** What the pad is for. */
sealed class PinAsk(val title: String) {
    /** Check the PIN, then [onRight]. With no PIN set (private mode from before PINs, or a forgotten one), set one first. */
    class Check(title: String, val onRight: () -> Unit) : PinAsk(title)
    /** Choose a new PIN, then [onSet]. */
    class Create(title: String, val onSet: () -> Unit) : PinAsk(title)
}

object PinPad {
    var ask by mutableStateOf<PinAsk?>(null)
        private set

    fun check(title: String, onRight: () -> Unit) = open(PinAsk.Check(title, onRight))

    fun create(title: String, onSet: () -> Unit) = open(PinAsk.Create(title, onSet))

    private fun open(a: PinAsk) {
        FocusMemory.forget(ROUTE)
        FocusMemory.remember(ROUTE, "k:1")
        ask = a
    }

    fun close() { ask = null; Nav.focusTick++ }
}

private const val ROUTE = "pinpad"
private val KEYS = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "⌫", "0", "OK")

private enum class Phase { Enter, New, Again, Forgot }

@Composable
fun PinPadOverlay() {
    // Wherever the library is drawn (the bottom-screen home too), the PIN is read before anything asks for it.
    PinVault.init(androidx.compose.ui.platform.LocalContext.current)
    val ask = PinPad.ask
    val shown = rememberLatest(ask)
    val scope = rememberCoroutineScope()
    var phase by remember(ask) { mutableStateOf(Phase.Enter) }
    var digits by remember(ask) { mutableStateOf("") }
    var first by remember(ask) { mutableStateOf("") }
    var note by remember(ask) { mutableStateOf<String?>(null) }
    var busy by remember(ask) { mutableStateOf(false) }
    var now by remember(ask) { mutableLongStateOf(System.currentTimeMillis()) }

    // Which phase it opens on: a check with no PIN (or a Forgot PIN wait that ran out) sets one first.
    LaunchedEffect(ask) {
        val a = ask ?: return@LaunchedEffect
        val rec = PinVault.record
        if (PinCode.resetDue(rec, System.currentTimeMillis())) {
            PinVault.clear()
            // Whatever was showing goes back into hiding before anyone picks the new PIN.
            Privacy.revealed = false
            HiddenItems.endPeeks()
            note = "The wait is over. Choose a new PIN"
        }
        phase = if (a is PinAsk.Check && PinVault.isSet) Phase.Enter else Phase.New
    }
    // The clock for waits, once a second while one runs.
    val waitMs = if (phase == Phase.Enter) PinCode.waitLeft(PinVault.record, now) else 0L
    val resetMs = PinCode.resetLeft(PinVault.record, now)
    LaunchedEffect(ask, waitMs > 0, resetMs != null) {
        while (ask != null && (waitMs > 0 || resetMs != null)) { delay(1000); now = System.currentTimeMillis() }
    }

    fun finish(a: PinAsk) {
        PinPad.close()
        when (a) {
            is PinAsk.Check -> a.onRight()
            is PinAsk.Create -> a.onSet()
        }
    }

    fun submit() {
        val a = ask ?: return
        if (busy) return
        when (phase) {
            Phase.Enter -> {
                val t = System.currentTimeMillis()
                now = t
                if (PinCode.waitLeft(PinVault.record, t) > 0) { Sounds.error(); return }
                if (!PinCode.valid(digits)) { note = "Your PIN is ${PinCode.MIN} to ${PinCode.MAX} digits"; Sounds.error(); return }
                val pin = digits
                busy = true
                scope.launch {
                    val r = withContext(Dispatchers.Default) { PinCode.attempt(PinVault.record, pin, t) }
                    busy = false
                    digits = ""
                    when (r) {
                        // Cancelled while checking: the count is kept, but nothing opens.
                        is PinCode.Attempt.Right -> { PinVault.save(r.rec); if (PinPad.ask === a) { Sounds.confirm(); finish(a) } }
                        is PinCode.Attempt.Wrong -> {
                            PinVault.save(r.rec)
                            Sounds.error()
                            note = if (r.waitMs > 0) "Wrong PIN. Wait ${PinCode.say(r.waitMs)} before the next try" else "Wrong PIN"
                        }
                        is PinCode.Attempt.Wait -> Sounds.error()
                        PinCode.Attempt.NotSet -> phase = Phase.New
                    }
                }
            }
            Phase.New -> {
                if (!PinCode.valid(digits)) { note = "Pick ${PinCode.MIN} to ${PinCode.MAX} digits"; Sounds.error(); return }
                first = digits
                digits = ""
                note = null
                phase = Phase.Again
                Sounds.select()
            }
            Phase.Again -> {
                if (digits != first) {
                    digits = ""; first = ""
                    note = "Those didn't match. Choose it again"
                    phase = Phase.New
                    Sounds.error()
                    return
                }
                val pin = digits
                busy = true
                scope.launch {
                    val rec = withContext(Dispatchers.Default) { PinCode.make(pin) }
                    busy = false
                    if (PinPad.ask !== a) return@launch
                    PinVault.save(rec)
                    Sounds.confirm()
                    Toasts.show("PIN set. Keep it somewhere safe")
                    finish(a)
                }
            }
            Phase.Forgot -> Unit
        }
    }

    fun type(k: String) {
        if (busy || phase == Phase.Forgot) return
        Sounds.key()
        when (k) {
            "⌫" -> digits = digits.dropLast(1)
            "OK" -> submit()
            else -> if (digits.length < PinCode.MAX) { digits += k; note = null }
        }
    }

    fun forgot() {
        if (phase != Phase.Enter) return
        val left = PinCode.resetLeft(PinVault.record, System.currentTimeMillis())
        if (left != null) { note = "You can choose a new PIN in ${PinCode.say(left)}"; return }
        FocusMemory.remember(ROUTE, "wait")
        phase = Phase.Forgot
    }

    InterceptButtons(ask != null) { b ->
        when (b) {
            Button.X -> if (phase != Phase.Forgot) { Sounds.key(); digits = digits.dropLast(1) }
            Button.Start -> submit()
            Button.Y -> forgot()
            else -> Unit
        }
        true
    }

    OverlayPanel(ask != null, onClose = {
        if (phase == Phase.Forgot) { phase = Phase.Enter; FocusMemory.remember(ROUTE, "k:1"); Nav.focusTick++ } else PinPad.close()
    }) {
        val a = shown ?: return@OverlayPanel
        PanelSurface(Modifier.width(360.dp)) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val title = when (phase) {
                    Phase.Enter -> a.title
                    Phase.New -> if (a is PinAsk.Create) a.title else "Choose a PIN for private mode"
                    Phase.Again -> "Enter it again"
                    Phase.Forgot -> "Forgot your PIN?"
                }
                Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (phase == Phase.Forgot) ForgotPage(onWait = {
                    PinVault.save(PinCode.askReset(PinVault.record, System.currentTimeMillis()))
                    now = System.currentTimeMillis()
                    phase = Phase.Enter
                    note = "You can choose a new PIN in ${PinCode.say(PinCode.RESET_WAIT_MS)}"
                    FocusMemory.remember(ROUTE, "k:1")
                    Nav.focusTick++
                }, onBack = { phase = Phase.Enter; FocusMemory.remember(ROUTE, "k:1"); Nav.focusTick++ })
                else {
                    val line = when {
                        busy -> "Checking…"
                        waitMs > 0 -> "Too many wrong tries. Wait ${PinCode.say(waitMs)}"
                        note != null -> note
                        phase == Phase.Enter && resetMs != null -> "New PIN possible in ${PinCode.say(resetMs)}"
                        phase == Phase.Enter -> "Enter your PIN"
                        else -> "${PinCode.MIN} to ${PinCode.MAX} digits"
                    }
                    Text(line.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = if (note != null || waitMs > 0) Palette.Peach else Palette.Muted,
                        textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    PinDots(digits.length)
                    Keypad(enabled = !busy && waitMs <= 0) { type(it) }
                    Row(Modifier.padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        ButtonHint("X", "Delete", Palette.Peach)
                        ButtonHint("Start", "OK", Palette.Mint)
                        if (phase == Phase.Enter) ButtonHint("Y", "Forgot", Palette.Lavender, onClick = { forgot() })
                        ButtonHint("B", "Cancel", Palette.Lavender)
                    }
                }
            }
        }
    }
}

/** One dot per digit typed, and rings for the digits a PIN may still have. */
@Composable
private fun PinDots(typed: Int) {
    Row(Modifier.height(22.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        for (i in 0 until PinCode.MAX) {
            val filled = i < typed
            val m = Modifier.size(if (i < PinCode.MIN || filled) 14.dp else 10.dp).let {
                if (filled) it.background(Palette.Mint, CircleShape) else it.border(2.dp, Palette.Muted.copy(alpha = if (i < PinCode.MIN) 0.8f else 0.4f), CircleShape)
            }
            Box(m)
        }
    }
}

/** 1 to 9, then ⌫ 0 OK, as on a phone. */
@Composable
private fun Keypad(enabled: Boolean, onKey: (String) -> Unit) {
    val target = rememberFocusTarget(ROUTE, KEYS.map { "k:$it" })
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (row in KEYS.chunked(3)) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            for (k in row) {
                val color = when (k) { "OK" -> Palette.Mint; "⌫" -> Palette.Peach; else -> Palette.Lavender }
                FocusCard(
                    Modifier.width(84.dp).height(40.dp).takesFocus(ROUTE, "k:$k", target == "k:$k"),
                    glow = color, shape = RoundedCornerShape(12.dp),
                    onClick = { if (enabled) onKey(k) else Sounds.error() },
                ) {
                    Box(Modifier.fillMaxSize().background(color.copy(alpha = if (k.length > 1 || k == "⌫") 0.18f else 0.08f)), contentAlignment = Alignment.Center) {
                        Text(k, style = MaterialTheme.typography.titleMedium, color = if (enabled) Palette.Text else Palette.Muted, maxLines = 1)
                    }
                }
            }
        }
    }
}

/** Forgot PIN: what happens, and the choice. */
@Composable
private fun ForgotPage(onWait: () -> Unit, onBack: () -> Unit) {
    val target = rememberFocusTarget(ROUTE, listOf("back", "wait"))
    Text(
        "Everything hidden stays hidden. After a ${PinCode.say(PinCode.RESET_WAIT_MS)} wait you can choose a new PIN here. " +
            "Entering the right PIN before then calls it off.",
        style = MaterialTheme.typography.bodyMedium, color = Palette.Muted, textAlign = TextAlign.Center, maxLines = 6, overflow = TextOverflow.Ellipsis,
    )
    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        PillButton("Back", Palette.Mint, Modifier.takesFocus(ROUTE, "back", target == "back"), onClick = onBack)
        PillButton("Start the wait", Palette.Peach, Modifier.takesFocus(ROUTE, "wait", target == "wait"), onClick = onWait)
    }
}
