package com.mobplayer.remote

import com.mobplayer.remote.network.UploadProgress
import com.mobplayer.remote.network.UploadResult
import com.mobplayer.remote.network.VideoUploadService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoUploadServiceTest {

    @Test
    fun testUploadProgressModel() {
        val progress = UploadProgress(
            bytesSent = 50_000_000L,
            totalBytes = 100_000_000L,
            fraction = 0.5f
        )
        assertEquals(50_000_000L, progress.bytesSent)
        assertEquals(100_000_000L, progress.totalBytes)
        assertEquals(0.5f, progress.fraction, 0.001f)
    }

    @Test
    fun testUploadResultTypes() {
        val progressResult = UploadResult.Progress(
            progress = UploadProgress(100L, 200L, 0.5f),
            fileName = "movie.mp4"
        )
        assertEquals("movie.mp4", progressResult.fileName)
        assertEquals(0.5f, progressResult.progress.fraction, 0.001f)

        val successResult = UploadResult.Success(
            fileName = "movie.mp4",
            response = "{\"status\":\"ok\"}"
        )
        assertEquals("movie.mp4", successResult.fileName)
        assertTrue(successResult.response.contains("status"))

        val failureResult = UploadResult.Failure(error = "Connection timeout")
        assertEquals("Connection timeout", failureResult.error)
    }

    @Test
    fun testVideoUploadServiceInitialization() {
        val service = VideoUploadService()
        assertNotNull(service)
    }
}
