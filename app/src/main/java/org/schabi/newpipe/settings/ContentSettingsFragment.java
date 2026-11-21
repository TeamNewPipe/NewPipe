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
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.PreferenceManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.schabi.newpipe.extractor.services.youtube.invidious.InvidiousBackend;
import org.schabi.newpipe.util.NavigationHelper;

import org.schabi.newpipe.DownloaderImpl;
import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.player.helper.PlayerHelper;
import org.schabi.newpipe.util.Localization;
import org.schabi.newpipe.util.image.ImageStrategy;
import org.schabi.newpipe.util.image.PreferredImageQuality;

import java.util.List;
import java.util.Locale;

import coil3.SingletonImageLoader;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class ContentSettingsFragment extends BasePreferenceFragment {
    private final CompositeDisposable invidiousDirectoryDisposables = new CompositeDisposable();
    private String youtubeRestrictedModeEnabledKey;

    @Override
    public void onCreatePreferences(final Bundle savedInstanceState, final String rootKey) {
        youtubeRestrictedModeEnabledKey = getString(R.string.youtube_restricted_mode_enabled);

        addPreferencesFromResourceRegistry();

        setupAppLanguagePreferences();
        setupImageQualityPref();
        setupInvidiousPreferences();
        requirePreference(R.string.comment_archive_clear_key)
                .setOnPreferenceClickListener(preference -> {
                    new MaterialAlertDialogBuilder(requireContext())
                            .setTitle(R.string.comment_archive_clear_title)
                            .setMessage(R.string.comment_archive_clear_confirm)
                            .setNegativeButton(android.R.string.cancel, null)
                            .setPositiveButton(R.string.comment_archive_clear_title,
                                    (dialog, which) -> clearOfflineComments())
                            .show();
                    return true;
                });
    }

    private void clearOfflineComments() {
        final Context appContext = requireContext().getApplicationContext();
        invidiousDirectoryDisposables.add(
                io.reactivex.rxjava3.core.Completable.fromAction(() ->
                        org.schabi.newpipe.local.comments.CommentArchive.clear(appContext))
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                                () -> Toast.makeText(requireContext(),
                                        R.string.comment_archive_cleared,
                                        Toast.LENGTH_SHORT).show(),
                                error -> Log.e(TAG, "Could not clear comment cache", error)
                        )
        );
    }

    private void setupInvidiousPreferences() {
        final var preferences = PreferenceManager.getDefaultSharedPreferences(requireContext());
        final String enabledKey = getString(R.string.invidious_enabled_key);
        final String instanceKey = getString(R.string.invidious_instance_key);
        final EditTextPreference instancePref = (EditTextPreference) requirePreference(
                R.string.invidious_instance_key);
        instancePref.setOnBindEditTextListener(editText -> editText.setInputType(
                android.text.InputType.TYPE_CLASS_TEXT
                        | android.text.InputType.TYPE_TEXT_VARIATION_URI));
        instancePref.setOnPreferenceChangeListener((preference, newValue) ->
                selectInvidiousInstance((String) newValue));
        final EditTextPreference savedPref = (EditTextPreference) requirePreference(
                R.string.invidious_instances_key);
        savedPref.setOnBindEditTextListener(editText -> {
            editText.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                    | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
                    | android.text.InputType.TYPE_TEXT_VARIATION_URI);
            editText.setSingleLine(false);
            editText.setMinLines(3);
        });
        savedPref.setOnPreferenceChangeListener((preference, newValue) -> {
            try {
                final String normalized = String.join("\n",
                        InvidiousInstances.parse((String) newValue));
                savedPref.setText(normalized);
                refreshInvidiousChoices();
            } catch (final IllegalArgumentException error) {
                Toast.makeText(requireContext(), R.string.invidious_invalid_instance,
                        Toast.LENGTH_LONG).show();
            }
            return false;
        });
        requirePreference(R.string.invidious_switch_instance_key)
                .setOnPreferenceChangeListener((preference, newValue) ->
                        selectInvidiousInstance((String) newValue));
        requirePreference(R.string.invidious_directory_key)
                .setOnPreferenceClickListener(preference -> {
                    loadInvidiousDirectory();
                    return true;
                });
        refreshInvidiousChoices();
        requirePreference(R.string.invidious_enabled_key)
                .setOnPreferenceChangeListener((preference, newValue) -> {
            final boolean enabled = (Boolean) newValue;
            if (enabled) {
                try {
                    InvidiousBackend.normalizeInstance(preferences.getString(instanceKey, ""));
                } catch (final IllegalArgumentException e) {
                    Toast.makeText(requireContext(), R.string.invidious_invalid_instance,
                            Toast.LENGTH_LONG).show();
                    return false;
                }
            }
            if (preferences.edit().putBoolean(enabledKey, enabled).commit()) {
                NavigationHelper.restartApp(requireActivity());
            }
            return false;
        });
    }

    private void loadInvidiousDirectory() {
        final Preference directoryPreference = requirePreference(
                R.string.invidious_directory_key);
        directoryPreference.setEnabled(false);
        directoryPreference.setSummary(R.string.invidious_directory_loading);
        invidiousDirectoryDisposables.add(Single.fromCallable(
                InvidiousInstanceDirectory::load)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(entries -> {
                    restoreInvidiousDirectoryPreference();
                    if (entries.isEmpty()) {
                        Toast.makeText(requireContext(), R.string.invidious_directory_empty,
                                Toast.LENGTH_LONG).show();
                    } else {
                        showInvidiousDirectory(entries);
                    }
                }, error -> {
                    restoreInvidiousDirectoryPreference();
                    Log.w(TAG, "Could not load the official Invidious directory", error);
                    Toast.makeText(requireContext(), R.string.invidious_directory_load_failed,
                            Toast.LENGTH_LONG).show();
                }));
    }

    private void restoreInvidiousDirectoryPreference() {
        if (!isAdded()) {
            return;
        }
        final Preference preference = requirePreference(R.string.invidious_directory_key);
        preference.setEnabled(true);
        preference.setSummary(R.string.invidious_directory_summary);
    }

    private void showInvidiousDirectory(
            final List<InvidiousInstanceDirectory.Entry> entries) {
        final CharSequence[] labels = new CharSequence[entries.size()];
        for (int index = 0; index < entries.size(); index++) {
            final InvidiousInstanceDirectory.Entry entry = entries.get(index);
            labels[index] = getString(R.string.invidious_directory_entry,
                    entry.getFlag(), entry.getHost(), entry.getRegion(), entry.getUptime());
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.invidious_directory_picker_title)
                .setItems(labels, (dialog, which) ->
                        confirmInvidiousDirectorySelection(entries.get(which)))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void confirmInvidiousDirectorySelection(
            final InvidiousInstanceDirectory.Entry entry) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.invidious_directory_warning_title)
                .setMessage(getString(R.string.invidious_directory_warning, entry.getUrl()))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.invidious_directory_select,
                        (dialog, which) -> selectInvidiousInstance(entry.getUrl()))
                .show();
    }

    private boolean selectInvidiousInstance(final String value) {
        final var preferences = PreferenceManager.getDefaultSharedPreferences(requireContext());
        final String instanceKey = getString(R.string.invidious_instance_key);
        final String savedKey = getString(R.string.invidious_instances_key);
        final String normalized;
        try {
            normalized = InvidiousBackend.normalizeInstance(value);
        } catch (final IllegalArgumentException error) {
            Toast.makeText(requireContext(), R.string.invidious_invalid_instance,
                    Toast.LENGTH_LONG).show();
            return false;
        }
        final String active = preferences.getString(instanceKey, "");
        final var instances = InvidiousInstances.choices(preferences.getString(savedKey, ""),
                active);
        if (!instances.contains(normalized)) {
            instances.add(normalized);
        }
        final String saved = String.join("\n", instances);
        if (preferences.edit().putString(instanceKey, normalized).putString(savedKey, saved)
                .commit()) {
            ((EditTextPreference) requirePreference(R.string.invidious_instance_key))
                    .setText(normalized);
            ((EditTextPreference) requirePreference(R.string.invidious_instances_key))
                    .setText(saved);
            refreshInvidiousChoices();
            if (!normalized.equals(active)
                    && preferences.getBoolean(getString(R.string.invidious_enabled_key), false)) {
                NavigationHelper.restartApp(requireActivity());
            }
        }
        return false;
    }

    private void refreshInvidiousChoices() {
        final var preferences = PreferenceManager.getDefaultSharedPreferences(requireContext());
        final String active = preferences.getString(getString(R.string.invidious_instance_key), "");
        InvidiousMediaPreferences.bind(requireActivity(),
                (EditTextPreference) requirePreference(R.string.invidious_media_origins_key),
                active);
        final var instances = InvidiousInstances.choices(preferences.getString(
                getString(R.string.invidious_instances_key), ""), active);
        final ListPreference switchPref = (ListPreference) requirePreference(
                R.string.invidious_switch_instance_key);
        final String[] choices = instances.toArray(new String[0]);
        switchPref.setEntries(choices);
        switchPref.setEntryValues(choices);
        switchPref.setValue(active);
        switchPref.setEnabled(!instances.isEmpty());
        switchPref.setSummary(active.isEmpty()
                ? getString(R.string.invidious_switch_instance_summary) : active);
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
            final String tag;
            if (systemLang.equals(language)) {
                final Locale systemLocale = LocaleListCompat.getAdjustedDefault().get(0);
                tag = (systemLocale == null ? Locale.getDefault() : systemLocale).toLanguageTag();
            } else {
                tag = language;
            }
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
        invidiousDirectoryDisposables.clear();
        super.onDestroy();

        final Context context = requireContext();
        NewPipe.setupLocalization(
            Localization.getPreferredLocalization(context),
            Localization.getPreferredContentCountry(context));
        PlayerHelper.resetFormat();
    }
}
