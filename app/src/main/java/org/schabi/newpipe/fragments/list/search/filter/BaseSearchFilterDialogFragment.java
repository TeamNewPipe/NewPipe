// Created by evermind-zz 2022, licensed GNU GPL version 3 or later

package org.schabi.newpipe.fragments.list.search.filter;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.schabi.newpipe.R;
import org.schabi.newpipe.fragments.list.search.SearchViewModel;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;

/**
 * Base dialog class for {@link DialogFragment} based search filter dialogs.
 */
public abstract class BaseSearchFilterDialogFragment extends DialogFragment {

    protected BaseSearchFilterUiGenerator dialogGenerator;
    protected SearchViewModel searchViewModel;

    private void createSearchFilterUi() {
        dialogGenerator = createSearchFilterDialogGenerator();
        dialogGenerator.createSearchUI();
    }

    @Override
    public void show(@NonNull final FragmentManager manager, @Nullable final String tag) {
        // Avoid multiple instances of the dialog that could be triggered by multiple taps
        if (manager.findFragmentByTag(tag) == null) {
            super.show(manager, tag);
        }
    }

    protected abstract BaseSearchFilterUiGenerator createSearchFilterDialogGenerator();

    /**
     * As we have different bindings we need to get this sorted in a method.
     *
     * @return the {@link Toolbar} null if there is no toolbar available.
     */
    @Nullable
    protected abstract Toolbar getToolbar();

    protected abstract View getRootView(@NonNull LayoutInflater inflater,
                                        @Nullable ViewGroup container);

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Make sure that the first parameter is pointing to instance of SearchFragment otherwise
        // another SearchViewModel object will be created instead of the existing one used.
        // -> the SearchViewModel is first instantiated in SearchFragment. Here we just use it.
        searchViewModel =
                new ViewModelProvider(requireParentFragment()).get(SearchViewModel.class);
    }

    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             final Bundle savedInstanceState) {
        final View rootView = getRootView(inflater, container);
        createSearchFilterUi();
        return rootView;
    }

    @Override
    public void onViewCreated(@NonNull final View view, final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        final Toolbar toolbar = getToolbar();
        if (toolbar != null) {
            initToolbar(toolbar);
        }
    }

    /**
     * Initialize the toolbar.
     * <p>
     * This method is only called if {@link #getToolbar()} is implemented to return a toolbar.
     *
     * @param toolbar the actual toolbar for this dialog fragment
     */
    protected void initToolbar(@NonNull final Toolbar toolbar) {
        toolbar.setTitle(R.string.filter);
        toolbar.setNavigationIcon(R.drawable.ic_arrow_back);
        toolbar.inflateMenu(R.menu.menu_search_filter_dialog_fragment);
        toolbar.setNavigationOnClickListener(v -> dismiss());
        toolbar.setNavigationContentDescription(R.string.cancel);

        final View okButton = toolbar.findViewById(R.id.search);
        okButton.setEnabled(true);

        final View resetButton = toolbar.findViewById(R.id.reset);
        resetButton.setEnabled(true);

        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.search) {
                searchViewModel.getSearchFilterLogic().prepareForSearch();
                dismiss();
                return true;
            } else if (item.getItemId() == R.id.reset) {
                searchViewModel.getSearchFilterLogic().reset();
                return true;
            }
            return false;
        });
    }
}
