package io.github.zahakj.tandem.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.zahakj.tandem.Button
import io.github.zahakj.tandem.Collections
import io.github.zahakj.tandem.GroupTarget
import io.github.zahakj.tandem.R
import io.github.zahakj.tandem.Selected
import io.github.zahakj.tandem.Selection
import io.github.zahakj.tandem.Tx
import io.github.zahakj.tandem.data.Look
import io.github.zahakj.tandem.data.Store
import kotlinx.coroutines.delay

/*
 * Start › Layout (and, in a system's or collection's games, Start › Games view): every home layout (or games view) as
 * a card along the bottom of the screen, each a live sketch in the current theme, the one in use ticked. Left / Right
 * (or L1 / R1) highlight a card and, once it rests a moment, the home behind turns into that layout, rising in as the
 * home does at startup, with the highlight on the same game, system or collection. Down reaches the card's own size
 * (the grid's icons, the shelves' cards, what Orbit holds, Spotlight's rail side), Left / Right change it, live too.
 * A keeps what shows; B (or Start) puts back what was there, nothing saved. Each layout's own settings are its own:
 * only the layout and the knob touched change.
 */

object LayoutSwitch {
    enum class Kind { Home, Games }

    var open by mutableStateOf(false)
        private set
    var kind by mutableStateOf(Kind.Home)
        private set
    /** The highlighted card. */
    var index by mutableIntStateOf(0)
        private set
    /** Down was pressed: Left / Right change the highlighted card's size instead. */
    var onKnob by mutableStateOf(false)
        private set

    /** The look when the switcher opened, put back by B. */
    private var before: Look? = null
    /** What was highlighted when it opened, found again in every layout tried. */
    private var spot = LayoutSpot()

    const val ROUTE = "layoutswitch"

    fun options(): List<Pair<String, String>> = if (kind == Kind.Home) HomeLayouts else GameViews

    private fun current(look: Look): String = if (kind == Kind.Home) look.homeLayout else look.gameView

    /** The layout (or view) in use when the switcher opened: its card is ticked. */
    fun inUse(): String? = before?.let(::current)

    fun show(k: Kind) {
        if (open) return
        kind = k
        // Home layouts are tried on the home itself: from another screen (Stats, a folder page) Start goes home first.
        if (k == Kind.Home && Nav.route != Route.Home) {
            FolderPopup.close()
            while (Nav.back()) Unit
            Nav.replace(Route.Home)
        }
        val look = Store.settings.look
        before = look
        spot = spotOf(Selection.current)
        index = options().indexOfFirst { it.first == current(look) }.coerceAtLeast(0)
        onKnob = false
        FocusMemory.forget(ROUTE)
        open = true
        Sounds.menuOpen()
    }

    /** Left / Right, L1 / R1: the next card (wrapping round), or the highlighted card's next size. */
    fun step(by: Int) {
        if (onKnob) {
            val id = options().getOrNull(index)?.first ?: return
            val knob = knobFor(kind, id) ?: return
            val look = Store.settings.look
            val i = knob.options.indexOfFirst { it.first == knob.get(look) }.coerceAtLeast(0)
            val next = (i + by).coerceIn(0, knob.options.size - 1)
            if (next == i) { Sounds.error(); return }
            Store.previewSettings { it.copy(look = knob.set(it.look, knob.options[next].first)) }
            Sounds.move()
            return
        }
        val n = options().size
        if (n == 0) return
        index = Math.floorMod(index + by, n)
        Sounds.move()
    }

    /** Down and Up: onto the highlighted card's size, and back to the cards. */
    fun knob(on: Boolean) {
        val id = options().getOrNull(index)?.first ?: return
        if (on && knobFor(kind, id) == null) return
        if (onKnob == on) return
        onKnob = on
        Sounds.move()
    }

    /** Shows the highlighted card's layout behind the switcher (after it rested a moment: LayoutSwitchHost). */
    internal fun tryHighlighted() {
        val id = options().getOrNull(index)?.first ?: return
        show(id)
    }

    private fun show(id: String) {
        val look = Store.settings.look
        if (current(look) == id) return
        if (kind == Kind.Home) carryFocus(id)
        // Hidden in the frame the layout changes, then it rises in (SwapFx.arrive), as at startup.
        SwapFx.arrive(StartFlow.librarySlot())
        Store.previewSettings { s -> s.copy(look = if (kind == Kind.Home) s.look.copy(homeLayout = id) else s.look.copy(gameView = id)) }
    }

    /** A: keeps the layout (and any size) on screen, saved. */
    fun keep() {
        if (!open) return
        tryHighlighted()
        val saved = Store.settings
        Store.updateSettings { saved }
        Sounds.confirm()
        finish()
    }

    /** B or Start: everything back the way it was, nothing saved. */
    fun cancel() {
        if (!open) return
        val b = before
        if (b != null) {
            val shown = current(Store.settings.look)
            if (kind == Kind.Home && shown != b.homeLayout) carryFocus(b.homeLayout)
            if (shown != current(b)) SwapFx.arrive(StartFlow.librarySlot())
            Store.previewSettings { it.copy(look = restore(it.look, b)) }
        }
        Sounds.back()
        finish()
    }

    private fun finish() {
        open = false
        onKnob = false
        before = null
        Nav.focusTick++
    }

    /** The layout and every knob the switcher can touch, as they were in [b]; anything else as it is now. */
    private fun restore(now: Look, b: Look): Look = now.copy(
        homeLayout = b.homeLayout, gameView = b.gameView, gridAcross = b.gridAcross, gridRows = b.gridRows,
        gridSize = b.gridSize, orbitGroups = b.orbitGroups, spotlightRail = b.spotlightRail,
    )

    /** The highlight goes where [layout] shows what was highlighted (LayoutFocus). */
    private fun carryFocus(layout: String) {
        val t = LayoutFocus.target(layout, spot)
        t.home?.let { FocusMemory.remember(Route.Home.key, it) }
        when (layout) {
            "orbit" -> t.group?.let { FocusMemory.remember(Orbit.ROUTE, it) }
            "spotlight" -> t.group?.let { SpotlightMode.rememberSpot(it, t.item.orEmpty()) }
        }
    }

    private fun spotOf(s: Selected): LayoutSpot = when (s) {
        is Selected.GameItem -> LayoutSpot(
            game = s.game.key, system = s.game.platform.id,
            continuing = runCatching { Collections.continuePlaying().any { it.key == s.game.key } }.getOrDefault(false),
            gameOnGrid = IconGrid.menuData?.second?.contains("game:${s.game.key}") == true,
        )
        is Selected.System -> LayoutSpot(system = s.library.platform.id)
        is Selected.Group -> when (val t = s.target) {
            is GroupTarget.System -> LayoutSpot(system = t.platformId)
            is GroupTarget.Collection -> LayoutSpot(collection = t.id)
            else -> LayoutSpot()
        }
        is Selected.App -> LayoutSpot(app = s.pkg)
        else -> LayoutSpot()
    }
}

/** A layout's own size (or the one setting most like it), on its card: Down, then Left / Right. */
private class Knob(val options: List<Pair<String, String>>, val get: (Look) -> String, val set: (Look, String) -> Look)

private fun knobFor(kind: LayoutSwitch.Kind, id: String): Knob? {
    val cardSizes = { Grid.sizes.filter { !it.first.startsWith("cols:") } }
    return when (kind) {
        LayoutSwitch.Kind.Home -> when (id) {
            // Icons across the screen, biggest first; fixed rows give way to the size picked.
            "grid" -> Knob((5..9).map { "$it" to IconGrid.sizeName(it) }, { it.gridAcross.toString() }) { l, v -> l.copy(gridAcross = v.toInt(), gridRows = 0) }
            "shelves" -> Knob(cardSizes().filter { it.first != "huge" }, { it.gridSize }) { l, v -> l.copy(gridSize = v) }
            "orbit" -> Knob(Orbit.Groups, { it.orbitGroups }) { l, v -> l.copy(orbitGroups = v) }
            "spotlight" -> Knob(SpotlightMode.Rails, { it.spotlightRail }) { l, v -> l.copy(spotlightRail = v) }
            else -> null
        }
        LayoutSwitch.Kind.Games -> when (id) {
            "grid" -> Knob(cardSizes(), { it.gridSize }) { l, v -> l.copy(gridSize = v) }
            else -> null
        }
    }
}

/** The switcher, composed in the app root over the screens and under Start (which opens it). */
@Composable
fun LayoutSwitchHost() {
    val open = LayoutSwitch.open
    PanelGuard(open)
    BackHandler(open) { LayoutSwitch.cancel() }
    InterceptButtons(open) { b ->
        when (b) {
            Button.L1 -> LayoutSwitch.step(-1 * shoulderSign())
            Button.R1 -> LayoutSwitch.step(1 * shoulderSign())
            Button.Start -> LayoutSwitch.cancel()
            else -> Unit
        }
        true
    }
    // The home behind follows the highlight once it rests, so skimming past cards never builds every layout.
    LaunchedEffect(open, LayoutSwitch.index) {
        if (!open) return@LaunchedEffect
        delay(220)
        LayoutSwitch.tryHighlighted()
    }
    val layer = rememberLayer(open)
    val still = Motion.style == AnimationStyle.Off || Store.settings.reduceMotion
    // Only a light dim: the point is to see the home behind.
    AnimatedVisibility(open, enter = fadeIn(tween(160)), exit = fadeOut(tween(160))) {
        Box(Modifier.fillMaxSize().background(Palette.NightDeep.copy(alpha = 0.2f)).swallowTaps { LayoutSwitch.cancel() })
    }
    AnimatedVisibility(
        open,
        enter = if (still) fadeIn(tween(0)) else slideInVertically(spring(0.85f, 420f)) { it / 2 } + fadeIn(tween(160)),
        exit = if (still) fadeOut(tween(0)) else slideOutVertically(tween(180)) { it / 2 } + fadeOut(tween(140)),
    ) {
        ProvideLayer(layer) {
            Box(
                Modifier.fillMaxSize()
                    .onFocusChanged { Layers.focusIn(layer, it.hasFocus) }
                    .onPreviewKeyEvent { !LayoutSwitch.open && it.isNavKey() }
                    .trapFocus()
                    .padding(PanelMargin),
                contentAlignment = Alignment.BottomCenter,
            ) { SwitcherPanel() }
        }
    }
}

/** L1 moves the highlight left and R1 right, as on screen: in right to left the cards run from the right, so L1 steps forward. */
private fun shoulderSign(): Int = if (rtlNow) -1 else 1

@Composable
private fun SwitcherPanel() {
    val kind = LayoutSwitch.kind
    val options = LayoutSwitch.options()
    val index = LayoutSwitch.index
    var pressed by androidx.compose.runtime.remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth()
            .graphicsLayer { shape = RoundedCornerShape(26.dp); clip = true; shadowElevation = 22f * density }
            .background(Palette.Card.copy(alpha = 0.96f))
            .swallowTaps()
            // One focus target for the whole panel: the D-pad and A are read here, so nothing settles or ticks twice.
            .takesFocus(LayoutSwitch.ROUTE, "cards", true)
            .focusable()
            .onKeyEvent { e ->
                val down = e.type == KeyEventType.KeyDown
                when (e.key) {
                    Key.DirectionLeft, Key.DirectionRight -> { if (down) LayoutSwitch.step(e.sideStep()); true }
                    Key.DirectionDown -> { if (down) LayoutSwitch.knob(true); true }
                    Key.DirectionUp -> { if (down) LayoutSwitch.knob(false); true }
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.ButtonA -> {
                        // Only a release after a press here counts (not the end of the A that opened it).
                        if (down) pressed = true else if (pressed) { pressed = false; LayoutSwitch.keep() }
                        true
                    }
                    else -> false
                }
            }
            .padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlyphChip(if (kind == LayoutSwitch.Kind.Home) Glyph.Home else Glyph.Grid, Palette.Butter, 30.dp)
            Spacer(Modifier.width(10.dp))
            Text(
                Tx.s(if (kind == LayoutSwitch.Kind.Home) R.string.look_gallery_layouts else R.string.look_games_view),
                Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                ButtonHint("◀▶", Tx.s(R.string.panels_layout_hint_try), Palette.Muted, tappable = false)
                val id = options.getOrNull(index)?.first
                if (id != null && knobFor(kind, id) != null) ButtonHint("▲▼", Tx.s(R.string.panels_layout_hint_adjust), Palette.Muted, tappable = false)
                ButtonHint("A", Tx.s(R.string.panels_layout_hint_keep), Palette.Mint, onClick = { LayoutSwitch.keep() })
                ButtonHint("B", Tx.s(R.string.panels_layout_hint_cancel), Palette.Lavender, onClick = { LayoutSwitch.cancel() })
            }
        }
        Spacer(Modifier.height(12.dp))
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val gap = 12.dp
            val w = ((maxWidth - gap * (options.size - 1)) / options.size.coerceAtLeast(1)).coerceAtMost(200.dp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap, Alignment.CenterHorizontally)) {
                options.forEachIndexed { i, (id, name) -> LayoutCard(kind, id, name, i == index, Modifier.width(w)) }
            }
        }
        // What the highlighted layout is like, on the home layouts (the same words as Settings › Home & layout).
        val blurb = if (kind == LayoutSwitch.Kind.Home) options.getOrNull(index)?.first?.let { LayoutBlurbs[it] } else null
        if (blurb != null) Text(
            Tx.s(blurb), Modifier.fillMaxWidth().padding(top = 10.dp, start = 4.dp, end = 4.dp).heightIn(min = 34.dp),
            style = MaterialTheme.typography.bodySmall, color = Palette.Muted, maxLines = 3, overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun LayoutCard(kind: LayoutSwitch.Kind, id: String, name: String, on: Boolean, modifier: Modifier) {
    val still = Motion.style == AnimationStyle.Off || Store.settings.reduceMotion
    val grow by animateFloatAsState(if (on) 1f else 0f, if (still) tween(0) else spring(0.7f, 500f), label = "layout-card")
    val knob = knobFor(kind, id)
    val onKnob = on && LayoutSwitch.onKnob
    val inUse = LayoutSwitch.inUse() == id
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .graphicsLayer {
                val s = 0.94f + 0.06f * grow
                scaleX = s; scaleY = s
                translationY = -6f * density * grow
                alpha = 0.72f + 0.28f * grow
            }
            .clip(shape)
            .background(Palette.CardHigh)
            .border(if (on) 2.dp else 1.dp, if (on && !onKnob) Palette.Butter else Palette.Text.copy(alpha = 0.12f), shape)
            .swallowTaps {
                val i = LayoutSwitch.options().indexOfFirst { it.first == id }
                if (i == LayoutSwitch.index) LayoutSwitch.keep() else LayoutSwitch.step(i - LayoutSwitch.index)
            },
    ) {
        // A live sketch of the layout in the theme of the moment, with the size it would have.
        val drift = if (on && !still && id == "spotlight") loopClock(9000) else null
        LayoutPreview(
            if (kind == LayoutSwitch.Kind.Home) id else "view-$id",
            Modifier.fillMaxWidth().aspectRatio(1.6f), rounded = false, drift = drift,
        )
        Row(Modifier.fillMaxWidth().padding(start = 10.dp, end = 8.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                name.substringBefore(" ("), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (inUse) Text("✓", style = MaterialTheme.typography.labelLarge, color = Palette.Butter)
        }
        // Its size, on every card that has one; lit when Down took the highlight onto it.
        Box(Modifier.fillMaxWidth().height(30.dp).padding(horizontal = 8.dp, vertical = 3.dp), contentAlignment = Alignment.Center) {
            if (knob != null) {
                val look = Store.settings.look
                val label = knob.options.firstOrNull { it.first == knob.get(look) }?.second ?: knob.options.first().second
                Box(
                    Modifier.fillMaxWidth().height(24.dp)
                        .background(if (onKnob) Palette.Butter.copy(alpha = 0.3f) else Color.Transparent, CircleShape)
                        .border(if (onKnob) 1.5.dp else 0.dp, if (onKnob) Palette.Butter else Color.Transparent, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    FitText(
                        if (!onKnob) label else if (isRtl) "▸ $label ◂" else "◂ $label ▸", MaterialTheme.typography.labelMedium, Modifier.padding(horizontal = 6.dp),
                        color = if (onKnob) Palette.Text else Palette.Muted,
                    )
                }
            }
        }
    }
}
