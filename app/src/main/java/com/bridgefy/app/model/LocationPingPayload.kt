package com.bridgefy.app.model

import kotlinx.serialization.Serializable

/**
 * Payload for LOCATION_PING messages — automatic background mesh broadcast.
 *
 * Serialized to JSON and stored in [MeshMessage.payload].
 * When received, this updates the sender's row in the peers table.
 */
@Serializable
data class LocationPingPayload(
    val senderId: String,
    val lat: Double,
    val lon: Double,
    val accuracyMeters: Float,
    val altitudeMeters: Float? = null,
    val timestampEpochSec: Long
)
