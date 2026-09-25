package com.example.equal_plus.telephony

import android.util.Log
import com.example.equal_plus.data.network.ElevenLabsConfig
import com.example.equal_plus.data.network.ElevenLabsService
import com.example.equal_plus.data.network.ElevenLabsTtsRequest
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.util.concurrent.TimeUnit

sealed interface ElevenLabsSessionState {
    data object Idle : ElevenLabsSessionState
    data object Connecting : ElevenLabsSessionState
    data object Active : ElevenLabsSessionState
    data class Speaking(val text: String) : ElevenLabsSessionState
    data object Concluded : ElevenLabsSessionState
    data class Error(val message: String, val throwable: Throwable? = null) : ElevenLabsSessionState
}

data class ElevenLabsConversationEvent(
    val type: String,
    val text: String? = null,
    val audioBytes: ByteArray? = null
)

class ElevenLabsConversationManager(
    private val apiKey: String = ElevenLabsConfig.API_KEY,
    private val elevenLabsService: ElevenLabsService = ElevenLabsService.create(apiKey),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) {

    companion object {
        private const val TAG = "ElevenLabsConvManager"
    }

    private val _sessionState = MutableStateFlow<ElevenLabsSessionState>(ElevenLabsSessionState.Idle)
    val sessionState: StateFlow<ElevenLabsSessionState> = _sessionState.asStateFlow()

    private val _conversationEvents = MutableSharedFlow<ElevenLabsConversationEvent>(extraBufferCapacity = 64)
    val conversationEvents: SharedFlow<ElevenLabsConversationEvent> = _conversationEvents.asSharedFlow()

    private var activeWebSocket: WebSocket? = null
    private val gson = Gson()

    /**
     * Synthesize speech for AI screening assistant turns and stream back audio bytes.
     */
    suspend fun synthesizeSpeech(
        text: String,
        voiceId: String = ElevenLabsConfig.DEFAULT_VOICE_ID,
        onAudioChunk: (ByteArray) -> Unit = {}
    ): Result<ByteArray> = withContext(Dispatchers.IO) {
        try {
            _sessionState.value = ElevenLabsSessionState.Speaking(text)
            val request = ElevenLabsTtsRequest(text = text)
            val response = elevenLabsService.textToSpeechStream(
                voiceId = voiceId,
                apiKey = apiKey,
                request = request
            )

            if (response.isSuccessful && response.body() != null) {
                val bytes = response.body()!!.bytes()
                onAudioChunk(bytes)
                _conversationEvents.emit(
                    ElevenLabsConversationEvent(
                        type = "speech_synthesized",
                        text = text,
                        audioBytes = bytes
                    )
                )
                _sessionState.value = ElevenLabsSessionState.Active
                Result.success(bytes)
            } else {
                val errorMsg = "ElevenLabs TTS failed: ${response.code()} ${response.message()}"
                Log.e(TAG, errorMsg)
                _sessionState.value = ElevenLabsSessionState.Error(errorMsg)
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error synthesizing speech with ElevenLabs", e)
            _sessionState.value = ElevenLabsSessionState.Error(e.localizedMessage ?: "TTS error", e)
            Result.failure(e)
        }
    }

    /**
     * Start a real-time Conversational AI session via WebSocket with ElevenLabs.
     */
    fun startConversationalSession(
        agentId: String,
        wsUrl: String = "${ElevenLabsConfig.CONVAI_WS_BASE_URL}?agent_id=$agentId"
    ) {
        closeSession()
        _sessionState.value = ElevenLabsSessionState.Connecting

        val okHttpClient = OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .pingInterval(15, TimeUnit.SECONDS)
            .build()

        val request = Request.Builder()
            .url(wsUrl)
            .addHeader(ElevenLabsConfig.HEADER_API_KEY, apiKey)
            .build()

        activeWebSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "ElevenLabs Conversational AI WebSocket connected.")
                _sessionState.value = ElevenLabsSessionState.Active
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                scope.launch {
                    _conversationEvents.emit(
                        ElevenLabsConversationEvent(
                            type = "message",
                            text = text
                        )
                    )
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                val audioData = bytes.toByteArray()
                scope.launch {
                    _conversationEvents.emit(
                        ElevenLabsConversationEvent(
                            type = "audio_chunk",
                            audioBytes = audioData
                        )
                    )
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                _sessionState.value = ElevenLabsSessionState.Concluded
                webSocket.close(1000, null)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "ElevenLabs WebSocket failure: ${t.localizedMessage}", t)
                _sessionState.value = ElevenLabsSessionState.Error(
                    t.localizedMessage ?: "ElevenLabs connection failure",
                    t
                )
            }
        })
    }

    /**
     * Send user or caller audio to the ElevenLabs Conversational agent.
     */
    fun sendAudio(data: ByteArray) {
        activeWebSocket?.send(ByteString.of(*data))
    }

    /**
     * Send a text message turn to the ElevenLabs Conversational agent.
     */
    fun sendTextMessage(text: String) {
        val payload = mapOf(
            "type" to "user_message",
            "text" to text
        )
        activeWebSocket?.send(gson.toJson(payload))
    }

    /**
     * Terminate the active session.
     */
    fun closeSession() {
        activeWebSocket?.close(1000, "Session closed")
        activeWebSocket = null
        _sessionState.value = ElevenLabsSessionState.Idle
    }
}
