package com.example.equal_plus

import com.example.equal_plus.data.local.entity.CallEntity
import com.example.equal_plus.data.model.CallStatus
import com.example.equal_plus.data.model.RiskLevel
import com.example.equal_plus.data.repository.CallRepository
import com.example.equal_plus.ui.home.HomeViewModel
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
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private class FakeCallRepo : CallRepository {
        val callsFlow = MutableStateFlow<List<CallEntity>>(emptyList())
        override fun getAllCalls(): Flow<List<CallEntity>> = callsFlow
        override fun getCallById(id: String): Flow<CallEntity?> = MutableStateFlow(null)
        override suspend fun getCallByIdDirect(id: String): CallEntity? = null
        override fun getCallsForNumber(phoneNumber: String): Flow<List<CallEntity>> = callsFlow
        override fun getCallsByStatus(status: CallStatus): Flow<List<CallEntity>> = callsFlow
        override fun getCallsByRiskLevel(riskLevel: RiskLevel): Flow<List<CallEntity>> = callsFlow
        override fun getSpamCalls(): Flow<List<CallEntity>> = callsFlow
        override suspend fun insertCall(call: CallEntity): Long = 1L
        override suspend fun insertCalls(calls: List<CallEntity>): List<Long> {
            callsFlow.value = calls
            return calls.map { 1L }
        }
        override suspend fun updateCall(call: CallEntity): Int = 1
        override suspend fun deleteCall(call: CallEntity): Int = 1
        override suspend fun deleteCallById(id: String): Int = 1
        override suspend fun clearAllCalls(): Int = 1
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
    fun testHomeViewModelCountsCalculation() = runTest {
        val fakeRepo = FakeCallRepo()
        val testCalls = listOf(
            CallEntity(
                id = "call1",
                phoneNumber = "+12345",
                status = CallStatus.BLOCKED,
                riskLevel = RiskLevel.HIGH,
                isSpam = true
            ),
            CallEntity(
                id = "call2",
                phoneNumber = "+67890",
                status = CallStatus.COMPLETED,
                riskLevel = RiskLevel.SAFE,
                isSpam = false
            ),
            CallEntity(
                id = "call3",
                phoneNumber = "+11223",
                status = CallStatus.COMPLETED,
                riskLevel = RiskLevel.MEDIUM,
                isSpam = true
            )
        )
        fakeRepo.callsFlow.value = testCalls

        val viewModel = HomeViewModel(fakeRepo)

        val state = viewModel.uiState.value
        assertEquals(3, state.handledCount)
        assertEquals(3, state.autoResolvedCount)
        assertEquals(2, state.blockedCount)
        assertEquals(1, state.needingAttentionCount)
        assertEquals(3, state.recentCalls.size)
    }
}
