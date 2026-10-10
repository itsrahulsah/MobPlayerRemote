package com.mobplayer.remote

import android.app.Application
import com.mobplayer.remote.network.ControlSocketRepository
import com.mobplayer.remote.network.NsdDiscoveryManager
import com.mobplayer.remote.repository.AuthTokenRepository
import com.mobplayer.remote.repository.DeviceDiscoveryRepository
import com.mobplayer.remote.repository.LogPreferencesRepository
import com.mobplayer.remote.repository.SearchPreferencesRepository

class MobPlayerRemoteApp : Application() {

    lateinit var tokenRepository: AuthTokenRepository
        private set

    lateinit var logPreferencesRepository: LogPreferencesRepository
        private set

    lateinit var searchPreferencesRepository: SearchPreferencesRepository
        private set

    lateinit var socketRepository: ControlSocketRepository
        private set

    lateinit var discoveryRepository: DeviceDiscoveryRepository
        private set

    override fun onCreate() {
        super.onCreate()
        tokenRepository = AuthTokenRepository(applicationContext)
        logPreferencesRepository = LogPreferencesRepository(applicationContext)
        searchPreferencesRepository = SearchPreferencesRepository(applicationContext)
        socketRepository = ControlSocketRepository(logPreferencesRepository = logPreferencesRepository)
        val nsdManager = NsdDiscoveryManager(applicationContext)
        discoveryRepository = DeviceDiscoveryRepository(nsdManager)
    }
}
