package io.github.zahakj.tandem.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.zahakj.tandem.Button
import io.github.zahakj.tandem.data.LaunchBoxDb
import io.github.zahakj.tandem.data.ScrapeConfig
import io.github.zahakj.tandem.data.ScrapeSettings
import io.github.zahakj.tandem.data.ScrapeSource
import io.github.zahakj.tandem.data.SourceTests
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/*
 * "Artwork & details setup": every scraping source explained in plain words (what it gives, whether it needs an
 * account, exactly where to get a key), with fields for the keys and a Test button that does one real lookup.
 * The list of sources is a popup of tiles in the order they are asked, opened from Settings › Library & art › Set up
 * sources; the welcome shows the same tiles. A on a source opens its card; L1 and R1 move the highlighted source
 * earlier or later, so the D-pad stays free to move around the grid. Nothing here touches the network until the
 * user presses Test or Download.
 */

object ArtSetup {
    /** The list of sources is open. */
    var wizard by mutableStateOf(false)
    /** The source whose card is open, on top of whatever is showing. */
    var sheet by mutableStateOf<ScrapeSource?>(null)
    /** The source highlighted in the list: the one L1 and R1 move. */
    internal var highlighted: ScrapeSource? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun openWizard() { FocusMemory.forget(WIZARD); highlighted = null; wizard = true }
    fun open(s: ScrapeSource) { FocusMemory.forget(SHEET); sheet = s }
    fun close() { if (sheet != null) sheet = null else wizard = false; Nav.focusTick++ }

    /** Moves the highlighted source one place earlier (-1) or later (1); at either end it stays put. */
    internal fun moveHighlighted(by: Int) {
        val s = highlighted ?: return
        val order = ScrapeConfig.settings.sourceOrder
        if (order.indexOf(s) + by !in order.indices) return
        ScrapeConfig.update { it.moved(s, by) }
        // The highlight follows the source to its new place.
        FocusMemory.remember(WIZARD, s.name)
        Nav.focusTick++
        Sounds.move()
    }

    /** Asks, then downloads the LaunchBox database in the background for every system the user has. */
    fun downloadLaunchBox(ctx: Context) {
        if (LaunchBoxDb.progress != null) { Toasts.show("Already downloading"); return }
        val platforms = LaunchBoxDb.wantedPlatforms()
        if (platforms.isEmpty()) { Toasts.show("None of your systems are in LaunchBox's database"); return }
        val metered = LaunchBoxDb.metered(ctx)
        Confirm.ask(
            "Download LaunchBox's database?",
            "About ${LaunchBoxDb.APPROX_MB} MB" + (if (metered) ", and you're on a metered connection: Wi-Fi is better" else "") +
                ". Tandem keeps only your ${platforms.size} systems and looks games up on this device.",
            "Download",
        ) {
            Toasts.show("Downloading LaunchBox's database…")
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    runCatching { LaunchBoxDb.download(platforms) }.onFailure { android.util.Log.w("Tandem", "LaunchBox", it) }.isSuccess
                }
                SourceTests.forget(ScrapeSource.LaunchBox)
                Toasts.show(if (ok) "LaunchBox is ready: ${"%,d".format(LaunchBoxDb.info?.games ?: 0)} games ✨" else "Couldn't download LaunchBox's database. Try again on Wi-Fi")
            }
        }
    }
}

private const val WIZARD = "artsetup"
private const val SHEET = "artsetup:sheet"

/** How to set a source up, in plain words. [numbered] steps are done in order; otherwise they are notes. */
class SourceGuide(val account: String, val steps: List<String>, val sends: String, val numbered: Boolean = true)

fun guideFor(s: ScrapeSource): SourceGuide = when (s) {
    ScrapeSource.LaunchBox -> SourceGuide(
        "No account needed",
        listOf(
            "Tandem downloads LaunchBox's free games database once (about ${LaunchBoxDb.APPROX_MB} MB, Wi-Fi recommended) and keeps just your systems.",
            "Games are looked up on this device. It refreshes once a month, when you scrape on Wi-Fi.",
        ),
        "Nothing about your games. Pictures come from images.launchbox-app.com.",
        numbered = false,
    )
    ScrapeSource.ScreenScraper -> SourceGuide(
        "Free account + developer ID",
        listOf(
            "Make a free account at screenscraper.fr.",
            "Ask for API access on their forum (\"Demande d'accès à l'API\") to get a developer ID and password. Tandem can't ship one.",
            "Enter both below. Your own login is optional but gives you a bigger daily quota.",
        ),
        "Your login, the developer ID, and each game's file name, size, system and checksum.",
    )
    ScrapeSource.Igdb -> SourceGuide(
        "Free Twitch account",
        listOf(
            "Sign in at dev.twitch.tv/console/apps (Twitch asks for two-factor sign-in first).",
            "Register Your Application: any name, redirect URL http://localhost, category Application Integration, client type Confidential.",
            "Copy the Client ID, press New Secret, and copy the Client Secret.",
        ),
        "Your client ID and secret (to Twitch) and each game's title.",
    )
    ScrapeSource.SteamGridDb -> SourceGuide(
        "Free account",
        listOf(
            "Sign in at steamgriddb.com (with your Steam account).",
            "Open steamgriddb.com/profile/preferences/api.",
            "Generate an API key and enter it below.",
        ),
        "Your API key and each game's title.",
    )
    ScrapeSource.Libretro -> SourceGuide(
        "No account needed",
        listOf("Always ready. Great for classic systems; it has nothing for Switch, Wii U or PC games."),
        "Picture names made from each game's file name or title.",
        numbered = false,
    )
    ScrapeSource.Hltb -> SourceGuide(
        "No account needed",
        listOf(
            "Uses the same search as the howlongtobeat.com website. It isn't an official API, so it can stop working when the site changes; scraping then just skips it.",
            "Times show on the bottom screen.",
        ),
        "Each game's title.",
        numbered = false,
    )
}

/** A source's state in two or three words, and its colour. */
fun sourceStatus(s: ScrapeSource, cfg: ScrapeSettings = ScrapeConfig.settings): Pair<String, Color> {
    val test = SourceTests.results[s]
    return when {
        SourceTests.running[s] == true -> "Testing…" to Palette.Sky
        s == ScrapeSource.LaunchBox && LaunchBoxDb.progress != null -> "Downloading ${((LaunchBoxDb.progress ?: 0f) * 100).toInt()}%" to Palette.Sky
        !cfg.isOn(s) -> "Off" to Palette.Muted
        !cfg.configured(s) -> "Needs a key" to Palette.Peach
        s == ScrapeSource.LaunchBox && LaunchBoxDb.info == null -> "Not downloaded" to Palette.Butter
        test != null && !test.ok -> "✗ Test failed" to Palette.Peach
        test != null -> "✓ Works" to Palette.Mint
        else -> "Ready" to Palette.Mint
    }
}

/** "Free key", "No account": what a source needs, shortest form. */
private fun needs(s: ScrapeSource) = when (s) {
    ScrapeSource.LaunchBox, ScrapeSource.Libretro, ScrapeSource.Hltb -> "No account"
    ScrapeSource.ScreenScraper -> "Account + dev ID"
    ScrapeSource.Igdb -> "Free Twitch key"
    ScrapeSource.SteamGridDb -> "Free key"
}

/** What a source gives, short enough for two lines of a tile (the whole of it is on its card). */
private fun tagline(s: ScrapeSource) = when (s) {
    ScrapeSource.ScreenScraper -> "Matches the file itself: art, videos and details"
    ScrapeSource.LaunchBox -> "Box art, logos, fanart and details, kept on this device"
    ScrapeSource.Igdb -> "Covers, artwork and details, great for newer games"
    ScrapeSource.SteamGridDb -> "Community covers, heroes and logos for PC and Switch"
    ScrapeSource.Libretro -> "Real box scans and title screens for classic systems"
    ScrapeSource.Hltb -> "How long a game takes: story, extras and 100%"
}

/** Each source's icon, the same on its tile and its card. */
private fun sourceGlyph(s: ScrapeSource) = when (s) {
    ScrapeSource.ScreenScraper -> Glyph.Cartridge
    ScrapeSource.LaunchBox -> Glyph.Layers
    ScrapeSource.Igdb -> Glyph.Gamepad
    ScrapeSource.SteamGridDb -> Glyph.Grid
    ScrapeSource.Libretro -> Glyph.Image
    ScrapeSource.Hltb -> Glyph.Clock
}

/** Each source's own colour, for its icon. */
private fun sourceColor(s: ScrapeSource): Color = when (s) {
    ScrapeSource.ScreenScraper -> Palette.Sky
    ScrapeSource.LaunchBox -> Palette.Butter
    ScrapeSource.Igdb -> Palette.Lavender
    ScrapeSource.SteamGridDb -> Palette.Mint
    ScrapeSource.Libretro -> Palette.Peach
    ScrapeSource.Hltb -> Palette.Accent
}

/** A source's round icon chip, grey while it is off. */
@Composable
private fun SourceChip(s: ScrapeSource, size: Dp, on: Boolean) =
    GlyphChip(sourceGlyph(s), if (on) sourceColor(s) else Palette.Muted, size = size, round = true)

private val TileGap = 12.dp

/**
 * One source as a tile, tinted by its state: its icon, name, what it needs, two lines on what it gives (left out when [compact]), and its state. A opens its setup card.
 */
@Composable
fun SourceTile(s: ScrapeSource, modifier: Modifier, compact: Boolean = false, onFocus: () -> Unit = {}) {
    val (status, color) = sourceStatus(s)
    val on = ScrapeConfig.settings.isOn(s)
    FocusCard(modifier, glow = color, shape = RoundedCornerShape(18.dp), onFocus = onFocus, onClick = { ArtSetup.open(s) }) {
        Column(
            Modifier.fillMaxSize()
                .background(Brush.verticalGradient(listOf(color.copy(alpha = 0.2f), color.copy(alpha = 0.04f))))
                .padding(12.dp)
                .graphicsLayer { alpha = if (on) 1f else 0.7f },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SourceChip(s, 38.dp, on)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    // The whole name, always: a long one shrinks a little instead of being cut.
                    ShrinkToFit(Modifier.fillMaxWidth(), alignment = Alignment.CenterStart) {
                        Text(s.label, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                    }
                    Text(needs(s), style = MaterialTheme.typography.labelMedium, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (!compact) {
                Spacer(Modifier.height(8.dp))
                Text(
                    tagline(s), style = MaterialTheme.typography.bodyMedium, color = Palette.Text.copy(alpha = 0.85f),
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.weight(1f))
            Pill(status, color)
        }
    }
}

/**
 * [sources] as a grid of [SourceTile]s, in their order, [columns] across, each [tileHeight] tall. [onFocus] hears
 * which one is highlighted.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SourceGrid(
    sources: List<ScrapeSource>, columns: Int, tileHeight: Dp, route: String, target: String?,
    compact: Boolean = false, onFocus: (ScrapeSource) -> Unit = {},
) {
    // Tiles by place, not by source: keyed tiles kept their old places in the FlowRow when the order changed. A moved
    // source takes the highlight to its new place through [target] instead.
    FlowRow(
        Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TileGap),
        verticalArrangement = Arrangement.spacedBy(TileGap), maxItemsInEachRow = columns,
    ) {
        sources.forEach { s ->
            SourceTile(
                s, Modifier.weight(1f).height(tileHeight).takesFocus(route, s.name, target == s.name),
                compact, onFocus = { onFocus(s) },
            )
        }
    }
}

/** What the list needs besides its tiles: the panel's padding, the header, the hints and the gaps between them. */
private val ListChrome = 140.dp

/** The popups: the list of sources (from Settings) and one source's card (from anywhere). */
@Composable
fun ArtSetupOverlay() {
    InterceptButtons(ArtSetup.wizard || ArtSetup.sheet != null) { b ->
        // L1 and R1 move the highlighted source in the list; the D-pad moves around the grid.
        if (ArtSetup.sheet == null && (b == Button.L1 || b == Button.R1)) ArtSetup.moveHighlighted(if (b == Button.R1) 1 else -1)
        true
    }
    OverlayPanel(ArtSetup.wizard, onClose = { ArtSetup.wizard = false; Nav.focusTick++ }) {
        BoxWithConstraints(contentAlignment = Alignment.Center) {
            val order = ScrapeConfig.settings.sourceOrder
            val target = rememberFocusTarget(WIZARD, order.map { it.name })
            // Three across on the top screen, two on a narrow one; the tiles share the height that is left, and drop
            // their two lines on what a source gives when it is too little.
            val columns = if (maxWidth >= 600.dp) 3 else 2
            val rows = (order.size + columns - 1) / columns
            val tileHeight = ((maxHeight - ListChrome - TileGap * (rows - 1)) / rows).coerceIn(92.dp, 140.dp)
            PanelSurface(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Pip(44.dp, mood = 1)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Artwork & details setup", style = MaterialTheme.typography.titleLarge, maxLines = 1)
                        Text(
                            "Scraping asks these left to right, each only for what's still missing. LaunchBox and libretro need no account.",
                            style = MaterialTheme.typography.bodySmall, color = Palette.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                SourceGrid(order, columns, tileHeight, WIZARD, target, compact = tileHeight < 124.dp) { ArtSetup.highlighted = it }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    ButtonHint("A", "Set up", Palette.Mint)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        ButtonHint("L1", "", Palette.Lavender)
                        ButtonHint("R1", "Ask earlier / later", Palette.Lavender)
                    }
                    ButtonHint("B", "Done", Palette.Lavender)
                }
            }
        }
    }
    val sheet = ArtSetup.sheet
    val shown = rememberLatest(sheet)
    OverlayPanel(sheet != null, onClose = { ArtSetup.sheet = null; Nav.focusTick++ }) {
        val s = shown ?: return@OverlayPanel
        BoxWithConstraints(contentAlignment = Alignment.Center) { SourceSheet(s, maxWidth, maxHeight) }
    }
}

/** A button on a source card. */
private data class SheetButton(val key: String, val label: String, val color: Color, val onClick: () -> Unit)

@Composable
private fun SourceSheet(s: ScrapeSource, maxWidth: Dp, maxHeight: Dp) {
    val ctx = LocalContext.current
    val cfg = ScrapeConfig.settings
    val g = guideFor(s)
    val (status, color) = sourceStatus(s)
    val test = SourceTests.results[s]

    fun keyChanged() = SourceTests.forget(s)
    val keys: List<KeyField> = when (s) {
        ScrapeSource.ScreenScraper -> listOf(
            KeyField("devid", "Developer ID", cfg.ssDevId) {
                TextEntry.ask("ScreenScraper developer ID", cfg.ssDevId) { v -> ScrapeConfig.update { it.copy(ssDevId = v.trim()) }; keyChanged() }
            },
            KeyField("devpw", "Dev password", cfg.ssDevPassword, secret = true) {
                TextEntry.ask("ScreenScraper developer password", "", secret = true) { v -> ScrapeConfig.update { it.copy(ssDevPassword = v) }; keyChanged() }
            },
            KeyField("user", "Your login", cfg.ssUser, optional = true) {
                TextEntry.ask("Your ScreenScraper username (optional)", cfg.ssUser) { v -> ScrapeConfig.update { it.copy(ssUser = v.trim(), ssVerifiedUser = "") }; keyChanged() }
            },
            KeyField("pw", "Your password", cfg.ssPassword, secret = true, optional = true) {
                TextEntry.ask("Your ScreenScraper password", "", secret = true) { v -> ScrapeConfig.update { it.copy(ssPassword = v, ssVerifiedUser = "") }; keyChanged() }
            },
        )
        ScrapeSource.Igdb -> listOf(
            KeyField("id", "Client ID", cfg.igdbClientId) {
                TextEntry.ask("Twitch Client ID (dev.twitch.tv/console/apps)", cfg.igdbClientId) { v -> ScrapeConfig.update { it.copy(igdbClientId = v.trim()) }; keyChanged() }
            },
            KeyField("secret", "Client secret", cfg.igdbSecret, secret = true) {
                TextEntry.ask("Twitch Client Secret", "", secret = true) { v -> ScrapeConfig.update { it.copy(igdbSecret = v.trim()) }; keyChanged() }
            },
        )
        ScrapeSource.SteamGridDb -> listOf(
            KeyField("key", "API key", cfg.sgdbKey, secret = true) {
                TextEntry.ask("SteamGridDB API key (steamgriddb.com › Preferences › API)", "", secret = true) { v -> ScrapeConfig.update { it.copy(sgdbKey = v.trim()) }; keyChanged() }
            },
        )
        else -> emptyList()
    }
    val on = cfg.isOn(s)
    val notDownloaded = s == ScrapeSource.LaunchBox && LaunchBoxDb.info == null && LaunchBoxDb.progress == null
    val actions = buildList {
        add(SheetButton("toggle", if (on) "Turn off" else "Turn on", if (on) Palette.Lavender else Palette.Mint) {
            ScrapeConfig.update { it.copy(off = if (on) it.off + s else it.off - s) }
            Sounds.toggle(!on)
        })
        if (s == ScrapeSource.LaunchBox) {
            add(SheetButton("download", if (LaunchBoxDb.info == null) "Download (${LaunchBoxDb.APPROX_MB} MB)" else "Download again", Palette.Butter) {
                ArtSetup.downloadLaunchBox(ctx)
            })
        }
        add(SheetButton("test", if (SourceTests.running[s] == true) "Testing…" else "Test", Palette.Sky) { SourceTests.run(ctx, s) })
        add(SheetButton("done", "Done", Palette.Lavender) { ArtSetup.sheet = null; Nav.focusTick++ })
    }
    // The button that does the obvious next thing, filled: Turn on for a source that is off, Download for LaunchBox
    // before its database is here, Test once the keys are in, and Done while they aren't.
    val primary = when {
        !on -> "toggle"
        notDownloaded -> "download"
        cfg.configured(s) -> "test"
        else -> "done"
    }
    val keysKeys = keys.map { it.key }
    val actionKeys = actions.map { it.key }
    // A source that still needs its keys opens on the first key; otherwise on the filled button.
    val target = rememberFocusTarget(
        SHEET,
        if (!cfg.configured(s)) keysKeys + actionKeys else listOf(primary) + actionKeys.filter { it != primary } + keysKeys,
    )
    val wide = maxWidth >= 600.dp

    PanelSurface(Modifier.fillMaxWidth().heightIn(max = maxHeight)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SourceChip(s, 50.dp, on)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(s.label, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.width(10.dp))
                    Pill(status, color)
                }
                Text(tagline(s), style = MaterialTheme.typography.bodyMedium, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.height(14.dp))
        // How to set it up beside what it gives and sends; scrolls if a small screen can't fit it, while the fields
        // and buttons below always show.
        val scroll = rememberScrollState()
        Column(Modifier.weight(1f, fill = false).fadingEdges(scroll).verticalScroll(scroll)) {
            if (wide) {
                Row {
                    HowTo(g, Modifier.weight(1.3f))
                    Spacer(Modifier.width(16.dp))
                    Facts(s, g, Modifier.weight(1f))
                }
            } else {
                HowTo(g, Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                Facts(s, g, Modifier.fillMaxWidth())
            }
        }
        if (test != null) {
            Spacer(Modifier.height(10.dp))
            val tone = if (test.ok) Palette.Mint else Palette.Peach
            Row(
                Modifier.fillMaxWidth().background(tone.copy(alpha = 0.14f), RoundedCornerShape(14.dp)).padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Pip(26.dp, mood = if (test.ok) 1 else 3, bounce = false)
                Spacer(Modifier.width(10.dp))
                Text(
                    (if (test.ok) "✓ " else "✗ ") + test.message, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall,
                    color = tone, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        // The keys as labelled fields across the card, then the buttons sharing one row in equal widths.
        if (keys.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().focusRow(), horizontalArrangement = Arrangement.spacedBy(SheetGap)) {
                keys.forEach { k -> KeyFieldCard(k, Modifier.weight(1f).takesFocus(SHEET, k.key, target == k.key)) }
            }
            Spacer(Modifier.height(SheetGap))
        }
        Row(Modifier.fillMaxWidth().focusRow(), horizontalArrangement = Arrangement.spacedBy(SheetGap)) {
            actions.forEach { b ->
                PillButton(
                    b.label, b.color, Modifier.weight(1f).takesFocus(SHEET, b.key, target == b.key),
                    fill = true, filled = b.key == primary, onClick = b.onClick,
                )
            }
        }
    }
}

private val SheetGap = 10.dp

/** Whether it needs an account, as a coloured tag, then the steps in number bubbles (or the notes, with dots). */
@Composable
private fun HowTo(g: SourceGuide, modifier: Modifier) {
    val free = g.account.startsWith("No account")
    val tone = if (free) Palette.Mint else Palette.Butter
    Column(modifier) {
        Row(
            Modifier.background(tone.copy(alpha = 0.16f), RoundedCornerShape(50)).padding(start = 9.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlyphIcon(if (free) Glyph.Sparkle else Glyph.Key, tone, Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text(g.account, style = MaterialTheme.typography.labelLarge, color = tone, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            g.steps.forEachIndexed { i, step ->
                Row {
                    Box(Modifier.size(20.dp).background(tone.copy(alpha = 0.2f), CircleShape), contentAlignment = Alignment.Center) {
                        if (g.numbered) Text("${i + 1}", style = MaterialTheme.typography.labelMedium, color = tone, maxLines = 1)
                        else Box(Modifier.size(6.dp).background(tone, CircleShape))
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(step, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 4, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** What a source gives and sends (and, for LaunchBox, what is on the device) as small labelled boxes. */
@Composable
private fun Facts(s: ScrapeSource, g: SourceGuide, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        InfoBox("Gives", s.gives + ".", Glyph.Image, Palette.Mint)
        InfoBox("Sends", g.sends, Glyph.Eye, Palette.Lavender)
        if (s == ScrapeSource.LaunchBox) {
            val info = LaunchBoxDb.info
            InfoBox(
                "On this device",
                when {
                    LaunchBoxDb.progress != null -> "Downloading… ${((LaunchBoxDb.progress ?: 0f) * 100).toInt()}%"
                    info == null -> "Not downloaded yet."
                    else -> "${"%,d".format(info.games)} games for ${info.platforms.size} systems, from " +
                        android.text.format.DateUtils.getRelativeTimeSpanString(info.built).toString().lowercase() + "."
                },
                Glyph.Save, Palette.Sky,
            )
        }
    }
}

@Composable
private fun InfoBox(label: String, text: String, glyph: Glyph, color: Color) {
    Column(Modifier.fillMaxWidth().background(color.copy(alpha = 0.1f), RoundedCornerShape(14.dp)).padding(horizontal = 12.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlyphIcon(glyph, color, Modifier.size(13.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = color, maxLines = 1)
        }
        Spacer(Modifier.height(2.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = Palette.Text, maxLines = 4, overflow = TextOverflow.Ellipsis)
    }
}

/** A key or login a source needs: A opens the keyboard for it. Secrets show only that they are saved. */
private class KeyField(val key: String, val label: String, val value: String, val secret: Boolean = false, val optional: Boolean = false, val onClick: () -> Unit) {
    val color: Color get() = when {
        value.isNotBlank() -> Palette.Mint
        optional -> Palette.Lavender
        else -> Palette.Peach
    }
    val shown: String get() = when {
        value.isBlank() -> if (optional) "Optional" else "Not set"
        secret -> "Saved ✓"
        else -> value
    }
}

/** A key as an input field: a key chip, its label over its value or state, and a pencil to say A edits it. */
@Composable
private fun KeyFieldCard(k: KeyField, modifier: Modifier) {
    val shape = RoundedCornerShape(14.dp)
    FocusCard(modifier.height(54.dp), glow = k.color, shape = shape, lifts = false, onClick = k.onClick) {
        Row(
            Modifier.fillMaxSize().background(Palette.NightDeep.copy(alpha = 0.4f)).border(1.5.dp, k.color.copy(alpha = 0.4f), shape)
                .padding(start = 9.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlyphChip(Glyph.Key, k.color, size = 30.dp, round = true)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(k.label, style = MaterialTheme.typography.labelMedium, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    k.shown, style = MaterialTheme.typography.titleSmall,
                    color = if (k.value.isBlank() || k.secret) k.color else Palette.Text, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(6.dp))
            GlyphIcon(Glyph.Pencil, Palette.Muted, Modifier.size(15.dp))
        }
    }
}
