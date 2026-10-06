package com.mobplayer.remote.network

import android.util.Log
import com.mobplayer.remote.model.LogDirection
import com.mobplayer.remote.model.LogEvent
import com.mobplayer.remote.model.RemoteLogEntry
import com.mobplayer.remote.model.WebSocketMessage
import com.mobplayer.remote.repository.LogPreferencesRepository
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed interface ConnectionState {
    data object Disconnected : ConnectionState
    data object Connecting : ConnectionState
    data object Connected : ConnectionState
    data class Error(val message: String) : ConnectionState
}

class ControlSocketRepository(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + Job()),
    private val logPreferencesRepository: LogPreferencesRepository? = null
) {
    private val json = Json { ignoreUnknownKeys = true }

    private val client = HttpClient(CIO) {
        install(WebSockets) {
            pingInterval = 15_000L
        }
    }

    private var activeSession: DefaultClientWebSocketSession? = null
    private var connectionJob: Job? = null
    var lastEndpointUrl: String? = null
        private set

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _incomingMessages = MutableSharedFlow<WebSocketMessage>(extraBufferCapacity = 64)
    val incomingMessages: SharedFlow<WebSocketMessage> = _incomingMessages.asSharedFlow()

    // Real-time Request & Response Logs Flow (initialized with persisted logs within 24 hours)
    private val _logs = MutableStateFlow<List<RemoteLogEntry>>(
        logPreferencesRepository?.loadLogs() ?: emptyList()
    )
    val logs: StateFlow<List<RemoteLogEntry>> = _logs.asStateFlow()

    private val _trafficLogs = MutableSharedFlow<LogEvent>(extraBufferCapacity = 128)
    val trafficLogs: SharedFlow<LogEvent> = _trafficLogs.asSharedFlow()

    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    fun logEvent(direction: LogDirection, type: String, payload: String, isSuccess: Boolean = true) {
        val timestamp = timeFormat.format(Date())
        val entry = RemoteLogEntry(
            timestamp = timestamp,
            direction = direction,
            type = type,
            payload = payload,
            isSuccess = isSuccess
        )

        _trafficLogs.tryEmit(
            LogEvent(
                id = entry.id,
                direction = direction,
                tag = type,
                message = payload
            )
        )

        // Log to Android Logcat or console
        when (direction) {
            LogDirection.REQUEST, LogDirection.OUTGOING -> safeLog(Log.INFO, TAG, "➡️ [REQUEST to TV] type='$type' | payload='$payload'")
            LogDirection.RESPONSE, LogDirection.INCOMING -> safeLog(Log.INFO, TAG, "⬅️ [RESPONSE from TV] type='$type' | payload='$payload'")
            LogDirection.STATE -> safeLog(Log.DEBUG, TAG, "📺 [STATE from TV] payload='$payload'")
            LogDirection.SYSTEM -> {
                if (isSuccess) safeLog(Log.INFO, TAG, "ℹ️ [SYSTEM] $type: $payload")
                else safeLog(Log.ERROR, TAG, "❌ [SYSTEM ERROR] $type: $payload")
            }
            LogDirection.ERROR -> safeLog(Log.ERROR, TAG, "❌ [ERROR] $type: $payload")
        }

        // Add to UI log state (keep newest 200 items, and prune entries older than 24h)
        val cutoff = System.currentTimeMillis() - (logPreferencesRepository?.retentionPeriodMs ?: LogPreferencesRepository.ONE_DAY_MS)
        _logs.update { list ->
            val pruned = list.filter { it.epochMs >= cutoff }
            val updated = (listOf(entry) + pruned).take(200)
            logPreferencesRepository?.saveLogs(updated)
            updated
        }
    }

    fun clearLogs() {
        _logs.value = emptyList()
        logPreferencesRepository?.clearLogs()
    }

    fun connect(endpointUrl: String) {
        lastEndpointUrl = endpointUrl
        disconnect()

        connectionJob = scope.launch {
            _connectionState.value = ConnectionState.Connecting
            logEvent(LogDirection.SYSTEM, "CONNECTING", "Establishing connection to $endpointUrl")
            try {
                client.webSocket(urlString = endpointUrl) {
                    activeSession = this
                    _connectionState.value = ConnectionState.Connected
                    logEvent(LogDirection.SYSTEM, "CONNECTED", "Connected to $endpointUrl")

                    try {
                        for (frame in incoming) {
                            if (frame is Frame.Text) {
                                val text = frame.readText()
                                try {
                                    val message = json.decodeFromString<WebSocketMessage>(text)
                                    if (message.type == "STATE_UPDATE") {
                                        logEvent(LogDirection.STATE, message.type, message.payload)
                                    } else {
                                        logEvent(LogDirection.RESPONSE, message.type, message.payload)
                                    }
                                    _incomingMessages.emit(message)
                                } catch (e: Exception) {
                                    logEvent(LogDirection.ERROR, "PARSE_ERROR", "Failed to parse: $text (${e.message})", false)
                                    safeLog(Log.ERROR, TAG, "Failed to parse message: $text", e)
                                }
                            }
                        }
                    } finally {
                        activeSession = null
                        if (_connectionState.value == ConnectionState.Connected) {
                            _connectionState.value = ConnectionState.Disconnected
                            logEvent(LogDirection.SYSTEM, "DISCONNECTED", "Connection closed by peer")
                        }
                    }
                }
            } catch (e: CancellationException) {
                logEvent(LogDirection.SYSTEM, "CANCELLED", "Connection cancelled")
                _connectionState.value = ConnectionState.Disconnected
            } catch (e: Exception) {
                val err = e.localizedMessage ?: "Connection error"
                logEvent(LogDirection.ERROR, "CONNECTION_ERROR", err, false)
                _connectionState.value = ConnectionState.Error(err)
            } finally {
                activeSession = null
            }
        }
    }

    fun reconnect() {
        lastEndpointUrl?.let { connect(it) }
    }

    suspend fun sendMessage(message: WebSocketMessage): Boolean {
        val session = activeSession
        return if (session != null && session.isActive) {
            try {
                val encoded = json.encodeToString(message)
                session.send(Frame.Text(encoded))
                logEvent(LogDirection.REQUEST, message.type, message.payload)
                true
            } catch (e: Exception) {
                logEvent(LogDirection.ERROR, "SEND_FAILED", "Failed to send ${message.type}: ${e.message}", false)
                safeLog(Log.ERROR, TAG, "Failed to send message: ${message.type}", e)
                false
            }
        } else {
            val errMsg = "Cannot send '${message.type}': Socket not connected"
            logEvent(LogDirection.ERROR, "SEND_BLOCKED", errMsg, false)
            safeLog(Log.WARN, TAG, errMsg)
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

        fun safeLog(priority: Int, tag: String, msg: String, tr: Throwable? = null) {
            try {
                when (priority) {
                    Log.DEBUG -> Log.d(tag, msg, tr)
                    Log.INFO -> Log.i(tag, msg, tr)
                    Log.WARN -> Log.w(tag, msg, tr)
                    Log.ERROR -> Log.e(tag, msg, tr)
                    else -> Log.v(tag, msg, tr)
                }
            } catch (_: Throwable) {
                // Host unit test environment fallback when android.util.Log is not mocked
                println("[$tag] $msg")
            }
        }
    }
}
