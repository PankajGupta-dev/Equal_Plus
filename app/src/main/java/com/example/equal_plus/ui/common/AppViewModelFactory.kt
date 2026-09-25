package com.example.equal_plus.ui.common

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.equal_plus.data.local.AppDatabase
import com.example.equal_plus.data.local.PolicyDataStore
import com.example.equal_plus.data.repository.ActionRepositoryImpl
import com.example.equal_plus.data.repository.CallRepositoryImpl
import com.example.equal_plus.data.repository.ConversationRepositoryImpl
import com.example.equal_plus.data.repository.PolicyRepositoryImpl
import com.example.equal_plus.ui.history.CallHistoryViewModel
import com.example.equal_plus.ui.home.HomeViewModel
import com.example.equal_plus.ui.policy.AiPolicyViewModel

class AppViewModelFactory(
    private val context: Context
) : ViewModelProvider.Factory {

    private val database by lazy { AppDatabase.getInstance(context) }
    private val policyDataStore by lazy { PolicyDataStore(context) }

    private val callRepository by lazy { CallRepositoryImpl(database.callDao()) }
    private val conversationRepository by lazy { ConversationRepositoryImpl(database.conversationDao()) }
    private val actionRepository by lazy { ActionRepositoryImpl(database.actionDao()) }
    private val policyRepository by lazy { PolicyRepositoryImpl(policyDataStore) }

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
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
