package io.github.zahakj.tandem

import android.content.Context
import android.icu.util.Calendar as IcuCalendar
import android.icu.util.ChineseCalendar
import android.icu.util.IslamicCalendar
import android.icu.util.TimeZone
import android.icu.util.ULocale
import android.text.format.DateUtils
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.zahakj.tandem.ui.joinParts
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle

/*
 * Every date Tandem shows goes through [Dates], so Settings › Look & feel › Calendar reaches all of them: the clocks,
 * the side panel, the bottom screen, the screensaver, Stats, the journal, the session editor, backups. A [DateKind]
 * says how much of the date a place shows (the weekday, the year) and how it writes a Gregorian date there (its own
 * translated pattern, unchanged from before); the Hijri and Chinese dates are put together from strings_calendar.xml
 * by [CalendarText], with the day's numbers from Android's ICU.
 *
 * Display only: saved data, file names, CSV exports and logs stay Gregorian (ISO), and relative times ("5 minutes
 * ago") stay as they are.
 */

/**
 * How a place shows a date. [weekday] its weekday (short "Sat" or full "Saturday", or none), [year] whether the year
 * shows, [day] false for a month alone ("Nov 2026"), [time] a time of day after the date. [gregorian] writes the
 * Gregorian date (with its time, if [time]) the way that place always has.
 */
class DateKind(
    val weekday: TextStyle?,
    val year: YearShown,
    val day: Boolean = true,
    val time: Boolean = false,
    val gregorian: (ZonedDateTime) -> String,
) {
    companion object {
        /** A Gregorian date written with the java.time pattern in [res] (a translated pattern, "EEE d MMM"). */
        fun pattern(@StringRes res: Int, weekday: TextStyle?, year: Boolean, day: Boolean = true) =
            DateKind(weekday, if (year) YearShown.Always else YearShown.Never, day) { it.format(DateTimeFormatter.ofPattern(Tx.s(res), Lang.locale)) }

        /** A Gregorian date written with Android's best pattern for [skeleton] in the language ("EEEEdMMMM"). */
        fun skeleton(skeleton: String, weekday: TextStyle?, year: Boolean) =
            DateKind(weekday, if (year) YearShown.Always else YearShown.Never) {
                java.text.SimpleDateFormat(android.text.format.DateFormat.getBestDateTimePattern(Lang.locale, skeleton), Lang.locale)
                    .format(java.util.Date(it.toInstant().toEpochMilli()))
            }

        /** "Sat 21 Nov": Stats' chart. */
        val ShortDay = pattern(R.string.stats_date_short, TextStyle.SHORT, year = false)
        /** "Saturday 21 November": a day's heading in Stats › Sessions. */
        val LongDay = pattern(R.string.stats_date_long, TextStyle.FULL, year = false)
        /** "21 Nov". */
        val DayMonth = pattern(R.string.stats_date_day_month, null, year = false)
        /** "21 Nov 2026": when a trophy was earned, Steam's dates. */
        val Date = pattern(R.string.stats_date_earned, null, year = true)
        /** "Sat 21 Nov 2026": a play session's day. */
        val SessionDay = pattern(R.string.stats_date_session, TextStyle.SHORT, year = true)
        /** "Nov 2026": the month a game was finished. */
        val MonthYear = pattern(R.string.stats_date_month, null, year = true, day = false)
        /** "Saturday 21 November 2026": the journal's days. */
        val JournalDay = pattern(R.string.apps_journal_date_long, TextStyle.FULL, year = true)
        /** "Sat 21 Nov": the clock widget. */
        val WidgetDay = pattern(R.string.widgets_date_short, TextStyle.SHORT, year = false)
        /** "21 November": beside the weekday on the analog clock widget. */
        val WidgetDayMonth = pattern(R.string.widgets_date_day_month, null, year = false)
        /** "Saturday, 21 November": the home clock. */
        val Clock = pattern(R.string.home_clock_date, TextStyle.FULL, year = false)
        /** "Saturday 21 November": the bottom screen's, the companion's and the screensaver's date. */
        val Today = skeleton("EEEEdMMMM", TextStyle.FULL, year = false)
        /** "Nov 3", or "Nov 3, 2025" in another year, as Android writes it: when a game was added. */
        val Added = DateKind(null, YearShown.NotThisYear) { byAndroid(it, 0) }
        /** "Nov 3, 2025" as Android writes it: when an app was installed. */
        val Installed = DateKind(null, YearShown.Always) { byAndroid(it, DateUtils.FORMAT_SHOW_YEAR) }

        private fun byAndroid(t: ZonedDateTime, flags: Int) = DateUtils.formatDateTime(
            Tx.context, t.toInstant().toEpochMilli(),
            DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH or flags,
        )
    }
}

object Dates {
    private const val PREFS = "calendar"
    private const val KEY = "choice"
    private lateinit var app: Context

    /** The calendar chosen in Settings. Composables reading it (through any format below) follow a change at once. */
    var choice by mutableStateOf(CalendarChoice.Gregorian)
        private set

    /** Once per process, after [Lang.init] (TandemApplication). */
    fun init(context: Context) {
        app = context.applicationContext
        choice = CalendarChoice.of(app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null))
    }

    /** Picks a calendar; both screens redraw their dates in it. Main thread. */
    fun choose(c: CalendarChoice) {
        if (c == choice) return
        choice = c
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, c.id).apply()
    }

    private val zone: ZoneId get() = ZoneId.systemDefault()

    /** [ms] as [kind] on one line; with two calendars, "Gregorian · second" (for places with room for both). */
    fun text(ms: Long, kind: DateKind, c: CalendarChoice = choice): String = text(at(ms), kind, c)

    fun text(date: LocalDate, kind: DateKind, c: CalendarChoice = choice): String = text(date.atStartOfDay(zone), kind, c)

    fun text(t: ZonedDateTime, kind: DateKind, c: CalendarChoice = choice): String {
        val (first, second) = lines(t, kind, c)
        return if (second == null) first else listOf(first, second).joinParts()
    }

    /**
     * [ms] as [kind] in the main calendar only: for the few places with no room for a second date at all (a plaque
     * under a finished game, a trophy card, a session menu's value).
     */
    fun main(ms: Long, kind: DateKind): String = lines(at(ms), kind, choice).first

    fun main(date: LocalDate, kind: DateKind): String = lines(date.atStartOfDay(zone), kind, choice).first

    /**
     * [ms] as [kind], with the second calendar apart (null with one calendar): for tight places that put it on a
     * smaller line of its own under the first (the clocks, the bottom screen's date).
     */
    fun lines(ms: Long, kind: DateKind, c: CalendarChoice = choice): Pair<String, String?> = lines(at(ms), kind, c)

    fun lines(t: ZonedDateTime, kind: DateKind, c: CalendarChoice = choice): Pair<String, String?> {
        val first = when (c.main) {
            CalendarSystem.Gregorian -> kind.gregorian(t)
            else -> other(c.main, t, kind, kind.weekday).let { if (kind.time) Tx.s(R.string.calendar_date_time, it, timeOf(t)) else it }
        }
        // The second date leaves the weekday (and the time) to the Gregorian one before it.
        return first to c.second?.let { other(it, t, kind, null) }
    }

    /** The day of the month alone, in the main calendar: the session editor's week strip. */
    fun dayNumber(date: LocalDate): String = when (val sys = choice.main) {
        CalendarSystem.Gregorian -> date.dayOfMonth.toString()
        else -> CalendarText.dayNumber(sys, day(sys, date), Lang.locale.language)
    }

    /**
     * "5 minutes ago", "yesterday", the way Android says it ([minResolution], [flags] as DateUtils takes them); past a
     * week, where Android writes the date itself, the date in the chosen calendar.
     */
    fun relative(time: Long, now: Long = System.currentTimeMillis(), minResolution: Long = DateUtils.MINUTE_IN_MILLIS, flags: Int = 0): String {
        val said = DateUtils.getRelativeTimeSpanString(time, now, minResolution, flags).toString()
        if (choice == CalendarChoice.Gregorian || kotlin.math.abs(now - time) < DateUtils.WEEK_IN_MILLIS) return said
        return text(time, agoKind(said))
    }

    /** A date past a week ago: the year only when it isn't this year; Gregorian as Android wrote it. */
    private fun agoKind(said: String) = DateKind(null, YearShown.NotThisYear) { said }

    /** [ms] as a [ZonedDateTime] here. */
    fun at(ms: Long): ZonedDateTime = Instant.ofEpochMilli(ms).atZone(zone)

    private fun timeOf(t: ZonedDateTime): String =
        android.text.format.DateFormat.getTimeFormat(Tx.context).format(java.util.Date(t.toInstant().toEpochMilli()))

    private fun other(sys: CalendarSystem, t: ZonedDateTime, kind: DateKind, weekday: TextStyle?): String {
        val d = day(sys, t.toLocalDate())
        val year = when (kind.year) {
            YearShown.Always -> true
            YearShown.Never -> false
            YearShown.NotThisYear -> d.year != day(sys, LocalDate.now(zone)).year
        }
        return CalendarText.format(sys, d, words(), weekday?.let { t.dayOfWeek.getDisplayName(it, Lang.locale) }, kind.day, year)
    }

    // ---- ICU: a day's numbers in the Hijri (Umm al-Qura) and Chinese calendars, kept for the last few days asked.

    private val days = object : LinkedHashMap<Pair<CalendarSystem, Long>, CalDay>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Pair<CalendarSystem, Long>, CalDay>?) = size > 32
    }

    private fun day(sys: CalendarSystem, date: LocalDate): CalDay = synchronized(days) {
        days.getOrPut(sys to date.toEpochDay()) { convert(sys, date) }
    }

    private fun convert(sys: CalendarSystem, date: LocalDate): CalDay {
        // Noon UTC of the day, read in UTC: the local day, whatever the time zone and daylight saving.
        val noon = date.toEpochDay() * 86_400_000L + 43_200_000L
        val utc = TimeZone.getTimeZone("UTC")
        return when (sys) {
            CalendarSystem.Hijri -> {
                val c = IslamicCalendar(utc, ULocale.ROOT)
                c.calculationType = IslamicCalendar.CalculationType.ISLAMIC_UMALQURA
                c.timeInMillis = noon
                CalDay(c.get(IcuCalendar.YEAR), c.get(IcuCalendar.MONTH) + 1, c.get(IcuCalendar.DAY_OF_MONTH))
            }
            CalendarSystem.Chinese -> {
                val c = ChineseCalendar(utc, ULocale.ROOT)
                c.timeInMillis = noon
                CalDay(
                    c.get(IcuCalendar.EXTENDED_YEAR), c.get(IcuCalendar.MONTH) + 1, c.get(IcuCalendar.DAY_OF_MONTH),
                    leap = c.get(IcuCalendar.IS_LEAP_MONTH) == 1, cycle = c.get(IcuCalendar.YEAR),
                )
            }
            CalendarSystem.Gregorian -> CalDay(date.year, date.monthValue, date.dayOfMonth)
        }
    }

    // ---- The words, made again when the language changes.

    @Volatile private var cached: Pair<Int, CalendarWords>? = null

    private fun words(): CalendarWords {
        val rev = Lang.revision.intValue
        cached?.let { (r, w) -> if (r == rev) return w }
        return CalendarWords(
            language = Lang.locale.language,
            hijriMonths = HijriMonths.map { Tx.s(it) },
            lunarMonths = LunarMonths.map { Tx.s(it) },
            zodiacYears = ZodiacYears.map { Tx.s(it) },
            leapMonth = Tx.s(R.string.calendar_lunar_leap),
            hijriDay = Tx.s(R.string.calendar_hijri_day),
            hijriDate = Tx.s(R.string.calendar_hijri_date),
            hijriMonthYear = Tx.s(R.string.calendar_hijri_month_year),
            lunarDay = Tx.s(R.string.calendar_lunar_day),
            lunarDate = Tx.s(R.string.calendar_lunar_date),
            lunarMonthYear = Tx.s(R.string.calendar_lunar_month_year),
            weekdayDate = Tx.s(R.string.calendar_weekday_date),
        ).also { cached = rev to it }
    }

    private val HijriMonths = listOf(
        R.string.calendar_hijri_month_1, R.string.calendar_hijri_month_2, R.string.calendar_hijri_month_3, R.string.calendar_hijri_month_4,
        R.string.calendar_hijri_month_5, R.string.calendar_hijri_month_6, R.string.calendar_hijri_month_7, R.string.calendar_hijri_month_8,
        R.string.calendar_hijri_month_9, R.string.calendar_hijri_month_10, R.string.calendar_hijri_month_11, R.string.calendar_hijri_month_12,
    )
    private val LunarMonths = listOf(
        R.string.calendar_lunar_month_1, R.string.calendar_lunar_month_2, R.string.calendar_lunar_month_3, R.string.calendar_lunar_month_4,
        R.string.calendar_lunar_month_5, R.string.calendar_lunar_month_6, R.string.calendar_lunar_month_7, R.string.calendar_lunar_month_8,
        R.string.calendar_lunar_month_9, R.string.calendar_lunar_month_10, R.string.calendar_lunar_month_11, R.string.calendar_lunar_month_12,
    )
    private val ZodiacYears = listOf(
        R.string.calendar_lunar_year_1, R.string.calendar_lunar_year_2, R.string.calendar_lunar_year_3, R.string.calendar_lunar_year_4,
        R.string.calendar_lunar_year_5, R.string.calendar_lunar_year_6, R.string.calendar_lunar_year_7, R.string.calendar_lunar_year_8,
        R.string.calendar_lunar_year_9, R.string.calendar_lunar_year_10, R.string.calendar_lunar_year_11, R.string.calendar_lunar_year_12,
    )
}
