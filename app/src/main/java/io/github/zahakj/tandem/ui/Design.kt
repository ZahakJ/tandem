package io.github.zahakj.tandem.ui

import kotlinx.coroutines.flow.first
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.zahakj.tandem.R
import io.github.zahakj.tandem.data.Store
import kotlin.math.min

/**
 * Tandem's colours. They come from the live theme ([Themes.live]), so every screen follows theme changes. The
 * default theme, Pastel Night, is a soft night sky with pastel accents.
 */
object Palette {
    val Night get() = Themes.live.background
    val NightDeep get() = Themes.live.backgroundDeep
    val Card get() = Themes.live.card
    val CardHigh get() = Themes.live.cardHigh
    val Peach get() = Themes.live.peach
    val Mint get() = Themes.live.mint
    val Lavender get() = Themes.live.lavender
    val Sky get() = Themes.live.sky
    val Butter get() = Themes.live.butter
    val Text get() = Themes.live.text
    val Muted get() = Themes.live.muted
    /** Text on top of a bright accent tile. */
    val OnAccent get() = Themes.live.onAccent
    /** The theme's (or the user's own) main accent. */
    val Accent get() = Themes.live.accent
    val accents get() = Themes.live.accents

    /** The standard card shape for the current theme. */
    val cardShape: Shape get() = RoundedCornerShape(Themes.live.corner.dp)

    /** A stable pastel for anything with a name, so each system and folder keeps its own colour. */
    fun forName(name: String): Color = accents[(name.hashCode() and 0x7fffffff) % accents.size]

    /** The highlight colour for an item whose own colour is [own]: its own, or the accent if the user prefers that. */
    fun highlightFor(own: Color): Color = if (Store.settings.look.highlightColor == "accent") Accent else own
}

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun nunito(weight: Int) = Font(
    R.font.nunito,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val Nunito = FontFamily(nunito(400), nunito(600), nunito(700), nunito(800), nunito(900))

private val TandemTypography = Typography().let { t ->
    fun TextStyle.n() = copy(fontFamily = Nunito)
    Typography(
        displaySmall = t.displaySmall.n().copy(fontWeight = FontWeight.Black, fontSize = 30.sp),
        headlineSmall = t.headlineSmall.n().copy(fontWeight = FontWeight.ExtraBold, fontSize = 22.sp),
        titleLarge = t.titleLarge.n().copy(fontWeight = FontWeight.ExtraBold, fontSize = 18.sp),
        titleMedium = t.titleMedium.n().copy(fontWeight = FontWeight.Bold, fontSize = 15.sp),
        titleSmall = t.titleSmall.n().copy(fontWeight = FontWeight.Bold, fontSize = 13.sp),
        bodyLarge = t.bodyLarge.n().copy(fontSize = 15.sp),
        bodyMedium = t.bodyMedium.n().copy(fontSize = 13.sp, lineHeight = 18.sp),
        bodySmall = t.bodySmall.n().copy(fontSize = 11.sp),
        labelLarge = t.labelLarge.n().copy(fontWeight = FontWeight.Bold, fontSize = 13.sp),
        labelMedium = t.labelMedium.n().copy(fontWeight = FontWeight.Bold, fontSize = 11.sp),
        labelSmall = t.labelSmall.n().copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
    )
}

/** True inside a [SwapStage], whose wallpaper sits behind the content. */
val LocalOnStage = androidx.compose.runtime.staticCompositionLocalOf { false }

@Composable
fun TandemTheme(content: @Composable () -> Unit) {
    val t = Themes.live
    val scheme = if (t.dark) {
        darkColorScheme(
            primary = t.accent, secondary = t.peach, tertiary = t.lavender,
            background = t.background, surface = t.background, onBackground = t.text, onSurface = t.text,
        )
    } else {
        lightColorScheme(
            primary = t.accent, secondary = t.peach, tertiary = t.lavender,
            background = t.background, surface = t.background, onBackground = t.text, onSurface = t.text,
        )
    }
    MaterialTheme(colorScheme = scheme, typography = TandemTypography) {
        // On a screen stage the wallpaper (picture, video or drawn backdrop) is already behind everything, so the
        // theme only sets colours and type; painting its background here would cover the wallpaper.
        Surface(Modifier.fillMaxSize(), color = if (LocalOnStage.current) Color.Transparent else t.background, contentColor = t.text) {
            content()
        }
    }
}

/** Slowly drifting pastel blobs behind everything; [tint] nudges them toward the highlighted item's colour. */
@Composable
fun AnimatedBackdrop(tint: Color = Palette.Lavender, modifier: Modifier = Modifier) =
    Backdrop(BackdropStyle.Blobs, tint, modifier)

/**
 * Pip, Tandem's mascot: a tiny two-screen handheld with a face. It bobs gently and blinks now and then.
 * [mood] changes its expression: 0 = happy, 1 = excited (sparkly eyes), 2 = sleepy, 3 = worried (raised brows, a
 * small frown).
 */
@Composable
fun Pip(size: Dp, modifier: Modifier = Modifier, mood: Int = 0, bounce: Boolean = true) {
    // Pip reacts: a glance the way you move, a happy hop when a game starts, sleepy eyes in the small hours.
    // A Pip on a hidden screen (a kept screen, the top bar while it's away) doesn't glance: its animation would still
    // make the whole window draw again. Nor does one on a screen behind a popup: the popup blurs that screen, and
    // every frame of a glance there meant blurring the whole screen again.
    val layer = LocalLayer.current
    val behindPopup = !LocalInScreensaver.current && (layer == null || layer is KeptLayer) && (Layers.top != null || Nav.overlayOpen)
    val awake = rememberResumed() && !behindPopup
    // Glances and blinks run on AmbientFrames: on every frame while Tandem is in use, on the ambient clock's ticks once
    // idle, so they never add frames of their own between the idle motion's.
    val lookX = remember { Animatable(0f) }
    val lookY = remember { Animatable(0f) }
    val toX = if (awake) PipMind.lookX else 0f
    val toY = if (awake) PipMind.lookY else 0f
    LaunchedEffect(toX) { kotlinx.coroutines.withContext(AmbientFrames) { lookX.animateTo(toX, androidx.compose.animation.core.spring(0.6f, 400f)) } }
    LaunchedEffect(toY) { kotlinx.coroutines.withContext(AmbientFrames) { lookY.animateTo(toY, androidx.compose.animation.core.spring(0.6f, 400f)) } }
    val hop = remember { Animatable(0f) }
    LaunchedEffect(PipMind.cheers) {
        if (PipMind.cheers == 0 || Themes.still) return@LaunchedEffect
        hop.snapTo(0f); hop.animateTo(1f, tween(170)); hop.animateTo(0f, androidx.compose.animation.core.spring(0.35f, 300f))
    }
    val night = remember { java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) in 1..5 }
    @Suppress("NAME_SHADOWING") val mood = if (mood == 0 && night) 2 else mood
    // The bob runs on the shared ambient clock's "lively" time (Ambient.kt): read only in the layer below, and it
    // rests once Tandem has been idle for a while (blinks and glances carry on).
    val bobbing = rememberAmbient(bounce && !Themes.still, livelyOnly = true)
    // Blinks every two to four seconds, never in step with another Pip, and now and then twice in a row. Runs
    // wherever Pip is drawn (top screen, bottom screen, panels, menus), only while that screen is showing.
    val blinkAnim = remember { Animatable(1f) }
    val still = Themes.still
    LaunchedEffect(still, awake) {
        if (still || !awake) { blinkAnim.snapTo(1f); return@LaunchedEffect }
        val rnd = java.util.Random()
        kotlinx.coroutines.delay(400L + rnd.nextInt(2200))
        while (true) {
            // After a long idle Pip rests with its eyes open, until the next input.
            if (Ambient.resting) androidx.compose.runtime.snapshotFlow { Ambient.resting }.first { !it }
            val times = if (rnd.nextInt(5) == 0) 2 else 1
            kotlinx.coroutines.withContext(AmbientFrames) {
                repeat(times) { i ->
                    blinkAnim.animateTo(0.08f, tween(65))
                    blinkAnim.animateTo(1f, tween(95))
                    if (i < times - 1) kotlinx.coroutines.delay(110)
                }
            }
            // Calmer once idle: every blink is a few frames of its screen.
            kotlinx.coroutines.delay(if (Ambient.idleNow()) 4000L + rnd.nextInt(3000) else 2000L + rnd.nextInt(2000))
        }
    }
    Canvas(modifier.size(size).graphicsLayer { translationY = (if (bobbing) -Ambient.pingPong(1400, lively = true) * size.toPx() * 0.06f else 0f) - hop.value * size.toPx() * 0.22f; rotationZ = hop.value * 6f }) {
        val w = this.size.width
        val h = this.size.height
        val r = CornerRadius(w * 0.14f)
        val top = Size(w * 0.84f, h * 0.44f)
        val bottom = Size(w * 0.72f, h * 0.36f)
        val topAt = Offset((w - top.width) / 2, h * 0.04f)
        val bottomAt = Offset((w - bottom.width) / 2, h * 0.56f)
        // Pip keeps its own dark screens even in light themes, so its face always reads.
        val screen = if (Themes.live.dark) Palette.NightDeep else Palette.Text
        val screen2 = if (Themes.live.dark) Palette.Night else Palette.Text.copy(alpha = 0.85f)
        val face = if (Themes.live.dark) Palette.Text else Palette.Night
        // Hinge
        drawRoundRect(Palette.Lavender.copy(alpha = 0.7f), Offset(w * 0.3f, h * 0.47f), Size(w * 0.4f, h * 0.1f), CornerRadius(w * 0.05f))
        // Body halves
        drawRoundRect(Palette.Mint, topAt, top, r)
        drawRoundRect(Palette.Sky, bottomAt, bottom, r)
        // Screens
        val inset = w * 0.07f
        drawRoundRect(screen, topAt + Offset(inset, inset), Size(top.width - 2 * inset, top.height - 2 * inset), CornerRadius(w * 0.08f))
        drawRoundRect(screen2, bottomAt + Offset(inset, inset * 0.8f), Size(bottom.width - 2 * inset, bottom.height - 1.6f * inset), CornerRadius(w * 0.07f))
        // Face on the top screen
        val eyeY = topAt.y + top.height * 0.48f
        val eyeDx = top.width * 0.2f
        val cx = w / 2
        // Big round eyes (white) with large dark pupils that travel far, so glances read even when Pip is small.
        val eyeR = w * 0.068f
        val open = if (still) 1f else blinkAnim.value
        for (side in listOf(-1, 1)) {
            val c = Offset(cx + side * eyeDx + lookX.value * eyeR * 0.25f, eyeY + lookY.value * eyeR * 0.2f)
            val pupil = Offset(lookX.value * eyeR * 0.5f, lookY.value * eyeR * 0.4f)
            when {
                mood == 2 -> drawLine(face, c - Offset(eyeR, 0f), c + Offset(eyeR, 0f), strokeWidth = w * 0.035f, cap = StrokeCap.Round)
                open < 0.35f -> drawArc(
                    // A closed eye: a happy little curve, clearly visible as a blink.
                    face, 10f, 160f, false, topLeft = c - Offset(eyeR, eyeR * 0.6f), size = Size(eyeR * 2, eyeR * 1.2f),
                    style = Stroke(width = w * 0.035f, cap = StrokeCap.Round),
                )
                else -> {
                    drawOval(face, c - Offset(eyeR, eyeR * open), Size(eyeR * 2, eyeR * 2 * open))
                    drawCircle(screen, eyeR * 0.58f * open.coerceAtLeast(0.6f), c + pupil)
                    // Sparkle stays put while the pupil moves, so the eye looks wet and alive.
                    drawCircle(face, eyeR * 0.2f, c + pupil + Offset(eyeR * 0.22f, -eyeR * 0.24f))
                }
            }
        }
        // Cheeks and smile
        for (side in listOf(-1, 1)) {
            drawCircle(Palette.Peach.copy(alpha = 0.75f), w * 0.04f, Offset(cx + side * eyeDx * 1.55f, eyeY + w * 0.06f))
        }
        if (mood == 3) {
            // Worried: brows raised towards the middle, and a small frown instead of the smile.
            for (side in listOf(-1, 1)) {
                val bx = cx + side * eyeDx
                val by = eyeY - eyeR * 1.6f
                drawLine(face, Offset(bx + side * eyeR * 1.1f, by + eyeR * 0.35f), Offset(bx - side * eyeR * 0.9f, by - eyeR * 0.35f), strokeWidth = w * 0.028f, cap = StrokeCap.Round)
            }
            drawArc(
                face, 205f, 130f, false,
                topLeft = Offset(cx - w * 0.055f, eyeY + w * 0.075f), size = Size(w * 0.11f, w * 0.07f),
                style = Stroke(width = w * 0.03f, cap = StrokeCap.Round),
            )
        } else drawArc(
            face, 20f, 140f, false,
            topLeft = Offset(cx - w * 0.07f, eyeY + w * 0.01f), size = Size(w * 0.14f, w * 0.1f),
            style = Stroke(width = w * 0.03f, cap = StrokeCap.Round),
        )
        // D-pad and buttons on the lower half
        val padC = Offset(bottomAt.x + bottom.width * 0.27f, bottomAt.y + bottom.height * 0.52f)
        val arm = w * 0.045f
        drawRoundRect(Palette.Lavender, padC - Offset(arm * 1.5f, arm / 2), Size(arm * 3, arm), CornerRadius(arm / 3))
        drawRoundRect(Palette.Lavender, padC - Offset(arm / 2, arm * 1.5f), Size(arm, arm * 3), CornerRadius(arm / 3))
        drawCircle(Palette.Peach, w * 0.035f, Offset(bottomAt.x + bottom.width * 0.72f, padC.y - arm * 0.6f))
        drawCircle(Palette.Butter, w * 0.035f, Offset(bottomAt.x + bottom.width * 0.82f, padC.y + arm * 0.6f))
    }
}

/** 0.3..1..0.3 every 1.7 s on the ambient clock: the Pulse highlight's strength. */
private object PulseState : State<Float> {
    override val value: Float get() = 0.3f + 0.7f * Ambient.pingPong(850)
}

/** The most a focused card may grow in total width, so large items stay inside their container. */
val MaxFocusGrowth = 14.dp

/** Room to leave around focusable items in lazy lists and grids, so a grown, glowing card is never clipped. */
val FocusPadding = 12.dp

/**
 * The card everything is built from. When highlighted it grows a little, lifts, and is marked in the theme's
 * highlight style (glow, outline, lift or pulse) in [glow]; selecting it gives a little squish.
 *
 * Growth is capped by size: a wide row grows only a couple of percent and never spills out of its panel, while a
 * small tile grows by the theme's full focus scale. All per-frame work happens in layer and draw lambdas, so a
 * focus change never recomposes the card's content.
 *
 * Touch (Touch.kt): an [entry] (a game, system, collection, app, a row of a built-in app) takes two taps, the
 * first highlighting it, and a long press highlights it and presses Y. A control acts on the first tap and takes
 * the focus with it. [onLongPress] gives any card its own long press instead (an entry's replaces the Y press).
 */
@Composable
fun FocusCard(
    modifier: Modifier = Modifier,
    glow: Color = Palette.Mint,
    shape: Shape = Palette.cardShape,
    onFocus: () -> Unit = {},
    entry: Boolean = false,
    onLongPress: (() -> Unit)? = null,
    onClick: () -> Unit,
    content: @Composable BoxScope.(focused: Boolean) -> Unit,
) {
    val theme = Themes.live
    val glowColor = Palette.highlightFor(glow)
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    var pressed by remember { mutableStateOf(false) }
    val requester = remember { FocusRequester() }
    // Set in the same focus callback as onFocus, so a long press knows when the screen has seen the new highlight.
    val hasFocus = remember { booleanArrayOf(false) }
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val tap by rememberUpdatedState(onClick)
    val hold by rememberUpdatedState(onLongPress)
    val holds = entry || onLongPress != null
    // Counted in FocusCards (per window) while it holds the focus, so a lost focus can be noticed and given back.
    var counted by remember { mutableStateOf(false) }
    val view = androidx.compose.ui.platform.LocalView.current
    androidx.compose.runtime.DisposableEffect(view) { onDispose { if (counted) FocusCards.held(view.rootView, hasFocus, false) } }
    val density = LocalDensity.current
    val growPx = with(density) { MaxFocusGrowth.toPx() }
    val focusScale = theme.focusScale
    // How far the card has grown: 0 at rest, 1 highlighted, -1 pressed. The scale itself depends on the card's width
    // (wide cards grow less) and is worked out in the graphics layer from the laid-out size. (Keeping the width in
    // state made every card of a new screen compose twice: once, then again after its first measure.)
    val grow = animateFloatAsState(
        when { pressed -> -1f; focused -> 1f; else -> 0f }, Motion.focus(), label = "card-scale",
    )
    val lift = animateFloatAsState(if (focused) 1f else 0f, Motion.soft(), label = "card-lift")
    // A soft band of light sweeps across the card once each time it's highlighted.
    val shine = remember { Animatable(1f) }
    LaunchedEffect(focused) {
        if (focused && theme.animation != AnimationStyle.Off) { shine.snapTo(0f); shine.animateTo(1f, tween(650, delayMillis = 60, easing = FastOutSlowInEasing)) }
    }
    val style = theme.highlight
    // The Pulse highlight breathes on the shared ambient clock (calmer once idle), read only in the draw lambda.
    val pulsing = rememberAmbient(focused && style == HighlightStyle.Pulse && theme.animation != AnimationStyle.Off)
    val pulse: State<Float>? = if (pulsing) PulseState else null
    // Always at least a 2 dp ring (the Lift style too): a shadow alone barely shows on light themes or over art.
    val thickness = with(density) { theme.highlightThickness.coerceAtLeast(2f).dp.toPx() }
    // A ring that can be seen on any theme: the item colour, pushed towards the text colour when it would melt into
    // the background, with a thin contrasting edge inside so it also reads over bright or busy art.
    val ringColor = if (contrast(glowColor, theme.background) < 2.2f) lerp(glowColor, theme.text, 0.65f) else glowColor
    val edgeInk = if (ringColor.luminance() > 0.5f) Color.Black.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.55f)
    val hairline = with(density) { 1.dp.toPx() }
    val material = theme.material
    val text = theme.text
    val surfaceColor = theme.cardHigh
    val muted = theme.muted
    val shadowDp = when (style) { HighlightStyle.Outline -> 0f; HighlightStyle.Lift -> 14f; else -> 10f }
    val liftDp = if (style == HighlightStyle.Lift) 7f else 4f
    Box(
        modifier
            .liftOrigin()
            // A press whose release never arrived (focus moved away mid-press) must not leave the card squished.
            .onFocusChanged {
                if (it.isFocused != counted) { counted = it.isFocused; FocusCards.held(view.rootView, hasFocus, it.isFocused) }
                hasFocus[0] = it.isFocused
                if (it.isFocused) { onFocus(); Sounds.move() } else pressed = false
            }
            // Focus, keys and taps sit outside the lift and zoom below, so D-pad focus search measures where the
            // card really is: a lifted card made its neighbours look "below" it and Down jumped sideways.
            // Focusable even in touch mode, so a screen can highlight something before the first button press.
            .focusRequester(requester)
            .focusable(interactionSource = interaction)
            .onKeyEvent { e ->
                val select = e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.NumPadEnter || e.key == Key.ButtonA
                if (!select) return@onKeyEvent false
                // Only a release that follows a press on this card counts: the end of a press that started
                // somewhere else (a popup that just closed, the previous screen) must not select this card.
                val wasPressed = pressed
                pressed = e.type == KeyEventType.KeyDown
                if (e.type == KeyEventType.KeyUp && wasPressed) { onClick(); Sounds.select() }
                true
            }
            .pointerInput(entry, holds) {
                detectTapGestures(
                    onPress = { pressed = true; tryAwaitRelease(); pressed = false },
                    onTap = {
                        val highlighted = hasFocus[0]
                        if (!highlighted) runCatching { requester.requestFocus() }
                        // An entry's first tap only highlights it; a second one opens it.
                        if (highlighted || !entry) { tap(); Sounds.select() }
                    },
                    // A long press is never also a tap (detectTapGestures drops the tap once it fires).
                    onLongPress = if (!holds) null else { _: Offset ->
                        Touch.buzz(haptic)
                        val own = hold
                        if (own != null) { if (!hasFocus[0]) runCatching { requester.requestFocus() }; own() }
                        else scope.launch { Touch.holdForY({ hasFocus[0] }) { runCatching { requester.requestFocus() } } }
                    },
                )
            }
            .graphicsLayer {
                val l = lift.value
                val g = grow.value
                val w = size.width
                val maxScale = if (w > 0f) min(focusScale, 1f + growPx / w) else 1f
                val s = if (g >= 0f) 1f + g * (maxScale - 1f) else 1f + g * 0.05f
                scaleX = s; scaleY = s
                translationY = -l * liftDp * this.density
                this.shape = shape
                clip = false
                shadowElevation = l * shadowDp * this.density
                ambientShadowColor = glowColor.copy(alpha = 0.35f)
                spotShadowColor = glowColor.copy(alpha = 0.9f)
            }
            .drawWithCache {
                // Shapes and brushes are built once per size or colour change, never per frame.
                val edge = shape.createOutline(size, layoutDirection, this)
                val ring = shape.createOutline(Size(size.width - thickness, size.height - thickness), layoutDirection, this)
                val hair = shape.createOutline(Size(size.width - hairline, size.height - hairline), layoutDirection, this)
                val ringBrush: Brush = if (style == HighlightStyle.Outline || style == HighlightStyle.Lift) SolidColor(ringColor)
                else Brush.linearGradient(listOf(text, ringColor, text))
                val inner = shape.createOutline(Size(size.width - thickness * 2f, size.height - thickness * 2f), layoutDirection, this)
                val innerStroke = Stroke(hairline)
                val surface: Brush = when (material) {
                    SurfaceMaterial.Solid -> SolidColor(surfaceColor)
                    SurfaceMaterial.Frosted -> Brush.verticalGradient(listOf(surfaceColor.copy(alpha = 0.8f), surfaceColor.copy(alpha = 0.55f)))
                    SurfaceMaterial.Outlined -> SolidColor(surfaceColor.copy(alpha = 0.32f))
                }
                val rim: Color? = when (material) {
                    SurfaceMaterial.Solid -> null
                    SurfaceMaterial.Frosted -> text.copy(alpha = 0.14f)
                    SurfaceMaterial.Outlined -> muted.copy(alpha = 0.55f)
                }
                val ringStroke = Stroke(thickness)
                val hairStroke = Stroke(hairline)
                onDrawWithContent {
                    drawOutline(edge, surface)
                    drawContent()
                    if (rim != null) translate(hairline / 2f, hairline / 2f) { drawOutline(hair, rim, style = hairStroke) }
                    val f = lift.value * (pulse?.value ?: 1f)
                    val sh = shine.value
                    if (sh in 0.001f..0.999f) {
                        val w = size.width; val x = -w * 0.6f + sh * w * 2.2f
                        drawOutline(edge, Brush.linearGradient(
                            0f to Color.Transparent, 0.5f to Color.White.copy(alpha = 0.22f * (1f - sh)), 1f to Color.Transparent,
                            start = androidx.compose.ui.geometry.Offset(x, 0f), end = androidx.compose.ui.geometry.Offset(x + w * 0.45f, size.height),
                        ))
                    }
                    if (f > 0.01f && thickness > 0f) {
                        translate(thickness / 2f, thickness / 2f) { drawOutline(ring, ringBrush, alpha = f.coerceAtMost(1f), style = ringStroke) }
                        translate(thickness, thickness) { drawOutline(inner, edgeInk, alpha = f.coerceAtMost(1f), style = innerStroke) }
                    }
                }
            }
            .clip(shape),
    ) { CompositionLocalProvider(LocalInFocusCard provides true) { content(focused) } }
}

/**
 * A round controller-button badge with a label, e.g. (A) Play. Follows the chosen button prompt style.
 *
 * Tapped, it presses its button through the same path as the controller ([HintPress], Touch.kt), or runs
 * [onClick] when given (for a hint whose button isn't routed the usual way). Not [tappable] (a legend), or inside
 * a [FocusCard], it leaves taps alone. The hit area grows to Android's minimum touch target (48 dp) on its own.
 */
@Composable
fun ButtonHint(button: String, label: String, color: Color = Palette.Text, onClick: (() -> Unit)? = null, tappable: Boolean = true) {
    val press = remember(button) { HintPress.of(button) }
    val active = tappable && !LocalInFocusCard.current && (onClick != null || press != null)
    val own by rememberUpdatedState(onClick)
    var pressed by remember { mutableStateOf(false) }
    val sink = animateFloatAsState(if (pressed) 1f else 0f, tween(if (pressed) 60 else 160), label = "hint-press")
    val touch = if (!active) Modifier else Modifier
        .graphicsLayer { val s = 1f - 0.08f * sink.value; scaleX = s; scaleY = s }
        .drawWithCache {
            val r = CornerRadius(size.height / 2f)
            val pad = 4.dp.toPx()
            onDrawBehind {
                val p = sink.value
                if (p > 0.01f) drawRoundRect(color.copy(alpha = 0.22f * p), Offset(-pad, -pad), Size(size.width + pad * 2, size.height + pad * 2), CornerRadius(r.x + pad))
            }
        }
        .pointerInput(press) {
            detectTapGestures(
                onPress = { pressed = true; tryAwaitRelease(); pressed = false },
                onTap = { at ->
                    val o = own
                    if (o != null) o() else if (press != null) {
                        val fraction = if (press is HintPress.Dpad && !press.horizontal) at.y / size.height else at.x / size.width
                        Touch.press(press, fraction.coerceIn(0f, 1f))
                    }
                },
            )
        }
    Row(touch, verticalAlignment = Alignment.CenterVertically) {
        if (button == "◀▶" || button == "▲▼") {
            DpadGlyph(horizontal = button == "◀▶", color = color)
        } else {
            val p = Prompts.of(button)
            // Longer labels (L1, R1) get a pill instead of a circle, so they're never squeezed.
            Box(
                Modifier.height(20.dp).widthIn(min = 20.dp)
                    .clip(if (p.square) RoundedCornerShape(6.dp) else RoundedCornerShape(50)).background(p.color ?: color)
                    .padding(horizontal = if (p.glyph.length > 1) 5.dp else 0.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(p.glyph, color = p.onColor ?: Palette.OnAccent, style = MaterialTheme.typography.labelMedium, maxLines = 1)
            }
        }
        if (label.isNotEmpty()) {
            Spacer(Modifier.width(5.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, color = Palette.Text, maxLines = 1)
        }
    }
}

/** A small D-pad with the left/right (or up/down) arms lit, for "adjust" hints. Drawn, so no font is needed. */
@Composable
private fun DpadGlyph(horizontal: Boolean, color: Color) {
    val dim = Palette.Muted.copy(alpha = 0.45f)
    Canvas(Modifier.size(20.dp)) {
        val u = size.minDimension
        val arm = u * 0.32f
        val c = Offset(u / 2, u / 2)
        val r = CornerRadius(arm * 0.3f)
        // Arms: lit along the axis that does something.
        drawRoundRect(if (horizontal) color else dim, Offset(0f, c.y - arm / 2), Size(u, arm), r)
        drawRoundRect(if (horizontal) dim else color, Offset(c.x - arm / 2, 0f), Size(arm, u), r)
        drawCircle(Palette.NightDeep.copy(alpha = 0.5f), arm * 0.22f, c)
    }
}

/** A small rounded tag, used for genres, years and counts. */
@Composable
fun Pill(text: String, color: Color = Palette.Lavender, modifier: Modifier = Modifier) {
    Box(
        modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.18f)).padding(horizontal = 9.dp, vertical = 3.dp),
    ) { Text(text, color = color, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) }
}
