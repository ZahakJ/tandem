package io.github.zahakj.tandem.ui

import androidx.annotation.StringRes
import io.github.zahakj.tandem.R

/*
 * The sections of a tile's edit screen (TileEditor.kt) and which of a tile's rows goes in which. Plain Kotlin, so the
 * sorting is unit tested: every action a tile's old Y menu had must land somewhere (or be on purpose elsewhere), and
 * no section is left with a lone row.
 */

/** One section of the edit screen, in the order L1 / R1 step through them. */
enum class EditSection(@StringRes val label: Int, @StringRes val blurb: Int, @StringRes val tip: Int) {
    /** Its name and how it looks: the artwork studio's parts, an app's icon, a widget's size, the shape and corners. */
    Look(R.string.grid_edit_section_look, R.string.grid_edit_section_look_blurb, R.string.grid_edit_tip_look),
    /** What's inside it and how it opens: smart folder rules, sorting and views, a game's status, the screen it opens on. */
    Options(R.string.grid_edit_section_options, R.string.grid_edit_section_options_blurb, R.string.grid_edit_tip_options),
    /** Where it lives, and hiding it or taking it away, the ones that can't be undone last. */
    Organize(R.string.grid_edit_section_organize, R.string.grid_edit_section_organize_blurb, R.string.grid_edit_tip_organize),
}

object EditSections {
    /**
     * Actions that are somewhere better than the edit screen: Open and Close game sit on the lift itself; moving is
     * holding Y and marking is X while moving; the grid as a whole (Add to home, Tidy up, Undo, Search, Swap screens) is
     * on an empty spot's Y; Design folder… is the Look section's own rows.
     */
    val elsewhere: Set<String> = setOf("open", "closegame", "move", "select", "add", "tidy", "undo", "search", "swap", "design")

    /** The section a row (by [MenuAction.id]) belongs in, or null for one in [elsewhere]. Anything new is an option. */
    fun of(id: String): EditSection? = when {
        id in elsewhere -> null
        id in LOOK -> EditSection.Look
        id in ORGANIZE -> EditSection.Organize
        else -> EditSection.Options
    }

    /**
     * Rows sorted into their sections, in section order and, inside each, in a sensible order (see [ORDER]); sections
     * with nothing are left out. A section that would hold a single row while others have more gives it to Options (or
     * to its neighbour): a widget's Size and Remove sit with its options rather than alone on a page each.
     */
    fun <T> arrange(rows: List<T>, id: (T) -> String): Map<EditSection, List<T>> {
        val by = LinkedHashMap<EditSection, MutableList<T>>()
        for (s in EditSection.entries) by[s] = ArrayList()
        for (r in rows) by.getValue(of(id(r)) ?: continue).add(r)
        val kept = by.filterValues { it.isNotEmpty() }.toMutableMap()
        while (kept.size > 1) {
            val lone = kept.entries.firstOrNull { it.value.size == 1 }?.key ?: break
            val order = kept.keys.sortedBy { it.ordinal }
            val into = if (lone != EditSection.Options && EditSection.Options in kept) EditSection.Options
            else order.getOrNull(order.indexOf(lone) - 1) ?: order[order.indexOf(lone) + 1]
            kept.getValue(into).addAll(kept.remove(lone)!!)
        }
        val out = LinkedHashMap<EditSection, List<T>>()
        for (s in EditSection.entries) kept[s]?.let { list -> out[s] = list.sortedBy { rank(id(it)) } }
        return out
    }

    /**
     * The section the screen opens on: Look while it has more than the shape and corners to change (a designable tile,
     * an app with its icon and name), otherwise Options, where a game's status and options are.
     */
    fun opening(sections: Map<EditSection, List<*>>): EditSection? {
        val look = sections[EditSection.Look]?.size ?: 0
        return when {
            look > 2 -> EditSection.Look
            EditSection.Options in sections -> EditSection.Options
            else -> sections.keys.firstOrNull()
        }
    }

    /** Where a row sorts within its section: the name first; what's inside before how it opens; deleting last. */
    fun rank(id: String): Int {
        val i = ORDER.indexOf(if (id.startsWith("act:")) "act:" else id)
        return if (i < 0) ORDER.size else i
    }

    /** The name and every part of the look (the Look rows built by the edit screen, an app's icon, a widget's size). */
    private val LOOK = setOf("rename", "art", "colour", "hero", "logo", "icon", "resize", "shape", "corners", "frame", "resetlook")
    /** Where it lives (other folders, out of this one, the home grid, this folder's order), hiding it, taking it away. */
    private val ORGANIZE = setOf("folder", "also", "out", "home", "sortfolder", "hidefolder", "remove", "delete", "uninstall")

    private val ORDER = listOf(
        // Look
        "rename", "art", "colour", "hero", "logo", "icon", "resize", "shape", "corners", "frame", "resetlook",
        // Options: what it is and what's inside, then how it opens and counts
        "status", "options", "rules", "smart", "freeze", "sortin", "viewin", "arrange", "unarrange", "act:",
        "screen", "other", "game", "dock", "hide", "info",
        // Organize: where it lives, then hiding, then taking it away
        "folder", "also", "out", "home", "sortfolder", "hidefolder", "remove", "delete", "uninstall",
    )
}
