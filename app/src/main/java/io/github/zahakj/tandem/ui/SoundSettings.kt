package io.github.zahakj.tandem.ui

import androidx.compose.ui.platform.LocalContext
import androidx.compose.animation.core.LinearEasing
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
import io.github.zahakj.tandem.R
import io.github.zahakj.tandem.Screensaver
import io.github.zahakj.tandem.Tx
import io.github.zahakj.tandem.audio.Plaza
import io.github.zahakj.tandem.audio.SoundPack
import io.github.zahakj.tandem.audio.Tracks
import io.github.zahakj.tandem.data.Store
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * The theme pack's own music and sounds, when the theme is a pack that has some: one switch each (the same parts as in
 * Theme packs, [PackParts]). Null when the theme brings no audio, so the group doesn't show.
 */
@Composable
fun themePackAudioSettings(): SettingSection? {
    val look = Store.settings.look
    val pack = ThemePacks.byId(look.theme) ?: return null
    if (pack.music.isEmpty() && pack.sounds == null) return null
    val parts = ThemePacks.parts(pack.id)
    val rows = buildList {
        if (pack.music.isNotEmpty()) add(
            toggle(
                Tx.s(R.string.sound_pack_music), Glyph.Music, Palette.Peach,
                Tx.p(R.plurals.sound_pack_music_help, pack.music.size, pack.name, pack.music.size),
                parts.music, preview = { NowPlayingCard(Modifier.fillMaxWidth()) },
                en = "Theme pack music",
            ) { on -> setPackPart(pack.id) { it.copy(music = on) }; Music.refresh() },
        )
        if (pack.sounds != null) add(
            toggle(
                Tx.s(R.string.sound_pack_sounds), Glyph.Bell, Palette.Butter,
                Tx.s(R.string.sound_pack_sounds_help, pack.name),
                parts.sounds,
                en = "Theme pack sounds",
            ) { on -> setPackPart(pack.id) { it.copy(sounds = on) }; Sounds.refreshFiles(); Sounds.select() },
        )
    }
    return SettingSection(Tx.s(R.string.sound_group_theme_pack), rows, note = pack.name, en = "Theme pack")
}

/** Changes which parts of the theme pack [id] are used. */
internal fun setPackPart(id: String, change: (io.github.zahakj.tandem.data.PackParts) -> io.github.zahakj.tandem.data.PackParts) = updateLook { l ->
    val now = change(l.packParts[id] ?: io.github.zahakj.tandem.data.PackParts())
    l.copy(packParts = if (now == io.github.zahakj.tandem.data.PackParts()) l.packParts - id else l.packParts + (id to now))
}

/** The "Sound & music" rows. The theme pack's own music and sounds come first when it has them ([themePackAudioSettings]). */
@Composable
fun soundSettings(trackCount: Int): SettingSection {
    val look = Store.settings.look
    val pack = SoundPack.of(look.soundPack)
    val recorded = remember(ThemePacks.packs) { ThemePacks.customSounds() }
    val recordedNow = recorded.firstOrNull { it.first == look.soundFiles }
    val themeHasAudio = ThemePacks.byId(look.theme)?.let { it.music.isNotEmpty() || it.sounds != null } == true
    // A row's help, and while the theme pack has audio of its own, that this is what plays when it's switched off.
    fun withFallback(help: String) = if (themeHasAudio) Tx.s(R.string.sound_fallback, help) else help
    val nowCard: @Composable () -> Unit = { NowPlayingCard(Modifier.fillMaxWidth()) }
    // UI sounds and their pack are one choice: Off, one of Tandem's packs, or Custom (a recorded pack, picked below).
    val soundNow = when { !look.sounds -> "off"; recordedNow != null -> "custom"; else -> pack.id }
    // Music and where it comes from are one choice too: Off, your folder, or one of Tandem's soundtracks.
    val musicNow = when { !look.music -> "off"; look.myMusic -> "folder"; else -> look.soundtrackId }
    return SettingSection(en = "Sound & music",
        title = Tx.s(R.string.sound_group),
        rows = listOf(
            choice(
                Tx.s(R.string.sound_ui), Glyph.Bell, Palette.Butter,
                withFallback(
                    Tx.s(
                        R.string.sound_ui_help,
                        when (soundNow) {
                            "off" -> Tx.s(R.string.sound_ui_off_help)
                            "custom" -> Tx.s(R.string.sound_ui_custom_help)
                            else -> pack.blurb
                        },
                    ),
                ),
                listOf("off" to Tx.s(R.string.common_off)) + SoundPack.entries.map { it.id to it.label } +
                    (if (recorded.isNotEmpty()) listOf("custom" to Tx.s(R.string.sound_ui_custom)) else emptyList()),
                soundNow,
                preview = { SoundPackPreview(pack.takeIf { soundNow != "off" && soundNow != "custom" }, play = soundNow != "off" && recordedNow == null) },
                look = { v -> PackCard(v) },
                en = "UI sounds",
            ) { v ->
                when (v) {
                    "off" -> updateLook { it.copy(sounds = false) }
                    "custom" -> updateLook { it.copy(sounds = true, soundFiles = recordedNow?.first ?: recorded.first().first) }
                    else -> { updateLook { it.copy(sounds = true, soundPack = v, soundFiles = "none") }; Sounds.ensurePack(SoundPack.of(v)) }
                }
                Sounds.refreshFiles()
                if (v != "off") Sounds.select()
            }.copy(keywords = "sound pack recorded cocoon sfx clicks beeps effects mute quiet"),
        ) + (if (recordedNow == null || !look.sounds) emptyList() else listOf(
            choice(
                Tx.s(R.string.sound_custom), Glyph.Sparkle, Palette.Butter,
                withFallback(Tx.s(R.string.sound_custom_help, recordedNow.second, pack.label)),
                recorded.map { it.first to it.second }, recordedNow.first,
                en = "Custom sounds",
            ) { v -> updateLook { it.copy(soundFiles = v) }; Sounds.refreshFiles(); Sounds.select() },
        )) + listOf(
            number(
                Tx.s(R.string.sound_volume), Glyph.Volume, Palette.Butter, Tx.s(R.string.sound_volume_help),
                look.soundVolume, 0.1f, 1f, 0.1f, ::pct,
                en = "Sound volume",
            ) { v -> updateLook { it.copy(soundVolume = v ?: 0.5f) }; Sounds.select() },
        ) + listOf(
            toggle(Tx.s(R.string.sound_pip_voice), Glyph.Heart, Palette.Lavender, Tx.s(R.string.sound_pip_voice_help), look.pipVoice,
                preview = { PipVoicePreview(look.pipVoice) }, en = "Pip's voice") { on -> updateLook { it.copy(pipVoice = on) } },
            choice(
                Tx.s(R.string.sound_music), Glyph.Music, Palette.Peach,
                withFallback(
                    if (trackCount == 0) Tx.s(R.string.sound_music_help_empty, Tracks.of(look.soundtrackId).title)
                    else Tx.p(R.plurals.sound_music_help_found, trackCount),
                ),
                // The soundtracks' names (Plaza, Lagoon, Terrace) are names, the same in every language; the line on
                // each card says what it is like.
                listOf("off" to Tx.s(R.string.common_off), "folder" to Tx.s(R.string.sound_music_folder)) + Tracks.soundtracks,
                musicNow,
                preview = nowCard,
                look = { v -> MusicChoiceArt(v) },
                details = Plaza.all.associate { it.id to it.mood },
                en = "Music",
            ) { v ->
                when (v) {
                    "off" -> updateLook { it.copy(music = false) }
                    "folder" -> updateLook { it.copy(music = true, myMusic = true) }
                    else -> { updateLook { it.copy(music = true, myMusic = false, soundtrack = v, soundtrackPicked = true) }; Music.backToChosen() }
                }
                if (v == "folder" && trackCount == 0) Toasts.show(Tx.s(R.string.sound_music_folder_empty))
            }.copy(keywords = "music from soundtrack bgm background folder songs built-in plaza lagoon terrace grooves mute quiet"),
            toggle(Tx.s(R.string.sound_shuffle), Glyph.Refresh, Palette.Peach, Tx.s(R.string.sound_shuffle_help), look.musicShuffle, en = "Shuffle my music") { on ->
                updateLook { it.copy(musicShuffle = on) }
            },
            number(
                Tx.s(R.string.sound_music_volume), Glyph.Volume, Palette.Peach, Tx.s(R.string.sound_music_volume_help),
                look.musicVolume, 0.1f, 1f, 0.1f, ::pct, preview = nowCard,
                en = "Music volume",
            ) { v -> updateLook { it.copy(musicVolume = v ?: 0.4f) } },
            SettingRow(
                Tx.s(R.string.sound_next), Music.now?.title ?: Tx.s(R.string.sound_next_none), Palette.Peach, Glyph.Music,
                Tx.s(R.string.sound_next_help, Music.source), preview = nowCard,
                en = "Next song",
            ) {
                if (!look.music) Toasts.show(Tx.s(R.string.sound_music_off)) else Music.skip()
            },
        ),
        note = if (bottomScreenMuted(LocalContext.current)) Tx.s(R.string.sound_bottom_muted) else null,
    )
}

/**
 * AYN handhelds play every app with a window on the bottom screen at that screen's own volume (a system setting,
 * readable by any app); Tandem always has one there, so at zero its sounds and music are silent.
 */
private fun bottomScreenMuted(ctx: android.content.Context): Boolean =
    runCatching { android.provider.Settings.System.getInt(ctx.contentResolver, "secondary_screen_volume_level", -1) == 0 }.getOrDefault(false)

/**
 * Keeps sound in step with the app: music follows the settings and theme, popups whoosh, toasts pop with Pip's
 * voice, and the screensaver gets its own sounds. Composed once in the app root.
 */
@Composable
fun SoundCues() {
    val look = Store.settings.look
    LaunchedEffect(look.music, look.musicVolume, look.myMusic, look.soundtrack, look.theme, look.packParts) { Music.refresh() }
    LaunchedEffect(look.soundPack) { Sounds.ensurePack(SoundPack.of(look.soundPack)) }
    LaunchedEffect(look.soundFiles, look.theme, look.packParts) { Sounds.refreshFiles() }

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

    val problem = LaunchFx.problem
    LaunchedEffect(problem) { if (problem != null) { Sounds.error(); Sounds.pipSays("Oh no, that one won't start"); Music.gameClosed() } } // tx-ok: never shown, it only shapes the babble

    // A game launched on the other screen has closed: the music may come back.
    val anyRunning = io.github.zahakj.tandem.RunningGames.list.isNotEmpty()
    LaunchedEffect(anyRunning) { if (!anyRunning) Music.gameClosed() }
}

// --- Previews --------------------------------------------------------------------------------------------------

private fun packColor(p: SoundPack): Color = when (p) {
    SoundPack.Modern -> Palette.Sky; SoundPack.Playful -> Palette.Lavender; SoundPack.Chiptune -> Palette.Mint; SoundPack.Bubbly -> Palette.Peach
    SoundPack.Minimal -> Palette.Muted; SoundPack.Studio -> Palette.Butter; SoundPack.Glass -> Palette.Sky; SoundPack.Air -> Palette.Mint; SoundPack.Thock -> Palette.Peach
}

/** An option of UI sounds in the picker: its pack's wave, a flat line for Off, a recording's bars for Custom. */
@Composable
private fun PackCard(id: String) {
    val pack = SoundPack.entries.firstOrNull { it.id == id }
    Box(Modifier.fillMaxSize().background(Palette.NightDeep).padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxWidth().height(40.dp)) {
            when {
                pack != null -> drawPackWave(pack, packColor(pack), 0f)
                id == "custom" -> for (i in 0 until 16) {
                    val h = size.height * (0.25f + 0.7f * kotlin.math.abs(sin(i * 1.7f)))
                    drawRoundRect(Palette.Butter, Offset(i * size.width / 16f, (size.height - h) / 2), Size(size.width / 24f, h), CornerRadius(2f))
                }
                else -> drawLine(Palette.Muted, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 2.dp.toPx(), StrokeCap.Round)
            }
        }
    }
}

/** The packs as chips, each with a little drawing of its character (none lit while sounds are off); plays a demo when [play]. */
@Composable
private fun SoundPackPreview(pack: SoundPack?, play: Boolean) {
    if (play && pack != null) LaunchedEffect(pack) { delay(260); Sounds.preview(pack) }
    // Only the lit pack moves, and it holds its frame once Tandem has settled.
    val t by rememberIdleLoop(2400, still = pack == null || Themes.still)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (p in SoundPack.entries) {
            val on = p == pack
            val color = packColor(p)
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
            // A low, rounded thump that is gone quickly.
            SoundPack.Studio -> sin((u * 4 - phase * 2) * 2 * PI).toFloat() * (1 - u).pow(4f)
            // A deep thud that lands hard and is gone: two slow swings, steeply damped.
            SoundPack.Thock -> sin((u * 2.5 - phase * 2) * 2 * PI).toFloat() * (1 - u).pow(6f) * 1.1f
            // A fast, bright ring that takes its time to fade.
            SoundPack.Glass -> sin((u * 16 - phase * 2) * 2 * PI).toFloat() * (1 - u).pow(1.2f) * 0.8f
            // A soft swell rising and falling.
            SoundPack.Air -> sin((u * 5 - phase * 2) * 2 * PI).toFloat() * sin(u * PI).toFloat() * 0.7f
        }
        val py = mid - y * h * 0.42f
        if (i == 0) path.moveTo(x, py) else path.lineTo(x, py)
    }
    drawPath(path, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
}

@Composable
private fun PipVoicePreview(on: Boolean) {
    LaunchedEffect(on) { if (on) { delay(300); Sounds.pipSays("Hi! I'm Pip, nice to meet you!") } } // tx-ok: never shown, it only shapes the babble
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Pip(84.dp, mood = if (on) 1 else 0)
        Spacer(Modifier.height(8.dp))
        Box(Modifier.clip(RoundedCornerShape(50)).background(Palette.CardHigh).padding(horizontal = 12.dp, vertical = 6.dp)) {
            Text(if (on) Tx.s(R.string.sound_pip_hello) else "…", style = MaterialTheme.typography.titleSmall)
        }
    }
}

// --- Now playing ---------------------------------------------------------------------------------------------------

/** A mini now-playing card: mood art, the track's name and where it comes from, and visualizer bars. */
@Composable
fun NowPlayingCard(modifier: Modifier = Modifier) {
    val look = Store.settings.look
    val np = Music.now
    val ownFiles = remember(look.myMusic, look.theme, look.packParts) { Music.playsFiles }
    val id = when {
        ownFiles -> "file"
        look.music && np != null && !np.own && Music.playing -> np.id
        else -> Music.trackId()
    }
    val song = Tracks.byId(id)
    val title = if (ownFiles) np?.takeIf { it.own }?.title ?: Tx.s(R.string.sound_now_your_music) else song?.title ?: Tracks.of(look.soundtrackId).title
    val mood = if (ownFiles) Music.source else song?.mood.orEmpty()
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
            Pill(Tx.s(if (!look.music) R.string.common_off else if (Music.playing) R.string.sound_now_playing else R.string.sound_now_paused), if (look.music) Palette.Peach else Palette.Muted)
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
            // Holds its bars once Tandem has settled, and draws nothing while they rest with no music playing.
            Ambient.awaitAwake()
            val moved = withFrameMillis { ms ->
                var moved = false
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
                    val step = (target - bars[i]) * if (target > bars[i]) 0.5f else 0.15f
                    if (kotlin.math.abs(step) > 0.0005f) moved = true
                    bars[i] += step
                }
                if (moved) frame++
                moved
            }
            if (!moved) delay(250)
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

/** A little animated picture for each groove's mood, drawn in code. */
@Composable
fun MoodArt(id: String, modifier: Modifier = Modifier) {
    val still = Themes.still
    // No frames at all while still (the old infinite transition ran on every vsync even then).
    val t by rememberIdleLoop(12000, still)
    Canvas(modifier) {
        val time = if (still) 0f else t
        when (id) {
            "plaza" -> drawPlaza(time)
            "lagoon" -> drawLagoon(time)
            "terrace" -> drawTerrace(time)
            "file" -> drawVinyl(time)
            else -> drawPastelNight(time)
        }
    }
}

/** An option of Music on the picker's cards: Off a quiet line, My music a record, a groove its mood picture. */
@Composable
private fun MusicChoiceArt(id: String) {
    when (id) {
        "off" -> Box(Modifier.fillMaxSize().background(Palette.NightDeep).padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxWidth().height(40.dp)) {
                drawLine(Palette.Muted, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 2.dp.toPx(), StrokeCap.Round)
            }
        }
        "folder" -> MoodArt("file", Modifier.fillMaxSize())
        else -> MoodArt(id, Modifier.fillMaxSize())
    }
}

/** A sunny little square: pastel house fronts with awnings, bunting swaying across, the sun over the roofs. */
private fun DrawScope.drawPlaza(t: Float) {
    val w = size.width; val h = size.height
    drawRect(Brush.verticalGradient(listOf(c(0x8ED1F5), c(0xFDF1D2))))
    drawCircle(Brush.radialGradient(listOf(c(0xFFF3B0), c(0xFFE07A).copy(alpha = 0f)), Offset(w * 0.78f, h * 0.22f), h * 0.3f), h * 0.3f, Offset(w * 0.78f, h * 0.22f))
    drawCircle(c(0xFFE07A), h * 0.1f, Offset(w * 0.78f, h * 0.22f))
    val fronts = listOf(c(0xF7B5A0), c(0xF9DE8F), c(0xA8DCC5), c(0xC9B8F2), c(0xF7B5A0), c(0x9FD0F0))
    val awnings = listOf(c(0xE4572E), c(0x3C8DBC), c(0xF3A712), c(0x4E9A4C), c(0x8E6FD8), c(0xE4572E))
    for (i in fronts.indices) {
        val bw = w / fronts.size
        val bh = h * (0.38f + 0.12f * hash(i, 61))
        val x = i * bw
        drawRect(fronts[i], Offset(x, h - bh), Size(bw + 1f, bh))
        drawRect(c(0xFFF8E7).copy(alpha = 0.85f), Offset(x + bw * 0.3f, h - bh * 0.78f), Size(bw * 0.4f, bh * 0.22f))
        // A striped awning over the door.
        for (s in 0 until 4) {
            drawRect(if (s % 2 == 0) awnings[i] else Color.White, Offset(x + bw * (0.15f + 0.175f * s), h - bh * 0.42f), Size(bw * 0.175f, bh * 0.1f))
        }
    }
    // Bunting from one side of the square to the other, swaying a little.
    val sway = sin(t * 4 * PI).toFloat() * h * 0.015f
    for (i in 0 until 12) {
        val f = (i + 0.5f) / 12f
        val y = h * 0.3f + sin(f * PI).toFloat() * h * 0.1f + sway
        val x = f * w
        val flag = Path().apply { moveTo(x - w * 0.03f, y); lineTo(x + w * 0.03f, y); lineTo(x, y + h * 0.08f); close() }
        drawPath(flag, listOf(c(0xFF8A5B), c(0xFFC75F), c(0x8EECC8), c(0xB8A8FF))[i % 4])
    }
}

/** Dusk over still water: a violet-to-peach sky, the low sun's reflection, ripples glinting as they drift. */
private fun DrawScope.drawLagoon(t: Float) {
    val w = size.width; val h = size.height
    val horizon = h * 0.58f
    drawRect(Brush.verticalGradient(listOf(c(0x5B5A9E), c(0xC98BB0), c(0xFFC9A0)), 0f, horizon), size = Size(w, horizon))
    drawRect(Brush.verticalGradient(listOf(c(0x4B4F8C), c(0x23264D)), horizon, h), Offset(0f, horizon), Size(w, h - horizon))
    val sun = Offset(w * 0.36f, horizon - h * 0.02f)
    drawCircle(c(0xFFE3B0), h * 0.11f, sun)
    // The sun's path on the water, broken into ripples.
    for (k in 0 until 7) {
        val y = horizon + h * (0.04f + 0.055f * k)
        val half = w * (0.1f - 0.008f * k) * (0.8f + 0.2f * sin((t * 6 + k * 0.4f) * 2 * PI).toFloat())
        drawLine(c(0xFFD9A8).copy(alpha = 0.75f - 0.08f * k), Offset(sun.x - half, y), Offset(sun.x + half, y), strokeWidth = 2.5f, cap = StrokeCap.Round)
    }
    for (i in 0 until 14) {
        val tw = (sin((t * 5 + hash(i, 71)) * 2 * PI).toFloat() + 1) / 2
        val x = ((hash(i, 72) + t * 0.2f) % 1f) * w
        val y = horizon + h * (0.06f + 0.34f * hash(i, 73))
        drawLine(Color.White.copy(alpha = 0.35f * tw), Offset(x, y), Offset(x + h * 0.06f, y), strokeWidth = 1.5f, cap = StrokeCap.Round)
    }
    // A first star.
    drawCircle(Color.White.copy(alpha = 0.6f + 0.4f * sin(t * 6 * PI).toFloat().coerceAtLeast(0f)), 1.8f, Offset(w * 0.8f, h * 0.14f))
}

/** A sunny morning: rolling hills under drifting clouds, grass swaying in the breeze. */
private fun DrawScope.drawTerrace(t: Float) {
    val w = size.width; val h = size.height
    drawRect(Brush.verticalGradient(listOf(c(0x7EC8F2), c(0xCDEBFA))))
    for (i in 0 until 4) {
        val x = ((hash(i, 11) + t * (0.5f + 0.3f * hash(i, 12))) % 1.3f) * w * 1.1f - w * 0.25f
        val y = h * (0.15f + 0.25f * hash(i, 13))
        val r = h * (0.07f + 0.04f * hash(i, 14))
        val cloud = Color.White.copy(alpha = 0.85f)
        drawCircle(cloud, r, Offset(x, y)); drawCircle(cloud, r * 1.3f, Offset(x + r * 1.2f, y - r * 0.3f)); drawCircle(cloud, r, Offset(x + r * 2.4f, y))
    }
    drawCircle(c(0x8FD18A), h * 0.9f, Offset(w * 0.2f, h * 1.55f))
    drawCircle(c(0x6DBB6B), h * 1.0f, Offset(w * 0.85f, h * 1.7f))
    // Grass blades swaying in the wind.
    for (i in 0 until 22) {
        val x = hash(i, 21) * w
        val base = h * (0.8f + 0.15f * hash(i, 22))
        val sway = sin((t * 6 + hash(i, 23)) * 2 * PI).toFloat() * h * 0.02f
        drawLine(c(0x4E9A4C), Offset(x, base), Offset(x + sway, base - h * 0.06f), strokeWidth = 2f, cap = StrokeCap.Round)
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
