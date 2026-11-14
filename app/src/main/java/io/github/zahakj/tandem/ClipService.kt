package io.github.zahakj.tandem

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.widget.Toast

/**
 * Records one clip with its sound (GameClip.kt). Android lets an app catch other apps' sound only from a foreground
 * service holding a screen-capture grant, so this runs for the length of the clip, with its notification, and stops.
 */
class ClipService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        @Suppress("DEPRECATION") val grant = intent?.getParcelableExtra<Intent>(X_GRANT)
        val title = intent?.getStringExtra(X_TITLE)
        if (grant == null || title == null) { stopSelf(); return START_NOT_STICKY }
        val gameOnTop = intent.getBooleanExtra(X_ON_TOP, true)
        val screen = intent.getIntExtra(X_SCREEN, android.view.Display.DEFAULT_DISPLAY)
        val voice = intent.getBooleanExtra(X_VOICE, false) &&
            checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val type = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION or (if (voice) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0)
        startForeground(NOTE, note(), type)
        val projection = runCatching {
            getSystemService(MediaProjectionManager::class.java).getMediaProjection(intent.getIntExtra(X_CODE, 0), grant)
        }.getOrNull()
        Thread({
            val r = try {
                GameClip.record(applicationContext, title, gameOnTop, projection, voice)
            } finally {
                projection?.stop()
            }
            say(this, said(r), screen)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }, "tandem-clip").start()
        return START_NOT_STICKY
    }

    private fun note(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Clips", NotificationManager.IMPORTANCE_LOW))
        return Notification.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("Recording a clip").setOngoing(true).build()
    }

    companion object {
        private const val CHANNEL = "clips"
        private const val NOTE = 7301
        private const val X_GRANT = "grant"
        private const val X_CODE = "code"
        private const val X_TITLE = "title"
        private const val X_ON_TOP = "onTop"
        private const val X_VOICE = "voice"
        private const val X_SCREEN = "screen"

        /** [screen]: the companion's display, where the result shows (never over the game). */
        fun start(ctx: Context, code: Int, grant: Intent, title: String, gameOnTop: Boolean, voice: Boolean, screen: Int) {
            ctx.startForegroundService(
                Intent(ctx, ClipService::class.java).putExtra(X_GRANT, grant).putExtra(X_CODE, code)
                    .putExtra(X_TITLE, title).putExtra(X_ON_TOP, gameOnTop).putExtra(X_VOICE, voice).putExtra(X_SCREEN, screen),
            )
        }

        /** What a clip's result says, in a toast. */
        fun said(r: GameClip.Result): String = when (r) {
            is GameClip.Result.Saved -> if (r.sound) "Clip saved to Gallery" else "Clip saved to Gallery, without sound"
            is GameClip.Result.Failed -> r.why
        }

        /** A toast on [screen] (the companion's), so it never lands in the game's picture. */
        fun say(ctx: Context, text: String, screen: Int) {
            val app = ctx.applicationContext
            val there = app.getSystemService(android.hardware.display.DisplayManager::class.java).getDisplay(screen)
                ?.let { app.createDisplayContext(it) } ?: app
            Handler(Looper.getMainLooper()).post { Toast.makeText(there, text, Toast.LENGTH_SHORT).show() }
        }
    }
}
