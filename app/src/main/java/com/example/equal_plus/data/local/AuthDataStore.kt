package com.example.equal_plus.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.authDataStore by preferencesDataStore(name = "equal_plus_auth_prefs")

/**
 * DataStore holding Auth0 session state and the is_verified flag.
 * Deliberately separate from PolicyDataStore to avoid cross-concern coupling.
 */
class AuthDataStore(private val context: Context) {

    private val dataStore = context.authDataStore

    companion object {
        val KEY_IS_VERIFIED    = booleanPreferencesKey("key_phone_verified")
        val KEY_VERIFIED_PHONE = stringPreferencesKey("key_verified_phone")
        val KEY_AUTH0_TOKEN    = stringPreferencesKey("key_auth0_access_token")
    }

    /** Emits true once the user has successfully verified their phone via OTP. */
    val isVerified: Flow<Boolean> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { it[KEY_IS_VERIFIED] ?: false }

    val verifiedPhone: Flow<String?> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { it[KEY_VERIFIED_PHONE] }

    suspend fun setVerified(phone: String, token: String) {
        dataStore.edit { prefs ->
            prefs[KEY_IS_VERIFIED]    = true
            prefs[KEY_VERIFIED_PHONE] = phone
            prefs[KEY_AUTH0_TOKEN]    = token
        }
    }

    suspend fun clearVerification() {
        dataStore.edit { prefs ->
            prefs.remove(KEY_IS_VERIFIED)
            prefs.remove(KEY_VERIFIED_PHONE)
            prefs.remove(KEY_AUTH0_TOKEN)
        }
    }
}
