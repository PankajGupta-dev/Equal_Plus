package com.example.equal_plus.ui.livecall

import com.example.equal_plus.data.model.RiskLevel
import com.example.equal_plus.telephony.VoipConnectionState

enum class LiveCallStatus {
    SCREENING,
    CONNECTED,
    ENDED
}

data class LiveCallState(
    val callId: String = "",
    val callerName: String = "No Active Call",
    val phoneNumber: String = "",
    val durationFormatted: String = "00:00",
    val detectedPurpose: String = "Standing by for incoming calls...",
    val riskLevel: RiskLevel = RiskLevel.SAFE,
    val aiStatusText: String = "AI screening service standing by...",
    val latestTranscript: String = "",
    val category: String = "",
    val status: LiveCallStatus = LiveCallStatus.SCREENING,
    val connectionState: VoipConnectionState = VoipConnectionState.Disconnected
)
