package com.mobplayer.remote.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.util.Log
import com.mobplayer.remote.model.TvDevice
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class NsdDiscoveryManager(private val context: Context) {

    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

    fun discoverDevices(): Flow<List<TvDevice>> = callbackFlow {
        val discoveredDevices = mutableMapOf<String, TvDevice>()
        
        // Acquire multicast lock for reliable mDNS packet reception on Wi-Fi
        val multicastLock = wifiManager?.createMulticastLock("MobPlayerRemoteMulticastLock")?.apply {
            setReferenceCounted(true)
            acquire()
        }

        val discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {
                Log.e(TAG, "Discovery failed to start: error $errorCode")
            }

            override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {
                Log.e(TAG, "Discovery failed to stop: error $errorCode")
            }

            override fun onDiscoveryStarted(serviceType: String?) {
                Log.d(TAG, "Discovery started for $serviceType")
            }

            override fun onDiscoveryStopped(serviceType: String?) {
                Log.d(TAG, "Discovery stopped")
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "Service found: ${serviceInfo.serviceName}")
                val name = serviceInfo.serviceName ?: ""
                if (name.startsWith(SERVICE_NAME_PREFIX, ignoreCase = true)) {
                    nsdManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(service: NsdServiceInfo?, errorCode: Int) {
                            Log.w(TAG, "Resolve failed for ${service?.serviceName}: error $errorCode")
                        }

                        override fun onServiceResolved(resolvedInfo: NsdServiceInfo) {
                            val host = resolvedInfo.host?.hostAddress ?: return
                            val port = resolvedInfo.port
                            val device = TvDevice(
                                name = resolvedInfo.serviceName,
                                host = host,
                                port = port
                            )
                            discoveredDevices[device.name] = device
                            trySend(discoveredDevices.values.toList())
                        }
                    })
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "Service lost: ${serviceInfo.serviceName}")
                discoveredDevices.remove(serviceInfo.serviceName)
                trySend(discoveredDevices.values.toList())
            }
        }

        try {
            nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting service discovery", e)
        }

        awaitClose {
            try {
                nsdManager.stopServiceDiscovery(discoveryListener)
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping service discovery", e)
            }
            if (multicastLock?.isHeld == true) {
                multicastLock.release()
            }
        }
    }

    companion object {
        private const val TAG = "NsdDiscoveryManager"
        private const val SERVICE_TYPE = "_http._tcp."
        private const val SERVICE_NAME_PREFIX = "MobPlayer"
    }
}
