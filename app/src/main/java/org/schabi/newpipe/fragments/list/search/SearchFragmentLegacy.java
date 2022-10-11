// Created by evermind-zz 2022, licensed GNU GPL version 3 or later

package org.schabi.newpipe.fragments.list.search;

import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;

import org.schabi.newpipe.R;
import org.schabi.newpipe.fragments.list.search.filter.SearchFilterLogic;
import org.schabi.newpipe.fragments.list.search.filter.SearchFilterUIOptionMenu;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import icepick.State;

/**
 * Fragment that hosts the action menu based filter 'dialog'.
 * <p>
 * Called ..Legacy because this was the way NewPipe had implemented the search filter dialog.
 * <p>
 * The new UI's are handled by {@link SearchFragment} and implemented by
 * using {@link androidx.fragment.app.DialogFragment}.
 */
public class SearchFragmentLegacy extends SearchFragment {

    @State
    protected int countOnPrepareOptionsMenuCalls = 0;
    private SearchFilterUIOptionMenu searchFilterUi;

    @Override
    protected void initViewModel() {
        logicVariant = SearchFilterLogic.Factory.Variant.SEARCH_FILTER_LOGIC_LEGACY;
        super.initViewModel();

        searchFilterUi = new SearchFilterUIOptionMenu(
                searchViewModel.getSearchFilterLogic(), requireContext());
    }

    @Override
    protected void createMenu(@NonNull final Menu menu,
                              @NonNull final MenuInflater inflater) {
        searchFilterUi.createSearchUI(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull final MenuItem item) {
        return searchFilterUi.onOptionsItemSelected(item);
    }

    @Override
    protected void initViews(final View rootView,
                             final Bundle savedInstanceState) {
        super.initViews(rootView, savedInstanceState);
        final Toolbar toolbar = (Toolbar) searchToolbarContainer.getParent();
        toolbar.setOverflowIcon(ContextCompat.getDrawable(requireContext(),
                R.drawable.ic_sort));
    }

    @Override
    public void onPrepareOptionsMenu(@NonNull final Menu menu) {
        super.onPrepareOptionsMenu(menu);
        // workaround: we want to hide the keyboard in case we open the options
        // menu. As somehow this method gets triggered twice but only the 2nd
        // time is relevant as the options menu is selected by the user.
        if (++countOnPrepareOptionsMenuCalls > 1) {
            hideKeyboardSearch();
        }
    }
}
