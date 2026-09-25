package com.example.equal_plus.onboarding

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.equal_plus.data.local.AppDatabase
import com.example.equal_plus.data.local.PolicyDataStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class OnboardingViewModel @JvmOverloads constructor(
    application: Application,
    private val policyDataStore: PolicyDataStore = PolicyDataStore(application),
    private val contactSyncHelper: ContactSyncHelper = ContactSyncHelper(
        application,
        AppDatabase.getInstance(application).knownContactDao()
    )
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    init {
        checkInitialStatus()
    }

    private fun checkInitialStatus() {
        viewModelScope.launch {
            policyDataStore.isOnboardingComplete.collect { isComplete ->
                if (isComplete) {
                    _uiState.update { it.copy(isOnboardingFinished = true) }
                }
            }
        }
    }

    fun onContinueFromWelcome() {
        _uiState.update {
            it.copy(
                currentStep = OnboardingStep.PhoneVerification,
                isLoading = true
            )
        }
        detectSimPhoneNumber()
    }

    /**
     * Attempts to read own SIM phone number via TelephonyManager or SubscriptionManager.
     * Falls back to manual entry if unavailable.
     */
    fun detectSimPhoneNumber() {
        val context = getApplication<Application>()
        var detectedNumber: String? = null

        val hasPhoneState = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED

        val hasPhoneNumbers = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.READ_PHONE_NUMBERS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            hasPhoneState
        }

        if (hasPhoneState || hasPhoneNumbers) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val subManager = context.getSystemService(SubscriptionManager::class.java)
                    val defaultSubId = SubscriptionManager.getDefaultSubscriptionId()
                    if (defaultSubId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                        detectedNumber = subManager?.getPhoneNumber(defaultSubId)
                    }
                }

                if (detectedNumber.isNullOrBlank()) {
                    val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
                    @Suppress("DEPRECATION")
                    detectedNumber = telephonyManager?.line1Number
                }
            } catch (e: SecurityException) {
                detectedNumber = null
            } catch (e: Exception) {
                detectedNumber = null
            }
        }

        if (!detectedNumber.isNullOrBlank() && detectedNumber.length >= 7) {
            val formatted = contactSyncHelper.normalizeToE164(detectedNumber, "US")
            _uiState.update {
                it.copy(
                    phoneVerificationStage = PhoneVerificationStage.SimDetected(formatted),
                    inputPhoneNumber = formatted,
                    isLoading = false
                )
            }
        } else {
            _uiState.update {
                it.copy(
                    phoneVerificationStage = PhoneVerificationStage.ManualEntry,
                    isLoading = false
                )
            }
        }
    }

    fun onPhoneNumberChanged(number: String) {
        _uiState.update {
            it.copy(
                inputPhoneNumber = number,
                phoneError = null
            )
        }
    }

    fun onOtpChanged(otp: String) {
        _uiState.update {
            it.copy(
                inputOtp = otp.filter { char -> char.isDigit() }.take(6),
                otpError = null
            )
        }
    }

    fun onUseManualPhoneNumber() {
        _uiState.update {
            it.copy(
                phoneVerificationStage = PhoneVerificationStage.ManualEntry,
                inputPhoneNumber = ""
            )
        }
    }

    fun requestOtpForNumber(rawNumber: String) {
        val cleanNumber = rawNumber.trim()
        if (cleanNumber.length < 10) {
            _uiState.update { it.copy(phoneError = "Please enter a valid phone number (at least 10 digits)") }
            return
        }

        val normalized = contactSyncHelper.normalizeToE164(cleanNumber, "US")
        _uiState.update {
            it.copy(
                phoneVerificationStage = PhoneVerificationStage.OtpEntry(normalized),
                inputPhoneNumber = normalized,
                inputOtp = "",
                phoneError = null
            )
        }
    }

    fun confirmSimNumber(simNumber: String) {
        onPhoneVerified(simNumber)
    }

    fun verifyOtp(enteredOtp: String) {
        val currentStage = _uiState.value.phoneVerificationStage
        if (currentStage is PhoneVerificationStage.OtpEntry) {
            if (enteredOtp.length == 6) {
                onPhoneVerified(currentStage.phoneNumber)
            } else {
                _uiState.update { it.copy(otpError = "Please enter the complete 6-digit OTP code") }
            }
        }
    }

    private fun onPhoneVerified(verifiedNumber: String) {
        viewModelScope.launch {
            policyDataStore.setUserVerifiedNumber(verifiedNumber)
            _uiState.update {
                it.copy(
                    verifiedPhoneNumber = verifiedNumber,
                    phoneVerificationStage = PhoneVerificationStage.Verified(verifiedNumber),
                    currentStep = OnboardingStep.PermissionRequest(OnboardingPermission.SEQUENCE[0]),
                    currentPermissionIndex = 0
                )
            }
        }
    }

    fun onPermissionGranted(permission: OnboardingPermission) {
        val nextIndex = _uiState.value.currentPermissionIndex + 1

        if (permission == OnboardingPermission.READ_CONTACTS) {
            // Trigger contact sync
            _uiState.update { it.copy(currentStep = OnboardingStep.ContactSyncing, isLoading = true) }
            syncDeviceContacts()
            return
        }

        if (nextIndex < OnboardingPermission.SEQUENCE.size) {
            val nextPermission = OnboardingPermission.SEQUENCE[nextIndex]
            _uiState.update {
                it.copy(
                    currentStep = OnboardingStep.PermissionRequest(nextPermission),
                    currentPermissionIndex = nextIndex,
                    showRationaleDialog = false
                )
            }
        } else {
            _uiState.update {
                it.copy(
                    currentStep = OnboardingStep.RoleRegistration,
                    showRationaleDialog = false
                )
            }
        }
    }

    fun onPermissionDenied(permission: OnboardingPermission) {
        _uiState.update { it.copy(showRationaleDialog = true) }
    }

    fun dismissRationaleDialog() {
        _uiState.update { it.copy(showRationaleDialog = false) }
    }

    fun skipPermission() {
        val nextIndex = _uiState.value.currentPermissionIndex + 1
        if (nextIndex < OnboardingPermission.SEQUENCE.size) {
            val nextPermission = OnboardingPermission.SEQUENCE[nextIndex]
            _uiState.update {
                it.copy(
                    currentStep = OnboardingStep.PermissionRequest(nextPermission),
                    currentPermissionIndex = nextIndex,
                    showRationaleDialog = false
                )
            }
        } else {
            _uiState.update {
                it.copy(
                    currentStep = OnboardingStep.RoleRegistration,
                    showRationaleDialog = false
                )
            }
        }
    }

    private fun syncDeviceContacts() {
        viewModelScope.launch {
            val count = contactSyncHelper.syncContacts()
            _uiState.update {
                it.copy(
                    syncedContactsCount = count,
                    isLoading = false,
                    currentStep = OnboardingStep.RoleRegistration
                )
            }
        }
    }

    fun onRoleResult(isGranted: Boolean) {
        _uiState.update {
            it.copy(
                isRoleScreeningGranted = isGranted,
                currentStep = OnboardingStep.Complete
            )
        }
    }

    fun finishOnboarding() {
        viewModelScope.launch {
            policyDataStore.setOnboardingComplete(true)
            _uiState.update { it.copy(isOnboardingFinished = true) }
        }
    }
}

class OnboardingViewModelFactory(
    private val application: Application
) : androidx.lifecycle.ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(OnboardingViewModel::class.java)) {
            return OnboardingViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
