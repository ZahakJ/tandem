package io.github.zahakj.tandem.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.Executors

/** The user's Steam Web API key and account (SteamID64), with the public name and picture Steam gave for it. */
@Serializable
data class SteamAccount(
    val key: String = "",
    val id: String = "",
    val name: String = "",
    val avatar: String = "",
) {
    /** Both halves are there: lookups may go out. */
    val signedIn: Boolean get() = key.isNotBlank() && id.isNotBlank()
}

/**
 * `steam.json` in the app's files folder, kept like `achievements.json` ([HomeStore.ra]): Compose state in memory,
 * written atomically on a background thread, out of Android's backups (it holds the key), and in Tandem's own
 * backups only with Account keys and logins.
 */
object SteamStore {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private val writer = Executors.newSingleThreadExecutor()
    private var dir: File? = null

    var account by mutableStateOf(SteamAccount())
        private set

    fun init(ctx: Context) {
        if (dir != null) return
        dir = ctx.applicationContext.filesDir
        reload()
    }

    /** Reads steam.json again (after a restore). Blocking. */
    fun reload() {
        val d = dir ?: return
        val a = runCatching { json.decodeFromString<SteamAccount>(File(d, "steam.json").readText()) }.getOrDefault(SteamAccount())
        synchronized(this) { account = a }
    }

    fun update(change: (SteamAccount) -> SteamAccount) {
        val snapshot = synchronized(this) { change(account).also { account = it } }
        val d = dir ?: return
        val text = json.encodeToString(snapshot)
        writer.execute {
            val file = File(d, "steam.json")
            val tmp = File(d, "steam.json.tmp")
            tmp.writeText(text)
            tmp.renameTo(file)
        }
    }
}
