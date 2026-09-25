package com.bridgefy.app.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

// Single DataStore instance at the module level (Android best practice)
private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "alertnet_settings")

/**
 * DataStore-backed settings repository for location & privacy preferences.
 *
 * Provides both synchronous read properties (for service-level checks)
 * and reactive flows (for Compose UI bindings).
 */
class SettingsRepository(
    private val context: Context,
    val selfDeviceId: String
) {
    companion object {
        private val MESH_LOCATION_BROADCAST = booleanPreferencesKey("mesh_location_broadcast")
        private val LOCAL_MAP_LOCATION = booleanPreferencesKey("local_map_location")
        private val HAS_SHOWN_LOCATION_CONSENT = booleanPreferencesKey("has_shown_location_consent")
    }

    private val dataStore get() = context.settingsDataStore

    // ─── Synchronous reads (for service-level checks) ────────────

    /**
     * Whether the user has opted in to broadcasting their location to the mesh.
     * Default: FALSE — opt-in only. Transmitting location without consent is a privacy violation.
     */
    val isMeshLocationBroadcastEnabled: Boolean
        get() = runBlocking {
            dataStore.data.first()[MESH_LOCATION_BROADCAST] ?: false
        }

    /**
     * Whether the user's own GPS location is shown on their local map.
     * Default: TRUE — local-only, never transmitted.
     */
    val isLocalMapLocationEnabled: Boolean
        get() = runBlocking {
            dataStore.data.first()[LOCAL_MAP_LOCATION] ?: true
        }

    /**
     * Whether the location consent dialog has been shown before.
     * Default: FALSE — show once on first map visit, then never again.
     */
    val hasShownLocationConsent: Boolean
        get() = runBlocking {
            dataStore.data.first()[HAS_SHOWN_LOCATION_CONSENT] ?: false
        }

    // ─── Reactive flows (for Compose UI bindings) ────────────────

    fun observeMeshLocationBroadcast(): Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[MESH_LOCATION_BROADCAST] ?: false
    }

    fun observeLocalMapLocation(): Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[LOCAL_MAP_LOCATION] ?: true
    }

    // ─── Write operations ────────────────────────────────────────

    suspend fun setMeshLocationBroadcast(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[MESH_LOCATION_BROADCAST] = enabled
        }
    }

    suspend fun setLocalMapLocation(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[LOCAL_MAP_LOCATION] = enabled
        }
    }

    suspend fun setHasShownLocationConsent(shown: Boolean) {
        dataStore.edit { prefs ->
            prefs[HAS_SHOWN_LOCATION_CONSENT] = shown
        }
    }
}
