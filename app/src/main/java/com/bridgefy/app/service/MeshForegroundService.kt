package com.bridgefy.app.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.bridgefy.app.BridgeFyApplication
import com.bridgefy.app.MainActivity
import kotlinx.coroutines.*

/**
 * Foreground service that keeps the mesh network alive when the app is backgrounded.
 *
 * Responsibilities:
 * - Maintains a persistent notification showing mesh status
 * - Keeps TransportManager (BLE + WiFi Direct) running
 * - Holds a partial WakeLock during active transfers
 * - Manages MeshManager lifecycle
 *
 * The service is started when the user opens the app and continues running
 * until explicitly stopped.
 */
class MeshForegroundService : Service() {

    companion object {
        private const val TAG = "MeshService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "bridgefy_mesh"
        private const val CHANNEL_NAME = "BridgeFy Mesh"

        fun start(context: Context) {
            val intent = Intent(context, MeshForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MeshForegroundService::class.java))
        }
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "MeshForegroundService created")

        createNotificationChannel()

        val notification = buildNotification("Initializing mesh network...")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        acquireWakeLock()

        // Start mesh
        scope.launch {
            try {
                val app = application as BridgeFyApplication
                app.meshManager.start()
                updateNotification("Mesh active — discovering peers...")

                // Update notification periodically with stats
                launch {
                    app.meshManager.meshStats.collect { stats ->
                        updateNotification(
                            "Peers: ${stats.activePeers} | " +
                                    "Sent: ${stats.messagesSent} | " +
                                    "Pending: ${stats.pendingMessages}"
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start mesh", e)
                updateNotification("Mesh error: ${e.message}")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "MeshForegroundService destroyed")

        scope.launch {
            try {
                val app = application as BridgeFyApplication
                app.meshManager.stop()
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping mesh", e)
            }
        }

        releaseWakeLock()
        scope.cancel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    // ─── Notification ────────────────────────────────────────────

    private fun buildNotification(contentText: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("BridgeFy Mesh Active")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun updateNotification(contentText: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID, buildNotification(contentText))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps BridgeFy mesh network active in background"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    // ─── WakeLock ────────────────────────────────────────────────

    @Suppress("DEPRECATION")
    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val powerManager = getSystemService(POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "BridgeFy::MeshWakeLock"
            ).apply {
                acquire()
            }
            Log.d(TAG, "WakeLock acquired")
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
                Log.d(TAG, "WakeLock released")
            }
        }
        wakeLock = null
    }
}
