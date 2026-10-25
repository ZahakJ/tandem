package io.github.zahakj.tandem.ui

import android.app.role.RoleManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.zahakj.tandem.Libraries
import io.github.zahakj.tandem.Library
import io.github.zahakj.tandem.Shell
import io.github.zahakj.tandem.data.LaunchBoxDb
import io.github.zahakj.tandem.data.ScrapeSource
import io.github.zahakj.tandem.data.Store
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/*
 * The first-run welcome: once the games are found, Pip asks a few quick questions instead of leaving a new user
 * to dig through Settings. Pick a home (with live sketches; the icon grid comes first when Cocoon's files are on
 * the device), bring over Cocoon's files (backup, theme packs, icon packs, platform files), set up where artwork
 * and details come from (LaunchBox and libretro need no account; the rest take a free key), allow resuming games
 * through Shizuku, and make Tandem the Home screen. Every step can be skipped; B goes back a step, and B on the
 * first step closes it. It shows once; Settings has every choice again.
 */

object Welcome {
    var step by mutableIntStateOf(0)

    /** True while the welcome is up (the app's own buttons stay quiet, like for any popup). */
    val open: Boolean get() = shown && !Store.settings.welcomeDone

    /** Set by the overlay while the home screen is showing with a library loaded. */
    internal var shown by mutableStateOf(false)

    fun finish() {
        Store.updateSettings { it.copy(welcomeDone = true) }
        Nav.focusTick++
    }
}

/** Cocoon's files found in the ROMs folder. */
private class CocoonThings(val themePacks: List<ThemePack>, val iconPacks: Int, val platformFiles: Int, val backup: File?) {
    val any get() = themePacks.isNotEmpty() || iconPacks > 0 || platformFiles > 0 || backup != null
}

@Composable
fun WelcomeOverlay() {
    val wanted = !Store.settings.welcomeDone && Libraries.all != null && Nav.route == Route.Home
    androidx.compose.runtime.SideEffect { Welcome.shown = wanted }
    val ctx = LocalContext.current
    val cocoon by produceState<CocoonThings?>(null, wanted, ThemePacks.packs) {
        if (!wanted) return@produceState
        val files = withContext(Dispatchers.IO) {
            File(Library.romsDir, "platforms").listFiles { f -> f.name.endsWith(".json", true) }?.size ?: 0
        }
        val backup = withContext(Dispatchers.IO) { CocoonSetup.newestBackup() }
        value = CocoonThings(ThemePacks.packs.filter { it.format == PackFormat.Cocoon }, ThemePacks.assetPacks.size, files, backup)
    }
    val found = cocoon?.any == true
    val steps = buildList {
        add("home")
        if (found) add("cocoon")
        add("art")
        add("resume")
        add("homescreen")
        add("done")
    }
    val step = Welcome.step.coerceIn(0, steps.size - 1)
    fun next() { if (step >= steps.size - 1) Welcome.finish() else Welcome.step = step + 1 }
    fun back() { if (step == 0) Welcome.finish() else Welcome.step = step - 1 }
    val visible = wanted && cocoon != null

    var homeTick by remember { mutableIntStateOf(0) }
    val askHome = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        homeTick++
        if (HomeMode.isDefault(ctx)) Toasts.show("Tandem is your Home screen now ✨")
        next()
    }

    OverlayPanel(visible, onClose = { back() }) {
        Column(
            Modifier.fillMaxWidth().background(Palette.Card, RoundedCornerShape(28.dp)).padding(horizontal = 22.dp, vertical = 18.dp),
        ) {
            AnimatedContent(
                steps[step],
                transitionSpec = { (slideInHorizontally(Motion.move()) { it / 6 } + fadeIn(tween(200))) togetherWith fadeOut(tween(120)) },
                label = "welcome",
            ) { id ->
                Column {
                    when (id) {
                        "home" -> HomeStep(found) { p -> Presets.applyQuietly(ctx, p); next() }
                        "cocoon" -> cocoon?.let { CocoonStep(it, ::next) }
                        "art" -> ArtStep(::next)
                        "resume" -> ResumeStep(::next)
                        "homescreen" -> HomeScreenStep(remember(homeTick) { HomeMode.isDefault(ctx) }, ::next) {
                            val roles = ctx.getSystemService(RoleManager::class.java)
                            val intent = roles?.takeIf { it.isRoleAvailable(RoleManager.ROLE_HOME) }?.createRequestRoleIntent(RoleManager.ROLE_HOME)
                            if (intent == null || runCatching { askHome.launch(intent) }.isFailure) { HomeMode.openHomeSettings(ctx); next() }
                        }
                        else -> DoneStep { Welcome.finish() }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                steps.indices.forEach { i ->
                    Box(Modifier.padding(end = 6.dp).size(if (i == step) 18.dp else 7.dp, 7.dp).clip(CircleShape).background(if (i <= step) Palette.Accent else Palette.Muted.copy(alpha = 0.35f)))
                }
                Spacer(Modifier.weight(1f))
                ButtonHint("A", "Choose", Palette.Mint)
                Spacer(Modifier.width(14.dp))
                ButtonHint("B", if (step == 0) "Skip all" else "Back", Palette.Lavender)
            }
        }
    }
}

@Composable
private fun StepTitle(title: String, detail: String, mood: Int = 1) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Pip(56.dp, mood = mood)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = Palette.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
    Spacer(Modifier.height(14.dp))
}

/** Step 1: three homes side by side, drawn live in the current theme. */
@Composable
private fun HomeStep(cocoonFound: Boolean, pick: (LookPreset) -> Unit) {
    val route = "welcome:home"
    val options = listOfNotNull(
        Presets.byId("cocoon").takeIf { cocoonFound },
        Presets.byId("cozy"),
        Presets.byId("cocoon").takeIf { !cocoonFound },
        Presets.byId("minimal"),
    )
    val target = rememberFocusTarget(route, options.map { it.id })
    StepTitle(
        "Hi! Which home feels like yours?",
        if (cocoonFound) "I found Cocoon files on this device, so the icon grid is first. You can change this any time in Settings." else "Pick one to start with. Settings › Home & layout has more, any time.",
    )
    Row(Modifier.fillMaxWidth().focusRow(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        options.forEach { p ->
            FocusCard(
                Modifier.weight(1f).height(196.dp).takesFocus(route, p.id, target == p.id),
                glow = p.color, shape = RoundedCornerShape(20.dp), onClick = { pick(p) },
            ) {
                Column(Modifier.fillMaxSize()) {
                    LayoutPreview(p.layout, Modifier.fillMaxWidth().weight(1f), rounded = false)
                    Column(Modifier.fillMaxWidth().background(lerp(p.color, Palette.Night, 0.8f)).padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(p.title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (cocoonFound && p.id == "cocoon") Pill("Suggested", p.color)
                        }
                        Text(p.changes.first(), style = MaterialTheme.typography.labelLarge, color = p.color, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

/** Step 2 (only when found): what Cocoon left behind, and whether to wear its theme. */
@Composable
private fun CocoonStep(c: CocoonThings, next: () -> Unit) {
    val route = "welcome:cocoon"
    val usingPack = ThemePacks.byId(Store.settings.look.theme) != null
    // Without a backup to name one, suggest the most recently installed pack.
    val pack = ThemePacks.newest(c.themePacks)
    val keys = listOfNotNull("import".takeIf { c.backup != null }, "use".takeIf { pack != null && !usingPack && c.backup == null }, "keep")
    val target = rememberFocusTarget(route, keys)
    StepTitle("I found your Cocoon things", "Nothing to copy: Tandem reads them where Cocoon keeps them, and never changes them.")
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (c.backup != null) FoundLine(Palette.Sky, "Your Cocoon backup of ${CocoonSetup.backupDate(c.backup)}: the same home, dock and theme in one press")
        if (c.themePacks.isNotEmpty()) FoundLine(Palette.Accent, "${io.github.zahakj.tandem.plural(c.themePacks.size, "theme pack")}: " + c.themePacks.take(3).joinToString(", ") { it.name })
        if (c.iconPacks > 0) FoundLine(Palette.Sky, "${io.github.zahakj.tandem.plural(c.iconPacks, "icon pack")} for systems and collections, used automatically")
        if (c.platformFiles > 0) FoundLine(Palette.Butter, "${io.github.zahakj.tandem.plural(c.platformFiles, "platform file")}: their emulators are ready in Systems")
        FoundLine(Palette.Mint, "Game names, descriptions and art from Cocoon's gamelists come over by themselves")
    }
    Spacer(Modifier.height(14.dp))
    Row(Modifier.focusRow(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if ("import" in keys) {
            val ctx = LocalContext.current
            PillButton(if (CocoonSetup.busy) "Bringing it over…" else "Bring my Cocoon setup over", Palette.Sky, Modifier.takesFocus(route, "import", target == "import")) {
                CocoonSetup.run(ctx) { ok -> if (ok) next() }
            }
        }
        if ("use" in keys && pack != null) {
            PillButton("Wear ${pack.name}", Palette.Accent, Modifier.takesFocus(route, "use", target == "use")) { applyTheme(pack.id); next() }
        }
        PillButton(if (usingPack) "Next" else "Keep this theme", Palette.Lavender, Modifier.takesFocus(route, "keep", target == "keep")) { next() }
    }
}

@Composable
private fun FoundLine(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * Artwork & details: the scraping sources as tiles (A on one explains it and takes its key), LaunchBox's
 * database one press away. Nothing is downloaded unless the user asks.
 */
@Composable
private fun ArtStep(next: () -> Unit) {
    val route = "welcome:art"
    val ctx = LocalContext.current
    // No-account sources first, so the easy path reads first.
    val tiles = listOf(
        ScrapeSource.LaunchBox, ScrapeSource.Libretro, ScrapeSource.Igdb,
        ScrapeSource.SteamGridDb, ScrapeSource.ScreenScraper, ScrapeSource.Hltb,
    )
    val lbReady = LaunchBoxDb.info != null || LaunchBoxDb.progress != null
    val buttons = listOfNotNull("lb".takeIf { !lbReady }, "next")
    val target = rememberFocusTarget(route, buttons + tiles.map { it.name })
    StepTitle(
        "Artwork & details",
        "LaunchBox and libretro need no account. IGDB, SteamGridDB and ScreenScraper add more with a free key: pick one to see how.",
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        tiles.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth().focusRow(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEach { s -> SourceTile(s, null, Modifier.weight(1f).takesFocus(route, s.name, target == s.name), compact = true) }
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    Row(Modifier.focusRow(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if ("lb" in buttons) {
            PillButton("Get LaunchBox (${LaunchBoxDb.APPROX_MB} MB, Wi-Fi)", Palette.Butter, Modifier.takesFocus(route, "lb", target == "lb")) {
                ArtSetup.downloadLaunchBox(ctx)
            }
        }
        PillButton("Next", Palette.Lavender, Modifier.takesFocus(route, "next", target == "next")) { next() }
    }
}

/** Step 3: resume running games through Shizuku. */
@Composable
private fun ResumeStep(next: () -> Unit) {
    val route = "welcome:resume"
    val ready = remember(ShizukuWatch.version) { Shell.ready() }
    val alive = remember(ShizukuWatch.version) { runCatching { rikka.shizuku.Shizuku.pingBinder() }.getOrDefault(false) }
    val keys = if (ready) listOf("next") else listOf("allow", "next")
    val target = rememberFocusTarget(route, keys)
    StepTitle(
        if (ready) "Resume is ready" else "Pick up right where you left off",
        when {
            ready -> "Picking a game that's still open brings it back instead of restarting it."
            alive -> "Allow Tandem in Shizuku and a game that's still running comes back as you left it, with the switcher (hold Select) to jump between games."
            else -> "With the Shizuku app running, Tandem can bring a game that's still open back instead of restarting it. Start Shizuku, then allow Tandem."
        },
        mood = if (ready) 1 else 0,
    )
    Row(Modifier.focusRow(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!ready) PillButton(if (alive) "Allow in Shizuku" else "How do I start Shizuku?", Palette.Mint, Modifier.takesFocus(route, "allow", target == "allow")) { askShizuku() }
        PillButton(if (ready) "Next" else "Skip for now", Palette.Lavender, Modifier.takesFocus(route, "next", target == "next")) { next() }
    }
}

/** Step 4: Tandem as the Home screen. */
@Composable
private fun HomeScreenStep(isHome: Boolean, next: () -> Unit, ask: () -> Unit) {
    val route = "welcome:homescreen"
    val keys = if (isHome) listOf("next") else listOf("make", "next")
    val target = rememberFocusTarget(route, keys)
    StepTitle(
        if (isHome) "Tandem is your Home screen" else "Make Tandem your Home screen?",
        "Press Home anywhere and you land back here, with your running game one button away. Your apps stay in the dock. You can switch back any time in Settings.",
    )
    Row(Modifier.focusRow(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!isHome) PillButton("Make it my Home screen", Palette.Mint, Modifier.takesFocus(route, "make", target == "make")) { ask() }
        PillButton(if (isHome) "Next" else "Not now", Palette.Lavender, Modifier.takesFocus(route, "next", target == "next")) { next() }
    }
}

/** The last step: the four buttons worth knowing. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DoneStep(done: () -> Unit) {
    val route = "welcome:done"
    val target = rememberFocusTarget(route, listOf("go"))
    StepTitle("All set. Let's play!", "Four buttons worth knowing, wherever you are:")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // A legend, not controls: tapping these does nothing.
        ButtonHint("Y", "Options for anything", Palette.Butter, tappable = false)
        val gridX = Store.settings.look.homeLayout == "grid" && Store.settings.look.xButton != "search"
        ButtonHint("X", if (gridX) "Swap screens on the grid, search elsewhere" else "Search", Palette.Sky, tappable = false)
        ButtonHint("Start", "Start panel, Settings inside (hold: quick panel)", Palette.Lavender, tappable = false)
        ButtonHint("Select", "Surprise me (hold: switcher)", Palette.Peach, tappable = false)
    }
    Spacer(Modifier.height(14.dp))
    PillButton("Let's play", Palette.Mint, Modifier.takesFocus(route, "go", target == "go")) { done() }
}
