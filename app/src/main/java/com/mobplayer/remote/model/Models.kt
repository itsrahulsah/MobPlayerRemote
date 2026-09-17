package com.mobplayer.remote.model

import kotlinx.serialization.Serializable

@Serializable
data class WebSocketMessage(
    val type: String,
    val payload: String
)

@Serializable
data class PlayerCommandPayload(
    val action: String, // "PLAY", "PAUSE", "SEEK", "SET_VOLUME"
    val seekToMs: Long? = null,
    val volume: Float? = null
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
    val port: Int
) {
    val endpointUrl: String
        get() = "ws://$host:$port/control"
}
