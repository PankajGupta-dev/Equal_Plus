package com.example.equal_plus.ui.livecall

import com.example.equal_plus.data.model.RiskLevel
import com.example.equal_plus.telephony.VoipConnectionState

enum class LiveCallStatus {
    SCREENING,
    CONNECTED,
    ENDED
}

data class LiveCallState(
    val callId: String = "live_call_8821",
    val callerName: String = "IRS Enforcement Bureau",
    val phoneNumber: String = "+1 (800) 829-1040",
    val durationFormatted: String = "00:42",
    val detectedPurpose: String = "Fraudulent tax penalty demand & gift card scam attempt",
    val riskLevel: RiskLevel = RiskLevel.HIGH,
    val aiStatusText: String = "AI Assistant challenging caller authority and requesting badge identification...",
    val latestTranscript: String = "Caller: 'You have unpaid federal back taxes. Immediate payment is required to avoid arrest.'\nAI: 'Please provide your IRS employee badge identification and case file number for verification.'",
    val category: String = "Financial Scam / Impersonation",
    val status: LiveCallStatus = LiveCallStatus.SCREENING,
    val connectionState: VoipConnectionState = VoipConnectionState.Streaming
)
