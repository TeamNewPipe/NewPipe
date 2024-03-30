package org.schabi.newpipe.settings.tabs

import com.grack.nanojson.JsonObject
import com.grack.nanojson.JsonParser
import com.grack.nanojson.JsonParserException
import org.junit.Assert
import org.junit.Test
import org.schabi.newpipe.settings.tabs.Tab.BlankTab
import org.schabi.newpipe.settings.tabs.Tab.ChannelTab
import org.schabi.newpipe.settings.tabs.Tab.Companion.from
import org.schabi.newpipe.settings.tabs.Tab.DefaultKioskTab
import org.schabi.newpipe.settings.tabs.Tab.KioskTab
import org.schabi.newpipe.settings.tabs.Tab.SubscriptionsTab
import org.schabi.newpipe.settings.tabs.TabsJsonHelper.InvalidJsonException
import org.schabi.newpipe.settings.tabs.TabsJsonHelper.getDefaultTabs
import org.schabi.newpipe.settings.tabs.TabsJsonHelper.getJsonToSave
import org.schabi.newpipe.settings.tabs.TabsJsonHelper.getTabsFromJson
import java.util.Arrays
import java.util.Objects

class TabsJsonHelperTest {
    @Test
    @Throws(InvalidJsonException::class)
    fun testEmptyAndNullRead() {
        val defaultTabs = getDefaultTabs()
        val emptyTabsJson = "{\"" + JSON_TABS_ARRAY_KEY + "\":[]}"
        var items = getTabsFromJson(emptyTabsJson)
        Assert.assertEquals(items, defaultTabs)
        val nullSource: String? = null
        items = getTabsFromJson(nullSource)
        Assert.assertEquals(items, defaultTabs)
    }

    @Test
    @Throws(InvalidJsonException::class)
    fun testInvalidIdRead() {
        val blankTabId = Tab.Type.BLANK.getTabId()
        val emptyTabsJson = ("{\"" + JSON_TABS_ARRAY_KEY + "\":["
                + "{\"" + JSON_TAB_ID_KEY + "\":" + blankTabId + "},"
                + "{\"" + JSON_TAB_ID_KEY + "\":" + 12345678 + "}" + "]}")
        val items = getTabsFromJson(emptyTabsJson)
        Assert.assertEquals("Should ignore the tab with invalid id", 1, items.size.toLong())
        Assert.assertEquals(blankTabId.toLong(), items[0]!!.getTabId().toLong())
    }

    @Test
    fun testInvalidRead() {
        val invalidList: List<String> = mutableListOf(
                "{\"notTabsArray\":[]}",
                "{invalidJSON]}",
                "{}"
        )
        for (invalidContent in invalidList) {
            try {
                getTabsFromJson(invalidContent)
                Assert.fail("didn't throw exception")
            } catch (e: Exception) {
                val isExpectedException = e is InvalidJsonException
                Assert.assertTrue("\"" + e.javaClass.getSimpleName()
                        + "\" is not the expected exception", isExpectedException)
            }
        }
    }

    @Test
    @Throws(JsonParserException::class)
    fun testEmptyAndNullSave() {
        val emptyList: List<Tab?> = emptyList<Tab>()
        var returnedJson = getJsonToSave(emptyList)
        Assert.assertTrue(isTabsArrayEmpty(returnedJson))
        val nullList: List<Tab?>? = null
        returnedJson = getJsonToSave(nullList)
        Assert.assertTrue(isTabsArrayEmpty(returnedJson))
    }

    @Throws(JsonParserException::class)
    private fun isTabsArrayEmpty(returnedJson: String): Boolean {
        val jsonObject = JsonParser.`object`().from(returnedJson)
        Assert.assertTrue(jsonObject.containsKey(JSON_TABS_ARRAY_KEY))
        return jsonObject.getArray(JSON_TABS_ARRAY_KEY).isEmpty()
    }

    @Test
    @Throws(JsonParserException::class)
    fun testSaveAndReading() {
        // Saving
        val blankTab = BlankTab()
        val defaultKioskTab = DefaultKioskTab()
        val subscriptionsTab = SubscriptionsTab()
        val channelTab = ChannelTab(
                666, "https://example.org", "testName")
        val kioskTab = KioskTab(123, "trending_key")
        val tabs = Arrays.asList(
                blankTab, defaultKioskTab, subscriptionsTab, channelTab, kioskTab)
        val returnedJson = getJsonToSave(tabs)

        // Reading
        val jsonObject = JsonParser.`object`().from(returnedJson)
        Assert.assertTrue(jsonObject.containsKey(JSON_TABS_ARRAY_KEY))
        val tabsFromArray = jsonObject.getArray(JSON_TABS_ARRAY_KEY)
        Assert.assertEquals(tabs.size.toLong(), tabsFromArray.size.toLong())
        val blankTabFromReturnedJson = Objects.requireNonNull(from(
                (tabsFromArray[0] as JsonObject)) as BlankTab?)
        Assert.assertEquals(blankTab.getTabId().toLong(), blankTabFromReturnedJson.getTabId().toLong())
        val defaultKioskTabFromReturnedJson = Objects.requireNonNull(
                from((tabsFromArray[1] as JsonObject)) as DefaultKioskTab?)
        Assert.assertEquals(defaultKioskTab.getTabId().toLong(), defaultKioskTabFromReturnedJson.getTabId().toLong())
        val subscriptionsTabFromReturnedJson = Objects.requireNonNull(
                from((tabsFromArray[2] as JsonObject)) as SubscriptionsTab?)
        Assert.assertEquals(subscriptionsTab.getTabId().toLong(), subscriptionsTabFromReturnedJson.getTabId().toLong())
        val channelTabFromReturnedJson = Objects.requireNonNull(from(
                (tabsFromArray[3] as JsonObject)) as ChannelTab?)
        Assert.assertEquals(channelTab.getTabId().toLong(), channelTabFromReturnedJson.getTabId().toLong())
        Assert.assertEquals(channelTab.getChannelServiceId().toLong(),
                channelTabFromReturnedJson.getChannelServiceId().toLong())
        Assert.assertEquals(channelTab.getChannelUrl(), channelTabFromReturnedJson.getChannelUrl())
        Assert.assertEquals(channelTab.getChannelName(), channelTabFromReturnedJson.getChannelName())
        val kioskTabFromReturnedJson = Objects.requireNonNull(from(
                (tabsFromArray[4] as JsonObject)) as KioskTab?)
        Assert.assertEquals(kioskTab.getTabId().toLong(), kioskTabFromReturnedJson.getTabId().toLong())
        Assert.assertEquals(kioskTab.getKioskServiceId().toLong(), kioskTabFromReturnedJson.getKioskServiceId().toLong())
        Assert.assertEquals(kioskTab.getKioskId(), kioskTabFromReturnedJson.getKioskId())
    }

    companion object {
        private const val JSON_TABS_ARRAY_KEY = "tabs"
        private const val JSON_TAB_ID_KEY = "tab_id"
    }
}
