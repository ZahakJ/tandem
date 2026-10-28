package io.github.zahakj.tandem.ui

import android.app.Presentation
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.text.format.DateUtils
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
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
import io.github.zahakj.tandem.RunningGames
import io.github.zahakj.tandem.everPlayed
import io.github.zahakj.tandem.plural
import io.github.zahakj.tandem.Launcher
import io.github.zahakj.tandem.Libraries
import io.github.zahakj.tandem.Selected
import io.github.zahakj.tandem.Selection
import io.github.zahakj.tandem.data.Store
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
    Box(Modifier.fillMaxSize().clearOfScrapeCard()) { Details(selected, mode, spotlight) { pillClear = it } }
    // A running scrape's card, along the bottom edge (under the screensaver's dimming, which BottomExtras draws).
    ScrapeCard()
    BottomExtras(bannerTop = if (spotlight) pillClear else 0.dp)
    ThemeOverlay()
}

@Composable
private fun Details(selected: Selected, mode: String, spotlight: Boolean, onPill: (androidx.compose.ui.unit.Dp) -> Unit) {
    if (SmartFolderForm.folderId != null) {
        // The smart folder editor's live preview (SmartFolderForm.kt).
        SmartPreview()
    } else if (mode == "clock") {
        ClockFace()
    } else if (spotlight) {
        // The icon grid's big-icon details (Spotlight.kt).
        SpotlightView(selected, onPill)
    } else {
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
        else -> {
            val split = Store.settings.look.heroSplit.coerceIn(0.25f, 0.7f)
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxWidth().weight(split)) {
                    HeroArt(game, Modifier.fillMaxSize().heroFade())
                    HeroLogo(game, Modifier.align(Alignment.BottomCenter).padding(16.dp).riseIn(game.key).floating(game.key.hashCode(), bob = 4.dp))
                    if (resumable) Pill("● Running", Palette.Mint, Modifier.align(Alignment.TopEnd).padding(14.dp).darkChip())
                }
                Column(
                    Modifier.fillMaxWidth().weight(1f - split).padding(start = 22.dp, end = 22.dp, top = 10.dp, bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) { GameInfo(game, resumable, descriptionLines = if (split > 0.55f) 4 else 7) }
            }
        }
    }
}

/** Pills, play time, description (or, without one, the box and a few facts) and button hints for a game. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.GameInfo(game: Game, resumable: Boolean, descriptionLines: Int, pinHints: Boolean = true) {
    val meta = game.meta
    val color = Palette.forName(game.platform.id)
    // Wraps onto a second line instead of running off the edge.
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Pill(game.platform.shortName, color)
        meta.year?.let { Pill("$it", Palette.Butter) }
        meta.genres.map(Collections::tidyGenre).distinctBy { it.lowercase() }.take(3).forEach { Pill(it, Palette.Lavender) }
        meta.rating?.let { Pill("★ ${"%.1f".format(it * 5)}", Palette.Peach) }
        // HowLongToBeat: the main story and 100%, when known.
        meta.timeToBeat?.takeIf { it.any }?.let { t ->
            val h = io.github.zahakj.tandem.data.TimeToBeat
            val main = listOf(t.main, t.extra).firstOrNull { it > 0 }
            Pill(
                "⏱ " + listOfNotNull(main?.let { h.hours(it) }, t.complete.takeIf { it > 0 }?.let { "${h.hours(it)} for 100%" }).joinToString(" · "),
                Palette.Sky,
            )
        }
        if (meta.favorite) Pill("♥ Favorite", Palette.Peach)
        GameExtrasBadges(game)
    }
    creditsLine(meta)?.let {
        Text(it, style = MaterialTheme.typography.labelLarge, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    Text(playLine(meta), style = MaterialTheme.typography.titleSmall, color = Palette.Mint, maxLines = 1, overflow = TextOverflow.Ellipsis)
    AchievementsStrip(game)
    val description = meta.description?.trim()?.takeIf { it.isNotEmpty() }
    if (description != null) {
        Text(
            description, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.bodyMedium, color = Palette.Muted,
            maxLines = descriptionLines, overflow = TextOverflow.Ellipsis,
        )
    } else if (pinHints) {
        // No description: the space below shows the game's box and a few facts instead of a big empty gap.
        GameFacts(game, Modifier.weight(1f))
    }
    if (pinHints && description != null) Spacer(Modifier.weight(1f)) else Spacer(Modifier.height(2.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ButtonHint("A", if (resumable) "Resume" else "Play", Palette.Mint)
        // A game still open: Y closes it (twice), as on the icon grid's Resume card.
        if (resumable) ButtonHint("Y", "Close", Palette.Peach) else ButtonHint("Y", "Options", Palette.Butter)
        ButtonHint("X", "Search", Palette.Sky)
        ListSwitchHint()
    }
}

/** L1 / R1, where they switch to the previous or next system, collection or genre. */
@Composable
private fun ListSwitchHint() {
    val label = when (val r = Nav.route) {
        is Route.System -> if (r.folder == null) "Systems" else null
        is Route.Collection -> if (r.id.startsWith("genre:")) "Genres" else "Lists"
        Route.Home -> "Rows"
        else -> null
    } ?: return
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
        add("Times played" to if (meta.playCount > 0) "${meta.playCount}" else "Not yet")
        if (meta.playSeconds >= 60) add("Play time" to formatPlayLong(meta.playSeconds))
        meta.timeToBeat?.takeIf { it.any }?.let { t ->
            val h = io.github.zahakj.tandem.data.TimeToBeat
            val parts = listOfNotNull(
                t.main.takeIf { it > 0 }?.let { "${h.hours(it)} main" },
                t.extra.takeIf { it > 0 }?.let { "${h.hours(it)} extras" },
                t.complete.takeIf { it > 0 }?.let { "${h.hours(it)} 100%" },
            )
            add("Time to beat" to parts.joinToString(" · "))
        }
        if (meta.lastPlayed > 0) add("Last played" to relative(meta.lastPlayed))
        if (meta.firstSeen > 0) add("Added" to DateUtils.formatDateTime(null, meta.firstSeen, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH))
        game.group?.let { add("Folder" to it) }
        add("File" to game.file.name)
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
            p == "1" -> "1 player"
            p.firstOrNull()?.isDigit() == true -> "$p players"
            else -> p
        }
    }
    val parts = listOfNotNull(m.developer, m.publisher?.takeIf { !it.equals(m.developer, ignoreCase = true) }, players)
    return parts.joinToString(" · ").ifEmpty { null }
}

/** "3 minutes ago", "Yesterday", or "just now". */
private fun relative(time: Long): String {
    val now = System.currentTimeMillis()
    return if (now - time < 60_000) "just now" else DateUtils.getRelativeTimeSpanString(time, now, DateUtils.MINUTE_IN_MILLIS).toString()
}

/** "Played 1 min · 1 minute ago". Imported history (a time or a date with no count) counts as played. */
private fun playLine(m: io.github.zahakj.tandem.data.GameMeta): String {
    if (!m.everPlayed) return "Not played yet: a fresh adventure!"
    val what = when {
        m.playSeconds > 0 -> "Played ${formatPlayLong(m.playSeconds)}"
        m.playCount > 0 -> "Played ${plural(m.playCount, "time")}"
        else -> "Played"
    }
    return if (m.lastPlayed > 0) "$what · ${relative(m.lastPlayed)}" else what
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
        if (played >= 60) Text("${formatPlayLong(played)} played in here", style = MaterialTheme.typography.bodyMedium, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(18.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ButtonHint("A", "Open", color)
            if (hasMenu) ButtonHint("Y", "Options", Palette.Butter)
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
        ButtonHint("A", "Open", color)
        ButtonHint("Y", "Options", Palette.Butter)
        ListSwitchHint()
    }
    if (!shown) {
        GroupDetails(
            platform.name, plural(games.size, "game"), games, color, true,
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
            ButtonHint("B", "Back", Palette.Lavender)
            ButtonHint("Start", "Close", Palette.Sky)
        }
    }
}

@Composable
private fun Greeting() {
    val now by rememberMinute()
    val hour = Calendar.getInstance().apply { time = now }.get(Calendar.HOUR_OF_DAY)
    val hello = when (hour) { in 5..11 -> "Good morning!"; in 12..17 -> "Good afternoon!"; in 18..22 -> "Good evening!"; else -> "Up late? Me too." }
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
            Text(if (total == 0) "Let's find some games" else "${plural(total, "game")} ready to play", style = MaterialTheme.typography.bodyMedium, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (shownLast != null) {
                Spacer(Modifier.height(18.dp))
                Row(
                    Modifier.clip(RoundedCornerShape(16.dp)).background(Palette.Card).padding(10.dp).widthIn(max = 360.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(54.dp).shadow(4.dp, RoundedCornerShape(10.dp)).clip(RoundedCornerShape(10.dp))) { GameArt(shownLast) }
                    Column(Modifier.padding(start = 10.dp)) {
                        Text("Last played", style = MaterialTheme.typography.labelMedium, color = Palette.Mint)
                        Text(shownLast.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}
