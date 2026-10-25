package io.github.zahakj.tandem.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.zahakj.tandem.Game
import io.github.zahakj.tandem.data.SortOrder
import io.github.zahakj.tandem.data.Store
import kotlinx.coroutines.delay

/*
 * L2 / R2 in a long list of games: jump to the previous or next letter (sorted by name), year (by release year)
 * or star rating, or a page of games for the other orders. A big bubble shows where you landed, like the letter
 * scrubber on a phone's contacts. The grid on screen registers its games; the jump just moves the remembered
 * focus and asks the grid to take it, so it works the same in the grid, list and carousel views.
 */
object QuickJump {
    private class Target(val route: String, val keys: List<String>, val labels: List<String>)

    /** Lists on screen, newest last: a folder popup's grid sits over the screen's and wins until it closes. */
    private val targets = ArrayList<Target>()

    /** The bubble's text, and a counter so the same letter twice still shows again. */
    var bubble by mutableStateOf<String?>(null)
        private set
    var serial by mutableIntStateOf(0)
        private set

    /** The label a game is grouped under for jumping, for the current sort. */
    fun labelOf(g: Game, index: Int, sort: SortOrder): String = when (sort) {
        SortOrder.Name -> g.title.trimStart().firstOrNull()?.uppercaseChar()?.let { if (it.isLetter()) it.toString() else "#" } ?: "#"
        SortOrder.Year -> g.meta.year?.toString() ?: "Unknown year"
        SortOrder.Rating -> g.meta.rating?.let { "★".repeat((it * 5).toInt().coerceIn(0, 5)).ifEmpty { "Unrated" } } ?: "Unrated"
        else -> "Page ${index / PAGE + 1}"
    }

    /** Called by the grid showing [games] on [route]. */
    @Composable
    fun Register(route: String, games: List<Game>, ordered: Boolean = false) {
        val sort = if (ordered) SortOrder.Recent else Store.settings.sort
        DisposableEffect(route, games, sort) {
            val t = Target(route, games.map { it.key }, games.mapIndexed { i, g -> labelOf(g, i, sort) })
            targets.add(t)
            onDispose { targets.remove(t) }
        }
    }

    /** L2 (-1) or R2 (+1). Returns false when there is no long list on screen. */
    fun jump(by: Int): Boolean {
        val t = targets.lastOrNull() ?: return false
        if (t.keys.size < 2) return false
        val at = t.keys.indexOf(FocusMemory.get(t.route)).coerceAtLeast(0)
        val here = t.labels[at]
        val next = if (by > 0) {
            (at + 1 until t.keys.size).firstOrNull { t.labels[it] != here } ?: (t.keys.size - 1)
        } else {
            // Back to the start of this group, or (already there) of the one before.
            val start = (at downTo 0).takeWhile { t.labels[it] == here }.last()
            if (start < at) start else {
                val prev = (start - 1).coerceAtLeast(0)
                (prev downTo 0).takeWhile { t.labels[it] == t.labels[prev] }.lastOrNull() ?: 0
            }
        }
        if (next == at) { Sounds.error(); return true }
        FocusMemory.remember(t.route, t.keys[next])
        Nav.focusTick++
        bubble = t.labels[next]
        serial++
        Sounds.move()
        return true
    }

    private const val PAGE = 24
}

/** The bubble that shows where L2 / R2 landed. Composed once over the screens. */
@Composable
fun QuickJumpBubble() {
    var visible by androidx.compose.runtime.remember { mutableStateOf(false) }
    val serial = QuickJump.serial
    LaunchedEffect(serial) {
        if (serial == 0) return@LaunchedEffect
        visible = true
        delay(700)
        visible = false
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AnimatedVisibility(
            visible,
            enter = scaleIn(spring(Spring.DampingRatioMediumBouncy), initialScale = 0.6f) + fadeIn(tween(90)),
            exit = scaleOut(tween(160), targetScale = 0.9f) + fadeOut(tween(160)),
        ) {
            Column(
                Modifier.widthIn(min = 110.dp, max = 360.dp).background(Palette.CardHigh, RoundedCornerShape(28.dp)).padding(horizontal = 26.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(QuickJump.bubble.orEmpty(), style = MaterialTheme.typography.displaySmall.copy(fontSize = 44.sp), color = Palette.Accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("L2 · R2 to jump", style = MaterialTheme.typography.labelLarge, color = Palette.Muted, maxLines = 1)
            }
        }
    }
}
