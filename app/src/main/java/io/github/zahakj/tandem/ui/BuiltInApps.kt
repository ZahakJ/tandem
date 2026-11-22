package io.github.zahakj.tandem.ui

import androidx.annotation.StringRes
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.zahakj.tandem.Button
import io.github.zahakj.tandem.Launcher
import io.github.zahakj.tandem.Libraries
import io.github.zahakj.tandem.Selected
import io.github.zahakj.tandem.Selection
import io.github.zahakj.tandem.R
import io.github.zahakj.tandem.Tx

/*
 * Tandem's built-in apps: each is a tile on the icon grid (key "tandem:<id>") that
 * can be moved, put in folders and removed like any app, with its own drawn icon (TandemIcons.kt) and a full screen
 * (Route.Tool). Gallery, Journal, Notes, Files and Controller test live in their own files.
 */

class BuiltInApp(
    val id: String,
    @StringRes private val titleRes: Int,
    /** One line about it, shown under its name on the other screen. */
    @StringRes private val blurbRes: Int,
    val icon: TIcon,
    val color: Color,
    /** On the home grid by default (Settings is only there when put there, for example by the Cocoon import). */
    val onHome: Boolean = true,
    val open: (Context) -> Unit,
) {
    val key: String get() = "tandem:$id"
    val title: String get() = Tx.s(titleRes)
    val blurb: String get() = Tx.s(blurbRes)
    val art: TileArt get() = TileArt.Tandem(icon, color)
}

object BuiltInApps {
    val all: List<BuiltInApp> = listOf(
        BuiltInApp("gallery", R.string.apps_gallery_title, R.string.apps_gallery_blurb, TIcon.Gallery, Color(0xFFF07F62)) { Nav.go(Route.Tool("gallery")) },
        BuiltInApp("journal", R.string.apps_journal_title, R.string.apps_journal_blurb, TIcon.Journal, Color(0xFFD9A03C)) { Nav.go(Route.Tool("journal")) },
        BuiltInApp("notes", R.string.apps_notes_title, R.string.apps_notes_blurb, TIcon.Notes, Color(0xFF5FB58C)) {
            NotesUi.reset(); Nav.go(Route.Tool("notes"))
        },
        BuiltInApp("themes", R.string.apps_themes_title, R.string.apps_themes_blurb, TIcon.Themes, Color(0xFF9A6CF0)) { Nav.go(Route.Appearance) },
        BuiltInApp("stats", R.string.apps_stats_title, R.string.apps_stats_blurb, TIcon.Stats, Color(0xFF2EB5A6)) { StatsTabs.tab = StatsTabs.OVERVIEW; Nav.go(Route.Stats) },
        BuiltInApp("achievements", R.string.apps_achievements_title, R.string.apps_achievements_blurb, TIcon.Trophy, Color(0xFFE6B92E)) {
            StatsTabs.tab = StatsTabs.TROPHIES; Nav.go(Route.Stats)
        },
        BuiltInApp("upnext", R.string.apps_upnext_title, R.string.apps_upnext_blurb, TIcon.UpNext, Color(0xFF4F8CF2)) { Nav.go(Route.Tool("upnext")) },
        BuiltInApp("files", R.string.apps_files_title, R.string.apps_files_blurb, TIcon.Files, Color(0xFF7C8AA8)) { Nav.go(Route.Tool("files")) },
        BuiltInApp("pad", R.string.apps_pad_title, R.string.apps_pad_blurb, TIcon.Controller, Color(0xFF4FBF6A)) { Nav.go(Route.Tool("pad")) },
        BuiltInApp("settings", R.string.apps_settings_title, R.string.apps_settings_blurb, TIcon.Gear, Color(0xFF8A94B8), onHome = false) { Nav.go(Route.Settings) },
    )

    fun byId(id: String): BuiltInApp? = all.firstOrNull { it.id == id }
}

/** The screen for a built-in app. */
@Composable
fun ToolScreen(route: Route.Tool) {
    when (route.id) {
        "gallery" -> GalleryScreen()
        "journal" -> JournalScreen()
        "notes" -> NotesScreen()
        "upnext" -> UpNextScreen()
        "files" -> FilesScreen()
        "pad" -> ControllerTestScreen()
        else -> PipNote(Tx.s(R.string.apps_gone_title), Tx.s(R.string.apps_gone), mood = 2)
    }
}

/** True while the built-in app's screen is in front with no popup over it (so it may use X and Y). */
fun toolActive(id: String): Boolean = Nav.route == Route.Tool(id) && !Nav.overlayOpen && Layers.top == null

/**
 * The layout every built-in app shares: on the left its floating icon, name, a line about it, facts and button
 * hints; on the right its content. Fits the top screen with the top bar showing.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ToolScaffold(
    app: BuiltInApp,
    subtitle: String?,
    hints: @Composable ColumnScope.() -> Unit,
    sideWidth: Dp = 210.dp,
    content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit,
) {
    Row(Modifier.fillMaxSize().padding(start = 22.dp, end = 10.dp, bottom = 6.dp)) {
        Column(Modifier.width(sideWidth).fillMaxHeight().padding(top = 10.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                Modifier.size(84.dp).floating(app.id.hashCode(), bob = 4.dp)
                    .graphicsLayer { shape = RoundedCornerShape(22.dp); clip = true; shadowElevation = 14f * density; spotShadowColor = app.color },
            ) { TandemIconTile(app.icon, app.color) }
            Spacer(Modifier.height(6.dp))
            Text(app.title, Modifier.riseIn(app.id, 0, 12.dp), style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(app.blurb, Modifier.riseIn(app.id, 1, 12.dp), style = MaterialTheme.typography.bodyMedium, color = Palette.Muted, maxLines = 3, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Pill(subtitle, app.color)
            Spacer(Modifier.weight(1f))
            hints()
        }
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f).fillMaxHeight()) { content() }
    }
}

/** A row in a built-in app's list: art on the left, a title and a line under it, an optional tag on the right. */
@Composable
fun ToolRow(
    title: String,
    detail: String?,
    color: Color,
    modifier: Modifier,
    tag: String? = null,
    height: Dp = 62.dp,
    onFocus: () -> Unit = {},
    onClick: () -> Unit,
    art: @Composable () -> Unit,
) {
    FocusCard(modifier.fillMaxWidth().height(height), glow = color, shape = RoundedCornerShape(16.dp), onFocus = onFocus, entry = true, onClick = onClick) {
        Row(Modifier.fillMaxSize().padding(horizontal = 9.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(height - 18.dp).clip(RoundedCornerShape(10.dp))) { art() }
            Column(Modifier.weight(1f).padding(start = 12.dp, end = 8.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!detail.isNullOrBlank()) Text(detail, style = MaterialTheme.typography.labelMedium, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (tag != null) Pill(tag, color)
        }
    }
}

// --- Up next ---------------------------------------------------------------------------------------------------

/** The Up next list as a full screen: A plays (or edits a note), Y takes it off, X adds one. */
@Composable
fun UpNextScreen() {
    val ctx = LocalContext.current
    val app = BuiltInApps.byId("upnext")!!
    val route = Route.Tool("upnext").key
    val entries = remember(io.github.zahakj.tandem.data.WidgetStore.config, Libraries.all, io.github.zahakj.tandem.Privacy.view) { UpNext.shown() }
    // Keyed by the entry itself (not its place), so after a removal the highlight goes to the neighbour.
    val keys = entries.map { (e, _) -> "${e.added}:${e.key ?: e.note}" }
    val target = rememberFocusTarget(route, keys)
    InterceptButtons(toolActive("upnext")) { b ->
        when (b) {
            Button.X -> { UpNext.ask(); true }
            Button.Y -> {
                val i = keys.indexOf(FocusMemory.get(route))
                entries.getOrNull(i)?.let { (e, g) -> UpNext.removeWithUndo(e, if (g != null) Tx.s(R.string.apps_upnext_took_off, g.title) else Tx.s(R.string.apps_upnext_took_off_note)) }
                true
            }
            Button.Select -> { val n = UpNext.clearPlayed(); Toasts.show(if (n == 0) Tx.s(R.string.apps_upnext_none_played) else Tx.p(R.plurals.apps_upnext_cleared, n)); true }
            else -> false
        }
    }
    ToolScaffold(app, Tx.p(R.plurals.apps_upnext_entries, entries.size), hints = {
        ButtonHint("A", Tx.s(R.string.apps_upnext_play), Palette.Mint)
        ButtonHint("X", Tx.s(R.string.apps_upnext_add), Palette.Sky)
        ButtonHint("Y", Tx.s(R.string.apps_upnext_remove), Palette.Peach)
        ButtonHint("Select", Tx.s(R.string.apps_upnext_clear_played), Palette.Lavender)
    }) {
        if (entries.isEmpty()) {
            EmptyNote(
                Tx.s(R.string.apps_upnext_empty), Tx.s(R.string.apps_upnext_add_one),
                Modifier.takesFocus(route, "empty", true), Modifier.align(Alignment.Center), mood = 0,
            ) { UpNext.ask() }
        } else {
            val state = rememberRouteListState(route)
            KeepInView(state, keys.indexOf(target))
            LazyColumn(
                Modifier.fillMaxSize().fadingEdges(state), state = state,
                contentPadding = PaddingValues(horizontal = FocusRoomSide, vertical = FocusRoom), verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(entries, key = { i, _ -> keys[i] }) { i, (e, g) ->
                    val played = UpNext.played(e, g)
                    ToolRow(
                        g?.title ?: e.note.orEmpty(), g?.platform?.name ?: Tx.s(R.string.apps_upnext_note), g?.let { SystemColors.of(it.platform.id) } ?: Palette.Butter,
                        Modifier.takesFocus(route, keys[i], keys[i] == target), tag = if (played) Tx.s(R.string.apps_upnext_played) else "#${i + 1}",
                        onFocus = { Selection.current = if (g != null) Selected.GameItem(g) else Selected.Home },
                        onClick = { if (g != null) Launcher.launch(ctx, g) else UpNext.editNote(e) },
                    ) {
                        if (g != null) GameArt(g) else TandemIconTile(TIcon.Note, Palette.Butter)
                    }
                }
            }
        }
    }
}

/** The chips a tool screen shows across its top (filters, groups), focusable with the D-pad. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ToolChips(route: String, chips: List<Pair<String, String>>, selected: String, target: String?, color: Color, onPick: (String) -> Unit) {
    FlowRow(Modifier.fillMaxWidth().padding(horizontal = FocusRoomSide, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for ((id, label) in chips) {
            val on = id == selected
            FocusCard(
                Modifier.height(34.dp).takesFocus(route, "chip:$id", "chip:$id" == target), glow = color, shape = RoundedCornerShape(50),
                onClick = { onPick(id) },
            ) {
                Box(
                    Modifier.fillMaxHeight().background(if (on) color.copy(alpha = 0.3f) else Color.Transparent).padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(label, style = MaterialTheme.typography.labelLarge, color = if (on) Palette.Text else Palette.Muted, maxLines = 1) }
            }
        }
    }
}
