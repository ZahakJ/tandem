package io.github.zahakj.tandem.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.Executors

/**
 * `week.json` in the app's files folder: This week's switch, salt, not-for-me list, what it showed lately and this
 * week's re-rolls ([WeekState]). Compose state in memory, written atomically on a background thread like the other
 * stores; in Tandem's backups with the game data, never in a shared look.
 */
object WeekStore {
    const val FILE = "week.json"
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private val writer = Executors.newSingleThreadExecutor()
    private var dir: File? = null

    var state by mutableStateOf(WeekState())
        private set

    fun init(ctx: Context) {
        if (dir != null) return
        dir = ctx.applicationContext.filesDir
        reload()
    }

    /** Reads week.json again (after a restore). Blocking. */
    fun reload() {
        val d = dir ?: return
        val s = runCatching { json.decodeFromString<WeekState>(File(d, FILE).readText()) }.getOrDefault(WeekState())
        synchronized(this) { state = s }
        // The salt is made once, on the device, the first time the board is wanted.
        if (s.salt == 0L) update { it.copy(salt = kotlin.random.Random.nextLong().takeIf { r -> r != 0L } ?: 1L) }
    }

    fun update(change: (WeekState) -> WeekState) {
        val (before, snapshot) = synchronized(this) { state.let { b -> b to change(b).also { state = it } } }
        if (snapshot == before) return
        val d = dir ?: return
        val text = json.encodeToString(snapshot)
        writer.execute {
            val tmp = File(d, "$FILE.tmp")
            tmp.writeText(text)
            tmp.renameTo(File(d, FILE))
        }
    }

    fun encode(s: WeekState): String = json.encodeToString(s)

    fun decode(text: String): WeekState = json.decodeFromString(text)
}
