package org.schabi.newpipe.settings;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.preference.ListPreference;

import org.schabi.newpipe.R;

/**
 * A fragment that displays proxy settings.
 */
public class ProxySettingsFragment extends BasePreferenceFragment {

    @Override
    public void onCreatePreferences(@Nullable final Bundle savedInstanceState,
                                    @Nullable final String rootKey) {
        addPreferencesFromResource(R.xml.proxy_settings);

        final ListPreference proxyTypePreference = findPreference("proxy_type");
        if (proxyTypePreference != null) {
            proxyTypePreference.setSummaryProvider(
                    ListPreference.SimpleSummaryProvider.getInstance());
        }
    }
}
