package io.github.zahakj.tandem

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.zahakj.tandem.data.Net
import io.github.zahakj.tandem.data.Store
import io.github.zahakj.tandem.ui.Toasts
import io.github.zahakj.tandem.ui.inBackground
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/*
 * The built-in updater. Once a day while Tandem is in front (and when asked, from Settings › Play & system › About ›
 * Check now) it asks GitHub for the latest release. A newer one shows in the Start panel and in Settings; nothing
 * pops up over a game and nothing installs until the user presses Update. Then the APK is downloaded to the cache,
 * checked against the release's SHA256SUMS and against the signing key of the installed Tandem, and installed: with
 * Shizuku silently (`pm install`), otherwise through Android's installer, which asks once to allow installs from
 * Tandem. The update restarts Tandem (PackageReplacedReceiver brings the library back).
 *
 * F-Droid doesn't allow apps that update themselves: a build with `-PselfUpdate=false` has BuildConfig.SELF_UPDATE
 * off, and then none of this runs or shows.
 */

/** A release newer than this build: its version ("0.4.1"), notes (Markdown) and where its files are. */
@Serializable
data class Release(val version: String, val notes: String, val apkName: String, val apkUrl: String, val sumsUrl: String)

/** Where an update stands. [Problem] is a short sentence for the user. */
sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class Downloading(val pct: Int) : UpdateState
    data object Verifying : UpdateState
    data object Installing : UpdateState
    data class Problem(val text: String) : UpdateState
}

/** The pure parts (versions, GitHub's answer, SHA256SUMS, the notes), so they can be tested without Android. */
object Releases {
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private class GhAsset(val name: String = "", @SerialName("browser_download_url") val url: String = "")

    @Serializable
    private class GhRelease(
        @SerialName("tag_name") val tag: String = "",
        val body: String? = null,
        val draft: Boolean = false,
        val prerelease: Boolean = false,
        val assets: List<GhAsset> = emptyList(),
    )

    /**
     * Compares two versions ("v0.4.1", "0.10.0", "0.5.0-rc1") number by number, so 0.10 is newer than 0.9. Missing
     * numbers count as 0 (0.4 = 0.4.0), and a pre-release ("-rc1") comes before its release.
     */
    fun compare(a: String, b: String): Int {
        fun split(v: String): Pair<List<Int>, String?> {
            val clean = v.trim().removePrefix("v").removePrefix("V").substringBefore('+')
            val pre = clean.substringAfter('-', "").ifEmpty { null }
            return clean.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 } to pre
        }
        val (na, pa) = split(a)
        val (nb, pb) = split(b)
        for (i in 0 until maxOf(na.size, nb.size)) {
            val c = na.getOrElse(i) { 0 }.compareTo(nb.getOrElse(i) { 0 })
            if (c != 0) return c
        }
        return when {
            pa == pb -> 0
            pa == null -> 1
            pb == null -> -1
            else -> pa.compareTo(pb)
        }
    }

    /**
     * Reads GitHub's latest release ([text]) into a [Release], when it is a finished one with the APK and its
     * SHA256SUMS. Null for drafts, pre-releases, a release missing either file, or anything that isn't an answer.
     */
    fun parse(text: String): Release? = runCatching {
        val r = json.decodeFromString<GhRelease>(text)
        if (r.draft || r.prerelease || r.tag.isBlank()) return null
        val version = r.tag.trim().removePrefix("v")
        val apk = r.assets.firstOrNull { it.name == "tandem-$version.apk" } ?: return null
        val sums = r.assets.firstOrNull { it.name == "SHA256SUMS" } ?: return null
        if (apk.url.isBlank() || sums.url.isBlank()) return null
        Release(version, r.body.orEmpty(), apk.name, apk.url, sums.url)
    }.getOrNull()

    /** The SHA-256 (lowercase hex) SHA256SUMS lists for [file], or null. Lines are `<sha256>  <name>` (`*name` too). */
    fun sumFor(sums: String, file: String): String? = sums.lineSequence().mapNotNull { line ->
        val parts = line.trim().split(Regex("\\s+"), limit = 2)
        if (parts.size != 2) return@mapNotNull null
        val (hash, name) = parts
        if (name.removePrefix("*") != file || !hash.matches(Regex("[0-9a-fA-F]{64}"))) null else hash.lowercase()
    }.firstOrNull()

    /** Release notes in Markdown as plain text: no heading marks, emphasis or link targets; list items get a dot. */
    fun plainNotes(md: String): String = md.replace("\r\n", "\n").lines().joinToString("\n") { raw ->
        var line = raw.trimEnd()
        line = line.replace(Regex("^#{1,6}\\s+"), "")
        line = line.replace(Regex("^(\\s*)[-*+]\\s+"), "$1• ")
        line = line.replace(Regex("!?\\[([^\\]]*)]\\([^)]*\\)"), "$1")
        line = line.replace(Regex("(\\*\\*|__)(.+?)\\1"), "$2").replace(Regex("`([^`]*)`"), "$1")
        line
    }.replace(Regex("\n{3,}"), "\n\n").trim()

    /**
     * The downloaded APK's signer may install over the installed one: the same certificates ([installed], [apk], as
     * SHA-256 hex), or a rotated key whose history ([apkHistory]) holds the installed one.
     */
    fun sameSigner(installed: Set<String>, apk: Set<String>, apkHistory: Set<String>): Boolean =
        installed.isNotEmpty() && (installed == apk || installed.all { it in apkHistory })

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}

object Updates {
    private const val LATEST = "https://api.github.com/repos/ZahakJ/tandem/releases/latest"
    private const val PREFS = "updates"
    private const val CHECKED = "checked_at"
    private const val FOUND = "found"
    private const val TOLD = "told"
    private const val DAY_MS = 24 * 60 * 60 * 1000L

    /** A release newer than this build, once a check found one (kept across restarts until it is installed). */
    var found by mutableStateOf<Release?>(null)
        private set
    var state by mutableStateOf<UpdateState>(UpdateState.Idle)
        private set
    /** When GitHub last answered (0: never), for "Up to date". */
    var checkedAt by mutableStateOf(0L)
        private set

    private var loaded = false

    /** The updater exists in this build and the user leaves it on. */
    val enabled: Boolean get() = BuildConfig.SELF_UPDATE && Store.settings.checkUpdates

    /**
     * A check or download is under way. Installing doesn't count: Android's question can be left without an answer
     * (Home pressed over it), and Update then simply asks again.
     */
    val busy: Boolean get() = state.let { it is UpdateState.Checking || it is UpdateState.Downloading || it == UpdateState.Verifying }

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Reads what an earlier check found, once per process; forgets a release this build already is (or passed). */
    private fun load(ctx: Context) {
        if (loaded) return
        loaded = true
        val p = prefs(ctx)
        checkedAt = p.getLong(CHECKED, 0L)
        val kept = p.getString(FOUND, null)?.let { runCatching { Store.json.decodeFromString<Release>(it) }.getOrNull() }
        if (kept != null && Releases.compare(kept.version, BuildConfig.VERSION_NAME) > 0) found = kept
        else {
            p.edit().remove(FOUND).apply()
            // An APK left from an update that is now installed.
            val left = File(ctx.cacheDir, "updates")
            Thread({ left.deleteRecursively() }, "tandem-update-tidy").start()
        }
    }

    /**
     * Tandem is in front again: checks GitHub if the last check is a day old. Quiet: a failed check says nothing and
     * is tried again next time. A new release is announced once, with a toast in Tandem (never over a game).
     */
    fun daily(ctx: Context) {
        if (!BuildConfig.SELF_UPDATE) return
        load(ctx)
        if (!enabled || NetGate.closed || busy) return
        if (System.currentTimeMillis() - checkedAt in 0 until DAY_MS) return
        check(ctx, manual = false)
    }

    /** Asks GitHub now. [manual]: from Settings, so the answer (or the problem) shows there. */
    fun check(ctx: Context, manual: Boolean = true) {
        if (!BuildConfig.SELF_UPDATE || busy) return
        load(ctx)
        val app = ctx.applicationContext
        state = UpdateState.Checking
        var result: Result<Release?> = Result.success(null)
        inBackground({
            result = runCatching { Releases.parse(String(Net.bytes(LATEST, mapOf("Accept" to "application/vnd.github+json"), 1 shl 20))) }
        }) {
            result.fold({ rel ->
                checkedAt = System.currentTimeMillis()
                val p = prefs(app).edit().putLong(CHECKED, checkedAt)
                val newer = rel?.takeIf { Releases.compare(it.version, BuildConfig.VERSION_NAME) > 0 }
                found = newer
                if (newer != null) p.putString(FOUND, Store.json.encodeToString(newer)) else p.remove(FOUND)
                p.apply()
                state = UpdateState.Idle
                if (newer != null && !manual && prefs(app).getString(TOLD, null) != newer.version) {
                    prefs(app).edit().putString(TOLD, newer.version).apply()
                    Toasts.show("Tandem ${newer.version} is out · open Start to update", 4500)
                }
            }) { t ->
                Log.i("Tandem", "Update check failed: ${t.message}")
                state = if (manual) UpdateState.Problem(problem(t)) else UpdateState.Idle
            }
        }
    }

    private fun problem(t: Throwable): String = when (t) {
        is PrivateOffline -> "Offline in private mode"
        is io.github.zahakj.tandem.data.HttpError -> "GitHub said ${t.code}"
        is IOException -> "Couldn't reach GitHub"
        else -> t.message ?: "That didn't work"
    }

    /** The row's words: this version and where the update stands. */
    fun statusLine(): String {
        val now = BuildConfig.VERSION_NAME
        return when (val s = state) {
            UpdateState.Checking -> "$now · Checking…"
            is UpdateState.Downloading -> "Downloading ${s.pct} %"
            UpdateState.Verifying -> "Verifying"
            UpdateState.Installing -> "Installing"
            is UpdateState.Problem -> s.text
            UpdateState.Idle -> found?.let { "${it.version} available" } ?: if (checkedAt > 0) "$now · Up to date" else now
        }
    }

    /**
     * The user pressed Update: downloads [found], verifies it and installs it. Never called without that press.
     * A problem shows as the state (in the popup and Settings) and leaves the release offered, to try again.
     */
    fun install(ctx: Context) {
        val rel = found ?: return
        if (!BuildConfig.SELF_UPDATE || busy) return
        val app = ctx.applicationContext
        state = UpdateState.Downloading(0)
        var outcome: String? = null
        inBackground({
            outcome = runCatching { fetchAndInstall(app, rel) }.getOrElse { t ->
                Log.w("Tandem", "Update failed", t)
                if (t is IOException) problem(t) else t.message ?: "That didn't work"
            }
        }) {
            // Null: Android's installer has it now (its answer comes to UpdateInstallReceiver), or Tandem restarts.
            outcome?.let { state = UpdateState.Problem(it) }
        }
    }

    /** Blocking. Null once the install is handed over, otherwise what went wrong. */
    private fun fetchAndInstall(ctx: Context, rel: Release): String? {
        val dir = File(ctx.cacheDir, "updates").apply { deleteRecursively(); mkdirs() }
        val apk = File(dir, rel.apkName)
        download(rel.apkUrl, apk) { state = UpdateState.Downloading(it) }
        state = UpdateState.Verifying
        verify(ctx, rel, apk)?.let { apk.delete(); return it }
        state = UpdateState.Installing
        if (Shell.ready() && installWithShizuku(apk)) return null
        installWithSession(ctx, apk)
        return null
    }

    /** Streams [url] into [to], reporting whole percents. Private mode stops it before it starts. */
    private fun download(url: String, to: File, progress: (Int) -> Unit) {
        NetGate.check()
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 15_000
            c.readTimeout = 30_000
            c.instanceFollowRedirects = true
            c.setRequestProperty("User-Agent", Net.AGENT)
            if (c.responseCode !in 200..299) throw io.github.zahakj.tandem.data.HttpError(c.responseCode, "")
            val total = c.contentLengthLong
            var done = 0L
            var shown = -1
            c.inputStream.use { input ->
                to.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        // A release APK is tens of MB; anything far bigger isn't one.
                        if (done > 256L shl 20) throw IOException("too large")
                        val pct = if (total > 0) (done * 100 / total).toInt().coerceIn(0, 100) else 0
                        if (pct != shown) { shown = pct; progress(pct) }
                    }
                }
            }
        } finally {
            c.disconnect()
        }
    }

    /**
     * Null when [apk] is the release and may install over this Tandem: its SHA-256 is the one SHA256SUMS lists, it is
     * Tandem, newer, and signed with the installed app's key. Otherwise why not.
     */
    private fun verify(ctx: Context, rel: Release, apk: File): String? {
        val sums = String(Net.bytes(rel.sumsUrl, maxBytes = 64 shl 10))
        val want = Releases.sumFor(sums, rel.apkName) ?: return "SHA256SUMS doesn't list ${rel.apkName}"
        val digest = MessageDigest.getInstance("SHA-256")
        apk.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) { val n = input.read(buf); if (n < 0) break; digest.update(buf, 0, n) }
        }
        if (digest.digest().joinToString("") { "%02x".format(it) } != want) return "Download damaged (checksum differs)"
        val pm = ctx.packageManager
        val theirs = pm.getPackageArchiveInfo(apk.path, PackageManager.GET_SIGNING_CERTIFICATES) ?: return "Not a readable APK"
        val mine = pm.getPackageInfo(ctx.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        if (theirs.packageName != ctx.packageName) return "Not a Tandem APK"
        if (theirs.longVersionCode <= mine.longVersionCode) return "Not newer than this Tandem"
        val history = theirs.signingInfo?.signingCertificateHistory.orEmpty().map { Releases.sha256(it.toByteArray()) }.toSet()
        if (!Releases.sameSigner(signers(mine), signers(theirs), history)) {
            // A debug build has the debug key: the release can't replace it. Install it by hand after uninstalling.
            return if (BuildConfig.DEBUG) "Can't install over a debug build" else "Not signed with Tandem's key: not installed"
        }
        return null
    }

    private fun signers(info: PackageInfo): Set<String> =
        info.signingInfo?.apkContentsSigners.orEmpty().map { Releases.sha256(it.toByteArray()) }.toSet()

    /** `pm install -r` through Shizuku, the APK on its stdin (the shell can't read Tandem's cache). True when it worked. */
    private fun installWithShizuku(apk: File): Boolean {
        val p = Shell.spawn(listOf("pm", "install", "-r", "-S", apk.length().toString())) ?: return false
        return try {
            p.outputStream.use { out -> apk.inputStream().use { it.copyTo(out) } }
            val said = p.inputStream.bufferedReader().readText()
            p.waitFor()
            if ("Success" !in said) Log.w("Tandem", "pm install said: ${said.trim()}")
            "Success" in said
        } catch (t: Throwable) {
            Log.w("Tandem", "Shizuku install failed", t)
            false
        } finally {
            Shell.release(p)
        }
    }

    /**
     * Android's installer: writes the APK into a session and commits it. Android asks the user to confirm (and, the
     * first time, to allow installs from Tandem); its answers arrive at [UpdateInstallReceiver].
     */
    private fun installWithSession(ctx: Context, apk: File) {
        val installer = ctx.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(ctx.packageName)
            setSize(apk.length())
            // Android 12+ may then let Tandem update itself without a second question, once installs are allowed.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            session.openWrite(apk.name, 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val answer = Intent(ctx, UpdateInstallReceiver::class.java)
            val pending = PendingIntent.getBroadcast(ctx, id, answer, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
            session.commit(pending.intentSender)
        }
    }

    /** Android's installer answered with a problem, or the user said no. */
    internal fun failed(text: String) { state = UpdateState.Problem(text) }

    /** The user backed out of Android's question: the update stays offered. */
    internal fun cancelled() { state = UpdateState.Idle }
}

/** Android's installer's answers for an update (see [Updates.install]). */
class UpdateInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return Updates.failed("Android didn't ask to install")
                runCatching { context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    .onFailure { Updates.failed("Couldn't open Android's installer") }
            }
            // Tandem is replaced and restarts; nothing to do.
            PackageInstaller.STATUS_SUCCESS -> Unit
            PackageInstaller.STATUS_FAILURE_ABORTED -> Updates.cancelled()
            else -> {
                val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                Log.w("Tandem", "Update install failed ($status): $msg")
                Updates.failed(if (status == PackageInstaller.STATUS_FAILURE_STORAGE) "Not enough space to install" else "Android didn't install it")
            }
        }
    }
}
