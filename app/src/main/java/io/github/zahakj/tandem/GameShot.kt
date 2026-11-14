package io.github.zahakj.tandem

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import androidx.core.content.FileProvider
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * A screenshot of the game's screen from its companion: saved to `ROMs/screenshots/` (Tandem's Gallery shows it) and
 * put on the clipboard, so it pastes straight into a chat. The screen is read through Shizuku (`screencap`), which
 * sees any app's window; the clipboard gets a copy shared through Tandem's file provider, readable by the app it is
 * pasted into.
 */
object GameShot {
    private const val DIR = "ROMs/screenshots"
    private val stamp = DateTimeFormatter.ofPattern("yyyy-MM-dd HH.mm.ss")
    private val hwc = Regex("""Display (\d+) \(HWC display (\d+)\)""")

    sealed class Result {
        data class Saved(val file: File) : Result()
        data class Failed(val why: String) : Result()
    }

    /** The physical display ids (`screencap -d`), primary first, from `dumpsys SurfaceFlinger --display-id`. Plain. */
    fun displays(dump: String?): List<String> =
        hwc.findAll(dump.orEmpty()).map { it.groupValues[2].toInt() to it.groupValues[1] }.sortedBy { it.first }.map { it.second }.toList()

    /** A file name for [title] at [time]: no characters a file system or a chat app would trip over. Plain. */
    fun fileName(title: String, time: LocalDateTime, fallback: String = "Screenshot", ext: String = "png"): String {
        val safe = title.replace(Regex("""[\\/:*?"<>|\n\r\t]"""), " ").replace(Regex("""\s+"""), " ").trim().take(80)
        return "${safe.ifEmpty { fallback }} ${stamp.format(time)}.$ext"
    }

    /** Takes it: [gameOnTop] when the game has the top screen. Blocking (asks Shizuku): call off the main thread. */
    fun take(ctx: Context, title: String, gameOnTop: Boolean): Result {
        val dir = File(Environment.getExternalStorageDirectory(), DIR)
        val file = File(dir, fileName(title, LocalDateTime.now()))
        Shell.run("mkdir", "-p", dir.path)
        val r = capture(file, gameOnTop)
        if (r !is Result.Saved) return r
        MediaScannerConnection.scanFile(ctx, arrayOf(file.path), arrayOf("image/png"), null)
        copyToClipboard(ctx, file)
        return r
    }

    /** Reads the game's screen into [file] (a PNG), whose folder must be there; nothing else. Blocking. */
    fun capture(file: File, gameOnTop: Boolean): Result {
        if (!Shell.ready()) return Result.Failed("Screenshots need Shizuku")
        val ids = displays(Shell.run("dumpsys", "SurfaceFlinger", "--display-id"))
        val id = (if (gameOnTop) ids.firstOrNull() else ids.getOrNull(1)) ?: return Result.Failed("Couldn't find the game's screen")
        Shell.run("screencap", "-p", "-d", id, file.path)
        if (!file.isFile || file.length() == 0L) return Result.Failed("Couldn't take the screenshot")
        return Result.Saved(file)
    }

    private fun copyToClipboard(ctx: Context, file: File) {
        runCatching {
            // One shared copy, replaced each time: the clipboard only ever holds the latest.
            val shared = File(ctx.cacheDir, "shots").apply { mkdirs() }
            shared.listFiles()?.forEach { it.delete() }
            val copy = File(shared, file.name).also { file.copyTo(it, overwrite = true) }
            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.shots", copy)
            val clip = ClipData.newUri(ctx.contentResolver, file.name, uri)
            ctx.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
        }.onFailure { android.util.Log.w("Tandem", "Couldn't copy the screenshot", it) }
    }
}
