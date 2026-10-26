package io.github.zahakj.tandem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Bringing the bottom-screen companion back after Tandem's process restarted mid-game (CompanionRestore.kt). */
class CompanionRestoreTest {

    /** Trimmed `dumpsys activity activities` from Android 13: the game on display 0, the stock launcher on 4. */
    private val dump = """
        ACTIVITY MANAGER ACTIVITIES (dumpsys activity activities)
        Display #0 (activities from top to bottom):
          * Task{8a1c2f #212 type=standard A=10187:org.example.emulator U=0 visible=true visibleRequested=true mode=fullscreen translucent=false sz=1}
            mLastPausedActivity: ActivityRecord{3e0b1d u0 io.github.zahakj.tandem/.MainActivity t5}
            isSleeping=false
            topResumedActivity=ActivityRecord{51f9a7 u0 org.example.emulator/.MainActivity t212}
            * Hist  #0: ActivityRecord{51f9a7 u0 org.example.emulator/.MainActivity t212}
          * Task{c41e09 #5 type=home A=10201:io.github.zahakj.tandem U=0 visible=false visibleRequested=false mode=fullscreen translucent=true sz=1}
            * Hist  #0: ActivityRecord{3e0b1d u0 io.github.zahakj.tandem/.MainActivity t5}

          Resumed activities in task display areas (from top to bottom):
            ResumedActivity: ActivityRecord{51f9a7 u0 org.example.emulator/.MainActivity t212}

        Display #4 (activities from top to bottom):
          * Task{77aa10 #3 type=home A=10094:com.android.launcher3 U=0 visible=true visibleRequested=true mode=fullscreen translucent=false sz=1}
            * Hist  #0: ActivityRecord{9b2c44 u0 com.android.launcher3/.secondarydisplay.SecondaryDisplayLauncher t3}

          Resumed activities in task display areas (from top to bottom):
            ResumedActivity: ActivityRecord{9b2c44 u0 com.android.launcher3/.secondarydisplay.SecondaryDisplayLauncher t3}

         topDisplayFocusedRootTask=Task{77aa10 #3 type=home A=10094:com.android.launcher3 U=0 visible=true visibleRequested=true mode=fullscreen translucent=false sz=1}
         mLastFocusedRootTask=Task{8a1c2f #212 type=standard A=10187:org.example.emulator U=0}
         topResumedActivity=ActivityRecord{9b2c44 u0 com.android.launcher3/.secondarydisplay.SecondaryDisplayLauncher t3}
    """.trimIndent()

    @Test fun findsTheAppInFrontOfEachDisplay() {
        // Not the global topResumedActivity (the stock launcher took that when the companion died).
        assertEquals("org.example.emulator", CompanionRestore.frontPackage(dump))
        assertEquals("com.android.launcher3", CompanionRestore.frontPackage(dump, displayId = 4))
        assertNull(CompanionRestore.frontPackage(dump, displayId = 2))
    }

    @Test fun pausedActivitiesDontCount() {
        val gameGone = dump.lineSequence().filterNot { "org.example.emulator/" in it && "Resumed" in it }.joinToString("\n")
        // Only Tandem's paused record and the game's history remain on display 0: nothing is resumed there.
        assertNull(CompanionRestore.frontPackage(gameGone))
    }

    @Test fun olderLayoutsFallBackToTheTopResumedActivity() {
        val old = """
            mFocusedActivity: ActivityRecord{1 u0 org.yuzu.yuzu_emu.ea/org.yuzu.yuzu_emu.activities.EmulationActivity t9}
            topResumedActivity=ActivityRecord{1 u0 org.yuzu.yuzu_emu.ea/org.yuzu.yuzu_emu.activities.EmulationActivity t9}
        """.trimIndent()
        assertEquals("org.yuzu.yuzu_emu.ea", CompanionRestore.frontPackage(old))
        assertNull(CompanionRestore.frontPackage(""))
    }

    private val now = 1_700_000_000_000L
    private val info = CompanionInfo(
        key = "switch/Hades II.nsp", title = "Hades II", system = "Switch", systemId = "switch",
        hero = null, box = null, logo = null, mode = GameCompanion.ART, pkg = "org.example.emulator", start = now - 120_000,
    )

    private fun should(
        info: CompanionInfo? = this.info,
        front: String? = "org.example.emulator",
        session: String? = this.info.key,
        hidden: Boolean = false,
        singleScreen: Boolean = false,
        second: Boolean = true,
        at: Long = now,
    ) = CompanionRestore.shouldRestore(info, front, session, hidden, singleScreen, second, at)

    @Test fun restoresWhenTheGameIsStillInFront() {
        assertTrue(should())
        assertTrue(should(info = info.copy(mode = GameCompanion.TANDEM)))
    }

    @Test fun neverWhenTheGameIsntInFront() {
        assertFalse(should(front = "io.github.zahakj.tandem"))
        assertFalse(should(front = "com.android.launcher3"))
        // Shizuku couldn't tell.
        assertFalse(should(front = null))
        // Started through "any app": no package to check.
        assertFalse(should(info = info.copy(pkg = null), front = null))
    }

    @Test fun neverForHiddenGamesOrAnotherGamesSession() {
        assertFalse(should(hidden = true))
        // Another game's session is open. A closed one (a visit to Tandem's home) is fine while the game is in front.
        assertTrue(should(session = null))
        assertFalse(should(session = "snes/Other.sfc"))
        assertFalse(should(info = null))
    }

    @Test fun neverWithoutABottomScreenOrWhenStale() {
        assertFalse(should(singleScreen = true))
        assertFalse(should(second = false))
        assertFalse(should(info = info.copy(mode = GameCompanion.OFF)))
        assertFalse(should(at = info.start + CompanionRestore.MAX_AGE_MS + 1))
        assertFalse(should(at = info.start - 1))
    }
}
