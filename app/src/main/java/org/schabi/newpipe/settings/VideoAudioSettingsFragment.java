package org.schabi.newpipe.settings;

import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.format.DateUtils;
import android.widget.Toast;

import androidx.core.os.ConfigurationCompat;
import androidx.core.os.LocaleListCompat;
import androidx.preference.ListPreference;
import androidx.preference.MultiSelectListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.SwitchPreferenceCompat;

import com.google.android.material.snackbar.Snackbar;

import org.schabi.newpipe.R;
import org.schabi.newpipe.player.equalizer.EqualizerDialog;
import org.schabi.newpipe.player.equalizer.EqualizerPreferences;
import org.schabi.newpipe.player.equalizer.EqualizerState;
import org.schabi.newpipe.util.ListHelper;
import org.schabi.newpipe.util.PermissionHelper;

import java.text.Collator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

public class VideoAudioSettingsFragment extends BasePreferenceFragment {
    private SharedPreferences.OnSharedPreferenceChangeListener listener;

    @Override
    public void onCreatePreferences(final Bundle savedInstanceState, final String rootKey) {
        org.schabi.newpipe.player.helper.PlayerRotationMode.initializePreference(requireContext());
        addPreferencesFromResourceRegistry();

        org.schabi.newpipe.player.helper.CustomPlaybackControls.bind(this);
        updateSeekOptions();
        updateResolutionOptions();
        setupEqualizerPreference();
        requirePreference(R.string.portrait_player_actions_key)
                .setOnPreferenceClickListener(preference -> {
                    org.schabi.newpipe.player.ui.PortraitPlayerActionsDialog.show(requireContext());
                    return true;
                });
        org.schabi.newpipe.util.external_communication.YtFifoUtils.configurePreference(
                requirePreference(R.string.yt_fifo_url_key));
        setupCaptionTranslationPreferences();
        setupPreferredPlaybackLanguages();
        requirePreference(R.string.native_pip_key)
                .setVisible(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O);
        requirePreference(R.string.primary_floating_player_action_key)
                .setVisible(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O);
        listener = (sharedPreferences, key) -> {

            // on M and above, if user chooses to minimise to popup player on exit
            // and the app doesn't have display over other apps permission,
            // show a snackbar to let the user give permission
            if (getString(R.string.minimize_on_exit_key).equals(key)) {
                final String newSetting = sharedPreferences.getString(key, null);
                if (newSetting != null
                        && newSetting.equals(getString(R.string.minimize_on_exit_popup_key))
                        && !Settings.canDrawOverlays(getContext())) {

                    Snackbar.make(getListView(), R.string.permission_display_over_apps,
                            Snackbar.LENGTH_INDEFINITE)
                            .setAction(R.string.settings, view ->
                                    PermissionHelper.checkSystemAlertWindowPermission(getContext()))
                            .show();

                }
            } else if (getString(R.string.use_inexact_seek_key).equals(key)) {
                updateSeekOptions();
            } else if (getString(R.string.show_higher_resolutions_key).equals(key)) {
                updateResolutionOptions();
            } else if (getString(R.string.caption_auto_translate_key).equals(key)
                    || getString(R.string.caption_translation_language_key).equals(key)) {
                CaptionTranslationPreferences.syncPreferredCaptionLanguage(requireContext());
            }
        };
    }

    private void setupPreferredPlaybackLanguages() {
        final MultiSelectListPreference preference =
                new MultiSelectListPreference(requireContext());
        preference.setKey(PreferredPlaybackLanguages.KEY);
        preference.setTitle(R.string.preferred_playback_languages);
        preference.setSummary(R.string.preferred_playback_languages_summary);
        preference.setIconSpaceReserved(false);
        final ListPreference languages = new ListPreference(requireContext());
        populateCaptionTranslationLanguages(languages);
        preference.setEntries(java.util.Arrays.copyOfRange(languages.getEntries(),
                1, languages.getEntries().length));
        preference.setEntryValues(java.util.Arrays.copyOfRange(languages.getEntryValues(),
                1, languages.getEntryValues().length));
        getPreferenceScreen().addPreference(preference);
    }

    private void setupCaptionTranslationPreferences() {
        final PreferenceCategory category = new PreferenceCategory(requireContext());
        category.setLayoutResource(R.layout.settings_category_header_layout);
        category.setTitle(R.string.caption_translation_category_title);
        category.setIconSpaceReserved(false);
        getPreferenceScreen().addPreference(category);

        final SwitchPreferenceCompat autoTranslate = new SwitchPreferenceCompat(requireContext());
        autoTranslate.setKey(getString(R.string.caption_auto_translate_key));
        autoTranslate.setDefaultValue(false);
        autoTranslate.setTitle(R.string.caption_auto_translate_title);
        autoTranslate.setSummary(R.string.caption_auto_translate_summary);
        autoTranslate.setIconSpaceReserved(false);
        autoTranslate.setSingleLineTitle(false);
        category.addPreference(autoTranslate);

        final ListPreference language = new ListPreference(requireContext());
        language.setKey(getString(R.string.caption_translation_language_key));
        language.setDefaultValue(getString(R.string.caption_translation_system_value));
        language.setTitle(R.string.caption_translation_language_title);
        language.setIconSpaceReserved(false);
        language.setSingleLineTitle(false);
        language.setSummaryProvider(ListPreference.SimpleSummaryProvider.getInstance());
        populateCaptionTranslationLanguages(language);
        category.addPreference(language);
        // Programmatic preferences need to be attached before setDependency() can resolve
        // their dependency through the PreferenceManager hierarchy.
        language.setDependency(getString(R.string.caption_auto_translate_key));
    }

    private void populateCaptionTranslationLanguages(final ListPreference preference) {
        final LocaleListCompat configuredLocales = ConfigurationCompat.getLocales(
                getResources().getConfiguration());
        final Locale displayLocale = configuredLocales.isEmpty() || configuredLocales.get(0) == null
                ? Locale.getDefault() : configuredLocales.get(0);
        final Collator collator = Collator.getInstance(displayLocale);
        final Map<String, String> languages = new TreeMap<>(collator::compare);

        for (final String languageCode : Locale.getISOLanguages()) {
            final Locale locale = Locale.forLanguageTag(languageCode);
            final String displayName = locale.getDisplayLanguage(displayLocale);
            if (!displayName.isEmpty()) {
                languages.putIfAbsent(displayName, languageCode);
            }
        }

        final CharSequence[] entries = new CharSequence[languages.size() + 1];
        final CharSequence[] values = new CharSequence[languages.size() + 1];
        entries[0] = getString(R.string.caption_translation_system_language);
        values[0] = getString(R.string.caption_translation_system_value);

        int index = 1;
        for (final Map.Entry<String, String> language : languages.entrySet()) {
            entries[index] = language.getKey();
            values[index] = language.getValue();
            index++;
        }
        preference.setEntries(entries);
        preference.setEntryValues(values);
    }

    private void setupEqualizerPreference() {
        final Preference preference = requirePreference(R.string.equalizer_settings_key);
        updateEqualizerPreferenceSummary(preference);
        preference.setOnPreferenceClickListener(clicked -> {
            EqualizerDialog.show(
                    requireContext(),
                    EqualizerDialog.forPreferences(requireContext()),
                    () -> updateEqualizerPreferenceSummary(preference));
            return true;
        });
    }

    private void updateEqualizerPreferenceSummary(final Preference preference) {
        final EqualizerState state = new EqualizerPreferences(requireContext()).load();
        if (!state.isEnabled()) {
            preference.setSummary(R.string.equalizer_settings_summary_disabled);
            return;
        }
        preference.setSummary(getString(
                R.string.equalizer_settings_summary_enabled,
                EqualizerDialog.getPresetDisplayName(requireContext(), state.getPreset())));
    }

    /**
     * Update default resolution, default popup resolution & mobile data resolution options.
     * <br />
     * Show high resolutions when "Show higher resolution" option is enabled.
     * Set default resolution to "best resolution" when "Show higher resolution" option
     * is disabled.
     */
    private void updateResolutionOptions() {
        final Resources resources = getResources();
        final boolean showHigherResolutions =  getPreferenceManager().getSharedPreferences()
                .getBoolean(resources.getString(R.string.show_higher_resolutions_key), false);

        // get sorted resolution lists
        final List<String> resolutionListDescriptions = ListHelper.getSortedResolutionList(
                resources,
                R.array.resolution_list_description,
                R.array.high_resolution_list_descriptions,
                showHigherResolutions);
        final List<String> resolutionListValues = ListHelper.getSortedResolutionList(
                resources,
                R.array.resolution_list_values,
                R.array.high_resolution_list_values,
                showHigherResolutions);
        final List<String> limitDataUsageResolutionValues = ListHelper.getSortedResolutionList(
                resources,
                R.array.limit_data_usage_values_list,
                R.array.high_resolution_limit_data_usage_values_list,
                showHigherResolutions);
        final List<String> limitDataUsageResolutionDescriptions = ListHelper
                .getSortedResolutionList(resources,
                R.array.limit_data_usage_description_list,
                R.array.high_resolution_list_descriptions,
                showHigherResolutions);

        // get resolution preferences
        final ListPreference defaultResolution = requirePreference(
                R.string.default_resolution_key);
        final ListPreference defaultPopupResolution = requirePreference(
                R.string.default_popup_resolution_key);
        final ListPreference mobileDataResolution = requirePreference(
                R.string.limit_mobile_data_usage_key);

        // update resolution preferences with new resolutions, entries & values for each
        defaultResolution.setEntries(resolutionListDescriptions.toArray(new String[0]));
        defaultResolution.setEntryValues(resolutionListValues.toArray(new String[0]));
        defaultPopupResolution.setEntries(resolutionListDescriptions.toArray(new String[0]));
        defaultPopupResolution.setEntryValues(resolutionListValues.toArray(new String[0]));
        mobileDataResolution.setEntries(
                limitDataUsageResolutionDescriptions.toArray(new String[0]));
        mobileDataResolution.setEntryValues(limitDataUsageResolutionValues.toArray(new String[0]));

        // if "Show higher resolution" option is disabled,
        // set default resolution to "best resolution"
        if (!showHigherResolutions) {
            if (ListHelper.isHighResolutionSelected(defaultResolution.getValue(),
                    R.array.high_resolution_list_values,
                    resources)) {
                defaultResolution.setValueIndex(0);
            }
            if (ListHelper.isHighResolutionSelected(defaultPopupResolution.getValue(),
                    R.array.high_resolution_list_values,
                    resources)) {
                defaultPopupResolution.setValueIndex(0);
            }
            if (ListHelper.isHighResolutionSelected(mobileDataResolution.getValue(),
                    R.array.high_resolution_limit_data_usage_values_list,
                    resources)) {
                mobileDataResolution.setValueIndex(0);
            }
        }
    }

    /**
     * Update fast-forward/-rewind seek duration options
     * according to language and inexact seek setting.
     * Exoplayer can't seek 5 seconds in audio when using inexact seek.
     */
    private void updateSeekOptions() {
        // initializing R.array.seek_duration_description to display the translation of seconds
        final Resources res = getResources();
        final String[] durationsValues = res.getStringArray(R.array.seek_duration_value);
        final List<String> displayedDurationValues = new LinkedList<>();
        final List<String> displayedDescriptionValues = new LinkedList<>();
        int currentDurationValue;
        final boolean inexactSeek = getPreferenceManager().getSharedPreferences()
                .getBoolean(res.getString(R.string.use_inexact_seek_key), false);

        for (final String durationsValue : durationsValues) {
            currentDurationValue =
                    Integer.parseInt(durationsValue) / (int) DateUtils.SECOND_IN_MILLIS;
            if (inexactSeek && currentDurationValue % 10 == 5) {
                continue;
            }

            displayedDurationValues.add(durationsValue);
            try {
                displayedDescriptionValues.add(String.format(
                        res.getQuantityString(R.plurals.seconds,
                                currentDurationValue),
                        currentDurationValue));
            } catch (final Resources.NotFoundException ignored) {
                // if this happens, the translation is missing,
                // and the english string will be displayed instead
            }
        }

        final ListPreference durations = requirePreference(R.string.seek_duration_key);
        durations.setEntryValues(displayedDurationValues.toArray(new CharSequence[0]));
        durations.setEntries(displayedDescriptionValues.toArray(new CharSequence[0]));
        final int selectedDuration = Integer.parseInt(durations.getValue());
        if (inexactSeek && selectedDuration / (int) DateUtils.SECOND_IN_MILLIS % 10 == 5) {
            final int newDuration = selectedDuration / (int) DateUtils.SECOND_IN_MILLIS + 5;
            durations.setValue(Integer.toString(newDuration * (int) DateUtils.SECOND_IN_MILLIS));

            final Toast toast = Toast
                    .makeText(getContext(),
                            getString(R.string.new_seek_duration_toast, newDuration),
                            Toast.LENGTH_LONG);
            toast.show();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        getPreferenceManager().getSharedPreferences()
                .registerOnSharedPreferenceChangeListener(listener);

    }

    @Override
    public void onPause() {
        super.onPause();
        getPreferenceManager().getSharedPreferences()
                .unregisterOnSharedPreferenceChangeListener(listener);
    }
}
