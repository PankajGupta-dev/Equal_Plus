package com.example.equal_plus.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.equal_plus.data.local.entity.CallEntity
import com.example.equal_plus.data.model.CallStatus
import com.example.equal_plus.data.model.CallType
import com.example.equal_plus.data.model.RiskLevel
import com.example.equal_plus.data.repository.CallRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

data class CallHistoryUiState(
    val isLoading: Boolean = true,
    val allCalls: List<CallEntity> = emptyList(),
    val filteredCalls: List<CallEntity> = emptyList(),
    val activeFilter: CallFilter = CallFilter.ALL
)

class CallHistoryViewModel(
    private val callRepository: CallRepository
) : ViewModel() {

    private val _activeFilter = MutableStateFlow(CallFilter.ALL)
    private val _uiState = MutableStateFlow(CallHistoryUiState())
    val uiState: StateFlow<CallHistoryUiState> = _uiState.asStateFlow()

    init {
        observeCallsAndFilter()
        refreshCalls()
    }

    fun refreshCalls() {
        viewModelScope.launch {
            callRepository.syncCallsWithFallback()
        }
    }

    private fun observeCallsAndFilter() {
        combine(callRepository.getAllCalls(), _activeFilter) { calls, filter ->
            val filtered = filterCalls(calls, filter)
            CallHistoryUiState(
                isLoading = false,
                allCalls = calls,
                filteredCalls = filtered,
                activeFilter = filter
            )
        }.onEach {
            _uiState.value = it
        }.launchIn(viewModelScope)
    }

    private fun filterCalls(calls: List<CallEntity>, filter: CallFilter): List<CallEntity> {
        return when (filter) {
            CallFilter.ALL -> calls
            CallFilter.RESOLVED -> calls.filter { it.status == CallStatus.COMPLETED }
            CallFilter.BLOCKED -> calls.filter { it.status == CallStatus.BLOCKED || it.isSpam }
            CallFilter.ESCALATED -> calls.filter {
                it.riskLevel == RiskLevel.HIGH || it.riskLevel == RiskLevel.CRITICAL || it.riskLevel == RiskLevel.MEDIUM
            }
            CallFilter.MISSED -> calls.filter {
                it.callType == CallType.MISSED || it.status == CallStatus.MISSED
            }
        }
    }

    fun setFilter(filter: CallFilter) {
        _activeFilter.value = filter
        _uiState.value = _uiState.value.copy(
            activeFilter = filter,
            filteredCalls = filterCalls(_uiState.value.allCalls, filter)
        )
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
                    summary = "Caller impersonated Chase fraud department asking for one-time passcode. Automatically blocked.",
                    transcription = "Caller: 'We detected suspicious activity on your card.'\nAI: 'This number is unauthorized. Terminating call.'",
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
                    summary = "Driver asked where to place package. AI instructed driver to leave package by the side porch.",
                    transcription = "Caller: 'Where should I leave the package?'\nAI: 'Please place it on the side porch.'",
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
                    summary = "Telemarketer pitching solar rebate program. AI politely declined and requested number removal.",
                    transcription = "Caller: 'Are you interested in $0 solar panels?'\nAI: 'Not interested. Please remove from call list.'",
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
                    summary = "Automated clinic reminder confirming dental cleaning appointment for tomorrow.",
                    transcription = "Clinic: 'Appointment tomorrow at 2:30 PM.'\nAI: 'Confirmed.'",
                    isSpam = false,
                    createdAt = now - 24 * 3600 * 1000,
                    durationSeconds = 40
                ),
                CallEntity(
                    id = "demo_call_5",
                    phoneNumber = "+1 (415) 555-0012",
                    contactName = null,
                    callType = CallType.MISSED,
                    status = CallStatus.MISSED,
                    riskLevel = RiskLevel.UNKNOWN,
                    riskScore = 0.10f,
                    category = "Unknown",
                    summary = "Missed incoming call from unlisted number. Caller hung up before AI screening connected.",
                    transcription = null,
                    isSpam = false,
                    createdAt = now - 30 * 3600 * 1000,
                    durationSeconds = 0
                )
            )
            callRepository.insertCalls(sampleCalls)
        }
    }
}
