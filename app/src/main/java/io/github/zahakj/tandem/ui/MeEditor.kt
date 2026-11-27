package io.github.zahakj.tandem.ui

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.zahakj.tandem.Button
import io.github.zahakj.tandem.Collections
import io.github.zahakj.tandem.Game
import io.github.zahakj.tandem.Libraries
import io.github.zahakj.tandem.Privacy
import io.github.zahakj.tandem.R
import io.github.zahakj.tandem.Selected
import io.github.zahakj.tandem.Selection
import io.github.zahakj.tandem.Tx
import io.github.zahakj.tandem.data.PlazaStore
import io.github.zahakj.tandem.pass.AvatarColors
import io.github.zahakj.tandem.pass.AvatarPart
import io.github.zahakj.tandem.pass.AvatarSpec
import io.github.zahakj.tandem.pass.CardCodec
import io.github.zahakj.tandem.pass.Greetings
import io.github.zahakj.tandem.pass.MeCard
import io.github.zahakj.tandem.pass.TandemPass
import kotlinx.coroutines.launch

/*
 * Me: the character maker for Tandem Pass (Route.Tool("me")). A big live preview on the left that blinks, bobs and
 * hops at every change; on the right the parts by tab (L1 / R1), each choice a small drawing of you wearing it. X
 * makes a surprise look, Y undoes, B is done (every change is saved as it is made). The Card tab holds the name,
 * the greeting and the favourite game. The first time, Pip walks you through it in three short steps.
 */

/** A tab of the maker and the parts on it. */
private enum class MeTab(@StringRes val title: Int, val parts: List<AvatarPart>) {
    Face(R.string.plaza_tab_face, listOf(AvatarPart.Shape, AvatarPart.Skin, AvatarPart.Mouth, AvatarPart.Cheeks, AvatarPart.Beard)),
    Eyes(R.string.plaza_tab_eyes, listOf(AvatarPart.Eyes, AvatarPart.EyeColor, AvatarPart.Brows)),
    Hair(R.string.plaza_tab_hair, listOf(AvatarPart.Hair, AvatarPart.HairColor)),
    Hats(R.string.plaza_tab_hat, listOf(AvatarPart.Headwear)),
    Outfit(R.string.plaza_tab_outfit, listOf(AvatarPart.Top, AvatarPart.TopColor)),
    Extras(R.string.plaza_tab_extras, listOf(AvatarPart.Glasses, AvatarPart.Earrings, AvatarPart.FaceExtra)),
    Colour(R.string.plaza_tab_colour, listOf(AvatarPart.Theme)),
    Card(R.string.plaza_tab_card, emptyList()),
}

@StringRes
private fun partTitle(p: AvatarPart): Int = when (p) {
    AvatarPart.Shape -> R.string.plaza_part_shape
    AvatarPart.Skin -> R.string.plaza_part_skin
    AvatarPart.Eyes -> R.string.plaza_part_eyes
    AvatarPart.EyeColor -> R.string.plaza_part_eye_color
    AvatarPart.Brows -> R.string.plaza_part_brows
    AvatarPart.Mouth -> R.string.plaza_part_mouth
    AvatarPart.Cheeks -> R.string.plaza_part_cheeks
    AvatarPart.Hair -> R.string.plaza_part_hair
    AvatarPart.HairColor -> R.string.plaza_part_hair_color
    AvatarPart.Headwear -> R.string.plaza_part_headwear
    AvatarPart.Top -> R.string.plaza_part_top
    AvatarPart.TopColor -> R.string.plaza_part_top_color
    AvatarPart.Glasses -> R.string.plaza_part_glasses
    AvatarPart.Earrings -> R.string.plaza_part_earrings
    AvatarPart.Theme -> R.string.plaza_part_theme
    AvatarPart.FaceExtra -> R.string.plaza_part_face_extra
    AvatarPart.Beard -> R.string.plaza_part_beard
}

/** The colour a swatch part shows for [value], or null for a part drawn as a little character. */
private fun swatch(p: AvatarPart, value: Int): Color? = when (p) {
    AvatarPart.Skin -> Color(AvatarColors.skin[value])
    AvatarPart.EyeColor -> Color(AvatarColors.eyes[value])
    AvatarPart.HairColor -> Color(AvatarColors.hair[value])
    AvatarPart.TopColor -> Color(AvatarColors.top[value])
    AvatarPart.Theme -> Color(AvatarColors.theme[value])
    else -> null
}

/**
 * Where a part's drawing zooms in: the face for features, the head for hair, a little further out for hats (the tall
 * ones: a top hat, a witch's hat, bunny ears), the body for tops.
 */
private enum class Zoom(val scale: Float, val x: Float, val y: Float) {
    Face(1.9f, 50f, 48f), Head(1.45f, 50f, 40f), Hat(1.2f, 50f, 34f), Body(1.6f, 50f, 92f), Whole(1f, 50f, 60f)
}

/**
 * Hairstyles that fall past the cheeks (bob, long, curly, braids, afro, wavy, twin tails, side shave, mullet, wolf cut,
 * hime, half-up, long curls, locs, box braids): see AvatarArt.kt.
 */
private val LONG_HAIR = setOf(3, 4, 7, 10, 11, 12, 13, 18, 21, 22, 23, 26, 27, 29, 31)

private fun zoomOf(p: AvatarPart) = when (p) {
    AvatarPart.Eyes, AvatarPart.Brows, AvatarPart.Mouth, AvatarPart.Cheeks, AvatarPart.Glasses, AvatarPart.Earrings,
    AvatarPart.FaceExtra, AvatarPart.Beard -> Zoom.Face
    AvatarPart.Shape, AvatarPart.Hair -> Zoom.Head
    AvatarPart.Headwear -> Zoom.Hat
    AvatarPart.Top -> Zoom.Body
    else -> Zoom.Whole
}

object MeEditor {
    const val ROUTE_ID = "me"

    /** The Pip guide's step while it shows (0..2), -1 when it doesn't. */
    var guide by mutableIntStateOf(-1)

    /** Turn Tandem Pass on once Me is made (the switch was flipped before there was a Me). */
    var thenTurnOn = false

    /** B goes on to the Plaza instead of back (the Plaza was asked for before there was a Me). */
    var thenPlaza by mutableStateOf(false)

    fun open(turnOnAfter: Boolean = false, plazaAfter: Boolean = false) {
        thenTurnOn = turnOnAfter
        thenPlaza = plazaAfter
        guide = if (PlazaStore.data.made) -1 else 0
        Nav.go(Route.Tool(ROUTE_ID))
    }
}

private const val ROUTE = "tool:me"

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MeEditorScreen() {
    val ctx = LocalContext.current
    val me = PlazaStore.data.me
    var tab by remember { mutableIntStateOf(0) }
    val undo = remember { mutableStateListOf<MeCard>() }
    var edits by remember { mutableIntStateOf(0) }
    val current = MeTab.entries[tab]

    fun change(next: MeCard) {
        val before = PlazaStore.data.me
        if (next == before) return
        undo.add(before)
        if (undo.size > 40) undo.removeAt(0)
        PlazaStore.update { it.copy(me = next) }
        edits++
    }

    // The bottom screen shows the card as others will see it while you make it.
    DisposableEffect(Unit) {
        PlazaBottom.showMe = true
        onDispose { PlazaBottom.showMe = false; MeEditor.guide = -1; ChipPictures.clear() }
    }
    LaunchedEffect(Unit) { Selection.current = Selected.Home }
    // Every tab's choices are drawings of you in each part: read the parts ahead, off the main thread, so a tab's first
    // visit doesn't parse tens of them as it slides in (again for a new face shape: hats and face parts fit each one).
    LaunchedEffect(me.avatar.shape) { warmWardrobe(listOf(me.avatar.shape)) }
    // Done (B, or its hint): only here is Me made and what was asked for before it carried out, never when the screen
    // merely goes away (a restart behind a game, a route change from elsewhere).
    androidx.activity.compose.BackHandler(!Nav.overlayOpen && Layers.top == null) {
        if (!PlazaStore.data.made) PlazaStore.update { it.copy(made = true) }
        val turnOn = MeEditor.thenTurnOn
        val plaza = MeEditor.thenPlaza
        MeEditor.thenTurnOn = false
        MeEditor.thenPlaza = false
        // Made from the Plaza's door: Done walks on into the Plaza, and Back from there goes where you came from.
        if (plaza) Nav.replace(Route.Tool(PlazaUi.ID)) else Nav.back()
        if (turnOn) PassSwitch.turnOn(ctx) else TandemPass.refresh(ctx)
    }

    // A new tab's choices only exist once it has drawn: the highlight is handed to them then, not before (asked any
    // sooner, it fell on the first thing on screen, the top bar's chips).
    var tabsSeen by remember { mutableIntStateOf(0) }
    LaunchedEffect(tab) {
        if (tabsSeen++ == 0) return@LaunchedEffect
        val part = MeTab.entries[tab].parts.firstOrNull()
        // A few frames: the new tab slides in, and its choices join the screen's focus list as they appear.
        repeat(6) {
            androidx.compose.runtime.withFrameNanos { }
            val ok = if (part != null) PageFocus.request(ROUTE, "${part.name}:${PlazaStore.data.me.avatar[part]}") else false
            if (ok) return@LaunchedEffect
        }
        Nav.focusTick++
    }
    // Pip's guide never holds the buttons back: doing what a step says moves it on (L1/R1, then X or Y, then the Card tab).
    InterceptButtons(Nav.route == Route.Tool(MeEditor.ROUTE_ID) && !Nav.overlayOpen && Layers.top == null) { b ->
        when (b) {
            Button.L1, Button.R1 -> {
                // The shoulder on the side the next tab is: in Arabic the tabs run from the right.
                tab = (tab + Sides.step(left = b == Button.L1, rtl = rtlNow) + MeTab.entries.size) % MeTab.entries.size
                if (MeEditor.guide == 0) MeEditor.guide = 1
                if (MeEditor.guide == 2 && MeTab.entries[tab] == MeTab.entries.last()) MeEditor.guide = -1
                FocusMemory.forget(ROUTE)
                Sounds.page()
                true
            }
            Button.X -> { change(me.copy(avatar = AvatarSpec.random())); Sounds.confirm(); if (MeEditor.guide == 1) MeEditor.guide = 2; true }
            Button.Y -> {
                if (MeEditor.guide == 1) MeEditor.guide = 2
                val last = undo.removeLastOrNull()
                if (last == null) Toasts.show(Tx.s(R.string.plaza_undo_none)) else { PlazaStore.update { it.copy(me = last) }; edits++; Sounds.undo() }
                true
            }
            else -> false
        }
    }

    Row(Modifier.fillMaxSize().padding(start = 18.dp, end = 10.dp, bottom = 8.dp)) {
        // The stage: you, big, with your greeting and name.
        Box(Modifier.width(296.dp).fillMaxHeight().padding(top = 8.dp, bottom = 6.dp)) {
            MeStage(me, edits, Modifier.fillMaxSize())
            if (MeEditor.guide >= 0) PipGuide(Modifier.align(Alignment.BottomCenter).padding(10.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).fillMaxHeight()) {
            MeTabs(tab) { tab = it; FocusMemory.forget(ROUTE); Nav.focusTick++ }
            // Reads the card when picked, so the same lambda serves every look and the tabs' choices can skip.
            val pickPart = remember { { p: AvatarPart, v: Int -> val now = PlazaStore.data.me; change(now.copy(avatar = now.avatar.with(p, v))) } }
            TabStack(current, Modifier.weight(1f).fillMaxWidth()) { t, active ->
                if (t == MeTab.Card) MeCardTab(me) { change(it) }
                else {
                    // A hidden tab keeps the look it last showed and catches up when it shows again, so a change
                    // isn't composed seven times over behind the tab on screen.
                    val shown = remember { arrayOf(me.avatar) }
                    if (active) shown[0] = me.avatar
                    PartsTab(t, active, shown[0], pickPart)
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp, start = 4.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                ButtonHint("X", Tx.s(R.string.plaza_hint_random), Palette.Butter)
                ButtonHint("Y", Tx.s(R.string.plaza_hint_undo), Palette.Peach)
                ButtonHint("B", Tx.s(R.string.plaza_hint_done), Palette.Mint)
            }
        }
    }
}

/** The big preview: a soft spotlight in the card's colour, the character, the greeting in a bubble, the name. */
@Composable
private fun MeStage(me: MeCard, edits: Int, modifier: Modifier) {
    val theme = avatarTheme(me.avatar)
    Box(
        modifier.clip(RoundedCornerShape(28.dp))
            .background(Brush.verticalGradient(listOf(theme.copy(alpha = 0.34f), Palette.Card.copy(alpha = 0.6f), theme.copy(alpha = 0.22f)))),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            // A floor disc and a few soft dots of light.
            drawOval(theme.copy(alpha = 0.35f), Offset(size.width * 0.12f, size.height * 0.74f), androidx.compose.ui.geometry.Size(size.width * 0.76f, size.height * 0.14f))
            drawCircle(Color.White.copy(alpha = 0.08f), size.width * 0.42f, Offset(size.width * 0.5f, size.height * 0.42f))
        }
        Column(Modifier.fillMaxSize().padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            GreetingBubble(Tx.s(greetingRes(me.greeting)), theme)
            AvatarView(me.avatar, Modifier.weight(1f).fillMaxWidth().padding(top = 4.dp), react = edits, waving = edits == 0, headroom = avatarHeadroom(me.avatar))
            Spacer(Modifier.height(6.dp))
            NameTag(displayName(me.name), theme)
        }
    }
}

/** A name as it shows: the one given, or "Friend". */
fun displayName(name: String): String = CardCodec.cleanName(name).ifEmpty { Tx.s(R.string.plaza_unnamed) }

@Composable
internal fun GreetingBubble(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier.widthIn(max = 260.dp).clip(RoundedCornerShape(16.dp)).background(Palette.CardHigh)
            .border(1.5.dp, color.copy(alpha = 0.7f), RoundedCornerShape(16.dp)).padding(horizontal = 12.dp, vertical = 6.dp),
    ) { Text(text, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center) }
}

@Composable
internal fun NameTag(name: String, color: Color, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(50)).background(color).padding(horizontal = 16.dp, vertical = 5.dp)) {
        FitText(name, MaterialTheme.typography.titleMedium, color = Color(0xFF2A2235))
    }
}

@Composable
private fun MeTabs(tab: Int, pick: (Int) -> Unit) {
    // The shoulders sit where they are on the handheld, whichever way the tabs run.
    val rtl = isRtl
    Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        ButtonHint(if (rtl) "R1" else "L1", "", Palette.Muted)
        Row(Modifier.weight(1f).padding(horizontal = 6.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            MeTab.entries.forEachIndexed { i, t ->
                val on = i == tab
                Box(
                    Modifier.weight(1f).height(30.dp).clip(RoundedCornerShape(50))
                        .background(if (on) Palette.Accent else Palette.Card.copy(alpha = 0.7f))
                        .pointerInput(i) { detectTapGestures { pick(i); Sounds.page() } },
                    contentAlignment = Alignment.Center,
                ) { FitText(Tx.s(t.title), MaterialTheme.typography.labelMedium, Modifier.padding(horizontal = 3.dp), color = if (on) Palette.OnAccent else Palette.Text.copy(alpha = 0.75f)) }
            }
        }
        ButtonHint(if (rtl) "L1" else "R1", "", Palette.Muted)
    }
}

/** A tab's place in [TabStack]: its focus layer, its fade and slide, and whether it may be measured while hidden. */
private class TabSlot(shown: Boolean) {
    val layer = KeptLayer().apply { dormant = !shown }
    val alpha = androidx.compose.animation.core.Animatable(if (shown) 1f else 0f)
    val slide = androidx.compose.animation.core.Animatable(0f)
    var ready by mutableStateOf(shown)
}

/**
 * The tabs, each kept composed once made, the one on screen placed and the rest hidden, as [KeptScreen] keeps a screen:
 * not placed (so neither drawn nor reached by focus search), measured ahead, their items in a [KeptLayer] that takes
 * no focus while dormant. A tab switch was a whole new tab (some forty choice cards) composed and measured in the
 * frame the slide starts, on every visit; now it shows a tab that is already there, and the choices that don't change
 * skip. The tabs not seen yet are made ahead, nearest first, one every few frames at a quiet moment after Me opens.
 * Same look as before: the new tab fades in over 200 ms rising from a fourteenth of its height (the spring of
 * slideInVertically), the old one fades out over 120 ms.
 */
@Composable
private fun TabStack(current: MeTab, modifier: Modifier, content: @Composable (tab: MeTab, active: Boolean) -> Unit) {
    val slots = remember { HashMap<MeTab, TabSlot>() }
    // Whether the stack has drawn: the tab Me opens on shows at once, like AnimatedContent's first content.
    val opened = remember { booleanArrayOf(false) }
    androidx.compose.runtime.SideEffect { opened[0] = true }
    val ahead = remember { mutableStateListOf<MeTab>() }
    if (current !in slots) slots[current] = TabSlot(shown = !opened[0])
    for (t in ahead) if (t !in slots) slots[t] = TabSlot(shown = false)
    val now by androidx.compose.runtime.rememberUpdatedState(current)
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(350)
        val all = MeTab.entries
        while (true) {
            // Only while the buttons rest: a made tab costs a long frame, which must not land on a highlight moving.
            while (android.os.SystemClock.elapsedRealtime() - io.github.zahakj.tandem.Idle.last < 200) kotlinx.coroutines.delay(100)
            val from = now.ordinal
            val next = all.filter { it !in slots && it !in ahead }
                .minByOrNull { t -> minOf((t.ordinal - from + all.size) % all.size, (from - t.ordinal + all.size) % all.size) } ?: break
            ahead += next
            repeat(3) { androidx.compose.runtime.withFrameNanos { } }
        }
    }
    androidx.compose.ui.layout.Layout(
        content = {
            for (t in MeTab.entries) {
                val slot = slots[t] ?: continue
                androidx.compose.runtime.key(t) {
                    val shown = t == current
                    androidx.compose.runtime.SideEffect { slot.layer.dormant = !shown }
                    if (!slot.ready) LaunchedEffect(Unit) { androidx.compose.runtime.withFrameNanos { }; slot.ready = true }
                    LaunchedEffect(shown) {
                        if (shown) {
                            if (slot.alpha.value >= 1f) return@LaunchedEffect
                            launch {
                                slot.slide.snapTo(1f / 14f)
                                slot.slide.animateTo(0f, androidx.compose.animation.core.spring(stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow, visibilityThreshold = 0.0005f))
                            }
                            slot.alpha.animateTo(1f, Motion.fade(200))
                        } else {
                            slot.alpha.animateTo(0f, Motion.fade(120))
                            slot.slide.snapTo(0f)
                        }
                    }
                    Box(
                        Modifier.layoutId(t).fillMaxSize().graphicsLayer {
                            alpha = slot.alpha.value
                            translationY = slot.slide.value * size.height
                        },
                    ) {
                        androidx.compose.runtime.CompositionLocalProvider(LocalLayer provides slot.layer) { content(t, shown) }
                    }
                }
            }
        },
        modifier = modifier,
    ) { measurables, c ->
        val placeables = measurables.map { m ->
            val t = m.layoutId as MeTab
            val slot = slots.getValue(t)
            // Hidden, it is measured from the frame after it is made, so making and measuring land in different frames.
            if (t == current || slot.ready) t to m.measure(c) else null
        }
        layout(c.maxWidth, c.maxHeight) {
            for (p in placeables) {
                if (p == null) continue
                val (t, placeable) = p
                // Placed while on screen or fading out; read here, so the fade re-places rather than recomposes.
                if (t == current || slots.getValue(t).alpha.value > 0f) placeable.place(0, 0, if (t == current) 1f else 0f)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PartsTab(tab: MeTab, active: Boolean, avatar: AvatarSpec, pick: (AvatarPart, Int) -> Unit) {
    // Each part's focus keys, made once per tab.
    val keysByPart = remember(tab) { tab.parts.map { p -> List(p.count) { "${p.name}:$it" } } }
    val keys = remember(tab) { keysByPart.flatten() }
    // The highlight starts on what you wear now. Only the tab on screen has a target: a hidden one (kept composed by
    // TabStack) must not touch the screen's remembered item, which every tab shares.
    val worn = tab.parts.firstOrNull()?.let { "${it.name}:${avatar[it]}" }
    if (active && worn != null && FocusMemory.get(ROUTE) !in keys) FocusMemory.remember(ROUTE, worn)
    val target = if (active) rememberFocusTarget(ROUTE, keys) else null
    val state = rememberRouteListState("$ROUTE:${tab.name}")
    val section = tab.parts.indexOfFirst { p -> target?.startsWith(p.name + ":") == true }
    KeepInView(state, if (active) section.coerceAtLeast(0) else -1)
    LazyColumn(
        Modifier.fillMaxSize().fadingEdges(state), state = state,
        contentPadding = PaddingValues(horizontal = FocusRoomSide, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        itemsIndexed(tab.parts, key = { _, p -> p.name }) { i, p ->
            Column {
                SectionTitle(Tx.s(partTitle(p)), Palette.Accent)
                FlowRow(
                    Modifier.fillMaxWidth().padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    val partKeys = keysByPart[i]
                    for (v in 0 until p.count) {
                        val key = partKeys[v]
                        PartChip(p, v, avatar, v == avatar[p], key, key == target, pick)
                    }
                }
            }
        }
    }
}

/** One choice: a swatch for colours, else a small drawing of you with it. */
@Composable
// Plain values only (the focus modifier is made inside), so a card whose choice, look and target are unchanged skips.
private fun PartChip(part: AvatarPart, value: Int, avatar: AvatarSpec, on: Boolean, key: String, wanted: Boolean, pick: (AvatarPart, Int) -> Unit) {
    val color = swatch(part, value)
    val chip = if (color != null) 46.dp else 62.dp
    val shape = if (color != null) CircleShape else ChipShape
    val label = Tx.s(R.string.plaza_option_n, value + 1, part.count)
    FocusCard(
        Modifier.takesFocus(ROUTE, key, wanted).size(chip).semantics { contentDescription = label },
        glow = Palette.Accent, shape = shape, lifts = false,
        onFocus = { Selection.current = Selected.Home },
        onClick = { pick(part, value) },
    ) {
        if (color != null) {
            Box(Modifier.fillMaxSize().padding(5.dp).clip(CircleShape).background(color))
        } else {
            // Face shapes show the jaw, and earrings the ears: no hat, and hair that falls past the cheeks is swapped
            // for a short cut.
            val shown = if (part == AvatarPart.Shape || part == AvatarPart.Earrings) {
                avatar.with(part, value).copy(headwear = 0, hair = if (avatar.hair in LONG_HAIR) 1 else avatar.hair)
            } else {
                avatar.with(part, value)
            }
            val z = zoomOf(part)
            Box(Modifier.fillMaxSize().background(if (on) Palette.Accent.copy(alpha = 0.22f) else Palette.Card.copy(alpha = 0.4f))) {
                Canvas(Modifier.fillMaxSize()) {
                    ChipPictures.ready // read here, so the chip draws again when its texture is made
                    when (val made = ChipPictures.of(shown, z, this)) {
                        is android.graphics.Bitmap -> drawIntoCanvas { it.nativeCanvas.drawBitmap(made, 0f, 0f, null) }
                        is android.graphics.Picture -> drawIntoCanvas { it.nativeCanvas.drawPicture(made) }
                    }
                }
            }
        }
        if (on) TickBadge(true, Palette.Accent, Modifier.align(Alignment.TopEnd).padding(2.dp), size = 16.dp)
    }
}

private val ChipShape = RoundedCornerShape(16.dp)

/** A choice's drawing: you in [shown], zoomed on the part ([z]), in this scope. */
private fun DrawScope.chipDrawing(shown: AvatarSpec, z: Zoom) {
    val u = kotlin.math.min(size.width / 100f, size.height / 120f)
    val ox = (size.width - 100f * u) / 2f
    val oy = size.height - 120f * u
    val pivot = Offset(ox + z.x * u, oy + z.y * u)
    withTransform({
        translate(size.width / 2f - pivot.x, size.height / 2f - pivot.y)
        scale(z.scale, z.scale, pivot)
    }) { drawAvatar(shown, shadow = z == Zoom.Whole) }
}

/**
 * The choices' drawings, made once each and kept while Me is open. A tab shows up to forty of them, each a whole
 * character of a few hundred shapes. Recorded, then turned into a picture on the GPU (a hardware bitmap), so a frame
 * draws forty textures: replaying the recordings cost the render thread 8 to 16 ms a frame while a tab slid in.
 */
private object ChipPictures {
    private data class Key(val spec: AvatarSpec, val z: Zoom, val w: Float, val h: Float, val density: Float, val dir: LayoutDirection)

    /** About 48 MB of them: every choice of every tab, with room to spare (a new look draws them again). */
    private val made = object : android.util.LruCache<Key, android.graphics.Bitmap>(48 * 1024 * 1024) {
        override fun sizeOf(key: Key, value: android.graphics.Bitmap) = value.allocationByteCount
    }
    private val scope = CanvasDrawScope()
    /** Recordings whose texture is being made, drawn as they are until it is. */
    private val pending = HashMap<Key, android.graphics.Picture>()
    private val maker = java.util.concurrent.Executors.newSingleThreadExecutor()
    private val main = android.os.Handler(android.os.Looper.getMainLooper())

    /** Bumped when a texture is ready, so the chips showing its recording draw it instead. */
    var ready by androidx.compose.runtime.mutableIntStateOf(0)
        private set

    /**
     * The texture, or the recording for now: making forty textures in the frame a tab first slides in held that frame
     * for over 100 ms, so they are made on another thread and the recording stands in until then.
     */
    fun of(shown: AvatarSpec, z: Zoom, on: DrawScope): Any {
        val key = Key(shown, z, on.size.width, on.size.height, on.density, on.layoutDirection)
        made.get(key)?.let { return it }
        pending[key]?.let { return it }
        val picture = android.graphics.Picture()
        val canvas = picture.beginRecording(kotlin.math.ceil(on.size.width).toInt().coerceAtLeast(1), kotlin.math.ceil(on.size.height).toInt().coerceAtLeast(1))
        scope.draw(on, on.layoutDirection, androidx.compose.ui.graphics.Canvas(canvas), on.size) { chipDrawing(shown, z) }
        picture.endRecording()
        pending[key] = picture
        maker.execute {
            val bitmap = runCatching { android.graphics.Bitmap.createBitmap(picture) }.getOrNull()
            main.post {
                if (pending.remove(key) != null && bitmap != null) { made.put(key, bitmap); ready++ }
            }
        }
        return picture
    }

    fun clear() { made.evictAll(); pending.clear() }
}

// --- The card tab ------------------------------------------------------------------------------------------------

@Composable
private fun MeCardTab(me: MeCard, change: (MeCard) -> Unit) {
    val keys = listOf("name", "greeting", "favorite", "recent")
    val target = rememberFocusTarget("$ROUTE:card", keys)
    var picking by remember { mutableStateOf<String?>(null) }
    val fav = me.favorite?.let { Libraries.byKey(it) }?.takeIf { !Privacy.isHidden(it) }
    Column(
        Modifier.fillMaxSize().padding(horizontal = FocusRoomSide, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        MenuRow(
            Tx.s(R.string.plaza_card_name), displayName(me.name).takeIf { me.name.isNotBlank() } ?: Tx.s(R.string.plaza_card_name_empty),
            "name", "$ROUTE:card", target, Palette.Peach, Glyph.Text,
        ) {
            TextEntry.ask(Tx.s(R.string.plaza_card_name_ask), me.name) { typed -> change(me.copy(name = CardCodec.cleanName(typed))) }
        }
        MenuRow(Tx.s(R.string.plaza_card_greeting), Tx.s(greetingRes(me.greeting)), "greeting", "$ROUTE:card", target, Palette.Sky, Glyph.Bell) { picking = "greeting" }
        MenuRow(
            Tx.s(R.string.plaza_card_favorite), fav?.title ?: Tx.s(R.string.plaza_card_favorite_none),
            "favorite", "$ROUTE:card", target, Palette.Lavender, Glyph.Heart,
        ) { picking = "favorite" }
        MenuRow(
            Tx.s(R.string.plaza_card_recent), onOff(me.shareRecent), "recent", "$ROUTE:card", target, Palette.Mint, Glyph.Clock,
        ) { change(me.copy(shareRecent = !me.shareRecent)); Sounds.toggle(!me.shareRecent) }
        Text(Tx.s(R.string.plaza_card_note), style = MaterialTheme.typography.bodySmall, color = Palette.Muted, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 6.dp))
    }
    GreetingPicker(picking == "greeting", me.greeting, { picking = null }) { change(me.copy(greeting = it)) }
    FavoritePicker(picking == "favorite", me.favorite, { picking = null }) { change(me.copy(favorite = it)) }
}

@StringRes
fun greetingRes(id: Int): Int = GreetingStrings[Greetings.known(id) - 1]

private val GreetingStrings = listOf(
    R.string.plaza_greeting_1, R.string.plaza_greeting_2, R.string.plaza_greeting_3, R.string.plaza_greeting_4,
    R.string.plaza_greeting_5, R.string.plaza_greeting_6, R.string.plaza_greeting_7, R.string.plaza_greeting_8,
    R.string.plaza_greeting_9, R.string.plaza_greeting_10, R.string.plaza_greeting_11, R.string.plaza_greeting_12,
    R.string.plaza_greeting_13, R.string.plaza_greeting_14, R.string.plaza_greeting_15, R.string.plaza_greeting_16,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GreetingPicker(open: Boolean, current: Int, close: () -> Unit, pick: (Int) -> Unit) {
    val route = "$ROUTE:greetings"
    PanelGuard(open)
    OverlayPanel(open, onClose = close) {
        val keys = Greetings.ids.map { "g$it" }
        val target = rememberFocusTarget(route, keys) ?: "g$current"
        PanelSurface(Modifier.width(620.dp)) {
            Text(Tx.s(R.string.plaza_card_greeting_pick), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 6.dp, bottom = 6.dp))
            FlowRow(
                Modifier.fillMaxWidth().padding(FocusRoomSide / 2),
                horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp), maxItemsInEachRow = 4,
            ) {
                for (id in Greetings.ids) {
                    val on = id == current
                    FocusCard(
                        Modifier.weight(1f).height(56.dp).takesFocus(route, "g$id", "g$id" == target),
                        glow = Palette.Sky, shape = RoundedCornerShape(16.dp), lifts = false,
                        onClick = { pick(id); close() },
                    ) {
                        Box(
                            Modifier.fillMaxSize().background(if (on) Palette.Sky.copy(alpha = 0.3f) else Color.Transparent).padding(horizontal = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) { ShrinkText(Tx.s(greetingRes(id)), MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center) }
                    }
                }
            }
        }
    }
}

/** Your favourites first, then what you play most and what you added lately: never a hidden game. */
private fun favoriteChoices(): List<Game> =
    (Collections.favorites() + Collections.mostPlayed().take(30) + Collections.recentlyAdded())
        .filter { !Privacy.isHidden(it) }.distinctBy { it.key }.take(80)

@Composable
private fun FavoritePicker(open: Boolean, current: String?, close: () -> Unit, pick: (String?) -> Unit) {
    val route = "$ROUTE:favorite"
    PanelGuard(open)
    OverlayPanel(open, onClose = close) {
        val games = remember(Libraries.all, io.github.zahakj.tandem.data.Store.meta) { favoriteChoices() }
        val keys = listOf("none") + games.map { it.key }
        val target = rememberFocusTarget(route, keys) ?: (current?.takeIf { c -> games.any { it.key == c } } ?: "none")
        PanelSurface(Modifier.width(520.dp).heightIn(max = 420.dp)) {
            Text(Tx.s(R.string.plaza_card_favorite_pick), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 6.dp, bottom = 4.dp))
            val state = rememberRouteListState(route)
            KeepInView(state, keys.indexOf(target))
            LazyColumn(
                Modifier.weight(1f, fill = false).fadingEdges(state), state = state,
                contentPadding = PaddingValues(horizontal = FocusRoomSide, vertical = FocusRoom), verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(keys, key = { _, k -> k }) { i, k ->
                    val g = games.getOrNull(i - 1)
                    ToolRow(
                        g?.title ?: Tx.s(R.string.plaza_card_favorite_clear), g?.platform?.name, if (k == current || (g == null && current == null)) Palette.Accent else Palette.Lavender,
                        Modifier.takesFocus(route, k, k == target), tag = if (k == current || (g == null && current == null)) "✓" else null, height = 56.dp,
                        onClick = { pick(g?.key); close() },
                    ) { if (g != null) GameArt(g) else TandemIconTile(TIcon.Heart, Palette.Lavender) }
                }
            }
        }
    }
}

// --- Pip's three steps -------------------------------------------------------------------------------------------

@Composable
private fun PipGuide(modifier: Modifier) {
    val step = MeEditor.guide
    val lines = listOf(R.string.plaza_guide_1, R.string.plaza_guide_2, R.string.plaza_guide_3)
    AnimatedVisibility(step >= 0, modifier, enter = fadeIn() + slideInVertically { it / 3 }, exit = fadeOut() + slideOutVertically { it / 3 }) {
        val s = step.coerceIn(0, 2)
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Palette.CardHigh).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Pip(40.dp, mood = 1)
                Spacer(Modifier.width(8.dp))
                Text(Tx.s(lines[s]), style = MaterialTheme.typography.bodyMedium, maxLines = 4, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(Tx.s(R.string.plaza_guide_step, s + 1, 3), style = MaterialTheme.typography.labelMedium, color = Palette.Muted, modifier = Modifier.weight(1f))
                PillButton(
                    Tx.s(if (s < 2) R.string.plaza_guide_next else R.string.plaza_guide_go), Palette.Accent,
                    Modifier.takesFocus("$ROUTE:guide", "next", true), filled = true,
                ) {
                    if (s < 2) MeEditor.guide = s + 1 else { MeEditor.guide = -1; Nav.focusTick++ }
                }
            }
        }
    }
}

/** The little "you" used where a face of the owner shows (Settings' preview, the Plaza's own spot). */
@Composable
fun MeFace(modifier: Modifier = Modifier, size: Dp = 48.dp) {
    Canvas(modifier.size(size)) { drawAvatar(PlazaStore.data.me.avatar, shadow = false) }
}
