package com.mobplayer.remote.model

import kotlinx.serialization.Serializable

@Serializable
data class WebSocketMessage(
    val type: String,
    val payload: String
)

@Serializable
data class PlayerCommandPayload(
    val action: String, // "PLAY", "PAUSE", "SEEK", "SET_VOLUME", "DPAD_UP", "DPAD_DOWN", "DPAD_LEFT", "DPAD_RIGHT", "DPAD_CENTER", "BACK", "HOME", "STOP", "MUTE", etc.
    val seekToMs: Long? = null,
    val positionMs: Long? = null,
    val volume: Float? = null,
    val keyCode: Int? = null,
    val url: String? = null,
    val title: String? = null
)

@Serializable
data class PlayerStatePayload(
    val isPlaying: Boolean,
    val positionMs: Long,
    val durationMs: Long = 0L,
    val volume: Float
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
}

enum class LogDirection {
    REQUEST,   // Outgoing command/request sent from Phone to TV
    RESPONSE,  // Incoming response received from TV
    STATE,     // Playback state update received from TV
    SYSTEM     // Socket lifecycle/connection event
}

data class RemoteLogEntry(
    val id: Long = System.currentTimeMillis(),
    val timestamp: String,
    val direction: LogDirection,
    val type: String,
    val payload: String,
    val isSuccess: Boolean = true
)

