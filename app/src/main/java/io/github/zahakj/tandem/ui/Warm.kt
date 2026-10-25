package io.github.zahakj.tandem.ui

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.Choreographer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.zahakj.tandem.Library
import io.github.zahakj.tandem.LaunchDisplay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/*
 * Keeping transitions smooth (docs/PERFORMANCE.md).
 *
 * A screen change or a return from a game has one frame (8-16 ms) to compose and draw the new picture. Anything
 * that reads the disk, asks a system service or sorts the library in that frame shows as a stutter. So:
 *  - [Facts] holds the few things Settings shows that need the disk or a system service (wallpaper files, the
 *    music folder, Cocoon backups, the Home role...). They are read on a background thread and kept as Compose
 *    state; screens read the state and never touch the disk while composing.
 *  - [AfterFrames] runs work once the next frames are on screen, so coming back to Tandem draws the last picture
 *    first and only then catches up (play time, the bottom screen, music, rescans).
 */

/** Disk and system facts for Settings, read off the main thread. */
object Facts {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var app: Context? = null
    @Volatile private var loaded = false

    /** Pictures and videos in ROMs/tandem/wallpapers. */
    var wallpapers by mutableStateOf<List<File>>(emptyList())
        private set
    /** Songs in ROMs/tandem/music. */
    var musicTracks by mutableIntStateOf(0)
        private set
    /** The newest complete Cocoon backup in ROMs/backups. */
    var cocoonBackup by mutableStateOf<File?>(null)
        private set
    /** Cocoon / Daijisho platform files in ROMs/platforms. */
    var cocoonPlatformFiles by mutableIntStateOf(0)
        private set
    /** Tandem holds the Home role. */
    var isHome by mutableStateOf(false)
        private set
    /** There is a second display games can use. */
    var hasSecondScreen by mutableStateOf(true)
        private set

    private class Snapshot(
        val wallpapers: List<File>, val musicTracks: Int, val cocoonBackup: File?, val cocoonPlatformFiles: Int,
        val isHome: Boolean, val hasSecondScreen: Boolean,
    )

    private fun read(ctx: Context) = Snapshot(
        wallpapers = runCatching { wallpaperFiles() }.getOrDefault(emptyList()),
        musicTracks = runCatching { Music.tracks().size }.getOrDefault(0),
        cocoonBackup = runCatching { CocoonSetup.newestBackup() }.getOrNull(),
        cocoonPlatformFiles = runCatching { File(Library.romsDir, "platforms").listFiles { f -> f.name.endsWith(".json", true) }?.size ?: 0 }.getOrDefault(0),
        isHome = runCatching { HomeMode.isDefault(ctx) }.getOrDefault(false),
        hasSecondScreen = runCatching { LaunchDisplay.secondary(ctx) != null }.getOrDefault(false),
    )

    private fun apply(s: Snapshot) {
        // Structural equality: unchanged values don't recompose anything.
        wallpapers = s.wallpapers
        musicTracks = s.musicTracks
        cocoonBackup = s.cocoonBackup
        cocoonPlatformFiles = s.cocoonPlatformFiles
        isHome = s.isHome
        hasSecondScreen = s.hasSecondScreen
        loaded = true
    }

    /** Reads everything again in the background (on start, on coming back, after a change in Settings). */
    fun refresh(ctx: Context) {
        val c = ctx.applicationContext
        app = c
        scope.launch {
            val s = withContext(Dispatchers.IO) { read(c) }
            apply(s)
        }
    }

    /**
     * For the very first read before [refresh] has finished: reads synchronously once, so a screen never shows
     * blanks. After that, reading the state is free.
     */
    fun ensure(ctx: Context) {
        if (loaded) return
        app = ctx.applicationContext
        apply(read(ctx.applicationContext))
    }
}

/**
 * A lifecycle that follows [parent] but never goes above [cap]. A screen kept composed while hidden (KeptScreen in
 * App.kt) runs under one capped at CREATED, so its videos, floating motion and polling stop until it shows again.
 */
class CappedLifecycleOwner(private val parent: androidx.lifecycle.LifecycleOwner) : androidx.lifecycle.LifecycleOwner {
    private val registry = androidx.lifecycle.LifecycleRegistry(this)
    private val observer = androidx.lifecycle.LifecycleEventObserver { _, _ -> sync() }
    override val lifecycle: androidx.lifecycle.Lifecycle get() = registry

    var cap: androidx.lifecycle.Lifecycle.State = androidx.lifecycle.Lifecycle.State.RESUMED
        set(value) { if (field != value) { field = value; sync() } }

    fun attach() { parent.lifecycle.addObserver(observer); sync() }

    fun detach() {
        parent.lifecycle.removeObserver(observer)
        if (registry.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.CREATED)) registry.currentState = androidx.lifecycle.Lifecycle.State.DESTROYED
    }

    private fun sync() {
        if (registry.currentState == androidx.lifecycle.Lifecycle.State.DESTROYED) return
        val p = parent.lifecycle.currentState
        val target = if (p < cap) p else cap
        if (target == androidx.lifecycle.Lifecycle.State.DESTROYED || target == androidx.lifecycle.Lifecycle.State.INITIALIZED) {
            if (target == androidx.lifecycle.Lifecycle.State.DESTROYED && registry.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.CREATED)) registry.currentState = target
            return
        }
        registry.currentState = target
    }
}

/** Runs work after the next frames are drawn, so it never lands in the frame that shows a new picture. */
object AfterFrames {
    private val main = Handler(Looper.getMainLooper())

    /**
     * Runs [block] on the main thread after [frames] more frames (two by default: the one being prepared and the
     * one after it), plus [delayMs]. Returns a cancel function.
     */
    fun run(frames: Int = 2, delayMs: Long = 0, block: () -> Unit): () -> Unit {
        var cancelled = false
        var left = frames
        val callback = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                if (cancelled) return
                if (--left > 0) { Choreographer.getInstance().postFrameCallback(this); return }
                if (delayMs > 0) main.postDelayed({ if (!cancelled) block() }, delayMs) else main.post { if (!cancelled) block() }
            }
        }
        main.post { if (!cancelled) Choreographer.getInstance().postFrameCallback(callback) }
        return { cancelled = true }
    }
}
