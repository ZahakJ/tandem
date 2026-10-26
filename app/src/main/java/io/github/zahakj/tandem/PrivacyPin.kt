package io.github.zahakj.tandem

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.zahakj.tandem.data.BackupFiles
import io.github.zahakj.tandem.data.HomeStore
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.Executors
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/*
 * The private mode PIN. Only a salted PBKDF2 hash is kept, in `private-pin.json` in Android's no-backup folder: never
 * in Tandem's backups (BackupFiles only copies files from filesDir) or Android's. Wrong tries wait longer each time
 * (PinCode.waitAfter); the count survives a restart.
 *
 * A forgotten PIN can't be got round at once: Forgot PIN starts a [PinCode.RESET_WAIT_MS] wait, after which a new PIN
 * can be set. Everything stays hidden meanwhile, and the right PIN cancels the wait. Someone with the handheld for a
 * minute learns nothing; someone who forgot gets back in a day.
 *
 * [PinCode] is plain, for tests (PinCodeTest); [PinVault] keeps the record on the device.
 */

/** What is kept. [hash] empty: no PIN set. Times are wall-clock ms. */
@Serializable
data class PinRecord(
    val salt: String = "",
    val hash: String = "",
    val rounds: Int = 0,
    /** Wrong tries in a row. */
    val failures: Int = 0,
    /** No tries before this. */
    val lockedUntil: Long = 0,
    /** Forgot PIN was asked for at this time; 0 when not. */
    val resetAsked: Long = 0,
) {
    val isSet: Boolean get() = hash.isNotEmpty()
}

object PinCode {
    const val MIN = 4
    const val MAX = 6
    const val ROUNDS = 120_000
    /** Wrong tries allowed before the waits start. */
    const val FREE_TRIES = 3
    const val FIRST_WAIT_MS = 30_000L
    const val MAX_WAIT_MS = 30 * 60_000L
    const val RESET_WAIT_MS = 24 * 3_600_000L

    fun valid(pin: String): Boolean = pin.length in MIN..MAX && pin.all { it in '0'..'9' }

    fun hash(pin: String, salt: ByteArray, rounds: Int): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, rounds, 256)
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    /** A new record for [pin], with a fresh salt and no failures. */
    fun make(pin: String, rounds: Int = ROUNDS, random: SecureRandom = SecureRandom()): PinRecord {
        require(valid(pin)) { "A PIN is $MIN to $MAX digits" }
        val salt = ByteArray(16).also(random::nextBytes)
        return PinRecord(b64(salt), b64(hash(pin, salt, rounds)), rounds)
    }

    fun matches(rec: PinRecord, pin: String): Boolean {
        if (!rec.isSet || !valid(pin)) return false
        val want = runCatching { unb64(rec.hash) }.getOrNull() ?: return false
        val salt = runCatching { unb64(rec.salt) }.getOrNull() ?: return false
        return MessageDigest.isEqual(want, hash(pin, salt, rec.rounds))
    }

    /** How long to wait after the [failures]th wrong try in a row: nothing for the first few, then 30 s doubling to 30 min. */
    fun waitAfter(failures: Int): Long {
        if (failures < FREE_TRIES) return 0
        val steps = (failures - FREE_TRIES).coerceAtMost(10)
        return (FIRST_WAIT_MS shl steps).coerceAtMost(MAX_WAIT_MS)
    }

    /** How long until the next try may be made; 0 when now. A clock set back never makes the wait longer than it was. */
    fun waitLeft(rec: PinRecord, now: Long): Long =
        (rec.lockedUntil - now).coerceIn(0, waitAfter(rec.failures).coerceAtLeast(0))

    sealed interface Attempt {
        /** Right: [rec] has the failures cleared and any Forgot PIN wait called off. */
        data class Right(val rec: PinRecord) : Attempt
        /** Wrong: [rec] counts it, and the next try waits [waitMs]. */
        data class Wrong(val rec: PinRecord, val waitMs: Long) : Attempt
        /** Too soon after a wrong one: nothing was checked. */
        data class Wait(val waitMs: Long) : Attempt
        data object NotSet : Attempt
    }

    fun attempt(rec: PinRecord, pin: String, now: Long): Attempt {
        if (!rec.isSet) return Attempt.NotSet
        waitLeft(rec, now).let { if (it > 0) return Attempt.Wait(it) }
        if (matches(rec, pin)) return Attempt.Right(rec.copy(failures = 0, lockedUntil = 0, resetAsked = 0))
        val failures = rec.failures + 1
        val wait = waitAfter(failures)
        return Attempt.Wrong(rec.copy(failures = failures, lockedUntil = if (wait > 0) now + wait else 0), wait)
    }

    /** Forgot PIN: starts the wait, or keeps the one already running. */
    fun askReset(rec: PinRecord, now: Long): PinRecord = if (rec.resetAsked > 0) rec else rec.copy(resetAsked = now)

    /** How long until a forgotten PIN may be replaced; null when no reset was asked for. */
    fun resetLeft(rec: PinRecord, now: Long): Long? {
        if (rec.resetAsked <= 0) return null
        // A clock set back counts the wait from now.
        val since = (now - rec.resetAsked).coerceAtLeast(0)
        return (RESET_WAIT_MS - since).coerceAtLeast(0)
    }

    /** The wait is over: the PIN is gone and a new one has to be set. */
    fun resetDue(rec: PinRecord, now: Long): Boolean = rec.isSet && resetLeft(rec, now) == 0L

    /** "25 s", "4 min", "23 h", for a wait. Rounded up, so it never says 0 while there is still one. */
    fun say(ms: Long): String {
        val s = (ms + 999) / 1000
        return when {
            s < 60 -> "$s s"
            s < 3600 -> "${(s + 59) / 60} min"
            else -> "${(s + 3599) / 3600} h"
        }
    }

    private fun b64(b: ByteArray): String = Base64.getEncoder().encodeToString(b)
    private fun unb64(s: String): ByteArray = Base64.getDecoder().decode(s)
}

/** The record on the device, as Compose state. Written atomically on a background thread. */
object PinVault {
    const val FILE = "private-pin.json"

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private val writer = Executors.newSingleThreadExecutor()
    private var file: File? = null

    var record by mutableStateOf(PinRecord())
        private set

    val isSet: Boolean get() = record.isSet

    fun init(ctx: Context) {
        if (file != null) return
        val f = File(ctx.applicationContext.noBackupFilesDir, FILE)
        file = f
        record = runCatching { json.decodeFromString<PinRecord>(f.readText()) }.getOrDefault(PinRecord())
    }

    fun save(rec: PinRecord) {
        record = rec
        val f = file ?: return
        val text = json.encodeToString(rec)
        writer.execute { runCatching { if (rec.isSet || rec.failures > 0) BackupFiles.atomicWrite(f, text) else f.delete() } }
    }

    /** Forgets the PIN (a Forgot PIN wait that ran out). */
    fun clear() = save(PinRecord())
}

/**
 * Private mode on and off, and revealing hidden games, behind the PIN (ui/PinPad.kt). Hiding them again never asks.
 */
object PrivateMode {
    /**
     * Settings' switch, and L1 + R1 + Select while private mode is off (a quick panic switch): on at once, after
     * choosing a PIN when there is none yet.
     */
    fun turnOn() {
        if (Privacy.enabled) return
        if (!PinVault.isSet) { io.github.zahakj.tandem.ui.PinPad.create("Choose a PIN for private mode") { switchOn() }; return }
        switchOn()
    }

    private fun switchOn() {
        HomeStore.updateSettings { it.copy(privateMode = true) }
        Privacy.revealed = false
        HiddenItems.endPeeks()
        // Nothing more goes online (Offline.kt): a scrape under way stops here.
        io.github.zahakj.tandem.data.Scraper.cancel()
        io.github.zahakj.tandem.ui.Sounds.toggle(true)
        io.github.zahakj.tandem.ui.Toasts.show("Private mode on. L1 + R1 + Select and your PIN show hidden games")
        io.github.zahakj.tandem.ui.Nav.focusTick++
    }

    fun turnOff() {
        if (!Privacy.enabled) return
        io.github.zahakj.tandem.ui.PinPad.check("Turn off private mode") {
            HomeStore.updateSettings { it.copy(privateMode = false) }
            Privacy.revealed = false
            HiddenItems.endPeeks()
            io.github.zahakj.tandem.ui.Sounds.toggle(false)
            io.github.zahakj.tandem.ui.Toasts.show("Private mode off")
            io.github.zahakj.tandem.ui.Nav.focusTick++
        }
    }

    /** Brings hidden games out in private mode, once the PIN is right. */
    fun reveal() {
        if (!Privacy.enabled || Privacy.revealed) return
        io.github.zahakj.tandem.ui.PinPad.check("Show hidden games") {
            Privacy.revealed = true
            io.github.zahakj.tandem.ui.Toasts.show("Psst… hidden games are showing")
            io.github.zahakj.tandem.ui.Nav.focusTick++
        }
    }

    /** Settings › Private mode PIN: the current PIN first, then the new one twice. */
    fun changePin() {
        val pad = io.github.zahakj.tandem.ui.PinPad
        if (!PinVault.isSet) { pad.create("Choose a PIN for private mode") {}; return }
        pad.check("Enter your current PIN") { pad.create("Choose a new PIN") {} }
    }
}
