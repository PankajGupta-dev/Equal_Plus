package com.bridgefy.app.media

import android.media.MediaPlayer
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * Manages voice message playback using [MediaPlayer].
 *
 * Provides reactive state and progress flows for UI binding.
 * Only one voice message can play at a time — starting a new one stops the previous.
 *
 * Usage:
 * ```
 * val player = VoicePlayerManager()
 * player.play(messageId, "/path/to/audio.m4a")
 * // Observe player.state and player.progress in Compose
 * player.pause()
 * player.resume()
 * player.stop()
 * ```
 */
class VoicePlayerManager {

    companion object {
        private const val TAG = "VoicePlayer"
        private const val PROGRESS_UPDATE_MS = 100L
    }

    private var player: MediaPlayer? = null
    private var progressJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _state = MutableStateFlow(PlayerState.IDLE)
    /** Current playback state */
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private val _progress = MutableStateFlow(0f)
    /** Playback progress as a fraction (0.0 to 1.0) */
    val progress: StateFlow<Float> = _progress.asStateFlow()

    private val _currentMessageId = MutableStateFlow<String?>(null)
    /** ID of the message currently being played */
    val currentMessageId: StateFlow<String?> = _currentMessageId.asStateFlow()

    private val _durationMs = MutableStateFlow(0)
    /** Total duration of the current audio in milliseconds */
    val durationMs: StateFlow<Int> = _durationMs.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0)
    /** Current playback position in milliseconds */
    val currentPositionMs: StateFlow<Int> = _currentPositionMs.asStateFlow()

    /**
     * Play a voice message from a file path.
     * Stops any currently playing message first.
     *
     * @param messageId The MeshMessage ID (used to track which bubble is playing)
     * @param filePath Absolute path to the audio file
     */
    fun play(messageId: String, filePath: String) {
        // Stop current playback
        stop()

        _currentMessageId.value = messageId
        _state.value = PlayerState.LOADING

        try {
            player = MediaPlayer().apply {
                setDataSource(filePath)

                setOnPreparedListener { mp ->
                    _durationMs.value = mp.duration
                    mp.start()
                    _state.value = PlayerState.PLAYING
                    startProgressTracking()
                    Log.d(TAG, "Playing: $filePath (${mp.duration}ms)")
                }

                setOnCompletionListener {
                    Log.d(TAG, "Playback completed: $messageId")
                    resetState()
                }

                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "MediaPlayer error: what=$what extra=$extra")
                    _state.value = PlayerState.ERROR
                    true
                }

                prepareAsync()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play: $filePath", e)
            _state.value = PlayerState.ERROR
        }
    }

    /**
     * Pause the current playback.
     */
    fun pause() {
        if (_state.value != PlayerState.PLAYING) return

        try {
            player?.pause()
            progressJob?.cancel()
            _state.value = PlayerState.PAUSED
            Log.d(TAG, "Paused")
        } catch (e: Exception) {
            Log.e(TAG, "Error pausing", e)
        }
    }

    /**
     * Resume paused playback.
     */
    fun resume() {
        if (_state.value != PlayerState.PAUSED) return

        try {
            player?.start()
            _state.value = PlayerState.PLAYING
            startProgressTracking()
            Log.d(TAG, "Resumed")
        } catch (e: Exception) {
            Log.e(TAG, "Error resuming", e)
        }
    }

    /**
     * Stop playback and release resources.
     */
    fun stop() {
        progressJob?.cancel()

        try {
            player?.apply {
                if (isPlaying) stop()
                release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping player", e)
        }

        player = null
        resetState()
    }

    /**
     * Seek to a position as a fraction (0.0 to 1.0).
     */
    fun seekTo(fraction: Float) {
        val clamped = fraction.coerceIn(0f, 1f)
        player?.let {
            val position = (it.duration * clamped).toInt()
            it.seekTo(position)
            _progress.value = clamped
            _currentPositionMs.value = position
        }
    }

    /**
     * Check if a specific message is currently playing.
     */
    fun isPlayingMessage(messageId: String): Boolean {
        return _currentMessageId.value == messageId &&
                (_state.value == PlayerState.PLAYING || _state.value == PlayerState.PAUSED)
    }

    /**
     * Track playback progress every 100ms.
     */
    private fun startProgressTracking() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                try {
                    val p = player ?: break
                    if (!p.isPlaying && _state.value == PlayerState.PLAYING) break

                    val position = p.currentPosition
                    val duration = p.duration
                    _currentPositionMs.value = position
                    _progress.value = if (duration > 0) position.toFloat() / duration else 0f
                } catch (e: Exception) {
                    break
                }
                delay(PROGRESS_UPDATE_MS)
            }
        }
    }

    private fun resetState() {
        _state.value = PlayerState.IDLE
        _progress.value = 0f
        _currentPositionMs.value = 0
        _currentMessageId.value = null
    }

    /**
     * Release all resources. Call when done with this manager.
     */
    fun release() {
        stop()
        scope.cancel()
    }
}

/**
 * Voice player lifecycle states.
 */
enum class PlayerState {
    /** No audio loaded */
    IDLE,
    /** Audio is being prepared (async) */
    LOADING,
    /** Audio is playing */
    PLAYING,
    /** Audio is paused */
    PAUSED,
    /** An error occurred */
    ERROR
}
