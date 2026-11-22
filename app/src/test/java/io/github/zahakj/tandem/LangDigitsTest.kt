package io.github.zahakj.tandem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** Arabic shows Western digits 0–9 everywhere (the owner's choice), and stays Arabic otherwise. */
class LangDigitsTest {
    private val ar = Lang.westernDigits(Locale.forLanguageTag("ar"))

    private fun assertWestern(text: String) =
        assertFalse("Arabic-Indic digits in \"$text\"", text.any { it in '٠'..'٩' || it in '۰'..'۹' })

    @Test fun arabicStaysArabic() {
        // The language decides the plural rules (CLDR's are per language) and the resources; only the digits change.
        assertEquals("ar", ar.language)
        assertEquals("latn", ar.getUnicodeLocaleType("nu"))
        assertEquals('0', DecimalFormatSymbols.getInstance(ar).zeroDigit)
    }

    @Test fun numbersAndPercentagesUseWesternDigits() {
        assertEquals("100%", String.format(ar, "%1\$d%%", 100))
        assertEquals("130", String.format(ar, "%1\$d", 130))
        assertWestern(String.format(ar, "%,d", 1234567))
        assertWestern(String.format(ar, "%.1f", 4.5))
    }

    @Test fun timesAndDatesUseWesternDigits() {
        val at = Calendar.getInstance().apply { set(2026, Calendar.NOVEMBER, 21, 6, 24) }.time
        assertEquals("06:24", SimpleDateFormat("HH:mm", ar).format(at))
        assertWestern(SimpleDateFormat("d MMMM yyyy", ar).format(at))
        assertWestern(java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT, ar).format(at))
    }

    @Test fun anArabicVariantKeepsItsRegion() {
        val eg = Lang.westernDigits(Locale.forLanguageTag("ar-EG"))
        assertEquals("EG", eg.country)
        assertEquals("latn", eg.getUnicodeLocaleType("nu"))
        // Even someone who asked Android for Arabic-Indic digits gets Western ones in Tandem.
        assertEquals("latn", Lang.westernDigits(Locale.forLanguageTag("ar-u-nu-arab")).getUnicodeLocaleType("nu"))
    }

    @Test fun otherLanguagesAreUntouched() {
        for (tag in listOf("en", "ja", "zh-CN", "fr")) {
            val l = Locale.forLanguageTag(tag)
            assertEquals(l, Lang.westernDigits(l))
        }
    }
}
