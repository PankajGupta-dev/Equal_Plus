package com.bridgefy.app.transfer

import android.content.Context
import android.net.Uri
import android.util.Log
import android.webkit.MimeTypeMap
import com.bridgefy.app.model.*
import com.bridgefy.app.repository.MessageRepository
import com.bridgefy.app.transport.wifidirect.WiFiDirectTransport
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.*
import java.util.UUID
import kotlin.math.ceil

/**
 * Manages all binary file transfers (images, voice, generic files) over WiFi Direct.
 *
 * Responsibilities:
 * - Stream files over WiFi Direct sockets using chunked binary protocol
 * - Write received files to scoped storage ([FileStorage])
 * - Emit [TransferProgress] for real-time UI updates
 * - Create [MeshMessage] records with local file path references
 * - Handle timeouts and partial transfer cleanup
 *
 * Wire protocol (sending):
 * ```
 * [0x01 type byte][4-byte header len][JSON header][raw file bytes in 64KB chunks]
 * ```
 */
class FileTransferManager(
    private val context: Context,
    private val deviceId: String,
    private val wifiTransport: WiFiDirectTransport,
    private val fileStorage: FileStorage,
    private val repository: MessageRepository
) {
    companion object {
        private const val TAG = "FileTransferManager"
        private const val CHUNK_SIZE = 65536 // 64KB
        private const val MAX_FILE_SIZE = 20 * 1024 * 1024L // 20MB
    }

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // ─── Progress Tracking ───────────────────────────────────────

    private val _transfers = MutableStateFlow<Map<String, TransferProgress>>(emptyMap())
    /** All active/recent transfer progress snapshots, keyed by transferId */
    val activeTransfers: StateFlow<Map<String, TransferProgress>> = _transfers.asStateFlow()

    private val _receivedFiles = MutableSharedFlow<MeshMessage>(extraBufferCapacity = 16)
    /** Emitted when a file transfer is fully received and saved */
    val receivedFiles: SharedFlow<MeshMessage> = _receivedFiles.asSharedFlow()

    // ─── SENDING ─────────────────────────────────────────────────

    /**
     * Send a file from a content URI (gallery image, file picker, etc.).
     *
     * @param targetId Peer to send to (null = broadcast, not supported for binary)
     * @param uri Content URI of the file to send
     * @param type MESSAGE type (IMAGE, VOICE, FILE)
     * @return The created [MeshMessage], or null on failure
     */
    suspend fun sendFile(
        targetId: String?,
        uri: Uri,
        type: MessageType,
        caption: String? = null
    ): MeshMessage? = withContext(Dispatchers.IO) {
        try {
            if (targetId == null) {
                Log.e(TAG, "Binary transfers require a specific target peer")
                return@withContext null
            }

            val contentResolver = context.contentResolver
            val isFileScheme = uri.scheme == "file"
            val fileObj = if (isFileScheme) File(uri.path ?: "") else null
            
            val exists = if (isFileScheme) fileObj?.exists() == true else true
            val readable = if (isFileScheme) fileObj?.canRead() == true else true
            val ext = MimeTypeMap.getFileExtensionFromUrl(uri.toString()).ifEmpty { "mp4" }
            val isValidUri = uri.toString().startsWith("content://") || uri.toString().startsWith("file://")

            if (type == MessageType.VIDEO) {
                Log.d("VideoDebug", "[VideoDebug] File validation started")
                Log.d("VideoDebug", "[VideoDebug] Transfer preparation started")
                Log.d("VideoDebug", "[VideoDebug] File exists = $exists")
                Log.d("VideoDebug", "[VideoDebug] File readable = $readable")
                Log.d("VideoDebug", "[VideoDebug] Extension = $ext")
                Log.d("VideoDebug", "[VideoDebug] Valid content URI = $isValidUri")
                Log.d("VideoDebug", "[VideoDebug] Local path resolved successfully: ${uri.path}")
            } else {
                Log.d("MediaDebug", "[MediaDebug] Video selected")
                Log.d("MediaDebug", "[MediaDebug] File path = ${uri.toString()}")
                Log.d("MediaDebug", "[MediaDebug] File exists = $exists")
            }

            // Get file metadata
            val mimeType = if (isFileScheme) {
                val extMap = MimeTypeMap.getFileExtensionFromUrl(uri.toString())
                MimeTypeMap.getSingleton().getMimeTypeFromExtension(extMap) ?: "video/mp4"
            } else {
                contentResolver.getType(uri) ?: "application/octet-stream"
            }

            val fileSize = if (isFileScheme && fileObj != null) {
                fileObj.length()
            } else {
                contentResolver.openFileDescriptor(uri, "r")?.use {
                    it.statSize
                } ?: 0L
            }

            if (type == MessageType.VIDEO) {
                Log.d("VideoDebug", "[VideoDebug] File size = ${String.format(java.util.Locale.US, "%.1f", fileSize / (1024f * 1024f))}MB")
            } else {
                Log.d("MediaDebug", "[MediaDebug] File size = ${fileSize / (1024f * 1024f)} MB")
            }

            if (fileSize > MAX_FILE_SIZE) {
                Log.e(TAG, "File too large: $fileSize bytes (max: $MAX_FILE_SIZE)")
                return@withContext null
            }

            if (fileSize <= 0) {
                Log.e(TAG, "Empty file or invalid path: $uri")
                return@withContext null
            }

            // Generate unique filename
            val extension = MimeTypeMap.getSingleton()
                .getExtensionFromMimeType(mimeType) ?: "bin"
            val fileName = fileStorage.generateFileName(type, extension)
            val messageId = UUID.randomUUID().toString()
            val transferId = UUID.randomUUID().toString()
            val totalChunks = ceil(fileSize.toDouble() / CHUNK_SIZE).toInt()

            if (type == MessageType.VIDEO) {
                Log.d("VideoDebug", "[VideoDebug] Generating chunks")
                Log.d("VideoDebug", "[VideoDebug] Total chunks = $totalChunks")
                Log.d("VideoDebug", "[VideoDebug] Chunk size = 64KB")
            }

            // Save a local copy
            val localCopy = fileStorage.createFile(fileName)
            if (isFileScheme && fileObj != null) {
                FileInputStream(fileObj).use { input ->
                    FileOutputStream(localCopy).use { output ->
                        input.copyTo(output, CHUNK_SIZE)
                        output.flush()
                        output.fd.sync()
                    }
                }
            } else {
                contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(localCopy).use { output ->
                        input.copyTo(output, CHUNK_SIZE)
                        output.flush()
                        output.fd.sync()
                    }
                } ?: run {
                    Log.e(TAG, "Cannot open file for local copy: $uri")
                    return@withContext null
                }
            }

            val verifiedSize = localCopy.length()
            Log.d(TAG, "Local copy created: $fileName ($verifiedSize bytes)")

            // Build transfer header
            val header = FileTransferHeader(
                transferId = transferId,
                messageId = messageId,
                senderId = deviceId,
                targetId = targetId,
                fileName = fileName,
                mimeType = mimeType,
                fileSize = verifiedSize,
                chunkSize = CHUNK_SIZE,
                totalChunks = totalChunks,
                messageType = type,
                caption = caption
            )

            // Create MeshMessage record
            val dbFileName = if (!caption.isNullOrBlank()) "$fileName|$caption" else fileName
            val message = MeshMessage(
                id = messageId,
                senderId = deviceId,
                targetId = targetId,
                type = type,
                payload = fileName,
                fileName = dbFileName,
                mimeType = mimeType,
                timestamp = System.currentTimeMillis(),
                ttl = 3,
                hopCount = 0,
                hopPath = listOf(deviceId),
                status = DeliveryStatus.SENDING
            )
            repository.insertMessage(message)
            if (type == MessageType.VIDEO) {
                Log.d("VideoDebug", "[VideoDebug] SQLite metadata saved")
            } else {
                Log.d("MediaDebug", "[MediaDebug] SQLite metadata saved")
            }

            // Initial progress
            updateProgress(transferId, messageId, 0, verifiedSize, TransferState.SENDING)

            // Stream send from LOCAL COPY
            val headerBytes = json.encodeToString(header).toByteArray(Charsets.UTF_8)
            val inputStream = FileInputStream(localCopy)

            Log.d("MediaDebug", "[MediaDebug] Starting chunk transfer")
            Log.d("MediaDebug", "[MediaDebug] Socket connected")

            val success = wifiTransport.sendBinaryTransfer(
                peerId = targetId,
                header = headerBytes,
                fileStream = inputStream,
                fileSize = verifiedSize,
                onProgress = { bytesSent ->
                    updateProgress(transferId, messageId, bytesSent, verifiedSize, TransferState.SENDING)
                }
            )

            // Update final status
            val finalStatus = if (success) DeliveryStatus.SENT else DeliveryStatus.FAILED
            repository.updateStatus(messageId, finalStatus)
            updateProgress(
                transferId, messageId, if (success) verifiedSize else 0, verifiedSize,
                if (success) TransferState.COMPLETED else TransferState.FAILED
            )

            if (success) {
                Log.d("MediaDebug", "[MediaDebug] Transfer success")
                Log.d(TAG, "File sent: $fileName ($verifiedSize bytes)")
            } else {
                Log.e(TAG, "File send failed: $fileName")
            }

            message.copy(status = finalStatus)
        } catch (e: Exception) {
            Log.e(TAG, "sendFile error", e)
            null
        }
    }

    /**
     * Send a voice recording file.
     *
     * @param targetId Peer to send to
     * @param audioFile The recorded .m4a file
     * @return The created [MeshMessage], or null on failure
     */
    suspend fun sendVoice(
        targetId: String?,
        audioFile: File
    ): MeshMessage? = withContext(Dispatchers.IO) {
        try {
            if (targetId == null) {
                Log.e(TAG, "Binary transfers require a specific target peer")
                return@withContext null
            }

            if (!audioFile.exists()) {
                Log.e(TAG, "Audio file not found: ${audioFile.absolutePath}")
                return@withContext null
            }

            val fileSize = audioFile.length()
            if (fileSize > MAX_FILE_SIZE || fileSize <= 0) {
                Log.e(TAG, "Invalid audio file size: $fileSize")
                return@withContext null
            }

            val fileName = fileStorage.generateFileName(MessageType.VOICE, "m4a")
            val messageId = UUID.randomUUID().toString()
            val transferId = UUID.randomUUID().toString()
            val totalChunks = ceil(fileSize.toDouble() / CHUNK_SIZE).toInt()

            val header = FileTransferHeader(
                transferId = transferId,
                messageId = messageId,
                senderId = deviceId,
                targetId = targetId,
                fileName = fileName,
                mimeType = "audio/mp4",
                fileSize = fileSize,
                chunkSize = CHUNK_SIZE,
                totalChunks = totalChunks,
                messageType = MessageType.VOICE
            )

            val message = MeshMessage(
                id = messageId,
                senderId = deviceId,
                targetId = targetId,
                type = MessageType.VOICE,
                payload = fileName,
                fileName = fileName,
                mimeType = "audio/mp4",
                timestamp = System.currentTimeMillis(),
                ttl = 3,
                hopCount = 0,
                hopPath = listOf(deviceId),
                status = DeliveryStatus.SENDING
            )
            repository.insertMessage(message)
            updateProgress(transferId, messageId, 0, fileSize, TransferState.SENDING)

            // Copy to media dir before sending (so sender also has a local copy)
            val localCopy = fileStorage.createFile(fileName)
            audioFile.copyTo(localCopy, overwrite = true)

            // BUG FIX: Read size from the stable local copy, not the temp file
            val verifiedSize = localCopy.length()
            if (verifiedSize != fileSize) {
                Log.w(TAG, "File size mismatch after copy: expected=$fileSize actual=$verifiedSize")
            }
            Log.d(TAG, "Voice file ready for send: $fileName ($verifiedSize bytes)")

            val headerBytes = json.encodeToString(header).toByteArray(Charsets.UTF_8)
            val inputStream = FileInputStream(localCopy)

            val success = wifiTransport.sendBinaryTransfer(
                peerId = targetId,
                header = headerBytes,
                fileStream = inputStream,
                fileSize = verifiedSize,
                onProgress = { bytesSent ->
                    Log.v(TAG, "Voice send progress: $bytesSent / $verifiedSize bytes")
                    updateProgress(transferId, messageId, bytesSent, verifiedSize, TransferState.SENDING)
                }
            )

            val finalStatus = if (success) DeliveryStatus.SENT else DeliveryStatus.FAILED
            repository.updateStatus(messageId, finalStatus)
            updateProgress(
                transferId, messageId, if (success) verifiedSize else 0, verifiedSize,
                if (success) TransferState.COMPLETED else TransferState.FAILED
            )

            // BUG FIX: Only delete original temp file on SUCCESS
            if (success) {
                audioFile.delete()
                Log.d(TAG, "Voice sent successfully: $fileName ($verifiedSize bytes)")
            } else {
                Log.e(TAG, "Voice send failed, keeping temp file: ${audioFile.absolutePath}")
            }

            message.copy(status = finalStatus)
        } catch (e: Exception) {
            Log.e(TAG, "sendVoice error", e)
            null
        }
    }

    // ─── RECEIVING ───────────────────────────────────────────────

    /**
     * Handle an incoming binary transfer from a connected peer.
     *
     * Called by [WiFiDirectTransport.handleClientConnection] when type byte = 0x01.
     *
     * @param inputStream The socket's DataInputStream (positioned after type byte)
     * @param senderIP IP address of the sending peer
     * @return The received [MeshMessage], or null on failure
     */
    suspend fun handleIncomingTransfer(
        inputStream: DataInputStream,
        senderIP: String
    ): MeshMessage? = withContext(Dispatchers.IO) {
        var outputFile: File? = null

        try {
            // Read header
            val headerLen = inputStream.readInt()
            if (headerLen <= 0 || headerLen > 65536) {
                Log.e(TAG, "Invalid header length: $headerLen from $senderIP")
                return@withContext null
            }

            val headerBytes = ByteArray(headerLen)
            inputStream.readFully(headerBytes)
            val header = json.decodeFromString<FileTransferHeader>(String(headerBytes, Charsets.UTF_8))

            Log.d(TAG, "Receiving: ${header.fileName} (${header.fileSize} bytes) from $senderIP")
            if (header.messageType == MessageType.VIDEO) {
                Log.d("ReceiverDebug", "[ReceiverDebug] Receiving video chunks")
            }

            // Validate
            if (header.fileSize > MAX_FILE_SIZE) {
                Log.e(TAG, "Incoming file too large: ${header.fileSize}")
                return@withContext null
            }

            // Create output file
            outputFile = fileStorage.createFile(header.fileName)
            var received = 0L

            updateProgress(
                header.transferId, header.messageId, 0, header.fileSize, TransferState.RECEIVING
            )

            // Stream chunks to disk
            FileOutputStream(outputFile).use { fos ->
                val buffer = ByteArray(header.chunkSize)
                while (received < header.fileSize) {
                    val toRead = minOf(
                        header.chunkSize.toLong(),
                        header.fileSize - received
                    ).toInt()
                    inputStream.readFully(buffer, 0, toRead)
                    fos.write(buffer, 0, toRead)
                    received += toRead

                    updateProgress(
                        header.transferId, header.messageId,
                        received, header.fileSize, TransferState.RECEIVING
                    )
                }
                fos.flush()
                fos.fd.sync() // Force OS to flush to physical storage
            }

            // BUG FIX: Verify received file size matches header
            val actualSize = outputFile.length()
            Log.d(TAG, "Received: ${header.fileName} (stream=$received bytes, disk=$actualSize bytes, expected=${header.fileSize})")

            if (received != header.fileSize || actualSize != header.fileSize) {
                Log.e(TAG, "FILE SIZE MISMATCH! expected=${header.fileSize} received=$received disk=$actualSize")
                outputFile.delete()
                return@withContext null
            }

            if (header.messageType == MessageType.VIDEO) {
                Log.d("ReceiverDebug", "[ReceiverDebug] Chunks received successfully")
                Log.d("ReceiverDebug", "[ReceiverDebug] File reconstructed")
                Log.d("ReceiverDebug", "[ReceiverDebug] Video saved locally")
            }

            // Create message record
            val dbFileName = if (!header.caption.isNullOrBlank()) "${header.fileName}|${header.caption}" else header.fileName
            val message = MeshMessage(
                id = header.messageId,
                senderId = header.senderId,
                targetId = header.targetId,
                type = header.messageType,
                payload = header.fileName,
                fileName = dbFileName,
                mimeType = header.mimeType,
                timestamp = System.currentTimeMillis(),
                ttl = 3,
                hopCount = 0,
                hopPath = listOf(header.senderId),
                status = DeliveryStatus.DELIVERED
            )
            repository.insertMessage(message)

            updateProgress(
                header.transferId, header.messageId,
                header.fileSize, header.fileSize, TransferState.COMPLETED
            )

            _receivedFiles.emit(message)
            message
        } catch (e: Exception) {
            Log.e(TAG, "Incoming transfer failed from $senderIP", e)
            // Clean up partial file
            outputFile?.let {
                if (it.exists()) {
                    it.delete()
                    Log.d(TAG, "Cleaned up partial file: ${it.name}")
                }
            }
            null
        }
    }

    // ─── Progress Helpers ────────────────────────────────────────

    private fun updateProgress(
        transferId: String,
        messageId: String,
        bytesTransferred: Long,
        totalBytes: Long,
        state: TransferState
    ) {
        val progress = TransferProgress(
            transferId = transferId,
            messageId = messageId,
            bytesTransferred = bytesTransferred,
            totalBytes = totalBytes,
            state = state
        )

        _transfers.update { map ->
            if (state == TransferState.COMPLETED || state == TransferState.FAILED) {
                // Keep completed/failed for 5 seconds then remove
                scope.launch {
                    delay(5000)
                    _transfers.update { it - transferId }
                }
            }
            map + (transferId to progress)
        }
    }

    /**
     * Get progress for a specific message being transferred.
     */
    fun getProgressForMessage(messageId: String): TransferProgress? {
        return _transfers.value.values.find { it.messageId == messageId }
    }

    /**
     * Clean up resources.
     */
    fun release() {
        scope.cancel()
    }
}
