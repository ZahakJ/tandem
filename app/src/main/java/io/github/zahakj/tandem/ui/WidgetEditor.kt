package io.github.zahakj.tandem.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import io.github.zahakj.tandem.Selected
import io.github.zahakj.tandem.Selection
import io.github.zahakj.tandem.data.Widget
import io.github.zahakj.tandem.data.WidgetPlace
import io.github.zahakj.tandem.data.WidgetSize
import io.github.zahakj.tandem.data.WidgetStore

private const val ADD_ID = "+add"
private val ADD_TILE = Widget(ADD_ID, ADD_ID, WidgetSize.Small)

/** Bottom screen preview: about the real screen's shape (1080 x 1240). */
private val BOTTOM_PREVIEW = DpSize(300.dp, 344.dp)

/** The widget editor: both screens' layouts, live; A on a widget customizes it, L1/R1 switch screens. */
@Composable
fun WidgetsScreen() {
    Widgets.ensure(LocalContext.current)
    val tab = WidgetUi.editorTab
    val area = if (tab == 0) WidgetArea.Top else WidgetArea.Bottom
    val routeKey = "widgets:$tab"
    val list = Widgets.list(area)
    val config = WidgetStore.config
    var resetArmed by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxSize().padding(start = 22.dp, end = 22.dp, bottom = 10.dp)) {
        Column(Modifier.width(236.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TabChip("Top screen", tab == 0, Palette.Butter, Modifier.remembersFocus(routeKey, "tab0", false)) { WidgetUi.editorTab = 0 }
                TabChip("Bottom screen", tab == 1, Palette.Sky, Modifier.remembersFocus(routeKey, "tab1", false)) { WidgetUi.editorTab = 1 }
            }
            Text(
                if (tab == 0) "A row of widgets on the home screen, under Continue playing."
                else "Shown on the bottom screen whenever nothing is highlighted. With none, Pip's greeting shows.",
                style = MaterialTheme.typography.bodySmall, color = Palette.Muted,
            )
            SideRow("+ Add a widget", null, routeKey, "add", list.isEmpty(), Palette.Mint) { WidgetUi.openGallery(area) }
            if (tab == 0) {
                SideRow("Show on home", onOff(config.homeEnabled), routeKey, "home", false, Palette.Butter) {
                    WidgetStore.update { it.copy(homeEnabled = !it.homeEnabled) }
                }
                SideRow("Rows", when (config.homeRows) { 1 -> "One"; 2 -> "Two"; else -> "Auto" }, routeKey, "rows", false, Palette.Butter) {
                    WidgetStore.update { it.copy(homeRows = (it.homeRows + 1) % 3) }
                }
            }
            SideRow(if (resetArmed) "Press again to reset" else "Reset to the default set", null, routeKey, "reset", false, Palette.Peach) {
                if (!resetArmed) { resetArmed = true; return@SideRow }
                resetArmed = false
                WidgetStore.update { it.copy(widgets = null) }
                Toasts.show("Widgets are back to the default set")
            }
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ButtonHint("A", "Customize", Palette.Mint)
                ButtonHint("Y", "Options", Palette.Butter)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ButtonHint("L1", "", Palette.Muted)
                ButtonHint("R1", "Switch screen", Palette.Muted)
            }
        }
        Spacer(Modifier.width(18.dp))
        Column(Modifier.weight(1f).fillMaxHeight()) {
            SectionTitle(if (tab == 0) "Home screen" else "Bottom screen", if (tab == 0) Palette.Butter else Palette.Sky)
            if (tab == 0) {
                if (!config.homeEnabled) Text("The widget row is off. Turn on Show on home to see it.", style = MaterialTheme.typography.labelMedium, color = Palette.Peach)
                val placed = Widgets.pack(list + ADD_TILE, WidgetArea.Top, Widgets.homeRows(list))
                val rowScroll = rememberScrollState()
                Box(Modifier.fillMaxWidth().fadingEdges(rowScroll, horizontal = true).focusRow().horizontalScroll(rowScroll).padding(horizontal = FocusRoomSide, vertical = FocusRoom)) {
                    PackedGrid(placed, TOP_CELL_W, TOP_CELL_H, TOP_GAP) { p, m -> EditorTile(p, WidgetArea.Top, routeKey, list.firstOrNull()?.id == p.widget.id, m) }
                }
                if (list.isEmpty()) PipNote("No widgets up here yet", "Add a clock, your storage, play stats and more.", mood = 1)
            } else {
                val gap = 10.dp
                val (cw, ch) = bottomCell(BOTTOM_PREVIEW.width, gap, gap)
                val placed = Widgets.pack(list + ADD_TILE, WidgetArea.Bottom)
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                    Box(
                        Modifier.size(BOTTOM_PREVIEW).clip(RoundedCornerShape(24.dp)).background(Palette.NightDeep)
                            .border(2.dp, Palette.CardHigh, RoundedCornerShape(24.dp)),
                    ) {
                        val previewScroll = rememberScrollState()
                        Column(Modifier.fillMaxSize().fadingEdges(previewScroll).verticalScroll(previewScroll).padding(gap)) {
                            if (list.isEmpty()) {
                                Text("Pip's greeting shows here.", Modifier.fillMaxWidth().padding(bottom = 8.dp), style = MaterialTheme.typography.labelMedium, color = Palette.Muted, textAlign = TextAlign.Center)
                            }
                            PackedGrid(placed, cw, ch, gap) { p, m -> EditorTile(p, WidgetArea.Bottom, routeKey, list.firstOrNull()?.id == p.widget.id, m) }
                        }
                    }
                }
            }
        }
    }
}

/** A widget in the editor: a live, non-interactive preview; A opens its options, move mode moves it. */
@Composable
private fun EditorTile(p: Placed, area: WidgetArea, routeKey: String, first: Boolean, m: Modifier) {
    val w = p.widget
    if (w.id == ADD_ID) {
        FocusFrame(
            m.remembersFocus(routeKey, "addtile", false), accent = Palette.Mint,
            onFocus = { FocusMemory.remember(routeKey, "addtile"); Selection.current = Selected.Home },
            onBlur = {}, onClick = { WidgetUi.openGallery(area) },
        ) { focused, _ ->
            Column(Modifier.fillMaxSize().background(Palette.Mint.copy(alpha = if (focused) 0.16f else 0.07f)), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text("+", style = MaterialTheme.typography.displaySmall, color = Palette.Mint)
                Text("Add a widget", style = MaterialTheme.typography.labelLarge, color = Palette.Mint)
            }
        }
        return
    }
    val kind = Widgets.kind(w) ?: return
    val key = "w:${w.id}"
    FocusFrame(
        m.movable(w.id, area).remembersFocus(routeKey, key, first),
        accent = kind.accent, drawSurface = false,
        onFocus = {
            WidgetUi.focused = w.id; WidgetUi.focusedArea = area; WidgetUi.focusedInEditor = true
            FocusMemory.remember(routeKey, key); Selection.current = Selected.Home
        },
        onBlur = { if (WidgetUi.focused == w.id) WidgetUi.focused = null },
        onClick = { WidgetUi.openMenu(w.id, area, inEditor = true) },
    ) { focused, _ ->
        WidgetView(w, p.size, Modifier.fillMaxSize(), area, interactive = false)
        if (WidgetUi.moving == w.id) {
            // The widget's own colour, deepened, while it moves: no grey veil.
            Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.lerp(kind.accent, Palette.NightDeep, 0.7f).copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
                Pill("◀ ▶  A done", Palette.Butter, Modifier.darkChip())
            }
        } else if (focused) {
            Pill(kind.name, kind.accent, Modifier.align(Alignment.BottomEnd).padding(6.dp).darkChip())
        }
    }
}

@Composable
private fun TabChip(label: String, selected: Boolean, color: Color, modifier: Modifier, onClick: () -> Unit) {
    FocusCard(modifier.height(32.dp), glow = color, shape = RoundedCornerShape(50), onFocus = { Selection.current = Selected.Home }, onClick = onClick) {
        Box(
            Modifier.fillMaxHeight().background(if (selected) color.copy(alpha = 0.85f) else color.copy(alpha = 0.14f)).padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) { Text(label, style = MaterialTheme.typography.labelLarge, color = if (selected) Palette.NightDeep else color, maxLines = 1) }
    }
}

@Composable
private fun SideRow(label: String, value: String?, routeKey: String, key: String, first: Boolean, color: Color, onClick: () -> Unit) {
    FocusCard(
        Modifier.fillMaxWidth().remembersFocus(routeKey, key, first), glow = color, shape = RoundedCornerShape(14.dp),
        onFocus = { FocusMemory.remember(routeKey, key); Selection.current = Selected.Home; WidgetUi.focused = null },
        onClick = onClick,
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (value != null) Pill(value, color)
        }
    }
}

/** A row in the widget menu. With [onStep], left and right change the value too. */
@Composable
private fun OptionRow(
    label: String,
    value: String?,
    routeKey: String,
    key: String,
    first: Boolean,
    color: Color = Palette.Mint,
    onStep: ((Int) -> Unit)? = null,
    onClick: () -> Unit,
) {
    val steps = if (onStep == null) Modifier else Modifier.onPreviewKeyEvent { e ->
        val d = when (e.key) { Key.DirectionLeft -> -1; Key.DirectionRight -> 1; else -> return@onPreviewKeyEvent false }
        if (e.type == KeyEventType.KeyDown) onStep(d)
        true
    }
    FocusCard(Modifier.fillMaxWidth().remembersFocus(routeKey, key, first).then(steps), glow = color, shape = RoundedCornerShape(14.dp), onClick = onClick) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (value != null) {
                if (onStep != null) Text("◀ ", style = MaterialTheme.typography.labelSmall, color = Palette.Muted)
                Pill(value, color)
                if (onStep != null) Text(" ▶", style = MaterialTheme.typography.labelSmall, color = Palette.Muted)
            }
        }
    }
}

private fun <T> cycle(list: List<T>, current: T, by: Int): T {
    val i = list.indexOf(current).coerceAtLeast(0)
    return list[((i + by) % list.size + list.size) % list.size]
}

/** A widget drawn at its real size in [area], shrunk to fit [box]. */
@Composable
fun ScaledPreview(w: Widget, size: WidgetSize, area: WidgetArea, box: DpSize) {
    val (cw, ch) = if (area == WidgetArea.Top) TOP_CELL_W to TOP_CELL_H else bottomCell(420.dp, 16.dp, 12.dp)
    val gap = if (area == WidgetArea.Top) TOP_GAP else 12.dp
    val (fw, fh) = Widgets.footprint(size, area)
    val wd = span(fw, cw, gap)
    val hd = span(fh, ch, gap)
    val scale = minOf(box.width / wd, box.height / hd, 1f)
    Box(Modifier.size(box), contentAlignment = Alignment.Center) {
        Box(Modifier.requiredSize(wd, hd).graphicsLayer { scaleX = scale; scaleY = scale }) {
            WidgetView(w, size, Modifier.fillMaxSize(), area, interactive = false)
        }
    }
}

/** Y on a widget (or A in the editor): size, screen, the widget's own settings, move and remove. */
@Composable
fun WidgetMenu() {
    var shownId by remember { mutableStateOf<String?>(null) }
    WidgetUi.menuFor?.let { shownId = it }
    OverlayPanel(WidgetUi.menuFor != null, onClose = { WidgetUi.close() }) {
        val w = shownId?.let { Widgets.byId(it) } ?: return@OverlayPanel
        val kind = Widgets.kind(w) ?: return@OverlayPanel
        val area = WidgetUi.menuArea
        val key = "overlay:wmenu:${WidgetUi.popupGen}"
        val others = Widgets.list(area).size
        PanelSurface(Modifier.width(720.dp).heightIn(max = 450.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.width(250.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(kind.icon, style = MaterialTheme.typography.headlineSmall, color = kind.accent)
                        Spacer(Modifier.width(8.dp))
                        Text(kind.name, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(kind.blurb, style = MaterialTheme.typography.bodySmall, color = Palette.Muted, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(10.dp))
                    Box(Modifier.clip(RoundedCornerShape(18.dp)).background(Palette.NightDeep.copy(alpha = 0.6f))) {
                        ScaledPreview(w, w.size, area, DpSize(250.dp, 180.dp))
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ButtonHint("◀▶", "Change", Palette.Butter)
                        ButtonHint("B", "Done", Palette.Lavender)
                    }
                }
                val options = rememberScrollState()
                // Scrolls with soft ends; the padding is room for the highlighted row to grow, lift and glow.
                Column(Modifier.weight(1f).fadingEdges(options).verticalScroll(options).padding(vertical = 14.dp, horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    // On the icon grid the size is the cells it covers, and it stays where it was put.
                    val onGrid = w.place == WidgetPlace.Grid
                    if (onGrid && GridWidgets.spans(kind).size > 1) {
                        val gridKey = GridWidgets.key(w.id)
                        OptionRow("Size", GridWidgets.span(gridKey).label, key, "size", true, Palette.Butter, onStep = { GridWidgets.resize(gridKey) }) { GridWidgets.resize(gridKey) }
                    } else if (!onGrid && kind.sizes.size > 1) {
                        val step = { by: Int -> Widgets.setSize(w.id, cycle(kind.sizes, w.size, by)) }
                        OptionRow("Size", w.size.label, key, "size", true, Palette.Butter, onStep = step) { step(1) }
                    }
                    if (!onGrid) {
                        val places = WidgetPlace.entries.filter { it != WidgetPlace.Grid }
                        val stepPlace = { by: Int -> Widgets.setPlace(w.id, cycle(places, w.place, by)) }
                        OptionRow("Show on", w.place.label, key, "place", kind.sizes.size <= 1, Palette.Sky, onStep = stepPlace) { stepPlace(1) }
                    }
                    for (opt in kind.options.filter { it.showFor(w.size) }) {
                        val current = w.options[opt.key] ?: opt.default
                        val values = opt.choices.map { it.first }
                        val step = { by: Int -> Widgets.setOption(w.id, opt.key, cycle(values, current, by)) }
                        OptionRow(opt.label, opt.choices.firstOrNull { it.first == current }?.second ?: current, key, "opt:${opt.key}", false, kind.accent, onStep = step) { step(1) }
                    }
                    kind.actions(w).forEachIndexed { i, a ->
                        OptionRow(a.label, a.value, key, "act:$i", false, a.color) { a.run(); if (a.closes) WidgetUi.close() else if (a.refocus) WidgetUi.refocus() }
                    }
                    if (others > 1 && Widgets.isOn(area, w)) {
                        OptionRow("Move", null, key, "move", false, Palette.Lavender) { WidgetUi.startMoving(w.id, area) }
                    }
                    if (!WidgetUi.menuInEditor && !onGrid) {
                        OptionRow("All widgets…", null, key, "editor", false, Palette.Lavender) {
                            WidgetUi.close()
                            WidgetUi.editorTab = if (area == WidgetArea.Top) 0 else 1
                            FocusMemory.remember("widgets:${WidgetUi.editorTab}", "w:${w.id}")
                            Nav.go(Route.Widgets)
                        }
                    }
                    // Removing can be undone (the toast offers it), so it asks nothing first.
                    OptionRow("Remove widget", null, key, "remove", false, Palette.Peach) {
                        WidgetUi.close()
                        if (onGrid) GridWidgets.remove(GridWidgets.key(w.id)) else {
                            val at = io.github.zahakj.tandem.data.WidgetStore.widgets.indexOfFirst { it.id == w.id }
                            Widgets.remove(w.id)
                            Undo.offer("Removed ${kind.name}") {
                                io.github.zahakj.tandem.data.WidgetStore.updateWidgets { list ->
                                    if (list.any { it.id == w.id }) list else list.toMutableList().apply { add(at.coerceIn(0, size), w) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Every kind of widget with a live preview; A adds it to the screen being edited. */
@Composable
fun WidgetGallery() {
    var area by remember { mutableStateOf(WidgetArea.Top) }
    WidgetUi.gallery?.let { area = it }
    OverlayPanel(WidgetUi.gallery != null, onClose = { WidgetUi.close() }) {
        val key = "overlay:wgallery:${WidgetUi.popupGen}"
        PanelSurface(Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Add a widget", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.width(10.dp))
                Pill(if (area == WidgetArea.Top) "Top screen" else "Bottom screen", if (area == WidgetArea.Top) Palette.Butter else Palette.Sky)
                Spacer(Modifier.weight(1f))
                ButtonHint("A", "Add", Palette.Mint)
                Spacer(Modifier.width(12.dp))
                ButtonHint("B", "Close", Palette.Lavender)
            }
            Spacer(Modifier.height(6.dp))
            val gallery = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
            LazyVerticalGrid(
                GridCells.Adaptive(214.dp),
                Modifier.fadingEdges(gallery),
                state = gallery,
                contentPadding = PaddingValues(horizontal = FocusRoomSide, vertical = FocusRoom),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(WidgetKinds.all, key = { it.id }) { kind ->
                    val ok = kind.available()
                    FocusCard(
                        Modifier.height(186.dp).remembersFocus(key, kind.id, kind == WidgetKinds.all.first()),
                        glow = kind.accent,
                        onClick = {
                            if (!ok) {
                                Toasts.show(kind.needs ?: "Not available yet")
                                if (kind.id == "achievements") { WidgetUi.close(); raSignIn() }
                                return@FocusCard
                            }
                            val w = Widgets.add(kind, area)
                            FocusMemory.remember("widgets:${if (area == WidgetArea.Top) 0 else 1}", "w:${w.id}")
                            WidgetUi.close()
                            Toasts.show("Added ${kind.name} ✓")
                        },
                    ) {
                        Column(Modifier.fillMaxSize().padding(10.dp)) {
                            Box(Modifier.fillMaxWidth().height(112.dp).clip(RoundedCornerShape(14.dp)).background(Palette.NightDeep.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
                                // Not available yet: the preview is faded back, rather than covered with a dark veil.
                                Box(Modifier.graphicsLayer { alpha = if (ok) 1f else 0.35f }) {
                                    ScaledPreview(Widget("preview-${kind.id}", kind.id, kind.defaultSize), kind.defaultSize, area, DpSize(190.dp, 104.dp))
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(kind.icon, style = MaterialTheme.typography.titleSmall, color = kind.accent)
                                Spacer(Modifier.width(6.dp))
                                Text(kind.name, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (!ok) Pill("Needs sign-in", Palette.Peach)
                            }
                            Text(kind.blurb, style = MaterialTheme.typography.labelMedium, color = Palette.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}
