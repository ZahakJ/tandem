package io.github.zahakj.tandem.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import io.github.zahakj.tandem.Library
import io.github.zahakj.tandem.MainActivity
import io.github.zahakj.tandem.NetGate
import io.github.zahakj.tandem.PrivateOffline
import io.github.zahakj.tandem.R
import io.github.zahakj.tandem.Tx
import io.github.zahakj.tandem.audio.SoundPack
import io.github.zahakj.tandem.data.ExtrasStore
import io.github.zahakj.tandem.data.GalleryEntry
import io.github.zahakj.tandem.data.GalleryIndex
import io.github.zahakj.tandem.data.GalleryUrls
import io.github.zahakj.tandem.data.HttpError
import io.github.zahakj.tandem.data.LookDownloads
import io.github.zahakj.tandem.data.LookFile
import io.github.zahakj.tandem.data.LookFileException
import io.github.zahakj.tandem.data.LookManifest
import io.github.zahakj.tandem.data.LookPart
import io.github.zahakj.tandem.data.LookProblem
import io.github.zahakj.tandem.data.Net
import io.github.zahakj.tandem.data.SharedLook
import io.github.zahakj.tandem.data.Store
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Share my look and Get a look: the current look as one `.tandem` file ([export]), and a file, link or gallery look
 * unpacked, checked and shown as a preview card ([staged]) before it is applied ([install]). The previous look is
 * kept for [undo], and an automatic backup is made first. The file format is data/LookFile.kt.
 */
object LookShare {
    private const val TAG = "Tandem"
    private const val PREFS = "look_share"

    /** Where "Save" puts looks: ROMs/tandem/looks/<name>.tandem. */
    val looksDir: File get() = File(Library.romsDir, "tandem/looks")

    // ---- What the sharer typed ----

    /** The name, author and description for the next export, kept in app preferences (never in a backup). */
    data class Draft(val name: String = "", val author: String = "", val description: String = "")

    fun draft(ctx: Context): Draft = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).let {
        Draft(it.getString("name", "").orEmpty(), it.getString("author", "").orEmpty(), it.getString("description", "").orEmpty())
    }

    fun saveDraft(ctx: Context, d: Draft) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("name", d.name).putString("author", d.author).putString("description", d.description).apply()
    }

    // ---- Export ----

    /** A look written to a file in the cache, ready to save or share. */
    class Exported(val file: File, val manifest: LookManifest, val videoLeftOut: Boolean, val heroesLeftOut: Boolean)

    /** Heroes are dropped when everything together would be bigger than this; the file stays easy to share. */
    private const val ART_BUDGET = 18L shl 20

    /**
     * The look on screen now as a `.tandem` file: the theme (colours, shape, motion) as theme.json, wallpaper pictures
     * and the pack art in use re-encoded (which also drops their metadata, like a photo's location), a preview, and
     * look.json with the allow-listed settings ([SharedLook]). Video wallpapers stay on the handheld. Blocking.
     */
    fun export(ctx: Context, draft: Draft): Exported {
        val settings = Store.settings
        val look = settings.look
        val spec = Themes.withParts(Themes.byId(look.theme) ?: BuiltInThemes.PastelNight, look)
        val resolved = Themes.resolve(look)
        val work = File(ctx.cacheDir, "look-export").apply { deleteRecursively(); mkdirs() }
        val files = LinkedHashMap<String, File>()
        val parts = linkedSetOf(LookPart.Colours, LookPart.Layout, LookPart.Motion, LookPart.Sounds)
        var video = false

        // Wallpapers: a picture goes in (re-encoded); a video stays home and its screen shows the theme's backdrop.
        val wallpaper = JSONObject()
        var topSource: File? = null
        fun wall(slot: ScreenSlot): String {
            val key = wallpaperKey(slot, look, resolved)
            val name = if (slot == ScreenSlot.Top) "top" else "bottom"
            return when {
                key.startsWith("file:") -> {
                    val src = File(key.removePrefix("file:"))
                    if (isVideoKey(key)) { video = true; return spec.backdrop.id }
                    if (slot == ScreenSlot.Bottom && src == topSource && files.containsKey("wallpapers/top.webp")) {
                        wallpaper.put(name, "wallpapers/top.webp"); return "theme"
                    }
                    val out = File(work, "wallpapers/$name.webp")
                    if (!reencode(src, out, 1920, 1920, Bitmap.CompressFormat.WEBP_LOSSY, 85)) return spec.backdrop.id
                    files["wallpapers/$name.webp"] = out
                    wallpaper.put(name, "wallpapers/$name.webp")
                    if (slot == ScreenSlot.Top) topSource = src
                    parts += LookPart.Wallpaper
                    "theme"
                }
                key == "hero" -> "hero"
                key == spec.backdrop.id && wallpaper.optString(name).isEmpty() -> "theme"
                else -> key
            }
        }
        val wallTop = wall(ScreenSlot.Top)
        val wallBottom = wall(ScreenSlot.Bottom)

        // The art in use: the theme pack's own, or the pack chosen in Icons from.
        var heroesLeftOut = false
        ThemePacks.artSource?.let { (key, art) ->
            val p = ThemePacks.parts(key)
            val folders = art.systemFolders().filter { (n, _) -> SYSTEM.matches(n) }.distinctBy { it.first }
            if (p.icons) {
                for ((n, dir) in folders) art.picture(dir, "icon")?.let { add(it, work, "systems/$n/icon.png", 256, files) }
                for (kind in PackArt.COLLECTIONS) art.collection(kind)?.let { add(it, work, "collections/$kind/icon.png", 256, files) }
                if (files.keys.any { it.startsWith("systems/") }) parts += LookPart.Icons
                if (files.keys.any { it.startsWith("collections/") }) parts += LookPart.Collections
            }
            if (p.heroes) {
                val heroes = LinkedHashMap<String, File>()
                for ((n, dir) in folders) {
                    art.picture(dir, "hero")?.let { add(it, work, "systems/$n/hero.webp", 960, heroes) }
                    art.picture(dir, "logo")?.let { add(it, work, "systems/$n/logo.png", 640, heroes) }
                }
                for (kind in PackArt.COLLECTIONS) art.collection(kind, "hero")?.let { add(it, work, "collections/$kind/hero.webp", 960, heroes) }
                val total = (files.values + heroes.values).sumOf { it.length() }
                if (total <= ART_BUDGET) {
                    files.putAll(heroes)
                    if (heroes.isNotEmpty()) parts += LookPart.Heroes
                } else {
                    heroesLeftOut = true
                    heroes.values.forEach { it.delete() }
                }
            }
        }

        // Tandem's art sets travel as their ids and are drawn again on the other handheld.
        if (look.artSet != null) { parts += LookPart.Icons; parts += LookPart.Heroes }

        val name = draft.name.trim().ifEmpty { spec.name }.take(60)
        val theme = themeJson(spec, name, draft.author.trim(), draft.description.trim(), wallpaper)
        val preview = File(work, "preview.png")
        if (drawPreview(resolved, files["wallpapers/top.webp"], preview)) files["preview.png"] = preview

        val manifest = LookManifest(
            name = name, author = draft.author.trim().take(40), description = draft.description.trim().take(300),
            created = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()),
            app = io.github.zahakj.tandem.data.Backups.appVersion(ctx).take(20),
            parts = parts.map { it.id },
            look = SharedLook.of(
                look, settings, ExtrasStore.settings, wallTop, wallBottom,
                // Set ids only: pictures uploaded for a system are the user's own and stay on the handheld.
                systemSets = io.github.zahakj.tandem.data.ArtSets.perSystem(io.github.zahakj.tandem.data.IconGridStore.config.styles),
            ),
        )
        val out = File(work, PackImport.safeName(name) + "." + LookFile.EXTENSION)
        out.outputStream().use { LookFile.write(it, manifest, theme, files) }
        if (out.length() > LookFile.MAX_FILE) throw LookFileException(LookProblem.TooBig)
        return Exported(out, manifest, video, heroesLeftOut)
    }

    private val SYSTEM = Regex("[a-z0-9_]{1,48}")

    /** Re-encodes [src] into [work]/[path] at most [max] pixels on a side; a PNG for .png paths, else WebP. */
    private fun add(src: File, work: File, path: String, max: Int, into: MutableMap<String, File>) {
        // Art fetched from the web for a system (or scraped for a game) is someone else's: it stays on the handheld.
        if (LookFile.localOnly(src.path)) return
        val out = File(work, path)
        val png = path.endsWith(".png")
        if (reencode(src, out, max, max, if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.WEBP_LOSSY, if (png) 100 else 82)) into[path] = out
    }

    /** Decodes [src] (downsampled when big), fits it in [maxW]×[maxH] and writes it as [format]. False when it isn't a picture. */
    private fun reencode(src: File, out: File, maxW: Int, maxH: Int, format: Bitmap.CompressFormat, quality: Int): Boolean = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(src.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxW && bounds.outHeight / (sample * 2) >= maxH) sample *= 2
        val bmp = BitmapFactory.decodeFile(src.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return false
        val scale = minOf(1f, maxW.toFloat() / bmp.width, maxH.toFloat() / bmp.height)
        val fitted = if (scale < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt().coerceAtLeast(1), (bmp.height * scale).toInt().coerceAtLeast(1), true) else bmp
        out.parentFile?.mkdirs()
        out.outputStream().use { fitted.compress(format, quality, it) }
        if (fitted !== bmp) fitted.recycle()
        bmp.recycle()
        true
    }.getOrElse { Log.w(TAG, "Couldn't re-encode a picture for a shared look: ${it.javaClass.simpleName}"); false }

    private fun hex(c: Color): String = if (c.alpha >= 1f) "#%06X".format(c.toArgb() and 0xFFFFFF) else "#%08X".format(c.toArgb())

    /** The theme as a Tandem theme.json (docs/THEMES.md): what ThemePacks reads back. */
    private fun themeJson(t: ThemeSpec, name: String, author: String, description: String, wallpaper: JSONObject): String {
        val builtIn = BuiltInThemes.list.map { it.id }
        val colors = JSONObject()
            .put("background", hex(t.background)).put("background_deep", hex(t.backgroundDeep)).put("card", hex(t.card))
            .put("card_high", hex(t.cardHigh)).put("text", hex(t.text)).put("muted", hex(t.muted)).put("on_accent", hex(t.onAccent))
            .put("mint", hex(t.mint)).put("peach", hex(t.peach)).put("lavender", hex(t.lavender)).put("sky", hex(t.sky))
            .put("butter", hex(t.butter)).put("accent", hex(t.accent)).put("glow", hex(t.glow))
        val shape = JSONObject().put("corner", t.corner.toDouble()).put("focus_scale", t.focusScale.toDouble())
            .put("highlight", t.highlight.id).put("highlight_thickness", t.highlightThickness.toDouble())
        val o = JSONObject()
            .put("name", name).put("author", author).put("description", description).put("version", "1")
            .put("base", (t.baseId ?: t.id).takeIf { it in builtIn } ?: BuiltInThemes.PastelNight.id)
            .put("mode", if (t.dark) "dark" else "light")
            .put("colors", colors).put("shape", shape)
            .put("material", t.material.id).put("motion", t.animation.id).put("backdrop", t.backdrop.id).put("icon_style", t.iconStyle.id)
            .put("overlay", when (t.overlay) { ScreenOverlay.Scanlines -> "scanlines"; ScreenOverlay.DotMatrix -> "dot_matrix"; ScreenOverlay.None -> "none" })
            .put("icon_scale", t.iconScale.toDouble())
        t.heroLogoScale?.let { o.put("hero_logo_scale", it.toDouble()) }
        t.heroGradient?.let { o.put("hero_gradient", it) }
        if (wallpaper.length() > 0) o.put("wallpaper", wallpaper)
        return o.toString(2)
    }

    /** A 640×360 picture of the look for galleries: its wallpaper or colours, three cards, the accent on the first. */
    private fun drawPreview(t: ThemeSpec, wallpaper: File?, out: File): Boolean = runCatching {
        val w = 640
        val h = 360
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), t.background.toArgb(), t.backgroundDeep.toArgb(), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        paint.shader = null
        wallpaper?.let { BitmapFactory.decodeFile(it.path) }?.let { wp ->
            val scale = maxOf(w.toFloat() / wp.width, h.toFloat() / wp.height)
            val dw = wp.width * scale
            val dh = wp.height * scale
            c.drawBitmap(wp, null, RectF((w - dw) / 2, (h - dh) / 2, (w + dw) / 2, (h + dh) / 2), Paint(Paint.FILTER_BITMAP_FLAG))
            wp.recycle()
            c.drawColor(android.graphics.Color.argb(70, 0, 0, 0))
        }
        val corner = (t.corner * 1.1f).coerceIn(0f, 40f)
        val colours = listOf(t.mint, t.peach, t.lavender)
        for (i in 0..2) {
            val x = 88f + i * 166f
            val r = RectF(x, 110f, x + 130f, 240f)
            paint.color = t.card.toArgb()
            c.drawRoundRect(r, corner, corner, paint)
            paint.color = colours[i].copy(alpha = 0.85f).toArgb()
            c.drawCircle(r.centerX(), r.centerY() - 8f, 30f, paint)
            if (i == 0) {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 6f
                paint.color = t.accent.toArgb()
                c.drawRoundRect(RectF(r.left - 6f, r.top - 6f, r.right + 6f, r.bottom + 6f), corner + 6f, corner + 6f, paint)
                paint.style = Paint.Style.FILL
            }
        }
        for ((i, col) in t.accents.withIndex()) {
            paint.color = col.toArgb()
            c.drawCircle(w / 2f + (i - 2) * 30f, 298f, 9f, paint)
        }
        out.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
        true
    }.getOrDefault(false)

    /**
     * Copies an export to ROMs/tandem/looks/, never over another file. [forGitHub] names it `.tandem.zip`: GitHub
     * takes only known kinds of file as attachments, and a .tandem is a zip.
     */
    fun saveToLooks(e: Exported, forGitHub: Boolean = false): File {
        looksDir.mkdirs()
        val base = PackImport.safeName(e.manifest.name)
        val ext = if (forGitHub) "${LookFile.EXTENSION}.zip" else LookFile.EXTENSION
        var dest = File(looksDir, "$base.$ext")
        var n = 2
        while (dest.exists()) dest = File(looksDir, "$base ($n).$ext").also { n++ }
        e.file.copyTo(dest)
        return dest
    }

    fun saveTo(ctx: Context, e: Exported, uri: Uri) {
        val out = ctx.contentResolver.openOutputStream(uri) ?: throw IOException("can't write")
        out.use { o -> e.file.inputStream().use { it.copyTo(o) } }
    }

    // ---- Import ----

    /** Where a look being imported came from, for the preview card. */
    sealed interface Source {
        data object Picked : Source
        data class Web(val host: String) : Source
        data class Gallery(val entry: GalleryEntry) : Source
    }

    /** A look unpacked and checked, waiting for the user to say yes. */
    class Staged(val unpacked: LookFile.Unpacked, val source: Source) {
        val manifest: LookManifest get() = unpacked.manifest
        val name: String = manifest.name.ifBlank { (unpacked.theme["name"] as? JsonPrimitive)?.content.orEmpty().take(60) }
        /** The picture on the card: its top wallpaper, else its preview. */
        val picture: File? = unpacked.images.firstOrNull { it.startsWith("wallpapers/top.") }?.let(unpacked::file)
            ?: unpacked.images.firstOrNull { it.startsWith("preview.") }?.let(unpacked::file)
        val colors: List<Color> = (unpacked.theme["colors"] as? JsonObject)?.let { c ->
            listOf("background", "card", "accent", "text", "mint", "peach", "lavender", "sky", "butter")
                .mapNotNull { k -> (c[k] as? JsonPrimitive)?.content?.let(::parseColor) }
        }.orEmpty()
        val wallpapers: Int = unpacked.images.count { it.startsWith("wallpapers/") }
        val icons: Int = unpacked.images.count { it.startsWith("systems/") && it.contains("/icon.") }
        val heroes: Int = unpacked.images.count { it.startsWith("systems/") && it.contains("/hero.") }
        val collections: Int = unpacked.images.count { it.startsWith("collections/") }
    }

    /** The look waiting on its preview card; null when none is. */
    var staged by mutableStateOf<Staged?>(null)
        private set

    /** A link Tandem was opened with (a QR code, a web page), for Get a look to ask about. */
    var link by mutableStateOf<GalleryUrls.Link?>(null)

    /** True while a download or an import is under way. */
    var busy by mutableStateOf(false)

    private val stagingRoot get(): File? = appCache?.let { File(it, "look-import") }
    private var appCache: File? = null

    fun downloads(ctx: Context) = LookDownloads(File(ctx.cacheDir, "look-gallery"), Net.AGENT) { NetGate.check() }

    /** Unpacks and checks [input]. Blocking; throws [LookFileException] (or an IOException) with what was wrong. */
    private fun stage(ctx: Context, input: InputStream, source: Source): Staged {
        appCache = ctx.cacheDir
        val root = stagingRoot!!.apply { mkdirs() }
        // Only one look waits at a time: earlier ones are gone.
        root.listFiles().orEmpty().forEach { it.deleteRecursively() }
        val dir = File(root, System.nanoTime().toString())
        val unpacked = input.use { LookFile.read(it, dir, ::decodes) }
        return Staged(unpacked, source)
    }

    /** Android's decoder must agree it is a picture of the size it claims. */
    private fun decodes(f: File): Boolean {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, o)
        return o.outWidth in 1..LookFile.MAX_SIDE && o.outHeight in 1..LookFile.MAX_SIDE
    }

    fun show(s: Staged) { staged = s }

    /** From a picked file. Blocking. */
    fun fromUri(ctx: Context, uri: Uri): Staged {
        val input = ctx.contentResolver.openInputStream(uri) ?: throw LookFileException(LookProblem.Unreadable)
        return stage(ctx, input, Source.Picked)
    }

    /** From an https address. Blocking. */
    fun fromUrl(ctx: Context, url: String): Staged {
        val safe = GalleryUrls.safeUrl(url) ?: throw LookFileException(LookProblem.BadPath)
        val tmp = File(ctx.cacheDir, "look-download.tandem")
        try {
            downloads(ctx).file(safe, tmp)
            return stage(ctx, tmp.inputStream(), Source.Web(Uri.parse(safe).host.orEmpty()))
        } finally {
            tmp.delete()
        }
    }

    /** From the gallery: downloaded, its checksum and size checked against the index. Blocking. */
    fun fromGallery(ctx: Context, e: GalleryEntry): Staged {
        if (!e.supported) throw LookFileException(LookProblem.NewerVersion)
        val tmp = File(ctx.cacheDir, "look-download.tandem")
        try {
            downloads(ctx).file(GalleryUrls.raw(e.file), tmp)
            if (tmp.length() != e.size) throw LookFileException(LookProblem.Mismatch)
            LookFile.verify(tmp, e.sha256)
            return stage(ctx, tmp.inputStream(), Source.Gallery(e))
        } finally {
            tmp.delete()
        }
    }

    /** The gallery's looks, from the network when it changed (or [cached] only). Blocking. */
    fun galleryIndex(ctx: Context, cached: Boolean = false): List<GalleryEntry> {
        val d = downloads(ctx)
        val file = if (cached) File(ctx.cacheDir, "look-gallery/index.json") else when (val r = d.index()) {
            is LookDownloads.Result.Fresh -> r.file
            is LookDownloads.Result.Cached -> r.file
        }
        if (!file.isFile) return emptyList()
        return GalleryIndex.parse(file.readText())
    }

    /** Puts the look on the card away without using it. */
    fun discard() {
        staged?.unpacked?.dir?.deleteRecursively()
        staged = null
        Nav.focusTick++
    }

    // ---- Applying, and going back ----

    private class Before(
        val look: io.github.zahakj.tandem.data.Look, val bottomMode: String, val floating: Boolean, val intensity: Int,
        /** The art set each system the look gave one of its own had before (null: none). */
        val systemSets: Map<String, String?> = emptyMap(),
    )
    private var before: Before? = null

    /** True while the look before the last import can be put back. */
    var undoable by mutableStateOf(false)
        private set

    /**
     * Installs [s] as a theme pack in ROMs/tandem/themes/ and applies its look over the current settings (an
     * automatic backup first). Call from the main thread; the copying runs on [io].
     */
    suspend fun install(ctx: Context, s: Staged) {
        val app = ctx.applicationContext
        io.github.zahakj.tandem.data.Backups.auto(app, "Shared look") // tx-ok: the reason is saved with the backup
        val dest = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            ThemePacks.tandemDir.mkdirs()
            val base = PackImport.safeName(s.name.ifBlank { "Look" }) // tx-ok: a folder name
            var d = File(ThemePacks.tandemDir, base)
            var n = 2
            while (d.exists()) d = File(ThemePacks.tandemDir, "$base ($n)").also { n++ }
            s.unpacked.dir.copyRecursively(d, overwrite = false)
            (s.source as? Source.Gallery)?.let { File(d, GALLERY_FILE).writeText(it.entry.id) }
            s.unpacked.dir.deleteRecursively()
            d
        }
        staged = null
        ThemePacks.refresh(force = true)
        val id = "pack:${dest.name}"
        val look = s.manifest.look
        val now = Store.settings
        val extras = ExtrasStore.settings
        val sets = look.systemArtSets.orEmpty()
        val grid = io.github.zahakj.tandem.data.IconGridStore
        before = Before(now.look, now.bottomMode, extras.floating, extras.intensity, sets.keys.associateWith { grid.config.styles["s:$it"]?.artSet })
        undoable = true
        Store.updateSettings { st ->
            val l = look.applyTo(st.look).copy(theme = id, iconPack = "auto", packParts = st.look.packParts - id)
            st.copy(look = l, bottomMode = look.bottomMode ?: st.bottomMode)
        }
        for ((id, set) in sets) grid.setStyle("s:$id", grid.config.styles["s:$id"]?.copy(artSet = set) ?: io.github.zahakj.tandem.data.FolderStyle(artSet = set))
        ExtrasStore.update { it.copy(floating = look.floating ?: it.floating, intensity = look.intensity ?: it.intensity) }
        refresh(ctx)
        Undo.offer(Tx.s(R.string.share_applied, s.name)) { undo(ctx) }
    }

    /** Puts the look from before the last import back. The installed pack stays in Theme packs. */
    fun undo(ctx: Context) {
        val b = before ?: return
        Store.updateSettings { it.copy(look = b.look, bottomMode = b.bottomMode) }
        ExtrasStore.update { it.copy(floating = b.floating, intensity = b.intensity) }
        val grid = io.github.zahakj.tandem.data.IconGridStore
        for ((id, set) in b.systemSets) grid.setStyle("s:$id", (grid.config.styles["s:$id"] ?: io.github.zahakj.tandem.data.FolderStyle()).copy(artSet = set))
        before = null
        undoable = false
        refresh(ctx)
    }

    private fun refresh(ctx: Context) {
        (ctx as? MainActivity)?.applyScreenRoles()
        Music.refresh()
        Sounds.refreshFiles()
        Sounds.ensurePack(SoundPack.of(Store.settings.look.soundPack))
    }

    /** Written beside a look installed from the gallery: its id, so Show QR can point at its page. */
    private const val GALLERY_FILE = "gallery-id.txt"

    /** The gallery id of the theme in use, when it came from the gallery. Reads a small file. */
    fun galleryIdInUse(): String? {
        val pack = ThemePacks.byId(Store.settings.look.theme) ?: return null
        return runCatching { File(pack.dir, GALLERY_FILE).readText().trim() }.getOrNull()?.takeIf { Regex("[a-z0-9][a-z0-9-]{0,47}").matches(it) }
    }

    // ---- Links ----

    /** A `tandem://look?…` link Tandem was opened with: Get a look opens and asks about it. False for other intents. */
    fun openLink(intent: Intent?): Boolean {
        if (intent?.action != Intent.ACTION_VIEW) return false
        val l = GalleryUrls.parse(intent.dataString ?: return false) ?: return false
        // Handled once: coming back to Tandem later doesn't ask again.
        intent.data = null
        link = l
        if (Nav.route != Route.GetLook) Nav.goNow(Route.GetLook)
        return true
    }

    /** What went wrong, in words. */
    fun message(e: Throwable): String = when (e) {
        is LookFileException -> Tx.s(
            when (e.problem) {
                LookProblem.NotALook -> R.string.share_error_not_a_look
                LookProblem.NewerVersion -> R.string.share_error_newer
                LookProblem.TooBig, LookProblem.TooManyFiles -> R.string.share_error_too_big
                LookProblem.BadPath -> R.string.share_error_bad_file
                LookProblem.BadImage -> R.string.share_error_bad_picture
                LookProblem.ImageTooLarge -> R.string.share_error_big_picture
                LookProblem.BadJson, LookProblem.Unreadable -> R.string.share_error_unreadable
                LookProblem.Mismatch -> R.string.share_error_mismatch
            },
        )
        is PrivateOffline -> e.message
        is HttpError -> if (e.code == 404) Tx.s(R.string.share_error_not_found) else Tx.s(R.string.share_error_offline)
        is IOException -> Tx.s(R.string.share_error_offline)
        else -> Tx.s(R.string.share_error_unreadable)
    }
}

/** A link shown as a QR code on the other screen while Share my look's Show QR is on. */
object LookQr {
    var link by mutableStateOf<String?>(null)
    var title by mutableStateOf("")
}
