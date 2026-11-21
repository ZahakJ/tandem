package io.github.zahakj.tandem.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/*
 * The bar over a browsable grid (Systems, Android apps): a search field that opens the on-screen keyboard (X does
 * too) and filter tabs with their counts, so a long list is narrowed to what you want instead of scrolled through.
 */

/** Height of the bar's field and tabs. */
val BrowseBarHeight = 44.dp

/** The search field: [query], or [hint] while it is empty. A (or X anywhere on the screen) opens the keyboard. */
@Composable
fun SearchField(query: String, hint: String, modifier: Modifier, onFocus: () -> Unit, onClick: () -> Unit) {
    val shape = RoundedCornerShape(BrowseBarHeight / 2)
    FocusCard(modifier.height(BrowseBarHeight), glow = Palette.Sky, shape = shape, lifts = false, onFocus = onFocus, onClick = onClick) {
        Row(
            Modifier.fillMaxSize().background(Palette.NightDeep.copy(alpha = 0.35f)).border(1.5.dp, Palette.Sky.copy(alpha = if (query.isEmpty()) 0.2f else 0.55f), shape)
                .padding(start = 14.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(18.dp)) { GlyphIcon(Glyph.Search, Palette.Sky) }
            Spacer(Modifier.width(10.dp))
            Text(
                query.ifEmpty { hint }, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall,
                color = if (query.isEmpty()) Palette.Muted else Palette.Text, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            ButtonHint("X", "", Palette.Sky, tappable = false)
        }
    }
}

/**
 * One filter tab: [label] and its [count] in a bubble, filled in [color] while [selected]. Tabs act on A, not on
 * focus, so moving along them to the grid never changes what the grid shows.
 */
@Composable
fun FilterTab(label: String, count: Int, color: Color, selected: Boolean, modifier: Modifier, onFocus: () -> Unit = {}, onClick: () -> Unit) {
    val back by animateColorAsState(if (selected) color else color.copy(alpha = 0.14f), Motion.fade(180), label = "tab-back")
    val ink = if (selected) Palette.OnAccent else color
    FocusCard(modifier.height(BrowseBarHeight), glow = color, shape = RoundedCornerShape(BrowseBarHeight / 2), lifts = false, onFocus = onFocus, onClick = onClick) {
        Row(Modifier.fillMaxHeight().background(back).padding(start = 14.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.titleSmall, color = ink, maxLines = 1)
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.height(30.dp).background(if (selected) Palette.OnAccent.copy(alpha = 0.18f) else color.copy(alpha = 0.18f), RoundedCornerShape(15.dp))
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center,
            ) { Text("$count", style = MaterialTheme.typography.labelLarge, color = ink, maxLines = 1) }
        }
    }
}
