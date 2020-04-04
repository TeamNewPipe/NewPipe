package org.schabi.newpipe.player.helper;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.os.PowerManager;
import android.util.Log;

import static android.content.Context.POWER_SERVICE;
import static android.content.Context.WIFI_SERVICE;

public class LockManager {
    private final String TAG = "LockManager@" + hashCode();

    private final PowerManager powerManager;
    private final WifiManager wifiManager;

    private PowerManager.WakeLock wakeLock;
    private WifiManager.WifiLock wifiLock;

    public LockManager(final Context context) {
        powerManager = ((PowerManager) context.getApplicationContext()
                .getSystemService(POWER_SERVICE));
        wifiManager = ((WifiManager) context.getApplicationContext()
                .getSystemService(WIFI_SERVICE));
    }

    public void acquireWifiAndCpu() {
        Log.d(TAG, "acquireWifiAndCpu() called");
        if (wakeLock != null && wakeLock.isHeld() && wifiLock != null && wifiLock.isHeld()) {
            return;
        }

        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
        wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL, TAG);

        if (wakeLock != null) {
            wakeLock.acquire();
        }
        if (wifiLock != null) {
            wifiLock.acquire();
        }
    }

    public void releaseWifiAndCpu() {
        Log.d(TAG, "releaseWifiAndCpu() called");
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
        if (wifiLock != null && wifiLock.isHeld()) {
            wifiLock.release();
        }

        wakeLock = null;
        wifiLock = null;
    }
}
