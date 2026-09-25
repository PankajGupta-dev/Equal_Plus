package com.bridgefy.app.repository

import android.util.Log
import com.bridgefy.app.db.DatabaseProvider
import com.bridgefy.app.db.MessageQueries
import com.bridgefy.app.db.SeenMessageQueries
import com.bridgefy.app.model.DeliveryStatus
import com.bridgefy.app.model.MeshMessage

/**
 * Single source of truth for mesh message persistence.
 *
 * Wraps SQLite operations with domain-level APIs.
 */
class MessageRepository {

    companion object {
        private const val TAG = "MessageRepository"
        /** Messages with TTL 0 older than this are cleaned up */
        private const val EXPIRED_CLEANUP_AGE_MS = 24 * 60 * 60 * 1000L // 24 hours
    }

    private val db get() = DatabaseProvider.db

    // ─── Message CRUD ────────────────────────────────────────────

    /**
     * Insert or replace a message.
     */
    suspend fun insertMessage(message: MeshMessage) {
        try {
            MessageQueries.insertMessage(db, message)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to insert message ${message.id}", e)
        }
    }

    /**
     * Update a message's delivery status.
     */
    suspend fun updateStatus(messageId: String, status: DeliveryStatus) {
        try {
            MessageQueries.updateStatus(db, messageId, status.name)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update status for $messageId", e)
        }
    }

    /**
     * Get a message by its ID.
     */
    suspend fun getById(messageId: String): MeshMessage? {
        return try {
            MessageQueries.getById(db, messageId)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get message $messageId", e)
            null
        }
    }

    // ─── Conversation Queries ────────────────────────────────────

    /**
     * Get conversation with a specific peer.
     * Returns only TEXT, IMAGE, FILE messages sorted by timestamp.
     */
    suspend fun getConversation(peerId: String): List<MeshMessage> {
        return try {
            MessageQueries.getConversation(db, peerId)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get conversation", e)
            emptyList()
        }
    }

    /**
     * Delete all messages in a conversation.
     * Called when user backs out of chat (message expiry policy).
     */
    suspend fun deleteConversation(peerId: String) {
        try {
            MessageQueries.deleteConversation(db, peerId)
            Log.d(TAG, "Deleted conversation with $peerId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete conversation with $peerId", e)
        }
    }

    // ─── Store-and-Forward Queue ─────────────────────────────────

    /**
     * Get messages that need to be relayed to other peers.
     */
    suspend fun getQueuedForRelay(): List<MeshMessage> {
        return try {
            MessageQueries.getPendingForRelay(db)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get relay queue", e)
            emptyList()
        }
    }

    // ─── Cleanup ─────────────────────────────────────────────────

    /**
     * Delete expired messages and old deduplication records.
     */
    suspend fun cleanupExpired() {
        try {
            val cutoff = System.currentTimeMillis() - EXPIRED_CLEANUP_AGE_MS
            MessageQueries.deleteExpired(db, cutoff)
            SeenMessageQueries.deleteOlderThan(db, cutoff)
            Log.d(TAG, "Cleanup completed")
        } catch (e: Exception) {
            Log.e(TAG, "Cleanup failed", e)
        }
    }

    // ─── Statistics ──────────────────────────────────────────────

    suspend fun countByStatus(status: DeliveryStatus): Int {
        return try {
            MessageQueries.countByStatus(db, status.name)
        } catch (e: Exception) {
            0
        }
    }

    suspend fun countDataMessages(): Int {
        return try {
            MessageQueries.countDataMessages(db)
        } catch (e: Exception) {
            0
        }
    }

    // ─── Seen Messages ───────────────────────────────────────────

    suspend fun markSeen(messageId: String) {
        try {
            SeenMessageQueries.insertSeen(db, messageId)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to mark seen: $messageId", e)
        }
    }

    suspend fun hasSeen(messageId: String): Boolean {
        return try {
            SeenMessageQueries.hasSeen(db, messageId)
        } catch (e: Exception) {
            false
        }
    }
}
