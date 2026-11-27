package io.github.zahakj.tandem.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.Dp
import io.github.zahakj.tandem.Library
import io.github.zahakj.tandem.data.ArtUpload
import io.github.zahakj.tandem.data.ExtrasStore
import io.github.zahakj.tandem.data.ImageSniff
import io.github.zahakj.tandem.data.Store
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/*
 * Pictures the user brings in for a tile (the artwork studio's Upload your own), animated ones included: GIF and
 * animated WebP play on the tile through Android's own decoder (AnimatedImageDrawable, which the render thread steps
 * along), decoded at the tile's size. They play only while the tile is composed (on screen), Tandem is in front, and
 * the setting (Settings › Home & layout › Grid shape › Animated icons) and Reduce motion allow; "While highlighted"
 * plays only the tile the highlight is on. Uploads are copied into ROMs/tandem/art/folders, where the studio has always
 * kept the pictures it was given: still ones scaled down and re-encoded, animations kept as they are once checked
 * (ArtUpload says what is accepted).
 */
object AnimatedArt {
    const val ALWAYS = "always"
    const val FOCUSED = "focused"
    const val OFF = "off"

    private val known = ConcurrentHashMap<String, Boolean>()

    /** Where uploaded pictures are kept (and found again by the studio's Your pictures). */
    val dir: File get() = File(Library.romsDir, "tandem/art/folders")

    /** Whether [file] moves: a GIF or an animated WebP, by its first bytes (looked at once per file and change). */
    fun isAnimated(file: File): Boolean = known.getOrPut(file.path + ":" + file.lastModified()) {
        runCatching { file.inputStream().use { s -> ArtUpload.kind(head(s))?.animated == true } }.getOrDefault(false)
    }

    private fun head(s: java.io.InputStream): ByteArray {
        val b = ByteArray(ArtUpload.HEAD)
        var n = 0
        while (n < b.size) { val r = s.read(b, n, b.size - n); if (r < 0) break; n += r }
        return if (n == b.size) b else b.copyOf(n)
    }

    /** What an upload came to: the file kept, or why not. */
    class Result(val file: File?, val problem: ArtUpload.Problem?)

    /**
     * Copies the picture at [uri] in for [tileKey]'s [slot] ("icon", "hero" or "logo"): checked, a still one scaled to
     * at most [ArtUpload.ICON_SIDE] (a hero to [ArtUpload.HERO_SIDE]) and re-encoded, an animation kept as it is once
     * Android has decoded it. Blocking; call it off the main thread.
     */
    fun import(ctx: Context, uri: Uri, tileKey: String, slot: String): Result = runCatching {
        val tmp = File(ctx.cacheDir, "art-upload.tmp")
        tmp.delete()
        // Copied first, capped: a file that turns out far too big is never read to its end.
        val cap = ArtUpload.MAX_STILL_BYTES + 1
        val copied = ctx.contentResolver.openInputStream(uri)?.use { input ->
            tmp.outputStream().use { out ->
                val buf = ByteArray(64 * 1024)
                var total = 0L
                while (total < cap) {
                    val r = input.read(buf)
                    if (r < 0) break
                    out.write(buf, 0, r)
                    total += r
                }
                total
            }
        } ?: return Result(null, ArtUpload.Problem.NotAPicture)
        val kind = tmp.inputStream().use { ArtUpload.kind(head(it)) }
        val size = if (kind == ArtUpload.Kind.Gif) tmp.inputStream().use { ArtUpload.gifSize(head(it)) } else ImageSniff.size(tmp)?.let { it.width to it.height }
        ArtUpload.check(kind, copied, size?.first ?: 0, size?.second ?: 0)?.let { tmp.delete(); return Result(null, it) }
        kind!!
        dir.mkdirs()
        val stamp = System.currentTimeMillis()
        val out = if (kind.animated) {
            // Android must be able to play it; then it is kept byte for byte.
            val d = ImageDecoder.decodeDrawable(ImageDecoder.createSource(tmp))
            if (d !is AnimatedImageDrawable) {
                // A GIF of one frame: a still picture after all.
                still(tmp, tileKey, slot, stamp)
            } else File(dir, ArtUpload.fileName(tileKey, slot, kind, stamp)).also { tmp.copyTo(it, overwrite = true) }
        } else still(tmp, tileKey, slot, stamp)
        tmp.delete()
        Result(out.takeIf { it.isFile && it.length() > 0 }, if (out.isFile) null else ArtUpload.Problem.NotAPicture)
    }.getOrElse { Result(null, ArtUpload.Problem.NotAPicture) }

    /** A still picture scaled to fit its slot and saved: PNG for an icon or logo (they may be see-through), JPEG for a hero. */
    private fun still(src: File, tileKey: String, slot: String, stamp: Long): File {
        val max = if (slot == "hero") ArtUpload.HERO_SIDE else ArtUpload.ICON_SIDE
        val bmp = ImageDecoder.decodeBitmap(ImageDecoder.createSource(src)) { d, info, _ ->
            val (w, h) = ArtUpload.fit(info.size.width, info.size.height, max)
            d.setTargetSize(w, h)
            d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        val hero = slot == "hero"
        val kind = if (hero) ArtUpload.Kind.Jpeg else ArtUpload.Kind.Png
        val out = File(dir, ArtUpload.fileName(tileKey, slot, kind, stamp))
        out.outputStream().use { bmp.compress(if (hero) Bitmap.CompressFormat.JPEG else Bitmap.CompressFormat.PNG, 90, it) }
        bmp.recycle()
        return out
    }
}

/**
 * An animated picture filling a tile (cropped to it), decoded at the tile's size. It plays only while the setting,
 * Reduce motion and the screen allow; otherwise its first frame stands still.
 */
@Composable
fun AnimatedTileArt(file: File, color: Color, size: Dp) {
    val px = tilePx(size)
    val drawable by produceState<Drawable?>(null, file.path, px) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                ImageDecoder.decodeDrawable(ImageDecoder.createSource(file)) { d, info, _ ->
                    // Decoded no bigger than the tile is shown (cropped to a square, so its shorter side fills it).
                    val short = minOf(info.size.width, info.size.height).coerceAtLeast(1)
                    val s = (px.toFloat() / short).coerceAtMost(1f)
                    d.setTargetSize((info.size.width * s).toInt().coerceAtLeast(1), (info.size.height * s).toInt().coerceAtLeast(1))
                }
            }.getOrNull()
        }
    }
    val d = drawable
    if (d == null) {
        Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(lerp(color, Color.White, 0.16f), lerp(color, Color.Black, 0.22f)))))
        return
    }
    val anim = d as? AnimatedImageDrawable
    val frame = remember(d) { mutableIntStateOf(0) }
    val resumed = rememberResumed()
    val policy = ExtrasStore.settings.animatedArt
    val highlighted = (IconGrid.spot?.art?.bare as? TileArt.Animated)?.file?.path == file.path
    val play = anim != null && resumed && policy != AnimatedArt.OFF && (policy != AnimatedArt.FOCUSED || highlighted) &&
        !Store.settings.reduceMotion && !Themes.still && !Ambient.settled
    DisposableEffect(anim) {
        anim?.callback = object : Drawable.Callback {
            override fun invalidateDrawable(who: Drawable) { frame.intValue++ }
            override fun scheduleDrawable(who: Drawable, what: Runnable, `when`: Long) {}
            override fun unscheduleDrawable(who: Drawable, what: Runnable) {}
        }
        onDispose { anim?.stop(); anim?.callback = null }
    }
    LaunchedEffect(anim, play) { if (play) anim?.start() else anim?.stop() }
    Canvas(Modifier.fillMaxSize()) {
        @Suppress("UNUSED_VARIABLE") val tick = frame.intValue
        val iw = d.intrinsicWidth.coerceAtLeast(1).toFloat()
        val ih = d.intrinsicHeight.coerceAtLeast(1).toFloat()
        val s = maxOf(this.size.width / iw, this.size.height / ih)
        val w = iw * s
        val h = ih * s
        val left = ((this.size.width - w) / 2f).toInt()
        val top = ((this.size.height - h) / 2f).toInt()
        d.setBounds(left, top, left + w.toInt(), top + h.toInt())
        drawIntoCanvas { d.draw(it.nativeCanvas) }
    }
}
