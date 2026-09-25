package com.example.equal_plus.ui.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

sealed interface UiState<out T> {
    data object Idle : UiState<Nothing>
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String, val throwable: Throwable? = null) : UiState<Nothing>
}

sealed interface UiEvent {
    data class ShowToast(val message: String) : UiEvent
    data class ShowSnackbar(val message: String, val actionLabel: String? = null) : UiEvent
    data class NavigateTo(val destinationId: Int) : UiEvent
}

abstract class BaseViewModel<S, E : Any>(initialState: S) : ViewModel() {

    private val _uiState = MutableStateFlow(initialState)
    val uiState: StateFlow<S> = _uiState.asStateFlow()

    private val _eventChannel = Channel<E>(Channel.BUFFERED)
    val events = _eventChannel.receiveAsFlow()

    protected fun updateState(reducer: S.() -> S) {
        _uiState.value = _uiState.value.reducer()
    }

    protected fun sendEvent(event: E) {
        viewModelScope.launch {
            _eventChannel.send(event)
        }
    }
}
