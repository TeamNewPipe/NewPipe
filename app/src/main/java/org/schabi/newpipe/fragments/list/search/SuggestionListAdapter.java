package org.schabi.newpipe.fragments.list.search;

import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.support.v4.content.ContextCompat;
import android.support.v4.widget.ResourceCursorAdapter;
import android.view.View;
import android.widget.TextView;

import org.schabi.newpipe.database.history.model.SearchHistoryEntry;

import java.util.List;

/*
 * Created by Christian Schabesberger on 02.08.16.
 *
 * Copyright (C) Christian Schabesberger 2016 <chris.schabesberger@mailbox.org>
 * SuggestionListAdapter.java is part of NewPipe.
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

/**
 * {@link ResourceCursorAdapter} to display suggestions.
 */
public class SuggestionListAdapter extends ResourceCursorAdapter {

    private static final String[] columns = new String[]{"_id", "title", "is_history_item"};
    private static final int INDEX_ID = 0;
    private static final int INDEX_TITLE = 1;
    private static final int INDEX_IS_HISTORY_ITEM = 2;


    public SuggestionListAdapter(Context context) {
        super(context, android.R.layout.simple_list_item_1, null, 0);
    }

    @Override
    public void bindView(View view, Context context, Cursor cursor) {
        ViewHolder viewHolder = new ViewHolder(view);
        viewHolder.suggestionTitle.setText(cursor.getString(INDEX_TITLE));
        boolean isHistoryItem = Boolean.valueOf(cursor.getString(INDEX_IS_HISTORY_ITEM));
        int color = isHistoryItem ? android.R.color.holo_blue_dark : android.R.color.secondary_text_light;
        viewHolder.suggestionTitle.setTextColor(ContextCompat.getColor(context, color));
    }

    public void clearAdapter() {
        //changeCursor(new MatrixCursor(columns, 0));
    }

    /**
     * Update the suggestion list
     * @param suggestions the list of suggestions
     */
    public void updateAdapter(List<SearchHistoryEntry> historyItems, List<String> suggestions) {
        MatrixCursor cursor = new MatrixCursor(columns, historyItems.size() + suggestions.size());
        for (int i = 0; i < historyItems.size(); i++) {
            insertRow(i, cursor, historyItems.get(i).getSearch(), true);
        }
        for (int i = 0; i < suggestions.size(); i++) {
            insertRow(historyItems.size() + i, cursor, suggestions.get(i), false);
        }
        changeCursor(cursor);
    }

    private void insertRow(int index, MatrixCursor cursor, String suggestion, boolean isHistoryItem) {
        String[] columnValues = new String[columns.length];
        columnValues[INDEX_TITLE] = suggestion;
        columnValues[INDEX_ID] = Integer.toString(index);
        columnValues[INDEX_IS_HISTORY_ITEM] = Boolean.toString(isHistoryItem);
        cursor.addRow(columnValues);
    }

    /**
     * Get the suggestion for a position
     * @param position the position of the suggestion
     * @return the suggestion
     */
    public String getSuggestion(int position) {
        return ((Cursor) getItem(position)).getString(INDEX_TITLE);
    }

    @Override
    public CharSequence convertToString(Cursor cursor) {
        return cursor.getString(INDEX_TITLE);
    }

    private class ViewHolder {
        private final TextView suggestionTitle;
        private ViewHolder(View view) {
            this.suggestionTitle = view.findViewById(android.R.id.text1);
        }
    }
}