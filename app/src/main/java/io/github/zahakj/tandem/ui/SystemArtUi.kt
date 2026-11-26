package io.github.zahakj.tandem.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import io.github.zahakj.tandem.Libraries
import io.github.zahakj.tandem.NetGate
import io.github.zahakj.tandem.R
import io.github.zahakj.tandem.Tx
import io.github.zahakj.tandem.data.ArtSlot
import io.github.zahakj.tandem.data.OFFLINE_NOTE
import io.github.zahakj.tandem.data.RunState
import io.github.zahakj.tandem.data.Scraper
import io.github.zahakj.tandem.data.Store
import io.github.zahakj.tandem.data.SystemArt
import io.github.zahakj.tandem.data.SystemArtFetch
import io.github.zahakj.tandem.data.WebKind
import io.github.zahakj.tandem.data.WebOption

/*
 * Real system art, as the user meets it: Fetch system art (Settings › Look & feel › System icons, the Scrape library
 * screen, a system's artwork studio), Real art for every system (System icons), and the words for what was fetched.
 * The fetching is SystemArtFetch.kt; the pictures are made by WebArt.kt.
 */
object SystemArtUi {
    fun kindLabel(k: WebKind): String = Tx.s(
        when (k) {
            WebKind.Photo -> R.string.grid_design_web_photo
            WebKind.Logo -> R.string.grid_design_web_logo
            WebKind.Icon -> R.string.grid_design_web_icon
            WebKind.Illustration -> R.string.grid_design_web_illustration
            WebKind.Controller -> R.string.grid_design_web_controller
            WebKind.Fanart -> R.string.grid_design_web_fanart
            WebKind.Background -> R.string.grid_design_web_background
            WebKind.Banner -> R.string.grid_design_web_banner
        },
    )

    /** "Console photo · Wikimedia Commons". */
    fun optionLabel(o: WebOption): String = Tx.s(R.string.grid_design_web_option, kindLabel(o.kind), o.source.label)

    fun styleLabel(id: String?): String = Tx.s(
        when (id) {
            SystemArt.PHOTOS -> R.string.look_real_art_photos
            SystemArt.LOGOS -> R.string.look_real_art_logos
            SystemArt.FANART -> R.string.look_real_art_fanart
            else -> R.string.look_real_art_off
        },
    )

    /**
     * Fetches art for [platformIds] (every system when null), once the user has started it: never in private mode, and
     * on mobile data only after asking. Says when it starts and when it is done.
     */
    fun fetch(platformIds: List<String>? = null, metered: Boolean = Scraper.metered()) {
        when {
            NetGate.closed -> { Toasts.show(OFFLINE_NOTE); Sounds.error(); return }
            SystemArtFetch.running -> { Toasts.show(Tx.s(R.string.scrape_system_art_running)); return }
            metered -> {
                Toasts.show(Tx.s(R.string.scrape_system_art_metered), 8000, Toasts.Action(Tx.s(R.string.scrape_system_art_metered_go)) { fetch(platformIds, metered = false) })
                return
            }
        }
        if (!SystemArtFetch.start(platformIds) { r -> done(r) }) { Toasts.show(Tx.s(R.string.scrape_system_art_nothing)); return }
        val one = platformIds?.singleOrNull()?.let { Libraries.byId(it)?.platform?.name }
        Toasts.show(if (one != null) Tx.s(R.string.scrape_system_art_started_one, one) else Tx.s(R.string.scrape_system_art_started))
    }

    private fun done(r: io.github.zahakj.tandem.data.SystemArtRun) {
        if (r.state == RunState.Cancelled) return
        val text = when {
            r.withArt > 0 -> Tx.p(R.plurals.scrape_system_art_done, r.total, r.withArt, r.total)
            r.notes.isNotEmpty() -> r.notes.first()
            else -> Tx.s(R.string.scrape_system_art_done_none)
        }
        Toasts.show(text, 6000)
        if (r.withArt > 0) Sounds.found()
    }

    /** The Fetch row's value: how far a fetch is, or how many systems have art. */
    fun status(): String {
        SystemArtFetch.run?.takeIf { !it.finished }?.let { return "${it.done}/${it.total}" }
        SystemArtFetch.revision
        val n = Libraries.all.orEmpty().count { SystemArtFetch.options(it.platform.id).isNotEmpty() }
        return if (n == 0) Tx.s(R.string.look_fetch_art_none) else Tx.p(R.plurals.look_fetch_art_have, n, n)
    }

    /** Settings › Look & feel › System icons: real art for every system, and fetching it. */
    fun rows(): List<SettingRow> {
        val look = Store.settings.look
        val now = look.systemArt ?: ""
        return listOf(
            choice(
                Tx.s(R.string.look_real_art), Glyph.Image, Palette.Peach, Tx.s(R.string.look_real_art_help),
                listOf("" to Tx.s(R.string.look_real_art_off)) + SystemArt.styles.map { it to styleLabel(it) }, now,
                look = { v -> WebArtScene(v) },
                en = "Real art",
            ) { v ->
                val before = Store.settings.look.systemArt
                val next = v.ifEmpty { null }
                if (next == before) return@choice
                Store.updateSettings { it.copy(look = it.look.copy(systemArt = next)) }
                val any = Libraries.all.orEmpty().any { SystemArtFetch.options(it.platform.id).isNotEmpty() }
                if (next != null && !any) {
                    Toasts.show(Tx.s(R.string.look_real_art_need_fetch), 8000, Toasts.Action(Tx.s(R.string.look_fetch_art_go)) { fetch() })
                } else {
                    // Back to what was there before (Tandem's set, the pack or the drawn art), in one press.
                    Undo.offer(Tx.s(R.string.look_real_art_done, styleLabel(next))) {
                        Store.updateSettings { it.copy(look = it.look.copy(systemArt = before)) }
                    }
                }
            },
            SettingRow(
                Tx.s(R.string.look_fetch_art), status(), Palette.Peach, Glyph.Download, Tx.s(R.string.look_fetch_art_help),
                opens = true, keywords = "scrape download system art console photo logo wheel fanart banner wikimedia screenscraper retroarch",
                en = "Fetch system art",
            ) { if (SystemArtFetch.running) SystemArtFetch.cancel() else fetch() },
        )
    }
}

/** What [style] makes of a few of your systems: the first one's hero over its icons, or a note when there is no art yet. */
@Composable
fun WebArtScene(style: String, modifier: Modifier = Modifier) {
    if (!SystemArt.isStyle(style)) { ArtSetScene(Store.settings.look.artSet ?: "", modifier); return }
    val rev = SystemArtFetch.revision
    val libs = Libraries.all
    val systems = remember(libs, rev) {
        libs.orEmpty().map { it.platform.id }.filter { SystemArtFetch.options(it).isNotEmpty() }.take(4)
    }
    if (systems.isEmpty()) {
        Box(modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp)).background(Palette.CardHigh).padding(16.dp), contentAlignment = Alignment.Center) {
            Text(Tx.s(R.string.look_real_art_none_yet), style = MaterialTheme.typography.bodyMedium, color = Palette.Muted, textAlign = TextAlign.Center)
        }
        return
    }
    @Composable
    fun Picture(id: String, slot: ArtSlot, m: Modifier) {
        val option = remember(id, style, rev) { SystemArt.styleChoice(style, slot, WebArt.available(id, slot).keys) }
        val file = option?.let { WebArt.file(id, it, slot) }
        Box(m.background(Brush.verticalGradient(listOf(SystemColors.of(id), lerp(SystemColors.of(id), Color.Black, 0.4f))))) {
            if (file != null) AsyncImage(file, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
    }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Picture(systems.first(), ArtSlot.Hero, Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp)))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (id in systems) Picture(id, ArtSlot.Icon, Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(10.dp)))
            repeat(4 - systems.size) { Box(Modifier.weight(1f)) }
        }
    }
}
