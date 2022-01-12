package org.schabi.newpipe.settings;

import android.os.Bundle;

import androidx.preference.Preference;

import org.schabi.newpipe.R;
import org.schabi.newpipe.error.ErrorInfo;
import org.schabi.newpipe.error.ErrorUtil;
import org.schabi.newpipe.error.UserAction;
import org.schabi.newpipe.util.PicassoHelper;

import leakcanary.LeakCanary;

public class DebugSettingsFragment extends BasePreferenceFragment {
    @Override
    public void onCreatePreferences(final Bundle savedInstanceState, final String rootKey) {
        addPreferencesFromResource(R.xml.debug_settings);

        final Preference showMemoryLeaksPreference
                = findPreference(getString(R.string.show_memory_leaks_key));
        final Preference showImageIndicatorsPreference
                = findPreference(getString(R.string.show_image_indicators_key));
        final Preference crashTheAppPreference
                = findPreference(getString(R.string.crash_the_app_key));
        final Preference showErrorSnackbarPreference
                = findPreference(getString(R.string.show_error_snackbar_key));
        final Preference createErrorNotificationPreference
                = findPreference(getString(R.string.create_error_notification_key));

        assert showMemoryLeaksPreference != null;
        assert showImageIndicatorsPreference != null;
        assert crashTheAppPreference != null;
        assert showErrorSnackbarPreference != null;
        assert createErrorNotificationPreference != null;

        showMemoryLeaksPreference.setOnPreferenceClickListener(preference -> {
            startActivity(LeakCanary.INSTANCE.newLeakDisplayActivityIntent());
            return true;
        });

        showImageIndicatorsPreference.setOnPreferenceChangeListener((preference, newValue) -> {
            PicassoHelper.setIndicatorsEnabled((Boolean) newValue);
            return true;
        });

        crashTheAppPreference.setOnPreferenceClickListener(preference -> {
            throw new RuntimeException();
        });

        showErrorSnackbarPreference.setOnPreferenceClickListener(preference -> {
            ErrorUtil.showUiErrorSnackbar(DebugSettingsFragment.this,
                    "Dummy", new RuntimeException("Dummy"));
            return true;
        });

        createErrorNotificationPreference.setOnPreferenceClickListener(preference -> {
            ErrorUtil.createNotification(requireContext(),
                    new ErrorInfo(new RuntimeException("Dummy"), UserAction.UI_ERROR, "Dummy"));
            return true;
        });
    }
}
