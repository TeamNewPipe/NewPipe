// Created by evermind-zz 2022, licensed GNU GPL version 3 or later

package org.schabi.newpipe.fragments.list.search.filter;

import android.content.res.Configuration;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;

/**
 * Every search filter option in this dialog is a {@link com.google.android.material.chip.Chip}.
 */
public class SearchFilterChipDialogFragment extends SearchFilterDialogFragment {

    @Override
    protected BaseSearchFilterUiGenerator createSearchFilterDialogGenerator() {
        return new SearchFilterChipDialogGenerator(
                searchViewModel.getSearchFilterLogic(), binding.verticalScroll, requireContext());
    }

    @Override
    public void onViewCreated(@NonNull final View view, final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        final Configuration configuration = getResources().getConfiguration();
        final DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
        final ViewGroup.LayoutParams layoutParams = binding.getRoot().getLayoutParams();

        if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            layoutParams.width = (int) (displayMetrics.widthPixels * 0.80f);
        } else if (configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
            layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
        }

        binding.getRoot().setLayoutParams(layoutParams);
    }
}
