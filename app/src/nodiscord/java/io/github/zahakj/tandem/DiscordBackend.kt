package io.github.zahakj.tandem

import android.app.Activity

/** Builds without Discord's Social SDK (the default; see app/build.gradle.kts): no Discord status, no setting. */
fun discordBackend(@Suppress("UNUSED_PARAMETER") host: DiscordSdk.Host): DiscordSdk = NoDiscord

private object NoDiscord : DiscordSdk {
    override val present = false
    override fun authorize(activity: Activity) = Unit
    override fun show(activity: DiscordActivity?) = Unit
    override fun unlink(refreshToken: String?) = Unit
}
