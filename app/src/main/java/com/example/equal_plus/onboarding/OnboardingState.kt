package com.example.equal_plus.onboarding

import android.Manifest

/**
 * Represents each permission step in the sequential onboarding flow.
 */
enum class OnboardingPermission(
    val permissionString: String,
    val title: String,
    val description: String,
    val rationale: String
) {
    READ_PHONE_STATE(
        permissionString = Manifest.permission.READ_PHONE_STATE,
        title = "Identify Incoming Calls",
        description = "Allows EQUAL+ to detect when your phone rings and capture the caller's number for instant screening.",
        rationale = "Without this permission, EQUAL+ cannot intercept or screen incoming calls before they ring."
    ),
    READ_CALL_LOG(
        permissionString = Manifest.permission.READ_CALL_LOG,
        title = "Call History & Verification",
        description = "Enables EQUAL+ to verify caller legitimacy against recent call patterns and display screening logs.",
        rationale = "Access to call logs is essential to analyze call context and detect repeat or fraudulent callers."
    ),
    ANSWER_PHONE_CALLS(
        permissionString = Manifest.permission.ANSWER_PHONE_CALLS,
        title = "Call Control & AI Assistant",
        description = "Permits EQUAL+ to answer or divert unknown calls to your AI screening assistant without disturbing you.",
        rationale = "Needed so your AI agent can accept and screen unknown calls in the background seamlessly."
    ),
    READ_CONTACTS(
        permissionString = Manifest.permission.READ_CONTACTS,
        title = "Trusted Contacts Protection",
        description = "Syncs your saved contacts so calls from friends, family, and colleagues always ring through instantly without delay.",
        rationale = "Your contacts are stored securely in local database storage on your device and are never screened or delayed."
    );

    companion object {
        val SEQUENCE = listOf(
            READ_PHONE_STATE,
            READ_CALL_LOG,
            ANSWER_PHONE_CALLS,
            READ_CONTACTS
        )
    }
}

/**
 * Top-level onboarding flow steps.
 */
sealed interface OnboardingStep {
    object Welcome : OnboardingStep
    object PhoneVerification : OnboardingStep
    data class PermissionRequest(val permission: OnboardingPermission) : OnboardingStep
    object ContactSyncing : OnboardingStep
    object RoleRegistration : OnboardingStep
    object Complete : OnboardingStep
}

/**
 * Sub-state for Phone Verification.
 */
sealed interface PhoneVerificationStage {
    data class SimDetected(val phoneNumber: String) : PhoneVerificationStage
    object ManualEntry : PhoneVerificationStage
    data class OtpEntry(val phoneNumber: String, val generatedOtp: String = "123456") : PhoneVerificationStage
    data class Verified(val phoneNumber: String) : PhoneVerificationStage
}

/**
 * Overall UI State for Onboarding.
 */
data class OnboardingUiState(
    val currentStep: OnboardingStep = OnboardingStep.Welcome,
    val phoneVerificationStage: PhoneVerificationStage = PhoneVerificationStage.ManualEntry,
    val inputPhoneNumber: String = "",
    val inputOtp: String = "",
    val phoneError: String? = null,
    val otpError: String? = null,
    val verifiedPhoneNumber: String? = null,
    val currentPermissionIndex: Int = 0,
    val showRationaleDialog: Boolean = false,
    val syncedContactsCount: Int = 0,
    val isRoleScreeningGranted: Boolean = false,
    val isOnboardingFinished: Boolean = false,
    val isLoading: Boolean = false
)
