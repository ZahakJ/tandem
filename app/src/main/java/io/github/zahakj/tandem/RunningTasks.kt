package io.github.zahakj.tandem

/**
 * Which apps have a game open, from `dumpsys activity activities`. A process alone says little: Android keeps an
 * app's process cached after its task is swiped away or its game is quit to the menu, and some emulators keep a
 * service running (GameNative, Eden). So a game counts as running only while its emulator still has a task with an
 * activity that isn't finishing, and, for the emulators whose games run in an activity of their own, while that
 * activity is there (not just the emulator's menu).
 *
 * Plain functions over the dump's text, so they are unit-tested without Android (RunningTasksTest).
 */
object RunningTasks {
    /** One activity in a task: its full class name, and whether it is on its way out. */
    data class Activity(val cls: String, val finishing: Boolean)

    /** "* Hist #0: ActivityRecord{…}" (every Android since 7) and "Run #0: ActivityRecord{…}" (Android 11 and older). */
    private val entry = Regex("""^\*?\s*(?:Hist|Run)\s+#\d+:\s*ActivityRecord\{\S+ u\d+ ([A-Za-z0-9_.]+)/([A-Za-z0-9_.$]+)""")

    /**
     * Every activity in a task, by package. Null when [dump] lists none at all: Tandem itself is always there when it
     * asks, so that means the dump didn't work, and callers can't tell what is running.
     */
    fun parse(dump: String?): Map<String, List<Activity>>? {
        dump ?: return null
        val out = HashMap<String, MutableList<Activity>>()
        var seen = false
        var last: Pair<String, Int>? = null
        for (raw in dump.lineSequence()) {
            val line = raw.trim()
            val m = entry.find(line)
            if (m != null) {
                seen = true
                val pkg = m.groupValues[1]
                val cls = m.groupValues[2].let { if (it.startsWith(".")) pkg + it else it }
                // ActivityRecord's own text ends "t212 f}" while it finishes.
                val finishing = " f}" in line
                val list = out.getOrPut(pkg) { ArrayList() }
                list += Activity(cls, finishing)
                last = pkg to list.lastIndex
                continue
            }
            // Detailed dumps (some builds, or -a) follow a record with its fields.
            val at = last ?: continue
            if (line.startsWith("* ") || line.isEmpty()) { last = null; continue }
            if ("finishing=true" in line || "state=FINISHING" in line || "state=DESTROYED" in line) {
                val list = out.getValue(at.first)
                list[at.second] = list[at.second].copy(finishing = true)
            }
        }
        return if (seen) out else null
    }

    /**
     * Emulators whose games run in an activity of their own, by the activities' package (not the app's: Eden is
     * dev.eden.eden_emulator, but its activities are org.yuzu.yuzu_emu's). Any other activity of theirs is a menu.
     */
    private val separateGameActivity = listOf(
        "org.yuzu.yuzu_emu.", // Eden, yuzu and their forks
        "org.citra.citra_emu.", // Azahar, Citra and forks
        "org.dolphinemu.dolphinemu.",
        "info.cemu.cemu.",
        "com.github.stenzek.duckstation.",
        "xyz.aethersx2.android.",
        "me.magnum.melonds.",
        "com.winlator.",
    )
    private val gameActivity = Regex("""Emulation|Emulator|XServerDisplay|RetroActivity""")

    /**
     * Whether [cls] is an activity a game plays in. For the emulators above, only their emulation activity is; for
     * the rest Tandem can't tell a menu from a game (RetroArch's menu and games share one activity, GameNative is
     * one activity throughout), so any activity counts.
     */
    fun isGameActivity(cls: String): Boolean {
        val simple = cls.substringAfterLast('.')
        if (separateGameActivity.none { cls.startsWith(it) }) return true
        return gameActivity.containsMatchIn(simple)
    }

    /** True when [pkg] has a task with an activity that isn't finishing. */
    fun hasTask(tasks: Map<String, List<Activity>>, pkg: String): Boolean =
        tasks[pkg].orEmpty().any { !it.finishing }

    /** True when [pkg] has a game open: a live activity a game plays in (see [isGameActivity]). */
    fun inGame(tasks: Map<String, List<Activity>>, pkg: String): Boolean =
        tasks[pkg].orEmpty().any { !it.finishing && isGameActivity(it.cls) }
}
