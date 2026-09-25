package com.bridgefy.app.call

import android.annotation.SuppressLint
import android.content.Context
import android.media.*
import android.util.Log
import kotlinx.coroutines.*
import java.io.InputStream
import java.io.OutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.Socket

class AudioEngine(private val context: Context) {
    companion object {
        private const val TAG = "AudioEngine"
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_IN = AudioFormat.CHANNEL_IN_MONO
        private const val CHANNEL_OUT = AudioFormat.CHANNEL_OUT_MONO
        private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
        private const val FRAME_SIZE = 640 // 20ms at 16kHz 16-bit Mono (16000 * 2 * 0.02)
        private const val JITTER_BUFFER_SIZE = FRAME_SIZE * 4 // 80ms
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null

    private var captureJob: Job? = null
    private var playbackJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Volatile
    var isMuted: Boolean = false

    @Volatile
    var isSpeakerOn: Boolean = false
        set(value) {
            field = value
            setSpeakerphone(value)
        }

    @SuppressLint("MissingPermission")
    fun start(socket: Socket) {
        stop()

        Log.d(TAG, "Starting AudioEngine")
        
        // 1. Initialize AudioRecord and AudioTrack
        val minRecordBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_IN, ENCODING)
        val recordBufSize = maxOf(minRecordBuf, FRAME_SIZE * 2)
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            SAMPLE_RATE,
            CHANNEL_IN,
            ENCODING,
            recordBufSize
        )

        val minTrackBuf = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_OUT, ENCODING)
        val trackBufSize = maxOf(minTrackBuf, JITTER_BUFFER_SIZE)
        
        // Android 6.0+ AudioTrack building
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        
        val audioFormat = AudioFormat.Builder()
            .setSampleRate(SAMPLE_RATE)
            .setChannelMask(CHANNEL_OUT)
            .setEncoding(ENCODING)
            .build()
            
        audioTrack = AudioTrack(
            audioAttributes,
            audioFormat,
            trackBufSize,
            AudioTrack.MODE_STREAM,
            AudioManager.AUDIO_SESSION_ID_GENERATE
        )

        // Set audio routing mode to communication
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        setSpeakerphone(isSpeakerOn)

        try {
            audioRecord?.startRecording()
            audioTrack?.play()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start AudioRecord or AudioTrack", e)
            return
        }

        // 2. Start Capture Loop (Mic -> Socket output)
        captureJob = scope.launch {
            try {
                val outputStream = DataOutputStream(socket.getOutputStream())
                val buffer = ByteArray(FRAME_SIZE)
                while (isActive) {
                    val record = audioRecord ?: break
                    val read = record.read(buffer, 0, FRAME_SIZE)
                    if (read > 0) {
                        if (isMuted) {
                            // Send silence bytes to maintain timing
                            buffer.fill(0)
                        }
                        outputStream.write(buffer, 0, read)
                        outputStream.flush()
                    } else if (read < 0) {
                        Log.e(TAG, "AudioRecord error: $read")
                        delay(20)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Audio capture loop exception: ${e.message}")
            }
        }

        // 3. Start Playback Loop (Socket input -> Speaker)
        playbackJob = scope.launch {
            try {
                val dataInputStream = DataInputStream(socket.getInputStream())
                val buffer = ByteArray(FRAME_SIZE)
                while (isActive) {
                    val track = audioTrack ?: break
                    dataInputStream.readFully(buffer)
                    track.write(buffer, 0, FRAME_SIZE)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Audio playback loop exception: ${e.message}")
            }
        }
    }

    fun stop() {
        Log.d(TAG, "Stopping AudioEngine")
        captureJob?.cancel()
        playbackJob?.cancel()
        captureJob = null
        playbackJob = null

        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing AudioRecord", e)
        }
        audioRecord = null

        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing AudioTrack", e)
        }
        audioTrack = null

        audioManager.mode = AudioManager.MODE_NORMAL
        audioManager.isSpeakerphoneOn = false
    }

    private fun setSpeakerphone(on: Boolean) {
        try {
            audioManager.isSpeakerphoneOn = on
            Log.d(TAG, "Speakerphone set to $on")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set speakerphone to $on", e)
        }
    }
}
