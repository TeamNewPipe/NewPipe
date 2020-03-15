package org.schabi.newpipe.util;

import android.content.Context;
import android.preference.PreferenceManager;

import org.jsoup.helper.StringUtil;
import org.schabi.newpipe.DownloaderImpl;
import org.schabi.newpipe.R;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class CookieUtils {
    private CookieUtils() {
    }

    public static String concatCookies(Collection<String> cookieStrings) {
        Set<String> cookieSet = new HashSet<>();
        for (String cookies : cookieStrings) {
            cookieSet.addAll(splitCookies(cookies));
        }
        return StringUtil.join(cookieSet, "; ").trim();
    }

    public static Set<String> splitCookies(String cookies) {
        return new HashSet<>(Arrays.asList(cookies.split("; *")));
    }
}
