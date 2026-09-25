package com.bridgefy.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bridgefy.app.repository.SettingsRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * ViewModel for LocationPrivacySettingsScreen.
 *
 * Binds to SettingsRepository toggle flows for reactive UI.
 */
class LocationPrivacyViewModel(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val isBroadcasting: StateFlow<Boolean> = settingsRepository
        .observeMeshLocationBroadcast()
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val isLocalMapEnabled: StateFlow<Boolean> = settingsRepository
        .observeLocalMapLocation()
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    fun setBroadcasting(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setMeshLocationBroadcast(enabled)
        }
    }

    fun setLocalMapLocation(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setLocalMapLocation(enabled)
        }
    }
}
