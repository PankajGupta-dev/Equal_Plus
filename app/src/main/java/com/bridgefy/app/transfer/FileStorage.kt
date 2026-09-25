package com.bridgefy.app.transfer

import android.content.Context
import android.util.Log
import com.bridgefy.app.model.MessageType
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Manages scoped storage for received and sent media files.
 *
 * All media is stored in [Context.getFilesDir]/alertnet_media/ which is
 * private to the app and survives app updates. No WRITE_EXTERNAL_STORAGE needed.
 */
class FileStorage(private val context: Context) {

    companion object {
        private const val TAG = "FileStorage"
        private const val MEDIA_DIR = "alertnet_media"
    }

    /** Root directory for all AlertNet media files */
    private val mediaDir: File by lazy {
        File(context.filesDir, MEDIA_DIR).also { it.mkdirs() }
    }

    /**
     * Generate a unique, human-readable filename.
     *
     * Format: `{PREFIX}_{yyyyMMdd_HHmmss}_{4-char-uuid}.{ext}`
     * Example: `IMG_20260419_001200_a1b2.jpg`
     */
    fun generateFileName(type: MessageType, extension: String): String {
        val prefix = when (type) {
            MessageType.IMAGE -> "IMG"
            MessageType.VIDEO -> "VID"
            MessageType.VOICE -> "VOICE"
            else -> "FILE"
        }
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val random = UUID.randomUUID().toString().take(4)
        return "${prefix}_${timestamp}_$random.$extension"
    }

    /**
     * Create a new file in the media directory.
     * Parent directories are created if needed.
     */
    fun createFile(fileName: String): File {
        return File(mediaDir, fileName).also {
            it.parentFile?.mkdirs()
        }
    }

    /**
     * Get an existing file from the media directory, or null if it doesn't exist.
     */
    fun getFile(fileName: String): File? {
        val file = File(mediaDir, fileName)
        return if (file.exists()) file else null
    }

    /**
     * Get the absolute path for a file in the media directory.
     */
    fun getFilePath(fileName: String): String {
        return File(mediaDir, fileName).absolutePath
    }

    /**
     * Delete a file from the media directory.
     */
    fun deleteFile(fileName: String): Boolean {
        val file = File(mediaDir, fileName)
        return if (file.exists()) {
            file.delete().also { success ->
                if (success) Log.d(TAG, "Deleted: $fileName")
                else Log.w(TAG, "Failed to delete: $fileName")
            }
        } else false
    }

    /**
     * Delete files older than [maxAgeMs] milliseconds.
     * Called periodically from maintenance loop.
     */
    fun cleanupOldFiles(maxAgeMs: Long) {
        val cutoff = System.currentTimeMillis() - maxAgeMs
        val files = mediaDir.listFiles() ?: return
        var deleted = 0

        for (file in files) {
            if (file.lastModified() < cutoff) {
                if (file.delete()) deleted++
            }
        }

        if (deleted > 0) {
            Log.d(TAG, "Cleaned up $deleted old media files")
        }
    }

    /**
     * Total size of all stored media files in bytes.
     */
    fun getTotalSize(): Long {
        return mediaDir.listFiles()?.sumOf { it.length() } ?: 0L
    }

    /**
     * Number of stored media files.
     */
    fun getFileCount(): Int {
        return mediaDir.listFiles()?.size ?: 0
    }
}
