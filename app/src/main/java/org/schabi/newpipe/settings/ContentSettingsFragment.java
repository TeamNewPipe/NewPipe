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
import org.schabi.newpipe.util.image.PreferredImageQuality;

import java.util.Locale;

import coil3.SingletonImageLoader;

public class ContentSettingsFragment extends BasePreferenceFragment {
    private String youtubeRestrictedModeEnabledKey;

    private String initialLanguage;

    @Override
    public void onCreatePreferences(final Bundle savedInstanceState, final String rootKey) {
        youtubeRestrictedModeEnabledKey = getString(R.string.youtube_restricted_mode_enabled);

        addPreferencesFromResourceRegistry();

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
                    final var loader = SingletonImageLoader.get(preference.getContext());
                    loader.getMemoryCache().clear();
                    loader.getDiskCache().clear();
                    Toast.makeText(preference.getContext(),
                            R.string.thumbnail_cache_wipe_complete_notice, Toast.LENGTH_SHORT)
                            .show();

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

        final String selectedLanguage =
                defaultPreferences.getString(getString(R.string.app_language_key), "en");

        if (!selectedLanguage.equals(initialLanguage)) {
            if (Build.VERSION.SDK_INT < 33) {
                Toast.makeText(
                        requireContext(),
                        R.string.localization_changes_requires_app_restart,
                        Toast.LENGTH_LONG
                ).show();
            }
            final Localization selectedLocalization = org.schabi.newpipe.util.Localization
                    .getPreferredLocalization(requireContext());
            final ContentCountry selectedContentCountry = org.schabi.newpipe.util.Localization
                    .getPreferredContentCountry(requireContext());
            NewPipe.setupLocalization(selectedLocalization, selectedContentCountry);
        }
    }
}
