/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Simple NetworkConnectivityObserver based on OuterTune's implementation
 * Provides network connectivity monitoring for auto-play functionality
 */
class NetworkConnectivityObserver(context: Context) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _networkStatus = MutableStateFlow(isCurrentlyConnected())
    val networkStatus = _networkStatus.asStateFlow()

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            publishCurrentStatus()
        }

        override fun onLost(network: Network) {
            publishCurrentStatus()
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            publishCurrentStatus()
        }
    }

    init {
        try {
            connectivityManager.registerDefaultNetworkCallback(networkCallback)
        } catch (_: Exception) {
            // Fallback: assume connected if registration fails
            _networkStatus.value = true
        }
    }

    fun unregister() {
        runCatching {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        }
    }

    private fun publishCurrentStatus() {
        _networkStatus.value = isCurrentlyConnected()
    }

    /**
     * Check current connectivity state synchronously
     */
    fun isCurrentlyConnected(): Boolean {
        return try {
            val activeNetwork = connectivityManager.activeNetwork
            val networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
            
            networkCapabilities?.let { capabilities ->
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            } == true
        } catch (_: Exception) {
            false
        }
    }
}
