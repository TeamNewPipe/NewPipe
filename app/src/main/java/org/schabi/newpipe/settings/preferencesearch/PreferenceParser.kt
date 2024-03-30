package org.schabi.newpipe.settings.preferencesearch

import android.content.Context
import android.text.TextUtils
import android.util.Log
import androidx.annotation.XmlRes
import androidx.preference.PreferenceManager
import org.schabi.newpipe.util.Localization
import org.xmlpull.v1.XmlPullParser
import java.util.Arrays

/**
 * Parses the corresponding preference-file(s).
 */
class PreferenceParser(
        private val context: Context,
        private val searchConfiguration: PreferenceSearchConfiguration
) {
    private val allPreferences: Map<String, *>

    init {
        allPreferences = PreferenceManager.getDefaultSharedPreferences(context).getAll()
    }

    fun parse(
            @XmlRes resId: Int
    ): List<PreferenceSearchItem> {
        val results: MutableList<PreferenceSearchItem> = ArrayList()
        val xpp: XmlPullParser = context.getResources().getXml(resId)
        try {
            xpp.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
            xpp.setFeature(XmlPullParser.FEATURE_REPORT_NAMESPACE_ATTRIBUTES, true)
            val breadcrumbs: MutableList<String?> = ArrayList()
            while (xpp.getEventType() != XmlPullParser.END_DOCUMENT) {
                if (xpp.getEventType() == XmlPullParser.START_TAG) {
                    val result: PreferenceSearchItem = parseSearchResult(
                            xpp,
                            Localization.concatenateStrings(" > ", breadcrumbs),
                            resId
                    )
                    if ((!searchConfiguration.getParserIgnoreElements().contains(xpp.getName())
                                    && result.hasData()
                                    && !("true" == getAttribute(xpp, NS_SEARCH, "ignore")))) {
                        results.add(result)
                    }
                    if (searchConfiguration.getParserContainerElements().contains(xpp.getName())) {
                        // This code adds breadcrumbs for certain containers (e.g. PreferenceScreen)
                        // Example: Video and Audio > Player
                        breadcrumbs.add(if (result.getTitle() == null) "" else result.getTitle())
                    }
                } else if ((xpp.getEventType() == XmlPullParser.END_TAG
                                && searchConfiguration.getParserContainerElements()
                                .contains(xpp.getName()))) {
                    breadcrumbs.removeAt(breadcrumbs.size - 1)
                }
                xpp.next()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse resid=" + resId, e)
        }
        return results
    }

    private fun getAttribute(
            xpp: XmlPullParser,
            attribute: String
    ): String {
        val nsSearchAttr: String? = getAttribute(xpp, NS_SEARCH, attribute)
        if (nsSearchAttr != null) {
            return nsSearchAttr
        }
        return getAttribute(xpp, NS_ANDROID, attribute)
    }

    private fun getAttribute(
            xpp: XmlPullParser,
            namespace: String,
            attribute: String
    ): String {
        return xpp.getAttributeValue(namespace, attribute)
    }

    private fun parseSearchResult(
            xpp: XmlPullParser,
            breadcrumbs: String,
            @XmlRes searchIndexItemResId: Int
    ): PreferenceSearchItem {
        val key: String = readString(getAttribute(xpp, "key"))
        val entries: Array<String?> = readStringArray(getAttribute(xpp, "entries"))
        val entryValues: Array<String?> = readStringArray(getAttribute(xpp, "entryValues"))
        return PreferenceSearchItem(
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
        )
    }

    private fun readStringArray(s: String?): Array<String?> {
        if (s == null) {
            return arrayOfNulls(0)
        }
        if (s.startsWith("@")) {
            try {
                return context.getResources().getStringArray(s.substring(1).toInt())
            } catch (e: Exception) {
                Log.w(TAG, "Unable to readStringArray from '" + s + "'", e)
            }
        }
        return arrayOfNulls(0)
    }

    private fun readString(s: String?): String {
        if (s == null) {
            return ""
        }
        if (s.startsWith("@")) {
            try {
                return context.getString(s.substring(1).toInt())
            } catch (e: Exception) {
                Log.w(TAG, "Unable to readString from '" + s + "'", e)
            }
        }
        return s
    }

    private fun tryFillInPreferenceValue(
            s: String?,
            key: String?,
            entries: Array<String>,
            entryValues: Array<String?>
    ): String {
        if (s == null) {
            return ""
        }
        if (key == null) {
            return s
        }

        // Resolve value
        var prefValue: Any? = allPreferences.get(key)
        if (prefValue == null) {
            return s
        }

        /*
         * Resolve ListPreference values
         *
         * entryValues = Values/Keys that are saved
         * entries     = Actual human readable names
         */if (entries.size > 0 && entryValues.size == entries.size) {
            val entryIndex: Int = Arrays.asList(*entryValues).indexOf(prefValue)
            if (entryIndex != -1) {
                prefValue = entries.get(entryIndex)
            }
        }
        return String.format(s, prefValue.toString())
    }

    companion object {
        private val TAG: String = "PreferenceParser"
        private val NS_ANDROID: String = "http://schemas.android.com/apk/res/android"
        private val NS_SEARCH: String = "http://schemas.android.com/apk/preferencesearch"
    }
}
