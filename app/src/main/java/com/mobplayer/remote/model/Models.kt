package com.mobplayer.remote.model

import kotlinx.serialization.Serializable

@Serializable
data class WebSocketMessage(
    val type: String,
    val payload: String
)

@Serializable
data class PlayerCommandPayload(
    val action: String, // "PLAY", "PAUSE", "STOP", "SEEK", "SET_VOLUME", "SET_SPEED", "DPAD_UP", "DPAD_DOWN", "DPAD_LEFT", "DPAD_RIGHT", "DPAD_CENTER", "BACK", "HOME", "MUTE", etc.
    val seekToMs: Long? = null,
    val positionMs: Long? = null,
    val volume: Float? = null,
    val speed: Float? = null,
    val keyCode: Int? = null,
    val url: String? = null,
    val title: String? = null
)

@Serializable
data class PlayerStatePayload(
    val isPlaying: Boolean,
    val positionMs: Long,
    val durationMs: Long = 0L,
    val volume: Float,
    val speed: Float = 1.0f
)

data class TvDevice(
    val name: String,
    val host: String,
    val port: Int = 8080
) {
    val endpointUrl: String
        get() {
            val formattedHost = if (host.contains(":") && !host.startsWith("[")) "[$host]" else host
            return "ws://$formattedHost:$port/control"
        }

    val httpBaseUrl: String
        get() {
            val formattedHost = if (host.contains(":") && !host.startsWith("[")) "[$host]" else host
            return "http://$formattedHost:$port"
        }
}

sealed interface UploadState {
    data object Idle : UploadState
    data class Uploading(
        val progress: Float,
        val bytesUploaded: Long,
        val totalBytes: Long,
        val fileName: String
    ) : UploadState
    data class Success(
        val fileName: String,
        val message: String = "Uploaded successfully"
    ) : UploadState
    data class Error(
        val errorMessage: String
    ) : UploadState
}

enum class LogDirection {
    REQUEST,   // Outgoing command/request sent from Phone to TV
    RESPONSE,  // Incoming response received from TV
    STATE,     // Playback state update received from TV
    SYSTEM,    // Socket lifecycle/connection event
    INCOMING,  // Incoming raw message
    OUTGOING,  // Outgoing raw message
    ERROR      // Socket or parsing error
}

data class RemoteLogEntry(
    val id: Long = System.currentTimeMillis(),
    val timestamp: String,
    val direction: LogDirection,
    val type: String,
    val payload: String,
    val isSuccess: Boolean = true
)

data class LogEvent(
    val id: Long = System.nanoTime(),
    val timestamp: Long = System.currentTimeMillis(),
    val direction: LogDirection,
    val tag: String,
    val message: String
)
