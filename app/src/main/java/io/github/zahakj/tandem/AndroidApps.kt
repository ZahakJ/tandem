package io.github.zahakj.tandem

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.util.Log
import io.github.zahakj.tandem.data.AppPrefs
import java.io.File

/** Installed Android apps: the Apps screen, and the "Android Games" system built from them. */
object AndroidApps {
    const val PLATFORM_ID = "android"

    /** Packages of every emulator in platforms.json; kept out of Android Games unless added by hand. */
    @Volatile private var knownEmulators: Set<String> = emptySet()

    /** Whether [pkg] is an emulator named in platforms.json (or an imported platform file). */
    fun isEmulator(pkg: String): Boolean = pkg in knownEmulators

    data class App(val packageName: String, val label: String, val isGame: Boolean)

    /** Every app with a launcher icon, except Tandem itself, sorted by name. Call off the main thread. */
    fun list(ctx: Context): List<App> {
        val pm = ctx.packageManager
        val games = gamePackages(ctx)
        val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return runCatching { pm.queryIntentActivities(main, 0) }.getOrDefault(emptyList())
            .map { it.activityInfo.applicationInfo }
            .filter { it.packageName != ctx.packageName }
            .distinctBy { it.packageName }
            .map { App(it.packageName, AppPrefs.name(it.packageName) ?: it.loadLabel(pm).toString(), it.packageName in games) }
            .sortedBy { it.label.lowercase() }
    }

    /**
     * Packages in Android Games: apps that call themselves games, plus ones the user added, minus ones they took
     * out (Treat as game, kept in [AppPrefs]). Emulators often call themselves games too; those stay out unless added
     * by hand.
     */
    fun gamePackages(ctx: Context, emulators: Set<String> = knownEmulators): Set<String> {
        AppPrefs.init(ctx)
        val chosen = AppPrefs.data.apps.filterValues { it.game != null }
        val added = chosen.filterValues { it.game == true }.keys
        val removed = chosen.filterValues { it.game == false }.keys
        val pm = ctx.packageManager
        val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val launchable = runCatching { pm.queryIntentActivities(main, 0) }.getOrDefault(emptyList())
            .map { it.activityInfo.applicationInfo }.distinctBy { it.packageName }
        val homes = homeApps(ctx)
        val detected = launchable
            .filter { isGame(it) && it.packageName !in emulators && !notAGame(it, homes) }
            .map { it.packageName }
        val installed = launchable.map { it.packageName }.toSet()
        return (detected + added.filter { it in installed }).toSet() - removed - ctx.packageName
    }

    /** Packages that can be the home screen (launchers, other frontends): never games, even when they say so. */
    private fun homeApps(ctx: Context): Set<String> {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return runCatching { ctx.packageManager.queryIntentActivities(home, 0) }.getOrDefault(emptyList())
            .map { it.activityInfo.packageName }.toSet()
    }

    /**
     * Apps that call themselves games but aren't one: launchers and frontends (some tag themselves as games),
     * system apps, emulators that aren't in platforms.json, and Tandem. Only for detection: the user can still add
     * any of them by hand in Systems.
     */
    private fun notAGame(ai: ApplicationInfo, homes: Set<String>): Boolean {
        val pkg = ai.packageName.lowercase()
        if (ai.packageName in homes) return true
        if ((ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0 && (ai.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0) return true
        if (pkg.split('.', '_', '-').any { it in NOT_GAME_WORDS }) return true
        return NOT_GAME_PACKAGES.any { pkg == it || pkg.startsWith("$it.") }
    }

    /** Words in a package name that mark a launcher, frontend or emulator. */
    private val NOT_GAME_WORDS = setOf(
        "launcher", "frontend", "emu", "emulator", "retroarch", "libretro", "ppsspp", "dolphinemu", "citra", "yuzu",
        "aethersx2", "nethersx2", "duckstation", "melonds", "drastic", "mupen64plusae", "flycast", "redream", "vita3k",
        "winlator", "lemuroid", "mgba", "lime3ds", "azahar", "sudachi",
    )

    /** Frontends, launchers and device tools that don't say so in their package name. */
    private val NOT_GAME_PACKAGES = listOf(
        "com.magneticchen.daijishou", "org.es_de", "com.unbrokensoftware.launchbox", "com.droiddeck", "com.ayn",
        "com.odin", "com.ayaneo", "com.retroid", "com.valvesoftware", "io.github.zahakj.tandem",
    )

    @Suppress("DEPRECATION")
    private fun isGame(ai: ApplicationInfo): Boolean =
        ai.category == ApplicationInfo.CATEGORY_GAME || (ai.flags and ApplicationInfo.FLAG_IS_GAME) != 0

    /** The Android Games system. Its "files" are stand-ins named after the package, e.g. /android/com.foo.app. */
    fun platform(ctx: Context, emulators: Set<String>): Platform {
        knownEmulators = emulators
        val app = ctx.applicationContext
        return Platform(
            id = PLATFORM_ID,
            name = "Android Games",
            shortName = "Android",
            folders = emptyList(),
            extensions = emptySet(),
            skipDirs = emptySet(),
            artIds = listOf("android", "androidgames"),
            allPlayers = listOf(LaunchSpec("Android", "android/app", null, "app", emptyMap())),
            source = { p -> games(app, p, emulators) },
            fallbackArt = { g -> AppPrefs.art(packageOf(g))?.let(::File)?.takeIf { it.isFile } ?: iconFile(app, packageOf(g)).takeIf { it.isFile } },
        )
    }

    fun packageOf(game: Game): String = game.file.name.removeSuffix(".app")

    private fun games(ctx: Context, platform: Platform, emulators: Set<String>): List<Game> {
        val pm = ctx.packageManager
        return gamePackages(ctx, emulators).mapNotNull { pkg ->
            val info = runCatching { pm.getApplicationInfo(pkg, 0) }.getOrNull() ?: return@mapNotNull null
            // Box art for the grid; only written when the app was installed or updated since last time.
            ensureIconFile(ctx, pkg)
            Game(platform, File("/android/$pkg.app"), AppPrefs.name(pkg) ?: info.loadLabel(pm).toString(), group = null)
        }
    }

    // ---- Icons ----

    private fun render(ctx: Context, pkg: String, sizePx: Int): Bitmap? = runCatching {
        val d = ctx.packageManager.getApplicationIcon(pkg)
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        d.setBounds(0, 0, sizePx, sizePx)
        d.draw(Canvas(bmp))
        bmp
    }.getOrNull()

    private fun iconFile(ctx: Context, pkg: String) = File(ctx.cacheDir, "app-icons/$pkg.png")

    private fun ensureIconFile(ctx: Context, pkg: String) {
        val f = iconFile(ctx, pkg)
        val updated = runCatching { ctx.packageManager.getPackageInfo(pkg, 0).lastUpdateTime }.getOrDefault(0L)
        if (f.isFile && f.lastModified() >= updated) return
        val bmp = render(ctx, pkg, 256) ?: return
        f.parentFile?.mkdirs()
        runCatching { f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) } }
            .onFailure { Log.w("Tandem", "Couldn't save icon for $pkg", it) }
    }

    // ---- Launching ----

    /**
     * Opens [pkg] the way the home screen does, so a running app comes back where it was. [options] can put it on
     * the other screen. Returns false when the app has nothing to open.
     */
    fun open(ctx: Context, pkg: String, options: Bundle?): Boolean {
        val pm = ctx.packageManager
        val intent = pm.getLaunchIntentForPackage(pkg) ?: pm.getLeanbackLaunchIntentForPackage(pkg) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        return try {
            ctx.startActivity(intent, options)
            true
        } catch (e: Exception) {
            Log.w("Tandem", "Couldn't open $pkg", e)
            false
        }
    }

    /** Opens the system's App info page for [pkg]. */
    fun openInfo(ctx: Context, pkg: String) {
        runCatching {
            ctx.startActivity(
                Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:$pkg"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    fun isLaunchable(ctx: Context, pkg: String): Boolean =
        ctx.packageManager.let { it.getLaunchIntentForPackage(pkg) != null || it.getLeanbackLaunchIntentForPackage(pkg) != null }
}
