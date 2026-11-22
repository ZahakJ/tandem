package io.github.zahakj.tandem

import java.util.Locale

/*
 * The words of the Hijri and Chinese calendars, put together from a day's numbers. Plain Kotlin, no Android: the
 * numbers come from Android's ICU ([Dates]), the words from strings_calendar.xml, so the tests can build every
 * language's text from the real resources on the JVM (CalendarTextTest).
 */

/** A calendar a date can be shown in. */
enum class CalendarSystem { Gregorian, Hijri, Chinese }

/**
 * Settings › Look & feel › Calendar: the calendar dates are shown in, alone or with a second one beside the
 * Gregorian date. [id] is what's saved.
 */
enum class CalendarChoice(val id: String, val main: CalendarSystem, val second: CalendarSystem?) {
    Gregorian("gregorian", CalendarSystem.Gregorian, null),
    Hijri("hijri", CalendarSystem.Hijri, null),
    Chinese("chinese", CalendarSystem.Chinese, null),
    GregorianHijri("gregorian+hijri", CalendarSystem.Gregorian, CalendarSystem.Hijri),
    GregorianChinese("gregorian+chinese", CalendarSystem.Gregorian, CalendarSystem.Chinese);

    companion object {
        fun of(id: String?): CalendarChoice = entries.firstOrNull { it.id == id } ?: Gregorian
    }
}

/**
 * One day in a calendar: [month] 1 to 12, [leap] for the Chinese calendar's added month (which repeats the number of
 * the month before it), [cycle] the year's place in the Chinese sixty-year cycle (1 = 甲子), 0 elsewhere.
 */
data class CalDay(val year: Int, val month: Int, val day: Int, val leap: Boolean = false, val cycle: Int = 0)

/** Whether a date shows its year: always, never, or only when it isn't this year (in the same calendar). */
enum class YearShown { Always, Never, NotThisYear }

/**
 * The words one language writes these calendars with (strings_calendar.xml): twelve Hijri month names, twelve
 * Chinese lunar month names, twelve zodiac years (Rat first), and the templates that put a day together.
 */
class CalendarWords(
    /** Its language ("ar", "ja", "zh"…): Chinese writes lunar days and years its own way, see [CalendarText]. */
    val language: String,
    val hijriMonths: List<String>,
    val lunarMonths: List<String>,
    val zodiacYears: List<String>,
    /** %1$s: a lunar month's name. */
    val leapMonth: String,
    /** %1$s day, %2$s month. */
    val hijriDay: String,
    /** %1$s day, %2$s month, %3$s year. */
    val hijriDate: String,
    /** %1$s month, %2$s year. */
    val hijriMonthYear: String,
    /** %1$s month, %2$s day. */
    val lunarDay: String,
    /** %1$s month, %2$s day, %3$s the year's name. */
    val lunarDate: String,
    /** %1$s month, %2$s the year's name. */
    val lunarMonthYear: String,
    /** %1$s weekday, %2$s the date. */
    val weekdayDate: String,
)

object CalendarText {
    // The Chinese calendar's own characters: the same in Chinese and Japanese, never translated.
    private const val STEMS = "甲乙丙丁戊己庚辛壬癸" // tx-ok: the ten heavenly stems
    private const val BRANCHES = "子丑寅卯辰巳午未申酉戌亥" // tx-ok: the twelve earthly branches

    /** A lunar day as Chinese calendars write it: 初一 … 初十, 十一 … 二十, 廿一 … 廿九, 三十. */
    fun chineseDay(day: Int): String {
        val digits = "一二三四五六七八九十" // tx-ok: Chinese numerals
        return when {
            day <= 10 -> "初" + digits[day - 1] // tx-ok: Chinese numerals
            day < 20 -> "十" + digits[day - 11] // tx-ok: Chinese numerals
            day == 20 -> "二十" // tx-ok: Chinese numerals
            day < 30 -> "廿" + digits[day - 21] // tx-ok: Chinese numerals
            else -> "三十" // tx-ok: Chinese numerals
        }
    }

    /** The year's stem and branch (干支) for its [cycle] place, 43 → 丙午. */
    fun ganzhi(cycle: Int): String = "${STEMS[(cycle - 1).mod(10)]}${BRANCHES[(cycle - 1).mod(12)]}"

    /** The zodiac animal of a [cycle] place, 0 for the Rat. */
    fun zodiac(cycle: Int): Int = (cycle - 1).mod(12)

    /**
     * [d] in [system] (Hijri or Chinese) in [w]'s words, with [weekday]'s name in front when given, the day when [day]
     * and the year when [year]: "Sat 11 Jumada al-Akhirah 1448 AH", "十月十三", "13 du dixième mois".
     */
    fun format(system: CalendarSystem, d: CalDay, w: CalendarWords, weekday: String? = null, day: Boolean = true, year: Boolean = false): String {
        val date = when (system) {
            CalendarSystem.Hijri -> {
                val month = w.hijriMonths[d.month - 1]
                when {
                    !day -> fill(w.hijriMonthYear, w, month, d.year.toString())
                    year -> fill(w.hijriDate, w, d.day.toString(), month, d.year.toString())
                    else -> fill(w.hijriDay, w, d.day.toString(), month)
                }
            }
            CalendarSystem.Chinese -> {
                val month = lunarMonth(d, w)
                when {
                    !day -> fill(w.lunarMonthYear, w, month, lunarYear(d, w))
                    year -> fill(w.lunarDate, w, month, lunarDay(d, w), lunarYear(d, w))
                    else -> fill(w.lunarDay, w, month, lunarDay(d, w))
                }
            }
            CalendarSystem.Gregorian -> error("Gregorian dates use their own patterns")
        }
        return if (weekday != null) fill(w.weekdayDate, w, weekday, date) else date
    }

    /** The day of the month alone, as a small calendar cell shows it: "13", or 十三 for a lunar day in Chinese. */
    fun dayNumber(system: CalendarSystem, d: CalDay, language: String): String =
        if (system == CalendarSystem.Chinese && language == "zh") chineseDay(d.day) else d.day.toString()

    private fun lunarMonth(d: CalDay, w: CalendarWords): String {
        val name = w.lunarMonths[d.month - 1]
        return if (d.leap) fill(w.leapMonth, w, name) else name
    }

    private fun lunarDay(d: CalDay, w: CalendarWords): String = dayNumber(CalendarSystem.Chinese, d, w.language)

    /**
     * The year's name: its zodiac animal ("Year of the Horse", 马年, 午年), with its stem and branch in front in Chinese
     * (丙午马年) and its stem in Japanese (丙午年), the way each writes it.
     */
    private fun lunarYear(d: CalDay, w: CalendarWords): String {
        val animal = w.zodiacYears[zodiac(d.cycle)]
        return when (w.language) {
            "zh" -> ganzhi(d.cycle) + animal
            "ja" -> ganzhi(d.cycle).take(1) + animal
            else -> animal
        }
    }

    private fun fill(template: String, w: CalendarWords, vararg args: String): String =
        String.format(Locale.forLanguageTag(w.language), template, *args)
}
