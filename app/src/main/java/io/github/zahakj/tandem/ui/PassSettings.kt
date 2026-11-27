package io.github.zahakj.tandem.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.zahakj.tandem.Libraries
import io.github.zahakj.tandem.R
import io.github.zahakj.tandem.Tx
import io.github.zahakj.tandem.data.PlazaStore
import io.github.zahakj.tandem.pass.IdentityStore
import io.github.zahakj.tandem.pass.PassBook
import io.github.zahakj.tandem.pass.TandemPass

/*
 * Tandem Pass in Settings (Play & system › Tandem Pass), the switch's way on (Me first, then the permission primer),
 * and the "What others see" explainer. docs/guide/plaza.md says the same in words.
 */

/** Turning Tandem Pass on and off, from Settings, the Plaza's empty page or the quick paths to it. */
object PassSwitch {
    fun turnOn(ctx: Context) {
        when {
            !TandemPass.supported() -> { Sounds.error(); Toasts.show(Tx.s(R.string.plaza_old_android)); return }
            ctx.getSystemService(android.bluetooth.BluetoothManager::class.java)?.adapter == null -> {
                Sounds.error(); Toasts.show(Tx.s(R.string.plaza_no_bluetooth)); return
            }
            // A card needs a character: Me first, then back to this.
            !PlazaStore.data.made -> { MeEditor.open(turnOnAfter = true); return }
            !TandemPass.granted(ctx) -> { PassPrimer.denied = false; PassPrimer.open = true; return }
        }
        PlazaStore.update { it.copy(on = true) }
        TandemPass.refresh(ctx)
        Sounds.toggle(true)
        Toasts.show(Tx.s(if (TandemPass.status == TandemPass.Status.BluetoothOff) R.string.plaza_bt_off_toast else R.string.plaza_on_toast))
    }

    fun turnOff(ctx: Context) {
        PlazaStore.update { it.copy(on = false) }
        TandemPass.refresh(ctx)
        Sounds.toggle(false)
    }
}

/** The permission primer: what each Bluetooth permission is for, before Android asks. */
object PassPrimer {
    var open by mutableStateOf(false)
    /** Android said no (or "don't ask again"): the primer offers Android's settings instead. */
    var denied by mutableStateOf(false)
}

/** "What others see": your card and what never leaves. */
object PassPrivacy {
    var open by mutableStateOf(false)
}

/** The primer and the explainer, composed at the root of the library (App.kt). */
@Composable
fun PassOverlays() {
    PermissionPrimer()
    PrivacyExplainer()
}

@Composable
private fun PermissionPrimer() {
    val ctx = LocalContext.current
    val open = PassPrimer.open
    val route = "pass-primer"
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.isNotEmpty() && result.values.all { it }) {
            PassPrimer.open = false
            Nav.focusTick++
            PassSwitch.turnOn(ctx)
        } else {
            PassPrimer.denied = true
            Nav.focusTick++
        }
    }
    PanelGuard(open)
    InterceptButtons(open) { true }
    OverlayPanel(open, onClose = { PassPrimer.open = false; Nav.focusTick++ }) {
        val target = rememberFocusTarget(route, listOf("go", "later"))
        PanelSurface(Modifier.width(600.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Pip(56.dp, mood = if (PassPrimer.denied) 3 else 1)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(Tx.s(R.string.plaza_primer_title), style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        Tx.s(if (PassPrimer.denied) R.string.plaza_primer_denied else R.string.plaza_primer_intro),
                        style = MaterialTheme.typography.bodyMedium, color = Palette.Muted, maxLines = 3, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            PrimerLine(Glyph.Search, Palette.Sky, R.string.plaza_primer_scan_title, R.string.plaza_primer_scan)
            PrimerLine(Glyph.Sparkle, Palette.Mint, R.string.plaza_primer_advertise_title, R.string.plaza_primer_advertise)
            PrimerLine(Glyph.Swap, Palette.Peach, R.string.plaza_primer_connect_title, R.string.plaza_primer_connect)
            Text(Tx.s(R.string.plaza_primer_never), style = MaterialTheme.typography.bodySmall, color = Palette.Muted, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp, start = 4.dp))
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PillButton(Tx.s(R.string.plaza_primer_later), Palette.Lavender, Modifier.weight(1f).takesFocus(route, "later", target == "later"), fill = true) {
                    PassPrimer.open = false; Nav.focusTick++
                }
                PillButton(
                    Tx.s(if (PassPrimer.denied) R.string.plaza_primer_settings else R.string.plaza_primer_continue), Palette.Mint,
                    Modifier.weight(1f).takesFocus(route, "go", target != "later"), fill = true, filled = true,
                ) {
                    if (PassPrimer.denied) {
                        PassPrimer.open = false
                        runCatching {
                            ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                    } else ask.launch(TandemPass.permissions)
                }
            }
        }
    }
}

@Composable
private fun PrimerLine(glyph: Glyph, color: Color, title: Int, body: Int) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        GlyphChip(glyph, color, 34.dp, round = true)
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(Tx.s(title), style = MaterialTheme.typography.titleSmall, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(Tx.s(body), style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun PrivacyExplainer() {
    val open = PassPrivacy.open
    PanelGuard(open)
    InterceptButtons(open) { true }
    OverlayPanel(open, onClose = { PassPrivacy.open = false; Nav.focusTick++ }) {
        val d = PlazaStore.data
        val (fav, recent) = TandemPass.previewTitles()
        val scroll = androidx.compose.foundation.rememberScrollState()
        PanelSurface(Modifier.width(720.dp)) {
            Row {
                MyCard(Modifier.width(220.dp).height(300.dp))
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f).heightIn(max = 300.dp).fadingEdges(scroll).verticalScroll(scroll), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(Tx.s(R.string.plaza_privacy_title), style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    SectionTitle(Tx.s(R.string.plaza_privacy_sent), Palette.Mint, Modifier.padding(top = 6.dp))
                    Bullet(Tx.s(R.string.plaza_privacy_item_character))
                    Bullet(Tx.s(R.string.plaza_privacy_item_name, displayName(d.me.name)))
                    Bullet(Tx.s(R.string.plaza_privacy_item_greeting, Tx.s(greetingRes(d.me.greeting))))
                    if (fav != null) Bullet(Tx.s(R.string.plaza_privacy_item_fav, fav))
                    if (recent != null) Bullet(Tx.s(R.string.plaza_privacy_item_recent, recent))
                    Bullet(Tx.p(R.plurals.plaza_privacy_item_passes, d.passes, d.passes))
                    Bullet(Tx.s(R.string.plaza_privacy_item_key))
                    SectionTitle(Tx.s(R.string.plaza_privacy_never), Palette.Peach, Modifier.padding(top = 8.dp))
                    Bullet(Tx.s(R.string.plaza_privacy_never_1))
                    Bullet(Tx.s(R.string.plaza_privacy_never_2))
                    Bullet(Tx.s(R.string.plaza_privacy_never_3))
                    Text(Tx.s(R.string.plaza_privacy_how), style = MaterialTheme.typography.bodySmall, color = Palette.Muted, maxLines = 4, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                }
            }
            // Below the card and the list, never pushed off the panel by a long list.
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.End) {
                PillButton(Tx.s(R.string.plaza_privacy_close), Palette.Mint, Modifier.takesFocus("pass-privacy", "ok", true)) { PassPrivacy.open = false; Nav.focusTick++ }
            }
        }
    }
}

@Composable
private fun Bullet(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(5.dp).clip(CircleShape).background(Palette.Muted))
        Text(text, Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** The side panel's picture for the Tandem Pass rows: your card and what Tandem Pass is doing. */
@Composable
private fun PassPreview() {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val (line, color) = statusLine(TandemPass.status)
        Pill(line, color)
        MyCard(Modifier.width(220.dp).height(300.dp))
    }
}

// --- Settings ------------------------------------------------------------------------------------------------------

/** Play & system › Tandem Pass. */
@Composable
fun passSettings(): SettingSection {
    val ctx = LocalContext.current
    val d = PlazaStore.data
    val blocked = d.blocked.size
    val id = TandemPass.identityOrNull()
    val hasId = id != null || androidx.compose.runtime.remember(d.on) { IdentityStore.exists(ctx) }
    val preview: @Composable () -> Unit = { PassPreview() }
    return SettingSection(Tx.s(R.string.plaza_settings_title), en = "Bump",
        rows = listOf(
            toggle(Tx.s(R.string.plaza_settings_on), Glyph.Sparkle, Palette.Mint, Tx.s(R.string.plaza_settings_on_help), d.on, preview = preview, en = "Bump") { on ->
                if (on) PassSwitch.turnOn(ctx) else PassSwitch.turnOff(ctx)
            }.copy(keywords = "bump bumping bumped streetpass street pass plaza passing bluetooth nearby meet people friends avatar character mii card wave"),
            toggle(Tx.s(R.string.plaza_settings_playing), Glyph.Gamepad, Palette.Sky, Tx.s(R.string.plaza_settings_playing_help), d.whilePlaying, en = "Bump while playing") { on ->
                PlazaStore.update { it.copy(whilePlaying = on) }
                TandemPass.refresh(ctx)
            }.copy(keywords = "background notification games battery bump bumping tandem pass"),
            SettingRow(
                Tx.s(R.string.plaza_settings_me), displayName(d.me.name), Palette.Peach, Glyph.Pencil, Tx.s(R.string.plaza_settings_me_help),
                opens = true, preview = preview, en = "Edit Me", keywords = "me avatar character maker face hair outfit name greeting favourite game",
            ) { MeEditor.open() },
            SettingRow(
                Tx.s(R.string.plaza_settings_preview), "", Palette.Lavender, Glyph.Eye, Tx.s(R.string.plaza_settings_preview_help),
                action = Tx.s(R.string.plaza_settings_preview_action), preview = preview, en = "What others see", keywords = "privacy card what is shared sent data",
            ) { PassPrivacy.open = true },
            SettingRow(
                Tx.s(R.string.plaza_settings_open), Tx.p(R.plurals.plaza_met_count, d.visitors.size, d.visitors.size), Palette.Butter, Glyph.Home,
                Tx.s(R.string.plaza_settings_open_help), opens = true, en = "Open the Plaza", keywords = "plaza visitors people met bumped passed",
            ) { PlazaUi.open() },
            SettingRow(
                Tx.s(R.string.plaza_settings_blocked), if (blocked == 0) Tx.s(R.string.plaza_settings_blocked_none) else Tx.p(R.plurals.plaza_settings_blocked_n, blocked, blocked),
                Palette.Peach, Glyph.Shield, Tx.s(R.string.plaza_settings_blocked_help), action = if (blocked > 0) Tx.s(R.string.plaza_settings_unblock_action) else null,
                en = "Blocked people", keywords = "block unblock blocked people bump tandem pass",
            ) {
                if (blocked == 0) Toasts.show(Tx.s(R.string.plaza_settings_blocked_none_toast))
                else Confirm.hold(Tx.s(R.string.plaza_unblock_title), Tx.s(R.string.plaza_unblock_detail), Tx.s(R.string.plaza_settings_unblock_action)) {
                    PlazaStore.update { PassBook.unblockAll(it) }
                    Toasts.show(Tx.s(R.string.plaza_settings_unblocked))
                }
            },
            SettingRow(
                Tx.s(R.string.plaza_settings_clear), "", Palette.Peach, Glyph.Trash, Tx.s(R.string.plaza_settings_clear_help),
                action = Tx.s(R.string.plaza_clear_yes), en = "Clear Plaza", keywords = "clear reset forget visitors plaza",
            ) {
                Confirm.hold(Tx.s(R.string.plaza_clear_title), Tx.s(R.string.plaza_clear_detail), Tx.s(R.string.plaza_clear_yes)) {
                    PlazaStore.update { PassBook.clear(it) }
                    Toasts.show(Tx.s(R.string.plaza_settings_cleared))
                }
            },
            SettingRow(
                Tx.s(R.string.plaza_settings_id), id?.fingerprint?.substringBefore(' ')?.let { "$it …" } ?: if (hasId) "" else Tx.s(R.string.plaza_settings_id_none), // tx-ok: an ID and an ellipsis
                Palette.Lavender, Glyph.Key,
                id?.let { Tx.s(R.string.plaza_settings_id_help, it.fingerprint) } ?: Tx.s(R.string.plaza_settings_id_help_none),
                action = if (hasId) Tx.s(R.string.plaza_settings_id_action) else null, en = "Bump ID", keywords = "identity key fingerprint safety number concord id",
            ) {
                if (hasId) TandemPass.withIdentity(ctx) { Toasts.show(Tx.s(R.string.plaza_settings_id_toast, it.fingerprint), 6000L) }
                else Toasts.show(Tx.s(R.string.plaza_settings_id_help_none))
            },
        ),
    )
}

/** The Start panel's row: the Plaza, with how many are new. Null in private mode. */
fun plazaStartRow(): MenuAction? {
    if (io.github.zahakj.tandem.Privacy.enabled) return null
    val d = PlazaStore.data
    val fresh = PassBook.newCount(d)
    val sub = if (!d.on) Tx.s(R.string.plaza_start_off) else null
    return MenuAction("plaza", Tx.s(R.string.plaza_app_title), null, Palette.Peach, icon = TIcon.Plaza, value = if (fresh > 0) Tx.p(R.plurals.plaza_start_new, fresh, fresh) else null, sub = sub) {
        StartMenu.go { PlazaUi.open() }
    }
}
