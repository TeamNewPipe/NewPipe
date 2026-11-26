/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.schabi.newpipe.player

import android.app.ActivityManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.os.PowerManager
import androidx.media3.common.PlaybackException
import androidx.preference.PreferenceManager
import java.net.UnknownHostException
import org.schabi.newpipe.MainActivity

/** Coarse, read-only network context. Never collects addresses, SSIDs, URLs or headers. */
internal class PlaybackNetworkDiagnostics(context: Context) : AutoCloseable {
    private val context = context.applicationContext
    private val connectivity = runCatching { this.context.getSystemService(ConnectivityManager::class.java) }.getOrNull()
    private val lock = Any()
    private var generation = 0
    private var observedNetwork: Network? = null
    private var observedBlocked: Boolean? = null
    private var callback: ConnectivityManager.NetworkCallback? = null

    init {
        val manager = connectivity
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && manager != null) {
            val token = ++generation
            val observer = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) = synchronized(lock) {
                    if (generation == token) {
                        observedNetwork = network
                        observedBlocked = null
                    }
                }

                override fun onBlockedStatusChanged(network: Network, blocked: Boolean) = synchronized(lock) {
                    if (generation == token && observedNetwork == network) observedBlocked = blocked
                }

                override fun onLost(network: Network) = synchronized(lock) {
                    if (generation == token && observedNetwork == network) {
                        observedNetwork = null
                        observedBlocked = null
                    }
                }
            }
            if (runCatching { manager.registerDefaultNetworkCallback(observer) }.isSuccess) callback = observer
        }
    }

    fun snapshot(mode: PlaybackPresentationMode, playWhenReady: Boolean): Snapshot {
        val active = runCatching { connectivity?.activeNetwork }
        val network = active.getOrNull()
        val available = if (connectivity == null || active.isFailure) null else network != null
        val capabilities = network?.let { runCatching { connectivity?.getNetworkCapabilities(it) }.getOrNull() }
        val blocked = synchronized(lock) {
            if (network != null && observedNetwork == network) observedBlocked else null
        }
        val power = runCatching { context.getSystemService(PowerManager::class.java) }.getOrNull()
        val activity = runCatching { context.getSystemService(ActivityManager::class.java) }.getOrNull()
        val activityBackground = runCatching {
            val preferences = PreferenceManager.getDefaultSharedPreferences(context)
            if (preferences.contains(MainActivity.KEY_IS_IN_BACKGROUND)) {
                preferences.getBoolean(MainActivity.KEY_IS_IN_BACKGROUND, true)
            } else {
                null
            }
        }.getOrNull()
        return Snapshot(
            transport = transport(capabilities, available),
            available = available,
            validated = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
            metered = runCatching { connectivity?.isActiveNetworkMetered }.getOrNull(),
            blocked = blocked,
            dataSaver = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                when (runCatching { connectivity?.restrictBackgroundStatus }.getOrNull()) {
                    ConnectivityManager.RESTRICT_BACKGROUND_STATUS_DISABLED -> DataSaver.DISABLED
                    ConnectivityManager.RESTRICT_BACKGROUND_STATUS_WHITELISTED -> DataSaver.ALLOWED
                    ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED -> DataSaver.RESTRICTED
                    else -> DataSaver.UNKNOWN
                }
            } else {
                DataSaver.UNSUPPORTED
            },
            backgroundRestricted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                runCatching { activity?.isBackgroundRestricted }.getOrNull()
            } else {
                null
            },
            activityBackground = activityBackground,
            interactive = runCatching { power?.isInteractive }.getOrNull(),
            deviceIdle = runCatching { power?.isDeviceIdleMode }.getOrNull(),
            powerSave = runCatching { power?.isPowerSaveMode }.getOrNull(),
            mode = mode,
            playWhenReady = playWhenReady
        )
    }

    override fun close() {
        val observer = synchronized(lock) {
            generation++
            observedNetwork = null
            observedBlocked = null
            callback.also { callback = null }
        }
        if (observer != null) runCatching { connectivity?.unregisterNetworkCallback(observer) }
    }

    internal enum class Transport { NONE, CELLULAR, WIFI, ETHERNET, VPN, BLUETOOTH, OTHER, UNKNOWN }
    internal enum class DataSaver { DISABLED, ALLOWED, RESTRICTED, UNSUPPORTED, UNKNOWN }

    internal data class Snapshot(
        val transport: Transport,
        val available: Boolean?,
        val validated: Boolean?,
        val metered: Boolean?,
        val blocked: Boolean?,
        val dataSaver: DataSaver,
        val backgroundRestricted: Boolean?,
        val activityBackground: Boolean?,
        val interactive: Boolean?,
        val deviceIdle: Boolean?,
        val powerSave: Boolean?,
        val mode: PlaybackPresentationMode,
        val playWhenReady: Boolean
    ) {
        fun describe(): String = "transport=$transport available=${available.label()} validated=${validated.label()} " +
            "metered=${metered.label()} blocked=${blocked.label()} dataSaver=$dataSaver " +
            "backgroundRestricted=${backgroundRestricted.label()} activityBackground=${activityBackground.label()} " +
            "interactive=${interactive.label()} deviceIdle=${deviceIdle.label()} powerSave=${powerSave.label()} " +
            "mode=$mode playWhenReady=$playWhenReady"

        private fun Boolean?.label(): String = this?.toString() ?: "unknown"
    }

    companion object {
        private fun transport(capabilities: NetworkCapabilities?, available: Boolean?): Transport = when {
            available == false -> Transport.NONE
            capabilities == null -> Transport.UNKNOWN
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> Transport.VPN
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> Transport.CELLULAR
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> Transport.WIFI
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> Transport.ETHERNET
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> Transport.BLUETOOTH
            else -> Transport.OTHER
        }

        fun isRelevant(error: PlaybackException): Boolean = error.errorCode in setOf(
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED
        ) || generateSequence(error as Throwable) { it.cause }.take(8).any { it is UnknownHostException }
    }
}
