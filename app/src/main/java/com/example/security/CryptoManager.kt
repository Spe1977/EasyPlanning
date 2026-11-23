package com.example.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.annotation.VisibleForTesting
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Cryptographic engine for EasyPlanning:
 * - Authenticated backup encryption: AES-256-GCM + PBKDF2WithHmacSHA256 (210,000 iterations)
 * - Password & PIN salted verifier hashing with constant-time comparison
 * - Hardware-backed Android Keystore AES-256-GCM vault for local sensitive preferences
 */
object CryptoManager {

    private val MAGIC_HEADER = byteArrayOf('E'.code.toByte(), 'P'.code.toByte(), 'B'.code.toByte(), 'K'.code.toByte())
    private const val FORMAT_VERSION: Byte = 1
    private const val SALT_LEN = 16
    private const val IV_LEN = 12
    private const val GCM_TAG_BITS = 128
    private const val PBKDF2_ITERATIONS = 210_000
    private const val KEY_BITS = 256
    private const val KEYSTORE_ALIAS = "easyplanning_local_master_key_v1"

    private val secureRandom = SecureRandom()

    @Volatile
    private var cachedKeystoreKey: SecretKey? = null

    @VisibleForTesting
    @Volatile
    internal var keyProviderOverride: (() -> SecretKey)? = null

    @VisibleForTesting
    internal fun resetKeystoreCacheForTesting() {
        cachedKeystoreKey = null
    }

    fun encryptBackup(plaintext: ByteArray, password: CharArray): ByteArray {
        return try {
            val salt = ByteArray(SALT_LEN).also { secureRandom.nextBytes(it) }
            val iv = ByteArray(IV_LEN).also { secureRandom.nextBytes(it) }
            val key = deriveAesKey(password, salt)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
            // Authenticate header + version + salt as AAD to prevent header tampering
            cipher.updateAAD(MAGIC_HEADER)
            cipher.updateAAD(byteArrayOf(FORMAT_VERSION))
            cipher.updateAAD(salt)
            val ciphertext = cipher.doFinal(plaintext)

            ByteArrayOutputStream(MAGIC_HEADER.size + 1 + SALT_LEN + IV_LEN + ciphertext.size).apply {
                write(MAGIC_HEADER)
                write(FORMAT_VERSION.toInt())
                write(salt)
                write(iv)
                write(ciphertext)
            }.toByteArray()
        } finally {
            password.fill('\u0000')
        }
    }

    fun decryptBackup(encryptedBlob: ByteArray, password: CharArray): ByteArray {
        return try {
            val minSize = MAGIC_HEADER.size + 1 + SALT_LEN + IV_LEN + (GCM_TAG_BITS / 8)
            require(encryptedBlob.size >= minSize) { "Invalid backup file size" }

            val input = ByteArrayInputStream(encryptedBlob)
            val header = ByteArray(MAGIC_HEADER.size)
            input.read(header)
            require(header.contentEquals(MAGIC_HEADER)) { "Unrecognized backup format" }

            val version = input.read().toByte()
            require(version == FORMAT_VERSION) { "Unsupported backup version: $version" }

            val salt = ByteArray(SALT_LEN).also { input.read(it) }
            val iv = ByteArray(IV_LEN).also { input.read(it) }
            val ciphertext = input.readBytes()

            val key = deriveAesKey(password, salt)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
            cipher.updateAAD(MAGIC_HEADER)
            cipher.updateAAD(byteArrayOf(version))
            cipher.updateAAD(salt)
            cipher.doFinal(ciphertext)
        } finally {
            password.fill('\u0000')
        }
    }

    /**
     * Generates a salted PBKDF2-HMAC-SHA256 verifier string ("v1:<b64Salt>:<b64Hash>")
     * then wraps it with Android Keystore AES-256-GCM before storing in DataStore.
     */
    fun createProtectedVerifier(secret: CharArray): String {
        return try {
            val salt = ByteArray(SALT_LEN).also { secureRandom.nextBytes(it) }
            val hash = pbkdf2(secret, salt)
            val raw = "v1:${Base64.encodeToString(salt, Base64.NO_WRAP)}:${Base64.encodeToString(hash, Base64.NO_WRAP)}"
            encryptWithKeystore(raw)
        } finally {
            secret.fill('\u0000')
        }
    }

    fun verifyProtectedSecret(candidate: CharArray, storedProtectedVerifier: String): Boolean {
        return try {
            val raw = decryptWithKeystore(storedProtectedVerifier)
            val parts = raw.split(":")
            if (parts.size != 3 || parts[0] != "v1") return false
            val salt = Base64.decode(parts[1], Base64.NO_WRAP)
            val expectedHash = Base64.decode(parts[2], Base64.NO_WRAP)
            val actualHash = pbkdf2(candidate, salt)
            MessageDigest.isEqual(expectedHash, actualHash)
        } catch (_: Exception) {
            false
        } finally {
            candidate.fill('\u0000')
        }
    }

    fun encryptWithKeystore(plaintext: String): String {
        val key = getOrCreateKeystoreKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val combined = ByteArray(1 + iv.size + ciphertext.size)
        combined[0] = iv.size.toByte()
        System.arraycopy(iv, 0, combined, 1, iv.size)
        System.arraycopy(ciphertext, 0, combined, 1 + iv.size, ciphertext.size)
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    fun decryptWithKeystore(encoded: String): String {
        val combined = Base64.decode(encoded, Base64.NO_WRAP)
        val gcmTagBytes = GCM_TAG_BITS / 8
        require(combined.size > 1 + gcmTagBytes) { "Corrupted keystore payload" }
        val ivLen = combined[0].toInt() and 0xFF
        require(ivLen in 1..(combined.size - 1 - gcmTagBytes)) { "Invalid keystore IV length" }
        val iv = combined.copyOfRange(1, 1 + ivLen)
        val ciphertext = combined.copyOfRange(1 + ivLen, combined.size)
        val key = getOrCreateKeystoreKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }

    private fun deriveAesKey(password: CharArray, salt: ByteArray): SecretKeySpec {
        val keyBytes = pbkdf2(password, salt)
        return SecretKeySpec(keyBytes, "AES")
    }

    private fun pbkdf2(password: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password, salt, PBKDF2_ITERATIONS, KEY_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return try {
            factory.generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    @Synchronized
    private fun getOrCreateKeystoreKey(): SecretKey {
        keyProviderOverride?.let { return it() }
        cachedKeystoreKey?.let { return it }
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = keyStore.getKey(KEYSTORE_ALIAS, null) as? SecretKey
        if (existing != null) {
            cachedKeystoreKey = existing
            return existing
        }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        val spec = KeyGenParameterSpec.Builder(
            KEYSTORE_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(KEY_BITS)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey().also { cachedKeystoreKey = it }
    }
}
