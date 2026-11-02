package io.github.zahakj.tandem

import android.app.Notification
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationManagerCompat

/** One notification as the side panel shows it. */
data class Note(
    val key: String,
    val pkg: String,
    val title: String,
    val text: String,
    val time: Long,
    val clearable: Boolean,
    val open: PendingIntent?,
)

/**
 * The notifications on the device, for the side panel (ui/SidePanel.kt). Android only shares them with an app the
 * user allowed (Settings › Notification access), which [NoteListener] then is; with Shizuku Tandem can allow itself.
 * Nothing leaves the device: the list lives in memory while the listener is connected.
 */
object Notes {
    private const val TAG = "Tandem"

    var list by mutableStateOf<List<Note>>(emptyList())
        internal set
    /** The listener is bound: the list is live. */
    var connected by mutableStateOf(false)
        internal set

    internal var service: NoteListener? = null

    fun allowed(ctx: Context): Boolean = ctx.packageName in NotificationManagerCompat.getEnabledListenerPackages(ctx)

    fun component(ctx: Context) = ComponentName(ctx, NoteListener::class.java)

    /** Lets Tandem read notifications through Shizuku. Blocking. True when it worked. */
    fun allowWithShizuku(ctx: Context): Boolean {
        if (!Shell.ready()) return false
        Shell.run("cmd", "notification", "allow_listener", component(ctx).flattenToString())
        return allowed(ctx)
    }

    /** Opens what [n] points at (and, as Android does, clears it when it asks to be). */
    fun open(n: Note) {
        runCatching { n.open?.send() }.onFailure { Log.w(TAG, "Couldn't open a notification from ${n.pkg}", it) }
        if (n.clearable) dismiss(n)
    }

    fun dismiss(n: Note) {
        runCatching { service?.cancelNotification(n.key) }
        list = list.filter { it.key != n.key }
    }

    /** Clears every notification that can be cleared. */
    fun clearAll() {
        runCatching { service?.cancelAllNotifications() }
        list = list.filter { !it.clearable }
    }

    /** What is worth showing: not Tandem's own, not group summaries, not empty. Plain, for tests. */
    fun keep(pkg: String, own: String, summary: Boolean, title: String, text: String): Boolean =
        pkg != own && !summary && (title.isNotBlank() || text.isNotBlank())

    internal fun read(own: String, sbns: Array<StatusBarNotification>?) {
        list = sbns.orEmpty().mapNotNull { s ->
            val n = s.notification ?: return@mapNotNull null
            val e = n.extras
            val title = e?.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
            val text = (e?.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: e?.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()
            val summary = n.flags and Notification.FLAG_GROUP_SUMMARY != 0
            if (!keep(s.packageName, own, summary, title, text)) return@mapNotNull null
            Note(s.key, s.packageName, title, text, s.postTime, s.isClearable, n.contentIntent)
        }.sortedByDescending { it.time }
    }
}

/** Android's link to the notifications (see [Notes]). */
class NoteListener : NotificationListenerService() {
    override fun onListenerConnected() {
        Notes.service = this
        Notes.connected = true
        refresh()
    }

    override fun onListenerDisconnected() {
        if (Notes.service === this) Notes.service = null
        Notes.connected = false
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) = refresh()

    override fun onNotificationRemoved(sbn: StatusBarNotification?) = refresh()

    private fun refresh() {
        Notes.read(packageName, runCatching { activeNotifications }.getOrNull())
    }
}
