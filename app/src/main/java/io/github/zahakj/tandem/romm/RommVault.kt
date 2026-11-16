package io.github.zahakj.tandem.romm

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Versioned AES-GCM envelope, shared with JVM tests. The key never leaves AndroidKeyStore on the device. */
internal object RommCipher {
    private val aad = "tandem-romm-config-v1".toByteArray(Charsets.UTF_8)

    fun encrypt(plain: ByteArray, key: SecretKey): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        cipher.updateAAD(aad)
        check(cipher.iv.size == 12)
        return byteArrayOf(1) + cipher.iv + cipher.doFinal(plain)
    }

    fun decrypt(envelope: ByteArray, key: SecretKey): ByteArray {
        require(envelope.size >= 29 && envelope[0] == 1.toByte()) { "Invalid credential record" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, envelope.copyOfRange(1, 13)))
        cipher.updateAAD(aad)
        return cipher.doFinal(envelope, 13, envelope.size - 13)
    }
}

/** All methods are blocking: the settings owner calls them on Dispatchers.IO. Never fall back to plaintext. */
class RommVault(context: Context) {
    private val file = AtomicFile(File(context.applicationContext.noBackupFilesDir, "romm-config.enc"))
    private val json = Json { ignoreUnknownKeys = true }

    fun load(): RommConfig {
        if (!exists()) return RommConfig()
        val bytes = file.openRead().use { require(it.channel.size() <= 32 * 1024) { "Invalid credential record" }; it.readBytes() }
        val plain = RommCipher.decrypt(bytes, key(false))
        return try { json.decodeFromString<RommConfig>(plain.toString(Charsets.UTF_8)) } finally { plain.fill(0) }
    }

    fun save(config: RommConfig) {
        val plain = json.encodeToString(config).toByteArray(Charsets.UTF_8)
        val encrypted = try { RommCipher.encrypt(plain, key(true)) } finally { plain.fill(0) }
        val stream = file.startWrite()
        try {
            stream.write(encrypted)
            file.finishWrite(stream)
        } catch (e: Exception) {
            file.failWrite(stream)
            throw e
        }
        // AtomicFile can log a finalization failure rather than throw it. Never report an unpersisted token as saved.
        check(file.openRead().use { it.readBytes() }.contentEquals(encrypted)) { "Could not verify credential write" }
    }

    fun clear() {
        file.delete()
        check(!exists()) { "Could not remove credentials" }
        // Forget also recovers from an unavailable/invalidated key; the next save creates a fresh one.
        KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry(ALIAS) }
    }

    private fun exists() = file.baseFile.exists() || File(file.baseFile.path + ".bak").exists()

    private fun key(create: Boolean): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        check(create) { "Credential key unavailable" }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build())
        }.generateKey()
    }

    companion object { private const val ALIAS = "tandem.romm.config.v1" }
}
