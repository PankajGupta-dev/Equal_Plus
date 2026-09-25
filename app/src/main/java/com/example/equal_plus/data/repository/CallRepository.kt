package com.example.equal_plus.data.repository

import com.example.equal_plus.data.local.dao.CallDao
import com.example.equal_plus.data.local.entity.CallEntity
import com.example.equal_plus.data.model.CallStatus
import com.example.equal_plus.data.model.RiskLevel
import kotlinx.coroutines.flow.Flow

interface CallRepository {
    fun getAllCalls(): Flow<List<CallEntity>>
    fun getCallById(id: String): Flow<CallEntity?>
    suspend fun getCallByIdDirect(id: String): CallEntity?
    fun getCallsForNumber(phoneNumber: String): Flow<List<CallEntity>>
    fun getCallsByStatus(status: CallStatus): Flow<List<CallEntity>>
    fun getCallsByRiskLevel(riskLevel: RiskLevel): Flow<List<CallEntity>>
    fun getSpamCalls(): Flow<List<CallEntity>>
    suspend fun insertCall(call: CallEntity): Long
    suspend fun insertCalls(calls: List<CallEntity>): List<Long>
    suspend fun updateCall(call: CallEntity): Int
    suspend fun deleteCall(call: CallEntity): Int
    suspend fun deleteCallById(id: String): Int
    suspend fun clearAllCalls(): Int
}

class CallRepositoryImpl(
    private val callDao: CallDao
) : CallRepository {

    override fun getAllCalls(): Flow<List<CallEntity>> = callDao.getAllCalls()

    override fun getCallById(id: String): Flow<CallEntity?> = callDao.getCallById(id)

    override suspend fun getCallByIdDirect(id: String): CallEntity? = callDao.getCallByIdDirect(id)

    override fun getCallsForNumber(phoneNumber: String): Flow<List<CallEntity>> =
        callDao.getCallsForNumber(phoneNumber)

    override fun getCallsByStatus(status: CallStatus): Flow<List<CallEntity>> =
        callDao.getCallsByStatus(status)

    override fun getCallsByRiskLevel(riskLevel: RiskLevel): Flow<List<CallEntity>> =
        callDao.getCallsByRiskLevel(riskLevel)

    override fun getSpamCalls(): Flow<List<CallEntity>> = callDao.getSpamCalls()

    override suspend fun insertCall(call: CallEntity): Long = callDao.insertCall(call)

    override suspend fun insertCalls(calls: List<CallEntity>): List<Long> = callDao.insertCalls(calls)

    override suspend fun updateCall(call: CallEntity): Int = callDao.updateCall(call)

    override suspend fun deleteCall(call: CallEntity): Int = callDao.deleteCall(call)

    override suspend fun deleteCallById(id: String): Int = callDao.deleteCallById(id)

    override suspend fun clearAllCalls(): Int = callDao.clearAllCalls()
}
