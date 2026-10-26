package io.github.zahakj.tandem.ui

import io.github.zahakj.tandem.Privacy

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import io.github.zahakj.tandem.Game
import io.github.zahakj.tandem.RetroAchievements
import io.github.zahakj.tandem.Selected
import io.github.zahakj.tandem.Selection
import io.github.zahakj.tandem.data.HomeStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Bumped to reload the achievements tab after signing in or out. */
private var raVersion by mutableStateOf(0)

/** Asks for the username, then the Web API key, checks them, and turns achievements on. */
fun raSignIn() {
    if (io.github.zahakj.tandem.NetGate.closed) { Toasts.show("Private mode keeps Tandem offline. Turn it off to sign in"); return }
    TextEntry.ask("RetroAchievements username", HomeStore.ra.user) { name ->
        val user = name.trim()
        if (user.isEmpty()) return@ask
        TextEntry.ask("Web API key (retroachievements.org › Settings › Keys)", "", secret = true) { k ->
            val key = k.trim()
            if (key.isEmpty()) return@ask
            Toasts.show("Checking with RetroAchievements…")
            kotlinx.coroutines.MainScope().launch {
                val p = RetroAchievements.verify(user, key)
                if (p == null) {
                    Toasts.show("That didn't work. Check the name, the key and your connection")
                } else {
                    HomeStore.updateRa { it.copy(user = p.user, key = key, enabled = true, matches = emptyMap()) }
                    RetroAchievements.invalidate()
                    raVersion++
                    Toasts.show("Signed in as ${p.user} 🏆")
                    Sounds.achievement()
                }
            }
        }
    }
}

@Composable
fun achievementsSettings(): SettingSection {
    val ra = HomeStore.ra
    val signedIn = ra.user.isNotBlank() && ra.key.isNotBlank()
    return SettingSection(
        "RetroAchievements",
        buildList {
            add(
                SettingRow(
                    "Account", if (signedIn) ra.user else "Sign in", Palette.Butter, Glyph.Trophy,
                    "Sign in with your RetroAchievements username and Web API key (retroachievements.org › Settings › Keys) to see trophies here, in Stats and on the bottom screen. Never for hidden games.",
                    keywords = "retroachievements ra trophies login sign in cheevos",
                ) { raSignIn() },
            )
            if (signedIn) {
                add(
                    SettingRow(
                        "Show achievements", onOff(ra.enabled), Palette.Butter, Glyph.Trophy, toggle = ra.enabled,
                        help = "Show a game's achievements and your progress. Off, Tandem doesn't talk to RetroAchievements at all.",
                        keywords = "retroachievements trophies",
                    ) {
                        HomeStore.updateRa { it.copy(enabled = !it.enabled) }
                        raVersion++
                    },
                )
                add(
                    SettingRow(
                        "Look up games again", "${ra.matches.count { it.value > 0 }} matched", Palette.Butter, Glyph.Refresh,
                        "Forget which RetroAchievements game each of yours matched, and match them again as you browse. Useful after replacing ROM files.",
                        keywords = "retroachievements rematch hash",
                    ) {
                        RetroAchievements.forgetMatches()
                        Toasts.show("I'll match your games again as you browse")
                    },
                )
                add(
                    SettingRow("Sign out", "", Palette.Peach, Glyph.Key, "Forget your RetroAchievements account on this device.", keywords = "retroachievements logout") {
                        HomeStore.updateRa { RaAccountSignedOut }
                        RetroAchievements.invalidate()
                        raVersion++
                        Toasts.show("Signed out of RetroAchievements")
                    },
                )
            }
        },
    )
}

private val RaAccountSignedOut = io.github.zahakj.tandem.data.RaAccount()

/** The achievements tab on the stats screen: profile and recently earned. */
@Composable
fun AchievementsTab() {
    val route = "stats:2"
    if (!RetroAchievements.enabled) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                PipNote(
                    "Bring your RetroAchievements",
                    "Sign in with your username and Web API key to see your trophies here and on the bottom screen.\n" +
                        "Tandem only talks to RetroAchievements when this is on, and never about hidden games.",
                    mood = 1,
                )
                FocusCard(
                    Modifier.width(220.dp).height(44.dp).remembersFocus(route, "signin", true), glow = Palette.Butter,
                    onFocus = { Selection.current = Selected.Home },
                    onClick = { if (HomeStore.ra.key.isNotBlank()) { HomeStore.updateRa { it.copy(enabled = true) }; raVersion++ } else raSignIn() },
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(if (HomeStore.ra.key.isNotBlank()) "Turn achievements on" else "Sign in", style = MaterialTheme.typography.titleSmall, color = Palette.Butter)
                    }
                }
            }
        }
        return
    }
    val version = raVersion
    // Whether each request has come back, so a failed one says so instead of "loading" forever.
    var profileDone by remember(version) { mutableStateOf(false) }
    var recentDone by remember(version) { mutableStateOf(false) }
    val profile by produceState<RetroAchievements.Profile?>(null, version) { value = RetroAchievements.profile(); profileDone = true }
    val recent by produceState<List<RetroAchievements.Earned>?>(null, version) { value = RetroAchievements.recent(); recentDone = true }
    val target = rememberFocusTarget(route, listOf("profile") + recent.orEmpty().map { "${it.date}/${it.title}" })
    val grid = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    LazyVerticalGrid(
        GridCells.Adaptive(240.dp),
        Modifier.fillMaxSize().fadingEdges(grid),
        state = grid,
        contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = FocusRoom, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item("profile", span = { GridItemSpan(maxLineSpan) }) {
            FocusCard(
                Modifier.fillMaxWidth().height(96.dp).takesFocus(route, "profile", target == "profile"), glow = Palette.Butter,
                onFocus = { FocusMemory.remember(route, "profile"); Selection.current = Selected.Home },
                onClick = { raVersion++ },
            ) {
                val p = profile
                Row(Modifier.fillMaxSize().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(70.dp).clip(CircleShape).background(Palette.CardHigh)) {
                        if (p != null) AsyncImage(p.picUrl, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    }
                    Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                        Text(p?.user ?: HomeStore.ra.user, style = MaterialTheme.typography.headlineSmall, maxLines = 1)
                        Text(
                            when {
                                p == null && profileDone -> "Couldn't reach RetroAchievements. A tries again"
                                p == null -> "Loading your profile…"
                                p.richPresence.isNotBlank() -> p.richPresence
                                p.motto.isNotBlank() -> p.motto
                                else -> "Member since ${p.memberSince}"
                            },
                            style = MaterialTheme.typography.bodyMedium, color = Palette.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (p != null) {
                        Column(horizontalAlignment = Alignment.End) {
                            Pill("🏆 ${p.points} points", Palette.Butter)
                            Spacer(Modifier.height(4.dp))
                            if (p.softPoints > 0) Pill("${p.softPoints} softcore", Palette.Lavender)
                        }
                    }
                }
            }
        }
        item("recent-title", span = { GridItemSpan(maxLineSpan) }) {
            SectionTitle("Earned in the last 30 days", Palette.Butter, Modifier.padding(top = 4.dp))
        }
        val list = recent
        when {
            list == null && recentDone -> item("failed", span = { GridItemSpan(maxLineSpan) }) {
                PipNote("Couldn't fetch your trophies", "Check the connection, then press A on your profile to try again.", mood = 2)
            }
            list == null -> item("loading", span = { GridItemSpan(maxLineSpan) }) { PipNote("Fetching your trophies…", "One moment.") }
            list.isEmpty() -> item("none", span = { GridItemSpan(maxLineSpan) }) {
                PipNote("Nothing new this month", "Your next achievement will shine right here.", mood = 2)
            }
            else -> items(list, key = { "${it.date}/${it.title}" }) { e ->
                val key = "${e.date}/${e.title}"
                FocusCard(
                    Modifier.fillMaxWidth().height(76.dp).takesFocus(route, key, target == key), glow = Palette.Butter, shape = RoundedCornerShape(16.dp),
                    onFocus = { FocusMemory.remember(route, key); Selection.current = Selected.Home },
                    onClick = { Toasts.show(e.description.ifBlank { e.title }) },
                ) {
                    Row(Modifier.fillMaxSize().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(e.badgeUrl, null, Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)))
                        Column(Modifier.weight(1f).padding(start = 10.dp)) {
                            Text(e.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(e.game, style = MaterialTheme.typography.labelMedium, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "${e.points} pts" + (if (e.hardcore) " · hardcore" else "") + " · " + e.date.substringBefore(' '),
                                style = MaterialTheme.typography.labelMedium, color = Palette.Butter, maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

private val greyscale = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })

/**
 * Achievement progress for the highlighted game, on the bottom screen. Looked up after a short pause on the game,
 * so scrolling past games doesn't send a request for each one.
 */
@Composable
fun AchievementsStrip(game: Game) {
    if (!RetroAchievements.enabled || Privacy.isHidden(game) || RetroAchievements.consoleId(game.platform.id) == null) return
    val progress by produceState(RetroAchievements.cachedProgress(game), game.key) {
        if (value == null) delay(700)
        value = runCatching { RetroAchievements.progress(game) }.getOrNull()
    }
    AnimatedVisibility(progress != null && progress!!.total > 0, enter = fadeIn() + expandVertically()) {
        val p = progress ?: return@AnimatedVisibility
        val fraction = if (p.total == 0) 0f else p.earned / p.total.toFloat()
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🏆 ${p.earned} / ${p.total}", style = MaterialTheme.typography.titleSmall, color = Palette.Butter)
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(50)).background(Palette.NightDeep)) {
                    Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().clip(RoundedCornerShape(50)).background(Palette.Butter))
                }
                Spacer(Modifier.width(10.dp))
                Text(if (p.earned == p.total) "Mastered ✨" else "${(fraction * 100).toInt()}%", style = MaterialTheme.typography.labelLarge, color = Palette.Butter)
            }
            Spacer(Modifier.height(6.dp))
            // Earned badges first, then the next few still to get (greyed).
            val shown = remember(p) { (p.achievements.filter { it.earned }.takeLast(6) + p.achievements.filter { !it.earned }).take(9) }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (a in shown) {
                    AsyncImage(
                        a.badgeUrl, a.title,
                        Modifier.size(34.dp).clip(RoundedCornerShape(8.dp)).alpha(if (a.earned) 1f else 0.45f),
                        colorFilter = if (a.earned) null else greyscale,
                    )
                }
            }
        }
    }
}
