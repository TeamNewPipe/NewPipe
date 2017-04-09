package org.schabi.newpipe.fragments.search;

import android.support.v7.widget.SearchView;

/**
 * Created by Christian Schabesberger on 02.08.16.
 *
 * Copyright (C) Christian Schabesberger 2016 <chris.schabesberger@mailbox.org>
 * SearchSuggestionListener.java is part of NewPipe.
 *
 * NewPipe is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NewPipe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NewPipe.  If not, see <http://www.gnu.org/licenses/>.
 */


public class SearchSuggestionListener implements SearchView.OnSuggestionListener{

    private final SearchView searchView;
    private final SuggestionListAdapter adapter;

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