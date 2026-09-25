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
        refreshCalls()
    }

    fun refreshCalls() {
        viewModelScope.launch {
            callRepository.syncCallsWithFallback()
        }
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
