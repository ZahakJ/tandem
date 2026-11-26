package io.github.zahakj.tandem.ui

/*
 * Keeping the highlight on the same thing when the home layout changes (Start › Layout, LayoutSwitch.kt). Each layout
 * names what it highlights its own way: the icon grid by tile ("game:<key>", "s:gba", "col:fav"), Shelves by row
 * ("c:<game>" in Continue, "s:gba", "col:fav"), Carousel, List and Console by entry ("s:gba", "col:fav"), Orbit by its
 * front orb (a grid key) and Spotlight by its rail group (a grid key) and the game in it. Plain Kotlin, so it is unit
 * tested (LayoutFocusTest).
 */

/** What was highlighted, said in the parts every layout can find again. */
data class LayoutSpot(
    /** A game's key, when a game was highlighted. */
    val game: String? = null,
    /** Its system, or the system highlighted. */
    val system: String? = null,
    /** The collection highlighted. */
    val collection: String? = null,
    /** An app's package. */
    val app: String? = null,
    /** The game is in Continue playing (Shelves' first row). */
    val continuing: Boolean = false,
    /** The game has a tile of its own on the home grid. */
    val gameOnGrid: Boolean = false,
)

/** Where a layout's highlight goes: the home route's key, and (Orbit, Spotlight) the group and item inside. */
data class LayoutTarget(val home: String?, val group: String? = null, val item: String? = null)

object LayoutFocus {
    /** The home route's key Orbit and Spotlight hold while their stage has the highlight (Orbit.STAGE, SpotlightMode.STAGE). */
    const val ORBIT_STAGE = "orbit"
    const val SPOTLIGHT_STAGE = "spotlight"

    /** The group a grid, Orbit or Spotlight shows the spot in: its system, its collection or its app. */
    private fun group(s: LayoutSpot): String? = s.system?.let { "s:$it" } ?: s.collection?.let { "col:$it" } ?: s.app?.let { "app:$it" }

    fun target(layout: String, s: LayoutSpot): LayoutTarget = when (layout) {
        "grid" -> LayoutTarget(if (s.game != null && s.gameOnGrid) "game:${s.game}" else group(s))
        "shelves" -> LayoutTarget(
            when {
                s.game != null && s.continuing -> "c:${s.game}"
                s.app != null -> "apps"
                else -> s.system?.let { "s:$it" } ?: s.collection?.let { "col:$it" }
            },
        )
        "orbit" -> LayoutTarget(ORBIT_STAGE, group = group(s))
        // Spotlight keeps every app in one group of its own.
        "spotlight" -> LayoutTarget(
            SPOTLIGHT_STAGE, group = if (s.app != null && s.system == null && s.collection == null) "apps" else group(s),
            item = s.game ?: s.app?.let { "app:$it" },
        )
        // Carousel, List and Console: one entry per system and collection.
        else -> LayoutTarget(s.system?.let { "s:$it" } ?: s.collection?.let { "col:$it" })
    }
}
