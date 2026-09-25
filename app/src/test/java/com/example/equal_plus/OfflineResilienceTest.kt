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
import com.example.equal_plus.data.model.RepositoryResult
import com.example.equal_plus.data.model.Resource
import com.example.equal_plus.data.model.RiskLevel
import com.example.equal_plus.data.model.SpeakerType
import com.example.equal_plus.data.network.ApiService
import com.example.equal_plus.data.network.model.ActionDto
import com.example.equal_plus.data.network.model.CallDto
import com.example.equal_plus.data.network.model.ConversationDto
import com.example.equal_plus.data.network.model.UserProfileDto
import com.example.equal_plus.data.network.model.UserPolicySyncDto
import com.example.equal_plus.data.repository.ActionRepositoryImpl
import com.example.equal_plus.data.repository.CallRepositoryImpl
import com.example.equal_plus.data.repository.ConversationRepositoryImpl
import com.example.equal_plus.ui.details.ConversationDetailsViewModel
import com.example.equal_plus.ui.history.CallHistoryViewModel
import com.example.equal_plus.ui.home.HomeViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class OfflineResilienceTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    class OfflineCallDao : CallDao {
        val storage = mutableMapOf<String, CallEntity>()
        private val flowState = MutableStateFlow<List<CallEntity>>(emptyList())

        override fun getAllCalls(): Flow<List<CallEntity>> = flowState.asStateFlow()
        override fun getCallById(id: String): Flow<CallEntity?> = flowOf(storage[id])
        override suspend fun getCallByIdDirect(id: String): CallEntity? = storage[id]
        override fun getCallsForNumber(phoneNumber: String): Flow<List<CallEntity>> =
            flowOf(storage.values.filter { it.phoneNumber == phoneNumber })
        override fun getCallsByStatus(status: CallStatus): Flow<List<CallEntity>> =
            flowOf(storage.values.filter { it.status == status })
        override fun getCallsByRiskLevel(riskLevel: RiskLevel): Flow<List<CallEntity>> =
            flowOf(storage.values.filter { it.riskLevel == riskLevel })
        override fun getSpamCalls(): Flow<List<CallEntity>> =
            flowOf(storage.values.filter { it.isSpam })
        override suspend fun insertCall(call: CallEntity): Long {
            storage[call.id] = call
            flowState.value = storage.values.toList()
            return 1L
        }
        override suspend fun insertCalls(calls: List<CallEntity>): List<Long> {
            calls.forEach { storage[it.id] = it }
            flowState.value = storage.values.toList()
            return calls.map { 1L }
        }
        override suspend fun updateCall(call: CallEntity): Int {
            storage[call.id] = call
            flowState.value = storage.values.toList()
            return 1
        }
        override suspend fun deleteCall(call: CallEntity): Int {
            val removed = storage.remove(call.id) != null
            flowState.value = storage.values.toList()
            return if (removed) 1 else 0
        }
        override suspend fun deleteCallById(id: String): Int {
            val removed = storage.remove(id) != null
            flowState.value = storage.values.toList()
            return if (removed) 1 else 0
        }
        override suspend fun clearAllCalls(): Int {
            val count = storage.size
            storage.clear()
            flowState.value = emptyList()
            return count
        }
    }

    class OfflineConversationDao : ConversationDao {
        val storage = mutableListOf<ConversationEntity>()
        private val flowState = MutableStateFlow<List<ConversationEntity>>(emptyList())

        override fun getConversationsForCall(callId: String): Flow<List<ConversationEntity>> =
            flowOf(storage.filter { it.callId == callId })
        override suspend fun getConversationsForCallDirect(callId: String): List<ConversationEntity> =
            storage.filter { it.callId == callId }
        override fun getFlaggedConversations(): Flow<List<ConversationEntity>> =
            flowOf(storage.filter { it.isFlagged })
        override suspend fun insertConversation(conversation: ConversationEntity): Long {
            storage.add(conversation)
            flowState.value = storage.toList()
            return storage.size.toLong()
        }
        override suspend fun insertConversations(conversations: List<ConversationEntity>): List<Long> {
            storage.addAll(conversations)
            flowState.value = storage.toList()
            return conversations.map { 1L }
        }
        override suspend fun updateConversation(conversation: ConversationEntity): Int = 1
        override suspend fun deleteConversation(conversation: ConversationEntity): Int = 1
        override suspend fun deleteConversationsForCall(callId: String): Int = 1
    }

    class OfflineActionDao : ActionDao {
        val storage = mutableListOf<ActionEntity>()
        private val flowState = MutableStateFlow<List<ActionEntity>>(emptyList())

        override fun getActionsForCall(callId: String): Flow<List<ActionEntity>> =
            flowOf(storage.filter { it.callId == callId })
        override fun getActionsByStatus(status: ActionStatus): Flow<List<ActionEntity>> =
            flowOf(storage.filter { it.status == status })
        override fun getPendingActions(): Flow<List<ActionEntity>> =
            flowOf(storage.filter { it.status == ActionStatus.PENDING })
        override fun getActionsByType(actionType: ActionType): Flow<List<ActionEntity>> =
            flowOf(storage.filter { it.actionType == actionType })
        override suspend fun insertAction(action: ActionEntity): Long {
            storage.add(action)
            flowState.value = storage.toList()
            return storage.size.toLong()
        }
        override suspend fun insertActions(actions: List<ActionEntity>): List<Long> {
            storage.addAll(actions)
            flowState.value = storage.toList()
            return actions.map { 1L }
        }
        override suspend fun updateAction(action: ActionEntity): Int = 1
        override suspend fun deleteAction(action: ActionEntity): Int = 1
        override suspend fun deleteActionsForCall(callId: String): Int = 1
    }

    class FailingNetworkApiService : ApiService {
        override suspend fun getCalls(limit: Int, offset: Int): Response<List<CallDto>> {
            throw IOException("Airplane mode / No network connection")
        }
        override suspend fun getCallById(callId: String): Response<CallDto> {
            throw IOException("Airplane mode / No network connection")
        }
        override suspend fun createCall(call: CallDto): Response<CallDto> {
            throw IOException("Airplane mode / No network connection")
        }
        override suspend fun updateCall(callId: String, call: CallDto): Response<CallDto> {
            throw IOException("Airplane mode / No network connection")
        }
        override suspend fun getConversationsForCall(callId: String): Response<List<ConversationDto>> {
            throw IOException("Airplane mode / No network connection")
        }
        override suspend fun createConversationTurn(conversation: ConversationDto): Response<ConversationDto> {
            throw IOException("Airplane mode / No network connection")
        }
        override suspend fun getActionsForCall(callId: String): Response<List<ActionDto>> {
            throw IOException("Airplane mode / No network connection")
        }
        override suspend fun createAction(action: ActionDto): Response<ActionDto> {
            throw IOException("Airplane mode / No network connection")
        }
        override suspend fun getUserProfile(): Response<UserProfileDto> {
            throw IOException("Airplane mode / No network connection")
        }
        override suspend fun syncPolicySettings(policySync: UserPolicySyncDto): Response<UserPolicySyncDto> {
            throw IOException("Airplane mode / No network connection")
        }
    }

    @Test
    fun `test Resource wrapper behavior`() {
        val successResource: Resource<String> = Resource.Success("Data Loaded")
        assertTrue(successResource.isSuccess)
        assertFalse(successResource.isError)
        assertEquals("Data Loaded", successResource.getOrNull())
        assertEquals("Data Loaded", successResource.getOrDefault("Default"))

        var callbackCalled = false
        successResource.onSuccess {
            assertEquals("Data Loaded", it)
            callbackCalled = true
        }
        assertTrue(callbackCalled)

        val errorResource: Resource<String> = Resource.Error("Offline mode", IOException("Network lost"))
        assertTrue(errorResource.isError)
        assertFalse(errorResource.isSuccess)
        assertEquals(null, errorResource.getOrNull())
        assertEquals("Default", errorResource.getOrDefault("Default"))
    }

    @Test
    fun `test RepositoryResult wrapper behavior`() {
        val cachedResult: RepositoryResult<List<String>> =
            RepositoryResult.FallbackCached(listOf("Item 1", "Item 2"), "Network down")
        assertTrue(cachedResult.isSuccess)
        assertEquals(2, cachedResult.getOrNull()?.size)
    }

    @Test
    fun `test airplane mode CallRepository sync falls back to cached Room calls`() = runTest {
        val dao = OfflineCallDao()
        val offlineCall = CallEntity(
            id = "cached_call_1",
            phoneNumber = "+18005550000",
            status = CallStatus.COMPLETED,
            riskLevel = RiskLevel.SAFE,
            category = "Delivery"
        )
        dao.insertCall(offlineCall)

        val failingApi = FailingNetworkApiService()
        val repo = CallRepositoryImpl(dao, failingApi)

        val result = repo.syncCallsWithFallback()
        assertTrue(result.isSuccess)
        val data = result.getOrNull()
        assertNotNull(data)
        assertEquals(1, data?.size)
        assertEquals("cached_call_1", data?.first()?.id)
    }

    @Test
    fun `test airplane mode HomeViewModel renders cached Room data without crashing`() = runTest {
        val dao = OfflineCallDao()
        val failingApi = FailingNetworkApiService()
        val repo = CallRepositoryImpl(dao, failingApi)

        dao.insertCalls(
            listOf(
                CallEntity(id = "c1", phoneNumber = "+1001", status = CallStatus.COMPLETED, riskLevel = RiskLevel.SAFE),
                CallEntity(id = "c2", phoneNumber = "+1002", status = CallStatus.BLOCKED, riskLevel = RiskLevel.HIGH)
            )
        )

        val viewModel = HomeViewModel(repo)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(2, state.handledCount)
        assertEquals(2, state.recentCalls.size)
    }

    @Test
    fun `test airplane mode CallHistoryViewModel renders cached Room data without crashing`() = runTest {
        val dao = OfflineCallDao()
        val failingApi = FailingNetworkApiService()
        val repo = CallRepositoryImpl(dao, failingApi)

        dao.insertCalls(
            listOf(
                CallEntity(id = "h1", phoneNumber = "+1001", status = CallStatus.COMPLETED, riskLevel = RiskLevel.SAFE),
                CallEntity(id = "h2", phoneNumber = "+1002", status = CallStatus.BLOCKED, riskLevel = RiskLevel.HIGH)
            )
        )

        val viewModel = CallHistoryViewModel(repo)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(2, state.allCalls.size)
        assertEquals(2, state.filteredCalls.size)
    }

    @Test
    fun `test airplane mode ConversationDetailsViewModel renders cached Room data without crashing`() = runTest {
        val callDao = OfflineCallDao()
        val convDao = OfflineConversationDao()
        val actDao = OfflineActionDao()
        val failingApi = FailingNetworkApiService()

        val callRepo = CallRepositoryImpl(callDao, failingApi)
        val convRepo = ConversationRepositoryImpl(convDao, failingApi)
        val actRepo = ActionRepositoryImpl(actDao, failingApi)

        val callId = "offline_call_100"
        callDao.insertCall(
            CallEntity(id = callId, phoneNumber = "+18005559999", status = CallStatus.COMPLETED, riskLevel = RiskLevel.SAFE)
        )
        convDao.insertConversation(
            ConversationEntity(callId = callId, speaker = SpeakerType.CALLER, message = "Where is my parcel?")
        )
        actDao.insertAction(
            ActionEntity(callId = callId, actionType = ActionType.NOTIFY_USER, status = ActionStatus.EXECUTED)
        )

        val viewModel = ConversationDetailsViewModel(callId, callRepo, convRepo, actRepo)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNotNull(state.call)
        assertEquals(callId, state.call?.id)
        assertEquals(1, state.conversations.size)
        assertEquals(1, state.actions.size)
    }
}
