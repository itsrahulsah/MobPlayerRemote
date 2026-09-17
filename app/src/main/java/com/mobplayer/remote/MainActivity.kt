package com.mobplayer.remote

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.lifecycle.lifecycleScope
import com.mobplayer.remote.network.ControlSocketRepository
import com.mobplayer.remote.network.NsdDiscoveryManager
import com.mobplayer.remote.repository.AuthTokenRepository
import com.mobplayer.remote.repository.DeviceDiscoveryRepository
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

    private lateinit var tokenRepository: AuthTokenRepository
    private lateinit var socketRepository: ControlSocketRepository
    private lateinit var discoveryRepository: DeviceDiscoveryRepository
    private lateinit var discoveryViewModel: DiscoveryViewModel
    private lateinit var authViewModel: AuthViewModel
    private lateinit var remoteViewModel: RemoteViewModel

    private var backgroundDisconnectJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        tokenRepository = AuthTokenRepository(applicationContext)
        socketRepository = ControlSocketRepository()
        val nsdManager = NsdDiscoveryManager(applicationContext)
        discoveryRepository = DeviceDiscoveryRepository(nsdManager)

        discoveryViewModel = DiscoveryViewModel(discoveryRepository)
        authViewModel = AuthViewModel(socketRepository, tokenRepository)
        remoteViewModel = RemoteViewModel(socketRepository)

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
                        socketRepository.disconnect()
                    }
                }
                Lifecycle.Event.ON_START -> {
                    // App resumed: cancel background disconnect
                    backgroundDisconnectJob?.cancel()
                    backgroundDisconnectJob = null
                }
                Lifecycle.Event.ON_DESTROY -> {
                    socketRepository.disconnect()
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
