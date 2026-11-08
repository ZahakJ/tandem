package io.github.zahakj.tandem.ui

import io.github.zahakj.tandem.audio.Sfx
import io.github.zahakj.tandem.data.Store
import java.io.File

/**
 * Recorded UI sounds: a folder of audio files named after what they are for, as in Cocoon sound packs
 * (`navigate.ogg`, `select.mp3`, `back.ogg`, `launch.ogg`...). [Look.soundFiles] picks the folder; each file stands in
 * for one or more of Tandem's sounds ([NAMES]), and the sounds a pack lacks still come from the chosen synthesized pack.
 */
object SoundFiles {
    /** File name (without extension) to the sounds it plays for. First match in the folder wins. */
    val NAMES: Map<String, List<Sfx>> = mapOf(
        "navigate" to listOf(Sfx.Move), "move" to listOf(Sfx.Move),
        "select" to listOf(Sfx.Select, Sfx.Confirm, Sfx.ToggleOn), "confirm" to listOf(Sfx.Confirm),
        "back" to listOf(Sfx.Back, Sfx.MenuClose, Sfx.ToggleOff),
        "launch" to listOf(Sfx.Launch),
        "notification" to listOf(Sfx.Toast, Sfx.Achievement, Sfx.Found),
        "folder_open" to listOf(Sfx.MenuOpen), "menu_open" to listOf(Sfx.MenuOpen),
        "folder_close" to listOf(Sfx.MenuClose), "menu_close" to listOf(Sfx.MenuClose),
        "grid_zoom_in" to listOf(Sfx.Page), "grid_zoom_out" to listOf(Sfx.Page), "page" to listOf(Sfx.Page),
        "favorite" to listOf(Sfx.Favorite), "error" to listOf(Sfx.Error),
        "saving_end" to listOf(Sfx.SaverOut),
    )

    /**
     * The folder that plays now: the theme pack's sounds while that part is on ([themeFolder]), else the sound pack
     * chosen in Sound & music, or null for synthesized sounds only. Reads the theme packs already loaded.
     */
    fun folder(): File? = themeFolder() ?: Store.settings.look.soundFiles.let { choice ->
        if (choice == "none") null else ThemePacks.soundPacks().firstOrNull { it.first == choice }?.third
    }

    /** The theme pack's own sounds, when it has some and they are on. */
    fun themeFolder(): File? =
        ThemePacks.byId(Store.settings.look.theme)?.takeIf { ThemePacks.parts(it.id).sounds }?.sounds

    /** Which file plays each sound in [dir], by [Sfx.ordinal] (null where the pack has none). */
    fun map(dir: File): Array<File?> {
        val out = arrayOfNulls<File>(Sfx.entries.size)
        val files = dir.listFiles().orEmpty().filter { it.isFile && it.extension.lowercase() in ThemePacks.audioExtensions }
            .associateBy { it.nameWithoutExtension.lowercase() }
        for ((name, kinds) in NAMES) {
            val f = files[name] ?: continue
            for (k in kinds) if (out[k.ordinal] == null) out[k.ordinal] = f
        }
        return out
    }
}
