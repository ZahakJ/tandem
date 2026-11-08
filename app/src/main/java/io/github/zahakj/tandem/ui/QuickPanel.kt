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
import androidx.compose.ui.graphics.Color
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

/** Hold Start, or ◐ in the top bar: brightness, volume, radios, battery and storage. */
@Composable
fun QuickPanel() {
    OverlayPanel(Panels.quick, onClose = { Panels.close() }) {
        PanelSurface(Modifier.width(640.dp)) { QuickControls("quick", narrow = false) }
    }
}

/**
 * The quick settings themselves: a header with battery and storage, brightness and volume, the radios and the
 * shortcuts. In the quick panel, and [narrow] in the side panel (SidePanel.kt), where the tiles go three to a row.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
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

    // The side panel shows battery and storage in its own header.
    if (!narrow) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Quick settings", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
        Pill("${if (charging) "⚡ " else ""}$battery%", if (battery <= 20 && !charging) Palette.Peach else Palette.Mint)
        Spacer(Modifier.width(8.dp))
        if (total > 0) Pill("${Formatter.formatShortFileSize(ctx, free)} free", Palette.Sky)
        }
        Spacer(Modifier.height(12.dp))
    }
    Column(verticalArrangement = Arrangement.spacedBy(if (narrow) 7.dp else 9.dp)) {
        LevelCard(
            "☀ Brightness", if (auto) "Auto" else "${bright * 100 / SystemControls.BRIGHT_STEPS}%", bright, SystemControls.BRIGHT_STEPS, Palette.Butter,
            Modifier.remembersFocus(key, "bright", true), compact = narrow,
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
            "♪ Volume", if (vol == 0) "Muted" else "${vol * 100 / maxVol}%", vol, maxVol, Palette.Peach,
            Modifier.remembersFocus(key, "vol", false), compact = narrow,
            onStep = { d -> SystemControls.setVolume(ctx, vol + d); tick++ },
            onClick = { SystemControls.setVolume(ctx, if (vol == 0) maxVol / 2 else 0); tick++ },
        )
        val radios: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {
            ToggleTile("Wi-Fi", wifi, Palette.Sky, cell().remembersFocus(key, "wifi", false)) {
                if (shizuku) io { SystemControls.setWifi(!wifi) } else { Panels.close(); SystemControls.open(ctx, Settings.Panel.ACTION_WIFI) }
            }
            ToggleTile("Bluetooth", bt, Palette.Lavender, cell().remembersFocus(key, "bt", false)) {
                if (shizuku) io { SystemControls.setBluetooth(!bt) } else { Panels.close(); SystemControls.open(ctx, Settings.ACTION_BLUETOOTH_SETTINGS) }
            }
            ToggleTile("Airplane", plane, Palette.Peach, cell().remembersFocus(key, "plane", false)) {
                if (shizuku) io { SystemControls.setAirplane(!plane) } else { Panels.close(); SystemControls.open(ctx, Settings.ACTION_AIRPLANE_MODE_SETTINGS) }
            }
            // Steps through Built-in and each connected controller: the one that plays as the built-in one.
            if (padLink) {
                val pad = io.github.zahakj.tandem.PadLink.linked
                ToggleTile("Controller", pad != null, Palette.Butter, cell().remembersFocus(key, "pad", false), value = pad?.name ?: "Built-in") {
                    io.github.zahakj.tandem.PadLink.cycle()
                }
            }
            ToggleTile("Battery saver", BatterySaver.on, Palette.Mint, cell().remembersFocus(key, "battery", false), short = "Saver") {
                BatterySaver.toggle(ctx)
            }
            // Pauses the Discord status without disconnecting (DiscordPresence.kt); only once an account is connected.
            if (DiscordPresence.available && DiscordPresence.linked) {
                ToggleTile("Discord status", DiscordPresence.shown, Palette.Mint, cell().remembersFocus(key, "discord", false), short = "Discord") {
                    DiscordPresence.setShown(ctx, !DiscordPresence.shown)
                }
            }
        }
        val shortcuts: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {
            ActionTile("▶ Running", Palette.Mint, cell().remembersFocus(key, "switcher", false)) { Panels.openSwitcher() }
            // Private mode on at once, or off with the PIN (PrivateMode); the panel closes so the PIN pad has the screen.
            ToggleTile("Private mode", io.github.zahakj.tandem.Privacy.enabled, Palette.Lavender, cell().remembersFocus(key, "private", false), height = 48.dp, short = "Private") {
                Panels.close()
                if (io.github.zahakj.tandem.Privacy.enabled) io.github.zahakj.tandem.PrivateMode.turnOff() else io.github.zahakj.tandem.PrivateMode.turnOn()
            }
            ActionTile("★ Stats", Palette.Butter, cell().remembersFocus(key, "stats", false)) {
                Panels.close(); if (Nav.route != Route.Stats) Nav.go(Route.Stats)
            }
            ActionTile("⚙ Android", Palette.Lavender, cell().remembersFocus(key, "android", false)) {
                Panels.close(); SystemControls.open(ctx, Settings.ACTION_SETTINGS)
            }
            if (shizuku) ActionTile("☾ Sleep", Palette.Sky, cell().remembersFocus(key, "sleep", false)) {
                Panels.close(); io { Shell.run("input", "keyevent", "223") }
            }
        }
        // Narrow, one grid of small tiles; else the radios and the shortcuts each have a row.
        if (narrow) TileRow(true) { radios(); shortcuts() } else { TileRow(false, radios); TileRow(false, shortcuts) }
    }
    // The side panel shows its own hints once, at the bottom.
    if (!narrow) {
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            ButtonHint("◀▶", "Adjust", Palette.Butter)
            ButtonHint("A", "Toggle", Palette.Mint)
            ButtonHint("B", "Close", Palette.Lavender)
        }
    }
    if (!shizuku) {
        Text(
            "With Shizuku, Wi-Fi, Bluetooth and airplane mode switch right here.",
            Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodySmall, color = Palette.Muted,
        )
    }
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
private fun LevelCard(label: String, value: String, level: Int, max: Int, color: Color, modifier: Modifier, compact: Boolean = false, onStep: (Int) -> Unit, onClick: () -> Unit) {
    FocusCard(
        modifier.fillMaxWidth().height(if (compact) 46.dp else 58.dp).onPreviewKeyEvent { e ->
            val d = when (e.key) { Key.DirectionLeft -> -1; Key.DirectionRight -> 1; else -> return@onPreviewKeyEvent false }
            if (e.type == KeyEventType.KeyDown) onStep(d)
            true
        },
        glow = color, shape = RoundedCornerShape(16.dp), onClick = onClick,
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            StepButton("−", color, if (compact) 32.dp else 40.dp) { onStep(-1) }
            Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(label, Modifier.weight(1f), style = if (compact) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleSmall, maxLines = 1)
                    Text(value, style = MaterialTheme.typography.labelLarge, color = color)
                }
                Spacer(Modifier.height(if (compact) 4.dp else 5.dp))
                Box(Modifier.fillMaxWidth().height(if (compact) 6.dp else 8.dp).clip(RoundedCornerShape(50)).background(Palette.NightDeep)) {
                    Box(Modifier.fillMaxWidth(level / max.toFloat()).fillMaxHeight().clip(RoundedCornerShape(50)).background(color))
                }
            }
            StepButton("+", color, if (compact) 32.dp else 40.dp) { onStep(1) }
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

/** An on / off tile; [height] matches the row it sits in (the action row's tiles are lower). */
@Composable
private fun ToggleTile(label: String, on: Boolean, color: Color, modifier: Modifier, value: String? = null, height: Dp = 62.dp, short: String? = null, onClick: () -> Unit) {
    val compact = LocalCompactTiles.current
    FocusCard(modifier.height(if (compact) 44.dp else height), glow = color, shape = RoundedCornerShape(if (compact) 14.dp else 16.dp), onClick = onClick) {
        Box(Modifier.fillMaxSize().background(if (on) color.copy(alpha = 0.22f) else Color.Transparent))
        // Compact: a short name alone, in the tile's colour while it is on.
        if (compact) {
            Text(
                short ?: label, Modifier.align(Alignment.Center).padding(horizontal = 4.dp), style = MaterialTheme.typography.labelMedium,
                color = if (on) color else Palette.Text, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            return@FocusCard
        }
        Column(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.Center) {
            Text(label, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                value ?: if (on) "On" else "Off", style = MaterialTheme.typography.labelMedium, color = if (on) color else Palette.Muted,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ActionTile(label: String, color: Color, modifier: Modifier, onClick: () -> Unit) {
    val compact = LocalCompactTiles.current
    FocusCard(modifier.height(if (compact) 44.dp else 48.dp), glow = color, shape = RoundedCornerShape(if (compact) 14.dp else 16.dp), onClick = onClick) {
        Box(Modifier.fillMaxSize().padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
            Text(label, style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Quick tiles drawn small, one line each (the side panel). */
private val LocalCompactTiles = androidx.compose.runtime.compositionLocalOf { false }

/** The width of one small tile, so a row that isn't full keeps its tiles the same size. */
private val LocalTileWidth = androidx.compose.runtime.compositionLocalOf<Dp?> { null }

/** A tile's share of its row: an equal part of a full-width row, or one small tile's width. */
@Composable
private fun androidx.compose.foundation.layout.RowScope.cell(): Modifier = LocalTileWidth.current?.let { Modifier.width(it) } ?: Modifier.weight(1f)

/** A row of quick tiles: one row as wide as the panel, or (in the narrow side panel) small ones, five to a row. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun TileRow(narrow: Boolean, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    if (narrow) androidx.compose.foundation.layout.BoxWithConstraints {
        // A hair under a fifth, so rounding never pushes the fifth tile onto the next row.
        val w = (maxWidth - 7.dp * 4) / 5 - 0.5.dp
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp), maxItemsInEachRow = 5,
            content = { androidx.compose.runtime.CompositionLocalProvider(LocalCompactTiles provides true, LocalTileWidth provides w) { content() } },
        )
    } else Row(horizontalArrangement = Arrangement.spacedBy(9.dp), content = content)
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
