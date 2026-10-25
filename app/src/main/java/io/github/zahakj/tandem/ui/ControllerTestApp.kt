package io.github.zahakj.tandem.ui

import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/*
 * Controller test: every button and stick, live. While it is open it takes every key and stick event before the
 * rest of Tandem (MainActivity hands them here first), so B, Start and Home-row buttons can be tested too. Hold B
 * (or Back) for a second and a half to leave.
 */

object PadTest {
    var active by mutableStateOf(false)
    val down = mutableStateMapOf<Int, Boolean>()
    val axes = mutableStateMapOf<Int, Float>()
    val log = mutableStateListOf<String>()
    var device by mutableStateOf<String?>(null)
    var presses by mutableStateOf(0)
    private var backDownAt = 0L

    /** Called by MainActivity for every key while active. True when it was used (always). */
    fun onKey(e: KeyEvent): Boolean {
        device = e.device?.name ?: device
        val code = e.keyCode
        val exitKey = code == KeyEvent.KEYCODE_BUTTON_B || code == KeyEvent.KEYCODE_BACK
        when (e.action) {
            KeyEvent.ACTION_DOWN -> {
                if (e.repeatCount == 0) {
                    down[code] = true
                    presses++
                    log.add(0, "${name(code)} down")
                    if (log.size > 9) log.removeAt(log.lastIndex)
                    if (exitKey) backDownAt = SystemClock.uptimeMillis()
                } else if (exitKey && backDownAt > 0 && SystemClock.uptimeMillis() - backDownAt > 1500) {
                    backDownAt = 0
                    leave()
                }
            }
            KeyEvent.ACTION_UP -> {
                down.remove(code)
                if (exitKey) backDownAt = 0
            }
        }
        return true
    }

    fun onMotion(ev: MotionEvent): Boolean {
        if (!ev.isFromSource(InputDevice.SOURCE_JOYSTICK) && !ev.isFromSource(InputDevice.SOURCE_GAMEPAD)) return false
        device = ev.device?.name ?: device
        for (a in AXES) axes[a] = ev.getAxisValue(a)
        return true
    }

    fun leave() {
        active = false
        down.clear(); axes.clear()
        Nav.back()
    }

    val AXES = intArrayOf(
        MotionEvent.AXIS_X, MotionEvent.AXIS_Y, MotionEvent.AXIS_Z, MotionEvent.AXIS_RZ, MotionEvent.AXIS_HAT_X, MotionEvent.AXIS_HAT_Y,
        MotionEvent.AXIS_LTRIGGER, MotionEvent.AXIS_RTRIGGER, MotionEvent.AXIS_BRAKE, MotionEvent.AXIS_GAS,
    )

    fun name(code: Int): String = KeyEvent.keyCodeToString(code).removePrefix("KEYCODE_").removePrefix("BUTTON_").replace('_', ' ')

    fun on(code: Int) = down[code] == true
    fun axis(a: Int) = axes[a] ?: 0f
}

@Composable
fun ControllerTestScreen() {
    val app = BuiltInApps.byId("pad")!!
    DisposableEffect(Unit) {
        PadTest.active = true
        PadTest.log.clear(); PadTest.presses = 0
        onDispose { PadTest.active = false; PadTest.down.clear(); PadTest.axes.clear() }
    }
    ToolScaffold(app, PadTest.device, sideWidth = 190.dp, hints = {
        Text("Press anything: it lights up here.", style = MaterialTheme.typography.bodyMedium, color = Palette.Muted)
        Box(Modifier.swallowTaps { PadTest.leave() }) { ButtonHint("B", "Hold to leave (or tap)", Palette.Lavender, onClick = { PadTest.leave() }) }
    }) {
        Row(Modifier.fillMaxSize().padding(vertical = 10.dp)) {
            Box(Modifier.weight(1f).fillMaxSize()) { PadDrawing(Modifier.fillMaxSize()) }
            Column(Modifier.width(170.dp).padding(start = 10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("Last presses", style = MaterialTheme.typography.labelLarge, color = app.color)
                for (l in PadTest.log) Text(l, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (PadTest.log.isEmpty()) Text("Nothing yet", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
                Box(Modifier.weight(1f))
                Text("Sticks", style = MaterialTheme.typography.labelLarge, color = app.color)
                Text("L  %+.2f  %+.2f".format(PadTest.axis(MotionEvent.AXIS_X), PadTest.axis(MotionEvent.AXIS_Y)), style = MaterialTheme.typography.bodySmall, maxLines = 1)
                Text("R  %+.2f  %+.2f".format(PadTest.axis(MotionEvent.AXIS_Z), PadTest.axis(MotionEvent.AXIS_RZ)), style = MaterialTheme.typography.bodySmall, maxLines = 1)
                Text(
                    "Triggers  %.2f  %.2f".format(
                        maxOf(PadTest.axis(MotionEvent.AXIS_LTRIGGER), PadTest.axis(MotionEvent.AXIS_BRAKE)),
                        maxOf(PadTest.axis(MotionEvent.AXIS_RTRIGGER), PadTest.axis(MotionEvent.AXIS_GAS)),
                    ),
                    style = MaterialTheme.typography.bodySmall, maxLines = 1,
                )
                Text("${PadTest.presses} presses", style = MaterialTheme.typography.bodySmall, color = Palette.Muted, maxLines = 1)
            }
        }
    }
}

/** The handheld, drawn: shoulders and triggers on top, D-pad and left stick left, face buttons and right stick right. */
@Composable
private fun PadDrawing(modifier: Modifier) {
    val body = Palette.CardHigh
    val idle = Palette.Muted.copy(alpha = 0.45f)
    val lit = Palette.Mint
    val face = listOf(Palette.Mint, Palette.Peach, Palette.Sky, Palette.Butter)
    val text = Palette.Text
    // Read the state here, so the canvas redraws on every change.
    val down = PadTest.down.toMap()
    val ax = PadTest.axes.toMap()
    Canvas(modifier) {
        fun on(code: Int) = down[code] == true
        fun axis(a: Int) = ax[a] ?: 0f
        val w = size.width
        val h = size.height
        val u = minOf(w / 26f, h / 15f)
        val ox = (w - 26f * u) / 2f
        val oy = (h - 15f * u) / 2f
        fun p(x: Float, y: Float) = Offset(ox + x * u, oy + y * u)
        fun rr(x: Float, y: Float, ww: Float, hh: Float, r: Float, c: Color, stroke: Boolean = false) =
            if (stroke) drawRoundRect(c, p(x, y), Size(ww * u, hh * u), CornerRadius(r * u), style = Stroke(0.25f * u))
            else drawRoundRect(c, p(x, y), Size(ww * u, hh * u), CornerRadius(r * u))
        // Triggers fill with how far they're pulled.
        val lt = maxOf(axis(MotionEvent.AXIS_LTRIGGER), axis(MotionEvent.AXIS_BRAKE), if (on(KeyEvent.KEYCODE_BUTTON_L2)) 1f else 0f)
        val rt = maxOf(axis(MotionEvent.AXIS_RTRIGGER), axis(MotionEvent.AXIS_GAS), if (on(KeyEvent.KEYCODE_BUTTON_R2)) 1f else 0f)
        rr(2f, 0f, 5f, 1.6f, 0.8f, idle); rr(2f, 0f, 5f * lt.coerceIn(0f, 1f), 1.6f, 0.8f, lit)
        rr(19f, 0f, 5f, 1.6f, 0.8f, idle); rr(19f, 0f, 5f * rt.coerceIn(0f, 1f), 1.6f, 0.8f, lit)
        rr(1.5f, 2f, 6f, 1.4f, 0.7f, if (on(KeyEvent.KEYCODE_BUTTON_L1)) lit else idle)
        rr(18.5f, 2f, 6f, 1.4f, 0.7f, if (on(KeyEvent.KEYCODE_BUTTON_R1)) lit else idle)
        // Body
        rr(0.5f, 3.8f, 25f, 10.8f, 3.5f, body)
        // D-pad: keys or the hat axes.
        val hx = axis(MotionEvent.AXIS_HAT_X)
        val hy = axis(MotionEvent.AXIS_HAT_Y)
        val dpad = mapOf(
            Offset(0f, -1f) to (on(KeyEvent.KEYCODE_DPAD_UP) || hy < -0.5f), Offset(0f, 1f) to (on(KeyEvent.KEYCODE_DPAD_DOWN) || hy > 0.5f),
            Offset(-1f, 0f) to (on(KeyEvent.KEYCODE_DPAD_LEFT) || hx < -0.5f), Offset(1f, 0f) to (on(KeyEvent.KEYCODE_DPAD_RIGHT) || hx > 0.5f),
        )
        val dc = Offset(5f, 6.8f)
        rr(dc.x - 0.6f, dc.y - 0.6f, 1.2f, 1.2f, 0.2f, idle)
        for ((d, pressed) in dpad) rr(dc.x + d.x * 1.2f - 0.6f, dc.y + d.y * 1.2f - 0.6f, 1.2f, 1.2f, 0.3f, if (pressed) lit else idle)
        // Face buttons: A bottom, B right, X left, Y top (the Nintendo-style AYN layout reads the same codes).
        val fc = Offset(21f, 6.8f)
        val buttons = listOf(
            Triple(Offset(0f, 1.3f), KeyEvent.KEYCODE_BUTTON_A, "A"), Triple(Offset(1.3f, 0f), KeyEvent.KEYCODE_BUTTON_B, "B"),
            Triple(Offset(-1.3f, 0f), KeyEvent.KEYCODE_BUTTON_X, "X"), Triple(Offset(0f, -1.3f), KeyEvent.KEYCODE_BUTTON_Y, "Y"),
        )
        buttons.forEachIndexed { i, (d, code, _) ->
            val c = p(fc.x + d.x, fc.y + d.y)
            drawCircle(if (on(code)) face[i] else idle, 0.62f * u, c)
            if (on(code)) drawCircle(text, 0.62f * u, c, style = Stroke(0.15f * u))
        }
        // Start / Select / Home-row.
        rr(10.6f, 5.6f, 1.8f, 0.8f, 0.4f, if (on(KeyEvent.KEYCODE_BUTTON_SELECT)) lit else idle)
        rr(13.6f, 5.6f, 1.8f, 0.8f, 0.4f, if (on(KeyEvent.KEYCODE_BUTTON_START) || on(KeyEvent.KEYCODE_MENU)) lit else idle)
        drawCircle(if (on(KeyEvent.KEYCODE_BUTTON_MODE) || on(KeyEvent.KEYCODE_HOME)) lit else idle, 0.45f * u, p(13f, 8f))
        // Sticks: the ring, the dot where the stick is, lit when clicked.
        fun stick(cx: Float, cy: Float, x: Float, y: Float, click: Boolean) {
            drawCircle(idle, 1.9f * u, p(cx, cy), style = Stroke(0.22f * u))
            val moved = abs(x) > 0.15f || abs(y) > 0.15f
            drawCircle(if (click) lit else if (moved) Palette.Sky else idle, 0.95f * u, p(cx + x * 1.1f, cy + y * 1.1f))
        }
        stick(8.5f, 10.8f, axis(MotionEvent.AXIS_X), axis(MotionEvent.AXIS_Y), on(KeyEvent.KEYCODE_BUTTON_THUMBL))
        stick(17.5f, 10.8f, axis(MotionEvent.AXIS_Z), axis(MotionEvent.AXIS_RZ), on(KeyEvent.KEYCODE_BUTTON_THUMBR))
    }
}

