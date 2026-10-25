package io.github.zahakj.tandem.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.Executors

/** How much room a widget takes. The footprint in grid cells depends on the screen; see `ui/Widgets.kt`. */
@Serializable
enum class WidgetSize(val label: String) { Small("Small"), Medium("Medium"), Wide("Wide"), Tall("Tall") }

/**
 * Where a widget shows: the widget row on the home (top) screen, the bottom screen when nothing is highlighted, both,
 * or a spot of its own on the icon grid (see IconGridConfig.spans).
 */
@Serializable
enum class WidgetPlace(val label: String) { Home("Home screen"), Bottom("Bottom screen"), Both("Both screens"), Grid("Home grid") }

/**
 * One placed widget. [type] is a widget kind id (see `WidgetKinds`); unknown types are kept but not shown, so a
 * newer backup still loads. [options] are the kind's own settings as strings. Order is the order in the list.
 */
@Serializable
data class Widget(
    val id: String,
    val type: String,
    val size: WidgetSize = WidgetSize.Medium,
    val place: WidgetPlace = WidgetPlace.Home,
    val options: Map<String, String> = emptyMap(),
)

/** An entry in the Up next list: a game from the library, or a free note ("Finish Zelda"). */
@Serializable
data class UpNextEntry(val key: String? = null, val note: String? = null, val added: Long = 0)

@Serializable
data class WidgetConfig(
    /** null until the user changes anything, so the defaults show. */
    val widgets: List<Widget>? = null,
    val homeEnabled: Boolean = true,
    /** Rows in the home widget area: 0 = automatic (two when a tall widget is there), 1 or 2. */
    val homeRows: Int = 0,
    val upNext: List<UpNextEntry> = emptyList(),
    /** Where Add here › Widget… on the icon grid puts a new widget: "cells" (a spot of its own) or "row" (the widget row). */
    val gridPlace: String = "cells",
)

/** Widgets and the Up next list, as `widgets.json` in the app's files folder, the same way [HomeStore] keeps its data. */
object WidgetStore {
    val DEFAULT_WIDGETS = listOf(
        Widget("storage", "storage", WidgetSize.Medium, WidgetPlace.Home),
        Widget("stats", "stats", WidgetSize.Medium, WidgetPlace.Home),
        Widget("greeting", "greeting", WidgetSize.Wide, WidgetPlace.Bottom),
    )

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private val writer = Executors.newSingleThreadExecutor()
    private var dir: File? = null

    var config by mutableStateOf(WidgetConfig())
        private set

    val widgets: List<Widget> get() = config.widgets ?: DEFAULT_WIDGETS

    /** Safe to call from anywhere, any number of times; reads the file once. */
    fun init(ctx: Context) {
        if (dir != null) return
        val d = ctx.applicationContext.filesDir
        dir = d
        reload()
    }

    /** Reads widgets.json again (after a restore). Blocking. */
    fun reload() {
        val d = dir ?: return
        val c = runCatching { json.decodeFromString<WidgetConfig>(File(d, "widgets.json").readText()) }.getOrDefault(WidgetConfig())
        synchronized(this) { config = c }
    }

    fun update(change: (WidgetConfig) -> WidgetConfig) {
        val snapshot = synchronized(this) { change(config).also { config = it } }
        val d = dir ?: return
        val text = json.encodeToString(snapshot)
        writer.execute {
            val tmp = File(d, "widgets.json.tmp")
            tmp.writeText(text)
            tmp.renameTo(File(d, "widgets.json"))
        }
    }

    fun updateWidgets(change: (List<Widget>) -> List<Widget>) = update { it.copy(widgets = change(it.widgets ?: DEFAULT_WIDGETS)) }

    fun updateWidget(id: String, change: (Widget) -> Widget) = updateWidgets { list -> list.map { if (it.id == id) change(it) else it } }

    fun newId(type: String): String = "$type-" + java.lang.Long.toString(System.currentTimeMillis(), 36) + (0..999).random()
}
