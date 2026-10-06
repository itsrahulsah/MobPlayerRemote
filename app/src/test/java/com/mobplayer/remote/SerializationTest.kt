package com.mobplayer.remote

import com.mobplayer.remote.model.PlayerCommandPayload
import com.mobplayer.remote.model.PlayerStatePayload
import com.mobplayer.remote.model.WebSocketMessage
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SerializationTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun testWebSocketMessageSerialization() {
        val msg = WebSocketMessage(type = "COMMAND", payload = "{\"action\":\"PLAY\"}")
        val encoded = json.encodeToString(msg)
        val decoded = json.decodeFromString<WebSocketMessage>(encoded)

        assertEquals("COMMAND", decoded.type)
        assertEquals("{\"action\":\"PLAY\"}", decoded.payload)
    }

    @Test
    fun testPlayerCommandPayloadPlayPause() {
        val playCommand = PlayerCommandPayload(action = "PLAY")
        val encodedPlay = json.encodeToString(playCommand)
        val decodedPlay = json.decodeFromString<PlayerCommandPayload>(encodedPlay)
        assertEquals("PLAY", decodedPlay.action)
        assertNull(decodedPlay.seekToMs)
        assertNull(decodedPlay.volume)

        val pauseCommand = PlayerCommandPayload(action = "PAUSE")
        val encodedPause = json.encodeToString(pauseCommand)
        val decodedPause = json.decodeFromString<PlayerCommandPayload>(encodedPause)
        assertEquals("PAUSE", decodedPause.action)
    }

    @Test
    fun testPlayerCommandPayloadSeek() {
        val seekCommand = PlayerCommandPayload(action = "SEEK", seekToMs = 45000L)
        val encoded = json.encodeToString(seekCommand)
        val decoded = json.decodeFromString<PlayerCommandPayload>(encoded)

        assertEquals("SEEK", decoded.action)
        assertEquals(45000L, decoded.seekToMs)
    }

    @Test
    fun testPlayerCommandPayloadSetVolume() {
        val volumeCommand = PlayerCommandPayload(action = "SET_VOLUME", volume = 0.75f)
        val encoded = json.encodeToString(volumeCommand)
        val decoded = json.decodeFromString<PlayerCommandPayload>(encoded)

        assertEquals("SET_VOLUME", decoded.action)
        assertEquals(0.75f, decoded.volume ?: 0f, 0.001f)
    }

    @Test
    fun testPlayerStatePayloadParsingWithDuration() {
        val rawJson = """
            {
                "isPlaying": true,
                "positionMs": 34520,
                "durationMs": 180000,
                "volume": 0.85
            }
        """.trimIndent()

        val decoded = json.decodeFromString<PlayerStatePayload>(rawJson)
        assertTrue(decoded.isPlaying)
        assertEquals(34520L, decoded.positionMs)
        assertEquals(180000L, decoded.durationMs)
        assertEquals(0.85f, decoded.volume, 0.001f)
    }

    @Test
    fun testPlayerStatePayloadBackwardCompatibilityWithoutDuration() {
        // If an older TV version sends state without durationMs
        val rawJson = """
            {
                "isPlaying": false,
                "positionMs": 1000,
                "volume": 1.0
            }
        """.trimIndent()
        val decoded = json.decodeFromString<PlayerStatePayload>(rawJson)
        assertEquals(false, decoded.isPlaying)
        assertEquals(1000L, decoded.positionMs)
        assertEquals(0L, decoded.durationMs)
        assertEquals(1.0f, decoded.volume, 0.001f)
        assertEquals(1.0f, decoded.speed, 0.001f)
    }

    @Test
    fun testPlayerCommandPayloadStop() {
        val stopCommand = PlayerCommandPayload(action = "STOP")
        val encoded = json.encodeToString(stopCommand)
        val decoded = json.decodeFromString<PlayerCommandPayload>(encoded)
        assertEquals("STOP", decoded.action)
        assertNull(decoded.seekToMs)
        assertNull(decoded.volume)
        assertNull(decoded.speed)
    }

    @Test
    fun testPlayerCommandPayloadSetSpeed() {
        val speedCommand = PlayerCommandPayload(action = "SET_SPEED", speed = 1.75f)
        val encoded = json.encodeToString(speedCommand)
        val decoded = json.decodeFromString<PlayerCommandPayload>(encoded)
        assertEquals("SET_SPEED", decoded.action)
        assertEquals(1.75f, decoded.speed ?: 1.0f, 0.001f)
    }

    @Test
    fun testPlayerStatePayloadWithSpeed() {
        val rawJson = """
            {
                "isPlaying": true,
                "positionMs": 15000,
                "durationMs": 120000,
                "volume": 0.6,
                "speed": 1.5
            }
        """.trimIndent()

        val decoded = json.decodeFromString<PlayerStatePayload>(rawJson)
        assertTrue(decoded.isPlaying)
        assertEquals(15000L, decoded.positionMs)
        assertEquals(120000L, decoded.durationMs)
        assertEquals(0.6f, decoded.volume, 0.001f)
        assertEquals(1.5f, decoded.speed, 0.001f)
    }

    @Test
    fun testTvDeviceHttpBaseUrl() {
        val device = com.mobplayer.remote.model.TvDevice("Living Room TV", "192.168.1.50", 8080)
        assertEquals("ws://192.168.1.50:8080/control", device.endpointUrl)
        assertEquals("http://192.168.1.50:8080", device.httpBaseUrl)
    }

    @Test
    fun testTvDeviceEndpointUrl() {
        val ipv4Device = com.mobplayer.remote.model.TvDevice(name = "TV IPv4", host = "192.168.1.50", port = 8080)
        assertEquals("ws://192.168.1.50:8080/control", ipv4Device.endpointUrl)

        val ipv6Device = com.mobplayer.remote.model.TvDevice(name = "TV IPv6", host = "fe80::1", port = 8080)
        assertEquals("ws://[fe80::1]:8080/control", ipv6Device.endpointUrl)

        val ipv6BracketedDevice = com.mobplayer.remote.model.TvDevice(name = "TV IPv6 Brackets", host = "[fe80::1]", port = 8080)
        assertEquals("ws://[fe80::1]:8080/control", ipv6BracketedDevice.endpointUrl)
    }
}
