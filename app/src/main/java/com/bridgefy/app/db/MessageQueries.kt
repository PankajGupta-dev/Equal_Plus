package com.bridgefy.app.db

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import com.bridgefy.app.model.MeshMessage

object MessageQueries {
    fun insertMessage(db: SQLiteDatabase, message: MeshMessage) {
        val cv = message.toContentValues()
        db.insertWithOnConflict("messages", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun updateStatus(db: SQLiteDatabase, messageId: String, status: String) {
        val cv = ContentValues().apply { put("status", status) }
        db.update("messages", cv, "id = ?", arrayOf(messageId))
    }

    fun getById(db: SQLiteDatabase, messageId: String): MeshMessage? {
        db.rawQuery("SELECT * FROM messages WHERE id = ? LIMIT 1", arrayOf(messageId)).use { cursor ->
            if (cursor.moveToFirst()) {
                return cursor.toMeshMessage()
            }
        }
        return null
    }

    fun getConversation(db: SQLiteDatabase, peerId: String): List<MeshMessage> {
        val messages = mutableListOf<MeshMessage>()
        db.rawQuery("""
            SELECT * FROM messages 
            WHERE (senderId = ? OR targetId = ?) 
            AND type IN ('TEXT', 'IMAGE', 'VIDEO', 'FILE', 'VOICE', 'LOCATION_SHARE')
            ORDER BY timestamp ASC
        """, arrayOf(peerId, peerId)).use { cursor ->
            while (cursor.moveToNext()) {
                messages.add(cursor.toMeshMessage())
            }
        }
        return messages
    }

    fun getPendingForRelay(db: SQLiteDatabase): List<MeshMessage> {
        val messages = mutableListOf<MeshMessage>()
        db.rawQuery("""
            SELECT * FROM messages 
            WHERE status IN ('QUEUED', 'SENT') 
            AND ttl > 0
            AND type IN ('TEXT', 'IMAGE', 'VIDEO', 'FILE', 'VOICE', 'ACK', 'LOCATION_PING')
            ORDER BY timestamp ASC
        """, emptyArray()).use { cursor ->
            while (cursor.moveToNext()) {
                messages.add(cursor.toMeshMessage())
            }
        }
        return messages
    }

    fun deleteConversation(db: SQLiteDatabase, peerId: String) {
        db.delete("messages", "(senderId = ? OR targetId = ?) AND type IN ('TEXT', 'IMAGE', 'VIDEO', 'FILE', 'VOICE')", arrayOf(peerId, peerId))
    }

    fun deleteExpired(db: SQLiteDatabase, before: Long) {
        db.delete("messages", "ttl <= 0 AND timestamp < ?", arrayOf(before.toString()))
    }

    fun countByStatus(db: SQLiteDatabase, status: String): Int {
        db.rawQuery("SELECT COUNT(*) FROM messages WHERE status = ?", arrayOf(status)).use { cursor ->
            if (cursor.moveToFirst()) {
                return cursor.getInt(0)
            }
        }
        return 0
    }

    fun countDataMessages(db: SQLiteDatabase): Int {
        db.rawQuery("SELECT COUNT(*) FROM messages WHERE type NOT IN ('ACK', 'PEER_ANNOUNCE', 'PEER_LEAVE', 'LOCATION_PING')", emptyArray()).use { cursor ->
            if (cursor.moveToFirst()) {
                return cursor.getInt(0)
            }
        }
        return 0
    }

    /**
     * Remove all queued LOCATION_PING messages from a specific sender.
     * Called before enqueuing a new ping to enforce one-ping-per-sender deduplication.
     */
    fun removeQueuedLocationPings(db: SQLiteDatabase, senderId: String) {
        db.delete(
            "messages",
            "type = 'LOCATION_PING' AND senderId = ? AND status IN ('QUEUED', 'SENT')",
            arrayOf(senderId)
        )
    }
}
