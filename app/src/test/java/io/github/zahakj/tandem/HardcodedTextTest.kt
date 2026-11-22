package io.github.zahakj.tandem

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Files whose text is already in the string resources stay that way: no new words written straight into the code of
 * a file listed in [extracted]. It reads the source and looks at the calls that show text (Text, Toasts.show,
 * ButtonHint, MenuAction…): a string literal with a letter in it, given to one of them, fails. Ids, keys and the
 * English names of settings rows (en = "…", keywords = "…") are not shown, so they pass, and so do symbols ("›",
 * "✓") and templates of numbers ("$n%"). A line that really must keep a literal says why with `// tx-ok: why`.
 * docs/TRANSLATING.md.
 */
class HardcodedTextTest {
    /** Converted files, relative to the source root. Add each file once its extraction is done. */
    private val extracted = listOf(
        "ui/StartPanel.kt",
        "ui/QuickPanel.kt",
        "ui/SidePanel.kt",
        "ui/LanguageSettings.kt",
        "ui/SettingsCatalog.kt", "ui/Settings.kt", "ui/SettingsSearch.kt", "ui/SettingsHome.kt", "ui/Choices.kt", "ui/Showcase.kt",
        "ui/SoundSettings.kt", "ui/Sound.kt", "audio/Sfx.kt", "audio/Calm.kt", "audio/Meadow.kt", "audio/Tracks.kt", "audio/Song.kt",
        "ui/Appearance.kt", "ui/Theme.kt", "ui/ThemeGallery.kt", "ui/ThemePacks.kt", "ui/ThemePacksScreen.kt", "ui/PackImport.kt",
        "ui/Flair.kt", "ui/Presets.kt", "ui/Perf.kt", "ui/StartFlow.kt", "ui/Welcome.kt",
        "ui/SystemFolders.kt",
        "data/GridTree.kt",
        "data/IconGridStore.kt",
        "data/SmartRule.kt",
        "ui/SmartFolders.kt",
        "ui/GridDrag.kt",
        "ui/GridPickers.kt",
        "ui/GridSelect.kt",
        "ui/GridSpots.kt",
        "ui/FolderDesign.kt",
        "ui/SmartFolderForm.kt",
        "ui/IconGrid.kt",
        "ui/Overlays.kt",
        "ui/ActionMenu.kt",
        "ui/AppActionMenu.kt",
        "ui/HoldConfirm.kt",
        "ui/TextEntry.kt",
        "Selection.kt",
        "ui/LayoutPrefs.kt",
        "ui/HeroArt.kt",
        "ui/HomeExtras.kt",
        "ui/QuickFind.kt",
        "ui/QuickJump.kt",
        "ui/HomeMode.kt",
        "ui/BrowseBar.kt",
        "Collections.kt",
        "ui/Layouts.kt",
        "ui/Screens.kt",
        "ui/Spotlight.kt",
        "ui/SpotlightHome.kt",
        "ui/Bookshelf.kt",
        "ui/Switcher.kt",
        "ui/App.kt",
    )

    /** Calls that show their string arguments, and how many leading arguments are ids, not text. */
    private val shows = mapOf(
        "Text" to 0, "ShrinkText" to 0, "Toasts.show" to 0, "Toasts.Action" to 0, "Pill" to 0, "ValuePill" to 0,
        "PillButton" to 0, "SectionTitle" to 0, "SettingsHeader" to 0, "ButtonHint" to 1, "MenuAction" to 1, "QuickTile" to 1,
        "LevelCard" to 0, "TextEntry.ask" to 0, "Undo.offer" to 0, "Confirm.ask" to 0, "SettingRow" to 0, "SettingSection" to 0,
        "choice" to 0, "toggle" to 0, "number" to 0, "Selected.Setting" to 0, "PickOption" to 1, "StepButton" to 1,
    )

    /** Named arguments that are never shown. */
    private val unshown = setOf("en", "keywords", "key", "id", "route", "tag", "routeKey")

    private val src: File = listOf("src/main/java/io/github/zahakj/tandem", "app/src/main/java/io/github/zahakj/tandem").map(::File).first { it.isDirectory }

    @Test fun extractedFilesKeepTheirTextInResources() {
        val found = extracted.flatMap { path -> literalsShown(path, File(src, path).readText()) }
        assertTrue("Text written into converted files (put it in strings_<area>.xml, see docs/TRANSLATING.md):\n" + found.joinToString("\n"), found.isEmpty())
    }

    @Test fun extractedFilesUseResourcePlurals() {
        val found = extracted.flatMap { path ->
            File(src, path).readLines().withIndex().filter { (_, l) -> Regex("""\bplural\(""").containsMatchIn(l) && "tx-ok" !in l }
                .map { (i, l) -> "$path:${i + 1}: ${l.trim()}" }
        }
        assertTrue("plural() is English only: use a <plurals> and Tx.p:\n" + found.joinToString("\n"), found.isEmpty())
    }

    @Test fun theCheckCatchesWhatItShould() {
        val sample = """
            Text("Hello")
            Text(Tx.s(R.string.x))
            ButtonHint("A", "Choose", Palette.Mint)
            ButtonHint("A", Tx.s(R.string.common_choose), Palette.Mint)
            MenuAction("back", "Back", Glyph.Back) { }
            Toasts.show("Saved ${'$'}name")
            Toasts.show("${'$'}n%")
            Text("›", style = s)
            SettingRow(Tx.s(R.string.a), v, en = "Accent colour", keywords = "color tint")
            Text(if (on) "On" else "Off")
            Text(x, Modifier.semantics { contentDescription = "Close" })
            Text("Kept") // tx-ok: a test
        """.trimIndent()
        val hits = literalsShown("sample", sample).map { it.substringAfter(": ").substringBefore(" in ") }
        assertTrue(hits.toString(), hits == listOf("\"Hello\"", "\"Choose\"", "\"Back\"", "\"Saved ${'$'}name\"", "\"On\"", "\"Off\"", "\"Close\""))
    }

    /** Every user-facing literal in [text]: "path:line: "literal" in Call(". */
    private fun literalsShown(path: String, text: String): List<String> {
        val out = ArrayList<String>()
        val lines = text.lines()
        fun line(at: Int) = text.substring(0, at).count { it == '\n' }
        fun skip(at: Int) = "tx-ok" in lines[line(at)]
        val calls = Regex("""(?<![\w.])(${shows.keys.joinToString("|") { Regex.escape(it) }})\(""")
        for (m in calls.findAll(text)) {
            val name = m.groupValues[1]
            val args = arguments(text, m.range.last + 1)
            args.forEachIndexed { i, arg ->
                if (i < shows.getValue(name)) return@forEachIndexed
                val named = Regex("""^\s*(\w+)\s*=(?!=)""").find(arg.text)?.groupValues?.get(1)
                if (named != null && named in unshown) return@forEachIndexed
                for ((at, lit) in arg.literals) if (shown(lit) && !skip(at)) out += "$path:${line(at) + 1}: \"$lit\" in $name("
            }
        }
        // Accessibility labels are text too, wherever they are set.
        for (m in Regex("""contentDescription\s*=\s*"((?:[^"\\]|\\.)*)"""").findAll(text)) {
            if (shown(m.groupValues[1]) && !skip(m.range.first)) out += "$path:${line(m.range.first) + 1}: \"${m.groupValues[1]}\" in contentDescription"
        }
        return out.distinct()
    }

    /** A literal someone reads: a letter left once the templates (${'$'}x, ${'$'}{…}) are taken out. */
    private fun shown(lit: String): Boolean =
        lit.replace(Regex("""\$\{[^}]*}"""), "").replace(Regex("""\$\w+"""), "").replace(Regex("""\\[nt"'\\$]"""), "").any { it.isLetter() }

    private class Arg(val text: String, val literals: List<Pair<Int, String>>)

    /**
     * The arguments of the call whose "(" ends at [start]: each one's text, and the string literals written directly
     * in it (not inside a lambda or a nested call, which are checked on their own). A small Kotlin reader: strings
     * with templates, chars, comments.
     */
    private fun arguments(text: String, start: Int): List<Arg> {
        val args = ArrayList<Arg>()
        var argStart = start
        var lits = ArrayList<Pair<Int, String>>()
        var depth = 0 // parens, brackets and braces inside this call
        var i = start
        while (i < text.length) {
            val c = text[i]
            when {
                text.startsWith("//", i) -> { i = text.indexOf('\n', i).let { if (it < 0) text.length else it }; continue }
                text.startsWith("/*", i) -> { i = text.indexOf("*/", i).let { if (it < 0) text.length else it + 2 }; continue }
                c == '\'' -> { i = endOfChar(text, i); continue }
                c == '"' -> {
                    val end = endOfString(text, i)
                    if (depth == 0 && !text.startsWith("\"\"\"", i)) lits += i to text.substring(i + 1, end - 1)
                    i = end; continue
                }
                c == '(' || c == '[' || c == '{' -> depth++
                c == ')' || c == ']' || c == '}' -> {
                    if (depth == 0) { args += Arg(text.substring(argStart, i), lits); return args }
                    depth--
                }
                c == ',' && depth == 0 -> { args += Arg(text.substring(argStart, i), lits); lits = ArrayList(); argStart = i + 1 }
            }
            i++
        }
        return args
    }

    private fun endOfChar(text: String, at: Int): Int {
        var i = at + 1
        while (i < text.length && text[i] != '\'') i += if (text[i] == '\\') 2 else 1
        return i + 1
    }

    /** The index just past the string literal starting at [at], templates with nested strings included. */
    private fun endOfString(text: String, at: Int): Int {
        if (text.startsWith("\"\"\"", at)) return text.indexOf("\"\"\"", at + 3).let { if (it < 0) text.length else it + 3 }
        var i = at + 1
        while (i < text.length) {
            when {
                text[i] == '\\' -> i += 2
                text[i] == '"' -> return i + 1
                text.startsWith("\${", i) -> {
                    var d = 1
                    i += 2
                    while (i < text.length && d > 0) {
                        when (text[i]) {
                            '{' -> d++
                            '}' -> d--
                            '"' -> { i = endOfString(text, i) - 1 }
                        }
                        i++
                    }
                }
                else -> i++
            }
        }
        return i
    }
}
