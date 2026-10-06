package com.mobplayer.remote.viewmodel

import android.util.Log
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
        Log.i(TAG, "[DiscoveryVM] Initializing DiscoveryViewModel. Triggering initial scan...")
        startDiscovery()
    }

    fun refresh() {
        Log.i(TAG, "[DiscoveryVM] User requested manual refresh. Restarting scan...")
        startDiscovery()
    }

    private fun startDiscovery() {
        scanJob?.cancel()
        _uiState.value = _uiState.value.copy(isScanning = true, errorMessage = null)
        Log.i(TAG, "[DiscoveryVM] Discovery scan started (isScanning = true).")

        scanJob = viewModelScope.launch {
            // Keep the active scan spinner running for a 3-second window
            launch {
                delay(3000L)
                _uiState.value = _uiState.value.copy(isScanning = false)
                Log.i(
                    TAG,
                    "[DiscoveryVM] 3-second scan window completed. Found ${_uiState.value.devices.size} device(s)."
                )
            }

            try {
                discoveryRepository.discoverDevices().collect { list ->
                    Log.i(
                        TAG,
                        "[DiscoveryVM] Received ${list.size} active device(s) from repository: " +
                                list.joinToString { "${it.name} (${it.host}:${it.port})" }
                    )
                    _uiState.value = _uiState.value.copy(devices = list)
                }
            } catch (e: Exception) {
                Log.e(TAG, "[DiscoveryVM] Error collecting discovered devices", e)
                _uiState.value = _uiState.value.copy(
                    isScanning = false,
                    errorMessage = e.localizedMessage ?: "Failed to scan devices"
                )
            }
        }
    }

    companion object {
        private const val TAG = "DiscoveryViewModel"
    }
}
