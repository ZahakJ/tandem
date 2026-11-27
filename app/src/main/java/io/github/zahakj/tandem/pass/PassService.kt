package io.github.zahakj.tandem.pass

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import io.github.zahakj.tandem.MainActivity
import io.github.zahakj.tandem.R
import io.github.zahakj.tandem.Tx

/**
 * "Pass while playing": keeps Tandem Pass going while a game is in front, as a connected-device foreground service
 * with its own quiet notification, so it is plain that Bluetooth is in use. Started from Tandem (never from the
 * background) when the setting is on, stopped when it goes off, Tandem Pass goes off or private mode comes on.
 * While Tandem itself is in front the radio scans at its usual pace; behind a game, at a slower one.
 */
class PassService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val ok = runCatching { startForeground(NOTE, note(), ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE) }.isSuccess
        if (!ok) { stopSelf(); return START_NOT_STICKY }
        TandemPass.service(this, true)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        TandemPass.service(this, false)
        super.onDestroy()
    }

    private fun note(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, Tx.s(R.string.plaza_note_channel), NotificationManager.IMPORTANCE_MIN))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(Tx.s(R.string.plaza_note_title)).setContentText(Tx.s(R.string.plaza_note_text))
            .setContentIntent(open).setOngoing(true).build()
    }

    companion object {
        private const val CHANNEL = "tandem-pass"
        private const val NOTE = 7311

        fun start(ctx: Context) {
            runCatching { ctx.startForegroundService(Intent(ctx, PassService::class.java)) }
                .onFailure { android.util.Log.w("TandemPass", "service: ${it.javaClass.simpleName}") }
        }

        fun stop(ctx: Context) {
            runCatching { ctx.stopService(Intent(ctx, PassService::class.java)) }
        }
    }
}
