package io.github.zahakj.tandem

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import java.io.File

/**
 * GameNative from Tandem's side: whether it is there, and its games out of a copy of its database
 * ([GameNativeLibrary]). [AppLibraries] makes the copy as root, alongside DroidDeck's library, and deletes it after.
 */
object GameNative {
    const val PACKAGE = GameNativeLibrary.PACKAGE
    private const val TAG = "Tandem"

    fun installed(ctx: Context): Boolean = runCatching { ctx.packageManager.getPackageInfo(PACKAGE, 0) }.isSuccess

    /**
     * The installed games in [db], a copy of GameNative's pluvia.db (with its -wal and -shm beside it), opened
     * read-only; null when it can't be read (a copy caught mid-write, say: the last list stays).
     */
    fun read(db: File): List<AppGame>? {
        fun attempt(): List<AppGame> =
            SQLiteDatabase.openDatabase(db.path, null, SQLiteDatabase.OPEN_READONLY).use { sql ->
                GameNativeLibrary.read { q -> runCatching { sql.rawQuery(q, null).use(::rows) }.getOrNull() }
                    ?: throw IllegalStateException("No tables")
            }
        return try {
            attempt()
        } catch (e: Exception) {
            // A stale -shm (the index of the -wal) can stop SQLite opening the copy; it rebuilds one without it.
            Log.w(TAG, "Reading GameNative's library failed, trying again without its -shm", e)
            File(db.path + "-shm").delete()
            runCatching { attempt() }.onFailure { Log.w(TAG, "Reading GameNative's library failed", it) }.getOrNull()
        }
    }

    private fun rows(c: Cursor): List<Map<String, Any?>> {
        val out = ArrayList<Map<String, Any?>>(c.count.coerceAtLeast(0))
        val names = c.columnNames
        while (c.moveToNext()) {
            out += names.indices.associate { i ->
                names[i] to when (c.getType(i)) {
                    Cursor.FIELD_TYPE_INTEGER -> c.getLong(i)
                    Cursor.FIELD_TYPE_FLOAT -> c.getDouble(i)
                    Cursor.FIELD_TYPE_STRING -> c.getString(i)
                    else -> null
                }
            }
        }
        return out
    }
}
