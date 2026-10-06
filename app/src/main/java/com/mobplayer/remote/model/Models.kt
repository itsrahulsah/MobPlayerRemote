package com.mobplayer.remote.model

import kotlinx.serialization.Serializable

@Serializable
data class WebSocketMessage(
    val type: String,
    val payload: String
)

@Serializable
data class PlayerCommandPayload(
    val action: String, // "PLAY", "PAUSE", "STOP", "SEEK", "SET_VOLUME", "SET_SPEED"
    val seekToMs: Long? = null,
    val volume: Float? = null,
    val speed: Float? = null
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
    val port: Int
) {
    val endpointUrl: String
        get() = "ws://$host:$port/control"

    val httpBaseUrl: String
        get() = "http://$host:$port"
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

data class LogEvent(
    val id: Long = System.nanoTime(),
    val timestamp: Long = System.currentTimeMillis(),
    val direction: LogDirection,
    val tag: String,
    val message: String
)

enum class LogDirection {
    INCOMING,
    OUTGOING,
    SYSTEM,
    ERROR
}
