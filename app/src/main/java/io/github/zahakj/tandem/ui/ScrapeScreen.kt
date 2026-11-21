package io.github.zahakj.tandem.ui

import io.github.zahakj.tandem.Privacy

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import coil.request.ImageRequest
import kotlinx.coroutines.delay
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import io.github.zahakj.tandem.data.LaunchBoxDb
import io.github.zahakj.tandem.data.MetaType
import io.github.zahakj.tandem.data.ScrapeOptions
import io.github.zahakj.tandem.data.ScrapeSource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import coil.compose.AsyncImage
import io.github.zahakj.tandem.Game
import io.github.zahakj.tandem.plural
import io.github.zahakj.tandem.Libraries
import io.github.zahakj.tandem.data.Found
import io.github.zahakj.tandem.data.LANGUAGES
import io.github.zahakj.tandem.data.Media
import io.github.zahakj.tandem.data.MediaKind
import io.github.zahakj.tandem.data.Region
import io.github.zahakj.tandem.data.RunState
import io.github.zahakj.tandem.data.ScrapeConfig
import io.github.zahakj.tandem.data.ScrapeMode
import io.github.zahakj.tandem.data.ScrapeRun
import io.github.zahakj.tandem.data.ScrapeScope
import io.github.zahakj.tandem.data.Scraper
import io.github.zahakj.tandem.data.Store
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** What the scrape screen opens with; set by whoever sends the user there. */
object ScrapeUi {
    var scope by mutableStateOf<ScrapeScope>(ScrapeScope.All)

    /** [now]: right after another screen change (Settings search going through Library & art), past Nav.go's guard. */
    fun open(scope: ScrapeScope = ScrapeScope.All, now: Boolean = false) {
        if (!Scraper.running) Scraper.dismiss()
        this.scope = scope
        if (Nav.route != Route.Scrape) { if (now) Nav.goNow(Route.Scrape) else Nav.go(Route.Scrape) }
    }

    /** "Scrape this game" from the game menu: runs straight away, with toasts instead of the scrape screen. */
    fun scrapeOne(game: Game) {
        when {
            Privacy.isHidden(game) -> Toasts.show("Hidden games are never sent anywhere")
            io.github.zahakj.tandem.NetGate.closed -> Toasts.show(io.github.zahakj.tandem.data.OFFLINE_NOTE)
            Scraper.running -> Toasts.show("Pip is already busy scraping. Try again when that's done")
            else -> {
                val o = Scraper.optionsFromSettings()
                // The user asked for this game specifically, so refresh what's there (locked fields still stay).
                val opts = o.copy(mode = ScrapeMode.Refresh, kinds = o.kinds.ifEmpty { setOf(MediaKind.Box) }, replaceLocked = false)
                start(ScrapeScope.One(game.key), opts) { Toasts.show("Looking for ${game.title}…") }
            }
        }
    }

    /**
     * Starts a scrape, first asking to download the LaunchBox database when LaunchBox is on and doesn't have these
     * games' systems yet (it's about 100 MB). [started] runs once the scrape is under way.
     */
    fun start(scope: ScrapeScope, options: ScrapeOptions, started: () -> Unit = {}) {
        if (io.github.zahakj.tandem.NetGate.closed) { Toasts.show(io.github.zahakj.tandem.data.OFFLINE_NOTE); return }
        fun go(o: ScrapeOptions) {
            Scraper.dismiss()
            if (Scraper.start(scope, o)) started() else Toasts.show("Couldn't start, is a scrape already running?")
        }
        val needs = Scraper.launchBoxNeeds(scope, options)
        if (needs == null) { go(options); return }
        val metered = Scraper.metered()
        Confirm.ask(
            "Download LaunchBox's games database?",
            "LaunchBox needs no account: Tandem downloads its database once (about ${io.github.zahakj.tandem.data.LaunchBoxDb.APPROX_MB} MB" +
                (if (metered) ", and you're on a metered connection" else "") +
                "), keeps just your systems and looks games up on this device. It refreshes once a month on Wi-Fi.",
            "Download & scrape",
            alt = "Skip LaunchBox",
            altAction = { go(options.copy(sources = options.sources - ScrapeSource.LaunchBox)) },
        ) { go(options.copy(downloadLaunchBox = true)) }
    }

    /** True when there's a details screen to show a scrape's progress card on (the bottom one, or the top when swapped). */
    val hasCard: Boolean get() = ScreenRoles.bottomShowing

    /**
     * A scrape just started from the scrape screen: with a second screen, go back to the library and let the card
     * there follow it, so the user can keep browsing. Without one, the progress stays here.
     */
    fun toBackground() {
        if (!hasCard) return
        if (Nav.route == Route.Scrape) Nav.back()
        Toasts.show(if (ScreenRoles.libraryOnBottom) "Scraping in the background: progress is on the top screen" else "Scraping in the background: progress is on the bottom screen")
    }
}

fun scopeLabel(scope: ScrapeScope): String = when (scope) {
    ScrapeScope.All -> "All systems"
    is ScrapeScope.System -> Libraries.byId(scope.platformId)?.platform?.name ?: scope.platformId
    is ScrapeScope.Folder -> "${Libraries.byId(scope.platformId)?.platform?.shortName ?: scope.platformId} › ${scope.folder}"
    is ScrapeScope.One -> Libraries.games.firstOrNull { it.key == scope.key }?.title ?: "One game"
}

private fun formatBytes(b: Long): String = when {
    b < 1024 * 1024 -> "${b / 1024} KB"
    b < 1024L * 1024 * 1024 -> "%.1f MB".format(b / 1048576.0)
    else -> "%.2f GB".format(b / 1073741824.0)
}

/** Rough size of a video snap from ScreenScraper, for the size estimate. */
private const val VIDEO_BYTES = 3L * 1024 * 1024

// ---------------------------------------------------------------------------------------------------------------

@Composable
fun ScrapeScreen() {
    val run = Scraper.run
    if (run == null) ScrapeSetup() else ScrapeProgress(run)
}

private val ScrapeRoute get() = Route.Scrape.key

/** A switch on the scrape screen: its label, a line on it, and the switch. */
@Composable
private fun SwitchRow(key: String, label: String, detail: String, on: Boolean, color: Color, onClick: () -> Unit) {
    FocusCard(
        Modifier.fillMaxWidth().remembersFocus(ScrapeRoute, key, false),
        glow = color, shape = RoundedCornerShape(16.dp), lifts = false, onClick = onClick,
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(10.dp))
            Toggle(on, color)
        }
    }
}

/** What the setup view says about the games in scope before a run. */
private data class ScrapePreview(val games: Int, val noBox: Int, val noText: Int, val hidden: Int)

/** Which grid of options is open over the setup view. */
private enum class ScrapePick { Scope, Folder, Language }

/** One kind of artwork as a tile: its name, a short line, and a picture of it from one of your games when there is one. */
private fun kindLabel(k: MediaKind) = if (k == MediaKind.Front) "Physical boxes" else k.label

private fun kindDetail(k: MediaKind): String = when (k) {
    MediaKind.Box -> "The cover on every card"
    MediaKind.Hero -> "Wide, on the bottom screen"
    MediaKind.Logo -> "Over the hero"
    MediaKind.Title -> "The title screen"
    MediaKind.Gameplay -> "A moment from the game"
    MediaKind.Video -> "About 3 MB a game"
    MediaKind.Front, MediaKind.Spine -> "Box and spine, for the Shelf"
}

private fun kindGlyph(k: MediaKind): Glyph = when (k) {
    MediaKind.Box -> Glyph.Image
    MediaKind.Hero -> Glyph.Layers
    MediaKind.Logo -> Glyph.Text
    MediaKind.Title -> Glyph.Star
    MediaKind.Gameplay -> Glyph.Gamepad
    MediaKind.Video -> Glyph.Play
    MediaKind.Front, MediaKind.Spine -> Glyph.Cartridge
}

/** The tile's picture: wide kinds fill it, the box and the logo are shown whole. */
private fun kindFit(k: MediaKind): ContentScale = when (k) {
    MediaKind.Box, MediaKind.Logo, MediaKind.Front, MediaKind.Spine -> ContentScale.Fit
    else -> ContentScale.Crop
}

/** For each kind of artwork, a file of that kind one of [games] already has (the first found), to show on its tile. */
private fun samplesOf(games: List<Game>): Map<MediaKind, String> {
    val out = HashMap<MediaKind, String>()
    val wanted = MediaKind.entries.filter { it != MediaKind.Video && it != MediaKind.Spine }
    for (g in games) {
        if (Privacy.isHidden(g)) continue
        for (k in wanted) {
            if (k in out) continue
            val p = k.get(g.meta) ?: continue
            if (java.io.File(p).isFile) out[k] = p
        }
        if (out.size == wanted.size) break
    }
    return out
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ScrapeSetup() {
    val cfg = ScrapeConfig.settings
    val scope = ScrapeUi.scope
    val libs = Libraries.all.orEmpty()
    val kinds = cfg.kinds
    val route = ScrapeRoute
    var picking by remember { mutableStateOf<ScrapePick?>(null) }
    val preview by produceState<ScrapePreview?>(null, scope, Store.meta.size) {
        value = withContext(Dispatchers.IO) {
            val games = Scraper.gamesIn(scope)
            val hidden = when (scope) {
                ScrapeScope.All -> Libraries.games
                is ScrapeScope.System -> Libraries.games.filter { it.platform.id == scope.platformId }
                is ScrapeScope.Folder -> Libraries.games.filter { it.platform.id == scope.platformId && it.group == scope.folder }
                is ScrapeScope.One -> Libraries.games.filter { it.key == scope.key }
            }.count { Privacy.isHidden(it) }
            ScrapePreview(games.size, games.count { !Media.has(it, MediaKind.Box) }, games.count { MetaType.Description.missing(it.meta) }, hidden)
        }
    }
    // A picture of each kind of artwork from your own games, the ones in scope first.
    val samples by produceState<Map<MediaKind, String>>(emptyMap(), scope, Store.meta.size) {
        value = withContext(Dispatchers.IO) {
            runCatching { samplesOf(Scraper.gamesIn(scope) + Libraries.games) }.getOrDefault(emptyMap())
        }
    }
    val systemId = (scope as? ScrapeScope.System)?.platformId ?: (scope as? ScrapeScope.Folder)?.platformId
    val groups = systemId?.let { Libraries.byId(it)?.groups }.orEmpty()
    val withGames = libs.filter { it.games.isNotEmpty() }

    Row(Modifier.fillMaxSize().padding(start = 8.dp, end = 22.dp)) {
        BoxWithConstraints(Modifier.weight(1.25f).fillMaxHeight()) {
            // Room on both sides for a highlighted row, which grows a little.
            val side = maxWidth * 0.04f + 6.dp
            val setup = rememberRouteListState("scrape:setup")
            LazyColumn(
                Modifier.fillMaxSize().fadingEdges(setup),
                state = setup,
                contentPadding = PaddingValues(start = side, end = side, top = FocusRoom, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                item("h:what") { SectionTitle("What to scrape", Palette.Peach) }
                item("scope") {
                    val platform = systemId?.let { Libraries.byId(it)?.platform }
                    PickRow(
                        "Games", if (platform == null) "Every system with games" else "One system", scopeLabel(scope), Palette.Peach,
                        Modifier.remembersFocus(route, "scope", false),
                        glyph = if (platform == null) Glyph.Layers else null,
                        art = platform?.let { p -> { SystemIcon(p, 32.dp) } },
                    ) { picking = ScrapePick.Scope }
                }
                if (groups.isNotEmpty() && systemId != null) item("folder") {
                    PickRow(
                        "Folder", "Only the games in one of its folders", (scope as? ScrapeScope.Folder)?.folder ?: "All folders", Palette.Peach,
                        Modifier.remembersFocus(route, "folder", false), glyph = Glyph.Folder,
                    ) { picking = ScrapePick.Folder }
                }
                item("mode") {
                    SegmentRow(
                        "Mode", cfg.mode.detail, ScrapeMode.entries.map { it.label }, cfg.mode.ordinal, Palette.Lavender,
                        Modifier.remembersFocus(route, "mode", false), trackWidth = 360.dp,
                    ) { i -> ScrapeConfig.update { it.copy(mode = ScrapeMode.entries[i]) } }
                }
                item("locked") {
                    SwitchRow(
                        "locked", "Replace locked fields",
                        if (cfg.replaceLocked) "Also replaces what you edited or picked by hand" else "What you edited or picked by hand stays",
                        cfg.replaceLocked, Palette.Peach,
                    ) { ScrapeConfig.update { it.copy(replaceLocked = !it.replaceLocked) } }
                }
                item("h:art") { SectionTitle("Artwork", Palette.Mint, Modifier.padding(top = 6.dp)) }
                // The physical box front and spine are one choice: the Shelf view wants both.
                item("art") {
                    val shown = MediaKind.entries.filter { it != MediaKind.Spine }
                    androidx.compose.foundation.layout.FlowRow(
                        Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp), maxItemsInEachRow = 4,
                    ) {
                        shown.forEach { k ->
                            val on = k in kinds
                            val pair = if (k == MediaKind.Front) setOf(MediaKind.Front, MediaKind.Spine) else setOf(k)
                            val detail = if (k == MediaKind.Video && on && (preview?.games ?: 0) > 0) "~${formatBytes(VIDEO_BYTES * (preview?.games ?: 0))} in all" else kindDetail(k)
                            val sample = samples[k]
                            PictureToggle(
                                kindLabel(k), detail, on, Palette.Mint,
                                Modifier.weight(1f).height(122.dp).remembersFocus(route, "k:${k.name}", false),
                                glyph = kindGlyph(k),
                                art = sample?.let { file -> { AsyncImage(file, null, Modifier.fillMaxSize(), contentScale = kindFit(k)) } },
                            ) { ScrapeConfig.update { it.copy(kinds = if (k in it.kinds) it.kinds - pair else it.kinds + pair) } }
                        }
                        // Keeps the last line's tiles the same width as the others.
                        repeat((4 - shown.size % 4) % 4) { Spacer(Modifier.weight(1f)) }
                    }
                }
                item("h:details") { SectionTitle("Details", Palette.Sky, Modifier.padding(top = 6.dp)) }
                item("meta") {
                    val canDetails = cfg.active.any { s -> s.details.any { it != MetaType.TimeToBeat } }
                    SwitchRow(
                        "meta", "Game details",
                        if (canDetails || !cfg.metadata) "Names, descriptions, genres, year, players…" else "Needs a source: turn on LaunchBox, ScreenScraper or IGDB below",
                        cfg.metadata, Palette.Sky,
                    ) { ScrapeConfig.update { it.copy(metadata = !it.metadata) } }
                }
                if (cfg.metadata) item("details") {
                    androidx.compose.foundation.layout.FlowRow(
                        Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp), maxItemsInEachRow = 4,
                    ) {
                        MetaType.entries.forEach { t ->
                            val on = t in cfg.details
                            val needsHltb = t == MetaType.TimeToBeat && ScrapeSource.Hltb !in cfg.active
                            TickChip(
                                t.label, on, Palette.Sky, Modifier.weight(1f).remembersFocus(route, "m:${t.name}", false),
                                note = if (on && needsHltb) "Needs HowLongToBeat" else null,
                            ) { ScrapeConfig.update { it.copy(details = if (t in it.details) it.details - t else it.details + t) } }
                        }
                        repeat((4 - MetaType.entries.size % 4) % 4) { Spacer(Modifier.weight(1f)) }
                    }
                }
                item("h:how") { SectionTitle("Region & language", Palette.Lavender, Modifier.padding(top = 6.dp)) }
                item("region") {
                    val r = cfg.regionPref
                    SegmentRow(
                        "Region", "Which box art and release to prefer", Region.entries.map { it.label }, r.ordinal, Palette.Lavender,
                        Modifier.remembersFocus(route, "region", false),
                    ) { i -> ScrapeConfig.update { it.copy(region = Region.entries[i].code) } }
                }
                item("lang") {
                    val l = cfg.languagePref
                    PickRow(
                        "Language", "For descriptions and genres (ScreenScraper)", LANGUAGES[l] ?: l, Palette.Lavender,
                        Modifier.remembersFocus(route, "lang", false), glyph = Glyph.Text,
                    ) { picking = ScrapePick.Language }
                }
                item("h:acc") { SectionTitle("Sources", Palette.Butter, Modifier.padding(top = 6.dp)) }
                item("sources") { SourcesCard(Modifier.remembersFocus(route, "sources", false)) }
            }
        }
        // Summary and the start button.
        Column(
            Modifier.weight(0.75f).fillMaxHeight().padding(start = 10.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
        ) {
            Pip(92.dp, mood = 1)
            Spacer(Modifier.height(10.dp))
            val p = preview
            Text(
                when {
                    p == null -> "Counting games…"
                    p.games == 0 -> "No games to scrape here"
                    p.games == 1 -> "1 game to check"
                    else -> "${p.games} games to check"
                },
                style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center,
            )
            if (p != null && p.games > 0) {
                Text(
                    // Missing details count too: a game with all its art can still lack a description.
                    when {
                        p.noBox == 0 && p.noText == 0 -> "Every one already has box art and a description"
                        p.noBox == 0 -> "All have box art · ${p.noText} without a description"
                        p.noText == 0 -> "${p.noBox} without box art yet"
                        else -> "${p.noBox} without box art · ${p.noText} without a description"
                    },
                    style = MaterialTheme.typography.bodyMedium, color = Palette.Muted, textAlign = TextAlign.Center,
                )
            }
            // In private mode even the number of hidden games stays private.
            if ((p?.hidden ?: 0) > 0 && !io.github.zahakj.tandem.Privacy.enabled) {
                Text("${plural(p!!.hidden, "hidden game")} skipped, never sent anywhere", style = MaterialTheme.typography.bodyMedium, color = Palette.Muted, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(16.dp))
            val wantsSomething = cfg.mode == ScrapeMode.Basics || kinds.isNotEmpty() || (cfg.metadata && cfg.details.isNotEmpty())
            val canStart = (p?.games ?: 0) > 0 && wantsSomething && cfg.active.isNotEmpty()
            FocusCard(
                Modifier.width(210.dp).height(54.dp).remembersFocus(route, "start", true),
                glow = Palette.Mint, shape = RoundedCornerShape(27.dp),
                onClick = {
                    when {
                        p == null -> Unit
                        p.games == 0 -> Toasts.show("No games to scrape here")
                        !wantsSomething -> Toasts.show("Turn on at least one kind of artwork or detail")
                        cfg.active.isEmpty() -> Toasts.show("Turn on at least one source under Sources")
                        else -> ScrapeUi.start(scope, Scraper.optionsFromSettings()) { ScrapeUi.toBackground() }
                    }
                },
            ) {
                Box(
                    Modifier.fillMaxSize().background(
                        if (canStart) Brush.horizontalGradient(listOf(Palette.Mint, Palette.Sky)) else Brush.horizontalGradient(listOf(Palette.CardHigh, Palette.CardHigh)),
                    ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Start scraping", style = MaterialTheme.typography.titleLarge, color = if (canStart) Palette.NightDeep else Palette.Muted)
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                if (ScrapeUi.hasCard) "I'll work in the background and show how it's going on the other screen, pausing while you play."
                else "You can leave this screen. I'll keep going and pause while you play.",
                style = MaterialTheme.typography.bodyMedium, color = Palette.Muted, textAlign = TextAlign.Center)
        }
    }

    // The grids of options the rows above open.
    val scopeChoices = remember(withGames) {
        listOf(PickOption<ScrapeScope>(ScrapeScope.All, "All systems", plural(withGames.sumOf { it.games.size }, "game"), art = { GlyphChip(Glyph.Layers, Palette.Peach, size = 52.dp, round = true) })) +
            withGames.map { lib -> PickOption<ScrapeScope>(ScrapeScope.System(lib.platform.id), lib.platform.name, plural(lib.games.size, "game"), art = { SystemIcon(lib.platform, 56.dp) }) }
    }
    ChoicePopup(
        picking == ScrapePick.Scope, "Which games", "Scrape every system, or just one", scopeChoices,
        if (scope is ScrapeScope.Folder) ScrapeScope.System(scope.platformId) else scope, Palette.Peach, "scrape:pick:scope",
        onClose = { picking = null; Nav.focusTick++ },
    ) { ScrapeUi.scope = it }
    if (systemId != null) {
        val folderChoices = remember(systemId, groups) {
            listOf(PickOption<String?>(null, "All folders", glyph = Glyph.Layers)) + groups.map { PickOption<String?>(it, it, glyph = Glyph.Folder) }
        }
        ChoicePopup(
            picking == ScrapePick.Folder, "Which folder", scopeLabel(ScrapeScope.System(systemId)), folderChoices,
            (scope as? ScrapeScope.Folder)?.folder, Palette.Peach, "scrape:pick:folder",
            onClose = { picking = null; Nav.focusTick++ },
        ) { f -> ScrapeUi.scope = if (f == null) ScrapeScope.System(systemId) else ScrapeScope.Folder(systemId, f) }
    }
    ChoicePopup(
        picking == ScrapePick.Language, "Language", "Descriptions and genres in this language, where ScreenScraper has it",
        remember { LANGUAGES.map { (code, name) -> PickOption(code, name, code.uppercase()) } },
        cfg.languagePref, Palette.Lavender, "scrape:pick:lang",
        onClose = { picking = null; Nav.focusTick++ },
    ) { code -> ScrapeConfig.update { it.copy(language = code) } }
}

/**
 * The sources in the order a scrape asks them, read-only: their icons and states in a line. Setting them up and
 * changing the order happen in one place, the Set up sources grid (ArtSetup.kt), which A opens.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun SourcesCard(modifier: Modifier) {
    val cfg = ScrapeConfig.settings
    FocusCard(modifier.fillMaxWidth(), glow = Palette.Butter, shape = RoundedCornerShape(16.dp), lifts = false, onClick = { ArtSetup.openWizard() }) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Each game asks the first one on, then the next only for what's still missing",
                    Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = Palette.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(10.dp))
                Box(
                    Modifier.height(30.dp).clip(RoundedCornerShape(50)).background(Palette.Butter).padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center,
                ) { Text("Set up sources", style = MaterialTheme.typography.labelLarge, color = Palette.OnAccent, maxLines = 1) }
            }
            Spacer(Modifier.height(8.dp))
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                cfg.sourceOrder.forEachIndexed { i, s ->
                    val (status, color) = sourceStatus(s, cfg)
                    val on = cfg.isOn(s)
                    Row(
                        Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = if (on) 0.16f else 0.07f))
                            .padding(start = 4.dp, end = 10.dp, top = 3.dp, bottom = 3.dp).graphicsLayer { alpha = if (on) 1f else 0.6f },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SourceChip(s, 24.dp, on)
                        Spacer(Modifier.width(6.dp))
                        Text("${i + 1}. ${if (s == ScrapeSource.Libretro) "libretro" else s.label}", style = MaterialTheme.typography.labelLarge, maxLines = 1)
                        Spacer(Modifier.width(6.dp))
                        Text(status, style = MaterialTheme.typography.labelMedium, color = color, maxLines = 1)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------

@Composable
private fun ScrapeProgress(run: ScrapeRun) {
    var showMissed by remember(run) { mutableStateOf(false) }
    val routeKey = "scrape:run"
    Row(Modifier.fillMaxSize().padding(horizontal = 22.dp).padding(bottom = 14.dp)) {
        Column(Modifier.width(344.dp).fillMaxHeight().padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Pip(64.dp, mood = when (run.state) { RunState.Paused -> 2; RunState.Cancelled -> 0; else -> 1 })
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        runTitle(run),
                        style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(scopeLabel(run.scope), style = MaterialTheme.typography.bodyMedium, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            // While the LaunchBox database downloads, the bar shows that instead of the games.
            val prep = run.prep
            ProgressBar(prep ?: if (run.total == 0) 1f else run.done.toFloat() / run.total)
            Row {
                Text(
                    if (prep != null) "Getting LaunchBox ready: ${(prep * 100).toInt()}%" else "${run.done} of ${plural(run.total, "game")}",
                    Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (run.bytes > 0) Text(formatBytes(run.bytes), style = MaterialTheme.typography.titleSmall, color = Palette.Muted)
            }
            if (!run.finished && run.current.isNotEmpty()) {
                Text(run.current, style = MaterialTheme.typography.labelLarge, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Stat(Palette.Mint, "${run.withArt} got new artwork")
            if (run.options.types.isNotEmpty()) Stat(Palette.Sky, "${run.withDetails} got details")
            Stat(Palette.Lavender, "${run.upToDate} already complete")
            if (run.notFound > 0) Stat(Palette.Peach, "${run.notFound} not found")
            if (run.failed > 0) Stat(Palette.Butter, "${run.failed} failed to download, try again later")
            runFooter(run)?.let { Text(it, style = MaterialTheme.typography.labelLarge, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            // At most two notes, one line each, so the buttons below always fit.
            run.notes.take(2).forEach { Text("• $it", style = MaterialTheme.typography.labelLarge, color = Palette.Peach, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            Spacer(Modifier.weight(1f))
            // When the run ends, Stop goes away and Done takes the focus.
            val target = rememberFocusTarget(routeKey, if (!run.finished) listOf("stop", "pause") else listOfNotNull("done", "new", "missed".takeIf { run.missedCount > 0 }))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 8.dp)) {
                if (!run.finished) {
                    PillButton("Stop", Palette.Peach, Modifier.takesFocus(routeKey, "stop", target == "stop")) { Scraper.cancel() }
                    PillButton(if (run.held) "Resume" else "Pause", Palette.Butter, Modifier.takesFocus(routeKey, "pause", target == "pause")) { Scraper.togglePause() }
                    if (ScrapeUi.hasCard) PillButton("Keep browsing", Palette.Mint, Modifier.takesFocus(routeKey, "hide", target == "hide")) { Nav.back() }
                } else {
                    PillButton("Done", Palette.Mint, Modifier.takesFocus(routeKey, "done", target == "done")) { Scraper.dismiss(); Nav.back() }
                    PillButton("New scrape", Palette.Sky, Modifier.takesFocus(routeKey, "new", target == "new")) { Scraper.dismiss() }
                    if (run.missedCount > 0) {
                        PillButton(if (showMissed) "Covers" else "Missed", Palette.Peach, Modifier.takesFocus(routeKey, "missed", target == "missed")) { showMissed = !showMissed }
                    }
                }
            }
        }
        Spacer(Modifier.width(18.dp))
        Column(Modifier.weight(1f).fillMaxHeight()) {
            if (showMissed && run.missed.isNotEmpty()) {
                SectionTitle("Not found: pick art by hand", Palette.Peach, Modifier.padding(top = 6.dp))
                MissedList(run)
            } else {
                SectionTitle(if (run.finished) "New artwork" else "Just found", Palette.Mint, Modifier.padding(top = 6.dp))
                if (run.recent.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            if (run.finished) "Nothing new this time" else "Covers will pop in here as I find them",
                            style = MaterialTheme.typography.bodyMedium, color = Palette.Muted,
                        )
                    }
                } else {
                    val found = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
                    LazyVerticalGrid(
                        GridCells.Adaptive(88.dp),
                        Modifier.fadingEdges(found),
                        state = found,
                        contentPadding = PaddingValues(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(run.recent, key = { it.file.path }) { f -> FoundCover(f) }
                    }
                }
            }
        }
    }
}

@Composable
private fun MissedList(run: ScrapeRun) {
    val games = remember(run.missed) {
        val byKey = Libraries.games.associateBy { it.key }
        run.missed.mapNotNull { byKey[it] }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val side = maxWidth * 0.055f + 6.dp
        val missed = androidx.compose.foundation.lazy.rememberLazyListState()
        LazyColumn(Modifier.fadingEdges(missed), state = missed, contentPadding = PaddingValues(horizontal = side, vertical = FocusRoom), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(games, key = { it.key }) { g ->
                FocusCard(Modifier.fillMaxWidth(), glow = Palette.Peach, shape = RoundedCornerShape(14.dp), onClick = { GameEditor.chooseArt(g) }) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(34.dp).clip(RoundedCornerShape(8.dp))) { GameArt(g) }
                        Spacer(Modifier.width(10.dp))
                        Text(g.title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Pill(g.platform.shortName, Palette.forName(g.platform.id))
                    }
                }
            }
        }
    }
}

@Composable
private fun Stat(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** The headline for a run, on the scrape screen and the progress card. */
private fun runTitle(run: ScrapeRun): String = when (run.state) {
    RunState.Running -> if (run.prep != null || run.done == 0) "Getting ready…" else "Finding art…"
    RunState.Paused -> if (run.held) "Paused" else "Paused while you play"
    RunState.Done -> "All done!"
    RunState.Cancelled -> "Stopped"
}

/** "LaunchBox · about 3 min left · 12.4 MB" while running; null when there's nothing to say. */
private fun runFooter(run: ScrapeRun): String? {
    if (run.finished) return null
    val eta = run.eta?.let { s -> if (s < 60) "under a minute left" else if (s < 120) "about a minute left" else "about ${s / 60} min left" }
    return listOfNotNull(run.source.ifEmpty { null }, eta, run.bytes.takeIf { it > 0 }?.let(::formatBytes)).joinToString(" · ").ifEmpty { null }
}

/** "New art for 12 games · details for 30 · 8.1 MB", or that nothing changed. */
private fun runSummary(run: ScrapeRun): String = listOfNotNull(
    run.withArt.takeIf { it > 0 }?.let { "new art for ${plural(it, "game")}" },
    run.withDetails.takeIf { it > 0 }?.let { if (run.withArt > 0) "details for $it" else "new details for ${plural(it, "game")}" },
    run.bytes.takeIf { it > 0 }?.let(::formatBytes),
).joinToString(" · ").replaceFirstChar { it.uppercase() }.ifEmpty { "Nothing new this time" }

/**
 * A progress bar. The fill animates in the draw phase only, so a moving bar never recomposes (or re-lays out)
 * anything around it.
 */
@Composable
fun ProgressBar(fraction: Float, modifier: Modifier = Modifier) {
    val target = fraction.coerceIn(0f, 1f)
    val f = remember { Animatable(target) }
    LaunchedEffect(target) { f.animateTo(target, tween(400)) }
    val colors = listOf(Palette.Mint, Palette.Sky)
    Box(
        modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(50)).background(Palette.CardHigh).drawBehind {
            val w = size.width * f.value
            if (w > 0f) drawRoundRect(Brush.horizontalGradient(colors, endX = w), size = Size(w, size.height), cornerRadius = CornerRadius(size.height / 2))
        },
    )
}

/**
 * A rounded button: a FocusCard with a word on it. Highlighted, it grows around its centre without lifting, so a row
 * of them stays in line. With [fill] it takes the width it is given (a share of a row) with the word centred, and
 * a word too long for it shrinks a little instead of being cut. [filled] paints it in its colour, for the one button
 * of a row that does the obvious next thing.
 */
@Composable
fun PillButton(text: String, color: Color, modifier: Modifier, fill: Boolean = false, filled: Boolean = false, onClick: () -> Unit) {
    val ink = if (filled) Palette.OnAccent else color
    FocusCard(modifier.height(42.dp), glow = color, shape = RoundedCornerShape(21.dp), lifts = false, onClick = onClick) {
        Box(
            (if (fill) Modifier.fillMaxSize() else Modifier.fillMaxHeight()).background(if (filled) color else color.copy(alpha = 0.22f)).padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (fill) ShrinkToFit { Text(text, style = MaterialTheme.typography.titleSmall, color = ink, maxLines = 1) }
            else Text(text, style = MaterialTheme.typography.titleSmall, color = ink, maxLines = 1)
        }
    }
}

/** A newly found cover that pops in with a little bounce. */
@Composable
private fun FoundCover(f: Found) {
    val still = Store.settings.reduceMotion
    val s = remember { Animatable(if (still) 1f else 0.3f) }
    LaunchedEffect(Unit) { s.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 260f)) }
    Box(
        Modifier.aspectRatio(1f)
            .graphicsLayer { scaleX = s.value; scaleY = s.value; alpha = ((s.value - 0.3f) / 0.7f).coerceIn(0f, 1f) }
            .clip(RoundedCornerShape(14.dp)).background(Palette.Card),
    ) {
        AsyncImage(f.file, f.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}

// ---------------------------------------------------------------------------------------------------------------

private fun pillText(run: ScrapeRun): String = when (run.state) {
    RunState.Paused -> "⏸ Scraping paused"
    else -> if (run.total == 1) "✦ Scraping…" else "✦ Scraping ${run.done}/${run.total}"
}

/** Shown in the top bar while a scrape runs, wherever the user is. */
@Composable
fun ScrapePill() {
    val run = Scraper.run ?: return
    if (run.finished) return
    Pill(pillText(run), Palette.Butter, Modifier.padding(end = 10.dp))
}

/** The same, floating in a corner, for screens without the top bar (the icon grid, fullscreen folders). */
@Composable
fun ScrapeBadge(show: Boolean) {
    val run = Scraper.run?.takeIf { show && !it.finished } ?: return
    // Top right, clear of the corner hints; a toast there covers it only for a moment.
    Box(Modifier.fillMaxSize().padding(14.dp), contentAlignment = Alignment.TopEnd) {
        Pill(pillText(run), Palette.Butter, Modifier.clip(RoundedCornerShape(50)).background(Palette.Card.copy(alpha = 0.85f)))
    }
}

private object ScrapeCardState {
    /** The run whose card the user closed (or whose summary has been up a while), so it stays closed. */
    var closed by mutableStateOf<ScrapeRun?>(null)
    /** The card's height in pixels (with its margin) while it shows, 0 when it doesn't; see [clearOfScrapeCard]. */
    var height by androidx.compose.runtime.mutableIntStateOf(0)
}

/**
 * Lays the details screen out above the scrape card instead of under it, so the card never covers the end of a
 * description or the button hints: the content gets the height the card leaves. Read in the layout phase only, so
 * the card showing, growing or going away re-lays out the screen without recomposing it.
 */
fun Modifier.clearOfScrapeCard(): Modifier = layout { m, c ->
    val room = if (c.hasBoundedHeight) ScrapeCardState.height.coerceIn(0, c.maxHeight) else 0
    val p = m.measure(c.copy(minHeight = (c.minHeight - room).coerceAtLeast(0), maxHeight = if (c.hasBoundedHeight) c.maxHeight - room else c.maxHeight))
    layout(p.width, p.height + room) { p.place(0, 0) }
}

/**
 * The scrape's progress on the details screen: the newest art as it arrives, what's being looked up,
 * counts, the source and time left, with Pause and Cancel under a thumb (it's a touch screen), and a summary when
 * it's done. Only reads the run's state, which changes at most four times a second.
 */
@Composable
fun ScrapeCard() {
    val run = Scraper.run
    // A single game's scrape answers with a toast instead.
    val show = run != null && run.scope !is ScrapeScope.One && ScrapeCardState.closed !== run
    val finished = run?.finished == true
    LaunchedEffect(run, finished) {
        if (run != null && finished) { delay(30_000); ScrapeCardState.closed = run }
    }
    val shown = rememberLatest(run.takeIf { show })
    // Going away: the screen above takes its room back as the card starts sliding out.
    androidx.compose.runtime.SideEffect { if (!show) ScrapeCardState.height = 0 }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(show, enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
            shown?.let { ScrapeCardBody(it, Modifier.onSizeChanged { size -> if (show) ScrapeCardState.height = size.height }) }
        }
    }
}

@Composable
private fun ScrapeCardBody(run: ScrapeRun, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val latest = run.recent.firstOrNull()
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier.padding(14.dp).widthIn(max = 620.dp).fillMaxWidth().shadow(16.dp, shape).clip(shape)
            .background(Palette.Card).swallowTaps().padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // The newest art found, or Pip until there is some.
            Box(Modifier.size(84.dp).clip(RoundedCornerShape(16.dp)).background(Palette.CardHigh), contentAlignment = Alignment.Center) {
                if (latest == null) {
                    Pip(54.dp, mood = if (run.state == RunState.Cancelled) 0 else 1, bounce = false)
                } else {
                    val request = remember(latest.file) { ImageRequest.Builder(ctx).data(latest.file).size(256).crossfade(true).build() }
                    AsyncImage(request, latest.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(runTitle(run), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${run.done} / ${run.total}", style = MaterialTheme.typography.titleSmall, color = Palette.Muted)
                }
                val prep = run.prep
                Text(
                    when {
                        prep != null -> "Getting LaunchBox ready: ${(prep * 100).toInt()}%"
                        !run.finished && run.current.isNotEmpty() -> run.current
                        latest != null -> "Last found: ${latest.title}"
                        else -> scopeLabel(run.scope)
                    },
                    style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                ProgressBar(prep ?: if (run.total == 0) 1f else run.done.toFloat() / run.total)
                Spacer(Modifier.height(5.dp))
                Text(
                    runFooter(run) ?: if (run.finished) runSummary(run) else scopeLabel(run.scope),
                    style = MaterialTheme.typography.labelMedium, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // Counts wrap onto a second line on a narrow screen rather than squeezing the buttons.
            @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
            androidx.compose.foundation.layout.FlowRow(
                Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Count(Palette.Mint, run.withArt, "found")
                if (run.options.types.isNotEmpty()) Count(Palette.Sky, run.withDetails, "details")
                Count(Palette.Peach, run.notFound, "missing")
                if (run.failed > 0) Count(Palette.Butter, run.failed, "failed")
                Count(Palette.Lavender, run.upToDate, "complete")
            }
            if (!run.finished) {
                CardButton(if (run.held) "Resume" else "Pause", Palette.Butter) { Scraper.togglePause() }
                CardButton("Cancel", Palette.Peach) { Scraper.cancel() }
            } else {
                CardButton("Close", Palette.Mint) { ScrapeCardState.closed = run }
            }
        }
        // Why a source was skipped, say; one line so the card keeps its size.
        run.notes.firstOrNull()?.let { Text("• $it", style = MaterialTheme.typography.labelMedium, color = Palette.Peach, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
}

@Composable
private fun Count(color: Color, n: Int, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(5.dp))
        Text("$n $label", style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

/** A tappable pill on the card (the details screen takes touches, never the controller's focus). */
@Composable
private fun CardButton(text: String, color: Color, onTap: () -> Unit) {
    Box(
        Modifier.height(36.dp).clip(RoundedCornerShape(18.dp)).background(color.copy(alpha = 0.22f))
            .swallowTaps { Sounds.confirm(); onTap() }.padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = MaterialTheme.typography.labelLarge, color = color) }
}

/**
 * Lives in the app root: pauses scraping while Tandem is in the background (a game is running) and tells the user
 * how a scrape went when they're not on the scrape screen.
 */
@Composable
fun ScrapeHost() {
    val owner = LocalContext.current as? LifecycleOwner
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_STOP -> Scraper.backgrounded.value = true
                Lifecycle.Event.ON_START -> Scraper.backgrounded.value = false
                else -> Unit
            }
        }
        owner?.lifecycle?.addObserver(observer)
        onDispose { owner?.lifecycle?.removeObserver(observer) }
    }
    val run = Scraper.run
    LaunchedEffect(run, run?.state) {
        val r = run ?: return@LaunchedEffect
        if (r.state != RunState.Done) return@LaunchedEffect
        val one = r.scope as? ScrapeScope.One
        if (one != null) {
            val title = Libraries.games.firstOrNull { it.key == one.key }?.title ?: "that game"
            Toasts.show(
                when {
                    r.withArt > 0 && r.withDetails > 0 -> "New art and details for $title ✨"
                    r.withArt > 0 -> "New art for $title ✨"
                    r.withDetails > 0 -> "New details for $title ✨"
                    r.notes.isNotEmpty() -> r.notes.first()
                    else -> "Couldn't find anything new for $title"
                },
            )
            if (Nav.route != Route.Scrape) Scraper.dismiss()
        } else if (Nav.route != Route.Scrape && !ScrapeUi.hasCard) {
            // With a second screen, the card there shows the summary instead.
            Toasts.show("Scraping done: new art for ${plural(r.withArt, "game")} ✨")
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------

/** The last measured size of downloaded media, and what it was measured for. */
private object MediaSizeCache {
    var key: Any? = null
    var value: Pair<Int, Long>? = null
}

/**
 * Settings › Library & art › Artwork & accounts. What a scrape fetches, the region and language are chosen on the
 * Scrape library screen, and the sources and their order under Set up sources; Settings doesn't repeat them.
 */
@Composable
fun scrapingSettings(): SettingSection {
    val s = ScrapeConfig.settings
    // Every Settings page composes this row (for search and summaries), so the folder walk is done once per change.
    val sizeKey = listOf(Scraper.run?.state, GameEditor.clearCount)
    val size by produceState(MediaSizeCache.value.takeIf { MediaSizeCache.key == sizeKey }, sizeKey) {
        if (MediaSizeCache.key == sizeKey && MediaSizeCache.value != null) return@produceState
        value = withContext(Dispatchers.IO) { Scraper.downloadedSize() }
        MediaSizeCache.key = sizeKey
        MediaSizeCache.value = value
    }
    return SettingSection(
        "Artwork & details",
        listOf(
            SettingRow(
                "Scrape library",
                Scraper.run?.takeIf { !it.finished }?.let { "${it.done}/${it.total}" } ?: "Art & details",
                Palette.Butter, Glyph.Download,
                "Download box art, logos, heroes, videos and descriptions for your games, from LaunchBox and libretro (no account needed), ScreenScraper, IGDB, SteamGridDB and HowLongToBeat.",
                opens = true, keywords = "scraper download artwork media metadata covers boxart",
            ) { ScrapeUi.open() },
            SettingRow(
                "Set up sources", "${s.active.size} of ${ScrapeSource.entries.size} on", Palette.Butter, Glyph.Wand,
                "Artwork & details setup: what each source gives, which need an account, where to get a free key, and a Test button for each. Also changes the order they're asked in.",
                opens = true, keywords = "wizard onboarding accounts keys tokens api order priority launchbox igdb twitch steamgriddb screenscraper howlongtobeat",
            ) { ArtSetup.openWizard() },
            SettingRow(
                "Clear downloaded media",
                size?.let { (n, b) -> if (n == 0) "Nothing yet" else "$n files · ${formatBytes(b)}" } ?: "…",
                Palette.Peach, Glyph.Restore,
                "Delete what scraping downloaded, for one system or all of them, to free up space. Asks first.",
                keywords = "delete space storage free",
            ) {
                when {
                    Scraper.running -> Toasts.show("Wait for the scrape to finish first")
                    size?.first == 0 -> Toasts.show("Nothing downloaded yet")
                    else -> GameEditor.confirmClear(size)
                }
            },
        ),
    )
}
