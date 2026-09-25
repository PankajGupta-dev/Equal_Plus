package com.bridgefy.app.mesh

/**
 * Real-time statistics about the mesh network's health and activity.
 */
data class MeshStats(
    /** Total peers ever discovered in this session */
    val totalPeers: Int = 0,
    /** Peers currently considered active (seen within last 5 minutes) */
    val activePeers: Int = 0,
    /** Messages originated by this device */
    val messagesSent: Int = 0,
    /** Messages relayed through this device for others */
    val messagesRelayed: Int = 0,
    /** Messages confirmed delivered via ACK */
    val messagesDelivered: Int = 0,
    /** Messages waiting in the store-and-forward queue */
    val pendingMessages: Int = 0,
    /** Total seen message IDs (deduplication table size) */
    val seenMessageCount: Int = 0
)
