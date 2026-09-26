package com.bridgefy.app

import android.app.Application
import android.content.Context
import android.util.Log
import com.bridgefy.app.db.DatabaseProvider
import com.bridgefy.app.mesh.MeshManager
import com.bridgefy.app.repository.MessageRepository
import com.bridgefy.app.repository.SettingsRepository
import com.bridgefy.app.transport.TransportManager
import com.bridgefy.app.call.VoiceCallManager
import java.util.UUID

/**
 * Application class for BridgeFy.
 *
 * Initializes core singletons and provides them to the rest of the app:
 * - Room database
 * - Device identity (per-install UUID)
 * - TransportManager
 * - MessageRepository
 * - MeshManager
 */
class BridgeFyApplication : Application() {

    companion object {
        private const val TAG = "BridgeFyApp"
        private const val PREFS_NAME = "bridgefy_prefs"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_DEVICE_NAME = "device_name"

        lateinit var instance: BridgeFyApplication
            private set
    }

    lateinit var deviceId: String
        private set

    lateinit var transportManager: TransportManager
        private set

    lateinit var messageRepository: MessageRepository
        private set

    lateinit var meshManager: MeshManager
        private set

    lateinit var settingsRepository: SettingsRepository
        private set

    lateinit var voiceCallManager: VoiceCallManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Initialize database
        DatabaseProvider.init(this)

        // Initialize device identity
        deviceId = getOrCreateDeviceId()
        Log.d(TAG, "Device ID: $deviceId")

        // Initialize core components
        transportManager = TransportManager(this, deviceId, getDeviceName())
        messageRepository = MessageRepository()
        meshManager = MeshManager(this, deviceId, transportManager, messageRepository)
        settingsRepository = SettingsRepository(this, deviceId)
        voiceCallManager = VoiceCallManager(this, deviceId, meshManager, transportManager.wifiDirectTransport)

        // Initialize 100% offline map tile manager
        com.bridgefy.app.map.OfflineMapManager.init(this)

        // Initialize MapLibre Native SDK with ZERO API key requirement
        try {
            com.mapbox.mapboxsdk.Mapbox.getInstance(this, "offline_token", com.mapbox.mapboxsdk.WellKnownTileServer.MapLibre)
            com.mapbox.mapboxsdk.Mapbox.getTileServerOptions()?.setApiKeyRequired(false)
            Log.d(TAG, "MapLibre initialized for 100% offline MBTiles operation (zero API keys)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize MapLibre: ${e.message}", e)
        }

        Log.d(TAG, "BridgeFy initialized")
    }

    /**
     * Get or create a per-install UUID stored in SharedPreferences.
     * This is the device's identity on the mesh network.
     */
    private fun getOrCreateDeviceId(): String {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existing = prefs.getString(KEY_DEVICE_ID, null)

        if (existing != null) return existing

        val newId = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_DEVICE_ID, newId).apply()
        Log.d(TAG, "Generated new device ID: $newId")
        return newId
    }

    /**
     * Get or set the user-visible device name.
     */
    fun getDeviceName(): String {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_DEVICE_NAME, null)
            ?: android.os.Build.MODEL.also { setDeviceName(it) }
    }

    fun setDeviceName(name: String) {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_DEVICE_NAME, name).apply()
    }
}
