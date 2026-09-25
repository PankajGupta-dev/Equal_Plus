package com.example.equal_plus.ui.policy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.equal_plus.data.model.CategoryPolicy
import com.example.equal_plus.data.model.PolicyCategory
import com.example.equal_plus.data.repository.PolicyRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

data class AiPolicyUiState(
    val isLoading: Boolean = true,
    val isGlobalScreeningEnabled: Boolean = true,
    val policies: Map<PolicyCategory, CategoryPolicy> = emptyMap()
)

class AiPolicyViewModel(
    private val policyRepository: PolicyRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AiPolicyUiState())
    val uiState: StateFlow<AiPolicyUiState> = _uiState.asStateFlow()

    init {
        observePolicies()
    }

    private fun observePolicies() {
        combine(
            policyRepository.isGlobalScreeningEnabled,
            policyRepository.getAllPolicies()
        ) { globalEnabled, list ->
            val map = list.associateBy { it.category }
            AiPolicyUiState(
                isLoading = false,
                isGlobalScreeningEnabled = globalEnabled,
                policies = map
            )
        }.onEach {
            _uiState.value = it
        }.launchIn(viewModelScope)
    }

    fun toggleGlobalScreening(enabled: Boolean) {
        viewModelScope.launch {
            policyRepository.setGlobalScreeningEnabled(enabled)
        }
    }

    fun updatePolicy(
        category: PolicyCategory,
        autoScreen: Boolean? = null,
        autoBlock: Boolean? = null,
        autoRecord: Boolean? = null
    ) {
        viewModelScope.launch {
            val current = _uiState.value.policies[category] ?: CategoryPolicy(category = category)
            val updated = current.copy(
                autoScreen = autoScreen ?: current.autoScreen,
                autoBlock = autoBlock ?: current.autoBlock,
                autoRecord = autoRecord ?: current.autoRecord
            )
            policyRepository.updatePolicy(updated)
        }
    }

    fun resetAllToDefaults() {
        viewModelScope.launch {
            policyRepository.resetAllPolicies()
        }
    }
}
