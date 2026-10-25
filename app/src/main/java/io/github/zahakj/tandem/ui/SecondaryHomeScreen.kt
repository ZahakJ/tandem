package io.github.zahakj.tandem.ui

import android.os.SystemClock
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import io.github.zahakj.tandem.GameCompanion
import io.github.zahakj.tandem.LibraryGuest
import io.github.zahakj.tandem.RunningGames
import io.github.zahakj.tandem.SecondaryHome
import io.github.zahakj.tandem.SecondaryHomeActivity
import io.github.zahakj.tandem.data.Store
import io.github.zahakj.tandem.data.SystemPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Calendar
import java.util.Date

/*
 * Tandem's bottom-screen Home (SecondaryHome.kt): what the bottom screen shows when nothing else is there.
 *
 * Calm and cheap, like the game companion: a still picture (the bottom wallpaper, never a playing video or a moving
 * backdrop), a clock that changes once a minute, the battery once a minute, and Pip without its bob. After a minute
 * without a touch it dims and only the minute clock and a burn-in drift of a pixel or two every 40 seconds remain; while dimmed,
 * the first touch only wakes it. Underneath Tandem's own bottom screen (a presentation, which covers it) it draws
 * nothing but the background.
 */

private const val DIM_AFTER_MS = 60_000L

@Composable
fun SecondaryHomeScreen(activity: SecondaryHomeActivity) {
    // Just started with Tandem's process (a boot or a cold start, when the library's own bottom screen comes a moment
    // later): only the bottom wallpaper, still, exactly what that screen opens on, so its arrival follows without a
    // flash of this screen's clock and buttons. After a few seconds the face shows if nothing covered it.
    val booting by produceState(StartFlow.processAgeMs() < BOOT_QUIET_MS) {
        if (!value) return@produceState
        delay((BOOT_QUIET_MS - StartFlow.processAgeMs()).coerceAtLeast(0))
        value = false
    }
    if (booting) {
        StillWallpaper(dimmed = false)
        return
    }
    // Tandem's own bottom screen is showing on top of this one: nothing to draw under it.
    if (GameCompanion.tandemBelow) {
        Box(Modifier.fillMaxSize().background(Palette.Night))
        return
    }
    // An app has the top screen: what was up there, Tandem's library, is down here (LibraryGuest.kt).
    val main = LibraryGuest.idleMain?.get()
    if (main != null && LibraryGuest.wanted(SecondaryHome.front, mainIdle = !main.isDestroyed, activity.defaultHome, SystemPrefs.data.singleScreen)) {
        GuestLibrary(activity, main)
        return
    }
    val resumed = rememberResumed()
    var wakeAt by remember { mutableLongStateOf(SystemClock.uptimeMillis()) }
    var dim by remember { mutableStateOf(false) }
    LaunchedEffect(wakeAt, resumed) {
        dim = false
        if (!resumed) return@LaunchedEffect
        delay(DIM_AFTER_MS)
        dim = true
    }
    val dimLevel = animateFloatAsState(if (dim) 0.3f else 1f, tween(if (dim) 2400 else 300), label = "bottom-home-dim")
    val shift = rememberBurnInShift(dim && resumed)
    Box(
        Modifier.fillMaxSize()
            .background(Palette.Night)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val wasDim = dim
                    wakeAt = SystemClock.uptimeMillis()
                    if (wasDim) {
                        // Dimmed: this touch only wakes the screen (and looks at what's in front again).
                        SecondaryHome.lookAtFront(activity)
                        down.consume()
                        do {
                            val e = awaitPointerEvent(PointerEventPass.Initial)
                            e.changes.forEach { it.consume() }
                        } while (e.changes.any { it.pressed })
                    }
                }
            },
    ) {
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                alpha = dimLevel.value
                translationX = shift.value.x
                translationY = shift.value.y
            },
        ) {
            StillWallpaper()
            Face(activity, dim, resumed)
        }
    }
}

/** The bottom screen's wallpaper as a still picture: a video's still frame, a backdrop that doesn't move. */
@Composable
private fun StillWallpaper(dimmed: Boolean = true) {
    val look = Store.settings.look
    val theme = Themes.live
    val key = remember(look.wallBottom, theme) { wallpaperKey(ScreenSlot.Bottom, look, theme) }
    Box(Modifier.fillMaxSize()) {
        when {
            key.startsWith("file:") && isVideoKey(key) -> WallpaperStill(File(key.removePrefix("file:")), soft = false)
            key.startsWith("file:") -> AsyncImage(File(key.removePrefix("file:")), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            // Undimmed (startup), the tint the library's bottom screen opens with.
            else -> Backdrop(BackdropStyle.of(key) ?: theme.backdrop, if (dimmed) Palette.Accent else Palette.Lavender, moving = false)
        }
        // Darkened so the clock and the panel stay readable on any picture.
        if (dimmed) Box(Modifier.fillMaxSize().background(Palette.Night.copy(alpha = (look.wallDim + 0.25f).coerceIn(0.3f, 0.85f))))
    }
}

/** How long after Tandem's process starts this screen shows only its wallpaper. */
private const val BOOT_QUIET_MS = 4_000L

/** Now, once a minute (on the minute), still while the screen isn't showing. */
@Composable
private fun rememberMinute(resumed: Boolean) = produceState(System.currentTimeMillis(), resumed) {
    value = System.currentTimeMillis()
    if (!resumed) return@produceState
    while (true) {
        delay(60_000L - System.currentTimeMillis() % 60_000L + 50)
        value = System.currentTimeMillis()
    }
}

@Composable
private fun Face(activity: SecondaryHomeActivity, dim: Boolean, resumed: Boolean) {
    val now by rememberMinute(resumed)
    val battery by produceState(-1 to false, resumed) {
        if (!resumed) return@produceState
        while (true) {
            value = withContext(Dispatchers.IO) { runCatching { SystemControls.battery(activity) }.getOrDefault(-1 to false) }
            delay(60_000)
        }
    }
    val app = (SecondaryHome.front as? SecondaryHome.Front.App)?.pkg
    Column(Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 20.dp)) {
        // Clock and battery, like the companion.
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(Clock.always(Date(now)), style = MaterialTheme.typography.displaySmall.copy(fontSize = 40.sp, lineHeight = 44.sp), maxLines = 1)
                Text(
                    android.text.format.DateFormat.format("EEEE, d MMMM", Date(now)).toString(),
                    style = MaterialTheme.typography.labelLarge, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            val (level, charging) = battery
            if (level >= 0) Pill((if (charging) "Charging " else "") + "$level%", if (level <= 20 && !charging) Palette.Peach else Palette.Mint)
        }
        Spacer(Modifier.weight(1f))
        if (app != null) AppPanel(activity, app, dim) else Hello(now, dim)
        Spacer(Modifier.weight(1f))
        // Hidden while dimmed, so a touch to wake never lands on one.
        Row(Modifier.fillMaxWidth().graphicsLayer { alpha = if (dim) 0f else 1f }, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // Never closes the app on top: it moves down here (Shizuku) or stays open behind Tandem.
            Chip("Back to Tandem", Palette.Mint, enabled = !dim) {
                Thread({ io.github.zahakj.tandem.AppMove.tandemOnTop(activity, app) }, "tandem-back").start()
            }
            val stock = remember { SecondaryHome.stockBottom(activity) != null }
            if (stock) Chip("Android apps", Palette.Sky, enabled = !dim) { SecondaryHome.openStockBottom(activity) }
        }
    }
}

/** An app is in front on the top screen: its icon and name, and a big button that moves it down here. */
@Composable
private fun AppPanel(activity: SecondaryHomeActivity, pkg: String, dim: Boolean) {
    val label = remember(pkg) { RunningGames.appLabel(activity, pkg) }
    var moving by remember(pkg) { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Palette.Card.copy(alpha = 0.82f)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            AppIcon(pkg, Modifier.size(72.dp).clip(RoundedCornerShape(18.dp)))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(label, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("Open on the top screen", style = MaterialTheme.typography.titleSmall, color = Palette.Sky, maxLines = 1)
                Text("Home brings Tandem back", style = MaterialTheme.typography.labelLarge, color = Palette.Muted, maxLines = 1)
            }
        }
        // Moves the app down here (Shizuku), and it opens here from then on.
        Row(Modifier.fillMaxWidth().graphicsLayer { alpha = if (dim) 0f else 1f }) {
            Chip(if (moving) "Moving…" else "Move $label here", Palette.Butter, enabled = !dim && !moving) {
                moving = true
                Thread({ io.github.zahakj.tandem.AppMove.moveToBottom(activity, pkg); activity.runOnUiThread { moving = false } }, "tandem-move-app").start()
            }
        }
    }
}

/** Nothing (or a game) on top: Pip and a hello, the same as Tandem's details greeting. */
@Composable
private fun Hello(now: Long, dim: Boolean) {
    val hour = remember(now) { Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.HOUR_OF_DAY) }
    val hello = when (hour) { in 5..11 -> "Good morning!"; in 12..17 -> "Good afternoon!"; in 18..22 -> "Good evening!"; else -> "Up late? Me too." }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        // Pip rests while dimmed (no blinks either); a still box keeps the layout from jumping.
        if (dim) Spacer(Modifier.size(120.dp)) else Pip(120.dp, mood = if (hour in 0..5) 2 else 1, bounce = false)
        Spacer(Modifier.height(14.dp))
        Text(hello, style = MaterialTheme.typography.titleLarge, color = Palette.Peach, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.Chip(text: String, color: Color, enabled: Boolean, onTap: () -> Unit) {
    Box(
        Modifier.weight(1f).widthIn(min = 120.dp).height(52.dp).clip(RoundedCornerShape(18.dp))
            .background(color.copy(alpha = 0.2f)).clickable(enabled = enabled, onClick = onTap),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.titleSmall, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 8.dp))
    }
}

/**
 * Tandem's library, borrowed from the stopped library activity [main] while an app has the top screen
 * (LibraryGuest.kt): the same screens and state, with [main] as their context (so starting things, menus and the
 * buttons work as on the top screen) and this screen's lifecycle (so it stops moving while covered or off). Back
 * goes to this activity's own dispatcher, where the library's handlers register here.
 */
@Composable
private fun GuestLibrary(activity: SecondaryHomeActivity, main: io.github.zahakj.tandem.MainActivity) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    androidx.compose.runtime.DisposableEffect(activity) {
        // This composition's frame clock: button presses go only to collectors running on it (Buttons.events).
        LibraryGuest.attach(activity, scope.coroutineContext[androidx.compose.runtime.MonotonicFrameClock])
        onDispose { LibraryGuest.detach(activity) }
    }
    LaunchedEffect(Unit) {
        // Already holding the controller (the user touched down here first): the library takes the focus now.
        if (activity.hasWindowFocus()) activity.takeLibraryFocus()
    }
    SwapStage(ScreenSlot.Bottom) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.ui.platform.LocalContext provides main,
            LocalLibraryGuest provides true,
        ) { LibraryRoot(main) }
    }
}
