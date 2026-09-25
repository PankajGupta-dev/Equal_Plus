package com.bridgefy.app.ui.viewmodel

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bridgefy.app.model.LocationSharePayload
import com.bridgefy.app.repository.SettingsRepository
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.flow.*
import java.util.concurrent.TimeUnit

/**
 * ViewModel for LocationShareBottomSheet.
 *
 * Fetches a one-shot GPS location on init (does NOT broadcast to mesh).
 * Uses FusedLocationProviderClient directly — no service binding required.
 */
class LocationShareViewModel(
    private val settingsRepository: SettingsRepository,
    private val appContext: Context
) : ViewModel() {

    sealed class LocationState {
        data object Acquiring : LocationState()
        data class Ready(val payload: LocationSharePayload) : LocationState()
        data object Failed : LocationState()
    }

    private val _locationState = MutableStateFlow<LocationState>(LocationState.Acquiring)
    val locationState: StateFlow<LocationState> = _locationState.asStateFlow()

    val isBroadcastingToMesh: StateFlow<Boolean> = settingsRepository
        .observeMeshLocationBroadcast()
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val cancellationTokenSource = CancellationTokenSource()

    init {
        fetchLocation()
    }

    @SuppressLint("MissingPermission")
    private fun fetchLocation() {
        val fusedClient = LocationServices.getFusedLocationProviderClient(appContext)

        val request = CurrentLocationRequest.Builder()
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .setMaxUpdateAgeMillis(TimeUnit.MINUTES.toMillis(2))
            .setDurationMillis(TimeUnit.SECONDS.toMillis(15))
            .build()

        fusedClient.getCurrentLocation(request, cancellationTokenSource.token)
            .addOnSuccessListener { location ->
                if (location != null) {
                    _locationState.value = LocationState.Ready(
                        LocationSharePayload(
                            lat = location.latitude,
                            lon = location.longitude,
                            accuracyMeters = location.accuracy,
                            altitudeMeters = if (location.hasAltitude()) location.altitude.toFloat() else null,
                            timestampEpochSec = location.time / 1000
                        )
                    )
                } else {
                    // getCurrentLocation returned null — try lastLocation as fallback
                    fetchLastKnown(fusedClient)
                }
            }
            .addOnFailureListener {
                // GPS radio failed — try lastLocation as fallback
                fetchLastKnown(fusedClient)
            }
    }

    @SuppressLint("MissingPermission")
    private fun fetchLastKnown(fusedClient: com.google.android.gms.location.FusedLocationProviderClient) {
        fusedClient.lastLocation
            .addOnSuccessListener { location ->
                _locationState.value = if (location != null) {
                    LocationState.Ready(
                        LocationSharePayload(
                            lat = location.latitude,
                            lon = location.longitude,
                            accuracyMeters = location.accuracy,
                            altitudeMeters = if (location.hasAltitude()) location.altitude.toFloat() else null,
                            timestampEpochSec = location.time / 1000
                        )
                    )
                } else {
                    LocationState.Failed
                }
            }
            .addOnFailureListener {
                _locationState.value = LocationState.Failed
            }
    }

    override fun onCleared() {
        cancellationTokenSource.cancel()
        super.onCleared()
    }
}
