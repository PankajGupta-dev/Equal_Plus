package com.example.equal_plus.data.repository

import com.example.equal_plus.data.local.dao.ConversationDao
import com.example.equal_plus.data.local.entity.ConversationEntity
import com.example.equal_plus.data.network.ApiService
import com.example.equal_plus.data.network.model.ConversationDto
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

    // Network-then-cache integration
    suspend fun syncConversationsForCall(callId: String): Result<List<ConversationEntity>> = Result.success(emptyList())
    suspend fun uploadConversationTurn(conversation: ConversationEntity): Result<ConversationEntity> = Result.success(conversation)
    suspend fun syncConversationsWithFallback(callId: String): com.example.equal_plus.data.model.Resource<List<ConversationEntity>> =
        com.example.equal_plus.data.model.Resource.Success(emptyList())
}

class ConversationRepositoryImpl(
    private val conversationDao: ConversationDao,
    private val apiService: ApiService? = null
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

    override suspend fun syncConversationsForCall(callId: String): Result<List<ConversationEntity>> {
        val service = apiService ?: return Result.failure(IllegalStateException("ApiService not configured"))
        return try {
            val response = service.getConversationsForCall(callId)
            if (response.isSuccessful && response.body() != null) {
                val remoteEntities = response.body()!!.map { it.toEntity() }
                if (remoteEntities.isNotEmpty()) {
                    conversationDao.insertConversations(remoteEntities)
                }
                Result.success(remoteEntities)
            } else {
                Result.failure(Exception("HTTP error ${response.code()}: ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun uploadConversationTurn(conversation: ConversationEntity): Result<ConversationEntity> {
        val service = apiService ?: return Result.failure(IllegalStateException("ApiService not configured"))
        return try {
            val dto = ConversationDto.fromEntity(conversation)
            val response = service.createConversationTurn(dto)
            if (response.isSuccessful && response.body() != null) {
                val savedEntity = response.body()!!.toEntity()
                conversationDao.insertConversation(savedEntity)
                Result.success(savedEntity)
            } else {
                Result.failure(Exception("HTTP error ${response.code()}: ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun syncConversationsWithFallback(callId: String): com.example.equal_plus.data.model.Resource<List<ConversationEntity>> {
        val service = apiService
        if (service != null) {
            try {
                val response = service.getConversationsForCall(callId)
                if (response.isSuccessful && response.body() != null) {
                    val remoteEntities = response.body()!!.map { it.toEntity() }
                    if (remoteEntities.isNotEmpty()) {
                        conversationDao.insertConversations(remoteEntities)
                    }
                    return com.example.equal_plus.data.model.Resource.Success(remoteEntities)
                }
            } catch (e: Exception) {
                // Offline / network failure: fallback to Room cache
            }
        }
        val cached = conversationDao.getConversationsForCallDirect(callId)
        return com.example.equal_plus.data.model.Resource.Success(cached)
    }
}

