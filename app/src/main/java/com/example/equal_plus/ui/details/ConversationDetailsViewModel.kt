package com.example.equal_plus.ui.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.equal_plus.data.local.entity.ActionEntity
import com.example.equal_plus.data.local.entity.CallEntity
import com.example.equal_plus.data.local.entity.ConversationEntity
import com.example.equal_plus.data.model.ActionStatus
import com.example.equal_plus.data.model.ActionType
import com.example.equal_plus.data.model.CallStatus
import com.example.equal_plus.data.model.CallType
import com.example.equal_plus.data.model.RiskLevel
import com.example.equal_plus.data.model.SpeakerType
import com.example.equal_plus.data.repository.ActionRepository
import com.example.equal_plus.data.repository.CallRepository
import com.example.equal_plus.data.repository.ConversationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

data class ConversationDetailsUiState(
    val isLoading: Boolean = true,
    val call: CallEntity? = null,
    val conversations: List<ConversationEntity> = emptyList(),
    val actions: List<ActionEntity> = emptyList()
)

class ConversationDetailsViewModel(
    private val callId: String,
    private val callRepository: CallRepository,
    private val conversationRepository: ConversationRepository,
    private val actionRepository: ActionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ConversationDetailsUiState())
    val uiState: StateFlow<ConversationDetailsUiState> = _uiState.asStateFlow()

    init {
        observeDetails()
        refreshDetails()
    }

    fun refreshDetails() {
        viewModelScope.launch {
            conversationRepository.syncConversationsWithFallback(callId)
            actionRepository.syncActionsWithFallback(callId)
        }
    }

    private fun observeDetails() {
        combine(
            callRepository.getCallById(callId),
            conversationRepository.getConversationsForCall(callId),
            actionRepository.getActionsForCall(callId)
        ) { call, conversations, actions ->
            ConversationDetailsUiState(
                isLoading = false,
                call = call,
                conversations = conversations,
                actions = actions
            )
        }.onEach {
            _uiState.value = it
        }.launchIn(viewModelScope)
    }

    class Factory(
        private val callId: String,
        private val callRepository: CallRepository,
        private val conversationRepository: ConversationRepository,
        private val actionRepository: ActionRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ConversationDetailsViewModel::class.java)) {
                return ConversationDetailsViewModel(
                    callId,
                    callRepository,
                    conversationRepository,
                    actionRepository
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
