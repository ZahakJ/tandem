package io.github.zahakj.tandem.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
 * The list of sources is a popup opened from Settings › Art & accounts and from the welcome; A on a source opens
 * its card. Nothing here touches the network until the user presses Test or Download.
 */

object ArtSetup {
    /** The list of sources is open. */
    var wizard by mutableStateOf(false)
    /** The source whose card is open, on top of whatever is showing. */
    var sheet by mutableStateOf<ScrapeSource?>(null)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun openWizard() { FocusMemory.forget(WIZARD); wizard = true }
    fun open(s: ScrapeSource) { FocusMemory.forget(SHEET); sheet = s }
    fun close() { if (sheet != null) sheet = null else wizard = false; Nav.focusTick++ }

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

/** How to set a source up, in plain words. */
class SourceGuide(val account: String, val steps: List<String>, val sends: String)

fun guideFor(s: ScrapeSource): SourceGuide = when (s) {
    ScrapeSource.LaunchBox -> SourceGuide(
        "No account needed",
        listOf(
            "Tandem downloads LaunchBox's free games database once (about ${LaunchBoxDb.APPROX_MB} MB, Wi-Fi recommended) and keeps just your systems.",
            "Games are looked up on this device. It refreshes once a month, when you scrape on Wi-Fi.",
        ),
        "Nothing about your games. Pictures come from images.launchbox-app.com.",
    )
    ScrapeSource.ScreenScraper -> SourceGuide(
        "Free account + developer ID",
        listOf(
            "1. Make a free account at screenscraper.fr.",
            "2. Ask for API access on their forum (\"Demande d'accès à l'API\") to get a developer ID and password. Tandem can't ship one.",
            "3. Enter both below. Your own login is optional but gives you a bigger daily quota.",
        ),
        "Your login, the developer ID, and each game's file name, size, system and checksum.",
    )
    ScrapeSource.Igdb -> SourceGuide(
        "Free Twitch account",
        listOf(
            "1. Sign in at dev.twitch.tv/console/apps (Twitch asks for two-factor sign-in first).",
            "2. Register Your Application: any name, redirect URL http://localhost, category Application Integration, client type Confidential.",
            "3. Copy the Client ID, press New Secret, and copy the Client Secret.",
        ),
        "Your client ID and secret (to Twitch) and each game's title.",
    )
    ScrapeSource.SteamGridDb -> SourceGuide(
        "Free account",
        listOf(
            "1. Sign in at steamgriddb.com (with your Steam account).",
            "2. Open steamgriddb.com/profile/preferences/api.",
            "3. Generate an API key and enter it below.",
        ),
        "Your API key and each game's title.",
    )
    ScrapeSource.Libretro -> SourceGuide(
        "No account needed",
        listOf("Always ready. Great for classic systems; it has nothing for Switch, Wii U or PC games."),
        "Picture names made from each game's file name or title.",
    )
    ScrapeSource.Hltb -> SourceGuide(
        "No account needed",
        listOf(
            "Uses the same search as the howlongtobeat.com website. It isn't an official API, so it can stop working when the site changes; scraping then just skips it.",
            "Times show on the bottom screen.",
        ),
        "Each game's title.",
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

/**
 * One source as a card: name, what it gives, its state. A opens its setup card; with [onMove], Left and Right
 * move it earlier or later in the order.
 */
@Composable
fun SourceTile(
    s: ScrapeSource, index: Int?, modifier: Modifier, compact: Boolean = false,
    onMove: ((Int) -> Unit)? = null, onFocus: () -> Unit = {},
) {
    val (status, color) = sourceStatus(s)
    FocusCard(
        modifier.fillMaxWidth().onPreviewKeyEvent { e ->
            val by = when (e.key) { Key.DirectionLeft -> -1; Key.DirectionRight -> 1; else -> 0 }
            if (onMove == null || by == 0) return@onPreviewKeyEvent false
            if (e.type == KeyEventType.KeyDown) { onMove(by); Sounds.move() }
            true
        },
        glow = color, shape = RoundedCornerShape(16.dp), onFocus = onFocus, onClick = { ArtSetup.open(s) },
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (index != null) {
                Box(Modifier.size(24.dp).clip(CircleShape).background(color.copy(alpha = 0.22f)), contentAlignment = Alignment.Center) {
                    Text("${index + 1}", style = MaterialTheme.typography.labelLarge, color = color)
                }
                Spacer(Modifier.width(10.dp))
            }
            if (compact) {
                // Two short lines: the name and what it needs (the welcome's tiles).
                Column(Modifier.weight(1f)) {
                    Text(s.label, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(needs(s), style = MaterialTheme.typography.bodySmall, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            } else {
                // One line: the name, then what it gives as far as it fits (the full text is on its card).
                Text(s.label, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                Spacer(Modifier.width(10.dp))
                Text(
                    needs(s) + " · " + s.gives, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
                    color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Pill(status, color)
        }
    }
}

/** The popups: the list of sources (from Settings) and one source's card (from anywhere). */
@Composable
fun ArtSetupOverlay() {
    InterceptButtons(ArtSetup.wizard || ArtSetup.sheet != null) { true }
    OverlayPanel(ArtSetup.wizard, onClose = { ArtSetup.wizard = false; Nav.focusTick++ }) {
        val cfg = ScrapeConfig.settings
        val order = cfg.sourceOrder
        val target = rememberFocusTarget(WIZARD, order.map { it.name })
        PanelSurface(Modifier.width(680.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Pip(52.dp, mood = 1)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Artwork & details setup", style = MaterialTheme.typography.titleLarge, maxLines = 1)
                    Text(
                        "Scraping asks these in order and stops at the first that has what's missing. LaunchBox and libretro need no account.",
                        style = MaterialTheme.typography.bodySmall, color = Palette.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Column(Modifier.padding(horizontal = 10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                order.forEachIndexed { i, s ->
                    SourceTile(
                        s, i, Modifier.takesFocus(WIZARD, s.name, target == s.name),
                        onMove = { by -> ScrapeConfig.update { it.moved(s, by) } },
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                ButtonHint("A", "Set up", Palette.Mint)
                ButtonHint("◀▶", "Ask earlier / later", Palette.Lavender)
                ButtonHint("B", "Done", Palette.Lavender)
            }
        }
    }
    val sheet = ArtSetup.sheet
    val shown = rememberLatest(sheet)
    OverlayPanel(sheet != null, onClose = { ArtSetup.sheet = null; Nav.focusTick++ }) {
        val s = shown ?: return@OverlayPanel
        androidx.compose.foundation.layout.BoxWithConstraints(contentAlignment = Alignment.Center) { SourceSheet(s, maxHeight) }
    }
}

/** A button on a source card. */
private data class SheetButton(val key: String, val label: String, val color: Color, val onClick: () -> Unit)

@Composable
private fun SourceSheet(s: ScrapeSource, maxHeight: androidx.compose.ui.unit.Dp) {
    val ctx = LocalContext.current
    val cfg = ScrapeConfig.settings
    val g = guideFor(s)
    val (status, color) = sourceStatus(s)
    val test = SourceTests.results[s]

    fun saved(name: String, v: String) = if (v.isBlank()) name else "$name ✓"
    fun keyChanged() = SourceTests.forget(s)
    val keys: List<SheetButton> = when (s) {
        ScrapeSource.ScreenScraper -> listOf(
            SheetButton("devid", saved("Developer ID", cfg.ssDevId), if (cfg.ssDevId.isBlank()) Palette.Peach else Palette.Sky) {
                TextEntry.ask("ScreenScraper developer ID", cfg.ssDevId) { v -> ScrapeConfig.update { it.copy(ssDevId = v.trim()) }; keyChanged() }
            },
            SheetButton("devpw", saved("Dev password", cfg.ssDevPassword), if (cfg.ssDevPassword.isBlank()) Palette.Peach else Palette.Sky) {
                TextEntry.ask("ScreenScraper developer password", "", secret = true) { v -> ScrapeConfig.update { it.copy(ssDevPassword = v) }; keyChanged() }
            },
            SheetButton("user", saved("Your login", cfg.ssUser), Palette.Lavender) {
                TextEntry.ask("Your ScreenScraper username (optional)", cfg.ssUser) { v -> ScrapeConfig.update { it.copy(ssUser = v.trim(), ssVerifiedUser = "") }; keyChanged() }
            },
            SheetButton("pw", saved("Your password", cfg.ssPassword), Palette.Lavender) {
                TextEntry.ask("Your ScreenScraper password", "", secret = true) { v -> ScrapeConfig.update { it.copy(ssPassword = v, ssVerifiedUser = "") }; keyChanged() }
            },
        )
        ScrapeSource.Igdb -> listOf(
            SheetButton("id", saved("Client ID", cfg.igdbClientId), if (cfg.igdbClientId.isBlank()) Palette.Peach else Palette.Sky) {
                TextEntry.ask("Twitch Client ID (dev.twitch.tv/console/apps)", cfg.igdbClientId) { v -> ScrapeConfig.update { it.copy(igdbClientId = v.trim()) }; keyChanged() }
            },
            SheetButton("secret", saved("Client secret", cfg.igdbSecret), if (cfg.igdbSecret.isBlank()) Palette.Peach else Palette.Sky) {
                TextEntry.ask("Twitch Client Secret", "", secret = true) { v -> ScrapeConfig.update { it.copy(igdbSecret = v.trim()) }; keyChanged() }
            },
        )
        ScrapeSource.SteamGridDb -> listOf(
            SheetButton("key", saved("API key", cfg.sgdbKey), if (cfg.sgdbKey.isBlank()) Palette.Peach else Palette.Sky) {
                TextEntry.ask("SteamGridDB API key (steamgriddb.com › Preferences › API)", "", secret = true) { v -> ScrapeConfig.update { it.copy(sgdbKey = v.trim()) }; keyChanged() }
            },
        )
        else -> emptyList()
    }
    val on = cfg.isOn(s)
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
    val keysKeys = keys.map { it.key }
    val actionKeys = actions.map { it.key }
    // A source that still needs its keys opens on the first key; a ready one on Test.
    val target = rememberFocusTarget(
        SHEET,
        if (!cfg.configured(s)) keysKeys + actionKeys else listOf("test") + actionKeys.filter { it != "test" } + keysKeys,
    )

    PanelSurface(Modifier.width(680.dp).heightIn(max = maxHeight)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Pip(48.dp, mood = if (test?.ok == false) 2 else 1)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(s.label, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.width(10.dp))
                    Pill(status, color)
                }
                Text(s.gives, style = MaterialTheme.typography.bodySmall, color = Palette.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.height(8.dp))
        // The explanation scrolls if a small screen can't fit it; the buttons below always show.
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(g.account, style = MaterialTheme.typography.titleSmall, color = Palette.Butter)
            g.steps.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
            Text("Sends: ${g.sends}", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
            if (s == ScrapeSource.LaunchBox) {
                val info = LaunchBoxDb.info
                Text(
                    when {
                        LaunchBoxDb.progress != null -> "Downloading… ${((LaunchBoxDb.progress ?: 0f) * 100).toInt()}%"
                        info == null -> "Not downloaded yet."
                        else -> "On this device: ${"%,d".format(info.games)} games for ${info.platforms.size} systems, from " +
                            android.text.format.DateUtils.getRelativeTimeSpanString(info.built).toString().lowercase() + "."
                    },
                    style = MaterialTheme.typography.bodySmall, color = Palette.Sky,
                )
            }
        }
        if (test != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                (if (test.ok) "✓ " else "✗ ") + test.message, style = MaterialTheme.typography.titleSmall,
                color = if (test.ok) Palette.Mint else Palette.Peach, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(10.dp))
        if (keys.isNotEmpty()) {
            SheetRow(keys, target)
            Spacer(Modifier.height(8.dp))
        }
        SheetRow(actions, target)
    }
}

@Composable
private fun SheetRow(buttons: List<SheetButton>, target: String?) {
    Row(Modifier.focusRow(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        buttons.forEach { b ->
            PillButton(b.label, b.color, Modifier.takesFocus(SHEET, b.key, target == b.key), b.onClick)
        }
    }
}
