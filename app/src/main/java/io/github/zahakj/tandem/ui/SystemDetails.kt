package io.github.zahakj.tandem.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import io.github.zahakj.tandem.Game
import io.github.zahakj.tandem.Platform
import io.github.zahakj.tandem.plural

/**
 * The bottom screen for a highlighted system when the theme pack has a hero for it: the hero fading into the
 * wallpaper, the pack's logo over it, then the name, count and hints. Returns false (drawing nothing) when there is
 * no hero, so the caller shows its usual view.
 */
@Composable
fun SystemHeroDetails(platform: Platform, games: List<Game>, played: Long, hints: @Composable () -> Unit): Boolean {
    val hero = ThemePacks.systemHero(platform.id) ?: return false
    val logo = ThemePacks.systemLogo(platform.id)
    val color = Palette.forName(platform.id)
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().weight(0.58f)) {
            AsyncImage(hero, platform.name, Modifier.fillMaxSize().heroFade(), contentScale = ContentScale.Crop)
            if (logo != null) {
                AsyncImage(
                    logo, platform.name,
                    Modifier.align(Alignment.Center).fillMaxWidth(Themes.live.heroLogoScale ?: 0.6f).height(120.dp).padding(horizontal = 16.dp),
                    contentScale = ContentScale.Fit,
                )
            }
        }
        Column(
            Modifier.fillMaxWidth().weight(0.42f).padding(horizontal = 22.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
        ) {
            Text(platform.name, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(plural(games.size, "game"), style = MaterialTheme.typography.titleMedium, color = color, textAlign = TextAlign.Center, maxLines = 1)
            if (played >= 60) Text("${formatPlayLong(played)} played in here", style = MaterialTheme.typography.bodyMedium, color = Palette.Muted, maxLines = 1)
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) { hints() }
        }
    }
    return true
}
