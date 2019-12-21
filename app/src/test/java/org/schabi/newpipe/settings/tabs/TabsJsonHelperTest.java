package org.schabi.newpipe.settings.tabs;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonParserException;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static java.util.Objects.requireNonNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class TabsJsonHelperTest {
    private static final String JSON_TABS_ARRAY_KEY = "tabs";
    private static final String JSON_TAB_ID_KEY = "tab_id";

    @Test
    public void testEmptyAndNullRead() throws TabsJsonHelper.InvalidJsonException {
        final List<Tab> defaultTabs = TabsJsonHelper.getDefaultTabs();

        final String emptyTabsJson = "{\"" + JSON_TABS_ARRAY_KEY + "\":[]}";
        List<Tab> items = TabsJsonHelper.getTabsFromJson(emptyTabsJson);
        assertEquals(items, defaultTabs);

        final String nullSource = null;
        items = TabsJsonHelper.getTabsFromJson(nullSource);
        assertEquals(items, defaultTabs);
    }

    @Test
    public void testInvalidIdRead() throws TabsJsonHelper.InvalidJsonException {
        final int blankTabId = Tab.Type.BLANK.getTabId();
        final String emptyTabsJson = "{\"" + JSON_TABS_ARRAY_KEY + "\":[" +
                "{\"" + JSON_TAB_ID_KEY + "\":" + blankTabId + "}," +
                "{\"" + JSON_TAB_ID_KEY + "\":" + 12345678 + "}" +
                "]}";
        final List<Tab> items = TabsJsonHelper.getTabsFromJson(emptyTabsJson);

        assertEquals("Should ignore the tab with invalid id", 1, items.size());
        assertEquals(blankTabId, items.get(0).getTabId());
    }

    @Test
    public void testInvalidRead() {
        final List<String> invalidList = Arrays.asList(
                "{\"notTabsArray\":[]}",
                "{invalidJSON]}",
                "{}"
        );

        for (String invalidContent : invalidList) {
            try {
                TabsJsonHelper.getTabsFromJson(invalidContent);

                fail("didn't throw exception");
            } catch (Exception e) {
                boolean isExpectedException = e instanceof TabsJsonHelper.InvalidJsonException;
                assertTrue("\"" + e.getClass().getSimpleName() + "\" is not the expected exception", isExpectedException);
            }
        }
    }

    @Test
    public void testEmptyAndNullSave() throws JsonParserException {
        final List<Tab> emptyList = Collections.emptyList();
        String returnedJson = TabsJsonHelper.getJsonToSave(emptyList);
        assertTrue(isTabsArrayEmpty(returnedJson));

        final List<Tab> nullList = null;
        returnedJson = TabsJsonHelper.getJsonToSave(nullList);
        assertTrue(isTabsArrayEmpty(returnedJson));
    }

    private boolean isTabsArrayEmpty(String returnedJson) throws JsonParserException {
        JsonObject jsonObject = JsonParser.object().from(returnedJson);
        assertTrue(jsonObject.containsKey(JSON_TABS_ARRAY_KEY));
        return jsonObject.getArray(JSON_TABS_ARRAY_KEY).size() == 0;
    }

    @Test
    public void testSaveAndReading() throws JsonParserException {
        // Saving
        final Tab.BlankTab blankTab = new Tab.BlankTab();
        final Tab.DefaultKioskTab defaultKioskTab = new Tab.DefaultKioskTab();
        final Tab.SubscriptionsTab subscriptionsTab = new Tab.SubscriptionsTab();
        final Tab.ChannelTab channelTab = new Tab.ChannelTab(666, "https://example.org", "testName");
        final Tab.KioskTab kioskTab = new Tab.KioskTab(123, "trending_key");

        final List<Tab> tabs = Arrays.asList(blankTab, defaultKioskTab, subscriptionsTab, channelTab, kioskTab);
        final String returnedJson = TabsJsonHelper.getJsonToSave(tabs);

        // Reading
        final JsonObject jsonObject = JsonParser.object().from(returnedJson);
        assertTrue(jsonObject.containsKey(JSON_TABS_ARRAY_KEY));
        final JsonArray tabsFromArray = jsonObject.getArray(JSON_TABS_ARRAY_KEY);

        assertEquals(tabs.size(), tabsFromArray.size());

        final Tab.BlankTab blankTabFromReturnedJson = requireNonNull((Tab.BlankTab) Tab.from(((JsonObject) tabsFromArray.get(0))));
        assertEquals(blankTab.getTabId(), blankTabFromReturnedJson.getTabId());

        final Tab.DefaultKioskTab defaultKioskTabFromReturnedJson = requireNonNull((Tab.DefaultKioskTab) Tab.from(((JsonObject) tabsFromArray.get(1))));
        assertEquals(defaultKioskTab.getTabId(), defaultKioskTabFromReturnedJson.getTabId());

        final Tab.SubscriptionsTab subscriptionsTabFromReturnedJson = requireNonNull((Tab.SubscriptionsTab) Tab.from(((JsonObject) tabsFromArray.get(2))));
        assertEquals(subscriptionsTab.getTabId(), subscriptionsTabFromReturnedJson.getTabId());

        final Tab.ChannelTab channelTabFromReturnedJson = requireNonNull((Tab.ChannelTab) Tab.from(((JsonObject) tabsFromArray.get(3))));
        assertEquals(channelTab.getTabId(), channelTabFromReturnedJson.getTabId());
        assertEquals(channelTab.getChannelServiceId(), channelTabFromReturnedJson.getChannelServiceId());
        assertEquals(channelTab.getChannelUrl(), channelTabFromReturnedJson.getChannelUrl());
        assertEquals(channelTab.getChannelName(), channelTabFromReturnedJson.getChannelName());

        final Tab.KioskTab kioskTabFromReturnedJson = requireNonNull((Tab.KioskTab) Tab.from(((JsonObject) tabsFromArray.get(4))));
        assertEquals(kioskTab.getTabId(), kioskTabFromReturnedJson.getTabId());
        assertEquals(kioskTab.getKioskServiceId(), kioskTabFromReturnedJson.getKioskServiceId());
        assertEquals(kioskTab.getKioskId(), kioskTabFromReturnedJson.getKioskId());
    }
}