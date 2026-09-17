package com.mobplayer.remote.ui

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mobplayer.remote.model.TvDevice
import com.mobplayer.remote.viewmodel.RemoteViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteControlScreen(
    device: TvDevice,
    viewModel: RemoteViewModel,
    onDisconnect: () -> Unit
) {
    val state by viewModel.playerState.collectAsState()
    val haptics = LocalHapticFeedback.current
    var showLoadMediaDialog by remember { mutableStateOf(false) }

    fun triggerHaptic() {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(device.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = if (state?.isPlaying == true) "Playing" else "Connected",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (state?.isPlaying == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showLoadMediaDialog = true }) {
                        Icon(Icons.Default.Link, contentDescription = "Load Video URL")
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

            // Playback scrubber section
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

            // Quick Transport Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(onClick = {
                    triggerHaptic()
                    viewModel.seekRelative(-10_000L)
                }) {
                    Icon(Icons.Default.FastRewind, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("-10s")
                }

                Button(onClick = {
                    triggerHaptic()
                    viewModel.togglePlayPause()
                }) {
                    Icon(
                        if (state?.isPlaying == true) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (state?.isPlaying == true) "Pause" else "Play")
                }

                OutlinedButton(onClick = {
                    triggerHaptic()
                    viewModel.seekRelative(10_000L)
                }) {
                    Text("+10s")
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(Icons.Default.FastForward, contentDescription = null)
                }
            }

            // Volume Control Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
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
                            text = "${(vol * 100).toInt()}%",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            triggerHaptic()
                            viewModel.onVolumeSliderChanged(0f)
                        }) {
                            Icon(Icons.Default.VolumeMute, contentDescription = "Mute")
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
