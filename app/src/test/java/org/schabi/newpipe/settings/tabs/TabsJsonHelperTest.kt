package org.schabi.newpipe.settings.tabs

import kotlinx.serialization.json.*
import java.util.Objects.requireNonNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class TabsJsonHelperTest {

    @Test
    @Throws(TabsJsonHelper.InvalidJsonException::class)
    fun testEmptyAndNullRead() {
        val defaultTabs = TabsJsonHelper.getDefaultTabs()

        val emptyTabsJson = "{\"$JSON_TABS_ARRAY_KEY\":[]}"
        var items = TabsJsonHelper.getTabsFromJson(emptyTabsJson)
        assertEquals(items, defaultTabs)

        val nullSource: String? = null
        items = TabsJsonHelper.getTabsFromJson(nullSource)
        assertEquals(items, defaultTabs)
    }

    @Test
    @Throws(TabsJsonHelper.InvalidJsonException::class)
    fun testInvalidIdRead() {
        val blankTabId = Tab.Type.BLANK.tabId
        val emptyTabsJson = "{\"$JSON_TABS_ARRAY_KEY\":[" +
            "{\"$JSON_TAB_ID_KEY\":$blankTabId}," +
            "{\"$JSON_TAB_ID_KEY\":12345678}" + "]}"
        val items = TabsJsonHelper.getTabsFromJson(emptyTabsJson)

        assertEquals("Should ignore the tab with invalid id", 1, items.size)
        assertEquals(blankTabId, items[0].tabId)
    }

    @Test
    fun testInvalidRead() {
        val invalidList = listOf(
            "{\"notTabsArray\":[]}",
            "{invalidJSON]}",
            "{}"
        )

        for (invalidContent in invalidList) {
            try {
                TabsJsonHelper.getTabsFromJson(invalidContent)

                fail("didn't throw exception")
            } catch (e: Exception) {
                val isExpectedException = e is TabsJsonHelper.InvalidJsonException
                assertTrue(
                    "\"" + e.javaClass.simpleName + "\" is not the expected exception",
                    isExpectedException
                )
            }
        }
    }

    @Test
    @Throws(Exception::class)
    fun testEmptyAndNullSave() {
        val emptyList = emptyList<Tab>()
        var returnedJson = TabsJsonHelper.getJsonToSave(emptyList)
        assertTrue(isTabsArrayEmpty(returnedJson))

        val nullList: List<Tab>? = null
        returnedJson = TabsJsonHelper.getJsonToSave(nullList)
        assertTrue(isTabsArrayEmpty(returnedJson))
    }

    @Throws(Exception::class)
    private fun isTabsArrayEmpty(returnedJson: String): Boolean {
        val jsonObject = Json.parseToJsonElement(returnedJson).jsonObject
        assertTrue(jsonObject.containsKey(JSON_TABS_ARRAY_KEY))
        return jsonObject.getArray(JSON_TABS_ARRAY_KEY).isEmpty()
    }

    @Test
    @Throws(Exception::class)
    fun testSaveAndReading() {
        // Saving
        val blankTab = Tab.BlankTab()
        val defaultKioskTab = Tab.DefaultKioskTab()
        val subscriptionsTab = Tab.SubscriptionsTab()
        val channelTab = Tab.ChannelTab(666, "https://example.org", "testName")
        val kioskTab = Tab.KioskTab(123, "trending_key")
        val feedGroupTab = Tab.FeedGroupTab(1L, "x", 123)

        val tabs = listOf(
            blankTab,
            defaultKioskTab,
            subscriptionsTab,
            channelTab,
            kioskTab,
            feedGroupTab
        )
        val returnedJson = TabsJsonHelper.getJsonToSave(tabs)

        // Reading
        val jsonObject = Json.parseToJsonElement(returnedJson).jsonObject
        assertTrue(jsonObject.containsKey(JSON_TABS_ARRAY_KEY))
        val jsonTabs = jsonObject["tabs"]?.jsonArray
        assertNotNull(jsonTabs)
        assertEquals(tabs.size.toLong(), jsonTabs!!.size.toLong())

        val blankTabFromReturnedJson = requireNonNull(
            Tab.from(jsonTabs[0] as JsonObject) as Tab.BlankTab
        )
        assertEquals(blankTab.tabId, blankTabFromReturnedJson.tabId)

        val defaultKioskTabFromReturnedJson = requireNonNull(
            Tab.from(tabsFromArray[1] as JsonObject) as Tab.DefaultKioskTab
        )
        assertEquals(defaultKioskTab.tabId, defaultKioskTabFromReturnedJson.tabId)

        val subscriptionsTabFromReturnedJson = requireNonNull(
            Tab.from(tabsFromArray[2] as JsonObject) as Tab.SubscriptionsTab
        )
        assertEquals(subscriptionsTab.tabId, subscriptionsTabFromReturnedJson.tabId)

        val channelTabFromReturnedJson = requireNonNull(
            Tab.from(tabsFromArray[3] as JsonObject) as Tab.ChannelTab
        )
        assertEquals(channelTab.tabId, channelTabFromReturnedJson.tabId)
        assertEquals(channelTab.channelServiceId, channelTabFromReturnedJson.channelServiceId)
        assertEquals(channelTab.channelUrl, channelTabFromReturnedJson.channelUrl)
        assertEquals(channelTab.channelName, channelTabFromReturnedJson.channelName)

        val kioskTabFromReturnedJson = requireNonNull(
            Tab.from(tabsFromArray[4] as JsonObject) as Tab.KioskTab
        )
        assertEquals(kioskTab.tabId, kioskTabFromReturnedJson.tabId)
        assertEquals(kioskTab.kioskServiceId, kioskTabFromReturnedJson.kioskServiceId)
        assertEquals(kioskTab.kioskId, kioskTabFromReturnedJson.kioskId)

        val grpTabFromReturnedJson = requireNonNull(
            Tab.from(tabsFromArray[5] as JsonObject) as Tab.FeedGroupTab
        )
        assertEquals(feedGroupTab.tabId, grpTabFromReturnedJson.tabId)
        assertEquals(feedGroupTab.feedGroupId, grpTabFromReturnedJson.feedGroupId)
        assertEquals(feedGroupTab.iconId, grpTabFromReturnedJson.iconId)
        assertEquals(feedGroupTab.feedGroupName, grpTabFromReturnedJson.feedGroupName)
    }

    companion object {
        private const val JSON_TABS_ARRAY_KEY = "tabs"
        private const val JSON_TAB_ID_KEY = "tab_id"
    }
}
