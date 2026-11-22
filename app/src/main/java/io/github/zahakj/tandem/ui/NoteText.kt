package io.github.zahakj.tandem.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import io.github.zahakj.tandem.Button
import io.github.zahakj.tandem.R
import io.github.zahakj.tandem.Tx
import io.github.zahakj.tandem.data.NoteDoc
import kotlinx.coroutines.delay
import java.io.File

/*
 * What notes share on both screens: typing with Android's keyboard ([NoteInput], [NoteTextField]), Markdown drawn as
 * it reads ([MdText]), and the pictures, checkboxes and glossary cells of a note.
 */

/** A request to type something with Android's keyboard, on the top screen. */
object NoteInput {
    class Request(val title: String, val initial: String, val multiLine: Boolean, val onDone: (String) -> Unit)

    var request by mutableStateOf<Request?>(null)
        private set

    /** Opens the keyboard over a field holding [initial]; [onDone] gets the text (also on B, for a note's text). */
    fun ask(title: String, initial: String = "", multiLine: Boolean = false, onDone: (String) -> Unit) {
        request = Request(title, initial, multiLine, onDone)
    }

    fun close() { request = null; Nav.focusTick++ }
}

/**
 * The typing popup: a field with Android's keyboard up. Start (or Done) keeps the text; B keeps a note's text and
 * leaves a name as it was. Drawn by the Notes screen, above its other popups.
 */
@Composable
fun NoteInputOverlay() {
    val req = NoteInput.request
    val shown = rememberLatest(req)
    val text = remember(req) { mutableStateOf(TextFieldValue(req?.initial.orEmpty(), TextRange(req?.initial.orEmpty().length))) }
    val done = { req?.let { r -> NoteInput.close(); r.onDone(text.value.text) }; Unit }
    PanelGuard(req != null)
    InterceptButtons(req != null) { b -> if (b == Button.Start) done(); true }
    OverlayPanel(req != null, onClose = { if (req?.multiLine == true) done() else NoteInput.close() }) {
        val r = shown ?: return@OverlayPanel
        Box(Modifier.fillMaxSize().imePadding(), contentAlignment = Alignment.Center) {
            PanelSurface(Modifier.width(660.dp)) {
                Text(r.title, Modifier.padding(horizontal = 4.dp), style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.size(10.dp))
                NoteTextField(text, r.multiLine, Modifier.fillMaxWidth().weight(1f, fill = false).heightIn(min = 48.dp, max = if (r.multiLine) 260.dp else 56.dp), onDone = done)
                Spacer(Modifier.size(12.dp))
                Row(Modifier.padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    ButtonHint("Start", Tx.s(R.string.apps_note_done), Palette.Mint, onClick = done)
                    ButtonHint("B", if (r.multiLine) Tx.s(R.string.apps_note_done) else Tx.s(R.string.apps_note_cancel), Palette.Lavender)
                }
            }
        }
    }
}

/**
 * A text field that brings up Android's keyboard as soon as it shows. [onDone] runs on the keyboard's Done (one-line
 * fields). It counts as a highlight in its window (FocusCards), so a D-pad press never pulls focus away from it.
 */
@Composable
fun NoteTextField(state: MutableState<TextFieldValue>, multiLine: Boolean, modifier: Modifier = Modifier, onDone: () -> Unit) {
    val requester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val view = LocalView.current
    val token = remember { Any() }
    // Again once the window has the focus: the companion only takes it when typing starts.
    val windowFocused = androidx.compose.ui.platform.LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(windowFocused) {
        delay(60)
        runCatching { requester.requestFocus() }
        keyboard?.show()
    }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { FocusCards.held(view.rootView, token, false) } }
    BasicTextField(
        state.value, { state.value = it },
        modifier.clip(RoundedCornerShape(14.dp)).background(Palette.NightDeep).padding(12.dp)
            .focusRequester(requester).onFocusChanged { FocusCards.held(view.rootView, token, it.isFocused) },
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = Palette.Text, lineHeight = 21.sp),
        cursorBrush = SolidColor(Palette.Mint),
        singleLine = !multiLine,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Sentences,
            imeAction = if (multiLine) ImeAction.Default else ImeAction.Done,
        ),
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { onDone() }),
    )
}

// --- Markdown as it reads ----------------------------------------------------------------------------------------

private enum class LineKind { H1, H2, H3, Bullet, Number, Quote, Code, Rule, Plain }

private class MdLine(val kind: LineKind, val text: String, val mark: String = "", val indent: Int = 0)

private val heading = Regex("""^(#{1,6})\s+(.*)$""")
private val bullet = Regex("""^(\s*)[-*+]\s+(.*)$""")
private val number = Regex("""^(\s*)(\d+[.)])\s+(.*)$""")
private val rule = Regex("""^([-*_])(\s*\1){2,}\s*$""")

private fun mdLines(text: String): List<MdLine> {
    val out = ArrayList<MdLine>()
    var fence = false
    for (raw in text.lines()) {
        val t = raw.trimStart()
        if (t.startsWith("```") || t.startsWith("~~~")) { fence = !fence; continue }
        if (fence) { out += MdLine(LineKind.Code, raw); continue }
        val h = heading.matchEntire(t)
        val b = bullet.matchEntire(raw)
        val n = number.matchEntire(raw)
        out += when {
            h != null -> MdLine(when (h.groupValues[1].length) { 1 -> LineKind.H1; 2 -> LineKind.H2; else -> LineKind.H3 }, h.groupValues[2])
            rule.matches(t) -> MdLine(LineKind.Rule, "")
            b != null -> MdLine(LineKind.Bullet, b.groupValues[2], "•", b.groupValues[1].length / 2)
            n != null -> MdLine(LineKind.Number, n.groupValues[3], n.groupValues[2], n.groupValues[1].length / 2)
            t.startsWith(">") -> MdLine(LineKind.Quote, t.removePrefix(">").trimStart())
            else -> MdLine(LineKind.Plain, t)
        }
    }
    return out
}

/** A line's inline Markdown (bold, italic, code, struck, links) as styled text. */
fun mdInline(s: String, link: Color = Palette.Sky): AnnotatedString {
    val i = NoteDoc.Inline.parse(s)
    return buildAnnotatedString {
        append(i.text)
        for (sp in i.spans) addStyle(
            when (sp.style) {
                NoteDoc.Style.Bold -> SpanStyle(fontWeight = FontWeight.ExtraBold)
                NoteDoc.Style.Italic -> SpanStyle(fontStyle = FontStyle.Italic)
                NoteDoc.Style.Code -> SpanStyle(fontFamily = FontFamily.Monospace, background = Palette.NightDeep)
                NoteDoc.Style.Strike -> SpanStyle(textDecoration = TextDecoration.LineThrough)
                NoteDoc.Style.Link -> SpanStyle(color = link, textDecoration = TextDecoration.Underline)
            },
            sp.start, sp.end,
        )
    }
}

/** A paragraph of Markdown drawn as it reads: headings, lists, quotes, code. At most [maxLines] lines of it. */
@Composable
fun MdText(text: String, modifier: Modifier = Modifier, maxLines: Int = 40, color: Color = Palette.Text) {
    val lines = remember(text) { mdLines(text) }
    val body = MaterialTheme.typography.bodyLarge.copy(color = color, lineHeight = 21.sp)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        for (l in lines.take(maxLines)) {
            val styled = l.text
            when (l.kind) {
                LineKind.H1 -> Text(mdInline(styled), style = MaterialTheme.typography.headlineSmall, color = color)
                LineKind.H2 -> Text(mdInline(styled), style = MaterialTheme.typography.titleLarge, color = color)
                LineKind.H3 -> Text(mdInline(styled), style = MaterialTheme.typography.titleMedium, color = color)
                LineKind.Bullet, LineKind.Number -> Row(Modifier.padding(start = (l.indent * 16).dp)) {
                    Text(l.mark, Modifier.widthIn(min = 18.dp), style = body.copy(color = Palette.Muted))
                    Text(mdInline(styled), style = body)
                }
                LineKind.Quote -> Row {
                    Box(Modifier.width(3.dp).heightIn(min = 20.dp).background(Palette.Lavender, RoundedCornerShape(2.dp)))
                    Text(mdInline(styled), Modifier.padding(start = 10.dp), style = body.copy(color = Palette.Muted, fontStyle = FontStyle.Italic))
                }
                LineKind.Code -> Text(
                    styled, Modifier.fillMaxWidth().background(Palette.NightDeep, RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 1.dp),
                    style = body.copy(fontFamily = FontFamily.Monospace, fontSize = 13.sp), maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                LineKind.Rule -> Box(Modifier.padding(vertical = 6.dp).fillMaxWidth().height(1.dp).background(Palette.Muted.copy(alpha = 0.4f)))
                LineKind.Plain -> Text(mdInline(styled), style = body)
            }
        }
        if (lines.size > maxLines) Text("…", style = body.copy(color = Palette.Muted))
    }
}

// --- Pieces of a note ----------------------------------------------------------------------------------------------

/** A picture in a note, decoded at [px] and loaded again whenever the file changes (a drawing edited). */
@Composable
fun NotePicture(file: File?, px: Int, modifier: Modifier = Modifier, crop: Boolean = false) {
    val ctx = LocalContext.current
    if (file == null) {
        Box(modifier.background(Palette.NightDeep), contentAlignment = Alignment.Center) {
            Text(Tx.s(R.string.apps_note_missing_picture), style = MaterialTheme.typography.labelMedium, color = Palette.Muted, maxLines = 1)
        }
        return
    }
    val stamp = file.lastModified()
    val request = remember(file, stamp, px) {
        val key = coil.memory.MemoryCache.Key("note:${file.path}:$stamp:$px")
        ImageRequest.Builder(ctx).data(file).size(px).precision(coil.size.Precision.INEXACT).memoryCacheKey(key).crossfade(120).build()
    }
    AsyncImage(request, null, modifier, contentScale = if (crop) ContentScale.Crop else ContentScale.Fit)
}

/** A checklist box, ticked or not. */
@Composable
fun TaskBox(done: Boolean, color: Color = Palette.Mint, size: Dp = 22.dp) {
    Canvas(Modifier.size(size)) {
        val s = this.size.minDimension
        val r = CornerRadius(s * 0.28f)
        if (done) drawRoundRect(color, cornerRadius = r) else drawRoundRect(Palette.Muted, cornerRadius = r, style = Stroke(s * 0.1f))
        if (done) {
            val ink = Palette.OnAccent
            drawLine(ink, Offset(s * 0.26f, s * 0.52f), Offset(s * 0.43f, s * 0.7f), s * 0.12f, StrokeCap.Round)
            drawLine(ink, Offset(s * 0.43f, s * 0.7f), Offset(s * 0.75f, s * 0.32f), s * 0.12f, StrokeCap.Round)
        }
    }
}

/** A checklist item as it reads: its box, then its text (struck through once done). */
@Composable
fun TaskLine(text: String, done: Boolean, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        TaskBox(done)
        Spacer(Modifier.width(12.dp))
        Text(
            mdInline(text.ifEmpty { Tx.s(R.string.apps_note_empty_line) }),
            style = MaterialTheme.typography.bodyLarge.copy(textDecoration = if (done) TextDecoration.LineThrough else null),
            color = if (done) Palette.Muted else Palette.Text, maxLines = 3, overflow = TextOverflow.Ellipsis,
        )
    }
}

/** One glossary cell: its drawn symbol, or its text. */
@Composable
fun GlossaryCell(note: File, cell: String, modifier: Modifier = Modifier, header: Boolean = false, pictureSize: Dp = 48.dp) {
    val picture = remember(cell) { NoteDoc.cellPicture(cell) }
    if (picture != null) {
        val file = remember(note, picture, io.github.zahakj.tandem.NoteStore.version) { io.github.zahakj.tandem.NoteStore.picture(note, picture) }
        NotePicture(file, tilePx(pictureSize), modifier.size(pictureSize).clip(RoundedCornerShape(8.dp)))
    } else {
        val style: TextStyle = if (header) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyLarge
        Text(mdInline(cell), modifier, style = style, color = if (header) Palette.Muted else Palette.Text, maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
}
