package io.github.zahakj.tandem.ui.whatsnew

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
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
import io.github.zahakj.tandem.data.TimeToBeat
import io.github.zahakj.tandem.data.WeekPick
import io.github.zahakj.tandem.data.WeekRow
import io.github.zahakj.tandem.data.WeekSlot
import io.github.zahakj.tandem.data.WeekWhy
import io.github.zahakj.tandem.ui.ButtonHint
import io.github.zahakj.tandem.ui.FitText
import io.github.zahakj.tandem.ui.Glyph
import io.github.zahakj.tandem.ui.GlyphChip
import io.github.zahakj.tandem.ui.GlyphIcon
import io.github.zahakj.tandem.ui.Palette
import io.github.zahakj.tandem.ui.Pill
import io.github.zahakj.tandem.ui.Pip
import io.github.zahakj.tandem.ui.SettingsCategories
import io.github.zahakj.tandem.ui.SettingsHomeTile
import io.github.zahakj.tandem.ui.SettingsTileHint
import io.github.zahakj.tandem.ui.WeekText
import io.github.zahakj.tandem.ui.mirroredInRtl
import io.github.zahakj.tandem.ui.onArt
import io.github.zahakj.tandem.ui.startSign
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * The 0.10 deck's pictures, made the way the 0.9 ones are (Visuals09.kt, whose sample tiles, covers, captions and
 * page turns they share): live pieces of Tandem with sample data, laid out at the design size from the start side,
 * arriving part by part and then playing on a loop; Reduce motion holds each on its most telling moment. The words in
 * them are Tandem's own (This week's reasons, the filter and rule names, the Settings pages), so every language
 * gets its own. Sample covers only: no game titles, nothing of the user's.
 */

/** A rounded card in the theme's card colour, with a soft shadow, as Tandem's popups and panels are. */
private fun Modifier.card(corner: Dp = 16.dp, shadow: Float = 8f): Modifier =
    graphicsLayer { shape = RoundedCornerShape(corner); clip = true; shadowElevation = shadow * density }.background(Palette.Card)

/** A small chip of a colour, as under This week's covers. */
@Composable
private fun TinyChip(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.2f)).padding(horizontal = 6.dp, vertical = 1.dp)) {
        FitText(text, MaterialTheme.typography.labelSmall, color = color)
    }
}

// --- 1. This week -----------------------------------------------------------------------------------------------

/**
 * The board: the Pick of the week card with its reason, then a row of covers, each with its reason on a chip; the
 * highlight walks the row and the reason under it lights up.
 */
@Composable
internal fun WeekVisual() {
    val t = rememberLoop(6000, still = 0.4f)
    val focus by remember { derivedStateOf { ((t.value * 4f).toInt()).coerceIn(0, 3) } }
    val hero = WeekPick("sample", WeekSlot.Pick, WeekWhy.TopRated)
    val chips = listOf(
        Tx.p(R.plurals.week_chip_days_ago, 3, 3) to Palette.Mint,
        Tx.s(R.string.week_chip_left, TimeToBeat.hours(7200)) to Palette.Peach,
        Tx.s(R.string.week_chip_hltb, TimeToBeat.hours(9000)) to Palette.Sky,
        "★ 4.6" to Palette.Peach, // tx-ok: stars and a rating
    )
    Box(Modifier.fillMaxSize()) {
        // Pick of the week: wide, its box on the start side, the reason beside it.
        Row(
            Modifier.offset(12.dp, 8.dp).width(336.dp).height(92.dp).arrive(0)
                .graphicsLayer { shape = RoundedCornerShape(16.dp); clip = true; shadowElevation = 8f * density }
                .background(lerp(Palette.Butter, Palette.Night, 0.8f))
                .border(2.dp, Palette.Butter.copy(alpha = 0.35f + 0.65f * (if (focus == 0 && t.value < 0.12f) 1f else 0f)), RoundedCornerShape(16.dp))
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SampleCover(4, Modifier.fillMaxHeight().width(54.dp).clip(RoundedCornerShape(8.dp)))
            Spacer(Modifier.width(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Pill(WeekText.tag(WeekSlot.Pick), Palette.Butter)
                Text(
                    WeekText.why(hero), style = MaterialTheme.typography.bodyMedium.onArt(), color = Palette.Text,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        // A row and its heading.
        Row(Modifier.offset(14.dp, 110.dp).arrive(1), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(Palette.Mint))
            Spacer(Modifier.width(6.dp))
            FitText(WeekText.title(WeekRow(WeekSlot.Resume, emptyList())), MaterialTheme.typography.titleSmall)
        }
        chips.forEachIndexed { i, (chip, color) ->
            val on = i == focus
            Column(
                Modifier.offset((14 + i * 84).dp, 134.dp).width(74.dp).arrive(2 + i),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.size(62.dp).graphicsLayer {
                        val s = if (on) 1.1f else 1f
                        scaleX = s; scaleY = s
                    }.then(if (on) Modifier.border(2.dp, Palette.Text, RoundedCornerShape(12.dp)) else Modifier).clip(RoundedCornerShape(12.dp)),
                ) { SampleCover(i + 6, Modifier.fillMaxSize()) }
                Spacer(Modifier.height(8.dp))
                TinyChip(chip, if (on) color else Palette.Muted)
            }
        }
        Box(Modifier.align(Alignment.TopEnd).padding(top = 106.dp, end = 14.dp).arrive(6)) {
            ButtonHint("X", Tx.s(R.string.week_hint_reroll), Palette.Sky, tappable = false)
        }
    }
}

// --- 2. Surprise me ---------------------------------------------------------------------------------------------

/** Select: a reel of covers spins past, slows, and lands on one, which rises with a burst; Pip cheers. */
@Composable
internal fun SurpriseVisual() {
    val t = rememberLoop(4200, still = 0.9f)
    val cover = 70f
    val gap = 12f
    Box(Modifier.fillMaxSize()) {
        // The reel: covers sliding from the end side, fast then slower, stopping with the chosen one in the middle.
        Box(
            Modifier.offset(0.dp, 46.dp).fillMaxWidth().height(110.dp).arrive(0).graphicsLayer { clip = true }
                .background(Brush.horizontalGradient(listOf(Color.Transparent, Palette.Card.copy(alpha = 0.7f), Color.Transparent))),
        ) {
            for (i in 0 until 9) {
                Box(
                    // From the end side towards the start (offset mirrors itself in right to left), slowing to a stop
                    // with the first cover, the chosen one, in the middle.
                    Modifier.offset {
                        val left = 6f * (1f - seg(t.value, 0.05f, 0.62f))
                        val x = 180f - cover / 2f + (i + left) * (cover + gap)
                        IntOffset((x * density).roundToInt(), (20 * density).roundToInt())
                    }.size(cover.dp, cover.dp).graphicsLayer {
                        val landed = seg(t.value, 0.62f, 0.72f) * (1f - seg(t.value, 0.92f, 0.99f))
                        val chosen = i == 0
                        val s = if (chosen) 1f + 0.2f * landed else 1f - 0.1f * landed
                        scaleX = s; scaleY = s
                        alpha = if (chosen) 1f else 1f - 0.5f * landed
                        shape = RoundedCornerShape(12.dp); clip = true
                        shadowElevation = if (chosen) 18f * density * landed else 0f
                        ambientShadowColor = Palette.Peach; spotShadowColor = Palette.Peach
                    },
                ) { SampleCover(i + 1, Modifier.fillMaxSize()) }
            }
        }
        // The burst round the chosen one.
        Canvas(Modifier.fillMaxSize().arrive(1)) {
            val b = seg(t.value, 0.64f, 0.85f) * (1f - seg(t.value, 0.9f, 0.97f))
            if (b <= 0f) return@Canvas
            val c = Offset(size.width / 2f, (46 + 20 + 35).dp.toPx())
            for (k in 0 until 10) {
                val a = k / 10f * 2f * PI.toFloat()
                val r1 = (48 + 18 * b).dp.toPx()
                val r2 = r1 + 10.dp.toPx() * b
                val col = Palette.accents[k % Palette.accents.size]
                drawLine(col.copy(alpha = b), Offset(c.x + kotlin.math.cos(a) * r1, c.y + sin(a) * r1), Offset(c.x + kotlin.math.cos(a) * r2, c.y + sin(a) * r2), 3.dp.toPx())
            }
        }
        Row(Modifier.align(Alignment.BottomStart).padding(start = 14.dp, bottom = 12.dp).arrive(2), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.graphicsLayer {
                    val p = (t.value / 0.08f).coerceIn(0f, 1f)
                    val s = 1f + 0.25f * sin(p * PI.toFloat())
                    scaleX = s; scaleY = s
                },
            ) { ButtonHint("Select", Tx.s(R.string.controls_command_surprise), Palette.Peach, tappable = false) }
        }
        Pip(
            44.dp, Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 6.dp).arrive(3)
                .graphicsLayer { translationY = -10.dp.toPx() * seg(t.value, 0.64f, 0.72f) * (1f - seg(t.value, 0.76f, 0.86f)) },
            mood = 1, bounce = false,
        )
    }
}

// --- 3. Multi-disc games ----------------------------------------------------------------------------------------

/** Three discs of one game slide together into one tile with its discs chip; a playlist file is written beside them. */
@Composable
internal fun DiscsVisual() {
    val t = rememberLoop(5200, still = 0.8f)
    val stack = { v: Float -> seg(v, 0.2f, 0.45f) * (1f - seg(v, 0.9f, 0.98f)) }
    Box(Modifier.fillMaxSize()) {
        // Three discs, apart, then stacked into one tile.
        for (d in 2 downTo 0) {
            Box(
                Modifier.offset {
                    val s = stack(t.value)
                    val apart = (24 + d * 82).dp.toPx()
                    val together = (106 + d * 6).dp.toPx()
                    IntOffset((apart + (together - apart) * s).roundToInt(), (34 - d * 6 * s).dp.roundToPx())
                }.size(74.dp, 96.dp).arrive(d).graphicsLayer { shape = RoundedCornerShape(12.dp); clip = true; shadowElevation = 6f * density },
            ) {
                SampleCover(5, Modifier.fillMaxSize())
                // Each disc's number, fading as they become one.
                Box(
                    Modifier.align(Alignment.BottomEnd).padding(5.dp).size(20.dp).clip(CircleShape).background(Palette.NightDeep.copy(alpha = 0.75f))
                        .graphicsLayer { alpha = 1f - stack(t.value) },
                    contentAlignment = Alignment.Center,
                ) { Text("${d + 1}", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold), color = Color.White) }
            }
        }
        // The one tile's chip.
        Box(Modifier.offset(100.dp, 140.dp).graphicsLayer { alpha = stack(t.value); translationY = (1f - stack(t.value)) * 8.dp.toPx() }) {
            Pill(Tx.p(R.plurals.screens_discs, 3, 3), Palette.Sky)
        }
        // The disc icon spinning on the tile.
        Canvas(Modifier.offset(170.dp, 22.dp).size(34.dp).graphicsLayer { alpha = stack(t.value); rotationZ = t.value * 720f }) {
            val r = size.minDimension / 2f
            drawCircle(Brush.sweepGradient(listOf(Palette.Sky, Palette.Lavender, Palette.Mint, Palette.Sky)), r)
            drawCircle(Palette.NightDeep, r * 0.28f)
            drawCircle(Color.White.copy(alpha = 0.5f), r * 0.75f, style = Stroke(1.dp.toPx()))
        }
        // Settings › Library: Make playlists, and the file it writes.
        Column(
            Modifier.offset(222.dp, 56.dp).width(126.dp).arrive(3).card().padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlyphChip(Glyph.Music, Palette.Sky, size = 24.dp, round = true)
                Spacer(Modifier.width(6.dp))
                Text(Tx.s(R.string.library_playlists_row), style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Row(
                Modifier.graphicsLayer { alpha = seg(t.value, 0.5f, 0.62f) * (1f - seg(t.value, 0.9f, 0.98f)) }
                    .clip(RoundedCornerShape(8.dp)).background(Palette.Sky.copy(alpha = 0.16f)).padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlyphIcon(Glyph.Save, Palette.Sky, Modifier.size(14.dp))
                Spacer(Modifier.width(5.dp))
                Text(".m3u", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = Palette.Sky) // tx-ok: a file type
            }
        }
    }
}

// --- 4. Sorting and smart folders -------------------------------------------------------------------------------

/**
 * A smart folder's rules, the new ones lit one after another (rating, developer, play time, last played), and the
 * All / Any switch flipping; beside it the list they make, re-sorting itself.
 */
@Composable
internal fun SmartVisual() {
    val t = rememberLoop(6400, still = 0.7f)
    val any by remember { derivedStateOf { t.value in 0.55f..0.95f } }
    val rules = listOf(
        Triple(Glyph.Star, Tx.s(R.string.grid_form_rating_from), Tx.s(R.string.grid_form_stars_at_least, "4")),
        Triple(Glyph.Gamepad, Tx.s(R.string.grid_form_developer), "Capcom"), // tx-ok: a studio's name, as in the help
        Triple(Glyph.Clock, Tx.s(R.string.grid_form_played_more), Tx.p(R.plurals.grid_form_hours, 10, 10)),
        Triple(Glyph.Refresh, Tx.s(R.string.grid_form_last), Tx.s(R.string.grid_form_last_never)),
    )
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.offset(10.dp, 8.dp).width(196.dp).arrive(0).card(corner = 18.dp).padding(6.dp)) {
            rules.forEachIndexed { i, (glyph, label, value) ->
                Box(Modifier.graphicsLayer { alpha = 0.35f + 0.65f * seg(t.value, 0.05f + i * 0.08f, 0.12f + i * 0.08f) }) {
                    MenuPill(glyph, label, Palette.Lavender, value = value)
                }
            }
            Spacer(Modifier.height(4.dp))
            // All rules / Any rule, the one in use lit.
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for ((k, label) in listOf(Tx.s(R.string.grid_form_combine_all), Tx.s(R.string.grid_form_combine_any)).withIndex()) {
                    val on = (k == 1) == any
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(50)).background(if (on) Palette.Lavender else Palette.CardHigh).padding(vertical = 4.dp),
                        contentAlignment = Alignment.Center,
                    ) { FitText(label, MaterialTheme.typography.labelMedium, color = if (on) Palette.NightDeep else Palette.Muted) }
                }
            }
        }
        // The list it makes: covers re-sorting themselves (favourites first: the hearted one rises to the top).
        val order = if (any) listOf(2, 0, 1, 3) else listOf(0, 1, 2, 3)
        for (i in 0 until 4) {
            val slot = order.indexOf(i)
            Row(
                Modifier.offset { IntOffset(220.dp.roundToPx(), (12 + slot * 52).dp.roundToPx()) }.width(130.dp).arrive(1 + i),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SampleCover(i + 10, Modifier.size(40.dp, 44.dp).clip(RoundedCornerShape(8.dp)))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(Modifier.fillMaxWidth(0.9f - i * 0.12f).height(7.dp).clip(RoundedCornerShape(50)).background(Palette.Text.copy(alpha = 0.7f)))
                    Box(Modifier.fillMaxWidth(0.5f).height(5.dp).clip(RoundedCornerShape(50)).background(Palette.Muted.copy(alpha = 0.6f)))
                }
                if (i == 2) Text("♥", color = Palette.Peach, style = MaterialTheme.typography.titleSmall) // tx-ok: a heart
            }
        }
    }
}

// --- 5. Search filters ------------------------------------------------------------------------------------------

/** Search with its filter chips: they are set one by one and the results narrow, with the count under them. */
@Composable
internal fun SearchVisual() {
    val t = rememberLoop(6000, still = 0.75f)
    val set by remember { derivedStateOf { (t.value * 5f).toInt().coerceIn(0, 4) } }
    val chips = listOf(
        Tx.s(R.string.menus_search_filter_system) to "SNES", // tx-ok: a system's short name
        Tx.s(R.string.menus_search_filter_genre) to "RPG", // tx-ok: a sample genre, as scraped
        Tx.s(R.string.menus_search_filter_year) to "1990s", // tx-ok: a sample decade
        Tx.s(R.string.menus_search_filter_rating) to "★ 4+", // tx-ok: stars
    )
    val counts = listOf(12, 8, 5, 3, 2)
    Box(Modifier.fillMaxSize()) {
        // The search bar.
        Row(
            Modifier.offset(14.dp, 8.dp).width(332.dp).height(38.dp).arrive(0).card(corner = 19.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlyphIcon(Glyph.Search, Palette.Mint, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Box(Modifier.size(2.dp, 18.dp).background(Palette.Mint.copy(alpha = if ((t.value * 8f).toInt() % 2 == 0) 1f else 0f)))
        }
        // The chips: each set in turn, its value in place of the name.
        Row(Modifier.offset(14.dp, 56.dp).arrive(1), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            chips.forEachIndexed { i, (name, value) ->
                val on = i < set
                Box(
                    Modifier.clip(RoundedCornerShape(50)).background(if (on) Palette.Mint.copy(alpha = 0.25f) else Palette.Card)
                        .border(1.5.dp, if (i == set) Palette.Text else Color.Transparent, RoundedCornerShape(50))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                ) { FitText(if (on) value else name, MaterialTheme.typography.labelLarge, color = if (on) Palette.Mint else Palette.Text) }
            }
        }
        // The results: covers that pass, the rest fading away.
        val shown = counts[set]
        for (k in 0 until 12) {
            val r = k / 6
            val c = k % 6
            SampleCover(
                k + 20,
                Modifier.offset((14 + c * 56).dp, (98 + r * 64).dp).size(48.dp, 56.dp).arrive(2 + r)
                    .graphicsLayer {
                        alpha = if (k < shown) 1f else 0.12f
                        val s = if (k < shown) 1f else 0.9f
                        scaleX = s; scaleY = s
                        shape = RoundedCornerShape(8.dp); clip = true
                    },
            )
        }
    }
}

// --- 6. RetroAchievements ---------------------------------------------------------------------------------------

/**
 * A game's achievement list with its filters, a locked one highlighted and its players' hints opening beside it,
 * and a disc matched by its hash.
 */
@Composable
internal fun AchievementsVisual() {
    val t = rememberLoop(5600, still = 0.6f)
    val hint = { v: Float -> seg(v, 0.3f, 0.42f) * (1f - seg(v, 0.88f, 0.96f)) }
    val sign = startSign
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.offset(10.dp, 8.dp).width(200.dp).arrive(0).card(corner = 18.dp).padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                TinyChip(Tx.s(R.string.accounts_ra_list_all), Palette.Muted)
                TinyChip(Tx.s(R.string.accounts_ra_list_locked), Palette.Butter)
                TinyChip(Tx.s(R.string.accounts_ra_list_missable), Palette.Muted)
            }
            for (i in 0 until 4) {
                val on = i == 1
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                        .then(if (on) Modifier.border(2.dp, Palette.Butter, RoundedCornerShape(10.dp)) else Modifier)
                        .padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(28.dp).clip(RoundedCornerShape(7.dp)).background(if (i == 0) Palette.Butter else Palette.CardHigh),
                        contentAlignment = Alignment.Center,
                    ) { GlyphIcon(if (i == 0) Glyph.Trophy else Glyph.Key, if (i == 0) Palette.NightDeep else Palette.Muted, Modifier.padding(6.dp)) }
                    Spacer(Modifier.width(6.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Box(Modifier.fillMaxWidth(0.85f - i * 0.1f).height(6.dp).clip(RoundedCornerShape(50)).background(Palette.Text.copy(alpha = 0.7f)))
                        Box(Modifier.fillMaxWidth(0.55f).height(4.dp).clip(RoundedCornerShape(50)).background(Palette.Muted.copy(alpha = 0.6f)))
                    }
                    FitText(Tx.p(R.plurals.accounts_ra_list_pts, (i + 1) * 5, (i + 1) * 5), MaterialTheme.typography.labelSmall, color = Palette.Butter)
                }
            }
        }
        // Players' hints, out beside the highlighted one.
        Column(
            Modifier.offset(216.dp, 30.dp).width(134.dp).graphicsLayer {
                val a = hint(t.value)
                alpha = a
                translationX = (1f - a) * -18.dp.toPx() * sign
            }.card(corner = 16.dp, shadow = 12f).padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlyphChip(Glyph.Info, Palette.Butter, size = 22.dp, round = true)
                Spacer(Modifier.width(6.dp))
                FitText(Tx.s(R.string.accounts_ra_list_hints), MaterialTheme.typography.labelLarge)
            }
            for (l in 0 until 3) Box(Modifier.fillMaxWidth(1f - l * 0.2f).height(5.dp).clip(RoundedCornerShape(50)).background(Palette.Text.copy(alpha = 0.5f)))
            Spacer(Modifier.height(2.dp))
            for (l in 0 until 2) Box(Modifier.fillMaxWidth(0.9f - l * 0.3f).height(5.dp).clip(RoundedCornerShape(50)).background(Palette.Text.copy(alpha = 0.5f)))
        }
        // A disc, matched by its hash.
        Row(Modifier.offset(220.dp, 170.dp).arrive(3), verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.size(34.dp).graphicsLayer { rotationZ = t.value * 360f }) {
                val r = size.minDimension / 2f
                drawCircle(Brush.sweepGradient(listOf(Palette.Butter, Palette.Peach, Palette.Lavender, Palette.Butter)), r)
                drawCircle(Palette.NightDeep, r * 0.28f)
            }
            Spacer(Modifier.width(8.dp))
            Box(Modifier.clip(RoundedCornerShape(50)).background(Palette.Mint.copy(alpha = 0.22f)).padding(horizontal = 8.dp, vertical = 3.dp)) {
                Text("#  ✓", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold), color = Palette.Mint) // tx-ok: a hash sign and a tick
            }
        }
    }
}

// --- 7. The Settings home ---------------------------------------------------------------------------------------

/** The Settings home itself, small: its eight tiles with their live sketches, and the highlight gliding over them. */
@Composable
internal fun SettingsVisual() {
    val t = rememberLoop(8 * 900, still = 0.3f)
    val ids = SettingsCategories.ids + "search"
    val glyphs = listOf(Glyph.Theme, Glyph.Home, Glyph.Screens, Glyph.Music, Glyph.Gamepad, Glyph.Cartridge, Glyph.Chart, Glyph.Search)
    val at by remember { derivedStateOf { (t.value * 8f).toInt().coerceIn(0, 7) } }
    val shape = RoundedCornerShape(16.dp)
    // The tiles are pictures here: they never take the focus, which stays on the deck's own buttons.
    Box(Modifier.fillMaxSize().focusProperties { canFocus = false }) {
        for (i in ids.indices) {
            val r = i / 4
            val c = i % 4
            val name = if (ids[i] == "search") Tx.s(R.string.settings_hint_search) else SettingsCategories.title(ids[i])
            Box(
                Modifier.offset((8 + c * 87).dp, (6 + r * 112).dp).size(80.dp, 104.dp).arrive(i).graphicsLayer {
                    val on = i == at
                    val s = if (on) 1.06f else 1f
                    scaleX = s; scaleY = s
                }.then(if (i == at) Modifier.border(2.dp, Palette.Accent, shape) else Modifier),
            ) {
                SettingsHomeTile(name, glyphs[i], Modifier.fillMaxSize().focusProperties { canFocus = false }, shape, onFocus = {}, onClick = {}) {
                    SettingsTileHint(ids[i])
                }
            }
        }
    }
}

// --- 8. A new soundtrack ----------------------------------------------------------------------------------------

/** An equaliser bouncing to a tune, notes floating up from it, and Pip bobbing along. */
@Composable
internal fun MusicVisual() {
    val t = rememberLoop(4800, still = 0.35f)
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.offset(40.dp, 40.dp).size(280.dp, 150.dp).arrive(0).card(corner = 22.dp, shadow = 10f))
        // The equaliser: soft bars in the theme's colours, each on its own beat.
        Canvas(Modifier.offset(64.dp, 70.dp).size(232.dp, 96.dp).arrive(1)) {
            val n = 9
            val bw = size.width / (n * 1.6f)
            val gap = (size.width - bw * n) / (n - 1)
            val a = Palette.accents
            for (i in 0 until n) {
                val level = 0.25f + 0.75f * abs(sin((t.value * (2 + i % 3) + i * 0.17f) * PI.toFloat()))
                val h = (size.height * level).coerceAtLeast(bw)
                val col = a[i % a.size]
                drawRoundRect(
                    Brush.verticalGradient(listOf(lerp(col, Color.White, 0.3f), col), size.height - h, size.height),
                    Offset(i * (bw + gap), size.height - h), Size(bw, h), CornerRadius(bw / 2f),
                )
            }
        }
        // Notes rising and swaying, fading as they go.
        Canvas(Modifier.fillMaxSize().mirroredInRtl().arrive(2)) {
            for (k in 0 until 5) {
                val p = (t.value * 1.5f + k / 5f) % 1f
                val x = (80 + k * 50).dp.toPx() + sin((p * 2f + k) * PI.toFloat()) * 8.dp.toPx()
                val y = (170 - p * 150).dp.toPx()
                val col = Palette.accents[(k + 1) % Palette.accents.size].copy(alpha = sin(p * PI.toFloat()))
                note(Offset(x, y), 7.dp.toPx(), col, double = k % 2 == 1)
            }
        }
        Pip(
            42.dp, Modifier.offset(286.dp, 4.dp).arrive(3).graphicsLayer { translationY = sin(t.value * 4f * PI.toFloat()) * 3.dp.toPx() },
            mood = 1, bounce = false,
        )
    }
}

/** A music note (a quaver, or two beamed when [double]) with its head at [at]. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.note(at: Offset, r: Float, color: Color, double: Boolean) {
    val stem = r * 3.4f
    drawOval(color, Offset(at.x - r * 1.2f, at.y - r * 0.8f), Size(r * 2.4f, r * 1.6f))
    drawLine(color, Offset(at.x + r * 1.05f, at.y), Offset(at.x + r * 1.05f, at.y - stem), r * 0.35f)
    if (double) {
        val x2 = at.x + r * 3.2f
        drawOval(color, Offset(x2 - r * 1.2f, at.y - r * 0.8f + r * 0.6f), Size(r * 2.4f, r * 1.6f))
        drawLine(color, Offset(x2 + r * 1.05f, at.y + r * 0.6f), Offset(x2 + r * 1.05f, at.y - stem + r * 0.6f), r * 0.35f)
        drawLine(color, Offset(at.x + r * 1.05f, at.y - stem), Offset(x2 + r * 1.05f, at.y - stem + r * 0.6f), r * 0.7f)
    } else {
        val flag = Path().apply {
            moveTo(at.x + r * 1.05f, at.y - stem)
            quadraticTo(at.x + r * 2.6f, at.y - stem + r * 1.2f, at.x + r * 2.0f, at.y - stem + r * 2.4f)
        }
        drawPath(flag, color, style = Stroke(r * 0.35f))
    }
}
