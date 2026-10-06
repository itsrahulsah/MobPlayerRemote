package com.mobplayer.remote.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mobplayer.remote.model.TvDevice
import com.mobplayer.remote.model.WebSocketMessage
import com.mobplayer.remote.network.ConnectionState
import com.mobplayer.remote.network.ControlSocketRepository
import com.mobplayer.remote.repository.AuthTokenRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface AuthStep {
    data object Idle : AuthStep
    data object Connecting : AuthStep
    data object PinRequired : AuthStep
    data object Authenticated : AuthStep
    data class Failed(val reason: String) : AuthStep
}

data class AuthUiState(
    val step: AuthStep = AuthStep.Idle,
    val selectedDevice: TvDevice? = null,
    val pinInput: String = "",
    val errorMessage: String? = null
)

class AuthViewModel(
    private val socketRepository: ControlSocketRepository,
    private val tokenRepository: AuthTokenRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    private var incomingMessagesJob: Job? = null
    private var connectionStateJob: Job? = null

    init {
        observeSocket()
    }

    private fun observeSocket() {
        connectionStateJob?.cancel()
        connectionStateJob = viewModelScope.launch {
            socketRepository.connectionState.collect { state ->
                when (state) {
                    is ConnectionState.Connected -> {
                        // Once connected, dispatch AUTH_REQUEST
                        dispatchAuthRequest()
                    }
                    is ConnectionState.Error -> {
                        // Only kick out to Failed screen if we were not already authenticated
                        if (_uiState.value.step !is AuthStep.Authenticated) {
                            _uiState.value = _uiState.value.copy(
                                step = AuthStep.Failed(state.message),
                                errorMessage = state.message
                            )
                        } else {
                            // If authenticated, just record the error message, do not reset UI to PIN screen
                            _uiState.value = _uiState.value.copy(
                                errorMessage = state.message
                            )
                        }
                    }
                    is ConnectionState.Disconnected -> {
                        if (_uiState.value.step is AuthStep.Connecting || _uiState.value.step is AuthStep.PinRequired) {
                            _uiState.value = _uiState.value.copy(
                                step = AuthStep.Failed("Disconnected from TV"),
                                errorMessage = "Connection closed by TV"
                            )
                        }
                    }
                    else -> Unit
                }
            }
        }

        incomingMessagesJob?.cancel()
        incomingMessagesJob = viewModelScope.launch {
            socketRepository.incomingMessages.collect { message ->
                handleIncomingMessage(message)
            }
        }
    }

    fun connectToDevice(device: TvDevice) {
        _uiState.value = AuthUiState(
            step = AuthStep.Connecting,
            selectedDevice = device,
            pinInput = ""
        )
        socketRepository.connect(device.endpointUrl)
    }

    fun connectToManualHost(host: String, port: Int = 8080) {
        val cleanHost = host.trim().removePrefix("http://").removePrefix("https://").removePrefix("ws://")
        val parsedPort = if (cleanHost.contains(":")) {
            cleanHost.substringAfter(":").toIntOrNull() ?: port
        } else {
            port
        }
        val finalHost = cleanHost.substringBefore(":")
        val device = TvDevice(
            name = "MobPlayer TV ($finalHost)",
            host = finalHost,
            port = parsedPort
        )
        connectToDevice(device)
    }

    private var pendingPinSubmit: String? = null

    fun retryConnection() {
        pendingPinSubmit = null
        val device = _uiState.value.selectedDevice ?: return
        connectToDevice(device)
    }

    private suspend fun dispatchAuthRequest() {
        val device = _uiState.value.selectedDevice ?: return
        val savedToken = tokenRepository.getToken(device.name) ?: "no-token-yet"
        socketRepository.sendMessage(WebSocketMessage("AUTH_REQUEST", savedToken))
    }

    fun onPinChanged(pin: String) {
        if (pin.length <= 4 && pin.all { it.isDigit() }) {
            _uiState.value = _uiState.value.copy(pinInput = pin, errorMessage = null)
        }
    }

    fun submitPin() {
        val pin = _uiState.value.pinInput
        if (pin.length == 4) {
            val isConnected = socketRepository.connectionState.value is ConnectionState.Connected
            if (isConnected) {
                viewModelScope.launch {
                    socketRepository.sendMessage(WebSocketMessage("PIN_SUBMIT", pin))
                }
            } else {
                pendingPinSubmit = pin
                val device = _uiState.value.selectedDevice
                if (device != null) {
                    _uiState.value = _uiState.value.copy(
                        step = AuthStep.Connecting,
                        errorMessage = null
                    )
                    socketRepository.connect(device.endpointUrl)
                }
            }
        }
    }

    private fun handleIncomingMessage(message: WebSocketMessage) {
        val device = _uiState.value.selectedDevice
        when (message.type) {
            "PIN_REQUIRED" -> {
                val autoPin = pendingPinSubmit
                if (autoPin != null && autoPin.length == 4) {
                    pendingPinSubmit = null
                    _uiState.value = _uiState.value.copy(
                        step = AuthStep.PinRequired,
                        pinInput = autoPin,
                        errorMessage = null
                    )
                    viewModelScope.launch {
                        socketRepository.sendMessage(WebSocketMessage("PIN_SUBMIT", autoPin))
                    }
                } else {
                    _uiState.value = _uiState.value.copy(
                        step = AuthStep.PinRequired,
                        errorMessage = null
                    )
                }
            }
            "AUTH_SUCCESS" -> {
                pendingPinSubmit = null
                val token = message.payload
                if (device != null && token.isNotBlank()) {
                    tokenRepository.saveToken(device.name, token)
                }
                _uiState.value = _uiState.value.copy(
                    step = AuthStep.Authenticated,
                    errorMessage = null
                )
            }
            "AUTH_FAILED" -> {
                pendingPinSubmit = null
                val reason = message.payload.ifBlank { "Authentication failed" }
                if (device != null) {
                    tokenRepository.clearToken(device.name)
                }
                _uiState.value = _uiState.value.copy(
                    step = AuthStep.Failed(reason),
                    errorMessage = reason,
                    pinInput = ""
                )
                socketRepository.disconnect()
            }
        }
    }

    fun reset() {
        pendingPinSubmit = null
        socketRepository.disconnect()
        _uiState.value = AuthUiState()
    }
}

