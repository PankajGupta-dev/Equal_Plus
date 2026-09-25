package com.bridgefy.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.bridgefy.app.service.LocationForegroundService
import com.bridgefy.app.service.MeshForegroundService
import com.bridgefy.app.ui.navigation.NavGraph
import com.bridgefy.app.ui.theme.BridgeFyTheme
import com.bridgefy.app.ui.theme.MeshNavy

/**
 * Main entry point for BridgeFy.
 *
 * Handles:
 * - Runtime permission requests (BLE, WiFi Direct, Location, Notifications)
 * - Starting the MeshForegroundService
 * - Starting the LocationForegroundService (when user has opted in)
 * - Hosting the Compose NavGraph
 */
class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            Log.d(TAG, "All permissions granted")
            startMeshService()
            startLocationServiceIfEnabled()
        } else {
            val denied = permissions.filter { !it.value }.keys
            Log.w(TAG, "Permissions denied: $denied")
            Toast.makeText(
                this,
                "Some permissions were denied. Mesh may not work properly.",
                Toast.LENGTH_LONG
            ).show()
            // Start anyway — transports will handle missing permissions gracefully
            startMeshService()
            // Only start location if the specific location permission was granted
            if (hasLocationPermission()) {
                startLocationServiceIfEnabled()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestPermissions()

        val app = application as BridgeFyApplication

        setContent {
            BridgeFyTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MeshNavy
                ) {
                    NavGraph(app = app)
                }
            }
        }
    }

    private fun requestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.addAll(listOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.NEARBY_WIFI_DEVICES
            ))
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val needed = permissions.filter {
            ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
        } else {
            startMeshService()
            startLocationServiceIfEnabled()
        }
    }

    private fun startMeshService() {
        MeshForegroundService.start(this)
    }

    /**
     * Start LocationForegroundService only when BOTH conditions are met:
     * 1. ACCESS_FINE_LOCATION permission is granted
     * 2. User has opted in to mesh location broadcasting via SettingsRepository
     *
     * Called on app launch after permissions are resolved.
     * Also triggered reactively when the user toggles broadcasting ON
     * in LocationPrivacySettingsScreen.
     */
    private fun startLocationServiceIfEnabled() {
        val app = application as BridgeFyApplication

        if (!hasLocationPermission()) {
            Log.d(TAG, "Location permission not granted — skipping location service")
            return
        }

        if (app.settingsRepository.isMeshLocationBroadcastEnabled) {
            Log.d(TAG, "Starting LocationForegroundService (broadcast enabled)")
            LocationForegroundService.start(this)
        } else {
            Log.d(TAG, "Location broadcast disabled — not starting location service")
        }
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) {
            MeshForegroundService.stop(this)
            LocationForegroundService.stop(this)
        }
    }
}
