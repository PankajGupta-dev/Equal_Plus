package com.bridgefy.app.model

/**
 * Real-time progress snapshot for an active file transfer.
 *
 * Emitted by [FileTransferManager] after each chunk is sent or received,
 * consumed by the UI to drive progress bars and status indicators.
 */
data class TransferProgress(
    /** Unique transfer session ID */
    val transferId: String,
    /** MeshMessage ID this transfer belongs to */
    val messageId: String,
    /** Bytes transferred so far */
    val bytesTransferred: Long,
    /** Total file size in bytes */
    val totalBytes: Long,
    /** Current transfer phase */
    val state: TransferState
) {
    /** Progress fraction 0.0 – 1.0 */
    val progress: Float
        get() = if (totalBytes > 0) bytesTransferred.toFloat() / totalBytes else 0f
}

/**
 * Lifecycle states of a file transfer session.
 */
enum class TransferState {
    /** Queued locally, waiting for socket */
    QUEUED,
    /** Actively streaming bytes to the peer */
    SENDING,
    /** Actively receiving bytes from the peer */
    RECEIVING,
    /** Transfer finished successfully */
    COMPLETED,
    /** Transfer failed (timeout, disconnect, I/O error) */
    FAILED
}
