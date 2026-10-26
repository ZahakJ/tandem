package io.github.zahakj.tandem.ui

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import io.github.zahakj.tandem.DiscordPresence
import io.github.zahakj.tandem.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Settings › Art & accounts › Discord (DiscordPresence.kt). No rows in builds without Discord's SDK. */
@Composable
fun discordSettings(): SettingSection {
    if (!DiscordPresence.available) return SettingSection("Discord", emptyList())
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { withContext(Dispatchers.IO) { DiscordPresence.refresh(ctx) } }
    val linked = DiscordPresence.linked
    val keywords = "discord rich presence status activity playing now friends"
    return SettingSection(
        "Discord",
        listOf(
            SettingRow(
                "Discord status", if (linked) "Connected" else "Connect", Palette.Lavender, Glyph.Bell,
                "While you play, your Discord status shows the game, its system and how long you've played. " +
                    "Never for hidden games, and nothing while private mode is on.",
                keywords = keywords,
            ) {
                if (linked) Toasts.show("Connected. Disconnect is under More options")
                else Confirm.ask(
                    "Show what you play on Discord?",
                    "Discord opens and asks you to authorize Tandem. Then your status shows the game you're playing " +
                        "while it plays. Disconnect here any time.",
                    "Connect",
                ) {
                    val activity = ctx as? Activity ?: MainActivity.live?.get()
                    if (activity != null) DiscordPresence.link(activity)
                }
            },
        ),
        more = if (!linked) emptyList() else listOf(
            SettingRow(
                "Disconnect Discord", "", Palette.Peach, Glyph.Key,
                "Stop showing what you play, and forget the connection to your Discord account on this device.",
                keywords = "$keywords logout sign out unlink",
            ) {
                DiscordPresence.unlink(ctx)
                Toasts.show("Disconnected from Discord")
            },
        ),
    )
}
