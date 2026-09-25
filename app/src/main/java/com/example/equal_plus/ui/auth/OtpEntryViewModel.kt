package com.example.equal_plus.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.equal_plus.data.auth.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class OtpEntryUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val isVerified: Boolean = false
)

class OtpEntryViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(OtpEntryUiState())
    val uiState: StateFlow<OtpEntryUiState> = _uiState.asStateFlow()

    fun verifyOtp(phoneNumber: String, code: String) {
        if (code.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "Please enter the verification code")
            return
        }
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            val result = authRepository.loginWithOtp(phoneNumber, code)
            if (result.isSuccess) {
                val token = result.getOrThrow()
                authRepository.markVerified(phoneNumber, token)
                _uiState.value = _uiState.value.copy(isLoading = false, isVerified = true)
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = result.exceptionOrNull()?.message ?: "Verification failed. Check your code."
                )
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
