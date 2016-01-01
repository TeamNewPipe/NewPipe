package org.schabi.newpipe;

import android.app.Application;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import info.guardianproject.netcipher.NetCipher;
import info.guardianproject.netcipher.proxy.OrbotHelper;

public class App extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        // if Orbot is installed, then default to using Tor, the user can still override
        if (OrbotHelper.requestStartTor(this)) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            configureTor(prefs.getBoolean(getString(R.string.useTor), true));
        }
    }

    /**
     * Set the proxy settings based on whether Tor should be enabled or not.
     */
    static void configureTor(boolean useTor) {
        if (useTor) {
            NetCipher.useTor();
        } else {
            NetCipher.setProxy(null);
        }
    }
}
