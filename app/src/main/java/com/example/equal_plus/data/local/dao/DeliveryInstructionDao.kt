package com.example.equal_plus.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.equal_plus.data.local.entity.DeliveryInstructionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DeliveryInstructionDao {

    @Query("SELECT * FROM delivery_instructions WHERE callId = :callId ORDER BY createdAt DESC")
    fun getInstructionsForCall(callId: String): Flow<List<DeliveryInstructionEntity>>

    @Query("SELECT * FROM delivery_instructions ORDER BY createdAt DESC")
    fun getAllInstructions(): Flow<List<DeliveryInstructionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInstruction(instruction: DeliveryInstructionEntity): Long

    @Query("DELETE FROM delivery_instructions WHERE callId = :callId")
    suspend fun deleteInstructionsForCall(callId: String): Int
}
