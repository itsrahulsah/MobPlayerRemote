package com.mobplayer.remote

import com.mobplayer.remote.model.LogDirection
import com.mobplayer.remote.model.PlayerStatePayload
import com.mobplayer.remote.model.WebSocketMessage
import com.mobplayer.remote.network.ControlSocketRepository
import com.mobplayer.remote.viewmodel.RemoteViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(JUnit4::class)
class RemoteViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val json = Json { ignoreUnknownKeys = true }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testVolumeSliderAndSeekState() = runTest(testDispatcher) {
        val testScope = this
        val socketRepo = ControlSocketRepository(testScope)
        val viewModel = RemoteViewModel(socketRepo)

        // Verify optimistic volume slider update
        viewModel.onVolumeSliderChanged(0.65f)
        val updatedState = viewModel.playerState.value
        assertNotNull(updatedState)
        assertEquals(0.65f, updatedState?.volume ?: 0f, 0.001f)

        // Verify relative volume adjustments
        viewModel.adjustVolumeRelative(0.10f)
        assertEquals(0.75f, viewModel.playerState.value?.volume ?: 0f, 0.001f)

        viewModel.adjustVolumeRelative(-0.25f)
        assertEquals(0.50f, viewModel.playerState.value?.volume ?: 0f, 0.001f)

        // Verify upper and lower bound clamping
        viewModel.adjustVolumeRelative(1.5f)
        assertEquals(1.0f, viewModel.playerState.value?.volume ?: 0f, 0.001f)

        viewModel.adjustVolumeRelative(-2.0f)
        assertEquals(0.0f, viewModel.playerState.value?.volume ?: 0f, 0.001f)
    }

    @Test
    fun testToggleMute() = runTest(testDispatcher) {
        val testScope = this
        val socketRepo = ControlSocketRepository(testScope)
        val viewModel = RemoteViewModel(socketRepo)

        viewModel.onVolumeSliderChanged(0.80f)
        viewModel.toggleMute()
        assertEquals(0.0f, viewModel.playerState.value?.volume ?: 1f, 0.001f)

        viewModel.toggleMute()
        assertEquals(0.80f, viewModel.playerState.value?.volume ?: 0f, 0.001f)
    }

    @Test
    fun testSeekClamping() = runTest(testDispatcher) {
        val testScope = this
        val socketRepo = ControlSocketRepository(testScope)
        val viewModel = RemoteViewModel(socketRepo)

        // Start with initial state: position = 5000ms, duration = 60000ms
        viewModel.onVolumeSliderChanged(0.5f)
        viewModel.seekTo(5000L)
        assertEquals(5000L, viewModel.playerState.value?.positionMs)

        // Seek negative -> clamped to 0
        viewModel.seekRelative(-10000L)
        assertEquals(0L, viewModel.playerState.value?.positionMs)

        // Seek forward
        viewModel.seekRelative(15000L)
        assertEquals(15000L, viewModel.playerState.value?.positionMs)
    }

    @Test
    fun testDpadAndNavigationCommands() = runTest(testDispatcher) {
        val testScope = this
        val socketRepo = ControlSocketRepository(testScope)
        val viewModel = RemoteViewModel(socketRepo)

        // Verify navigation methods run cleanly without throwing
        viewModel.sendDpadUp()
        viewModel.sendDpadDown()
        viewModel.sendDpadLeft()
        viewModel.sendDpadRight()
        viewModel.sendDpadCenter()
        viewModel.sendBack()
        viewModel.sendHome()
        viewModel.stop()
        assertFalse(viewModel.playerState.value?.isPlaying == true)
    }

    @Test
    fun testTrafficLoggingAndClearing() = runTest(testDispatcher) {
        val testScope = this
        val socketRepo = ControlSocketRepository(testScope)
        val viewModel = RemoteViewModel(socketRepo)

        socketRepo.logEvent(LogDirection.REQUEST, "COMMAND", "{\"action\":\"PLAY\"}")
        socketRepo.logEvent(LogDirection.RESPONSE, "COMMAND_SUCCESS", "PLAY")

        val currentLogs = viewModel.logs.value
        assertEquals(2, currentLogs.size)
        assertEquals(LogDirection.RESPONSE, currentLogs[0].direction)
        assertEquals("COMMAND_SUCCESS", currentLogs[0].type)
        assertEquals(LogDirection.REQUEST, currentLogs[1].direction)
        assertEquals("COMMAND", currentLogs[1].type)

        // Test clearing logs
        viewModel.clearLogs()
        assertTrue(viewModel.logs.value.isEmpty())
    }

    @Test
    fun testToggleMuteAndUnmute() = runTest(testDispatcher) {
        val testScope = this
        val socketRepo = ControlSocketRepository(testScope)
        val viewModel = RemoteViewModel(socketRepo)

        // Initial volume 0.70
        viewModel.onVolumeSliderChanged(0.70f)
        assertEquals(0.70f, viewModel.playerState.value?.volume ?: 0f, 0.001f)

        // Toggle mute -> should be 0.0f
        viewModel.toggleMute()
        assertEquals(0.0f, viewModel.playerState.value?.volume ?: 1f, 0.001f)

        // Toggle unmute -> should restore 0.70f
        viewModel.toggleMute()
        assertEquals(0.70f, viewModel.playerState.value?.volume ?: 0f, 0.001f)
    }

    @Test
    fun testSetPlaybackSpeedClamping() = runTest(testDispatcher) {
        val testScope = this
        val socketRepo = ControlSocketRepository(testScope)
        val viewModel = RemoteViewModel(socketRepo)

        // Normal speed
        viewModel.setPlaybackSpeed(1.5f)
        assertEquals(1.5f, viewModel.playerState.value?.speed ?: 1.0f, 0.001f)

        // Clamping upper bound (max 3.0f)
        viewModel.setPlaybackSpeed(5.0f)
        assertEquals(3.0f, viewModel.playerState.value?.speed ?: 1.0f, 0.001f)

        // Clamping lower bound (min 0.25f)
        viewModel.setPlaybackSpeed(0.1f)
        assertEquals(0.25f, viewModel.playerState.value?.speed ?: 1.0f, 0.001f)
    }

    @Test
    fun testSeekRelative30Seconds() = runTest(testDispatcher) {
        val testScope = this
        val socketRepo = ControlSocketRepository(testScope)
        val viewModel = RemoteViewModel(socketRepo)

        // Should not crash and should clamp to 0 if starting at 0
        viewModel.seekRelative(-30_000L)
        viewModel.seekRelative(30_000L)
    }

    @Test
    fun testStopCommandExecution() = runTest(testDispatcher) {
        val testScope = this
        val socketRepo = ControlSocketRepository(testScope)
        val viewModel = RemoteViewModel(socketRepo)

        // Set simulated playback state
        viewModel.onVolumeSliderChanged(0.5f)
        viewModel.seekTo(12000L)
        assertEquals(12000L, viewModel.playerState.value?.positionMs)

        // Calling stop must reset playing state and positionMs to 0
        viewModel.stop()
        assertEquals(false, viewModel.playerState.value?.isPlaying)
        assertEquals(0L, viewModel.playerState.value?.positionMs)
    }

    @Test
    fun testSendRawCommand() = runTest(testDispatcher) {
        val testScope = this
        val socketRepo = ControlSocketRepository(testScope)
        val viewModel = RemoteViewModel(socketRepo)

        // Valid raw command
        viewModel.sendRawCommand("COMMAND", "{\"action\":\"PLAY\"}")
        viewModel.sendRawCommand("CUSTOM_EVENT", "{\"payload\":\"test\"}")

        // Blank type boundary test: should be ignored safely
        viewModel.sendRawCommand("", "{}")
        viewModel.sendRawCommand("   ", "{}")
    }

    @Test
    fun testSendBackAndHomeNavigation() = runTest(testDispatcher) {
        val testScope = this
        val socketRepo = ControlSocketRepository(testScope)
        val viewModel = RemoteViewModel(socketRepo)

        viewModel.sendBack()
        viewModel.sendHome()
    }

    @Test
    fun testEventLogsAndClear() = runTest(testDispatcher) {
        val testScope = this
        val socketRepo = ControlSocketRepository(testScope)
        val viewModel = RemoteViewModel(socketRepo)

        assertTrue(viewModel.eventLogs.value.isEmpty())
        viewModel.clearLogs()
        assertTrue(viewModel.eventLogs.value.isEmpty())
    }

    @Test
    fun testUploadStateReset() = runTest(testDispatcher) {
        val testScope = this
        val socketRepo = ControlSocketRepository(testScope)
        val viewModel = RemoteViewModel(socketRepo)

        viewModel.resetUploadState()
        assertEquals(com.mobplayer.remote.model.UploadState.Idle, viewModel.uploadState.value)
    }

    @Test
    fun testSendTextInput() = runTest(testDispatcher) {
        val testScope = this
        val socketRepo = ControlSocketRepository(testScope)
        val viewModel = RemoteViewModel(socketRepo)

        viewModel.sendTextInput("Big Buck Bunny")
    }

    @Test
    fun testSendTextSubmitAndSearch() = runTest(testDispatcher) {
        val testScope = this
        val socketRepo = ControlSocketRepository(testScope)
        val viewModel = RemoteViewModel(socketRepo)

        // Valid query
        viewModel.sendTextSubmit("relaxing music")
        viewModel.search("4K nature")

        // Blank query boundary condition: should not dispatch
        viewModel.sendTextSubmit("")
        viewModel.sendTextSubmit("   ")
        viewModel.search("")
    }

    @Test
    fun testLoadMediaCommand() = runTest(testDispatcher) {
        val testScope = this
        val socketRepo = ControlSocketRepository(testScope)
        val viewModel = RemoteViewModel(socketRepo)

        viewModel.loadMedia("https://example.com/video.mp4", "Sample Video")
        viewModel.loadMedia("") // Blank URL boundary test
    }
}
