package com.bridgefy.app.model

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Core mesh message that travels across the decentralized network.
 *
 * Every message carries routing metadata (TTL, hopCount, hopPath) enabling
 * multi-hop relay without fixed routes. The payload is always encrypted
 * before transmission and decrypted on delivery.
 *
 * @property id Globally unique message identifier for deduplication
 * @property senderId UUID of the originating device
 * @property targetId UUID of the intended recipient; null = broadcast to all
 * @property type The kind of message (TEXT, IMAGE, FILE, ACK, etc.)
 * @property payload Encrypted content (text, base64 file data, or ACK reference)
 * @property fileName Original filename for FILE/IMAGE messages
 * @property mimeType MIME type for FILE/IMAGE messages
 * @property timestamp Creation time in epoch millis
 * @property ttl Time-to-live: decremented at each hop, message dropped at 0
 * @property hopCount Number of hops this message has traveled
 * @property hopPath Ordered list of device IDs that relayed this message (loop prevention)
 * @property status Current delivery status on this device
 * @property ackForMessageId If this is an ACK, the original message ID being acknowledged
 */
@Serializable
data class MeshMessage(
    val id: String = UUID.randomUUID().toString(),
    val senderId: String,
    val targetId: String? = null,
    val type: MessageType = MessageType.TEXT,
    val payload: String = "",
    val fileName: String? = null,
    val mimeType: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val ttl: Int = 7,
    val hopCount: Int = 0,
    val hopPath: List<String> = emptyList(),
    val status: DeliveryStatus = DeliveryStatus.QUEUED,
    val ackForMessageId: String? = null
)
