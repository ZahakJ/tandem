package io.github.zahakj.tandem.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Process
import android.util.Log
import io.github.zahakj.tandem.audio.Player
import io.github.zahakj.tandem.audio.Song

/**
 * Plays Tandem's synthesized soundtrack on its own audio thread through an [AudioTrack].
 *
 * The thread exists only while music plays: [stop] fades out and then ends the thread and releases the track, so
 * Tandem uses no CPU for music while a game runs. Rendering happens in blocks into preallocated buffers; nothing is
 * allocated per block, so the garbage collector never interrupts the UI because of music.
 */
internal object SynthEngine {
    private const val FRAMES = 1024
    const val FADE_SECONDS = 0.6f

    @Volatile private var thread: Thread? = null
    @Volatile private var pending: Song? = null
    @Volatile private var stopping = false
    @Volatile private var restart = false
    /** Wanted volume, 0..1, already including ducking. */
    @Volatile var volume = 0.4f
    @Volatile var player: Player? = null
        private set
    /** Called on the audio thread each time a song has played through once (for shuffle). */
    @Volatile var onLooped: (() -> Unit)? = null

    val running: Boolean get() = thread != null

    fun outputRate(ctx: Context): Int =
        (ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager)
            .getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)?.toIntOrNull()?.takeIf { it in 22050..96000 } ?: 48000

    /** Starts (or switches to) [song]. Switching fades the old song out and the new one in. */
    @Synchronized
    fun play(ctx: Context, song: Song) {
        stopping = false
        if (player?.song?.id != song.id) pending = song
        if (thread == null) start(outputRate(ctx)) else restart = true
    }

    /** How long the current fade-out takes. */
    @Volatile private var fadeOut = FADE_SECONDS

    /** Fades out over [seconds], then stops the thread and releases the audio track. */
    fun stop(seconds: Float = FADE_SECONDS) { fadeOut = seconds; stopping = true; restart = false }

    private fun start(rate: Int) {
        val t = Thread({ run(rate) }, "tandem-music")
        thread = t
        t.start()
    }

    private fun run(rate: Int) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
        val track = try {
            val min = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_FLOAT)
            AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_FLOAT).setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build())
                .setTransferMode(AudioTrack.MODE_STREAM)
                // Music doesn't need low latency: a deep buffer lets the CPU sleep between blocks.
                .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_POWER_SAVING)
                .setBufferSizeInBytes(maxOf(min, FRAMES * 8 * 4))
                .build()
        } catch (e: Exception) {
            Log.w("Tandem", "Couldn't open audio for music", e)
            thread = null
            return
        }
        val buf = FloatArray(FRAMES * 2)
        var gain = 0f
        var lastLoop = 0
        var faded = false
        try {
            track.play()
            while (true) {
                val next = pending
                if (next != null && (player == null || gain <= 0f)) {
                    pending = null
                    player = Player(next, rate, seed = (System.nanoTime() and 0xFFFF).toInt())
                    lastLoop = 0
                }
                val p = player ?: break
                val want = if (stopping || pending != null) 0f else volume
                val step = 1f / ((if (stopping) fadeOut else FADE_SECONDS) * rate)
                p.render(buf, FRAMES)
                var i = 0
                while (i < buf.size) {
                    if (gain < want) gain = minOf(want, gain + step) else if (gain > want) gain = maxOf(want, gain - step)
                    buf[i] *= gain; buf[i + 1] *= gain
                    i += 2
                }
                track.write(buf, 0, buf.size, AudioTrack.WRITE_BLOCKING)
                if (p.loops != lastLoop) { lastLoop = p.loops; onLooped?.invoke() }
                if (stopping && gain <= 0f) { faded = true; break }
            }
            if (faded) {
                // The end of the fade is still in the track's buffer: push silence through so it plays out, rather
                // than pausing (and cutting) in the middle of it.
                buf.fill(0f)
                var left = track.bufferSizeInFrames
                while (left > 0 && !restart) {
                    val n = minOf(left, FRAMES)
                    track.write(buf, 0, n * 2, AudioTrack.WRITE_BLOCKING)
                    left -= n
                }
            }
        } catch (e: Exception) {
            Log.w("Tandem", "Music stopped", e)
        } finally {
            runCatching { track.pause(); track.flush() }
            track.release()
            synchronized(this) {
                thread = null
                // A play() that came in while we were stopping starts a fresh thread.
                if (restart) { restart = false; stopping = false; start(rate) }
            }
        }
    }
}
