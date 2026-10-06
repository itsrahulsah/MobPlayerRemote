package com.mobplayer.remote.network

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.mobplayer.remote.MainActivity
import com.mobplayer.remote.model.UploadState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MediaUploadForegroundService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var uploadJob: Job? = null
    private val uploadService = VideoUploadService()
    private lateinit var notificationManager: NotificationManager

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL -> {
                cancelUpload()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            ACTION_START_UPLOAD -> {
                val serverUrl = intent.getStringExtra(EXTRA_SERVER_URL) ?: ""
                val fileUriString = intent.getStringExtra(EXTRA_FILE_URI) ?: ""
                val fileUri = Uri.parse(fileUriString)

                if (serverUrl.isNotBlank() && fileUriString.isNotBlank()) {
                    startUpload(serverUrl, fileUri)
                } else {
                    stopSelf()
                }
            }
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startUpload(serverUrl: String, fileUri: Uri) {
        uploadJob?.cancel()

        val initialNotification = buildNotification(
            title = "Uploading video to MobPlayer TV",
            content = "Preparing upload...",
            progress = 0,
            isOngoing = true
        )
        startForeground(NOTIFICATION_ID, initialNotification)

        uploadJob = serviceScope.launch {
            uploadService.uploadVideo(applicationContext, serverUrl, fileUri).collect { result ->
                when (result) {
                    is UploadResult.Progress -> {
                        val progressPercent = (result.progress.fraction * 100).toInt()
                        _serviceUploadState.value = UploadState.Uploading(
                            progress = result.progress.fraction,
                            bytesUploaded = result.progress.bytesSent,
                            totalBytes = result.progress.totalBytes,
                            fileName = result.fileName
                        )
                        val updatedNotification = buildNotification(
                            title = "Uploading to MobPlayer TV",
                            content = "${result.fileName} ($progressPercent%)",
                            progress = progressPercent,
                            isOngoing = true
                        )
                        notificationManager.notify(NOTIFICATION_ID, updatedNotification)
                    }

                    is UploadResult.Success -> {
                        _serviceUploadState.value = UploadState.Success(
                            fileName = result.fileName,
                            message = "Upload complete"
                        )
                        val completeNotification = buildNotification(
                            title = "Upload Complete",
                            content = "${result.fileName} uploaded to TV",
                            progress = 100,
                            isOngoing = false
                        )
                        notificationManager.notify(NOTIFICATION_ID, completeNotification)
                        stopForeground(STOP_FOREGROUND_DETACH)
                        stopSelf()
                    }

                    is UploadResult.Failure -> {
                        _serviceUploadState.value = UploadState.Error(result.error)
                        val errorNotification = buildNotification(
                            title = "Upload Failed",
                            content = result.error,
                            progress = 0,
                            isOngoing = false
                        )
                        notificationManager.notify(NOTIFICATION_ID, errorNotification)
                        stopForeground(STOP_FOREGROUND_DETACH)
                        stopSelf()
                    }
                }
            }
        }
    }

    private fun cancelUpload() {
        uploadJob?.cancel()
        uploadJob = null
        _serviceUploadState.value = UploadState.Idle
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Media Upload Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows real-time progress for video uploads to MobPlayer TV"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(
        title: String,
        content: String,
        progress: Int,
        isOngoing: Boolean
    ) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle(title)
        .setContentText(content)
        .setSmallIcon(android.R.drawable.stat_sys_upload)
        .setOngoing(isOngoing)
        .setProgress(100, progress, progress == 0 && isOngoing)
        .setContentIntent(
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        )
        .apply {
            if (isOngoing) {
                val cancelIntent = Intent(this@MediaUploadForegroundService, MediaUploadForegroundService::class.java).apply {
                    action = ACTION_CANCEL
                }
                val cancelPendingIntent = PendingIntent.getService(
                    this@MediaUploadForegroundService,
                    1,
                    cancelIntent,
                    PendingIntent.FLAG_IMMUTABLE
                )
                addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", cancelPendingIntent)
            }
        }
        .build()

    override fun onDestroy() {
        super.onDestroy()
        uploadJob?.cancel()
    }

    companion object {
        const val CHANNEL_ID = "mobplayer_media_upload_channel"
        const val NOTIFICATION_ID = 2001

        const val ACTION_START_UPLOAD = "com.mobplayer.remote.action.START_UPLOAD"
        const val ACTION_CANCEL = "com.mobplayer.remote.action.CANCEL_UPLOAD"

        const val EXTRA_SERVER_URL = "extra_server_url"
        const val EXTRA_FILE_URI = "extra_file_uri"

        private val _serviceUploadState = MutableStateFlow<UploadState>(UploadState.Idle)
        val serviceUploadState: StateFlow<UploadState> = _serviceUploadState.asStateFlow()

        fun startUpload(context: Context, serverUrl: String, fileUri: Uri) {
            val intent = Intent(context, MediaUploadForegroundService::class.java).apply {
                action = ACTION_START_UPLOAD
                putExtra(EXTRA_SERVER_URL, serverUrl)
                putExtra(EXTRA_FILE_URI, fileUri.toString())
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun cancel(context: Context) {
            val intent = Intent(context, MediaUploadForegroundService::class.java).apply {
                action = ACTION_CANCEL
            }
            context.startService(intent)
        }

        fun resetState() {
            _serviceUploadState.value = UploadState.Idle
        }
    }
}
