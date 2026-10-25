package io.github.zahakj.tandem.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import io.github.zahakj.tandem.AndroidApps
import io.github.zahakj.tandem.Game
import io.github.zahakj.tandem.LaunchDisplay
import io.github.zahakj.tandem.Libraries
import io.github.zahakj.tandem.RunningGames
import io.github.zahakj.tandem.data.AppPrefs
import io.github.zahakj.tandem.data.AppScreen
import io.github.zahakj.tandem.data.HomeStore
import io.github.zahakj.tandem.data.IconGridStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/*
 * One menu for an Android app wherever it shows: the dock, the All apps drawer, the Android apps screen and (through
 * [appMenuActions]) the icon grid. Open, open on the other screen once, which screen it always opens on, home and
 * dock, a name and icon of your own, Android Games, hiding it from the drawer, App info and Uninstall.
 */

/** Choosing a picture in Tandem's Files or Gallery for someone else (an app's icon). */
object ImagePick {
    /** [accept]: the files A picks (pictures unless said otherwise); [what] names them in the button hint. */
    class Request(val title: String, val onPick: (File) -> Unit, val accept: (File) -> Boolean = ImagePick::isImage, val what: String = "picture")

    var request by mutableStateOf<Request?>(null)
        private set

    private val IMAGE = setOf("png", "jpg", "jpeg", "webp")

    fun isImage(f: File): Boolean = f.isFile && f.extension.lowercase() in IMAGE

    /** Whether A on [f] picks it for the open request. */
    fun accepts(f: File): Boolean = f.isFile && request?.accept?.invoke(f) == true

    /** Opens [tool] ("files" or "gallery") to pick a picture; [onPick] gets it, and Tandem comes back here. */
    fun start(tool: String, title: String, onPick: (File) -> Unit) = start(tool, Request(title, onPick))

    /** Opens [tool] to pick a file of another kind (a game's jingle, say). */
    fun start(tool: String, r: Request) {
        request = r
        FocusMemory.forget(Route.Tool(tool).key)
        Nav.go(Route.Tool(tool))
    }

    fun pick(f: File) {
        val r = request ?: return
        request = null
        Nav.back()
        r.onPick(f)
    }

    fun cancel() { request = null }
}

/** What the app menu does, shared by every place an app shows. */
object AppActions {
    /** The app whose icon is being chosen (the icon chooser is open). */
    var iconFor by mutableStateOf<String?>(null)

    /** The Android Games game for [pkg], when it is one. */
    fun gameOf(pkg: String): Game? =
        Libraries.byId(AndroidApps.PLATFORM_ID)?.games?.firstOrNull { AndroidApps.packageOf(it) == pkg }

    /** Whether [pkg] is in Android Games: the user's choice, else what the library found. */
    fun isGame(pkg: String): Boolean = AppPrefs.of(pkg).game ?: (gameOf(pkg) != null)

    /** Whether Open starts [pkg] on the bottom screen now. */
    fun opensOnBottom(pkg: String): Boolean = gameOf(pkg)?.let { LaunchDisplay.wantsBottom(it) } ?: LaunchDisplay.appWantsBottom(pkg)

    fun toggleGame(ctx: Context, pkg: String) {
        val now = isGame(pkg)
        AppPrefs.update(pkg) { it.copy(game = !now) }
        val label = RunningGames.appLabel(ctx, pkg)
        Toasts.show(if (now) "Took $label out of Android Games" else "$label is in Android Games now")
        LibraryScan.request(ctx, announce = false, onlyIfChanged = false)
    }

    fun rename(ctx: Context, pkg: String) {
        val shown = RunningGames.appLabel(ctx, pkg)
        // The app's own name: typing it (or nothing) goes back to it.
        val original = runCatching { ctx.packageManager.let { pm -> pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() } }.getOrDefault(shown)
        TextEntry.ask("Name for $shown", shown) { typed ->
            val name = typed.trim().takeIf { it.isNotEmpty() && it != original }
            AppPrefs.update(pkg) { it.copy(name = name) }
            Toasts.show(if (name == null) "Back to its own name" else "Renamed to $name")
            if (gameOf(pkg) != null) LibraryScan.request(ctx, announce = false, onlyIfChanged = false)
        }
    }

    fun setArt(ctx: Context, pkg: String, file: File?) {
        AppPrefs.update(pkg) { it.copy(art = file?.path) }
        Toasts.show(if (file == null) "Back to the app's own icon" else "New icon for ${RunningGames.appLabel(ctx, pkg)}")
    }

    fun uninstall(ctx: Context, pkg: String) {
        runCatching {
            ctx.startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure { Toasts.show("Android's uninstaller won't open") }
    }

    fun info(ctx: Context, pkg: String) = AndroidApps.openInfo(ctx, pkg)
}

/**
 * The actions for app [pkg]. [done] leaves the menu and whatever it is over (an app is opening, another screen is
 * coming); [close] returns to what is under it. [extra] (the dock's Move left / right) follows Open.
 */
fun appMenuActions(
    ctx: Context,
    pkg: String,
    done: () -> Unit = { Panels.close() },
    close: () -> Unit = { Panels.closeAppMenu() },
    extra: List<MenuAction> = emptyList(),
): List<MenuAction> = buildList {
    val dual = LaunchDisplay.dualScreen(ctx)
    val bottom = AppActions.opensOnBottom(pkg)
    add(MenuAction("open", "Open", Glyph.Play, Palette.Mint, value = if (dual) (if (bottom) "Bottom" else "Top") else null,
        help = if (dual) "The screen it opens on now." else null) { done(); Dock.open(ctx, pkg) })
    val builtIn = Dock.builtIn(pkg) != null
    if (dual && !builtIn) add(MenuAction("other", if (bottom) "Open on the top screen" else "Open on the bottom screen", Glyph.Screens, Palette.Sky) {
        done(); Dock.open(ctx, pkg, bottom = !bottom)
    })
    addAll(extra)
    if (dual && !builtIn) {
        val own = AppPrefs.of(pkg).screen
        val shown = own?.let(AppScreen::label) ?: if (AppPrefs.data.defaultScreen == AppScreen.AUTO) "Auto" else "Default"
        add(MenuAction("screen", "Always open on", Glyph.Screens, Palette.Butter, value = shown,
            help = "The screen this app always opens on. Tap for Top, then Bottom, then the default for every app.") {
            val next = when (own) { null -> AppScreen.TOP; AppScreen.TOP -> AppScreen.BOTTOM; else -> null }
            AppPrefs.update(pkg) { it.copy(screen = next) }
        })
    }
    if (IconGrid.enabled) {
        val key = "app:$pkg"
        val onHome = key in IconGridStore.config.added || IconGridStore.inFolders().contains(key)
        add(MenuAction("home", if (onHome) "Remove from home" else "Add to home", Glyph.Star, Palette.Butter, value = if (onHome) "★" else null) {
            if (onHome) IconGridStore.remove(key, default = false) else IconGridStore.add(key, default = false)
            Toasts.show(if (onHome) "Taken off the home grid" else "On the home grid now ★")
        })
    }
    val docked = pkg in (HomeStore.settings.dock ?: Dock.pinned(ctx))
    add(MenuAction("dock", if (docked) "Unpin from dock" else "Pin to dock", Glyph.Home, Palette.Sky, value = if (docked) "✓" else null) {
        Dock.togglePin(ctx, pkg)
    })
    if (builtIn) return@buildList
    add(MenuAction("rename", "Rename", Glyph.Pencil, Palette.Butter, value = if (AppPrefs.name(pkg) != null) "Yours" else null) {
        close(); AppActions.rename(ctx, pkg)
    })
    add(MenuAction("icon", "Choose icon", Glyph.Image, Palette.Butter, value = if (AppPrefs.art(pkg) != null) "Yours" else null) {
        close(); FocusMemory.forget("appicon:$pkg"); AppActions.iconFor = pkg
    })
    val game = AppActions.isGame(pkg)
    add(MenuAction("game", "Treat as game", Glyph.Gamepad, Palette.Peach, value = if (game) "✓" else null, more = true) { AppActions.toggleGame(ctx, pkg) })
    val hidden = AppPrefs.of(pkg).hidden
    add(MenuAction("hide", if (hidden) "Show in the drawer" else "Hide from drawer", Glyph.Eye, Palette.Lavender, more = true) {
        AppPrefs.update(pkg) { it.copy(hidden = !hidden) }
        Toasts.show(if (hidden) "Back in All apps" else "Hidden from All apps. Show hidden apps brings it back")
    })
    add(MenuAction("info", "App info", Glyph.Info, Palette.Lavender, more = true) { done(); AppActions.info(ctx, pkg) })
    add(MenuAction("uninstall", "Uninstall…", Glyph.Trash, Palette.Peach, more = true) { done(); AppActions.uninstall(ctx, pkg) })
}

/**
 * Y on an app in the dock, the drawer or the Android apps screen: the app lifts out with [appMenuActions] beside
 * it. Rendered once in the app root ([HomeHost]), over the drawer when it was opened there.
 */
@Composable
fun AppActionMenu() {
    val ctx = LocalContext.current
    val open = Panels.appMenu != null
    val shown = rememberLatest(Panels.appMenu)
    PanelGuard(open)
    ActionMenuHost(open, onClose = { Panels.closeAppMenu() }) spec@{
        val pkg = shown ?: return@spec null
        // In the dock: its place can change too.
        val dock = HomeStore.settings.dock ?: Dock.pinned(ctx)
        val moves = if (!Panels.apps && pkg in dock) listOf(
            MenuAction("left", "Move left", Glyph.Back, Palette.Sky, value = "◀") { Dock.move(ctx, pkg, -1) },
            MenuAction("right", "Move right", Glyph.Play, Palette.Sky, value = "▶") { Dock.move(ctx, pkg, 1) },
        ) else emptyList()
        val subtitle = if (Dock.builtIn(pkg) != null) "Tandem" else if (AppActions.isGame(pkg)) "Android game" else "App"
        MenuSpec("appmenu:$pkg", RunningGames.appLabel(ctx, pkg), subtitle, Palette.Sky, appMenuActions(ctx, pkg, extra = moves)) { AppTileIcon(pkg, 120.dp) }
    }
}

/** A pack's system icon offered as an app icon. */
private class PackIcon(val name: String, val file: File)

/** Choose icon: a picture from Files or the Gallery, a pack's icon, or the app's own again. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppIconChooser() {
    val ctx = LocalContext.current
    val pkg = AppActions.iconFor
    val shown = rememberLatest(pkg)
    val close: () -> Unit = { AppActions.iconFor = null; Nav.focusTick++ }
    PanelGuard(pkg != null)
    OverlayPanel(pkg != null, onClose = close) {
        val p = shown ?: return@OverlayPanel
        val label = RunningGames.appLabel(ctx, p)
        val icons by produceState<List<PackIcon>?>(null, p) {
            value = withContext(Dispatchers.IO) {
                runCatching {
                    (ThemePacks.packs.map { it.art } + ThemePacks.assetPacks.map { it.art })
                        .flatMap { it.icons() }.distinctBy { it.second.path }
                        .sortedBy { it.first.lowercase() }.take(400).map { PackIcon(it.first, it.second) }
                }.getOrDefault(emptyList())
            }
        }
        val routeKey = "appicon:$p"
        val pick: (String) -> Unit = { tool ->
            AppActions.iconFor = null
            Panels.close()
            ImagePick.start(tool, "Pick an icon for $label") { f -> AppActions.setArt(ctx, p, f) }
        }
        val own = AppPrefs.art(p) != null
        val buttons = listOfNotNull("files" to "From Files", "gallery" to "From Gallery", ("own" to "App's own icon").takeIf { own })
        val keys = buttons.map { "b:${it.first}" } + icons.orEmpty().map { it.file.path }
        val target = rememberFocusTarget(routeKey, keys)
        PanelSurface(Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp))) { AppIcon(p, Modifier.fillMaxSize()) }
                Spacer(Modifier.width(12.dp))
                Text("Icon for $label", Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                ButtonHint("B", "Close", Palette.Lavender)
            }
            val grid = rememberLazyGridState()
            LazyVerticalGrid(
                GridCells.Adaptive(92.dp),
                Modifier.fadingEdges(grid),
                state = grid,
                contentPadding = PaddingValues(horizontal = FocusRoomSide, vertical = FocusRoom),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item("buttons", span = { GridItemSpan(maxLineSpan) }) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        buttons.forEach { (id, text) ->
                            FocusCard(
                                Modifier.height(44.dp).takesFocus(routeKey, "b:$id", target == "b:$id"),
                                glow = Palette.Sky, shape = RoundedCornerShape(14.dp),
                                onClick = { if (id == "own") { close(); AppActions.setArt(ctx, p, null) } else pick(id) },
                            ) {
                                Box(Modifier.fillMaxSize().padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
                                    Text(text, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                                }
                            }
                        }
                    }
                }
                val list = icons
                item("packs", span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        when { list == null -> "Looking through your icon packs…"; list.isEmpty() -> "No icon packs found. Theme packs with system icons show theirs here"; else -> "From your icon packs" },
                        style = MaterialTheme.typography.labelLarge, color = Palette.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                }
                items(list.orEmpty(), key = { it.file.path }) { icon ->
                    FocusCard(
                        Modifier.aspectRatio(0.86f).takesFocus(routeKey, icon.file.path, target == icon.file.path),
                        glow = Palette.Butter,
                        onClick = { close(); AppActions.setArt(ctx, p, icon.file) },
                    ) {
                        Column(Modifier.fillMaxSize().padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            AsyncImage(rememberSizedRequest(icon.file, 160), null, Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)), contentScale = ContentScale.Crop)
                            Spacer(Modifier.height(4.dp))
                            Text(icon.name, Modifier.fillMaxWidth(), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}
