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
import androidx.core.os.LocaleListCompat;
import androidx.preference.Preference;

import org.schabi.newpipe.DownloaderImpl;
import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.util.Localization;
import org.schabi.newpipe.util.image.ImageStrategy;
import org.schabi.newpipe.util.image.PicassoHelper;
import org.schabi.newpipe.util.image.PreferredImageQuality;

import java.io.IOException;
import java.util.Locale;

public class ContentSettingsFragment extends BasePreferenceFragment {
    private String youtubeRestrictedModeEnabledKey;

    @Override
    public void onCreatePreferences(final Bundle savedInstanceState, final String rootKey) {
        youtubeRestrictedModeEnabledKey = getString(R.string.youtube_restricted_mode_enabled);

        addPreferencesFromResourceRegistry();

        final var appLanguagePref = requirePreference(R.string.app_language_key);
        if (Build.VERSION.SDK_INT >= 33) {
            appLanguagePref.setVisible(false);
            final var newAppLanguagePref =
                    requirePreference(R.string.app_language_android_13_and_up_key);
            newAppLanguagePref.setSummaryProvider(preference -> {
                final Locale loc = AppCompatDelegate.getApplicationLocales().get(0);
                return loc != null ? loc.getDisplayName() : getString(R.string.systems_language);
            });
            newAppLanguagePref.setOnPreferenceClickListener(preference -> {
                final Intent intent = new Intent(Settings.ACTION_APP_LOCALE_SETTINGS)
                        .setData(Uri.fromParts("package", requireContext().getPackageName(), null));
                startActivity(intent);
                return true;
            });
            newAppLanguagePref.setVisible(true);
        } else {
            appLanguagePref.setOnPreferenceChangeListener((preference, newValue) -> {
                final String language = (String) newValue;
                final String systemLang = getString(R.string.default_localization_key);
                final String tag = systemLang.equals(language) ? null : language;
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag));
                return true;
            });
        }

        requirePreference(R.string.image_quality_key).setOnPreferenceChangeListener(
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

        final var context = requireContext();
        final var selectedLocalization = Localization.getPreferredLocalization(context);
        final var selectedContentCountry = Localization.getPreferredContentCountry(context);
        NewPipe.setupLocalization(selectedLocalization, selectedContentCountry);
    }
}
