package io.github.zahakj.tandem

import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.concurrent.Executors

/**
 * Which apps are installed. Read once, then kept up to date from the system's package broadcasts, so checking an
 * emulator never touches the package manager on the UI thread.
 */
object Emulators {
    /** Compose state: menus and the Systems screen update the moment an emulator is installed or removed. */
    var installed by mutableStateOf<Set<String>>(emptySet())
        private set

    /** Bumped on every package change; lets the library pick up newly installed Android games. */
    var changes by mutableIntStateOf(0)
        private set

    @Volatile private var ready = false
    private val worker = Executors.newSingleThreadExecutor()

    fun isInstalled(pkg: String): Boolean = pkg in installed

    /** Reads the installed list the first time; later calls are free. Safe from any thread. */
    fun refresh(ctx: Context) {
        if (ready) return
        synchronized(this) {
            if (ready) return
            val app = ctx.applicationContext
            installed = read(app)
            listen(app)
            ready = true
        }
    }

    private fun read(ctx: Context): Set<String> =
        runCatching { ctx.packageManager.getInstalledApplications(0).map { it.packageName }.toSet() }.getOrDefault(emptySet())

    private fun listen(ctx: Context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                // Re-read off the main thread; a whole list is simpler and safer than patching the set.
                worker.execute {
                    installed = read(ctx)
                    changes++
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        if (Build.VERSION.SDK_INT >= 33) ctx.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        else ctx.registerReceiver(receiver, filter)
    }

    private val OBTAINIUM = listOf("dev.imranr.obtainium", "dev.imranr.obtainium.fdroid")

    /**
     * Opens the place to get [spec]'s emulator. A GitHub (or Codeberg/GitLab) releases page goes to Obtainium when it is
     * installed, so it keeps the emulator updated; otherwise the page opens in the browser. Without a page, the Play
     * Store listing for the package is used.
     */
    fun openStore(ctx: Context, spec: LaunchSpec): Boolean {
        val page = spec.store
        val tries = buildList {
            if (page != null) {
                val obtainium = OBTAINIUM.firstOrNull { isInstalled(it) }
                val trackable = listOf("github.com", "codeberg.org", "gitlab.com").any { page.contains(it) }
                // Obtainium's documented "add app" link: obtainium://add/<url>.
                if (obtainium != null && trackable) add(Intent(Intent.ACTION_VIEW, Uri.parse("obtainium://add/$page")))
                add(Intent(Intent.ACTION_VIEW, Uri.parse(page)))
            } else {
                add(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${spec.packageName}")))
                add(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=${spec.packageName}")))
            }
        }
        for (intent in tries) {
            try {
                ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return true
            } catch (e: ActivityNotFoundException) {
                Log.i("Tandem", "Nothing opens ${intent.data}")
            } catch (e: SecurityException) {
                Log.w("Tandem", "Not allowed to open ${intent.data}", e)
            }
        }
        return false
    }
}
