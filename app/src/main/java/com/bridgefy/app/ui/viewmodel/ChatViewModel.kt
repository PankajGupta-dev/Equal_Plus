package com.bridgefy.app.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bridgefy.app.media.PlayerState
import com.bridgefy.app.media.RecorderState
import com.bridgefy.app.mesh.MeshManager
import com.bridgefy.app.model.DeliveryStatus
import com.bridgefy.app.model.MeshMessage
import com.bridgefy.app.model.MessageType
import com.bridgefy.app.model.TransferProgress
import com.bridgefy.app.model.LocationSharePayload
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import java.io.File

/**
 * ViewModel for the chat screen.
 *
 * Provides reactive message list, send operations, voice recording/playback,
 * file transfer progress, and message expiry when the user navigates away.
 */
class ChatViewModel(
    private val meshManager: MeshManager,
    val deviceId: String
) : ViewModel() {

    private var currentPeerId: String? = null

    private val _messages = MutableStateFlow<List<MeshMessage>>(emptyList())
    /** Messages in the current conversation */
    val messages: StateFlow<List<MeshMessage>> = _messages.asStateFlow()

    private val _sendingState = MutableStateFlow<SendingState>(SendingState.Idle)
    val sendingState: StateFlow<SendingState> = _sendingState.asStateFlow()

    private var pollJob: Job? = null

    // ─── Voice Recording ─────────────────────────────────────────

    /** Voice recorder state (IDLE / RECORDING) */
    val voiceRecorderState: StateFlow<RecorderState> = meshManager.voiceRecorder.state

    /** Recording amplitude for waveform UI */
    val voiceAmplitude: StateFlow<Int> = meshManager.voiceRecorder.amplitude

    /** Recording duration in ms */
    val voiceRecordingDuration: StateFlow<Long> = meshManager.voiceRecorder.durationMs

    // ─── Voice Playback ──────────────────────────────────────────

    /** Voice player state (IDLE / LOADING / PLAYING / PAUSED / ERROR) */
    val voicePlayerState: StateFlow<PlayerState> = meshManager.voicePlayer.state

    /** Currently playing message ID */
    val playingMessageId: StateFlow<String?> = meshManager.voicePlayer.currentMessageId

    /** Playback progress (0.0 – 1.0) */
    val playbackProgress: StateFlow<Float> = meshManager.voicePlayer.progress

    /** Playback duration in ms */
    val playbackDuration: StateFlow<Int> = meshManager.voicePlayer.durationMs

    // ─── Transfer Progress ───────────────────────────────────────

    /** Active file transfer progress map */
    val activeTransfers: StateFlow<Map<String, TransferProgress>> =
        meshManager.fileTransferManager.activeTransfers

    // ─── Conversation Lifecycle ──────────────────────────────────

    /**
     * Set the peer we're chatting with and start polling the conversation.
     */
    fun openConversation(peerId: String) {
        currentPeerId = peerId
        startPolling()

        // Also observe ACK-driven delivery updates
        viewModelScope.launch {
            meshManager.ackTracker.deliveredMessages.collect { deliveredIds ->
                refreshMessages()
            }
        }
    }

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (isActive && currentPeerId != null) {
                refreshMessages()
                delay(2000L) // Poll every 2 seconds
            }
        }
    }

    private suspend fun refreshMessages() {
        currentPeerId?.let { peerId ->
            try {
                val msgs = meshManager.getConversation(peerId)
                _messages.value = msgs
            } catch (e: Exception) {
                // Ignore DB errors on refresh
            }
        }
    }

    // ─── Send Text ───────────────────────────────────────────────

    /**
     * Send a text message to the current peer.
     */
    fun sendTextMessage(text: String) {
        val peerId = currentPeerId ?: return
        if (text.isBlank()) return

        _sendingState.value = SendingState.Sending

        viewModelScope.launch {
            try {
                meshManager.sendTextMessage(peerId, text)
                refreshMessages()
                _sendingState.value = SendingState.Idle
            } catch (e: Exception) {
                _sendingState.value = SendingState.Error(e.message ?: "Send failed")
            }
        }
    }

    /**
     * Send a broadcast message (no specific target).
     */
    fun sendBroadcastMessage(text: String) {
        if (text.isBlank()) return

        viewModelScope.launch {
            try {
                meshManager.sendTextMessage(null, text)
            } catch (e: Exception) {
                _sendingState.value = SendingState.Error(e.message ?: "Broadcast failed")
            }
        }
    }

    // ─── Send Image & Video ──────────────────────────────────────

    /**
     * Send an image from gallery/camera to the current peer.
     */
    fun sendImage(uri: Uri, caption: String? = null) {
        val peerId = currentPeerId

        _sendingState.value = SendingState.Sending

        viewModelScope.launch {
            try {
                val result = meshManager.sendImage(peerId, uri, caption)
                if (result == null) {
                    _sendingState.value = SendingState.Error("Image send failed")
                } else {
                    refreshMessages()
                    _sendingState.value = SendingState.Idle
                }
            } catch (e: Exception) {
                _sendingState.value = SendingState.Error(e.message ?: "Image send failed")
            }
        }
    }

    /**
     * Send a video from gallery/camera to the current peer.
     */
    fun sendVideo(uri: Uri, caption: String? = null) {
        val peerId = currentPeerId

        _sendingState.value = SendingState.Sending

        viewModelScope.launch {
            try {
                val result = meshManager.sendVideo(peerId, uri, caption)
                if (result == null) {
                    _sendingState.value = SendingState.Error("Video send failed")
                } else {
                    refreshMessages()
                    _sendingState.value = SendingState.Idle
                }
            } catch (e: Exception) {
                _sendingState.value = SendingState.Error(e.message ?: "Video send failed")
            }
        }
    }

    /**
     * Send a file/image to the current peer (generic picker).
     */
    fun sendFile(uri: Uri) {
        val peerId = currentPeerId

        _sendingState.value = SendingState.Sending

        viewModelScope.launch {
            try {
                meshManager.sendFile(peerId, uri)
                refreshMessages()
                _sendingState.value = SendingState.Idle
            } catch (e: Exception) {
                _sendingState.value = SendingState.Error(e.message ?: "File send failed")
            }
        }
    }

    // ─── Send Location Share ─────────────────────────────────────

    /**
     * Send a location share message to the current peer.
     * Does NOT broadcast to mesh — it's a targeted chat message only.
     */
    fun sendLocationShare(payload: LocationSharePayload) {
        val peerId = currentPeerId ?: return

        _sendingState.value = SendingState.Sending

        viewModelScope.launch {
            try {
                meshManager.sendLocationShare(peerId, payload)
                refreshMessages()
                _sendingState.value = SendingState.Idle
            } catch (e: Exception) {
                _sendingState.value = SendingState.Error(e.message ?: "Location send failed")
            }
        }
    }

    // ─── Voice Recording ─────────────────────────────────────────

    /**
     * Start recording a voice message.
     */
    fun startVoiceRecording() {
        try {
            meshManager.voiceRecorder.startRecording()
        } catch (e: Exception) {
            _sendingState.value = SendingState.Error(e.message ?: "Recording failed")
        }
    }

    /**
     * Stop recording and send the voice message.
     */
    fun stopVoiceRecordingAndSend() {
        val peerId = currentPeerId ?: return

        val audioFile = meshManager.voiceRecorder.stopRecording() ?: return
        _sendingState.value = SendingState.Sending

        viewModelScope.launch {
            try {
                meshManager.sendVoiceMessage(peerId, audioFile)
                refreshMessages()
                _sendingState.value = SendingState.Idle
            } catch (e: Exception) {
                _sendingState.value = SendingState.Error(e.message ?: "Voice send failed")
            }
        }
    }

    /**
     * Cancel the current voice recording.
     */
    fun cancelVoiceRecording() {
        meshManager.voiceRecorder.cancelRecording()
    }

    // ─── Voice Playback ──────────────────────────────────────────

    /**
     * Play a received voice message.
     */
    fun playVoiceMessage(message: MeshMessage) {
        if (message.type != MessageType.VOICE) return
        val fileName = message.payload
        val filePath = meshManager.getMediaFilePath(fileName)

        if (File(filePath).exists()) {
            meshManager.voicePlayer.play(message.id, filePath)
        } else {
            _sendingState.value = SendingState.Error("Audio file not found")
        }
    }

    /**
     * Pause voice playback.
     */
    fun pauseVoicePlayback() {
        meshManager.voicePlayer.pause()
    }

    /**
     * Resume voice playback.
     */
    fun resumeVoicePlayback() {
        meshManager.voicePlayer.resume()
    }

    /**
     * Stop voice playback.
     */
    fun stopVoicePlayback() {
        meshManager.voicePlayer.stop()
    }

    /**
     * Seek voice playback to a position.
     */
    fun seekVoicePlayback(fraction: Float) {
        meshManager.voicePlayer.seekTo(fraction)
    }

    // ─── Helpers ─────────────────────────────────────────────────

    /**
     * Decrypt a message payload for display.
     */
    fun decryptPayload(message: MeshMessage): String {
        return when (message.type) {
            MessageType.TEXT -> meshManager.decryptPayload(message.payload)
            MessageType.ACK -> "✓ Delivered"
            MessageType.IMAGE -> {
                val parts = message.fileName?.split("|")
                parts?.getOrNull(1) ?: "Image"
            }
            MessageType.VIDEO -> {
                val parts = message.fileName?.split("|")
                parts?.getOrNull(1) ?: "Video"
            }
            MessageType.VOICE -> formatDuration(message)
            MessageType.LOCATION_SHARE -> "📍 Location"
            else -> message.fileName ?: "File"
        }
    }

    /**
     * Get the file path for a media message.
     */
    fun getMediaFilePath(message: MeshMessage): String? {
        val fileName = message.payload
        return if (fileName.isNotBlank()) meshManager.getMediaFilePath(fileName) else null
    }

    /**
     * Get transfer progress for a specific message.
     */
    fun getTransferProgress(messageId: String): TransferProgress? {
        return meshManager.fileTransferManager.getProgressForMessage(messageId)
    }

    private fun formatDuration(message: MeshMessage): String {
        // Voice messages store duration in the fileName suffix or we show generic label
        return "Voice message"
    }

    /**
     * Called when user navigates back from the chat screen.
     */
    fun onBackPressed() {
        val peerId = currentPeerId ?: return
        pollJob?.cancel()
        meshManager.voicePlayer.stop()
        viewModelScope.launch {
            meshManager.expireConversation(peerId)
        }
        currentPeerId = null
    }

    override fun onCleared() {
        super.onCleared()
        currentPeerId?.let { peerId ->
            viewModelScope.launch {
                meshManager.expireConversation(peerId)
            }
        }
    }
}

/**
 * State of the message sending operation.
 */
sealed class SendingState {
    data object Idle : SendingState()
    data object Sending : SendingState()
    data class Error(val message: String) : SendingState()
}
