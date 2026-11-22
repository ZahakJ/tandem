package io.github.zahakj.tandem.data

import android.content.Context
import androidx.compose.runtime.mutableStateMapOf
import io.github.zahakj.tandem.Lang
import io.github.zahakj.tandem.R
import io.github.zahakj.tandem.Tx
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
                TestResult(false, e.message ?: Tx.s(R.string.scrape_test_said_no))
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                TestResult(false, Tx.s(R.string.scrape_test_timeout))
            } catch (e: HttpError) {
                TestResult(false, Tx.s(R.string.scrape_test_http, e.code))
            } catch (e: Exception) {
                TestResult(false, Tx.s(R.string.scrape_test_unreachable))
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
                val info = LaunchBoxDb.info ?: return TestResult(false, Tx.s(R.string.scrape_test_lb_not_downloaded))
                val index = LaunchBoxDb.index() ?: return TestResult(false, Tx.s(R.string.scrape_test_lb_not_downloaded))
                val platform = "Nintendo Switch".takeIf { index.has(it) } ?: info.platforms.firstOrNull { index.has(it) }
                    ?: return TestResult(false, Tx.s(R.string.scrape_test_lb_no_systems))
                if (platform == "Nintendo Switch") {
                    val g = index.find(listOf(LbQuery("t", platform, listOf(GAME))))["t"]
                        ?: return TestResult(false, Tx.s(R.string.scrape_test_lb_not_found, GAME))
                    TestResult(true, Tx.p(R.plurals.scrape_test_lb_found, g.images.size, g.name, g.year?.toString() ?: "?", g.images.size))
                } else {
                    TestResult(true, Tx.p(R.plurals.scrape_test_lb_games, info.games, String.format(Lang.locale, "%,d", info.games)))
                }
            }
            ScrapeSource.ScreenScraper -> {
                if (!cfg.hasScreenScraper) return TestResult(false, Tx.s(R.string.scrape_test_ss_needs))
                val ss = ScreenScraper(cfg)
                ss.signIn()
                val g = ss.findByTitle(GAME, 225, cfg.languagePref, cfg.regionPref) ?: return TestResult(false, Tx.s(R.string.scrape_test_nothing_signed_in))
                TestResult(true, if (cfg.ssUser.isNotBlank()) Tx.s(R.string.scrape_test_found_as, g.name ?: GAME, cfg.ssUser) else Tx.s(R.string.scrape_test_found, g.name ?: GAME))
            }
            ScrapeSource.Igdb -> {
                if (!cfg.hasIgdb) return TestResult(false, Tx.s(R.string.scrape_test_igdb_needs))
                val g = Igdb(cfg.igdbClientId, cfg.igdbSecret).search(GAME, 130) ?: return TestResult(false, Tx.s(R.string.scrape_test_nothing_signed_in))
                val pictures = g.screenshots.size + g.artworks.size + (if (g.cover != null) 1 else 0)
                TestResult(
                    true,
                    g.year?.let { Tx.p(R.plurals.scrape_test_igdb_found_year, pictures, g.name, it.toString(), pictures) }
                        ?: Tx.p(R.plurals.scrape_test_igdb_found, pictures, g.name, pictures),
                )
            }
            ScrapeSource.SteamGridDb -> {
                if (!cfg.hasSteamGridDb) return TestResult(false, Tx.s(R.string.scrape_test_sgdb_needs))
                val (id, name) = SteamGridDb(cfg.sgdbKey).find(GAME) ?: return TestResult(false, Tx.s(R.string.scrape_test_sgdb_nothing))
                val heroes = SteamGridDb(cfg.sgdbKey).candidates(id, MediaKind.Hero).size
                TestResult(true, Tx.p(R.plurals.scrape_test_sgdb_found, heroes, name, heroes))
            }
            ScrapeSource.Libretro -> {
                val ok = Libretro(ctx.cacheDir).listed("Nintendo - Super Nintendo Entertainment System", MediaKind.Box, "Super Mario World (USA)")
                if (ok) TestResult(true, Tx.s(R.string.scrape_test_libretro_found)) else TestResult(false, Tx.s(R.string.scrape_test_libretro_failed))
            }
            ScrapeSource.Hltb -> {
                val g = Hltb().search(GAME) ?: return TestResult(false, Tx.s(R.string.scrape_test_nothing))
                TestResult(true, Tx.s(R.string.scrape_test_hltb_found, g.name, TimeToBeat.hours(g.time.main), TimeToBeat.hours(g.time.complete)))
            }
        }
    }
}
