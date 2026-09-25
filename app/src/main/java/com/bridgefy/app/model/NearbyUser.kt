package com.bridgefy.app.model

/**
 * Represents a single physical user discovered nearby, deduplicated across
 * BLE and WiFi Direct transports.
 *
 * This is the UI-facing model — it never exposes MAC addresses or raw device IDs.
 * The [id] is the AlertNet UUID parsed from the BLE identity payload.
 *
 * @property id AlertNet UUID (stable across sessions, stored in SharedPreferences)
 * @property name Human-readable name (device model name)
 * @property rssi BLE signal strength in dBm (null if discovered via WiFi Direct only)
 * @property connectionType How this user was discovered in the current scan cycle
 * @property status Current connection lifecycle state
 * @property lastSeen Epoch millis when this user was last detected
 */
data class NearbyUser(
    val id: String,
    val name: String,
    val rssi: Int? = null,
    val connectionType: ConnectionType,
    val status: UserStatus,
    val lastSeen: Long = System.currentTimeMillis()
)

/**
 * Discovery/connection medium through which a nearby user was found.
 */
enum class ConnectionType {
    /** Discovered via BLE advertisement + GATT identity read */
    BLE,
    /** Discovered via WiFi Direct peer discovery (fallback) */
    WIFI_DIRECT
}

/**
 * Connection lifecycle state for a nearby user.
 */
enum class UserStatus {
    /** Discovered but no active transport connection */
    NEARBY,
    /** WiFi Direct connection is being established */
    CONNECTING,
    /** Active transport connection, ready to chat */
    CONNECTED
}
