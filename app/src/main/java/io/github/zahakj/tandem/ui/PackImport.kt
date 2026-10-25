package io.github.zahakj.tandem.ui

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * Imports theme packs the user picked with the system file picker: a folder or a .zip, in Tandem's format or as a
 * Cocoon pack (theme.json with wallpapers, and smart folder or icon overlay asset packs). Everything is copied
 * into `ROMs/tandem/themes/<name>/`, so the pack keeps working without the app it came from. Nothing is downloaded.
 */
object PackImport {
    private const val TAG = "Tandem"
    /** Packs are pictures and short videos; anything bigger than this is not a theme pack. */
    private const val MAX_BYTES = 1_500L * 1024 * 1024
    private const val MAX_FILES = 20_000

    sealed interface Result {
        data class Done(val name: String, val dir: File) : Result
        data class Failed(val reason: String) : Result
    }

    /** Copies a picked folder. Call off the main thread. */
    fun fromTree(ctx: Context, tree: Uri): Result = into(ctx, guessName(ctx, tree, folder = true)) { dest ->
        val root = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        copyTree(ctx, tree, root, dest, Budget())
    }

    /** Unpacks a picked .zip. Call off the main thread. */
    fun fromZip(ctx: Context, zip: Uri): Result = into(ctx, guessName(ctx, zip, folder = false)) { dest ->
        val input = ctx.contentResolver.openInputStream(zip) ?: error("Couldn't open that file")
        input.use { unzip(it, dest) }
    }

    private class Budget(var bytes: Long = 0, var files: Int = 0) {
        fun add(n: Long) {
            bytes += n
            if (bytes > MAX_BYTES) error("That's too big for a theme pack")
        }
        fun file() { if (++files > MAX_FILES) error("That has too many files for a theme pack") }
    }

    private fun into(ctx: Context, name: String, copy: (File) -> Unit): Result {
        val staging = File(ctx.cacheDir, "pack-import-${System.nanoTime()}")
        return try {
            staging.mkdirs()
            copy(staging)
            val root = packRoot(staging) ?: return Result.Failed("No theme.json or icon folders in there")
            ensureThemeJson(root, name)
            val finalName = safeName(readName(root) ?: name)
            ThemePacks.tandemDir.mkdirs()
            var dest = File(ThemePacks.tandemDir, finalName)
            var n = 2
            while (dest.exists()) dest = File(ThemePacks.tandemDir, "$finalName ($n)").also { n++ }
            if (!root.renameTo(dest)) { root.copyRecursively(dest, overwrite = false) }
            Result.Done(readName(dest) ?: finalName, dest)
        } catch (e: Exception) {
            Log.w(TAG, "Theme pack import failed", e)
            Result.Failed(e.message ?: "Couldn't import that")
        } finally {
            staging.deleteRecursively()
        }
    }

    /**
     * The folder that is the pack: the one holding theme.json (often one level down in a zip), or one holding
     * Cocoon smart folders or icon overlays without a theme.
     */
    private fun packRoot(dir: File): File? {
        fun find(d: File, depth: Int): File? {
            if (File(d, "theme.json").isFile) return d
            if (depth <= 0) return null
            return d.listFiles().orEmpty().filter { it.isDirectory }.firstNotNullOfOrNull { find(it, depth - 1) }
        }
        find(dir, 3)?.let { return it }
        // Icons only: an asset pack (by_platform folders) with no theme around it.
        return if (ThemePacks.findDir(dir, "by_platform", 4) != null || ThemePacks.findDir(dir, "icon_overlays", 3) != null ||
            File(dir, "systems").isDirectory
        ) dir else null
    }

    /** An asset pack without a theme gets a small Tandem theme.json, so it shows up and can lend its icons. */
    private fun ensureThemeJson(root: File, fallbackName: String) {
        val f = File(root, "theme.json")
        if (f.isFile) return
        val meta = ThemePacks.assetMeta(root)
            ?: root.listFiles().orEmpty().filter { it.isDirectory }.firstNotNullOfOrNull { ThemePacks.assetMeta(it) }
        val o = JSONObject()
            .put("name", meta?.optString("name")?.ifBlank { null } ?: fallbackName)
            .put("author", meta?.optString("author") ?: "")
            .put("description", meta?.optString("description")?.ifBlank { null } ?: "Icons")
            .put("version", meta?.optString("version") ?: "")
            .put("base", "pastel-night")
        f.writeText(o.toString(2))
    }

    private fun readName(dir: File): String? =
        runCatching { JSONObject(File(dir, "theme.json").readText()).optString("name").takeIf { it.isNotBlank() } }.getOrNull()

    fun safeName(s: String): String =
        s.replace(Regex("[\\\\/:*?\"<>|\\x00-\\x1F]"), " ").replace(Regex("\\s+"), " ").trim().trim('.').take(60).ifBlank { "Theme pack" }

    private fun guessName(ctx: Context, uri: Uri, folder: Boolean): String {
        val display = runCatching {
            val doc = if (folder) DocumentsContract.buildDocumentUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri)) else uri
            ctx.contentResolver.query(doc, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull()
        return (display ?: "Theme pack").removeSuffix(".zip").removeSuffix(".ZIP")
    }

    private fun copyTree(ctx: Context, tree: Uri, doc: Uri, dest: File, budget: Budget) {
        dest.mkdirs()
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getDocumentId(doc))
        val cols = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE)
        val entries = mutableListOf<Triple<String, String, String>>()
        ctx.contentResolver.query(children, cols, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val name = c.getString(1)
                if (name != null) entries += Triple(c.getString(0), name, c.getString(2) ?: "")
            }
        }
        for ((id, name, mime) in entries) {
            val clean = safeEntry(name) ?: continue
            val child = DocumentsContract.buildDocumentUriUsingTree(tree, id)
            if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                copyTree(ctx, tree, child, File(dest, clean), budget)
            } else {
                budget.file()
                ctx.contentResolver.openInputStream(child)?.use { input -> write(input, File(dest, clean), budget) }
            }
        }
    }

    private fun unzip(input: InputStream, dest: File) {
        val budget = Budget()
        val base = dest.canonicalFile
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                val parts = e.name.replace('\\', '/').split('/').filter { it.isNotEmpty() }
                // Skip anything that tries to leave the folder, and macOS clutter.
                if (parts.any { it == ".." || safeEntry(it) == null } || parts.firstOrNull() == "__MACOSX") continue
                val out = File(base, parts.joinToString("/"))
                if (!out.canonicalPath.startsWith(base.path + File.separator)) continue
                if (e.isDirectory) { out.mkdirs(); continue }
                budget.file()
                out.parentFile?.mkdirs()
                write(zip, out, budget)
            }
        }
    }

    private fun write(input: InputStream, out: File, budget: Budget) {
        out.outputStream().use { o ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                budget.add(n.toLong())
                o.write(buf, 0, n)
            }
        }
    }

    /** A file name that is safe to create, or null for ones to skip (hidden files, path tricks). */
    private fun safeEntry(name: String): String? {
        if (name.isBlank() || name == "." || name == ".." || name.startsWith(".") || name.contains('/') || name.contains('\\')) return null
        return name
    }

    /** Deletes a pack Tandem owns (in ROMs/tandem/themes). Packs Cocoon installed are never touched. */
    fun remove(pack: ThemePack): Boolean {
        if (!pack.removable) return false
        val dir = pack.dir.canonicalFile
        if (dir.parentFile?.canonicalPath != ThemePacks.tandemDir.canonicalPath) return false
        return dir.deleteRecursively()
    }
}
