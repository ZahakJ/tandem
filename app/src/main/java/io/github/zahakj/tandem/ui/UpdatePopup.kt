package io.github.zahakj.tandem.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import io.github.zahakj.tandem.BuildConfig
import io.github.zahakj.tandem.Button
import io.github.zahakj.tandem.Buttons
import io.github.zahakj.tandem.DateKind
import io.github.zahakj.tandem.Dates
import io.github.zahakj.tandem.Lang
import io.github.zahakj.tandem.NoteSection
import io.github.zahakj.tandem.R
import io.github.zahakj.tandem.Release
import io.github.zahakj.tandem.Releases
import io.github.zahakj.tandem.Tx
import io.github.zahakj.tandem.UpdateState
import io.github.zahakj.tandem.Updates
import io.github.zahakj.tandem.WhatsNewRules
import io.github.zahakj.tandem.data.Store
import io.github.zahakj.tandem.ui.whatsnew.Arrivals
import io.github.zahakj.tandem.ui.whatsnew.arrive
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.abs
import kotlin.math.sign

/**
 * A new Tandem (Updates.kt), previewed before it is installed: the jump from this version to the new one with its size
 * and date, the release notes as a card per part (Releases.sections), and, when the release carries screenshots
 * (`whatsnew-*.webp|png`), a gallery of them, big, that L1 / R1 or a swipe walk. Update and Not now along the bottom.
 * Opened only by the user (Start › the update row, or Settings › Play & system › Updates), never by itself, and only
 * then are the screenshots fetched. Up and Down scroll the notes; the buttons keep the focus.
 */
object UpdatePopup {
    const val ROUTE = "update"

    var open by mutableStateOf(false)
        private set

    /** Opens on Update: picking the notice was the ask, so A once more installs. */
    fun show() { FocusMemory.forget(ROUTE); FocusMemory.remember(ROUTE, "yes"); open = true }

    fun close() { open = false; Nav.focusTick++ }
}

@Composable
fun UpdateOverlay() {
    val rel = Updates.found
    val visible = UpdatePopup.open && rel != null
    val shown = rememberLatest(rel)
    val ctx = LocalContext.current
    val shots = Updates.shots
    var shot by remember { mutableIntStateOf(0) }
    var shotDir by remember { mutableIntStateOf(1) }
    fun step(by: Int) {
        val to = (shot + by).coerceIn(0, (shots.size - 1).coerceAtLeast(0))
        if (to != shot) { shotDir = by.sign; shot = to; Sounds.move() }
    }
    LaunchedEffect(visible) { if (visible) { shot = 0; Updates.preview(ctx) } }
    InterceptButtons(visible) { b ->
        if (!Buttons.repeat) when (b) {
            Button.L1 -> step(-1)
            Button.R1 -> step(1)
            else -> Unit
        }
        true
    }
    OverlayPanel(visible, onClose = { UpdatePopup.close() }) {
        val r = shown ?: return@OverlayPanel
        UpdatePanel(r, shots, shot.coerceIn(0, (shots.size - 1).coerceAtLeast(0)), shotDir, ::step)
    }
}

@Composable
private fun UpdatePanel(r: Release, shots: List<File>, shot: Int, shotDir: Int, step: (Int) -> Unit) {
    val ctx = LocalContext.current
    val state = Updates.state
    val target = rememberFocusTarget(UpdatePopup.ROUTE, listOf("no", "yes"))
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val rtl = isRtl
    val sections = remember(r.notes) { Releases.sections(r.notes) }
    // The parts arrive one after another as the popup opens (Reduce motion: all at once).
    Arrivals { Column(
        Modifier.fillMaxSize()
            .clip(RoundedCornerShape(30.dp))
            .background(Palette.Card.copy(alpha = 0.97f))
            .drawBehind {
                val w = size.width
                val end = if (rtl) 0f else w
                drawCircle(Brush.radialGradient(listOf(Palette.Peach.copy(alpha = 0.22f), Color.Transparent), Offset(end, 0f), w * 0.55f), w * 0.55f, Offset(end, 0f))
                drawCircle(Brush.radialGradient(listOf(Palette.Accent.copy(alpha = 0.1f), Color.Transparent), Offset(w - end, size.height), w * 0.45f), w * 0.45f, Offset(w - end, size.height))
            }
            .border(1.dp, Palette.Peach.copy(alpha = 0.22f), RoundedCornerShape(30.dp))
            .trapFocus()
            .swallowTaps()
            .onPreviewKeyEvent { e ->
                val by = when (e.key) { Key.DirectionUp -> -180f; Key.DirectionDown -> 180f; else -> return@onPreviewKeyEvent false }
                if (e.type == KeyEventType.KeyDown) scope.launch { scroll.animateScrollBy(by) }
                true
            }
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        VersionJump(r, Modifier.arrive(0))
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth().weight(1f)) {
            if (shots.isNotEmpty()) {
                Gallery(shots, shot, shotDir, step, Modifier.weight(1.2f).fillMaxHeight().arrive(1))
                Spacer(Modifier.width(16.dp))
            }
            Notes(sections, shots.isEmpty(), Modifier.weight(1f).fillMaxHeight().fadingEdges(scroll).verticalScroll(scroll))
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            // Where it stands, or why it didn't work; a debug build is told up front that it can't take the release;
            // otherwise, when the new version brings a What's new, that Pip will show it.
            val tour = Store.settings.whatsNew && Releases.compare(WhatsNewRules.minorOf(r.version), WhatsNewRules.minorOf(BuildConfig.VERSION_NAME)) > 0
            val line = when {
                state is UpdateState.Problem -> state.text
                state != UpdateState.Idle -> Updates.statusLine()
                BuildConfig.DEBUG -> Tx.s(R.string.accounts_update_debug_note)
                tour -> Tx.s(R.string.whatsnew_preview_tour)
                else -> null
            }
            Text(
                line.orEmpty(), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis,
                color = if (state is UpdateState.Problem) Palette.Peach else Palette.Muted,
            )
            Spacer(Modifier.width(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PillButton(Tx.s(R.string.accounts_update_not_now), Palette.Mint, Modifier.takesFocus(UpdatePopup.ROUTE, "no", target == "no")) { UpdatePopup.close() }
                val yes = when (state) {
                    is UpdateState.Downloading -> Tx.s(R.string.accounts_update_percent, state.pct)
                    UpdateState.Verifying -> Tx.s(R.string.accounts_update_verifying_now)
                    is UpdateState.Problem -> Tx.s(R.string.accounts_update_try_again)
                    else -> Tx.s(R.string.accounts_update_update)
                }
                PillButton(yes, Palette.Peach, Modifier.takesFocus(UpdatePopup.ROUTE, "yes", target == "yes"), filled = true) { Updates.install(ctx) }
            }
        }
    } }
}

/** Pip, "A new Tandem", this version → the new one, and the download's size and the day it came out. */
@Composable
private fun VersionJump(r: Release, modifier: Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Pip(54.dp, mood = 1)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                Tx.s(R.string.whatsnew_preview_eyebrow), style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold),
                color = Palette.Peach, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.clip(RoundedCornerShape(50)).background(Palette.CardHigh).padding(horizontal = 10.dp, vertical = 2.dp)) {
                    Text(Updates.current, style = MaterialTheme.typography.titleMedium, color = Palette.Muted, maxLines = 1)
                }
                // An arrow towards the end side: mirrored in right to left, as the versions are.
                GlyphIcon(Glyph.Forward, Palette.Muted, Modifier.padding(horizontal = 6.dp).size(20.dp))
                Box(Modifier.clip(RoundedCornerShape(50)).background(Palette.Peach).padding(horizontal = 12.dp, vertical = 2.dp)) {
                    Text(
                        Tx.s(R.string.accounts_update_title, r.version), style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                        color = Palette.OnAccent, maxLines = 1,
                    )
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (r.apkSize > 0) Fact(Glyph.Download, Tx.s(R.string.whatsnew_preview_size, android.text.format.Formatter.formatShortFileSize(Tx.context, r.apkSize)))
            val day = remember(r.published, Lang.revision.intValue) {
                runCatching { java.time.Instant.parse(r.published).toEpochMilli() }.getOrNull()?.let { Dates.text(it, DateKind.Added) }
            }
            if (day != null) Fact(Glyph.Clock, Tx.s(R.string.whatsnew_preview_date, day))
        }
    }
}

@Composable
private fun Fact(glyph: Glyph, text: String) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(Palette.CardHigh.copy(alpha = 0.8f)).padding(start = 3.dp, end = 10.dp, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlyphChip(glyph, Palette.Sky, size = 20.dp, round = true)
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

/** The release's screenshots, one at a time and as big as the room allows; L1 / R1, the dots or a swipe walk them. */
@Composable
private fun Gallery(shots: List<File>, at: Int, dir: Int, step: (Int) -> Unit, modifier: Modifier) {
    val sign = startSign
    val rtl = isRtl
    val still = showcaseStill()
    Column(modifier) {
        AnimatedContent(
            at,
            Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(20.dp)).background(Palette.NightDeep)
                .border(1.dp, Palette.Peach.copy(alpha = 0.25f), RoundedCornerShape(20.dp))
                .pointerInput(rtl) {
                    var dx = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { dx = 0f },
                        onDragEnd = { if (abs(dx) > 48.dp.toPx()) step(if ((if (rtl) 1f else -1f) * sign(dx) > 0f) 1 else -1) },
                    ) { change, amount -> dx += amount; change.consume() }
                },
            transitionSpec = {
                if (still) fadeIn(tween(120)) togetherWith fadeOut(tween(80))
                else (slideInHorizontally(tween(340)) { (it / 3 * dir * sign).toInt() } + fadeIn(tween(240))) togetherWith
                    (slideOutHorizontally(tween(300)) { (-it / 4 * dir * sign).toInt() } + fadeOut(tween(160)))
            },
            label = "update-shot",
        ) { i ->
            AsyncImage(shots[i], null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        }
        if (shots.size > 1) Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            ButtonHint("L1", "", Palette.Lavender)
            Spacer(Modifier.width(4.dp))
            ButtonHint("R1", Tx.s(R.string.whatsnew_preview_pictures), Palette.Lavender)
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                shots.indices.forEach { i ->
                    val on = i == at
                    Box(
                        Modifier.size(if (on) 18.dp else 7.dp, 7.dp).clip(CircleShape)
                            .background(if (on) Palette.Peach else Palette.Muted.copy(alpha = 0.4f))
                            .swallowTaps { step(i - at) },
                    )
                }
            }
        }
    }
}

/**
 * The notes, a card per part: its title by a glyph and colour picked from the words in it, and its points. Beside a
 * gallery the cards stack; alone they share two columns, each card going to the shorter one.
 */
@Composable
private fun Notes(sections: List<NoteSection>, wide: Boolean, modifier: Modifier) {
    Column(modifier) {
        if (sections.isEmpty()) {
            Text(Tx.s(R.string.accounts_update_no_notes), style = MaterialTheme.typography.bodyMedium, color = Palette.Muted)
            return@Column
        }
        if (!wide) {
            sections.forEachIndexed { i, s -> NoteCard(s, i, Modifier.padding(bottom = 10.dp).arrive(2 + i)) }
        } else {
            val (left, right) = remember(sections) { balance(sections) }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for (col in listOf(left, right)) Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    col.forEach { i -> NoteCard(sections[i], i, Modifier.arrive(1 + i)) }
                }
            }
        }
    }
}

/** Splits the parts into two columns of about equal length, in order, each part going to the shorter column. */
private fun balance(sections: List<NoteSection>): Pair<List<Int>, List<Int>> {
    val a = ArrayList<Int>()
    val b = ArrayList<Int>()
    var ha = 0
    var hb = 0
    sections.forEachIndexed { i, s ->
        val h = 60 + s.items.sumOf { 40 + it.length }
        if (ha <= hb) { a += i; ha += h } else { b += i; hb += h }
    }
    return a to b
}

private val NoteColors = listOf<() -> Color>({ Palette.Peach }, { Palette.Lavender }, { Palette.Sky }, { Palette.Mint }, { Palette.Butter })

/** A glyph for a part of the notes, from the (English) words of its title. */
private fun noteGlyph(title: String): Glyph {
    val t = title.lowercase()
    fun has(vararg w: String) = w.any { it in t }
    return when {
        has("fix", "bug") -> Glyph.Wand
        has("layout", "home", "screen") -> Glyph.Layers
        has("grid", "edit", "tile", "icon") -> Glyph.Pencil
        has("art", "theme", "look", "wallpaper") -> Glyph.Image
        has("library", "game", "import") -> Glyph.Gamepad
        has("sound", "music") -> Glyph.Music
        has("control", "button") -> Glyph.Gamepad
        has("speed", "perform", "faster") -> Glyph.Motion
        has("privacy", "secur", "sign") -> Glyph.Shield
        has("language", "translat") -> Glyph.Text
        else -> Glyph.Sparkle
    }
}

@Composable
private fun NoteCard(s: NoteSection, i: Int, modifier: Modifier) {
    val color = NoteColors[i % NoteColors.size]()
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(lerp(Palette.CardHigh, color, 0.07f))
            .border(1.dp, color.copy(alpha = 0.24f), RoundedCornerShape(18.dp)).padding(12.dp),
    ) {
        if (s.title.isNotEmpty()) Row(Modifier.padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            GlyphChip(noteGlyph(s.title), color, size = 26.dp, round = true)
            Spacer(Modifier.width(8.dp))
            Text(
                s.title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold), color = Palette.readable(color),
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }
        s.items.forEach { item ->
            Row(Modifier.padding(top = 5.dp)) {
                Box(Modifier.padding(top = 7.dp).size(6.dp).clip(CircleShape).background(color))
                Spacer(Modifier.width(8.dp))
                Text(item, style = MaterialTheme.typography.bodyMedium, color = Palette.Text.copy(alpha = 0.88f))
            }
        }
    }
}
