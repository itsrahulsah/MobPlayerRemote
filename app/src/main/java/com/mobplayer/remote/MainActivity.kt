package com.mobplayer.remote

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.mobplayer.remote.ui.DiscoveryScreen
import com.mobplayer.remote.ui.PinAuthScreen
import com.mobplayer.remote.ui.RemoteControlScreen
import com.mobplayer.remote.viewmodel.AuthStep
import com.mobplayer.remote.viewmodel.AuthViewModel
import com.mobplayer.remote.viewmodel.DiscoveryViewModel
import com.mobplayer.remote.viewmodel.RemoteViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val app get() = application as MobPlayerRemoteApp

    private val discoveryViewModel: DiscoveryViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return DiscoveryViewModel(app.discoveryRepository) as T
            }
        }
    }

    private val authViewModel: AuthViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return AuthViewModel(app.socketRepository, app.tokenRepository) as T
            }
        }
    }

    private val remoteViewModel: RemoteViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return RemoteViewModel(app.socketRepository, app.searchPreferencesRepository) as T
            }
        }
    }

    private var backgroundDisconnectJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setupLifecycleKeepalive()

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation()
                }
            }
        }
    }

    private fun setupLifecycleKeepalive() {
        lifecycle.addObserver(LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    // App entered background: keep socket alive for up to 3 minutes
                    backgroundDisconnectJob?.cancel()
                    backgroundDisconnectJob = lifecycleScope.launch {
                        delay(180_000L) // 3 minutes
                        app.socketRepository.disconnect()
                    }
                }
                Lifecycle.Event.ON_START -> {
                    // App resumed: cancel background disconnect
                    backgroundDisconnectJob?.cancel()
                    backgroundDisconnectJob = null
                }
                Lifecycle.Event.ON_DESTROY -> {
                    if (isFinishing) {
                        app.socketRepository.disconnect()
                    }
                }
                else -> Unit
            }
        })
    }

    @Composable
    private fun AppNavigation() {
        val authState by authViewModel.uiState.collectAsState()

        Crossfade(targetState = authState.step, label = "ScreenTransition") { step ->
            when (step) {
                is AuthStep.Authenticated -> {
                    val device = authState.selectedDevice
                    if (device != null) {
                        RemoteControlScreen(
                            device = device,
                            viewModel = remoteViewModel,
                            onDisconnect = {
                                authViewModel.reset()
                            }
                        )
                    } else {
                        DiscoveryScreen(
                            viewModel = discoveryViewModel,
                            onDeviceSelected = { selected ->
                                authViewModel.connectToDevice(selected)
                            }
                        )
                    }
                }

                is AuthStep.PinRequired, is AuthStep.Connecting, is AuthStep.Failed -> {
                    PinAuthScreen(
                        viewModel = authViewModel,
                        onBack = {
                            authViewModel.reset()
                        }
                    )
                }

                is AuthStep.Idle -> {
                    DiscoveryScreen(
                        viewModel = discoveryViewModel,
                        onDeviceSelected = { selected ->
                            authViewModel.connectToDevice(selected)
                        }
                    )
                }
            }
        }
    }
}
