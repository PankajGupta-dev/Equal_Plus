package com.bridgefy.app.db

import android.database.sqlite.SQLiteDatabase
import com.bridgefy.app.model.MeshPeer

object PeerQueries {
    fun insertPeer(db: SQLiteDatabase, peer: MeshPeer) {
        val cv = peer.toContentValues()
        db.insertWithOnConflict("peers", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    /**
     * Insert or update a peer, cleaning up duplicate rows that may exist
     * from the MAC → UUID identity upgrade lifecycle.
     *
     * When WiFi Direct first discovers a device, it creates a row keyed by
     * MAC address. After the handshake, a second row keyed by UUID is created.
     * This method ensures only the UUID-keyed row survives.
     */
    fun upsertPeer(db: SQLiteDatabase, peer: MeshPeer) {
        // If this peer has a MAC address, delete any old row where deviceId = MAC
        // (but only if the current peer's deviceId is NOT the MAC itself)
        if (peer.macAddress != null && peer.deviceId != peer.macAddress) {
            db.delete("peers", "deviceId = ?", arrayOf(peer.macAddress))
        }
        // If this peer has a bridgefyId, clean up any other rows with the same bridgefyId
        // but a different deviceId (stale duplicates from prior sessions)
        if (peer.bridgefyId != null) {
            db.delete(
                "peers",
                "bridgefyId = ? AND deviceId != ?",
                arrayOf(peer.bridgefyId, peer.deviceId)
            )
        }
        val cv = peer.toContentValues()
        db.insertWithOnConflict("peers", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun getActivePeers(db: SQLiteDatabase, since: Long): List<MeshPeer> {
        val peers = mutableListOf<MeshPeer>()
        db.rawQuery("SELECT * FROM peers WHERE lastSeen >= ? ORDER BY lastSeen DESC", arrayOf(since.toString())).use { cursor ->
            while (cursor.moveToNext()) {
                peers.add(cursor.toMeshPeer())
            }
        }
        return peers
    }

    /**
     * Find a peer by its BridgeFy identity UUID.
     * Used for deduplication when the same physical device is seen via multiple transports.
     */
    fun getByBridgefyId(db: SQLiteDatabase, bridgefyId: String): MeshPeer? {
        db.rawQuery(
            "SELECT * FROM peers WHERE bridgefyId = ? LIMIT 1",
            arrayOf(bridgefyId)
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                return cursor.toMeshPeer()
            }
        }
        return null
    }

    /**
     * Update the connection status of a peer.
     */
    fun updateConnectionStatus(db: SQLiteDatabase, deviceId: String, isConnected: Boolean) {
        val cv = android.content.ContentValues().apply {
            put("isConnected", if (isConnected) 1 else 0)
        }
        db.update("peers", cv, "deviceId = ?", arrayOf(deviceId))
    }

    fun deleteStale(db: SQLiteDatabase, before: Long) {
        db.delete("peers", "lastSeen < ?", arrayOf(before.toString()))
    }

    /**
     * Update a peer's location — called ONLY when processing a LOCATION_PING.
     * NEVER called for LOCATION_SHARE messages.
     */
    fun updatePeerLocation(
        db: SQLiteDatabase,
        deviceId: String,
        latitude: Double,
        longitude: Double,
        accuracyMeters: Float,
        updatedAt: Long
    ) {
        val cv = android.content.ContentValues().apply {
            put("latitude", latitude)
            put("longitude", longitude)
            put("location_accuracy_meters", accuracyMeters)
            put("location_updated_at", updatedAt)
        }
        db.update("peers", cv, "deviceId = ?", arrayOf(deviceId))
    }

    /**
     * Get all peers that have known GPS coordinates — for map rendering.
     */
    fun getPeersWithLocation(db: SQLiteDatabase): List<MeshPeer> {
        val peers = mutableListOf<MeshPeer>()
        db.rawQuery(
            "SELECT * FROM peers WHERE latitude IS NOT NULL AND longitude IS NOT NULL ORDER BY location_updated_at DESC",
            emptyArray()
        ).use { cursor ->
            while (cursor.moveToNext()) {
                peers.add(cursor.toMeshPeer())
            }
        }
        return peers
    }
}
