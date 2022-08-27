package org.schabi.newpipe.settings;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.preference.Preference;

import org.schabi.newpipe.R;

public class ReturnYouTubeDislikeSettingsFragment extends BasePreferenceFragment {

    @Override
    public void onCreatePreferences(final Bundle savedInstanceState, final String rootKey) {
        addPreferencesFromResourceRegistry();

        final Preference rydWebsitePreference =
                findPreference(getString(R.string.ryd_home_page_key));
        rydWebsitePreference.setOnPreferenceClickListener((Preference p) -> {
            final Intent i = new Intent(Intent.ACTION_VIEW,
                    Uri.parse(getString(R.string.ryd_home_page_url)));
            startActivity(i);
            return true;
        });

        final Preference rydSecurityFaqPreference =
                findPreference(getString(R.string.ryd_security_faq_key));
        rydSecurityFaqPreference.setOnPreferenceClickListener((Preference p) -> {
            final Intent i = new Intent(Intent.ACTION_VIEW,
                    Uri.parse(getString(R.string.ryd_security_faq_url)));
            startActivity(i);
            return true;
        });
    }
}
