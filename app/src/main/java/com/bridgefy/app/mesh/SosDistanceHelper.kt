package com.bridgefy.app.mesh

import android.location.Location
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Utility for 15-meter SOS emergency radius calculation.
 *
 * Calculates physical proximity using:
 * 1. Geodetic distance (WGS-84) when GPS coordinates are available.
 * 2. Log-distance path loss signal attenuation based on BLE RSSI when indoors/offline without GPS.
 */
object SosDistanceHelper {

    /** Maximum allowed radius for SOS emergency broadcast in meters */
    const val MAX_SOS_RADIUS_METERS = 15.0

    /**
     * Empirical BLE RSSI cutoff corresponding to ~15 meters in typical environments.
     * Based on log-distance path loss model:
     * RSSI = TxPower(1m) - 10 * n * log10(d)
     * For TxPower = -59 dBm and path loss exponent n = 2.4, d = 15m => RSSI ~= -86.5 dBm.
     * Devices with RSSI >= -86 dBm are within the 15-meter zone.
     */
    const val BLE_15M_RSSI_THRESHOLD = -86

    /**
     * Estimate physical distance in meters from Bluetooth Low Energy RSSI.
     * @param rssi Measured signal strength in dBm.
     * @param txPower Measured RSSI at 1 meter (default -59 dBm).
     * @param pathLoss Path loss exponent (default 2.4 for indoor/semi-open environments).
     */
    fun estimateBleDistance(
        rssi: Int,
        txPower: Int = -59,
        pathLoss: Double = 2.4
    ): Double {
        if (rssi >= 0) return 0.5
        val ratio = (txPower - rssi) / (10.0 * pathLoss)
        return 10.0.pow(ratio)
    }

    /**
     * Calculate exact surface distance in meters between two GPS coordinates using WGS-84 ellipsoid.
     */
    fun calculateGpsDistance(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Double {
        val results = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, results)
        return results[0].toDouble()
    }

    /**
     * Determines whether a target device is within the strict 15-meter SOS radius.
     *
     * Evaluation order:
     * 1. Exact GPS distance if both sender and peer coordinates are known.
     * 2. BLE RSSI signal strength threshold if Bluetooth proximity is available.
     * 3. Fallback to direct connected status if within immediate short-range Wi-Fi Direct.
     */
    fun isWithin15Meters(
        peerLat: Double?,
        peerLon: Double?,
        myLat: Double?,
        myLon: Double?,
        rssi: Int?,
        isDirectlyConnected: Boolean = false
    ): Boolean {
        // Priority 1: Exact GPS comparison
        if (peerLat != null && peerLon != null && myLat != null && myLon != null) {
            val dist = calculateGpsDistance(myLat, myLon, peerLat, peerLon)
            return dist <= MAX_SOS_RADIUS_METERS
        }

        // Priority 2: BLE RSSI attenuation
        if (rssi != null) {
            return rssi >= BLE_15M_RSSI_THRESHOLD || estimateBleDistance(rssi) <= MAX_SOS_RADIUS_METERS
        }

        // Priority 3: Direct 1-hop connected device without known GPS/RSSI
        return isDirectlyConnected
    }

    /**
     * Extract latitude and longitude from a standard BridgeFy SOS payload string if present.
     */
    fun extractCoordinates(payload: String): Pair<Double, Double>? {
        val regex = Regex("""Location:\s*([-\d.]+)\s*,\s*([-\d.]+)""")
        val match = regex.find(payload) ?: return null
        val lat = match.groupValues.getOrNull(1)?.toDoubleOrNull() ?: return null
        val lon = match.groupValues.getOrNull(2)?.toDoubleOrNull() ?: return null
        return Pair(lat, lon)
    }

    /**
     * Formats a human-readable proximity string for UI alerts.
     */
    fun formatDistanceString(distanceMeters: Double?): String {
        if (distanceMeters == null) return "Within 15m radius"
        val rounded = distanceMeters.roundToInt()
        return if (rounded <= 1) "Within ~1 meter" else "Within ~$rounded meters (in 15m range)"
    }
}
