package org.schabi.newpipe.settings.drawer_items;

import android.content.Context;
import android.content.SharedPreferences;
import android.widget.Toast;

import androidx.preference.PreferenceManager;

import org.schabi.newpipe.R;

import java.util.List;

public final class DrawerItemManager {
    private final SharedPreferences sharedPreferences;
    private final String savedDrawerItemsKey;
    private final Context context;
    private SavedDrawerItemsChangeListener savedDrawerItemsChangeListener;
    private SharedPreferences.OnSharedPreferenceChangeListener preferenceChangeListener;

    private DrawerItemManager(final Context context) {
        this.context = context;
        this.sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        this.savedDrawerItemsKey = context.getString(R.string.saved_drawer_items_key);
    }

    public static DrawerItemManager getManager(final Context context) {
        return new DrawerItemManager(context);
    }

    public void saveDrawerItems(final List<DrawerItem> drawerItemList) {
        final String jsonToSave = DrawerItemsJsonHelper.getJsonToSave(drawerItemList);
        sharedPreferences.edit().putString(savedDrawerItemsKey, jsonToSave).apply();
    }

    public List<DrawerItem> getDrawerItems() {
        final String savedJson = sharedPreferences.getString(savedDrawerItemsKey, null);
        try {
            return DrawerItemsJsonHelper.getDawerItemsFromJson(savedJson);
        } catch (final DrawerItemsJsonHelper.InvalidJsonException e) {
            Toast.makeText(context, R.string.saved_drawer_items_invalid_json, Toast.LENGTH_SHORT)
                    .show();
            return getDefaultDrawerItems();
        }
    }

    public void resetDrawerItems() {
        sharedPreferences.edit().remove(savedDrawerItemsKey).apply();
    }

    public List<DrawerItem> getDefaultDrawerItems() {
        return DrawerItemsJsonHelper.getDefaultDrawerItems();
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Listener
    //////////////////////////////////////////////////////////////////////////*/

    public void setSavedDrawerItemsListener(final SavedDrawerItemsChangeListener listener) {
        if (preferenceChangeListener != null) {
            sharedPreferences.unregisterOnSharedPreferenceChangeListener(preferenceChangeListener);
        }
        savedDrawerItemsChangeListener = listener;
        preferenceChangeListener = getPreferenceChangeListener();
        sharedPreferences.registerOnSharedPreferenceChangeListener(preferenceChangeListener);
    }

    public void unsetSavedDrawerItemsListener() {
        if (preferenceChangeListener != null) {
            sharedPreferences.unregisterOnSharedPreferenceChangeListener(preferenceChangeListener);
        }
        savedDrawerItemsChangeListener = null;
        preferenceChangeListener = null;
    }

    private SharedPreferences.OnSharedPreferenceChangeListener getPreferenceChangeListener() {
        return (sp, key) -> {
            if (key.equals(savedDrawerItemsKey)) {
                if (savedDrawerItemsChangeListener != null) {
                    savedDrawerItemsChangeListener.onDrawerItemsChanged();
                }
            }
        };
    }

    public interface SavedDrawerItemsChangeListener {
        void onDrawerItemsChanged();
    }
}
