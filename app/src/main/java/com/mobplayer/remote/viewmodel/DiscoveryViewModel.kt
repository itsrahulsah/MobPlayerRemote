package com.mobplayer.remote.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mobplayer.remote.model.TvDevice
import com.mobplayer.remote.repository.DeviceDiscoveryRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DiscoveryUiState(
    val isScanning: Boolean = true,
    val devices: List<TvDevice> = emptyList(),
    val errorMessage: String? = null
)

class DiscoveryViewModel(
    private val discoveryRepository: DeviceDiscoveryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DiscoveryUiState())
    val uiState: StateFlow<DiscoveryUiState> = _uiState.asStateFlow()

    private var scanJob: Job? = null

    init {
        startDiscovery()
    }

    fun refresh() {
        startDiscovery()
    }

    private fun startDiscovery() {
        scanJob?.cancel()
        _uiState.value = _uiState.value.copy(isScanning = true, errorMessage = null)

        scanJob = viewModelScope.launch {
            // Stop progress spinner after 3 seconds discovery window
            launch {
                delay(3000L)
                _uiState.value = _uiState.value.copy(isScanning = false)
            }

            try {
                discoveryRepository.discoverDevices().collect { list ->
                    _uiState.value = _uiState.value.copy(devices = list)
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isScanning = false,
                    errorMessage = e.localizedMessage ?: "Failed to scan devices"
                )
            }
        }
    }
}
