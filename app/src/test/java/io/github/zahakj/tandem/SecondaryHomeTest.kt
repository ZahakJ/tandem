package io.github.zahakj.tandem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Tandem's bottom-screen Home (SecondaryHome.kt): what it shows, and which stock launcher it opens on purpose. */
class SecondaryHomeTest {
    private val own = "io.github.zahakj.tandem"

    @Test fun anAppInFrontIsShownByName() {
        assertEquals(SecondaryHome.Front.App("com.droiddeck.launcher"), SecondaryHome.classify("com.droiddeck.launcher", own, game = false))
    }

    @Test fun gamesNeverShowTheirName() {
        assertEquals(SecondaryHome.Front.Game("org.ppsspp.ppsspp"), SecondaryHome.classify("org.ppsspp.ppsspp", own, game = true))
    }

    @Test fun tandemItselfOrNothingIsTheGreeting() {
        assertNull(SecondaryHome.classify(own, own, game = false))
        assertNull(SecondaryHome.classify(null, own, game = false))
        assertNull(SecondaryHome.classify("com.android.systemui", own, game = false))
    }

    @Test fun picksLauncher3OverOtherHomes() {
        val homes = listOf(
            "com.android.settings" to "com.android.settings.FallbackHome",
            own to "$own.MainActivity",
            "com.other.home" to "com.other.home.Main",
            "com.android.launcher3" to "com.android.launcher3.uioverrides.QuickstepLauncher",
        )
        val c = SecondaryHome.pickStock(homes, own)
        assertEquals("com.android.launcher3" to "com.android.launcher3.uioverrides.QuickstepLauncher", c)
    }

    @Test fun neverPicksTandemOrFallbackHome() {
        assertNull(SecondaryHome.pickStock(listOf(own to "$own.SecondaryHomeActivity", "com.android.settings" to "x.FallbackHome", "android" to "x.ResolverActivity"), own))
        assertEquals("com.other.home", SecondaryHome.pickStock(listOf(own to "a", "com.other.home" to "b"), own)?.first)
    }
}
