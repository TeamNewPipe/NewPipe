package org.schabi.newpipe.settings;

import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceManager;
import androidx.preference.SwitchPreferenceCompat;

import org.schabi.newpipe.R;

public class ExoPlayerSettingsFragment extends BasePreferenceFragment {

    @Override
    public void onCreatePreferences(@Nullable final Bundle savedInstanceState,
                                    @Nullable final String rootKey) {
        addPreferencesFromResourceRegistry();

        final String disabledMediaTunnelingAutomaticallyKey =
                getString(R.string.disabled_media_tunneling_automatically_key);
        final SwitchPreferenceCompat disableMediaTunnelingPref =
                (SwitchPreferenceCompat) requirePreference(R.string.disable_media_tunneling_key);
        final SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(requireContext());
        final boolean mediaTunnelingAutomaticallyDisabled =
                prefs.getInt(disabledMediaTunnelingAutomaticallyKey, -1) == 1;
        final String summaryText = getString(R.string.disable_media_tunneling_summary);
        disableMediaTunnelingPref.setSummary(mediaTunnelingAutomaticallyDisabled
                ? summaryText + " " + getString(R.string.disable_media_tunneling_automatic_info)
                : summaryText);

        disableMediaTunnelingPref.setOnPreferenceChangeListener((Preference p, Object enabled) -> {
                    if (Boolean.FALSE.equals(enabled)) {
                        PreferenceManager.getDefaultSharedPreferences(requireContext())
                                .edit()
                                .putInt(disabledMediaTunnelingAutomaticallyKey, 0)
                                .apply();
                        // the info text might have been shown before
                        p.setSummary(R.string.disable_media_tunneling_summary);
                    }
                    return true;
                });
    }
}
