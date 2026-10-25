package io.github.zahakj.tandem.data

import android.content.Context
import androidx.compose.runtime.mutableStateMapOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withTimeout

/** The result of a source's Test button. */
data class TestResult(val ok: Boolean, val message: String, val at: Long = System.currentTimeMillis())

/**
 * The Test button on each source's setup card: one real lookup of a well-known game, run only when the user
 * presses it. Results are Compose state, kept until the next test of that source or until its keys change.
 */
object SourceTests {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val results = mutableStateMapOf<ScrapeSource, TestResult>()
    val running = mutableStateMapOf<ScrapeSource, Boolean>()

    fun forget(s: ScrapeSource) { results.remove(s) }

    fun run(ctx: Context, s: ScrapeSource) {
        if (running[s] == true) return
        running[s] = true
        results.remove(s)
        val app = ctx.applicationContext
        scope.launch {
            val r = try {
                withTimeout(45_000) { runInterruptible { test(app, s) } }
            } catch (e: SourceDown) {
                TestResult(false, e.message ?: "It said no")
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                TestResult(false, "No answer in time. Check the Wi-Fi and try again")
            } catch (e: HttpError) {
                TestResult(false, "It answered with an error (${e.code})")
            } catch (e: Exception) {
                TestResult(false, "Couldn't reach it. Check the Wi-Fi and try again")
            }
            results[s] = r
            running.remove(s)
        }
    }

    private const val GAME = "Super Mario Odyssey"

    private fun test(ctx: Context, s: ScrapeSource): TestResult {
        val cfg = ScrapeConfig.settings
        return when (s) {
            ScrapeSource.LaunchBox -> {
                val info = LaunchBoxDb.info ?: return TestResult(false, "Not downloaded yet: press Download")
                val index = LaunchBoxDb.index() ?: return TestResult(false, "Not downloaded yet: press Download")
                val platform = "Nintendo Switch".takeIf { index.has(it) } ?: info.platforms.firstOrNull { index.has(it) }
                    ?: return TestResult(false, "The download has none of your systems; download it again")
                if (platform == "Nintendo Switch") {
                    val g = index.find(listOf(LbQuery("t", platform, listOf(GAME))))["t"]
                        ?: return TestResult(false, "Couldn't find $GAME in the download")
                    TestResult(true, "Found ${g.name} (${g.year ?: "?"}) with ${g.images.size} pictures, on this device")
                } else {
                    TestResult(true, "${"%,d".format(info.games)} games for your systems, on this device")
                }
            }
            ScrapeSource.ScreenScraper -> {
                if (!cfg.hasScreenScraper) return TestResult(false, "Needs a developer ID and developer password")
                val ss = ScreenScraper(cfg)
                ss.signIn()
                val g = ss.findByTitle(GAME, 225, cfg.languagePref, cfg.regionPref) ?: return TestResult(false, "Signed in, but the search found nothing")
                TestResult(true, "Found ${g.name ?: GAME}" + if (cfg.ssUser.isNotBlank()) ", signed in as ${cfg.ssUser}" else "")
            }
            ScrapeSource.Igdb -> {
                if (!cfg.hasIgdb) return TestResult(false, "Needs a Twitch client ID and client secret")
                val g = Igdb(cfg.igdbClientId, cfg.igdbSecret).search(GAME, 130) ?: return TestResult(false, "Signed in, but the search found nothing")
                TestResult(true, "Found ${g.name}${g.year?.let { " ($it)" } ?: ""} with ${g.screenshots.size + g.artworks.size + (if (g.cover != null) 1 else 0)} pictures")
            }
            ScrapeSource.SteamGridDb -> {
                if (!cfg.hasSteamGridDb) return TestResult(false, "Needs an API key")
                val (id, name) = SteamGridDb(cfg.sgdbKey).find(GAME) ?: return TestResult(false, "The key works, but the search found nothing")
                val heroes = SteamGridDb(cfg.sgdbKey).candidates(id, MediaKind.Hero).size
                TestResult(true, "Found $name with $heroes heroes")
            }
            ScrapeSource.Libretro -> {
                val ok = Libretro(ctx.cacheDir).listed("Nintendo - Super Nintendo Entertainment System", MediaKind.Box, "Super Mario World (USA)")
                if (ok) TestResult(true, "Found Super Mario World's box art") else TestResult(false, "Couldn't read the thumbnail list")
            }
            ScrapeSource.Hltb -> {
                val g = Hltb().search(GAME) ?: return TestResult(false, "The search found nothing")
                TestResult(true, "${g.name}: ${TimeToBeat.hours(g.time.main)} main story, ${TimeToBeat.hours(g.time.complete)} for 100%")
            }
        }
    }
}
