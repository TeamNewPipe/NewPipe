package org.schabi.newpipe.settings;

import android.os.Bundle;

import androidx.preference.Preference;

import org.schabi.newpipe.R;

import leakcanary.LeakCanary;

public class DebugSettingsFragment extends BasePreferenceFragment {
    @Override
    public void onCreatePreferences(final Bundle savedInstanceState, final String rootKey) {
        addPreferencesFromResource(R.xml.debug_settings);

        final Preference showMemoryLeaksPreference
                = findPreference(getString(R.string.show_memory_leaks_key));
        final Preference crashTheAppPreference
                = findPreference(getString(R.string.crash_the_app_key));

        assert showMemoryLeaksPreference != null;
        assert crashTheAppPreference != null;

        showMemoryLeaksPreference.setOnPreferenceClickListener(preference -> {
            startActivity(LeakCanary.INSTANCE.newLeakDisplayActivityIntent());
            return true;
        });

        crashTheAppPreference.setOnPreferenceClickListener(preference -> {
            throw new RuntimeException();
        });
    }
}
