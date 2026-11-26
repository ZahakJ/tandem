package io.github.zahakj.tandem.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import io.github.zahakj.tandem.Tx
import io.github.zahakj.tandem.data.ArtSlot
import io.github.zahakj.tandem.data.FolderStyle
import io.github.zahakj.tandem.data.IconGridStore
import io.github.zahakj.tandem.data.Store
import io.github.zahakj.tandem.data.SystemArt
import io.github.zahakj.tandem.data.SystemArtFetch
import io.github.zahakj.tandem.data.WebKind
import io.github.zahakj.tandem.data.WebOption
import io.github.zahakj.tandem.data.styleOf
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min

/*
 * Real system art (data/SystemArt.kt) made into the pictures a system wears, drawn on the handheld:
 *
 * - an icon: the photo, illustration or logo fitted, never cut, on a backdrop in the system's colour; a photo shot on
 *   white (most of Wikimedia's) loses its white surround first, so the console sits on the colour;
 * - a hero: a backdrop (fanart, a background) filling 16:9, else the console on a deep wash of its colour, kept inside
 *   the middle of the picture so the bottom screen (1240×1080, cut from the middle) shows it whole too;
 * - a title: the logo itself, when it is see-through;
 * - for a hero made from a console (not a backdrop), the console alone too ([subjectBehind]), so the screens that show
 *   the hero with a logo and a name over it (the other screen's Spotlight, the grid's hero banner) compose them round
 *   the device instead of across it.
 *
 * Made once into cacheDir/web-art (made again when missing, when the picture or the system's colour changes) on one
 * background thread, and handed to the rest of Tandem as files, like the art sets (SetArt.kt).
 */
object WebArt {
    /** Raised whenever the making changes, so old pictures are never shown again. */
    private const val VERSION = 1
    const val ICON_PX = 384
    const val HERO_W = 1920
    const val HERO_H = 1080
    /** The long side of a hero's cut-out at most ([subjectBehind]). */
    const val SUBJECT_PX = 900

    /** Bumped (on the main thread, a little after a batch) when new pictures are ready: screens look again. */
    var revision by mutableIntStateOf(0)
        private set

    private val ready = ConcurrentHashMap<String, Boolean>()
    private val pending: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "web-art").apply { isDaemon = true; priority = Thread.NORM_PRIORITY - 1 } }
    private val main by lazy { Handler(Looper.getMainLooper()) }
    private val bump = Runnable { revision++ }

    /** The pictures of a system that can show in [slot] (a title only takes see-through ones). */
    fun available(platformId: String, slot: ArtSlot): Map<WebOption, File> {
        val all = SystemArtFetch.files(platformId)
        return if (slot == ArtSlot.Logo) all.filter { (_, f) -> f.extension.lowercase() == "png" } else all
    }

    /**
     * The web picture [platformId]'s [slot] shows now under [style] (its saved style when null), or null for none. Reads
     * the disk only when the system or every system has picked web art.
     */
    fun choice(platformId: String, style: FolderStyle?, slot: ArtSlot): WebOption? {
        val st = style ?: IconGridStore.config.styleOf("s:$platformId")
        val mine = SystemArt.mine(st, slot)
        val global = Store.settings.look.systemArt
        if (mine == null && global == null) return null
        // Read so a screen asking before the pictures arrive looks again once they do.
        SystemArtFetch.revision
        return SystemArt.choose(slot, mine, st.artSet, global, available(platformId, slot).keys)
    }

    /** The picture [option] makes for [slot] of [platformId], when it is made already; else null, and it is made. */
    fun file(platformId: String, option: WebOption, slot: ArtSlot): File? {
        val src = available(platformId, slot)[option] ?: return null
        if (slot == ArtSlot.Logo) return src
        val ctx = runCatching { Tx.context }.getOrNull() ?: return null
        val color = SystemColors.of(platformId)
        val tag = listOf(VERSION, src.length(), src.lastModified(), color.toArgb()).hashCode().toUInt().toString(36)
        val ext = if (slot == ArtSlot.Hero) "webp" else "png"
        val out = File(ctx.cacheDir, "web-art/${SystemArt.folderName(platformId)}/${option.id}-${slot.name.lowercase()}-$tag.$ext")
        if (ready[out.path] == true) return out
        if (out.isFile) { ready[out.path] = true; return out }
        enqueue(out) { render(src, option.kind, slot, color, out) }
        revision
        return null
    }

    /** [platformId]'s picture for [slot] under [style], or null when it shows no web art (or it is still being made). */
    fun pictureFor(platformId: String, style: FolderStyle?, slot: ArtSlot): File? =
        choice(platformId, style, slot)?.let { file(platformId, it, slot) }

    /**
     * When [hero] is [platformId]'s web hero made from a cut-out picture (a console photo, a controller, an
     * illustration: not a backdrop), that picture alone, trimmed, with its soft shadow and room round it for the shadow,
     * so a screen can place it beside the logo and the name instead of drawing them over it (Spotlight.kt). Null for any
     * other hero, and while it is being made.
     */
    fun subjectBehind(platformId: String?, style: FolderStyle?, hero: File?): File? {
        if (platformId == null || hero == null) return null
        val option = choice(platformId, style, ArtSlot.Hero) ?: return null
        if (option.kind == WebKind.Fanart || option.kind == WebKind.Background) return null
        if (file(platformId, option, ArtSlot.Hero) != hero) return null
        val src = available(platformId, ArtSlot.Hero)[option] ?: return null
        val ctx = runCatching { Tx.context }.getOrNull() ?: return null
        val tag = listOf(VERSION, src.length(), src.lastModified()).hashCode().toUInt().toString(36)
        val out = File(ctx.cacheDir, "web-art/${SystemArt.folderName(platformId)}/${option.id}-subject-$tag.png")
        if (ready[out.path] == true) return out
        if (out.isFile) { ready[out.path] = true; return out }
        enqueue(out) { renderSubject(src, option.kind, out) }
        revision
        return null
    }

    private fun enqueue(out: File, make: () -> Unit) {
        if (!pending.add(out.path)) return
        worker.execute {
            try {
                if (!out.isFile) runCatching { make() }
                if (out.isFile) {
                    ready[out.path] = true
                    // Older makings of the same picture go.
                    val stem = out.name.substringBeforeLast('-')
                    out.parentFile?.listFiles { f -> f.name.startsWith("$stem-") && f.name != out.name }?.forEach { it.delete() }
                }
            } finally {
                pending.remove(out.path)
                main.removeCallbacks(bump)
                main.postDelayed(bump, 250)
            }
        }
    }

    private fun decode(src: File, maxSide: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(src.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val bmp = BitmapFactory.decodeFile(src.path, BitmapFactory.Options().apply { inSampleSize = sample; inMutable = true }) ?: return null
        return if (bmp.config == Bitmap.Config.ARGB_8888 && bmp.isMutable) bmp else bmp.copy(Bitmap.Config.ARGB_8888, true).also { if (it !== bmp) bmp.recycle() }
    }

    private fun render(src: File, kind: WebKind, slot: ArtSlot, color: Color, out: File) {
        val (w, h) = if (slot == ArtSlot.Hero) HERO_W to HERO_H else ICON_PX to ICON_PX
        val art = decode(src, if (slot == ArtSlot.Hero) 1920 else 768) ?: return
        val canvas = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(canvas)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val wide = kind == WebKind.Fanart || kind == WebKind.Background
        if (slot == ArtSlot.Hero && wide && art.width >= art.height * 1.45f) {
            // A backdrop: fills the hero, cut evenly on the long side.
            c.drawBitmap(art, cover(art.width, art.height, w, h), Rect(0, 0, w, h), paint)
        } else {
            backdrop(c, w, h, color, slot)
            val subject = if (kind == WebKind.Photo || kind == WebKind.Controller) cutWhite(art) else art
            val opaque = !hasClearEdge(subject)
            // The box the subject fits in: most of an icon; the middle of a hero, clear of the bottom screen's cut.
            val box = if (slot == ArtSlot.Hero) {
                val bw = if (kind == WebKind.Banner) 1180f else 1040f
                val bh = if (kind == WebKind.Banner) 420f else 760f
                RectF((w - bw) / 2f, (h - bh) / 2f, (w + bw) / 2f, (h + bh) / 2f)
            } else {
                val share = when (kind) { WebKind.Logo, WebKind.Icon -> 0.74f; else -> 0.84f }
                val side = w * share
                RectF((w - side) / 2f, (h - side) / 2f, (w + side) / 2f, (h + side) / 2f)
            }
            val dst = fit(subject.width, subject.height, box)
            if (opaque) {
                // A photo with a background of its own: a rounded card with a soft shadow, never cut.
                val r = min(dst.width(), dst.height()) * 0.06f
                val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = android.graphics.Color.BLACK; setShadowLayer(w * 0.03f, 0f, w * 0.012f, 0x66000000) }
                c.drawRoundRect(dst, r, r, shadow)
                c.save()
                c.clipPath(android.graphics.Path().apply { addRoundRect(dst, r, r, android.graphics.Path.Direction.CW) })
                c.drawBitmap(subject, null, dst, paint)
                c.restore()
            } else {
                // A soft shadow in the subject's own shape, a little below it.
                val alpha = subject.extractAlpha()
                val shadow = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                    this.color = 0x55000000
                    maskFilter = android.graphics.BlurMaskFilter(w * 0.022f, android.graphics.BlurMaskFilter.Blur.NORMAL)
                }
                c.drawBitmap(alpha, null, RectF(dst).apply { offset(0f, w * 0.012f) }, shadow)
                alpha.recycle()
                c.drawBitmap(subject, null, dst, paint)
            }
            if (subject !== art) subject.recycle()
        }
        art.recycle()
        out.parentFile?.mkdirs()
        val tmp = File(out.path + ".part")
        val ok = runCatching {
            tmp.outputStream().use { o ->
                if (slot == ArtSlot.Hero) canvas.compress(Bitmap.CompressFormat.WEBP_LOSSY, 86, o) else canvas.compress(Bitmap.CompressFormat.PNG, 100, o)
            }
        }.getOrDefault(false)
        canvas.recycle()
        if (ok) tmp.renameTo(out) else tmp.delete()
    }

    /** The cut-out alone ([subjectBehind]): trimmed, at most [SUBJECT_PX] a side, its shadow below it. */
    private fun renderSubject(src: File, kind: WebKind, out: File) {
        val art = decode(src, 1200) ?: return
        val cut = if (kind == WebKind.Photo || kind == WebKind.Controller) cutWhite(art) else art
        val opaque = !hasClearEdge(cut)
        val box = if (opaque) Rect(0, 0, cut.width, cut.height) else opaqueBounds(cut) ?: Rect(0, 0, cut.width, cut.height)
        val scale = min(1f, SUBJECT_PX.toFloat() / max(box.width(), box.height()))
        val sw = box.width() * scale
        val sh = box.height() * scale
        val margin = max(sw, sh) * 0.08f
        val canvas = Bitmap.createBitmap((sw + margin * 2).toInt(), (sh + margin * 2).toInt(), Bitmap.Config.ARGB_8888)
        val c = Canvas(canvas)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        // A little above the middle, so the shadow below has its room.
        val dst = RectF(margin, margin * 0.7f, margin + sw, margin * 0.7f + sh)
        val drop = max(sw, sh) * 0.022f
        if (opaque) {
            val r = min(sw, sh) * 0.06f
            val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.BLACK; setShadowLayer(margin * 0.45f, 0f, drop, 0x66000000) }
            c.drawRoundRect(dst, r, r, shadow)
            c.save()
            c.clipPath(android.graphics.Path().apply { addRoundRect(dst, r, r, android.graphics.Path.Direction.CW) })
            c.drawBitmap(cut, box, dst, paint)
            c.restore()
        } else {
            val alpha = cut.extractAlpha()
            val shadow = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                color = 0x66000000
                maskFilter = android.graphics.BlurMaskFilter(margin * 0.4f, android.graphics.BlurMaskFilter.Blur.NORMAL)
            }
            c.drawBitmap(alpha, box, RectF(dst).apply { offset(0f, drop) }, shadow)
            alpha.recycle()
            c.drawBitmap(cut, box, dst, paint)
        }
        if (cut !== art) cut.recycle()
        art.recycle()
        out.parentFile?.mkdirs()
        val tmp = File(out.path + ".part")
        val ok = runCatching { tmp.outputStream().use { o -> canvas.compress(Bitmap.CompressFormat.PNG, 100, o) } }.getOrDefault(false)
        canvas.recycle()
        if (ok) tmp.renameTo(out) else tmp.delete()
    }

    /** The smallest box holding every pixel of [b] that isn't (nearly) see-through, or null for none. */
    private fun opaqueBounds(b: Bitmap): Rect? {
        val w = b.width
        val h = b.height
        val px = IntArray(w * h)
        b.getPixels(px, 0, w, 0, 0, w, h)
        var l = w; var t = h; var r = -1; var btm = -1
        for (y in 0 until h) for (x in 0 until w) {
            if (px[y * w + x] ushr 24 > 16) {
                if (x < l) l = x
                if (x > r) r = x
                if (y < t) t = y
                if (y > btm) btm = y
            }
        }
        return if (r < l || btm < t) null else Rect(l, t, r + 1, btm + 1)
    }

    /** The system's colour as a backdrop: lighter at the top, deeper at the bottom, a soft glow behind the middle. */
    private fun backdrop(c: Canvas, w: Int, h: Int, color: Color, slot: ArtSlot) {
        val top = lerp(color, Color.White, if (slot == ArtSlot.Hero) 0.05f else 0.18f)
        val bottom = lerp(color, Color.Black, if (slot == ArtSlot.Hero) 0.62f else 0.32f)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), Paint().apply { shader = LinearGradient(0f, 0f, 0f, h.toFloat(), top.toArgb(), bottom.toArgb(), Shader.TileMode.CLAMP) })
        val glow = lerp(color, Color.White, 0.45f).copy(alpha = 0.42f)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), Paint().apply {
            shader = RadialGradient(w / 2f, h * 0.46f, max(w, h) * 0.5f, glow.toArgb(), glow.copy(alpha = 0f).toArgb(), Shader.TileMode.CLAMP)
        })
    }

    /** The part of a [sw]×[sh] picture that fills [w]×[h], cut evenly on the long side. */
    private fun cover(sw: Int, sh: Int, w: Int, h: Int): Rect {
        val scale = max(w.toFloat() / sw, h.toFloat() / sh)
        val cw = (w / scale).toInt().coerceAtMost(sw)
        val ch = (h / scale).toInt().coerceAtMost(sh)
        val x = (sw - cw) / 2
        val y = (sh - ch) / 2
        return Rect(x, y, x + cw, y + ch)
    }

    /** A [sw]×[sh] picture fitted whole in [box], centred. */
    private fun fit(sw: Int, sh: Int, box: RectF): RectF {
        val scale = min(box.width() / sw, box.height() / sh)
        val dw = sw * scale
        val dh = sh * scale
        return RectF(box.centerX() - dw / 2f, box.centerY() - dh / 2f, box.centerX() + dw / 2f, box.centerY() + dh / 2f)
    }

    /** True when the picture's edges are mostly see-through (it floats on the backdrop). */
    private fun hasClearEdge(b: Bitmap): Boolean {
        var clear = 0
        var all = 0
        val stepX = max(1, b.width / 32)
        val stepY = max(1, b.height / 32)
        for (x in 0 until b.width step stepX) { all += 2; if (b.getPixel(x, 0) ushr 24 < 40) clear++; if (b.getPixel(x, b.height - 1) ushr 24 < 40) clear++ }
        for (y in 0 until b.height step stepY) { all += 2; if (b.getPixel(0, y) ushr 24 < 40) clear++; if (b.getPixel(b.width - 1, y) ushr 24 < 40) clear++ }
        return all > 0 && clear > all * 0.6f
    }

    /**
     * A photo shot on white without it: near-white pixels joined to the edges become see-through (a flood from the
     * border, so white parts of the console itself stay). The same picture when its edges aren't white.
     */
    private fun cutWhite(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        fun whiteish(p: Int): Boolean {
            if (p ushr 24 < 40) return true
            val r = (p shr 16) and 0xff; val g = (p shr 8) and 0xff; val b = p and 0xff
            return r > 232 && g > 232 && b > 232 && max(r, max(g, b)) - min(r, min(g, b)) < 14
        }
        var edgeWhite = 0
        var edges = 0
        for (x in 0 until w) { edges += 2; if (whiteish(px[x])) edgeWhite++; if (whiteish(px[(h - 1) * w + x])) edgeWhite++ }
        for (y in 0 until h) { edges += 2; if (whiteish(px[y * w])) edgeWhite++; if (whiteish(px[y * w + w - 1])) edgeWhite++ }
        if (edgeWhite < edges * 0.85f) return src
        val seen = BooleanArray(w * h)
        val queue = IntArray(w * h)
        var head = 0
        var tail = 0
        fun push(i: Int) { if (!seen[i] && whiteish(px[i])) { seen[i] = true; queue[tail++] = i } }
        for (x in 0 until w) { push(x); push((h - 1) * w + x) }
        for (y in 0 until h) { push(y * w); push(y * w + w - 1) }
        while (head < tail) {
            val i = queue[head++]
            val x = i % w
            val y = i / w
            if (x > 0) push(i - 1)
            if (x < w - 1) push(i + 1)
            if (y > 0) push(i - w)
            if (y < h - 1) push(i + w)
        }
        for (i in px.indices) if (seen[i]) px[i] = 0
        // A soft edge: pixels beside the cut-out are half see-through.
        for (y in 1 until h - 1) for (x in 1 until w - 1) {
            val i = y * w + x
            if (seen[i]) continue
            if (seen[i - 1] || seen[i + 1] || seen[i - w] || seen[i + w]) px[i] = (px[i] and 0x00ffffff) or (0x90 shl 24)
        }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(px, 0, w, 0, 0, w, h)
        return out
    }

}
