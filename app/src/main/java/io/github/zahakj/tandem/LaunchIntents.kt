package io.github.zahakj.tandem

import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.SystemClock
import android.view.Display
import io.github.zahakj.tandem.data.AppPrefs
import io.github.zahakj.tandem.data.AppScreen
import io.github.zahakj.tandem.data.SystemPrefs
import java.io.File

/** Something that stops a game from starting, explained in plain words by Pip. */
sealed interface LaunchProblem {
    val game: Game

    /** None of the system's emulators is installed; [suggested] is the one to get. */
    data class NoEmulator(override val game: Game, val suggested: LaunchSpec?) : LaunchProblem
    /** The emulator is installed but refused to open (moved activity, old version, or blocked). */
    data class WontOpen(override val game: Game, val spec: LaunchSpec, val detail: String?) : LaunchProblem
    data class FileMissing(override val game: Game) : LaunchProblem
    /** A .dpt/.pcgame style shortcut without the id the emulator needs. */
    data class BadShortcut(override val game: Game, val detail: String) : LaunchProblem
    /** An Android game that was uninstalled. */
    data class AppMissing(override val game: Game) : LaunchProblem
}

/** Thrown while building an intent when the game file can't be turned into what the emulator needs. */
class BadShortcutException(message: String) : Exception(message)

/**
 * Turns a [LaunchSpec] and a game into an intent. Templates in extras and data:
 * - {file.path} absolute path, {file.name}, {file.stem} (name without extension), {file.ext}, {file.dir}
 * - {file.uri} a link the emulator can open (a shared document link when Tandem has folder access, else file://)
 * - {file.fileuri} always file://
 * - {file.id} the id inside a small shortcut file, e.g. "PCSA00011" from "[vita_game_id] PCSA00011" or a GameNative app id
 * - {file.text} the shortcut file's first line, as written
 * - {storage} internal storage (/storage/emulated/0), {roms} the ROMs folder, {package} the emulator's package
 */
object LaunchIntents {
    private val TOKEN = Regex("""\{([A-Za-z0-9_.]+)\}""")

    fun build(spec: LaunchSpec, game: Game, sharedUri: Uri): Intent {
        val file = game.file
        val vars = Vars(spec, file, sharedUri)
        val intent = Intent()
        if (spec.component != LaunchSpec.ANY_APP) {
            intent.component = ComponentName.unflattenFromString(spec.component)
                ?: throw IllegalArgumentException("Bad component ${spec.component}")
        }
        intent.action = spec.action ?: if (spec.component == LaunchSpec.ANY_APP) Intent.ACTION_VIEW else null
        spec.categories.forEach { intent.addCategory(it) }
        intent.addFlags(spec.flags)
        var data: Uri? = null
        when {
            spec.game == "data" -> data = sharedUri
            spec.game == "data-file" -> data = Uri.fromFile(file)
            spec.game.startsWith("extra-uri:") -> intent.putExtra(spec.game.substringAfter(':'), sharedUri.toString())
            spec.game.startsWith("extra-path:") -> intent.putExtra(spec.game.substringAfter(':'), file.path)
        }
        spec.data?.let { data = Uri.parse(vars.expand(it)) }
        // setData and setType each clear the other, so they're set together.
        val type = spec.type?.let(vars::expand)?.takeIf { it.isNotBlank() }
        when {
            data != null && type != null -> intent.setDataAndType(data, type)
            data != null -> intent.data = data
            type != null -> intent.type = type
        }
        for ((key, extra) in spec.extras) {
            val values = extra.values.map(vars::expand)
            val one = values.firstOrNull().orEmpty()
            fun number(): String = one.trim().ifEmpty { throw BadShortcutException("\"$key\" is empty") }
            when (extra.type) {
                "strings" -> intent.putExtra(key, values.toTypedArray())
                "int" -> intent.putExtra(key, number().toIntOrNull() ?: throw BadShortcutException("\"$one\" isn't a number"))
                "long" -> intent.putExtra(key, number().toLongOrNull() ?: throw BadShortcutException("\"$one\" isn't a number"))
                "float" -> intent.putExtra(key, number().toFloatOrNull() ?: throw BadShortcutException("\"$one\" isn't a number"))
                "bool" -> intent.putExtra(key, one.trim().equals("true", ignoreCase = true))
                "uri" -> intent.putExtra(key, Uri.parse(one))
                else -> intent.putExtra(key, one)
            }
        }
        return intent
    }

    private class Vars(val spec: LaunchSpec, val file: File, val sharedUri: Uri) {
        private val text by lazy { GameFiles.firstLine(file) }
        private val tags by lazy { GameFiles.tags(file) }

        fun expand(template: String): String = TOKEN.replace(template) { m ->
            when (val name = m.groupValues[1]) {
                "file.path" -> file.path
                "file.name" -> file.name
                "file.stem" -> file.nameWithoutExtension
                "file.ext" -> file.extension
                "file.dir" -> file.parent.orEmpty()
                "file.uri" -> sharedUri.toString()
                "file.fileuri" -> Uri.fromFile(file).toString()
                "file.text" -> text ?: throw BadShortcutException("${file.name} is empty")
                "file.id" -> GameFiles.idIn(text) ?: throw BadShortcutException("${file.name} has no game id inside")
                "file.mime" -> GameFiles.mime(file)
                "storage" -> Environment.getExternalStorageDirectory().path
                "roms" -> Library.romsDir.path
                "package" -> spec.packageName
                else -> if (name.startsWith("tags.")) {
                    // A "[name] value" line in a Daijisho-style template file; a plain id file works too.
                    tags[name.removePrefix("tags.").lowercase()] ?: GameFiles.idIn(text)
                        ?: throw BadShortcutException("${file.name} has no [${name.removePrefix("tags.")}] line")
                } else {
                    m.value
                }
            }
        }
    }
}

/** Reading the small text files some systems use instead of real games (Vita .dpt/.psvita, Windows .pcgame/.steam). */
object GameFiles {
    /** First meaningful line, or null when the file is empty, unreadable or too big to be a shortcut. */
    fun firstLine(file: File): String? {
        if (!file.isFile || file.length() > 64 * 1024) return null
        return runCatching {
            file.bufferedReader().useLines { lines -> lines.map { it.trim().removePrefix("﻿") }.firstOrNull { it.isNotEmpty() && !it.startsWith("#") } }
        }.getOrNull()
    }

    /** "[name] value" lines (Daijisho player template files), keyed by lowercase name. */
    fun tags(file: File): Map<String, String> {
        if (!file.isFile || file.length() > 64 * 1024) return emptyMap()
        val line = Regex("""^\s*\[([^\]]+)]\s*(.*?)\s*$""")
        return runCatching {
            file.readLines().mapNotNull { line.find(it) }.associate { it.groupValues[1].trim().lowercase() to it.groupValues[2] }
        }.getOrDefault(emptyMap())
    }

    fun mime(file: File): String =
        android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase()) ?: "application/octet-stream"

    /** "[vita_game_id] PCSA00011" -> "PCSA00011", "app_id=123" -> "123", "1594838860" -> itself. */
    fun idIn(line: String?): String? {
        if (line == null) return null
        val stripped = line.replace(Regex("""\[[^\]]*\]"""), " ")
        return stripped.split(Regex("""[\s=:,;]+""")).lastOrNull { it.isNotBlank() }?.trim()
    }
}

/** Which screen a game starts on. The Thor's bottom screen is the device's second display. */
object LaunchDisplay {
    /** The second screen, if there is one: a presentation display first, else any non-default display. */
    fun secondary(ctx: Context): Display? {
        val dm = ctx.getSystemService(DisplayManager::class.java) ?: return null
        return dm.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION).firstOrNull()
            ?: dm.displays.firstOrNull { it.displayId != Display.DEFAULT_DISPLAY }
    }

    /** True when games may use the second screen: there is one, and single screen mode is off. */
    fun dualScreen(ctx: Context): Boolean = !SystemPrefs.data.singleScreen && secondary(ctx) != null

    /**
     * Whether [game] asks for the bottom screen: its own choice, else (an Android game) the app's saved screen, else
     * the folder it is opened from, else its system's.
     */
    fun wantsBottom(game: Game): Boolean = when (
        game.meta.screen ?: (if (game.platform.isApps) AppPrefs.of(AndroidApps.packageOf(game)).screen?.takeIf { it != AppScreen.AUTO } else null)
            ?: io.github.zahakj.tandem.ui.SmartFolders.screenFor(game)
    ) {
        "bottom" -> true
        "top" -> false
        else -> SystemPrefs.config(game.platform.id).bottomScreen
    }

    /**
     * Whether app [pkg] opens on the bottom screen: its saved screen, else the default for apps. Auto opens it on the
     * screen it was started from ([LaunchFrom]).
     */
    fun appWantsBottom(pkg: String): Boolean = when (AppPrefs.screen(pkg)) {
        AppScreen.BOTTOM -> true
        AppScreen.TOP -> false
        else -> LaunchFrom.bottom()
    }

    /** Start options for the top or bottom screen; null with one screen. */
    fun appOptions(ctx: Context, bottom: Boolean): Bundle? {
        if (bottom) return optionsFor(ctx, true)
        if (!dualScreen(ctx)) return null
        return ActivityOptions.makeBasic().setLaunchDisplayId(Display.DEFAULT_DISPLAY).toBundle()
    }

    /** The display id of the bottom ([bottom]) or top screen; null when there is no bottom one. */
    fun displayId(ctx: Context, bottom: Boolean): Int? = if (bottom) secondary(ctx)?.displayId else Display.DEFAULT_DISPLAY

    /** Start options for the bottom screen, or null to start normally (top screen, or single screen fallback). */
    fun optionsFor(ctx: Context, bottom: Boolean): Bundle? {
        if (!bottom || SystemPrefs.data.singleScreen) return null
        val display = secondary(ctx) ?: return null
        // Its focus down there is wanted: Tandem mustn't take it back (FocusGuard).
        FocusGuard.bottomAppStarted = true
        return ActivityOptions.makeBasic().setLaunchDisplayId(display.displayId).toBundle()
    }
}

/**
 * Where the last input came from, so an app set to Auto opens on the screen it was started from: a touch on the
 * bottom screen opens it there, a touch on the top one up there, and the controller on the screen the library is on
 * (the bottom one while the screens are swapped, or while another app has the top one: [LibraryGuest]).
 */
object LaunchFrom {
    private const val TOUCH_MS = 3_000L

    @Volatile private var touchAt = 0L
    @Volatile private var touchBottom = false
    @Volatile private var keyAt = 0L

    /** A touch on the top ([bottom] false) or the bottom screen. */
    fun touch(bottom: Boolean) {
        touchAt = SystemClock.uptimeMillis()
        touchBottom = bottom
    }

    /** A button or key press. */
    fun key() {
        keyAt = SystemClock.uptimeMillis()
    }

    /** True when the latest input was a recent touch, not a button press. */
    fun touched(): Boolean = touchAt > keyAt && SystemClock.uptimeMillis() - touchAt < TOUCH_MS

    /** True when what starts now was started from the bottom screen. */
    fun bottom(): Boolean = if (touched()) touchBottom else io.github.zahakj.tandem.ui.ScreenRoles.libraryOnBottom || LibraryGuest.up
}
