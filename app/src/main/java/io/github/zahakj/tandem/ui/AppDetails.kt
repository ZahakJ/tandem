package io.github.zahakj.tandem.ui

import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.text.format.DateUtils
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import io.github.zahakj.tandem.AndroidApps
import io.github.zahakj.tandem.Collections
import io.github.zahakj.tandem.Game
import io.github.zahakj.tandem.Libraries
import io.github.zahakj.tandem.RunningGames
import io.github.zahakj.tandem.data.Store
import io.github.zahakj.tandem.everPlayed
import io.github.zahakj.tandem.plural
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/*
 * The other screen for an app highlighted in the dock: the app's own icon, big, over a soft blur of its colours that
 * fills the screen edge to edge, and what Tandem knows about it laid out from the top down (so nothing is pushed off
 * the bottom): what it is, its version, and for an emulator the games played with it. Built once per app off the
 * main thread and kept, so moving along the dock only cross-fades between ready pictures.
 */

/** What the details show for one app, read from the package manager. */
class AppLook(
    val label: String,
    /** The icon as the launcher draws it. */
    val icon: ImageBitmap?,
    /** A tiny copy of the icon's artwork (an adaptive icon's layers, unmasked), stretched and blurred for the backdrop. */
    val wash: ImageBitmap?,
    /** The icon's main colour, or null for a grey icon. */
    val color: Color?,
    val category: String?,
    val version: String?,
    val installed: Long,
    val updated: Long,
    val system: Boolean,
)

object AppLooks {
    private val cache = LruCache<String, AppLook>(32)

    fun cached(pkg: String): AppLook? = cache.get(pkg)

    /** Every app and how many are in the dock, as last read (for All apps). */
    @Volatile var allApps: Pair<List<AppEntry>, Int>? = null
        private set

    /** Blocking; call off the main thread. */
    fun loadAllApps(ctx: Context): Pair<List<AppEntry>, Int> =
        (Dock.allApps(ctx) to Dock.pinned(ctx).size).also { allApps = it }

    /** Blocking; call off the main thread. Null for Tandem's own apps and for apps that are gone. */
    fun load(ctx: Context, pkg: String): AppLook? {
        cache.get(pkg)?.let { return it }
        if (Dock.builtIn(pkg) != null) return null
        val pm = ctx.packageManager
        val info = runCatching { pm.getPackageInfo(pkg, 0) }.getOrNull() ?: return null
        val app = info.applicationInfo
        val drawable = runCatching { pm.getApplicationIcon(pkg) }.getOrNull()
        val icon = drawable?.let { render(it, 288) }
        val wash = drawable?.let { d ->
            if (d is AdaptiveIconDrawable) layers(d, 40) else render(d, 40)
        }
        val look = AppLook(
            label = RunningGames.appLabel(ctx, pkg),
            icon = icon?.asImageBitmap(),
            wash = wash?.asImageBitmap(),
            color = (wash ?: icon)?.let { keyColor(it) },
            category = app?.let { a ->
                ApplicationInfo.getCategoryTitle(ctx, a.category)?.toString()?.takeIf { a.category != ApplicationInfo.CATEGORY_UNDEFINED }
            },
            version = info.versionName?.takeIf { it.isNotBlank() }?.let { v -> if (v.length > 18) v.take(18) + "…" else v },
            installed = info.firstInstallTime,
            updated = info.lastUpdateTime,
            system = app != null && app.flags and ApplicationInfo.FLAG_SYSTEM != 0,
        )
        cache.put(pkg, look)
        return look
    }

    private fun render(d: Drawable, px: Int): Bitmap? = runCatching {
        Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888).also { b ->
            d.setBounds(0, 0, px, px)
            d.draw(Canvas(b))
        }
    }.getOrNull()

    /** An adaptive icon's background and foreground, unmasked, so the backdrop has colour right to its edges. */
    private fun layers(d: AdaptiveIconDrawable, px: Int): Bitmap? = runCatching {
        Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888).also { b ->
            val c = Canvas(b)
            for (layer in listOfNotNull(d.background, d.foreground)) {
                layer.setBounds(0, 0, px, px)
                layer.draw(c)
            }
        }
    }.getOrNull()

    /**
     * The icon's main colour: the busiest of twelve hue bands, each pixel counted by how vivid it is, averaged
     * within that band (so a two-colour icon gives one of its colours, not the mud between them). Null when the
     * icon is mostly grey.
     */
    private fun keyColor(b: Bitmap): Color? {
        val small = if (b.width > 48) Bitmap.createScaledBitmap(b, 40, 40, true) else b
        val weight = DoubleArray(12)
        val sums = Array(12) { DoubleArray(3) }
        val hsv = FloatArray(3)
        var vivid = 0.0
        var all = 0.0
        for (y in 0 until small.height) for (x in 0 until small.width) {
            val c = small.getPixel(x, y)
            if (android.graphics.Color.alpha(c) < 160) continue
            android.graphics.Color.colorToHSV(c, hsv)
            all += 1.0
            val w = (hsv[1] * hsv[2]).toDouble().let { it * it }
            if (w < 0.04) continue
            vivid += 1.0
            val bin = (hsv[0] / 30f).toInt().coerceIn(0, 11)
            weight[bin] += w
            sums[bin][0] += android.graphics.Color.red(c) * w
            sums[bin][1] += android.graphics.Color.green(c) * w
            sums[bin][2] += android.graphics.Color.blue(c) * w
        }
        if (all == 0.0 || vivid / all < 0.06) return null
        val best = weight.indices.maxBy { weight[it] }
        val w = weight[best]
        return Color((sums[best][0] / w / 255).toFloat(), (sums[best][1] / w / 255).toFloat(), (sums[best][2] / w / 255).toFloat())
    }
}

/** Builds the looks of [pkgs] in the background, so the first highlight of each already has its picture. */
@Composable
fun PrefetchAppLooks(pkgs: List<String>) {
    val ctx = LocalContext.current
    LaunchedEffect(pkgs) { withContext(Dispatchers.IO) { pkgs.forEach { AppLooks.load(ctx, it) } } }
}

/** The colour an app's details take: its icon's, or (grey icon, still loading) the theme's sky. */
fun appTint(pkg: String): Color = Dock.builtIn(pkg)?.color ?: AppLooks.cached(pkg)?.color ?: Palette.Sky

/** "3 minutes ago", "Yesterday", or "just now". */
private fun ago(time: Long): String {
    val now = System.currentTimeMillis()
    return if (now - time < 60_000) "just now" else DateUtils.getRelativeTimeSpanString(time, now, DateUtils.MINUTE_IN_MILLIS).toString()
}

/** The height of the button hints, so the content stays clear of them. */
private val AppHintsHeight = 36.dp

/**
 * Details for the app [pkg]. With [chrome] it draws its own button hints (the classic details screen); without, the
 * spotlight around it does, and [topClear] keeps the content below its status pill. [shift] (-1..1) slides the
 * backdrop while it cross-fades, as the spotlight's own do.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppDetails(pkg: String, chrome: Boolean, shift: () -> Float = { 0f }, topClear: Dp = 0.dp, front: () -> Float = { 1f }) {
    val ctx = LocalContext.current
    val builtIn = Dock.builtIn(pkg)
    val drawer = pkg == Dock.ALL_APPS
    val look by produceState(AppLooks.cached(pkg), pkg) {
        if (value == null && builtIn == null && !drawer) value = withContext(Dispatchers.IO) { AppLooks.load(ctx, pkg) }
    }
    // All apps: every app and how many are in the dock, refreshed in the background each time it is highlighted.
    val apps by produceState(AppLooks.allApps, drawer) {
        if (drawer) value = withContext(Dispatchers.IO) { AppLooks.loadAllApps(ctx) }
    }
    val l = look
    val label = when {
        drawer -> "All apps"
        else -> builtIn?.title ?: l?.label ?: remember(pkg) { RunningGames.appLabel(ctx, pkg) }
    }
    val tint = builtIn?.color ?: l?.color ?: Palette.Sky
    val dark = Themes.live.dark
    val running = builtIn == null && RunningGames.list.any { it.pkg == pkg }
    val meta = Store.meta
    val libs = Libraries.all
    // As an Android game in the library: its own play history. As an emulator: the systems it plays and the
    // (visible) games last played with it.
    val asGame = remember(pkg, libs, meta) {
        Libraries.byId(AndroidApps.PLATFORM_ID)?.games?.firstOrNull { AndroidApps.packageOf(it) == pkg }
    }
    val systems = remember(pkg, libs) {
        libs.orEmpty().filter { lib -> !lib.platform.isApps && lib.platform.allPlayers.any { it.packageName == pkg } }.map { it.platform }
    }
    val played = remember(pkg, libs, meta, systems) {
        val ids = systems.map { it.id }.toSet()
        if (ids.isEmpty()) emptyList()
        else Collections.visibleGames().filter { g -> g.meta.everPlayed && g.platform.id in ids && g.platform.playerFor(g)?.packageName == pkg }
            .sortedByDescending { it.meta.lastPlayed }
    }
    val kind = when {
        builtIn != null -> "Tandem app"
        drawer -> "App drawer"
        systems.isNotEmpty() || AndroidApps.isEmulator(pkg) -> "Emulator"
        asGame != null -> "Android game"
        l?.system == true -> "System app"
        else -> "Android app"
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        AppBackdrop(l?.wash, l?.icon, builtIn, tint, dark, shift)
        // Clear of the now-playing banner (top centre) when a game is running, and of the button hints below. The
        // content scrolls between the two if it ever runs long, but is sized from the room left to fit without.
        val topPad = 22.dp + maxOf(topClear, if (nowPlaying() != null) 56.dp else 0.dp)
        val bottomPad = 14.dp + AppHintsHeight + 12.dp
        val room = maxHeight - topPad - bottomPad
        val iconSize = (room * 0.26f).coerceIn(72.dp, 140.dp)
        val cover = if (room < 360.dp) 64.dp else 76.dp
        val scroll = rememberScrollState()
        Column(
            Modifier.fillMaxSize().padding(top = topPad, bottom = bottomPad).fadingEdges(scroll).verticalScroll(scroll)
                .padding(horizontal = 28.dp)
                .graphicsLayer { translationX = shift() * size.width * 0.04f; alpha = front() },
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                AppBigIcon(pkg, l?.icon, tint, iconSize)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(kind.uppercase(), style = MaterialTheme.typography.labelLarge, color = lerp(tint, Palette.Text, if (dark) 0.35f else 0.55f), maxLines = 1)
                    Text(label, style = MaterialTheme.typography.displaySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    val sub = when {
                        builtIn != null -> builtIn.blurb
                        drawer -> "Every app on this device. Pin your favourites to the dock with Y."
                        else -> listOfNotNull(l?.version?.let { "Version $it" }, l?.updated?.takeIf { it > 0 }?.let { "updated ${ago(it)}" }).joinToString(" · ")
                    }
                    if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodyLarge, color = Palette.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            val pills = buildList {
                if (running) add("● Running" to Palette.Mint)
                l?.category?.let { add(it to Palette.Lavender) }
                if (asGame != null) add("In Android Games" to Palette.Peach)
                systems.take(8).forEach { add(it.shortName to SystemColors.of(it.id)) }
                if (systems.size > 8) add("+${systems.size - 8} more" to Palette.Muted)
            }
            if (pills.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    pills.forEach { (text, color) -> Pill(text, lerp(color, Palette.Text, if (dark) 0.15f else 0.35f), Modifier.background(Palette.NightDeep.copy(alpha = 0.35f), RoundedCornerShape(50))) }
                }
            }
            val facts = buildList {
                if (drawer) apps?.let { (all, docked) ->
                    add("Apps" to "${all.size}")
                    add("In the dock" to "$docked")
                }
                asGame?.meta?.let { m ->
                    add("Played" to if (m.playSeconds >= 60) formatPlayLong(m.playSeconds) else if (m.playCount > 0) plural(m.playCount, "time") else "Not yet")
                    if (m.lastPlayed > 0) add("Last played" to ago(m.lastPlayed))
                }
                if (systems.isNotEmpty()) {
                    add("Games played" to if (played.isEmpty()) "None yet" else "${played.size}")
                    played.sumOf { it.meta.playSeconds }.takeIf { it >= 60 }?.let { add("Time played" to formatPlayLong(it)) }
                    played.firstOrNull()?.meta?.lastPlayed?.takeIf { it > 0 }?.let { add("Last used" to ago(it)) }
                }
                // A system app's install date is just when the system was built: not worth a line.
                l?.installed?.takeIf { it > 0 && !l.system }?.let {
                    add("Installed" to DateUtils.formatDateTime(ctx, it, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH or DateUtils.FORMAT_SHOW_YEAR))
                }
            }
            if (facts.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    facts.forEach { (name, value) -> Fact(name, value) }
                }
            }
            if (played.isNotEmpty()) PlayedWith(played, label, cover)
            if (drawer) apps?.first?.takeIf { it.isNotEmpty() }?.let { AppPeek(it) }
        }
        if (chrome) {
            Row(
                Modifier.align(Alignment.BottomEnd).padding(14.dp).heightIn(min = AppHintsHeight)
                    .background(Palette.Card.copy(alpha = 0.9f), RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) { AppHints(pkg) }
        }
    }
}

/** What the buttons do on a dock app. */
@Composable
fun AppHints(pkg: String) {
    ButtonHint("A", "Open", Palette.Mint)
    // Tandem's own apps (and the All apps button) have no Android app menu; in the All apps drawer Y pins to the dock.
    if (Panels.apps) ButtonHint("Y", "Pin to dock", Palette.Butter)
    else if (Dock.builtIn(pkg) == null && pkg != Dock.ALL_APPS) ButtonHint("Y", "Options", Palette.Butter)
    if (ScreenRoles.xSwaps()) ButtonHint("X", "Swap", Palette.Sky)
}

/**
 * The backdrop: the icon's artwork stretched over the whole screen and blurred into soft colour, on the theme's
 * background, with a wash of the theme towards the bottom so the text reads. Every layer fills the screen; the
 * blur clamps at the edges instead of fading to a dark rim.
 */
@Composable
private fun AppBackdrop(wash: ImageBitmap?, icon: ImageBitmap?, builtIn: BuiltInApp?, tint: Color, dark: Boolean, shift: () -> Float) {
    val base = Palette.Night
    val deep = Palette.NightDeep
    BoxWithConstraints(
        Modifier.fillMaxSize().background(lerp(base, tint, if (dark) 0.3f else 0.14f))
            .graphicsLayer {
                // Zoomed just enough while it slides that its edges never come into view.
                val v = shift()
                translationX = v * size.width * 0.1f
                val s = 1f + 0.2f * kotlin.math.abs(v)
                scaleX = s; scaleY = s
            },
    ) {
        if (wash != null) {
            Image(
                wash, null,
                Modifier.fillMaxSize().graphicsLayer { scaleX = 1.25f; scaleY = 1.25f; alpha = if (dark) 0.45f else 0.35f }
                    .blur(56.dp, BlurredEdgeTreatment.Rectangle),
                contentScale = ContentScale.Crop, filterQuality = FilterQuality.Low,
            )
        }
        Box(
            Modifier.fillMaxSize().drawWithCache {
                val fall = Brush.verticalGradient(
                    0f to tint.copy(alpha = if (dark) 0.10f else 0.06f),
                    0.45f to base.copy(alpha = if (dark) 0.3f else 0.4f),
                    1f to deep.copy(alpha = if (dark) 0.78f else 0.72f),
                )
                val glow = Brush.radialGradient(
                    listOf(tint.copy(alpha = if (dark) 0.32f else 0.22f), Color.Transparent),
                    center = Offset(size.width * 0.16f, size.height * 0.2f), radius = size.maxDimension * 0.45f,
                )
                onDrawBehind {
                    drawRect(fall)
                    drawRect(glow)
                }
            },
        )
        // The icon again, huge and faint, tilted off the bottom right corner: the lower half carries its colour and
        // shape instead of an empty band.
        val mark = maxHeight * 0.78f
        Box(
            Modifier.align(Alignment.BottomEnd).size(mark).graphicsLayer {
                translationX = size.width * 0.22f; translationY = size.height * 0.2f
                rotationZ = -14f; alpha = if (dark) 0.08f else 0.1f
                shape = RoundedCornerShape(mark * 0.24f); clip = true
            },
        ) {
            when {
                builtIn != null -> TandemIconTile(builtIn.icon, builtIn.color)
                icon != null -> Image(icon, null, Modifier.fillMaxSize(), filterQuality = FilterQuality.Low)
            }
        }
    }
}

/** The app's icon, big, with a soft shadow in its own colour; Tandem's apps get their drawn icon. */
@Composable
private fun AppBigIcon(pkg: String, icon: ImageBitmap?, tint: Color, size: Dp) {
    val builtIn = Dock.builtIn(pkg)
    val shape = RoundedCornerShape(size * 0.24f)
    Box(
        Modifier.size(size).graphicsLayer {
            // An adaptive icon is masked already (its circle sits inside this shape); a legacy one can bring square corners.
            this.shape = shape; clip = true
            shadowElevation = if (builtIn != null) 18f * density else 0f
            spotShadowColor = Color.Black; ambientShadowColor = tint
        },
    ) {
        when {
            builtIn != null -> TandemIconTile(builtIn.icon, builtIn.color)
            pkg == Dock.ALL_APPS -> Box(Modifier.fillMaxSize().background(lerp(Palette.Card, Palette.Sky, 0.28f)), contentAlignment = Alignment.Center) {
                GlyphIcon(Glyph.Grid, Palette.Sky, Modifier.fillMaxSize(0.52f))
            }
            icon != null -> Image(icon, null, Modifier.fillMaxSize(), filterQuality = FilterQuality.High)
            // Still loading (only ever for a moment): the small dock icon, so the slot never sits empty.
            else -> AppIcon(pkg, Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun Fact(name: String, value: String) {
    Column(
        Modifier.widthIn(min = 104.dp).background(Palette.Card.copy(alpha = 0.62f), RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(name, style = MaterialTheme.typography.labelMedium, color = Palette.Muted, maxLines = 1)
        Text(value, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A peek into All apps: two rows of icons, as many as fit across, and a count of the rest. */
@Composable
private fun AppPeek(apps: List<AppEntry>) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val icon = 52.dp
        val gap = 14.dp
        val across = ((maxWidth + gap) / (icon + gap)).toInt().coerceAtLeast(1)
        val room = across * 2
        val more = apps.size - room
        val shown = apps.take(if (more > 0) room - 1 else room)
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            for (row in shown.chunked(across).take(2)) {
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    row.forEach { a -> AppIcon(a.pkg, Modifier.size(icon).clip(RoundedCornerShape(icon * 0.24f))) }
                    if (more > 0 && row.size < across) {
                        Box(
                            Modifier.size(icon).background(Palette.Card.copy(alpha = 0.62f), RoundedCornerShape(icon * 0.3f)),
                            contentAlignment = Alignment.Center,
                        ) { Text("+${more + 1}", style = MaterialTheme.typography.titleSmall, color = Palette.Text) }
                    }
                }
            }
        }
    }
}

/** The games last played with an emulator: as many covers as fit across, and a count of the rest. */
@Composable
private fun PlayedWith(games: List<Game>, app: String, cover: Dp) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Recently played in $app", style = MaterialTheme.typography.titleSmall, color = Palette.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val gap = 10.dp
            val fit = ((maxWidth + gap) / (cover + gap)).toInt().coerceAtLeast(1)
            val more = games.size - fit
            val shown = games.take(if (more > 0) fit - 1 else fit)
            val px = tilePx(cover)
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                for (g in shown) {
                    Column(Modifier.width(cover)) {
                        Box(Modifier.size(cover).graphicsLayer { shape = RoundedCornerShape(12.dp); clip = true }) {
                            val box = remember(g.key, Store.meta) { Libraries.of(g)?.box(g) }
                            if (box != null) AsyncImage(rememberSizedRequest(box, px), g.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            else GameArt(g)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(g.title, style = MaterialTheme.typography.labelSmall, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (more > 0) {
                    Box(
                        Modifier.size(cover).background(Palette.Card.copy(alpha = 0.62f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center,
                    ) { Text("+${more + 1}", style = MaterialTheme.typography.titleMedium, color = Palette.Text) }
                }
            }
        }
    }
}
