package com.example.equal_plus.ui.auth

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.equal_plus.data.auth.AuthRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val TAG = "PhoneVerifyVM"

data class PhoneVerificationUiState(
    val isLoading: Boolean = false
)

/**
 * One-shot events emitted via SharedFlow so they are consumed exactly once
 * and never silently disappear due to state recomposition.
 */
sealed class PhoneVerificationEvent {
    data class Error(val message: String) : PhoneVerificationEvent()
    data class CodeSent(val phoneNumber: String) : PhoneVerificationEvent()
}

class PhoneVerificationViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PhoneVerificationUiState())
    val uiState: StateFlow<PhoneVerificationUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<PhoneVerificationEvent>()
    val events: SharedFlow<PhoneVerificationEvent> = _events.asSharedFlow()

    fun sendCode(phoneNumber: String) {
        if (phoneNumber.isBlank()) {
            viewModelScope.launch {
                _events.emit(PhoneVerificationEvent.Error("Please enter a valid phone number"))
            }
            return
        }
        Log.d(TAG, "Sending OTP to: $phoneNumber")
        _uiState.value = PhoneVerificationUiState(isLoading = true)
        viewModelScope.launch {
            val result = authRepository.startPasswordlessSms(phoneNumber)
            _uiState.value = PhoneVerificationUiState(isLoading = false)
            result.fold(
                onSuccess = {
                    Log.d(TAG, "OTP sent successfully to $phoneNumber")
                    _events.emit(PhoneVerificationEvent.CodeSent(phoneNumber))
                },
                onFailure = { error ->
                    val msg = error.message ?: "Failed to send verification code"
                    Log.e(TAG, "OTP send failed: $msg", error)
                    _events.emit(PhoneVerificationEvent.Error(msg))
                }
            )
        }
    }
}
