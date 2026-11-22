package io.github.zahakj.tandem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.kxml2.io.KXmlParser
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.time.LocalDate
import java.time.chrono.HijrahDate
import java.time.temporal.ChronoField

/**
 * The Hijri and Chinese dates (CalendarText.kt), built from the real strings_calendar.xml of every language. The day's
 * numbers come from Android's ICU on the device; here the Hijri ones come from java.time's Umm al-Qura calendar (the
 * same tables) and the Chinese ones are written in.
 */
class CalendarTextTest {
    private val res: File = listOf("src/main/res", "app/src/main/res").map(::File).first { it.isDirectory }

    /** The strings of one language's strings_calendar.xml, escapes undone as Android does. */
    private fun strings(folder: String): Map<String, String> {
        val p = KXmlParser()
        val out = HashMap<String, String>()
        File(res, "$folder/strings_calendar.xml").reader().use { r ->
            p.setInput(r)
            while (p.next() != XmlPullParser.END_DOCUMENT) {
                if (p.eventType == XmlPullParser.START_TAG && p.name == "string") {
                    val name = p.getAttributeValue(null, "name")
                    out[name] = p.nextText().replace("\\'", "'").replace("\\\"", "\"")
                }
            }
        }
        return out
    }

    private fun words(folder: String, language: String): CalendarWords {
        val s = strings(folder)
        fun twelve(prefix: String) = (1..12).map { s.getValue("calendar_${prefix}_$it") }
        return CalendarWords(
            language, twelve("hijri_month"), twelve("lunar_month"), twelve("lunar_year"),
            s.getValue("calendar_lunar_leap"), s.getValue("calendar_hijri_day"), s.getValue("calendar_hijri_date"),
            s.getValue("calendar_hijri_month_year"), s.getValue("calendar_lunar_day"), s.getValue("calendar_lunar_date"),
            s.getValue("calendar_lunar_month_year"), s.getValue("calendar_weekday_date"),
        )
    }

    private val en by lazy { words("values", "en") }
    private val ar by lazy { words("values-ar", "ar") }
    private val ja by lazy { words("values-ja", "ja") }
    private val zh by lazy { words("values-zh-rCN", "zh") }
    private val fr by lazy { words("values-fr", "fr") }

    /** 21 November 2026 in the Hijri calendar, from java.time's Umm al-Qura tables. */
    private val hijri: CalDay by lazy {
        val h = HijrahDate.from(LocalDate.of(2026, 11, 21))
        CalDay(h.get(ChronoField.YEAR), h.get(ChronoField.MONTH_OF_YEAR), h.get(ChronoField.DAY_OF_MONTH))
    }

    /** The same day in the Chinese calendar: the 13th of the tenth month of 丙午 (the 43rd year of the cycle). */
    private val lunar = CalDay(2026, 10, 13, cycle = 43)

    private val Hijri = CalendarSystem.Hijri
    private val Chinese = CalendarSystem.Chinese

    @Test fun hijriDayFromUmmAlQura() = assertEquals(CalDay(1448, 6, 11), hijri)

    @Test fun hijriInEveryLanguage() {
        assertEquals("11 Jumada al-Akhirah", CalendarText.format(Hijri, hijri, en))
        assertEquals("11 Jumada al-Akhirah 1448 AH", CalendarText.format(Hijri, hijri, en, year = true))
        assertEquals("Sat 11 Jumada al-Akhirah", CalendarText.format(Hijri, hijri, en, weekday = "Sat"))
        assertEquals("Jumada al-Akhirah 1448 AH", CalendarText.format(Hijri, hijri, en, day = false, year = true))
        assertEquals("السبت 11 جمادى الآخرة 1448 هـ", CalendarText.format(Hijri, hijri, ar, weekday = "السبت", year = true))
        assertEquals("ヒジュラ暦1448年ジュマーダー・アル＝アーヒラ11日", CalendarText.format(Hijri, hijri, ja, year = true))
        assertEquals("主马达·阿色尼月11日 周六", CalendarText.format(Hijri, hijri, zh, weekday = "周六"))
        assertEquals("11 joumada ath-thania 1448 de l’hégire", CalendarText.format(Hijri, hijri, fr, year = true))
    }

    @Test fun chineseInEveryLanguage() {
        assertEquals("十月十三", CalendarText.format(Chinese, lunar, zh))
        assertEquals("丙午马年十月十三", CalendarText.format(Chinese, lunar, zh, year = true))
        assertEquals("丙午马年十月", CalendarText.format(Chinese, lunar, zh, day = false, year = true))
        assertEquals("十月十三 星期六", CalendarText.format(Chinese, lunar, zh, weekday = "星期六"))
        assertEquals("丙午年十月13日", CalendarText.format(Chinese, lunar, ja, year = true))
        assertEquals("十月13日（土）", CalendarText.format(Chinese, lunar, ja, weekday = "土"))
        assertEquals("Tenth Month 13", CalendarText.format(Chinese, lunar, en))
        assertEquals("Tenth Month 13, Year of the Horse", CalendarText.format(Chinese, lunar, en, year = true))
        assertEquals("13 من الشهر العاشر، عام الحصان", CalendarText.format(Chinese, lunar, ar, year = true))
        assertEquals("13 du dixième mois", CalendarText.format(Chinese, lunar, fr))
    }

    @Test fun leapMonths() {
        val leap = CalDay(2025, 6, 1, leap = true, cycle = 42)
        assertEquals("闰六月初一", CalendarText.format(Chinese, leap, zh))
        assertEquals("Leap Sixth Month 1", CalendarText.format(Chinese, leap, en))
    }

    @Test fun chineseDays() {
        val want = mapOf(1 to "初一", 9 to "初九", 10 to "初十", 11 to "十一", 19 to "十九", 20 to "二十", 21 to "廿一", 29 to "廿九", 30 to "三十")
        for ((d, s) in want) assertEquals(s, CalendarText.chineseDay(d))
        assertEquals("十三", CalendarText.dayNumber(Chinese, lunar, "zh"))
        assertEquals("13", CalendarText.dayNumber(Chinese, lunar, "ja"))
        assertEquals("11", CalendarText.dayNumber(Hijri, hijri, "zh"))
    }

    @Test fun sexagenaryYears() {
        assertEquals("甲子", CalendarText.ganzhi(1))
        assertEquals("甲辰", CalendarText.ganzhi(41)) // 2024, the Dragon
        assertEquals("丙午", CalendarText.ganzhi(43)) // 2026, the Horse
        assertEquals("癸亥", CalendarText.ganzhi(60))
        assertEquals(6, CalendarText.zodiac(43))
    }

    @Test fun everyLanguageHasEveryWordAndWesternDigits() {
        for (w in listOf(en, ar, ja, zh, fr)) {
            for (m in 1..12) for (leap in listOf(false, true)) {
                val d = CalDay(1448, m, 29, leap, cycle = m * 5)
                for (sys in listOf(Hijri, Chinese)) for (year in listOf(false, true)) {
                    val s = CalendarText.format(sys, d, w, weekday = "W", year = year)
                    assertTrue("${w.language}: $s", s.isNotBlank() && "%" !in s)
                    assertFalse("${w.language}: Arabic-Indic digits in $s", s.any { it in '٠'..'٩' })
                }
            }
        }
    }

    @Test fun choicesKeepTheirIds() {
        for (c in CalendarChoice.entries) assertEquals(c, CalendarChoice.of(c.id))
        assertEquals(CalendarChoice.Gregorian, CalendarChoice.of(null))
        assertEquals(CalendarChoice.Gregorian, CalendarChoice.of("julian"))
        assertEquals(CalendarSystem.Gregorian, CalendarChoice.GregorianHijri.main)
        assertEquals(CalendarSystem.Chinese, CalendarChoice.GregorianChinese.second)
    }
}
