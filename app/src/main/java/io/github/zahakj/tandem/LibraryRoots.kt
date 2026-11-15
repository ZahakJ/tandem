package io.github.zahakj.tandem

/**
 * The folders games are read from: the ROMs folder on internal storage (Tandem's own, always read, even if it
 * doesn't exist), and any the user picked in Settings › Library & art › Games › Game folders or from an empty home
 * (an SD card, a games folder anywhere on the device). A root can be a folder per system, one system's own folder or
 * a mix (GameFinder), and they all pour into the same systems. Pure: the folder picker's answers,
 * the checks on a new root and the merge are worked out here; Library.kt does the reading, and the roots
 * themselves sit in systems.json (SystemPrefs.roots) with the document links the picker granted (SystemPrefs.trees).
 */
object LibraryRoots {
    /** Android's provider for internal storage and SD cards; its document ids are "<volume>:<path>". */
    const val EXTERNAL_STORAGE = "com.android.externalstorage.documents"
    /** The Downloads provider; a folder on local storage picked there comes as "raw:/storage/...". */
    const val DOWNLOADS = "com.android.providers.downloads.documents"

    /**
     * The file path behind a folder the system picker returned: "primary:Games" is `<internal>/Games`,
     * "1234-ABCD:Games" is `/storage/1234-ABCD/Games` (an SD card). Null for a folder that isn't on local storage
     * (a cloud drive, say), which can't be read as files.
     */
    fun pathOf(authority: String?, treeDocumentId: String?, internal: String): String? {
        if (treeDocumentId == null) return null
        return when (authority) {
            EXTERNAL_STORAGE -> {
                val colon = treeDocumentId.indexOf(':')
                if (colon <= 0) return null
                val volume = treeDocumentId.substring(0, colon)
                // Volume names are letters, digits and dashes (primary, 1234-ABCD); anything else isn't a volume.
                if (!volume.all { it.isLetterOrDigit() || it == '-' || it == '_' }) return null
                val rel = treeDocumentId.substring(colon + 1).trim('/')
                val base = if (volume.equals("primary", ignoreCase = true)) internal.trimEnd('/') else "/storage/$volume"
                if (rel.isEmpty()) base else "$base/$rel"
            }
            DOWNLOADS -> treeDocumentId.takeIf { it.startsWith("raw:/") }?.removePrefix("raw:")?.trimEnd('/')?.ifEmpty { null }
            else -> null
        }
    }

    /**
     * True when the picker returned the ROMs folder on internal storage, or all of internal storage: its link is
     * then Tandem's link for sharing games on internal storage (Launcher), not another games folder.
     */
    fun isRomsTree(authority: String?, treeDocumentId: String?): Boolean {
        if (authority != EXTERNAL_STORAGE || treeDocumentId == null) return false
        if (!treeDocumentId.startsWith("primary:", ignoreCase = true)) return false
        val path = treeDocumentId.substringAfter(':').trim('/')
        return path.isEmpty() || path.equals("ROMs", ignoreCase = true)
    }

    /** Why a picked folder can't be a root, or [Ok]. */
    enum class Verdict { Ok, IsRoms, InsideRoms, AboveRoms, Already }

    /**
     * Whether [path] can be added next to [roms] and the [roots] already there. The ROMs folder, anything inside it
     * (already scanned) and anything above it (which would scan the ROMs folder twice) are refused, as is a folder
     * already added or inside one that is.
     */
    fun check(path: String, roms: String, roots: List<String>): Verdict {
        val p = tidy(path)
        val r = tidy(roms)
        return when {
            p.equals(r, ignoreCase = true) -> Verdict.IsRoms
            p.startsWith("$r/", ignoreCase = true) -> Verdict.InsideRoms
            r.startsWith("$p/", ignoreCase = true) -> Verdict.AboveRoms
            roots.any { val o = tidy(it); p.equals(o, ignoreCase = true) || p.startsWith("$o/", ignoreCase = true) } -> Verdict.Already
            else -> Verdict.Ok
        }
    }

    /**
     * A root's name for lists: the folder's name, with the storage it is on when that isn't internal storage
     * ("Games · SD card 1234-ABCD").
     */
    fun label(path: String, internal: String): String {
        val p = tidy(path)
        val name = p.substringAfterLast('/').ifEmpty { p }
        val where = where(p, internal)
        return if (where == null) name else "$name · $where"
    }

    /** "SD card 1234-ABCD" for a path on a removable volume; null on internal storage. */
    fun where(path: String, internal: String): String? {
        val p = tidy(path)
        val i = tidy(internal)
        if (p == i || p.startsWith("$i/")) return null
        val volume = Regex("^/storage/([^/]+)").find(p)?.groupValues?.get(1) ?: return null
        return if (volume == "emulated" || volume == "self") null else "SD card $volume"
    }

    /** The path as people read it: without the storage prefix ("Games/n3ds", "1234-ABCD/Games"). */
    fun shortPath(path: String, internal: String): String {
        val p = tidy(path)
        val i = tidy(internal)
        return when {
            p == i -> "Internal storage"
            p.startsWith("$i/") -> p.substring(i.length + 1)
            p.startsWith("/storage/") -> p.removePrefix("/storage/")
            else -> p
        }
    }

    /** True when [path] is a removable volume's path, which may be unmounted (an SD card taken out). */
    fun removable(path: String, internal: String): Boolean = where(path, internal) != null

    /**
     * Games from every root in one list, the ROMs folder's first: a game key found in two roots (the same file
     * name in both) keeps the first one, so what the main tree holds is never shadowed by a copy elsewhere.
     */
    fun <G> merge(perRoot: List<List<G>>, key: (G) -> String): List<G> = perRoot.flatten().distinctBy(key)

    /**
     * Where a game's art is looked for, in order: its own root's `downloaded_media`, then the ROMs folder's (where
     * scrapes save). A game in the ROMs folder looks only there.
     */
    fun artOrder(gameRoot: String?, main: String): List<String> =
        if (gameRoot == null || tidy(gameRoot).equals(tidy(main), ignoreCase = true)) listOf(main) else listOf(gameRoot, main)

    private fun tidy(path: String): String = path.replace('\\', '/').trimEnd('/')
}
