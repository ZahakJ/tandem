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
import androidx.compose.foundation.basicMarquee
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
import io.github.zahakj.tandem.Lang
import io.github.zahakj.tandem.Library
import io.github.zahakj.tandem.R
import io.github.zahakj.tandem.Shell
import io.github.zahakj.tandem.Tx
import io.github.zahakj.tandem.data.LaunchBoxDb
import io.github.zahakj.tandem.data.ScrapeSource
import io.github.zahakj.tandem.data.Store
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/*
 * The first-run welcome: once the games are found, Pip asks a few quick questions instead of leaving a new user
 * to dig through Settings. Pick a language (the rest of the welcome then speaks it), a home (with live sketches; the icon grid comes first when Cocoon's files are on
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
        // The soundtrack is written down now, so a later start doesn't take this setup for one from before Calm.
        Store.updateSettings { it.copy(welcomeDone = true, look = it.look.copy(soundtrack = it.look.soundtrack ?: "calm")) }
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
        add("language")
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
        if (HomeMode.isDefault(ctx)) Toasts.show(Tx.s(R.string.look_welcome_home_set))
        next()
    }

    OverlayPanel(visible, onClose = { back() }) {
        val rtl = isRtl
        Column(
            Modifier.fillMaxWidth().background(Palette.Card, RoundedCornerShape(28.dp)).padding(horizontal = 22.dp, vertical = 18.dp),
        ) {
            AnimatedContent(
                steps[step],
                // The next step comes in from the end side (the left in right to left).
                transitionSpec = { (slideInHorizontally(Motion.move()) { if (rtl) -it / 6 else it / 6 } + fadeIn(tween(200))) togetherWith fadeOut(tween(120)) },
                label = "welcome",
            ) { id ->
                Column {
                    when (id) {
                        "language" -> LanguageStep { tag -> Lang.choose(tag); next() }
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
                ButtonHint("A", Tx.s(R.string.common_choose), Palette.Mint)
                Spacer(Modifier.width(14.dp))
                ButtonHint("B", Tx.s(if (step == 0) R.string.look_welcome_skip_all else R.string.common_back), Palette.Lavender)
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

/**
 * Step 1: the language, one card per language with a greeting in its own script (the same miniature as Settings ›
 * Look & feel › Language), English first and highlighted. Picking one switches both screens at once, so the rest of
 * the welcome speaks it, and keeps it like the Settings row does. System default isn't offered here: Settings has it.
 */
@Composable
private fun LanguageStep(pick: (String) -> Unit) {
    val route = "welcome:language"
    val options = Lang.offered
    // English starts highlighted, unless a language was already picked (Android's own per-app setting, say).
    remember { if (FocusMemory.get(route) == null && options.any { it.tag == Lang.chosen }) FocusMemory.remember(route, Lang.chosen) }
    val target = rememberFocusTarget(route, options.map { it.tag })
    StepTitle(Tx.s(R.string.look_welcome_language), Tx.s(R.string.look_welcome_language_detail))
    Row(Modifier.fillMaxWidth().focusRow(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        options.forEach { o ->
            val inUse = o.tag == Lang.chosen
            FocusCard(
                Modifier.weight(1f).height(168.dp).takesFocus(route, o.tag, target == o.tag),
                glow = Palette.Sky, shape = RoundedCornerShape(20.dp), onClick = { pick(o.tag) },
            ) {
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxWidth().weight(1f).background(Palette.NightDeep)) { LanguageLook(o.tag) }
                    Row(
                        Modifier.fillMaxWidth().background(lerp(Palette.Sky, Palette.Night, 0.8f)).padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Each name in its own language and script, never translated, so anyone finds theirs.
                        Text(
                            o.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium.copy(localeList = androidx.compose.ui.text.intl.LocaleList(o.tag)),
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        if (inUse) Box(Modifier.size(9.dp).clip(CircleShape).background(Palette.Sky))
                    }
                }
            }
        }
    }
}

/** Step 2: four homes side by side, drawn live in the current theme. */
@Composable
private fun HomeStep(cocoonFound: Boolean, pick: (LookPreset) -> Unit) {
    val route = "welcome:home"
    val options = listOfNotNull(
        Presets.byId("cocoon").takeIf { cocoonFound },
        Presets.byId("cozy"),
        Presets.byId("orbit"),
        Presets.byId("cocoon").takeIf { !cocoonFound },
        Presets.byId("minimal"),
    )
    val target = rememberFocusTarget(route, options.map { it.id })
    StepTitle(
        Tx.s(R.string.look_welcome_home),
        Tx.s(if (cocoonFound) R.string.look_welcome_home_cocoon else R.string.look_welcome_home_detail),
    )
    Row(Modifier.fillMaxWidth().focusRow(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        options.forEach { p ->
            FocusCard(
                Modifier.weight(1f).height(196.dp).takesFocus(route, p.id, target == p.id),
                glow = p.color, shape = RoundedCornerShape(20.dp), onClick = { pick(p) },
            ) { focused ->
                Column(Modifier.fillMaxSize()) {
                    val footer = lerp(p.color, Palette.Night, 0.8f)
                    // A sketch rather than the live grid settings, so it reads at a glance like the other three.
                    LayoutPreview(p.layout, Modifier.fillMaxWidth().weight(1f), rounded = false, sketch = true)
                    Column(Modifier.fillMaxWidth().background(footer).padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Text(p.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        // What it changes scrolls by on the highlighted card when it is longer than the card.
                        Text(
                            p.changes.first(),
                            if (focused) Modifier.basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 900, velocity = 40.dp) else Modifier,
                            style = MaterialTheme.typography.labelLarge, color = p.color, maxLines = 1,
                            overflow = if (focused) TextOverflow.Clip else TextOverflow.Ellipsis,
                        )
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
    StepTitle(Tx.s(R.string.look_welcome_cocoon), Tx.s(R.string.look_welcome_cocoon_detail))
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (c.backup != null) FoundLine(Palette.Sky, Tx.s(R.string.look_welcome_cocoon_backup, CocoonSetup.backupDate(c.backup)))
        if (c.themePacks.isNotEmpty()) {
            FoundLine(Palette.Accent, Tx.p(R.plurals.look_welcome_cocoon_themes, c.themePacks.size, c.themePacks.size, c.themePacks.take(3).joinToString(", ") { it.name }))
        }
        if (c.iconPacks > 0) FoundLine(Palette.Sky, Tx.p(R.plurals.look_welcome_cocoon_icons, c.iconPacks))
        if (c.platformFiles > 0) FoundLine(Palette.Butter, Tx.p(R.plurals.look_welcome_cocoon_platforms, c.platformFiles))
        FoundLine(Palette.Mint, Tx.s(R.string.look_welcome_cocoon_gamelists))
    }
    Spacer(Modifier.height(14.dp))
    Row(Modifier.focusRow(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if ("import" in keys) {
            val ctx = LocalContext.current
            PillButton(Tx.s(if (CocoonSetup.busy) R.string.look_welcome_cocoon_bringing else R.string.look_welcome_cocoon_bring), Palette.Sky, Modifier.takesFocus(route, "import", target == "import")) {
                CocoonSetup.run(ctx) { ok -> if (ok) next() }
            }
        }
        if ("use" in keys && pack != null) {
            PillButton(Tx.s(R.string.look_welcome_cocoon_wear, pack.name), Palette.Accent, Modifier.takesFocus(route, "use", target == "use")) { applyTheme(pack.id); next() }
        }
        PillButton(Tx.s(if (usingPack) R.string.look_welcome_next else R.string.look_welcome_cocoon_keep), Palette.Lavender, Modifier.takesFocus(route, "keep", target == "keep")) { next() }
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
        Tx.s(R.string.look_welcome_art),
        Tx.s(R.string.look_welcome_art_detail),
    )
    // The same tiles as the setup list, without the order (that is the list's business) or the two lines on what
    // each gives, so the step fits under its title.
    SourceGrid(tiles, columns = 3, tileHeight = 92.dp, route = route, target = target, compact = true)
    Spacer(Modifier.height(12.dp))
    Row(Modifier.focusRow(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if ("lb" in buttons) {
            PillButton(Tx.s(R.string.look_welcome_art_launchbox, LaunchBoxDb.APPROX_MB), Palette.Butter, Modifier.takesFocus(route, "lb", target == "lb")) {
                ArtSetup.downloadLaunchBox(ctx)
            }
        }
        PillButton(Tx.s(R.string.look_welcome_next), Palette.Lavender, Modifier.takesFocus(route, "next", target == "next")) { next() }
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
        Tx.s(if (ready) R.string.look_welcome_resume_ready else R.string.look_welcome_resume),
        Tx.s(
            when {
                ready -> R.string.look_welcome_resume_ready_detail
                alive -> R.string.look_welcome_resume_allow_detail
                else -> R.string.look_welcome_resume_start_detail
            },
        ),
        mood = if (ready) 1 else 0,
    )
    Row(Modifier.focusRow(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!ready) {
            PillButton(Tx.s(if (alive) R.string.look_welcome_resume_allow else R.string.look_welcome_resume_how), Palette.Mint, Modifier.takesFocus(route, "allow", target == "allow")) {
                askShizuku()
            }
        }
        PillButton(Tx.s(if (ready) R.string.look_welcome_next else R.string.look_welcome_skip_now), Palette.Lavender, Modifier.takesFocus(route, "next", target == "next")) { next() }
    }
}

/** Step 4: Tandem as the Home screen. */
@Composable
private fun HomeScreenStep(isHome: Boolean, next: () -> Unit, ask: () -> Unit) {
    val route = "welcome:homescreen"
    val keys = if (isHome) listOf("next") else listOf("make", "next")
    val target = rememberFocusTarget(route, keys)
    StepTitle(
        Tx.s(if (isHome) R.string.look_welcome_homescreen_is else R.string.look_welcome_homescreen),
        Tx.s(R.string.look_welcome_homescreen_detail),
    )
    Row(Modifier.focusRow(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!isHome) PillButton(Tx.s(R.string.look_welcome_homescreen_make), Palette.Mint, Modifier.takesFocus(route, "make", target == "make")) { ask() }
        PillButton(Tx.s(if (isHome) R.string.look_welcome_next else R.string.look_welcome_not_now), Palette.Lavender, Modifier.takesFocus(route, "next", target == "next")) { next() }
    }
}

/** The last step: the four buttons worth knowing. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DoneStep(done: () -> Unit) {
    val route = "welcome:done"
    val target = rememberFocusTarget(route, listOf("go"))
    StepTitle(Tx.s(R.string.look_welcome_done), Tx.s(R.string.look_welcome_done_detail))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // A legend, not controls: tapping these does nothing.
        ButtonHint("Y", Tx.s(R.string.look_welcome_done_y), Palette.Butter, tappable = false)
        val layout = Store.settings.look.homeLayout
        val homeX = (layout == "grid" || layout == "orbit") && Store.settings.look.xButton != "search"
        ButtonHint("X", Tx.s(if (homeX) R.string.look_welcome_done_x_swap else R.string.settings_hint_search), Palette.Sky, tappable = false)
        ButtonHint("Start", Tx.s(R.string.look_welcome_done_start), Palette.Lavender, tappable = false)
        ButtonHint("Select", Tx.s(R.string.look_welcome_done_select), Palette.Peach, tappable = false)
    }
    Spacer(Modifier.height(14.dp))
    PillButton(Tx.s(R.string.look_welcome_play), Palette.Mint, Modifier.takesFocus(route, "go", target == "go")) { done() }
}
