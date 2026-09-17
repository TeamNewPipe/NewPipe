package org.schabi.newpipe.util;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

import java.net.InetSocketAddress;
import java.net.Proxy;

/**
 * A class to manage proxy settings.
 */
public class ProxyManager {

    private final SharedPreferences sharedPreferences;

    /**
     * Creates a new ProxyManager.
     * @param context the context to use
     */
    public ProxyManager(final Context context) {
        this.sharedPreferences =
                PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext());
    }

    /**
     * Checks if the proxy is enabled.
     * @return true if the proxy is enabled, false otherwise
     */
    public boolean isProxyEnabled() {
        return sharedPreferences.getBoolean("use_proxy", false);
    }

    /**
     * Gets the proxy host.
     * @return the proxy host
     */
    public String getProxyHost() {
        return sharedPreferences.getString("proxy_host", "127.0.0.1");
    }

    /**
     * Gets the proxy port.
     * @return the proxy port
     */
    public int getProxyPort() {
        final String portString = sharedPreferences.getString("proxy_port", "1080");
        try {
            return Integer.parseInt(portString);
        } catch (final NumberFormatException e) {
            return 1080;
        }
    }

    /**
     * Gets the proxy type.
     * @return the proxy type
     */
    public Proxy.Type getProxyType() {
        final String type = sharedPreferences.getString("proxy_type", "SOCKS");
        if ("SOCKS".equals(type)) {
            return Proxy.Type.SOCKS;
        } else {
            return Proxy.Type.HTTP;
        }
    }

    /**
     * Gets the proxy.
     * @return the proxy, or null if the proxy is not enabled
     */
    public Proxy getProxy() {
        if (!isProxyEnabled()) {
            return null;
        }
        return new Proxy(getProxyType(), new InetSocketAddress(getProxyHost(), getProxyPort()));
    }
}
