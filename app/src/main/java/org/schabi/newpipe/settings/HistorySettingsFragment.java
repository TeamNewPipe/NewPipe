package org.schabi.newpipe.settings;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.preference.Preference;
import android.widget.Toast;

import org.schabi.newpipe.R;
import org.schabi.newpipe.util.InfoCache;

public class HistorySettingsFragment extends BasePreferenceFragment {
    private String cacheWipeKey;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        cacheWipeKey = getString(R.string.metadata_cache_wipe_key);
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.history_settings);
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        if (preference.getKey().equals(cacheWipeKey)) {
            InfoCache.getInstance().clearCache();
            Toast.makeText(preference.getContext(), R.string.metadata_cache_wipe_complete_notice,
                    Toast.LENGTH_SHORT).show();
        }

        return super.onPreferenceTreeClick(preference);
    }
}
