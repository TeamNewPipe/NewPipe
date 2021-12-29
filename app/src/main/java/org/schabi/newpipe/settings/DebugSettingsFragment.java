package org.schabi.newpipe.settings;

import android.content.Intent;
import android.os.Bundle;

import androidx.preference.Preference;

import org.schabi.newpipe.R;
import org.schabi.newpipe.error.ErrorInfo;
import org.schabi.newpipe.error.ErrorUtil;
import org.schabi.newpipe.error.UserAction;
import org.schabi.newpipe.util.PicassoHelper;

import java.util.Optional;

public class DebugSettingsFragment extends BasePreferenceFragment {
    @Override
    public void onCreatePreferences(final Bundle savedInstanceState, final String rootKey) {
        addPreferencesFromResourceRegistry();

        final Preference allowHeapDumpingPreference
                = findPreference(getString(R.string.allow_heap_dumping_key));
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

        assert allowHeapDumpingPreference != null;
        assert showMemoryLeaksPreference != null;
        assert showImageIndicatorsPreference != null;
        assert crashTheAppPreference != null;
        assert showErrorSnackbarPreference != null;
        assert createErrorNotificationPreference != null;

        final Optional<DebugSettingsBVLeakCanaryAPI> optPDLeakCanary = getBVLeakCanary();

        allowHeapDumpingPreference.setEnabled(optPDLeakCanary.isPresent());
        showMemoryLeaksPreference.setEnabled(optPDLeakCanary.isPresent());

        if (optPDLeakCanary.isPresent()) {
            final DebugSettingsBVLeakCanaryAPI pdLeakCanary = optPDLeakCanary.get();

            showMemoryLeaksPreference.setOnPreferenceClickListener(preference -> {
                startActivity(pdLeakCanary.getNewLeakDisplayActivityIntent());
                return true;
            });
        } else {
            allowHeapDumpingPreference.setSummary(R.string.leak_canary_not_available);
            showMemoryLeaksPreference.setSummary(R.string.leak_canary_not_available);
        }

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

    private Optional<DebugSettingsBVLeakCanaryAPI> getBVLeakCanary() {
        try {
            // Try to find the implementation of the LeakCanary API
            return Optional.of((DebugSettingsBVLeakCanaryAPI)
                    Class.forName(DebugSettingsBVLeakCanaryAPI.IMPL_CLASS).newInstance());
        } catch (final ClassNotFoundException
                | IllegalAccessException | java.lang.InstantiationException e) {
            return Optional.empty();
        }
    }

    /**
     * Build variant dependent leak canary API for this fragment.
     * Why is LeakCanary not used directly? Because it can't be assured
     */
    public interface DebugSettingsBVLeakCanaryAPI {
        String IMPL_CLASS =
                "org.schabi.newpipe.settings.DebugSettingsBVLeakCanary";

        Intent getNewLeakDisplayActivityIntent();
    }
}
