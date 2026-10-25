package io.github.zahakj.tandem.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.view.SurfaceView
import android.view.TextureView
import androidx.annotation.OptIn
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Constraints
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.RenderersFactory
import androidx.media3.exoplayer.mediacodec.MediaCodecAdapter
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.video.MediaCodecVideoRenderer
import androidx.media3.exoplayer.video.VideoRendererEventListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.max
import kotlin.math.roundToInt

/** True while the screen this composable is on is visible (its lifecycle is at least STARTED). */
@Composable
fun rememberIsVisible(): Boolean {
    val owner = LocalLifecycleOwner.current
    var visible by remember { mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, _ -> visible = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    return visible
}

/**
 * A muted, looping video that fills its box (cropped to cover, like a wallpaper). Decoding is done in hardware by
 * ExoPlayer, video only (the sound track is never decoded) and at most about 30 frames a second. The player only
 * exists while the screen is visible: it is released when Tandem is hidden (a game is running) and created again
 * when Tandem comes back, so a video never costs anything while you play. While [paused] (covered by Settings, a
 * popup or the screensaver) it holds its frame and decodes nothing.
 *
 * With [surface] (full-screen wallpapers) the frames go to a SurfaceView: straight to the display compositor, so a
 * new video frame doesn't make Tandem redraw its window (a TextureView redraws the whole window for every frame).
 * A SurfaceView can't be faded, masked or cropped by Compose, so it is only for a box that fills its screen; smaller
 * or faded videos keep the TextureView.
 *
 * The video fades in once its first frame is ready, so whatever is drawn underneath shows until then.
 */
@OptIn(UnstableApi::class)
@Composable
fun LoopingVideo(file: File, modifier: Modifier = Modifier, paused: Boolean = false, surface: Boolean = false, onReady: (Boolean) -> Unit = {}) {
    if (!rememberIsVisible()) return
    val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current
    // A player kept from a moment ago (a quick trip out of Tandem) carries on where it was, with no fade.
    val kept = remember(file) { VideoKeeper.take(file) }
    var ready by remember(file) { mutableStateOf(kept != null) }
    var videoSize by remember(file) { mutableStateOf(kept?.videoSize ?: VideoSize.UNKNOWN) }
    val player = remember(file) { kept ?: newPlayer(ctx, file) }
    SideEffect { if (player.playWhenReady == paused) player.playWhenReady = !paused }
    var view by remember { mutableStateOf<TextureView?>(null) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() { ready = true; onReady(true) }
            override fun onVideoSizeChanged(size: VideoSize) { videoSize = size; view?.let { cropToFill(it, size) } }
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) { ready = false; onReady(false) }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            // Tandem going to the background (a game, Home): keep the player for a few seconds in case it comes
            // straight back. Anything else (another wallpaper) lets it go now.
            if (!owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) VideoKeeper.park(file, player) else player.release()
            onReady(false)
        }
    }
    val cover = animateFloatAsState(if (ready) 0f else 1f, tween(if (kept != null) 0 else 450), label = "video-in")
    if (surface) {
        val night = Palette.NightDeep
        Box(modifier.fillMaxSize()) {
            AndroidView(
                factory = { c -> SurfaceView(c).also { player.setVideoSurfaceView(it) } },
                modifier = Modifier.coverCrop(videoSize),
                onRelease = { player.clearVideoSurfaceView(it) },
            )
            // Until the first frame: the screen's night colour, fading away (a SurfaceView can't fade itself).
            Box(Modifier.fillMaxSize().drawBehind { val a = cover.value; if (a > 0.001f) drawRect(night, alpha = a) })
        }
    } else {
        AndroidView(
            factory = { c ->
                TextureView(c).apply {
                    isOpaque = false
                    surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        override fun onSurfaceTextureAvailable(s: SurfaceTexture, w: Int, h: Int) { cropToFill(this@apply, player.videoSize) }
                        override fun onSurfaceTextureSizeChanged(s: SurfaceTexture, w: Int, h: Int) { cropToFill(this@apply, player.videoSize) }
                        override fun onSurfaceTextureDestroyed(s: SurfaceTexture) = true
                        override fun onSurfaceTextureUpdated(s: SurfaceTexture) {}
                    }
                    player.setVideoTextureView(this)
                    view = this
                }
            },
            modifier = modifier.fillMaxSize().graphicsLayer { alpha = 1f - cover.value },
            onRelease = { player.clearVideoTextureView(it) },
        )
    }
}

@OptIn(UnstableApi::class)
private fun newPlayer(ctx: Context, file: File): ExoPlayer {
    // Video only: no audio renderer, so a wallpaper's sound track is never decoded (it was only muted before).
    val renderers = RenderersFactory { handler, videoListener, _, _, _ -> arrayOf<Renderer>(CappedVideoRenderer(ctx, handler, videoListener)) }
    // A local file needs only a few seconds read ahead; the default keeps up to 50 s of samples in memory.
    val buffer = androidx.media3.exoplayer.DefaultLoadControl.Builder()
        .setBufferDurationsMs(2_500, 5_000, 500, 1_000)
        .build()
    return ExoPlayer.Builder(ctx, renderers).setLoadControl(buffer).build().apply {
        setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
        repeatMode = Player.REPEAT_MODE_ALL
        volume = 0f
        playWhenReady = true
        prepare()
    }
}

/**
 * A video renderer that shows at most ~30 frames a second: a 60 fps wallpaper skips every other frame (released
 * unrendered), halving what the display has to compose. Slower videos are untouched.
 */
@OptIn(UnstableApi::class)
private class CappedVideoRenderer(ctx: Context, handler: Handler, listener: VideoRendererEventListener) :
    MediaCodecVideoRenderer(ctx, MediaCodecSelector.DEFAULT, 0L, handler, listener, 50) {
    private var lastUs = C.TIME_UNSET

    override fun processOutputBuffer(
        positionUs: Long, elapsedRealtimeUs: Long, codec: MediaCodecAdapter?, buffer: ByteBuffer?, bufferIndex: Int,
        bufferFlags: Int, sampleCount: Int, bufferPresentationTimeUs: Long, isDecodeOnlyBuffer: Boolean,
        isLastBuffer: Boolean, format: Format,
    ): Boolean {
        if (codec != null && !isDecodeOnlyBuffer && !isLastBuffer && lastUs != C.TIME_UNSET) {
            val gap = bufferPresentationTimeUs - lastUs
            if (gap in 0 until MIN_GAP_US) {
                skipOutputBuffer(codec, bufferIndex, bufferPresentationTimeUs)
                return true
            }
        }
        val done = super.processOutputBuffer(
            positionUs, elapsedRealtimeUs, codec, buffer, bufferIndex, bufferFlags, sampleCount, bufferPresentationTimeUs,
            isDecodeOnlyBuffer, isLastBuffer, format,
        )
        if (done && !isDecodeOnlyBuffer) lastUs = bufferPresentationTimeUs
        return done
    }

    override fun onPositionReset(positionUs: Long, joining: Boolean) {
        super.onPositionReset(positionUs, joining)
        lastUs = C.TIME_UNSET
    }

    companion object {
        /** Frames closer together than this (a little under 1/30 s) are skipped. */
        const val MIN_GAP_US = 30_000L
    }
}

/** Measures its content to cover the whole box at the video's aspect, centred; the overflow is off the screen. */
private fun Modifier.coverCrop(size: VideoSize): Modifier = layout { measurable, c ->
    val w = c.maxWidth
    val h = c.maxHeight
    val aspect = if (size.width > 0 && size.height > 0) size.width * size.pixelWidthHeightRatio / size.height else 0f
    var cw = w
    var ch = h
    if (aspect > 0f && w > 0 && h > 0) {
        if (aspect > w.toFloat() / h) cw = (h * aspect).roundToInt() else ch = (w / aspect).roundToInt()
    }
    val p = measurable.measure(Constraints.fixed(cw, ch))
    layout(w, h) { p.place((w - cw) / 2, (h - ch) / 2) }
}

/**
 * Players of videos that just left the screen because Tandem went to the background, paused and kept for
 * [KEEP_MS]. Coming back within that time reuses the player: no new decoder, no re-buffering, no fade on the main
 * thread's first frames. Later (a game that kept running), it is released, so a video never costs anything while
 * you play.
 */
private object VideoKeeper {
    private const val KEEP_MS = 8_000L
    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    private val parked = HashMap<String, ExoPlayer>()

    fun take(file: File): ExoPlayer? = parked.remove(file.path)?.also { main.removeCallbacksAndMessages(it) }

    fun park(file: File, player: ExoPlayer) {
        parked.remove(file.path)?.takeIf { it !== player }?.let { old -> main.removeCallbacksAndMessages(old); old.release() }
        player.playWhenReady = false
        parked[file.path] = player
        main.postAtTime({
            if (parked[file.path] === player) { parked.remove(file.path); player.release() }
        }, player, android.os.SystemClock.uptimeMillis() + KEEP_MS)
    }
}

/** Scales the texture so the video covers the whole view, cropping the overflow equally on both sides. */
private fun cropToFill(view: TextureView, size: VideoSize) {
    val vw = view.width.toFloat()
    val vh = view.height.toFloat()
    if (size.width == 0 || size.height == 0 || vw == 0f || vh == 0f) return
    val videoAspect = size.width * size.pixelWidthHeightRatio / size.height
    val viewAspect = vw / vh
    val sx: Float
    val sy: Float
    if (videoAspect > viewAspect) { sx = videoAspect / viewAspect; sy = 1f } else { sx = 1f; sy = viewAspect / videoAspect }
    view.setTransform(Matrix().apply { setScale(sx, sy, vw / 2f, vh / 2f) })
}

/*
 * Still pictures of wallpapers: a frame of a video (for the second screen showing the same video, so it is decoded
 * once) and a small, softened picture (behind Settings). Made on a background thread and kept in a small cache, so
 * opening Settings or showing the other screen never decodes anything on the main thread.
 */
object Stills {
    private val cache = android.util.LruCache<String, ImageBitmap>(8)

    fun cached(file: File, soft: Boolean): ImageBitmap? = cache.get(key(file, soft))

    private fun key(file: File, soft: Boolean) = file.path + if (soft) "#soft" else "#sharp"

    suspend fun load(file: File, soft: Boolean): ImageBitmap? {
        cache.get(key(file, soft))?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching {
                val src = if (file.extension.lowercase() in ThemePacks.videoExtensions) videoFrame(file, if (soft) 320 else 1280)
                else picture(file, if (soft) 320 else 1280)
                src?.let { if (soft) soften(it) else it }?.asImageBitmap()
            }.getOrNull()?.also { cache.put(key(file, soft), it) }
        }
    }

    private fun videoFrame(file: File, max: Int): Bitmap? {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(file.path)
            r.getScaledFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, max, max)
                ?: r.getScaledFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, max, max)
        } finally {
            runCatching { r.release() }
        }
    }

    private fun picture(file: File, max: Int): Bitmap? {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, o)
        if (o.outWidth <= 0 || o.outHeight <= 0) return null
        var sample = 1
        while (max(o.outWidth, o.outHeight) / (sample * 2) >= max) sample *= 2
        return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    /** A tiny, blurred copy: drawn stretched with bilinear filtering it reads as a soft, calm picture. */
    private fun soften(src: Bitmap): Bitmap {
        val long = 96f
        val scale = long / max(src.width, src.height)
        val w = max(2, (src.width * scale).roundToInt())
        val h = max(2, (src.height * scale).roundToInt())
        val small = Bitmap.createScaledBitmap(src, w, h, true)
        val px = IntArray(w * h)
        small.getPixels(px, 0, w, 0, 0, w, h)
        repeat(3) { boxBlur(px, w, h, 2) }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }

    private fun boxBlur(px: IntArray, w: Int, h: Int, r: Int) {
        val tmp = IntArray(px.size)
        fun pass(src: IntArray, dst: IntArray, len: Int, lines: Int, step: Int, lineStep: Int) {
            for (line in 0 until lines) {
                val base = line * lineStep
                for (i in 0 until len) {
                    var a = 0; var rr = 0; var g = 0; var b = 0; var n = 0
                    for (k in -r..r) {
                        val j = (i + k).coerceIn(0, len - 1)
                        val c = src[base + j * step]
                        a += c ushr 24; rr += (c shr 16) and 0xff; g += (c shr 8) and 0xff; b += c and 0xff; n++
                    }
                    dst[base + i * step] = ((a / n) shl 24) or ((rr / n) shl 16) or ((g / n) shl 8) or (b / n)
                }
            }
        }
        pass(px, tmp, w, h, 1, w)
        pass(tmp, px, h, w, w, 1)
    }
}

/**
 * A still of [file] filling its box: a frame of a video or the picture itself; [soft] gives the small blurred one.
 * Nothing shows until it is ready (it is usually made ahead, see [Stills]).
 */
@Composable
fun WallpaperStill(file: File, soft: Boolean, modifier: Modifier = Modifier, alpha: () -> Float = { 1f }, delayMs: Long = 0) {
    val bmp by produceState(Stills.cached(file, soft), file, soft) {
        if (value != null) return@produceState
        if (delayMs > 0) delay(delayMs)
        value = Stills.load(file, soft)
    }
    val b = bmp ?: return
    Image(
        b, null, modifier.fillMaxSize().graphicsLayer { this.alpha = alpha() },
        contentScale = ContentScale.Crop, filterQuality = if (soft) FilterQuality.Low else FilterQuality.Medium,
    )
}
