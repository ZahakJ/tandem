package io.github.zahakj.tandem.ui

import io.github.zahakj.tandem.R
import io.github.zahakj.tandem.Tx
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import io.github.zahakj.tandem.Button
import io.github.zahakj.tandem.Selection
import io.github.zahakj.tandem.data.FolderStyle
import io.github.zahakj.tandem.data.IconGridStore
import io.github.zahakj.tandem.data.Store
import io.github.zahakj.tandem.data.styleOf

/*
 * A tile's edit screen (Y › Edit): the tile big on the left, live, with Pip perched on it, and what can be changed in
 * a few sections on the right, L1 / R1 stepping between them: Name · Look · Contents · Behaviour · Organize · Remove
 * (only those that apply to this tile). Every change shows at once on the preview, on the grid behind and on the other
 * screen. The rows are the tile's own actions (tileMenuSpec), sorted by EditSections, plus the Look rows built here;
 * Artwork opens the artwork studio (FolderDesign.kt) over this screen, and B there comes back here.
 */

object TileEditor {
    var target by mutableStateOf<TileMenuTarget?>(null)
        private set
    var section by mutableStateOf(EditSection.Look)

    /** A section was picked with L1 / R1 (otherwise the screen opens on the first one there is). */
    internal var chosen = false

    fun open(key: String, folder: String?) {
        IconGrid.choosingFolder = false
        IconGrid.folderPage = null
        ActionMenu.choosing = null
        ActionMenu.group = null
        chosen = false
        target = TileMenuTarget(key, folder)
    }

    fun close() {
        target = null
        IconGrid.choosingFolder = false
        IconGrid.folderPage = null
        ActionMenu.choosing = null
        Nav.focusTick++
    }

    fun routeOf(t: TileMenuTarget, s: EditSection) = "tileedit:${t.folder.orEmpty()}:${t.key}:${s.name}"
}

/** The edit screen, composed in the app root over the icon grid's lift and under the artwork studio. */
@Composable
fun TileEditorHost() {
    val ctx = LocalContext.current
    val t = TileEditor.target
    val shown = rememberLatest(t)
    val visible = t != null
    PanelGuard(visible)
    // The other screen keeps moving as the preview changes (Ambient.livePreview), as in the artwork studio.
    DisposableEffect(visible) {
        Ambient.livePreview = visible
        onDispose { Ambient.livePreview = false }
    }
    val catalog = IconGrid.menuData?.first
    val item = shown?.let { catalog?.item(it.key) }
    val spec = remember(shown, item, IconGridStore.config, IconGrid.choosingFolder, IconGrid.folderPage, io.github.zahakj.tandem.Lang.revision.intValue) {
        if (shown == null || item == null || catalog == null) null else tileMenuSpec(shown, item, catalog, ctx)?.let { SmartFolders.extendMenu(it) }
    }
    val sections: Map<EditSection, List<MenuAction>> = remember(spec, item) { if (item == null) emptyMap() else sectionsFor(item, spec) }
    // Gone (deleted, moved out of sight, hidden): the screen closes on its own.
    LaunchedEffect(visible, item == null) { if (visible && item == null) TileEditor.close() }
    LaunchedEffect(sections) {
        if (sections.isNotEmpty() && (TileEditor.section !in sections.keys || !TileEditor.chosen)) {
            TileEditor.section = if (EditSection.Look in sections.keys && !TileEditor.chosen) EditSection.Look else sections.keys.first()
        }
    }
    InterceptButtons(visible) { b ->
        when (b) {
            Button.L1, Button.R1 -> {
                val keys = sections.keys.toList()
                if (keys.size > 1 && ActionMenu.choosing == null && !IconGrid.choosingFolder) {
                    val i = keys.indexOf(TileEditor.section).coerceAtLeast(0)
                    val by = if (b == Button.R1) 1 else -1
                    TileEditor.section = keys[Math.floorMod(i + by, keys.size)]
                    TileEditor.chosen = true
                    PipMind.look(by, 0)
                    Sounds.page()
                }
            }
            Button.Y, Button.Grab -> TileEditor.close()
            else -> Unit
        }
        true
    }
    val morph = rememberMorph(visible) { item?.let { tileMorph(shown?.key?.takeIf { shown.folder == null }, it.art, it.color) } }
    OverlayPanel(visible, onClose = {
        when {
            ActionMenu.choosing != null -> ActionMenu.choosing = null
            IconGrid.choosingFolder -> spec?.page?.onBack?.invoke() ?: run { IconGrid.choosingFolder = false; IconGrid.folderPage = null }
            else -> TileEditor.close()
        }
    }, morph = morph) {
        val target = shown ?: return@OverlayPanel
        val it = item ?: return@OverlayPanel
        // The other screen shows the tile as it is now.
        LaunchedEffect(it) { IconGrid.spot = it; Selection.current = it.selected }
        Column(Modifier.fillMaxSize().background(Palette.Card, RoundedCornerShape(26.dp)).padding(top = 14.dp)) {
            EditorHeader(it, sections.keys.toList())
            Row(Modifier.fillMaxWidth().weight(1f).padding(start = 18.dp, end = 10.dp, bottom = 12.dp)) {
                EditorPreview(it, Modifier.width(236.dp).fillMaxHeight())
                Spacer(Modifier.width(14.dp))
                Box(Modifier.weight(1f).fillMaxHeight()) { SectionPane(target, spec, sections) }
            }
        }
    }
}

/** The rows of each section that has any, in section order. */
private fun sectionsFor(item: GridItem, spec: MenuSpec?): Map<EditSection, List<MenuAction>> {
    val out = LinkedHashMap<EditSection, MutableList<MenuAction>>()
    for (s in EditSection.entries) out[s] = ArrayList()
    out.getValue(EditSection.Look).addAll(lookRows(item))
    for (a in spec?.actions.orEmpty()) {
        val s = EditSections.of(a.id) ?: continue
        out.getValue(s).add(a)
    }
    return out.filterValues { it.isNotEmpty() }
}

/**
 * Look: the artwork studio's parts (designable tiles: folders, systems, collections, genres), the shape and the
 * corners (every tile), each changed in place where it can be and shown at once.
 */
private fun lookRows(item: GridItem): List<MenuAction> {
    if (item.kind == TileKind.Widget || item.kind == TileKind.Now) return emptyList()
    val key = item.key
    val designable = FolderLooks.designable(item)
    val style = IconGridStore.config.styleOf(key)
    val grid = Store.settings.look.gridRoundness.coerceIn(0f, 0.5f)
    fun save(st: FolderStyle) {
        IconGridStore.setStyle(key, if (designable) st else st.shapeOnly())
        Sounds.toggle(true)
    }
    return buildList {
        if (designable) {
            add(MenuAction("art", Tx.s(R.string.grid_edit_artwork), Glyph.Image, Palette.Mint, sub = Tx.s(R.string.grid_edit_artwork_sub), value = "▸") {
                FolderDesign.open(key, FolderDesign.Page.Icon)
            })
            add(MenuAction(
                "colour", Tx.s(R.string.grid_design_colour), Glyph.Theme, Palette.Peach, value = "▸",
                art = { Box(Modifier.fillMaxSize().background(item.color)) },
            ) { FolderDesign.open(key, FolderDesign.Page.Colour) })
            add(MenuAction("hero", Tx.s(R.string.grid_design_hero), Glyph.Layers, Palette.Sky, sub = Tx.s(R.string.grid_edit_hero_sub), value = "▸") {
                FolderDesign.open(key, FolderDesign.Page.Hero)
            })
            add(MenuAction("logo", Tx.s(R.string.grid_edit_logo), Glyph.Text, Palette.Butter, sub = Tx.s(R.string.grid_edit_logo_sub), value = "▸") {
                FolderDesign.open(key, FolderDesign.Page.Title)
            })
        }
        val shapes = listOf(FolderStyle.SHAPE_THEME, FolderStyle.SHAPE_ROUNDED, FolderStyle.SHAPE_CIRCLE)
        fun shapeName(s: String) = Tx.s(when (s) { FolderStyle.SHAPE_ROUNDED -> R.string.grid_design_shape_rounded; FolderStyle.SHAPE_CIRCLE -> R.string.grid_design_shape_circle; else -> R.string.grid_edit_shape_grid })
        fun stepShape(by: Int) {
            val i = shapes.indexOf(style.shape).coerceAtLeast(0)
            val next = shapes[(i + by).coerceIn(0, shapes.size - 1)]
            if (next != style.shape) save(style.copy(shape = next))
        }
        add(MenuAction(
            "shape", Tx.s(R.string.grid_design_shape), Glyph.Corners, Palette.Lavender, value = shapeName(style.shape), step = ::stepShape,
            choices = shapes.map { s -> MenuChoice(shapeName(s), s == style.shape) { save(style.copy(shape = s)) } },
        ) { stepShape(1) })
        if (style.shape == FolderStyle.SHAPE_THEME) {
            fun stepCorners(by: Int) {
                val next = FolderStyle.stepCorners(style.corners, by)
                if (next != style.corners) save(style.copy(corners = next))
            }
            add(MenuAction(
                "corners", Tx.s(R.string.grid_edit_corners), Glyph.Corners, Palette.Lavender,
                value = cornersLabel(style.corners, grid), step = ::stepCorners,
                sub = if (style.corners == null) Tx.s(R.string.grid_edit_corners_follows) else Tx.s(R.string.grid_edit_corners_own),
                help = Tx.s(R.string.grid_edit_corners_help),
            ) { stepCorners(1) })
        }
        if (designable && style.icon == FolderStyle.ICON_GAME) add(MenuAction(
            "frame", Tx.s(R.string.grid_design_frame), Glyph.Highlight, Palette.Lavender,
            value = Tx.s(if (style.frame) R.string.grid_design_frame_theme else R.string.grid_design_frame_none),
        ) { save(style.copy(frame = !style.frame)) })
        val own = if (designable) style else style.shapeOnly()
        if (!own.isDefault) add(MenuAction("resetlook", Tx.s(R.string.grid_design_reset), Glyph.Restore, Palette.Peach) {
            val before = own
            IconGridStore.setStyle(key, FolderStyle.DEFAULT)
            Sounds.toggle(false)
            Undo.offer(Tx.s(R.string.grid_design_reset_done)) { IconGridStore.setStyle(key, before) }
        })
    }
}

/** "Follow the grid · 20%" or "35%". */
internal fun cornersLabel(own: Float?, grid: Float): String =
    if (own == null) Tx.s(R.string.grid_edit_corners_follow, pct(grid)) else pct(own)

@Composable
private fun EditorHeader(item: GridItem, sections: List<EditSection>) {
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 18.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        GlyphChip(Glyph.Wand, Palette.Lavender, 30.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(Tx.s(R.string.grid_edit_heading, item.type), style = MaterialTheme.typography.labelMedium, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(item.title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (sections.size > 1) {
            ButtonHint("L1", "", Palette.Muted)
            Spacer(Modifier.width(6.dp))
            SectionTabs(sections)
            Spacer(Modifier.width(6.dp))
            ButtonHint("R1", "", Palette.Muted)
            Spacer(Modifier.width(14.dp))
        }
        ButtonHint("B", Tx.s(R.string.grid_edit_done), Palette.Lavender)
    }
}

/** The sections as small pills, the one shown filled; its pill glides to the next with L1 / R1. */
@Composable
private fun SectionTabs(sections: List<EditSection>) {
    Row(
        Modifier.background(Palette.CardHigh, RoundedCornerShape(50)).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        for (s in sections) {
            val on = s == TileEditor.section
            val fill by animateColorAsState(if (on) sectionColor(s).copy(alpha = 0.32f) else Color.Transparent, Motion.fade(160), label = "tab")
            Box(
                Modifier.background(fill, RoundedCornerShape(50)).swallowTaps { TileEditor.section = s; TileEditor.chosen = true; Sounds.page() }
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(
                    Tx.s(s.label), style = MaterialTheme.typography.labelMedium, color = if (on) Palette.Text else Palette.Muted,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun sectionColor(s: EditSection): Color = when (s) {
    EditSection.Name -> Palette.Butter
    EditSection.Look -> Palette.Mint
    EditSection.Contents -> Palette.Sky
    EditSection.Behaviour -> Palette.Lavender
    EditSection.Organize -> Palette.Butter
    EditSection.Remove -> Palette.Peach
}

/**
 * The tile as it is now, big, with Pip perched on its corner (curious while the look changes, worried on Remove, a hop
 * when a change lands), its name, whether it keeps its own corners, and the other screen's picture.
 */
@Composable
private fun EditorPreview(item: GridItem, modifier: Modifier) {
    val grid = Store.settings.look.gridRoundness.coerceIn(0f, 0.5f)
    val tile = 132.dp
    val corner = tile * grid
    val shape = FolderLooks.shape(item.style, tile, corner)
    // A change landing on the tile: a small pop of the preview and a hop from Pip.
    val styleKey = item.style
    val first = remember { booleanArrayOf(true) }
    var pops by remember { mutableStateOf(0) }
    LaunchedEffect(styleKey) {
        if (first[0]) { first[0] = false; return@LaunchedEffect }
        pops++
        if (!Store.settings.reduceMotion) PipMind.cheer()
    }
    val bump = remember { androidx.compose.animation.core.Animatable(1f) }
    LaunchedEffect(pops) {
        if (pops == 0 || Motion.style == AnimationStyle.Off || Store.settings.reduceMotion) return@LaunchedEffect
        bump.snapTo(0.9f)
        bump.animateTo(1f, androidx.compose.animation.core.spring(0.45f, 420f))
    }
    val mood = when (TileEditor.section) {
        EditSection.Remove -> 3
        EditSection.Look -> 1
        else -> 0
    }
    Column(modifier.padding(top = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.padding(top = 28.dp)) {
            // Pip peeks over the tile's top corner, behind it.
            Pip(
                44.dp, mood = mood,
                modifier = Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-30).dp),
            )
            Box(
                Modifier.size(tile).graphicsLayer {
                    val s = bump.value
                    scaleX = s; scaleY = s
                    this.shape = shape; clip = true
                    shadowElevation = 18f * density; spotShadowColor = item.color; ambientShadowColor = item.color
                },
            ) { TileArtView(item.art, tile) }
        }
        Spacer(Modifier.height(10.dp))
        Text(item.title, style = MaterialTheme.typography.titleMedium.onArt(), maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        Text(
            listOf(item.type, item.detail).filter { it.isNotBlank() }.joinParts(), style = MaterialTheme.typography.labelMedium,
            color = lerp(item.color, Palette.Text, 0.45f), maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        // Its own corners, when it doesn't follow the grid's: said plainly, so it is never a mystery on the grid.
        val own = item.style?.takeIf { it.ownCorners }?.corners
        if (own != null) {
            Spacer(Modifier.height(6.dp))
            Row(
                Modifier.background(Palette.Lavender.copy(alpha = 0.2f), RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlyphIcon(Glyph.Corners, Palette.Lavender, Modifier.size(12.dp))
                Spacer(Modifier.width(5.dp))
                Text(Tx.s(R.string.grid_edit_corners_chip, pct(own)), style = MaterialTheme.typography.labelSmall, color = Palette.Lavender, maxLines = 1)
            }
        }
        Spacer(Modifier.weight(1f))
        val hero = remember(item) { heroFor(item) }
        Box(
            Modifier.fillMaxWidth().aspectRatio(16f / 9f).graphicsLayer { this.shape = RoundedCornerShape(12.dp); clip = true },
            contentAlignment = Alignment.Center,
        ) {
            if (hero != null) AsyncImage(rememberSizedRequest(hero, 480), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else ColorSweep(item.color, Modifier.fillMaxSize().glyphEcho(FolderLooks.sweepGlyph(item)))
        }
        Text(Tx.s(R.string.grid_design_other_screen), Modifier.padding(top = 3.dp), style = MaterialTheme.typography.labelSmall, color = Palette.Muted, maxLines = 1)
    }
}

/**
 * The section shown: its name and what it holds, then its rows under one gliding highlight. A row that opens a page
 * (Move to folder…) or a choice (Open games on) slides it in here; B goes back to the section.
 */
@Composable
private fun SectionPane(t: TileMenuTarget, spec: MenuSpec?, sections: Map<EditSection, List<MenuAction>>) {
    val section = TileEditor.section
    val rows = sections[section].orEmpty()
    val choosing = ActionMenu.choosing?.let { id -> rows.firstOrNull { it.id == id && it.choices != null } }
    val page = spec?.page
    val level = when {
        choosing != null -> "choose:${choosing.id}"
        page != null -> "page:${page.title}"
        else -> "section:${section.name}"
    }
    val still = Motion.style == AnimationStyle.Off || Store.settings.reduceMotion
    val toEnd = if (isRtl) -1 else 1
    val order = remember(sections) { sections.keys.toList() }
    AnimatedContent(
        level, Modifier.fillMaxSize(),
        transitionSpec = {
            // Sections slide the way L1 / R1 went; a page or a choice comes in from the end side.
            val from = order.indexOfFirst { initialState == "section:${it.name}" }
            val to = order.indexOfFirst { targetState == "section:${it.name}" }
            val dir = when {
                from >= 0 && to >= 0 -> if (to > from) 1 else -1
                targetState.startsWith("section:") -> -1
                else -> 1
            } * toEnd
            if (still) fadeIn(tween(0)) togetherWith fadeOut(tween(0))
            else (slideInHorizontally(tween(240, easing = Motion.EmphasizedDecelerate)) { dir * it / 4 } + fadeIn(tween(200)))
                .togetherWith(slideOutHorizontally(tween(160)) { -dir * it / 6 } + fadeOut(tween(120)))
        },
        label = "edit-section",
    ) { key ->
        val routeKey = TileEditor.routeOf(t, section) + ":" + key.substringBefore(':')
        val closesAfter = section == EditSection.Organize || section == EditSection.Remove
        fun after(a: MenuAction): MenuAction = if (!closesAfter || a.choices != null || a.hold) a else a.withClick {
            a.onClick()
            // Moved, taken out or removed: done here, unless it opened a page of choices first.
            if (!IconGrid.choosingFolder) TileEditor.close()
        }
        val back: (String) -> MenuAction = { title -> MenuAction("back", title, Glyph.Back, Palette.Lavender) {
            if (key.startsWith("choose:")) ActionMenu.choosing = null else page?.onBack?.invoke()
        } }
        val shownRows: List<MenuAction> = when {
            key.startsWith("choose:") && choosing != null -> listOf(back(choosing.label)) + choosing.choices.orEmpty().mapIndexed { i, c ->
                MenuAction("ch:$i", c.label, c.glyph ?: choosing.glyph, choosing.color, value = if (c.selected) "✓" else null, sub = c.sub, art = c.art) {
                    c.pick()
                    ActionMenu.choosing = null
                }
            }
            key.startsWith("page:") && page != null -> listOf(back(page.title)) + page.actions.map(::after)
            else -> rows.map(::after)
        }
        val state = rememberScrollState()
        Column(Modifier.fillMaxSize()) {
            val title = when {
                key.startsWith("choose:") -> choosing?.label
                key.startsWith("page:") -> page?.title
                else -> null
            }
            Text(
                title ?: Tx.s(section.label), Modifier.padding(start = 14.dp, top = 2.dp), style = MaterialTheme.typography.titleMedium,
                color = sectionColor(section), maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (title == null) Text(
                Tx.s(section.blurb), Modifier.padding(start = 14.dp), style = MaterialTheme.typography.labelMedium, color = Palette.Muted,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Box(Modifier.fillMaxWidth().weight(1f).fadingEdges(state).verticalScroll(state)) {
                LiftRows(routeKey, shownRows, onFocus = {})
            }
        }
    }
}
