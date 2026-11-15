package io.github.zahakj.tandem

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.os.Build
import android.os.FileObserver
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.MediaStore
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import io.github.zahakj.tandem.data.Store
import io.github.zahakj.tandem.data.SystemPrefs
import io.github.zahakj.tandem.ui.LibraryScan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Turns a burst of file events into one rescan: a few seconds of quiet after the last event, and never while a scan
 * is running (the change waits, and one rescan follows the scan). Pure, so it can be tested; [FolderWatch] feeds it
 * from the main thread. Times are any clock in milliseconds, the same one throughout.
 */
class RescanCoalescer(private val quietMs: Long) {
    private var lastChange = NONE

    /** Something changed at [now]; a change while one is already waiting just restarts the quiet time. */
    fun changed(now: Long) { lastChange = now }

    /** A change is waiting for its rescan. */
    val pending: Boolean get() = lastChange != NONE

    /** How long until the pending change is due, or null with nothing pending. */
    fun waitFor(now: Long): Long? = if (!pending) null else (lastChange + quietMs - now).coerceAtLeast(0)

    /**
     * True when a rescan should start now: something changed, it has been quiet for the whole quiet time, and no
     * scan is running. The pending change is taken; while a scan runs it stays, so the scan that follows sees it,
     * and several changes during a scan still make one rescan.
     */
    fun take(now: Long, scanning: Boolean): Boolean {
        if (!pending || now - lastChange < quietMs || scanning) return false
        lastChange = NONE
        return true
    }

    private companion object {
        const val NONE = Long.MIN_VALUE
    }
}

/**
 * Watches the game folders while Tandem is on screen, so a game copied over USB or MTP (or an SD card put back)
 * shows up by itself a few seconds later, with the light rescan that re-lists only the folders whose contents
 * changed. Three ears, since no single one hears everything on Android's shared storage: one `inotify` instance
 * ([FileObserver]) over every root, its system folders and every folder holding a game (it hears Tandem's own
 * process and some others, but on the FUSE-backed shared storage of Android 11 and later it misses writes from
 * other apps, MTP and adb); a [ContentObserver] on the media store, which hears files that MTP and file managers
 * add; and a timestamp poll every [POLL_MS] over the same folders, which catches whatever both missed at the cost
 * of a few hundred `stat` calls. Events are coalesced by [RescanCoalescer] and never start a scan while one runs.
 * Started and stopped with the activity by [FolderWatchHost]; nothing watches while Tandem is in the background.
 */
object FolderWatch {
    /** Quiet time after the last event before the rescan: a copy of several files makes one scan, not one each. */
    const val QUIET_MS = 3_000L
    /** Enough for every root, system folder and game folder of a big library; `inotify` has its own limit (8192). */
    private const val MAX_DIRS = 512
    private const val MASK = FileObserver.CREATE or FileObserver.DELETE or FileObserver.MOVED_FROM or FileObserver.MOVED_TO or
        FileObserver.CLOSE_WRITE or FileObserver.DELETE_SELF or FileObserver.MOVE_SELF

    /** How often the folders' timestamps are compared, the net under the two event sources. */
    const val POLL_MS = 10_000L

    private val coalescer = RescanCoalescer(QUIET_MS)
    private val handler = Handler(Looper.getMainLooper())
    private var observer: FileObserver? = null
    private var media: ContentObserver? = null
    private var app: Context? = null
    /** The folders being watched and their modification times at the last look (a folder's time changes when a file in it comes or goes). */
    @Volatile private var dirs: List<File> = emptyList()
    @Volatile private var stamps: Map<File, Long> = emptyMap()
    @Volatile private var polling = false

    /** How many folders are being watched (for the Settings row). */
    @Volatile var watching = 0
        private set

    /**
     * The folders worth watching: each root (a new system folder appears there), each system folder that exists in
     * each root, and every folder a game was found in (a subfolder's new game changes that folder, not the system's).
     * Only folders that exist: `inotify` can't watch what isn't there, and an SD card that is out is noticed
     * through its mount broadcast instead.
     */
    fun foldersToWatch(roots: List<File>, libs: List<PlatformLibrary>): List<File> {
        val out = LinkedHashSet<File>()
        for (root in roots) if (root.isDirectory) out += root
        for (lib in libs) {
            if (lib.platform.isApps) continue
            for (root in roots) for (name in lib.platform.folders) Library.folder(name, root).takeIf { it.isDirectory }?.let { out += it }
            // Folders named for the system some other way ("3DS ROMs", or a root that is one system's folder).
            for (dir in lib.folders) if (dir.isDirectory) out += dir
        }
        for (lib in libs) if (!lib.platform.isApps) for (g in lib.games) g.file.parentFile?.let { out += it }
        return out.take(MAX_DIRS)
    }

    /** Watches [dirs] (replacing whatever was watched). Blocking on the folder count; call off the main thread. */
    fun start(ctx: Context, dirs: List<File>) {
        app = ctx.applicationContext
        val fresh = if (dirs.isEmpty()) null else object : FileObserver(dirs, MASK) {
            override fun onEvent(event: Int, path: String?) {
                // Hidden files (.nomedia, a file manager's .trashed-...) are never games, so they change nothing.
                if (path != null && path.startsWith(".")) return
                handler.post { noticed() }
            }
        }
        fresh?.startWatching()
        val now = stamp(dirs)
        synchronized(this) {
            observer?.stopWatching()
            observer = fresh
            watching = if (fresh == null) 0 else dirs.size
            this.dirs = dirs
            stamps = now
            if (media == null && dirs.isNotEmpty()) {
                val m = object : ContentObserver(handler) {
                    override fun onChange(selfChange: Boolean) { noticed() }
                }
                runCatching { app?.contentResolver?.registerContentObserver(MediaStore.Files.getContentUri("external"), true, m) }
                media = m
            }
            if (!polling && dirs.isNotEmpty()) { polling = true; handler.postDelayed(poll, POLL_MS) }
        }
    }

    /** Each folder's modification time; a folder that is gone counts as 0, so its going is a change too. */
    fun stamp(dirs: List<File>): Map<File, Long> = dirs.associateWith { it.lastModified() }

    /** Off the main thread: compares the folders' times with the last look and reports a change; then looks again later. */
    private val poll = object : Runnable {
        override fun run() {
            val watched = dirs
            if (!polling || watched.isEmpty()) { polling = false; return }
            Thread({
                val now = stamp(watched)
                val changed = now != stamps
                stamps = now
                handler.post {
                    if (changed) noticed()
                    if (polling) handler.postDelayed(this, POLL_MS)
                }
            }, "tandem-folder-poll").start()
        }
    }

    fun stop() {
        synchronized(this) {
            observer?.stopWatching()
            observer = null
            watching = 0
            media?.let { m -> runCatching { app?.contentResolver?.unregisterContentObserver(m) } }
            media = null
            polling = false
            dirs = emptyList()
        }
        handler.removeCallbacks(check)
        handler.removeCallbacks(poll)
    }

    /** A storage volume came or went (an SD card): what is on it shows or hides, after the same quiet time. */
    fun mounted() = noticed()

    /** Main thread. */
    private fun noticed() {
        coalescer.changed(SystemClock.uptimeMillis())
        handler.removeCallbacks(check)
        handler.postDelayed(check, QUIET_MS)
    }

    private val check = object : Runnable {
        override fun run() {
            val now = SystemClock.uptimeMillis()
            when {
                coalescer.take(now, LibraryScan.scanning) -> app?.let { LibraryScan.request(it, announce = false, onlyIfChanged = true) }
                // Not yet (a scan is running, or the quiet time moved): look again in a moment.
                coalescer.pending -> handler.postDelayed(this, (coalescer.waitFor(now) ?: QUIET_MS).coerceAtLeast(500))
            }
        }
    }
}

/**
 * Runs [FolderWatch] with the activity: watching while it is started, re-armed whenever the library (and so the set
 * of folders), the roots or the Watch game folders setting change, and off as soon as the activity stops. Also
 * listens for storage volumes coming and going, so an SD card put back shows its games without a visit to Settings.
 */
@Composable
fun FolderWatchHost() {
    val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(c: Context?, intent: Intent?) { FolderWatch.mounted() }
            }
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_MEDIA_MOUNTED)
                addAction(Intent.ACTION_MEDIA_UNMOUNTED)
                addAction(Intent.ACTION_MEDIA_EJECT)
                addAction(Intent.ACTION_MEDIA_REMOVED)
                addDataScheme("file")
            }
            if (Build.VERSION.SDK_INT >= 33) ctx.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            else ctx.registerReceiver(receiver, filter)
            try {
                snapshotFlow { Triple(Libraries.all, Store.settings.watchFolders, SystemPrefs.data.roots) }.collectLatest { (libs, on, _) ->
                    if (!on || libs == null) { FolderWatch.stop(); return@collectLatest }
                    withContext(Dispatchers.IO) { FolderWatch.start(ctx, FolderWatch.foldersToWatch(Library.roots(), libs)) }
                }
            } finally {
                runCatching { ctx.unregisterReceiver(receiver) }
                FolderWatch.stop()
            }
        }
    }
}
