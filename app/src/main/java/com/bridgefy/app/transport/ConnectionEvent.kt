package com.bridgefy.app.transport

import com.bridgefy.app.model.MeshPeer

/**
 * Events emitted by transport layers for connection lifecycle tracking.
 */
sealed class ConnectionEvent {
    /** A new peer has connected or been discovered */
    data class PeerConnected(val peer: MeshPeer) : ConnectionEvent()

    /** A previously connected peer has disconnected or gone out of range */
    data class PeerDisconnected(val peerId: String) : ConnectionEvent()

    /** An error occurred in the transport layer */
    data class TransportError(
        val error: Throwable,
        val message: String = error.message ?: "Unknown transport error"
    ) : ConnectionEvent()
}
