package com.mobplayer.remote.network

import android.util.Log
import com.mobplayer.remote.model.WebSocketMessage
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

sealed interface ConnectionState {
    data object Disconnected : ConnectionState
    data object Connecting : ConnectionState
    data object Connected : ConnectionState
    data class Error(val message: String) : ConnectionState
}

class ControlSocketRepository(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + Job())
) {
    private val json = Json { ignoreUnknownKeys = true }

    private val client = HttpClient(CIO) {
        install(WebSockets) {
            pingInterval = 15_000L
        }
    }

    private var activeSession: DefaultClientWebSocketSession? = null
    private var connectionJob: Job? = null

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _incomingMessages = MutableSharedFlow<WebSocketMessage>(extraBufferCapacity = 64)
    val incomingMessages: SharedFlow<WebSocketMessage> = _incomingMessages.asSharedFlow()

    fun connect(endpointUrl: String) {
        disconnect()

        connectionJob = scope.launch {
            _connectionState.value = ConnectionState.Connecting
            try {
                client.webSocket(urlString = endpointUrl) {
                    activeSession = this
                    _connectionState.value = ConnectionState.Connected
                    Log.d(TAG, "Connected to $endpointUrl")

                    try {
                        for (frame in incoming) {
                            if (frame is Frame.Text) {
                                val text = frame.readText()
                                try {
                                    val message = json.decodeFromString<WebSocketMessage>(text)
                                    _incomingMessages.emit(message)
                                } catch (e: Exception) {
                                    Log.e(TAG, "Failed to parse message: $text", e)
                                }
                            }
                        }
                    } finally {
                        activeSession = null
                        if (_connectionState.value == ConnectionState.Connected) {
                            _connectionState.value = ConnectionState.Disconnected
                        }
                    }
                }
            } catch (e: CancellationException) {
                Log.d(TAG, "Connection cancelled")
                _connectionState.value = ConnectionState.Disconnected
            } catch (e: Exception) {
                Log.e(TAG, "Connection error", e)
                _connectionState.value = ConnectionState.Error(e.localizedMessage ?: "Connection error")
            } finally {
                activeSession = null
            }
        }
    }

    suspend fun sendMessage(message: WebSocketMessage): Boolean {
        val session = activeSession
        return if (session != null && session.isActive) {
            try {
                val encoded = json.encodeToString(message)
                session.send(Frame.Text(encoded))
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send message: ${message.type}", e)
                false
            }
        } else {
            Log.w(TAG, "Cannot send message, socket not connected")
            false
        }
    }

    fun disconnect() {
        connectionJob?.cancel()
        connectionJob = null
        scope.launch {
            try {
                activeSession?.close()
            } catch (_: Exception) {
            } finally {
                activeSession = null
                _connectionState.value = ConnectionState.Disconnected
            }
        }
    }

    companion object {
        private const val TAG = "ControlSocketRepository"
    }
}
