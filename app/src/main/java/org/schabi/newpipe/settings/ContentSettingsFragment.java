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
import org.schabi.newpipe.player.helper.PlayerHelper;
import org.schabi.newpipe.util.Localization;
import org.schabi.newpipe.util.image.ImageStrategy;
import org.schabi.newpipe.util.image.PreferredImageQuality;

import java.util.Locale;

import coil3.SingletonImageLoader;

public class ContentSettingsFragment extends BasePreferenceFragment {
    private String youtubeRestrictedModeEnabledKey;

    @Override
    public void onCreatePreferences(final Bundle savedInstanceState, final String rootKey) {
        youtubeRestrictedModeEnabledKey = getString(R.string.youtube_restricted_mode_enabled);

        addPreferencesFromResourceRegistry();

        setupAppLanguagePreferences();
        setupImageQualityPref();
    }

    private void setupAppLanguagePreferences() {
        final Preference appLanguagePref = requirePreference(R.string.app_language_key);
        // Android 13+ allows to set app specific languages
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appLanguagePref.setVisible(false);

            final Preference newAppLanguagePref =
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
            return;
        }

        appLanguagePref.setOnPreferenceChangeListener((preference, newValue) -> {
            final String language = (String) newValue;
            final String systemLang = getString(R.string.default_localization_key);
            final String tag = systemLang.equals(language) ? null : language;
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag));
            return true;
        });
    }

    private void setupImageQualityPref() {
        requirePreference(R.string.image_quality_key).setOnPreferenceChangeListener(
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

        final Context context = requireContext();
        NewPipe.setupLocalization(
            Localization.getPreferredLocalization(context),
            Localization.getPreferredContentCountry(context));
        PlayerHelper.resetFormat();
    }
}
