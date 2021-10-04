package org.schabi.newpipe.settings.drawer_items;

import androidx.annotation.Nullable;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonParserException;
import com.grack.nanojson.JsonStringWriter;
import com.grack.nanojson.JsonWriter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class DrawerItemsJsonHelper {
    private static final String JSON_DRAWER_ITEMS_ARRAY_KEY = "tabs";

    private static final List<DrawerItem> FALLBACK_INITIAL_DRAWER_ITEM_LIST
            = Collections.unmodifiableList(
            Arrays.asList(
                    // we need that many to show all a available kiosks
                    DrawerItem.Type.DEFAULT_KIOSK.getDrawerItem(),
                    DrawerItem.Type.DEFAULT_KIOSK.getDrawerItem(),
                    DrawerItem.Type.DEFAULT_KIOSK.getDrawerItem(),
                    DrawerItem.Type.DEFAULT_KIOSK.getDrawerItem(),
                    DrawerItem.Type.SUBSCRIPTIONS.getDrawerItem(),
                    DrawerItem.Type.FEED.getDrawerItem(),
                    DrawerItem.Type.BOOKMARKS.getDrawerItem(),
                    DrawerItem.Type.DOWNLOADS.getDrawerItem(),
                    DrawerItem.Type.HISTORY.getDrawerItem()));

    private DrawerItemsJsonHelper() {  }
    /**
     * Try to reads the passed JSON and returns the list of DrawerItems if no error were
     * encountered.
     * <p>
     * If the JSON is null or empty, or the list of DrawerItems that it represents is empty, the
     * {@link #getDefaultDrawerItems fallback list} will be returned.
     * <p>
     * DrawerItems with invalid ids (i.e. not in the {@link DrawerItem.Type} enum) will be ignored.
     *
     * @param drawerItemsJson a JSON string got from {@link #getJsonToSave(List)}.
     * @return a list of {@link DrawerItem drawerItems}.
     * @throws DrawerItemsJsonHelper.InvalidJsonException if the JSON string is not valid
     */
    public static List<DrawerItem> getDawerItemsFromJson(@Nullable final String drawerItemsJson)
            throws InvalidJsonException {
        if (drawerItemsJson == null || drawerItemsJson.isEmpty()) {
            return getDefaultDrawerItems();
        }

        final List<DrawerItem> returnDrawerItems = new ArrayList<>();

        final JsonObject outerJsonObject;
        try {
            outerJsonObject = JsonParser.object().from(drawerItemsJson);

            if (!outerJsonObject.has(JSON_DRAWER_ITEMS_ARRAY_KEY)) {
                throw new InvalidJsonException(
                        "JSON doesn't contain \"" + JSON_DRAWER_ITEMS_ARRAY_KEY + "\" array");
            }

            final JsonArray tabsArray = outerJsonObject.getArray(JSON_DRAWER_ITEMS_ARRAY_KEY);

            for (final Object o : tabsArray) {
                if (!(o instanceof JsonObject)) {
                    continue;
                }

                final DrawerItem drawerItem = DrawerItem.from((JsonObject) o);

                if (drawerItem != null) {
                    returnDrawerItems.add(drawerItem);
                }
            }
        } catch (final JsonParserException e) {
            throw new InvalidJsonException(e);
        }

        if (returnDrawerItems.isEmpty()) {
            return getDefaultDrawerItems();
        }

        return returnDrawerItems;
    }

    /**
     * Get a JSON representation from a list of tabs.
     *
     * @param drawerItemList a list of {@link DrawerItem drawerItem}.
     * @return a JSON string representing the list of drawerItems
     */
    public static String getJsonToSave(@Nullable final List<DrawerItem> drawerItemList) {
        final JsonStringWriter jsonWriter = JsonWriter.string();
        jsonWriter.object();

        jsonWriter.array(JSON_DRAWER_ITEMS_ARRAY_KEY);
        if (drawerItemList != null) {
            for (final DrawerItem tab : drawerItemList) {
                tab.writeJsonOn(jsonWriter);
            }
        }
        jsonWriter.end();

        jsonWriter.end();
        return jsonWriter.done();
    }

    public static List<DrawerItem> getDefaultDrawerItems() {
        return FALLBACK_INITIAL_DRAWER_ITEM_LIST;
    }

    public static final class InvalidJsonException extends Exception {
        private InvalidJsonException() {
            super();
        }

        private InvalidJsonException(final String message) {
            super(message);
        }

        private InvalidJsonException(final Throwable cause) {
            super(cause);
        }
    }
}
