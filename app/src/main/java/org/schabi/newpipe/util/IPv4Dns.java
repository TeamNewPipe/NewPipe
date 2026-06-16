package org.schabi.newpipe.util;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

import org.schabi.newpipe.R;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.stream.Collectors;

import okhttp3.Dns;

public final class IPv4Dns implements Dns {
    private final SharedPreferences prefs;
    private final String ipv4OnlyKey;

    public IPv4Dns(final Context context) {
        this.prefs = PreferenceManager.getDefaultSharedPreferences(context);
        this.ipv4OnlyKey = context.getString(R.string.ipv4_only_key);
    }

    @NonNull
    @Override
    public List<InetAddress> lookup(@NonNull final String hostname) throws UnknownHostException {
        final List<InetAddress> addresses = Dns.SYSTEM.lookup(hostname);
        if (prefs.getBoolean(ipv4OnlyKey, false)) {
            final List<InetAddress> ipv4Addresses = addresses.stream()
                    .filter(address -> address instanceof Inet4Address)
                    .collect(Collectors.toList());
            if (!ipv4Addresses.isEmpty()) {
                return ipv4Addresses;
            }
        }
        return addresses;
    }
}
