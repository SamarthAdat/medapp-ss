package com.ss.medrecord.core.connectivity

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

enum class NetworkStatus {
    /** Connected to a network that has passed a captive-portal/internet check. */
    AVAILABLE,

    /** No usable connection. All writes stay local and queue for sync. */
    UNAVAILABLE,
}

/**
 * Exposes device connectivity as a stream. The sync engine (Phase 3) subscribes
 * to trigger pushes on reconnect; the UI subscribes to render the offline badge.
 */
interface ConnectivityObserver {
    val status: Flow<NetworkStatus>
    fun currentStatus(): NetworkStatus
}

@Singleton
class ConnectivityObserverImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : ConnectivityObserver {

    private val connectivityManager: ConnectivityManager? =
        context.getSystemService(ConnectivityManager::class.java)

    override val status: Flow<NetworkStatus> = callbackFlow {
        val manager = connectivityManager
        if (manager == null) {
            trySend(NetworkStatus.UNAVAILABLE)
            awaitClose { }
            return@callbackFlow
        }

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(NetworkStatus.AVAILABLE)
            }

            override fun onLost(network: Network) {
                trySend(currentStatus())
            }

            override fun onUnavailable() {
                trySend(NetworkStatus.UNAVAILABLE)
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities,
            ) {
                trySend(networkCapabilities.toStatus())
            }
        }

        trySend(currentStatus())

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        manager.registerNetworkCallback(request, callback)

        awaitClose { manager.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()

    override fun currentStatus(): NetworkStatus {
        val manager = connectivityManager ?: return NetworkStatus.UNAVAILABLE
        val active = manager.activeNetwork ?: return NetworkStatus.UNAVAILABLE
        val capabilities = manager.getNetworkCapabilities(active)
            ?: return NetworkStatus.UNAVAILABLE
        return capabilities.toStatus()
    }

    private fun NetworkCapabilities.toStatus(): NetworkStatus {
        val hasInternet = hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        val isValidated = hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        return if (hasInternet && isValidated) NetworkStatus.AVAILABLE else NetworkStatus.UNAVAILABLE
    }
}
