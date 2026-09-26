package com.bridgefy.app.mesh

import android.content.Context
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.bridgefy.app.media.VoicePlayerManager
import com.bridgefy.app.media.VoiceRecorderManager
import com.bridgefy.app.model.*
import com.bridgefy.app.repository.MessageRepository
import com.bridgefy.app.security.CryptoManager
import com.bridgefy.app.security.KeyManager
import com.bridgefy.app.transfer.FileStorage
import com.bridgefy.app.transfer.FileTransferManager
import com.bridgefy.app.transport.TransportManager
import com.bridgefy.app.db.DatabaseProvider
import com.bridgefy.app.db.MessageQueries
import com.bridgefy.app.db.PeerQueries
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.bridgefy.app.transport.ConnectionEvent
import java.io.File
import java.util.UUID

/**
 * Top-level orchestrator for the AlertNet mesh network.
 *
 * MeshManager is the "brain" — it coordinates all subsystems:
 * - [TransportManager]: BLE + WiFi Direct communication
 * - [MessageRouter]: Routing decisions (deliver, forward, drop)
 * - [PeerDiscoveryManager]: Peer tracking and lifecycle
 * - [MessageRepository]: Persistent message storage
 * - [CryptoManager]: AES-256-GCM encryption/decryption
 * - [AckTracker]: Delivery acknowledgment tracking
 * - [DeduplicationManager]: Duplicate message prevention
 *
 * Background loops:
 * - Store-and-forward: retries queued messages when new peers appear
 * - Maintenance: cleans up expired data periodically
 * - ACK timeout: checks for unconfirmed deliveries
 */
class MeshManager(
    private val context: Context,
    private val deviceId: String,
    private val transportManager: TransportManager,
    private val repository: MessageRepository
) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    val deduplicationManager = DeduplicationManager()
    val ackTracker = AckTracker(deviceId)
    val messageRouter = MessageRouter(deduplicationManager, ackTracker, deviceId)
    val peerDiscoveryManager = PeerDiscoveryManager(transportManager, DiscoveryConfig())

    // ─── Media Components ────────────────────────────────────────

    val fileStorage = FileStorage(context)
    val fileTransferManager = FileTransferManager(
        context, deviceId, transportManager.wifiDirectTransport,
        fileStorage, repository
    )
    val voiceRecorder = VoiceRecorderManager(context)
    val voicePlayer = VoicePlayerManager()

    // ─── Public Flows ────────────────────────────────────────────

    /** Active mesh peers */
    val activePeers: StateFlow<List<MeshPeer>> = peerDiscoveryManager.activePeers

    /** Current discovery state machine phase */
    val discoveryState: StateFlow<DiscoveryState> = peerDiscoveryManager.discoveryState

    /** Unified, deduplicated nearby users for the UI */
    val nearbyUsers: StateFlow<List<NearbyUser>> = peerDiscoveryManager.nearbyUsers

    /** Which discovery source is currently active */
    val activeDiscoverySource: StateFlow<ConnectionType?> = peerDiscoveryManager.activeDiscoverySource

    private val _meshStats = MutableStateFlow(MeshStats())
    /** Real-time mesh statistics */
    val meshStats: StateFlow<MeshStats> = _meshStats.asStateFlow()

    /** Emits incoming SOS alerts from other peers for the UI to display */
    private val _incomingSOS = MutableSharedFlow<MeshMessage>(extraBufferCapacity = 8)
    val incomingSOS: SharedFlow<MeshMessage> = _incomingSOS.asSharedFlow()

    /** Emits incoming CALL_REQUEST, CALL_ACCEPT, CALL_REJECT, CALL_END signals */
    private val _incomingCallSignals = MutableSharedFlow<MeshMessage>(extraBufferCapacity = 8)
    val incomingCallSignals: SharedFlow<MeshMessage> = _incomingCallSignals.asSharedFlow()

    private var isRunning = false

    // ─── Connection Initiation ──────────────────────────────────

    companion object {
        private const val TAG = "MeshManager"
        private const val STORE_FORWARD_INTERVAL_MS = 30_000L
        private const val MAINTENANCE_INTERVAL_MS = 60 * 60 * 1000L // 1 hour
        private const val ACK_CHECK_INTERVAL_MS = 60_000L
        private const val MAX_FILE_SIZE = 20 * 1024 * 1024L // 20 MB
        private const val CONNECTION_TIMEOUT_MS = 20_000L // 20 seconds
    }

    /**
     * Initiate a real WiFi Direct P2P connection to a nearby user.
     *
     * Called when the user explicitly taps a device in the list.
     * This is a suspend function that blocks until the connection either
     * succeeds (PeerConnected event received) or times out (20 seconds).
     *
     * @param userId The AlertNet ID or device ID of the peer to connect to.
     * @return true if the connection succeeded, false on failure or timeout.
     */
    suspend fun connectToUser(userId: String): String? {
        // Return immediately if already connected
        val peer = activePeers.value.find {
            it.alertnetId == userId || it.deviceId == userId
        }
        if (peer != null && peer.isConnected) {
            val actualId = peer.alertnetId ?: peer.deviceId
            peerDiscoveryManager.markConnected(actualId)
            return actualId
        }

        peerDiscoveryManager.markConnecting(userId)

        if (peer == null) {
            Log.w(TAG, "Cannot connect: peer $userId not found in active peers")
            peerDiscoveryManager.markDisconnected(userId)
            return null
        }

        val address = peer.macAddress ?: peer.deviceId
        Log.d(TAG, "Initiating WiFi Direct connection to ${peer.displayName} ($address)")

        // Step 1: Initiate the WiFi Direct P2P connection via the framework
        val initiated = transportManager.wifiDirectTransport.connectToPeerByAddress(address)
        if (!initiated) {
            Log.w(TAG, "WiFi Direct connect request was rejected for $address")
            peerDiscoveryManager.markDisconnected(userId)
            return null
        }

        // Step 2: Wait for the actual PeerConnected event with timeout
        return try {
            withTimeout(CONNECTION_TIMEOUT_MS) {
                val connectedPeer = transportManager.connectionEvents
                    .filterIsInstance<ConnectionEvent.PeerConnected>()
                    .first { event ->
                        val eventPeer = event.peer
                        eventPeer.macAddress == address || eventPeer.deviceId == address ||
                            eventPeer.alertnetId == peer.alertnetId || eventPeer.deviceId == peer.alertnetId
                    }
                val actualId = connectedPeer.peer.alertnetId ?: connectedPeer.peer.deviceId
                Log.d(TAG, "Connection to ${peer.displayName} ($actualId) succeeded")
                peerDiscoveryManager.markConnected(actualId)
                if (actualId != userId) {
                    peerDiscoveryManager.markConnected(userId)
                }
                actualId
            }
        } catch (e: TimeoutCancellationException) {
            Log.w(TAG, "Connection to ${peer.displayName} ($userId) timed out after ${CONNECTION_TIMEOUT_MS}ms")
            peerDiscoveryManager.markDisconnected(userId)
            null
        } catch (e: Exception) {
            Log.e(TAG, "Connection to ${peer.displayName} ($userId) failed", e)
            peerDiscoveryManager.markDisconnected(userId)
            null
        }
    }

    // ─── Lifecycle ───────────────────────────────────────────────

    suspend fun start() {
        if (isRunning) return
        isRunning = true

        Log.d(TAG, "Starting MeshManager for device $deviceId")

        // Initialize subsystems
        deduplicationManager.initialize()
        transportManager.start()
        peerDiscoveryManager.start()

        // Wire FileTransferManager to WiFiDirectTransport for binary receives
        transportManager.wifiDirectTransport.fileTransferManager = fileTransferManager

        // Start message processing pipeline
        startMessageProcessing()

        // Listen for received file transfers
        startFileReceiveProcessing()

        // Start background loops
        startStoreAndForward()
        startMaintenanceLoop()
        startAckTimeoutChecker()

        // Trigger store-and-forward when new peers connect
        scope.launch {
            transportManager.connectionEvents.collect { event ->
                if (event is com.bridgefy.app.transport.ConnectionEvent.PeerConnected) {
                    Log.d(TAG, "New peer connected, triggering store-and-forward")
                    retryPendingMessages()
                }
            }
        }

        updateStats()
        Log.d(TAG, "MeshManager started")
    }

    suspend fun stop() {
        isRunning = false
        peerDiscoveryManager.stop()
        transportManager.stop()
        voiceRecorder.release()
        voicePlayer.release()
        fileTransferManager.release()
        scope.coroutineContext.cancelChildren()
        Log.d(TAG, "MeshManager stopped")
    }

    // ─── Public API (for ViewModels) ────────────────────────────

    /**
     * Send a text message to a specific peer (or broadcast if targetId is null).
     */
    suspend fun sendTextMessage(targetId: String?, text: String) {
        val encrypted = encryptPayload(text) ?: text

        val message = MeshMessage(
            id = UUID.randomUUID().toString(),
            senderId = deviceId,
            targetId = targetId,
            type = MessageType.TEXT,
            payload = encrypted,
            timestamp = System.currentTimeMillis(),
            ttl = 7,
            hopCount = 0,
            hopPath = listOf(deviceId),
            status = DeliveryStatus.QUEUED
        )

        // Mark as seen immediately (don't re-process our own messages)
        deduplicationManager.markSeen(message.id)

        // Persist
        repository.insertMessage(message)

        // Track ACK if targeted
        if (targetId != null) {
            ackTracker.expectAck(message.id)
        }

        // Send via transports
        sendViaTransport(message)

        updateStats()
    }

    /**
     * Send an image to a specific peer using streaming binary transfer.
     *
     * Uses [FileTransferManager] for efficient chunked transfer (no base64).
     */
    suspend fun sendImage(targetId: String?, uri: Uri, caption: String? = null): MeshMessage? {
        val result = fileTransferManager.sendFile(targetId, uri, MessageType.IMAGE, caption)
        if (result != null) {
            deduplicationManager.markSeen(result.id)
            if (targetId != null) ackTracker.expectAck(result.id)
            updateStats()
        }
        return result
    }

    /**
     * Send a video to a specific peer using streaming binary transfer.
     */
    suspend fun sendVideo(targetId: String?, uri: Uri, caption: String? = null): MeshMessage? {
        val result = fileTransferManager.sendFile(targetId, uri, MessageType.VIDEO, caption)
        if (result != null) {
            deduplicationManager.markSeen(result.id)
            if (targetId != null) ackTracker.expectAck(result.id)
            updateStats()
        }
        return result
    }

    /**
     * Send a voice message to a specific peer using streaming binary transfer.
     *
     * @param targetId Peer to send to
     * @param audioFile The recorded .m4a file from [VoiceRecorderManager]
     */
    suspend fun sendVoiceMessage(targetId: String?, audioFile: File): MeshMessage? {
        val result = fileTransferManager.sendVoice(targetId, audioFile)
        if (result != null) {
            deduplicationManager.markSeen(result.id)
            if (targetId != null) ackTracker.expectAck(result.id)
            updateStats()
        }
        return result
    }

    /**
     * Send a generic file to a specific peer using streaming binary transfer.
     */
    suspend fun sendFile(targetId: String?, uri: Uri): MeshMessage? {
        val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
        val type = when {
            mimeType.startsWith("image/") -> MessageType.IMAGE
            mimeType.startsWith("video/") -> MessageType.VIDEO
            else -> MessageType.FILE
        }
        val result = fileTransferManager.sendFile(targetId, uri, type)
        if (result != null) {
            deduplicationManager.markSeen(result.id)
            if (targetId != null) ackTracker.expectAck(result.id)
            updateStats()
        }
        return result
    }

    /**
     * Get the absolute path of a received media file.
     */
    fun getMediaFilePath(fileName: String): String {
        return fileStorage.getFilePath(fileName)
    }

    /**
     * Get conversation messages with a specific peer.
     */
    suspend fun getConversation(peerId: String): List<MeshMessage> {
        return repository.getConversation(peerId)
    }

    /**
     * Delete conversation messages when user backs out of chat (expiry policy).
     */
    suspend fun expireConversation(peerId: String) {
        repository.deleteConversation(peerId)
        Log.d(TAG, "Expired conversation with $peerId")
    }

    // ─── Location Messages ───────────────────────────────────────

    /**
     * Send a LOCATION_PING message to the mesh.
     * Deduplicates: removes any previously queued pings from this sender.
     */
    suspend fun sendLocationPing(message: MeshMessage) {
        // Deduplication — only the latest ping per sender is useful
        try {
            MessageQueries.removeQueuedLocationPings(DatabaseProvider.db, message.senderId)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to dedup location pings", e)
        }

        deduplicationManager.markSeen(message.id)
        repository.insertMessage(message)
        sendViaTransport(message)
        updateStats()
    }

    /**
     * Send an SOS broadcast to ALL nearby peers.
     * Includes current GPS coordinates (if available) and a "HELP ME!" message.
     * targetId = null → broadcasts to everyone.
     */
    suspend fun sendSOS(lat: Double?, lon: Double?) {
        val sosPayload = buildString {
            append("🆘 SOS — HELP ME!")
            if (lat != null && lon != null) {
                append("\nLocation: $lat, $lon")
            }
        }
        val encrypted = encryptPayload(sosPayload) ?: sosPayload

        val message = MeshMessage(
            id = UUID.randomUUID().toString(),
            senderId = deviceId,
            targetId = null,  // broadcast to all
            type = MessageType.SOS,
            payload = encrypted,
            timestamp = System.currentTimeMillis(),
            ttl = 7,
            hopCount = 0,
            hopPath = listOf(deviceId),
            status = DeliveryStatus.QUEUED
        )

        deduplicationManager.markSeen(message.id)
        repository.insertMessage(message)
        val serialized = json.encodeToString(message).toByteArray(Charsets.UTF_8)
        transportManager.broadcastSOS(serialized, lat, lon)
        repository.updateStatus(message.id, DeliveryStatus.SENT)
        updateStats()
        Log.d(TAG, "SOS emergency broadcast sent to all devices within 15m radius")
    }

    /**
     * Send a LOCATION_SHARE message to a specific peer (chat bubble).
     * Does NOT update peer location — it's a chat artifact only.
     */
    suspend fun sendLocationShare(targetId: String, payload: LocationSharePayload) {
        val message = MeshMessage(
            id = UUID.randomUUID().toString(),
            senderId = deviceId,
            targetId = targetId,
            type = MessageType.LOCATION_SHARE,
            payload = json.encodeToString(payload),
            timestamp = System.currentTimeMillis(),
            ttl = 7,
            hopCount = 0,
            hopPath = listOf(deviceId),
            status = DeliveryStatus.QUEUED
        )

        deduplicationManager.markSeen(message.id)
        repository.insertMessage(message)

        ackTracker.expectAck(message.id)

        sendViaTransport(message)
        updateStats()
    }

    /**
     * Decrypt a message payload for display.
     */
    fun decryptPayload(encryptedPayload: String): String {
        val key = KeyManager.getKey(context) ?: return encryptedPayload
        return CryptoManager.decryptString(encryptedPayload, key) ?: encryptedPayload
    }

    /**
     * Send a voice call signaling message to a target peer.
     */
    suspend fun sendCallSignal(targetId: String, type: MessageType, callSignal: CallSignal) {
        val message = MeshMessage(
            id = UUID.randomUUID().toString(),
            senderId = deviceId,
            targetId = targetId,
            type = type,
            payload = json.encodeToString(callSignal),
            timestamp = System.currentTimeMillis(),
            ttl = 1,
            hopCount = 0,
            hopPath = listOf(deviceId),
            status = DeliveryStatus.QUEUED
        )
        try {
            val serialized = json.encodeToString(message).toByteArray(Charsets.UTF_8)
            transportManager.sendToPeer(targetId, serialized)
            Log.d(TAG, "Sent call signal $type to $targetId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send call signal $type to $targetId", e)
        }
    }

    // ─── Internal: File Receive Processing ─────────────────────

    private fun startFileReceiveProcessing() {
        scope.launch {
            fileTransferManager.receivedFiles.collect { message ->
                Log.d(TAG, "File received: ${message.fileName} (${message.type})")
                deduplicationManager.markSeen(message.id)
                updateStats()
            }
        }
    }

    // ─── Internal: Message Processing Pipeline ──────────────────

    private fun startMessageProcessing() {
        scope.launch {
            transportManager.incomingMessages.collect { transportMsg ->
                try {
                    val messageJson = String(transportMsg.data, Charsets.UTF_8)
                    val message = json.decodeFromString<MeshMessage>(messageJson)

                    // Passively learn the direct peer's UUID!
                    // If hopCount is 0, the direct peer is the sender.
                    // If hopCount > 0, the direct peer is the last one in the hopPath.
                    val directPeerUuid = message.hopPath.lastOrNull() ?: message.senderId
                    if (directPeerUuid != transportMsg.senderPeerId) {
                        Log.d(TAG, "Passively learning peer UUID: upgrading ${transportMsg.senderPeerId} to $directPeerUuid")
                        transportManager.upgradePeerId(
                            oldId = transportMsg.senderPeerId,
                            newId = directPeerUuid
                        )
                    }

                    processIncomingMessage(message)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to process incoming message", e)
                }
            }
        }
    }

    private suspend fun processIncomingMessage(message: MeshMessage) {
        val peers = activePeers.value
        val decision = messageRouter.routeMessage(message, peers)

        when (decision) {
            is RoutingDecision.Drop -> {
                // Nothing to do
            }

            is RoutingDecision.Deliver -> {
                // Store for local display
                val delivered = decision.message.copy(status = DeliveryStatus.DELIVERED)
                repository.insertMessage(delivered)

                // Send ACK back if applicable
                decision.ackMessage?.let { ack ->
                    deduplicationManager.markSeen(ack.id)
                    repository.insertMessage(ack)
                    sendViaTransport(ack)
                }
                updateStats()
            }

            is RoutingDecision.Forward -> {
                // Store for relay
                repository.insertMessage(decision.message)
                // Forward to selected peers
                sendToTargets(decision.message, decision.targets)
                updateStats()
            }

            is RoutingDecision.DeliverAndForward -> {
                // Deliver locally
                val delivered = decision.message.copy(status = DeliveryStatus.DELIVERED)
                repository.insertMessage(delivered)
                // Also forward to other peers
                sendToTargets(decision.forwardMessage, decision.targets)
                updateStats()
            }

            is RoutingDecision.AckReceived -> {
                // Update the original message status to DELIVERED
                repository.updateStatus(decision.originalMessageId, DeliveryStatus.DELIVERED)
                Log.d(TAG, "Message ${decision.originalMessageId} confirmed delivered")
                updateStats()
            }

            is RoutingDecision.PeerControl -> {
                // Peer announcements are handled by PeerDiscoveryManager
                Log.d(TAG, "Peer control message: ${decision.message.type}")
            }

            is RoutingDecision.LocationPingReceived -> {
                handleIncomingLocationPing(decision.message)
            }

            is RoutingDecision.SOSReceived -> {
                val delivered = decision.message.copy(status = DeliveryStatus.DELIVERED)
                repository.insertMessage(delivered)
                _incomingSOS.emit(delivered)
                Log.d(TAG, "SOS received from ${decision.message.senderId}")
                updateStats()
            }

            is RoutingDecision.CallSignalReceived -> {
                scope.launch {
                    _incomingCallSignals.emit(decision.message)
                }
                Log.d(TAG, "Call signal received: ${decision.message.type} from ${decision.message.senderId}")
            }
        }
    }

    // ─── Internal: Transport Sending ────────────────────────────

    private suspend fun sendViaTransport(message: MeshMessage) {
        try {
            val serialized = json.encodeToString(message).toByteArray(Charsets.UTF_8)

            if (message.targetId != null) {
                // Targeted: try direct send first, then broadcast
                val directSuccess = transportManager.sendToPeer(message.targetId, serialized)
                if (directSuccess) {
                    repository.updateStatus(message.id, DeliveryStatus.SENT)
                } else {
                    // Peer not directly reachable, broadcast for relay
                    val exclude = message.hopPath.toSet()
                    transportManager.broadcastToAll(serialized, exclude)
                    repository.updateStatus(message.id, DeliveryStatus.SENT)
                }
            } else {
                // Broadcast
                val exclude = message.hopPath.toSet()
                transportManager.broadcastToAll(serialized, exclude)
                repository.updateStatus(message.id, DeliveryStatus.SENT)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Transport send failed for ${message.id}", e)
            repository.updateStatus(message.id, DeliveryStatus.FAILED)
        }
    }

    private suspend fun sendToTargets(message: MeshMessage, targets: List<MeshPeer>) {
        val serialized = json.encodeToString(message).toByteArray(Charsets.UTF_8)

        for (peer in targets) {
            scope.launch {
                try {
                    transportManager.sendToPeer(peer.deviceId, serialized)
                } catch (e: Exception) {
                    Log.e(TAG, "Forward to ${peer.deviceId} failed", e)
                }
            }
        }

        repository.updateStatus(message.id, DeliveryStatus.SENT)
    }

    // ─── Internal: Store-and-Forward Loop ───────────────────────

    private fun startStoreAndForward() {
        scope.launch {
            while (isActive && isRunning) {
                delay(STORE_FORWARD_INTERVAL_MS)
                retryPendingMessages()
            }
        }
    }

    private suspend fun retryPendingMessages() {
        try {
            val pending = repository.getQueuedForRelay()
            if (pending.isEmpty()) return

            val peers = activePeers.value
            if (peers.isEmpty()) return

            Log.d(TAG, "Store-and-forward: ${pending.size} pending, ${peers.size} peers")

            for (message in pending) {
                val newPeers = peers.filter { it.deviceId !in message.hopPath }
                if (newPeers.isNotEmpty()) {
                    sendToTargets(message, newPeers)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Store-and-forward error", e)
        }
    }

    // ─── Internal: Maintenance Loop ─────────────────────────────

    private fun startMaintenanceLoop() {
        scope.launch {
            while (isActive && isRunning) {
                delay(MAINTENANCE_INTERVAL_MS)
                try {
                    // Clean up old deduplication records
                    deduplicationManager.cleanup()
                    // Clean up expired messages
                    repository.cleanupExpired()
                    updateStats()
                } catch (e: Exception) {
                    Log.e(TAG, "Maintenance error", e)
                }
            }
        }
    }

    // ─── Internal: ACK Timeout Checker ──────────────────────────

    private fun startAckTimeoutChecker() {
        scope.launch {
            while (isActive && isRunning) {
                delay(ACK_CHECK_INTERVAL_MS)
                try {
                    val timedOut = ackTracker.getTimedOutAcks()
                    for (messageId in timedOut) {
                        // Mark as SENT (delivered to relay, but unconfirmed at target)
                        repository.updateStatus(messageId, DeliveryStatus.SENT)
                    }
                    if (timedOut.isNotEmpty()) {
                        updateStats()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "ACK timeout check error", e)
                }
            }
        }
    }

    // ─── Internal: Stats ────────────────────────────────────────

    private suspend fun updateStats() {
        try {
            _meshStats.value = MeshStats(
                totalPeers = activePeers.value.size,
                activePeers = activePeers.value.count { it.isConnected },
                messagesSent = repository.countByStatus(DeliveryStatus.SENT) +
                        repository.countByStatus(DeliveryStatus.DELIVERED),
                messagesRelayed = 0, // TODO: track separately
                messagesDelivered = repository.countByStatus(DeliveryStatus.DELIVERED),
                pendingMessages = repository.countByStatus(DeliveryStatus.QUEUED),
                seenMessageCount = deduplicationManager.cacheSize()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Stats update failed", e)
        }
    }

    // ─── Internal: Location Ping Handling ──────────────────────

    /**
     * Process an incoming LOCATION_PING message.
     * Updates the sender's row in the peers table with their GPS coordinates.
     *
     * Guards:
     * - Drops pings older than 15 minutes (900 seconds)
     * - Never updates our own location from an echoed-back ping
     * - LOCATION_SHARE messages must NEVER call this method
     */
    private fun handleIncomingLocationPing(message: MeshMessage) {
        val payload = try {
            json.decodeFromString<LocationPingPayload>(message.payload)
        } catch (e: Exception) {
            Log.w(TAG, "Malformed LOCATION_PING payload — dropping", e)
            return  // malformed payload — drop silently, do not crash
        }

        // Freshness gate — a 15-minute-old location is misleading on a live map
        val ageSeconds = (System.currentTimeMillis() / 1000) - payload.timestampEpochSec
        if (ageSeconds > 900) {
            Log.d(TAG, "Dropping stale LOCATION_PING (${ageSeconds}s old)")
            return
        }

        // Never update our own location from an echoed-back ping
        if (payload.senderId == deviceId) return

        // Update peer DB with their location
        try {
            PeerQueries.updatePeerLocation(
                db = DatabaseProvider.db,
                deviceId = payload.senderId,
                latitude = payload.lat,
                longitude = payload.lon,
                accuracyMeters = payload.accuracyMeters,
                updatedAt = payload.timestampEpochSec * 1000
            )
            Log.d(TAG, "Updated peer location: ${payload.senderId} → (${payload.lat}, ${payload.lon})")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update peer location", e)
        }
    }

    // ─── Internal: Encryption ───────────────────────────────────

    private fun encryptPayload(plaintext: String): String? {
        val key = KeyManager.getKey(context) ?: return null
        return CryptoManager.encryptString(plaintext, key)
    }
}
