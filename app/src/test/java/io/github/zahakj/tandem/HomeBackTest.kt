package io.github.zahakj.tandem

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Home brings a game covered by another app back first, and goes to Tandem on the second press (HomeBack.kt). */
class HomeBackTest {
    private val long = HomeBack.SECOND_PRESS_MS * 10

    @Test fun anAppOverTheCompanionBringsTheGameBack() {
        // Discord opened on the bottom screen a while ago, over the companion.
        assertTrue(HomeBack.decide(companionUp = true, sessionOpen = true, coveredForMs = 5_000, gameAway = false, sinceLastMs = long))
    }

    @Test fun anAppOverTheGameOnTopBringsTheGameBack() {
        assertTrue(HomeBack.decide(companionUp = true, sessionOpen = true, coveredForMs = null, gameAway = true, sinceLastMs = long))
    }

    @Test fun theSecondPressGoesToTandem() {
        assertFalse(HomeBack.decide(true, true, coveredForMs = 5_000, gameAway = true, sinceLastMs = 1_200))
        // A while later, the next covered game comes back again.
        assertTrue(HomeBack.decide(true, true, coveredForMs = 5_000, gameAway = false, sinceLastMs = HomeBack.SECOND_PRESS_MS))
    }

    @Test fun homeFromThePlayingGameIsHome() {
        // The game in front, its companion showing.
        assertFalse(HomeBack.decide(true, true, coveredForMs = null, gameAway = false, sinceLastMs = long))
        // Covered only just now: by the Home press itself (the bottom-screen home coming up), not by an app.
        assertFalse(HomeBack.decide(true, true, coveredForMs = 150, gameAway = false, sinceLastMs = long))
    }

    @Test fun theBottomScreenFocusTellsWhatCovers() {
        val own = "io.github.zahakj.tandem"
        assertTrue(HomeBack.coveredBy("com.discord", own, "dev.eden.eden_emulator"))
        // The companion or the bottom-screen home, the game itself, nothing, or a system window: not covered.
        assertFalse(HomeBack.coveredBy(own, own, "dev.eden.eden_emulator"))
        assertFalse(HomeBack.coveredBy("dev.eden.eden_emulator", own, "dev.eden.eden_emulator"))
        assertFalse(HomeBack.coveredBy(null, own, "dev.eden.eden_emulator"))
        assertFalse(HomeBack.coveredBy("com.android.systemui", own, "dev.eden.eden_emulator"))
    }

    @Test fun noGameRunningIsHome() {
        assertFalse(HomeBack.decide(companionUp = false, sessionOpen = true, coveredForMs = 5_000, gameAway = true, sinceLastMs = long))
        // Tandem already came back since (the session is closed).
        assertFalse(HomeBack.decide(companionUp = true, sessionOpen = false, coveredForMs = 5_000, gameAway = true, sinceLastMs = long))
    }
}
