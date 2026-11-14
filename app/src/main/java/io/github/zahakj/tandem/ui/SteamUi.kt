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
import io.github.zahakj.tandem.Libraries
import io.github.zahakj.tandem.NetGate
import io.github.zahakj.tandem.Privacy
import io.github.zahakj.tandem.Selected
import io.github.zahakj.tandem.Selection
import io.github.zahakj.tandem.SteamAchievement
import io.github.zahakj.tandem.SteamAchievements
import io.github.zahakj.tandem.SteamApi
import io.github.zahakj.tandem.SteamGameAchievements
import io.github.zahakj.tandem.data.Net
import io.github.zahakj.tandem.data.SteamStore
import io.github.zahakj.tandem.plural
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

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
private val dateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy")

private fun dateOf(ms: Long): String = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(dateFormat)

/** Pip's toasts for new unlocks, one after another, with the achievement's icon beside Pip. */
object SteamUnlocks {
    private const val SHOW_MS = 4200L
    private var job: Job? = null

    fun show(toasts: List<SteamApi.Toast>) {
        if (toasts.isEmpty()) return
        job?.cancel()
        job = MainScope().launch {
            Sounds.achievement()
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
        "Steam",
        buildList {
            add(
                SettingRow(
                    "Steam Web API key", if (acc.key.isNotBlank()) "Set" else "Not set", SteamColor, Glyph.Key,
                    "Your own key from steamcommunity.com/dev/apikey (any domain name will do). Tandem sends it only to Steam, with your account id and the app ids of your Steam games, to read your achievements. Kept on this device, out of Android's backups.",
                    keywords = "steam web api key achievements token",
                ) { steamAskKey() },
            )
            add(
                SettingRow(
                    "Steam account", if (acc.id.isNotBlank()) acc.name.ifBlank { acc.id } else "Sign in", SteamColor, Glyph.Trophy,
                    "Your SteamID64, your profile address or your profile name (looked up through Steam). Your game details must be public in Steam's privacy settings. Never for hidden games, and nothing in private mode.",
                    keywords = "steam account steamid vanity profile achievements sign in login",
                ) { steamSignIn() },
            )
            if (acc.signedIn) {
                add(
                    SettingRow(
                        "Look up achievements", if (SteamAchievements.refreshing) "Looking…" else plural(known, "game"), SteamColor, Glyph.Refresh,
                        "Ask Steam about every Steam game in your library now. Otherwise a game is looked up when you highlight it or come back from playing it.",
                        keywords = "steam refresh achievements look up",
                    ) { steamLookUp() },
                )
                add(
                    SettingRow("Sign out", "", Palette.Peach, Glyph.Key, "Forget your Steam key and account on this device, and the achievements fetched for them.", keywords = "steam logout") {
                        SteamAchievements.signOut()
                        steamVersion++
                        Toasts.show("Signed out of Steam")
                    },
                )
            }
        },
    )
}

/** Asks for the key; [then] runs once it is saved (the sign-in continues), else a toast says what's next. */
fun steamAskKey(then: (() -> Unit)? = null) {
    TextEntry.ask("Steam Web API key (steamcommunity.com/dev/apikey)", "", secret = true) { k ->
        val key = k.trim()
        if (key.isEmpty()) return@ask
        SteamStore.update { it.copy(key = key) }
        steamVersion++
        if (then != null) then()
        else Toasts.show(if (SteamStore.account.id.isBlank()) "Key saved. Now add your Steam account" else "Key saved")
    }
}

/** Asks for the account (after the key, when there is none yet), checks it with Steam, then looks everything up. */
fun steamSignIn() {
    if (NetGate.closed) { Toasts.show("Private mode keeps Tandem offline. Turn it off to sign in"); return }
    val acc = SteamStore.account
    if (acc.key.isBlank()) { steamAskKey(then = ::steamSignIn); return }
    TextEntry.ask("Your SteamID64, profile address or profile name", acc.id) { typed ->
        val input = SteamApi.parseAccount(typed)
        if (input == null) { Toasts.show("That doesn't look like a Steam account. Try your SteamID64 or profile address"); return@ask }
        Toasts.show("Asking Steam…")
        MainScope().launch {
            val key = SteamStore.account.key
            val id = withContext(Dispatchers.IO) {
                runCatching { input.steamId ?: input.vanity?.let { SteamApi.parseVanity(Net.text(SteamApi.vanityUrl(key, it))) } }.getOrNull()
            }
            if (id == null) {
                Sounds.error()
                Toasts.show(if (input.vanity != null) "Steam knows no profile called “${input.vanity}”. Try your SteamID64" else "Couldn't reach Steam. Check the key and your connection")
                return@launch
            }
            val p = withContext(Dispatchers.IO) { runCatching { SteamApi.parseSummaries(Net.text(SteamApi.summariesUrl(key, id))) }.getOrNull() }
            if (p == null) {
                Sounds.error()
                Toasts.show("Steam didn't accept the key or the account. Check both and try again")
                return@launch
            }
            SteamStore.update { it.copy(id = p.steamId, name = p.name, avatar = p.avatar) }
            steamVersion++
            Sounds.achievement()
            Toasts.show("Signed in to Steam as ${p.name.ifBlank { p.steamId }} 🏆")
            steamLookUp(quiet = true)
        }
    }
}

/** Look up achievements: the whole library, with a toast when it is done. */
fun steamLookUp(quiet: Boolean = false) {
    if (NetGate.closed) { Toasts.show("Private mode keeps Tandem offline"); return }
    if (SteamAchievements.refreshing) { Toasts.show("Still looking…"); return }
    if (!quiet) Toasts.show("Looking up your Steam achievements…")
    SteamAchievements.refreshAll { looked, unlocked, failed ->
        val text = when {
            looked == 0 && failed == 0 -> "No Steam games in your library yet. Steam games are .steam shortcuts in ROMs/windows"
            looked == 0 -> SteamAchievements.problem ?: "Couldn't reach Steam. Check the key and your connection"
            else -> "Looked up ${plural(looked, "Steam game")}: ${plural(unlocked, "achievement")} unlocked" +
                (if (failed > 0) " · ${plural(failed, "game")} couldn't be looked up" else "")
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
                Pill("Steam", SteamColor)
                Spacer(Modifier.width(8.dp))
                Text("🏆 ${p.unlocked} / ${p.total}", style = MaterialTheme.typography.titleSmall, color = SteamColor, maxLines = 1)
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(50)).background(Palette.NightDeep)) {
                    Box(Modifier.fillMaxWidth(p.fraction).fillMaxHeight().clip(RoundedCornerShape(50)).background(SteamColor))
                }
                Spacer(Modifier.width(10.dp))
                Text(if (p.unlocked == p.total) "All done ✨" else "${(p.fraction * 100).toInt()}%", style = MaterialTheme.typography.labelLarge, color = SteamColor, maxLines = 1)
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
            Toasts.show("Asking Steam again…")
            SteamAchievements.lookUp(game) { r -> Toasts.show(if (r == null) (SteamAchievements.problem ?: "Couldn't reach Steam") else "Up to date: ${r.unlocked} of ${r.total} unlocked") }
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
                            record == null -> "Asking Steam…"
                            record.none -> "Steam lists no achievements for this game"
                            else -> "${record.unlocked} of ${record.total} unlocked · last looked ${dateOf(record.fetchedAt)}"
                        },
                        style = MaterialTheme.typography.bodyMedium, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Pill("Steam", SteamColor)
            }
            Spacer(Modifier.height(10.dp))
            if (list.isEmpty()) {
                if (record == null) PipNote("Fetching achievements…", "One moment.")
                else PipNote("Nothing to unlock here", "Steam lists no achievements for this game.", mood = 2)
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
                ButtonHint("A", "Details", Palette.Mint)
                ButtonHint("X", "Look up again", SteamColor)
                ButtonHint("B", "Close", Palette.Lavender)
            }
        }
    }
}

/** One achievement: its icon (grey while locked), name, description and how rare it is; A says the description. */
@Composable
private fun SteamAchievementRow(a: SteamAchievement, modifier: Modifier) {
    val description = when {
        a.description.isNotBlank() -> a.description
        a.hidden && !a.unlocked -> "Hidden until you unlock it"
        else -> ""
    }
    val rarity = SteamApi.percentLabel(a.percent)
    FocusCard(
        modifier.fillMaxWidth().height(66.dp), glow = SteamColor, shape = RoundedCornerShape(16.dp),
        onFocus = { FocusMemory.remember(SteamUi.ROUTE, a.id) },
        onClick = { Toasts.show(listOfNotNull(a.name, description.ifBlank { null }, rarity?.let { "$it of players have it" }).joinToString(". "), 4500) },
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            SteamIcon(a, 48.dp)
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(a.name, style = MaterialTheme.typography.titleSmall, color = if (a.unlocked) Palette.Text else Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (description.isNotBlank()) Text(description, style = MaterialTheme.typography.labelMedium, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (a.unlocked) "Unlocked" + (if (a.unlockedAt > 0) " · ${dateOf(a.unlockedAt)}" else "") else "Locked",
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
    val title: String get() = record.gameName.ifBlank { game?.title ?: "Steam app ${record.appId}" }
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
                line = listOfNotNull(SteamApi.percentLabel(a.percent)?.let { "$it of players" }, dateOf(a.unlockedAt)).joinToString(" · "),
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
                    Pill("Steam", SteamColor)
                }
                Text(
                    when {
                        SteamAchievements.refreshing -> "Looking up your games…"
                        SteamAchievements.problem != null -> SteamAchievements.problem!!
                        entries.isEmpty() -> "No Steam games with achievements yet. A looks them up"
                        else -> "$unlocked of $total achievements across ${plural(entries.size, "game")}" + (if (perfect > 0) " · ${plural(perfect, "game")} at 100%" else "") + ". A looks again"
                    },
                    style = MaterialTheme.typography.bodyMedium, color = Palette.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Pill("🏆 $unlocked unlocked", SteamColor)
                if (perfect > 0) {
                    Spacer(Modifier.height(4.dp))
                    Pill("$perfect at 100%", Palette.Butter)
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
        onClick = { e.game?.let { SteamUi.openFor(it) } ?: Toasts.show("That game isn't in your library any more") },
    ) {
        Row(Modifier.fillMaxSize().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            val latest = remember(p) { p.achievements.filter { it.unlocked }.maxByOrNull { it.unlockedAt } ?: p.achievements.firstOrNull() }
            if (latest != null) SteamIcon(latest, 52.dp)
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text(e.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${p.unlocked} of ${p.total}" + (if (p.unlocked == p.total) " · All done ✨" else " · ${(p.fraction * 100).toInt()}%"),
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
            Text("Connect Steam", style = MaterialTheme.typography.titleSmall, color = SteamColor)
        }
    }
}
