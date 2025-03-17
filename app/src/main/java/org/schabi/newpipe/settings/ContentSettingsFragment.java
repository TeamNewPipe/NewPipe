package org.schabi.newpipe.settings;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.preference.Preference;

import org.schabi.newpipe.DownloaderImpl;
import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.localization.ContentCountry;
import org.schabi.newpipe.extractor.localization.Localization;
import org.schabi.newpipe.util.image.ImageStrategy;
import org.schabi.newpipe.util.image.PicassoHelper;
import org.schabi.newpipe.util.image.PreferredImageQuality;

import java.io.IOException;
import java.util.Locale;

public class ContentSettingsFragment extends BasePreferenceFragment {
    private String youtubeRestrictedModeEnabledKey;

    private Localization initialSelectedLocalization;
    private ContentCountry initialSelectedContentCountry;
    private String initialLanguage;

    @Override
    public void onCreatePreferences(final Bundle savedInstanceState, final String rootKey) {
        youtubeRestrictedModeEnabledKey = getString(R.string.youtube_restricted_mode_enabled);

        addPreferencesFromResourceRegistry();

        initialSelectedLocalization = org.schabi.newpipe.util.Localization
                .getPreferredLocalization(requireContext());
        initialSelectedContentCountry = org.schabi.newpipe.util.Localization
                .getPreferredContentCountry(requireContext());
        initialLanguage = defaultPreferences.getString(getString(R.string.app_language_key), "en");

        if (Build.VERSION.SDK_INT >= 33) {
            requirePreference(R.string.app_language_key).setVisible(false);
            final Preference newAppLanguagePref =
                    requirePreference(R.string.app_language_android_13_and_up_key);
            newAppLanguagePref.setSummaryProvider(preference -> {
                final Locale customLocale = AppCompatDelegate.getApplicationLocales().get(0);
                if (customLocale != null) {
                    return customLocale.getDisplayName();
                }
                return getString(R.string.systems_language);
            });
            newAppLanguagePref.setOnPreferenceClickListener(preference -> {
                final Intent intent = new Intent(Settings.ACTION_APP_LOCALE_SETTINGS)
                        .setData(Uri.fromParts("package", requireContext().getPackageName(), null));
                startActivity(intent);
                return true;
            });
            newAppLanguagePref.setVisible(true);
        }

        final Preference imageQualityPreference = requirePreference(R.string.image_quality_key);
        imageQualityPreference.setOnPreferenceChangeListener(
                (preference, newValue) -> {
                    ImageStrategy.setPreferredImageQuality(PreferredImageQuality
                            .fromPreferenceKey(requireContext(), (String) newValue));
                    try {
                        PicassoHelper.clearCache(preference.getContext());
                        Toast.makeText(preference.getContext(),
                                R.string.thumbnail_cache_wipe_complete_notice, Toast.LENGTH_SHORT)
                                .show();
                    } catch (final IOException e) {
                        Log.e(TAG, "Unable to clear Picasso cache", e);
                    }
                    return true;
                });
    }

    @Override
    public boolean onPreferenceTreeClick(final Preference preference) {
        if (preference.getKey().equals(youtubeRestrictedModeEnabledKey)) {
            final Context context = getContext();
            if (context != null) {
                DownloaderImpl.getInstance().updateYoutubeRestrictedModeCookies(context);
            } else {
                Log.w(TAG, "onPreferenceTreeClick: null context");
            }
        }

        return super.onPreferenceTreeClick(preference);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        final Localization selectedLocalization = org.schabi.newpipe.util.Localization
                .getPreferredLocalization(requireContext());
        final ContentCountry selectedContentCountry = org.schabi.newpipe.util.Localization
                .getPreferredContentCountry(requireContext());
        final String selectedLanguage =
                defaultPreferences.getString(getString(R.string.app_language_key), "en");

        if (!selectedLocalization.equals(initialSelectedLocalization)
                || !selectedContentCountry.equals(initialSelectedContentCountry)
                || !selectedLanguage.equals(initialLanguage)) {
            if (Build.VERSION.SDK_INT < 33) {
                Toast.makeText(
                        requireContext(),
                        R.string.localization_changes_requires_app_restart,
                        Toast.LENGTH_LONG
                ).show();
            }
            NewPipe.setupLocalization(selectedLocalization, selectedContentCountry);
        }
    }
}
