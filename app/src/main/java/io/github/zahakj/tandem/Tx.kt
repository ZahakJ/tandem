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
     * Right-to-left screens wait for the RTL pass (docs/TRANSLATING.md lists what still assumes left to right):
     * until then Arabic text runs right to left inside each line, but every screen keeps its left-to-right layout,
     * which is how Tandem has always looked on an Arabic Android. Turning this on is that pass's last step.
     */
    const val RTL_READY = false

    /** The tag picked in Settings, or [SYSTEM]. */
    var chosen by mutableStateOf(SYSTEM)
        private set

    /** The locale text is in now: the chosen language, or Android's for [SYSTEM]. Any thread. */
    @Volatile var locale: Locale = Locale.getDefault()
        private set

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
            if (system == null && tag != SYSTEM && !prefs.getBoolean("handed", false)) setSystemChoice(tag)
            else if (system != null) tag = system
            prefs.edit().putBoolean("handed", true).putString(KEY, tag).apply()
        }
        chosen = tag
        apply()
        // Android's language changing (or, on 13+, Tandem's in Android's own settings), and every other
        // configuration change, which on 11 and 12 also puts Android's default locale back.
        app.registerComponentCallbacks(object : ComponentCallbacks {
            override fun onConfigurationChanged(newConfig: Configuration) = refresh()
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

    /** The configuration changed: the language may have (Android's, or Tandem's in Android's settings). */
    private fun refresh() {
        if (Build.VERSION.SDK_INT >= 33) systemChoice().orEmpty().let { if (it != chosen) chosen = it }
        apply()
    }

    private fun apply() {
        val want = if (chosen == SYSTEM) systemLocale() else Locale.forLanguageTag(chosen)
        // On 11 and 12 Android's default locale is Android's language: set it, so dates, numbers and anything else
        // formatted with the default locale speak the same language as the text. (13+ does this itself.)
        if (Build.VERSION.SDK_INT < 33) {
            val list = if (chosen == SYSTEM) Resources.getSystem().configuration.locales else LocaleList(want)
            if (LocaleList.getDefault() != list) LocaleList.setDefault(list)
        }
        val changed = want != locale || !Tx.ready
        locale = want
        Tx.load(app, if (chosen == SYSTEM) null else want)
        if (changed) revision.intValue++
    }

    /** Android's own language, whatever Tandem's is. */
    private fun systemLocale(): Locale =
        if (Build.VERSION.SDK_INT >= 33) app.getSystemService(LocaleManager::class.java).systemLocales[0] ?: Locale.getDefault()
        else Resources.getSystem().configuration.locales[0] ?: Locale.getDefault()

    /** The app language Android keeps (13+): a tag, or null for none (follow the system). */
    private fun systemChoice(): String? {
        if (Build.VERSION.SDK_INT < 33) return null
        val list = runCatching { app.getSystemService(LocaleManager::class.java).applicationLocales }.getOrNull() ?: return null
        if (list.isEmpty) return null
        val l = list[0]
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

    /** The name of the language in use, in itself (the Language row's value). */
    fun nameOf(tag: String): String = offered.firstOrNull { it.tag == tag }?.name ?: Tx.s(R.string.common_language_system)

    private fun isRtl(l: Locale) = l.language in setOf("ar", "fa", "he", "iw", "ur")
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

    /** Loads the resources for [locale] (null: Android's own language, the app's plain resources). */
    internal fun load(app: Context, locale: Locale?) {
        this.app = app
        val ctx = if (locale == null) app else app.createConfigurationContext(
            Configuration(app.resources.configuration).apply { setLocales(LocaleList(locale)) },
        )
        localized = ctx
        res = ctx.resources
    }

    private fun r(): Resources {
        Lang.revision.intValue
        return res ?: error("Tx used before Lang.init (TandemApplication)")
    }

    /** A string. */
    fun s(@StringRes id: Int): String = r().getString(id)

    /** A string with its %1$s, %2$d… filled in. */
    fun s(@StringRes id: Int, vararg args: Any): String = r().getString(id, *args)

    /** The form of a plural for [n], with %1$d being [n]. */
    fun p(@PluralsRes id: Int, n: Int): String = r().getQuantityString(id, n, n)

    /** The form of a plural for [n], with [args] (put [n] among them yourself). */
    fun p(@PluralsRes id: Int, n: Int, vararg args: Any): String = r().getQuantityString(id, n, *args)

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
