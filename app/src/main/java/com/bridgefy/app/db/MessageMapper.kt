package com.bridgefy.app.db

import android.content.ContentValues
import android.database.Cursor
import com.bridgefy.app.model.DeliveryStatus
import com.bridgefy.app.model.MeshMessage
import com.bridgefy.app.model.MeshPeer
import com.bridgefy.app.model.MessageType
import com.bridgefy.app.model.TransportType
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val json = Json { ignoreUnknownKeys = true }

// ─── MeshMessage ↔ ContentValues/Cursor ────────────────────────

fun MeshMessage.toContentValues(): ContentValues {
    return ContentValues().apply {
        put("id", id)
        put("senderId", senderId)
        put("targetId", targetId)
        put("type", type.name)
        put("payload", payload)
        put("fileName", fileName)
        put("mimeType", mimeType)
        put("timestamp", timestamp)
        put("ttl", ttl)
        put("hopCount", hopCount)
        put("hopPath", json.encodeToString(hopPath))
        put("status", status.name)
        put("ackForMessageId", ackForMessageId)
    }
}

fun Cursor.toMeshMessage(): MeshMessage {
    val idStr = getString(getColumnIndexOrThrow("id"))
    val typeStr = getString(getColumnIndexOrThrow("type"))
    val statusStr = getString(getColumnIndexOrThrow("status"))
    val hopPathStr = getString(getColumnIndexOrThrow("hopPath"))

    return MeshMessage(
        id = idStr,
        senderId = getString(getColumnIndexOrThrow("senderId")),
        targetId = getString(getColumnIndexOrThrow("targetId")),
        type = try { MessageType.valueOf(typeStr) } catch(e: Exception) { MessageType.TEXT },
        payload = getString(getColumnIndexOrThrow("payload")),
        fileName = getString(getColumnIndexOrThrow("fileName")),
        mimeType = getString(getColumnIndexOrThrow("mimeType")),
        timestamp = getLong(getColumnIndexOrThrow("timestamp")),
        ttl = getInt(getColumnIndexOrThrow("ttl")),
        hopCount = getInt(getColumnIndexOrThrow("hopCount")),
        hopPath = try { json.decodeFromString<List<String>>(hopPathStr) } catch(e: Exception) { emptyList() },
        status = try { DeliveryStatus.valueOf(statusStr) } catch(e: Exception) { DeliveryStatus.QUEUED },
        ackForMessageId = getString(getColumnIndexOrThrow("ackForMessageId"))
    )
}

// ─── MeshPeer ↔ ContentValues/Cursor ───────────────────────────

fun MeshPeer.toContentValues(): ContentValues {
    return ContentValues().apply {
        put("deviceId", deviceId)
        put("displayName", displayName)
        put("lastSeen", lastSeen)
        if (rssi != null) put("rssi", rssi) else putNull("rssi")
        put("transportType", transportType.name)
        put("discoveryType", discoveryType.name)
        put("ipAddress", ipAddress)
        put("macAddress", macAddress)
        put("isConnected", if (isConnected) 1 else 0)
        put("bridgefyId", bridgefyId)
        put("username", username)
        if (latitude != null) put("latitude", latitude) else putNull("latitude")
        if (longitude != null) put("longitude", longitude) else putNull("longitude")
        if (locationAccuracyMeters != null) put("location_accuracy_meters", locationAccuracyMeters) else putNull("location_accuracy_meters")
        if (locationUpdatedAt != null) put("location_updated_at", locationUpdatedAt) else putNull("location_updated_at")
    }
}

fun Cursor.toMeshPeer(): MeshPeer {
    val tTypeStr = getString(getColumnIndexOrThrow("transportType"))
    val dTypeStr = getString(getColumnIndexOrThrow("discoveryType"))
    return MeshPeer(
        deviceId = getString(getColumnIndexOrThrow("deviceId")),
        displayName = getString(getColumnIndexOrThrow("displayName")),
        lastSeen = getLong(getColumnIndexOrThrow("lastSeen")),
        rssi = if (isNull(getColumnIndexOrThrow("rssi"))) null else getInt(getColumnIndexOrThrow("rssi")),
        transportType = try { TransportType.valueOf(tTypeStr) } catch(e: Exception) { TransportType.WIFI_DIRECT },
        discoveryType = try { TransportType.valueOf(dTypeStr) } catch(e: Exception) { TransportType.BLE },
        ipAddress = getString(getColumnIndexOrThrow("ipAddress")),
        macAddress = getString(getColumnIndexOrThrow("macAddress")),
        isConnected = getInt(getColumnIndexOrThrow("isConnected")) == 1,
        bridgefyId = getString(getColumnIndexOrThrow("bridgefyId")),
        username = getString(getColumnIndexOrThrow("username")),
        latitude = if (isNull(getColumnIndexOrThrow("latitude"))) null else getDouble(getColumnIndexOrThrow("latitude")),
        longitude = if (isNull(getColumnIndexOrThrow("longitude"))) null else getDouble(getColumnIndexOrThrow("longitude")),
        locationAccuracyMeters = if (isNull(getColumnIndexOrThrow("location_accuracy_meters"))) null else getFloat(getColumnIndexOrThrow("location_accuracy_meters")),
        locationUpdatedAt = if (isNull(getColumnIndexOrThrow("location_updated_at"))) null else getLong(getColumnIndexOrThrow("location_updated_at"))
    )
}
