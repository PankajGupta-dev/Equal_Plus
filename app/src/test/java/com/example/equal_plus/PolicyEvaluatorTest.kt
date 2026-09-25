package com.example.equal_plus

import com.example.equal_plus.data.local.entity.CallEntity
import com.example.equal_plus.data.model.CallStatus
import com.example.equal_plus.data.model.CategoryPolicy
import com.example.equal_plus.data.model.PolicyCategory
import com.example.equal_plus.data.model.RiskLevel
import com.example.equal_plus.data.repository.CallRepository
import com.example.equal_plus.data.repository.PolicyRepository
import com.example.equal_plus.service.PolicyEvaluator
import com.example.equal_plus.service.ScreeningDecision
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class PolicyEvaluatorTest {

    private class FakePolicyRepo : PolicyRepository {
        val globalEnabledFlow = MutableStateFlow(true)
        val policies = mutableMapOf(
            PolicyCategory.SCAM to CategoryPolicy(category = PolicyCategory.SCAM, autoBlock = true, autoScreen = true),
            PolicyCategory.UNKNOWN_CALLER to CategoryPolicy(category = PolicyCategory.UNKNOWN_CALLER, autoBlock = false, autoScreen = true),
            PolicyCategory.PERSONAL to CategoryPolicy(category = PolicyCategory.PERSONAL, autoBlock = false, autoScreen = false),
            PolicyCategory.TELEMARKETING to CategoryPolicy(category = PolicyCategory.TELEMARKETING, autoBlock = true, autoScreen = true)
        )

        override val isGlobalScreeningEnabled: Flow<Boolean> = globalEnabledFlow
        override fun getPolicyForCategory(category: PolicyCategory): Flow<CategoryPolicy> =
            MutableStateFlow(policies[category] ?: CategoryPolicy(category))
        override fun getAllPolicies(): Flow<List<CategoryPolicy>> = MutableStateFlow(policies.values.toList())
        override suspend fun updatePolicy(policy: CategoryPolicy) { policies[policy.category] = policy }
        override suspend fun setGlobalScreeningEnabled(enabled: Boolean) { globalEnabledFlow.value = enabled }
        override suspend fun resetCategoryPolicy(category: PolicyCategory) {}
        override suspend fun resetAllPolicies() {}
    }

    private class FakeCallRepo : CallRepository {
        val calls = mutableListOf<CallEntity>()
        override fun getAllCalls(): Flow<List<CallEntity>> = MutableStateFlow(calls)
        override fun getCallById(id: String): Flow<CallEntity?> = MutableStateFlow(null)
        override suspend fun getCallByIdDirect(id: String): CallEntity? = null
        override fun getCallsForNumber(phoneNumber: String): Flow<List<CallEntity>> =
            MutableStateFlow(calls.filter { it.phoneNumber == phoneNumber })
        override fun getCallsByStatus(status: CallStatus): Flow<List<CallEntity>> = MutableStateFlow(emptyList())
        override fun getCallsByRiskLevel(riskLevel: RiskLevel): Flow<List<CallEntity>> = MutableStateFlow(emptyList())
        override fun getSpamCalls(): Flow<List<CallEntity>> = MutableStateFlow(emptyList())
        override suspend fun insertCall(call: CallEntity): Long { calls.add(call); return 1L }
        override suspend fun insertCalls(calls: List<CallEntity>): List<Long> { this.calls.addAll(calls); return listOf(1L) }
        override suspend fun updateCall(call: CallEntity): Int = 1
        override suspend fun deleteCall(call: CallEntity): Int = 1
        override suspend fun deleteCallById(id: String): Int = 1
        override suspend fun clearAllCalls(): Int = 1
    }

    @Test
    fun testGlobalDisabledReturnsAllow() = runTest {
        val policyRepo = FakePolicyRepo()
        policyRepo.globalEnabledFlow.value = false
        val callRepo = FakeCallRepo()

        val evaluator = PolicyEvaluator(policyRepo, callRepo)
        val decision = evaluator.evaluateIncomingCall("+18005550000", null)
        assertEquals(ScreeningDecision.ALLOW, decision)
    }

    @Test
    fun testKnownPersonalContactReturnsAllow() = runTest {
        val policyRepo = FakePolicyRepo()
        val callRepo = FakeCallRepo()

        val evaluator = PolicyEvaluator(policyRepo, callRepo)
        val decision = evaluator.evaluateIncomingCall("+15551234567", "Mom")
        assertEquals(ScreeningDecision.ALLOW, decision)
    }

    @Test
    fun testKnownSpamHistoryReturnsBlock() = runTest {
        val policyRepo = FakePolicyRepo()
        val callRepo = FakeCallRepo()
        val spamNumber = "+18006660000"
        callRepo.calls.add(
            CallEntity(
                id = "call_spam",
                phoneNumber = spamNumber,
                status = CallStatus.BLOCKED,
                riskLevel = RiskLevel.HIGH,
                isSpam = true
            )
        )

        val evaluator = PolicyEvaluator(policyRepo, callRepo)
        val decision = evaluator.evaluateIncomingCall(spamNumber, null)
        assertEquals(ScreeningDecision.BLOCK, decision)
    }

    @Test
    fun testUnknownCallerReturnsSilenceForScreening() = runTest {
        val policyRepo = FakePolicyRepo()
        val callRepo = FakeCallRepo()

        val evaluator = PolicyEvaluator(policyRepo, callRepo)
        val decision = evaluator.evaluateIncomingCall("+19998887777", null)
        assertEquals(ScreeningDecision.SILENCE, decision)
    }
}
