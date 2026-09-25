package com.bridgefy.app.model

import kotlinx.serialization.Serializable

/**
 * Delivery lifecycle status of a mesh message.
 */
@Serializable
enum class DeliveryStatus {
    /** Queued locally, not yet sent to any peer */
    QUEUED,
    /** Currently being transmitted */
    SENDING,
    /** Sent to at least one relay peer */
    SENT,
    /** Delivery confirmed via ACK from target device */
    DELIVERED,
    /** All send attempts failed */
    FAILED,
    /** TTL expired or message aged out */
    EXPIRED
}
