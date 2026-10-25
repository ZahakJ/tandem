package io.github.zahakj.tandem.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.SoundPool
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.zahakj.tandem.Library
import io.github.zahakj.tandem.audio.Family
import io.github.zahakj.tandem.audio.Jingles
import io.github.zahakj.tandem.audio.LAUNCH_JINGLE_SECONDS
import io.github.zahakj.tandem.audio.Sfx
import io.github.zahakj.tandem.audio.SfxSynth
import io.github.zahakj.tandem.audio.SoundPack
import io.github.zahakj.tandem.audio.Tracks
import io.github.zahakj.tandem.audio.renderJingle
import io.github.zahakj.tandem.data.Store
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.pow
import kotlin.random.Random

/**
 * UI sounds. They are synthesized in code (see [SfxSynth]) the first time they are needed, cached as tiny WAV files
 * at the device's own sample rate, and played through a [SoundPool], which mixes them on Android's fast audio path
 * (well under 20 ms) without allocating per sound.
 *
 * Only the chosen pack is kept loaded. Jingles and Pip's voice are made in the background on first use.
 */
object Sounds {
    private const val VERSION = 4
    private var pool: SoundPool? = null
    private var dir: File? = null
    private var rate = 48000
    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "tandem-sfx").apply { priority = Thread.MIN_PRIORITY } }
    private val main = Handler(Looper.getMainLooper())

    /** Loaded sample ids by "pack/kind", "jingle/<family>", "launch/<family>" and "pip/<n>". */
    private val ids = ConcurrentHashMap<String, Int>()
    /** The same for each pack's sounds, by [Sfx.ordinal] (0 = not loaded yet), so playing one builds no key string. */
    private val packIds = ConcurrentHashMap<SoundPack, IntArray>()
    private val loading = ConcurrentHashMap.newKeySet<String>()
    private var loadedPack: SoundPack? = null

    private var lastAt = 0L
    private var lastKind: Sfx? = null
    private var lastMoveAt = 0L
    private var quietUntil = 0L

    // A virtual cursor from the D-pad, so ticks rise and fall a little as you move around.
    private var row = 0
    private var col = 0
    private var holding = false
    private val rowSteps = floatArrayOf(0f, 2f, 4f, 7f, 9f)

    fun init(ctx: Context) {
        if (pool != null) return
        rate = SynthEngine.outputRate(ctx)
        pool = SoundPool.Builder()
            .setMaxStreams(8)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    // Game usage: the volume buttons control UI sounds and music together, like on a console.
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .build()
        dir = File(ctx.cacheDir, "sounds").apply { mkdirs() }
        // Old cache files from earlier versions.
        dir?.listFiles()?.filter { !it.name.startsWith("v$VERSION-") }?.forEach { it.delete() }
        ensurePack(SoundPack.of(Store.settings.look.soundPack))
        worker.execute {
            for (i in 0 until 4) load("pip/$i") { SfxSynth.pipSyllable(i, rate) }
            load("hold") { SfxSynth.holdRise(rate, HOLD_MS / 1000.0) }
        }
        if (Store.settings.look.jingles != "off") prepareJingles()
    }

    /** Makes sure [pack] is loaded and the previous one is unloaded. Cheap when nothing changed. */
    fun ensurePack(pack: SoundPack) {
        if (loadedPack == pack) return
        val old = loadedPack
        loadedPack = pack
        worker.execute {
            val table = packIds.getOrPut(pack) { IntArray(Sfx.entries.size) }
            for (k in Sfx.entries) {
                val key = "${pack.id}/${k.name}"
                load(key) { SfxSynth.render(pack, k, rate) }
                table[k.ordinal] = ids[key] ?: 0
            }
            if (old != null && old != pack && loadedPack != old) unloadPack(old)
        }
    }

    private fun unloadPack(pack: SoundPack) {
        packIds.remove(pack)
        for (k in Sfx.entries) ids.remove("${pack.id}/${k.name}")?.let { pool?.unload(it) }
    }

    /** Renders (or reuses the cached file for) [key] and loads it. Runs on the worker thread. */
    private fun load(key: String, make: () -> FloatArray) {
        if (ids.containsKey(key) || !loading.add(key)) return
        try {
            val f = File(dir ?: return, "v$VERSION-${rate}-${key.replace('/', '-')}.wav")
            if (!f.isFile) writeWav(f, make(), rate)
            pool?.load(f.path, 1)?.let { ids[key] = it }
        } catch (e: Exception) {
            Log.w("Tandem", "Couldn't make sound $key", e)
        } finally {
            loading.remove(key)
        }
    }

    // --- What the UI calls ------------------------------------------------------------------------------------

    fun move() { play(Sfx.Move) }
    fun select() { play(Sfx.Select) }
    fun back() { play(Sfx.Back) }
    fun error() { play(Sfx.Error) }
    fun toggle() { play(Sfx.ToggleOn) }
    fun toggle(on: Boolean) { play(if (on) Sfx.ToggleOn else Sfx.ToggleOff) }
    fun menuOpen() { play(Sfx.MenuOpen) }
    fun menuClose() { play(Sfx.MenuClose) }
    fun favorite() { play(Sfx.Favorite) }
    fun confirm() { play(Sfx.Confirm) }
    /** An Undo (from a toast, Select or a menu) took a step back. */
    fun undo() { play(Sfx.Back) }

    /** The rising tone under a hold-to-confirm ring (HoldConfirm.kt); returns the stream for [stopHold]. */
    fun holdRise(): Int {
        if (!Store.settings.look.sounds) return 0
        val id = ids["hold"] ?: return 0
        return playId(id, Sfx.Confirm, 0.7f, 1f)
    }

    /** Stops a [holdRise] tone: let go early, or the hold confirmed and [confirm] takes over. */
    fun stopHold(stream: Int) { if (stream != 0) pool?.stop(stream) }
    fun achievement() { play(Sfx.Achievement) }
    fun key() { play(Sfx.Key) }
    fun saverIn() { play(Sfx.SaverIn) }
    fun saverOut() { play(Sfx.SaverOut) }
    /** L1 / R1 turned to the next system, collection, settings page or tab. */
    fun page() { play(Sfx.Page) }
    /** The two screens traded places. */
    fun swap() { play(Sfx.Swap) }

    private var lastFoundAt = 0L

    /** Scraping found art for a game. Safe from any thread; at most a few blips a second. */
    fun found() {
        val now = SystemClock.uptimeMillis()
        if (now - lastFoundAt < 350) return
        lastFoundAt = now
        play(Sfx.Found, volume = 0.7f)
    }

    /**
     * A game is starting: the system's launch jingle, or the pack's launch sting.
     *
     * The emulator covers Tandem about 0.75-0.85 s after A (the launch animation is 0.6 s, then Android starts the
     * emulator; measured on the Thor for RetroArch, Azahar and Eden), so every launch sound is over within 0.6 s and
     * decays on its own. If a game ever comes up sooner, [fadeTail] eases what's left out instead of cutting it.
     */
    fun launch(platformId: String? = null) {
        val look = Store.settings.look
        GameJingles.stopNow()
        // The music fades out (in a quarter of a second) under the launch sound and stays off while the game runs
        // (even on the other screen).
        Music.gameLaunched()
        val family = platformId?.let(Family::of)
        if (family != null && look.sounds && look.jingles != "off") {
            val id = ids["launch/${family.name}"]
            if (id != null) { markTail(playId(id, Sfx.Launch, 0.9f, 1f), 0.9f); return }
            prepareJingles()
        }
        markTail(play(Sfx.Launch), base(Sfx.Launch))
    }

    // The launch sound still ringing, so it can be faded rather than cut when Tandem leaves the screen.
    private var tailStream = 0
    private var tailUntil = 0L
    private var tailVolume = 0f
    private var tailGain = 0f
    private val tailStep = object : Runnable {
        override fun run() {
            val p = pool ?: return
            tailGain -= 0.2f
            if (tailGain <= 0f) { p.stop(tailStream); tailStream = 0; return }
            val v = tailVolume * tailGain * tailGain
            p.setVolume(tailStream, v, v)
            main.postDelayed(this, 12)
        }
    }

    private fun markTail(stream: Int, vol: Float) {
        if (stream == 0) return
        main.removeCallbacks(tailStep)
        tailStream = stream
        tailUntil = SystemClock.uptimeMillis() + 800
        tailVolume = (vol * Store.settings.look.soundVolume).coerceIn(0f, 1f)
        tailGain = 1f
    }

    /** Tandem is leaving the screen: a launch sound that is still ringing fades out over 60 ms instead of stopping dead. */
    fun fadeTail() {
        if (tailStream == 0 || SystemClock.uptimeMillis() > tailUntil) return
        main.removeCallbacks(tailStep)
        main.post(tailStep)
    }

    /** A system was opened: its little hello jingle, when the user turned those on. */
    fun systemOpened(platformId: String) {
        val look = Store.settings.look
        if (!look.sounds || look.jingles != "all") return
        val id = ids["jingle/${Family.of(platformId).name}"] ?: return prepareJingles()
        playId(id, Sfx.Confirm, 0.7f, 1f)
    }

    /** Plays [f]'s launch jingle on its own (settings preview). */
    fun previewJingle(f: Family) {
        val id = ids["launch/${f.name}"] ?: return prepareJingles()
        playId(id, Sfx.Launch, 0.9f, 1f, mark = false)
    }

    /** Renders every family's jingles in the background (once; cached on disk afterwards). */
    fun prepareJingles() {
        if (pool == null) return
        worker.execute {
            for (f in Family.entries) load("launch/${f.name}") { renderJingle(Jingles.launch(f), rate, 0.6f, LAUNCH_JINGLE_SECONDS) }
            if (Store.settings.look.jingles == "all") {
                for (f in Family.entries) load("jingle/${f.name}") { renderJingle(Jingles.details(f), rate, 0.6f, 1.6) }
            }
        }
    }

    /** Pip "says" [text]: a few quick chirpy syllables, more for longer messages. */
    fun pipSays(text: String) {
        val look = Store.settings.look
        if (!look.sounds || !look.pipVoice) return
        val words = text.split(' ').count { it.any(Char::isLetter) }
        val n = (words + 1).coerceIn(2, 7)
        val rnd = Random(text.hashCode())
        val scale = floatArrayOf(0f, 2f, 4f, 7f, 9f, 12f)
        var t = 90L
        for (i in 0 until n) {
            val syl = rnd.nextInt(4)
            // Questions go up at the end; everything else settles down.
            val semis = if (i == n - 1) (if (text.trimEnd().endsWith("?")) 12f else 0f) else scale[rnd.nextInt(scale.size)]
            val r = 2f.pow(semis / 12f) * (0.95f + rnd.nextFloat() * 0.1f)
            main.postDelayed({ ids["pip/$syl"]?.let { playId(it, Sfx.Toast, 0.55f, r, mark = false) } }, t)
            t += 62L + rnd.nextInt(30)
        }
    }

    /** A toast appeared: a soft pop, then Pip's voice. */
    fun toast(text: String) {
        // A richer sound (favorite, error...) just played for the same action: let it speak alone.
        val busy = lastKind != null && lastKind != Sfx.Move && lastKind != Sfx.Select && SystemClock.uptimeMillis() - lastAt < 250
        if (!busy) play(Sfx.Toast)
        pipSays(text)
    }

    /** Plays a short demo of [pack] (used when choosing a pack). */
    fun preview(pack: SoundPack) {
        ensurePack(pack)
        val seq = listOf(Sfx.Move to 0L, Sfx.Move to 110L, Sfx.Select to 260L, Sfx.ToggleOn to 520L, Sfx.Favorite to 760L)
        for ((k, at) in seq) main.postDelayed({
            packIds[pack]?.get(k.ordinal)?.takeIf { it != 0 }?.let { playId(it, k, base(k), 1f, mark = false) }
        }, at + 120)
    }

    /** No focus ticks for a moment, e.g. while Tandem comes back and puts focus where you left it. */
    fun hush(ms: Long) { quietUntil = SystemClock.uptimeMillis() + ms }

    /** The D-pad moved; [repeat] > 0 while it is held down. */
    fun onDirection(dx: Int, dy: Int, repeat: Int) {
        row += dy; col += dx
        holding = repeat > 0
    }

    /** Plays [kind] from the chosen pack; returns its stream id (0 when nothing played). Allocates nothing. */
    fun play(kind: Sfx, volume: Float = 1f): Int {
        val look = Store.settings.look
        if (!look.sounds) return 0
        val now = SystemClock.uptimeMillis()
        val since = now - lastAt
        val pack = SoundPack.of(look.soundPack)
        var rate = 1f
        var vol = base(kind) * volume
        when (kind) {
            Sfx.Move -> {
                if (now < quietUntil) return 0
                // A screen opening moves focus right after a select; never tick over a richer sound.
                if (lastKind != Sfx.Move && since < 180) return 0
                // Holding the D-pad: fewer, softer ticks so it never gets tiring.
                val gap = if (holding) 85 else 40
                if (now - lastMoveAt < gap) return 0
                lastMoveAt = now
                if (holding) vol *= 0.7f
                // The cursor's place nudges the pitch; the Modern pack only by a shade, so it stays one sound.
                val semis = rowSteps[((row % 5) + 5) % 5] * 0.5f + (if (col and 1 == 1) 0.5f else 0f)
                rate = 2f.pow(semis * (if (pack == SoundPack.Modern) 0.35f else 1f) / 12f)
            }
            // A launch, toggle or any special sound replaces the plain select that follows it.
            Sfx.Select -> if (lastKind != null && lastKind != Sfx.Move && lastKind != Sfx.Select && since < 160) return 0
            Sfx.MenuClose -> if (lastKind != null && lastKind != Sfx.Move && since < 250) return 0
            Sfx.MenuOpen -> if (lastKind != null && lastKind != Sfx.Move && lastKind != Sfx.Select && since < 250) return 0
            // Flipping through pages with L1 / R1 held: no more than about eight a second.
            Sfx.Page -> if (lastKind == Sfx.Page && since < 120) return 0
            else -> {}
        }
        ensurePack(pack)
        val id = packIds[pack]?.get(kind.ordinal) ?: 0
        if (id == 0) return 0
        return playId(id, kind, vol, rate)
    }

    private fun playId(id: Int, kind: Sfx, vol: Float, rate: Float, mark: Boolean = true): Int {
        if (mark) { lastAt = SystemClock.uptimeMillis(); lastKind = kind }
        val v = (vol * Store.settings.look.soundVolume).coerceIn(0f, 1f)
        // When all streams are busy, SoundPool drops the lowest priority first: ticks give way to everything else.
        val priority = when (kind) { Sfx.Move, Sfx.Key -> 0; Sfx.Launch, Sfx.Achievement -> 2; else -> 1 }
        return pool?.play(id, v, v, priority, 0, rate.coerceIn(0.5f, 2f)) ?: 0
    }

    private fun base(kind: Sfx): Float = when (kind) {
        Sfx.Move, Sfx.Key -> 0.6f
        Sfx.Launch, Sfx.Achievement -> 0.95f
        else -> 0.8f
    }

    private fun writeWav(file: File, samples: FloatArray, rate: Int) {
        val data = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (s in samples) data.putShort((s.coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort())
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + data.capacity()); put("WAVE".toByteArray())
            put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(1); putInt(rate); putInt(rate * 2); putShort(2); putShort(16)
            put("data".toByteArray()); putInt(data.capacity())
        }
        val tmp = File(file.path + ".tmp")
        FileOutputStream(tmp).use { it.write(header.array()); it.write(data.array()) }
        tmp.renameTo(file)
    }
}

/** What the music player is doing, for the now-playing card. */
data class NowPlaying(val id: String, val title: String, val mood: String, val own: Boolean)

/**
 * Background music: Tandem's own synthesized soundtrack ([Tracks]), or the user's files from `ROMs/tandem/music/`.
 *
 * It plays only while Tandem is on screen: it fades out and fully stops (no audio thread, no CPU) when a game starts
 * or the screen turns off, and fades back in when you return. It asks Android for audio focus, pauses for calls and
 * other apps, ducks under notifications, and stays quiet if another app was already playing music.
 */
object Music {
    val dir: File get() = File(Library.romsDir, "tandem/music")
    private val extensions = setOf("mp3", "ogg", "oga", "opus", "flac", "m4a", "aac", "wav")
    private val main = Handler(Looper.getMainLooper())
    private var app: Context? = null
    private var player: MediaPlayer? = null
    private var queue = ArrayDeque<File>()
    private var visible = false
    private var fade = 0f
    private var duck = 1f
    private var dim = 1f
    private var gameUp = false
    private var hasFocus = false
    private var focusRequest: AudioFocusRequest? = null
    private var shuffleTrack: String? = null
    /** Another app was playing when we came back: leave it alone until next time. */
    private var yielded = false

    var now by mutableStateOf<NowPlaying?>(null)
        private set

    fun tracks(): List<File> = dir.listFiles().orEmpty().filter { it.isFile && it.extension.lowercase() in extensions }.sortedBy { it.name.lowercase() }

    fun init(ctx: Context) { app = ctx.applicationContext }

    /** Tandem is on screen. */
    fun resume() {
        visible = true
        dim = 1f
        gameUp = false
        val am = audio()
        // Someone is already listening to their own music: don't talk over it.
        yielded = am != null && am.isMusicActive && player == null && !SynthEngine.running
        refresh()
    }

    /** Tandem left the screen (a game is running, or the device slept): fade out and stop completely. */
    fun pause() {
        visible = false
        stopAll()
    }

    /** Re-reads the settings: starts, stops, switches track or changes volume. */
    fun refresh() {
        val look = Store.settings.look
        if (!look.music || !visible || yielded || gameUp) { stopAll(); return }
        if (!requestFocus()) { stopAll(); return }
        val own = look.myMusic && tracks().isNotEmpty()
        if (own) {
            SynthEngine.stop()
            val p = player
            if (p == null) next() else {
                if (!p.isPlaying) runCatching { p.start() }
                fadeTo(1f)
            }
        } else {
            stopFile()
            val ctx = app ?: return
            val id = trackId()
            val song = Tracks.byId(id) ?: Tracks.all.first()
            SynthEngine.volume = synthVolume()
            SynthEngine.onLooped = if (look.musicTrack == "shuffle") ({ main.post { shuffleNext() } }) else null
            SynthEngine.play(ctx, song)
            now = NowPlaying(song.id, song.title, song.mood, own = false)
        }
    }

    /** The soundtrack id the settings ask for right now. */
    fun trackId(): String {
        val look = Store.settings.look
        // "Next song" picked something else; it holds until the soundtrack or theme setting changes.
        skipped?.let { (forKey, id) -> if (forKey == skipKey()) return id else skipped = null }
        val pack = Tracks.names(look.musicPack).map { it.first }
        return when (look.musicTrack) {
            "theme" -> Tracks.forTheme(look.theme, look.musicPack)
            "shuffle" -> shuffleTrack?.takeIf { it in pack } ?: pack.random().also { shuffleTrack = it }
            else -> look.musicTrack
        }
    }

    private fun skipKey(): String = Store.settings.look.let { it.musicTrack + "|" + it.theme + "|" + it.musicPack }

    private fun shuffleNext() {
        val cur = shuffleTrack
        shuffleTrack = Tracks.names(Store.settings.look.musicPack).map { it.first }.filter { it != cur }.random()
        refresh()
    }

    /** Next song: another file from the music folder, or another soundtrack piece when shuffling. */
    fun skip() {
        val look = Store.settings.look
        when {
            look.myMusic && tracks().isNotEmpty() -> { stopFile(); next() }
            look.musicTrack == "shuffle" -> shuffleNext()
            else -> {
                val ids = Tracks.names(look.musicPack).map { it.first }
                val next = ids[(ids.indexOf(trackId()) + 1) % ids.size]
                skipped = skipKey() to next
                refresh()
            }
        }
    }

    private var skipped: Pair<String, String>? = null

    /** A game is starting: fade out now, and stay quiet until Tandem is back in front or the game has closed. */
    fun gameLaunched() { gameUp = true; stopAll(fast = true) }

    /** The launched game failed to start or is no longer running. */
    fun gameClosed() { if (gameUp) { gameUp = false; refresh() } }

    /** Quieter while the screensaver is up. */
    fun dim(on: Boolean) { dim = if (on) 0.45f else 1f; applyVolume() }

    /** Peak levels of the soundtrack's instruments (0..1), for the visualizer; empty when nothing is playing. */
    fun levels(): FloatArray? = SynthEngine.player?.meters?.takeIf { SynthEngine.running }

    val playing: Boolean get() = SynthEngine.running || player?.isPlaying == true

    // Dips while a game's hover jingle plays (GameJingles).
    private var jingle = 1f

    /** A game's hover jingle started or stopped: the music dips under it. */
    fun underJingle(on: Boolean) {
        val j = if (on) 0.35f else 1f
        if (j != jingle) { jingle = j; applyVolume() }
    }

    private fun synthVolume() = Store.settings.look.musicVolume * duck * dim * jingle

    private fun applyVolume() {
        SynthEngine.volume = synthVolume()
        val v = fade * Store.settings.look.musicVolume * duck * dim * jingle
        player?.runCatching { setVolume(v, v) }
    }

    /** Fades out and stops; [fast] (a game launching) takes a quarter of a second, so the launch sound has the room. */
    private fun stopAll(fast: Boolean = false) {
        SynthEngine.stop(if (fast) 0.25f else SynthEngine.FADE_SECONDS)
        fadeTo(0f, if (fast) 0.16f else 0.08f) { stopFile() }
        abandonFocus()
    }

    // --- Audio focus ---------------------------------------------------------------------------------------------

    private fun audio(): AudioManager? = app?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_GAIN -> { duck = 1f; applyVolume(); if (visible) refresh() }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> { duck = 0.25f; applyVolume() }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> { SynthEngine.stop(); fadeTo(0f) { player?.takeIf { it.isPlaying }?.pause() } }
            AudioManager.AUDIOFOCUS_LOSS -> { hasFocus = false; yielded = true; stopAll() }
        }
    }

    private fun requestFocus(): Boolean {
        if (hasFocus) return true
        val am = audio() ?: return false
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setOnAudioFocusChangeListener(focusListener, main)
            .setWillPauseWhenDucked(false)
            .build()
        focusRequest = req
        hasFocus = am.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        return hasFocus
    }

    private fun abandonFocus() {
        val req = focusRequest ?: return
        audio()?.abandonAudioFocusRequest(req)
        focusRequest = null
        hasFocus = false
    }

    // --- The user's own files --------------------------------------------------------------------------------

    private fun next() {
        val look = Store.settings.look
        if (queue.isEmpty()) {
            val all = tracks()
            if (all.isEmpty()) return
            queue = ArrayDeque(if (look.musicShuffle) all.shuffled() else all)
        }
        val file = queue.removeFirst()
        runCatching {
            player = MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                setDataSource(file.path)
                setOnCompletionListener { stopFile(); if (visible && Store.settings.look.music) next() }
                setOnErrorListener { _, _, _ -> main.post { stopFile(); next() }; true }
                prepare()
                setVolume(0f, 0f)
                start()
            }
            fade = 0f
            now = NowPlaying("file", file.nameWithoutExtension, "From your music folder", own = true)
            fadeTo(1f)
        }.onFailure { Log.w("Tandem", "Couldn't play ${file.name}", it); stopFile() }
    }

    private fun stopFile() {
        main.removeCallbacksAndMessages(FADE)
        player?.runCatching { stop(); release() }
        player = null
        fade = 0f
    }

    private val FADE = Any()

    /** Smoothly ramps the file player's volume by [step] every 40 ms (about half a second by default), then runs [then]. */
    private fun fadeTo(target: Float, step: Float = 0.08f, then: () -> Unit = {}) {
        main.removeCallbacksAndMessages(FADE)
        val tick = object : Runnable {
            override fun run() {
                val p = player ?: return then()
                fade += (target - fade).coerceIn(-step, step)
                applyVolume()
                if (abs(target - fade) > 0.001f) main.postAtTime(this, FADE, SystemClock.uptimeMillis() + 40) else then()
                if (p !== player) return
            }
        }
        main.postAtTime(tick, FADE, SystemClock.uptimeMillis())
    }
}
