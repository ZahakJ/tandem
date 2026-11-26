package io.github.zahakj.tandem.ui

/*
 * Pip's face beside a list of a tile's rows (the lift's card, the edit screen): worried only over a row that can't be
 * taken back, named here row by row (by MenuAction.id). Never guessed from a row's colour (Colour, Favourite and a
 * game's performance rows are Peach too) or its place in the list. Plain Kotlin, so it is unit tested (PipMoodsTest).
 */
object PipMoods {
    /** Pip's moods (Design.kt's Pip). */
    const val HAPPY = 0
    const val EXCITED = 1
    const val WORRIED = 3

    /**
     * The rows that take something away for good: delete a folder, a game or every shortcut in a folder, uninstall an
     * app, remove a tile or a widget, close a running game, reset a game's play time. Hiding, moving and resetting a
     * look (Undo offers it back) are not among them.
     */
    val worrying: Set<String> = setOf("delete", "all", "uninstall", "remove", "closegame", "reset")

    fun worried(rowId: String?): Boolean = rowId != null && rowId in worrying

    /** On the lift's card: worried over such a row, excited inside a group, a page or a choice, happy otherwise. */
    fun lift(rowId: String?, deeper: Boolean): Int = when {
        worried(rowId) -> WORRIED
        deeper -> EXCITED
        else -> HAPPY
    }

    /** Beside the edit screen's preview: worried over such a row, excited while the look changes, happy otherwise. */
    fun editor(rowId: String?, section: EditSection): Int = when {
        worried(rowId) -> WORRIED
        section == EditSection.Look -> EXCITED
        else -> HAPPY
    }
}
