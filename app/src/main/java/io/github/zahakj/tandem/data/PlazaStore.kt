package io.github.zahakj.tandem.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.zahakj.tandem.pass.PlazaData
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.Executors

/**
 * `plaza.json` in the app's files folder: Tandem Pass's switches, your card (Me), the people met and the keys
 * blocked ([PlazaData], versioned by its `v`). Compose state in memory, written atomically on a background thread like
 * the other stores; in Tandem's backups with the game data, never in a shared look, and kept out of Android's cloud
 * backup (it holds other people's cards). The identity's seed is not here: IdentityStore keeps it apart.
 */
object PlazaStore {
    const val FILE = "plaza.json"
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private val writer = Executors.newSingleThreadExecutor()
    private var dir: File? = null

    var data by mutableStateOf(PlazaData())
        private set

    fun init(ctx: Context) {
        if (dir != null) return
        dir = ctx.applicationContext.filesDir
        reload()
    }

    /** Reads plaza.json again (after a restore). Blocking. */
    fun reload() {
        val d = dir ?: return
        val s = runCatching { decode(File(d, FILE).readText()) }.getOrDefault(PlazaData())
        synchronized(this) { data = s }
    }

    /** Changes the data; safe from any thread (the radio's results arrive on its own). */
    fun update(change: (PlazaData) -> PlazaData) {
        val (before, snapshot) = synchronized(this) { data.let { b -> b to change(b).also { data = it } } }
        if (snapshot == before) return
        val d = dir ?: return
        // Encoded on the writer thread too, in order: the people met make it long, and Me saves on every change.
        writer.execute {
            val text = json.encodeToString(snapshot)
            val tmp = File(d, "$FILE.tmp")
            tmp.writeText(text)
            tmp.renameTo(File(d, FILE))
        }
    }

    fun encode(s: PlazaData): String = json.encodeToString(s)

    /** Reads a plaza.json; a newer version's unknown fields are ignored, characters out of range are tidied. */
    fun decode(text: String): PlazaData = json.decodeFromString<PlazaData>(text).let { p ->
        p.copy(me = p.me.copy(avatar = p.me.avatar.clean()), visitors = p.visitors.map { it.copy(avatar = it.avatar.clean()) })
    }
}
