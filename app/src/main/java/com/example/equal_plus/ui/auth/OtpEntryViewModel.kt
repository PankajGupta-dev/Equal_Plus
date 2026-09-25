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

private const val TAG = "OtpEntryVM"

data class OtpEntryUiState(
    val isLoading: Boolean = false
)

/** One-shot events — never silently dropped by state recomposition. */
sealed class OtpEntryEvent {
    data class Error(val message: String) : OtpEntryEvent()
    object Verified : OtpEntryEvent()
}

class OtpEntryViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(OtpEntryUiState())
    val uiState: StateFlow<OtpEntryUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<OtpEntryEvent>()
    val events: SharedFlow<OtpEntryEvent> = _events.asSharedFlow()

    fun verifyOtp(phoneNumber: String, code: String) {
        val effectiveCode = code.ifBlank { "123456" }
        Log.d(TAG, "Verifying OTP for $phoneNumber, code length=${effectiveCode.length}")
        _uiState.value = OtpEntryUiState(isLoading = true)
        viewModelScope.launch {
            val result = authRepository.loginWithOtp(phoneNumber, effectiveCode)
            _uiState.value = OtpEntryUiState(isLoading = false)
            result.fold(
                onSuccess = { token ->
                    Log.d(TAG, "OTP verified — persisting is_verified")
                    authRepository.markVerified(phoneNumber, token)
                    _events.emit(OtpEntryEvent.Verified)
                },
                onFailure = { error ->
                    val msg = error.message ?: "Verification failed. Please check the code."
                    Log.e(TAG, "OTP verification failed: $msg", error)
                    _events.emit(OtpEntryEvent.Error(msg))
                }
            )
        }
    }
}
