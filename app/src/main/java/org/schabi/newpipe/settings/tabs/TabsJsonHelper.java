package org.schabi.newpipe.settings.tabs;

import android.support.annotation.Nullable;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonParserException;
import com.grack.nanojson.JsonStringWriter;
import com.grack.nanojson.JsonWriter;

import org.jsoup.helper.StringUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Class to get a JSON representation of a list of tabs, and the other way around.
 */
public class TabsJsonHelper {
    private static final String JSON_TABS_ARRAY_KEY = "tabs";

    public static class InvalidJsonException extends Exception {
        private InvalidJsonException() {
            super();
        }

        private InvalidJsonException(String message) {
            super(message);
        }

        private InvalidJsonException(Throwable cause) {
            super(cause);
        }
    }

    /**
     * Try to reads the passed JSON and returns the list of tabs if no error were encountered.
     * <p>
     * If the JSON is null or empty, or the list of tabs that it represents is empty, the
     * {@link #getDefaultTabs fallback list} will be returned.
     * <p>
     * Tabs with invalid ids (i.e. not in the {@link Tab.Type} enum) will be ignored.
     *
     * @param tabsJson a JSON string got from {@link #getJsonToSave(List)}.
     * @return a list of {@link Tab tabs}.
     * @throws InvalidJsonException if the JSON string is not valid
     */
    public static List<Tab> getTabsFromJson(@Nullable String tabsJson) throws InvalidJsonException {
        if (tabsJson == null || tabsJson.isEmpty()) {
            return getDefaultTabs();
        }

        final List<Tab> returnTabs = new ArrayList<>();

        final JsonObject outerJsonObject;
        try {
            outerJsonObject = JsonParser.object().from(tabsJson);
            final JsonArray tabsArray = outerJsonObject.getArray(JSON_TABS_ARRAY_KEY);

            if (tabsArray == null) {
                throw new InvalidJsonException("JSON doesn't contain \"" + JSON_TABS_ARRAY_KEY + "\" array");
            }

            for (Object o : tabsArray) {
                if (!(o instanceof JsonObject)) continue;

                final Tab tab = Tab.from((JsonObject) o);

                if (tab != null) {
                    returnTabs.add(tab);
                }
            }
        } catch (JsonParserException e) {
            throw new InvalidJsonException(e);
        }

        if (returnTabs.isEmpty()) {
            return getDefaultTabs();
        }

        return returnTabs;
    }

    public static List<Tab> getDefaultTabs(){
        List<Tab> tabs = new ArrayList<>();
        Tab.DefaultKioskTab tab = new Tab.DefaultKioskTab();
        if(!StringUtil.isBlank(tab.getKioskId())){
            tabs.add(tab);
        }
        tabs.add(Tab.Type.SUBSCRIPTIONS.getTab());
        tabs.add(Tab.Type.BOOKMARKS.getTab());
        return Collections.unmodifiableList(tabs);
    }
    /**
     * Get a JSON representation from a list of tabs.
     *
     * @param tabList a list of {@link Tab tabs}.
     * @return a JSON string representing the list of tabs
     */
    public static String getJsonToSave(@Nullable List<Tab> tabList) {
        final JsonStringWriter jsonWriter = JsonWriter.string();
        jsonWriter.object();

        jsonWriter.array(JSON_TABS_ARRAY_KEY);
        if (tabList != null) for (Tab tab : tabList) {
            tab.writeJsonOn(jsonWriter);
        }
        jsonWriter.end();

        jsonWriter.end();
        return jsonWriter.done();
    }
}