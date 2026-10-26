package io.github.zahakj.tandem

import io.github.zahakj.tandem.data.HomeStore
import io.github.zahakj.tandem.data.Net
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Going offline in private mode (Offline.kt): the airplane mode plan on fake radios, and the network gate. */
class OfflineTest {
    /** Radios that do what they are told, unless told to fail; [log] lists every change. */
    private class Fake(var airplane: Boolean?, var wifi: Boolean?, var broken: Boolean = false, var keepsWifi: Boolean = false) : Radios {
        val log = ArrayList<String>()
        override fun airplane() = airplane
        override fun setAirplane(on: Boolean): Boolean {
            if (broken) return false
            log += "airplane $on"
            airplane = on
            // Android 13 turns Wi-Fi off with airplane mode, unless it was kept on in airplane mode before.
            if (on && !keepsWifi) wifi = false
            return true
        }
        override fun wifi() = wifi
        override fun setWifi(on: Boolean): Boolean {
            if (broken) return false
            log += "wifi $on"
            wifi = on
            return true
        }
    }

    @After fun privateModeOff() = HomeStore.updateSettings { it.copy(privateMode = false) }

    @Test fun airplaneModeGoesOnAndBackOff() {
        val r = Fake(airplane = false, wifi = true)
        val c = OfflinePlan.begin(OfflineChanges(), r)
        assertEquals(OfflineChanges(airplane = true), c)
        assertTrue(OfflinePlan.isOffline(r))
        val left = OfflinePlan.end(c, r)
        assertFalse(left.any)
        assertEquals(false, r.airplane)
        assertEquals(listOf("airplane true", "airplane false"), r.log)
    }

    @Test fun airplaneModeTheUserHadOnIsNeverTurnedOff() {
        val r = Fake(airplane = true, wifi = false)
        val c = OfflinePlan.begin(OfflineChanges(), r)
        assertFalse(c.any)
        OfflinePlan.end(c, r)
        assertEquals(true, r.airplane)
        assertTrue(r.log.isEmpty())
    }

    @Test fun wifiKeptOnInAirplaneModeGoesOffAndComesBack() {
        val r = Fake(airplane = false, wifi = true, keepsWifi = true)
        val c = OfflinePlan.begin(OfflineChanges(), r)
        assertEquals(OfflineChanges(airplane = true, wifi = true), c)
        assertTrue(OfflinePlan.isOffline(r))
        assertFalse(OfflinePlan.end(c, r).any)
        assertEquals(listOf("airplane true", "wifi false", "airplane false", "wifi true"), r.log)
        assertEquals(true, r.wifi)
    }

    @Test fun theUsersOwnAirplaneModeWithWifiOnLosesOnlyWifiForTheGame() {
        val r = Fake(airplane = true, wifi = true)
        val c = OfflinePlan.begin(OfflineChanges(), r)
        assertEquals(OfflineChanges(wifi = true), c)
        OfflinePlan.end(c, r)
        assertEquals(true, r.airplane)
        assertEquals(true, r.wifi)
    }

    @Test fun aSecondHiddenGameKeepsWhatTheFirstChanged() {
        // Airplane mode is on because of the first game: the second must not count it as the user's.
        val r = Fake(airplane = true, wifi = false)
        val had = OfflineChanges(airplane = true)
        assertEquals(had, OfflinePlan.begin(had, r))
        OfflinePlan.end(had, r)
        assertEquals(false, r.airplane)
    }

    @Test fun whatCouldntBePutBackIsKeptToTryAgain() {
        val r = Fake(airplane = true, wifi = false, broken = true)
        val had = OfflineChanges(airplane = true, wifi = true)
        assertEquals(had, OfflinePlan.end(had, r))
        r.broken = false
        assertFalse(OfflinePlan.end(had, r).any)
        assertEquals(false, r.airplane)
        assertEquals(true, r.wifi)
    }

    @Test fun radiosThatCantBeReadAreLeftAlone() {
        val r = Fake(airplane = null, wifi = null)
        assertFalse(OfflinePlan.begin(OfflineChanges(), r).any)
        assertTrue(r.log.isEmpty())
    }

    @Test fun theChangesSurviveARestartAsJson() {
        val json = kotlinx.serialization.json.Json { encodeDefaults = false }
        val c = OfflineChanges(airplane = true)
        assertEquals(c, json.decodeFromString(OfflineChanges.serializer(), json.encodeToString(OfflineChanges.serializer(), c)))
    }

    @Test fun privateModeStopsEveryRequestBeforeItLeaves() {
        HomeStore.updateSettings { it.copy(privateMode = true) }
        assertTrue(NetGate.closed)
        // An address that can't be reached: had it been tried, the error would be a different one.
        for (call in listOf<() -> Unit>({ Net.bytes("https://tandem.invalid/x") }, { Net.post("https://tandem.invalid/x", "{}") }, { NetGate.check() })) {
            try { call(); fail("went online") } catch (e: PrivateOffline) { }
        }
        HomeStore.updateSettings { it.copy(privateMode = false) }
        assertFalse(NetGate.closed)
        NetGate.check()
        try { Net.bytes("https://tandem.invalid/x"); fail("tandem.invalid answered") } catch (e: PrivateOffline) { fail("blocked with private mode off") } catch (e: java.io.IOException) { }
    }

    @Test fun privateModeStopsAScrapeFromStarting() {
        HomeStore.updateSettings { it.copy(privateMode = true) }
        assertFalse(io.github.zahakj.tandem.data.Scraper.start(io.github.zahakj.tandem.data.ScrapeScope.All, io.github.zahakj.tandem.data.Scraper.optionsFromSettings()))
    }
}
