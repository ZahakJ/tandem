package io.github.zahakj.tandem.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.unit.dp
import io.github.zahakj.tandem.Lang
import io.github.zahakj.tandem.R
import io.github.zahakj.tandem.Tx

/*
 * Settings › Look & feel › Language: the language Tandem speaks (Tx.kt, docs/TRANSLATING.md). The options are
 * each language's own name, so whoever reads it finds theirs whatever the screen is in now; English words find the
 * row in search in every language.
 */

/** A greeting in each language, the picker's miniature of it. */
private val Hello = mapOf("en" to "Hello", "ar" to "مرحبا", "ja" to "こんにちは", "zh-CN" to "你好", "fr" to "Bonjour")

@Composable
fun languageSettings(): SettingSection {
    val options = listOf(Lang.SYSTEM to Tx.s(R.string.common_language_system)) + Lang.offered.map { it.tag to it.name }
    val label = Tx.s(R.string.common_language)
    val row = choice(
        label, Glyph.Text, Palette.Sky, Tx.s(R.string.common_language_help),
        options, Lang.chosen,
        look = { tag -> LanguageLook(tag) },
        en = "Language",
    ) { tag -> Lang.choose(tag) }.copy(keywords = "language translation english arabic japanese chinese french locale")
    return SettingSection(label, listOf(row), en = "Language")
}

/** A greeting in [tag]'s own script and shapes; for System default, a gear (it follows Android's). */
@Composable
private fun LanguageLook(tag: String) {
    Box(Modifier.fillMaxSize().padding(8.dp), contentAlignment = Alignment.Center) {
        val text = Hello[tag]
        if (text == null) Box(Modifier.fillMaxSize(0.4f)) { TIconGlyph(TIcon.Gear, Palette.Sky) }
        else Text(
            text, style = MaterialTheme.typography.headlineSmall.copy(localeList = LocaleList(tag)), color = Palette.Text,
            maxLines = 1,
        )
    }
}
