package com.bridgefy.app.transport.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import androidx.core.app.ActivityCompat
import com.bridgefy.app.mesh.SosDistanceHelper
import com.bridgefy.app.model.DeliveryStatus
import com.bridgefy.app.model.MeshMessage
import com.bridgefy.app.model.MeshPeer
import com.bridgefy.app.model.MessageType
import com.bridgefy.app.model.TransportType
import com.bridgefy.app.transport.ConnectionEvent
import com.bridgefy.app.transport.Transport
import com.bridgefy.app.transport.TransportMessage
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

/**
 * BLE transport implementation for AlertNet mesh.
 *
 * Responsibilities:
 * - Advertise this device's AlertNet identity via GATT server
 *   (service data contains 8-char UUID prefix, IDENTITY_CHARACTERISTIC
 *    returns full "ALERTNET:<uuid>:<username>" on read)
 * - Scan for nearby AlertNet peers via BLE scanning
 * - Filter out non-AlertNet devices: only peers that broadcast our
 *   MESH_SERVICE_UUID are candidates, and only those whose GATT
 *   IDENTITY_CHARACTERISTIC returns a valid ALERTNET payload are shown
 * - Exchange small messages (<500 bytes) via GATT characteristics
 * - For larger payloads, the TransportManager will prefer WiFi Direct
 *
 * Battery optimization:
 * - Duty-cycled scanning: controlled externally by PeerDiscoveryManager
 * - Low-power scan mode
 * - Advertising uses low-latency mode only when actively sending
 */
class BleTransport(
    private val context: Context,
    private val deviceId: String,
    private val username: String
) : Transport {

    companion object {
        private const val TAG = "BleTransport"
    }

    override val transportType = TransportType.BLE

    private val _discoveredPeers = MutableStateFlow<List<MeshPeer>>(emptyList())
    override val discoveredPeers: StateFlow<List<MeshPeer>> = _discoveredPeers.asStateFlow()

    private val _incomingMessages = MutableSharedFlow<TransportMessage>(extraBufferCapacity = 64)
    override val incomingMessages: SharedFlow<TransportMessage> = _incomingMessages.asSharedFlow()

    private val _connectionEvents = MutableSharedFlow<ConnectionEvent>(extraBufferCapacity = 16)
    override val connectionEvents: SharedFlow<ConnectionEvent> = _connectionEvents.asSharedFlow()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val peersMap = ConcurrentHashMap<String, MeshPeer>()

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bleScanner: BluetoothLeScanner? = null
    private var bleAdvertiser: BluetoothLeAdvertiser? = null
    private var gattServer: BluetoothGattServer? = null
    private var isRunning = false
    private var scanJob: Job? = null

    /**
     * Tracks MAC addresses for which a GATT identity read is already in-flight,
     * preventing duplicate connections to the same device during a scan window.
     */
    private val pendingIdentityReads = ConcurrentHashMap.newKeySet<String>()

    private val json = Json { ignoreUnknownKeys = true }
    private val recentSosBeacons = ConcurrentHashMap<String, Long>()

    /**
     * Tracks all BLE devices recently detected in radio range (<= 15m radius).
     * Used for immediate zero-handshake SOS delivery to unconnected nearby phones.
     */
    data class ScannedDeviceInfo(
        val device: BluetoothDevice,
        val rssi: Int,
        val timestamp: Long
    )
    private val nearbyScannedBleDevices = ConcurrentHashMap<String, ScannedDeviceInfo>()

    /** The full AlertNet identity string served via GATT */
    private val identityPayload: String
        get() = "${BleConstants.IDENTITY_PREFIX}:$deviceId:$username"

    // ─── Lifecycle ───────────────────────────────────────────────

    override suspend fun start() {
        if (isRunning) return

        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        bluetoothAdapter = bluetoothManager?.adapter

        if (bluetoothAdapter == null || !bluetoothAdapter!!.isEnabled) {
            Log.w(TAG, "Bluetooth not available or disabled")
            _connectionEvents.emit(
                ConnectionEvent.TransportError(IllegalStateException("Bluetooth not available"))
            )
            return
        }

        if (!hasPermissions()) {
            Log.w(TAG, "Missing BLE permissions")
            _connectionEvents.emit(
                ConnectionEvent.TransportError(SecurityException("Missing BLE permissions"))
            )
            return
        }

        bleScanner = bluetoothAdapter!!.bluetoothLeScanner
        bleAdvertiser = bluetoothAdapter!!.bluetoothLeAdvertiser

        isRunning = true
        startGattServer()
        startAdvertising()
        // Scanning is now controlled externally by PeerDiscoveryManager
        // via startDiscovery() / stopDiscovery()

        Log.d(TAG, "BLE transport started (identity: $identityPayload)")
    }

    override suspend fun stop() {
        isRunning = false
        scanJob?.cancel()
        stopAdvertising()
        stopGattServer()
        scope.coroutineContext.cancelChildren()
        peersMap.clear()
        pendingIdentityReads.clear()
        _discoveredPeers.value = emptyList()
        Log.d(TAG, "BLE transport stopped")
    }

    // ─── GATT Server (receive messages + serve identity) ─────────

    @SuppressLint("MissingPermission")
    private fun startGattServer() {
        if (!hasPermissions()) return

        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager

        gattServer = bluetoothManager.openGattServer(context, object : BluetoothGattServerCallback() {

            override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
                val address = device.address ?: return
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        Log.d(TAG, "GATT client connected: $address")
                        scope.launch {
                            val peer = MeshPeer(
                                deviceId = address, // will be updated when we read their mesh ID
                                displayName = device.name ?: "BLE Device",
                                transportType = TransportType.BLE,
                                macAddress = address,
                                isConnected = true
                            )
                            peersMap[address] = peer
                            updatePeersList()
                            _connectionEvents.emit(ConnectionEvent.PeerConnected(peer))
                        }
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        Log.d(TAG, "GATT client disconnected: $address")
                        scope.launch {
                            peersMap.remove(address)
                            updatePeersList()
                            _connectionEvents.emit(ConnectionEvent.PeerDisconnected(address))
                        }
                    }
                }
            }

            override fun onCharacteristicWriteRequest(
                device: BluetoothDevice,
                requestId: Int,
                characteristic: BluetoothGattCharacteristic,
                preparedWrite: Boolean,
                responseNeeded: Boolean,
                offset: Int,
                value: ByteArray
            ) {
                when (characteristic.uuid) {
                    BleConstants.MESSAGE_CHARACTERISTIC -> {
                        Log.d(TAG, "Received ${value.size} bytes from ${device.address}")
                        scope.launch {
                            _incomingMessages.emit(
                                TransportMessage(
                                    data = value,
                                    senderPeerId = device.address ?: "unknown",
                                    transportType = TransportType.BLE
                                )
                            )
                        }
                        if (responseNeeded) {
                            gattServer?.sendResponse(
                                device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null
                            )
                        }
                    }
                    BleConstants.MESH_ID_CHARACTERISTIC -> {
                        // Client writing their mesh ID
                        val meshId = String(value, Charsets.UTF_8)
                        Log.d(TAG, "Peer mesh ID received: $meshId from ${device.address}")
                        val existing = peersMap[device.address]
                        if (existing != null) {
                            peersMap[device.address] = existing.copy(deviceId = meshId)
                            // Also store under the mesh ID
                            peersMap[meshId] = existing.copy(deviceId = meshId)
                            scope.launch { updatePeersList() }
                        }
                        if (responseNeeded) {
                            gattServer?.sendResponse(
                                device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null
                            )
                        }
                    }
                    else -> {
                        if (responseNeeded) {
                            gattServer?.sendResponse(
                                device, requestId, BluetoothGatt.GATT_FAILURE, 0, null
                            )
                        }
                    }
                }
            }

            override fun onCharacteristicReadRequest(
                device: BluetoothDevice,
                requestId: Int,
                offset: Int,
                characteristic: BluetoothGattCharacteristic
            ) {
                when (characteristic.uuid) {
                    BleConstants.MESH_ID_CHARACTERISTIC -> {
                        gattServer?.sendResponse(
                            device, requestId, BluetoothGatt.GATT_SUCCESS,
                            0, deviceId.toByteArray(Charsets.UTF_8)
                        )
                    }
                    BleConstants.IDENTITY_CHARACTERISTIC -> {
                        // Serve full AlertNet identity: "ALERTNET:<uuid>:<username>"
                        val payload = identityPayload.toByteArray(Charsets.UTF_8)
                        // Handle offset reads for large payloads
                        val chunk = if (offset < payload.size) {
                            payload.copyOfRange(offset, payload.size)
                        } else {
                            byteArrayOf()
                        }
                        gattServer?.sendResponse(
                            device, requestId, BluetoothGatt.GATT_SUCCESS, offset, chunk
                        )
                        Log.d(TAG, "Served identity to ${device.address}: $identityPayload")
                    }
                    else -> {
                        gattServer?.sendResponse(
                            device, requestId, BluetoothGatt.GATT_FAILURE, 0, null
                        )
                    }
                }
            }
        })

        // Create GATT service with characteristics
        val service = BluetoothGattService(
            BleConstants.MESH_SERVICE_UUID,
            BluetoothGattService.SERVICE_TYPE_PRIMARY
        )

        val meshIdChar = BluetoothGattCharacteristic(
            BleConstants.MESH_ID_CHARACTERISTIC,
            BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_WRITE,
            BluetoothGattCharacteristic.PERMISSION_READ or BluetoothGattCharacteristic.PERMISSION_WRITE
        )

        val messageChar = BluetoothGattCharacteristic(
            BleConstants.MESSAGE_CHARACTERISTIC,
            BluetoothGattCharacteristic.PROPERTY_WRITE,
            BluetoothGattCharacteristic.PERMISSION_WRITE
        )

        val identityChar = BluetoothGattCharacteristic(
            BleConstants.IDENTITY_CHARACTERISTIC,
            BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ
        )

        service.addCharacteristic(meshIdChar)
        service.addCharacteristic(messageChar)
        service.addCharacteristic(identityChar)
        gattServer?.addService(service)

        Log.d(TAG, "GATT server started with identity characteristic")
    }

    @SuppressLint("MissingPermission")
    private fun stopGattServer() {
        if (!hasPermissions()) return
        gattServer?.close()
        gattServer = null
    }

    // ─── BLE Advertising ────────────────────────────────────────

    @SuppressLint("MissingPermission")
    private fun startAdvertising() {
        if (!hasPermissions() || bleAdvertiser == null) return

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_POWER)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .setConnectable(true)
            .build()

        // Include service UUID + short device ID prefix as service data
        // so scanners can pre-filter before doing a full GATT read
        val idPrefix = deviceId.take(8).toByteArray(Charsets.UTF_8)

        // Split advertisement packet (<= 31 bytes each) into main data and scan response
        // Main data: MESH_SERVICE_UUID (18 bytes <= 31 bytes)
        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false) // save space
            .addServiceUuid(ParcelUuid(BleConstants.MESH_SERVICE_UUID))
            .build()

        // Scan response: Service Data with ID prefix (26 bytes <= 31 bytes)
        val scanResponse = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .addServiceData(ParcelUuid(BleConstants.MESH_SERVICE_UUID), idPrefix)
            .build()

        bleAdvertiser?.startAdvertising(settings, data, scanResponse, advertiseCallback)
        Log.d(TAG, "BLE advertising started with ID prefix: ${deviceId.take(8)}")
    }

    @SuppressLint("MissingPermission")
    private fun stopAdvertising() {
        if (!hasPermissions()) return
        bleAdvertiser?.stopAdvertising(advertiseCallback)
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            Log.d(TAG, "Advertising started successfully")
        }

        override fun onStartFailure(errorCode: Int) {
            Log.e(TAG, "Advertising failed: $errorCode")
            scope.launch {
                _connectionEvents.emit(
                    ConnectionEvent.TransportError(
                        RuntimeException("BLE advertising failed: $errorCode")
                    )
                )
            }
        }
    }

    // ─── BLE Scanning (externally controlled) ───────────────────

    /**
     * Start BLE scanning. Called by PeerDiscoveryManager.
     */
    override fun startDiscovery() {
        if (!isRunning) return
        startScan()
        Log.d(TAG, "BLE discovery started (externally controlled)")
    }

    /**
     * Stop BLE scanning. Called by PeerDiscoveryManager.
     */
    override fun stopDiscovery() {
        stopScan()
        Log.d(TAG, "BLE discovery stopped (externally controlled)")
    }

    @Volatile
    private var isScanning = false

    @SuppressLint("MissingPermission")
    private fun startScan() {
        if (!hasPermissions() || bleScanner == null || isScanning) return

        val filters = listOf(
            ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(BleConstants.MESH_SERVICE_UUID))
                .build(),
            ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(BleConstants.SOS_SERVICE_UUID_16))
                .build(),
            ScanFilter.Builder()
                .setManufacturerData(BleConstants.SOS_MANUFACTURER_ID, null)
                .build()
        )

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
            .build()

        try {
            bleScanner?.startScan(filters, settings, scanCallback)
            isScanning = true
            Log.d(TAG, "BLE scan started (BALANCED mode with SOS filters)")
        } catch (e: Exception) {
            isScanning = false
            Log.e(TAG, "Failed to start BLE scan", e)
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopScan() {
        if (!hasPermissions() || !isScanning) return
        try {
            bleScanner?.stopScan(scanCallback)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop BLE scan", e)
        } finally {
            isScanning = false
        }
    }

    /**
     * BLE scan callback that implements AlertNet identity filtering.
     *
     * Two-phase discovery:
     * 1. Scan result arrives with our MESH_SERVICE_UUID → candidate found
     * 2. Initiate GATT connect to read IDENTITY_CHARACTERISTIC
     * 3. Parse "ALERTNET:<uuid>:<username>" → only then add to peers list
     *
     * Non-AlertNet devices (headphones, watches, etc.) never pass phase 1
     * because they don't advertise our custom service UUID.
     * Devices that advertise our UUID but fail the GATT identity read
     * are silently ignored.
     */
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device ?: return
            val address = device.address ?: return

            // Record this device as seen recently within 15m radius for direct GATT delivery
            if (SosDistanceHelper.isWithin15Meters(null, null, null, null, result.rssi)) {
                nearbyScannedBleDevices[address] = ScannedDeviceInfo(device, result.rssi, System.currentTimeMillis())
            }

            // Check if this advertisement contains an SOS emergency beacon
            val serviceData = result.scanRecord?.getServiceData(ParcelUuid(BleConstants.MESH_SERVICE_UUID))
                ?: result.scanRecord?.getServiceData(ParcelUuid(BleConstants.SOS_SERVICE_UUID_16))
            val mfgData = result.scanRecord?.getManufacturerSpecificData(BleConstants.SOS_MANUFACTURER_ID)

            var sosPayload: String? = null
            if (mfgData != null) {
                val str = String(mfgData, Charsets.UTF_8)
                if (str.startsWith("SOS:")) {
                    sosPayload = str
                }
            }
            if (sosPayload == null && serviceData != null) {
                val str = String(serviceData, Charsets.UTF_8)
                if (str.startsWith("SOS:")) {
                    sosPayload = str
                }
            }
            if (sosPayload == null) {
                // Check raw advertisement bytes for SOS: pattern
                val rawBytes = result.scanRecord?.bytes
                if (rawBytes != null) {
                    val rawStr = String(rawBytes, Charsets.ISO_8859_1)
                    val idx = rawStr.indexOf("SOS:")
                    if (idx != -1) {
                        sosPayload = rawStr.substring(idx).takeWhile { it.code in 32..126 }
                    }
                }
            }

            if (sosPayload != null) {
                handleIncomingSosBeacon(address, sosPayload, result.rssi)
                return
            }

            if (serviceData != null) {
                val idPrefix = String(serviceData, Charsets.UTF_8)
                Log.d(TAG, "BridgeFy candidate: $address (prefix: $idPrefix, RSSI: ${result.rssi})")
            }

            // Check if we already know this peer's identity
            val existingByMac = peersMap.values.find { it.macAddress == address }
            if (existingByMac?.bridgefyId != null || existingByMac?.alertnetId != null) {
                // Already identified — just update RSSI and lastSeen
                val key = existingByMac.bridgefyId ?: existingByMac.alertnetId!!
                peersMap[key] = existingByMac.copy(
                    rssi = result.rssi,
                    lastSeen = System.currentTimeMillis()
                )
                scope.launch { updatePeersList() }
                return
            }

            // Avoid duplicate GATT reads for the same MAC
            if (!pendingIdentityReads.add(address)) {
                return
            }

            // Phase 2: GATT connect to read full identity
            scope.launch {
                readAlertNetIdentity(device, result.rssi)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "BLE scan failed: $errorCode")
        }
    }

    /**
     * Connect to a BLE device via GATT and read its IDENTITY_CHARACTERISTIC
     * to get the full "ALERTNET:<uuid>:<username>" payload.
     *
     * Only adds the device to the peers list if the identity is valid.
     */
    @SuppressLint("MissingPermission")
    private suspend fun readAlertNetIdentity(device: BluetoothDevice, rssi: Int) {
        val address = device.address ?: return

        try {
            val result = CompletableDeferred<MeshPeer?>()

            @SuppressLint("MissingPermission")
            val gatt = device.connectGatt(context, false, object : BluetoothGattCallback() {
                @SuppressLint("MissingPermission")
                override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        gatt.discoverServices()
                    } else {
                        if (!result.isCompleted) result.complete(null)
                        gatt.close()
                    }
                }

                @SuppressLint("MissingPermission")
                override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                    if (status != BluetoothGatt.GATT_SUCCESS) {
                        if (!result.isCompleted) result.complete(null)
                        gatt.close()
                        return
                    }

                    val service = gatt.getService(BleConstants.MESH_SERVICE_UUID)
                    val identityChar = service?.getCharacteristic(BleConstants.IDENTITY_CHARACTERISTIC)

                    if (identityChar == null) {
                        Log.w(TAG, "No identity characteristic on $address — not an AlertNet device")
                        if (!result.isCompleted) result.complete(null)
                        gatt.close()
                        return
                    }

                    val readOk = gatt.readCharacteristic(identityChar)
                    if (!readOk) {
                        if (!result.isCompleted) result.complete(null)
                        gatt.close()
                    }
                }

                @SuppressLint("MissingPermission")
                override fun onCharacteristicRead(
                    gatt: BluetoothGatt,
                    characteristic: BluetoothGattCharacteristic,
                    status: Int
                ) {
                    gatt.close()

                    if (status != BluetoothGatt.GATT_SUCCESS || characteristic.value == null) {
                        if (!result.isCompleted) result.complete(null)
                        return
                    }


                    val identity = String(characteristic.value, Charsets.UTF_8)
                    val parsed = parseAlertNetIdentity(identity)

                    if (parsed == null) {
                        Log.w(TAG, "Invalid identity from $address: $identity")
                        if (!result.isCompleted) result.complete(null)
                        return
                    }

                    val peer = MeshPeer(
                        deviceId = parsed.first,  // AlertNet UUID
                        displayName = parsed.second,  // Username
                        lastSeen = System.currentTimeMillis(),
                        rssi = rssi,
                        transportType = TransportType.BLE,
                        discoveryType = TransportType.BLE,
                        macAddress = address,
                        isConnected = false,
                        bridgefyId = parsed.first,
                        username = parsed.second
                    )

                    Log.d(TAG, "AlertNet peer identified: ${parsed.second} (${parsed.first}) at $address")
                    if (!result.isCompleted) result.complete(peer)
                }
            })

            // Wait with timeout
            val peer = withTimeoutOrNull(BleConstants.IDENTITY_SCAN_TIMEOUT_MS) {
                result.await()
            }

            if (peer != null) {
                // Store under AlertNet ID (not MAC) for deduplication
                peersMap[peer.alertnetId!!] = peer
                updatePeersList()
                _connectionEvents.emit(ConnectionEvent.PeerConnected(peer))
            }
        } catch (e: Exception) {
            Log.e(TAG, "GATT identity read failed for $address", e)
        } finally {
            pendingIdentityReads.remove(address)
        }
    }

    /**
     * Parse an AlertNet identity string: "ALERTNET:<uuid>:<username>"
     * @return Pair(uuid, username) or null if format is invalid
     */
    private fun parseAlertNetIdentity(identity: String): Pair<String, String>? {
        if (!identity.startsWith(BleConstants.IDENTITY_PREFIX + ":")) return null
        val parts = identity.split(":", limit = 3)
        if (parts.size < 3) return null
        val uuid = parts[1]
        val name = parts[2]
        if (uuid.isBlank() || name.isBlank()) return null
        return Pair(uuid, name)
    }

    // ─── Send Message ───────────────────────────────────────────

    @SuppressLint("MissingPermission")
    suspend fun sendDataToRemoteDevice(device: BluetoothDevice, data: ByteArray): Boolean {
        if (!hasPermissions()) return false
        val result = CompletableDeferred<Boolean>()

        val gatt = device.connectGatt(context, false, object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    gatt.requestMtu(BleConstants.REQUESTED_MTU)
                } else {
                    if (!result.isCompleted) result.complete(false)
                    gatt.close()
                }
            }

            override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    gatt.discoverServices()
                } else {
                    if (!result.isCompleted) result.complete(false)
                    gatt.close()
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    if (!result.isCompleted) result.complete(false)
                    gatt.close()
                    return
                }

                val service = gatt.getService(BleConstants.MESH_SERVICE_UUID)
                val characteristic = service?.getCharacteristic(BleConstants.MESSAGE_CHARACTERISTIC)

                if (characteristic == null) {
                    Log.d(TAG, "Message characteristic not found on device ${device.address}")
                    if (!result.isCompleted) result.complete(false)
                    gatt.close()
                    return
                }

                characteristic.value = data
                val writeResult = gatt.writeCharacteristic(characteristic)
                if (!writeResult) {
                    if (!result.isCompleted) result.complete(false)
                    gatt.close()
                }
            }

            override fun onCharacteristicWrite(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int
            ) {
                result.complete(status == BluetoothGatt.GATT_SUCCESS)
                gatt.close()
            }
        })

        return withTimeoutOrNull(3_000) { result.await() } ?: false
    }

    @SuppressLint("MissingPermission")
    override suspend fun sendMessage(peerId: String, data: ByteArray): Boolean {
        if (!hasPermissions()) return false

        if (data.size > BleConstants.MAX_BLE_PAYLOAD) {
            Log.w(TAG, "Payload too large for BLE (${data.size} bytes), use WiFi Direct")
            return false
        }

        return withContext(Dispatchers.IO) {
            try {
                // Find peer by AlertNet ID, deviceId, or MAC address
                val peer = peersMap[peerId]
                    ?: peersMap.values.find { it.macAddress == peerId || it.deviceId == peerId || it.bridgefyId == peerId }
                val macAddress = peer?.macAddress ?: if (peerId.contains(":")) peerId else null

                if (macAddress == null) {
                    Log.w(TAG, "No MAC address for peer: $peerId")
                    return@withContext false
                }

                val device = bluetoothAdapter?.getRemoteDevice(macAddress) ?: return@withContext false
                sendDataToRemoteDevice(device, data)
            } catch (e: Exception) {
                Log.e(TAG, "BLE send failed", e)
                false
            }
        }
    }

    override suspend fun broadcastMessage(data: ByteArray, excludePeers: Set<String>) {
        val targets = peersMap.values
            .filter { it.deviceId !in excludePeers && it.macAddress !in excludePeers }
            .distinctBy { it.macAddress }

        for (peer in targets) {
            scope.launch {
                try {
                    sendMessage(peer.deviceId, data)
                } catch (e: Exception) {
                    Log.e(TAG, "Broadcast to ${peer.deviceId} failed", e)
                }
            }
        }
    }

    /**
     * Broadcast an emergency SOS to all nearby devices strictly within the 15-meter radius.
     * 1. Broadcasts high-priority connectionless BLE SOS beacon packets (reaches ALL nearby devices,
     *    connected or unconnected, without requiring pairing or connection).
     * 2. Direct parallel BLE GATT writes to all nearby discovered peers within 15 meters.
     * 3. Direct parallel BLE GATT writes to all recently scanned nearby devices within 15 meters
     *    (even if not yet connected or in peersMap).
     */
    suspend fun broadcastSOS(
        data: ByteArray,
        lat: Double?,
        lon: Double?,
        excludePeers: Set<String> = emptySet()
    ) {
        // 1. Connectionless BLE SOS Beacon Advertising (reaches all devices in 15m radius immediately)
        broadcastSosBeacon(lat, lon)

        // 2. Filter target peers to only those within 15 meters
        val targetsWithin15m = peersMap.values
            .filter { it.deviceId !in excludePeers && it.macAddress !in excludePeers }
            .filter { peer ->
                SosDistanceHelper.isWithin15Meters(
                    peerLat = peer.latitude,
                    peerLon = peer.longitude,
                    myLat = lat,
                    myLon = lon,
                    rssi = peer.rssi,
                    isDirectlyConnected = peer.isConnected
                )
            }
            .distinctBy { it.macAddress ?: it.deviceId }

        Log.d(TAG, "Broadcasting direct BLE SOS to ${targetsWithin15m.size} peers within 15m radius")
        for (peer in targetsWithin15m) {
            scope.launch {
                try {
                    sendMessage(peer.deviceId, data)
                } catch (e: Exception) {
                    Log.e(TAG, "Direct BLE SOS to ${peer.deviceId} failed", e)
                }
            }
        }

        // 3. Direct parallel BLE GATT writes to all recently scanned nearby devices within 15m (unconnected/unpaired)
        val now = System.currentTimeMillis()
        val targetedMacs = targetsWithin15m.mapNotNull { it.macAddress }.toSet()
        val scannedWithin15m = nearbyScannedBleDevices.values
            .filter { (now - it.timestamp) < 60_000L } // detected in the last 60 seconds
            .filter { it.device.address !in targetedMacs && it.device.address !in excludePeers }
            .filter { SosDistanceHelper.isWithin15Meters(null, null, null, null, it.rssi) }

        Log.d(TAG, "Broadcasting direct BLE SOS to ${scannedWithin15m.size} unconnected nearby scanned devices")
        for (scanned in scannedWithin15m) {
            scope.launch {
                try {
                    sendDataToRemoteDevice(scanned.device, data)
                } catch (e: Exception) {
                    Log.d(TAG, "Direct GATT write to unconnected device ${scanned.device.address} failed: ${e.message}")
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun broadcastSosBeacon(lat: Double?, lon: Double?) {
        if (!hasPermissions() || bleAdvertiser == null) return

        scope.launch {
            try {
                // Pause standard background advertisement to prevent collision on single-instance BLE advertisers
                try {
                    bleAdvertiser?.stopAdvertising(advertiseCallback)
                } catch (_: Exception) {}

                val sosSettings = AdvertiseSettings.Builder()
                    .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                    .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                    .setConnectable(true)
                    .build()

                // Compact payload format for legacy 31-byte BLE advertising:
                // Primary packet: 16-bit SOS Service UUID (4 bytes) + Mfg Data with sender ID (14 bytes) = 18 bytes <= 31 bytes
                val shortId = deviceId.take(6)
                val sosData = AdvertiseData.Builder()
                    .setIncludeDeviceName(false)
                    .addServiceUuid(ParcelUuid(BleConstants.SOS_SERVICE_UUID_16))
                    .addManufacturerData(
                        BleConstants.SOS_MANUFACTURER_ID,
                        "SOS:$shortId".toByteArray(Charsets.UTF_8)
                    )
                    .build()

                // Scan response: If GPS location is available, include coordinates in scan response
                val scanResponseBuilder = AdvertiseData.Builder().setIncludeDeviceName(false)
                if (lat != null && lon != null) {
                    val locStr = String.format(java.util.Locale.US, "SOS:%s:%.3f,%.3f", shortId, lat, lon)
                    scanResponseBuilder.addManufacturerData(
                        BleConstants.SOS_MANUFACTURER_ID,
                        locStr.toByteArray(Charsets.UTF_8)
                    )
                } else {
                    scanResponseBuilder.addServiceData(
                        ParcelUuid(BleConstants.SOS_SERVICE_UUID_16),
                        "SOS:$shortId".toByteArray(Charsets.UTF_8)
                    )
                }
                val sosScanResponse = scanResponseBuilder.build()

                val sosCallback = object : AdvertiseCallback() {
                    override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
                        Log.d(TAG, "Emergency BLE SOS beacon active (low latency, high power)")
                    }
                    override fun onStartFailure(errorCode: Int) {
                        Log.e(TAG, "Emergency BLE SOS beacon failed: $errorCode")
                    }
                }

                bleAdvertiser?.startAdvertising(sosSettings, sosData, sosScanResponse, sosCallback)
                Log.d(TAG, "Started emergency BLE SOS beacon broadcast")

                // Keep high-priority SOS beacon active for 30 seconds
                delay(30_000L)

                try {
                    bleAdvertiser?.stopAdvertising(sosCallback)
                } catch (_: Exception) {}

                // Resume standard advertising if transport is still running
                if (isRunning) {
                    startAdvertising()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed in broadcastSosBeacon", e)
                if (isRunning) {
                    startAdvertising()
                }
            }
        }
    }

    private fun handleIncomingSosBeacon(macAddress: String, dataStr: String, rssi: Int) {
        // Enforce 15-meter radius constraint
        if (!SosDistanceHelper.isWithin15Meters(null, null, null, null, rssi)) {
            Log.d(TAG, "Ignoring SOS beacon from $macAddress (RSSI $rssi is beyond 15m)")
            return
        }

        val parts = dataStr.removePrefix("SOS:").split(":")
        val senderShortId = parts.getOrNull(0)?.take(8) ?: "UNKNOWN"
        var lat: Double? = null
        var lon: Double? = null
        if (parts.size >= 2) {
            val coords = parts[1].split(",")
            if (coords.size == 2) {
                lat = coords[0].toDoubleOrNull()
                lon = coords[1].toDoubleOrNull()
            }
        }

        val now = System.currentTimeMillis()
        val dedupKey = "$macAddress:$senderShortId"
        val lastSeen = recentSosBeacons[dedupKey] ?: 0L
        if (now - lastSeen < 10_000L) {
            // Deduplicate within 10 seconds
            return
        }
        recentSosBeacons[dedupKey] = now

        val distanceEst = SosDistanceHelper.estimateBleDistance(rssi)
        val approxMeters = distanceEst.roundToInt().coerceAtLeast(1)

        val sosPayload = buildString {
            append("🆘 SOS — HELP ME!\n")
            append("Proximity: ~${approxMeters}m away (within 15m radius, Signal: ${rssi} dBm)")
            if (lat != null && lon != null) {
                append("\nLocation: $lat, $lon")
            }
        }

        val sosMessage = MeshMessage(
            id = UUID.randomUUID().toString(),
            senderId = senderShortId,
            targetId = null,
            type = MessageType.SOS,
            payload = sosPayload,
            timestamp = now,
            ttl = 1,
            hopCount = 0,
            hopPath = listOf(senderShortId),
            status = DeliveryStatus.DELIVERED
        )

        scope.launch {
            try {
                val serialized = json.encodeToString(sosMessage).toByteArray(Charsets.UTF_8)
                _incomingMessages.emit(
                    TransportMessage(
                        data = serialized,
                        senderPeerId = senderShortId,
                        transportType = TransportType.BLE,
                        rssi = rssi
                    )
                )
                Log.d(TAG, "Delivered incoming emergency SOS beacon from $senderShortId within 15m ($rssi dBm)")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to emit incoming SOS beacon", e)
            }
        }
    }

    override fun upgradePeerId(oldId: String, newId: String) {
        val existing = peersMap[oldId]
        if (existing != null && oldId != newId) {
            peersMap.remove(oldId)
            val updated = existing.copy(deviceId = newId)
            peersMap[newId] = updated
            scope.launch { updatePeersList() }
            Log.d(TAG, "Upgraded BLE peer ID from $oldId to $newId")
        }
    }

    private fun updatePeersList() {
        _discoveredPeers.value = peersMap.values
            .distinctBy { it.alertnetId ?: it.macAddress ?: it.deviceId }
            .toList()
    }

    private fun hasPermissions(): Boolean {
        val hasConnect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.checkSelfPermission(
                context, Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else true

        val hasScan = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.checkSelfPermission(
                context, Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED
        } else true

        val hasAdvertise = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.checkSelfPermission(
                context, Manifest.permission.BLUETOOTH_ADVERTISE
            ) == PackageManager.PERMISSION_GRANTED
        } else true

        val hasLocation = ActivityCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        return hasConnect && hasScan && hasAdvertise && hasLocation
    }
}
