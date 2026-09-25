package com.example.equal_plus.data.repository

import com.example.equal_plus.data.local.dao.ActionDao
import com.example.equal_plus.data.local.entity.ActionEntity
import com.example.equal_plus.data.model.ActionStatus
import com.example.equal_plus.data.model.ActionType
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
}

class ActionRepositoryImpl(
    private val actionDao: ActionDao
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
}
