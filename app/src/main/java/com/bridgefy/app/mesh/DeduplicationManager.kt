package com.bridgefy.app.mesh

import android.util.Log
import com.bridgefy.app.db.DatabaseProvider
import com.bridgefy.app.db.SeenMessageQueries
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.LinkedList

/**
 * Memory-efficient duplicate message detection for the mesh network.
 *
 * Uses a bounded in-memory LRU set backed by SQLite persistence.
 * This prevents the same message from being processed/forwarded multiple
 * times as it propagates through the mesh.
 *
 * Design:
 * - In-memory LinkedHashSet capped at [MAX_CACHE_SIZE] (LRU eviction)
 * - Backed by SQLite `seen_messages` table for persistence across restarts
 * - Automatic cleanup of records older than [CLEANUP_AGE_MS] (24 hours)
 */
class DeduplicationManager {

    companion object {
        private const val TAG = "DeduplicationManager"
        private const val MAX_CACHE_SIZE = 10_000
        private const val CLEANUP_AGE_MS = 24 * 60 * 60 * 1000L // 24 hours
    }

    private val cache = LinkedHashSet<String>()
    private val insertionOrder = LinkedList<String>()
    private val mutex = Mutex()

    /**
     * Load persisted seen IDs from database.
     * Call once during initialization.
     */
    suspend fun initialize() {
        mutex.withLock {
            try {
                val all = SeenMessageQueries.getAllSeen(DatabaseProvider.db)
                for (pair in all) {
                    addToCache(pair.first)
                }
                Log.d(TAG, "Initialized with ${cache.size} seen message IDs")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize deduplication cache", e)
            }
        }
    }

    /**
     * Check if a message ID has been seen before.
     *
     * @return true if this message has already been processed
     */
    suspend fun isDuplicate(messageId: String): Boolean {
        mutex.withLock {
            // Fast path: in-memory check
            if (cache.contains(messageId)) return true

            // Slow path: database check (for IDs evicted from memory)
            return try {
                SeenMessageQueries.hasSeen(DatabaseProvider.db, messageId)
            } catch (e: Exception) {
                false
            }
        }
    }

    /**
     * Mark a message ID as seen (processed).
     * Persists to both in-memory cache and SQLite database.
     */
    suspend fun markSeen(messageId: String) {
        mutex.withLock {
            addToCache(messageId)

            try {
                SeenMessageQueries.insertSeen(DatabaseProvider.db, messageId)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to persist seen message: $messageId", e)
            }
        }
    }

    /**
     * Remove old deduplication records from both memory and database.
     * Should be called periodically (e.g., every hour).
     */
    suspend fun cleanup() {
        try {
            val cutoff = System.currentTimeMillis() - CLEANUP_AGE_MS
            SeenMessageQueries.deleteOlderThan(DatabaseProvider.db, cutoff)

            val count = SeenMessageQueries.count(DatabaseProvider.db)
            Log.d(TAG, "Cleanup complete. Remaining seen records: $count")
        } catch (e: Exception) {
            Log.e(TAG, "Cleanup failed", e)
        }
    }

    /**
     * Get the current size of the deduplication cache.
     */
    fun cacheSize(): Int = cache.size

    // ─── Private ─────────────────────────────────────────────────

    private fun addToCache(messageId: String) {
        if (cache.add(messageId)) {
            insertionOrder.add(messageId)

            // Evict oldest entries if cache exceeds limit
            while (cache.size > MAX_CACHE_SIZE) {
                val oldest = insertionOrder.poll()
                if (oldest != null) {
                    cache.remove(oldest)
                }
            }
        }
    }
}
