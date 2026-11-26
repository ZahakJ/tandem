package io.github.zahakj.tandem.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What a bug report takes out (Redact.kt), and which log lines are Tandem's. */
class RedactTest {
    private val r = Redactor(
        secrets = listOf("zahak_ra", "AbCdEfGh12345678", "hunter22", "76561198000000000", ""),
        hidden = listOf("Secret Garden", "Secret Garden (USA)", "x"),
        safeNames = listOf("psx", "n3ds"),
    )

    private fun clean(s: String) = r.line(s)

    @Test fun secretsTandemKeepsAreTakenOutAnywhere() {
        val out = clean("RA login zahak_ra with key AbCdEfGh12345678, steam 76561198000000000 pass HUNTER22")
        assertFalse(out.contains("zahak_ra")); assertFalse(out.contains("AbCdEfGh")); assertFalse(out.contains("765611"))
        assertFalse(out.lowercase().contains("hunter22"))
        assertTrue(out.contains(Redactor.SECRET))
    }

    @Test fun hiddenGamesAreNeverNamed() {
        val out = clean("Launching Secret Garden on DuckStation")
        assertEquals("Launching ${Redactor.HIDDEN} on DuckStation", out)
        // One-letter names are too short to hunt for.
        assertEquals("x marks the spot", clean("x marks the spot"))
    }

    @Test fun pathsKeepOnlyNamesTandemKnows() {
        assertEquals("open /storage/emulated/0/ROMs/psx/<name>.chd", clean("open /storage/emulated/0/ROMs/psx/Final Fantasy VII (USA) (Disc 1).chd"))
        assertEquals("/storage/1A2B-3C4D/ROMs/n3ds/<name>/<name>.3ds", clean("/storage/1A2B-3C4D/ROMs/n3ds/RPG/Pokemon X.3ds"))
        assertEquals("/data/user/0/io.github.zahakj.tandem/files/settings.json", clean("/data/user/0/io.github.zahakj.tandem/files/settings.json"))
        // A home folder's user name goes.
        assertEquals("cp /home/<name>/<name>.txt, done", clean("cp /home/zahak/notes.txt, done"))
        // Paths inside a URL, and fractions, aren't paths.
        assertEquals("3/4 done", clean("3/4 done"))
    }

    @Test fun contentLinksLoseEverythingAfterTheirApp() {
        assertEquals("uri content://com.android.externalstorage.documents/<redacted>",
            clean("uri content://com.android.externalstorage.documents/tree/primary%3AROMs/document/primary%3AROMs%2Fpsx%2FGame.chd"))
    }

    @Test fun emailsAndIpAddresses() {
        assertEquals("mail <email> from <ip> and <ip>", clean("mail someone@example.com from 192.168.1.20 and fe80::1c2b:3cff:fe4d:5e6f"))
        // Log times are not addresses.
        assertEquals("11-26 12:30:45.123 ok", clean("11-26 12:30:45.123 ok"))
    }

    @Test fun keyedValuesAndTokens() {
        assertEquals("GET https://api.example.com/v1?key=<redacted>&q=zelda", clean("GET https://api.example.com/v1?key=abc123&q=zelda"))
        assertEquals("ssid=<redacted> password: <redacted>", clean("ssid=Benzene password: opensesame"))
        assertEquals("Authorization: Bearer <redacted>", clean("Authorization: Bearer eyJhbGciOi.J9xyz"))
        assertEquals("devid <redacted>", clean("devid 9f86d081884c7d659a2feaa0c55ad015a3bf4f1b"))
        assertEquals("https://<redacted>@host.example/x", clean("https://user:pw@host.example/x"))
    }

    @Test fun ordinaryLinesStayAsTheyAre() {
        val line = "11-26 12:30:45.123  1234  1250 W Tandem  : Skipping a player of psx"
        assertEquals(line, clean(line))
        assertEquals("at io.github.zahakj.tandem.Library.scan(Library.kt:180)", clean("at io.github.zahakj.tandem.Library.scan(Library.kt:180)"))
    }

    @Test fun onlyTandemsLinesAreKept() {
        val lines = listOf(
            "11-26 12:30:45.123  1234  1250 W Tandem  : one",
            "11-26 12:30:45.124  1234  1250 I TandemPerf: two",
            "11-26 12:30:45.125  1234  1250 D OpenGLRenderer: three",
            "11-26 12:30:45.126  1234  1234 E AndroidRuntime: FATAL EXCEPTION: main",
            "--------- beginning of main",
        )
        assertEquals(listOf(lines[0], lines[1], lines[3]), ReportLog.ours(lines))
        assertEquals("Tandem", ReportLog.tagOf(lines[0]))
        assertNull(ReportLog.tagOf(lines[4]))
    }

    @Test fun aWholeTextLineByLine() {
        assertEquals("a <email>\nb <ip>", r.text("a me@x.org\nb 10.0.0.1"))
    }
}
