package io.github.zahakj.tandem.ui

import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import io.github.zahakj.tandem.Backdrop
import io.github.zahakj.tandem.Game
import io.github.zahakj.tandem.Libraries
import io.github.zahakj.tandem.data.Store
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

/*
 * Which picture fills a screen behind a game, and where to crop it.
 *
 * Every game's art covers the whole screen, whatever it has: 16:9 key art, a 3:1 banner, title screens, gameplay
 * shots or only a box. Each candidate is scored by its kind (key art beats a title screen, which beats a gameplay
 * shot, which beats the box), by how much of it survives the crop to the screen's shape, and by how far it has to be
 * enlarged. The winner is cropped on its busiest stretch, so a banner with its characters on one side shows them
 * rather than the plain side. Everything is read from file headers and a ~96 px thumbnail, off the main thread, and
 * remembered, so passing over a game a second time finds its answer at once.
 */
object ArtPick {
    /** The picture to show and how to crop it to the screen. */
    class Choice(val file: File, val kind: Backdrop.Kind, val align: Alignment)

    private class Size(val w: Int, val h: Int) { val aspect get() = w.toFloat() / h }

    private val sizes = ConcurrentHashMap<String, Size>()
    private val aligns = ConcurrentHashMap<String, Alignment>()
    private val choices = ConcurrentHashMap<String, Choice>()

    private fun fileKey(f: File) = f.path + ":" + f.lastModified()

    /** The screen's shape and size, rounded so nearby sizes share their answers. */
    private fun roomKey(w: Int, h: Int) = "${(w / 32) * 32}x${(h / 32) * 32}"

    private fun choiceKey(cands: List<Backdrop>, w: Int, h: Int) =
        roomKey(w, h) + "|" + cands.joinToString("|") { it.file.path }

    /** The answer for these pictures at this size, if it was worked out before (no file access). */
    fun cached(cands: List<Backdrop>, w: Int, h: Int): Choice? = if (cands.isEmpty()) null else choices[choiceKey(cands, w, h)]

    /** Works out the best picture for a [w] x [h] px screen. Reads files: call it off the main thread. */
    fun choose(cands: List<Backdrop>, w: Int, h: Int): Choice? {
        if (cands.isEmpty() || w <= 0 || h <= 0) return null
        val key = choiceKey(cands, w, h)
        choices[key]?.let { return it }
        val room = w.toFloat() / h
        var best: Backdrop? = null
        var bestScore = -1f
        for (c in cands) {
            val s = size(c.file) ?: continue
            val keep = minOf(s.aspect, room) / maxOf(s.aspect, room)
            val enlarge = maxOf(w.toFloat() / s.w, h.toFloat() / s.h)
            val sharp = minOf(1f, 1f / enlarge).pow(0.4f)
            val score = c.kind.weight * keep.pow(0.35f) * sharp
            if (score > bestScore) { bestScore = score; best = c }
        }
        val pick = best ?: return null
        val choice = Choice(pick.file, pick.kind, cropAlignment(pick.file, room))
        choices[key] = choice
        return choice
    }

    private fun size(f: File): Size? {
        val k = fileKey(f)
        sizes[k]?.let { return it }
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching { BitmapFactory.decodeFile(f.path, o) }
        if (o.outWidth <= 0 || o.outHeight <= 0) return null
        return Size(o.outWidth, o.outHeight).also { sizes[k] = it }
    }

    /** Width over height of the picture at [f], from its header (remembered); null if it can't be read. */
    fun aspect(f: File): Float? = size(f)?.aspect

    /** The aspect if it was read before (no file access). */
    fun cachedAspect(f: File): Float? = sizes[fileKey(f)]?.aspect

    /**
     * Where to crop [f] to the shape [aspect] (width over height): on its busiest stretch, gently pulled towards the
     * middle. Wide banners often keep their characters to one side over a plain colour; a centred crop would
     * show mostly the plain part. Reads a thumbnail of about 96 pixels.
     */
    fun cropAlignment(f: File, aspect: Float): Alignment {
        val k = fileKey(f) + "@" + (aspect * 20).roundToInt()
        aligns[k]?.let { return it }
        val a = runCatching { computeAlignment(f.path, aspect) }.getOrDefault(Alignment.Center)
        aligns[k] = a
        return a
    }

    private fun computeAlignment(path: String, aspect: Float): Alignment {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return Alignment.Center
        val imageAspect = bounds.outWidth.toFloat() / bounds.outHeight
        // Already the screen's shape (give or take): nothing to choose.
        if (abs(imageAspect / aspect - 1f) < 0.04f) return Alignment.Center
        val opts = BitmapFactory.Options().apply { inSampleSize = maxOf(1, maxOf(bounds.outWidth, bounds.outHeight) / 96) }
        val b = BitmapFactory.decodeFile(path, opts) ?: return Alignment.Center
        val w = b.width; val h = b.height
        val px = IntArray(w * h)
        b.getPixels(px, 0, w, 0, 0, w, h)
        b.recycle()
        if (w < 3 || h < 3) return Alignment.Center
        val lum = FloatArray(px.size) { i -> val c = px[i]; ((c shr 16 and 0xff) * 0.3f + (c shr 8 and 0xff) * 0.59f + (c and 0xff) * 0.11f) }
        // Detail: how much each pixel differs from its right and lower neighbours, summed per column and per row.
        val cols = FloatArray(w)
        val rows = FloatArray(h)
        for (y in 0 until h - 1) for (x in 0 until w - 1) {
            val i = y * w + x
            val d = abs(lum[i] - lum[i + 1]) + abs(lum[i] - lum[i + w])
            cols[x] += d; rows[y] += d
        }
        return if (imageAspect > aspect) BiasAlignment(busiest(cols, aspect / imageAspect), 0f)
        else BiasAlignment(0f, busiest(rows, imageAspect / aspect))
    }

    /** The bias (-1..1) of the window [frac] of [energy]'s length long holding the most detail, preferring the middle. */
    private fun busiest(energy: FloatArray, frac: Float): Float {
        val n = energy.size
        val k = (n * frac).roundToInt().coerceIn(1, n)
        if (k >= n) return 0f
        val prefix = FloatArray(n + 1)
        for (i in 0 until n) prefix[i + 1] = prefix[i] + energy[i]
        var best = 0f
        var bestScore = -1f
        for (start in 0..n - k) {
            val bias = start.toFloat() / (n - k) * 2f - 1f
            val score = (prefix[start + k] - prefix[start]) * (1f - 0.2f * abs(bias))
            if (score > bestScore) { bestScore = score; best = bias }
        }
        return best
    }

    @Volatile private var warmed: String? = null

    /**
     * Works out every game's choice for a [w] x [h] screen in the background, once per size, so moving onto a game
     * for the first time finds its picture ready instead of waiting a beat for it.
     */
    suspend fun warmAll(w: Int, h: Int) {
        val room = roomKey(w, h)
        if (warmed == room) return
        warmed = room
        withContext(Dispatchers.IO) {
            for (g in Libraries.games) {
                val lib = Libraries.of(g) ?: continue
                runCatching { choose(lib.backdrops(g), w, h) }
            }
        }
    }
}

/** The pictures that could fill the screen behind [game], recomputed when its details change. */
@Composable
fun rememberBackdrops(game: Game): List<Backdrop> {
    val meta = Store.meta
    return remember(game.key, meta, Libraries.all) { Libraries.of(game)?.backdrops(game).orEmpty() }
}

/**
 * The picture to fill a [w] x [h] px screen behind [game]: known at once when it was worked out before, otherwise
 * null for the moment it takes to read the files.
 */
@Composable
fun rememberArtChoice(cands: List<Backdrop>, w: Int, h: Int): ArtPick.Choice? {
    // Keyed state, not produceState: a new game never shows the last one's answer for a frame.
    val state = remember(cands, w, h) { mutableStateOf(ArtPick.cached(cands, w, h)) }
    LaunchedEffect(state) {
        if (state.value == null) state.value = withContext(Dispatchers.IO) { ArtPick.choose(cands, w, h) }
    }
    return state.value
}
