package io.github.zahakj.tandem.ui.whatsnew

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.zahakj.tandem.R
import io.github.zahakj.tandem.Tx
import io.github.zahakj.tandem.audio.Plaza
import io.github.zahakj.tandem.data.WeekRow
import io.github.zahakj.tandem.data.WeekSlot
import io.github.zahakj.tandem.pass.AvatarPart
import io.github.zahakj.tandem.pass.AvatarSpec
import io.github.zahakj.tandem.ui.AvatarView
import io.github.zahakj.tandem.ui.ButtonHint
import io.github.zahakj.tandem.ui.FitText
import io.github.zahakj.tandem.ui.Glyph
import io.github.zahakj.tandem.ui.GlyphChip
import io.github.zahakj.tandem.ui.GreetingBubble
import io.github.zahakj.tandem.ui.MoodArt
import io.github.zahakj.tandem.ui.Palette
import io.github.zahakj.tandem.ui.Pill
import io.github.zahakj.tandem.ui.Pip
import io.github.zahakj.tandem.ui.PlazaLook
import io.github.zahakj.tandem.ui.PlazaPainter
import io.github.zahakj.tandem.ui.PlazaWorld
import io.github.zahakj.tandem.ui.SettingsCategories
import io.github.zahakj.tandem.ui.SettingsHomeTile
import io.github.zahakj.tandem.ui.SettingsTileHint
import io.github.zahakj.tandem.ui.TileNameInset
import io.github.zahakj.tandem.ui.WeekText
import io.github.zahakj.tandem.ui.avatarTheme
import io.github.zahakj.tandem.ui.drawAvatar
import io.github.zahakj.tandem.ui.mirroredInRtl
import io.github.zahakj.tandem.ui.showcaseStill
import io.github.zahakj.tandem.ui.startSign
import io.github.zahakj.tandem.ui.tileNameStyle
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/*
 * The 0.11 deck's pictures, made the way the 0.9 and 0.10 ones are (Visuals09.kt, Visuals010.kt): live pieces of
 * Tandem with sample data, laid out at the design size from the start side, arriving part by part and then playing on
 * a loop; Reduce motion holds each on its most telling moment. The characters are samples, nobody real; the words
 * are Tandem's own (greetings, Me's tabs, Settings rows, the soundtracks' moods), so every language gets its own.
 */

/** Sample characters: one for you, the rest people met on the way. */
private val You = AvatarSpec(shape = 0, skin = 2, eyes = 1, hair = 6, hairColor = 8, top = 4, topColor = 7, theme = 7, cheeks = 1)
private val Crowd = listOf(
    AvatarSpec(shape = 3, skin = 7, eyes = 6, eyeColor = 3, hair = 7, hairColor = 0, headwear = 3, top = 1, topColor = 3, theme = 3, mouth = 1),
    AvatarSpec(shape = 4, skin = 4, eyes = 2, hair = 4, hairColor = 5, glasses = 1, top = 2, topColor = 5, theme = 4, mouth = 2, cheeks = 3),
    AvatarSpec(shape = 1, skin = 9, eyes = 5, hair = 8, hairColor = 10, headwear = 1, top = 3, topColor = 0, theme = 1, mouth = 4),
    AvatarSpec(shape = 2, skin = 11, eyes = 3, hair = 10, hairColor = 2, top = 5, topColor = 9, theme = 6, cheeks = 2),
    AvatarSpec(shape = 5, skin = 1, eyes = 7, hair = 12, hairColor = 6, earrings = 1, top = 0, topColor = 4, theme = 8, mouth = 3),
    AvatarSpec(shape = 0, skin = 5, eyes = 4, hair = 2, hairColor = 11, headwear = 6, top = 6, topColor = 1, theme = 2),
)

/** A rounded card in the theme's card colour, with a soft shadow. */
private fun Modifier.card(corner: Dp = 16.dp, shadow: Float = 8f): Modifier =
    graphicsLayer { shape = RoundedCornerShape(corner); clip = true; shadowElevation = shadow * density }.background(Palette.Card)

/** [spec]'s head and shoulders, drawn in this box (the way Me's choices show a hairstyle). */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.head(spec: AvatarSpec, zoom: Float = 1.45f, py: Float = 40f) {
    val u = min(size.width / 100f, size.height / 120f)
    val ox = (size.width - 100f * u) / 2f
    val oy = size.height - 120f * u
    val pivot = Offset(ox + 50f * u, oy + py * u)
    withTransform({
        translate(size.width / 2f - pivot.x, size.height / 2f - pivot.y)
        scale(zoom, zoom, pivot)
    }) { drawAvatar(spec, shadow = false) }
}

// --- 1. Bump ----------------------------------------------------------------------------------------------------

/**
 * Two handhelds, you and someone else, on either side; they come near, Bluetooth ripples between them, and each one's
 * card flies over to the other, who waves and says hello.
 */
@Composable
internal fun PassVisual() {
    val t = rememberLoop(5600, still = 0.8f)
    val got by remember { derivedStateOf { t.value in 0.62f..0.97f } }
    val them = Crowd[0]
    val sign = startSign
    Box(Modifier.fillMaxSize()) {
        // The two of you, gliding together and apart.
        listOf(You to 0, them to 1).forEach { (spec, side) ->
            val theme = avatarTheme(spec)
            Box(
                Modifier.offset(if (side == 0) 16.dp else 230.dp, 52.dp).size(114.dp, 150.dp).arrive(side)
                    .graphicsLayer {
                        val near = seg(t.value, 0.05f, 0.25f) - seg(t.value, 0.8f, 0.97f)
                        val dir = if (side == 0) 1f else -1f
                        translationX = dir * near * 22.dp.toPx() * sign
                    }
                    .clip(RoundedCornerShape(24.dp))
                    .background(Brush.verticalGradient(listOf(theme.copy(alpha = 0.55f), Palette.Card, Palette.Card)))
                    .border(2.dp, theme.copy(alpha = 0.8f), RoundedCornerShape(24.dp)),
            ) {
                AvatarView(spec, Modifier.fillMaxSize().padding(top = 18.dp, bottom = 8.dp), waving = got, seed = side * 17)
            }
        }
        // Their hello, once the cards have crossed.
        Box(Modifier.offset(150.dp, 8.dp).width(200.dp).arrive(3).graphicsLayer { alpha = seg(t.value, 0.62f, 0.7f) * (1f - seg(t.value, 0.9f, 0.97f)) }) {
            GreetingBubble(Tx.s(R.string.plaza_greeting_2), avatarTheme(them), Modifier.align(Alignment.TopEnd))
        }
        // The ripples between you, and the two cards crossing over them.
        Canvas(Modifier.fillMaxSize().mirroredInRtl().arrive(2)) {
            val cx = size.width / 2f
            val cy = 128.dp.toPx()
            val on = seg(t.value, 0.2f, 0.3f) * (1f - seg(t.value, 0.66f, 0.74f))
            for (k in 0 until 3) {
                val p = (t.value * 3.2f + k / 3f) % 1f
                drawCircle(Palette.Sky.copy(alpha = on * 0.7f * (1f - p)), 10.dp.toPx() + p * 46.dp.toPx(), Offset(cx, cy), style = Stroke(2.dp.toPx()))
            }
            val fly = seg(t.value, 0.32f, 0.62f)
            if (fly > 0f && fly < 1f) {
                for (side in 0..1) {
                    val from = if (side == 0) 73.dp.toPx() else size.width - 73.dp.toPx()
                    val to = size.width - from
                    val x = from + (to - from) * fly
                    val y = cy + (if (side == 0) -1f else 1f) * sin(fly * PI.toFloat()) * 52.dp.toPx()
                    val spec = if (side == 0) You else them
                    val w = 34.dp.toPx(); val h = 42.dp.toPx()
                    withTransform({ translate(x - w / 2f, y - h / 2f); rotate((fly - 0.5f) * 24f * (if (side == 0) 1f else -1f), Offset(w / 2f, h / 2f)) }) {
                        drawRoundRect(Palette.Card, size = Size(w, h), cornerRadius = CornerRadius(8.dp.toPx()))
                        drawRoundRect(avatarTheme(spec), size = Size(w, h), cornerRadius = CornerRadius(8.dp.toPx()), style = Stroke(2.dp.toPx()))
                        // The character on the flying card, small.
                        val pad = 2.dp.toPx(); val top = 4.dp.toPx()
                        withTransform({ translate(pad, top) }) {
                            drawAvatar(spec, shadow = false, box = Size(w - 4.dp.toPx(), h - 6.dp.toPx()))
                        }
                    }
                }
            }
        }
        Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp).arrive(4)) {
            GlyphChip(Glyph.Bump, Palette.Sky, size = 30.dp, round = true)
        }
    }
}

// --- 2. The Plaza -----------------------------------------------------------------------------------------------

/**
 * The Plaza itself, small: the square in the afternoon with a few sample people strolling, chatting and sitting, the
 * highlight stepping from one to the next as each stops to wave.
 */
@Composable
internal fun PlazaVisual() {
    val still = showcaseStill()
    val world = remember { PlazaWorld(11L) }
    val painter = remember { PlazaPainter() }
    val accents = Palette.accents
    val look = remember(accents) { PlazaLook(accents) }
    val frame = remember { mutableLongStateOf(0L) }
    val time = remember { floatArrayOf(0f) }
    val keys = remember { Crowd.indices.map { "sample$it" } }
    val t = rememberLoop(12000, still = 0.1f)
    val who by remember { derivedStateOf { (t.value * 3f).toInt().coerceIn(0, 2) * 2 } }
    SideEffect {
        world.sync(You, keys, Crowd, Crowd.indices.map { it == 1 })
        world.focus(keys[who])
        if (still) world.arrange()
        frame.longValue++
    }
    LaunchedEffect(still) {
        if (still) return@LaunchedEffect
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else (now - last) / 1e9f
                last = now
                world.step(dt)
                time[0] += dt.coerceAtMost(0.05f)
                frame.longValue = now
            }
        }
    }
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize().arrive(0).clip(RoundedCornerShape(20.dp))) {
            frame.longValue
            painter.draw(this, world, time[0], 15.5f, look, still)
        }
        Row(
            Modifier.offset(10.dp, 8.dp).arrive(1).clip(RoundedCornerShape(50)).background(Palette.Card.copy(alpha = 0.9f))
                .padding(start = 3.dp, end = 10.dp, top = 3.dp, bottom = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlyphChip(Glyph.Fountain, Palette.Mint, size = 20.dp, round = true)
            Spacer(Modifier.width(6.dp))
            FitText(Tx.p(R.plurals.plaza_met_count, Crowd.size, Crowd.size), MaterialTheme.typography.labelLarge, color = Palette.Text)
        }
    }
}

// --- 3. Me ------------------------------------------------------------------------------------------------------

/** Me: you on a little stage, Me's tabs on the Hair page, the highlight walking its choices as you try each one on. */
@Composable
internal fun MeVisual() {
    val t = rememberLoop(8 * 1100, still = 0.45f)
    val hairs = listOf(6, 1, 3, 9, 12, 5, 11, 13)
    val at by remember { derivedStateOf { (t.value * hairs.size).toInt().coerceIn(0, hairs.lastIndex) } }
    val worn = You.with(AvatarPart.Hair, hairs[at])
    val theme = avatarTheme(worn)
    Box(Modifier.fillMaxSize()) {
        // The stage.
        Column(
            Modifier.offset(10.dp, 8.dp).size(124.dp, 216.dp).arrive(0)
                .clip(RoundedCornerShape(24.dp))
                .background(Brush.verticalGradient(listOf(theme.copy(alpha = 0.34f), Palette.Card.copy(alpha = 0.7f), theme.copy(alpha = 0.22f))))
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            GreetingBubble(Tx.s(R.string.plaza_greeting_1), theme)
            AvatarView(worn, Modifier.weight(1f).fillMaxWidth().padding(top = 4.dp), react = at, seed = 5)
        }
        Column(Modifier.offset(144.dp, 8.dp).width(206.dp)) {
            // The tabs, Hair on.
            Row(Modifier.fillMaxWidth().arrive(1), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                listOf(R.string.plaza_tab_face, R.string.plaza_tab_eyes, R.string.plaza_tab_hair, R.string.plaza_tab_hat).forEachIndexed { i, res ->
                    val on = i == 2
                    Box(
                        Modifier.weight(1f).height(24.dp).clip(RoundedCornerShape(50)).background(if (on) Palette.Accent else Palette.Card.copy(alpha = 0.7f)),
                        contentAlignment = Alignment.Center,
                    ) { FitText(Tx.s(res), MaterialTheme.typography.labelSmall, Modifier.padding(horizontal = 3.dp), color = if (on) Palette.OnAccent else Palette.Text.copy(alpha = 0.75f)) }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(Tx.s(R.string.plaza_part_hair), style = MaterialTheme.typography.labelLarge, color = Palette.Accent, modifier = Modifier.arrive(2), maxLines = 1)
            Spacer(Modifier.height(4.dp))
            for (r in 0..1) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 10.dp)) {
                    for (c in 0..3) {
                        val i = r * 4 + c
                        val on = i == at
                        Box(
                            Modifier.size(44.dp).arrive(3 + i).graphicsLayer { val s = if (on) 1.08f else 1f; scaleX = s; scaleY = s }
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (on) Palette.Accent.copy(alpha = 0.22f) else Palette.Card.copy(alpha = 0.55f))
                                .then(if (on) Modifier.border(2.dp, Palette.Accent, RoundedCornerShape(12.dp)) else Modifier),
                        ) { Canvas(Modifier.fillMaxSize()) { head(You.with(AvatarPart.Hair, hairs[i])) } }
                    }
                }
            }
            Row(Modifier.arrive(11).padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ButtonHint("X", Tx.s(R.string.plaza_hint_random), Palette.Butter, tappable = false)
                ButtonHint("Y", Tx.s(R.string.plaza_hint_undo), Palette.Peach, tappable = false)
            }
        }
    }
}

// --- 4. Three grooves -------------------------------------------------------------------------------------------

/** The three grooves' mood pictures down the start side, and the now-playing card turning from one to the next. */
@Composable
internal fun GroovesVisual() {
    val songs = Plaza.all
    val t = rememberLoop(songs.size * 2600, still = 0.1f)
    val at by remember { derivedStateOf { (t.value * songs.size).toInt().coerceIn(0, songs.lastIndex) } }
    Box(Modifier.fillMaxSize()) {
        songs.forEachIndexed { i, s ->
            val on = i == at
            Box(
                Modifier.offset(10.dp, (10 + i * 72).dp).size(104.dp, 64.dp).arrive(i)
                    .graphicsLayer { val k = if (on) 1.05f else 1f; scaleX = k; scaleY = k; alpha = if (on) 1f else 0.7f }
                    .clip(RoundedCornerShape(14.dp))
                    .then(if (on) Modifier.border(2.dp, Palette.Accent, RoundedCornerShape(14.dp)) else Modifier),
            ) {
                MoodArt(s.id, Modifier.fillMaxSize())
                Box(Modifier.align(Alignment.BottomStart).padding(5.dp).clip(RoundedCornerShape(50)).background(Palette.Card.copy(alpha = 0.85f)).padding(horizontal = 7.dp, vertical = 1.dp)) {
                    FitText(s.title, MaterialTheme.typography.labelSmall, color = Palette.Text)
                }
            }
        }
        // Now playing.
        Column(Modifier.offset(128.dp, 10.dp).size(222.dp, 212.dp).arrive(3).card(corner = 20.dp, shadow = 10f).padding(10.dp)) {
            Turning(at, Modifier.fillMaxWidth().weight(1f)) { i ->
                val s = songs[i]
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(14.dp))) {
                        MoodArt(s.id, Modifier.fillMaxSize())
                        Bars(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(30.dp).padding(horizontal = 12.dp, vertical = 6.dp), i)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(s.title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                            Text(s.mood, style = MaterialTheme.typography.bodySmall, color = Palette.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        Spacer(Modifier.width(6.dp))
                        Pill(Tx.s(R.string.sound_now_playing), Palette.Peach)
                    }
                }
            }
        }
    }
}

/** Soft bars bouncing along, each on its own beat (the now-playing card's, without the music's levels). */
@Composable
private fun Bars(modifier: Modifier, seed: Int) {
    val t = rememberLoop(3200, still = 0.3f)
    Canvas(modifier) {
        val n = 14
        val gap = size.width / n
        val bw = gap * 0.55f
        val colors = listOf(Palette.Mint, Palette.Sky, Palette.Lavender, Palette.Peach, Palette.Butter)
        for (i in 0 until n) {
            val level = 0.2f + 0.8f * abs(sin((t.value * (2 + (i + seed) % 3) + i * 0.23f) * PI.toFloat()))
            val h = size.height * level
            drawRoundRect(colors[i % colors.size].copy(alpha = 0.9f), Offset(i * gap + (gap - bw) / 2f, size.height - h), Size(bw, h), CornerRadius(bw / 2f))
        }
    }
}

// --- 5. A glyph for every setting -------------------------------------------------------------------------------

/** Sound & music's boxy tile on the start side, and its page beside it, each row with a glyph of its own. */
@Composable
internal fun GlyphsVisual() {
    val rows = listOf(
        Glyph.Music to R.string.sound_music, Glyph.Headphones to R.string.sound_music_volume, Glyph.Shuffle to R.string.sound_shuffle,
        Glyph.Skip to R.string.sound_next, Glyph.Bubble to R.string.sound_pip_voice, Glyph.Volume to R.string.sound_volume,
    )
    val tints = listOf(Palette.Peach, Palette.Peach, Palette.Peach, Palette.Peach, Palette.Lavender, Palette.Butter)
    val t = rememberLoop(rows.size * 900, still = 0.05f)
    val at by remember { derivedStateOf { (t.value * rows.size).toInt().coerceIn(0, rows.lastIndex) } }
    Box(Modifier.fillMaxSize().focusProperties { canFocus = false }) {
        val name = SettingsCategories.title("sound")
        val style = tileNameStyle(listOf(name), 150.dp - TileNameInset)
        val shape = RoundedCornerShape(16.dp)
        Box(Modifier.offset(8.dp, 58.dp).size(150.dp, 112.dp).arrive(0).border(2.dp, Palette.Accent, shape)) {
            SettingsHomeTile(name, style, Glyph.Music, Modifier.fillMaxSize().focusProperties { canFocus = false }, shape, onFocus = {}, onClick = {}) {
                SettingsTileHint("sound")
            }
        }
        Box(
            Modifier.offset(168.dp, 6.dp).size(184.dp, 220.dp).arrive(1).card(corner = 18.dp).background(Palette.CardHigh)
                .padding(horizontal = 4.dp, vertical = 6.dp),
        ) {
            RowHighlight(Palette.Accent, 34.dp) { at * 34.5f }
            Column {
                rows.forEachIndexed { i, (g, res) ->
                    MenuPill(g, Tx.s(res), tints[i], Modifier.arrive(2 + i))
                }
            }
        }
    }
}

// --- 6. Switch: one save ----------------------------------------------------------------------------------------

/**
 * The two Peanuts apps side by side, a game built in both. Its save hops over from Peanuts Native+ to the official
 * Peanuts Native, which plays it, and Pip says it carried on.
 */
@Composable
internal fun SwitchVisual() {
    val t = rememberLoop(5200, still = 0.85f)
    Box(Modifier.fillMaxSize()) {
        // The two apps, each with the game built in it.
        val apps = listOf("Peanuts Native", "Peanuts Native+") // tx-ok: app names
        apps.forEachIndexed { i, name ->
            val lit = i == 0 && t.value > 0.5f
            val tint = if (i == 0) Palette.Mint else Palette.Lavender
            Column(
                Modifier.offset(if (i == 0) 12.dp else 188.dp, 12.dp).size(160.dp, 146.dp).arrive(i)
                    .card(corner = 18.dp).background(Palette.CardHigh)
                    .then(if (lit) Modifier.border(2.dp, Palette.Mint, RoundedCornerShape(18.dp)) else Modifier)
                    .padding(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GlyphChip(Glyph.Cartridge, tint, size = 26.dp)
                    Spacer(Modifier.width(6.dp))
                    FitText(name, MaterialTheme.typography.titleSmall, color = Palette.Text, minScale = 0.7f)
                }
                Spacer(Modifier.height(10.dp))
                Box(
                    Modifier.size(64.dp, 86.dp).graphicsLayer {
                        // The official app's copy is the one that starts: it pops when the save is in.
                        val pop = if (i == 0) seg(t.value, 0.46f, 0.52f) * (1f - seg(t.value, 0.52f, 0.62f)) else 0f
                        val s = 1f + 0.1f * pop
                        scaleX = s; scaleY = s
                        alpha = if (i == 1) 1f - 0.45f * seg(t.value, 0.5f, 0.6f) * (1f - seg(t.value, 0.95f, 1f)) else 1f
                    }.clip(RoundedCornerShape(10.dp)),
                ) {
                    SampleCover(7, Modifier.fillMaxSize())
                    if (lit) Box(Modifier.align(Alignment.Center)) { GlyphChip(Glyph.Play, Palette.Mint, size = 30.dp, round = true) }
                }
            }
        }
        // The save, hopping from Peanuts Native+ to Peanuts Native.
        Canvas(Modifier.fillMaxSize().mirroredInRtl().arrive(2)) {
            val p = seg(t.value, 0.12f, 0.46f)
            val on = seg(t.value, 0.06f, 0.12f) * (1f - seg(t.value, 0.46f, 0.52f))
            if (on <= 0f) return@Canvas
            val from = Offset(268.dp.toPx(), 104.dp.toPx())
            val to = Offset(92.dp.toPx(), 104.dp.toPx())
            val lift = 70.dp.toPx()
            fun at(q: Float) = Offset(from.x + (to.x - from.x) * q, from.y - sin(q * PI.toFloat()) * lift)
            // A dotted trail behind it.
            for (k in 1..6) {
                val q = (p - k * 0.04f).coerceAtLeast(0f)
                drawCircle(Palette.Butter.copy(alpha = on * 0.6f * (1f - k / 7f)), 2.5.dp.toPx(), at(q))
            }
            val c = at(p)
            drawCircle(Palette.Butter.copy(alpha = on), 15.dp.toPx(), c)
            drawCircle(Palette.Card.copy(alpha = on), 12.dp.toPx(), c)
            // A little floppy: the save.
            val r = 7.dp.toPx()
            drawRoundRect(Palette.Butter.copy(alpha = on), Offset(c.x - r, c.y - r), Size(2 * r, 2 * r), CornerRadius(1.5.dp.toPx()))
            drawRect(Palette.Card.copy(alpha = on), Offset(c.x - r * 0.5f, c.y - r), Size(r, r * 0.7f))
            drawRect(Palette.Card.copy(alpha = on), Offset(c.x - r * 0.6f, c.y + r * 0.15f), Size(r * 1.2f, r * 0.75f))
        }
        // Pip says so.
        Row(
            Modifier.offset(12.dp, 172.dp).arrive(3)
                .graphicsLayer { alpha = seg(t.value, 0.52f, 0.6f) * (1f - seg(t.value, 0.95f, 1f)) }
                .clip(RoundedCornerShape(50)).background(Palette.CardHigh).padding(start = 4.dp, end = 14.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Pip(34.dp, mood = 1, bounce = false)
            Spacer(Modifier.width(8.dp))
            FitText(Tx.s(R.string.play_save_from_native), MaterialTheme.typography.titleSmall, color = Palette.Text)
        }
    }
}

// --- 7. A steadier highlight ------------------------------------------------------------------------------------

/**
 * This week's row: X re-rolls it, the covers flip over to new ones and the highlight stays right where it was, its
 * whole ring showing.
 */
@Composable
internal fun SteadyVisual() {
    val t = rememberLoop(4800, still = 0.7f)
    val rolled by remember { derivedStateOf { t.value in 0.42f..0.999f } }
    val focus = 1
    Box(Modifier.fillMaxSize()) {
        // The row's heading, and X to re-roll it at the end.
        Row(Modifier.offset(14.dp, 16.dp).width(332.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).arrive(0), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(Palette.Mint))
                Spacer(Modifier.width(6.dp))
                FitText(WeekText.title(WeekRow(WeekSlot.Resume, emptyList())), MaterialTheme.typography.titleSmall)
            }
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.arrive(1).graphicsLayer { val s = 1f + 0.18f * (seg(t.value, 0.3f, 0.36f) - seg(t.value, 0.36f, 0.44f)); scaleX = s; scaleY = s },
            ) { ButtonHint("X", Tx.s(R.string.week_hint_reroll), Palette.Sky, tappable = false) }
        }
        for (i in 0 until 4) {
            val on = i == focus
            Box(
                Modifier.offset((18 + i * 84).dp, 58.dp).size(72.dp, 96.dp).arrive(2 + i)
                    .graphicsLayer {
                        // Each cover turns over in its turn, the new one on its back.
                        val a = 0.36f + i * 0.04f
                        val p = ((t.value - a) / 0.12f).coerceIn(0f, 1f)
                        scaleX = abs(cos(p * PI.toFloat())).coerceAtLeast(0.02f) * (if (on) 1.1f else 1f)
                        scaleY = if (on) 1.1f else 1f
                    },
            ) {
                val p = ((t.value - (0.36f + i * 0.04f)) / 0.12f).coerceIn(0f, 1f)
                val k = if (p > 0.5f && t.value < 0.999f) i + 9 else i + 2
                SampleCover(k, Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)))
            }
        }
        // The highlight: on the same spot before and after, never clipped.
        Box(
            Modifier.offset((18 + focus * 84 - 4).dp, 54.dp).size(80.dp, 104.dp).arrive(6)
                .border(2.5.dp, Palette.Text, RoundedCornerShape(15.dp)),
        )
        // The re-rolled row's reason chips, as on the board: the highlighted one's lit.
        for (i in 0 until 4) {
            val on = i == focus
            Box(
                Modifier.offset((18 + i * 84).dp, 170.dp).width(72.dp).arrive(7 + i),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.clip(RoundedCornerShape(50)).background((if (on) Palette.Mint else Palette.Muted).copy(alpha = 0.2f)).padding(horizontal = 6.dp, vertical = 1.dp)) {
                    FitText(Tx.p(R.plurals.week_chip_days_ago, i + 2, i + 2), MaterialTheme.typography.labelSmall, color = if (on) Palette.Mint else Palette.Muted)
                }
            }
        }
        Pip(
            36.dp, Modifier.offset(316.dp, 194.dp).arrive(11).graphicsLayer { translationY = -seg(t.value, 0.52f, 0.58f) * (1f - seg(t.value, 0.58f, 0.7f)) * 10.dp.toPx() },
            mood = if (rolled) 1 else 0, bounce = false,
        )
    }
}
