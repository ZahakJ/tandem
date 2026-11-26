package io.github.zahakj.tandem.data

/**
 * Takes what a bug report must never carry out of a log line or a summary: secrets ([Redactor.secrets]: account
 * keys, passwords, tokens and user names Tandem knows, written out wherever they appear), the names of hidden games
 * ([Redactor.hidden], titles and file names), e-mail addresses, IP addresses, the values of key=, token=,
 * password=-like parameters, long tokens, content:// links, and in every file path the names of its folders and
 * files beyond those Tandem knows ([Redactor.safeNames]: ROMs, tandem, system folders…), so neither a game's name
 * nor a user's shows. Pure, so it is unit tested.
 */
class Redactor(
    secrets: Collection<String> = emptyList(),
    hidden: Collection<String> = emptyList(),
    safeNames: Collection<String> = emptyList(),
) {
    private val secrets = secrets.map { it.trim() }.filter { it.length >= 3 }.distinct().sortedByDescending { it.length }
    private val hidden = hidden.map { it.trim() }.filter { it.length >= 2 }.distinct().sortedByDescending { it.length }
    private val safe = (SAFE + safeNames.map { it.lowercase() }).toSet()

    fun line(text: String): String {
        var t = text
        for (s in secrets) t = t.replace(s, SECRET, ignoreCase = true)
        for (h in hidden) t = t.replace(h, HIDDEN, ignoreCase = true)
        t = CONTENT.replace(t) { m -> m.groupValues[1] + "/" + REDACTED }
        t = URL_USER.replace(t) { m -> m.groupValues[1] + REDACTED + "@" }
        t = EMAIL.replace(t, "<email>")
        t = BEARER.replace(t) { m -> m.groupValues[1] + REDACTED }
        t = PARAM.replace(t) { m -> m.groupValues[1] + m.groupValues[2] + REDACTED }
        t = IPV4.replace(t, "<ip>")
        t = IPV6.replace(t, "<ip>")
        t = MAC.replace(t, "<mac>")
        t = PATH.replace(t) { m -> path(m.value) }
        t = TOKEN.replace(t, REDACTED)
        return t
    }

    fun text(text: String): String = text.lineSequence().joinToString("\n") { line(it) }

    /** A path with every name Tandem doesn't know replaced, keeping a file's extension: /storage/emulated/0/ROMs/psx/<name>.chd. */
    private fun path(p: String): String {
        val parts = p.split('/')
        return parts.mapIndexed { i, part ->
            when {
                part.isBlank() -> part
                part.lowercase() in safe -> part
                part.all { it.isDigit() } -> part
                // An SD card's volume id (1A2B-3C4D) says nothing about anyone.
                VOLUME.matches(part) -> part
                // A file keeps its kind: "<name>.chd".
                i == parts.lastIndex && EXT.containsMatchIn(part) -> "<name>." + EXT.findAll(part).last().groupValues[1]
                else -> "<name>"
            }
        }.joinToString("/")
    }

    companion object {
        const val REDACTED = "<redacted>"
        const val SECRET = "<secret>"
        const val HIDDEN = "<hidden game>"

        /** Folder and file names that are Android's, Tandem's or a frontend's, never a person's or a game's. */
        val SAFE = setOf(
            "storage", "emulated", "sdcard", "self", "primary", "data", "user", "user_de", "mnt", "media", "android", "obb",
            "files", "cache", "code_cache", "shared_prefs", "databases", "app", "system", "vendor", "product", "apex", "proc",
            "dev", "lib", "lib64", "framework", "home", "tmp", "etc", "roms", "tandem", "reports", "downloaded_media", "manuals", "notes",
            "screenshots", "clips", "backups", "backup", "platforms", "themes", "bios", "saves", "states", "covers", "box2dfront",
            "hero", "logo", "logos", "marquees", "wheel", "fanart", "screenshot_title", "titlescreens", "videos", "icon",
            "io.github.zahakj.tandem", "base.apk", "platforms.json", "settings.json", "library.json", "home.json",
            "sessions.json", "icon-grid.json", "download", "documents", "pictures", "music", "movies", "dcim", "0",
        )
        private val CONTENT = Regex("""(content://[A-Za-z0-9._-]+)/\S+""")
        private val URL_USER = Regex("""(\b[a-z][a-z0-9+.-]*://)[^\s/@:]+(?::[^\s/@]*)?@""", RegexOption.IGNORE_CASE)
        private val EMAIL = Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}""")
        private val PARAM = Regex(
            """\b((?:api[_-]?key|apikey|key|token|access[_-]?token|auth|authorization|password|passwd|pwd|secret|sspassword|devpassword|ssid|username|user|login|steamid|steamkey|client[_-]?secret)\b\s*)([=:]\s*"?)(?!(?:Bearer|Basic|OAuth)\s|<)[^\s&",;]+""",
            RegexOption.IGNORE_CASE,
        )
        private val BEARER = Regex("""(\b(?:Bearer|Basic|OAuth)\s+)[A-Za-z0-9._~+/=-]+""", RegexOption.IGNORE_CASE)
        private val IPV4 = Regex("""\b(?:\d{1,3}\.){3}\d{1,3}\b""")
        private val IPV6 = Regex("""\b(?:[0-9a-fA-F]{1,4}:){5,7}[0-9a-fA-F]{1,4}\b|(?<![\w:])(?:[0-9a-fA-F]{1,4}:)+:(?:[0-9a-fA-F]{1,4}:?)*[0-9a-fA-F]{0,4}""")
        private val MAC = Regex("""\b(?:[0-9a-fA-F]{2}:){5}[0-9a-fA-F]{2}\b""")
        /** A file path. Its names may hold spaces ("Final Fantasy VII (USA).chd"): it ends at a comma, a quote or the line's end. */
        private val PATH = Regex("""(?<![\w.:/])/(?:[^/:"'<>|,;=\n]+/)*[^/:"'<>|,;=\n]*""")
        private val EXT = Regex("""\.([A-Za-z0-9]{1,5})(?![A-Za-z0-9])""")
        private val VOLUME = Regex("""[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}""")
        /** Long runs of letters and digits mixed (keys, tokens, hashes): 24 characters or more, with a digit and a letter. */
        private val TOKEN = Regex("""\b(?=[A-Za-z0-9_-]*\d)(?=[A-Za-z0-9_-]*[A-Za-z])[A-Za-z0-9_-]{24,}\b""")
    }
}

/** Which lines of a `logcat -v threadtime` dump are Tandem's own: its tags (Tandem, TandemPerf…) and its crashes. Pure. */
object ReportLog {
    /** "11-26 12:30:45.123  1234  1250 W Tandem  : message": the tag. */
    private val HEAD = Regex("""^\d\d-\d\d \d\d:\d\d:\d\d\.\d+\s+\d+\s+\d+ [VDIWEFA] (.+?)\s*: ?""")

    fun tagOf(line: String): String? = HEAD.find(line)?.groupValues?.get(1)?.trim()

    fun ours(lines: List<String>): List<String> = lines.filter { l ->
        val tag = tagOf(l) ?: return@filter false
        tag.startsWith("Tandem") || tag == "AndroidRuntime"
    }
}
