package io.github.zahakj.tandem.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.zahakj.tandem.Libraries
import io.github.zahakj.tandem.R
import io.github.zahakj.tandem.Tx
import io.github.zahakj.tandem.data.Store

/*
 * The shoulders' strip: where L1 and R1 go, on screen. Two looks:
 * - compact: L1, the group before (dimmed), the group you are on (a pill in its colour), the group after, R1;
 * - chips: L1, a row of short names (the "short" of platforms.json for systems: 3DS, GBA, Switch) with the one you
 *   are on filled, R1. It scrolls with you and doubles as a sense of place among many systems; a tap on a chip jumps
 *   there.
 * R1 is the next group in every language; in right to left the row is mirrored, so R1 and the next group sit on the
 * left, where the next things are (GroupPaging.kt). The L1 and R1 glyphs press their buttons when tapped.
 */

/** One group on the strip: its key (GroupPaging's), the short name shown, and its colour. */
data class StripGroup(val key: String, val label: String, val color: Color)

/** What the strip calls a home entry: a system by its short name, Continue as Continue, anything else by its name. */
fun stripLabel(key: String, title: String): String = when {
    key.startsWith("s:") -> Libraries.byId(key.removePrefix("s:"))?.platform?.shortName?.ifBlank { null } ?: title
    GroupPaging.section(key) == GroupPaging.CONTINUE -> Tx.s(R.string.home_strip_continue)
    else -> title
}

/**
 * The strip for [groups] with [current] lit. [chips] > 0 shows that many short names at most (fewer when the room is
 * short); 0 the compact look. [wrap]: L1 on the first group goes round to the last (false: nothing before it shows).
 */
@Composable
fun GroupStrip(
    groups: List<StripGroup>, current: String?, modifier: Modifier = Modifier, chips: Int = 0, wrap: Boolean = true,
    onPick: ((String) -> Unit)? = null,
) {
    if (groups.size < 2) return
    val at = groups.indexOfFirst { it.key == current }.coerceAtLeast(0)
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.awayWhileTrying()) { ButtonHint("L1", "", Palette.Lavender) }
        if (chips > 0) {
            BoxWithConstraints(Modifier.weight(1f, fill = false)) {
                // About 64 dp a chip with its gap: as many as the room holds, never more than asked.
                val room = minOf(chips, (maxWidth / 64.dp).toInt().coerceAtLeast(3))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    for (i in GroupPaging.window(groups.size, at, room)) Chip(groups[i], i == at, onPick)
                }
            }
        } else {
            val (before, after) = GroupPaging.around(groups.map { it.key }, groups[at].key, wrap)
            Side(groups.firstOrNull { it.key == before }, onPick)
            Current(groups, at)
            Side(groups.firstOrNull { it.key == after }, onPick)
        }
        Box(Modifier.awayWhileTrying()) { ButtonHint("R1", "", Palette.Lavender) }
    }
}

@Composable
private fun Side(g: StripGroup?, onPick: ((String) -> Unit)?) {
    if (g == null) { Spacer(Modifier.widthIn(min = 24.dp)); return }
    Text(
        g.label, Modifier.widthIn(max = 110.dp).then(if (onPick != null) Modifier.swallowTaps { onPick(g.key) } else Modifier),
        style = MaterialTheme.typography.labelLarge.overArt(), color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
    )
}

/** The group you are on, sliding in from the way the shoulders went. */
@Composable
private fun Current(groups: List<StripGroup>, at: Int) {
    val still = Motion.style == AnimationStyle.Off || Store.settings.reduceMotion
    val sign = startSign
    AnimatedContent(
        groups[at], contentAlignment = Alignment.Center, label = "strip-current",
        transitionSpec = {
            val n = groups.size
            val from = groups.indexOfFirst { it.key == initialState.key }
            val to = groups.indexOfFirst { it.key == targetState.key }
            // The short way round: from the last group to the first is a step forward.
            var d = if (from < 0 || to < 0) 0 else to - from
            if (d > n / 2) d -= n else if (d < -n / 2) d += n
            val dir = (if (d >= 0) 1 else -1) * sign.toInt()
            if (still) fadeIn(Motion.fade(120)) togetherWith fadeOut(Motion.fade(80))
            else (slideInHorizontally(Motion.move()) { it / 2 * dir } + fadeIn(Motion.fade(160))) togetherWith
                (slideOutHorizontally(Motion.move()) { -it / 2 * dir } + fadeOut(Motion.fade(100)))
        },
    ) { g ->
        Box(
            Modifier.height(28.dp).widthIn(max = 220.dp).clip(RoundedCornerShape(50)).background(g.color).padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                g.label, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold), color = Palette.OnAccent,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun Chip(g: StripGroup, on: Boolean, onPick: ((String) -> Unit)?) {
    val lit by animateFloatAsState(if (on) 1f else 0f, Motion.soft(), label = "strip-chip")
    Box(
        Modifier.height(26.dp).widthIn(max = 120.dp)
            .graphicsLayer { val s = 1f + 0.06f * lit; scaleX = s; scaleY = s }
            .clip(RoundedCornerShape(50))
            .background(if (on) g.color else Palette.Card.copy(alpha = 0.62f))
            .then(if (onPick != null) Modifier.swallowTaps { onPick(g.key) } else Modifier)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            g.label, style = MaterialTheme.typography.labelMedium.copy(fontWeight = if (on) FontWeight.ExtraBold else FontWeight.SemiBold),
            color = if (on) Palette.OnAccent else Palette.Text.copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

/** L1 at the start edge and R1 at the end, for a layout whose groups show themselves (Orbit's ring of orbs). */
@Composable
fun ShoulderEnds(modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.awayWhileTrying()) { ButtonHint("L1", "", Palette.Lavender) }
        Spacer(Modifier.weight(1f))
        Box(Modifier.awayWhileTrying()) { ButtonHint("R1", "", Palette.Lavender) }
    }
}
