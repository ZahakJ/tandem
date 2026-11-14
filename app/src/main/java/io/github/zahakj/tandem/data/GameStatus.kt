package io.github.zahakj.tandem.data

/**
 * Where a game stands with you: in the backlog, being played, finished, completed, set aside, or one that never ends.
 * Kept in [GameMeta.status] with when it was set; null is no status at all. A game in the backlog (or with none)
 * becomes Playing by itself after a real session ([afterSession]); everything else is the user's to say.
 */
enum class GameStatus(val id: String, val label: String, val blurb: String) {
    Backlog("backlog", "Backlog", "To play some day"),
    Playing("playing", "Playing", "On the go now"),
    Finished("finished", "Finished", "Saw the credits"),
    Completed("completed", "Completed", "Everything done, 100%"),
    Shelved("shelved", "Shelved", "Set aside for now"),
    Endless("endless", "Endless", "No ending: played for fun"),
    ;

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
