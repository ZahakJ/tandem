package io.github.zahakj.tandem.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import io.github.zahakj.tandem.Button
import io.github.zahakj.tandem.CompanionInfo
import io.github.zahakj.tandem.Game
import io.github.zahakj.tandem.Libraries
import io.github.zahakj.tandem.ManualDoc
import io.github.zahakj.tandem.ManualPages
import io.github.zahakj.tandem.Manuals
import io.github.zahakj.tandem.PagedManual
import io.github.zahakj.tandem.data.ManualText
import io.github.zahakj.tandem.plural
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

/*
 * Reading a game's manual (Manuals.kt), page by page: on Tandem's screen from the game's Y menu (ManualScreen, a
 * route), and on the bottom screen while the game plays (CompanionManualPanel, a face of the companion). Both draw
 * the same [ManualView]; the first answers the controller, the second touch alone, since the controller belongs to
 * the game while it plays. The page each manual was left at is remembered per game (ManualPages).
 */

object ManualUi {
    /** Opens the reader on [game]'s manual, on Tandem's screen. */
    fun open(game: Game) {
        val route = Route.Manual(game.key)
        FocusMemory.forget(route.key)
        Nav.go(route)
    }
}

/** What the reader is doing with one manual: the page, and how a page is fitted (or how big text is). */
class ReaderState(val key: String, val count: Int) {
    var page by mutableIntStateOf(ManualPages.get(key).coerceIn(0, (count - 1).coerceAtLeast(0)))
        private set

    /** Pictures: the page as wide as the screen, scrolled, instead of whole. Text: larger type. */
    var zoomed by mutableStateOf(false)

    /** Bumped on every page turn, so a page's scroll starts at the top again. */
    var turn by mutableIntStateOf(0)
        private set

    fun go(to: Int): Boolean {
        val p = to.coerceIn(0, (count - 1).coerceAtLeast(0))
        if (p == page) return false
        page = p
        turn++
        ManualPages.set(key, p)
        return true
    }

    fun next(): Boolean = go(page + 1)
    fun previous(): Boolean = go(page - 1)
}

/** The manual at [file], opened off the main thread and closed when this leaves the screen. Null while opening; [failed] after. */
@Composable
fun rememberManualDoc(file: File?): Pair<ManualDoc?, Boolean> {
    var failed by remember(file) { mutableStateOf(false) }
    val doc by produceState<ManualDoc?>(null, file) {
        value = file?.let { withContext(Dispatchers.IO) { ManualDoc.open(it) } }
        failed = value == null
    }
    DisposableEffect(doc) { onDispose { doc?.close() } }
    return doc to failed
}

// --- On Tandem's screen ----------------------------------------------------------------------------------------------

@Composable
fun ManualScreen(route: Route.Manual) {
    val ctx = LocalContext.current
    // Idempotent and tiny, so it may run in composition: the reader state below reads the remembered page at once.
    remember(ctx) { ManualPages.init(ctx) }
    val game = remember(route.gameKey, Libraries.all) { Libraries.byKey(route.gameKey) }
    val file = remember(game, Manuals.version, game?.meta?.manual) { game?.let { Manuals.forGame(it) } }
    val (doc, failed) = rememberManualDoc(file)
    val color = game?.let { SystemColors.of(it.platform.id) } ?: Palette.Butter
    if (game == null || file == null || failed) {
        EmptyNote(
            when {
                game == null -> "That game isn't in the library any more"
                file == null -> "No manual for ${game.title}: put one in ${Manuals.shortPath(Manuals.folderFor(game.platform))}"
                else -> "Couldn't read ${file.name}"
            },
            "Go back", Modifier.takesFocus(route.key, "back", true), Modifier.fillMaxSize(), mood = 2, color = color,
        ) { Nav.back() }
        return
    }
    if (doc == null) {
        PipNote("Opening ${file.name}…", "A big manual takes a moment.", mood = 1, modifier = Modifier.fillMaxSize().padding(top = 60.dp))
        return
    }
    val state = remember(doc) { ReaderState(game.key, doc.pageCount) }
    val text = doc is ManualDoc.Text
    val active = Nav.route == route && !Nav.overlayOpen && Layers.top == null
    InterceptButtons(active) { b ->
        when (b) {
            Button.L1 -> { if (state.previous()) Sounds.page() else Sounds.error(); true }
            Button.R1 -> { if (state.next()) Sounds.page() else Sounds.error(); true }
            Button.Start -> { askPage(state); true }
            else -> false
        }
    }
    Row(Modifier.fillMaxSize().padding(start = 22.dp, end = 12.dp, bottom = 8.dp)) {
        // The side: what is being read, where in it, and the buttons.
        Column(Modifier.width(196.dp).fillMaxHeight().padding(top = 8.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(84.dp).clip(RoundedCornerShape(18.dp))) { GameArt(game) }
            Spacer(Modifier.height(4.dp))
            Text(game.title, Modifier.riseIn(route.key, 0, 12.dp), style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(Manuals.describe(file), Modifier.riseIn(route.key, 1, 12.dp), style = MaterialTheme.typography.bodySmall, color = Palette.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (text) {
                val title = (doc as ManualDoc.Text).pages.getOrNull(state.page)?.title
                if (title != null) Pill(title, color, Modifier.widthIn(max = 190.dp))
            }
            Pill("${state.page + 1} / ${doc.pageCount}", color)
            Spacer(Modifier.weight(1f))
            ButtonHint("◀▶", if (text) "Chapter" else "Page", Palette.Sky)
            ButtonHint("▲▼", "Scroll", Palette.Sky)
            ButtonHint("A", if (text) (if (state.zoomed) "Smaller text" else "Larger text") else (if (state.zoomed) "Fit page" else "Fit width"), Palette.Mint)
            ButtonHint("Start", "Go to page", Palette.Butter)
            ButtonHint("B", "Back", Palette.Lavender)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f).fillMaxHeight()) {
            val scroll = rememberScrollState()
            val scope = rememberCoroutineScope()
            LaunchedEffect(state.turn) { scroll.scrollTo(0) }
            // One card holds the controller: Left and Right turn, Up and Down scroll, A fits the page the other way.
            FocusCard(
                Modifier.fillMaxWidth().weight(1f).takesFocus(route.key, "page", true).onPreviewKeyEvent { e ->
                    val sideways = e.key == Key.DirectionLeft || e.key == Key.DirectionRight
                    val vertical = e.key == Key.DirectionUp || e.key == Key.DirectionDown
                    if (!sideways && !vertical) return@onPreviewKeyEvent false
                    // Up at the very top leaves for the top bar, like any screen; everything else stays here.
                    if (e.key == Key.DirectionUp && scroll.value == 0) return@onPreviewKeyEvent false
                    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent true
                    when (e.key) {
                        Key.DirectionLeft -> if (state.previous()) Sounds.page() else Sounds.error()
                        Key.DirectionRight -> if (state.next()) Sounds.page() else Sounds.error()
                        Key.DirectionUp -> scope.launch { scroll.scrollPage(-scroll.viewportSize * 0.6f) }
                        Key.DirectionDown -> if (scroll.canScrollForward) scope.launch { scroll.scrollPage(scroll.viewportSize * 0.6f) }
                    }
                    true
                },
                glow = color, shape = RoundedCornerShape(20.dp), onClick = { state.zoomed = !state.zoomed },
            ) {
                ManualView(doc, state, scroll, Modifier.fillMaxSize())
            }
            Spacer(Modifier.height(8.dp))
            PageBar(state, color, Modifier.fillMaxWidth().padding(horizontal = 6.dp))
        }
    }
}

/** Start: type a page number on the controller keyboard. */
private fun askPage(state: ReaderState) {
    TextEntry.ask("Go to page (1 to ${state.count})") { typed ->
        val n = typed.trim().toIntOrNull()
        if (n == null || n !in 1..state.count) Toasts.show("Type a page number from 1 to ${state.count}")
        else { state.go(n - 1); Sounds.page() }
    }
}

/** A D-pad scroll: eased, or at once with animations off. */
private suspend fun ScrollState.scrollPage(by: Float) {
    if (Motion.style == AnimationStyle.Off) scrollBy(by) else animateScrollBy(by)
}

// --- The page ------------------------------------------------------------------------------------------------------

/**
 * One page of [doc], the way [state] asks for it. Pictures fit the room whole, or the width (scrolled by finger, the
 * D-pad or a drag); text is laid out from its blocks. Touch: a tap on the left or right third turns the page, one in
 * the middle fits the page the other way.
 */
@Composable
fun ManualView(doc: ManualDoc, state: ReaderState, scroll: ScrollState, modifier: Modifier = Modifier) {
    Box(
        modifier.background(Palette.NightDeep).pointerInput(state) {
            detectTapGestures { at ->
                when {
                    at.x < size.width / 3f -> if (state.previous()) Sounds.page()
                    at.x > size.width * 2 / 3f -> if (state.next()) Sounds.page()
                    else -> state.zoomed = !state.zoomed
                }
            }
        },
    ) {
        when (doc) {
            is ManualDoc.Text -> TextPage(doc.pages.getOrNull(state.page), state.zoomed, scroll)
            is PagedManual -> PicturePage(doc, state.page, state.zoomed, scroll)
        }
    }
}

@Composable
private fun PicturePage(doc: PagedManual, page: Int, fitWidth: Boolean, scroll: ScrollState) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val maxW = constraints.maxWidth
        val maxH = constraints.maxHeight
        val aspect = doc.aspect(page)
        // Drawn as wide as it will show: the whole width when zoomed, else what the height leaves for the page's shape
        // (unknown until its first drawing: then the width, and the picture is scaled down to fit meanwhile).
        val renderW = if (fitWidth || aspect == null) maxW else minOf(maxW, (maxH * aspect).roundToInt())
        // The page shown stays while the next one draws, so a turn never flashes to an empty screen.
        var shown by remember(doc) { mutableStateOf<Pair<Int, Bitmap>?>(null) }
        LaunchedEffect(doc, page, renderW) {
            val b = withContext(Dispatchers.IO) { doc.page(page, renderW) }
            if (b != null) shown = page to b
            // The next page is drawn ahead, so turning to it is instant.
            withContext(Dispatchers.IO) { doc.page(page + 1, renderW) }
        }
        val s = shown
        if (s == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Pip(48.dp, mood = 1) }
            return@BoxWithConstraints
        }
        val bitmap = remember(s.second) { s.second.asImageBitmap() }
        if (fitWidth) {
            Column(Modifier.fillMaxSize().fadingEdges(scroll).verticalScroll(scroll)) {
                Image(bitmap, null, Modifier.fillMaxWidth().aspectRatio(s.second.width / s.second.height.toFloat()), contentScale = ContentScale.FillWidth)
            }
        } else {
            Image(bitmap, null, Modifier.fillMaxSize().padding(6.dp), contentScale = ContentScale.Fit)
        }
        if (s.first != page) Box(Modifier.align(Alignment.TopEnd).padding(10.dp)) { Pip(28.dp, mood = 1, bounce = false) }
    }
}

/** A page of a text manual, drawn as it reads. [large] sets bigger type, for reading from further away. */
@Composable
private fun TextPage(page: ManualText.Page?, large: Boolean, scroll: ScrollState) {
    val blocks = page?.blocks.orEmpty()
    val k = if (large) 1.25f else 1f
    val body = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp * k, lineHeight = 23.sp * k)
    Column(
        Modifier.fillMaxSize().fadingEdges(scroll).verticalScroll(scroll).padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (blocks.isEmpty()) Text("Nothing on this page.", style = body, color = Palette.Muted)
        for (b in blocks) when (b) {
            is ManualText.Block.Heading -> Text(
                mdInline(b.text),
                Modifier.padding(top = if (b.level <= 2) 6.dp else 2.dp),
                style = when (b.level) {
                    1 -> MaterialTheme.typography.headlineMedium.copy(fontSize = 26.sp * k, lineHeight = 32.sp * k)
                    2 -> MaterialTheme.typography.headlineSmall.copy(fontSize = 22.sp * k, lineHeight = 28.sp * k)
                    else -> MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp * k, lineHeight = 24.sp * k)
                },
                color = if (b.level <= 2) Palette.Text else Palette.Butter,
            )
            is ManualText.Block.Paragraph -> Text(mdInline(b.text), style = body)
            is ManualText.Block.Item -> Row(Modifier.padding(start = (b.indent * 18).dp)) {
                Text(b.number ?: "•", Modifier.widthIn(min = 22.dp).padding(end = 6.dp), style = body, color = Palette.Muted)
                Text(mdInline(b.text), style = body)
            }
            is ManualText.Block.Quote -> Row(Modifier.height(IntrinsicSize.Min)) {
                Box(Modifier.width(3.dp).fillMaxHeight().background(Palette.Lavender, RoundedCornerShape(2.dp)))
                Text(mdInline(b.text), Modifier.padding(start = 12.dp), style = body.copy(fontStyle = FontStyle.Italic), color = Palette.Muted)
            }
            // Maps and tables drawn in text keep their columns: one line each, scrolled sideways when too wide.
            is ManualText.Block.Code -> {
                val side = rememberScrollState()
                Text(
                    b.text,
                    Modifier.fillMaxWidth().background(Palette.Card, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 8.dp)
                        .fadingEdges(side, horizontal = true).horizontalScroll(side),
                    style = body.copy(fontFamily = FontFamily.Monospace, fontSize = 13.sp * k, lineHeight = 17.sp * k), softWrap = false,
                )
            }
            ManualText.Block.Rule -> Box(Modifier.padding(vertical = 4.dp).fillMaxWidth().height(1.dp).background(Palette.Muted.copy(alpha = 0.4f)))
        }
        Spacer(Modifier.height(20.dp))
    }
}

/**
 * The slim page indicator: a thin track filled as far as the page, and the numbers. A finger on the track (a tap or a
 * drag) goes to that page, which is how the bottom screen jumps far without a keyboard.
 */
@Composable
fun PageBar(state: ReaderState, color: Color, modifier: Modifier = Modifier) {
    val count = state.count.coerceAtLeast(1)
    fun pick(x: Float, width: Int) { state.go(((x / width.coerceAtLeast(1)) * count).toInt().coerceIn(0, count - 1)) }
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            Modifier.weight(1f).height(22.dp).pointerInput(state) {
                detectTapGestures { pick(it.x, size.width) }
            }.pointerInput(state) {
                detectHorizontalDragGestures(onDragStart = { pick(it.x, size.width) }) { change, _ -> pick(change.position.x, size.width); change.consume() }
            },
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Palette.Card))
            Box(Modifier.fillMaxWidth((state.page + 1) / count.toFloat()).height(4.dp).clip(RoundedCornerShape(2.dp)).background(color))
        }
        Text("${state.page + 1} / $count", style = MaterialTheme.typography.labelLarge, color = Palette.Muted, maxLines = 1)
    }
}

// --- On the bottom screen, while the game plays ----------------------------------------------------------------------

/** The manual face of the companion (CompanionScreen.kt), open for one game at a time like the notes. */
object CompanionManual {
    var open by mutableStateOf(false)
        private set
    private var forKey: String? = null

    fun show(key: String) { forKey = key; open = true }
    fun hide() { open = false }

    /** The companion now shows [key]'s game: the panel stays open only for the game it was opened for. */
    fun follow(key: String) { if (forKey != key) hide() }
}

@Composable
fun CompanionManualPanel(info: CompanionInfo) {
    val ctx = LocalContext.current
    remember(ctx) { ManualPages.init(ctx) }
    // The path came with the game; failing that, the library in this process (when it is loaded) knows.
    val file = remember(info.key, info.manual) { info.manual?.let { File(it) }?.takeIf { it.exists() } ?: Manuals.forKey(info.key) }
    val (doc, failed) = rememberManualDoc(file)
    val tint = Palette.forName(info.systemId)
    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val state = if (doc != null) remember(doc) { ReaderState(info.key, doc.pageCount) } else null
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TouchChip("‹ Game", Palette.Lavender) { CompanionManual.hide() }
            Text(
                (doc as? ManualDoc.Text)?.pages?.getOrNull(state?.page ?: 0)?.title ?: file?.name ?: "Manual",
                Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (state != null) {
                val text = doc is ManualDoc.Text
                TouchChip(if (text) (if (state.zoomed) "Smaller" else "Larger") else (if (state.zoomed) "Fit page" else "Fit width"), Palette.Mint) {
                    state.zoomed = !state.zoomed
                }
            }
        }
        Box(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(16.dp))) {
            when {
                file == null || failed -> Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Pip(64.dp, mood = 2)
                    Text(
                        if (file == null) "No manual for this game. Put one in ROMs/manuals." else "Couldn't read ${file.name}.",
                        style = MaterialTheme.typography.bodyLarge, color = Palette.Muted, maxLines = 3, overflow = TextOverflow.Ellipsis,
                    )
                }
                doc == null || state == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Pip(56.dp, mood = 1) }
                else -> {
                    val scroll = rememberScrollState()
                    LaunchedEffect(state.turn) { scroll.scrollTo(0) }
                    ManualView(doc, state, scroll, Modifier.fillMaxSize())
                }
            }
        }
        if (state != null) {
            PageBar(state, tint, Modifier.fillMaxWidth().padding(horizontal = 4.dp))
            Text(
                "Tap the sides to turn, the middle to zoom. ${plural(state.count, "page")}.",
                style = MaterialTheme.typography.labelMedium, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun TouchChip(text: String, color: Color, onTap: () -> Unit) {
    Box(
        Modifier.height(40.dp).clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.18f)).clickable(onClick = onTap).padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = MaterialTheme.typography.labelLarge, color = color, maxLines = 1) }
}
