package com.mobplayer.remote.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobplayer.remote.model.LogDirection
import com.mobplayer.remote.model.PlayerStatePayload
import com.mobplayer.remote.model.RemoteLogEntry
import com.mobplayer.remote.model.TvDevice
import com.mobplayer.remote.model.UploadState
import com.mobplayer.remote.network.ConnectionState
import com.mobplayer.remote.viewmodel.RemoteViewModel
import java.util.Locale
import kotlin.math.abs

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
    val uploadState by viewModel.uploadState.collectAsState()
    val searchHistory by viewModel.searchHistory.collectAsState()
    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Remote Pad, 1: Player
    var showLoadMediaDialog by remember { mutableStateOf(false) }
    var showSearchDialog by remember { mutableStateOf(false) }
    var showLogsSheet by remember { mutableStateOf(false) }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.uploadVideo(context, device.httpBaseUrl, uri)
        }
    }

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
                    // Search on TV / YouTube
                    IconButton(onClick = {
                        triggerHaptic()
                        showSearchDialog = true
                    }) {
                        Icon(Icons.Default.Search, contentDescription = "Search TV")
                    }

                    // TV Logs
                    IconButton(onClick = {
                        triggerHaptic()
                        showLogsSheet = true
                    }) {
                        BadgedBox(
                            badge = {
                                if (logs.isNotEmpty()) {
                                    Badge { Text(logs.size.toString()) }
                                }
                            }
                        ) {
                            Icon(Icons.Default.Terminal, contentDescription = "TV Logs")
                        }
                    }

                    // Upload Local Video File
                    IconButton(onClick = {
                        triggerHaptic()
                        videoPickerLauncher.launch(arrayOf("video/*"))
                    }) {
                        Icon(Icons.Default.CloudUpload, contentDescription = "Upload Video")
                    }

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

            // Ongoing or recent upload banner
            if (uploadState !is UploadState.Idle) {
                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    UploadProgressCard(
                        uploadState = uploadState,
                        onCancel = { viewModel.cancelUpload(context) },
                        onDismiss = { viewModel.resetUploadState() }
                    )
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
                        onOpenLoadDialog = { showLoadMediaDialog = true },
                        onOpenSearchDialog = { showSearchDialog = true },
                        onPickVideo = { videoPickerLauncher.launch(arrayOf("video/*")) }
                    )
                }
            }
        }
    }

    if (showSearchDialog) {
        SearchTvDialog(
            onDismiss = { showSearchDialog = false },
            onSearch = { query ->
                viewModel.sendTextSubmit(query)
                showSearchDialog = false
            },
            onQueryChange = { text ->
                viewModel.sendTextInput(text)
            },
            searchHistory = searchHistory
        )
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

    if (showLogsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showLogsSheet = false }
        ) {
            TvLogsTabContent(
                logs = logs,
                onClearLogs = { viewModel.clearLogs() },
                onSendRawCommand = { type, payload -> viewModel.sendRawCommand(type, payload) },
                onTriggerHaptic = ::triggerHaptic
            )
        }
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
    val searchHistory by viewModel.searchHistory.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current

    val suggestions = remember(searchHistory, searchQuery) {
        viewModel.getFilteredSuggestions(searchQuery)
    }
    val defaultPresets = listOf("Music", "Movies", "News", "Gaming", "4K", "Trailers")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Search Bar with Auto-fill & Recent Suggestions (Positioned directly on top of Back & Home)
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search TV, YouTube, titles...") },
                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = {
                                    searchQuery = ""
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Clear search",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        IconButton(
                            onClick = {
                                if (searchQuery.isNotBlank()) {
                                    onTriggerHaptic()
                                    viewModel.sendTextSubmit(searchQuery.trim())
                                    focusManager.clearFocus()
                                }
                            },
                            enabled = searchQuery.isNotBlank(),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Submit search",
                                modifier = Modifier.size(18.dp),
                                tint = if (searchQuery.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    if (searchQuery.isNotBlank()) {
                        onTriggerHaptic()
                        viewModel.sendTextSubmit(searchQuery.trim())
                        focusManager.clearFocus()
                    }
                }),
                modifier = Modifier.fillMaxWidth()
            )

            // Auto-Fill Suggestions (Ranked with Key Filter, Best / Latest on top)
            if (suggestions.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (searchQuery.isBlank()) "Recent Searches" else "Best Matches",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (searchQuery.isBlank()) {
                        Text(
                            text = "Clear",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clickable {
                                    onTriggerHaptic()
                                    viewModel.clearSearchHistory()
                                }
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    suggestions.forEachIndexed { index, item ->
                        val isTopMatch = searchQuery.isNotBlank() && index == 0
                        SuggestionChip(
                            onClick = {
                                onTriggerHaptic()
                                searchQuery = item
                                viewModel.sendTextSubmit(item)
                                focusManager.clearFocus()
                            },
                            label = {
                                Text(
                                    item,
                                    maxLines = 1,
                                    fontWeight = if (isTopMatch) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            icon = {
                                Icon(
                                    Icons.Default.History,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = if (isTopMatch) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
            } else if (searchQuery.isBlank()) {
                // Quick key presets if history is empty
                Text(
                    text = "Quick Key Filters",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    defaultPresets.forEach { preset ->
                        SuggestionChip(
                            onClick = {
                                onTriggerHaptic()
                                searchQuery = preset
                                viewModel.sendTextSubmit(preset)
                                focusManager.clearFocus()
                            },
                            label = { Text(preset) },
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
            }
        }

        // Navigation Action Buttons (Back, Home)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedButton(
                onClick = {
                    onTriggerHaptic()
                    viewModel.sendBack()
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                Spacer(modifier = Modifier.width(6.dp))
                Text("Back")
            }

            OutlinedButton(
                onClick = {
                    onTriggerHaptic()
                    viewModel.sendHome()
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Home, contentDescription = "Home")
                Spacer(modifier = Modifier.width(6.dp))
                Text("Home")
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

        // Quick Volume & Mute Row
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "TV Volume",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            onTriggerHaptic()
                            viewModel.adjustVolumeRelative(-0.05f)
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.VolumeDown, contentDescription = "Volume Down")
                    }

                    IconButton(
                        onClick = {
                            onTriggerHaptic()
                            viewModel.toggleMute()
                        }
                    ) {
                        val isMuted = (state?.volume ?: 0.5f) == 0f
                        Icon(
                            if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeMute,
                            contentDescription = "Mute Toggle",
                            tint = if (isMuted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    IconButton(
                        onClick = {
                            onTriggerHaptic()
                            viewModel.adjustVolumeRelative(0.05f)
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Volume Up")
                    }
                }
            }
        }

        // Mini Playback Transport Row (Rewind, Play/Pause, Stop, Forward)
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
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Icon(Icons.Default.FastRewind, contentDescription = "Rewind 10s")
            }

            FilledIconButton(
                onClick = {
                    onTriggerHaptic()
                    viewModel.togglePlayPause()
                },
                modifier = Modifier.size(62.dp),
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(
                    if (state?.isPlaying == true) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (state?.isPlaying == true) "Pause" else "Play",
                    modifier = Modifier.size(32.dp)
                )
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
                Icon(Icons.Default.Stop, contentDescription = "Stop", modifier = Modifier.size(26.dp))
            }

            FilledIconButton(
                onClick = {
                    onTriggerHaptic()
                    viewModel.seekRelative(10_000L)
                },
                modifier = Modifier.size(52.dp),
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Icon(Icons.Default.FastForward, contentDescription = "Forward 10s")
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
    onOpenLoadDialog: () -> Unit,
    onOpenSearchDialog: () -> Unit,
    onPickVideo: () -> Unit
) {
    val currentPos = (state?.positionMs ?: 0L).coerceAtLeast(0L)
    val totalDuration = (state?.durationMs ?: 0L).coerceAtLeast(0L)
    val maxSliderRange = maxOf(1f, totalDuration.toFloat())

    var isUserSeeking by remember { mutableStateOf(false) }
    var seekSliderValue by remember { mutableFloatStateOf(0f) }

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

        // Transport Controls Row (10s seek, 30s seek, play/pause, stop)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledIconButton(
                onClick = {
                    onTriggerHaptic()
                    viewModel.seekRelative(-30_000L)
                },
                modifier = Modifier.size(46.dp),
                shape = CircleShape
            ) {
                Text("-30", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }

            FilledIconButton(
                onClick = {
                    onTriggerHaptic()
                    viewModel.seekRelative(-10_000L)
                },
                modifier = Modifier.size(48.dp),
                shape = CircleShape
            ) {
                Icon(Icons.Default.FastRewind, contentDescription = "Rewind 10s")
            }

            FilledIconButton(
                onClick = {
                    onTriggerHaptic()
                    viewModel.togglePlayPause()
                },
                modifier = Modifier.size(68.dp),
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
                    viewModel.stop()
                },
                modifier = Modifier.size(48.dp),
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            ) {
                Icon(Icons.Default.Stop, contentDescription = "Stop")
            }

            FilledIconButton(
                onClick = {
                    onTriggerHaptic()
                    viewModel.seekRelative(10_000L)
                },
                modifier = Modifier.size(48.dp),
                shape = CircleShape
            ) {
                Icon(Icons.Default.FastForward, contentDescription = "Forward 10s")
            }

            FilledIconButton(
                onClick = {
                    onTriggerHaptic()
                    viewModel.seekRelative(30_000L)
                },
                modifier = Modifier.size(46.dp),
                shape = CircleShape
            ) {
                Text("+30", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
        }

        // Playback Speed Selector Chips
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Playback Speed",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
                val currentSpeed = state?.speed ?: 1.0f
                speeds.forEach { speed ->
                    FilterChip(
                        selected = abs(currentSpeed - speed) < 0.05f,
                        onClick = {
                            onTriggerHaptic()
                            viewModel.setPlaybackSpeed(speed)
                        },
                        label = { Text("${speed}x") }
                    )
                }
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
                    val currentVol = state?.volume ?: 0.5f
                    Text(
                        text = "${(currentVol * 100).toInt()}%",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            onTriggerHaptic()
                            viewModel.toggleMute()
                        }
                    ) {
                        val isMuted = (state?.volume ?: 0.5f) == 0f
                        Icon(
                            if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeMute,
                            contentDescription = "Mute",
                            tint = if (isMuted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Slider(
                        value = (state?.volume ?: 0.5f).coerceIn(0f, 1f),
                        onValueChange = { viewModel.onVolumeSliderChanged(it) },
                        modifier = Modifier.weight(1f)
                    )

                    IconButton(
                        onClick = {
                            onTriggerHaptic()
                            viewModel.adjustVolumeRelative(0.1f)
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Volume Up")
                    }
                }
            }
        }

        // Media Stream, Search & Upload Actions Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    onTriggerHaptic()
                    onOpenLoadDialog()
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Link, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Stream")
            }

            Button(
                onClick = {
                    onTriggerHaptic()
                    onOpenSearchDialog()
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary
                )
            ) {
                Icon(Icons.Default.Search, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Search")
            }

            OutlinedButton(
                onClick = {
                    onTriggerHaptic()
                    onPickVideo()
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.CloudUpload, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Upload")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

/**
 * Tab 2: Real-Time TV Diagnostics, Raw Command Sender & WebSocket Logs
 */
@Composable
private fun TvLogsTabContent(
    logs: List<RemoteLogEntry>,
    onClearLogs: () -> Unit,
    onSendRawCommand: (String, String) -> Unit,
    onTriggerHaptic: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var selectedFilter by remember { mutableStateOf<LogDirection?>(null) }
    var logSearchQuery by remember { mutableStateOf("") }
    var rawType by remember { mutableStateOf("COMMAND") }
    var rawPayload by remember { mutableStateOf("{\"action\":\"PLAY\"}") }

    val filteredLogs = remember(logs, selectedFilter, logSearchQuery) {
        logs.filter { entry ->
            val matchesFilter = selectedFilter == null || entry.direction == selectedFilter
            val matchesSearch = logSearchQuery.isBlank() ||
                entry.type.contains(logSearchQuery, ignoreCase = true) ||
                entry.payload.contains(logSearchQuery, ignoreCase = true)
            matchesFilter && matchesSearch
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Raw Command Sender
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Text("Send Raw WebSocket Frame", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
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
                            onTriggerHaptic()
                            onSendRawCommand(rawType, rawPayload)
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

        // Search Bar for Logs
        OutlinedTextField(
            value = logSearchQuery,
            onValueChange = { logSearchQuery = it },
            placeholder = { Text("Search logs by keyword...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (logSearchQuery.isNotEmpty()) {
                    IconButton(onClick = { logSearchQuery = "" }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear search")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Logs Header Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Console Logs (${filteredLogs.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Row {
                IconButton(
                    onClick = {
                        onTriggerHaptic()
                        val dump = logs.joinToString("\n") {
                            "[${it.timestamp}] [${it.direction}] ${it.type}: ${it.payload}"
                        }
                        clipboardManager.setText(AnnotatedString(dump))
                        Toast.makeText(context, "Copied ${logs.size} log entries", Toast.LENGTH_SHORT).show()
                    },
                    enabled = logs.isNotEmpty()
                ) {
                    Icon(Icons.Default.Link, contentDescription = "Copy Dump")
                }

                IconButton(
                    onClick = {
                        onTriggerHaptic()
                        val targetLogs = if (filteredLogs.isNotEmpty()) filteredLogs else logs
                        val exportDate = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
                        val dump = buildString {
                            appendLine("=== MobPlayer TV Remote Console Logs ===")
                            appendLine("Exported: $exportDate")
                            appendLine("Total Entries: ${targetLogs.size}")
                            appendLine("Retention Policy: Past 24 Hours")
                            appendLine("========================================")
                            appendLine()
                            targetLogs.forEach { entry ->
                                val status = if (entry.isSuccess) "OK" else "FAIL"
                                appendLine("[${entry.timestamp}] [${entry.direction}] [$status] ${entry.type}: ${entry.payload}")
                            }
                        }
                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "MobPlayer TV Remote Logs (${targetLogs.size} entries)")
                            putExtra(Intent.EXTRA_TEXT, dump)
                        }
                        val shareIntent = Intent.createChooser(sendIntent, "Share TV Logs").apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(shareIntent)
                    },
                    enabled = logs.isNotEmpty()
                ) {
                    Icon(Icons.Default.Share, contentDescription = "Share Logs")
                }

                IconButton(
                    onClick = {
                        onTriggerHaptic()
                        onClearLogs()
                    },
                    enabled = logs.isNotEmpty()
                ) {
                    Icon(
                        Icons.Default.DeleteSweep,
                        contentDescription = "Clear Logs",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        // Filter Chips Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedFilter == null,
                onClick = {
                    onTriggerHaptic()
                    selectedFilter = null
                },
                label = { Text("All (${logs.size})") }
            )
            FilterChip(
                selected = selectedFilter == LogDirection.REQUEST,
                onClick = {
                    onTriggerHaptic()
                    selectedFilter = if (selectedFilter == LogDirection.REQUEST) null else LogDirection.REQUEST
                },
                label = { Text("Requests ⬆️") }
            )
            FilterChip(
                selected = selectedFilter == LogDirection.RESPONSE,
                onClick = {
                    onTriggerHaptic()
                    selectedFilter = if (selectedFilter == LogDirection.RESPONSE) null else LogDirection.RESPONSE
                },
                label = { Text("Responses ⬇️") }
            )
            FilterChip(
                selected = selectedFilter == LogDirection.STATE,
                onClick = {
                    onTriggerHaptic()
                    selectedFilter = if (selectedFilter == LogDirection.STATE) null else LogDirection.STATE
                },
                label = { Text("State 📺") }
            )
            FilterChip(
                selected = selectedFilter == LogDirection.SYSTEM,
                onClick = {
                    onTriggerHaptic()
                    selectedFilter = if (selectedFilter == LogDirection.SYSTEM) null else LogDirection.SYSTEM
                },
                label = { Text("System ℹ️") }
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
        LogDirection.REQUEST, LogDirection.OUTGOING -> Triple(Color(0xFFE8F5E9), Color(0xFF2E7D32), "REQ ⬆️")
        LogDirection.RESPONSE, LogDirection.INCOMING -> Triple(Color(0xFFE3F2FD), Color(0xFF1565C0), "RESP ⬇️")
        LogDirection.STATE -> Triple(Color(0xFFEDE7F6), Color(0xFF512DA8), "STATE 📺")
        LogDirection.SYSTEM -> if (entry.isSuccess) {
            Triple(Color(0xFFFFF3E0), Color(0xFFE65100), "SYS ℹ️")
        } else {
            Triple(Color(0xFFFFEBEE), Color(0xFFC62828), "ERR ❌")
        }
        LogDirection.ERROR -> Triple(Color(0xFFFFEBEE), Color(0xFFC62828), "ERR ❌")
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
private fun UploadProgressCard(
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Uploading: ${uploadState.fileName}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        TextButton(onClick = onCancel) {
                            Text("Cancel", color = MaterialTheme.colorScheme.error)
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { uploadState.progress },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${(uploadState.progress * 100).toInt()}% • ${uploadState.bytesUploaded / (1024 * 1024)}MB / ${uploadState.totalBytes / (1024 * 1024)}MB",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
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

@Composable
fun SearchTvDialog(
    onDismiss: () -> Unit,
    onSearch: (query: String) -> Unit,
    onQueryChange: ((String) -> Unit)? = null,
    searchHistory: List<String> = emptyList()
) {
    var queryInput by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        },
        title = { Text("Search on TV", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    "Search YouTube videos, songs, trailers, or type directly to your TV screen:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = queryInput,
                    onValueChange = {
                        queryInput = it
                        onQueryChange?.invoke(it)
                    },
                    label = { Text("Search query") },
                    placeholder = { Text("e.g. music, movie trailers...") },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null)
                    },
                    trailingIcon = {
                        if (queryInput.isNotEmpty()) {
                            IconButton(onClick = {
                                queryInput = ""
                                onQueryChange?.invoke("")
                            }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        if (queryInput.isNotBlank()) {
                            onSearch(queryInput.trim())
                        }
                    }),
                    modifier = Modifier.fillMaxWidth()
                )

                val dialogSuggestions = remember(searchHistory, queryInput) {
                    com.mobplayer.remote.repository.SearchPreferencesRepository.filterAndRankSuggestions(queryInput, searchHistory)
                }

                if (dialogSuggestions.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        if (queryInput.isBlank()) "Recent Searches:" else "Best Matches:",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        dialogSuggestions.take(8).forEachIndexed { index, recent ->
                            val isTop = queryInput.isNotBlank() && index == 0
                            SuggestionChip(
                                onClick = {
                                    queryInput = recent
                                    onSearch(recent)
                                },
                                label = {
                                    Text(
                                        recent,
                                        fontWeight = if (isTop) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                icon = {
                                    Icon(
                                        Icons.Default.History,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = if (isTop) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text("Popular Presets:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val presets = listOf("Music", "News", "Gaming", "Movie Trailers", "Sports", "4K HDR")
                    presets.forEach { preset ->
                        SuggestionChip(
                            onClick = {
                                queryInput = preset
                                onSearch(preset)
                            },
                            label = { Text(preset) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSearch(queryInput.trim()) },
                enabled = queryInput.isNotBlank()
            ) {
                Text("Search on TV")
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
