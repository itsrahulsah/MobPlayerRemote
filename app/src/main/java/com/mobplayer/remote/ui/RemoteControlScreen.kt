package com.mobplayer.remote.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobplayer.remote.model.LogDirection
import com.mobplayer.remote.model.LogEvent
import com.mobplayer.remote.model.TvDevice
import com.mobplayer.remote.model.UploadState
import com.mobplayer.remote.viewmodel.RemoteViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteControlScreen(
    device: TvDevice,
    viewModel: RemoteViewModel,
    onDisconnect: () -> Unit
) {
    val state by viewModel.playerState.collectAsState()
    val uploadState by viewModel.uploadState.collectAsState()
    val eventLogs by viewModel.eventLogs.collectAsState()
    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current

    var showLoadMediaDialog by remember { mutableStateOf(false) }
    var showDiagnosticsSheet by remember { mutableStateOf(false) }

    fun triggerHaptic() {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    // Video File Picker Launcher
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            triggerHaptic()
            viewModel.uploadVideo(context, device.httpBaseUrl, uri)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(device.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = if (state?.isPlaying == true) "Playing (${state?.speed ?: 1.0f}x)" else "Connected",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (state?.isPlaying == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { videoPickerLauncher.launch("video/*") }) {
                        Icon(Icons.Default.CloudUpload, contentDescription = "Upload Video to TV")
                    }
                    IconButton(onClick = { showLoadMediaDialog = true }) {
                        Icon(Icons.Default.Link, contentDescription = "Load Video URL")
                    }
                    IconButton(onClick = { showDiagnosticsSheet = true }) {
                        Icon(Icons.Default.BugReport, contentDescription = "Live Diagnostics")
                    }
                    IconButton(onClick = {
                        triggerHaptic()
                        onDisconnect()
                    }) {
                        Icon(Icons.Default.PowerSettingsNew, contentDescription = "Disconnect")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // Upload Status Card (Active / Completed / Failed)
            UploadStatusCard(
                uploadState = uploadState,
                onCancel = { viewModel.cancelUpload(context) },
                onDismiss = { viewModel.resetUploadState() }
            )

            // Playback Scrubber Section
            val currentPos = state?.positionMs ?: 0L
            val totalDuration = state?.durationMs ?: 0L
            var isUserSeeking by remember { mutableStateOf(false) }
            var seekSliderValue by remember { mutableFloatStateOf(0f) }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Slider(
                        value = if (isUserSeeking) seekSliderValue else currentPos.toFloat(),
                        onValueChange = {
                            isUserSeeking = true
                            seekSliderValue = it
                        },
                        onValueChangeFinished = {
                            isUserSeeking = false
                            viewModel.seekTo(seekSliderValue.toLong())
                            triggerHaptic()
                        },
                        valueRange = 0f..maxOf(1f, totalDuration.toFloat()),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatTime(if (isUserSeeking) seekSliderValue.toLong() else currentPos),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = formatTime(totalDuration),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            // D-Pad Controller Section
            Box(
                modifier = Modifier
                    .size(240.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                // Top Button (Volume Up)
                IconButton(
                    onClick = {
                        triggerHaptic()
                        viewModel.adjustVolumeRelative(0.05f)
                    },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 10.dp)
                ) {
                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Volume Up", modifier = Modifier.size(32.dp))
                }

                // Bottom Button (Volume Down)
                IconButton(
                    onClick = {
                        triggerHaptic()
                        viewModel.adjustVolumeRelative(-0.05f)
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 10.dp)
                ) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Volume Down", modifier = Modifier.size(32.dp))
                }

                // Left Button (Seek -10s)
                IconButton(
                    onClick = {
                        triggerHaptic()
                        viewModel.seekRelative(-10_000L)
                    },
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 10.dp)
                ) {
                    Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "Seek -10s", modifier = Modifier.size(32.dp))
                }

                // Right Button (Seek +10s)
                IconButton(
                    onClick = {
                        triggerHaptic()
                        viewModel.seekRelative(10_000L)
                    },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 10.dp)
                ) {
                    Icon(Icons.Default.KeyboardArrowRight, contentDescription = "Seek +10s", modifier = Modifier.size(32.dp))
                }

                // Center Button (Play / Pause toggle)
                FilledIconButton(
                    onClick = {
                        triggerHaptic()
                        viewModel.togglePlayPause()
                    },
                    modifier = Modifier.size(72.dp),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        if (state?.isPlaying == true) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Toggle Play/Pause",
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            // Extended Transport Buttons (-30s, Stop, Play/Pause, +30s)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(onClick = {
                    triggerHaptic()
                    viewModel.seekRelative(-30_000L)
                }) {
                    Text("-30s")
                }

                OutlinedButton(onClick = {
                    triggerHaptic()
                    viewModel.seekRelative(-10_000L)
                }) {
                    Text("-10s")
                }

                Button(
                    onClick = {
                        triggerHaptic()
                        viewModel.stop()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.Stop, contentDescription = "Stop")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Stop")
                }

                OutlinedButton(onClick = {
                    triggerHaptic()
                    viewModel.seekRelative(10_000L)
                }) {
                    Text("+10s")
                }

                OutlinedButton(onClick = {
                    triggerHaptic()
                    viewModel.seekRelative(30_000L)
                }) {
                    Text("+30s")
                }
            }

            // Playback Speed Selector Chips
            val currentSpeed = state?.speed ?: 1.0f
            val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                speeds.forEach { speed ->
                    val isSelected = Math.abs(currentSpeed - speed) < 0.05f
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            triggerHaptic()
                            viewModel.setPlaybackSpeed(speed)
                        },
                        label = { Text("${speed}x", style = MaterialTheme.typography.labelSmall) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }
            }

            // Volume Control Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Volume",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        val vol = state?.volume ?: 0.5f
                        Text(
                            text = if (vol <= 0.001f) "Muted" else "${(vol * 100).toInt()}%",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (vol <= 0.001f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val currentVol = state?.volume ?: 0.5f
                        val isMuted = currentVol <= 0.001f

                        IconButton(onClick = {
                            triggerHaptic()
                            viewModel.toggleMute()
                        }) {
                            Icon(
                                if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeMute,
                                contentDescription = if (isMuted) "Unmute" else "Mute",
                                tint = if (isMuted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Slider(
                            value = state?.volume ?: 0.5f,
                            onValueChange = { viewModel.onVolumeSliderChanged(it) },
                            valueRange = 0f..1f,
                            modifier = Modifier.weight(1f)
                        )

                        IconButton(onClick = {
                            triggerHaptic()
                            viewModel.onVolumeSliderChanged(1f)
                        }) {
                            Icon(Icons.Default.VolumeUp, contentDescription = "Max Volume")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showLoadMediaDialog) {
        LoadMediaDialog(
            onDismiss = { showLoadMediaDialog = false },
            onLoad = { url ->
                viewModel.loadMedia(url)
                showLoadMediaDialog = false
            }
        )
    }

    if (showDiagnosticsSheet) {
        DiagnosticsBottomSheet(
            eventLogs = eventLogs,
            onDismiss = { showDiagnosticsSheet = false },
            onSendRawCommand = { type, payload -> viewModel.sendRawCommand(type, payload) },
            onClearLogs = { viewModel.clearLogs() }
        )
    }
}

@Composable
private fun UploadStatusCard(
    uploadState: UploadState,
    onCancel: () -> Unit,
    onDismiss: () -> Unit
) {
    when (uploadState) {
        is UploadState.Uploading -> {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Uploading: ${uploadState.fileName}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${(uploadState.progress * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { uploadState.progress },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val mbUploaded = uploadState.bytesUploaded / (1024 * 1024)
                        val mbTotal = uploadState.totalBytes / (1024 * 1024)
                        Text(
                            text = "$mbUploaded MB / $mbTotal MB",
                            style = MaterialTheme.typography.labelSmall
                        )
                        TextButton(onClick = onCancel) {
                            Text("Cancel", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
        is UploadState.Success -> {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Uploaded Successfully", fontWeight = FontWeight.Bold)
                        Text(uploadState.fileName, style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = onDismiss) {
                        Text("Dismiss")
                    }
                }
            }
        }
        is UploadState.Error -> {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Upload Failed", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                        Text(uploadState.errorMessage, style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = onDismiss) {
                        Text("Dismiss")
                    }
                }
            }
        }
        is UploadState.Idle -> Unit
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DiagnosticsBottomSheet(
    eventLogs: List<LogEvent>,
    onDismiss: () -> Unit,
    onSendRawCommand: (String, String) -> Unit,
    onClearLogs: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val listState = rememberLazyListState()
    var rawType by remember { mutableStateOf("COMMAND") }
    var rawPayload by remember { mutableStateOf("{\"action\":\"PLAY\"}") }

    LaunchedEffect(eventLogs.size) {
        if (eventLogs.isNotEmpty()) {
            listState.animateScrollToItem(eventLogs.size - 1)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Live Diagnostics & Console",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                TextButton(onClick = onClearLogs) {
                    Text("Clear Logs")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Raw Command Dispatcher
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Send Raw WebSocket Frame", style = MaterialTheme.typography.labelSmall)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = rawType,
                            onValueChange = { rawType = it },
                            label = { Text("Type") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = rawPayload,
                            onValueChange = { rawPayload = it },
                            label = { Text("Payload") },
                            modifier = Modifier.weight(2f),
                            singleLine = true
                        )
                        IconButton(
                            onClick = {
                                onSendRawCommand(rawType, rawPayload)
                            },
                            modifier = Modifier.align(Alignment.CenterVertically)
                        ) {
                            Icon(Icons.Default.Send, contentDescription = "Send")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Log Console Window
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                if (eventLogs.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No traffic events logged yet", style = MaterialTheme.typography.bodySmall)
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp)
                    ) {
                        items(eventLogs, key = { it.id }) { log ->
                            LogItemRow(log = log)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LogItemRow(log: LogEvent) {
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()) }
    val timeStr = remember(log.timestamp) { timeFormat.format(Date(log.timestamp)) }

    val color = when (log.direction) {
        LogDirection.INCOMING -> MaterialTheme.colorScheme.primary
        LogDirection.OUTGOING -> MaterialTheme.colorScheme.tertiary
        LogDirection.ERROR -> MaterialTheme.colorScheme.error
        LogDirection.SYSTEM -> MaterialTheme.colorScheme.outline
    }

    val dirSymbol = when (log.direction) {
        LogDirection.INCOMING -> "◄ [RX]"
        LogDirection.OUTGOING -> "► [TX]"
        LogDirection.ERROR -> "✖ [ERR]"
        LogDirection.SYSTEM -> "ℹ [SYS]"
    }

    Column(modifier = Modifier.padding(vertical = 2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "$dirSymbol ${log.tag}",
                color = color,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = timeStr,
                color = MaterialTheme.colorScheme.outline,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp
            )
        }
        Text(
            text = log.message,
            color = MaterialTheme.colorScheme.onSurface,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp
        )
    }
}

@Composable
fun LoadMediaDialog(
    onDismiss: () -> Unit,
    onLoad: (String) -> Unit
) {
    var urlInput by remember {
        mutableStateOf("https://storage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Stream Media to TV") },
        text = {
            Column {
                Text("Enter a direct video URL (MP4 / HLS):")
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    label = { Text("Video URL") },
                    singleLine = false,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("Quick Samples:", style = MaterialTheme.typography.labelSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SuggestionChip(
                        onClick = {
                            urlInput = "https://storage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"
                        },
                        label = { Text("Big Buck Bunny") }
                    )
                    SuggestionChip(
                        onClick = {
                            urlInput = "https://storage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4"
                        },
                        label = { Text("Elephant's Dream") }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onLoad(urlInput) },
                enabled = urlInput.isNotBlank()
            ) {
                Text("Play on TV")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun formatTime(millis: Long): String {
    if (millis <= 0) return "00:00"
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val hours = minutes / 60
    return if (hours > 0) {
        String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes % 60, seconds)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }
}
