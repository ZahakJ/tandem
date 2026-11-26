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
import androidx.compose.ui.focus.onFocusChanged
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
import io.github.zahakj.tandem.Lang
import io.github.zahakj.tandem.Libraries
import io.github.zahakj.tandem.R
import io.github.zahakj.tandem.Tx
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
            Privacy.isHidden(game) -> Toasts.show(Tx.s(R.string.scrape_hidden_never_sent))
            io.github.zahakj.tandem.NetGate.closed -> Toasts.show(io.github.zahakj.tandem.data.OFFLINE_NOTE)
            Scraper.running -> Toasts.show(Tx.s(R.string.scrape_busy))
            else -> {
                val o = Scraper.optionsFromSettings()
                // The user asked for this game specifically, so refresh what's there (locked fields still stay).
                val opts = o.copy(mode = ScrapeMode.Refresh, kinds = o.kinds.ifEmpty { setOf(MediaKind.Box) }, replaceLocked = false)
                start(ScrapeScope.One(game.key), opts) { Toasts.show(Tx.s(R.string.scrape_looking_for, game.title)) }
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
            if (Scraper.start(scope, o)) started() else Toasts.show(Tx.s(R.string.scrape_cant_start))
        }
        val needs = Scraper.launchBoxNeeds(scope, options)
        if (needs == null) { go(options); return }
        val metered = Scraper.metered()
        Confirm.ask(
            Tx.s(R.string.scrape_lb_ask_title),
            Tx.s(if (metered) R.string.scrape_lb_ask_text_metered else R.string.scrape_lb_ask_text, io.github.zahakj.tandem.data.LaunchBoxDb.APPROX_MB),
            Tx.s(R.string.scrape_lb_ask_yes),
            alt = Tx.s(R.string.scrape_lb_ask_skip),
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
        Toasts.show(Tx.s(if (ScreenRoles.libraryOnBottom) R.string.scrape_background_top else R.string.scrape_background_bottom))
    }
}

fun scopeLabel(scope: ScrapeScope): String = when (scope) {
    ScrapeScope.All -> Tx.s(R.string.scrape_scope_all)
    is ScrapeScope.System -> Libraries.byId(scope.platformId)?.platform?.name ?: scope.platformId
    is ScrapeScope.Folder -> "${Libraries.byId(scope.platformId)?.platform?.shortName ?: scope.platformId} › ${scope.folder}"
    is ScrapeScope.One -> Libraries.games.firstOrNull { it.key == scope.key }?.title ?: Tx.s(R.string.scrape_scope_one)
}

private fun formatBytes(b: Long): String = when {
    b < 1024 * 1024 -> Tx.s(R.string.scrape_size_kb, (b / 1024).toInt())
    b < 1024L * 1024 * 1024 -> Tx.s(R.string.scrape_size_mb, String.format(Lang.locale, "%.1f", b / 1048576.0))
    else -> Tx.s(R.string.scrape_size_gb, String.format(Lang.locale, "%.2f", b / 1073741824.0))
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

/**
 * A switch on the scrape screen: its label, a line on it (two lines when it needs them, never cut), and a switch
 * whose track is lit in the row's colour while it is on, the card tinted with it too.
 */
@Composable
private fun SwitchRow(key: String, label: String, detail: String, on: Boolean, color: Color, onClick: () -> Unit) {
    FocusCard(
        Modifier.fillMaxWidth().remembersFocus(ScrapeRoute, key, false),
        glow = color, shape = RoundedCornerShape(16.dp), lifts = false, onClick = onClick,
    ) {
        Row(
            Modifier.fillMaxWidth().background(color.copy(alpha = if (on) 0.1f else 0f)).padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = Palette.Muted, maxLines = 2)
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

/** One kind of artwork as a tile: a short name, a line on it, and a picture of it from one of your games when there is one. */
private fun kindLabel(k: MediaKind) = when (k) {
    MediaKind.Hero -> Tx.s(R.string.scrape_tile_hero)
    MediaKind.Gameplay -> Tx.s(R.string.scrape_tile_gameplay)
    MediaKind.Video -> Tx.s(R.string.scrape_tile_video)
    MediaKind.Front, MediaKind.Spine -> Tx.s(R.string.scrape_tile_physical)
    else -> k.label
}

/** The highlighted art tile, so the side says what that kind is and where it shows. */
private var focusedKind by mutableStateOf<MediaKind?>(null)

/** Where a kind of artwork shows up in Tandem, for the side next to the tiles. */
private fun kindWhere(k: MediaKind): String = Tx.s(
    when (k) {
        MediaKind.Box -> R.string.scrape_where_box
        MediaKind.Hero -> R.string.scrape_where_hero
        MediaKind.Logo -> R.string.scrape_where_logo
        MediaKind.Title -> R.string.scrape_where_title
        MediaKind.Gameplay -> R.string.scrape_where_gameplay
        MediaKind.Video -> if (Store.settings.look.videoSnaps) R.string.scrape_where_video else R.string.scrape_where_video_off
        MediaKind.Front, MediaKind.Spine -> R.string.scrape_where_physical
    },
)

private fun kindDetail(k: MediaKind): String = Tx.s(
    when (k) {
        MediaKind.Box -> R.string.scrape_tile_box_detail
        MediaKind.Hero -> R.string.scrape_tile_hero_detail
        MediaKind.Logo -> R.string.scrape_tile_logo_detail
        MediaKind.Title -> R.string.scrape_tile_title_detail
        MediaKind.Gameplay -> R.string.scrape_tile_gameplay_detail
        MediaKind.Video -> R.string.scrape_tile_video_detail
        MediaKind.Front, MediaKind.Spine -> R.string.scrape_tile_physical_detail
    },
)

/**
 * A kind of artwork, switched on and off: its picture, its name and two lines on it, with a tick. Nothing is cut:
 * the name is short and the line may take two lines.
 */
@Composable
private fun ArtTile(label: String, detail: String, on: Boolean, color: Color, glyph: Glyph, modifier: Modifier, art: (@Composable () -> Unit)?, onClick: () -> Unit) {
    FocusCard(modifier, glow = color, shape = RoundedCornerShape(16.dp), onClick = onClick) {
        Column(
            Modifier.fillMaxSize()
                .background(Brush.verticalGradient(listOf(color.copy(alpha = if (on) 0.24f else 0.06f), color.copy(alpha = if (on) 0.06f else 0.02f))))
                .padding(8.dp),
        ) {
            Box(Modifier.fillMaxWidth().weight(1f)) {
                Box(
                    Modifier.fillMaxSize().clip(RoundedCornerShape(11.dp)).background(Palette.NightDeep.copy(alpha = 0.35f))
                        .graphicsLayer { alpha = if (on) 1f else 0.45f },
                    contentAlignment = Alignment.Center,
                ) {
                    if (art != null) art() else GlyphChip(glyph, if (on) color else Palette.Muted, size = 40.dp, round = true)
                }
                TickBadge(on, color, Modifier.align(Alignment.TopEnd).padding(5.dp))
            }
            Spacer(Modifier.height(6.dp))
            Text(label, style = MaterialTheme.typography.titleSmall, color = if (on) Palette.Text else Palette.Text.copy(alpha = 0.7f), maxLines = 1)
            Text(detail, Modifier.height(30.dp), style = MaterialTheme.typography.labelSmall, color = Palette.Muted, maxLines = 2)
        }
    }
}

/**
 * A kind of game details as a chip sized to its words, with a tick; [needs] is a small badge for a source it
 * still needs.
 */
@Composable
private fun DetailChip(label: String, on: Boolean, color: Color, needs: String?, modifier: Modifier, onClick: () -> Unit) {
    FocusCard(modifier.height(40.dp), glow = color, shape = RoundedCornerShape(50), lifts = false, onClick = onClick) {
        Row(
            Modifier.fillMaxHeight().background(color.copy(alpha = if (on) 0.2f else 0.06f)).padding(start = 6.dp, end = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TickBadge(on, color, size = 26.dp)
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, color = if (on) Palette.Text else Palette.Text.copy(alpha = 0.7f), maxLines = 1, softWrap = false)
            if (needs != null) {
                Spacer(Modifier.width(8.dp))
                Text(
                    needs, Modifier.clip(RoundedCornerShape(50)).background(Palette.Peach.copy(alpha = 0.2f)).padding(horizontal = 7.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall, color = Palette.Peach, maxLines = 1, softWrap = false,
                )
            }
        }
    }
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
                item("h:what") { SectionTitle(Tx.s(R.string.scrape_setup_what), Palette.Peach) }
                item("scope") {
                    val platform = systemId?.let { Libraries.byId(it)?.platform }
                    PickRow(
                        Tx.s(R.string.scrape_setup_games), Tx.s(if (platform == null) R.string.scrape_setup_games_all else R.string.scrape_setup_games_one), scopeLabel(scope), Palette.Peach,
                        Modifier.remembersFocus(route, "scope", false),
                        glyph = if (platform == null) Glyph.Layers else null,
                        art = platform?.let { p -> { SystemIcon(p, 32.dp) } },
                    ) { picking = ScrapePick.Scope }
                }
                if (groups.isNotEmpty() && systemId != null) item("folder") {
                    PickRow(
                        Tx.s(R.string.scrape_setup_folder), Tx.s(R.string.scrape_setup_folder_detail), (scope as? ScrapeScope.Folder)?.folder ?: Tx.s(R.string.scrape_setup_all_folders), Palette.Peach,
                        Modifier.remembersFocus(route, "folder", false), glyph = Glyph.Folder,
                    ) { picking = ScrapePick.Folder }
                }
                item("mode") {
                    SegmentRow(
                        // What the mode does is said beside the Start button, where the track can't crowd it out.
                        Tx.s(R.string.scrape_setup_mode), null, ScrapeMode.entries.map { it.label }, cfg.mode.ordinal, Palette.Lavender,
                        Modifier.remembersFocus(route, "mode", false), trackWidth = 360.dp,
                    ) { i -> ScrapeConfig.update { it.copy(mode = ScrapeMode.entries[i]) } }
                }
                item("locked") {
                    SwitchRow(
                        "locked", Tx.s(R.string.scrape_setup_locked),
                        Tx.s(if (cfg.replaceLocked) R.string.scrape_setup_locked_on else R.string.scrape_setup_locked_off),
                        cfg.replaceLocked, Palette.Peach,
                    ) { ScrapeConfig.update { it.copy(replaceLocked = !it.replaceLocked) } }
                }
                item("h:art") { SectionTitle(Tx.s(R.string.scrape_setup_artwork), Palette.Mint, Modifier.padding(top = 6.dp)) }
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
                            val detail = if (k == MediaKind.Video && on && (preview?.games ?: 0) > 0) Tx.s(R.string.scrape_tile_video_size, formatBytes(VIDEO_BYTES * (preview?.games ?: 0))) else kindDetail(k)
                            val sample = samples[k]
                            ArtTile(
                                kindLabel(k), detail, on, Palette.Mint, kindGlyph(k),
                                Modifier.weight(1f).height(140.dp).remembersFocus(route, "k:${k.name}", false)
                                    .onFocusChanged { if (it.isFocused) focusedKind = k else if (focusedKind == k) focusedKind = null },
                                art = sample?.let { file -> { AsyncImage(file, null, Modifier.fillMaxSize(), contentScale = kindFit(k)) } },
                            ) { ScrapeConfig.update { it.copy(kinds = if (k in it.kinds) it.kinds - pair else it.kinds + pair) } }
                        }
                        // Keeps the last line's tiles the same width as the others.
                        repeat((4 - shown.size % 4) % 4) { Spacer(Modifier.weight(1f)) }
                    }
                }
                item("h:details") { SectionTitle(Tx.s(R.string.scrape_setup_details), Palette.Sky, Modifier.padding(top = 6.dp)) }
                item("meta") {
                    val canDetails = cfg.active.any { s -> s.details.any { it != MetaType.TimeToBeat } }
                    SwitchRow(
                        "meta", Tx.s(R.string.scrape_setup_game_details),
                        Tx.s(if (canDetails || !cfg.metadata) R.string.scrape_setup_game_details_detail else R.string.scrape_setup_game_details_needs),
                        cfg.metadata, Palette.Sky,
                    ) { ScrapeConfig.update { it.copy(metadata = !it.metadata) } }
                }
                if (cfg.metadata) item("details") {
                    // Chips as wide as their words, as many to a line as fit; each line stretches its chips to the
                    // full width, so the lines end evenly.
                    androidx.compose.foundation.layout.FlowRow(
                        Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        MetaType.entries.forEach { t ->
                            val on = t in cfg.details
                            val needsHltb = t == MetaType.TimeToBeat && ScrapeSource.Hltb !in cfg.active
                            DetailChip(
                                t.label, on, Palette.Sky, if (on && needsHltb) Tx.s(R.string.scrape_setup_needs_hltb) else null,
                                Modifier.weight(1f).remembersFocus(route, "m:${t.name}", false),
                            ) { ScrapeConfig.update { it.copy(details = if (t in it.details) it.details - t else it.details + t) } }
                        }
                    }
                }
                item("h:how") { SectionTitle(Tx.s(R.string.scrape_setup_region_language), Palette.Lavender, Modifier.padding(top = 6.dp)) }
                item("region") {
                    val r = cfg.regionPref
                    SegmentRow(
                        Tx.s(R.string.scrape_setup_region), Tx.s(R.string.scrape_setup_region_detail), Region.entries.map { it.label }, r.ordinal, Palette.Lavender,
                        Modifier.remembersFocus(route, "region", false),
                    ) { i -> ScrapeConfig.update { it.copy(region = Region.entries[i].code) } }
                }
                item("lang") {
                    val l = cfg.languagePref
                    PickRow(
                        Tx.s(R.string.scrape_setup_language), Tx.s(R.string.scrape_setup_language_detail), LANGUAGES[l] ?: l, Palette.Lavender,
                        Modifier.remembersFocus(route, "lang", false), glyph = Glyph.Text,
                    ) { picking = ScrapePick.Language }
                }
                item("h:acc") { SectionTitle(Tx.s(R.string.scrape_setup_sources), Palette.Butter, Modifier.padding(top = 6.dp)) }
                item("sources") { SourcesCard(Modifier.remembersFocus(route, "sources", false)) }
                // Art for the systems themselves (console photos, logos, backdrops), not for the games.
                item("h:systems") { SectionTitle(Tx.s(R.string.scrape_system_art_title), Palette.Peach, Modifier.padding(top = 6.dp)) }
                item("systemart") {
                    PickRow(
                        Tx.s(R.string.look_fetch_art), Tx.s(R.string.scrape_system_art_detail), SystemArtUi.status(), Palette.Peach,
                        Modifier.remembersFocus(route, "systemart", false), glyph = Glyph.Download,
                    ) { if (io.github.zahakj.tandem.data.SystemArtFetch.running) io.github.zahakj.tandem.data.SystemArtFetch.cancel() else SystemArtUi.fetch() }
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
                    p == null -> Tx.s(R.string.scrape_setup_counting)
                    p.games == 0 -> Tx.s(R.string.scrape_setup_none)
                    else -> Tx.p(R.plurals.scrape_setup_to_check, p.games)
                },
                style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center,
            )
            if (p != null && p.games > 0) {
                Text(
                    // Missing details count too: a game with all its art can still lack a description.
                    when {
                        p.noBox == 0 && p.noText == 0 -> Tx.s(R.string.scrape_setup_all_complete)
                        p.noBox == 0 -> Tx.s(R.string.scrape_setup_all_have_box, Tx.p(R.plurals.scrape_setup_no_text, p.noText))
                        p.noText == 0 -> Tx.p(R.plurals.scrape_setup_no_box_yet, p.noBox)
                        else -> Tx.s(R.string.scrape_setup_no_both, Tx.p(R.plurals.scrape_setup_no_box, p.noBox), Tx.p(R.plurals.scrape_setup_no_text, p.noText))
                    },
                    style = MaterialTheme.typography.bodyMedium, color = Palette.Muted, textAlign = TextAlign.Center,
                )
            }
            // In private mode even the number of hidden games stays private.
            if ((p?.hidden ?: 0) > 0 && !io.github.zahakj.tandem.Privacy.enabled) {
                Text(Tx.p(R.plurals.scrape_setup_hidden_skipped, p!!.hidden), style = MaterialTheme.typography.bodyMedium, color = Palette.Muted, textAlign = TextAlign.Center)
            }
            // What the highlighted kind of art is and where it shows; otherwise what the chosen mode will do (the Mode
            // row has room only for its three choices).
            Spacer(Modifier.height(8.dp))
            val where = focusedKind
            Text(
                if (where != null) kindWhere(where) else Tx.s(R.string.scrape_mode_line, cfg.mode.label, cfg.mode.detail.replaceFirstChar { it.lowercase(Lang.locale) }),
                Modifier.clip(RoundedCornerShape(14.dp)).background((if (where != null) Palette.Mint else Palette.Lavender).copy(alpha = 0.12f)).padding(horizontal = 12.dp, vertical = 6.dp),
                style = MaterialTheme.typography.bodySmall, color = if (where != null) Palette.Mint else Palette.Lavender, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            val wantsSomething = cfg.mode == ScrapeMode.Basics || kinds.isNotEmpty() || (cfg.metadata && cfg.details.isNotEmpty())
            val canStart = (p?.games ?: 0) > 0 && wantsSomething && cfg.active.isNotEmpty()
            FocusCard(
                Modifier.width(210.dp).height(54.dp).remembersFocus(route, "start", true),
                glow = Palette.Mint, shape = RoundedCornerShape(27.dp),
                onClick = {
                    when {
                        p == null -> Unit
                        p.games == 0 -> Toasts.show(Tx.s(R.string.scrape_setup_none))
                        !wantsSomething -> Toasts.show(Tx.s(R.string.scrape_setup_want_something))
                        cfg.active.isEmpty() -> Toasts.show(Tx.s(R.string.scrape_setup_want_source))
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
                    Text(Tx.s(R.string.scrape_setup_start), style = MaterialTheme.typography.titleLarge, color = if (canStart) Palette.NightDeep else Palette.Muted)
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                Tx.s(if (ScrapeUi.hasCard) R.string.scrape_setup_leave_card else R.string.scrape_setup_leave),
                style = MaterialTheme.typography.bodyMedium, color = Palette.Muted, textAlign = TextAlign.Center)
        }
    }

    // The grids of options the rows above open.
    val scopeChoices = remember(withGames, Lang.revision.intValue) {
        listOf(PickOption<ScrapeScope>(ScrapeScope.All, Tx.s(R.string.scrape_scope_all), Tx.p(R.plurals.scrape_games, withGames.sumOf { it.games.size }), art = { GlyphChip(Glyph.Layers, Palette.Peach, size = 52.dp, round = true) })) +
            withGames.map { lib -> PickOption<ScrapeScope>(ScrapeScope.System(lib.platform.id), lib.platform.name, Tx.p(R.plurals.scrape_games, lib.games.size), art = { SystemIcon(lib.platform, 56.dp) }) }
    }
    ChoicePopup(
        picking == ScrapePick.Scope, Tx.s(R.string.scrape_pick_scope_title), Tx.s(R.string.scrape_pick_scope_detail), scopeChoices,
        if (scope is ScrapeScope.Folder) ScrapeScope.System(scope.platformId) else scope, Palette.Peach, "scrape:pick:scope",
        onClose = { picking = null; Nav.focusTick++ },
    ) { ScrapeUi.scope = it }
    if (systemId != null) {
        val folderChoices = remember(systemId, groups, Lang.revision.intValue) {
            listOf(PickOption<String?>(null, Tx.s(R.string.scrape_setup_all_folders), glyph = Glyph.Layers)) + groups.map { PickOption<String?>(it, it, glyph = Glyph.Folder) }
        }
        ChoicePopup(
            picking == ScrapePick.Folder, Tx.s(R.string.scrape_pick_folder_title), scopeLabel(ScrapeScope.System(systemId)), folderChoices,
            (scope as? ScrapeScope.Folder)?.folder, Palette.Peach, "scrape:pick:folder",
            onClose = { picking = null; Nav.focusTick++ },
        ) { f -> ScrapeUi.scope = if (f == null) ScrapeScope.System(systemId) else ScrapeScope.Folder(systemId, f) }
    }
    ChoicePopup(
        picking == ScrapePick.Language, Tx.s(R.string.scrape_setup_language), Tx.s(R.string.scrape_pick_language_detail),
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
                    Tx.s(R.string.scrape_sources_order),
                    Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = Palette.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(10.dp))
                Box(
                    Modifier.height(30.dp).clip(RoundedCornerShape(50)).background(Palette.Butter).padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(Tx.s(R.string.scrape_sources_set_up), style = MaterialTheme.typography.labelLarge, color = Palette.OnAccent, maxLines = 1) }
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
                    if (prep != null) Tx.s(R.string.scrape_run_lb_ready, (prep * 100).toInt()) else Tx.p(R.plurals.scrape_run_done_of, run.total, run.done, run.total),
                    Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (run.bytes > 0) Text(formatBytes(run.bytes), style = MaterialTheme.typography.titleSmall, color = Palette.Muted)
            }
            if (!run.finished && run.current.isNotEmpty()) {
                Text(run.current, style = MaterialTheme.typography.labelLarge, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Stat(Palette.Mint, Tx.p(R.plurals.scrape_run_got_art, run.withArt))
            if (run.options.types.isNotEmpty()) Stat(Palette.Sky, Tx.p(R.plurals.scrape_run_got_details, run.withDetails))
            Stat(Palette.Lavender, Tx.p(R.plurals.scrape_run_complete, run.upToDate))
            if (run.notFound > 0) Stat(Palette.Peach, Tx.p(R.plurals.scrape_run_not_found, run.notFound))
            if (run.failed > 0) Stat(Palette.Butter, Tx.p(R.plurals.scrape_run_failed, run.failed))
            runFooter(run)?.let { Text(it, style = MaterialTheme.typography.labelLarge, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            // At most two notes, one line each, so the buttons below always fit.
            run.notes.take(2).forEach { Text("• $it", style = MaterialTheme.typography.labelLarge, color = Palette.Peach, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            Spacer(Modifier.weight(1f))
            // When the run ends, Stop goes away and Done takes the focus.
            val target = rememberFocusTarget(routeKey, if (!run.finished) listOf("stop", "pause") else listOfNotNull("done", "new", "missed".takeIf { run.missedCount > 0 }))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 8.dp)) {
                if (!run.finished) {
                    PillButton(Tx.s(R.string.scrape_run_stop), Palette.Peach, Modifier.takesFocus(routeKey, "stop", target == "stop")) { Scraper.cancel() }
                    PillButton(Tx.s(if (run.held) R.string.scrape_run_resume else R.string.scrape_run_pause), Palette.Butter, Modifier.takesFocus(routeKey, "pause", target == "pause")) { Scraper.togglePause() }
                    if (ScrapeUi.hasCard) PillButton(Tx.s(R.string.scrape_run_keep_browsing), Palette.Mint, Modifier.takesFocus(routeKey, "hide", target == "hide")) { Nav.back() }
                } else {
                    PillButton(Tx.s(R.string.scrape_run_done), Palette.Mint, Modifier.takesFocus(routeKey, "done", target == "done")) { Scraper.dismiss(); Nav.back() }
                    PillButton(Tx.s(R.string.scrape_run_new), Palette.Sky, Modifier.takesFocus(routeKey, "new", target == "new")) { Scraper.dismiss() }
                    if (run.missedCount > 0) {
                        PillButton(Tx.s(if (showMissed) R.string.scrape_run_covers else R.string.scrape_run_missed), Palette.Peach, Modifier.takesFocus(routeKey, "missed", target == "missed")) { showMissed = !showMissed }
                    }
                }
            }
        }
        Spacer(Modifier.width(18.dp))
        Column(Modifier.weight(1f).fillMaxHeight()) {
            if (showMissed && run.missed.isNotEmpty()) {
                SectionTitle(Tx.s(R.string.scrape_run_missed_title), Palette.Peach, Modifier.padding(top = 6.dp))
                MissedList(run)
            } else {
                SectionTitle(Tx.s(if (run.finished) R.string.scrape_run_new_artwork else R.string.scrape_run_just_found), Palette.Mint, Modifier.padding(top = 6.dp))
                if (run.recent.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            Tx.s(if (run.finished) R.string.scrape_run_nothing_new else R.string.scrape_run_covers_soon),
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
private fun runTitle(run: ScrapeRun): String = Tx.s(
    when (run.state) {
        RunState.Running -> if (run.prep != null || run.done == 0) R.string.scrape_run_getting_ready else R.string.scrape_run_finding
        RunState.Paused -> if (run.held) R.string.scrape_run_paused else R.string.scrape_run_paused_play
        RunState.Done -> R.string.scrape_run_all_done
        RunState.Cancelled -> R.string.scrape_run_stopped
    },
)

/** "LaunchBox · about 3 min left · 12.4 MB" while running; null when there's nothing to say. */
private fun runFooter(run: ScrapeRun): String? {
    if (run.finished) return null
    val eta = run.eta?.let { s ->
        if (s < 60) Tx.s(R.string.scrape_run_eta_under_minute) else if (s < 120) Tx.s(R.string.scrape_run_eta_minute)
        else Tx.p(R.plurals.scrape_run_eta_minutes, (s / 60).toInt())
    }
    return listOfNotNull(run.source.ifEmpty { null }, eta, run.bytes.takeIf { it > 0 }?.let(::formatBytes)).joinParts().ifEmpty { null }
}

/** "New art for 12 games · details for 30 · 8.1 MB", or that nothing changed. */
private fun runSummary(run: ScrapeRun): String = listOfNotNull(
    run.withArt.takeIf { it > 0 }?.let { Tx.p(R.plurals.scrape_run_summary_art, it) },
    run.withDetails.takeIf { it > 0 }?.let { Tx.p(if (run.withArt > 0) R.plurals.scrape_run_summary_details_too else R.plurals.scrape_run_summary_details, it) },
    run.bytes.takeIf { it > 0 }?.let(::formatBytes),
).joinParts().replaceFirstChar { it.uppercase(Lang.locale) }.ifEmpty { Tx.s(R.string.scrape_run_nothing_new) }

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
            // From the start side: the right in right to left.
            if (w > 0f) mirroredInRtl { drawRoundRect(Brush.horizontalGradient(colors, endX = w), size = Size(w, size.height), cornerRadius = CornerRadius(size.height / 2)) }
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
            else FitText(text, MaterialTheme.typography.titleSmall, color = ink)
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
    RunState.Paused -> Tx.s(R.string.scrape_pill_paused)
    else -> if (run.total == 1) Tx.s(R.string.scrape_pill_one) else Tx.s(R.string.scrape_pill_count, run.done, run.total)
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
                        prep != null -> Tx.s(R.string.scrape_run_lb_ready, (prep * 100).toInt())
                        !run.finished && run.current.isNotEmpty() -> run.current
                        latest != null -> Tx.s(R.string.scrape_card_last_found, latest.title)
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
                Count(Palette.Mint, Tx.p(R.plurals.scrape_card_found, run.withArt))
                if (run.options.types.isNotEmpty()) Count(Palette.Sky, Tx.p(R.plurals.scrape_card_details, run.withDetails))
                Count(Palette.Peach, Tx.p(R.plurals.scrape_card_missing, run.notFound))
                if (run.failed > 0) Count(Palette.Butter, Tx.p(R.plurals.scrape_card_failed, run.failed))
                Count(Palette.Lavender, Tx.p(R.plurals.scrape_card_complete, run.upToDate))
            }
            if (!run.finished) {
                CardButton(Tx.s(if (run.held) R.string.scrape_run_resume else R.string.scrape_run_pause), Palette.Butter) { Scraper.togglePause() }
                CardButton(Tx.s(R.string.scrape_card_cancel), Palette.Peach) { Scraper.cancel() }
            } else {
                CardButton(Tx.s(R.string.common_close), Palette.Mint) { ScrapeCardState.closed = run }
            }
        }
        // Why a source was skipped, say; one line so the card keeps its size.
        run.notes.firstOrNull()?.let { Text("• $it", style = MaterialTheme.typography.labelMedium, color = Palette.Peach, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
}

@Composable
private fun Count(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(5.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, maxLines = 1)
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
            val title = Libraries.games.firstOrNull { it.key == one.key }?.title ?: Tx.s(R.string.scrape_one_that_game)
            Toasts.show(
                when {
                    r.withArt > 0 && r.withDetails > 0 -> Tx.s(R.string.scrape_one_art_details, title)
                    r.withArt > 0 -> Tx.s(R.string.scrape_one_art, title)
                    r.withDetails > 0 -> Tx.s(R.string.scrape_one_details, title)
                    r.notes.isNotEmpty() -> r.notes.first()
                    else -> Tx.s(R.string.scrape_one_nothing, title)
                },
            )
            if (Nav.route != Route.Scrape) Scraper.dismiss()
        } else if (Nav.route != Route.Scrape && !ScrapeUi.hasCard) {
            // With a second screen, the card there shows the summary instead.
            Toasts.show(Tx.p(R.plurals.scrape_done_toast, r.withArt))
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
    return SettingSection(en = "Artwork & details",
        title = Tx.s(R.string.scrape_settings_title),
        rows = listOf(
            SettingRow(
                Tx.s(R.string.scrape_settings_scrape),
                Scraper.run?.takeIf { !it.finished }?.let { "${it.done}/${it.total}" } ?: Tx.s(R.string.scrape_settings_scrape_value),
                Palette.Butter, Glyph.Download,
                Tx.s(R.string.scrape_settings_scrape_help),
                opens = true, keywords = "scraper download artwork media metadata covers boxart", en = "Scrape library",
            ) { ScrapeUi.open() },
            SettingRow(
                Tx.s(R.string.scrape_sources_set_up), Tx.s(R.string.scrape_settings_sources_value, s.active.size, ScrapeSource.entries.size), Palette.Butter, Glyph.Wand,
                Tx.s(R.string.scrape_settings_sources_help),
                opens = true, keywords = "wizard onboarding accounts keys tokens api order priority launchbox igdb twitch steamgriddb screenscraper howlongtobeat",
                en = "Set up sources",
            ) { ArtSetup.openWizard() },
            SettingRow(
                Tx.s(R.string.scrape_settings_clear),
                size?.let { (n, b) -> if (n == 0) Tx.s(R.string.scrape_settings_clear_none) else Tx.p(R.plurals.scrape_settings_clear_files, n, n, formatBytes(b)) } ?: "…",
                Palette.Peach, Glyph.Restore,
                Tx.s(R.string.scrape_settings_clear_help),
                keywords = "delete space storage free", en = "Clear downloaded media",
            ) {
                when {
                    Scraper.running -> Toasts.show(Tx.s(R.string.scrape_settings_wait))
                    size?.first == 0 -> Toasts.show(Tx.s(R.string.scrape_settings_nothing))
                    else -> GameEditor.confirmClear(size)
                }
            },
        ),
    )
}
