package io.github.zahakj.tandem

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import io.github.zahakj.tandem.data.HomeStore
import java.util.concurrent.Executors

/*
 * Immersion mode: nothing from Android cuts into a game. While a game Tandem started is playing, Do Not Disturb is
 * on with a policy of Tandem's own: no notification pop-ups, sounds, vibrations, lights or badges; only alarms, and
 * the game's own sound, get through. When the session ends (Tandem comes back) the filter and the policy go back to
 * what they were.
 *
 * - Settings › Play & system › Playing › Immersion mode, on by default. It needs Android's Do Not Disturb access for
 *   Tandem (an enabled notification listener, Pip's notes, counts as that): else, with Shizuku, Tandem allows itself
 *   the first time; without either, the setting's row opens Android's list.
 * - Do Not Disturb the user turned on themselves is left alone, and so never turned off by a game ending.
 * - What was changed is written down first (preferences), so a Tandem restart puts it back unless a game is still
 *   being played, and a Do Not Disturb the user changed mid-game isn't undone: only Tandem's own filter is reset.
 *
 * [ImmersionPlan] is plain, for tests (ImmersionTest).
 */

/** Android's interruption filter and notification policy, as far as Tandem reads and sets them. */
interface Quiet {
    /** The current interruption filter ([NotificationManager.INTERRUPTION_FILTER_ALL] when Do Not Disturb is off), or null when unknown. */
    fun filter(): Int?
    fun setFilter(filter: Int): Boolean
    /** The saved Do Not Disturb policy, or null when it can't be read. */
    fun policy(): QuietPolicy?
    fun setPolicy(p: QuietPolicy): Boolean
}

/** A notification policy's numbers, as [NotificationManager.Policy] holds them. */
data class QuietPolicy(val categories: Int, val callSenders: Int, val messageSenders: Int, val suppressedEffects: Int, val conversationSenders: Int)

/** What Tandem changed, to put it back: the filter and policy before the game. */
data class QuietChanges(val filter: Int, val policy: QuietPolicy)

object ImmersionPlan {
    const val ALL = NotificationManager.INTERRUPTION_FILTER_ALL
    const val PRIORITY = NotificationManager.INTERRUPTION_FILTER_PRIORITY

    /**
     * The policy while a game plays: alarms and media (the game's sound) only; nothing shows, lights up, peeks or
     * badges. Notifications still land in the shade for later.
     */
    val quiet = QuietPolicy(
        categories = NotificationManager.Policy.PRIORITY_CATEGORY_ALARMS or NotificationManager.Policy.PRIORITY_CATEGORY_MEDIA,
        callSenders = NotificationManager.Policy.PRIORITY_SENDERS_CONTACTS,
        messageSenders = NotificationManager.Policy.PRIORITY_SENDERS_CONTACTS,
        suppressedEffects = NotificationManager.Policy.SUPPRESSED_EFFECT_FULL_SCREEN_INTENT or NotificationManager.Policy.SUPPRESSED_EFFECT_LIGHTS or
            NotificationManager.Policy.SUPPRESSED_EFFECT_PEEK or NotificationManager.Policy.SUPPRESSED_EFFECT_STATUS_BAR or
            NotificationManager.Policy.SUPPRESSED_EFFECT_BADGE or NotificationManager.Policy.SUPPRESSED_EFFECT_AMBIENT,
        conversationSenders = NotificationManager.Policy.CONVERSATION_SENDERS_NONE,
    )

    /**
     * Goes quiet. Already changed for an earlier game ([had]): as is. Do Not Disturb already on (any filter but
     * [ALL]), or a filter or policy that can't be read: left alone, nothing to put back. Returns what was changed.
     */
    fun begin(had: QuietChanges?, q: Quiet): QuietChanges? {
        if (had != null) return had
        val filter = q.filter() ?: return null
        if (filter != ALL) return null
        val before = q.policy() ?: return null
        if (!q.setPolicy(quiet)) return null
        val changes = QuietChanges(filter, before)
        if (!q.setFilter(PRIORITY)) { q.setPolicy(before); return null }
        return changes
    }

    /**
     * Puts back what [had] says was changed. The filter only while it is still Tandem's ([PRIORITY]): a user who
     * turned Do Not Disturb off, or to another mode, mid-game keeps that. The policy always, since it is theirs.
     * Returns what is still to put back (a step that failed, to try again), or null when all is back.
     */
    fun end(had: QuietChanges, q: Quiet): QuietChanges? {
        val filterBack = q.filter() != PRIORITY || q.setFilter(had.filter)
        val policyBack = q.setPolicy(had.policy)
        return if (filterBack && policyBack) null else had
    }
}

/** Android's own filter and policy, through [NotificationManager]. Needs Do Not Disturb access, else every call fails. */
private class AndroidQuiet(private val nm: NotificationManager) : Quiet {
    override fun filter(): Int? = runCatching { nm.currentInterruptionFilter }.getOrNull()?.takeIf { it != NotificationManager.INTERRUPTION_FILTER_UNKNOWN }

    // The change lands a moment later (reading it back at once still shows the old filter), so a call that didn't throw counts.
    override fun setFilter(filter: Int): Boolean = runCatching { nm.setInterruptionFilter(filter); true }.getOrDefault(false)

    override fun policy(): QuietPolicy? = runCatching { nm.notificationPolicy }.getOrNull()?.let {
        QuietPolicy(it.priorityCategories, it.priorityCallSenders, it.priorityMessageSenders, it.suppressedVisualEffects, it.priorityConversationSenders)
    }

    override fun setPolicy(p: QuietPolicy): Boolean = runCatching {
        nm.notificationPolicy = NotificationManager.Policy(p.categories, p.callSenders, p.messageSenders, p.suppressedEffects, p.conversationSenders)
        true
    }.getOrDefault(false)
}

object Immersion {
    private const val TAG = "Tandem"
    private const val PREFS = "immersion"

    /** Every change runs here, one at a time and in order. */
    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "tandem-immersion") }

    private fun nm(ctx: Context) = ctx.applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    /** Bumped (main thread) when Tandem gained Do Not Disturb access, so Settings re-reads [granted]. */
    var changed by mutableIntStateOf(0)
        private set

    /** Whether the setting is on. It only does anything once [granted]. */
    fun on(): Boolean = HomeStore.settings.immersion

    /** Whether Android lets Tandem change Do Not Disturb. */
    fun granted(ctx: Context): Boolean = runCatching { nm(ctx).isNotificationPolicyAccessGranted }.getOrDefault(false)

    /**
     * Allows Tandem Do Not Disturb access through Shizuku (`cmd notification allow_dnd`), the way Android's own
     * settings list would. Blocking; true when it is granted afterwards.
     */
    fun allow(ctx: Context): Boolean {
        if (granted(ctx)) return true
        if (!Shell.ready()) return false
        Shell.run("cmd", "notification", "allow_dnd", ctx.packageName) ?: return false
        val ok = granted(ctx)
        Log.i(TAG, if (ok) "Allowed Do Not Disturb access through Shizuku" else "Shizuku couldn't allow Do Not Disturb access")
        if (ok) android.os.Handler(android.os.Looper.getMainLooper()).post { changed++ }
        return ok
    }

    /** Android's list of apps allowed to change Do Not Disturb, for the row to open when Shizuku can't allow it. */
    fun settingsIntent(): Intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** The game's session began (Sessions.start): quiet, if wanted. Any thread; returns at once. */
    fun started(ctx: Context) {
        if (!on()) return
        val app = ctx.applicationContext
        worker.execute {
            if (!granted(app) && !allow(app)) { Log.i(TAG, "Immersion mode needs Do Not Disturb access"); return@execute }
            val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val had = load(prefs)
            val changes = ImmersionPlan.begin(had, AndroidQuiet(nm(app)))
            if (changes != null && changes != had) {
                save(prefs, changes)
                Log.i(TAG, "Quiet for the game: Do Not Disturb on")
            } else if (changes == null) Log.i(TAG, "Immersion mode left Do Not Disturb as it was")
        }
    }

    /** The game's session is over: Do Not Disturb back as it was. Any thread; returns at once. */
    fun gameEnded(ctx: Context) {
        val app = ctx.applicationContext
        worker.execute {
            val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val had = load(prefs) ?: return@execute
            if (!granted(app)) return@execute
            val left = ImmersionPlan.end(had, AndroidQuiet(nm(app)))
            save(prefs, left)
            Log.i(TAG, if (left == null) "Do Not Disturb back as it was" else "Couldn't put Do Not Disturb back yet")
        }
    }

    /** At startup: a change left from before a restart is put back unless a game is still being played. */
    fun startup(ctx: Context) {
        if (Sessions.openKey(ctx) == null) gameEnded(ctx)
    }

    private fun save(prefs: android.content.SharedPreferences, c: QuietChanges?) {
        val e = prefs.edit()
        if (c == null) e.clear() else {
            val p = c.policy
            e.putInt("filter", c.filter).putInt("categories", p.categories).putInt("calls", p.callSenders).putInt("messages", p.messageSenders)
                .putInt("effects", p.suppressedEffects).putInt("conversations", p.conversationSenders)
        }
        e.commit()
    }

    private fun load(prefs: android.content.SharedPreferences): QuietChanges? {
        if (!prefs.contains("filter")) return null
        return QuietChanges(
            prefs.getInt("filter", ImmersionPlan.ALL),
            QuietPolicy(prefs.getInt("categories", 0), prefs.getInt("calls", 0), prefs.getInt("messages", 0), prefs.getInt("effects", 0), prefs.getInt("conversations", 0)),
        )
    }
}
