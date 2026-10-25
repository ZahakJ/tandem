package io.github.zahakj.tandem.ui

import android.content.Context
import android.os.SystemClock
import android.text.format.Formatter
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import coil.compose.AsyncImage
import io.github.zahakj.tandem.Collections
import io.github.zahakj.tandem.DeviceInfo
import io.github.zahakj.tandem.Game
import io.github.zahakj.tandem.Launcher
import io.github.zahakj.tandem.Libraries
import io.github.zahakj.tandem.MainActivity
import io.github.zahakj.tandem.Privacy
import io.github.zahakj.tandem.RetroAchievements
import io.github.zahakj.tandem.RunningGames
import io.github.zahakj.tandem.StorageInfo
import io.github.zahakj.tandem.VolumeInfo
import io.github.zahakj.tandem.data.HomeStore
import io.github.zahakj.tandem.data.Store
import io.github.zahakj.tandem.data.WidgetSize
import io.github.zahakj.tandem.data.WidgetSize.Medium
import io.github.zahakj.tandem.data.WidgetSize.Small
import io.github.zahakj.tandem.data.WidgetSize.Tall
import io.github.zahakj.tandem.data.WidgetSize.Wide
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.cos
import kotlin.math.sin

/** Every built-in widget, in the order the gallery shows them. */
object WidgetKinds {
    private val ALL = listOf(Small, Medium, Wide, Tall)

    val all: List<WidgetKind> = listOf(
        WidgetKind(
            "storage", "Storage", "Free space, the biggest systems and folders. A measures again.", "◔", Palette.Mint,
            ALL, Medium,
            options = listOf(
                WidgetOption("list", "List", listOf("systems" to "Biggest systems", "folders" to "Biggest folders", "none" to "Just the rings"), "systems") { it != Small },
                toggleOption("extras", "Art & cache sizes", true) { it != Small },
            ),
            actions = { listOf(WidgetAction("Measure again", color = Palette.Mint) { WidgetUi.rescan++; Toasts.show("Measuring your folders…") }) },
            content = { s, m -> StorageWidget(s, m) },
        ),
        WidgetKind(
            "battery", "Battery", "Charge, temperature and time left. A opens quick settings.", "⚡", Palette.Butter,
            listOf(Small, Medium, Tall), Small,
            options = listOf(toggleOption("temp", "Temperature", true), toggleOption("estimate", "Time left", true)),
            content = { s, m -> BatteryWidget(s, m) },
        ),
        WidgetKind(
            "clock", "Clock", "Digital, analog or big and friendly. A switches the style.", "◷", Palette.Lavender,
            ALL, Small,
            options = listOf(
                WidgetOption("style", "Style", listOf("digital" to "Digital", "analog" to "Analog", "big" to "Big & friendly"), "digital"),
                WidgetOption("hours", "Hours", listOf("system" to "Like Android", "12" to "12-hour", "24" to "24-hour"), "system"),
                toggleOption("seconds", "Seconds", false),
                toggleOption("date", "Date", true),
            ),
            content = { s, m -> ClockWidget(s, m) },
        ),
        WidgetKind(
            "nowplaying", "Now playing", "The game waiting in the background. A resumes it.", "▶", Palette.Mint,
            listOf(Small, Medium, Wide, Tall), Medium,
            content = { s, m -> NowPlayingWidget(s, m) },
        ),
        WidgetKind(
            "continue", "Continue playing", "Your latest games, one press away.", "↺", Palette.Mint,
            listOf(Medium, Wide, Tall), Wide,
            content = { s, m ->
                val list = rememberGames { Collections.continuePlaying() }
                GameStrip(s, m, "Continue playing", list.map { StripItem(it.key, it, it.title) }, "Nothing played yet", "Your latest games land here")
            },
        ),
        WidgetKind(
            "favorites", "Favorites", "The games you ♥.", "♥", Palette.Peach,
            listOf(Medium, Wide, Tall), Wide,
            options = listOf(WidgetOption("order", "Order", listOf("name" to "A to Z", "recent" to "Recently played", "most" to "Most played"), "name")),
            content = { s, m ->
                val order = s.opt("order")
                val list = rememberGames(order) {
                    val f = Collections.favorites()
                    when (order) {
                        "recent" -> f.sortedByDescending { it.meta.lastPlayed }
                        "most" -> f.sortedByDescending { it.meta.playSeconds }
                        else -> f
                    }
                }
                GameStrip(s, m, "Favorites", list.map { StripItem(it.key, it, it.title) }, "No favorites yet", "Press Y on a game › Add to favorites")
            },
        ),
        WidgetKind(
            "random", "Surprise me", "A random pick, with a reroll.", "🎲", Palette.Lavender,
            listOf(Small, Medium, Tall), Medium,
            options = listOf(WidgetOption("pool", "Pick from", listOf("unplayed" to "Never played", "any" to "Everything", "favorites" to "Favorites"), "unplayed")),
            actions = { w -> listOf(WidgetAction("Pick another", color = Palette.Lavender, closes = false) { Surprise.reroll(w.id, w.options["pool"] ?: "unplayed") }) },
            content = { s, m -> SurpriseWidget(s, m) },
        ),
        WidgetKind(
            "recent", "Recently added", "New arrivals in your library.", "✦", Palette.Sky,
            listOf(Medium, Wide, Tall), Wide,
            content = { s, m ->
                val list = rememberGames { Collections.recentlyAdded() }
                val week = System.currentTimeMillis() - 7L * 24 * 3600_000
                GameStrip(s, m, "Recently added", list.map { StripItem(it.key, it, it.title, badge = if (it.meta.firstSeen > week) "New" else null) }, "No games yet", "Add some to your ROMs folder")
            },
        ),
        WidgetKind(
            "stats", "Play stats", "Today, this week, your streak and a little chart. A opens stats.", "★", Palette.Butter,
            ALL, Medium,
            content = { s, m -> StatsWidget(s, m) },
        ),
        WidgetKind(
            "achievements", "Achievements", "Your latest RetroAchievements unlocks.", "🏆", Palette.Butter,
            listOf(Medium, Wide, Tall), Medium,
            available = { HomeStore.ra.let { it.enabled && it.user.isNotBlank() && it.key.isNotBlank() } },
            needs = "Sign in to RetroAchievements in Settings first",
            actions = { listOf(WidgetAction("Check again", color = Palette.Butter) { RaRecent.at = 0; RaRecent.version++ }) },
            content = { s, m -> AchievementsWidget(s, m) },
        ),
        WidgetKind(
            "pip", "Pip", "Your little companion. Pip notices the time and your streak.", "☺", Palette.Peach,
            listOf(Small, Medium, Tall), Small,
            options = listOf(
                toggleOption("talk", "Speech bubble", true),
                WidgetOption("mood", "Mood", listOf("auto" to "Automatic", "happy" to "Happy", "excited" to "Excited", "sleepy" to "Sleepy"), "auto"),
            ),
            content = { s, m -> PipWidget(s, m) },
        ),
        WidgetKind(
            "upnext", "Up next", "Your list of games to play next. Y on any game › Add to Up next.", "☰", Palette.Sky,
            listOf(Medium, Wide, Tall), Wide,
            options = listOf(WidgetOption("played", "Played games", listOf("show" to "Show with ✓", "hide" to "Hide"), "show")),
            actions = { _ ->
                buildList {
                    add(WidgetAction("Add a game or note…", color = Palette.Sky) { UpNext.ask() })
                    val played = UpNext.shown().count { (e, g) -> UpNext.played(e, g) }
                    if (played > 0) add(WidgetAction("Clear played games", "$played", Palette.Mint, closes = false, refocus = true) { Toasts.show("Cleared ${plural(UpNext.clearPlayed(), "game", "games")}") })
                    for ((e, g) in UpNext.shown().take(12)) add(WidgetAction("Remove ${g?.title ?: e.note.orEmpty()}", null, Palette.Peach, closes = false, refocus = true) { UpNext.remove(e) })
                }
            },
            content = { s, m -> UpNextWidget(s, m) },
        ),
        WidgetKind(
            "sysmon", "System monitor", "Free memory, CPU temperature (when Android shares it) and uptime.", "▤", Palette.Sky,
            listOf(Small, Medium, Tall), Medium,
            options = listOf(toggleOption("uptime", "Uptime", true)),
            content = { s, m -> SysmonWidget(s, m) },
        ),
        WidgetKind(
            "apps", "App shortcuts", "Your dock apps in a mini dock.", "⊞", Palette.Sky,
            listOf(Medium, Wide, Tall), Medium,
            actions = { listOf(WidgetAction("Choose dock apps", color = Palette.Sky) { Panels.openApps() }) },
            content = { s, m -> AppsWidget(s, m) },
        ),
        WidgetKind(
            "greeting", "Greeting", "Pip says hello, with the time and your last game. A plays it.", "✿", Palette.Lavender,
            listOf(Medium, Wide, Tall), Wide,
            content = { s, m -> GreetingWidget(s, m) },
        ),
    )

    private val byId = all.associateBy { it.id }
    fun byId(id: String): WidgetKind? = byId[id]
}

// ---------------------------------------------------------------- shared pieces

private fun play(ctx: Context, g: Game) = Launcher.launch(Host.activity ?: ctx, g)

private fun bytes(ctx: Context, b: Long): String = Formatter.formatShortFileSize(ctx, b)

/** Minutes as "45m" or "2h 5m". */
private fun hm(minutes: Long): String = if (minutes < 60) "${minutes}m" else "${minutes / 60}h ${minutes % 60}m"

/** Play time for widgets: "0m", "under 1m", "45m", "2h 5m". */
private fun shortPlay(seconds: Long): String = when {
    seconds <= 0 -> "0m"
    seconds < 60 -> "under 1m"
    else -> hm(seconds / 60)
}

/** A game list from the library, recomputed only when the library, its details or privacy change. */
@Composable
private fun rememberGames(vararg keys: Any?, compute: () -> List<Game>): List<Game> {
    val meta = Store.meta
    val libs = Libraries.all
    val hidden = Privacy.view
    return remember(meta, libs, hidden, *keys) { compute() }
}

@Composable
private fun Header(icon: String, title: String, color: Color, modifier: Modifier = Modifier, trailing: String? = null) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(icon, style = MaterialTheme.typography.labelLarge, color = color)
        Spacer(Modifier.width(5.dp))
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (trailing != null) Text(trailing, style = MaterialTheme.typography.labelMedium, color = Palette.Muted, maxLines = 1)
    }
}

/** A ring that sweeps to [fraction] when it appears or changes. */
@Composable
fun Ring(fraction: Float, color: Color, modifier: Modifier, stroke: Dp = 7.dp, content: @Composable BoxScope.() -> Unit = {}) {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(fraction) { anim.animateTo(fraction.coerceIn(0f, 1f), spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessVeryLow)) }
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val s = stroke.toPx()
            val d = size.minDimension - s
            val tl = Offset((size.width - d) / 2, (size.height - d) / 2)
            drawArc(Palette.NightDeep.copy(alpha = 0.75f), 0f, 360f, false, tl, Size(d, d), style = Stroke(s))
            val sweep = 360f * anim.value
            if (sweep > 0.5f) drawArc(color, -90f, sweep, false, tl, Size(d, d), style = Stroke(s, cap = StrokeCap.Round))
        }
        content()
    }
}

/** A thin rounded bar that grows to [fraction]. */
@Composable
private fun Bar(fraction: Float, color: Color, modifier: Modifier) {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(fraction) { anim.animateTo(fraction.coerceIn(0f, 1f), spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessVeryLow)) }
    Canvas(modifier) {
        val r = CornerRadius(size.height / 2)
        drawRoundRect(Palette.NightDeep.copy(alpha = 0.75f), cornerRadius = r)
        val w = size.width * anim.value
        if (w > 0.5f) drawRoundRect(Brush.horizontalGradient(listOf(color.copy(alpha = 0.6f), color), endX = w), size = Size(w.coerceAtLeast(size.height), size.height), cornerRadius = r)
    }
}

/** A short centered message for empty widgets. */
@Composable
private fun EmptyNote(icon: String, title: String, detail: String?, color: Color, compact: Boolean, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        if (!compact) {
            Text(icon, style = MaterialTheme.typography.headlineSmall, color = color)
            Spacer(Modifier.height(2.dp))
        }
        Text(title, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (detail != null) Text(detail, style = MaterialTheme.typography.labelMedium, color = Palette.Muted, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** A small rounded action chip inside a widget, highlighted while it is the selected part. */
@Composable
private fun Chip(text: String, color: Color, selected: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier.clip(RoundedCornerShape(50))
            .background(if (selected) color else color.copy(alpha = 0.18f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = MaterialTheme.typography.labelMedium, color = if (selected) Palette.NightDeep else color, maxLines = 1) }
}

/** Highlight for the selected item inside a widget: a soft ring and a little pop. */
private fun Modifier.picked(on: Boolean, color: Color, shape: RoundedCornerShape): Modifier =
    graphicsLayer { val s = if (on) 1.07f else 1f; scaleX = s; scaleY = s }
        .then(if (on) Modifier.border(2.5.dp, color, shape) else Modifier)

// ---------------------------------------------------------------- storage

private fun fullnessColor(f: Float, base: Color) = when {
    f >= 0.9f -> Palette.Peach
    f >= 0.75f -> Palette.Butter
    else -> base
}

@Composable
private fun StorageWidget(s: WidgetScope, m: Modifier) {
    val ctx = LocalContext.current
    LaunchedEffect(Unit) {
        if (StorageInfo.volumes.isEmpty()) withContext(Dispatchers.IO) { StorageInfo.load(ctx); StorageInfo.readVolumes(ctx) }
    }
    val vols = StorageInfo.volumes
    val sizes = StorageInfo.sizes
    val listMode = s.opt("list")
    val libs = Libraries.all
    val rows: List<Pair<String, Long>> = remember(sizes, libs, listMode) {
        when (listMode) {
            "systems" -> StorageInfo.systemSizes().map { it.first.shortName to it.second }
            "folders" -> sizes?.biggest.orEmpty().map { it.name to it.bytes }
            else -> emptyList()
        }
    }
    val extras = s.on("extras") && sizes != null
    WidgetCard(s, m, onClick = { WidgetUi.rescan++; Toasts.show("Measuring your folders…") }) { _, _ ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(12.dp)) {
            val w = maxWidth
            val h = maxHeight
            if (vols.isEmpty()) {
                EmptyNote("◔", "Checking storage…", null, Palette.Mint, compact = true)
                return@BoxWithConstraints
            }
            val showList = listMode != "none" && s.size != Small
            if (h < 130.dp) {
                // One row: rings, then the list or the extras if there's room.
                val ring = min(h - 4.dp, 54.dp)
                Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val maxRings = if (w > 230.dp) 2 else 1
                    for (v in vols.take(maxRings)) VolumeChip(v, ring, ctx)
                    if (w > 330.dp && showList) StorageList(rows, sizes, Modifier.weight(1f), ctx, h)
                    else if (w > 230.dp && vols.size < 2 && extras) Extras(sizes!!, ctx, Modifier.weight(1f))
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    Header("◔", "Storage", Palette.Mint, trailing = if (StorageInfo.scanning) "measuring…" else null)
                    Spacer(Modifier.height(6.dp))
                    if (w < 200.dp) {
                        val v = vols.first()
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            VolumeChip(v, 52.dp, ctx)
                        }
                        vols.getOrNull(1)?.let { sd ->
                            Spacer(Modifier.height(4.dp))
                            Text("${sd.label} · ${bytes(ctx, sd.free)} free", style = MaterialTheme.typography.labelSmall, color = fullnessColor(sd.fraction, Palette.Sky), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            for (v in vols.take(2)) VolumeChip(v, if (h > 220.dp) 64.dp else 52.dp, ctx)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    if (showList) StorageList(rows, sizes, Modifier.weight(1f).fillMaxWidth(), ctx, null, narrow = w < 200.dp)
                    else Spacer(Modifier.weight(1f))
                    if (extras) {
                        val sz = sizes!!
                        Text(
                            "Art ${bytes(ctx, sz.mediaBytes)} · Cache ${bytes(ctx, sz.cacheBytes)}",
                            style = MaterialTheme.typography.labelSmall, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VolumeChip(v: VolumeInfo, ring: Dp, ctx: Context) {
    val color = fullnessColor(v.fraction, if (v.removable) Palette.Sky else Palette.Mint)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Ring(v.fraction, color, Modifier.size(ring), stroke = if (ring > 56.dp) 8.dp else 6.dp) {
            Text("${(v.fraction * 100).toInt()}%", style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
        Spacer(Modifier.width(7.dp))
        Column {
            Text(v.label, style = MaterialTheme.typography.labelMedium, color = Palette.Muted, maxLines = 1)
            Text(bytes(ctx, v.free), style = MaterialTheme.typography.titleSmall, color = color, maxLines = 1, softWrap = false)
            Text("free", style = MaterialTheme.typography.labelSmall, color = Palette.Muted, maxLines = 1)
        }
    }
}

@Composable
private fun Extras(sz: io.github.zahakj.tandem.FolderSizes, ctx: Context, modifier: Modifier) {
    val games = sz.roms.filterKeys { it != "downloaded_media" && it != "tandem" }.values.sum()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(1.dp)) {
        ExtraLine("Games", bytes(ctx, games), Palette.Mint)
        ExtraLine("Art & videos", bytes(ctx, sz.mediaBytes), Palette.Lavender)
        ExtraLine("Cache", bytes(ctx, sz.cacheBytes), Palette.Sky)
    }
}

@Composable
private fun ExtraLine(label: String, value: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = Palette.Muted, maxLines = 1)
        Text(value, style = MaterialTheme.typography.labelMedium, color = color, maxLines = 1)
    }
}

/** The biggest systems or folders as bars. [fixedHeight] limits the rows for one-row widgets. */
@Composable
private fun StorageList(rows: List<Pair<String, Long>>, sizes: io.github.zahakj.tandem.FolderSizes?, modifier: Modifier, ctx: Context, fixedHeight: Dp?, narrow: Boolean = false) {
    BoxWithConstraints(modifier) {
        if (rows.isEmpty()) {
            Text(
                if (StorageInfo.scanning || sizes == null) "Measuring folders…" else "Nothing to measure",
                Modifier.align(Alignment.CenterStart), style = MaterialTheme.typography.labelMedium, color = Palette.Muted,
            )
            return@BoxWithConstraints
        }
        val rowH = if (narrow) 26.dp else 19.dp
        val n = (((fixedHeight ?: maxHeight) + 3.dp) / (rowH + 3.dp)).toInt().coerceIn(1, 8)
        val max = rows.first().second.coerceAtLeast(1)
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            for ((name, b) in rows.take(n)) {
                val color = Palette.forName(name)
                if (narrow) {
                    Column(Modifier.height(rowH)) {
                        Row {
                            Text(name, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(bytes(ctx, b), style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1)
                        }
                        Bar(b / max.toFloat(), color, Modifier.fillMaxWidth().height(4.dp))
                    }
                } else {
                    Row(Modifier.height(rowH), verticalAlignment = Alignment.CenterVertically) {
                        Text(name, Modifier.width(62.dp), style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Bar(b / max.toFloat(), color, Modifier.weight(1f).height(7.dp))
                        Text(bytes(ctx, b), Modifier.width(58.dp).padding(start = 6.dp), style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1, textAlign = TextAlign.End)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- battery

@Composable
private fun BatteryWidget(s: WidgetScope, m: Modifier) {
    val ctx = LocalContext.current
    val b = rememberPolled(30_000) { DeviceInfo.battery(ctx) }
    WidgetCard(s, m, onClick = { Panels.toggleQuick() }) { _, _ ->
        if (b == null || b.level < 0) {
            EmptyNote("⚡", "Battery", "Checking…", Palette.Butter, compact = true)
            return@WidgetCard
        }
        val color = when {
            b.charging || b.full -> Palette.Mint
            b.level <= 15 -> Palette.Peach
            b.level <= 35 -> Palette.Butter
            else -> Palette.Mint
        }
        val status = when {
            b.full -> "Fully charged"
            b.charging -> "Charging"
            b.plugged -> "Plugged in"
            b.level <= 15 -> "Running low"
            else -> "On battery"
        }
        val estimate = b.minutesLeft?.takeIf { s.on("estimate") }?.let { if (b.charging) "Full in ${hm(it)}" else "About ${hm(it)} left" }
        val temp = b.tempC?.takeIf { s.on("temp") }?.let { "${"%.0f".format(it)}°C" + if (it >= 42f) " · warm" else "" }
        val ring = @Composable { size: Dp ->
            // Charging pulses on the shared ambient clock, read only in the layer (no recomposition per frame).
            val pulsing = rememberAmbient(b.charging && !Store.settings.reduceMotion)
            Ring(
                b.level / 100f, color,
                Modifier.size(size).graphicsLayer { alpha = if (pulsing) 0.55f + 0.45f * Ambient.pingPong(1100) else 1f },
                stroke = if (size > 60.dp) 8.dp else 6.dp,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (b.charging) Text("⚡", style = MaterialTheme.typography.labelSmall, color = Palette.Butter)
                    Text("${b.level}%", style = if (size > 60.dp) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleSmall, maxLines = 1)
                }
            }
        }
        when {
            s.small -> Column(Modifier.fillMaxSize().padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                ring(60.dp)
                Spacer(Modifier.height(3.dp))
                Text(temp?.let { "$status · ${it.substringBefore(' ')}" } ?: status, style = MaterialTheme.typography.labelSmall, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            s.high -> Column(Modifier.fillMaxSize().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Header("⚡", "Battery", Palette.Butter)
                Spacer(Modifier.weight(1f))
                ring(84.dp)
                Spacer(Modifier.height(8.dp))
                Text(status, style = MaterialTheme.typography.titleSmall, color = color)
                estimate?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = Palette.Muted, maxLines = 1) }
                temp?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = Palette.Muted, maxLines = 1) }
                Spacer(Modifier.weight(1f))
            }
            else -> Row(Modifier.fillMaxSize().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                ring(62.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Battery", style = MaterialTheme.typography.labelMedium, color = Palette.Butter)
                    Text(status, style = MaterialTheme.typography.titleSmall, color = color, maxLines = 1)
                    estimate?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    temp?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = Palette.Muted, maxLines = 1) }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- clock

private val CLOCK_STYLES = listOf("digital" to "Digital", "analog" to "Analog", "big" to "Big & friendly")

@Composable
private fun ClockWidget(s: WidgetScope, m: Modifier) {
    val ctx = LocalContext.current
    val seconds = s.on("seconds")
    val nowMs = rememberNow(if (seconds) 1000 else 60_000)
    val now = LocalDateTime.ofInstant(Instant.ofEpochMilli(nowMs), ZoneId.systemDefault())
    val is24 = when (s.opt("hours")) { "12" -> false; "24" -> true; else -> android.text.format.DateFormat.is24HourFormat(ctx) }
    val time = now.format(DateTimeFormatter.ofPattern(if (is24) "HH:mm" else "h:mm"))
    val ampm = if (is24) null else now.format(DateTimeFormatter.ofPattern("a"))
    val secs = if (seconds) now.format(DateTimeFormatter.ofPattern(":ss")) else null
    val showDate = s.on("date")
    val date = now.format(DateTimeFormatter.ofPattern("EEE d MMM"))
    val style = s.opt("style")
    WidgetCard(s, m, onClick = {
        val i = CLOCK_STYLES.indexOfFirst { it.first == style }
        val next = CLOCK_STYLES[(i + 1) % CLOCK_STYLES.size]
        Widgets.setOption(s.widget.id, "style", next.first)
        Toasts.show("Clock style: ${next.second}")
    }) { _, _ ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(10.dp), contentAlignment = Alignment.Center) {
            val w = maxWidth
            val h = maxHeight
            when (style) {
                "analog" -> {
                    if (s.small || (w < 200.dp && h < 130.dp)) {
                        AnalogFace(now, seconds, Modifier.size(min(w, h)))
                    } else if (h > w * 1.2f || (h > 150.dp && w < 220.dp)) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            AnalogFace(now, seconds, Modifier.size(min(w, h - 44.dp)))
                            Spacer(Modifier.height(6.dp))
                            Text(time + (ampm?.let { " $it" } ?: ""), style = MaterialTheme.typography.titleMedium)
                            if (showDate) Text(date, style = MaterialTheme.typography.labelLarge, color = Palette.Lavender)
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AnalogFace(now, seconds, Modifier.size(min(h, w / 2)))
                            Spacer(Modifier.width(14.dp))
                            Column {
                                Text(now.format(DateTimeFormatter.ofPattern("EEEE")), style = MaterialTheme.typography.titleLarge, maxLines = 1)
                                if (showDate) Text(now.format(DateTimeFormatter.ofPattern("d MMMM")), style = MaterialTheme.typography.titleSmall, color = Palette.Lavender, maxLines = 1)
                                Text(time + (ampm?.let { " $it" } ?: ""), style = MaterialTheme.typography.labelLarge, color = Palette.Muted)
                            }
                        }
                    }
                }
                "big" -> {
                    val brush = Brush.linearGradient(listOf(Palette.Butter, Palette.Peach, Palette.Lavender))
                    val stacked = h > w * 1.1f
                    if (stacked) {
                        val hours = now.format(DateTimeFormatter.ofPattern(if (is24) "HH" else "h"))
                        val mins = now.format(DateTimeFormatter.ofPattern("mm"))
                        val big = (min(w * 0.62f, h * 0.34f).value).sp
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(hours, style = MaterialTheme.typography.displaySmall.copy(brush = brush, fontSize = big, lineHeight = big, fontWeight = FontWeight.Black))
                            Text(mins, style = MaterialTheme.typography.displaySmall.copy(brush = brush, fontSize = big, lineHeight = big, fontWeight = FontWeight.Black))
                            if (showDate) Pill(now.format(DateTimeFormatter.ofPattern("EEEE")), Palette.Lavender)
                        }
                    } else {
                        val big = (min(h * (if (showDate) 0.52f else 0.7f), w / (time.length * 0.62f + 0.5f)).value).sp
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(time, style = MaterialTheme.typography.displaySmall.copy(brush = brush, fontSize = big, lineHeight = big, fontWeight = FontWeight.Black), maxLines = 1)
                            if (showDate) Text(
                                now.format(DateTimeFormatter.ofPattern("EEEE")) + (ampm?.let { " · $it" } ?: ""),
                                style = MaterialTheme.typography.labelLarge, color = Palette.Lavender, maxLines = 1,
                            )
                        }
                    }
                }
                else -> {
                    val wanted = when {
                        s.small -> 30f
                        h > 180.dp -> 54f
                        w > 380.dp -> 48f
                        else -> 40f
                    }
                    // Never wider than the card: Nunito digits are about 0.6 em wide.
                    val tailEm = (if (secs != null) 1.2f else 0f) + (if (ampm != null) 1.1f else 0f)
                    val main = minOf(wanted, w.value / (time.length * 0.6f + tailEm + 0.3f)).sp
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(time, style = MaterialTheme.typography.displaySmall.copy(fontSize = main, lineHeight = main), maxLines = 1)
                            val tail = listOfNotNull(secs, ampm?.let { " $it" }).joinToString("")
                            if (tail.isNotEmpty()) Text(tail, Modifier.padding(bottom = 4.dp, start = 2.dp), style = MaterialTheme.typography.titleSmall, color = Palette.Muted, maxLines = 1)
                        }
                        if (showDate) Text(date, style = MaterialTheme.typography.labelLarge, color = Palette.Lavender, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun AnalogFace(now: LocalDateTime, seconds: Boolean, modifier: Modifier) {
    Canvas(modifier) {
        val r = size.minDimension / 2
        val c = center
        drawCircle(Palette.NightDeep.copy(alpha = 0.55f), r)
        drawCircle(Palette.Lavender.copy(alpha = 0.35f), r - 1.dp.toPx(), style = Stroke(2.dp.toPx()))
        for (i in 0 until 12) {
            val a = Math.toRadians(i * 30.0)
            val p = c + Offset(sin(a).toFloat(), -cos(a).toFloat()) * (r * 0.8f)
            drawCircle(if (i % 3 == 0) Palette.Text else Palette.Muted.copy(alpha = 0.7f), if (i % 3 == 0) r * 0.05f else r * 0.03f, p)
        }
        fun hand(deg: Double, len: Float, color: Color, width: Float) {
            val a = Math.toRadians(deg)
            drawLine(color, c, c + Offset(sin(a).toFloat(), -cos(a).toFloat()) * len, strokeWidth = width, cap = StrokeCap.Round)
        }
        val min = now.minute + now.second / 60.0
        hand((now.hour % 12 + min / 60.0) * 30, r * 0.48f, Palette.Lavender, r * 0.09f)
        hand(min * 6, r * 0.7f, Palette.Mint, r * 0.06f)
        if (seconds) hand(now.second * 6.0, r * 0.76f, Palette.Peach, r * 0.025f)
        drawCircle(Palette.Butter, r * 0.07f, c)
    }
}

// ---------------------------------------------------------------- now playing

@Composable
private fun NowPlayingWidget(s: WidgetScope, m: Modifier) {
    val ctx = LocalContext.current
    val r = nowPlaying()
    val others = (RunningGames.list.size - 1).coerceAtLeast(0)
    val game = r?.game
    val color = game?.let { Palette.forName(it.platform.id) } ?: Palette.Mint
    val twoParts = r != null && others > 0 && !s.small
    WidgetCard(s, m, accent = color, cursorCount = if (twoParts) 2 else 0, onClick = { i ->
        when {
            r == null -> Toasts.show(if (RunningGames.canSee) "Start a game and it'll wait here" else "Allow Tandem in Shizuku to see running games")
            i == 1 && twoParts -> Panels.openSwitcher()
            else -> RunningGames.resume(Host.activity ?: ctx, r)
        }
    }) { focused, cursor ->
        if (r == null || game == null) {
            Row(Modifier.fillMaxSize().padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                if (!s.small) { Pip(48.dp, mood = 2); Spacer(Modifier.width(8.dp)) }
                EmptyNote(
                    "▶", if (RunningGames.canSee) "Nothing running" else "Can't see games",
                    if (RunningGames.canSee) "Games you leave open wait here" else "Needs Shizuku", Palette.Mint, compact = true,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
            return@WidgetCard
        }
        val lib = Libraries.of(game)
        val hero = lib?.hero(game) ?: lib?.screenshot(game)
        if (s.small) {
            // The art fades into the card's own tint where the name sits (a mask, not a dark band), and the name
            // carries a soft shadow.
            Box(Modifier.fillMaxSize().fadeMask(0f to 1f, 0.45f to 1f, 1f to 0.3f)) { GameArt(game) }
            Pill("● Running", Palette.Mint, Modifier.align(Alignment.TopStart).padding(6.dp).darkChip())
            Text(game.title, Modifier.align(Alignment.BottomStart).padding(TextInset), style = MaterialTheme.typography.labelLarge.onArt(), maxLines = 2, overflow = TextOverflow.Ellipsis)
            return@WidgetCard
        }
        // The art fades out on the left, where the text sits, into the card's own tint: no dark band over it.
        if (hero != null) AsyncImage(hero, null, Modifier.fillMaxSize().fadeMaskHorizontal(0f to 0.1f, 0.5f to 0.4f, 1f to 1f), contentScale = ContentScale.Crop)
        val played = RunningGames.sessionSeconds(r)
        val detail = if (played != null && played > 60) "${game.platform.shortName} · ${shortPlay(played)} this time" else "${game.platform.shortName} · ${r.appName}"
        if (s.high) {
            Column(Modifier.fillMaxSize().padding(12.dp)) {
                Text("● NOW PLAYING", style = MaterialTheme.typography.labelMedium, color = Palette.Mint)
                Spacer(Modifier.height(6.dp))
                Box(Modifier.weight(1f).aspectRatio(1f, matchHeightConstraintsFirst = true).align(Alignment.CenterHorizontally).clip(RoundedCornerShape(14.dp))) { GameArt(game) }
                Spacer(Modifier.height(6.dp))
                Text(game.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(detail, style = MaterialTheme.typography.labelSmall, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
                    Chip("▶ Resume", Palette.Mint, focused && cursor == 0)
                    if (twoParts) Chip("+$others", Palette.Lavender, focused && cursor == 1)
                }
            }
        } else {
            Row(Modifier.fillMaxSize().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.fillMaxHeight().aspectRatio(1f).clip(RoundedCornerShape(12.dp))) { GameArt(game) }
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text("● PAUSED IN ${r.appName.uppercase()}", style = MaterialTheme.typography.labelSmall, color = Palette.Mint, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(game.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(detail, style = MaterialTheme.typography.labelSmall, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Chip("▶ Resume", Palette.Mint, focused && cursor == 0)
                    if (twoParts) Chip("+$others more", Palette.Lavender, focused && cursor == 1)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- game strips

/** One entry in a strip: a game, or a note (Up next) when [game] is null. */
private data class StripItem(val key: String, val game: Game?, val label: String, val done: Boolean = false, val badge: String? = null)

/**
 * A row (or, for tall widgets, a list) of games. The D-pad moves along it; A runs [onItem] (plays the game by
 * default). [addSlot] adds a "+" tile at the end.
 */
@Composable
private fun GameStrip(
    s: WidgetScope,
    m: Modifier,
    title: String,
    items: List<StripItem>,
    emptyTitle: String,
    emptyDetail: String,
    addSlot: (() -> Unit)? = null,
    onItem: ((StripItem) -> Unit)? = null,
) {
    val ctx = LocalContext.current
    val accent = s.kind.accent
    val act: (StripItem) -> Unit = onItem ?: { it.game?.let { g -> play(ctx, g) } }
    BoxWithConstraints(m) {
        val pad = 10.dp
        val header = 20.dp
        val gap = 8.dp
        val innerW = maxWidth - pad * 2
        val innerH = maxHeight - pad * 2 - header
        val list = s.size == Tall || (s.high && maxWidth < 220.dp)
        val capacity: Int
        val tile: Dp
        val perRow: Int
        if (list) {
            tile = 36.dp
            perRow = 1
            capacity = ((innerH + 6.dp) / (tile + 6.dp)).toInt().coerceAtLeast(1)
        } else {
            val lines = ((innerH + gap) / (76.dp + gap)).toInt().coerceIn(1, 3)
            tile = ((innerH - gap * (lines - 1)) / lines).coerceAtMost(120.dp)
            perRow = ((innerW + gap) / (tile + gap)).toInt().coerceAtLeast(1)
            capacity = perRow * lines
        }
        val shown = items.take(if (addSlot != null) capacity - 1 else capacity)
        val count = shown.size + if (addSlot != null) 1 else 0
        WidgetCard(s, Modifier.fillMaxSize(), cursorCount = count, vertical = list, onClick = { i ->
            if (i < shown.size) act(shown[i]) else addSlot?.invoke()
        }) { focused, cursor ->
            Column(Modifier.fillMaxSize().padding(pad)) {
                val label = when {
                    !focused || count == 0 -> title
                    cursor < shown.size -> shown[cursor].label
                    else -> "Add to Up next"
                }
                Header(s.kind.icon, label, accent, Modifier.height(header), trailing = if (items.isNotEmpty() && !focused) "${items.size}" else null)
                if (shown.isEmpty() && addSlot == null) {
                    EmptyNote(s.kind.icon, emptyTitle, emptyDetail, accent, compact = true)
                    return@Column
                }
                if (list) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        shown.forEachIndexed { i, it ->
                            val on = focused && cursor == i
                            Row(
                                Modifier.fillMaxWidth().height(tile).clip(RoundedCornerShape(10.dp))
                                    .background(if (on) accent.copy(alpha = 0.22f) else Color.Transparent)
                                    .widgetTap(s) { act(it) },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(Modifier.size(tile).clip(RoundedCornerShape(9.dp))) { StripArt(it) }
                                Text(it.label, Modifier.weight(1f).padding(horizontal = 8.dp), style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (it.done) Palette.Muted else Palette.Text)
                                if (it.done) Text("✓", Modifier.padding(end = 6.dp), color = Palette.Mint, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                        if (addSlot != null) {
                            val on = focused && cursor == shown.size
                            Row(
                                Modifier.fillMaxWidth().height(tile).clip(RoundedCornerShape(10.dp)).background(accent.copy(alpha = if (on) 0.3f else 0.1f)).widgetTap(s) { addSlot() },
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
                            ) { Text(if (shown.isEmpty()) "+ Add your first game" else "+ Add", style = MaterialTheme.typography.labelLarge, color = accent) }
                        }
                    }
                } else {
                    Spacer(Modifier.height(2.dp))
                    val all = shown.size + if (addSlot != null) 1 else 0
                    Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                        for (rowStart in 0 until all step perRow) {
                            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                                for (i in rowStart until minOf(all, rowStart + perRow)) {
                                    val on = focused && cursor == i
                                    val shape = RoundedCornerShape(12.dp)
                                    if (i < shown.size) {
                                        val it = shown[i]
                                        Box(Modifier.size(tile).picked(on, Palette.Butter, shape).clip(shape).widgetTap(s) { act(it) }) {
                                            StripArt(it)
                                            if (it.done) Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.lerp(Palette.Mint, Palette.NightDeep, 0.7f).copy(alpha = 0.6f)), contentAlignment = Alignment.Center) {
                                                Text("✓", style = MaterialTheme.typography.headlineSmall, color = Palette.Mint)
                                            }
                                            it.badge?.let { b -> Pill(b, Palette.Sky, Modifier.align(Alignment.BottomStart).padding(3.dp).darkChip()) }
                                        }
                                    } else {
                                        Box(
                                            Modifier.size(tile).picked(on, Palette.Butter, shape).clip(shape).background(accent.copy(alpha = 0.16f)).widgetTap(s) { addSlot?.invoke() },
                                            contentAlignment = Alignment.Center,
                                        ) { Text("+", style = MaterialTheme.typography.headlineSmall, color = accent) }
                                    }
                                }
                            }
                        }
                    }
                    if (shown.isEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text(emptyDetail, style = MaterialTheme.typography.labelMedium, color = Palette.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun StripArt(it: StripItem) {
    val g = it.game
    if (g != null) GameArt(g)
    else Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Palette.Sky.copy(alpha = 0.45f), Palette.CardHigh))), contentAlignment = Alignment.Center) {
        Text(it.label, Modifier.padding(4.dp), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun UpNextWidget(s: WidgetScope, m: Modifier) {
    val ctx = LocalContext.current
    val hidePlayed = s.opt("played") == "hide"
    val entries = io.github.zahakj.tandem.data.WidgetStore.config.upNext
    val items = remember(entries, Store.meta, Libraries.all, Privacy.view, hidePlayed) {
        UpNext.shown().map { (e, g) -> StripItem("${e.key}|${e.note}|${e.added}", g, g?.title ?: e.note.orEmpty(), UpNext.played(e, g)) }
            .filter { !(hidePlayed && it.done) }
    }
    GameStrip(
        s, m, "Up next", items, "Nothing up next", "Y on a game › Add to Up next",
        addSlot = { UpNext.ask() },
        onItem = { item ->
            val g = item.game
            if (g != null) play(ctx, g)
            else UpNext.entries().firstOrNull { "${it.key}|${it.note}|${it.added}" == item.key }?.let { UpNext.editNote(it) }
        },
    )
}

// ---------------------------------------------------------------- surprise me

/** The current random pick of each Surprise widget (kept while Tandem runs). */
object Surprise {
    private val picks = mutableStateMapOf<String, String>()
    private val spinCount = mutableStateMapOf<String, Int>()
    fun spins(id: String): Int = spinCount[id] ?: 0

    private fun pool(kind: String): List<Game> {
        val all = Collections.visibleGames()
        return when (kind) {
            "favorites" -> all.filter { it.meta.favorite }
            "any" -> all
            else -> all.filter { it.meta.playCount == 0 }.ifEmpty { all }
        }
    }

    fun current(id: String, kind: String): Game? {
        val pool = pool(kind)
        val key = picks[id]
        return pool.firstOrNull { it.key == key }
    }

    fun ensure(id: String, kind: String) {
        if (current(id, kind) == null) pool(kind).randomOrNull()?.let { picks[id] = it.key }
    }

    fun reroll(id: String, kind: String) {
        val pool = pool(kind)
        val old = picks[id]
        (pool.filter { it.key != old }.ifEmpty { pool }).randomOrNull()?.let { picks[id] = it.key }
        spinCount[id] = spins(id) + 1
    }
}

@Composable
private fun SurpriseWidget(s: WidgetScope, m: Modifier) {
    val ctx = LocalContext.current
    val kind = s.opt("pool")
    val id = s.widget.id
    val libs = Libraries.all
    LaunchedEffect(id, kind, libs) { Surprise.ensure(id, kind) }
    val game = Surprise.current(id, kind)
    val spin by animateFloatAsState(Surprise.spins(id) * 360f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow), label = "dice")
    WidgetCard(s, m, accent = Palette.Lavender, cursorCount = if (game != null) 2 else 0, onClick = { i ->
        if (game == null) Toasts.show("Nothing to pick from yet")
        else if (i == 1) Surprise.reroll(id, kind) else play(ctx, game)
    }) { focused, cursor ->
        if (game == null) {
            EmptyNote("🎲", if (kind == "favorites") "No favorites yet" else "No games yet", "Nothing to pick from", Palette.Lavender, compact = s.small)
            return@WidgetCard
        }
        val dice = @Composable { mod: Modifier ->
            Box(
                mod.size(30.dp).clip(CircleShape).background(if (focused && cursor == 1) Palette.Lavender else Palette.NightDeep.copy(alpha = 0.8f))
                    .widgetTap(s) { Surprise.reroll(id, kind) },
                contentAlignment = Alignment.Center,
            ) { Text("🎲", Modifier.graphicsLayer { rotationZ = spin }, fontSize = 15.sp) }
        }
        Crossfade(game, label = "pick") { g ->
            when {
                s.small -> Box(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().fadeMask(0f to 1f, 0.45f to 1f, 1f to 0.3f)) { GameArt(g) }
                    Text(g.title, Modifier.align(Alignment.BottomStart).padding(TextInset).fillMaxWidth(0.8f), style = MaterialTheme.typography.labelLarge.onArt(), maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                s.high -> Column(Modifier.fillMaxSize().padding(10.dp)) {
                    Header("🎲", "Surprise me", Palette.Lavender)
                    Spacer(Modifier.height(6.dp))
                    Box(Modifier.weight(1f).aspectRatio(1f, matchHeightConstraintsFirst = true).align(Alignment.CenterHorizontally).picked(focused && cursor == 0, Palette.Butter, RoundedCornerShape(14.dp)).clip(RoundedCornerShape(14.dp))) { GameArt(g) }
                    Spacer(Modifier.height(6.dp))
                    Text(g.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(g.platform.name, style = MaterialTheme.typography.labelSmall, color = Palette.Muted, maxLines = 1)
                }
                else -> Row(Modifier.fillMaxSize().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.fillMaxHeight().aspectRatio(1f).picked(focused && cursor == 0, Palette.Butter, RoundedCornerShape(12.dp)).clip(RoundedCornerShape(12.dp))) { GameArt(g) }
                    Column(Modifier.weight(1f).padding(start = 10.dp, end = 30.dp)) {
                        Text("🎲 How about…", style = MaterialTheme.typography.labelMedium, color = Palette.Lavender, maxLines = 1)
                        Text(g.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(g.platform.shortName, style = MaterialTheme.typography.labelSmall, color = Palette.forName(g.platform.id), maxLines = 1)
                    }
                }
            }
        }
        dice(Modifier.align(Alignment.TopEnd).padding(6.dp))
    }
}

// ---------------------------------------------------------------- play stats

/** Today, the last 7 days and the streak, from the play history (hidden games only while revealed). */
class MiniStats(val today: Long, val week: Long, val days: LongArray, val streak: Int, val playedToday: Boolean)

@Composable
fun rememberMiniStats(): MiniStats {
    val sessions = HomeStore.sessions
    val hidden = Privacy.showHidden
    val view = Privacy.view
    val meta = Store.meta
    // Re-read every few minutes so "today" moves on at midnight.
    val day = rememberNow(5 * 60_000).let { LocalDate.now().toEpochDay() }
    return remember(sessions, view, meta, day) {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val days = LongArray(7)
        val playDays = HashSet<LocalDate>()
        for (s in sessions) {
            if (!hidden && !Privacy.canShowKey(s.k)) continue
            val d = Instant.ofEpochMilli(s.s).atZone(zone).toLocalDate()
            playDays += d
            val ago = ChronoUnit.DAYS.between(d, today)
            if (ago in 0..6) days[(6 - ago).toInt()] += s.d
        }
        var d = if (today in playDays) today else today.minusDays(1)
        var streak = 0
        while (d in playDays) { streak++; d = d.minusDays(1) }
        MiniStats(days[6], days.sum(), days, streak, today in playDays)
    }
}

@Composable
private fun StatsWidget(s: WidgetScope, m: Modifier) {
    val st = rememberMiniStats()
    val streak = if (st.streak == 0) "No streak yet" else "🔥 ${plural(st.streak, "day", "days")}"
    WidgetCard(s, m, onClick = { StatsTabs.tab = 0; if (Nav.route != Route.Stats) Nav.go(Route.Stats) }) { _, _ ->
        Box(Modifier.fillMaxSize().padding(12.dp)) {
            when {
                s.small -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                    Text("★ Today", style = MaterialTheme.typography.labelLarge, color = Palette.Butter)
                    Text(shortPlay(st.today), style = MaterialTheme.typography.headlineSmall, maxLines = 1)
                    Text(streak, style = MaterialTheme.typography.labelMedium, color = if (st.streak > 0) Palette.Peach else Palette.Muted, maxLines = 1)
                }
                s.high -> Column(Modifier.fillMaxSize()) {
                    Header("★", "Play time", Palette.Butter)
                    Spacer(Modifier.height(6.dp))
                    Text("Today", style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
                    Text(shortPlay(st.today), style = MaterialTheme.typography.headlineSmall, maxLines = 1)
                    Text("${shortPlay(st.week)} this week", style = MaterialTheme.typography.labelLarge, color = Palette.Mint, maxLines = 1)
                    Text(streak, style = MaterialTheme.typography.labelLarge, color = if (st.streak > 0) Palette.Peach else Palette.Muted, maxLines = 1)
                    Spacer(Modifier.height(8.dp))
                    WeekBars(st.days, Modifier.fillMaxWidth().weight(1f), labels = true)
                }
                else -> Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.widthIn(min = 84.dp)) {
                        Text("★ Today", style = MaterialTheme.typography.labelLarge, color = Palette.Butter)
                        Text(shortPlay(st.today), style = MaterialTheme.typography.titleLarge, maxLines = 1)
                        Text("${shortPlay(st.week)} this week", style = MaterialTheme.typography.labelMedium, color = Palette.Mint, maxLines = 1)
                        Text(streak, style = MaterialTheme.typography.labelMedium, color = if (st.streak > 0) Palette.Peach else Palette.Muted, maxLines = 1)
                    }
                    Spacer(Modifier.width(12.dp))
                    WeekBars(st.days, Modifier.weight(1f).fillMaxHeight(), labels = s.size == Wide)
                }
            }
        }
    }
}

private val DAY_LETTERS = DateTimeFormatter.ofPattern("EEEEE")

/** The last 7 days as soft bars that grow in; today is the warm one on the right. */
@Composable
private fun WeekBars(days: LongArray, modifier: Modifier, labels: Boolean) {
    val grow = remember { Animatable(0f) }
    LaunchedEffect(Unit) { grow.animateTo(1f, spring(Spring.DampingRatioLowBouncy, Spring.StiffnessVeryLow)) }
    val today = LocalDate.now()
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().weight(1f)) {
            val max = maxOf(days.max(), 20 * 60L).toFloat()
            val slot = size.width / 7f
            val barW = slot * 0.56f
            val base = size.height - 2.dp.toPx()
            for (i in 0 until 7) {
                val cx = slot * i + slot / 2
                val v = days[i]
                if (v == 0L) {
                    drawCircle(Palette.Muted.copy(alpha = 0.4f), 2.5.dp.toPx(), Offset(cx, base - 2.5.dp.toPx()))
                    continue
                }
                val h = ((base - 4.dp.toPx()) * (v / max) * grow.value).coerceAtLeast(5.dp.toPx())
                val brush = if (i == 6) Brush.verticalGradient(listOf(Palette.Butter, Palette.Peach), startY = base - h, endY = base)
                else Brush.verticalGradient(listOf(Palette.Mint, Palette.Sky.copy(alpha = 0.7f)), startY = base - h, endY = base)
                drawRoundRect(brush, Offset(cx - barW / 2, base - h), Size(barW, h), CornerRadius(barW / 2))
            }
        }
        if (labels) {
            Row(Modifier.fillMaxWidth()) {
                for (i in 0 until 7) {
                    Text(
                        today.minusDays((6 - i).toLong()).format(DAY_LETTERS), Modifier.weight(1f), textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall, color = if (i == 6) Palette.Peach else Palette.Muted,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------- achievements

/** Recent RetroAchievements unlocks, fetched at most every 10 minutes and only while a widget shows them. */
object RaRecent {
    var list by mutableStateOf<List<RetroAchievements.Earned>?>(null)
        private set
    var at = 0L
    var version by mutableIntStateOf(0)

    suspend fun refresh() {
        if (!RetroAchievements.enabled) return
        if (list != null && System.currentTimeMillis() - at < 10 * 60_000L) return
        at = System.currentTimeMillis()
        list = RetroAchievements.recent()
    }
}

@Composable
private fun AchievementsWidget(s: WidgetScope, m: Modifier) {
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(owner, RaRecent.version) { owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { RaRecent.refresh() } }
    val list = RaRecent.list
    WidgetCard(s, m, onClick = { StatsTabs.tab = 2; if (Nav.route != Route.Stats) Nav.go(Route.Stats) }) { _, _ ->
        Column(Modifier.fillMaxSize().padding(12.dp)) {
            if (list == null || list.isEmpty()) {
                Header("🏆", "Achievements", Palette.Butter)
                EmptyNote("🏆", if (list == null) "Checking…" else "No unlocks this month", if (list == null) null else "Your next one shows up here", Palette.Butter, compact = true)
                return@Column
            }
            val first = list.first()
            if (s.high) {
                Header("🏆", "Latest unlocks", Palette.Butter)
                Spacer(Modifier.height(6.dp))
                BoxWithConstraints(Modifier.weight(1f)) {
                    val n = ((maxHeight + 6.dp) / 46.dp).toInt().coerceAtLeast(1)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (a in list.take(n)) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(40.dp)) {
                            AsyncImage(a.badgeUrl, null, Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)))
                            Column(Modifier.padding(start = 8.dp)) {
                                Text(a.title, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${a.game} · ${a.points} pts", style = MaterialTheme.typography.labelSmall, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            } else {
                Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(first.badgeUrl, null, Modifier.size(58.dp).clip(RoundedCornerShape(12.dp)))
                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                        Text("🏆 Latest unlock", style = MaterialTheme.typography.labelMedium, color = Palette.Butter, maxLines = 1)
                        Text(first.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${first.game} · ${first.points} pts${if (first.hardcore) " · hardcore" else ""}", style = MaterialTheme.typography.labelSmall, color = Palette.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    if (s.size == Wide) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            for (a in list.drop(1).take(3)) AsyncImage(a.badgeUrl, null, Modifier.size(38.dp).clip(RoundedCornerShape(8.dp)))
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Pip

@Composable
private fun PipWidget(s: WidgetScope, m: Modifier) {
    val st = rememberMiniStats()
    val now = rememberNow(60_000)
    val hour = LocalDateTime.ofInstant(Instant.ofEpochMilli(now), ZoneId.systemDefault()).hour
    val unplayed = rememberGames { Collections.neverPlayed() }.size
    val mood = when (s.opt("mood")) {
        "happy" -> 0
        "excited" -> 1
        "sleepy" -> 2
        else -> when {
            hour < 6 -> 2
            st.streak >= 3 || st.playedToday -> 1
            else -> 0
        }
    }
    val lines = remember(hour / 3, st.streak, st.playedToday, unplayed) { pipLines(hour, st, unplayed) }
    var line by remember { mutableIntStateOf(0) }
    val hop = remember { Animatable(0f) }
    var hops by remember { mutableIntStateOf(0) }
    LaunchedEffect(hops) {
        if (hops == 0) return@LaunchedEffect
        hop.snapTo(0f)
        hop.animateTo(1f, tween(140)); hop.animateTo(0f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium))
    }
    val talk = s.on("talk")
    val text = lines[line % lines.size]
    WidgetCard(s, m, accent = Palette.Peach, onClick = { line++; hops++ }) { _, _ ->
        val pip = @Composable { size: Dp -> Pip(size, Modifier.graphicsLayer { translationY = -hop.value * size.toPx() * 0.18f }, mood = mood) }
        when {
            s.small -> Column(Modifier.fillMaxSize().padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                pip(if (talk) 52.dp else 72.dp)
                if (talk) Text(text, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            s.high -> Column(Modifier.fillMaxSize().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                pip(96.dp)
                if (talk) {
                    Spacer(Modifier.height(10.dp))
                    Bubble(text, Modifier.fillMaxWidth())
                }
            }
            else -> Row(Modifier.fillMaxSize().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                pip(72.dp)
                Spacer(Modifier.width(10.dp))
                if (talk) Bubble(text, Modifier.weight(1f))
                else Text("Pip", style = MaterialTheme.typography.titleLarge, color = Palette.Peach)
            }
        }
    }
}

@Composable
private fun Bubble(text: String, modifier: Modifier) {
    Box(modifier.clip(RoundedCornerShape(16.dp)).background(Palette.CardHigh).padding(horizontal = 10.dp, vertical = 7.dp)) {
        Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
}

private fun pipLines(hour: Int, st: MiniStats, unplayed: Int): List<String> = buildList {
    when (hour) {
        in 0..5 -> { add("Up late? Me too."); add("One more level, then bed?") }
        in 6..11 -> add("Good morning! Quick game before the day?")
        in 12..17 -> add("Afternoon break? I'll keep your seat warm.")
        else -> add("Cosy evening. What are we playing?")
    }
    when {
        st.streak >= 2 && st.playedToday -> add("${st.streak} days in a row! Look at you go.")
        st.streak >= 1 && !st.playedToday -> add("Play today to keep your ${plural(st.streak, "day", "days")} streak!")
        st.playedToday -> add("${shortPlay(st.today)} today. Nice!")
        else -> add("I missed you. Shall we play?")
    }
    if (unplayed > 0) add("${plural(unplayed, "game", "games")} you haven't tried yet 👀")
    add("Psst… Select picks a game for you.")
    add("Hold Select to see what's running.")
    add("I like it when you're here.")
}

// ---------------------------------------------------------------- system monitor

@Composable
private fun SysmonWidget(s: WidgetScope, m: Modifier) {
    val ctx = LocalContext.current
    val mem = rememberPolled(5_000) { DeviceInfo.memory(ctx) }
    val temp = rememberPolled(10_000) { DeviceInfo.cpuTempC() }
    val now = rememberNow(60_000)
    val uptime = if (s.on("uptime")) now.let { "Up ${hm(SystemClock.elapsedRealtime() / 60_000)}" } else null
    WidgetCard(s, m, accent = Palette.Sky) { _, _ ->
        if (mem == null) {
            EmptyNote("▤", "System", "Checking…", Palette.Sky, compact = true)
            return@WidgetCard
        }
        val color = fullnessColor(mem.usedFraction, Palette.Sky)
        val ring = @Composable { size: Dp ->
            Ring(mem.usedFraction, color, Modifier.size(size), stroke = 6.dp) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("RAM", style = MaterialTheme.typography.labelSmall, color = Palette.Muted)
                    Text("${(mem.usedFraction * 100).toInt()}%", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        val tempText = temp?.let { "CPU ${"%.0f".format(it)}°C" }
        val tempColor = temp?.let { if (it >= 70) Palette.Peach else if (it >= 55) Palette.Butter else Palette.Mint } ?: Palette.Muted
        when {
            s.small -> Column(Modifier.fillMaxSize().padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                ring(56.dp)
                Spacer(Modifier.height(3.dp))
                Text("${bytes(ctx, mem.free)} free", style = MaterialTheme.typography.labelSmall, color = Palette.Muted, maxLines = 1)
                tempText?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = tempColor, maxLines = 1) }
            }
            else -> {
                val content: @Composable ColumnScope.() -> Unit = {
                    Text("Memory", style = MaterialTheme.typography.labelMedium, color = Palette.Sky)
                    Text("${bytes(ctx, mem.free)} free", style = MaterialTheme.typography.titleSmall, maxLines = 1)
                    Text("of ${bytes(ctx, mem.total)}", style = MaterialTheme.typography.labelSmall, color = Palette.Muted, maxLines = 1)
                    tempText?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = tempColor, maxLines = 1) }
                    uptime?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = Palette.Muted, maxLines = 1) }
                }
                if (s.high) Column(Modifier.fillMaxSize().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    ring(84.dp); Spacer(Modifier.height(8.dp)); Column(horizontalAlignment = Alignment.CenterHorizontally, content = content)
                } else Row(Modifier.fillMaxSize().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    ring(62.dp); Spacer(Modifier.width(12.dp)); Column(content = content)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- app shortcuts

@Composable
private fun AppsWidget(s: WidgetScope, m: Modifier) {
    val ctx = LocalContext.current
    val resumes = (Host.activity as? MainActivity)?.resumeCount?.intValue ?: 0
    val apps = remember(HomeStore.settings.dock, resumes) { Dock.pinned(ctx) }
    BoxWithConstraints(m) {
        val vertical = s.size == Tall
        val slot = if (vertical) 40.dp else 66.dp
        val avail = if (vertical) maxHeight - 34.dp else maxWidth - 16.dp
        val fit = ((avail) / slot).toInt().coerceAtLeast(1)
        val shown = apps.take(fit - 1)
        val count = shown.size + 1
        WidgetCard(s, Modifier.fillMaxSize(), cursorCount = count, vertical = vertical, onClick = { i ->
            if (i < shown.size) Dock.open(Host.activity ?: ctx, shown[i]) else Panels.openApps()
        }) { focused, cursor ->
            val tile = @Composable { i: Int, mod: Modifier ->
                val on = focused && cursor == i
                val pkg = shown.getOrNull(i)
                if (vertical) {
                    Row(
                        mod.fillMaxWidth().height(36.dp).clip(RoundedCornerShape(10.dp)).background(if (on) Palette.Sky.copy(alpha = 0.22f) else Color.Transparent)
                            .widgetTap(s) { if (pkg != null) Dock.open(Host.activity ?: ctx, pkg) else Panels.openApps() },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (pkg != null) AppIcon(pkg, Modifier.padding(4.dp).size(28.dp)) else Text("⊞", Modifier.padding(horizontal = 9.dp), color = Palette.Sky, style = MaterialTheme.typography.titleMedium)
                        Text(if (pkg != null) RunningGames.appLabel(ctx, pkg) else "All apps", style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                } else {
                    Column(
                        mod.width(58.dp).picked(on, Palette.Sky, RoundedCornerShape(12.dp)).clip(RoundedCornerShape(12.dp))
                            .widgetTap(s) { if (pkg != null) Dock.open(Host.activity ?: ctx, pkg) else Panels.openApps() }.padding(vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        if (pkg != null) AppIcon(pkg, Modifier.size(40.dp))
                        else Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Palette.Sky.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                            Text("⊞", color = Palette.Sky, style = MaterialTheme.typography.titleLarge)
                        }
                        Text(if (pkg != null) RunningGames.appLabel(ctx, pkg) else "All apps", style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                    }
                }
            }
            if (vertical) Column(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Header("⊞", "Apps", Palette.Sky)
                for (i in 0 until count) tile(i, Modifier)
            } else Row(Modifier.fillMaxSize().padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly) {
                for (i in 0 until count) tile(i, Modifier)
            }
        }
    }
}

// ---------------------------------------------------------------- greeting

@Composable
private fun GreetingWidget(s: WidgetScope, m: Modifier) {
    val ctx = LocalContext.current
    val now = rememberNow(60_000)
    val date = LocalDateTime.ofInstant(Instant.ofEpochMilli(now), ZoneId.systemDefault())
    val hour = date.hour
    val hello = when (hour) { in 5..11 -> "Good morning!"; in 12..17 -> "Good afternoon!"; in 18..22 -> "Good evening!"; else -> "Up late? Me too." }
    val total = rememberGames { Collections.visibleGames() }.size
    val last = rememberGames { Collections.continuePlaying().take(1) }.firstOrNull()
    val time = java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(now))
    val games = if (total == 0) "No games yet" else "${plural(total, "game", "games")} ready to play"
    WidgetCard(s, m, accent = Palette.Lavender, onClick = { if (last != null) play(ctx, last) else Toasts.show(hello) }) { _, _ ->
        val lastCard = @Composable { mod: Modifier ->
            if (last != null) Row(
                mod.clip(RoundedCornerShape(14.dp)).background(Palette.Lavender.copy(alpha = 0.14f)).widgetTap(s) { play(ctx, last) }.padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp))) { GameArt(last) }
                Column(Modifier.padding(start = 9.dp)) {
                    Text("Last played", style = MaterialTheme.typography.labelSmall, color = Palette.Mint)
                    Text(last.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        if (s.high) {
            BoxWithConstraints(Modifier.fillMaxSize().padding(14.dp)) {
                val pip = min(maxHeight * 0.38f, 130.dp)
                val roomy = maxWidth > 200.dp && maxHeight > 260.dp
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Pip(pip, mood = if (hour in 0..5) 2 else 1)
                    Spacer(Modifier.height(10.dp))
                    Text(time, style = MaterialTheme.typography.displaySmall)
                    Text(hello, style = MaterialTheme.typography.titleLarge, color = Palette.Peach, textAlign = TextAlign.Center)
                    Text(games, style = MaterialTheme.typography.bodyMedium, color = Palette.Muted, textAlign = TextAlign.Center)
                    if (roomy) {
                        Spacer(Modifier.height(12.dp))
                        lastCard(Modifier.widthIn(max = 320.dp))
                    }
                }
            }
        } else {
            Row(Modifier.fillMaxSize().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Pip(64.dp, mood = if (hour in 0..5) 2 else 1)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(time, style = MaterialTheme.typography.headlineSmall, maxLines = 1)
                    Text(hello, style = MaterialTheme.typography.titleSmall, color = Palette.Peach, maxLines = 1)
                    Text(games, style = MaterialTheme.typography.labelMedium, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (s.size == Wide) lastCard(Modifier.widthIn(max = 190.dp))
            }
        }
    }
}
