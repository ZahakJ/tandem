package io.github.zahakj.tandem.ui

import android.app.Presentation
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.view.Display
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.activity.setViewTreeOnBackPressedDispatcherOwner
import coil.compose.AsyncImage
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.remember
import io.github.zahakj.tandem.Collections
import io.github.zahakj.tandem.Game
import io.github.zahakj.tandem.Privacy
import io.github.zahakj.tandem.Dates
import io.github.zahakj.tandem.DateKind
import io.github.zahakj.tandem.R
import io.github.zahakj.tandem.RunningGames
import io.github.zahakj.tandem.Tx
import io.github.zahakj.tandem.everPlayed
import io.github.zahakj.tandem.Launcher
import io.github.zahakj.tandem.Libraries
import io.github.zahakj.tandem.Selected
import io.github.zahakj.tandem.Selection
import io.github.zahakj.tandem.data.Store
import io.github.zahakj.tandem.data.playStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

/** Details for whatever is highlighted, shown on the second (bottom) screen. */
class BottomScreen(private val activity: ComponentActivity, display: Display) : Presentation(activity, display) {
    private var view: ComposeView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Never take input focus from the top screen: controller keys always arrive at the activity, which hands
        // them on here (forwardKey) while the library is on this screen.
        window?.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        // Behind the first frame: the theme's backdrop colour, never the dialog theme's grey.
        window?.let { io.github.zahakj.tandem.StartLook.paint(it, io.github.zahakj.tandem.StartLook.colorOf(Themes.live)) }
        setContentView(
            ComposeView(context).apply {
                setViewTreeLifecycleOwner(activity)
                setViewTreeSavedStateRegistryOwner(activity)
                setViewTreeOnBackPressedDispatcherOwner(activity)
                setContent { BottomRoot(activity, this@BottomScreen, this) }
                QuietSemantics.watch(this)
            }.also { view = it },
        )
        // No Android bars on Tandem's bottom screen either (they follow the focused window of the display, which
        // FocusGuard keeps Tandem's own).
        io.github.zahakj.tandem.SystemBars.hide(window)
    }

    override fun onStart() {
        super.onStart()
        io.github.zahakj.tandem.SystemBars.hide(window)
        showing = this
        ScreenRoles.bottomShowing = true
    }

    override fun onStop() {
        guard?.let { handler.removeCallbacks(it) }
        guard = null
        parked = false
        // Showing the details again replaces this window with a new one, whose start can come before this stop.
        forget(this)
        super.onStop()
    }

    override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean {
        if (ev.actionMasked == android.view.MotionEvent.ACTION_DOWN) io.github.zahakj.tandem.LaunchFrom.touch(bottom = true)
        return io.github.zahakj.tandem.Idle.onTouch(ev) || super.dispatchTouchEvent(ev)
    }

    /**
     * Keys normally reach the activity, which hands them on here ([forwardKey]); this window never takes the focus.
     * But the AYN Thor hands controller input to the presentation of the app in front by itself, and does so while
     * Tandem's screensaver ([io.github.zahakj.tandem.TandemDream]) covers the top screen: then the key wakes it. A
     * key that arrives here any other time goes through the activity, so it is handled exactly as on the top screen
     * (A selects, Start and the screensaver work), never half-handled by this window alone.
     */
    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        // Hidden in the background: never a key's destination (a hidden window gets none anyway).
        if (parked) return false
        if (io.github.zahakj.tandem.Dreaming.onKey(event)) return true
        val main = activity as? io.github.zahakj.tandem.MainActivity
        if (main != null && main.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) return main.dispatchKeyEvent(event)
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(ev: android.view.MotionEvent): Boolean {
        if (parked) return false
        if (io.github.zahakj.tandem.Dreaming.onMotion(ev)) return true
        val main = activity as? io.github.zahakj.tandem.MainActivity
        if (main != null && main.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) return main.dispatchGenericMotionEvent(ev)
        return super.dispatchGenericMotionEvent(ev)
    }

    /** Hidden while Tandem is in the background ([park]); still the bottom screen Tandem shows when it comes back. */
    var parked = false
        private set

    /**
     * Tandem went to the background: the window is hidden instead of taken down, so coming back shows it again with
     * no new window and no new composition. Hidden means gone from the display: it draws nothing, can't be touched
     * and is no input target, so the controller and the screen belong to whatever runs there (a game, its
     * companion, the bottom-screen home). Its content stops moving with the activity's lifecycle.
     */
    fun park() {
        if (parked || !isShowing) return
        parked = true
        guard?.let { handler.removeCallbacks(it) }
        guard = null
        window?.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
        hide()
    }

    /** Shows the parked window again. False when there is nothing to show (it was taken down meanwhile). */
    fun unpark(): Boolean {
        if (!parked) return isShowing
        parked = false
        window?.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
        // On a hidden (not dismissed) dialog, show() only makes its window visible again.
        show()
        io.github.zahakj.tandem.SystemBars.hide(window)
        showing = this
        ScreenRoles.bottomShowing = true
        return isShowing
    }

    /**
     * Back after the screensaver (this screen stayed up under it): the library here takes the controls again at
     * once, and [ControlsGuard] checks a moment later that it really holds them.
     */
    fun afterDream() {
        takeControls()
        if (ScreenRoles.libraryOnBottom) watchControls()
    }

    /**
     * A key from the controller (already remapped: A is select) for the library drawn here. True when it was used.
     * With the library here and nothing on this screen holding the focus (so nothing could use the key), it asks for
     * the focus and tries once more; if that finds nothing to focus either, [ControlsGuard] puts things right within
     * a second instead of leaving every button but X dead.
     */
    fun forwardKey(event: android.view.KeyEvent): Boolean {
        val v = view ?: return false
        if (v.dispatchKeyEvent(event)) return true
        if (event.action != android.view.KeyEvent.ACTION_DOWN || !ScreenRoles.libraryOnBottom || v.hasFocus()) return false
        if (v.requestFocus()) {
            Nav.focusTick++
            return v.dispatchKeyEvent(event)
        }
        if (event.repeatCount == 0) watchControls()
        return false
    }

    /** Home, or back with the library here: make sure it holds the controls (and its remembered item the focus). */
    fun takeControls() {
        view?.let { if (!it.hasFocus()) it.requestFocus() }
        Nav.focusTick++
    }

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var guard: Runnable? = null

    /** Checks once, [ControlsGuard.GRACE_MS] from now, that the library here can take the controls; fixes it if not. */
    internal fun watchControls() {
        if (guard != null) return
        Nav.focusTick++
        val check = Runnable {
            guard = null
            val v = view
            val fix = ControlsGuard.decide(
                libraryOnBottom = ScreenRoles.libraryOnBottom && showing === this@BottomScreen,
                focused = v?.hasFocus() == true || v?.requestFocus() == true,
                libraryLoaded = Libraries.all != null,
                scanning = LibraryScan.scanning,
                onHome = Nav.route == Route.Home,
            )
            if (fix != ControlsGuard.Fix.None) android.util.Log.w("Tandem", "Bottom screen without the controls: $fix")
            when (fix) {
                ControlsGuard.Fix.None, ControlsGuard.Fix.Wait -> Unit
                ControlsGuard.Fix.Load -> LibraryScan.ensureLoaded(activity)
                ControlsGuard.Fix.SwapBack -> ScreenRoles.swapFailed()
            }
        }
        guard = check
        handler.postDelayed(check, ControlsGuard.GRACE_MS)
    }

    companion object {
        /** The bottom screen on show right now. */
        private var showing: BottomScreen? = null

        private fun forget(screen: BottomScreen) {
            if (showing === screen) {
                showing = null
                ScreenRoles.bottomShowing = false
            }
        }

        /** Shows the bottom screen on the first secondary display, if the device has one. */
        fun showOn(activity: ComponentActivity): BottomScreen? {
            val dm = activity.getSystemService(DisplayManager::class.java)
            val display = dm.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION).firstOrNull() ?: return null
            val screen = runCatching { BottomScreen(activity, display) }.getOrNull() ?: return null
            return try {
                screen.show()
                screen
            } catch (t: Throwable) {
                // Dialog.show() runs onStart (which marks the bottom screen as showing) before adding the window, so a
                // window Android refused would leave the library "on the bottom screen" with no bottom screen: the top
                // screen showed the details and nothing anywhere took the controls.
                android.util.Log.w("Tandem", "Couldn't show the bottom screen", t)
                forget(screen)
                null
            }
        }
    }
}

/**
 * The bottom screen's content: the details, or (with the screens swapped) the whole library, given the activity as
 * its context so everything that starts apps, asks for results or handles Back works exactly as on the top screen.
 */
@Composable
private fun BottomRoot(activity: ComponentActivity, screen: BottomScreen, view: ComposeView) {
    val main = activity as? io.github.zahakj.tandem.MainActivity
    SwapStage(ScreenSlot.Bottom) { BottomStageContent(activity, main, screen, view) }
}

@Composable
private fun BottomStageContent(activity: ComponentActivity, main: io.github.zahakj.tandem.MainActivity?, screen: BottomScreen, view: ComposeView) {
    if (ScreenRoles.libraryOnBottom && main != null) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.ui.platform.LocalContext provides activity,
            androidx.activity.compose.LocalOnBackPressedDispatcherOwner provides activity,
            androidx.activity.compose.LocalActivityResultRegistryOwner provides activity,
        ) { LibraryRoot(main) }
        androidx.compose.runtime.LaunchedEffect(Unit) {
            view.requestFocus()
            // The startup splash has nothing to focus, so the check below waits for the library. Something must be
            // loading it: on a cold start the first scan may have belonged to the top screen's library, which this
            // screen just replaced (see ScanRunner).
            LibraryScan.ensureLoaded(activity)
            val libraries = androidx.compose.runtime.snapshotFlow { Libraries.all }
            if (kotlinx.coroutines.withTimeoutOrNull(4000) { libraries.first { it != null } } == null) {
                LibraryScan.ensureLoaded(activity)
                libraries.first { it != null }
            }
            // If the controls never arrive here (the device won't focus a view on this screen), swap back.
            delay(1500)
            if (ScreenRoles.libraryOnBottom && !view.hasFocus()) screen.watchControls()
        }
    } else {
        TandemTheme { BottomContent() }
    }
}


/** Everything on the details screen. Public so the swapped-screens mode can show it on the other display. */
@Composable
fun BottomContent() {
    // A game that was hidden (or rescanned away) while highlighted is never left on show.
    val selected = when (val s = Selection.current) {
        is Selected.GameItem -> if (Libraries.of(s.game) == null || !Privacy.canShow(s.game)) Selected.Home else s
        else -> s
    }
    val mode = Store.settings.bottomMode
    // How far down Spotlight's status pill reaches (top right), so the now-playing banner drops below it.
    var pillClear by remember { mutableStateOf(0.dp) }
    val spotlight = mode != "clock" && Spotlight.wanted(selected)
    // The wallpaper is the screen's own, drawn by its SwapStage. The details sit above a running scrape's card,
    // never under it.
    // Trying layouts the details step back: their button pill (A Open, L1 R1 Groups) would say the wrong thing.
    Box(Modifier.fillMaxSize().clearOfScrapeCard().awayWhileTrying()) { Details(selected, mode, spotlight) { pillClear = it } }
    // Trying layouts (Start › Layout): a card of the one on the other screen, so both screens say the same, and the
    // try-on's buttons where the details' button pill was.
    androidx.compose.animation.AnimatedVisibility(
        LayoutSwitch.open, Modifier.fillMaxSize(),
        enter = fadeIn(Motion.fade(180)) + androidx.compose.animation.scaleIn(Motion.move(), initialScale = 0.9f), exit = fadeOut(Motion.fade(140)),
    ) {
        Box(Modifier.fillMaxSize().background(Palette.NightDeep.copy(alpha = 0.35f)), contentAlignment = Alignment.Center) {
            LayoutTryOnCard(Modifier.padding(24.dp))
            TryOnHints(Modifier.align(Alignment.BottomEnd).padding(14.dp))
        }
    }
    // What's new on the other screen: the deck's slides, the one on show lit, so both screens say the same.
    androidx.compose.animation.AnimatedVisibility(
        io.github.zahakj.tandem.ui.whatsnew.WhatsNew.open, Modifier.fillMaxSize(),
        enter = fadeIn(Motion.fade(180)) + androidx.compose.animation.scaleIn(Motion.move(), initialScale = 0.92f), exit = fadeOut(Motion.fade(140)),
    ) {
        Box(Modifier.fillMaxSize().background(Palette.NightDeep.copy(alpha = 0.6f)).swallowTaps(), contentAlignment = Alignment.Center) {
            io.github.zahakj.tandem.ui.whatsnew.WhatsNewCompanion(Modifier.padding(20.dp))
        }
    }
    // A running scrape's card, along the bottom edge (under the screensaver's dimming, which BottomExtras draws).
    ScrapeCard()
    BottomExtras(bannerTop = if (spotlight) pillClear else 0.dp)
    ThemeOverlay()
}

@Composable
private fun Details(selected: Selected, mode: String, spotlight: Boolean, onPill: (androidx.compose.ui.unit.Dp) -> Unit) {
    if (LookQr.link != null) {
        // Share my look › Show QR: a code for a phone's camera.
        LookQrScreen()
    } else if (SmartFolderForm.folderId != null) {
        // The smart folder editor's live preview (SmartFolderForm.kt).
        SmartPreview()
    } else if (PlazaBottom.active) {
        // The Plaza's highlighted visitor, or your own card while Me is open (PlazaScreen.kt).
        PlazaBottomView()
    } else if (mode == "clock") {
        ClockFace()
    } else AnimatedContent(
        spotlight to selected, contentKey = { it.first }, label = "bottom-kind",
        transitionSpec = { fadeIn(Motion.fade(220)) togetherWith fadeOut(Motion.fade(160)) },
    ) { (big, s) ->
        // Systems, folders, collections and apps (and on the icon grid, games too) in Spotlight's full-screen look;
        // moving between a game and a system cross-fades from one look to the other, each keeping what it showed.
        if (big) SpotlightView(s, onPill) else Box(Modifier.fillMaxSize()) { LayoutDetails(s, mode) }
    }
}

/** A game (and anything Spotlight doesn't show) in the details of the layouts other than the icon grid. */
@Composable
private fun LayoutDetails(selected: Selected, mode: String) {
    run {
        SpotlightGround(mode != "art" && mode != "both" && selected is Selected.GameItem)
        AnimatedContent(selected, transitionSpec = { fadeIn(Motion.fade(260)) togetherWith fadeOut(Motion.fade(200)) }, label = "bottom") { s ->
            when (s) {
                is Selected.GameItem -> GameDetails(s.game, mode)
                is Selected.Group -> GroupDetails(s.title, s.subtitle, s.games, s.color, s.target != null)
                is Selected.System -> {
                    val games = remember(s.library, Store.meta, Privacy.view) { Collections.visible(s.library.games) }
                    SystemDetails(s.library.platform, games)
                }
                is Selected.Setting -> SettingDetails(s)
                is Selected.App -> AppDetails(s.pkg, chrome = true)
                // Inside Settings the screen is a calm companion (its wallpaper stills and softens, Backdrops.kt): an
                // open page's summary, or Pip's greeting over all of it, never widget cards floating over half of it.
                Selected.Home -> if (Nav.inSettings) SettingsIdle.page?.let { SettingDetails(it) } ?: Greeting() else BottomWidgets { Greeting() }
            }
        }
    }
}

/**
 * The theme's background over the wallpaper while [on] (a game's details), outside the details'
 * cross-fade, so moving between games never lets the wallpaper show through between two pictures.
 */
@Composable
private fun SpotlightGround(on: Boolean) {
    WallpaperCover.Cover(LocalScreenSlot.current, on)
    val a = androidx.compose.animation.core.animateFloatAsState(if (on) 1f else 0f, Motion.fade(260), label = "spotlight-ground")
    val ground = Palette.Night
    Spacer(Modifier.fillMaxSize().drawBehind { val v = a.value; if (v > 0.001f) drawRect(ground, alpha = v) })
}

@Composable
private fun GameDetails(game: Game, mode: String) {
    // From the list of running games Tandem already keeps, so moving between games never asks Shizuku.
    val resumable = RunningGames.list.any { it.game?.key == game.key }
    when (mode) {
        "art" -> ArtOnly(game, resumable)
        "both" -> ArtWithInfo(game, resumable) {
            AnimatedLogo(game, maxWidth = 320.dp, maxHeight = 76.dp, modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 4.dp), textAlign = TextAlign.Center)
            GameInfo(game, resumable, descriptionLines = 3, pinHints = false)
        }
        else -> ArtDetails(game, resumable)
    }
}

/**
 * A game's details, in every home layout: the game alone, never half game and half wallpaper. Its art fills the
 * screen and fades, down to where its words begin, into the theme's background (which [Details] lays behind it), so
 * the logo rests on the art and the info under it reads over any picture and any wallpaper.
 */
@Composable
private fun ArtDetails(game: Game, resumable: Boolean) {
    val look = Store.settings.look
    // Where the info starts (px from the top), so the fade ends just there whatever the info's height.
    var infoTop by remember(game.key) { mutableFloatStateOf(-1f) }
    var artTop by remember { mutableFloatStateOf(0f) }
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().onPlaced { artTop = it.positionInRoot().y }.spotlightDetailsFade { infoTop - artTop }) {
            SpotlightArt(game, if (look.spotlightBackdrop == "art") "art" else "blur", look.spotlightMotion)
        }
        if (resumable) Pill(Tx.s(R.string.screens_running), Palette.Mint, Modifier.align(Alignment.TopEnd).padding(16.dp).darkChip())
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 26.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            AnimatedLogo(game, maxWidth = 340.dp, maxHeight = (96 * Hero.logoScale / 0.7f).dp, modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 6.dp), textAlign = TextAlign.Center)
            Column(
                Modifier.fillMaxWidth().onPlaced { infoTop = it.positionInRoot().y },
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) { GameInfo(game, resumable, descriptionLines = 3, pinHints = false, onArt = true) }
        }
    }
}

/**
 * The art above the info: whole down to a little over the logo, then fading so that from where the info begins
 * ([infoTop], px; until it is known, 55 % down) only about a tenth of it is left over the theme's background.
 */
private fun Modifier.spotlightDetailsFade(infoTop: () -> Float): Modifier =
    graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }.drawWithCache {
        val f = (infoTop().takeIf { it > 0f } ?: (size.height * 0.55f)) / size.height
        val at = f.coerceIn(0.3f, 0.9f)
        val mask = Brush.verticalGradient(
            0f to Color.Black, (at - 0.3f) to Color.Black, (at - 0.12f) to Color.Black.copy(alpha = 0.45f),
            at to Color.Black.copy(alpha = 0.14f), 1f to Color.Black.copy(alpha = 0.1f),
        )
        onDrawWithContent {
            drawContent()
            drawRect(mask, blendMode = androidx.compose.ui.graphics.BlendMode.DstIn)
        }
    }

/** Pills, play time, description (or, without one, the box and a few facts) and button hints for a game. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.GameInfo(game: Game, resumable: Boolean, descriptionLines: Int, pinHints: Boolean = true, onArt: Boolean = false) {
    val meta = game.meta
    val color = Palette.forName(game.platform.id)
    // Over art (Spotlight), the chips get a dark ground and the words a shadow, as on the top screen.
    @Composable
    fun chip(text: String, c: Color) = if (onArt) Pill(text, Palette.readable(c), Modifier.darkChip()) else Pill(text, c)
    @Composable
    fun androidx.compose.ui.text.TextStyle.art() = if (onArt) overArt() else this
    // Wraps onto a second line instead of running off the edge.
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        meta.playStatus?.let { chip(it.chip, it.color) }
        chip(game.platform.shortName, color)
        // A multi-disc game shown as one tile (Discs.kt).
        if (game.discs.size > 1) chip(Tx.p(R.plurals.screens_discs, game.discs.size), Palette.Sky)
        meta.year?.let { chip("$it", Palette.Butter) }
        meta.genres.map(Collections::tidyGenre).distinctBy { it.lowercase() }.take(3).forEach { chip(it, Palette.Lavender) }
        meta.rating?.let { chip("★ ${"%.1f".format(io.github.zahakj.tandem.Lang.locale, it * 5)}", Palette.Peach) }
        // HowLongToBeat: the main story and 100%, when known.
        meta.timeToBeat?.takeIf { it.any }?.let { t ->
            val h = io.github.zahakj.tandem.data.TimeToBeat
            val main = listOf(t.main, t.extra).firstOrNull { it > 0 }
            chip(
                Tx.s(R.string.screens_ttb_chip, listOfNotNull(main?.let { h.hours(it) }, t.complete.takeIf { it > 0 }?.let { Tx.s(R.string.screens_ttb_complete, h.hours(it)) }).joinParts()),
                Palette.Sky,
            )
        }
        if (meta.favorite) chip(Tx.s(R.string.screens_favorite), Palette.Peach)
        GameExtrasBadges(game)
    }
    // On this week's board: why, with the facts behind it (larger while the board itself is on show).
    val onBoard = WeekBoardOn.pickOf(game) != null
    WeekReason(game, onArt, large = onBoard)
    creditsLine(meta)?.let {
        Text(it, style = MaterialTheme.typography.labelLarge.art(), color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    Text(playLine(meta), style = MaterialTheme.typography.titleSmall.art(), color = Palette.Mint, maxLines = 1, overflow = TextOverflow.Ellipsis)
    AchievementsStrip(game)
    val description = meta.description?.trim()?.takeIf { it.isNotEmpty() }
    if (description != null) {
        Text(
            description, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.bodyMedium.art(),
            color = if (onArt) Palette.Text else Palette.Muted,
            maxLines = descriptionLines, overflow = TextOverflow.Ellipsis,
        )
    } else if (pinHints) {
        // No description: the space below shows the game's box and a few facts instead of a big empty gap.
        GameFacts(game, Modifier.weight(1f))
    }
    if (pinHints && description != null) Spacer(Modifier.weight(1f)) else Spacer(Modifier.height(2.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ButtonHint("A", Tx.s(if (resumable) R.string.screens_hint_resume else R.string.screens_hint_play), Palette.Mint)
        // A game still open: Y closes it (twice), as on the icon grid's Resume card.
        if (resumable) ButtonHint("Y", Tx.s(R.string.common_close), Palette.Peach) else ButtonHint("Y", Tx.s(R.string.screens_hint_options), Palette.Butter)
        // On This week's board X re-rolls the row; on Spotlight X opens the whole story, unless the X button setting
        // gave it away.
        if (onBoard) ButtonHint("X", Tx.s(R.string.week_hint_reroll), Palette.Sky)
        else if (SpotlightMode.enabled && SpotlightMode.ownsX) ButtonHint("X", Tx.s(R.string.screens_hint_more), Palette.Sky) else ButtonHint("X", Tx.s(R.string.screens_hint_search), Palette.Sky)
        // The board as a popup: L1 / R1 do nothing there.
        if (!onBoard || !WeekBoardOn.popup) ListSwitchHint()
    }
}

/**
 * L1 / R1: what they turn to here. Inside a system, the systems either side by their short names ("L1 GBA  R1 PSX",
 * mirrored in right to left with the next one first); elsewhere what they step through: the systems, lists or genres,
 * on Home each layout's groups, the shelves, or the icon grid's zoom (GroupPaging.kt).
 */
@Composable
internal fun ListSwitchHint() {
    val route = Nav.route
    if (route is Route.System && route.folder == null) {
        val libs = Libraries.all
        val ids = remember(libs, Store.meta, Privacy.view, route.platformId) {
            libs.orEmpty().filter { Collections.visible(it.games).isNotEmpty() || it.platform.id == route.platformId }.map { it.platform.id }
        }
        val (before, after) = GroupPaging.around(ids, route.platformId)
        if (before != null && after != null) {
            fun short(id: String) = Libraries.byId(id)?.platform?.let { p -> p.shortName.ifBlank { p.name } }.orEmpty()
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                ButtonHint("L1", short(before), Palette.Lavender)
                ButtonHint("R1", short(after), Palette.Lavender)
            }
            return
        }
    }
    val label = when (val r = route) {
        is Route.System -> if (r.folder == null) R.string.screens_switch_systems else null
        is Route.Collection -> if (r.id.startsWith("genre:")) R.string.screens_switch_genres else R.string.screens_switch_lists
        Route.Home -> when (Store.settings.look.homeLayout) {
            "grid" -> R.string.screens_switch_zoom
            "shelves" -> R.string.screens_switch_rows
            else -> R.string.screens_switch_groups
        }
        else -> null
    }?.let { Tx.s(it) } ?: return
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        ButtonHint("L1", "", Palette.Lavender)
        ButtonHint("R1", label, Palette.Lavender)
    }
}

/** The box art (when the hero above shows something else) next to a few facts, centred in the space it has. */
@Composable
private fun GameFacts(game: Game, modifier: Modifier) {
    val lib = Libraries.of(game)
    val meta = game.meta
    val heroShown = lib?.let { it.hero(game) ?: it.screenshot(game) } != null
    val box = if (heroShown) lib?.box(game) else null
    val facts = buildList {
        add(Tx.s(R.string.screens_fact_times_played) to if (meta.playCount > 0) "${meta.playCount}" else Tx.s(R.string.screens_fact_not_yet))
        if (meta.playSeconds >= 60) add(Tx.s(R.string.screens_fact_play_time) to formatPlayLong(meta.playSeconds))
        meta.timeToBeat?.takeIf { it.any }?.let { t ->
            val h = io.github.zahakj.tandem.data.TimeToBeat
            val parts = listOfNotNull(
                t.main.takeIf { it > 0 }?.let { Tx.s(R.string.screens_ttb_main, h.hours(it)) },
                t.extra.takeIf { it > 0 }?.let { Tx.s(R.string.screens_ttb_extras, h.hours(it)) },
                t.complete.takeIf { it > 0 }?.let { Tx.s(R.string.screens_ttb_100, h.hours(it)) },
            )
            add(Tx.s(R.string.screens_fact_time_to_beat) to parts.joinParts())
        }
        if (meta.lastPlayed > 0) add(Tx.s(R.string.screens_fact_last_played) to relative(meta.lastPlayed))
        if (meta.firstSeen > 0) add(Tx.s(R.string.screens_fact_added) to Dates.text(meta.firstSeen, DateKind.Added))
        game.group?.let { add(Tx.s(R.string.screens_fact_folder) to it) }
        add(Tx.s(R.string.screens_fact_file) to game.file.name)
    }
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        if (box != null) {
            AsyncImage(
                box, game.title,
                Modifier.fillMaxHeight(0.9f).widthIn(max = 200.dp).aspectRatio(0.8f, matchHeightConstraintsFirst = true).shadow(10.dp, RoundedCornerShape(16.dp)).clip(RoundedCornerShape(16.dp)),
                contentScale = ContentScale.Fit,
            )
        }
        // Only as many facts as fit the space: on a short area the last ones are left out, never cut in half.
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            val fit = ((maxHeight + 8.dp) / 42.dp).toInt().coerceAtLeast(1)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((label, value) in facts.take(fit)) {
                    Column {
                        Text(label, style = MaterialTheme.typography.labelMedium, color = Palette.Muted, maxLines = 1)
                        Text(value, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

/** "Game Freak · Nintendo · 1–4 players" from whatever is known, on one quiet line. */
internal fun creditsLine(m: io.github.zahakj.tandem.data.GameMeta): String? {
    val players = m.players?.trim()?.replace('-', '–')?.let { p ->
        when {
            p == "1" -> Tx.s(R.string.screens_players_one)
            p.firstOrNull()?.isDigit() == true -> Tx.s(R.string.screens_players, p)
            else -> p
        }
    }
    val parts = listOfNotNull(m.developer, m.publisher?.takeIf { !it.equals(m.developer, ignoreCase = true) }, players)
    return parts.joinParts().ifEmpty { null }
}

/** "3 minutes ago", "Yesterday", or "just now". */
private fun relative(time: Long): String {
    val now = System.currentTimeMillis()
    return if (now - time < 60_000) Tx.s(R.string.screens_just_now) else Dates.relative(time, now)
}

/** "Played 1 min · 1 minute ago". Imported history (a time or a date with no count) counts as played. */
private fun playLine(m: io.github.zahakj.tandem.data.GameMeta): String {
    if (!m.everPlayed) return Tx.s(R.string.screens_not_played)
    val what = when {
        m.playSeconds > 0 -> Tx.s(R.string.screens_played_time, formatPlayLong(m.playSeconds))
        m.playCount > 0 -> Tx.p(R.plurals.screens_played_times, m.playCount)
        else -> Tx.s(R.string.screens_played)
    }
    return if (m.lastPlayed > 0) Tx.s(R.string.screens_played_when, what, relative(m.lastPlayed)) else what
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GroupDetails(title: String, subtitle: String, games: List<Game>, color: Color, hasMenu: Boolean, icon: (@Composable () -> Unit)? = null) {
    val covers = remember(games, Store.meta) { coversOf(games, 5) }
    val played = games.sumOf { it.meta.playSeconds }
    Column(Modifier.fillMaxSize().clearOfNowPlaying().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        // The fan takes what the text leaves (up to 190 dp), so a short screen shrinks it instead of cutting the hints.
        if (icon != null) icon() else if (covers.isEmpty()) Pip(130.dp, mood = 1) else CoverFan(covers, spread = 1.4f, modifier = Modifier.fillMaxWidth().heightIn(max = 190.dp).fillMaxHeight().weight(1f, fill = false).riseIn(title).floating(title.hashCode(), bob = 5.dp))
        Spacer(Modifier.height(22.dp))
        Text(title, Modifier.riseIn(title, 1, 16.dp), style = MaterialTheme.typography.displaySmall, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(subtitle, style = MaterialTheme.typography.titleMedium, color = color, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
        // Under a minute isn't worth a line.
        if (played >= 60) Text(Tx.s(R.string.screens_played_in_here, formatPlayLong(played)), style = MaterialTheme.typography.bodyMedium, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(18.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ButtonHint("A", Tx.s(R.string.screens_hint_open), color)
            if (hasMenu) ButtonHint("Y", Tx.s(R.string.screens_hint_options), Palette.Butter)
            ListSwitchHint()
        }
    }
}

/**
 * A highlighted system: the theme pack's hero and logo when it has them, otherwise the cover fan (or the system's
 * own icon for Android games and pack icons: never one app's icon standing in for a whole system).
 */
@Composable
private fun SystemDetails(platform: io.github.zahakj.tandem.Platform, games: List<Game>) {
    val color = Palette.forName(platform.id)
    val shown = SystemHeroDetails(platform, games, games.sumOf { it.meta.playSeconds }) {
        ButtonHint("A", Tx.s(R.string.screens_hint_open), color)
        ButtonHint("Y", Tx.s(R.string.screens_hint_options), Palette.Butter)
        ListSwitchHint()
    }
    if (!shown) {
        GroupDetails(
            platform.name, Tx.p(R.plurals.screens_games, games.size), games, color, true,
            icon = if (platform.isApps || ThemePacks.systemIcon(platform.id) != null) ({ SystemIcon(platform, 150.dp) }) else null,
        )
    }
}

/** A setting highlighted on the top screen, explained. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingDetails(s: Selected.Setting) {
    Column(Modifier.fillMaxSize().clearOfNowPlaying().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Pip(110.dp, mood = 0)
        Spacer(Modifier.height(16.dp))
        Text(s.title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(6.dp))
        if (s.value.isNotBlank()) Pill(s.value, s.color)
        if (s.detail != null) {
            Spacer(Modifier.height(14.dp))
            // A long explanation is cut short with an ellipsis rather than pushing the hints off the screen.
            Text(s.detail, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.bodyLarge, color = Palette.Muted, textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(18.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ButtonHint("A", s.action, s.color)
            ButtonHint("B", Tx.s(R.string.common_back), Palette.Lavender)
            ButtonHint("Start", Tx.s(R.string.common_close), Palette.Sky)
        }
    }
}

@Composable
private fun Greeting() {
    val now by rememberMinute()
    val hour = Calendar.getInstance().apply { time = now }.get(Calendar.HOUR_OF_DAY)
    val hello = Tx.s(
        when (hour) { in 5..11 -> R.string.screens_good_morning; in 12..17 -> R.string.screens_good_afternoon; in 18..22 -> R.string.screens_good_evening; else -> R.string.screens_up_late },
    )
    val libs = Libraries.all
    val showHidden = Privacy.view
    // Only games that may be shown are counted, and hidden ones are never "last played" here.
    val (total, last) = remember(libs, Store.meta, showHidden) { Collections.visibleGames().size to Collections.continuePlaying().firstOrNull() }
    // The game running now is on the banner above already, so it isn't offered twice.
    val running = nowPlaying()?.game?.key
    // Below the now-playing banner and sized to the room left (the landscape screen with a game running has well
    // under 400 dp): Pip shrinks and the last-played card goes before anything is cut off.
    BoxWithConstraints(Modifier.fillMaxSize().clearOfNowPlaying().padding(horizontal = 24.dp, vertical = 16.dp)) {
        val pip = (maxHeight * 0.32f).coerceIn(64.dp, 150.dp)
        val shownLast = last?.takeIf { it.key != running && maxHeight >= 380.dp }
        // A touch above the middle: with Pip on top, the true centre looks low.
        Column(Modifier.fillMaxSize().padding(bottom = maxHeight * 0.05f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Pip(pip, mood = if (hour in 0..5) 2 else 1)
            Spacer(Modifier.height(16.dp))
            Text(Clock.always(now), style = MaterialTheme.typography.displaySmall, maxLines = 1)
            Text(hello, style = MaterialTheme.typography.titleLarge, color = Palette.Peach, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(if (total == 0) Tx.s(R.string.screens_no_games) else Tx.p(R.plurals.screens_games_ready, total), style = MaterialTheme.typography.bodyMedium, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (shownLast != null) {
                Spacer(Modifier.height(18.dp))
                Row(
                    Modifier.clip(RoundedCornerShape(16.dp)).background(Palette.Card).padding(10.dp).widthIn(max = 360.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(54.dp).shadow(4.dp, RoundedCornerShape(10.dp)).clip(RoundedCornerShape(10.dp))) { GameArt(shownLast) }
                    Column(Modifier.padding(start = 10.dp)) {
                        Text(Tx.s(R.string.screens_fact_last_played), style = MaterialTheme.typography.labelMedium, color = Palette.Mint)
                        Text(shownLast.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}
