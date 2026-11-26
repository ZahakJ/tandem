package io.github.zahakj.tandem.ui.whatsnew

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.zahakj.tandem.R
import io.github.zahakj.tandem.Tx
import io.github.zahakj.tandem.data.ArtSets
import io.github.zahakj.tandem.data.Store
import io.github.zahakj.tandem.ui.ArtSet
import io.github.zahakj.tandem.ui.ButtonHint
import io.github.zahakj.tandem.ui.FitText
import io.github.zahakj.tandem.ui.Glyph
import io.github.zahakj.tandem.ui.GlyphChip
import io.github.zahakj.tandem.ui.GroupStrip
import io.github.zahakj.tandem.ui.HomeLayouts
import io.github.zahakj.tandem.ui.LayoutDots
import io.github.zahakj.tandem.ui.LayoutPreview
import io.github.zahakj.tandem.ui.Palette
import io.github.zahakj.tandem.ui.Pip
import io.github.zahakj.tandem.ui.QrCode
import io.github.zahakj.tandem.ui.SetHeroPreview
import io.github.zahakj.tandem.ui.SetIconPreview
import io.github.zahakj.tandem.ui.StripGroup
import io.github.zahakj.tandem.ui.fadeMask
import io.github.zahakj.tandem.ui.mirroredInRtl
import io.github.zahakj.tandem.ui.onArt
import io.github.zahakj.tandem.ui.showcaseStill
import io.github.zahakj.tandem.ui.startSign
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * The 0.9 deck's pictures. Each is a live piece of Tandem drawn by its own composables with sample data, never a
 * screenshot: the art-set icons and heroes (SetArt.kt), the layout sketches (LayoutPreview), the L1 / R1 strip
 * (GroupStrip), the try-on's dots, a real QR code, Pip. They are laid out at the design size (DeckScreen.kt,
 * 360 × 232 dp) from the start side, so right to left mirrors them like the screens they show; their parts arrive one
 * after another ([arrive]) and then play on a loop ([rememberLoop]). Reduce motion: each holds its most telling moment.
 * Sample systems only (a few consoles by their short names); no game titles, nothing of the user's.
 */

/** Sample systems: an id the art sets draw, and its short name (system names are never translated). */
internal val Samples = listOf(
    "snes" to "SNES", "gba" to "GBA", "n64" to "N64", "psx" to "PS1", "genesis" to "Genesis", "nds" to "DS", "gb" to "Game Boy", "switch" to "Switch",
)

/** The art set the pictures wear: the user's own when one is on, else Pastel Pop. */
internal fun sampleSet(): String = Store.settings.look.artSet?.takeIf { ArtSets.isSet(it) } ?: ArtSets.PASTEL

/** A system tile as the grid draws it with an art set. */
@Composable
internal fun SampleTile(k: Int, size: Dp, modifier: Modifier = Modifier, corner: Dp = 14.dp) {
    val (id, short) = Samples[k % Samples.size]
    Box(modifier.size(size).clip(RoundedCornerShape(corner))) { SetIconPreview(sampleSet(), id, short) }
}

/** Box art for a sample game, drawn in the theme's colours (a sun over two hills). */
@Composable
internal fun SampleCover(k: Int, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val a = Palette.accents
        val c1 = a[k % a.size]
        val c2 = a[(k + 2) % a.size]
        val w = size.width
        val h = size.height
        drawRect(Brush.linearGradient(listOf(c1, lerp(c2, Palette.NightDeep, 0.3f)), Offset.Zero, Offset(w, h)))
        drawCircle(Palette.Butter.copy(alpha = 0.85f), size.minDimension * 0.16f, Offset(w * (0.25f + 0.5f * ((k * 37) % 10) / 10f), h * 0.3f))
        drawCircle(lerp(c2, Color.White, 0.25f).copy(alpha = 0.75f), w * 0.7f, Offset(w * 0.15f, h * 1.22f))
        drawCircle(lerp(c1, Palette.NightDeep, 0.3f).copy(alpha = 0.85f), w * 0.62f, Offset(w * 0.95f, h * 1.3f))
    }
}

/** A row of a popup card like the lift's: an icon chip, the label and maybe a value chip. */
@Composable
internal fun MenuPill(glyph: Glyph, label: String, color: Color, modifier: Modifier = Modifier, value: String? = null, sub: String? = null) {
    Row(modifier.fillMaxWidth().height(if (sub != null) 44.dp else 34.dp).padding(horizontal = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        GlyphChip(glyph, color, size = 24.dp, round = true)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (sub != null) Text(sub, style = MaterialTheme.typography.labelSmall, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (value != null) {
            Spacer(Modifier.width(6.dp))
            Box(Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.22f)).padding(horizontal = 8.dp, vertical = 2.dp)) {
                Text(value, style = MaterialTheme.typography.labelMedium, color = color, maxLines = 1)
            }
        }
    }
}

/** The gliding highlight of a popup card: a pill ringed in [color], at [y] (dp) from the card's first row. */
@Composable
internal fun RowHighlight(color: Color, height: Dp, y: () -> Float) {
    Box(
        Modifier.offset { IntOffset(0, (y() * density).roundToInt()) }.fillMaxWidth().height(height)
            .clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.2f)).border(2.dp, color, RoundedCornerShape(50)),
    )
}

/** A short line in a pill: what a part of a picture is called. */
@Composable
internal fun Caption(glyph: Glyph, text: String, color: Color, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(50)).background(Palette.Card.copy(alpha = 0.92f)).padding(start = 3.dp, end = 10.dp, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlyphChip(glyph, color, size = 20.dp, round = true)
        Spacer(Modifier.width(6.dp))
        FitText(text, MaterialTheme.typography.labelLarge, color = Palette.Text)
    }
}

/** Slides between the values of a loop's step, from the end side, as Tandem's pages turn. */
@Composable
internal fun <T> Turning(value: T, modifier: Modifier = Modifier, content: @Composable (T) -> Unit) {
    val sign = startSign
    val still = showcaseStill()
    AnimatedContent(
        value, modifier,
        transitionSpec = {
            if (still) fadeIn(tween(0)) togetherWith fadeOut(tween(0))
            else (slideInHorizontally(tween(420)) { (it / 2 * sign).toInt() } + fadeIn(tween(300))) togetherWith
                (slideOutHorizontally(tween(420)) { (-it / 2 * sign).toInt() } + fadeOut(tween(240)))
        },
        label = "wn-turn",
    ) { content(it) }
}

// --- 1. The lift ------------------------------------------------------------------------------------------------

/**
 * A grid of system tiles; Y is pressed on one, which rises where it sits as the rest dim, and the lift's card comes
 * out beside it with Pip on top: Open, then the highlight glides to Edit; Hold Y to move it underneath.
 */
@Composable
internal fun LiftVisual() {
    val t = rememberLoop(5600, still = 0.7f)
    val sign = startSign
    fun lift(v: Float) = seg(v, 0.1f, 0.22f) * (1f - seg(v, 0.88f, 0.97f))
    val tile = 58.dp
    val gap = 12.dp
    val x0 = 14.dp
    val y0 = 52.dp
    Box(Modifier.fillMaxSize()) {
        for (r in 0..1) for (c in 0..2) {
            val k = r * 3 + c
            val at = Modifier.offset(x0 + (tile + gap) * c, y0 + (tile + gap) * r).arrive(k)
            if (k == 1) continue
            SampleTile(k, tile, at.graphicsLayer { alpha = 1f - 0.55f * lift(t.value) })
        }
        // The lifted tile, drawn last so it rises over its neighbours, and its name under it.
        SampleTile(
            1, tile,
            Modifier.offset(x0 + tile + gap, y0).arrive(1).graphicsLayer {
                val l = lift(t.value)
                val s = 1f + 0.22f * l
                scaleX = s; scaleY = s
                translationY = -8.dp.toPx() * l
                shadowElevation = 22f * density * l
                shape = RoundedCornerShape(14.dp)
                clip = true
                ambientShadowColor = Palette.Peach; spotShadowColor = Palette.Peach
            },
        )
        Text(
            Samples[1].second, Modifier.offset(x0 + tile + gap, y0 + tile + 12.dp).width(tile).graphicsLayer { alpha = lift(t.value) },
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold), maxLines = 1,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        // Y, pressed.
        Box(
            Modifier.offset(x0 + tile + gap + tile - 10.dp, y0 - 16.dp).arrive(6).graphicsLayer {
                val p = ((t.value - 0.03f) / 0.1f).coerceIn(0f, 1f)
                val s = 1f + 0.3f * sin(p * PI.toFloat())
                scaleX = s; scaleY = s
            },
        ) { ButtonHint("Y", "", Palette.Butter, tappable = false) }
        // The card, with Pip perched on it.
        Column(
            Modifier.offset(200.dp, 14.dp).width(146.dp).graphicsLayer {
                val a = seg(t.value, 0.18f, 0.32f) * (1f - seg(t.value, 0.86f, 0.94f))
                alpha = a
                translationX = (1f - a) * -26.dp.toPx() * sign
            },
        ) {
            Box(Modifier.fillMaxWidth().height(34.dp)) {
                Pip(40.dp, Modifier.align(Alignment.BottomEnd).padding(end = 12.dp).offset(y = 6.dp), mood = 1)
            }
            Column(
                Modifier.fillMaxWidth().graphicsLayer { shape = RoundedCornerShape(18.dp); clip = true; shadowElevation = 14f * density }
                    .background(Palette.Card).padding(6.dp),
            ) {
                Box {
                    RowHighlight(Palette.Peach, 34.dp) { 38f * seg(t.value, 0.5f, 0.6f) }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        MenuPill(Glyph.Play, Tx.s(R.string.grid_menu_open), Palette.Mint)
                        MenuPill(Glyph.Pencil, Tx.s(R.string.grid_lift_edit), Palette.Peach)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Box(Modifier.padding(start = 6.dp, bottom = 2.dp)) { ButtonHint("Y", Tx.s(R.string.grid_lift_hold_move), Palette.Muted, tappable = false) }
            }
        }
    }
}

// --- 2. The edit screen -----------------------------------------------------------------------------------------

/**
 * The edit screen in brief: its three sections along the top (Look lit), a live preview of the tile wearing an
 * uploaded animation (Pip bobbing, a GIF badge) whose corners round and sharpen, and the rows that do it.
 */
@Composable
internal fun EditVisual() {
    val t = rememberLoop(6000, still = 0.45f)
    // Corners from 10 to 44 dp and back; the chip under the preview says how round, in steps of 5 %.
    val round = { 0.5f - 0.5f * cos(t.value * 2f * PI.toFloat()) }
    val pct by remember { derivedStateOf { (10 + round() * 40).roundToInt() / 5 * 5 } }
    val gif = "GIF" // tx-ok: a file format
    Box(Modifier.fillMaxSize()) {
        Row(Modifier.offset(14.dp, 8.dp).arrive(0), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(R.string.grid_edit_section_look, R.string.grid_edit_section_options, R.string.grid_edit_section_organize).forEachIndexed { i, res ->
                val on = i == 0
                Box(
                    Modifier.clip(RoundedCornerShape(50)).background(if (on) Palette.Lavender else Palette.Card).padding(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    Text(Tx.s(res), style = MaterialTheme.typography.labelLarge, color = if (on) Palette.OnAccent else Palette.Muted, maxLines = 1)
                }
            }
        }
        Box(
            Modifier.offset(20.dp, 48.dp).size(124.dp).arrive(1).graphicsLayer {
                shape = RoundedCornerShape((10f + 34f * round()).dp)
                clip = true
                shadowElevation = 12f * density
            }.background(Brush.linearGradient(listOf(Palette.Sky, Palette.Lavender, Palette.Peach))),
            contentAlignment = Alignment.Center,
        ) {
            Pip(78.dp, mood = 1)
            Box(
                Modifier.align(Alignment.TopEnd).padding(10.dp).clip(RoundedCornerShape(6.dp)).background(Palette.NightDeep.copy(alpha = 0.7f))
                    .padding(horizontal = 6.dp, vertical = 1.dp),
            ) { Text(gif, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold), color = Color.White, maxLines = 1) }
        }
        Box(Modifier.offset(14.dp, 184.dp).width(136.dp).arrive(2), contentAlignment = Alignment.Center) {
            Caption(Glyph.Corners, Tx.s(R.string.grid_edit_corners_chip, Tx.s(R.string.common_percent, pct)), Palette.Lavender)
        }
        Column(
            Modifier.offset(168.dp, 48.dp).width(178.dp).arrive(3)
                .graphicsLayer { shape = RoundedCornerShape(18.dp); clip = true; shadowElevation = 10f * density }
                .background(Palette.Card).padding(6.dp),
        ) {
            Box {
                RowHighlight(Palette.Lavender, 44.dp) { 48f * seg(t.value, 0.45f, 0.55f) * (1f - seg(t.value, 0.92f, 0.99f)) }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    MenuPill(Glyph.Image, Tx.s(R.string.grid_design_upload), Palette.Peach, sub = Tx.s(R.string.grid_design_upload_sub))
                    MenuPill(Glyph.Corners, Tx.s(R.string.grid_edit_corners), Palette.Lavender, value = "◂ ${Tx.s(R.string.common_percent, pct)} ▸", sub = " ")
                }
            }
        }
    }
}

// --- 3. Try-on --------------------------------------------------------------------------------------------------

/** Start › Layout: the layouts put on one after another, the try-on popup (Pip, the name, the dots) and its buttons. */
@Composable
internal fun TryOnVisual() {
    val ids = listOf("shelves", "grid", "carousel", "orbit", "spotlight", "stage")
    val clock = rememberLoop(ids.size * 1700, still = 5.5f / ids.size)
    val k by remember { derivedStateOf { (clock.value * ids.size).toInt().coerceIn(0, ids.size - 1) } }
    val names = HomeLayouts.toMap()
    Box(Modifier.fillMaxSize()) {
        Turning(k, Modifier.offset(14.dp, 4.dp).size(332.dp, 174.dp).arrive(0).clip(RoundedCornerShape(16.dp))) { i ->
            LayoutPreview(ids[i], Modifier.fillMaxSize())
        }
        Column(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 38.dp).arrive(2)
                .graphicsLayer { shape = RoundedCornerShape(22.dp); clip = true; shadowElevation = 18f * density; ambientShadowColor = Palette.Butter; spotShadowColor = Palette.Butter }
                .background(Palette.Card.copy(alpha = 0.97f)).padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Pip(26.dp, mood = 1)
                Spacer(Modifier.width(8.dp))
                Text(
                    names[ids[k]].orEmpty().substringBefore(" ("), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            LayoutDots(ids.size, k, Modifier.padding(top = 5.dp))
        }
        Row(
            Modifier.align(Alignment.BottomCenter).arrive(3).clip(RoundedCornerShape(50)).background(Palette.Card.copy(alpha = 0.9f))
                .padding(horizontal = 12.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                ButtonHint("L1", "", Palette.Lavender, tappable = false)
                ButtonHint("R1", Tx.s(R.string.panels_layout_hint_layout), Palette.Lavender, tappable = false)
            }
            ButtonHint("A", Tx.s(R.string.panels_layout_hint_keep), Palette.Mint, tappable = false)
            ButtonHint("B", Tx.s(R.string.panels_layout_hint_cancel), Palette.Peach, tappable = false)
        }
    }
}

// --- 4. Stage ---------------------------------------------------------------------------------------------------

/** Stage: a page per system, its art filling the back, a row of covers; L1 / R1 (the strip on top) turn the pages. */
@Composable
internal fun StageVisual() {
    val pages = Samples.take(3)
    val clock = rememberLoop(pages.size * 2800, still = 0.12f)
    val k by remember { derivedStateOf { (clock.value * pages.size).toInt().coerceIn(0, pages.size - 1) } }
    val focus by remember { derivedStateOf { ((clock.value * pages.size) % 1f * 4f).toInt().coerceIn(0, 3) } }
    val set = sampleSet()
    val groups = pages.map { (id, short) -> StripGroup(id, short, Palette.forName(id)) }
    Box(Modifier.fillMaxSize().clip(RoundedCornerShape(18.dp))) {
        Turning(k, Modifier.fillMaxSize().arrive(0)) { i ->
            val (id, short) = pages[i]
            Box(Modifier.fillMaxSize()) {
                SetHeroPreview(set, id, short, Modifier.fillMaxSize().fadeMask(0f to 0.95f, 0.55f to 0.6f, 1f to 0.05f), logo = false)
                Text(
                    short, Modifier.offset(16.dp, 46.dp), style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold).onArt(),
                    maxLines = 1,
                )
                Row(Modifier.align(Alignment.BottomStart).padding(start = 16.dp, bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (j in 0 until 5) {
                        val on = j == focus
                        Box(
                            Modifier.size(48.dp, 64.dp).graphicsLayer {
                                val s = if (on) 1.12f else 1f
                                scaleX = s; scaleY = s
                                translationY = if (on) -4.dp.toPx() else 0f
                            }.then(if (on) Modifier.border(2.dp, Palette.Text, RoundedCornerShape(10.dp)) else Modifier)
                                .clip(RoundedCornerShape(10.dp)),
                        ) { SampleCover(i * 5 + j, Modifier.fillMaxSize()) }
                    }
                }
            }
        }
        GroupStrip(groups, pages[k].first, Modifier.align(Alignment.TopCenter).padding(top = 8.dp).arrive(2))
    }
}

// --- 5. Gliding about -------------------------------------------------------------------------------------------

/**
 * The strip of groups L1 / R1 jump between, the Continue card leading the grid, and the highlight gliding from one
 * tile to the next with a little overshoot (the Travelling highlight).
 */
@Composable
internal fun GlideVisual() {
    val stops = 5
    val clock = rememberLoop(stops * 1300, still = 0.1f)
    val step by remember { derivedStateOf { (clock.value * stops).toInt().coerceIn(0, stops - 1) } }
    val strip = listOf(StripGroup("continue", Tx.s(R.string.home_strip_continue), Palette.Mint)) +
        Samples.take(3).map { (id, short) -> StripGroup(id, short, Palette.forName(id)) }
    val tile = 54f
    // Places in dp from the start side: the Continue card (two tiles wide), then tiles.
    val spots = listOf(
        floatArrayOf(14f, 56f, tile * 2 + 10f, tile), floatArrayOf(142f, 56f, tile, tile), floatArrayOf(206f, 56f, tile, tile),
        floatArrayOf(206f, 120f, tile, tile), floatArrayOf(78f, 120f, tile, tile),
    )
    Box(Modifier.fillMaxSize()) {
        GroupStrip(strip, strip[step % strip.size].key, Modifier.align(Alignment.TopCenter).padding(top = 6.dp).arrive(0), chips = 4)
        Box(Modifier.offset(14.dp, 56.dp).size((tile * 2 + 10f).dp, tile.dp).arrive(1).clip(RoundedCornerShape(12.dp))) {
            SampleCover(3, Modifier.fillMaxSize())
            Text(
                Tx.s(R.string.home_strip_continue), Modifier.align(Alignment.BottomStart).padding(8.dp),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold).onArt(), color = Color.White, maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(Modifier.align(Alignment.TopEnd).padding(7.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                for (d in 0 until 3) Box(Modifier.size(if (d == 0) 6.dp else 4.dp).clip(CircleShape).background(Color.White.copy(alpha = if (d == 0) 1f else 0.6f)))
            }
        }
        val tiles = listOf(142f to 56f, 206f to 56f, 270f to 56f, 14f to 120f, 78f to 120f, 142f to 120f, 206f to 120f, 270f to 120f)
        tiles.forEachIndexed { i, (x, y) -> SampleTile(i + 1, tile.dp, Modifier.offset(x.dp, y.dp).arrive(2 + i), corner = 12.dp) }
        // The highlight: drawn over the tiles in their own places, mirrored with them in right to left.
        Canvas(Modifier.fillMaxSize().mirroredInRtl().arrive(4)) {
            val p = clock.value * stops
            val at = p.toInt() % stops
            val into = (p - p.toInt()).coerceIn(0f, 1f)
            val from = spots[at]
            val to = spots[(at + 1) % stops]
            val x = (into / 0.32f).coerceAtMost(1f)
            val k = 1f - (1f - x) * (1f - x) + 0.12f * sin(x * PI.toFloat())
            fun mix(i: Int) = (from[i] + (to[i] - from[i]) * k).dp.toPx()
            val o = 3.dp.toPx()
            drawRoundRect(
                Palette.Text, Offset(mix(0) - o, mix(1) - o), Size(mix(2) + o * 2, mix(3) + o * 2), CornerRadius(15.dp.toPx()),
                style = Stroke(2.5f.dp.toPx()),
            )
        }
        Caption(Glyph.Highlight, Tx.s(R.string.look_travel), Palette.Mint, Modifier.align(Alignment.BottomEnd).padding(end = 10.dp, bottom = 6.dp).arrive(11))
    }
}

// --- 6. System art ----------------------------------------------------------------------------------------------

/** The four art sets in turn on a few systems, a hero and four icons, with the set's name lit below. */
@Composable
internal fun ArtVisual() {
    val sets = ArtSet.entries
    val clock = rememberLoop(sets.size * 2400, still = 0.1f)
    val k by remember { derivedStateOf { (clock.value * sets.size).toInt().coerceIn(0, sets.size - 1) } }
    val still = showcaseStill()
    Box(Modifier.fillMaxSize()) {
        AnimatedContent(
            k, Modifier.offset(14.dp, 8.dp).size(332.dp, 120.dp).arrive(0),
            transitionSpec = {
                if (still) fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                else (fadeIn(tween(420)) + scaleIn(tween(420), initialScale = 0.94f)) togetherWith fadeOut(tween(260))
            },
            label = "wn-art",
        ) { i ->
            val set = sets[i].id
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(206.dp, 116.dp).clip(RoundedCornerShape(14.dp))) {
                    SetHeroPreview(set, Samples[0].first, Samples[0].second, Modifier.fillMaxSize())
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (r in 0..1) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (c in 0..1) {
                            val (id, short) = Samples[1 + r * 2 + c]
                            Box(Modifier.size(54.dp).clip(RoundedCornerShape(12.dp))) { SetIconPreview(set, id, short) }
                        }
                    }
                }
            }
        }
        Column(Modifier.offset(14.dp, 142.dp).width(332.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            sets.chunked(2).forEachIndexed { r, pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pair.forEachIndexed { c, s ->
                        val i = r * 2 + c
                        val on = i == k
                        val color = Palette.accents[i % Palette.accents.size]
                        Box(
                            Modifier.weight(1f).height(30.dp).arrive(1 + i).clip(RoundedCornerShape(50))
                                .background(if (on) color else Palette.Card).padding(horizontal = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            FitText(s.label, MaterialTheme.typography.labelLarge.copy(fontWeight = if (on) FontWeight.ExtraBold else FontWeight.SemiBold), color = if (on) Palette.OnAccent else Palette.Muted)
                        }
                    }
                }
            }
            Caption(Glyph.Download, Tx.s(R.string.look_fetch_art), Palette.Peach, Modifier.arrive(6))
        }
    }
}

// --- 7. More libraries ------------------------------------------------------------------------------------------

/** DroidDeck and GameNative on the start side; their games flow across and land on the grid one tile each. */
@Composable
internal fun LibraryVisual() {
    val t = rememberLoop(5600, still = 0.8f)
    val sources = listOf("DroidDeck" to Palette.Sky, "GameNative" to Palette.Mint) // tx-ok: app names
    val glyphs = listOf(Glyph.Gamepad, Glyph.Cartridge)
    Box(Modifier.fillMaxSize()) {
        sources.forEachIndexed { i, (name, color) ->
            Column(
                Modifier.offset(14.dp, (30 + i * 96).dp).width(128.dp).arrive(i)
                    .graphicsLayer { shape = RoundedCornerShape(16.dp); clip = true; shadowElevation = 8f * density }
                    .background(Palette.Card).padding(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GlyphChip(glyphs[i], color, size = 28.dp)
                    Spacer(Modifier.width(8.dp))
                    FitText(name, MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold))
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (d in 0 until 4) Box(Modifier.size(22.dp, 28.dp).clip(RoundedCornerShape(5.dp)).background(lerp(color, Palette.Card, 0.25f + d * 0.15f)))
                }
            }
        }
        // Games on their way: dots from each card to the grid.
        Canvas(Modifier.fillMaxSize().mirroredInRtl().arrive(2)) {
            for (s in 0..1) for (d in 0 until 3) {
                val p = ((t.value * 2f + d / 3f) % 1f)
                val from = Offset(146.dp.toPx(), (64 + s * 96).dp.toPx())
                val to = Offset(196.dp.toPx(), (60 + 60 * (d % 3)).dp.toPx())
                val x = from.x + (to.x - from.x) * p
                val y = from.y + (to.y - from.y) * p - sin(p * PI.toFloat()) * 10.dp.toPx()
                drawCircle((if (s == 0) Palette.Sky else Palette.Mint).copy(alpha = sin(p * PI.toFloat())), 3.5f.dp.toPx(), Offset(x, y))
            }
        }
        for (j in 0 until 9) {
            val r = j / 3
            val c = j % 3
            SampleCover(
                j + 2,
                Modifier.offset((200 + c * 50).dp, (22 + r * 62).dp).size(44.dp, 56.dp).arrive(3)
                    .graphicsLayer {
                        val a = seg(t.value, 0.08f + j * 0.06f, 0.16f + j * 0.06f) * (1f - seg(t.value, 0.92f, 0.99f))
                        alpha = a
                        val s = 0.7f + 0.3f * a
                        scaleX = s; scaleY = s
                        shape = RoundedCornerShape(8.dp); clip = true
                    },
            )
        }
    }
}

// --- 8. Share and feel ------------------------------------------------------------------------------------------

/** Share my look (a look card with its real QR code) beside Thock's keycap thocking and a move's light buzz. */
@Composable
internal fun ShareVisual() {
    val t = rememberLoop(2200, still = 0.0f)
    val link = "https://github.com/ZahakJ/tandem"
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.offset(14.dp, 8.dp).width(150.dp).arrive(0)
                .graphicsLayer { shape = RoundedCornerShape(18.dp); clip = true; shadowElevation = 10f * density }
                .background(Palette.Card).padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            LayoutPreview(Store.settings.look.homeLayout, Modifier.fillMaxWidth().height(62.dp), sketch = true)
            Spacer(Modifier.height(6.dp))
            FitText(Tx.s(R.string.share_title), MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold))
            Spacer(Modifier.height(6.dp))
            QrCode(link, Modifier.size(86.dp))
        }
        // Thock: a keycap pressed down, rings going out.
        Column(
            Modifier.offset(180.dp, 8.dp).width(166.dp).height(122.dp).arrive(1)
                .graphicsLayer { shape = RoundedCornerShape(18.dp); clip = true }
                .background(Palette.Card).padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Canvas(Modifier.fillMaxWidth().weight(1f)) {
                // Down for a moment at the start of each loop (and down, held still, with Reduce motion).
                val down = if (t.value <= 0.16f) 1f else 0f
                val cx = size.width / 2
                val cy = size.height / 2
                val ring = ((t.value - 0.08f) / 0.6f).coerceIn(0f, 1f)
                for (k in 0..1) {
                    val rr = (ring - k * 0.25f).coerceIn(0f, 1f)
                    if (rr > 0f) drawCircle(Palette.Lavender.copy(alpha = (1f - rr) * 0.7f), size.height * (0.3f + rr * 0.45f), Offset(cx, cy), style = Stroke(2.dp.toPx()))
                }
                val kw = size.height * 0.62f
                val sink = down * 4.dp.toPx()
                drawRoundRect(lerp(Palette.Lavender, Palette.NightDeep, 0.45f), Offset(cx - kw / 2, cy - kw / 2 + 5.dp.toPx()), Size(kw, kw), CornerRadius(10.dp.toPx()))
                drawRoundRect(Palette.Lavender, Offset(cx - kw / 2, cy - kw / 2 + sink), Size(kw, kw), CornerRadius(10.dp.toPx()))
                drawRoundRect(Color.White.copy(alpha = 0.25f), Offset(cx - kw / 2 + kw * 0.14f, cy - kw / 2 + sink + kw * 0.1f), Size(kw * 0.72f, kw * 0.5f), CornerRadius(7.dp.toPx()))
            }
            Caption(Glyph.Music, Tx.s(R.string.sound_pack_thock), Palette.Lavender)
        }
        // Feel each move: a D-pad step and the buzz it gives.
        Column(
            Modifier.offset(180.dp, 140.dp).width(166.dp).height(84.dp).arrive(2)
                .graphicsLayer { shape = RoundedCornerShape(18.dp); clip = true }
                .background(Palette.Card).padding(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlyphChip(Glyph.Gamepad, Palette.Mint, size = 24.dp, round = true)
                Spacer(Modifier.width(6.dp))
                FitText(Tx.s(R.string.controls_feel), MaterialTheme.typography.labelLarge, color = Palette.Text)
            }
            Canvas(Modifier.fillMaxWidth().weight(1f).padding(top = 6.dp)) {
                // A short buzz each time the highlight moves: a squiggle that swells and settles, four times a loop.
                val w = size.width
                val mid = size.height / 2
                val beat = (t.value * 4f) % 1f
                val amp = (1f - beat).let { it * it } * size.height * 0.42f
                var prev = Offset(0f, mid)
                val n = 60
                for (i in 1..n) {
                    val x = w * i / n
                    val env = sin(i.toFloat() / n * PI.toFloat())
                    val y = mid + sin(i * 1.3f + t.value * 40f) * amp * env
                    drawLine(Palette.Mint, prev, Offset(x, y), 2.dp.toPx())
                    prev = Offset(x, y)
                }
            }
        }
    }
}
