package com.mobplayer.remote.network

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.onUpload
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.InputStream

data class UploadProgress(
    val bytesSent: Long,
    val totalBytes: Long,
    val fraction: Float
)

sealed interface UploadResult {
    data class Progress(val progress: UploadProgress, val fileName: String) : UploadResult
    data class Success(val fileName: String, val response: String) : UploadResult
    data class Failure(val error: String) : UploadResult
}

class VideoUploadService(
    private val client: HttpClient = HttpClient(CIO)
) {
    fun uploadVideo(
        context: Context,
        serverUrl: String,
        fileUri: Uri
    ): Flow<UploadResult> = flow {
        val fileName = getFileName(context, fileUri) ?: "video_${System.currentTimeMillis()}.mp4"
        val fileSize = getFileSize(context, fileUri)

        emit(UploadResult.Progress(UploadProgress(0L, fileSize, 0f), fileName))

        val inputStream: InputStream = context.contentResolver.openInputStream(fileUri)
            ?: throw IllegalStateException("Cannot read file from URI: $fileUri")

        try {
            val bytes = inputStream.use { it.readBytes() }

            val response = client.post("$serverUrl/upload") {
                setBody(
                    MultiPartFormDataContent(
                        formData {
                            append("video", bytes, Headers.build {
                                append(HttpHeaders.ContentType, "video/*")
                                append(HttpHeaders.ContentDisposition, "filename=\"$fileName\"")
                            })
                        }
                    )
                )
            onUpload { bytesSentTotal, contentLength ->
                // Progress is monitored during chunk streaming
            }
            }

            if (response.status == HttpStatusCode.OK || response.status == HttpStatusCode.Created) {
                emit(UploadResult.Success(fileName, response.bodyAsText()))
            } else {
                emit(UploadResult.Failure("Server returned ${response.status.value}: ${response.status.description}"))
            }
        } catch (e: Exception) {
            emit(UploadResult.Failure(e.localizedMessage ?: "Unknown upload error"))
        }
    }.flowOn(Dispatchers.IO)

    fun getFileName(context: Context, uri: Uri): String? {
        var name: String? = null
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && nameIndex >= 0) {
                    name = cursor.getString(nameIndex)
                }
            }
        } catch (_: Exception) {
        }
        return name
    }

    fun getFileSize(context: Context, uri: Uri): Long {
        var size: Long = 0L
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst() && sizeIndex >= 0) {
                    size = cursor.getLong(sizeIndex)
                }
            }
        } catch (_: Exception) {
        }
        return size
    }
}
