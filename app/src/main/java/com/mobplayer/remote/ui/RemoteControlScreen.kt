package com.mobplayer.remote.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobplayer.remote.model.LogDirection
import com.mobplayer.remote.model.PlayerStatePayload
import com.mobplayer.remote.model.RemoteLogEntry
import com.mobplayer.remote.model.TvDevice
import com.mobplayer.remote.network.ConnectionState
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
    val connectionState by viewModel.connectionState.collectAsState()
    val logs by viewModel.logs.collectAsState()
    val haptics = LocalHapticFeedback.current

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Remote Pad, 1: Player, 2: TV Logs
    var showLoadMediaDialog by remember { mutableStateOf(false) }

    fun triggerHaptic() {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    val isConnected = connectionState is ConnectionState.Connected
    val isConnecting = connectionState is ConnectionState.Connecting

    val statusColor by animateColorAsState(
        targetValue = when {
            isConnected -> Color(0xFF4CAF50)
            isConnecting -> Color(0xFFFFC107)
            else -> Color(0xFFF44336)
        },
        label = "StatusColor"
    )

    val statusText = when {
        isConnecting -> "Connecting..."
        isConnected -> if (state?.isPlaying == true) "Playing" else "Connected"
        connectionState is ConnectionState.Error -> "Connection Error"
        else -> "Disconnected"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(statusColor, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = device.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = "${device.host}:${device.port} • $statusText",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    // Quick Load Media Dialog
                    IconButton(onClick = {
                        triggerHaptic()
                        showLoadMediaDialog = true
                    }) {
                        Icon(Icons.Default.Link, contentDescription = "Stream Video URL")
                    }

                    // Power / Disconnect
                    IconButton(onClick = {
                        triggerHaptic()
                        onDisconnect()
                    }) {
                        Icon(
                            Icons.Default.PowerSettingsNew,
                            contentDescription = "Disconnect",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Reconnect alert banner if not connected
            AnimatedVisibility(visible = !isConnected) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (isConnecting) "Reconnecting to TV..." else "Socket disconnected from TV",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    FilledTonalButton(
                        onClick = {
                            triggerHaptic()
                            viewModel.reconnect()
                        },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Reconnect", fontSize = 12.sp)
                    }
                }
            }

            // Tab Selector
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = {
                        triggerHaptic()
                        selectedTab = 0
                    },
                    icon = { Icon(Icons.Default.Navigation, contentDescription = null, modifier = Modifier.size(20.dp)) },
                    text = { Text("Remote Pad") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = {
                        triggerHaptic()
                        selectedTab = 1
                    },
                    icon = { Icon(Icons.Default.Movie, contentDescription = null, modifier = Modifier.size(20.dp)) },
                    text = { Text("Player") }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = {
                        triggerHaptic()
                        selectedTab = 2
                    },
                    icon = {
                        BadgedBox(
                            badge = {
                                if (logs.isNotEmpty()) {
                                    Badge { Text(logs.size.toString()) }
                                }
                            }
                        ) {
                            Icon(Icons.Default.Terminal, contentDescription = null, modifier = Modifier.size(20.dp))
                        }
                    },
                    text = { Text("TV Logs") }
                )
            }

            // Tab Contents
            Box(modifier = Modifier.fillMaxSize()) {
                when (selectedTab) {
                    0 -> RemotePadTabContent(
                        state = state,
                        viewModel = viewModel,
                        onTriggerHaptic = ::triggerHaptic
                    )
                    1 -> MediaPlayerTabContent(
                        state = state,
                        viewModel = viewModel,
                        onTriggerHaptic = ::triggerHaptic,
                        onOpenLoadDialog = { showLoadMediaDialog = true }
                    )
                    2 -> TvLogsTabContent(
                        logs = logs,
                        onClearLogs = { viewModel.clearLogs() },
                        onTriggerHaptic = ::triggerHaptic
                    )
                }
            }
        }
    }

    if (showLoadMediaDialog) {
        LoadMediaDialog(
            onDismiss = { showLoadMediaDialog = false },
            onLoad = { url, title ->
                viewModel.loadMedia(url, title)
                showLoadMediaDialog = false
            }
        )
    }
}

/**
 * Tab 0: Modern Tactical D-Pad & TV Navigation
 */
@Composable
private fun RemotePadTabContent(
    state: PlayerStatePayload?,
    viewModel: RemoteViewModel,
    onTriggerHaptic: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Navigation Action Buttons (Back, Home, Stop)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            OutlinedButton(
                onClick = {
                    onTriggerHaptic()
                    viewModel.sendBack()
                },
                modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                Spacer(modifier = Modifier.width(4.dp))
                Text("Back")
            }

            OutlinedButton(
                onClick = {
                    onTriggerHaptic()
                    viewModel.sendHome()
                },
                modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Home, contentDescription = "Home")
                Spacer(modifier = Modifier.width(4.dp))
                Text("Home")
            }

            OutlinedButton(
                onClick = {
                    onTriggerHaptic()
                    viewModel.stop()
                },
                modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(Icons.Default.Stop, contentDescription = "Stop")
                Spacer(modifier = Modifier.width(4.dp))
                Text("Stop")
            }
        }

        // Circular D-Pad Controller
        Box(
            modifier = Modifier
                .size(260.dp)
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    shape = CircleShape
                )
                .border(2.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            // UP
            IconButton(
                onClick = {
                    onTriggerHaptic()
                    viewModel.sendDpadUp()
                },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp)
                    .size(56.dp)
            ) {
                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Up", modifier = Modifier.size(36.dp))
            }

            // DOWN
            IconButton(
                onClick = {
                    onTriggerHaptic()
                    viewModel.sendDpadDown()
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp)
                    .size(56.dp)
            ) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Down", modifier = Modifier.size(36.dp))
            }

            // LEFT
            IconButton(
                onClick = {
                    onTriggerHaptic()
                    viewModel.sendDpadLeft()
                },
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 12.dp)
                    .size(56.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Left", modifier = Modifier.size(36.dp))
            }

            // RIGHT
            IconButton(
                onClick = {
                    onTriggerHaptic()
                    viewModel.sendDpadRight()
                },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 12.dp)
                    .size(56.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Right", modifier = Modifier.size(36.dp))
            }

            // CENTER OK / SELECT
            FilledIconButton(
                onClick = {
                    onTriggerHaptic()
                    viewModel.sendDpadCenter()
                },
                modifier = Modifier.size(80.dp),
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    text = "OK",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }

        // Quick Volume Strip
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = {
                    onTriggerHaptic()
                    viewModel.adjustVolumeRelative(-0.05f)
                }) {
                    Icon(Icons.AutoMirrored.Filled.VolumeDown, contentDescription = "Volume Down")
                }

                FilledTonalButton(
                    onClick = {
                        onTriggerHaptic()
                        viewModel.toggleMute()
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    val vol = state?.volume ?: 0.5f
                    val isMuted = vol <= 0.01f
                    Icon(
                        if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeMute,
                        contentDescription = "Mute"
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (isMuted) "Muted" else "${(vol * 100).toInt()}%")
                }

                IconButton(onClick = {
                    onTriggerHaptic()
                    viewModel.adjustVolumeRelative(0.05f)
                }) {
                    Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Volume Up")
                }
            }
        }

        // Quick Transport Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = {
                    onTriggerHaptic()
                    viewModel.seekRelative(-10_000L)
                },
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.FastRewind, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("-10s")
            }

            Button(
                onClick = {
                    onTriggerHaptic()
                    viewModel.togglePlayPause()
                },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    if (state?.isPlaying == true) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (state?.isPlaying == true) "Pause" else "Play")
            }

            OutlinedButton(
                onClick = {
                    onTriggerHaptic()
                    viewModel.seekRelative(10_000L)
                },
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("+10s")
                Spacer(modifier = Modifier.width(4.dp))
                Icon(Icons.Default.FastForward, contentDescription = null)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

/**
 * Tab 1: Enhanced Media Player & Scrubber Control
 */
@Composable
private fun MediaPlayerTabContent(
    state: PlayerStatePayload?,
    viewModel: RemoteViewModel,
    onTriggerHaptic: () -> Unit,
    onOpenLoadDialog: () -> Unit
) {
    val currentPos = (state?.positionMs ?: 0L).coerceAtLeast(0L)
    val totalDuration = (state?.durationMs ?: 0L).coerceAtLeast(0L)
    val maxSliderRange = maxOf(1f, totalDuration.toFloat())

    var isUserSeeking by remember { mutableStateOf(false) }
    var seekSliderValue by remember { mutableFloatStateOf(0f) }

    // Crucial bugfix: always coerce into bounds to prevent Compose Slider crashes!
    val rawSliderValue = if (isUserSeeking) seekSliderValue else currentPos.toFloat()
    val safeSliderValue = rawSliderValue.coerceIn(0f, maxSliderRange)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Status & Scrubber Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "TV Media Stream",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Box(
                        modifier = Modifier
                            .background(
                                color = if (state?.isPlaying == true) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (state?.isPlaying == true) "Playing" else "Paused",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (state?.isPlaying == true) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Seek Slider with safe range clamping
                Slider(
                    value = safeSliderValue,
                    onValueChange = {
                        isUserSeeking = true
                        seekSliderValue = it.coerceIn(0f, maxSliderRange)
                    },
                    onValueChangeFinished = {
                        isUserSeeking = false
                        viewModel.seekTo(seekSliderValue.toLong())
                        onTriggerHaptic()
                    },
                    valueRange = 0f..maxSliderRange,
                    enabled = totalDuration > 0L,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatTime(if (isUserSeeking) seekSliderValue.toLong() else currentPos),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = formatTime(totalDuration),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        // Transport Controls Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledIconButton(
                onClick = {
                    onTriggerHaptic()
                    viewModel.seekRelative(-10_000L)
                },
                modifier = Modifier.size(52.dp),
                shape = CircleShape
            ) {
                Icon(Icons.Default.FastRewind, contentDescription = "Rewind 10s")
            }

            FilledIconButton(
                onClick = {
                    onTriggerHaptic()
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
                    contentDescription = "Play/Pause",
                    modifier = Modifier.size(36.dp)
                )
            }

            FilledIconButton(
                onClick = {
                    onTriggerHaptic()
                    viewModel.seekRelative(10_000L)
                },
                modifier = Modifier.size(52.dp),
                shape = CircleShape
            ) {
                Icon(Icons.Default.FastForward, contentDescription = "Forward 10s")
            }

            FilledIconButton(
                onClick = {
                    onTriggerHaptic()
                    viewModel.stop()
                },
                modifier = Modifier.size(52.dp),
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            ) {
                Icon(Icons.Default.Stop, contentDescription = "Stop")
            }
        }

        // Full Volume Control Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
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
                    val vol = (state?.volume ?: 0.5f).coerceIn(0f, 1f)
                    Text(
                        text = "${(vol * 100).toInt()}%",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        onTriggerHaptic()
                        viewModel.onVolumeSliderChanged(0f)
                    }) {
                        Icon(Icons.AutoMirrored.Filled.VolumeMute, contentDescription = "Mute")
                    }

                    val safeVol = (state?.volume ?: 0.5f).coerceIn(0f, 1f)
                    Slider(
                        value = safeVol,
                        onValueChange = { viewModel.onVolumeSliderChanged(it) },
                        valueRange = 0f..1f,
                        modifier = Modifier.weight(1f)
                    )

                    IconButton(onClick = {
                        onTriggerHaptic()
                        viewModel.onVolumeSliderChanged(1f)
                    }) {
                        Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Max Volume")
                    }
                }
            }
        }

        // Quick Media Launcher Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Quick Stream Samples",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    OutlinedButton(
                        onClick = onOpenLoadDialog,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Text("Custom URL", fontSize = 11.sp)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SuggestionChip(
                        onClick = {
                            onTriggerHaptic()
                            viewModel.loadMedia(
                                "https://storage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
                                "Big Buck Bunny"
                            )
                        },
                        label = { Text("🐰 Big Buck Bunny") }
                    )
                    SuggestionChip(
                        onClick = {
                            onTriggerHaptic()
                            viewModel.loadMedia(
                                "https://storage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4",
                                "Elephant's Dream"
                            )
                        },
                        label = { Text("🐘 Elephant's Dream") }
                    )
                    SuggestionChip(
                        onClick = {
                            onTriggerHaptic()
                            viewModel.loadMedia(
                                "https://storage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4",
                                "Tears of Steel"
                            )
                        },
                        label = { Text("🚀 Tears of Steel") }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

/**
 * Tab 2: Real-time Live Remote Request & Response TV Logs
 */
@Composable
private fun TvLogsTabContent(
    logs: List<RemoteLogEntry>,
    onClearLogs: () -> Unit,
    onTriggerHaptic: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    var selectedFilter by remember { mutableStateOf<LogDirection?>(null) }

    val filteredLogs = remember(logs, selectedFilter) {
        if (selectedFilter == null) logs else logs.filter { it.direction == selectedFilter }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Log Actions & Filters Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Live Traffic (${filteredLogs.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        onTriggerHaptic()
                        val text = filteredLogs.joinToString("\n") {
                            "[${it.timestamp}] [${it.direction}] ${it.type} -> ${it.payload}"
                        }
                        clipboardManager.setText(AnnotatedString(text))
                        Toast.makeText(context, "Logs copied to clipboard", Toast.LENGTH_SHORT).show()
                    },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text("Copy", fontSize = 12.sp)
                }

                IconButton(
                    onClick = {
                        onTriggerHaptic()
                        onClearLogs()
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = "Clear Logs", modifier = Modifier.size(20.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Filter Chips Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedFilter == null,
                onClick = { selectedFilter = null },
                label = { Text("All (${logs.size})") }
            )
            FilterChip(
                selected = selectedFilter == LogDirection.REQUEST,
                onClick = { selectedFilter = if (selectedFilter == LogDirection.REQUEST) null else LogDirection.REQUEST },
                label = { Text("Requests ⬆️ (${logs.count { it.direction == LogDirection.REQUEST }})") }
            )
            FilterChip(
                selected = selectedFilter == LogDirection.RESPONSE,
                onClick = { selectedFilter = if (selectedFilter == LogDirection.RESPONSE) null else LogDirection.RESPONSE },
                label = { Text("Responses ⬇️ (${logs.count { it.direction == LogDirection.RESPONSE }})") }
            )
            FilterChip(
                selected = selectedFilter == LogDirection.STATE,
                onClick = { selectedFilter = if (selectedFilter == LogDirection.STATE) null else LogDirection.STATE },
                label = { Text("State 📺 (${logs.count { it.direction == LogDirection.STATE }})") }
            )
            FilterChip(
                selected = selectedFilter == LogDirection.SYSTEM,
                onClick = { selectedFilter = if (selectedFilter == LogDirection.SYSTEM) null else LogDirection.SYSTEM },
                label = { Text("System ℹ️ (${logs.count { it.direction == LogDirection.SYSTEM }})") }
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (filteredLogs.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (logs.isEmpty()) "No traffic recorded yet.\nInteract with the remote to see real-time requests & responses." else "No logs match the selected filter.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredLogs, key = { it.id }) { entry ->
                    LogEntryCard(entry = entry)
                }
            }
        }
    }
}

@Composable
private fun LogEntryCard(entry: RemoteLogEntry) {
    val (badgeBg, badgeFg, label) = when (entry.direction) {
        LogDirection.REQUEST -> Triple(Color(0xFFE8F5E9), Color(0xFF2E7D32), "REQ ⬆️")
        LogDirection.RESPONSE -> Triple(Color(0xFFE3F2FD), Color(0xFF1565C0), "RESP ⬇️")
        LogDirection.STATE -> Triple(Color(0xFFEDE7F6), Color(0xFF512DA8), "STATE 📺")
        LogDirection.SYSTEM -> if (entry.isSuccess) {
            Triple(Color(0xFFFFF3E0), Color(0xFFE65100), "SYS ℹ️")
        } else {
            Triple(Color(0xFFFFEBEE), Color(0xFFC62828), "ERR ❌")
        }
    }

    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .background(badgeBg, RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = label,
                            color = badgeFg,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = entry.type,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Text(
                    text = entry.timestamp,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace
                )
            }

            if (entry.payload.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            RoundedCornerShape(6.dp)
                        )
                        .padding(6.dp)
                ) {
                    Text(
                        text = entry.payload,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
fun LoadMediaDialog(
    onDismiss: () -> Unit,
    onLoad: (url: String, title: String?) -> Unit
) {
    var urlInput by remember {
        mutableStateOf("https://storage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4")
    }
    var titleInput by remember { mutableStateOf("Big Buck Bunny") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Stream Media to TV") },
        text = {
            Column {
                Text(
                    "Enter a direct MP4 / HLS video stream URL to play instantly on your TV screen:",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = titleInput,
                    onValueChange = { titleInput = it },
                    label = { Text("Media Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    label = { Text("Video URL (HTTPS)") },
                    singleLine = false,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text("Sample Presets:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SuggestionChip(
                        onClick = {
                            titleInput = "Big Buck Bunny"
                            urlInput = "https://storage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"
                        },
                        label = { Text("🐰 Bunny") }
                    )
                    SuggestionChip(
                        onClick = {
                            titleInput = "Elephant's Dream"
                            urlInput = "https://storage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4"
                        },
                        label = { Text("🐘 Dream") }
                    )
                    SuggestionChip(
                        onClick = {
                            titleInput = "Tears of Steel"
                            urlInput = "https://storage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4"
                        },
                        label = { Text("🚀 Sci-Fi") }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onLoad(urlInput.trim(), titleInput.trim()) },
                enabled = urlInput.isNotBlank()
            ) {
                Text("Play on TV")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
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
