package com.example.equal_plus.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.equal_plus.data.local.entity.ActionEntity
import com.example.equal_plus.data.model.ActionStatus
import com.example.equal_plus.data.model.ActionType
import kotlinx.coroutines.flow.Flow

@Dao
interface ActionDao {

    @Query("SELECT * FROM actions WHERE callId = :callId ORDER BY timestamp ASC")
    fun getActionsForCall(callId: String): Flow<List<ActionEntity>>

    @Query("SELECT * FROM actions WHERE status = :status ORDER BY timestamp ASC")
    fun getActionsByStatus(status: ActionStatus): Flow<List<ActionEntity>>

    @Query("SELECT * FROM actions WHERE status = 'PENDING' ORDER BY timestamp ASC")
    fun getPendingActions(): Flow<List<ActionEntity>>

    @Query("SELECT * FROM actions WHERE actionType = :actionType ORDER BY timestamp DESC")
    fun getActionsByType(actionType: ActionType): Flow<List<ActionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAction(action: ActionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertActions(actions: List<ActionEntity>): List<Long>

    @Update
    suspend fun updateAction(action: ActionEntity): Int

    @Delete
    suspend fun deleteAction(action: ActionEntity): Int

    @Query("DELETE FROM actions WHERE callId = :callId")
    suspend fun deleteActionsForCall(callId: String): Int
}
