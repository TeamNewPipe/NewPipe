package org.schabi.newpipe.settings.tabs;

import java.util.stream.Collectors;
import org.schabi.newpipe.learning.LearningMode;
import android.content.Context;
import android.content.SharedPreferences;
import android.widget.Toast;

import androidx.preference.PreferenceManager;

import org.schabi.newpipe.R;

import java.util.List;

public final class TabsManager {
    private final SharedPreferences sharedPreferences;
    private final String savedTabsKey;
    private final Context context;
    private SavedTabsChangeListener savedTabsChangeListener;
    private SharedPreferences.OnSharedPreferenceChangeListener preferenceChangeListener;

    private TabsManager(final Context context) {
        this.context = context;
        this.sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        this.savedTabsKey = context.getString(R.string.saved_tabs_key);
    }

    public static TabsManager getManager(final Context context) {
        return new TabsManager(context);
    }

    public List<Tab> getTabs() {
        final String savedJson = sharedPreferences.getString(savedTabsKey, null);
        try {
            return TabsJsonHelper.getTabsFromJson(savedJson);
        } catch (final TabsJsonHelper.InvalidJsonException e) {
            Toast.makeText(context, R.string.saved_tabs_invalid_json, Toast.LENGTH_SHORT).show();
            return getDefaultTabs();
        }
    }

    /**
     * Keep disabled Learning tabs saved so enabling Learning restores the user's home layout.
     * @return the configured tabs available to the active profile
     */
    public List<Tab> getVisibleTabs() {
        return visibleTabs(getTabs(), LearningMode.isEnabled(context));
    }

    static List<Tab> visibleTabs(final List<Tab> configured, final boolean learningEnabled) {
        final List<Tab> visible = configured.stream()
                .filter(tab -> learningEnabled || !(tab instanceof Tab.LearningTab))
                .collect(Collectors.toList());
        return visible.isEmpty() ? TabsJsonHelper.getDefaultTabs() : visible;
    }

    public void saveTabs(final List<Tab> tabList) {
        final String jsonToSave = TabsJsonHelper.getJsonToSave(tabList);
        sharedPreferences.edit().putString(savedTabsKey, jsonToSave).apply();
    }

    public void resetTabs() {
        sharedPreferences.edit().remove(savedTabsKey).apply();
        // Restore the first Home section as the startup destination as well.
        // Otherwise a previously customized startup tab can survive Restore Defaults.
        HomeStartupTab.save(context, null);
    }

    public List<Tab> getDefaultTabs() {
        return TabsJsonHelper.getDefaultTabs();
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Listener
    //////////////////////////////////////////////////////////////////////////*/

    public void setSavedTabsListener(final SavedTabsChangeListener listener) {
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
        return (sp, key) -> {
            if (savedTabsChangeListener != null && (savedTabsKey.equals(key)
                    || context.getString(R.string.learning_mode_key).equals(key)
                    || (key != null && key.startsWith("learning_profile_enabled_")))) {
                savedTabsChangeListener.onTabsChanged();
            }
        };
    }

    public interface SavedTabsChangeListener {
        void onTabsChanged();
    }
}
