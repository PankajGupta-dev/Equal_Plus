package com.example.equal_plus.data.network

import com.example.equal_plus.data.network.model.ActionDto
import com.example.equal_plus.data.network.model.CallDto
import com.example.equal_plus.data.network.model.ConversationDto
import com.example.equal_plus.data.network.model.UserProfileDto
import com.example.equal_plus.data.network.model.UserPolicySyncDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {

    // Calls Endpoints (FastAPI calls.py)
    @GET("api/v1/calls")
    suspend fun getCalls(
        @Query("limit") limit: Int = 100,
        @Query("offset") offset: Int = 0
    ): Response<List<CallDto>>

    @GET("api/v1/calls/{call_id}")
    suspend fun getCallById(
        @Path("call_id") callId: String
    ): Response<CallDto>

    @POST("api/v1/calls")
    suspend fun createCall(
        @Body call: CallDto
    ): Response<CallDto>

    @PUT("api/v1/calls/{call_id}")
    suspend fun updateCall(
        @Path("call_id") callId: String,
        @Body call: CallDto
    ): Response<CallDto>

    // Conversations Endpoints (FastAPI conversations.py)
    @GET("api/v1/conversations/{call_id}")
    suspend fun getConversationsForCall(
        @Path("call_id") callId: String
    ): Response<List<ConversationDto>>

    @POST("api/v1/conversations")
    suspend fun createConversationTurn(
        @Body conversation: ConversationDto
    ): Response<ConversationDto>

    // Actions Endpoints
    @GET("api/v1/actions/{call_id}")
    suspend fun getActionsForCall(
        @Path("call_id") callId: String
    ): Response<List<ActionDto>>

    @POST("api/v1/actions")
    suspend fun createAction(
        @Body action: ActionDto
    ): Response<ActionDto>

    // Users & Policy Endpoints (FastAPI users.py)
    @GET("api/v1/users/me")
    suspend fun getUserProfile(): Response<UserProfileDto>

    @PUT("api/v1/users/policy")
    suspend fun syncPolicySettings(
        @Body policySync: UserPolicySyncDto
    ): Response<UserPolicySyncDto>
}
