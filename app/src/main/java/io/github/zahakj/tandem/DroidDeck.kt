package io.github.zahakj.tandem

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log

/**
 * DroidDeck from Tandem's side: whether it is there, its version, and its library through the read-only provider
 * proposed upstream ([DroidDeckContract]; not in DroidDeck 0.7.1). Reading its files as root, and turning its games
 * into Tandem's, is [AppLibraries]'s, shared with GameNative.
 */
object DroidDeck {
    const val PACKAGE = DroidDeckContract.PACKAGE
    private const val TAG = "Tandem"

    fun installed(ctx: Context): Boolean = versionCode(ctx) != null

    /** DroidDeck's versionCode (0.7.1 is 19), or null when it isn't installed. */
    fun versionCode(ctx: Context): Long? = runCatching {
        ctx.packageManager.getPackageInfo(PACKAGE, 0).longVersionCode
    }.getOrNull()

    /**
     * DroidDeck's games through its provider, or null when it has none (or Tandem may not read it): then they are read
     * as root. Every row is a game DroidDeck can start; store games carry their store's id when DroidDeck gives it.
     */
    fun queryProvider(ctx: Context): List<AppGame>? {
        if (ctx.packageManager.resolveContentProvider(DroidDeckContract.AUTHORITY, 0) == null) return null
        if (ctx.checkSelfPermission(DroidDeckContract.PERMISSION) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "DroidDeck's library provider is there, but ${DroidDeckContract.PERMISSION} isn't granted")
            return null
        }
        val c = DroidDeckContract
        val games = try {
            ctx.contentResolver.query(Uri.parse(c.gamesUri()), null, null, null, null)?.use { cur ->
                val id = cur.getColumnIndex(c.COL_APP_ID)
                if (id < 0) return@use null
                fun text(col: String): String? = cur.getColumnIndex(col).takeIf { it >= 0 && !cur.isNull(it) }?.let(cur::getString)
                fun millis(col: String): Long = cur.getColumnIndex(col).takeIf { it >= 0 && !cur.isNull(it) }?.let(cur::getLong)?.coerceAtLeast(0) ?: 0
                val out = ArrayList<AppGame>()
                while (cur.moveToNext()) {
                    val appId = DroidDeckLibrary.normalId(text(c.COL_APP_ID)) ?: continue
                    val source = DroidDeckLibrary.normalSource(text(c.COL_SOURCE))
                    out += AppGame(
                        app = AppLibrary.DROIDDECK,
                        source = source,
                        appId = appId,
                        name = text(c.COL_NAME)?.trim().orEmpty(),
                        installedAt = DroidDeckLibrary.millis(millis(c.COL_INSTALLED_AT)),
                        lastPlayed = DroidDeckLibrary.millis(millis(c.COL_LAST_PLAYED)),
                        art = text(c.COL_CAPSULE_URI),
                        hero = text(c.COL_HERO_URI),
                        logo = text(c.COL_LOGO_URI),
                        icon = text(c.COL_ICON_URI),
                        storeId = if (source == AppGame.STEAM) appId else text(c.COL_STORE_ID)?.trim()?.ifEmpty { null },
                    )
                }
                out.distinctBy { it.id }
            }
        } catch (e: Exception) {
            Log.w(TAG, "DroidDeck's library provider failed", e)
            null
        } ?: return null
        watch(ctx)
        return games
    }

    @Volatile private var watching = false

    /** While Tandem runs, a change to DroidDeck's library (it notifies on every save) brings a quiet rescan. */
    private fun watch(ctx: Context) {
        if (watching) return
        val app = ctx.applicationContext
        val main = android.os.Handler(android.os.Looper.getMainLooper())
        val observer = object : android.database.ContentObserver(main) {
            override fun onChange(selfChange: Boolean) {
                io.github.zahakj.tandem.ui.LibraryScan.request(app, announce = false, onlyIfChanged = true)
            }
        }
        runCatching {
            app.contentResolver.registerContentObserver(Uri.parse(DroidDeckContract.gamesUri()), true, observer)
            watching = true
        }.onFailure { Log.w(TAG, "Couldn't watch DroidDeck's library", it) }
    }
}
