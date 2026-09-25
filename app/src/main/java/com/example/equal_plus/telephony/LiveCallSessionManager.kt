package com.example.equal_plus.telephony

import android.content.Context
import android.util.Log
import com.example.equal_plus.data.local.AppDatabase
import com.example.equal_plus.data.local.entity.CallEntity
import com.example.equal_plus.data.model.CallStatus
import com.example.equal_plus.data.model.CallType
import com.example.equal_plus.data.model.RiskLevel
import com.example.equal_plus.ui.livecall.LiveCallState
import com.example.equal_plus.ui.livecall.LiveCallStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Central singleton managing the active incoming call session, in-call audio capture,
 * Speech-to-Text transcription, AI evaluation, and forwarding to the human user.
 */
object LiveCallSessionManager {
    private const val TAG = "LiveCallSessionMgr"

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _activeCallState = MutableStateFlow(
        LiveCallState(
            connectionState = VoipConnectionState.Disconnected
        )
    )
    val activeCallState: StateFlow<LiveCallState> = _activeCallState.asStateFlow()

    private var voicePlayer: AiCallVoicePlayer? = null
    private var audioCapturePipeline: InCallAudioCapturePipeline? = null
    private var sttPipeline: InCallSpeechToTextPipeline? = null

    private var durationJob: Job? = null
    private var currentCallStartTime: Long = 0L

    /**
     * Initializes voice player if needed.
     */
    fun init(context: Context) {
        if (voicePlayer == null) {
            voicePlayer = AiCallVoicePlayer(context.applicationContext)
        }
    }

    /**
     * Called immediately when a call enters the device (via CallScreeningService or PhoneStateReceiver).
     * Logs the call into Room DB so Home dashboard updates in real time, and broadcasts to Live Call UI.
     */
    fun onCallEntering(
        context: Context,
        phoneNumber: String,
        callerName: String?,
        isKnownCaller: Boolean,
        callId: String = UUID.randomUUID().toString()
    ) {
        init(context)
        val now = System.currentTimeMillis()
        currentCallStartTime = now

        val displayPhone = phoneNumber.ifBlank { "Unknown Caller" }
        val displayName = callerName ?: if (isKnownCaller) "Known Contact" else "Unknown Caller"
        val initialRisk = if (isKnownCaller) RiskLevel.SAFE else RiskLevel.MEDIUM

        Log.i(TAG, "New call entering: $displayPhone (Known: $isKnownCaller, ID: $callId)")

        // 1. Insert into Room DB so HomeFragment immediately reflects it in Recent Calls and count
        scope.launch {
            try {
                val db = AppDatabase.getInstance(context)
                val callEntity = CallEntity(
                    id = callId,
                    phoneNumber = displayPhone,
                    contactName = displayName,
                    callType = CallType.INCOMING,
                    status = if (isKnownCaller) CallStatus.RINGING else CallStatus.SCREENING,
                    riskLevel = initialRisk,
                    startTime = now,
                    createdAt = now,
                    updatedAt = now
                )
                db.callDao().insertCall(callEntity)
                Log.d(TAG, "Inserted incoming call record into Room DB: $callId")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to insert call into Room DB", e)
            }
        }

        // 2. Broadcast active call state to LiveAiCallFragment
        _activeCallState.update {
            LiveCallState(
                callId = callId,
                callerName = displayName,
                phoneNumber = displayPhone,
                durationFormatted = "00:00",
                detectedPurpose = if (isKnownCaller) "Known contact calling" else "Analyzing caller intent...",
                riskLevel = initialRisk,
                aiStatusText = if (isKnownCaller) "Whitelisted caller ringing directly." else "EQUAL+ AI intercepting incoming call...",
                latestTranscript = "",
                status = LiveCallStatus.SCREENING,
                connectionState = VoipConnectionState.Connected
            )
        }
    }

    /**
     * Automatically answers the call, isolates user mic from the caller, starts
     * audio capture & STT pipelines, and speaks greeting to caller.
     */
    fun answerCallAndBeginAiScreening(context: Context, callId: String) {
        scope.launch {
            Log.i(TAG, "Attempting to pick up call: $callId")
            val answered = CallAnswerHelper.answerRingingCallWithDelay(context, 500)

            if (answered) {
                Log.i(TAG, "Call successfully picked up! Initializing Audio Capture & STT pipelines.")
                val greeting = AiCallVoicePlayer.DEFAULT_SCREENING_GREETING

                // Update Room DB status
                try {
                    val db = AppDatabase.getInstance(context)
                    val existing = db.callDao().getCallByIdDirect(callId)
                    if (existing != null) {
                        db.callDao().updateCall(
                            existing.copy(
                                status = CallStatus.ACTIVE,
                                updatedAt = System.currentTimeMillis()
                            )
                        )
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error updating call status in Room", e)
                }

                // 1. Initialize and start Audio Capture (automatically mutes user mic)
                audioCapturePipeline = InCallAudioCapturePipeline(context)
                audioCapturePipeline?.startCapture()

                // 2. Initialize and start continuous Speech-to-Text pipeline
                sttPipeline = InCallSpeechToTextPipeline(
                    context = context,
                    onTranscript = { speaker, text, isFinal ->
                        appendTranscript(speaker, text)
                        updateLiveTranscriptInDb(context, callId, text)
                    },
                    onAiForwardDecision = { reason ->
                        forwardCallToUser(context, reason)
                    }
                )
                sttPipeline?.startListening()

                // 3. Update Live Call State
                _activeCallState.update { current ->
                    current.copy(
                        status = LiveCallStatus.CONNECTED,
                        connectionState = VoipConnectionState.Streaming,
                        aiStatusText = "AI actively screening caller (User mic isolated)...",
                        latestTranscript = "AI Assistant: $greeting"
                    )
                }

                // 4. Start duration timer
                startDurationTimer()

                // 5. Speak AI greeting to caller
                voicePlayer?.speak(greeting)
            } else {
                Log.w(TAG, "Failed to answer call via acceptRingingCall. Ringer may be finishing.")
            }
        }
    }

    /**
     * Forwards the active call to the human user by unmuting the microphone
     * and alerting the user that the AI has handed over the call.
     */
    fun forwardCallToUser(context: Context, reason: String = "User requested takeover") {
        Log.i(TAG, "Executing AI call forward to user. Reason: $reason")

        // Inform caller
        voicePlayer?.speak("Thank you. Connecting you now, please hold a moment.")

        // Unmute user's physical microphone
        audioCapturePipeline?.forwardCallToUser()

        _activeCallState.update { current ->
            current.copy(
                status = LiveCallStatus.CONNECTED,
                aiStatusText = "Call forwarded to user ($reason). You are now talking to caller."
            )
        }

        // Update database
        val callId = _activeCallState.value.callId
        if (callId.isNotBlank()) {
            scope.launch {
                try {
                    val db = AppDatabase.getInstance(context)
                    val existing = db.callDao().getCallByIdDirect(callId)
                    if (existing != null) {
                        db.callDao().updateCall(
                            existing.copy(
                                summary = "Forwarded to user: $reason",
                                updatedAt = System.currentTimeMillis()
                            )
                        )
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error updating call forwarding in DB", e)
                }
            }
        }
    }

    private fun startDurationTimer() {
        durationJob?.cancel()
        durationJob = scope.launch {
            var seconds = 0L
            while (true) {
                delay(1000)
                seconds++
                val mins = seconds / 60
                val secs = seconds % 60
                val formatted = String.format("%02d:%02d", mins, secs)
                _activeCallState.update { it.copy(durationFormatted = formatted) }
            }
        }
    }

    /**
     * Updates transcript as conversation unfolds.
     */
    fun appendTranscript(speaker: String, text: String) {
        _activeCallState.update { current ->
            val newEntry = "$speaker: $text"
            val combined = if (current.latestTranscript.isBlank()) newEntry else "${current.latestTranscript}\n\n$newEntry"
            current.copy(latestTranscript = combined)
        }
    }

    private fun updateLiveTranscriptInDb(context: Context, callId: String, text: String) {
        if (callId.isBlank()) return
        scope.launch {
            try {
                val db = AppDatabase.getInstance(context)
                val existing = db.callDao().getCallByIdDirect(callId)
                if (existing != null) {
                    val fullTranscript = _activeCallState.value.latestTranscript
                    db.callDao().updateCall(
                        existing.copy(
                            transcription = fullTranscript,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error saving live transcript chunk to DB: ${e.message}")
            }
        }
    }

    /**
     * Updates AI status text displayed on screen.
     */
    fun updateAiStatus(status: String) {
        _activeCallState.update { it.copy(aiStatusText = status) }
    }

    /**
     * Called when the call ends / is disconnected.
     */
    fun onCallEnded(context: Context) {
        durationJob?.cancel()
        durationJob = null

        // Stop STT and Audio Capture pipelines
        sttPipeline?.stopListening()
        sttPipeline = null

        audioCapturePipeline?.stopCapture()
        audioCapturePipeline = null

        voicePlayer?.stop()

        val current = _activeCallState.value
        val callId = current.callId
        val now = System.currentTimeMillis()
        val duration = if (currentCallStartTime > 0) (now - currentCallStartTime) / 1000 else 0L

        Log.i(TAG, "Call ended for ID: $callId. Total duration: ${duration}s")

        if (callId.isNotBlank()) {
            scope.launch {
                try {
                    val db = AppDatabase.getInstance(context)
                    val existing = db.callDao().getCallByIdDirect(callId)
                    if (existing != null) {
                        db.callDao().updateCall(
                            existing.copy(
                                status = if (existing.riskLevel == RiskLevel.HIGH) CallStatus.BLOCKED else CallStatus.COMPLETED,
                                endTime = now,
                                durationSeconds = duration,
                                transcription = current.latestTranscript.ifBlank { "Screening session completed." },
                                updatedAt = now
                            )
                        )
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to finalize call in Room DB", e)
                }
            }
        }

        _activeCallState.update {
            it.copy(
                status = LiveCallStatus.ENDED,
                connectionState = VoipConnectionState.CallEnded,
                aiStatusText = "Call session completed. Summary recorded in call history."
            )
        }
    }

    fun resetToStandby() {
        durationJob?.cancel()
        durationJob = null
        _activeCallState.update {
            LiveCallState(
                connectionState = VoipConnectionState.Disconnected
            )
        }
    }
}
