package io.github.zahakj.tandem.data

import androidx.annotation.StringRes
import io.github.zahakj.tandem.R
import io.github.zahakj.tandem.Tx

/**
 * Where a game stands with you: in the backlog, being played, finished, completed, set aside, or one that never ends.
 * Kept in [GameMeta.status] with when it was set; null is no status at all. A game in the backlog (or with none)
 * becomes Playing by itself after a real session ([afterSession]); everything else is the user's to say.
 */
enum class GameStatus(val id: String, @StringRes private val labelRes: Int, @StringRes private val blurbRes: Int) {
    Backlog("backlog", R.string.data_status_backlog, R.string.data_status_backlog_blurb),
    Playing("playing", R.string.data_status_playing, R.string.data_status_playing_blurb),
    Finished("finished", R.string.data_status_finished, R.string.data_status_finished_blurb),
    Completed("completed", R.string.data_status_completed, R.string.data_status_completed_blurb),
    Shelved("shelved", R.string.data_status_shelved, R.string.data_status_shelved_blurb),
    Endless("endless", R.string.data_status_endless, R.string.data_status_endless_blurb),
    ;

    val label: String get() = Tx.s(labelRes)
    val blurb: String get() = Tx.s(blurbRes)

    /** Finished or better: the game counts as beaten. */
    val beaten: Boolean get() = this == Finished || this == Completed

    companion object {
        fun of(id: String?): GameStatus? = id?.let { s -> entries.firstOrNull { it.id == s } }

        /** A session this long turns a game in the backlog (or with no status) into one being played. */
        const val PLAYING_AFTER_SECONDS = 10 * 60L

        /**
         * What a game's details become after a session of [seconds] ending at [now]: Playing when it had no status
         * or sat in the backlog and the session was long enough; otherwise unchanged.
         */
        fun afterSession(m: GameMeta, seconds: Long, now: Long): GameMeta {
            val s = of(m.status)
            if (seconds < PLAYING_AFTER_SECONDS || (s != null && s != Backlog)) return m
            return m.copy(status = Playing.id, statusAt = now)
        }

        /**
         * [m] with status [to] (null: none) set at [now]. The first time a game is beaten is kept in
         * [GameMeta.finishedAt], so going back to Playing for a second run doesn't lose it.
         */
        fun set(m: GameMeta, to: GameStatus?, now: Long): GameMeta {
            if (of(m.status) == to) return m
            val firstBeat = if (to?.beaten == true && m.finishedAt == 0L) now else m.finishedAt
            return m.copy(status = to?.id, statusAt = if (to == null) 0 else now, finishedAt = firstBeat)
        }
    }
}

/** The game's status, or null. */
val GameMeta.playStatus: GameStatus? get() = GameStatus.of(status)
