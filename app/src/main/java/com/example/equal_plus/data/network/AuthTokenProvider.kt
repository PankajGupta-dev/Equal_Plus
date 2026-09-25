package com.example.equal_plus.data.network

interface AuthTokenProvider {
    suspend fun getAuthToken(): String?
}
