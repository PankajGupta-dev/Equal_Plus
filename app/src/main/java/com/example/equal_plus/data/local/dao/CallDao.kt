package com.example.equal_plus.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.equal_plus.data.local.entity.CallEntity
import com.example.equal_plus.data.model.CallStatus
import com.example.equal_plus.data.model.RiskLevel
import kotlinx.coroutines.flow.Flow

@Dao
interface CallDao {

    @Query("SELECT * FROM calls ORDER BY createdAt DESC")
    fun getAllCalls(): Flow<List<CallEntity>>

    @Query("SELECT * FROM calls WHERE id = :id LIMIT 1")
    fun getCallById(id: String): Flow<CallEntity?>

    @Query("SELECT * FROM calls WHERE id = :id LIMIT 1")
    suspend fun getCallByIdDirect(id: String): CallEntity?

    @Query("SELECT * FROM calls WHERE phoneNumber = :phoneNumber ORDER BY createdAt DESC")
    fun getCallsForNumber(phoneNumber: String): Flow<List<CallEntity>>

    @Query("SELECT * FROM calls WHERE status = :status ORDER BY createdAt DESC")
    fun getCallsByStatus(status: CallStatus): Flow<List<CallEntity>>

    @Query("SELECT * FROM calls WHERE riskLevel = :riskLevel ORDER BY createdAt DESC")
    fun getCallsByRiskLevel(riskLevel: RiskLevel): Flow<List<CallEntity>>

    @Query("SELECT * FROM calls WHERE isSpam = 1 ORDER BY createdAt DESC")
    fun getSpamCalls(): Flow<List<CallEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCall(call: CallEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCalls(calls: List<CallEntity>): List<Long>

    @Update
    suspend fun updateCall(call: CallEntity): Int

    @Delete
    suspend fun deleteCall(call: CallEntity): Int

    @Query("DELETE FROM calls WHERE id = :id")
    suspend fun deleteCallById(id: String): Int

    @Query("DELETE FROM calls")
    suspend fun clearAllCalls(): Int
}
