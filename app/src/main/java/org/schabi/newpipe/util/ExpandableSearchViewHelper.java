package org.schabi.newpipe.util;

import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;

import java.util.function.Consumer;

/** Configures a toolbar SearchView with consistent query and expansion behavior. */
public final class ExpandableSearchViewHelper {
    private ExpandableSearchViewHelper() {
    }

    public static void configure(@NonNull final MenuItem searchItem,
                                 @NonNull final String hint,
                                 @NonNull final String restoredQuery,
                                 final boolean restoredExpanded,
                                 @NonNull final Consumer<String> queryConsumer,
                                 @NonNull final Consumer<Boolean> expansionConsumer) {
        configure(searchItem, hint, restoredQuery, restoredExpanded,
                queryConsumer, null, expansionConsumer);
    }

    /**
     * Adds an optional submit callback for searches that have a remote query endpoint.
     *
     * @param searchItem search menu item
     * @param hint text displayed in the search field
     * @param restoredQuery saved query, if any
     * @param restoredExpanded whether the field was previously expanded
     * @param queryConsumer listener for every query-text change
     * @param submitConsumer optional listener for an explicit query submission
     * @param expansionConsumer listener for search-field expansion and collapse
     */
    public static void configure(@NonNull final MenuItem searchItem,
                                 @NonNull final String hint,
                                 @NonNull final String restoredQuery,
                                 final boolean restoredExpanded,
                                 @NonNull final Consumer<String> queryConsumer,
                                 @Nullable final Consumer<String> submitConsumer,
                                 @NonNull final Consumer<Boolean> expansionConsumer) {
        final SearchView searchView = (SearchView) searchItem.getActionView();
        searchView.setMaxWidth(Integer.MAX_VALUE);
        searchView.setQueryHint(hint);
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(final String query) {
                queryConsumer.accept(query);
                if (submitConsumer != null) {
                    submitConsumer.accept(query);
                }
                searchView.clearFocus();
                return true;
            }

            @Override
            public boolean onQueryTextChange(final String newText) {
                queryConsumer.accept(newText);
                return true;
            }
        });
        searchItem.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
            @Override
            public boolean onMenuItemActionExpand(@NonNull final MenuItem item) {
                expansionConsumer.accept(true);
                return true;
            }

            @Override
            public boolean onMenuItemActionCollapse(@NonNull final MenuItem item) {
                expansionConsumer.accept(false);
                queryConsumer.accept("");
                return true;
            }
        });

        if (restoredExpanded) {
            searchItem.expandActionView();
            searchView.setQuery(restoredQuery, false);
        }
    }
}
