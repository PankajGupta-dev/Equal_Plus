package com.example.equal_plus.ui.common

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.auth0.android.Auth0
import com.example.equal_plus.R
import com.example.equal_plus.data.auth.AuthRepository
import com.example.equal_plus.data.local.AppDatabase
import com.example.equal_plus.data.local.AuthDataStore
import com.example.equal_plus.data.local.PolicyDataStore
import com.example.equal_plus.data.network.NetworkClient
import com.example.equal_plus.data.repository.ActionRepositoryImpl
import com.example.equal_plus.data.repository.CallRepositoryImpl
import com.example.equal_plus.data.repository.ConversationRepositoryImpl
import com.example.equal_plus.data.repository.PolicyRepositoryImpl
import com.example.equal_plus.ui.auth.OtpEntryViewModel
import com.example.equal_plus.ui.auth.PhoneVerificationViewModel
import com.example.equal_plus.ui.history.CallHistoryViewModel
import com.example.equal_plus.ui.home.HomeViewModel
import com.example.equal_plus.ui.policy.AiPolicyViewModel

class AppViewModelFactory(
    private val context: Context
) : ViewModelProvider.Factory {

    private val database by lazy { AppDatabase.getInstance(context) }
    private val policyDataStore by lazy { PolicyDataStore(context) }
    private val authDataStore by lazy { AuthDataStore(context) }
    private val apiService by lazy { NetworkClient.createApiService(tokenProvider = policyDataStore) }

    private val callRepository by lazy { CallRepositoryImpl(database.callDao(), apiService) }
    private val conversationRepository by lazy { ConversationRepositoryImpl(database.conversationDao(), apiService) }
    private val actionRepository by lazy { ActionRepositoryImpl(database.actionDao(), apiService) }
    private val policyRepository by lazy { PolicyRepositoryImpl(policyDataStore) }

    // Auth0 — credentials read from res/values/auth0.xml
    private val auth0 by lazy {
        Auth0(
            context.getString(R.string.com_auth0_client_id),
            context.getString(R.string.com_auth0_domain)
        )
    }
    private val authRepository by lazy { AuthRepository(auth0, authDataStore) }

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(HomeViewModel::class.java) -> {
                HomeViewModel(callRepository) as T
            }
            modelClass.isAssignableFrom(CallHistoryViewModel::class.java) -> {
                CallHistoryViewModel(callRepository) as T
            }
            modelClass.isAssignableFrom(AiPolicyViewModel::class.java) -> {
                AiPolicyViewModel(policyRepository) as T
            }
            modelClass.isAssignableFrom(com.example.equal_plus.ui.livecall.LiveAiCallViewModel::class.java) -> {
                com.example.equal_plus.ui.livecall.LiveAiCallViewModel() as T
            }
            modelClass.isAssignableFrom(PhoneVerificationViewModel::class.java) -> {
                PhoneVerificationViewModel(authRepository) as T
            }
            modelClass.isAssignableFrom(OtpEntryViewModel::class.java) -> {
                OtpEntryViewModel(authRepository) as T
            }
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
