package io.github.zahakj.tandem

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * What the bottom-screen companion showed, kept on disk while a game plays, so [GameCompanion.restore] can bring it
 * back after Tandem's process restarts mid-game. The checks are plain functions so they can be tested without Android.
 */
object CompanionRestore {
    private const val PREFS = "companion"
    /** How long to wait for Shizuku's binder in a process that has only just started. */
    const val SHIZUKU_WAIT_MS = 10_000L
    /** A companion older than this is stale (the same cap as a play session). */
    const val MAX_AGE_MS = 6 * 60 * 60 * 1000L

    /** Remembers [info] for a restart, or forgets the companion (null). Safe from any thread. */
    fun save(ctx: Context, info: CompanionInfo?) {
        val edit = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear()
        if (info != null) {
            edit.putString("key", info.key).putString("title", info.title).putString("system", info.system)
                .putString("systemId", info.systemId).putString("hero", info.hero).putString("box", info.box)
                .putString("logo", info.logo).putString("mode", info.mode).putString("pkg", info.pkg)
                .putLong("start", info.start).putLong("earlier", info.earlier)
        }
        edit.apply()
    }

    fun load(ctx: Context): CompanionInfo? {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key = p.getString("key", null) ?: return null
        return CompanionInfo(
            key, p.getString("title", null).orEmpty(), p.getString("system", null).orEmpty(), p.getString("systemId", null).orEmpty(),
            p.getString("hero", null), p.getString("box", null), p.getString("logo", null),
            p.getString("mode", null) ?: GameCompanion.ART, p.getString("pkg", null), p.getLong("start", 0L), p.getLong("earlier", 0L),
        )
    }

    /**
     * Whether the companion recorded as [info] should come back: the game it was opened for ([CompanionInfo.pkg])
     * is the app in front on the main display ([frontPkg]), its play session is still open ([sessionKey]: Tandem
     * hasn't come back since), it isn't hidden, and there is a bottom screen for it.
     */
    fun shouldRestore(
        info: CompanionInfo?,
        frontPkg: String?,
        sessionKey: String?,
        hidden: Boolean,
        singleScreen: Boolean,
        hasSecondDisplay: Boolean,
        now: Long,
    ): Boolean {
        info ?: return false
        val pkg = info.pkg
        return info.mode != GameCompanion.OFF && !pkg.isNullOrEmpty() && frontPkg == pkg && sessionKey == info.key &&
            !hidden && !singleScreen && hasSecondDisplay && now - info.start in 0..MAX_AGE_MS
    }

    private val record = Regex("""ActivityRecord\{\S+ u\d+ ([A-Za-z0-9_.]+)/""")
    /** "ResumedActivity: …", "mResumedActivity: …" or "topResumedActivity=…" (not mLastResumedActivity and the like). */
    private val resumed = Regex("""^(?:m|top)?ResumedActivity\s*[:=]""")

    /**
     * The package of the activity resumed on display [displayId], from `dumpsys activity activities`: the first
     * resumed activity listed in that display's section (tasks are listed top to bottom). Older layouts without
     * display sections fall back to the global `topResumedActivity`. Null when nothing is resumed there.
     */
    fun frontPackage(dump: String, displayId: Int = 0): String? {
        var sections = false
        var inside = false
        for (raw in dump.lineSequence()) {
            val line = raw.trim()
            if (line.startsWith("Display #")) {
                sections = true
                inside = line.removePrefix("Display #").takeWhile { it.isDigit() } == displayId.toString()
                continue
            }
            if (inside && resumed.containsMatchIn(line)) {
                record.find(line)?.let { return it.groupValues[1] }
            }
        }
        if (sections) return null
        return dump.lineSequence().map { it.trim() }.firstOrNull { it.startsWith("topResumedActivity=") }
            ?.let { record.find(it)?.groupValues?.get(1) }
    }
}

/**
 * Tandem was just updated (`adb install -r`, a store update). The update killed Tandem's process, and with it the
 * companion under a game that may still be playing: bring it back ([GameCompanion.restore]).
 */
class PackageReplacedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pending = goAsync()
        GameCompanion.restore(context) { runCatching { pending.finish() } }
    }
}
