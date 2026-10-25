package io.github.zahakj.tandem.ui

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import io.github.zahakj.tandem.Game
import io.github.zahakj.tandem.Library
import io.github.zahakj.tandem.Privacy
import io.github.zahakj.tandem.Screensaver
import io.github.zahakj.tandem.Selected
import io.github.zahakj.tandem.Selection
import io.github.zahakj.tandem.data.JingleLookup
import io.github.zahakj.tandem.data.Store
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors

/**
 * A game's own hover jingle ([JingleLookup]): it starts after you rest on the game for [REST_MS] (so passing over
 * games while scrolling plays nothing), fades out when you move off, and follows the sound settings (UI sounds on,
 * the sound volume, Play jingles). Never during a game, the launch, a popup or the screensaver. Files are opened
 * and prepared on a background thread; the music dips while one plays.
 */
object GameJingles {
    const val REST_MS = 600L
    private const val FADE_IN_MS = 160L
    private const val FADE_OUT_MS = 450L
    private const val TICK_MS = 30L

    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "tandem-jingle").apply { priority = Thread.MIN_PRIORITY } }
    private var player: MediaPlayer? = null
    private var gain = 0f
    private var target = 0f
    private var fadeMs = FADE_IN_MS
    /** Bumped for every start and stop, so a file that finishes preparing late never plays over something newer. */
    @Volatile private var token = 0

    /** The game whose jingle is playing (or being prepared). */
    var playingKey: String? = null
        private set

    /** Jingles may play at all right now, by the settings. */
    fun allowed(): Boolean {
        val look = Store.settings.look
        return look.sounds && look.gameJingles && look.soundVolume > 0f
    }

    /** The jingle [game] would play, if it has its own. Blocking (checks files). */
    fun fileFor(game: Game): File? =
        (JingleLookup.find(game.meta, Library.romsDir, game.platform.artIds, game.stem) as? JingleLookup.Source.Own)?.file

    /** Plays [file] for [key] from the start (main thread). [preview]: from Edit details, also with Play jingles off. */
    fun play(key: String, file: File, preview: Boolean = false) {
        val look = Store.settings.look
        if (!look.sounds || (!preview && !allowed())) return
        stopNow()
        val mine = ++token
        playingKey = key
        worker.execute {
            val p = runCatching {
                MediaPlayer().apply {
                    setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                    setDataSource(file.path)
                    setVolume(0f, 0f)
                    prepare()
                }
            }.onFailure { Log.w("Tandem", "Couldn't play jingle ${file.name}", it) }.getOrNull() ?: return@execute
            main.post {
                if (mine != token) { p.release(); return@post }
                player = p
                p.setOnCompletionListener { if (player === it) stopNow() }
                runCatching { p.start() }
                Music.underJingle(true)
                gain = 0f
                fadeTo(1f, FADE_IN_MS)
            }
        }
    }

    /** Eases the jingle out (moving off the game, a popup, a launch). Safe to call when nothing plays. */
    fun fadeOut() {
        if (Looper.myLooper() != Looper.getMainLooper()) { main.post { fadeOut() }; return }
        token++
        playingKey = null
        if (player == null) return
        fadeTo(0f, FADE_OUT_MS)
    }

    /** Stops at once (Tandem left the screen). Main thread. */
    fun stopNow() {
        token++
        playingKey = null
        main.removeCallbacks(step)
        player?.runCatching { stop(); release() }
        player = null
        gain = 0f
        Music.underJingle(false)
    }

    private fun fadeTo(to: Float, ms: Long) {
        target = to
        fadeMs = ms
        main.removeCallbacks(step)
        main.post(step)
    }

    private val step = object : Runnable {
        override fun run() {
            val p = player ?: return
            val delta = TICK_MS.toFloat() / fadeMs
            gain = if (target > gain) minOf(target, gain + delta) else maxOf(target, gain - delta)
            if (gain <= 0f && target <= 0f) { stopNow(); return }
            val v = (gain * gain * Store.settings.look.soundVolume).coerceIn(0f, 1f)
            runCatching { p.setVolume(v, v) }
            if (gain != target) main.postAtTime(this, SystemClock.uptimeMillis() + TICK_MS)
        }
    }
}

/**
 * Starts and stops hover jingles as the highlight moves. Composed once in the app root ([SoundCues]); reads files
 * only on [Dispatchers.IO], after the rest delay.
 */
@Composable
fun GameJingleWatcher() {
    val game = (Selection.current as? Selected.GameItem)?.game
    val quiet = !rememberResumed() || Screensaver.active || LaunchFx.busy || Nav.overlayOpen || Layers.top != null || !GameJingles.allowed()
    LaunchedEffect(game?.key, quiet) {
        if (game == null) { GameJingles.fadeOut(); return@LaunchedEffect }
        // Still on the game whose jingle plays (a popup opened over it and closed again): let it carry on.
        if (GameJingles.playingKey == game.key && !quiet) return@LaunchedEffect
        GameJingles.fadeOut()
        if (quiet) return@LaunchedEffect
        delay(GameJingles.REST_MS)
        if (!Privacy.canShow(game)) return@LaunchedEffect
        val file = withContext(Dispatchers.IO) { GameJingles.fileFor(game) } ?: return@LaunchedEffect
        GameJingles.play(game.key, file)
    }
}
