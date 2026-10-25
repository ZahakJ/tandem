package io.github.zahakj.tandem.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import io.github.zahakj.tandem.data.Store
import java.io.File

/**
 * System and collection art for the icon grid and Spotlight. A thin front for [ThemePacks], which reads Cocoon
 * smart-folder packs (`ROMs/assets/smart_folders/<pack>/smart_folders/by_platform/<system>/{icon,hero,logo}.png`)
 * as well as the art inside theme packs, and honours the Look.iconPack choice and each pack's switches.
 */
object IconPacks {
    /**
     * Changes whenever the art in use may have changed, so screens that looked pictures up look again. The theme and
     * accent are in it too: built-in folders are drawn in the theme's colours (FolderLooks.kt).
     */
    val version: Int
        get() {
            val look = Store.settings.look
            return listOf(System.identityHashCode(ThemePacks.packs), System.identityHashCode(ThemePacks.assetPacks), look.iconPack, look.theme, look.packParts, look.accent).hashCode()
        }

    fun systemIcon(platformId: String): File? = ThemePacks.systemIcon(platformId)
    fun systemHero(platformId: String): File? = ThemePacks.systemHero(platformId)
    fun systemLogo(platformId: String): File? = ThemePacks.systemLogo(platformId)

    /** A collection's smart-folder art: [kind] is a collection id ("fav", "most", "added", "continue") or a pack folder name. */
    fun collectionIcon(kind: String): File? = ThemePacks.collectionIcon(kind)
    fun collectionHero(kind: String): File? = ThemePacks.collectionHero(kind)

    /** Smart-folder art for a home folder ([names] from [FolderArtNames]): icon, hero and logo. */
    fun folderIcon(names: List<String>): File? = ThemePacks.folderArt(names, "icon")
    fun folderHero(names: List<String>): File? = ThemePacks.folderArt(names, "hero")
    fun folderLogo(names: List<String>): File? = ThemePacks.folderArt(names, "logo")

    private val prints = HashMap<String, Long>()

    /** A cheap content fingerprint (size and the first 64 KB), so two pack pictures of the same image count as one. */
    fun fingerprint(f: File): Long = prints.getOrPut(f.path) {
        runCatching {
            val crc = java.util.zip.CRC32()
            f.inputStream().use { s -> val b = ByteArray(65536); val n = s.read(b); if (n > 0) crc.update(b, 0, n) }
            crc.value xor (f.length() shl 32)
        }.getOrDefault(f.length())
    }

    /** True when some pack's art is in use (reading it makes a composable follow pack changes). */
    val ready: Boolean get() = ThemePacks.artSource != null
}

/** Reads the packs again (off the main thread) once when a screen that shows pack art opens. */
@Composable
fun IconPacksDriver() {
    LaunchedEffect(Unit) { ThemePacks.refresh() }
}

/**
 * Which smart-folder art a home folder wears: the pack folder names to try, from its [HomeFolder.art] or its name.
 * Some packs have by_platform/{android_app, androidapps, androidgames, emulators, steam, windows...}.
 */
object FolderArtNames {
    private val apps = listOf("android_app", "androidapps", "apps")
    private val emulators = listOf("emulators", "emulator")
    private val byName: Map<String, List<String>> = mapOf(
        "apps" to apps, "androidapps" to apps, "androidapp" to apps, "myapps" to apps, "applications" to apps,
        "emulators" to emulators, "emulator" to emulators, "emus" to emulators,
        "androidgames" to listOf("androidgames", "android_games"),
        "steam" to listOf("steam"), "windows" to listOf("windows", "pc"), "pcgames" to listOf("windows", "pc"),
    )

    fun of(f: io.github.zahakj.tandem.data.HomeFolder): List<String> {
        f.art?.takeIf { it.isNotBlank() }?.let { a -> return (listOf(a) + byName[PackArt.norm(a)].orEmpty()).distinct() }
        return byName[PackArt.norm(f.name)].orEmpty()
    }
}
