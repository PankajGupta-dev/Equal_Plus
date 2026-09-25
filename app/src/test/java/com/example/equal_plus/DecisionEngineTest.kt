package com.example.equal_plus

import com.example.equal_plus.data.local.entity.ActionEntity
import com.example.equal_plus.data.local.entity.CallEntity
import com.example.equal_plus.data.model.ActionStatus
import com.example.equal_plus.data.model.ActionType
import com.example.equal_plus.data.model.CallStatus
import com.example.equal_plus.data.model.NextAction
import com.example.equal_plus.data.model.RiskLevel
import com.example.equal_plus.data.repository.ActionRepository
import com.example.equal_plus.data.repository.CallRepository
import com.example.equal_plus.domain.DecisionEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DecisionEngineTest {

    private class FakeActionRepo : ActionRepository {
        val actions = mutableListOf<ActionEntity>()
        override fun getActionsForCall(callId: String): Flow<List<ActionEntity>> =
            MutableStateFlow(actions.filter { it.callId == callId })
        override fun getActionsByStatus(status: ActionStatus): Flow<List<ActionEntity>> = MutableStateFlow(emptyList())
        override fun getPendingActions(): Flow<List<ActionEntity>> = MutableStateFlow(emptyList())
        override fun getActionsByType(actionType: ActionType): Flow<List<ActionEntity>> = MutableStateFlow(emptyList())
        override suspend fun insertAction(action: ActionEntity): Long {
            actions.add(action)
            return 1L
        }
        override suspend fun insertActions(actions: List<ActionEntity>): List<Long> {
            this.actions.addAll(actions)
            return actions.map { 1L }
        }
        override suspend fun updateAction(action: ActionEntity): Int = 1
        override suspend fun deleteAction(action: ActionEntity): Int = 1
        override suspend fun deleteActionsForCall(callId: String): Int = 1
    }

    private class FakeCallRepo : CallRepository {
        val calls = mutableListOf<CallEntity>()
        override fun getAllCalls(): Flow<List<CallEntity>> = MutableStateFlow(calls)
        override fun getCallById(id: String): Flow<CallEntity?> = MutableStateFlow(null)
        override suspend fun getCallByIdDirect(id: String): CallEntity? = calls.find { it.id == id }
        override fun getCallsForNumber(phoneNumber: String): Flow<List<CallEntity>> = MutableStateFlow(emptyList())
        override fun getCallsByStatus(status: CallStatus): Flow<List<CallEntity>> = MutableStateFlow(emptyList())
        override fun getCallsByRiskLevel(riskLevel: RiskLevel): Flow<List<CallEntity>> = MutableStateFlow(emptyList())
        override fun getSpamCalls(): Flow<List<CallEntity>> = MutableStateFlow(emptyList())
        override suspend fun insertCall(call: CallEntity): Long { calls.add(call); return 1L }
        override suspend fun insertCalls(calls: List<CallEntity>): List<Long> { this.calls.addAll(calls); return listOf(1L) }
        override suspend fun updateCall(call: CallEntity): Int {
            val idx = calls.indexOfFirst { it.id == call.id }
            if (idx >= 0) calls[idx] = call
            return 1
        }
        override suspend fun deleteCall(call: CallEntity): Int = 1
        override suspend fun deleteCallById(id: String): Int = 1
        override suspend fun clearAllCalls(): Int = 1
    }

    @Test
    fun testParseDecisionJson() {
        val json = """
            {
                "intent": "Delivery Dropoff Coordination",
                "risk_level": "SAFE",
                "risk_score": 0.02,
                "next_action": "RESOLVE",
                "response": "Please place the package on the front porch.",
                "entities": {
                    "location": "Front Porch",
                    "carrier": "FedEx",
                    "tracking": "987654321"
                },
                "actions": [
                    {
                        "type": "SAVE_DELIVERY_INSTRUCTION",
                        "description": "Package dropped at front porch by FedEx",
                        "payload": {
                            "location": "Front Porch"
                        }
                    },
                    {
                        "type": "CREATE_REMINDER",
                        "description": "Bring in FedEx package from porch",
                        "payload": {
                            "reminder_time": "18:00"
                        }
                    }
                ]
            }
        """.trimIndent()

        val engine = DecisionEngine(FakeActionRepo())
        val decision = engine.parseDecisionJson(json)

        assertEquals("Delivery Dropoff Coordination", decision.intent)
        assertEquals(RiskLevel.SAFE, decision.riskLevel)
        assertEquals(0.02f, decision.riskScore, 0.001f)
        assertEquals(NextAction.RESOLVE, decision.nextAction)
        assertEquals("Please place the package on the front porch.", decision.response)
        assertEquals("Front Porch", decision.entities["location"])
        assertEquals(2, decision.actions.size)
        assertEquals("SAVE_DELIVERY_INSTRUCTION", decision.actions[0].type)
        assertEquals("CREATE_REMINDER", decision.actions[1].type)
    }

    @Test
    fun testExecuteTerminateScamCallDecision() = runTest {
        val fakeActionRepo = FakeActionRepo()
        val fakeCallRepo = FakeCallRepo()
        val callId = "call_scam_001"

        fakeCallRepo.calls.add(
            CallEntity(
                id = callId,
                phoneNumber = "+18005559999",
                status = CallStatus.SCREENING,
                riskLevel = RiskLevel.UNKNOWN
            )
        )

        val json = """
            {
                "intent": "Fake IRS Tax Threat",
                "risk_level": "CRITICAL",
                "risk_score": 0.98,
                "next_action": "TERMINATE",
                "response": "This call is identified as a fraudulent tax scam. Terminating call.",
                "actions": [
                    {
                        "type": "BLOCK_CALL",
                        "description": "Auto-blocked IRS scam imposter."
                    }
                ]
            }
        """.trimIndent()

        val engine = DecisionEngine(fakeActionRepo, fakeCallRepo)
        val result = engine.executeDecision(callId, json)

        assertEquals(CallStatus.BLOCKED, result.updatedCallStatus)
        assertEquals(RiskLevel.CRITICAL, result.updatedRiskLevel)
        assertEquals(1, fakeActionRepo.actions.size)
        assertEquals(ActionType.BLOCK_CALL, fakeActionRepo.actions[0].actionType)
        assertEquals(CallStatus.BLOCKED, fakeCallRepo.calls[0].status)
    }

    @Test
    fun testExecuteResolveActionsPersistence() = runTest {
        val fakeActionRepo = FakeActionRepo()
        val fakeCallRepo = FakeCallRepo()
        val callId = "call_delivery_002"

        val json = """
            {
                "intent": "Doctor Appointment Confirmation",
                "risk_level": "SAFE",
                "risk_score": 0.01,
                "next_action": "RESOLVE",
                "response": "Confirmed for tomorrow at 3 PM.",
                "entities": {
                    "doctor": "Dr. Miller",
                    "time": "Tomorrow 3:00 PM"
                },
                "actions": [
                    {
                        "type": "CREATE_CALENDAR_EVENT",
                        "description": "Appointment with Dr. Miller tomorrow at 3 PM"
                    },
                    {
                        "type": "REQUEST_CALLBACK",
                        "description": "Call clinic if running late"
                    }
                ]
            }
        """.trimIndent()

        val engine = DecisionEngine(fakeActionRepo, fakeCallRepo)
        val result = engine.executeDecision(callId, json)

        assertEquals(CallStatus.COMPLETED, result.updatedCallStatus)
        assertEquals(2, result.executedActions.size)
        assertEquals(2, fakeActionRepo.actions.size)
        assertTrue(fakeActionRepo.actions.any { it.actionType == ActionType.CREATE_CALENDAR_EVENT })
        assertTrue(fakeActionRepo.actions.any { it.actionType == ActionType.REQUEST_CALLBACK })
    }
}
