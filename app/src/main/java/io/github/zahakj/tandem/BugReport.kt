package io.github.zahakj.tandem

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import io.github.zahakj.tandem.data.HomeStore
import io.github.zahakj.tandem.data.Redactor
import io.github.zahakj.tandem.data.ReportLog
import io.github.zahakj.tandem.data.ScrapeConfig
import io.github.zahakj.tandem.data.SteamStore
import io.github.zahakj.tandem.data.Store
import io.github.zahakj.tandem.data.SystemPrefs
import io.github.zahakj.tandem.ui.Confirm
import io.github.zahakj.tandem.ui.Toasts
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Settings › Play & system › Updates › Send a bug report: a zip in ROMs/tandem/reports/ with what helps find a
 * problem, so nobody needs adb. It holds report.txt (Tandem's version, the device, Android, the settings that change
 * how things behave) and logcat.txt (Tandem's own log lines, of its own process: an app may read those without any
 * permission). Both are redacted first ([Redactor]): account keys, passwords and user names Tandem keeps, hidden
 * games' names, e-mails, IP addresses, tokens, and every folder or file name in a path that isn't Tandem's or a
 * system's. Nothing is sent: Tandem offers Android's share sheet, and the file stays where it is.
 */
object BugReport {
    private const val TAG = "Tandem"
    val dir: File get() = File(Library.romsDir, "tandem/reports")

    /** A asks for it: made in the background, then Share or just where it is. */
    fun make(ctx: Context) {
        Toasts.show(Tx.s(R.string.settings_report_making))
        Thread({
            val zip = runCatching { write(ctx) }.onFailure { Log.w(TAG, "Couldn't save a bug report", it) }.getOrNull()
            Handler(Looper.getMainLooper()).post {
                if (zip == null) { Toasts.show(Tx.s(R.string.settings_report_failed)); return@post }
                val where = zip.relativeToOrNull(Library.romsDir.parentFile ?: Library.romsDir)?.path ?: zip.path
                Confirm.ask(Tx.s(R.string.settings_report_saved), Tx.s(R.string.settings_report_saved_detail, where), Tx.s(R.string.settings_report_share)) {
                    share(ctx, zip)
                }
            }
        }, "tandem-report").start()
    }

    /** Writes the zip; blocking. */
    fun write(ctx: Context): File {
        val redactor = redactor()
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date())
        dir.mkdirs()
        val zip = File(dir, "tandem-report-$stamp.zip")
        val report = redactor.text(summary(ctx))
        val log = ReportLog.ours(readLog()).joinToString("\n") { redactor.line(it) }
        ZipOutputStream(zip.outputStream().buffered()).use { z ->
            z.putNextEntry(ZipEntry("report.txt")); z.write(report.toByteArray()); z.closeEntry()
            z.putNextEntry(ZipEntry("logcat.txt")); z.write(log.toByteArray()); z.closeEntry()
        }
        return zip
    }

    /** What must never leave: the secrets Tandem keeps, hidden games' names, and the names safe to keep in paths. */
    private fun redactor(): Redactor {
        val ra = HomeStore.ra
        val steam = SteamStore.account
        val ss = ScrapeConfig.settings
        val secrets = listOf(ra.user, ra.key, steam.key, steam.id, steam.name, ss.ssUser, ss.ssPassword, ss.ssDevId, ss.ssDevPassword, ss.sgdbKey, ss.igdbClientId, ss.igdbSecret, ss.ssVerifiedUser)
        val hidden = Libraries.games.filter { it.meta.hidden || Privacy.isHidden(it) }.flatMap { listOf(it.title, it.fileTitle, it.stem, it.file.name) } +
            Store.settings.hiddenFolders.mapNotNull { it.folder }
        val safe = Libraries.all.orEmpty().flatMap { lib -> listOf(lib.platform.id) + lib.platform.folders.filter { !it.startsWith("/") } + lib.platform.artIds }
        return Redactor(secrets, hidden, safe)
    }

    /** This process's log, newest last (at most the last few thousand lines). */
    private fun readLog(): List<String> = runCatching {
        val p = ProcessBuilder("logcat", "-d", "-v", "threadtime", "-t", "6000", "--pid=${android.os.Process.myPid()}").redirectErrorStream(true).start()
        val lines = p.inputStream.bufferedReader().readLines()
        p.waitFor()
        lines
    }.getOrDefault(emptyList())

    /** The facts and settings worth knowing, one per line, in English (a report is read by whoever fixes it). */
    private fun summary(ctx: Context): String = buildString {
        val s = Store.settings
        val look = s.look
        val home = HomeStore.settings
        fun line(k: String, v: Any?) = appendLine("$k: $v")
        appendLine("Tandem bug report") // tx-ok: a file for developers, always English
        line("Tandem", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}, ${BuildConfig.BUILD_TYPE})")
        line("Device", "${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})")
        line("Android", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        line("Made", SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.ROOT).format(Date()))
        line("Language", Lang.locale.toLanguageTag())
        line("Second screen", LaunchDisplay.secondary(ctx) != null)
        line("Single screen", SystemPrefs.data.singleScreen)
        line("Home layout", look.homeLayout)
        line("Theme", look.theme)
        line("Reduce motion", s.reduceMotion)
        line("Sort", "${s.sort} (ignore articles ${s.ignoreArticles}, favorites first ${s.favoritesFirst})")
        line("Private mode", home.privateMode)
        line("Show hidden", s.showHidden)
        line("Close previous game", home.closePrevious)
        line("Companion screen", home.companion)
        line("Shizuku", runCatching { Shell.ready() }.getOrDefault(false))
        line("Own platforms.json", File(Library.romsDir, "tandem/platforms.json").isFile)
        line("Extra game folders", SystemPrefs.data.roots.size)
        line("Watch folders", s.watchFolders)
        line("RetroAchievements", if (HomeStore.ra.user.isNotBlank()) "signed in, ${if (HomeStore.ra.enabled) "on" else "off"}" else "no")
        line("Steam", if (SteamStore.account.signedIn) "signed in" else "no")
        line("Scraping sources", ScrapeConfig.settings.order.filter { it !in ScrapeConfig.settings.off }.joinToString())
        appendLine()
        appendLine("Systems (games, emulator):")
        for (lib in Libraries.all.orEmpty().sortedBy { it.platform.id }) {
            val visible = lib.games.count { !Privacy.isHidden(it) }
            val player = runCatching { lib.platform.players.firstOrNull()?.name }.getOrNull() ?: "none installed"
            appendLine("  ${lib.platform.id}: $visible, $player")
        }
    }

    private fun share(ctx: Context, zip: File) {
        val uri = runCatching {
            val d = File(ctx.cacheDir, "share").apply { mkdirs() }
            d.listFiles()?.forEach { it.delete() }
            val copy = File(d, zip.name).also { zip.copyTo(it, overwrite = true) }
            androidx.core.content.FileProvider.getUriForFile(ctx, "${ctx.packageName}.shots", copy)
        }.getOrNull()
        if (uri == null) { Toasts.show(Tx.s(R.string.settings_report_share_failed)); return }
        val send = Intent(Intent.ACTION_SEND).setType("application/zip")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, "Tandem bug report") // tx-ok: the file's subject, for the developer
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = android.content.ClipData.newRawUri(zip.name, uri)
        runCatching { ctx.startActivity(Intent.createChooser(send, Tx.s(R.string.settings_report_share)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { Toasts.show(Tx.s(R.string.settings_report_share_failed)) }
    }
}
