package dev.brahmkshatriya.echo.di

import android.app.Application
import android.content.Context.CONNECTIVITY_SERVICE
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.Network
import dev.brahmkshatriya.echo.common.models.Message
import dev.brahmkshatriya.echo.common.models.NetworkConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Process wide singletons shared between the UI, the extension loader and the player service.
 * Mirrors `di/App.kt` of the phone app, minus the download cache which is not used on the watch.
 */
data class App(
    val context: Application,
    val settings: SharedPreferences,
) {
    val throwFlow = MutableSharedFlow<Throwable>()
    val messageFlow = MutableSharedFlow<Message>()
    val scope = CoroutineScope(Dispatchers.IO)

    private val _networkFlow = MutableStateFlow(NetworkConnection.NotConnected)
    val networkFlow = _networkFlow.asStateFlow()
    val isUnmetered get() = networkFlow.value == NetworkConnection.Unmetered

    init {
        scope.launch {
            throwFlow.collectLatest {
                it.printStackTrace()
            }
        }
        val connectivityManager =
            context.getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                val isMetered = connectivityManager.isActiveNetworkMetered
                _networkFlow.value = if (isMetered) NetworkConnection.Metered
                else NetworkConnection.Unmetered
            }

            override fun onLost(network: Network) {
                _networkFlow.value = NetworkConnection.NotConnected
            }
        }
        runCatching { connectivityManager.registerDefaultNetworkCallback(networkCallback) }
    }
}
