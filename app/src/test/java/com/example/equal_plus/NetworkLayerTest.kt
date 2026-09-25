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
import com.example.equal_plus.data.network.ApiService
import com.example.equal_plus.data.network.AuthInterceptor
import com.example.equal_plus.data.network.AuthTokenProvider
import com.example.equal_plus.data.network.model.ActionDto
import com.example.equal_plus.data.network.model.CallDto
import com.example.equal_plus.data.network.model.ConversationDto
import com.example.equal_plus.data.network.model.UserProfileDto
import com.example.equal_plus.data.network.model.UserPolicySyncDto
import com.example.equal_plus.data.repository.ActionRepositoryImpl
import com.example.equal_plus.data.repository.CallRepositoryImpl
import com.example.equal_plus.data.repository.ConversationRepositoryImpl
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkLayerTest {

    // --- Fake DAOs ---
    class FakeCallDao : CallDao {
        val calls = mutableMapOf<String, CallEntity>()

        override fun getAllCalls(): Flow<List<CallEntity>> = flowOf(calls.values.toList())
        override fun getCallById(id: String): Flow<CallEntity?> = flowOf(calls[id])
        override suspend fun getCallByIdDirect(id: String): CallEntity? = calls[id]
        override fun getCallsForNumber(phoneNumber: String): Flow<List<CallEntity>> =
            flowOf(calls.values.filter { it.phoneNumber == phoneNumber })
        override fun getCallsByStatus(status: CallStatus): Flow<List<CallEntity>> =
            flowOf(calls.values.filter { it.status == status })
        override fun getCallsByRiskLevel(riskLevel: RiskLevel): Flow<List<CallEntity>> =
            flowOf(calls.values.filter { it.riskLevel == riskLevel })
        override fun getSpamCalls(): Flow<List<CallEntity>> =
            flowOf(calls.values.filter { it.isSpam })
        override suspend fun insertCall(call: CallEntity): Long {
            calls[call.id] = call
            return 1L
        }
        override suspend fun insertCalls(callList: List<CallEntity>): List<Long> {
            callList.forEach { calls[it.id] = it }
            return callList.map { 1L }
        }
        override suspend fun updateCall(call: CallEntity): Int {
            calls[call.id] = call
            return 1
        }
        override suspend fun deleteCall(call: CallEntity): Int {
            return if (calls.remove(call.id) != null) 1 else 0
        }
        override suspend fun deleteCallById(id: String): Int {
            return if (calls.remove(id) != null) 1 else 0
        }
        override suspend fun clearAllCalls(): Int {
            val count = calls.size
            calls.clear()
            return count
        }
    }

    class FakeConversationDao : ConversationDao {
        val list = mutableListOf<ConversationEntity>()

        override fun getConversationsForCall(callId: String): Flow<List<ConversationEntity>> =
            flowOf(list.filter { it.callId == callId })
        override suspend fun getConversationsForCallDirect(callId: String): List<ConversationEntity> =
            list.filter { it.callId == callId }
        override fun getFlaggedConversations(): Flow<List<ConversationEntity>> =
            flowOf(list.filter { it.isFlagged })
        override suspend fun insertConversation(conversation: ConversationEntity): Long {
            list.add(conversation)
            return list.size.toLong()
        }
        override suspend fun insertConversations(conversations: List<ConversationEntity>): List<Long> {
            list.addAll(conversations)
            return conversations.map { 1L }
        }
        override suspend fun updateConversation(conversation: ConversationEntity): Int = 1
        override suspend fun deleteConversation(conversation: ConversationEntity): Int {
            return if (list.remove(conversation)) 1 else 0
        }
        override suspend fun deleteConversationsForCall(callId: String): Int {
            val before = list.size
            list.removeAll { it.callId == callId }
            return before - list.size
        }
    }

    class FakeActionDao : ActionDao {
        val list = mutableListOf<ActionEntity>()

        override fun getActionsForCall(callId: String): Flow<List<ActionEntity>> =
            flowOf(list.filter { it.callId == callId })
        override fun getActionsByStatus(status: ActionStatus): Flow<List<ActionEntity>> =
            flowOf(list.filter { it.status == status })
        override fun getPendingActions(): Flow<List<ActionEntity>> =
            flowOf(list.filter { it.status == ActionStatus.PENDING })
        override fun getActionsByType(actionType: ActionType): Flow<List<ActionEntity>> =
            flowOf(list.filter { it.actionType == actionType })
        override suspend fun insertAction(action: ActionEntity): Long {
            list.add(action)
            return list.size.toLong()
        }
        override suspend fun insertActions(actions: List<ActionEntity>): List<Long> {
            list.addAll(actions)
            return actions.map { 1L }
        }
        override suspend fun updateAction(action: ActionEntity): Int = 1
        override suspend fun deleteAction(action: ActionEntity): Int {
            return if (list.remove(action)) 1 else 0
        }
        override suspend fun deleteActionsForCall(callId: String): Int {
            val before = list.size
            list.removeAll { it.callId == callId }
            return before - list.size
        }
    }

    // --- Fake ApiService ---
    class FakeApiService(
        var remoteCalls: MutableList<CallDto> = mutableListOf(),
        var remoteConversations: MutableList<ConversationDto> = mutableListOf(),
        var remoteActions: MutableList<ActionDto> = mutableListOf(),
        var shouldFail: Boolean = false
    ) : ApiService {

        override suspend fun getCalls(limit: Int, offset: Int): retrofit2.Response<List<CallDto>> {
            if (shouldFail) return retrofit2.Response.error(500, "Server error".toResponseBody())
            return retrofit2.Response.success(remoteCalls)
        }

        override suspend fun getCallById(callId: String): retrofit2.Response<CallDto> {
            if (shouldFail) return retrofit2.Response.error(500, "Server error".toResponseBody())
            val item = remoteCalls.find { it.id == callId }
            return if (item != null) retrofit2.Response.success(item) else retrofit2.Response.error(404, "Not found".toResponseBody())
        }

        override suspend fun createCall(call: CallDto): retrofit2.Response<CallDto> {
            if (shouldFail) return retrofit2.Response.error(500, "Server error".toResponseBody())
            remoteCalls.add(call)
            return retrofit2.Response.success(call)
        }

        override suspend fun updateCall(callId: String, call: CallDto): retrofit2.Response<CallDto> {
            if (shouldFail) return retrofit2.Response.error(500, "Server error".toResponseBody())
            remoteCalls.removeAll { it.id == callId }
            remoteCalls.add(call)
            return retrofit2.Response.success(call)
        }

        override suspend fun getConversationsForCall(callId: String): retrofit2.Response<List<ConversationDto>> {
            if (shouldFail) return retrofit2.Response.error(500, "Server error".toResponseBody())
            return retrofit2.Response.success(remoteConversations.filter { it.callId == callId })
        }

        override suspend fun createConversationTurn(conversation: ConversationDto): retrofit2.Response<ConversationDto> {
            if (shouldFail) return retrofit2.Response.error(500, "Server error".toResponseBody())
            remoteConversations.add(conversation)
            return retrofit2.Response.success(conversation)
        }

        override suspend fun getActionsForCall(callId: String): retrofit2.Response<List<ActionDto>> {
            if (shouldFail) return retrofit2.Response.error(500, "Server error".toResponseBody())
            return retrofit2.Response.success(remoteActions.filter { it.callId == callId })
        }

        override suspend fun createAction(action: ActionDto): retrofit2.Response<ActionDto> {
            if (shouldFail) return retrofit2.Response.error(500, "Server error".toResponseBody())
            remoteActions.add(action)
            return retrofit2.Response.success(action)
        }

        override suspend fun getUserProfile(): retrofit2.Response<UserProfileDto> {
            return retrofit2.Response.success(UserProfileDto("user_123", "+1234567890", "Test User", "test@equalplus.ai"))
        }

        override suspend fun syncPolicySettings(policySync: UserPolicySyncDto): retrofit2.Response<UserPolicySyncDto> {
            return retrofit2.Response.success(policySync)
        }
    }

    // Fake Interceptor Chain for testing AuthInterceptor
    class FakeInterceptorChain(private val request: Request) : Interceptor.Chain {
        var capturedRequest: Request? = null

        override fun request(): Request = request
        override fun proceed(request: Request): Response {
            capturedRequest = request
            return Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("{}".toResponseBody())
                .build()
        }
        override fun connection() = null
        override fun call(): okhttp3.Call = throw NotImplementedError()
        override fun connectTimeoutMillis() = 1000
        override fun withConnectTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this
        override fun readTimeoutMillis() = 1000
        override fun withReadTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this
        override fun writeTimeoutMillis() = 1000
        override fun withWriteTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this
    }

    @Test
    fun `test AuthInterceptor injects bearer token when available`() {
        val tokenProvider = object : AuthTokenProvider {
            override suspend fun getAuthToken(): String = "test_jwt_bearer_token_xyz"
        }
        val interceptor = AuthInterceptor(tokenProvider)
        val initialRequest = Request.Builder().url("https://api.equalplus.ai/api/v1/calls").build()
        val chain = FakeInterceptorChain(initialRequest)

        interceptor.intercept(chain)

        assertNotNull(chain.capturedRequest)
        assertEquals("Bearer test_jwt_bearer_token_xyz", chain.capturedRequest?.header("Authorization"))
        assertEquals("application/json", chain.capturedRequest?.header("Accept"))
    }

    @Test
    fun `test AuthInterceptor does not inject Authorization header when token is null`() {
        val tokenProvider = object : AuthTokenProvider {
            override suspend fun getAuthToken(): String? = null
        }
        val interceptor = AuthInterceptor(tokenProvider)
        val initialRequest = Request.Builder().url("https://api.equalplus.ai/api/v1/calls").build()
        val chain = FakeInterceptorChain(initialRequest)

        interceptor.intercept(chain)

        assertNotNull(chain.capturedRequest)
        assertNull(chain.capturedRequest?.header("Authorization"))
        assertEquals("application/json", chain.capturedRequest?.header("Accept"))
    }

    @Test
    fun `test DTO entity mapping`() {
        val callDto = CallDto(
            id = "call_test_01",
            phoneNumber = "+18005551234",
            contactName = "Acme Corp",
            callType = "INCOMING",
            status = "COMPLETED",
            riskLevel = "LOW",
            riskScore = 0.12f,
            category = "DELIVERY",
            summary = "Package delivered",
            transcription = "Left at door",
            durationSeconds = 45L
        )

        val entity = callDto.toEntity()
        assertEquals("call_test_01", entity.id)
        assertEquals("+18005551234", entity.phoneNumber)
        assertEquals(CallType.INCOMING, entity.callType)
        assertEquals(CallStatus.COMPLETED, entity.status)
        assertEquals(RiskLevel.LOW, entity.riskLevel)
        assertEquals(0.12f, entity.riskScore)

        val roundTripDto = CallDto.fromEntity(entity)
        assertEquals(callDto.id, roundTripDto.id)
        assertEquals(callDto.phoneNumber, roundTripDto.phoneNumber)
        assertEquals(callDto.status, roundTripDto.status)
    }

    @Test
    fun `test CallRepository syncCallsFromBackend caches remote calls to Room`() = runTest {
        val fakeDao = FakeCallDao()
        val fakeApi = FakeApiService(
            remoteCalls = mutableListOf(
                CallDto("call_1", "+111111", status = "COMPLETED", riskLevel = "SAFE"),
                CallDto("call_2", "+222222", status = "BLOCKED", riskLevel = "CRITICAL")
            )
        )

        val repository = CallRepositoryImpl(fakeDao, fakeApi)
        val result = repository.syncCallsFromBackend()

        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrNull()?.size)
        // Verify cached in Room DAO
        assertEquals(2, fakeDao.calls.size)
        assertNotNull(fakeDao.calls["call_1"])
        assertNotNull(fakeDao.calls["call_2"])
        assertEquals(CallStatus.BLOCKED, fakeDao.calls["call_2"]?.status)
    }

    @Test
    fun `test CallRepository fetchAndCacheCallById caches to Room`() = runTest {
        val fakeDao = FakeCallDao()
        val fakeApi = FakeApiService(
            remoteCalls = mutableListOf(
                CallDto("call_99", "+999999", status = "SCREENING", riskLevel = "MEDIUM")
            )
        )

        val repository = CallRepositoryImpl(fakeDao, fakeApi)
        val result = repository.fetchAndCacheCallById("call_99")

        assertTrue(result.isSuccess)
        val fetched = result.getOrNull()
        assertNotNull(fetched)
        assertEquals("call_99", fetched?.id)
        assertEquals(RiskLevel.MEDIUM, fakeDao.calls["call_99"]?.riskLevel)
    }

    @Test
    fun `test ConversationRepository syncConversationsForCall caches to Room`() = runTest {
        val fakeDao = FakeConversationDao()
        val fakeApi = FakeApiService(
            remoteConversations = mutableListOf(
                ConversationDto(1L, "call_1", speaker = "CALLER", message = "Hello"),
                ConversationDto(2L, "call_1", speaker = "AI", message = "Hello! Who is calling?"),
                ConversationDto(3L, "call_2", speaker = "CALLER", message = "Other call")
            )
        )

        val repository = ConversationRepositoryImpl(fakeDao, fakeApi)
        val result = repository.syncConversationsForCall("call_1")

        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrNull()?.size)
        // Verify cached in Room DAO
        assertEquals(2, fakeDao.list.size)
        assertEquals(SpeakerType.CALLER, fakeDao.list[0].speaker)
        assertEquals(SpeakerType.ASSISTANT, fakeDao.list[1].speaker)
    }

    @Test
    fun `test ActionRepository syncActionsForCall caches to Room`() = runTest {
        val fakeDao = FakeActionDao()
        val fakeApi = FakeApiService(
            remoteActions = mutableListOf(
                ActionDto(1L, "call_1", actionType = "CREATE_REMINDER", status = "EXECUTED", description = "Reminder set"),
                ActionDto(2L, "call_1", actionType = "BLOCK_CALL", status = "EXECUTED", description = "Caller blocked")
            )
        )

        val repository = ActionRepositoryImpl(fakeDao, fakeApi)
        val result = repository.syncActionsForCall("call_1")

        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrNull()?.size)
        // Verify cached in Room DAO
        assertEquals(2, fakeDao.list.size)
        assertEquals(ActionType.CREATE_REMINDER, fakeDao.list[0].actionType)
        assertEquals(ActionType.BLOCK_CALL, fakeDao.list[1].actionType)
    }

    @Test
    fun `test CallRepository handles network error gracefully`() = runTest {
        val fakeDao = FakeCallDao()
        val fakeApi = FakeApiService(shouldFail = true)

        val repository = CallRepositoryImpl(fakeDao, fakeApi)
        val result = repository.syncCallsFromBackend()

        assertTrue(result.isFailure)
        assertEquals(0, fakeDao.calls.size)
    }
}
