package io.github.zahakj.tandem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Finding the app to move to the other screen in `dumpsys activity activities` (AppMove.kt). */
class AppMoveTest {

    /** Trimmed from Android 13: Discord in front on the top screen (focused), Tandem's bottom-screen home below. */
    private val dump = """
        ACTIVITY MANAGER ACTIVITIES (dumpsys activity activities)
        Display #0 (activities from top to bottom):
          * Task{8a1c2f #212 type=standard A=10187:com.discord U=0 visible=true visibleRequested=true mode=fullscreen translucent=false sz=1}
            topResumedActivity=ActivityRecord{51f9a7 u0 com.discord/.main.MainActivity t212}
            * Hist  #0: ActivityRecord{51f9a7 u0 com.discord/.main.MainActivity t212}
          * Task{c41e09 #5 type=home A=10201:io.github.zahakj.tandem U=0 visible=false visibleRequested=false mode=fullscreen translucent=true sz=1}
            * Hist  #0: ActivityRecord{3e0b1d u0 io.github.zahakj.tandem/.MainActivity t5}
          * Task{99aa01 #180 type=standard A=10150:org.mozilla.firefox U=0 visible=false visibleRequested=false mode=fullscreen translucent=false sz=1}
            * Hist  #0: ActivityRecord{77bb02 u0 org.mozilla.firefox/.App t180}

          Resumed activities in task display areas (from top to bottom):
            ResumedActivity: ActivityRecord{51f9a7 u0 com.discord/.main.MainActivity t212}

        Display #4 (activities from top to bottom):
          * Task{77aa10 #3 type=home A=10201:io.github.zahakj.tandem U=0 visible=true visibleRequested=true mode=fullscreen translucent=false sz=1}
            * Hist  #0: ActivityRecord{9b2c44 u0 io.github.zahakj.tandem/.SecondaryHomeActivity t3}

         topDisplayFocusedRootTask=Task{8a1c2f #212 type=standard A=10187:com.discord U=0}
         topResumedActivity=ActivityRecord{51f9a7 u0 com.discord/.main.MainActivity t212}
    """.trimIndent()

    private val own = "io.github.zahakj.tandem"

    @Test fun listsRootTasksPerDisplayTopFirst() {
        val tasks = AppMove.tasks(dump)
        assertEquals(listOf(212, 5, 180, 3), tasks.map { it.id })
        assertEquals(listOf(0, 0, 0, 4), tasks.map { it.display })
        assertEquals(listOf(true, false, false, true), tasks.map { it.top })
        assertEquals("com.discord", tasks.first().pkg)
        assertEquals("home", tasks[1].type)
    }

    @Test fun movesTheFocusedApp() {
        val t = AppMove.pick(dump) { it == own }
        assertEquals(212, t?.id)
        assertEquals(0, t?.display)
    }

    @Test fun leavesOutWhatItMustNotMove() {
        // A game (or anything the caller skips) in front stays; an app behind Tandem is never picked.
        assertNull(AppMove.pick(dump) { it == own || it == "com.discord" })
    }

    @Test fun focusOnTheBottomHomeMovesTheAppOnTop() {
        // The last line (the global topResumedActivity) now names the bottom-screen home.
        val bottomFocused = dump.lines().dropLast(1).joinToString("\n") +
            "\n topResumedActivity=ActivityRecord{9b2c44 u0 io.github.zahakj.tandem/.SecondaryHomeActivity t3}"
        assertEquals(3, AppMove.focused(bottomFocused)?.id)
        assertEquals(212, AppMove.pick(bottomFocused) { it == own }?.id)
    }

    @Test fun nestedTasksBelongToTheirRoot() {
        val nested = """
            Display #0 (activities from top to bottom):
              * Task{1 #40 type=standard A=10001:com.example U=0 visible=true}
                * Task{2 #41 type=standard A=10001:com.example U=0 visible=true}
                  * Hist  #0: ActivityRecord{aa u0 com.example/.Main t41}
             topResumedActivity=ActivityRecord{aa u0 com.example/.Main t41}
        """.trimIndent()
        assertEquals(listOf(40), AppMove.tasks(nested).map { it.id })
        assertEquals(40, AppMove.focused(nested)?.id)
        assertEquals(40, AppMove.taskOf(nested, "com.example")?.id)
    }

    @Test fun movingUpSwapsWithTheAppOnTop() {
        // Firefox in front on the top screen, Discord on the bottom one and focused: Firefox gives way.
        val swap = """
            Display #0 (activities from top to bottom):
              * Task{1 #180 type=standard A=10150:org.mozilla.firefox U=0 visible=true}
                * Hist  #0: ActivityRecord{77bb02 u0 org.mozilla.firefox/.App t180}
              * Task{2 #5 type=home A=10201:io.github.zahakj.tandem U=0 visible=false}
                * Hist  #0: ActivityRecord{3e0b1d u0 io.github.zahakj.tandem/.MainActivity t5}
            Display #4 (activities from top to bottom):
              * Task{3 #212 type=standard A=10187:com.discord U=0 visible=true}
                * Hist  #0: ActivityRecord{51f9a7 u0 com.discord/.main.MainActivity t212}
             topResumedActivity=ActivityRecord{51f9a7 u0 com.discord/.main.MainActivity t212}
        """.trimIndent()
        val up = AppMove.pick(swap) { it == own }!!
        assertEquals(212, up.id)
        assertEquals(180, AppMove.swapPartner(swap, up) { it == own }?.id)
        // A game on top (left alone unless allowed) stays.
        assertNull(AppMove.swapPartner(swap, up) { it == own || it == "org.mozilla.firefox" })
    }

    @Test fun tandemOnTopIsNoSwapPartner() {
        // Tandem's library is on top: it can't be moved; the bottom-screen home shows it instead.
        val tandemTop = """
            Display #0 (activities from top to bottom):
              * Task{2 #5 type=home A=10201:io.github.zahakj.tandem U=0 visible=true}
                * Hist  #0: ActivityRecord{3e0b1d u0 io.github.zahakj.tandem/.MainActivity t5}
            Display #4 (activities from top to bottom):
              * Task{3 #212 type=standard A=10187:com.discord U=0 visible=true}
                * Hist  #0: ActivityRecord{51f9a7 u0 com.discord/.main.MainActivity t212}
             topResumedActivity=ActivityRecord{51f9a7 u0 com.discord/.main.MainActivity t212}
        """.trimIndent()
        val up = AppMove.pick(tandemTop) { it == own }!!
        assertNull(AppMove.swapPartner(tandemTop, up) { it == own })
    }

    @Test fun findsAnOpenAppAnywhere() {
        assertEquals(180, AppMove.taskOf(dump, "org.mozilla.firefox")?.id)
        assertNull(AppMove.taskOf(dump, "com.example.closed"))
    }
}
