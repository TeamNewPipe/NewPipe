package org.schabi.newpipe.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.annotation.ColorRes;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.preference.Preference;

import org.schabi.newpipe.R;
import org.schabi.newpipe.settings.custom.EditColorPreference;

public class SponsorBlockCategoriesSettingsFragment extends BasePreferenceFragment {
    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onCreatePreferences(final Bundle savedInstanceState, final String rootKey) {
        addPreferencesFromResource(R.xml.sponsor_block_category_settings);

        final Preference resetPreferenceView =
                findPreference(getString(R.string.sponsorblock_category_reset_key));
        if (resetPreferenceView != null) {
            resetPreferenceView.setOnPreferenceClickListener(preference -> {
                final Context context = getContext();

                if (context != null) {
                    final SharedPreferences.Editor editor =
                            getPreferenceManager()
                                    .getSharedPreferences()
                                    .edit();

                    setColorPreference(editor,
                            R.string.sponsorblock_category_sponsor_color_key,
                            R.color.sponsor_segment);

                    setColorPreference(editor,
                            R.string.sponsorblock_category_intro_color_key,
                            R.color.intro_segment);

                    setColorPreference(editor,
                            R.string.sponsorblock_category_outro_color_key,
                            R.color.outro_segment);

                    setColorPreference(editor,
                            R.string.sponsorblock_category_interaction_color_key,
                            R.color.interaction_segment);

                    setColorPreference(editor,
                            R.string.sponsorblock_category_self_promo_color_key,
                            R.color.self_promo_segment);

                    setColorPreference(editor,
                            R.string.sponsorblock_category_non_music_color_key,
                            R.color.non_music_segment);

                    editor.apply();
                }

                return true;
            });
        }
    }

    private void setColorPreference(final SharedPreferences.Editor editor,
                                    @StringRes final int resId,
                                    @ColorRes final int colorId) {
        final String colorStr = "#" + Integer.toHexString(getResources().getColor(colorId));
        editor.putString(getString(resId), colorStr);
        final EditColorPreference colorPreference = findPreference(getString(resId));
        colorPreference.setText(colorStr);
    }
}
