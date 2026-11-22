package io.github.zahakj.tandem.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.zahakj.tandem.Collections
import io.github.zahakj.tandem.Launcher
import io.github.zahakj.tandem.Libraries
import io.github.zahakj.tandem.Privacy
import io.github.zahakj.tandem.Selected
import io.github.zahakj.tandem.Selection
import io.github.zahakj.tandem.DateKind
import io.github.zahakj.tandem.Dates
import io.github.zahakj.tandem.R
import io.github.zahakj.tandem.Tx
import io.github.zahakj.tandem.data.ExtrasStore
import io.github.zahakj.tandem.data.GameStatus
import io.github.zahakj.tandem.data.HomeStore
import io.github.zahakj.tandem.data.Store
import io.github.zahakj.tandem.data.Trophy
import io.github.zahakj.tandem.data.TrophyFacts
import io.github.zahakj.tandem.data.TrophyList
import io.github.zahakj.tandem.data.TrophyTier
import io.github.zahakj.tandem.data.playStatus

/*
 * Tandem's trophies on screen: the check that notices new ones (after a session or a change of status) and
 * celebrates them, and the Trophies tab of Stats: the cabinet of finished games and every trophy, earned or not.
 * Hidden games count for nothing here, so a trophy never gives one away.
 */
object Trophies {
    /** What the trophies see now. Reads the library: call it off the main thread when it can be. */
    fun facts(): TrophyFacts {
        val games = Libraries.games.filter { !Privacy.isHidden(it) }
        val sessions = HomeStore.sessions.filter { !Privacy.isHiddenKey(it.k) }
        return TrophyFacts(games.map { it.meta }, sessions, games.size)
    }

    /**
     * Records the trophies earned since the last check and celebrates them. The very first check records what was
     * already earned without a word, so nobody is greeted by a dozen at once.
     */
    fun check() {
        // Before the library is in, most trophies would look unearned and be "found" later all at once.
        if (Libraries.all == null) return
        var fresh: List<Trophy> = emptyList()
        inBackground({
            val f = facts()
            val now = System.currentTimeMillis()
            val known = ExtrasStore.trophies.unlocked
            val earned = TrophyList.withMaster.filter { it.earned(f) }
            fresh = earned.filter { it.id !in known }
            if (fresh.isEmpty()) return@inBackground
            val first = known.isEmpty()
            ExtrasStore.updateTrophies { r -> r.copy(unlocked = r.unlocked + fresh.associate { it.id to now }) }
            if (first) fresh = emptyList()
        }) {
            val t = fresh.maxByOrNull { it.tier.ordinal } ?: return@inBackground
            Sounds.achievement()
            val more = fresh.size - 1
            Toasts.show(if (more > 0) Tx.p(R.plurals.stats_trophy_new_more, more, more, t.tier.label, t.title) else Tx.s(R.string.stats_trophy_new, t.tier.label, t.title, t.detail), 5000)
        }
    }
}

/** A tier's metal: the light and the shade of its cup. */
private fun TrophyTier.metal(): Pair<Color, Color> = when (this) {
    TrophyTier.Bronze -> Color(0xFFE2A46A) to Color(0xFF8A5226)
    TrophyTier.Silver -> Color(0xFFF1F4F8) to Color(0xFF8C96A3)
    TrophyTier.Gold -> Color(0xFFFFE07A) to Color(0xFFC08A12)
    TrophyTier.Platinum -> Color(0xFFEAF6FF) to Color(0xFF7FA6CC)
}

/** A trophy cup in [tier]'s metal, with a glint; [earned] false draws its outline only. */
@Composable
fun TrophyCup(tier: TrophyTier, earned: Boolean, size: Dp, modifier: Modifier = Modifier) {
    val (light, shade) = tier.metal()
    val dim = Palette.Muted.copy(alpha = 0.45f)
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val bowl = Path().apply {
            moveTo(w * 0.24f, h * 0.12f)
            lineTo(w * 0.76f, h * 0.12f)
            cubicTo(w * 0.76f, h * 0.48f, w * 0.64f, h * 0.6f, w * 0.5f, h * 0.6f)
            cubicTo(w * 0.36f, h * 0.6f, w * 0.24f, h * 0.48f, w * 0.24f, h * 0.12f)
            close()
        }
        val stroke = Stroke(w * 0.06f)
        if (!earned) {
            drawPath(bowl, dim, style = stroke)
            drawArc(dim, 90f, 180f, false, Offset(w * 0.1f, h * 0.18f), Size(w * 0.2f, h * 0.22f), style = stroke)
            drawArc(dim, -90f, 180f, false, Offset(w * 0.7f, h * 0.18f), Size(w * 0.2f, h * 0.22f), style = stroke)
            drawLine(dim, Offset(w * 0.5f, h * 0.6f), Offset(w * 0.5f, h * 0.76f), w * 0.06f)
            drawRoundRect(dim, Offset(w * 0.3f, h * 0.76f), Size(w * 0.4f, h * 0.12f), CornerRadius(w * 0.04f), style = stroke)
            return@Canvas
        }
        val metal = Brush.linearGradient(listOf(light, shade, light.copy(alpha = 0.9f)), Offset(0f, 0f), Offset(w, h))
        drawArc(shade, 90f, 180f, false, Offset(w * 0.1f, h * 0.18f), Size(w * 0.2f, h * 0.22f), style = Stroke(w * 0.07f))
        drawArc(shade, -90f, 180f, false, Offset(w * 0.7f, h * 0.18f), Size(w * 0.2f, h * 0.22f), style = Stroke(w * 0.07f))
        drawPath(bowl, metal)
        drawRect(shade, Offset(w * 0.45f, h * 0.58f), Size(w * 0.1f, h * 0.2f))
        drawRoundRect(metal, Offset(w * 0.28f, h * 0.76f), Size(w * 0.44f, h * 0.14f), CornerRadius(w * 0.04f))
        // A glint down the bowl's left side.
        drawLine(Color.White.copy(alpha = 0.7f), Offset(w * 0.34f, h * 0.18f), Offset(w * 0.37f, h * 0.42f), w * 0.04f)
        if (tier == TrophyTier.Platinum) drawCircle(Color.White.copy(alpha = 0.85f), w * 0.04f, Offset(w * 0.62f, h * 0.22f))
    }
}

/** The Trophies tab: a summary, the cabinet of finished games, then every trophy, earned ones first. */
@Composable
fun TrophiesTab() {
    val ctx = LocalContext.current
    val route = "stats:trophies"
    val meta = Store.meta
    val sessions = HomeStore.sessions
    val record = ExtrasStore.trophies
    val facts = remember(meta, sessions, Libraries.all, Privacy.view) { Trophies.facts() }
    val beaten = remember(meta, Libraries.all, Privacy.view) {
        Collections.visibleGames().filter { !Privacy.isHidden(it) && it.meta.playStatus?.beaten == true }.sortedByDescending { it.meta.finishedAt }
    }
    val trophies = remember(facts, record) {
        TrophyList.withMaster.sortedWith(compareByDescending<Trophy> { it.earned(facts) }.thenByDescending { record.unlocked[it.id] ?: 0L }.thenByDescending { it.progress(facts) })
    }
    val earnedCount = trophies.count { it.earned(facts) }
    // Sections, each one lazy item: the summary, the cabinet, then the trophies two to a row.
    val rows = trophies.chunked(2)
    val keys = listOf("summary") + beaten.map { "fin:${it.key}" } + trophies.map { "t:${it.id}" }
    val target = rememberFocusTarget(route, keys)
    val list = rememberRouteListState(route)
    val targetItem = when {
        target == null -> -1
        target == "summary" -> 0
        target.startsWith("fin:") -> 1
        else -> (if (beaten.isEmpty()) 1 else 2) + rows.indexOfFirst { r -> r.any { "t:${it.id}" == target } }.coerceAtLeast(0)
    }
    KeepInView(list, targetItem)
    LazyColumn(
        Modifier.fillMaxSize().fadingEdges(list),
        state = list,
        contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = FocusRoom, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item("summary") {
            FocusCard(
                Modifier.fillMaxWidth().height(104.dp).takesFocus(route, "summary", target == "summary"), glow = Palette.Butter,
                onFocus = { FocusMemory.remember(route, "summary"); Selection.current = Selected.Home },
                onClick = { Toasts.show(Tx.s(R.string.stats_trophies_about)) },
            ) {
                Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Palette.Butter.copy(alpha = 0.25f), Palette.Card))))
                Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    TrophyCup(if (earnedCount == trophies.size) TrophyTier.Platinum else TrophyTier.Gold, earnedCount > 0, 64.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(Tx.p(R.plurals.stats_trophies_earned, trophies.size, earnedCount, trophies.size), style = MaterialTheme.typography.headlineSmall, maxLines = 1)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            for (tier in TrophyTier.entries) {
                                val n = trophies.count { it.tier == tier && it.earned(facts) }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    TrophyCup(tier, true, 18.dp)
                                    Text(" $n", style = MaterialTheme.typography.labelLarge, color = Palette.Muted)
                                }
                            }
                            Spacer(Modifier.weight(1f))
                            Text(Tx.p(R.plurals.stats_trophies_finished, beaten.size), style = MaterialTheme.typography.labelLarge, color = Palette.Butter, maxLines = 1)
                        }
                    }
                }
            }
        }
        if (beaten.isNotEmpty()) item("cabinet") {
            Column {
                SectionTitle(Tx.s(R.string.stats_trophies_cabinet), Palette.Butter)
                val row = rememberLazyListState()
                LazyRow(Modifier.fadingEdges(row, horizontal = true).focusRow(), state = row, horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(horizontal = FocusRoomSide, vertical = FocusRoom)) {
                    items(beaten, key = { it.key }) { g ->
                        val key = "fin:${g.key}"
                        val s = g.meta.playStatus
                        Column(Modifier.width(104.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            GameCard(g, Modifier.size(104.dp).takesFocus(route, key, key == target), onFocus = { FocusMemory.remember(route, key); Selection.current = Selected.GameItem(g) }, onClick = { Launcher.launch(ctx, g) }, showTitle = false)
                            // A small plaque under each: the cup for finished or completed, and when.
                            Row(Modifier.padding(top = 4.dp).clip(RoundedCornerShape(6.dp)).background(Palette.CardHigh).padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                TrophyCup(if (s == GameStatus.Completed) TrophyTier.Platinum else TrophyTier.Gold, true, 14.dp)
                                Text(
                                    " " + (g.meta.finishedAt.takeIf { it > 0 }?.let { Dates.main(it, DateKind.MonthYear) } ?: s?.label.orEmpty()),
                                    style = MaterialTheme.typography.labelSmall, color = Palette.Muted, maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }
        }
        items(rows, key = { r -> "r:" + r.first().id }) { r ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for (t in r) TrophyCard(t, facts, record.unlocked[t.id], Modifier.weight(1f).takesFocus(route, "t:${t.id}", "t:${t.id}" == target), route)
                if (r.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun TrophyCard(t: Trophy, facts: TrophyFacts, at: Long?, modifier: Modifier, route: String) {
    val earned = t.earned(facts)
    val color = t.tier.metal().first.let { if (earned) it else Palette.Muted }
    FocusCard(
        modifier.height(84.dp), glow = if (earned) t.tier.metal().first else Palette.Lavender,
        onFocus = { FocusMemory.remember(route, "t:${t.id}"); Selection.current = Selected.Home },
        onClick = { Toasts.show(if (earned) Tx.s(R.string.stats_trophy_said, t.title, t.detail) else Tx.s(R.string.stats_trophy_progress_said, t.detail, progressWords(t, facts))) },
    ) {
        if (earned) Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(t.tier.metal().first.copy(alpha = 0.16f), Palette.Card))))
        Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            // Locked: the cup's outline inside a ring that fills as you get closer.
            Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
                if (!earned) {
                    val p = t.progress(facts)
                    val ring = t.tier.metal().first
                    Canvas(Modifier.fillMaxSize()) {
                        val sw = 3.dp.toPx()
                        val inset = sw / 2
                        drawArc(Palette.Muted.copy(alpha = 0.18f), 0f, 360f, false, Offset(inset, inset), Size(size.width - sw, size.height - sw), style = Stroke(sw))
                        if (p > 0f) drawArc(ring, -90f, 360f * p, false, Offset(inset, inset), Size(size.width - sw, size.height - sw), style = Stroke(sw, cap = androidx.compose.ui.graphics.StrokeCap.Round))
                    }
                }
                TrophyCup(t.tier, earned, if (earned) 46.dp else 32.dp)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                FitText(t.title, MaterialTheme.typography.titleSmall, color = if (earned) Palette.Text else Palette.Muted, minScale = 0.7f)
                Text(t.detail, style = MaterialTheme.typography.labelMedium, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                if (earned) {
                    // The main calendar only: a third line on a small card has no room for two dates.
                    Text(
                        at?.let { Tx.s(R.string.stats_trophy_earned_on, t.tier.label, Dates.main(it, DateKind.Date)) } ?: t.tier.label,
                        style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1,
                    )
                } else {
                    FitText(progressWords(t, facts), MaterialTheme.typography.labelSmall, color = Palette.Muted)
                }
            }
        }
    }
}

/** "4 / 10", or hours for the time trophies ("12 h / 100 h"). */
private fun progressWords(t: Trophy, f: TrophyFacts): String {
    val have = t.have(f).coerceAtMost(t.goal)
    return if (t.goal >= 3600) Tx.s(R.string.stats_trophy_hours, (have / 3600).toInt(), (t.goal / 3600).toInt()) else "$have / ${t.goal}"
}
