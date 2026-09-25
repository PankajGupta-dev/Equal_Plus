package com.example.equal_plus.ui.livecall

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class LiveAiCallViewModel : ViewModel() {

    private val _liveCallState = MutableStateFlow(LiveCallState())
    val liveCallState: StateFlow<LiveCallState> = _liveCallState.asStateFlow()

    fun endCall() {
        _liveCallState.value = _liveCallState.value.copy(
            status = LiveCallStatus.ENDED,
            aiStatusText = "Call ended by user. Auto-navigating to conversation summary..."
        )
    }

    fun takeOverCall() {
        _liveCallState.value = _liveCallState.value.copy(
            status = LiveCallStatus.CONNECTED,
            aiStatusText = "User took over the audio call. Live transcription continuing in background."
        )
    }

    fun triggerEndCallForNavigation() {
        _liveCallState.value = _liveCallState.value.copy(
            status = LiveCallStatus.ENDED
        )
    }
}
