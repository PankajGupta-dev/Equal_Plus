package com.bridgefy.app.mesh

/**
 * Simplified discovery states — WiFi Direct only.
 */
enum class DiscoveryState {
    /** Not actively scanning */
    IDLE,
    /** WiFi Direct discovery active, searching for peers */
    SCANNING_WIFI,
    /** Peers discovered via WiFi Direct */
    WIFI_FOUND,
    // Legacy states kept for backward compatibility with UI
    /** @deprecated BLE discovery removed — kept so existing UI references compile */
    SCANNING_BLE,
    /** @deprecated BLE identity reads removed — kept so existing UI references compile */
    READING_IDENTITIES,
    /** @deprecated BLE discovery removed — kept so existing UI references compile */
    BLE_FOUND,
    /** @deprecated WiFi fallback renamed to SCANNING_WIFI — kept so existing UI references compile */
    FALLBACK_WIFI_SCAN
}
