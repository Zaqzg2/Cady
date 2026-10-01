package com.cady.cadysalesapp.data.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

/**
 * "Is there a network at all?" — deliberately only checks the INTERNET capability, not
 * VALIDATED: networks that block Google's connectivity probe (corporate Wi-Fi, some
 * regions) would otherwise be reported offline even though Firestore works. A real
 * failure is caught by the per-document timeouts in SyncService instead.
 */
@Singleton
class ConnectivityObserver @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val manager: ConnectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    fun isOnlineNow(): Boolean {
        val network = manager.activeNetwork ?: return false
        val caps = manager.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    val isOnline: Flow<Boolean> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(isOnlineNow())
            }

            override fun onLost(network: Network) {
                // The *default* network was lost; if another one takes over, onAvailable follows.
                trySend(false)
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                trySend(networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))
            }
        }
        manager.registerDefaultNetworkCallback(callback)
        trySend(isOnlineNow())
        awaitClose { manager.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()
}
