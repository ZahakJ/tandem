package io.github.zahakj.tandem

import java.security.MessageDigest

/**
 * Reading GameNative's library: the games installed from Steam, Epic, GOG and Amazon, out of its own database
 * (`databases/pluvia.db`, a copy of it: Tandem never opens GameNative's files in place). No Android here: the queries
 * go through [Db], which is Android's SQLite on the device and a plain SQLite driver in the tests.
 *
 * How GameNative itself tells an installed game (its DAOs' getInstalledGames, what its "Export for frontend" sync
 * writes files for): Steam games whose app_info row has is_downloaded = 1; Epic games with is_installed = 1 that
 * aren't DLC or Unreal Engine content; GOG games with is_installed = 1 and not excluded; Amazon games with
 * is_installed = 1. A column a GameNative version doesn't have yet is left out of the query.
 */
object GameNativeLibrary {
    const val PACKAGE = "app.gamenative"
    const val DATA = "/data/data/app.gamenative"
    const val DB = "$DATA/databases/pluvia.db"
    /** The database and its write-ahead log: copied together, or the copy misses what isn't checkpointed yet. */
    val DB_FILES = listOf(DB, "$DB-wal", "$DB-shm")
    /** Coil's disk cache, where GameNative keeps the pictures it showed (its MainActivity sets it up). */
    const val IMAGE_CACHE = "$DATA/cache/image_cache"

    /** Epic namespaces GameNative leaves out of its installed games (Unreal Engine content). */
    private val NOT_GAMES_EPIC = listOf("ue", "89efe5924d3d467c839449ab6ab52e7f")

    /** One query on the database: rows of column name to value (a Long, a Double, a String or null); null if it failed. */
    fun interface Db {
        fun query(sql: String): List<Map<String, Any?>>?
    }

    /**
     * Every game installed in GameNative that Tandem can start, Steam first; null when the database can't be read at
     * all. Never throws for a strange database.
     */
    fun read(db: Db): List<AppGame>? {
        val tables = db.query("SELECT name FROM sqlite_master WHERE type = 'table'")
            ?.mapNotNull { it["name"] as? String }?.toSet() ?: return null
        val columns = HashMap<String, Set<String>>()
        fun cols(table: String): Set<String> = columns.getOrPut(table) {
            if (table !in tables) emptySet()
            else db.query("PRAGMA table_info(`$table`)")?.mapNotNull { it["name"] as? String }?.toSet().orEmpty()
        }
        /** SELECT the [wanted] columns [table] has (null for the rest), where [where]'s conditions on columns it has hold. */
        fun select(table: String, wanted: List<String>, where: List<Pair<String, String>>): List<Map<String, Any?>> {
            val have = cols(table)
            val picked = wanted.filter { it in have }
            if (picked.isEmpty()) return emptyList()
            val conditions = where.filter { (col, _) -> col in have }.map { it.second }
            val sql = "SELECT " + picked.joinToString(", ") { "`$it`" } + " FROM `$table`" +
                (if (conditions.isEmpty()) "" else " WHERE " + conditions.joinToString(" AND "))
            return db.query(sql).orEmpty()
        }

        // When each game was last started in GameNative, by "<SOURCE>_<id>" (milliseconds).
        val played = HashMap<String, Long>()
        if ("library_play_history" in tables) {
            for (r in select("library_play_history", listOf("app_id", "last_played"), emptyList())) {
                val key = r.str("app_id") ?: continue
                played[key] = DroidDeckLibrary.millis(r.long("last_played"))
            }
        }
        fun lastPlayed(source: String, id: String, own: Long = 0): Long =
            maxOf(played["${source.uppercase()}_$id"] ?: 0, DroidDeckLibrary.millis(own))

        val out = ArrayList<AppGame>()

        // Steam: GameNative's own rule, app_info.is_downloaded.
        if ("is_downloaded" in cols("app_info") && "name" in cols("steam_app")) {
            val rows = db.query(
                "SELECT steam_app.id AS id, steam_app.name AS name FROM steam_app INNER JOIN app_info ON steam_app.id = app_info.id " +
                    "WHERE app_info.is_downloaded = 1",
            ).orEmpty()
            for (r in rows) {
                val id = DroidDeckLibrary.normalId(r.str("id")) ?: continue
                out += AppGame(AppLibrary.GAMENATIVE, AppGame.STEAM, id, r.str("name")?.trim().orEmpty(),
                    lastPlayed = lastPlayed("steam", id), storeId = id)
            }
        }

        // Epic: GameNative's row id is what its launch takes; the app name is Epic's own id (DroidDeck's too).
        if ("is_installed" in cols("epic_games")) {
            val rows = select(
                "epic_games",
                listOf("id", "app_name", "catalog_id", "title", "art_cover", "art_square", "art_logo", "art_portrait"),
                listOf(
                    "is_installed" to "is_installed = 1",
                    "is_dlc" to "is_dlc = 0",
                    "namespace" to "namespace NOT IN (" + NOT_GAMES_EPIC.joinToString(", ") { "'$it'" } + ")",
                ),
            )
            for (r in rows) {
                val id = DroidDeckLibrary.normalId(r.str("id")) ?: continue
                val cover = r.url("art_cover")
                val square = r.url("art_square")
                // The pictures GameNative's library shows (LibraryViewModel), so the ones its cache has.
                out += AppGame(AppLibrary.GAMENATIVE, "epic", id, r.str("title")?.trim().orEmpty(),
                    lastPlayed = lastPlayed("epic", id),
                    art = cover ?: square, hero = r.url("art_portrait") ?: square ?: cover, logo = r.url("art_logo"),
                    icon = square ?: cover,
                    storeId = r.str("app_name")?.trim()?.ifEmpty { null } ?: r.str("catalog_id")?.trim()?.ifEmpty { null })
            }
        }

        // GOG: the id is GOG's product id, as a string; GameNative's launch takes it as a number.
        if ("is_installed" in cols("gog_games")) {
            val rows = select(
                "gog_games",
                listOf("id", "title", "image_url", "icon_url", "background_url", "vertical_cover_url", "last_played"),
                listOf("is_installed" to "is_installed = 1", "exclude" to "exclude = 0"),
            )
            for (r in rows) {
                val id = DroidDeckLibrary.normalId(r.str("id"))?.takeIf { it.toIntOrNull() != null } ?: continue
                val image = r.url("image_url")
                val iconUrl = r.url("icon_url")
                out += AppGame(AppLibrary.GAMENATIVE, "gog", id, r.str("title")?.trim().orEmpty(),
                    lastPlayed = lastPlayed("gog", id, r.long("last_played")),
                    art = r.url("vertical_cover_url") ?: iconUrl ?: image, hero = r.url("background_url") ?: image ?: iconUrl,
                    icon = iconUrl ?: image, storeId = id)
            }
        }

        // Amazon: GameNative's row id for the launch, Amazon's product id to match.
        if ("is_installed" in cols("amazon_games")) {
            val rows = select(
                "amazon_games",
                listOf("app_id", "product_id", "title", "art_url", "hero_url", "last_played"),
                listOf("is_installed" to "is_installed = 1"),
            )
            for (r in rows) {
                val id = DroidDeckLibrary.normalId(r.str("app_id")) ?: continue
                val art = r.url("art_url")
                out += AppGame(AppLibrary.GAMENATIVE, "amazon", id, r.str("title")?.trim().orEmpty(),
                    lastPlayed = lastPlayed("amazon", id, r.long("last_played")),
                    art = art, hero = r.url("hero_url") ?: art, icon = art,
                    storeId = r.str("product_id")?.trim()?.ifEmpty { null })
            }
        }
        return out.distinctBy { it.id }
    }

    /**
     * Where Coil (GameNative's image loader) keeps the picture it loaded from [url]: its disk cache names an entry by
     * the SHA-256 of the address, and keeps the bytes in "<hash>.1" (".0" holds the response headers).
     */
    fun cachedPicture(url: String): String = "$IMAGE_CACHE/${sha256(url)}.1"

    /** Where to look for a picture of each kind of [g] as root: what GameNative's cache holds for its addresses. */
    fun artCandidates(g: AppGame): Map<DroidDeckLibrary.ArtKind, List<String>> =
        DroidDeckLibrary.ArtKind.entries.associateWith { kind -> listOfNotNull(kind.of(g)?.let(::cachedPicture)) }

    private fun sha256(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    private fun Map<String, Any?>.str(col: String): String? = when (val v = this[col]) {
        null -> null
        is Double -> if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()
        is Float -> if (v % 1f == 0f) v.toLong().toString() else v.toString()
        else -> v.toString()
    }

    private fun Map<String, Any?>.long(col: String): Long = when (val v = this[col]) {
        is Number -> v.toLong()
        is String -> v.trim().toLongOrNull() ?: 0
        else -> 0
    }

    /** A picture's web address, or null for an empty or odd one. */
    private fun Map<String, Any?>.url(col: String): String? =
        str(col)?.trim()?.takeIf { it.startsWith("https://") || it.startsWith("http://") }
}
