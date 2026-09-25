package com.example.equal_plus.data.repository

import com.example.equal_plus.data.local.dao.CallDao
import com.example.equal_plus.data.local.entity.CallEntity
import com.example.equal_plus.data.model.CallStatus
import com.example.equal_plus.data.model.RiskLevel
import com.example.equal_plus.data.network.ApiService
import com.example.equal_plus.data.network.model.CallDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull

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

    // Network-then-cache integration
    suspend fun syncCallsFromBackend(): Result<List<CallEntity>> = Result.success(emptyList())
    suspend fun fetchAndCacheCallById(id: String): Result<CallEntity?> = Result.success(null)
    suspend fun uploadCall(call: CallEntity): Result<CallEntity> = Result.success(call)
    suspend fun syncCallsWithFallback(): com.example.equal_plus.data.model.Resource<List<CallEntity>> =
        com.example.equal_plus.data.model.Resource.Success(emptyList())
}

class CallRepositoryImpl(
    private val callDao: CallDao,
    private val apiService: ApiService? = null
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

    override suspend fun syncCallsFromBackend(): Result<List<CallEntity>> {
        val service = apiService ?: return Result.failure(IllegalStateException("ApiService not configured"))
        return try {
            val response = service.getCalls()
            if (response.isSuccessful && response.body() != null) {
                val remoteCalls = response.body()!!.map { it.toEntity() }
                if (remoteCalls.isNotEmpty()) {
                    callDao.insertCalls(remoteCalls)
                }
                Result.success(remoteCalls)
            } else {
                Result.failure(Exception("HTTP error ${response.code()}: ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun fetchAndCacheCallById(id: String): Result<CallEntity?> {
        val service = apiService ?: return Result.failure(IllegalStateException("ApiService not configured"))
        return try {
            val response = service.getCallById(id)
            if (response.isSuccessful && response.body() != null) {
                val remoteCall = response.body()!!.toEntity()
                callDao.insertCall(remoteCall)
                Result.success(remoteCall)
            } else {
                Result.failure(Exception("HTTP error ${response.code()}: ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun uploadCall(call: CallEntity): Result<CallEntity> {
        val service = apiService ?: return Result.failure(IllegalStateException("ApiService not configured"))
        return try {
            val dto = CallDto.fromEntity(call)
            val response = service.createCall(dto)
            if (response.isSuccessful && response.body() != null) {
                val savedEntity = response.body()!!.toEntity()
                callDao.insertCall(savedEntity)
                Result.success(savedEntity)
            } else {
                Result.failure(Exception("HTTP error ${response.code()}: ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun syncCallsWithFallback(): com.example.equal_plus.data.model.Resource<List<CallEntity>> {
        val service = apiService
        if (service != null) {
            try {
                val response = service.getCalls()
                if (response.isSuccessful && response.body() != null) {
                    val remoteCalls = response.body()!!.map { it.toEntity() }
                    if (remoteCalls.isNotEmpty()) {
                        callDao.insertCalls(remoteCalls)
                    }
                    return com.example.equal_plus.data.model.Resource.Success(remoteCalls)
                }
            } catch (e: Exception) {
                // Offline / network failure: fallback to Room cache
            }
        }
        val cachedCalls = callDao.getAllCalls()
        val cached = cachedCalls.firstOrNull() ?: emptyList()
        return com.example.equal_plus.data.model.Resource.Success(cached)
    }
}

