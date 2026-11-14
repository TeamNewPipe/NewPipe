/*
 * SPDX-FileCopyrightText: 2026 ninjaeu (Merak)
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.util;

import android.content.Context;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import org.schabi.newpipe.R;

/**
 * Adds a "search by title" field to a fragment's toolbar and reports every change of the
 * query, so the fragment can filter the items it already shows.
 * <p>
 * Closing the field clears the query. The toolbar also closes the field whenever it rebuilds
 * its menu (for example when another fragment adds menu items). Both cases reach this class
 * the same way, as the field's text turning empty, so this class waits one main loop cycle
 * before it acts: if the fragment rebuilt the menu in the meantime, the query survives and
 * the new field reopens with it.
 * </p>
 */
public final class TitleFilterMenu {

    /** Receives the filter each time the user changes the query. */
    public interface Listener {
        /**
         * @param filter the filter for the new query, or {@code null} to show every item
         */
        void onTitleFilterChanged(@Nullable TitleFilter filter);
    }

    @NonNull
    private final Fragment fragment;
    @StringRes
    private final int hintRes;
    @NonNull
    private final Listener listener;

    @NonNull
    private String query;
    @Nullable
    private MenuItem menuItem;
    /** Counts menu rebuilds, so a pending clear can tell whether a newer menu exists. */
    private int menuGeneration = 0;

    /**
     * @param fragment     the fragment that owns the toolbar menu
     * @param hintRes      the hint and menu title, for example "Search playlists"
     * @param initialQuery the query restored from saved state, if any
     * @param listener     receives every change of the filter
     */
    public TitleFilterMenu(@NonNull final Fragment fragment,
                           @StringRes final int hintRes,
                           @Nullable final String initialQuery,
                           @NonNull final Listener listener) {
        this.fragment = fragment;
        this.hintRes = hintRes;
        this.query = initialQuery == null ? "" : initialQuery;
        this.listener = listener;
    }

    /**
     * Adds the search field to the menu. Call this from {@code onCreateOptionsMenu}.
     *
     * @param menu the menu being created
     */
    public void addTo(@NonNull final Menu menu) {
        menuGeneration++;
        final Context themedContext = getToolbarContext();

        final MenuItem item = menu.add(Menu.NONE, R.id.menu_item_title_filter, Menu.NONE,
                hintRes);
        item.setIcon(R.drawable.ic_filter_list);
        item.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM
                | MenuItem.SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW);

        // Built in code: a menu XML actionViewClass is created by reflection, which R8 can
        // break in minified release builds.
        final SearchView searchView = new SearchView(themedContext);
        searchView.setQueryHint(themedContext.getString(hintRes));
        searchView.setMaxWidth(Integer.MAX_VALUE);
        searchView.setImeOptions(EditorInfo.IME_ACTION_SEARCH
                | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        item.setActionView(searchView);
        menuItem = item;

        if (query.isEmpty()) {
            attachQueryListener(item, searchView);
            return;
        }

        // Reopen the field with the saved query. Expanding the field empties its text, so the
        // listener attaches only after the query is back in place.
        final String savedQuery = query;
        final Runnable restore = () -> {
            if (menuItem != item) {
                return; // a newer menu replaced this one before the restore ran
            }
            item.expandActionView();
            searchView.setQuery(savedQuery, false);
            searchView.clearFocus();
            attachQueryListener(item, searchView);
        };
        final View root = fragment.getView();
        if (root != null) {
            root.post(restore);
        } else {
            restore.run();
        }
    }

    /**
     * @return the toolbar's themed context, so the search text uses the toolbar's colors
     */
    @NonNull
    private Context getToolbarContext() {
        final FragmentActivity activity = fragment.getActivity();
        if (activity instanceof AppCompatActivity) {
            final ActionBar actionBar = ((AppCompatActivity) activity).getSupportActionBar();
            if (actionBar != null) {
                return actionBar.getThemedContext();
            }
        }
        return fragment.requireContext();
    }

    private void attachQueryListener(@NonNull final MenuItem item,
                                     @NonNull final SearchView searchView) {
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(final String submitted) {
                // Results already follow each keystroke; submitting only hides the keyboard.
                searchView.clearFocus();
                return true;
            }

            @Override
            public boolean onQueryTextChange(final String newText) {
                if (TextUtils.isEmpty(newText)) {
                    onTextCleared(item, searchView);
                } else {
                    setQuery(newText);
                }
                return true;
            }
        });
    }

    /**
     * Empty text arrives when the user deletes the query, when the user closes the field, and
     * when the toolbar closes the field to rebuild its menu. Only the last case must keep the
     * query, and it is recognizable one main loop cycle later, once the new menu exists.
     *
     * @param item       the menu item that holds the field
     * @param searchView the field whose text turned empty
     */
    private void onTextCleared(@NonNull final MenuItem item,
                               @NonNull final SearchView searchView) {
        if (fragment.isStateSaved()) {
            return; // the activity is being torn down (e.g. rotation): keep the saved query
        }
        final int generationAtClear = menuGeneration;
        final Runnable decide = () -> {
            if (fragment.isStateSaved() || searchView.getQuery().length() > 0) {
                return; // torn down meanwhile, or the user typed again
            }
            if (item.isActionViewExpanded() || generationAtClear == menuGeneration) {
                setQuery(""); // the user deleted the text or closed the field
            }
        };
        final View root = fragment.getView();
        if (root != null) {
            root.post(decide);
        } else {
            decide.run();
        }
    }

    private void setQuery(@NonNull final String newQuery) {
        if (newQuery.equals(query)) {
            return;
        }
        query = newQuery;
        listener.onTitleFilterChanged(TitleFilter.of(query));
    }

    /**
     * @return the current query, for saving in the fragment state
     */
    @NonNull
    public String getQuery() {
        return query;
    }

    /**
     * @return the filter for the current query, or {@code null} when the query is empty
     */
    @Nullable
    public TitleFilter getFilter() {
        return TitleFilter.of(query);
    }

    /**
     * Forgets the menu item. Call this from {@code onDestroyView}.
     */
    public void release() {
        menuItem = null;
    }
}
