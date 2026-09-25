package com.bridgefy.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.bridgefy.app.BridgeFyApplication
import com.bridgefy.app.mesh.MeshManager

/**
 * Factory for creating ViewModels with the MeshManager dependency.
 * Provides manual dependency injection without Hilt/Dagger.
 */
class ViewModelFactory(
    private val app: BridgeFyApplication
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(ChatViewModel::class.java) -> {
                ChatViewModel(app.meshManager, app.deviceId) as T
            }
            modelClass.isAssignableFrom(PeersViewModel::class.java) -> {
                PeersViewModel(app.meshManager) as T
            }
            modelClass.isAssignableFrom(MeshMapViewModel::class.java) -> {
                MeshMapViewModel(app.settingsRepository, app.applicationContext) as T
            }
            modelClass.isAssignableFrom(LocationShareViewModel::class.java) -> {
                LocationShareViewModel(app.settingsRepository, app.applicationContext) as T
            }
            modelClass.isAssignableFrom(LocationPrivacyViewModel::class.java) -> {
                LocationPrivacyViewModel(app.settingsRepository) as T
            }
            modelClass.isAssignableFrom(VoiceCallViewModel::class.java) -> {
                VoiceCallViewModel(app.voiceCallManager) as T
            }
            modelClass.isAssignableFrom(CallHistoryViewModel::class.java) -> {
                CallHistoryViewModel() as T
            }
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
