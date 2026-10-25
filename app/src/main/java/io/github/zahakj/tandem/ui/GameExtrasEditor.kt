@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package io.github.zahakj.tandem.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.zahakj.tandem.AndroidApps
import io.github.zahakj.tandem.Game
import io.github.zahakj.tandem.Library
import io.github.zahakj.tandem.Privacy
import io.github.zahakj.tandem.RetroAchievements
import io.github.zahakj.tandem.RunningGames
import io.github.zahakj.tandem.data.HomeStore
import io.github.zahakj.tandem.data.JingleLookup
import io.github.zahakj.tandem.data.LogoFit
import io.github.zahakj.tandem.data.Store
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/*
 * The per-game extras in Edit details (GameEditor.kt): a hover jingle, a companion app for the other screen, the
 * RetroAchievements match and the logo's size and place. All of it lives in the game's GameMeta, so backups carry it.
 */

/** Which part of Edit details shows: the fields and extras, or one of the pickers. */
enum class EditPage { Main, Apps, Achievements }

object GameExtras {
    private const val MAX_JINGLE_BYTES = 20L shl 20

    /** Bumped after a jingle file is copied, so rows showing it look again. */
    var jingleVersion by mutableIntStateOf(0)

    /**
     * Copies [src] into the game's jingles folder as `<game file name>.<ext>` (unless it is already there) and returns
     * the copy. Null when it is too big or can't be copied. Blocking.
     */
    fun saveJingle(g: Game, src: File): File? = runCatching {
        if (!src.isFile || src.length() > MAX_JINGLE_BYTES || !JingleLookup.isAudio(src)) return null
        val dir = JingleLookup.folder(Library.romsDir, g.platform.artIds.firstOrNull() ?: g.platform.id).apply { mkdirs() }
        val dest = File(dir, "${g.stem}.${src.extension.lowercase()}")
        if (src.canonicalPath != dest.canonicalPath) src.copyTo(dest, overwrite = true)
        dest
    }.getOrNull()

    /** Opens Tandem's Files to choose a jingle for [g]; Edit details opens again afterwards. */
    fun chooseJingle(g: Game) {
        GameEditor.close()
        ImagePick.start(
            "files",
            ImagePick.Request(
                "Choose a jingle for ${g.title}",
                onPick = { f ->
                    var saved: File? = null
                    inBackground({ saved = saveJingle(g, f) }) {
                        val s = saved
                        if (s == null) Toasts.show("Couldn't use that file (OGG, MP3 or WAV up to 20 MB)")
                        else {
                            Store.update(g.key) { it.copy(jingle = s.path, jingleOff = false) }
                            jingleVersion++
                            Toasts.show("Jingle saved for ${g.title}")
                        }
                        GameEditor.edit(g)
                    }
                },
                accept = JingleLookup::isAudio,
                what = "jingle",
            ),
        )
    }

    fun setFit(g: Game, change: (LogoFit) -> LogoFit) {
        Store.update(g.key) { m -> m.copy(logoFit = change(m.logoFit ?: LogoFit()).tidy().takeUnless { it.isDefault }) }
    }

    fun setRa(g: Game, id: Int?) {
        Store.update(g.key) { it.copy(raId = id) }
        RetroAchievements.invalidate()
    }
}

/** The jingle [game] would play, worked out off the main thread; null while reading. */
@Composable
private fun rememberJingle(game: Game): JingleLookup.Source? {
    val meta = game.meta
    val src by produceState<JingleLookup.Source?>(null, game.key, meta.jingle, meta.jingleOff, GameExtras.jingleVersion) {
        value = withContext(Dispatchers.IO) { JingleLookup.find(game.meta, Library.romsDir, game.platform.artIds, game.stem) }
    }
    return src
}

/** Small badges for the details screen: the game has its own jingle, or a companion app. */
@Composable
fun GameExtrasBadges(game: Game, chip: Modifier = Modifier) {
    val src = rememberJingle(game)
    if (src is JingleLookup.Source.Own) Pill("♪ Jingle", Palette.Lavender, chip)
    if (game.meta.companionApp != null) Pill("▣ Companion app", Palette.Sky, chip)
}

/** The extras, as items under the fields in Edit details. [focus] notes which button holds the focus. */
fun LazyListScope.gameExtrasItems(g: Game, routeKey: String, focus: (String) -> Unit, open: (EditPage) -> Unit) {
    item("extras-title") {
        Text("Extras", Modifier.padding(top = 8.dp, start = 4.dp), style = MaterialTheme.typography.titleMedium, color = Palette.Muted)
    }
    item("jingle") { JingleRow(g, routeKey, focus) }
    item("companion") { CompanionRow(g, routeKey, focus, open) }
    item("ra") { AchievementsRow(g, routeKey, focus, open) }
    item("logo") { LogoRow(g, routeKey, focus) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExtraRow(label: String, value: String, set: Boolean, buttons: @Composable FlowRowScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(Palette.CardHigh.copy(alpha = 0.55f), RoundedCornerShape(14.dp)).padding(horizontal = 12.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.width(110.dp), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                value, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = if (set) Palette.Text else Palette.Muted,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = buttons)
    }
}

@Composable
private fun ExtraButton(text: String, color: Color, routeKey: String, id: String, focus: (String) -> Unit, onClick: () -> Unit) {
    FocusCard(
        Modifier.height(34.dp).remembersFocus(routeKey, id, false), glow = color, shape = RoundedCornerShape(17.dp),
        onFocus = { focus(id); FocusMemory.remember(routeKey, id) }, onClick = onClick,
    ) {
        Box(Modifier.fillMaxHeight().background(color.copy(alpha = 0.2f)).padding(horizontal = 13.dp), contentAlignment = Alignment.Center) {
            Text(text, style = MaterialTheme.typography.labelLarge, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

// --- Jingle --------------------------------------------------------------------------------------------------------

@Composable
private fun JingleRow(g: Game, routeKey: String, focus: (String) -> Unit) {
    val src = rememberJingle(g)
    val value = when (src) {
        null -> "Looking…"
        JingleLookup.Source.Off -> "None for this game"
        JingleLookup.Source.System -> "None · the system's jingles play"
        is JingleLookup.Source.Own -> src.file.name + if (src.picked) "" else " · from the jingles folder"
    }
    ExtraRow("Hover jingle", value, src is JingleLookup.Source.Own) {
        ExtraButton("Choose file", Palette.Mint, routeKey, "jingle:pick", focus) { GameExtras.chooseJingle(g) }
        // The buttons stay put whatever is chosen, so the highlight never loses its place.
        ExtraButton("▶ Play", Palette.Sky, routeKey, "jingle:play", focus) {
            when {
                src !is JingleLookup.Source.Own -> Toasts.show("No jingle for this game yet")
                !Store.settings.look.sounds -> Toasts.show("Turn on UI sounds to hear jingles")
                else -> GameJingles.play(g.key, src.file, preview = true)
            }
        }
        ExtraButton("None", Palette.Peach, routeKey, "jingle:off", focus) {
            GameJingles.fadeOut()
            Store.update(g.key) { it.copy(jingleOff = true) }
        }
        ExtraButton("Automatic", Palette.Lavender, routeKey, "jingle:auto", focus) {
            Store.update(g.key) { it.copy(jingle = null, jingleOff = false) }
            Toasts.show("Plays the file in the jingles folder, if there is one")
        }
    }
}

// --- Companion app -------------------------------------------------------------------------------------------------

@Composable
private fun CompanionRow(g: Game, routeKey: String, focus: (String) -> Unit, open: (EditPage) -> Unit) {
    val ctx = LocalContext.current
    val meta = g.meta
    val pkg = meta.companionApp
    val label by produceState<String?>(null, pkg) { value = pkg?.let { p -> withContext(Dispatchers.IO) { RunningGames.appLabel(ctx, p) } } }
    val value = when {
        pkg == null -> "None"
        else -> (label ?: pkg) + if (meta.companionAppClose) " · closes after the game" else " · keeps running"
    }
    ExtraRow("Companion app", value, pkg != null) {
        ExtraButton(if (pkg == null) "Choose app" else "Change", Palette.Mint, routeKey, "app:pick", focus) { open(EditPage.Apps) }
        ExtraButton(if (meta.companionAppClose) "Close after: on" else "Close after: off", Palette.Butter, routeKey, "app:close", focus) {
            Store.update(g.key) { it.copy(companionAppClose = !it.companionAppClose) }
        }
        ExtraButton("Remove", Palette.Peach, routeKey, "app:remove", focus) {
            if (pkg == null) Toasts.show("No companion app chosen") else Store.update(g.key) { it.copy(companionApp = null) }
        }
    }
}

/** Installed apps to start with the game on the other screen. */
@Composable
fun CompanionAppPage(g: Game, routeKey: String, modifier: Modifier, done: () -> Unit) {
    val ctx = LocalContext.current
    val apps by produceState<List<AndroidApps.App>?>(null) { value = withContext(Dispatchers.IO) { AndroidApps.list(ctx) } }
    val list = apps
    val key = "$routeKey:apps"
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "Starts on the other screen with the game, and takes the place of Tandem's companion there. Not with one screen, " +
                "a game on the bottom screen, or a system left to its emulator.",
            Modifier.padding(horizontal = 34.dp), style = MaterialTheme.typography.bodySmall, color = Palette.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
        if (list == null) {
            Text("Reading your apps…", Modifier.padding(horizontal = 34.dp, vertical = 12.dp), style = MaterialTheme.typography.bodyMedium, color = Palette.Muted)
            return@Column
        }
        val state = rememberLazyListState()
        val current = g.meta.companionApp
        val target = current?.takeIf { c -> list.any { it.packageName == c } } ?: list.firstOrNull()?.packageName
        KeepInView(state, list.indexOfFirst { it.packageName == target }.coerceAtLeast(0))
        LazyColumn(
            Modifier.fillMaxWidth().weight(1f, fill = false).heightIn(max = 300.dp).fadingEdges(state), state = state,
            contentPadding = PaddingValues(horizontal = 34.dp, vertical = FocusRoom), verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            items(list, key = { it.packageName }) { app ->
                val on = app.packageName == current
                FocusCard(
                    Modifier.fillMaxWidth().takesFocus(key, app.packageName, app.packageName == target),
                    glow = Palette.Mint, shape = RoundedCornerShape(14.dp),
                    onClick = {
                        Store.update(g.key) { it.copy(companionApp = app.packageName) }
                        Toasts.show("${app.label} starts with ${g.title}")
                        done()
                    },
                ) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                        AppIcon(app.packageName, Modifier.size(32.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(app.label, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (on) Pill("Chosen", Palette.Mint)
                    }
                }
            }
        }
    }
}

// --- RetroAchievements ---------------------------------------------------------------------------------------------

@Composable
private fun AchievementsRow(g: Game, routeKey: String, focus: (String) -> Unit, open: (EditPage) -> Unit) {
    val meta = g.meta
    val chosen = meta.raId
    val auto = HomeStore.ra.matches[g.key]
    val id = chosen ?: auto
    val title = RetroAchievements.cachedProgress(g)?.title?.takeIf { it.isNotBlank() }
    val value = when {
        chosen == 0 -> "No achievements (your choice)"
        chosen != null -> (title?.let { "$it · " } ?: "") + "#$chosen · your choice"
        auto == null -> "Automatic · not looked up yet"
        auto == 0 -> "Automatic · no match found"
        else -> (title?.let { "$it · " } ?: "") + "#$auto · automatic"
    }
    ExtraRow("Achievements", value, id != null && id > 0) {
        ExtraButton("Find match", Palette.Mint, routeKey, "ra:find", focus) { open(EditPage.Achievements) }
        ExtraButton("Type ID", Palette.Sky, routeKey, "ra:type", focus) { askRaId(g) }
        ExtraButton("No achievements", Palette.Peach, routeKey, "ra:none", focus) { GameExtras.setRa(g, 0) }
        ExtraButton("Automatic", Palette.Lavender, routeKey, "ra:auto", focus) { GameExtras.setRa(g, null) }
    }
}

private fun askRaId(g: Game) {
    TextEntry.ask("RetroAchievements game ID (the number in its page address)", g.meta.raId?.takeIf { it > 0 }?.toString().orEmpty()) { typed ->
        val t = typed.trim()
        if (t.isEmpty()) return@ask
        val n = t.toIntOrNull()?.takeIf { it > 0 }
        if (n == null) Toasts.show("That isn't a game ID: it's a whole number, like 1234")
        else { GameExtras.setRa(g, n); Toasts.show("Matched to game #$n") }
    }
}

/**
 * Candidates for the match. The only part of Edit details that may go online, and only once it is opened: the
 * console's public game list (cached for a week) when signed in, never for a hidden game.
 */
@Composable
fun AchievementsPage(g: Game, routeKey: String, modifier: Modifier, done: () -> Unit) {
    val hidden = Privacy.isHidden(g)
    val signedIn = RetroAchievements.enabled
    val supported = RetroAchievements.consoleId(g.platform.id) != null
    val found by produceState<List<RetroAchievements.Candidate>?>(null, g.key) {
        value = if (hidden || !signedIn || !supported) emptyList() else RetroAchievements.candidates(g) ?: emptyList()
    }
    val current = g.meta.raId ?: HomeStore.ra.matches[g.key]
    val currentTitle by produceState<String?>(null, g.key, current) {
        value = if (hidden || !signedIn || current == null || current <= 0) null else RetroAchievements.titleOf(g, current)
    }
    val key = "$routeKey:ra"
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val note = when {
            hidden -> "Hidden games are never looked up. Type an ID or choose No achievements instead."
            !signedIn -> "Sign in to RetroAchievements (Settings › Art & accounts) to see suggestions. You can still type an ID."
            !supported -> "RetroAchievements has no list for ${g.platform.name} that Tandem knows."
            current == null -> "Not matched yet."
            current == 0 -> "Currently: no achievements."
            else -> "Currently: " + (currentTitle ?: "game") + " · #$current"
        }
        Text(note, Modifier.padding(horizontal = 34.dp), style = MaterialTheme.typography.bodySmall, color = Palette.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        val list = found
        val state = rememberLazyListState()
        LazyColumn(
            Modifier.fillMaxWidth().weight(1f, fill = false).heightIn(max = 300.dp).fadingEdges(state), state = state,
            contentPadding = PaddingValues(horizontal = 34.dp, vertical = FocusRoom), verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            // The choices that need nothing from the network come first, and hold the focus while candidates load.
            item("type") { ActionRow("Type a game ID", Palette.Sky, key, "type", true) { askRaId(g); done() } }
            item("none") { ActionRow("No achievements", Palette.Peach, key, "none", false) { GameExtras.setRa(g, 0); done() } }
            item("auto") {
                ActionRow("Automatic", Palette.Lavender, key, "auto", false) {
                    GameExtras.setRa(g, null)
                    Toasts.show("Found automatically from now on")
                    done()
                }
            }
            if (list == null) item("wait") {
                Text("Looking through the list…", Modifier.padding(vertical = 8.dp), style = MaterialTheme.typography.bodyMedium, color = Palette.Muted)
            } else if (list.isEmpty() && signedIn && supported && !hidden) item("empty") {
                Text("No close titles in the list.", Modifier.padding(vertical = 8.dp), style = MaterialTheme.typography.bodyMedium, color = Palette.Muted)
            }
            items(list.orEmpty(), key = { "c${it.id}" }) { c ->
                FocusCard(
                    Modifier.fillMaxWidth().takesFocus(key, "c${c.id}", false),
                    glow = Palette.Mint, shape = RoundedCornerShape(14.dp),
                    onClick = { GameExtras.setRa(g, c.id); Toasts.show("Matched to ${c.title}"); done() },
                ) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(c.title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (c.byHash) { Pill("Same file", Palette.Mint); Spacer(Modifier.width(6.dp)) }
                        if (c.id == current) { Pill("Current", Palette.Butter); Spacer(Modifier.width(6.dp)) }
                        Text("#${c.id}", style = MaterialTheme.typography.labelMedium, color = Palette.Muted, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionRow(text: String, color: Color, key: String, id: String, first: Boolean, onClick: () -> Unit) {
    FocusCard(Modifier.fillMaxWidth().takesFocus(key, id, first), glow = color, shape = RoundedCornerShape(14.dp), onClick = onClick) {
        Text(text, Modifier.padding(horizontal = 12.dp, vertical = 9.dp), style = MaterialTheme.typography.titleSmall, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// --- Logo ----------------------------------------------------------------------------------------------------------

@Composable
private fun LogoRow(g: Game, routeKey: String, focus: (String) -> Unit) {
    val fit = (g.meta.logoFit ?: LogoFit()).tidy()
    val nudged = if (fit.dx != 0 || fit.dy != 0) " · moved ${fit.dx}, ${fit.dy}" else ""
    ExtraRow("Logo", "${fit.scale}% · ${LogoFit.label(fit.anchor)}$nudged · see the other screen", !fit.isDefault) {
        ExtraButton("− Smaller", Palette.Sky, routeKey, "logo:small", focus) { GameExtras.setFit(g) { it.copy(scale = it.scale - LogoFit.STEP) } }
        ExtraButton("+ Bigger", Palette.Sky, routeKey, "logo:big", focus) { GameExtras.setFit(g) { it.copy(scale = it.scale + LogoFit.STEP) } }
        ExtraButton("Place: ${LogoFit.label(fit.anchor)}", Palette.Butter, routeKey, "logo:anchor", focus) {
            GameExtras.setFit(g) { it.copy(anchor = LogoFit.nextAnchor(it.anchor)) }
        }
        ExtraButton("◀", Palette.Lavender, routeKey, "logo:left", focus) { GameExtras.setFit(g) { it.copy(dx = it.dx - 1) } }
        ExtraButton("▶", Palette.Lavender, routeKey, "logo:right", focus) { GameExtras.setFit(g) { it.copy(dx = it.dx + 1) } }
        ExtraButton("▲", Palette.Lavender, routeKey, "logo:up", focus) { GameExtras.setFit(g) { it.copy(dy = it.dy - 1) } }
        ExtraButton("▼", Palette.Lavender, routeKey, "logo:down", focus) { GameExtras.setFit(g) { it.copy(dy = it.dy + 1) } }
        ExtraButton("Reset", Palette.Peach, routeKey, "logo:reset", focus) { GameExtras.setFit(g) { LogoFit() } }
    }
}
