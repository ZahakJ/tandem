package io.github.zahakj.tandem.ui.whatsnew

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.zahakj.tandem.BuildConfig
import io.github.zahakj.tandem.Libraries
import io.github.zahakj.tandem.R
import io.github.zahakj.tandem.Tx
import io.github.zahakj.tandem.WhatsNewRules
import io.github.zahakj.tandem.data.Store
import io.github.zahakj.tandem.ui.FocusMemory
import io.github.zahakj.tandem.ui.Glyph
import io.github.zahakj.tandem.ui.LayoutSwitch
import io.github.zahakj.tandem.ui.Nav
import io.github.zahakj.tandem.ui.Palette
import io.github.zahakj.tandem.ui.PipMind
import io.github.zahakj.tandem.ui.Route
import io.github.zahakj.tandem.ui.SettingRow
import io.github.zahakj.tandem.ui.Showcase
import io.github.zahakj.tandem.ui.Sounds
import io.github.zahakj.tandem.ui.Welcome
import io.github.zahakj.tandem.ui.toggle
import kotlinx.coroutines.delay

/*
 * What's new: after an update to a new minor version, the first time Tandem's home shows, a deck of slides walks
 * through what came with it (DeckScreen.kt on the top screen, its companion on the bottom one). It opens once
 * (Settings.whatsNewSeen), never on a fresh install, and not at all with Show what's new off (WhatsNewRules).
 * Settings › Play & system › What's new in 0.9 opens it again whenever you like.
 */
object WhatsNew {
    const val ROUTE = "whatsnew"

    var open by mutableStateOf(false)
        private set

    /** The deck on show. */
    var deck by mutableStateOf<Deck?>(null)
        private set

    /** The slide on show, and the way the last step went (+1 forward, -1 back) for the slide-in. */
    var index by mutableIntStateOf(0)
        private set
    var dir by mutableIntStateOf(1)
        private set

    /** Asked once per start, the first time the home is ready. */
    private var decided = false

    /** The deck this build shows: its minor version's (a patch shows its minor's). */
    val current: Deck? get() = Decks.of(WhatsNewRules.minorOf(BuildConfig.VERSION_NAME))

    /** Opens [d] on its first slide (Settings › What's new, or the start of a new version). */
    fun show(d: Deck? = current) {
        if (d == null || d.slides.isEmpty()) return
        FocusMemory.forget(ROUTE)
        deck = d
        index = 0
        dir = 1
        open = true
    }

    /** Closes the deck, halfway or at the end: either way it was offered, so it counts as seen. */
    fun close() {
        val d = deck
        open = false
        if (d != null) markSeen(d.version)
        Nav.focusTick++
    }

    /** One slide forward (+1) or back (-1); nothing past either end. */
    fun step(by: Int) {
        val d = deck ?: return
        val to = (index + by).coerceIn(0, d.slides.size - 1)
        if (to == index) return
        go(to)
    }

    /** Straight to slide [to] (a dot or a name on the other screen). */
    fun go(to: Int) {
        val d = deck ?: return
        val t = to.coerceIn(0, d.slides.size - 1)
        if (t == index) return
        dir = if (t > index) 1 else -1
        index = t
        Sounds.move()
        if (!Store.settings.reduceMotion) PipMind.cheer()
    }

    private fun markSeen(version: String) {
        val seen = Store.settings.whatsNewSeen
        if (seen != null && io.github.zahakj.tandem.Releases.compare(seen, version) >= 0) return
        Store.updateSettings { it.copy(whatsNewSeen = version) }
    }

    /** The start-of-version check (WhatsNewRules): opens the deck, marks it seen quietly, or does nothing. */
    fun decide() {
        if (decided) return
        decided = true
        val s = Store.settings
        when (val d = WhatsNewRules.decide(BuildConfig.VERSION_NAME, s.whatsNewSeen, s.welcomeDone, s.whatsNew, Decks.versions)) {
            is WhatsNewRules.Decision.Show -> show(Decks.of(d.version))
            is WhatsNewRules.Decision.MarkSeen -> markSeen(d.version)
            WhatsNewRules.Decision.Stay -> Unit
        }
    }
}

/**
 * Opens the deck by itself once the home is up: the library loaded, the welcome done, nothing else open and no layout
 * being tried. A moment's wait first, so it doesn't pop in over the home still drawing itself.
 */
@Composable
fun WhatsNewHost() {
    val ready = Libraries.all != null && Nav.route == Route.Home && Store.settings.welcomeDone && !Welcome.open &&
        !Nav.overlayOpen && !LayoutSwitch.open
    LaunchedEffect(ready) {
        if (!ready) return@LaunchedEffect
        delay(900)
        WhatsNew.decide()
    }
}

/**
 * Settings › Play & system › Tandem: What's new in 0.9 (opens the deck, its slides playing in the side panel) and Show
 * what's new (the switch). In every build, with the updater or without.
 */
@Composable
fun whatsNewRows(): List<SettingRow> {
    val deck = WhatsNew.current
    val on = Store.settings.whatsNew
    val rows = ArrayList<SettingRow>()
    if (deck != null) rows += SettingRow(
        Tx.s(R.string.whatsnew_row, shortVersion(deck.version)), Tx.p(R.plurals.whatsnew_row_slides, deck.slides.size, deck.slides.size),
        Palette.Butter, icon = Glyph.Sparkle,
        help = Tx.s(R.string.whatsnew_row_help),
        preview = { DeckShowcase(deck) },
        keywords = "whats new what's new changelog release notes features tour slides deck news version",
        action = Tx.s(R.string.whatsnew_row_action),
        en = "What's new",
    ) { WhatsNew.show(deck) }
    rows += toggle(
        Tx.s(R.string.whatsnew_switch), Glyph.Sparkle, Palette.Butter, Tx.s(R.string.whatsnew_switch_help), on,
        en = "Show what's new",
    ) { v -> Store.updateSettings { it.copy(whatsNew = v) } }.copy(keywords = "whats new what's new popup tour after update changelog news slides")
    return rows
}

/** "0.9.0" → "0.9": how a minor version is named to people. */
fun shortVersion(v: String): String = v.removeSuffix(".0").ifEmpty { v }

/** The side panel's preview of the deck: its slides' pictures in turn, each named. */
@Composable
private fun DeckShowcase(deck: Deck) {
    Showcase(deck.slides.map { Tx.s(it.short) }, -1, color = Palette.Butter) { i ->
        DesignBox { deck.slides[i].visual() }
    }
}
