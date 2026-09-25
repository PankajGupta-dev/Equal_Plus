package com.bridgefy.app.mesh

/**
 * Configuration for peer discovery timing.
 *
 * Optimized for speed — WiFi Direct only, no BLE phase.
 */
data class DiscoveryConfig(
    /** How long to run WiFi Direct discovery per scan cycle */
    val wifiScanTimeoutMs: Long = 8_000L,
    /** Cooldown between scan cycles (keeps discovery responsive) */
    val scanCooldownMs: Long = 5_000L,
    /** Peers not seen for this duration are considered stale */
    val peerExpiryMs: Long = 5 * 60 * 1000L,
    /** How often to run the stale-peer cleanup */
    val cleanupIntervalMs: Long = 60_000L
)
