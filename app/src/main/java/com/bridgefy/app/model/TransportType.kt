package com.bridgefy.app.model

import kotlinx.serialization.Serializable

/**
 * Transport medium used to communicate with a peer.
 */
@Serializable
enum class TransportType {
    /** Bluetooth Low Energy – for discovery beacons and small messages */
    BLE,
    /** WiFi Direct – for large payloads, files, and high-throughput messaging */
    WIFI_DIRECT,
    /** Peer is reachable via both transports */
    BOTH
}
