package com.bridgefy.app.ui.viewmodel

import android.annotation.SuppressLint
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bridgefy.app.mesh.DiscoveryState
import com.bridgefy.app.mesh.MeshManager
import com.bridgefy.app.mesh.MeshStats
import com.bridgefy.app.model.*
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * ViewModel for the peers/discovery screen and mesh stats.
 *
 * Exposes the unified [NearbyUser] list (deduplicated, clean names)
 * split into connected and nearby-only sections for the UI.
 */
class PeersViewModel(
    private val meshManager: MeshManager
) : ViewModel() {

    /** All nearby users (unified, deduplicated) */
    val nearbyUsers: StateFlow<List<NearbyUser>> = meshManager.nearbyUsers

    /** Connected users (filtered from nearbyUsers) */
    val connectedUsers: StateFlow<List<NearbyUser>> = nearbyUsers
        .map { users -> users.filter { it.status == UserStatus.CONNECTED } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Nearby-only users (not yet connected) */
    val nearbyOnlyUsers: StateFlow<List<NearbyUser>> = nearbyUsers
        .map { users -> users.filter { it.status != UserStatus.CONNECTED } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Currently active mesh peers (raw, for backward compatibility) */
    val peers: StateFlow<List<MeshPeer>> = meshManager.activePeers

    /** Real-time mesh statistics */
    val meshStats: StateFlow<MeshStats> = meshManager.meshStats

    /** Current discovery state machine phase */
    val discoveryState: StateFlow<DiscoveryState> = meshManager.discoveryState

    /** Which discovery source is currently active */
    val activeDiscoverySource: StateFlow<ConnectionType?> = meshManager.activeDiscoverySource

    /** Whether any scan is currently active (derived from discoveryState) */
    val isDiscovering: StateFlow<Boolean> = discoveryState
        .map { it != DiscoveryState.IDLE }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /** Incoming SOS alerts from other peers */
    val incomingSOS: SharedFlow<MeshMessage> = meshManager.incomingSOS

    /** Whether SOS is being sent */
    private val _sosSending = MutableStateFlow(false)
    val sosSending: StateFlow<Boolean> = _sosSending.asStateFlow()

    // ─── Connection State ────────────────────────────────────────

    /** ID of the user currently being connected to (null = no connection in progress) */
    private val _connectingUserId = MutableStateFlow<String?>(null)
    val connectingUserId: StateFlow<String?> = _connectingUserId.asStateFlow()

    /** Error message from the last failed connection attempt */
    private val _connectionError = MutableStateFlow<String?>(null)
    val connectionError: StateFlow<String?> = _connectionError.asStateFlow()

    /**
     * Trigger an immediate peer discovery sweep.
     */
    fun refreshPeers() {
        meshManager.peerDiscoveryManager.requestScan()
    }

    /**
     * Connect to a nearby user — initiates a real WiFi Direct P2P connection.
     *
     * Blocks duplicate taps: if a connection is already in progress, the call
     * is silently ignored. On success, [onSuccess] is invoked (typically to
     * navigate to the chat screen). On failure/timeout, [connectionError] is set.
     */
    fun connectToUser(userId: String, onSuccess: (String) -> Unit) {
        // Prevent duplicate connection attempts
        if (_connectingUserId.value != null) return

        _connectingUserId.value = userId
        _connectionError.value = null

        viewModelScope.launch {
            val connectedId = meshManager.connectToUser(userId)
            if (connectedId != null) {
                onSuccess(connectedId)
            } else {
                _connectionError.value = "Connection failed. Please try again."
            }
            _connectingUserId.value = null
        }
    }

    /**
     * Clear the connection error message (e.g., after user dismisses the error).
     */
    fun clearConnectionError() {
        _connectionError.value = null
    }

    /**
     * Send an SOS broadcast with current GPS location to all nearby peers.
     */
    @SuppressLint("MissingPermission")
    fun sendSOS(fusedClient: FusedLocationProviderClient?) {
        _sosSending.value = true

        if (fusedClient != null) {
            // Try fast cached location first for instant SOS broadcast
            fusedClient.lastLocation.addOnSuccessListener { lastLoc ->
                if (lastLoc != null) {
                    viewModelScope.launch {
                        meshManager.sendSOS(lastLoc.latitude, lastLoc.longitude)
                        _sosSending.value = false
                    }
                } else {
                    fusedClient.getCurrentLocation(
                        Priority.PRIORITY_HIGH_ACCURACY,
                        CancellationTokenSource().token
                    ).addOnSuccessListener { location ->
                        viewModelScope.launch {
                            meshManager.sendSOS(location?.latitude, location?.longitude)
                            _sosSending.value = false
                        }
                    }.addOnFailureListener {
                        viewModelScope.launch {
                            meshManager.sendSOS(null, null)
                            _sosSending.value = false
                        }
                    }
                }
            }.addOnFailureListener {
                viewModelScope.launch {
                    meshManager.sendSOS(null, null)
                    _sosSending.value = false
                }
            }
        } else {
            // No location client — send without coordinates immediately
            viewModelScope.launch {
                meshManager.sendSOS(null, null)
                _sosSending.value = false
            }
        }
    }
}

