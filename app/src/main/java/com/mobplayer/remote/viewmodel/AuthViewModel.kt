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
                        _uiState.value = _uiState.value.copy(
                            step = AuthStep.Failed(state.message),
                            errorMessage = state.message
                        )
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
            viewModelScope.launch {
                socketRepository.sendMessage(WebSocketMessage("PIN_SUBMIT", pin))
            }
        }
    }

    private fun handleIncomingMessage(message: WebSocketMessage) {
        val device = _uiState.value.selectedDevice
        when (message.type) {
            "PIN_REQUIRED" -> {
                _uiState.value = _uiState.value.copy(
                    step = AuthStep.PinRequired,
                    errorMessage = null
                )
            }
            "AUTH_SUCCESS" -> {
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
        socketRepository.disconnect()
        _uiState.value = AuthUiState()
    }
}
