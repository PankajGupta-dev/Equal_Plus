package com.bridgefy.app.call

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.camera.core.Preview
import androidx.lifecycle.LifecycleOwner
import com.bridgefy.app.BridgeFyApplication
import com.bridgefy.app.db.DatabaseProvider
import com.bridgefy.app.db.CallLogQueries
import com.bridgefy.app.mesh.MeshManager
import com.bridgefy.app.model.*
import com.bridgefy.app.transport.wifidirect.WiFiDirectTransport
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.net.Socket
import java.util.UUID

class VoiceCallManager(
    private val context: Context,
    private val deviceId: String,
    private val meshManager: MeshManager,
    private val wifiDirectTransport: WiFiDirectTransport
) {
    companion object {
        private const val TAG = "VoiceCallManager"
        private const val CALL_TIMEOUT_MS = 25000L // 25 seconds
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val audioEngine = AudioEngine(context)
    private val videoEngine = VideoEngine(context)

    private val _callState = MutableStateFlow(CallState.IDLE)
    val callState: StateFlow<CallState> = _callState.asStateFlow()

    private val _peerId = MutableStateFlow<String?>(null)
    val peerId: StateFlow<String?> = _peerId.asStateFlow()

    private val _peerName = MutableStateFlow<String>("")
    val peerName: StateFlow<String> = _peerName.asStateFlow()

    private val _callDuration = MutableStateFlow(0L)
    val callDuration: StateFlow<Long> = _callDuration.asStateFlow()

    private val _isVideoCall = MutableStateFlow(false)
    val isVideoCall: StateFlow<Boolean> = _isVideoCall.asStateFlow()

    val isMuted = MutableStateFlow(false)
    val isSpeakerOn = MutableStateFlow(false)

    val isLocalVideoEnabled = MutableStateFlow(true)
    val isFrontCamera = MutableStateFlow(true)

    val remoteVideoFrame: StateFlow<Bitmap?> = videoEngine.remoteFrame

    private var activeSocket: Socket? = null
    private var activeVideoSocket: Socket? = null
    private var callId: String? = null
    private var isIncoming = false
    private var startTime: Long = 0L

    private var timerJob: Job? = null
    private var timeoutJob: Job? = null

    init {
        // Observe incoming call signaling messages from MeshManager
        scope.launch {
            meshManager.incomingCallSignals.collect { message ->
                handleIncomingCallSignal(message)
            }
        }
    }

    private suspend fun handleIncomingCallSignal(message: MeshMessage) {
        val signal = try {
            kotlinx.serialization.json.Json.decodeFromString<CallSignal>(message.payload)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse call signal payload", e)
            return
        }

        withContext(Dispatchers.Main) {
            when (message.type) {
                MessageType.CALL_REQUEST, MessageType.VIDEO_CALL_REQUEST -> {
                    if (_callState.value != CallState.IDLE) {
                        // Busy, reject automatically
                        scope.launch {
                            meshManager.sendCallSignal(
                                signal.callerDeviceId,
                                if (message.type == MessageType.VIDEO_CALL_REQUEST) MessageType.VIDEO_CALL_REJECT else MessageType.CALL_REJECT,
                                CallSignal(signal.callId, deviceId, getMyName(), isVideo = (message.type == MessageType.VIDEO_CALL_REQUEST))
                            )
                        }
                        return@withContext
                    }

                    // Incoming call request
                    isIncoming = true
                    callId = signal.callId
                    _peerId.value = signal.callerDeviceId
                    _peerName.value = signal.callerName
                    _isVideoCall.value = (message.type == MessageType.VIDEO_CALL_REQUEST || signal.isVideo)
                    _callState.value = CallState.RINGING
                    startTime = System.currentTimeMillis()

                    saveNewCallLog(
                        signal.callId,
                        signal.callerDeviceId,
                        deviceId,
                        CallLogStatus.MISSED,
                        if (_isVideoCall.value) CallType.VIDEO else CallType.VOICE
                    )

                    // Start Audio Server for callee to accept connection from caller
                    wifiDirectTransport.startAudioServer { socket ->
                        onAudioSocketConnected(socket)
                    }

                    // Start Video Server if it is a video call
                    if (_isVideoCall.value) {
                        wifiDirectTransport.startVideoServer { socket ->
                            onVideoSocketConnected(socket)
                        }
                    }

                    // Ringing timeout
                    startTimeoutTimer(CALL_TIMEOUT_MS) {
                        Log.d(TAG, "Ringing timeout — auto rejecting")
                        rejectCall()
                    }
                }

                MessageType.CALL_ACCEPT, MessageType.VIDEO_CALL_ACCEPT -> {
                    if (_callState.value == CallState.CALLING && callId == signal.callId) {
                        cancelTimeoutTimer()
                        _callState.value = CallState.CONNECTED
                        isIncoming = false
                        startTime = System.currentTimeMillis()

                        // As caller, connect to the callee's servers
                        scope.launch(Dispatchers.IO) {
                            val audioSocket = wifiDirectTransport.connectAudioSocket(signal.callerDeviceId)
                            if (audioSocket != null) {
                                onAudioSocketConnected(audioSocket)
                            } else {
                                Log.e(TAG, "Failed to connect audio socket to callee")
                                withContext(Dispatchers.Main) {
                                    endCall()
                                }
                                return@launch
                            }

                            if (_isVideoCall.value) {
                                val videoSocket = wifiDirectTransport.connectVideoSocket(signal.callerDeviceId)
                                if (videoSocket != null) {
                                    onVideoSocketConnected(videoSocket)
                                } else {
                                    Log.e(TAG, "Failed to connect video socket to callee")
                                    withContext(Dispatchers.Main) {
                                        endCall()
                                    }
                                }
                            }
                        }
                    }
                }

                MessageType.CALL_REJECT, MessageType.VIDEO_CALL_REJECT -> {
                    if ((_callState.value == CallState.CALLING || _callState.value == CallState.RINGING) && callId == signal.callId) {
                        cancelTimeoutTimer()
                        Log.d(TAG, "Call rejected by remote")
                        cleanupCall(CallLogStatus.REJECTED)
                    }
                }

                MessageType.CALL_END, MessageType.VIDEO_CALL_END -> {
                    if (_callState.value != CallState.IDLE && callId == signal.callId) {
                        cancelTimeoutTimer()
                        Log.d(TAG, "Call ended by remote")
                        cleanupCall(CallLogStatus.ENDED)
                    }
                }

                else -> {}
            }
        }
    }

    fun initiateCall(targetPeerId: String, targetPeerName: String) {
        if (_callState.value != CallState.IDLE) return

        val newCallId = UUID.randomUUID().toString()
        callId = newCallId
        isIncoming = false
        _isVideoCall.value = false
        _peerId.value = targetPeerId
        _peerName.value = targetPeerName
        _callState.value = CallState.CALLING
        startTime = System.currentTimeMillis()

        saveNewCallLog(newCallId, deviceId, targetPeerId, CallLogStatus.MISSED, CallType.VOICE)

        scope.launch {
            meshManager.sendCallSignal(
                targetPeerId,
                MessageType.CALL_REQUEST,
                CallSignal(newCallId, deviceId, getMyName(), isVideo = false)
            )
        }

        startTimeoutTimer(CALL_TIMEOUT_MS) {
            Log.d(TAG, "Calling timeout — no response")
            cleanupCall(CallLogStatus.MISSED)
        }
    }

    fun initiateVideoCall(targetPeerId: String, targetPeerName: String) {
        if (_callState.value != CallState.IDLE) return

        val newCallId = UUID.randomUUID().toString()
        callId = newCallId
        isIncoming = false
        _isVideoCall.value = true
        _peerId.value = targetPeerId
        _peerName.value = targetPeerName
        _callState.value = CallState.CALLING
        startTime = System.currentTimeMillis()

        saveNewCallLog(newCallId, deviceId, targetPeerId, CallLogStatus.MISSED, CallType.VIDEO)

        scope.launch {
            meshManager.sendCallSignal(
                targetPeerId,
                MessageType.VIDEO_CALL_REQUEST,
                CallSignal(newCallId, deviceId, getMyName(), isVideo = true)
            )
        }

        startTimeoutTimer(CALL_TIMEOUT_MS) {
            Log.d(TAG, "Calling timeout — no response")
            cleanupCall(CallLogStatus.MISSED)
        }
    }

    fun acceptCall() {
        if (_callState.value != CallState.RINGING) return
        cancelTimeoutTimer()

        val pId = _peerId.value ?: return
        val cId = callId ?: return
        val isVideo = _isVideoCall.value

        scope.launch {
            meshManager.sendCallSignal(
                pId,
                if (isVideo) MessageType.VIDEO_CALL_ACCEPT else MessageType.CALL_ACCEPT,
                CallSignal(cId, deviceId, getMyName(), isVideo = isVideo)
            )
        }

        _callState.value = CallState.CONNECTED
        startTime = System.currentTimeMillis()
    }

    fun rejectCall() {
        if (_callState.value != CallState.RINGING) return
        cancelTimeoutTimer()

        val pId = _peerId.value ?: return
        val cId = callId ?: return
        val isVideo = _isVideoCall.value

        scope.launch {
            meshManager.sendCallSignal(
                pId,
                if (isVideo) MessageType.VIDEO_CALL_REJECT else MessageType.CALL_REJECT,
                CallSignal(cId, deviceId, getMyName(), isVideo = isVideo)
            )
        }

        cleanupCall(CallLogStatus.REJECTED)
    }

    fun endCall() {
        if (_callState.value == CallState.IDLE) return
        cancelTimeoutTimer()

        val pId = _peerId.value
        val cId = callId
        val isVideo = _isVideoCall.value

        if (pId != null && cId != null) {
            scope.launch {
                meshManager.sendCallSignal(
                    pId,
                    if (isVideo) MessageType.VIDEO_CALL_END else MessageType.CALL_END,
                    CallSignal(cId, deviceId, getMyName(), isVideo = isVideo)
                )
            }
        }

        val finalStatus = if (_callState.value == CallState.CONNECTED) CallLogStatus.ENDED else CallLogStatus.MISSED
        cleanupCall(finalStatus)
    }

    fun toggleMute() {
        isMuted.value = !isMuted.value
        audioEngine.isMuted = isMuted.value
    }

    fun toggleSpeaker() {
        isSpeakerOn.value = !isSpeakerOn.value
        audioEngine.isSpeakerOn = isSpeakerOn.value
    }

    fun toggleLocalVideo() {
        isLocalVideoEnabled.value = !isLocalVideoEnabled.value
        videoEngine.isVideoEnabled = isLocalVideoEnabled.value
    }

    fun toggleCameraFacing() {
        isFrontCamera.value = !isFrontCamera.value
    }

    fun startCameraCapture(lifecycleOwner: LifecycleOwner, onPreviewReady: (Preview) -> Unit) {
        val socket = activeVideoSocket ?: return
        videoEngine.isVideoEnabled = isLocalVideoEnabled.value
        videoEngine.startCapture(
            lifecycleOwner = lifecycleOwner,
            socket = socket,
            isFrontCamera = isFrontCamera.value,
            onPreviewReady = onPreviewReady
        )
    }

    private fun onAudioSocketConnected(socket: Socket) {
        Log.d(TAG, "Audio socket connection established!")
        activeSocket = socket
        
        // Start recording & playback
        audioEngine.isMuted = isMuted.value
        audioEngine.isSpeakerOn = isSpeakerOn.value
        audioEngine.start(socket)

        scope.launch(Dispatchers.Main) {
            _callState.value = CallState.CONNECTED
            startCallTimer()
        }
    }

    private fun onVideoSocketConnected(socket: Socket) {
        Log.d(TAG, "Video socket connection established!")
        activeVideoSocket = socket
        
        // Start playback loop
        videoEngine.startPlayback(socket)
    }

    private fun startCallTimer() {
        timerJob?.cancel()
        _callDuration.value = 0L
        timerJob = scope.launch(Dispatchers.Main) {
            while (isActive && _callState.value == CallState.CONNECTED) {
                delay(1000)
                _callDuration.value += 1
            }
        }
    }

    private fun startTimeoutTimer(timeoutMs: Long, onTimeout: () -> Unit) {
        timeoutJob?.cancel()
        timeoutJob = scope.launch(Dispatchers.Main) {
            delay(timeoutMs)
            onTimeout()
        }
    }

    private fun cancelTimeoutTimer() {
        timeoutJob?.cancel()
        timeoutJob = null
    }

    private fun cleanupCall(status: CallLogStatus) {
        scope.launch(Dispatchers.Main) {
            _callState.value = CallState.ENDED
            timerJob?.cancel()
            timerJob = null

            // Stop engines
            audioEngine.stop()
            videoEngine.stop()

            // Close Sockets
            withContext(Dispatchers.IO) {
                try {
                    activeSocket?.close()
                } catch (_: Exception) {}
                activeSocket = null

                try {
                    activeVideoSocket?.close()
                } catch (_: Exception) {}
                activeVideoSocket = null
                
                // Stop servers
                wifiDirectTransport.stopAudioServer()
                wifiDirectTransport.stopVideoServer()
            }

            // Save database log
            val durationSecs = _callDuration.value.toInt()
            callId?.let { cId ->
                updateCallLogEnd(cId, durationSecs, status)
            }

            delay(2000) // Keep state as ENDED briefly so UI can show it
            
            // Reset state
            _callState.value = CallState.IDLE
            _peerId.value = null
            _peerName.value = ""
            _callDuration.value = 0L
            _isVideoCall.value = false
            isMuted.value = false
            isSpeakerOn.value = false
            isLocalVideoEnabled.value = true
            isFrontCamera.value = true
            callId = null
        }
    }

    private fun getMyName(): String {
        return BridgeFyApplication.instance.getDeviceName()
    }

    private fun saveNewCallLog(id: String, callerId: String, receiverId: String, status: CallLogStatus, type: CallType) {
        scope.launch(Dispatchers.IO) {
            try {
                val log = CallLog(
                    id = id,
                    callerId = callerId,
                    receiverId = receiverId,
                    startTime = System.currentTimeMillis(),
                    status = status,
                    callType = type
                )
                CallLogQueries.insertCallLog(DatabaseProvider.db, log)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save new call log", e)
            }
        }
    }

    private fun updateCallLogEnd(id: String, duration: Int, status: CallLogStatus) {
        scope.launch(Dispatchers.IO) {
            try {
                CallLogQueries.updateCallEnd(
                    DatabaseProvider.db,
                    id,
                    System.currentTimeMillis(),
                    duration,
                    status
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update call log end", e)
            }
        }
    }
}
