package com.example.equal_plus.telephony

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
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.util.concurrent.TimeUnit

sealed interface VoipConnectionState {
    data object Disconnected : VoipConnectionState
    data object Connecting : VoipConnectionState
    data object Connected : VoipConnectionState
    data object Streaming : VoipConnectionState
    data object CallEnded : VoipConnectionState
    data class Error(val message: String, val throwable: Throwable? = null) : VoipConnectionState
}

data class VoipInboundMessage(
    val event: String = "",
    val speaker: String? = null,
    val text: String? = null,
    val statusText: String? = null,
    val riskLevel: String? = null,
    val riskScore: Float? = null,
    val detectedPurpose: String? = null,
    val rawJson: String? = null
)

interface VoipGatewayClient {
    val connectionState: StateFlow<VoipConnectionState>
    val inboundMessages: SharedFlow<VoipInboundMessage>
    fun connect(wsUrl: String, callId: String)
    fun sendAudio(data: ByteArray)
    fun sendText(messageJson: String)
    fun sendTakeoverSignal()
    fun endCall()
    fun disconnect()
}

class VoipGatewayClientImpl(
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(15, TimeUnit.SECONDS)
        .build(),
    private val gson: Gson = Gson(),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) : VoipGatewayClient {

    private val _connectionState = MutableStateFlow<VoipConnectionState>(VoipConnectionState.Disconnected)
    override val connectionState: StateFlow<VoipConnectionState> = _connectionState.asStateFlow()

    private val _inboundMessages = MutableSharedFlow<VoipInboundMessage>(extraBufferCapacity = 64)
    override val inboundMessages: SharedFlow<VoipInboundMessage> = _inboundMessages.asSharedFlow()

    private var activeWebSocket: WebSocket? = null
    private var currentCallId: String? = null

    override fun connect(wsUrl: String, callId: String) {
        disconnect()
        currentCallId = callId
        _connectionState.value = VoipConnectionState.Connecting

        val request = Request.Builder()
            .url(wsUrl)
            .addHeader("X-Call-ID", callId)
            .addHeader("X-Client-Version", "EqualPlus-1.0")
            .build()

        activeWebSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                _connectionState.value = VoipConnectionState.Connected
                // Send session initialization packet
                val initPayload = mapOf(
                    "action" to "init_session",
                    "call_id" to callId,
                    "timestamp" to System.currentTimeMillis()
                )
                webSocket.send(gson.toJson(initPayload))
                _connectionState.value = VoipConnectionState.Streaming
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val parsed = gson.fromJson(text, VoipInboundMessage::class.java)?.copy(rawJson = text)
                    if (parsed != null) {
                        scope.launch {
                            _inboundMessages.emit(parsed)
                            if (parsed.event.equals("call_ended", ignoreCase = true) ||
                                parsed.event.equals("terminated", ignoreCase = true)
                            ) {
                                _connectionState.value = VoipConnectionState.CallEnded
                            }
                        }
                    }
                } catch (e: Exception) {
                    val fallback = VoipInboundMessage(event = "raw", text = text, rawJson = text)
                    scope.launch { _inboundMessages.emit(fallback) }
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                val binaryEvent = VoipInboundMessage(
                    event = "audio_chunk",
                    statusText = "Received audio chunk (${bytes.size} bytes)"
                )
                scope.launch { _inboundMessages.emit(binaryEvent) }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                _connectionState.value = VoipConnectionState.CallEnded
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                _connectionState.value = VoipConnectionState.Disconnected
                activeWebSocket = null
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                _connectionState.value = VoipConnectionState.Error(
                    message = t.localizedMessage ?: "WebSocket connection failure",
                    throwable = t
                )
                activeWebSocket = null
            }
        })
    }

    override fun sendAudio(data: ByteArray) {
        activeWebSocket?.send(data.toByteString())
    }

    override fun sendText(messageJson: String) {
        activeWebSocket?.send(messageJson)
    }

    override fun sendTakeoverSignal() {
        val payload = mapOf(
            "action" to "takeover_call",
            "call_id" to (currentCallId ?: ""),
            "timestamp" to System.currentTimeMillis()
        )
        sendText(gson.toJson(payload))
    }

    override fun endCall() {
        val payload = mapOf(
            "action" to "end_call",
            "call_id" to (currentCallId ?: ""),
            "timestamp" to System.currentTimeMillis()
        )
        sendText(gson.toJson(payload))
        _connectionState.value = VoipConnectionState.CallEnded
        disconnect()
    }

    override fun disconnect() {
        activeWebSocket?.close(1000, "User disconnected session")
        activeWebSocket = null
        if (_connectionState.value !is VoipConnectionState.CallEnded) {
            _connectionState.value = VoipConnectionState.Disconnected
        }
    }
}
