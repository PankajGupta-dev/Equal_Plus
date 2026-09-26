package com.bridgefy.app.transport.ble

import java.util.UUID

/**
 * BLE GATT service and characteristic UUIDs for BridgeFy mesh communication.
 *
 * All BridgeFy devices advertise the same service UUID so they can
 * discover each other. The characteristics are used for data exchange:
 * - MESH_ID_CHAR: readable, contains the device's mesh UUID
 * - MESSAGE_CHAR: writable, used to send small messages (<512 bytes)
 */
object BleConstants {

    /** Main BridgeFy mesh service UUID */
    val MESH_SERVICE_UUID: UUID = UUID.fromString("a1e7-0001-0000-0000-000000000000".let {
        "0000${it.substring(0, 4)}-0000-1000-8000-00805f9b34fb"
    }).let {
        // Use a fully custom UUID for our mesh service
        UUID.fromString("f47ac10b-58cc-4372-a567-0e02b2c3d479")
    }

    /** Characteristic containing the device's mesh identity UUID (read) */
    val MESH_ID_CHARACTERISTIC: UUID =
        UUID.fromString("f47ac10b-58cc-4372-a567-0e02b2c3d480")

    /** Characteristic for writing mesh message data (write) */
    val MESSAGE_CHARACTERISTIC: UUID =
        UUID.fromString("f47ac10b-58cc-4372-a567-0e02b2c3d481")

    /** Characteristic for broadcasting BridgeFy identity (read): BRIDGEFY:<uuid>:<username> */
    val IDENTITY_CHARACTERISTIC: UUID =
        UUID.fromString("f47ac10b-58cc-4372-a567-0e02b2c3d482")

    /** Client Characteristic Configuration Descriptor for notifications */
    val CCCD_UUID: UUID =
        UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    /** Prefix used to identify BridgeFy devices in BLE identity payloads */
    const val IDENTITY_PREFIX = "BRIDGEFY"

    /** Maximum bytes available for service data in a BLE advertisement */
    const val MAX_ADVERTISE_DATA = 20

    /** Timeout for GATT identity reads during discovery (ms) */
    const val IDENTITY_SCAN_TIMEOUT_MS = 2_000L

    /** Maximum BLE MTU we request (Android supports up to 517) */
    const val REQUESTED_MTU = 512

    /** BLE scan duration in milliseconds */
    const val SCAN_DURATION_MS = 30_000L

    /** BLE scan interval (idle between scans) in milliseconds */
    const val SCAN_INTERVAL_MS = 5_000L

    /** Maximum payload size for BLE transport (after MTU overhead) */
    const val MAX_BLE_PAYLOAD = 500

    /** 16-bit short UUID for compact SOS broadcast advertisement packets (fits in legacy 31-byte BLE frame) */
    val SOS_SERVICE_UUID_16: UUID = UUID.fromString("0000bf01-0000-1000-8000-00805f9b34fb")

    /** Manufacturer ID used for connectionless SOS broadcasts: 0xBFBF (BridgeFy) */
    const val SOS_MANUFACTURER_ID = 0xBFBF
}
