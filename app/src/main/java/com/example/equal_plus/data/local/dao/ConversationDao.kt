package com.example.equal_plus.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.equal_plus.data.local.entity.ConversationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {

    @Query("SELECT * FROM conversations WHERE callId = :callId ORDER BY timestamp ASC")
    fun getConversationsForCall(callId: String): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE callId = :callId ORDER BY timestamp ASC")
    suspend fun getConversationsForCallDirect(callId: String): List<ConversationEntity>

    @Query("SELECT * FROM conversations WHERE isFlagged = 1 ORDER BY timestamp DESC")
    fun getFlaggedConversations(): Flow<List<ConversationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversation(conversation: ConversationEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversations(conversations: List<ConversationEntity>): List<Long>

    @Update
    suspend fun updateConversation(conversation: ConversationEntity): Int

    @Delete
    suspend fun deleteConversation(conversation: ConversationEntity): Int

    @Query("DELETE FROM conversations WHERE callId = :callId")
    suspend fun deleteConversationsForCall(callId: String): Int
}
