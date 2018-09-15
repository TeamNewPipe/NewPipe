package org.schabi.newpipe.settings.tabs;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.widget.Toast;

import org.schabi.newpipe.R;

import java.util.List;

public class TabsManager {
    private final SharedPreferences sharedPreferences;
    private final String savedTabsKey;
    private final Context context;

    public static TabsManager getManager(Context context) {
        return new TabsManager(context);
    }

    private TabsManager(Context context) {
        this.context = context;
        this.sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        this.savedTabsKey = context.getString(R.string.saved_tabs_key);
    }

    public List<Tab> getTabs() {
        final String savedJson = sharedPreferences.getString(savedTabsKey, null);
        try {
            return TabsJsonHelper.getTabsFromJson(savedJson);
        } catch (TabsJsonHelper.InvalidJsonException e) {
            Toast.makeText(context, R.string.saved_tabs_invalid_json, Toast.LENGTH_SHORT).show();
            return getDefaultTabs();
        }
    }

    public void saveTabs(List<Tab> tabList) {
        final String jsonToSave = TabsJsonHelper.getJsonToSave(tabList);
        sharedPreferences.edit().putString(savedTabsKey, jsonToSave).apply();
    }

    public void resetTabs() {
        sharedPreferences.edit().remove(savedTabsKey).apply();
    }

    public List<Tab> getDefaultTabs() {
        return TabsJsonHelper.FALLBACK_INITIAL_TABS_LIST;
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Listener
    //////////////////////////////////////////////////////////////////////////*/

    public interface SavedTabsChangeListener {
        void onTabsChanged();
    }

    private SavedTabsChangeListener savedTabsChangeListener;
    private SharedPreferences.OnSharedPreferenceChangeListener preferenceChangeListener;

    public void setSavedTabsListener(SavedTabsChangeListener listener) {
        if (preferenceChangeListener != null) {
            sharedPreferences.unregisterOnSharedPreferenceChangeListener(preferenceChangeListener);
        }
        savedTabsChangeListener = listener;
        preferenceChangeListener = getPreferenceChangeListener();
        sharedPreferences.registerOnSharedPreferenceChangeListener(preferenceChangeListener);
    }

    public void unsetSavedTabsListener() {
        if (preferenceChangeListener != null) {
            sharedPreferences.unregisterOnSharedPreferenceChangeListener(preferenceChangeListener);
        }
        preferenceChangeListener = null;
        savedTabsChangeListener = null;
    }

    private SharedPreferences.OnSharedPreferenceChangeListener getPreferenceChangeListener() {
        return (sharedPreferences, key) -> {
            if (key.equals(savedTabsKey)) {
                if (savedTabsChangeListener != null) savedTabsChangeListener.onTabsChanged();
            }
        };
    }

}







