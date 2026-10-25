package io.github.zahakj.tandem

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.Process
import android.os.StatFs
import android.os.storage.StorageManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files

/** Free and total space on one storage volume. */
data class VolumeInfo(val label: String, val free: Long, val total: Long, val removable: Boolean) {
    val used: Long get() = (total - free).coerceAtLeast(0)
    val fraction: Float get() = if (total > 0) used.toFloat() / total else 0f
}

@Serializable
data class SizedFolder(val name: String, val bytes: Long)

/** The result of measuring folders, kept on disk so it shows straight away next time. */
@Serializable
data class FolderSizes(
    val at: Long = 0,
    /** Every entry directly inside ROMs, by name (system folders, downloaded_media, ...). */
    val roms: Map<String, Long> = emptyMap(),
    /** The biggest folders on the shared storage (ROMs split into its folders), biggest first. */
    val biggest: List<SizedFolder> = emptyList(),
    /** ROMs/downloaded_media: scraped artwork and videos. */
    val mediaBytes: Long = 0,
    /** Tandem's own picture cache. */
    val cacheBytes: Long = 0,
)

/**
 * Storage for the storage widget. Volumes are a quick StatFs; folder sizes walk the file tree, which can take a
 * while on a big library, so it only runs on a background thread while Tandem is in front, stops as soon as it
 * isn't, and is cached (memory + `widget-cache.json`) for [STALE_MS].
 */
object StorageInfo {
    private const val STALE_MS = 15 * 60_000L
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    var volumes by mutableStateOf<List<VolumeInfo>>(emptyList())
        private set
    var sizes by mutableStateOf<FolderSizes?>(null)
        private set
    var scanning by mutableStateOf(false)
        private set
    private var loaded = false

    /** Blocking but cheap. */
    fun readVolumes(ctx: Context) {
        val sm = ctx.getSystemService(StorageManager::class.java) ?: return
        volumes = sm.storageVolumes.mapNotNull { v ->
            val dir = v.directory ?: return@mapNotNull null
            runCatching {
                val fs = StatFs(dir.path)
                VolumeInfo(if (v.isPrimary) "Internal" else if (v.isRemovable) "SD card" else v.getDescription(ctx), fs.availableBytes, fs.totalBytes, v.isRemovable)
            }.getOrNull()
        }.filter { it.total > 0 }.sortedBy { if (it.removable) 1 else 0 }
    }

    /** Loads the last result from disk, once. Blocking. */
    fun load(ctx: Context) {
        if (loaded) return
        loaded = true
        if (sizes == null) sizes = runCatching { json.decodeFromString<FolderSizes>(cacheFile(ctx).readText()) }.getOrNull()
    }

    fun stale(): Boolean = System.currentTimeMillis() - (sizes?.at ?: 0) > STALE_MS

    /**
     * Measures the folders. Blocking and slow: call off the main thread. Gives up (keeping the old result) as soon as
     * [alive] returns false, so it never runs on while a game is being played.
     */
    fun scan(ctx: Context, alive: () -> Boolean): Boolean {
        synchronized(lock) { return scanLocked(ctx, alive) }
    }

    private val lock = Any()

    private fun scanLocked(ctx: Context, alive: () -> Boolean): Boolean {
        if (!alive()) return false
        scanning = true
        val oldPriority = Process.getThreadPriority(Process.myTid())
        Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
        try {
            val romsDir = Library.romsDir
            val roms = HashMap<String, Long>()
            for (f in romsDir.listFiles().orEmpty()) {
                if (f.name.startsWith(".")) continue
                roms[f.name] = if (f.isDirectory) sizeOf(f, alive) ?: return false else f.length()
            }
            val biggest = mutableListOf<SizedFolder>()
            val root = Environment.getExternalStorageDirectory()
            for (f in root.listFiles().orEmpty()) {
                if (!f.isDirectory || f.name == "Android" || f.name.startsWith(".")) continue
                if (f.canonicalPath == romsDir.canonicalPath) {
                    for ((name, bytes) in roms) if (File(romsDir, name).isDirectory) biggest += SizedFolder("${f.name}/$name", bytes)
                    continue
                }
                biggest += SizedFolder(f.name, sizeOf(f, alive) ?: return false)
            }
            val result = FolderSizes(
                at = System.currentTimeMillis(),
                roms = roms,
                biggest = biggest.filter { it.bytes > 0 }.sortedByDescending { it.bytes }.take(8),
                mediaBytes = roms["downloaded_media"] ?: 0,
                cacheBytes = sizeOf(ctx.cacheDir, alive) ?: 0,
            )
            sizes = result
            runCatching { cacheFile(ctx).writeText(json.encodeToString(result)) }
            return true
        } finally {
            Process.setThreadPriority(oldPriority)
            scanning = false
        }
    }

    /** Space used by each system's ROM folders, biggest first. */
    fun systemSizes(): List<Pair<Platform, Long>> {
        val roms = sizes?.roms ?: return emptyList()
        return Libraries.all.orEmpty().map { lib ->
            lib.platform to lib.platform.folders.distinctBy { it.substringBefore('/') }.sumOf { roms[it.substringBefore('/')] ?: 0L }
        }.filter { it.second > 0 }.sortedByDescending { it.second }
    }

    private fun cacheFile(ctx: Context) = File(ctx.applicationContext.filesDir, "widget-cache.json")

    /** Total size of the files under [dir]; null when stopped early. Symbolic links are not followed. */
    private fun sizeOf(dir: File, alive: () -> Boolean): Long? {
        var total = 0L
        var seen = 0
        val stack = ArrayDeque<File>()
        stack.addLast(dir)
        while (stack.isNotEmpty()) {
            val d = stack.removeLast()
            for (f in d.listFiles().orEmpty()) {
                if ((++seen and 127) == 0 && !alive()) return null
                if (Files.isSymbolicLink(f.toPath())) continue
                if (f.isDirectory) stack.addLast(f) else total += f.length()
            }
        }
        return total
    }
}

data class BatteryState(val level: Int, val charging: Boolean, val full: Boolean, val plugged: Boolean, val tempC: Float?, val minutesLeft: Long?)
data class MemoryState(val free: Long, val total: Long) {
    val usedFraction: Float get() = if (total > 0) (total - free).toFloat() / total else 0f
}

/** Battery, memory and temperature readings for the device widgets. Each is a quick read; nothing here polls. */
object DeviceInfo {
    fun battery(ctx: Context): BatteryState {
        val bm = ctx.getSystemService(BatteryManager::class.java)
        val i = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val fromIntent = i?.let {
            val l = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val s = it.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            if (l >= 0 && s > 0) l * 100 / s else null
        }
        val level = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.takeIf { it in 0..100 } ?: fromIntent ?: -1
        val status = i?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val plugged = (i?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING
        val full = status == BatteryManager.BATTERY_STATUS_FULL || (plugged && level >= 100)
        val temp = i?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)?.takeIf { it > -400 && it < 1200 }?.div(10f)
        val minutes: Long? = when {
            charging -> runCatching { bm?.computeChargeTimeRemaining() ?: -1L }.getOrDefault(-1L).takeIf { it > 0 }?.div(60_000)
            !plugged && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                runCatching { ctx.getSystemService(PowerManager::class.java)?.batteryDischargePrediction?.toMinutes() }.getOrNull()?.takeIf { it > 0 }
            else -> null
        }
        return BatteryState(level, charging, full, plugged, temp, minutes)
    }

    fun memory(ctx: Context): MemoryState {
        val am = ctx.getSystemService(ActivityManager::class.java)
        val info = ActivityManager.MemoryInfo()
        am?.getMemoryInfo(info)
        return MemoryState(info.availMem, info.totalMem)
    }

    private var tempFile: File? = null
    private var searched = false

    /** The CPU temperature in °C, when Android lets apps read it (often it doesn't). Blocking but quick. */
    @Synchronized
    fun cpuTempC(): Float? {
        if (!searched) { searched = true; tempFile = findCpuZone() }
        val f = tempFile ?: return null
        val raw = runCatching { f.readText().trim().toFloat() }.getOrNull() ?: return null
        val c = if (raw > 1000) raw / 1000f else raw
        return c.takeIf { it in 5f..130f }
    }

    private fun findCpuZone(): File? {
        val zones = File("/sys/class/thermal").listFiles { f -> f.name.startsWith("thermal_zone") }.orEmpty().sortedBy { it.name.removePrefix("thermal_zone").toIntOrNull() ?: Int.MAX_VALUE }
        val readable = zones.mapNotNull { z ->
            val type = runCatching { File(z, "type").readText().trim().lowercase() }.getOrNull() ?: return@mapNotNull null
            val t = File(z, "temp")
            if (runCatching { t.readText().trim().toFloat() }.isSuccess) type to t else null
        }
        return readable.firstOrNull { (type, _) -> listOf("cpu", "soc", "tsens", "cluster", "apc").any { type.contains(it) } && !type.contains("gpu") }?.second
    }
}
