package io.github.zahakj.tandem.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import io.github.zahakj.tandem.NetGate
import io.github.zahakj.tandem.romm.RommAddress
import io.github.zahakj.tandem.romm.RommClient
import io.github.zahakj.tandem.romm.RommConfig
import io.github.zahakj.tandem.romm.RommHttpTransport
import io.github.zahakj.tandem.romm.RommResult
import io.github.zahakj.tandem.romm.RommVault
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext

/** Shared by both displays. Only explicit settings actions probe the server; no library/startup networking. */
private object RommSettingsState {
    private val scope = MainScope()
    private lateinit var vault: RommVault
    private var initialized = false
    private var storageFailed = false
    private var testJob: Job? = null
    var config by mutableStateOf(RommConfig())
        private set
    var busy by mutableStateOf(false)
        private set
    var testing by mutableStateOf(false)
        private set
    var status by mutableStateOf("Loading configuration…")
        private set

    fun start(context: Context) {
        if (initialized) return
        initialized = true
        busy = true
        scope.launch {
            try {
                config = withContext(Dispatchers.IO) {
                    vault = RommVault(context)
                    vault.load()
                }
                status = disconnected()
            } catch (_: Exception) {
                storageFailed = true
                status = "Secure storage could not be read. Use Forget RoMM, then configure again"
            } finally { busy = false }
        }
    }

    private fun disconnected() = if (config.enabled) "Disconnected · test connection to check" else "Disabled"

    fun available(): Boolean {
        if (busy) { Toasts.show("Wait for RoMM to finish, or cancel the connection test"); return false }
        if (storageFailed) { Toasts.show("Use Forget RoMM to reset the unreadable configuration"); return false }
        return true
    }

    fun save(next: RommConfig) {
        if (!available()) return
        busy = true
        scope.launch {
            try {
                withContext(Dispatchers.IO) { vault.save(next) }
                config = next
                status = disconnected()
            } catch (_: Exception) {
                status = "Could not verify secure configuration was saved. Try again or use Forget RoMM"
            } finally { busy = false }
        }
    }

    fun forget() {
        if (busy) { Toasts.show("Wait for RoMM to finish, or cancel the connection test"); return }
        busy = true
        scope.launch {
            try {
                withContext(Dispatchers.IO) { vault.clear() }
                config = RommConfig()
                storageFailed = false
                status = "Disabled · configuration removed"
            } catch (_: Exception) {
                status = "Could not remove secure configuration. Try again"
            } finally { busy = false }
        }
    }

    fun test() {
        if (!available()) return
        busy = true
        testing = true
        status = "Connecting…"
        val snapshot = config
        testJob = scope.launch {
            try {
                val result = runInterruptible(Dispatchers.IO) {
                    RommClient(RommHttpTransport(NetGate::check), NetGate::check).test(snapshot)
                }
                status = when (result) {
                    is RommResult.Connected -> "Connected · RoMM 5.4.1 · ${result.platforms} platforms"
                    is RommResult.Failed -> result.problem.message
                }
            } catch (e: CancellationException) {
                status = disconnected()
                throw e
            } finally {
                busy = false
                testing = false
                testJob = null
            }
        }
    }

    fun cancelTest() { testJob?.cancel() }
}

/** Uses Tandem's settings rows, side-panel help and controller keyboard on either library display. */
@Composable
fun rommSettings(): SettingSection {
    val context = LocalContext.current.applicationContext
    LaunchedEffect(Unit) { RommSettingsState.start(context) }
    val privateMode = NetGate.closed
    LaunchedEffect(privateMode) { if (privateMode) RommSettingsState.cancelTest() }
    val config = RommSettingsState.config
    return SettingSection(
        "RoMM",
        listOf(
            SettingRow(
                "Enable RoMM", "", Palette.Mint, Glyph.Key,
                "Optional RoMM 5.4.1 connection. Configure a server and token first. Private mode always keeps Tandem offline. Connections run only when you choose Test connection.",
                toggle = config.enabled, keywords = "romm remote server enable disable",
            ) {
                if (!RommSettingsState.available()) return@SettingRow
                if (!config.configured) Toasts.show("Set the RoMM server URL and API token first")
                else RommSettingsState.save(config.copy(enabled = !config.enabled))
            },
            SettingRow(
                "RoMM server URL", config.server.ifEmpty { "Not set" }, Palette.Sky, Glyph.Key,
                "Enter the HTTPS website URL, including a reverse-proxy subpath if used, without /api. Tailscale connections also need HTTPS with a valid certificate. Changing servers removes the saved token and disables RoMM.",
                keywords = "romm address https tailscale host server",
            ) {
                if (!RommSettingsState.available()) return@SettingRow
                TextEntry.ask("RoMM server URL", config.server) { entered ->
                    val address = try { RommAddress.normalize(entered) } catch (_: IllegalArgumentException) {
                        Toasts.show("Enter a valid HTTPS website URL without credentials, query, fragment or /api")
                        return@ask
                    }
                    if (address != config.server) RommSettingsState.save(RommConfig(server = address))
                }
            },
            SettingRow(
                "RoMM API token", if (config.token.isEmpty()) "Not set" else "Saved securely", Palette.Sky, Glyph.Key,
                "Use a dedicated Client API Token with platforms.read permission. The token is encrypted with Android Keystore and excluded from backups. Leave the entry blank to remove it. Changing servers requires a new token.",
                keywords = "romm authentication client api token credentials",
            ) {
                if (!RommSettingsState.available()) return@SettingRow
                if (config.server.isEmpty()) { Toasts.show("Set the RoMM server URL first"); return@SettingRow }
                // Never populate a text editor with the stored token.
                TextEntry.ask("RoMM Client API Token", secret = true) { entered ->
                    val token = entered.trim()
                    if (token.isNotEmpty() && !RommAddress.validToken(token)) Toasts.show("Enter the token without spaces or control characters")
                    else RommSettingsState.save(config.copy(token = token, enabled = config.enabled && token.isNotEmpty()))
                }
            },
            SettingRow(
                "Test RoMM connection", if (privateMode) "Private mode · offline" else RommSettingsState.status, Palette.Mint, Glyph.Refresh,
                "Check the running server's OpenAPI version and platform operation, then make an authenticated read-only platform request. No games are changed. Connected means the last test succeeded; it is not a continuous connection.",
                action = if (RommSettingsState.testing) "Cancel" else "Test", keywords = "romm connect disconnected status error retry",
            ) { if (RommSettingsState.testing) RommSettingsState.cancelTest() else RommSettingsState.test() },
            SettingRow(
                "Forget RoMM", "", Palette.Peach, Glyph.Key,
                "Remove the server and encrypted token from this device and disable RoMM. Your local library and server content stay as they are.",
                keywords = "romm disconnect logout remove token",
            ) { RommSettingsState.forget() },
        ),
    )
}
