package org.schabi.newpipe.settings;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import static android.content.Context.CONNECTIVITY_SERVICE;

public class NetworkHelper {
    /**
     * @param context to use to check for network connectivity.
     * @return true if connected, false otherwise.
     */
    private static NetworkInfo getNetWorkInfo(final Context context) {
        final ConnectivityManager connMgr = (ConnectivityManager)
                context.getSystemService(CONNECTIVITY_SERVICE);
        return connMgr.getActiveNetworkInfo();
    }

    public static boolean isOnline(final Context context) {
        final NetworkInfo networkInfo = getNetWorkInfo(context);
        return networkInfo != null && networkInfo.isConnected();
    }

    public static boolean isConnectedByRoaming(final Context context) {
        final NetworkInfo networkInfo = getNetWorkInfo(context);
        return networkInfo != null && networkInfo.isRoaming();
    }

    public static boolean isOnlineByWifi(final Context context) {
        final NetworkInfo networkInfo = getNetWorkInfo(context);
        return networkInfo != null && networkInfo.isConnected() &&
                networkInfo.getType() == ConnectivityManager.TYPE_WIFI;
    }
}
