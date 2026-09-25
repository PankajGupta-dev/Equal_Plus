package com.example.equal_plus.ui.livecall

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.equal_plus.data.model.RiskLevel
import com.example.equal_plus.telephony.VoipConnectionState
import com.example.equal_plus.telephony.VoipGatewayClient
import com.example.equal_plus.telephony.VoipGatewayClientImpl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LiveAiCallViewModel(
    private val voipGatewayClient: VoipGatewayClient = VoipGatewayClientImpl(),
    private val elevenLabsManager: com.example.equal_plus.telephony.ElevenLabsConversationManager = com.example.equal_plus.telephony.ElevenLabsConversationManager()
) : ViewModel() {

    private val _liveCallState = MutableStateFlow(
        LiveCallState(
            connectionState = VoipConnectionState.Disconnected
        )
    )
    val liveCallState: StateFlow<LiveCallState> = _liveCallState.asStateFlow()

    val connectionState: StateFlow<VoipConnectionState> = voipGatewayClient.connectionState
    val elevenLabsSessionState: StateFlow<com.example.equal_plus.telephony.ElevenLabsSessionState> = elevenLabsManager.sessionState

    init {
        observeSessionManager()
        observeVoipClient()
    }

    private fun observeSessionManager() {
        viewModelScope.launch {
            com.example.equal_plus.telephony.LiveCallSessionManager.activeCallState.collect { sessionState ->
                if (sessionState.callId.isNotBlank() || sessionState.connectionState !is VoipConnectionState.Disconnected) {
                    _liveCallState.value = sessionState
                }
            }
        }
    }

    private fun observeVoipClient() {
        viewModelScope.launch {
            voipGatewayClient.connectionState.collect { connState ->
                val current = _liveCallState.value
                val newStatus = when (connState) {
                    is VoipConnectionState.CallEnded -> LiveCallStatus.ENDED
                    is VoipConnectionState.Connected, is VoipConnectionState.Streaming -> current.status
                    is VoipConnectionState.Disconnected, is VoipConnectionState.Connecting, is VoipConnectionState.Error -> current.status
                }
                val newAiStatus = when (connState) {
                    is VoipConnectionState.Connecting -> "Establishing secure VoIP audio stream to backend..."
                    is VoipConnectionState.Connected -> "Connected to AI Call Gateway. Starting screening..."
                    is VoipConnectionState.Streaming -> if (current.status == LiveCallStatus.CONNECTED) {
                        "Call in progress (User takeover active)"
                    } else {
                        "AI Assistant actively screening caller..."
                    }
                    is VoipConnectionState.CallEnded -> "Call session concluded. Auto-navigating to conversation summary..."
                    is VoipConnectionState.Disconnected -> "Audio channel disconnected."
                    is VoipConnectionState.Error -> "Gateway connection error: ${connState.message}"
                }
                _liveCallState.value = current.copy(
                    connectionState = connState,
                    status = newStatus,
                    aiStatusText = newAiStatus
                )
            }
        }

        viewModelScope.launch {
            voipGatewayClient.inboundMessages.collect { msg ->
                val current = _liveCallState.value
                val updatedTranscript = if (!msg.text.isNullOrBlank()) {
                    val speakerPrefix = if (msg.speaker != null) "${msg.speaker}: " else ""
                    if (current.latestTranscript.isBlank()) {
                        "$speakerPrefix${msg.text}"
                    } else {
                        "${current.latestTranscript}\n\n$speakerPrefix${msg.text}"
                    }
                } else {
                    current.latestTranscript
                }

                val updatedRisk = when (msg.riskLevel?.uppercase()) {
                    "SAFE" -> RiskLevel.SAFE
                    "LOW" -> RiskLevel.LOW
                    "MEDIUM" -> RiskLevel.MEDIUM
                    "HIGH" -> RiskLevel.HIGH
                    "CRITICAL" -> RiskLevel.CRITICAL
                    else -> current.riskLevel
                }

                val updatedPurpose = msg.detectedPurpose ?: current.detectedPurpose
                val updatedAiStatus = msg.statusText ?: current.aiStatusText

                val updatedStatus = if (msg.event.equals("call_ended", ignoreCase = true) ||
                    msg.event.equals("terminated", ignoreCase = true)
                ) {
                    LiveCallStatus.ENDED
                } else {
                    current.status
                }

                _liveCallState.value = current.copy(
                    latestTranscript = updatedTranscript,
                    riskLevel = updatedRisk,
                    detectedPurpose = updatedPurpose,
                    aiStatusText = updatedAiStatus,
                    status = updatedStatus
                )
            }
        }
    }

    fun startCallSession(
        callId: String,
        callerName: String? = null,
        phoneNumber: String? = null,
        wsUrl: String = "wss://api.equalplus.ai/v1/voip/stream"
    ) {
        _liveCallState.value = _liveCallState.value.copy(
            callId = callId,
            callerName = callerName ?: "Incoming Caller",
            phoneNumber = phoneNumber ?: "",
            status = LiveCallStatus.SCREENING,
            connectionState = VoipConnectionState.Connecting
        )
        voipGatewayClient.connect(wsUrl, callId)
    }

    fun endCall() {
        com.example.equal_plus.callscreening.AppContextProvider.applicationContext?.let { ctx ->
            com.example.equal_plus.telephony.LiveCallSessionManager.onCallEnded(ctx)
        }
        voipGatewayClient.endCall()
        _liveCallState.value = _liveCallState.value.copy(
            status = LiveCallStatus.ENDED,
            aiStatusText = "Call ended by user. Auto-navigating to conversation summary...",
            connectionState = VoipConnectionState.CallEnded
        )
    }

    fun takeOverCall() {
        voipGatewayClient.sendTakeoverSignal()
        _liveCallState.value = _liveCallState.value.copy(
            status = LiveCallStatus.CONNECTED,
            aiStatusText = "User took over call audio. Backend VoIP stream active.",
            connectionState = VoipConnectionState.Streaming
        )
    }

    fun triggerEndCallForNavigation() {
        endCall()
    }

    /**
     * Synthesize and speak an AI response using the configured ElevenLabs voice model,
     * transmitting synthesized audio into the live VoIP call stream.
     */
    fun speakAiResponse(text: String, voiceId: String = com.example.equal_plus.data.network.ElevenLabsConfig.DEFAULT_VOICE_ID) {
        viewModelScope.launch {
            _liveCallState.value = _liveCallState.value.copy(
                aiStatusText = "ElevenLabs AI speaking response..."
            )
            val result = elevenLabsManager.synthesizeSpeech(
                text = text,
                voiceId = voiceId
            ) { audioChunk ->
                // Send synthesized audio directly through the VoIP audio gateway
                voipGatewayClient.sendAudio(audioChunk)
            }
            if (result.isSuccess) {
                val current = _liveCallState.value
                val newTranscript = if (current.latestTranscript.isBlank()) {
                    "AI Assistant: $text"
                } else {
                    "${current.latestTranscript}\n\nAI Assistant: $text"
                }
                _liveCallState.value = current.copy(
                    latestTranscript = newTranscript,
                    aiStatusText = "AI Assistant actively screening caller..."
                )
            } else {
                _liveCallState.value = _liveCallState.value.copy(
                    aiStatusText = "AI screening caller (Voice fallback: ${result.exceptionOrNull()?.localizedMessage})"
                )
            }
        }
    }

    /**
     * Connects directly to ElevenLabs Conversational AI WebSocket agent for full-duplex conversational call screening.
     */
    fun startElevenLabsConversation(agentId: String) {
        elevenLabsManager.startConversationalSession(agentId)
        viewModelScope.launch {
            elevenLabsManager.conversationEvents.collect { event ->
                when (event.type) {
                    "message" -> {
                        event.text?.let { msg ->
                            val current = _liveCallState.value
                            _liveCallState.value = current.copy(
                                latestTranscript = if (current.latestTranscript.isBlank()) msg else "${current.latestTranscript}\n\n$msg"
                            )
                        }
                    }
                    "audio_chunk" -> {
                        event.audioBytes?.let { audio ->
                            voipGatewayClient.sendAudio(audio)
                        }
                    }
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        elevenLabsManager.closeSession()
        voipGatewayClient.disconnect()
    }
}
