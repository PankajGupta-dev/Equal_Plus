package com.example.equal_plus.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.equal_plus.data.local.entity.CallEntity
import com.example.equal_plus.data.model.CallStatus
import com.example.equal_plus.data.model.CallType
import com.example.equal_plus.data.model.RiskLevel
import com.example.equal_plus.data.repository.CallRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.util.UUID

data class HomeUiState(
    val isLoading: Boolean = true,
    val handledCount: Int = 0,
    val autoResolvedCount: Int = 0,
    val blockedCount: Int = 0,
    val needingAttentionCount: Int = 0,
    val recentCalls: List<CallEntity> = emptyList()
)

class HomeViewModel(
    private val callRepository: CallRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        observeCalls()
    }

    private fun observeCalls() {
        callRepository.getAllCalls()
            .onEach { calls ->
                val handled = calls.size
                val autoResolved = calls.count { it.status == CallStatus.COMPLETED || it.status == CallStatus.BLOCKED }
                val blocked = calls.count { it.status == CallStatus.BLOCKED || it.isSpam || it.riskLevel == RiskLevel.HIGH || it.riskLevel == RiskLevel.CRITICAL }
                val needingAttention = calls.count { it.riskLevel == RiskLevel.MEDIUM || (it.isSpam && it.status != CallStatus.BLOCKED) }

                _uiState.value = HomeUiState(
                    isLoading = false,
                    handledCount = handled,
                    autoResolvedCount = autoResolved,
                    blockedCount = blocked,
                    needingAttentionCount = needingAttention,
                    recentCalls = calls
                )
            }
            .launchIn(viewModelScope)
    }

    fun seedDemoData() {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val sampleCalls = listOf(
                CallEntity(
                    id = "demo_call_1",
                    phoneNumber = "+1 (800) 242-7338",
                    contactName = "Chase Fraud Prevention",
                    callType = CallType.INCOMING,
                    status = CallStatus.BLOCKED,
                    riskLevel = RiskLevel.HIGH,
                    riskScore = 0.92f,
                    category = "Financial Scam",
                    summary = "Caller impersonated Chase fraud department asking for one-time passcode. AI detected credential harvesting pattern and automatically terminated call.",
                    transcription = "Caller: 'We detected suspicious activity on your card. Please verify the code sent to your mobile.'\nAI: 'This number is not authorized by Chase Bank. Terminating call.'",
                    isSpam = true,
                    createdAt = now - 15 * 60 * 1000,
                    durationSeconds = 48
                ),
                CallEntity(
                    id = "demo_call_2",
                    phoneNumber = "+1 (555) 782-9901",
                    contactName = "FedEx Driver Mike",
                    callType = CallType.INCOMING,
                    status = CallStatus.COMPLETED,
                    riskLevel = RiskLevel.SAFE,
                    riskScore = 0.05f,
                    category = "Delivery",
                    summary = "Driver asked where to place package behind gate. AI instructed driver to leave package by the side porch under the cover.",
                    transcription = "Caller: 'Hi, I have a package requiring signature or safe drop.'\nAI: 'Please place the parcel behind the side gate near the porch.'",
                    isSpam = false,
                    createdAt = now - 2 * 3600 * 1000,
                    durationSeconds = 62
                ),
                CallEntity(
                    id = "demo_call_3",
                    phoneNumber = "+1 (917) 555-0143",
                    contactName = "Solar Energy Promotions",
                    callType = CallType.INCOMING,
                    status = CallStatus.COMPLETED,
                    riskLevel = RiskLevel.MEDIUM,
                    riskScore = 0.65f,
                    category = "Telemarketing",
                    summary = "Telemarketer pitching residential solar rebate program. AI politely declined and requested number removal.",
                    transcription = "Caller: 'Are you the homeowner interested in $0 solar panels?'\nAI: 'The homeowner is not interested in solar proposals. Please add this number to your do-not-call list.'",
                    isSpam = true,
                    createdAt = now - 5 * 3600 * 1000,
                    durationSeconds = 35
                ),
                CallEntity(
                    id = "demo_call_4",
                    phoneNumber = "+1 (650) 555-8822",
                    contactName = "Stanford Health Care",
                    callType = CallType.INCOMING,
                    status = CallStatus.COMPLETED,
                    riskLevel = RiskLevel.SAFE,
                    riskScore = 0.01f,
                    category = "Appointment",
                    summary = "Automated clinic reminder confirming dental cleaning appointment for tomorrow at 2:30 PM.",
                    transcription = "Clinic: 'Reminder of appointment tomorrow with Dr. Smith at 2:30 PM.'\nAI: 'Confirmed. Added to user calendar.'",
                    isSpam = false,
                    createdAt = now - 24 * 3600 * 1000,
                    durationSeconds = 40
                )
            )
            callRepository.insertCalls(sampleCalls)
        }
    }

    class Factory(
        private val callRepository: CallRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
                return HomeViewModel(callRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
