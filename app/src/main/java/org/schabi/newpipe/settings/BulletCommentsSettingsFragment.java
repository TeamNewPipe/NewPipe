package org.schabi.newpipe.settings;

import android.content.SharedPreferences;
import android.os.Bundle;
import androidx.preference.SeekBarPreference;
import org.schabi.newpipe.R;

public class BulletCommentsSettingsFragment extends BasePreferenceFragment {

    private SharedPreferences.OnSharedPreferenceChangeListener listener;

    @Override
    public void onCreatePreferences(final Bundle savedInstanceState, final String rootKey) {
        addPreferencesFromResourceRegistry();

        listener = (sharedPreferences, s) -> {
            // add listeners to show the current float duration of regular bullet comments and top_bottom bullet comments
            if (s.equals(getString(R.string.top_bottom_bullet_comments_duration_key))){
                final int newSetting = sharedPreferences.getInt(s, 8);
                final SeekBarPreference topBottomBulletCommentsDuration = findPreference(s);
                assert topBottomBulletCommentsDuration != null;
                topBottomBulletCommentsDuration.setSummary(newSetting + " seconds");
            }
            else if (s.equals(getString(R.string.regular_bullet_comments_duration_key))){
                final int newSetting = sharedPreferences.getInt(s, 8);
                final SeekBarPreference regularBulletCommentsDuration = findPreference(s);
                assert regularBulletCommentsDuration != null;
                regularBulletCommentsDuration.setSummary(newSetting + " seconds");
            }
            // Add listeners for the rows preferences
            else if (s.equals(getString(R.string.max_bullet_comments_rows_top_key)) ||
                    s.equals(getString(R.string.max_bullet_comments_rows_bottom_key)) ||
                    s.equals(getString(R.string.max_bullet_comments_rows_regular_key))) {
                final int newSetting = sharedPreferences.getInt(s, 15);
                final SeekBarPreference rowsPref = findPreference(s);
                if (rowsPref != null) {
                    rowsPref.setSummary(String.valueOf(newSetting));
                }
            }
        };

        // Initialize min values for duration preferences
        final SeekBarPreference regularBulletCommentsDuration = findPreference(getString(R.string.regular_bullet_comments_duration_key));
        assert regularBulletCommentsDuration != null;
        regularBulletCommentsDuration.setMin(5);
        final SeekBarPreference topBottomBulletCommentsDuration = findPreference(getString(R.string.top_bottom_bullet_comments_duration_key));
        assert topBottomBulletCommentsDuration != null;
        topBottomBulletCommentsDuration.setMin(5);

        // Initialize summaries for rows preferences
        SeekBarPreference topRowsPref = findPreference(getString(R.string.max_bullet_comments_rows_top_key));
        SeekBarPreference bottomRowsPref = findPreference(getString(R.string.max_bullet_comments_rows_bottom_key));
        SeekBarPreference regularRowsPref = findPreference(getString(R.string.max_bullet_comments_rows_regular_key));

        SharedPreferences sharedPreferences = getPreferenceManager().getSharedPreferences();
        if (topRowsPref != null) {
            topRowsPref.setSummary(String.valueOf(sharedPreferences.getInt(
                    getString(R.string.max_bullet_comments_rows_top_key), 15)));
        }
        if (bottomRowsPref != null) {
            bottomRowsPref.setSummary(String.valueOf(sharedPreferences.getInt(
                    getString(R.string.max_bullet_comments_rows_bottom_key), 15)));
        }
        if (regularRowsPref != null) {
            regularRowsPref.setSummary(String.valueOf(sharedPreferences.getInt(
                    getString(R.string.max_bullet_comments_rows_regular_key), 15)));
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