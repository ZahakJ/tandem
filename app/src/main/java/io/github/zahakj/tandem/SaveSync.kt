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
    private fun peanutsRoot(pkg: String) = "$DATA/$pkg/files/saves"
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
        if (game.platform.id != "switch" || (pkg != EDEN && !PeanutsNative.isPeanuts(pkg))) return null
        // The Peanuts app the game plays in: the one starting it, or the one that has built it.
        val peanuts = if (PeanutsNative.isPeanuts(pkg)) pkg else PeanutsNative.appFor(game)?.takeIf { PeanutsNative.of(game, it)?.state == "ready" } ?: return null
        val title = PeanutsNative.of(game, peanuts)?.titleId?.takeIf { it.length == 16 } ?: return null
        if (!Emulators.isInstalled(EDEN) || !Emulators.isInstalled(peanuts) || !Shell.ready()) return null
        // A game already open is brought back as it is; its save is the one in memory.
        if (RunningGames.list.any { it.game?.key == game.key }) return null
        // Starting in one Peanuts app: a newer save made in the other one (the official app and Peanuts Native+ each
        // keep their own) comes over first, so moving a game between them never loses play.
        val between = if (PeanutsNative.isPeanuts(pkg)) runCatching { fromOtherPeanuts(ctx, title, pkg) }
            .onFailure { Log.w("Tandem", "Save sync between Peanuts apps for $title failed", it) }.getOrNull() else null
        return runCatching { sync(ctx, title, peanutsRoot(peanuts), toPeanuts = PeanutsNative.isPeanuts(pkg)) ?: between }
            .onFailure { Log.w("Tandem", "Save sync for $title failed", it) }
            .getOrElse { Tx.s(R.string.play_save_failed, it.message.toString()) }
    }

    /**
     * Copies [title]'s save from the other Peanuts app into [pkg] when the other one's is newer. Both keep saves the
     * same way (saves/<title>, saves/<title>_device) under the same account, so it is a plain copy; the one replaced
     * is kept in the backups first.
     */
    private fun fromOtherPeanuts(ctx: Context, title: String, pkg: String): String? {
        val other = PeanutsNative.PACKAGES.firstOrNull { it != pkg && Emulators.isInstalled(it) } ?: return null
        val script = Shell.script(ctx, "save-sync.sh")
        fun sh(vararg args: String): String = Shell.su(script, *args) ?: error("root isn't available")
        fun age(dir: String) = sh("age", dir).trim().toLongOrNull() ?: 0L
        val stageRoot = File(ctx.cacheDir, "save-sync").apply { mkdirs() }
        var copied = false
        try {
            for (part in listOf(title, "${title}_device")) {
                val src = "${peanutsRoot(other)}/$part"
                val dst = "${peanutsRoot(pkg)}/$part"
                val srcAge = age(src)
                if (srcAge == 0L || srcAge - age(dst) < SAME_S) continue
                val stage = File(stageRoot, "$part-peanuts")
                sh("stage", src, stage.path, ctx.cacheDir.path)
                check(stage.isDirectory) { "couldn't read the save" }
                val said = sh("install", stage.path, dst, "$BACKUPS/$title/peanuts", "work")
                check("synced" in said) { said.trim().ifEmpty { "the copy failed" } }
                copied = true
            }
        } finally {
            stageRoot.listFiles()?.filter { it.name.startsWith(title) }?.forEach { it.deleteRecursively() }
        }
        if (!copied) return null
        Log.i("Tandem", "Save of $title brought from $other to $pkg")
        return Tx.s(R.string.play_save_from_native)
    }

    private fun sync(ctx: Context, title: String, peanutsRoot: String, toPeanuts: Boolean): String? {
        val script = Shell.script(ctx, "save-sync.sh")
        fun sh(vararg args: String): String = Shell.su(script, *args) ?: error("root isn't available")
        val users = sh("users", EDEN_ROOT, title).lines().map { it.trim() }.filter { it.length == 32 }
        val user = users.firstOrNull { it != DEVICE }
        val parts = when {
            user != null && DEVICE in users -> listOf(Part("$EDEN_ROOT/$user/$title", "$peanutsRoot/$title"), Part("$EDEN_ROOT/$DEVICE/$title", "$peanutsRoot/${title}_device"))
            user != null -> listOf(Part("$EDEN_ROOT/$user/$title", "$peanutsRoot/$title"))
            DEVICE in users -> listOf(Part("$EDEN_ROOT/$DEVICE/$title", "$peanutsRoot/$title"))
            // Eden has never saved this game: a Peanuts save goes to Eden's own user (Animal Crossing's to the device save).
            else -> {
                val own = sh("users", EDEN_ROOT, ".").lines().map { it.trim() }.firstOrNull { it.length == 32 && it != DEVICE } ?: return null
                listOf(Part("$EDEN_ROOT/${if (title == AcnhSave.TITLE) DEVICE else own}/$title", "$peanutsRoot/$title"))
            }
        }
        fun age(dir: String) = sh("age", dir).trim().toLongOrNull() ?: 0L
        val edenAge = parts.maxOf { age(it.eden) }
        val peanutsAge = parts.maxOf { age(it.peanuts) }
        val newer = if (toPeanuts) edenAge - peanutsAge else peanutsAge - edenAge
        if (newer < SAME_S || (if (toPeanuts) edenAge else peanutsAge) == 0L) return null

        val edenUser = user ?: sh("users", EDEN_ROOT, ".").lines().map { it.trim() }.firstOrNull { it.length == 32 && it != DEVICE }
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
        return Tx.s(if (toPeanuts) R.string.play_save_from_eden else R.string.play_save_from_native)
    }
}
