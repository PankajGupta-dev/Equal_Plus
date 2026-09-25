package com.bridgefy.app.transport.wifidirect

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.NetworkInfo
import android.net.wifi.WpsInfo
import android.net.wifi.p2p.*
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import com.bridgefy.app.model.MeshPeer
import com.bridgefy.app.model.TransportType
import com.bridgefy.app.transport.ConnectionEvent
import com.bridgefy.app.transport.Transport
import com.bridgefy.app.transport.TransportMessage
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.*
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import com.bridgefy.app.transfer.FileTransferManager
import java.util.concurrent.ConcurrentHashMap

/**
 * WiFi Direct transport implementation for AlertNet mesh.
 *
 * Handles high-throughput data transfer including large files (up to 20MB).
 * Uses a length-prefixed binary framing protocol for reliable messaging:
 *
 * Frame format: [4 bytes: payload length (big-endian)] [N bytes: payload]
 *
 * Responsibilities:
 * - Peer discovery via WifiP2pManager
 * - P2P connection management (handles both group owner and client roles)
 * - Persistent server socket for incoming connections
 * - Client connections for outgoing messages
 * - Streaming file transfer without loading into memory
 */
class WiFiDirectTransport(
    private val context: Context,
    private val deviceId: String
) : Transport {

    companion object {
        private const val TAG = "WiFiDirectTransport"
        const val PORT = 8888
        private const val SOCKET_TIMEOUT_MS = 2_000
        private const val DISCOVERY_INTERVAL_MS = 10_000L
        private const val MAX_PAYLOAD_SIZE = 20 * 1024 * 1024 // 20 MB
    }

    override val transportType = TransportType.WIFI_DIRECT

    private val _discoveredPeers = MutableStateFlow<List<MeshPeer>>(emptyList())
    override val discoveredPeers: StateFlow<List<MeshPeer>> = _discoveredPeers.asStateFlow()

    private val _incomingMessages = MutableSharedFlow<TransportMessage>(extraBufferCapacity = 64)
    override val incomingMessages: SharedFlow<TransportMessage> = _incomingMessages.asSharedFlow()

    private val _connectionEvents = MutableSharedFlow<ConnectionEvent>(extraBufferCapacity = 16)
    override val connectionEvents: SharedFlow<ConnectionEvent> = _connectionEvents.asSharedFlow()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val peersMap = ConcurrentHashMap<String, MeshPeer>()

    /** Maps device address to IP for connected peers */
    private val peerIpMap = ConcurrentHashMap<String, String>()

    private var wifiP2pManager: WifiP2pManager? = null
    private var channel: WifiP2pManager.Channel? = null
    private var receiver: BroadcastReceiver? = null
    private var serverJob: Job? = null
    private var discoveryJob: Job? = null
    private var isRunning = false
    private var isGroupOwner = false
    private var groupOwnerAddress: String? = null

    @Volatile
    private var connectingMacAddress: String? = null

    /** Set by MeshManager after construction to enable binary file transfers */
    var fileTransferManager: FileTransferManager? = null

    // ─── Lifecycle ───────────────────────────────────────────────

    override suspend fun start() {
        if (isRunning) return

        wifiP2pManager = context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
        if (wifiP2pManager == null) {
            Log.e(TAG, "WiFi Direct not supported")
            _connectionEvents.emit(
                ConnectionEvent.TransportError(UnsupportedOperationException("WiFi Direct not supported"))
            )
            return
        }

        channel = wifiP2pManager!!.initialize(context, context.mainLooper, null)
        isRunning = true

        registerReceiver()
        startServerSocket()
        // Discovery is now controlled externally by PeerDiscoveryManager
        // via startDiscovery() / stopDiscovery()

        Log.d(TAG, "WiFi Direct transport started")
    }

    override suspend fun stop() {
        isRunning = false
        discoveryJob?.cancel()
        serverJob?.cancel()
        unregisterReceiver()
        scope.coroutineContext.cancelChildren()
        peersMap.clear()
        peerIpMap.clear()
        _discoveredPeers.value = emptyList()
        Log.d(TAG, "WiFi Direct transport stopped")
    }

    // ─── Broadcast Receiver ─────────────────────────────────────

    private fun registerReceiver() {
        val intentFilter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
        }

        receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                when (intent.action) {
                    WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                        val state = intent.getIntExtra(
                            WifiP2pManager.EXTRA_WIFI_STATE,
                            WifiP2pManager.WIFI_P2P_STATE_DISABLED
                        )
                        if (state != WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
                            Log.w(TAG, "WiFi Direct is disabled")
                        }
                    }

                    WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                        requestPeerList()
                    }

                    WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                        @Suppress("DEPRECATION")
                        val networkInfo = intent.getParcelableExtra<NetworkInfo>(
                            WifiP2pManager.EXTRA_NETWORK_INFO
                        )
                        if (networkInfo?.isConnected == true) {
                            requestConnectionInfo()
                        } else {
                            Log.d(TAG, "Disconnected from WiFi Direct group")
                            isGroupOwner = false
                            groupOwnerAddress = null
                            connectingMacAddress = null
                        }
                    }
                }
            }
        }

        context.registerReceiver(receiver, intentFilter)
    }

    private fun unregisterReceiver() {
        try {
            receiver?.let { context.unregisterReceiver(it) }
        } catch (e: Exception) {
            Log.w(TAG, "Receiver already unregistered")
        }
        receiver = null
    }

    // ─── Peer Discovery ─────────────────────────────────────────

    /**
     * Start WiFi Direct discovery. Called by PeerDiscoveryManager.
     */
    override fun startDiscovery() {
        if (!isRunning) return
        discoverPeers()
        Log.d(TAG, "WiFi Direct discovery started (externally controlled)")
    }

    /**
     * Stop WiFi Direct discovery. Called by PeerDiscoveryManager.
     */
    @SuppressLint("MissingPermission")
    override fun stopDiscovery() {
        if (!hasPermissions()) return
        wifiP2pManager?.stopPeerDiscovery(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d(TAG, "WiFi Direct discovery stopped")
            }
            override fun onFailure(reason: Int) {
                Log.w(TAG, "Failed to stop WiFi Direct discovery: $reason")
            }
        })
    }

    @SuppressLint("MissingPermission")
    private fun discoverPeers() {
        if (!hasPermissions()) return

        wifiP2pManager?.discoverPeers(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d(TAG, "Peer discovery started")
            }

            override fun onFailure(reason: Int) {
                Log.e(TAG, "Peer discovery failed: $reason")
            }
        })
    }

    @SuppressLint("MissingPermission")
    private fun requestPeerList() {
        if (!hasPermissions()) return

        wifiP2pManager?.requestPeers(channel) { peers ->
            val meshPeers = peers.deviceList.map { device ->
                MeshPeer(
                    deviceId = device.deviceAddress,
                    displayName = device.deviceName.ifEmpty { "WiFi-${device.deviceAddress.takeLast(5)}" },
                    lastSeen = System.currentTimeMillis(),
                    transportType = TransportType.WIFI_DIRECT,
                    macAddress = device.deviceAddress,
                    isConnected = device.status == WifiP2pDevice.CONNECTED
                )
            }

            for (peer in meshPeers) {
                // Check if a UUID-keyed peer already exists with this MAC address.
                // This happens after a handshake upgrades a MAC → UUID entry.
                // Without this check, both entries coexist and cause duplicates.
                val existingByMac = peersMap.values.find {
                    it.macAddress == peer.macAddress && it.deviceId != peer.deviceId
                }
                if (existingByMac != null) {
                    // Update the existing UUID-keyed peer with fresh discovery data
                    peersMap[existingByMac.deviceId] = existingByMac.copy(
                        lastSeen = peer.lastSeen,
                        isConnected = peer.isConnected || existingByMac.isConnected
                    )
                    // Don't add the MAC-keyed duplicate
                } else {
                    peersMap[peer.deviceId] = peer
                }
            }
            _discoveredPeers.value = peersMap.values.toList()

            // NOTE: No auto-connect here. Connection is user-initiated only
            // via connectToPeerByAddress() called from MeshManager.
        }
    }

    /**
     * Initiate a WiFi Direct P2P connection to a peer by device address.
     *
     * This is called when the user explicitly taps a device in the list.
     * Returns true if the connection intent was accepted by the framework,
     * false if it was rejected (e.g., missing permissions, busy, etc.).
     *
     * Note: `onSuccess` means the framework accepted the connect request,
     * NOT that the P2P group is fully formed. The actual group formation
     * arrives asynchronously via WIFI_P2P_CONNECTION_CHANGED_ACTION.
     */
    @SuppressLint("MissingPermission")
    suspend fun connectToPeerByAddress(deviceAddress: String): Boolean {
        if (!hasPermissions()) return false

        connectingMacAddress = deviceAddress

        return suspendCancellableCoroutine { cont ->
            val config = WifiP2pConfig().apply {
                this.deviceAddress = deviceAddress
                this.wps.setup = WpsInfo.PBC
            }

            wifiP2pManager?.connect(channel, config, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Log.d(TAG, "Connection initiated to $deviceAddress")
                    if (cont.isActive) cont.resume(true)
                }

                override fun onFailure(reason: Int) {
                    Log.e(TAG, "Connection to $deviceAddress failed: reason=$reason")
                    connectingMacAddress = null
                    if (cont.isActive) cont.resume(false)
                }
            }) ?: run {
                // wifiP2pManager is null
                connectingMacAddress = null
                if (cont.isActive) cont.resume(false)
            }
        }
    }

    private fun requestConnectionInfo() {
        wifiP2pManager?.requestConnectionInfo(channel) { info ->
            if (info.groupFormed) {
                isGroupOwner = info.isGroupOwner
                groupOwnerAddress = info.groupOwnerAddress?.hostAddress

                Log.d(TAG, "Group formed. isGroupOwner=$isGroupOwner, ownerAddr=$groupOwnerAddress")

                if (!isGroupOwner && groupOwnerAddress != null) {
                    // As a client, we know the GO's IP. Register it.
                    val mac = connectingMacAddress
                    if (mac != null) {
                        peerIpMap[mac] = groupOwnerAddress!!
                        val existing = peersMap[mac]
                        if (existing != null) {
                            peersMap[mac] = existing.copy(ipAddress = groupOwnerAddress, isConnected = true)
                            _discoveredPeers.value = peersMap.values.toList()
                        }
                    } else {
                        peerIpMap["group_owner"] = groupOwnerAddress!!
                    }
                    scope.launch {
                        // Send our device ID to the group owner
                        sendDeviceIdHandshake(groupOwnerAddress!!)
                        _connectionEvents.emit(
                            ConnectionEvent.PeerConnected(
                                MeshPeer(
                                    deviceId = "group_owner",
                                    displayName = "Group Owner",
                                    transportType = TransportType.WIFI_DIRECT,
                                    ipAddress = groupOwnerAddress,
                                    isConnected = true
                                )
                            )
                        )
                    }
                }
            }
        }
    }

    // ─── Server Socket ──────────────────────────────────────────

    private fun startServerSocket() {
        serverJob = scope.launch {
            var serverSocket: ServerSocket? = null
            try {
                serverSocket = ServerSocket(PORT)
                serverSocket.reuseAddress = true
                Log.d(TAG, "Server socket listening on port $PORT")

                while (isActive && isRunning) {
                    try {
                        val socket = serverSocket.accept()
                        launch { handleClientConnection(socket) }
                    } catch (e: Exception) {
                        if (isActive && isRunning) {
                            Log.e(TAG, "Error accepting connection", e)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Server socket failed", e)
                _connectionEvents.emit(
                    ConnectionEvent.TransportError(e, "Server socket failed: ${e.message}")
                )
            } finally {
                try { serverSocket?.close() } catch (_: Exception) {}
            }
        }
    }

    /**
     * Handle an incoming client connection with type-byte dispatch.
     *
     * Protocol:
     * 1. Read 1 byte → frame type (0x00 = JSON, 0x01 = binary file)
     * 2. Dispatch to appropriate handler
     *
     * Legacy support: If the first byte looks like part of a 4-byte int
     * (i.e., old protocol without type prefix), fall back to JSON handling.
     */
    private suspend fun handleClientConnection(socket: Socket) {
        val senderIP = socket.inetAddress?.hostAddress ?: "unknown"

        try {
            val inputStream = DataInputStream(BufferedInputStream(socket.getInputStream()))

            // Read type byte
            val typeByte = inputStream.readByte().toInt() and 0xFF

            when (typeByte) {
                0x00 -> handleJsonMessage(inputStream, senderIP)
                0x01 -> handleBinaryTransfer(inputStream, senderIP)
                else -> {
                    // Legacy compatibility: treat as first byte of a 4-byte length
                    Log.w(TAG, "Unknown type byte $typeByte from $senderIP, trying legacy parse")
                    handleLegacyMessage(typeByte, inputStream, senderIP)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling connection from $senderIP", e)
        } finally {
            try { socket.close() } catch (_: Exception) {}
        }
    }

    /**
     * Handle a JSON-framed message (type byte 0x00).
     * Frame: [4-byte length][JSON payload]
     */
    private suspend fun handleJsonMessage(inputStream: DataInputStream, senderIP: String) {
        val length = inputStream.readInt()

        if (length <= 0 || length > MAX_PAYLOAD_SIZE) {
            Log.e(TAG, "Invalid payload length: $length from $senderIP")
            return
        }

        val data = ByteArray(length)
        inputStream.readFully(data)

        Log.d(TAG, "Received $length bytes (JSON) from $senderIP")

        // Check if it's a handshake
        val text = String(data, Charsets.UTF_8)
        if (text.startsWith("MESH_ID:")) {
            handleHandshake(text, senderIP)
            return
        }

        // Regular message
        val senderId = peerIpMap.entries.find { it.value == senderIP }?.key ?: senderIP
        _incomingMessages.emit(
            TransportMessage(
                data = data,
                senderPeerId = senderId,
                transportType = TransportType.WIFI_DIRECT
            )
        )
    }

    /**
     * Handle a binary file transfer (type byte 0x01).
     * Delegates to [FileTransferManager] for chunked receive.
     */
    private suspend fun handleBinaryTransfer(inputStream: DataInputStream, senderIP: String) {
        val ftm = fileTransferManager
        if (ftm == null) {
            Log.e(TAG, "Binary transfer received but FileTransferManager not set")
            return
        }
        Log.d(TAG, "Binary transfer incoming from $senderIP")
        ftm.handleIncomingTransfer(inputStream, senderIP)
    }

    /**
     * Legacy fallback: reconstruct the 4-byte length from the type byte + 3 more bytes.
     * Supports old peers that don't send the type prefix.
     */
    private suspend fun handleLegacyMessage(firstByte: Int, inputStream: DataInputStream, senderIP: String) {
        // Reconstruct length: firstByte is the MSB of a 4-byte big-endian int
        val b2 = inputStream.readByte().toInt() and 0xFF
        val b3 = inputStream.readByte().toInt() and 0xFF
        val b4 = inputStream.readByte().toInt() and 0xFF
        val length = (firstByte shl 24) or (b2 shl 16) or (b3 shl 8) or b4

        if (length <= 0 || length > MAX_PAYLOAD_SIZE) {
            Log.e(TAG, "Invalid legacy payload length: $length from $senderIP")
            return
        }

        val data = ByteArray(length)
        inputStream.readFully(data)

        val text = String(data, Charsets.UTF_8)
        if (text.startsWith("MESH_ID:")) {
            handleHandshake(text, senderIP)
            return
        }

        val senderId = peerIpMap.entries.find { it.value == senderIP }?.key ?: senderIP
        _incomingMessages.emit(
            TransportMessage(
                data = data,
                senderPeerId = senderId,
                transportType = TransportType.WIFI_DIRECT
            )
        )
    }

    /**
     * Process a MESH_ID handshake from a peer.
     */
    private suspend fun handleHandshake(text: String, senderIP: String) {
        val meshId = text.removePrefix("MESH_ID:")
        peerIpMap[meshId] = senderIP
        Log.d(TAG, "Peer $meshId registered at $senderIP")

        var existingPeer = peersMap.values.find { it.ipAddress == senderIP }
        if (existingPeer == null && !isGroupOwner && senderIP == groupOwnerAddress) {
            existingPeer = peersMap.values.find { it.isConnected && it.transportType == TransportType.WIFI_DIRECT }
        }
        if (existingPeer == null && connectingMacAddress != null) {
            existingPeer = peersMap[connectingMacAddress]
        }

        val peer = if (existingPeer != null) {
            peersMap.remove(existingPeer.deviceId)
            if (existingPeer.macAddress != null) {
                peerIpMap[existingPeer.macAddress] = senderIP
            }
            existingPeer.copy(deviceId = meshId, ipAddress = senderIP, isConnected = true)
        } else {
            MeshPeer(
                deviceId = meshId,
                displayName = "Peer-${meshId.take(8)}",
                transportType = TransportType.WIFI_DIRECT,
                ipAddress = senderIP,
                isConnected = true
            )
        }

        peersMap[meshId] = peer
        _discoveredPeers.value = peersMap.values.toList()
        _connectionEvents.emit(ConnectionEvent.PeerConnected(peer))

        connectingMacAddress = null

        if (isGroupOwner) {
            scope.launch { sendDeviceIdHandshake(senderIP) }
        }
    }

    // ─── Send Message ───────────────────────────────────────────

    override suspend fun sendMessage(peerId: String, data: ByteArray): Boolean {
        if (data.size > MAX_PAYLOAD_SIZE) {
            Log.e(TAG, "Payload exceeds 20MB limit: ${data.size} bytes")
            return false
        }

        return withContext(Dispatchers.IO) {
            try {
                val targetIP = resolveIP(peerId) ?: run {
                    Log.w(TAG, "Cannot resolve IP for peer: $peerId")
                    return@withContext false
                }

                val socket = Socket()
                socket.connect(InetSocketAddress(targetIP, PORT), SOCKET_TIMEOUT_MS)

                val outputStream = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))

                // Write type byte + length-prefixed frame
                outputStream.writeByte(0x00) // JSON message type
                outputStream.writeInt(data.size)
                outputStream.write(data)
                outputStream.flush()

                socket.close()
                Log.d(TAG, "Sent ${data.size} bytes to $peerId ($targetIP)")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Send to $peerId failed", e)
                false
            }
        }
    }

    override suspend fun broadcastMessage(data: ByteArray, excludePeers: Set<String>) {
        val targets = peersMap.values
            .filter { it.deviceId !in excludePeers }
            .filter { it.ipAddress != null || peerIpMap.containsKey(it.deviceId) }

        for (peer in targets) {
            scope.launch {
                try {
                    sendMessage(peer.deviceId, data)
                } catch (e: Exception) {
                    Log.e(TAG, "Broadcast to ${peer.deviceId} failed", e)
                }
            }
        }

        // Also send to group owner if we're not the GO
        if (!isGroupOwner && groupOwnerAddress != null) {
            val goId = peerIpMap.entries.find { it.value == groupOwnerAddress }?.key ?: "group_owner"
            if (goId !in excludePeers) {
                scope.launch {
                    try {
                        sendMessage(goId, data)
                    } catch (e: Exception) {
                        Log.e(TAG, "Broadcast to group owner failed", e)
                    }
                }
            }
        }
    }

    override fun upgradePeerId(oldId: String, newId: String) {
        val existing = peersMap[oldId]
        if (existing != null && oldId != newId) {
            peersMap.remove(oldId)
            val updated = existing.copy(deviceId = newId)
            peersMap[newId] = updated
            _discoveredPeers.value = peersMap.values.toList()
            Log.d(TAG, "Upgraded WiFi Direct peer ID from $oldId to $newId")
        }
    }

    /**
     * Send device ID handshake to a newly connected peer.
     */
    private suspend fun sendDeviceIdHandshake(targetIP: String) {
        withContext(Dispatchers.IO) {
            try {
                val handshake = "MESH_ID:$deviceId".toByteArray(Charsets.UTF_8)
                val socket = Socket()
                socket.connect(InetSocketAddress(targetIP, PORT), SOCKET_TIMEOUT_MS)

                val outputStream = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))
                outputStream.writeByte(0x00) // JSON message type
                outputStream.writeInt(handshake.size)
                outputStream.write(handshake)
                outputStream.flush()
                socket.close()

                Log.d(TAG, "Sent device ID handshake to $targetIP")
            } catch (e: Exception) {
                Log.e(TAG, "Handshake to $targetIP failed", e)
            }
        }
    }

    // ─── Binary File Transfer ────────────────────────────────────

    /**
     * Stream a binary file to a peer using the chunked binary protocol.
     *
     * Wire format: [0x01][4-byte header len][header JSON][raw file bytes]
     *
     * @param peerId Target peer's device ID
     * @param header Serialized [FileTransferHeader] JSON bytes
     * @param fileStream InputStream of the file to send
     * @param fileSize Total file size in bytes
     * @param onProgress Callback with bytes sent so far
     * @return true if the entire file was sent successfully
     */
    suspend fun sendBinaryTransfer(
        peerId: String,
        header: ByteArray,
        fileStream: java.io.InputStream,
        fileSize: Long,
        onProgress: (Long) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        val targetIP = resolveIP(peerId) ?: run {
            Log.w(TAG, "Cannot resolve IP for binary transfer to: $peerId")
            fileStream.close()
            return@withContext false
        }

        try {
            val socket = Socket()
            socket.connect(InetSocketAddress(targetIP, PORT), 30_000) // 30s timeout for large files
            socket.sendBufferSize = 131072 // 128KB send buffer for throughput

            val outputStream = DataOutputStream(BufferedOutputStream(socket.getOutputStream(), 131072))

            // Type byte
            outputStream.writeByte(0x01)

            // Header
            outputStream.writeInt(header.size)
            outputStream.write(header)

            val isVideo = String(header, Charsets.UTF_8).contains("\"messageType\":\"VIDEO\"")
            if (isVideo) {
                Log.d("VideoDebug", "[VideoDebug] Socket connected")
            }

            // Stream file in chunks
            val buffer = ByteArray(65536) // 64KB chunks
            var totalSent = 0L
            var chunkIndex = 0
            val totalChunks = ((fileSize + 65535) / 65536).toInt()

            while (totalSent < fileSize) {
                val read = fileStream.read(buffer)
                if (read == -1) break
                outputStream.write(buffer, 0, read)
                totalSent += read
                chunkIndex++
                if (isVideo) {
                    Log.d("VideoDebug", "[VideoDebug] Sending chunk $chunkIndex/$totalChunks")
                }
                onProgress(totalSent)
            }

            outputStream.flush()
            fileStream.close()

            // BUG FIX: Verify we sent the exact number of bytes expected
            if (totalSent != fileSize) {
                Log.e(TAG, "SEND SIZE MISMATCH: sent=$totalSent expected=$fileSize to $peerId")
                socket.close()
                return@withContext false
            }

            // BUG FIX: Gracefully signal end-of-stream so the receiver's
            // readFully() doesn't get an abrupt RST.
            try {
                socket.soTimeout = 15000 // 15s timeout to wait for receiver completion
                socket.shutdownOutput()
                // Wait for the receiver to completely read all bytes and close the connection
                socket.getInputStream().read()
            } catch (e: Exception) {
                Log.w(TAG, "Graceful socket shutdown wait interrupted: ${e.message}")
            }
            socket.close()

            if (isVideo) {
                Log.d("VideoDebug", "[VideoDebug] Transfer completed")
            }
            Log.d(TAG, "Binary transfer complete: $totalSent bytes to $peerId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Binary transfer to $peerId failed", e)
            try { fileStream.close() } catch (_: Exception) {}
            false
        }
    }

    /**
     * Resolve a peer's device ID to its IP address.
     */
    private fun resolveIP(peerId: String): String? {
        // Direct IP map lookup
        peerIpMap[peerId]?.let { return it }

        // Check if peerId IS an IP address
        if (peerId.matches(Regex("\\d+\\.\\d+\\.\\d+\\.\\d+"))) {
            return peerId
        }

        // Check peer model for stored IP
        peersMap[peerId]?.ipAddress?.let { return it }

        // If we're the client and the peer might be the group owner
        if (!isGroupOwner) {
            return groupOwnerAddress
        }

        return null
    }

    private fun hasPermissions(): Boolean {
        val hasLocation = ActivityCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val hasNearby = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.checkSelfPermission(
                context, Manifest.permission.NEARBY_WIFI_DEVICES
            ) == PackageManager.PERMISSION_GRANTED
        } else true

        return hasLocation && hasNearby
    }

    // ─── Audio Socket Transport ──────────────────────────────────

    private var audioServerSocket: ServerSocket? = null
    private var audioServerJob: Job? = null

    fun startAudioServer(onConnection: (Socket) -> Unit) {
        stopAudioServer()
        audioServerJob = scope.launch(Dispatchers.IO) {
            try {
                val server = ServerSocket(8889)
                audioServerSocket = server
                server.reuseAddress = true
                Log.d(TAG, "Audio server socket listening on port 8889")
                while (isActive && isRunning) {
                    val socket = server.accept().apply { tcpNoDelay = true }
                    Log.d(TAG, "Audio server accepted connection from ${socket.inetAddress?.hostAddress}")
                    onConnection(socket)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Audio server socket exception: ${e.message}")
            } finally {
                try { audioServerSocket?.close() } catch (_: Exception) {}
                audioServerSocket = null
            }
        }
    }

    fun stopAudioServer() {
        audioServerJob?.cancel()
        audioServerJob = null
        try {
            audioServerSocket?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing audio server socket", e)
        }
        audioServerSocket = null
    }

    suspend fun connectAudioSocket(peerId: String): Socket? = withContext(Dispatchers.IO) {
        val targetIP = resolveIP(peerId) ?: run {
            Log.w(TAG, "Cannot resolve IP for audio socket to peer: $peerId")
            return@withContext null
        }
        try {
            val socket = Socket().apply { tcpNoDelay = true }
            socket.connect(InetSocketAddress(targetIP, 8889), 5000)
            Log.d(TAG, "Audio socket connected to $peerId ($targetIP) on port 8889")
            socket
        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect audio socket to $peerId ($targetIP) on port 8889", e)
            null
        }
    }

    // ─── Video Socket Transport ──────────────────────────────────

    private var videoServerSocket: ServerSocket? = null
    private var videoServerJob: Job? = null

    fun startVideoServer(onConnection: (Socket) -> Unit) {
        stopVideoServer()
        videoServerJob = scope.launch(Dispatchers.IO) {
            try {
                val server = ServerSocket(8890)
                videoServerSocket = server
                server.reuseAddress = true
                Log.d(TAG, "Video server socket listening on port 8890")
                while (isActive && isRunning) {
                    val socket = server.accept().apply { tcpNoDelay = true }
                    Log.d(TAG, "Video server accepted connection from ${socket.inetAddress?.hostAddress}")
                    onConnection(socket)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Video server socket exception: ${e.message}")
            } finally {
                try { videoServerSocket?.close() } catch (_: Exception) {}
                videoServerSocket = null
            }
        }
    }

    fun stopVideoServer() {
        videoServerJob?.cancel()
        videoServerJob = null
        try {
            videoServerSocket?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing video server socket", e)
        }
        videoServerSocket = null
    }

    suspend fun connectVideoSocket(peerId: String): Socket? = withContext(Dispatchers.IO) {
        val targetIP = resolveIP(peerId) ?: run {
            Log.w(TAG, "Cannot resolve IP for video socket to peer: $peerId")
            return@withContext null
        }
        try {
            val socket = Socket().apply { tcpNoDelay = true }
            socket.connect(InetSocketAddress(targetIP, 8890), 5000)
            Log.d(TAG, "Video socket connected to $peerId ($targetIP) on port 8890")
            socket
        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect video socket to $peerId ($targetIP) on port 8890", e)
            null
        }
    }
}

