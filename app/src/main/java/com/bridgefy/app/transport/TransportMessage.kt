package com.bridgefy.app.transport

import com.bridgefy.app.model.TransportType

/**
 * Raw data received from a transport layer.
 * Contains the serialized (encrypted) message bytes and sender metadata.
 */
data class TransportMessage(
    /** Raw bytes of the serialized MeshMessage */
    val data: ByteArray,
    /** Device ID of the peer that sent this message */
    val senderPeerId: String,
    /** Which transport this message arrived on */
    val transportType: TransportType,
    /** BLE signal strength at time of receipt (null for WiFi Direct) */
    val rssi: Int? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TransportMessage) return false
        return data.contentEquals(other.data) &&
                senderPeerId == other.senderPeerId &&
                transportType == other.transportType
    }

    override fun hashCode(): Int {
        var result = data.contentHashCode()
        result = 31 * result + senderPeerId.hashCode()
        result = 31 * result + transportType.hashCode()
        return result
    }
}
