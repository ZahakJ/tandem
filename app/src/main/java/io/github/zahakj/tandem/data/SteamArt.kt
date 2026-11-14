package io.github.zahakj.tandem.data

import io.github.zahakj.tandem.GameFiles
import io.github.zahakj.tandem.Libraries
import io.github.zahakj.tandem.Privacy
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Gives Steam shortcuts (a .steam file holding the app id) the store's own box art, hero and logo, after every
 * library scan: Steam serves them by app id, with no account, key or name search, so they are always the right game.
 * Only kinds a game has none of (from any frontend) are fetched, never hidden games, and nothing in private mode
 * (Net checks it). A game whose download fails is tried again on the next scan.
 */
object SteamArt {
    private val worker = Executors.newSingleThreadExecutor { r ->
        Thread({ android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND); r.run() }, "tandem-steam-art").apply { isDaemon = true }
    }
    private val queued = AtomicBoolean(false)

    private val FILES = listOf(MediaKind.Box to "library_600x900_2x.jpg", MediaKind.Hero to "library_hero.jpg", MediaKind.Logo to "logo.png")

    /** Asks for a pass over the library in the background. Requests while one is waiting share it. */
    fun request() {
        if (!queued.compareAndSet(false, true)) return
        worker.execute {
            queued.set(false)
            runCatching { pass() }
        }
    }

    private fun pass() {
        for (g in Libraries.games) {
            if (!g.file.extension.equals("steam", ignoreCase = true) || g.meta.hidden || Privacy.isHidden(g)) continue
            val id = GameFiles.idIn(GameFiles.firstLine(g.file))?.takeIf { it.all(Char::isDigit) } ?: continue
            for ((kind, name) in FILES) {
                if (Media.has(g, kind)) continue
                val file = runCatching { Media.save(g, kind, Net.bytes("https://cdn.cloudflare.steamstatic.com/steam/apps/$id/$name")) }.getOrNull() ?: continue
                Store.update(g.key) { m -> if (kind.get(m) == null) kind.set(m, file.path) else m }
            }
        }
    }
}
