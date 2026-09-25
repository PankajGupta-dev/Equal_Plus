package com.bridgefy.app.media

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File

/**
 * Manages voice recording for voice messages using [MediaRecorder].
 *
 * Records to AAC/M4A format which provides good quality at small file sizes
 * and is universally supported on Android for playback.
 *
 * Usage:
 * ```
 * val recorder = VoiceRecorderManager(context)
 * val tempFile = recorder.startRecording()   // starts recording
 * // ... user holds record button ...
 * val finalFile = recorder.stopRecording()   // returns recorded file
 * // ... send finalFile via FileTransferManager ...
 * ```
 */
class VoiceRecorderManager(private val context: Context) {

    companion object {
        private const val TAG = "VoiceRecorder"
        /** Maximum recording duration (2 minutes) */
        const val MAX_DURATION_MS = 120_000
        private const val SAMPLE_RATE = 44100
        private const val BIT_RATE = 128000
        private const val AMPLITUDE_POLL_MS = 100L
    }

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var amplitudeJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _state = MutableStateFlow(RecorderState.IDLE)
    /** Current recorder state */
    val state: StateFlow<RecorderState> = _state.asStateFlow()

    private val _amplitude = MutableStateFlow(0)
    /** Current audio amplitude (0–32767), polled every 100ms for waveform visualization */
    val amplitude: StateFlow<Int> = _amplitude.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    /** Elapsed recording time in milliseconds */
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    /**
     * Start recording a voice message.
     *
     * @return The temporary output file (in cacheDir)
     * @throws IllegalStateException if already recording
     */
    fun startRecording(): File {
        if (_state.value == RecorderState.RECORDING) {
            throw IllegalStateException("Already recording")
        }

        val file = File(context.cacheDir, "voice_${System.currentTimeMillis()}.m4a")
        outputFile = file

        try {
            @Suppress("DEPRECATION")
            recorder = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                MediaRecorder()
            }).apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(SAMPLE_RATE)
                setAudioEncodingBitRate(BIT_RATE)
                setOutputFile(file.absolutePath)
                setMaxDuration(MAX_DURATION_MS)
                setOnInfoListener { _, what, _ ->
                    if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
                        Log.d(TAG, "Max recording duration reached")
                        stopRecording()
                    }
                }
                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "MediaRecorder error: what=$what extra=$extra")
                    cleanup()
                }
                prepare()
                start()
            }

            _state.value = RecorderState.RECORDING
            startAmplitudePolling()

            Log.d(TAG, "Recording started: ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording", e)
            cleanup()
            throw e
        }

        return file
    }

    /**
     * Stop recording and return the recorded file.
     *
     * @return The recorded .m4a file, or null if not recording
     */
    fun stopRecording(): File? {
        if (_state.value != RecorderState.RECORDING) {
            return null
        }

        amplitudeJob?.cancel()

        try {
            recorder?.apply {
                stop()
                release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recorder", e)
        }

        recorder = null
        _state.value = RecorderState.IDLE
        _amplitude.value = 0
        _durationMs.value = 0

        val file = outputFile

        // BUG FIX: MediaRecorder.stop() returns before the OS finishes writing
        // the MPEG-4 container trailer (moov atom). Wait briefly and verify.
        if (file != null && file.exists()) {
            // Give the filesystem time to flush the container trailer
            Thread.sleep(200)

            val size = file.length()
            if (size <= 0) {
                Log.e(TAG, "Recording file is empty after stop! Discarding.")
                file.delete()
                outputFile = null
                return null
            }
            Log.d(TAG, "Recording stopped: ${file.absolutePath} ($size bytes)")
        } else {
            Log.e(TAG, "Recording file not found after stop")
            return null
        }

        return file
    }

    /**
     * Cancel recording and delete the temporary file.
     */
    fun cancelRecording() {
        stopRecording()
        outputFile?.let {
            if (it.exists()) {
                it.delete()
                Log.d(TAG, "Cancelled recording, deleted temp file")
            }
        }
        outputFile = null
    }

    /**
     * Poll MediaRecorder.maxAmplitude every 100ms for waveform visualization.
     */
    private fun startAmplitudePolling() {
        val startTime = System.currentTimeMillis()
        amplitudeJob = scope.launch {
            while (isActive && _state.value == RecorderState.RECORDING) {
                try {
                    _amplitude.value = recorder?.maxAmplitude ?: 0
                    _durationMs.value = System.currentTimeMillis() - startTime
                } catch (e: Exception) {
                    // Recorder may have been released
                    break
                }
                delay(AMPLITUDE_POLL_MS)
            }
        }
    }

    /**
     * Clean up resources after an error.
     */
    private fun cleanup() {
        amplitudeJob?.cancel()
        try {
            recorder?.release()
        } catch (_: Exception) {}
        recorder = null
        _state.value = RecorderState.IDLE
        _amplitude.value = 0
        _durationMs.value = 0
    }

    /**
     * Release all resources. Call when done with this manager.
     */
    fun release() {
        cancelRecording()
        scope.cancel()
    }
}

/**
 * Voice recorder lifecycle states.
 */
enum class RecorderState {
    /** Not recording */
    IDLE,
    /** Actively recording audio */
    RECORDING
}
