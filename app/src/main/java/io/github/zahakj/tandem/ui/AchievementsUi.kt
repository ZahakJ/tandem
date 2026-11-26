package io.github.zahakj.tandem.ui

import io.github.zahakj.tandem.Lang
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
import io.github.zahakj.tandem.Libraries
import io.github.zahakj.tandem.R
import io.github.zahakj.tandem.RetroAchievements
import io.github.zahakj.tandem.Selected
import io.github.zahakj.tandem.SteamAchievements
import io.github.zahakj.tandem.Selection
import io.github.zahakj.tandem.Tx
import io.github.zahakj.tandem.data.HomeStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Bumped to reload the achievements tab after signing in or out. */
private var raVersion by mutableStateOf(0)

/** Asks for the username, then the Web API key, checks them, and turns achievements on. */
fun raSignIn() {
    if (io.github.zahakj.tandem.NetGate.closed) { Toasts.show(Tx.s(R.string.accounts_ra_offline)); return }
    TextEntry.ask(Tx.s(R.string.accounts_ra_username), HomeStore.ra.user) { name ->
        val user = name.trim()
        if (user.isEmpty()) return@ask
        TextEntry.ask(Tx.s(R.string.accounts_ra_key), "", secret = true) { k ->
            val key = k.trim()
            if (key.isEmpty()) return@ask
            Toasts.show(Tx.s(R.string.accounts_ra_checking))
            kotlinx.coroutines.MainScope().launch {
                val p = RetroAchievements.verify(user, key)
                if (p == null) {
                    Toasts.show(Tx.s(R.string.accounts_ra_sign_in_failed))
                } else {
                    HomeStore.updateRa { it.copy(user = p.user, key = key, enabled = true, matches = emptyMap()) }
                    RetroAchievements.invalidate()
                    raVersion++
                    Toasts.show(Tx.s(R.string.accounts_ra_signed_in, p.user))
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
        "RetroAchievements", // tx-ok: a name
        buildList {
            add(
                SettingRow(
                    Tx.s(R.string.accounts_ra_account), if (signedIn) ra.user else Tx.s(R.string.accounts_ra_sign_in), Palette.Butter, Glyph.Trophy,
                    Tx.s(R.string.accounts_ra_account_help),
                    keywords = "retroachievements ra trophies login sign in cheevos", en = "Account",
                ) { raSignIn() },
            )
            if (signedIn) {
                add(
                    SettingRow(
                        Tx.s(R.string.accounts_ra_show), onOff(ra.enabled), Palette.Butter, Glyph.Trophy, toggle = ra.enabled,
                        help = Tx.s(R.string.accounts_ra_show_help),
                        keywords = "retroachievements trophies", en = "Show achievements",
                    ) {
                        HomeStore.updateRa { it.copy(enabled = !it.enabled) }
                        raVersion++
                    },
                )
                add(
                    SettingRow(
                        Tx.s(R.string.accounts_ra_rematch), Tx.p(R.plurals.accounts_ra_matched, ra.matches.count { it.value > 0 }), Palette.Butter, Glyph.Refresh,
                        Tx.s(R.string.accounts_ra_rematch_help),
                        keywords = "retroachievements rematch hash", en = "Look up games again",
                    ) {
                        RetroAchievements.forgetMatches()
                        Toasts.show(Tx.s(R.string.accounts_ra_rematch_toast))
                    },
                )
                add(
                    SettingRow(
                        Tx.s(R.string.accounts_ra_sign_out), "", Palette.Peach, Glyph.Key, Tx.s(R.string.accounts_ra_sign_out_help),
                        keywords = "retroachievements logout", en = "Sign out",
                    ) {
                        HomeStore.updateRa { RaAccountSignedOut }
                        RetroAchievements.forgetProgress()
                        raVersion++
                        Toasts.show(Tx.s(R.string.accounts_ra_signed_out))
                    },
                )
            }
        },
    )
}

private val RaAccountSignedOut = io.github.zahakj.tandem.data.RaAccount()

/**
 * One unlock on the achievements tab's recent list, from RetroAchievements or Steam, so both sort into one list.
 * [at] is when it was earned (ms); 0 when the service didn't say, which puts it last.
 */
class RecentUnlock(
    val key: String,
    val title: String,
    val game: String,
    val line: String,
    val badge: String,
    val at: Long,
    val steam: Boolean,
    val detail: String,
    /** What A does besides saying [detail]: a Steam unlock opens its game's list. */
    val open: (() -> Unit)? = null,
)

/** RetroAchievements' "2024-05-01 12:00:00" (UTC) as ms; 0 when it doesn't read. */
internal fun raDateMs(date: String): Long = runCatching {
    java.time.LocalDateTime.parse(date.trim().replace(' ', 'T')).toInstant(java.time.ZoneOffset.UTC).toEpochMilli()
}.getOrDefault(0L)

internal fun RetroAchievements.Earned.recent(): RecentUnlock = RecentUnlock(
    key = "ra:$date/$title",
    title = title,
    game = game,
    line = Tx.p(if (hardcore) R.plurals.accounts_ra_unlock_line_hardcore else R.plurals.accounts_ra_unlock_line, points, points, date.substringBefore(' ')),
    badge = badgeUrl,
    at = raDateMs(date),
    steam = false,
    detail = description.ifBlank { title },
)

/**
 * The achievements tab on the stats screen: the RetroAchievements profile and the Steam account, what was earned in
 * the last 30 days on either, and every Steam game with achievements.
 */
@Composable
fun AchievementsTab() {
    val route = "stats:2"
    val ra = RetroAchievements.enabled
    val steam = SteamAchievements.enabled
    if (!ra && !steam) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                PipNote(
                    Tx.s(R.string.accounts_achievements_bring),
                    Tx.s(R.string.accounts_achievements_bring_text),
                    mood = 1,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FocusCard(
                        Modifier.width(220.dp).height(44.dp).remembersFocus(route, "signin", true), glow = Palette.Butter,
                        onFocus = { Selection.current = Selected.Home },
                        onClick = { if (HomeStore.ra.key.isNotBlank()) { HomeStore.updateRa { it.copy(enabled = true) }; raVersion++ } else raSignIn() },
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(if (HomeStore.ra.key.isNotBlank()) Tx.s(R.string.accounts_ra_turn_on) else "RetroAchievements", style = MaterialTheme.typography.titleSmall, color = Palette.Butter) // tx-ok: a name
                        }
                    }
                    SteamSignInButton(Modifier.remembersFocus(route, "steam-signin", false))
                }
            }
        }
        return
    }
    val version = raVersion + steamVersion
    // Whether each request has come back, so a failed one says so instead of "loading" forever.
    var profileDone by remember(version) { mutableStateOf(false) }
    var recentDone by remember(version) { mutableStateOf(false) }
    val profile by produceState<RetroAchievements.Profile?>(null, version) { value = if (ra) RetroAchievements.profile() else null; profileDone = true }
    val raRecent by produceState<List<RetroAchievements.Earned>?>(null, version) { value = if (ra) RetroAchievements.recent() else emptyList(); recentDone = true }
    // Steam's side is what is cached: it changes as looks come back.
    val steamRecords = SteamAchievements.games.values.toList()
    val steamGames = remember(steamRecords, Libraries.all, Privacy.view, steam) { steamGameEntries() }
    // Keyed on the language too: each unlock's line is text.
    val recent: List<RecentUnlock>? = remember(raRecent, steamGames, Lang.revision.intValue) {
        raRecent?.let { list -> (list.map { it.recent() } + steamRecentUnlocks(steamGames)).sortedByDescending { it.at } }
    }
    val keys = buildList {
        if (ra) add("profile")
        if (steam) add("steam")
        recent.orEmpty().forEach { add(it.key) }
        steamGames.forEach { add("sg:${it.record.appId}") }
    }
    val target = rememberFocusTarget(route, keys)
    val grid = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    LazyVerticalGrid(
        GridCells.Adaptive(240.dp),
        Modifier.fillMaxSize().fadingEdges(grid),
        state = grid,
        contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = FocusRoom, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (ra) item("profile", span = { GridItemSpan(maxLineSpan) }) {
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
                                p == null && profileDone -> Tx.s(R.string.accounts_ra_unreachable)
                                p == null -> Tx.s(R.string.accounts_ra_loading)
                                p.richPresence.isNotBlank() -> p.richPresence
                                p.motto.isNotBlank() -> p.motto
                                else -> Tx.s(R.string.accounts_ra_member_since, p.memberSince)
                            },
                            style = MaterialTheme.typography.bodyMedium, color = Palette.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (p != null) {
                        Column(horizontalAlignment = Alignment.End) {
                            Pill(Tx.p(R.plurals.accounts_ra_points, p.points), Palette.Butter)
                            Spacer(Modifier.height(4.dp))
                            if (p.softPoints > 0) Pill(Tx.p(R.plurals.accounts_ra_softcore, p.softPoints), Palette.Lavender)
                        }
                    }
                }
            }
        }
        if (steam) item("steam", span = { GridItemSpan(maxLineSpan) }) {
            SteamProfileCard(steamGames, Modifier.takesFocus(route, "steam", target == "steam")) { FocusMemory.remember(route, "steam"); Selection.current = Selected.Home }
        }
        item("recent-title", span = { GridItemSpan(maxLineSpan) }) {
            SectionTitle(Tx.s(R.string.accounts_achievements_recent), Palette.Butter, Modifier.padding(top = 4.dp))
        }
        val list = recent
        when {
            list == null && recentDone -> item("failed", span = { GridItemSpan(maxLineSpan) }) {
                PipNote(Tx.s(R.string.accounts_achievements_failed), Tx.s(R.string.accounts_achievements_failed_text), mood = 2)
            }
            list == null -> item("loading", span = { GridItemSpan(maxLineSpan) }) { PipNote(Tx.s(R.string.accounts_achievements_loading), Tx.s(R.string.accounts_achievements_loading_text)) }
            list.isEmpty() -> item("none", span = { GridItemSpan(maxLineSpan) }) {
                PipNote(Tx.s(R.string.accounts_achievements_none), Tx.s(R.string.accounts_achievements_none_text), mood = 2)
            }
            else -> items(list, key = { it.key }) { e ->
                val key = e.key
                val color = if (e.steam) Palette.Sky else Palette.Butter
                FocusCard(
                    Modifier.fillMaxWidth().height(76.dp).takesFocus(route, key, target == key), glow = color, shape = RoundedCornerShape(16.dp),
                    onFocus = { FocusMemory.remember(route, key); Selection.current = Selected.Home },
                    onClick = { e.open?.invoke() ?: Toasts.show(e.detail) },
                ) {
                    Row(Modifier.fillMaxSize().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(e.badge, null, Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)).background(Palette.CardHigh))
                        Column(Modifier.weight(1f).padding(start = 10.dp)) {
                            Text(e.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(e.game, style = MaterialTheme.typography.labelMedium, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(e.line, style = MaterialTheme.typography.labelMedium, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (e.steam) Pill("Steam", Palette.Sky, Modifier.padding(start = 6.dp)) // tx-ok: a name
                    }
                }
            }
        }
        if (steamGames.isNotEmpty()) {
            item("steam-title", span = { GridItemSpan(maxLineSpan) }) {
                SectionTitle(Tx.s(R.string.accounts_steam_games), Palette.Sky, Modifier.padding(top = 4.dp))
            }
            items(steamGames, key = { "sg:${it.record.appId}" }) { e ->
                val key = "sg:${e.record.appId}"
                SteamGameCard(e, Modifier.takesFocus(route, key, target == key)) {
                    FocusMemory.remember(route, key)
                    Selection.current = e.game?.let { Selected.GameItem(it) } ?: Selected.Home
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
    // A Steam game (a .steam shortcut) has Steam's achievements, never RetroAchievements'.
    if (SteamUi.applies(game)) { SteamStrip(game); return }
    if (!RetroAchievements.enabled || Privacy.isHidden(game) || RetroAchievements.consoleId(game.platform.id) == null) return
    val progress by produceState(RetroAchievements.cachedProgress(game), game.key) {
        // The value carries over from the game shown before: start from this one's.
        value = RetroAchievements.cachedProgress(game)
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
                Text(if (p.earned == p.total) Tx.s(R.string.accounts_achievements_mastered) else "${(fraction * 100).toInt()}%", style = MaterialTheme.typography.labelLarge, color = Palette.Butter)
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
