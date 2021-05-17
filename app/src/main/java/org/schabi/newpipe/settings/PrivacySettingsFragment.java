package org.schabi.newpipe.settings;

import android.os.Bundle;
import android.view.WindowManager;

import org.schabi.newpipe.R;

public class PrivacySettingsFragment extends BasePreferenceFragment {
    @Override
    public void onCreatePreferences(final Bundle savedInstanceState, final String rootKey) {
        addPreferencesFromResource(R.xml.privacy_settings);

        final String screenCaptureKey = getString(R.string.enable_screen_capture_key);

        findPreference(screenCaptureKey).setOnPreferenceChangeListener(((preference, newValue) -> {
            if (newValue.toString().equals("false")) {
                getActivity().getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE,
                        WindowManager.LayoutParams.FLAG_SECURE);
            } else {
                getActivity().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
            }
            return true;
        }));
    }
}
