package io.github.zahakj.tandem.ui

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.LruCache
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.repeatOnLifecycle
import io.github.zahakj.tandem.Button
import io.github.zahakj.tandem.Buttons
import io.github.zahakj.tandem.Chords
import io.github.zahakj.tandem.Idle
import io.github.zahakj.tandem.MainActivity
import io.github.zahakj.tandem.RetroAchievements
import io.github.zahakj.tandem.RunningGames
import io.github.zahakj.tandem.Screensaver
import io.github.zahakj.tandem.data.HomeStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference

/** The top-screen activity, for things on the bottom screen that start other apps (they must start from here). */
object Host {
    private var ref: WeakReference<Activity>? = null
    var activity: Activity?
        get() = ref?.get()
        set(value) { ref = value?.let(::WeakReference) }

    /**
     * Home pressed while Tandem is the Home app (or Tandem's launcher icon tapped while it runs): back to the home
     * screen with every popup closed. [returning] is true when Tandem was in the background (a game was in front):
     * then it just comes back (landing on the Resume page when a game runs). Pressed again while already home with
     * nothing open, it goes back to the start of the home screen, like a launcher's second Home press.
     */
    fun onNewIntent(intent: Intent, returning: Boolean = false) {
        val home = intent.hasCategory(Intent.CATEGORY_HOME) || intent.hasCategory(Intent.CATEGORY_LAUNCHER)
        if (!home) return
        val alreadyHome = !returning && Nav.route == Route.Home && !Nav.overlayOpen && Layers.top == null &&
            FolderPopup.route == null && IconGrid.openFolder == null && IconGrid.menu == null && !IconGrid.adding && IconGrid.moving == null
        Screensaver.wake()
        TextEntry.request = null
        Confirm.request = null
        GameEditor.close()
        Nav.menuFor = null; Nav.groupMenu = false; Nav.search = false
        Panels.switcher = false; Panels.quick = false; Panels.apps = false; Panels.appMenu = null; Panels.homeExplainer = false
        AppActions.iconFor = null
        WidgetUi.close(); WidgetUi.moving = null
        IconGrid.menu = null; IconGrid.choosingFolder = false; IconGrid.openFolder = null; IconGrid.adding = false; IconGrid.moving = null; IconGrid.selecting = null
        FolderPopup.close()
        ActionMenu.dismiss()
        if (PadTest.active) PadTest.active = false
        while (Nav.back()) Unit
        Nav.replace(Route.Home)
        if (alreadyHome) {
            // Second Home: back to the start (the Resume page or the first tile), the strip folded away.
            HomeExtras.recentOpen = false
            FocusMemory.forget(Route.Home.key)
            ScrollMemory.put("home:icons:${io.github.zahakj.tandem.data.Store.settings.look.gridScroll}", 0, 0)
            Sounds.back()
        }
        Nav.focusTick++
    }
}

/**
 * Everything this feature area adds on top of the screens: popups, the screensaver, button handling and the
 * checks for running games. Polling only happens while Tandem is resumed; it stops completely while a game is in
 * front, so it never costs the game anything.
 */
@Composable
fun HomeHost() {
    val ctx = LocalContext.current
    val activity = ctx as ComponentActivity
    Host.activity = activity

    // Running games: checked when Tandem comes back, then gently while it stays in front.
    LaunchedEffect(activity) {
        activity.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            RetroAchievements.invalidate()
            // Not in the first frames back from a game: the answer changes what the home screen shows, and the
            // Shizuku call is better made once Tandem has drawn and settled.
            delay(450)
            var first = true
            while (true) {
                withContext(Dispatchers.IO) { RunningGames.refresh(ctx, maxAgeMs = if (first) 0 else 4000) }
                first = false
                delay(if (Panels.switcher) 3_000 else 12_000)
            }
        }
    }
    // Screensaver timer: a cheap check every few seconds, only while resumed.
    LaunchedEffect(activity) {
        activity.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            Screensaver.wake()
            while (true) {
                delay(5_000)
                val minutes = HomeStore.settings.screensaverMinutes
                if (minutes > 0 && !Screensaver.active && android.os.SystemClock.elapsedRealtime() - Idle.last > minutes * 60_000L) Screensaver.start()
            }
        }
    }
    DisposableEffect(activity) {
        val observer = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_PAUSE) Chords.reset() }
        activity.lifecycle.addObserver(observer)
        onDispose { activity.lifecycle.removeObserver(observer) }
    }
    // This area's popups own the buttons while open, so the screen underneath never acts on the same press (Select
    // closing the switcher must not also start a surprise, Start closing the quick panel must not open Settings).
    InterceptButtons(Panels.switcher && TextEntry.request == null) { b ->
        when (b) {
            Button.X -> switcherCloseFocused(ctx)
            Button.Y -> switcherCloseAll(ctx)
            Button.Select -> Panels.close()
            else -> Unit
        }
        true
    }
    InterceptButtons(Panels.quick && TextEntry.request == null) { b ->
        if (b == Button.Start) Panels.close()
        true
    }
    // The drawer: Y opens the app menu over it, X searches.
    InterceptButtons(Panels.apps && Panels.appMenu == null && AppActions.iconFor == null && TextEntry.request == null) { b ->
        when (b) {
            Button.Y -> Panels.focusedApp?.let { Panels.openAppMenu(it) }
            Button.X -> AppsDrawerState.search()
            else -> Unit
        }
        true
    }
    InterceptButtons((Panels.appMenu != null || Panels.homeExplainer || AppActions.iconFor != null) && TextEntry.request == null) { true }
    LaunchedEffect(Unit) {
        Buttons.events.collect { b ->
            if (Nav.overlayOpen) return@collect
            when {
                Nav.route == Route.Stats && (b == Button.L1 || b == Button.R1) -> { StatsTabs.shift(if (b == Button.R1) 1 else -1); Sounds.page() }
                Nav.route == Route.Home && b == Button.Y -> Panels.focusedApp?.let { Panels.openAppMenu(it) }
            }
        }
    }

    GameSwitcher()
    QuickPanel()
    AppsDrawer()
    AppActionMenu()
    AppIconChooser()
    HomeExplainer()
    WidgetHost()
    ScreensaverOverlay()
}

/**
 * When Tandem is the Home app, Back on the home screen has nowhere to go and must not close the launcher. Composed
 * first so every other Back handler wins over it.
 */
@Composable
fun HomeBackGuard() {
    val ctx = LocalContext.current
    val resumes = (ctx as? MainActivity)?.resumeCount?.intValue ?: 0
    val isHome = androidx.compose.runtime.remember(resumes) { HomeMode.isDefault(ctx) }
    BackHandler(enabled = isHome) { }
}

/** The rounded card popups are drawn on. Taps on it don't fall through and close the popup. */
@Composable
fun PanelSurface(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.trapFocus().clip(RoundedCornerShape(26.dp)).background(Palette.Card).swallowTaps().padding(18.dp),
        content = content,
    )
}

/** Pip with a short message, for loading and error notes inside popups and screens. Empty pages use [EmptyNote]. */
@Composable
fun PipNote(title: String, detail: String, mood: Int = 0, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Pip(72.dp, mood = mood)
        Spacer(Modifier.height(8.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(detail, style = MaterialTheme.typography.bodyMedium, color = Palette.Muted, textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * An empty list, grid or page (UI-RULES.md): Pip, one line, and one button that does the obvious thing, so the
 * highlight always has somewhere to be. [focus] is the button's `takesFocus` on the screen's route.
 */
@Composable
fun EmptyNote(line: String, action: String, focus: Modifier, modifier: Modifier = Modifier, mood: Int = 2, color: Color = Palette.Mint, onAction: () -> Unit) {
    Column(modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Pip(72.dp, mood = mood)
        Spacer(Modifier.height(8.dp))
        Text(line, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(14.dp))
        PillButton(action, color, focus, onAction)
    }
}

/** Takes taps here, so they don't reach whatever is underneath; [onTap] runs for a tap. */
fun Modifier.swallowTaps(onTap: () -> Unit = {}): Modifier = pointerInput(onTap) { detectTapGestures { onTap() } }

/** A dark translucent backing, so light chips stay readable over bright artwork. */
fun Modifier.darkChip(): Modifier = clip(RoundedCornerShape(50)).background(Palette.NightDeep.copy(alpha = 0.78f))

/** App icons as bitmaps, cached. */
object AppIcons {
    private val cache = LruCache<String, ImageBitmap>(96)

    fun cached(pkg: String): ImageBitmap? = cache.get(pkg)

    /** Blocking; call off the main thread. */
    fun load(ctx: android.content.Context, pkg: String): ImageBitmap? {
        cache.get(pkg)?.let { return it }
        val drawable = runCatching { ctx.packageManager.getApplicationIcon(pkg) }.getOrNull() ?: return null
        val size = 144
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, size, size)
        drawable.draw(Canvas(bmp))
        return bmp.asImageBitmap().also { cache.put(pkg, it) }
    }
}

/** Runs blocking [work] (Shizuku, files) off the main thread, then [done] back on it. */
@OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
fun inBackground(work: () -> Unit, done: () -> Unit = {}) {
    kotlinx.coroutines.GlobalScope.launch(Dispatchers.Main) {
        withContext(Dispatchers.IO) { work() }
        done()
    }
}

@Composable
fun AppIcon(pkg: String, modifier: Modifier = Modifier) {
    // One of Tandem's own apps pinned to the dock ("tandem:settings"): its drawn icon, rounded like an app icon.
    Dock.builtIn(pkg)?.let { app ->
        Box(modifier.clip(RoundedCornerShape(22))) { TandemIconTile(app.icon, app.color) }
        return
    }
    // The user's own picture (Choose icon), rounded like an app icon.
    io.github.zahakj.tandem.data.AppPrefs.art(pkg)?.let { own ->
        Box(modifier.clip(RoundedCornerShape(22))) { CustomAppArt(own, 192, Modifier.matchParentSize()) { DrawnAppIcon(pkg, Modifier.matchParentSize()) } }
        return
    }
    DrawnAppIcon(pkg, modifier)
}

@Composable
private fun DrawnAppIcon(pkg: String, modifier: Modifier) {
    val ctx = LocalContext.current
    val icon by produceState(AppIcons.cached(pkg), pkg) { value = withContext(Dispatchers.IO) { AppIcons.load(ctx, pkg) } }
    Box(modifier) { icon?.let { Image(it, null, Modifier.matchParentSize()) } }
}
