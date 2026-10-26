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
    fun fileName(title: String, time: LocalDateTime): String {
        val safe = title.replace(Regex("""[\\/:*?"<>|\n\r\t]"""), " ").replace(Regex("""\s+"""), " ").trim().take(80)
        return "${safe.ifEmpty { "Screenshot" }} ${stamp.format(time)}.png"
    }

    /** Takes it: [gameOnTop] when the game has the top screen. Blocking (asks Shizuku): call off the main thread. */
    fun take(ctx: Context, title: String, gameOnTop: Boolean): Result {
        if (!Shell.ready()) return Result.Failed("Screenshots need Shizuku")
        val ids = displays(Shell.run("dumpsys", "SurfaceFlinger", "--display-id"))
        val id = (if (gameOnTop) ids.firstOrNull() else ids.getOrNull(1)) ?: return Result.Failed("Couldn't find the game's screen")
        val dir = File(Environment.getExternalStorageDirectory(), DIR)
        val file = File(dir, fileName(title, LocalDateTime.now()))
        Shell.run("mkdir", "-p", dir.path)
        Shell.run("screencap", "-p", "-d", id, file.path)
        if (!file.isFile || file.length() == 0L) return Result.Failed("Couldn't take the screenshot")
        MediaScannerConnection.scanFile(ctx, arrayOf(file.path), arrayOf("image/png"), null)
        copyToClipboard(ctx, file)
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
