package com.msahil432.multitool.util

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Utility for hashing and verifying passwords using PBKDF2 with SHA-256 and secure random salts.
 * Plaintext passwords are never persisted or logged.
 */
object PasswordSecurity {

    private const val SALT_LENGTH_BYTES = 16
    private const val ITERATIONS = 10_000
    private const val KEY_LENGTH_BITS = 256
    private const val ALGORITHM = "PBKDF2WithHmacSHA256"

    /**
     * Generates a salted hash for the given plaintext [password].
     * Returns a string formatted as "<saltHex>:<hashHex>".
     */
    fun hashPassword(password: String): String {
        val random = SecureRandom()
        val salt = ByteArray(SALT_LENGTH_BYTES)
        random.nextBytes(salt)

        val hash = deriveHash(password, salt)
        val saltHex = salt.joinToString("") { "%02x".format(it) }
        val hashHex = hash.joinToString("") { "%02x".format(it) }
        return "$saltHex:$hashHex"
    }

    /**
     * Verifies whether [password] matches the stored [storedSaltedHash].
     * Performs constant-time comparison to protect against timing attacks.
     */
    fun verifyPassword(password: String, storedSaltedHash: String): Boolean {
        if (storedSaltedHash.isBlank()) return false
        val parts = storedSaltedHash.split(":")
        if (parts.size != 2) return false

        val saltHex = parts[0]
        val expectedHashHex = parts[1]

        val salt = hexToByteArray(saltHex) ?: return false
        val computedHash = deriveHash(password, salt)
        val computedHashHex = computedHash.joinToString("") { "%02x".format(it) }

        return MessageDigest.isEqual(
            computedHashHex.toByteArray(Charsets.UTF_8),
            expectedHashHex.toByteArray(Charsets.UTF_8)
        )
    }

    private fun deriveHash(password: String, salt: ByteArray): ByteArray {
        return try {
            val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH_BITS)
            val factory = SecretKeyFactory.getInstance(ALGORITHM)
            factory.generateSecret(spec).encoded
        } catch (_: Exception) {
            // Fallback to SHA-256 with salt if PBKDF2 factory is unavailable
            val digest = MessageDigest.getInstance("SHA-256")
            digest.update(salt)
            digest.digest(password.toByteArray(Charsets.UTF_8))
        }
    }

    private fun hexToByteArray(hex: String): ByteArray? {
        if (hex.length % 2 != 0) return null
        return try {
            ByteArray(hex.length / 2) { i ->
                hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
            }
        } catch (_: Exception) {
            null
        }
    }
}
