package io.github.zahakj.tandem.ui

import androidx.annotation.StringRes
import io.github.zahakj.tandem.R

/*
 * The sections of a tile's edit screen (TileEditor.kt) and which of a tile's actions goes in which. Plain Kotlin, so
 * the sorting is unit tested: every action a tile's old Y menu had must land somewhere (or be on purpose elsewhere).
 */

/** One section of the edit screen, in the order L1 / R1 step through them. */
enum class EditSection(@StringRes val label: Int, @StringRes val blurb: Int) {
    Name(R.string.grid_edit_section_name, R.string.grid_edit_section_name_blurb),
    Look(R.string.grid_edit_section_look, R.string.grid_edit_section_look_blurb),
    Contents(R.string.grid_edit_section_contents, R.string.grid_edit_section_contents_blurb),
    Behaviour(R.string.grid_edit_section_behaviour, R.string.grid_edit_section_behaviour_blurb),
    Organize(R.string.grid_edit_section_organize, R.string.grid_edit_section_organize_blurb),
    Remove(R.string.grid_edit_section_remove, R.string.grid_edit_section_remove_blurb),
}

object EditSections {
    /**
     * Actions that are somewhere better than the edit screen: Open and Close game sit on the lift itself; moving is
     * holding Y and marking is X while moving; the grid as a whole (Add to home, Tidy up, Undo, Search, Swap screens) is
     * on an empty spot's Y; Design folder… is the Look section's own rows.
     */
    val elsewhere: Set<String> = setOf("open", "closegame", "move", "select", "add", "tidy", "undo", "search", "swap", "design")

    /** The section a tile action (by [MenuAction.id]) belongs in, or null for one in [elsewhere]. */
    fun of(id: String): EditSection? = when {
        id in elsewhere -> null
        id == "rename" -> EditSection.Name
        // An app's own icon, a widget's size.
        id == "icon" || id == "resize" -> EditSection.Look
        id in CONTENTS || id.startsWith("act:") -> EditSection.Contents
        id in BEHAVIOUR -> EditSection.Behaviour
        id in ORGANIZE -> EditSection.Organize
        id in REMOVE -> EditSection.Remove
        else -> EditSection.Behaviour
    }

    /** Smart folder rules, sorting and views inside, a system's arrangement, a game's options and status, a widget's own. */
    private val CONTENTS = setOf("rules", "smart", "freeze", "sortin", "viewin", "options", "arrange", "unarrange", "status")
    /** Where it opens, and how an app counts (a game, in the drawer, in the dock). */
    private val BEHAVIOUR = setOf("screen", "other", "game", "hide", "dock", "info")
    /** Where it lives: other folders, out of this one, the home grid, this folder's order. */
    private val ORGANIZE = setOf("folder", "also", "out", "home", "sortfolder")
    /** Hiding it and taking it away. */
    private val REMOVE = setOf("hidefolder", "remove", "delete", "uninstall")
}
