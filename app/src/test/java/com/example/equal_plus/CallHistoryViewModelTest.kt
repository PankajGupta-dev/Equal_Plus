package com.example.equal_plus

import com.example.equal_plus.data.local.entity.CallEntity
import com.example.equal_plus.data.model.CallStatus
import com.example.equal_plus.data.model.CallType
import com.example.equal_plus.data.model.RiskLevel
import com.example.equal_plus.data.repository.CallRepository
import com.example.equal_plus.ui.history.CallFilter
import com.example.equal_plus.ui.history.CallHistoryViewModel
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
class CallHistoryViewModelTest {

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
    fun testCallHistoryClientSideFiltering() = runTest {
        val fakeRepo = FakeCallRepo()
        val testCalls = listOf(
            CallEntity(
                id = "call1",
                phoneNumber = "+111",
                status = CallStatus.COMPLETED,
                riskLevel = RiskLevel.SAFE,
                callType = CallType.INCOMING,
                isSpam = false
            ),
            CallEntity(
                id = "call2",
                phoneNumber = "+222",
                status = CallStatus.BLOCKED,
                riskLevel = RiskLevel.HIGH,
                callType = CallType.INCOMING,
                isSpam = true
            ),
            CallEntity(
                id = "call3",
                phoneNumber = "+333",
                status = CallStatus.MISSED,
                riskLevel = RiskLevel.UNKNOWN,
                callType = CallType.MISSED,
                isSpam = false
            ),
            CallEntity(
                id = "call4",
                phoneNumber = "+444",
                status = CallStatus.SCREENING,
                riskLevel = RiskLevel.MEDIUM,
                callType = CallType.INCOMING,
                isSpam = false
            )
        )
        fakeRepo.callsFlow.value = testCalls

        val viewModel = CallHistoryViewModel(fakeRepo)

        // 1. ALL Filter
        assertEquals(4, viewModel.uiState.value.filteredCalls.size)

        // 2. RESOLVED Filter
        viewModel.setFilter(CallFilter.RESOLVED)
        assertEquals(1, viewModel.uiState.value.filteredCalls.size)
        assertEquals("call1", viewModel.uiState.value.filteredCalls[0].id)

        // 3. BLOCKED Filter
        viewModel.setFilter(CallFilter.BLOCKED)
        assertEquals(1, viewModel.uiState.value.filteredCalls.size)
        assertEquals("call2", viewModel.uiState.value.filteredCalls[0].id)

        // 4. ESCALATED / Needs Review Filter (HIGH or MEDIUM risk)
        viewModel.setFilter(CallFilter.ESCALATED)
        assertEquals(2, viewModel.uiState.value.filteredCalls.size) // call2 (HIGH) and call4 (MEDIUM)

        // 5. MISSED Filter
        viewModel.setFilter(CallFilter.MISSED)
        assertEquals(1, viewModel.uiState.value.filteredCalls.size)
        assertEquals("call3", viewModel.uiState.value.filteredCalls[0].id)
    }
}
