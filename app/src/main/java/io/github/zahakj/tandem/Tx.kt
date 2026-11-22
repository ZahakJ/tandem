package io.github.zahakj.tandem

import android.app.LocaleManager
import android.content.ComponentCallbacks
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.LayoutDirection
import java.util.Locale

/*
 * Tandem's languages. Every piece of text the user sees comes from res/values(-xx)/strings_<area>.xml through [Tx], in
 * composables and everywhere else alike (docs/TRANSLATING.md).
 *
 * Why one helper and not Compose's stringResource: the language can be Tandem's own, different from Android's. On
 * Android 13 and later Android itself keeps an app's language (LocaleManager) and every window's resources follow
 * it; on Android 11 and 12 nothing does, and making an activity's resources follow would mean recreating every
 * activity (the library, its bottom screen, the companion, the screensaver), which they were never built for. [Tx]
 * reads from resources made for the chosen language, the same way on every version, and reading it inside a
 * composable subscribes that composable to [Lang.revision]: a change of language redraws both screens where they
 * are, nothing recreated, nothing lost.
 */

/** The language Tandem speaks: Android's, or one picked in Settings › Play & system › Language. */
object Lang {
    /** A language on offer: its BCP 47 [tag] and its [name] in itself (so anyone can find their own). */
    class Option(val tag: String, val name: String)

    /** Follow Android's language. */
    const val SYSTEM = ""

    val offered = listOf(
        Option("en", "English"),
        Option("ar", "العربية"),
        Option("ja", "日本語"),
        Option("zh-CN", "简体中文"),
        Option("fr", "Français"),
    )

    /**
     * Arabic lays every screen out right to left (docs/TRANSLATING.md, "Right to left": what mirrors, what stays left
     * to right on purpose, and how the D-pad steps). Turning this off puts every language back to left to right,
     * with Arabic text still running right to left inside each line.
     */
    const val RTL_READY = true

    /** The tag picked in Settings, or [SYSTEM]. */
    var chosen by mutableStateOf(SYSTEM)
        private set

    /**
     * The locale text is in now: the chosen language, or Android's for [SYSTEM], with Western digits in Arabic
     * ([westernDigits]). Use it for every date and number formatted in code. Any thread; a composable reading it
     * follows a change of language.
     */
    var locale: Locale by mutableStateOf(westernDigits(Locale.getDefault()))
        private set

    /**
     * [l] with Western digits (0–9) where its language would write Arabic-Indic ones (٠١٢): the owner's choice for
     * Arabic, whatever Android's own setting. Only the digits change: the language, and so its plural rules (CLDR's
     * are per language), and its text stay Arabic.
     */
    fun westernDigits(l: Locale): Locale =
        if (l.language == "ar" && l.getUnicodeLocaleType("nu") != "latn")
            Locale.Builder().setLocale(l).setUnicodeLocaleKeyword("nu", "latn").build()
        else l

    /** [westernDigits] for every locale in [list]. */
    fun westernDigits(list: LocaleList): LocaleList =
        LocaleList(*Array(list.size()) { westernDigits(list[it]) })

    /**
     * Bumped whenever the language in use changes. [Tx] reads it, so everything composed from strings is composed
     * again; code that keeps a string (remember, a cache) keys it on this.
     */
    val revision = mutableIntStateOf(0)

    /** Screens lay out right to left only for a right-to-left language, and only once [RTL_READY]. */
    val layoutDirection: LayoutDirection
        get() {
            revision.intValue
            return if (RTL_READY && isRtl(locale)) LayoutDirection.Rtl else LayoutDirection.Ltr
        }

    private const val PREFS = "language"
    private const val KEY = "tag"
    private lateinit var app: Context

    /** Once per process, before anything shows (TandemApplication). */
    fun init(context: Context) {
        app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        var tag = prefs.getString(KEY, SYSTEM).orEmpty()
        if (Build.VERSION.SDK_INT >= 33) {
            // Android keeps the app's language (its own Settings › Apps › Tandem › Language changes it too), so it
            // has the last word; a choice made before an update to Android 13 is handed to it once.
            val system = systemChoice()
            if (system == SYSTEM && tag != SYSTEM && !prefs.getBoolean("handed", false)) setSystemChoice(tag)
            else if (system != null) tag = system
            prefs.edit().putBoolean("handed", true).putString(KEY, tag).apply()
        }
        chosen = tag
        apply()
        // Android's language changing (or, on 13+, Tandem's in Android's own settings), and every other
        // configuration change, which also puts Android's default locale back. Every window's theme calls [sync]
        // too (TandemTheme): Android 13's per-app language reaches the activities' configuration, which this
        // process-wide callback can miss.
        app.registerComponentCallbacks(object : ComponentCallbacks {
            override fun onConfigurationChanged(newConfig: Configuration) = sync()
            @Deprecated("Deprecated in Java")
            override fun onLowMemory() = Unit
        })
    }

    /** Picks a language ([SYSTEM] or an [offered] tag); every screen follows at once. Main thread. */
    fun choose(tag: String) {
        if (tag == chosen) return
        chosen = tag
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, tag).apply()
        if (Build.VERSION.SDK_INT >= 33) setSystemChoice(tag)
        apply()
    }

    /**
     * The configuration changed: the language may have (Android's, or Tandem's in Android's settings, or
     * `cmd locale set-app-locales`). Cheap when nothing did. Main thread.
     */
    fun sync() {
        if (!::app.isInitialized) return
        if (Build.VERSION.SDK_INT >= 33) systemChoice()?.takeIf { it != chosen }?.let { tag ->
            chosen = tag
            app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, tag).apply()
        }
        apply()
    }

    private fun apply() {
        val list = westernDigits(if (chosen == SYSTEM) systemLocales() else LocaleList(Locale.forLanguageTag(chosen)))
        // Android's default locale: dates and numbers formatted without a locale (DateUtils, DateFormat, "%d") speak
        // the same language as the text, with the same digits. On 11 and 12 Android's default is Android's language;
        // on 13+ Android sets it to the app's language at each change, with Arabic-Indic digits for Arabic.
        if (LocaleList.getDefault() != list) LocaleList.setDefault(list)
        val want = list[0]
        val changed = want != locale || !Tx.ready
        locale = want
        // Tx reads from its own resources made for [list], never the app's live ones: the text changes exactly when
        // the revision (and with it the layout direction) does, never half a screen before.
        Tx.load(app, list)
        if (changed) revision.intValue++
    }

    /** Android's own languages, whatever Tandem's is. */
    private fun systemLocales(): LocaleList =
        if (Build.VERSION.SDK_INT >= 33) app.getSystemService(LocaleManager::class.java).systemLocales.takeUnless { it.isEmpty } ?: LocaleList.getAdjustedDefault()
        else Resources.getSystem().configuration.locales

    /** The app language Android keeps (13+): a tag, [SYSTEM] for none, or null when Android couldn't be asked. */
    private fun systemChoice(): String? {
        if (Build.VERSION.SDK_INT < 33) return null
        val list = runCatching { app.getSystemService(LocaleManager::class.java).applicationLocales }.getOrNull() ?: return null
        if (list.isEmpty) return SYSTEM
        val l = list[0]
        // The pseudo-locales of a debug build (docs/TRANSLATING.md) stay as they are.
        if (l.country == "XA" || l.country == "XB") return l.toLanguageTag()
        // Back to one of ours: zh-Hans-CN and zh-CN are the same choice, any English is English.
        return offered.firstOrNull { Locale.forLanguageTag(it.tag).let { o -> o.language == l.language && (o.country.isEmpty() || o.country == l.country || l.script == "Hans") } }?.tag
            ?: l.toLanguageTag()
    }

    private fun setSystemChoice(tag: String) {
        if (Build.VERSION.SDK_INT < 33) return
        runCatching {
            app.getSystemService(LocaleManager::class.java).applicationLocales =
                if (tag == SYSTEM) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
        }
    }

    // Only a right-to-left language Tandem has text for: Android set to Hebrew or Persian shows Tandem in English,
    // which must stay left to right.
    private fun isRtl(l: Locale) = l.language == "ar"
}

/**
 * Text from the string resources, in Tandem's language. Works from any thread and in composables, where it also
 * subscribes to the language (see [Lang.revision]). Never keep what it returns in remember {} or a field without
 * keying it on [Lang.revision]; keep the resource id instead.
 */
object Tx {
    @Volatile private var res: Resources? = null
    @Volatile private var english: Resources? = null
    @Volatile private var localized: Context? = null
    private lateinit var app: Context

    internal val ready get() = res != null

    /** Loads the resources for [locales]. */
    internal fun load(app: Context, locales: LocaleList) {
        this.app = app
        val ctx = app.createConfigurationContext(Configuration(app.resources.configuration).apply { setLocales(locales) })
        localized = ctx
        res = ctx.resources
    }

    private fun r(): Resources {
        Lang.revision.intValue
        return res ?: error("Tx used before Lang.init (TandemApplication)")
    }

    /** A string. */
    fun s(@StringRes id: Int): String = r().getString(id)

    /** A string with its %1$s, %2$d… filled in (text arguments isolated in right to left, see [bidi]). */
    fun s(@StringRes id: Int, vararg args: Any): String = r().getString(id, *bidi(args))

    /** The form of a plural for [n], with %1$d being [n]. */
    fun p(@PluralsRes id: Int, n: Int): String = r().getQuantityString(id, n, n)

    /** The form of a plural for [n], with [args] (put [n] among them yourself). */
    fun p(@PluralsRes id: Int, n: Int, vararg args: Any): String = r().getQuantityString(id, n, *bidi(args))

    /**
     * Names shown as a list ("Snes9x, RetroArch, Dolphin"), joined with the language's comma (、 in Japanese and
     * Chinese, ، in Arabic, where each name is isolated so Latin names keep their order from the right). Only for text
     * shown now: never for text saved, parsed back or logged.
     */
    fun list(items: Iterable<String>): String {
        val sep = s(R.string.common_list_sep)
        return if (Lang.layoutDirection == LayoutDirection.Rtl) items.joinToString(sep) { "⁨$it⁩" } else items.joinToString(sep)
    }

    /** [list] of [items] shown by [label]. */
    fun <T> list(items: Iterable<T>, label: (T) -> String): String = list(items.map(label))

    /**
     * In a right-to-left layout every text argument is a first-strong isolate: a Latin game title, a file path or a
     * URL keeps its own direction inside the Arabic sentence, so its punctuation and slashes stay where they belong
     * ("/ROMs/snes", "Pokémon (USA)!") and it never pulls the words around it out of order. Left to right: as given.
     */
    private fun bidi(args: Array<out Any>): Array<out Any> =
        if (Lang.layoutDirection != LayoutDirection.Rtl) args
        else Array(args.size) { i -> args[i].let { a -> if (a is CharSequence) "\u2068$a\u2069" else a } }

    /**
     * The English text of [id], whatever the language: for stable keys and for search, so an English word still
     * finds a setting in every language. Never shown.
     */
    fun en(@StringRes id: Int): String {
        val e = english ?: app.let { c ->
            c.createConfigurationContext(Configuration(c.resources.configuration).apply { setLocales(LocaleList(Locale.ENGLISH)) }).resources
        }.also { english = it }
        return e.getString(id)
    }

    /**
     * A context in Tandem's language, for Android's own formatters that take one (DateUtils, Formatter). Not for
     * starting activities or anything else that needs an activity.
     */
    val context: Context get() { Lang.revision.intValue; return localized ?: error("Tx used before Lang.init") }
}
