package com.bridgefy.app.transport

import android.content.Context
import android.util.Log
import com.bridgefy.app.model.MeshPeer
import com.bridgefy.app.model.TransportType
import com.bridgefy.app.transport.ble.BleTransport
import com.bridgefy.app.transport.wifidirect.WiFiDirectTransport
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * Orchestrates multiple transport layers (BLE + WiFi Direct).
 *
 * Transport separation policy:
 * - **Discovery**: BLE is the primary discovery mechanism (low power);
 *   WiFi Direct discovery is a fallback. Controlled by [PeerDiscoveryManager].
 * - **Sending**: ALL message payloads (text/images/files) go through WiFi Direct
 *   exclusively. BLE is never used for sending.
 * - **Receiving**: Messages can arrive on either transport (BLE GATT writes or
 *   WiFi Direct sockets). Both are merged into [incomingMessages].
 *
 * Send reliability:
 * - Failed sends are retried up to [MAX_SEND_RETRIES] times with exponential backoff
 */
class TransportManager(
    private val context: Context,
    private val deviceId: String,
    private val username: String
) {
    companion object {
        private const val TAG = "TransportManager"
        private const val MAX_SEND_RETRIES = 3
        private const val RETRY_BASE_DELAY_MS = 1000L
    }

    private val bleTransport = BleTransport(context, deviceId, username)
    internal val wifiDirectTransport = WiFiDirectTransport(context, deviceId)
    private val transports: List<Transport> = listOf(bleTransport, wifiDirectTransport)

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // ─── Peer Flows (for PeerDiscoveryManager) ──────────────────

    /** BLE-discovered peers (read by PeerDiscoveryManager during BLE scan phase) */
    val blePeers: StateFlow<List<MeshPeer>> = bleTransport.discoveredPeers

    /** WiFi Direct-discovered peers (read by PeerDiscoveryManager during WiFi fallback) */
    val wifiPeers: StateFlow<List<MeshPeer>> = wifiDirectTransport.discoveredPeers

    /**
     * Merged stream of incoming messages from all transports.
     * Messages can arrive on either BLE or WiFi Direct.
     */
    val incomingMessages: SharedFlow<TransportMessage> = merge(
        bleTransport.incomingMessages,
        wifiDirectTransport.incomingMessages
    ).shareIn(scope, SharingStarted.Eagerly, replay = 0)

    /**
     * Merged connection events from all transports.
     */
    val connectionEvents: SharedFlow<ConnectionEvent> = merge(
        bleTransport.connectionEvents,
        wifiDirectTransport.connectionEvents
    ).shareIn(scope, SharingStarted.Eagerly, replay = 0)

    // ─── Lifecycle ───────────────────────────────────────────────

    suspend fun start() {
        Log.d(TAG, "Starting all transports")
        coroutineScope {
            launch { bleTransport.start() }
            launch { wifiDirectTransport.start() }
        }
        Log.d(TAG, "All transports started")
    }

    suspend fun stop() {
        Log.d(TAG, "Stopping all transports")
        coroutineScope {
            launch { bleTransport.stop() }
            launch { wifiDirectTransport.stop() }
        }
        scope.coroutineContext.cancelChildren()
        Log.d(TAG, "All transports stopped")
    }

    // ─── Discovery Control (called by PeerDiscoveryManager) ─────

    fun startBleDiscovery() {
        bleTransport.startDiscovery()
    }

    fun stopBleDiscovery() {
        bleTransport.stopDiscovery()
    }

    fun startWifiDiscovery() {
        wifiDirectTransport.startDiscovery()
    }

    fun stopWifiDiscovery() {
        wifiDirectTransport.stopDiscovery()
    }

    // ─── Send (WiFi Direct Only) ────────────────────────────────

    /**
     * Send data to a specific peer via WiFi Direct with retry logic.
     *
     * BLE is NEVER used for sending — it is discovery-only.
     */
    suspend fun sendToPeer(peerId: String, data: ByteArray): Boolean {
        return sendWithRetry(peerId, data)
    }

    /**
     * Broadcast data to all known peers via WiFi Direct only.
     */
    suspend fun broadcastToAll(data: ByteArray, exclude: Set<String> = emptySet()) {
        wifiDirectTransport.broadcastMessage(data, exclude)
        // Also broadcast via BLE if the payload size fits the BLE MTU limit,
        // reaching nearby phones that are discovered but not currently in a WiFi P2P group.
        if (data.size <= com.bridgefy.app.transport.ble.BleConstants.MAX_BLE_PAYLOAD) {
            bleTransport.broadcastMessage(data, exclude)
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────

    /**
     * Retry sending via WiFi Direct with exponential backoff.
     */
    private suspend fun sendWithRetry(peerId: String, data: ByteArray): Boolean {
        repeat(MAX_SEND_RETRIES) { attempt ->
            val success = wifiDirectTransport.sendMessage(peerId, data)
            if (success) {
                if (attempt > 0) {
                    Log.d(TAG, "Send to $peerId succeeded on retry #$attempt")
                }
                return true
            }
            if (attempt < MAX_SEND_RETRIES - 1) {
                val delayMs = RETRY_BASE_DELAY_MS * (attempt + 1)
                Log.d(TAG, "Send to $peerId failed (attempt ${attempt + 1}/$MAX_SEND_RETRIES), retrying in ${delayMs}ms")
                delay(delayMs)
            }
        }
        Log.w(TAG, "All $MAX_SEND_RETRIES send attempts to $peerId failed")
        return false
    }

    /**
     * Upgrades a peer's identity across all transports from a temporary ID (like MAC address)
     * to its actual mesh UUID.
     */
    fun upgradePeerId(oldId: String, newId: String) {
        for (transport in transports) {
            transport.upgradePeerId(oldId, newId)
        }
    }
}
