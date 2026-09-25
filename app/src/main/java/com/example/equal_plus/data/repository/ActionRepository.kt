package com.example.equal_plus.data.repository

import com.example.equal_plus.data.local.dao.ActionDao
import com.example.equal_plus.data.local.entity.ActionEntity
import com.example.equal_plus.data.model.ActionStatus
import com.example.equal_plus.data.model.ActionType
import com.example.equal_plus.data.network.ApiService
import com.example.equal_plus.data.network.model.ActionDto
import kotlinx.coroutines.flow.Flow

interface ActionRepository {
    fun getActionsForCall(callId: String): Flow<List<ActionEntity>>
    fun getActionsByStatus(status: ActionStatus): Flow<List<ActionEntity>>
    fun getPendingActions(): Flow<List<ActionEntity>>
    fun getActionsByType(actionType: ActionType): Flow<List<ActionEntity>>
    suspend fun insertAction(action: ActionEntity): Long
    suspend fun insertActions(actions: List<ActionEntity>): List<Long>
    suspend fun updateAction(action: ActionEntity): Int
    suspend fun deleteAction(action: ActionEntity): Int
    suspend fun deleteActionsForCall(callId: String): Int

    // Network-then-cache integration
    suspend fun syncActionsForCall(callId: String): Result<List<ActionEntity>> = Result.success(emptyList())
    suspend fun uploadAction(action: ActionEntity): Result<ActionEntity> = Result.success(action)
}

class ActionRepositoryImpl(
    private val actionDao: ActionDao,
    private val apiService: ApiService? = null
) : ActionRepository {

    override fun getActionsForCall(callId: String): Flow<List<ActionEntity>> =
        actionDao.getActionsForCall(callId)

    override fun getActionsByStatus(status: ActionStatus): Flow<List<ActionEntity>> =
        actionDao.getActionsByStatus(status)

    override fun getPendingActions(): Flow<List<ActionEntity>> =
        actionDao.getPendingActions()

    override fun getActionsByType(actionType: ActionType): Flow<List<ActionEntity>> =
        actionDao.getActionsByType(actionType)

    override suspend fun insertAction(action: ActionEntity): Long =
        actionDao.insertAction(action)

    override suspend fun insertActions(actions: List<ActionEntity>): List<Long> =
        actionDao.insertActions(actions)

    override suspend fun updateAction(action: ActionEntity): Int =
        actionDao.updateAction(action)

    override suspend fun deleteAction(action: ActionEntity): Int =
        actionDao.deleteAction(action)

    override suspend fun deleteActionsForCall(callId: String): Int =
        actionDao.deleteActionsForCall(callId)

    override suspend fun syncActionsForCall(callId: String): Result<List<ActionEntity>> {
        val service = apiService ?: return Result.failure(IllegalStateException("ApiService not configured"))
        return try {
            val response = service.getActionsForCall(callId)
            if (response.isSuccessful && response.body() != null) {
                val remoteEntities = response.body()!!.map { it.toEntity() }
                if (remoteEntities.isNotEmpty()) {
                    actionDao.insertActions(remoteEntities)
                }
                Result.success(remoteEntities)
            } else {
                Result.failure(Exception("HTTP error ${response.code()}: ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun uploadAction(action: ActionEntity): Result<ActionEntity> {
        val service = apiService ?: return Result.failure(IllegalStateException("ApiService not configured"))
        return try {
            val dto = ActionDto.fromEntity(action)
            val response = service.createAction(dto)
            if (response.isSuccessful && response.body() != null) {
                val savedEntity = response.body()!!.toEntity()
                actionDao.insertAction(savedEntity)
                Result.success(savedEntity)
            } else {
                Result.failure(Exception("HTTP error ${response.code()}: ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

