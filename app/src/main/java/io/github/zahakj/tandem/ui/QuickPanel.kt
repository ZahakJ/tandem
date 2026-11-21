package io.github.zahakj.tandem.ui

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import android.text.format.Formatter
import io.github.zahakj.tandem.DiscordPresence
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.zahakj.tandem.MainActivity
import io.github.zahakj.tandem.Shell
import io.github.zahakj.tandem.data.Store
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * The device controls the quick panel uses. Android lets apps change volume directly; brightness needs the
 * "modify system settings" permission or Shizuku; Wi-Fi, Bluetooth and airplane mode can only be switched through
 * Shizuku, so without it they open Android's own panel instead.
 */
object SystemControls {
    const val BRIGHT_STEPS = 20

    /** Brightness as a 0..[BRIGHT_STEPS] level on a perceptual curve, like Android's own slider. */
    fun brightnessLevel(ctx: Context): Int {
        val raw = Settings.System.getInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128)
        return (sqrt(raw / 255f) * BRIGHT_STEPS).roundToInt().coerceIn(0, BRIGHT_STEPS)
    }

    fun autoBrightness(ctx: Context): Boolean =
        Settings.System.getInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, 0) == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC

    fun canSetBrightness(ctx: Context): Boolean = Settings.System.canWrite(ctx) || Shell.ready()

    /** Blocking (Shizuku). False when Tandem isn't allowed to change it. */
    fun setBrightnessLevel(ctx: Context, level: Int): Boolean {
        val l = level.coerceIn(0, BRIGHT_STEPS) / BRIGHT_STEPS.toFloat()
        val raw = (l * l * 255).roundToInt().coerceIn(1, 255)
        if (Settings.System.canWrite(ctx)) {
            val cr = ctx.contentResolver
            Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
            Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, raw)
            return true
        }
        if (!Shell.ready()) return false
        Shell.run("settings", "put", "system", "screen_brightness_mode", "0")
        return Shell.run("settings", "put", "system", "screen_brightness", "$raw") != null
    }

    fun setAutoBrightness(ctx: Context, on: Boolean): Boolean {
        val v = if (on) 1 else 0
        if (Settings.System.canWrite(ctx)) return Settings.System.putInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, v)
        return Shell.run("settings", "put", "system", "screen_brightness_mode", "$v") != null
    }

    fun autoRotate(ctx: Context): Boolean = Settings.System.getInt(ctx.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0) != 0

    /** Blocking (Shizuku). Turns auto-rotate off when something turned it on. */
    fun rotationOff(ctx: Context) {
        if (!autoRotate(ctx)) return
        if (Settings.System.canWrite(ctx)) Settings.System.putInt(ctx.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0)
        else if (Shell.ready()) Shell.run("settings", "put", "system", "accelerometer_rotation", "0")
    }

    private fun audio(ctx: Context) = ctx.getSystemService(AudioManager::class.java)
    fun volume(ctx: Context): Int = audio(ctx).getStreamVolume(AudioManager.STREAM_MUSIC)
    fun maxVolume(ctx: Context): Int = audio(ctx).getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    fun setVolume(ctx: Context, v: Int) = runCatching { audio(ctx).setStreamVolume(AudioManager.STREAM_MUSIC, v.coerceIn(0, maxVolume(ctx)), 0) }

    fun wifiOn(ctx: Context): Boolean = runCatching { ctx.applicationContext.getSystemService(WifiManager::class.java).isWifiEnabled }.getOrDefault(false)
    fun bluetoothOn(ctx: Context): Boolean = Settings.Global.getInt(ctx.contentResolver, Settings.Global.BLUETOOTH_ON, 0) != 0
    fun airplaneOn(ctx: Context): Boolean = Settings.Global.getInt(ctx.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) != 0

    /** Blocking. True when switched directly; false means the caller should open Android's settings instead. */
    fun setWifi(on: Boolean) = Shell.run("svc", "wifi", if (on) "enable" else "disable") != null
    fun setBluetooth(on: Boolean) = Shell.run("svc", "bluetooth", if (on) "enable" else "disable") != null
    fun setAirplane(on: Boolean) = Shell.run("cmd", "connectivity", "airplane-mode", if (on) "enable" else "disable") != null

    fun battery(ctx: Context): Pair<Int, Boolean> {
        val level = ctx.getSystemService(BatteryManager::class.java).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val status = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        return level to (status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL)
    }

    /** Free and total bytes on the shared storage where the games live. */
    fun storage(): Pair<Long, Long> = runCatching {
        val fs = StatFs(Environment.getExternalStorageDirectory().path)
        fs.availableBytes to fs.totalBytes
    }.getOrDefault(0L to 0L)

    fun open(ctx: Context, action: String) {
        val tries = listOf(Intent(action), Intent(Settings.ACTION_SETTINGS))
        for (i in tries) if (runCatching { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess) return
    }
}

/** Hold Start, ◐ Quick in the top bar or the Battery widget: brightness, volume and the device tiles, over anything. */
@Composable
fun QuickPanel() {
    OverlayPanel(Panels.quick, onClose = { Panels.close() }) {
        PanelSurface(Modifier.width(660.dp)) { QuickControls("quick", narrow = false) }
    }
}

/**
 * The quick settings themselves, one set for both places: device state you flip while playing. Brightness and volume
 * as sliders, then the tiles. Going places (stats, settings, the game switcher) is Start's job, never a tile here.
 * In the quick panel under a header with battery and storage; [narrow] in the side panel (SidePanel.kt), which has
 * its own header, with smaller tiles.
 */
@Composable
fun androidx.compose.foundation.layout.ColumnScope.QuickControls(key: String, narrow: Boolean) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    // Re-read the device state once a second while open (radios take a moment to switch).
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(1000); tick++ } }
    // Whether a Discord account is connected (a small file read), for the Discord status tile.
    LaunchedEffect(Unit) { if (DiscordPresence.available) withContext(Dispatchers.IO) { DiscordPresence.refresh(ctx) } }
    val t = tick
    // While a change is on its way, show where it's going so a held button steps smoothly.
    var brightTarget by remember { mutableIntStateOf(-1) }
    val readBright = remember(t) { SystemControls.brightnessLevel(ctx) }
    val bright = if (brightTarget >= 0) brightTarget else readBright
    val auto = remember(t) { SystemControls.autoBrightness(ctx) }
    val vol = remember(t) { SystemControls.volume(ctx) }
    val maxVol = remember { SystemControls.maxVolume(ctx).coerceAtLeast(1) }
    val wifi = remember(t) { SystemControls.wifiOn(ctx) }
    val bt = remember(t) { SystemControls.bluetoothOn(ctx) }
    val plane = remember(t) { SystemControls.airplaneOn(ctx) }
    val (battery, charging) = remember(t / 10) { SystemControls.battery(ctx) }
    val (free, total) = remember(t / 10) { SystemControls.storage() }
    val shizuku = remember { Shell.ready() }
    // Controller passthrough (PadLink.kt): only where it can work.
    val padLink = remember { io.github.zahakj.tandem.PadLink.available(ctx) }
    val canBright = remember(t / 5) { SystemControls.canSetBrightness(ctx) }
    fun io(work: () -> Unit) = scope.launch { withContext(Dispatchers.IO) { work() }; tick++ }

    if (!narrow) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Quick settings", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            Pill("${if (charging) "⚡ " else ""}$battery%", if (battery <= 20 && !charging) Palette.Peach else Palette.Mint)
            if (total > 0) { Spacer(Modifier.width(8.dp)); Pill("${Formatter.formatShortFileSize(ctx, free)} free", Palette.Sky) }
        }
        Spacer(Modifier.height(14.dp))
    }
    val gap = if (narrow) 8.dp else 10.dp
    Column(verticalArrangement = Arrangement.spacedBy(gap)) {
        LevelCard(
            "Brightness", if (auto) "Auto" else "${bright * 100 / SystemControls.BRIGHT_STEPS}%", bright, SystemControls.BRIGHT_STEPS, Palette.Butter,
            QuickIcon.Sun, Modifier.remembersFocus(key, "bright", true), compact = narrow,
            onStep = { d ->
                if (!canBright) askWriteSettings(ctx)
                else {
                    val next = (bright + d).coerceIn(0, SystemControls.BRIGHT_STEPS)
                    brightTarget = next
                    scope.launch {
                        withContext(Dispatchers.IO) { SystemControls.setBrightnessLevel(ctx, next) }
                        if (brightTarget == next) brightTarget = -1
                        tick++
                    }
                }
            },
            onClick = {
                if (!canBright) askWriteSettings(ctx)
                else io { SystemControls.setAutoBrightness(ctx, !auto) }
            },
        )
        LevelCard(
            "Volume", if (vol == 0) "Muted" else "${vol * 100 / maxVol}%", vol, maxVol, Palette.Peach,
            if (vol == 0) QuickIcon.Muted else QuickIcon.Speaker, Modifier.remembersFocus(key, "vol", false), compact = narrow,
            onStep = { d -> SystemControls.setVolume(ctx, vol + d); tick++ },
            onClick = { SystemControls.setVolume(ctx, if (vol == 0) maxVol / 2 else 0); tick++ },
        )
        val private = io.github.zahakj.tandem.Privacy.enabled
        val tiles = buildList {
            add(QuickTile("wifi", "Wi-Fi", QuickIcon.Wifi, Palette.Sky, wifi, if (wifi) "On" else "Off") {
                if (shizuku) io { SystemControls.setWifi(!wifi) } else { Panels.close(); SystemControls.open(ctx, Settings.Panel.ACTION_WIFI) }
            })
            add(QuickTile("bt", "Bluetooth", QuickIcon.Bluetooth, Palette.Lavender, bt, if (bt) "On" else "Off") {
                if (shizuku) io { SystemControls.setBluetooth(!bt) } else { Panels.close(); SystemControls.open(ctx, Settings.ACTION_BLUETOOTH_SETTINGS) }
            })
            add(QuickTile("plane", "Airplane", QuickIcon.Plane, Palette.Peach, plane, if (plane) "On" else "Off") {
                if (shizuku) io { SystemControls.setAirplane(!plane) } else { Panels.close(); SystemControls.open(ctx, Settings.ACTION_AIRPLANE_MODE_SETTINGS) }
            })
            // Steps through Built-in and each connected controller: the one that plays as the built-in one.
            if (padLink) {
                val pad = io.github.zahakj.tandem.PadLink.linked
                add(QuickTile("pad", "Controller", QuickIcon.Pad, Palette.Butter, pad != null, pad?.name ?: "Built-in") {
                    io.github.zahakj.tandem.PadLink.cycle()
                })
            }
            // Pauses the Discord status without disconnecting (DiscordPresence.kt); only once an account is connected.
            if (DiscordPresence.available && DiscordPresence.linked) {
                add(QuickTile("discord", "Discord", QuickIcon.Chat, Palette.Mint, DiscordPresence.shown, if (DiscordPresence.shown) "Showing" else "Paused") {
                    DiscordPresence.setShown(ctx, !DiscordPresence.shown)
                })
            }
            add(QuickTile("battery", "Battery saver", QuickIcon.Leaf, Palette.Mint, BatterySaver.on, if (BatterySaver.on) "On" else "Off", short = "Saver") {
                BatterySaver.toggle(ctx)
            })
            // Private mode on at once, or off with the PIN (PrivateMode); the panel closes so the PIN pad has the screen.
            add(QuickTile("private", "Private mode", QuickIcon.Shield, Palette.Lavender, private, if (private) "On" else "Off", short = "Private") {
                Panels.close()
                if (private) io.github.zahakj.tandem.PrivateMode.turnOff() else io.github.zahakj.tandem.PrivateMode.turnOn()
            })
            // Rests the screens now, like Android's own screen saver tile (Home & layout › Screensaver sets when it
            // comes by itself).
            add(QuickTile("rest", "Screensaver", QuickIcon.Moon, Palette.Sky, null, "Start now", short = "Rest") {
                Panels.close(); io.github.zahakj.tandem.Screensaver.start()
            })
            add(QuickTile("android", "Android", QuickIcon.Gear, Palette.Lavender, null, "Settings") {
                Panels.close(); SystemControls.open(ctx, Settings.ACTION_SETTINGS)
            })
        }
        TileGrid(tiles, key, narrow, gap)
    }
    // Without Shizuku a radio tile can only open Android's own page for it.
    if (!shizuku) {
        Text(
            "With Shizuku, Wi-Fi, Bluetooth and Airplane switch right here.",
            Modifier.padding(top = 8.dp, start = 4.dp), style = MaterialTheme.typography.bodySmall, color = Palette.Muted,
        )
    }
}

/** One quick tile: [on] is null for a tile that does something rather than switches something. */
private class QuickTile(
    val id: String, val label: String, val icon: QuickIcon, val color: Color, val on: Boolean?, val value: String,
    /** The name on a small tile (the side panel). */
    val short: String = label,
    val onClick: () -> Unit,
)

/**
 * The tiles in rows as even as they can be, up to four to a row (seven make four and three, nine three threes), every
 * row as wide as the panel, so there is never a lone tile or a hole at the end.
 */
@Composable
private fun TileGrid(tiles: List<QuickTile>, key: String, narrow: Boolean, gap: Dp) {
    val most = 4
    val rows = (tiles.size + most - 1) / most
    val base = tiles.size / rows
    val extra = tiles.size % rows
    Column(verticalArrangement = Arrangement.spacedBy(gap)) {
        var at = 0
        repeat(rows) { r ->
            val n = base + if (r < extra) 1 else 0
            val row = tiles.subList(at, at + n)
            at += n
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                for (tile in row) androidx.compose.runtime.key(tile.id) {
                    QuickTileCard(tile, Modifier.weight(1f).remembersFocus(key, tile.id, false), narrow)
                }
            }
        }
    }
}

/**
 * A tile: a round icon that fills with the tile's colour while it is on, the name and what it is set to. Small (the
 * side panel), the icon sits over a short name and the icon alone shows on or off.
 */
@Composable
private fun QuickTileCard(tile: QuickTile, modifier: Modifier, small: Boolean) {
    val on = tile.on == true
    val color = tile.color
    FocusCard(modifier.height(if (small) 66.dp else 64.dp), glow = color, shape = RoundedCornerShape(18.dp), onClick = tile.onClick) {
        // Off tiles get a faint wash of the text colour: on a light theme the bare card matches the panel behind it.
        Box(Modifier.fillMaxSize().background(if (on) color.copy(alpha = 0.2f) else Palette.Text.copy(alpha = 0.06f)))
        if (small) {
            Column(
                Modifier.fillMaxSize().padding(horizontal = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
            ) {
                IconChip(tile, 30.dp)
                Spacer(Modifier.height(5.dp))
                Text(tile.short, style = MaterialTheme.typography.labelMedium, color = if (on) color else Palette.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        } else {
            Row(Modifier.fillMaxSize().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                IconChip(tile, 34.dp)
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    ShrinkText(tile.label, MaterialTheme.typography.titleSmall, maxLines = 1)
                    Text(tile.value, style = MaterialTheme.typography.labelMedium, color = if (on) color else Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** Filled with the tile's colour while on, a soft tint of it while off; a tile that only does something keeps the tint. */
@Composable
private fun IconChip(tile: QuickTile, size: Dp) {
    val on = tile.on == true
    val ink = when {
        on -> if (tile.color.luminance() > 0.45f) Color(0xFF17141F) else Color.White
        tile.on == null -> tile.color
        else -> Palette.Muted
    }
    Box(
        Modifier.size(size).clip(RoundedCornerShape(50)).background(if (on) tile.color else tile.color.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) { Box(Modifier.size(size * 0.6f)) { tile.icon.Draw(ink) } }
}

private fun askWriteSettings(ctx: Context) {
    Toasts.show("Allow Tandem to change system settings, then come back")
    Panels.close()
    runCatching {
        ctx.startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

/** A level you change with left and right (or the − and + ends by touch); A runs [onClick]. */
@Composable
private fun LevelCard(
    label: String, value: String, level: Int, max: Int, color: Color, icon: QuickIcon, modifier: Modifier, compact: Boolean = false,
    onStep: (Int) -> Unit, onClick: () -> Unit,
) {
    FocusCard(
        modifier.fillMaxWidth().height(if (compact) 50.dp else 60.dp).onPreviewKeyEvent { e ->
            val d = when (e.key) { Key.DirectionLeft -> -1; Key.DirectionRight -> 1; else -> return@onPreviewKeyEvent false }
            if (e.type == KeyEventType.KeyDown) onStep(d)
            true
        },
        glow = color, shape = RoundedCornerShape(18.dp), onClick = onClick,
    ) {
        Box(Modifier.fillMaxSize().background(Palette.Text.copy(alpha = 0.04f)))
        Row(Modifier.fillMaxSize().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            StepButton("−", color, if (compact) 30.dp else 36.dp) { onStep(-1) }
            Spacer(Modifier.width(10.dp))
            Box(Modifier.size(if (compact) 18.dp else 20.dp)) { icon.Draw(color) }
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(label, Modifier.weight(1f), style = if (compact) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleSmall, maxLines = 1)
                    Text(value, style = MaterialTheme.typography.labelLarge, color = color)
                }
                Spacer(Modifier.height(if (compact) 4.dp else 6.dp))
                Box(Modifier.fillMaxWidth().height(if (compact) 6.dp else 8.dp).clip(RoundedCornerShape(50)).background(Palette.NightDeep)) {
                    Box(Modifier.fillMaxWidth(level / max.toFloat()).fillMaxHeight().clip(RoundedCornerShape(50)).background(color))
                }
            }
            StepButton("+", color, if (compact) 30.dp else 36.dp) { onStep(1) }
        }
    }
}

@Composable
private fun StepButton(symbol: String, color: Color, size: Dp, onTap: () -> Unit) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.16f)).swallowTaps(onTap),
        contentAlignment = Alignment.Center,
    ) { Text(symbol, style = MaterialTheme.typography.titleLarge, color = color) }
}

/**
 * The quick tiles' icons: Glyph's own (Glyphs.kt) where it has one, and the rest drawn here in the same stroke on the
 * same 24-unit square (Glyph has no radios, sun or moon).
 */
private enum class QuickIcon {
    Sun, Speaker, Muted, Wifi, Bluetooth, Plane, Pad, Chat, Leaf, Shield, Moon, Gear;

    @Composable
    fun Draw(ink: Color) {
        when (this) {
            Speaker -> GlyphIcon(Glyph.Volume, ink)
            Pad -> GlyphIcon(Glyph.Gamepad, ink)
            Shield -> GlyphIcon(Glyph.Shield, ink)
            Gear -> TIconGlyph(TIcon.Gear, ink)
            else -> Canvas(Modifier.fillMaxSize()) { drawQuick(this@QuickIcon, ink) }
        }
    }
}

private fun DrawScope.drawQuick(icon: QuickIcon, ink: Color) {
    val u = size.minDimension / 24f
    val w = 2.1f * u
    val stroke = Stroke(width = w, cap = StrokeCap.Round, join = StrokeJoin.Round)
    fun p(x: Float, y: Float) = Offset(x * u, y * u)
    fun line(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(ink, p(x1, y1), p(x2, y2), w, StrokeCap.Round)
    fun path(vararg pts: Float, close: Boolean = false) {
        val path = Path()
        path.moveTo(pts[0] * u, pts[1] * u)
        var i = 2
        while (i < pts.size) { path.lineTo(pts[i] * u, pts[i + 1] * u); i += 2 }
        if (close) path.close()
        drawPath(path, ink, style = stroke)
    }
    fun arc(cx: Float, cy: Float, r: Float, start: Float, sweep: Float) =
        drawArc(ink, start, sweep, false, p(cx - r, cy - r), Size(2 * r * u, 2 * r * u), style = stroke)
    when (icon) {
        QuickIcon.Sun -> {
            drawCircle(ink, 4.2f * u, p(12f, 12f), style = stroke)
            for (k in 0 until 8) {
                val a = Math.toRadians(k * 45.0)
                val c = kotlin.math.cos(a).toFloat()
                val s = kotlin.math.sin(a).toFloat()
                line(12f + c * 7.3f, 12f + s * 7.3f, 12f + c * 9.6f, 12f + s * 9.6f)
            }
        }
        QuickIcon.Muted -> {
            path(3.5f, 9.5f, 7.5f, 9.5f, 12.5f, 5f, 12.5f, 19f, 7.5f, 14.5f, 3.5f, 14.5f, close = true)
            line(16f, 9.5f, 21f, 14.5f)
            line(21f, 9.5f, 16f, 14.5f)
        }
        QuickIcon.Wifi -> {
            drawCircle(ink, 1.6f * u, p(12f, 19f))
            arc(12f, 19f, 5f, 225f, 90f)
            arc(12f, 19f, 9.5f, 225f, 90f)
            arc(12f, 19f, 14f, 228f, 84f)
        }
        QuickIcon.Bluetooth -> path(6.5f, 7.5f, 17f, 16.5f, 12f, 21f, 12f, 3f, 17f, 7.5f, 6.5f, 16.5f)
        QuickIcon.Plane -> {
            drawRoundRect(ink, p(10.6f, 2.5f), Size(2.8f * u, 18.5f * u), CornerRadius(1.4f * u, 1.4f * u))
            path(2.5f, 14.5f, 12f, 9f, 21.5f, 14.5f)
            path(8f, 21f, 12f, 18.5f, 16f, 21f)
        }
        QuickIcon.Chat -> {
            drawRoundRect(ink, p(3f, 4.5f), Size(18f * u, 12.5f * u), CornerRadius(4f * u, 4f * u), style = stroke)
            path(8.5f, 17f, 8f, 20.5f, 12.5f, 17f)
            drawCircle(ink, 1.4f * u, p(9f, 10.8f))
            drawCircle(ink, 1.4f * u, p(15f, 10.8f))
        }
        QuickIcon.Leaf -> {
            // A leaf, battery saver's usual mark (an empty battery would read as a warning).
            val leaf = Path().apply {
                moveTo(5f * u, 19f * u)
                cubicTo(4f * u, 9f * u, 10f * u, 4f * u, 20f * u, 4f * u)
                cubicTo(20f * u, 14f * u, 15f * u, 20f * u, 5f * u, 19f * u)
                close()
            }
            drawPath(leaf, ink, style = stroke)
            line(5f, 19f, 13f, 11f)
        }
        QuickIcon.Moon -> {
            val disc = Path().apply { addOval(Rect(p(12f, 12f), 8.5f * u)) }
            val bite = Path().apply { addOval(Rect(p(16.5f, 8f), 7f * u)) }
            drawPath(Path.combine(PathOperation.Difference, disc, bite), ink)
        }
        else -> Unit
    }
}

/**
 * Battery saver: still screens and quiet speakers until it is turned off again. On, it notes the few settings it
 * changes (reduce motion, video snaps, panning art, music, animation style) and sets them; off, it puts back exactly
 * those, so anything changed meanwhile elsewhere stays. Your layout and theme are never touched.
 */
object BatterySaver {
    val on: Boolean get() = Store.settings.batterySaver != null

    fun toggle(ctx: android.content.Context) {
        val s = Store.settings
        val b = s.batterySaver
        if (b == null) {
            val l = s.look
            Store.updateSettings {
                it.copy(
                    batterySaver = io.github.zahakj.tandem.data.BatteryBefore(it.reduceMotion, l.videoSnaps, l.kenBurns, l.music, l.motion),
                    reduceMotion = true,
                    look = it.look.copy(videoSnaps = false, kenBurns = false, music = false, motion = AnimationStyle.Minimal.id),
                )
            }
            Toasts.show("Battery saver on: still screens, no music")
        } else {
            Store.updateSettings {
                it.copy(
                    batterySaver = null, reduceMotion = b.reduceMotion,
                    look = it.look.copy(videoSnaps = b.videoSnaps, kenBurns = b.kenBurns, music = b.music, motion = b.motion),
                )
            }
            Toasts.show("Battery saver off")
        }
        (ctx as? MainActivity)?.applyScreenRoles()
        Music.refresh()
    }
}
