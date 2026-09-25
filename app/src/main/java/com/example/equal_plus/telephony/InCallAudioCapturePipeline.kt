package com.example.equal_plus.telephony

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sqrt

/**
 * Pipeline to capture incoming call audio via AudioRecord, isolate the user microphone
 * so the caller speaks only to the AI, and stream PCM audio chunks to the STT pipeline.
 */
class InCallAudioCapturePipeline(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) {
    companion object {
        private const val TAG = "InCallAudioCapture"
        const val SAMPLE_RATE = 16000
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    private var audioRecord: AudioRecord? = null
    private var captureJob: Job? = null
    private val _audioChunks = MutableSharedFlow<ByteArray>(extraBufferCapacity = 64)
    val audioChunks: SharedFlow<ByteArray> = _audioChunks.asSharedFlow()

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    @Volatile
    var isCapturing: Boolean = false
        private set

    @Volatile
    var isUserConnected: Boolean = false
        private set

    /**
     * Initializes the in-call audio capture pipeline and isolates the user's microphone.
     */
    fun startCapture(onChunk: ((ByteArray) -> Unit)? = null): Boolean {
        if (isCapturing) {
            Log.d(TAG, "Audio capture is already running.")
            return true
        }

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Cannot start in-call audio capture: RECORD_AUDIO permission missing")
            return false
        }

        val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        if (minBufferSize <= 0) {
            Log.e(TAG, "Invalid minBufferSize for AudioRecord: $minBufferSize")
            return false
        }

        val bufferSize = minBufferSize.coerceAtLeast(SAMPLE_RATE * 2) // ~1 second buffer

        // Try VOICE_COMMUNICATION first for AEC/NS, fallback to MIC
        audioRecord = createAudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, bufferSize)
            ?: createAudioRecord(MediaRecorder.AudioSource.MIC, bufferSize)

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord initialization failed")
            audioRecord?.release()
            audioRecord = null
            return false
        }

        // Isolate user microphone so the caller does not hear the human user
        isolateUserMicrophone()

        try {
            audioRecord?.startRecording()
            isCapturing = true
            isUserConnected = false
            Log.i(TAG, "In-call audio capture pipeline started successfully (User mic isolated)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start AudioRecord recording", e)
            stopCapture()
            return false
        }

        captureJob = scope.launch {
            val buffer = ByteArray(2048)
            while (isActive && isCapturing) {
                val bytesRead = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                if (bytesRead > 0) {
                    val chunk = buffer.copyOf(bytesRead)
                    _audioChunks.emit(chunk)
                    onChunk?.invoke(chunk)
                }
            }
        }

        return true
    }

    private fun createAudioRecord(source: Int, bufferSize: Int): AudioRecord? {
        return try {
            AudioRecord(source, SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT, bufferSize)
        } catch (e: Exception) {
            Log.w(TAG, "Could not create AudioRecord with source $source: ${e.message}")
            null
        }
    }

    /**
     * Mutes user's physical microphone so the caller is isolated with the AI assistant.
     */
    fun isolateUserMicrophone() {
        try {
            audioManager?.mode = AudioManager.MODE_IN_CALL
            audioManager?.isMicrophoneMute = true
            isUserConnected = false
            Log.i(TAG, "User microphone MUTED for AI screening isolation.")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to isolate user microphone: ${e.message}")
        }
    }

    /**
     * Unmutes user's microphone and routes audio to user when AI forwards the call.
     */
    fun forwardCallToUser() {
        try {
            audioManager?.mode = AudioManager.MODE_IN_CALL
            audioManager?.isMicrophoneMute = false
            audioManager?.isSpeakerphoneOn = false
            isUserConnected = true
            Log.i(TAG, "Call FORWARDED to user: microphone UNMUTED for direct conversation.")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to unmute microphone during call forwarding: ${e.message}")
        }
    }

    /**
     * Stops the audio capture and restores audio settings.
     */
    fun stopCapture() {
        isCapturing = false
        captureJob?.cancel()
        captureJob = null

        try {
            if (audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                audioRecord?.stop()
            }
            audioRecord?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing AudioRecord: ${e.message}")
        } finally {
            audioRecord = null
        }

        // Unmute microphone back to default
        try {
            audioManager?.isMicrophoneMute = false
        } catch (e: Exception) {
            Log.w(TAG, "Error unmuting microphone: ${e.message}")
        }

        Log.i(TAG, "In-call audio capture pipeline stopped.")
    }

    /**
     * Calculates the RMS volume of a 16-bit PCM chunk to detect voice presence.
     */
    fun calculateRms(buffer: ByteArray): Double {
        var sum = 0.0
        val numSamples = buffer.size / 2
        if (numSamples == 0) return 0.0

        for (i in 0 until numSamples) {
            val sample = ((buffer[i * 2 + 1].toInt() shl 8) or (buffer[i * 2].toInt() and 0xFF)).toShort()
            sum += sample * sample
        }
        return sqrt(sum / numSamples)
    }
}
