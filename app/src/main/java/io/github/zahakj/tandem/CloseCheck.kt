package io.github.zahakj.tandem

/**
 * Which running games starting a game would close, worked out before it starts so Tandem can ask first (the
 * "still running" question in ui/CloseAsk.kt). Mirrors what [Launcher.start] does once the game really starts:
 *
 * - The same game, still open in its emulator: a resume. Nothing closes, and nothing else does either.
 * - The same emulator with a different game (or one opened outside Tandem): that emulator is force-stopped. One
 *   package can hold many games (RetroArch runs every core, GameNative and Winlator every PC game), so this is
 *   about the game, not the app.
 * - With "Close previous game" on: every other running emulator too.
 *
 * Pure, so the decision is unit-tested without Android (CloseCheckTest).
 */
object CloseCheck {
    /** An emulator running now; [lastPath] is the file of the game Tandem last started in it, null if none. */
    data class Slot(val pkg: String, val lastPath: String?)

    /**
     * The running emulators that starting the game at [path] in [pkg] would close: the same emulator first, then the
     * others in [running]'s order. [anyApp]: a shortcut that opens whichever app handles it, which never resumes or
     * closes its own app, though "Close previous game" still applies.
     */
    fun wouldClose(running: List<Slot>, pkg: String, path: String, anyApp: Boolean, closePrevious: Boolean): List<Slot> {
        val same = if (anyApp) null else running.firstOrNull { it.pkg == pkg }
        if (same != null && same.lastPath == path) return emptyList()
        val others = if (closePrevious) running.filter { it.pkg != pkg } else emptyList()
        return listOfNotNull(same) + others
    }

    /**
     * Of [closing], the ones worth asking about: those with a game Tandem started in them, which may have progress
     * to save. An emulator opened by hand with no game Tandem knows of (its menu, a settings screen) is just closed.
     */
    fun worthAsking(closing: List<Slot>): List<Slot> = closing.filter { it.lastPath != null }

    /**
     * The game [slot] has open, to name in the question: the one at its [Slot.lastPath] in [byPath] (the library by
     * file). Null when the emulator was opened outside Tandem (no path), so it is named by its app alone and a game
     * that isn't running (a hidden one, say) is never named.
     */
    fun <G> gameFor(slot: Slot, byPath: Map<String, G>): G? = slot.lastPath?.let(byPath::get)

    /**
     * One game in the question, as it may be shown: [title] null when it isn't a Tandem game (opened in the emulator
     * itself), [hidden] while private mode keeps it out of sight.
     */
    data class Entry(val title: String?, val appName: String, val hidden: Boolean)

    /** The name [e] may be shown by, or null: a hidden game is never named, nor is its emulator. */
    fun shownName(e: Entry): String? = if (e.hidden) null else e.title ?: e.appName

    /** How [e] is named in words ("A hidden game" when it may not be). */
    fun name(e: Entry): String = shownName(e) ?: Tx.s(R.string.play_close_hidden_game)

    /** Whom the question is about: one game (by [shownName]) or how many. Plain data, tested without Android. */
    sealed class Who {
        data class One(val name: String?) : Who()
        data class Many(val count: Int) : Who()
    }

    fun who(closing: List<Entry>): Who {
        require(closing.isNotEmpty())
        return if (closing.size == 1) Who.One(shownName(closing[0])) else Who.Many(closing.size)
    }

    /** The question's title and explanation, for [closing] (not empty) when starting [starting]. */
    fun headline(closing: List<Entry>, starting: String): Pair<String, String> = when (val w = who(closing)) {
        is Who.One -> Tx.s(R.string.play_close_one, w.name ?: Tx.s(R.string.play_close_hidden_game)) to Tx.s(R.string.play_close_one_text, starting)
        is Who.Many -> Tx.p(R.plurals.play_close_many, w.count) to Tx.s(R.string.play_close_many_text, starting)
    }

    /** The safe button's label: resumes [first], the one most in the way. */
    fun resumeLabel(first: Entry): String = shownName(first)?.let { Tx.s(R.string.play_close_resume, it) } ?: Tx.s(R.string.play_close_resume_it)
}
