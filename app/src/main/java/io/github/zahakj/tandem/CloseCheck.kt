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
     * One game in the question, as it may be shown: [title] null when it isn't a Tandem game (opened in the emulator
     * itself), [hidden] while private mode keeps it out of sight.
     */
    data class Entry(val title: String?, val appName: String, val hidden: Boolean)

    /** How [e] is named. A hidden game is never named, nor is its emulator. */
    fun name(e: Entry): String = when {
        e.hidden -> "A hidden game"
        else -> e.title ?: e.appName
    }

    /** The question's title and explanation, for [closing] (not empty) when starting [starting]. */
    fun headline(closing: List<Entry>, starting: String): Pair<String, String> {
        require(closing.isNotEmpty())
        return if (closing.size == 1) {
            "${name(closing[0])} is still running" to
                "Starting $starting will close it. Anything you haven't saved there will be lost."
        } else {
            "${closing.size} games are still running" to
                "Starting $starting will close them all. Anything you haven't saved in them will be lost."
        }
    }

    /** The safe button's label: resumes [first], the one most in the way. */
    fun resumeLabel(first: Entry): String = if (first.hidden) "Resume it" else "Resume ${first.title ?: first.appName}"
}
