// Created by evermind-zz 2022, licensed GNU GPL version 3 or later

package org.schabi.newpipe.fragments.list.search.filter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.schabi.newpipe.databinding.SearchFilterDialogFragmentBinding;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;

/**
 * A search filter dialog that also looks like a dialog aka. 'dialog style'.
 */
public class SearchFilterDialogFragment extends BaseSearchFilterDialogFragment {

    protected SearchFilterDialogFragmentBinding binding;

    @Override
    protected BaseSearchFilterUiGenerator createSearchFilterDialogGenerator() {
        return new SearchFilterDialogGenerator(
                searchViewModel.getSearchFilterLogic(), binding.verticalScroll, requireContext());
    }

    @Override
    @Nullable
    protected Toolbar getToolbar() {
        return binding.toolbarLayout.toolbar;
    }

    @Override
    protected View getRootView(@NonNull final LayoutInflater inflater,
                               @Nullable final ViewGroup container) {
        binding = SearchFilterDialogFragmentBinding
                .inflate(inflater, container, false);
        return binding.getRoot();
    }
}
