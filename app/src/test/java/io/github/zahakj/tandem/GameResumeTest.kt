package io.github.zahakj.tandem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which task a running game is brought back by (GameResume.kt). */
class GameResumeTest {
    private val eden = "dev.eden.eden_emulator"

    /**
     * Trimmed from the Thor (Android 13), Tandem in front: The World Ends with You runs in Eden's emulation activity,
     * in a task of its own (#1544) rooted at that activity, and an older Eden menu task (#1530) sits below. This
     * build closes the activity's name before the task ("…Activity} t1544}").
     */
    private val dump = """
        ACTIVITY MANAGER ACTIVITIES (dumpsys activity activities)
        Display #4 (activities from top to bottom):
          * Task{77aa10 #1556 type=home A=10147:io.github.zahakj.tandem U=0 visible=true visibleRequested=true mode=fullscreen translucent=false sz=1}
            * Hist  #0: ActivityRecord{afa8c87 u0 io.github.zahakj.tandem/.SecondaryHomeActivity} t1556}
        Display #0 (activities from top to bottom):
          * Task{c41e09 #1549 type=home A=10147:io.github.zahakj.tandem U=0 visible=true visibleRequested=true mode=fullscreen translucent=false sz=1}
            topResumedActivity=ActivityRecord{3e0b1d u0 io.github.zahakj.tandem/.MainActivity} t1549}
            * Hist  #0: ActivityRecord{3e0b1d u0 io.github.zahakj.tandem/.MainActivity} t1549}
          * Task{d80e092 #1544 type=standard A=10121:dev.eden.eden_emulator U=0 visible=false visibleRequested=false mode=fullscreen translucent=false sz=1}
            * Hist  #0: ActivityRecord{f842e9e u0 dev.eden.eden_emulator/org.yuzu.yuzu_emu.activities.EmulationActivity} t1544}
          * Task{1b2c3d #1530 type=standard A=10121:dev.eden.eden_emulator U=0 visible=false visibleRequested=false mode=fullscreen translucent=false sz=1}
            * Hist  #0: ActivityRecord{aa11bb u0 dev.eden.eden_emulator/org.yuzu.yuzu_emu.ui.main.MainActivity} t1530}
    """.trimIndent()

    @Test fun bringsBackTheTaskTheGameRunsIn() {
        val t = GameResume.target(RunningTasks.parse(dump), eden)
        // Not the menu's task, which the launcher intent would have reused or replaced.
        assertEquals(GameResume.Target(task = 1544, display = 0), t)
        assertFalse(GameResume.onBottom(t!!))
    }

    @Test fun readsTheOlderRecordFormatToo() {
        val older = "Display #0 (activities from top to bottom):\n" +
            "* Hist  #1: ActivityRecord{51f9a7 u0 dev.eden.eden_emulator/org.yuzu.yuzu_emu.activities.EmulationActivity t212}\n" +
            "* Hist  #0: ActivityRecord{77c0e1 u0 dev.eden.eden_emulator/org.yuzu.yuzu_emu.HomeActivity t212}"
        assertEquals(GameResume.Target(212, 0), GameResume.target(RunningTasks.parse(older), eden))
    }

    @Test fun aGameOnTheBottomScreenStaysThere() {
        val down = dump.replace("Display #4 (activities from top to bottom):",
            "Display #4 (activities from top to bottom):\n  * Hist  #0: ActivityRecord{1 u0 dev.eden.eden_emulator/org.yuzu.yuzu_emu.activities.EmulationActivity} t1600}")
        val t = GameResume.target(RunningTasks.parse(down), eden)
        // Listed first (on top), on the second display.
        assertEquals(GameResume.Target(1600, 4), t)
        assertTrue(GameResume.onBottom(t!!))
    }

    @Test fun noGameTaskMeansTheLauncherIntent() {
        // Only the menu is left: nothing to bring back by task.
        val menuOnly = dump.lines().filterNot { "EmulationActivity" in it }.joinToString("\n")
        assertNull(GameResume.target(RunningTasks.parse(menuOnly), eden))
        // Finishing, or no dump at all (no Shizuku).
        assertNull(GameResume.target(RunningTasks.parse(dump.replace("EmulationActivity} t1544}", "EmulationActivity} t1544 f}}")), eden))
        assertNull(GameResume.target(null, eden))
    }

    @Test fun aRecordWithoutItsTaskIsNotUsed() {
        val noTask = "* Hist #0: ActivityRecord{1 u0 com.retroarch/.browser.retroactivity.RetroActivityFuture}"
        assertNull(GameResume.target(RunningTasks.parse(noTask), "com.retroarch"))
    }
}
