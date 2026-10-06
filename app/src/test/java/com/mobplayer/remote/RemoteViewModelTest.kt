package com.mobplayer.remote

import com.mobplayer.remote.model.PlayerStatePayload
import com.mobplayer.remote.model.WebSocketMessage
import com.mobplayer.remote.network.ControlSocketRepository
import com.mobplayer.remote.viewmodel.RemoteViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
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

        viewModel.stop()
        // Method executes cleanly without error
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
}
