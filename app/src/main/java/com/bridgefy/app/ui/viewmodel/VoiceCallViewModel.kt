package com.bridgefy.app.ui.viewmodel

import android.graphics.Bitmap
import androidx.camera.core.Preview
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import com.bridgefy.app.call.CallState
import com.bridgefy.app.call.VoiceCallManager
import kotlinx.coroutines.flow.StateFlow

class VoiceCallViewModel(
    private val voiceCallManager: VoiceCallManager
) : ViewModel() {

    val callState: StateFlow<CallState> = voiceCallManager.callState
    val peerId: StateFlow<String?> = voiceCallManager.peerId
    val peerName: StateFlow<String> = voiceCallManager.peerName
    val callDuration: StateFlow<Long> = voiceCallManager.callDuration
    val isMuted: StateFlow<Boolean> = voiceCallManager.isMuted
    val isSpeaker: StateFlow<Boolean> = voiceCallManager.isSpeakerOn

    val isVideoCall: StateFlow<Boolean> = voiceCallManager.isVideoCall
    val remoteVideoFrame: StateFlow<Bitmap?> = voiceCallManager.remoteVideoFrame
    val isLocalVideoEnabled: StateFlow<Boolean> = voiceCallManager.isLocalVideoEnabled
    val isFrontCamera: StateFlow<Boolean> = voiceCallManager.isFrontCamera

    fun acceptCall() {
        voiceCallManager.acceptCall()
    }

    fun rejectCall() {
        voiceCallManager.rejectCall()
    }

    fun endCall() {
        voiceCallManager.endCall()
    }

    fun toggleMute() {
        voiceCallManager.toggleMute()
    }

    fun toggleSpeaker() {
        voiceCallManager.toggleSpeaker()
    }

    fun toggleLocalVideo() {
        voiceCallManager.toggleLocalVideo()
    }

    fun toggleCameraFacing() {
        voiceCallManager.toggleCameraFacing()
    }

    fun startCameraCapture(lifecycleOwner: LifecycleOwner, onPreviewReady: (Preview) -> Unit) {
        voiceCallManager.startCameraCapture(lifecycleOwner, onPreviewReady)
    }
}
