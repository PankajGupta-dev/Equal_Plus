package com.bridgefy.app.model

import kotlinx.serialization.Serializable

/**
 * Payload for voice call signaling messages (CALL_REQUEST, CALL_ACCEPT, CALL_REJECT, CALL_END).
 *
 * Serialized to JSON and stored in [MeshMessage.payload].
 * These messages travel through the existing mesh signaling pipeline — they do NOT
 * carry audio data (audio goes through a dedicated socket on port 8889).
 *
 * @property callId Unique identifier for this call session (UUID)
 * @property callerDeviceId Device ID of the caller (initiator)
 * @property callerName Human-readable name of the caller
 */
@Serializable
data class CallSignal(
    val callId: String,
    val callerDeviceId: String,
    val callerName: String,
    val isVideo: Boolean = false
)
