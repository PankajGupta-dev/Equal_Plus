package com.example.equal_plus

import com.example.equal_plus.data.local.entity.ActionEntity
import com.example.equal_plus.data.local.entity.CallEntity
import com.example.equal_plus.data.local.entity.ConversationEntity
import com.example.equal_plus.data.model.ActionStatus
import com.example.equal_plus.data.model.ActionType
import com.example.equal_plus.data.model.CallStatus
import com.example.equal_plus.data.model.CallType
import com.example.equal_plus.data.model.RiskLevel
import com.example.equal_plus.data.model.SpeakerType
import com.example.equal_plus.data.repository.ActionRepository
import com.example.equal_plus.data.repository.CallRepository
import com.example.equal_plus.data.repository.ConversationRepository
import com.example.equal_plus.ui.details.ConversationDetailsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConversationDetailsViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private class FakeCallRepo : CallRepository {
        val callsFlow = MutableStateFlow<List<CallEntity>>(emptyList())
        override fun getAllCalls(): Flow<List<CallEntity>> = callsFlow
        override fun getCallById(id: String): Flow<CallEntity?> = MutableStateFlow(callsFlow.value.find { it.id == id })
        override suspend fun getCallByIdDirect(id: String): CallEntity? = callsFlow.value.find { it.id == id }
        override fun getCallsForNumber(phoneNumber: String): Flow<List<CallEntity>> = callsFlow
        override fun getCallsByStatus(status: CallStatus): Flow<List<CallEntity>> = callsFlow
        override fun getCallsByRiskLevel(riskLevel: RiskLevel): Flow<List<CallEntity>> = callsFlow
        override fun getSpamCalls(): Flow<List<CallEntity>> = callsFlow
        override suspend fun insertCall(call: CallEntity): Long {
            callsFlow.value = callsFlow.value + call
            return 1L
        }
        override suspend fun insertCalls(calls: List<CallEntity>): List<Long> {
            callsFlow.value = callsFlow.value + calls
            return calls.map { 1L }
        }
        override suspend fun updateCall(call: CallEntity): Int = 1
        override suspend fun deleteCall(call: CallEntity): Int = 1
        override suspend fun deleteCallById(id: String): Int = 1
        override suspend fun clearAllCalls(): Int = 1
    }

    private class FakeConversationRepo : ConversationRepository {
        val turnsFlow = MutableStateFlow<List<ConversationEntity>>(emptyList())
        override fun getConversationsForCall(callId: String): Flow<List<ConversationEntity>> =
            MutableStateFlow(turnsFlow.value.filter { it.callId == callId })
        override suspend fun getConversationsForCallDirect(callId: String): List<ConversationEntity> =
            turnsFlow.value.filter { it.callId == callId }
        override fun getFlaggedConversations(): Flow<List<ConversationEntity>> = turnsFlow
        override suspend fun insertConversation(conversation: ConversationEntity): Long {
            turnsFlow.value = turnsFlow.value + conversation
            return 1L
        }
        override suspend fun insertConversations(conversations: List<ConversationEntity>): List<Long> {
            turnsFlow.value = turnsFlow.value + conversations
            return conversations.map { 1L }
        }
        override suspend fun updateConversation(conversation: ConversationEntity): Int = 1
        override suspend fun deleteConversation(conversation: ConversationEntity): Int = 1
        override suspend fun deleteConversationsForCall(callId: String): Int = 1
    }

    private class FakeActionRepo : ActionRepository {
        val actionsFlow = MutableStateFlow<List<ActionEntity>>(emptyList())
        override fun getActionsForCall(callId: String): Flow<List<ActionEntity>> =
            MutableStateFlow(actionsFlow.value.filter { it.callId == callId })
        override fun getActionsByStatus(status: ActionStatus): Flow<List<ActionEntity>> = actionsFlow
        override fun getPendingActions(): Flow<List<ActionEntity>> = actionsFlow
        override fun getActionsByType(actionType: ActionType): Flow<List<ActionEntity>> = actionsFlow
        override suspend fun insertAction(action: ActionEntity): Long {
            actionsFlow.value = actionsFlow.value + action
            return 1L
        }
        override suspend fun insertActions(actions: List<ActionEntity>): List<Long> {
            actionsFlow.value = actionsFlow.value + actions
            return actions.map { 1L }
        }
        override suspend fun updateAction(action: ActionEntity): Int = 1
        override suspend fun deleteAction(action: ActionEntity): Int = 1
        override suspend fun deleteActionsForCall(callId: String): Int = 1
    }

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testConversationDetailsViewModelDataFetch() = runTest {
        val fakeCallRepo = FakeCallRepo()
        val fakeConvRepo = FakeConversationRepo()
        val fakeActionRepo = FakeActionRepo()

        val callId = "test_call_999"
        val call = CallEntity(
            id = callId,
            phoneNumber = "+1999999999",
            contactName = "Security Center",
            status = CallStatus.BLOCKED,
            riskLevel = RiskLevel.HIGH
        )
        fakeCallRepo.insertCall(call)

        val turn = ConversationEntity(
            callId = callId,
            speaker = SpeakerType.CALLER,
            message = "Verify your account code."
        )
        fakeConvRepo.insertConversation(turn)

        val action = ActionEntity(
            callId = callId,
            actionType = ActionType.BLOCK_NUMBER,
            status = ActionStatus.EXECUTED
        )
        fakeActionRepo.insertAction(action)

        val viewModel = ConversationDetailsViewModel(
            callId = callId,
            callRepository = fakeCallRepo,
            conversationRepository = fakeConvRepo,
            actionRepository = fakeActionRepo
        )

        val state = viewModel.uiState.value
        assertNotNull(state.call)
        assertEquals(callId, state.call?.id)
        assertEquals(1, state.conversations.size)
        assertEquals("Verify your account code.", state.conversations[0].message)
        assertEquals(1, state.actions.size)
        assertEquals(ActionType.BLOCK_NUMBER, state.actions[0].actionType)
    }
}
