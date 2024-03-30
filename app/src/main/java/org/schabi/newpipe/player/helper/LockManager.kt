package org.schabi.newpipe.player.helper

import android.content.Context
import android.net.wifi.WifiManager
import android.net.wifi.WifiManager.WifiLock
import android.os.PowerManager
import android.os.PowerManager.WakeLock
import android.util.Log
import androidx.core.content.ContextCompat

class LockManager(context: Context) {
    private val TAG: String = "LockManager@" + hashCode()
    private val powerManager: PowerManager?
    private val wifiManager: WifiManager?
    private var wakeLock: WakeLock? = null
    private var wifiLock: WifiLock? = null

    init {
        powerManager = ContextCompat.getSystemService(context.getApplicationContext(),
                PowerManager::class.java)
        wifiManager = ContextCompat.getSystemService(context, WifiManager::class.java)
    }

    fun acquireWifiAndCpu() {
        Log.d(TAG, "acquireWifiAndCpu() called")
        if ((wakeLock != null) && wakeLock!!.isHeld() && (wifiLock != null) && wifiLock!!.isHeld()) {
            return
        }
        wakeLock = powerManager!!.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG)
        wifiLock = wifiManager!!.createWifiLock(WifiManager.WIFI_MODE_FULL, TAG)
        if (wakeLock != null) {
            wakeLock!!.acquire()
        }
        if (wifiLock != null) {
            wifiLock!!.acquire()
        }
    }

    fun releaseWifiAndCpu() {
        Log.d(TAG, "releaseWifiAndCpu() called")
        if (wakeLock != null && wakeLock!!.isHeld()) {
            wakeLock!!.release()
        }
        if (wifiLock != null && wifiLock!!.isHeld()) {
            wifiLock!!.release()
        }
        wakeLock = null
        wifiLock = null
    }
}
