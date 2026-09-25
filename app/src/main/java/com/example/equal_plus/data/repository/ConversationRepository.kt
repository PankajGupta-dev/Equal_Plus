package com.example.equal_plus.data.repository

import com.example.equal_plus.data.local.dao.ConversationDao
import com.example.equal_plus.data.local.entity.ConversationEntity
import kotlinx.coroutines.flow.Flow

interface ConversationRepository {
    fun getConversationsForCall(callId: String): Flow<List<ConversationEntity>>
    suspend fun getConversationsForCallDirect(callId: String): List<ConversationEntity>
    fun getFlaggedConversations(): Flow<List<ConversationEntity>>
    suspend fun insertConversation(conversation: ConversationEntity): Long
    suspend fun insertConversations(conversations: List<ConversationEntity>): List<Long>
    suspend fun updateConversation(conversation: ConversationEntity): Int
    suspend fun deleteConversation(conversation: ConversationEntity): Int
    suspend fun deleteConversationsForCall(callId: String): Int
}

class ConversationRepositoryImpl(
    private val conversationDao: ConversationDao
) : ConversationRepository {

    override fun getConversationsForCall(callId: String): Flow<List<ConversationEntity>> =
        conversationDao.getConversationsForCall(callId)

    override suspend fun getConversationsForCallDirect(callId: String): List<ConversationEntity> =
        conversationDao.getConversationsForCallDirect(callId)

    override fun getFlaggedConversations(): Flow<List<ConversationEntity>> =
        conversationDao.getFlaggedConversations()

    override suspend fun insertConversation(conversation: ConversationEntity): Long =
        conversationDao.insertConversation(conversation)

    override suspend fun insertConversations(conversations: List<ConversationEntity>): List<Long> =
        conversationDao.insertConversations(conversations)

    override suspend fun updateConversation(conversation: ConversationEntity): Int =
        conversationDao.updateConversation(conversation)

    override suspend fun deleteConversation(conversation: ConversationEntity): Int =
        conversationDao.deleteConversation(conversation)

    override suspend fun deleteConversationsForCall(callId: String): Int =
        conversationDao.deleteConversationsForCall(callId)
}
