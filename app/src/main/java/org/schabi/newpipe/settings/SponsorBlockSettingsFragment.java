package org.schabi.newpipe.settings;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;

import org.schabi.newpipe.R;

import java.util.HashSet;

public class SponsorBlockSettingsFragment extends BasePreferenceFragment {

    @Override
    public void onCreatePreferences(final Bundle savedInstanceState, final String rootKey) {
        addPreferencesFromResource(R.xml.sponsor_block_settings);

        final Preference sponsorBlockWebsitePreference =
                findPreference(getString(R.string.sponsor_block_home_page_key));
        sponsorBlockWebsitePreference.setOnPreferenceClickListener((Preference p) -> {
            final Intent i = new Intent(Intent.ACTION_VIEW,
                    Uri.parse(getString(R.string.sponsor_block_homepage_url)));
            startActivity(i);
            return true;
        });

        final Preference sponsorBlockPrivacyPreference =
                findPreference(getString(R.string.sponsor_block_privacy_key));
        sponsorBlockPrivacyPreference.setOnPreferenceClickListener((Preference p) -> {
            final Intent i = new Intent(Intent.ACTION_VIEW,
                    Uri.parse(getString(R.string.sponsor_block_privacy_policy_url)));
            startActivity(i);
            return true;
        });

        final Preference sponsorBlockApiUrlPreference =
                findPreference(getString(R.string.sponsor_block_api_url_key));
        sponsorBlockApiUrlPreference
                .setOnPreferenceChangeListener((preference, newValue) -> {
                    updateDependencies(preference, newValue);
                    return true;
                });

        final Preference sponsorBlockClearWhitelistPreference =
                findPreference(getString(R.string.sponsor_block_clear_whitelist_key));
        sponsorBlockClearWhitelistPreference.setOnPreferenceClickListener((Preference p) -> {
            new AlertDialog.Builder(p.getContext())
                    .setMessage(R.string.sponsor_block_confirm_clear_whitelist)
                    .setPositiveButton(R.string.yes, (dialog, which) -> {
                        getPreferenceManager()
                                .getSharedPreferences()
                                .edit()
                                .putStringSet(getString(
                                        R.string.sponsor_block_whitelist_key), new HashSet<>())
                                .apply();
                        Toast.makeText(p.getContext(),
                                R.string.sponsor_block_whitelist_cleared_toast,
                                Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton(R.string.no, (dialog, which) -> {
                        dialog.dismiss();
                    })
                    .show();
            return true;
        });
    }

    @Override
    public void onViewCreated(@NonNull final View view, @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        final Preference sponsorBlockApiUrlPreference =
                findPreference(getString(R.string.sponsor_block_api_url_key));
        final String sponsorBlockApiUrlPreferenceValue =
                getPreferenceManager()
                        .getSharedPreferences()
                        .getString(getString(R.string.sponsor_block_api_url_key), null);
        updateDependencies(sponsorBlockApiUrlPreference, sponsorBlockApiUrlPreferenceValue);
    }

    private void updateDependencies(final Preference preference, final Object newValue) {
        // This is a workaround to force dependency updates for custom preferences.

        // sponsor_block_api_url_key
        if (preference.getKey().equals(getString(R.string.sponsor_block_api_url_key))) {
            findPreference(getString(R.string.sponsor_block_enable_key))
                    .onDependencyChanged(preference,
                            newValue == null || newValue.equals(""));
            findPreference(getString(R.string.sponsor_block_notifications_key))
                    .onDependencyChanged(preference,
                            newValue == null || newValue.equals(""));
            findPreference(getString(R.string.sponsor_block_categories_key))
                    .onDependencyChanged(preference,
                            newValue == null || newValue.equals(""));
            findPreference(getString(R.string.sponsor_block_clear_whitelist_key))
                    .onDependencyChanged(preference,
                            newValue == null || newValue.equals(""));
        }
    }
}
