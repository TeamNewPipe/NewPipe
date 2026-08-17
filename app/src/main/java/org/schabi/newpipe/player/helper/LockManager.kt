package org.schabi.newpipe.player.helper

import android.content.Context
import android.net.wifi.WifiManager
import android.os.PowerManager
import android.util.Log
import androidx.core.content.ContextCompat

class LockManager(context: Context) {
    private val tag = "NewPipe:LockManager@${hashCode()}"

    private val powerManager: PowerManager? by lazy {
        ContextCompat.getSystemService(context.applicationContext, PowerManager::class.java)
    }
    private val wifiManager: WifiManager? by lazy {
        ContextCompat.getSystemService(context.applicationContext, WifiManager::class.java)
    }

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    fun acquireWifiAndCpu() {
        Log.d(tag, "acquireWifiAndCpu() called")
        if (wakeLock?.isHeld == true && wifiLock?.isHeld == true) {
            return
        }

        wakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, tag)
        wifiLock = wifiManager?.createWifiLock(WifiManager.WIFI_MODE_FULL, tag)

        wakeLock?.acquire()
        wifiLock?.acquire()
    }

    fun releaseWifiAndCpu() {
        Log.d(tag, "releaseWifiAndCpu() called")
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
        if (wifiLock?.isHeld == true) {
            wifiLock?.release()
        }

        wakeLock = null
        wifiLock = null
    }
}
