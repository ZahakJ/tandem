package io.github.zahakj.tandem

import android.content.Context
import android.util.Log
import java.io.File

/**
 * One save for a Switch game, whether it plays in Eden Duo or natively in Peanuts Native. Right before either starts
 * a game the other also has, the newer of the two saves is copied over the one about to be played, so playing on
 * either always carries on from where you last were. The save it replaces is kept first, the newest 20 per game and
 * app, in ROMs/tandem/save-sync/backups/<title>/<app>.
 *
 * The two keep saves differently: Eden in its user folders (a game's device save in the all-zero user), Peanuts in
 * saves/<title> (and saves/<title>_device for a game with both kinds). Copying needs root (both are other apps'
 * folders, and a save must keep its owner and SELinux label), so this runs only when Shizuku can reach `su`. Both
 * saves keep their file times, so once copied they read as the same age and nothing is copied back. Animal Crossing
 * holds its owner's account inside the save, and the two apps have different accounts: its copy gets the other
 * account put in ([AcnhSave]), or isn't copied at all.
 */
object SaveSync {
    const val EDEN = "io.github.zahakj.edenduo"
    private const val DATA = "/data/media/0/Android/data"
    private const val EDEN_ROOT = "$DATA/$EDEN/files/nand/user/save/0000000000000000"
    private const val PEANUTS_ROOT = "$DATA/${PeanutsNative.PKG}/files/saves"
    private const val BACKUPS = "/data/media/0/ROMs/tandem/save-sync/backups"
    /** Eden's device saves sit in the all-zero user. */
    private const val DEVICE = "00000000000000000000000000000000"
    /** The one account Peanuts Native gives every game (its hle_account.c). */
    const val PEANUTS_USER = "53574E415449564578626C0000000001"
    /** Saves whose times differ by less than this are the same save. */
    private const val SAME_S = 2

    private data class Part(val eden: String, val peanuts: String)

    /**
     * Brings [game]'s save up to date in [pkg] (Eden Duo or Peanuts) before it starts. Blocking (shell calls and a
     * copy): call off the main thread. Returns what it did for the user to see, or null when nothing changed.
     */
    fun beforeLaunch(ctx: Context, game: Game, pkg: String): String? {
        if (game.platform.id != "switch" || (pkg != EDEN && pkg != PeanutsNative.PKG)) return null
        val title = PeanutsNative.of(game)?.titleId?.takeIf { it.length == 16 } ?: return null
        if (!Emulators.isInstalled(EDEN) || !PeanutsNative.installed() || !Shell.ready()) return null
        // A game already open is brought back as it is; its save is the one in memory.
        if (RunningGames.list.any { it.game?.key == game.key }) return null
        return runCatching { sync(ctx, title, toPeanuts = pkg == PeanutsNative.PKG) }
            .onFailure { Log.w("Tandem", "Save sync for $title failed", it) }
            .getOrElse { "Couldn't bring the save across: ${it.message}. Both saves are as they were" }
    }

    private fun sync(ctx: Context, title: String, toPeanuts: Boolean): String? {
        val script = script(ctx)
        fun sh(vararg args: String): String = Shell.run("su", "-c", (listOf("sh", script) + args).joinToString(" ") { "'$it'" })
            ?: error("root isn't available")
        val users = sh("users", EDEN_ROOT, title).lines().map { it.trim() }.filter { it.length == 32 }
        val user = users.firstOrNull { it != DEVICE }
        val parts = when {
            user != null && DEVICE in users -> listOf(Part("$EDEN_ROOT/$user/$title", "$PEANUTS_ROOT/$title"), Part("$EDEN_ROOT/$DEVICE/$title", "$PEANUTS_ROOT/${title}_device"))
            user != null -> listOf(Part("$EDEN_ROOT/$user/$title", "$PEANUTS_ROOT/$title"))
            DEVICE in users -> listOf(Part("$EDEN_ROOT/$DEVICE/$title", "$PEANUTS_ROOT/$title"))
            // Eden has never saved this game: a Peanuts save goes to Eden's own user (Animal Crossing's to the device save).
            else -> {
                val own = sh("users", EDEN_ROOT, "..").lines().map { it.trim() }.firstOrNull { it.length == 32 && it != DEVICE } ?: return null
                listOf(Part("$EDEN_ROOT/${if (title == AcnhSave.TITLE) DEVICE else own}/$title", "$PEANUTS_ROOT/$title"))
            }
        }
        fun age(dir: String) = sh("age", dir).trim().toLongOrNull() ?: 0L
        val edenAge = parts.maxOf { age(it.eden) }
        val peanutsAge = parts.maxOf { age(it.peanuts) }
        val newer = if (toPeanuts) edenAge - peanutsAge else peanutsAge - edenAge
        if (newer < SAME_S || (if (toPeanuts) edenAge else peanutsAge) == 0L) return null

        val edenUser = user ?: sh("users", EDEN_ROOT, "..").lines().map { it.trim() }.firstOrNull { it.length == 32 && it != DEVICE }
        val stageRoot = File(ctx.cacheDir, "save-sync").apply { mkdirs() }
        try {
            // Every part is staged (and its account swapped) before anything is written, so a refusal changes nothing.
            val staged = parts.mapIndexed { i, p ->
                val stage = File(stageRoot, "$title-$i")
                val src = if (toPeanuts) p.eden else p.peanuts
                if (age(src) == 0L) return@mapIndexed null
                sh("stage", src, stage.path, ctx.cacheDir.path)
                check(stage.isDirectory) { "couldn't read the save" }
                if (title == AcnhSave.TITLE && File(stage, "main.dat").isFile) {
                    val eden = edenUser ?: error("Eden has no user")
                    if (toPeanuts) AcnhSave.swapUser(stage, eden, PEANUTS_USER) else AcnhSave.swapUser(stage, PEANUTS_USER, eden)
                }
                p to stage
            }.filterNotNull()
            for ((p, stage) in staged) {
                val dst = if (toPeanuts) p.peanuts else p.eden
                val app = if (toPeanuts) "peanuts" else "eden"
                val said = sh("install", stage.path, dst, "$BACKUPS/$title/$app", if (toPeanuts) "work" else "-")
                check("synced" in said) { said.trim().ifEmpty { "the copy failed" } }
            }
        } finally {
            stageRoot.listFiles()?.filter { it.name.startsWith(title) }?.forEach { it.deleteRecursively() }
        }
        Log.i("Tandem", "Save of $title brought ${if (toPeanuts) "from Eden Duo to Peanuts" else "from Peanuts to Eden Duo"}")
        return if (toPeanuts) "Carried on from your Eden Duo save" else "Carried on from your native save"
    }

    /** The shell script, copied out of the APK where root can read it (again whenever the app is updated). */
    private fun script(ctx: Context): String {
        val f = File(ctx.filesDir, "save-sync.sh")
        val wanted = ctx.assets.open("save-sync.sh").use { it.readBytes() }
        if (!f.isFile || !f.readBytes().contentEquals(wanted)) f.writeBytes(wanted)
        return f.path
    }
}
