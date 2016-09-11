package org.schabi.newpipe.search_fragment;

import android.support.v7.widget.SearchView;

/**
 * Created by the-scrabi on 02.08.16.
 */

public class SearchSuggestionListener implements SearchView.OnSuggestionListener{

    private SearchView searchView;
    private SuggestionListAdapter adapter;

    public SearchSuggestionListener(SearchView searchView, SuggestionListAdapter adapter) {
        this.searchView = searchView;
        this.adapter = adapter;
    }

    @Override
    public boolean onSuggestionSelect(int position) {
        String suggestion = adapter.getSuggestion(position);
        searchView.setQuery(suggestion,true);
        return false;
    }

    @Override
    public boolean onSuggestionClick(int position) {
        String suggestion = adapter.getSuggestion(position);
        searchView.setQuery(suggestion,true);
        return false;
    }
}