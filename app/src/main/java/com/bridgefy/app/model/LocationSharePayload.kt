package com.bridgefy.app.model

import kotlinx.serialization.Serializable

/**
 * Payload for LOCATION_SHARE messages — user-triggered chat location share.
 *
 * Serialized to JSON and stored in [MeshMessage.payload].
 * This is a chat artifact only — it must NEVER update peer location in the DB.
 */
@Serializable
data class LocationSharePayload(
    val lat: Double,
    val lon: Double,
    val accuracyMeters: Float,
    val altitudeMeters: Float? = null,
    val timestampEpochSec: Long,
    /** Optional user-written note, e.g. "Meet me here" */
    val label: String? = null
)
