package io.github.zahakj.tandem.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.zahakj.tandem.Libraries
import io.github.zahakj.tandem.Selected
import io.github.zahakj.tandem.Selection
import io.github.zahakj.tandem.Collections
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/*
 * Game search (X) also finds the places around the games: systems, Android apps and settings. They show as a row
 * of chips above the game results, so "snes" opens the SNES, "retroarch" opens the app, and "music" jumps to the
 * music settings, all from the same search. Hidden games' systems stay out unless hidden games are showing.
 */

class QuickFind(
    val key: String,
    val title: String,
    val kind: String,
    val color: Color,
    val icon: @Composable () -> Unit,
    val go: () -> Unit,
)

/** Systems, apps and settings matching the normalized query [q] (two letters or more). */
@Composable
fun rememberQuickFinds(q: String): List<QuickFind> {
    val ctx = LocalContext.current
    val apps by produceState<List<AppEntry>>(emptyList()) { value = withContext(Dispatchers.IO) { Dock.allApps(ctx) } }
    if (q.length < 2) return emptyList()
    val systems = Libraries.all.orEmpty().filter { lib ->
        Collections.visible(lib.games).isNotEmpty() &&
            (normalize(lib.platform.name).contains(q) || normalize(lib.platform.shortName).startsWith(q) || normalize(lib.platform.id) == q)
    }.take(3).map { lib ->
        QuickFind("qf:s:${lib.platform.id}", lib.platform.name, "System", Palette.forName(lib.platform.id), { SystemIcon(lib.platform, 30.dp) }) {
            Nav.closeOverlays(); Nav.go(Route.System(lib.platform.id))
        }
    }
    val appHits = apps.filter { a -> normalize(a.label).split(" ").any { it.startsWith(q) } || normalize(a.label).startsWith(q) }.take(2).map { a ->
        QuickFind("qf:a:${a.pkg}", a.label, "App", Palette.Sky, { AppIcon(a.pkg, Modifier.size(30.dp)) }) { Nav.closeOverlays(); Dock.open(ctx, a.pkg) }
    }
    val settings = matchHits(settingsHits(), q, 3).map { h ->
        QuickFind("qf:set:${h.key}|${h.where}", h.title, h.where.substringBefore(" › More"), h.color, { if (h.icon != null) GlyphChip(h.icon, h.color) else GlyphChip(Glyph.Info, h.color) }) {
            Nav.closeOverlays(); h.go()
        }
    }
    return systems + appHits + settings
}

/** The chips above the game results. [target] is the results' focus target. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuickFindRow(finds: List<QuickFind>, routeKey: String, active: Boolean, target: String?) {
    Column(Modifier.fillMaxWidth()) {
        Text("Also found", style = MaterialTheme.typography.labelLarge, color = Palette.Muted, maxLines = 1)
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            finds.forEach { f ->
                FocusCard(
                    Modifier.height(48.dp).widthIn(max = 260.dp).takesFocus(routeKey, f.key, active && target == f.key),
                    glow = f.color, shape = RoundedCornerShape(16.dp),
                    onFocus = { Selection.current = Selected.Setting(f.title, f.kind, null, f.color, "Open") },
                    onClick = f.go,
                ) {
                    Row(Modifier.padding(horizontal = 10.dp).height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) { f.icon() }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(f.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(f.kind, style = MaterialTheme.typography.labelMedium, color = f.color, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}
