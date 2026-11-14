package io.github.zahakj.tandem

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.os.SystemClock
import android.util.Log

/**
 * A clip's sound (GameClip.kt): what the game plays, caught through a screen-capture grant ([projection]; Android's
 * playback capture, which an app may refuse), mixed with the microphone when [voice], encoded to AAC as it comes.
 * Kept in memory: 15 seconds is a few hundred KB. Either source may be missing; with neither, [ok] is false.
 */
@SuppressLint("MissingPermission")
class ClipAudio(projection: MediaProjection?, voice: Boolean) {
    class Sample(val data: ByteArray, val timeUs: Long, val flags: Int)

    private val game: AudioRecord? = projection?.let { p ->
        runCatching {
            val capture = AudioPlaybackCaptureConfiguration.Builder(p)
                .addMatchingUsage(AudioAttributes.USAGE_GAME).addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN).build()
            AudioRecord.Builder().setAudioPlaybackCaptureConfig(capture).setAudioFormat(pcm(AudioFormat.CHANNEL_IN_STEREO))
                .setBufferSizeInBytes(BUFFER).build().takeIf { it.state == AudioRecord.STATE_INITIALIZED }
        }.onFailure { Log.w("Tandem", "No game sound for the clip", it) }.getOrNull()
    }
    private val mic: AudioRecord? = if (!voice) null else runCatching {
        AudioRecord.Builder().setAudioSource(MediaRecorder.AudioSource.MIC).setAudioFormat(pcm(AudioFormat.CHANNEL_IN_MONO))
            .setBufferSizeInBytes(BUFFER).build().takeIf { it.state == AudioRecord.STATE_INITIALIZED }
    }.onFailure { Log.w("Tandem", "No voice for the clip", it) }.getOrNull()

    val ok: Boolean get() = game != null || mic != null

    /** The encoded sound, in order, and its format once the encoder has said it. */
    val samples = ArrayList<Sample>()
    @Volatile var format: MediaFormat? = null
        private set
    /** When the first sound came in ([SystemClock.elapsedRealtimeNanos]), or 0 before. */
    @Volatile var startedNs = 0L
        private set

    @Volatile private var running = false
    private var thread: Thread? = null

    fun start() {
        if (!ok) return
        running = true
        thread = Thread({ runCatching { loop() }.onFailure { Log.w("Tandem", "Clip sound stopped", it) } }, "tandem-clip-sound").apply { start() }
    }

    /** Stops and finishes the encoding. Blocking for a moment. */
    fun stop() {
        running = false
        thread?.join(3000)
    }

    private fun loop() {
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        codec.configure(MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, RATE, 2).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, 160_000)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, FRAME * 4)
        }, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()
        game?.startRecording()
        mic?.startRecording()
        val stereo = ShortArray(FRAME * 2)
        val voice = ShortArray(FRAME)
        val bytes = java.nio.ByteBuffer.allocate(FRAME * 4).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        var frames = 0L
        try {
            while (running) {
                // The game's sound sets the pace when there is some (Android fills silence in); else the microphone.
                if (game != null) {
                    if (game.read(stereo, 0, stereo.size) <= 0) continue
                    val got = mic?.read(voice, 0, FRAME, AudioRecord.READ_NON_BLOCKING) ?: 0
                    for (i in 0 until got) {
                        stereo[2 * i] = mix(stereo[2 * i], voice[i])
                        stereo[2 * i + 1] = mix(stereo[2 * i + 1], voice[i])
                    }
                } else {
                    if (mic!!.read(voice, 0, FRAME) <= 0) continue
                    for (i in 0 until FRAME) { stereo[2 * i] = voice[i]; stereo[2 * i + 1] = voice[i] }
                }
                if (startedNs == 0L) startedNs = SystemClock.elapsedRealtimeNanos()
                bytes.clear(); bytes.asShortBuffer().put(stereo)
                feed(codec, bytes.array(), frames * 1_000_000L / RATE, 0)
                frames += FRAME
                drain(codec)
            }
            feed(codec, ByteArray(0), frames * 1_000_000L / RATE, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            drain(codec, untilEnd = true)
        } finally {
            game?.runCatching { stop(); release() }
            mic?.runCatching { stop(); release() }
            codec.runCatching { stop(); release() }
        }
    }

    private fun feed(codec: MediaCodec, pcm: ByteArray, timeUs: Long, flags: Int) {
        val i = codec.dequeueInputBuffer(100_000)
        if (i < 0) return
        codec.getInputBuffer(i)!!.apply { clear(); put(pcm) }
        codec.queueInputBuffer(i, 0, pcm.size, timeUs, flags)
    }

    private fun drain(codec: MediaCodec, untilEnd: Boolean = false) {
        val info = MediaCodec.BufferInfo()
        while (true) {
            val o = codec.dequeueOutputBuffer(info, if (untilEnd) 100_000 else 0)
            when {
                o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> format = codec.outputFormat
                o >= 0 -> {
                    val end = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    // The setup bytes travel in [format]; the muxer takes them from there.
                    if (info.size > 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                        val data = ByteArray(info.size)
                        codec.getOutputBuffer(o)!!.apply { position(info.offset); get(data) }
                        samples += Sample(data, info.presentationTimeUs, info.flags)
                    }
                    codec.releaseOutputBuffer(o, false)
                    if (end) return
                }
                o == MediaCodec.INFO_TRY_AGAIN_LATER -> return
            }
        }
    }

    private companion object {
        const val RATE = 48_000
        /** One AAC frame of samples per channel. */
        const val FRAME = 1024
        const val BUFFER = FRAME * 4 * 8

        fun pcm(channels: Int): AudioFormat =
            AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(RATE).setChannelMask(channels).build()

        fun mix(a: Short, b: Short): Short = (a + b).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
    }
}
