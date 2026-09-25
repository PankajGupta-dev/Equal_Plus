package com.example.equal_plus

import com.example.equal_plus.data.local.dao.ActionDao
import com.example.equal_plus.data.local.entity.ActionEntity
import com.example.equal_plus.data.local.entity.CallEntity
import com.example.equal_plus.data.model.ActionStatus
import com.example.equal_plus.data.model.ActionType
import com.example.equal_plus.data.model.CallStatus
import com.example.equal_plus.data.model.NextAction
import com.example.equal_plus.data.model.RiskLevel
import com.example.equal_plus.data.repository.ActionRepository
import com.example.equal_plus.data.repository.ActionRepositoryImpl
import com.example.equal_plus.data.repository.CallRepository
import com.example.equal_plus.domain.BackendActionItem
import com.example.equal_plus.domain.BackendDecision
import com.example.equal_plus.domain.DecisionEngine
import com.example.equal_plus.service.NotificationHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationHelperTest {

    class FakeActionRepo : ActionRepository {
        val actions = mutableListOf<ActionEntity>()

        override fun getActionsForCall(callId: String): Flow<List<ActionEntity>> =
            flowOf(actions.filter { it.callId == callId })
        override fun getActionsByStatus(status: ActionStatus): Flow<List<ActionEntity>> =
            flowOf(actions.filter { it.status == status })
        override fun getPendingActions(): Flow<List<ActionEntity>> =
            flowOf(actions.filter { it.status == ActionStatus.PENDING })
        override fun getActionsByType(actionType: ActionType): Flow<List<ActionEntity>> =
            flowOf(actions.filter { it.actionType == actionType })
        override suspend fun insertAction(action: ActionEntity): Long {
            actions.add(action)
            return actions.size.toLong()
        }
        override suspend fun insertActions(actions: List<ActionEntity>): List<Long> {
            this.actions.addAll(actions)
            return actions.map { it.id }
        }
        override suspend fun updateAction(action: ActionEntity): Int = 1
        override suspend fun deleteAction(action: ActionEntity): Int = 1
        override suspend fun deleteActionsForCall(callId: String): Int = 1
    }

    class FakeCallRepo : CallRepository {
        val calls = mutableMapOf<String, CallEntity>()

        override fun getAllCalls(): Flow<List<CallEntity>> = flowOf(calls.values.toList())
        override fun getCallById(id: String): Flow<CallEntity?> = flowOf(calls[id])
        override suspend fun getCallByIdDirect(id: String): CallEntity? = calls[id]
        override fun getCallsForNumber(phoneNumber: String): Flow<List<CallEntity>> = flowOf(emptyList())
        override fun getCallsByStatus(status: CallStatus): Flow<List<CallEntity>> = flowOf(emptyList())
        override fun getCallsByRiskLevel(riskLevel: RiskLevel): Flow<List<CallEntity>> = flowOf(emptyList())
        override fun getSpamCalls(): Flow<List<CallEntity>> = flowOf(emptyList())
        override suspend fun insertCall(call: CallEntity): Long {
            calls[call.id] = call
            return 1L
        }
        override suspend fun insertCalls(calls: List<CallEntity>): List<Long> = emptyList()
        override suspend fun updateCall(call: CallEntity): Int {
            calls[call.id] = call
            return 1
        }
        override suspend fun deleteCall(call: CallEntity): Int = 1
        override suspend fun deleteCallById(id: String): Int = 1
        override suspend fun clearAllCalls(): Int = 1
    }

    @Test
    fun `test DecisionEngine executes NOTIFY_USER and creates ActionEntity`() = runTest {
        val actionRepo = FakeActionRepo()
        val callRepo = FakeCallRepo()
        callRepo.insertCall(
            CallEntity(
                id = "call_escalate_1",
                phoneNumber = "+18005551234",
                status = CallStatus.SCREENING
            )
        )

        val engine = DecisionEngine(
            actionRepository = actionRepo,
            callRepository = callRepo
        )

        val decision = BackendDecision(
            intent = "Urgent Wire Transfer Authorization",
            riskLevel = RiskLevel.HIGH,
            riskScore = 0.89f,
            nextAction = NextAction.ESCALATE,
            response = "Caller demands urgent bank approval.",
            actions = listOf(
                BackendActionItem(
                    type = "NOTIFY_USER",
                    description = "Urgent: Caller requesting immediate bank transfer override."
                )
            )
        )

        val result = engine.executeDecision("call_escalate_1", decision)

        assertEquals("call_escalate_1", result.callId)
        assertEquals(CallStatus.ACTIVE, result.updatedCallStatus)
        assertEquals(RiskLevel.HIGH, result.updatedRiskLevel)
        assertEquals(1, result.executedActions.size)
        assertEquals(ActionType.NOTIFY_USER, result.executedActions[0].actionType)

        // Verify persisted in repository
        val storedAction = actionRepo.actions.find { it.callId == "call_escalate_1" }
        assertNotNull(storedAction)
        assertEquals(ActionType.NOTIFY_USER, storedAction?.actionType)
        assertEquals(ActionStatus.EXECUTED, storedAction?.status)
    }

    @Test
    fun `test DecisionEngine fallback generates NOTIFY_USER on ESCALATE when actions list is empty`() = runTest {
        val actionRepo = FakeActionRepo()
        val engine = DecisionEngine(actionRepository = actionRepo)

        val decision = BackendDecision(
            intent = "Important VIP Inquiry",
            riskLevel = RiskLevel.SAFE,
            nextAction = NextAction.ESCALATE,
            actions = emptyList()
        )

        val result = engine.executeDecision("call_vip_1", decision)
        assertEquals(1, result.executedActions.size)
        assertEquals(ActionType.NOTIFY_USER, result.executedActions[0].actionType)
    }

    @Test
    fun `test Notification constants in NotificationHelper`() {
        assertEquals("equal_plus_call_alerts", NotificationHelper.CHANNEL_ID_ALERTS)
        assertEquals("Equal Plus Call Alerts & Security", NotificationHelper.CHANNEL_NAME_ALERTS)
        assertEquals(1000, NotificationHelper.NOTIFICATION_ID_BASE)
    }
}
