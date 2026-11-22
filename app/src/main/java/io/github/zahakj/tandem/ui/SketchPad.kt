package io.github.zahakj.tandem.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.zahakj.tandem.Button
import io.github.zahakj.tandem.NoteStore
import io.github.zahakj.tandem.R
import io.github.zahakj.tandem.Tx
import io.github.zahakj.tandem.data.SketchCrop
import io.github.zahakj.tandem.data.SketchData
import io.github.zahakj.tandem.data.SketchStroke
import io.github.zahakj.tandem.data.Sketches
import io.github.zahakj.tandem.strokePath
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/*
 * Drawing in notes: a sketch on paper, a symbol for a glossary, or marks and a crop on a screenshot. A finger draws
 * on either screen; with the controller the D-pad moves a cursor and A puts the pen down and lifts it. The note
 * links to the flattened PNG; the strokes are kept beside it (NoteStore.saveSketch).
 */

enum class SketchTool(@StringRes private val res: Int) {
    Pen(R.string.apps_sketch_pen), Eraser(R.string.apps_sketch_eraser), Crop(R.string.apps_sketch_crop);
    val label: String get() = Tx.s(res)
}

/** A drawing being edited: strokes and crop with undo, the pen, and the controller's cursor. */
class SketchState(private val start: SketchData, val background: ImageBitmap?) {
    val w = start.w
    val h = start.h
    private val side = max(w, h).toFloat()
    var strokes by mutableStateOf(start.strokes)
        private set
    var crop by mutableStateOf(start.crop)
        private set
    var tool by mutableStateOf(SketchTool.Pen)
    var ink by mutableIntStateOf(0)
    var width by mutableIntStateOf(1)
    /** The stroke being drawn, in picture pixels. */
    val live = mutableStateListOf<Float>()
    /** The crop being dragged out: its two corners. */
    var cropping by mutableStateOf<Pair<Offset, Offset>?>(null)
        private set
    /** The controller's cursor, once the D-pad has moved it; [penDown] while A has put the pen down. */
    var cursor by mutableStateOf<Offset?>(null)
        private set
    var penDown by mutableStateOf(false)
        private set
    private val undos = ArrayList<Pair<List<SketchStroke>, SketchCrop?>>()
    private val redos = ArrayList<Pair<List<SketchStroke>, SketchCrop?>>()
    private var erased = false

    val changed: Boolean get() = strokes != start.strokes || crop != start.crop

    fun result(): SketchData = start.copy(strokes = strokes, crop = crop)

    private fun snapshot() { undos += strokes to crop; redos.clear() }

    fun begin(at: Offset) {
        when (tool) {
            SketchTool.Pen -> { live.clear(); live += at.x; live += at.y }
            SketchTool.Eraser -> { snapshot(); erased = false; erase(at) }
            SketchTool.Crop -> cropping = at to at
        }
    }

    fun extend(at: Offset) {
        when (tool) {
            SketchTool.Pen -> if (live.isNotEmpty()) { live += at.x; live += at.y }
            SketchTool.Eraser -> erase(at)
            SketchTool.Crop -> cropping = cropping?.let { it.first to at }
        }
    }

    fun end() {
        when (tool) {
            SketchTool.Pen -> if (live.isNotEmpty()) {
                val pts = Sketches.thin(live.toList(), side * 0.002f).map { (it * 10f).roundToInt() / 10f }
                snapshot()
                strokes = strokes + SketchStroke(Sketches.INKS[ink], Sketches.WIDTHS[width] * side, pts)
                live.clear()
            }
            SketchTool.Eraser -> if (!erased) { undos.removeLastOrNull() }
            SketchTool.Crop -> cropping?.let { (a, b) ->
                cropping = null
                Sketches.crop(a.x, a.y, b.x, b.y, w, h)?.let { snapshot(); crop = it }
            }
        }
    }

    private fun erase(at: Offset) {
        val after = Sketches.erase(strokes, at.x, at.y, side * 0.015f)
        if (after.size != strokes.size) { strokes = after; erased = true }
    }

    fun undo() {
        val last = undos.removeLastOrNull() ?: return
        redos += strokes to crop
        strokes = last.first; crop = last.second
    }

    fun redo() {
        val next = redos.removeLastOrNull() ?: return
        undos += strokes to crop
        strokes = next.first; crop = next.second
    }

    fun clear() {
        if (strokes.isEmpty() && crop == null) return
        snapshot(); strokes = emptyList(); crop = null
    }

    fun fullPicture() { if (crop != null) { snapshot(); crop = null } }

    fun nextTool() {
        if (penDown) press()
        tool = SketchTool.entries[(tool.ordinal + 1) % SketchTool.entries.size]
    }

    /** D-pad: moves the cursor by [dx], [dy] steps (faster while held), drawing if the pen is down. */
    fun move(dx: Int, dy: Int, repeat: Int) {
        val step = side * 0.006f * (1f + (repeat / 4f).coerceAtMost(5f))
        val from = cursor ?: Offset(w / 2f, h / 2f)
        val to = Offset((from.x + dx * step).coerceIn(0f, w.toFloat()), (from.y + dy * step).coerceIn(0f, h.toFloat()))
        cursor = to
        if (penDown) extend(to)
    }

    /** A: puts the pen down at the cursor, or lifts it. */
    fun press() {
        val at = cursor ?: Offset(w / 2f, h / 2f).also { cursor = it }
        if (penDown) { penDown = false; end() } else { penDown = true; begin(at) }
    }
}

/** A drawing open for editing: where it is saved and what happens then. */
class SketchSession(
    val title: String,
    val state: SketchState,
    private val target: NoteStore.DrawTarget,
    /** "top" or "companion": the screen it is open on. */
    val owner: String,
    private val onSaved: (File) -> Unit,
    /** Says something to the user (a toast on the screen it is open on). */
    private val say: (String) -> Unit,
    /** Left without a drawing (Discard, or a new sheet left blank). */
    val onDiscard: () -> Unit,
) {
    /** Saves the drawing (when anything changed), then [after] on the main thread. */
    fun save(after: () -> Unit) {
        if (!state.changed) {
            if (!target.png.isFile) onDiscard()
            after()
            return
        }
        val data = state.result()
        NoteStore.later({ NoteStore.saveSketch(target.png, data) }) { ok ->
            if (ok == true) onSaved(target.png) else say(Tx.s(R.string.apps_sketch_save_failed))
            after()
        }
    }
}

object SketchPad {
    var session by mutableStateOf<SketchSession?>(null)
        private set

    /**
     * Opens a drawing for [note]: a blank sheet ([existing] null; [square] for a glossary symbol), a drawing made
     * here, or marks on a picture. [onSaved] gets the PNG once it is saved (a marked picture is a new file).
     */
    fun open(
        note: File, existing: File?, square: Boolean, title: String, owner: String = "top", say: (String) -> Unit = { Toasts.show(it) },
        onDiscard: () -> Unit = {}, onSaved: (File) -> Unit,
    ) {
        NoteStore.later({
            val t = NoteStore.drawTarget(note, existing, square) ?: return@later null
            val bg = t.background?.let { NoteStore.decode(it)?.asImageBitmap() }
            t to bg
        }) { r ->
            val (t, bg) = r ?: run { say(Tx.s(R.string.apps_sketch_open_failed)); return@later }
            session = SketchSession(title, SketchState(t.start, bg), t, owner, onSaved, say, onDiscard)
        }
    }

    /** Saves the open drawing and closes it. */
    fun done() { session?.save { close() } }

    /** Closes the open drawing without saving it. */
    fun discard() { session?.onDiscard?.invoke(); close() }

    fun close() { session = null; Nav.focusTick++ }
}

/** The drawing itself: paper or the picture, the strokes, the crop and the cursor. Takes finger strokes. */
@Composable
fun SketchCanvas(s: SketchState, modifier: Modifier = Modifier) {
    var box by remember { mutableStateOf(IntSize.Zero) }
    val paper = remember { Color(Sketches.argb(Sketches.PAPER)) }
    val paths = remember(s.strokes) { s.strokes.map { strokePath(it.points) } }
    fun fit(): Triple<Float, Float, Float> {
        val scale = minOf(box.width / s.w.toFloat(), box.height / s.h.toFloat()).coerceAtLeast(0.0001f)
        return Triple(scale, (box.width - s.w * scale) / 2f, (box.height - s.h * scale) / 2f)
    }
    Canvas(
        modifier.onSizeChanged { box = it }.pointerInput(s) {
            awaitEachGesture {
                val down = awaitFirstDown()
                fun at(p: Offset): Offset { val (k, ox, oy) = fit(); return Offset(((p.x - ox) / k).coerceIn(0f, s.w.toFloat()), ((p.y - oy) / k).coerceIn(0f, s.h.toFloat())) }
                s.begin(at(down.position))
                down.consume()
                while (true) {
                    val e = awaitPointerEvent()
                    val c = e.changes.firstOrNull { it.id == down.id } ?: break
                    if (!c.pressed) break
                    s.extend(at(c.position))
                    c.consume()
                }
                s.end()
            }
        },
    ) {
        val (k, ox, oy) = fit()
        val bg = s.background
        if (bg != null) {
            drawImage(bg, dstOffset = IntOffset(ox.roundToInt(), oy.roundToInt()), dstSize = IntSize((s.w * k).roundToInt(), (s.h * k).roundToInt()))
        } else {
            drawRect(paper, Offset(ox, oy), Size(s.w * k, s.h * k))
        }
        withTransform({ translate(ox, oy); scale(k, k, Offset.Zero) }) {
            s.strokes.forEachIndexed { i, st -> drawPath(paths[i], Color(Sketches.argb(st.color)), style = Stroke(st.width, cap = StrokeCap.Round, join = StrokeJoin.Round)) }
            if (s.live.isNotEmpty()) {
                drawPath(strokePath(s.live.toList()), Color(Sketches.argb(Sketches.INKS[s.ink])), style = Stroke(Sketches.WIDTHS[s.width] * max(s.w, s.h), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
        val c = s.cropping?.let { (a, b) -> Sketches.crop(a.x, a.y, b.x, b.y, s.w, s.h) ?: SketchCrop(0, 0, 0, 0) } ?: s.crop
        if (c != null) dimOutside(Offset(ox + c.left * k, oy + c.top * k), Size(c.width * k, c.height * k), Offset(ox, oy), Size(s.w * k, s.h * k))
        s.cursor?.let { p ->
            val at = Offset(ox + p.x * k, oy + p.y * k)
            val r = 7.dp.toPx()
            drawCircle(Color.White, r + 2.dp.toPx(), at, style = Stroke(2.dp.toPx()))
            drawCircle(Color(Sketches.argb(Sketches.INKS[s.ink])), r, at, style = if (s.penDown) androidx.compose.ui.graphics.drawscope.Fill else Stroke(2.5.dp.toPx()))
        }
    }
}

/** Darkens the picture outside the crop and outlines it. */
private fun DrawScope.dimOutside(at: Offset, size: Size, all: Offset, allSize: Size) {
    val shade = Color.Black.copy(alpha = 0.55f)
    drawRect(shade, all, Size(allSize.width, at.y - all.y))
    drawRect(shade, Offset(all.x, at.y + size.height), Size(allSize.width, all.y + allSize.height - at.y - size.height))
    drawRect(shade, Offset(all.x, at.y), Size(at.x - all.x, size.height))
    drawRect(shade, Offset(at.x + size.width, at.y), Size(all.x + allSize.width - at.x - size.width, size.height))
    drawRect(Color.White, at, size, style = Stroke(1.5.dp.toPx()))
}

/** Touch controls for the pen: inks, widths, tools, undo and redo, clear. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SketchTools(s: SketchState, modifier: Modifier = Modifier) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Sketches.INKS.forEachIndexed { i, ink ->
            val on = s.ink == i && s.tool == SketchTool.Pen
            Box(
                Modifier.size(34.dp).clip(CircleShape).background(if (on) Palette.Text.copy(alpha = 0.25f) else Color.Transparent)
                    .clickable { s.ink = i; s.tool = SketchTool.Pen },
                contentAlignment = Alignment.Center,
            ) { Box(Modifier.size(if (on) 24.dp else 20.dp).clip(CircleShape).background(Color(Sketches.argb(ink)))) }
        }
        Sketches.WIDTHS.indices.forEach { i ->
            val on = s.width == i
            Box(
                Modifier.size(34.dp).clip(CircleShape).background(if (on) Palette.Text.copy(alpha = 0.25f) else Color.Transparent).clickable { s.width = i },
                contentAlignment = Alignment.Center,
            ) { Box(Modifier.size((5 + i * 5).dp).clip(CircleShape).background(Palette.Text)) }
        }
        for (t in SketchTool.entries) ToolChip(t.label, s.tool == t, Palette.Sky) { s.tool = t }
        ToolChip(Tx.s(R.string.apps_sketch_undo), false, Palette.Butter) { s.undo() }
        ToolChip(Tx.s(R.string.apps_sketch_redo), false, Palette.Butter) { s.redo() }
        if (s.crop != null) ToolChip(Tx.s(R.string.apps_sketch_whole), false, Palette.Lavender) { s.fullPicture() }
        ToolChip(Tx.s(R.string.apps_sketch_clear), false, Palette.Peach) { s.clear() }
    }
}

@Composable
private fun ToolChip(label: String, on: Boolean, color: Color, onTap: () -> Unit) {
    Box(
        Modifier.height(34.dp).clip(RoundedCornerShape(50)).background(if (on) color.copy(alpha = 0.32f) else Palette.CardHigh)
            .clickable(onClick = onTap).padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, style = MaterialTheme.typography.labelLarge, color = if (on) Palette.Text else Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis) }
}

/**
 * The drawing on the top screen, over everything in the Notes app. D-pad moves the cursor, A puts the pen down and
 * lifts it, X undoes (Select redoes), Y changes the tool, L1 / R1 the ink, L2 / R2 the width; Start or B saves.
 */
@Composable
fun SketchPadOverlay() {
    val session = SketchPad.session?.takeIf { it.owner == "top" }
    val shown = rememberLatest(session)
    val done = { SketchPad.done() }
    PanelGuard(session != null)
    InterceptButtons(session != null) { b ->
        val s = session?.state ?: return@InterceptButtons true
        when (b) {
            Button.X -> s.undo()
            Button.Select -> s.redo()
            Button.Y -> { s.nextTool(); Toasts.show(s.tool.label) }
            Button.L1 -> s.ink = (s.ink + Sketches.INKS.size - 1) % Sketches.INKS.size
            Button.R1 -> s.ink = (s.ink + 1) % Sketches.INKS.size
            Button.L2 -> s.width = (s.width - 1).coerceAtLeast(0)
            Button.R2 -> s.width = (s.width + 1).coerceAtMost(Sketches.WIDTHS.size - 1)
            Button.Start -> done()
            else -> Unit
        }
        true
    }
    OverlayPanel(session != null, onClose = done) {
        val ses = shown ?: return@OverlayPanel
        val s = ses.state
        val requester = remember(ses) { FocusRequester() }
        val view = LocalView.current
        val token = remember { Any() }
        LaunchedEffect(ses) { runCatching { requester.requestFocus() } }
        PanelSurface(Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(ses.title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                ToolChip(Tx.s(R.string.apps_sketch_discard), false, Palette.Peach) { SketchPad.discard() }
                Box(Modifier.size(8.dp))
                ToolChip(Tx.s(R.string.apps_sketch_done), true, Palette.Mint) { done() }
            }
            SketchTools(s, Modifier.fillMaxWidth().padding(vertical = 8.dp))
            SketchCanvas(
                s,
                Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(12.dp))
                    .focusRequester(requester).onFocusChanged { FocusCards.held(view.rootView, token, it.isFocused) }
                    .onKeyEvent { e ->
                        val down = e.type == KeyEventType.KeyDown
                        val repeat = e.nativeKeyEvent.repeatCount
                        when (e.key) {
                            Key.DirectionLeft -> { if (down) s.move(-1, 0, repeat); true }
                            Key.DirectionRight -> { if (down) s.move(1, 0, repeat); true }
                            Key.DirectionUp -> { if (down) s.move(0, -1, repeat); true }
                            Key.DirectionDown -> { if (down) s.move(0, 1, repeat); true }
                            Key.DirectionCenter, Key.Enter, Key.ButtonA -> { if (down && repeat == 0) s.press(); true }
                            else -> false
                        }
                    }
                    .focusable(),
            )
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                ButtonHint("A", if (s.penDown) Tx.s(R.string.apps_sketch_lift) else s.tool.label, Palette.Mint)
                ButtonHint("Y", Tx.s(R.string.apps_sketch_tool), Palette.Sky)
                ButtonHint("X", Tx.s(R.string.apps_sketch_undo), Palette.Butter)
                ButtonHint("L1", Tx.s(R.string.apps_sketch_ink), Palette.Lavender)
                ButtonHint("R2", Tx.s(R.string.apps_sketch_width), Palette.Lavender)
                ButtonHint("Start", Tx.s(R.string.apps_sketch_done), Palette.Mint)
            }
        }
    }
}

