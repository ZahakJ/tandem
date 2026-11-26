package io.github.zahakj.tandem.ui.whatsnew

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import io.github.zahakj.tandem.R
import io.github.zahakj.tandem.ui.Glyph

/*
 * The What's new decks: one per minor version (x.Y.0), one slide per feature. A slide is a picture first (a live piece
 * of Tandem drawn by its own composables with sample data, see Visuals09.kt), then a short title, a line or two, and a
 * short name for the dots and the other screen.
 *
 * ADDING A RELEASE: every minor version needs a deck. Write its slides (a visual each; strings in
 * strings_whatsnew.xml in all five languages) and add it to [Decks.all]. WhatsNewTest fails the build when
 * versionName is a new x.Y.0 without one, and a patch (x.Y.z) shows its minor's deck. docs/RELEASING.md.
 */

/** A slide's colour, looked up in the live theme when drawn (Palette.Peach…), so the registry stays plain data. */
enum class Tint { Peach, Mint, Lavender, Sky, Butter }

/**
 * One feature: [title] and [body] on the top screen beside its [visual], [short] on the dots and the other screen,
 * [glyph] and [tint] for its chip, [mood] Pip's face while it shows (0 happy, 1 excited).
 */
class Slide(
    val id: String,
    @StringRes val title: Int,
    @StringRes val body: Int,
    @StringRes val short: Int,
    val glyph: Glyph,
    val tint: Tint,
    val mood: Int,
    val visual: @Composable () -> Unit,
)

/** A minor version's deck: its [version] ("0.9.0"), a [tagline] under the version, and its slides. */
class Deck(val version: String, @StringRes val tagline: Int, val slides: List<Slide>)

object Decks {
    /** Every deck, oldest first. A new minor version adds its own at the end. */
    val all: List<Deck> by lazy { listOf(deck090, deck0100) }

    val versions: List<String> get() = all.map { it.version }

    fun of(version: String): Deck? = all.firstOrNull { it.version == version }
}

/** 0.9: the lift, the edit screen, try-on, Stage, gliding about, system art, more libraries, sharing and Thock. */
private val deck090 = Deck(
    "0.9.0", R.string.whatsnew_090_tagline,
    listOf(
        Slide("lift", R.string.whatsnew_090_lift_title, R.string.whatsnew_090_lift_body, R.string.whatsnew_090_lift_short, Glyph.Move, Tint.Peach, 1) { LiftVisual() },
        Slide("edit", R.string.whatsnew_090_edit_title, R.string.whatsnew_090_edit_body, R.string.whatsnew_090_edit_short, Glyph.Pencil, Tint.Lavender, 1) { EditVisual() },
        Slide("tryon", R.string.whatsnew_090_tryon_title, R.string.whatsnew_090_tryon_body, R.string.whatsnew_090_tryon_short, Glyph.Layers, Tint.Butter, 0) { TryOnVisual() },
        Slide("stage", R.string.whatsnew_090_stage_title, R.string.whatsnew_090_stage_body, R.string.whatsnew_090_stage_short, Glyph.Screens, Tint.Sky, 1) { StageVisual() },
        Slide("glide", R.string.whatsnew_090_glide_title, R.string.whatsnew_090_glide_body, R.string.whatsnew_090_glide_short, Glyph.Highlight, Tint.Mint, 0) { GlideVisual() },
        Slide("art", R.string.whatsnew_090_art_title, R.string.whatsnew_090_art_body, R.string.whatsnew_090_art_short, Glyph.Image, Tint.Peach, 1) { ArtVisual() },
        Slide("library", R.string.whatsnew_090_library_title, R.string.whatsnew_090_library_body, R.string.whatsnew_090_library_short, Glyph.Gamepad, Tint.Sky, 0) { LibraryVisual() },
        Slide("share", R.string.whatsnew_090_share_title, R.string.whatsnew_090_share_body, R.string.whatsnew_090_share_short, Glyph.Heart, Tint.Lavender, 1) { ShareVisual() },
    ),
)

/**
 * 0.10: This week, Surprise me's reel, multi-disc games, smarter sorting and rules, search filters, RetroAchievements
 * up close, the Settings home, and Plaza, the new soundtrack, last (an equaliser, notes and
 * Pip). Pictures in Visuals010.kt.
 */
private val deck0100 = Deck(
    "0.10.0", R.string.whatsnew_0100_tagline,
    listOf(
        Slide("week", R.string.whatsnew_0100_week_title, R.string.whatsnew_0100_week_body, R.string.whatsnew_0100_week_short, Glyph.Star, Tint.Butter, 1) { WeekVisual() },
        Slide("surprise", R.string.whatsnew_0100_surprise_title, R.string.whatsnew_0100_surprise_body, R.string.whatsnew_0100_surprise_short, Glyph.Sparkle, Tint.Peach, 1) { SurpriseVisual() },
        Slide("discs", R.string.whatsnew_0100_discs_title, R.string.whatsnew_0100_discs_body, R.string.whatsnew_0100_discs_short, Glyph.Layers, Tint.Sky, 0) { DiscsVisual() },
        Slide("smart", R.string.whatsnew_0100_smart_title, R.string.whatsnew_0100_smart_body, R.string.whatsnew_0100_smart_short, Glyph.Sort, Tint.Lavender, 0) { SmartVisual() },
        Slide("search", R.string.whatsnew_0100_search_title, R.string.whatsnew_0100_search_body, R.string.whatsnew_0100_search_short, Glyph.Search, Tint.Mint, 0) { SearchVisual() },
        Slide("ra", R.string.whatsnew_0100_ra_title, R.string.whatsnew_0100_ra_body, R.string.whatsnew_0100_ra_short, Glyph.Trophy, Tint.Butter, 1) { AchievementsVisual() },
        Slide("settings", R.string.whatsnew_0100_settings_title, R.string.whatsnew_0100_settings_body, R.string.whatsnew_0100_settings_short, Glyph.Grid, Tint.Peach, 0) { SettingsVisual() },
        Slide("music", R.string.whatsnew_0100_music_title, R.string.whatsnew_0100_music_body, R.string.whatsnew_0100_music_short, Glyph.Music, Tint.Lavender, 1) { MusicVisual() },
    ),
)
