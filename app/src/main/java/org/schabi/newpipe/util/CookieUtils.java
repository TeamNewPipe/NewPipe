package org.schabi.newpipe.util;

import android.text.TextUtils;

import androidx.collection.ArraySet;

import java.util.Collection;

public final class CookieUtils {
    private CookieUtils() {
    }

    public static String concatCookies(final Collection<String> cookieStrings) {
        final ArraySet<String> cookieSet = new ArraySet<>();
        for (final String cookies : cookieStrings) {
            cookieSet.addAll(new ArraySet<>(cookies.split("; *")));
        }
        return TextUtils.join("; ", cookieSet).trim();
    }
}
