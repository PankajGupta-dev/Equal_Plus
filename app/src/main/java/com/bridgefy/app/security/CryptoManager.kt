package com.bridgefy.app.security

import android.util.Base64
import android.util.Log
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Handles AES-256-GCM encryption and decryption for mesh message payloads.
 *
 * Every message is encrypted before leaving the device and decrypted only
 * when delivered to the target. Relay nodes forward encrypted payloads
 * without being able to read the content.
 *
 * Uses:
 * - AES-256-GCM for authenticated encryption (confidentiality + integrity)
 * - 12-byte random IV per message (never reused)
 * - 128-bit authentication tag
 *
 * The encrypted output format is: IV (12 bytes) + ciphertext + tag (appended by GCM)
 * encoded as Base64 for safe JSON transmission.
 */
object CryptoManager {

    private const val TAG = "CryptoManager"
    private const val ALGORITHM = "AES/GCM/NoPadding"
    private const val GCM_IV_LENGTH = 12
    private const val GCM_TAG_LENGTH = 128 // bits

    /**
     * Encrypt plaintext bytes using AES-256-GCM.
     *
     * @param plaintext The raw bytes to encrypt
     * @param key The AES secret key (must be 256-bit)
     * @return Base64-encoded string containing IV + ciphertext + GCM tag,
     *         or null if encryption fails
     */
    fun encrypt(plaintext: ByteArray, key: SecretKey): String? {
        return try {
            val cipher = Cipher.getInstance(ALGORITHM)
            cipher.init(Cipher.ENCRYPT_MODE, key)

            val iv = cipher.iv  // GCM generates a random IV automatically
            val ciphertext = cipher.doFinal(plaintext)

            // Prepend IV to ciphertext for transmission
            val combined = ByteArray(iv.size + ciphertext.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(ciphertext, 0, combined, iv.size, ciphertext.size)

            Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e(TAG, "Encryption failed", e)
            null
        }
    }

    /**
     * Encrypt a string payload.
     */
    fun encryptString(plaintext: String, key: SecretKey): String? {
        return encrypt(plaintext.toByteArray(Charsets.UTF_8), key)
    }

    /**
     * Decrypt a Base64-encoded AES-256-GCM ciphertext.
     *
     * @param encryptedBase64 Base64 string containing IV + ciphertext + tag
     * @param key The AES secret key used for encryption
     * @return Decrypted bytes, or null if decryption fails (wrong key, tampered data, etc.)
     */
    fun decrypt(encryptedBase64: String, key: SecretKey): ByteArray? {
        return try {
            val combined = Base64.decode(encryptedBase64, Base64.NO_WRAP)

            if (combined.size < GCM_IV_LENGTH) {
                Log.e(TAG, "Encrypted data too short")
                return null
            }

            // Extract IV from the first 12 bytes
            val iv = combined.copyOfRange(0, GCM_IV_LENGTH)
            val ciphertext = combined.copyOfRange(GCM_IV_LENGTH, combined.size)

            val cipher = Cipher.getInstance(ALGORITHM)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, key, spec)

            cipher.doFinal(ciphertext)
        } catch (e: Exception) {
            Log.e(TAG, "Decryption failed", e)
            null
        }
    }

    /**
     * Decrypt to a string payload.
     */
    fun decryptString(encryptedBase64: String, key: SecretKey): String? {
        return decrypt(encryptedBase64, key)?.toString(Charsets.UTF_8)
    }
}
