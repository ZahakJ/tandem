package io.github.zahakj.tandem.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.zahakj.tandem.CalendarChoice
import io.github.zahakj.tandem.DateKind
import io.github.zahakj.tandem.Dates
import io.github.zahakj.tandem.Lang
import io.github.zahakj.tandem.R
import io.github.zahakj.tandem.Tx

/*
 * Settings › Look & feel › Language: the language Tandem speaks (Tx.kt, docs/TRANSLATING.md), and beside it the
 * calendar its dates are shown in (Dates.kt). The language options are each language's own name, so whoever reads it
 * finds theirs whatever the screen is in now; English words find the rows in search in every language.
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
    return SettingSection(label, listOf(row, calendarRow()), en = "Language")
}

private val CalendarLabels = mapOf(
    CalendarChoice.Gregorian to R.string.calendar_option_gregorian,
    CalendarChoice.Hijri to R.string.calendar_option_hijri,
    CalendarChoice.Chinese to R.string.calendar_option_chinese,
    CalendarChoice.GregorianHijri to R.string.calendar_option_gregorian_hijri,
    CalendarChoice.GregorianChinese to R.string.calendar_option_gregorian_chinese,
)

/** The calendar dates are shown in: each option's card shows today in it. */
@Composable
private fun calendarRow(): SettingRow = choice(
    Tx.s(R.string.calendar_setting), Glyph.Clock, Palette.Butter, Tx.s(R.string.calendar_setting_help),
    CalendarChoice.entries.map { it.id to Tx.s(CalendarLabels.getValue(it)) }, Dates.choice.id,
    look = { id -> CalendarLook(CalendarChoice.of(id)) },
    en = "Calendar",
) { id -> Dates.choose(CalendarChoice.of(id)) }.copy(
    // Words people may look for in their own language too: التقويم هجري, 农历 公历, 旧暦 西暦, hégire.
    keywords = "calendar date hijri islamic umm al-qura lunar chinese gregorian ramadan " +
        "التقويم هجري ميلادي 历法 农历 阴历 公历 日历 暦 旧暦 西暦 ヒジュラ暦 カレンダー calendrier hégire lunaire",
)

/** Today in [c]: the date, and under it the second calendar's when there is one. */
@Composable
private fun CalendarLook(c: CalendarChoice) {
    val (first, second) = Dates.lines(System.currentTimeMillis(), DateKind.Date, c)
    Column(
        Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ShrinkToFit(Modifier.fillMaxWidth()) {
            Text(first, style = MaterialTheme.typography.titleMedium, color = Palette.Text, maxLines = 1, textAlign = TextAlign.Center)
        }
        if (second != null) ShrinkToFit(Modifier.fillMaxWidth().padding(top = 2.dp)) {
            Text(second, style = MaterialTheme.typography.labelLarge, color = Palette.Butter, maxLines = 1, textAlign = TextAlign.Center)
        }
    }
}

/** A greeting in [tag]'s own script and shapes; for System default, a gear (it follows Android's). The welcome's language cards show it too. */
@Composable
internal fun LanguageLook(tag: String) {
    Box(Modifier.fillMaxSize().padding(8.dp), contentAlignment = Alignment.Center) {
        val text = Hello[tag]
        if (text == null) Box(Modifier.fillMaxSize(0.4f)) { TIconGlyph(TIcon.Gear, Palette.Sky) }
        else ShrinkToFit {
            Text(text, style = MaterialTheme.typography.headlineSmall.copy(localeList = LocaleList(tag)), color = Palette.Text, maxLines = 1)
        }
    }
}
