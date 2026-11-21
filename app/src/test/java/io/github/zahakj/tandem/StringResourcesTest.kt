package io.github.zahakj.tandem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.kxml2.io.KXmlParser
import org.xmlpull.v1.XmlPullParser
import java.io.File

/**
 * The string resources (res/values/strings_<area>.xml and their translations in values-ar, values-ja, values-zh-rCN,
 * values-fr): what a translation may hold, so a mistake fails here rather than as a crash or a wrong word on the
 * handheld. Missing translations are allowed (they show in English); the coverage is printed. docs/TRANSLATING.md.
 */
class StringResourcesTest {
    /** One `<string>` or `<plurals>`: its raw text (escapes as written), per quantity for plurals. */
    private class Entry(val name: String, val file: String, val texts: Map<String, String>, val plural: Boolean, val translatable: Boolean)

    private class Folder(val name: String, val entries: List<Entry>) {
        val byName = entries.associateBy { it.name }
        /** The language tag, "" for the default (English). */
        val tag: String get() = name.removePrefix("values").removePrefix("-").replace("-r", "-")
    }

    private val res: File = listOf("src/main/res", "app/src/main/res").map(::File).first { it.isDirectory }

    private val folders: List<Folder> by lazy {
        res.listFiles().orEmpty().filter { it.isDirectory && (it.name == "values" || it.name.startsWith("values-")) }
            .map { dir -> Folder(dir.name, dir.listFiles().orEmpty().filter { isStrings(it) }.sortedBy { it.name }.flatMap(::parse)) }
            .filter { it.name == "values" || it.entries.isNotEmpty() }
    }

    private val default get() = folders.first { it.name == "values" }
    private val translations get() = folders.filter { it.name != "values" }

    private fun isStrings(f: File) = f.isFile && f.name.endsWith(".xml") && (f.name == "strings.xml" || f.name.startsWith("strings_"))

    private fun parse(f: File): List<Entry> {
        val p = KXmlParser()
        p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        val out = ArrayList<Entry>()
        f.reader().use { reader ->
            p.setInput(reader)
            while (p.next() != XmlPullParser.END_DOCUMENT) {
                if (p.eventType != XmlPullParser.START_TAG) continue
                val translatable = p.getAttributeValue(null, "translatable") != "false"
                when (p.name) {
                    "string" -> { val name = p.getAttributeValue(null, "name"); out += Entry(name, f.name, mapOf("" to text(p)), false, translatable) }
                    "plurals" -> {
                        val name = p.getAttributeValue(null, "name")
                        val items = LinkedHashMap<String, String>()
                        while (!(p.next() == XmlPullParser.END_TAG && p.name == "plurals")) {
                            if (p.eventType == XmlPullParser.START_TAG && p.name == "item") items[p.getAttributeValue(null, "quantity")] = text(p)
                        }
                        out += Entry(name, f.name, items, true, translatable)
                    }
                }
            }
        }
        return out
    }

    /** The text of the element the parser is on, inner tags (xliff:g, b) and all, up to its end tag. */
    private fun text(p: KXmlParser): String {
        val sb = StringBuilder()
        var depth = 1
        while (depth > 0) {
            when (p.nextToken()) {
                XmlPullParser.START_TAG -> depth++
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.TEXT, XmlPullParser.CDSECT, XmlPullParser.ENTITY_REF -> sb.append(p.text)
            }
        }
        return sb.toString()
    }

    /** The format arguments in [s]: "1s", "2d"… (position and conversion); %% and %n are plain text. */
    private fun args(s: String): List<String> =
        Spec.findAll(s).filter { it.groupValues[3] !in setOf("%", "n") }.map { (it.groupValues[1].ifEmpty { "?" }) + it.groupValues[3] }.toList()

    // No space flag, so "100% free" is text, not an argument.
    private val Spec = Regex("""%(?:(\d+)\$)?([-#+0,(]*\d*(?:\.\d+)?)([a-zA-Z%])""")

    /** The plural quantities each language needs (CLDR, as Android's ICU uses them). French "many" is optional. */
    private val needs = mapOf(
        "" to setOf("one", "other"), "en" to setOf("one", "other"),
        "ar" to setOf("zero", "one", "two", "few", "many", "other"),
        "fr" to setOf("one", "other"),
        "ja" to setOf("other"), "zh-CN" to setOf("other"),
    )
    private val allowed = mapOf("fr" to setOf("one", "many", "other"))

    @Test fun keysAreUniqueAndNamedForTheirFile() {
        for (f in folders) {
            val twice = f.entries.groupBy { it.name }.filter { it.value.size > 1 }.map { (k, v) -> "$k in ${v.map { it.file }}" }
            assertTrue("${f.name}: keys defined twice: $twice", twice.isEmpty())
        }
        for (e in default.entries) {
            assertTrue("${e.name}: keys are snake_case", e.name.matches(Regex("[a-z][a-z0-9_]*")))
            // strings_panels.xml holds panels_… keys (strings.xml is the manifest's few, from before the split).
            val area = e.file.removePrefix("strings_").removeSuffix(".xml")
            if (e.file != "strings.xml") assertTrue("${e.name} (${e.file}) should start with ${area}_", e.name.startsWith(area + "_"))
        }
    }

    @Test fun translationsOnlyHoldWhatEnglishHasInTheSameFile() {
        for (f in translations) {
            for (e in f.entries) {
                val d = default.byName[e.name]
                assertTrue("${f.name}/${e.file}: ${e.name} isn't in the English strings", d != null)
                assertEquals("${f.name}: ${e.name} belongs in ${d!!.file}", d.file, e.file)
                assertEquals("${f.name}: ${e.name} is a ${if (d.plural) "plurals" else "string"} in English", d.plural, e.plural)
                assertTrue("${f.name}: ${e.name} is marked not to be translated", d.translatable)
            }
        }
    }

    @Test fun englishUsesPositionalArguments() {
        for (e in default.entries) for ((q, t) in e.texts) {
            val bad = args(t).filter { it.startsWith("?") }
            assertTrue("${e.name}${if (q.isEmpty()) "" else "[$q]"}: write %1\$s, %2\$d…, not %s: \"$t\"", bad.isEmpty())
            // A lone % in a string with arguments would break formatting: it must be %%.
            if (args(t).isNotEmpty()) {
                val stray = Regex("%").findAll(t).count() - Spec.findAll(t).sumOf { if (it.value == "%%") 2 else 1 }
                assertTrue("${e.name}: a lone % must be written %%: \"$t\"", stray == 0)
            }
        }
    }

    @Test fun placeholdersMatchEnglish() {
        for (f in translations) for (e in f.entries) {
            val d = default.byName[e.name] ?: continue
            if (!e.plural) {
                assertEquals("${f.name}: ${e.name} has other arguments than English", args(d.texts.getValue("")).sorted(), args(e.texts.getValue("")).sorted())
                continue
            }
            // A plural form may leave out the number ("un jeu"), but never use an argument English doesn't have,
            // and "other" has them all.
            val english = args(d.texts["other"].orEmpty()).toSet()
            for ((q, t) in e.texts) {
                val extra = args(t).toSet() - english
                assertTrue("${f.name}: ${e.name}[$q] uses $extra, which English doesn't", extra.isEmpty())
            }
            assertEquals("${f.name}: ${e.name}[other] has other arguments than English", english, args(e.texts["other"].orEmpty()).toSet())
        }
    }

    @Test fun pluralsHaveTheFormsTheirLanguageNeeds() {
        for (f in folders) {
            val need = needs[f.tag] ?: error("No plural rules for ${f.name}: add its CLDR quantities to this test")
            val may = need + allowed[f.tag].orEmpty()
            for (e in f.entries.filter { it.plural }) {
                val have = e.texts.keys
                assertTrue("${f.name}: ${e.name} lacks ${need - have}", have.containsAll(need))
                assertTrue("${f.name}: ${e.name} has ${have - may}, which ${f.tag.ifEmpty { "English" }} never uses", (have - may).isEmpty())
            }
        }
    }

    @Test fun quotesAndApostrophesAreEscaped() {
        for (f in folders) for (e in f.entries) for ((q, t) in e.texts) {
            val where = "${f.name}: ${e.name}${if (q.isEmpty()) "" else "[$q]"}"
            val quoted = t.length >= 2 && t.startsWith('"') && t.endsWith('"')
            if (!quoted) {
                assertTrue("$where: write \\' for an apostrophe: \"$t\"", !Regex("""(?<!\\)'""").containsMatchIn(t))
                assertTrue("$where: write \\\" for a double quote: \"$t\"", !Regex("""(?<!\\)"""").containsMatchIn(t))
            }
            assertTrue("$where: a leading @ or ? must be escaped (\\@, \\?)", !t.startsWith("@") && !t.startsWith("?"))
        }
    }

    @Test fun languagesOfferedMatchTheLocaleConfigAndTheFolders() {
        val config = File(res, "xml/locales_config.xml").readText()
        val listed = Regex("""android:name="([^"]+)"""").findAll(config).map { it.groupValues[1] }.toList()
        assertEquals("locales_config.xml lists Lang.offered", Lang.offered.map { it.tag }, listed)
        for (f in translations) assertTrue("${f.name} is a language Tandem doesn't offer (Lang.offered)", f.tag in listed)
    }

    @Test fun coverage() {
        val total = default.entries.count { it.translatable }
        println("String resources: $total in English")
        for (tag in Lang.offered.map { it.tag }.filter { it != "en" }) {
            val f = translations.firstOrNull { it.tag == tag }
            val done = f?.entries?.size ?: 0
            println("  $tag: $done of $total translated (${if (total == 0) 100 else done * 100 / total}%), the rest shows in English")
        }
    }
}
