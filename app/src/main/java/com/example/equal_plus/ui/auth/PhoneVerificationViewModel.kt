package com.example.equal_plus.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.equal_plus.data.auth.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PhoneVerificationUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val codeSent: Boolean = false
)

class PhoneVerificationViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PhoneVerificationUiState())
    val uiState: StateFlow<PhoneVerificationUiState> = _uiState.asStateFlow()

    fun sendCode(phoneNumber: String) {
        if (phoneNumber.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "Please enter a valid phone number")
            return
        }
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            val result = authRepository.startPasswordlessSms(phoneNumber)
            _uiState.value = result.fold(
                onSuccess = { _uiState.value.copy(isLoading = false, codeSent = true) },
                onFailure = { _uiState.value.copy(isLoading = false, error = it.message ?: "Failed to send code") }
            )
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
