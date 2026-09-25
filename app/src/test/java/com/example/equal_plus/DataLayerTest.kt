package com.example.equal_plus

import com.example.equal_plus.data.local.dao.ActionDao
import com.example.equal_plus.data.local.dao.CallDao
import com.example.equal_plus.data.local.dao.ConversationDao
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
import com.example.equal_plus.data.repository.ActionRepositoryImpl
import com.example.equal_plus.data.repository.CallRepository
import com.example.equal_plus.data.repository.CallRepositoryImpl
import com.example.equal_plus.data.repository.ConversationRepository
import com.example.equal_plus.data.repository.ConversationRepositoryImpl
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class DataLayerTest {

    private class FakeCallDao : CallDao {
        val calls = mutableListOf<CallEntity>()
        override fun getAllCalls(): Flow<List<CallEntity>> = flowOf(calls)
        override fun getCallById(id: String): Flow<CallEntity?> = flowOf(calls.find { it.id == id })
        override suspend fun getCallByIdDirect(id: String): CallEntity? = calls.find { it.id == id }
        override fun getCallsForNumber(phoneNumber: String): Flow<List<CallEntity>> = flowOf(calls.filter { it.phoneNumber == phoneNumber })
        override fun getCallsByStatus(status: CallStatus): Flow<List<CallEntity>> = flowOf(calls.filter { it.status == status })
        override fun getCallsByRiskLevel(riskLevel: RiskLevel): Flow<List<CallEntity>> = flowOf(calls.filter { it.riskLevel == riskLevel })
        override fun getSpamCalls(): Flow<List<CallEntity>> = flowOf(calls.filter { it.isSpam })
        override suspend fun insertCall(call: CallEntity): Long { calls.add(call); return 1L }
        override suspend fun insertCalls(calls: List<CallEntity>): List<Long> { this.calls.addAll(calls); return listOf(1L) }
        override suspend fun updateCall(call: CallEntity): Int = 1
        override suspend fun deleteCall(call: CallEntity): Int { calls.remove(call); return 1 }
        override suspend fun deleteCallById(id: String): Int { calls.removeAll { it.id == id }; return 1 }
        override suspend fun clearAllCalls(): Int { val count = calls.size; calls.clear(); return count }
    }

    private class FakeConversationDao : ConversationDao {
        val conversations = mutableListOf<ConversationEntity>()
        override fun getConversationsForCall(callId: String): Flow<List<ConversationEntity>> = flowOf(conversations.filter { it.callId == callId })
        override suspend fun getConversationsForCallDirect(callId: String): List<ConversationEntity> = conversations.filter { it.callId == callId }
        override fun getFlaggedConversations(): Flow<List<ConversationEntity>> = flowOf(conversations.filter { it.isFlagged })
        override suspend fun insertConversation(conversation: ConversationEntity): Long { conversations.add(conversation); return 1L }
        override suspend fun insertConversations(conversations: List<ConversationEntity>): List<Long> { this.conversations.addAll(conversations); return listOf(1L) }
        override suspend fun updateConversation(conversation: ConversationEntity): Int = 1
        override suspend fun deleteConversation(conversation: ConversationEntity): Int { conversations.remove(conversation); return 1 }
        override suspend fun deleteConversationsForCall(callId: String): Int { conversations.removeAll { it.callId == callId }; return 1 }
    }

    private class FakeActionDao : ActionDao {
        val actions = mutableListOf<ActionEntity>()
        override fun getActionsForCall(callId: String): Flow<List<ActionEntity>> = flowOf(actions.filter { it.callId == callId })
        override fun getActionsByStatus(status: ActionStatus): Flow<List<ActionEntity>> = flowOf(actions.filter { it.status == status })
        override fun getPendingActions(): Flow<List<ActionEntity>> = flowOf(actions.filter { it.status == ActionStatus.PENDING })
        override fun getActionsByType(actionType: ActionType): Flow<List<ActionEntity>> = flowOf(actions.filter { it.actionType == actionType })
        override suspend fun insertAction(action: ActionEntity): Long { actions.add(action); return 1L }
        override suspend fun insertActions(actions: List<ActionEntity>): List<Long> { this.actions.addAll(actions); return listOf(1L) }
        override suspend fun updateAction(action: ActionEntity): Int = 1
        override suspend fun deleteAction(action: ActionEntity): Int { actions.remove(action); return 1 }
        override suspend fun deleteActionsForCall(callId: String): Int { actions.removeAll { it.callId == callId }; return 1 }
    }

    @Test
    fun testCallRepositoryInjectionAndInsertion() = runBlocking {
        val fakeDao = FakeCallDao()
        val repository: CallRepository = CallRepositoryImpl(fakeDao)

        val call = CallEntity(
            id = "call-123",
            phoneNumber = "+1234567890",
            callType = CallType.INCOMING,
            status = CallStatus.RINGING,
            riskLevel = RiskLevel.MEDIUM
        )

        repository.insertCall(call)
        val direct = repository.getCallByIdDirect("call-123")
        assertNotNull(direct)
        assertEquals("+1234567890", direct?.phoneNumber)
    }

    @Test
    fun testConversationRepositoryInjection() = runBlocking {
        val fakeDao = FakeConversationDao()
        val repository: ConversationRepository = ConversationRepositoryImpl(fakeDao)

        val conversation = ConversationEntity(
            callId = "call-123",
            speaker = SpeakerType.CALLER,
            message = "Hello, I am calling regarding your account."
        )

        repository.insertConversation(conversation)
        val list = repository.getConversationsForCallDirect("call-123")
        assertEquals(1, list.size)
        assertEquals("Hello, I am calling regarding your account.", list[0].message)
    }

    @Test
    fun testActionRepositoryInjection() = runBlocking {
        val fakeDao = FakeActionDao()
        val repository: ActionRepository = ActionRepositoryImpl(fakeDao)

        val action = ActionEntity(
            callId = "call-123",
            actionType = ActionType.BLOCK_NUMBER,
            status = ActionStatus.PENDING
        )

        repository.insertAction(action)
        assertNotNull(repository)
    }
}
