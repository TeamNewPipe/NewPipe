package org.schabi.newpipe.settings.tabs

import com.grack.nanojson.JsonArray
import com.grack.nanojson.JsonObject
import com.grack.nanojson.JsonParser
import com.grack.nanojson.JsonParserException
import com.grack.nanojson.JsonStringWriter
import com.grack.nanojson.JsonWriter

/**
 * Class to get a JSON representation of a list of tabs, and the other way around.
 */
object TabsJsonHelper {
    private val JSON_TABS_ARRAY_KEY: String = "tabs"
    private val FALLBACK_INITIAL_TABS_LIST: List<Tab?> = java.util.List.of(
            Tab.Type.DEFAULT_KIOSK.getTab(),
            Tab.Type.FEED.getTab(),
            Tab.Type.SUBSCRIPTIONS.getTab(),
            Tab.Type.BOOKMARKS.getTab())

    /**
     * Try to reads the passed JSON and returns the list of tabs if no error were encountered.
     *
     *
     * If the JSON is null or empty, or the list of tabs that it represents is empty, the
     * [fallback list][.getDefaultTabs] will be returned.
     *
     *
     * Tabs with invalid ids (i.e. not in the [Tab.Type] enum) will be ignored.
     *
     * @param tabsJson a JSON string got from [.getJsonToSave].
     * @return a list of [tabs][Tab].
     * @throws InvalidJsonException if the JSON string is not valid
     */
    @JvmStatic
    @Throws(InvalidJsonException::class)
    fun getTabsFromJson(tabsJson: String?): List<Tab?> {
        if (tabsJson == null || tabsJson.isEmpty()) {
            return getDefaultTabs()
        }
        val returnTabs: MutableList<Tab?> = ArrayList()
        val outerJsonObject: JsonObject
        try {
            outerJsonObject = JsonParser.`object`().from(tabsJson)
            if (!outerJsonObject.has(JSON_TABS_ARRAY_KEY)) {
                throw InvalidJsonException(("JSON doesn't contain \"" + JSON_TABS_ARRAY_KEY
                        + "\" array"))
            }
            val tabsArray: JsonArray = outerJsonObject.getArray(JSON_TABS_ARRAY_KEY)
            for (o: Any? in tabsArray) {
                if (!(o is JsonObject)) {
                    continue
                }
                val tab: Tab? = Tab.Companion.from(o)
                if (tab != null) {
                    returnTabs.add(tab)
                }
            }
        } catch (e: JsonParserException) {
            throw InvalidJsonException(e)
        }
        if (returnTabs.isEmpty()) {
            return getDefaultTabs()
        }
        return returnTabs
    }

    /**
     * Get a JSON representation from a list of tabs.
     *
     * @param tabList a list of [tabs][Tab].
     * @return a JSON string representing the list of tabs
     */
    fun getJsonToSave(tabList: List<Tab?>?): String {
        val jsonWriter: JsonStringWriter = JsonWriter.string()
        jsonWriter.`object`()
        jsonWriter.array(JSON_TABS_ARRAY_KEY)
        if (tabList != null) {
            for (tab: Tab? in tabList) {
                tab!!.writeJsonOn(jsonWriter)
            }
        }
        jsonWriter.end()
        jsonWriter.end()
        return jsonWriter.done()
    }

    @JvmStatic
    fun getDefaultTabs(): List<Tab?> {
        return FALLBACK_INITIAL_TABS_LIST
    }

    class InvalidJsonException : Exception {
        private constructor() : super()
        constructor(message: String) : super(message)
        constructor(cause: Throwable) : super(cause)
    }
}
