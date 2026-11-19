package com.padanam.psc.data.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Emits whether the phone currently has a validated internet connection.
 * A returning connection is used by the app to push offline changes and pull
 * anything the account workspace gained while the phone was away.
 */
class NetworkMonitor(context: Context) {

    private val manager =
        context.applicationContext.getSystemService(ConnectivityManager::class.java)

    val online: Flow<Boolean> = callbackFlow {
        fun current(): Boolean {
            val network = manager?.activeNetwork ?: return false
            val capabilities = manager.getNetworkCapabilities(network) ?: return false
            return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        }

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(true)
            }

            override fun onLost(network: Network) {
                trySend(current())
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                trySend(
                    networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                )
            }
        }

        trySend(current())
        manager?.registerDefaultNetworkCallback(callback)
        awaitClose { runCatching { manager?.unregisterNetworkCallback(callback) } }
    }.distinctUntilChanged()
}
