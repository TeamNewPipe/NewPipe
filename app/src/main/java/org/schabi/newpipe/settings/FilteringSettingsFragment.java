package org.schabi.newpipe.settings;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.SwitchPreferenceCompat;

import org.schabi.newpipe.R;
import org.schabi.newpipe.database.feed.model.FeedGroupEntity;
import org.schabi.newpipe.local.feed.service.FeedLoadService;
import org.schabi.newpipe.util.InfoCache;

public class FilteringSettingsFragment
        extends BasePreferenceFragment
        implements Preference.OnPreferenceChangeListener {
    private Context context;
    private Activity activity;
    private boolean settingChanged;

    @Override
    public void onCreatePreferences(final Bundle savedInstanceState, final String rootKey) {
        setPreferencesFromResource(R.xml.filtering_settings, rootKey);

        requirePreference(R.string.reset_video_filters_key)
                .setOnPreferenceClickListener(preference -> {
                    new AlertDialog
                            .Builder(context)
                            .setMessage(R.string.filter_settings_reset_prompt_message)
                            .setNegativeButton(R.string.cancel, null)
                            .setPositiveButton(R.string.yes, (dialog, which) -> {
                                resetFilters();
                                dialog.dismiss();
                            })
                            .show();
                    return true;
                });

        requirePreference(R.string.simple_video_title_filter_key)
                .setOnPreferenceChangeListener(this);

        requirePreference(R.string.simple_uploader_name_filter_key)
                .setOnPreferenceChangeListener(this);

        requirePreference(R.string.hide_shorts_key)
                .setOnPreferenceChangeListener(this);

        requirePreference(R.string.regex_video_title_filter_key)
                .setOnPreferenceChangeListener(this);

        requirePreference(R.string.regex_uploader_name_filter_key)
                .setOnPreferenceChangeListener(this);
    }

    @Override
    public void onAttach(@NonNull final Context c) {
        super.onAttach(c);
        context = c;
        activity = requireActivity();
    }

    @Override
    public void onStop() {
        if (settingChanged) {
            new AlertDialog
                    .Builder(context)
                    .setTitle(R.string.filter_settings_changed_prompt_title)
                    .setMessage(R.string.filter_settings_changed_prompt_message)
                    .setNegativeButton(R.string.no, null)
                    .setPositiveButton(R.string.yes, (dialog, which) -> {
                        InfoCache.getInstance().clearCache();
                        final Intent intent =
                                new Intent(context, FeedLoadService.class);
                        intent.putExtra(
                                FeedLoadService.EXTRA_GROUP_ID, FeedGroupEntity.GROUP_ALL_ID);
                        intent.putExtra(
                                FeedLoadService.FORCE, true);
                        activity.startService(intent);
                        dialog.dismiss();
                    })
                    .show();
        }
        super.onStop();
    }

    @Override
    public boolean onPreferenceChange(@NonNull final Preference preference, final Object newValue) {
        settingChanged = true;
        return true;
    }

    private void resetFilters() {
        final SharedPreferences preferences = getPreferenceManager().getSharedPreferences();
        preferences
                .edit()
                .putString(getString(R.string.simple_video_title_filter_key), "")
                .putString(getString(R.string.simple_uploader_name_filter_key), "")
                .putBoolean(getString(R.string.hide_shorts_key), false)
                .putString(getString(R.string.regex_video_title_filter_key), "")
                .putString(getString(R.string.regex_uploader_name_filter_key), "")
                .apply();

        final EditTextPreference simpleVideoTitleFilterPreference =
                findPreference(getString(R.string.simple_video_title_filter_key));
        simpleVideoTitleFilterPreference.setText("");

        final EditTextPreference simpleUploaderNameFilterPreference =
                findPreference(getString(R.string.simple_uploader_name_filter_key));
        simpleUploaderNameFilterPreference.setText("");

        final SwitchPreferenceCompat hideShortsPreference =
                findPreference(getString(R.string.hide_shorts_key));
        hideShortsPreference.setChecked(false);

        final EditTextPreference regexVideoTitleFilterPreference =
                findPreference(getString(R.string.regex_video_title_filter_key));
        regexVideoTitleFilterPreference.setText("");

        final EditTextPreference regexUploaderNameFilterPreference =
                findPreference(getString(R.string.regex_uploader_name_filter_key));
        regexUploaderNameFilterPreference.setText("");

        settingChanged = true;
        Toast.makeText(getContext(), R.string.filter_settings_reset_toast,
                Toast.LENGTH_SHORT).show();
    }
}
