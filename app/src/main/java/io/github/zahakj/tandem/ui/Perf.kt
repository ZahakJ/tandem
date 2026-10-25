package io.github.zahakj.tandem.ui

import android.content.Context
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.metrics.performance.JankStats
import androidx.metrics.performance.PerformanceMetricsState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/*
 * The frame timing overlay (Settings › Data & about › Developer): JankStats watches every frame of the top screen's
 * window; janky ones (longer than twice the display's frame time) are logged with what was on screen, under the tag
 * TandemJank, and a small corner readout shows the running totals. docs/PERFORMANCE.md explains how to use it.
 */

/** Developer switches, kept in their own small preferences file. */
object DevTools {
    private const val PREFS = "dev"
    private var app: Context? = null

    /** The frame timing overlay and its logging are on. */
    var frameOverlay by mutableStateOf(false)
        private set

    fun init(ctx: Context) {
        app = ctx.applicationContext
        frameOverlay = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("frameOverlay", false)
    }

    fun showFrameOverlay(on: Boolean) {
        frameOverlay = on
        app?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()?.putBoolean("frameOverlay", on)?.apply()
        if (on) FrameStats.reset()
    }

    private var tour: kotlinx.coroutines.Job? = null
    var touring by mutableStateOf(false)
        private set

    /**
     * Shows every built-in theme and the newest imported pack in turn, a few seconds each, to look over contrast and
     * layout (ThemeContrastTest checks the numbers). Only a preview: the chosen theme is never changed, and pressing
     * it again stops the tour.
     */
    fun tourThemes() {
        if (touring) { tour?.cancel(); touring = false; return }
        touring = true
        val themes = BuiltInThemes.list + listOfNotNull(ThemePacks.newestCocoonPack()?.spec)
        tour = kotlinx.coroutines.MainScope().launch {
            try {
                for ((i, t) in themes.withIndex()) {
                    Themes.preview = t.id
                    Toasts.show("Theme ${i + 1} of ${themes.size}: ${t.name}", 3000)
                    Log.i("TandemTheme", "${i + 1}/${themes.size} ${t.id}")
                    delay(3200)
                }
            } finally {
                Themes.preview = null
                touring = false
            }
        }
    }
}

/** Running totals, written on JankStats' thread and read by the readout twice a second. */
private object FrameStats {
    val frames = AtomicInteger()
    val janky = AtomicInteger()
    val worstNs = AtomicLong()
    @Volatile var last: String = ""

    fun reset() { frames.set(0); janky.set(0); worstNs.set(0); last = "" }
}

/** The Developer group's rows (Data & about page). */
@Composable
fun developerSettings(): SettingSection = SettingSection(
    "Developer",
    listOf(
        SettingRow(
            "Frame timing overlay", onOff(DevTools.frameOverlay), Palette.Lavender, Glyph.Chart, toggle = DevTools.frameOverlay,
            help = "Counts slow frames while you use Tandem and shows the totals in a corner. Each slow frame is also " +
                "written to the system log (tag TandemJank) with the screen it happened on, for measuring smoothness.",
            keywords = "developer performance jank stutter fps frames debug smooth",
        ) { DevTools.showFrameOverlay(!DevTools.frameOverlay) },
        SettingRow(
            "Tour every theme", if (DevTools.touring) "Stop" else "${BuiltInThemes.list.size} + imported", Palette.Lavender, Glyph.Theme,
            help = "Shows each built-in theme and the newest imported pack for a few seconds, to check colours and " +
                "text. Your theme stays as it is.",
            keywords = "developer themes contrast preview cycle debug",
        ) { DevTools.tourThemes() },
    ),
    Glyph.Chart,
)

/** Composed in the app root; does nothing (and costs nothing) while the overlay is off. */
@Composable
fun FrameTimingOverlay() {
    if (!DevTools.frameOverlay) return
    val ctx = LocalContext.current
    val view = LocalView.current
    val window = activityOf(ctx)?.window ?: return
    DisposableEffect(window) {
        val listener = JankStats.OnFrameListener { frame ->
            FrameStats.frames.incrementAndGet()
            if (!frame.isJank) return@OnFrameListener
            FrameStats.janky.incrementAndGet()
            val ns = frame.frameDurationUiNanos
            FrameStats.worstNs.accumulateAndGet(ns) { a, b -> maxOf(a, b) }
            val states = frame.states.joinToString(" ") { "${it.key}=${it.value}" }
            val ms = ns / 100_000 / 10.0
            FrameStats.last = "$ms ms · $states"
            Log.w(TAG, "janky frame ${ms}ms $states")
        }
        val stats = runCatching { JankStats.createAndTrack(window, listener) }.getOrNull()
        onDispose { stats?.isTrackingEnabled = false }
    }
    // What was on screen, attached to every frame JankStats reports.
    LaunchedEffect(view) {
        val holder = PerformanceMetricsState.getHolderForHierarchy(view)
        snapshotFlow {
            listOf(
                "route" to Nav.route.key,
                "popup" to when {
                    StartMenu.open -> "start"
                    Nav.menuFor != null -> "gamemenu"
                    Nav.search -> "search"
                    FolderPopup.route != null -> "folder"
                    Nav.overlayOpen -> "other"
                    else -> "none"
                },
                "swapped" to ScreenRoles.libraryOnBottom.toString(),
            )
        }.collect { pairs -> holder.state?.let { s -> pairs.forEach { (k, v) -> s.putState(k, v) } } }
    }
    // A summary in the log every 10 seconds, so a whole session can be read back.
    LaunchedEffect(Unit) {
        while (true) {
            delay(10_000)
            val f = FrameStats.frames.get()
            if (f > 0) Log.i(TAG, "summary: ${FrameStats.janky.get()} janky of $f frames (${pct1(FrameStats.janky.get(), f)}), worst ${FrameStats.worstNs.get() / 1_000_000} ms")
        }
    }
    val text by produceState("") {
        while (true) {
            val f = FrameStats.frames.get()
            val j = FrameStats.janky.get()
            value = "$f frames · $j slow (${pct1(j, f)}) · worst ${FrameStats.worstNs.get() / 1_000_000} ms" +
                FrameStats.last.takeIf { it.isNotEmpty() }?.let { "\nlast slow: $it" }.orEmpty()
            delay(500)
        }
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomStart) {
        Text(
            text, Modifier.padding(8.dp).clip(RoundedCornerShape(10.dp)).background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall, color = Color.White, maxLines = 2,
        )
    }
}

private const val TAG = "TandemJank"

private fun pct1(n: Int, of: Int): String = if (of == 0) "0%" else "${Math.round(n * 1000.0 / of) / 10.0}%"
