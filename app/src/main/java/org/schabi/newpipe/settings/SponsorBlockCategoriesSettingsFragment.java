package org.schabi.newpipe.settings;

import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.annotation.ColorRes;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;
import androidx.preference.SwitchPreference;

import org.schabi.newpipe.R;
import org.schabi.newpipe.settings.custom.EditColorPreference;

public class SponsorBlockCategoriesSettingsFragment extends BasePreferenceFragment {
    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onCreatePreferences(final Bundle savedInstanceState, final String rootKey) {
        addPreferencesFromResourceRegistry();

        final Preference allOnPreference =
                findPreference(getString(R.string.sponsor_block_category_all_on_key));
        allOnPreference.setOnPreferenceClickListener(p -> {
            final SwitchPreference sponsorCategoryPreference =
                    findPreference(getString(R.string.sponsor_block_category_sponsor_key));
            final SwitchPreference introCategoryPreference =
                    findPreference(getString(R.string.sponsor_block_category_intro_key));
            final SwitchPreference outroCategoryPreference =
                    findPreference(getString(R.string.sponsor_block_category_outro_key));
            final SwitchPreference interactionCategoryPreference =
                    findPreference(getString(R.string.sponsor_block_category_interaction_key));
            final SwitchPreference highlightCategoryPreference =
                    findPreference(getString(R.string.sponsor_block_category_highlight_key));
            final SwitchPreference selfPromoCategoryPreference =
                    findPreference(getString(R.string.sponsor_block_category_self_promo_key));
            final SwitchPreference nonMusicCategoryPreference =
                    findPreference(getString(R.string.sponsor_block_category_non_music_key));
            final SwitchPreference previewCategoryPreference =
                    findPreference(getString(R.string.sponsor_block_category_preview_key));
            final SwitchPreference fillerCategoryPreference =
                    findPreference(getString(R.string.sponsor_block_category_filler_key));

            sponsorCategoryPreference.setChecked(true);
            introCategoryPreference.setChecked(true);
            outroCategoryPreference.setChecked(true);
            interactionCategoryPreference.setChecked(true);
            highlightCategoryPreference.setChecked(true);
            selfPromoCategoryPreference.setChecked(true);
            nonMusicCategoryPreference.setChecked(true);
            previewCategoryPreference.setChecked(true);
            fillerCategoryPreference.setChecked(true);

            return true;
        });

        final Preference allOffPreference =
                findPreference(getString(R.string.sponsor_block_category_all_off_key));
        allOffPreference.setOnPreferenceClickListener(p -> {
            final SwitchPreference sponsorCategoryPreference =
                    findPreference(getString(R.string.sponsor_block_category_sponsor_key));
            final SwitchPreference introCategoryPreference =
                    findPreference(getString(R.string.sponsor_block_category_intro_key));
            final SwitchPreference outroCategoryPreference =
                    findPreference(getString(R.string.sponsor_block_category_outro_key));
            final SwitchPreference interactionCategoryPreference =
                    findPreference(getString(R.string.sponsor_block_category_interaction_key));
            final SwitchPreference highlightCategoryPreference =
                    findPreference(getString(R.string.sponsor_block_category_highlight_key));
            final SwitchPreference selfPromoCategoryPreference =
                    findPreference(getString(R.string.sponsor_block_category_self_promo_key));
            final SwitchPreference nonMusicCategoryPreference =
                    findPreference(getString(R.string.sponsor_block_category_non_music_key));
            final SwitchPreference previewCategoryPreference =
                    findPreference(getString(R.string.sponsor_block_category_preview_key));
            final SwitchPreference fillerCategoryPreference =
                    findPreference(getString(R.string.sponsor_block_category_filler_key));

            sponsorCategoryPreference.setChecked(false);
            introCategoryPreference.setChecked(false);
            outroCategoryPreference.setChecked(false);
            interactionCategoryPreference.setChecked(false);
            highlightCategoryPreference.setChecked(false);
            selfPromoCategoryPreference.setChecked(false);
            nonMusicCategoryPreference.setChecked(false);
            previewCategoryPreference.setChecked(false);
            fillerCategoryPreference.setChecked(false);

            return true;
        });

        final Preference resetPreference =
                findPreference(getString(R.string.sponsor_block_category_reset_key));
        resetPreference.setOnPreferenceClickListener(p -> {
            new AlertDialog.Builder(p.getContext())
                    .setMessage(R.string.sponsor_block_confirm_reset_colors)
                    .setPositiveButton(R.string.yes, (dialog, which) -> {
                        final SharedPreferences.Editor editor =
                                getPreferenceManager()
                                        .getSharedPreferences()
                                        .edit();

                        setColorPreference(editor,
                                R.string.sponsor_block_category_sponsor_color_key,
                                R.color.sponsor_segment);
                        setColorPreference(editor,
                                R.string.sponsor_block_category_intro_color_key,
                                R.color.intro_segment);
                        setColorPreference(editor,
                                R.string.sponsor_block_category_outro_color_key,
                                R.color.outro_segment);
                        setColorPreference(editor,
                                R.string.sponsor_block_category_interaction_color_key,
                                R.color.interaction_segment);
                        setColorPreference(editor,
                                R.string.sponsor_block_category_highlight_color_key,
                                R.color.highlight_segment);
                        setColorPreference(editor,
                                R.string.sponsor_block_category_self_promo_color_key,
                                R.color.self_promo_segment);
                        setColorPreference(editor,
                                R.string.sponsor_block_category_non_music_color_key,
                                R.color.non_music_segment);
                        setColorPreference(editor,
                                R.string.sponsor_block_category_preview_color_key,
                                R.color.preview_segment);
                        setColorPreference(editor,
                                R.string.sponsor_block_category_filler_color_key,
                                R.color.filler_segment);
                        setColorPreference(editor,
                                R.string.sponsor_block_category_pending_color_key,
                                R.color.pending_segment);

                        editor.apply();
                    })
                    .setNegativeButton(R.string.cancel, (dialog, which) -> dialog.dismiss())
                    .show();
            return true;
        });
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
