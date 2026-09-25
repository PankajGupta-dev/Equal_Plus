package com.bridgefy.app.transport

import com.bridgefy.app.model.MeshPeer
import com.bridgefy.app.model.TransportType
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Abstraction over a communication transport (BLE or WiFi Direct).
 *
 * Each transport implementation handles discovery, connection management,
 * and data transfer for its specific medium. The [TransportManager]
 * orchestrates multiple transports and exposes a unified API.
 */
interface Transport {

    /** The type of transport this implementation provides */
    val transportType: TransportType

    /** Currently discovered peers via this transport */
    val discoveredPeers: StateFlow<List<MeshPeer>>

    /** Incoming messages received through this transport */
    val incomingMessages: SharedFlow<TransportMessage>

    /** Connection lifecycle events */
    val connectionEvents: SharedFlow<ConnectionEvent>

    /**
     * Upgrades a peer's identity from a temporary ID (like MAC address) to its actual mesh UUID.
     */
    fun upgradePeerId(oldId: String, newId: String) {}

    /** Start discovery and listening */
    suspend fun start()

    /** Stop all transport activity */
    suspend fun stop()

    /** Start only the discovery/scanning component (called by PeerDiscoveryManager) */
    fun startDiscovery() {}

    /** Stop only the discovery/scanning component (called by PeerDiscoveryManager) */
    fun stopDiscovery() {}

    /**
     * Send raw data to a specific peer.
     * @return true if the send was successful
     */
    suspend fun sendMessage(peerId: String, data: ByteArray): Boolean

    /**
     * Broadcast raw data to all connected/discovered peers.
     * @param excludePeers Set of peer IDs to skip (e.g., the sender)
     */
    suspend fun broadcastMessage(data: ByteArray, excludePeers: Set<String> = emptySet())
}
