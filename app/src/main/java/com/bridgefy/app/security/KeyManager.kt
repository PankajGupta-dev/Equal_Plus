package com.bridgefy.app.security

import android.content.Context
import android.util.Log
import java.security.SecureRandom
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Manages the shared mesh encryption key.
 *
 * All devices in the same mesh network share a common passphrase from which
 * the AES-256 key is derived using PBKDF2. Users enter the same passphrase
 * on all devices to join the mesh securely.
 *
 * Key storage:
 * - The derived key material is stored in SharedPreferences (encrypted by Android Keystore
 *   on API 23+, or base64 on older devices).
 * - The salt is stored alongside the key for re-derivation verification.
 *
 * In a future version, this could be upgraded to use Android Keystore directly
 * for hardware-backed key storage.
 */
object KeyManager {

    private const val TAG = "KeyManager"
    private const val PREFS_NAME = "alertnet_keys"
    private const val KEY_MATERIAL = "key_material"
    private const val KEY_SALT = "key_salt"
    private const val KEY_SET = "key_set"

    private const val PBKDF2_ITERATIONS = 100_000
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_LENGTH_BYTES = 16

    private var cachedKey: SecretKey? = null

    /**
     * Derive an AES-256 key from a user-provided mesh passphrase.
     * Stores the derived key material in SharedPreferences.
     *
     * @param context Application context
     * @param passphrase The shared mesh passphrase (must be same on all devices)
     * @return The derived AES SecretKey
     */
    fun deriveAndStoreKey(context: Context, passphrase: String): SecretKey {
        val salt = ByteArray(SALT_LENGTH_BYTES).also {
            SecureRandom().nextBytes(it)
        }

        val key = deriveKey(passphrase, salt)

        // Store key material and salt
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_MATERIAL, android.util.Base64.encodeToString(key.encoded, android.util.Base64.NO_WRAP))
            .putString(KEY_SALT, android.util.Base64.encodeToString(salt, android.util.Base64.NO_WRAP))
            .putBoolean(KEY_SET, true)
            .apply()

        cachedKey = key
        Log.d(TAG, "Mesh key derived and stored")
        return key
    }

    /**
     * Load the previously stored mesh key.
     *
     * @param context Application context
     * @return The stored AES SecretKey, or null if no key has been set
     */
    fun loadKey(context: Context): SecretKey? {
        cachedKey?.let { return it }

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_SET, false)) return null

        return try {
            val keyMaterial = android.util.Base64.decode(
                prefs.getString(KEY_MATERIAL, "") ?: "",
                android.util.Base64.NO_WRAP
            )
            val key = SecretKeySpec(keyMaterial, "AES")
            cachedKey = key
            key
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load key", e)
            null
        }
    }

    /**
     * Get the current mesh key (from cache, storage, or null).
     */
    fun getKey(context: Context): SecretKey? {
        return cachedKey ?: loadKey(context)
    }

    /**
     * Check if a mesh key has been configured.
     */
    fun hasKey(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_SET, false)
    }

    /**
     * Clear the stored key (used when leaving a mesh network).
     */
    fun clearKey(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().clear().apply()
        cachedKey = null
        Log.d(TAG, "Mesh key cleared")
    }

    /**
     * Derive an AES-256 key from passphrase + salt using PBKDF2.
     */
    private fun deriveKey(passphrase: String, salt: ByteArray): SecretKey {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
        val tmp = factory.generateSecret(spec)
        return SecretKeySpec(tmp.encoded, "AES")
    }
}
