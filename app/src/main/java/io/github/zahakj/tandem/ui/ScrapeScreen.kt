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
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
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

    fun open(scope: ScrapeScope = ScrapeScope.All) {
        if (!Scraper.running) Scraper.dismiss()
        this.scope = scope
        if (Nav.route != Route.Scrape) Nav.go(Route.Scrape)
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

/** A row of the scrape screen's option list. */
@Composable
private fun OptionRow(
    key: String, label: String, value: String, color: Color = Palette.Mint, detail: String? = null,
    onSide: ((Int) -> Unit)? = null, onClick: () -> Unit,
) {
    val route = Route.Scrape.key
    FocusCard(
        Modifier.fillMaxWidth().remembersFocus(route, key, false).onPreviewKeyEvent { e ->
            // Left / Right step through a choice (or move a source), when the row has one.
            val by = when (e.key) { Key.DirectionLeft -> -1; Key.DirectionRight -> 1; else -> 0 }
            if (onSide == null || by == 0) return@onPreviewKeyEvent false
            if (e.type == KeyEventType.KeyDown) { onSide(by); Sounds.move() }
            true
        },
        glow = color, shape = RoundedCornerShape(16.dp),
        onFocus = { FocusMemory.remember(route, key) }, onClick = onClick,
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.titleSmall)
                if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(8.dp))
            Pill(value, color)
        }
    }
}

/** What the setup view says about the games in scope before a run. */
private data class ScrapePreview(val games: Int, val noBox: Int, val noText: Int, val hidden: Int)

@Composable
private fun ScrapeSetup() {
    val cfg = ScrapeConfig.settings
    val scope = ScrapeUi.scope
    val libs = Libraries.all.orEmpty()
    val kinds = cfg.kinds
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
    val systemId = (scope as? ScrapeScope.System)?.platformId ?: (scope as? ScrapeScope.Folder)?.platformId
    val groups = systemId?.let { Libraries.byId(it)?.groups }.orEmpty()

    Row(Modifier.fillMaxSize().padding(start = 8.dp, end = 22.dp)) {
        BoxWithConstraints(Modifier.weight(1.25f).fillMaxHeight()) {
            // Room on both sides for a highlighted row, which grows by a tenth.
            val side = maxWidth * 0.055f + 6.dp
            val setup = rememberRouteListState("scrape:setup")
            LazyColumn(
                Modifier.fillMaxSize().fadingEdges(setup),
                state = setup,
                contentPadding = PaddingValues(start = side, end = side, top = FocusRoom, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                item("h:what") { SectionTitle("What to scrape", Palette.Peach) }
                item("scope") {
                    fun step(d: Int) {
                        val order = listOf<ScrapeScope>(ScrapeScope.All) + libs.filter { it.games.isNotEmpty() }.map { ScrapeScope.System(it.platform.id) }
                        val at = order.indexOfFirst { it == scope || (it is ScrapeScope.System && it.platformId == systemId) }.coerceAtLeast(0)
                        ScrapeUi.scope = order[(at + d + order.size) % order.size]
                    }
                    OptionRow("scope", "Games", scopeLabel(scope), Palette.Peach, "◀▶ All systems or one", onSide = ::step) { step(1) }
                }
                if (groups.isNotEmpty() && systemId != null) item("folder") {
                    OptionRow("folder", "Folder", (scope as? ScrapeScope.Folder)?.folder ?: "All folders", Palette.Peach) {
                        val order = listOf<String?>(null) + groups
                        val next = order[(order.indexOf((scope as? ScrapeScope.Folder)?.folder) + 1) % order.size]
                        ScrapeUi.scope = if (next == null) ScrapeScope.System(systemId) else ScrapeScope.Folder(systemId, next)
                    }
                }
                item("mode") {
                    fun step(d: Int) = ScrapeConfig.update { it.copy(mode = ScrapeMode.entries[(it.mode.ordinal + d + ScrapeMode.entries.size) % ScrapeMode.entries.size]) }
                    OptionRow("mode", "Mode", cfg.mode.label, Palette.Lavender, cfg.mode.detail, onSide = ::step) { step(1) }
                }
                item("locked") {
                    OptionRow(
                        "locked", "Replace locked fields", onOff(cfg.replaceLocked), if (cfg.replaceLocked) Palette.Peach else Palette.Muted,
                        if (cfg.replaceLocked) "Also replaces what you edited or picked by hand" else "What you edited or picked by hand stays",
                    ) { ScrapeConfig.update { it.copy(replaceLocked = !it.replaceLocked) } }
                }
                item("h:art") { SectionTitle("Artwork", Palette.Mint, Modifier.padding(top = 6.dp)) }
                items(MediaKind.entries, key = { "k:${it.name}" }) { k ->
                    val on = k in kinds
                    val value = when {
                        k == MediaKind.Video && on -> "On · ~${formatBytes(VIDEO_BYTES * (preview?.games ?: 0))}"
                        else -> onOff(on)
                    }
                    val detail = when (k) {
                        MediaKind.Box -> "The cover on every card"
                        MediaKind.Hero -> "Wide picture on the bottom screen"
                        MediaKind.Logo -> "Game logo over the hero"
                        MediaKind.Title -> "The game's title screen"
                        MediaKind.Gameplay -> "A moment from the game"
                        MediaKind.Video -> "Short clip, about 3 MB a game (ScreenScraper, some LaunchBox games)"
                    }
                    OptionRow("k:${k.name}", k.label, value, if (on) Palette.Mint else Palette.Muted, detail) {
                        ScrapeConfig.update { it.copy(kinds = if (k in it.kinds) it.kinds - k else it.kinds + k) }
                    }
                }
                item("h:details") { SectionTitle("Details", Palette.Sky, Modifier.padding(top = 6.dp)) }
                item("meta") {
                    val canDetails = cfg.active.any { s -> s.details.any { it != MetaType.TimeToBeat } }
                    OptionRow(
                        "meta", "Game details", if (!cfg.metadata) "Off" else if (canDetails) "On" else "Needs a source",
                        if (cfg.metadata) Palette.Sky else Palette.Muted,
                        if (canDetails || !cfg.metadata) "Names, descriptions, genres, year, players…" else "Turn on LaunchBox, ScreenScraper or IGDB below",
                    ) { ScrapeConfig.update { it.copy(metadata = !it.metadata) } }
                }
                if (cfg.metadata) items(MetaType.entries, key = { "m:${it.name}" }) { t ->
                    val on = t in cfg.details
                    val needsHltb = t == MetaType.TimeToBeat && ScrapeSource.Hltb !in cfg.active
                    OptionRow(
                        "m:${t.name}", t.label, if (on && needsHltb) "Needs HowLongToBeat" else onOff(on),
                        if (on) Palette.Sky else Palette.Muted, t.detail,
                    ) { ScrapeConfig.update { it.copy(details = if (t in it.details) it.details - t else it.details + t) } }
                }
                item("h:how") { SectionTitle("Region & language", Palette.Lavender, Modifier.padding(top = 6.dp)) }
                item("region") { RegionRow("region") }
                item("lang") { LanguageRow("lang") }
                item("h:acc") { SectionTitle("Sources, in order", Palette.Butter, Modifier.padding(top = 6.dp)) }
                item("acc:hint") {
                    Text(
                        "Each game asks the first source, then the next one only for what's still missing. ◀▶ moves a source; A sets it up.",
                        Modifier.padding(horizontal = 6.dp), style = MaterialTheme.typography.bodySmall, color = Palette.Muted,
                    )
                }
                itemsIndexed(cfg.sourceOrder, key = { _, s -> "src:${s.name}" }) { i, s ->
                    val (status, color) = sourceStatus(s, cfg)
                    OptionRow(
                        "src:${s.name}", "${i + 1}. ${s.label}", status, color, s.gives,
                        onSide = { by -> ScrapeConfig.update { it.moved(s, by) } },
                    ) { ArtSetup.open(s) }
                }
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
            Spacer(Modifier.height(6.dp))
            Text(
                cfg.active.joinToString(" › ") { if (it == ScrapeSource.Libretro) "libretro" else it.label }.ifEmpty { "No sources on" },
                style = MaterialTheme.typography.labelMedium, color = Palette.Butter, textAlign = TextAlign.Center,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            // In private mode even the number of hidden games stays private.
            if ((p?.hidden ?: 0) > 0 && !io.github.zahakj.tandem.Privacy.enabled) {
                Text("${plural(p!!.hidden, "hidden game")} skipped, never sent anywhere", style = MaterialTheme.typography.bodyMedium, color = Palette.Muted, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(16.dp))
            val wantsSomething = cfg.mode == ScrapeMode.Basics || kinds.isNotEmpty() || (cfg.metadata && cfg.details.isNotEmpty())
            val canStart = (p?.games ?: 0) > 0 && wantsSomething && cfg.active.isNotEmpty()
            FocusCard(
                Modifier.width(210.dp).height(54.dp).remembersFocus(Route.Scrape.key, "start", true),
                glow = Palette.Mint, shape = RoundedCornerShape(27.dp),
                onFocus = { FocusMemory.remember(Route.Scrape.key, "start") },
                onClick = {
                    when {
                        p == null -> Unit
                        p.games == 0 -> Toasts.show("No games to scrape here")
                        !wantsSomething -> Toasts.show("Turn on at least one kind of artwork or detail")
                        cfg.active.isEmpty() -> Toasts.show("Turn on at least one source below")
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
}

@Composable
private fun RegionRow(key: String) {
    val r = ScrapeConfig.settings.regionPref
    fun step(d: Int) = ScrapeConfig.update { it.copy(region = Region.entries[(r.ordinal + d + Region.entries.size) % Region.entries.size].code) }
    OptionRow(key, "Region", r.label, Palette.Lavender, "Which box art and release to prefer", onSide = ::step) { step(1) }
}

@Composable
private fun LanguageRow(key: String) {
    val l = ScrapeConfig.settings.languagePref
    val codes = LANGUAGES.keys.toList()
    fun step(d: Int) = ScrapeConfig.update { it.copy(language = codes[(codes.indexOf(l) + d + codes.size) % codes.size]) }
    OptionRow(key, "Language", LANGUAGES[l] ?: l, Palette.Lavender, "For descriptions and genres (ScreenScraper)", onSide = ::step) { step(1) }
}

/** One Settings row per source, in the user's order: its state, and A opens its setup card. */
fun sourceRows(): List<SettingRow> {
    val cfg = ScrapeConfig.settings
    return cfg.sourceOrder.map { s ->
        val (status, color) = sourceStatus(s, cfg)
        val g = guideFor(s)
        SettingRow(
            s.label, status, color, Glyph.Key,
            "${s.gives}. ${g.account}.",
            opens = true, action = "Set up",
            keywords = "scraper source account key token api ${s.name.lowercase()} " + when (s) {
                ScrapeSource.Igdb -> "twitch client secret"
                ScrapeSource.ScreenScraper -> "screenscraper developer login password user"
                ScrapeSource.SteamGridDb -> "sgdb heroes grids logos"
                ScrapeSource.LaunchBox -> "launchbox games database download offline"
                ScrapeSource.Hltb -> "how long to beat time hours completion"
                ScrapeSource.Libretro -> "thumbnails boxart"
            },
        ) { ArtSetup.open(s) }
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
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 8.dp)) {
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
        Text(text, style = MaterialTheme.typography.bodyMedium)
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

/** A rounded button: a FocusCard with a word on it. */
@Composable
fun PillButton(text: String, color: Color, modifier: Modifier, onClick: () -> Unit) {
    FocusCard(modifier.height(42.dp), glow = color, shape = RoundedCornerShape(21.dp), onClick = onClick) {
        Box(Modifier.fillMaxHeight().background(color.copy(alpha = 0.22f)).padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
            Text(text, style = MaterialTheme.typography.titleSmall, color = color)
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

private val PRESETS = listOf(
    "Box art" to setOf(MediaKind.Box),
    "Box, logo, hero" to setOf(MediaKind.Box, MediaKind.Logo, MediaKind.Hero),
    "Box, logo, hero, screenshot" to setOf(MediaKind.Box, MediaKind.Logo, MediaKind.Hero, MediaKind.Gameplay),
    "All pictures" to (MediaKind.entries.toSet() - MediaKind.Video),
    "All, with videos" to MediaKind.entries.toSet(),
)

/** The last measured size of downloaded media, and what it was measured for. */
private object MediaSizeCache {
    var key: Any? = null
    var value: Pair<Int, Long>? = null
}

/** Settings › Artwork & details. */
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
    val preset = PRESETS.indexOfFirst { it.second == s.kinds }
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
            run {
                fun step(d: Int) = ScrapeConfig.update { it.copy(kinds = PRESETS[((if (preset < 0) 0 else preset) + d + PRESETS.size) % PRESETS.size].second) }
                SettingRow(
                    "Artwork to download", if (preset >= 0) PRESETS[preset].first else "Custom (${s.kinds.size})", Palette.Butter, Glyph.Image,
                    "Which pictures a scrape fetches. More kinds look richer but take longer and use more space.",
                    onLeft = { step(-1) }, onRight = { step(1) }, keywords = "media kinds box logo hero screenshot video",
                ) { step(1) }
            },
            run {
                fun step(d: Int) = ScrapeConfig.update { it.copy(region = Region.entries[(s.regionPref.ordinal + d + Region.entries.size) % Region.entries.size].code) }
                SettingRow(
                    "Preferred region", s.regionPref.label, Palette.Butter, Glyph.Sort,
                    "Box art and names from this region are picked first, when a game has several.",
                    onLeft = { step(-1) }, onRight = { step(1) }, keywords = "usa europe japan country",
                ) { step(1) }
            },
            run {
                val codes = LANGUAGES.keys.toList()
                fun step(d: Int) = ScrapeConfig.update { it.copy(language = codes[(codes.indexOf(s.languagePref) + d + codes.size) % codes.size]) }
                SettingRow(
                    "Preferred language", LANGUAGES[s.languagePref] ?: s.languagePref, Palette.Butter, Glyph.Text,
                    "Descriptions and genres come in this language when the source has it.",
                    onLeft = { step(-1) }, onRight = { step(1) }, keywords = "translation descriptions",
                ) { step(1) }
            },
            SettingRow(
                "Set up sources", "${s.active.size} of ${ScrapeSource.entries.size} on", Palette.Butter, Glyph.Wand,
                "Artwork & details setup: what each source gives, which need an account, where to get a free key, and a Test button for each. Also changes the order they're asked in.",
                opens = true, keywords = "wizard onboarding accounts keys tokens api order priority launchbox igdb twitch steamgriddb screenscraper howlongtobeat",
            ) { ArtSetup.openWizard() },
        ) + sourceRows() + SettingRow(
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
    )
}
