package io.github.zahakj.tandem.data

import java.util.Locale

/**
 * The key a title sorts by when games go by name: lowercase, without a leading article, so "The Legend of Zelda"
 * sorts under L. The articles are English's ("The", "A", "An") in every language, since most game titles are
 * English, plus those of the language Tandem shows and the device's own ([articlesFor]): French "Le", "La",
 * "Les", "L'"; German "Der", "Die", "Das"; Spanish "El", "La", "Los", "Las". Japanese and Chinese have none. An
 * Arabic title loses the "ال" its first word starts with ("الأسطورة" sorts as "أسطورة"). A title that is only an
 * article ("The") keeps it. Pure, so it is unit tested.
 */
object SortKeys {
    private val ARTICLES = mapOf(
        "en" to listOf("the ", "a ", "an "),
        "fr" to listOf("les ", "le ", "la ", "l'", "l’"),
        "de" to listOf("der ", "die ", "das "),
        "es" to listOf("los ", "las ", "el ", "la "),
    )

    /** The articles to skip for [languages] (language codes, "fr" or "fr-CA"): English's, then theirs. */
    fun articlesFor(languages: Collection<String>): List<String> =
        (listOf("en") + languages.map { it.substringBefore('-').substringBefore('_').lowercase() }).distinct().flatMap { ARTICLES[it].orEmpty() }.distinct()

    /** The articles of English, Tandem's language and the device's, read each time (they follow a language change). */
    fun current(): List<String> = cache.let { (langs, list) ->
        val now = listOf(io.github.zahakj.tandem.Lang.locale.language, systemLanguage())
        if (now == langs) list else articlesFor(now).also { cache = now to it }
    }

    @Volatile private var cache: Pair<List<String>, List<String>> = emptyList<String>() to emptyList()

    private fun systemLanguage(): String =
        runCatching { android.content.res.Resources.getSystem().configuration.locales[0].language }.getOrDefault("en")

    /** [title]'s sort key: lowercase, without a leading article from [articles] or an Arabic "ال". */
    fun name(title: String, articles: List<String>, skip: Boolean = true): String {
        val t = title.trim().lowercase(Locale.ROOT)
        if (!skip) return t
        for (a in articles) if (t.length > a.length && t.startsWith(a)) return t.substring(a.length).trimStart().ifEmpty { t }
        if (t.length > 3 && t.startsWith("ال") && t[2].isLetter()) return t.substring(2)
        return t
    }
}
