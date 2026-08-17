package org.schabi.newpipe.settings.tabs

import kotlinx.serialization.json.*

object TabsJsonHelper {

    @JvmStatic
    @Throws(InvalidJsonException::class)
    fun getTabsFromJson(json: String?): List<Tab> {
        if (json.isNullOrEmpty()) {
            return defaultTabs
        }

        try {
            val jsonObject = Json.parseToJsonElement(json).jsonObject
            val jsonTabs = jsonObject["tabs"]?.jsonArray ?: return defaultTabs
            val tabList = mutableListOf<Tab>()
            for (i in 0 until jsonTabs.size) {
                val tabObj = jsonTabs[i].jsonObject
                val tab = Tab.from(tabObj)
                if (tab != null) {
                    tabList.add(tab)
                }
            }
            return tabList
        } catch (e: Exception) {
            throw InvalidJsonException(e)
        }
    }

    @JvmStatic
    fun getJsonToSave(tabList: List<Tab>): String {
        val jsonArray = buildJsonArray {
            for (tab in tabList) {
                add(tab.writeJsonObject())
            }
        }
        val jsonObject = buildJsonObject {
            put("tabs", jsonArray)
        }
        return jsonObject.toString()
    }

    @JvmStatic
    val defaultTabs: List<Tab>
        get() = listOf(
            Tab.Type.SUBSCRIPTIONS.tab,
            Tab.Type.BOOKMARKS.tab,
            Tab.Type.HISTORY.tab
        )

    class InvalidJsonException : Exception {
        constructor(message: String?) : super(message)
        constructor(cause: Throwable?) : super(cause)
    }
}
