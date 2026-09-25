package com.example.equal_plus.service

import com.example.equal_plus.data.model.CallStatus
import com.example.equal_plus.data.model.PolicyCategory
import com.example.equal_plus.data.model.RiskLevel
import com.example.equal_plus.data.repository.CallRepository
import com.example.equal_plus.data.repository.PolicyRepository
import kotlinx.coroutines.flow.first

class PolicyEvaluator(
    private val policyRepository: PolicyRepository,
    private val callRepository: CallRepository
) {

    suspend fun evaluateIncomingCall(
        phoneNumber: String,
        contactName: String? = null
    ): ScreeningDecision {
        val isGlobalEnabled = policyRepository.isGlobalScreeningEnabled.first()
        if (!isGlobalEnabled) {
            return ScreeningDecision.ALLOW
        }

        // Known contact rule: if caller is a known personal contact, check personal policy
        val isPersonalContact = !contactName.isNullOrBlank()
        if (isPersonalContact) {
            val personalPolicy = policyRepository.getPolicyForCategory(PolicyCategory.PERSONAL).first()
            if (!personalPolicy.autoScreen) {
                return ScreeningDecision.ALLOW
            }
        }

        // Check history for existing spam or high risk rating
        val pastCalls = callRepository.getCallsForNumber(phoneNumber).first()
        val hasSpamHistory = pastCalls.any { it.isSpam || it.status == CallStatus.BLOCKED }
        val hasHighRiskHistory = pastCalls.any { it.riskLevel == RiskLevel.HIGH || it.riskLevel == RiskLevel.CRITICAL }

        val scamPolicy = policyRepository.getPolicyForCategory(PolicyCategory.SCAM).first()
        if ((hasSpamHistory || hasHighRiskHistory) && scamPolicy.autoBlock) {
            return ScreeningDecision.BLOCK
        }

        // Check Unknown caller policy
        val unknownPolicy = policyRepository.getPolicyForCategory(PolicyCategory.UNKNOWN_CALLER).first()
        if (!isPersonalContact) {
            if (unknownPolicy.autoBlock && hasSpamHistory) {
                return ScreeningDecision.BLOCK
            }
            if (unknownPolicy.autoScreen) {
                return ScreeningDecision.SILENCE
            }
        }

        return ScreeningDecision.ALLOW
    }
}
