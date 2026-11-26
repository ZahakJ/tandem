package io.github.zahakj.tandem.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
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
 * Start › Layout (and, in a system's or collection's games, Start › Games view): a try-on mode. The Start panel closes
 * and the real home (or games) becomes the preview, whole and uncovered, with thin accent brackets in its corners
 * to say it is being tried. L1 / R1 put on the layout before or after, live (R1 is the next one in every language;
 * the dots run the other way in right to left), each with a short popup naming it, saying what it is like and where
 * it is among them. The D-pad moves inside the layout as usual, so it can be felt; the highlight stays on the same
 * game, system or collection (LayoutFocus). L2 / R2 change the layout's own size (the grid's icons, the shelves'
 * cards, what Orbit holds, Spotlight's rail side, Stage's covers), live too. A keeps what shows, saved once; B or
 * Start puts back what was there, nothing written. A pill along the top says all that; the other screen shows a card
 * of the layout being tried.
 */

object LayoutSwitch {
    enum class Kind { Home, Games }

    var open by mutableStateOf(false)
        private set
    var kind by mutableStateOf(Kind.Home)
        private set
    /** The layout being tried. */
    var index by mutableIntStateOf(0)
        private set
    /** Bumped each time a layout is put on (or the size changes): the popup shows again. */
    var popTick by mutableIntStateOf(0)
        private set

    /** The look when the switcher opened, put back by B. */
    private var before: Look? = null
    /** What was highlighted when it opened, found again in every layout tried. */
    private var spot = LayoutSpot()
    /** A went down while trying: its release keeps the layout (the release of the A that opened it doesn't). */
    private var aDown = false

    fun options(): List<Pair<String, String>> = if (kind == Kind.Home) HomeLayouts else GameViews

    private fun current(look: Look): String = if (kind == Kind.Home) look.homeLayout else look.gameView

    /** The layout (or view) in use when the switcher opened. */
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
        aDown = false
        open = true
        popTick++
        Sounds.menuOpen()
    }

    /** L1 (-1) / R1 (+1): the layout before or after, wrapping round; it goes on once it rests a moment (host). */
    fun step(by: Int) {
        val n = options().size
        if (n == 0) return
        index = Math.floorMod(index + by, n)
        popTick++
        Sounds.page()
    }

    /** L2 (-1) / R2 (+1): the tried layout's own size, live. */
    fun size(by: Int) {
        val id = options().getOrNull(index)?.first ?: return
        val knob = knobFor(kind, id) ?: run { Sounds.error(); return }
        val look = Store.settings.look
        val i = knob.options.indexOfFirst { it.first == knob.get(look) }.coerceAtLeast(0)
        val next = (i + by).coerceIn(0, knob.options.size - 1)
        if (next == i) { Sounds.error(); return }
        Store.previewSettings { it.copy(look = knob.set(it.look, knob.options[next].first)) }
        popTick++
        Sounds.move()
    }

    /** The tried layout's size as shown (its knob's label), or null for a layout without one. */
    fun sizeLabel(): String? {
        val id = options().getOrNull(index)?.first ?: return null
        val knob = knobFor(kind, id) ?: return null
        val look = Store.settings.look
        return knob.options.firstOrNull { it.first == knob.get(look) }?.second ?: knob.options.first().second
    }

    /** Puts the tried layout on (after it rested a moment: LayoutSwitchHost). */
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

    /**
     * A while trying, from the app root before the screen sees it: only a release after a press made here keeps the
     * layout. True when the key was used up.
     */
    fun onKey(e: KeyEvent): Boolean {
        if (!open) return false
        val select = e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.NumPadEnter || e.key == Key.ButtonA
        if (!select) return false
        when (e.type) {
            KeyEventType.KeyDown -> if (e.nativeKeyEvent.repeatCount == 0) aDown = true
            KeyEventType.KeyUp -> if (aDown) { aDown = false; keep() }
        }
        return true
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
        aDown = false
        before = null
        Nav.focusTick++
    }

    /** The layout and every knob the switcher can touch, as they were in [b]; anything else as it is now. */
    private fun restore(now: Look, b: Look): Look = now.copy(
        homeLayout = b.homeLayout, gameView = b.gameView, gridAcross = b.gridAcross, gridRows = b.gridRows,
        gridSize = b.gridSize, orbitGroups = b.orbitGroups, spotlightRail = b.spotlightRail, stageCovers = b.stageCovers,
    )

    /** The highlight goes where [layout] shows what was highlighted (LayoutFocus). */
    private fun carryFocus(layout: String) {
        val t = LayoutFocus.target(layout, spot)
        t.home?.let { FocusMemory.remember(Route.Home.key, it) }
        when (layout) {
            "orbit" -> t.group?.let { FocusMemory.remember(Orbit.ROUTE, it) }
            "spotlight" -> t.group?.let { SpotlightMode.rememberSpot(it, t.item.orEmpty()) }
            "stage" -> t.group?.let { StageMode.rememberSpot(it, t.item) }
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

/** A layout's own size (or the one setting most like it): L2 / R2 while trying it. */
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
            "stage" -> Knob(StageMode.Covers, { it.stageCovers }) { l, v -> l.copy(stageCovers = v) }
            else -> null
        }
        LayoutSwitch.Kind.Games -> when (id) {
            "grid" -> Knob(cardSizes(), { it.gridSize }) { l, v -> l.copy(gridSize = v) }
            else -> null
        }
    }
}

/** Try-on mode, composed in the app root over the screens: the brackets, the pill, the popup. Nothing covers the layout. */
@Composable
fun LayoutSwitchHost() {
    val open = LayoutSwitch.open
    BackHandler(open) { LayoutSwitch.cancel() }
    // Every button belongs to the try-on while it lasts (a Y menu or the side panel over a layout being tried would
    // only confuse); the D-pad and A go through the screen as usual (A is caught at the root: LayoutSwitch.onKey).
    InterceptButtons(open) { b ->
        when (b) {
            Button.L1 -> LayoutSwitch.step(-1)
            Button.R1 -> LayoutSwitch.step(1)
            Button.L2 -> LayoutSwitch.size(-1)
            Button.R2, Button.SidePanel -> LayoutSwitch.size(1)
            Button.Start -> LayoutSwitch.cancel()
            else -> Unit
        }
        true
    }
    // The layout goes on once the shoulders rest a moment, so skimming past several never builds every one.
    LaunchedEffect(open, LayoutSwitch.index) {
        if (!open) return@LaunchedEffect
        delay(160)
        LayoutSwitch.tryHighlighted()
    }
    val still = Motion.style == AnimationStyle.Off || Store.settings.reduceMotion
    AnimatedVisibility(open, enter = fadeIn(tween(if (still) 0 else 180)), exit = fadeOut(tween(if (still) 0 else 160))) {
        Box(Modifier.fillMaxSize()) {
            TryOnBrackets(Modifier.fillMaxSize())
            TryOnPill(Modifier.align(Alignment.TopCenter).padding(top = 8.dp))
            TryOnPopup(Modifier.align(Alignment.Center).offset(y = (-24).dp))
        }
    }
}

/** Thin accent brackets in the four corners: edit mode, without covering anything. */
@Composable
private fun TryOnBrackets(modifier: Modifier) {
    val color = Palette.Butter
    Canvas(modifier.padding(6.dp)) {
        val arm = 34.dp.toPx()
        val w = 3.dp.toPx()
        val r = size
        fun l(a: Offset, b: Offset) = drawLine(color, a, b, w, StrokeCap.Round)
        l(Offset(0f, 0f), Offset(arm, 0f)); l(Offset(0f, 0f), Offset(0f, arm))
        l(Offset(r.width, 0f), Offset(r.width - arm, 0f)); l(Offset(r.width, 0f), Offset(r.width, arm))
        l(Offset(0f, r.height), Offset(arm, r.height)); l(Offset(0f, r.height), Offset(0f, r.height - arm))
        l(Offset(r.width, r.height), Offset(r.width - arm, r.height)); l(Offset(r.width, r.height), Offset(r.width, r.height - arm))
    }
}

/** "L1 ◂ Orbit ▸ R1 · L2 R2 Size: Large · A Keep · B Cancel" in one small pill along the top. */
@Composable
private fun TryOnPill(modifier: Modifier) {
    val name = LayoutSwitch.options().getOrNull(LayoutSwitch.index)?.second?.substringBefore(" (").orEmpty()
    val size = LayoutSwitch.sizeLabel()
    Row(
        modifier.graphicsLayer { shape = RoundedCornerShape(50); clip = true; shadowElevation = 14f * density }
            .background(Palette.Card.copy(alpha = 0.94f)).padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ButtonHint("L1", "", Palette.Lavender, onClick = { LayoutSwitch.step(-1) })
        Text("◂ $name ▸", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold), color = Palette.Butter, maxLines = 1, overflow = TextOverflow.Ellipsis)
        ButtonHint("R1", "", Palette.Lavender, onClick = { LayoutSwitch.step(1) })
        if (size != null) {
            Dot()
            ButtonHint("L2", "", Palette.Sky, onClick = { LayoutSwitch.size(-1) })
            ButtonHint("R2", Tx.s(R.string.panels_layout_hint_size, size), Palette.Sky, onClick = { LayoutSwitch.size(1) })
        }
        Dot()
        ButtonHint("A", Tx.s(R.string.panels_layout_hint_keep), Palette.Mint, onClick = { LayoutSwitch.keep() })
        ButtonHint("B", Tx.s(R.string.panels_layout_hint_cancel), Palette.Peach, onClick = { LayoutSwitch.cancel() })
    }
}

@Composable
private fun Dot() = Box(Modifier.size(4.dp).clip(CircleShape).background(Palette.Muted.copy(alpha = 0.6f)))

/**
 * The popup each time a layout goes on: Pip, its name, a line about it and a dot for each layout, popping in on a
 * spring, gone after a moment and a half.
 */
@Composable
private fun TryOnPopup(modifier: Modifier) {
    val still = Motion.style == AnimationStyle.Off || Store.settings.reduceMotion
    val shown = remember { Animatable(0f) }
    val tick = LayoutSwitch.popTick
    LaunchedEffect(tick) {
        if (still) shown.snapTo(1f) else shown.animateTo(1f, spring(0.6f, 520f))
        delay(1500)
        shown.animateTo(0f, tween(if (still) 0 else 260))
    }
    val kind = LayoutSwitch.kind
    val options = LayoutSwitch.options()
    val index = LayoutSwitch.index
    val (id, name) = options.getOrNull(index) ?: return
    val blurb = if (kind == LayoutSwitch.Kind.Home) LayoutBlurbs[id] else null
    Column(
        modifier.widthIn(max = 420.dp)
            .graphicsLayer {
                val v = shown.value
                alpha = v.coerceIn(0f, 1f)
                val s = 0.86f + 0.14f * v
                scaleX = s; scaleY = s
                shape = RoundedCornerShape(26.dp); clip = true; shadowElevation = 24f * density
                ambientShadowColor = Palette.Butter; spotShadowColor = Palette.Butter
            }
            .background(Palette.Card.copy(alpha = 0.96f)).padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Pip(34.dp, mood = 1)
            Spacer(Modifier.width(10.dp))
            Text(
                name.substringBefore(" ("), style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (LayoutSwitch.inUse() == id) { Spacer(Modifier.width(8.dp)); Text("✓", style = MaterialTheme.typography.titleMedium, color = Palette.Butter) }
        }
        if (blurb != null) Text(
            Tx.s(blurb), Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodySmall, color = Palette.Muted,
            maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
        )
        LayoutDots(options.size, index, Modifier.padding(top = 10.dp))
    }
}

/** A dot for each layout, the one tried lit (they run from the right in right to left, as the layouts do). */
@Composable
fun LayoutDots(count: Int, at: Int, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        for (i in 0 until count) {
            val on = i == at
            Box(Modifier.size(if (on) 9.dp else 6.dp).clip(CircleShape).background(if (on) Palette.Butter else Palette.Muted.copy(alpha = 0.45f)))
        }
    }
}

/** The other screen while trying layouts: a card of the one on, so both screens agree. */
@Composable
fun LayoutTryOnCard(modifier: Modifier = Modifier) {
    val kind = LayoutSwitch.kind
    val options = LayoutSwitch.options()
    val index = LayoutSwitch.index
    val (id, name) = options.getOrNull(index) ?: return
    Column(
        modifier.widthIn(max = 380.dp)
            .graphicsLayer { shape = RoundedCornerShape(26.dp); clip = true; shadowElevation = 20f * density; ambientShadowColor = Palette.Butter; spotShadowColor = Palette.Butter }
            .background(Palette.Card.copy(alpha = 0.96f)).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LayoutPreview(if (kind == LayoutSwitch.Kind.Home) id else "view-$id", Modifier.fillMaxWidth().aspectRatio(1.6f))
        Text(
            name.substringBefore(" ("), Modifier.padding(top = 10.dp), style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        LayoutDots(options.size, index, Modifier.padding(top = 8.dp))
    }
}
