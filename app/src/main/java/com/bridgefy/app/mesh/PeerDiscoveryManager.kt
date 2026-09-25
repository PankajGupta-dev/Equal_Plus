package com.bridgefy.app.mesh

import android.util.Log
import com.bridgefy.app.db.DatabaseProvider
import com.bridgefy.app.db.PeerQueries
import com.bridgefy.app.model.*
import com.bridgefy.app.transport.ConnectionEvent
import com.bridgefy.app.transport.TransportManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * Simplified peer discovery using WiFi Direct ONLY.
 *
 * Why WiFi Direct only?
 * - All messages (text, image, voice, file, location) go through WiFi Direct
 * - BLE was only used for discovery but added 10+ seconds of delay
 * - BLE GATT identity reads are unreliable and cause devices to not appear
 * - WiFi Direct discovery is fast (~2-3 seconds to see nearby devices)
 *
 * Flow:
 *   1. Start WiFi Direct discovery immediately on launch
 *   2. Devices appear in the list within seconds
 *   3. User taps a device → WiFi Direct P2P connection
 *   4. Connected, ready to chat
 */
class PeerDiscoveryManager(
    private val transportManager: TransportManager,
    private val config: DiscoveryConfig = DiscoveryConfig()
) {
    companion object {
        private const val TAG = "PeerDiscoveryManager"
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var scanJob: Job? = null

    // ─── Public Flows ────────────────────────────────────────────

    private val _discoveryState = MutableStateFlow(DiscoveryState.IDLE)
    val discoveryState: StateFlow<DiscoveryState> = _discoveryState.asStateFlow()

    private val _activePeers = MutableStateFlow<List<MeshPeer>>(emptyList())
    val activePeers: StateFlow<List<MeshPeer>> = _activePeers.asStateFlow()

    private val _nearbyUsers = MutableStateFlow<List<NearbyUser>>(emptyList())
    val nearbyUsers: StateFlow<List<NearbyUser>> = _nearbyUsers.asStateFlow()

    private val _activeDiscoverySource = MutableStateFlow<ConnectionType?>(null)
    val activeDiscoverySource: StateFlow<ConnectionType?> = _activeDiscoverySource.asStateFlow()

    private val connectedIds = mutableSetOf<String>()

    // ─── Lifecycle ───────────────────────────────────────────────

    fun start() {
        // Listen to connection events
        scope.launch {
            transportManager.connectionEvents.collect { event ->
                when (event) {
                    is ConnectionEvent.PeerConnected -> {
                        Log.d(TAG, "Peer connected: ${event.peer.deviceId}")
                        connectedIds.add(event.peer.alertnetId ?: event.peer.deviceId)
                        try {
                            PeerQueries.insertPeer(DatabaseProvider.db, event.peer)
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to persist connected peer", e)
                        }
                        refreshActivePeers()
                        rebuildNearbyUsers()
                    }
                    is ConnectionEvent.PeerDisconnected -> {
                        Log.d(TAG, "Peer disconnected: ${event.peerId}")
                        connectedIds.remove(event.peerId)
                        refreshActivePeers()
                        rebuildNearbyUsers()
                    }
                    is ConnectionEvent.TransportError -> {
                        Log.w(TAG, "Transport error: ${event.message}")
                    }
                }
            }
        }

        // Continuously collect WiFi Direct discovered peers and rebuild UI
        scope.launch {
            transportManager.wifiPeers.collect { peers ->
                if (peers.isNotEmpty()) {
                    persistPeers(peers)
                    refreshActivePeers()
                    rebuildNearbyUsers()
                }
            }
        }

        // Periodic stale peer cleanup
        scope.launch {
            while (isActive) {
                delay(config.cleanupIntervalMs)
                cleanupStalePeers()
                refreshActivePeers()
                rebuildNearbyUsers()
            }
        }

        // Start WiFi Direct discovery immediately
        startScanning()
    }

    fun stop() {
        scanJob?.cancel()
        scope.coroutineContext.cancelChildren()
        _discoveryState.value = DiscoveryState.IDLE
        _activeDiscoverySource.value = null
    }

    /**
     * Pull-to-refresh: restart WiFi Direct discovery immediately.
     */
    fun requestScan() {
        scanJob?.cancel()
        startScanning()
    }

    fun markConnecting(userId: String) {
        val current = _nearbyUsers.value.toMutableList()
        val index = current.indexOfFirst { it.id == userId }
        if (index >= 0) {
            current[index] = current[index].copy(status = UserStatus.CONNECTING)
            _nearbyUsers.value = current
        }
    }

    fun markConnected(userId: String) {
        connectedIds.add(userId)
        rebuildNearbyUsers()
    }

    /**
     * Revert a user from CONNECTING back to NEARBY when a connection attempt
     * fails or times out. Ensures no stale "connecting" states remain.
     */
    fun markDisconnected(userId: String) {
        connectedIds.remove(userId)
        val current = _nearbyUsers.value.toMutableList()
        val index = current.indexOfFirst { it.id == userId }
        if (index >= 0) {
            current[index] = current[index].copy(status = UserStatus.NEARBY)
            _nearbyUsers.value = current
        }
    }

    // ─── WiFi Direct Discovery Loop ──────────────────────────────

    private fun startScanning() {
        scanJob = scope.launch {
            while (isActive) {
                _discoveryState.value = DiscoveryState.SCANNING_WIFI
                _activeDiscoverySource.value = ConnectionType.WIFI_DIRECT

                Log.d(TAG, "Starting WiFi Direct discovery")
                transportManager.startWifiDiscovery()

                // Keep discovery active for the scan window
                delay(config.wifiScanTimeoutMs)

                val currentPeers = transportManager.wifiPeers.value
                if (currentPeers.isNotEmpty()) {
                    _discoveryState.value = DiscoveryState.WIFI_FOUND
                    Log.d(TAG, "Found ${currentPeers.size} WiFi Direct peers")
                } else {
                    _discoveryState.value = DiscoveryState.IDLE
                    Log.d(TAG, "No peers found, will retry")
                }

                // Brief cooldown before next cycle
                delay(config.scanCooldownMs)
            }
        }
    }

    // ─── NearbyUser List Builder ─────────────────────────────────

    private fun rebuildNearbyUsers() {
        val peers = _activePeers.value

        // ── Step 1: Pre-filter MAC-keyed duplicates ──────────────────
        // After a handshake, the DB may contain both a MAC-keyed row and a
        // UUID-keyed row for the same physical device. Build a lookup of
        // macAddress → UUID-keyed peer so we can skip MAC-keyed duplicates.
        val macToUuidPeer = mutableMapOf<String, MeshPeer>()
        for (peer in peers) {
            if (peer.macAddress != null && !isMacAddress(peer.deviceId)) {
                macToUuidPeer[peer.macAddress] = peer
            }
        }

        val dedupedPeers = peers.filter { peer ->
            if (isMacAddress(peer.deviceId)) {
                // Skip this MAC-keyed peer if a UUID-keyed peer covers the same MAC
                val dominated = macToUuidPeer.containsKey(peer.deviceId)
                if (dominated) {
                    Log.d(TAG, "Skipping MAC-keyed duplicate: ${peer.deviceId}")
                }
                !dominated
            } else {
                true
            }
        }

        // ── Step 2: Build NearbyUser map (deduplicated by userId) ────
        val userMap = mutableMapOf<String, NearbyUser>()

        for (peer in dedupedPeers) {
            val userId = peer.alertnetId ?: peer.deviceId
            // Use the device display name directly — no complex transformations
            val userName = peer.displayName.ifBlank { "Device-${userId.take(6)}" }

            val status = when {
                peer.isConnected || userId in connectedIds -> UserStatus.CONNECTED
                else -> UserStatus.NEARBY
            }

            val existing = userMap[userId]
            if (existing == null) {
                userMap[userId] = NearbyUser(
                    id = userId,
                    name = userName,
                    rssi = peer.rssi,
                    connectionType = ConnectionType.WIFI_DIRECT,
                    status = status,
                    lastSeen = peer.lastSeen
                )
            } else {
                val mergedStatus = when {
                    existing.status == UserStatus.CONNECTED || status == UserStatus.CONNECTED -> UserStatus.CONNECTED
                    existing.status == UserStatus.CONNECTING || status == UserStatus.CONNECTING -> UserStatus.CONNECTING
                    else -> UserStatus.NEARBY
                }
                userMap[userId] = existing.copy(
                    status = mergedStatus,
                    lastSeen = maxOf(existing.lastSeen, peer.lastSeen)
                )
            }
        }

        _nearbyUsers.value = userMap.values
            .sortedWith(
                compareByDescending<NearbyUser> { it.status == UserStatus.CONNECTED }
                    .thenByDescending { it.lastSeen }
            )
            .toList()

        Log.d(TAG, "NearbyUsers: ${_nearbyUsers.value.size} " +
                "(${_nearbyUsers.value.count { it.status == UserStatus.CONNECTED }} connected)")
    }

    /**
     * Returns true if the given string looks like a WiFi Direct MAC address
     * (e.g., "aa:bb:cc:dd:ee:ff"). Used to identify peers that haven't yet
     * been upgraded to a UUID via handshake.
     */
    private fun isMacAddress(id: String): Boolean {
        return id.matches(Regex("^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$"))
    }

    // ─── Helpers ─────────────────────────────────────────────────

    private suspend fun persistPeers(peers: List<MeshPeer>) {
        for (peer in peers) {
            try {
                PeerQueries.upsertPeer(DatabaseProvider.db, peer)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to persist peer: ${peer.deviceId}", e)
            }
        }
    }

    private suspend fun refreshActivePeers() {
        try {
            val since = System.currentTimeMillis() - config.peerExpiryMs
            val peers = PeerQueries.getActivePeers(DatabaseProvider.db, since)
            _activePeers.value = peers
        } catch (e: Exception) {
            Log.e(TAG, "Failed to refresh peers", e)
        }
    }

    private suspend fun cleanupStalePeers() {
        try {
            val cutoff = System.currentTimeMillis() - config.peerExpiryMs
            PeerQueries.deleteStale(DatabaseProvider.db, cutoff)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clean up stale peers", e)
        }
    }
}
