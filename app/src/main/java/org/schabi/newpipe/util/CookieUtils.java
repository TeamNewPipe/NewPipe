package org.schabi.newpipe.util;

import android.text.TextUtils;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public final class CookieUtils {
    private CookieUtils() {
    }

    public static String concatCookies(final Collection<String> cookieStrings) {
        Set<String> cookieSet = new HashSet<>();
        for (String cookies : cookieStrings) {
            cookieSet.addAll(splitCookies(cookies));
        }
        return TextUtils.join("; ", cookieSet).trim();
    }

    public static Set<String> splitCookies(final String cookies) {
        return new HashSet<>(Arrays.asList(cookies.split("; *")));
    }
}
