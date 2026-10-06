package com.mobplayer.remote.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mobplayer.remote.model.LogEvent
import com.mobplayer.remote.model.PlayerCommandPayload
import com.mobplayer.remote.model.PlayerStatePayload
import com.mobplayer.remote.model.UploadState
import com.mobplayer.remote.model.WebSocketMessage
import com.mobplayer.remote.network.ControlSocketRepository
import com.mobplayer.remote.network.MediaUploadForegroundService
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@OptIn(FlowPreview::class)
class RemoteViewModel(
    private val socketRepository: ControlSocketRepository
) : ViewModel() {

    private val json = Json { ignoreUnknownKeys = true }

    private val _playerState = MutableStateFlow<PlayerStatePayload?>(null)
    val playerState: StateFlow<PlayerStatePayload?> = _playerState.asStateFlow()

    // Volume debouncing flow to avoid flooding WebSocket during rapid slider adjustments
    private val volumeUpdateFlow = MutableSharedFlow<Float>(extraBufferCapacity = 1)

    // Previous volume before mute to allow unmute restoration
    private var preMuteVolume: Float = 0.5f

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

    fun play() {
        dispatchCommand(PlayerCommandPayload(action = "PLAY"))
    }

    fun pause() {
        dispatchCommand(PlayerCommandPayload(action = "PAUSE"))
    }

    fun stop() {
        dispatchCommand(PlayerCommandPayload(action = "STOP"))
    }

    fun togglePlayPause() {
        val currentState = _playerState.value
        if (currentState?.isPlaying == true) {
            pause()
        } else {
            play()
        }
    }

    fun seekTo(positionMs: Long) {
        val duration = _playerState.value?.durationMs ?: Long.MAX_VALUE
        val targetMs = positionMs.coerceIn(0L, if (duration > 0) duration else Long.MAX_VALUE)
        dispatchCommand(PlayerCommandPayload(action = "SEEK", seekToMs = targetMs))
    }

    fun seekRelative(offsetMs: Long) {
        val current = _playerState.value?.positionMs ?: 0L
        val duration = _playerState.value?.durationMs ?: Long.MAX_VALUE
        val targetMs = (current + offsetMs).coerceIn(0L, if (duration > 0) duration else Long.MAX_VALUE)
        seekTo(targetMs)
    }

    fun onVolumeSliderChanged(volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        if (clamped > 0f) {
            preMuteVolume = clamped
        }
        // Optimistically update local state for instantaneous slider response
        _playerState.value = _playerState.value?.copy(volume = clamped)
            ?: PlayerStatePayload(isPlaying = false, positionMs = 0L, durationMs = 0L, volume = clamped)
        volumeUpdateFlow.tryEmit(clamped)
    }

    fun adjustVolumeRelative(delta: Float) {
        val current = _playerState.value?.volume ?: 0.5f
        val newVol = (current + delta).coerceIn(0f, 1f)
        onVolumeSliderChanged(newVol)
    }

    fun toggleMute() {
        val current = _playerState.value?.volume ?: 0.5f
        if (current > 0f) {
            preMuteVolume = current
            onVolumeSliderChanged(0f)
        } else {
            onVolumeSliderChanged(if (preMuteVolume > 0f) preMuteVolume else 0.5f)
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        val clampedSpeed = speed.coerceIn(0.25f, 3.0f)
        _playerState.value = _playerState.value?.copy(speed = clampedSpeed)
            ?: PlayerStatePayload(isPlaying = false, positionMs = 0L, durationMs = 0L, volume = 0.5f, speed = clampedSpeed)
        dispatchCommand(PlayerCommandPayload(action = "SET_SPEED", speed = clampedSpeed))
    }

    fun loadMedia(url: String) {
        if (url.isBlank()) return
        viewModelScope.launch {
            socketRepository.sendMessage(WebSocketMessage("LOAD_MEDIA", url.trim()))
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

    fun clearLogs() {
        _eventLogs.value = emptyList()
    }

    private fun dispatchCommand(command: PlayerCommandPayload) {
        viewModelScope.launch {
            val payload = json.encodeToString(command)
            socketRepository.sendMessage(WebSocketMessage("COMMAND", payload))
        }
    }

    companion object {
        private const val TAG = "RemoteViewModel"
    }
}
