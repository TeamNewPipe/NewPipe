package org.schabi.newpipe.settings;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;

import org.schabi.newpipe.R;

import java.util.HashSet;
import java.util.Objects;

public class SponsorBlockSettingsFragment extends BasePreferenceFragment {
    @Override
    public void onCreatePreferences(final Bundle savedInstanceState, final String rootKey) {
        addPreferencesFromResourceRegistry();

        final Preference sponsorBlockWebsitePreference =
                findPreference(getString(R.string.sponsor_block_home_page_key));
        assert sponsorBlockWebsitePreference != null;
        sponsorBlockWebsitePreference.setOnPreferenceClickListener((Preference p) -> {
            final Intent i = new Intent(Intent.ACTION_VIEW,
                    Uri.parse(getString(R.string.sponsor_block_homepage_url)));
            startActivity(i);
            return true;
        });

        final Preference sponsorBlockPrivacyPreference =
                findPreference(getString(R.string.sponsor_block_privacy_key));
        assert sponsorBlockPrivacyPreference != null;
        sponsorBlockPrivacyPreference.setOnPreferenceClickListener((Preference p) -> {
            final Intent i = new Intent(Intent.ACTION_VIEW,
                    Uri.parse(getString(R.string.sponsor_block_privacy_policy_url)));
            startActivity(i);
            return true;
        });

        final Preference sponsorBlockClearWhitelistPreference =
                findPreference(getString(R.string.sponsor_block_clear_whitelist_key));
        assert sponsorBlockClearWhitelistPreference != null;
        sponsorBlockClearWhitelistPreference.setOnPreferenceClickListener((Preference p) -> {
            new AlertDialog.Builder(p.getContext())
                    .setMessage(R.string.sponsor_block_confirm_clear_whitelist)
                    .setPositiveButton(R.string.yes, (dialog, which) -> {
                        Objects.requireNonNull(getPreferenceManager()
                                        .getSharedPreferences())
                                .edit()
                                .putStringSet(getString(
                                        R.string.sponsor_block_whitelist_key), new HashSet<>())
                                .apply();
                        Toast.makeText(p.getContext(),
                                R.string.sponsor_block_whitelist_cleared_toast,
                                Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton(R.string.no, (dialog, which) -> dialog.dismiss())
                    .show();
            return true;
        });
    }
}
