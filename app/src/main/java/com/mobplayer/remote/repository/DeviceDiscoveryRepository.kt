package com.mobplayer.remote.repository

import com.mobplayer.remote.model.TvDevice
import com.mobplayer.remote.network.NsdDiscoveryManager
import kotlinx.coroutines.flow.Flow

class DeviceDiscoveryRepository(private val discoveryManager: NsdDiscoveryManager) {

    fun discoverDevices(): Flow<List<TvDevice>> {
        return discoveryManager.discoverDevices()
    }
}
