package io.github.zahakj.tandem

import org.junit.Assert.assertEquals
import org.junit.Test

/** Back to Tandem when the game it started ends, not when the user switched apps (GameExit.kt). */
class GameExitTest {
    private val own = "io.github.zahakj.tandem"
    private val cemu = "info.cemu.cemu"

    private fun tasks(vararg pkgs: String) = pkgs.associateWith { listOf(RunningTasks.Activity("$it.Main", finishing = false)) }

    private fun step(seen: Boolean, tasks: Map<String, List<RunningTasks.Activity>>?, front: String?, frontIsGame: Boolean = false) =
        GameExit.step(seen, cemu, tasks, front, own, frontIsGame)

    @Test fun theGameCrashesOntoAnotherApp() {
        // Playing: the game is in front.
        val playing = step(seen = false, tasks(cemu, own, "com.discord"), front = cemu)
        assertEquals(GameExit.Step(seen = true, back = false), playing)
        // It crashed: its task is gone and Discord, underneath, came up.
        assertEquals(GameExit.Step(seen = false, back = true), step(playing.seen, tasks(own, "com.discord"), front = "com.discord"))
        // Android's own launcher, or nothing in front yet.
        assertEquals(true, step(true, tasks(own), front = "com.android.launcher3").back)
        assertEquals(true, step(true, tasks(own), front = null).back)
    }

    @Test fun aTaskThatIsOnlyFinishingCountsAsGone() {
        val closing = mapOf(cemu to listOf(RunningTasks.Activity("$cemu.EmulationActivity", finishing = true)))
        assertEquals(true, step(true, closing, front = "com.discord").back)
    }

    @Test fun tandemAlreadyBackNeedsNothing() {
        assertEquals(GameExit.Step(seen = false, back = false), step(true, tasks(own), front = own))
    }

    @Test fun theUserSwitchedAppsWhileTheGameRuns() {
        // Over to a browser on purpose, the game still running: no longer "in front".
        val away = step(seen = true, tasks(cemu, "org.mozilla.ironfox"), front = "org.mozilla.ironfox")
        assertEquals(GameExit.Step(seen = false, back = false), away)
        // Then the game is closed (from Recents, say): the user stays in the browser.
        assertEquals(GameExit.Step(seen = false, back = false), step(away.seen, tasks("org.mozilla.ironfox"), front = "org.mozilla.ironfox"))
    }

    @Test fun homePressedDuringTheGame() {
        // Tandem came forward with the game still running: never seen in front again, so its later end does nothing.
        val home = step(seen = true, tasks(cemu, own), front = own)
        assertEquals(false, home.seen)
        assertEquals(false, step(home.seen, tasks(own), front = "com.discord").back)
    }

    @Test fun beforeTheGameShowsNothingHappens() {
        // The emulator is still starting: no task yet, never seen.
        assertEquals(GameExit.Step(seen = false, back = false), step(false, tasks(own, "com.discord"), front = "com.discord"))
    }

    @Test fun aMomentWithNothingInFrontKeepsWhatWasSeen() {
        assertEquals(GameExit.Step(seen = true, back = false), step(true, tasks(cemu), front = null))
    }

    @Test fun anotherGameTakingOverIsNotAnEnd() {
        assertEquals(false, step(true, tasks("org.example.other"), front = "org.example.other", frontIsGame = true).back)
    }

    @Test fun anUnreadableDumpChangesNothing() {
        assertEquals(GameExit.Step(seen = true, back = false), step(true, null, front = null))
        assertEquals(GameExit.Step(seen = false, back = false), step(false, null, front = "com.discord"))
    }
}
