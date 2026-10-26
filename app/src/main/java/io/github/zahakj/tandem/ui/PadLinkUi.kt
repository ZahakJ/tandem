package io.github.zahakj.tandem.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.zahakj.tandem.PadLink

/**
 * Controller passthrough on the in-game companion (PadLink.kt): the "Play with <name>" offer while a controller that
 * just connected waits for its A or Start, and short notices ("Playing with …", "Back to the built-in controls").
 * The companion never has the controller, so the offer is answered on the pad itself, or by touch here.
 */
@Composable
fun PadLinkCard(modifier: Modifier = Modifier) {
    val offer = PadLink.offer
    val notice = PadLink.notice
    val shown = offer?.name ?: notice
    AnimatedVisibility(
        shown != null, modifier,
        enter = fadeIn() + slideInVertically { it / 2 }, exit = fadeOut() + slideOutVertically { it / 2 },
    ) {
        Column(
            Modifier.padding(16.dp).fillMaxWidth()
                .shadow(14.dp, RoundedCornerShape(22.dp))
                .background(Palette.Card, RoundedCornerShape(22.dp))
                .padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (offer != null) {
                Text(
                    "Play with ${offer.name}?", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Press A or Start on it. It takes over the built-in controls in this game.",
                    style = MaterialTheme.typography.bodySmall, color = Palette.Muted, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    ButtonHint("A", "Play with it", Palette.Mint, onClick = { PadLink.accept() })
                    Spacer(Modifier.width(18.dp))
                    ButtonHint("B", "Not now", Palette.Lavender, onClick = { PadLink.decline() })
                }
            } else if (notice != null) {
                Text(notice, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
