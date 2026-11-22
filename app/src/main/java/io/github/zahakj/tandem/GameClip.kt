package io.github.zahakj.tandem

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaScannerConnection
import android.media.projection.MediaProjection
import android.os.Environment
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.nio.ByteBuffer
import java.time.LocalDateTime

/**
 * A short video clip of the game's screen from its companion: the next [SECONDS] seconds, or less when stopped,
 * saved to `ROMs/clips/` (Tandem's Gallery shows it). The picture is recorded through Shizuku with Android's
 * `screenrecord`, which sees any app's window, in hardware H.264 at the screen's own size. The sound (ClipAudio.kt)
 * is the game's, through a screen-capture grant, and your voice when that is on; it is put in beside the picture
 * afterwards, lined up with the moment the picture started.
 */
object GameClip {
    const val SECONDS = 15
    const val DIR = "ROMs/clips"
    private const val BIT_RATE = "12000000"
    /** The picture while it records, before the sound goes in. A dot name: the Gallery skips it. */
    private const val RAW = ".recording.mp4"

    /** When the clip being recorded ends ([SystemClock.elapsedRealtime]), or 0 while none is. */
    var endsAt by mutableLongStateOf(0L)
        private set

    sealed class Result {
        data class Saved(val file: File, val sound: Boolean) : Result()
        data class Failed(val why: String) : Result()
    }

    /** A file name for [title] at [time], the same as a screenshot's but a video. Plain. */
    fun fileName(title: String, time: LocalDateTime): String = GameShot.fileName(title, time, "Clip", "mp4")

    /**
     * Lets Tandem catch the game's sound without asking each time (the screen-capture grant), and hear your [voice].
     * Through Shizuku; true when the screen-capture grant is now Tandem's. Blocking.
     */
    fun grant(ctx: Context, voice: Boolean): Boolean {
        if (!Shell.ready()) return false
        val pkg = ctx.packageName
        if (voice) Shell.run("pm", "grant", pkg, android.Manifest.permission.RECORD_AUDIO)
        Shell.run("appops", "set", pkg, "PROJECT_MEDIA", "allow")
        return Shell.run("appops", "get", pkg, "PROJECT_MEDIA").orEmpty().contains("allow")
    }

    /**
     * Records it: [gameOnTop] when the game has the top screen, the game's sound through [projection] when given, and
     * the microphone when [voice] (and allowed). Blocking for the whole clip: call off the main thread. One at a time;
     * [stop] ends it early.
     */
    fun record(ctx: Context, title: String, gameOnTop: Boolean, projection: MediaProjection?, voice: Boolean): Result {
        if (!Shell.ready()) return Result.Failed(Tx.s(R.string.play_clip_needs_shizuku))
        if (endsAt != 0L) return Result.Failed(Tx.s(R.string.play_clip_busy))
        val ids = GameShot.displays(Shell.run("dumpsys", "SurfaceFlinger", "--display-id"))
        val id = (if (gameOnTop) ids.firstOrNull() else ids.getOrNull(1)) ?: return Result.Failed(Tx.s(R.string.play_shot_no_screen))
        val dir = File(Environment.getExternalStorageDirectory(), DIR)
        val file = File(dir, fileName(title, LocalDateTime.now()))
        val raw = File(dir, RAW)
        Shell.run("mkdir", "-p", dir.path)
        Shell.run("rm", "-f", raw.path)
        val heard = voice && ctx.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val audio = ClipAudio(projection, heard).takeIf { it.ok }
        // screenrecord makes its file as it starts: when it shows up is when the picture began.
        var pictureNs = 0L
        val watch = Thread {
            val until = SystemClock.elapsedRealtime() + 3000
            while (!raw.exists() && SystemClock.elapsedRealtime() < until) Thread.sleep(5)
            pictureNs = SystemClock.elapsedRealtimeNanos()
        }
        endsAt = SystemClock.elapsedRealtime() + SECONDS * 1000L
        // Android's green microphone dot sits over the game's screen while the microphone is on: off for the clip.
        val dot = if (heard) Indicator.hide() else null
        try {
            audio?.start()
            watch.start()
            Shell.run("screenrecord", "--display-id", id, "--time-limit", "$SECONDS", "--bit-rate", BIT_RATE, raw.path)
        } finally {
            audio?.stop()
            dot?.let { Indicator.restore(it) }
            endsAt = 0L
        }
        watch.join(100)
        if (!raw.isFile || raw.length() == 0L) return Result.Failed(Tx.s(R.string.play_clip_failed))
        val format = audio?.format
        val sound = audio != null && format != null && audio.samples.isNotEmpty() && runCatching {
            mux(raw, file, format, audio.samples, (pictureNs - audio.startedNs) / 1000)
        }.onFailure { android.util.Log.w("Tandem", "Couldn't add the clip's sound", it); file.delete() }.isSuccess
        if (!sound && !raw.renameTo(file)) return Result.Failed(Tx.s(R.string.play_clip_not_saved))
        raw.delete()
        MediaScannerConnection.scanFile(ctx, arrayOf(file.path), arrayOf("video/mp4"), null)
        return Result.Saved(file, sound)
    }

    /** Android's camera and microphone indicator (SystemUI follows this flag as it changes). Through Shizuku. */
    private object Indicator {
        private const val NS = "privacy"
        private const val KEY = "camera_mic_icons_enabled"

        /** Hides it; returns what to put back ("null" when it was never set). */
        fun hide(): String {
            val was = Shell.run("device_config", "get", NS, KEY)?.trim().orEmpty().ifEmpty { "null" }
            Shell.run("device_config", "put", NS, KEY, "false")
            return was
        }

        fun restore(was: String) {
            if (was == "null") Shell.run("device_config", "delete", NS, KEY) else Shell.run("device_config", "put", NS, KEY, was)
        }
    }

    /** Ends the clip being recorded now; it is kept up to here. Blocking (asks Shizuku). */
    fun stop() {
        if (endsAt != 0L) Shell.run("pkill", "-2", "-x", "screenrecord")
    }

    /** [video]'s picture and the [sound] (its first [leadUs] cut, the part before the picture began) into [out]. */
    private fun mux(video: File, out: File, format: MediaFormat, sound: List<ClipAudio.Sample>, leadUs: Long) {
        val ex = MediaExtractor().apply { setDataSource(video.path) }
        val muxer = MediaMuxer(out.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        try {
            val track = (0 until ex.trackCount).first { ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME).orEmpty().startsWith("video/") }
            val picture = ex.getTrackFormat(track)
            ex.selectTrack(track)
            if (picture.containsKey(MediaFormat.KEY_ROTATION)) muxer.setOrientationHint(picture.getInteger(MediaFormat.KEY_ROTATION))
            val v = muxer.addTrack(picture)
            val a = muxer.addTrack(format)
            muxer.start()
            val buf = ByteBuffer.allocate(4 shl 20)
            val info = MediaCodec.BufferInfo()
            var last = 0L
            while (true) {
                val size = ex.readSampleData(buf, 0)
                if (size < 0) break
                val key = if (ex.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
                last = ex.sampleTime
                info.set(0, size, last, key)
                muxer.writeSampleData(v, buf, info)
                ex.advance()
            }
            for (s in sound) {
                val t = s.timeUs - leadUs
                if (t < 0) continue
                if (t > last) break
                info.set(0, s.data.size, t, 0)
                muxer.writeSampleData(a, ByteBuffer.wrap(s.data), info)
            }
            muxer.stop()
        } finally {
            runCatching { muxer.release() }
            ex.release()
        }
    }
}
