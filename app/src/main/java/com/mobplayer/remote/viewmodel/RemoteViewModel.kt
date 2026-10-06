package com.mobplayer.remote.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mobplayer.remote.model.LogEvent
import com.mobplayer.remote.model.PlayerCommandPayload
import com.mobplayer.remote.model.PlayerStatePayload
import com.mobplayer.remote.model.RemoteLogEntry
import com.mobplayer.remote.model.UploadState
import com.mobplayer.remote.model.WebSocketMessage
import com.mobplayer.remote.network.ConnectionState
import com.mobplayer.remote.network.ControlSocketRepository
import com.mobplayer.remote.network.MediaUploadForegroundService
import com.mobplayer.remote.repository.SearchPreferencesRepository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@OptIn(FlowPreview::class)
class RemoteViewModel(
    private val socketRepository: ControlSocketRepository,
    private val searchPreferencesRepository: SearchPreferencesRepository? = null
) : ViewModel() {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val _playerState = MutableStateFlow<PlayerStatePayload?>(null)
    val playerState: StateFlow<PlayerStatePayload?> = _playerState.asStateFlow()

    val connectionState: StateFlow<ConnectionState> = socketRepository.connectionState
    val logs: StateFlow<List<RemoteLogEntry>> = socketRepository.logs

    // Search query history in MRU order (latest on top)
    private val _searchHistory = MutableStateFlow<List<String>>(
        searchPreferencesRepository?.getSearchHistory() ?: emptyList()
    )
    val searchHistory: StateFlow<List<String>> = _searchHistory.asStateFlow()

    // Volume debouncing flow to avoid flooding WebSocket during rapid slider adjustments
    private val volumeUpdateFlow = MutableSharedFlow<Float>(extraBufferCapacity = 1)

    // Previous volume before mute to allow unmute restoration
    private var preMuteVolume: Float = 0.5f

    // Smooth position advancement while playing
    private var progressTickerJob: Job? = null

    // Live diagnostics event logs buffer (capped at 100 items)
    private val _eventLogs = MutableStateFlow<List<LogEvent>>(emptyList())
    val eventLogs: StateFlow<List<LogEvent>> = _eventLogs.asStateFlow()

    // Upload state forwarded from the foreground service
    val uploadState: StateFlow<UploadState> = MediaUploadForegroundService.serviceUploadState

    init {
        observeStateUpdates()
        observeVolumeUpdates()
        observeTrafficLogs()
    }

    private fun observeStateUpdates() {
        viewModelScope.launch {
            socketRepository.incomingMessages.collect { message ->
                when (message.type) {
                    "STATE_UPDATE" -> {
                        try {
                            val state = json.decodeFromString<PlayerStatePayload>(message.payload)
                            _playerState.value = state
                            handlePlaybackTicker(state.isPlaying)
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to parse STATE_UPDATE payload: ${message.payload}", e)
                        }
                    }
                    "COMMAND_SUCCESS" -> {
                        Log.d(TAG, "Server acknowledged command: ${message.payload}")
                    }
                }
            }
        }
    }

    private fun observeVolumeUpdates() {
        viewModelScope.launch {
            volumeUpdateFlow
                .debounce(50L)
                .collect { volume ->
                    val command = PlayerCommandPayload(
                        action = "SET_VOLUME",
                        volume = volume
                    )
                    dispatchCommand(command)
                }
        }
    }

    private fun observeTrafficLogs() {
        viewModelScope.launch {
            socketRepository.trafficLogs.collect { logEvent ->
                val current = _eventLogs.value.toMutableList()
                if (current.size >= 100) {
                    current.removeAt(0)
                }
                current.add(logEvent)
                _eventLogs.value = current
            }
        }
    }

    private fun handlePlaybackTicker(isPlaying: Boolean) {
        if (isPlaying) {
            if (progressTickerJob?.isActive != true) {
                progressTickerJob = viewModelScope.launch {
                    while (isActive) {
                        delay(1000L)
                        val cur = _playerState.value ?: break
                        if (!cur.isPlaying) break
                        val dur = cur.durationMs
                        val nextPos = if (dur > 0) minOf(cur.positionMs + 1000L, dur) else cur.positionMs + 1000L
                        _playerState.value = cur.copy(positionMs = nextPos)
                    }
                }
            }
        } else {
            progressTickerJob?.cancel()
            progressTickerJob = null
        }
    }

    fun play() {
        _playerState.value = _playerState.value?.copy(isPlaying = true)
            ?: PlayerStatePayload(isPlaying = true, positionMs = 0L, durationMs = 0L, volume = 1f)
        handlePlaybackTicker(true)
        dispatchCommand(PlayerCommandPayload(action = "PLAY"))
    }

    fun pause() {
        _playerState.value = _playerState.value?.copy(isPlaying = false)
        handlePlaybackTicker(false)
        dispatchCommand(PlayerCommandPayload(action = "PAUSE"))
    }

    fun togglePlayPause() {
        val isCurrentlyPlaying = _playerState.value?.isPlaying == true
        if (isCurrentlyPlaying) {
            pause()
        } else {
            play()
        }
    }

    fun stop() {
        _playerState.value = _playerState.value?.copy(isPlaying = false, positionMs = 0L)
        handlePlaybackTicker(false)
        dispatchCommand(PlayerCommandPayload(action = "STOP"))
    }

    fun seekTo(positionMs: Long) {
        val current = _playerState.value
        val duration = current?.durationMs ?: 0L
        val maxBound = if (duration > 0) duration else Long.MAX_VALUE
        val targetMs = positionMs.coerceIn(0L, maxBound)

        if (current != null) {
            _playerState.value = current.copy(positionMs = targetMs)
        }
        dispatchCommand(PlayerCommandPayload(action = "SEEK", seekToMs = targetMs))
    }

    fun seekRelative(offsetMs: Long) {
        val currentPos = _playerState.value?.positionMs ?: 0L
        val duration = _playerState.value?.durationMs ?: 0L
        val maxBound = if (duration > 0) duration else Long.MAX_VALUE
        val targetMs = (currentPos + offsetMs).coerceIn(0L, maxBound)
        seekTo(targetMs)
    }

    fun onVolumeSliderChanged(volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        if (clamped > 0f) {
            preMuteVolume = clamped
        }
        val current = _playerState.value
        _playerState.value = current?.copy(volume = clamped)
            ?: PlayerStatePayload(isPlaying = false, positionMs = 0L, durationMs = 0L, volume = clamped)
        volumeUpdateFlow.tryEmit(clamped)
    }

    fun adjustVolumeRelative(delta: Float) {
        val currentVol = _playerState.value?.volume ?: 0.5f
        val newVol = (currentVol + delta).coerceIn(0f, 1f)
        onVolumeSliderChanged(newVol)
    }

    fun toggleMute() {
        val current = _playerState.value?.volume ?: 0.5f
        if (current > 0f) {
            preMuteVolume = current
            onVolumeSliderChanged(0f)
        } else {
            onVolumeSliderChanged(if (preMuteVolume > 0f) preMuteVolume else 0.8f)
        }
        dispatchCommand(PlayerCommandPayload(action = "MUTE"))
    }

    fun setPlaybackSpeed(speed: Float) {
        val clampedSpeed = speed.coerceIn(0.25f, 3.0f)
        val current = _playerState.value
        _playerState.value = current?.copy(speed = clampedSpeed)
            ?: PlayerStatePayload(isPlaying = false, positionMs = 0L, durationMs = 0L, volume = 0.5f, speed = clampedSpeed)
        dispatchCommand(PlayerCommandPayload(action = "SET_SPEED", speed = clampedSpeed))
    }

    // D-Pad and Navigation Key Commands
    fun sendDpadUp() {
        dispatchCommand(PlayerCommandPayload(action = "DPAD_UP"))
    }

    fun sendDpadDown() {
        dispatchCommand(PlayerCommandPayload(action = "DPAD_DOWN"))
    }

    fun sendDpadLeft() {
        dispatchCommand(PlayerCommandPayload(action = "DPAD_LEFT"))
    }

    fun sendDpadRight() {
        dispatchCommand(PlayerCommandPayload(action = "DPAD_RIGHT"))
    }

    fun sendDpadCenter() {
        dispatchCommand(PlayerCommandPayload(action = "DPAD_CENTER"))
    }

    fun sendBack() {
        dispatchCommand(PlayerCommandPayload(action = "BACK"))
    }

    fun sendHome() {
        dispatchCommand(PlayerCommandPayload(action = "HOME"))
    }

    fun saveSearchQuery(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return
        val updated = searchPreferencesRepository?.saveQuery(trimmed) ?: run {
            val list = _searchHistory.value.toMutableList()
            list.removeAll { it.equals(trimmed, ignoreCase = true) }
            list.add(0, trimmed)
            list
        }
        _searchHistory.value = updated
    }

    fun clearSearchHistory() {
        searchPreferencesRepository?.clearHistory()
        _searchHistory.value = emptyList()
    }

    fun sendTextInput(text: String) {
        viewModelScope.launch {
            socketRepository.sendMessage(WebSocketMessage("TEXT_INPUT", text))
        }
    }

    fun sendTextSubmit(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return
        saveSearchQuery(trimmed)
        viewModelScope.launch {
            socketRepository.sendMessage(WebSocketMessage("TEXT_SUBMIT", trimmed))
        }
    }

    fun search(query: String) {
        sendTextSubmit(query)
    }

    fun loadMedia(url: String, title: String? = null) {
        if (url.isBlank()) return
        viewModelScope.launch {
            val payload = json.encodeToString(
                PlayerCommandPayload(
                    action = "LOAD_MEDIA",
                    url = url.trim(),
                    title = title ?: "Remote Stream"
                )
            )
            socketRepository.sendMessage(WebSocketMessage("LOAD_MEDIA", payload))
        }
    }

    fun uploadVideo(context: Context, serverUrl: String, fileUri: Uri) {
        MediaUploadForegroundService.startUpload(context, serverUrl, fileUri)
    }

    fun cancelUpload(context: Context) {
        MediaUploadForegroundService.cancel(context)
    }

    fun resetUploadState() {
        MediaUploadForegroundService.resetState()
    }

    fun sendRawCommand(type: String, payload: String) {
        if (type.isBlank()) return
        viewModelScope.launch {
            socketRepository.sendMessage(WebSocketMessage(type.trim(), payload.trim()))
        }
    }

    fun reconnect() {
        socketRepository.reconnect()
    }

    fun clearLogs() {
        socketRepository.clearLogs()
        _eventLogs.value = emptyList()
    }

    private fun dispatchCommand(command: PlayerCommandPayload) {
        viewModelScope.launch {
            val payload = json.encodeToString(command)
            socketRepository.sendMessage(WebSocketMessage("COMMAND", payload))
        }
    }

    override fun onCleared() {
        super.onCleared()
        progressTickerJob?.cancel()
    }

    companion object {
        private const val TAG = "RemoteViewModel"
    }
}
