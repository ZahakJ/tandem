package io.github.zahakj.tandem.ui

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.LruCache
import android.util.SizeF
import android.view.View
import android.view.ViewGroup
import android.widget.RemoteViews
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.zahakj.tandem.Emulators
import io.github.zahakj.tandem.MainActivity
import io.github.zahakj.tandem.data.AppWidgetEntry
import io.github.zahakj.tandem.data.AppWidgetFit
import io.github.zahakj.tandem.data.AppWidgetGroup
import io.github.zahakj.tandem.data.AppWidgetGroups
import io.github.zahakj.tandem.data.AppWidgetRef
import io.github.zahakj.tandem.data.GridShown
import io.github.zahakj.tandem.data.GridSpan
import io.github.zahakj.tandem.data.IconGridStore
import io.github.zahakj.tandem.data.Widget
import io.github.zahakj.tandem.data.WidgetPlace
import io.github.zahakj.tandem.data.WidgetSize
import io.github.zahakj.tandem.data.WidgetStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/*
 * Android app widgets (weather, a calendar, a music player) hosted among Tandem's own: in the home widget row, on
 * the bottom screen and in the icon grid's cells, through one AppWidgetHost for the process, listening while the
 * library is in front (MainActivity.onStart / onStop). Adding one goes through Android: the user picks a provider
 * (AppWidgetPicker), Android asks once whether Tandem may host widgets, the provider's own set-up screen runs when it
 * has one (AppWidgetAdd), and the widget is then stored like any other, with Android's id in its options
 * (data/AppWidgets.kt). Its size comes from the provider's minimum size (AppWidgetFit) and can be changed like any
 * other widget's; the view is told the size it really gets, so the provider lays out for it.
 */

/** The kind every hosted Android widget is. On screen it goes by its provider's name. */
val AppWidgetKind = WidgetKind(
    AppWidgetRef.TYPE, "Android widget", "A widget from one of your apps: weather, a calendar, music and more.", "▣", Palette.Sky,
    listOf(WidgetSize.Small, WidgetSize.Medium, WidgetSize.Wide, WidgetSize.Tall), WidgetSize.Medium,
    label = { w -> AppWidgetRef.label(w) ?: "Android widget" },
    actions = { w -> listOfNotNull(AppWidgetAdd.setUpAction(w)) },
    content = { s, m -> AppWidgetContent(s, m) },
)

/** Where a picked widget goes: a widget area, a spot on the home grid, or a spot in a folder (one cell). */
sealed class AppWidgetTarget {
    class Area(val area: WidgetArea) : AppWidgetTarget()
    class GridAt(val route: String, val at: Int) : AppWidgetTarget()
    class FolderAt(val route: String, val folder: String, val shown: GridShown, val at: Int) : AppWidgetTarget()
}

/** The one AppWidgetHost, the views it makes and what is known about the installed providers. */
object AppWidgetHosting {
    /** Stable: Android keeps the widgets bound to this host id across restarts and updates. */
    private const val HOST_ID = 0x7a6d

    private var host: Host? = null

    /** Bumped when the host is made anew (for a new activity), so hosted views are made again against it. */
    var generation by mutableIntStateOf(0)
        private set

    /** Bumped when a provider changes or goes, so hosted widgets look at theirs again. */
    var providersTick by mutableIntStateOf(0)
        private set

    private class Host(val owner: Activity) : AppWidgetHost(owner, HOST_ID) {
        override fun onCreateView(context: Context, appWidgetId: Int, appWidget: AppWidgetProviderInfo?): AppWidgetHostView =
            RelayView(context, appWidgetId)

        override fun onProvidersChanged() { providersTick++ }

        override fun onProviderChanged(appWidgetId: Int, appWidget: AppWidgetProviderInfo?) {
            super.onProviderChanged(appWidgetId, appWidget)
            providersTick++
        }
    }

    /** Called from MainActivity.onStart: updates flow while the library is in front. A new activity gets a host of its own. */
    fun start(activity: Activity) {
        val h = host?.takeIf { it.owner === activity } ?: run {
            host?.let { old -> runCatching { old.stopListening() } }
            Host(activity).also { host = it; generation++ }
        }
        runCatching { h.startListening() }.onFailure { Log.w("Tandem", "Android widgets: can't start listening", it) }
    }

    /** Called from MainActivity.onStop: nothing updates while a game plays. */
    fun stop() {
        host?.let { runCatching { it.stopListening() } }
    }

    fun manager(ctx: Context): AppWidgetManager? = runCatching { AppWidgetManager.getInstance(ctx) }.getOrNull()

    /** The provider behind Android's widget [id], or null when it is gone (uninstalled) or the id isn't bound. */
    fun info(ctx: Context, id: Int): AppWidgetProviderInfo? = manager(ctx)?.let { m -> runCatching { m.getAppWidgetInfo(id) }.getOrNull() }

    fun allocate(): Int? = host?.let { h -> runCatching { h.allocateAppWidgetId() }.getOrNull() }

    fun delete(id: Int) { host?.let { h -> runCatching { h.deleteAppWidgetId(id) } } }

    /** Starts the provider's set-up screen for [id]; Android lends it the permission that needs. False when it can't start. */
    fun configure(activity: Activity, id: Int, requestCode: Int): Boolean {
        val h = host ?: return false
        return runCatching { h.startAppWidgetConfigureActivityForResult(activity, id, 0, requestCode, null); true }
            .getOrElse { Log.w("Tandem", "Android widgets: set-up screen won't start", it); false }
    }

    // --- Views -------------------------------------------------------------------------------------------------------

    /** Every hosted view of each widget id, on either screen and in previews. Guarded by itself. */
    private val views = HashMap<Int, LinkedHashSet<RelayView>>()

    /** A view for widget [id], with its current content, kept up to date while the host listens. */
    fun view(id: Int, info: AppWidgetProviderInfo): RelayView? {
        val h = host ?: return null
        val v = runCatching { h.createView(h.owner, id, info) as RelayView }
            .getOrElse { Log.w("Tandem", "Android widgets: can't make a view for $id", it); return null }
        // Android pads a widget's view for other launchers' grids; Tandem's card is the frame here.
        v.setPadding(0, 0, 0, 0)
        synchronized(views) { views.getOrPut(id) { LinkedHashSet() }.add(v) }
        v.made = true
        return v
    }

    fun release(v: RelayView) {
        synchronized(views) { views[v.widgetId]?.let { set -> set.remove(v); if (set.isEmpty()) views.remove(v.widgetId) } }
    }

    internal fun siblings(v: RelayView): List<RelayView> = synchronized(views) { views[v.widgetId]?.filter { it !== v } ?: emptyList() }

    /** A on a hosted widget: a click on the first thing in it that takes clicks (usually its main action), else nothing. */
    fun click(v: View): Boolean = firstClickable(v)?.performClick() ?: false

    private fun firstClickable(v: View): View? {
        if (v.isClickable && v.isShown && v !is AppWidgetHostView) return v
        if (v is ViewGroup) for (i in 0 until v.childCount) firstClickable(v.getChildAt(i))?.let { return it }
        return null
    }

    /** Tells the provider the size (dp) its widget really gets, so it lays out for it. */
    fun tellSize(v: AppWidgetHostView, w: Float, h: Float) {
        if (w <= 0f || h <= 0f) return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) v.updateAppWidgetSize(Bundle(), listOf(SizeF(w, h)))
            else @Suppress("DEPRECATION") v.updateAppWidgetSize(Bundle(), w.toInt(), h.toInt(), w.toInt(), h.toInt())
        }
    }

    // --- Providers ---------------------------------------------------------------------------------------------------

    /** What the picker lists: the apps' widgets in order, and each provider's details by its name. */
    class Catalog(val groups: List<AppWidgetGroup>, val infos: Map<String, AppWidgetProviderInfo>) {
        val isEmpty: Boolean get() = groups.isEmpty()
    }

    /** Every home screen widget the installed apps offer. Blocking (the package manager): off the main thread. */
    fun catalog(ctx: Context): Catalog {
        val pm = ctx.packageManager
        val density = ctx.resources.displayMetrics.density
        val list = manager(ctx)?.let { m -> runCatching { m.installedProviders }.getOrNull() } ?: return Catalog(emptyList(), emptyMap())
        val infos = HashMap<String, AppWidgetProviderInfo>()
        val entries = list.filter { it.widgetCategory and AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN != 0 }.map { info ->
            val pkg = info.provider.packageName
            val app = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
            val label = runCatching { info.loadLabel(pm) }.getOrNull()?.takeIf { it.isNotBlank() } ?: app
            val name = info.provider.flattenToShortString()
            infos[name] = info
            AppWidgetEntry(name, pkg, app, label, (info.minWidth / density).toInt(), (info.minHeight / density).toInt(), info.configure != null)
        }
        return Catalog(AppWidgetGroups.group(entries), infos)
    }

    private val previews = LruCache<String, ImageBitmap>(48)

    fun cachedPreview(provider: String): ImageBitmap? = previews.get(provider)

    /** The provider's preview picture (its icon when it has none), small. Blocking: off the main thread. */
    fun preview(ctx: Context, provider: String, info: AppWidgetProviderInfo): ImageBitmap? {
        previews.get(provider)?.let { return it }
        val d: Drawable = runCatching { info.loadPreviewImage(ctx, 0) }.getOrNull()
            ?: runCatching { info.loadIcon(ctx, 0) }.getOrNull() ?: return null
        val maxW = 360; val maxH = 220
        var w = d.intrinsicWidth.takeIf { it > 0 } ?: maxW
        var h = d.intrinsicHeight.takeIf { it > 0 } ?: maxH
        val scale = minOf(maxW.toFloat() / w, maxH.toFloat() / h, 1f)
        w = (w * scale).toInt().coerceAtLeast(1); h = (h * scale).toInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        d.setBounds(0, 0, w, h)
        d.draw(Canvas(bmp))
        return bmp.asImageBitmap().also { previews.put(provider, it) }
    }

    /**
     * After a return or a provider change: a widget whose provider Android no longer knows (its app uninstalled, or
     * its id already given back) goes, with a word; and ids the host still holds that no widget uses any more (a
     * reset to the default set, an Undo that never came) are given back to Android.
     */
    fun sweep(ctx: Context) {
        val m = manager(ctx) ?: return
        val used = HashSet<Int>()
        for (w in WidgetStore.widgets.filter { it.type == AppWidgetRef.TYPE }) {
            val id = AppWidgetRef.id(w) ?: continue
            used += id
            if (runCatching { m.getAppWidgetInfo(id) }.getOrNull() != null) continue
            val name = AppWidgetKind.label(w)
            if (w.place == WidgetPlace.Grid) IconGridStore.remove(GridWidgets.key(w.id), default = false)
            Widgets.remove(w.id)
            delete(id)
            Toasts.show("$name isn't available any more, so it was taken off")
        }
        // Never while a widget is being added: its id isn't stored until Android has set it up.
        if (AppWidgetAdd.pending != null) return
        val h = host ?: return
        runCatching { h.appWidgetIds }.getOrNull()?.filter { it !in used }?.forEach { delete(it) }
    }
}

/**
 * A hosted widget's view. Android sends a widget's updates to the view made last for its id; with the same widget on
 * both screens, or shown in a preview too, that view passes them on to the others, so every copy stays current.
 * Nothing inside may take the controller's focus: that stays in Compose. Taps still reach the widget.
 */
class RelayView(ctx: Context, val widgetId: Int) : AppWidgetHostView(ctx) {
    /** Set once the host has made it; the content it is born with isn't passed on. */
    internal var made = false
    private var receiving = false

    init {
        descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        isFocusable = false
        isFocusableInTouchMode = false
    }

    // Only the content updates can be passed on: the SDK hides a provider change (the copies are made again instead,
    // see HostedWidget) and a collection's "data changed" nudge (the newest copy refreshes; the others on their next update).
    override fun updateAppWidget(remoteViews: RemoteViews?) {
        super.updateAppWidget(remoteViews)
        pass { it.updateAppWidget(remoteViews) }
    }

    private fun pass(what: (RelayView) -> Unit) {
        if (receiving || !made) return
        for (v in AppWidgetHosting.siblings(this)) {
            v.receiving = true
            try { what(v) } catch (e: Exception) { Log.w("Tandem", "Android widgets: a copy didn't take an update", e) } finally { v.receiving = false }
        }
    }
}

/** Adding a widget: Android's permission to host it, the provider's set-up screen, then its place in Tandem. */
object AppWidgetAdd {
    private const val REQ_BIND = 0x7a01
    private const val REQ_CONFIGURE = 0x7a02
    private const val REQ_RECONFIGURE = 0x7a03

    class Pending(val id: Int, val entry: AppWidgetEntry, val info: AppWidgetProviderInfo, val target: AppWidgetTarget)

    /** The widget being added, between the pick and its place; its id isn't stored yet. */
    var pending: Pending? = null
        private set

    fun begin(activity: Activity, entry: AppWidgetEntry, info: AppWidgetProviderInfo, target: AppWidgetTarget) {
        if (pending != null) return
        val id = AppWidgetHosting.allocate() ?: run { Toasts.show("Android widgets aren't available right now"); Sounds.error(); return }
        pending = Pending(id, entry, info, target)
        // Allowed once the user ticked "always allow" in Android's question; otherwise Android asks now.
        val m = AppWidgetHosting.manager(activity)
        val bound = m != null && runCatching { m.bindAppWidgetIdIfAllowed(id, info.profile, info.provider, null) }.getOrDefault(false)
        if (bound) { configureOrFinish(activity); return }
        val ask = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, info.profile)
        @Suppress("DEPRECATION")
        runCatching { activity.startActivityForResult(ask, REQ_BIND) }.onFailure { cancel("Android wouldn't ask about the widget") }
    }

    /** The set-up screen, unless the provider does without one (or says it is optional); then the widget goes in. */
    private fun configureOrFinish(activity: Activity) {
        val p = pending ?: return
        if (p.info.configure == null || p.info.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL != 0) { finish(); return }
        if (!AppWidgetHosting.configure(activity, p.id, REQ_CONFIGURE)) finish()
    }

    /** MainActivity.onActivityResult: true when the result was one of these. */
    fun onActivityResult(activity: Activity, requestCode: Int, resultCode: Int): Boolean {
        when (requestCode) {
            REQ_BIND -> if (resultCode == Activity.RESULT_OK) configureOrFinish(activity) else cancel("Android didn't allow the widget")
            REQ_CONFIGURE -> if (resultCode == Activity.RESULT_OK) finish() else cancel(null)
            REQ_RECONFIGURE -> Unit
            else -> return false
        }
        return true
    }

    private fun finish() {
        val p = pending ?: return
        pending = null
        AppWidgetPlan.place(p.target, p.entry, AppWidgetRef.options(p.id, p.entry.provider, p.entry.label))
        Sounds.confirm()
        Toasts.show("Added ${p.entry.label} ✓")
    }

    private fun cancel(why: String?) {
        pending?.let { AppWidgetHosting.delete(it.id) }
        pending = null
        if (why != null) { Toasts.show(why); Sounds.error() }
    }

    /** "Set up…" in a hosted widget's options, for providers whose set-up screen can be opened again. */
    fun setUpAction(w: Widget): WidgetAction? {
        val activity = Host.activity ?: return null
        val id = AppWidgetRef.id(w) ?: return null
        val info = AppWidgetHosting.info(activity, id) ?: return null
        if (info.configure == null || info.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_RECONFIGURABLE == 0) return null
        return WidgetAction("Set up…", color = Palette.Sky) {
            if (!AppWidgetHosting.configure(activity, id, REQ_RECONFIGURE)) { Toasts.show("Its set-up screen won't open"); Sounds.error() }
        }
    }
}

/** The size a provider's widget gets in each place, and putting it there. */
object AppWidgetPlan {
    /** The size for [entry] in [area]: as many of the area's cells as its minimum size needs. */
    fun sizeIn(area: WidgetArea, entry: AppWidgetEntry): WidgetSize {
        val (cw, ch, gap) = when (area) {
            WidgetArea.Top -> Triple(TOP_CELL_W, TOP_CELL_H, TOP_GAP)
            // The bottom screen's cells, as BottomWidgets works them out for its 468 dp width.
            WidgetArea.Bottom -> bottomCell(468.dp, 16.dp, 12.dp).let { Triple(it.first, it.second, 12.dp) }
        }
        val cols = AppWidgetFit.cells(entry.minW.toFloat(), cw.value, gap.value, if (area == WidgetArea.Top) 3 else 2)
        val rows = AppWidgetFit.cells(entry.minH.toFloat(), ch.value, gap.value, 2)
        return AppWidgetFit.sizeFor(cols, rows, AppWidgetKind.sizes) { Widgets.footprint(it, area) }
    }

    /** The cells for [entry] on the icon grid as it is drawn now. */
    fun gridSpan(entry: AppWidgetEntry): GridSpan =
        AppWidgetFit.spanFor(entry.minW.toFloat(), entry.minH.toFloat(), IconGrid.tileDp.value, IconGrid.gapDp.value)

    /** "2×1": what the picker says [entry] will cover at [target]. */
    fun sizeLabel(target: AppWidgetTarget, entry: AppWidgetEntry): String = when (target) {
        is AppWidgetTarget.Area -> Widgets.footprint(sizeIn(target.area, entry), target.area).let { "${it.first}×${it.second}" }
        is AppWidgetTarget.GridAt -> if (GridWidgets.inCells) gridSpan(entry).label else Widgets.footprint(sizeIn(WidgetArea.Top, entry), WidgetArea.Top).let { "${it.first}×${it.second}" }
        is AppWidgetTarget.FolderAt -> "1×1"
    }

    /** Puts the widget in its place and leaves the highlight on it. */
    fun place(target: AppWidgetTarget, entry: AppWidgetEntry, options: Map<String, String>) {
        when (target) {
            is AppWidgetTarget.Area -> {
                val w = Widgets.add(AppWidgetKind, target.area, sizeIn(target.area, entry), options)
                FocusMemory.remember("widgets:${if (target.area == WidgetArea.Top) 0 else 1}", "w:${w.id}")
            }
            is AppWidgetTarget.GridAt -> {
                // Widgets set to show as a row join the row at the row's size (GridWidgets.add says so in its toast).
                if (!GridWidgets.inCells) { place(AppWidgetTarget.Area(WidgetArea.Top), entry, options); return }
                GridWidgets.add(AppWidgetKind, homeShown(), target.at, gridSpan(entry), options)?.let { FocusMemory.remember(target.route, it) }
            }
            is AppWidgetTarget.FolderAt ->
                GridWidgets.addInFolder(AppWidgetKind, target.folder, target.shown, target.at, options)?.let { FocusMemory.remember(target.route, it) }
        }
        Nav.focusTick++
    }
}

/** The picker's state: where the widget it picks will go. */
object AppWidgetUi {
    var picking by mutableStateOf<AppWidgetTarget?>(null)
        private set

    /** A fresh focus key per opening, so it opens on its first widget. */
    var gen by mutableIntStateOf(0)
        private set

    val open: Boolean get() = picking != null

    fun pick(target: AppWidgetTarget) {
        if (TextEntry.request != null) return
        picking = target
        gen++
    }

    fun close() {
        picking = null
        Nav.focusTick++
    }
}

/** The widget on its card: Android's view, told its size; a placeholder in the gallery; a note when its provider is gone. */
@Composable
fun AppWidgetContent(s: WidgetScope, m: Modifier) {
    val id = AppWidgetRef.id(s.widget)
    val view = remember { mutableStateOf<RelayView?>(null) }
    WidgetCard(s, m, onClick = { _ -> view.value?.let { v -> AppWidgetHosting.click(v) } }) { _, _ ->
        if (id == null) AppWidgetPlaceholder(s) else HostedWidget(id, s, view)
    }
}

@Composable
private fun HostedWidget(id: Int, s: WidgetScope, out: MutableState<RelayView?>) {
    val ctx = LocalContext.current
    val gen = AppWidgetHosting.generation
    val tick = AppWidgetHosting.providersTick
    val info = remember(id, tick) { AppWidgetHosting.info(ctx, id) }
    if (info == null) {
        Column(Modifier.fillMaxSize().padding(8.dp), Arrangement.Center, Alignment.CenterHorizontally) {
            Text("▣", style = MaterialTheme.typography.titleLarge, color = Palette.Muted)
            if (!s.small) Text("Not available", style = MaterialTheme.typography.labelMedium, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        return
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = maxWidth.value
        val h = maxHeight.value
        // A new host (a new activity), or a provider changed (its app updated): the view is made again, against the
        // host and with the provider's current content, so it keeps getting updates.
        key(gen, tick) {
            AndroidView(
                factory = { c -> AppWidgetHosting.view(id, info) ?: View(c) },
                modifier = Modifier.fillMaxSize(),
                onRelease = { v -> if (v is RelayView) { AppWidgetHosting.release(v); if (out.value === v) out.value = null } },
                update = { v ->
                    if (v is RelayView) {
                        AppWidgetHosting.tellSize(v, w, h)
                        if (s.interactive) out.value = v
                    }
                },
            )
        }
    }
}

/** The gallery's and the grid picker's preview: there is no widget yet to show. */
@Composable
private fun AppWidgetPlaceholder(s: WidgetScope) {
    Column(Modifier.fillMaxSize().padding(10.dp), Arrangement.Center, Alignment.CenterHorizontally) {
        Text("▣", style = MaterialTheme.typography.displaySmall, color = Palette.Sky)
        if (!s.small) {
            Text(
                "A widget from one of your apps", style = MaterialTheme.typography.labelMedium, color = Palette.Muted,
                textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Composed once with the widget popups (WidgetHost): the picker, and the look at the providers after each return. */
@Composable
fun AppWidgetFlow() {
    val ctx = LocalContext.current
    val resumes = (ctx as? MainActivity)?.resumeCount?.intValue ?: 0
    val tick = AppWidgetHosting.providersTick
    LaunchedEffect(resumes, tick) { AppWidgetHosting.sweep(ctx) }
    AppWidgetPicker()
}

/** Every widget the installed apps offer, by app, with its preview and the size it would take; A adds it. */
@Composable
fun AppWidgetPicker() {
    var target by remember { mutableStateOf<AppWidgetTarget?>(null) }
    AppWidgetUi.picking?.let { target = it }
    val ctx = LocalContext.current
    OverlayPanel(AppWidgetUi.open, onClose = { AppWidgetUi.close() }) {
        val t = target ?: return@OverlayPanel
        val key = "overlay:appwidgets:${AppWidgetUi.gen}"
        // Read when the picker opens and when apps come or go; never while composing.
        val packages = Emulators.changes
        val catalog by produceState<AppWidgetHosting.Catalog?>(null, packages, AppWidgetUi.gen) {
            value = withContext(Dispatchers.IO) { runCatching { AppWidgetHosting.catalog(ctx) }.getOrNull() ?: AppWidgetHosting.Catalog(emptyList(), emptyMap()) }
        }
        PanelSurface(Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Add an Android widget", style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.width(10.dp))
                val (where, color) = when (t) {
                    is AppWidgetTarget.Area -> if (t.area == WidgetArea.Top) "Top screen" to Palette.Butter else "Bottom screen" to Palette.Sky
                    is AppWidgetTarget.GridAt -> "Home grid" to Palette.Butter
                    is AppWidgetTarget.FolderAt -> "In the folder" to Palette.Butter
                }
                Pill(where, color)
                Spacer(Modifier.weight(1f))
                ButtonHint("A", "Add", Palette.Mint)
                Spacer(Modifier.width(12.dp))
                ButtonHint("B", "Close", Palette.Lavender)
            }
            Spacer(Modifier.height(6.dp))
            val list = catalog
            when {
                list == null -> PipNote("Looking through your apps…", "The widgets they offer will show here.", mood = 1)
                list.isEmpty -> EmptyNote("None of your apps offers a widget yet", "Go back", Modifier.remembersFocus(key, "back", true)) { AppWidgetUi.close() }
                else -> ProviderGrid(list, t, key)
            }
        }
    }
}

@Composable
private fun ProviderGrid(catalog: AppWidgetHosting.Catalog, target: AppWidgetTarget, key: String) {
    val ctx = LocalContext.current
    val state = rememberLazyGridState()
    val first = catalog.groups.first().entries.first().provider
    LazyVerticalGrid(
        GridCells.Adaptive(214.dp),
        Modifier.fadingEdges(state),
        state = state,
        contentPadding = PaddingValues(horizontal = FocusRoomSide, vertical = FocusRoom),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        for (g in catalog.groups) {
            item(key = "app:${g.pkg}", span = { GridItemSpan(maxLineSpan) }) {
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(g.pkg, Modifier.size(26.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(g.app, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Pill(plural(g.entries.size, "widget", "widgets"), Palette.Muted)
                }
            }
            items(g.entries, key = { it.provider }) { e ->
                val info = catalog.infos[e.provider]
                FocusCard(
                    Modifier.height(186.dp).remembersFocus(key, "p:${e.provider}", e.provider == first),
                    glow = Palette.Sky,
                    onClick = {
                        val activity = Host.activity ?: ctx as? Activity
                        if (info == null || activity == null) { Toasts.show("That widget can't be added right now"); Sounds.error(); return@FocusCard }
                        AppWidgetUi.close()
                        AppWidgetAdd.begin(activity, e, info, target)
                    },
                ) {
                    Column(Modifier.fillMaxSize().padding(10.dp)) {
                        Box(Modifier.fillMaxWidth().height(112.dp).clip(RoundedCornerShape(14.dp)).background(Palette.NightDeep.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
                            val preview by produceState(AppWidgetHosting.cachedPreview(e.provider), e.provider) {
                                if (value == null && info != null) value = withContext(Dispatchers.IO) { runCatching { AppWidgetHosting.preview(ctx, e.provider, info) }.getOrNull() }
                            }
                            val p = preview
                            if (p != null) Image(p, null, Modifier.fillMaxSize().padding(8.dp), contentScale = ContentScale.Fit)
                            else Text("▣", style = MaterialTheme.typography.displaySmall, color = Palette.Sky)
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(e.label, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.width(6.dp))
                            Pill(AppWidgetPlan.sizeLabel(target, e), Palette.Butter)
                        }
                        Text(
                            if (e.configures) "Asks a few questions first" else "Ready to go",
                            style = MaterialTheme.typography.labelMedium, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
