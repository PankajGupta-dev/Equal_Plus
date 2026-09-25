package com.bridgefy.app.mesh

import android.util.Log
import com.bridgefy.app.model.DeliveryStatus
import com.bridgefy.app.model.MeshMessage
import com.bridgefy.app.model.MessageType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks delivery acknowledgments for messages sent through the mesh.
 *
 * When a message is delivered to its target, the target generates an ACK
 * message that routes back through the mesh to the original sender.
 * This tracker monitors pending ACKs and updates message status accordingly.
 *
 * Timeout: If no ACK is received within [ACK_TIMEOUT_MS], the message is
 * marked as SENT (delivered to relay but unconfirmed at target).
 */
class AckTracker(private val deviceId: String) {

    companion object {
        private const val TAG = "AckTracker"
        private const val ACK_TIMEOUT_MS = 5 * 60 * 1000L // 5 minutes
    }

    /**
     * Map of messageId → timestamp when ACK was expected.
     * Entries are added when we originate a message and removed
     * when the corresponding ACK arrives.
     */
    private val pendingAcks = ConcurrentHashMap<String, Long>()

    private val _deliveredMessages = MutableStateFlow<Set<String>>(emptySet())
    /** Set of message IDs that have been confirmed delivered */
    val deliveredMessages: StateFlow<Set<String>> = _deliveredMessages

    private val mutex = Mutex()

    /**
     * Register a message as expecting an ACK.
     * Call when originating a new message (not when relaying).
     */
    fun expectAck(messageId: String) {
        pendingAcks[messageId] = System.currentTimeMillis()
        Log.d(TAG, "Expecting ACK for $messageId")
    }

    /**
     * Process a received ACK message.
     *
     * @return true if this ACK was for one of our pending messages
     */
    suspend fun processAck(ackMessage: MeshMessage): Boolean {
        val originalId = ackMessage.ackForMessageId ?: return false

        if (pendingAcks.remove(originalId) != null) {
            mutex.withLock {
                _deliveredMessages.value = _deliveredMessages.value + originalId
            }
            Log.d(TAG, "ACK received for $originalId — message delivered!")
            return true
        }

        return false
    }

    /**
     * Create an ACK message for a received message.
     * The ACK is routed back to the original sender via the mesh.
     *
     * @param originalMessage The message being acknowledged
     * @return An ACK MeshMessage targeting the original sender
     */
    fun createAck(originalMessage: MeshMessage): MeshMessage {
        return MeshMessage(
            id = UUID.randomUUID().toString(),
            senderId = deviceId,
            targetId = originalMessage.senderId,
            type = MessageType.ACK,
            payload = "", // ACKs carry no payload
            timestamp = System.currentTimeMillis(),
            // Give ACK enough hops to reach back (original hopCount + buffer)
            ttl = originalMessage.hopCount + 3,
            hopCount = 0,
            hopPath = listOf(deviceId),
            status = DeliveryStatus.QUEUED,
            ackForMessageId = originalMessage.id
        )
    }

    /**
     * Check for timed-out ACKs and return their message IDs.
     * These messages will be marked as SENT (unconfirmed).
     */
    suspend fun getTimedOutAcks(): List<String> {
        val now = System.currentTimeMillis()
        val timedOut = pendingAcks.entries
            .filter { now - it.value > ACK_TIMEOUT_MS }
            .map { it.key }

        for (id in timedOut) {
            pendingAcks.remove(id)
        }

        if (timedOut.isNotEmpty()) {
            Log.d(TAG, "Timed out ${timedOut.size} pending ACKs")
        }

        return timedOut
    }

    /**
     * Check if a message delivery has been confirmed.
     */
    fun isDelivered(messageId: String): Boolean {
        return messageId in _deliveredMessages.value
    }

    /**
     * Number of ACKs we're currently waiting for.
     */
    fun pendingCount(): Int = pendingAcks.size
}
