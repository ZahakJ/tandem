package io.github.zahakj.tandem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which emulators have a game open, from `dumpsys activity activities` (RunningTasks.kt). */
class RunningTasksTest {
    private val eden = "dev.eden.eden_emulator"
    private val retroarch = "com.retroarch"
    private val gamenative = "app.gamenative"
    private val cemu = "info.cemu.cemu"
    private val melonds = "me.magnum.melonds"

    /**
     * Trimmed from Android 13, Tandem in front. Eden has a game open (its emulation activity above its menu), RetroArch
     * sits in Recents, Cemu only shows its menu, melonDS is finishing its emulator, and GameNative's process is
     * cached with no task at all (it isn't listed).
     */
    private val dump = """
        ACTIVITY MANAGER ACTIVITIES (dumpsys activity activities)
        Display #0 (activities from top to bottom):
          * Task{c41e09 #5 type=home A=10201:io.github.zahakj.tandem U=0 visible=true visibleRequested=true mode=fullscreen translucent=false sz=1}
            topResumedActivity=ActivityRecord{3e0b1d u0 io.github.zahakj.tandem/.MainActivity t5}
            * Hist  #0: ActivityRecord{3e0b1d u0 io.github.zahakj.tandem/.MainActivity t5}
          * Task{8a1c2f #212 type=standard A=10187:dev.eden.eden_emulator U=0 visible=false visibleRequested=false mode=fullscreen translucent=false sz=2}
            mLastPausedActivity: ActivityRecord{51f9a7 u0 dev.eden.eden_emulator/org.yuzu.yuzu_emu.activities.EmulationActivity t212}
            * Hist  #1: ActivityRecord{51f9a7 u0 dev.eden.eden_emulator/org.yuzu.yuzu_emu.activities.EmulationActivity t212}
            * Hist  #0: ActivityRecord{77c0e1 u0 dev.eden.eden_emulator/org.yuzu.yuzu_emu.HomeActivity t212}
          * Task{1b2c3d #214 type=standard A=10190:com.retroarch U=0 visible=false visibleRequested=false mode=fullscreen translucent=false sz=1}
            * Hist  #0: ActivityRecord{aa11bb u0 com.retroarch/.browser.retroactivity.RetroActivityFuture t214}
          * Task{4d5e6f #215 type=standard A=10192:info.cemu.cemu U=0 visible=false visibleRequested=false mode=fullscreen translucent=false sz=1}
            * Hist  #0: ActivityRecord{cc22dd u0 info.cemu.cemu/.MainActivity t215}
          * Task{7a8b9c #216 type=standard A=10193:me.magnum.melonds U=0 visible=false visibleRequested=false mode=fullscreen translucent=false sz=2}
            * Hist  #1: ActivityRecord{ee33ff u0 me.magnum.melonds/.ui.emulator.EmulatorActivity t216 f}}
            * Hist  #0: ActivityRecord{ee4400 u0 me.magnum.melonds/.ui.romlist.RomListActivity t216}

          Resumed activities in task display areas (from top to bottom):
            ResumedActivity: ActivityRecord{3e0b1d u0 io.github.zahakj.tandem/.MainActivity t5}
    """.trimIndent()

    private val tasks = RunningTasks.parse(dump)!!

    @Test fun readsEveryActivityByPackage() {
        assertEquals(
            listOf("org.yuzu.yuzu_emu.activities.EmulationActivity", "org.yuzu.yuzu_emu.HomeActivity"),
            tasks.getValue(eden).map { it.cls },
        )
        // ".Name" is short for the package's own class.
        assertEquals("com.retroarch.browser.retroactivity.RetroActivityFuture", tasks.getValue(retroarch).single().cls)
        // mLastPausedActivity and ResumedActivity lines repeat records; they aren't counted again.
        assertEquals(1, tasks.getValue("io.github.zahakj.tandem").size)
    }

    @Test fun aCachedProcessWithNoTaskIsNoGame() {
        assertFalse(RunningTasks.hasTask(tasks, gamenative))
        assertFalse(RunningTasks.inGame(tasks, gamenative))
    }

    @Test fun aTaskInRecentsWithItsEmulationActivityIsAGame() {
        assertTrue(RunningTasks.inGame(tasks, eden))
        assertTrue(RunningTasks.inGame(tasks, retroarch))
    }

    @Test fun anEmulatorShowingOnlyItsMenuIsOpenButNotInAGame() {
        assertTrue(RunningTasks.hasTask(tasks, cemu))
        assertFalse(RunningTasks.inGame(tasks, cemu))
    }

    @Test fun aFinishingActivityDoesntCount() {
        assertTrue(tasks.getValue(melonds).first().finishing)
        // Only its ROM list is left: the emulator is open, no game is.
        assertTrue(RunningTasks.hasTask(tasks, melonds))
        assertFalse(RunningTasks.inGame(tasks, melonds))
        val onlyFinishing = RunningTasks.parse(
            "* Hist #0: ActivityRecord{1 u0 app.gamenative/.MainActivity t9 f}}\n* Hist #0: ActivityRecord{2 u0 io.github.zahakj.tandem/.MainActivity t5}",
        )!!
        assertFalse(RunningTasks.hasTask(onlyFinishing, gamenative))
    }

    @Test fun detailedDumpsMarkFinishingInTheirFields() {
        val detailed = """
            * Hist #0: ActivityRecord{1 u0 com.retroarch/.browser.retroactivity.RetroActivityFuture t9}
                packageName=com.retroarch processName=com.retroarch
                state=FINISHING stopped=false delayedResume=false finishing=true
            * Hist #0: ActivityRecord{2 u0 io.github.zahakj.tandem/.MainActivity t5}
                state=RESUMED stopped=false finishing=false
        """.trimIndent()
        val t = RunningTasks.parse(detailed)!!
        assertFalse(RunningTasks.inGame(t, retroarch))
        assertTrue(RunningTasks.hasTask(t, "io.github.zahakj.tandem"))
    }

    @Test fun olderAndroidListsTaskRecordsAndRunLines() {
        val old = """
            Stack #12: type=standard mode=fullscreen
              * TaskRecord{5a6b7c #212 A=dev.eden.eden_emulator U=0 StackId=12 sz=1}
                * Hist #0: ActivityRecord{51f9a7 u0 dev.eden.eden_emulator/org.yuzu.yuzu_emu.activities.EmulationActivity t212}
              Running activities (most recent first):
                TaskRecord{5a6b7c #212 A=dev.eden.eden_emulator U=0 StackId=12 sz=1}
                  Run #0: ActivityRecord{51f9a7 u0 dev.eden.eden_emulator/org.yuzu.yuzu_emu.activities.EmulationActivity t212}
        """.trimIndent()
        assertTrue(RunningTasks.inGame(RunningTasks.parse(old)!!, eden))
    }

    @Test fun anUnreadableDumpTellsNothing() {
        assertNull(RunningTasks.parse(null))
        assertNull(RunningTasks.parse(""))
        assertNull(RunningTasks.parse("Permission Denial: can't dump ActivityManager"))
    }

    @Test fun onlyKnownEmulatorsNeedTheirGameActivity() {
        // One activity for menu and game alike: any live activity counts.
        assertTrue(RunningTasks.isGameActivity("app.gamenative.MainActivity"))
        assertTrue(RunningTasks.isGameActivity("org.ppsspp.ppsspp.PpssppActivity"))
        assertTrue(RunningTasks.isGameActivity("com.winlator.XServerDisplayActivity"))
        assertFalse(RunningTasks.isGameActivity("com.winlator.MainActivity"))
        assertTrue(RunningTasks.isGameActivity("org.dolphinemu.dolphinemu.activities.EmulationActivity"))
        assertFalse(RunningTasks.isGameActivity("org.dolphinemu.dolphinemu.ui.main.MainActivity"))
    }
}
