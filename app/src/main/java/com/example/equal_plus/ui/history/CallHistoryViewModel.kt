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
}
