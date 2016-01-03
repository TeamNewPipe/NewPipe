package org.schabi.newpipe;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import info.guardianproject.netcipher.NetCipher;
import info.guardianproject.netcipher.proxy.OrbotHelper;

public class App extends Application {

    private static boolean useTor;

    @Override
    public void onCreate() {
        super.onCreate();

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        if(prefs.getBoolean(getString(R.string.useTorKey), false)) {
            OrbotHelper.requestStartTor(this);
            configureTor(true);
        } else {
            configureTor(false);
        }
    }

    /**
     * Set the proxy settings based on whether Tor should be enabled or not.
     */
    static void configureTor(boolean enabled) {
        useTor = enabled;
        if (useTor) {
            NetCipher.useTor();
        } else {
            NetCipher.setProxy(null);
        }
    }

    static void checkStartTor(Context context) {
        if (useTor) {
            OrbotHelper.requestStartTor(context);
        }
    }

    static boolean isUsingTor() {
        return useTor;
    }
}
