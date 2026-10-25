package io.github.zahakj.tandem.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.zahakj.tandem.Screensaver
import io.github.zahakj.tandem.audio.Family
import io.github.zahakj.tandem.audio.SoundPack
import io.github.zahakj.tandem.audio.Tracks
import io.github.zahakj.tandem.data.Store
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/** The "Sound & music" section of Appearance. */
@Composable
fun soundSettings(trackCount: Int): SettingSection {
    val look = Store.settings.look
    val pack = SoundPack.of(look.soundPack)
    val packTracks = Tracks.names(look.musicPack)
    val themeTrack = packTracks.first { it.first == Tracks.forTheme(look.theme, look.musicPack) }.second
    val nowCard: @Composable () -> Unit = { NowPlayingCard(Modifier.fillMaxWidth()) }
    return SettingSection(
        "Sound & music",
        listOf(
            toggle("UI sounds", Glyph.Bell, Palette.Butter, "Soft clicks and chimes as you move around. Made by Tandem itself, no sound files.", look.sounds,
                preview = { SoundPackPreview(pack, play = false) }) { on -> updateLook { it.copy(sounds = on) }; if (on) Sounds.select() },
            choice(
                "Sound pack", Glyph.Sparkle, Palette.Butter, pack.blurb,
                SoundPack.entries.map { it.id to it.label }, pack.id,
                preview = { SoundPackPreview(pack, play = true) },
            ) { v -> updateLook { it.copy(soundPack = v) }; Sounds.ensurePack(SoundPack.of(v)) },
            number(
                "Sound volume", Glyph.Volume, Palette.Butter, "How loud the UI sounds are, compared with the system volume.",
                look.soundVolume, 0.1f, 1f, 0.1f, ::pct,
            ) { v -> updateLook { it.copy(soundVolume = v ?: 0.5f) }; Sounds.select() },
            toggle(
                "Background music", Glyph.Music, Palette.Peach,
                "Tandem's own soundtrack while you browse. It fades out and stops completely while a game runs, and stays quiet when another app is playing.",
                look.music, preview = nowCard,
            ) { on -> updateLook { it.copy(music = on) } },
            choice(
                "Soundtrack pack", Glyph.Music, Palette.Peach,
                "Meadow Tales: storybook pieces for piano, strings, woodwinds, accordion and harp. " +
                    "Pastel Arcade: the original lo-fi, synth and chiptune tracks.",
                Tracks.packs, look.musicPack,
                preview = nowCard,
            ) { v ->
                // A piece picked from the other pack makes way for the new pack's match.
                updateLook { it.copy(musicPack = v, music = true, musicTrack = if (Tracks.names(v).any { t -> t.first == it.musicTrack }) it.musicTrack else if (it.musicTrack == "shuffle") "shuffle" else "theme") }
            },
            choice(
                "Soundtrack", Glyph.Music, Palette.Peach,
                "Match theme picks the piece that suits your theme. Shuffle plays a different one each time a song comes round.",
                listOf("theme" to "Match theme · $themeTrack") + packTracks + ("shuffle" to "Shuffle"), look.musicTrack,
                preview = nowCard,
            ) { v -> updateLook { it.copy(musicTrack = v, music = true) } },
            number(
                "Music volume", Glyph.Volume, Palette.Peach, "How loud the background music plays.",
                look.musicVolume, 0.1f, 1f, 0.1f, ::pct, preview = nowCard,
            ) { v -> updateLook { it.copy(musicVolume = v ?: 0.4f) } },
            toggle(
                "My music folder", Glyph.Folder, Palette.Peach,
                "Play your own songs from ROMs/tandem/music instead (MP3, OGG, Opus, FLAC, M4A, WAV). " +
                    if (trackCount == 0) "The folder is empty right now." else "$trackCount songs found.",
                look.myMusic, preview = nowCard,
            ) { on -> updateLook { it.copy(myMusic = on) }; if (on && trackCount == 0) Toasts.show("Add songs to ROMs/tandem/music first") },
            toggle("Shuffle my music", Glyph.Refresh, Palette.Peach, "Play the music folder in a random order.", look.musicShuffle) { on ->
                updateLook { it.copy(musicShuffle = on) }
            },
            SettingRow(
                "Next song", Music.now?.title ?: "Nothing yet", Palette.Peach, Glyph.Music,
                "Skip to another song: the next file in your folder, or another piece of the soundtrack.", preview = nowCard,
            ) {
                if (!look.music) Toasts.show("Turn on background music first") else Music.skip()
            },
            choice(
                "System jingles", Glyph.Gamepad, Palette.Lavender,
                "A tiny original jingle per family of systems instead of the pack's launch sound: square waves for handhelds, FM for Sega, shimmer for PlayStation and more.",
                listOf("off" to "Off", "launch" to "When a game starts", "all" to "Systems and games"), look.jingles,
                preview = { JinglePreview(look.jingles) },
            ) { v -> updateLook { it.copy(jingles = v) }; if (v != "off") Sounds.prepareJingles() },
            toggle(
                "Play jingles", Glyph.Music, Palette.Lavender,
                "A game's own jingle plays when you rest on it for a moment: a file in downloaded_media/<system>/jingles, or one picked in the game's Edit details. It follows the sound volume and UI sounds.",
                look.gameJingles,
            ) { on -> updateLook { it.copy(gameJingles = on) }; if (!on) GameJingles.fadeOut() },
            toggle("Pip's voice", Glyph.Heart, Palette.Lavender, "Pip babbles a little when it has something to tell you.", look.pipVoice,
                preview = { PipVoicePreview(look.pipVoice) }) { on -> updateLook { it.copy(pipVoice = on) } },
        ),
    )
}

/**
 * Keeps sound in step with the app: music follows the settings and theme, popups whoosh, toasts pop with Pip's
 * voice, and the screensaver gets its own sounds. Composed once in the app root.
 */
@Composable
fun SoundCues() {
    val look = Store.settings.look
    LaunchedEffect(look.music, look.musicVolume, look.musicTrack, look.musicPack, look.myMusic, look.theme) { Music.refresh() }
    LaunchedEffect(look.soundPack) { Sounds.ensurePack(SoundPack.of(look.soundPack)) }

    // A launch counts as a popup for focus, but it has its own sound.
    val open = Nav.overlayOpen && !LaunchFx.busy
    var wasOpen by remember { mutableStateOf(open) }
    LaunchedEffect(open) {
        if (open != wasOpen) { if (open) Sounds.menuOpen() else Sounds.menuClose(); wasOpen = open }
    }
    // Every popup layer too (a confirm over a Y menu, a folder, the Start panel, a tile's menu), so each one opening
    // or closing whooshes once. Sounds skips a second whoosh in the same moment, and the close after B's own sound.
    val layers = Layers.count
    var wasLayers by remember { mutableStateOf(layers) }
    LaunchedEffect(layers) {
        if (layers > wasLayers) Sounds.menuOpen() else if (layers < wasLayers) Sounds.menuClose()
        wasLayers = layers
    }

    val toast = Toasts.id
    val firstToast = remember { toast }
    LaunchedEffect(toast) { if (toast != firstToast) Toasts.message?.let(Sounds::toast) }

    val saver = Screensaver.active
    var wasSaver by remember { mutableStateOf(saver) }
    LaunchedEffect(saver) {
        if (saver != wasSaver) { if (saver) Sounds.saverIn() else Sounds.saverOut(); wasSaver = saver }
        Music.dim(saver)
    }
    GameJingleWatcher()

    val problem = LaunchFx.problem
    LaunchedEffect(problem) { if (problem != null) { Sounds.error(); Sounds.pipSays("Oh no, that one won't start"); Music.gameClosed() } }

    // A game launched on the other screen has closed: the music may come back.
    val anyRunning = io.github.zahakj.tandem.RunningGames.list.isNotEmpty()
    LaunchedEffect(anyRunning) { if (!anyRunning) Music.gameClosed() }
}

// --- Previews --------------------------------------------------------------------------------------------------

/** The four packs as chips, each with a little drawing of its character; plays a demo when [play]. */
@Composable
private fun SoundPackPreview(pack: SoundPack, play: Boolean) {
    if (play) LaunchedEffect(pack) { delay(260); Sounds.preview(pack) }
    val t by rememberInfiniteTransition(label = "pack").animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "t")
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (p in SoundPack.entries) {
            val on = p == pack
            val color = when (p) { SoundPack.Modern -> Palette.Sky; SoundPack.Playful -> Palette.Lavender; SoundPack.Chiptune -> Palette.Mint; SoundPack.Bubbly -> Palette.Peach; SoundPack.Minimal -> Palette.Muted }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (on) color.copy(alpha = 0.22f) else Palette.Card.copy(alpha = 0.5f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(p.label, Modifier.width(84.dp), style = MaterialTheme.typography.titleSmall, color = if (on) color else Palette.Muted)
                Canvas(Modifier.weight(1f).height(22.dp)) {
                    val phase = if (on && !Themes.still) t else 0f
                    drawPackWave(p, color.copy(alpha = if (on) 1f else 0.45f), phase)
                }
            }
        }
    }
}

private fun DrawScope.drawPackWave(p: SoundPack, color: Color, phase: Float) {
    val w = size.width
    val h = size.height
    val mid = h / 2
    val path = Path()
    val n = 64
    for (i in 0..n) {
        val x = w * i / n
        val u = i.toFloat() / n
        val env = (1 - u).pow(1.5f)
        val y = when (p) {
            // A short, quickly settling ripple: a tap with a soft tone behind it.
            SoundPack.Modern -> sin((u * 10 - phase * 2) * 2 * PI).toFloat() * (1 - u).pow(5f)
            SoundPack.Playful -> sin((u * 6 - phase * 2) * 2 * PI).toFloat() * env
            SoundPack.Chiptune -> (if (((u * 8 - phase * 2) % 1 + 1) % 1 < 0.5f) 1f else -1f) * (kotlin.math.floor(env * 4) / 4)
            SoundPack.Bubbly -> sin((u * u * 9 - phase * 2) * 2 * PI).toFloat() * env
            SoundPack.Minimal -> if (u < 0.08f) sin(u * 80).toFloat() * (1 - u / 0.08f) else 0f
        }
        val py = mid - y * h * 0.42f
        if (i == 0) path.moveTo(x, py) else path.lineTo(x, py)
    }
    drawPath(path, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
}

@Composable
private fun JinglePreview(mode: String) {
    var i by remember { mutableIntStateOf(0) }
    val fam = Family.entries[i % Family.entries.size]
    LaunchedEffect(mode) {
        if (mode == "off") return@LaunchedEffect
        Sounds.prepareJingles()
        delay(400)
        // Play a few families, one after another.
        repeat(3) { k ->
            i = (System.currentTimeMillis() / 1000).toInt() + k
            Sounds.previewJingle(Family.entries[i % Family.entries.size])
            delay(2600)
        }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Pip(72.dp, mood = if (mode == "off") 2 else 1)
        Spacer(Modifier.height(8.dp))
        Text(if (mode == "off") "Jingles are off" else fam.label, style = MaterialTheme.typography.titleSmall, color = Palette.Lavender)
    }
}

@Composable
private fun PipVoicePreview(on: Boolean) {
    LaunchedEffect(on) { if (on) { delay(300); Sounds.pipSays("Hi! I'm Pip, nice to meet you!") } }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Pip(84.dp, mood = if (on) 1 else 0)
        Spacer(Modifier.height(8.dp))
        Box(Modifier.clip(RoundedCornerShape(50)).background(Palette.CardHigh).padding(horizontal = 12.dp, vertical = 6.dp)) {
            Text(if (on) "Hi! I'm Pip!" else "…", style = MaterialTheme.typography.titleSmall)
        }
    }
}

// --- Now playing ---------------------------------------------------------------------------------------------------

/** A mini now-playing card: mood art, the track's name and mood, and visualizer bars. */
@Composable
fun NowPlayingCard(modifier: Modifier = Modifier) {
    val look = Store.settings.look
    val np = Music.now
    val ownFiles = look.myMusic && remember(look.myMusic) { Music.tracks().isNotEmpty() }
    val id = when {
        ownFiles -> "file"
        look.music && np != null && !np.own && Music.playing -> np.id
        else -> Music.trackId()
    }
    val song = Tracks.byId(id)
    val title = if (ownFiles) np?.takeIf { it.own }?.title ?: "Your music" else song?.title ?: "Hillside Breeze"
    val mood = if (ownFiles) "From ROMs/tandem/music" else song?.mood.orEmpty()
    Column(modifier) {
        Box(Modifier.fillMaxWidth().aspectRatio(1.7f).clip(RoundedCornerShape(18.dp))) {
            MoodArt(id, Modifier.fillMaxSize())
            Visualizer(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(44.dp).padding(horizontal = 14.dp, vertical = 8.dp))
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(mood, style = MaterialTheme.typography.bodySmall, color = Palette.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(8.dp))
            Pill(if (!look.music) "Off" else if (Music.playing) "Playing" else "Paused", if (look.music) Palette.Peach else Palette.Muted)
        }
    }
}

/** Bars that dance with the soundtrack's instruments. Animates only while it is on screen. */
@Composable
private fun Visualizer(modifier: Modifier) {
    val n = 14
    val bars = remember { FloatArray(n) }
    var frame by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            withFrameMillis { ms ->
                val lv = Music.levels()
                val t = ms / 1000.0
                for (i in 0 until n) {
                    val target = when {
                        lv != null && lv.isNotEmpty() -> {
                            val v = lv[(i * lv.size) / n].coerceIn(0f, 1f).pow(0.6f)
                            v * (0.65f + 0.35f * sin(t * 7.0 + i * 1.7).toFloat().let { (it + 1) / 2 })
                        }
                        Music.playing -> 0.25f + 0.2f * sin(t * 5.0 + i).toFloat()
                        else -> 0.06f
                    }
                    bars[i] += (target - bars[i]) * if (target > bars[i]) 0.5f else 0.15f
                }
                frame++
            }
        }
    }
    val colors = listOf(Palette.Mint, Palette.Sky, Palette.Lavender, Palette.Peach, Palette.Butter)
    Canvas(modifier) {
        frame // read so the canvas redraws each frame
        val gap = size.width / n
        val bw = gap * 0.55f
        for (i in 0 until n) {
            val h = (bars[i].coerceIn(0.04f, 1f)) * size.height
            drawRoundRect(
                colors[i % colors.size].copy(alpha = 0.9f),
                Offset(i * gap + (gap - bw) / 2, size.height - h), Size(bw, h), CornerRadius(bw / 2),
            )
        }
    }
}

/** A little animated picture for each track's mood, drawn in code. */
@Composable
fun MoodArt(id: String, modifier: Modifier = Modifier) {
    val still = Themes.still
    val t by rememberInfiniteTransition(label = "mood").animateFloat(0f, 1f, infiniteRepeatable(tween(12000, easing = LinearEasing), RepeatMode.Restart), label = "t")
    if (id == "pips-jam") {
        Box(modifier, contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) { drawPipsJam(if (still) 0f else t) }
            Pip(64.dp, Modifier.padding(bottom = 18.dp), mood = 1)
        }
        return
    }
    Canvas(modifier) {
        val time = if (still) 0f else t
        when (id) {
            "starlight-arcade" -> drawArcade(time)
            "sakura-breeze" -> drawSakura(time)
            "rainy-library" -> drawRain(time)
            "synthwave-drive" -> drawSynthwave(time)
            "file" -> drawVinyl(time)
            "hillside-breeze" -> drawHills(time, dusk = false)
            "flying-home" -> drawHills(time, dusk = true)
            "seaside-train" -> drawSeaside(time)
            "lantern-town" -> drawLanterns(time)
            "forest-spirits" -> drawSpirits(time)
            "summer-rain" -> drawRain(time)
            else -> drawPastelNight(time)
        }
    }
}

/** Rolling hills under drifting clouds; at dusk the sky turns peach and a little bird glides home. */
private fun DrawScope.drawHills(t: Float, dusk: Boolean) {
    val w = size.width; val h = size.height
    drawRect(Brush.verticalGradient(if (dusk) listOf(c(0x6C6BB5), c(0xF7A98B), c(0xFFD9A0)) else listOf(c(0x7EC8F2), c(0xCDEBFA))))
    if (dusk) drawCircle(c(0xFFF1C1).copy(alpha = 0.9f), h * 0.12f, Offset(w * 0.72f, h * 0.55f))
    for (i in 0 until 4) {
        val x = ((hash(i, 11) + t * (0.5f + 0.3f * hash(i, 12))) % 1.3f) * w * 1.1f - w * 0.25f
        val y = h * (0.15f + 0.25f * hash(i, 13))
        val r = h * (0.07f + 0.04f * hash(i, 14))
        val cloud = Color.White.copy(alpha = if (dusk) 0.55f else 0.85f)
        drawCircle(cloud, r, Offset(x, y)); drawCircle(cloud, r * 1.3f, Offset(x + r * 1.2f, y - r * 0.3f)); drawCircle(cloud, r, Offset(x + r * 2.4f, y))
    }
    drawCircle(if (dusk) c(0x6E8C5A) else c(0x8FD18A), h * 0.9f, Offset(w * 0.2f, h * 1.55f))
    drawCircle(if (dusk) c(0x58744A) else c(0x6DBB6B), h * 1.0f, Offset(w * 0.85f, h * 1.7f))
    // Grass blades swaying in the wind.
    for (i in 0 until 22) {
        val x = hash(i, 21) * w
        val base = h * (0.8f + 0.15f * hash(i, 22))
        val sway = sin((t * 6 + hash(i, 23)) * 2 * PI).toFloat() * h * 0.02f
        drawLine(if (dusk) c(0x3F5A35) else c(0x4E9A4C), Offset(x, base), Offset(x + sway, base - h * 0.06f), strokeWidth = 2f, cap = StrokeCap.Round)
    }
    if (dusk) {
        val bx = w * (0.15f + 0.7f * t); val by = h * (0.3f + 0.04f * sin(t * 8 * PI).toFloat())
        val flap = sin(t * 40 * PI).toFloat() * h * 0.02f
        drawLine(c(0x2E2440), Offset(bx - h * 0.04f, by - flap), Offset(bx, by), strokeWidth = 2.5f, cap = StrokeCap.Round)
        drawLine(c(0x2E2440), Offset(bx, by), Offset(bx + h * 0.04f, by - flap), strokeWidth = 2.5f, cap = StrokeCap.Round)
    }
}

/** A little train running along the shore, with the sea sparkling behind it. */
private fun DrawScope.drawSeaside(t: Float) {
    val w = size.width; val h = size.height
    drawRect(Brush.verticalGradient(listOf(c(0x8FD3F4), c(0xE6F6FF))), size = Size(w, h * 0.55f))
    drawRect(Brush.verticalGradient(listOf(c(0x3C8DBC), c(0x2A6F9E))), Offset(0f, h * 0.55f), Size(w, h * 0.3f))
    for (i in 0 until 16) {
        val tw = (sin((t * 8 + hash(i, 31)) * 2 * PI).toFloat() + 1) / 2
        drawLine(Color.White.copy(alpha = 0.6f * tw), Offset(hash(i, 32) * w, h * (0.58f + 0.22f * hash(i, 33))), Offset(hash(i, 32) * w + h * 0.05f, h * (0.58f + 0.22f * hash(i, 33))), strokeWidth = 1.5f)
    }
    drawRect(c(0xE9D8A6), Offset(0f, h * 0.85f), Size(w, h * 0.15f))
    drawLine(c(0x7A5C3E), Offset(0f, h * 0.84f), Offset(w, h * 0.84f), strokeWidth = 3f)
    val x0 = ((t * 1.5f) % 1.4f) * w * 1.2f - w * 0.4f
    val carW = h * 0.22f; val carH = h * 0.12f
    for (k in 0 until 3) {
        val x = x0 - k * (carW + h * 0.03f)
        drawRoundRect(if (k == 0) c(0xE4572E) else c(0xF3A712), Offset(x, h * 0.84f - carH - h * 0.03f), Size(carW, carH), CornerRadius(h * 0.03f))
        drawRect(c(0xFFF4D6), Offset(x + carW * 0.15f, h * 0.84f - carH), Size(carW * 0.3f, carH * 0.35f))
        drawRect(c(0xFFF4D6), Offset(x + carW * 0.55f, h * 0.84f - carH), Size(carW * 0.3f, carH * 0.35f))
        drawCircle(c(0x333333), h * 0.022f, Offset(x + carW * 0.25f, h * 0.84f - h * 0.02f))
        drawCircle(c(0x333333), h * 0.022f, Offset(x + carW * 0.75f, h * 0.84f - h * 0.02f))
    }
}

/** Strings of warm lanterns swinging over a little town at night. */
private fun DrawScope.drawLanterns(t: Float) {
    val w = size.width; val h = size.height
    drawRect(Brush.verticalGradient(listOf(c(0x1D1B3A), c(0x3B2A4A))))
    for (i in 0 until 7) {
        val bw = w * (0.1f + 0.08f * hash(i, 41)); val bh = h * (0.25f + 0.25f * hash(i, 42))
        val x = i * w / 6.5f - w * 0.05f
        drawRect(c(0x16142B), Offset(x, h - bh), Size(bw, bh))
        drawRect(c(0xFFD27A).copy(alpha = 0.8f), Offset(x + bw * 0.35f, h - bh * 0.7f), Size(bw * 0.25f, bh * 0.15f))
    }
    for (row in 0 until 2) {
        val y0 = h * (0.22f + 0.18f * row)
        for (i in 0 until 7) {
            val x = w * (i + 0.5f) / 7f
            val sag = sin((i + 0.5f) / 7f * PI).toFloat() * h * 0.08f
            val swing = sin((t * 5 + i * 0.3f + row) * 2 * PI).toFloat() * h * 0.01f
            val p = Offset(x + swing, y0 + sag + h * 0.04f)
            val col = listOf(c(0xFF8A5B), c(0xFFC75F), c(0xF9F871))[(i + row) % 3]
            drawCircle(Brush.radialGradient(listOf(col.copy(alpha = 0.45f), Color.Transparent), p, h * 0.1f), h * 0.1f, p)
            drawCircle(col, h * 0.025f, p)
        }
    }
}

/** Little glowing forest spirits drifting between dark trees. */
private fun DrawScope.drawSpirits(t: Float) {
    val w = size.width; val h = size.height
    drawRect(Brush.verticalGradient(listOf(c(0x16302B), c(0x0B1A17))))
    for (i in 0 until 6) {
        val x = w * (i + 0.3f * hash(i, 51)) / 5.5f
        drawRect(c(0x0A1512), Offset(x, 0f), Size(w * (0.04f + 0.03f * hash(i, 52)), h))
    }
    for (i in 0 until 14) {
        val ph = (t * (0.6f + hash(i, 53)) + hash(i, 54)) * 2 * PI
        val x = hash(i, 55) * w + cos(ph).toFloat() * w * 0.04f
        val y = h * (0.25f + 0.6f * hash(i, 56)) + sin(ph * 1.3).toFloat() * h * 0.05f
        val glow = 0.5f + 0.5f * sin(ph * 2).toFloat()
        drawCircle(Brush.radialGradient(listOf(c(0xD8FFB0).copy(alpha = 0.5f * glow), Color.Transparent), Offset(x, y), h * 0.07f), h * 0.07f, Offset(x, y))
        drawCircle(c(0xF1FFD6).copy(alpha = 0.6f + 0.4f * glow), h * 0.012f, Offset(x, y))
    }
}

private fun c(hex: Long) = Color(0xFF000000 or hex)

/** Repeatable pseudo-random numbers for star fields and petals. */
private fun hash(i: Int, salt: Int = 0): Float {
    var x = i * 374761393 + salt * 668265263
    x = (x xor (x ushr 13)) * 1274126177
    return ((x xor (x ushr 16)) and 0xFFFF) / 65535f
}

private fun DrawScope.drawPastelNight(t: Float) {
    drawRect(Brush.verticalGradient(listOf(c(0x2C2950), c(0x13121C))))
    val w = size.width; val h = size.height
    val blobs = listOf(c(0xB8A8FF), c(0xFFB4A2), c(0x8EECC8))
    for ((i, col) in blobs.withIndex()) {
        val a = (t + i / 3f) * 2 * PI
        drawCircle(col.copy(alpha = 0.18f), h * 0.45f, Offset(w * (0.25f + 0.25f * i) + cos(a).toFloat() * w * 0.05f, h * (0.75f + 0.05f * sin(a).toFloat())))
    }
    for (i in 0 until 26) {
        val tw = 0.35f + 0.65f * ((sin((t * 6 + hash(i, 3)) * 2 * PI).toFloat() + 1) / 2)
        drawCircle(Color.White.copy(alpha = tw * 0.8f), 1.2f + hash(i, 2) * 1.6f, Offset(hash(i) * w, hash(i, 1) * h * 0.7f))
    }
    val moon = Offset(w * 0.78f, h * 0.28f)
    drawCircle(c(0xFFE49A), h * 0.13f, moon)
    drawCircle(c(0x262347), h * 0.12f, moon + Offset(h * 0.06f, -h * 0.03f))
}

private fun DrawScope.drawArcade(t: Float) {
    drawRect(c(0x0C0517))
    val w = size.width; val h = size.height
    val px = h / 28
    for (i in 0 until 30) {
        val on = ((t * 24 + hash(i, 5) * 8).toInt() % 3) != 0
        if (!on) continue
        val col = listOf(c(0x5CF2D6), c(0xFF5DA2), c(0xFFD166), c(0x4CC9F0))[i % 4]
        drawRect(col, Offset((hash(i) * w / px).toInt() * px, (hash(i, 1) * h * 0.8f / px).toInt() * px), Size(px, px))
    }
    // A bouncing pixel coin.
    val bounce = kotlin.math.abs(sin(t * 12 * PI)).toFloat()
    val cx = w / 2; val cy = h * 0.62f - bounce * h * 0.2f
    val coin = listOf("..XXXX..", ".XYYYYX.", "XYYXYYYX", "XYYXYYYX", "XYYXYYYX", "XYYXYYYX", ".XYYYYX.", "..XXXX..")
    for ((ry, line) in coin.withIndex()) for ((rx, ch) in line.withIndex()) {
        val col = when (ch) { 'X' -> c(0xC77D1F); 'Y' -> c(0xFFD166); else -> continue }
        drawRect(col, Offset(cx + (rx - 4) * px, cy + (ry - 4) * px), Size(px, px))
    }
    for (y in 0 until (h / 3).toInt()) drawRect(Color.Black.copy(alpha = 0.12f), Offset(0f, y * 3f), Size(w, 1f))
}

private fun DrawScope.drawSakura(t: Float) {
    drawRect(Brush.verticalGradient(listOf(c(0x4A2A3D), c(0x1F1219))))
    val w = size.width; val h = size.height
    drawCircle(c(0xFFB7C5).copy(alpha = 0.35f), h * 0.32f, Offset(w * 0.7f, h * 0.38f))
    drawLine(c(0x120A0E), Offset(0f, h * 0.2f), Offset(w * 0.45f, h * 0.32f), strokeWidth = h * 0.04f, cap = StrokeCap.Round)
    drawLine(c(0x120A0E), Offset(w * 0.25f, h * 0.26f), Offset(w * 0.38f, h * 0.12f), strokeWidth = h * 0.025f, cap = StrokeCap.Round)
    for (i in 0 until 18) {
        val speed = 0.6f + hash(i, 7) * 0.8f
        val y = ((hash(i, 1) + t * speed * 2) % 1f) * (h * 1.1f) - h * 0.05f
        val x = hash(i) * w + sin((t * 4 + hash(i, 2)) * 2 * PI).toFloat() * w * 0.04f
        val ang = (t * 360 * speed + hash(i, 3) * 360)
        rotate(ang, Offset(x, y)) {
            drawOval(c(0xFFC6D9).copy(alpha = 0.85f), Offset(x - 5f, y - 3f), Size(10f, 6f))
        }
    }
}

private fun DrawScope.drawRain(t: Float) {
    drawRect(Brush.verticalGradient(listOf(c(0x1B2A3F), c(0x0D1522))))
    val w = size.width; val h = size.height
    // Warm lamp glow.
    drawCircle(Brush.radialGradient(listOf(c(0xFFC857).copy(alpha = 0.45f), Color.Transparent), Offset(w * 0.15f, h * 0.8f), h * 0.6f), h * 0.6f, Offset(w * 0.15f, h * 0.8f))
    // Rain on the glass.
    for (i in 0 until 40) {
        val x = hash(i) * w
        val y = ((hash(i, 1) + t * (6 + hash(i, 2) * 4)) % 1f) * h
        drawLine(c(0xA9C7FF).copy(alpha = 0.35f), Offset(x, y), Offset(x - 2f, y + h * 0.07f), strokeWidth = 1.5f)
    }
    // Window frame and books.
    drawRoundRect(c(0x2A1E14), Offset(w * 0.08f, h * 0.08f), Size(w * 0.84f, h * 0.6f), CornerRadius(8f), style = Stroke(width = h * 0.035f))
    drawLine(c(0x2A1E14), Offset(w / 2, h * 0.08f), Offset(w / 2, h * 0.68f), strokeWidth = h * 0.025f)
    var x = w * 0.45f
    val books = listOf(c(0xE0A6D8), c(0x8CC8E8), c(0xFF9466), c(0xA8E6B8), c(0xFFC857), c(0xB8A8FF))
    var k = 0
    while (x < w * 0.95f) {
        val bw = w * (0.03f + hash(k, 9) * 0.025f)
        val bh = h * (0.16f + hash(k, 4) * 0.08f)
        drawRect(books[k % books.size].copy(alpha = 0.8f), Offset(x, h - bh), Size(bw, bh))
        x += bw + 2f; k++
    }
}

private fun DrawScope.drawSynthwave(t: Float) {
    val w = size.width; val h = size.height
    val horizon = h * 0.58f
    drawRect(Brush.verticalGradient(listOf(c(0x1A0F2E), c(0x7A2A6E), c(0xFF5DA2)), endY = horizon), size = Size(w, horizon))
    // Striped sun.
    val sunC = Offset(w / 2, horizon)
    val r = h * 0.34f
    drawCircle(Brush.verticalGradient(listOf(c(0xFFD166), c(0xFF5DA2)), startY = horizon - r, endY = horizon), r, sunC)
    for (k in 0 until 5) {
        val y = horizon - r * (0.12f + k * 0.12f)
        drawRect(c(0xB8467E), Offset(0f, y), Size(w, r * 0.03f + k * 1.2f))
    }
    drawRect(c(0x14061F), Offset(0f, horizon), Size(w, h - horizon))
    val grid = c(0xFF5DA2).copy(alpha = 0.7f)
    for (k in 0 until 8) {
        val p = ((k + t * 8) % 8f) / 8f
        val y = horizon + (h - horizon) * p * p
        drawLine(grid, Offset(0f, y), Offset(w, y), strokeWidth = 1.5f)
    }
    for (k in -6..6) drawLine(grid, Offset(w / 2 + k * w * 0.02f, horizon), Offset(w / 2 + k * w * 0.16f, h), strokeWidth = 1.5f)
}

private fun DrawScope.drawPipsJam(t: Float) {
    drawRect(Brush.verticalGradient(listOf(c(0x8EECC8).copy(alpha = 0.55f), c(0x8FD3FF).copy(alpha = 0.45f))))
    val w = size.width; val h = size.height
    val notes = listOf(c(0xFF6F7F), c(0x9B7BFF), c(0xE0A400), c(0x2BB894))
    for (i in 0 until 8) {
        val p = (hash(i, 1) + t * 3) % 1f
        val x = hash(i) * w + sin((p * 2 + hash(i, 2)) * 2 * PI).toFloat() * w * 0.03f
        val y = h * (1.05f - p * 1.1f)
        val col = notes[i % notes.size].copy(alpha = (1 - p).coerceIn(0f, 1f))
        val r = h * 0.035f
        drawCircle(col, r, Offset(x, y))
        drawLine(col, Offset(x + r * 0.9f, y), Offset(x + r * 0.9f, y - r * 3), strokeWidth = r * 0.45f)
        drawLine(col, Offset(x + r * 0.9f, y - r * 3), Offset(x + r * 2.2f, y - r * 2.3f), strokeWidth = r * 0.45f)
    }
}

private fun DrawScope.drawVinyl(t: Float) {
    drawRect(Brush.verticalGradient(listOf(c(0x29234A), c(0x13121C))))
    val ctr = Offset(size.width / 2, size.height / 2)
    val r = size.height * 0.42f
    drawCircle(c(0x0B0A12), r, ctr)
    for (k in 1..7) drawCircle(Color.White.copy(alpha = 0.06f), r * (0.45f + k * 0.075f), ctr, style = Stroke(1f))
    rotate(t * 360f * 6, ctr) {
        drawArc(Color.White.copy(alpha = 0.12f), -20f, 40f, false, Offset(ctr.x - r * 0.9f, ctr.y - r * 0.9f), Size(r * 1.8f, r * 1.8f), style = Stroke(r * 0.25f))
    }
    drawCircle(c(0xFFB4A2), r * 0.3f, ctr)
    drawCircle(c(0x0B0A12), r * 0.04f, ctr)
}
