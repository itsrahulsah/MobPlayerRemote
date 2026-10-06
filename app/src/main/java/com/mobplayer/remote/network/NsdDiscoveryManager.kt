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
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resume

class NsdDiscoveryManager(private val context: Context) {

    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private val resolveMutex = Mutex()

    fun discoverDevices(): Flow<List<TvDevice>> = callbackFlow {
        val discoveredDevices = mutableMapOf<String, TvDevice>()

        Log.i(TAG, "==================================================")
        Log.i(TAG, "[NSD] Initializing TV discovery")
        Log.i(TAG, "[NSD] Target Service Type: '$SERVICE_TYPE' (mDNS: $SERVICE_TYPE_MDNS_DOMAIN)")
        Log.i(TAG, "[NSD] Target Service Name: '$EXPECTED_SERVICE_NAME'")
        Log.i(TAG, "[NSD] Target Default Port: $DEFAULT_PORT")
        Log.i(TAG, "==================================================")

        // Acquire MulticastLock to ensure Android Wi-Fi driver receives mDNS multicast packets
        val multicastLock = wifiManager?.createMulticastLock("MobPlayerRemoteMulticastLock")?.apply {
            setReferenceCounted(true)
            try {
                acquire()
                Log.d(TAG, "[NSD] Wi-Fi MulticastLock ACQUIRED (isHeld: $isHeld)")
            } catch (e: Exception) {
                Log.e(TAG, "[NSD] Failed to acquire MulticastLock", e)
            }
        }

        val discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String?) {
                Log.i(TAG, "[NSD] >>> Discovery STARTED for serviceType: '$serviceType'")
            }

            override fun onDiscoveryStopped(serviceType: String?) {
                Log.i(TAG, "[NSD] <<< Discovery STOPPED for serviceType: '$serviceType'")
            }

            override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {
                Log.e(
                    TAG,
                    "[NSD] Start discovery FAILED for '$serviceType'! Reason: ${errorCodeToString(errorCode)} (code: $errorCode)"
                )
            }

            override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {
                Log.e(
                    TAG,
                    "[NSD] Stop discovery FAILED for '$serviceType'! Reason: ${errorCodeToString(errorCode)} (code: $errorCode)"
                )
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                val foundName = serviceInfo.serviceName ?: ""
                val foundType = serviceInfo.serviceType ?: ""

                Log.d(TAG, "[NSD] --------------------------------------------------")
                Log.d(TAG, "[NSD] Service FOUND on network:")
                Log.d(TAG, "[NSD]   Name: '$foundName'")
                Log.d(TAG, "[NSD]   Type: '$foundType'")
                Log.d(TAG, "[NSD] --------------------------------------------------")

                // Check against expected MobPlayer TV signature
                val isMatch = foundName.startsWith(EXPECTED_SERVICE_NAME, ignoreCase = true) ||
                              foundName.startsWith("MobPlayer", ignoreCase = true)

                if (isMatch) {
                    Log.i(
                        TAG,
                        "[NSD] -> Service '$foundName' MATCHED '$EXPECTED_SERVICE_NAME'. Scheduling safe sequential resolve..."
                    )

                    launch {
                        resolveServiceSafely(serviceInfo) { resolvedDevice ->
                            if (resolvedDevice != null) {
                                discoveredDevices[resolvedDevice.name] = resolvedDevice
                                val currentList = discoveredDevices.values.toList()
                                Log.i(
                                    TAG,
                                    "[NSD] Updated active devices (${currentList.size}): " +
                                            currentList.joinToString { "${it.name} -> ws://${it.host}:${it.port}/control" }
                                )
                                trySend(currentList)
                            }
                        }
                    }
                } else {
                    Log.d(
                        TAG,
                        "[NSD] Ignored service '$foundName' (does not match target '$EXPECTED_SERVICE_NAME')"
                    )
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                val lostName = serviceInfo.serviceName ?: ""
                val lostType = serviceInfo.serviceType ?: ""

                Log.w(TAG, "[NSD] --------------------------------------------------")
                Log.w(TAG, "[NSD] Service LOST on network:")
                Log.w(TAG, "[NSD]   Name: '$lostName'")
                Log.w(TAG, "[NSD]   Type: '$lostType'")
                Log.w(TAG, "[NSD] --------------------------------------------------")

                val removed = discoveredDevices.remove(lostName)
                if (removed != null) {
                    Log.i(TAG, "[NSD] Removed '$lostName' from active device list.")
                    val currentList = discoveredDevices.values.toList()
                    Log.d(TAG, "[NSD] Remaining active devices: ${currentList.size}")
                    trySend(currentList)
                }
            }
        }

        try {
            Log.i(TAG, "[NSD] Calling nsdManager.discoverServices(type='$SERVICE_TYPE', protocol=PROTOCOL_DNS_SD)...")
            nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        } catch (e: Exception) {
            Log.e(TAG, "[NSD] Exception starting service discovery for type '$SERVICE_TYPE'", e)
        }

        awaitClose {
            Log.i(TAG, "[NSD] Cleaning up discovery listener and releasing resources...")
            try {
                nsdManager.stopServiceDiscovery(discoveryListener)
            } catch (e: Exception) {
                Log.w(TAG, "[NSD] Exception stopping service discovery", e)
            }
            if (multicastLock?.isHeld == true) {
                multicastLock.release()
                Log.d(TAG, "[NSD] Wi-Fi MulticastLock RELEASED")
            }
        }
    }

    /**
     * Resolves an NsdServiceInfo sequentially using a Mutex to prevent
     * Android's NsdManager.FAILURE_ALREADY_ACTIVE (error code 3) concurrency crash.
     */
    private suspend fun resolveServiceSafely(
        serviceInfo: NsdServiceInfo,
        onResolved: (TvDevice?) -> Unit
    ) {
        resolveMutex.withLock {
            suspendCancellableCoroutine { continuation ->
                val serviceName = serviceInfo.serviceName
                Log.d(TAG, "[NSD] Starting safe resolve for '$serviceName'...")

                try {
                    nsdManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(failedInfo: NsdServiceInfo?, errorCode: Int) {
                            Log.e(
                                TAG,
                                "[NSD] Resolve FAILED for '${failedInfo?.serviceName ?: serviceName}'! " +
                                        "Reason: ${errorCodeToString(errorCode)} (code: $errorCode)"
                            )
                            onResolved(null)
                            if (continuation.isActive) continuation.resume(Unit)
                        }

                        override fun onServiceResolved(resolvedInfo: NsdServiceInfo) {
                            val resolvedHost = resolvedInfo.host?.hostAddress
                            val resolvedPort = if (resolvedInfo.port > 0) resolvedInfo.port else DEFAULT_PORT

                            Log.i(TAG, "[NSD] ==================================================")
                            Log.i(TAG, "[NSD] Service RESOLVED Successfully!")
                            Log.i(TAG, "[NSD]   Name:     '${resolvedInfo.serviceName}'")
                            Log.i(TAG, "[NSD]   Host IP:  $resolvedHost")
                            Log.i(TAG, "[NSD]   Port:     $resolvedPort")
                            Log.i(TAG, "[NSD]   Endpoint: ws://$resolvedHost:$resolvedPort/control")
                            Log.i(TAG, "[NSD] ==================================================")

                            if (!resolvedHost.isNullOrBlank()) {
                                val device = TvDevice(
                                    name = resolvedInfo.serviceName,
                                    host = resolvedHost,
                                    port = resolvedPort
                                )
                                onResolved(device)
                            } else {
                                Log.w(TAG, "[NSD] Host address was null or blank for '${resolvedInfo.serviceName}'")
                                onResolved(null)
                            }

                            if (continuation.isActive) continuation.resume(Unit)
                        }
                    })
                } catch (e: Exception) {
                    Log.e(TAG, "[NSD] Exception while resolving service '$serviceName'", e)
                    onResolved(null)
                    if (continuation.isActive) continuation.resume(Unit)
                }
            }
        }
    }

    private fun errorCodeToString(errorCode: Int): String {
        return when (errorCode) {
            NsdManager.FAILURE_INTERNAL_ERROR -> "FAILURE_INTERNAL_ERROR"
            NsdManager.FAILURE_ALREADY_ACTIVE -> "FAILURE_ALREADY_ACTIVE"
            NsdManager.FAILURE_MAX_LIMIT -> "FAILURE_MAX_LIMIT"
            else -> "UNKNOWN_ERROR"
        }
    }

    companion object {
        private const val TAG = "NsdDiscoveryManager"

        /**
         * Custom DNS-SD service type for MobPlayer TV.
         * In Android NsdManager, the domain ".local" is assumed by PROTOCOL_DNS_SD,
         * so the registration/discovery format is "_mobplayer._tcp."
         * which resolves over the local network as "_mobplayer._tcp.local."
         */
        const val SERVICE_TYPE = "_mobplayer._tcp."
        const val SERVICE_TYPE_MDNS_DOMAIN = "_mobplayer._tcp.local"
        const val EXPECTED_SERVICE_NAME = "MobPlayer TV"
        const val DEFAULT_PORT = 8080
    }
}
