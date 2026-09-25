package com.bridgefy.app.mesh

import android.util.Log
import com.bridgefy.app.model.DeliveryStatus
import com.bridgefy.app.model.MeshMessage
import com.bridgefy.app.model.MeshPeer
import com.bridgefy.app.model.MessageType
import kotlin.random.Random

/**
 * Core routing engine for the AlertNet mesh network.
 *
 * Implements opportunistic routing with probabilistic forwarding:
 * - Messages are flooded to peers not in the hop path
 * - TTL limits maximum hops (prevents infinite propagation)
 * - Probabilistic forwarding reduces flooding at scale (50+ devices)
 * - Hop path tracking prevents routing loops
 *
 * Routing decisions:
 * - DROP: duplicate or TTL expired
 * - DELIVER: message is addressed to this device
 * - FORWARD: relay to other peers with decremented TTL
 * - ACK: generate delivery acknowledgment
 */
class MessageRouter(
    private val deduplicationManager: DeduplicationManager,
    private val ackTracker: AckTracker,
    private val deviceId: String
) {
    companion object {
        private const val TAG = "MessageRouter"
    }

    /**
     * Make a routing decision for an incoming message.
     *
     * Algorithm:
     * 1. Check for duplicates → DROP
     * 2. Mark as seen
     * 3. If ACK message → process ACK
     * 4. If addressed to us → DELIVER + generate ACK
     * 5. If broadcast (targetId == null) → DELIVER + FORWARD
     * 6. If TTL expired → DROP
     * 7. Otherwise → FORWARD with decremented TTL
     */
    suspend fun routeMessage(
        message: MeshMessage,
        activePeers: List<MeshPeer>
    ): RoutingDecision {
        // 1. Duplicate check
        if (deduplicationManager.isDuplicate(message.id)) {
            Log.d(TAG, "DROP duplicate: ${message.id}")
            return RoutingDecision.Drop
        }

        // 2. Mark as seen (prevents re-processing)
        deduplicationManager.markSeen(message.id)

        // 3b. Handle Call signaling messages
        if (message.type == MessageType.CALL_REQUEST ||
            message.type == MessageType.CALL_ACCEPT ||
            message.type == MessageType.CALL_REJECT ||
            message.type == MessageType.CALL_END ||
            message.type == MessageType.VIDEO_CALL_REQUEST ||
            message.type == MessageType.VIDEO_CALL_ACCEPT ||
            message.type == MessageType.VIDEO_CALL_REJECT ||
            message.type == MessageType.VIDEO_CALL_END) {
            return RoutingDecision.CallSignalReceived(message)
        }

        // 3. Handle ACK messages
        if (message.type == MessageType.ACK) {
            if (message.targetId == deviceId) {
                // ACK is for us
                ackTracker.processAck(message)
                return RoutingDecision.AckReceived(message.ackForMessageId ?: "")
            }
            // ACK for someone else — forward it
            if (message.ttl <= 1) return RoutingDecision.Drop
            val forwarded = prepareForForward(message)
            val targets = selectForwardingPeers(forwarded, activePeers)
            return if (targets.isNotEmpty()) {
                RoutingDecision.Forward(forwarded, targets)
            } else {
                RoutingDecision.Drop
            }
        }

        // 4. Handle PEER_ANNOUNCE / PEER_LEAVE (control messages, never deliver to UI)
        if (message.type == MessageType.PEER_ANNOUNCE || message.type == MessageType.PEER_LEAVE) {
            return RoutingDecision.PeerControl(message)
        }

        // 4b. Handle LOCATION_PING — deliver to location handler + forward
        if (message.type == MessageType.LOCATION_PING) {
            if (message.ttl <= 1) {
                return RoutingDecision.LocationPingReceived(message)
            }
            val forwarded = prepareForForward(message)
            val targets = selectForwardingPeers(forwarded, activePeers)
            return RoutingDecision.LocationPingReceived(message)
        }

        // 4c. Handle SOS — deliver as alert + forward to maximize reach
        if (message.type == MessageType.SOS) {
            if (message.ttl <= 1) {
                return RoutingDecision.SOSReceived(message)
            }
            val forwarded = prepareForForward(message)
            val targets = selectForwardingPeers(forwarded, activePeers)
            // Forward SOS to maximize reach in the mesh
            if (targets.isNotEmpty()) {
                return RoutingDecision.SOSReceived(message)
            }
            return RoutingDecision.SOSReceived(message)
        }

        // 5. Message addressed to this device
        if (message.targetId == deviceId) {
            Log.d(TAG, "DELIVER to self: ${message.id}")
            // Generate ACK to send back
            val ack = ackTracker.createAck(message)
            return RoutingDecision.Deliver(message, ack)
        }

        // 6. Broadcast message (targetId == null) — deliver AND forward
        if (message.targetId == null) {
            Log.d(TAG, "DELIVER broadcast: ${message.id}")
            if (message.ttl <= 1) {
                return RoutingDecision.Deliver(message, ackMessage = null)
            }
            val forwarded = prepareForForward(message)
            val targets = selectForwardingPeers(forwarded, activePeers)
            return RoutingDecision.DeliverAndForward(message, forwarded, targets)
        }

        // 7. Message for someone else — check TTL and forward
        if (message.ttl <= 1) {
            Log.d(TAG, "DROP TTL expired: ${message.id}")
            return RoutingDecision.Drop
        }

        val forwarded = prepareForForward(message)
        val targets = selectForwardingPeers(forwarded, activePeers)

        return if (targets.isNotEmpty()) {
            Log.d(TAG, "FORWARD ${message.id} to ${targets.size} peers, ttl=${forwarded.ttl}")
            RoutingDecision.Forward(forwarded, targets)
        } else {
            Log.d(TAG, "No forwarding targets for ${message.id}")
            RoutingDecision.Drop
        }
    }

    /**
     * Prepare a message for forwarding: decrement TTL, increment hop count,
     * add this device to the hop path.
     */
    private fun prepareForForward(message: MeshMessage): MeshMessage {
        return message.copy(
            ttl = message.ttl - 1,
            hopCount = message.hopCount + 1,
            hopPath = message.hopPath + deviceId,
            status = DeliveryStatus.QUEUED
        )
    }

    /**
     * Select which peers to forward a message to.
     *
     * Rules:
     * 1. Exclude peers already in the hop path (loop prevention)
     * 2. Exclude the original sender
     * 3. Apply probabilistic forwarding based on peer count (flood control)
     *
     * Probabilistic forwarding thresholds:
     * - <10 peers: forward to all (probability = 1.0)
     * - 10-30 peers: forward probability = 0.7
     * - 30-50 peers: forward probability = 0.5
     * - >50 peers: forward probability = 0.3
     */
    fun selectForwardingPeers(
        message: MeshMessage,
        allPeers: List<MeshPeer>
    ): List<MeshPeer> {
        val excludeIds = message.hopPath.toSet() + message.senderId
        val candidates = allPeers.filter { it.deviceId !in excludeIds }

        val probability = calculateForwardProbability(allPeers.size)

        return if (probability >= 1.0) {
            candidates
        } else {
            candidates.filter { Random.nextDouble() < probability }
        }
    }

    /**
     * Calculate forwarding probability based on network density.
     * Lower probability at higher peer counts prevents flooding.
     */
    fun calculateForwardProbability(peerCount: Int): Double {
        return when {
            peerCount < 10 -> 1.0
            peerCount < 30 -> 0.7
            peerCount < 50 -> 0.5
            else -> 0.3
        }
    }
}

/**
 * Result of the routing decision for an incoming message.
 */
sealed class RoutingDecision {
    /** Message is a duplicate or TTL expired — discard */
    data object Drop : RoutingDecision()

    /** Message is for this device — deliver to UI */
    data class Deliver(
        val message: MeshMessage,
        val ackMessage: MeshMessage?  // ACK to send back, null for broadcasts
    ) : RoutingDecision()

    /** Message should be relayed to other peers */
    data class Forward(
        val message: MeshMessage,
        val targets: List<MeshPeer>
    ) : RoutingDecision()

    /** Broadcast: deliver to UI AND forward to peers */
    data class DeliverAndForward(
        val message: MeshMessage,
        val forwardMessage: MeshMessage,
        val targets: List<MeshPeer>
    ) : RoutingDecision()

    /** ACK received for one of our sent messages */
    data class AckReceived(val originalMessageId: String) : RoutingDecision()

    /** Peer control message (announce/leave) */
    data class PeerControl(val message: MeshMessage) : RoutingDecision()

    /** LOCATION_PING received — process for peer location update, then optionally forward */
    data class LocationPingReceived(val message: MeshMessage) : RoutingDecision()

    /** SOS received — show emergency alert to the user */
    data class SOSReceived(val message: MeshMessage) : RoutingDecision()

    /** Call signaling received */
    data class CallSignalReceived(val message: MeshMessage) : RoutingDecision()
}
