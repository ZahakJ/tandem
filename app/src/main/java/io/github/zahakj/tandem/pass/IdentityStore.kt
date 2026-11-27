package io.github.zahakj.tandem.pass

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Keeps Tandem Pass's identity seed (PassIdentity) on this device only.
 *
 * The seed is made with SecureRandom the first time it is needed and kept in `pass-identity.bin` in the no-backup
 * folder (never in Android's backups, Tandem's backups or a shared look), sealed with AES-GCM by a key that lives in
 * the Android keystore and can't be taken out of it. Android 11 can't keep an Ed25519 key in its keystore, and Concord
 * needs the seed itself (it derives its other keys from it), so the seed is what is kept, wrapped, rather than a
 * keystore key. Should the keystore be unusable (some handhelds' firmware), the seed is kept unwrapped in the same
 * private folder, which only Tandem can read, and the file says so.
 *
 * File: `"TPID" ‖ version 1 ‖ mode (1 sealed, 0 plain) ‖ [12-byte IV ‖ ciphertext with tag] or [32-byte seed]`.
 */
object IdentityStore {
    private const val FILE = "pass-identity.bin"
    private const val ALIAS = "tandem-pass-identity"
    private val MAGIC = byteArrayOf('T'.code.toByte(), 'P'.code.toByte(), 'I'.code.toByte(), 'D'.code.toByte())

    @Volatile private var cached: PassIdentity? = null

    /**
     * The identity, made on first use. Blocking (the keystore and the first signature): call it off the main thread.
     * Null while an existing file can't be read (a keystore not ready yet after boot): it is kept as it is and tried
     * again next time, never replaced, so nobody you met sees you as someone new.
     */
    @Synchronized
    fun get(ctx: Context): PassIdentity? {
        cached?.let { return it }
        val f = File(ctx.noBackupFilesDir, FILE)
        val seed = if (f.isFile) {
            runCatching { read(f.readBytes()) }
                .onFailure { android.util.Log.w("Tandem", "Tandem Pass identity unreadable for now: ${it.javaClass.simpleName}") }
                .getOrNull() ?: return null
        } else ByteArray(Ed25519.SEED_BYTES).also { SecureRandom().nextBytes(it); write(f, it) }
        return PassIdentity(seed).also { cached = it }
    }

    /** True once an identity exists on this device. */
    fun exists(ctx: Context): Boolean = File(ctx.noBackupFilesDir, FILE).isFile

    private fun read(b: ByteArray): ByteArray? {
        if (b.size < 6 || !b.copyOfRange(0, 4).contentEquals(MAGIC) || b[4].toInt() != 1) return null
        return when (b[5].toInt()) {
            0 -> b.copyOfRange(6, b.size).takeIf { it.size == Ed25519.SEED_BYTES }
            1 -> {
                val iv = b.copyOfRange(6, 18)
                val c = Cipher.getInstance("AES/GCM/NoPadding")
                c.init(Cipher.DECRYPT_MODE, key() ?: return null, GCMParameterSpec(128, iv))
                c.doFinal(b.copyOfRange(18, b.size)).takeIf { it.size == Ed25519.SEED_BYTES }
            }
            else -> null
        }
    }

    private fun write(f: File, seed: ByteArray) {
        val sealed = runCatching {
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.ENCRYPT_MODE, key() ?: error("no keystore"))
            MAGIC + byteArrayOf(1, 1) + c.iv + c.doFinal(seed)
        }.onFailure { android.util.Log.w("Tandem", "Keystore unavailable, Tandem Pass identity kept in the private folder") }.getOrNull()
        val bytes = sealed ?: (MAGIC + byteArrayOf(1, 0) + seed)
        val tmp = File(f.parentFile, "$FILE.tmp")
        tmp.writeBytes(bytes)
        tmp.renameTo(f)
    }

    private fun key(): SecretKey? = runCatching {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(
                KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
        }.generateKey()
    }.getOrNull()
}
