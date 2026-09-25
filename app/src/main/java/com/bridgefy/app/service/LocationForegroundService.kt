package com.bridgefy.app.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import com.bridgefy.app.BridgeFyApplication
import com.bridgefy.app.model.LocationPingPayload
import com.bridgefy.app.model.MeshMessage
import com.bridgefy.app.model.MessageType
import com.google.android.gms.location.*
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Dedicated foreground service for GPS location.
 *
 * Standalone from [MeshForegroundService] — do NOT merge location logic there.
 *
 * Responsibilities:
 * - Periodic location updates (5-min interval, 50m displacement, BALANCED_POWER)
 * - Immediate ping on WiFi Direct session ready
 * - One-shot fix for chat location share (does NOT broadcast to mesh)
 * - Hard-gates all mesh broadcasts on user opt-in preference
 */
class LocationForegroundService : LifecycleService() {

    companion object {
        private const val TAG = "LocationService"
        const val NOTIFICATION_ID = 1002
        private const val CHANNEL_ID = "location_service_channel"

        fun start(context: Context) {
            val intent = Intent(context, LocationForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, LocationForegroundService::class.java))
        }
    }

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private lateinit var fusedClient: FusedLocationProviderClient
    private var cancellationTokenSource = CancellationTokenSource()
    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): LocationForegroundService = this@LocationForegroundService
    }

    // Offline-friendly high-accuracy periodic location request.
    // PRIORITY_HIGH_ACCURACY: forces physical GPS hardware satellite lock (vital offline).
    // 0m displacement gate: updates continuously so path tracing in navigations is real-time.
    // 5-second interval: keeps location fresh for live map and routing.
    private val periodicRequest = LocationRequest.Builder(
        Priority.PRIORITY_HIGH_ACCURACY,
        TimeUnit.SECONDS.toMillis(5)
    ).apply {
        setMinUpdateIntervalMillis(TimeUnit.SECONDS.toMillis(3))
        setMinUpdateDistanceMeters(0f)
        setGranularity(Granularity.GRANULARITY_PERMISSION_LEVEL)
        setWaitForAccurateLocation(true)
        setMaxUpdateDelayMillis(TimeUnit.SECONDS.toMillis(5))
    }.build()

    private val periodicCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { location ->
                // Accuracy gate — don't broadcast a wildly inaccurate fix
                if (location.accuracy <= 50f) {
                    broadcastLocationToMesh(location)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        fusedClient = LocationServices.getFusedLocationProviderClient(this)

        createNotificationChannel()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                buildNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIFICATION_ID, buildNotification())
        }
        startPeriodicUpdates()

        Log.d(TAG, "LocationForegroundService started")
    }

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return binder
    }

    /**
     * Called by PeerDiscoveryManager.onWifiDirectSessionReady().
     * Sends an immediate ping so a newly-connected peer sees us on their map
     * without waiting up to 5 minutes for the next periodic update.
     */
    @SuppressLint("MissingPermission")
    fun requestImmediateLocationPing() {
        val oneShot = CurrentLocationRequest.Builder()
            .setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
            .setMaxUpdateAgeMillis(TimeUnit.MINUTES.toMillis(2))  // accept a 2-min cached fix
            .setDurationMillis(TimeUnit.SECONDS.toMillis(10))     // don't spin the radio >10s
            .build()

        fusedClient.getCurrentLocation(oneShot, cancellationTokenSource.token)
            .addOnSuccessListener { location ->
                location?.let { broadcastLocationToMesh(it) }
                    ?: fetchLastKnownAndBroadcast()
            }
            .addOnFailureListener { fetchLastKnownAndBroadcast() }
    }

    /**
     * Called by LocationShareViewModel when the user opens the location share sheet.
     * Returns a one-shot location for preview and sending — does NOT broadcast to mesh.
     */
    @SuppressLint("MissingPermission")
    fun requestOneShotForShare(onResult: (Location?) -> Unit) {
        val oneShot = CurrentLocationRequest.Builder()
            .setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
            .setMaxUpdateAgeMillis(TimeUnit.MINUTES.toMillis(2))
            .setDurationMillis(TimeUnit.SECONDS.toMillis(15))
            .build()

        fusedClient.getCurrentLocation(oneShot, cancellationTokenSource.token)
            .addOnSuccessListener { onResult(it) }
            .addOnFailureListener { onResult(null) }
    }

    private fun broadcastLocationToMesh(location: Location) {
        val app = application as BridgeFyApplication

        // Hard gate — if user has not opted in, never transmit location
        if (!app.settingsRepository.isMeshLocationBroadcastEnabled) return

        val payload = LocationPingPayload(
            senderId = app.deviceId,
            lat = location.latitude,
            lon = location.longitude,
            accuracyMeters = location.accuracy,
            altitudeMeters = if (location.hasAltitude()) location.altitude.toFloat() else null,
            timestampEpochSec = location.time / 1000
        )

        val message = MeshMessage(
            id = UUID.randomUUID().toString(),
            senderId = app.deviceId,
            targetId = null,     // null = broadcast to all peers
            type = MessageType.LOCATION_PING,
            payload = json.encodeToString(payload),
            ttl = 3,             // lower than TEXT — stale location is harmful, not just useless
            timestamp = System.currentTimeMillis(),
            hopPath = listOf(app.deviceId)
        )

        GlobalScope.launch(Dispatchers.IO) {
            try {
                app.meshManager.sendLocationPing(message)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send location ping", e)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun fetchLastKnownAndBroadcast() {
        fusedClient.lastLocation.addOnSuccessListener { location ->
            location?.let { broadcastLocationToMesh(it) }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startPeriodicUpdates() {
        fusedClient.requestLocationUpdates(periodicRequest, periodicCallback, mainLooper)
    }

    override fun onDestroy() {
        fusedClient.removeLocationUpdates(periodicCallback)
        cancellationTokenSource.cancel()
        Log.d(TAG, "LocationForegroundService stopped")
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Mesh Location",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Used to share your location with nearby mesh peers"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java)
                ?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("BridgeFy")
            .setContentText("Sharing location with mesh network")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }
}
