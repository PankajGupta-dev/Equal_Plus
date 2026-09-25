package com.example.equal_plus.callscreening

import android.util.Log
import com.example.equal_plus.data.local.AppDatabase
import com.example.equal_plus.data.repository.ActionRepositoryImpl
import com.example.equal_plus.data.repository.CallRepositoryImpl
import com.example.equal_plus.domain.DecisionEngine
import com.example.equal_plus.service.NotificationHelper
import com.example.equal_plus.telephony.LiveCallSessionManager
import com.example.equal_plus.telephony.VoipGatewayClient
import com.example.equal_plus.telephony.VoipGatewayClientImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Production implementation of [AiCallHandoffTrigger].
 * Connects incoming screened calls to backend AI pipeline via [VoipGatewayClient].
 */
class AiCallHandoffTriggerImpl(
    private val voipClient: VoipGatewayClient = VoipGatewayClientImpl(),
    private val backendWsUrl: String = "ws://10.0.2.2:8000/ws/call",
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) : AiCallHandoffTrigger {

    companion object {
        private const val TAG = "AiCallHandoffTriggerImpl"
    }

    override suspend fun triggerHandoff(incomingNumber: String, callId: String) {
        Log.i(TAG, "Initiating AI call handoff for unknown caller: $incomingNumber (Call ID: $callId)")

        val context = AppContextProvider.applicationContext
        val decisionEngine = if (context != null) {
            val db = AppDatabase.getInstance(context)
            val callRepo = CallRepositoryImpl(db.callDao())
            val actionRepo = ActionRepositoryImpl(db.actionDao())
            val notificationHelper = NotificationHelper(context)
            DecisionEngine(
                actionRepository = actionRepo,
                callRepository = callRepo,
                notificationHelper = notificationHelper,
                context = context
            )
        } else null

        // 1. Establish WebSocket connection to VoIP AI Backend
        voipClient.connect(backendWsUrl, callId)

        // 2. Listen for inbound WebSocket messages (transcripts, decisions, status updates)
        scope.launch {
            voipClient.inboundMessages.collectLatest { message ->
                Log.d(TAG, "Received VoIP inbound message for $callId: event='${message.event}'")
                when {
                    message.event.equals("transcript", ignoreCase = true) || !message.text.isNullOrBlank() -> {
                        val speaker = message.speaker ?: "Caller"
                        val text = message.text ?: ""
                        if (text.isNotBlank()) {
                            LiveCallSessionManager.appendTranscript(speaker, text)
                        }
                    }

                    message.event.equals("decision", ignoreCase = true) -> {
                        message.rawJson?.let { json ->
                            decisionEngine?.executeDecision(callId, json)
                        }
                    }

                    message.event.equals("call_ended", ignoreCase = true) ||
                    message.event.equals("terminated", ignoreCase = true) -> {
                        Log.i(TAG, "VoIP Gateway reported call ended for $callId")
                        if (context != null) {
                            LiveCallSessionManager.onCallEnded(context)
                        }
                    }
                }
            }
        }
    }
}
