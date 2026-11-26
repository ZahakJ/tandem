package io.github.zahakj.tandem.data

/*
 * Tandem's own art sets: a matching icon, hero and logo for every system, drawn on the handheld (ui/SetArt.kt) in one
 * of a few styles. This file is the plain part (no Android): the set ids and which art a system shows, so
 * ArtSetsTest checks the order of precedence.
 *
 * Where each piece is kept:
 * - the set for every system: Look.artSet (settings.json, the Look part of a backup, Share my look);
 * - one system's own set: FolderStyle.artSet of its tile "s:<id>" (icon-grid.json, the Layout part of a backup);
 * - pictures the user uploaded: FolderStyle.iconFile / heroFile / logoFile (copies in ROMs/tandem/art/folders);
 * - the drawn pictures themselves: a cache (cacheDir/art-sets), drawn again whenever it is missing.
 * None of these is touched by a rescan or by Icons from (Look.iconPack).
 */

/** Which art a system's icon, hero or logo comes from, strongest first. */
enum class ArtSource { Upload, Set, Pack, Drawn }

/** What one slot of a system shows: [set] for [ArtSource.Set], [file] for [ArtSource.Upload]. */
data class ArtPick(val source: ArtSource, val set: String? = null, val file: String? = null)

object ArtSets {
    const val PASTEL = "pastel"
    const val NEON = "neon"
    const val PAPER = "paper"
    const val MONO = "mono"

    /** Every set, in the order the pickers list them. */
    val ids: List<String> = listOf(PASTEL, NEON, PAPER, MONO)

    /** A system's own choice: no set here, even with a global one (the pack's art, else Tandem's drawn icon). */
    const val PACK = "pack"

    /** [Look.artSet] written into a shared look for "no set" (null there would mean "leave it as it is"). */
    const val OFF = "off"

    fun isSet(id: String?): Boolean = id != null && id in ids

    /** A valid per-system choice: a set id or [PACK]. */
    fun isChoice(id: String?): Boolean = isSet(id) || id == PACK

    /**
     * The set a system is drawn in: its own choice ([perSystem]) over the global one ([global]). [PACK] turns the
     * global set off for this system; an unknown id (from a newer Tandem, a hand-edited file) is ignored.
     */
    fun setFor(perSystem: String?, global: String?): String? = when {
        perSystem == PACK -> null
        isSet(perSystem) -> perSystem
        isSet(global) -> global
        else -> null
    }

    /**
     * What one slot shows, strongest first: a picture the user [upload]ed for it; else the system's set
     * ([setFor]); else the art pack's picture when it has one ([packHas]); else Tandem's drawn art.
     */
    fun pick(upload: String?, perSystem: String?, global: String?, packHas: Boolean): ArtPick {
        if (!upload.isNullOrBlank()) return ArtPick(ArtSource.Upload, file = upload)
        setFor(perSystem, global)?.let { return ArtPick(ArtSource.Set, set = it) }
        return if (packHas) ArtPick(ArtSource.Pack) else ArtPick(ArtSource.Drawn)
    }

    /** The upload a [style] keeps for a slot ("icon", "hero", "logo"), when that slot shows the user's picture. */
    fun upload(style: FolderStyle?, slot: String): String? = when (slot) {
        "icon" -> style?.iconFile?.takeIf { style.icon == FolderStyle.ICON_FILE }
        "hero" -> style?.heroFile?.takeIf { style.hero == FolderStyle.HERO_FILE }
        "logo" -> style?.logoFile?.takeIf { style.logo == FolderStyle.LOGO_FILE }
        else -> null
    }

    private val SYSTEM_ID = Regex("[a-z0-9_]{1,48}")

    /** Per-system sets from a shared look, kept only when the id and the choice are both ones Tandem knows. */
    fun sanitize(map: Map<String, String>?): Map<String, String>? =
        map?.filter { (k, v) -> SYSTEM_ID.matches(k) && isChoice(v) }?.entries?.take(300)?.associate { it.key to it.value }?.takeIf { it.isNotEmpty() }

    /** The per-system sets kept in [styles] (tile keys "s:<id>"), for Share my look. */
    fun perSystem(styles: Map<String, FolderStyle>): Map<String, String> =
        styles.mapNotNull { (k, st) -> if (k.startsWith("s:") && isChoice(st.artSet)) k.removePrefix("s:") to st.artSet!! else null }.toMap()

    /** [styles] with [sets] applied to the system tiles ("s:<id>"), each other part of their style kept. */
    fun applyPerSystem(styles: Map<String, FolderStyle>, sets: Map<String, String>): Map<String, FolderStyle> {
        val out = LinkedHashMap(styles)
        for ((id, set) in sets) {
            val key = "s:$id"
            val next = (styles[key] ?: FolderStyle.DEFAULT).copy(artSet = set)
            if (next.isDefault) out.remove(key) else out[key] = next
        }
        return out
    }
}
