package org.schabi.newpipe.settings.preferencesearch;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.XmlRes;
import androidx.preference.PreferenceManager;

import org.xmlpull.v1.XmlPullParser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Parses the corresponding preference-file(s).
 */
public class PreferenceParser {
    private static final String TAG = "PreferenceParser";

    private static final String NS_ANDROID = "http://schemas.android.com/apk/res/android";
    private static final String NS_SEARCH = "http://schemas.android.com/apk/preferencesearch";

    private final Context context;
    private final Map<String, ?> allPreferences;
    private final PreferenceSearchConfiguration searchConfiguration;

    public PreferenceParser(
            final Context context,
            final PreferenceSearchConfiguration searchConfiguration
    ) {
        this.context = context;
        this.allPreferences =  PreferenceManager.getDefaultSharedPreferences(context).getAll();
        this.searchConfiguration = searchConfiguration;
    }

    public List<PreferenceSearchItem> parse(
            @XmlRes final int resId
    ) {
        final List<PreferenceSearchItem> results = new ArrayList<>();
        final XmlPullParser xpp = context.getResources().getXml(resId);

        try {
            xpp.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);
            xpp.setFeature(XmlPullParser.FEATURE_REPORT_NAMESPACE_ATTRIBUTES, true);

            final List<String> breadcrumbs = new ArrayList<>();
            while (xpp.getEventType() != XmlPullParser.END_DOCUMENT) {
                if (xpp.getEventType() == XmlPullParser.START_TAG) {
                    final PreferenceSearchItem result = parseSearchResult(
                            xpp,
                            joinBreadcrumbs(breadcrumbs),
                            resId
                    );

                    if (!searchConfiguration.getParserIgnoreElements().contains(xpp.getName())
                            && result.hasData()
                            && !"true".equals(getAttribute(xpp, NS_SEARCH, "ignore"))) {
                        results.add(result);
                    }
                    if (searchConfiguration.getParserContainerElements().contains(xpp.getName())) {
                        // This code adds breadcrumbs for certain containers (e.g. PreferenceScreen)
                        // Example: Video and Audio > Player
                        breadcrumbs.add(result.getTitle() == null ? "" : result.getTitle());
                    }
                } else if (xpp.getEventType() == XmlPullParser.END_TAG
                        && searchConfiguration.getParserContainerElements()
                            .contains(xpp.getName())) {
                    breadcrumbs.remove(breadcrumbs.size() - 1);
                }

                xpp.next();
            }
        } catch (final Exception e) {
            Log.w(TAG, "Failed to parse resid=" + resId, e);
        }
        return results;
    }

    private String joinBreadcrumbs(final List<String> breadcrumbs) {
        return breadcrumbs.stream()
                .filter(crumb -> !TextUtils.isEmpty(crumb))
                .collect(Collectors.joining(" > "));
    }

    private String getAttribute(
            final XmlPullParser xpp,
            @NonNull final String attribute
    ) {
        final String nsSearchAttr = getAttribute(xpp, NS_SEARCH, attribute);
        if (nsSearchAttr != null) {
            return nsSearchAttr;
        }
        return getAttribute(xpp, NS_ANDROID, attribute);
    }

    private String getAttribute(
            final XmlPullParser xpp,
            @NonNull final String namespace,
            @NonNull final String attribute
    ) {
        return xpp.getAttributeValue(namespace, attribute);
    }

    private PreferenceSearchItem parseSearchResult(
            final XmlPullParser xpp,
            final String breadcrumbs,
            @XmlRes final int searchIndexItemResId
    ) {
        final String key = readString(getAttribute(xpp, "key"));
        final String[] entries = readStringArray(getAttribute(xpp, "entries"));
        final String[] entryValues = readStringArray(getAttribute(xpp, "entryValues"));

        return new PreferenceSearchItem(
            key,
            tryFillInPreferenceValue(
                readString(getAttribute(xpp, "title")),
                key,
                entries,
                entryValues),
            tryFillInPreferenceValue(
                readString(getAttribute(xpp, "summary")),
                key,
                entries,
                entryValues),
            TextUtils.join(",", entries),
            breadcrumbs,
            searchIndexItemResId
        );
    }

    private String[] readStringArray(@Nullable final String s) {
        if (s == null) {
            return new String[0];
        }
        if (s.startsWith("@")) {
            try {
                return context.getResources().getStringArray(Integer.parseInt(s.substring(1)));
            } catch (final Exception e) {
                Log.w(TAG, "Unable to readStringArray from '" + s + "'", e);
            }
        }
        return new String[0];
    }

    private String readString(@Nullable final String s) {
        if (s == null) {
            return "";
        }
        if (s.startsWith("@")) {
            try {
                return context.getString(Integer.parseInt(s.substring(1)));
            } catch (final Exception e) {
                Log.w(TAG, "Unable to readString from '" + s + "'", e);
            }
        }
        return s;
    }

    private String tryFillInPreferenceValue(
            @Nullable final String s,
            @Nullable final String key,
            final String[] entries,
            final String[] entryValues
    ) {
        if (s == null) {
            return "";
        }
        if (key == null) {
            return s;
        }

        // Resolve value
        Object prefValue = allPreferences.get(key);
        if (prefValue == null) {
            return s;
        }

        /*
         * Resolve ListPreference values
         *
         * entryValues = Values/Keys that are saved
         * entries     = Actual human readable names
         */
        if (entries.length > 0 && entryValues.length == entries.length) {
            final int entryIndex = Arrays.asList(entryValues).indexOf(prefValue);
            if (entryIndex != -1) {
                prefValue = entries[entryIndex];
            }
        }

        return String.format(s, prefValue.toString());
    }
}
