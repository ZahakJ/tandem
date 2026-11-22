@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package io.github.zahakj.tandem.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import io.github.zahakj.tandem.Manuals
import io.github.zahakj.tandem.Privacy
import io.github.zahakj.tandem.R
import io.github.zahakj.tandem.Tx
import io.github.zahakj.tandem.RetroAchievements
import io.github.zahakj.tandem.RunningGames
import io.github.zahakj.tandem.data.HomeStore
import io.github.zahakj.tandem.data.LogoFit
import io.github.zahakj.tandem.data.Store
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/*
 * The per-game extras in Edit details (GameEditor.kt): a companion app for the other screen, the
 * RetroAchievements match and the logo's size and place. All of it lives in the game's GameMeta, so backups carry it.
 */

/** Which part of Edit details shows: the fields and extras, or one of the pickers. */
enum class EditPage { Main, Apps, Achievements }

object GameExtras {
    fun setFit(g: Game, change: (LogoFit) -> LogoFit) {
        Store.update(g.key) { m -> m.copy(logoFit = change(m.logoFit ?: LogoFit()).tidy().takeUnless { it.isDefault }) }
    }

    fun setRa(g: Game, id: Int?) {
        Store.update(g.key) { it.copy(raId = id) }
        RetroAchievements.invalidate()
    }
}

/** Small badges for the details screen: the game has a companion app. */
@Composable
fun GameExtrasBadges(game: Game, chip: Modifier = Modifier) {
    if (game.meta.companionApp != null) Pill(Tx.s(R.string.library_extras_companion_badge), Palette.Sky, chip)
    // Built by Peanuts Native: Play starts it natively.
    if (io.github.zahakj.tandem.PeanutsNative.ready(game)) Pill(Tx.s(R.string.library_extras_native_badge), Palette.Mint, chip)
}

/** The extras, as items under the fields in Edit details. [focus] notes which button holds the focus. */
fun LazyListScope.gameExtrasItems(g: Game, routeKey: String, focus: (String) -> Unit, open: (EditPage) -> Unit) {
    item("extras-title") {
        Text(Tx.s(R.string.library_extras_title), Modifier.padding(top = 8.dp, start = 4.dp), style = MaterialTheme.typography.titleMedium, color = Palette.Muted)
    }
    item("companion") { CompanionRow(g, routeKey, focus, open) }
    item("ra") { AchievementsRow(g, routeKey, focus, open) }
    item("manual") { ManualRow(g, routeKey, focus) }
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
            FitText(text, MaterialTheme.typography.labelLarge, color = color)
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
        pkg == null -> Tx.s(R.string.library_extras_none)
        else -> Tx.s(if (meta.companionAppClose) R.string.library_extras_app_closes else R.string.library_extras_app_keeps, label ?: pkg)
    }
    ExtraRow(Tx.s(R.string.library_extras_app), value, pkg != null) {
        ExtraButton(Tx.s(if (pkg == null) R.string.library_extras_choose_app else R.string.library_extras_change), Palette.Mint, routeKey, "app:pick", focus) { open(EditPage.Apps) }
        ExtraButton(Tx.s(if (meta.companionAppClose) R.string.library_extras_close_on else R.string.library_extras_close_off), Palette.Butter, routeKey, "app:close", focus) {
            Store.update(g.key) { it.copy(companionAppClose = !it.companionAppClose) }
        }
        ExtraButton(Tx.s(R.string.library_extras_remove), Palette.Peach, routeKey, "app:remove", focus) {
            if (pkg == null) Toasts.show(Tx.s(R.string.library_extras_no_app)) else Store.update(g.key) { it.copy(companionApp = null) }
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
            Tx.s(R.string.library_extras_app_note),
            Modifier.padding(horizontal = 34.dp), style = MaterialTheme.typography.bodySmall, color = Palette.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
        if (list == null) {
            Text(Tx.s(R.string.library_extras_reading_apps), Modifier.padding(horizontal = 34.dp, vertical = 12.dp), style = MaterialTheme.typography.bodyMedium, color = Palette.Muted)
            return@Column
        }
        // The apps as a grid of icons, the chosen one ticked.
        val state = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
        val current = g.meta.companionApp
        val target = current?.takeIf { c -> list.any { it.packageName == c } } ?: list.firstOrNull()?.packageName
        KeepInView(state, list.indexOfFirst { it.packageName == target }.coerceAtLeast(0))
        androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
            androidx.compose.foundation.lazy.grid.GridCells.Adaptive(116.dp),
            Modifier.fillMaxWidth().weight(1f, fill = false).heightIn(max = 300.dp).fadingEdges(state), state = state,
            contentPadding = PaddingValues(horizontal = 34.dp, vertical = FocusRoom),
            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(list.size, key = { list[it].packageName }) { i ->
                val app = list[i]
                val on = app.packageName == current
                FocusCard(
                    Modifier.height(104.dp).takesFocus(key, app.packageName, app.packageName == target),
                    glow = Palette.Mint, shape = RoundedCornerShape(16.dp),
                    onClick = {
                        Store.update(g.key) { it.copy(companionApp = app.packageName) }
                        Toasts.show(Tx.s(R.string.library_extras_app_starts, app.label, g.title))
                        done()
                    },
                ) {
                    Column(
                        Modifier.matchParentSize().background(Palette.Mint.copy(alpha = if (on) 0.2f else 0.04f)).padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                    ) {
                        AppIcon(app.packageName, Modifier.size(48.dp))
                        Spacer(Modifier.height(6.dp))
                        ShrinkText(app.label, MaterialTheme.typography.labelLarge, Modifier.fillMaxWidth().height(34.dp), maxLines = 2, minScale = 0.75f, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                    if (on) TickBadge(true, Palette.Mint, Modifier.align(Alignment.TopEnd).padding(6.dp), size = 20.dp)
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
        chosen == 0 -> Tx.s(R.string.library_ra_none_chosen)
        chosen != null -> title?.let { Tx.s(R.string.library_ra_chosen_title, it, chosen) } ?: Tx.s(R.string.library_ra_chosen, chosen)
        auto == null -> Tx.s(R.string.library_ra_auto_unknown)
        auto == 0 -> Tx.s(R.string.library_ra_auto_none)
        else -> title?.let { Tx.s(R.string.library_ra_auto_title, it, auto) } ?: Tx.s(R.string.library_ra_auto, auto)
    }
    ExtraRow(Tx.s(R.string.library_ra_row), value, id != null && id > 0) {
        ExtraButton(Tx.s(R.string.library_ra_find), Palette.Mint, routeKey, "ra:find", focus) { open(EditPage.Achievements) }
        ExtraButton(Tx.s(R.string.library_ra_type), Palette.Sky, routeKey, "ra:type", focus) { askRaId(g) }
        ExtraButton(Tx.s(R.string.library_ra_no), Palette.Peach, routeKey, "ra:none", focus) { GameExtras.setRa(g, 0) }
        ExtraButton(Tx.s(R.string.library_ra_automatic), Palette.Lavender, routeKey, "ra:auto", focus) { GameExtras.setRa(g, null) }
    }
}

private fun askRaId(g: Game) {
    TextEntry.ask(Tx.s(R.string.library_ra_ask), g.meta.raId?.takeIf { it > 0 }?.toString().orEmpty()) { typed ->
        val t = typed.trim()
        if (t.isEmpty()) return@ask
        val n = t.toIntOrNull()?.takeIf { it > 0 }
        if (n == null) Toasts.show(Tx.s(R.string.library_ra_not_id))
        else { GameExtras.setRa(g, n); Toasts.show(Tx.s(R.string.library_ra_matched_id, n)) }
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
            hidden -> Tx.s(R.string.library_ra_hidden)
            !signedIn -> Tx.s(R.string.library_ra_signed_out)
            !supported -> Tx.s(R.string.library_ra_unsupported, g.platform.name)
            current == null -> Tx.s(R.string.library_ra_not_matched)
            current == 0 -> Tx.s(R.string.library_ra_currently_none)
            else -> Tx.s(R.string.library_ra_currently, currentTitle ?: Tx.s(R.string.library_ra_game), current)
        }
        Text(note, Modifier.padding(horizontal = 34.dp), style = MaterialTheme.typography.bodySmall, color = Palette.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        val list = found
        val state = rememberLazyListState()
        LazyColumn(
            Modifier.fillMaxWidth().weight(1f, fill = false).heightIn(max = 300.dp).fadingEdges(state), state = state,
            contentPadding = PaddingValues(horizontal = 34.dp, vertical = FocusRoom), verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            // The choices that need nothing from the network come first, and hold the focus while candidates load.
            item("type") { ActionRow(Tx.s(R.string.library_ra_type_long), Palette.Sky, key, "type", true) { askRaId(g); done() } }
            item("none") { ActionRow(Tx.s(R.string.library_ra_no), Palette.Peach, key, "none", false) { GameExtras.setRa(g, 0); done() } }
            item("auto") {
                ActionRow(Tx.s(R.string.library_ra_automatic), Palette.Lavender, key, "auto", false) {
                    GameExtras.setRa(g, null)
                    Toasts.show(Tx.s(R.string.library_ra_auto_from_now))
                    done()
                }
            }
            if (list == null) item("wait") {
                Text(Tx.s(R.string.library_ra_looking), Modifier.padding(vertical = 8.dp), style = MaterialTheme.typography.bodyMedium, color = Palette.Muted)
            } else if (list.isEmpty() && signedIn && supported && !hidden) item("empty") {
                Text(Tx.s(R.string.library_ra_no_close), Modifier.padding(vertical = 8.dp), style = MaterialTheme.typography.bodyMedium, color = Palette.Muted)
            }
            items(list.orEmpty(), key = { "c${it.id}" }) { c ->
                FocusCard(
                    Modifier.fillMaxWidth().takesFocus(key, "c${c.id}", false),
                    glow = Palette.Mint, shape = RoundedCornerShape(14.dp),
                    onClick = { GameExtras.setRa(g, c.id); Toasts.show(Tx.s(R.string.library_ra_matched, c.title)); done() },
                ) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(c.title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (c.byHash) { Pill(Tx.s(R.string.library_ra_same_file), Palette.Mint); Spacer(Modifier.width(6.dp)) }
                        if (c.id == current) { Pill(Tx.s(R.string.library_ra_current), Palette.Butter); Spacer(Modifier.width(6.dp)) }
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

// --- Manual --------------------------------------------------------------------------------------------------------

/**
 * The game's manual or guide (Manuals.kt): the one found by name in ROMs/manuals, or a file picked here, which is
 * copied in next to the others and remembered in the game's details. Picking goes through Android's document
 * picker, like a picture for the artwork (GameEditor.kt), since that is the one way to reach a file from any app.
 */
@Composable
private fun ManualRow(g: Game, routeKey: String, focus: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val meta = g.meta
    val chosen = io.github.zahakj.tandem.FileChecks.file(meta.manual)
    val found = remember(g.key, Manuals.version) { Manuals.foundFor(g) }
    var copying by remember(g.key) { mutableStateOf(false) }
    val value = when {
        copying -> Tx.s(R.string.library_manual_copying)
        chosen != null -> Tx.s(R.string.library_manual_yours, Manuals.describe(chosen))
        meta.manual != null -> found?.let { Tx.s(R.string.library_manual_gone_using, it.name) } ?: Tx.s(R.string.library_manual_gone)
        found != null -> Tx.s(R.string.library_manual_found, Manuals.describe(found))
        else -> Tx.s(R.string.library_manual_none, Manuals.shortPath(Manuals.folderFor(g.platform)))
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        copying = true
        scope.launch {
            val file = withContext(Dispatchers.IO) { Manuals.adopt(ctx, g, uri) }
            copying = false
            Toasts.show(if (file != null) Tx.s(R.string.library_manual_now, file.name, g.title) else Tx.s(R.string.library_manual_bad_file))
        }
    }
    ExtraRow(Tx.s(R.string.library_manual_row), value, chosen != null || found != null) {
        ExtraButton(Tx.s(if (chosen != null) R.string.library_manual_change else R.string.library_manual_choose), Palette.Mint, routeKey, "manual:pick", focus) {
            if (!copying) runCatching { pick.launch(Manuals.pickerTypes) }.onFailure { Toasts.show(Tx.s(R.string.library_manual_no_picker)) }
        }
        if (chosen != null || found != null) ExtraButton(Tx.s(R.string.library_manual_read), Palette.Sky, routeKey, "manual:read", focus) {
            GameEditor.close()
            ManualUi.open(g)
        }
        if (meta.manual != null) ExtraButton(Tx.s(if (found != null) R.string.library_manual_use_found else R.string.library_manual_forget), Palette.Peach, routeKey, "manual:auto", focus) {
            // The file itself stays in ROMs/manuals: it is the user's, and a rescan may well match it by name.
            Store.update(g.key) { it.copy(manual = null) }
            Toasts.show(if (found != null) Tx.s(R.string.library_manual_reading_again, found.name) else Tx.s(R.string.library_manual_forgotten))
        }
    }
}

// --- Logo ----------------------------------------------------------------------------------------------------------

@Composable
private fun LogoRow(g: Game, routeKey: String, focus: (String) -> Unit) {
    val fit = (g.meta.logoFit ?: LogoFit()).tidy()
    val place = LogoFit.label(fit.anchor)
    val value = if (fit.dx != 0 || fit.dy != 0) Tx.s(R.string.library_logo_value_moved, fit.scale, place, fit.dx, fit.dy) else Tx.s(R.string.library_logo_value, fit.scale, place)
    ExtraRow(Tx.s(R.string.library_logo_row), value, !fit.isDefault) {
        ExtraButton(Tx.s(R.string.library_logo_smaller), Palette.Sky, routeKey, "logo:small", focus) { GameExtras.setFit(g) { it.copy(scale = it.scale - LogoFit.STEP) } }
        ExtraButton(Tx.s(R.string.library_logo_bigger), Palette.Sky, routeKey, "logo:big", focus) { GameExtras.setFit(g) { it.copy(scale = it.scale + LogoFit.STEP) } }
        ExtraButton(Tx.s(R.string.library_logo_place, place), Palette.Butter, routeKey, "logo:anchor", focus) {
            GameExtras.setFit(g) { it.copy(anchor = LogoFit.nextAnchor(it.anchor)) }
        }
        ExtraButton("◀", Palette.Lavender, routeKey, "logo:left", focus) { GameExtras.setFit(g) { it.copy(dx = it.dx - 1) } }
        ExtraButton("▶", Palette.Lavender, routeKey, "logo:right", focus) { GameExtras.setFit(g) { it.copy(dx = it.dx + 1) } }
        ExtraButton("▲", Palette.Lavender, routeKey, "logo:up", focus) { GameExtras.setFit(g) { it.copy(dy = it.dy - 1) } }
        ExtraButton("▼", Palette.Lavender, routeKey, "logo:down", focus) { GameExtras.setFit(g) { it.copy(dy = it.dy + 1) } }
        ExtraButton(Tx.s(R.string.library_logo_reset), Palette.Peach, routeKey, "logo:reset", focus) { GameExtras.setFit(g) { LogoFit() } }
    }
}
