package org.schabi.newpipe.cast.session

import android.content.Context
import java.util.concurrent.CopyOnWriteArrayList
import org.schabi.newpipe.cast.driver.CastDriver
import org.schabi.newpipe.cast.driver.chromecast.ChromecastDriver
import org.schabi.newpipe.cast.driver.lounge.YouTubeLoungeDriver
import org.schabi.newpipe.cast.model.CastDevice
import org.schabi.newpipe.cast.model.CastDeviceType
import org.schabi.newpipe.cast.model.CastPlayerState

class CastSessionManager private constructor(context: Context) {

    private val chromecastDriver = ChromecastDriver(context.applicationContext)
    private val loungeDriver = YouTubeLoungeDriver(context.applicationContext)
    private val drivers: List<CastDriver> = listOf(chromecastDriver, loungeDriver)

    private val discoveredDevices = mutableMapOf<String, CastDevice>()
    private val listeners = CopyOnWriteArrayList<CastStateListener>()
    private val discoveryListeners = CopyOnWriteArrayList<(List<CastDevice>) -> Unit>()

    var currentSession: CastSession? = null
        private set

    val isConnected: Boolean
        get() = currentSession?.isConnected() == true

    val currentDevice: CastDevice?
        get() = currentSession?.device

    companion object {
        @Volatile
        private var instance: CastSessionManager? = null

        @JvmStatic
        fun getInstance(context: Context): CastSessionManager {
            return instance ?: synchronized(this) {
                instance ?: CastSessionManager(context).also { instance = it }
            }
        }
    }

    fun addListener(listener: CastStateListener?) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener)
        }
    }

    fun removeListener(listener: CastStateListener?) {
        if (listener != null) {
            listeners.remove(listener)
        }
    }

    fun addDiscoveryListener(listener: (List<CastDevice>) -> Unit) {
        discoveryListeners.add(listener)
        listener(discoveredDevices.values.toList())
    }

    fun removeDiscoveryListener(listener: (List<CastDevice>) -> Unit) {
        discoveryListeners.remove(listener)
    }

    fun startDiscovery() {
        drivers.forEach { driver ->
            driver.startDiscovery(
                onDeviceFound = { device ->
                    synchronized(discoveredDevices) {
                        discoveredDevices[device.id] = device
                    }
                    notifyDiscoveryListeners()
                },
                onDeviceLost = { deviceId ->
                    synchronized(discoveredDevices) {
                        discoveredDevices.remove(deviceId)
                    }
                    notifyDiscoveryListeners()
                }
            )
        }
    }

    fun stopDiscovery() {
        drivers.forEach { it.stopDiscovery() }
    }

    fun getDiscoveredDevices(): List<CastDevice> {
        return synchronized(discoveredDevices) {
            discoveredDevices.values.toList()
        }
    }

    fun connect(device: CastDevice) {
        currentSession?.disconnect()

        val driver = when (device.type) {
            CastDeviceType.CHROMECAST -> chromecastDriver
            CastDeviceType.YOUTUBE_LOUNGE -> loungeDriver
        }

        val sessionListener = object : CastStateListener {
            override fun onSessionStarted(session: CastSession) {
                currentSession = session
                listeners.forEach { it.onSessionStarted(session) }
            }

            override fun onSessionEnded(session: CastSession, error: Throwable?) {
                if (currentSession == session) {
                    currentSession = null
                }
                listeners.forEach { it.onSessionEnded(session, error) }
            }

            override fun onPlaybackStateChanged(state: CastPlayerState) {
                listeners.forEach { it.onPlaybackStateChanged(state) }
            }

            override fun onProgressUpdated(positionMs: Long, durationMs: Long) {
                listeners.forEach { it.onProgressUpdated(positionMs, durationMs) }
            }

            override fun onMediaCompleted() {
                listeners.forEach { it.onMediaCompleted() }
            }

            override fun onVolumeChanged(volume: Float) {
                listeners.forEach { it.onVolumeChanged(volume) }
            }
        }

        val session = driver.createSession(device, sessionListener)
        currentSession = session
        session.connect()
    }

    fun disconnect() {
        currentSession?.disconnect()
        currentSession = null
    }

    fun pairWithTvCode(code: String, callback: (Result<CastDevice>) -> Unit) {
        loungeDriver.pairWithTvCode(code) { result ->
            result.onSuccess { device ->
                synchronized(discoveredDevices) {
                    discoveredDevices[device.id] = device
                }
                notifyDiscoveryListeners()
            }
            callback(result)
        }
    }

    private fun notifyDiscoveryListeners() {
        val list = getDiscoveredDevices()
        discoveryListeners.forEach { it(list) }
    }
}
