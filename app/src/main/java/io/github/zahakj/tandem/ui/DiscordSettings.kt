package io.github.zahakj.tandem.ui

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import io.github.zahakj.tandem.DiscordPresence
import io.github.zahakj.tandem.MainActivity
import io.github.zahakj.tandem.R
import io.github.zahakj.tandem.Tx
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Settings › Library & art › Accounts › Discord (DiscordPresence.kt). No rows in builds without Discord's SDK. */
@Composable
fun discordSettings(): SettingSection {
    if (!DiscordPresence.available) return SettingSection("Discord", emptyList()) // tx-ok: a name
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { withContext(Dispatchers.IO) { DiscordPresence.refresh(ctx) } }
    val linked = DiscordPresence.linked
    val keywords = "discord rich presence status activity playing now friends"
    return SettingSection(
        "Discord", // tx-ok: a name
        listOf(
            SettingRow(
                Tx.s(R.string.accounts_discord_status), Tx.s(if (linked) R.string.accounts_discord_connected else R.string.accounts_discord_connect),
                Palette.Lavender, Glyph.Bell, Tx.s(R.string.accounts_discord_status_help),
                keywords = keywords, en = "Discord status",
            ) {
                if (linked) Toasts.show(Tx.s(R.string.accounts_discord_connected_toast))
                else Confirm.ask(
                    Tx.s(R.string.accounts_discord_ask_title), Tx.s(R.string.accounts_discord_ask_text), Tx.s(R.string.accounts_discord_connect),
                ) {
                    val activity = ctx as? Activity ?: MainActivity.live?.get()
                    if (activity != null) DiscordPresence.link(activity)
                }
            },
        ) + if (!linked) emptyList() else listOf(
            SettingRow(
                Tx.s(R.string.accounts_discord_disconnect), "", Palette.Peach, Glyph.Key, Tx.s(R.string.accounts_discord_disconnect_help),
                keywords = "$keywords logout sign out unlink", en = "Disconnect Discord",
            ) {
                DiscordPresence.unlink(ctx)
                Toasts.show(Tx.s(R.string.accounts_discord_disconnected))
            },
        ),
    )
}
