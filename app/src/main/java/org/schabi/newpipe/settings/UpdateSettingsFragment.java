package org.schabi.newpipe.settings;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.widget.Toast;

import androidx.preference.Preference;
import androidx.preference.PreferenceManager;

import org.schabi.newpipe.NewVersionWorker;
import org.schabi.newpipe.R;

public class UpdateSettingsFragment extends BasePreferenceFragment {
    private final Preference.OnPreferenceChangeListener updatePreferenceChange = (p, nVal) -> {
        final boolean checkForUpdates = (boolean) nVal;
        defaultPreferences.edit()
                .putBoolean(getString(R.string.update_app_key), checkForUpdates)
                .apply();

        if (checkForUpdates) {
            NewVersionWorker.enqueueNewVersionCheckingWork(requireContext(), true);
        }
        return true;
    };

    private final Preference.OnPreferenceClickListener manualUpdateClick = preference -> {
        Toast.makeText(getContext(), R.string.checking_updates_toast, Toast.LENGTH_SHORT).show();
        NewVersionWorker.enqueueNewVersionCheckingWork(requireContext(), true);
        return true;
    };

    @Override
    public void onCreatePreferences(final Bundle savedInstanceState, final String rootKey) {
        addPreferencesFromResourceRegistry();

        findPreference(getString(R.string.update_app_key))
                .setOnPreferenceChangeListener(updatePreferenceChange);
        findPreference(getString(R.string.manual_update_key))
                .setOnPreferenceClickListener(manualUpdateClick);
    }

    public static void askForConsentToUpdateChecks(final Context context) {
        new AlertDialog.Builder(context)
                .setTitle(context.getString(R.string.check_for_updates))
                .setMessage(context.getString(R.string.auto_update_check_description))
                .setPositiveButton(context.getString(R.string.yes), (d, w) -> {
                    d.dismiss();
                    setAutoUpdateCheckEnabled(context, true);
                })
                .setNegativeButton(R.string.no, (d, w) -> {
                    d.dismiss();
                    // set explicitly to false, since the default is true on previous versions
                    setAutoUpdateCheckEnabled(context, false);
                })
                .show();
    }

    private static void setAutoUpdateCheckEnabled(final Context context, final boolean enabled) {
        PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .putBoolean(context.getString(R.string.update_app_key), enabled)
                .putBoolean(context.getString(R.string.update_check_consent_key), true)
                .apply();
    }

    /**
     * Whether the user was asked for consent to automatically check for app updates.
     * @param context
     * @return true if the user was asked for consent, false otherwise
     */
    public static boolean wasUserAskedForConsent(final Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(context.getString(R.string.update_check_consent_key), false);
    }
}
