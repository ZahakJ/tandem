package io.github.zahakj.tandem.ui

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
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
import io.github.zahakj.tandem.Button
import io.github.zahakj.tandem.Game
import io.github.zahakj.tandem.DateKind
import io.github.zahakj.tandem.Dates
import io.github.zahakj.tandem.Libraries
import io.github.zahakj.tandem.NetGate
import io.github.zahakj.tandem.Privacy
import io.github.zahakj.tandem.R
import io.github.zahakj.tandem.Selected
import io.github.zahakj.tandem.Selection
import io.github.zahakj.tandem.SteamAchievement
import io.github.zahakj.tandem.SteamAchievements
import io.github.zahakj.tandem.SteamApi
import io.github.zahakj.tandem.SteamGameAchievements
import io.github.zahakj.tandem.Tx
import io.github.zahakj.tandem.data.Net
import io.github.zahakj.tandem.data.SteamStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/*
 * Steam achievements on screen: the Steam group in Settings (key, account, look up, sign out), Pip's unlock toasts
 * after a session, the strip under a Steam game on the bottom screen, the full list of a game's achievements (Y ›
 * Achievements), and the Steam parts of Stats › Achievements. The client is SteamAchievements.kt.
 */

/** Bumped after signing in or out, so the achievements tab loads again. */
var steamVersion by mutableIntStateOf(0)

/** Steam's blue, for the Steam pill and everything Steam here. */
private val SteamColor get() = Palette.Sky

private val greyscale = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
/** "3 Nov 2026", in Tandem's language and calendar. */
private fun dateOf(ms: Long): String = Dates.text(ms, DateKind.Date)

/** Pip's toasts for new unlocks, one after another, with the achievement's icon beside Pip. */
object SteamUnlocks {
    private const val SHOW_MS = 4200L
    private var job: Job? = null

    fun show(toasts: List<SteamApi.Toast>) {
        if (toasts.isEmpty()) return
        job?.cancel()
        job = MainScope().launch {
            Sounds.goodNews()
            toasts.forEachIndexed { i, t ->
                if (i > 0) delay(SHOW_MS + 400)
                Toasts.show(t.text, SHOW_MS, icon = t.icon)
            }
        }
    }
}

// --- Settings ----------------------------------------------------------------------------------------------------

@Composable
fun steamSettings(): SettingSection {
    val acc = SteamStore.account
    val known = SteamAchievements.games.size
    return SettingSection(
        "Steam", // tx-ok: a name
        buildList {
            add(
                SettingRow(
                    Tx.s(R.string.accounts_steam_key), Tx.s(if (acc.key.isNotBlank()) R.string.accounts_steam_key_set else R.string.accounts_steam_key_not_set), SteamColor, Glyph.Key,
                    Tx.s(R.string.accounts_steam_key_help),
                    keywords = "steam web api key achievements token", en = "Steam Web API key",
                ) { steamAskKey() },
            )
            add(
                SettingRow(
                    Tx.s(R.string.accounts_steam_account), if (acc.id.isNotBlank()) acc.name.ifBlank { acc.id } else Tx.s(R.string.accounts_ra_sign_in), SteamColor, Glyph.Trophy,
                    Tx.s(R.string.accounts_steam_account_help),
                    keywords = "steam account steamid vanity profile achievements sign in login", en = "Steam account",
                ) { steamSignIn() },
            )
            if (acc.signedIn) {
                add(
                    SettingRow(
                        Tx.s(R.string.accounts_steam_look_up),
                        if (SteamAchievements.refreshing) Tx.s(R.string.accounts_steam_looking) else Tx.p(R.plurals.accounts_steam_games_count, known), SteamColor, Glyph.Refresh,
                        Tx.s(R.string.accounts_steam_look_up_help),
                        keywords = "steam refresh achievements look up", en = "Look up achievements",
                    ) { steamLookUp() },
                )
                add(
                    SettingRow(
                        Tx.s(R.string.accounts_ra_sign_out), "", Palette.Peach, Glyph.Key, Tx.s(R.string.accounts_steam_sign_out_help),
                        keywords = "steam logout", en = "Sign out",
                    ) {
                        SteamAchievements.signOut()
                        steamVersion++
                        Toasts.show(Tx.s(R.string.accounts_steam_signed_out))
                    },
                )
            }
        },
    )
}

/** Asks for the key; [then] runs once it is saved (the sign-in continues), else a toast says what's next. */
fun steamAskKey(then: (() -> Unit)? = null) {
    TextEntry.ask(Tx.s(R.string.accounts_steam_key_ask), "", secret = true) { k ->
        val key = k.trim()
        if (key.isEmpty()) return@ask
        SteamStore.update { it.copy(key = key) }
        steamVersion++
        if (then != null) then()
        else Toasts.show(Tx.s(if (SteamStore.account.id.isBlank()) R.string.accounts_steam_key_saved_next else R.string.accounts_steam_key_saved))
    }
}

/** Asks for the account (after the key, when there is none yet), checks it with Steam, then looks everything up. */
fun steamSignIn() {
    if (NetGate.closed) { Toasts.show(Tx.s(R.string.accounts_ra_offline)); return }
    val acc = SteamStore.account
    if (acc.key.isBlank()) { steamAskKey(then = ::steamSignIn); return }
    TextEntry.ask(Tx.s(R.string.accounts_steam_account_ask), acc.id) { typed ->
        val input = SteamApi.parseAccount(typed)
        if (input == null) { Toasts.show(Tx.s(R.string.accounts_steam_not_account)); return@ask }
        Toasts.show(Tx.s(R.string.accounts_steam_asking))
        MainScope().launch {
            val key = SteamStore.account.key
            val id = withContext(Dispatchers.IO) {
                runCatching { input.steamId ?: input.vanity?.let { SteamApi.parseVanity(Net.text(SteamApi.vanityUrl(key, it))) } }.getOrNull()
            }
            if (id == null) {
                Sounds.error()
                Toasts.show(input.vanity?.let { Tx.s(R.string.accounts_steam_no_profile, it) } ?: Tx.s(R.string.accounts_steam_unreachable_key))
                return@launch
            }
            val p = withContext(Dispatchers.IO) { runCatching { SteamApi.parseSummaries(Net.text(SteamApi.summariesUrl(key, id))) }.getOrNull() }
            if (p == null) {
                Sounds.error()
                Toasts.show(Tx.s(R.string.accounts_steam_refused))
                return@launch
            }
            SteamStore.update { it.copy(id = p.steamId, name = p.name, avatar = p.avatar) }
            steamVersion++
            Sounds.achievement()
            Toasts.show(Tx.s(R.string.accounts_steam_signed_in, p.name.ifBlank { p.steamId }))
            steamLookUp(quiet = true)
        }
    }
}

/** Look up achievements: the whole library, with a toast when it is done. */
fun steamLookUp(quiet: Boolean = false) {
    if (NetGate.closed) { Toasts.show(Tx.s(R.string.accounts_steam_offline)); return }
    if (SteamAchievements.refreshing) { Toasts.show(Tx.s(R.string.accounts_steam_still_looking)); return }
    if (!quiet) Toasts.show(Tx.s(R.string.accounts_steam_looking_up))
    SteamAchievements.refreshAll { looked, unlocked, failed ->
        val text = when {
            looked == 0 && failed == 0 -> Tx.s(R.string.accounts_steam_no_games)
            looked == 0 -> SteamAchievements.problem ?: Tx.s(R.string.accounts_steam_unreachable_key)
            else -> Tx.s(
                R.string.accounts_steam_looked_up,
                Tx.p(R.plurals.accounts_steam_looked_up_games, looked), Tx.p(R.plurals.accounts_steam_looked_up_achievements, unlocked),
            ).let { if (failed > 0) Tx.p(R.plurals.accounts_steam_looked_up_failed, failed, it, failed) else it }
        }
        Toasts.show(text, 4500)
    }
}

// --- The bottom screen ---------------------------------------------------------------------------------------------

/**
 * Progress in a Steam game under its play time on the bottom screen, like the RetroAchievements strip: a Steam pill,
 * the count, a bar, then the latest unlocks and the next few to get in grey. Looked up again after a short pause on
 * the game when the record is old, so scrolling past games sends nothing.
 */
@Composable
fun SteamStrip(game: Game) {
    val record = SteamAchievements.cached(game)
    LaunchedEffect(game.key) {
        if (record == null || SteamAchievements.stale(record, SteamAchievements.BROWSE_MAX_AGE_MS)) {
            delay(700)
            runCatching { SteamAchievements.progress(game) }
        }
    }
    AnimatedVisibility(record != null && record.total > 0, enter = fadeIn() + expandVertically()) {
        val p = record ?: return@AnimatedVisibility
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Pill("Steam", SteamColor) // tx-ok: a name
                Spacer(Modifier.width(8.dp))
                Text("🏆 ${p.unlocked} / ${p.total}", style = MaterialTheme.typography.titleSmall, color = SteamColor, maxLines = 1)
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(50)).background(Palette.NightDeep)) {
                    Box(Modifier.fillMaxWidth(p.fraction).fillMaxHeight().clip(RoundedCornerShape(50)).background(SteamColor))
                }
                Spacer(Modifier.width(10.dp))
                Text(if (p.unlocked == p.total) Tx.s(R.string.accounts_steam_all_done) else "${(p.fraction * 100).toInt()}%", style = MaterialTheme.typography.labelLarge, color = SteamColor, maxLines = 1)
            }
            Spacer(Modifier.height(6.dp))
            val shown = remember(p) { SteamApi.displayOrder(p.achievements).let { it.filter { a -> a.unlocked }.take(6) + it.filter { a -> !a.unlocked } }.take(9) }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (a in shown) SteamIcon(a, 34.dp)
            }
        }
    }
}

/** An achievement's icon: coloured when unlocked, Steam's grey one faded while locked. */
@Composable
private fun SteamIcon(a: SteamAchievement, size: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    AsyncImage(
        a.shownIcon, a.name,
        modifier.size(size).clip(RoundedCornerShape(size * 0.22f)).background(Palette.CardHigh).alpha(if (a.unlocked) 1f else 0.5f),
        colorFilter = if (a.unlocked) null else greyscale,
    )
}

// --- A game's achievements -----------------------------------------------------------------------------------------

/** The popup with every achievement of one Steam game (Y › Achievements, A on a game in Stats). */
object SteamUi {
    const val ROUTE = "steamach"
    var popupFor by mutableStateOf<Game?>(null)
        private set

    fun openFor(game: Game) {
        FocusMemory.forget(ROUTE)
        popupFor = game
    }

    fun close() {
        popupFor = null
        Nav.focusTick++
    }

    /** True for a game the popup and the Y menu row apply to: a Steam game, with the account set, not hidden. */
    fun applies(game: Game): Boolean = SteamAchievements.enabled && !Privacy.isHidden(game) && SteamAchievements.appIdOf(game) != null
}

/** Placed in HomeHost, so it opens over any screen. */
@Composable
fun SteamAchievementsPopup() {
    val g = SteamUi.popupFor
    val shown = rememberLatest(g)
    PanelGuard(g != null)
    InterceptButtons(g != null && TextEntry.request == null) { b ->
        if (b == Button.X) shown?.let { game ->
            Toasts.show(Tx.s(R.string.accounts_steam_asking_again))
            SteamAchievements.lookUp(game) { r ->
                Toasts.show(if (r == null) (SteamAchievements.problem ?: Tx.s(R.string.accounts_steam_unreachable)) else Tx.s(R.string.accounts_steam_up_to_date, r.unlocked, r.total))
            }
        }
        true
    }
    OverlayPanel(g != null, onClose = { SteamUi.close() }) {
        val game = shown ?: return@OverlayPanel
        val record = SteamAchievements.cached(game)
        LaunchedEffect(game.key) { if (record == null) runCatching { SteamAchievements.progress(game) } }
        val list = remember(record) { SteamApi.displayOrder(record?.achievements.orEmpty()) }
        val target = rememberFocusTarget(SteamUi.ROUTE, list.map { it.id })
        val state = rememberLazyListState()
        KeepInView(state, list.indexOfFirst { it.id == target })
        PanelSurface(Modifier.width(620.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(record?.gameName?.ifBlank { null } ?: game.title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        when {
                            record == null -> Tx.s(R.string.accounts_steam_asking)
                            record.none -> Tx.s(R.string.accounts_steam_none_listed)
                            else -> Tx.s(R.string.accounts_steam_unlocked_looked, record.unlocked, record.total, dateOf(record.fetchedAt))
                        },
                        style = MaterialTheme.typography.bodyMedium, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Pill("Steam", SteamColor) // tx-ok: a name
            }
            Spacer(Modifier.height(10.dp))
            if (list.isEmpty()) {
                if (record == null) PipNote(Tx.s(R.string.accounts_steam_fetching), Tx.s(R.string.accounts_achievements_loading_text))
                else PipNote(Tx.s(R.string.accounts_steam_nothing), Tx.s(R.string.accounts_steam_nothing_text), mood = 2)
            } else {
                LazyColumn(
                    Modifier.weight(1f, fill = false).fadingEdges(state),
                    state = state,
                    contentPadding = PaddingValues(horizontal = FocusRoomSide, vertical = FocusRoom),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(list, key = { it.id }) { a -> SteamAchievementRow(a, Modifier.takesFocus(SteamUi.ROUTE, a.id, a.id == target)) }
                }
            }
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                ButtonHint("A", Tx.s(R.string.accounts_steam_details), Palette.Mint)
                ButtonHint("X", Tx.s(R.string.accounts_steam_look_again), SteamColor)
                ButtonHint("B", Tx.s(R.string.common_close), Palette.Lavender)
            }
        }
    }
}

/** One achievement: its icon (grey while locked), name, description and how rare it is; A says the description. */
@Composable
private fun SteamAchievementRow(a: SteamAchievement, modifier: Modifier) {
    val description = when {
        a.description.isNotBlank() -> a.description
        a.hidden && !a.unlocked -> Tx.s(R.string.accounts_steam_hidden)
        else -> ""
    }
    val rarity = SteamApi.percentLabel(a.percent)
    FocusCard(
        modifier.fillMaxWidth().height(66.dp), glow = SteamColor, shape = RoundedCornerShape(16.dp),
        onFocus = { FocusMemory.remember(SteamUi.ROUTE, a.id) },
        onClick = { Toasts.show(listOfNotNull(a.name, description.ifBlank { null }, rarity?.let { Tx.s(R.string.accounts_steam_players_have_it, it) }).joinToString(". "), 4500) },
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            SteamIcon(a, 48.dp)
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(a.name, style = MaterialTheme.typography.titleSmall, color = if (a.unlocked) Palette.Text else Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (description.isNotBlank()) Text(description, style = MaterialTheme.typography.labelMedium, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    when {
                        !a.unlocked -> Tx.s(R.string.accounts_steam_locked)
                        a.unlockedAt > 0 -> Tx.s(R.string.accounts_steam_unlocked_on, dateOf(a.unlockedAt))
                        else -> Tx.s(R.string.accounts_steam_unlocked)
                    },
                    style = MaterialTheme.typography.labelSmall, color = if (a.unlocked) SteamColor else Palette.Muted, maxLines = 1,
                )
            }
            if (rarity != null) Pill(rarity, if (SteamApi.rare(a.percent)) Palette.Butter else Palette.Lavender)
        }
    }
}

// --- Stats › Achievements --------------------------------------------------------------------------------------------

/** The Steam games with achievements, as Stats lists them: most unlocked first. A record whose file is gone has no game. */
class SteamGameEntry(val game: Game?, val record: SteamGameAchievements) {
    val title: String get() = record.gameName.ifBlank { game?.title ?: Tx.s(R.string.accounts_steam_app, record.appId) }
}

/** Every known Steam game that may be shown, with its library game when there is one. */
fun steamGameEntries(): List<SteamGameEntry> {
    if (!SteamAchievements.enabled) return emptyList()
    val byId = HashMap<Int, Game>()
    for (g in Libraries.games) {
        if (Privacy.isHidden(g)) continue
        val id = SteamAchievements.appIdOf(g) ?: continue
        byId.putIfAbsent(id, g)
    }
    return SteamAchievements.games.values
        .filter { it.total > 0 }
        .map { SteamGameEntry(byId[it.appId], it) }
        // A hidden game's record has no visible game: it stays out, so it gives nothing away.
        .filter { it.game != null }
        .sortedWith(compareByDescending<SteamGameEntry> { it.record.unlocked }.thenBy { it.title.lowercase() })
}

/** Steam unlocks from the last [days] days, newest first, with the game each came from. */
fun steamRecentUnlocks(entries: List<SteamGameEntry>, days: Int = 30, now: Long = System.currentTimeMillis()): List<RecentUnlock> {
    val since = now - days * 24L * 60 * 60 * 1000
    return entries.flatMap { e ->
        e.record.achievements.filter { it.unlocked && it.unlockedAt >= since }.map { a ->
            RecentUnlock(
                key = "steam:${e.record.appId}/${a.id}",
                title = a.name,
                game = e.title,
                line = listOfNotNull(SteamApi.percentLabel(a.percent)?.let { Tx.s(R.string.accounts_steam_of_players, it) }, dateOf(a.unlockedAt)).joinParts(),
                badge = a.icon,
                at = a.unlockedAt,
                steam = true,
                detail = a.description.ifBlank { a.name },
                open = e.game?.let { g -> { SteamUi.openFor(g) } },
            )
        }
    }.sortedByDescending { it.at }
}

/** The Steam account on the achievements tab: picture, name, totals. A looks every game up again. */
@Composable
fun SteamProfileCard(entries: List<SteamGameEntry>, modifier: Modifier, onFocus: () -> Unit) {
    val acc = SteamStore.account
    val unlocked = entries.sumOf { it.record.unlocked }
    val total = entries.sumOf { it.record.total }
    val perfect = entries.count { it.record.total > 0 && it.record.unlocked == it.record.total }
    FocusCard(modifier.fillMaxWidth().height(96.dp), glow = SteamColor, onFocus = onFocus, onClick = { steamLookUp() }) {
        Row(Modifier.fillMaxSize().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(70.dp).clip(CircleShape).background(Palette.CardHigh)) {
                if (acc.avatar.isNotBlank()) AsyncImage(acc.avatar, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(acc.name.ifBlank { acc.id }, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.width(10.dp))
                    Pill("Steam", SteamColor) // tx-ok: a name
                }
                Text(
                    when {
                        SteamAchievements.refreshing -> Tx.s(R.string.accounts_steam_looking_games)
                        SteamAchievements.problem != null -> SteamAchievements.problem!!
                        entries.isEmpty() -> Tx.s(R.string.accounts_steam_no_achievements)
                        perfect > 0 -> Tx.s(
                            R.string.accounts_steam_across_perfect, unlocked, total,
                            Tx.p(R.plurals.accounts_steam_games_count, entries.size), Tx.p(R.plurals.accounts_steam_games_count, perfect),
                        )
                        else -> Tx.s(R.string.accounts_steam_across, unlocked, total, Tx.p(R.plurals.accounts_steam_games_count, entries.size))
                    },
                    style = MaterialTheme.typography.bodyMedium, color = Palette.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Pill(Tx.p(R.plurals.accounts_steam_unlocked_pill, unlocked), SteamColor)
                if (perfect > 0) {
                    Spacer(Modifier.height(4.dp))
                    Pill(Tx.p(R.plurals.accounts_steam_perfect_pill, perfect), Palette.Butter)
                }
            }
        }
    }
}

/** One Steam game in Stats: title, the count and a bar. A opens its achievements. */
@Composable
fun SteamGameCard(e: SteamGameEntry, modifier: Modifier, onFocus: () -> Unit) {
    val p = e.record
    FocusCard(
        modifier.fillMaxWidth().height(76.dp), glow = SteamColor, shape = RoundedCornerShape(16.dp), onFocus = onFocus,
        onClick = { e.game?.let { SteamUi.openFor(it) } ?: Toasts.show(Tx.s(R.string.accounts_steam_not_in_library)) },
    ) {
        Row(Modifier.fillMaxSize().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            val latest = remember(p) { p.achievements.filter { it.unlocked }.maxByOrNull { it.unlockedAt } ?: p.achievements.firstOrNull() }
            if (latest != null) SteamIcon(latest, 52.dp)
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text(e.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (p.unlocked == p.total) Tx.s(R.string.accounts_steam_card_done, p.unlocked, p.total)
                    else Tx.s(R.string.accounts_steam_card_progress, p.unlocked, p.total, (p.fraction * 100).toInt()),
                    style = MaterialTheme.typography.labelMedium, color = SteamColor, maxLines = 1,
                )
                Spacer(Modifier.height(5.dp))
                Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)).background(Palette.NightDeep)) {
                    Box(Modifier.fillMaxWidth(p.fraction).fillMaxHeight().clip(RoundedCornerShape(50)).background(SteamColor))
                }
            }
        }
    }
}

/** The note on an empty achievements tab, for the Steam half: sign in, or (signed in) look games up. */
@Composable
fun SteamSignInButton(modifier: Modifier) {
    FocusCard(
        modifier.width(220.dp).height(44.dp), glow = SteamColor,
        onFocus = { Selection.current = Selected.Home },
        onClick = { steamSignIn() },
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(Tx.s(R.string.accounts_steam_connect), style = MaterialTheme.typography.titleSmall, color = SteamColor)
        }
    }
}
