package com.example.equal_plus.ui.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.equal_plus.data.local.entity.ActionEntity
import com.example.equal_plus.data.local.entity.CallEntity
import com.example.equal_plus.data.local.entity.ConversationEntity
import com.example.equal_plus.data.model.ActionStatus
import com.example.equal_plus.data.model.ActionType
import com.example.equal_plus.data.model.CallStatus
import com.example.equal_plus.data.model.CallType
import com.example.equal_plus.data.model.RiskLevel
import com.example.equal_plus.data.model.SpeakerType
import com.example.equal_plus.data.repository.ActionRepository
import com.example.equal_plus.data.repository.CallRepository
import com.example.equal_plus.data.repository.ConversationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

data class ConversationDetailsUiState(
    val isLoading: Boolean = true,
    val call: CallEntity? = null,
    val conversations: List<ConversationEntity> = emptyList(),
    val actions: List<ActionEntity> = emptyList()
)

class ConversationDetailsViewModel(
    private val callId: String,
    private val callRepository: CallRepository,
    private val conversationRepository: ConversationRepository,
    private val actionRepository: ActionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ConversationDetailsUiState())
    val uiState: StateFlow<ConversationDetailsUiState> = _uiState.asStateFlow()

    init {
        ensureSampleDataForCallId()
        observeDetails()
    }

    private fun observeDetails() {
        combine(
            callRepository.getCallById(callId),
            conversationRepository.getConversationsForCall(callId),
            actionRepository.getActionsForCall(callId)
        ) { call, conversations, actions ->
            ConversationDetailsUiState(
                isLoading = false,
                call = call,
                conversations = conversations,
                actions = actions
            )
        }.onEach {
            _uiState.value = it
        }.launchIn(viewModelScope)
    }

    private fun ensureSampleDataForCallId() {
        viewModelScope.launch {
            val existingCall = callRepository.getCallByIdDirect(callId)
            val now = System.currentTimeMillis()

            if (existingCall == null) {
                val fallbackCall = CallEntity(
                    id = callId,
                    phoneNumber = "+1 (800) 242-7338",
                    contactName = "Chase Fraud Prevention Unit",
                    callType = CallType.INCOMING,
                    status = CallStatus.BLOCKED,
                    riskLevel = RiskLevel.HIGH,
                    riskScore = 0.94f,
                    category = "Financial Scam",
                    summary = "Caller impersonated Chase fraud department asking for card security codes. AI challenged credentials and automatically terminated & blocked number.",
                    transcription = "Caller requested one-time authorization code. AI screening denied access.",
                    isSpam = true,
                    createdAt = now - 10 * 60 * 1000,
                    durationSeconds = 48
                )
                callRepository.insertCall(fallbackCall)
            }

            val existingTurns = conversationRepository.getConversationsForCallDirect(callId)
            if (existingTurns.isEmpty()) {
                val sampleTurns = listOf(
                    ConversationEntity(
                        callId = callId,
                        speaker = SpeakerType.CALLER,
                        message = "Hello, this is officer Marcus from Chase Fraud Prevention. We have flagged a $1,450 wire transfer on your debit card.",
                        timestamp = now - 9 * 60 * 1000,
                        confidence = 0.95f,
                        sentiment = "Urgent / Coercive",
                        intent = "Create Urgency & Establish Authority",
                        riskLevel = RiskLevel.MEDIUM,
                        isFlagged = true
                    ),
                    ConversationEntity(
                        callId = callId,
                        speaker = SpeakerType.ASSISTANT,
                        message = "Hello. This is Equal Plus AI Call Assistant. To verify this security alert, please state your employee ID badge and department case reference number.",
                        timestamp = now - 8 * 60 * 1000,
                        confidence = 0.98f,
                        sentiment = "Neutral / Firm",
                        intent = "Credential Challenge",
                        riskLevel = RiskLevel.SAFE,
                        isFlagged = false
                    ),
                    ConversationEntity(
                        callId = callId,
                        speaker = SpeakerType.CALLER,
                        message = "We cannot provide that over public lines! I just sent a 6-digit text code to the device. Read that code to me right now to cancel the transaction!",
                        timestamp = now - 7 * 60 * 1000,
                        confidence = 0.97f,
                        sentiment = "Aggressive / Demanding",
                        intent = "OTP / Credential Harvesting",
                        riskLevel = RiskLevel.HIGH,
                        isFlagged = true
                    ),
                    ConversationEntity(
                        callId = callId,
                        speaker = SpeakerType.ASSISTANT,
                        message = "Warning: Legitimate financial institutions never request SMS passcodes over incoming calls. This call is classified as fraudulent. Terminating and blacklisting number.",
                        timestamp = now - 6 * 60 * 1000,
                        confidence = 0.99f,
                        sentiment = "Authoritative",
                        intent = "Call Termination",
                        riskLevel = RiskLevel.SAFE,
                        isFlagged = false
                    )
                )
                conversationRepository.insertConversations(sampleTurns)

                val sampleActions = listOf(
                    ActionEntity(
                        callId = callId,
                        actionType = ActionType.WARN_USER,
                        status = ActionStatus.EXECUTED,
                        timestamp = now - 8 * 60 * 1000,
                        description = "Displayed high-risk fraudulent caller banner on active notification.",
                        executedAt = now - 8 * 60 * 1000
                    ),
                    ActionEntity(
                        callId = callId,
                        actionType = ActionType.END_CALL,
                        status = ActionStatus.EXECUTED,
                        timestamp = now - 6 * 60 * 1000,
                        description = "Automated AI call termination triggered due to OTP harvesting violation.",
                        executedAt = now - 6 * 60 * 1000
                    ),
                    ActionEntity(
                        callId = callId,
                        actionType = ActionType.BLOCK_NUMBER,
                        status = ActionStatus.EXECUTED,
                        timestamp = now - 5 * 60 * 1000,
                        description = "Blacklisted caller phone number in global scam database.",
                        executedAt = now - 5 * 60 * 1000
                    )
                )
                actionRepository.insertActions(sampleActions)
            }
        }
    }

    class Factory(
        private val callId: String,
        private val callRepository: CallRepository,
        private val conversationRepository: ConversationRepository,
        private val actionRepository: ActionRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ConversationDetailsViewModel::class.java)) {
                return ConversationDetailsViewModel(
                    callId,
                    callRepository,
                    conversationRepository,
                    actionRepository
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
