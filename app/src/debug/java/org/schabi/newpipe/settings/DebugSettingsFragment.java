package org.schabi.newpipe.settings;

import android.os.Bundle;

import androidx.annotation.Nullable;

import org.schabi.newpipe.R;

import leakcanary.LeakCanary;

public class DebugSettingsFragment extends BasePreferenceFragment {
    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        findPreference(getString(R.string.show_memory_leaks_key))
                .setOnPreferenceClickListener(preference -> {
                    startActivity(LeakCanary.INSTANCE.newLeakDisplayActivityIntent());
                    return true;
                });
    }

    @Override
    public void onCreatePreferences(final Bundle savedInstanceState, final String rootKey) {
        addPreferencesFromResource(R.xml.debug_settings);
    }
}
