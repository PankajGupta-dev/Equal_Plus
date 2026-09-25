package com.bridgefy.app.model

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Metadata header sent before a binary file transfer.
 *
 * Wire protocol: [0x01 type byte][4-byte header length][this JSON][raw file bytes]
 *
 * The receiver uses this to create the output file, allocate buffers,
 * and track progress before the raw bytes start streaming.
 */
@Serializable
data class FileTransferHeader(
    /** Unique transfer session ID for progress tracking */
    val transferId: String = UUID.randomUUID().toString(),
    /** MeshMessage ID this transfer belongs to */
    val messageId: String,
    /** Device UUID of the sender */
    val senderId: String,
    /** Device UUID of the intended recipient; null = broadcast */
    val targetId: String? = null,
    /** Human-readable file name (e.g., IMG_20260419_001200_a1b2.jpg) */
    val fileName: String,
    /** MIME type (e.g., image/jpeg, audio/mp4) */
    val mimeType: String,
    /** Total file size in bytes */
    val fileSize: Long,
    /** Chunk size used for streaming (default 64KB) */
    val chunkSize: Int = 65536,
    /** Total number of chunks = ceil(fileSize / chunkSize) */
    val totalChunks: Int,
    /** Message type: IMAGE, FILE, or VOICE */
    val messageType: MessageType,
    /** Optional caption for images or videos */
    val caption: String? = null
)
